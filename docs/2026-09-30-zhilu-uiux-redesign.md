# 知录（ZhiLu）UI/UX 重设计方案

> 版本：v1.0
> 日期：2026-09-30
> 性质：视觉与交互层重构方案（**不动** ViewModel / Domain / Data / 数据库）
> 前置文档：`2026-07-06-zhilu-design.md`（v1.0 冻结设计稿）

---

> **实施状态**：✅ 本方案 P0–P5 已全部落地（2026-09-30），全量验收通过（`testDebugUnitTest` → `lintDebug` → `assembleDebug`）。详见 `CHANGELOG.md` 的 2026-09-30 条目。

## 0. 结论摘要（TL;DR）

知录已经演化成一个远超 MVP 的成熟产品：数据层 v4（多知识卡片 KnowledgeCard、BRANCH 分支块、TODO、复习/提醒、LaTeX、代码块、回收站、`.dtk` 导入导出），视觉层也从最初设计稿的「紫色 Material3」自然演化为「**暖纸 + 墨色灰蓝 + 衬线标题**」的编辑风。

这个编辑风是**差异化资产，应当保留并强化，而不是推翻**。本次重设计做三件事：

1. **收敛一致性**——修复已发现的 11 类硬编码/不一致问题（颜色、透明度、圆角、阴影、动效时长、按钮形状、重复组件、暗色失效等）。
2. **深化动效**——把 `Motion.kt` 从「几个常量」升级为一套可复用的动效系统（时长/缓动/弹簧/错峰），让交互从「能用」变成「有手感」。
3. **优化信息架构**——重点重排首页（统计卡太占位、搜索不可发现）、编辑器（工具栏与块插入的可见性）、标签/提醒/回收站等页面的层级。

**兼容性承诺（详见 §8）**：本次只改 `ui/` 层的表现代码。所有 `ViewModel` 方法签名、`UiState` 契约、`Repository` 接口、Domain 模型、Room schema（v4）、导航路由（`Destination.path`）**保持不变**。任何一处视觉改动都可独立回滚，不牵连业务逻辑。

---

## 1. 现状审计（诊断结论）

### 1.1 现有视觉体系（已确认）

| 维度 | 现状 |
|---|---|
| 背景 | 暖纸 `#FAF8F3`（暗色 `#1A1A1A`） |
| 主色 | 墨蓝 `#3D4F6B`，主容器 `#D8DFEA` |
| 辅助色 | 赭 `#6B5B4A`、橄榄 `#5A6650` |
| 标题字体 | 衬线 Serif（编辑感） |
| 正文字体 | 无衬线 SansSerif |
| 标签色板 | `TagColors` 7 色低饱和编辑风 |
| 卡片 | `AppCard`：12dp 圆角 + 1dp 描边 + 0.5dp 阴影 |
| 动效 | `Motion.kt`：150/300/500ms + 3 条缓动 |

### 1.2 已具备的动效（质量参差）

| 位置 | 现状 |
|---|---|
| 导航转场 | 底部 Tab 淡入淡出；编辑页横向滑入 + 纵向滑出（用 token）✅ |
| 列表入场 | `AnimatedListItem` 错峰淡入上滑（40ms 错峰）✅ 规范 |
| 按压反馈 | 首页/卡片图标按压缩放 0.86~0.92（用 token）✅ |
| 焦点动画 | `KnowledgeCardItem` 焦点描边/阴影，**硬编码 200ms** ⚠️ |
| 保存状态 | `SaveStatusIndicator` 旋转圈/对勾缩放（150/200ms）✅ |
| FAB 菜单 | `HomeFabMenu` 淡入淡出 + **加号瞬时旋转（无补间）** ⚠️ |
| 分支展开 | `BranchBlockContent` 箭头旋转，**未指定时长（走默认弹簧）** ⚠️ |
| 块插入指示 | `BlockInsertIndicator` 默认 `AnimatedVisibility`（未走 token）⚠️ |

### 1.3 一致性问题清单（需在本次修复）

