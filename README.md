# 知录 (ZhiLu)

**知录** 是一款本地优先的 Android 知识笔记应用，帮助你高效记录、整理和复习各类知识。基于 Block 内容模型，支持文本、图片、链接、LaTeX、代码、待办等多种内容形式，并内置艾宾浩斯复习计划、提醒系统与 AI 知识助手。

---

## 功能特性

### 笔记系统

- **Block 内容模型** — 每条笔记由多个内容块组成，支持 TEXT、IMAGE、LINK、DIVIDER、LATEX、CODE、TODO、BRANCH 等类型
- **所见即所得编辑** — 查看即编辑，自动保存（500ms 防抖）
- **知识卡片** — 在笔记内创建子卡片组，分组管理相关内容；打开笔记先看目录（小节默认收起）
- **标签管理** — 7 色标签调色板，多对多关联，支持标签筛选
- **收藏功能** — 标记重要笔记
- **公式与行内排版** — 独立公式块（`$$...$$`）与正文行内公式（`$...$`）均实时渲染为 LaTeX 图片；**编辑态也渲染**（源码透明 + 覆盖层补画），光标进入公式时恢复源码可改。行内同时支持 `**粗体**`、`` `代码` `` 与 `[文字](url)` 链接，另有语义标记（要点 / 想法 / 注意 / 待办）

### 浏览与检索

- **首页** — 圆角卡片流，统计行（笔记数 / 标签数 / 图片数）；右上角可切「列表 / 时间线」，时间线在卡片之上叠一层吸附的日期分节
- **列表手势** — 右滑 / 左滑只**露出**「收藏」「删除」操作槽，点按槽位才真正执行。手势带方向锁定（横向位移需超过纵向 1.6 倍才生效）、单行互斥与滚动自动收起，上下翻动列表不会误触；长按菜单与读屏自定义动作提供等价入口。切换视图或进出搜索时露出态一并作废
- **底部导航** — 五格：笔记 / 复习 / ＋ / 助手 / 我的（＋ 是「新建 / 导入」弹层）。图标下方带文字标签，选中态是一整颗胶囊把「图标 + 文字」一起包住，颜色过渡与字重同时作用在文字上
- **标签筛选条** — 搜索框下方横滑的标签 chips，可多选（AND 语义）、长按可就地重命名 / 删除；空关键词 + 选中标签即纯标签浏览。笔记卡片上的标签胶囊一点直达同一筛选 —— 标签页撤掉后，这里是标签的唯一出口
- **全局搜索** — 关键词与标签筛选可组合（输入 `#` 弹出标签候选，点选即落成筛选 chip），支持最近搜索记录（入口在首页搜索框与笔记内查找）

### 复习与提醒

- **艾宾浩斯复习计划** — 为笔记启用复习计划，按 1 天 / 3 天 / 7 天 / 15 天 / 30 天间隔自动安排复习
- **复习评价** — 每次复习后可评价（困难 / 正常 / 已掌握），动态调整下次复习时间
- **复习统计** — 复习中心顶部展示近 7 天复习曲线、评价分布与计划毕业率（0/1 这类假精度不写，没有计划时不显示分母）
- **笔记 TODO 提醒** — 在笔记内创建待办事项并设置提醒时间
- **复习中心** — 底栏第二格，两档：「待复习」按逾期 / 今天 / 接下来 / 以后分区（另有已暂停、已完成折叠区），每行给出档位进度点与「开始复习」；「提醒」按待处理 / 已逾期 / 已完成三档筛选，待办提醒可就地完成 / 取消 / 延后，复习提醒只读（点行切到「待复习」处理）
- **后台通知** — 基于 WorkManager 的定时检查，设备重启后自动恢复

### AI 助手

