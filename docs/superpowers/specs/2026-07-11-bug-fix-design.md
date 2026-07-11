# 知录笔记编辑页 Bug 修复设计文档

**Goal:** 修复 4 个独立 bug：连续添加块崩溃、拍照图片块丢失、链接块点击无反应、LaTeX 渲染异常与卡顿。

**Scope:** 仅修改 `ui/note` 层，不动数据模型和 Repository。

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
  - 暴露 `val blocks: SnapshotStateList<Block>` 给 Compose 直接观察；
  - `addBlock(type, afterIndex)`：生成唯一 id，设置 `isNewlyAdded = true`，插入列表；
  - `moveBlock/removeBlock/updateContent/clearNewFlag` 等操作；
  - `toPersistableList()`：持久化时按索引生成 `sortOrder`；
  - 用简单 `isProcessing` 标志防止同一帧内多次添加（UI 层再配 300ms 防抖）。
- `NoteViewModel` 不再直接操作 `_blocks`，改为调用 `BlockListManager`；
- UI 层新块焦点：`LaunchedEffect(block.id) { awaitFrame(); focusRequester.requestFocus() }`，布局完成后再要焦点。

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
- 导入前插入占位块，失败时自动移除并 Toast 提示；
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
- `LinkBlockEditor` placeholder 改为 `"标题|https://..."`。

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

## 5. Error Handling & Validation

- 块列表：防连点 + 延迟焦点；
- 图片导入：`Result<String>`，失败移除占位块 + Toast；
- 链接跳转：协议白名单 + ActivityNotFound 处理；
- LaTeX：Error 状态 UI 降级。

---

## 6. Testing

- 自动：`compileDebugKotlin`、`assembleDebug`、`lintDebug`；
- 手动：
  1. 快速连点「文字」按钮不崩溃；
  2. 拍照后笔记出现图片块；
  3. 相册选图后笔记出现图片块；
  4. 链接块只读态点击标题，系统浏览器打开；
  5. `align*` 输入不崩溃，显示 Error 提示；
  6. 长列表滑动无明显卡顿。
