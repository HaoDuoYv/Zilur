package com.example.zhilu.ui.navigation

import androidx.navigation.NavHostController

class NavigationActions(private val navController: NavHostController) {
    fun openNewNote() {
        navController.navigate(Destination.NoteEdit.createRoute())
    }

    fun openNote(noteId: Long) {
        navController.navigate(Destination.NoteEdit.createRoute(noteId))
    }

    fun openCamera() {
        navController.navigate(Destination.Camera.path)
    }

    fun openTrash() {
        navController.navigate(Destination.Trash.path)
    }
}
