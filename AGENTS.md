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

- 分层：`ui/`（Compose + ViewModel）→ `domain/`（model / repository 接口 / usecase）→ `data/`（Room、Repository 实现、DataStore）。导出在 `export/`，提醒 Worker 在 `reminder/`。
- 笔记 = `Note` + 多个 `KnowledgeCard`，块挂在 `Block.cardId` 下。BRANCH 块的子块用 `Block.parentBranchId` 指向父分支；顶层块为 `parentBranchId == null`。
- `NoteRepositoryImpl.hydrate()`：无 `cardId` 的孤儿块会被并入第一张卡片并重排 `sortOrder`；没有卡片时块挂在 note 本身。改保存/加载逻辑时别破坏这条规则。
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
- `.dtk` 的 `note.json` 必须含 `dtkVersion`（当前为 1）；导入只支持 `.dtk`，高版本拒绝。
- 公式：HTML/Markdown 导出渲染为 base64 PNG；`.dtk` 保留 LaTeX 源码。

## 行内标记与语义强调（这一块最容易改错）

三套**刻意取值分离**的颜色系统，改任何一套前先确认改的是哪一套：

| 用途 | 来源 | 落库字段 |
| --- | --- | --- |
| 语义色（要点/想法/注意/待办） | `EmphasisTone` → `ui/theme/EmphasisTones.kt` | 无（由文本语法表达） |
| 卡片身份色 | `domain/model/CardAccent.kt` 的固定轮转序 | `note_cards.accent`（`Int?`，null = 用户没改过，渲染时按序号回退） |
| 主题强调色 | DataStore | — |

- **行内语法的存储形态**：`{{k:要点}}` / `{{i:}}` / `{{w:}}` / `{{t:}}`，后缀 `-c`（只变色）/`-u`（下划线）。角色前缀必填，正文非空、不含花括号与换行，转义用 `\{{` `\}}`。
- **语法只存在于存储形态**。编辑器的缓冲区（`TextBlockEditor` 的 `buffer`）**只有可见文本**，标记放在同级的 `spans: List<InlineSpan>` 里，保存时才 `materialize`（设计文档 §3.7）。所以：任何"读 `Block.content` 直接展示"的地方都必须先 `InlineMarkup.stripMarkup`；导出、摘要、预览、AI 摘要都算。新增消费点时别忘了这一条。
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

## 文档索引

- `README.md` — 功能、结构、环境要求。
- `docs/superpowers/specs|plans/` — 近期设计与实现计划（含具体验收命令）。
- `2026-07-06-zhilu-design.md` — 总体设计与编码规范来源。
- 根目录 `2026-*.md` 与 `.trae/` 为历史设计稿；`.trae/`、`.claude/`、`.worktrees/`、`local.properties` 已 gitignore。
