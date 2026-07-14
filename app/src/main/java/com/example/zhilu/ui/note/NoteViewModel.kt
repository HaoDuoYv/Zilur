package com.example.zhilu.ui.note

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.mutableStateListOf
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.ReviewRepository
import com.example.zhilu.domain.repository.TagRepository
import com.example.zhilu.domain.repository.TodoRepository
import com.example.zhilu.export.DtkExporter
import com.example.zhilu.export.HtmlExporter
import com.example.zhilu.export.MarkdownExporter
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
    private val mediaRepository: MediaRepository,
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
    private var nextCardId: Long = -1L
    private var currentCardId: Long = -1L
    private var _isDragging = false
    private val pendingRemovals = mutableMapOf<Long, PendingBlockRemoval>()
    private val removalConfirmJobs = mutableMapOf<Long, Job>()
    private var nextRemovalToken: Long = 1L
    private val _branchExpandedStates = mutableMapOf<Long, Boolean>()
    private var todoObservationJob: Job? = null
    private var observedTodoNoteId: Long = 0L
    private var recordingReviewPlanId: Long? = null

    init {
        currentCardId = nextCardId--
        _uiState.update { state ->
            state.copy(cards = knowledgeCardsFromBlocks(state.title, state.blocks))
        }
        load(NoteRouteArgs.noteId(savedStateHandle))
        observeTags()
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
                    initBranchExpandedStates(isEditing)
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

    fun onTitleChange(value: String) {
        _uiState.update { state ->
            state.copy(
                title = value,
                cards = state.cards.map { card ->
                    if (card.id == currentCardId) card.copy(title = value) else card
                }
            )
        }
        scheduleSave()
    }

    fun onCardTitleChange(cardId: Long, value: String) {
        _uiState.update { state ->
            state.copy(
                cards = state.cards.map { card ->
                    if (card.id == cardId) card.copy(title = value) else card
                }
            )
        }
        if (cardId == currentCardId) {
            _uiState.update { it.copy(title = value) }
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
            title = state.title,
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
            _branchExpandedStates[blockId] = false
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
        _isDragging = dragging
    }

    fun moveBlock(fromId: Long, toId: Long) {
        val fromBlock = _blocks.find { it.id == fromId } ?: return
        val toBlock = _blocks.find { it.id == toId } ?: return
        if (fromBlock.id == toBlock.id) return
        if (fromBlock.parentBranchId != null || toBlock.parentBranchId != null) return

        val fromIndex = _blocks.indexOfFirst { it.id == fromId }
        val toIndex = _blocks.indexOfFirst { it.id == toId }
        if (fromIndex < 0 || toIndex < 0) return

        if (fromBlock.type == BlockType.BRANCH) {
            val children = _blocks.filter { it.parentBranchId == fromId }
            val group = listOf(fromBlock) + children
            _blocks.removeAll(group)
            val newToIndex = _blocks.indexOfFirst { it.id == toId }
            val insertIndex = if (fromIndex < toIndex) newToIndex + 1 else newToIndex
            _blocks.addAll(insertIndex.coerceIn(0, _blocks.size), group)
        } else {
            _blocks.removeAt(fromIndex)
            val newToIndex = _blocks.indexOfFirst { it.id == toId }
            _blocks.add(newToIndex.coerceIn(0, _blocks.size), fromBlock)
        }
        syncBlocksToState()
        if (!_isDragging) scheduleSave()
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
        _branchExpandedStates[branchId] ?: false

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

    private fun initBranchExpandedStates(isEditing: Boolean) {
        _branchExpandedStates.clear()
        _blocks.forEach { block ->
            if (block.type == BlockType.BRANCH) {
                _branchExpandedStates[block.id] = !isEditing
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
        _uiState.update { state ->
            state.copy(
                isEditing = true,
                activeCardId = activeCardId,
                cards = state.cards.map { card ->
                    card.copy(isFocused = card.id == activeCardId)
                }
            )
        }
        focusCard(activeCardId)
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

    fun shareNote(
        format: ShareFormat,
        onReady: (File, String) -> Unit
    ) {
        viewModelScope.launch {
            val note = _uiState.value.toNote()
            val mediaResult = mediaRepository.getAllMedia()
            val media = (mediaResult as? RepositoryResult.Success)?.data.orEmpty()
            val noteMedia = media.filter { m ->
                note.blocks.any { it.type == BlockType.IMAGE && it.content.contains(m.id.toString()) }
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

    private suspend fun scheduleReviewReminder(plan: ReviewPlan) {
        val nextReviewAt = plan.nextReviewAt
        if (nextReviewAt == null) {
            when (val result = reminderRepository.cancel(ReminderType.REVIEW, plan.id)) {
                is RepositoryResult.Success -> Unit
                is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
            }
            return
        }
        val reminder = ReminderInstance(
            type = ReminderType.REVIEW,
            sourceId = plan.id,
            noteId = plan.noteId,
            dueAt = nextReviewAt,
            notificationId = "review-${plan.id}".hashCode()
        )
        when (val result = reminderRepository.upsertScheduled(reminder)) {
            is RepositoryResult.Success -> Unit
            is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
        }
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
        val tag = Tag(name = name, color = tagPalette[_uiState.value.availableTags.size % tagPalette.size])
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
                    val savedNote = result.data
                    val newId = savedNote.id
                    val cardIdMap = state.cards.mapIndexed { index, card ->
                        card.id to savedNote.cards.getOrNull(index)?.id
                    }.toMap()
                    val blockIdMap = buildMap {
                        var blockOffset = 0
                        state.cards.forEach { card ->
                            card.blocks.forEachIndexed { index, block ->
                                savedNote.blocks.getOrNull(blockOffset + index)?.let { savedBlock ->
                                    put(block.id, savedBlock.id)
                                }
                            }
                            blockOffset += card.blocks.size
                        }
                    }
                    if (version == saveVersion) {
                        _uiState.update {
                            it.copy(
                                noteId = newId,
                                isEditing = if (exitEditMode) false else it.isEditing,
                                isSaving = false,
                                saveStatus = SaveStatus.SAVED,
                                lastSavedAt = System.currentTimeMillis(),
                                cards = it.cards.map { card ->
                                    val savedId = cardIdMap[card.id]
                                    card.copy(
                                        id = savedId ?: card.id,
                                        blocks = card.blocks.map { block ->
                                            block.copy(
                                                id = blockIdMap[block.id] ?: block.id,
                                                noteId = newId,
                                                cardId = savedId ?: card.id,
                                                parentBranchId = block.parentBranchId?.let { pid ->
                                                    blockIdMap[pid] ?: pid
                                                }
                                            )
                                        }
                                    )
                                },
                                activeCardId = it.activeCardId?.let { id -> cardIdMap[id] ?: id }
                            )
                        }
                    } else if (state.noteId == 0L && newId > 0L) {
                        _uiState.update { it.copy(noteId = newId) }
                    }
                    if (state.noteId == 0L && newId > 0L) {
                        observeTodos(newId)
                    }
                    currentCardId = cardIdMap[currentCardId] ?: currentCardId
                    _blocks.replaceAll { block ->
                        block.copy(
                            id = blockIdMap[block.id] ?: block.id,
                            noteId = newId,
                            cardId = cardIdMap[block.cardId] ?: block.cardId,
                            parentBranchId = block.parentBranchId?.let { pid ->
                                blockIdMap[pid] ?: pid
                            }
                        )
                    }
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
        val reminder = ReminderInstance(
            type = ReminderType.TODO,
            sourceId = todoId,
            noteId = noteId,
            dueAt = dueAt,
            notificationId = "todo-$todoId".hashCode()
        )
        when (val result = reminderRepository.upsertScheduled(reminder)) {
            is RepositoryResult.Success -> Unit
            is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
        }
    }

    private suspend fun reconcileTodoReminder(todo: TodoItem) {
        when {
            todo.isCompleted -> {
                when (val result = reminderRepository.markDone(ReminderType.TODO, todo.id)) {
                    is RepositoryResult.Success -> Unit
                    is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
                }
            }
            todo.remindAt != null -> {
                scheduleTodoReminder(
                    todoId = todo.id,
                    noteId = todo.noteId ?: _uiState.value.noteId,
                    dueAt = todo.remindAt
                )
            }
            else -> {
                when (val result = reminderRepository.cancel(ReminderType.TODO, todo.id)) {
                    is RepositoryResult.Success -> Unit
                    is RepositoryResult.Error -> _uiState.update { it.copy(error = result.message) }
                }
            }
        }
    }

    private val tagPalette = listOf(
        0xFF6750A4.toInt(),
        0xFF0061A4.toInt(),
        0xFF006B2E.toInt(),
        0xFF946700.toInt(),
        0xFF8C1D40.toInt()
    )

    private fun ReviewPlan?.isDue(now: Long = System.currentTimeMillis()): Boolean =
        this?.nextReviewAt?.let { nextReviewAt -> enabled && nextReviewAt <= now } ?: false
}
