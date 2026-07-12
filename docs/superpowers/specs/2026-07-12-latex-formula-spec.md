# 知录 LaTeX 公式规范

**Goal：** 统一项目内 LaTeX 公式的输入、渲染与兼容性行为，避免平台差异导致显示异常。

**Scope：** 适用于 `ui/note/blocks/LatexBlockEditor.kt`、`LatexBlockView`、`ReadOnlyLatexBlockContent` 及所有通过 `LatexRenderer` 渲染的公式。

---

## 1. 渲染引擎

- 使用 **JLatexMath** 将 LaTeX 源码渲染为 `ImageBitmap`。
- 不支持 MathJax、KaTeX 等 Web 渲染引擎。
- 所有公式在渲染前都会经过 `sanitizeLatex()` 预处理。

---

## 2. 支持的定界符

| 输入形式 | 处理方式 | 说明 |
|---|---|---|
| 裸文本，如 `E=mc^2` | 自动包裹为 `$$E=mc^2$$` | 推荐日常输入 |
| `$$...$$` | 原样保留 | 显示公式 |
| `$...$` | 原样保留 | 行内公式 |
| `\(...\)` | 原样保留 | 行内公式 |
| `\[...\]` | 去掉外层 `\[\]`，再按裸文本规则处理 | 兼容部分导出格式 |
| `\begin{...}...\end{...}` | 原样保留 | 环境公式 |

---

## 3. 环境转换

- `align` / `align*` 环境会被转换为 `array` 环境：
  - `\begin{align}` → `\begin{array}{rl}`
  - `\begin{align*}` → `\begin{array}{rl}`
  - `\end{align}` / `\end{align*}` → `\end{array}`
- 其他环境（如 `array`、`matrix`、`cases`）保持原样。

---

## 4. 行间距参数

- JLatexMath 不支持 `\\[8pt]` 这类可选行间距参数，在 `array` 环境中会把 `[8pt]` 当成普通文本渲染。
- `sanitizeLatex()` 会统一把 `\\[<spacing>]` 替换为 `\\`，保留换行但丢弃间距。
- 示例：
  ```latex
  \begin{array}{ll}
  a & b \\[8pt]
  c & d
  \end{array}
  ```
  处理后等价于：
  ```latex
  \begin{array}{ll}
  a & b \\
  c & d
  \end{array}
  ```

---

## 5. 推荐写法

### 5.1 简单公式

```latex
E = mc^2
```

自动渲染为显示公式。

### 5.2 多行对齐

```latex
\begin{array}{ll}
\textbf{原方程：} & (y^2 - 1)\mathrm{d}x + (2xy - \cos y)\mathrm{d}y = 0 \\
\textbf{拆项：} & y^2\mathrm{d}x + 2xy\mathrm{d}y - \mathrm{d}x - \cos y\mathrm{d}y = 0 \\
\textbf{通解：} & \boxed{xy^2 - x - \sin y = C}
\end{array}
```

- 使用 `array` 环境
- 行分隔只用 `\\`，不要写 `\\[8pt]`
- 列格式用 `ll`、`rl`、`cc` 等标准格式

### 5.3 避免使用

| 不推荐 | 原因 |
|---|---|
| `\\[8pt]` | 间距参数会被丢弃，旧版本会渲染为文本 |
| `align` / `align*` | 会自动转换，但建议直接写 `array` |
| 混合 `$$` 与 `\begin{array}` | 语法冗余，虽可渲染但不够干净 |

---

## 6. 异常与降级

- 渲染失败时显示红色错误图标，并记录日志。
- 不会阻塞 UI，错误块可重新编辑。
- 渲染结果会缓存，同一段源码不会重复渲染。

---

## 7. 测试

- 新增/修改 `sanitizeLatex()` 行为时，需在 `app/src/test/java/com/example/zhilu/ui/note/latex/LatexSanitizerTest.kt` 中补充用例。
- 关键覆盖点：裸公式包裹、`align` 转 `array`、`\\[8pt]` 剥离、显示定界符去除。
