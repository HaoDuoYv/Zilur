# 入口重构：标签页 → 复习中心 —— 设计说明

日期：2026-10-08
状态：**方案待确认（未实现）**
范围：底栏入口调整、复习中心（复习 + 提醒）、标签并入搜索链路

---

## 背景

底栏现在是 **笔记 / 标签 / ＋ / 助手 / 我的**。其中「标签」页承担着两个职责：

1. **标签索引**：列出全部标签（含每条笔记数）、新建、删除；
2. **按标签筛选笔记**：点标签 → 同屏切换成该标签下的笔记列表。

问题是这个入口的"性价比"在下降：标签的终点是**筛选**，而筛选天然属于搜索；把一整格底栏
交给"一个筛选器的管理页"偏重了。与此同时，App 里另有一块能力**已经长齐了后台、却没有前台**：

| 能力 | 数据层 / 域层 | UI 现状 |
|---|---|---|
| 复习计划（阶梯式：1/3/7/15/30 天） | `ReviewPlanEntity` / `ReviewDao` / `ReviewRepositoryImpl` / `ReviewSchedulePolicy` 全部就绪 | 唯一入口是**笔记内**顶栏「复习」胶囊 → `ReviewSheet`；**没有任何全局视图**，`ReviewDao` 甚至没有"列出所有计划"的查询 |
| 待办提醒 | `TodoItemEntity` / `TodoDao` / `TodoRepositoryImpl` + `ReminderInstance(TODO)` | 待办只能逐篇进笔记看；提醒要去「我的 → 提醒中心」 |
| 提醒 | `ReminderDao` / `ReminderRepositoryImpl` / `ReminderNotifier` / WorkManager 轮询 | `ReminderCenterScreen`（三级入口：我的 → 提醒中心） |

本轮决策：**把「标签」槽位换成「复习中心」**，让复习与提醒有稳定的高频入口；标签降级为
**搜索链路里的筛选器**，不再占独立入口。

---

## 决策总表

| # | 决策点 | 结论 | 理由 |
|---|---|---|---|
| D1 | 底栏第二槽位 | 标签 → **复习**（同位替换，不改顺序） | 肌肉记忆成本最低 |
| D2 | 复习中心信息架构 | **两档：待复习 / 提醒** | 一档管"计划与队列"，一档管"提醒实例"，动词清晰不重叠 |
| D3 | 复习提醒 vs 计划的关系 | **单一事实来源**：REVIEW 提醒由计划驱动，提醒页里**只读** | 两个地方都能改同一状态，必然不一致 |
| D4 | 标签筛选多选语义 | **AND**（必须同时含有全部选中标签） | "筛选"的心智模型是逐步收窄；OR 语义后续可加切换 |
| D5 | 标签管理（重命名/删除/新建）的新家 | 搜索筛选条：**长按 chip 就地重命名/删除** + 条尾「管理」进标签管理弹层 | 不再有独立标签页，但能力一个都不能丢 |
| D6 | 首页铃铛去向 | → **复习中心（提醒档）** | 铃铛带"到期红点"，语义就是提醒 |
| D7 | 暂停的计划「继续」 | 新增 `enablePlan`：**保留档位**，`nextReviewAt = now`（立即回到待复习队列） | 现有 `startPlan` 会把 `currentStep` 重置为 0 —— 那是"重新开始"，不是"继续" |
| D8 | 命名 | 保留用户提议的「**复习中心**」 | 「今日」「计划」等命名会把待办也卷进来，那是阶段二的事 |

---

## 一、入口调整总览

### 1.1 底栏

```
之前：  笔记  标签   (＋)   助手   我的
之后：  笔记  复习   (＋)   助手   我的
```

- 新增 `Destination.Review = "review"`；**删除** `Destination.Tags`；
- `TopLevelRoutes` 中 `tags` 换成 `review`；
- 图标成对（沿用"实心↔描边"编码）：提案 **`Icons.Filled.Style` / `Icons.Outlined.Style`**
  （卡堆 = 复习卡片的隐喻）；备选 `School`（毕业帽）、`Quiz`；
- 排序、选中态、`switchTopLevel` 的 flags（`popUpTo(start)+saveState+launchSingleTop+restoreState`）
  全部不动。

### 1.2 铃铛（首页顶栏）

保留（它背着"到期红点"），但目标从 `Destination.Reminders` 改为
**复习中心 → 提醒档**（`HomeTopBar.onOpenReminders` 改调 `navigateToReview(tab = Reminders)`）。

### 1.3 「我的」设置页

