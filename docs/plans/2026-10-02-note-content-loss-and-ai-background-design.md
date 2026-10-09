# 笔记正文丢失 · 助手输入区重排 · AI 生成后台运行 —— 设计说明

日期：2026-10-02
状态：已实现，构建 / 单测 / Lint 全绿（见文末验证结论）

## 背景

这一轮同时报了五个问题，其中 ②③④ 是同一件事的不同切面：

1. 聊天区 AI 消息与输入法之间空隙太大，多行生成文本显示不清，参考主流对话应用的输入框样式重排；
2. 修改笔记的知识点标题，原有正文直接丢失；
3. AI 生成的内容「收藏」之后，正文丢失，只剩标题；
4. 推测根因是「知识点标题与笔记第一条标题不一致」导致内容被清空，要求排查修复；
5. AI 生成过程要支持后台运行。

排查后确认：**②③ 与 ④ 的推测无关**。真正的根因是笔记正文有两个互斥的存放位置，
而写侧只认其中一个；④ 里观察到的「标题不一致」是另一个独立现象（卡片标题继承笔记标题），
不是清空的原因。下面按根因分开写。

---

## 一、②③：笔记正文为什么会「整段消失」

### 现象

- 在笔记详情里改一次知识点标题 → 正文全没了；
- 在笔记列表里对 AI 生成的笔记点「收藏」 → 正文全没了，只剩标题；
- 助手页里，AI 自己也会说「**该笔记当前正文为空（无内容块）**」，然后提出「需要我把内容重新写入吗」。

三条现象指向同一件事：**某条写路径把这篇笔记的块写成了空表**。

### 根因：`Note.blocks` 与 `Note.cards[].blocks` 是互斥的双来源

从数据库读出来的笔记，`NoteRepositoryImpl.hydrate()` **只会填两处中的一处**：

```kotlin
val cardDomains = if (cards.isEmpty()) emptyList() else { /* 按卡片分组块 */ }
val fallbackBlocks = if (cards.isEmpty()) blocks else emptyList()
return NoteMapper.toDomain(entity, fallbackBlocks, cardDomains, tags)
```

于是：

| 笔记形态 | `note.blocks` | `note.cards[i].blocks` |
|---|---|---|
| 有知识卡片 | **空** | 有内容 |
| 没有卡片 | 有内容 | 空 |

**两者互斥，永远不会同时有值。** 而 `NoteRepositoryImpl.replaceBlocks()` 此前写的是：

```kotlin
val sourceBlocks = note.blocks      // ← 有卡片的笔记在这里读到空表
```

只要有一处「读出来 → 改一个字段 → 写回去」的往返，正文就被这次写操作删掉。命中的路径有三条：

| 入口 | 代码 | 现象 |
|---|---|---|
| 列表「收藏」 | `HomeViewModel.setFavorite` → `noteRepository.updateNote(note.copy(isFavorite = favorite))` | 问题 ③ |
| AI 改标题 | `AiToolExecutor.updateNote` → `existing.copy(title = ...)` | 问题 ②（改知识点的场景） |
| AI 改标签 | `AiToolExecutor.addTags` → `existing.copy(tags = tags)` | 同类隐患 |

而 `updateNote()` 是「标题/卡片/块/标签」全量覆盖式落库，所以哪怕只改一个布尔值，块也会被一起重写。

**为什么编辑器里改正文却没问题？** 因为编辑器不走这条路径：`NoteUiState.toNote()` 会
**总是**重建 `cards` 并把所有块摊平进 `blocks`，两条路径都非空，恰好绕开了这个坑。
这也解释了为什么这个 bug 能活这么久——日常编辑路径完全正常，只有「不经编辑器」的写路径会踩中。

### 方案：给「取正文」定一个唯一入口

在 `Note` 上加一个派生属性，把双来源的知识收在一处：

```kotlin
val contentBlocks: List<Block>
    get() = if (blocks.isNotEmpty()) blocks else cards.flatMap { it.blocks }
```

- **写侧**：`NoteRepositoryImpl.replaceBlocks()` 改读 `note.contentBlocks` —— 这是 ②③ 的直接修复；
- **读侧**：所有「把正文交给别人看」的地方统一改用它，顺带修掉一批隐性错误：
  - AI 的 `formatNote` / `snippetOf` / 引用上下文：此前把有卡片的笔记读成空，于是模型会一本正经地回复「正文为空」；
  - `NotePreview.notePreviewText()`、`NoteRow.noteMetaParts()`：列表摘要与「图 N / 链接 N」计数对有卡片的笔记恒为空/为 0；
  - `JsonExporter` / `ImportKnowledgeUseCase` / `SettingsViewModel`（清理孤儿媒体）/ `NoteViewModel.shareNote()`（分享计数）。

