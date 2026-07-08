# 知录（ZhiLu）设计文档

> 版本：v1.0 MVP  
> 日期：2026-07-06  
> 状态：**v1.0 已冻结（Frozen）**  
> 评审：已通过

---

## 1. 项目概述

知录是一款本地优先的 Android 知识点记录应用，帮助用户高效记录、整理和复习各类知识。采用 Block 内容模型，支持文字、图片、链接等多种内容形式，未来可扩展 AI 搜索、OCR 识别、复习提醒等高级功能。

### 1.1 核心价值

- **本地优先**：数据全部存储在本地，隐私安全
- **Block 架构**：内容以块为单位，扩展性极强
- **Material 3**：现代化设计语言，流畅体验
- **可演进**：从 MVP 起步，逐步叠加 AI、OCR、复习等功能

### 1.2 MVP 验收标准

用户可以完整完成以下流程且无 Bug：

```
安装 APP → 新建知识 → 输入标题 → 输入正文 → 拍一张照片
→ 添加标签 → 保存 → 搜索 → 找到 → 导出 JSON → 删除 → 回收站恢复
```

---

## 2. 设计原则

### 2.1 Local First（本地优先）

所有数据优先存储在本地，不依赖服务器。网络功能（AI、云同步）作为可选扩展，不影响核心功能使用。

### 2.2 Keep It Simple（简单优于复杂）

MVP 阶段不引入不必要的架构层（如 UseCase），功能复杂后再逐步添加。代码量最少化，可读性最大化。

### 2.3 Block First（Block 优先）

所有内容以 Block 为单位组织，UI 渲染和数据存储都围绕 Block 模型展开。新增内容类型只需添加新的 BlockType 和 Renderer。

### 2.4 Material 3 First（Material 3 优先）

统一使用 Material 3 设计规范，主题色、圆角、间距、阴影等保持一致。自定义组件也要符合 Material 3 风格。

### 2.5 MVP 优先

先完成最小可用版本，验证核心流程，再逐步叠加高级功能。每个阶段结束必须能独立运行。

---

## 3. 技术栈

| 类别 | 技术选型 | 说明 |
|------|----------|------|
| 语言 | Kotlin | 官方推荐，简洁安全 |
| UI | Jetpack Compose + Material 3 | 声明式 UI，现代 Android 标准 |
| 导航 | Navigation Compose | 类型安全的 Compose 导航 |
| 架构 | MVVM + Clean Architecture | 单 Module，分层清晰 |
| 依赖注入 | Hilt | Google 官方 DI 框架 |
| 异步 | Coroutines + Flow | 响应式数据流 |
| 数据库 | Room | SQLite 封装，官方推荐 |
| 配置存储 | DataStore Preferences | SharedPreferences 替代品 |
| 图片加载 | Coil | Kotlin 原生，Compose 友好 |
| 拍照 | CameraX | Jetpack 相机库 |
| 后台任务 | WorkManager | 后续提醒功能使用 |
| OCR | ML Kit | 后续文字识别使用 |
| 网络 | Retrofit + OkHttp | 后续 AI API 使用 |
| 序列化 | Kotlin Serialization | 编译期序列化 |
| 日志 | Timber | 轻量日志库 |
| 测试 | JUnit5 + Turbine + Compose Test | 单元测试 + UI 测试 |

---

## 3. 架构设计

### 3.1 整体架构：Clean Architecture + MVVM（单 Module）

```
┌─────────────────────────────────────────────┐
│                  UI 层                       │
│  Compose Screen / ViewModel / Navigation    │
└─────────────────┬───────────────────────────┘
                  │ 依赖
┌─────────────────▼───────────────────────────┐
│               Domain 层                      │
│    Model / Repository 接口                  │
└─────────────────┬───────────────────────────┘
                  │ 实现
┌─────────────────▼───────────────────────────┐
│                Data 层                       │
│  Room / DataStore / Repository 实现          │
└─────────────────────────────────────────────┘
```

### 3.2 分层原则

