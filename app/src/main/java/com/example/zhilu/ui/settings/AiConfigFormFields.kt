package com.example.zhilu.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.AiVendorPreset
import com.example.zhilu.ui.component.AppChip

/**
 * 供应商预设胶囊。
 *
 * 选中态只表示"这组端点是从它带出来的"，之后改过端点也不会自动取消选中 ——
 * 那会让标签闪来闪去。它是**填表辅助**，不是绑定关系。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VendorChips(
    selected: String,
    onPick: (AiVendorPreset) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        AiVendorPreset.presets.forEach { preset ->
            AppChip(
                label = preset.label,
                selected = selected == preset.label,
                onClick = { onPick(preset) }
            )
        }
    }
}
