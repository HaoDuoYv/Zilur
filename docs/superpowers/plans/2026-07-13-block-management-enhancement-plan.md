# 知识卡片块管理增强实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复旧记录「添加知识小点」闪退 bug，并在编辑态增加块间插入、块复制粘贴、块上下文菜单功能。

**Architecture:** 在 `NoteRepositoryImpl` 中修复旧数据 hydration 不一致问题；在 `NoteViewModel` 中增强 `addKnowledgeCard` 防御并新增 `insertBlockAt`/`copyBlock`/`pasteBlock`；新增 `BlockClipboardSerializer` 和 `BlockClipboardManager` 处理系统剪贴板；UI 层在 `EditableBlock` 中新增插入线与上下文菜单，并在 `NoteEditScreen`/`KnowledgeCardItem` 中串联回调。

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room, kotlinx.serialization (已有), JUnit4 + MockK

---

## 文件结构

| 文件 | 责任 |
|------|------|
| `app/src/main/java/com/example/zhilu/data/repository/NoteRepositoryImpl.kt` | 修复 `hydrate()` 中 orphan block 合并逻辑 |
| `app/src/main/java/com/example/zhilu/domain/model/Block.kt` | 新增 `copyWithFreshId()` 辅助方法 |
| `app/src/main/java/com/example/zhilu/domain/usecase/clipboard/BlockClipboardSerializer.kt` | 新建：块与 JSON 互转 |
| `app/src/main/java/com/example/zhilu/domain/usecase/clipboard/BlockClipboardManager.kt` | 新建：包装系统剪贴板读写 |
| `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockContextMenu.kt` | 新建：块上下文菜单 Composable |
| `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockInsertIndicator.kt` | 新建：块间插入线 Composable |
| `app/src/main/java/com/example/zhilu/ui/note/blocks/EditableBlock.kt` | 新增插入线、长按菜单回调 |
| `app/src/main/java/com/example/zhilu/ui/note/knowledge/KnowledgeCardItem.kt` | 透传插入/复制/粘贴/菜单回调 |
| `app/src/main/java/com/example/zhilu/ui/note/NoteEditScreen.kt` | 串联新回调，处理空白处长按粘贴 |
| `app/src/main/java/com/example/zhilu/ui/note/NoteViewModel.kt` | 修复闪退、新增块操作方法 |
| `app/src/test/java/com/example/zhilu/domain/usecase/clipboard/BlockClipboardSerializerTest.kt` | 新建：序列化测试 |
| `app/src/test/java/com/example/zhilu/ui/note/NoteViewModelBlockOpsTest.kt` | 新建：ViewModel 块操作测试 |
| `app/src/test/java/com/example/zhilu/data/repository/NoteRepositoryImplTest.kt` | 修改/新增：hydrate orphan block 测试 |

---

