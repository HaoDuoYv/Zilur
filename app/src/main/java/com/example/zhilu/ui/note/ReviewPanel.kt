package com.example.zhilu.ui.note

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.ReviewPlan
import com.example.zhilu.domain.model.ReviewRating

@Composable
fun ReviewPanel(
    plan: ReviewPlan?,
    isDue: Boolean,
    isRecording: Boolean,
    onStart: () -> Unit,
    onDisable: () -> Unit,
    onRate: (ReviewRating) -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("复习计划", style = MaterialTheme.typography.titleMedium)
            if (plan == null || !plan.enabled) {
                Button(onClick = onStart) { Text("开启复习") }
            } else {
                Text("下一次复习：${formatReminderTime(plan.nextReviewAt)}")
                if (isDue) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { onRate(ReviewRating.HARD) },
                            enabled = !isRecording
                        ) { Text("困难") }
                        TextButton(
                            onClick = { onRate(ReviewRating.NORMAL) },
                            enabled = !isRecording
                        ) { Text("一般") }
                        Button(
                            onClick = { onRate(ReviewRating.MASTERED) },
                            enabled = !isRecording
                        ) { Text("掌握") }
                    }
                }
                TextButton(onClick = onDisable) { Text("关闭复习") }
            }
        }
    }
}
