# AGENTS.md — 知录 (ZhiLu)

本地优先 Android 知识笔记应用。单模块 `:app`，包名 `com.example.zhilu`。
技术栈：Kotlin 2.0 + Compose + Hilt（kapt）+ Room + WorkManager。无 CI。

## 常用命令

Windows 用 `.\gradlew.bat`，其余平台 `./gradlew`。本仓库的实现计划习惯加 `--no-daemon`。

```bash
# 快速编译检查（改 UI / ViewModel 后最常用）
./gradlew :app:compileDebugKotlin --no-daemon

# 全量单元测试
./gradlew :app:testDebugUnitTest --no-daemon

# 单个测试类 / 方法
./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.ui.note.NoteViewModelBlockOpsTest" --no-daemon
./gradlew :app:testDebugUnitTest --tests "com.example.zhilu.data.repository.NoteRepositoryImplTest.hydrate*" --no-daemon

# Lint / 构建
./gradlew :app:lintDebug --no-daemon
./gradlew :app:assembleDebug --no-daemon

# 插桩测试（需模拟器/真机；MigrationTest 在这里）
./gradlew :app:connectedDebugAndroidTest
```

收尾验证顺序：`testDebugUnitTest` → `lintDebug` → `assembleDebug`。

## 架构（不易从文件名看出）

- 分层：`ui/`（Compose + ViewModel）→ `domain/`（model / repository 接口 / usecase）→ `data/`（Room、Repository 实现、DataStore）。导出在 `export/`，提醒 Worker 在 `reminder/`，进程级 AI 任务运行时在 `ai/`（**服务层，不是混层**：`ai/` 下零 `androidx.compose` 引用，依赖方向只有 ui → ai → data/domain，与 `reminder/` 同级看待）。
- 笔记 = `Note` + 多个 `KnowledgeCard`，块挂在 `Block.cardId` 下。BRANCH 块的子块用 `Block.parentBranchId` 指向父分支；顶层块为 `parentBranchId == null`。
- **块树的顺序与父子关系一律走 `domain/model/BlockOrdering.kt`**（纯函数 + `BlockOrderingTest`）：`move`（分支块连子块一起搬、子块不参与卡片级移动）、`renumber`、`arrowUpSwap`/`arrowDownSwap`、`flatten`/`revive`（剪贴板粘贴发新 id）。
  - 抽它的理由不是"文件太长"，而是这里**每次算错都是静默的数据损坏**（顺序错乱、子块挂到别的分支、粘贴后全挂同一个父块），而 `NoteViewModel` 里的版本只能经 ViewModel 间接测。
  - `revive` **必须由调用方传 `nextId` 回调**：`nextBlockId` 是实例级递减计数器，粘贴是一次性预分配，这个语义收进纯函数就变成了隐藏状态。
  - 同一条判据适用于别处：**"是规则而不是编排"的逻辑放 `domain/`**，`ui/` 只留 Compose 与状态机。行内标记那一套（`domain/markup/`）就是这么落的。
- `NoteRepositoryImpl.hydrate()`：无 `cardId` 的孤儿块会被并入第一张卡片并重排 `sortOrder`；没有卡片时块挂在 note 本身。改保存/加载逻辑时别破坏这条规则。
- **`NoteUiState.toNote()` 是把块挂回卡片的唯一落点**（`NoteUiState.kt` 里 `block.copy(cardId = card.id)`）——仓库层靠 `cardMap[block.cardId]` 找归属，漏填/填错就等于"只改标题却把正文删了"。有 `NoteUiStateToNoteTest` 守着。
- `NoteViewModel` 未保存块用递减负数临时 id（`nextBlockId--`）。保存后通过 id 映射回写真实 `cardId` / `parentBranchId`。
- 块剪贴板：JSON 带 `CLIPBOARD_PREFIX` 前缀写入系统剪贴板；识别粘贴只看前缀。
- 入口：`MainActivity`、`ZhiLuApplication`（Hilt + WorkManager 周期提醒）。导航在 `ui/navigation/`。

## 数据库（强约束）

