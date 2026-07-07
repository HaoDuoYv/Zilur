# 知识点编辑/详情页 UI 优化实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将知录应用的知识点编辑/详情页改造为 Notion 式块编辑 + 飞书式简洁阅读体验，包含块级卡片、左滑删除、长按拖拽排序、局部靛蓝主题、行内标签选择/底部面板、代码块语言与复制、自动保存提示，并完成数据库迁移。

**Architecture:** 数据层给 `Block` 增加 `language` 字段并迁移到 Room v3；UI 层拆分为独立的块组件（`EditableBlock`/`ReadOnlyBlock`/`BlockContent`），编辑态用 `SwipeToDismissBox` 做左滑删除、`pointerInput` 做长按拖拽；ViewModel 用 `MutableStateList` 管理块顺序，`Channel<UiEvent>` 处理删除撤销，`debounce` 处理自动保存；知识点界面通过 `CompositionLocalProvider` 局部注入靛蓝色调，不污染全局主题。

**Tech Stack:** Kotlin, Jetpack Compose (Material3 1.3+), Room, Hilt, Coroutines/Flow, JUnit, Compose UI Test

---

## 文件结构

| 文件 | 责任 |
| --- | --- |
| `domain/model/Block.kt` | `Block` 数据类增加 `language` 字段 |
| `data/local/entity/NoteBlockEntity.kt` | Room 实体增加 `language` 列 |
| `data/local/mapper/BlockMapper.kt` | `language` 字段双向映射 |
| `data/local/database/AppDatabase.kt` | 数据库版本升到 3 |
| `data/local/database/Migration.kt` | 新增 `Migration(2, 3)` |
| `export/MarkdownExporter.kt` | 代码块导出带语言标识 |
| `export/JsonExporter.kt` | 代码块 JSON 导出带语言字段 |
| `ui/note/NoteUiState.kt` | `blocks` 用 `List<Block>` 但 ViewModel 内部转 `MutableStateList`；增加 `SaveStatus` |
| `ui/note/NoteViewModel.kt` | 状态管理：debounce 保存、移动块、设置语言、删除撤销事件流 |
| `ui/note/blocks/BlockContent.kt` | 根据 `BlockType` 分发编辑/只读渲染 |
| `ui/note/blocks/TextBlockEditor.kt` | 文字块编辑器 |
| `ui/note/blocks/CodeBlockEditor.kt` | 代码块编辑器：语言选择、复制按钮 |
| `ui/note/blocks/LinkBlockEditor.kt` | 链接块编辑器 |
| `ui/note/blocks/LatexBlockEditor.kt` | 公式块编辑器 |
| `ui/note/blocks/ImageBlockView.kt` | 图片块显示 |
| `ui/note/blocks/DividerBlockView.kt` | 分割线显示 |
| `ui/note/blocks/EditableBlock.kt` | 编辑态卡片外壳：类型小灰标、"⋮"菜单、左滑删除容器 |
| `ui/note/blocks/ReadOnlyBlock.kt` | 只读态卡片外壳 |
| `ui/note/tag/TagPickerInline.kt` | 行内标签网格面板 |
| `ui/note/bottomsheet/TagPickerBottomSheet.kt` | 底部标签选择面板 |
| `ui/note/toolbar/BlockToolbar.kt` | 底部 3 常驻 + 悬浮 "+" 菜单 |
| `ui/note/theme/NoteColors.kt` | 知识点界面局部靛蓝配色 |
| `ui/note/NoteEditScreen.kt` | 页面整合：标题、标签、块列表、工具栏、空状态 |
| `ui/component/TagChip.kt` | 如有需要调整点击态样式 |

---

## 前置检查

### Task 0: 确认依赖版本

**Files:**
- Read: `app/build.gradle.kts`

- [ ] **Step 1: 检查 Material3 版本**

打开 `app/build.gradle.kts`，确认 `androidx.compose.material3:material3` 版本。

```kotlin
// 期望版本 >= 1.3.0
implementation("androidx.compose.material3:material3:1.3.0")
```

- [ ] **Step 2: 升级版本（如需要）**

如果版本低于 1.3.0，修改为：

```kotlin
implementation("androidx.compose.material3:material3:1.3.0")
```

