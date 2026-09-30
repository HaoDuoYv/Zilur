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

- 当前 `AppDatabase` **version = 4**，schema 导出到 `app/schemas/`（已纳入版本控制，改表必须提交）。
- **禁止** `fallbackToDestructiveMigration()`。每次升版本必须在 `Migration.kt` 写 Migration，并挂到 `Migration.all`（`di/AppModule.kt` 使用）。
- Room / Hilt 走 **kapt**（不是 KSP）。`room.schemaLocation` 已在 `app/build.gradle.kts` 配好。
- `BlockType.value`（TEXT=1 … BRANCH=8）是持久化稳定值，**不可改序/改值**，导出 JSON 与库内都依赖它。
- 迁移验证在 androidTest：`MigrationTest`，需要 `connectedDebugAndroidTest`。

## 导出 / 导入

- 格式：JSON、Markdown、HTML（图片/公式内嵌 base64）、`.dtk`（ZIP：`note.json` + `media/`）。
- `.dtk` 的 `note.json` 必须含 `dtkVersion`（当前为 1）；导入只支持 `.dtk`，高版本拒绝。
- 公式：HTML/Markdown 导出渲染为 base64 PNG；`.dtk` 保留 LaTeX 源码。

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