- 提醒组里的「提醒中心」入口 → 改名为「**复习中心**」，指向同一目的地（带提醒档意图）；
- 「通知权限」「提醒总开关」不动。

### 1.4 通知深链

`MainActivity.navigateFrom` 的三条分支：

| Intent extra | 现在 | 之后 |
|---|---|---|
| `EXTRA_NOTE_ID` | 打开笔记 | 不变 |
| `EXTRA_REMINDER_CENTER` | `Destination.Reminders` | **复习中心（提醒档）** |
| `EXTRA_OPEN_ASSISTANT` | 助手（复用 entry） | 不变 |

`Destination.Reminders` 路由**删除**（唯一引用方就是上面这几处，无外部深链，安全）。

### 1.5 跨页「带意图」的交接：`AppIntents`

**为什么不能走路由参数**：`Destination` 的 KDoc 已写明——带参数的路由每次都是新 entry，
会把目标页的 ViewModel 换成一份新状态（助手页踩过：开场白参数造出第二个实例）。
所以"进复习中心时落在哪一档""进搜索时预置哪个标签"这类**一次性意图**，走进程级交接：

```kotlin
// ui/navigation/AppIntents.kt（新增，模式对齐 AiPromptHandoff）
object AppIntents {
    /** 下一次进入复习中心时预选的档位（消费即清空）。 */
    val pendingReviewTab = MutableStateFlow<ReviewTab?>(null)
    /** 下一次打开该笔记时自动弹出复习面板（消费即清空）。 */
    val pendingReviewSheetNoteId = MutableStateFlow<Long?>(null)
    /** 下一次进入首页搜索时预置的标签筛选（消费即清空）。 */
    val pendingSearchTagId = MutableStateFlow<Long?>(null)
}
```

消费方在 `LaunchedEffect(Unit)` 里取一次并**立即清空**（consume-once），避免下次误触发。

`Destination.kt` 另外新增与 `navigateToAssistant()` 并列的两个助手：

- `navigateToReview(tab: ReviewTab? = null)`（写意图 + 顶层切换）；
- `navigateTopLevel(destination)` —— 把 `BottomBar` 里私有的 `switchTopLevel` 提升为公开扩展，
  供「助手 / 复习 / 首页」三处共用同一套 flags（避免第三份拷贝）。

> 复习中心**不用记档位持久化**：它是顶层页，`saveState/restoreState` 已经会把
> `SegmentedToggle` 的选择（`rememberSaveable`）带回来；`AppIntents` 只负责"这次进来要看哪一档"。

---

## 二、复习中心

### 2.1 骨架

```
Scaffold(AppTabScaffold) + AppTopBar("复习中心")
└─ SegmentedToggle 两档（复用 ui/component/SegmentedToggle）
   ├─ 待复习 0  ← 计划与队列（默认档）
   └─ 提醒 0
```

档位计数（"待复习 3""提醒 5"）直接进分段标题——底栏没法带角标，用分段承载同样的信息。

### 2.2 「待复习」档

**分区（自上而下）**，每区一个 `SectionHeader`：

| 分区 | 内容 | 规则 |
|---|---|---|
| 已逾期 | `nextReviewAt < 今天 0 点` | 红色强调（`colorScheme.error` 族），置顶 |
| 今天 | `< 明天 0 点` | 主色强调 |
| 接下来 | 未来 7 天内 | 常规色，仅展示 |
| 已暂停 / 已完成 | 折叠区（默认收起，带计数） | 已暂停可「继续」；已完成可「重新开始」 |

**行设计**（新组件 `ReviewPlanRow`，复用 `DocumentRow` 书脊结构）：

```
┃ 笔记标题（rowTitle）
┃ ● ● ● ○ ○   第 3/5 次 · 上次：一般 · 逾期 2 天
┃                              [开始复习]
```

- 阶梯进度点 = `currentStep`（共 5 档），让"复习到哪一步"一眼可见；
- 「开始复习」：`navigate(NoteEdit.createRoute(noteId))` + **进程级交接**
  `AppIntents.pendingReviewSheetNoteId`，笔记页首次组合时自动弹出 `ReviewSheet`；
- 行为菜单（⋮）：**暂停复习**（`disablePlan`）/ **继续**（新增 `enablePlan`，D7）/
  **重新开始**（`startPlan`，仅已完成区）/ 打开笔记。

**顶部统计条**（`MetaLine`）：`今日已复习 N 篇 · 进行中 M 个计划`
（N 来自 `review_events` 的 `COUNT(*) WHERE reviewedAt >= 今天0点`，下推 DAO，不在内存聚合）。

