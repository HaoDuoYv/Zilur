package com.example.zhilu.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun BottomBar(navController: NavHostController) {
    val items = listOf(
        Destination.Home to "首页",
        Destination.Tags to "标签",
        Destination.Explore to "探索",
        Destination.Settings to "设置"
    )

    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        items.forEach { (destination, label) ->
            NavigationBarItem(
                selected = currentDestination?.hierarchy?.any { it.route == destination.path } == true,
                onClick = {
                    navController.navigate(destination.path) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = when (destination) {
                            Destination.Home -> Icons.Default.Home
                            Destination.Tags -> Icons.Default.Label
                            Destination.Explore -> Icons.Default.Explore
                            Destination.Settings -> Icons.Default.Settings
                            else -> Icons.Default.Home
                        },
                        contentDescription = label
                    )
                },
                label = { Text(label) }
            )
        }
    }
}
