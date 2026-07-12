# 知录笔记编辑页 Bug 修复实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 4 个独立 bug：连续添加块崩溃、拍照图片块丢失、链接块点击无反应、LaTeX 渲染异常与卡顿。

**Architecture:** 将 `NoteViewModel` 中的块列表操作抽离到 `BlockListManager`，由它统一维护 `SnapshotStateList<Block>` 并生成持久化时的 `sortOrder`；图片导入统一走 `ImageFileManager` 复制到 `filesDir/images/`，相机拍照目标文件也改到同一目录；链接块拆分为 `LinkBlockEditor`（编辑）与 `LinkBlockViewer`（只读点击跳转）；LaTeX 增加 `sanitizeLatex` 预处理、`produceState` 异步渲染和错误状态 UI。

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Hilt, Room, Coil, JLatexMath, JUnit 4, kotlinx-coroutines-test

---

## 文件结构

| 新建文件 | 修改文件 |
|----------|----------|
| `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockListManager.kt` | `app/src/main/java/com/example/zhilu/ui/note/NoteViewModel.kt` |
| `app/src/main/java/com/example/zhilu/ui/note/blocks/LinkBlockViewer.kt` | `app/src/main/java/com/example/zhilu/ui/note/NoteUiState.kt` |
| `app/src/main/java/com/example/zhilu/data/local/file/ImageFileManager.kt` | `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockContent.kt` |
| `app/src/main/java/com/example/zhilu/di/FileManagerModule.kt` | `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockCard.kt` |
| `app/src/main/res/xml/file_paths.xml` | `app/src/main/java/com/example/zhilu/ui/note/blocks/EditableBlock.kt` |
| `app/src/test/java/com/example/zhilu/ui/note/blocks/BlockListManagerTest.kt` | `app/src/main/java/com/example/zhilu/ui/note/blocks/TextBlockEditor.kt` |
| `app/src/test/java/com/example/zhilu/ui/note/blocks/LinkBlockViewerTest.kt` | `app/src/main/java/com/example/zhilu/ui/note/blocks/CodeBlockEditor.kt` |
| `app/src/test/java/com/example/zhilu/data/local/file/ImageFileManagerTest.kt` | `app/src/main/java/com/example/zhilu/ui/note/blocks/LinkBlockEditor.kt` |
| `app/src/test/java/com/example/zhilu/ui/note/latex/LatexSanitizerTest.kt` | `app/src/main/java/com/example/zhilu/ui/note/blocks/LatexBlockEditor.kt` |
| | `app/src/main/java/com/example/zhilu/ui/note/blocks/ImageBlockView.kt` |
| | `app/src/main/java/com/example/zhilu/ui/note/latex/LatexRenderer.kt` |
| | `app/src/main/java/com/example/zhilu/ui/note/toolbar/BlockToolbar.kt` |
| | `app/src/main/java/com/example/zhilu/ui/note/NoteEditScreen.kt` |
| | `app/src/main/java/com/example/zhilu/ui/camera/CameraScreen.kt` |
| | `app/src/main/AndroidManifest.xml` |
| | `app/src/test/java/com/example/zhilu/ui/note/NoteViewModelAdvancedBlockTest.kt` |

---

## 前置说明：临时 ID 与持久化

当前 `NoteRepositoryImpl.replaceBlocks()` 在每次保存时都会**删除所有旧块并重新插入**，插入时把 `block.id` 重置为 `0`。因此 UI 中的临时负数 ID 不需要在保存后替换为数据库真实 ID——下次保存时本来就会全部重建。

本计划仍按 spec 使用负数作为新块临时 ID，仅用于 Compose `LazyColumn` 的 `key` 和当前会话内的块识别，不实现保存后的 ID 回写（避免在保存过程中 reload 导致竞态）。

---

## Task 1: 创建 BlockListManager 并写单元测试

**Files:**
- Create: `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockListManager.kt`
- Test: `app/src/test/java/com/example/zhilu/ui/note/blocks/BlockListManagerTest.kt`

- [ ] **Step 1: 写失败的单元测试**