**空状态**：没有任何计划时给引导——「在任意笔记顶栏点「复习」即可开启计划」+ 按钮「去笔记列表」。

### 2.3 「提醒」档

**吸收现有 `ReminderCenterScreen` 的全部能力**，沿用三档筛选
（待处理 / 已逾期 / 已完成，`ReminderFilter` + `SegmentedToggle` 原样复用）。

行设计升级（`ReminderRow` 已有状态书脊 + 完成/取消，本轮**富化信息**）：

| 字段 | 来源 | 说明 |
|---|---|---|
| 主标题 | `notes.title` | 现在行里只有到期时间 + "关联笔记"，**看不到是哪篇**（要补 join） |
| 副标题 | TODO → `todo_items.content`；REVIEW → 「第 N/5 次复习」 | 提醒"要做什么"必须写出来 |
| 类型标记 | `复习` / `待办` 小 chip | 区分来源 |
| 到期时间 | `dueAt` | 保留 |

**操作规则（单一事实来源，D3）**：

| 类型 | 完成 | 取消 | 延后 |
|---|---|---|---|
| **待办提醒**（TODO） | 提醒 `DONE` **+ 回写 `todo_items.completedAt`**（笔记里同时勾上） | 提醒 `CANCELED` **+ 清空 `todo.remindAt`**（待办保留，只是不再提醒） | 「延后 1 小时 / 明天上午」= `upsertScheduled(dueAt = …)` |
| **复习提醒**（REVIEW） | **不提供**（只读）。点击行 → 复习中心「待复习」档 | **不提供** | **不提供** |

> 为什么 REVIEW 只读：复习提醒的生死必须跟 `review_plans.nextReviewAt` 一致
> （开启/评级/暂停时由 `scheduleReviewReminder` 重建）。允许在提醒页直接完成，
> 就会出现"提醒完成了但计划还等着下一次"的分叉。
> 行上给出说明文案：「由复习计划驱动，去「待复习」里处理」。

**要修的数据层问题（现状缺陷）**：
1. `ReminderRepository.markDone(type, sourceId)` **只翻提醒状态、不回写源头** ——
   完成"待办提醒"后，笔记里的待办仍是未勾选。→ 新增域层用例
   `ResolveReminderUseCase`（完成 / 取消 / 延后三分支，按 `type` 联动源头），
   `ReminderCenterViewModel` 里两处调用改走用例；
2. 没有「稍后提醒（snooze）」——用 `upsertScheduled` 改 `dueAt` 即可，低成本补上。

### 2.4 复习机制现状（不改，只展示）

- 阶梯：`[1, 3, 7, 15, 30]` 天（`ReviewSchedulePolicy.defaultIntervalsMillis`）；
- 评级：困难 / 一般 / 掌握（`ReviewRating.HARD / NORMAL / MASTERED`）；
  HARD 退回上一档且间隔回到 1 天，NORMAL +1 档，MASTERED +2 档；越过末档 → 毕业
  （`enabled=false; completedAt=now`）；
- 复习中心把这些**显性化**：进度点、下次时间、毕业状态——算法本身上不再动。

### 2.5 需要新增的数据层能力

| 层 | 新增 | 用途 |
|---|---|---|
| `ReviewDao` | `observePlansWithNote(): Flow<List<ReviewPlanRow>>`（`review_plans JOIN notes`，带 `notes.title`） | 复习档的列表（含暂停/完成） |
| `ReviewDao` | `countEventsSince(since): Flow<Int>` | 顶部「今日已复习 N 篇」 |
| `ReviewRepository` | `observePlans(): Flow<RepositoryResult<List<ReviewPlanWithNote>>>` | 同上 |
| `ReviewRepository` | `enablePlan(noteId, now)`（保留 `currentStep`，`nextReviewAt = now`） | D7「继续」 |
| `ReminderDao` | `observeAllWithContext(): Flow<List<ReminderWithContext>>`（LEFT JOIN `notes` / `todo_items`） | 提醒档的标题与"要做什么" |
| 域层 | `ReviewQueueClassifier`（纯函数：按 `nextReviewAt` 分 逾期/今天/未来） | 与 `ReminderClassifier` 同风格，可单测 |
| 域层 | `ResolveReminderUseCase` | 提醒完成/取消/延后联动源头（2.3） |

