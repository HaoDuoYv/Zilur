# 知录 (ZhiLu)

**知录** 是一款本地优先的 Android 知识笔记应用，帮助你高效记录、整理和复习各类知识。基于 Block 内容模型，支持文本、图片、链接、LaTeX、代码、待办等多种内容形式，并内置艾宾浩斯复习计划与提醒系统。

---

## 功能特性

### 笔记系统

- **Block 内容模型** — 每条笔记由多个内容块组成，支持 TEXT、IMAGE、LINK、DIVIDER、LATEX、CODE、TODO、BRANCH 等类型
- **所见即所得编辑** — 查看即编辑，自动保存（300ms 防抖）
- **知识卡片** — 在笔记内创建子卡片组，分组管理相关内容
- **标签管理** — 7 色标签调色板，多对多关联，支持标签筛选
- **收藏功能** — 标记重要笔记

### 浏览与检索

- **首页** — 笔记卡片流 / 时间轴双模式，统计卡片（笔记数 / 标签数 / 图片数）
- **全局搜索** — 按标题、正文、标签搜索，支持最近搜索记录
- **标签页** — 按标签浏览笔记，显示各标签下笔记数量
- **探索页** — 搜索入口 + 热门标签 + 最近浏览

### 复习与提醒

- **艾宾浩斯复习计划** — 为笔记启用复习计划，按 1 天 / 3 天 / 7 天 / 15 天 / 30 天间隔自动安排复习
- **复习评价** — 每次复习后可评价（困难 / 正常 / 已掌握），动态调整下次复习时间
- **笔记 TODO 提醒** — 在笔记内创建待办事项并设置提醒时间
- **提醒中心** — 集中查看今日 / 逾期 / 未来 / 已完成的所有提醒
- **后台通知** — 基于 WorkManager 的定时检查，设备重启后自动恢复

### 多媒体

- **拍照记录** — CameraX 集成，拍照后自动压缩（1MB 以内）并插入笔记
- **全屏图片查看** — 点击笔记中的图片进入全屏预览

### 数据管理

- **本地存储** — 所有数据存储在本地设备，不依赖服务器
- **JSON 导出 / 导入** — 完整笔记数据备份与恢复
- **Markdown 导出** — 将笔记导出为 Markdown 格式
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
| 数据库 | Room（10 张表） |
| 配置存储 | DataStore Preferences |
| 导航 | Navigation Compose |
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
│   │   ├── latex/      # LaTeX 公式渲染
│   │   ├── tag/        # 标签选择器
│   │   └── toolbar/    # 编辑工具栏
│   ├── tag/            # 标签管理
│   ├── explore/        # 搜索与探索
│   ├── settings/       # 设置
│   ├── reminder/       # 提醒中心
│   ├── trash/          # 回收站
│   ├── camera/         # 拍照
│   ├── component/      # 通用 UI 组件
│   ├── navigation/     # 导航配置
│   └── theme/          # Material 3 主题与设计 Token
├── domain/
│   ├── model/          # 业务模型
│   └── repository/     # Repository 接口
├── data/
│   ├── local/
│   │   ├── dao/        # Room DAO
│   │   ├── entity/     # Room Entity
│   │   ├── database/   # AppDatabase + Migration
│   │   └── mapper/     # Entity ↔ Model 转换
│   ├── repository/     # Repository 实现
│   └── datastore/      # DataStore 配置
├── reminder/           # WorkManager 提醒 Worker
├── export/             # JSON / Markdown 导出
├── common/             # 扩展函数与工具类
└── di/                 # Hilt 依赖模块
```

---

## 数据模型

### Block 类型

| 类型 | 值 | 说明 |
|------|----|------|
| `TEXT` | 1 | 富文本 |
| `IMAGE` | 2 | 图片 |
| `LINK` | 3 | 链接 |
| `DIVIDER` | 4 | 分割线 |
| `LATEX` | 5 | LaTeX 数学公式 |
| `CODE` | 6 | 代码块 |
| `TODO` | 7 | 待办事项 |
| `BRANCH` | 8 | 分支内容 |

### 主要数据库表

| 表名 | 说明 |
|------|------|
| `Note` | 笔记主表 |
| `NoteBlock` | 内容块 |
| `NoteCard` | 知识卡片 |
| `Tag` | 标签（名称 + 颜色） |
| `NoteTag` | 笔记-标签关联 |
| `Media` | 媒体资源 |
| `TodoItem` | 待办事项 |
| `ReminderInstance` | 提醒实例 |
| `ReviewPlan` | 复习计划 |
| `ReviewEvent` | 复习记录 |

---

## 开发

### 环境要求

- Android Studio Koala+ (2024.1+)
- JDK 17
- Android SDK 35
- Gradle 8.7+

### 构建

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

### 数据库迁移

从 v1.0 开始，每次数据库版本升级必须编写 Migration，禁止使用 `fallbackToDestructiveMigration()`。Schema 文件输出到 `app/schemas/`。

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

v2.0+         云同步、AI 搜索与摘要、OCR 识别、多端支持
```

---

## License

MIT
