package com.example.zhilu.ui.review

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.palettePaint

/** 子标签可视高度；容器再各留 [ContainerPadding]。 */
private val SubTabHeight = 34.dp

/** 容器内边距（选中色块与容器边之间露出的一圈）。 */
private val ContainerPadding = 3.dp

/**
 * 复习中心的二级子标签（待办「待处理 / 已逾期 / 已完成」、提醒的三档筛选）。
 *
 * 与一级的 [com.example.zhilu.ui.component.SegmentedToggle] 刻意做出层级差：
 * 一级是「内容宽度 + 白底选中块」的大分段，二级是**等宽铺满**的矮胶囊
 * （产品原型里 `.sub-tabs` 就是 `flex:1` 等分）—— 两级长得一样就没有层级了。
 *
 * 容器走 `surfaceVariant`，选中项浮白（纸墨）或浮描边（动森，与卡片同材质逻辑）；
 * 颜色全部是 md3 语义角色，两套外观 × 明暗四象限自适应。
 */
@Composable
fun <T> ReviewSubTabs(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val paint = palettePaint(LocalThemePalette.current)
    val border = paint.componentBorder
    val bordered = border != Color.Unspecified

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (bordered) BorderStroke(2.dp, border) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ContainerPadding)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            options.forEachIndexed { index, (value, label) ->
                val isSelected = value == selected
                val background by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    label = "sub_tab_bg"
                )
                if (index > 0) Spacer(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(SubTabHeight)
                        .then(
                            if (isSelected) {
                                Modifier.shadow(elevation = 1.dp, shape = CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .clip(CircleShape)
                        .background(background)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(value) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = ZhiLuType.label,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                }
            }
        }
    }
}