- 当前 `AppDatabase` **version = 6**（v6 加了 `note_blocks.emphasis` 与 `note_cards.accent`），schema 导出到 `app/schemas/`（已纳入版本控制，改表必须提交）。
- **禁止** `fallbackToDestructiveMigration()`。每次升版本必须在 `Migration.kt` 写 Migration，并挂到 `Migration.all`（`di/AppModule.kt` 使用）。
- Room / Hilt 走 **kapt**（不是 KSP）。`room.schemaLocation` 已在 `app/build.gradle.kts` 配好。
- `BlockType.value`（TEXT=1 … BRANCH=8）是持久化稳定值，**不可改序/改值**，导出 JSON 与库内都依赖它。
- 迁移验证在 androidTest：`MigrationTest`，需要 `connectedDebugAndroidTest`。

## 导出 / 导入

- 格式：JSON、Markdown、HTML（图片/公式内嵌 base64）、`.dtk`（ZIP：`note.json` + `media/`）。
- `.dtk` 的 `note.json` **必须含 `dtkVersion`**（当前为 1，见 `DtkExporter.DTK_VERSION`）；导入只支持 `.dtk`，高版本拒绝。
- 公式：HTML/Markdown 导出渲染为 base64 PNG；`.dtk` 保留 LaTeX 源码。
  - 准确说：**HTML 渲染成内嵌 base64 图片**（`class="formula"`，块级进 `.latex` 容器，整条被标记包住时按语义色上色）；**Markdown 保留 `$…$` / `$$…$$` 源码** —— `.md` 是文本格式，写死图片反而不可编辑。
- **两个导出格式的行内解析是同一份**（`export/InlineExportTokens.kt` 的 `inlineExportTokens`）：先按语义标记切段，再在段内识别公式 / 行内代码 / 链接。**别再让某个导出器自己写一套** —— 原先 HTML 与 Markdown 各写各的，结果"LaTeX 公式渲染为图片"**只在块级公式上成立**，行内 `$…$` 被当普通文字原样吐出去（真机反馈："html 公式未渲染"）。
  - 段内的普通文字要**继承本段的语义角色**（`{{i:正}}` → `Styled`）。第一版吐成 `Plain`，强调信息在导出里整个消失；`InlineExportTokensTest` 钉着这条。
  - `$$…$$` 必须排在 `$…$` 前面，与只读态词法器同一个理由（否则行内规则会吃到最后一个 `$`）。
  - **Markdown 里不许写 `<span style=…>`**：Markdown 没有颜色，内联 HTML 粘到聊天/邮件里会原样露出标签；更糟的是它会把 `{{k:$W…$}}` 包成 `<span …>$W…$</span>`，让不少渲染器不再把里面当公式解析。语义标记在 Markdown 里**只剥壳、留文字**。
  - **小节结构要保住**：两个导出器都**不要**再把各小节的块 `flatMap` 成一条平铺流 —— 那样小节标题会整个消失。HTML 每节一个 `<section class="card">`（编号徽标 + 标题 + 点数 + 身份色），Markdown 多节时落成 `##`。
  - 分支子块必须跟着导出（Markdown 用 `<details>/<summary>`，HTML 用 `<details open>`）：原先 Markdown 只写一行分支标题，**子块内容凭空消失**。
  - `color-mix()` 别用（要 Chrome 111+，而导出文件是发给别人的）：徽标底色在 Kotlin 里算成纯 hex，再用 CSS 变量传进去。