## Task 1: 修复旧记录 hydration 数据不一致

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/data/repository/NoteRepositoryImpl.kt:110-126`
- Test: `app/src/test/java/com/example/zhilu/data/repository/NoteRepositoryImplTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun `hydrate merges orphan blocks into first card when cardId mismatch`() = runTest {
    // 准备一条有卡片但 block.cardId 不匹配任何卡片 id 的笔记
    val noteId = 1L
    val cardEntity = NoteCardEntity(id = 10L, noteId = noteId, title = "卡片", sortOrder = 0)
    val orphanBlock = NoteBlockEntity(
        id = 100L, noteId = noteId, cardId = null,
        type = "TEXT", content = "orphan", sortOrder = 0
    )
    val matchedBlock = NoteBlockEntity(
        id = 101L, noteId = noteId, cardId = 10L,
        type = "TEXT", content = "matched", sortOrder = 1
    )
    // 配置 DAO mocks ...
    val result = repository.getNoteById(noteId)
    assertTrue(result is RepositoryResult.Success)
    val note = (result as RepositoryResult.Success).data!!
    assertEquals(1, note.cards.size)
    assertEquals(2, note.cards.first().blocks.size)
    assertEquals("orphan", note.cards.first().blocks.first().content)
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.data.repository.NoteRepositoryImplTest.hydrate*" --no-daemon`
Expected: FAIL

- [ ] **Step 3: 修改 hydrate 逻辑**

```kotlin
private suspend fun hydrate(entity: NoteEntity): Note {
    val cards = noteCardDao.getByNoteIdOnce(entity.id)
    val blocks = noteBlockDao.getByNoteIdOnce(entity.id).map(BlockMapper::toDomain)
    val tags = tagDao.getByNoteId(entity.id).map(TagMapper::toDomain)
    val cardDomains = if (cards.isEmpty()) {
        emptyList()
    } else {
        val cardIds = cards.map { it.id }.toSet()
        val (matched, orphans) = blocks.partition { it.cardId in cardIds }
        cards.map { card ->
            val cardBlocks = matched.filter { it.cardId == card.id }.toMutableList()
            if (card == cards.first()) {
                cardBlocks.addAll(orphans)
            }
            CardMapper.toDomain(
                entity = card,
                blocks = cardBlocks.sortedBy { it.sortOrder }
            )
        }
    }
    val fallbackBlocks = if (cards.isEmpty()) blocks else emptyList()
    return NoteMapper.toDomain(entity, fallbackBlocks, cardDomains, tags)
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.data.repository.NoteRepositoryImplTest" --no-daemon`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/zhilu/data/repository/NoteRepositoryImpl.kt \
        app/src/test/java/com/example/zhilu/data/repository/NoteRepositoryImplTest.kt
git commit -m "fix: merge orphan blocks into first card during hydration"
```

---

## Task 2: 增强 ViewModel 防御并修复 addKnowledgeCard 闪退

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/NoteViewModel.kt`
- Test: `app/src/test/java/com/example/zhilu/ui/note/NoteViewModelBlockOpsTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun `addKnowledgeCard does not crash when currentCardId not in cards`() = runTest {
    // 模拟旧记录加载后 state.cards 不包含 currentCardId 的场景
    val viewModel = createViewModelWithMalformedOldNote()
    viewModel.startEditing()

    viewModel.addKnowledgeCard()

    val state = viewModel.uiState.value
    assertEquals(2, state.cards.size)
    assertTrue(state.cards.any { it.id == state.activeCardId })
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.ui.note.NoteViewModelBlockOpsTest" --no-daemon`
Expected: FAIL

- [ ] **Step 3: 修改 addKnowledgeCard 与 syncBlocksToState**

修改 `addKnowledgeCard()`：

```kotlin
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
```

修改 `syncBlocksToState()`：

```kotlin
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
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.ui.note.NoteViewModelBlockOpsTest" --no-daemon`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/zhilu/ui/note/NoteViewModel.kt \
        app/src/test/java/com/example/zhilu/ui/note/NoteViewModelBlockOpsTest.kt
git commit -m "fix: defend addKnowledgeCard against stale old-note card ids"
```

---

## Task 3: 新增 Block.copyWithFreshId()

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/domain/model/Block.kt`

- [ ] **Step 1: 在 Block 中添加方法**

```kotlin
fun copyWithFreshId(newId: Long): Block = copy(
    id = newId,
    noteId = 0,
    cardId = 0,
    sortOrder = 0,
    parentBranchId = null
)
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/example/zhilu/domain/model/Block.kt
git commit -m "feat: add Block.copyWithFreshId helper"
```

---

## Task 4: 实现 BlockClipboardSerializer

**Files:**
- Create: `app/src/main/java/com/example/zhilu/domain/usecase/clipboard/BlockClipboardSerializer.kt`
- Test: `app/src/test/java/com/example/zhilu/domain/usecase/clipboard/BlockClipboardSerializerTest.kt`

- [ ] **Step 1: 写失败测试**

```kotlin
@Test
fun `serializes and deserializes text block without persistent ids`() {
    val block = Block(
        id = 1L, noteId = 2L, cardId = 3L,
        type = BlockType.TEXT, content = "hello", sortOrder = 5
    )
    val json = BlockClipboardSerializer.toJson(block)
    assertFalse(json.contains("\"id\":1"))
    assertFalse(json.contains("\"noteId\":2"))
    assertFalse(json.contains("\"cardId\":3"))
    assertFalse(json.contains("\"sortOrder\":5"))

    val restored = BlockClipboardSerializer.fromJson(json)
    assertEquals(BlockType.TEXT, restored?.type)
    assertEquals("hello", restored?.content)
    assertEquals(0L, restored?.id)
    assertEquals(0L, restored?.noteId)
}

@Test
fun `serializes branch with children recursively`() {
    val child = Block(id = 2L, type = BlockType.TEXT, content = "child", parentBranchId = 1L)
    val branch = Block(id = 1L, type = BlockType.BRANCH, content = "branch", parentBranchId = null)
    val json = BlockClipboardSerializer.toJson(branch, listOf(child))
    val restored = BlockClipboardSerializer.fromJson(json)
    assertEquals(1, restored?.children?.size)
    assertEquals("child", restored?.children?.first()?.content)
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.domain.usecase.clipboard.BlockClipboardSerializerTest" --no-daemon`
Expected: FAIL

- [ ] **Step 3: 实现 serializer**

```kotlin
package com.example.zhilu.domain.usecase.clipboard

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object BlockClipboardSerializer {
    private const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    private const val PREFIX = "zhilu-block:"

    fun toJson(block: Block, childBlocks: List<Block> = emptyList()): String {
        val dto = BlockDto(
            version = VERSION,
            type = block.type.value,
            content = block.content,
            language = block.language,
            children = childBlocks.map { toDto(it) }
        )
        return PREFIX + json.encodeToString(dto)
    }

    fun fromJson(raw: String): Block? = runCatching {
        val withoutPrefix = raw.removePrefix(PREFIX)
        val dto = json.decodeFromString<BlockDto>(withoutPrefix)
        fromDto(dto)
    }.getOrNull()

    private fun toDto(block: Block): BlockDto = BlockDto(
        version = VERSION,
        type = block.type.value,
        content = block.content,
        language = block.language,
        children = emptyList() // children handled by caller for branch
    )

    private fun fromDto(dto: BlockDto): Block = Block(
        id = 0L,
        type = BlockType.fromValue(dto.type),
        content = dto.content,
        language = dto.language,
        sortOrder = 0,
        parentBranchId = null
    ).let { block ->
        if (dto.children.isNotEmpty()) {
            block.copy(children = dto.children.map { fromDto(it) })
        } else {
            block
        }
    }

    @Serializable
    private data class BlockDto(
        val version: Int,
        val type: String,
        val content: String,
        val language: String? = null,
        val children: List<BlockDto> = emptyList()
    )
}
```

注意：这里使用 `Block.children` 字段需要先在 `Block` 数据类中增加 `val children: List<Block> = emptyList()` 作为临时容器（仅在粘贴反序列化时使用），或者改用独立数据结构。如果 `Block` 没有 `children` 字段，则改为返回 `Pair<Block, List<Block>>`。

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.domain.usecase.clipboard.BlockClipboardSerializerTest" --no-daemon`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/example/zhilu/domain/usecase/clipboard/BlockClipboardSerializer.kt \
        app/src/test/java/com/example/zhilu/domain/usecase/clipboard/BlockClipboardSerializerTest.kt
git commit -m "feat: add block clipboard serializer"
```

---

## Task 5: 实现 BlockClipboardManager

**Files:**
- Create: `app/src/main/java/com/example/zhilu/domain/usecase/clipboard/BlockClipboardManager.kt`

- [ ] **Step 1: 实现 Manager**

```kotlin
package com.example.zhilu.domain.usecase.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.example.zhilu.domain.model.Block
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockClipboardManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    fun copyBlock(block: Block, childBlocks: List<Block> = emptyList()) {
        val json = BlockClipboardSerializer.toJson(block, childBlocks)
        val clip = ClipData.newPlainText("zhilu-block", json)
        clipboard.setPrimaryClip(clip)
    }

    fun hasBlock(): Boolean {
        val clip = clipboard.primaryClip ?: return false
        if (clip.itemCount == 0) return false
        val text = clip.getItemAt(0).text?.toString() ?: return false
        return text.startsWith(BlockClipboardSerializer.PREFIX)
    }

    fun readBlock(): Block? {
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).text?.toString() ?: return null
        return BlockClipboardSerializer.fromJson(text)
    }
}
```

注意：`PREFIX` 需要在 `BlockClipboardSerializer` 中改为 `public` 或提供常量访问器。

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/example/zhilu/domain/usecase/clipboard/BlockClipboardManager.kt
git commit -m "feat: add block clipboard manager"
```