> 既有约定要遵守：**计数下推 DAO**（单条 `GROUP BY`/`COUNT`，别在 VM 内存聚合）；
> 新 `@Query` 落库后检查 `*Dao_Impl` 的失效表集合，漏表 Flow 不重发。

---

## 三、提醒与复习的关联方式（一张图）

```
源头（单一事实来源）              投递（跟随源头生灭）              触达
─────────────────────           ──────────────────────           ──────
ReviewPlan.nextReviewAt  ─────▶  ReminderInstance(REVIEW)  ─────▶ WorkManager 轮询(≤15min)
TodoItem.remindAt        ─────▶  ReminderInstance(TODO)    ─────▶   → 通知 → 点进笔记
```

**同步规则（谁生谁灭）**：

| 源头动作 | 已有实现 | 提醒效果 |
|---|---|---|
| 开启复习计划 | `NoteViewModel.startReviewPlan` | 生成 REVIEW 提醒（`dueAt = nextReviewAt`）✅ 已有 |
| 复习评级 | `recordReview` → `scheduleReviewReminder(updatedPlan)` | 更新/取消（毕业时 `nextReviewAt=null` → 取消）✅ 已有 |
| 暂停计划 | `disableReviewPlan` | 取消 REVIEW 提醒 ✅ 已有 |
| **继续计划（新）** | `enablePlan` | 重建 REVIEW 提醒（`dueAt = now`）⬜ 要补 |
| 创建/修改带提醒的待办 | `createTodo` / `reconcileTodoReminder` | 生成/更新 TODO 提醒 ✅ 已有 |
| **提醒页完成/取消待办提醒** | — | 回写源头 ⬜ 要补（见 2.3） |

**通知权限**：复习中心在首次开启复习 / 首次创建带提醒待办时，沿用
`requestNotificationPermissionIfNeeded`（`NoteEditScreen` 现有流程）；
提醒档顶部在无权限时显示一条常驻提示条（点进系统设置）——比"通知静默不发"可解释得多。

---

## 四、标签在搜索链路中的处理逻辑

原则：**标签不再有任何独立入口；它的全部出口都收敛为"搜索里的筛选条件"。**

### 4.1 交互

搜索框（首页唯一入口）聚焦或有关键词时，下方出现「**标签筛选条**」（新组件 `TagFilterBar`）：

- 全部标签横向滚动 chips（按笔记数降序，`TagDao.countNotesPerTag` 已有）；
- **点选 = 加筛选**（多选，AND 语义 D4）；再点 = 取消；有选中时条首出现「清除」；
- 条尾「**管理**」chip → 标签管理弹层（4.4）；
- **长按任意 chip** → 就地弹出该标签的菜单：重命名 / 删除。

### 4.2 查询语义（组合查询）

`NoteDao.search` 升级为一条**关键词 × 标签集合**的组合查询（`EXISTS` 子查询替代原有
`LEFT JOIN + DISTINCT`，同时保留对 `tags.name` 的 LIKE）：

```sql
SELECT n.* FROM notes n
WHERE n.deletedAt IS NULL
  AND (
    :keyword = ''
    OR n.title LIKE '%' || :keyword || '%'
    OR EXISTS (SELECT 1 FROM note_blocks b
               WHERE b.noteId = n.id AND b.content LIKE '%' || :keyword || '%')
    OR EXISTS (SELECT 1 FROM note_tags nt JOIN tags t ON t.id = nt.tagId
               WHERE nt.noteId = n.id AND t.name LIKE '%' || :keyword || '%')
  )
  AND (
    :tagCount = 0
    OR (SELECT COUNT(DISTINCT nt2.tagId) FROM note_tags nt2
        WHERE nt2.noteId = n.id AND nt2.tagId IN (:tagIds)) = :tagCount
  )
ORDER BY n.updatedAt DESC
```

- `tagCount` 单独传参（Room 不支持在 SQL 里写集合长度）；AND 语义靠"命中的标签数 = 选中数"；
- **关键词可以为空**：空关键词 + 选中标签 = 纯标签浏览——**这就是老标签页"筛选"职责的接管**；
- 关键词非空时不再走 `TAG_QUERY_PREFIX` 的另一条代码路径（见 4.3）。

### 4.3 `#标签名` 的归一

现状：输入 `#xxx` 时走 `getTagByName` 精确匹配。之后统一为**输入归一**：

- 提交/防抖命中时，若 `#` 后的名字**能匹配到标签** → 转成一个筛选 chip，输入框清空；
- 匹配不到 → 按普通关键词搜索（不报错）；
- （阶段二）输入 `#` 时浮出标签建议下拉。

