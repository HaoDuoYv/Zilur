package com.example.zhilu.ui.home

import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.model.ReminderWithContext
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.TagRepository
import com.example.zhilu.ui.navigation.AppIntents
import com.example.zhilu.ui.theme.TagCreationPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelSearchTest {
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
    fun `query debounces into a single search`() = runTest(dispatcher) {
        val noteRepository = SearchTestNoteRepository()
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()

        viewModel.onQueryChange("二")
        viewModel.onQueryChange("二次")
        viewModel.onQueryChange("二次型")
        advanceUntilIdle()

        assertEquals("连续输入只应触发一次检索", 1, noteRepository.searchCalls.size)
        assertEquals("二次型", noteRepository.searchCalls.single())
        assertEquals("二次型", viewModel.uiState.value.query)
    }

    @Test
    fun `empty query clears results without searching`() = runTest(dispatcher) {
        val noteRepository = SearchTestNoteRepository()
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()

        viewModel.onQueryChange("二次型")
        advanceUntilIdle()
        viewModel.clearQuery()
        advanceUntilIdle()

        assertEquals("空查询不应发起检索", 1, noteRepository.searchCalls.size)
        assertEquals(emptyList<Note>(), viewModel.uiState.value.searchResults)
        assertEquals("", viewModel.uiState.value.query)
    }

    @Test
    fun `hash prefix normalizes into a tag filter chip`() = runTest(dispatcher) {
        val noteRepository = SearchTestNoteRepository()
        val tagRepository = SearchTestTagRepository()
        val viewModel = createViewModel(noteRepository, tagRepository)
        advanceUntilIdle()

        viewModel.onQueryChange("#考研数学")
        advanceUntilIdle()

        // `#名称` 不再是独立的检索分支：命中标签后归一成筛选 chip，与点 chip 汇成同一套状态
        assertEquals("应剥掉 # 后按名字找标签", listOf("考研数学"), tagRepository.requestedTagNames)
        assertEquals("关键词应被清空，只留标签", "", viewModel.uiState.value.query)
        assertEquals(setOf(7L), viewModel.uiState.value.selectedTagIds)
        assertEquals("标签 id 要进组合查询", listOf(listOf(7L)), noteRepository.tagCalls)
        assertEquals("关键词为空也要查（纯标签浏览）", listOf(""), noteRepository.searchCalls)
        assertEquals(
            "应返回该标签下的笔记",
            listOf("标签内笔记"),
            viewModel.uiState.value.searchResults.map { it.title }
        )
    }

    @Test
    fun `unknown hash prefix falls back to full text search`() = runTest(dispatcher) {
        val noteRepository = SearchTestNoteRepository()
        val tagRepository = SearchTestTagRepository(resolvableNames = emptySet())
        val viewModel = createViewModel(noteRepository, tagRepository)
        advanceUntilIdle()

        viewModel.onQueryChange("#不存在")
        advanceUntilIdle()

        assertEquals("没匹配到标签时保留原关键词走全文检索", listOf("#不存在"), noteRepository.searchCalls)
        assertEquals("不应凭空造出标签筛选", emptySet<Long>(), viewModel.uiState.value.selectedTagIds)
        assertEquals("没有标签筛选，查询要带空标签集", listOf(emptyList<Long>()), noteRepository.tagCalls)
    }

    @Test
    fun `recent queries are deduplicated and capped`() = runTest(dispatcher) {
        val noteRepository = SearchTestNoteRepository()
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()

        (1..8).forEach { index ->
            viewModel.onQueryChange("关键词$index")
            advanceUntilIdle()
        }
        viewModel.onQueryChange("关键词3")
        advanceUntilIdle()

        val recent = viewModel.uiState.value.recentQueries
        assertEquals("最近搜索上限为 6 条", 6, recent.size)
        assertEquals("最新一次查询排在最前", "关键词3", recent.first())
        assertEquals("重复关键词只保留一条", recent.size, recent.distinct().size)
    }

    // ---- 标签筛选：标签页撤销后，这里是标签唯一的常驻出口 ----

    @Test
    fun `tag chip filters without a keyword`() = runTest(dispatcher) {
        val noteRepository = SearchTestNoteRepository()
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()

        viewModel.toggleTagFilter(7L)
        advanceUntilIdle()

        assertEquals("点 chip 是明确动作，应立即重查而不是等输入防抖", listOf(""), noteRepository.searchCalls)
        assertEquals(listOf(listOf(7L)), noteRepository.tagCalls)
        assertEquals(setOf(7L), viewModel.uiState.value.selectedTagIds)
        assertEquals("空关键词 + 标签也算搜索态（纯标签浏览）", true, viewModel.uiState.value.isSearchActive)

        viewModel.toggleTagFilter(8L)
        advanceUntilIdle()

        assertEquals(
            "多选是 AND 语义：选中的标签要一起下推给仓库",
            listOf(listOf(7L), listOf(7L, 8L)),
            noteRepository.tagCalls
        )
    }

    @Test
    fun `clearing tag filters exits the search state`() = runTest(dispatcher) {
        val noteRepository = SearchTestNoteRepository()
        val viewModel = createViewModel(noteRepository)
        advanceUntilIdle()

        viewModel.toggleTagFilter(7L)
        advanceUntilIdle()
        viewModel.clearTagFilters()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(emptySet<Long>(), state.selectedTagIds)
        assertEquals("关键词与标签都空时应直接回笔记列表，不再查一次", 1, noteRepository.searchCalls.size)
        assertEquals(emptyList<Note>(), state.searchResults)
        assertEquals(false, state.isSearchActive)
    }

    @Test
    fun `clear query also drops tag filters`() = runTest(dispatcher) {
        val viewModel = createViewModel(SearchTestNoteRepository())
        advanceUntilIdle()

        viewModel.toggleTagFilter(7L)
        viewModel.onQueryChange("极限")
        advanceUntilIdle()
        viewModel.clearQuery()

        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertEquals(emptySet<Long>(), state.selectedTagIds)
        assertEquals(false, state.isSearchActive)
    }

    @Test
    fun `tag intent from a note card pill lands as a filter`() = runTest(dispatcher) {
        val noteRepository = SearchTestNoteRepository()
        val intents = AppIntents()
        val viewModel = createViewModel(noteRepository, appIntents = intents)
        advanceUntilIdle()

        viewModel.onQueryChange("极限")
        advanceUntilIdle()
        // 笔记卡片上的标签胶囊 → AppIntents → 首页筛选（同一个 ViewModel 就地生效）
        intents.requestSearchTag(9L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("点标签的预期是「看这个标签的笔记」，关键词要让位", "", state.query)
        assertEquals(setOf(9L), state.selectedTagIds)
        assertEquals(listOf(listOf(9L)), noteRepository.tagCalls.takeLast(1))
        assertEquals("意图消费即清空", null, intents.pendingSearchTagId.value)
    }

    @Test
    fun `deleting a selected tag drops it from the filter`() = runTest(dispatcher) {
        val math = Tag(id = 1L, name = "数学", color = 0xFF0061A4.toInt())
        val noteRepository = SearchTestNoteRepository()
        val tagRepository = SearchTestTagRepository(tags = listOf(math))
        val viewModel = createViewModel(noteRepository, tagRepository)
        advanceUntilIdle()

        viewModel.toggleTagFilter(math.id)
        advanceUntilIdle()
        viewModel.deleteTag(math)
        advanceUntilIdle()

        assertEquals(listOf(math), tagRepository.deletedTags)
        assertEquals(
            "删掉的标签不能继续挂在筛选上，否则筛选悬空、结果永远为空",
            emptySet<Long>(),
            viewModel.uiState.value.selectedTagIds
        )
    }

    @Test
    fun `tag filters are ordered by note count`() = runTest(dispatcher) {
        val math = Tag(id = 1L, name = "数学", color = 0xFF0061A4.toInt())
        val english = Tag(id = 2L, name = "英语", color = 0xFF006B2E.toInt())
        val unused = Tag(id = 3L, name = "空标签", color = 0xFF6750A4.toInt())
        val tagRepository = SearchTestTagRepository(
            tags = listOf(english, unused, math),
            noteCountsByTag = mapOf(math.id to 5, english.id to 3)
        )
        val viewModel = createViewModel(SearchTestNoteRepository(), tagRepository)
        advanceUntilIdle()

        assertEquals(
            listOf("数学" to 5, "英语" to 3, "空标签" to 0),
            viewModel.uiState.value.tagFilters.map { it.tag.name to it.noteCount }
        )
    }

    @Test
    fun `creating a tag trims the name and uses the shared palette`() = runTest(dispatcher) {
        val tagRepository = SearchTestTagRepository(resolvableNames = emptySet())
        val viewModel = createViewModel(SearchTestNoteRepository(), tagRepository)
        advanceUntilIdle()

        viewModel.createTag("  数学  ")
        advanceUntilIdle()

        val inserted = tagRepository.insertedTags.single()
        assertEquals("数学", inserted.name)
        assertTrue("颜色必须来自共享色板，三个新建入口才一致", TagCreationPalette.contains(inserted.color))
    }

    @Test
    fun `creating a tag skips blank and existing names`() = runTest(dispatcher) {
        val tagRepository = SearchTestTagRepository()
        val viewModel = createViewModel(SearchTestNoteRepository(), tagRepository)
        advanceUntilIdle()

        viewModel.createTag("   ")
        viewModel.createTag("数学")
        advanceUntilIdle()

        assertEquals(emptyList<Tag>(), tagRepository.insertedTags)
    }

    // ---- `#` 输入补全 ----

    @Test
    fun `tag suggestions match the trailing hash token`() = runTest(dispatcher) {
        val tagRepository = SearchTestTagRepository(
            tags = listOf(
                Tag(id = 1, name = "计算机网络"),
                Tag(id = 2, name = "计算几何"),
                Tag(id = 3, name = "线性代数")
            )
        )
        val viewModel = createViewModel(SearchTestNoteRepository(), tagRepository)
        advanceUntilIdle()

        viewModel.onQueryChange("论文 #计算")

        // 候选顺序继承筛选条的排序（笔记数降序、同名按名），这里只钉"命中了哪几个"
        assertEquals(setOf(1L, 2L), viewModel.uiState.value.tagSuggestions.map { it.tag.id }.toSet())
    }

    @Test
    fun `lone hash offers every unselected tag`() = runTest(dispatcher) {
        val tagRepository = SearchTestTagRepository(
            tags = listOf(Tag(id = 1, name = "计算机网络"), Tag(id = 2, name = "线性代数"))
        )
        val viewModel = createViewModel(SearchTestNoteRepository(), tagRepository)
        advanceUntilIdle()

        viewModel.onQueryChange("#")

        // 只打一个 `#` 是"我要挑一个标签"的手势，不是打错了
        assertEquals(setOf(1L, 2L), viewModel.uiState.value.tagSuggestions.map { it.tag.id }.toSet())
    }

    @Test
    fun `suggestions never offer a tag that is already filtering`() = runTest(dispatcher) {
        val tagRepository = SearchTestTagRepository(
            tags = listOf(Tag(id = 1, name = "计算机网络"), Tag(id = 2, name = "计算几何"))
        )
        val viewModel = createViewModel(SearchTestNoteRepository(), tagRepository)
        advanceUntilIdle()

        viewModel.toggleTagFilter(1)
        advanceUntilIdle()
        viewModel.onQueryChange("#计算")

        // 已经在筛选里的标签再点一次只会把它取消掉，所以不列出来
        assertEquals(listOf(2L), viewModel.uiState.value.tagSuggestions.map { it.tag.id })
    }
    @Test
    fun `picking a suggestion replaces the token with a filter chip`() = runTest(dispatcher) {
        val noteRepository = SearchTestNoteRepository()
        val tagRepository = SearchTestTagRepository(tags = listOf(Tag(id = 7, name = "计算机")))
        val viewModel = createViewModel(noteRepository, tagRepository)
        advanceUntilIdle()

        viewModel.onQueryChange("论文 #计")
        viewModel.applyTagSuggestion(7)
        advanceUntilIdle()

        // token 摘掉、余下的关键词留着；标签落成 chip
        assertEquals("论文", viewModel.uiState.value.query)
        assertEquals(setOf(7L), viewModel.uiState.value.selectedTagIds)
        // 点候选是明确动作，立刻重查而不是等输入防抖
        assertEquals(listOf(7L), noteRepository.tagCalls.last())
        assertTrue(viewModel.uiState.value.tagSuggestions.isEmpty())
    }

    @Test
    fun `picking a suggestion clears the token even when the query was only a hash`() =
        runTest(dispatcher) {
            val tagRepository = SearchTestTagRepository(tags = listOf(Tag(id = 7, name = "计算机")))
            val viewModel = createViewModel(SearchTestNoteRepository(), tagRepository)
            advanceUntilIdle()

            viewModel.onQueryChange("#计")
            viewModel.applyTagSuggestion(7)
            advanceUntilIdle()

            // 空关键词 + 一个标签 = 纯标签浏览，搜索态仍成立
            assertEquals("", viewModel.uiState.value.query)
            assertTrue(viewModel.uiState.value.isSearchActive)
        }

    @Test
    fun `plain keyword keeps a trailing hash as text when no tag matches`() =
        runTest(dispatcher) {
            val noteRepository = SearchTestNoteRepository()
            val tagRepository = SearchTestTagRepository(tags = listOf(Tag(id = 7, name = "计算机")))
            val viewModel = createViewModel(noteRepository, tagRepository)
            advanceUntilIdle()

            viewModel.onQueryChange("C#")
            viewModel.submitSearch()
            advanceUntilIdle()

            // `C#` 不是标签引用（井号不在词首），原样当关键词搜
            assertTrue(viewModel.uiState.value.selectedTagIds.isEmpty())
            assertEquals(listOf(emptyList<Long>()), noteRepository.tagCalls)
        }

    @Test
    fun `trailing hash whose name matches no tag falls back to full text`() = runTest(dispatcher) {
        val noteRepository = SearchTestNoteRepository()
        val tagRepository = SearchTestTagRepository(
            tags = listOf(Tag(id = 7, name = "计算机")),
            resolvableNames = setOf("计算机")
        )
        val viewModel = createViewModel(noteRepository, tagRepository)
        advanceUntilIdle()

        viewModel.onQueryChange("论文 #不存在的标签")
        viewModel.submitSearch()
        advanceUntilIdle()

        // 匹配不到就不改写查询 —— 保留原文走全文检索（既有语义）
        assertEquals("论文 #不存在的标签", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.selectedTagIds.isEmpty())
    }

    private fun createViewModel(
        noteRepository: NoteRepository,
        tagRepository: TagRepository = SearchTestTagRepository(),
        appIntents: AppIntents = AppIntents()
    ): HomeViewModel = HomeViewModel(
        noteRepository = noteRepository,
        tagRepository = tagRepository,
        mediaRepository = SearchTestMediaRepository(),
        reminderRepository = SearchTestReminderRepository(),
        appIntents = appIntents
    )
}

private class SearchTestNoteRepository : NoteRepository {
    val searchCalls = mutableListOf<String>()
    val tagCalls = mutableListOf<List<Long>>()

    override fun getAllNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun getFavoriteNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun getDeletedNotes(): Flow<RepositoryResult<List<Note>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun getNoteById(id: Long): RepositoryResult<Note?> =
        RepositoryResult.Success(null)

    override suspend fun searchNotes(keyword: String, tagIds: List<Long>): RepositoryResult<List<Note>> {
        searchCalls += keyword
        tagCalls += tagIds
        // 带标签筛选时回一条可分辨的结果：首页"关键词 / 标签"两条链路各自断言得开。
        return if (tagIds.isEmpty()) {
            RepositoryResult.Success(
                listOf(Note(id = 1L, title = "命中 $keyword", blocks = listOf(textBlock())))
            )
        } else {
            RepositoryResult.Success(
                listOf(Note(id = 2L, title = "标签内笔记", blocks = listOf(textBlock())))
            )
        }
    }

    override suspend fun getNotesByTagId(tagId: Long): RepositoryResult<List<Note>> =
        RepositoryResult.Success(
            listOf(Note(id = 2L, title = "标签内笔记", blocks = listOf(textBlock())))
        )

    override suspend fun insertNote(note: Note): RepositoryResult<Note> =
        RepositoryResult.Success(note)

    override suspend fun updateNote(note: Note): RepositoryResult<Note> =
        RepositoryResult.Success(note)

    override suspend fun deleteNote(note: Note): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun softDeleteNote(id: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun restoreNote(id: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun clearDeletedNotes(): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun getNoteCount(): RepositoryResult<Int> =
        RepositoryResult.Success(0)

    private fun textBlock() = Block(id = 1L, type = BlockType.TEXT, content = "正文", sortOrder = 0)
}

private class SearchTestTagRepository(
    private val tags: List<Tag> = emptyList(),
    private val noteCountsByTag: Map<Long, Int> = emptyMap(),
    private val resolvableNames: Set<String>? = null
) : TagRepository {
    val requestedTagNames = mutableListOf<String>()
    val insertedTags = mutableListOf<Tag>()
    val updatedTags = mutableListOf<Tag>()
    val deletedTags = mutableListOf<Tag>()

    override fun getAllTags(): Flow<RepositoryResult<List<Tag>>> =
        flowOf(RepositoryResult.Success(tags))

    override suspend fun getTagById(id: Long): RepositoryResult<Tag?> =
        RepositoryResult.Success(tags.firstOrNull { it.id == id })

    /**
     * 默认把**任何**名字都解析成一个 id=7 的标签（既有用例只关心"有没有走到按名字找"）；
     * 传 [resolvableNames] 收窄成"只认识这几个名字"，用来验"没匹配到就回退全文检索"的分支。
     */
    override suspend fun getTagByName(name: String): RepositoryResult<Tag?> {
        requestedTagNames += name
        val allowed = resolvableNames?.contains(name) ?: true
        return RepositoryResult.Success(
            if (allowed) Tag(id = 7L, name = name, color = 0xFF6B5B8A.toInt()) else null
        )
    }

    override suspend fun getTagsByNoteId(noteId: Long): RepositoryResult<List<Tag>> =
        RepositoryResult.Success(emptyList())

    override suspend fun insertTag(tag: Tag): RepositoryResult<Long> {
        insertedTags += tag
        return RepositoryResult.Success(1L)
    }

    override suspend fun updateTag(tag: Tag): RepositoryResult<Unit> {
        updatedTags += tag
        return RepositoryResult.Success(Unit)
    }

    override suspend fun deleteTag(tag: Tag): RepositoryResult<Unit> {
        deletedTags += tag
        return RepositoryResult.Success(Unit)
    }

    override fun getTagCount(): Flow<RepositoryResult<Int>> =
        flowOf(RepositoryResult.Success(tags.size))

    override fun getNoteCountsByTag(): Flow<RepositoryResult<Map<Long, Int>>> =
        flowOf(RepositoryResult.Success(noteCountsByTag))
}

private class SearchTestMediaRepository : MediaRepository {
    override suspend fun getAllMedia(): RepositoryResult<List<Media>> =
        RepositoryResult.Success(emptyList())

    override suspend fun getMediaById(id: Long): RepositoryResult<Media?> =
        RepositoryResult.Success(null)

    override suspend fun insertMedia(media: Media): RepositoryResult<Long> =
        RepositoryResult.Success(1L)

    override suspend fun updateMedia(media: Media): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun deleteMedia(media: Media): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override fun getMediaCount(): Flow<RepositoryResult<Int>> =
        flowOf(RepositoryResult.Success(0))

    override suspend fun getTotalSize(): RepositoryResult<Long> =
        RepositoryResult.Success(0L)
}

private class SearchTestReminderRepository : ReminderRepository {
    override fun observeAll(): Flow<RepositoryResult<List<ReminderInstance>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override fun observeAllWithContext(): Flow<RepositoryResult<List<ReminderWithContext>>> =
        flowOf(RepositoryResult.Success(emptyList()))

    override suspend fun getDueReminders(now: Long): RepositoryResult<List<ReminderInstance>> =
        RepositoryResult.Success(emptyList())

    override suspend fun getActiveReminder(
        type: ReminderType,
        sourceId: Long
    ): RepositoryResult<ReminderInstance?> = RepositoryResult.Success(null)

    override suspend fun upsertScheduled(reminder: ReminderInstance): RepositoryResult<Long> =
        RepositoryResult.Success(reminder.id)

    override suspend fun markDone(type: ReminderType, sourceId: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)

    override suspend fun markFired(
        reminder: ReminderInstance,
        firedAt: Long
    ): RepositoryResult<Unit> = RepositoryResult.Success(Unit)

    override suspend fun cancel(type: ReminderType, sourceId: Long): RepositoryResult<Unit> =
        RepositoryResult.Success(Unit)
}