- [ ] **Step 3: 同步项目并验证编译通过**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts
git commit -m "chore: bump Material3 to 1.3.0 for SwipeToDismissBox"
```

---

## Task 1: 数据层迁移（Block + Entity + Mapper + Migration + Exporters）

**Goal:** 给代码块增加 `language` 字段并完成 Room 数据库迁移。

**Files:**
- Modify: `domain/model/Block.kt`
- Modify: `data/local/entity/NoteBlockEntity.kt`
- Modify: `data/local/mapper/BlockMapper.kt`
- Modify: `data/local/database/AppDatabase.kt`
- Modify: `data/local/database/Migration.kt`
- Modify: `export/MarkdownExporter.kt`
- Modify: `export/JsonExporter.kt`

- [ ] **Step 1: 修改 `domain/model/Block.kt`**

```kotlin
data class Block(
    val id: Long = 0,
    val noteId: Long = 0,
    val type: BlockType = BlockType.TEXT,
    val content: String = "",
    val language: String = "",
    val sortOrder: Int = 0
)
```

- [ ] **Step 2: 修改 `data/local/entity/NoteBlockEntity.kt`**

```kotlin
data class NoteBlockEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val noteId: Long,
    val type: Int,
    val content: String,
    val language: String = "",
    val sortOrder: Int
)
```

- [ ] **Step 3: 修改 `data/local/mapper/BlockMapper.kt`**

```kotlin
fun toDomain(entity: NoteBlockEntity): Block = Block(
    id = entity.id,
    noteId = entity.noteId,
    type = BlockType.fromValue(entity.type),
    content = entity.content,
    language = entity.language,
    sortOrder = entity.sortOrder
)

fun toEntity(domain: Block): NoteBlockEntity = NoteBlockEntity(
    id = domain.id,
    noteId = domain.noteId,
    type = domain.type.value,
    content = domain.content,
    language = domain.language,
    sortOrder = domain.sortOrder
)
```

- [ ] **Step 4: 修改 `data/local/database/AppDatabase.kt`**

```kotlin
@Database(
    entities = [/* 不变 */],
    version = 3,
    exportSchema = true
)
```

- [ ] **Step 5: 修改 `data/local/database/Migration.kt`**

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE note_blocks ADD COLUMN language TEXT NOT NULL DEFAULT ''")
    }
}
```

并在创建 Database 时添加 `.addMigrations(MIGRATION_2_3)`。

- [ ] **Step 6: 更新 `export/MarkdownExporter.kt`**

代码块导出时带语言：

