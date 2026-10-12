# AI 链路优化:检索缺陷修复 + Prompt Cache 设计

> 范围:检索质量(不使用向量/embedding)与 LLM 请求组装优化。
> 不含:本地 embedding 模型、向量库、hybrid 融合检索。已明确排除。
>
> 依据:对 `HaoDuoYv/Zilur` @ `8d1a615` 的源码走查 + 本地实测(见 §1.2)。

---

## 0. 结论速览

| # | 项 | 性质 | 收益 | 本地开销 | 工作量 |
|---|---|---|---|---|---|
| D1 | 中文检索无分词 | 缺陷 | 高 | 降低 | 1–2 天 |
| D2 | 分词器选择(阻塞项) | 决策 | — | — | 半天 |
| D3 | 搜索无 LIMIT / 无相关性排序 | 缺陷 | 中 | 无 | 2 小时 |
| D4 | `snippetOf` 取错内容块 | 缺陷 | 中 | 无 | 2 小时 |
| D5 | 查询词未改写 | 缺陷 | 中 | 无 | 改字符串 |
| D6 | `LIKE` 无法走索引 | 缺陷 | 低(当前体量) | 降低 | 随 D1 |
| P1 | 图片上下文污染 system prompt | 缺陷 | **高**(50× 价差) | 无 | 半天 |
| P2 | tools description 过重 | 优化 | 中 | 无 | 半天 |
| P3 | 字段顺序对齐 | 防御 | 低 | 无 | 10 分钟 |
| P4 | usage 缓存命中率可观测 | 新增 | 间接 | 无 | 半天 |

**优先级:P1 → D1/D2 → D3/D4 → P4 → D5 → P2 → P3。**
P1 是全表里唯一「零本地开销 + 最高收益」的一格,应最先做。

> **状态(2026-10-12)**:10 项**已全部落地**。落地清单、5 处文档修正、真机验收结论见 **§5 执行记录**。
> 其中 §5.2-① 是一条能整体废掉中文检索的真机坑(Android 不认显式 `AND`),优先级高于本文档任何一节。

---

## 1. 检索侧缺陷

### D1 · 中文检索实质失效 `P0`

**位置**
- `data/local/dao/NoteDao.kt:37-39` — `search()`
- `data/local/dao/NoteDao.kt:60-68` — `searchWithTags()`

**现状**

```sql
WHERE n.deletedAt IS NULL
  AND (
    n.title LIKE '%' || :keyword || '%'
    OR b.content LIKE '%' || :keyword || '%'
    OR t.name LIKE '%' || :keyword || '%'
  )
ORDER BY n.updatedAt DESC
```

`LIKE '%…%'` 无分词、无倒排索引,逐行扫描。中文语境下只能命中**连续字面子串**:
搜「惯性定理」匹配不到「二次型的**惯性定理**与规范形」——虽然这几乎是所有用户的预期行为。

> ⚠️ **执行期修正**:上面这个例子是错的 —— `LIKE '%惯性定理%'` 恰恰**能**命中「二次型的惯性定理与规范形」(子串就是子串)。
> 见 §5.2-⑤:改造的真实收益是多关键词 AND、免全表扫描、以及对行内标记免疫,不是"补上漏掉的子串命中"。

**影响面**

- AI 工具 `search_notes`(`domain/ai/usecase/AiToolExecutor.kt:253` 起)
- 首页全局搜索 + 标签筛选条(`ui/home/HomeViewModel.kt:301` → `data/repository/NoteRepositoryImpl.kt:44`)

即:**AI 的检索能力与人的检索能力,当前都停在「子串匹配」。**

**修法**:见 D2/D6,统一由 FTS 索引承担。

---

### D2 · 分词器选择——阻塞项 `P0`

D1 的修法依赖 FTS,但**默认分词器会让中文从「部分失效」变成「彻底失效」**。这是实施前必须知道的实测结论。

#### 实测(本地 SQLite 3.40.1)

```sql
CREATE VIRTUAL TABLE t USING fts5(body, tokenize='unicode61');
INSERT INTO t(body) VALUES ('二次型的惯性定理与规范形');
INSERT INTO t(body) VALUES ('性能优化的实践总结');
```

用 `fts5vocab` 查看实际切分结果:

```
token: '二次型的惯性定理与规范形'      ← 整串汉字是一个 token
token: '性能优化的实践总结'
```

匹配结果:

| 查询 | 命中 |
|---|---|
| `"惯性定理"` | **0** |
| `"定理"` | **0** |
| `"性能优化"` | **0** |
| `"优化"` | **0** |

`unicode61` 把连续 CJK 字符视为单个 alphanumeric 串。**直接给 Room 加 `@Fts4(tokenizer = UNICODE61)` 会让中文搜索 100% 失效,比现在的 `LIKE` 还差。**

#### 三方案实测对比

语料:300 篇合成中文笔记,正文共 468.5 KB。

