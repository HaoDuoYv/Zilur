package com.example.zhilu.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.zhilu.data.local.dao.MediaDao
import com.example.zhilu.data.local.dao.NoteBlockDao
import com.example.zhilu.data.local.dao.NoteDao
import com.example.zhilu.data.local.dao.ReminderDao
import com.example.zhilu.data.local.dao.ReviewDao
import com.example.zhilu.data.local.dao.TagDao
import com.example.zhilu.data.local.dao.TodoDao
import com.example.zhilu.data.local.entity.MediaEntity
import com.example.zhilu.data.local.entity.NoteBlockEntity
import com.example.zhilu.data.local.entity.NoteEntity
import com.example.zhilu.data.local.entity.NoteTagEntity
import com.example.zhilu.data.local.entity.ReminderInstanceEntity
import com.example.zhilu.data.local.entity.ReviewEventEntity
import com.example.zhilu.data.local.entity.ReviewPlanEntity
import com.example.zhilu.data.local.entity.TagEntity
import com.example.zhilu.data.local.entity.TodoItemEntity

@Database(
    entities = [
        NoteEntity::class,
        TagEntity::class,
        NoteTagEntity::class,
        NoteBlockEntity::class,
        MediaEntity::class,
        ReviewPlanEntity::class,
        ReviewEventEntity::class,
        TodoItemEntity::class,
        ReminderInstanceEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun noteBlockDao(): NoteBlockDao
    abstract fun mediaDao(): MediaDao
    abstract fun reviewDao(): ReviewDao
    abstract fun todoDao(): TodoDao
    abstract fun reminderDao(): ReminderDao
}
