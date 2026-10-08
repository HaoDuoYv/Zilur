# AI 引用失效 · 全局单任务互斥 · 消息划词与引用回复 —— 设计说明

日期：2026-10-08
状态：已实现（批 0~5），单项测试与全量单测见文末验证结论

## 背景

本轮三个问题，分别落在**上下文注入**、**任务并发**、**消息交互**三块：

1. **引用功能无效果**：引用笔记/卡片/块之后，AI 仍识别不到引用意图；发出去的消息里也
   看不出「引用了什么块」。
2. **会话异步但状态会被重置**：切页面回来，当前任务状态没了；任务还在后台跑，界面却
   允许再发下一条（没有任何互斥）。
3. **聊天消息只能整体复制**：无法选择部分内容复制，也没有「引用某条消息」的能力。

实现前确认了两项产品决策：

| 决策点 | 选择 | 含义 |
|---|---|---|
| 互斥范围 | **全局单任务** | 同一时刻只允许一条生成任务；任何会话生成中，输入栏都显示「停止」 |
| 引用语义 | **引用时冻结快照** | 点引用那一刻把内容格式化为文本冻存，AI 读快照，不按 id 现查 |

---

## 一、问题 1：引用为什么「没效果」

### 现象

- 从笔记页「引用到 AI」、从助手页「引用笔记」选一条，发送后 AI 的回答与引用内容无关；
- 用户消息气泡里只有原文，看不出这条消息引用了什么，也无从判断 AI 到底收到没有。

### 根因：三处叠加

**A) 两套 id 坐标系打架（致命）**

编辑器里的卡片/块 id 是**内存坐标**：新建内容为负数，且 `NoteViewModel.saveInternal`
刻意不回写主键（改了会击穿 LazyColumn 的 key、丢输入焦点）。而引用上下文此前是按 id
**在请求期回查数据库**——对未保存内容必然 miss，引用内容退化成「卡片/块不存在」。

**B) 注入位置错误**

引用内容被拼在**超长 system prompt 的末尾**。模型把那一大段当背景噪音，与用户问题的
关联极弱；真机表现就是「引用了，但它当没看见」。

**C) 全链路没有 refs**

`AiMessage` / `AiMessageEntity` / `AiMessageMapper` / 气泡渲染全都没有引用字段。
于是引用只活在输入框里：一发出去就没了——无法回看、无法在追问轮重放、无从调试。

### 方案

**① 引用快照：内容在「点引用」那一刻冻存**

新增 `domain/ai/AiRefSnapshot.kt`（纯函数，可单测），把笔记/卡片/块格式化为文本：

- `AiRef` 增加 `snapshot: String`（随引用一起走），`AiRefManager` / 助手页 `addRef` /
  笔记页三处 cite 入口都会在**引用时刻**填好快照；
- 头部**不写内部 id**（对模型无价值，负数 id 还会误导它），只写「笔记《X》中的卡片《Y》」
  这类归属上下文；
- 单条快照上限 12 000 字符，超长截断并标注；
- 请求期只保留一层防御回退：快照为空（老数据/异常入口）才按 id 查一次库
  （`AiTaskManager.ensureRefSnapshots`），查不到写明「已不可用」。

**② 注入位置：上下文进 user 消息，与问题同处一条**

新增 `data/ai/AiUserMessageText.kt`（纯函数），组装顺序固定为：

```
【用户引用的知识内容】  ← refs[].snapshot（逐条，总上限 24 000 字符）
【引用的消息】          ← 引用回复的原文（上限 1 000 字符）
<用户问题>              ← 跟在上下文后面，贴近生成位置
【附件内容】            ← 文本附件（保持既有语义，放最后）
```

这是主流形态（ChatGPT 附件、Cursor `@file`、IM 引用回复都是「上下文与提问同处一条消息」）：
关联性强，且**可追溯**——消息落库后，后续追问轮能重放同一条消息的上下文。

`contextText` 从此只承载「本次附带图片的 URI 说明」，system prompt 里不再塞引用。

**③ 引用随消息落库、并在界面上回放**

- `ai_messages` 增加 `refsJson`（引用含快照的 JSON）；用户气泡顶部渲染引用 chips
  （类型 + 标题），历史回看时仍能看出「这条问了什么材料」；
- 请求组装时**所有携带 refs 的历史消息都注入**（不只最新一条）：否则「把它整理成卡片」
  这类追问轮会丢掉源材料。

### 为什么不保留「按 id 现查」

那等于要求「引用目标必须先落库且 id 稳定」。而编辑器的 id 语义本来就是**临时坐标**，
`saveInternal` 又刻意不回写主键。把引用绑到这套 id 上，就是在两套坐标系之间搭一座注定
塌的桥。快照把语义钉在「用户当时看到的内容」上——这正是引用要表达的。

---

## 二、问题 2：任务状态被重置 + 没有互斥

### 现象

