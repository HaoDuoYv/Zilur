package com.example.zhilu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.zhilu.data.datastore.AccentColor
import com.example.zhilu.data.datastore.ThemePalette
import com.example.zhilu.domain.model.CardAccent
import com.example.zhilu.domain.model.EmphasisTone

/**
 * 动森主题配色。
 *
 * 观感取自 `liuyuhong0324/AnimalIslandUI`（动森风 UI 组件库）那一类设计：
 * **奶油暖白底 + 草绿主色 + 桃粉/天蓝点缀 + 大圆角**，高饱和只用在点缀上。
 *
 * 两套色都按 M3 角色成套给出，理由同 `AccentPalette`：`on*` 是跟着底色定的对比色，
 * 单独调任何一项都会在另一处塌掉（深色模式下的按钮最容易暗成一团）。
 *
 * 深色那一套不是把浅色反过来：动森风在暗色下要走"夜晚的岛"——偏蓝的深墨绿底、
 * 主色提亮成嫩叶绿，而不是把奶油底压黑。
 */

// ── 动森 · 浅色（白天的岛）─────────────────────────────────────────────
/**
 * 主色：**青绿 teal**，取自参考仓库 `AnimalIslandUI` 的 `theme/Color.kt`：
 * `PrimaryColor = #19C8B9`。
 *
 * 但**没有直接用原值**：`#19C8B9` 配白字只有 **2.10**、配深棕字 3.38，两条都不达标。
 * 这里按同一色相压暗到 `#0F766D`（白字 **5.48**）。观感仍是那支游戏里的青绿，
 * 只是够深到能承载文字 —— 参考实现是展示型 UI，不承担正文对比度，我们承担。
 */
private val LeafLight = Color(0xFF0F766D)
private val OnLeafLight = Color(0xFFFFFFFF)
private val LeafContainerLight = Color(0xFFE6F9F6)   // = 参考仓库 PrimaryColorBg
private val OnLeafContainerLight = Color(0xFF0B3A36)

/**
 * 次级：暖桃，取自参考仓库 `WarmPeachPink = #E18C6F`。
 *
 * 同样压暗（原值当小标记只有 2.40）。`#B37059` 是保持色相下、压到 3.66 的那一档。
 * 配深墨字而不是白字 —— 见 [OnPeachLight]。
 */
private val PeachLight = Color(0xFF8D5745)
private val OnPeachLight = Color(0xFFFFFFFF)
private val PeachContainerLight = Color(0xFFFBE4DA)
private val OnPeachContainerLight = Color(0xFF4A241A)

/**
 * 三级：侧栏蓝，取自参考仓库 `SidebarActiveBg = #B7C6E5`。
 *
 * 那个值太亮（当填充只有 1.7 上下），取同色相的深一档 `#4E6E9E` 才能承载文字。
 */
private val SkyLight = Color(0xFF4E6E9E)
private val OnSkyLight = Color(0xFFFFFFFF)
private val SkyContainerLight = Color(0xFFDCE4F2)
private val OnSkyContainerLight = Color(0xFF23334D)

/**
 * 页面底：**米白**，直接用参考仓库的 `BgColor = #F8F8F0`。
 *
 * 原来我用的是 `#FBF6E9`（凭观感调的奶油色）。参考值是**偏绿的米白**，
 * 和那支青绿主色是配套的 —— 这正是"按仓库实现"该对齐的地方。
 */
private val CreamLight = Color(0xFFF8F8F0)

/**
 * 正文：**暖褐**，直接用参考仓库的 `TextColor = #794F27`。
 *
 * 这是整套观感里最像"动森"的一笔 —— 不是灰黑，而是带暖调的褐。
 * 配米白底 6.65，比灰黑那种冷调更有纸质气。
 */
private val OnCreamLight = Color(0xFF794F27)

/** 卡面：参考仓库 `BgColorContent = #F7F3DF`。 */
private val CardLight = Color(0xFFF7F3DF)

/** 凹陷面：参考仓库 `BgColorSecondary = #F0E8D8`。 */
private val SunkenLight = Color(0xFFF0E8D8)