共改动 12 个读写点。**保留** `cards.ifEmpty { note.blocks }` 这类写法（三个导出器、
`NoteViewModel.load()`）：它们本来就是「没有卡片才回退到 blocks」，与互斥语义一致，不是 bug。

### 顺带说明：④ 的「标题不一致」（2026-10-09 已修复）

用户观察到「新增知识点时，标题取的是笔记标题」，这与清空无关，但确实是个独立的小毛病。
**已修复**——根因不是 `knowledgeCardsFromBlocks`，而是 `NoteViewModel` 里一套「笔记标题 ↔
当前卡片标题」的双向同步：`onTitleChange` 把笔记标题写进**当前卡片**（「新增知识点 →
改笔记标题」这条操作流里 currentCardId 正好指向新卡）、`onCardTitleChange` 把卡片标题
回写笔记标题、`ensureCurrentCardExists` 的兜底卡也带 `title = state.title`，三条路径一起制造了「继承」。三处全部移除后，
笔记标题与卡片标题**完全独立**（唯一保留的复制是隐式单卡**诞生时**取一次笔记标题当初始值，
那是快照不是持续同步）。回归用例：`NoteViewModelKnowledgeCardTest` 的
`noteTitleChangeDoesNotLeakIntoAnyCard` / `cardTitleChangeDoesNotOverwriteNoteTitle`。

---

## 二、①：输入框与键盘之间的那块空白

### 现象与测量

真机截图里，输入框与键盘之间空出一块**几乎与键盘等高**的空白（1080×2400 屏上约 600px）。
这不是「留白偏大」，是**同一份 IME inset 被消费了两次**。

### 根因：`adjustUnspecified` 被系统解析成 `adjustPan`

`AndroidManifest` 里没有声明 `windowSoftInputMode`，Activity 也没有可滚动的原生 View
（Compose 对框架而言只是一个 `AndroidComposeView`），于是系统按 `adjustUnspecified` 的规则
把它解析成了 **`adjust=pan`**。在设备上用 `dumpsys window windows` 可以直接读到：

```
mAttrs={... sim={adjust=pan forwardNavigation} ty=BASE_APPLICATION ...
```

`adjust=pan` 的语义是「由框架把窗口表面整体上推，让焦点可见」——
而 `AssistantInputBar` 自己又写了 `Modifier.imePadding()`。两次上推叠加，就空出一块键盘高度的白。

### 方案

1. **声明 `android:windowSoftInputMode="adjustResize"`**（Activity 级）。
   `adjustResize` + `setDecorFitsSystemWindows(window, false)` 是官方推荐的组合：
   API 30+ 上窗口不再被 resize（`dumpsys` 已确认键盘弹出时 `mFrame` 保持全屏）、
   也不会被 pan，只派发 IME insets；API 30 以下 `WindowInsetsCompat.Type.ime()` 本就不提供，
   `imePadding()` 自动是空操作，由系统 resize 兜住。**不需要版本分支**。
2. **IME 在根层 `AppShell` 收口一次**：整棵树只缩一次，各页不必各自 `imePadding()`。
   这与 API 30 以下「窗口被 IME 顶掉一块」的原生行为一致，于是「别的页面要不要处理键盘」
   这个问题被消掉了——笔记编辑器的输入块、设置页的配置项自动都在键盘之上。
   相应地，`AssistantInputBar` 里原有的 `imePadding()` **删掉**（否则又是一次双计）。

   为什么不是「各页各自 imePadding」：那要逐个页面排查（编辑器、设置、搜索…），
   而根层一次就等价于系统 resize 的原生语义，改动面反而更小。

### 输入区重排（参考图二）

旧结构是「`＋` 图标 + `OutlinedTextField` + 圆形发送」三块并列，文本区被压缩成
`ZhiLuType.bodySmall`（14sp）+ `maxLines = 4`，多行提示词几行就糊在一起；
`OutlinedTextField` 还自带聚焦态描边，视觉上是很重的「表单」。

新结构把**附件预览、正文、工具行收进同一个圆角面**：