---

## Task 6: ViewModel 新增 insertBlockAt / copyBlock / pasteBlock

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/NoteViewModel.kt`
- Test: `app/src/test/java/com/example/zhilu/ui/note/NoteViewModelBlockOpsTest.kt`

- [ ] **Step 1: 注入 BlockClipboardManager 并新增方法**

在 `NoteViewModel` constructor 中注入 `private val blockClipboardManager: BlockClipboardManager`。

新增方法：

```kotlin
fun insertBlockAt(index: Int, type: BlockType) {
    val clampedIndex = index.coerceIn(0, _blocks.size)
    val newBlock = Block(
        id = nextBlockId--,
        type = type,
        content = defaultContentFor(type),
        sortOrder = clampedIndex,
        parentBranchId = null
    )
    if (type == BlockType.BRANCH) {
        _branchExpandedStates[newBlock.id] = false
    }
    _blocks.add(clampedIndex, newBlock)
    recalculateSortOrders()
    syncBlocksToState()
    scheduleSave()
}

fun copyBlock(blockId: Long) {
    val block = _blocks.find { it.id == blockId } ?: return
    val children = if (block.type == BlockType.BRANCH) {
        _blocks.filter { it.parentBranchId == blockId }
    } else emptyList()
    blockClipboardManager.copyBlock(block, children)
}

