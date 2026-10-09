package com.example.zhilu.domain.reminder

/**
 * 复习阶梯的「天」表示与校验（纯逻辑；设置页、存储层与排期策略共用）。
 *
 * 阶梯的**唯一存储形态**是一串天数（`1,3,7,15,30`）；毫秒换算只发生在
 * [ReviewSchedulePolicy.fromDays]。校验规则集中在这里：
 * - 设置页用它做即时提示（错在哪一句直接显示）；
 * - 存储层用它兜脏数据（读回来不合法就回落默认阶梯，应用不会崩）。
 */
object ReviewIntervals {

    /** 出厂阶梯：1 / 3 / 7 / 15 / 30 天。 */
    val DEFAULT: List<Long> = listOf(1, 3, 7, 15, 30)

    /** 快捷预设（设置页一键填入，仍可继续手改）。 */
    val PRESETS: List<Preset> = listOf(
        Preset(label = "默认", days = DEFAULT),
        Preset(label = "密集", days = listOf(1, 2, 4, 7, 15)),
        Preset(label = "长周期", days = listOf(1, 4, 10, 25, 60))
    )

    /** 档位上限：再多就超出"复习"的范畴，退化成时间管理了。 */
    const val MAX_STEPS = 10

    const val MIN_DAY = 1L

    /** 单档上限一年 —— 比这更远的间隔，计划基本等于"退役"。 */
    const val MAX_DAY = 365L

    data class Preset(val label: String, val days: List<Long>)

    /**
     * 从输入串解析天数序列（分隔符：中英文逗号 / 顿号 / 空白）。
     *
     * 任一段不是非负整数就返回 null —— 调用方给"只能填正整数"的提示；
     * 0 与负数在这里放行到 [validate]，好让错误文案更具体（"每档需在 1–365 天之间"）。
     */
    fun parse(raw: String): List<Long>? {
        val tokens = raw
            .split(',', '，', '、', ' ', '\n', '\t')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.map { it.toLongOrNull() ?: return null }
    }

    /**
     * 校验阶梯；合法返回 null，否则返回一句给用户看的错误文案。
     *
     * 顺序固定：先看"有没有/够不够多"，再看单档范围，最后看排列 ——
     * 一次只报最根本的那个问题，用户改完再看下一句。
     */
    fun validate(days: List<Long>): String? = when {
        days.isEmpty() -> "至少需要一档"
        days.size > MAX_STEPS -> "最多 $MAX_STEPS 档"
        days.any { it < MIN_DAY || it > MAX_DAY } -> "每档间隔需在 $MIN_DAY–$MAX_DAY 天之间"
        days.zipWithNext().any { (a, b) -> b <= a } -> "间隔需要从小到大排列"
        else -> null
    }

    /** 存储用的规范串（也是输入框回填的格式）：`1,3,7,15,30`。 */
    fun format(days: List<Long>): String = days.joinToString(",")

    /** 人读的阶梯预览：`1 天 → 3 天 → … → 毕业`。 */
    fun preview(days: List<Long>): String =
        days.joinToString(" → ") { "$it 天" } + " → 毕业"

    /** 「我的」里那一行入口的说明：`1 · 3 · 7 · 15 · 30 天（共 5 档）`。 */
    fun summary(days: List<Long>): String =
        days.joinToString(" · ") + " 天（共 ${days.size} 档）"
}