这样"手输 `#tag`"与"点 chip"最终汇成**同一个状态**（选中标签集合），只有一套语义。

### 4.4 标签管理的新家

| 能力 | 之前 | 之后 |
|---|---|---|
| 新建标签 | 标签页顶栏「＋」/ 笔记内 TagPickerInline | **TagPickerInline 保留**（加标签的主路径）+ 管理弹层里的「新建」 |
| **重命名**（当前完全没有 UI，`TagRepository.updateTag` 已就绪） | — | 长按 chip → 「重命名」；管理弹层内也可 |
| 删除 | 标签页 TagRow ⋮ | 长按 chip → 「删除」（沿用现有确认文案：「仅移除标签关联，不会删除笔记内容」） |
| 合并 | 无 | 阶段二（管理弹层里多选 → 合并到一个） |

边界规则（写进实现）：

- 重命名 = 改 `tags.name`（**同一 id，关联不断**）→ 笔记里的胶囊、搜索结果、`#xx` 输入即时同步；
- 删除 = 仅删 `note_tags` 关联 + `tags` 行，**正文文本不动**（标签不是从正文解析出来的）；
- 未保存的新笔记上用 TagPickerInline 新建的标签，沿用现有 `createTag` 路径。

### 4.5 老 `TagsScreen` 能力去向（逐条对账，确保零丢失）

| 老能力 | 新位置 |
|---|---|
| 标签索引列表（含计数） | 搜索页「标签筛选条」+ 管理弹层（计数保留） |
| 点标签 → 该标签的笔记列表 | 搜索页：选中 chip（空关键词） |
| 新建标签 | TagPickerInline / 管理弹层 |
| 删除标签 | 长按 chip / 管理弹层 |
| 「N 条笔记 · N 个标签」统计行 | 搜索页结果头（已有 `SearchResultHeader`）与筛选条即可表达；首页统计行的「N 标签」可选做成可点（进搜索展开筛选条） |
| 空状态引导 | 筛选条为空时提示「还没有标签，在笔记里添加第一个标签」 |

---

## 五、跳转关系总表

| From | 触发 | To | 实现方式 |
|---|---|---|---|
| 底栏 | 点「复习」 | 复习中心（记住上次档位） | `switchTopLevel` |
| 首页铃铛 | 点铃铛（带红点） | 复习中心 · 提醒档 | `navigateToReview(Reminders)` + `AppIntents` |
| 通知 | 点提醒通知 | 复习中心 · 提醒档 | `MainActivity.navigateFrom`（同左） |
| 通知 | 点"笔记"类通知 | 笔记（不变） | `EXTRA_NOTE_ID` |
| 复习中心 · 待复习 | 点「开始复习」 | 笔记（只读）+ **自动弹复习面板** | `NoteEdit.createRoute` + `AppIntents.pendingReviewSheetNoteId` |
| 复习中心 · 提醒 | 点行 | 该笔记 | `NoteEdit.createRoute` |
| 复习中心 · 提醒 | 点 REVIEW 行 | 切到「待复习」档 | 就地 `selectTab`（说明文案引导） |
| 笔记（只读） | 点顶栏「复习」胶囊 | `ReviewSheet`（不变） | 现状 |
| 笔记（卡片标签胶囊） | 点标签 | 首页搜索 + 预置该标签筛选 | `AppIntents.pendingSearchTagId` + `navigateTopLevel(Home)` |
| 我的 | 点「复习中心」 | 复习中心 · 提醒档 | `navigateToReview(Reminders)` |
| 我的 | 点「回收站 / 外观 / AI 配置」 | 不变 | 现状 |
| 助手 / ＋弹层 | 不变 | — | 现状 |

> 「笔记卡片标签胶囊可点」是补上的**标签→搜索**桥梁（现在 `NoteTagsLine` 是纯展示、
> 明确注释为"非交互元素"）——标签既然只剩搜索这一个出口，从笔记一键跳筛选就是它的主要入口。

---

## 六、文件清单

**新增**

```
ui/review/ReviewCenterScreen.kt        # 两档骨架（AppTabScaffold + SegmentedToggle）
ui/review/ReviewCenterViewModel.kt     # 计划流 + 提醒流 + 档位/筛选状态
ui/review/ReviewCenterUiState.kt
ui/review/ReviewPlanRow.kt             # 计划行（进度点 / 到期 / 开始复习 / ⋮）
ui/review/ReviewHistoryOrHint.kt?      # 空状态与引导（可并入 Screen）
ui/navigation/AppIntents.kt            # 一次性跨页意图（对齐 AiPromptHandoff 模式）
ui/search/TagFilterBar.kt              # 标签筛选 chips 条
ui/search/TagManageSheet.kt            # 标签管理弹层（重命名/删除/新建）
domain/reminder/ReviewQueueClassifier.kt
domain/usecase/ResolveReminderUseCase.kt
```

