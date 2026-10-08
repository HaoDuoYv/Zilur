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

## AI 配置（多供应商）

**一套配置 = 一个 `AiService`，列表存在 `AiSettings` 里**（`domain/model/AiService.kt`）。
DataStore 只存一个 JSON 字符串键 `ai_settings`，不再拆成一堆 Preferences 键 ——
服务是**列表**，拆键就得自己编号、还得处理删除留下的空洞。

- **不预设模型清单**。早先的 `AiProvider` 给每个供应商挂 `textModels`/`visionModels` 硬编码清单，那是错的：模型增删比 app 发版快得多，清单过期后用户填不了新的、也删不掉下线的。**模型 ID 一律手填**，供应商预设（`AiVendorPreset`）只负责带出端点。
- **只有一个 `model`，没有 `visionModel`**。早先分两个字段是因为默认了"识图要用专门的 VL 模型"；现在主流大模型一个就能同时处理文本和图片，逼用户填两个字段只会让人以为必须配两个服务。发图时用的就是 `model` 本身。
- **端点永远可编辑**。`AiVendorPreset` 只是填表便利，不是白名单 —— 自建网关 / 中转站改端点即可。
- `AiSettings.resolveActive()` **三层回退，顺序固定**：显式 `activeId` → 第一个启用的 → null。第二层不能省：用户删掉或停用当前服务时 `activeId` 会变成悬空引用，不回退就会出现"界面写着当前使用 X、实际一个请求都发不出去"。
- `AiSettings.fallbackChain()` 是**去掉自己、从当前之后绕回队首**的启用服务序列，保证每个最多试一次。
- **失败回退只在"一字未出"时发生**（`AiAssistantRepositoryImpl` 的 `hasEmitted`）。一旦往外吐过增量就不能换服务重来 —— 那会把两段回答接在一起，用户看到"两个模型各说了一半"。有 `AiSettingsTest` 钉住解析与回退链。
- **旧单配置会自动迁移**（`UserPreferences.legacyAiSettings`）：只读一次旧的 `ai_endpoint`/`ai_api_key`/`ai_model` 键，且**只在 `ai_settings` 为空时**执行 —— 否则用户清空服务列表后旧配置会"复活"。
- **AI 配置有独立入口**：`Destination.AiConfig`（「我的」→ AI 配置）。行尾用 `aiConfigSummary()` 写出「当前使用：X · 共 N 个」，三种状态各给一句（没接入 / 没可用的 / 正常）。
- **一键切换在两处**：配置页点某一行即可切换（最高频动作不该藏在菜单里）；助手页顶栏下方的 `AiModelBar` 点开底部弹层切换。**模型条放在顶栏下方而不是挤进标题** —— 标题只承载"这是哪个页面"，模型是可切换的**状态**，混在一起用户会以为模型名是标题的一部分。
- **AI 的三处列表一律是「胶囊块」，不是裸行**（配置页服务列表、切换层选项、顶部模型条）：块内左侧 `ProviderAvatar`（圆角方块 + 品牌色 + 首字），中间名字 + 供应商·模型，右侧操作。套一层胶囊是为了把"这是一个 AI"的边界画出来 —— 一行里同时有整块点击/开关/编辑/删除四个交互，裸行时它们就是四个散落图标，用户分不清点到哪是哪。
  - **居中的写法有坑**：`Arrangement.Center` + `horizontalScroll` 是错的 —— 居中下横向滚动会把溢出部分推到屏幕外且**滚不回来**（滚动起点在内容中间），长名字直接消失。用 `widthIn(max = 220.dp)` 限宽。
  - 也**别用"两侧等宽占位配平"**去把内容挤到中间：右侧的勾（图标 + 间距）比预估的占位宽，整组会偏左。让**整组作为一个单位**居中，别自己算。
  - `ProviderAvatar` 取首字的规则是**先找 ASCII 再退回汉字**（`OpenAI` → `O`，`通义千问` → `通`）。用首字而不是真 logo：各家 logo 是注册商标，且要随包分发一堆矢量资源。
  - 品牌色取各家主色，**认不出来的回落到主题主色**（与其编一个颜色，不如跟随外观）。
