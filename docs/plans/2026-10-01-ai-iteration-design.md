# 知录 AI 功能迭代设计方案

> 状态：待实施。目标是在既有 AI 助手（对话 + Function Calling + 多模态识图）之上，补齐「体验、异步化、全局状态、生成占用、引用」五项能力。

## 0. 需求清单

| # | 需求 | 一句话结论 |
|---|---|---|
| 1 | 聊天框与 AI 输出（文本 / 工具调用 / 代码）美化 | 代码块加复制、工具徽章友好化、引用块加边线、输入栏加引用 chips、流式光标/思考态 |
| 2 | 生成过程异步化，切页不中断 | 生成任务从 `viewModelScope` 迁到**进程级 `AiTaskManager`** |
| 3 | 状态栏显示 AI 任务进行状态 | 根层 `AppShell` 挂全局 AI 状态条，跨页可见 |
| 4 | 知识点框被占用时「生成中」动画 + 禁点 | 由 `AiTaskManager.lockedRefs` 驱动卡片 shimmer + 禁用交互 |
| 5 | 知识点 / 块增加「引用」功能 | 新增 `AiRef` 模型 + 跨页引用交接 + 上下文注入 LLM |
| 6 | 被引用且生成中的知识点/块显示生成态防误点 | 同 #4，锁的粒度覆盖 NOTE / CARD / BLOCK |

## 1. 现状问题（根因）

- `AssistantViewModel.sendMessage()` 用 `viewModelScope.launch` 承载整个生成协程；页面离开（切 tab / 回退）即触发 `ViewModel.onCleared` 取消协程，**流式生成被中断**，未落库的回复丢失。
- 工具调用状态、流式文本、生成占用等状态都锁在 `AssistantViewModel` 内部，其它页面（首页、笔记编辑页）无从感知。
- AI 输出渲染（`AiMessageContent`）已有代码块 / LaTeX / 标题 / 列表 / 引用 / 行内格式，但缺「代码复制、引用块边线、友好工具名、思考态」等细节。

## 2. 总体架构

新增一个**应用级（进程级）编排层**，作为「AI 任务的唯一事实源」：

```
┌────────────────────────────────────────────────────────────┐
│  UI 层（Compose）                                            │
│   AppShell ── 全局 AI 状态条（观察 AiTaskManager.state）        │
│   AssistantScreen/VM ── 提交任务 + 观察任务状态 + 渲染流式        │
│   NoteEditScreen / Home ── 观察 lockedRefs 渲染生成态 + 禁用     │
├────────────────────────────────────────────────────────────┤
│  应用编排层 com.example.zhilu.ai（@Singleton，进程级）           │
│   AiTaskManager  ── 持有独立 CoroutineScope，跑生成协程          │
│                     StateFlow<AiTaskState>（任务列表 + 锁）      │
│   AiRefManager   ── 跨页「待附加引用」交接（StateFlow）           │
├────────────────────────────────────────────────────────────┤
│  domain / data 层（复用，不改动分层语义）                        │
│   AiAssistantRepository（新增 contextText 上下文入参）           │
│   AiConversationRepository / NoteRepository / UserPreferences   │
└────────────────────────────────────────────────────────────┘
```

**关键决策**：生成协程的生命周期从「页面」提升到「进程」。`AiTaskManager` 用 `SupervisorJob() + Dispatchers.IO` 的独立 scope（不依赖任何 ViewModel），App 不退出任务就不中断。

## 3. 数据模型

### 3.1 任务模型 `AiTask` / `AiTaskPhase`

```kotlin
enum class AiTaskPhase { QUEUED, RUNNING, TOOL_CALLING, STREAMING, SUCCEEDED, FAILED }

data class AiTask(
    val id: String,             // UUID
    val conversationId: Long,   // 所属会话（create 后回填）
    val phase: AiTaskPhase,
    val toolName: String? = null,   // 当前工具（TOOL_CALLING 阶段）
    val streamText: String = "",    // 流式累积文本
    val refs: List<AiRef> = emptyList(),  // 引用的目标（驱动锁）
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isActive: Boolean get() =
        phase == QUEUED || phase == RUNNING || phase == TOOL_CALLING || phase == STREAMING
}

data class AiTaskState(val tasks: List<AiTask> = emptyList()) {
    val activeTasks: List<AiTask> get() = tasks.filter { it.isActive }
    /** 被占用的目标集合（去重），供 UI 判断「生成中」。 */
    val lockedRefs: Set<AiRef> get() = activeTasks.flatMap { it.refs }.toSet()
}
```

