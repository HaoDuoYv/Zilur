package com.example.zhilu.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.example.zhilu.data.datastore.AccentColor
import com.example.zhilu.domain.markup.InlineBrush
import com.example.zhilu.domain.model.CardAccent
import com.example.zhilu.domain.model.EmphasisTone

/**
 * 语义角色 → 颜色的映射。
 *
 * **色值不新写一行 hex**：直接派生自 [AccentPalette] 已有的 `primary`。
 * 之所以不另建一份色表：那 8 个值本来就与 `accentPaint(OCHRE/MOSS/CRIMSON/TEAL)` 逐值相同，
 * 抄第二遍只会让色值分散在两处，破坏"色相取自同一批值、让标签胶囊和强调色像一家人"的单一来源。
 */
val EmphasisTone.accent: AccentColor
    get() = when (this) {
        EmphasisTone.KEY -> AccentColor.OCHRE
        EmphasisTone.IDEA -> AccentColor.MOSS
        // 「注意」用正红：它是最需要一眼看到的角色，绛红那档低饱和玫瑰压不住
        EmphasisTone.WARN -> AccentColor.SCARLET
        EmphasisTone.TODO -> AccentColor.TEAL
    }

/**
 * 无障碍色板开关（设计文档 §3.9 第 3 条）。
 *
 * 默认关闭：默认色板的四个色相彼此区分得开，而且与主题强调色同源。
 * 开启后换成 [AccessibleToneBlock]/[AccessibleToneInk] 那套（见其上的说明）。
 *
 * 走 CompositionLocal 而不是逐层传参：语义色被用在块渲染、gutter、行内工具条、
 * 底部工具栏等 6 处，都在 `MainActivity` 的子树里；由 `ZhiLuTheme` 一处下发即可。
 */
val LocalAccessibleEmphasis = staticCompositionLocalOf { false }

/**
 * 无障碍语义色板，取自 **Okabe & Ito** 的色盲友好配色，并按**相对亮度**拉开档位：
 *
 * | 角色 | 色值 | 相对亮度 |
 * | --- | --- | --- |
 * | 要点 | `#E69F00` 橙 | ≈0.58 |
 * | 注意 | `#D55E00` 朱红 | ≈0.32 |
 * | 想法 | `#0072B2` 蓝 | ≈0.20 |
 * | 待办 | `#007A5E` 蓝绿 | ≈0.10 |
 *
 * 为什么是这四条：默认色板里「要点=赭黄 / 想法=苔绿」这一对在红绿色盲下几乎同色，
 * 而语义角色**必须能靠颜色本身分辨**（它是内容的分类，不是装饰）。Okabe-Ito 的色相
 * 在三种常见色觉障碍下都可区分；再叠加明度阶梯后，即使完全去色（灰度打印、
 * 重度色觉障碍）四个角色仍能靠深浅读出来。
 */
private fun accessibleToneBlock(tone: EmphasisTone, darkTheme: Boolean): Color = when (tone) {
    EmphasisTone.KEY -> if (darkTheme) Color(0xFFF0B429) else Color(0xFFE69F00)
    EmphasisTone.IDEA -> if (darkTheme) Color(0xFF56B4E9) else Color(0xFF0072B2)
    EmphasisTone.WARN -> if (darkTheme) Color(0xFFE8734A) else Color(0xFFD55E00)
    EmphasisTone.TODO -> if (darkTheme) Color(0xFF3FBFA0) else Color(0xFF007A5E)
}

/** 同一套色板的文字墨色：浅色主题压暗、深色主题提亮，保证正文对比度。 */
private fun accessibleToneInk(tone: EmphasisTone, darkTheme: Boolean): Color = when (tone) {
    EmphasisTone.KEY -> if (darkTheme) Color(0xFFF5D08A) else Color(0xFF4A3200)
    EmphasisTone.IDEA -> if (darkTheme) Color(0xFF9AD4F0) else Color(0xFF00405F)
    EmphasisTone.WARN -> if (darkTheme) Color(0xFFF0A98A) else Color(0xFF7A2E00)
    EmphasisTone.TODO -> if (darkTheme) Color(0xFF8FD8C4) else Color(0xFF00453A)
}