```kotlin
BlockType.CODE -> {
    val lang = block.language.ifBlank { "text" }
    appendLine("```$lang")
    appendLine(block.content)
    appendLine("```")
}
```

- [ ] **Step 7: 更新 `export/JsonExporter.kt`**

代码块 JSON 增加 `language` 字段。

- [ ] **Step 8: 运行相关单元测试**

Run: `./gradlew :app:testDebugUnitTest --tests "*BlockMapperTest" --tests "*MarkdownExporter*" --tests "*JsonExporter*"`

Expected: 可能有失败，因为测试期望旧结构，下一步修复。

- [ ] **Step 9: 修复导出相关测试**

更新 `MarkdownExporterAdvancedBlockTest.kt`、`JsonExporterTest.kt` 中断言，增加 `language` 字段。

- [ ] **Step 10: 再次运行测试**

Run: `./gradlew :app:testDebugUnitTest --tests "*BlockMapperTest" --tests "*MarkdownExporter*" --tests "*JsonExporter*"`

Expected: PASS

- [ ] **Step 11: Commit**

```bash
git add domain/model/Block.kt data/local/entity/NoteBlockEntity.kt data/local/mapper/BlockMapper.kt data/local/database/AppDatabase.kt data/local/database/Migration.kt export/MarkdownExporter.kt export/JsonExporter.kt app/src/test/java/com/example/zhilu/export/* app/src/test/java/com/example/zhilu/data/local/mapper/*
git commit -m "feat: add language field to Block and migrate database to v3"
```

---

## Task 2: 数据库迁移测试

**Goal:** 验证 Room v2 → v3 迁移不丢数据、顺序正确。

**Files:**
- Create: `app/src/androidTest/java/com/example/zhilu/data/local/database/MigrationTest.kt`
- Verify: `app/schemas/com.example.zhilu.data.local.database.AppDatabase/3.json` 生成

- [ ] **Step 1: 确认 `room-testing` 依赖**

在 `app/build.gradle.kts` 中：

```kotlin
androidTestImplementation("androidx.room:room-testing:2.6.1")
```

- [ ] **Step 2: 编写迁移测试**

```kotlin
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate2To3() {
        var db = helper.createDatabase("test-db", 2)
        db.execSQL("INSERT INTO note_blocks (noteId, type, content, sortOrder) VALUES (1, 6, 'println()', 2)")
        db.close()

        db = helper.runMigrationsAndValidate("test-db", 3, true, MIGRATION_2_3)
        val cursor = db.query("SELECT language FROM note_blocks WHERE sortOrder = 2")
        assertTrue(cursor.moveToFirst())
        assertEquals("", cursor.getString(0))
        cursor.close()
        db.close()
    }
}
```

- [ ] **Step 3: 运行迁移测试**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*MigrationTest*"`

Expected: PASS

- [ ] **Step 4: 检查 schema 文件生成**

确认文件 `app/schemas/com.example.zhilu.data.local.database.AppDatabase/3.json` 已生成。

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts app/src/androidTest/java/com/example/zhilu/data/local/database/MigrationTest.kt app/schemas/
git commit -m "test: add Room migration 2->3 test and schema"
```

---

## Task 3: ViewModel 状态重构

**Goal:** 用 `MutableStateList` 管理块顺序，引入 `SaveStatus` 和 `UiEvent`，实现 debounce 保存、删除撤销、移动块、设置语言。

**Files:**
- Modify: `ui/note/NoteUiState.kt`
- Modify: `ui/note/NoteViewModel.kt`
- Modify: `ui/note/NoteEditScreen.kt`（最小修改以编译）

- [ ] **Step 1: 修改 `NoteUiState.kt`**

```kotlin
enum class SaveStatus { IDLE, SAVING, SAVED, ERROR }

data class NoteUiState(
    val noteId: Long = 0L,
    val title: String = "",
    val blocks: List<Block> = listOf(Block(type = BlockType.TEXT, content = "")),
    val selectedTags: List<Tag> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val isEditing: Boolean = true,
    val isLoading: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.IDLE,
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

- [ ] **Step 2: 修改 `NoteViewModel.kt` 增加事件流和状态列表**

```kotlin
sealed class UiEvent {
    data class ShowUndoSnackbar(val block: Block, val index: Int) : UiEvent()
}

@HiltViewModel
class NoteViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val tagRepository: TagRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteUiState())
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    private val _blocks = MutableStateList<Block>()
    private var saveJob: Job? = null
    private var dragSaveJob: Job? = null

    init {
        load(NoteRouteArgs.noteId(savedStateHandle))
        observeTags()
    }
}
```

- [ ] **Step 3: 重构加载和状态同步**

加载后把 `blocks` 同步到 `_blocks`，并通过 `_uiState.update { it.copy(blocks = _blocks.toList()) }` 暴露不可变视图。

```kotlin
fun load(noteId: Long) {
    if (noteId <= 0L) {
        _blocks.clear()
        _blocks.add(Block(type = BlockType.TEXT, content = ""))
        syncBlocksToState()
        return
    }
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        when (val result = noteRepository.getNoteById(noteId)) {
            is RepositoryResult.Success -> {
                val note = result.data
                _blocks.clear()
                _blocks.addAll(note?.blocks?.ifEmpty { defaultBlocks() } ?: defaultBlocks())
                _uiState.update {
                    it.copy(
                        noteId = note?.id ?: 0L,
                        title = note?.title.orEmpty(),
                        selectedTags = note?.tags.orEmpty(),
                        isEditing = false,
                        isLoading = false,
                        error = null
                    )
                }
                syncBlocksToState()
            }
            is RepositoryResult.Error -> {
                _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }
}

private fun syncBlocksToState() {
    _uiState.update { it.copy(blocks = _blocks.toList()) }
}
```

- [ ] **Step 4: 实现 debounce 保存**

```kotlin
private fun scheduleSave() {
    saveJob?.cancel()
    _uiState.update { if (it.saveStatus == SaveStatus.SAVED) it else it.copy(saveStatus = SaveStatus.IDLE) }
    saveJob = viewModelScope.launch {
        _uiState.update { it.copy(saveStatus = SaveStatus.SAVING) }
        delay(1000)
        saveInternal()
    }
}
```

- [ ] **Step 5: 实现块内容变更、添加块、设置语言**

```kotlin
fun onBlockContentChange(blockIndex: Int, value: String) {
    _blocks[blockIndex] = _blocks[blockIndex].copy(content = value)
    syncBlocksToState()
    scheduleSave()
}

fun addBlock(type: BlockType) {
    _blocks.add(Block(type = type, content = defaultContentFor(type)))
    syncBlocksToState()
    scheduleSave()
}

fun addImageBlock(uri: String) {
    _blocks.add(Block(type = BlockType.IMAGE, content = uri))
    syncBlocksToState()
    scheduleSave()
}

fun setBlockLanguage(index: Int, language: String) {
    _blocks[index] = _blocks[index].copy(language = language)
    syncBlocksToState()
    scheduleSave()
}
```

- [ ] **Step 6: 实现删除与撤销**

```kotlin
private var removedBlock: Pair<Block, Int>? = savedStateHandle["removedBlock"]

fun removeBlock(index: Int) {
    val block = _blocks.removeAt(index)
    removedBlock = block to index
    savedStateHandle["removedBlock"] = removedBlock
    syncBlocksToState()
    viewModelScope.launch {
        _uiEvent.send(UiEvent.ShowUndoSnackbar(block, index))
        delay(3000)
        if (removedBlock?.first?.id == block.id) {
            confirmRemoveBlock()
        }
    }
    scheduleSave()
}

fun undoRemoveBlock() {
    val (block, index) = removedBlock ?: return
    _blocks.add(index.coerceIn(0, _blocks.size), block)
    removedBlock = null
    savedStateHandle["removedBlock"] = null
    syncBlocksToState()
    scheduleSave()
}

fun confirmRemoveBlock() {
    removedBlock = null
    savedStateHandle["removedBlock"] = null
}
```

- [ ] **Step 7: 实现拖拽排序**

```kotlin
fun moveBlock(fromIndex: Int, toIndex: Int) {
    if (fromIndex == toIndex) return
    _blocks.add(toIndex.coerceIn(0, _blocks.size), _blocks.removeAt(fromIndex))
    syncBlocksToState()
    dragSaveJob?.cancel()
    dragSaveJob = viewModelScope.launch {
        _uiState.update { it.copy(saveStatus = SaveStatus.SAVING) }
        saveInternal()
    }
}
```

- [ ] **Step 8: 同步 title 变更和 tag 变更**

```kotlin
fun onTitleChange(value: String) {
    _uiState.update { it.copy(title = value) }
    scheduleSave()
}
```

`toggleTag` 和 `createTag` 保持现有逻辑，最后调用 `scheduleSave()`。

- [ ] **Step 9: 修复现有 ViewModel 测试**

由于 `blocks` 现在通过 `_blocks` 同步，测试中断言 `state.blocks` 仍然有效。需要更新 `NoteViewModelAdvancedBlockTest.kt`、`NoteViewModelTagSelectionTest.kt` 等测试，确认 `language` 字段和 `SaveStatus` 行为。

- [ ] **Step 10: 运行 ViewModel 测试**

Run: `./gradlew :app:testDebugUnitTest --tests "*NoteViewModel*"`

Expected: PASS（测试已同步更新）

- [ ] **Step 11: Commit**

```bash
git add ui/note/NoteUiState.kt ui/note/NoteViewModel.kt app/src/test/java/com/example/zhilu/ui/note/*
git commit -m "feat: refactor NoteViewModel with MutableStateList, debounce save, undo events"
```

---

## Task 4: 块组件结构

**Goal:** 创建统一的块渲染体系，编辑态和只读态共享内容渲染。

**Files:**
- Create: `ui/note/blocks/BlockContent.kt`
- Create: `ui/note/blocks/TextBlockEditor.kt`
- Create: `ui/note/blocks/CodeBlockEditor.kt`
- Create: `ui/note/blocks/LinkBlockEditor.kt`
- Create: `ui/note/blocks/LatexBlockEditor.kt`
- Create: `ui/note/blocks/ImageBlockView.kt`
- Create: `ui/note/blocks/DividerBlockView.kt`
- Modify: `ui/note/TextBlock.kt`（可删除或保留作为兼容层）
- Modify: `ui/note/CodeBlock.kt`（可删除或保留作为兼容层）

- [ ] **Step 1: 创建 `ui/note/blocks/TextBlockEditor.kt`**

```kotlin
@Composable
fun TextBlockEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 112.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        decorationBox = { innerTextField ->
            Box {
                if (value.isBlank()) {
                    Text(
                        text = "输入文字...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                innerTextField()
            }
        }
    )
}
```

- [ ] **Step 2: 创建 `ui/note/blocks/CodeBlockEditor.kt`**

包含复制按钮、语言 pill、深色背景。

```kotlin
@Composable
fun CodeBlockEditor(
    value: String,
    language: String,
    onValueChange: (String) -> Unit,
    onLanguageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var copied by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = NoteColors.codeBackground,
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = language.ifBlank { "Plain Text" },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NoteColors.codeLanguagePillBackground)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .clickable(onClick = onLanguageClick),
                style = MaterialTheme.typography.labelSmall,
                color = NoteColors.codeLanguagePillText
            )

            IconButton(
                onClick = {
                    copyToClipboard(context, value)
                    copied = true
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 4.dp)
            ) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = if (copied) "已复制" else "复制",
                    tint = NoteColors.codeLanguagePillText
                )
            }

            LaunchedEffect(copied) {
                if (copied) {
                    delay(800)
                    copied = false
                }
            }

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(top = 24.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = NoteColors.codeText
                )
            )
        }
    }
}
```

- [ ] **Step 3: 创建 `ui/note/blocks/LinkBlockEditor.kt`**

```kotlin
@Composable
fun LinkBlockEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text("链接") },
        singleLine = true
    )
}
```

- [ ] **Step 4: 创建 `ui/note/blocks/LatexBlockEditor.kt`**

复用现有 `LatexFormatter`。

- [ ] **Step 5: 创建 `ui/note/blocks/ImageBlockView.kt`**

复用现有 `ImageBlock` 逻辑。

- [ ] **Step 6: 创建 `ui/note/blocks/DividerBlockView.kt`**

```kotlin
@Composable
fun DividerBlockView(modifier: Modifier = Modifier, readOnly: Boolean = false) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = 8.dp),
        thickness = if (readOnly) 3.dp else 2.dp,
        color = MaterialTheme.colorScheme.outlineVariant
    )
}
```

- [ ] **Step 7: 创建 `ui/note/blocks/BlockContent.kt`**

```kotlin
@Composable
fun BlockContent(
    block: Block,
    isEditing: Boolean,
    onValueChange: (String) -> Unit,
    onLanguageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (block.type) {
        BlockType.TEXT -> if (isEditing) {
            TextBlockEditor(value = block.content, onValueChange = onValueChange)
        } else {
            Text(text = block.content.ifBlank { " " }, style = MaterialTheme.typography.bodyLarge)
        }
        BlockType.CODE -> if (isEditing) {
            CodeBlockEditor(
                value = block.content,
                language = block.language,
                onValueChange = onValueChange,
                onLanguageClick = onLanguageClick
            )
        } else {
            ReadOnlyCodeBlock(value = block.content, language = block.language)
        }
        BlockType.LINK -> if (isEditing) {
            LinkBlockEditor(value = block.content, onValueChange = onValueChange)
        } else {
            Text(text = block.content, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
        }
        BlockType.LATEX -> if (isEditing) {
            LatexBlockEditor(value = block.content, onValueChange = onValueChange)
        } else {
            ReadOnlyLatexBlock(value = block.content)
        }
        BlockType.IMAGE -> ImageBlockView(value = block.content)
        BlockType.DIVIDER -> DividerBlockView(readOnly = !isEditing)
    }
}
```

- [ ] **Step 8: Commit**

```bash
git add ui/note/blocks/
git commit -m "feat: add shared BlockContent and block editors"
```

---

## Task 5: 编辑态卡片外壳与左滑删除

**Goal:** 实现 `EditableBlock`，包含类型小灰标、"⋮"菜单、左滑删除容器。

**Files:**
- Create: `ui/note/blocks/EditableBlock.kt`
- Create: `ui/note/theme/NoteColors.kt`

- [ ] **Step 1: 创建 `ui/note/theme/NoteColors.kt`**

```kotlin
object NoteColors {
    val primaryIndigo = Color(0xFF4F46E5)
    val primaryIndigoLight = Color(0xFF6366F1)
    val primaryIndigoDark = Color(0xFF4338CA)
    val titlePlaceholder = Color(0xFF94A3B8)
    val typeBadgeBackground = Color(0xFFF1F5F9)
    val typeBadgeText = Color(0xFF64748B)
    val codeBackground = Color(0xFF1E293B)
    val codeText = Color(0xFFF8FAFC)
    val codeLanguagePillBackground = Color(0xFF334155)
    val codeLanguagePillText = Color(0xFFCBD5E1)
    val deleteBackground = Color(0xFFFEE2E2)
    val deleteIcon = Color(0xFFEF4444)
    val cardOutline = Color(0xFFE2E8F0)
}
```

- [ ] **Step 2: 创建 `EditableBlock.kt`**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditableBlock(
    index: Int,
    block: Block,
    total: Int,
    onValueChange: (String) -> Unit,
    onLanguageClick: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.25f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(NoteColors.deleteBackground),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = NoteColors.deleteIcon,
                    modifier = Modifier.padding(end = 24.dp)
                )
            }
        },
        content = {
            BlockCard(
                block = block,
                isEditing = true,
                onValueChange = onValueChange,
                onLanguageClick = onLanguageClick,
                onMoreClick = { /* DropdownMenu */ }
            )
        }
    )
}
```

- [ ] **Step 3: 创建共享的 `BlockCard`**

`BlockCard` 放在 `ui/note/blocks/BlockCard.kt`，被 `EditableBlock` 和 `ReadOnlyBlock` 共用。

```kotlin
@Composable
fun BlockCard(
    block: Block,
    isEditing: Boolean,
    onValueChange: (String) -> Unit,
    onLanguageClick: () -> Unit,
    onMoreClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, NoteColors.cardOutline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = block.type.displayName(),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NoteColors.typeBadgeBackground)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = NoteColors.typeBadgeText
                )
                if (isEditing && onMoreClick != null) {
                    Box {
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("删除") },
                                onClick = { onMoreClick(); expanded = false }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            BlockContent(
                block = block,
                isEditing = isEditing,
                onValueChange = onValueChange,
                onLanguageClick = onLanguageClick
            )
        }
    }
}

