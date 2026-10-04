package com.example.zhilu.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zhilu.domain.markup.InlineKind
import com.example.zhilu.domain.markup.InlineNodes
import com.example.zhilu.ui.component.RichText
import com.example.zhilu.ui.navigation.LocalAppSnackbar
import com.example.zhilu.ui.note.blocks.AtomVisibility
import com.example.zhilu.ui.note.blocks.LocalAtomVisibility as LocalVisibilitySink
import com.example.zhilu.ui.note.blocks.LatexBlockEditor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Surface
import com.example.zhilu.ui.note.latex.LatexImage
import com.example.zhilu.ui.note.latex.rememberLatexImage
import com.example.zhilu.ui.note.blocks.ReadOnlyLatexBlockContent
import com.example.zhilu.ui.note.blocks.TextBlockEditor
import com.example.zhilu.ui.theme.ZhiLuTheme
import timber.log.Timber

/**
 * 行内公式**尺寸诊断屏**（只在 debug 变体）。
 *
 * 要回答的问题：设计文档 §14.5 的"含 CJK 的行内公式偏小"，到底小在哪一环？
 * 一条公式的观感由三组数字共同决定，缺任何一组都会误判：
 *
 * 1. **位图自身尺寸** —— 由渲染字号决定，与布局无关（只读态画的就是它）；
 * 2. **不可见源码占的盒子** —— 控盒（字号撑高 + 字距压宽）的结果；
 * 3. **覆盖层最终缩放** —— `min(盒宽/图宽, 行高限制)`，可能把图锁在 1.0 以下。
 *
 * 老版本只在日志里打印 1、2 两组，于是"图小"和"盒子把图衬托小"分不开。这一屏把三组
 * **并列**显示，并且下面同一段文字用只读路径（[RichText]）再画一遍作为对照；
 * 同一份数字还会走 Timber 打一行（`adb logcat -s ZhiLuProbe`），方便与截图逐一对照。
 *
 * 拉起：`adb shell am start -n com.example.zhilu/com.example.zhilu.debug.FormulaProbeActivity`
 */
class FormulaProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZhiLuTheme { FormulaProbeScreen() } }
    }
}

/**
 * 采样文本：四条公式覆盖四种会互相干扰的情形。
 *
 * - `a` 含 CJK（`\text{发}`）—— 就是 §14.5 报的那一条；
 * - `b` 纯 ASCII 短公式 —— 对照组，看"只有 CJK 偏小"是否成立；
 * - `c` 长公式 —— 会折行，走控盒的"只调字号"分支；
 * - `d` 带上下标 —— 图偏高，最容易撞上"行高限制"。
 */
private const val PROBE_TEXT =
    "GBN：代入 \$W_{\\text{发}}=W_{\\text{收}}=W\$ → \$2W \\le 2^n\$，" +
        "于是 \$W_{\\text{发}} \\le 2^n - 1\$ 与 \$2^{n-1}\$ 都成立，收尾补一句话让它自然折行。"

/**
 * 压力采样：**越来越长**的公式，每条单独一行。
 *
 * 编辑态的原子盒子等于"源码占宽"，而源码比图宽得多 —— 所以只有当源码被字距压到**比图还窄**时，
 * 覆盖层的宽度项才会真的开始缩小公式。这一组用来看"多长才会被缩"，以及被缩时比例是多少。
 */
private const val PROBE_LONG_TEXT =
    "短：\$a+b\$\n" +
        "中：\$W_{\\text{发}} \\le 2^n - 1\$\n" +
        "长：\$\\sum_{i=1}^{n} W_{\\text{发},i} \\le 2^{n-1} - 1\$\n" +
        "超长：\$\\sum_{i=1}^{n} \\left( W_{\\text{发},i} + W_{\\text{收},i} \\right) \\le 2^{n-1} - \\frac{1}{2}\$"

