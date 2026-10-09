package com.example.zhilu.data.local.entity

/**
 * 近 N 天里某一天的复习次数（`review_events` 的按天聚合投影，**不是表实体**）。
 *
 * [dayIndex] 以调用方给的窗口起点为 0，所以分桶口径由调用方决定 ——
 * 复习中心按**今天 0 点**往前推整周，而不是"最近 168 小时"，
 * 否则同一个数字会在午夜前后跳一下。分桶在 SQL 里做（单条 `GROUP BY`），
 * 不把事件行捞进内存再数。
 */
data class ReviewDailyCountRow(
    val dayIndex: Int,
    val eventCount: Int
)

/** 窗口内某个评价档的次数（`review_events.rating` 存的是整数值）。 */
data class ReviewRatingCountRow(
    val rating: Int,
    val eventCount: Int
)