| # | 问题 | 位置 |
|---|---|---|
| A1 | 高亮色硬编码亮蓝 `#1D4ED8`，与墨蓝主色冲突 | `NoteListItem.kt` |
| A2 | 工具栏 `Color.White` 硬编码，**暗色模式失效** | `KnowledgeBottomToolbar.kt` |
| A3 | `NoteColors.kt` 整套 indigo 色（`#4F46E5`）为**死代码**，与主色冲突 | `note/theme/NoteColors.kt` |
| A4 | 全屏预览黑/白硬编码（可接受，但需主题化） | `ImageViewer.kt` |
| B | 透明度值散落（0.08/0.12/0.15/0.25/0.38/0.5/0.55/0.6/0.7/0.85），无统一 token | 多处 |
| C | 圆角来源不统一：8dp 私有常量 vs `ShapeTokens.Small(8dp)` | `BranchBlockContent`、`DividerBlockView` |
| D | 动效时长散落：200/180ms 硬编码、默认动画 | `KnowledgeCardItem`、`BlockToolbar` 等 |
| E | 阴影层级不统一：0/0.5/4/6/8dp 混用 | 多处 |
| F | 分隔线粗细 3 套：0.5/1/3/4dp | 多处 |
| G | 按钮形状不统一：空态胶囊 vs 其余矩形 | `AppEmptyState`、`ReviewPanel` |
| H | 组件重复：两个 `TagChip`、`HomeFabMenu` 未复用 `AppFAB`、图片来源下拉两处、`EmptyState`(home) 冗余 | 多处 |
| I | 删除操作视觉不一致：上下文菜单删除项未标红 | `BlockContextMenu` |
| J | 错误态缺失：`ImageViewer.loadError` 死状态、Todo 创建失败静默 | `ImageViewer`、`TodoBlock` |
| K | 标题色 token 语义不统一：`onBackground` vs `onSurface` | `AppTopBar` vs 卡片 |

---

## 2. 设计方向与视觉风格

### 2.1 设计关键词

**纸墨 · 静谧 · 内容优先 · 轻盈**

「知录」是记录与整理知识的工具，气质应当是安静的、可信赖的、让内容说话的。动效是「锦上添花」而非「主角」——关掉所有动效，页面依然要能立得住（静帧即半成品）。

### 2.2 视觉方向：在「编辑风」基础上收敛

| 维度 | 决定 |
|---|---|
| 主色 | 保留墨蓝 `#3D4F6B` 作为「墨色」，**不再引入第二套 accent**；所有强调（高亮、焦点、选中）统一由主色/三级色派生 |
| 背景 | 暖纸 `#FAF8F3` 保留，作为品牌的温度来源 |
| 标题字体 | 保留衬线，强化「纸面印刷」感；正文无衬线保证长文可读 |
| 标签色板 | 统一用 `TagColors`（7 色低饱和），删除 `NoteColors` 死代码 |
| 语义色 | 新增「高亮 Highlight」「遮罩 Scrim」等语义 token，替代散落的 `Color(0x…)` |
| 暗色 | 全量走主题 token，修复工具栏白底失效 |

### 2.3 与 motion-web 设计原则的对齐

本方案借用 motion-web 的三条核心纪律，映射到 Compose：

1. **静帧即半成品**——每个页面先过「无动效静帧」审校，再谈动效。
2. **交互动效 = 输入产生动效**——手势驱动的弹簧反馈优先于自动播放动画；不做「屏幕保护程序」式的无意义自动运动。
3. **能点 ≠ 看得出能点**——所有非标准按钮的交互（块插入、拖拽、滑动删除、FAB 展开）都要有「邀请」提示（视觉 affordance），否则用户发现不了。

---

## 3. 设计系统升级（Token 层）

> 本节的 token 变更**全部向后兼容**：只新增/重命名内部值，不改动任何已消费 token 的语义（`primary` 仍是 `primary`）。

### 3.1 颜色（`Color.kt`）

**保留现有 scheme，新增语义 token：**

```kotlin
// 新增：语义色（替代散落硬编码）
object SemanticColors {
    val Highlight = Color(0xFF3D4F6B)   // 搜索高亮，替代 #1D4ED8
    val HighlightBg = Color(0x1A3D4F6B) // 高亮背景（10% 墨蓝）
}
```

**透明度分层（新增，替代散落的 alpha）：**

```kotlin
object AlphaTokens {
    const val Subtle = 0.08f     // 分隔底
    const val Hover = 0.12f      // 选中底
    const val Divider = 0.15f    // 细分割线
    const val Disabled = 0.38f   // 禁用
    const val Border = 0.5f      // 常规描边
    const val Overlay = 0.55f    // 分支背景
}
```