```kotlin
package com.example.zhilu.ui.note.blocks

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockListManagerTest {

    @Test
    fun addBlockReturnsNegativeTemporaryId() {
        val manager = BlockListManager()
        val id = manager.addBlock(BlockType.TEXT)
        assertTrue("temporary id should be negative", id < 0)
        assertEquals(1, manager.blocks.size)
    }

    @Test
    fun toPersistableListGeneratesSortOrderByIndex() {
        val manager = BlockListManager()
        manager.addBlock(BlockType.TEXT, content = "A")
        manager.addBlock(BlockType.TEXT, content = "B")
        manager.addBlock(BlockType.IMAGE, content = "uri")

        val persistable = manager.toPersistableList()

        assertEquals(listOf(0, 1, 2), persistable.map { it.sortOrder })
        assertEquals(listOf("A", "B", "uri"), persistable.map { it.content })
    }

    @Test
    fun moveBlockReordersBlocks() {
        val manager = BlockListManager()
        manager.addBlock(BlockType.TEXT, content = "A")
        manager.addBlock(BlockType.TEXT, content = "B")

        manager.moveBlock(0, 1)

        assertEquals(listOf("B", "A"), manager.blocks.map { it.content })
    }

    @Test
    fun updateContentChangesTargetBlock() {
        val manager = BlockListManager()
        manager.addBlock(BlockType.TEXT, content = "A")

        manager.updateContent(0, "Updated")

        assertEquals("Updated", manager.blocks[0].content)
    }

    @Test
    fun removeBlockReturnsRemovedBlockAndRemovesIt() {
        val manager = BlockListManager()
        manager.addBlock(BlockType.TEXT, content = "A")
        manager.addBlock(BlockType.TEXT, content = "B")

        val removed = manager.removeBlock(0)

        assertEquals("A", removed?.content)
        assertEquals(listOf("B"), manager.blocks.map { it.content })
    }

    @Test
    fun addBlockAfterIndexInsertsAtCorrectPosition() {
        val manager = BlockListManager()
        manager.addBlock(BlockType.TEXT, content = "A")
        manager.addBlock(BlockType.TEXT, content = "B")

        manager.addBlock(BlockType.TEXT, content = "C", afterIndex = 0)

        assertEquals(listOf("A", "C", "B"), manager.blocks.map { it.content })
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.ui.note.blocks.BlockListManagerTest" --no-daemon`
Expected: 编译失败或测试失败（`BlockListManager` 不存在）。

- [ ] **Step 2: 实现 BlockListManager**

```kotlin
package com.example.zhilu.ui.note.blocks

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType

class BlockListManager(initialBlocks: List<Block> = emptyList()) {

    private val _blocks = mutableStateListOf<Block>().apply { addAll(initialBlocks) }
    val blocks: SnapshotStateList<Block> = _blocks

    private val _newlyAddedIds = mutableStateListOf<Long>()
    val newlyAddedIds: List<Long> get() = _newlyAddedIds

    private var nextTempId = -1L
    private var isProcessing = false

    fun addBlock(type: BlockType, content: String = "", afterIndex: Int = -1): Long {
        if (isProcessing) return 0L
        isProcessing = true
        val id = nextTempId--
        val insertIndex = if (afterIndex >= 0) afterIndex + 1 else _blocks.size
        _blocks.add(insertIndex, Block(id = id, type = type, content = content))
        _newlyAddedIds.add(id)
        isProcessing = false
        return id
    }

    fun moveBlock(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _blocks.indices) return
        val normalizedTo = toIndex.coerceIn(0, _blocks.size)
        if (fromIndex == normalizedTo) return
        if (fromIndex == _blocks.lastIndex && normalizedTo == _blocks.size) return
        val block = _blocks.removeAt(fromIndex)
        _blocks.add(normalizedTo, block)
    }

    fun removeBlock(index: Int): Block? {
        if (index !in _blocks.indices) return null
        return _blocks.removeAt(index)
    }

    fun updateContent(index: Int, content: String) {
        if (index !in _blocks.indices) return
        val block = _blocks[index]
        _blocks[index] = block.copy(content = content)
    }

    fun updateLanguage(index: Int, language: String) {
        if (index !in _blocks.indices) return
        val block = _blocks[index]
        _blocks[index] = block.copy(language = language)
    }

    fun clearNewFlag(id: Long) {
        _newlyAddedIds.remove(id)
    }

    fun replaceBlocks(blocks: List<Block>) {
        _blocks.clear()
        _blocks.addAll(blocks)
        _newlyAddedIds.clear()
    }

    fun toPersistableList(): List<Block> = _blocks.mapIndexed { index, block ->
        block.copy(sortOrder = index)
    }
}
```

- [ ] **Step 3: 运行测试**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.ui.note.blocks.BlockListManagerTest" --no-daemon`
Expected: BUILD SUCCESSFUL，6 个测试全部通过。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/zhilu/ui/note/blocks/BlockListManager.kt \
        app/src/test/java/com/example/zhilu/ui/note/blocks/BlockListManagerTest.kt
git commit -m "feat(note): add BlockListManager to centralize block list mutations"
```

---

## Task 2: 将 BlockListManager 接入 NoteViewModel

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/NoteUiState.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/NoteViewModel.kt`
- Modify: `app/src/test/java/com/example/zhilu/ui/note/NoteViewModelAdvancedBlockTest.kt`

- [ ] **Step 1: 修改 NoteUiState 的 blocks 类型**

```kotlin
package com.example.zhilu.ui.note

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.domain.model.TodoItem

data class NoteUiState(
    val noteId: Long = 0L,
    val title: String = "",
    val blocks: SnapshotStateList<Block> = mutableStateListOf(
        Block(type = BlockType.TEXT, content = "", sortOrder = 0)
    ),
    val selectedTags: List<Tag> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val todoItems: List<TodoItem> = emptyList(),
    val showCompletedTodos: Boolean = false,
    val reviewPlan: ReviewPlan? = null,
    val isReviewDue: Boolean = false,
    val isRecordingReview: Boolean = false,
    val isEditing: Boolean = true,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    val lastSavedAt: Long? = null,
    val isProcessingImage: Boolean = false,
    val newlyAddedBlockIds: List<Long> = emptyList(),
    val error: String? = null
) {
    fun toNote(): Note = Note(
        id = noteId,
        title = title,
        updatedAt = System.currentTimeMillis(),
        blocks = blocks.mapIndexed { index, block -> block.copy(sortOrder = index) },
        tags = selectedTags
    )
}
```