/**
 * 凹陷面上的次级文字。
 *
 * 参考仓库的 `TextColorSecondary = #9F927D` 只有 **2.86**（远低于 4.5），
 * 所以取同色相压暗的 `#6D6455`（配凹陷面 4.78、配页面底 5.46）。
 * 保留"灰褐色"的气质，只是能读。
 */
private val OnSunkenLight = Color(0xFF6D6455)

/** 描边：参考仓库 `BorderColorLight = #C4B89E` / `BgColorDisabled = #F0ECE2`。 */
private val OutlineAnimalLight = Color(0xFFC4B89E)
private val HairlineAnimalLight = Color(0xFFE3DCCB)

/**
 * 错误色：参考仓库 `ErrorColor = #E05A5A`。
 *
 * 原值小标记压底 3.40（够），但配白字只有 3.63（不够）。这里保留色相压到
 * `#C94444`（= 参考仓库的 `ErrorColorActive`）配白字 **4.75**。
 * 顺带一提：**这一档终于和「注意」语义色拉开了**。
 */
private val ErrorAnimalLight = Color(0xFFC94444)
private val OnErrorAnimalLight = Color(0xFFFFFFFF)
private val ErrorContainerAnimalLight = Color(0xFFFADFD8)
private val OnErrorContainerAnimalLight = Color(0xFF4A170E)

// ── 动森 · 深色（夜晚的岛）─────────────────────────────────────────────
// 参考仓库只有浅色一套（`values-night` 里没有配色），所以深色是**按它的品牌色相推的**：
// 青绿主色提亮、底色取"夜色暖褐"（而不是把米白压黑）。正文仍用暖调，保持纸感。
private val LeafDark = Color(0xFF3FD9C8)         // 提亮后的 #19C8B9
private val OnLeafDark = Color(0xFF00302B)
private val LeafContainerDark = Color(0xFF17403B)
private val OnLeafContainerDark = Color(0xFFB6EFE7)

private val PeachDark = Color(0xFFF0B49C)         // 提亮后的 #E18C6F
private val OnPeachDark = Color(0xFF4A241A)
private val PeachContainerDark = Color(0xFF56332A)
private val OnPeachContainerDark = Color(0xFFF7DCD1)

private val SkyDark = Color(0xFFAEC3E2)           // 提亮后的 #B7C6E5
private val OnSkyDark = Color(0xFF243247)
private val SkyContainerDark = Color(0xFF2E3B52)
private val OnSkyContainerDark = Color(0xFFDCE4F2)

/** 夜色：暖褐调的深底，与白天的米白 + 暖褐正文是同一族色相的降调。 */
private val CreamDark = Color(0xFF231C15)
private val OnCreamDark = Color(0xFFEFE3D2)
private val CardDark = Color(0xFF2E251C)
private val SunkenDark = Color(0xFF3A2F24)
private val OnSunkenDark = Color(0xFFC4B8A0)

private val OutlineAnimalDark = Color(0xFF4C544A)
private val HairlineAnimalDark = Color(0xFF333A33)

private val ErrorAnimalDark = Color(0xFFF0A79A)
private val OnErrorAnimalDark = Color(0xFF5C1C12)
private val ErrorContainerAnimalDark = Color(0xFF7A2A1E)
private val OnErrorContainerAnimalDark = Color(0xFFFADFD8)

// ── 动森：身份色轮转（取自参考仓库的 App 调色板）──────────────────────
/**
 * 卡片身份色的轮转序。
 *
 * 直接取参考仓库 `Color.kt` 的 `App*` 那一组（NookPhone 图标色）——
 * 它们本来就是"同一台设备上并列出现的 App 图标色"，用在这里再合适不过，
 * 也省得我另调一套。
 */
val AnimalIslandCardAccents = listOf(
    0xFFE59266.toInt(), // 暖橙（AppOrange）
    0xFF8AC68A.toInt(), // 嫩芽绿（AppGreen）
    0xFF889DF0.toInt(), // 湖水蓝（AppBlue）
    0xFFF7CD67.toInt(), // 阳光黄（AppYellow）
    0xFFB77DEE.toInt(), // 绣球紫（AppPurple）
    0xFF82D5BB.toInt(), // 薄荷青（AppTeal）
    0xFF9A835A.toInt()  // 燕麦棕（AppBrown）
)

