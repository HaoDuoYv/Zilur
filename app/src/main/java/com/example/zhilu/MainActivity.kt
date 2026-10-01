package com.example.zhilu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.zhilu.data.datastore.ThemeMode
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.reminder.ReminderNotifier
import com.example.zhilu.ui.navigation.AppShell
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.theme.ZhiLuTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var navController: NavHostController? = null

    @Inject
    lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val initialRoute = routeFromIntent(intent) ?: Destination.Home.path
        setContent {
            val themeMode by userPreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val controller = rememberNavController()
            navController = controller
            ZhiLuTheme(themeMode = themeMode) {
                AppShell(
                    navController = controller,
                    startDestination = initialRoute
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFromIntent(intent)?.let { route ->
            navController?.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    private fun routeFromIntent(intent: android.content.Intent?): String? {
        if (intent == null) return null

        val noteId = if (intent.hasExtra(ReminderNotifier.EXTRA_NOTE_ID)) {
            intent.getLongExtra(ReminderNotifier.EXTRA_NOTE_ID, 0L).takeIf { it > 0L }
        } else {
            null
        }

        return when {
            noteId != null -> Destination.NoteEdit.createRoute(noteId)
            intent.getBooleanExtra(ReminderNotifier.EXTRA_REMINDER_CENTER, false) -> Destination.Reminders.path
            else -> null
        }
    }
}
