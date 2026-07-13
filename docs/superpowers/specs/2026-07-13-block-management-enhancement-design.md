# 知识卡片块管理增强设计文档

## 目标

1. 修复「添加知识小点」按钮在旧记录上点击时导致应用闪退的 bug。
2. 在知识卡片编辑态内增强块管理能力：
   - 块间自由插入：在任意两个块之间插入新内容块。
   - 块复制粘贴：支持跨笔记复制单块，内容、格式、属性完整保留。
   - 上下文菜单：长按块弹出操作菜单。
   - 实时保存：所有块操作后自动保存。

## 背景

当前 `NoteEditScreen` 使用 `NoteViewModel` 管理学院内存中的 `_blocks` 和 `state.cards`。部分旧记录来自「卡片制」改造前的数据结构，改造后这些旧记录被自动包裹成单张 `KnowledgeCard`，但数据库层面的 `NoteBlockEntity.cardId` 与 `NoteCardEntity.id` 可能存在不一致，导致点击「添加知识小点」时状态机假设不成立而崩溃。

同时，当前块操作仅支持底部工具栏添加块到末尾、块卡片内的上移/下移/删除，缺少块间任意位置插入、复制粘贴等高频编辑能力。

## 方案概述

采用「完整修复 + 系统剪贴板 + 插入线/上下文菜单」方案：

- **闪退修复**：在 `NoteRepositoryImpl.hydrate()` 中合并 orphan block 到第一张卡片；在 `NoteViewModel.addKnowledgeCard()` 中增加 `currentCardId` 存在性校验和 fallback 逻辑。
- **块间插入**：编辑态每个块上下方显示「在此插入」分割线；同时长按块弹出上下文菜单，支持「在上方插入」「在下方插入」。
- **复制粘贴**：单块复制时序列化为 JSON，以 `zhilu-block:` 前缀文本写入系统剪贴板；粘贴时解析并重新生成 id，支持跨笔记使用。
- **保存策略**：所有块操作后调用 `scheduleSave()`，保持现有 500ms debounce；退出编辑时调用 `saveNow()`。

拖拽重排作为二期功能，本次不实现。

## 详细设计

### 1. 闪退修复

#### 1.1 数据层兜底

在 `NoteRepositoryImpl.hydrate()` 中：

- 如果 `noteCardDao.getByNoteIdOnce(entity.id)` 返回非空卡片列表，但某些 `NoteBlockEntity` 的 `cardId` 不匹配任何卡片 id，则把这些 orphan block 合并到第一张卡片。
- 保证每张卡片至少有一个 block 时走卡片路径；否则 fallback 到 `note.blocks`。

#### 1.2 ViewModel 防御

在 `NoteViewModel.addKnowledgeCard()` 中：

- 调用前检查 `state.cards` 是否包含 `currentCardId`。
- 如果不包含，先创建一张 fallback 卡片，把当前 `_blocks` 归入其中，并更新 `currentCardId` 为该卡片 id，再继续原有逻辑。
- `previousBlocks` 取 `_blocks` 的深拷贝，避免 `replaceBlocks(defaultBlocks())` 污染旧引用。

在 `NoteViewModel.syncBlocksToState()` 中：

- 如果 `state.cards` 为空，调用 `knowledgeCardsFromBlocks(state.title, blocks)` 生成默认卡片，避免空列表进入后续 `flatMap`/`itemsIndexed` 路径。

### 2. 块间插入

#### 2.1 UI 交互

- 编辑态下，每个 `EditableBlock` 上下渲染隐藏的「插入线占位区」。
- 用户点击块间区域时，占位区显示为蓝色分割线并带「+」图标。
- 点击「+」后，底部弹出块类型选择（复用 `KnowledgeBottomToolbar` 的块类型按钮）。
- 选定类型后，`NoteViewModel.insertBlockAt(index, type)` 在指定位置插入默认块。

