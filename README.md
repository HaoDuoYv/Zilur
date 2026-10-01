# 知录 (ZhiLu)

**知录** 是一款本地优先的 Android 知识笔记应用，帮助你高效记录、整理和复习各类知识。基于 Block 内容模型，支持文本、图片、链接、LaTeX、代码、待办等多种内容形式，并内置艾宾浩斯复习计划、提醒系统与 AI 知识助手。

---

## 功能特性

### 笔记系统

- **Block 内容模型** — 每条笔记由多个内容块组成，支持 TEXT、IMAGE、LINK、DIVIDER、LATEX、CODE、TODO、BRANCH 等类型
- **所见即所得编辑** — 查看即编辑，自动保存（300ms 防抖）
- **知识卡片** — 在笔记内创建子卡片组，分组管理相关内容
- **标签管理** — 7 色标签调色板，多对多关联，支持标签筛选
- **收藏功能** — 标记重要笔记
- **公式与行内排版** — 独立公式块（`$$...$$`）与正文行内公式（`$...$`）均实时渲染为 LaTeX 图片，行内同时支持 `**粗体**` 与 `` `代码` ``

### 浏览与检索

- **首页** — 笔记卡片流 / 时间轴双模式，统计卡片（笔记数 / 标签数 / 图片数）
- **列表手势** — 右滑 / 左滑只**露出**「收藏」「删除」操作槽，点按槽位才真正执行。手势带方向锁定（横向位移需超过纵向 1.6 倍才生效）、单行互斥与滚动自动收起，上下翻动列表不会误触；长按菜单与读屏自定义动作提供等价入口
- **全局搜索** — 按标题、正文、标签搜索，支持最近搜索记录
- **标签页** — 按标签浏览笔记，显示各标签下笔记数量
- **探索页** — 搜索入口 + 热门标签 + 最近浏览

### 复习与提醒

- **艾宾浩斯复习计划** — 为笔记启用复习计划，按 1 天 / 3 天 / 7 天 / 15 天 / 30 天间隔自动安排复习
- **复习评价** — 每次复习后可评价（困难 / 正常 / 已掌握），动态调整下次复习时间
- **笔记 TODO 提醒** — 在笔记内创建待办事项并设置提醒时间
- **提醒中心** — 集中查看今日 / 逾期 / 未来 / 已完成的所有提醒
- **后台通知** — 基于 WorkManager 的定时检查，设备重启后自动恢复

### AI 助手

- **多轮对话** — SSE 流式输出，对话与消息本地持久化，支持历史会话切换
- **Function Calling** — 内置 6 个工具：`list_notes` / `search_notes` / `get_note` / `create_note` / `update_note` / `add_tags`，AI 可直接读写你的知识库
- **引用提问** — 把整篇笔记或某个内容块「引用到 AI」，改写只作用于被引用的目标
- **多模态识图** — 附图后视觉模型可识别图中文字，并把图片写入笔记
- **生成占用锁** — 正在生成的目标在编辑器中标记为「生成中」并禁点，避免并发改写冲突
- **进程级任务管理** — 生成过程切页不中断，全局状态栏常驻显示进度并可一键回到对话
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
- **HTML / Markdown** — 图片自动内嵌为 base64，LaTeX 公式渲染为图片，单文件即可查看
- **`.dtk` 导入导出** — 带媒体的笔记归档格式，导入前可预览，便于设备间迁移
- **JSON 备份** — 完整数据备份与恢复
- **回收站** — 软删除，30 天有效期，可手动清空

### 个性化

- **主题模式** — 浅色 / 深色 / 跟随系统
- **主题色** — 从 7 色调色板中选择应用强调色

### 体验与无障碍

- **设计 Token 化** — 颜色 / 透明度 / 圆角 / 阴影 / 动效时长统一收敛为语义化 Token，消除硬编码不一致
- **动效系统** — 统一的时长 / 缓动 / 弹簧规范，页面切换采用水平滑动 push/pop 转场
- **减少动态效果** — 跟随系统「移除动画」设置自动降级为无动画（reduced-motion）
- **触控可达性** — 可交互元素最小触控目标 48dp

