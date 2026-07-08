package com.example.zhilu.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable

@Composable
fun AppFAB(
    contentDescription: String,
    onClick: () -> Unit
) {
    FloatingActionButton(onClick = onClick) {
        Icon(Icons.Default.Add, contentDescription = contentDescription)
    }
}
