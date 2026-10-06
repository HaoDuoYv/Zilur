package com.example.zhilu.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.zhilu.data.datastore.AccentColor
import com.example.zhilu.data.datastore.ThemePalette

/**
 * 强调色的 M3 角色组合（一套浅色或一套深色）。
 *
 * 之所以一次给四个角色而不是只给一个主色：主色单独挑好看没用，
 * 真正决定可用性的是「主色上的文字能不能看清」和「容器色上的标签能不能读」。
 * 四个角色必须成套设计——`onPrimary` 是跟着 `primary` 的对比色，
 * `primaryContainer` / `onPrimaryContainer` 同理，任何一项单独调都会在另一个语境下塌掉。
 */
@Immutable
data class AccentRoles(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color
)

/**
 * 一个强调色的完整定义：展示名 + 浅 / 深两套角色。
 *
 * 深色的那一套不是把浅色反过来用：`primary` 必须是**提亮后的**版本
 * （深底上的强调元素要够亮才立得住），而 `onPrimary` 反过来是深色调。
 * 直接沿用浅色主色会导致深色模式下的按钮暗成一团。
 */
@Immutable
data class AccentPaint(
    val label: String,
    val light: AccentRoles,
    val dark: AccentRoles
)

/**
 * 纸墨（默认外观）的 8 色强调色板。
 *
 * 色相取自 `TagColors` 已经在用的那一套（紫 / 蓝 / 青 / 绿 / 赭 / 红 / 灰），
 * 只是把明度压到当主色够用的程度——用户挑的强调色要和列表里花花绿绿的标签胶囊
 * 看起来像一家人，而不是两个调色师各画一半。
 *
 * 只换 `primary` 这一族，`secondary`（暖褐）与 `tertiary`（橄榄）保持不动。
 * 这两个角色在纸墨主题里其实是「次级中性色」，承担的是次级信息的层次，
 * 跟着强调色一起变会把整个界面染成一个颜色，纸墨的中性底子就没了。
 *
 * **动森外观不走这里**：它自带一套同键不同值的柔和色，见 `ThemePalettes.kt`。
 * 两者键一一对应，所以切换外观不会丢用户已选的档位。
 */
internal fun paperInkAccentPaint(accent: AccentColor): AccentPaint = when (accent) {
    AccentColor.INK -> AccentPaint(
        label = "墨蓝",
        light = AccentRoles(
            primary = Color(0xFF3D4F6B),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE3E8F0),
            onPrimaryContainer = Color(0xFF1E2A3D)
        ),
        dark = AccentRoles(
            primary = Color(0xFFA8B8D2),
            onPrimary = Color(0xFF1E2A3D),
            primaryContainer = Color(0xFF2F3D53),
            onPrimaryContainer = Color(0xFFD8DFEA)
        )
    )

    AccentColor.VIOLET -> AccentPaint(
        label = "黛紫",
        light = AccentRoles(
            primary = Color(0xFF5F4F80),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE7E2F0),
            onPrimaryContainer = Color(0xFF2A2140)
        ),
        dark = AccentRoles(
            primary = Color(0xFFC6B9DE),
            onPrimary = Color(0xFF2A2140),
            primaryContainer = Color(0xFF3C3355),
            onPrimaryContainer = Color(0xFFE2DCEE)
        )
    )

    AccentColor.TEAL -> AccentPaint(
        label = "松石",
        light = AccentRoles(
            primary = Color(0xFF3D6E6B),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFDFEAE9),
            onPrimaryContainer = Color(0xFF1B3331)
        ),
        dark = AccentRoles(
            primary = Color(0xFFA5D2CF),
            onPrimary = Color(0xFF12302E),
            primaryContainer = Color(0xFF2C4745),
            onPrimaryContainer = Color(0xFFD4E7E5)
        )
    )

    AccentColor.MOSS -> AccentPaint(
        label = "苔绿",
        light = AccentRoles(
            primary = Color(0xFF4B6B4F),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE1EADD),
            onPrimaryContainer = Color(0xFF223522)
        ),
        dark = AccentRoles(
            primary = Color(0xFFAFCDB2),
            onPrimary = Color(0xFF16301A),
            primaryContainer = Color(0xFF334B35),
            onPrimaryContainer = Color(0xFFDCE9DD)
        )
    )

    AccentColor.OCHRE -> AccentPaint(
        label = "赭土",
        light = AccentRoles(
            primary = Color(0xFF7C5C31),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFEFE3D2),
            onPrimaryContainer = Color(0xFF3A2A12)
        ),
        dark = AccentRoles(
            primary = Color(0xFFDCC09A),
            onPrimary = Color(0xFF3A2A12),
            primaryContainer = Color(0xFF4C3A22),
            onPrimaryContainer = Color(0xFFEDDFCB)
        )
    )

    AccentColor.CRIMSON -> AccentPaint(
        label = "绛红",
        light = AccentRoles(
            primary = Color(0xFF8A4550),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFF3DFE1),
            onPrimaryContainer = Color(0xFF3F161C)
        ),
        dark = AccentRoles(
            primary = Color(0xFFEDB9BF),
            onPrimary = Color(0xFF3F161C),
            primaryContainer = Color(0xFF52262C),
            onPrimaryContainer = Color(0xFFF1DADE)
        )
    )

    AccentColor.GRAPHITE -> AccentPaint(
        label = "石墨",
        light = AccentRoles(
            primary = Color(0xFF4A4A46),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFE5E4E0),
            onPrimaryContainer = Color(0xFF2B2B28)
        ),
        dark = AccentRoles(
            primary = Color(0xFFC7C6C0),
            onPrimary = Color(0xFF2B2B28),
            primaryContainer = Color(0xFF3A3A36),
            onPrimaryContainer = Color(0xFFE5E4E0)
        )
    )

    // 正红：色板里唯一"喊出来"的颜色。
    // 既有的绛红 `#8A4550` 是低饱和玫瑰调，当"注意"标记够用，但当强调色时压不住场；
    // 正红补的就是这一档 —— 需要一眼看到重点时选它。
    // 它同时是「注意」语义色的来源（见 EmphasisTones.accent），所以
    // 卡片身份色**不能**再用这个值（一色一义），那一档是更暗的朱红 `#B23B32`。
    AccentColor.SCARLET -> AccentPaint(
        label = "正红",
        light = AccentRoles(
            primary = Color(0xFFC0392B),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFF7DEDB),
            onPrimaryContainer = Color(0xFF4A1310)
        ),
        dark = AccentRoles(
            primary = Color(0xFFF2938A),
            onPrimary = Color(0xFF4A1310),
            primaryContainer = Color(0xFF5C1F19),
            onPrimaryContainer = Color(0xFFF7DAD6)
        )
    )
}

/**
 * 取某一套（浅 / 深）的角色色。
 *
 * 用**当前外观**的色板：`accentRoles(CRIMSON, …)` 在纸墨下是绛红、在动森下是樱花粉。
 */
fun accentRoles(
    accent: AccentColor,
    darkTheme: Boolean,
    palette: ThemePalette = ThemePalette.DEFAULT
): AccentRoles {
    val paint = palettePaint(palette).accent(accent)
    return if (darkTheme) paint.dark else paint.light
}
