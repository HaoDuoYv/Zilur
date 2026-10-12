package com.example.zhilu

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.data.repository.NoteSearchIndexer
import com.example.zhilu.reminder.ReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class ZhiLuApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var userPreferences: UserPreferences

    @Inject
    lateinit var searchIndexer: NoteSearchIndexer

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        applicationScope.launch {
            reminderScheduler.setEnabled(userPreferences.remindersEnabled.first())
        }
        // 检索索引兜底：行数不一致（冷迁移建空表、某次写路径中途失败）就全量重建。
        // 异步且吞异常（reconcile 内部处理），不阻塞启动。
        applicationScope.launch(Dispatchers.IO) {
            searchIndexer.reconcile()
        }
    }
}