- [ ] **Step 2: 重构 NoteViewModel**

移除 `private val _blocks = mutableStateListOf<Block>()`，改为：

```kotlin
private val blockListManager = BlockListManager()
```

`init` 中不需要额外初始化；`defaultBlocks()` 仍用于新笔记。

替换以下方法：

```kotlin
fun load(noteId: Long) {
    if (noteId <= 0L) return
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        when (val result = noteRepository.getNoteById(noteId)) {
            is RepositoryResult.Success -> {
                val note = result.data
                blockListManager.replaceBlocks(note?.blocks?.ifEmpty { defaultBlocks() } ?: defaultBlocks())
                _uiState.update {
                    it.copy(
                        noteId = note?.id ?: 0L,
                        title = note?.title.orEmpty(),
                        blocks = blockListManager.blocks,
                        selectedTags = note?.tags.orEmpty(),
                        todoItems = emptyList(),
                        reviewPlan = null,
                        isReviewDue = false,
                        isEditing = note == null,
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
                _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }
}

fun onBlockContentChange(blockIndex: Int, value: String) {
    blockListManager.updateContent(blockIndex, value)
    syncBlocksToState()
    scheduleSave()
}

fun addBlock(type: BlockType) {
    val content = defaultContentFor(type)
    blockListManager.addBlock(type = type, content = content)
    syncBlocksToState()
    if (content.isNotBlank() || type == BlockType.DIVIDER || type == BlockType.TODO) {
        scheduleSave()
    }
}

fun setBlockLanguage(index: Int, language: String) {
    blockListManager.updateLanguage(index, language)
    syncBlocksToState()
    scheduleSave()
}

fun moveBlock(fromIndex: Int, toIndex: Int) {
    blockListManager.moveBlock(fromIndex, toIndex)
    syncBlocksToState()
    if (!_isDragging) scheduleSave()
}

fun removeBlock(index: Int) {
    val block = blockListManager.blocks.getOrNull(index) ?: return
    if (block.type == BlockType.TODO && _uiState.value.todoItems.isNotEmpty()) {
        _uiState.update { it.copy(error = "TODO block still has linked items.") }
        return
    }
    val removed = blockListManager.removeBlock(index)
    if (removed != null && blockListManager.blocks.isEmpty()) {
        blockListManager.replaceBlocks(defaultBlocks())
    }
    val token = nextRemovalToken++
    pendingRemovals[token] = PendingBlockRemoval(
        block = removed ?: block,
        index = index.coerceAtMost(blockListManager.blocks.size)
    )
    syncBlocksToState()
    _uiEvents.trySend(UiEvent.ShowUndoSnackbar(removed ?: block, index, token))
    removalConfirmJobs[token]?.cancel()
    removalConfirmJobs[token] = viewModelScope.launch {
        delay(5_000)
        confirmRemoveBlock(token)
    }
    scheduleSave()
}

fun undoRemoveBlock(token: Long) {
    val removal = pendingRemovals.remove(token) ?: return
    val isOnlyDefaultBlankBlock = blockListManager.blocks.size == 1 &&
        blockListManager.blocks.single().type == BlockType.TEXT &&
        blockListManager.blocks.single().content.isBlank()
    if (isOnlyDefaultBlankBlock) {
        blockListManager.replaceBlocks(emptyList())
    }
    val currentIndex = removal.index.coerceIn(0, blockListManager.blocks.size)
    blockListManager.addBlock(
        type = removal.block.type,
        content = removal.block.content,
        afterIndex = currentIndex - 1
    )
    // Restore language if needed
    val insertedIndex = (currentIndex - 1 + 1).coerceIn(0, blockListManager.blocks.lastIndex)
    if (removal.block.language.isNotBlank()) {
        blockListManager.updateLanguage(insertedIndex, removal.block.language)
    }
    removalConfirmJobs.remove(token)?.cancel()
    syncBlocksToState()
    scheduleSave()
}

fun onBlockFocused(id: Long) {
    blockListManager.clearNewFlag(id)
    syncBlocksToState()
}

private fun replaceBlocks(blocks: List<Block>) {
    blockListManager.replaceBlocks(blocks.ifEmpty { defaultBlocks() })
}

private fun syncBlocksToState() {
    _uiState.update {
        it.copy(
            blocks = blockListManager.blocks,
            newlyAddedBlockIds = blockListManager.newlyAddedIds
        )
    }
}

private fun blocksForState(): List<Block> =
    blockListManager.toPersistableList()
```

**注意**：`undoRemoveBlock` 原来的实现是 `add` 到指定 index，这里改用 `afterIndex` 参数；如果语义不完全一致，需手动调整回直接插入指定 index 的方式（可在 `BlockListManager` 中补充 `addBlockAt(index)` 方法）。

`addImageBlock` 和 `addImageFromGallery` 会在 Task 4 重写，这里先保留空壳或删除。