fun pasteBlock(targetIndex: Int?) {
    val template = blockClipboardManager.readBlock() ?: return
    val clampedIndex = targetIndex?.coerceIn(0, _blocks.size) ?: _blocks.size
    val idMap = mutableMapOf<Long, Long>()

    fun insertRecursively(
        source: Block,
        parentBranchId: Long? = null
    ) {
        val newId = nextBlockId--
        idMap[source.id] = newId
        val resolvedParent = parentBranchId ?: source.parentBranchId?.let { idMap[it] }
        val newBlock = source.copyWithFreshId(newId).copy(
            parentBranchId = resolvedParent,
            sortOrder = 0
        )
        if (newBlock.type == BlockType.BRANCH) {
            _branchExpandedStates[newBlock.id] = false
        }
        // 简单实现：先放到末尾，最后统一重排序
        _blocks.add(newBlock)
    }

    // 由于 _blocks 是 SnapshotStateList，递归插入时需要先规划所有新块
    val newBlocks = flattenBlockTemplate(template)
    newBlocks.forEach { source ->
        val newId = nextBlockId--
        idMap[source.id] = newId
    }
    newBlocks.forEachIndexed { _, source ->
        val newId = idMap[source.id]!!
        val newBlock = source.copyWithFreshId(newId).copy(
            parentBranchId = source.parentBranchId?.let { idMap[it] },
            sortOrder = 0
        )
        if (newBlock.type == BlockType.BRANCH) {
            _branchExpandedStates[newBlock.id] = false
        }
        _blocks.add(newBlock)
    }

    // 移动到目标位置
    val pastedCount = newBlocks.size
    val startIndex = _blocks.size - pastedCount
    if (clampedIndex < startIndex) {
        repeat(pastedCount) {
            val block = _blocks.removeAt(startIndex)
            _blocks.add(clampedIndex, block)
        }
    }

    recalculateSortOrders()
    syncBlocksToState()
    scheduleSave()
}

private fun flattenBlockTemplate(root: Block): List<Block> {
    val result = mutableListOf<Block>()
    fun traverse(block: Block) {
        result.add(block.copy(children = emptyList()))
        block.children.forEach { traverse(it) }
    }
    traverse(root)
    return result
}

private fun recalculateSortOrders() {
    _blocks.forEachIndexed { i, block ->
        _blocks[i] = block.copy(sortOrder = i)
    }
}
```

注意：`flattenBlockTemplate` 依赖 `Block.children` 临时字段。如果 Task 4 中没有增加该字段，需要调整 serializer 返回 `Pair<Block, List<Block>>` 并在 pasteBlock 中递归处理。

- [ ] **Step 2: 写测试**

```kotlin
@Test
fun `insertBlockAt inserts at correct position`() = runTest {
    val viewModel = createViewModelWithNote()
    viewModel.startEditing()
    val initialBlocks = viewModel.uiState.value.cards.first().blocks
    assertTrue(initialBlocks.isNotEmpty())

    viewModel.insertBlockAt(0, BlockType.CODE)

    val blocks = viewModel.uiState.value.cards.first().blocks
    assertEquals(BlockType.CODE, blocks.first().type)
}