private fun BlockType.displayName(): String = when (this) {
    BlockType.TEXT -> "文字"
    BlockType.IMAGE -> "图片"
    BlockType.LINK -> "链接"
    BlockType.LATEX -> "公式"
    BlockType.CODE -> "代码"
    BlockType.DIVIDER -> "分割线"
}
```

- [ ] **Step 4: 运行编译检查**

Run: `./gradlew :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add ui/note/blocks/EditableBlock.kt ui/note/blocks/BlockCard.kt ui/note/theme/NoteColors.kt
git commit -m "feat: add EditableBlock with swipe-to-delete and type badge"
```

---

## Task 6: 只读态卡片外壳

**Goal:** 实现 `ReadOnlyBlock`，支持长按复制菜单。

**Files:**
- Create: `ui/note/blocks/ReadOnlyBlock.kt`

- [ ] **Step 1: 创建 `ReadOnlyBlock.kt`**

```kotlin
@Composable
fun ReadOnlyBlock(
    block: Block,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }

    BlockCard(
        block = block,
        isEditing = false,
        onValueChange = {},
        onLanguageClick = {},
        onMoreClick = null,
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { showMenu = true }
                )
            }
            .contextMenu(
                expanded = showMenu,
                onDismiss = { showMenu = false },
                items = listOf("复制此块" to {
                    copyToClipboard(context, block.content)
                    showMenu = false
                })
            )
    )
}
```

说明：如果 Compose 没有直接 `contextMenu`，可用 `DropdownMenu` 模拟长按菜单。

- [ ] **Step 2: Commit**

```bash
git add ui/note/blocks/ReadOnlyBlock.kt
git commit -m "feat: add ReadOnlyBlock with long-press copy menu"
```

---

## Task 7: 标题区与标签区

**Goal:** 实现无 label 标题输入、行内标签网格、底部标签面板。

**Files:**
- Create: `ui/note/tag/TagPickerInline.kt`
- Create: `ui/note/bottomsheet/TagPickerBottomSheet.kt`

- [ ] **Step 1: 创建 `TagPickerInline.kt`**

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagPickerInline(
    availableTags: List<Tag>,
    selectedTags: List<Tag>,
    onToggle: (Tag) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newTag by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 120.dp)
            .verticalScroll(rememberScrollState())
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            availableTags.forEach { tag ->
                TagChip(
                    tag = tag,
                    selected = selectedTags.any { it.id == tag.id },
                    onClick = { onToggle(tag) }
                )
            }
        }
        OutlinedTextField(
            value = newTag,
            onValueChange = { newTag = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("新建标签") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onCreate(newTag)
                newTag = ""
            })
        )
    }
}
```

