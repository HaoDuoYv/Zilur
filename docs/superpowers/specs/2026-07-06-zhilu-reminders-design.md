# 知录复习提醒与待办提醒设计

日期：2026-07-06
状态：已确认设计，待实现计划

## 1. 背景

知录当前已经具备知识点记录、标签、搜索、图片、导出、只读详情、LaTeX 公式与代码块能力。设计文档中将“复习提醒（艾宾浩斯）”和“待办提醒”列为后续高级功能。本设计把这两类提醒统一到一套本地优先的提醒系统中，并保留后续扩展到独立待办页和更精准提醒的空间。

## 2. 目标

- 用户可以为重要知识点手动开启艾宾浩斯复习计划。
- 用户可以在每次复习后按“困难 / 一般 / 掌握”反馈，系统据此推进下一次复习时间。
- 用户可以在知识点中添加 TODO 块，并为每条待办设置提醒时间。
- 应用内新增提醒中心，集中展示今日复习、待办提醒、逾期项和历史完成项。
- 系统通知到达后默认打开对应知识点详情。
- 通知权限关闭时，应用不崩溃，提醒中心仍能显示到期项目。

## 3. 非目标

- 第一版不自动为所有知识点开启复习。
- 第一版不实现独立待办页，只设计数据模型和导航扩展点。
- 第一版不追求精确到分钟的强提醒，不使用 AlarmManager 作为主方案。
- 第一版不做云同步、跨设备提醒同步或账号体系。

## 4. 设计决策

### 4.1 待办提醒范围

采用“先笔记内 TODO，后续独立待办页”的路线。第一版在知识点编辑器中增加 TODO 块，TODO 项天然关联当前 noteId。后续新增独立待办页时，复用相同的 `TodoItem` 数据模型，并允许 noteId 为空。

### 4.2 复习提醒开启方式

复习计划由用户在知识详情页手动开启。这样可以避免所有笔记都产生提醒噪音，也更符合“重要知识点才需要复习”的使用习惯。

### 4.3 通知体验

采用“通知直达知识点 + 应用内提醒中心统一管理”：

- 点击系统通知：打开对应知识点详情页。
- 打开提醒中心：查看今日复习、待办提醒、逾期项和已完成历史。

### 4.4 复习推进策略

用户完成一次复习后选择掌握程度：

- 困难：缩短间隔，较快再次提醒。
- 一般：按当前节奏推进。
- 掌握：进入下一档更长间隔。

默认艾宾浩斯档位为：1 天、3 天、7 天、15 天、30 天。具体推进由纯 Kotlin 策略类计算，避免业务逻辑散落在 ViewModel 或 Worker 中。

### 4.5 调度方案

第一版使用 WorkManager 做可靠提醒检查。WorkManager 按系统允许的周期扫描本地数据库中到期且未完成的提醒，并发出系统通知。应用启动、开启复习、创建待办、完成提醒等状态变化后，也触发一次一次性提醒检查。

后续如果需要更精准的提醒，可在调度层增加 AlarmManager 实现，而不改变复习和待办的数据模型。

## 5. 数据模型

### 5.1 ReviewPlan

表示某个知识点的复习计划。

字段：

- `id: Long`
- `noteId: Long`
- `enabled: Boolean`
- `currentStep: Int`
- `nextReviewAt: Long?`
- `createdAt: Long`
- `updatedAt: Long`
- `completedAt: Long?`

约束：

- 一个知识点最多一个启用中的复习计划。
- note 删除或进入回收站后，关联复习提醒不应继续弹通知。

### 5.2 ReviewEvent

表示一次复习记录。

字段：

- `id: Long`
- `planId: Long`
- `noteId: Long`
- `reviewedAt: Long`
- `rating: ReviewRating`
- `previousStep: Int`
- `nextStep: Int`
- `nextReviewAt: Long?`

`ReviewRating` 固定值：

- `HARD`
- `NORMAL`
- `MASTERED`

### 5.3 TodoItem

表示一条待办。

字段：

- `id: Long`
- `noteId: Long?`
- `content: String`
- `remindAt: Long?`
- `completedAt: Long?`
- `createdAt: Long`
- `updatedAt: Long`
- `sortOrder: Int`

行为：

- 未完成待办默认显示。
- 已完成待办默认隐藏，可通过“显示已完成”开关查看。
- 第一版主要从知识点详情/编辑页创建，后续独立待办页可以创建 noteId 为空的待办。

### 5.4 ReminderInstance

统一承载复习和待办的到期提醒。

字段：

- `id: Long`
- `type: ReminderType`
- `sourceId: Long`
- `noteId: Long?`
- `dueAt: Long`
- `status: ReminderStatus`
- `notificationId: Int`
- `createdAt: Long`
- `updatedAt: Long`
- `firedAt: Long?`

`ReminderType` 固定值：

- `REVIEW`
- `TODO`

`ReminderStatus` 固定值：

- `SCHEDULED`
- `FIRED`
- `DONE`
- `CANCELED`