- **`SettingsGroup` 的 `Spacing.Md` 是给"设置行"之间留的**，用在胶囊列表上会松得像散了架。列表处自己套一层 `Column(spacedBy(Spacing.Xs))` 重排。
- **`maxTokens` 是「思考 + 正文」的总预算**：推理模型（会输出 `reasoning_content` 的那些）的思考 token 也算在内 —— 给它 2048 等于正文没额度可用，详见「AI 流式响应」一节。
- **密钥没有额外加密**，就在 DataStore 里。UI 上必须**诚实标注**这一点（别说"本地加密存储"）：它保护不了 root 设备或已导出的备份。真要做需要 EncryptedSharedPreferences 或 Tink，是独立的一件事。
- 改这块要跑：`AiSettingsTest`（解析与回退链）、`assembleDebug` + 真机看一眼配置页与切换层。

## 主题与外观（三个正交维度，别混成一个开关）

| 维度 | 类型 | 落库键 | 决定什么 |
| --- | --- | --- | --- |
| 外观 | `ThemePalette`（PAPER_INK / ANIMAL_ISLAND） | `theme_palette` | 长什么样：配色、圆角、卡片身份色轮转序 |
| 明暗 | `ThemeMode`（SYSTEM / LIGHT / DARK） | `theme_mode` | 亮还是暗（每种外观自带浅深两套） |
| 强调色 | `AccentColor`（8 档） | `accent_color` | `primary` 那一族 |

- **色值全在 `ui/theme/ThemePalettes.kt`，存储层只放名字**（与 `AccentColor` 同一个道理）。`palettePaint(palette)` 返回一份 `PalettePaint`：浅深两套 `ColorScheme`、两套 `ExtendedColors`、`Shapes`、卡片身份色轮转序，外加**该外观自己的强调色表**。
- **强调色的键在两套外观下一一对应、色值不同**：纸墨的 `CRIMSON` 是绛红，动森的 `CRIMSON` 是樱花粉。这样**切换外观不会丢用户已选的档位**（`AccentPaletteTest` 钉着"两套色值必须不同"）。
- **圆角跟着外观走**（`PalettePaint.shapes`）：动森整体放大约 1.6 倍，"一切都是圆的"是它观感的一半。取圆角优先用 `MaterialTheme.shapes`，别直接写死 `RoundedCornerShape(12.dp)`。
- **`cardAccentColor` 与 `accentRoles` 都要传 `palette`**（缺省是纸墨）：用户**改过**的卡片身份色（`stored != null`）原样保留——那是显式选择；没改过的按当前外观轮转，否则奶油底上会钉着 7 个纸墨深色。
- **语义色分两种用途，取色函数也不同**：
  - **小标记**（块级色条、gutter 圆点、设置页预览）走 `emphasisToneColor` → `PalettePaint.toneBlock`；
  - **行内文字**走 `emphasisInkColor`（浅色用一套加深墨色，深色用 accent 的深色 primary）。
  为什么不能都直接用 accent 的 `primary`：两者要满足的对比关系不同。动森的「蜂蜜」`#D9A441` 当按钮填充配深字很好看（6.16），但压在奶油底上当色条只有 **2.08** —— 等于色条消失（对照纸墨 5.67）。所以小标记另取一档更暗的暖金 `#A0741C`（3.04~3.88）。有 `toneBlockColorsAreVisibleOnEverySurface` 守着（门槛 3.0，图形按 WCAG 非文本要求）。
  - 反过来说，**`emphasisInkColor` 那套墨色不随外观变**：实测在奶油底上 7.40~7.75、叠 20% 语义底色后仍 5.8+，再往亮里调反而掉到 4.5 以下。别为了"统一"去改它。
