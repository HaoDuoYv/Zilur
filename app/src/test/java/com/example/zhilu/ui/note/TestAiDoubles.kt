package com.example.zhilu.ui.note

import com.example.zhilu.ai.AiRefManager
import com.example.zhilu.ai.AiTaskManager
import com.example.zhilu.domain.ai.model.AiTaskState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * NoteViewModel 测试用的 AI 管理器替身。
 *
 * 不能简单用 `mockk(relaxed = true)`：松弛模式下 mock 出来的 StateFlow 无法真正发射，
 * `observeAiTasks` 收集状态时会抛 KotlinNothingValueException，
 * 把大量与 AI 无关的用例一起带崩。这里显式给 state 配一个真实的 MutableStateFlow。
 */
fun fakeAiTaskManager(): AiTaskManager =
    mockk(relaxed = true) { every { state } returns MutableStateFlow(AiTaskState()) }

fun fakeAiRefManager(): AiRefManager = mockk(relaxed = true)