**删除/替换：**
- 删除 `NoteColors.kt`（死代码，且与主色冲突）。
- `NoteListItem` 高亮 `#1D4ED8` → `SemanticColors.Highlight`。
- `KnowledgeBottomToolbar` `Color.White` → `MaterialTheme.colorScheme.surface`。

### 3.2 形状（`Shape.kt`）

统一圆角来源，新增胶囊常量：

```kotlin
object ShapeTokens {
    val ExtraSmall = 4.dp
    val Small = 8.dp       // 小卡/代码块
    val Medium = 12.dp     // 卡片
    val Large = 16.dp      // FAB/大按钮
    val ExtraLarge = 24.dp // 大圆角容器
    val Pill = 50          // 胶囊（替代散落的 CircleShape/RoundedCornerShape(percent=50)）
}
```

**替换：**
- `BranchBlockContent.BranchCornerRadius=8.dp` → `ShapeTokens.Small`。
- `DividerBlockView` 编辑态 `RoundedCornerShape(8.dp)` → `ShapeTokens.Small`。
- `AppEmptyState`/`TagChip` 的 `CircleShape` → `RoundedCornerShape(ShapeTokens.Pill)`。

### 3.3 动效系统（`Motion.kt` 扩展）

```kotlin
object MotionDuration {
    const val Quick = 100        // 按压/微反馈
    const val Short = 150        // 保存状态、icon 反馈
    const val Medium = 300       // 卡片入场、焦点
    const val Long = 500         // 页面转场
    const val Emphasized = 600   // 强调型（FAB 展开）
}

object MotionEasing {
    val Standard = FastOutSlowInEasing                    // 常规
    val EmphasizedDecelerate = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)  // 入场减速
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)  // 离场加速
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)   // Material 标准
}

object MotionSpring {           // 手势跟手用弹簧
    val Snappy = Spring(stiffness = 500f, dampingRatio = 0.6f)  // 拖拽缩放
    val Bouncy = Spring(stiffness = 300f, dampingRatio = 0.5f)  // FAB 展开/回弹
}
```

**统一替换散落的硬编码时长**：焦点 200ms、工具栏 180ms → `MotionDuration.Short/Medium`；`HomeFabMenu` 加号旋转补上补间。

### 3.4 阴影（Elevation）

统一为 4 级，写入 `AppCardStyle`：

```kotlin
object ElevationTokens {
    val Flat = 0.dp          // 平铺（标签、块）
    val Card = 0.5.dp        // 卡片常态
    val Raised = 3.dp        // 悬浮/聚焦
    val Floating = 6.dp      // FAB、拖拽拾起
}
```

### 3.5 间距

沿用现有 4/8/12/16/24 体系，新增 `space-2 = 2.dp`（元信息间距）。不再新增层级，避免碎片化。

---

## 4. 页面级布局重设计

### 4.1 首页 HomeScreen（重点）

**现状问题**：三张统计卡（知识/标签/图片）每次启动都占满首屏上 1/3，信息价值低；搜索藏在右上角图标（去探索页），不可发现；「列表/时间线」切换是右侧小胶囊，弱 affordance。

**重设计**：

```
┌──────────────────────────────┐
│  知录               🔔 ✏️     │ ← TopBar（铃铛带逾期角标）
│  ┌────────────────────────┐  │
│  │ 🔍 搜索知识、标签…        │  │ ← 醒目搜索条（点击→探索页聚焦搜索框）
│  └────────────────────────┘  │
│  最近编辑            [列表|时间线] │ ← 分组标题 + 分段切换（居中）
├──────────────────────────────┤
│  ▍ 微积分基本定理             │  │ ← 卡片：左侧标签色条
│  ▍ 牛顿-莱布尼茨公式…   ⭐  🗑 │  │
│  ▍ [数学] [高数]   图2 · 昨天 │  │
│  …                            │
├──────────────────────────────┤
│    🏠   🏷️   🧭   ⚙️          │
└──────────────────────────────┘
              ✏️（FAB 速度拨号：新建/拍照/导入图片/导入.dtk）
```

**关键改动**：

