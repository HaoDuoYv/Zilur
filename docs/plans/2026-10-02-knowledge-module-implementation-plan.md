# 知识点模块重构 · 执行计划

> 依据：[2026-10-02-knowledge-module-redesign.md](./2026-10-02-knowledge-module-redesign.md)（**v4**）
> 原型：[docs/prototype/knowledge-module-redesign.html](../prototype/knowledge-module-redesign.html)（v4）
> 本文件只排期与验收，需求细节以设计文档为准（引用写作 §x.y）。

---

## 0. 已确认的前提（开工前核对过）

| 项 | 结论 |
| --- | --- |
| 构建链 | `JDK 17.0.4.1`（`JAVA_HOME` 已设）；`sdk.dir=C:\Users\Administrator\AppData\Local\Android\Sdk`；`compileSdk 35 / minSdk 26 / targetSdk 35` |
| 基线 | `:app:compileDebugKotlin --no-daemon` **通过**（18s，up-to-date） |
| 真机 | MuMu 模拟器 `emulator-5554`，Android 12 / SDK 32 / x86_64 / 1440×2560。已把重复的 TCP transport（5555/7555/16384）断开，只留一条，避免 Gradle "more than one device" |
| 应用 | 尚未安装 `com.example.zhilu`（只有旧的 `.test` 包），需要 `installDebug` |
| DB | 现状 `version = 5`，`Migration.all` 4 条；**本次升到 6** |
| 老数据 | **按用户决定：不做兼容**。但仍写正规 `Migration(5,6)`（就两条 `ALTER TABLE ADD COLUMN`，成本比破坏性迁移更低，且不违反 `AGENTS.md`） |

---

## 1. 范围与取舍

设计文档的 P0–P3 全量很大。本次按"**能真机看到、能单测验住、AI 同步**"三条标准切成 9 个阶段，
**每阶段独立可编译、可验证**，前 6 个阶段构成一条完整可用链路（结构 + 强调 + AI）。

明确**不在本次**范围（写回设计文档的后续项）：

- 「卡片摊平 + 块级 lazy」重构（§6.6 第 4 条）；
- 手打辅助（§3.11.4）；
- 色盲友好色板（§3.9 第 3 条）；
- 让 `**` 也进 span 表（§10 P3）。

---

## 2. 阶段划分

### A. 纯函数基础设施（无 UI，可单测）
**产物**
- `ui/note/blocks/InlineSpan.kt` —— `InlineSpan(tone, brush, start, end)` + `adjustSpans()`（§3.11.2 的 8 条规则）
- `ui/note/blocks/InlineMarkup.kt` —— `parseSpans()` / `materialize()` / `stripMarkup()`（§3.6、§3.10）
- `ui/note/blocks/InlineMarkupNormalizer.kt` —— `normalize()`（§3.11.3 的 6 条规则）
- `ui/theme/EmphasisTones.kt` —— `EmphasisTone` 4 角色 + → `AccentColor` 映射 + 4 个加深墨色（§3.2）+ `CardAccentRotation`（§5.2）

**验收**：`InlineSpanAdjustTest`、`InlineMarkupTest`、`InlineMarkupNormalizerTest`、`EmphasisTonesTest`、`CardAccentRotationTest`
+ `materialize(parseSpans(t)) == t` 性质测试。

### B. 数据层 v6（`accent` / `emphasis`）
**产物**：`KnowledgeCard.accent: Int?`、`Block.emphasis: Int`；两个 Entity/Mapper；
`NoteUiState.toNote()`；`NoteViewModel` 的构造点/`blocksForState`/`syncBlocksToState`；
`AppDatabase` v6 + `Migration(5,6)` + `app/schemas/.../6.json`；`MigrationTest.migrate5To6`。

**验收**：`NoteRepositoryImplTest` 往返用例（emphasis 不被清零）、`CardMapperTest`；
`testDebugUnitTest`；`connectedDebugAndroidTest --tests "*MigrationTest*"`。

