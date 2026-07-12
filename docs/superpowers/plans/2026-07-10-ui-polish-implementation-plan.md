# 知录笔记编辑页 UI 打磨实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不引入新依赖、不改动业务逻辑的前提下，按设计文档对笔记编辑页进行 8 项视觉与交互打磨：inline 标签输入、类型图标化、分割线去卡片、图片圆角统一、公式横向滚动、节奏化间距、工具栏边界、底部留白遮罩。

**Architecture:** 所有改动集中在 Compose UI 层。`NoteEditScreen` 负责整体列表间距与底部遮罩；`TagPickerInline` 自包含标签展开/输入逻辑；`BlockCard` / `BlockContent` / 各 BlockView 负责单块视觉；`BlockToolbar` 负责工具栏样式。通过纯组合与 Modifier 调整完成，不改动 ViewModel 与数据模型。

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Coil, JLatexMath Android.

---

## 文件结构

| 文件 | 责任 |
|------|------|
| `app/src/main/java/com/example/zhilu/ui/note/tag/TagPickerInline.kt` | 标签 inline 输入与候选标签展开区域 |
| `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockCard.kt` | 类型标签改为图标，编辑态显示/只读态隐藏 |
| `app/src/main/java/com/example/zhilu/ui/note/blocks/DividerBlockView.kt` | 分割线去卡片化，编辑态触控区与拖拽态 |
| `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockContent.kt` | DIVIDER 分支不再套 BlockCard |
| `app/src/main/java/com/example/zhilu/ui/note/blocks/ImageBlockView.kt` | 图片顶满卡片、最大高度 400dp、占位背景 |
| `app/src/main/java/com/example/zhilu/ui/note/blocks/LatexBlockEditor.kt` | 公式编辑预览与阅读态横向滚动 |
| `app/src/main/java/com/example/zhilu/ui/note/blocks/EditableBlock.kt` | 编辑态块间距、分割线无卡片手势兼容 |
| `app/src/main/java/com/example/zhilu/ui/note/blocks/ReadOnlyBlock.kt` | 只读态块间距 |
| `app/src/main/java/com/example/zhilu/ui/note/toolbar/BlockToolbar.kt` | 工具栏贴底、阴影、顶部分隔线 |
| `app/src/main/java/com/example/zhilu/ui/note/NoteEditScreen.kt` | 列表间距、底部渐变遮罩、只读态底部 padding |

---

## Task 1: 标签区 Inline 输入

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/tag/TagPickerInline.kt`
- Test: 手动运行应用，进入编辑记录页，点击「+ 标签」验证展开/收起。

### Step 1: 替换 TagPickerInline 实现

将 `TagPickerInline` 改为：默认显示已选 chip 行 + 「+ 标签」chip；点击后下方展开候选区域，最前为输入框，后为未选中的已有标签；回车创建并自动选中；点击外部或返回键收起。

```kotlin
package com.example.zhilu.ui.note.tag

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.example.zhilu.domain.model.Tag
import com.example.zhilu.ui.component.TagChip