1. **搜索条置顶**：占位「搜索知识、标签…」，点击 `navigate(Explore)` 并携带「自动聚焦」意图（`savedStateHandle` 传 `focus=true`）。不破坏现有 Explore 逻辑。
2. **统计卡下沉**：从首屏常驻改为——① 首次启动/空态时展示为「欢迎统计条」；② 有数据后收成一行细字「126 知识 · 18 标签 · 352 图片」，随列表一起滚动。数据仍来自 `noteCount/tagCount/mediaCount`，`HomeViewModel` 不变。
3. **模式切换**：从右侧小胶囊改为「最近编辑」标题旁的居中分段控件（`SegmentedButton`），`viewMode` 状态不变。
4. **FAB 速度拨号**：展开加号旋转 45°（补补间）、菜单项错峰上浮、点空白收拢。新增「拍照」「导入图片」两项直通（拍照走 `camera` 路由 + 回传，导入图片走 `.dtk` 之外的图片导入能力，若暂无则本期仅保留「新建 / 导入.dtk」两项）。
5. **卡片滑动手势**：`NoteCard` 增加 `SwipeToDismiss`（左滑删除→软删除，右滑收藏），作为图标按钮的补充，**二者调同一个 ViewModel 方法**。

### 4.2 笔记编辑页 NoteEditScreen（核心，改动最大但风险可控）

**现状**：功能极强（多卡片 + BRANCH + TODO + 复习 + LaTeX/代码/链接/图片），但「查看即编辑」双态 + 活动卡焦点 + 底部工具栏 + 块插入指示的认知负担高。

**重设计（结构不变，只改视觉与交互清晰度）**：

```
┌──────────────────────────────┐
│ ←  数学·已保存        🏷️ 💾 ⋮ │ ← 保存状态 + 显式保存/分享/编辑
├──────────────────────────────┤
│  微积分基本定理               │ ← 标题（衬线，编辑态可输入）
│  [数学][高数]  +标签          │
├──────────────────────────────┤
│ ┌ 卡片1（聚焦态：2dp 墨蓝描边）┐ │
│ │  ┃ 文字块…                 │ │
│ │  ── 插入指示（细线+号，常显）──│ │ ← 常显而非仅悬停
│ │  ┃ 📷 公式图                │ │
│ │  ┌ BRANCH 分支 ▾ ┐          │ │
│ │  └───────────────┘          │ │
│ └────────────────────────────┘ │
│  ┌ 卡片2（未聚焦）┐             │
│  └───────────────┘             │
│  [+ 添加知识卡片]              │
├──────────────────────────────┤
│  文 图 公式 代码 链 分支 待办  │ ← 统一工具栏（含标签，图标带文字）
└──────────────────────────────┘
```

**关键改动**：

1. **工具栏统一**：`KnowledgeBottomToolbar` 与 `BlockToolbar` 的「图片来源下拉」合并为一处；块类型补齐（TEXT/IMAGE/LATEX/CODE/LINK/BRANCH/DIVIDER/TODO），图标 + 文字标签，禁用态走 `AlphaTokens.Disabled`。
2. **块插入指示常显**：`BlockInsertIndicator` 由「悬停出现」改为「编辑态常显淡线」，点击弹出 `BlockTypePickerSheet`（补齐 IMAGE/DIVIDER/TODO）。可发现性直接解决。
3. **拖拽拾起反馈**：拖拽中块用 `ElevationTokens.Floating` 阴影 + 1.02 缩放（弹簧），落地回弹。
4. **撤销/删除**：保留现有「删除→Snackbar 撤销」机制（不动 `NoteViewModel` 的 `pendingRemovals` 逻辑）；仅把滑动删除的背景色与图标统一。
5. **只读/编辑切换**：`查看`态工具栏收为「编辑 / 分享」，点击编辑平滑切换（`AnimatedContent`）。`startEditing`/`saveNow` 逻辑不变。
6. **复习面板**：`ReviewPanel` 按钮统一为胶囊，`isRecording` 增加进度反馈（按钮内旋转圈）。

### 4.3 探索页 ExploreScreen