- **单元测试里跑公式渲染需要三个前置**（`HtmlExporterFormulaTest` 是范例）：
  ① `testOptions.unitTests.isIncludeAndroidResources = true` —— jlatexmath 要从 assets 读 `TeXFormulaSettings.xml` 与字体，读不到时 `TeXFormula` 静态初始化抛异常，导出器会**安静地走 `<code>` 降级分支**（很容易误判成"公式导出没实现"）；
  ② `testOptions.unitTests.all { forkEvery = 1 }` —— **每个测试类单开 JVM**。`TeXFormula` 的静态初始化只要失败一次（典型触发：某个**纯 JUnit 类**在 Robolectric 沙箱之外碰到它），这个类就永久不可用，之后渲染全抛 `NoClassDefFoundError`。症状极具迷惑性：**单独跑绿、跟别的类一起跑红**。代价是全量测试从 ~35s 涨到 ~3min；
  ③ 测试里手动 `JLatexMathAndroid.init(context)` —— 生产中这句由库自带的 `JLatexMathInitProvider` 干，Robolectric 不跑 ContentProvider。
  ④ `src/test/resources/robolectric.properties` 里 `sdk=34` —— Robolectric 4.13 在启用资源加载时最高支持 34，而模块 `targetSdk` 是 35。
- **JSON 备份必须内嵌图片字节**（`media[].data` = base64 data URL，导出时由 `SettingsViewModel.collectMediaData` 读入）。只写 `uri` 的话，备份里剩的是一个指向本机内部存储的路径，换设备/清过数据后永远恢复不出图。`.dtk` 反过来：图片作为文件放进 zip，不写 `data`（免得包体翻倍）。
- **备份必须保住三类引用**：`cards`（小节结构）、`Block.parentBranchId`（分支层级）、`cardId`/`mediaId`。导出时都要写，导入时统一走 `export/BackupRestore.kt` 的 `remapForInsert()` 换成负临时 id，再由 `NoteRepositoryImpl.replaceCards/replaceBlocks` 映射成真实主键。以前只写扁平的 `contentBlocks`，于是恢复后小节被 `hydrate` 合并成一张卡、子块变成顶层块 —— 改这块务必跑 `BackupRoundTripTest`。
- 有卡片时 `blocks` 必须留空、只有扁平块时 `cards` 必须留空：`Note.contentBlocks` 的语义是"blocks 非空就只用 blocks"。

## UI 状态与一次性提示

- **一次性提示（snackbar）要"先消费、再弹"，而且 `showSnackbar` 必须派发到屏幕作用域**：
  - 先消费：`showSnackbar` 会挂起到提示消失，若把 `clearMessages()` 排在它后面，用户看到提示时切走页面会取消协程 → 消息永远留在 state 里，之后每次进入该页面都重弹（真机反馈过）。
  - **但只做到这一步还不够（会踩第二个坑）**：`consumeXxx()` 会把 `LaunchedEffect` 的 key 变成 `null`，Compose 随即**取消并重启**这个 effect，挂起中的 `showSnackbar` 被一起取消 —— 表现是提示一闪即逝、甚至完全不出现。正确写法是让 effect 只负责消费 + 派发：
    ```kotlin
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        viewModel.consumeMessage()
        scope.launch { snackbar.showSnackbar(message) }   // ← 交给屏幕作用域，不受 key 变化影响
    }
    ```

## 行内标记与语义强调（这一块最容易改错）

三套**刻意取值分离**的颜色系统，改任何一套前先确认改的是哪一套：

| 用途 | 来源 | 落库字段 |
| --- | --- | --- |
| 语义色（要点/想法/注意/待办） | `EmphasisTone` → `ui/theme/EmphasisTones.kt` | 无（由文本语法表达） |
| 卡片身份色 | `domain/model/CardAccent.kt` 的固定轮转序 | `note_cards.accent`（`Int?`，null = 用户没改过，渲染时按序号回退） |
| 主题强调色 | DataStore | — |