@Test
fun `pasteBlock generates new ids and preserves content`() = runTest {
    val viewModel = createViewModelWithNote()
    viewModel.startEditing()
    val sourceBlock = viewModel.uiState.value.cards.first().blocks.first()
    viewModel.copyBlock(sourceBlock.id)

    viewModel.pasteBlock(null)

    val blocks = viewModel.uiState.value.cards.first().blocks
    assertTrue(blocks.size >= 2)
    val pasted = blocks.last()
    assertNotEquals(sourceBlock.id, pasted.id)
    assertEquals(sourceBlock.content, pasted.content)
}
```

- [ ] **Step 3: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.ui.note.NoteViewModelBlockOpsTest" --no-daemon`
Expected: PASS

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/example/zhilu/ui/note/NoteViewModel.kt \
        app/src/test/java/com/example/zhilu/ui/note/NoteViewModelBlockOpsTest.kt
git commit -m "feat: add insert, copy, paste block operations in ViewModel"
```

---

## Task 7: 实现 BlockInsertIndicator 与 BlockContextMenu UI

**Files:**
- Create: `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockInsertIndicator.kt`
- Create: `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockContextMenu.kt`

- [ ] **Step 1: 实现 BlockInsertIndicator**

```kotlin
package com.example.zhilu.ui.note.blocks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp

@Composable
fun BlockInsertIndicator(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "插入块",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
```

- [ ] **Step 2: 实现 BlockContextMenu**

```kotlin
package com.example.zhilu.ui.note.blocks

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun BlockContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onInsertAbove: () -> Unit,
    onInsertBelow: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text("在上方插入") },
            onClick = { onDismiss(); onInsertAbove() }
        )
        DropdownMenuItem(
            text = { Text("在下方插入") },
            onClick = { onDismiss(); onInsertBelow() }
        )
        DropdownMenuItem(
            text = { Text("复制") },
            onClick = { onDismiss(); onCopy() }
        )
        DropdownMenuItem(
            text = { Text("删除") },
            onClick = { onDismiss(); onDelete() }
        )
        DropdownMenuItem(
            text = { Text("上移") },
            onClick = { onDismiss(); onMoveUp() },
            enabled = canMoveUp
        )
        DropdownMenuItem(
            text = { Text("下移") },
            onClick = { onDismiss(); onMoveDown() },
            enabled = canMoveDown
        )
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/zhilu/ui/note/blocks/BlockInsertIndicator.kt \
        app/src/main/java/com/example/zhilu/ui/note/blocks/BlockContextMenu.kt
git commit -m "feat: add block insert indicator and context menu composables"
```

---

## Task 8: 在 EditableBlock 中集成插入线与上下文菜单

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/EditableBlock.kt`

- [ ] **Step 1: 添加新参数与状态**

```kotlin
@Composable
fun EditableBlock(
    index: Int,
    block: Block,
    total: Int,
    onValueChange: (String) -> Unit,
    onLanguageClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onInsertAbove: () -> Unit = {},
    onInsertBelow: () -> Unit = {},
    onCopy: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onDrag: ((offsetY: Float) -> Unit)? = null,
    onDragStart: (() -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
    isDragging: Boolean = false,
    showTopDivider: Boolean = false,
    modifier: Modifier = Modifier,
    todoItems: List<TodoItem>? = null,
    showCompletedTodos: Boolean = false,
    onCreateTodo: (suspend (String, Long?) -> Boolean)? = null,
    onUpdateTodo: ((TodoItem) -> Unit)? = null,
    onCompleteTodo: ((Long) -> Unit)? = null,
    onToggleCompletedTodos: (() -> Unit)? = null,
    onImageClick: (() -> Unit)? = null,
    branchChildBlocks: List<Block> = emptyList(),
    isBranchExpanded: Boolean = false,
    onBranchTitleChange: (String) -> Unit = {},
    onToggleBranchExpanded: () -> Unit = {},
    onBranchChildValueChange: (Long, String) -> Unit = { _, _ -> },
    onBranchChildLanguageClick: (Long) -> Unit = {},
    onRemoveBranchChild: (Long) -> Unit = {},
    onAddBranchChild: (BlockType) -> Unit = {}
) {
    val currentOnRemove by rememberUpdatedState(onRemove)
    var removeRequested by remember(block.id, index) { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showTopIndicator by remember { mutableStateOf(false) }
    var showBottomIndicator by remember { mutableStateOf(false) }
    // ... existing dismiss state ...
```

