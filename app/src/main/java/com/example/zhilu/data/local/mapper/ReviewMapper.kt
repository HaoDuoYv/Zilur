package com.example.zhilu.data.local.mapper

import com.example.zhilu.data.local.entity.ReviewEventEntity
import com.example.zhilu.data.local.entity.ReviewPlanEntity
import com.example.zhilu.domain.model.ReviewEvent
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewRating

object ReviewMapper {
    fun toDomain(entity: ReviewPlanEntity): ReviewPlan = ReviewPlan(
        id = entity.id,
        noteId = entity.noteId,
        enabled = entity.enabled,
        currentStep = entity.currentStep,
        nextReviewAt = entity.nextReviewAt,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        completedAt = entity.completedAt
    )

    fun toEntity(domain: ReviewPlan): ReviewPlanEntity = ReviewPlanEntity(
        id = domain.id,
        noteId = domain.noteId,
        enabled = domain.enabled,
        currentStep = domain.currentStep,
        nextReviewAt = domain.nextReviewAt,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
        completedAt = domain.completedAt
    )

    fun toDomain(entity: ReviewEventEntity): ReviewEvent = ReviewEvent(
        id = entity.id,
        planId = entity.planId,
        noteId = entity.noteId,
        reviewedAt = entity.reviewedAt,
        rating = ReviewRating.fromValue(entity.rating),
        previousStep = entity.previousStep,
        nextStep = entity.nextStep,
        nextReviewAt = entity.nextReviewAt
    )

    fun toEntity(domain: ReviewEvent): ReviewEventEntity = ReviewEventEntity(
        id = domain.id,
        planId = domain.planId,
        noteId = domain.noteId,
        reviewedAt = domain.reviewedAt,
        rating = domain.rating.value,
        previousStep = domain.previousStep,
        nextStep = domain.nextStep,
        nextReviewAt = domain.nextReviewAt
    )
}
