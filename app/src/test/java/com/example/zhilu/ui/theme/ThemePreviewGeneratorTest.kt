package com.example.zhilu.ui.theme

import androidx.compose.ui.graphics.Color
import com.example.zhilu.data.datastore.AccentColor
import com.example.zhilu.data.datastore.ThemePalette
import com.example.zhilu.domain.model.EmphasisTone
import java.io.File
import org.junit.Test

/**
 * 生成一份**主题预览页**（`app/build/theme-preview/index.html`）。
 *
 * 为什么需要它：动森主题是**视觉**产物，而"看起来对不对"没法靠断言回答。
 * 这一页把所有色值、圆角阶梯、对比度实测值摊开，用浏览器直接看 ——
 * 换配色时可以立刻发现"某一档糊了"，不必先装到设备上再一个个页面翻。
 *
 * 值全部取自 `palettePaint()`，**不重复写 hex**：预览页与真机永远同源，
 * 不会出现"预览好看、真机是另一个色"。
 *
 * 它不做断言（唯一目的是产出可看的文件）。真正的守卫是 `AccentPaletteTest`。
 */
class ThemePreviewGeneratorTest {

    private val sur = "\u00A5"

    @Test
    fun generatePreview() {
        val outDir = File("build/theme-preview").apply { mkdirs() }
        val accents = AccentColor.entries

        val html = buildString {
            appendLine("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">")
            appendLine("<title>知录 · 主题预览</title>")
            appendLine("<style>${PREVIEW_CSS}</style></head><body>")
            appendLine("<h1>主题预览</h1>")
            appendLine(
                "<p class=\"note\">由 <code>ThemePreviewGeneratorTest</code> 从 " +
                    "<code>palettePaint()</code> 直接生成 —— 与真机同源，" +
                    "不是另画的一份。</p>"
            )

            ThemePalette.entries.forEach { palette ->
                val paint = palettePaint(palette)
                appendLine("<h2>${paletteLabel(palette)} <span class=\"dim\">${palette.name}</span></h2>")
                appendLine("<p class=\"desc\">${paletteDescription(palette)}</p>")

                listOf(true to "深色", false to "浅色").forEach { (dark, modeName) ->
                    val scheme = paint.scheme(dark)
                    appendLine("<section class=\"mode\" style=\"background:${scheme.background.hex()};color:${scheme.onBackground.hex()}\">")
                    appendLine("<h3>${paletteLabel(palette)} · $modeName</h3>")

                    appendLine("<div class=\"row\">${swatch("background", scheme.background)}${swatch("surface", scheme.surface)}${swatch("surfaceVariant", scheme.surfaceVariant)}${swatch("outline", scheme.outline)}${swatch("outlineVariant", scheme.outlineVariant)}</div>")
                    appendLine("<div class=\"row\">${swatch("onBackground", scheme.onBackground)}${swatch("onSurface", scheme.onSurface)}${swatch("onSurfaceVariant", scheme.onSurfaceVariant)}</div>")

                    appendLine("<h4>主色族（按钮 / FAB）</h4>")
                    appendLine("<div class=\"row\">")
                    listOf(
                        "primary" to scheme.primary, "secondary" to scheme.secondary,
                        "tertiary" to scheme.tertiary, "error" to scheme.error,
                        "primaryContainer" to scheme.primaryContainer
                    ).forEach { (name, c) -> append(swatch(name, c)) }
                    appendLine("</div>")

                    appendLine("<h4>语义角色（要点 / 想法 / 注意 / 待办）</h4>")
                    appendLine("<div class=\"row\">")
                    EmphasisTone.entries.forEach { tone ->
                        val mark = paint.toneBlock(tone, dark)
                        append(swatch(tone.label, mark))
                    }
                    appendLine("</div>")

                    appendLine("<h4>卡片身份色轮转</h4>")
                    appendLine("<div class=\"row\">")
                    paint.cardAccents.forEachIndexed { index, argb ->
                        val c = if (dark) darkTagColor(argb) else Color(argb)
                        append(swatch("#$index", c))
                    }
                    appendLine("</div>")

                    appendLine("<h4>强调色板（8 档）</h4>")
                    appendLine("<div class=\"row\">")
                    accents.forEach { accent ->
                        val roles = paint.accent(accent)
                        append(swatch(roles.label, if (dark) roles.dark.primary else roles.light.primary))
                    }
                    appendLine("</div>")

                    appendLine("<h4>圆角阶梯</h4><div class=\"row\">")
                    // 圆角 token 是**数值**（`ShapeTokens` / 动森那套），不是从 Shapes 反解 ——
                    // CornerBasedShape 不暴露角尺寸，反解要绕一大圈还不准。
                    cornerSteps(palette).forEach { (n, dp) ->
                        append("<div class=\"radius\" style=\"border-radius:${dp.toInt()}px\">$n<br><small>${dp.toInt()}dp</small></div>")
                    }
                    appendLine("</div>")

                    appendLine(contrastTable(palette, dark, scheme, paint))
                    appendLine("</section>")
                }
            }
            appendLine("</body></html>")
        }

        File(outDir, "index.html").writeText(html)
        println("PREVIEW ${File(outDir, "index.html").absolutePath} (${html.length} chars)")
    }

    /** 对比度实测表：调色时直接看数字，不用再跑一遍测试。 */
    private fun contrastTable(
        palette: ThemePalette,
        dark: Boolean,
        scheme: androidx.compose.material3.ColorScheme,
        paint: PalettePaint
    ): String = buildString {
        val label = "${palette.name}/${if (dark) "dark" else "light"}"
        appendLine("<h4>对比度实测（文字需 ≥ 4.5，图形 ≥ 3.0）</h4>")
        appendLine("<table><tr><th>配对</th><th>实测</th><th>门槛</th><th>结果</th></tr>")

        fun row(name: String, fg: Color, bg: Color, threshold: Float) {
            val ratio = contrastRatio(fg, bg)
            val ok = ratio >= threshold
            appendLine(
                "<tr><td>$name</td><td class=\"num\">${"%.2f".format(ratio)}</td>" +
                    "<td class=\"num\">${"%.1f".format(threshold)}</td>" +
                    "<td class=\"${if (ok) "ok" else "bad"}\">${if (ok) "通过" else "不足"}</td></tr>"
            )
        }

        row("正文 / 背景", scheme.onBackground, scheme.background, 4.5f)
        row("正文 / 卡面", scheme.onSurface, scheme.surface, 4.5f)
        row("次级文字 / 凹陷面", scheme.onSurfaceVariant, scheme.surfaceVariant, 4.5f)
        row("onPrimary / primary", scheme.onPrimary, scheme.primary, 4.5f)
        row("onSecondary / secondary", scheme.onSecondary, scheme.secondary, 4.5f)
        row("onTertiary / tertiary", scheme.onTertiary, scheme.tertiary, 4.5f)
        row("onError / error", scheme.onError, scheme.error, 4.5f)

        EmphasisTone.entries.forEach { tone ->
            row("${tone.label}标记 / 卡面", paint.toneBlock(tone, dark), scheme.surface, 3.0f)
        }
        paint.cardAccents.forEachIndexed { index, argb ->
            val fill = if (dark) darkTagColor(argb) else Color(argb)
            row("身份色#$index 上的字", inkOnFill(fill), fill, 3.0f)
        }
        appendLine("</table>")
        append("<p class=\"dim small\">$label · 由 palettePaint() 生成</p>")
    }

    private fun swatch(name: String, color: Color): String =
        "<div class=\"sw\"><div class=\"chip\" style=\"background:${color.hex()}\"></div>" +
            "<div class=\"name\">$name</div><div class=\"hex\">${color.hex()}</div></div>"

    private fun Color.hex(): String = "#%06X".format((value shr 32).toInt() and 0xFFFFFF)

    /**
     * 一套外观的圆角阶梯（dp）。
     *
     * 直接从 token 常量取，**不从 `Shapes` 反解**：`CornerBasedShape` 不暴露角尺寸，
     * 反解既绕又不准。两套外观的数值分别是 `ShapeTokens`/`Radius` 与动森自己那组。
     */
    private fun cornerSteps(palette: ThemePalette): List<Pair<String, Float>> = when (palette) {
        ThemePalette.PAPER_INK -> listOf(
            "XS" to ShapeTokens.ExtraSmall.value,
            "S" to ShapeTokens.Small.value,
            "M" to Radius.Card.value,
            "L" to ShapeTokens.Large.value,
            "XL" to ShapeTokens.ExtraLarge.value
        )

        ThemePalette.ANIMAL_ISLAND -> listOf(
            "XS" to 8f, "S" to 14f, "M" to 20f, "L" to 26f, "XL" to 34f
        )
    }

    private companion object {
        /** 用 [sur] 只是为了让 diff 里能看出这是生成物；CSS 本身与主题无关。 */
        const val PREVIEW_CSS = """
            body { font-family: -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif; margin: 0; padding: 20px; background: #f5f5f5; color: #222; }
            h1 { font-size: 22px; margin: 0 0 4px; }
            h2 { font-size: 18px; margin: 28px 0 2px; }
            h3 { font-size: 15px; margin: 0 0 12px; opacity: 0.8; }
            h4 { font-size: 13px; margin: 18px 0 6px; opacity: 0.7; font-weight: 600; }
            .note, .desc, .dim { opacity: 0.6; font-size: 12px; }
            .small { font-size: 11px; }
            .mode { padding: 18px; border-radius: 14px; margin: 10px 0; }
            .row { display: flex; flex-wrap: wrap; gap: 10px; }
            .sw { text-align: center; font-size: 11px; }
            .chip { width: 78px; height: 46px; border-radius: 8px; border: 1px solid rgba(128,128,128,0.25); }
            .name { margin-top: 4px; }
            .hex { opacity: 0.55; font-family: monospace; font-size: 10px; }
            .radius { width: 78px; height: 46px; border: 1px dashed currentColor; opacity: 0.75; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 11px; }
            table { border-collapse: collapse; font-size: 12px; margin-top: 6px; }
            th, td { padding: 3px 10px 3px 0; text-align: left; }
            th { opacity: 0.6; font-weight: 600; }
            .num { font-family: monospace; }
            .ok { color: #2e7d32; }
            .bad { color: #c62828; font-weight: 700; }
        """
    }
}
