package com.example.zhilu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.zhilu.ai.AiPromptHandoff
import com.example.zhilu.ai.AiTaskManager
import com.example.zhilu.ai.AiTaskNotifications
import com.example.zhilu.common.AppForegroundTracker
import com.example.zhilu.data.datastore.AccentColor
import com.example.zhilu.data.datastore.ThemeMode
import com.example.zhilu.data.datastore.ThemePalette
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.reminder.ReminderNotifier
import com.example.zhilu.ui.navigation.AppIntents
import com.example.zhilu.ui.navigation.AppShell
import com.example.zhilu.ui.navigation.Destination
import com.example.zhilu.ui.navigation.navigateToAssistant
import com.example.zhilu.ui.navigation.navigateToReview
import com.example.zhilu.ui.review.ReviewTab
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
    lateinit var promptHandoff: AiPromptHandoff

    @Inject
    lateinit var appIntents: AppIntents

    @Inject
    lateinit var foregroundTracker: AppForegroundTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 冷启动带目标页（点 AI 任务通知进来）时**仍然是 home 起栈**，构图完成后再 push 目标页：
        // 把目标页当 startDestination 会让返回栈根落错地方（从助手页按返回直接退出应用，
        // 而不是回到首页）；而且 startDestination 必须与注册的路由模板逐字一致，
        // 传真实路由串（带参数）也会错。
        // 配置变更重建（savedInstanceState != null）时不再导航：返回栈本身已被恢复，
        // 再导航一次会压出重复页面。
        val navigateFromIntent = savedInstanceState == null
        setContent {
            val themeMode by userPreferences.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val accentColor by userPreferences.accentColor
                .collectAsState(initial = AccentColor.DEFAULT)
            val themePalette by userPreferences.themePalette
                .collectAsState(initial = ThemePalette.DEFAULT)
            val accessibleEmphasis by userPreferences.accessibleEmphasis
                .collectAsState(initial = false)
            val controller = rememberNavController()
            navController = controller
            LaunchedEffect(Unit) {
                if (navigateFromIntent) navigateFrom(intent, controller)
            }
            ZhiLuTheme(
                themeMode = themeMode,
                accentColor = accentColor,
                palette = themePalette,
                accessibleEmphasis = accessibleEmphasis
            ) {
                AppShell(
                    navController = controller,
                    startDestination = Destination.Home.path,
                    aiTaskManager = aiTaskManager,
                    promptHandoff = promptHandoff,
                    appIntents = appIntents
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
        navController?.let { navigateFrom(intent, it) }
    }

    /**
     * 按 intent 内容和路由把用户带到目标页（冷启动与 [onNewIntent] 共用一份映射）。
     *
     * 助手页走 [navigateToAssistant]（与底栏切换同一套选项）：它会**复用/恢复**助手页原有的
     * 导航 entry —— 也就复用了同一个 ViewModel。直接 `navigate("assistant")` 会压入第二个
     * 助手页实例，把任务状态、当前会话和输入草稿全部重置（真机 bug 的第二个入口）。
     */
    private fun navigateFrom(intent: android.content.Intent?, controller: NavHostController) {
        if (intent == null) return

        val noteId = if (intent.hasExtra(ReminderNotifier.EXTRA_NOTE_ID)) {
            intent.getLongExtra(ReminderNotifier.EXTRA_NOTE_ID, 0L).takeIf { it > 0L }
        } else {
            null
        }
        when {
            noteId != null -> controller.navigate(Destination.NoteEdit.createRoute(noteId)) {
                launchSingleTop = true
            }
            intent.getBooleanExtra(ReminderNotifier.EXTRA_REMINDER_CENTER, false) ->
                // 提醒通知 → 复习中心的「提醒」档（顶层页切换 + 一次性档位意图，
                // 而不是旧的提醒中心独立路由：那里已被复习中心吸收）。
                controller.navigateToReview(appIntents, ReviewTab.Reminders)
            // AI 任务通知点击：直接回到助手页看结果 / 继续对话。
            // 走底栏那套切换选项（不是裸 navigate）：复用/恢复原有 entry 与 ViewModel，
            // 结果就是「回到原来那条对话」，而不是一张重新开出来的白纸。
            intent.getBooleanExtra(AiTaskNotifications.EXTRA_OPEN_ASSISTANT, false) ->
                controller.navigateToAssistant()
        }
    }
}