- 生成中切到别的页面再回来：任务状态（流式文本、生成中标识）归零；
- 任务明明还在后台跑，输入栏却恢复成「发送」，点一下能再发一条；
- 从通知点回助手页，有时会落到一张全新的空白对话上，正在生成的内容看不见。

### 根因：四处

**A) 任务层没有守卫，UI 层用异步状态做门控**

`AiTaskManager.submit()` 无条件入队；而输入栏的禁用依据 `isGenerating` 是由
`taskManager.state` **异步回填**的派生值——状态没回流的那几帧里它就是 false。

**B) `MainActivity` 把「路由模板」当路由用**

```kotlin
intent.getBooleanExtra(AiTaskNotifications.EXTRA_OPEN_ASSISTANT, false) -> Destination.Assistant.path
```

`Destination.Assistant.path` 是**模板串** `"assistant?prefill={prefill}"`。`onNewIntent`
里直接 `navigate(route)`：字面量 `{prefill}` 会被当参数值填进输入框（真机踩过），
而且不在助手页时会**压入第二个助手页 entry** → 新的 `AssistantViewModel` → 状态整体重置。

**C) 「AI 创建」预填走带参数路由**

`navigateToAssistant(prefill)` 用 `launchSingleTop = false`，每次都是一次新 entry +
新 ViewModel：正在跑的生成、当前会话、输入草稿一起没了。

**D) 当前会话不进 SavedStateHandle**

进程被系统回收再回来，会话与草稿无处恢复。

### 方案

**① 任务层：把裁决点下沉到唯一写入口**

```kotlin
sealed interface AiSubmitResult {
    data class Accepted(val taskId: String) : AiSubmitResult
    data class Rejected(val reason: String, val running: AiTask?) : AiSubmitResult
}
```

`AiTaskManager.submit()` 在 `synchronized(submitLock)` 里**一次完成**「查有没有活跃任务」
与「入队新任务」。任务表只有这一个「新增」写入口，锁在这里才真正闭死并发窗口；
UI 侧的 `isGenerating` 从此只是显示投影，不再承担门控职责。

被拒**不产生任何副作用**：`AssistantViewModel` 保留输入框内容与附件，只弹一句提示
（「正在生成中，先停一下或等它回答完」）——稿子不能因为一次发送失败就消失。

**② 状态层：全局态与本会话态分开**

`AssistantUiState` 只存**一份**任务（`runningTask`，全局唯一），其余全部派生：

| 派生 | 含义 | 用处 |
|---|---|---|
| `isGenerating` | 全局是否有任务在跑 | 输入栏显示「停止」/「发送」 |
| `activeTask` | `runningTask` 且属于当前会话 | 渲染流式气泡、工具状态 |
| `isStreamingVisible` | 本会话是否在生成 | 流式气泡、滚动锚点、工具高亮 |

关键修正：
- 切会话/新建对话**不再手动清 `activeTask`**（旧实现为了不串气泡而清它，顺手把互斥信号
  也清了）。会话切换由派生逻辑天然隔离，互斥信号始终在；
- 新建会话的「回流」（任务解析出会话 id 后把视图带过去）只认**本 VM 提交**的那条任务
  （`pendingOwnTaskId`），否则刚点开空白对话的用户会被别人的任务拽走；
- `currentConversationId` 与输入草稿镜像进 `SavedStateHandle`（集中一处镜像，不散在各写入点）。

**③ 导航层：跨页交接不走路由参数**

- `Destination.Assistant` 去掉 `?prefill=`，助手页路由恢复为无参数；
- 新增 `ai/AiPromptHandoff.kt`（进程级单例，与 `AiRefManager` 同一套路数）承载
  「AI 创建」开场白：**导航退化成一次干净的底栏切换**（`restoreState` 复用原 entry 与
  ViewModel），话术另行送达、用完即清；
- `MainActivity` 的通知跳转改为走 `navigateToAssistant()`（与底栏同一套选项），
  冷启动固定 **home 起栈 + 构图后 push 目标页**（把目标页当 `startDestination` 会让返回键
  从助手页直接退出应用），配置变更重建时不再重复导航。

### 为什么不收敛任务表

任务表还兼着两个职责：`AndroidAiTaskHost` 靠**差集**判断「哪些任务刚结束」来发通知；
`NoteViewModel` 靠「刚成功完成」消费生成结果。删掉已完成任务会同时打坏这两处，
所以选择「完成任务留在表里、但不算活跃（不占锁）」。

---

## 三、问题 3：划词复制 + 引用回复

### 方案

**① 抽出通用的「长按不动弹菜单 / 长按拖动划词」容器**

`ui/component/LongPressSelectableText.kt`：手势三路，顺序不可换——

1. 点一下 → 顺着上冒给祖先 clickable；
2. 长按后拖动 → 交给 `SelectionContainer` 划词（全程不消费）；
3. 长按不动（600ms / 12px 抖动阈值）→ 回调 `onLongPress`，由调用方弹菜单。