### 3.2 引用模型 `AiRef`

```kotlin
enum class AiRefKind { NOTE, CARD, BLOCK }

data class AiRef(
    val kind: AiRefKind,
    val noteId: Long,
    val cardId: Long? = null,
    val blockId: Long? = null,
    val title: String = ""        // 展示用（笔记标题 / 卡片标题 / 块摘要）
) {
    /** 判断该引用是否命中某个「笔记 / 卡片 / 块」。 */
    fun matches(noteId: Long, cardId: Long? = null, blockId: Long? = null): Boolean
}
```

- 引用**不落库**（只作为一次请求的上下文与展示 chip），避免为「临时关联」引入新表与迁移。
- `AiRef` 作为 `data class` 天然支持 `Set` 去重与相等比较，锁集合直接基于它。

## 4. 核心模块职责

### 4.1 `AiTaskManager`（进程级，@Singleton）

职责：**提交、执行、跟踪 AI 生成任务**。

```
submit(request: AiSubmitRequest)          // 非挂起：立即入队，返回 taskId
  ├─ 生成 taskId，phase=QUEUED，入 state
  └─ scope.launch { run(taskId, request) }

run(taskId, request):
  1. RUNNING：解析 title → ensureConversation（create 或复用 request.conversationId）→ 回填 task.conversationId
  2. 持久化 USER 消息（含 refs 摘要标题）
  3. resolveRefContext(request.refs) → 拼「引用上下文」文本
  4. TOOL_CALLING / STREAMING：调 generateReply(config, history, onDelta, onToolEvent, contextText)
       onDelta → 累积 task.streamText（StateFlow 更新，UI 流式可见）
       onToolEvent → task.toolName 更新 + 持久化一条 TOOL 消息
  5. SUCCEEDED：持久化最终 ASSISTANT 消息
  6. FAILED：task.error 记录（输入内容回显由 VM 用原 text 恢复）
```

对外暴露：
- `val state: StateFlow<AiTaskState>`（任务列表 + lockedRefs）
- `val scope` 私有，与 ViewModel 无关

### 4.2 `AiRefManager`（@Singleton）

职责：**跨页「待附加引用」交接**（笔记页 → 助手页）。

```
val pendingRefs: StateFlow<List<AiRef>>
fun addPending(ref: AiRef)      // 笔记页点「引用到 AI」时调用，然后导航到助手
fun consumePending(): List<AiRef>  // 助手 VM 拉取并清空
```

### 4.3 `AssistantViewModel`（瘦身）

从「承载生成」改为「提交 + 观察 + 渲染」：

- 持续 `flatMapLatest` 观察当前会话的 `getMessages()`，作为**消息列表唯一来源**（不再手动 append）。
- 观察 `AiTaskManager.state`，派生：
  - `activeTask`（当前会话的活跃任务，若无则为 null）
  - `isGenerating` / `toolStatus` / `streamingText`（从 `activeTask` 计算属性派生）
- 观察 `AiRefManager.pendingRefs`，吸附为 `attachedRefs`。
- `sendMessage()`：校验 → `taskManager.submit(...)` → 清空输入与附件/引用。
- 会话 id 的「新建回流」：当观察到活跃任务 `conversationId` 非空且当前会话为空时，自动采纳该会话 id。

### 4.4 UI 层

| 模块 | 改动 |
|---|---|
| `AppShell` | 根层观察 `AiTaskManager.state`，有活跃任务时顶部渲染 `GlobalAiStatusBar`（转圈 + 「AI 生成中 · {工具}」+ 点击跳助手） |
| `AssistantScreen` | 消息列表末尾，当 `isGenerating` 时追加合成流式气泡；`ToolStatusBar` 保留为页内精简提示 |
| `AssistantInputBar` | 新增「引用」入口 + 引用 chips 行（可删除） |
| `KnowledgeCardItem` | 新增 `isGenerating` 参数：shimmer 覆盖层 + `clickable(enabled=false)` |
| `CardBlockList` | 传入块级 `generatingBlockIds`，块显示脉动边 + 禁用交互 |
| `NoteEditScreen` | 观察 lockedRefs，算每卡/每块生成态；卡片头与块菜单加「引用到 AI」入口 |
| `HomeNoteItem` | 笔记级生成态提示（可选，浅度） |

