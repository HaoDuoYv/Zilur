package com.example.zhilu.domain.model

/**
 * 卡片身份色板。
 *
 * 放在 domain 而不是 ui：它是**会被持久化的数据**（`NoteCardEntity.accent` 存的就是 [argb]），
 * 而且 AI 工具也要按名字读写它。渲染（深色提亮、透明度）才是 UI 的事。
 *
 * 色值取自 `TagColors` 的低饱和批次 —— **刻意不用 `AccentPalette.primary`**：
 * 赭土 `#7C5C31` / 苔绿 `#4B6B4F` / 绛红 `#8A4550` / 松石 `#3D6E6B` 这 4 个 primary
 * 与 4 个语义角色[EmphasisTone]**逐值相同**，"卡片 02 的心线"会和"这张卡里某条小点的要点标记"
 * 撞成一个颜色，直接违反"一色一义"。这一批是同一色相家族的低饱和版本：
 * 值与语义色不同、与标签胶囊一致，而且"身份色比内容色更淡"正好符合"容器让位于内容"。
 *
 * **声明顺序就是默认轮转序**（见 [ordered]），不是随手排的：
 * 前 5 项覆盖 5 个不同色族（蓝 / 赭 / 青 / 红 / 绿），覆盖绝大多数笔记的卡片数。
 * `(i × 3) % 7` 这类取模公式按真实色板代入后，前 4 张会落在苔绿 / 石墨 / 松石
 * 这段低对比区间，恰好没达成它自己的目标。
 *
 * 变体名（`ink`/`ochre`/…）是给 AI 工具用的稳定标识，**不要改**，改动等于改协议。
 */
enum class CardAccent(val argb: Int, val label: String) {
    /** 墨蓝。 */
    INK(0xFF4A5C7A.toInt(), "墨蓝"),

    /** 赭土。 */
    OCHRE(0xFF8A6B3A.toInt(), "赭土"),

    /** 松石。 */
    TEAL(0xFF4A7A7A.toInt(), "松石"),

    /** 绛红。 */
    CRIMSON(0xFF8A4A5B.toInt(), "绛红"),

    /** 苔绿。 */
    MOSS(0xFF4A6B5B.toInt(), "苔绿"),

    /** 石墨。 */
    GRAPHITE(0xFF6B6B6B.toInt(), "石墨"),

    /** 黛紫。 */
    VIOLET(0xFF6B5B8A.toInt(), "黛紫"),

    /**
     * 朱红：色板里唯一的正红档，用来标"这节最重要"。
     *
     * 刻意比「注意」语义色（正红 `#C0392B`）更暗一档：语义色是**内容**上的标记，
     * 身份色是**容器**上的标记，两者撞值会让"这条小点被标了注意"和"这张卡是红色身份"
     * 变成同一个视觉信号（一色一义）。
     *
     * **追加在末尾**：声明顺序就是轮转序，插在中间会改变已有笔记里卡片的默认配色。
     */
    VERMILION(0xFFB23B32.toInt(), "朱红");

    companion object {
        /** 默认轮转序。 */
        val ordered: List<CardAccent> = entries

        /** 用户没改过时，第 [index] 张卡片的默认身份色。 */
        fun at(index: Int): CardAccent {
            val size = entries.size
            return entries[((index % size) + size) % size]
        }

        /** AI 工具与导入用的名字解析（忽略大小写）。 */
        fun fromName(raw: String?): CardAccent? =
            raw?.trim()?.let { name -> entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }

        fun fromArgb(value: Int): CardAccent? = entries.firstOrNull { it.argb == value }
    }
}