@Composable
fun TagPickerInline(
    availableTags: List<Tag>,
    selectedTags: List<Tag>,
    onToggle: (Tag) -> Unit,
    onCreate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    val selectedTagIds = remember(selectedTags) { selectedTags.map { it.id }.toSet() }
    val unselectedTags = remember(availableTags, selectedTagIds) {
        availableTags.filter { !selectedTagIds.contains(it.id) }
    }

    fun submitTag() {
        val trimmed = newTagName.trim()
        if (trimmed.isBlank()) return
        onCreate(trimmed)
        newTagName = ""
    }

    BackHandler(enabled = isExpanded && newTagName.isBlank()) {
        isExpanded = false
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures {
                    if (isExpanded && newTagName.isBlank()) {
                        isExpanded = false
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                selectedTags.forEach { tag ->
                    TagChip(
                        tag = tag,
                        selected = true,
                        onClick = { onToggle(tag) }
                    )
                }
                if (!isExpanded) {
                    AddTagChip(onClick = { isExpanded = true })
                }
            }

            if (isExpanded) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TagInputField(
                            value = newTagName,
                            onValueChange = { newTagName = it },
                            onSubmit = { submitTag() }
                        )

                        if (unselectedTags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                unselectedTags.forEach { tag ->
                                    TagChip(
                                        tag = tag,
                                        selected = false,
                                        onClick = { onToggle(tag) }
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "暂无更多标签",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTagChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "标签",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun TagInputField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .defaultMinSize(minWidth = 120.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier.defaultMinSize(minWidth = 96.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier,
                    textStyle = MaterialTheme.typography.labelMedium.copy(color = textColor),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    singleLine = true,
                    cursorBrush = SolidColor(textColor),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                text = "新标签",
                                style = MaterialTheme.typography.labelMedium,
                                color = placeholderColor
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }
    }
}
```

### Step 2: 编译检查

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL

---

## Task 2: BlockCard 类型标签改为小图标

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockCard.kt`
- Test: 打开编辑记录页，确认右上角显示小图标；切换为只读态，图标消失。

### Step 1: 添加类型图标映射

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.ui.graphics.vector.ImageVector

fun blockTypeIcon(type: BlockType): ImageVector? = when (type) {
    BlockType.TEXT -> Icons.AutoMirrored.Filled.Article
    BlockType.IMAGE -> Icons.Default.Image
    BlockType.LINK -> Icons.Default.Link
    BlockType.LATEX -> Icons.Default.Functions
    BlockType.CODE -> Icons.Default.Code
    BlockType.DIVIDER -> null
    BlockType.TODO -> null
}
```

### Step 2: 修改 BlockCard 头部

```kotlin
@Composable
fun BlockCard(
    block: Block,
    isEditing: Boolean,
    // ... existing params ...
) {
    // ... existing border/background ...

    Surface(...) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditing) {
                    val icon = blockTypeIcon(block.type)
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = blockTypeLabel(block.type),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                }
                if (shouldShowBlockActionMenu(...)) {
                    BlockOverflowMenu(...)
                }
            }
            BlockContent(...)
        }
    }
}
```

### Step 3: 删除 TypeBadge composable

删除 `BlockCard.kt` 中的 `private fun TypeBadge(...)` 及其调用。

### Step 4: 编译检查

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL

---

## Task 3: 分割线去卡片化

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/DividerBlockView.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockContent.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/EditableBlock.kt`（兼容拖拽态）
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/ReadOnlyBlock.kt`（兼容只读态）
- Test: 添加分割线块，观察阅读态为纯横线，编辑态有触控区；长按拖拽时线条变粗变 primary 色。

### Step 1: 重写 DividerBlockView

```kotlin
package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun DividerBlockView(
    readOnly: Boolean,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier
) {
    val lineColor = if (isDragging) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val lineThickness = if (isDragging) 4.dp else 3.dp

    if (readOnly) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(lineThickness)
                    .clip(RoundedCornerShape(lineThickness / 2))
                    .background(lineColor)
            )
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(lineThickness)
                    .clip(RoundedCornerShape(lineThickness / 2))
                    .background(lineColor)
            )
        }
    }
}
```

### Step 2: BlockContent 中 DIVIDER 不再套 BlockCard

由于 `BlockContent` 被 `BlockCard` 调用，`BlockCard` 会继续保持卡片。为了避免双重卡片，需要让 `EditableBlock` / `ReadOnlyBlock` 对 `DIVIDER` 类型特殊处理：不调用 `BlockCard`，直接渲染 `DividerBlockView`。因此 `BlockContent` 中的 `DIVIDER` 分支先保留，但实际不会被调用（见 Step 3/4）。或者更干净地，把 `BlockContent` 的 `DIVIDER` 分支删除，由调用方处理。

这里选择由调用方处理。修改 `BlockContent.kt`：

```kotlin
BlockType.DIVIDER -> DividerBlockView(readOnly = !isEditing, modifier = modifier)
```

保持不变，但 `EditableBlock` 和 `ReadOnlyBlock` 会覆盖此分支。

### Step 3: EditableBlock 对 DIVIDER 特殊处理

```kotlin
@Composable
fun EditableBlock(...) {
    // ... existing code ...

    val content: @Composable () -> Unit = {
        if (block.type == BlockType.DIVIDER) {
            DividerBlockView(
                readOnly = false,
                isDragging = isDragging,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            BlockCard(
                block = block,
                isEditing = true,
                // ... existing params ...
            )
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.then(dragModifier),
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = { DeleteBackground() }
    ) {
        content()
    }
}
```

### Step 4: ReadOnlyBlock 对 DIVIDER 特殊处理

```kotlin
@Composable
fun ReadOnlyBlock(...) {
    // ... existing code ...

    Box(
        modifier = modifier.pointerInput(block.id, block.content) {
            detectTapGestures(onLongPress = { copyMenuExpanded = true })
        }
    ) {
        if (block.type == BlockType.DIVIDER) {
            DividerBlockView(
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            BlockCard(
                block = block,
                isEditing = false,
                // ... existing params ...
            )
        }
        DropdownMenu(...)
    }
}
```

### Step 5: 编译检查

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL

---

## Task 4: 图片块圆角统一与最大高度

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/ImageBlockView.kt`
- Test: 添加图片块，观察图片顶满卡片，圆角由外层 BlockCard 提供；加载占位为 surfaceVariant。

### Step 1: 重写 ImageBlockView

```kotlin
package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.zhilu.domain.model.ImageBlockContent

private const val MAX_IMAGE_HEIGHT_DP = 400

@Composable
fun ImageBlockView(
    value: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val imageUri = ImageBlockContent.displayUri(value)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp, max = MAX_IMAGE_HEIGHT_DP.dp),
        contentAlignment = Alignment.Center
    ) {
        if (imageUri.isNotBlank()) {
            SubcomposeAsyncImage(
                model = imageUri,
                contentDescription = "图片",
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        enabled = onClick != null,
                        onClick = { onClick?.invoke() }
                    ),
                contentScale = ContentScale.Crop,
                loading = {
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
                },
                error = {
                    ImagePlaceholder(
                        icon = Icons.Default.BrokenImage,
                        message = "图片加载失败"
                    )
                }
            )
        } else {
            ImagePlaceholder(
                icon = Icons.Default.Image,
                message = "尚未选择图片"
            )
        }
    }
}

@Composable
private fun ImagePlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

### Step 2: 编译检查

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL

---

## Task 5: 公式块横向滚动

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/LatexBlockEditor.kt`
- Test: 输入长公式，编辑态与阅读态均可横向滚动。

### Step 1: 修改 LatexPreview 与 ReadOnlyLatexBlockContent

由于按渲染宽度判断短/长较复杂，先统一使用 `HorizontalScroll` + 左对齐，保证长公式不被截断。

```kotlin
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState

@Composable
private fun LatexPreview(
    value: String,
    modifier: Modifier = Modifier
) {
    if (value.isBlank()) return

    val state = rememberLatexImage(
        latex = value,
        textSizeSp = 18f,
        color = MaterialTheme.colorScheme.onSurface
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp)
            ) {
                LatexImage(
                    state = state,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ReadOnlyLatexBlockContent(
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp)
        ) {
            if (value.isBlank()) {
                Text(
                    text = "空公式",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val state = rememberLatexImage(
                    latex = value,
                    textSizeSp = 20f,
                    color = MaterialTheme.colorScheme.onSurface
                )
                LatexImage(
                    state = state,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
```

### Step 2: 编译检查

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL

---

## Task 6: 节奏化块间距

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/NoteEditScreen.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/EditableBlock.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/blocks/ReadOnlyBlock.kt`
- Test: 混合添加文本/图片/分割线块，观察同类块间距 8dp、异类 16dp、分割线前后 24dp。

### Step 1: 在 NoteEditScreen 中移除统一 12dp 间距

将 `LazyColumn` 的 `verticalArrangement = Arrangement.spacedBy(12.dp)` 改为 `Arrangement.spacedBy(0.dp)`，改由每个 item 自行控制间距。

```kotlin
LazyColumn(
    state = listState,
    modifier = Modifier.weight(1f),
    verticalArrangement = Arrangement.spacedBy(0.dp),
    contentPadding = PaddingValues(16.dp)
) {
    // ...
}
```

### Step 2: 新增 EditableBlock 与 ReadOnlyBlock 的间距逻辑

在 `EditableBlock.kt` 和 `ReadOnlyBlock.kt` 新增 `spacingModifier`：

```kotlin
import com.example.zhilu.domain.model.BlockType

fun spacingForEditableBlock(
    prevType: BlockType?,
    currentType: BlockType,
    nextType: BlockType?
): androidx.compose.ui.unit.Dp = when {
    currentType == BlockType.DIVIDER || prevType == BlockType.DIVIDER -> 24.dp
    prevType != null && prevType == currentType -> 8.dp
    prevType != null -> 16.dp
    else -> 0.dp
}
```

更实用的做法是在 `NoteEditScreen` 渲染列表时直接给每个 item 设置 padding top，并计算是否需要显示同类分隔线：

```kotlin
itemsIndexed(state.blocks, key = { _, block -> block.id }) { index, block ->
    val prevType = state.blocks.getOrNull(index - 1)?.type
    val topPadding = when {
        block.type == BlockType.DIVIDER || prevType == BlockType.DIVIDER -> 24.dp
        prevType != null && prevType == block.type -> 8.dp
        prevType != null -> 16.dp
        else -> 0.dp
    }
    val showTopDivider = prevType != null &&
        prevType == block.type &&
        block.type != BlockType.DIVIDER &&
        block.type != BlockType.IMAGE &&
        block.type != BlockType.LATEX
    EditableBlock(
        modifier = Modifier
            .padding(top = topPadding)
            .animateItem(),
        showTopDivider = showTopDivider,
        // ... other params ...
    )
}
```

只读态同样处理：

```kotlin
itemsIndexed(state.blocks) { index, block ->
    val prevType = state.blocks.getOrNull(index - 1)?.type
    val topPadding = when {
        block.type == BlockType.DIVIDER || prevType == BlockType.DIVIDER -> 24.dp
        prevType != null && prevType == block.type -> 8.dp
        prevType != null -> 16.dp
        else -> 0.dp
    }
    val showTopDivider = prevType != null &&
        prevType == block.type &&
        block.type != BlockType.DIVIDER &&
        block.type != BlockType.IMAGE &&
        block.type != BlockType.LATEX
    ReadOnlyBlock(
        modifier = Modifier.padding(top = topPadding),
        showTopDivider = showTopDivider,
        // ... other params ...
    )
}
```

### Step 3: 同类型块之间的淡分隔线

在 Task 3 的基础上，对 `EditableBlock` 和 `ReadOnlyBlock` 的 `SwipeToDismissBox` 外层用 `Column` 包裹，当 `showTopDivider` 为 true 时在顶部渲染一条 1dp 淡分隔线。

`EditableBlock.kt`：

```kotlin
import androidx.compose.foundation.background

@Composable
fun EditableBlock(
    // ... existing params ...
    showTopDivider: Boolean = false
) {
    // ... existing dismissState and dragModifier ...

    Column(
        modifier = modifier.then(dragModifier)
    ) {
        if (showTopDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
            )
        }
        SwipeToDismissBox(
            state = dismissState,
            modifier = Modifier.fillMaxWidth(),
            enableDismissFromStartToEnd = false,
            enableDismissFromEndToStart = true,
            backgroundContent = { DeleteBackground() }
        ) {
            if (block.type == BlockType.DIVIDER) {
                DividerBlockView(
                    readOnly = false,
                    isDragging = isDragging,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                BlockCard(
                    block = block,
                    isEditing = true,
                    // ... existing params ...
                )
            }
        }
    }
}
```

`ReadOnlyBlock.kt`：

```kotlin
@Composable
fun ReadOnlyBlock(
    // ... existing params ...
    showTopDivider: Boolean = false
) {
    // ... existing copyMenuExpanded ...

    Box(
        modifier = modifier.pointerInput(block.id, block.content) {
            detectTapGestures(onLongPress = { copyMenuExpanded = true })
        }
    ) {
        Column {
            if (showTopDivider) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                )
            }
            if (block.type == BlockType.DIVIDER) {
                DividerBlockView(
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                BlockCard(
                    block = block,
                    isEditing = false,
                    // ... existing params ...
                )
            }
        }
        DropdownMenu(...)
    }
}
```

### Step 4: 编译检查

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL

---

## Task 7: 工具栏样式调整

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/toolbar/BlockToolbar.kt`
- Modify: `app/src/main/java/com/example/zhilu/ui/note/NoteEditScreen.kt`（工具栏容器贴底）
- Test: 编辑页底部工具栏贴底，顶部有淡分隔线，阴影 4dp。

### Step 1: 修改 BlockToolbar

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
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, top = 8.dp, bottom = 0.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceVariant),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp
    ) {
        // ... existing AnimatedContent ...
    }
}
```

### Step 2: 在 NoteEditScreen 中给工具栏加顶部分隔线并贴底

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider

// 替换 NoteEditScreen 中工具栏代码：
if (state.isEditing) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BlockToolbar(
                onAddText = { viewModel.addBlock(BlockType.TEXT) },
                onAddCode = { viewModel.addBlock(BlockType.CODE) },
                onAddImage = { navController.navigate("camera") },
                onPickImageFromGallery = ::launchGalleryPicker,
                onAddLink = { viewModel.addBlock(BlockType.LINK) },
                onAddLatex = { viewModel.addBlock(BlockType.LATEX) },
                onAddDivider = { viewModel.addBlock(BlockType.DIVIDER) }
            )
            if (state.isProcessingImage) { ... }
        }
    }
}
```

