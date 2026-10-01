# 知识点 / 标签列表：降密与滑动防误触设计

> 状态：已实现并实机验证
> 实现分支：`codex/zhilu-mvp`
> 范围：首页笔记列表（列表视图 / 时间线视图）、标签索引、回收站三处「文档行」
> 相关文件：`ui/home/*`、`ui/tag/*`、`ui/trash/*`、`ui/component/{DocumentRow,NoteRow,SwipeRevealRow}.kt`、`ui/theme/Spacing.kt`

---

## 一、问题诊断

### 1.1 紧凑感的来源（不是「行高太小」，而是「节奏缺失」）

一行「文档行」的内容高度构成（列表视图）：

| 段落 | 字阶 | 行高 | 与上一段间距 |
|---|---|---|---|
| 标题 | `rowTitle` 16sp | 22 | — |
| 摘要（最多 2 行） | `bodySmall` 14sp | 22 ×2 | **2** |
| 标签行 | `label` 12sp | 16 | **6** |
| 元信息行 | `meta` 12sp | 18 | **6** |

内容高约 114dp，加上下 padding 各 12dp，单行约 **138dp**。

真正的三个问题：

1. **行内层级被压平**。标题↔摘要只有 2dp，摘要↔标签 6dp，标签↔元信息 6dp —— 四个层级里后三层几乎等距，读者无法靠留白判断「哪几行是一组」。尤其标签行（12sp）与元信息行（12sp）在 6dp 间距下融成一片噪点。
2. **信息层级倒挂**。标签是「分类身份」，却用了全行最小的字号（12sp / letterSpacing 0.6），比摘要还弱；而标签页里标签名又是 16sp。同一个概念在两级页面里权重不一致。
3. **行与行之间没有垂直留白**。仅 0.5dp 发丝线，上一行元信息底边到下一行标题顶边只隔 12 + 0.5 + 12 = **24.5dp**，视觉上是一张密排表格。

### 1.2 误触的机制根因

现状实现是 `SwipeToDismissBox` + `rememberSwipeToDismissBoxState`：

```kotlin
confirmValueChange = { value ->
    when (value) {
        StartToEnd -> { onToggleFavorite(); false }   // 右滑 = 收藏
        EndToStart -> { onDeleteRequest(); false }    // 左滑 = 删除
        else -> false
    }
}
```

| 根因 | 说明 |
|---|---|
| **G1 方向不锁定** | 水平拖拽与 LazyColumn 的纵向滚动手势在同一 pointer 流里竞争。手指「上下滚动」时几乎不可能有零横向漂移，一旦横向分量先越 slop，列表就被劫持成「拖拽某一行」。 |
| **G2 无中间态** | 位移越过阈值 → 松手即 settle → **立即执行**。用户没有任何「我快越线了」的反馈窗口。 |
| **G3 动作静默** | 收藏只在高亮 `tertiary` 星标上体现，滑完看不出发生过什么；删除虽有弹窗兜底，但弹窗本身就是「误触的证据」。 |
| **G4 两侧都占满** | 左、右都被一次性动作占用，没有给「露出按钮再点」这种带缓冲的交互留位置。 |

### 1.3 结论

**降密要动节奏（间距梯度 + 层级权重），防误触要动手势范式（把「执行」从手势里拿出来），而不是调一个阈值。**

---

## 二、布局降密方案

### 2.1 新增间距 token（`ui/theme/Spacing.kt`）

```kotlin
// 列表行节奏
val RowVertical  = 16.dp   // 行上下内边距（原裸用 Md=12）
val RowGapTight  = 6.dp    // 行内同组间距：标题 ↔ 摘要
val RowGapGroup  = 10.dp   // 行内跨组间距：摘要 ↔ 标签
val RowGapMeta   = 8.dp    // 行内从属间距：标签 ↔ 元信息
val ListTopGap   = 4.dp    // 头部与列表之间的呼吸
val ListBottomGap = 24.dp  // 列表底部留白
```

沿用既有数值阶梯（4 的倍数），不引入新基数。

### 2.2 行内间距重排（`NoteRowBody`）

