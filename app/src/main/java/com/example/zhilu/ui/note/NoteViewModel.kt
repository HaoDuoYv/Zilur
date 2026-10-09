package com.example.zhilu.ui.note

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateListOf
import com.example.zhilu.ai.AiRefManager
import com.example.zhilu.ai.AiTaskManager
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.ai.AiRefSnapshot
import com.example.zhilu.domain.ai.model.AiRef
import com.example.zhilu.domain.ai.model.AiRefKind
import com.example.zhilu.domain.ai.model.AiTask
import com.example.zhilu.domain.ai.model.AiTaskPhase
import com.example.zhilu.domain.markup.InlineFormulaConversion
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockOrdering
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.EmphasisTone
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.domain.reminder.ReviewReminderSync
import com.example.zhilu.domain.reminder.TodoReminderSync
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.ReviewRepository
import com.example.zhilu.domain.repository.TagRepository
import com.example.zhilu.domain.repository.TodoRepository
import com.example.zhilu.domain.usecase.clipboard.BlockClipboardData
import com.example.zhilu.domain.usecase.clipboard.BlockClipboardManager
import com.example.zhilu.export.DtkExporter
import com.example.zhilu.export.HtmlExporter
import com.example.zhilu.export.MarkdownExporter
import com.example.zhilu.ui.theme.TagCreationPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 卡片临时 id 的起点，刻意远离 0。
 *
 * 早期版本会把内存里的临时负数 id 当成真实主键写进数据库（已在
 * `NoteRepositoryImpl.replaceCards` 修复），这些残留值落在 -1 附近。
 * 临时 id 若仍从 -1 开始，就会与残留主键撞号：两张卡片拿到同一个 id，
 * LazyColumn 的 key 重复会直接抛 IllegalArgumentException 崩溃。
 */
private const val TEMP_CARD_ID_SEED = -9_000_000_000_000L

sealed interface UiEvent {
    data class ShowUndoSnackbar(val block: Block, val index: Int, val token: Long) : UiEvent
}