/**
 * 复现"光标在行间插入时公式消失"：两行，各含一条行内公式。
 *
 * 光标初始化在文末，按回车就会在**第二行末尾**插入换行；把光标点到第一行行尾再回车，
 * 则是在**行间**插入。两种情况都会让"光标所在原子恢复源码"这条规则重新算一遍，
 * 是 `hiddenMath` 最容易漏掉整行的位置。
 */
private const val PROBE_MULTILINE =
    "第一行含公式 \$a+b\$ 结束\n" +
        "第二行含公式 \$c^2\$ 结束"

/**
 * 超宽公式（用户报"右侧被裁"那条的同形）：定积分定义，本身就比卡片宽。
 *
 * 选它是因为它同时有上下限、lim 下标与 Σ 上下限 —— 横向很宽、纵向也不矮。
 */
private const val PROBE_WIDE_LATEX =
    "\\int_{a}^{b} f(x)\\,dx = \\lim_{\\lambda \\to 0} \\sum_{i=1}^{n} f(\\xi_i) \\Delta x_i"

/**
 * 极宽公式：约为可视宽度的 2 倍以上。
 *
 * 用来区分"缩到放得下"与"溢出被裁"两条分支 —— [PROBE_WIDE_LATEX] 还在可缩范围内，
 * 这条会逼近 `LatexImage` 里 `maxWidth == Constraints.Infinity` 的那条退化路径。
 */
private const val PROBE_EXTREME_LATEX =
    "\\int_{a}^{b} f(x)\\,dx = \\lim_{\\lambda \\to 0} \\sum_{i=1}^{n} f(\\xi_i) \\Delta x_i" +
        " = \\lim_{n \\to \\infty} \\sum_{k=1}^{n} f\\left(a + \\frac{k(b-a)}{n}\\right) \\cdot \\frac{b-a}{n}"

@Composable
private fun FormulaProbeScreen() {
    // 编辑器要往 snackbar 报"公式里不能加标记"，正式包里这个 local 由 AppShell 下发；
    // 诊断屏没有 AppShell，所以自己给一个（不给会直接抛异常，踩过）。
    val snackbar = remember { SnackbarHostState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("行内公式尺寸诊断", style = MaterialTheme.typography.titleMedium)
        CompositionLocalProvider(LocalAppSnackbar provides snackbar) {
            ProbeSection(title = "A 混排（§14.5 实测那条）", initial = PROBE_TEXT)
            HorizontalDivider()
            ProbeSection(title = "B 一条比一条长（找缩小的临界）", initial = PROBE_LONG_TEXT)
            HorizontalDivider()
            // 「光标在行间插入时公式消失」的复现位：两行文字各含一条公式，
            // 把光标点到行尾再回车，看可见性快照里 hiddenAtoms 有没有把整行公式漏掉
            ProbeSection(title = "C 行间插入（把光标点到行尾再回车）", initial = PROBE_MULTILINE)
            HorizontalDivider()
            LatexBlockProbe()
        }
    }
}

/**
 * 公式块「宽到超出卡片」的回归位。
 *
 * 要回答的问题：公式比可视宽度宽时，是**缩小到放得下**还是**溢出被裁**？
 * 踩过的坑：编辑态/只读态在 `LatexImage` 外面套了 `horizontalScroll`，而横向滚动会把宽度约束
 * 变成无限、跳过"缩到放得下"那条分支 —— 结果超宽公式被裁掉右半截（真机实测 `… = \lim \sum`
 * 之后整段消失）。修法是去掉横向滚动、只留一个有界容器。
 *
 * [LayoutVariantProbe] 里的 B 变体**刻意保留了旧的横向滚动写法**：它是这条规则的反例，
 * 以后谁再想加回 `horizontalScroll`，跑一眼这一屏就能看见代价。
 */