| 段落 | 改前 | 改后 | 理由 |
|---|---|---|---|
| 摘要 | `2.dp` | `RowGapTight` = 6 | 标题/摘要是「同一组」的上下两行，6dp 足以建立主次又不脱离 |
| 标签 | `6.dp` | `RowGapGroup` = 10 | 标签是新的信息组，需要明确大于组内间距 |
| 元信息 | `6.dp` | `RowGapMeta` = 8 | 元信息是标签组的脚注，从属于标签，故略小于跨组间距 |

形成 **6 → 10 → 8** 的节奏梯度，读者可仅凭留白读出「标题+摘要是主体，标签+元信息是分类与出处」。

**硬编码的 `2.dp` / `6.dp` 全部换成 token**，让节奏可被集中调节。

### 2.3 行上下留白（`DocumentRow`）

`top = Spacing.Md` (12) → `top = Spacing.RowVertical` (16)。

- 列表视图单行高度 138 → **146dp**
- 时间线视图的 `Card` 变体本来就是 `CardPadding` (16)，改完后两种形态**上下留白一致**，切换视图时行距不再跳动

### 2.4 分隔线两侧缩进（`ZhiLuDivider` 用法）

改前：`Modifier.padding(start = Spacing.PageGutter)` —— 线从正文左边界一直拉到屏幕右缘。
改后：`Modifier.padding(horizontal = Spacing.PageGutter)` —— **两侧都缩进**。

效果：线变成「正文宽度」的短线，右边多出 20dp 留白后，每一行在视觉上更像一个独立段落而不是表格行。发丝线本身保持 `Hairline` (0.5dp) + `outlineVariant`，不加强对比度。

### 2.5 标签索引页（`TagRow` / `TagsScreen`）

标签页的问题是**同一套 `DocumentRow` 撑出了表格观感**：每行只有一个 16sp 标签名 + 一个 ⋮，行与行完全等距等宽。

调整：

1. 行上下 padding 同步升到 `RowVertical`（16），行高从约 46dp → **约 54dp**
2. 分割线同样改为两侧缩进
3. 标签列表的 `bottom` 内容留白从 `Xl` (24) 提到 `Xxl` (32)，末端不再贴边
4. **不新增滑动面**：标签的删除继续走 ⋮ 显式菜单 + `AlertDialog` 二次确认。标签数量通常远少于笔记，滑动收益低而误触成本高；破坏性操作的入口收敛到显式点击

> 后续可选增强（本次不做，需改 `TagRepository` 接口 + 8 个测试替身）：标签行增加「N 条笔记」次级信息，把单行文本行变成两级条目。留作独立需求。

### 2.6 头部呼吸感

- `HomeListHeader`：`vertical = Spacing.Xs` (4) → `Spacing.Sm` (8)
- `SectionHeader`：保持 `vertical = Spacing.Sm`，但时间线视图的吸附标题因此与行有 8 + 16 = 24dp 的分隔

---

## 三、交互重排与防误触方案

### 3.1 范式变更：从「滑到底即执行」到「滑动露出 → 点按执行」

新交互**不改变左右语义**（用户肌肉记忆零成本迁移），只把「执行」这一步从手势里拿出来：

| 手势 | 露出槽（改前：直接执行） | 改后行为 |
|---|---|---|
| 右滑（StartToEnd） | 直接切换收藏 | 左侧露出 `收藏 / 取消收藏` 按钮（`tertiary`） |
| 左滑（EndToStart） | 直接弹删除确认 | 右侧露出 `删除` 按钮（`error`） |
| 长按 | 弹菜单（保留） | 弹菜单（保留，作为无障碍等价入口） |

### 3.2 三层防误触机制

```
第一层：手势层 —— 滑动不再具备任何执行能力
   ├─ 方向锁定：水平位移必须先于垂直位移越过 touch slop（awaitHorizontalTouchSlopOrCancellation）
   ├─ 一旦判定为纵向，手势立刻归还给 LazyColumn，行完全不响应
   └─ 滑动只改变行的水平偏移，最远 = 露出槽宽度，不可能「滑过头」

第二层：操作层 —— 必须点按真实按钮
   ├─ 露出槽内是 92dp 宽的按钮，图标 + 文案，触控目标 ≥ 48dp
   ├─ 同一时刻只允许一行处于露出态（单行互斥）
   └─ 纵向滚动 / 点击行内容 / 点击别处 / 切页 → 自动收起

第三层：确认层 —— 不可逆动作仍有确认或撤销
   ├─ 删除：仍走既有 DeleteNoteDialog 二次确认
   └─ 收藏：立即生效 + Snackbar 提供「撤销」（原本完全无反馈）
```

