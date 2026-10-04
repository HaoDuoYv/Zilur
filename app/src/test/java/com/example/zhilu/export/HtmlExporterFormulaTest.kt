package com.example.zhilu.export

import android.content.Context
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Note
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import ru.noties.jlatexmath.JLatexMathAndroid

/**
 * 公式**真的渲染成了图片**（而不是走降级分支）。
 *
 * 这里同时是三条环境前提的活文档（配置在 `app/build.gradle.kts` 与
 * `src/test/resources/robolectric.properties`）：
 * ① `unitTests.isIncludeAndroidResources = true` —— jlatexmath 要从 assets 读
 *    `TeXFormulaSettings.xml` 与内置字体；
 * ② `unitTests.all { forkEvery = 1 }` —— `TeXFormula` 的静态初始化只要失败一次
 *    （例如某个**纯 JUnit 类**在 Robolectric 沙箱之外碰到它），这个类就永久不可用，
 *    之后所有渲染都抛 `NoClassDefFoundError` 并被导出器降级成 `<code>`。
 *    不隔离的话这个测试类**单独跑绿、跟别的类一起跑红**；
 * ③ 测试里手动 `JLatexMathAndroid.init(context)` —— 生产中这句由库自带的
 *    `JLatexMathInitProvider` 干，Robolectric 不跑 ContentProvider。
 *
 * 三条缺一不可，而缺了任何一条的**表现都是"公式没渲染成图片"** —— 很容易误判成功能没实现。
 */
@RunWith(RobolectricTestRunner::class)
class HtmlExporterFormulaTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val exporter = HtmlExporter(context, MediaFileManager(context))

    @Before
    fun initLatex() {
        JLatexMathAndroid.init(context)
    }

    @Test
    fun inline_formula_becomes_inline_image() = runTest {
        val note = Note(
            title = "行内公式",
            blocks = listOf(Block(type = BlockType.TEXT, content = "序号空间 = \$2^n\$。"))
        )

        val html = exporter.exportNote(note).getOrThrow()

        assertTrue(
            "行内公式必须变成 <img>：${html.substringAfter("<body>").take(400)}",
            html.contains("class=\"formula\"")
        )
        assertTrue("必须是内嵌 base64", html.contains("src=\"data:image/png;base64,"))
        assertTrue("公式源码要留进 alt（利于无障碍与复制）", html.contains("alt=\"2^n\""))
    }

    @Test
    fun display_formula_in_text_block_becomes_image() = runTest {
        val note = Note(
            title = "块级公式",
            blocks = listOf(Block(type = BlockType.TEXT, content = "推导：\n\$\$E=mc^2\$\$"))
        )

        val html = exporter.exportNote(note).getOrThrow()

        assertTrue(html.contains("class=\"formula\""))
        assertTrue(html.contains("alt=\"E=mc^2\""))
    }

    @Test
    fun latex_block_becomes_centered_image() = runTest {
        val note = Note(
            title = "公式块",
            blocks = listOf(Block(type = BlockType.LATEX, content = "\\frac{a}{b}"))
        )

        val html = exporter.exportNote(note).getOrThrow()

        assertTrue("公式块要走 .latex 容器", html.contains("class=\"latex\""))
        assertTrue(html.contains("data:image/png;base64,"))
    }

    @Test
    fun marked_formula_uses_semantic_ink_color() = runTest {
        // 整条公式被标记包住时，App 内也是给它上色的 —— 导出要跟上
        val note = Note(
            title = "上色公式",
            blocks = listOf(Block(type = BlockType.TEXT, content = "结论：{{k:\$W \\le 2^{n-1}\$}} 成立"))
        )

        val html = exporter.exportNote(note).getOrThrow()

        assertTrue(html.contains("class=\"formula\""))
    }
}
