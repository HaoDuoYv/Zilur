package com.example.zhilu.ui.note.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.component.AppButton
import com.example.zhilu.ui.component.AppButtonVariant

/**
 * 「添加新的知识小点」整宽入口。
 *
 * 形状从"卡片槽位"（12dp 圆角 + 自绘边框 + 卡片投影）收敛到 [AppButton] 的 outline 变体：
 * 纸墨是 M3 描边按钮（全胶囊），动森是有厚度的立体键 —— 两者共用同一档"卡片内动作"语义。
 */
@Composable
fun AddKnowledgeCardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppButton(
        onClick = onClick,
        variant = AppButtonVariant.Outline,
        fillWidth = true,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "添加新的知识小点",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
