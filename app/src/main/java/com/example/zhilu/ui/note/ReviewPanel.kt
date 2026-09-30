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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.ui.component.AppCard
import com.example.zhilu.ui.theme.ShapeTokens

private val PillShape = RoundedCornerShape(ShapeTokens.Pill)

@Composable
fun ReviewPanel(
    plan: ReviewPlan?,
    isDue: Boolean,
    isRecording: Boolean,
    onStart: () -> Unit,
    onDisable: () -> Unit,
    onRate: (ReviewRating) -> Unit
) {
    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "复习计划",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (isRecording) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "正在录音识别…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (plan == null || !plan.enabled) {
                OutlinedButton(onClick = onStart, shape = PillShape) { Text("开启复习") }
            } else {
                Text(
                    text = "下一次复习：${formatReminderTime(plan.nextReviewAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isDue) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
