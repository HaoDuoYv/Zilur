package com.example.zhilu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.zhilu.ai.AiTaskManager
import com.example.zhilu.ai.AiTaskNotifications
import com.example.zhilu.common.AppForegroundTracker
import com.example.zhilu.data.datastore.AccentColor
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

    @Inject
    lateinit var aiTaskManager: AiTaskManager

    @Inject
    lateinit var foregroundTracker: AppForegroundTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val initialRoute = routeFromIntent(intent) ?: Destination.Home.path
        setContent {
            val themeMode by userPreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val accentColor by userPreferences.accentColor
                .collectAsState(initial = AccentColor.DEFAULT)
            val controller = rememberNavController()
            navController = controller
            ZhiLuTheme(themeMode = themeMode, accentColor = accentColor) {
                AppShell(
                    navController = controller,
                    startDestination = initialRoute,
                    aiTaskManager = aiTaskManager
                )
            }
        }
    }

    // 单 Activity 应用，用它的 started/stopped 作为前后台判据（见 AppForegroundTracker）。
    override fun onStart() {
        super.onStart()
        foregroundTracker.onActivityStarted()
    }

    override fun onStop() {
        super.onStop()
        foregroundTracker.onActivityStopped()
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
            // AI 任务通知点击：直接回到助手页看结果 / 继续对话。
            intent.getBooleanExtra(AiTaskNotifications.EXTRA_OPEN_ASSISTANT, false) -> Destination.Assistant.path
            else -> null
        }
    }
}