/** 动森：标签胶囊色。低饱和改成"粉彩感"——降饱和但**提明度**，落在奶油底上才不发灰。 */
val AnimalIslandTagColors = listOf(
    0xFFCE8FA8.toInt(), // 樱花粉
    0xFF8FA8CE.toInt(), // 湖水蓝
    0xFF8FCE9A.toInt(), // 嫩芽绿
    0xFFD4B483.toInt(), // 燕麦黄
    0xFFCE9A8F.toInt(), // 陶土
    0xFF8FCFC9.toInt(), // 浅薄荷
    0xFFA9A392.toInt()  // 温灰
)

/** 动森：暖色投影，不是中性黑。用中性黑会在奶油底上显脏。 */
private val AnimalShadowLight = Color(0x1A5C4A2E)
private val AnimalShadowDark = Color(0x73000000)

/** 动森：圆角整体放大约 1.6 倍，"一切都是圆的"是这套观感的一半。 */
private val AnimalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

/**
 * 一套外观的完整定义。
 *
 * [PalettePaint.accent] 与 [PalettePaint.toneBlock] 都在这里：**强调色的具体色值必须
 * 跟着外观走**。纸墨的墨蓝/绛红放到动森上会像没换完主题，所以动森自带一套同名的柔和色
 * （枚举键复用，存储值不变——用户切外观不会丢已选的强调色档位）。
 */
@Immutable
data class PalettePaint(
    val label: String,
    val light: ColorScheme,
    val dark: ColorScheme,
    val lightExtended: ExtendedColors,
    val darkExtended: ExtendedColors,
    val shapes: Shapes,
    /**
     * 组件描边色。**纸墨为「无」**（[Color.Unspecified]）——它靠阴影分层，描边正是
     * 当初"表格感"的来源、已从全站退场；动森反过来**靠描边立形状**，这是参考仓库
     * 最显眼的特征之一（按钮 / 输入框 / 标签 / 卡片全带 2~2.5dp 边框）。
     */
    val componentBorder: Color,
    /** 卡片投影色。动森用**暖褐**而不是中性黑，否则压在米白底上会发脏。 */
    val cardShadow: Color,
    /**
     * 代码块的面 / 字 / 描边。
     *
     * **两套外观都让代码块保持"深色终端"**（参考仓库就是这么做的：`#2B2118` 底 + `#E8D5BC` 字，
     * 而它的浅色主题也是这套）。理由是代码块**越像另一个世界越好**——它和正文的性质不同，
     * 用浅底会跟周围的卡片糊在一起。
     *
     * 顺带修掉纸墨原来的问题：它之前用 `surfaceVariant` 当代码底，也就是**灰底灰字**，
     * 跟卡片只差一档明度、几乎看不出是个代码块。
     */
    val codeSurface: Color,
    val onCodeSurface: Color,
    val codeBorder: Color,
    val cardAccents: List<Int>,
    val tagColors: List<Int>,
    /**
     * 四个语义角色的**小标记色**（块级色条、gutter 圆点、设置页色板预览）。
     *
     * 为什么不能直接用 accent 的 `primary`：两者要满足的对比关系**不同**。
     * `primary` 是"填充 + 上面的字"，动森的「蜂蜜」`#D9A441` 配深字很合适（6.16），
     * 但它压在奶油底上只有 **2.08** —— 当色条就是"几乎看不见"（真机对比纸墨是 5.67）。
     * 所以小标记另取一档更暗的暖黄 `#A0741C`（3.04~3.88）。
     *
     * 纸墨那套两者恰好同值（它的 `primary` 本来就够深），这不是巧合而是中性底子决定的；
     * 动森是高饱和点缀，必须分开。
     */
    val toneBlocks: Map<EmphasisTone, ToneBlockColors>,
    private val accents: Map<AccentColor, AccentPaint>
) {
    fun scheme(darkTheme: Boolean): ColorScheme = if (darkTheme) dark else light
    fun extended(darkTheme: Boolean): ExtendedColors = if (darkTheme) darkExtended else lightExtended
    fun accent(accent: AccentColor): AccentPaint = accents[accent] ?: accents.getValue(AccentColor.DEFAULT)

    /** 某个语义角色在当前明暗下的小标记色。 */
    fun toneBlock(tone: EmphasisTone, darkTheme: Boolean): Color {
        val colors = toneBlocks.getValue(tone)
        return if (darkTheme) colors.dark else colors.light
    }
}