- **只读行内路径在 `RichText` 里读一次 `LocalThemePalette`** 再往下传（`buildInlineLatexText` / `rememberInlineLatexContent` / `inlineToneSpanStyle` 都是普通函数或带默认值的，读不了 CompositionLocal）。**别让 8 个 `RichText` 调用点各传一遍** —— 那是漏一个就有一处配错颜色的做法。
- **压在有填充色块上的文字一律走 `inkOnFill(fill)`，别写死 `Color.White`**（gutter 激活序号、语义色圆里的首字都踩过）。深色模式下主色是**提亮后**的浅色，白字实测只有 **1.68**（动森嫩叶绿）、2.01（纸墨浅墨蓝）、身份色圆上 2.64；浅色模式下这些恰好都该配白字，所以**这个缺陷只在深色模式露出来**。
  - 阈值不是魔数：`FILL_LUMINANCE_FLIP` 是"白字与深字对比度相等"的那个填充亮度（解二次方程得来），取它就是"永远选更清楚的那一边"。
  - **别自己写第二份亮度/对比度公式**：`contrastRatio(a, b)` 用 Compose 的 `Color.luminance()`。我另写了一份按 WCAG gamma 线性化的版本，与 Compose 口径差了近一倍（同一个 `#A8B8D2`：0.47 vs 0.72），于是"该配深字的亮底"被判成该配白字，阈值也定错了。
  - 已知边界：中等明度填充两种字色都到不了 4.5（最多 3.0~3.4），是物理天花板；那两处是短标签，按 3.0 卡。要更高只能改填充色。
- **`on*` 角色要用数值定，不能凭"浅色配白字"的直觉**。动森浅色的 `onSecondary`（桃粉上）白字只有 **2.62**、`onTertiary`（天蓝上）3.50、`onError` 4.45 —— 前两个换深墨后是 6.64 / 4.98，第三个把填充压深一档后白字 5.36。`AccentPaletteTest.printRecommendedOnColors` 会把每个填充的"当前值 vs 最佳值"打出来，调色时直接抄。
- **标签颜色不跟外观走**：`tag.color` 是**落库数据**（建标签时从 `TagColors` 抽的），按外观重映射等于悄悄改用户数据。`AnimalIslandTagColors` 只给**新建**标签用。
- **动森的「组件外观」不只是配色**（只改色值等于没换 UI —— 用户这么反馈过）。它有一套材质语言，统一落在 `PalettePaint.componentBorder`：**纸墨 = `Color.Unspecified`（无描边，靠阴影分层）；动森 = 有描边，靠描边立形状**。各组件读**这一个值**决定走"纸"还是走"塑料"，**不要各自去比 `ThemePalette.ANIMAL_ISLAND`**。
  - `ui/component/ToySurface.kt` — **「玩具按钮」**：底下一层同色实心当"厚度"、上面那层抬起来当面，按下时面落到厚度上。参考仓库里 Button / Switch 手柄 / Checkbox **全都**用这个手法，是那套 UI"像玩具"的主要来源。**普通 `shadow()` 给不出这个效果** —— 阴影是虚的，厚度是实的。厚度色用 `shade(面色)`（同色相压暗，对应参考仓库 `ShadowBtn` 与面色 `BgColor` 的关系）。
  - `AppCard` — 动森下加 2dp 描边 + `Modifier.shadow`（**暖褐** `spotColor`，不是中性黑；中性黑压米白底会发脏）+ 20dp 圆角；`irregular = true` 时换成**四角半径不等**的手作圆角。
  - `AppSwitch`（`ui/component/AppSwitch.kt`）— 动森下自绘（胶囊轨道 + 2.5dp 描边 + 浮起手柄）；纸墨仍用 M3 `Switch`。**开启色用强调色而不是参考仓库的 `SuccessColor`**：绿色已被语义角色「想法」占掉，「一色一义」不允许再拿它当"开启"。
  - `TagChip` / `SegmentedToggle` — 动森下加描边。
  - `HomeSearchField` — 动森下胶囊 + 2.5dp 描边 + 底部厚度。**刻意不做参考仓库那种"聚焦整体上浮"**：布局盒高度不变、底部会空出 3dp 位移，字段密集处会看着在抖；这里只换描边色。