/** 取某个角色在指定主题下的**块级**用色（= 该 AccentColor 的 primary）。 */
fun emphasisToneColor(
    tone: EmphasisTone,
    darkTheme: Boolean,
    accessible: Boolean = false
): Color = if (accessible) {
    accessibleToneBlock(tone, darkTheme)
} else {
    accentRoles(tone.accent, darkTheme).primary
}

/**
 * 行内着色用的「加深墨色」。
 *
 * 块级标签是小面积、字号也小，用 primary 就够；但行内是 14.5px 正文里的小字，
 * 直接用 `#8A4550` 这类中低明度色做文字色，对比度不足（尤其压在 20% 底色上），
 * 需要再压一档明度才既醒目又不刺眼。因此这 4 个常量是**唯一**为语义色新增的颜色。
 */
@Immutable
data class EmphasisInkPalette(
    val key: Color,
    val idea: Color,
    val warn: Color,
    val todo: Color
) {
    fun of(tone: EmphasisTone): Color = when (tone) {
        EmphasisTone.KEY -> key
        EmphasisTone.IDEA -> idea
        EmphasisTone.WARN -> warn
        EmphasisTone.TODO -> todo
    }
}

private val LightEmphasisInk = EmphasisInkPalette(
    key = Color(0xFF5E431F),
    idea = Color(0xFF33513A),
    // 与"正红"同族但压得更暗：正文小字压在 20% 红底上要够黑才读得清
    warn = Color(0xFF8C2018),
    todo = Color(0xFF275250)
)

/**
 * 行内文字色：浅色主题用加深墨色；深色主题直接用 primary ——
 * `AccentPalette` 的深色 primary 本来就是"提亮后的版本"，再压明度会糊在深底上。
 */
fun emphasisInkColor(
    tone: EmphasisTone,
    darkTheme: Boolean,
    accessible: Boolean = false
): Color = when {
    accessible -> accessibleToneInk(tone, darkTheme)
    darkTheme -> accentRoles(tone.accent, true).primary
    else -> LightEmphasisInk.of(tone)
}

/**
 * 行内标记的 SpanStyle，**只读路径与编辑路径共用**。
 *
 * 两处必须用同一个函数，否则"编辑时看到的颜色"与"退出编辑后看到的颜色"会不一致 ——
 * 那是最容易被用户发现、也最难解释的一类不一致。
 */
fun inlineToneSpanStyle(
    tone: EmphasisTone,
    brush: InlineBrush,
    darkTheme: Boolean,
    accessible: Boolean = false
): SpanStyle {
    val ink = emphasisInkColor(tone, darkTheme, accessible)
    return when (brush) {
        InlineBrush.HIGHLIGHT -> SpanStyle(
            color = ink,
            background = emphasisToneColor(tone, darkTheme, accessible)
                .copy(alpha = AlphaTokens.InlineMark),
            fontWeight = FontWeight.SemiBold
        )

        InlineBrush.COLOR -> SpanStyle(
            color = ink,
            fontWeight = FontWeight.SemiBold
        )

        InlineBrush.UNDERLINE -> SpanStyle(
            color = ink,
            fontWeight = FontWeight.SemiBold,
            textDecoration = TextDecoration.Underline
        )

        // 加粗不带语义色：它不分类内容，只改字重
        InlineBrush.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    }
}

/**
 * 卡片身份色的**显式轮转序**（不是取模公式）。
 *
 * 色板本身的定义与理由在 `domain/model/CardAccent.kt`（它会被持久化，也会被 AI 工具读写）；
 * 这里只负责渲染：`accent == null` 表示"用户没改过" → 按卡片序号回退到轮转色。
 */
val CardAccentRotation: List<CardAccent> = CardAccent.ordered

/** 第 [index] 张卡片的默认身份色（argb）。 */
fun cardAccentAt(index: Int): Int = CardAccent.at(index).argb

/**
 * 卡片身份色在指定主题下的显示色。
 *
 * `stored == null` → 按卡片序号回退到轮转色。这样导入一份不带 `accent` 的备份后，
 * 外观仍然是合理的（设计文档 §9.3）。
 */
fun cardAccentColor(stored: Int?, index: Int, darkTheme: Boolean): Color {
    val raw = stored ?: cardAccentAt(index)
    return if (darkTheme) darkTagColor(raw) else Color(raw)
}
