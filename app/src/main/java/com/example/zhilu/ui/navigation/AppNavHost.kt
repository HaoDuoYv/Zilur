package com.example.zhilu.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.zhilu.ui.camera.CameraScreen
import com.example.zhilu.ui.explore.ExploreScreen
import com.example.zhilu.ui.home.HomeScreen
import com.example.zhilu.ui.note.NoteEditScreen
import com.example.zhilu.ui.reminder.ReminderCenterScreen
import com.example.zhilu.ui.settings.SettingsScreen
import com.example.zhilu.ui.tag.TagsScreen
import com.example.zhilu.ui.trash.TrashScreen
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.MotionEasing
import com.example.zhilu.ui.theme.motionEnterTween
import com.example.zhilu.ui.theme.motionExitTween

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = Destination.Home.path
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(
            route = Destination.Home.path,
            enterTransition = { bottomNavEnter() },
            exitTransition = { bottomNavExit() },
            popEnterTransition = { bottomNavEnter() },
            popExitTransition = { bottomNavExit() }
        ) {
            HomeScreen(navController = navController)
        }
        composable(
            route = Destination.Tags.path,
            enterTransition = { bottomNavEnter() },
            exitTransition = { bottomNavExit() },
            popEnterTransition = { bottomNavEnter() },
            popExitTransition = { bottomNavExit() }
        ) {
            TagsScreen(navController = navController)
        }
        composable(
            route = Destination.Explore.path,
            enterTransition = { bottomNavEnter() },
            exitTransition = { bottomNavExit() },
            popEnterTransition = { bottomNavEnter() },
            popExitTransition = { bottomNavExit() }
        ) {
            ExploreScreen(navController = navController)
        }
        composable(
            route = Destination.Settings.path,
            enterTransition = { bottomNavEnter() },
            exitTransition = { bottomNavExit() },
            popEnterTransition = { bottomNavEnter() },
            popExitTransition = { bottomNavExit() }
        ) {
            SettingsScreen(navController = navController)
        }
        composable(Destination.Camera.path) {
            CameraScreen(navController = navController)
        }
        composable(Destination.Trash.path) {
            TrashScreen(navController = navController)
        }
        composable(Destination.Reminders.path) {
            ReminderCenterScreen(navController = navController)
        }
        composable(
            route = Destination.NoteEdit.path,
            arguments = listOf(navArgument(Destination.NoteEdit.ARG_NOTE_ID) { type = NavType.LongType }),
            enterTransition = {
                slideInHorizontally(
                    motionEnterTween(MotionDuration.Long, easing = MotionEasing.EaseOutCubic)
                ) { it } + fadeIn(
                    motionEnterTween(MotionDuration.Medium, easing = MotionEasing.EaseOutCubic)
                )
            },
            exitTransition = {
                slideOutVertically(
                    motionExitTween(MotionDuration.Medium, easing = MotionEasing.EaseOutCubic)
                ) { it } + fadeOut(
                    motionExitTween(MotionDuration.Medium, easing = MotionEasing.EaseOutCubic)
                )
            },
            popEnterTransition = {
                fadeIn(motionEnterTween(MotionDuration.Medium, easing = MotionEasing.EaseOutCubic))
            },
            popExitTransition = {
                slideOutVertically(
                    motionExitTween(MotionDuration.Medium, easing = MotionEasing.EaseOutCubic)
                ) { it } + fadeOut(
                    motionExitTween(MotionDuration.Medium, easing = MotionEasing.EaseOutCubic)
                )
            }
        ) { entry ->
            NoteEditScreen(
                navController = navController,
                noteId = entry.arguments?.getLong(Destination.NoteEdit.ARG_NOTE_ID) ?: 0L
            )
        }
    }
}

private fun bottomNavEnter() =
    fadeIn(motionEnterTween(MotionDuration.Medium, easing = MotionEasing.EaseOutCubic))

private fun bottomNavExit() =
    fadeOut(motionExitTween(MotionDuration.Medium, easing = MotionEasing.EaseOutCubic))