- **多轮对话** — SSE 流式输出，对话与消息本地持久化，支持历史会话切换
- **Function Calling** — 内置 12 个工具：`list_notes` / `search_notes` / `get_note` / `create_note` / `update_note` / `add_blocks` / `set_block_emphasis` / `add_tags` / `add_todos`（可带 `remindAt` 建提醒）/ `delete_note` / `schedule_review`（开启复习，已有计划绝不重置进度）/ `list_due_reviews`，AI 可直接读写你的知识库
- **引用提问** — 把整篇笔记或某个内容块「引用到 AI」，改写只作用于被引用的目标
- **多模态识图** — 附图后视觉模型可识别图中文字，并把图片写入笔记
- **生成占用锁** — 正在生成的目标在编辑器中标记为「生成中」并禁点，避免并发改写冲突
- **进程级任务管理** — 生成过程切页不中断，全局状态栏常驻显示进度并可一键回到对话
- **后台继续生成** — 生成期间拉起 `dataSync` 前台服务保活：切走或锁屏都不会中断，通知栏常驻进度并带「停止」按钮；任务在后台结束时补一条完成/失败通知，点开直达助手页
- **供应商预设** — DeepSeek / 通义千问 / 智谱 / Moonshot / OpenAI / 自定义，统一走 OpenAI 兼容协议，文本与视觉模型分开配置

### OCR 文字识别

- 对话中附上图片即可让视觉模型提取文字，识别结果可直接喂给工具写入笔记
- 自动压缩超限图片并按真实格式推断 MIME，避免 token 超限与类型不符

### 多媒体

- **拍照记录** — CameraX 集成，拍照后自动压缩（1MB 以内）并插入笔记
- **相册选图** — 从系统相册导入图片，自动落盘到应用内部存储
- **全屏图片查看** — 点击笔记中的图片进入全屏预览

### 数据管理

- **本地存储** — 所有数据存储在本地设备，不依赖服务器
- **笔记分享** — 一键导出为 HTML 网页 / Markdown 文档 / `.dtk` 应用格式
- **HTML 导出** — 图片与公式（块级 + **行内**）全部内嵌为 base64 图片，单文件即可查看；按**小节**分块呈现（编号徽标 + 标题 + 点数 + 身份色），分支子块可折叠，观感与 App 内的知识详情一致
- **Markdown 导出** — 图片内嵌 base64；公式保留 `$…$` / `$$…$$` 标准写法（文本格式下可编辑、任何支持 KaTeX/MathJax 的渲染器都能显示）；小节落成 `##`，**不写内联 HTML 标签**
- **`.dtk` 导入导出** — 带媒体的笔记归档格式，导入前可预览，便于设备间迁移
- **JSON 备份** — 完整数据备份与恢复
- **回收站** — 软删除，30 天有效期，可手动清空

### 个性化

外观设置收在「我的 → 配色与主题」这个**独立页面**（入口行尾直接写出当前外观与明暗）。