| 方案 | 索引体积 | 膨胀 | 中文召回 |
|---|---|---|---|
| A `unicode61` 直接索引原文 | 688.0 KB | 1.47× | **0/8** |
| B **应用层 bigram + FTS5** | 672.0 KB | **1.43×** | **8/8** |
| C `trigram` tokenizer | 964.0 KB | 2.06× | 2/4(短词全废) |

方案 C(`trigram`)实测:

```
"惯性定理" -> 0 hits      ← 4 字词搜不到
"定理"     -> 0 hits      ← 2 字词搜不到
"复习"     -> 0 hits      ← 2 字词搜不到
"二次型"   -> 298 hits    ← 几乎全库命中(子串噪音)
"梯度下降" -> 299 hits    ← 同上
```

trigram 要求查询串 ≥3 字符且必须与原文连续对齐,对中文检索是双重不匹配。

#### 方案 B 细节(推荐采纳)

在**写入索引前**与**查询前**各做一次同构 bigram 转换,FTS 只存/查空格分隔的 token 流:

```kotlin
fun bigrams(s: String): List<String> =
    Regex("[一-\u9FFF]+|[A-Za-z0-9_.+-]+").findAll(s).flatMap { m ->
        val t = m.value
        when {
            t.first().code in 0x4E00..0x9FFF && t.length > 1 ->
                (0 until t.length - 1).map { t.substring(it, it + 2) }
            t.first().code in 0x4E00..0x9FFF -> listOf(t)   // 单字成词
            else -> listOf(t.lowercase())                    // ASCII/数字整体保留
        }
    }.toList()
```

写入:`' '.join(bigrams(body))`;查询:`MATCH ' '.join(bigrams(query))`(默认 AND 语义,多词自然收紧)。

实测召回(同一语料):

```
惯性定理  -> 1 hits   性能优化  -> 1 hits   定理    -> 1 hits
预热      -> 1 hits   复习间隔  -> 1 hits   180度   -> 1 hits
20分钟    -> 1 hits   二次型    -> 1 hits
```

8/8 命中,无误召,索引膨胀低于直接索引原文。

**优点**:零第三方依赖(不用 jieba 及其词典)、中文/英文/代码/公式统一处理、体积最优。
**代价**:索引 1.43×;单字查询(如「火」)会命中含该字的全部内容块,需在 UI 侧保留长度下限(现有实现建议 ≥2 字符)。

**备选**:若 bigram 召回噪声偏高,可换 `jieba` 词级分词 + `@Fts4`,召回更准但需引入词典依赖(~1–3 MB)。**建议先上 bigram,用真实笔记集 A/B 后再决定。**

---

### D3 · 搜索无 LIMIT、无相关性排序 `P1`

**位置**:`NoteDao.kt:41` 及 `:68` — 两处 `ORDER BY n.updatedAt DESC`,**均无 `LIMIT`**。

**问题**
1. 返回全量命中。笔记量上千后,`search_notes` 会把整个结果集 `joinToString` 灌进 tool 结果(`AiToolExecutor.kt:292` 附近),直接冲击上下文预算。
2. 按**修改时间**排序,而非相关性。用户搜「烤箱」,半年前写的正确笔记排在刚建的无关笔记之后。

**修法**

```sql
SELECT n.* FROM notes n
JOIN notes_fts f ON n.id = f.rowid
WHERE notes_fts MATCH :query AND n.deletedAt IS NULL
ORDER BY bm25(notes_fts)
LIMIT :limit;
```

`bm25()` 在较新的 Android SQLite build 中可用;不可用时降级为 `ORDER BY n.updatedAt DESC` 并保留 `LIMIT`。

**`LIMIT` 取值建议**:`search_notes` 工具路径取 **10**;UI 搜索路径取 **50**(配合既有分页/滚动加载)。
两者需分开参数化,**不要共用一个常量**——UI 要看得全,LLM 要看得准。

---

### D4 · `snippetOf` 取错内容块 `P1`

**位置**:`domain/ai/usecase/AiToolExecutor.kt:887-894`

```kotlin
private fun snippetOf(note: Note): String {
    val text = note.contentBlocks.firstOrNull { it.type == BlockType.TEXT }?.content
        ?.let(InlineMarkup::stripMarkup)?.replace('\n', ' ').trim().take(60)
    return if (text.isNullOrBlank()) "" else "｜$text"
}
```

**问题**:取的是**第一个** TEXT 块。若关键词命中在第 5 个块,返回给模型的摘要与命中内容**完全无关**——模型据此判断「这篇笔记不相关」,于是不再 `get_note`,检索链路就此断掉,且用户无从察觉。

**修法**(按优先级)

1. 首选:FTS 的 `snippet(notes_fts, col, '…', '…', '…', 12)` 让 SQLite 直接给出命中位置周边文本。
2. 次选(不改 DAO):遍历 `contentBlocks`,取**第一个含关键词**的 TEXT 块,而不是第一个 TEXT 块。
3. 兜底:命中块超过 200 字符时,同时返回块标题与命中行号,便于模型定位。

