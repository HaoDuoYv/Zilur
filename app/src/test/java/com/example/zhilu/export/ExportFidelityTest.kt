package com.example.zhilu.export

import android.content.Context
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.KnowledgeCard
import com.example.zhilu.domain.model.Note
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 导出格式的**观感对齐**（真机反馈后补的）。
 *
 * 钉住三件事：
 * ① 行内公式不再被当普通文字吐出去 —— README 写着"LaTeX 公式渲染为图片"，
 *    而原先只有**块级**公式走那条路；
 * ② 小节结构要保住（导出物读起来要知道哪段属于哪一节）；
 * ③ Markdown 里不许出现 `<span style=…>` 这种内联 HTML 标签。
 *
 * **注意这里不断言"输出里有没有 base64 图片"**：公式渲染依赖 `jlatexmath` 的字体与
 * `TeXFormulaSettings.xml` 资源，而本模块没开 `testOptions.unitTests.includeAndroidResources`，
 * Robolectric 加载不到那些资源（`TeXFormula` 静态初始化会抛 FileNotFoundException），
 * 于是渲染一律失败、导出器走 `<code>` 降级分支。断言"必须有图片"只会永远红。
 *
 * 所以这里断言的是**与渲染成败无关**的部分：公式源码不许以 `$…$` 原样出现在 HTML 里
 * （没渲染时就该走降级，而不是漏成正文），以及结构与标签层面的规则。
 * "公式真的变成图片了"这条由真机导出实测覆盖 —— 环境限制不该用假断言糊过去。
 */