- **Domain 层**：纯 Kotlin，不依赖任何 Android 框架。包含业务模型和 Repository 接口。
- **UI 层**：依赖 Domain 层。ViewModel 通过 StateFlow 驱动 Compose 渲染。
- **Data 层**：实现 Domain 层的 Repository 接口，向下操作数据库和存储。

### 3.3 数据流

```
Compose UI
     │
     │ collectAsState()
     ▼
StateFlow (UiState)
     │
     ▼
ViewModel
     │
     ▼
Repository（接口）
     │
     ▼
RepositoryImpl
     │
 ┌───┴────┐
 ▼        ▼
Room    DataStore
```

### 3.4 UI State 规范

每个页面对应一个 UiState data class，页面只有一个状态源：

```kotlin
data class HomeUiState(
    val notes: List<Note> = emptyList(),
    val isLoading: Boolean = false,
    val viewMode: ViewMode = ViewMode.LIST,
    val error: String? = null
)
```

### 3.5 命名规范（架构相关）

| 类别 | 命名规则 | 示例 |
|------|----------|------|
| Entity | `XxxEntity` | `NoteEntity`, `TagEntity` |
| DAO | `XxxDao` | `NoteDao`, `TagDao` |
| Repository 接口 | `XxxRepository` | `NoteRepository` |
| Repository 实现 | `XxxRepositoryImpl` | `NoteRepositoryImpl` |
| ViewModel | `XxxViewModel` | `HomeViewModel` |
| UiState | `XxxUiState` | `HomeUiState` |
| Mapper | `XxxMapper` | `NoteMapper` |

> MVP 阶段不引入 UseCase，业务复杂后再加。

### 3.6 Repository 返回规范

Repository 接口的所有返回值统一使用 `Result<T>` 或自定义 sealed class 包装：

```kotlin
sealed class RepositoryResult<out T> {
    data class Success<out T>(val data: T) : RepositoryResult<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : RepositoryResult<Nothing>()
}
```

**规范：**

- **读操作（返回 Flow）**：Flow 内的值也使用 `RepositoryResult` 包装
- **写操作（suspend）**：返回 `RepositoryResult<Unit>` 或 `RepositoryResult<T>`
- **所有异常**：在 RepositoryImpl 内部捕获，转换为 `Error` 状态返回，不向上抛出
- **ViewModel 处理**：根据 `Success`/`Error` 更新 UiState 的 `error` 字段或数据字段

---

## 4. 数据模型 & 数据库设计

### 4.1 数据库表结构（5 张表）

#### Note 表 — 知识点

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Long | PRIMARY KEY AUTOINCREMENT | 主键 |
| title | String | NOT NULL | 标题 |
| createdAt | Long | NOT NULL | 创建时间戳 |
| updatedAt | Long | NOT NULL | 更新时间戳 |
| isFavorite | Boolean | NOT NULL DEFAULT false | 是否收藏 |
| deletedAt | Long | NULL |  删除时间（软删除，NULL 表示未删除） |

#### Tag 表 — 标签

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Long | PRIMARY KEY AUTOINCREMENT | 主键 |
| name | String | NOT NULL UNIQUE | 标签名 |
| color | Int | NOT NULL | 标签颜色（ARGB int，从 Palette 中选） |

#### NoteTag 表 — 关联表（多对多）

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| noteId | Long | NOT NULL, FOREIGN KEY → Note.id | 知识点 ID |
| tagId | Long | NOT NULL, FOREIGN KEY → Tag.id | 标签 ID |

复合主键：(noteId, tagId)

#### NoteBlock 表 — 内容块

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Long | PRIMARY KEY AUTOINCREMENT | 主键 |
| noteId | Long | NOT NULL, FOREIGN KEY → Note.id | 所属知识点 |
| type | Int | NOT NULL | Block 类型（固定 value，见 BlockType 枚举） |
| content | String | NOT NULL | 内容（文字/链接/mediaId） |
| sortOrder | Int | NOT NULL | 排序权重 |

#### Media 表 — 媒体资源

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Long | PRIMARY KEY AUTOINCREMENT | 主键 |
| uri | String | NOT NULL | 文件路径 |
| width | Int | NULL | 宽度（px） |
| height | Int | NULL | 高度（px） |
| size | Long | NOT NULL | 文件大小（bytes） |
| createdAt | Long | NOT NULL | 创建时间戳 |