## 5. 状态机与交互流程

### 5.1 任务状态机

```
QUEUED ──▶ RUNNING ──▶ TOOL_CALLING ◀──┐（多轮工具）
                         │             │
                         └──▶ STREAMING─┘
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
                SUCCEEDED                FAILED
```
`isActive` 覆盖 QUEUED/RUNNING/TOOL_CALLING/STREAMING，四种都是「生成占用中」。

### 5.2 引用 → 提问流程（#5）

1. 笔记编辑页，卡片头「引用」按钮 或 块长按菜单「引用到 AI」→ `AiRefManager.addPending(ref)` → `navController.navigate("assistant")`。
2. 助手 VM 吸附 pendingRefs → 输入栏显示引用 chip（带类型图标 + 标题）。
3. 用户输入指令 → `sendMessage()` → `taskManager.submit(refs=...)`。
4. 任务执行时 `resolveRefContext` 读取真实内容注入 LLM 上下文 → LLM 可 `get_note/update_note` 精准操作。
5. 任务激活期间，被引用目标进入 `lockedRefs` → 目标卡片/块显示生成态并禁点。

### 5.3 生成占用锁（#4/#6）

- 锁来源 = 活跃任务的 `refs` 集合（用户显式引用即占用）。
- 笔记页对每张卡 / 每个块调用 `AiRef.matches(...)` 判断是否命中锁定集合。
- 命中 → `KnowledgeCardItem(isGenerating=true)`：卡片出现 shimmer 覆盖 + 边框主色脉动 + 整卡禁用点击；块级同理（在 `CardBlockList` 层给命中块加脉动边 + 禁用交互）。

## 6. 聊天 UI 美化（#1）

| 位置 | 现状 | 迭代 |
|---|---|---|
| 代码块 | 纯底 + 语言标签 | 加「复制」按钮；语言 chip；等宽字；顶部工具条 |
| 工具徽章 | 「调用了 xx」 | 友好中文名映射（list_notes→读取笔记列表…）+ 进行中的小 spinner |
| 引用块 | 斜体缩进 | 左侧主色边线 + 弱化底 |
| 输入栏 | 图片/文件 chips | 增加「引用」chips（类型图标 + 标题 + 删除） |
| 流式 | 光标 `▍` | 空文本时显示「思考中」三点动画，有文本时尾随光标 |
| 状态提示 | 页内 ToolStatusBar | 升级为根层全局状态条 + 页内精简提示 |

## 7. 兼容与边界

- 不新增 Room 表、不改 `Note`/`Block` 模型、不改导航路由（引用跳转复用 `Destination.Assistant`）。
- `AiTaskManager` 是进程级单例，App 进程存活即任务存活；进程被杀（系统回收）不承诺恢复——与既有 Reminder/WorkManager 策略一致，属可接受边界。
- `generateReply` 仅新增带默认值的 `contextText` 参数，向后兼容既有调用。
- 引用不落库，历史会话回看不含引用 chip（引用只在当次请求生效）。

## 8. 实施顺序

1. 领域模型 `AiRef` / `AiTask` / `AiTaskState` + `AiRefManager` + `AiTaskManager`。
2. `AiAssistantRepository.generateReply` 加 `contextText`。
3. `AssistantViewModel` 重构（提交 + 观察）。
4. `AppShell` 全局状态条。
5. 笔记页生成态 + 引用入口。
6. 聊天 UI 美化。
7. `assembleDebug` / `lint` 全绿。

## 9. 实机验证（MuMu 模拟器，Android，1920×1080）

验证环境：`127.0.0.1:16384`，真实 AI 配置（qwen provider + dashscope compatible-mode），Debug APK 直装。

### 6 项需求验收结果

