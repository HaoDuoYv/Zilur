package com.example.zhilu.ui.assistant

import androidx.lifecycle.SavedStateHandle
import com.example.zhilu.ai.AiPromptHandoff
import com.example.zhilu.ai.AiRefManager
import com.example.zhilu.ai.AiSubmitRequest
import com.example.zhilu.ai.AiSubmitResult
import com.example.zhilu.ai.AiTaskManager
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.ai.model.AiTask
import com.example.zhilu.domain.ai.model.AiTaskPhase
import com.example.zhilu.domain.ai.model.AiTaskState
import com.example.zhilu.domain.model.AiConversation
import com.example.zhilu.domain.model.AiMessage
import com.example.zhilu.domain.model.AiRole
import com.example.zhilu.domain.model.AiService
import com.example.zhilu.domain.model.AiSettings
import com.example.zhilu.domain.repository.AiConversationRepository
import com.example.zhilu.domain.repository.NoteRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 助手页「任务状态不被切页/切会话重置」+ 全局互斥的 UI 语义。
 *
 * 任务管理器是替身（真实互斥语义由 `AiTaskManagerTest` 钉），这里只关心
 * ViewModel 如何把任务状态投影成 `isGenerating`（全局）/ `activeTask`（本会话）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AssistantViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `引用某条消息后 提交会带上 quotedMessageId 并收起引用条`() = runTest(dispatcher) {
        val taskState = MutableStateFlow(AiTaskState())
        val taskManager = fakeTaskManager(taskState, AiSubmitResult.Accepted("task-1"))
        val vm = viewModel(taskManager = taskManager)
        advanceUntilIdle()

        val quoted = AiMessage(
            id = 5L,
            conversationId = 7L,
            role = AiRole.ASSISTANT,
            content = "上一条回答"
        )
        vm.quoteMessage(quoted)
        assertEquals(quoted, vm.uiState.value.quotedMessage)

        vm.updateInput("接着这条说")
        vm.sendMessage()
        advanceUntilIdle()

        val request = slot<AiSubmitRequest>()
        verify { taskManager.submit(capture(request)) }
        assertEquals("引用的消息 id 必须随请求落库", 5L, request.captured.quotedMessageId)
        assertNull("发送成功后引用条要收掉", vm.uiState.value.quotedMessage)
    }

    @Test
    fun `切换会话会清掉引用条（引用不跨会话）`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.quoteMessage(AiMessage(id = 5L, conversationId = 7L, role = AiRole.USER, content = "问题"))
        vm.selectConversation(9L)
        advanceUntilIdle()

        assertNull(vm.uiState.value.quotedMessage)
    }

    @Test
    fun `跨页交来的开场白落进输入框 且只生效一次`() = runTest(dispatcher) {
        val handoff = AiPromptHandoff()
        val vm = viewModel(promptHandoff = handoff)
        advanceUntilIdle()

        handoff.handoff("帮我创建：")
        advanceUntilIdle()

        assertEquals("帮我创建：", vm.uiState.value.inputText)
        assertEquals("取走后必须清空，否则每次重进页面都会再填一遍", "", handoff.prompt.value)
    }

    @Test
    fun `提交被拒时保留输入并给出提示`() = runTest(dispatcher) {
        val vm = viewModel(
            taskManager = fakeTaskManager(
                taskState = MutableStateFlow(AiTaskState()),
                submitResult = AiSubmitResult.Rejected("正在生成中，先停一下或等它回答完", null)
            )
        )
        advanceUntilIdle()

        vm.updateInput("这稿子必须留着")
        vm.sendMessage()
        advanceUntilIdle()

        assertEquals("被拒时输入框一个字都不能动", "这稿子必须留着", vm.uiState.value.inputText)
        assertEquals("正在生成中，先停一下或等它回答完", vm.uiState.value.notice)
    }

    @Test
    fun `受理后清空输入 新会话解析出来时回流`() = runTest(dispatcher) {
        val taskState = MutableStateFlow(AiTaskState())
        val vm = viewModel(
            taskManager = fakeTaskManager(taskState, AiSubmitResult.Accepted("task-1"))
        )
        advanceUntilIdle()

        vm.updateInput("帮我整理二次型")
        vm.sendMessage()
        advanceUntilIdle()
        assertEquals("", vm.uiState.value.inputText)
        assertNull(vm.uiState.value.currentConversationId)

        // 任务先以 conversationId=0 入队，解析出会话后回流
        taskState.value = AiTaskState(
            tasks = listOf(AiTask(id = "task-1", conversationId = 42L, phase = AiTaskPhase.RUNNING))
        )
        advanceUntilIdle()

        assertEquals(42L, vm.uiState.value.currentConversationId)
        assertEquals("task-1", vm.uiState.value.activeTask?.id)
        assertTrue(vm.uiState.value.isGenerating)
        assertTrue(vm.uiState.value.isStreamingVisible)
    }

    @Test
    fun `别人的任务不会被回流 但互斥信号仍在`() = runTest(dispatcher) {
        val taskState = MutableStateFlow(AiTaskState())
        val vm = viewModel(
            taskManager = fakeTaskManager(taskState, AiSubmitResult.Accepted("task-1"))
        )
        advanceUntilIdle()

        // 另一条会话的任务（不是本 VM 提交的）
        taskState.value = AiTaskState(
            tasks = listOf(AiTask(id = "task-2", conversationId = 42L, phase = AiTaskPhase.RUNNING))
        )
        advanceUntilIdle()

        assertNull("不能把用户硬拽到别人的会话", vm.uiState.value.currentConversationId)
        assertTrue("全局互斥信号必须在（输入栏要显示停止）", vm.uiState.value.isGenerating)
        assertFalse("本会话不该渲染别人的流式气泡", vm.uiState.value.isStreamingVisible)
        assertNull(vm.uiState.value.streamingText)
    }

    @Test
    fun `切换到别的会话后任务状态不丢 停止按钮仍在`() = runTest(dispatcher) {
        val taskState = MutableStateFlow(AiTaskState())
        val vm = viewModel(
            taskManager = fakeTaskManager(taskState, AiSubmitResult.Accepted("task-1"))
        )
        advanceUntilIdle()

        vm.selectConversation(7L)
        taskState.value = AiTaskState(
            tasks = listOf(
                AiTask(
                    id = "task-1",
                    conversationId = 7L,
                    phase = AiTaskPhase.STREAMING,
                    streamText = "半个回答"
                )
            )
        )
        advanceUntilIdle()
        assertEquals("task-1", vm.uiState.value.activeTask?.id)

        // 再切到 8：任务还在跑（互斥与停止按钮保留），但流式气泡不跟过去
        vm.selectConversation(8L)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isGenerating)
        assertEquals("task-1", vm.uiState.value.runningTask?.id)
        assertNull(vm.uiState.value.activeTask)
        assertNull(vm.uiState.value.streamingText)
        assertFalse(vm.uiState.value.isStreamingVisible)
    }

    @Test
    fun `停止掐的是全局那条任务`() = runTest(dispatcher) {
        val taskState = MutableStateFlow(AiTaskState())
        val taskManager = fakeTaskManager(taskState, AiSubmitResult.Accepted("task-1"))
        val vm = viewModel(taskManager = taskManager)
        advanceUntilIdle()

        taskState.value = AiTaskState(
            tasks = listOf(AiTask(id = "task-1", conversationId = 42L, phase = AiTaskPhase.RUNNING))
        )
        advanceUntilIdle()

        vm.stopGeneration()

        verify { taskManager.cancel("task-1") }
    }

    @Test
    fun `任务失败时恢复刚才的输入并报错`() = runTest(dispatcher) {
        val taskState = MutableStateFlow(AiTaskState())
        val vm = viewModel(
            taskManager = fakeTaskManager(taskState, AiSubmitResult.Accepted("task-1"))
        )
        advanceUntilIdle()

        vm.updateInput("会失败的问题")
        vm.sendMessage()
        advanceUntilIdle()
        assertEquals("", vm.uiState.value.inputText)

        taskState.value = AiTaskState(
            tasks = listOf(AiTask(id = "task-1", phase = AiTaskPhase.FAILED, error = "网络超时"))
        )
        advanceUntilIdle()

        assertEquals("网络超时", vm.uiState.value.error)
        assertEquals("会失败的问题", vm.uiState.value.inputText)
    }

    @Test
    fun `SavedStateHandle 恢复所在会话与输入草稿 且后续改动继续镜像`() = runTest(dispatcher) {
        val handle = SavedStateHandle(
            mapOf(
                "assistantConversationId" to 9L,
                "assistantInputText" to "半截草稿"
            )
        )
        val vm = viewModel(savedState = handle)
        advanceUntilIdle()

        assertEquals(9L, vm.uiState.value.currentConversationId)
        assertEquals("半截草稿", vm.uiState.value.inputText)

        // 后续改动仍要写回句柄（否则进程回收后回到的是旧值）
        vm.updateInput("改过的草稿")
        advanceUntilIdle()
        assertEquals("改过的草稿", handle.get<String>("assistantInputText"))

        vm.selectConversation(11L)
        advanceUntilIdle()
        assertEquals(11L, handle.get<Long>("assistantConversationId"))
    }

    // ── 替身 ────────────────────────────────────────────────────────────────

    private fun viewModel(
        taskManager: AiTaskManager = fakeTaskManager(
            MutableStateFlow(AiTaskState()),
            AiSubmitResult.Accepted("task-x")
        ),
        savedState: SavedStateHandle = SavedStateHandle(),
        promptHandoff: AiPromptHandoff = AiPromptHandoff()
    ): AssistantViewModel = AssistantViewModel(
        savedStateHandle = savedState,
        conversationRepository = FakeConversationRepository(),
        userPreferences = mockk { every { aiSettings } returns MutableStateFlow(USABLE_SETTINGS) },
        mediaFileManager = mockk<MediaFileManager>(relaxed = true),
        taskManager = taskManager,
        // pendingRefs 必须是真 StateFlow：松弛 mock 的 Flow 收集起来会抛异常，
        // 把与引用无关的用例一起带崩（同 TestAiDoubles 的教训）。
        refManager = mockk<AiRefManager>(relaxed = true) {
            every { pendingRefs } returns MutableStateFlow(emptyList())
        },
        promptHandoff = promptHandoff,
        noteRepository = mockk<NoteRepository>(relaxed = true)
    )

    private fun fakeTaskManager(
        taskState: MutableStateFlow<AiTaskState>,
        submitResult: AiSubmitResult
    ): AiTaskManager = mockk(relaxed = true) {
        every { state } returns taskState
        every { submit(any()) } returns submitResult
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

private class FakeConversationRepository : AiConversationRepository {

    private val messages = MutableStateFlow<RepositoryResult<List<AiMessage>>>(
        RepositoryResult.Success(emptyList())
    )

    override fun getConversations(): Flow<RepositoryResult<List<AiConversation>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun getMessages(conversationId: Long): Flow<RepositoryResult<List<AiMessage>>> = messages

    override suspend fun createConversation(title: String): RepositoryResult<AiConversation> =
        RepositoryResult.Success(AiConversation(id = 1L, title = title))

    override suspend fun updateConversationTitle(conversationId: Long, title: String): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun deleteConversation(conversationId: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun addMessage(message: AiMessage): RepositoryResult<AiMessage> =
        RepositoryResult.Success(message)
}
