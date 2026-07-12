# 知录笔记编辑页 UI 打磨设计文档

## 概述

对「知录（ZhiLu）」笔记编辑页进行一次视觉与交互的精细化打磨，解决当前标签输入表单化、类型标签抢眼、分割线冗余、块间距拥挤、工具栏贴底、图片圆角冲突、公式截断、底部留白不足等 8 个问题。本次改动不新增业务功能，仅优化现有编辑/阅读体验的视觉呈现与交互细节。

## 设计目标

- 让标签操作更轻量、更符合 inline 编辑直觉。
- 弱化内容块的类型标识，让内容本身成为视觉重心。
- 让分割线回归「分隔符」本质，不再被卡片包裹。
- 通过节奏化的间距与工具栏处理，提升页面层次感。
- 解决图片圆角冲突与公式截断问题。

## 设计范围

- 影响页面：`NoteEditScreen.kt`（编辑态与阅读态共用的大部分组件）。
- 影响组件：`TagPickerInline.kt`、`BlockCard.kt`、`BlockContent.kt`、`DividerBlockView.kt`、`ImageBlockView.kt`、`LatexBlockEditor.kt`、`BlockToolbar.kt`、`EditableBlock.kt`、`ReadOnlyBlock.kt`。
- 不涉及：数据模型、ViewModel 逻辑、导航、后端存储。

## 详细设计

### 1. 标签区：Inline 输入

**目标**：去掉整行的「新建标签」输入框 +「添加」按钮，改为在 chip 行内直接创建。

#### 交互

- 默认状态：已选标签以 chip 横向排列，末尾跟一个「+ 标签」chip。
- 点击「+ 标签」后，在标签行**下方展开一个候选区域**。
  - 区域内第一位置是固定输入框 `[ 新标签___ ]`。
  - 输入框右侧/下方紧接**已有标签候选列表**（可横向滚动或换行）。
  - 点选候选标签即切换选中/取消。
  - 点击候选标签不会让输入框失焦。
- 输入框中按回车（`ImeAction.Done`）：
  - 若输入非空，创建新标签并**自动选中加入当前笔记**。
  - 输入框保持焦点，方便连续创建多个标签。
- 只有点击展开区域外部或按返回键时，才收起输入框并恢复为「+ 标签」chip。

#### 视觉

- 输入框为圆角 capsule，高度 32dp，与 chip 一致。
- 最小宽度 120dp，避免过短。
- 边框：实线，`outlineVariant` alpha 0.38。
- placeholder 文字「新标签」，颜色 `onSurfaceVariant`。
- 输入文字样式 `labelMedium`，颜色 `onSurface`。
- 候选标签 chip 与已选标签视觉一致，未选中状态使用 `surfaceVariant`。

#### 涉及文件

- `app/src/main/java/com/example/zhilu/ui/note/tag/TagPickerInline.kt`（主要改动）
- `app/src/main/java/com/example/zhilu/ui/note/NoteEditScreen.kt`（如需要调整外层 padding）

---

### 2. 内容块

#### 2.1 类型标签改为小图标

**目标**：去掉抢眼的文字类型标签，只读态完全隐藏，编辑态用弱化小图标替代。

- 只读态：`BlockCard` 顶部不显示任何类型标识。
- 编辑态：`BlockCard` 右上角「⋮」菜单左侧显示一个 20dp 的类型图标。
- 图标映射：
  - 文本 📝
  - 代码 💻
  - 图片 📷
  - 链接 🔗
  - 公式 ∑
  - 分割线：不显示图标
  - 待办：本次不加图标
- 图标颜色 `onSurfaceVariant`。

#### 涉及文件

- `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockCard.kt`

#### 2.2 分割线块去卡片化

**目标**：分割线不应被大卡片包裹。

- 阅读态：直接渲染一条 3dp 高的横线，左右 16dp 边距，颜色 `outlineVariant`，cap 为圆角。
- 编辑态：保留 48dp 高的触控区，背景色 `outlineVariant` alpha 0.08，中间显示 3dp 横线。
- 横线不使用左右渐变（避免深色模式下显脏），使用实线 + 圆角 cap。
- 拖拽排序时：横线加粗到 4dp，颜色变为 `primary`。

#### 涉及文件

- `app/src/main/java/com/example/zhilu/ui/note/blocks/DividerBlockView.kt`
- `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockContent.kt`（移除 DIVIDER 的 BlockCard 包裹）
- `app/src/main/java/com/example/zhilu/ui/note/blocks/EditableBlock.kt` / `ReadOnlyBlock.kt`（手势区域处理）