### 4.2 数据库 ER 图

```
Note ────1:N────▶ NoteBlock
 │                  │
 │                  │N:1
 │                  ▼
 │                Media
 │
 │N:N
 │
 ▼
Tag
```

**实体关系说明：**

| 关系 | 说明 |
|------|------|
| Note → NoteBlock | 1:N，一个知识点包含多个内容块 |
| NoteBlock → Media | N:1，一个图片块引用一个媒体资源 |
| Note ↔ Tag | N:N，通过 NoteTag 关联表实现多对多 |

### 4.3 Block 类型

MVP 支持 4 种：

```kotlin
enum class BlockType(val value: Int) {
    TEXT(1),       // 文字块
    IMAGE(2),      // 图片块（content 存 mediaId）
    LINK(3),       // 链接块（content 存 URL）
    DIVIDER(4)     // 分割线
}
```

**重要：** 使用固定 `value` 而非 `ordinal`，避免未来调整枚举顺序导致历史数据失效。通过 Room TypeConverter 在数据库中保存和读取 `value`。

后续扩展（v2.0+）：LATEX(5), CODE(6), TODO(7), OCR(8), AI(9), QUOTE(10), TABLE(11), AUDIO(12) 等，仅需新增枚举值，无需修改表结构。

### 4.3 标签颜色 Palette

固定 7 种颜色，用户从中选择，保证视觉统一：

| 颜色名 | ARGB 值 | 用途 |
|--------|---------|------|
| Purple | 0xFF6750A4 | 默认（紫色） |
| Blue | 0xFF0061A4 | 蓝色 |
| Green | 0xFF006B2E | 绿色 |
| Orange | 0xFF946700 | 橙色 |
| Red | 0xFF8C1D40 | 红色 |
| Cyan | 0xFF006877 | 青色 |
| Gray | 0xFF49454F | 灰色 |

### 4.4 Entity vs Domain Model

- `domain/model/` 下存放纯 Kotlin 业务模型（Note, Tag, Block, Media）
- `data/local/entity/` 下存放 Room Entity
- `data/local/mapper/` 下存放 Mapper 转换类

> MVP 阶段可以较简单，但结构必须预留，后续迁移成本低。

### 4.5 Room Migration

从 v1.0 开始，每次数据库版本升级必须写 Migration，禁止使用 `fallbackToDestructiveMigration()`。

---

## 5. 页面结构 & 导航

### 5.1 底部导航 4 Tab

| Tab | 图标 | 名称 | 路由 |
|-----|------|------|------|
| 1 | 🏠 | 首页 | Home |
| 2 | 🏷️ | 标签 | Tags |
| 3 | 🧭 | 探索 | Explore |
| 4 | ⚙️ | 设置 | Settings |

### 5.2 路由定义（Destination）

```kotlin
sealed class Destination(val path: String) {
    // 底部导航
    data object Home : Destination("home")
    data object Tags : Destination("tags")
    data object Explore : Destination("explore")
    data object Settings : Destination("settings")
    
    // 笔记编辑（查看即编辑）
    data object NoteEdit : Destination("note/{noteId}") {
        fun createRoute(noteId: Long = 0L) = "note/$noteId"
    }
    
    // 拍照
    data object Camera : Destination("camera")
    
    // 回收站
    data object Trash : Destination("trash")
}
```

### 5.3 页面清单

| 页面 | 路径 | 功能 |
|------|------|------|
| HomeScreen | home | 知识点列表（列表/时间轴双模式） |
| TagsScreen | tags | 标签管理 & 按标签筛选 |
| ExploreScreen | explore | 搜索 + 最近搜索 + 热门标签 + 最近浏览 |
| SettingsScreen | settings | 设置 & 导出 |
| NoteEditScreen | note/{id} | 笔记编辑（查看即编辑） |
| CameraScreen | camera | 拍照 |
| TrashScreen | trash | 回收站 |

### 5.4 Navigation 结构