| 需求 | 结论 | 实测依据 |
|---|---|---|
| 1 聊天美化 / 输出格式化 | ✅ | 代码块「语言 chip + 复制 + 等宽 + 横向滚动」、工具徽章、三点思考动画、流式光标 `▍`、**Markdown 表格**均正常渲染 |
| 2 异步生成不中断 | ✅ | 发送后立即切「笔记」tab，任务继续运行，回到助手页可见完整回答 |
| 3 全局状态提示 | ✅ | 顶栏状态条显示「AI 生成中 · 读取笔记」并随工具名变化，点击可跳助手，任务结束自动消失 |
| 4 知识点生成态 | ✅ | 被占用卡片显示「生成中」徽章 + 脉动边框，点击被禁用 |
| 5 引用功能 | ✅ | 卡片头「引用到 AI」→ 跳助手 → 输入栏上方出现引用 chip → 提问后 AI **真实改写笔记**并补标签 |
| 6 引用目标生成态 | ✅ | 被引用卡片在生成期间同 4 项表现，引用入口自动隐藏 |

### 验证中修复的缺陷

1. **全局状态条压住各页顶栏**：原为根层浮层叠加。改为 `Column[状态条, Box(weight 1f){Scaffold}]` 占位布局；状态条出现时对下方子树 `consumeWindowInsets(WindowInsets.statusBars)`，避免 `AppTabScaffold` 的 `safeDrawing` 二次让位。
2. **Markdown 表格未渲染**（原样显示 `| a | b |` 管道符）：`AiMessageContent` 新增 `MdBlock` 归约 + `MarkdownTable`（表头主色淡底加粗 / 斑马纹 / 行分隔线 / 定宽单元格 + 横向滚动）。
3. **长回答末尾被推出屏幕**：原 `animateScrollToItem(最后一条)` 只把该条**顶部**对齐视口。改为列表末尾新增 `bottom-anchor` 锚点项，滚到锚点下标即滚到底部。
4. **流式输出不跟随**：自动滚动 `LaunchedEffect` 的 key 未包含流式文本。改为 key = 流式文本，并以 `canScrollForward` 判定是否贴底 + `autoFollow` 开关（用户主动上翻则暂停跟随，回到底部自动恢复）。
5. **引用标签歧义**：无标题卡片的引用 chip 一律回落笔记标题，多张卡无法区分。改为「卡片标题 → 卡内首个有内容块摘要 → 笔记标题」。
6. **AI 写入笔记残留裸 Markdown**：笔记正文渲染层不解析行首标记与行内加粗，`##`/`**` 会原样显示。已在 system prompt 增加「写笔记用纯文本排版」约定（标题写纯文本、列表用「·」、表格不进笔记块）。

### 已知边界

- `update_note` 多卡片坍缩到首卡的历史局限仍存在，本次未改。
- 进程被杀（系统回收）不恢复进行中的 AI 任务。
- `update_note` 会重建块行，**块 id 会变化**：引用（AiRef）不落库，跨轮无效，不受影响；但如果将来要把块 id 持久化（如块级收藏），需先改成原地更新。

## 10. 行内公式渲染 + 用例深挖（第二轮迭代）

### 10.1 行内 LaTeX 公式

现状缺口：笔记正文与聊天里的行内 `$x$` 只显示带 `$` 的源码，只有显示公式 `$$...$$` 能渲染成图。

实现分层：

| 文件 | 职责 |
|---|---|
| `ui/note/latex/InlineLatex.kt` | `buildInlineLatexText()` 解析行内格式并把公式换成 inline content 占位片段；`rememberInlineLatexContent()` 为每个占位生成 `InlineTextContent`（复用 `rememberLatexImage` 全局缓存） |
| `ui/component/RichText.kt` | 基于 `BasicText(inlineContent = ...)` 的通用富文本组件（Material3 `Text` 不支持 inlineContent） |
| `ui/assistant/AiMessageContent.kt` | 各 Markdown 行分支与表格单元格改用 `RichText`；删除废弃的 `inlineStyle` |
| `ui/note/blocks/ExpandableBlockContent.kt` | `ExpandableText` 由 `Text` 改 `RichText`，笔记正文与聊天共用同一渲染路径 |

**实现要点（踩坑记录）**：Compose 的行内内容**不是**靠「文本里塞占位字符」匹配，而是靠
`androidx.compose.foundation.text.appendInlineContent(id, alternateText)` 写入的字符串注解
（`INLINE_CONTENT_TAG`）定位区间，`inlineContent` map 的 key 即该 **id 字符串**。
最初用私有区字符 `\uE000` 当占位符，编译通过但实机只渲染成缺字方框。
`alternateText` 用公式原文（`$x^2$`），渲染失败或无障碍朗读时都能退化成可读文本。