约束：

- 同一个 source 在未完成状态下只保留一个有效提醒实例。
- 完成复习或待办时，对应提醒实例进入 `DONE`。
- 关闭复习或删除待办时，对应提醒实例进入 `CANCELED`。

## 6. Domain 与 Repository

新增 Repository 接口：

- `ReviewRepository`
- `TodoRepository`
- `ReminderRepository`

新增纯 Kotlin 策略类：

- `ReviewSchedulePolicy`
  - 输入：当前 step、rating、当前时间
  - 输出：nextStep、nextReviewAt、是否完成计划
- `ReminderClassifier`
  - 输入：提醒列表、当前时间
  - 输出：今日、逾期、未来、已完成分组

Repository 返回继续沿用当前项目的 `RepositoryResult` 规范，所有数据层异常在 RepositoryImpl 内部捕获。

## 7. UI 设计

### 7.1 知识详情页

只读详情页新增复习区：

- 未开启：显示“开启复习”按钮。
- 已开启：显示下一次复习时间、当前进度、关闭复习入口。
- 到期时：显示“开始复习”或“已复习”操作。

复习完成操作提供三个按钮：

- 困难
- 一般
- 掌握

### 7.2 编辑页 TODO 块

底部 Block 工具栏新增“待办”按钮。添加后出现 TODO 块编辑器，支持：

- 输入待办内容
- 选择提醒时间
- 标记完成
- 切换显示已完成

TODO 块可以在同一知识点中包含多条待办。第一版不要求 TODO 支持复杂子任务、优先级或重复规则。

### 7.3 提醒中心

新增二级页面 `ReminderCenterScreen`，入口放在首页顶部提醒图标和设置页“提醒”分组。

页面分组：

- 今日复习
- 待办提醒
- 逾期
- 已完成/历史

每个列表项展示：

- 类型：复习或待办
- 关联知识点标题
- 到期时间
- 状态
- 快捷操作：复习反馈、完成待办、打开知识点

### 7.4 设置页

新增提醒设置分组：

- 提醒总开关
- 默认提醒时间
- 通知权限状态
- 跳转系统通知设置

## 8. 通知与权限

Android 13+ 需要 `POST_NOTIFICATIONS` 权限。应用应在用户首次开启复习或创建带提醒的待办时请求权限。

权限关闭时：

- 不创建会导致崩溃的通知调用。
- 提醒中心仍显示到期提醒。
- 设置页显示权限状态并提供跳转系统设置入口。

通知内容：

- 复习提醒标题：`该复习了：{知识点标题}`
- 复习提醒正文：`选择困难、一般或掌握来安排下一次复习`
- 待办提醒标题：`待办提醒：{待办内容}`
- 待办提醒正文：`来自：{知识点标题}`

点击通知打开对应知识点详情。如果 noteId 不存在或已被永久删除，则打开提醒中心。

## 9. 数据迁移

数据库版本从当前版本升级到新版本时新增表：

- `review_plans`
- `review_events`
- `todo_items`
- `reminder_instances`

必须提供 Room Migration，禁止破坏已有笔记、标签、媒体和 Block 数据。新增表不需要回填历史数据。

## 10. 错误处理

- 提醒调度失败：记录错误并在 UiState 中显示 Snackbar，不影响笔记编辑。
- 通知权限未授予：提醒中心可用，系统通知跳过。
- 关联知识点不存在：提醒中心项显示“知识点已不存在”，允许取消提醒。
- 重复调度：通过 `ReminderInstance` 的 source 约束和状态过滤避免重复通知。

## 11. 测试策略

单元测试：

- `ReviewSchedulePolicy` 的困难/一般/掌握推进逻辑。
- 艾宾浩斯档位边界：最后一档完成后不再生成下一次提醒。
- TODO 完成后默认隐藏，可切换显示已完成。
- `ReminderClassifier` 正确区分今日、逾期、未来、已完成。
- Repository 新增/完成/取消提醒时状态正确。

集成测试：

- 开启复习生成首个 `ReminderInstance`。
- 完成一次复习后生成下一次 `ReminderInstance`。
- 创建带提醒的 TODO 生成 TODO 类型提醒。
- 删除或软删除知识点后，关联提醒不再触发通知。

构建验证：

- `.\gradlew.bat :app:testDebugUnitTest --no-daemon`
- `.\gradlew.bat :app:assembleDebug --no-daemon`

在当前中文路径环境下，如遇 Gradle/JUnit 类加载路径问题，继续从 ASCII junction `C:\codex-links\zhilu` 运行验证命令。

## 12. 后续扩展

- 独立待办页：复用 `TodoItem`，允许 noteId 为空。
- 精准提醒：在调度层增加 AlarmManager 实现。
- 自定义复习间隔：设置页允许用户配置复习档位。
- 重复待办：为 `TodoItem` 增加 repeat rule。
- 首页提醒摘要：显示今日复习数和待办数。