### C. AI function calling 重写（§9.4 + 本轮新增要求）
**产物**（`domain/ai/usecase/AiToolExecutor.kt` + `data/ai/AiAssistantRepositoryImpl.kt`）
- 工具清单从 6 个扩到 **9 个**：新增 `delete_note`、`add_blocks`（追加块而不覆盖）、`mark_emphasis`（块级语义标记）；
  `create_note`/`update_note` 的块定义增加 `emphasis` 与 `tone`/`brush`；
  新增 `accent` 到 `create_note` 的卡片参数（**卡片维度**）。
- 系统提示词：修正「正文不渲染 Markdown」的错误陈述；声明 `{{ }}` 语法与 4 角色含义；
  给 3 个正例 + 3 个反例（含"公式写在标记外"）。
- `snippetOf` 先 `stripMarkup` 再截断（§3.10 第 10 条）。

**验收**：导出/工具层单测（新增 `AiToolExecutorTest`）；真机跑一次 AI 建卡/标记。

### D. L1 块级语义标记 + 结构化行（P0 + P2 前半）
**产物**：`BlockGutter.kt`、`EmphasisMarker.kt`；`BlockCard` 去常态描边；`CardBlockList` 间距节奏；
`KnowledgeCardItem` 卡片容器与心线；`Block.emphasis` 渲染（左缘 3dp 条 + 8% 底 + 标签词）；
编辑态焦点三重反馈（§4.6）。