@Composable
private fun LatexBlockProbe() {
    var value by remember { mutableStateOf(PROBE_WIDE_LATEX) }

    Text("D 超宽公式块（编辑态 vs 只读态）", style = MaterialTheme.typography.titleSmall)

    Text("① 编辑态 LatexBlockEditor", style = MaterialTheme.typography.labelMedium)
    LatexBlockEditor(value = value, onValueChange = { value = it })

    Text("② 只读态 ReadOnlyLatexBlockContent", style = MaterialTheme.typography.labelMedium)
    ReadOnlyLatexBlockContent(value = value)

    HorizontalDivider()
    LayoutVariantProbe()

    HorizontalDivider()
    SelectableTextProbe()
}

/**
 * 「点一下」与「划词」能不能共存。
 *
 * 要回答的问题：`SelectionContainer` 会不会把手势整个吃掉，导致
 * - 点一下**不触发**外层的 clickable（分支标题点不动 = 展开不了）
 * - 长按**不弹**块级菜单
 *
 * 三种写法并排，各自带计数，点一下看数字涨不涨：
 * A 外层 clickable + SelectionContainer          （线上旧写法）
 * B 同上，另外把 SelectionContainer 的 hover 手势禁掉
 * C 完全不套 SelectionContainer（对照：点一下必然有效）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableTextProbe() {
    Text("F 点一下 vs 划词（看计数涨不涨）", style = MaterialTheme.typography.titleSmall)
    val plain = "点我这一行文字看计数"

    var countA by remember { mutableStateOf(0) }
    Text("A 外层 clickable + SelectionContainer（计数=$countA）", style = MaterialTheme.typography.labelMedium)
    Box(modifier = Modifier.fillMaxWidth().clickable { countA++ }) {
        SelectionContainer {
            Text(plain, style = MaterialTheme.typography.bodyLarge)
        }
    }

    var countB by remember { mutableStateOf(0) }
    Text("B 禁掉 hover 手势（计数=$countB）", style = MaterialTheme.typography.labelMedium)
    Box(modifier = Modifier.fillMaxWidth().clickable { countB++ }) {
        SelectionContainer(modifier = Modifier.disableSelectionHover()) {
            Text(plain, style = MaterialTheme.typography.bodyLarge)
        }
    }

    var countC by remember { mutableStateOf(0) }
    Text("C 不套 SelectionContainer（计数=$countC）", style = MaterialTheme.typography.labelMedium)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { countC++ }
    ) {
        Text(plain, style = MaterialTheme.typography.bodyLarge)
    }

    var countD by remember { mutableStateOf(0) }
    Text("D 长按不拖（应记为 1）计数=$countD", style = MaterialTheme.typography.labelMedium)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val still = withTimeoutOrNull(600L) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.pressed }) return@withTimeoutOrNull false
                            val drift = event.changes.filter { it.pressed }
                                .maxOfOrNull { (it.position - down.position).getDistance() } ?: 0f
                            if (drift > 12f) return@withTimeoutOrNull false
                        }
                        @Suppress("UNREACHABLE_CODE") false
                    }
                    if (still == null) countD++
                }
            }
    ) {
        SelectionContainer {
            Text(plain, style = MaterialTheme.typography.bodyLarge)
        }
    }

    // E 复刻线上写法：外层 clickable + 内层 combinedClickable + SelectionContainer
    var countE by remember { mutableStateOf(0) }
    var longE by remember { mutableStateOf(0) }
    Text(
        "E 外层clickable+combinedClickable（点=$countE 长=$longE）",
        style = MaterialTheme.typography.labelMedium
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { countE++ }
    ) {
        Box(
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { longE++ })
        ) {
            SelectionContainer {
                Text(plain, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    // F 修法：把"点一下"收进内层，与长按组合在同一个 combinedClickable 里（不再依赖外层）
    var countF by remember { mutableStateOf(0) }
    var longF by remember { mutableStateOf(0) }
    Text("F 点击内收（点=$countF 长=$longF）", style = MaterialTheme.typography.labelMedium)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { countF++ }, onLongClick = { longF++ })
    ) {
        SelectionContainer {
            Text(plain, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/**
 * 把 `SelectionContainer` 的**悬停划词**手势挤掉。
 *
 * 手机上根本没有鼠标，这个手势只会白占一层指针输入 —— 而它可能正是"点一下不生效"的原因。
 */