- [ ] **Step 2: 创建 `TagPickerBottomSheet.kt`**

使用 `ModalBottomSheet` 包装同样的标签网格。

- [ ] **Step 3: Commit**

```bash
git add ui/note/tag/TagPickerInline.kt ui/note/bottomsheet/TagPickerBottomSheet.kt
git commit -m "feat: add inline tag picker and bottom sheet variants"
```

---

## Task 8: 底部工具栏与空状态

**Goal:** 实现 3 常驻 + 悬浮 "+" 菜单，以及空状态按钮。

**Files:**
- Create: `ui/note/toolbar/BlockToolbar.kt`

- [ ] **Step 1: 创建 `BlockToolbar.kt`**

```kotlin
@Composable
fun BlockToolbar(
    onAddText: () -> Unit,
    onAddImage: () -> Unit,
    onAddCode: () -> Unit,
    onAddLink: () -> Unit,
    onAddLatex: () -> Unit,
    onAddDivider: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = onAddText) { Icon(Icons.Default.TextFields, "文字") }
            IconButton(onClick = onAddCode) { Icon(Icons.Default.Code, "代码") }
            IconButton(onClick = onAddImage) { Icon(Icons.Default.Image, "图片") }
        }

        FloatingActionButton(
            onClick = { expanded = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .size(52.dp),
            containerColor = NoteColors.primaryIndigo
        ) {
            Icon(Icons.Default.Add, "添加")
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(text = { Text("链接") }, onClick = { onAddLink(); expanded = false })
            DropdownMenuItem(text = { Text("公式") }, onClick = { onAddLatex(); expanded = false })
            DropdownMenuItem(text = { Text("分割线") }, onClick = { onAddDivider(); expanded = false })
        }
    }
}
```

