package com.example.zhilu.ui.navigation

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

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String = Destination.Home.path
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Destination.Home.path) {
            HomeScreen(navController = navController)
        }
        composable(Destination.Tags.path) {
            TagsScreen(navController = navController)
        }
        composable(Destination.Explore.path) {
            ExploreScreen(navController = navController)
        }
        composable(Destination.Settings.path) {
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
            arguments = listOf(navArgument(Destination.NoteEdit.ARG_NOTE_ID) { type = NavType.LongType })
        ) { entry ->
            NoteEditScreen(
                navController = navController,
                noteId = entry.arguments?.getLong(Destination.NoteEdit.ARG_NOTE_ID) ?: 0L
            )
        }
    }
}