- **行内语法的存储形态**：`{{k:要点}}` / `{{i:}}` / `{{w:}}` / `{{t:}}`，后缀 `-c`（只变色）/`-u`（下划线）。角色前缀必填，正文非空、不含花括号与换行，转义用 `\{{` `\}}`。**唯一的例外是公式**：标记体内可以整段出现 `$…$`（`{{k:$W \le 2^{n-1}$}}`）—— "给整条公式上色"只有这一种可存形态。
- **公式是行内标记的原子对象**（`domain/markup/MathSpans.kt`）：与公式相交的标记必须**完整包住**整条公式，切进公式内部一律不合法（`canCover`）。物化时不在公式内转义花括号（`_{\text{发}}` 写成 `_{\text{发\}}}` 会让 LaTeX 解析失败、退化成源码显示）；编辑器里选区端点落在公式内会**向外吸附**成整条公式。踩过的坑：早期没这层约束，`{{k:…}}` 被织进 `_{\text{发}}` 的花括号之间，写出 `{{w:$W_}}{{{w:\text}}…` 这种再也读不回来的碎片（真机反馈）。
- **编辑态渲染公式**（已实现，取代了早期"编辑态只能看源码"的结论）：`BasicTextField` 确实**不支持 inline content**，但不需要换编辑器也能做到 ——
  `InlineAtomLayer.kt` 里 `AtomStyleTransformation` 把公式源码设成 **Transparent（只改样式、不改长度）**，覆盖层再用 `onTextLayout` 的 `getCursorRect` 把公式图画在源码让出的空位里。
  - **偏移映射是恒等的**（没有 `OffsetMapping`），所以不存在"映射算错、光标乱跳"这类 bug；选中的仍是那段源码，只是看不见。
  - 早先试过"把源码折叠成 0 宽再画覆盖层"，真机上直接失败：不占宽度就会被覆盖层压住后文。**别走那条路**（P0 结论，设计文档 §8）。
  - **光标进入某条公式时它必须变成"可见的源码"** —— 这件事有**两半，缺一半就等于公式消失**：
    ① 覆盖层不再画它（`InlineNodes.hiddenMath` 按 `activeAtomOffset` 排除）；
    ② `AtomStyleTransformation` **必须撤销那一条的 `Color.Transparent`**（同一个 `activeAtomOffset` 传进去）。
    只做 ① 的话，公式既没有图、源码又是透明的，屏幕上只剩一块选区底色 —— 用户报的"点进公式公式就没了"就是这个。
    有 `AtomStyleTransformationTest` 守着（活动原子不透明、其余仍透明、紧贴两端也算活动）。
  - 渲染失败/未完成时覆盖层退回显示源码小字 —— 公式写错时仍看得见、改得动。
- **`InlineNodes`（`domain/markup/InlineNodes.kt`）是行内原子的统一定义**：坐标与 `InlineSpan` **同一套**（块内可见文本偏移），所以原子自动获得与标记同等的编辑行为，`InlineSpanAdjuster` 一行都不用改。
  - 公式：隐藏源码 + 覆盖层画图；行内代码：**就地套等宽样式**，不隐藏、不参与覆盖层（它没有"盒宽与图不符"的问题）。
  - 选区吸附用 `InlineNodes.snapOutside`（公式与代码都算）——**单测过不等于接线对**：这里踩过"改了 domain 但编辑器仍调旧的 `MathSpans.snapOutside`"，测试全绿而功能失效。
- **公式图绘制尺寸：一次算完，不做反馈环**（`ui/note/blocks/AtomBox.kt` 的 `drawScale`，纯函数 + 单测）。
  - 规则只有一条：**只缩小、不放大**，上限取「该行剩余宽度」与「行盒高度 × 1.25」里更紧的那个。1.25 是给含 CJK 的公式留的溢出余量（图 98px vs ASCII 行盒 82px，严格贴行盒会被压到 84%，肉眼可见地比只读态小）。
  - **不要**再去"让不可见源码占的盒子等于公式图"（曾用 `AtomBoxStyle` 调 `fontSize`/`letterSpacing`）。它必须靠「量盒宽 → 改样式 → 再布局 → 再量」的**反馈环**收敛，实测收益只有 17px 留白，却换来一整类 bug：字距 sp/px 串单位被夹成 +16sp（源码撑成两行）、`getCursorRect(end)` 取到的是**行尾换行符**（单行原子被判成折行）、按文本偏移做键的样式表被重新排版清空。全过程见设计文档 §14.5。
  - **覆盖层那个盒子的尺寸只该由"画什么"决定**：给它**定宽**会横向裁掉公式（源码比图窄时，实测 208px 的盒子装 573px 的图），给它**定高**会纵向裁掉公式（图比行盒高时，`MAX_HEIGHT_OVERFLOW` 允许的那 25% 余量正好被裁掉，表现是含 CJK 的公式像被切了下半截）。两次都真机栽过，所以现在**只 `offset` 定位，宽高都交给内容撑** —— 它是纯绘制层、不参与排版。
  - **量法本身也踩过坑**：`getBoundingBox` 在 `BasicTextField` 的算子里对某些偏移直接返回 null；判折行要看 `getCursorRect(end).top` 是否与起点同行。量之前先用真机数字确认"量法与渲染自洽"。
  - 代价（已知、接受）：源码天然比图宽时公式后面有一段留白。长公式的正解是「转为公式块」，不是把字距压回去。
