package com.example.zhilu.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.zhilu.ui.assistant.AssistantScreen
import com.example.zhilu.ui.camera.CameraScreen
import com.example.zhilu.ui.home.HomeScreen
import com.example.zhilu.ui.note.NoteEditScreen
import com.example.zhilu.ui.reminder.ReminderCenterScreen
import com.example.zhilu.ui.settings.AppearanceScreen
import com.example.zhilu.ui.settings.AiConfigScreen
import com.example.zhilu.ui.settings.SettingsScreen
import com.example.zhilu.ui.tag.TagsScreen
import com.example.zhilu.ui.trash.TrashScreen
import com.example.zhilu.ui.theme.LocalReducedMotion
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.MotionEasing
import com.example.zhilu.ui.theme.motionEnterTween
import com.example.zhilu.ui.theme.motionExitTween

/**
 * 应用级导航转场。统一为「水平滑动 push/pop」语言：
 * - 底部导航平级切换：1/4 屏宽轻微滑动 + 交叉淡入淡出（避免纯淡入淡出露背景闪烁）。
 * - 层级子页面（编辑 / 回收站 / 提醒 / 相机）：全宽水平滑动，进右出、退左回。
 * 所有转场保留 enabled 门控，系统开启「移除动画」时降级为 0ms（无障碍）。
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = Destination.Home.path,
    modifier: Modifier = Modifier
) {
    val motion = !LocalReducedMotion.current
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(
            route = Destination.Home.path,
            enterTransition = { bottomNavEnter(motion) },
            exitTransition = { bottomNavExit(motion) },
            popEnterTransition = { bottomNavPopEnter(motion) },
            popExitTransition = { bottomNavPopExit(motion) }
        ) {
            HomeScreen(navController = navController)
        }
        composable(
            route = Destination.Tags.path,
            enterTransition = { bottomNavEnter(motion) },
            exitTransition = { bottomNavExit(motion) },
            popEnterTransition = { bottomNavPopEnter(motion) },
            popExitTransition = { bottomNavPopExit(motion) }
        ) {
            TagsScreen(navController = navController)
        }
        composable(
            route = Destination.Settings.path,
            enterTransition = { bottomNavEnter(motion) },
            exitTransition = { bottomNavExit(motion) },
            popEnterTransition = { bottomNavPopEnter(motion) },
            popExitTransition = { bottomNavPopExit(motion) }
        ) {
            SettingsScreen(navController = navController)
        }
        composable(
            route = Destination.Assistant.path,
            enterTransition = { bottomNavEnter(motion) },
            exitTransition = { bottomNavExit(motion) },
            popEnterTransition = { bottomNavPopEnter(motion) },
            popExitTransition = { bottomNavPopExit(motion) }
        ) {
            AssistantScreen(navController = navController)
        }
        composable(
            route = Destination.Camera.path,
            enterTransition = { forwardEnter(motion) },
            exitTransition = { forwardExit(motion) },
            popEnterTransition = { backEnter(motion) },
            popExitTransition = { backExit(motion) }
        ) {
            CameraScreen(navController = navController)
        }
        composable(
            route = Destination.Trash.path,
            enterTransition = { forwardEnter(motion) },
            exitTransition = { forwardExit(motion) },
            popEnterTransition = { backEnter(motion) },
            popExitTransition = { backExit(motion) }
        ) {
            TrashScreen(navController = navController)
        }
        composable(
            route = Destination.Reminders.path,
            enterTransition = { forwardEnter(motion) },
            exitTransition = { forwardExit(motion) },
            popEnterTransition = { backEnter(motion) },
            popExitTransition = { backExit(motion) }
        ) {
            ReminderCenterScreen(navController = navController)
        }
        composable(
            route = Destination.Appearance.path,
            enterTransition = { forwardEnter(motion) },
            exitTransition = { forwardExit(motion) },
            popEnterTransition = { backEnter(motion) },
            popExitTransition = { backExit(motion) }
        ) {
            AppearanceScreen(navController = navController)
        }
        composable(
            route = Destination.AiConfig.path,
            enterTransition = { forwardEnter(motion) },
            exitTransition = { forwardExit(motion) },
            popEnterTransition = { backEnter(motion) },
            popExitTransition = { backExit(motion) }
        ) {
            AiConfigScreen(navController = navController)
        }
        composable(
            route = Destination.NoteEdit.path,
            arguments = listOf(navArgument(Destination.NoteEdit.ARG_NOTE_ID) { type = NavType.LongType }),
            enterTransition = { forwardEnter(motion) },
            exitTransition = { forwardExit(motion) },
            popEnterTransition = { backEnter(motion) },
            popExitTransition = { backExit(motion) }
        ) {
            NoteEditScreen(navController = navController)
        }
    }
}

// ---- 底部导航平级切换：1/4 屏宽轻微滑动 + 交叉淡入淡出 ----

private fun bottomNavEnter(enabled: Boolean) =
    slideInHorizontally(
        animationSpec = motionEnterTween(
            MotionDuration.Medium,
            easing = MotionEasing.Enter,
            enabled = enabled
        )
    ) { it / 4 } + fadeIn(
        motionEnterTween(
            MotionDuration.Medium,
            easing = MotionEasing.Enter,
            enabled = enabled
        )
    )

private fun bottomNavExit(enabled: Boolean) =
    slideOutHorizontally(
        animationSpec = motionExitTween(
            MotionDuration.Medium,
            easing = MotionEasing.Exit,
            enabled = enabled
        )
    ) { -it / 4 } + fadeOut(
        motionExitTween(
            MotionDuration.Medium,
            easing = MotionEasing.Exit,
            enabled = enabled
        )
    )

private fun bottomNavPopEnter(enabled: Boolean) =
    slideInHorizontally(
        animationSpec = motionEnterTween(
            MotionDuration.Medium,
            easing = MotionEasing.Enter,
            enabled = enabled
        )
    ) { -it / 4 } + fadeIn(
        motionEnterTween(
            MotionDuration.Medium,
            easing = MotionEasing.Enter,
            enabled = enabled
        )
    )

private fun bottomNavPopExit(enabled: Boolean) =
    slideOutHorizontally(
        animationSpec = motionExitTween(
            MotionDuration.Medium,
            easing = MotionEasing.Exit,
            enabled = enabled
        )
    ) { it / 4 } + fadeOut(
        motionExitTween(
            MotionDuration.Medium,
            easing = MotionEasing.Exit,
            enabled = enabled
        )
    )

// ---- 层级子页面：全宽水平 push/pop ----

private fun forwardEnter(enabled: Boolean) =
    slideInHorizontally(
        animationSpec = motionEnterTween(
            MotionDuration.Long,
            easing = MotionEasing.Enter,
            enabled = enabled
        )
    ) { it } + fadeIn(
        motionEnterTween(
            MotionDuration.Long,
            easing = MotionEasing.Enter,
            enabled = enabled
        )
    )

private fun forwardExit(enabled: Boolean) =
    slideOutHorizontally(
        animationSpec = motionExitTween(
            MotionDuration.Long,
            easing = MotionEasing.Exit,
            enabled = enabled
        )
    ) { -it / 3 } + fadeOut(
        motionExitTween(
            MotionDuration.Long,
            easing = MotionEasing.Exit,
            enabled = enabled
        )
    )

private fun backEnter(enabled: Boolean) =
    slideInHorizontally(
        animationSpec = motionEnterTween(
            MotionDuration.Long,
            easing = MotionEasing.Enter,
            enabled = enabled
        )
    ) { -it / 3 } + fadeIn(
        motionEnterTween(
            MotionDuration.Long,
            easing = MotionEasing.Enter,
            enabled = enabled
        )
    )

private fun backExit(enabled: Boolean) =
    slideOutHorizontally(
        animationSpec = motionExitTween(
            MotionDuration.Long,
            easing = MotionEasing.Exit,
            enabled = enabled
        )
    ) { it } + fadeOut(
        motionExitTween(
            MotionDuration.Long,
            easing = MotionEasing.Exit,
            enabled = enabled
        )
    )