/** 一个语义角色在浅 / 深两套方案下的小标记色。 */
@Immutable
data class ToneBlockColors(val light: Color, val dark: Color)

private val PaperInkLight = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight,
    onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    // M3 **组件**会读的 surface* 角色：不显式给就会落到 material3 默认的淡紫上
    // （真机症状：底部弹层底部一条 `#F7F2FA`）。纸墨按它自己的"暖纸"层级给。
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = SurfaceLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerHigh = SurfaceVariantLight,
    surfaceContainerHighest = SurfaceVariantLight,
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = SurfaceVariantLight,
    inverseSurface = Color(0xFF2F2C27),
    inverseOnSurface = Color(0xFFF3F0E9),
    inversePrimary = PrimaryLight,
    surfaceTint = PrimaryLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

private val PaperInkDark = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark,
    onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    surfaceContainerLowest = Color(0xFF141413),
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceVariantDark,
    surfaceContainerHighest = SurfaceVariantDark,
    surfaceBright = Color(0xFF33322E),
    surfaceDim = Color(0xFF101010),
    inverseSurface = Color(0xFFF2F0E9),
    inverseOnSurface = Color(0xFF2F2C27),
    inversePrimary = PrimaryDark,
    surfaceTint = PrimaryDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val AnimalIslandLight = lightColorScheme(
    primary = LeafLight,
    onPrimary = OnLeafLight,
    primaryContainer = LeafContainerLight,
    onPrimaryContainer = OnLeafContainerLight,
    secondary = PeachLight,
    onSecondary = OnPeachLight,
    secondaryContainer = PeachContainerLight,
    onSecondaryContainer = OnPeachContainerLight,
    tertiary = SkyLight,
    onTertiary = OnSkyLight,
    tertiaryContainer = SkyContainerLight,
    onTertiaryContainer = OnSkyContainerLight,
    background = CreamLight,
    onBackground = OnCreamLight,
    surface = CardLight,
    onSurface = OnCreamLight,
    surfaceVariant = SunkenLight,
    onSurfaceVariant = OnSunkenLight,
    outline = OutlineAnimalLight,
    outlineVariant = HairlineAnimalLight,
    // ↓ M3 的**组件**会读这几个角色（底部弹层 `surfaceContainerLow`、菜单 `surfaceContainer`、
    //   对话框 `surfaceContainerHigh`、滚动条 `surfaceBright`…）。不显式给就会落到
    //   material3 的默认值上 —— 那套默认是**淡紫**，跟动森/纸墨都不搭。
    //   真机症状：新建弹层底部一条 `#F7F2FA` 的淡紫。层级按"离页面底越近越亮"给。
    surfaceContainerLowest = Color(0xFFFFFDF7),
    surfaceContainerLow = CardLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = SunkenLight,
    surfaceContainerHighest = SunkenLight,
    surfaceBright = CardLight,
    surfaceDim = SunkenLight,
    inverseSurface = Color(0xFF3A2F24),
    inverseOnSurface = Color(0xFFF7F3DF),
    inversePrimary = Color(0xFF3FD9C8),
    surfaceTint = Color(0xFF0F766D),
    error = ErrorAnimalLight,
    onError = OnErrorAnimalLight,
    errorContainer = ErrorContainerAnimalLight,
    onErrorContainer = OnErrorContainerAnimalLight
)