- [ ] **Step 3: 修复现有 ViewModel 测试的构造函数**

`NoteViewModelAdvancedBlockTest` 中所有 `NoteViewModel(...)` 调用需要补充 `mediaRepository` 和 `context` 参数。创建两个空实现：

```kotlin
private class EmptyMediaRepository : MediaRepository {
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
```

以及 mock Context：

```kotlin
private fun mockContext(): Context = mockk(relaxed = true)
```

但如果项目没有 MockK，使用 Mockito 或手动 stub。更简单的做法：引入 `androidx.test.core.app.ApplicationProvider`（需要 `testImplementation("androidx.test:core:1.6.1")`）。

如果无法引入依赖，可暂时用 `mockk` 或 Mockito。检查项目当前是否有 mock 库；若没有，本计划**增加** `testImplementation("io.mockk:mockk:1.13.12")` 到 `app/build.gradle.kts`。

- [ ] **Step 4: 运行 ViewModel 相关测试**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.ui.note.NoteViewModelAdvancedBlockTest" --no-daemon`
Expected: 全部通过。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/zhilu/ui/note/NoteUiState.kt \
        app/src/main/java/com/example/zhilu/ui/note/NoteViewModel.kt \
        app/src/test/java/com/example/zhilu/ui/note/NoteViewModelAdvancedBlockTest.kt \
        app/build.gradle.kts
git commit -m "refactor(note): integrate BlockListManager into NoteViewModel"
```

---

## Task 3: 工具栏防抖 + 新块焦点延迟请求

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/toolbar/BlockToolbar.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/EditableBlock.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockCard.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockContent.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/TextBlockEditor.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/CodeBlockEditor.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/LinkBlockEditor.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/LatexBlockEditor.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/NoteEditScreen.kt`

- [ ] **Step 1: 给 BlockToolbar 加 300ms 防抖**

```kotlin
@Composable
fun BlockToolbar(
    onAddText: () -> Unit,
    onAddCode: () -> Unit,
    onAddImage: () -> Unit,
    onPickImageFromGallery: () -> Unit,
    onAddLink: () -> Unit,
    onAddLatex: () -> Unit,
    onAddDivider: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isAdding by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun debounced(action: () -> Unit) {
        if (isAdding) return
        isAdding = true
        action()
        scope.launch { delay(300); isAdding = false }
    }

    Surface(...) {
        AnimatedContent(...) { isExpanded ->
            if (isExpanded) {
                ExpandedToolbar(
                    onAddText = { debounced(onAddText) },
                    onAddCode = { debounced(onAddCode) },
                    onAddImage = { debounced(onAddImage) },
                    onPickImageFromGallery = { debounced(onPickImageFromGallery) },
                    onAddLink = { debounced(onAddLink) },
                    onAddLatex = { debounced(onAddLatex) },
                    onAddDivider = { debounced(onAddDivider) },
                    onCollapse = { expanded = false }
                )
            } else {
                CollapsedToolbar(
                    onAddText = { debounced(onAddText) },
                    onAddCode = { debounced(onAddCode) },
                    onAddImage = { debounced(onAddImage) },
                    onPickImageFromGallery = { debounced(onPickImageFromGallery) },
                    onExpand = { expanded = true }
                )
            }
        }
    }
}
```

记得在 imports 中加入 `rememberCoroutineScope` 和 `delay`。

- [ ] **Step 2: 让 Editor 支持外部 FocusRequester**

以 `TextBlockEditor` 为例，其他编辑器类似：

```kotlin
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

@Composable
fun TextBlockEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val textStyle = ...

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        textStyle = textStyle,
        decorationBox = { ... }
    )
}
```

`CodeBlockEditor`、`LinkBlockEditor`、`LatexBlockEditor` 同样增加 `focusRequester: FocusRequester? = null` 参数，并在 `BasicTextField` modifier 链上调用 `.focusRequester(focusRequester)`。

- [ ] **Step 3: BlockContent 处理新块焦点**

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.android.awaitFrame