**改造**

```
ui/navigation/Destination.kt           # +Review，-Tags/-Reminders，+navigateToReview
ui/navigation/BottomBar.kt             # bottomTabs() 第二项换图标与文案
ui/navigation/AppNavHost.kt            # 路由增删
MainActivity.kt                        # 深链改向
ui/home/HomeTopBar.kt / HomeScreen.kt  # 铃铛目标；搜索区挂 TagFilterBar
ui/home/HomeViewModel.kt               # 标签筛选状态 + 组合查询 + # 归一
ui/note/blocks/…/NoteTagsLine.kt       # 胶囊可点（可选回调）
ui/note/ReviewSheet.kt                 # 顺手修：「正在录音识别…」是误导文案（无录音功能）
data/local/dao/NoteDao.kt              # searchWithTags
data/local/dao/ReviewDao.kt            # observePlansWithNote / countEventsSince
data/local/dao/ReminderDao.kt          # observeAllWithContext
data/repository/ReviewRepositoryImpl.kt / domain/repository/ReviewRepository.kt
data/repository/ReminderRepositoryImpl.kt（联动交用例，接口保持）
ui/settings/SettingsScreen.kt / ReminderSection.kt   # 入口改名与指向
```

**删除 / 吸收**

```
ui/tag/TagsScreen.kt / TagsViewModel.kt / TagsUiState.kt   # 职责拆入搜索与管理弹层
ui/tag/TagRow.kt / TagFilterBanner.kt / NewTagInput.kt     # 组件能力吸收进新组件
ui/reminder/ReminderCenterScreen.kt                        # 被复习中心「提醒」档吸收
ui/reminder/ReminderCenterViewModel.kt                     # 逻辑迁入 ReviewCenterViewModel
ui/reminder/{ReminderRow, ReminderFilter}.kt               # 保留复用（富化）
Destination.Tags / Destination.Reminders
```

---

## 七、分期

**阶段一（核心，一次做完即闭环）**

1. 底栏与路由替换（D1）+ `AppIntents` + `navigateToReview`；
2. 复习中心两档骨架 + 「待复习」全功能（含 `enablePlan`、`ReviewQueueClassifier`）；
3. 「提醒」档：吸收提醒中心 + 行富化（标题/待办内容）+ 联动源头 + 延后；
4. 标签并入搜索：`TagFilterBar` + 组合查询 + `#` 归一 + 长按管理 + 胶囊可点跳筛选；
5. 铃铛 / 设置页 / 通知深链改向；`ReviewSheet` 文案修正。

**阶段二（增强，可各自独立）**

- 全局「待办」档（跨笔记待办列表：`TodoDao` 需补 `observeAll` / `delete`）；
- 复习统计与历史（读取 `review_events`：近 7 天曲线、毕业率）；
- `#` 自动补全下拉；标签合并；
- AI 工具扩展：`schedule_review` / `list_due_reviews` / `add_todos` 支持 `remindAt`；
- 通知动作按钮（通知栏直接「完成 / 延后」，需 `BroadcastReceiver`）；
- 自定义复习间隔 / SM-2。

---

## 八、待你拍板的点（我的默认已写进正文）

| # | 问题 | 我的默认 | 备选 |
|---|---|---|---|
| P1 | 多标签是 AND 还是 OR | AND | 后续加"任一"切换 |
| P2 | 复习提醒在提醒页是否只读 | 只读（D3） | 允许完成（要处理与计划的一致性） |
| P3 | 「继续」暂停的计划 | 保留档位、立即到期（D7） | 保留档位、明天到期 |
| P4 | 底栏图标 | `Style` 卡堆 | `School` / `Quiz` |
| P5 | 复习中心是否现在就把「待办」开成一档 | 不开（阶段二） | 一步到位三档 |

---

## 九、验收清单（阶段一，真机）

1. 底栏为 笔记 / 复习 / ＋ / 助手 / 我的，选中态与图标成对切换正常；
2. 复习中心：逾期置顶（红）、今天、接下来 7 天、暂停/完成折叠区；进度点与实际 `currentStep` 一致；
3. 「开始复习」→ 打开笔记**并自动弹面板**；评级后面板显示"下次复习：X 天后"，返回列表已刷新、
   提醒时间同步；
