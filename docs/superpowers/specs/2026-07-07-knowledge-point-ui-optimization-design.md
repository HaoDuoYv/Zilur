# 知识点编辑/详情页 UI 优化设计

## 1. 背景与目标

当前知识点编辑页（`NoteEditScreen.kt`）存在标签说明文字过多、内容块无边界感、底部工具栏拥挤、块内操作入口隐藏深、颜色不统一等问题。本次优化目标是：

- 让编辑态更接近 Notion 的块编辑体验
- 让只读态更接近飞书文档的清爽阅读体验
- 遵循"做减法"原则：能少一个字就少一个字，能用视觉暗示就不用文字解释
- 所有改动仅影响知识点编辑/详情页，不改动全应用主题色

## 2. 设计原则

1. **做减法**：去掉冗余 label、说明文案、分隔线
2. **块级卡片化**：每个内容块用圆角卡片包裹，类型用小灰标标注
3. **高频操作一步直达**：文字/代码/拍照常驻工具栏，代码块语言标签可点击
4. **低频操作两步可接受**：公式、分割线、链接收进悬浮 "+" 菜单
5. **移动端优先**：左滑删除、长按拖拽排序、底部标签面板
6. **局部靛蓝**：只在知识点界面使用 Indigo 主色，其他页面保持绿色主题

## 3. 数据模型变更

给 `Block` 增加 `language: String = ""` 字段，仅对 `BlockType.CODE` 有意义。

变更文件：

- `domain/model/Block.kt`：`Block` 增加 `language`
- `data/local/entity/NoteBlockEntity.kt`：增加 `language` 列
- `data/local/mapper/BlockMapper.kt`：映射 `language`
- `data/local/database/AppDatabase.kt`：`version = 3`
- `data/local/database/Migration.kt`：新增 `Migration(2, 3)`
  - `ALTER TABLE note_blocks ADD COLUMN language TEXT NOT NULL DEFAULT ''`
- 同步更新 `MarkdownExporter`、`JsonExporter` 及对应测试

说明：UI 层不再显式维护 `sortOrder`，列表顺序由 `MutableStateList` 索引决定，持久化时按索引重新生成 `sortOrder`。

## 4. 组件结构

```
ui/note/
├── NoteEditScreen.kt          // 页面布局、状态监听、导航
├── NoteViewModel.kt           // 状态管理与事件流
├── blocks/
│   ├── EditableBlock.kt       // 编辑态卡片外壳：类型小灰标、"⋮"菜单、左滑删除
│   ├── ReadOnlyBlock.kt       // 只读态卡片外壳
│   ├── BlockContent.kt        // 根据 type 分发到具体编辑器/渲染器
│   ├── TextBlockEditor.kt
│   ├── CodeBlockEditor.kt     // 语言 pill + 复制按钮
│   ├── LinkBlockEditor.kt
│   ├── LatexBlockEditor.kt
│   ├── ImageBlockView.kt
│   └── DividerBlockView.kt
├── tag/
│   └── TagPickerInline.kt     // 行内标签网格（<20 个标签时用）
├── bottomsheet/
│   └── TagPickerBottomSheet.kt // 底部标签面板（≥20 个标签时用）
├── toolbar/
│   └── BlockToolbar.kt        // 3 常驻 + 悬浮 "+" 展开菜单
└── theme/
    └── NoteColors.kt          // 知识点界面局部颜色
```

## 5. 编辑态 UI

### 5.1 标题区

- 去掉 "标题" label
- 使用 `BasicTextField`，字号 `headlineMedium`（20sp），颜色 Indigo
- placeholder："输入标题..."
- 标题与标签区用 **16dp 留白** 分隔，**不要分隔线**
- 软键盘右下角显示"下一步"
- 标题输入后按回车/下一步/收起键盘：若内容区为空，自动插入一个文字块并聚焦

### 5.2 标签区

- 只显示已选标签 chip + "+ 标签" 文字按钮
- 删除所有说明文案
- 标签数 < 20：点击 "+ 标签" 后，标签区展开为固定 **120dp** 的网格面板，展示所有候选标签，点选即添加/取消；再次点击 "+ 标签" 或点外部收起
- 标签数 ≥ 20：点击 "+ 标签" 从底部弹出 `TagPickerBottomSheet`
- 两种模式都支持在候选区直接输入新建标签，回车确认