方案 2 改动最小、收益最直接,可作为第一步;方案 1 在引入 FTS 后顺带完成。

> ⚠️ **执行期修正**:方案 1 **不可用**。bigram 之后 FTS 列里存的是 token 流(`二次 次型 型知 …`),
> `snippet()` 截取的正是这段 token 流,拼出来是「惯性 性定 定理」这种不可读文本。
> 实际落地 = 方案 2 + 命中位置开窗。见 §5.2-②。

---

### D5 · 查询词未改写 `P2`

**位置**:`AiAssistantRepositoryImpl.kt` 的 `SYSTEM_PROMPT`(第 195 行起)

**问题**:用户说「我那篇讲惯性定理的笔记写了啥」,模型传给 `search_notes` 的 `query` 完全取决于其自由发挥,很可能直接传整句自然语言,导致 `LIKE` 一次都命中不了。

**修法**:在 system prompt 增加一节(**仅一条**,不新增调用):

```
【检索】
- 调用 search_notes 前,先把用户问题改写成 1–3 个**关键词**(名词/术语/专名),
  用空格分隔作为 query;不要把整句用户输入直接当 query 传。
- query 无结果时,换用同义词或上位词再试一次;仍无结果才回答「没找到」。
```

配合 D3 的 `snippet` 与 D4,整条链路即可闭合。**本项零额外 LLM 调用、零本地开销。**

---

### D6 · `LIKE` 无法走索引 `P2`

`LIKE '%kw%'` 的前导通配符使 B-Tree 索引完全失效,复杂度 O(n)。当前笔记体量下可接受,随 D1 一并消除。

同路径修复后,5000 条中文笔记的查询耗时从秒级降到毫秒级,这是 FTS 的既定收益,无需额外设计。

---

## 2. Prompt Cache 设计

### 2.1 机制

缓存命中要求:**同一会话内,两次请求的 prompt 前缀逐 token 完全一致**。只要前缀不变,命中部分按缓存价计费。

| 供应商 | 命中价 / 未命中价 | 门槛 | 有效期 | 上报字段 |
|---|---|---|---|---|
| DeepSeek V4-Flash | 0.02 / 1 元每千 token(**50×**) | 64 token | ~30 min 无活动 | `prompt_cache_hit_tokens` / `prompt_cache_miss_tokens` |
| OpenAI | 读 0.1× / 写 1.25×(GPT-5.6+) | 1024 token | 模型相关 | `usage.prompt_tokens_details.cached_tokens` |

本项目 system prompt(90 行中文)+ 12 个工具的中文 schema,轻松越过门槛。**收益量级:输入成本最多降一个数量级。**

---

### 2.2 P1 · 图片上下文污染 system prompt(最高收益)

**位置**:`data/ai/AiAssistantRepositoryImpl.kt:136-142`

```kotlin
val basePrompt = service.systemPrompt.ifBlank { SYSTEM_PROMPT }
val systemPrompt = if (contextText.isBlank()) {
    basePrompt
} else {
    "$basePrompt\n\n$contextText"     // ← contextText 随图片 URI 变化
}
messages.add(ChatMessageDto(role = "system", content = textContent(systemPrompt)))
```

`contextText` 由 `AiTaskManager.resolveImageContext()`(`ai/AiTaskManager.kt:340-349`)生成,内容是**本次附带的图片 URI 列表**,每轮都不同。

**后果**:用户一旦附图,system prompt 字节级改变 → **从第一个 token 起整段前缀失配**,system + tools + 全部历史全部按未命中价计费。在 DeepSeek 上是 50 倍价差。

**这不是笔误,是与既有约定相悖**:`data/ai/AiUserMessageText.kt` 的类注释已明确写出原则——

> 曾经的实现把引用内容拼在超长 system prompt 的末尾,模型常把它当背景噪音……
> 主流做法是把上下文**与提问放在同一条 user 消息**里。

图片这条路**没有遵守自己的原则**。

**修法**:把图片说明作为 `trailing` 段注入**最新一条 user 消息**,而不是拼进 system。

```kotlin
// AiUserMessageText.build(...) 新增参数
fun build(
    base: String,
    fileText: String?,
    refs: List<AiRef>,
    quotedText: String?,
    trailing: String? = null            // 新增:运行时上下文,不落库
): String {
    ...
    if (!fileText.isNullOrBlank()) sections.add("【附件内容】\n$fileText")
    if (!trailing.isNullOrBlank()) sections.add(trailing)   // 追加在最后
    return sections.joinToString("\n\n")
}
```

```kotlin
// AiAssistantRepositoryImpl.buildMessages
history.forEachIndexed { index, message ->
    when (message.role) {
        AiRole.USER -> {
            val isLatest = index == history.lastIndex
            val text = AiUserMessageText.build(
                base = message.content,
                fileText = message.fileText,
                refs = message.refs,
                quotedText = message.quotedMessageId?.let { byId[it]?.content },
                trailing = imageContext.takeIf { isLatest }   // 仅最新一条
            )
            ...
        }
    }
}
```

