package com.example.zhilu.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * P0 技术验证屏（**不进主干**，只存在于 debug 变体）。
 *
 * 要回答的问题：`BasicTextField` + `VisualTransformation` 把公式/行内代码折叠成 0 宽之后，
 * 还能不能拿回准确的几何信息来画覆盖层。四条验证对应设计文档 §8。
 *
 * 拉起：`adb shell am start -n com.example.zhilu/com.example.zhilu.debug.P0SpikeActivity`
 */
class P0SpikeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { P0SpikeScreen() }
        }
    }
}

/** 采样文本：**故意够长以便折行**，并含 4 个原子（3 公式 + 1 行内代码）。 */
private const val SPIKE_TEXT =
    "见 \$W_{\\text{发}} \\le 2^{n-1}\$ 与 \$a+b\$ 说明，这里补一句让整行足够长从而折行，" +
        "末尾还有 `code()` 以及 \$x^2\$ 两个原子。"

private val MATH = Regex("\\$[^$]+\\$")
private val INLINE_CODE = Regex("`[^`]+`")

/** 按真实规则解析原子区间（与 `InlineNodes` 将来的实现同形）。 */
private fun atomRanges(text: String): List<IntRange> =
    (MATH.findAll(text).map { it.range } + INLINE_CODE.findAll(text).map { it.range })
        .filter { it.first < text.length }
        .sortedBy { it.first }
        .toList()

@Composable
private fun P0SpikeScreen() {
    // 三种模式对照：原始 / 折叠成 0 宽 / 源码透明但保留宽度
    var mode by remember { mutableStateOf(0) }
    var value by remember { mutableStateOf(TextFieldValue(SPIKE_TEXT)) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current

    val transformation = remember(mode) {
        when (mode) {
            1 -> CollapseTransformation() as VisualTransformation
            2 -> HideSourceTransformation()
            else -> VisualTransformation.None
        }
    }

    val atoms = remember { atomRanges(SPIKE_TEXT) }
    val collapseMap = (transformation as? CollapseTransformation)?.lastMap

    val geometryReport = remember(layout, collapseMap, mode) {
        val result = layout ?: return@remember "layout = null"
        if (mode == 2) {
            // 透明模式：坐标与原文一一对应，量一下每个原子**自己占了多宽**
            buildString {
                appendLine("模式＝透明保留宽度；原长=${SPIKE_TEXT.length} 布局长=${result.layoutInput.text.length}")
                atoms.forEachIndexed { index, range ->
                    val start = runCatching { result.getCursorRect(range.first) }.getOrNull()
                    val end = runCatching { result.getCursorRect(range.last + 1) }.getOrNull()
                    if (start == null || end == null) {
                        appendLine("atom$index 取矩形失败")
                        return@buildString
                    }
                    val reserved = if (start.top == end.top) end.left - start.left else -1f
                    appendLine(
                        "atom$index [${range.first},${range.last}] 源码占宽=%.0fpx 起始 x=%.0f y=%.0f h=%.0f 折行=%s"
                            .format(
                                reserved, start.left, start.top, start.height,
                                if (reserved < 0) "是" else "否"
                            )
                    )
                }
            }
        } else {
            val map = collapseMap ?: return@remember "未折叠（对照组）"
            buildString {
                appendLine("模式＝折叠 0 宽；原长=${SPIKE_TEXT.length} 折叠后长=${result.layoutInput.text.length}")
                atoms.forEachIndexed { index, range ->
                    val at = map.originalToTransformed(range.first)
                    val rect = runCatching { result.getCursorRect(at) }.getOrNull()
                    appendLine(
                        "atom$index [${range.first},${range.last}] → trans=$at " + (
                            rect?.let {
                                "x=%.0f y=%.0f h=%.0f line=${runCatching { result.getLineForOffset(at) }.getOrDefault(-1)}"
                                    .format(it.left, it.top, it.height)
                            } ?: "取矩形失败"
                            )
                    )
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("P0：编辑态渲染原子的两种做法", style = MaterialTheme.typography.titleMedium)
        Text(
            "模式：" + when (mode) {
                1 -> "折叠成 0 宽（预期：覆盖层压住后文）"
                2 -> "源码透明 + 保留宽度（预期：覆盖层正好落在空位里）"
                else -> "原始对照"
            },
            style = MaterialTheme.typography.labelMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("原始", "折叠0宽", "透明").forEachIndexed { index, label ->
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = if (mode == index) Color(0xFFFFD54F) else Color(0xFF9E9E9E),
                    modifier = Modifier
                        .background(Color(0xFF303030))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable { mode = index }
                )
            }
        }

        Text("— 输入框（紫块＝覆盖层）—", style = MaterialTheme.typography.labelMedium)
        Box(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                visualTransformation = transformation,
                onTextLayout = { layout = it },
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF202020))
                    .padding(8.dp)
            )
            val result = layout
            if (mode != 0 && result != null) {
                atoms.forEachIndexed { index, range ->
                    val at = if (mode == 1) {
                        collapseMap?.originalToTransformed(range.first) ?: return@forEachIndexed
                    } else {
                        range.first
                    }
                    val start = runCatching { result.getCursorRect(at) }.getOrNull()
                        ?: return@forEachIndexed
                    val widthPx = if (mode == 2) {
                        val end = runCatching { result.getCursorRect(range.last + 1) }.getOrNull()
                        if (end != null && end.top == start.top) (end.left - start.left) else 84f
                    } else {
                        84f
                    }
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (start.left + with(density) { 8.dp.toPx() }).roundToInt(),
                                    (start.top + with(density) { 8.dp.toPx() }).roundToInt()
                                )
                            }
                            .width(with(density) { widthPx.toDp() })
                            .height(with(density) { start.height.toDp() })
                            .background(
                                if (mode == 2) Color(0x887C4DFF) else Color(0x88FF5252)
                            )
                    ) {
                        Text("原子$index", fontSize = 9.sp, color = Color.White)
                    }
                }
            }
        }

        Text("— 几何诊断 —", style = MaterialTheme.typography.labelMedium)
        Text(geometryReport, fontSize = 12.sp)

        Text(
            "验证 4（IME 组词 / TalkBack）需手动：把光标点进原子再打字、开 TalkBack 读一遍。",
            fontSize = 12.sp,
            color = Color(0xFFFFB74D)
        )
    }
}