```
ui/navigation/
├── AppNavHost.kt        // 导航容器
├── Destination.kt       // 路由定义
├── BottomBar.kt         // 底部导航栏组件
└── NavigationActions.kt // 导航动作封装
```

### 5.5 FAB 新建入口

点击首页 FAB 弹出 BottomSheet，选项：

- 📄 空白知识
- 📷 拍照记录
- 🖼️ 导入图片

> 后续扩展：🎤 语音、📝 OCR、🤖 AI 生成

---

## 6. 核心页面设计

### 6.1 首页（HomeScreen）

**布局结构：**

```
┌─────────────────────────┐
│  知录            ⋮      │  ← TopAppBar
│  ┌───────────────────┐  │
│  │🔍 搜索知识...  🤖 AI│ │  ← 搜索框
│  └───────────────────┘  │
├─────────────────────────┤
│  📄126  🏷️18  📷352    │  ← 统计卡片
├─────────────────────────┤
│ [📋列表] [🕒时间轴] ⭐收藏│  ← 模式切换
├─────────────────────────┤
│                         │
│   ┌─────────────────┐   │
│   │▍ 卡片1          │   │  ← 左侧色条 + 卡片
│   └─────────────────┘   │
│   ┌─────────────────┐   │
│   │▍ 卡片2          │   │
│   └─────────────────┘   │
│                         │
├─────────────────────────┤
│  🏠   🏷️   🧭   ⚙️     │  ← BottomBar
└─────────────────────────┘
       ✏️                 ← FAB
```

**卡片信息：**
- 左侧 4dp 标签主色条（取第一个标签的颜色，无标签则用灰色）
- 标题 + 2 行内容预览
- 标签 Chips（最多 3 个，按添加顺序排列）
- Block 图标（图片数 🖼、链接数 🔗）
- 时间
- 右上角 ⭐ 收藏按钮

**双模式：**
- 列表模式（默认）：卡片流
- 时间轴模式：按「今天/昨天/本周/更早」分组，左侧时间线

**空状态：**
- 大图标 📖 + "还没有知识" + 引导文案 + "开始记录"按钮

### 6.2 笔记编辑页（NoteEditScreen）

**设计原则：查看即编辑，自动保存。**

**布局结构：**

```
┌─────────────────────────┐
│ ←  数学·已保存  🏷️ 🤖 ⋮ │  ← TopAppBar + 保存状态
├─────────────────────────┤
│  微积分基本定理          │  ← 标题（大号字）
├─────────────────────────┤
│  牛顿-莱布尼茨公式...    │  ← TextBlock
│                         │
│  ┌───────────────────┐  │
│  │     📷 公式图      │  │  ← ImageBlock（最大高220dp）
│  └───────────────────┘  │
│                         │
│  ┃ 🔗 参考链接          │  ← LinkBlock
│  ┃ https://...         │  │
│                         │
│  ─── 常用公式 ───       │  ← DividerBlock
│                         │
│  [+ 点击添加新内容块]    │  ← 添加 Block 入口
│                         │
├─────────────────────────┤
│ 📝  📷  🔗  ➖  ⋯       │  ← 底部快捷工具栏
└─────────────────────────┘
```

**自动保存：**
- 输入防抖自动保存（500ms）
- 顶部显示状态："正在保存..." → "✓ 已保存"

**添加 Block：**
- 底部工具栏：快速添加常用类型
- 点击 "+" 弹出 BottomSheet：文字/拍照/图片/链接/分割线 + 更多（灰度预留）

### 6.3 标签页（TagsScreen）

- 顶部："全部笔记" 入口（显示总数量）
- 标签列表：颜色圆点 + 标签名 + 笔记数量
- 右上角 "+" 新建标签
- 长按标签：编辑/删除

### 6.4 探索页（ExploreScreen）

- 顶部：搜索框（带 AI 搜索入口）
- 最近搜索（Chip 列表，可清除）
- 热门标签（彩色 Chip）
- 最近浏览（列表）
- 搜索结果：笔记 / 标签 / 图片 分类 Tab（v1.0 只做笔记+标签）

