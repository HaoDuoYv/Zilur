package com.example.zhilu.ui.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zhilu.common.RepositoryResult
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderType
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.model.TodoItem
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.ReviewRepository
import com.example.zhilu.domain.repository.TagRepository
import com.example.zhilu.domain.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
    private val reviewRepository: ReviewRepository,
    private val todoRepository: TodoRepository,
    private val reminderRepository: ReminderRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _uiState = MutableStateFlow(NoteUiState())
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    private var saveJob: Job? = null
    private var todoObservationJob: Job? = null
    private var observedTodoNoteId: Long = 0L
    private var recordingReviewPlanId: Long? = null

    init {
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
                    _uiState.update {
                        it.copy(
                            noteId = note?.id ?: 0L,
                            title = note?.title.orEmpty(),
                            blocks = note?.blocks?.ifEmpty { defaultBlocks() } ?: defaultBlocks(),
                            selectedTags = note?.tags.orEmpty(),
                            todoItems = emptyList(),
                            reviewPlan = null,
                            isReviewDue = false,
                            isEditing = false,
                            isLoading = false,
                            error = null
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
        _uiState.update { it.copy(title = value) }
        scheduleSave()
    }

    fun onBlockContentChange(blockIndex: Int, value: String) {
        _uiState.update { state ->
            state.copy(
                blocks = state.blocks.mapIndexed { index, block ->
                    if (index == blockIndex) block.copy(content = value) else block
                }
            )
        }
        scheduleSave()
    }

    fun addBlock(type: BlockType) {
        val content = defaultContentFor(type)
        _uiState.update { state ->
            state.copy(
                blocks = state.blocks + Block(
                    type = type,
                    content = content,
                    sortOrder = state.blocks.size
                )
            )
        }
        if (content.isNotBlank() || type == BlockType.DIVIDER || type == BlockType.TODO) {
            scheduleSave()
        }
    }

    fun addImageBlock(uri: String) {
        _uiState.update { state ->
            state.copy(
                blocks = state.blocks + Block(
                    type = BlockType.IMAGE,
                    content = uri,
                    sortOrder = state.blocks.size
                )
            )
        }
        scheduleSave()
    }

    fun removeBlock(index: Int) {
        val block = _uiState.value.blocks.getOrNull(index) ?: return
        if (block.type == BlockType.TODO && _uiState.value.todoItems.isNotEmpty()) {
            _uiState.update { it.copy(error = "TODO block still has linked items.") }
            return
        }
        _uiState.update { state ->
            val remaining = state.blocks.filterIndexed { blockIndex, _ -> blockIndex != index }
            state.copy(blocks = remaining.ifEmpty { defaultBlocks() })
        }
        scheduleSave()
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
        viewModelScope.launch { saveInternal(exitEditMode = true) }
    }

    fun startEditing() {
        _uiState.update { it.copy(isEditing = true) }
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
        val currentNoteId = _uiState.value.noteId
        if (currentNoteId > 0L) return currentNoteId

        _uiState.update { it.copy(isSaving = true) }
        return when (val result = noteRepository.insertNote(_uiState.value.toNote())) {
            is RepositoryResult.Success -> {
                val newId = result.data
                _uiState.update {
                    it.copy(
                        noteId = newId,
                        isSaving = false,
                        lastSavedAt = System.currentTimeMillis()
                    )
                }
                observeTodos(newId)
                newId
            }
            is RepositoryResult.Error -> {
                _uiState.update { it.copy(isSaving = false, error = result.message) }
                0L
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
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(500)
            saveInternal(exitEditMode = false)
        }
    }

    private suspend fun saveInternal(exitEditMode: Boolean) {
        val state = _uiState.value
        if (state.title.isBlank() && state.blocks.all { it.content.isBlank() }) return
        _uiState.update { it.copy(isSaving = true) }
        val note = state.toNote()
        val result = if (state.noteId == 0L) {
            noteRepository.insertNote(note)
        } else {
            noteRepository.updateNote(note)
        }
        when (result) {
            is RepositoryResult.Success -> {
                val newId = if (state.noteId == 0L && result.data is Long) result.data else state.noteId
                _uiState.update {
                    it.copy(
                        noteId = newId,
                        isEditing = if (exitEditMode) false else it.isEditing,
                        isSaving = false,
                        lastSavedAt = System.currentTimeMillis()
                    )
                }
                if (state.noteId == 0L && newId > 0L) {
                    observeTodos(newId)
                }
            }
            is RepositoryResult.Error -> _uiState.update {
                it.copy(isSaving = false, error = result.message)
            }
        }
    }

    private fun defaultBlocks(): List<Block> = listOf(
        Block(type = BlockType.TEXT, content = "", sortOrder = 0)
    )

    private fun defaultContentFor(type: BlockType): String = when (type) {
        BlockType.TEXT -> ""
        BlockType.IMAGE -> ""
        BlockType.LINK -> "https://"
        BlockType.LATEX -> ""
        BlockType.CODE -> ""
        BlockType.DIVIDER -> ""
        BlockType.TODO -> ""
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