- 搜索框：聚焦态加 2dp 主色描边 + 轻微上浮阴影；`focus` 意图自动聚焦（配合 §4.1）。
- 最近搜索：每条加「清除 ×」，标题行右侧加「清空全部」。
- 标签：改为 `TagChip` 网格（4 列），而非平铺 AssistChip。
- 结果：保留 `highlightQuery` 高亮，但高亮色改 `SemanticColors.Highlight`。

### 4.4 标签页 TagsScreen

- `TagRow` 精简：删掉行内「筛选」按钮（点击整行即筛选），删除改为行尾溢出菜单（`⋮` → 编辑/删除），减少行内按钮拥挤。
- 选中标签：顶部加一条可关闭的筛选横幅「正在筛选：数学 ×」。
- 颜色点：与 `TagColors` 强绑定，选色走固定 7 色弹窗（复用编辑页 `createTag` 的色板）。

### 4.5 设置页 SettingsScreen

- 主题模式：`FilterChip` ×3 → `SegmentedButton` 三段式。
- 分组卡片加图标（外观/数据/提醒/回收站），行内统一 `SettingRow`。
- 导出/导入按钮：从两个 `OutlinedButton` 并排 → 独立可点行（带图标 + 描述），更清晰。

### 4.6 提醒中心 ReminderCenterScreen

- 三个分组改为顶部 `SegmentedButton`（待处理/已逾期/已完成）或保留分节但加**可折叠**；本期建议保留分节、优化视觉层级。
- 已逾期项：加左侧红色竖条 + 更强的 `errorContainer`，让「逾期」一眼可辨。
- 状态胶囊统一尺寸，补齐图标（待办/复习类型图标）。

### 4.7 回收站 TrashScreen

- 卡片加「剩余 X 天」倒计时提示（`deletedAt` 已存在，30 天规则）。
- 左滑恢复、右滑永久删除（补充现有按钮）。
- 清空按钮加二次确认（现有点击即清空，需补确认对话框）。

### 4.8 相机 CameraScreen

- 快门/切换/返回图标统一为 48dp 触控目标，快门按压有弹簧缩放反馈。
- 拍完回传的「capturedImageUri」链路不变。

---

## 5. 交互设计（动效编排）

### 5.1 全局动效原则

| 原则 | 落地 |
|---|---|
| 手势优先 | 弹簧 > 补间；跟手的东西用 `MotionSpring` |
| 静帧立得住 | 所有动效可被 `prefers-reduced-motion` 关停，静帧仍完整 |
| 错峰 | 列表/菜单入场用 40ms 错峰（已有，保持） |
| 一致性 | 所有时长/缓动走 `Motion.kt`，禁止再散落字面量 |

### 5.2 关键交互规格

| 交互 | 触发 | 规格 |
|---|---|---|
| 列表入场 | 进入页面 | 错峰 40ms，淡入 + 上滑 `it/5`，300ms，EaseOutCubic（沿用） |
| 卡片按压 | 按下 | 缩放 0.97，弹簧 Snappy，100ms |
| 卡片滑动 | 横滑 | SwipeToDismiss，删除背景 `errorContainer`，阈值 0.5 宽度 |
| 搜索高亮 | 命中 | 背景 `HighlightBg` + 文字加粗（替换亮蓝） |
| FAB 展开 | 点击 | 加号旋转 45°（150ms EaseInOutCubic），菜单项错峰上浮 + 回弹弹簧 |
| 焦点切换 | 选中卡片 | 描边 1→2dp、阴影 0.5→6dp，200ms Standard（统一 token） |
| 块拖拽 | 长按拖动 | 拾起阴影 6dp + 缩放 1.02 弹簧，落地回弹 |
| 块插入指示 | 编辑态常显 | 细线 + 加号圆点，hover/接近时加号放大 1.2 |
| 分支展开 | 点箭头 | 箭头旋转 90°，子块 `AnimatedVisibility` 展开（走 token） |
| 保存状态 | 自动保存 | 圈 → 对勾缩放弹入（沿用），ERROR 加轻微抖动 |
| 页面转场 | 导航 | 底部 Tab 淡入淡出；编辑页横滑入/纵滑出（沿用，走 token） |

### 5.3 无障碍与降级

- `Accessibility.isReduceMotionEnabled` 为 true 时：关闭错峰、弹簧、拖拽缩放；列表直接显示，转场用 150ms 淡入。
- 所有图标按钮补 `contentDescription`；触控目标 ≥ 48dp（现有部分 40dp 需上调）。
- 高亮、焦点、逾期等状态**不只用颜色**，同时用描边/图标/形状区分（色盲友好）。