- **配色方案** — 两套可选，一键切换：
  - **纸墨**（默认）— 暖纸底 + 墨蓝，克制的编辑式排版；
  - **动森** — 取自 [AnimalIslandUI](https://github.com/liuyuhong0324/AnimalIslandUI)：米白底 + 青绿主色 + 暖褐正文，圆润活泼的岛屿风。
  缩略卡直接画出每套外观的底色与三个主色，不用切过去试；切换即时生效并持久化。
- **主题模式** — 浅色 / 深色 / 跟随系统。与配色方案**正交**：每套外观都自带浅深两套方案（动森的深色是"夜晚的岛"，偏蓝的深墨绿底而不是把奶油底压黑）。
- **强调色** — 8 色强调色板，替换 M3 的 `primary` 一族，随偏好持久化。
  同一个档位在两套外观下**取不同色值**（纸墨的「绛红」在动森下是「樱花粉」），
  所以切换外观不会丢掉已经选好的档位。
- **色盲友好语义色** — 要点/想法/注意/待办改用色觉障碍下可分辨、明度分级更明显的一套

> 新配色都要过 `AccentPaletteTest` 的对比度检查：文字 4.5、图形标记 3.0（WCAG）。
> 动森这套在落地时被它挡下了**六次**——初版的叶绿配白字只有 3.46、薄荷 2.84、
> 樱花粉 3.07、珊瑚 3.13、温灰 4.39，而「要点」的色条压在奶油底上只有 **2.08**
> （等于色条消失）。肉眼完全看不出来，全是量出来才发现的。

### 体验与无障碍

- **设计 Token 化** — 颜色 / 透明度 / 圆角 / 阴影 / 动效时长统一收敛为语义化 Token，消除硬编码不一致
- **动效系统** — 统一的时长 / 缓动 / 弹簧规范，页面切换采用水平滑动 push/pop 转场
- **减少动态效果** — 跟随系统「移除动画」设置自动降级为无动画（reduced-motion）
- **触控可达性** — 可交互元素最小触控目标 48dp

---

## 技术栈

| 类别       | 选型                              |
| -------- | ------------------------------- |
| 语言       | Kotlin                          |
| UI       | Jetpack Compose + Material 3    |
| 架构       | MVVM + Clean Architecture       |
| 依赖注入     | Hilt                            |
| 异步       | Coroutines + Flow               |
| 数据库      | Room（12 张表，v6）                  |
| 配置存储     | DataStore Preferences（Protobuf） |
| 导航       | Navigation Compose              |
| 网络       | OkHttp 4（OpenAI 兼容协议 + SSE 流式）  |
| 拍照       | CameraX                         |
| 图片加载     | Coil                            |
| 后台任务     | WorkManager                     |
| LaTeX 渲染 | jlatexmath-android              |
| 序列化      | Kotlin Serialization            |
| 日志       | Timber                          |

---

## 项目结构

```
app/
├── ui/
│   ├── home/           # 首页（笔记列表 / 时间轴）
│   ├── note/           # 笔记编辑（Block 编辑器）
│   │   ├── blocks/     # 各类型 Block 渲染组件 + 行内原子层
│   │   ├── knowledge/  # 知识卡片组件
│   │   ├── latex/      # LaTeX 渲染（块级 + 行内公式）
│   │   ├── find/       # 笔记内查找
│   │   ├── tag/        # 标签选择器
│   │   └── toolbar/    # 编辑工具栏
│   ├── assistant/      # AI 助手对话界面
│   ├── review/         # 复习中心（待复习 / 提醒两档）
│   ├── search/         # 搜索链路（标签筛选条 + 标签管理弹层 / 对话框）
│   ├── settings/       # 设置（含 AI 供应商配置）
│   ├── reminder/       # 提醒行组件与筛选 / 延后选项（列表已并入复习中心）
│   ├── trash/          # 回收站
│   ├── camera/         # 拍照
│   ├── create/         # 底栏 ＋ 的「新建 / 导入」弹层
│   ├── component/      # 通用 UI 组件（RichText / 生成指示器等）
│   ├── navigation/     # 导航配置 + 全局 AI 状态栏
│   └── theme/          # Material 3 主题与设计 Token
├── ai/                 # 进程级 AI 任务管理（AiTaskManager / AiRefManager / 前台服务）
├── domain/
│   ├── model/          # 业务模型（含 Block 内容格式）
│   ├── markup/         # 行内标记与公式语法的纯逻辑（解析 / 物化 / 编辑影响）
│   ├── repository/     # Repository 接口
│   ├── usecase/        # 用例（备份恢复、导入知识等）
│   └── ai/             # AI 领域层
│       ├── model/      # AiTask / AiRef 等任务与引用模型
│       ├── repository/ # AI 仓库接口
│       └── usecase/    # AiToolExecutor（Function Calling 工具集）
├── data/
│   ├── local/
│   │   ├── dao/        # Room DAO
│   │   ├── entity/     # Room Entity
│   │   ├── database/   # AppDatabase + Migration
│   │   ├── file/       # 媒体 / 归档文件管理
│   │   └── mapper/     # Entity ↔ Model 转换
│   ├── repository/     # Repository 实现
│   ├── ai/             # LLM 客户端与 DTO（OpenAI 兼容）
│   └── datastore/      # DataStore 配置
├── reminder/           # WorkManager 提醒 Worker
├── export/             # HTML / Markdown / DTK / JSON 导入导出
├── common/             # 跨层的前台状态跟踪（AppForegroundTracker）
└── di/                 # Hilt 依赖模块
```

---

## 数据模型

### Block 类型

| 类型        | 值 | 说明                                       |
| --------- | - | ---------------------------------------- |
| `TEXT`    | 1 | 富文本（支持行内 `$...$` 公式、`**粗体**`、`` `代码` ``） |
| `IMAGE`   | 2 | 图片，`content` 格式为 `mediaId\|uri`          |
| `LINK`    | 3 | 链接                                       |
| `DIVIDER` | 4 | 分割线                                      |
| `LATEX`   | 5 | LaTeX 数学公式（`content` 存裸源码）               |
| `CODE`    | 6 | 代码块                                      |
| `TODO`    | 7 | 待办事项                                     |
| `BRANCH`  | 8 | 分支内容                                     |

> **图片块的 `content` 必须是 `mediaId\|uri`**，不能只存 `uri`。  
> 导出、分享与媒体清理都按 `mediaId` 判断图片归属，裸 URI 会被当成「没有归属的图」丢掉。  
> 统一的编解码入口是 `ImageBlockContent`（`fromMedia` / `resolveMedia` / `resolveUri`）。

### 主要数据库表

| 表名                   | 说明                  |
| -------------------- | ------------------- |
| `notes`              | 笔记主表                |
| `note_blocks`        | 内容块                 |
| `note_cards`         | 知识卡片                |
| `tags`               | 标签（名称 + 颜色）         |
| `note_tags`          | 笔记-标签关联             |
| `media`              | 媒体资源（登记过的图才算「笔记图片」） |
| `todo_items`         | 待办事项                |
| `reminder_instances` | 提醒实例                |
| `review_plans`       | 复习计划                |
| `review_events`      | 复习记录                |
| `ai_conversations`   | AI 对话会话             |
| `ai_messages`        | AI 消息（含工具调用与附图引用）   |

> 搜索筛选条与标签管理弹层里的「N 条笔记」由 `note_tags JOIN notes` 的单条 `GROUP BY` 聚合算出  
> （`TagDao.countNotesPerTag`，过滤 `deletedAt IS NULL`），不会为了显示一个计数把笔记逐条加载进内存。

> 笔记正文在库里有两个**互斥**的落点：有知识卡片的笔记，块挂在卡片下（`note_blocks.cardId` 指向 `note_cards`），  
> 从库里读出来的 `Note.blocks` 是**空的**；没有卡片的笔记，块才直接归属笔记。  
> 取正文统一走 `Note.contentBlocks`（`blocks` 为空时回退到卡片下的块），  
> 读写两侧都不要自己判空——写侧漏掉这一层，一次「只改标题」的往返就会把整篇正文删掉。

存储路径约定：`filesDir/media` 存放相册 / 拍照导入的图片，`filesDir/images` 存放聊天附件落的图。

---

## 开发

### 环境要求

- Android Studio Koala+ (2024.1+)
- JDK 17
- Android SDK 35
- Gradle 8.7+

### 构建与验收

```bash
# 调试构建
./gradlew assembleDebug

# 运行单元测试
./gradlew testDebugUnitTest

# 运行插桩测试
./gradlew connectedAndroidTest

# Lint 检查
./gradlew lintDebug
```

> **交付前三者必须全绿**：`assembleDebug`、`lintDebug`、`testDebugUnitTest`。  
> 注意 `assembleDebug` **不会编译 test 源集**——只跑它，测试文件的编译错误会被长期掩盖。  
> 只要改动过 ViewModel / 用例的构造函数，就顺手跑一次 `testDebugUnitTest`。

### 配置 AI 助手

在「设置 → AI 助手」中选择供应商（DeepSeek / 通义千问 / 智谱 / Moonshot / OpenAI / 自定义）、  
填写 API Key 并分别选择文本模型与视觉模型。端点统一走 OpenAI 兼容协议，  
自定义供应商需手动填写 endpoint。配置仅存于本机 DataStore。

### 键盘与窗口 insets

- Activity 声明 `android:windowSoftInputMode="adjustResize"`，并配合 `WindowCompat.setDecorFitsSystemWindows(window, false)`。  
  这两者缺一不可：不声明 softInputMode 时系统会把 `adjustUnspecified` 解析成 **`adjustPan`**，  
  框架先把窗口表面整体上推一次，页面里再 `imePadding()` 就变成同一个 inset 消费两遍，  
  输入框与键盘之间会空出「与键盘等高」的一整块空白。
- **IME 只在根层 `AppShell` 让位一次**（`Modifier.imePadding()`），各页不要再自己加，  
  这与 API 30 以下「窗口被键盘顶掉一块」的原生行为等价。
- 其余系统栏 inset 同样归口根层：根 Scaffold 的 `contentWindowInsets` 归零，  
  底栏与全局 AI 状态条各自消费自己的那条边。

### UI 组件约定

- **列表行有两种形态**（`ui/component/NoteRow.kt`）：`Document` 是纯文档行，靠发丝线分隔，左缘带  
  通高标签色书脊，给回收站这类高密度列表用；`Card` 是圆角纸卡，靠留白分隔，  
  **不带书脊**——3dp 的书脊遇到 14dp 圆角会被切成两头收窄的细条，看着像渲染瑕疵。  
  首页的两种视图都用 `Card`，区别只在时间线多一层吸附日期分节。
- **分段控件不要用 `Surface(onClick = …)` 承载分段**（`ui/component/SegmentedToggle.kt`）。  
  `Surface(onClick)` 把 `minimumInteractiveComponentSize()` 套在**背景之上**：布局盒撑到 48dp，  
  背景却仍按内容自然尺寸（约 27dp）居中绘制 → 选中色块浮在容器中间、上下各空 10dp，  
  也就是「选中框没有铺满」。正确做法是把背景画在段槽自身（定高 + `fillMaxHeight` 语义）上，  
  触控目标由外层 `minimumInteractiveComponentSize()` 兜住。
- **底部导航的选中态要落在文字上**（`ui/navigation/BottomBar.kt`）。图标与文字放进同一个 `Column`，  
  整块包进 `clip(CircleShape).background(indicatorColor)`——胶囊因此铺满「图标 + 文字」，  
  文字与图标共用一份 `animateColorAsState` 的 `contentColor`，选中时再切 `FontWeight.SemiBold`。  
  只把胶囊套在图标上会让文字游离在选中态之外，读起来像「图标选中了、标签没变」。
- **时间线视图的轨道画在卡片外层**（`ui/home/TimelineRail.kt` 的 `Modifier.timelineRail`）：  
  `drawBehind` 画一条 `outlineVariant` 竖线加一颗 `primary` 节点圆，首/尾行用 `isFirst` / `isLast`  
  把线段收在节点处，避免轨道穿出列表首尾。
- **滑动露出 = 操作槽压上来，不是行内容让开**（`ui/component/SwipeRevealRow.kt`）。  
  展开时内容层**不做位移**，露出槽叠在上层按 `offset` 从行外滑入，被盖住的只有行尾那一块。  
  早期的做法是整行平移 `RevealWidth`（92dp）：页面留白 20dp + 卡片内边距 16dp 恰好落在被推走的一段里，  
  短标题行（如「红黑树」）的标题 / 标签 / 时间会整段滑出可视区，用户看不出自己在操作哪一行。
- **露出槽的点击必须用 `enabled` 门控**。槽位未露出时只是被 `offset` 推到行外做视觉裁剪，  
  `Modifier.clip` **不裁剪触摸区**——不门控的话，静息态下行首 / 行尾那一整个 92dp 宽的区域  
  会悄悄接走点击并直接执行动作。`enabled` 一律跟着 `revealedSide` 走。
- **有行处于露出态时收起列表页的 FAB**（`ui/home/HomeScreen.kt`）。「新建」FAB 常驻右下，  
  几何上正好压住最后一行露出的删除槽并抢走触摸。必须用 `AnimatedVisibility(visible = reveal == null)`  
  而不是 `if`——退出动画跑完后节点才真正离开触摸树，在此之前点击仍会落到 FAB 上。
- 露出槽的形状要与其覆盖的卡片对齐（`Radius.Card`）。用 `MaterialTheme.shapes.medium` 差 2dp，  
  圆角处会露出背景色的白边。
- **强调色只替换 `primary` 一族**（`ui/theme/AccentPalette.kt` + `Theme.kt` 的 `ColorScheme.withAccent`）。
  `secondary`（暖褐）与 `tertiary`（橄榄）在纸墨主题里承担的是次级中性色，把它们一起换掉会让
  整屏被强调色染满，纸墨的中性底子就没了。色值定义与弹性设计的另一端严格分开：
  DataStore 只持久化 `AccentColor` 枚举名（`data/datastore/UserPreferences.kt`），
  调色板色值归 ui 层，新增颜色时请把枚举项追加在末尾以不打乱既有用户的存储值。
- **强调色必须成套定义四个角色**（`primary` / `onPrimary` / `primaryContainer` / `onPrimaryContainer`）。
  单独挑主色没有意义——每个色调的最暗用法产生了 FAB 上的字，浅色容器产生了选中筛选 chips 上的字；
  每新增一色都必须通过两个模式下四对角色的 WCAG AA 对比度门槛（断言写在 `AccentPaletteTest` 里）。
- **深色强调色必须是提亮后的版本**。直接沿用浅色主色会让深色模式的按钮暗成一团；
  `AccentPaletteTest.darkPrimaryIsBrightened` 用相对亮度守着这条。
- **搜索命中高亮读当前 `colorScheme.primary`**，不要回到写死的 hex
  （历史上 `SemanticColors.Highlight` 就把主色 hex 抄了一份，换了强调色后搜索结果仍是原来的墨蓝）。
  `highlightMatches` 是不可组合的纯函数，颜色由组合层显式传入，也因此它没有默认值——
  漏传会当场编译不过，而不是悄悄变错色。
- **复习提醒只有一条来源**（`domain/reminder/ReviewReminderSync.kt`）。REVIEW 提醒由 `review_plans.nextReviewAt`  
  驱动：开启 / 评级 / 继续 / 重新开始后重建，暂停或毕业（`nextReviewAt = null`）后取消。  
  页面各自拼 `upsertScheduled` / `cancel` 迟早漏配一处，于是出现「计划还等着下次复习、提醒却已经没了」的分叉，  
  所以三个状态动作统一收口在 `ManageReviewPlanUseCase`，提醒页对 REVIEW 行只读（点行切到「待复习」档处理）。
- **提醒处置要回写源头**（`domain/usecase/ResolveReminderUseCase.kt`）。在提醒列表里完成待办提醒，  
  必须同时写 `todo_items.completedAt`（笔记里的待办同步打勾）；取消同理要清 `remindAt`。  
  只翻提醒实例的状态会让提醒与待办各说各话——这正是「提醒里完成了、笔记里还欠着勾」的旧缺陷。
- **跨页一次性意图不要走路由参数**（`ui/navigation/AppIntents.kt`）。带参数的路由每次都是**新的导航 entry**，  
  会把目标页的 ViewModel 换成一份新状态（助手页曾因此造出第二个实例、丢掉后台任务与输入草稿）。  
  「进复习中心落在哪一档」「打开笔记自动弹复习面板」「进搜索预置哪个标签」一律走进程级单例，消费即清空；  
  并且**用 collect 而不是读一次**——顶层页会被 `restoreState` 复用同一个 ViewModel，只读一次就只在它首次创建时生效。

### 键盘与焦点

- **弹层关闭后要主动唤回键盘**（`ui/assistant/AssistantScreen.restoreComposerFocus`）。  
  弹层打开只是让主窗口失焦、系统收起 IME，输入框的 Compose 焦点**未必**被清掉，  
  此时直接 `FocusRequester.requestFocus()` 会因「已经聚焦」而完全失效。  
  必须 `LocalFocusManager.clearFocus()` 先强制走一遍 unfocused，隔一小段时间再 `requestFocus()`，  
  让输入框经历一次真实的焦点变化才会重新拉起 IME。
- **附件面板不是 BottomSheet，而是「键盘位」上的一块**（`ui/assistant/AttachPanel.kt`）。  
  面板高度恒等于键盘高度、顶部与键盘顶部重合，键盘与面板共用同一块底部高度。  
  实现上只需给输入栏补 `面板高 - 当前键盘高` 的占位——根层 `imePadding()` 已经让整棵树缩掉了键盘那一截，  
  两者相加恒等于面板高度，键盘收起的过程中正好此消彼长，**输入栏一像素都不动**。  
  不要另写一段时长相同的补间去「对齐」键盘动画：IME inset 在 API 30+ 本来就是逐帧动画，  
  再叠一段只会互相拉扯。**也不要用 `ModalBottomSheet` 做这件事**——它独立于键盘长上来，  
  必然经历一次「键盘先收、弹层再起」，输入栏在中间被甩上又甩下。
- **面板展开期间要压住底部导航**（`ui/navigation/AppShell.LocalSuppressBottomBar`）。  
  底栏的显示条件是「平级页 && 键盘不可见」，而面板展开时键盘恰恰是收着的，  
  底栏会冒出来把内容区顶矮一截，输入栏就跳了。状态无法从子树往上抬（页面在 `AppShell` 内部），  
  所以由 `AppShell` 反向下发一个 setter，页面在自己的 `DisposableEffect` 里声明「此刻别显示底栏」。
- **收起面板时不能同帧撤掉占位**（`AssistantScreen.panelEngaged`）。  
  「面板可见」和「占位存在」必须是两个状态：收起面板的那一刻键盘高度还是 0，  
  如果同一帧就把 `面板高 - 键盘高` 的占位撤掉，输入栏会先掉到屏幕底、再被升起的键盘顶回来。  
  正确做法是收起后继续握着这块高度（`panelEngaged`），等键盘长到同样高再放手，  
  期间占位会随 `imeBottom` 上升自己缩到 0，两者始终互补。同理，底栏压制也要跟 `panelEngaged` 而不是 `attachPanelVisible`。
- **别在自动保存里回写数据库主键**（`ui/note/NoteViewModel.saveInternal`）。  
  编辑页 `LazyColumn` 的 key 是 `card.id`，保存后把本地临时 id 换成数据库主键会让 key 变化，  
  正在输入的 `BasicTextField` 被销毁重建 → 焦点丢失 → 键盘被收起。  
  仓库层 `updateNote` 是「按 noteId 全删再插」且主键归零重新分配，下一次保存根本不依赖上一次返回的主键，  
  **唯一需要记住的是 noteId**。

### 设计文档

方案与设计文档位于 `docs/plans/`，命名格式为 `YYYY-MM-DD-<主题>-design.md`；实现后需回写验证结论。

### 数据库迁移

当前数据库版本为 **v6**。每次版本升级必须编写 Migration，禁止使用 `fallbackToDestructiveMigration()`。Schema 文件输出到 `app/schemas/`（已纳入版本控制，改表必须提交）。

---

## Commit 规范

```
feat:      新功能
fix:       修复 bug
refactor:  重构（不影响功能）
docs:      文档更新
test:      测试相关
style:     格式 / 样式调整
chore:     构建 / 工具 / 依赖
```

---

## 路标

```
v1.0 MVP      核心笔记记录、Block 编辑器、标签分类、拍照、搜索、导出、回收站

v1.5          代码块、LaTeX 公式、待办清单、知识卡片、复习提醒

v2.0          纸墨视觉重构、设计 Token、动效与无障碍、HTML / Markdown / .dtk 分享

v2.1          AI 知识助手（Function Calling + 引用提问 + 流式输出）、多模态识图与 OCR、
              行内公式渲染

v2.2+         云同步、多端支持、AI 摘要与知识关联
```

---

## License

MIT