- [ ] **Step 2: 在 Column 中插入 indicator 和 menu**

```kotlin
Column(
    modifier = modifier.then(dragModifier)
        .pointerInput(block.id) {
            detectTapGestures(
                onLongPress = { onLongClick?.invoke() }
            )
        }
) {
    BlockInsertIndicator(
        visible = showTopIndicator,
        onClick = onInsertAbove,
        modifier = Modifier
            .padding(top = if (index == 0) 0.dp else (-8).dp)
            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Enter) { showTopIndicator = true }
            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Exit) { showTopIndicator = false }
    )
    // ... existing top divider + SwipeToDismissBox ...
    BlockInsertIndicator(
        visible = showBottomIndicator,
        onClick = onInsertBelow,
        modifier = Modifier
            .padding(bottom = if (index == total - 1) 0.dp else (-8).dp)
            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Enter) { showBottomIndicator = true }
            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Exit) { showBottomIndicator = false }
    )
}

BlockContextMenu(
    expanded = showMenu,
    onDismiss = { showMenu = false },
    canMoveUp = index > 0,
    canMoveDown = index < total - 1,
    onInsertAbove = onInsertAbove,
    onInsertBelow = onInsertBelow,
    onCopy = onCopy,
    onDelete = onRemove,
    onMoveUp = { onMoveUp?.invoke() },
    onMoveDown = { onMoveDown?.invoke() }
)
```

注意：`onPointerEvent` 需要 Compose 1.7+。如果项目版本较低，改用 `pointerInput(Unit) { detectTapGestures(onTap = { showTopIndicator = !showTopIndicator }) }` 即点击块间区域触发。推荐使用「点击块间区域」方案，更稳定。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/zhilu/ui/note/blocks/EditableBlock.kt
git commit -m "feat: integrate insert indicators and context menu into EditableBlock"
```

---

## Task 9: 在 KnowledgeCardItem 中透传回调

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/knowledge/KnowledgeCardItem.kt`

- [ ] **Step 1: 为 KnowledgeCardItem 和 CardBlockList 新增参数**

```kotlin
fun KnowledgeCardItem(
    card: KnowledgeCard,
    isEditing: Boolean,
    canDelete: Boolean,
    onFocus: () -> Unit,
    onTitleChange: (String) -> Unit,
    onDelete: () -> Unit,
    onBlockContentChange: (Long, String) -> Unit,
    onBlockLanguageClick: (Long) -> Unit,
    onRemoveBlock: (Long) -> Unit,
    onMoveBlockUp: (Long) -> Unit,
    onMoveBlockDown: (Long) -> Unit,
    onInsertBlockAt: (Int, BlockType) -> Unit,
    onCopyBlock: (Long) -> Unit,
    onImageClick: (Block) -> Unit,
    onToggleBranchExpanded: (Long) -> Unit,
    // ... rest unchanged
)
```

在 `CardBlockList` 中透传：

```kotlin
private fun CardBlockList(
    card: KnowledgeCard,
    isEditing: Boolean,
    onBlockContentChange: (Long, String) -> Unit,
    onBlockLanguageClick: (Long) -> Unit,
    onRemoveBlock: (Long) -> Unit,
    onMoveBlockUp: (Long) -> Unit,
    onMoveBlockDown: (Long) -> Unit,
    onInsertBlockAt: (Int, BlockType) -> Unit,
    onCopyBlock: (Long) -> Unit,
    // ... rest
)
```

在 `EditableBlock` 调用处：