### E. L2 行内着色：编辑器改造（P2 核心，本次最大改动）
**产物**：`TextBlockEditor` 改为「可见文本 + span 表」的受控编辑器（§3.7）；
`InlineMarkToolbar.kt`（三态，§3.8）；`KnowledgeBottomToolbar` 增加第 7 槽位「标记」；
`note/NoteEditScreen` 接线；`$`/`` ` ``/`**` 保持原有可见行为。

**验收**：真机验证「看不到标记、退格删不到标记、连续打字不丢字、光标不跳、标记中可连续输入」。

### F. 语法读取方与导出（§3.10、§9.3）
**产物**：`notePreviewText`、`readOnlyBlockClipboardText`、`snippetOf` 剥离；
`MarkdownExporter` / `HtmlExporter` 把 `{{ }}` 转成 `<span>`；
`AiToolExecutor.formatNote` / `AiTaskManager.formatBlock` 保留语法。

### G. 卡片身份系统（P1）
**产物**：`CardIdentityBadge.kt`；卡片身份色轮转 + 长按改色；摘要行；折叠态（会话内）；
章节留白 + 发丝线；`AppCardStyle` 拆出知识卡局部规格（§5.5）。

### H. 导航（P3，视进度）
`CardOutlineSheet.kt`、`CardIndexRail.kt`、sticky 卡头、页内查找。

### I. 收尾
真机全流程回归 + `testDebugUnitTest` → `lintDebug` → `assembleDebug`；
订正 `AGENTS.md`（DB 版本、新概念）；更新设计文档 §13 的"已实现/未实现"。

---

## 3. 真机测试（MuMu `emulator-5554`）

```powershell
$adb = "E:\Program Files\Netease\MuMu\nx_main\adb.exe"
.\gradlew.bat :app:installDebug --no-daemon
& $adb -s emulator-5554 shell am start -n com.example.zhilu/.MainActivity
```

**手测清单**（每阶段跑对应项）

| # | 场景 | 期望 |
| --- | --- | --- |
| 1 | 新建笔记 → 输入正文 | 无任何 `{{` 字符露出 |
| 2 | 选中一个字 → 点色块 | 该字着色；再点同色 → 取消 |
| 3 | 光标停在已标记文字里 | 工具条高亮当前角色 |
| 4 | 不选字点色块 → 连续打字 | 输入的字自动带上该角色；失焦后不残留空标记 |
| 5 | 在标记内/边界打字、退格、全选删除 | 不出现错位或残留；最坏只是标记消失 |
| 6 | 中文输入法组合输入中途打断 | 不丢字、光标不跳 |
| 7 | 2000 字长块连续输入 | 不卡顿 |
| 8 | 块级语义标记（gutter 圆点） | 2 步内完成；再点取消 |
| 9 | 卡片序号徽标 + 身份色 + 折叠 | 一屏可扫 6+ 个小节 |
| 10 | 保存 → 杀进程 → 重开 | 标记与 emphasis 完整还原 |
| 11 | AI：让它建一篇带标记的笔记 | 工具调用成功，正文带正确标记与强调 |
| 12 | AI：让它删除一篇笔记 | 走 `delete_note`（软删除），列表消失 |
| 13 | 导出 Markdown | 无 `{{` 残留（代码块除外） |
| 14 | 首页列表预览 | 不出现 `{{` |

---

## 4. 风险与回退

| 风险 | 对策 |
| --- | --- |
| **E 阶段（编辑器改造）是最大风险** | 纯函数先行（A 阶段）把 offset 风险关在单测里；E 只做接线。先跑通"只读渲染"，再打开编辑态 |
| 真机输入法行为与模拟器不同 | MuMu 上用系统拼音输入法覆盖组合输入场景；同时保留 `\{{` 转义作为兜底 |
| 阶段间编译失败 | 每阶段结束必跑 `compileDebugKotlin`；跨阶段不并行改同一文件 |
| 迁移 schema 校验失败 | §9.1 第 2 条：`@ColumnInfo(defaultValue=…)` 与 DDL 逐字对齐 |
| 范围过大 | 每阶段独立提交（`feat:` / `refactor:`），A–F 完成即为可用增量；G/H 可延后 |

---

## 6. 实施状态（本次会话实录）

> 更新于本轮开发结束时。每行都是"真的编译过 / 真的跑过测试 / 真的在 MuMu 上看过"的状态。

| 阶段 | 状态 | 证据 |
| --- | --- | --- |
| A 纯函数基础设施 | **完成** | `domain/markup/{InlineSpan,InlineMarkup,InlineMarkupNormalizer}.kt`；`InlineSpanAdjustTest`/`InlineMarkupTest`/`InlineMarkupNormalizerTest`/`EmphasisToneTest`/`EmphasisTonesTest` 全绿 |
| B 数据层 v6 | **完成** | `accent`/`emphasis` 全链路；`AppDatabase v6` + `Migration(5,6)`；`app/schemas/…/6.json` 已生成（emphasis notNull+default 0、accent 可空无默认）；`MigrationTest.migrate5To6` **在 MuMu 上通过**（3 tests on emulator-5554） |
| C AI function calling | **完成** | 工具由 6 个扩到 **9 个**（新增 `add_blocks` / `set_block_emphasis` / `delete_note`，`create_note`/`update_note` 支持 `cards` 小节结构与 `emphasis`）；系统提示词重写并修正既有的 `\$\$` 转义 bug；`AiToolExecutorDefinitionsTest` 校验 9 份 schema 合法 |
| D L1 块级语义标记 + 结构化行 | **完成（真机已验证）** | 新 `BlockGutter`（序号 + 标记圆点 + 四角色菜单）、`NoteViewModel.setBlockEmphasis`；`BlockCard` 去常态描边 + 左缘 3dp 色条 + 8% 淡底 + 角色标签词 + 焦点 4% 淡底；`CardBlockList` 改为 gutter+内容两列、心线（卡片色 26%）、间距节奏 4/16/24dp、撤掉同类发丝线；点 gutter 序号激活块。真机：`emphasis=3` 落库、绛红条+「注意」标签渲染正确、与 L2 行内标记叠加共存 |
| E L2 编辑器改造 | **完成（真机已验证）** | `TextBlockEditor` 改为「可见文本缓冲 + span 表」；`InlineMarkToolbar`（4 角色 + 3 笔触 + 清除 + 「标记中」旗标）；真机上"点色块 → 输入"链路走通：DB 落 `{{i:XY}}`、界面 `XY` 着色、全程无标记字符 |
| F 语法读取方 + 导出 | **完成** | `notePreviewText`、`readOnlyBlockClipboardText`、`AiToolExecutor.snippetOf` 均剥离；Markdown/HTML 导出把 `{{ }}` 转成内联 `<span>`（`export/InlineMarkupExport.kt`）；块剪贴板与 JSON 备份承载 `emphasis` |
| G 卡片身份系统 | **完成（真机已验证）** | `CardIdentityBadge`（衬线两位补零、身份色 12% 底）、`CardHeader` 重写（徽标 + 18sp 衬线标题 + `N 点` 计数胶囊 + 摘要行 + 折叠箭头，长按=全部折叠）、`cardSummary()` 纯函数（先剥离语法再截断，含单测）、`NoteViewModel.{toggleCardExpanded,toggleAllCardsExpanded}`（复用原本是死字段的 `isExpanded`，会话内）、卡片静息阴影 `ElevationTokens.Raised`、折叠态左侧 3dp 身份色竖条、章节间距 16→28dp（`Spacing.SectionGap`）。真机：浏览态徽标/摘要/心线/L1/L2 全部正确，摘要已剥离 `{{i:XY}}`；折叠态只剩头部 + 竖条 |
| H 导航设施（P3） | **完成（真机已验证）** | ① **页内目录 sheet**：`CardOutlineSheet`（序号用卡片身份色 / 标题 / 小点数，当前行带身份色竖条 + 7% 底）、`NoteTopBar` 目录入口（仅卡片数 ≥ 3）、点击 `animateScrollToItem(cardIndex + 1)`。② **右缘索引轨**：`CardIndexRail`（刻度颜色 = 卡片身份色、长度 ∝ 小点数、当前加长加粗；拖动 scrub 连续跳转 + `01 标题` 气泡；点按单刻度跳转；卡片数 ≥ 4 才出现，滚动/拖动淡入、静置 2.5s 淡出，**淡出后连手势层一起撤掉**）。③ **sticky 当前小节条**：`CardStickyBar`（卡片头滚出 56dp 后浮出，点它回到该小节顶部）。真机三项均验证：目录三行三色 + 当前行高亮、索引轨 4 刻度 4 色、吸附条在头滚出后出现且与索引轨同屏 |
| I 收尾 | **完成** | `testDebugUnitTest` 248 通过 / `lintDebug` / `assembleDebug`；MuMu 真机跑通启动 → 建笔记 → 编辑 → 标记 → 落库 |

### 本次真机暴露并修掉的 6 个 bug（都写进了代码注释）

| # | 现象 | 根因 | 修法 |
| --- | --- | --- | --- |
| 1 | 工具条**永不出现** | `Modifier.onFocusChanged` 挂在包装 Box 上观测不到内部 `BasicTextField` 的焦点，`isFocused` 恒为 false | 改用 `interactionSource.collectIsFocusedAsState()` |
| 2 | 光标在文末时工具条仍不出现 | `TextLayoutResult.getBoundingBox(offset)` 取的是**某个字符**的盒子，`offset == length` 越界抛异常，被 `runCatching` 吞掉 | 退化为"最后一个字符"的盒子 |
| 3 | 点了色块、接着打字**没有颜色**，DB 里也没有标记 | `InlineSpanAdjuster.normalize` 把零长 span 当"空区间"过滤掉 → `commit` 一提交就丢了「标记中」 | `normalize` 保留零长 span（但要区分"原本零长"与"被编辑削成零长"），并加回归测试 |
| 4 | 工具条被卡片**裁掉右侧**的「下划线 / 清除」，位置随光标跳动、点不准 | 手机宽度下浮动工具条比正文区还宽；贴边浮动会被卡片裁剪 | 工具条改为**停靠在正文下方**（位置恒定、不被裁、无需测量选区盒子） |
| 5 | 去掉块级底色后，整块变成**滑动删除的红底**，像被标记为待删除 | `BlockCard` 的 Surface 底色改成了 `Color.Transparent`，而块外层是 `SwipeToDismissBox`——删除红底直接透出来 | Surface 底色保持**不透明**的 `surface`；焦点淡底与语义淡底改画在内层 `drawBehind` |
| 6 | 轻点块体就激活块，会把删除红底**拉出来且卡住** | 在块体内的 `pointerInput` 上加 `onTap` 与 `SwipeToDismissBox` 的横向手势打架 | 激活改由 gutter 序号承担（gutter 在滑动手势之外） |

另外顺手修掉：`InlineMarkup.stripMarkup` 的快速通道只看 `{`，导致转义序列 `\}}` 里的反斜杠漏给用户；`CardBlockList` 加 gutter 后工具条宽度不足导致右侧按钮被裁（控件尺寸收窄）。

### 下一轮建议的接续顺序

**设计文档 P0/P1/P2/P3 的可落地部分已全部实现**（结构、身份、强调三层、AI 工具、三层导航）。
以下是设计里明确标注为"可选/后续"、本次**有意未做**的项：

1. **底部工具栏的「标记」入口**（§3.8）：编辑器内已能零输入起色（划词工具条 + 「标记中」），
   这个入口只是"不想选中文字"时的第二条路径，需要把选区状态从编辑器提升到屏幕层；
2. **页内查找**（§6.4，P3）：命中高亮 + 上下箭头跳转；
3. **手打辅助**（§3.11.4，P3）：`{{` 自动补全 + 角色提示；
4. **色盲友好色板**（§3.9 第 3 条）：设置项；
5. **让 `**` 也进 span 表**（§10 P3）：机制已就位，只是"再加一个 span 类型"。

### AI function calling 真机端到端验证（已通过）

配置好 LLM（provider=qwen / DashScope 兼容模式）后在助手页发「帮我整理一份关于二次型的知识点」，
完整链路跑通，**这是本次 AI 工具重写唯一算数的验证**：

| 验证点 | 真机结果 |
| --- | --- |
| 工具被调用 | 界面依次出现「🔧 搜索笔记」→「↻ 创建笔记」两张工具芯片，证明多轮 tool-call 正常 |
| `create_note` 的新 `cards` 结构 | 落库 6 个小节：一、定义与矩阵表示 / 二、线性替换与合同 / 三、标准形的求法 / 四、惯性定理与规范形 / 五、正定性及判别法 / 六、实对称矩阵与正交变换 |
| 卡片身份色 | 6 条 `accent` 分别是墨蓝/赭土/松石/绛红/苔绿/石墨 —— 正是 `CardAccent` 轮转序的前 6 个 |
| 块级 `emphasis` | block 23 `emphasis=3`（注意）、block 27 `emphasis=4`（待办） |
| 新行内语法 | 模型自己写出了 `{{w:注意}}`、`{{k:顺序主子式}}`，**角色用对了**；`normalize` 没有破坏它们 |
| 渲染 | 打开该笔记：`01`–`06` 序号码徽标、心线、衬线标题、摘要行（已剥离语法）、L1 左缘色条 + 角色标签词、L2 行内着色全部正确 |

**顺带暴露并修掉一个真实缺陷**：AI 写的 `type=todo` 块只把内容放在 `block.content`，
而 `TodoBlockContent` 原先只要 `todoItems != null` 就无条件渲染 `TodoBlock`，
表为空时显示「暂无待办」——AI 写进去的待办整条看不见。已改为
`todoItems.isNotEmpty() || block.content.isBlank()` 才走 todo 列表渲染，否则回退显示块正文。

### AI 能力复查（第二轮）与红色强调色

**复查方法**：逐个比对「笔记的全部能力」与 `AiToolExecutor` 的 9 份工具 schema + 系统提示词。

| 能力 | 复查前 | 处理 |
| --- | --- | --- |
| **分支子块**（`Block.parentBranchId`） | ❌ schema 里 `branch` 没有 `children`，解析器也不设父 id —— AI 只能建"有标题没内容"的空壳分支 | ✅ `blockItemSchema` 加 `children`（子块 schema 单独定义并**只开放一层**）；`parseBlocks` 递归解析 + 发**唯一负临时 id** 当父 id，由仓储层 `idMapping` 重映射 |
| **待办项**（`todo_items` 可勾选清单） | ❌ 只能建 `type=todo` 的空块 | ✅ 新工具 `add_todos`（待办项挂在**笔记**上，所以只要 noteId，绕开"写入重建块 id"的坑）；`get_note` 输出补上现有待办，否则模型不知道已有清单、只会堆重复项 |
| **三个"待办"同名** | ⚠️ `emphasis:todo` / `{{t:…}}` / `type:todo` 都叫待办，提示词没区分 | ✅ 提示词新增专门一节讲清三者，并给出"要能勾选→type=todo+add_todos；只要一句提醒→emphasis:todo" |
| **`divider` 必须带 content** | ⚠️ `required:["type","content"]`，分割线没有正文 | ✅ 改为只要求 `type` |
| **加粗与语义标记的关系** | ⚠️ 只说"会渲染成粗体" | ✅ 说明二者同属一套行内体系且**不能互相嵌套** |
| **`get_note` 的分支层级** | ⚠️ 子块与父块平铺输出，模型会以为并列 | ✅ 子块缩进 `└` 输出，孤儿块用 `⚠` 兜底显示 |
| **标题里的语法** | ⚠️ 标题是纯文本，模型写 `{{k:…}}` 会原样显示成一堆花括号 | ✅ `create_note`/`update_note`/`cards[].title` 统一过 `stripMarkup` |
| 插入位置 / 重排 | ⚠️ `add_blocks` 只能追加到末尾 | 提示词已明说"不能插到中间或重排，要重排用 update_note"（不新增工具） |
| 复习/提醒设置 | ❌ 未覆盖 | 属独立子系统，本次不做 |

**真机端到端验证**（让 AI「建一篇带分支（两个子块）和两条待办的笔记」）：

| 落库结果 | 证据 |
| --- | --- |
| 分支层级真的成立 | block 780 `type=8(BRANCH)`，block 781/782 的 `parentBranchId=780` —— 临时 id 重映射链路通了 |
| 待办项真的可勾选 | `todo_items` 新增 2 条，均挂 `noteId=2` |
| TODO 块不带正文 | block 783 `type=7(TODO)` 且 content 为空 —— 提示词"待办块自身不写 content"被遵守 |

**红色强调色**（用户要求"字体和笔记的强调色要增加红色这种一眼重点的颜色"）：

- **字体/语义**：`EmphasisTone.WARN`（注意）从绛红 `#8A4550`（低饱和玫瑰）换成**正红 `#C0392B`**，配套的浅色墨色也压深到 `#8C2018`。真机：第 ⑦ 条「注意」块呈现正红左缘条 + 红字标签 + 红底，块内 `{{w:注意}}` 也是红底高亮，与相邻的「要点」金棕对比鲜明。
- **笔记强调色**：新增第 8 档 `AccentColor.SCARLET`（正红，深色模式提亮为 `#F2938A`），追加在枚举**末尾**以免打乱既有用户按 name 持久化的值。
- **卡片身份色**：新增 `CardAccent.VERMILION`（朱红 `#B23B32`）并**追加在轮转序末尾**（插中间会改变已有笔记的默认配色）。刻意比语义正红更暗一档，遵守「一色一义」——语义色标内容、身份色标容器，撞值会让两个信号混成一个。
- **顺带修的布局隐患**：设置里的强调色板从 7 色加到 8 色后，单行 8 个单元格只有 ~32.5dp，会小于选中态圆直径 36dp —— 正是 `SwatchSize` 注释里记着的"被横向压扁成椭圆"那个坑。改为**每行 4 个折行**，真机确认两行八圆比例正常。

### 真机反馈的三个缺陷（已修，第三轮）

| # | 现象 | 根因 | 修法 |
| --- | --- | --- | --- |
| 1 | 首页摘要把公式定界符原样显示成 `· $n$ = 编号比特数` | `notePreviewText` 只做了 `stripMarkup`（管 `{{}}`/`**`），没管 `$…$` 与反引号。首页那行是 `Text` 而不是 `RichText`，渲染不了公式图片 | 新增 `plainPreviewText()`：剥标记后再去掉 `$$…$$`/`$…$` 定界符与反引号，**保留公式源码**。真机确认摘要变成 `· n = 编号比特数`，整页再无 `$` |
| 2 | `.dtk` 导入后小节结构全丢（六小节并成一篇流水账） | `JsonExporter.appendNote` 只写扁平的 `contentBlocks`，`cards` 根本没进备份；`ImportKnowledgeUseCase` 又硬写 `cards = emptyList()` | 备份格式加 `cards[{title,accent,blocks}]` 与 `Block.parentBranchId`；导入统一走新的纯函数 `remapForInsert()`（卡片 / 父子 / 图片三类引用成套换成负临时 id）。顺带补上 `.dtk` 一直缺的 `dtkVersion` 字段 |
| 3a | JSON 备份导入时图片进不来 | 导出只写 `media[].uri`（指向本机内部存储的路径），**没有内嵌字节**；导入端 `importMedia` 只是重新插了一行指向旧 uri 的记录，等于没恢复 | 备份格式加 `media[].data`（base64 data URL）；导入时解码 → 写回内部存储 → 媒体行指向新 uri。老备份（无 `data`）退回原行为 |
| 3b | 导入成功后每次进「我的」都重弹一次提示 | `SettingsScreen` 里 `clearMessages()` 排在 `showSnackbar()` **之后**；那个调用会挂起到提示消失，用户中途切走页面 → 协程被取消 → 消息永远清不掉 | 改成**先消费再弹**。真机确认：产生提示后离开再回「我的」不再重弹 |

顺带把设置页残留的英文提示（"Choose a JSON file location to export."、"Imported N notes" 等）改成中文，导入/导出提示补上图片张数。

新增 18 条单测：`NotePreviewMathTest`（摘要纯文本化 9 条）、`BackupRoundTripTest`（小节 / 身份色 / 分支层级 / 图片字节四样往返不丢 + `remapForInsert` 的 id 与引用重写 9 条）。
**JSON 导入的真机 SAF 流程没有自动化验证**（要驱动系统文件选择器），该路径靠上面这组往返单测覆盖。

### 收尾项（全部完成，均真机验证）
| 项 | 状态 | 真机证据 |
| --- | --- | --- |
| 底部工具栏「标记」入口（§3.8） | ✅ | 底部第 7 格画笔图标 → 弹出「标记为要点/想法/注意/待办 + 取消标记中」，四个色点颜色正确；选「标记为要点」后打字 → 落库 `{{k:ZZZ}}` |
| 无障碍色板（§3.9 第 3 条） | ✅ | 设置页「色盲友好语义色」开关 → 预览行四色切换为 Okabe-Ito 那套（亮橙/蓝/朱红/蓝绿）；DataStore `accessible_emphasis = true` |
| `**` 进 span 表（§10 P3） | ✅ | 光标落在 `{{k:QQQ}}` 内 → 点工具条「加粗」→ 落库变成 `**QQQ2QQQ**`（KEY span 就地转为 BOLD 并随输入扩展） |
| 手打辅助真机确认（§3.11.4） | ✅ | 光标在块文本末尾时打 `{{k:test}}` → 落库 `{{k:test}}`（**修复前是 `\{{k:test\}}`**），并把前一条 `{{k:ZZZ}}` 正确挖开成两条独立标记 |

### 收尾时又查出并修掉的 3 个既有 bug

| # | 现象 | 根因 | 修法 |
| --- | --- | --- | --- |
| 7 | 光标停在已标记文字**末尾**时，点另一个角色/加粗再打字，**新标记凭空消失** | 规则 2（"末尾插入即扩展"）把紧邻的前一条 span 扩过来覆盖新文字，随后 `normalize` 的"先到先得"把新标记整条丢掉 | `adjust` 检测插入点上是否存在零长标记（「标记中」），存在时关掉规则 2 —— 那是用户在**特意**起新标记 |
| 8 | 工具条上的**加粗按钮是死的**，点什么都没反应 | `pickBrush` 的 `when` 三个分支都要求 `activeSpan`/`pendingSpan` 非空；加粗不带语义角色，若从没点过角色色块，三个分支全不命中，也没有 `else` | 补 `else` 分支：用该笔触在光标处 `startPending`（角色默认 KEY，加粗渲染时忽略） |
| 9 | 摘掉块级底色后整块变红底 | （见前文 bug 5） | — |

### 仍未做（设计标注为可选，本次有意不做）

1. **索引轨的"内容高度 > 1.5 屏"条件**用卡片数 ≥ 4 近似（已在偏差表说明）；
2. **导出端的无障碍色板**：`export/InlineMarkupExport.kt` 用自己的一套 hex（domain 层读不到 CompositionLocal），开启无障碍色板时导出文件仍是默认色。要一致得把开关传进导出用例——属独立小项；
3. **`**` 的内容限制**：加粗内容若含花括号或换行，按"非法写法原样显示"处理（不成标记），这是与解析器一贯口径一致的有意取舍。

### 与设计文档的已知偏差（实现时按实际情况定的）

| 偏差 | 原因 |
| --- | --- |
| 划词工具条**停靠在正文下方**，而非"浮在选区上方 8dp" | 手机宽度下浮动会被卡片裁掉右侧控件，且位置随光标跳动点不准（bug #4） |
| 块级标记入口由**点 gutter 序号先激活块**再出圆点，而非"长按块" | 长按与滑动删除、长按菜单三者手势重叠；点 gutter 是最短路径（bug #6） |
| `EditableBlock`/`ReadOnlyBlock` 的 `showTopDivider` 参数**已删除**（连带同类发丝线的渲染） | 结构已由心线 + 间距节奏承担；留着是死参数（已清理） |
| sticky 卡头用**浮出式吸附条**（`CardStickyBar`）而非真正的 `stickyHeader` | 真 `stickyHeader` 要把卡片拆成"头 + 正文"两个 LazyColumn item，会连带打断卡片 Surface 的整体性（§5.5）、滑动/长按手势边界与 `key`/拖拽状态绑定；代价大于收益。浮出条视觉等价（白底 + 发丝线 + 轻微阴影，与首页 `HomeNoteList` 的 stickyHeader 一致）且可点回该小节顶部 |
| 索引轨的"内容高度 > 1.5 屏"条件用**卡片数 ≥ 4** 近似 | 精确测量内容高度需要在 `LazyListState.layoutInfo` 上做估算，收益不值；4 张卡在实践中就已经超过一屏 |

---

## 7. 提交切分

| 顺序 | 类型 | 内容 |
| --- | --- | --- |
| 1 | `docs:` | 设计文档 v4 + 原型 v4 + 本执行计划 |
| 2 | `feat:` | A：行内标记纯函数 + 语义色 + 卡片轮转序（含单测） |
| 3 | `feat:` | B：v6 迁移，`accent` / `emphasis` 全链路 |
| 4 | `feat:` | C：AI 工具与系统提示按新设计重写 |
| 5 | `refactor:` | D：块级结构化行 + 语义标记渲染 |
| 6 | `feat:` | E：编辑器改造 + 划词工具条三态 |
| 7 | `refactor:` | F：语法读取方剥离 + 导出器转换 |
| 8 | `feat:` | G：卡片身份系统 |
| 9 | `feat:` | H：导航设施（视进度） |
| 10 | `chore:` | I：收尾验证 + 文档订正 |
