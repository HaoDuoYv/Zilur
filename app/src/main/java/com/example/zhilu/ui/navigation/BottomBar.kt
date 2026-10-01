package com.example.zhilu.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.zhilu.ui.component.ZhiLuDivider
import com.example.zhilu.ui.theme.motionSpring

/** 底部导航项：选中 / 未选中用成对图标表达，比单一色值更容易一眼分辨。 */
private data class BottomTab(
    val destination: Destination,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

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

@Composable
fun BottomBar(navController: NavHostController) {
    val tabs = bottomTabs()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Column {
        ZhiLuDivider()
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            tabs.forEach { tab ->
                val selected = currentDestination?.hierarchy
                    ?.any { it.route == tab.destination.path } == true
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.96f,
                    animationSpec = motionSpring(),
                    label = "bottom_bar_item_scale"
                )

                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        navController.navigate(tab.destination.path) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            modifier = Modifier.graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    }
}