- **`animalHandDrawnShape` 别再改回 `GenericShape` 手画贝塞尔**（`ui/theme/IrregularShape.kt`）。第一版照参考仓库 `Modal.kt` 的 `AnimalModalShape` 自己写了一整圈曲线，**四个角渲染成内凹的尖角**、卡片像被咬了一口 —— `GenericShape` 画错方向不报错，只安静地画出错形状，是**真机截图**才发现的。现在用 `RoundedCornerShape` 逐角指定不等半径：观感来源相同（不等角半径才是"手作感"的真来源），路径交给框架生成就不会写反。
- **`ColorScheme` 的"没显式给的角色"会落到 material3 的默认值上，而那是淡紫**。踩过：`ModalBottomSheet` 的容器读的是 `surfaceContainerLow`，我没设过，于是真机上新建弹层的底色是 `#F7F2FA`（两套外观都没有这个色）。M3 的**组件**（底部弹层 / 菜单 / 对话框 / 滚动条）会读 `surfaceContainerLowest…Highest`、`surfaceBright`、`surfaceDim`、`inverseSurface`、`inverseOnSurface`、`inversePrimary`、`surfaceTint`，**两套外观都要显式给全**（纸墨与动森各一组，见 `ThemePalettes.kt`）。
  - 判据不是"代码里有没有引用"，而是"**M3 组件会不会读**"——上面那些角色在 app 代码里 grep 不到，但组件内部在读。
- **`ToySurface` 的高度是显式参数（`faceHeight`），别改回 `matchParentSize()`**。第一版靠它对齐两层，外层若没有固定尺寸（比如只有 `wrapContentSize` 的圆钮）厚度层就退化成 0 高度、**按钮整个消失**。现在高度由参数决定，外层尺寸从它推出来，不存在这条退化路径。改这个签名时记得同步 `BottomBar` 的调用点。
- **代码块用"深色终端"配色，两套外观都是**（`PalettePaint.codeSurface` / `onCodeSurface` / `codeBorder`）。参考仓库的 `AnimalCodeBlock` 就是 `#2B2118` 底 + `#E8D5BC` 字（配字对比 11.01），而它的浅色主题也用这套。理由是代码块**越像另一个世界越好**：它和正文性质不同，用浅底会跟周围卡片糊在一起。
  - 顺带修掉纸墨原来的问题：它之前拿 `surfaceVariant` 当代码底，也就是**灰底灰字**，跟卡片只差一档明度、几乎看不出是个代码块。
  - 导出端（`HtmlExporter` 的 `<pre>`）跟着改成同一组色值，并有 `exportNote_codeBlockUsesTerminalPalette` 守着 —— **导出件与 app 里看到的必须是同一个东西**。
  - 动森深色下代码底 `#1B1510` 与卡面 `#2E251C` 只差 1.20，光靠明度分不开，所以**描边是必需的**（`#8A7B66`，与卡面 3.65）。
- **分割线在动森下画成波浪**（`ui/component/WaveDivider.kt`）。参考仓库是位图（`wave_yellow`）且按 `FillHeight` 拉伸；这里改成**现画的正弦波**：位图在高密度屏会糊、拉到别的宽度上浪形会被压扁、而且两套外观得各备一张图。**波峰数按宽度算而不是写死个数**，所以容器多宽浪的疏密都一样。
- **`GeneratingBadge` 的指示器跟着外观换**：纸墨是 M3 转圈；动森是**三颗挨个弹起的小球**（`BouncingDots`）。刻意**不是转圈** —— 动森那套东西没有"旋转"这个语汇，它的一切都是浮、沉、弹。
  - **没有照搬参考仓库的 `IslandAnimation`**（会摇的树 + 游动的鱼）：那是主视觉动画，几百行矢量路径、还用 `System.currentTimeMillis()` 驱动鱼，塞进一个 12dp 高的徽章里完全看不出是什么，却会拖慢每次重组。取的是它的**动势**，不是它的实现。
