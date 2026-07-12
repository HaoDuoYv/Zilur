# 笔记分享与导入设计文档

## Why

当前笔记详情页的「分享」按钮仅发送纯文本（标题 + 各 Block 的 content），无法保留图片、公式、代码等富内容的完整呈现，也无法被其他设备或本应用重新导入。需要把分享格式升级为 HTML、Markdown、.dtk 三种，并在首页提供 .dtk 导入入口，实现知识点的完整导出与跨设备迁移。

## Goals

1. 笔记详情页分享支持三种格式：
   - **HTML**：自包含文件，图片与公式均内嵌 base64，样式与软件内排版大致一致。
   - **Markdown**：自包含文件，图片与公式均内嵌 base64，保留代码块、链接、待办等结构。
   - **.dtk**：应用自定义格式，ZIP 压缩包，包含单条笔记的 JSON 元数据与媒体文件，可在首页「导入知识点」处导入。
2. 首页新增「导入知识点」入口，支持选择 .dtk 文件并静默创建笔记。
3. 导出/导入过程不阻塞主线程，失败时给出明确提示。

## Non-Goals

- 不支持从 HTML/Markdown 文件导入（仅 .dtk 可导入）。
- 不实现跨应用云端同步，导出/导入依赖本地文件与系统分享。
- 不迁移复习/提醒/待办状态，导入时仅恢复笔记内容与标签。

## Architecture

```
app/src/main/java/com/example/zhilu/
├── export/
│   ├── MarkdownExporter.kt          # Markdown 导出（增强，支持 base64 图片/公式）
│   ├── HtmlExporter.kt              # HTML 导出
│   └── DtkExporter.kt               # .dtk 导出（ZIP 打包）
├── data/local/file/
│   ├── ArchiveManager.kt            # ZIP 打包/解压
│   └── MediaFileManager.kt          # 媒体文件复制/读取/base64 转换
├── ui/note/
│   └── ShareFormatBottomSheet.kt    # 分享格式选择 BottomSheet
├── ui/home/
│   └── ImportKnowledgePoint.kt      # FAB 导入相关 UI/launcher
└── domain/usecase/
    └── ImportKnowledgeUseCase.kt    # 导入业务逻辑
```

## Design Details

### 1. 数据模型

#### 导出输入

使用 `Note`（含 `KnowledgeCard` 列表）、`List<Media>`、`List<TodoItem>`。若当前 UI 状态使用 `KnowledgeCard`，导出前需把卡片聚合成统一的 Block 列表并重新生成 `sortOrder`。

#### .dtk 内部结构

```
example.dtk (ZIP)
├── note.json
└── media/
    ├── image_1.png
    ├── image_2.jpg
    └── ...
```

- `note.json` 沿用现有 `JsonExporter` 的 note 结构，但仅包含单条笔记，并增加 `version` 与 `dtkVersion` 字段（当前为 1）。
- `media/` 目录存放笔记引用的图片文件，文件名使用导出时为媒体生成的新 ID 或原始文件名，JSON 中 `Media.uri` 记录相对路径 `media/<filename>`。
- 公式不放入 `media/`，而是在导出时渲染为 base64 PNG 后以内嵌方式写入 HTML/Markdown；.dtk 中公式保留原始 LaTeX 源码在 `Block.content`。

### 2. 导出流程

#### 分享入口

1. 用户在笔记详情页点击顶部「分享」图标。
2. 弹出 `ShareFormatBottomSheet`：「HTML」「Markdown」「.dtk」。
3. 用户选择格式后，在 `NoteViewModel` 或 UseCase 中后台生成文件。
4. 生成完成后通过 `FileProvider` 暴露 URI，调用 `ACTION_SEND` 打开系统分享面板。

#### HTML 导出

- 使用 Jetpack Compose 的 `rememberLatexImage` 渲染能力或 `LatexRenderer` 同步渲染接口把 LaTeX 转为 base64 PNG。
- 图片通过 `MediaFileManager` 读取为字节数组后转 base64。
- 输出完整 HTML 文档：`<html><head><style>...</style></head><body>...</body></html>`。
- 样式目标：白色卡片背景、16dp 等效内边距、代码块等宽背景、公式居中、图片自适应宽度。

#### Markdown 导出

- 复用 `MarkdownExporter`，增强图片与公式处理：
  - 图片：从 `Media` 读取后生成 `data:image/png;base64,...` 链接。
  - 公式：调用 LaTeX 渲染生成 base64 PNG，输出 `![formula](data:image/png;base64,...)`。
  - 代码块、链接、待办、分割线保持现有 Markdown 语法。
