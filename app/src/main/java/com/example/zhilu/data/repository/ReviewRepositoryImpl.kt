package com.example.zhilu.data.repository

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.dao.ReviewDao
import com.example.zhilu.data.local.mapper.ReviewMapper
import com.example.zhilu.domain.model.ReviewEvent
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewPlanWithNote
import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.domain.reminder.ReviewSchedulePolicy
import com.example.zhilu.domain.repository.ReviewRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class ReviewRepositoryImpl(
    private val reviewDao: ReviewDao,
    private val schedulePolicy: ReviewSchedulePolicy,
    private val transactionRunner: RepositoryTransactionRunner
) : ReviewRepository {
    override fun observePlans(): Flow<RepositoryResult<List<ReviewPlanWithNote>>> =
        reviewDao.observePlansWithNote()
            .map { rows ->
                RepositoryResult.Success(rows.map(ReviewMapper::toDomainWithNote))
                    as RepositoryResult<List<ReviewPlanWithNote>>
            }
            .catch { e -> emit(RepositoryResult.Error("Failed to load review plans", e)) }

    override fun observeEventCountSince(since: Long): Flow<Int> = reviewDao.countEventsSince(since)

    override suspend fun getPlanByNoteId(noteId: Long): RepositoryResult<ReviewPlan?> = runCatching {
        reviewDao.getPlanByNoteId(noteId)?.let(ReviewMapper::toDomain)
    }.toRepositoryResult("Failed to load review plan")

    override suspend fun startPlan(noteId: Long, now: Long): RepositoryResult<ReviewPlan> = runCatching {
        val schedule = schedulePolicy.start(now)
        transactionRunner.runInTransaction {
            val existing = reviewDao.getPlanByNoteId(noteId)?.let(ReviewMapper::toDomain)
            if (existing != null) {
                val enabledPlan = existing.copy(
                    enabled = true,
                    currentStep = schedule.nextStep,
                    nextReviewAt = schedule.nextReviewAt,
                    updatedAt = now,
                    completedAt = null
                )
                reviewDao.updatePlan(ReviewMapper.toEntity(enabledPlan))
                enabledPlan
            } else {
                val plan = ReviewPlan(
                    noteId = noteId,
                    enabled = true,
                    currentStep = schedule.nextStep,
                    nextReviewAt = schedule.nextReviewAt,
                    createdAt = now,
                    updatedAt = now,
                    completedAt = null
                )
                val id = reviewDao.insertPlan(ReviewMapper.toEntity(plan))
                if (id > 0) {
                    plan.copy(id = id)
                } else {
                    val inserted = reviewDao.getPlanByNoteId(noteId)?.let(ReviewMapper::toDomain)
                        ?: error("Review plan insert was ignored and no existing plan was found")
                    val enabledPlan = inserted.copy(
                        enabled = true,
                        currentStep = schedule.nextStep,
                        nextReviewAt = schedule.nextReviewAt,
                        updatedAt = now,
                        completedAt = null
                    )
                    reviewDao.updatePlan(ReviewMapper.toEntity(enabledPlan))
                    enabledPlan
                }
            }
        }
    }.toRepositoryResult("Failed to start review plan")

    override suspend fun enablePlan(noteId: Long, now: Long): RepositoryResult<ReviewPlan> = runCatching {
        val existing = reviewDao.getPlanByNoteId(noteId)?.let(ReviewMapper::toDomain)
            ?: error("Review plan not found for note $noteId")
        val resumed = existing.copy(
            enabled = true,
            nextReviewAt = now,
            updatedAt = now,
            completedAt = null
        )
        reviewDao.updatePlan(ReviewMapper.toEntity(resumed))
        resumed
    }.toRepositoryResult("Failed to enable review plan")

    override suspend fun recordReview(
        plan: ReviewPlan,
        rating: ReviewRating,
        reviewedAt: Long
    ): RepositoryResult<ReviewPlan> = runCatching {
        val schedule = schedulePolicy.advance(plan.currentStep, rating, reviewedAt)
        transactionRunner.runInTransaction {
            reviewDao.insertEvent(
                ReviewMapper.toEntity(
                    ReviewEvent(
                        planId = plan.id,
                        noteId = plan.noteId,
                        reviewedAt = reviewedAt,
                        rating = rating,
                        previousStep = plan.currentStep,
                        nextStep = schedule.nextStep,
                        nextReviewAt = schedule.nextReviewAt
                    )
                )
            )
            val updatedPlan = plan.copy(
                enabled = !schedule.completed,
                currentStep = schedule.nextStep,
                nextReviewAt = schedule.nextReviewAt,
                updatedAt = reviewedAt,
                completedAt = if (schedule.completed) reviewedAt else null
            )
            reviewDao.updatePlan(ReviewMapper.toEntity(updatedPlan))
            updatedPlan
        }
    }.toRepositoryResult("Failed to record review")

    override suspend fun disablePlan(noteId: Long): RepositoryResult<Unit> = runCatching {
        val plan = reviewDao.getPlanByNoteId(noteId)?.let(ReviewMapper::toDomain) ?: return@runCatching
        reviewDao.updatePlan(
            ReviewMapper.toEntity(
                plan.copy(
                    enabled = false,
                    nextReviewAt = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        )
    }.toRepositoryResult("Failed to disable review plan")
}