### 6.5 设置页（SettingsScreen）

分组列表：

**外观**
- 主题模式（浅色/深色/跟随系统）
- 主题色（选 Palette）

**数据**
- 存储空间（笔记数/图片数/总大小）
- 导出 JSON
- 导入备份
- 导出 Markdown

**其他**
- 回收站
- 关于知录（版本号）
- 给个好评

---

## 7. MVP 功能范围

### 7.1 v1.0 包含

- ✅ 知识点 CRUD（新建/编辑/删除/查看）
- ✅ Block 内容（TEXT / IMAGE / LINK / DIVIDER）
- ✅ 标签管理（新建/编辑/删除/多对多关联）
- ✅ 拍照（CameraX）+ 图片管理（Media 表）
- ✅ 全局搜索（标题/内容/标签）
- ✅ 回收站（软删除，30天有效期，手动清理）
- ✅ 数据导出（JSON / Markdown）+ 导入 JSON
- ✅ 首页列表/时间轴双模式
- ✅ 收藏功能
- ✅ Material 3 主题（浅色/深色/跟随系统）
- ✅ 标签颜色 Palette

### 7.2 v1.0 不包含（v2.0+）

- ❌ AI 搜索 / AI 总结 / AI 问答
- ❌ OCR 文字识别
- ❌ 复习提醒（艾宾浩斯）
- ❌ 待办提醒
- ❌ LaTeX 公式渲染
- ❌ 代码块
- ❌ 云同步
- ❌ 语音输入

---

## 8. UI Design Token

### 8.1 Spacing（间距）

| Token | 值 | 用途 |
|-------|-----|------|
| space-4 | 4dp | 微小间距，Chip 内边距 |
| space-8 | 8dp | 小间距，元素之间 |
| space-12 | 12dp | 中等间距，卡片内边距 |
| space-16 | 16dp | 标准间距，页面边距 |
| space-24 | 24dp | 大间距，分区之间 |

### 8.2 Corner（圆角）

| Token | 值 | 用途 |
|-------|-----|------|
| corner-sm | 8dp | 小按钮、Chip |
| corner-md | 12dp | 卡片、输入框 |
| corner-lg | 16dp | FAB、大按钮 |

### 8.3 Elevation（阴影）

| Token | 值 | 用途 |
|-------|-----|------|
| elevation-0 | 0dp | 无阴影 |
| elevation-1 | 1dp | 卡片常态 |
| elevation-2 | 3dp | 卡片悬停/选中 |
| elevation-3 | 6dp | FAB |

### 8.4 Typography（字体）

| Token | 大小 | 粗细 | 用途 |
|-------|------|------|------|
| headlineLarge | 24sp | Medium | 页面标题 |
| headlineMedium | 20sp | SemiBold | 笔记标题 |
| titleLarge | 18sp | Medium | 列表项标题 |
| bodyLarge | 16sp | Regular | 正文内容 |
| bodyMedium | 14sp | Regular | 次要内容 |
| bodySmall | 12sp | Regular | 时间、说明 |
| labelLarge | 14sp | Medium | 按钮文字 |
| labelMedium | 12sp | Medium | Chip 文字 |

### 8.5 Icon Size（图标尺寸）

| Token | 值 | 用途 |
|-------|-----|------|
| icon-sm | 18dp | 小图标（Chip 内） |
| icon-md | 24dp | 标准图标（导航） |
| icon-lg | 32dp | 大图标（FAB） |

---

## 9. 性能约束

### 9.1 首页性能

- **列表渲染**：使用 `LazyColumn`，支持 1000+ 条笔记流畅滚动
- **图片加载**：使用 Coil 加载缩略图（最大 400px），不加载原图
- **分页预留**：预留分页接口，未来大数据量时启用
- **数据库索引**：`Note.createdAt`、`Note.updatedAt`、`Note.isFavorite`、`Note.deletedAt` 必须加索引

### 9.2 编辑页性能

- **自动保存防抖**：500ms 防抖，避免频繁写数据库
- **图片压缩**：拍照后自动压缩到 1MB 以内
- **图片预览**：ImageBlock 最大高度 220dp，点击进入全屏预览

