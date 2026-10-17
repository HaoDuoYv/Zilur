package com.example.zhilu.di

import android.content.Context
import androidx.room.Room
import com.example.zhilu.ai.AiTaskHost
import com.example.zhilu.ai.AndroidAiTaskHost
import com.example.zhilu.data.local.dao.AiConversationDao
import com.example.zhilu.data.local.dao.AiMessageDao
import com.example.zhilu.data.local.dao.MediaDao
import com.example.zhilu.data.local.dao.NoteBlockDao
import com.example.zhilu.data.local.dao.NoteCardDao
import com.example.zhilu.data.local.dao.NoteDao
import com.example.zhilu.data.local.dao.NoteFtsDao
import com.example.zhilu.data.local.dao.ReminderDao
import com.example.zhilu.data.local.dao.ReviewDao
import com.example.zhilu.data.local.dao.TagDao
import com.example.zhilu.data.local.dao.TodoDao
import com.example.zhilu.data.local.database.AppDatabase
import com.example.zhilu.data.ai.AiAssistantRepositoryImpl
import com.example.zhilu.data.ai.LlmApiClient
import com.example.zhilu.data.cloud.CloudApiClient
import com.example.zhilu.data.cloud.CloudRepositoryImpl
import com.example.zhilu.data.datastore.CloudPreferences
import com.example.zhilu.data.datastore.UserPreferences
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.data.local.database.Migration
import com.example.zhilu.data.repository.AiConversationRepositoryImpl
import com.example.zhilu.data.repository.MediaRepositoryImpl
import com.example.zhilu.data.repository.NoteRepositoryImpl
import com.example.zhilu.data.repository.NoteSearchIndexer
import com.example.zhilu.data.repository.ReminderRepositoryImpl
import com.example.zhilu.data.repository.ReviewRepositoryImpl
import com.example.zhilu.data.repository.RoomRepositoryTransactionRunner
import com.example.zhilu.data.repository.TagRepositoryImpl
import com.example.zhilu.data.repository.TodoRepositoryImpl
import com.example.zhilu.domain.ai.repository.AiAssistantRepository
import com.example.zhilu.domain.ai.usecase.AiToolExecutor
import com.example.zhilu.domain.repository.AiConversationRepository
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.ReviewRepository
import com.example.zhilu.domain.repository.TagRepository
import com.example.zhilu.domain.repository.CloudRepository
import com.example.zhilu.domain.repository.TodoRepository
import com.example.zhilu.reminder.ReminderScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "zhilu.db")
            .addMigrations(*Migration.all)
            .build()

    @Provides
    fun provideNoteDao(database: AppDatabase): NoteDao = database.noteDao()

    @Provides
    fun provideTagDao(database: AppDatabase): TagDao = database.tagDao()

    @Provides
    fun provideNoteBlockDao(database: AppDatabase): NoteBlockDao = database.noteBlockDao()

    @Provides
    fun provideNoteCardDao(database: AppDatabase): NoteCardDao = database.noteCardDao()

    @Provides
    fun provideNoteFtsDao(database: AppDatabase): NoteFtsDao = database.noteFtsDao()

    @Provides
    fun provideMediaDao(database: AppDatabase): MediaDao = database.mediaDao()

    @Provides
    fun provideReviewDao(database: AppDatabase): ReviewDao = database.reviewDao()

    @Provides
    fun provideTodoDao(database: AppDatabase): TodoDao = database.todoDao()

    @Provides
    fun provideReminderDao(database: AppDatabase): ReminderDao = database.reminderDao()

    @Provides
    fun provideAiConversationDao(database: AppDatabase): AiConversationDao = database.aiConversationDao()

    @Provides
    fun provideAiMessageDao(database: AppDatabase): AiMessageDao = database.aiMessageDao()

    @Provides
    @Singleton
    fun provideNoteRepository(
        database: AppDatabase,
        noteDao: NoteDao,
        noteBlockDao: NoteBlockDao,
        noteCardDao: NoteCardDao,
        tagDao: TagDao,
        searchIndexer: NoteSearchIndexer
    ): NoteRepository = NoteRepositoryImpl(database, noteDao, noteBlockDao, noteCardDao, tagDao, searchIndexer)

    @Provides
    @Singleton
    fun provideTagRepository(
        database: AppDatabase,
        tagDao: TagDao,
        searchIndexer: NoteSearchIndexer
    ): TagRepository = TagRepositoryImpl(
        tagDao = tagDao,
        searchIndexer = searchIndexer,
        transactionRunner = RoomRepositoryTransactionRunner(database)
    )

    @Provides
    @Singleton
    fun provideMediaRepository(mediaDao: MediaDao): MediaRepository = MediaRepositoryImpl(mediaDao)

    @Provides
    @Singleton
    fun provideReviewRepository(
        database: AppDatabase,
        reviewDao: ReviewDao,
        userPreferences: UserPreferences
    ): ReviewRepository = ReviewRepositoryImpl(
        reviewDao = reviewDao,
        // 阶梯不再在构造期固定：仓库每次写操作读**当前**用户配置（见 currentPolicy）
        userPreferences = userPreferences,
        transactionRunner = RoomRepositoryTransactionRunner(database)
    )

    @Provides
    @Singleton
    fun provideTodoRepository(todoDao: TodoDao): TodoRepository = TodoRepositoryImpl(todoDao)

    @Provides
    @Singleton
    fun provideReminderRepository(
        database: AppDatabase,
        reminderDao: ReminderDao,
        reminderScheduler: ReminderScheduler
    ): ReminderRepository = ReminderRepositoryImpl(
        reminderDao = reminderDao,
        transactionRunner = RoomRepositoryTransactionRunner(database),
        reminderCheckScheduler = reminderScheduler::scheduleOneTimeCheck
    )

    @Provides
    @Singleton
    fun provideReminderWorkEnqueuer(
        @ApplicationContext context: Context
    ): ReminderScheduler.WorkEnqueuer = ReminderScheduler.WorkManagerWorkEnqueuer(context)

    @Provides
    @Singleton
    fun provideMediaFileManager(@ApplicationContext context: Context): MediaFileManager =
        MediaFileManager(context)

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences =
        UserPreferences(context)

    @Provides
    @Singleton
    fun provideAiConversationRepository(
        conversationDao: AiConversationDao,
        messageDao: AiMessageDao
    ): AiConversationRepository = AiConversationRepositoryImpl(conversationDao, messageDao)

    @Provides
    @Singleton
    fun provideAiAssistantRepository(
        llmApiClient: LlmApiClient,
        aiToolExecutor: AiToolExecutor,
        mediaFileManager: MediaFileManager
    ): AiAssistantRepository = AiAssistantRepositoryImpl(llmApiClient, aiToolExecutor, mediaFileManager)

    @Provides
    @Singleton
    fun provideAiTaskHost(impl: AndroidAiTaskHost): AiTaskHost = impl

    /**
     * 可选云端服务。**没有地址也能被注入** —— 仓库内部按"未配置"优雅失败，
     * 界面据此显示"还没连接云端"。本地功能不依赖它是否可用。
     */
    @Provides
    @Singleton
    fun provideCloudRepository(
        preferences: CloudPreferences,
        apiClient: CloudApiClient,
        @ApplicationContext context: Context
    ): CloudRepository = CloudRepositoryImpl(preferences, apiClient, context)
}
