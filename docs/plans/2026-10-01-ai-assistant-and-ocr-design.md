# 知录 AI 助手 + OCR 设计方案

> 状态：已实施（P0–P4 全部完成，`testDebugUnitTest` → `lintDebug` → `assembleDebug` 全绿）。端到端真机验证（配真实 Key 对话、发图识图）待用户实测。

## 1. 目标

为知录新增两项能力：

1. **AI 助手（对话式）**——以自然语言对话帮助用户在应用内创建、整理、修改、读取知识点；具备操作应用全部功能的能力（生成笔记、搜索、打标签等）；支持文字 / 图片 / 文件输入；对 AI 输出施加严格约束（LaTeX 语法、Block 结构），生成内容可直接落地为应用内的 `Note` + `Block`。
2. **OCR 文字识别**——图片文字识别，复用多模态大模型的识图能力（不引入传统 OCR SDK），识别结果可直接用于知识点创建与整理。

## 2. 技术选型

| 维度 | 选型 | 理由 |
|---|---|---|
| 网络层 | **OkHttp 4**（新增依赖） | 轻量、支持 SSE 流式；不引入 Retrofit（LLM 调用结构简单，用 kotlinx-serialization 手写 DTO 更可控） |
| 序列化 | **kotlinx-serialization**（已有） | 复用现有依赖 |
| API 协议 | **OpenAI 兼容 `/chat/completions`** | 一套代码兼容 DeepSeek / 通义千问 / 智谱 / Moonshot / OpenAI 等 |
| 流式输出 | **SSE（server-sent events）** | 对话体验必需；OkHttp 手动解析 SSE 流 |
| 多模态 | **`image_url`（base64 data URL）** | OpenAI 兼容协议的视觉输入，OCR 直接复用 |
| AI 配置存储 | **DataStore `UserPreferences`**（已有） | 无需数据库迁移 |
| 工具调用 | **Function Calling** | LLM 输出结构化工具调用，应用执行后回填，实现「AI 操作应用」 |

**明确不引入**：Retrofit、传统 OCR SDK（ML Kit / Tesseract）、Room 新表（MVP 阶段）。

## 3. 架构设计

严格沿用现有 `ui → domain → data` 分层，新增 `ai` 相关包：

```
app/src/main/java/com/example/zhilu/
├── domain/
│   └── ai/                          # 新增：AI 领域层
│       ├── model/
│       │   ├── AiMessage.kt         # 对话消息（role/content/附件）
│       │   ├── AiConversation.kt    # 会话
│       │   └── AiTool.kt            # 工具定义（schema + 执行器）
│       ├── repository/
│       │   ├── AiAssistantRepository.kt     # LLM 对话接口
│       │   └── AiConversationRepository.kt  # 对话持久化接口
│       └── usecase/
│           └── AiToolExecutor.kt    # 工具执行（调 Note/Tag/Media Repository）
├── data/
│   ├── ai/                          # 新增：AI 网络层
│   │   ├── LlmApiClient.kt          # OpenAI 兼容客户端（OkHttp + SSE）
│   │   ├── dto/                     # 请求/响应 DTO
│   │   ├── AiAssistantRepositoryImpl.kt
│   │   └── AiConversationRepositoryImpl.kt
│   └── local/                       # 复用现有 Room 目录
│       ├── entity/                  # + AiConversationEntity / AiMessageEntity
│       └── dao/                     # + AiConversationDao / AiMessageDao
└── ui/
    ├── assistant/                   # 新增：对话 UI
    │   ├── AssistantScreen.kt
    │   ├── AssistantViewModel.kt
    │   ├── AiMessageBubble.kt
    │   └── AssistantInputBar.kt
    └── settings/                    # + AI 配置分区
```

### 分层职责

- **`LlmApiClient`**：纯 HTTP 客户端，只负责「发送 messages + tools → 返回 SSE 流 / 完整响应」。不感知业务。
- **`AiAssistantRepository`**：组装 system prompt、维护工具调用循环、执行流式回调。
- **`AiToolExecutor`**：把 LLM 的工具调用映射为对 `NoteRepository` / `TagRepository` / `MediaRepository` 的实际操作，返回结果文本。
- **`AssistantViewModel`**：持有会话状态、驱动 UI 流式渲染。

## 4. 数据模型

### 4.1 对话消息（持久化，Room v4 → v5）