- **行内公式 → 公式块**走 `InlineFormulaConversion.promote` + `NoteViewModel.insertBlockAfter`：前者纯函数（摘出正文与裸 LaTeX 源码，并收掉多余空格），后者沿用源块的 `parentBranchId` 并重排 `sortOrder`。
  能力经 `LocalFormulaPromotion` 下发（**与 `MarkChannel` 同一模式**），避免把回调穿过 `NoteEditScreen → BlockCard（两处）→ BlockContent → TextBlockEditor` 六层；只读态传 null。
- **行内链接已做**（`[文字](url)`，**自动识别**，用户已确认）：`InlineKind.LINK` + `InlineNodes` 里的 `inlineLink` 正则；编辑态与只读态都套"强调色 + 下划线"（编辑态显示全文以便修改；只读态只显示文字）。落地前查过全库含 `](` 的块数为 **0**，所以自动识别不改写任何既有内容。
  - 只读路径的链接色**必须由调用方传**：`buildInlineLatexText` 是普通函数（非 @Composable），在里面读 `MaterialTheme` 编译不过（踩过）；已加 `linkColor` 参数。**`RichText` 是它唯一的调用点**（已查证），所以不存在"其它调用点待接"的问题。AI 消息气泡走的是自己的渲染路径（`AiMessageContent` + `rememberLatexImage`），与本函数无关。
- **语法只存在于存储形态**。编辑器的缓冲区（`TextBlockEditor` 的 `buffer`）**只有可见文本**，标记放在同级的 `spans: List<InlineSpan>` 里，保存时才 `materialize`（设计文档 §3.7）。所以：任何"读 `Block.content` 直接展示"的地方都必须先 `InlineMarkup.stripMarkup`；导出、摘要、预览、AI 摘要都算。新增消费点时别忘了这一条。
- **编辑态与只读态必须对同一段文本解析出同一组公式**（`InlineLatexParsingParityTest`）。两条路径的解析器不同（编辑态 `InlineNodes`/`MathSpans`，只读态 `buildInlineLatexText` 自己的词法器），不一致的表现就是"公式在某一侧凭空消失"或"中间的文字被吞掉"。两条已经踩过的坑：
  - **只读态词法器里块级 `$$…$$` 必须排在行内 `$…$` 前面**。反了的话 `$([^$]+?)$` 会匹配第一个 `$` 到最后一个 `$` 之间的全部内容 —— `$$E=mc^2$$\n行内：$a$` 会被解析成一条源码为 `E=mc^2$$\n行内：` 的"公式"，**中间那段文字在只读态直接消失**。
  - **分组号与分支判断要一起改**：给词法器加一条分支会移动所有 `groupValues[i]` 的下标，而"先判某个 group 非空"的写法一旦对不上号，就会把公式当链接文字吞掉（本轮踩过：`$a+b$` 只显示成 `a+b`、`formulas` 为空）。改成先取出 `groupValues` 到有名字的局部变量再判。
  - `InlineNode.contentOf` 要同时剥掉 `$$` 与 `$` 两种定界符：只剥一层会让块级公式在编辑态把 `$E=mc^2$` 喂给渲染器、只读态喂裸源码，两侧输入不同、渲染就可能不同。