### 3.3 方向锁定为什么能解决问题

`awaitHorizontalTouchSlopOrCancellation` 的语义是：**只有当水平方向的位移先于交叉轴（垂直）越过 touch slop 时才返回非 null**；否则返回 null，表示这个手势的主轴是垂直的。

对比现状：`SwipeToDismissBox` 内部用的是不区分主轴的 slop 判定，斜向（哪怕偏垂直）拖拽也可能进入水平拖拽态；一旦进入，行的水平偏移就跟着手指走，松手时若超过 50% 宽度即触发动作 —— 这就是「上下翻动时误触」的完整因果链。

锁定后：

- 纯纵向滑动 → 100% 交给 LazyColumn，行偏移恒为 0
- 纯横向滑动 → 进入拖拽，但**只露出不执行**
- 斜向滑动 → 谁先越 slop 谁赢；即使横向赢，结果也只是「露出一个按钮」，不会再产生状态变更

### 3.4 拖拽与吸附细节

| 参数 | 取值 | 说明 |
|---|---|---|
| `RevealWidth` | 92.dp | 露出槽宽度，容纳「取消收藏」这类 4 字文案 |
| 吸附阈值 | 露出宽度的 **35%** | 低于此值回弹归零，避免轻碰即展开 |
| 展开动画 | `Spring`（MediumLow 阻尼） | 松手后吸附，带轻微回弹，强化「已经到位」的感知 |
| 收起动画 | `Spring` / 快速补间 | 滚动或点按触发 |
| 触觉反馈 | 吸附展开时 `HapticFeedbackType.TextHandleMove` | 一次轻振动，明确告知状态切换 |
| 拖拽范围 | `coerceIn(-RevealWidth, RevealWidth)` | 物理上不可能滑过头 |

### 3.5 状态归属

展开状态**提升到列表层**（`HomeScreen` / `TrashScreen`），以 `revealedId: Long?` 表示：

- 天然实现「单行互斥」—— 给某个 id 展开时，上一行自动收回
- 接入 `LazyListState.isScrollInProgress`：滚动中强制 `revealedId = null`
- 列表刷新 / 页面切走时状态自然重置

`HomeNoteItem` / `TrashRow` 因此改为受控组件（`revealed: Boolean` + `onRevealChange`），不再各自持有 `remember` 状态。

### 3.6 回收站同步

`TrashRow` 与笔记行是同一套文档行体系，手势冲突问题完全一致：

| 手势 | 改前 | 改后 |
|---|---|---|
| 右滑 | 直接恢复（不可撤销） | 左侧露出 `恢复` 按钮 |
| 左滑 | 直接弹永久删除确认 | 右侧露出 `永久删除` 按钮 |

永久删除仍走 `DeleteForeverDialog`。恢复动作从「滑到底直接生效」变成「露出后点按」，同样消除了误恢复。

---

## 四、受影响文件

**新增**

| 文件 | 作用 |
|---|---|
| `ui/component/SwipeRevealRow.kt` | 方向锁定 + 露出式滑动行，全站复用 |
| `docs/plans/2026-10-01-list-density-and-swipe-guard-design.md` | 本文件 |

**修改**