- 保留笔记标题作为一级标题，标签以 `#标签名` 形式追加。

#### .dtk 导出

1. 在应用缓存目录创建临时目录。
2. 写入 `note.json`。
3. 遍历笔记中的图片块，把对应 `Media` 文件复制到 `media/` 目录。
4. 调用 `ArchiveManager.zip()` 打包为 `.dtk` 文件。
5. 通过 `FileProvider` 分享该文件。

### 3. 导入流程

#### 入口

首页 FAB（悬浮按钮）点击后展开菜单，新增「导入知识点」选项。

#### 文件选择

使用 `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())`，限定 MIME 类型为 `application/zip` 或自定义 `application/octet-stream`（配合 `.dtk` 后缀过滤）。

#### 解析与确认

1. 后台在 `Dispatchers.IO` 中：
   - 把选择 URI 复制到缓存目录。
   - 用 `ArchiveManager.unzip()` 解压。
   - 解析 `note.json` 得到 `Note` 与 `List<Media>`（此时 `Media.uri` 为相对路径）。
   - 把 `media/` 下文件复制到应用私有目录 `filesDir/images/`。
   - 更新 `Media.uri` 为新的内部文件 URI。
2. 弹出确认对话框，展示：
   - 笔记标题
   - 块数量
   - 图片数量
3. 用户点击「导入」后，调用 `NoteRepository.insertNote()` 保存笔记与媒体。
4. 首页显示 Snackbar「导入成功」。

#### 版本兼容

- `note.json` 必须包含 `dtkVersion` 字段。
- 当前只支持版本 1；未来版本不匹配时提示「不支持的 .dtk 版本」。

### 4. 错误处理

| 场景 | 行为 |
|------|------|
| 导出时图片文件不存在 | 跳过该图片并在 HTML/Markdown 中保留占位文本，不中断导出 |
| 公式渲染失败 | 保留原始 LaTeX 源码作为文本，不中断导出 |
| ZIP 打包/IO 异常 | Snackbar 提示「导出失败：存储空间不足或文件被占用」 |
| 用户选择非 .dtk 文件 | 对话框提示「请选择 .dtk 格式的文件」 |
| ZIP 损坏或 JSON 解析失败 | 对话框提示「文件已损坏或格式不正确」 |
| .dtk 版本不兼容 | 对话框提示「不支持的 .dtk 版本，请升级应用」 |
| 导入时媒体复制失败 | 回滚已创建的临时文件，对话框提示导入失败 |

### 5. 测试策略

- **单元测试**：
  - `HtmlExporterTest`：断言输出包含标题、图片 base64、公式 img、代码块 pre 标签。
  - `MarkdownExporterTest`：断言图片与公式使用 base64 数据 URI，代码块 fenced。
  - `DtkExporterTest`：断言 ZIP 包含 `note.json` 与预期 `media/` 文件。
  - `ArchiveManagerTest`：断言 ZIP 打包/解压后文件一致。
  - `ImportKnowledgeUseCaseTest`：断言导入后 `Media.uri` 指向应用私有目录，笔记块顺序正确。
- **构建验证**：`assembleDebug`、`lintDebug`。
- **手动验证**：导出三种格式并通过文件管理器/微信发送；导入 .dtk 后检查媒体显示。

## Affected Code

- `app/src/main/java/com/example/zhilu/export/MarkdownExporter.kt`
- `app/src/main/java/com/example/zhilu/export/HtmlExporter.kt`（新建）
- `app/src/main/java/com/example/zhilu/export/DtkExporter.kt`（新建）
- `app/src/main/java/com/example/zhilu/data/local/file/ArchiveManager.kt`（新建）
- `app/src/main/java/com/example/zhilu/data/local/file/MediaFileManager.kt`（新建或复用）
- `app/src/main/java/com/example/zhilu/ui/note/NoteEditScreen.kt`
- `app/src/main/java/com/example/zhilu/ui/note/ShareFormatBottomSheet.kt`（新建）
- `app/src/main/java/com/example/zhilu/ui/home/HomeScreen.kt`
- `app/src/main/java/com/example/zhilu/ui/home/ImportKnowledgePoint.kt`（新建）
- `app/src/main/java/com/example/zhilu/domain/usecase/ImportKnowledgeUseCase.kt`（新建）
- `app/src/main/res/xml/file_paths.xml`（若 FileProvider 路径不足则补充）

## Open Questions

无。本设计基于用户已确认的方案与交互流程。