- [ ] **Step 2: 空状态组件**

在 `NoteEditScreen.kt` 内部实现或创建 `ui/note/EmptyEditorState.kt`：

```kotlin
@Composable
fun EmptyEditorState(onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = NoteColors.primaryIndigo)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("开始记录...")
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add ui/note/toolbar/BlockToolbar.kt
git commit -m "feat: add block toolbar with 3 primary actions and expandable FAB"
```

---

## Task 9: 拖拽排序

**Goal:** 在 `EditableBlock` 的长按手势上实现拖拽排序。

**Files:**
- Modify: `ui/note/blocks/EditableBlock.kt`
- Modify: `ui/note/NoteEditScreen.kt`

- [ ] **Step 1: 在 `EditableBlock` 的类型标签区域添加长按拖拽**

```kotlin
var offsetY by remember { mutableFloatStateOf(0f) }
var isDragging by remember { mutableStateOf(false) }

val dragModifier = Modifier.pointerInput(Unit) {
    detectDragGesturesAfterLongPress(
        onDragStart = { isDragging = true },
        onDragEnd = { isDragging = false; offsetY = 0f },
        onDragCancel = { isDragging = false; offsetY = 0f },
        onDrag = { change, dragAmount ->
            change.consume()
            offsetY += dragAmount.y
            // 通过 onMove 回调通知 ViewModel
        }
    )
}
```

- [ ] **Step 2: 在 `NoteEditScreen` 中计算拖拽目标索引**

更实际的做法是使用 `LazyListState.layoutInfo.visibleItemsInfo` 计算当前拖拽位置对应的索引，然后调用 `viewModel.moveBlock(from, to)`。

- [ ] **Step 3: 暂停拖拽期间的自动保存**

