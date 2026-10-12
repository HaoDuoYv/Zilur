package com.example.zhilu.ui.component

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.palettePaint

/**
 * 页面级进度条（不确定态）。
 *
 * 两套外观两种材质：
 * - **纸墨**：M3 原生 [LinearProgressIndicator]，一点没动；
 * - **动森**：胶囊形状 + `componentBorder` 暖褐描边 + 圆头笔帽 —— M3 默认那条
 *   四段小色块是"材料设计"的语言，跟动森那套圆润的实体控件不是一回事。
 *
 * 页面级加载("正在导入/正在读取")用它；卡片内的小尺寸忙碌指示器用
 * [GeneratingIndicator]（动森三球），别混用。
 */
@Composable
fun AppProgressBar(modifier: Modifier = Modifier) {
    val paint = palettePaint(LocalThemePalette.current)
    if (paint.componentBorder == Color.Unspecified) {
        LinearProgressIndicator(modifier = modifier.fillMaxWidth())
        return
    }
    val shape = RoundedCornerShape(percent = 50)
    LinearProgressIndicator(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(2.dp, paint.componentBorder, shape),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round
    )
}
