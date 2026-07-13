package com.example.zhilu.di

import android.content.Context
import androidx.room.Room
import com.example.zhilu.data.local.dao.MediaDao
import com.example.zhilu.data.local.dao.NoteBlockDao
import com.example.zhilu.data.local.dao.NoteCardDao
import com.example.zhilu.data.local.dao.NoteDao
import com.example.zhilu.data.local.dao.ReminderDao
import com.example.zhilu.data.local.dao.ReviewDao
import com.example.zhilu.data.local.dao.TagDao
import com.example.zhilu.data.local.dao.TodoDao
import com.example.zhilu.data.local.database.AppDatabase
import com.example.zhilu.data.local.file.MediaFileManager
import com.example.zhilu.data.local.database.Migration
import com.example.zhilu.data.repository.MediaRepositoryImpl
import com.example.zhilu.data.repository.NoteRepositoryImpl
import com.example.zhilu.data.repository.ReminderRepositoryImpl
import com.example.zhilu.data.repository.ReviewRepositoryImpl
import com.example.zhilu.data.repository.RoomRepositoryTransactionRunner
import com.example.zhilu.data.repository.TagRepositoryImpl
import com.example.zhilu.data.repository.TodoRepositoryImpl
import com.example.zhilu.domain.reminder.ReviewSchedulePolicy
import com.example.zhilu.domain.repository.MediaRepository
import com.example.zhilu.domain.repository.NoteRepository
import com.example.zhilu.domain.repository.ReminderRepository
import com.example.zhilu.domain.repository.ReviewRepository
import com.example.zhilu.domain.repository.TagRepository
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
    fun provideMediaDao(database: AppDatabase): MediaDao = database.mediaDao()

    @Provides
    fun provideReviewDao(database: AppDatabase): ReviewDao = database.reviewDao()

    @Provides
    fun provideTodoDao(database: AppDatabase): TodoDao = database.todoDao()

    @Provides
    fun provideReminderDao(database: AppDatabase): ReminderDao = database.reminderDao()

    @Provides
    @Singleton
    fun provideNoteRepository(
        database: AppDatabase,
        noteDao: NoteDao,
        noteBlockDao: NoteBlockDao,
        noteCardDao: NoteCardDao,
        tagDao: TagDao
    ): NoteRepository = NoteRepositoryImpl(database, noteDao, noteBlockDao, noteCardDao, tagDao)

    @Provides
    @Singleton
    fun provideTagRepository(tagDao: TagDao): TagRepository = TagRepositoryImpl(tagDao)

    @Provides
    @Singleton
    fun provideMediaRepository(mediaDao: MediaDao): MediaRepository = MediaRepositoryImpl(mediaDao)

    @Provides
    @Singleton
    fun provideReviewRepository(
        database: AppDatabase,
        reviewDao: ReviewDao
    ): ReviewRepository = ReviewRepositoryImpl(
        reviewDao = reviewDao,
        schedulePolicy = ReviewSchedulePolicy(),
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
}