### 9.3 搜索性能

- **搜索索引**：预留全文搜索索引，MVP 使用 Room LIKE 查询
- **搜索防抖**：300ms 防抖，避免每次输入都查数据库

---

## 10. Definition of Done（完成定义）

每个阶段结束必须满足以下全部条件：

| 检查项 | 说明 |
|--------|------|
| ✅ 能编译 | `./gradlew assembleDebug` 成功 |
| ✅ 能运行 | 应用启动正常，无白屏 |
| ✅ 无 Crash | 手动测试核心流程无崩溃 |
| ✅ 无 Lint Error | `./gradlew lintDebug` 零错误 |
| ✅ 单元测试通过 | `./gradlew testDebugUnitTest` 全部通过 |
| ✅ Git Commit | 代码已提交，提交信息符合规范 |
| ✅ 更新 CHANGELOG | 记录阶段完成内容 |

---

## 11. 开发阶段规划

| 阶段 | 内容 | 产出 |
|------|------|------|
| **P0** | UI 原型设计 | 5 个核心页面线框图 |
| **P1** | 项目初始化 | Hilt / Nav / Theme / Room 基础搭建，空项目跑通 |
| **P2** | 数据层 | Note + NoteBlock + Media + Room DAO + Repository |
| **P3** | 首页 + 标签 | 首页列表/时间轴 + 标签页 + FAB 入口 |
| **P4** | 编辑器 | NoteEditScreen + Block 渲染与编辑 + 标签关联 |
| **P5** | 拍照 + 导出 | CameraX 拍照 + 图片管理 + JSON 导入导出 |
| **P6** | 搜索 + 收尾 | 探索页搜索 + Markdown 导出 + 回收站 + v1.0 发布 |

每个阶段结束必须能独立运行和测试。

---

## 9. 目录结构

```
app/
├── ui/
│   ├── home/              # 首页
│   ├── note/              # 笔记编辑
│   ├── tag/               # 标签管理
│   ├── explore/           # 探索/搜索
│   ├── settings/          # 设置
│   ├── trash/             # 回收站
│   ├── camera/            # 拍照
│   ├── component/         # 通用组件（AppCard, TagChip...）
│   ├── navigation/        # 导航
│   └── theme/             # Material 3 主题
│
├── domain/
│   ├── model/             # Note / Tag / Block / Media
│   └── repository/        # Repository 接口
│
├── data/
│   ├── local/
│   │   ├── dao/           # NoteDao / TagDao / BlockDao / MediaDao
│   │   ├── entity/        # Entity 类
│   │   ├── database/      # AppDatabase + Migration
│   │   └── mapper/        # Entity ↔ Model
│   ├── datastore/         # DataStore 配置
│   ├── repository/        # Repository 实现
│   └── remote/            # 预留（AI API）
│
├── camera/                # CameraX 封装
├── export/                # 导出功能（JSON/Markdown）
├── common/                # 扩展函数、工具类
└── di/                    # Hilt Module
```

### 包管理原则

- 禁止使用 `util`、`utils`、`helper`、`manager`、`base`、`other` 等"垃圾桶包"
- 所有代码必须有明确归属
- Compose 单文件不超过 300 行，超过则拆分子组件

---

## 10. 开发规范

### 10.1 Git Commit 规范

```
feat:     新功能
fix:      修复 bug
refactor: 重构（不影响功能）
docs:     文档更新
test:     测试相关
style:    格式、样式调整
chore:    构建/工具/依赖
```

示例：
```
feat: 完成首页列表渲染
feat: 新增 CameraX 拍照功能
fix: 修复图片删除后空指针
refactor: 重构 Repository 层
docs: 更新 README
```

### 10.2 Branch 规范

```
main          ← 主分支，稳定版本
develop       ← 开发主分支
feature/xxx   ← 功能分支
fix/xxx       ← Bug 修复分支
```

### 10.3 Kotlin / Compose 编码规范

