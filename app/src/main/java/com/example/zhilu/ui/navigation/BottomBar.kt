package com.example.zhilu.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.zhilu.ui.component.ZhiLuDivider
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.motionSpring

/** 底部导航项：选中 / 未选中用成对图标表达，比单一色值更容易一眼分辨。 */
private data class BottomTab(
    val destination: Destination,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

/** 导航条可视高度（不含系统手势条内边距）。 */
private val BarHeight = 72.dp

/** 选中指示胶囊：把「图标 + 文字」整体包住，所以是竖排的一颗药丸。 */
private val IndicatorHorizontalPadding = 16.dp
private val IndicatorVerticalPadding = 6.dp
private val IconSize = 22.dp
private val IconLabelGap = 3.dp

private fun bottomTabs(): List<BottomTab> = listOf(
    BottomTab(
        destination = Destination.Home,
        label = "笔记",
        selectedIcon = Icons.AutoMirrored.Filled.Article,
        unselectedIcon = Icons.AutoMirrored.Outlined.Article
    ),
    BottomTab(
        destination = Destination.Tags,
        label = "标签",
        selectedIcon = Icons.AutoMirrored.Filled.Label,
        unselectedIcon = Icons.AutoMirrored.Outlined.Label
    ),
    BottomTab(
        destination = Destination.Assistant,
        label = "助手",
        selectedIcon = Icons.Default.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
    ),
    BottomTab(
        destination = Destination.Settings,
        label = "我的",
        selectedIcon = Icons.Default.Person,
        unselectedIcon = Icons.Outlined.Person
    )
)

/**
 * 底部导航。
 *
 * 选中态由**一整颗胶囊**表达，并且把图标与文字一起包进去——指示器不只在图标上出现，
 * 文字同样随选中态变化（颜色从 `onSurfaceVariant` 过渡到 `primary`，字重加粗），
 * 于是「选中」这个信息在整项上都读得出来，而不是只有图标那一小块。
 */
@Composable
fun BottomBar(navController: NavHostController) {
    val tabs = bottomTabs()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column {
        ZhiLuDivider()
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = bottomInset)
                    .height(BarHeight)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == tab.destination.path } == true

                    BottomTabItem(
                        tab = tab,
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.destination.path) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.BottomTabItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
        label = "bottom_bar_indicator"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "bottom_bar_content"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = motionSpring(),
        label = "bottom_bar_item_scale"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(CircleShape)
                .background(indicatorColor)
                .padding(
                    horizontal = IndicatorHorizontalPadding,
                    vertical = IndicatorVerticalPadding
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(IconSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
            )
            Spacer(modifier = Modifier.height(IconLabelGap))
            // 文字参与选中态：与图标同一份 contentColor，一起做颜色过渡；
            // 字重只做离散切换（FontWeight 没法插值），配合颜色渐变足够读出来。
            Text(
                text = tab.label,
                style = ZhiLuType.label,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