```kotlin
enum class AiRole { SYSTEM, USER, ASSISTANT, TOOL }

data class AiMessage(
    val id: Long = 0,                        // 持久化主键
    val conversationId: Long = 0,
    val role: AiRole,
    val content: String,
    val images: List<String> = emptyList(),  // 图片 URI 列表（多模态/OCR）
    val fileText: String? = null,            // 附件提取文本
    val toolCallId: String? = null,          // TOOL 消息用
    val toolName: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class AiConversation(
    val id: Long = 0,
    val title: String = "新对话",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

**决策：对话历史持久化保存**（用户确认）。新增两张 Room 表，`AppDatabase` 升到 **v5** 并写 Migration（符合 AGENTS.md 强约束：禁止 `fallbackToDestructiveMigration()`）。

| 表 | 字段 |
|---|---|
| `ai_conversations` | id, title, createdAt, updatedAt |
| `ai_messages` | id, conversationId(FK → conversations, cascade), role, content, imagesJson, fileText, toolCallId, toolName, createdAt |

- 消息按 `conversationId` 建索引；`role` 存枚举名，`images` 序列化为 JSON 字符串。
- domain 层新增 `AiConversationRepository` 接口；data 层实现（`AiConversationDao` + `AiMessageDao` + Mapper）。
- `isStreaming` 等 UI 状态不落库，由 `AssistantViewModel` 的 UiState 持有。

### 4.2 AI 配置（DataStore，复用 `UserPreferences`）

```kotlin
// 新增字段
aiEndpoint: String   // 如 https://api.deepseek.com/v1
aiApiKey: String     // 用户自备
aiModel: String      // 如 deepseek-chat / qwen-plus / glm-4
aiVisionModel: String // 视觉模型，默认同 aiModel（用于 OCR）
```

敏感：`aiApiKey` 存 DataStore（本地明文，与现有主题/提醒设置一致；本地优先应用可接受）。**绝不出现在日志**。

## 5. AI 工具调用设计（核心）

### 5.1 工具集（MVP 六件套）

| 工具 | 参数 | 对应 Repository 操作 |
|---|---|---|
| `create_note` | `title`, `blocks[]`, `tags[]` | `NoteRepository.insertNote` + `TagRepository` |
| `search_notes` | `query` | `NoteRepository.searchNotes` |
| `get_note` | `noteId` | `NoteRepository.getNoteById` |
| `update_note` | `noteId`, `title?`, `blocks[]?` | `NoteRepository.updateNote` |
| `add_tags` | `noteId`, `tags[]` | 查 `Note` + 补 `Tag` + `updateNote` |
| `list_notes` | 无 | `NoteRepository.getAllNotes`（截断返回标题列表） |

### 5.2 Block 结构化 schema（给 LLM）

LLM 通过 `create_note` 的 `blocks` 参数生成结构化内容，直接映射为 `Block`：

```json
{
  "type": "text" | "latex" | "code" | "todo" | "link" | "divider" | "branch",
  "content": "…",
  "language": "…（仅 code）",
  "children": []  // 仅 branch
}
```

- `IMAGE` 类型**不在** `create_note` 工具里暴露（AI 生成图片是另一能力，超出本次范围）。
- `LATEX` 的 `content` 存放裸 LaTeX 源码（`$$...$$` 由渲染层处理，见 §6）。

### 5.3 工具调用循环（Agent Loop）

```
用户消息 → 组装 messages（system + 历史 + 用户 + tools 定义）
  → 调 LLM
  → 若返回 tool_calls：执行工具 → 追加 tool 消息（结果）→ 回到「调 LLM」（上限 5 轮防死循环）
  → 无 tool_calls：SSE 流式输出最终回答