- **改完外观必须在真机上看一眼**：`lintDebug` 与 481 条单测全绿也照不出"形状画反了""底色是淡紫"这类问题。两轮都是靠截图才发现的。
- **模拟器的合成点击测不了 Compose 的展开态**：`adb shell input tap` 打不开知识卡片（点箭头、点卡片都试过，截图逐像素相同）。要看展开后的块，别在这上面磨 —— 直接把待验的块 `sortOrder` 改成负数（提到最前）重开，或者走导出路径用文本断言。改完记得把注入的测试块删掉。
- **外观有独立入口**：`Destination.Appearance`（"我的" → 配色与主题）。外观项已经长到三组 + 配色缩略卡，混在设置长列表里既难找也没空间。入口行尾用 `appearanceSummary()` 写出「当前外观 · 明暗」，不进去也知道现在是什么。
- **动森的色值来自参考仓库 `E:\study\gitproject\ui\AnimalIslandUI` 的 `theme/Color.kt`**（`ml.liuyuhong.animalislandui`）：
  - **直接用原值的**：`BgColor #F8F8F0`（页面底，原来是凭观感调的 `#FBF6E9`）、`TextColor #794F27`（正文暖褐）、`BgColorContent #F7F3DF`、`BgColorSecondary #F0E8D8`、`BorderColorLight #C4B89E`、`PrimaryColorBg #E6F9F6`，以及 `App*` 那组 NookPhone 图标色用作**卡片身份色轮转**与**强调色板**。
  - **必须改值才能用的**：参考实现是展示型 UI，不承担正文对比度。它的 `PrimaryColor #19C8B9` 配白字只有 **2.10**、`TextColorSecondary #9F927D` 只有 **2.86**、`SuccessColor` 2.25、`FocusYellow` 1.55、`WarmPeachPink` 2.40。这些都**按同一色相压暗**到过线（青绿 → `#0F766D`，次级文字 → `#6D6455` 等），保留"同一个游戏"的观感。
  - **参考仓库没有暗色方案**（只有 `values-night/themes.xml`，无 `colors.xml`），所以动森的深色是**按它的品牌色相推的**：青绿提亮、底色取"夜色暖褐"而不是把米白压黑。
  - **一动参考值就要回去量**：参考的 `#E18C6F` 压到 `#B37059` 时落进了"白字 3.90 / 深字 4.46"的中等明度带，两种字色都到不了 4.5，得再压一档到 `#8D5745`。
- **新配色必须过对比度测试**（`AccentPaletteTest`，WCAG 相对亮度，门槛 4.5）：逐对检查 8 档强调色 × 2 套外观 × 浅深 ×（实心主色 + 容器色），外加背景/卡面/凹陷面上的正文与次级文字。**动森这套前后被它挡下来七次**——叶绿配白字 3.46、绣球紫 4.12、薄荷 2.84、苔绿 3.30、樱花粉 3.07、珊瑚 3.13、温灰 4.39，肉眼完全看不出来，全是量出来才发现的。已知例外只有纸墨浅色的次级文字（4.10，见 `acceptedShortfalls`），**逐条列出而不是整体放行**。
- **`docs/theme/` 下有预览页与真机截图**：`palette-preview.html` 由 `ThemePreviewGeneratorTest` 从 `palettePaint()` 直接生成（与真机同源），改色后重跑该测试再截图即可。设备不在手边时用它看配色。
- 组件里读 `LocalThemePalette` 即可，由 `ZhiLuTheme` 一处下发，别逐层传参。

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

## AI 任务与互斥（全局单任务）

- **同一时刻只允许一条生成任务**：`AiTaskManager.submit()` 里的 `synchronized(submitLock)` 是唯一裁决点，返回 `AiSubmitResult.Accepted/Rejected`。UI 的 `isGenerating` 只是**显示投影**，不再承担门控职责——它由 `taskManager.state` 异步回填，拿它当锁一定有空窗期（真机 bug：任务还在跑却允许再发一条）。
  - 被拒时**不产生任何副作用**：`AssistantViewModel` 保留输入框、图片、引用，只弹提示。别在 Rejected 分支里清输入。
  - 已完成的任务**留在任务表里但不算活跃**（`AndroidAiTaskHost` 靠差集发通知、笔记页靠「刚成功完成」消费结果），所以别写「任务完成就把它删掉」。