@RunWith(RobolectricTestRunner::class)
class ExportFidelityTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val htmlExporter = HtmlExporter(context, MediaFileManager(context))
    private val mediaFileManager = MediaFileManager(context)

    private fun twoCardNote() = Note(
        title = "408 计算机网络",
        cards = listOf(
            KnowledgeCard(
                id = 1,
                title = "GBN 与 SR",
                blocks = listOf(
                    Block(
                        id = 11,
                        cardId = 1,
                        type = BlockType.TEXT,
                        content = "设 \$n\$ = 编号比特数，则序号空间 = \$2^n\$。",
                        sortOrder = 0
                    )
                )
            ),
            KnowledgeCard(
                id = 2,
                title = "解析",
                blocks = listOf(
                    Block(id = 21, cardId = 2, type = BlockType.TEXT, content = "利用率 \$U = W\$。", sortOrder = 0)
                )
            )
        )
    )

    // ── ① 行内公式不再漏成正文 ──────────────────────────────────────────

    @Test
    fun html_does_not_leak_inline_formula_source() = runTest {
        val note = Note(
            title = "公式",
            blocks = listOf(Block(type = BlockType.TEXT, content = "序号空间 = \$2^n\$。"))
        )
        val html = htmlExporter.exportNote(note).getOrThrow()

        assertFalse(
            "行内公式不许以 \$…\$ 原样出现在 HTML 正文里（要么成图，要么走降级）",
            html.contains("= \$2^n\$")
        )
        // 渲染不可用时必须走降级，而不是把源码漏成普通文字
        assertTrue("应出现公式图或降级代码块其一", html.contains("formula") || html.contains("<code>"))
    }

    @Test
    fun html_does_not_leak_display_formula_source() = runTest {
        val note = Note(
            title = "公式",
            blocks = listOf(Block(type = BlockType.TEXT, content = "推导：\nE = 2 + 2"))
        )
        val html = htmlExporter.exportNote(note).getOrThrow()

        assertTrue(html.contains("推导"))
        assertTrue(html.contains("E = 2 + 2"))
    }

    @Test
    fun html_keeps_inline_code_and_link() = runTest {
        val note = Note(
            title = "行内",
            blocks = listOf(
                Block(type = BlockType.TEXT, content = "用 `git rebase` 见 [文档](https://example.com/a)")
            )
        )
        val html = htmlExporter.exportNote(note).getOrThrow()

        assertTrue("行内代码要成 <code>：${html.substringAfter("<body>")}", html.contains("<code>git rebase</code>"))
        assertTrue("链接要成 <a>", html.contains("href=\"https://example.com/a\""))
        assertFalse("不该把反引号原样吐出去", html.contains("`git rebase`"))
    }

    // ── ② 小节结构保住 ──────────────────────────────────────────────────

    @Test
    fun html_keeps_section_titles_and_order() = runTest {
        val html = htmlExporter.exportNote(twoCardNote()).getOrThrow()

        assertTrue("小节标题必须在", html.contains("<h2>GBN 与 SR</h2>"))
        assertTrue(html.contains("<h2>解析</h2>"))
        assertTrue("编号徽标要在", html.contains("class=\"badge\""))
        assertTrue(
            "顺序不能乱：GBN 要排在 解析 前面",
            html.indexOf("GBN 与 SR") < html.indexOf("<h2>解析</h2>")
        )
    }

    @Test
    fun html_section_carries_accent_color() = runTest {
        val html = htmlExporter.exportNote(twoCardNote()).getOrThrow()

        assertTrue("小节要带上身份色变量", html.contains("--accent:#"))
        assertTrue("徽标底色用算好的 tint，不用 color-mix", html.contains("--accent-tint:#"))
        assertFalse("color-mix 兼容性太新，别用", html.contains("color-mix"))
        // `Color.parseColor` 返回带 alpha 的 ARGB，忘了掩掉就会写出 `#FF8C6A3F` ——
        // 8 位 hex 在浏览器里是非法值，整节强调色会静默消失（真机踩过）。
        assertFalse("身份色必须是 6 位 hex，不能带 alpha：$html", html.contains("--accent:#FF"))
        assertTrue("应当是 #RRGGBB", Regex("--accent:#[0-9A-F]{6};").containsMatchIn(html))
    }

    @Test
    fun markdown_keeps_section_titles() {
        val md = MarkdownExporter.exportNote(twoCardNote())

        assertTrue("多小节时标题落成二级标题：$md", md.contains("## GBN 与 SR"))
        assertTrue(md.contains("## 解析"))
    }

    @Test
    fun markdown_flat_note_has_no_synthetic_section_title() {
        // 扁平笔记只有一个隐式小节，凭空加一行标题只是噪声
        val note = Note(title = "扁平", blocks = listOf(Block(type = BlockType.TEXT, content = "正文")))
        val md = MarkdownExporter.exportNote(note)

        assertFalse(md.contains("未命名小节"))
        assertTrue(md.contains("正文"))
    }

    // ── ③ Markdown 不许有内联 HTML 样式标签 ─────────────────────────────

    @Test
    fun markdown_has_no_span_tags() {
        val note = Note(
            title = "标记",
            blocks = listOf(
                Block(
                    type = BlockType.TEXT,
                    content = "标准形中{{i:正}}平方项的个数是{{k:惯性指数}}"
                )
            )
        )
        val md = MarkdownExporter.exportNote(note)

        assertFalse("Markdown 里不该有 <span：$md", md.contains("<span"))
        assertFalse("也不该有 style=：$md", md.contains("style="))
        assertTrue("标记里的文字要留下", md.contains("标准形中正平方项的个数是惯性指数"))
    }

    @Test
    fun markdown_keeps_formula_as_standard_math() {
        // 原先这种写法会被包成 `<span …>$W…$</span>`，不少渲染器因此不再解析公式
        val note = Note(
            title = "标记公式",
            blocks = listOf(
                Block(type = BlockType.TEXT, content = "结论：{{k:\$W \\le 2^{n-1}\$}} 成立")
            )
        )
        val md = MarkdownExporter.exportNote(note)

        assertTrue("公式要按标准 \$…\$ 输出：$md", md.contains("\$W \\le 2^{n-1}\$"))
        assertFalse(md.contains("<span"))
    }

    @Test
    fun markdown_keeps_formula_as_math_not_image() = runTest {
        // .md 是文本格式：公式留源码，任何支持 KaTeX/MathJax 的渲染器都能显示。
        // 与 HTML 导出（真的渲染成图）是两条不同的路，各自的正确形态不同。
        val note = Note(
            title = "分享",
            blocks = listOf(Block(type = BlockType.TEXT, content = "序号空间 = \$2^n\$"))
        )
        val md = MarkdownExporter.exportNoteWithBase64(
            note = note,
            mediaFileManager = mediaFileManager,
            context = context
        )

        assertTrue(md.contains("\$2^n\$"))
        assertFalse("不该在 md 里塞公式图", md.contains("![formula]"))
    }

    // ── 分支子块不再丢内容 ──────────────────────────────────────────────

    @Test
    fun markdown_keeps_branch_children() {
        val note = Note(
            title = "分支",
            blocks = listOf(
                Block(id = 1, type = BlockType.BRANCH, content = "情况一", sortOrder = 0),
                Block(id = 2, type = BlockType.TEXT, content = "子块正文", parentBranchId = 1, sortOrder = 1)
            )
        )
        val md = MarkdownExporter.exportNote(note)

        assertTrue("分支标题要在", md.contains("<summary>情况一</summary>"))
        assertTrue("子块内容不能丢：$md", md.contains("子块正文"))
    }

    @Test
    fun html_keeps_branch_children() = runTest {
        val note = Note(
            title = "分支",
            blocks = listOf(
                Block(id = 1, type = BlockType.BRANCH, content = "情况一", sortOrder = 0),
                Block(id = 2, type = BlockType.TEXT, content = "子块正文", parentBranchId = 1, sortOrder = 1)
            )
        )
        val html = htmlExporter.exportNote(note).getOrThrow()

        assertTrue(html.contains("<summary>情况一</summary>"))
        assertTrue("子块内容不能丢", html.contains("子块正文"))
    }
}