4. 暂停 → REVIEW 提醒消失；「继续」→ 立即回到待复习且**档位没被重置**；
5. 提醒档：待办提醒显示笔记标题 + 待办文本；「完成」后**去笔记里看，待办已勾选**；
   「取消」后待办保留但不再提醒；「延后 1 小时」dueAt 前移；REVIEW 行只读且引导正确；
6. 铃铛 / 通知 / 设置页入口 → 均落在复习中心 · 提醒档；
7. 搜索：chips 多选 AND 生效；空关键词 + 标签 = 标签浏览；`#标签名` 变成 chip；
   结果行标签胶囊点击 → 就地收窄为筛选；长按 chip 重命名后，笔记内胶囊同步更新；
8. 删除标签 → 仅解关联，笔记正文与内容不变；
9. 两套外观（纸墨 / 动森）× 明暗模式无样式破损；无 `CorruptionException`、无空白列表。

---

## 十、实施记录（阶段一）

> 拍板结果：**P1–P5 全按默认**（多标签 AND；REVIEW 提醒只读；「继续」保留档位、立即到期；
> 底栏图标 `Style`；「待办」不单独开档）。

### 10.1 落地范围

| 层 | 新增 | 改动 |
|---|---|---|
| 数据 | `ReviewPlanRow` / `ReminderWithContextRow` 投影；`ReviewDao.observePlansWithNote`、`countEventsSince`；`ReminderDao.observeAllWithContext`；`NoteDao.searchWithTags`（`COUNT(DISTINCT nt2.tagId) = :tagCount` → AND 语义） | `NoteRepository.searchNotes(keyword, tagIds)`、`ReviewRepository` 补 `observePlans` / `observeEventCountSince` / `enablePlan`、`ReminderRepository.observeAllWithContext`、`TodoRepository.updateRemindAt` 及各自实现 |
| 领域 | `ReviewQueueClassifier`、`ReviewReminderSync`、`ResolveReminderUseCase`、`ManageReviewPlanUseCase` | — |
| 导航 | `AppIntents`（一次性意图）；`navigateTopLevel` / `navigateToAssistant` / `navigateToReview` / `navigateToSearchTag` | `Destination`（删 `Tags` / `Reminders`，加 `Review`）、`BottomBar`、`AppNavHost`、`AppShell`（下发 `LocalAppIntents`）、`MainActivity` 通知深链 |
| 界面 | `ui/review/`（Screen / ViewModel / UiState / `ReviewPlanRow`）、`ui/search/`（`TagFilterBar` / `TagManageSheet` / `TagDialogs`）、`ui/component/QuietAction`、`ui/reminder/SnoozeOption` | `ReminderRow`（富化上下文 + 按类型分叉动作）、`HomeScreen` / `HomeViewModel` / `HomeUiState`（关键词 × 标签组合筛选）、`TagChip`（长按菜单）、`NoteRow` / `NoteTagsLine` / `HomeNoteItem`（标签胶囊可点 = 标签→搜索的桥梁）、`NoteEditScreen`（消费"自动弹复习面板"意图） |
| 移除 | — | `ui/tag/` 整包（6 个文件）+ `TagsViewModelTest`；`ui/reminder/ReminderCenter*` 三件 + `ReminderCenterViewModelTest` |

### 10.2 双主题适配

新增界面**没有一处硬编码色值或圆角**（复核：在 `ui/review/`、`ui/search/`、`ReminderRow`、
`QuietAction`、`NoteTagsLine` 里 grep `Color(0x` / `Color.White` / `Color.Black` / `RoundedCornerShape(数字)` 均无命中）：

- 分区书脊色（逾期 `error` / 今天 `primary` / 接下来 `tertiary` / 已暂停 `outline`）、
  提醒状态胶囊（`reminderStatusStyle` 由 `colorScheme` 派生）全部走 md3 语义角色；
- 圆角取 `MaterialTheme.shapes`（跟随外观：纸墨 14dp / 动森 20dp），按钮与胶囊走 `ShapeTokens.Pill`；
- 动森"靠描边立形状"由 `palettePaint(LocalThemePalette.current).componentBorder`
  这一**唯一判据**表达（标签 chip 选中态用标签色描边）；
- 标签色统一经 `rememberTagAccent`（深色下自动提亮），新建标签色板三处共享 `TagCreationPalette`；
- 复习计划行 / 提醒行沿用 `DocumentRow`（无背景、无阴影），两套外观下都自然成立。