### 10.2 图片写入笔记（图片能力的真正缺口）

实测「添加图片」链路本身正常（菜单 → 选择器 → chip → 发送 → 视觉模型 OCR 准确）。
缺口在**工具层不支持 image 块**，导致「把这张图存进笔记」做不到。补齐三处：

1. `AiToolExecutor`：`create_note` / `update_note` 的 block `enum` 增加 `image`，`blockTypeFromName` 映射到 `BlockType.IMAGE`；回读笔记时 `formatNote`/`formatBlock` 用 `ImageBlockContent.displayUri` 暴露真实 URI。
2. `AiTaskManager.resolveImageContext`：把本次附图 URI 明确交代给模型（「原样复制，不要改写」）。
3. 取图链路健壮性：`MediaFileManager.persistReadPermission` + `resolveExtension`/`resolveImageMime`；`AssistantViewModel.attachImages` 失败不再静默；选择器缺失时 `runCatching` 提示而不是崩溃。

### 10.3 用例深挖实机结论

| 用例 | 结论 | 依据 |
|---|---|---|
| 块级引用 → 只改该块 | ✅ | DB 校验：仅目标块变化，标题与图片块未动 |
| 引用整篇笔记问答 | ✅ | 正确引用内容，未调用任何写工具，笔记零改动 |
| 多轮上下文 | ✅ | 连续 3 轮正确承接 cited note / cited block |
| 图片附件 + OCR + 写入笔记 | ✅ | OCR 逐字正确；`create_note` 产出 TEXT 块 + IMAGE 块（内部 `file://` URI），详情页图片正常显示 |
| 工具失败 / 越权 | ✅ | 令其改 `id=999`：先 `get_note`，如实回答「不存在，不擅自创建」，无幻觉成功 |
| 生成中跨页 + 全局状态条 | ✅ | 切页后顶栏「AI 生成中 / 查看」正常且不压顶栏 |
| 长回答自动跟随 | ✅ | 流式结束后视口停在底部 |
| 断网失败恢复 | ✅ | 失败后输入内容保留可重发、无助手消息落库；恢复网络重发成功 |
| 历史切换 / 新建对话 | ✅ | 均正常 |

### 10.4 本轮修复的缺陷

**笔记详情页读到旧快照**：`NoteViewModel.load()` 是一次性 `getNoteById` 快照，不跟随 Room 变化。
「笔记页 → 引用到 AI → 让 AI 改写 → 切回笔记页」会看到旧内容（已实测复现）。
修复：新增 `NoteViewModel.refreshIfBrowsing()`（`isEditing || isLoading` 时直接返回，避免覆盖用户正在编辑的内容），
接在两条路径上——① `NoteEditScreen` 的 `ON_RESUME` 观察者（从助手页回来）；
② `observeAiTasks` 用「上次活跃任务 id 集合 → 本次已 SUCCEEDED」的状态迁移判断生成结束。

### 10.5 验收

`./gradlew :app:assembleDebug :app:lintDebug` 全绿；上述全部用例在 MuMu（1920×1080）实机复验通过。

---

## 11. 收尾轮：图片块脱媒治理 + 测试集修复（第三轮迭代）

§10.2 补上了「工具层能写 image 块」，但**只写了裸 URI**——这一轮查真值时发现它留下的隐患，并顺带修复了长期瘫痪的测试集。

### 11.1 缺陷：AI 写进笔记的图片块脱离媒体体系

**现象**：笔记里 AI 写入的图片块 content 是裸 URI（`file:///.../files/images/img_*.png`），`media` 表里没有对应记录。

**根因**：`AssistantViewModel.attachImages` → `MediaFileManager.importUriToInternal` 只把图落到内部存储并返回 `file://` 字符串（对聊天附件这是合理的），但 `AiToolExecutor.parseBlocks` 把模型回填的 URI **原样写进块**，于是这张图既不是聊天附件、也没成为「笔记图片」。

**影响**（三处都已确认成立）：