**为何用 `isLatest` 而非无条件追加**:`imageContext` 是本轮运行时数据,只有最新 user 消息携带它是语义正确的;无条件追加会让每条历史消息都带上「本次附带的图片」描述。

**为何放最后**:`AiUserMessageText` 的既有顺序是 引用 → 引用消息 → 用户问题 → 附件内容。图片说明属上下文,与附件同级,放末尾贴近生成位置,不影响前缀。

**改后前缀**:`system(稳定) + history[0..n-2](稳定) + [最新 user: 引用 + 问题 + 图片说明]`。
历史为追加式写入,前缀逐轮稳定 → 全轮命中。

**附带收益**:修复了「图片说明被当成全局人设指令」的语义污染。

---

### 2.3 P2 · tools description 过重

**位置**:`domain/ai/usecase/AiToolExecutor.kt:133-248`

**问题**:12 个工具的 `description` 与 `parametersJson` 里混入了大量**本属全局规范**的内容,例如:

- 「任何写入都会重建块 id,因此要先用 get_note 取最新块 id」
- 「追加不会覆盖已有待办」「待办项挂在笔记上,不是挂在某条块上」
- block schema 里逐字段的 `description`(强调色枚举含义、latex 不含 `$$` 包裹等)

这些内容每轮都要重发。更关键的是:**详细语法规范其实已经在 `SYSTEM_PROMPT` 里写了一遍**(LaTeX 禁用 `align`、行内标记正反例、`add_blocks` vs `update_note` 的选择规则)。工具描述里是重复的、且更啰嗦的版本。

**原则**:tool description 只保留**工具选型决策信息**,参数写法规范统一由 system prompt 承担。两者都在稳定前缀内,收益相同,但 tools 体积直接决定每次请求的基础开销。

**拆分示例**

```
description (保留):
  「在已有笔记末尾追加内容块,不覆盖原有内容。可指定追加到哪个小节。」
  → 保留:这是模型判断「何时该用 add_blocks」的全部依据

移入 SYSTEM_PROMPT:
  「add_blocks 只能追加到小节末尾,不能插到中间或重排。」
  「要重排就用 update_note 重写整篇。」
  「任何写入都会重建块 id,拿到旧 blockId 后不要直接复用,先重新 get_note。」
```

**注意**:`update_note` / `delete_note` 的 description 含风险语义,不可只留功能描述——「传入内容会完整覆盖原有部分」「删除是软删除,用户仍可恢复」「用户明确要求删除时才调用」这类**约束性文案必须留在 tool description**,它们直接抑制误调用。瘦身目标是**去掉重复的语法说明**,不是去掉安全提示。

**风险与验证**:精简可能降低工具选择准确率。改前用 §2.6 的日志记录 `toolName`,对比改动前后同一组测试问句的调用序列与误调用率。

---

### 2.4 P3 · 字段顺序对齐(防御性,**非性能优化**)

> **需明确澄清**:调换 `ChatCompletionRequest` 的 JSON 字段顺序,**本身不构成性能优化**。
> 服务端解析 JSON 得到对象后,按自身固定 schema 组装 prompt,**不按 JSON 字节序**。
> 上一版文档曾将其列为性能项,此处修正。

**现状**:`data/ai/dto/ChatDto.kt:28-38` 声明序为 `model → messages → stream → tools`。

**协议约定的渲染序**(OpenAI 文档「How prompt caching works」明确列出):

```
Hidden system message → OpenAI-provided instructions → Tools(定义与 schema)
  → Developer message → Application instructions → Context history → Conversation messages
```

DeepSeek 文档同为:`put static content first (system prompts, tool definitions, reference documents) and variable content last`。

**结论**:JSON 字段序**大概率**不影响服务端行为,故本项价值仅在于:

1. **防御中转站实现差异**。部分第三方中转站/自建网关可能直接拼接 JSON 字符串构造 prompt,此时顺序会影响前缀稳定性。
2. **零风险**。同名字段调序无兼容性影响,且若发生实现差异,固定序优于随机序。

**建议改动**:将 `tools` 声明前移至 `messages` 之前,与协议渲染序对齐。

```kotlin
@Serializable
data class ChatCompletionRequest(
    val model: String,
    val tools: List<ToolDto>? = null,        // 前移:对齐协议渲染序
    val stream: Boolean = false,
    val messages: List<ChatMessageDto>,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    // P4 新增,见下
    @SerialName("stream_options") val streamOptions: StreamOptionsDto? = null
)
```

**不要**为此改动做「顺序不同则缓存必然失效」的宣称。验证以 §2.6 的实测命中率为准。

---

### 2.5 P4 · usage 缓存命中率可观测(新增能力)

**目的**:P1/P2 是否生效,当前**无法从代码或界面观察**。必须先能测,再谈优化。

**关键前提:流式请求默认不返回 usage。** 需显式传 `stream_options.include_usage`,服务端才在流的最后一个 chunk 附带 usage。

**改动清单**

1. `ChatDto.kt` 新增:

