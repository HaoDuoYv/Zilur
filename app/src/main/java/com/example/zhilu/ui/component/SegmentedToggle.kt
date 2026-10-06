package com.example.zhilu.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.palettePaint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.ZhiLuType

/** 分段可视高度；容器再各留 [ContainerPadding]，整体 44dp。 */
private val SegmentHeight = 40.dp

/** 容器内边距，也就是选中色块与容器边之间露出的那一圈。 */
private val ContainerPadding = 2.dp

/** 段内左右留白。 */
private val SegmentPadding = 16.dp

/**
 * 通用分段控件（胶囊容器 + 白底选中项）。
 * 用于首页「列表 / 时间线」、设置「主题模式」、提醒中心「待处理 / 已逾期 / 已完成」。
 *
 * ## 为什么不用 M3 的 `Surface(onClick = …)` 承载每个分段
 *
 * `Surface(onClick)` 会把 `minimumInteractiveComponentSize()` 套在**背景之上**：
 * 布局盒被撑到 48dp，而背景仍按内容自然尺寸（约 27dp）居中绘制。
 * 结果是选中色块浮在容器中间、上下各空出一截——即「选中框没有铺满」。
 *
 * 因此这里把背景画在**段槽自身**上（[SegmentHeight] 定高 + `fillMaxHeight` 语义），
 * 触控目标改由外层 [minimumInteractiveComponentSize] 兜住：
 * 视觉 = 段槽大小，触控 = 至少 48dp，两者不再互相牵制。
 */
@Composable
fun <T> SegmentedToggle(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val paint = palettePaint(LocalThemePalette.current)
    val border = paint.componentBorder
    val bordered = border != Color.Unspecified
    Box(
        modifier = modifier.minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            // 动森：分段控件也描边（参考仓库 `Tabs` 是「圆角 + 2dp 描边 + 底边线」）。
            // 纸墨不加 —— 那里靠底色深浅分清容器与选中项就够了。
            border = if (bordered) BorderStroke(2.dp, border) else null
        ) {
            Row(
                modifier = Modifier
                    .padding(ContainerPadding)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.forEach { (value, label) ->
                    val isSelected = value == selected
                    val background by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            Color.Transparent
                        },
                        label = "segmented_bg"
                    )

                    Box(
                        modifier = Modifier
                            .height(SegmentHeight)
                            .clip(CircleShape)
                            .background(background)
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(value) }
                            )
                            .padding(horizontal = SegmentPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = ZhiLuType.chip,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}
