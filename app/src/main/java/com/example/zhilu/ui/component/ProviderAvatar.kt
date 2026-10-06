package com.example.zhilu.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.inkOnFill
import com.example.zhilu.ui.theme.palettePaint
import com.example.zhilu.ui.theme.shade

/**
 * 供应商图标块：圆角方块 + 品牌色底 + 一个字的标记。
 *
 * 为什么用"一个字"而不是真 logo：各家 logo 是注册商标，塞进 app 有法律风险，
 * 而且要随包分发一堆矢量资源。取首字（GPT → G、通义千问 → 通、DeepSeek → D）
 * 在列表里已经足够把几行区分开 —— 这也是多数聚合客户端的做法。
 *
 * 品牌色取**各家自己的主色**（见 [brandColor]），不是主题色：
 * 这个方块的职责就是"让人一眼认出是哪家"，染成主题色反而失去了信息。
 */
@Composable
fun ProviderAvatar(
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val paint = palettePaint(LocalThemePalette.current)
    val bordered = paint.componentBorder != Color.Unspecified
    val brand = brandColor(label)
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(brand)
            .then(
                // 动森靠描边立形状，纸墨不加 —— 与卡片/按钮同一套判据
                if (bordered) Modifier.border(2.dp, shade(brand).copy(alpha = 0.5f), shape) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = providerInitial(label),
            style = MaterialTheme.typography.titleMedium,
            color = inkOnFill(brand),
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 取一个字的标记。
 *
 * 顺序有讲究：**先看有没有英文/数字**再退回汉字 —— 「OpenAI」要取 `O` 而不是把整串
 * 塞进方块；「通义千问」没有 ASCII，才取首字「通」。
 */
internal fun providerInitial(label: String): String {
    val trimmed = label.trim()
    if (trimmed.isEmpty()) return "?"
    val ascii = trimmed.firstOrNull { it.isLetterOrDigit() && it.code < 128 }
    if (ascii != null) return ascii.uppercaseChar().toString()
    return trimmed.take(1)
}

/**
 * 供应商品牌色。
 *
 * 认不出来的一律回落到主题主色 —— 与其编一个颜色，不如让它跟当前外观一致。
 * 这里的色值是各家的公开主色，只用于图标底（不需要承担正文对比度，
 * 字色由 [inkOnFill] 按亮度自动选深浅）。
 */
@Composable
internal fun brandColor(label: String): Color {
    val lower = label.lowercase()
    val brand = when {
        "openai" in lower || lower.startsWith("gpt") -> Color(0xFF10A37F)
        "deepseek" in lower -> Color(0xFF4D6BFE)
        "通义" in lower || "qwen" in lower || "阿里" in lower -> Color(0xFF615CED)
        "智谱" in lower || "glm" in lower -> Color(0xFF3859FF)
        "moonshot" in lower || "kimi" in lower -> Color(0xFF1F1F1F)
        "openrouter" in lower -> Color(0xFF6467F2)
        "硅基" in lower || "siliconflow" in lower -> Color(0xFF6D5AE6)
        "ollama" in lower -> Color(0xFF3A3A3A)
        "gemini" in lower || "google" in lower -> Color(0xFF4285F4)
        "claude" in lower || "anthropic" in lower -> Color(0xFFD97757)
        "月之暗面" in lower -> Color(0xFF1F1F1F)
        else -> Color.Unspecified
    }
    return if (brand == Color.Unspecified) MaterialTheme.colorScheme.primary else brand
}