@Composable
fun BlockContent(
    block: Block,
    isEditing: Boolean,
    isNewlyAdded: Boolean = false,
    onFocused: (() -> Unit)? = null,
    onValueChange: (String) -> Unit = {},
    onLanguageClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    ...
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isNewlyAdded) {
        if (isNewlyAdded) {
            awaitFrame()
            focusRequester.requestFocus()
            onFocused?.invoke()
        }
    }

    when (block.type) {
        BlockType.TEXT -> {
            if (isEditing) {
                TextBlockEditor(
                    value = block.content,
                    onValueChange = onValueChange,
                    modifier = modifier,
                    focusRequester = focusRequester
                )
            } else { ... }
        }
        BlockType.CODE -> {
            if (isEditing) {
                CodeBlockEditor(
                    value = block.content,
                    language = block.language,
                    onValueChange = onValueChange,
                    onLanguageClick = onLanguageClick,
                    modifier = modifier,
                    focusRequester = focusRequester
                )
            } else { ... }
        }
        BlockType.LINK -> {
            if (isEditing) {
                LinkBlockEditor(
                    value = block.content,
                    onValueChange = onValueChange,
                    modifier = modifier,
                    focusRequester = focusRequester
                )
            } else { ... }
        }
        BlockType.LATEX -> {
            if (isEditing) {
                LatexBlockEditor(
                    value = block.content,
                    onValueChange = onValueChange,
                    modifier = modifier,
                    focusRequester = focusRequester
                )
            } else { ... }
        }
        ...
    }
}
```

- [ ] **Step 4: BlockCard 透传 isNewlyAdded / onFocused**

```kotlin
@Composable
fun BlockCard(
    block: Block,
    isEditing: Boolean,
    isNewlyAdded: Boolean = false,
    onFocused: (() -> Unit)? = null,
    onValueChange: (String) -> Unit = {},
    ...
) {
    Surface(...) {
        Column(...) {
            ...
            BlockContent(
                block = block,
                isEditing = isEditing,
                isNewlyAdded = isNewlyAdded,
                onFocused = onFocused,
                onValueChange = onValueChange,
                ...
            )
        }
    }
}
```

- [ ] **Step 5: EditableBlock 透传**

```kotlin
@Composable
fun EditableBlock(
    index: Int,
    block: Block,
    total: Int,
    isNewlyAdded: Boolean = false,
    onFocused: (() -> Unit)? = null,
    ...
) {
    ...
    BlockCard(
        block = block,
        isEditing = true,
        isNewlyAdded = isNewlyAdded,
        onFocused = onFocused,
        ...
    )
}
```

- [ ] **Step 6: NoteEditScreen 使用新标志**

在 `itemsIndexed(state.blocks, ...)` 内：

```kotlin
val isNewlyAdded = state.newlyAddedBlockIds.contains(block.id)
EditableBlock(
    index = index,
    block = block,
    total = state.blocks.size,
    isNewlyAdded = isNewlyAdded,
    onFocused = { viewModel.onBlockFocused(block.id) },
    ...
)
```

- [ ] **Step 7: 编译检查**

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/zhilu/ui/note/toolbar/BlockToolbar.kt \
        app/src/main/java/com/example/zhilu/ui/note/blocks/*.kt \
        app/src/main/java/com/example/zhilu/ui/note/NoteEditScreen.kt
git commit -m "fix(note): debounce toolbar and delay focus request for new blocks"
```

---

## Task 4: 图片导入统一

**Files:**
- Create: `app/src/main/java/com/example/zhilu/data/local/file/ImageFileManager.kt`
- Create: `app/src/main/java/com/example/zhilu/di/FileManagerModule.kt`
- Create: `app/src/main/res/xml/file_paths.xml`
- Create: `app/src/test/java/com/example/zhilu/data/local/file/ImageFileManagerTest.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/NoteViewModel.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/ImageBlockView.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/camera/CameraScreen.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/test/java/com/example/zhilu/ui/note/NoteViewModelAdvancedBlockTest.kt`

- [ ] **Step 1: 创建 ImageFileManager**

```kotlin
package com.example.zhilu.data.local.file

import java.io.File
import java.io.InputStream
import java.util.UUID

class ImageFileManager(private val imagesDir: File) {

    fun import(input: InputStream, extension: String): Result<String> {
        return runCatching {
            imagesDir.mkdirs()
            val sanitizedExtension = extension.lowercase().trimStart('.')
            val fileName = "${System.currentTimeMillis()}_${UUID.randomUUID()}.${sanitizedExtension}"
            val destFile = File(imagesDir, fileName)
            input.use { src ->
                destFile.outputStream().use { out ->
                    src.copyTo(out)
                }
            }
            destFile.toURI().toString()
        }
    }

    fun createCameraTargetFile(): File {
        imagesDir.mkdirs()
        val fileName = "camera_${System.currentTimeMillis()}.jpg"
        return File(imagesDir, fileName).apply { createNewFile() }
    }
}
```

- [ ] **Step 2: Hilt 提供 ImageFileManager**

```kotlin
package com.example.zhilu.di

import android.content.Context
import com.example.zhilu.data.local.file.ImageFileManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File

@Module
@InstallIn(SingletonComponent::class)
object FileManagerModule {

    @Provides
    fun provideImageFileManager(@ApplicationContext context: Context): ImageFileManager {
        return ImageFileManager(File(context.filesDir, "images"))
    }
}
```

- [ ] **Step 3: 写 ImageFileManagerTest**

```kotlin
package com.example.zhilu.data.local.file

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class ImageFileManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun importCopiesInputStreamToImagesDir() {
        val imagesDir = tempFolder.newFolder("images")
        val manager = ImageFileManager(imagesDir)
        val bytes = "fake-image".toByteArray()

        val result = manager.import(ByteArrayInputStream(bytes), "jpg")

        assertTrue(result.isSuccess)
        val uri = result.getOrThrow()
        assertTrue(uri.startsWith("file:"))

        val savedFile = File(File(uri).path)
        assertEquals("fake-image", savedFile.readText())
        assertTrue(savedFile.parentFile?.absolutePath?.contains("images") == true)
    }

    @Test
    fun importStripsLeadingDotFromExtension() {
        val imagesDir = tempFolder.newFolder("images")
        val manager = ImageFileManager(imagesDir)

        val result = manager.import(ByteArrayInputStream(byteArrayOf()), ".png")

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().endsWith(".png"))
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.data.local.file.ImageFileManagerTest" --no-daemon`
Expected: 通过。