| 位置 | 后果 |
|---|---|
| `DtkExporter` | `mediaId()` 为 null → `return@forEach` 静默跳过，导出包里丢图 |
| `NoteViewModel.shareNote` → `HtmlExporter` | 媒体筛选收不到该媒体 → `imageToBase64` 抛「未找到媒体」→ 页面显示「[图片不可读]」 |
| 媒体孤儿清理 | 按 `mediaId` 判断归属 → 这类图被误判为孤儿 |

**方案**：

1. **源头治理**：`AiToolExecutor` 注入 `MediaRepository` + `MediaFileManager`，新增 `registerImageContent()`。写入图片块前先登记 `Media`（同 URI 已登记则复用，保证 `update_note` 反复回写不产生重复记录），content 统一写成 `mediaId|uri`；`content://` 先复制进内部存储。无法解析为本地可读图片的块**丢弃并计数**，工具结果里如实回传「有几张因路径不可读被跳过，请勿声称已写入」——避免模型在工具失败时仍然幻觉成功。
2. **消除重复逻辑**：把「`mediaId` 命中 → 否则按内嵌 URI 反查」抽成 `ImageBlockContent.resolveMedia(content, mediaById): Media?`，`DtkExporter` / `HtmlExporter` / `MarkdownExporter` 统一调用（此前三个导出器各写一遍，其中 DtkExporter 还漏了兜底）。
3. **收紧筛选**：`shareNote` 的媒体筛选由 `content.contains(m.id.toString())` 改为 `mediaId(content) == m.id || content.contains(m.uri)`，消除子串误匹配。

**实机验证**：令 AI「把这张图写进新笔记」→ 新笔记块 content = `6|file:///...img_1790866905981_....png`，`media` 表新增 id=6（size 27898）→ 详情页图片正常渲染 → 分享 HTML 产物含 `<img src="data:image/png;base64,`，「图片不可读」出现 0 次。

### 11.2 缺陷：单元测试集长期无法编译

`testDebugUnitTest` 直接 `compileDebugUnitTestKotlin FAILED`。7 个测试文件（Todo / Review / TagSelection / AdvancedBlock / BlockOps / KnowledgeCard / ReadOnly）构造 `NoteViewModel` 时均未传 AI 迭代新增的 `aiTaskManager`、`refManager`。自 §4.1 引入 `AiTaskManager` 起测试就断了，因为日常只跑 `assembleDebug`（**不编译 test 源集**）所以长期无人察觉——等于这段时间没有任何回归保护。

补参后暴露第二个问题：直接 `mockk(relaxed = true)` 会让 `aiTaskManager.state` 成为「永不发射」的假 `StateFlow`，`observeAiTasks` 收集时抛 `KotlinNothingValueException`，**57 个与 AI 无关的用例被连带击穿**。

**方案**：新增 `ui/note/TestAiDoubles.kt` 提供 `fakeAiTaskManager()`（显式 `every { state } returns MutableStateFlow(AiTaskState())`）与 `fakeAiRefManager()`，7 个文件统一改用。

### 11.3 环境约束：Windows 上的 `file://` 不可用

`testDebugUnitTest` 在本机跑在 Windows JVM 上，`Uri.fromFile(file).toString()` 会把 `C:\...` 编码成 `file://C:%5C...`，`Uri.parse().path` **反解为空字符串**，导致 `MediaFileManager.copyToCache` 必然抛 message 为空的 `FileNotFoundException`（表象极像产品 bug）。

因此**依赖真实文件拷贝的导出测试在 Windows 上写不出来**（既有 `MarkdownExporterBase64Test` 就是刻意只测 code 块、不碰图片）。图片相关回归改放纯逻辑层——这也是把兜底逻辑抽成 `ImageBlockContent.resolveMedia` 的额外收益：新增 3 个用例覆盖 mediaId 命中 / 裸 URI 兜底 / 无匹配返回 null。

### 11.4 验收

- `./gradlew :app:assembleDebug`、`:app:lintDebug`、`:app:testDebugUnitTest` 三者全绿，单元测试 **153 个用例 0 失败**（修复前测试源集根本无法编译）。
- 实机：AI 图片写入 → 媒体登记 → 详情页渲染 → HTML 分享内嵌 base64，全链路通过。
- 测试产生的临时笔记/孤儿图片/会话已清理，应用恢复干净状态。