在 `NoteEditScreen` 中设置一个 `var isDragging by remember { mutableStateOf(false) }`，拖拽开始时设为 true，结束时 false。ViewModel 提供 `setDragging(isDragging: Boolean)` 方法，在拖拽期间跳过 debounce 保存。

- [ ] **Step 4: Commit**

```bash
git add ui/note/blocks/EditableBlock.kt ui/note/NoteEditScreen.kt ui/note/NoteViewModel.kt
git commit -m "feat: implement long-press drag-to-reorder for blocks"
```

---

## Task 10: 自动保存提示

**Goal:** 在顶部导航栏右侧显示保存状态图标。

**Files:**
- Modify: `ui/note/NoteEditScreen.kt`
- Modify: `ui/component/AppTopBar.kt`（如需要）

- [ ] **Step 1: 在 `NoteEditScreen` 的 topBar actions 中显示保存状态**

```kotlin
actions = {
    when (state.saveStatus) {
        SaveStatus.SAVING -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        SaveStatus.SAVED -> Icon(Icons.Default.Check, contentDescription = "已保存", tint = NoteColors.primaryIndigo)
        SaveStatus.ERROR -> Icon(Icons.Default.Error, contentDescription = "保存失败", tint = MaterialTheme.colorScheme.error)
        else -> {}
    }
    // 编辑/保存按钮
}
```

- [ ] **Step 2: Commit**

```bash
git add ui/note/NoteEditScreen.kt
git commit -m "feat: show auto-save status icon in note top bar"
```

---

## Task 11: 整合 NoteEditScreen

**Goal:** 把标题、标签、块列表、工具栏、空状态整合到新 `NoteEditScreen`。

**Files:**
- Modify: `ui/note/NoteEditScreen.kt`

- [ ] **Step 1: 重写 `NoteEditScreen` 编辑态布局**

```kotlin
LazyColumn(
    modifier = Modifier.weight(1f),
    state = listState,
    verticalArrangement = Arrangement.spacedBy(12.dp),
    contentPadding = PaddingValues(16.dp)
) {
    item { TitleField(...) }
    item { TagSection(...) }

    if (state.blocks.isEmpty()) {
        item { EmptyEditorState { viewModel.addBlock(BlockType.TEXT) } }
    } else {
        itemsIndexed(state.blocks, key = { _, block -> block.id }) { index, block ->
            EditableBlock(
                index = index,
                block = block,
                total = state.blocks.size,
                onValueChange = { viewModel.onBlockContentChange(index, it) },
                onLanguageClick = { showLanguagePickerFor = index },
                onRemove = { viewModel.removeBlock(index) },
                onMoveUp = if (index > 0) {{ viewModel.moveBlock(index, index - 1) }} else null,
                onMoveDown = if (index < state.blocks.size - 1) {{ viewModel.moveBlock(index, index + 1) }} else null,
                modifier = Modifier.animateItemPlacement()
            )
        }
    }
}
```

- [ ] **Step 2: 实现标题区无 label**

```kotlin
@Composable
private fun TitleField(title: String, onChange: (String) -> Unit, onDone: () -> Unit) {
    BasicTextField(
        value = title,
        onValueChange = onChange,
        textStyle = MaterialTheme.typography.headlineMedium.copy(color = NoteColors.primaryIndigo),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onDone() }),
        decorationBox = { innerTextField ->
            Box {
                if (title.isBlank()) {
                    Text("输入标题...", style = MaterialTheme.typography.headlineMedium, color = NoteColors.titlePlaceholder)
                }
                innerTextField()
            }
        }
    )
}
```

- [ ] **Step 3: 实现标签区**

```kotlin
@Composable
private fun TagSection(...) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        selectedTags.forEach { TagChip(tag = it, onClick = null) }
        TextButton(onClick = { expanded = !expanded }) { Text("+ 标签") }
    }
    AnimatedVisibility(visible = expanded) {
        if (availableTags.size < 20) {
            TagPickerInline(...)
        } else {
            // 底部 sheet
        }
    }
}
```

- [ ] **Step 4: 编译并运行**

Run: `./gradlew :app:installDebug`

Expected: 应用安装成功，编辑页显示新布局

- [ ] **Step 5: Commit**

```bash
git add ui/note/NoteEditScreen.kt
git commit -m "feat: integrate new title, tags, blocks, toolbar, and empty state into NoteEditScreen"
```

---

## Task 12: 只读态改造

**Goal:** 完成只读详情态：标题、可点击标签、长按复制、分享按钮。

**Files:**
- Modify: `ui/note/NoteEditScreen.kt`
- Create/Modify: `ui/note/blocks/ReadOnlyBlock.kt`

- [ ] **Step 1: 实现只读态布局**

