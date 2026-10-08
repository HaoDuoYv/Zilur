package com.example.zhilu.ai

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.domain.ai.model.AiTaskPhase
import com.example.zhilu.domain.ai.model.AiTaskState
import com.example.zhilu.domain.ai.repository.AiAssistantRepository
import com.example.zhilu.domain.ai.repository.AiAttempt
import com.example.zhilu.domain.model.AiConversation
import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.domain.model.AiService
import com.example.zhilu.domain.model.AiSettings
import com.example.zhilu.domain.repository.AiConversationRepository
import com.example.zhilu.domain.repository.NoteRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 全局单任务互斥的裁决点测试。
 *
 * 这些用例只关心**任务表的并发语义**（[AiTaskManager.submit] 的受理/拒绝），
 * 不关心生成内容，所以替身里让 `generateReply` 永远挂起（模拟「一直在跑」）。
 */
class AiTaskManagerTest {

    @Test
    fun `已有任务在跑时再提交会被拒绝 且不产生第二条任务`() {
        val manager = newManager { awaitCancellation() }

        val first = manager.submit(AiSubmitRequest(text = "第一条"))
        val second = manager.submit(AiSubmitRequest(text = "第二条"))

        assertTrue("第一条应被受理", first is AiSubmitResult.Accepted)
        assertTrue("任务在跑时第二条必须被拒", second is AiSubmitResult.Rejected)
        assertEquals("任务表里只能有一条活跃任务", 1, manager.state.value.activeTasks.size)
    }

    @Test
    fun `被拒时带回正在跑的那条任务 供 UI 说明原因`() {
        val manager = newManager { awaitCancellation() }
        val first = manager.submit(AiSubmitRequest(text = "第一条")) as AiSubmitResult.Accepted

        val rejected = manager.submit(AiSubmitRequest(text = "第二条")) as AiSubmitResult.Rejected

        assertTrue(rejected.reason.isNotBlank())
        assertEquals(first.taskId, rejected.running?.id)
    }

    @Test
    fun `停止之后立刻可以再次提交`() {
        val manager = newManager { awaitCancellation() }
        val first = manager.submit(AiSubmitRequest(text = "第一条")) as AiSubmitResult.Accepted

        manager.cancel(first.taskId)
        val second = manager.submit(AiSubmitRequest(text = "第二条"))

        assertTrue("取消后锁必须释放", second is AiSubmitResult.Accepted)
    }

    @Test
    fun `已完成的任务留在表里但不占锁`() {
        val manager = newManager { "答完了" }

        manager.submit(AiSubmitRequest(text = "第一条"))
        awaitState(manager) { state -> state.tasks.any { it.phase == AiTaskPhase.SUCCEEDED } }

        val second = manager.submit(AiSubmitRequest(text = "第二条"))

        assertTrue("完成的任务不该继续锁住提交", second is AiSubmitResult.Accepted)
        // 完成的任务仍留在表里（通知与「刚生成完」消费它），只是不算活跃。
        assertEquals(2, manager.state.value.tasks.size)
        assertEquals(1, manager.state.value.activeTasks.size)
    }

    private fun newManager(respond: suspend () -> String): AiTaskManager = AiTaskManager(
        aiAssistantRepository = FakeAssistantRepository(respond),
        conversationRepository = FakeConversationRepository(),
        noteRepository = mockk<NoteRepository>(relaxed = true),
        userPreferences = mockk<UserPreferences> {
            every { aiSettings } returns MutableStateFlow(USABLE_SETTINGS)
        },
        taskHost = NoOpTaskHost()
    )

    /**
     * 等待某个任务状态出现。
     *
     * 用 [runBlocking] 而不是 `runTest`：任务协程跑在管理器自己的 IO scope 上，
     * `runTest` 的虚拟时钟会「快进」到超时，等真实线程时会直接误判失败。
     */
    private fun awaitState(
        manager: AiTaskManager,
        timeoutMs: Long = 5_000,
        predicate: (AiTaskState) -> Boolean
    ): AiTaskState = runBlocking {
        // StateFlow.first(谓词) 会先看当前值，再等后续发射。
        withTimeout(timeoutMs) { manager.state.first(predicate) }
    }
}

private val USABLE_SETTINGS = AiSettings(
    services = listOf(
        AiService(
            id = "svc-1",
            name = "测试服务",
            endpoint = "https://example.com/v1",
            apiKey = "key",
            model = "model"
        )
    )
)

private class NoOpTaskHost : AiTaskHost {
    override fun sync(state: AiTaskState) = Unit
}

private class FakeAssistantRepository(
    /** 用 `{ awaitCancellation() }` 表示「一直在跑」。 */
    private val respond: suspend () -> String
) : AiAssistantRepository {

    override suspend fun generateReply(
        attempt: AiAttempt,
        history: List<AiMessage>,
        onDelta: (String) -> Unit,
        onToolEvent: suspend (String) -> Unit,
        onFallback: suspend (String) -> Unit,
        contextText: String
    ): Result<String> {
        val text = respond()
        onDelta(text)
        return Result.success(text)
    }
}

private class FakeConversationRepository : AiConversationRepository {

    private val stored = mutableListOf<AiMessage>()
    private val messages = MutableStateFlow<RepositoryResult<List<AiMessage>>>(
        RepositoryResult.Success(emptyList())
    )
    private var nextMessageId = 1L

    override fun getConversations(): Flow<RepositoryResult<List<AiConversation>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun getMessages(conversationId: Long): Flow<RepositoryResult<List<AiMessage>>> = messages

    override suspend fun createConversation(title: String): RepositoryResult<AiConversation> =
        RepositoryResult.Success(AiConversation(id = 42L, title = title))

    override suspend fun updateConversationTitle(conversationId: Long, title: String): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun deleteConversation(conversationId: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun addMessage(message: AiMessage): RepositoryResult<AiMessage> {
        val saved = message.copy(id = nextMessageId++)
        stored += saved
        messages.value = RepositoryResult.Success(stored.toList())
        return RepositoryResult.Success(saved)
    }
}