#### 2.3 图片块圆角统一

**目标**：消除图片自身圆角与卡片圆角的冲突。

- 图片块仍放在 `BlockCard` 内，但图片内容顶满卡片内部。
- 移除 `ImageBlockView` 内部给图片单独加的圆角与 clip，让卡片的 16dp 圆角成为唯一边界。
- 图片使用 `ContentScale.Crop` 填满。
- 图片最大高度 400dp，超出时底部加渐变遮罩暗示更多内容。
- 加载中/错误占位也顶满卡片，背景色使用 `surfaceVariant`。

#### 涉及文件

- `app/src/main/java/com/example/zhilu/ui/note/blocks/ImageBlockView.kt`
- `app/src/main/java/com/example/zhilu/ui/note/blocks/BlockCard.kt`

#### 2.4 公式块横向滚动

**目标**：解决长公式被截断的问题。

- 编辑态预览和阅读态公式都用 `HorizontalScroll` 包裹。
- 短公式（渲染宽度小于屏幕宽度）居中显示。
- 长公式（渲染宽度超过屏幕宽度）左对齐并支持横向滚动。
- 如果按渲染宽度测量实现复杂，可先统一左对齐，后续迭代。
- 滚动区域底部预留 8dp 给系统滚动条；如系统不显示滚动条，可加极淡右边缘渐变暗示可滚动。

#### 涉及文件

- `app/src/main/java/com/example/zhilu/ui/note/blocks/LatexBlockEditor.kt`
- `app/src/main/java/com/example/zhilu/ui/note/latex/LatexImage.kt`

---

### 3. 间距与工具栏

#### 3.1 节奏化块间距

- 不同类型块之间：16dp。
- 同类型块之间：8dp + 一条 1dp 分隔线（`outlineVariant` alpha 0.15）。
- 分割线与其相邻块之间：24dp，让分割线成为段落分隔符。

#### 涉及文件

- `app/src/main/java/com/example/zhilu/ui/note/NoteEditScreen.kt`（LazyColumn 间距逻辑）
- `app/src/main/java/com/example/zhilu/ui/note/blocks/EditableBlock.kt`
- `app/src/main/java/com/example/zhilu/ui/note/blocks/ReadOnlyBlock.kt`

#### 3.2 工具栏

- 工具栏贴底（`bottom = 0dp`）。
- `shadowElevation = 4dp`。
- 顶部加 1dp 分隔线，`outlineVariant` alpha 0.3。
- 保持 pill 形状和现有图标布局。
- 背景使用 `surfaceVariant`。

#### 涉及文件

- `app/src/main/java/com/example/zhilu/ui/note/toolbar/BlockToolbar.kt`
- `app/src/main/java/com/example/zhilu/ui/note/NoteEditScreen.kt`（工具栏容器）

---

### 4. 底部留白

#### 4.1 编辑态

- `LazyColumn` 底部 `contentPadding` 设为 0dp（或 8dp 防裁切）。
- 在 `LazyColumn` 外部、工具栏上方叠加一个 40dp 高的渐变遮罩。
- 遮罩使用垂直渐变：`Transparent` → `background`。
- 遮罩不拦截触摸事件，仅作为视觉过渡。

#### 4.2 只读态

- 底部 padding 固定 32dp。
- 不加渐变遮罩。

#### 涉及文件

- `app/src/main/java/com/example/zhilu/ui/note/NoteEditScreen.kt`

## 依赖与限制

- 不引入新依赖。
- 保持 Android API 26+ 兼容。
- 折叠/展开、横向滚动等使用 Jetpack Compose 原生 API。

## 验收标准

1. 标签区：点击「+ 标签」展开候选区域，输入框固定在最前，回车创建并自动选中，点击外部收起。
2. 编辑态块卡片右上角显示类型小图标，只读态不显示。
3. 分割线阅读态为纯横线，编辑态有 48dp 触控区，拖拽时变粗变 primary 色。
4. 图片顶满卡片，最大高度 400dp，圆角统一。
5. 公式长内容可横向滚动，短公式居中（或统一左对齐兜底）。
6. 块间距符合「同类 8dp、异类 16dp、分割线前后 24dp」。
7. 工具栏贴底 + 4dp 阴影 + 顶部分隔线。
8. 编辑态底部有渐变遮罩，只读态底部有 32dp padding。
9. `./gradlew assembleDebug` 构建成功，无新增 lint 错误。