private val AnimalIslandDark = darkColorScheme(
    primary = LeafDark,
    onPrimary = OnLeafDark,
    primaryContainer = LeafContainerDark,
    onPrimaryContainer = OnLeafContainerDark,
    secondary = PeachDark,
    onSecondary = OnPeachDark,
    secondaryContainer = PeachContainerDark,
    onSecondaryContainer = OnPeachContainerDark,
    tertiary = SkyDark,
    onTertiary = OnSkyDark,
    tertiaryContainer = SkyContainerDark,
    onTertiaryContainer = OnSkyContainerDark,
    background = CreamDark,
    onBackground = OnCreamDark,
    surface = CardDark,
    onSurface = OnCreamDark,
    surfaceVariant = SunkenDark,
    onSurfaceVariant = OnSunkenDark,
    outline = OutlineAnimalDark,
    outlineVariant = HairlineAnimalDark,
    // 同浅色那组：不给就会落到 material3 的淡紫默认值上
    surfaceContainerLowest = Color(0xFF1C1610),
    surfaceContainerLow = CardDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = SunkenDark,
    surfaceContainerHighest = SunkenDark,
    surfaceBright = Color(0xFF3A2F24),
    surfaceDim = Color(0xFF181310),
    inverseSurface = Color(0xFFEFE3D2),
    inverseOnSurface = Color(0xFF2E251C),
    inversePrimary = Color(0xFF0F766D),
    surfaceTint = Color(0xFF3FD9C8),
    error = ErrorAnimalDark,
    onError = OnErrorAnimalDark,
    errorContainer = ErrorContainerAnimalDark,
    onErrorContainer = OnErrorContainerAnimalDark
)

private val AnimalIslandExtendedLight = ExtendedColors(
    inkBody = Color(0xFF4A4638),
    inkFaint = Color(0xFFA79E88),
    hairline = HairlineAnimalLight,
    scrim = Color(0x52000000),
    shadowInk = AnimalShadowLight,
    isDark = false
)

private val AnimalIslandExtendedDark = ExtendedColors(
    inkBody = Color(0xFFD6D0C0),
    inkFaint = Color(0xFF847E6F),
    hairline = HairlineAnimalDark,
    scrim = Color(0x52000000),
    shadowInk = AnimalShadowDark,
    isDark = true
)

/**
 * 动森那套强调色。
 *
 * 键与纸墨**一一对应**（[AccentColor] 的 7 档），只是色值换成柔和版：
 * 叶子绿占掉 `INK`（原墨蓝）那一档——它在两套主题里都是"最中性的那个默认选择"。
 */