```kotlin
@Serializable
data class StreamOptionsDto(
    @SerialName("include_usage") val includeUsage: Boolean = true
)

@Serializable
data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("prompt_cache_hit_tokens") val cacheHitTokens: Int? = null,       // DeepSeek
    @SerialName("prompt_cache_miss_tokens") val cacheMissTokens: Int? = null,     // DeepSeek
    @SerialName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetailsDto? = null  // OpenAI
) {
    @Serializable
    data class PromptTokensDetailsDto(
        @SerialName("cached_tokens") val cachedTokens: Int? = null
    )

    /** 统一取值:优先 DeepSeek 字段,回退 OpenAI 嵌套字段。 */
    val effectiveCachedTokens: Int
        get() = cacheHitTokens ?: promptTokensDetails?.cachedTokens ?: 0
}

@Serializable
data class ChatCompletionChunk(
    val choices: List<ChunkChoiceDto> = emptyList(),
    val usage: UsageDto? = null            // 仅当 include_usage=true 时出现
)
```

2. `SseStreamAssembler`(`data/ai/LlmApiClient.kt:65-141`)收集 usage。注意其 `accept()` 在 `[DONE]` 时即置 `terminated` 并返回,而 usage chunk 通常在 `[DONE]` **之前**的最后一个 data 分片,现有逻辑不会丢它,但**空 choices + usage 的 chunk 需显式处理**(现逻辑在 `chunk.choices.firstOrNull() ?: return null` 处提前返回,应在该分支之前捕获 usage)。

> ⚠️ **执行期修正**:「现有逻辑不会丢它」是**误判**。读循环是 `if (assembler.terminated) break`
> (`LlmApiClient.kt:208`),而 `terminated` 在 `finish_reason` 分片就置位(`:94`) —— usage 分片虽在 `[DONE]` 之前,
> 却在 `finish_reason` **之后**,必然丢。必须拆「`[DONE]`」与「`finish_reason`」两种结束信号。见 §5.2-③。

3. `StreamOutcome` 增加 `usage: UsageDto?`。

4. `AiTaskManager.run()` 落一条 Timber 日志:

```kotlin
Timber.d("AI usage: in=%d out=%d cacheHit=%d hitRate=%.1f%% service=%s",
    usage.promptTokens, usage.completionTokens, usage.effectiveCachedTokens,
    100.0 * usage.effectiveCachedTokens / usage.promptTokens.coerceAtLeast(1),
    service.displayName)
```

5. **不接入 UI**。调试期日志即可,避免为一个调试指标设计界面。

**兼容性风险与降级**

`stream_options` 并非所有 OpenAI 兼容服务都支持(本地 Ollama、各家中转站实现不一),可能返回 400。

降级策略:

- `streamOptions` 默认可配,**默认关闭**;
- 需要开启时,若收到 HTTP 400 且响应体含 `stream_options` 相关报错,则**自动去掉该字段重试一次**,并记日志。

```kotlin
// LlmApiClient.chatStream 内
catch (e: LlmApiException) {
    if (request.streamOptions != null && e.code == 400) {
        Timber.w("stream_options 不被支持,降级重试: ${e.message}")
        return chatStream(endpoint, apiKey, request.copy(streamOptions = null), onDelta)
    }
    throw e
}
```

**此项只用于验证 P1/P2。** 若最终不保留日志,可整体移除 `stream_options`,不影响 P1/P2 本身。

---

### 2.6 缓存路由类参数评估(暂不采纳)

> 结论先行:**本节参数一律不实施**,且实施顺序上排在 P1 之后。原因见 §2.6.4。
> 记录本节是为了避免后续看到「prompt cache 优化」直奔 `prompt_cache_key`——那个顺序是反的。

#### 2.6.1 参数名纠正

| 常见叫法 | 实际参数 | 出处 |
|---|---|---|
| `prompt_cache_id` | **`prompt_cache_key`** | OpenAI 正式参数 |
| `x-session-id` | 真实,但**非模型厂商 API 参数** | 网关/路由层消费 |
| 「声明 KV 缓存复用」 | 无此语义,见 §2.6.2 | — |

`X-Session-ID` 由 **OpenRouter、腾讯云 TokenHub、LLMGateway、xAI(其 header 名为 `x-grok-conv-id`)** 等消费;OpenAI 与 DeepSeek 官方 API 均不读取。

#### 2.6.2 三者职责不同,不可混为一谈

缓存能否复用分两层:

**第一层 — 前缀精确匹配,决定「能不能」复用。**
KV 缓存驻留在某台推理机的显存中。前缀 token 不逐一致 → 物理上不可能命中。
**这一层完全由 prompt 如何组装决定,任何参数都改不了它。**

**第二层 — 路由亲和,决定「同一台机器」能否接上。**
请求 A 落在机器 1 写入缓存,请求 B 被路由到机器 2,机器 1 的缓存便用不上。
**这一层才是参数的作用域。**