```

关键点：**工具调用阶段非流式**（需要完整 tool_calls JSON），**最终回答阶段流式**。两者在同一 `LlmApiClient` 内切换 `stream` 参数。

## 6. 提示词与约束（保证输出可直接落地）

`system prompt` 固化以下规范，确保 AI 产物无需二次加工即可被应用使用：

1. **身份**：你是知录的知识助手，只能操作用户的本地知识库，不编造不存在的笔记。
2. **LaTeX 规范**（对齐 `docs/superpowers/specs/2026-07-12-latex-formula-spec.md`）：
   - 显示公式用 `$$...$$`，行内用 `$...$`；
   - 多行对齐**只用** `\begin{array}{ll}...\end{array}`，**禁止** `align` / `align*`；
   - 行分隔只用 `\\`，**禁止** `\\[8pt]`；
   - 简单公式可裸写 `E = mc^2`。
3. **Block 类型语义**：`text` 普通文本、`latex` 公式、`code` 代码（需 `language`）、`todo` 待办、`link` 链接、`divider` 分割线、`branch` 分支（子块在 `children`）、`image` 图片（`content` 填本次消息附带图片的 URI，由 `AiTaskManager.resolveImageContext` 在上下文里给出）。

   > `image` 块的落库形态是 `mediaId|uri`（`ImageBlockContent`），**模型只负责给 URI**：
   > `AiToolExecutor.registerImageContent()` 会在写入前把该图登记进 `media` 表并补上 `mediaId` 前缀。
   > 直接存裸 URI 会让图片脱离媒体体系——导出/分享按 `mediaId` 找不到它，媒体清理还会误判为孤儿。
4. **生成即落库**：涉及「创建/记录」时，必须调用 `create_note`，不要只把内容打在对话里。
5. **读取优先**：用户问「我的笔记…」时，先 `search_notes` / `get_note`，基于真实数据回答，不得虚构。
6. **短答复**：回答精炼，除非用户要求展开。

## 7. OCR 设计

**方案：复用多模态大模型识图，不引入 ML Kit / Tesseract。**

- 用户在对话中附图片 → `LlmApiClient` 把图片转 `data:image/jpeg;base64,...` 放入 `image_url` → 视觉模型识别文字 → 返回。
- 独立「OCR 快捷路径」：`选图 → 识别 → 预览文字 → 一键 create_note`，本质仍是同一条 LLM 识图调用，只是前置了明确指令「逐字识别图片中的文字，保留原始排版」。
- 图片压缩：复用现有 CameraX 压缩策略（≤1MB），避免 base64 过大超 token。

**OCR 落地为知识点**：识别结果通过 `create_note` 工具（或用户确认后）写入笔记，而非只显示在对话里。

## 8. UI/UX 设计

### 8.1 入口

- **已确认**：首页 FAB 速度拨号 + 探索页入口（不改底部导航结构）。
- 首页 FAB 展开后新增「AI 助手」选项；探索页顶部新增「AI 助手」入口卡片。
- AI 助手页顶部提供「对话历史」入口，支持回看/继续/删除历史会话（持久化）。

### 8.2 对话页（AssistantScreen）

- 顶部：标题「助手」+ 清空对话 / 设置 AI 的入口。
- 中部：消息列表（用户靠右、AI 靠左），AI 消息流式渲染；工具调用过程显示轻量「正在查找笔记…」状态。
- 底部：输入栏 = 文本框 + 图片按钮（相册/拍照）+ 文件按钮（txt/md）+ 发送。
- 空状态：引导文案 + 快捷指令（如「帮我整理今天的笔记」「识别这张图片」）。

### 8.3 设置页新增「AI 助手」分区

- 端点、API Key（密文显示）、模型名、视觉模型名；「测试连接」按钮。

### 8.4 视觉风格

沿用编辑风 Token（暖纸 + 墨蓝 + `AppCard`），AI 气泡用墨蓝 / 浅墨蓝底区分，流式光标用墨蓝。

## 9. 兼容性与边界

- **不破坏现有功能**：只新增 `ai` 包与设置项，不改 ViewModel 既有签名、不改 `Note`/`Block` 模型、不改导航现有路由（仅新增 `Destination.Assistant`）。
- **Room v4 → v5**：新增 `ai_conversations` / `ai_messages` 两表 + Migration（挂到 `Migration.all`），schema 导出到 `app/schemas/5.json` 纳入版本控制。
- **不新增传统 OCR 依赖**。
- **网络失败 / 无 Key / 超配额** 均须明确报错（气泡内提示），不静默失败。
- **敏感信息**：API Key 不入日志（沿用 Timber，但 Key 相关一律脱敏）。

## 10. 分阶段实施计划

| 阶段 | 内容 | 验收 |
|---|---|---|
| **P0** | Room v4→v5 迁移（对话/消息表 + MigrationTest）；引入 OkHttp；`UserPreferences` 加 AI 配置；`LlmApiClient` 基础（非流式单轮）；设置页 AI 分区 | `assembleDebug` 通过；Migration 测试通过；设置可保存 |
| **P1** | `AiMessage`/`AiConversation` 模型 + Repository；对话 UI + SSE 流式输出；对话历史持久化读写；导航入口 | 与模型对话可见流式回复；历史可回看 |
| **P2** | Function Calling 框架 + 六工具；`AiToolExecutor`；system prompt 约束；工具循环 | AI 可创建/搜索/读取笔记并回答 |
| **P3** | 多模态图片输入；OCR 识图 + 落地知识点 | 发图 → 识别文字 → 生成笔记 |
| **P4** | 文件输入（txt/md）；错误态/空态/无障碍；收尾 lint + 全量测试 | 全量验收全绿 |

## 11. 验收清单

- [x] `testDebugUnitTest` → `lintDebug` → `assembleDebug` 全绿
- [x] 设置页配置 API Key 后可正常对话（流式）——MuMu 实机验证通过
- [x] AI 能通过工具真实创建 / 搜索 / 读取笔记，不编造——MuMu 实机验证通过（含越权请求：要求改 `id=999` 时如实报错、不擅自创建）
- [x] AI 生成的 LaTeX 符合规范（`$$` + `array`，无 `align`/`\\[8pt]`）——system prompt 固化约束
- [x] 发图片可识别文字并落地为知识点——MuMu 实机验证通过（OCR 逐字正确，`create_note` 同时产出文本块与图片块）
- [x] 未配置 Key / 网络失败 / 超配额均有明确提示——断网实测：输入内容保留可重发，无助手消息落库
- [x] Room 已升至 v5，Migration 测试通过，schema 导出 5.json
- [x] 行内 `$...$` 公式渲染——见 `2026-10-01-ai-iteration-design.md` 第 10 节