### 10.3 验收结论

| 项 | 结果 |
|---|---|
| `:app:assembleDebug` | ✅ APK 23.8 MB |
| `:app:lintDebug` | ✅ |
| `:app:testDebugUnitTest` | ✅ 573 例 / 78 个测试类全绿（新增 `ReviewQueueClassifierTest` 9 例、`ResolveReminderUseCaseTest` 6 例、`ReviewCenterViewModelTest` 14 例、`ReviewPlanRowTimeLabelTest` 8 例；`HomeViewModelSearchTest` 重写为 13 例，覆盖 AND 组合筛选与标签意图） |
| 真机 / 模拟器 | ✅ 见 10.4（含两处真机缺陷修复） |

### 10.4 真机验收（MuMu Android，1440×2560 @ 640dpi）

截图存档：`docs/review/verify-stage2-*.png`（两套外观 × 明暗四象限全覆盖）。

| 验收点 | 证据 |
|---|---|
| 底栏同位替换成五格（笔记 / 复习 / ＋ / 助手 / 我的），「标签」入口消失 | `01-home` |
| 复习中心「待复习」空态（无计划时给引导，不是空白列表） | `02-review-pending` |
| 笔记内「复习」面板 →「开启复习」后计划与 REVIEW 提醒**同源落库**（`sourceId` = 计划 id） | `04-review-sheet` / `05-plan-started`；DB `review_plans: 1\|6\|1\|0\|…`、`reminder_instances: 1\|1\|1\|6\|…\|1` |
| 队列分区（`接下来 · 1`）+ 阶梯进度点 + 档位文案 | `06-review-queue` |
| 提醒档三档筛选（待处理 / 已逾期 / 已完成），REVIEW 行**只读**并给「去待复习」引导 | `07-reminders` / `22-reminder-meta` |
| 首页铃铛 / 设置页「复习中心」/ 通知深链都落「提醒」档（先切「待复习」再点铃铛，仍落提醒档） | `08-bell` / `09-bell-to-reminders` / `13-settings` / `14-settings-entry` |
| 搜索标签筛选条（chip 多选 + 纯标签浏览）与命中收窄 | `10-tag-bar` / `11-tag-filter` |
| 笔记卡片标签胶囊 → 搜索的桥梁（AND 收窄为 1 条，且**未误开笔记**） | `12-pill-bridge` |
| 双主题 × 明暗四象限无破损 | 动森浅色 `01`–`12`；纸墨深色 `16` / `17`；纸墨浅色 `18` / `21`；动森深色 `23` / `24` |

### 10.5 真机暴露并修复的两处缺陷

两处都是设计稿没预见到、只有真机跑起来才显形的：

1. **「明天到期」显示成「后天」**（`ReviewPlanRow.daysUntil`）
   原实现按小时**向上取整**。下午 16:06 开启的 1 天间隔计划，到期时间是「明天 16:06」，
   距今天 0 点是 1 天又 16 小时 → 向上取整得 2 天 → 文案写成「后天」。
   改为**自然日差**（`(due - startOfToday) / DAY`）。`upcoming` / `later` 区的到期时间必定
   ≥ 明天 0 点，整除结果最小就是 1，不会掉出「明天」档。
   护栏：`ReviewPlanRowTimeLabelTest`（8 例）。
   > 复验用原生数据：同一个计划（`nextReviewAt = 1791533184344` = 2026-10-09 16:06）
   > 修复前显示「后天」，修复后显示「明天」—— `21-review-tomorrow`。

2. **REVIEW 提醒行的 meta 重复了笔记标题**（`ReminderRow`）
   REVIEW 行的主标题**就是**笔记标题，而 meta 又拼了一遍同样的文本：
   `第 1/5 次复习 · GBN vs. Selective Repeat: … · 2026-10-09 16:06`。
   改为「标题已经吃掉笔记标题时不再重复」（`noteTitle?.takeIf { it != title }`），
   顺带把写死的 `/5` 换成 `ReviewSchedulePolicy.defaultStepCount` —— 档位总数此前在
   复习中心与提醒行各有一份，改阶梯长度会让文案悄悄说谎。
   > 复验：meta 变成 `第 1/5 次复习 · 2026-10-09 16:06` —— `22-reminder-meta`。

### 10.6 未纳入本次改动

- 「待办」不单独开档（按 **P5** 拍板，留到阶段二），待办提醒仍在提醒档里处置；
- 复习中心的「复习会话」入口复用笔记内的复习面板，不新建独立会话页。