### 5.3 内容块

- 每个块用 `Surface` 圆角卡片（16dp）包裹
- 卡片顶部一行：左侧类型小灰标（背景 `#F1F5F9`，文字 `#64748B`），右侧只保留 "⋮" 更多按钮
- "⋮" 菜单项：编辑、删除、上移、下移、设置语言（代码块）
- 块与块之间 12dp 间距
- **左滑删除**：阈值 25%，滑动过程中实时显示红色删除背景；松手删除并弹出 Snackbar 撤销
- **长按拖拽排序**：长按块顶部类型标签区域触发，块轻微上浮 + 阴影加深，拖拽到目标位置松手插入
- 拖拽开始时暂停自动保存，结束后恢复

### 5.4 代码块

- 卡片右上角显示语言 pill（背景 `#334155`，文字 `#94A3B8`）
- **编辑态**：语言 pill 可点击，点击后弹出语言选择列表
- 编辑态复制按钮常驻右上角
- 支持语言：Plain Text、Kotlin、Java、Python、C++、JavaScript、Go、SQL、Bash、Markdown
- 深色背景 `#1E293B`，浅色文字

### 5.5 底部工具栏

- 3 个常驻图标按钮：**文字、代码、拍照**
- 右侧悬浮 "+" 按钮（52dp），点击展开菜单：**链接、公式、分割线**
- 工具栏有轻微背景模糊/阴影

### 5.6 空状态

- 当没有内容块（或只有默认空文字块）时，页面中央显示按钮："开始记录..."
- 点击后添加文字块并聚焦

## 6. 只读详情态 UI

### 6.1 标题

- 字号同编辑态 `headlineMedium`
- 未输入标题时显示"新建知识"，颜色 `#94A3B8`

### 6.2 标签

- 显示已选标签 chip
- **标签可点击**，点击后跳转到该标签下的知识列表

### 6.3 内容块

- 与编辑态共享卡片外壳样式
- 顶部左侧类型小灰标，**不显示 "⋮" 按钮**
- **长按块弹出菜单**：复制此块、分享此块
- 文字块直接渲染文本
- 代码块深色背景，右上角语言 pill
- 只读态代码块复制按钮**默认隐藏**，点击代码块区域后浮现，2 秒后自动消失
- 分割线只读态加粗到 **3dp** 或颜色加深

### 6.4 顶部操作

- 左侧返回
- 右侧：**分享图标** + **编辑图标**，都用描边风格
- 自动保存提示不展示

### 6.5 空状态

- 文案："暂无内容"
- 显示主色填充的"编辑"按钮

## 7. ViewModel 与状态变更

### 7.1 状态

```kotlin
data class NoteUiState(
    val noteId: Long = 0L,
    val title: String = "",
    val blocks: List<Block> = listOf(Block(type = BlockType.TEXT, content = "", sortOrder = 0)),
    val selectedTags: List<Tag> = emptyList(),
    val availableTags: List<Tag> = emptyList(),
    val isEditing: Boolean = true,
    val isLoading: Boolean = false,
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    val error: String? = null
)

enum class SaveStatus { IDLE, SAVING, SAVED, ERROR }
```

### 7.2 事件流

使用 `Channel<UiEvent>` / `SharedFlow` 处理一次事件：

```kotlin
sealed class UiEvent {
    data class ShowUndoSnackbar(val block: Block, val index: Int) : UiEvent()
    data class ConfirmRemove(val blockId: Long) : UiEvent()
}
```

### 7.3 新增/调整方法

- `moveBlock(fromIndex: Int, toIndex: Int)`：使用 `MutableStateList.move()`，拖拽结束后再保存
- `setBlockLanguage(index: Int, language: String)`：更新对应块语言
- `removeBlock(index: Int)`：触发删除事件，UI 立刻移除并展示撤销 Snackbar
- `undoRemoveBlock()` / `confirmRemoveBlock()`：恢复或真正删除
- 所有保存统一走 `debounce(1000ms)`
- 自动保存图标状态：`SAVING` 旋转，`SAVED` 对勾（2 秒后回 IDLE），`ERROR` 叉 + Snackbar

