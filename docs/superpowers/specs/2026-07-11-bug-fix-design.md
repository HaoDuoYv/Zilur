# 知录笔记编辑页 Bug 修复设计文档

**Goal:** 修复 4 个独立 bug：连续添加块崩溃、拍照图片块丢失、链接块点击无反应、LaTeX 渲染异常与卡顿。

**Scope:** 以 `ui/note` 为主，允许新增 `data/local/file/ImageFileManager.kt`（或等效包）负责文件复制，允许修改 `NoteViewModel` 的保存与块操作逻辑。数据模型实体（`BlockEntity`、`MediaEntity`）本身不变，但 `NoteViewModel.save()` 需配合临时 ID 替换逻辑。

---

## 1. BlockListManager 抽离（崩溃修复）

### Problem
快速点击添加块按钮时 App 崩溃。根因不是 `SnapshotStateList` 并发修改（Compose 主线程单线程），而是：
- 焦点请求在块完成布局前触发；
- `animateItemPlacement` 与快速插入冲突；
- 连点导致重复插入/焦点抢占。

### Design
- 新建 `ui/note/blocks/BlockListManager.kt`：
  - 内部用 `mutableStateListOf<Block>()` 持块；
  - 暴露 `val blocks: SnapshotStateList<Block>` 给 ViewModel；`NoteUiState.blocks` 字段类型也声明为 `SnapshotStateList<Block>`，直接引用 `BlockListManager.blocks`，Compose 可细粒度观察变化；
  - `addBlock(type, afterIndex)`：新块临时 id 用负数（如 `-System.currentTimeMillis()`），数据库自增 id 均为正数，天然不冲突；插入列表，并在内部 `newlyAddedIds` 集合中记录该 id；
  - 保存流程：负数 id 的块走 `INSERT`，正数 id 的块走 `UPDATE`；保存完成后用数据库返回的真实 id 替换 `BlockListManager` 中的临时 id；
  - `moveBlock/removeBlock/updateContent/clearNewFlag(id)` 等操作；
  - `toPersistableList()`：持久化时按索引生成 `sortOrder`；
  - 用简单 `isProcessing` 标志防止同一帧内多次添加（UI 层再配 300ms 防抖）。
- `NoteViewModel` 不再直接操作 `_blocks`，改为调用 `BlockListManager`；
- UI 层新块焦点：`LaunchedEffect(block.id) { awaitFrame(); focusRequester.requestFocus(); viewModel.clearNewFlag(block.id) }`，布局完成后再要焦点。

---

## 2. 图片导入统一（拍照图片丢失修复）

### Problem
拍照后笔记里没出现图片块。相机返回的 Uri 通常是临时 content Uri，权限失效后图片无法显示。

### Design
- 新增 `ImageFileManager`：
  - 负责把相册 Uri 复制到 `context.filesDir/images/`；
  - `import(uri: Uri): Result<String>` 返回内部 Uri。
- `MediaRepository` 只管理元数据，不碰文件系统；
- 相机场景优化：启动相机前预创建内部文件，通过 `FileProvider` 把该文件作为拍照目标。返回后无需再 import；
- `NoteViewModel` 统一方法：
  - 相册：`importImageToStorage(uri)` → 插入 Media → `BlockListManager.addBlock(IMAGE, internalUri)`；
  - 相机：直接用内部文件 Uri → 插入 Media → `BlockListManager.addBlock(IMAGE, internalUri)`；
- 占位块就是 `BlockType.IMAGE`，`content = ""`；`ImageBlockView` 处理 `content.isEmpty()` 时显示 `CircularProgressIndicator` 骨架；导入成功后更新 `content` 为内部 Uri，失败后 `BlockListManager.removeBlock(index)` 移除；
- `isProcessingImage` 保持 Boolean（当前单选），后续多选可改计数器。

---

## 3. LinkBlockViewer 拆分（链接点击修复）

### Problem
链接块只读态点了没反应，因为当前 `LinkBlockEditor` 只提供编辑态输入框。