- `AssistantUiState` 只存一份任务（`runningTask`，全局唯一），其余全部派生：`isGenerating`（全局，输入栏显示「停止」）/ `activeTask`（属于当前会话，渲染流式气泡）/ `isStreamingVisible`（本会话在生成，滚动锚点与工具高亮）。
  - **切会话 / 新建对话不要手动清任务字段**（旧实现这么干，顺手把互斥信号一起清了）。会话隔离由 `activeTask` 的派生天然完成。
  - 新建会话的「回流」（任务解析出会话 id 后把视图带过去）只认**本 VM 提交**的那条任务（`pendingOwnTaskId`），否则刚点开空白对话的用户会被别人的任务拽走；用户主动切会话/新建对话时作废这个标记。
- 当前会话与输入草稿镜像进 `SavedStateHandle`（`persistDraft()` 一处集中镜像，别在六七个写入点各写一遍）。

## AI 流式响应（结束判定 / 空产出 / 截断）

- **「流读完了」不等于「流正常结束」**：`LlmApiClient.chatStream` 原先用 `readUtf8Line() ?: break` 收尾 —— 连接被提前关闭时循环"正常"退出，空文本走成功路径落库成一条 **0 长度 ASSISTANT 消息**（界面一个空白气泡，且不报错）。结束判定现在集中在 `SseStreamAssembler`：收到 `data: [DONE]` **或**某个分片带 `finish_reason` 才算正常结束，否则 `finish()` 抛错。改这里先读 `SseStreamAssemblerTest`。
- **正常结束但既没正文也没工具调用，同样是失败**："成功但空"只会变成空白气泡（历史 bug：`ai_messages` 里那几条 `length=0` 的 ASSISTANT）。
- **`finish_reason == "length"` 一律当失败**：被 `max_tokens` 截断的回答不可信 —— 正文可能只说了一半，工具调用的 arguments JSON 也可能是残的。给的是可执行提示（去 AI 配置调大上限），而不是把半句话当结果存下来（半截回复留在会话里只会被当成完整结果看）。
- **推理模型的思考 token 也计入 `max_tokens`**（「回答莫名其妙只有开头几个字」的根因）。真机实证：`glm-5.3` 一次小问题的 `reasoning_content` 就吃掉 1599 token（`maxTokens: 2048` 时正文只剩 3 个字）；同一个模型被要求写 1200 词长文时 **8190/8192 token 全用在思考上、正文只吐出 1 个字符**（`finish_reason=length`），而 DashScope 上该模型的思考**关不掉**（`enable_thinking` 只接受 `True`）。
  - `DeltaDto` 目前**只读 `content`**、丢掉 `reasoning_content`：思考期间界面只有状态条的「AI 正在生成…」，不会卡死，但也看不到进展。要接思考展示得先把它加进 `DeltaDto` 与聚合器。
  - 配推理模型时 `maxTokens` 要给「思考 + 正文」的总预算（按上万给），长文场景更建议换非推理模型。
- **HTTP 200 但流里塞的是错误对象**（`{"error":{...}}`，如额度耗尽）时 `accept()` 直接把服务端原话抛出去，不再被当成"没有内容的正常响应"吞掉。

## AI 引用与引用回复（两条独立链路）

- **引用知识内容**（笔记/卡片/块）：内容在**点引用那一刻**由 `AiRefSnapshot` 格式化成 `AiRef.snapshot` 冻存，**不按 id 回查**——编辑器 id 是内存坐标（新建为负、`saveInternal` 不回写主键），按 id 查必然 miss。只有快照为空（老数据/异常入口）才走 `ensureRefSnapshots` 的按 id 兜底。
  - 快照随消息落库（`ai_messages.refsJson`），历史回看时用户气泡顶部渲染引用 chips；去重按 `targetKey()`（`distinctByTarget`），不要用 `equals`（同一目标不同时刻的快照不相等）。
