package com.example.zhilu.domain.model

/**
 * 块级语义标记的角色。
 *
 * 放在 domain 层且**不带任何颜色信息** —— 颜色是 UI 的事（见 `ui/theme/EmphasisTones.kt`），
 * 领域模型只关心"这条小点被标成了哪种语义"。
 *
 * 角色命名要跨领域成立：知录是通用记录工具，不只服务数学。
 * 「定义 / 重点 / 易错」是考试笔记的语汇，写读书摘抄、会议纪要、生活记录时会别扭，
 * 因此收敛为 **要点 / 想法 / 注意 / 待办**。
 *
 * [value] 是持久化稳定值，**不可改序/改值**（0 保留给"未标记"）。
 */
enum class EmphasisTone(
    val value: Int,
    /** UI 标签词，块级标记显示在正文最前面。 */
    val label: String,
    /** 行内语法里的角色字母：`{{k:文字}}`。 */
    val code: Char
) {
    /** 定义、结论、主旨、核心句、关键数据。 */
    KEY(1, "要点", 'k'),

    /** 自己的理解、联想、评价、灵感。 */
    IDEA(2, "想法", 'i'),

    /** 易错、陷阱、存疑、待确认。 */
    WARN(3, "注意", 'w'),

    /** 跟进、延伸阅读、待补充。 */
    TODO(4, "待办", 't');

    companion object {
        /** 未标记。 */
        const val NONE_VALUE = 0

        fun fromValue(value: Int): EmphasisTone? = entries.firstOrNull { it.value == value }

        fun fromCode(code: Char): EmphasisTone? = entries.firstOrNull { it.code == code }

        fun fromName(raw: String?): EmphasisTone? =
            raw?.trim()?.let { name -> entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }

        /** 领域值 ↔ 持久化值。未标记写 0。 */
        fun toValue(tone: EmphasisTone?): Int = tone?.value ?: NONE_VALUE
    }
}