private val AnimalIslandAccents: Map<AccentColor, AccentPaint> = mapOf(
    AccentColor.INK to AccentPaint(
        label = "青绿",
        light = AccentRoles(
            // 与浅色方案主色同值（= 参考仓库 PrimaryColor `#19C8B9` 的可用版本）：
            // 两处不一致会让"用默认强调色"和"选青绿"渲染出不同颜色。
            primary = Color(0xFF0F766D),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE6F9F6),
            onPrimaryContainer = Color(0xFF0B3A36)
        ),
        dark = AccentRoles(
            primary = Color(0xFF3FD9C8),
            onPrimary = Color(0xFF00302B),
            primaryContainer = Color(0xFF17403B),
            onPrimaryContainer = Color(0xFFB6EFE7)
        )
    ),
    AccentColor.VIOLET to AccentPaint(
        label = "绣球紫",
        light = AccentRoles(
            // = 参考仓库 AppPurple `#B77DEE`，配深字
            primary = Color(0xFFB77DEE),
            onPrimary = Color(0xFF33204A),
            primaryContainer = Color(0xFFEDE2F7),
            onPrimaryContainer = Color(0xFF33204A)
        ),
        dark = AccentRoles(
            primary = Color(0xFFC9AFE6),
            onPrimary = Color(0xFF33204A),
            primaryContainer = Color(0xFF453259),
            onPrimaryContainer = Color(0xFFE8DCF5)
        )
    ),
    AccentColor.TEAL to AccentPaint(
        label = "薄荷",
        light = AccentRoles(
            // = 参考仓库 AppTeal `#82D5BB`，配深字
            primary = Color(0xFF82D5BB),
            onPrimary = Color(0xFF123A36),
            primaryContainer = Color(0xFFD8F0ED),
            onPrimaryContainer = Color(0xFF123A36)
        ),
        dark = AccentRoles(
            primary = Color(0xFF8FD8D1),
            onPrimary = Color(0xFF0E322E),
            primaryContainer = Color(0xFF2A4D49),
            onPrimaryContainer = Color(0xFFD2EDEA)
        )
    ),
    AccentColor.MOSS to AccentPaint(
        label = "嫩芽绿",
        light = AccentRoles(
            // = 参考仓库 AppGreen `#8AC68A`。中等明度，配深字（白字只有 2.0 上下）
            primary = Color(0xFF8AC68A),
            onPrimary = Color(0xFF1E331E),
            primaryContainer = Color(0xFFE6F0D5),
            onPrimaryContainer = Color(0xFF2A3D14)
        ),
        dark = AccentRoles(
            primary = Color(0xFFB8D68C),
            onPrimary = Color(0xFF26380F),
            primaryContainer = Color(0xFF3D5222),
            onPrimaryContainer = Color(0xFFDFEDC8)
        )
    ),
    AccentColor.OCHRE to AccentPaint(
        label = "阳光黄",
        light = AccentRoles(
            // = 参考仓库 AppYellow `#F7CD67`，配深字 6.7
            primary = Color(0xFFF7CD67),
            onPrimary = Color(0xFF3A2A08),
            primaryContainer = Color(0xFFFBEFD2),
            onPrimaryContainer = Color(0xFF4A3609)
        ),
        dark = AccentRoles(
            primary = Color(0xFFFBE0A0),
            onPrimary = Color(0xFF3A2A08),
            primaryContainer = Color(0xFF57431A),
            onPrimaryContainer = Color(0xFFF8E6C0)
        )
    ),
    AccentColor.CRIMSON to AccentPaint(
        label = "樱花粉",
        light = AccentRoles(
            // = 参考仓库 AppPink `#F8A6B2`，配深字
            primary = Color(0xFFF8A6B2),
            onPrimary = Color(0xFF4A1F2A),
            primaryContainer = Color(0xFFFBE2E8),
            onPrimaryContainer = Color(0xFF4A1F2A)
        ),
        dark = AccentRoles(
            primary = Color(0xFFF0AEC0),
            onPrimary = Color(0xFF4A1F2A),
            primaryContainer = Color(0xFF5C3140),
            onPrimaryContainer = Color(0xFFF8DDE4)
        )
    ),
    AccentColor.GRAPHITE to AccentPaint(
        label = "温灰",
        light = AccentRoles(
            primary = Color(0xFF736D60),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEAE5D9),
            onPrimaryContainer = Color(0xFF332F26)
        ),
        dark = AccentRoles(
            primary = Color(0xFFC6BFB0),
            onPrimary = Color(0xFF332F26),
            primaryContainer = Color(0xFF453F34),
            onPrimaryContainer = Color(0xFFE8E2D5)
        )
    ),
    AccentColor.SCARLET to AccentPaint(
        label = "暖橙",
        light = AccentRoles(
            // = 参考仓库 AppOrange `#E59266`，配深字
            primary = Color(0xFFE59266),
            onPrimary = Color(0xFF4A1A08),
            primaryContainer = Color(0xFFFBE0DA),
            onPrimaryContainer = Color(0xFF4A180F)
        ),
        dark = AccentRoles(
            primary = Color(0xFFF2A392),
            onPrimary = Color(0xFF4A180F),
            primaryContainer = Color(0xFF5C2A1F),
            onPrimaryContainer = Color(0xFFF8DCD5)
        )
    )
)

private val PaperInkAccents: Map<AccentColor, AccentPaint> =
    AccentColor.entries.associateWith { paperInkAccentPaint(it) }

/** 卡片身份色的回退轮转序（用户没改过时按序号取）。 */
private val PaperInkCardAccents: List<Int> = CardAccent.ordered.map { it.argb }

/**
 * 纸墨的语义小标记色：**直接等于 accent 的 primary**。
 *
 * 纸墨的 primary 本来就够深（要点 `#7C5C31` on 纸底 5.76），没有"填充好看、当标记看不见"
 * 的矛盾，所以不另取色 —— 少一份要维护的色值。
 */
private val PaperInkToneBlocks: Map<EmphasisTone, ToneBlockColors> = EmphasisTone.entries.associateWith { tone ->
    val roles = paperInkAccentPaint(tone.accent)
    ToneBlockColors(light = roles.light.primary, dark = roles.dark.primary)
}