### Design
- 新增 `ui/note/blocks/LinkBlockViewer.kt`：
  - 解析 `title|url`，容错：无 `|` 的纯 URL 用域名做 title；
  - 显示链接图标 + title（primary 色，暗示可点击）+ URL（onSurfaceVariant，单行省略）；
  - 整个卡片点击跳转，发 `ACTION_VIEW` Intent；
  - URL 协议白名单：只接受 http/https；
  - `resolveActivity` + `try-catch(ActivityNotFoundException)`，无浏览器时 Toast；
  - 格式错误时显示"链接格式错误，点击编辑修复"。
- `BlockContent` 分发：`isEditing` 用 `LinkBlockEditor`，只读用 `LinkBlockViewer`；
- `LinkBlockEditor` 同步修改：placeholder 改为 `"标题|https://..."`，提示用户输入格式。

---

## 4. LaTeX 预处理与渲染优化

### Problem
输入 `align*` 或 `\\[4pt]` 报错，长列表滑动卡顿。

### Design
- 新增 `sanitizeLatex(input: String): String`：
  - 检测已有定界符（`$`, `\begin{`, `\[`, `\(`），避免重复包裹；
  - 无定界符时包成 `$$...$$`；
  - 简单正则把 `align*` 两列环境转成 `array{rl}`；
  - 复杂/不支持环境不强行转换，失败时 Error 降级；
  - 移除 `\[`, `\]` 后再做定界符判断。
- 渲染状态机：`LatexRenderState { Loading, Success(bitmap), Error(rawLatex, message) }`；
- `rememberLatexImage` 改用 `produceState` + `Dispatchers.Default`：
  - 先查 `LruCache`；
  - 未命中在后台线程渲染；
  - 成功缓存并更新状态；
  - 失败进入 Error 状态；
- `LruCache` 容量提升到 100+；
- Error UI：显示 `⚠ 公式渲染失败` + 原始源码（等宽字体），不崩溃。

---

## 5. 文件变更清单

| 新建文件 | 修改文件 |
|----------|----------|
| `ui/note/blocks/BlockListManager.kt` | `ui/note/NoteViewModel.kt` |
| `ui/note/blocks/LinkBlockViewer.kt` | `ui/note/blocks/BlockContent.kt` |
| `data/local/file/ImageFileManager.kt`（或等效包） | `ui/note/blocks/LinkBlockEditor.kt`（placeholder） |
| `ui/note/latex/LatexRenderState.kt`（如需要） | `ui/note/blocks/ImageBlockView.kt`（loading 态） |
| | `ui/note/blocks/LatexBlockEditor.kt` / `LatexImage.kt` |

---

## 6. Error Handling & Validation

- 块列表：防连点 + 延迟焦点；
- 图片导入：`Result<String>`，失败移除占位块 + Toast；
- 链接跳转：协议白名单 + ActivityNotFound 处理；
- LaTeX：Error 状态 UI 降级。

---

## 7. Testing

### 单元测试
- `BlockListManagerTest`：
  - 快速两次 `addBlock` 只产生一个块；
  - `moveBlock` 后顺序正确；
  - `toPersistableList()` 的 `sortOrder` 按索引生成；
  - 临时 id 为负数。
- `LatexSanitizerTest`：
  - 行内 `$...$` 不重复包裹；
  - 裸公式包成 `$$...$$`；
  - `align*` 两列环境转成 `array{rl}`；
  - 非法输入有确定输出。
- `ImageFileManagerTest`（使用 mock ContentResolver）：
  - 成功复制返回 `Result.Success`；
  - 输入流为空返回 `Result.Failure`；
  - IO 异常返回 `Result.Failure`。

### 构建与 lint
- `compileDebugKotlin`、`assembleDebug`、`lintDebug`。

### 手动验证
  1. 快速连点「文字」按钮不崩溃；
  2. 拍照后笔记出现图片块；
  3. 相册选图后笔记出现图片块；
  4. 链接块只读态点击标题，系统浏览器打开；
  5. `align*` 输入不崩溃，显示 Error 提示；
  6. 长列表滑动无明显卡顿。
