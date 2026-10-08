package com.example.zhilu.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ShapeTokens
import com.example.zhilu.ui.theme.ZhiLuType

private val PillShape = RoundedCornerShape(ShapeTokens.Pill)

/**
 * 复习面板（底部 sheet）：由顶栏「复习」胶囊唤出，承载开启、评级与关闭，
 * 常态下不占用正文版面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSheet(
    plan: ReviewPlan?,
    isDue: Boolean,
    isRecording: Boolean,
    onStart: () -> Unit,
    onDisable: () -> Unit,
    onRate: (ReviewRating) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.PageGutter)
                .padding(bottom = Spacing.Xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md)
        ) {
            Text(
                text = "复习计划",
                style = ZhiLuType.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isRecording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        // isRecording 只是"写库中"的忙碌标志（本页没有录音功能），
                        // 旧文案「正在录音识别…」会让人以为这里有语音识别。
                        text = "正在保存复习结果…",
                        style = ZhiLuType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (plan == null || !plan.enabled) {
                OutlinedButton(onClick = onStart, shape = PillShape) { Text("开启复习") }
            } else {
                Text(
                    text = "下一次复习：${formatReminderTime(plan.nextReviewAt)}",
                    style = ZhiLuType.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isDue) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { onRate(ReviewRating.HARD) },
                            enabled = !isRecording,
                            shape = PillShape
                        ) { Text("困难") }
                        OutlinedButton(
                            onClick = { onRate(ReviewRating.NORMAL) },
                            enabled = !isRecording,
                            shape = PillShape
                        ) { Text("一般") }
                        Button(
                            onClick = { onRate(ReviewRating.MASTERED) },
                            enabled = !isRecording,
                            shape = PillShape
                        ) { Text("掌握") }
                    }
                }
                TextButton(onClick = onDisable) { Text("关闭复习") }
            }
        }
    }
}