package com.example.zhilu.ui.note.latex

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * LaTeX 渲染过程中的状态。
 */
sealed class LatexRenderState {
    data object Loading : LatexRenderState()
    data class Success(val image: ImageBitmap) : LatexRenderState()
    data class Error(val message: String, val source: String) : LatexRenderState()
}

/**
 * 语义化的 LaTeX 渲染异常。
 */
class LatexRenderException(message: String) : Exception(message)

fun sanitizeLatex(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""

    var result = trimmed

    // JLatexMath 在 array 环境里会把 \[8pt] 的可选间距当成普通文本渲染，
    // 因此先统一去掉行间距参数，保留换行符 \\。
    result = result.replace(Regex("""\\\\\[.*?]"""), "\\\\\\\\")

    result = convertAlignToArray(result)

    result = result.removeSurrounding("\\[", "\\]")

    if (result.startsWith("$") ||
        result.startsWith("\\begin{") ||
        result.startsWith("\\(")) {
        return result
    }

    return "$$${result}$$"
}

private fun convertAlignToArray(input: String): String {
    if (!input.contains("\\begin{align")) return input
    return input
        .replace("\\begin{align*}", "\\begin{array}{rl}")
        .replace("\\end{align*}", "\\end{array}")
        .replace("\\begin{align}", "\\begin{array}{rl}")
        .replace("\\end{align}", "\\end{array}")
        .replace(Regex("""\\\\\[.*?]"""), "\\\\\\\\")
}

/**
 * 轻量级全局 LaTeX 渲染结果缓存。
 */
class LatexRenderCache private constructor() {

    private val cache = LruCache<String, ImageBitmap>(MAX_CACHE_ENTRIES)

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }

    companion object {
        private const val MAX_CACHE_ENTRIES = 100

        @Volatile
        private var instance: LatexRenderCache? = null

        fun getInstance(): LatexRenderCache {
            return instance ?: synchronized(this) {
                instance ?: LatexRenderCache().also { instance = it }
            }
        }
    }
}

/**
 * 同步将 LaTeX 字符串渲染为 [ImageBitmap]。
 *
 * @param latex 原始 LaTeX 源码
 * @param textSize 期望字号，单位 px
 * @param color 公式前景色，ARGB
 */
private fun renderLatexSync(
    latex: String,
    textSize: Float,
    color: Int
): Result<ImageBitmap> {
    return try {
        val drawable = JLatexMathDrawable.builder(latex)
            .textSize(textSize)
            .color(color)
            .align(JLatexMathDrawable.ALIGN_CENTER)
            .padding(0)
            .build()

        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)

        // 如果 Drawable 已经能够直接生成 Bitmap，优先复用；否则自建 Canvas 绘制。
        val bitmap: Bitmap = drawable.toBitmap(width, height, Bitmap.Config.ARGB_8888)
        Result.success(bitmap.asImageBitmap())
    } catch (e: Exception) {
        val message = when (e) {
            is org.scilab.forge.jlatexmath.JMathTeXException ->
                "LaTeX 语法错误：${e.localizedMessage ?: "无法解析公式"}"

            is IllegalArgumentException ->
                e.localizedMessage ?: "非法参数"

            else -> "渲染失败：${e.localizedMessage ?: e.javaClass.simpleName}"
        }
        Result.failure(LatexRenderException(message))
    }
}

/**
 * 异步将 LaTeX 字符串渲染为 [ImageBitmap]。
 *
 * @param latex 原始 LaTeX 源码
 * @param textSize 期望字号，单位 px
 * @param color 公式前景色，ARGB
 */
suspend fun renderLatex(
    latex: String,
    textSize: Float,
    color: Int
): Result<ImageBitmap> = withContext(Dispatchers.Default) {
    if (latex.isBlank()) {
        return@withContext Result.failure(LatexRenderException("LaTeX 输入为空"))
    }
    renderLatexSync(latex, textSize, color)
}

/**
 *  remember 一个 LaTeX 渲染状态，内部自动缓存最近结果。
 *
 * @param latex 原始 LaTeX 源码
 * @param textSizeSp 期望字号，单位 sp
 * @param color 公式前景色，默认跟随主题 onSurface
 */
@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun rememberLatexImage(
    latex: String,
    textSizeSp: Float = 18f,
    color: Color = MaterialTheme.colorScheme.onSurface
): LatexRenderState {
    val density = LocalDensity.current
    val cache = remember { LatexRenderCache.getInstance() }

    val textSizePx = with(density) { textSizeSp.sp.toPx() }
    val colorInt = color.toArgb()
    val sanitized = remember(latex) { sanitizeLatex(latex) }
    val cacheKey = "$sanitized|$textSizePx|$colorInt"

    return produceState<LatexRenderState>(
        initialValue = LatexRenderState.Loading,
        key1 = cacheKey
    ) {
        val cached = cache.get(cacheKey)
        if (cached != null) {
            value = LatexRenderState.Success(cached)
            return@produceState
        }

        value = LatexRenderState.Loading
        val result = renderLatex(sanitized, textSizePx, colorInt)
        value = result.fold(
            onSuccess = { image ->
                cache.put(cacheKey, image)
                LatexRenderState.Success(image)
            },
            onFailure = { error ->
                LatexRenderState.Error(error.message ?: "渲染失败", latex)
            }
        )
    }.value
}

/**
 * 用于快速在 Composable 中显示 LaTeX 渲染结果的帮助组件。
 *
 * 根据 [state] 自动展示加载、成功或错误状态，并保持居中对齐。
 */
@Composable
fun LatexImage(
    state: LatexRenderState,
    modifier: Modifier = Modifier,
    contentDescription: String = "LaTeX 公式"
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (state) {
            is LatexRenderState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }

            is LatexRenderState.Success -> {
                Image(
                    bitmap = state.image,
                    contentDescription = contentDescription
                )
            }

            is LatexRenderState.Error -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "公式渲染失败",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.source,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