| 参数 | 作用层 | 官方定性 |
|---|---|---|
| `prompt_cache_key` | 第二层 | OpenAI 文档:作为路由的次级 key,**不保证命中,仅提高概率** |
| `X-Session-ID` | 第二层 | OpenRouter:sticky routing;TokenHub:「解决物理上把请求路由到哪个推理实例」 |

**需特别澄清**:`prompt_cache_key` **不是缓存开关,不「声明」复用**。
真正具备「显式声明」语义的是 Anthropic 的 `cache_control: {"type":"ephemeral"}`,
或 OpenAI GPT-5.6+ 的 `prompt_cache_breakpoint`——后者仅新模型支持,且本项目未预设 Anthropic。

#### 2.6.3 各家支持情况

| 供应商 | `prompt_cache_key` | `X-Session-ID` | 说明 |
|---|---|---|---|
| DeepSeek | ✗ | ✗ | 官方:「fully automatic — no code changes, no explicit cache keys」 |
| OpenAI | ✓ | ✗ | 路由提示;每 key 建议流量 ≤15 req/min |
| OpenRouter | ✓(回退用) | ✓ | body `session_id` 优先于 header,≤256 字符 |
| 腾讯云 TokenHub | ✓ | ✓ | 官方推荐二者配合 |
| xAI | ✓ | `x-grok-conv-id` | — |
| 通义 / 智谱 / Moonshot / 硅基流动 | 依版本 | 依网关 | 以各自文档为准 |
| Ollama(本地) | ✗ | ✗ | 不支持 |

#### 2.6.4 为何本项目不采纳

**① 核心场景不存在。** `prompt_cache_key` 解决的是 OpenAI 文档所述的路由溢出:
> 每分钟超过 15 次请求的流量可能会导致溢出路由

Zilu 是单用户本地 app,一机一用户,流量远低于该阈值。TokenHub 建议的对象是
「Agent / RAG / 代码助手 / 客服」等多用户高并发场景,与本项目形态不符。

**② 预设首选供应商不需要。** DeepSeek 是 `AiService.kt` 预设列表第一项,且明确全程自动、无需任何 header 或开关。

**③ 关键:修完 P1 可能就不需要它们了。**

OpenRouter 在未传 `session_id` 时,粘性路由 key 是**自动推导**的:
> 默认通过对**第一个 system(或 developer)消息**与**第一条非 system 消息**做哈希识别会话

即:**只要 system prompt 稳定,网关层的天然亲和已自动建立。**
而 P1 要修的正是 system prompt 不稳定(附图即变)。正确因果链为:

```
P1(修好 system 稳定性) → 自动获得隐式路由亲和 → 显式传 key 的边际收益趋近 0
```

**顺序不可颠倒。** 先稳定前缀,再谈路由调优。

**④ 唯一相关场景亦不必覆盖。** `AiSettings.fallbackChain()`(`domain/model/AiService.kt:114`)
在失败时切换供应商,跨供应商必然丢失前缀缓存——但这是**故障路径**而非常态,
且 P1 修好后正常会话本就不会 miss。为故障路径引入常驻参数不划算。

#### 2.6.5 若将来仍要加,怎么加

在 P1 + P4 落地、且实测命中率仍低于预期时,按以下约束实施:

- **取值**:`conversationId`(TokenHub:「赋值为整体上下文总 ID,而非单一 session_id」)。`AiTaskManager` 已持有该值,传参近乎零成本。
- **粒度**:仅按会话,**不按用户**。本项目无多用户,再粗即可。
- **开关**:按服务(`AiService`)可选,字段级而非全局,默认关闭。
- **降级**:收到 HTTP 400 时自动去掉该字段重试一次——部分 OpenAI 兼容实现(Ollama、部分中转站)会因未知字段报错,与 §2.5 的 `stream_options` 同款降级逻辑,可复用。
- **禁止**:不得为了「优化命中」而在前缀中插入任何时间戳/随机串——那会同时摧毁第一层匹配,是净负收益。

---

## 3. 落地顺序

| 阶段 | 内容 | 产出 |
|---|---|---|
| **1** | P1(图片上下文移出 system)+ P3 | 输入成本下降,零风险 |
| **2** | D2 分词器决策 → D1 + D6(FTS 索引落地) | 中文可检索 |
| **3** | D3(LIMIT + bm25)+ D4(snippet) | 检索质量闭环 |
| **4** | P4(usage 日志),**回测阶段 1–3** | 可量化验证 |
| **5** | D5(query 改写提示词)+ P2(description 瘦身) | 锦上添花 |

**P4 放在阶段 4 而非阶段 1** 的理由:先改完再做 A/B,避免中途为开日志反复扰动请求体。但需在阶段 1 前确认 `stream_options` 的支持情况(可用任一服务手测)。

**验证集(阶段 4 执行)**:固定 20 条中文测试问句,覆盖 ①命中 ②未命中 ③同义改写 ④中英混排 ⑤公式/代码。每个阶段前后各跑一遍,记录 `hitRate` 与 `toolName` 序列。

---

## 4. 明确不做的事