## 8. 交互细节

| 交互 | 行为 |
| --- | --- |
| 左滑删除 | 阈值 25%，实时红色背景，删除后 Snackbar 撤销，点外部或撤销按钮恢复，3 秒超时真正删除 |
| 长按拖拽 | 长按类型标签区域触发，块上浮 + 阴影，拖拽结束更新顺序 |
| 标签选择 | <20 行内网格展开，≥20 底部面板，都支持新建标签 |
| 代码块语言 | 编辑态点击语言 pill 弹出选择 |
| 复制按钮 | 编辑态常驻；只读态点击代码块浮现，2 秒后消失；图标瞬间变"✓"反馈 |
| 标题回车 | 内容为空时自动创建文字块并聚焦 |
| 自动保存 | debounce 1s，顶部只显示图标 |

## 9. 配色方案（局部）

- **主色 Indigo**：`#4F46E5`
- **主色浅色**：`#6366F1`
- **主色深色**：`#4338CA`
- **标题 placeholder/空状态**：`#94A3B8`
- **类型小灰标背景**：`#F1F5F9`
- **类型小灰标文字**：`#64748B`
- **代码块背景**：`#1E293B`
- **代码块语言 pill 背景**：`#334155`
- **代码块语言 pill 文字**：`#94A3B8`
- **卡片描边**：`#E2E8F0` 或无边框靠阴影
- **删除背景**：`#FEE2E2`
- **删除图标**：`#EF4444`

实现：在 `ui/note/theme/NoteColors.kt` 定义，通过 `CompositionLocalProvider` 局部注入，不影响全局主题。

## 10. 技术约束

1. **Material3 版本**：升级到 **1.3.0+**，避免 `SwipeToDismissBox` alpha API 破坏性变更
2. **拖拽排序实现**：先确认 Kotlin 版本
   - Kotlin < 1.9：使用 `Modifier.pointerInput` 自定义长按拖拽
   - Kotlin ≥ 1.9：可考虑 `foundation:1.7.0` 的 drag-and-drop API
3. **数据库迁移**：
   - Room version 升到 3
   - 加 `androidx.room:room-testing`
   - `3.json` 必须提交版本控制
4. **动画 key**：`LazyColumn` 使用 `itemsIndexed(..., key = { it.id })`
5. **输入法检测**：键盘收起时加 `FocusManager` 焦点校验，避免折叠屏误判
6. **代码块主题**：用 `CompositionLocalProvider` 局部覆盖，不污染全局

## 11. 实现顺序

1. 数据层变更（Block + Entity + Mapper + Migration）
2. 立即跑 migration test
3. 左滑删除骨架 + 新块组件结构（可并行）
4. 颜色/主题局部覆盖
5. 标题区、标签区改造
6. 底部工具栏 + 空状态
7. 拖拽排序
8. 代码块增强（language + 复制）
9. 自动保存提示
10. 只读态同步改造
11. 测试补全

## 12. 测试计划

### P0（阻塞发布）

- `NoteViewModel` 单元测试
  - debounce：快速连续输入只触发一次保存
  - 删除 undo：3 秒超时后 undo 无效、旋转屏幕后 undo 有效
  - 移动块：从 0 移到末尾、从末尾移到 0、只有 1 个块时无操作
- 数据库迁移测试：旧数据顺序不变、外键约束、`language` 列默认空字符串
- UI 测试：左滑删除、拖拽排序

### P1（尽量做）

- 标题回车/下一步焦点链
- 键盘收起自动创建块
- 代码块复制按钮浮现/消失
- 标签网格展开/收起动画
- 无障碍语义标签

### P2（有余力）

- 100 块笔记打开性能（< 300ms）
- 大量代码块滚动帧率
- 视觉回归交互态

## 13. 边界与后续迭代

- 本次不做富文本、协同编辑、块内嵌套
- 代码块语法高亮渲染（彩色关键字）本次不做，只加语言标识
- 图片块裁剪/压缩本次保持现状
- 后续可考虑：语音输入块、模板块、块级评论

## 14. 参考

- Notion 块编辑体验
- 飞书文档简洁阅读感
- Material3 `SwipeToDismissBox` 官方文档
- Jetpack Compose `LazyColumn` 拖拽排序社区方案