### Step 3: 编译检查

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL

---

## Task 8: 底部渐变遮罩与留白

**Files:**
- Modify: `app/src/main/java/com/example/zhilu/ui/note/NoteEditScreen.kt`
- Test: 编辑态滚动到底部，工具栏上方有 40dp 渐变遮罩；只读态底部有 32dp padding。

### Step 1: 编辑态 LazyColumn 底部 padding 设为 0dp

已在 Task 6 中将 `contentPadding` 保留为 `PaddingValues(16.dp)`。需要把它改为顶部/左右 16dp、底部 0dp：

```kotlin
LazyColumn(
    state = listState,
    modifier = Modifier.weight(1f),
    verticalArrangement = Arrangement.spacedBy(0.dp),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 0.dp)
) { ... }
```

### Step 2: 在 LazyColumn 与工具栏之间加渐变遮罩

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush

if (state.isEditing) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to MaterialTheme.colorScheme.background
                )
            )
    )
}
```

此 Box 需要放在 LazyColumn 与工具栏 Column 之间。如果当前结构是 LazyColumn 与工具栏在同一 Column 中，直接在中间插入此 Box 即可。

### Step 3: 只读态底部 padding 32dp

只读态 `LazyColumn` 的 `contentPadding` 底部改为 32dp：

```kotlin
LazyColumn(
    state = listState,
    modifier = Modifier.weight(1f),
    verticalArrangement = Arrangement.spacedBy(0.dp),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
) { ... }
```

由于编辑态和只读态在同一 LazyColumn 中，需要条件化：

```kotlin
val bottomPadding = if (state.isEditing) 0.dp else 32.dp
contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = bottomPadding)
```

### Step 4: 编译检查

Run: `./gradlew :app:compileDebugKotlin --no-daemon`
Expected: BUILD SUCCESSFUL

---

## Task 9: 构建与 lint 验证

**Files:** 全项目。

### Step 1: 完整编译

Run: `./gradlew :app:assembleDebug --no-daemon`
Expected: BUILD SUCCESSFUL

### Step 2: Lint 检查

Run: `./gradlew :app:lintDebug --no-daemon`
Expected: BUILD SUCCESSFUL；允许既有警告，不引入新错误。

### Step 3: 真机/模拟器验证

- 打开编辑记录页。
- 验证标签 inline 输入、类型图标、分割线、图片圆角、公式滚动、间距节奏、工具栏阴影、底部遮罩。

---

## 依赖关系

- Task 3（分割线去卡片）需要在 Task 2（BlockCard 图标）之后做，避免同时修改 `BlockCard` 冲突。
- Task 6（间距）依赖 Task 3（分割线特殊处理），因为间距逻辑需要识别 divider。
- Task 7 和 Task 8 相互独立，但都依赖 Task 6（因为 `LazyColumn` 的 padding 与间距同时修改）。
- Task 9 必须在所有任务之后。

推荐执行顺序：1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9。