private data class PendingBlockRemoval(
    val block: Block,
    val index: Int
)

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
    private val reviewRepository: ReviewRepository,
    private val todoRepository: TodoRepository,
    private val reminderRepository: ReminderRepository,
    /**
     * 待办 → TODO 提醒的同步点。默认值由 [reminderRepository] 现搭 ——
     * 测试注入的是替身仓库，同步点自然跟着用替身，不必改几十处构造调用。
     */
    private val todoReminderSync: TodoReminderSync = TodoReminderSync(reminderRepository),
    /** 复习计划 → REVIEW 提醒的同步点，同上。 */
    private val reviewReminderSync: ReviewReminderSync = ReviewReminderSync(reminderRepository),
    private val mediaRepository: MediaRepository,
    private val blockClipboardManager: BlockClipboardManager,
    private val aiTaskManager: AiTaskManager,
    private val refManager: AiRefManager,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(NoteUiState())
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    private val _blocks = mutableStateListOf<Block>().apply { addAll(defaultBlocks()) }
    private val _uiEvents = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = _uiEvents.receiveAsFlow()

    private val saveMutex = Mutex()
    private var saveJob: Job? = null
    private var saveVersion: Long = 0L
    private var nextBlockId: Long = -1L
    private var nextCardId: Long = TEMP_CARD_ID_SEED
    private var currentCardId: Long = TEMP_CARD_ID_SEED
    private var _isDragging = false
    private val pendingRemovals = mutableMapOf<Long, PendingBlockRemoval>()
    private val removalConfirmJobs = mutableMapOf<Long, Job>()
    private var nextRemovalToken: Long = 1L
    private val _branchExpandedStates = mutableMapOf<Long, Boolean>()
    private var todoObservationJob: Job? = null
    private var observedTodoNoteId: Long = 0L
    private var recordingReviewPlanId: Long? = null
    /** 上一次 AI 状态广播里处于生成中的任务 id，用于识别「刚刚成功结束」。 */
    private var previouslyActiveAiTaskIds: Set<String> = emptySet()

    init {
        currentCardId = nextCardId--
        _uiState.update { state ->
            state.copy(cards = knowledgeCardsFromBlocks(state.title, state.blocks))
        }
        load(NoteRouteArgs.noteId(savedStateHandle))
        observeTags()
        observeAiTasks()
    }

    /**
     * 只读浏览态下的重新载入。
     *
     * 笔记正文可能在别处被改掉（最典型的是 AI 工具 create_note / update_note 落库），
     * 而 [load] 是一次性快照读取，不会自动跟随数据库变化。用户从助手页切回来、
     * 或停留在本页时后台 AI 任务刚好写完这篇笔记，都必须重新拉一次，否则会看到旧内容。
     *
     * 编辑态下不做刷新：此时屏幕上的内容才是最新事实，不能被覆盖。
     */
    fun refreshIfBrowsing() {
        val state = _uiState.value
        if (state.isEditing || state.isLoading) return
        val id = state.noteId
        if (id <= 0L) return
        load(id)
    }

    fun load(noteId: Long) {
        if (noteId <= 0L) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = noteRepository.getNoteById(noteId)) {
                is RepositoryResult.Success -> {
                    val note = result.data
                    val isEditing = note == null
                    currentCardId = nextCardId--
                    replaceBlocks(note?.blocks?.ifEmpty { defaultBlocks() } ?: defaultBlocks())
                    initBranchExpandedStates()
                    val loadedBlocks = blocksForState()
                    val cards = note?.cards?.takeIf { it.isNotEmpty() }
                        ?: knowledgeCardsFromBlocks(note?.title.orEmpty(), loadedBlocks)
                    val activeCardId = if (isEditing) cards.firstOrNull()?.id ?: currentCardId else null
                    currentCardId = activeCardId ?: currentCardId
                    replaceBlocks(cards.find { it.id == currentCardId }?.blocks ?: loadedBlocks)
                    _uiState.update {
                        it.copy(
                            noteId = note?.id ?: 0L,
                            title = note?.title.orEmpty(),
                            blocks = blocksForState(),
                            cards = cards.map { card ->
                                card.copy(isFocused = activeCardId != null && card.id == activeCardId)
                            },
                            activeCardId = activeCardId,
                            selectedTags = note?.tags.orEmpty(),
                            todoItems = emptyList(),
                            reviewPlan = null,
                            isReviewDue = false,
                            isEditing = isEditing,
                            isLoading = false,
                            error = null,
                            branchExpandedStates = _branchExpandedStates.toMap()
                        )
                    }
                    note?.let {
                        observeTodos(it.id)
                        loadAndApplyReviewPlan(it.id)
                    } ?: stopObservingTodos()
                }
                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, error = result.message)
                    }
                }
            }
        }
    }

    /**
     * 笔记标题**独立于任何卡片标题**。
     *
     * 曾经这里会把新值同步进「当前卡片」的标题，想让单卡笔记少维护一个字段；
     * 但「新增知识点 → 改笔记标题」时 currentCardId 正好指向新卡，新卡还没起名
     * 就被覆盖成笔记标题（用户报告：「新增知识点时，标题取的是笔记标题」）。
     * 标题一律各归各：改笔记标题只改笔记标题。
     */
    fun onTitleChange(value: String) {
        _uiState.update { state ->
            state.copy(title = value)
        }
        scheduleSave()
    }

    /**
     * 卡片标题同样独立：改小节标题**不回写**笔记标题。
     *
     * 这是与 [onTitleChange] 旧行为对称的另一半（原先是一套双向同步）。多卡场景下
     * 回写是数据损坏级别的意外——改第三个小节的标题，整篇笔记的标题被替掉。
     */
    fun onCardTitleChange(cardId: Long, value: String) {
        _uiState.update { state ->
            state.copy(
                cards = state.cards.map { card ->
                    if (card.id == cardId) card.copy(title = value) else card
                }
            )
        }
        scheduleSave()
    }

    fun focusCard(cardId: Long) {
        if (cardId == currentCardId) {
            _uiState.update { state ->
                state.copy(
                    activeCardId = cardId,
                    cards = state.cards.map { it.copy(isFocused = it.id == cardId) }
                )
            }
            return
        }

        val previousBlocks = blocksForState()
        ensureCurrentCardExists(previousBlocks)
        val targetCard = _uiState.value.cards.find { it.id == cardId }

        _uiState.update { state ->
            state.copy(
                activeCardId = cardId,
                cards = state.cards.map { card ->
                    when {
                        card.id == currentCardId -> card.copy(blocks = previousBlocks, isFocused = false)
                        card.id == cardId -> card.copy(isFocused = true)
                        else -> card.copy(isFocused = false)
                    }
                }
            )
        }

        currentCardId = cardId
        replaceBlocks(targetCard?.blocks?.ifEmpty { defaultBlocks() } ?: defaultBlocks())
        syncBlocksToState()
    }

    fun addKnowledgeCard() {
        val previousBlocks = blocksForState()
        ensureCurrentCardExists(previousBlocks)
        val newCardId = nextCardId--
        val newCard = KnowledgeCard(
            id = newCardId,
            title = "",
            blocks = emptyList(),
            isExpanded = true,
            isFocused = true
        )

        _uiState.update { state ->
            state.copy(
                activeCardId = newCardId,
                cards = state.cards.map { card ->
                    if (card.id == currentCardId) {
                        card.copy(blocks = previousBlocks, isFocused = false)
                    } else {
                        card.copy(isFocused = false)
                    }
                } + newCard
            )
        }

        currentCardId = newCardId
        replaceBlocks(defaultBlocks())
        syncBlocksToState()
        scheduleSave()
    }

    private fun ensureCurrentCardExists(currentBlocks: List<Block>) {
        val state = _uiState.value
        if (state.cards.any { it.id == currentCardId }) return
        val fallbackCard = KnowledgeCard(
            id = currentCardId,
            // 兜底卡必须是「无名」的：曾经填 state.title，于是任何一次 currentCardId
            // 与卡片列表脱节（旧 startEditing 的误判就是这样）都会兜出一张
            // 「标题同笔记标题」的卡——这正是「新卡片标题继承笔记标题」的另一个来源。
            title = "",
            blocks = currentBlocks,
            isExpanded = true,
            isFocused = false
        )
        _uiState.update { it.copy(cards = it.cards + fallbackCard) }
    }

    fun removeKnowledgeCard(cardId: Long) {
        val currentCards = _uiState.value.cards
        if (currentCards.size <= 1) return

        val removedCard = currentCards.find { it.id == cardId } ?: return
        val remainingCards = currentCards.filter { it.id != cardId }
        removedCard.blocks.forEach { block ->
            _branchExpandedStates.remove(block.id)
        }

        val newActiveCardId = when {
            currentCardId != cardId -> currentCardId
            remainingCards.isNotEmpty() -> remainingCards.first().id
            else -> currentCardId
        }

        if (currentCardId == cardId) {
            currentCardId = newActiveCardId
        }

        _uiState.update { state ->
            state.copy(
                cards = remainingCards.map { card ->
                    card.copy(isFocused = card.id == newActiveCardId)
                },
                activeCardId = newActiveCardId
            )
        }

        val targetCard = _uiState.value.cards.find { it.id == currentCardId }
        replaceBlocks(targetCard?.blocks?.ifEmpty { defaultBlocks() } ?: defaultBlocks())
        syncBlocksToState()
        scheduleSave()
    }

    fun onBlockContentChange(blockId: Long, value: String) {
        val index = _blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val block = _blocks[index]
        _blocks[index] = block.copy(content = value)
        syncBlocksToState()
        scheduleSave()
    }

    /**
     * 折叠 / 展开一张小节。
     *
     * 用的是 `KnowledgeCard.isExpanded`。折叠态是**会话内状态**：数据库里没有这一列，
     * 所以重开笔记会回到 `CardMapper.toDomain` 给的默认值 —— 那里给的是 `false`
     * （**小节默认收起**：打开笔记先看到目录，点开某节才看内容）。
     */
    fun toggleCardExpanded(cardId: Long) {
        _uiState.update { state ->
            state.copy(
                cards = state.cards.map { card ->
                    if (card.id == cardId) card.copy(isExpanded = !card.isExpanded) else card
                }
            )
        }
    }

    /** 全部折叠 / 全部展开（长按折叠箭头触发）。任一展开 → 全部折叠，否则全部展开。 */
    fun toggleAllCardsExpanded() {
        _uiState.update { state ->
            val anyExpanded = state.cards.any { it.isExpanded }
            state.copy(cards = state.cards.map { it.copy(isExpanded = !anyExpanded) })
        }
    }

    fun addBlock(type: BlockType, parentBranchId: Long? = null) {
        val content = defaultContentFor(type)
        val blockId = nextBlockId--
        _blocks += Block(
            id = blockId,
            type = type,
            content = content,
            sortOrder = _blocks.size,
            parentBranchId = parentBranchId
        )
        if (type == BlockType.BRANCH) {
            _branchExpandedStates[blockId] = true
        }
        syncBlocksToState()
        if (content.isNotBlank() || type == BlockType.DIVIDER || type == BlockType.TODO) {
            scheduleSave()
        }
    }

    fun addImageBlock(uri: String, parentBranchId: Long? = null) {
        _blocks += Block(
            id = nextBlockId--,
            type = BlockType.IMAGE,
            content = uri,
            sortOrder = _blocks.size,
            parentBranchId = parentBranchId
        )
        syncBlocksToState()
        scheduleSave()
    }

    fun addImageFromGallery(uri: Uri, parentBranchId: Long? = null) {
        _uiState.update { it.copy(isProcessingImage = true, error = null) }
        viewModelScope.launch {
            try {
                val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
                val extension = resolveImageExtension(uri)
                val fileName = "${System.currentTimeMillis()}_${UUID.randomUUID()}.$extension"
                val destFile = File(imagesDir, fileName)

                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("无法读取所选图片")

                val internalUri = Uri.fromFile(destFile).toString()
                val media = Media(
                    uri = internalUri,
                    size = destFile.length(),
                    createdAt = System.currentTimeMillis()
                )
                when (val result = mediaRepository.insertMedia(media)) {
                    is RepositoryResult.Success -> {
                        addImageBlock(ImageBlockContent.fromMedia(result.data, internalUri), parentBranchId)
                        _uiState.update { it.copy(isProcessingImage = false, error = null) }
                    }
                    is RepositoryResult.Error -> {
                        destFile.deleteSilently()
                        _uiState.update { it.copy(isProcessingImage = false, error = result.message) }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isProcessingImage = false, error = e.message ?: "保存图片失败") }
            }
        }
    }

    fun addBlockToActiveCard(type: BlockType) {
        val activeCardId = _uiState.value.activeCardId ?: return
        focusCard(activeCardId)
        addBlock(type)
    }

    fun addImageToActiveCardFromGallery(uri: Uri) {
        val activeCardId = _uiState.value.activeCardId ?: return
        focusCard(activeCardId)
        addImageFromGallery(uri)
    }

    fun addImageToActiveCardFromCamera(uri: String) {
        val activeCardId = _uiState.value.activeCardId ?: return
        focusCard(activeCardId)
        addImageBlock(uri)
    }

    fun insertBlockAt(index: Int, type: BlockType) {        val clampedIndex = index.coerceIn(0, _blocks.size)
        val newBlock = Block(
            id = nextBlockId--,
            type = type,
            content = defaultContentFor(type),
            sortOrder = clampedIndex,
            parentBranchId = null
        )
        if (type == BlockType.BRANCH) {
            _branchExpandedStates[newBlock.id] = true
        }
        _blocks.add(clampedIndex, newBlock)
        recalculateSortOrders()
        syncBlocksToState()
        scheduleSave()
    }

    /**
     * 在某块**之后**插入一块，并带上内容。
     *
     * 与 [insertBlockAt] 的区别是"带内容 + 按块定位"：给"把行内公式提升成公式块"这类
     * 搬运动作用 —— 调用方手里只有源块的 id 和一段要搬过去的源码（见
     * [com.example.zhilu.domain.markup.InlineFormulaConversion]）。
     *
     * 新块沿用源块的 `parentBranchId`：在分支子块里提升公式，公式块该留在同一个分支里，
     * 而不是跳到分支外面去。
     */
    fun insertBlockAfter(afterBlockId: Long, type: BlockType, content: String) {
        val sourceIndex = _blocks.indexOfFirst { it.id == afterBlockId }
        val insertIndex = if (sourceIndex < 0) _blocks.size else sourceIndex + 1
        val parentBranchId = _blocks.getOrNull(sourceIndex)?.parentBranchId
        val newBlock = Block(
            id = nextBlockId--,
            type = type,
            content = content,
            sortOrder = insertIndex,
            parentBranchId = parentBranchId
        )
        if (type == BlockType.BRANCH) {
            _branchExpandedStates[newBlock.id] = true
        }
        _blocks.add(insertIndex, newBlock)
        recalculateSortOrders()
        syncBlocksToState()
        scheduleSave()
    }

    /**
     * 反向：「公式块 → 行内公式」。
     *
     * **就地**把它变成文本块，内容是 `$源码$`，而不是搬进另一段话里 ——
     * 跨块搬运要同时"删本块 + 改另一个块的正文"，两个动作任何一个出错都会丢内容；
     * 就地转换只有一个动作。转完它就是一行普通文字（公式会立刻按行内原子渲染出来），
     * 用户想放进哪句话里，自己剪切即可。
     */
    fun demoteLatexBlock(blockId: Long) {
        val index = _blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val block = _blocks[index]
        if (block.type != BlockType.LATEX) return
        val inline = InlineFormulaConversion.demoteToInline(block.content) ?: return
        _blocks[index] = block.copy(type = BlockType.TEXT, content = inline)
        syncBlocksToState()
        scheduleSave()
    }

    fun copyBlock(blockId: Long) {
        val block = _blocks.find { it.id == blockId } ?: return
        val children = if (block.type == BlockType.BRANCH) {
            _blocks.filter { it.parentBranchId == blockId }
        } else emptyList()
        val data = BlockClipboardData(
            block = block,
            children = children.map { BlockClipboardData(it) }
        )
        blockClipboardManager.copyBlock(data)
    }

    fun hasBlockInClipboard(): Boolean = blockClipboardManager.hasBlock()

    fun pasteBlock(targetIndex: Int?) {
        val template = blockClipboardManager.readBlock() ?: return
        val clampedIndex = targetIndex?.coerceIn(0, _blocks.size) ?: _blocks.size

        // 摊平 + 发新 id 的规则在 domain/model/BlockOrdering（纯函数，有单测）——
        // 子块指向新父 id 这一步曾经因为"都留默认 0"而把多个分支的子块全挂到同一个父块下。
        val newBlocks = BlockOrdering.revive(
            flat = BlockOrdering.flatten(template),
            nextId = { nextBlockId-- }
        )
        if (newBlocks.isEmpty()) return
        newBlocks.filter { it.type == BlockType.BRANCH }
            .forEach { _branchExpandedStates[it.id] = true }

        _blocks.addAll(clampedIndex, newBlocks)

        recalculateSortOrders()
        syncBlocksToState()
        scheduleSave()
    }

    private fun recalculateSortOrders() {
        val renumbered = BlockOrdering.renumber(_blocks)
        _blocks.clear()
        _blocks.addAll(renumbered)
    }

    private fun resolveImageExtension(uri: Uri): String {
        val mimeType = context.contentResolver.getType(uri)
        return when {
            mimeType?.contains("png", ignoreCase = true) == true -> "png"
            mimeType?.contains("gif", ignoreCase = true) == true -> "gif"
            mimeType?.contains("webp", ignoreCase = true) == true -> "webp"
            else -> "jpg"
        }
    }

    private fun File.deleteSilently() {
        runCatching { delete() }
    }

    fun setBlockLanguage(blockId: Long, language: String) {
        val index = _blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        val block = _blocks[index]
        _blocks[index] = block.copy(language = language)
        syncBlocksToState()
        scheduleSave()
    }

    fun setDragging(dragging: Boolean) {
        val wasDragging = _isDragging
        _isDragging = dragging
        // 拖动期间 moveBlock 不落盘，松手时才补一次保存。
        if (wasDragging && !dragging) scheduleSave()
    }

    fun moveBlock(fromId: Long, toId: Long) {
        // 整表重排的规则在 domain/model/BlockOrdering（纯函数，有单测）：
        // 分支块要连子块一起搬、子块不参与卡片级移动、方向由原始前后关系决定。
        val moved = BlockOrdering.move(_blocks, fromId, toId) ?: return
        _blocks.clear()
        _blocks.addAll(moved)
        syncBlocksToState()
        if (!_isDragging) scheduleSave()
    }

    /**
     * 设置 / 取消某一条块的**块级语义标记**（L1）。
     *
     * 块级标记是块自己的属性，不涉及文本，所以直接改内存块表再落盘即可。
     * `null` 表示取消标记。分支子块也能标记（`_blocks` 里是扁平的，含子块）。
     */
    fun setBlockEmphasis(blockId: Long, tone: EmphasisTone?) {
        val index = _blocks.indexOfFirst { it.id == blockId }
        if (index < 0) return
        if (_blocks[index].emphasis == tone) return
        _blocks[index] = _blocks[index].copy(emphasis = tone)
        syncBlocksToState()
        scheduleSave()
    }

    fun removeBlock(blockId: Long) {
        val block = _blocks.find { it.id == blockId } ?: return
        if (block.type == BlockType.TODO && _uiState.value.todoItems.isNotEmpty()) {
            _uiState.update { it.copy(error = "TODO block still has linked items.") }
            return
        }
        val removedIndex = _blocks.indexOfFirst { it.id == blockId }
        val removed = if (block.type == BlockType.BRANCH) {
            val group = listOf(block) + _blocks.filter { it.parentBranchId == blockId }
            _blocks.removeAll(group)
            group.first()
        } else {
            _blocks.removeAt(removedIndex)
        }
        if (block.type == BlockType.BRANCH) {
            _branchExpandedStates.remove(blockId)
        }
        if (_blocks.isEmpty()) {
            _blocks.addAll(defaultBlocks())
        }
        val token = nextRemovalToken++
        pendingRemovals[token] = PendingBlockRemoval(
            block = removed,
            index = removedIndex.coerceAtMost(_blocks.size)
        )
        syncBlocksToState()
        _uiEvents.trySend(UiEvent.ShowUndoSnackbar(removed, removedIndex, token))
        removalConfirmJobs[token]?.cancel()
        removalConfirmJobs[token] = viewModelScope.launch {
            delay(5_000)
            confirmRemoveBlock(token)
        }
        scheduleSave()
    }

    fun undoRemoveBlock() {
        undoRemoveBlock(pendingRemovals.keys.lastOrNull() ?: return)
    }

    fun undoRemoveBlock(token: Long) {
        val removal = pendingRemovals.remove(token) ?: return
        val isOnlyDefaultBlankBlock = _blocks.size == 1 &&
            _blocks.single().type == BlockType.TEXT &&
            _blocks.single().content.isBlank()
        if (isOnlyDefaultBlankBlock) {
            _blocks.clear()
        }
        _blocks.add(removal.index.coerceIn(0, _blocks.size), removal.block)
        removalConfirmJobs.remove(token)?.cancel()
        syncBlocksToState()
        scheduleSave()
    }

    fun confirmRemoveBlock() {
        confirmRemoveBlock(pendingRemovals.keys.lastOrNull() ?: return)
    }

    fun confirmRemoveBlock(token: Long) {
        pendingRemovals.remove(token)
        removalConfirmJobs.remove(token)?.cancel()
    }

    fun isBranchExpanded(branchId: Long): Boolean =
        _branchExpandedStates[branchId] ?: true

    fun toggleBranchExpanded(branchId: Long) {
        _branchExpandedStates[branchId] = !isBranchExpanded(branchId)
        syncBlocksToState()
    }

    fun addBranchChildBlock(branchId: Long, type: BlockType) {
        val branchIndex = _blocks.indexOfFirst { it.id == branchId && it.type == BlockType.BRANCH }
        if (branchIndex < 0) return
        val childIndices = _blocks.indices.filter { _blocks[it].parentBranchId == branchId }
        val insertIndex = if (childIndices.isEmpty()) branchIndex + 1 else childIndices.last() + 1
        _blocks.add(
            insertIndex.coerceIn(0, _blocks.size),
            Block(
                id = nextBlockId--,
                type = type,
                content = defaultContentFor(type),
                sortOrder = insertIndex,
                parentBranchId = branchId
            )
        )
        syncBlocksToState()
        scheduleSave()
    }

    fun addBranchChildImageBlock(branchId: Long, uri: String) {
        val branchIndex = _blocks.indexOfFirst { it.id == branchId && it.type == BlockType.BRANCH }
        if (branchIndex < 0) return
        val childIndices = _blocks.indices.filter { _blocks[it].parentBranchId == branchId }
        val insertIndex = if (childIndices.isEmpty()) branchIndex + 1 else childIndices.last() + 1
        _blocks.add(
            insertIndex.coerceIn(0, _blocks.size),
            Block(
                id = nextBlockId--,
                type = BlockType.IMAGE,
                content = uri,
                sortOrder = insertIndex,
                parentBranchId = branchId
            )
        )
        syncBlocksToState()
        scheduleSave()
    }

    private fun initBranchExpandedStates() {
        _branchExpandedStates.clear()
        _blocks.forEach { block ->
            if (block.type == BlockType.BRANCH) {
                _branchExpandedStates[block.id] = true
            }
        }
    }

    fun toggleTag(tag: Tag) {
        _uiState.update { state ->
            val selected = if (state.selectedTags.any { it.id == tag.id }) {
                state.selectedTags.filterNot { it.id == tag.id }
            } else {
                state.selectedTags + tag
            }
            state.copy(selectedTags = selected)
        }
        scheduleSave()
    }

    fun createTag(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            when (val existing = tagRepository.getTagByName(trimmed)) {
                is RepositoryResult.Success -> {
                    val tag = existing.data ?: createNewTag(trimmed)
                    toggleTag(tag)
                }
                is RepositoryResult.Error -> _uiState.update { it.copy(error = existing.message) }
            }
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        val version = nextSaveVersion()
        viewModelScope.launch { saveInternal(version = version, exitEditMode = true) }
    }

    fun startEditing() {
        val activeCardId = _uiState.value.activeCardId
            ?: _uiState.value.cards.firstOrNull()?.id
            ?: currentCardId
        // 浏览态下 currentCardId 还是 init 里的临时负值，直接 focusCard(activeCardId) 会
        // 走到「切换卡片」分支，`ensureCurrentCardExists` 误判「当前卡片不存在」而凭空
        // 多兜一张卡（title 同笔记标题）。所以这里先把 currentCardId 对齐到真实卡片，
        // 再手动完成 focusCard 的「载入活跃卡片块」——只做块切换，不做任何卡片增删。
        currentCardId = activeCardId
        val targetCard = _uiState.value.cards.find { it.id == activeCardId }
        replaceBlocks(targetCard?.blocks?.ifEmpty { defaultBlocks() } ?: defaultBlocks())
        _uiState.update { state ->
            state.copy(
                isEditing = true,
                activeCardId = activeCardId,
                cards = state.cards.map { card ->
                    card.copy(isFocused = card.id == activeCardId)
                }
            )
        }
        syncBlocksToState()
    }

    fun startReviewPlan(now: Long = System.currentTimeMillis()) {
        val noteId = _uiState.value.noteId
        if (noteId <= 0L) return
        viewModelScope.launch {
            when (val result = reviewRepository.startPlan(noteId, now)) {
                is RepositoryResult.Success -> {
                    val plan = result.data
                    _uiState.update {
                        it.copy(reviewPlan = plan, isReviewDue = plan.isDue(now), error = null)
                    }
                    scheduleReviewReminder(plan)
                }
                is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun recordReview(rating: ReviewRating, now: Long = System.currentTimeMillis()) {
        val plan = _uiState.value.reviewPlan?.takeIf { it.enabled } ?: return
        if (recordingReviewPlanId == plan.id) return
        recordingReviewPlanId = plan.id
        _uiState.update { it.copy(isRecordingReview = true) }
        viewModelScope.launch {
            try {
                when (val result = reviewRepository.recordReview(plan, rating, now)) {
                    is RepositoryResult.Success -> {
                        val updatedPlan = result.data
                        _uiState.update {
                            it.copy(reviewPlan = updatedPlan, isReviewDue = updatedPlan.isDue(now), error = null)
                        }
                        scheduleReviewReminder(updatedPlan)
                    }
                    is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
                }
            } finally {
                if (recordingReviewPlanId == plan.id) {
                    recordingReviewPlanId = null
                    _uiState.update { it.copy(isRecordingReview = false) }
                }
            }
        }
    }

    fun disableReviewPlan() {
        val plan = _uiState.value.reviewPlan ?: return
        viewModelScope.launch {
            when (val result = reviewRepository.disablePlan(plan.noteId)) {
                is RepositoryResult.Success -> {
                    val disabledPlan = plan.copy(enabled = false, nextReviewAt = null)
                    _uiState.update {
                        it.copy(reviewPlan = disabledPlan, isReviewDue = false, error = null)
                    }
                    when (val cancel = reminderRepository.cancel(ReminderType.REVIEW, plan.id)) {
                        is RepositoryResult.Success -> Unit
                        is RepositoryResult.Error -> _uiState.update { it.copy(error = cancel.message) }
                    }
                }
                is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    suspend fun createTodo(content: String, remindAt: Long?): Boolean {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return false
        val noteId = ensureNoteIdForTodo()
        if (noteId <= 0L) return false
        val todo = TodoItem(
            noteId = noteId,
            content = trimmed,
            remindAt = remindAt,
            sortOrder = _uiState.value.todoItems.size
        )
        return when (val result = todoRepository.addTodo(todo)) {
            is RepositoryResult.Success -> {
                val todoId = result.data
                if (remindAt != null) {
                    scheduleTodoReminder(todoId = todoId, noteId = noteId, dueAt = remindAt)
                }
                true
            }
            is RepositoryResult.Error -> {
                _uiState.update { it.copy(error = result.message) }
                false
            }
        }
    }

    fun updateTodo(todo: TodoItem) {
        if (todo.id <= 0L) return
        viewModelScope.launch {
            when (val result = todoRepository.updateTodo(todo)) {
                is RepositoryResult.Success -> reconcileTodoReminder(todo)
                is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun completeTodo(todoId: Long) {
        if (todoId <= 0L) return
        viewModelScope.launch {
            when (val result = todoRepository.completeTodo(todoId, System.currentTimeMillis())) {
                is RepositoryResult.Success -> {
                    when (val reminder = reminderRepository.markDone(ReminderType.TODO, todoId)) {
                        is RepositoryResult.Success -> Unit
                        is RepositoryResult.Error -> _uiState.update { it.copy(error = reminder.message) }
                    }
                }
                is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    fun toggleCompletedTodos() {
        _uiState.update { it.copy(showCompletedTodos = !it.showCompletedTodos) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ---- AI 引用与生成占用 ----

    /** 把当前整篇笔记作为引用目标交给助手。 */
    fun citeNoteToAi() {
        val state = _uiState.value
        refManager.addPending(
            AiRef(
                kind = AiRefKind.NOTE,
                noteId = state.noteId,
                title = state.title,
                // 快照在引用这一刻冻结：编辑器 id 是内存坐标（未保存笔记 noteId=0、
                // 新建块 id 为负），AI 侧按 id 回查必然落空，内容只能从这里带走。
                snapshot = AiRefSnapshot.forNote(state.title, state.toNote().contentBlocks)
            )
        )
    }

    /** 把某张知识卡片作为引用目标交给助手。 */
    fun citeCardToAi(card: KnowledgeCard) {
        val state = _uiState.value
        refManager.addPending(
            AiRef(
                kind = AiRefKind.CARD,
                noteId = state.noteId,
                cardId = card.id,
                title = cardLabel(card, state.title),
                snapshot = AiRefSnapshot.forCard(state.title, card.title, card.blocks)
            )
        )
    }

    /**
     * 卡片引用标签：优先卡片标题；标题为空（默认「知识小点」）时退而使用卡内首个有内容的块摘要，
     * 最后才回落笔记标题——否则多张无标题卡片会显示成同一个标签，无法区分引用目标。
     */
    private fun cardLabel(card: KnowledgeCard, noteTitle: String): String {
        val title = card.title.trim()
        if (title.isNotEmpty()) return title
        val snippet = card.blocks.firstOrNull { it.content.isNotBlank() }
            ?.let { blockTitleSnippet(it) }
            .orEmpty()
        return snippet.ifEmpty { noteTitle }
    }

    /** 把某个块作为引用目标交给助手。 */
    fun citeBlockToAi(block: Block) {
        val state = _uiState.value
        val card = state.cards.firstOrNull { it.id == block.cardId }
        refManager.addPending(
            AiRef(
                kind = AiRefKind.BLOCK,
                noteId = state.noteId,
                cardId = block.cardId ?: card?.id,
                blockId = block.id,
                title = blockTitleSnippet(block),
                snapshot = AiRefSnapshot.forBlock(state.title, block)
            )
        )
    }

    private fun blockTitleSnippet(block: Block): String {
        val raw = block.content.replace('\n', ' ').trim()
        return raw.take(24).ifBlank {
            when (block.type) {
                BlockType.IMAGE -> "[图片]"
                BlockType.DIVIDER -> "[分割线]"
                BlockType.BRANCH -> "[分支]"
                else -> "[块]"
            }
        }
    }

    private fun observeAiTasks() {
        viewModelScope.launch {
            aiTaskManager.state.collect { taskState ->
                val locked = taskState.lockedRefs
                val current = _uiState.value
                val noteId = current.noteId
                val generatingCardIds = current.cards.filter { card ->
                    locked.any { ref ->
                        ref.locksCard(noteId, card.id, card.blocks.map { it.id })
                    }
                }.map { it.id }.toSet()
                val generatingBlockIds = current.cards.flatMap { card ->
                    card.blocks.filter { block ->
                        locked.any { ref -> ref.locksBlock(noteId, card.id, block.id) }
                    }
                }.map { it.id }.toSet()
                _uiState.update {
                    it.copy(
                        generatingCardIds = generatingCardIds,
                        generatingBlockIds = generatingBlockIds
                    )
                }

                // 生成占用解除：本页可能刚被 AI 工具改过内容，浏览态下重新拉取。
                if (justFinishedSuccessfully(taskState.tasks)) {
                    refreshIfBrowsing()
                }
            }
        }
    }

    /**
     * 上一次广播里还在跑的任务，现在是否已经成功结束。
     * 用状态迁移判断，避免每次广播都触发一次重载。
     */
    private fun justFinishedSuccessfully(tasks: List<AiTask>): Boolean {
        val activeIds = tasks.filter { it.isActive }.map { it.id }.toSet()
        val finished = previouslyActiveAiTaskIds
            .filter { it !in activeIds }
            .mapNotNull { id -> tasks.firstOrNull { it.id == id } }
            .any { it.phase == AiTaskPhase.SUCCEEDED }
        previouslyActiveAiTaskIds = activeIds
        return finished
    }

    fun shareNote(
        format: ShareFormat,
        onReady: (File, String) -> Unit
    ) {
        viewModelScope.launch {
            val note = _uiState.value.toNote()
            val mediaResult = mediaRepository.getAllMedia()
            val media = (mediaResult as? RepositoryResult.Success)?.data.orEmpty()
            val noteMedia = media.filter { m ->
                // 精确匹配：优先按块里登记的 mediaId，其次按块是否内嵌该媒体的 URI。
                // 早期用 content.contains(m.id.toString()) 做子串匹配，一旦 URL 里碰巧出现相同数字
                // 就会误收无关媒体，而真正引用的媒体反而可能漏掉。
                note.contentBlocks.any { block ->
                    block.type == BlockType.IMAGE && (
                        ImageBlockContent.mediaId(block.content) == m.id ||
                            block.content.contains(m.uri)
                        )
                }
            }
            val mediaFileManager = MediaFileManager(context)
            when (format) {
                ShareFormat.HTML -> {
                    HtmlExporter(context, mediaFileManager).exportNote(note, noteMedia)
                        .onSuccess { html ->
                            val file = File(context.cacheDir, "share_${System.currentTimeMillis()}.html")
                            file.writeText(html)
                            onReady(file, "text/html")
                        }
                        .onFailure { error ->
                            _uiState.update { it.copy(error = "HTML 导出失败：${error.message}") }
                        }
                }
                ShareFormat.MARKDOWN -> {
                    runCatching {
                        val md = MarkdownExporter.exportNoteWithBase64(
                            note = note,
                            media = noteMedia,
                            mediaFileManager = mediaFileManager,
                            context = context
                        )
                        val file = File(context.cacheDir, "share_${System.currentTimeMillis()}.md")
                        file.writeText(md)
                        onReady(file, "text/markdown")
                    }.onFailure { error ->
                        _uiState.update { it.copy(error = "Markdown 导出失败：${error.message}") }
                    }
                }
                ShareFormat.DTK -> {
                    DtkExporter(context, mediaFileManager).exportNote(note, noteMedia)
                        .onSuccess { file ->
                            onReady(file, "application/zip")
                        }
                        .onFailure { error ->
                            _uiState.update { it.copy(error = ".dtk 导出失败：${error.message}") }
                        }
                }
            }
        }
    }

    private fun replaceBlocks(blocks: List<Block>) {
        _blocks.clear()
        _blocks.addAll(blocks.ifEmpty { defaultBlocks() })
    }

    private fun syncBlocksToState() {
        val blocks = blocksForState()
        _uiState.update { state ->
            val cards = state.cards.takeIf { it.isNotEmpty() }
                ?: knowledgeCardsFromBlocks(state.title, blocks)
            state.copy(
                blocks = blocks,
                cards = cards.map { card ->
                    if (card.id == currentCardId) card.copy(blocks = blocks) else card
                },
                branchExpandedStates = _branchExpandedStates.toMap()
            )
        }
    }

    private fun blocksForState(): List<Block> =
        _blocks.mapIndexed { index, block -> block.copy(sortOrder = index) }

    private fun knowledgeCardsFromBlocks(
        title: String,
        blocks: List<Block>
    ): List<KnowledgeCard> = listOf(
        KnowledgeCard(
            id = currentCardId,
            title = title,
            blocks = blocks,
            isExpanded = true,
            isFocused = false
        )
    )

    private suspend fun loadAndApplyReviewPlan(noteId: Long) {
        when (val result = reviewRepository.getPlanByNoteId(noteId)) {
            is RepositoryResult.Success -> {
                val plan = result.data
                _uiState.update { it.copy(reviewPlan = plan, isReviewDue = plan.isDue()) }
                plan?.let { reconcileLoadedReviewReminder(it) }
            }
            is RepositoryResult.Error -> {
                _uiState.update { it.copy(error = result.message) }
            }
        }
    }

    private suspend fun reconcileLoadedReviewReminder(plan: ReviewPlan) {
        if (plan.nextReviewAt == null) {
            scheduleReviewReminder(plan)
            return
        }
        if (!plan.enabled) return

        when (val active = reminderRepository.getActiveReminder(ReminderType.REVIEW, plan.id)) {
            is RepositoryResult.Success -> {
                if (active.data == null) {
                    scheduleReviewReminder(plan)
                }
            }
            is RepositoryResult.Error -> _uiState.update { it.copy(error = active.message) }
        }
    }

    /**
     * 复习计划变化后重建 REVIEW 提醒。
     *
     * 调共用同步点而不是自己 `upsertScheduled` —— 这里原先手写了一份等价逻辑
     * （连 `"review-${plan.id}".hashCode()` 都复制了一遍），于是"REVIEW 提醒只有一条来源"
     * 的说法在笔记页并不成立：通知 id 的派生规则一变，两处就会算出不同的 id。
     */
    private suspend fun scheduleReviewReminder(plan: ReviewPlan) {
        syncReminder { reviewReminderSync.sync(plan) }
    }

    private fun observeTags() {
        viewModelScope.launch {
            tagRepository.getAllTags().collect { result ->
                when (result) {
                    is RepositoryResult.Success -> _uiState.update {
                        it.copy(availableTags = result.data)
                    }
                    is RepositoryResult.Error -> _uiState.update {
                        it.copy(error = result.message)
                    }
                }
            }
        }
    }

    private fun observeTodos(noteId: Long) {
        if (noteId <= 0L || observedTodoNoteId == noteId) return
        todoObservationJob?.cancel()
        observedTodoNoteId = noteId
        todoObservationJob = viewModelScope.launch {
            todoRepository.observeByNoteId(noteId).collect { result ->
                when (result) {
                    is RepositoryResult.Success -> _uiState.update {
                        it.copy(todoItems = result.data, error = null)
                    }
                    is RepositoryResult.Error -> _uiState.update {
                        it.copy(error = result.message)
                    }
                }
            }
        }
    }

    private fun stopObservingTodos() {
        todoObservationJob?.cancel()
        todoObservationJob = null
        observedTodoNoteId = 0L
        _uiState.update { it.copy(todoItems = emptyList()) }
    }

    private suspend fun ensureNoteIdForTodo(): Long {
        _uiState.value.noteId.takeIf { it > 0L }?.let { return it }

        return saveMutex.withLock {
            val currentNoteId = _uiState.value.noteId
            if (currentNoteId > 0L) return@withLock currentNoteId

            _uiState.update { it.copy(isSaving = true, saveStatus = SaveStatus.SAVING) }
            when (val result = noteRepository.insertNote(_uiState.value.toNote())) {
                is RepositoryResult.Success -> {
                    val savedNote = result.data
                    val newId = savedNote.id
                    _uiState.update {
                        it.copy(
                            noteId = newId,
                            isSaving = false,
                            saveStatus = SaveStatus.SAVED,
                            lastSavedAt = System.currentTimeMillis()
                        )
                    }
                    observeTodos(newId)
                    newId
                }
                is RepositoryResult.Error -> {
                    _uiState.update {
                        it.copy(isSaving = false, saveStatus = SaveStatus.ERROR, error = result.message)
                    }
                    0L
                }
            }
        }
    }

    private suspend fun createNewTag(name: String): Tag {
        val tag = Tag(
            name = name,
            color = TagCreationPalette[_uiState.value.availableTags.size % TagCreationPalette.size]
        )
        return when (val insert = tagRepository.insertTag(tag)) {
            is RepositoryResult.Success -> tag.copy(id = insert.data)
            is RepositoryResult.Error -> {
                _uiState.update { it.copy(error = insert.message) }
                tag
            }
        }
    }

    private fun scheduleSave() {
        if (_isDragging) return
        val version = nextSaveVersion()
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            viewModelScope.launch { saveInternal(version = version, exitEditMode = false) }
        }
    }

    private fun nextSaveVersion(): Long {
        saveVersion += 1
        _uiState.update { it.copy(isSaving = true, saveStatus = SaveStatus.SAVING) }
        return saveVersion
    }

    /**
     * 写库。成功后**只**采纳数据库分配的笔记 id，卡片 / 块的 id 保持本地值不变。
     *
     * 此前这里会把本地临时 id（新建时为负数）回写成数据库主键。这个回写有两个问题：
     *
     * 1. **它会把正在输入的输入框整块换掉。** 编辑页的 `LazyColumn` 用 `card.id` 作 key，
     *    key 一变 Compose 就当成一个新条目，正在聚焦的 `BasicTextField` 随之销毁重建，
     *    焦点丢失 → 键盘被收起。自动保存每 500ms 一次，于是「打字打到一半键盘没了」。
     * 2. **它根本没必要。** `NoteRepositoryImpl.updateNote` 是「按 noteId 全删再插」，
     *    而且卡片 / 块的主键在落库前会被显式归零交给数据库重新分配（见 `replaceCards`）。
     *    也就是说下一次保存根本不依赖上一次返回的主键——唯一需要记住的是 note id，
     *    用来决定走 insert 还是 update。
     *
     * 保留本地 id 还有两个附带的正收益：`branchExpandedStates`、AI 生成占用的
     * `generatingCardIds` / `generatingBlockIds` 都是按本地 id 记的，不再因为回写而失效。
     */
    private suspend fun saveInternal(version: Long, exitEditMode: Boolean) {
        saveMutex.withLock {
            val state = _uiState.value
            if (state.noteId == 0L && state.title.isBlank() && state.blocks.all { it.content.isBlank() }) {
                if (version == saveVersion) {
                    _uiState.update { it.copy(isSaving = false, saveStatus = SaveStatus.IDLE) }
                }
                return
            }
            _uiState.update { it.copy(isSaving = true, saveStatus = SaveStatus.SAVING) }
            val note = state.toNote()
            val result = if (state.noteId == 0L) {
                noteRepository.insertNote(note)
            } else {
                noteRepository.updateNote(note)
            }
            when (result) {
                is RepositoryResult.Success -> {
                    val newId = result.data.id
                    if (version == saveVersion) {
                        _uiState.update {
                            it.copy(
                                noteId = newId,
                                isEditing = if (exitEditMode) false else it.isEditing,
                                isSaving = false,
                                saveStatus = SaveStatus.SAVED,
                                lastSavedAt = System.currentTimeMillis()
                            )
                        }
                    } else if (state.noteId == 0L && newId > 0L) {
                        _uiState.update { it.copy(noteId = newId) }
                    }
                    if (state.noteId == 0L && newId > 0L) {
                        observeTodos(newId)
                    }
                    // 只把 noteId 带下去，卡片 / 块的 id 一律不动。见 saveInternal 的 KDoc。
                    _blocks.replaceAll { block -> block.copy(noteId = newId) }
                }
                is RepositoryResult.Error -> {
                    if (version == saveVersion) {
                        _uiState.update {
                            it.copy(isSaving = false, saveStatus = SaveStatus.ERROR, error = result.message)
                        }
                    }
                }
            }
        }
    }

    private fun defaultBlocks(): List<Block> = listOf(
        Block(id = nextBlockId--, type = BlockType.TEXT, content = "", sortOrder = 0)
    )

    private fun defaultContentFor(type: BlockType): String = when (type) {
        BlockType.TEXT -> ""
        BlockType.IMAGE -> ""
        BlockType.LINK -> ""
        BlockType.LATEX -> ""
        BlockType.CODE -> ""
        BlockType.DIVIDER -> ""
        BlockType.TODO -> ""
        BlockType.BRANCH -> ""
    }

    private suspend fun scheduleTodoReminder(todoId: Long, noteId: Long, dueAt: Long) {
        syncReminder { todoReminderSync.schedule(todoId = todoId, noteId = noteId, dueAt = dueAt) }
    }

    private suspend fun reconcileTodoReminder(todo: TodoItem) {
        // 走同步点按状态配平，而不是在这里再判一遍"完成 / 有提醒 / 都没"——
        // 那套判断 AI 的 add_todos 也要用（见 TodoReminderSync）。
        syncReminder { todoReminderSync.reconcile(todo, fallbackNoteId = _uiState.value.noteId) }
    }

    /** 两个同步点都只回结果，错误统一浮到界面（笔记页没有别的错误通道）。 */
    private suspend fun syncReminder(block: suspend () -> RepositoryResult<Unit>) {
        when (val result = block()) {
            is RepositoryResult.Success -> Unit
            is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
        }
    }

    private fun ReviewPlan?.isDue(now: Long = System.currentTimeMillis()): Boolean =
        this?.nextReviewAt?.let { nextReviewAt -> enabled && nextReviewAt <= now } ?: false
}