- [ ] **Step 4: NoteViewModel 注入 ImageFileManager 并重构图片导入**

构造函数增加：

```kotlin
private val imageFileManager: ImageFileManager,
```

替换 `addImageFromGallery` 和 `addImageBlock`：

```kotlin
fun addImageBlock(content: String) {
    blockListManager.addBlock(type = BlockType.IMAGE, content = content)
    syncBlocksToState()
    scheduleSave()
}

fun importImageFromGallery(uri: Uri) {
    _uiState.update { it.copy(isProcessingImage = true, error = null) }
    viewModelScope.launch {
        try {
            val extension = resolveImageExtension(uri)
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("无法读取所选图片")
            when (val importResult = imageFileManager.import(input, extension)) {
                is Result.Success -> {
                    val internalUri = importResult.getOrThrow()
                    insertImageMediaAndAddBlock(internalUri)
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(isProcessingImage = false, error = "图片导入失败") }
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isProcessingImage = false, error = e.message ?: "保存图片失败") }
        }
    }
}

private suspend fun insertImageMediaAndAddBlock(internalUri: String) {
    val media = Media(
        uri = internalUri,
        size = 0L,
        createdAt = System.currentTimeMillis()
    )
    when (val result = mediaRepository.insertMedia(media)) {
        is RepositoryResult.Success -> {
            addImageBlock(ImageBlockContent.fromMedia(result.data, internalUri))
            _uiState.update { it.copy(isProcessingImage = false, error = null) }
        }
        is RepositoryResult.Error -> {
            _uiState.update { it.copy(isProcessingImage = false, error = result.message) }
        }
    }
}
```

删除旧的 `addImageFromGallery` 实现。`resolveImageExtension` 保留。

- [ ] **Step 5: ImageBlockView 处理 loading 状态**

当 `value` 为空或解析后 uri 为空时，显示 `CircularProgressIndicator` 而不是 "尚未选择图片"：

```kotlin
@Composable
fun ImageBlockView(...) {
    val imageUri = ImageBlockContent.displayUri(value)

    Box(...) {
        if (imageUri.isBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            SubcomposeAsyncImage(...)
        }
    }
}
```

- [ ] **Step 6: 相机拍照目录统一**

修改 `CameraScreen.kt` 中的 `createImageFile`：

```kotlin
private fun createImageFile(root: File): File {
    val directory = File(root, "images").also { it.mkdirs() }
    val timestamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(System.currentTimeMillis())
    return File(directory, "IMG-$timestamp.jpg")
}
```

- [ ] **Step 7: AndroidManifest 添加 FileProvider**

```xml
<application ...>
    ...
    <provider
        android:name="androidx.core.content.FileProvider"
        android:authorities="${applicationId}.fileprovider"
        android:exported="false"
        android:grantUriPermissions="true">
        <meta-data
            android:name="android.support.FILE_PROVIDER_PATHS"
            android:resource="@xml/file_paths" />
    </provider>
</application>
```

- [ ] **Step 8: 创建 file_paths.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <files-path
        name="images"
        path="images/" />
</paths>
```

- [ ] **Step 9: 更新 ViewModel 测试的构造函数**

所有 `NoteViewModel` 构造补充 `imageFileManager` 参数：`EmptyImageFileManager()` 或真实 `ImageFileManager(tempFolder)`。

- [ ] **Step 10: 编译 + 测试**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.data.local.file.ImageFileManagerTest" --tests "com.example.zhilu.ui.note.NoteViewModelAdvancedBlockTest" --no-daemon
```
Expected: 全部通过。

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/com/example/zhilu/data/local/file/ImageFileManager.kt \
        app/src/main/java/com/example/zhilu/di/FileManagerModule.kt \
        app/src/main/res/xml/file_paths.xml \
        app/src/test/java/com/example/zhilu/data/local/file/ImageFileManagerTest.kt \
        app/src/main/java/com/example/zhilu/ui/note/NoteViewModel.kt \
        app/src/main/java/com/example/zhilu/ui/note/blocks/ImageBlockView.kt \
        app/src/main/java/com/example/zhilu/ui/camera/CameraScreen.kt \
        app/src/main/AndroidManifest.xml \
        app/src/test/java/com/example/zhilu/ui/note/NoteViewModelAdvancedBlockTest.kt
git commit -m "fix(note): unify image import path and add loading state"
```

---

## Task 5: 链接块拆分

**Files:**
- Create: `app/src/main/java/com/example/zhilu/ui/note/blocks/LinkBlockViewer.kt`
- Create: `app/src/test/java/com/example/zhilu/ui/note/blocks/LinkBlockViewerTest.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockContent.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/LinkBlockEditor.kt`

- [ ] **Step 1: 创建 LinkBlockViewer**

```kotlin
package com.example.zhilu.ui.note.blocks

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast

@Composable
fun LinkBlockViewer(
    value: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val (title, url) = remember(value) { parseLinkContent(value) }
    val clickable = url.isNotBlank() && isSafeUrl(url)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = clickable) {
                openLink(context, url)
            }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Link,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (clickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title.ifBlank { url.ifBlank { "链接" } },
                style = MaterialTheme.typography.titleMedium,
                color = if (clickable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (url.isNotBlank()) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (url.isBlank() && title.isNotBlank()) {
            Text(
                text = "链接格式错误，点击编辑修复",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

fun parseLinkContent(content: String): Pair<String, String> {
    val trimmed = content.trim()
    val separatorIndex = trimmed.indexOf('|')
    return if (separatorIndex > 0) {
        val title = trimmed.substring(0, separatorIndex).trim()
        val url = trimmed.substring(separatorIndex + 1).trim()
        title to url
    } else if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)) {
        val domain = runCatching { Uri.parse(trimmed).host }.getOrNull() ?: trimmed
        domain to trimmed
    } else {
        trimmed to ""
    }
}

fun isSafeUrl(url: String): Boolean {
    return try {
        val uri = Uri.parse(url)
        uri.scheme?.lowercase() in listOf("http", "https")
    } catch (_: Exception) {
        false
    }
}

fun openLink(context: Context, url: String) {
    if (!isSafeUrl(url)) {
        Toast.makeText(context, "链接格式不正确", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "未找到可打开链接的应用", Toast.LENGTH_SHORT).show()
        }
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}
```

- [ ] **Step 2: BlockContent 只读态使用 LinkBlockViewer**

```kotlin
BlockType.LINK -> {
    if (isEditing) {
        LinkBlockEditor(...)
    } else {
        LinkBlockViewer(value = block.content, modifier = modifier)
    }
}
```

同时删除 `BlockContent` 内旧的 `ReadOnlyLinkBlockContent` 私有函数。

- [ ] **Step 3: 修改 LinkBlockEditor placeholder**

```kotlin
LinkTextField(
    value = title,
    placeholder = "标题",
    ...
)
LinkTextField(
    value = url,
    placeholder = "https://...",
    ...
)
```

并复用 `LinkBlockViewer.parseLinkContent` / `formatLinkContent`。如果 `LinkBlockEditor` 已有自己的解析，保留即可，但要确保两者语义一致。

- [ ] **Step 4: 写 LinkBlockViewer 单元测试**

```kotlin
package com.example.zhilu.ui.note.blocks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkBlockViewerTest {

    @Test
    fun parseLinkContentSplitsTitleAndUrl() {
        val (title, url) = parseLinkContent("GitHub|https://github.com")
        assertEquals("GitHub", title)
        assertEquals("https://github.com", url)
    }

    @Test
    fun parseLinkContentUsesDomainForBareUrl() {
        val (title, url) = parseLinkContent("https://github.com")
        assertEquals("github.com", title)
        assertEquals("https://github.com", url)
    }

    @Test
    fun parseLinkContentTreatsInvalidAsTitleOnly() {
        val (title, url) = parseLinkContent("just text")
        assertEquals("just text", title)
        assertEquals("", url)
    }

    @Test
    fun isSafeUrlAcceptsHttpAndHttps() {
        assertTrue(isSafeUrl("https://example.com"))
        assertTrue(isSafeUrl("http://example.com"))
        assertFalse(isSafeUrl("javascript:alert(1)"))
        assertFalse(isSafeUrl("intent://evil"))
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.ui.note.blocks.LinkBlockViewerTest" --no-daemon`
Expected: 通过。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/zhilu/ui/note/blocks/LinkBlockViewer.kt \
        app/src/main/java/com/example/zhilu/ui/note/blocks/BlockContent.kt \
        app/src/main/java/com/example/zhilu/ui/note/blocks/LinkBlockEditor.kt \
        app/src/test/java/com/example/zhilu/ui/note/blocks/LinkBlockViewerTest.kt
git commit -m "feat(note): split link block into editor and viewer with safe click handling"
```

---

## Task 6: LaTeX 预处理与渲染优化

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/latex/LatexRenderer.kt`
- Create: `app/src/test/java/com/example/zhilu/ui/note/latex/LatexSanitizerTest.kt`

- [ ] **Step 1: 实现 sanitizeLatex**

在 `LatexRenderer.kt` 中追加：

```kotlin
fun sanitizeLatex(input: String): String {
    var result = input.trim()

    result = convertAlignToArray(result)

    result = result.replace("\\[", "").replace("\\]", "")

    if (result.startsWith("$") ||
        result.startsWith("\\begin{") ||
        result.startsWith("\\(")) {
        return result
    }

    return "$$${result}$$"
}

private fun convertAlignToArray(input: String): String {
    if (!input.contains("\\begin{align")) return input
    return input
        .replace("\\begin{align*}", "\\begin{array}{rl}")
        .replace("\\end{align*}", "\\end{array}")
        .replace("\\begin{align}", "\\begin{array}{rl}")
        .replace("\\end{align}", "\\end{array}")
        .replace(Regex("""\\\\\[.*?]"""), "\\\\")
}
```

- [ ] **Step 2: rememberLatexImage 改为 produceState + Dispatchers.Default**

```kotlin
import androidx.compose.runtime.produceState

@Composable
fun rememberLatexImage(
    latex: String,
    textSizeSp: Float = 18f,
    color: Color = MaterialTheme.colorScheme.onSurface
): LatexRenderState {
    val density = LocalDensity.current
    val cache = remember { LatexRenderCache.getInstance() }

    val textSizePx = with(density) { textSizeSp.sp.toPx() }
    val colorInt = color.toArgb()
    val sanitized = remember(latex) { sanitizeLatex(latex) }
    val cacheKey = "$sanitized|$textSizePx|$colorInt"

    return produceState(
        initialValue = LatexRenderState.Loading,
        key1 = cacheKey
    ) {
        val cached = cache.get(cacheKey)
        if (cached != null) {
            value = LatexRenderState.Success(cached)
            return@produceState
        }

        value = LatexRenderState.Loading
        val result = withContext(Dispatchers.Default) {
            renderLatex(sanitized, textSizePx, colorInt)
        }
        value = result.fold(
            onSuccess = { image ->
                cache.put(cacheKey, image)
                LatexRenderState.Success(image)
            },
            onFailure = { error ->
                LatexRenderState.Error(error.message ?: "渲染失败", latex)
            }
        )
    }.value
}
```

- [ ] **Step 3: 增大缓存容量**

```kotlin
private const val MAX_CACHE_ENTRIES = 100
```

- [ ] **Step 4: 优化 Error UI**

```kotlin
is LatexRenderState.Error -> {
    Text(
        text = "⚠ 公式渲染失败",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center
    )
    Text(
        text = state.source,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}
```

- [ ] **Step 5: 写 LatexSanitizerTest**

```kotlin
package com.example.zhilu.ui.note.latex

import org.junit.Assert.assertEquals
import org.junit.Test

class LatexSanitizerTest {

    @Test
    fun bareFormulaWrappedInDisplayMath() {
        assertEquals("$$E=mc^2$$", sanitizeLatex("E=mc^2"))
    }

    @Test
    fun inlineFormulaNotDoubleWrapped() {
        assertEquals("$E=mc^2$", sanitizeLatex("$E=mc^2$"))
    }

    @Test
    fun alignStarConvertedToArray() {
        val input = "\\begin{align*}x &= 1 \\\\\\ y &= 2\\end{align*}"
        val output = sanitizeLatex(input)
        assertTrue(output.contains("\\begin{array}{rl}"))
        assertTrue(output.contains("\\end{array}"))
    }

    @Test
    fun displayMathDelimitersRemoved() {
        assertEquals("$$E=mc^2$$", sanitizeLatex("\\[E=mc^2\\]"))
    }

    @Test
    fun beginEnvironmentNotWrapped() {
        val input = "\\begin{array}{rl}1 & 2\\\\3 & 4\\end{array}"
        assertEquals(input, sanitizeLatex(input))
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.ui.note.latex.LatexSanitizerTest" --no-daemon`
Expected: 通过。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/zhilu/ui/note/latex/LatexRenderer.kt \
        app/src/test/java/com/example/zhilu/ui/note/latex/LatexSanitizerTest.kt
git commit -m "fix(latex): sanitize input, render off main thread, and enlarge cache"
```

---

## Task 7: 构建与 Lint 验证

- [ ] **Step 1: 运行完整单元测试**

```bash
./gradlew :app:testDebugUnitTest --no-daemon
```
Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: 编译 + 打包 + Lint**

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebug :app:lintDebug --no-daemon
```
Expected: BUILD SUCCESSFUL，无新增 lint error。

- [ ] **Step 3: Commit（如只有构建产物变更则不提交 build 目录）**

```bash
git status
```
确认没有误提交 build 目录。然后：

```bash
git commit -m "test(note): verify bug fix build and lint"
```

---

## 手动验证清单

1. 快速连点工具栏「文字」按钮，App 不崩溃；
2. 拍照后返回笔记页，出现图片块；
3. 从相册选择图片后，出现图片块；
4. 链接块只读态点击标题，用系统浏览器打开；
5. 输入 `align*` 环境不崩溃，显示 Error 提示；
6. 长列表滑动无明显卡顿。

---

## 计划自审

1. **Spec 覆盖**：4 个 bug 都对应到任务：Task 1/3（崩溃）、Task 4（图片丢失）、Task 5（链接点击）、Task 6（LaTeX）。
2. **占位符扫描**：无 TBD/TODO。
3. **类型一致性**：`BlockListManager.blocks` 和 `NoteUiState.blocks` 均为 `SnapshotStateList<Block>`；`newlyAddedBlockIds` 在 `BlockListManager`、`NoteUiState`、UI 中一致使用 `List<Long>`。
4. **风险点**：
   - `undoRemoveBlock` 用 `afterIndex` 恢复位置，需确保与原 `removeBlock` 语义一致；
   - 现有 `NoteViewModelAdvancedBlockTest` 构造函数需要补 `mediaRepository`、`context`、`imageFileManager`，可能触发额外依赖引入；
   - 临时负数 ID 不在保存后回写，因为 `NoteRepositoryImpl` 会重建所有块。

---

## 执行方式选择

Plan complete and saved to `docs/superpowers/plans/2026-07-11-bug-fix-implementation-plan.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