- 文本格式代码必须放在 **`domain/markup/`**（`ui/` 之下的东西 domain 不能依赖）。`InlineMarkup`（解析/物化）、`InlineSpanAdjuster`（编辑对 span 的影响）、`InlineMarkupNormalizer`（规整破损语法）、`EditorTextTransition`（打字路径的转移，含手打辅助 `{{k:…}}` 识别）。
- 打字走的是 `BasicTextField` 的 `onValueChange` **直写路径，不经过 `commit()`**（后者只被工具条调用）。想在任何输入上做手脚，必须挂 `onValueChange` / `applyTypedText`。这条踩过：手打辅助第一版挂在 `commit()` 上，真机打 `{{k:test}}` 只是被转义成字面量。
- AI 工具写入与系统剪贴板粘贴是**文本直接落库**的两个入口，必须过 `InlineMarkupNormalizer.normalize`。
- `Block`/`KnowledgeCard` 都是带默认值的 data class，加字段时注意所有 `copy`/重建点（漏了会静默丢数据）。
- **加粗是一种「笔触」（`InlineBrush.BOLD`），不是语义角色**：它没有 `{{}}` 语法，走既有的 `**…**`。做成笔触是为了让 `InlineSpanAdjuster` 的 8 条规则、`normalize`、`apply`/`clear` 一行都不用改就能获得同等的编辑行为。代价是 BOLD span 的 `tone` 字段无意义（渲染时忽略），这是有意的取舍。
- **底部工具栏「标记」→ 编辑器**走 `MarkChannel`（`ui/note/blocks/MarkChannel.kt`）：屏幕层只下发一次性指令（含 `blockId`），编辑器认领后清空。**不要**为此把选区/「标记中」提升到屏幕层——那是把编辑器掏空并制造第二份状态源。副作用是底部入口无法回显「标记中」（回显归正文旁那条工具条）。
- **无障碍色板**：`LocalAccessibleEmphasis` 由 `ZhiLuTheme` 一处下发（设置项 `accessible_emphasis`）。语义色用在 6 处渲染点，新增用色点时记得读这个 local 并透传给 `emphasisToneColor`/`emphasisInkColor`/`inlineToneSpanStyle`。导出端（`InlineMarkupExport`）在 domain 层读不到 local，目前不跟随该开关。
- **「一色一义」是硬规则**：卡片身份色（`CardAccent`）不得与四个语义角色的 primary 撞值 —— 语义色标**内容**、身份色标**容器**，撞值会把两个信号混成一个。有单测守着这条（`EmphasisTonesTest`）。所以「注意」用正红 `#C0392B`（`AccentColor.SCARLET`）时，卡片那一档必须是更暗的朱红 `#B23B32`（`CardAccent.VERMILION`）。加色一律**追加在末尾**：`AccentColor` 按 name 持久化、`CardAccent` 的声明顺序就是轮转序，插中间会改掉既有笔记的配色。
- **设置里的强调色板每行 4 个**（`SwatchesPerRow`）：单元格宽度 = 内容宽 / 每行个数，圆的直径不能超过它，否则会被 `weight(1f)` 压成椭圆。7 色时一行勉强够（~37.7dp），8 色就塌了。

## AI 工具（`domain/ai/usecase/AiToolExecutor.kt`）

- 10 个工具：`list_notes` / `search_notes` / `get_note` / `create_note` / `update_note` / `add_blocks` / `set_block_emphasis` / `add_tags` / `add_todos` / `delete_note`。加工具时同步更新 `definitions`、`execute` 的分发，以及系统提示词（`data/ai/AiAssistantRepositoryImpl.kt` 的 `SYSTEM_PROMPT`）——**提示词和 schema 是两份真相，改一份不改另一份模型就会用错**。
- **分支子块**靠 `branch` 块的 `children` 数组：`parseBlocks` 递归解析，并给每个新块发**唯一的负临时 id** 当 `parentBranchId`，由 `NoteRepositoryImpl.replaceBlocks` 的 `idMapping` 重映射成真实 id。**绝不能都留默认的 0**，那样多个分支的子块会全挂到同一个父块下。
- **待办项挂在笔记上，不是挂在块上**（`todo_items.noteId`）：所以 `add_todos` 只要 noteId，不需要 blockId。待办块只是"这篇笔记的待办清单"的显示位。
- 任何写入都会**重建块 id**，工具描述里已写明"先用 get_note 取最新 id"。
- 标题（笔记标题、小节标题）是纯文本，**必须过 `stripMarkup`**；正文才走 `normalize` 保留标记。