| 项 | 理由 |
|---|---|
| 向量 / embedding / hybrid 检索 | 已排除。即使将来需要,本笔记库大量内容为公式、代码、术语、人名,短文本精确匹配是向量检索的弱项,应以 BM25 为主而非补充 |
| jieba 词级分词 | 先用 bigram,真实语料 A/B 后再决定。避免过早引入词典依赖 |
| FTS5 + `BundledSQLiteDriver` | Room 2.6.1 无 `@Fts5` 注解,需引入额外 driver(+2–3 MB APK)。`@Fts4` + 应用层分词已满足需求 |
| `prompt_cache_key` / 显式 cache breakpoint / `X-Session-ID` | 解决的是**路由亲和**,而单用户本地 app 不存在路由溢出;DeepSeek 全程自动。**且顺序不可颠倒**——先做 P1 稳定前缀,再做路由调优。详见 §2.6 |
| 压缩/摘要历史对话 | 会改变前缀,直接摧毁缓存命中。当前 `REF_TOTAL_MAX_CHARS = 24_000` 的截断是单向的(`AiUserMessageText.kt`),若未来引入摘要需重新评估 |

---

## 5. 执行记录(2026-10-12)

**10 项全部落地。** 实际执行顺序与 §3 的建议略有出入:`P1 → P3 → P4 → D1/D2/D6 → D3/D4 → D5 → P2`。
把 P4(观测能力)提到检索改造之前 —— 先有量尺再动刀;FTS 是全表唯一有停机风险的改动,放中间便于单独验证。

### 5.1 落地清单

| 项 | 位置 | 关键决策 / 与文档的差异 |
|---|---|---|
| P1 | `AiAssistantRepositoryImpl.buildMessages` · `AiUserMessageText.build(trailing=)` | 运行时上下文挂到**最新一条 user 消息末尾**,只移数据不移语义:标签词(如「本次附带的图片」)的解释仍留在 system prompt,否则模型认不出来 |
| P3 | `ChatDto.ChatCompletionRequest` | 声明序 `model → tools → stream → messages`;仅防御性,无性能含义(§2.4 已澄清) |
| P4 | `LlmApiClient` · `AiAssistantRepositoryImpl.logUsage` | 见 5.2-③;`stream_options` 按 build type 开关:debug 带、release 不带(即文档的"默认关闭"),另加 400 自动降级重试 |
| D1/D2/D6 | `NoteFtsEntity` · `NoteFtsDao` · `Migration 7→8` · `NoteSearchIndexer` · `NoteSearchTokens` | 方案 B(bigram)原样采纳。`@Fts4(tokenize=unicode61)` + 自存内容的独立虚拟表(非 external content:索引正文是聚合产物,无处可指) |
| D3 | `NoteDao.searchFts` / `searchWithTagsFts` | LIMIT:UI 200(`DEFAULT_SEARCH_LIMIT`)/ AI 工具 10(`AI_SEARCH_LIMIT`);排序降级见 5.2-④ |
| D4 | `AiToolExecutor.snippetOf` | 见 5.2-② |
| D5 | `SYSTEM_PROMPT` 【检索】一节 | 比文档多一句「每个关键词至少 2 个字」—— bigram 索引里没有单字 token |
| P2 | `AiToolExecutor.definitions` | 工具载荷 **11311 → 8298 字符(-26.6%)**;加了体积守卫单测防回涨 |

**索引范围** = 标题 + 全部块内容(含分支子块)+ 全部标签名,**不含** `note_cards.title` ——
与改造前 `LIKE` 的覆盖范围一致,不顺手扩面。

**一致性兜底**:`NoteSearchIndexer.reconcile()` 在启动时异步跑一次,`notes` 全表行数 ≠ 索引行数就全量重建。
因此 `reindex` **无条件写行**(正文为空也写空行),保住「一篇笔记 = 一行索引」的严格不变量 ——
否则空笔记会让行数永远对不上,每次启动都白跑一次重建。

### 5.2 对本文档的修正(实现期 / 真机发现)

**① 显式 `AND` 在 Android 上不是运算符 —— 最严重的一处,桌面测不出来**

`MATCH '矩阵 AND 矩阵'` 在**桌面 SQLite 3.53.1** 上正常,在**设备 SQLite 3.32.2** 上是 **0 命中**
(`AND` 被当成一个普通搜索词);换成空格连接的 `MATCH '矩阵 矩阵'` 两边都是 2 命中。

后果量级:多词查询一律 0 结果 —— 而且**任何 ≥3 字的汉字查询都会产出 ≥2 个 bigram**,即中文检索整体失效。
文档 §D2 原本写的就是空格连接(`MATCH ' '.join(bigrams(query))`),是实现时擅自改成 ` AND ` 引入的。
`NoteSearchTokensTest` 已加一条守卫钉死(禁止 `MATCH` 串出现 `AND`)。
**教训**:FTS 的查询语法在不同 SQLite 构建间不可假定一致,隐式语义(空格 = AND)才是可移植的那一个。