/**
 * 动森的语义小标记色。
 *
 * 色相全部换成**参考仓库的品牌族**：要点=FocusYellow、想法=SuccessColor、注意=ErrorColor、
 * 待办=PrimaryColor。但四个都**取压暗版**，因为原值当色条压在米白底上全都不够
 * （FocusYellow 1.55、SuccessColor 2.25、PrimaryColor 1.6 上下）。
 *
 * 门槛 3.0（图形按 WCAG 非文本要求），由 `toneBlockColorsAreVisibleOnEverySurface` 守着。
 */
private val AnimalIslandToneBlocks: Map<EmphasisTone, ToneBlockColors> = mapOf(
    // FocusYellow #FFCC00 的压暗版
    EmphasisTone.KEY to ToneBlockColors(light = Color(0xFF8A6A0F), dark = Color(0xFFF7CD67)),
    // SuccessColor #6FBA2C 的压暗版
    EmphasisTone.IDEA to ToneBlockColors(light = Color(0xFF4C7A1D), dark = Color(0xFFA8D97A)),
    // ErrorColor #E05A5A 的压暗版
    EmphasisTone.WARN to ToneBlockColors(light = Color(0xFFB4423F), dark = Color(0xFFF2A392)),
    // PrimaryColor #19C8B9 的压暗版
    EmphasisTone.TODO to ToneBlockColors(light = Color(0xFF11655F), dark = Color(0xFF67D9CC))
)

/** 取某套外观的完整定义。 */
fun palettePaint(palette: ThemePalette): PalettePaint = when (palette) {
    ThemePalette.PAPER_INK -> PalettePaint(
        label = "纸墨",
        light = PaperInkLight,
        dark = PaperInkDark,
        lightExtended = LightExtendedColors,
        darkExtended = DarkExtendedColors,
        shapes = AppShapes,
        // 纸墨**无描边**：它靠阴影分层（描边是当初"表格感"的来源，已从全站退场）
        componentBorder = Color.Unspecified,
        cardShadow = Color(0x1F000000),
        // 纸墨原来是"灰底灰字"（代码底 = surfaceVariant），跟卡片只差一档明度、
        // 几乎看不出是个代码块。改成深色终端，两套外观的代码块才是同一个"另一个世界"。
        codeSurface = Color(0xFF26241F),
        onCodeSurface = Color(0xFFE8E4D8),
        codeBorder = Color(0xFF3A3730),
        cardAccents = PaperInkCardAccents,
        tagColors = TagColors,
        toneBlocks = PaperInkToneBlocks,
        accents = PaperInkAccents
    )

    ThemePalette.ANIMAL_ISLAND -> PalettePaint(
        label = "动森",
        light = AnimalIslandLight,
        dark = AnimalIslandDark,
        lightExtended = AnimalIslandExtendedLight,
        darkExtended = AnimalIslandExtendedDark,
        shapes = AnimalShapes,
        // 动森反过来**靠描边立形状**，这是参考仓库最显眼的特征
        componentBorder = Color(0xFFC4B89E),   // = 参考仓库 BorderColorLight
        cardShadow = Color(0x596B5C43),        // 暖褐投影，不是中性黑
        // 参考仓库 `AnimalCodeBlock` 的原值：#2B2118 底 + #E8D5BC 字（配字对比 11.01）
        codeSurface = Color(0xFF2B2118),
        onCodeSurface = Color(0xFFE8D5BC),
        codeBorder = Color(0xFF3D3028),        // 参考仓库的边框色
        cardAccents = AnimalIslandCardAccents,
        tagColors = AnimalIslandTagColors,
        toneBlocks = AnimalIslandToneBlocks,
        accents = AnimalIslandAccents
    )
}

/** 外观的展示名（设置页用），不需要构造整份色板。 */
fun paletteLabel(palette: ThemePalette): String = when (palette) {
    ThemePalette.PAPER_INK -> "纸墨"
    ThemePalette.ANIMAL_ISLAND -> "动森"
}

/** 外观的一句话说明（挑外观时用得上，光看名字不知道差在哪）。 */
fun paletteDescription(palette: ThemePalette): String = when (palette) {
    ThemePalette.PAPER_INK -> "暖纸底 + 墨蓝，克制的编辑式排版"
    ThemePalette.ANIMAL_ISLAND -> "米白底 + 青绿，圆润活泼的岛屿风"
}
