package com.example.zhilu.ui.component

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 分割线规格：发丝线用于常规分隔，粗线用于强调。 */
object DividerTokens {
    val Hairline = 0.5.dp
    val Bold = 1.dp
}

@Composable
fun ZhiLuDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerTokens.Hairline,
    color: Color = MaterialTheme.colorScheme.outlineVariant
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color
    )
}