---

## 6. 组件级规范（本次新建/合并）

| 组件 | 动作 | 说明 |
|---|---|---|
| `AppCard` | 微调 | 内部 16dp padding 与调用方双重 padding 问题：明确「`AppCard` 自带内边距，调用方不再加」 |
| `NoteListItem` | 修复 | 去双重 padding、`clickable` 改 `AppCard.onClick`、高亮色换语义 token |
| `TagChip` | 合并 | 删除 `ui/tag/TagChip.kt` 冗余包装，全站用 `ui/component/TagChip.kt` |
| `HomeFabMenu` | 重构 | 复用 `AppFAB`，补旋转补间 + 错峰 |
| `AppEmptyState` | 统一 | 图标改 `ImageVector`（可访问性），按钮胶囊风格与全站一致 |
| `AppTopBar` | 修复 | 标题色统一 `onSurface`，加滚动阴影渐变（可选） |
| `KnowledgeBottomToolbar` | 修复 | 白底→`surface`，合并图片下拉，补齐块类型 |
| `ImageViewer` | 修复 | `loadError` 渲染错误态，转场加淡入淡出 |

---

## 7. 信息架构不变项（导航）

**本次不动导航图**。`Destination` 路由、底部 4 Tab、编辑页参数 `note/{noteId}` 全部保持不变。新增的「拍照 / 导入图片」FAB 入口复用现有 `camera` 路由与 `capturedImageUri` 回传链路，不新增路由。

---

## 8. 兼容性方案（如何不破坏现有逻辑）

### 8.1 分层改动边界