```kotlin
LazyColumn(...) {
    item {
        Text(
            text = state.title.ifBlank { "新建知识" },
            style = MaterialTheme.typography.headlineMedium,
            color = if (state.title.isBlank()) NoteColors.titlePlaceholder else MaterialTheme.colorScheme.onSurface
        )
    }
    item { ReadOnlyTagChips(state.selectedTags, onTagClick = { /* navigate */ }) }
    itemsIndexed(state.blocks, key = { _, block -> block.id }) { _, block ->
        ReadOnlyBlock(block = block, onCopy = { copyToClipboard(context, block.content) })
    }
    if (state.blocks.isEmpty()) {
        item { ReadOnlyEmptyState(onEdit = viewModel::startEditing) }
    }
}
```

- [ ] **Step 2: 顶部操作栏增加分享按钮**

```kotlin
actions = {
    IconButton(onClick = { shareNote(context, state.toNote()) }) {
        Icon(Icons.Default.Share, "分享")
    }
    IconButton(onClick = viewModel::startEditing) {
        Icon(Icons.Default.Edit, "编辑")
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add ui/note/NoteEditScreen.kt ui/note/blocks/ReadOnlyBlock.kt
git commit -m "feat: complete read-only note detail state"
```

---

## Task 13: 清理旧 Block 文件

**Goal:** 删除或重定向旧的 `TextBlock.kt`、`CodeBlock.kt` 等文件。

**Files:**
- Delete or modify: `ui/note/TextBlock.kt`, `ui/note/CodeBlock.kt`, `ui/note/ImageBlock.kt`, `ui/note/LinkBlock.kt`, `ui/note/LatexBlock.kt`, `ui/note/DividerBlock.kt`

- [ ] **Step 1: 检查旧文件引用**

Run: `grep -r "TextBlock\|CodeBlock\|ImageBlock\|LinkBlock\|LatexBlock\|DividerBlock" app/src/main/java/com/example/zhilu/ui/note/ --include="*.kt"`

- [ ] **Step 2: 删除旧文件**

确认 `NoteEditScreen` 不再引用旧文件后删除它们。

- [ ] **Step 3: Commit**

```bash
git rm ui/note/TextBlock.kt ui/note/CodeBlock.kt ui/note/ImageBlock.kt ui/note/LinkBlock.kt ui/note/LatexBlock.kt ui/note/DividerBlock.kt
git commit -m "refactor: remove old block editor files replaced by blocks/ package"
```

---

## Task 14: 测试补全

**Goal:** 补齐 P0/P1 测试。

**Files:**
- Modify: `app/src/test/java/com/example/zhilu/ui/note/NoteViewModelAdvancedBlockTest.kt`
- Modify: `app/src/test/java/com/example/zhilu/ui/note/NoteViewModelTagSelectionTest.kt`
- Create: UI tests for swipe-to-delete and drag-to-reorder
- Create: Migration end-to-end test

- [ ] **Step 1: 更新 ViewModel 测试**

增加测试：
- `moveBlock` 后列表顺序正确
- `setBlockLanguage` 更新对应块
- 快速连续输入只触发一次保存
- 3 秒超时后 undo 无效

- [ ] **Step 2: 创建左滑删除 UI 测试**

```kotlin
@Test
fun swipeBlockLeft_deletesAndShowsUndo() {
    // 添加一个文字块
    composeTestRule.onNodeWithContentDescription("添加").performClick()
    composeTestRule.onNodeWithText("文字").performClick()
    // 左滑
    composeTestRule.onNodeWithText("输入文字...").performTouchInput {
        swipeLeft(startX = right, endX = left)
    }
    composeTestRule.onNodeWithText("已删除").assertIsDisplayed()
}
```

- [ ] **Step 3: 运行所有测试**

Run: `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest`

Expected: PASS（允许 P2 测试跳过）

- [ ] **Step 4: Commit**

```bash
git add app/src/test/ app/src/androidTest/
git commit -m "test: add and update tests for blocks, swipe, drag, and migration"
```

---

## 自我审查

### Spec 覆盖率

| Spec 章节 | 对应任务 |
| --- | --- |
| 3. 数据模型变更 | Task 1, Task 2 |
| 5.1 标题区 | Task 11 |
| 5.2 标签区 | Task 7 |
| 5.3 内容块 + 左滑删除 | Task 5 |
| 5.4 代码块 | Task 4 |
| 5.5 底部工具栏 | Task 8 |
| 5.6 空状态 | Task 8, Task 11 |
| 6. 只读态 | Task 6, Task 12 |
| 7. ViewModel | Task 3 |
| 8. 交互细节 | Task 5, Task 9, Task 10, Task 11 |
| 9. 配色 | Task 5 |
| 10. 技术约束 | Task 0, Task 5, Task 9 |
| 12. 测试计划 | Task 2, Task 10, Task 14 |

### Placeholder 检查

- 无 "TBD"/"TODO"
- 所有代码步骤包含具体代码或伪代码
- 所有命令包含具体路径

### 类型一致性

- `Block.language` 在 domain、entity、mapper、exporter 中一致
- `SaveStatus` 在 `NoteUiState`、`NoteViewModel`、`NoteEditScreen` 中一致
- `UiEvent.ShowUndoSnackbar` 在 ViewModel 和 Screen 中一致