**② D4 的首选方案 `snippet()` 不可用**

bigram 化之后 FTS 列里存的是 token 流,`snippet()` 截取的就是这段流,拼出来是
「惯性 性定 定理」这种不可读文本。实际走文档的次选 + 命中位置开窗:
遍历 TEXT 块找第一个含关键词的 → 命中点前 15 / 后 45 字符开窗 → 兜底取第一个 TEXT 块截 60 字。
(必须先 `InlineMarkup.stripMarkup`,否则 `{{k:惯}}性定理` 这类被标记切开的词在原文里找不全。)

**③ P4 的「现有逻辑不会丢 usage」是误判**

读循环是 `if (assembler.terminated) break`(`LlmApiClient.kt:208`),而 `terminated` 在
`finish_reason` 分片就置位(`:94`)。开启 `include_usage` 后 usage 分片虽在 `[DONE]` 之前,
却在 `finish_reason` **之后**才到 → 必然丢。
修法:拆 `sawDone`(收到 `[DONE]`)与 `terminated`(收到 `finish_reason`)——
只有 `sawDone` 立刻停;`terminated` 后再探最多 5 行等 usage,拿到即止。

**④ D3 的 `bm25()` 在 FTS4 不存在(文档已给降级预案,此处补实际取值)**

`bm25()` 是 FTS5 的函数;FTS4 只有 `matchinfo()`,要按 `pcx`/`pcnalx` 编码自己解才算得出相关性,
成本远高于收益。排序降到 `updatedAt DESC`,结果集由 LIMIT 兜住。
候选本身就是 bigram AND 的精确命中,「同样命中时新内容优先」是合理降级。

**⑤ D1 的举例不成立(结论仍成立)**

`LIKE '%惯性定理%'` **是能**命中「二次型的惯性定理与规范形」的 —— 它就是子串。
真机上 10 个真实关键词做了 `FTS vs LIKE` 对照,**10/10 完全一致**,所以对**单个 ≥2 字关键词**
而言这次改造是**等价替换**。真实收益是另外三条:

1. **多关键词 AND**:`矩阵 对称` 这类输入在 `LIKE` 下必然 0 命中(它把空格当字面字符);
2. **免全表扫描**(D6),笔记量上去后是量级差异;
3. **对行内标记免疫**:正文里 `{{k:惯性定理}}` 这种被标记切开的写法,`LIKE` 会漏,
   bigram 索引不会 —— 索引是按块正文重新分词的,标记字符天然是分隔符。

### 5.3 真机验收(MuMu 模拟器,Android 14 / SQLite 3.32.2)

- **迁移**:`user_version` 7 → 8,启动无崩溃。`MIGRATION_7_8` 的 `CREATE VIRTUAL TABLE` 与
  `app/schemas/8.json` 里 Room 生成的定义**逐字一致**(这是启动时 schema 校验失败即崩的已知风险点)。
- **索引**:首启 `reconcile()` 全量重建,`notes` 10 行 ↔ `note_fts` 10 行;二次启动仍一致(不再重建)。
- **等价性**:10 个真实关键词 `fts == like` 全中(二次型 / 对称矩阵 / 计算机网络 / 操作系统 /
  线性代数 / 数据结构 / 微积分 / 知识点 / 矩阵 / 整理)。
- **UI 端到端**:「对称矩阵」→ 1 条并高亮;「计算机网络」(4 字 = 4 bigram,AND bug 下必然 0)→ 3 条;
  「矩阵 对称」→ 1 条(多关键词 AND);「矩阵 网络」→ 0 条(正确收窄)。
- **索引正文形状**:`二次 次型 型知 知识 识点 点整 整理` / `二次 次型 型是 n 元二 次齐 齐次 …`
- **三件套**:`testDebugUnitTest` 695 例 + `lintDebug` + `assembleDebug` 全绿。

**顺带修掉的一处**:多关键词检索在 UI 上**不高亮**(高亮函数把整串查询当单一针,
`矩阵 对称` 两个词都命中却一处不亮)。`highlightMatches` 改为按空白切词、逐个找命中并合并重叠区间。

### 5.4 未做 / 延后

| 项 | 原因 |
|---|---|
| §3 的「20 条固定测试问句」A/B 验证集 | 需要真实对话才能采到 `hitRate` 与 `toolName` 序列。usage 日志已就位(`AI usage: round=… in=… out=… cacheHit=… hitRate=… service=…`),随时可采 |
| `reasoning_content` 进 UI | 既有待办,与本次无关 |
| §2.6 的路由类参数 | 维持不采纳 |

---

## 附录:实测环境

- SQLite 3.40.1(Python 内置),FTS5 可用
- 语料:300 篇合成中文笔记,正文 468.5 KB,词表 24 个中文术语
- 索引体积经 `PRAGMA optimize` 后由 `page_count × page_size` 得出
- 召回测试词表:惯性定理 / 性能优化 / 定理 / 预热 / 复习间隔 / 180度 / 20分钟 / 二次型