- **单文件单 Screen**：一个 Compose Screen 一个文件，超过 250~300 行必须拆分子组件
- **子组件命名**：`HomeTopBar`, `HomeNoteCard`, `HomeFab` 等
- **优先 StateFlow**：不用 LiveData，统一 StateFlow + collectAsState()
- **密封类状态**：加载/成功/错误用 sealed class 或 data class + error 字段
- **协程异常处理**：所有 suspend 函数必须处理异常，不向上抛到 UI
- **日志统一 Timber**：禁止 `println()`、`Log.d()` 等混用

### 10.4 命名规范总结

| 类型 | 规则 | 示例 |
|------|------|------|
| Entity | XxxEntity | NoteEntity |
| DAO | XxxDao | NoteDao |
| Repository 接口 | XxxRepository | NoteRepository |
| Repository 实现 | XxxRepositoryImpl | NoteRepositoryImpl |
| ViewModel | XxxViewModel | HomeViewModel |
| UiState | XxxUiState | HomeUiState |
| Screen | XxxScreen | HomeScreen |
| Mapper | XxxMapper | NoteMapper |
| 枚举 | PascalCase | BlockType.TEXT |

---

## 11. 错误处理

### 11.1 数据层

- Room 操作使用 `Result<T>` 或自定义 sealed class 包装结果
- 所有 suspend 函数内部 try-catch，不抛异常到上层
- 图片操作错误有明确错误类型（拍照失败、写入失败等）

### 11.2 UI 层

- 每个 UiState 包含 `error: String?` 字段
- 错误显示使用 Snackbar
- 关键操作（删除、清空回收站）有确认对话框

### 11.3 全局

- Application 级 CoroutineExceptionHandler
- 崩溃日志写入本地文件
- Timber 统一日志输出

---

## 12. 测试策略

| 层级 | 测试内容 | 框架 |
|------|----------|------|
| Data 层 | DAO 增删改查、Repository 逻辑 | Room Testing + JUnit5 + Turbine |
| Mapper | 转换逻辑正确性 | JUnit5 |
| Domain | 业务逻辑（v2.0 UseCase 后增加） | JUnit5 |
| UI 层 | 关键页面组件渲染、交互 | Compose Test |

MVP 重点：**单元测试覆盖率 > 60%**（DAO / Repository / Mapper）。

---

## 13. 后续扩展预留

### AI 功能（v2.0）

```kotlin
interface AiService {
    suspend fun summarize(note: Note): String
    suspend fun ask(question: String, context: List<Note>): String
    suspend fun ocrImage(imageUri: String): String
}
```

- DeepSeek / 通义千问 / OpenAI 兼容 API，只需替换实现
- 搜索页 AI 搜索入口已预留
- 编辑页 AI 按钮已预留

### 复习提醒（v2.0）

- Note 表已预留可扩展空间
- WorkManager 实现定时提醒
- 艾宾浩斯间隔：1天 / 3天 / 7天 / 15天 / 30天

### Block 类型扩展

- LATEX（公式）
- CODE（代码块）
- TODO（待办）
- QUOTE（引用）
- TABLE（表格）
- AUDIO（语音）
- DRAWING（手绘）

仅需新增 BlockType 枚举值 + 对应渲染组件，数据库无需迁移。

---

## 14. UI 原型

UI 原型图见：`docs/ui-mockups/`

| 文件 | 说明 |
|------|------|
| 01-home-v2.png | 首页（列表模式 + 空状态） |
| 02-note-editor.png | 笔记编辑页 + Block BottomSheet |
| 03-tags-search-settings.png | 标签页 / 探索页 / 设置页 |

---

## 15. Roadmap

```
v1.0 MVP ──────────────────────┐
  核心记录功能                │
  Block 编辑器                 │
  标签分类                    │
  拍照                         │
  搜索 + 导出                  │
  回收站                       │
                               │
v1.5 ────────────┐             │
  代码块         │             │
  引用块         │             │
  待办清单       │             │
                 │             │
v2.0 ────────────┤─────────────┘
  AI 搜索
  AI 总结/问答
  OCR 识别
  复习提醒
  公式渲染
  
v3.0
  云同步
  多端支持
```

---

**文档结束**