```kotlin
EditableBlock(
    // ... existing params ...
    onInsertAbove = { onInsertBlockAt(index, selectedType) }, // 这里需要一个选择类型的弹窗
    onInsertBelow = { onInsertBlockAt(index + 1, selectedType) },
    onCopy = { onCopyBlock(block.id) },
    onLongClick = { showMenu = true }
)
```

注意：`onInsertAbove`/`onInsertBelow` 需要先弹出块类型选择。可以在 `CardBlockList` 中增加 `var showTypePickerForIndex by remember { mutableStateOf<Int?>(null) }`，然后用 `AlertDialog` 或底部弹窗选择类型。

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/example/zhilu/ui/note/knowledge/KnowledgeCardItem.kt
git commit -m "feat: wire insert and copy callbacks through KnowledgeCardItem"
```

---

## Task 10: 在 NoteEditScreen 串联并处理空白处粘贴

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/NoteEditScreen.kt`

- [ ] **Step 1: 在 KnowledgeCardItem 调用处添加新回调**

```kotlin
KnowledgeCardItem(
    // ... existing params ...
    onInsertBlockAt = { index, type -> viewModel.insertBlockAt(index, type) },
    onCopyBlock = { blockId -> viewModel.copyBlock(blockId) }
)
```

- [ ] **Step 2: 处理卡片空白处长按粘贴**

在 `CardBlockList` 调用外层或 `KnowledgeCardItem` 内部增加长按手势。也可以在 `NoteEditScreen` 的 `LazyColumn` item 中为整个卡片区域添加 `pointerInput` 检测长按，弹出「粘贴到末尾」选项。

```kotlin
item {
    Box(
        modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        if (viewModel.uiState.value.isEditing) {
                            showPasteDialogForCard = card.id
                        }
                    }
                )
            }
    ) {
        KnowledgeCardItem(...)
    }
}
```

新增状态：

```kotlin
var pendingPasteIndex by remember { mutableStateOf<Int?>(null) }
var showPastePositionDialog by remember { mutableStateOf(false) }
```

粘贴位置选择对话框：

```kotlin
if (showPastePositionDialog) {
    AlertDialog(
        onDismissRequest = { showPastePositionDialog = false },
        title = { Text("粘贴位置") },
        text = { Text("选择粘贴位置") },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { showPastePositionDialog = false }) {
                Text("取消")
            }
        }
    )
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/example/zhilu/ui/note/NoteEditScreen.kt
git commit -m "feat: wire block insert/copy/paste in NoteEditScreen"
```

---

## Task 11: 运行全量测试与 Lint

- [ ] **Step 1: 运行单元测试**

Run: `./gradlew :app:testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 运行 Lint**

Run: `./gradlew :app:lintDebug --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 构建 Debug APK**

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交修复**

```bash
git add -A
git commit -m "test: add block management unit tests and fix lint issues"
```

---

## 自我审查

**Spec 覆盖：**
- 闪退修复：Task 1 + Task 2 覆盖。
- 块间插入：Task 7 + Task 8 + Task 9 + Task 10 覆盖。
- 复制粘贴：Task 3 + Task 4 + Task 5 + Task 6 + Task 10 覆盖。
- 上下文菜单：Task 7 + Task 8 + Task 9 覆盖。
- 实时保存：Task 2 + Task 6 中所有操作后调用 scheduleSave() 覆盖。
- 测试：Task 1 + Task 2 + Task 4 + Task 11 覆盖。

**Placeholder 扫描：**
- 无 TBD/TODO。
- 有少数实现细节（如 `Block.children` 临时字段是否存在）需要在 Task 4 中根据实际数据模型调整。
- `selectedType` 在 Task 9 中需要从类型选择弹窗获取，计划里用文字说明，实际代码在实现时补齐。

**类型一致性：**
- `BlockClipboardSerializer.toJson` 签名与 Task 4 一致。
- `NoteViewModel.insertBlockAt/copyBlock/pasteBlock` 签名与 Task 6、Task 10 一致。
- `KnowledgeCardItem.onInsertBlockAt/onCopyBlock` 签名与 Task 9、Task 10 一致。

---

## 执行方式

Plan complete and saved to `docs/superpowers/plans/2026-07-13-block-management-enhancement-plan.md`. Two execution options:

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