---

## 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Clean Architecture |
| 依赖注入 | Hilt |
| 异步 | Coroutines + Flow |
| 数据库 | Room（12 张表，v5） |
| 配置存储 | DataStore Preferences（Protobuf） |
| 导航 | Navigation Compose |
| 网络 | OkHttp 4（OpenAI 兼容协议 + SSE 流式） |
| 拍照 | CameraX |
| 图片加载 | Coil |
| 后台任务 | WorkManager |
| LaTeX 渲染 | jlatexmath-android |
| 序列化 | Kotlin Serialization |
| 日志 | Timber |

---

## 项目结构

```
app/
├── ui/
│   ├── home/           # 首页（笔记列表 / 时间轴）
│   ├── note/           # 笔记编辑（Block 编辑器）
│   │   ├── blocks/     # 各类型 Block 渲染组件
│   │   ├── knowledge/  # 知识卡片组件
│   │   ├── latex/      # LaTeX 渲染（块级 + 行内公式）
│   │   ├── tag/        # 标签选择器
│   │   └── toolbar/    # 编辑工具栏
│   ├── assistant/      # AI 助手对话界面
│   ├── tag/            # 标签管理
│   ├── explore/        # 搜索与探索
│   ├── settings/       # 设置（含 AI 供应商配置）
│   ├── reminder/       # 提醒中心
│   ├── trash/          # 回收站
│   ├── camera/         # 拍照
│   ├── component/      # 通用 UI 组件（RichText / 生成指示器等）
│   ├── navigation/     # 导航配置 + 全局 AI 状态栏
│   └── theme/          # Material 3 主题与设计 Token
├── ai/                 # 进程级 AI 任务管理（AiTaskManager / AiRefManager）
├── domain/
│   ├── model/          # 业务模型（含 Block 内容格式）
│   ├── repository/     # Repository 接口
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
├── common/             # 扩展函数与工具类
└── di/                 # Hilt 依赖模块
```

---

## 数据模型

### Block 类型

| 类型 | 值 | 说明 |
|------|----|------|
| `TEXT` | 1 | 富文本（支持行内 `$...$` 公式、`**粗体**`、`` `代码` ``） |
| `IMAGE` | 2 | 图片，`content` 格式为 `mediaId\|uri` |
| `LINK` | 3 | 链接 |
| `DIVIDER` | 4 | 分割线 |
| `LATEX` | 5 | LaTeX 数学公式（`content` 存裸源码） |
| `CODE` | 6 | 代码块 |
| `TODO` | 7 | 待办事项 |
| `BRANCH` | 8 | 分支内容 |

> **图片块的 `content` 必须是 `mediaId\|uri`**，不能只存 `uri`。
> 导出、分享与媒体清理都按 `mediaId` 判断图片归属，裸 URI 会被当成「没有归属的图」丢掉。
> 统一的编解码入口是 `ImageBlockContent`（`fromMedia` / `resolveMedia` / `resolveUri`）。

### 主要数据库表

| 表名 | 说明 |
|------|------|
| `notes` | 笔记主表 |
| `note_blocks` | 内容块 |
| `note_cards` | 知识卡片 |
| `tags` | 标签（名称 + 颜色） |
| `note_tags` | 笔记-标签关联 |
| `media` | 媒体资源（登记过的图才算「笔记图片」） |
| `todo_items` | 待办事项 |
| `reminder_instances` | 提醒实例 |
| `review_plans` | 复习计划 |
| `review_events` | 复习记录 |
| `ai_conversations` | AI 对话会话 |
| `ai_messages` | AI 消息（含工具调用与附图引用） |

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

### 设计文档

方案与设计文档位于 `docs/plans/`，命名格式为 `YYYY-MM-DD-<主题>-design.md`；实现后需回写验证结论。

### 数据库迁移

当前数据库版本为 **v5**。每次版本升级必须编写 Migration，禁止使用 `fallbackToDestructiveMigration()`。Schema 文件输出到 `app/schemas/`。

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