## 约定（与默认不同或容易踩坑）

- Commit：`feat:` / `fix:` / `refactor:` / `docs:` / `test:` / `style:` / `chore:`（见 `CHANGELOG.md`、git log）。
- 禁止 `util` / `utils` / `helper` / `manager` / `base` / `other` 垃圾桶包；Compose 单 Screen 单文件，超过 ~300 行拆子组件（命名如 `HomeTopBar`）。
- 状态统一 `StateFlow` + `collectAsState()`，不用 LiveData；日志只用 Timber，禁止 `println` / `Log.d`。
- suspend 函数自行吞掉/包装异常，不抛到 UI（`RepositoryResult`）。
- 注意：`RepositoryResult` 物理在 `domain/repository/RepositoryResult.kt`，包名却是 `com.example.zhilu.common`——import 用 `common`，不要按目录新建 `common` 包。
- 涉及 Android Context 的单元测试用 Robolectric（exporter、导入等）；纯逻辑测试用 JUnit4 + MockK。
- 无 lint/detekt 额外配置；验收以 `lintDebug` 零错误为准。

## 入口与导航（改动频繁，先看这里）

- **新建 / 导入的唯一入口是底栏正中央的 ＋**（`ui/navigation/BottomBar.kt` 的 `CenterCreateSlot` + `ui/create/CreateSheet.kt`），由 `AppShell` 托管，四个平级页都能唤起。首页**不再有 FAB**（`HomeCreateFab` 已删除）。
  - 所以**行左划/右划露出操作槽时不需要再收起任何悬浮件** —— 以前 FAB 会压住删除键，`HomeScreen` 里那段"露出态隐藏 FAB"的联动已随 FAB 一起移除。若哪天把 FAB 加回来，记得把这层联动也加回来。
  - ＋ 上浮 24dp，必须画在底栏 `Surface` **之外**：Surface 会把内容裁到边界内，放在里面只剩半个圆（真机第一版就是这样）。
- 设置页只留**导出**；导入（JSON / .dtk）在 ＋ 的弹层里。逻辑本体在两个共用用例：`RestoreJsonBackupUseCase`、`ImportKnowledgeUseCase`。
- 「AI 创建」= 跳助手页并把输入框预填成「帮我创建：」，靠助手路由的可选参数 `assistant?prefill=`。
- **`Destination.path` 与 `Destination.route` 是两件事，别混用**：`path` 是在 NavHost 里注册的**路由模板**（助手页是 `assistant?prefill={prefill}`），`route` 才是拿去 `navigate()` 的**实际路由**（`assistant`）。混用的两种翻车这轮都踩了：拿模板比选中态 → 助手页被判定为非平级页、**底栏整条消失**；拿模板去 navigate → `{prefill}` 被当字面值传进去，**输入框里出现 `{prefill}` 这行字**。涉及助手页的三处（`TopLevelRoutes`、底栏 `isOn`、`switchTopLevel`）都必须用 `route`。
- 底栏 tab 的选中态**只改颜色**（原型 `.nav .tab.cur{color:var(--accent)}`，没有胶囊背景）；图标另做成对切换（实心 ↔ 描边）——纯靠颜色表达选中，对色觉障碍与灰度屏是失效的。
- **小节默认收起，但两种状态都能展开**：`CardMapper.toDomain` 显式 `isExpanded = false`，打开笔记先看到目录（标题 + 摘要 + 收起箭头）。`KnowledgeCard.isExpanded` 的默认值仍是 `true`，那是给"扁平笔记的隐式单卡"用的（只有一节时收起等于什么都看不见）。
  - 点一张收起的小节会顺手把它展开；**只读态同样有效**（`KnowledgeCardItem` 的 `clickable` 只按 `!isGenerating` 门控，`onFocus` 才判 `isEditing`）。
  - 曾经只读态整卡不可点（`enabled = isEditing`），后果是 **AI 刚建的笔记点进去全是空壳卡片** —— 小节默认收起 + 只读态展不开 = 用户以为没生成内容。别再把它锁回去。
  - 展开状态是**会话态**：没有落库列，重开笔记回到目录。