```
┌─────────────────────────────── 本次修改范围 ───────────────────────────────┐
│ ui/theme/*      Color/Shape/Type/Motion/Theme —— 只增不删语义，向后兼容      │
│ ui/component/*  表现组件 —— 修复视觉 + 合并重复，签名向后兼容                 │
│ ui/{home,note,explore,tag,settings,reminder,trash}/*  页面 Composable       │
│                 只改布局/样式/动效，不改 state 读写                          │
└─────────────────────────────────────────────────────────────────────────────┘
┌─────────────────────────────── 绝对不动 ────────────────────────────────────┐
│ ViewModel（HomeViewModel / NoteViewModel / 其余）—— 方法签名与行为不变        │
│ UiState data class —— 字段契约不变                                           │
│ domain/model、domain/repository、domain/usecase —— 不变                      │
│ data/（Room DAO/Entity/Mapper/RepositoryImpl）—— 不变                       │
│ AppDatabase v4 + Migration —— 不变（禁止 fallbackToDestructiveMigration）     │
│ export/、reminder/、di/ —— 不变                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 8.2 关键兼容性约束（逐条）

1. **`NoteViewModel` 的块 ID 映射不可触碰**：保存时 `_blocks` 递减负 id → 真实 id 的回写、`cardId`/`parentBranchId` 重排、`hydrate()` 孤儿块规则——全部在 ViewModel/Repository 内，本次渲染层改动**只消费 `state.cards` / `state.blocks`**，不反向修改。
2. **`KnowledgeCardItem` 参数不变**：虽参数多（~25 个回调），但为保持 ViewModel 契约，本次**不删减参数**，只在组件内部改样式；参数多的问题记入技术债，后续单独重构。
3. **状态流契约不变**：所有页面仍 `StateFlow + collectAsState()`，新增 UI 状态（如搜索自动聚焦 `focus`）用 `savedStateHandle` 一次性传递，不改 `UiState` 字段。
4. **软删除/回收站 30 天逻辑不变**：`deletedAt`、`softDeleteNote` 逻辑不动，回收站的「剩余天数」提示只是**读 `deletedAt` 展示**，不写库。
5. **提醒/复习/待办链路不变**：`ReminderRepository`、`ReviewRepository`、`TodoRepository` 与 `ReminderCheckWorker` 全不动；提醒中心只是视觉层级调整。
6. **主题 token 向后兼容**：`primary`/`background`/`surface` 等既有 token 语义不变，只新增 `SemanticColors`/`AlphaTokens`/`ElevationTokens`；旧代码不报错。
7. **数据库 schema v4 不变**：本次零数据层改动，无 Migration，`MigrationTest` 不受影响。

### 8.3 回滚策略

- 每个视觉改动都是**独立的、可编译的提交**（`style:` / `refactor:`），不混合功能改动。
- 若某页面视觉改动引发回归，单独 revert 该页面 commit，不牵连其他。
- 验收命令沿用项目惯例：`testDebugUnitTest` → `lintDebug` → `assembleDebug`（零 lint 错误）。

---

## 9. 分阶段落地路线

| 阶段 | 内容 | 产出 | 风险 |
|---|---|---|---|
| **P0 设计系统** | 颜色语义化 + Alpha/Elevation token + 删除 `NoteColors` 死代码 + 修复暗色 | `Color.kt`/`Shape.kt`/`Motion.kt` 升级，零页面变化 | 低 |
| **P1 组件修复** | 合并 `TagChip`、重构 `HomeFabMenu`、统一 `AppCard`/`NoteListItem`/`AppEmptyState`/`AppTopBar`、修复 `ImageViewer` | 组件层一致 | 低 |
| **P2 首页重构** | 搜索条置顶、统计条下沉、分段切换、FAB 速度拨号、卡片滑动 | `HomeScreen` | 中 |
| **P3 编辑器重构** | 工具栏统一、插入指示常显、拖拽反馈、只读/编辑切换、复习面板 | `NoteEditScreen` + blocks | 中 |
| **P4 其余页面** | 探索/标签/设置/提醒/回收站视觉层级 | 各 Screen | 低 |
| **P5 动效增强** | 全量对齐 `Motion.kt`、reduced-motion 降级、触控目标 ≥48dp | 全局 | 低 |

每阶段结束满足 Definition of Done：能编译、无 lint 错误、单测通过、视觉自审（静帧 + 动效）通过。

---

## 10. 验收清单

- [ ] 暗色模式下工具栏/卡片/输入框无「白块」失效
- [ ] 全站无硬编码颜色（`grep "Color(0x"` 仅剩有意保留的沉浸预览黑白）
- [ ] 搜索高亮色统一为墨蓝语义色
- [ ] 所有动效时长/缓动来自 `Motion.kt`
- [ ] 块插入指示在编辑态常显，用户无需教程即可发现
- [ ] FAB 展开有旋转补间 + 菜单错峰
- [ ] 拖拽块有拾起/落地反馈
- [ ] `prefers-reduced-motion` 下降级后页面仍完整可用
- [ ] 触控目标 ≥48dp，图标有 contentDescription
- [ ] `testDebugUnitTest` / `lintDebug` / `assembleDebug` 全绿
- [ ] `MigrationTest`（androidTest）不受影响（本方案零数据层改动）

---

## 附录：关键文件改动索引

| 文件 | 改动 |
|---|---|
| `ui/theme/Color.kt` | 新增 `SemanticColors`/`AlphaTokens`/`ElevationTokens` |
| `ui/theme/Shape.kt` | 新增 `ShapeTokens.Pill` |
| `ui/theme/Motion.kt` | 新增 `MotionSpring`、补缓动、时长分级 |
| `ui/note/theme/NoteColors.kt` | 删除（死代码） |
| `ui/component/NoteListItem.kt` | 高亮色、去双重 padding、`onClick` |
| `ui/component/AppCard.kt` | 明确内边距契约 |
| `ui/component/AppEmptyState.kt` | 图标 ImageVector、按钮胶囊 |
| `ui/component/AppTopBar.kt` | 标题色统一 |
| `ui/component/ImageViewer.kt` | 错误态、转场 |
| `ui/tag/TagChip.kt` | 删除冗余包装 |
| `ui/home/HomeFabMenu.kt` | 复用 AppFAB、旋转补间、错峰 |
| `ui/home/HomeScreen.kt` | 搜索条、统计下沉、分段切换、滑动 |
| `ui/note/toolbar/KnowledgeBottomToolbar.kt` | 白底修复、合并图片下拉、补齐类型 |
| `ui/note/blocks/*` | 圆角/透明度/时长 token 化、插入指示常显、拖拽反馈 |
| `ui/note/ReviewPanel.kt` | 胶囊按钮、isRecording 反馈 |

**文档结束**