笔记页的 `SelectableBlockText` 改为它的委托（菜单条目留在各自页面）；助手页气泡同样接入。
两条老弯路写进注释：把菜单挂最外层会让选区永远起不来；用 `combinedClickable` 的空
`onClick` 会吞掉点击。

**② 气泡：菜单 + 引用块**

- 长按气泡弹菜单：**复制全文** / **引用**；想复制一部分不必找菜单，长按后直接拖即可划词；
- 被引用的消息在气泡顶部渲染成「引用块」（强调竖条 + 来源 + 两行摘要）；
- 引用条在输入区顶部（来源 + 一行摘要 + ✕ 取消），发送前随时可撤。

**③ 引用消息的链路**

```
长按气泡「引用」→ uiState.quotedMessage
  → 发送时 AiSubmitRequest.quotedMessageId
  → 落库 ai_messages.quotedMessageId
  → 请求组装：quotedMessageId 在历史里反查正文 → 【引用的消息】注入 user 消息
  → 渲染：气泡顶部引用块 / 输入条引用条
```

**只存 id、不抄正文**：被引用的消息本来就在同一会话的历史里，抄一份正文只会多一份可能
过期的副本；反查不到（跨会话/被删）就自然降级为「没有引用段」，不会把错误内容塞给模型。

---

## 变更清单

| 层 | 文件 | 说明 |
|---|---|---|
| DB | `AiMessageEntity` / `Migration` / `AppDatabase` | `refsJson TEXT`、`quotedMessageId INTEGER`（可空无默认），v6→v7 |
| 域 | `AiRef`（+`snapshot`/`targetKey`/`distinctByTarget`）、`AiRefSnapshot`（新）、`AiMessage`（+refs/quotedMessageId） | 引用快照与按坐标去重 |
| 数据 | `AiMapper`、`AiAssistantRepositoryImpl`、`AiUserMessageText`（新） | 落库容错、user 消息注入与截断 |
| 任务 | `AiTaskManager` | `AiSubmitResult` + 提交闸门；引用改走 user 消息；快照兜底 |
| 状态 | `AssistantUiState`、`AssistantViewModel`、`AiPromptHandoff`（新） | 全局/本会话派生、SavedStateHandle、开场白交接 |
| 导航 | `Destination`、`AppNavHost`、`AppShell`、`MainActivity`、`BottomBar` | 去 prefill 路由参数、统一跳转选项 |
| 组件 | `LongPressSelectableText`（新）、`SelectableBlockText`、`AiMessageBubble`、`AssistantInputBar` | 手势抽取、气泡菜单/引用块、引用条 |

---

## 验证结论

### 静态检查

```
./gradlew.bat :app:testDebugUnitTest --no-daemon   # 全量单测：BUILD SUCCESSFUL
./gradlew.bat :app:lintDebug                       # 零错误
./gradlew.bat :app:assembleDebug                   # APK 正常产出
```

新增/扩展的测试（全部通过）：

- `AiRefSnapshotTest`（5）：快照抬头、八种块渲染、截断（钉住格式，改渲染先改它）
- `AiUserMessageTextTest`（9）：段落顺序（引用 → 引用消息 → 问题 → 附件）、超限截断、空段不产生
- `AiMapperTest`（5）：refsJson 往返、坏 JSON 容错、空引用落 null
- `AiTaskManagerTest`（4）：已在跑再提交被拒、被拒带回正在跑的任务、停止后可再提交、完成的任务不占锁
- `AssistantViewModelTest`（10）：被拒不吞输入、受理后清空并在新会话解析后回流、别人的任务不被回流、
  切会话不丢任务状态（停止按钮仍在）、失败恢复草稿、SavedStateHandle 往返、开场白交接、
  引用消息随请求提交并收起引用条

### 实机（MuMu，Android 12 / API 32，设备 `127.0.0.1:16384`）

插桩迁移测试真机执行通过：

```
adb install -r app-debug.apk && adb install -r app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.zhilu.data.local.database.MigrationTest \
    com.example.zhilu.test/androidx.test.runner.AndroidJUnitRunner
→ OK (4 tests)
```

其中新增的 `migrate6To7_addsRefsJsonAndQuotedMessageId` 覆盖：v6 建库 → 写入老消息 →
跑 `MIGRATION_6_7` → `runMigrationsAndValidate` 对比 v7 schema → 老行两列为 NULL、正文未动 →
新值可写入并原样读回。

### 未能验证的部分（如实说明）

- **手势与视觉**（长按菜单、划词选区、引用块/引用条/引用 chips 的排版）需要人工在真机或模拟器上
  逐步操作确认，自动化用例覆盖不到；MuMu 只有一个占位输入法，涉及 IME 几何的结论也不适用于它。
- **进程回收后的恢复**（SavedStateHandle）只能靠单测覆盖「句柄读写」这一段，
  系统杀进程再回来的整条链路需要真机验证。
- **AI 回答质量**（模型是否真的"看懂"了引用段落）依赖真实 API Key，本轮用纯函数测试钉住了
  注入形态与顺序，实际效果需真机会话确认。