/** 把原子源码设成完全透明：布局仍按源码占宽，覆盖层把图片画在这个空位里。 */
internal class HideSourceTransformation(
    private val ranges: List<IntRange> = atomRanges(SPIKE_TEXT)
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val annotated = androidx.compose.ui.text.buildAnnotatedString {
            append(text)
            ranges.forEach { range ->
                val start = range.first.coerceIn(0, text.length)
                val end = (range.last + 1).coerceIn(0, text.length)
                if (end > start) {
                    addStyle(
                        androidx.compose.ui.text.SpanStyle(color = Color.Transparent),
                        start,
                        end
                    )
                }
            }
        }
        return TransformedText(annotated, OffsetMapping.Identity)
    }
}


/** 折叠结果：折叠后的文本 + 前缀可见字数表（原坐标 → 折叠后坐标）。 */
internal class CollapseMap(val text: String, private val prefix: IntArray) {

    fun originalToTransformed(offset: Int): Int = prefix[offset.coerceIn(0, prefix.size - 1)]

    fun transformedToOriginal(offset: Int): Int {
        val target = offset.coerceIn(0, text.length)
        var index = 0
        while (index < prefix.size && prefix[index] < target) index++
        return (index - 1).coerceIn(0, prefix.size - 1)
    }

    fun offsetMapping(): OffsetMapping = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int = this@CollapseMap.originalToTransformed(offset)

        override fun transformedToOriginal(offset: Int): Int = this@CollapseMap.transformedToOriginal(offset)
    }

    companion object {
        fun of(source: String, ranges: List<IntRange>): CollapseMap {
            val collapsed = BooleanArray(source.length)
            ranges.forEach { range ->
                for (index in range.first..minOf(range.last, source.length - 1)) {
                    if (index >= 0) collapsed[index] = true
                }
            }
            val text = buildString {
                source.forEachIndexed { index, char -> if (!collapsed[index]) append(char) }
            }
            val prefix = IntArray(source.length + 1)
            for (index in source.indices) {
                prefix[index + 1] = prefix[index] + if (collapsed[index]) 0 else 1
            }
            return CollapseMap(text, prefix)
        }
    }
}

/**
 * 把公式与行内代码折叠成 0 宽。
 *
 * 与设计文档 §7 一致：非活动原子在布局里不占宽度，位置由覆盖层补画。
 * `lastMap` 留给试验屏读诊断数据。
 */
internal class CollapseTransformation(
    private val ranges: List<IntRange> = atomRanges(SPIKE_TEXT)
) : VisualTransformation {

    @Volatile
    var lastMap: CollapseMap? = null
        private set

    override fun filter(text: AnnotatedString): TransformedText {
        val map = CollapseMap.of(text.text, ranges)
        lastMap = map
        return TransformedText(AnnotatedString(map.text), map.offsetMapping())
    }
}