#### 2.2 ViewModel 方法

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
    // 重新计算后续 block 的 sortOrder
    _blocks.forEachIndexed { i, block ->
        _blocks[i] = block.copy(sortOrder = i)
    }
    syncBlocksToState()
    scheduleSave()
}
```

### 3. 上下文菜单

长按任意可编辑块弹出 `BlockContextMenu`（基于 `DropdownMenu`）：

- 在上方插入
- 在下方插入
- 复制
- 删除（复用现有 undo 机制）
- 上移（复用现有逻辑）
- 下移（复用现有逻辑）

菜单项根据块位置动态启用/禁用：例如第一个块禁用「上移」，最后一个块禁用「下移」。

### 4. 复制粘贴

#### 4.1 序列化格式

自定义 MIME 使用文本标记方案，剪贴板文本格式：

```
zhilu-block:{"version":1,"type":"TEXT","content":"...","language":"","children":[]}
```

JSON 字段：

| 字段 | 说明 |
|------|------|
| version | 协议版本，当前为 1 |
| type | BlockType.value，如 TEXT/CODE/LINK/LATEX/BRANCH/IMAGE/DIVIDER/TODO |
| content | 块内容 |
| language | 代码块语言 |
| children | BRANCH 子块数组，递归结构 |

不序列化：`id`、`noteId`、`cardId`、`sortOrder`、`parentBranchId`。

#### 4.2 BlockClipboardSerializer

新建 `BlockClipboardSerializer`：

- `fun toJson(block: Block): String`
- `fun fromJson(json: String): Block?`
- BRANCH 块递归处理子块。
- IMAGE 块只复制 URI 字符串，不复制图片文件。

#### 4.3 剪贴板管理

新建 `BlockClipboardManager`（注入 `@ApplicationContext`）：

- `copyBlock(block: Block)`：序列化后写入系统剪贴板。
- `hasBlock(): Boolean`：读取剪贴板文本并检测 `zhilu-block:` 前缀。
- `readBlock(): Block?`：读取并反序列化。

#### 4.4 粘贴交互

- 长按卡片空白区域弹出「粘贴」选项。
- 选择后弹出位置菜单：「粘贴到上方」「粘贴到下方」「粘贴到末尾」。
- `NoteViewModel.pasteBlock(targetIndex: Int?)`：
  1. 读取剪贴板并解析 JSON。
  2. 递归为新块及子块分配新 id。
  3. 根据 `targetIndex` 插入到 `_blocks` 指定位置；为 null 时追加到末尾。
  4. `syncBlocksToState()` + `scheduleSave()`。
  5. 自动滚动到新粘贴块。

### 5. 状态保存

- 所有块操作（插入、复制、粘贴、移动、删除）完成后调用 `scheduleSave()`。
- 保持现有 500ms debounce，连续操作只保存最终状态。
- 退出编辑模式时调用 `saveNow()` 强制立即保存。

## 数据模型

不新增实体表，仅新增辅助方法和工具类：

- `Block.copyWithFreshId()`：生成新 id 并清除持久化相关字段。
- `BlockClipboardSerializer`：纯工具类，无状态。
- `BlockClipboardManager`：包装 `ClipboardManager`。

## 错误处理

| 场景 | 处理 |
|------|------|
| 旧记录 cardId 不匹配 | 数据层合并 orphan block；ViewModel 增加 fallback 卡片 |
| 插入索引越界 | clamp 到合法范围 |
| 剪贴板无内容/无前缀/解析失败 | 静默跳过，不弹错误 |
| 粘贴 BRANCH 块 | 递归重建子块 parentBranchId |
| 粘贴 IMAGE 块但原图已删 | 显示占位图 |
| 空 cards 列表 | syncBlocksToState 时生成默认卡片 |

## 测试计划

### 单元测试

- `BlockClipboardSerializerTest`
  - TEXT/CODE/LINK/LATEX/BRANCH/IMAGE 序列化与反序列化。
  - 验证 id/noteId/cardId/sortOrder/parentBranchId 不被序列化。
  - BRANCH 子块递归正确。
- `NoteViewModelBlockOpsTest`
  - 模拟旧记录 cardId 不一致，`addKnowledgeCard()` 不崩溃且状态正确。
  - `insertBlockAt` 在首、中、尾插入后 `_blocks` 顺序正确。
  - 复制并粘贴后新块 id 不同、内容相同、BRANCH 子块 parentBranchId 重新映射。
- `NoteRepositoryImplTest`
  - hydrate 时 orphan block 被合并到第一张卡片。

### 集成/E2E 测试

- 真机/模拟器打开卡片制改造前的旧记录（含链接、公式、文本），进入编辑模式点击「添加知识小点」10 次，无崩溃。
- 块间插入线插入各类块，验证顺序与内容。
- 长按块复制，切换到其他笔记粘贴，验证内容与格式。

## 验收标准

1. 旧记录点击「添加知识小点」不再闪退。
2. 编辑态每个块上下方可出现插入线并成功插入新块。
3. 长按块可显示上下文菜单并执行在上方/下方插入、复制、删除、上移、下移。
4. 单块可复制到系统剪贴板并在其他笔记中粘贴，内容/格式/属性完整。
5. 所有块操作后 500ms 内自动保存，退出编辑时立即保存。
6. `./gradlew :app:testDebugUnitTest --no-daemon` 和 `./gradlew :app:lintDebug --no-daemon` 通过。