- **引用消息**（IM 式引用回复）：只存 `quotedMessageId`（同会话内自洽），正文在请求组装时按 id 从历史里现取——抄一份副本只会多一份可能过期的内容。
- 两条链路都注入 **user 消息本体**（`data/ai/AiUserMessageText.kt`），顺序固定：引用内容 → 引用的消息 → 用户问题 → 附件。**不要再往 system prompt 里塞引用**：超长提示词末尾的上下文会被模型当背景噪音（真机表现是「引用了，但它当没看见」）。`SYSTEM_PROMPT` 里的【用户引用】规则段是配套说明，别删。
- 请求组装时**所有带 refs 的历史消息都会注入**（不只最新一条），否则「把它整理成卡片」这类追问轮会丢源材料。

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
- 「AI 创建」= 投一句「帮我创建：」到进程级 `AiPromptHandoff`，再走一次**普通底栏切换**到助手页（`navigateToAssistant()`）；助手页 ViewModel 观察 `prompt` 落进输入框、用完即清。
  - **跨页交接一律不要用路由参数**（助手页曾经的 `assistant?prefill=` 已删）：带参数的路由每次都是一次**新导航 entry**，会造出第二个 ViewModel，把正在跑的生成、当前会话、输入草稿全部重置（真机 bug：「任务在后台跑，切回来是一张白纸」）；而「不经过导航、直接拼路由串」的入口（通知跳转）还会把 `{prefill}` 这种**模板占位符**当字面值填进输入框。同类交接用进程级单例：引用走 `AiRefManager`，开场白走 `AiPromptHandoff`。
  - 通知/提醒跳助手页也走 `navigateToAssistant()`（`launchSingleTop + restoreState`），**别裸 `navigate("assistant")`**——会压入第二个助手页实例；冷启动则固定 **home 起栈 + 构图后 push**（把目标页当 `startDestination` 会让返回键从助手页直接退出应用），配置变更重建时不再重复导航。
- **`Destination.path` 与 `Destination.route` 是两件事，别混用**：`path` 是在 NavHost 里注册的**路由模板**（还剩 `note/{noteId}` 一个带参数的页面），`route` 才是拿去 `navigate()` 的**实际路由**。混用的两种翻车都踩过：拿模板比选中态 → 页面被判定为非平级页、**底栏整条消失**；拿模板去 navigate → `{noteId}` 这类占位符被当字面值传进去。`TopLevelRoutes`、底栏 `isOn`、`switchTopLevel`、通知跳转都必须用 `route`。
- 底栏 tab 的选中态**只改颜色**（原型 `.nav .tab.cur{color:var(--accent)}`，没有胶囊背景）；图标另做成对切换（实心 ↔ 描边）——纯靠颜色表达选中，对色觉障碍与灰度屏是失效的。
- **小节默认收起，但两种状态都能展开**：`CardMapper.toDomain` 显式 `isExpanded = false`，打开笔记先看到目录（标题 + 摘要 + 收起箭头）。`KnowledgeCard.isExpanded` 的默认值仍是 `true`，那是给"扁平笔记的隐式单卡"用的（只有一节时收起等于什么都看不见）。
  - 点一张收起的小节会顺手把它展开；**只读态同样有效**（`KnowledgeCardItem` 的 `clickable` 只按 `!isGenerating` 门控，`onFocus` 才判 `isEditing`）。
  - 曾经只读态整卡不可点（`enabled = isEditing`），后果是 **AI 刚建的笔记点进去全是空壳卡片** —— 小节默认收起 + 只读态展不开 = 用户以为没生成内容。别再把它锁回去。
  - 展开状态是**会话态**：没有落库列，重开笔记回到目录。
- **只读正文可选中复制**（`SelectableBlockText`，手势本体在 `ui/component/LongPressSelectableText.kt`）：`SelectionContainer` 提供划词与系统复制工具栏；长按不动弹菜单（条目由调用方给）。TEXT 块与**分支标题**用它，**助手页消息气泡也用它**（条目是「复制全文 / 引用」）。
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