```
┌─ Surface(surface + hairline, Radius.Composer = 22dp) ──┐
│  [图片缩略图 …]                                        │
│  [引用 chip …] / [文件行]                              │
│  正文：BasicTextField，ZhiLuType.body(16sp/行高26sp)    │
│        maxLines = 6                                    │
│  ＋(附件菜单)                        [发送 / 停止]      │
└────────────────────────────────────────────────────────┘
```

要点：

- 正文改用 `BasicTextField` + `ZhiLuType.body`，行高从 22sp 放宽到 26sp、可见行数 4 → 6，
  直接对应「多行文本显示不清」；
- 容器用 `surface` 底色 + `outlineVariant` 发丝边（不是 `outline` 描边），
  在暖纸底上读作「一块面」而不是「一个输入框」，色值全部来自既有语义 token，深浅色自动跟随；
- 附件入口保持单图标 + 菜单（图片 / 文件 / 引用笔记），工具行只留 `＋` 与发送两枚；
- **生成中把「发送」换成「停止」**——见下一节，任务能活到页面之外以后，需要一个随时可点的出口。

`Radius` 新增语义别名 `Composer = 22dp`（比 `Sheet` 大一档）：容器里同时装附件、多行正文和工具行，
圆角要大到让「一整块面」读得出来，小了就会和正文段落块混在一起。

---

## 三、⑤：AI 生成支持后台运行

### 现状

`AiTaskManager` 已经是进程级单例，生成协程跑在独立于 ViewModel 的 `CoroutineScope` 上，
**切页面不会中断**。缺的不是「切页不中断」，而是：

- 用户离开应用（切走 / 锁屏）后，进程降级为后台进程，随时可能被回收 → 生成中断；
- 生成过程在应用之外完全不可见，用户不知道还要等多久、是不是已经失败。

### 方案：前台服务 + 通知

```
AiTaskManager.state (唯一事实源)
      │  init { scope.launch { _state.collect { taskHost.sync(it) } } }
      ▼
AiTaskHost  ← 接口，让任务管理器不依赖 Android API
      ▼
AndroidAiTaskHost
   ├─ 有活跃任务 → ContextCompat.startForegroundService(AiTaskService, 标题/正文/任务id)
   └─ 无活跃任务 → stopService + （应用在后台时）补一条完成/失败通知
                            ▼
                     AiTaskService : @AndroidEntryPoint
                        onStartCommand → startForeground(常驻通知)
                        ACTION_CANCEL  → taskManager.cancel(taskId)
```

关键设计点：

- **状态同步收在一处**：不在每个 `_state.update` 后面手动补通知（漏一处就会出现「进度不动了」），
  而是让 `init` 里的一条收集协程做唯一出口。
- **完成通知按前后台分流**：`AppForegroundTracker` 用单 Activity 的 started/stopped 判断
  （不用 `ActivityManager.getRunningAppProcesses()`，那个在新版本上只能看到自己进程）。
  用户就停在会话页时结果已经扑面而来，再弹通知是打扰。
- **通知 channel `zhilu_ai_tasks`，IMPORTANCE_LOW**：不发声不震动、不显示角标。
  它是「正在进行」的状态，和提醒（`zhilu_reminders`，DEFAULT）不是一类东西。
- **通知带「停止」按钮**：`PendingIntent.getService` → `ACTION_CANCEL` → `AiTaskManager.cancel()`。
  `cancel` 语义是**把任务从表里移除**，而不是标成「已取消」——任务表同时是目标占用锁与
  流式气泡的数据源，移出即两者一起收干净。
- **`startForegroundService` 失败不抛给上层**：Android 12+ 禁止从后台启动前台服务，
  真被拦下来只影响通知保活，生成本身照跑，所以 `runCatching` + 日志即可。

清单新增：`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_DATA_SYNC` 权限，
`<service android:foregroundServiceType="dataSync">`（targetSdk 34+ 必需）。
通知点击落回助手页：`AiTaskNotifications.EXTRA_OPEN_ASSISTANT` → `MainActivity.routeFromIntent`。

### 顺带的小重构

`toolNameLabel`（工具名 → 「读取笔记」等中文）原本是 `ui/assistant` 里的 `internal fun`。
现在通知也要用同一份文案，把它移到 `ai/AiToolLabels.kt`，三个调用点（`AssistantScreen`、
`AiMessageBubble`、`GlobalAiStatusBar`）改 import，避免让通知去 import 界面代码。

### 未做（有意留下）

- **任务表只增不减**：已完成的任务仍留在 `AiTaskState.tasks` 里（每个还带着整段 `streamText`）。
  本轮没动它——改动面涉及 `AssistantViewModel` 的错误去重逻辑，与本次需求无关；
