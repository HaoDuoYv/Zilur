package com.example.zhilu.data.local.entity

import androidx.room.Embedded

/**
 * `review_plans` JOIN `notes` 的查询投影（**不是表实体**，仅供 Room 查询映射）。
 *
 * 复习中心列表要显示笔记标题，标题不在 `review_plans` 里，必须连表取；
 * 用独立投影而不是给 [ReviewPlanEntity] 挂 `@Ignore` 字段，避免污染表结构。
 */
data class ReviewPlanRow(
    @Embedded val plan: ReviewPlanEntity,
    val noteTitle: String
)