@Composable
private fun Modifier.disableSelectionHover(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.forEach { change ->
                if (change.type == PointerType.Mouse) change.consume()
            }
        }
    }
}

/**
 * 同一张超宽公式图，三种布局各画一遍，用来定"到底是哪一层把它裁了"。
 *
 * A 有界 + `LatexImage`：整条缩到放得下（**这就是修好之后的行为**）
 * B 有界 + 横向滚动：**旧写法的反例** —— 宽度约束变无限，缩放被跳过，右半截被裁
 * C 只读块（同 B 的写法）：同样被裁
 */
@Composable
private fun LayoutVariantProbe() {
    Text("E 布局对照（同一张公式图）", style = MaterialTheme.typography.titleSmall)
    val state = rememberLatexImage(
        latex = PROBE_EXTREME_LATEX,
        textSizeSp = 18f,
        color = MaterialTheme.colorScheme.onSurface
    )
    val wide = remember { "\$\$" + PROBE_EXTREME_LATEX + "\$\$" }

    Text("A 有界 + LatexImage（正确）", style = MaterialTheme.typography.labelMedium)
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            LatexImage(state = state)
        }
    }

    Text("B 有界 + horizontalScroll（反例：右半截会被裁）", style = MaterialTheme.typography.labelMedium)
    Surface(
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            LatexImage(state = state, modifier = Modifier.wrapContentWidth())
        }
    }

    Text("C 只读块（若哪天它又被裁，看这里）", style = MaterialTheme.typography.labelMedium)
    ReadOnlyLatexBlockContent(value = wide)
}

/**
 * 一节对照：编辑态 / 只读态 / 可见性三段用**同一段文字**。
 *
 * 编辑态的实参必须与 BlockCard 一致（`value` + `onValueChange` 回写），否则测的不是真实路径。
 */
@Composable
private fun ProbeSection(title: String, initial: String) {
    var value by remember { mutableStateOf(initial) }
    val nodes = remember(value) { InlineNodes.of(value) }
    var visibility by remember { mutableStateOf<AtomVisibility?>(null) }

    Text(title, style = MaterialTheme.typography.titleSmall)

    Text("① 编辑态（真实 TextBlockEditor）", style = MaterialTheme.typography.labelMedium)
    CompositionLocalProvider(LocalVisibilitySink provides { incoming ->
        if (visibility != incoming) {
            visibility = incoming
            // 同时打到日志（`adb logcat -s ZhiLuProbe`）：屏幕上字小又挤，关键数字容易看漏
            Timber.tag("ZhiLuProbe").d("$title 可见性 $incoming")
        }
    }) {
        TextBlockEditor(value = value, onValueChange = { value = it })
    }

    visibility?.let {
        Text(
            text = "可见性：光标=${it.activeOffset ?: "-"} 全部=${it.allAtoms.size} 待画=${it.hiddenAtoms.size}",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        Text(
            text = "  全部=${it.allAtoms}\n  待画=${it.hiddenAtoms}",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
    }

    Text("② 只读态（RichText = 公式图的天然尺寸）", style = MaterialTheme.typography.labelMedium)
    RichText(text = value, style = MaterialTheme.typography.bodyLarge)

    Text("③ 本段解析出的公式源码", style = MaterialTheme.typography.labelMedium)
    nodes.filter { it.kind == InlineKind.MATH }.forEach { node ->
        Text(
            text = "[${node.start},${node.end}] ${node.contentOf(value)}",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}
/** 几何数字打日志用的一行紧凑格式（`adb logcat -s ZhiLuProbe`）。 */