- **只读正文可选中复制**（`SelectableBlockText`）：`SelectionContainer` 提供划词与系统复制工具栏；长按不动弹块级菜单（复制 / 引用到 AI）。TEXT 块与**分支标题**都用它。
  - **三个动作必须共存，靠"是否消费事件"分流，顺序不能换**：① 点一下 → **不消费**，让事件往上冒给祖先的 clickable（分支标题靠它展开、正文块靠它展开所属小节）；② 长按后拖动 → 交给 `SelectionContainer` 划词；③ 长按不动 → 弹块级菜单，**只有这一种情况才消费事件**（否则祖先会把长按当成点击，展开态乱跳）。
  - **不要用 `combinedClickable(onClick = {})` 挂菜单**：那个空 `onClick` 会把点击**消费掉**，祖先的 clickable 收不到 —— 表现是"点一下没反应"（真机实测：分支标题点不动、正文块点不开小节）。必须用裸 `pointerInput`。`FormulaProbeActivity` 的 F 段并列了正确与错误两种写法，改这里之前先看那一屏。
  - **不要把块级菜单挂在最外层**（`ReadOnlyBlock` 曾经的 `detectTapGestures(onLongPress = …)`）：父级指针输入先于子级收到事件，外层一旦认领长按，`SelectionContainer` 的选区永远起不来 —— "文字看着能选、实际选不动"。
  - 菜单判定窗口 **600ms，比平台的 500ms 晚一档**：同时判的话"长按后立刻拖"会**同时**弹出选区与菜单（真机两个一起出现）。
  - 位移判据用**相对 DOWN 的累计位移**，不是 `positionChange()`：后者每帧只给"相对上一帧"的位移，匀速拖动时单帧很小，会漏判成"没动"。
  - 复制内容一律走 `readOnlyBlockClipboardText`（按块类型分派 + 剥行内标记），有 `ReadOnlyBlockTest` 守着。
  - 链接块不套 `SelectionContainer`：链接正文靠点击打开，套上会把手势吃掉。
- **分支块（BRANCH）的展开态与编辑态无关**（`CardBlockList` 的 `isBranchExpanded(states, id)`，纯函数 + `BranchExpandedRuleTest`）：只读态曾被写死成"永远展开"，于是箭头照画、点了没反应，分支内容永远收不起来。默认展开（新加的分支不至于看起来像空的）。
- **公式块不要套 `horizontalScroll`**（`LatexBlockEditor` 的 `LatexFigure`）：横向滚动会把子项宽度约束变成**无限**，而 `LatexImage` 的"缩到放得下"读的正是 `constraints.maxWidth` —— 拿到 `Infinity` 时那条分支被跳过（`scale = 1`），超宽公式于是**被裁掉右半截**（真机实测：`\int_a^b f(x)dx = \lim\sum\dots` 后半段整段消失，编辑态与只读态都中招）。现在只留一个有界容器，公式等比缩小、完整可见；极长公式缩得偏小本就该走「转为公式块」。诊断屏 `FormulaProbeActivity` 的 E 段保留了旧写法的反例（B 变体），想加回滚动先看那一屏。

## 文档索引

- `README.md` — 功能、结构、环境要求。
- `docs/superpowers/specs|plans/` — 近期设计与实现计划（含具体验收命令）。
- `2026-07-06-zhilu-design.md` — 总体设计与编码规范来源。
- 根目录 `2026-*.md` 与 `.trae/` 为历史设计稿；`.trae/`、`.claude/`、`.worktrees/`、`local.properties` 已 gitignore。