| 文件 | 改动 |
|---|---|
| `ui/theme/Spacing.kt` | 新增 `RowVertical` / `RowGapTight` / `RowGapGroup` / `RowGapMeta` / `ListTopGap` / `ListBottomGap` |
| `ui/component/DocumentRow.kt` | 上下 padding → `RowVertical`；新增 `onRevealReset` 钩子（点击已展开的行先收起） |
| `ui/component/NoteRow.kt` | 行内间距 2/6/6 → 6/10/8，改用 token |
| `ui/home/HomeNoteItem.kt` | 移除 `SwipeToDismissBox`，接入 `SwipeRevealRow`；改为受控展开 |
| `ui/home/HomeNoteList.kt` | 接收 `revealedId` / `onReveal`；分割线两侧缩进 |
| `ui/home/HomeScreen.kt` | 提升 `revealedId`；`LazyListState` 滚动收起；收藏 Snackbar 撤销 |
| `ui/home/HomeListHeader.kt` | 增加上下呼吸 |
| `ui/tag/TagRow.kt`、`ui/tag/TagsScreen.kt` | 行高与留白调整；分割线两侧缩进 |
| `ui/trash/TrashRow.kt`、`ui/trash/TrashScreen.kt` | 同步接入 `SwipeRevealRow` + 受控展开 + 滚动收起 |

---

## 五、验证口径

1. `./gradlew :app:assembleDebug`、`:app:lintDebug`、`:app:testDebugUnitTest` 三者全绿
2. 实机（MuMu 1920×1080 / rotation 1）验证：
   - 快速上下滚动列表，任一行的水平偏移恒为 0，不出现收藏或删除
   - 斜向 45° 滑动，同样不产生状态变更
   - 横向滑动后行停在露出态，按钮可见可点；点按才生效
   - 同时只允许一行露出；滚动时露出态自动收起
   - 删除走二次确认；收藏可撤销
3. 视觉复核：列表视图单行高度由约 138dp 提升至约 146dp，行内出现 6/10/8 的留白梯度

## 六、验证结论（2026-10-01，MuMu 1920×1080）

三项构建命令全绿：`assembleDebug` / `lintDebug` / `testDebugUnitTest`（**153 个用例 0 失败**）。

实机用例与结果：

| # | 用例 | 结果 |
|---|---|---|
| 1 | 纯纵向滑动 | 列表正常滚动，`notes` 表无任何变化 |
| 2 | 斜向 320:260（接近但未达 1.6 倍阈值） | 归列表滚动，行偏移为 0 |
| 3 | 斜向 400:200 | 同上，未进入拖拽 |
| 4 | 右滑（900px） | 只露出「收藏」槽；`isFavorite` 仍为 0，无 Snackbar |
| 5 | 点按「收藏」槽 | `isFavorite` 0 → 1，弹出「已收藏 / 撤销」Snackbar |
| 6 | 点按「撤销」 | `isFavorite` 1 → 0，状态精确回滚 |
| 7 | 先露第 1 行、再露第 2 行 | 第 1 行自动收回，任意时刻仅一行露出 |
| 8 | 露出态下纵向滚动 | 露出态自动收起 |
| 9 | 左滑 | 只露出「删除」槽，未触发删除 |
| 10 | 点按「删除」槽 | 弹出「移入回收站？」二次确认，槽位随即收回 |
| 11 | 时间线视图右滑卡片 | 露出槽与卡片外距对齐，圆角一致 |
| 12 | 标签页 | 行呈现「标签名 + N 条笔记」两级信息，行距明显拉开；计数与 `tags` 表（16 条）一致 |

### 实机过程中发现并修复的缺陷

**撤销收藏被静默跳过。** `HomeViewModel.setFavorite` 最初带了一句
「值与现状相同就提前返回」的守卫：`if (note.isFavorite == favorite) return`。
但调用方传进来的 `note` 是**列表渲染时的快照**，它的 `isFavorite` 是切换**前**的旧值；
撤销时 `favorite` 恰好等于这个旧值，于是守卫直接 return，撤销永远不生效
（表现为点了「撤销」但收藏状态不回滚）。
现已移除该守卫，并在函数注释里写明原因，避免再次被「优化」加回去。

> 教训：用渲染快照和期望值做「无变化就跳过」的比较，在快照本身已经过期时必然出错。
> 这类守卫只有在能读到**权威当前值**时才是安全的。

### 测试中的坐标口径

MuMu 的 `wm size` 报 1080×1920，但实际 override 为 **1920×1080 + rotation 1**；
截图、`input tap/swipe` 一律按 1920×1080 取值。Bottom tab y≈1038（笔记 234 / 标签 717 / 助手 1201 / 我的 1685）。