- **进程被杀后的续传**：`START_NOT_STICKY`，服务不重启、任务不恢复。
  「后台运行」定义为「用户离开应用期间不中断」，不含「进程被系统杀掉后接着跑」。

---

## 验证结论

### 静态检查

`./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 三条全绿
（62 actionable tasks）。本轮没有新增/删除测试用例：`contentBlocks` 是既有语义的收敛，
上面列的 12 个读写点原本没有单测覆盖；改动集中在 UI 与平台层，不适合 Robolectric。

### 实机（MuMu，Android 12 / API 32，设备 `127.0.0.1:16384`）

**数据库现状先坐实了这次事故**——`notes` 里出现了「有卡片但一个块都没有」的笔记：

| note id | 标题 | cards | blocks |
|---|---|---|---|
| 2 | 红黑树 | 5 | **0** ← 正文已被此前某次 copy() 往返删除 |
| 3 | 二次型（Quadratic Form）知识点全梳理 | 2 | 44 |
| 4 | 408 计网｜信道利用率计算全梳理 | 1 | 30 |
| 5 | REST API Design Best Practices | 0 | 11 |

（笔记 2 的正文在这个 bug 存活的期间就已经被删掉了，属于**已发生的数据丢失，无法从库里恢复**，
本轮只保证不再发生。如果那篇笔记的内容对你重要，可以让 AI 依据历史对话重新写入。）

**问题 ③（列表收藏）** 取笔记 3 实测 —— 收藏前 `(isFavorite=0, cards=2, blocks=44)`，
点一次「收藏」后 `(1, 2, 44)`，取消收藏后 `(0, 2, 44)`：**两次全量落库都没再碰到正文**。
修复前这一列会掉到 0。

**问题 ②（AI 改笔记）** 让助手对笔记 3 执行一次 `add_tags`（与 `update_note` 同一条
「读出来 → 改字段 → 写回去」路径）：标签写入成功（新增 `testtag`），而
`(isFavorite=0, cards=2, blocks=44)` 保持不变。同时 AI 的回复里正常列出了笔记的现有标签，
**不再出现「该笔记当前正文为空」**——读侧同样生效。

**问题 ⑤（后台运行）**：
- 生成中 `dumpsys activity services` 显示
  `com.example.zhilu/.ai.AiTaskService`、`startForegroundCount=1`、`isForeground=true`、
  `foregroundId=4101`、`channel=zhilu_ai_tasks`、`actions=1`（「停止」）；
- 按 HOME 把应用退到后台后服务仍在（`isForeground=true`），通知栏记录 `flags=0x6a`
  = ONGOING + NO_CLEAR + ONLY_ALERT_ONCE + FOREGROUND_SERVICE，`contentIntent` 指向 Activity；
- 任务在后台结束后服务自动停止，并补发完成通知（id=4102，标题「AI 生成完成」，
  正文是 AI 对笔记 3 的摘要），`seen=false`——正是「用户没看着应用时告诉他一声」的预期行为。

### 未能验证的部分（如实说明）

**IME 几何无法在本机复现**：MuMu 只带一个 Sogou 占位输入法，`mInputShown=true` 但
`mFrame=[0,1080][1920,1080]`（高度 0）、`mInsetsHint=bottom 0`，键盘高度恒为 0，
所以「输入框紧贴键盘、中间不留白」这一步没有实测。依据是两条可读证的证据：

1. `dumpsys window windows` 读到的窗口属性是 `sim={adjust=pan}` —— 现象与
   「框架上推一次 + imePadding 再推一次」的叠加量吻合（输入框被抬到约两倍键盘高度处）；
2. `adjustResize` + `setDecorFitsSystemWindows(false)` + `imePadding()` 是官方推荐组合，
   且同一份 dumpsys 已确认键盘弹出时应用窗口 `mFrame` 保持全屏（不会被 resize）。

在有真实输入法的设备上复核时，重点看两处：输入框下沿是否紧贴键盘上沿、
顶部「AI 助手」标题栏是否仍在（旧行为下它会被 pan 顶出屏幕）。

### 测试数据清理

实机验证产生的数据已还原：笔记 3 的标签回到原有 4 个、`testtag` 与其关联记录删除、
测试会话（conversations id=16 及其 6 条消息）删除、进程重启后通知栏已清空。
`notes.updatedAt` 因收藏往返被刷新一次，无法回退（无副作用）。

