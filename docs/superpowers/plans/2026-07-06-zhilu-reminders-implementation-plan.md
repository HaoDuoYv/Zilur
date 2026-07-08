# ZhiLu Reminders Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add manually enabled Ebbinghaus review reminders, note-linked TODO reminders, a reminder center, and reliable local notification scheduling.

**Architecture:** Build the feature in layers: pure domain scheduling logic first, then Room entities/DAO/migration, then repositories, then Compose UI, then WorkManager notification delivery. Review and TODO reminders share `ReminderInstance`, so the reminder center and notifier do not need separate pipelines.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, Room, Hilt, Coroutines/Flow, WorkManager, Android notification APIs, JUnit.

---

## Execution Notes

- Run Gradle commands from `C:\codex-links\zhilu` because the original project path contains Chinese characters and has previously caused JVM class-loading issues.
- Do not use destructive git commands. This project currently has many untracked files because the repo started empty.
- Use TDD for each task. Run the listed test command and confirm it fails before production code for that task.
- Commit after each task only if the repository has a sensible initial commit strategy. If there is still no root commit, skip commits and leave files unstaged.

## File Map

### Domain

- Create `app/src/main/java/com/example/zhilu/domain/model/ReviewModels.kt`
  - Owns `ReviewPlan`, `ReviewEvent`, `ReviewRating`, `ReviewScheduleResult`.
- Create `app/src/main/java/com/example/zhilu/domain/model/TodoItem.kt`
  - Owns note-linked TODO data.
- Create `app/src/main/java/com/example/zhilu/domain/model/ReminderModels.kt`
  - Owns `ReminderInstance`, `ReminderType`, `ReminderStatus`, `ReminderBucket`.
- Create `app/src/main/java/com/example/zhilu/domain/reminder/ReviewSchedulePolicy.kt`
  - Pure Kotlin Ebbinghaus schedule advancement.
- Create `app/src/main/java/com/example/zhilu/domain/reminder/ReminderClassifier.kt`
  - Pure Kotlin reminder center grouping.
- Create repository interfaces under `app/src/main/java/com/example/zhilu/domain/repository/`.

### Data

- Create Room entities under `app/src/main/java/com/example/zhilu/data/local/entity/`.
- Create DAO interfaces under `app/src/main/java/com/example/zhilu/data/local/dao/`.
- Create mappers under `app/src/main/java/com/example/zhilu/data/local/mapper/`.
- Create repository implementations under `app/src/main/java/com/example/zhilu/data/repository/`.
- Modify `AppDatabase.kt`, `Migration.kt`, and `AppModule.kt`.

### UI

- Modify `NoteEditScreen.kt`, `NoteViewModel.kt`, and `NoteUiState.kt` for review controls and TODO block entry.
- Create TODO UI files under `app/src/main/java/com/example/zhilu/ui/note/`.
- Create reminder center files under `app/src/main/java/com/example/zhilu/ui/reminder/`.
- Modify `Destination.kt`, `AppNavHost.kt`, `HomeScreen.kt`, and `SettingsScreen.kt` for entry points.

### Notifications

- Create `app/src/main/java/com/example/zhilu/reminder/ReminderScheduler.kt`.
- Create `app/src/main/java/com/example/zhilu/reminder/ReminderCheckWorker.kt`.
- Create `app/src/main/java/com/example/zhilu/reminder/ReminderNotifier.kt`.
- Modify `AndroidManifest.xml` and `build.gradle.kts`.

---

### Task 1: Domain Models And Review Schedule Policy

**Files:**
- Create: `app/src/main/java/com/example/zhilu/domain/model/ReviewModels.kt`
- Create: `app/src/main/java/com/example/zhilu/domain/model/TodoItem.kt`
- Create: `app/src/main/java/com/example/zhilu/domain/model/ReminderModels.kt`
- Create: `app/src/main/java/com/example/zhilu/domain/reminder/ReviewSchedulePolicy.kt`
- Test: `app/src/test/java/com/example/zhilu/domain/reminder/ReviewSchedulePolicyTest.kt`

- [ ] **Step 1: Write failing tests**

Create `ReviewSchedulePolicyTest.kt`:

```kotlin
package com.example.zhilu.domain.reminder

import com.example.zhilu.domain.model.ReviewRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSchedulePolicyTest {
    private val now = 1_000_000L
    private val day = 86_400_000L
    private val policy = ReviewSchedulePolicy()

    @Test
    fun firstReviewStartsAtOneDay() {
        val result = policy.start(now)

        assertEquals(0, result.nextStep)
        assertEquals(now + day, result.nextReviewAt)
        assertFalse(result.completed)
    }

    @Test
    fun hardRepeatsSoonWithoutAdvancingStep() {
        val result = policy.advance(currentStep = 2, rating = ReviewRating.HARD, reviewedAt = now)

        assertEquals(1, result.nextStep)
        assertEquals(now + day, result.nextReviewAt)
        assertFalse(result.completed)
    }

    @Test
    fun normalAdvancesOneStep() {
        val result = policy.advance(currentStep = 1, rating = ReviewRating.NORMAL, reviewedAt = now)

        assertEquals(2, result.nextStep)
        assertEquals(now + 7 * day, result.nextReviewAt)
        assertFalse(result.completed)
    }

    @Test
    fun masteredAdvancesFaster() {
        val result = policy.advance(currentStep = 1, rating = ReviewRating.MASTERED, reviewedAt = now)

        assertEquals(3, result.nextStep)
        assertEquals(now + 15 * day, result.nextReviewAt)
        assertFalse(result.completed)
    }

    @Test
    fun finalStepCompletesPlan() {
        val result = policy.advance(currentStep = 4, rating = ReviewRating.MASTERED, reviewedAt = now)

        assertEquals(4, result.nextStep)
        assertEquals(null, result.nextReviewAt)
        assertTrue(result.completed)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
cd C:\codex-links\zhilu
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.domain.reminder.ReviewSchedulePolicyTest --no-daemon
```

Expected: compile failure for missing `ReviewSchedulePolicy` and `ReviewRating`.

- [ ] **Step 3: Add minimal domain implementation**

Create `ReviewModels.kt`:

```kotlin
package com.example.zhilu.domain.model

data class ReviewPlan(
    val id: Long = 0,
    val noteId: Long,
    val enabled: Boolean = true,
    val currentStep: Int = 0,
    val nextReviewAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

data class ReviewEvent(
    val id: Long = 0,
    val planId: Long,
    val noteId: Long,
    val reviewedAt: Long,
    val rating: ReviewRating,
    val previousStep: Int,
    val nextStep: Int,
    val nextReviewAt: Long?
)

enum class ReviewRating(val value: Int) {
    HARD(1),
    NORMAL(2),
    MASTERED(3);

    companion object {
        fun fromValue(value: Int): ReviewRating =
            entries.firstOrNull { it.value == value } ?: NORMAL
    }
}

data class ReviewScheduleResult(
    val nextStep: Int,
    val nextReviewAt: Long?,
    val completed: Boolean
)
```

Create `TodoItem.kt`:

```kotlin
package com.example.zhilu.domain.model

data class TodoItem(
    val id: Long = 0,
    val noteId: Long? = null,
    val content: String = "",
    val remindAt: Long? = null,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0
) {
    val isCompleted: Boolean
        get() = completedAt != null
}
```

Create `ReminderModels.kt`:

```kotlin
package com.example.zhilu.domain.model

data class ReminderInstance(
    val id: Long = 0,
    val type: ReminderType,
    val sourceId: Long,
    val noteId: Long? = null,
    val dueAt: Long,
    val status: ReminderStatus = ReminderStatus.SCHEDULED,
    val notificationId: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val firedAt: Long? = null
)

enum class ReminderType(val value: Int) {
    REVIEW(1),
    TODO(2);

    companion object {
        fun fromValue(value: Int): ReminderType =
            entries.firstOrNull { it.value == value } ?: REVIEW
    }
}

enum class ReminderStatus(val value: Int) {
    SCHEDULED(1),
    FIRED(2),
    DONE(3),
    CANCELED(4);

    companion object {
        fun fromValue(value: Int): ReminderStatus =
            entries.firstOrNull { it.value == value } ?: SCHEDULED
    }
}

data class ReminderBucket(
    val today: List<ReminderInstance> = emptyList(),
    val overdue: List<ReminderInstance> = emptyList(),
    val future: List<ReminderInstance> = emptyList(),
    val completed: List<ReminderInstance> = emptyList()
)
```

Create `ReviewSchedulePolicy.kt`:

```kotlin
package com.example.zhilu.domain.reminder

import com.example.zhilu.domain.model.ReviewRating
import com.example.zhilu.domain.model.ReviewScheduleResult

class ReviewSchedulePolicy(
    private val intervalsMillis: List<Long> = defaultIntervalsMillis
) {
    fun start(now: Long): ReviewScheduleResult =
        ReviewScheduleResult(
            nextStep = 0,
            nextReviewAt = now + intervalsMillis[0],
            completed = false
        )

    fun advance(currentStep: Int, rating: ReviewRating, reviewedAt: Long): ReviewScheduleResult {
        val targetStep = when (rating) {
            ReviewRating.HARD -> (currentStep - 1).coerceAtLeast(0)
            ReviewRating.NORMAL -> currentStep + 1
            ReviewRating.MASTERED -> currentStep + 2
        }
        val boundedStep = targetStep.coerceAtMost(intervalsMillis.lastIndex)
        val completed = targetStep > intervalsMillis.lastIndex ||
            currentStep >= intervalsMillis.lastIndex && rating == ReviewRating.MASTERED
        return ReviewScheduleResult(
            nextStep = boundedStep,
            nextReviewAt = if (completed) null else reviewedAt + intervalsMillis[boundedStep],
            completed = completed
        )
    }

    companion object {
        private const val DAY = 86_400_000L
        val defaultIntervalsMillis: List<Long> = listOf(1, 3, 7, 15, 30).map { it * DAY }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run the same targeted test command. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/example/zhilu/domain/model app/src/main/java/com/example/zhilu/domain/reminder app/src/test/java/com/example/zhilu/domain/reminder/ReviewSchedulePolicyTest.kt
git commit -m "feat: add reminder domain scheduling policy"
```

If the repository still has no root commit and the project files are untracked, skip this commit and document that in the final handoff.

---

### Task 2: Reminder Classifier

**Files:**
- Create: `app/src/main/java/com/example/zhilu/domain/reminder/ReminderClassifier.kt`
- Test: `app/src/test/java/com/example/zhilu/domain/reminder/ReminderClassifierTest.kt`

- [ ] **Step 1: Write failing tests**

```kotlin
package com.example.zhilu.domain.reminder

import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderStatus
import com.example.zhilu.domain.model.ReminderType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderClassifierTest {
    private val startOfToday = 1_000_000L
    private val day = 86_400_000L
    private val classifier = ReminderClassifier()

    @Test
    fun groupsScheduledRemindersByDueTimeAndCompletedStatus() {
        val overdue = reminder(id = 1, dueAt = startOfToday - 1)
        val today = reminder(id = 2, dueAt = startOfToday + 1_000)
        val future = reminder(id = 3, dueAt = startOfToday + day + 1)
        val done = reminder(id = 4, dueAt = startOfToday, status = ReminderStatus.DONE)

        val bucket = classifier.classify(
            reminders = listOf(future, done, overdue, today),
            now = startOfToday + 2_000,
            startOfToday = startOfToday
        )

        assertEquals(listOf(today), bucket.today)
        assertEquals(listOf(overdue), bucket.overdue)
        assertEquals(listOf(future), bucket.future)
        assertEquals(listOf(done), bucket.completed)
    }

    private fun reminder(
        id: Long,
        dueAt: Long,
        status: ReminderStatus = ReminderStatus.SCHEDULED
    ) = ReminderInstance(
        id = id,
        type = ReminderType.REVIEW,
        sourceId = id,
        noteId = id,
        dueAt = dueAt,
        status = status,
        notificationId = id.toInt()
    )
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
cd C:\codex-links\zhilu
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.domain.reminder.ReminderClassifierTest --no-daemon
```

Expected: compile failure for missing `ReminderClassifier`.

- [ ] **Step 3: Implement classifier**

```kotlin
package com.example.zhilu.domain.reminder

import com.example.zhilu.domain.model.ReminderBucket
import com.example.zhilu.domain.model.ReminderInstance
import com.example.zhilu.domain.model.ReminderStatus

class ReminderClassifier {
    fun classify(
        reminders: List<ReminderInstance>,
        now: Long,
        startOfToday: Long
    ): ReminderBucket {
        val endOfToday = startOfToday + DAY
        val active = reminders.filter { it.status == ReminderStatus.SCHEDULED || it.status == ReminderStatus.FIRED }
        return ReminderBucket(
            today = active.filter { it.dueAt >= now && it.dueAt < endOfToday }.sortedBy { it.dueAt },
            overdue = active.filter { it.dueAt < now }.sortedBy { it.dueAt },
            future = active.filter { it.dueAt >= endOfToday }.sortedBy { it.dueAt },
            completed = reminders.filter { it.status == ReminderStatus.DONE }.sortedByDescending { it.updatedAt }
        )
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
```

- [ ] **Step 4: Run tests**

Run the targeted classifier test plus Task 1 tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.domain.reminder.* --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/example/zhilu/domain/reminder/ReminderClassifier.kt app/src/test/java/com/example/zhilu/domain/reminder/ReminderClassifierTest.kt
git commit -m "feat: classify reminder center buckets"
```

---

### Task 3: Room Entities, DAOs, Mappers, And Migration

**Files:**
- Create: `ReviewPlanEntity.kt`, `ReviewEventEntity.kt`, `TodoItemEntity.kt`, `ReminderInstanceEntity.kt`
- Create: `ReviewDao.kt`, `TodoDao.kt`, `ReminderDao.kt`
- Create: `ReviewMapper.kt`, `TodoMapper.kt`, `ReminderMapper.kt`
- Modify: `AppDatabase.kt`, `Migration.kt`, `Converters.kt`
- Test: `app/src/test/java/com/example/zhilu/data/local/mapper/ReminderMapperTest.kt`

- [ ] **Step 1: Write mapper test**

```kotlin
package com.example.zhilu.data.local.mapper

import com.example.zhilu.data.local.entity.ReminderInstanceEntity
import com.example.zhilu.domain.model.ReminderStatus
import com.example.zhilu.domain.model.ReminderType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderMapperTest {
    @Test
    fun mapsReminderEntityToDomainUsingStableEnumValues() {
        val entity = ReminderInstanceEntity(
            id = 9,
            type = ReminderType.TODO.value,
            sourceId = 4,
            noteId = 7,
            dueAt = 100,
            status = ReminderStatus.FIRED.value,
            notificationId = 44,
            createdAt = 1,
            updatedAt = 2,
            firedAt = 3
        )

        val domain = ReminderMapper.toDomain(entity)

        assertEquals(ReminderType.TODO, domain.type)
        assertEquals(ReminderStatus.FIRED, domain.status)
        assertEquals(7L, domain.noteId)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
cd C:\codex-links\zhilu
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.data.local.mapper.ReminderMapperTest --no-daemon
```

Expected: compile failure for missing entity and mapper.

- [ ] **Step 3: Create Room entities**

Use `Long?` only where the spec allows nulls. Add indices for query paths.

```kotlin
@Entity(
    tableName = "review_plans",
    indices = [Index(value = ["noteId"], unique = true)]
)
data class ReviewPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val enabled: Boolean,
    val currentStep: Int,
    val nextReviewAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?
)
```

```kotlin
@Entity(
    tableName = "review_events",
    indices = [Index(value = ["planId"]), Index(value = ["noteId"])]
)
data class ReviewEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val planId: Long,
    val noteId: Long,
    val reviewedAt: Long,
    val rating: Int,
    val previousStep: Int,
    val nextStep: Int,
    val nextReviewAt: Long?
)
```

```kotlin
@Entity(
    tableName = "todo_items",
    indices = [Index(value = ["noteId"]), Index(value = ["remindAt"])]
)
data class TodoItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long?,
    val content: String,
    val remindAt: Long?,
    val completedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val sortOrder: Int
)
```

```kotlin
@Entity(
    tableName = "reminder_instances",
    indices = [
        Index(value = ["type", "sourceId", "status"]),
        Index(value = ["noteId"]),
        Index(value = ["dueAt"])
    ]
)
data class ReminderInstanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: Int,
    val sourceId: Long,
    val noteId: Long?,
    val dueAt: Long,
    val status: Int,
    val notificationId: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val firedAt: Long?
)
```

- [ ] **Step 4: Create DAO interfaces**

Essential DAO methods:

```kotlin
@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_plans WHERE noteId = :noteId LIMIT 1")
    suspend fun getPlanByNoteId(noteId: Long): ReviewPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: ReviewPlanEntity): Long

    @Update
    suspend fun updatePlan(plan: ReviewPlanEntity)

    @Insert
    suspend fun insertEvent(event: ReviewEventEntity): Long
}
```

```kotlin
@Dao
interface TodoDao {
    @Query("SELECT * FROM todo_items WHERE noteId = :noteId ORDER BY sortOrder, createdAt")
    fun observeByNoteId(noteId: Long): Flow<List<TodoItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(todo: TodoItemEntity): Long

    @Update
    suspend fun update(todo: TodoItemEntity)

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun getById(id: Long): TodoItemEntity?
}
```

```kotlin
@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder_instances ORDER BY dueAt")
    fun observeAll(): Flow<List<ReminderInstanceEntity>>

    @Query("SELECT * FROM reminder_instances WHERE dueAt <= :now AND status IN (:statuses) ORDER BY dueAt")
    suspend fun dueReminders(now: Long, statuses: List<Int>): List<ReminderInstanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderInstanceEntity): Long

    @Update
    suspend fun update(reminder: ReminderInstanceEntity)

    @Query("UPDATE reminder_instances SET status = :status, updatedAt = :updatedAt WHERE type = :type AND sourceId = :sourceId AND status IN (:activeStatuses)")
    suspend fun markActiveForSource(type: Int, sourceId: Long, status: Int, updatedAt: Long, activeStatuses: List<Int>)
}
```

- [ ] **Step 5: Create mappers**

`ReminderMapper` must match the mapper test:

```kotlin
object ReminderMapper {
    fun toDomain(entity: ReminderInstanceEntity): ReminderInstance = ReminderInstance(
        id = entity.id,
        type = ReminderType.fromValue(entity.type),
        sourceId = entity.sourceId,
        noteId = entity.noteId,
        dueAt = entity.dueAt,
        status = ReminderStatus.fromValue(entity.status),
        notificationId = entity.notificationId,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        firedAt = entity.firedAt
    )

    fun toEntity(domain: ReminderInstance): ReminderInstanceEntity = ReminderInstanceEntity(
        id = domain.id,
        type = domain.type.value,
        sourceId = domain.sourceId,
        noteId = domain.noteId,
        dueAt = domain.dueAt,
        status = domain.status.value,
        notificationId = domain.notificationId,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt,
        firedAt = domain.firedAt
    )
}
```

- [ ] **Step 6: Wire database and migration**

Modify `AppDatabase.kt`:

- Add the four new entity classes to `entities`.
- Change `version = 2`.
- Add abstract DAO accessors.

Modify `Migration.kt`:

```kotlin
object Migration {
    private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `review_plans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `noteId` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `currentStep` INTEGER NOT NULL, `nextReviewAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `completedAt` INTEGER)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_review_plans_noteId` ON `review_plans` (`noteId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `review_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `planId` INTEGER NOT NULL, `noteId` INTEGER NOT NULL, `reviewedAt` INTEGER NOT NULL, `rating` INTEGER NOT NULL, `previousStep` INTEGER NOT NULL, `nextStep` INTEGER NOT NULL, `nextReviewAt` INTEGER)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_events_planId` ON `review_events` (`planId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_review_events_noteId` ON `review_events` (`noteId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `todo_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `noteId` INTEGER, `content` TEXT NOT NULL, `remindAt` INTEGER, `completedAt` INTEGER, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_noteId` ON `todo_items` (`noteId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_remindAt` ON `todo_items` (`remindAt`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `reminder_instances` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `sourceId` INTEGER NOT NULL, `noteId` INTEGER, `dueAt` INTEGER NOT NULL, `status` INTEGER NOT NULL, `notificationId` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `firedAt` INTEGER)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_instances_type_sourceId_status` ON `reminder_instances` (`type`, `sourceId`, `status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_instances_noteId` ON `reminder_instances` (`noteId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminder_instances_dueAt` ON `reminder_instances` (`dueAt`)")
        }
    }

    val all = arrayOf(MIGRATION_1_2)
}
```

- [ ] **Step 7: Run tests and compile**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.data.local.mapper.ReminderMapperTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

Expected: both commands end with `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/com/example/zhilu/data/local app/src/test/java/com/example/zhilu/data/local/mapper/ReminderMapperTest.kt
git commit -m "feat: add reminder room schema"
```

---

### Task 4: Repositories And Reminder State Transitions

**Files:**
- Create: `ReviewRepository.kt`, `TodoRepository.kt`, `ReminderRepository.kt`
- Create: `ReviewRepositoryImpl.kt`, `TodoRepositoryImpl.kt`, `ReminderRepositoryImpl.kt`
- Modify: `AppModule.kt`
- Test: `app/src/test/java/com/example/zhilu/data/repository/ReminderRepositoryImplTest.kt`

- [ ] **Step 1: Write repository tests using fakes**

Test the repository methods with fake DAOs if Room test dependencies are not configured. Required behavior:

```kotlin
@Test
fun insertScheduledReminderCancelsPreviousActiveReminderForSameSource() = runTest {
    val dao = FakeReminderDao()
    val repository = ReminderRepositoryImpl(dao)

    repository.upsertScheduled(
        ReminderInstance(
            type = ReminderType.REVIEW,
            sourceId = 8,
            noteId = 5,
            dueAt = 100,
            notificationId = 800
        )
    )
    repository.upsertScheduled(
        ReminderInstance(
            type = ReminderType.REVIEW,
            sourceId = 8,
            noteId = 5,
            dueAt = 200,
            notificationId = 801
        )
    )

    val active = dao.items.filter { it.status == ReminderStatus.SCHEDULED.value }
    assertEquals(1, active.size)
    assertEquals(200L, active.single().dueAt)
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.data.repository.ReminderRepositoryImplTest --no-daemon
```

Expected: compile failure for missing repository classes.

- [ ] **Step 3: Add repository interfaces**

```kotlin
interface ReminderRepository {
    fun observeAll(): Flow<RepositoryResult<List<ReminderInstance>>>
    suspend fun getDueReminders(now: Long): RepositoryResult<List<ReminderInstance>>
    suspend fun upsertScheduled(reminder: ReminderInstance): RepositoryResult<Long>
    suspend fun markDone(type: ReminderType, sourceId: Long): RepositoryResult<Unit>
    suspend fun markFired(reminder: ReminderInstance, firedAt: Long): RepositoryResult<Unit>
    suspend fun cancel(type: ReminderType, sourceId: Long): RepositoryResult<Unit>
}
```

```kotlin
interface ReviewRepository {
    suspend fun getPlanByNoteId(noteId: Long): RepositoryResult<ReviewPlan?>
    suspend fun startPlan(noteId: Long, now: Long): RepositoryResult<ReviewPlan>
    suspend fun recordReview(plan: ReviewPlan, rating: ReviewRating, reviewedAt: Long): RepositoryResult<ReviewPlan>
    suspend fun disablePlan(noteId: Long): RepositoryResult<Unit>
}
```

```kotlin
interface TodoRepository {
    fun observeByNoteId(noteId: Long): Flow<RepositoryResult<List<TodoItem>>>
    suspend fun addTodo(todo: TodoItem): RepositoryResult<Long>
    suspend fun updateTodo(todo: TodoItem): RepositoryResult<Unit>
    suspend fun completeTodo(id: Long, completedAt: Long): RepositoryResult<Unit>
}
```

- [ ] **Step 4: Implement repositories**

Follow existing `NoteRepositoryImpl` style:

- Wrap suspend DAO calls in `runCatching { ... }.toRepositoryResult("message")`.
- Map Flow values to `RepositoryResult.Success`.
- In `ReviewRepositoryImpl.startPlan`, call `ReviewSchedulePolicy.start(now)` and create a `ReviewPlan`.
- In `ReviewRepositoryImpl.recordReview`, insert `ReviewEvent`, update `ReviewPlan`, and let UI or service create the next `ReminderInstance`.
- In `ReminderRepositoryImpl.upsertScheduled`, cancel active reminders for same type/source before insert.

- [ ] **Step 5: Wire Hilt**

Modify `AppModule.kt`:

```kotlin
@Provides
fun provideReviewDao(database: AppDatabase): ReviewDao = database.reviewDao()

@Provides
fun provideTodoDao(database: AppDatabase): TodoDao = database.todoDao()

@Provides
fun provideReminderDao(database: AppDatabase): ReminderDao = database.reminderDao()

@Provides
@Singleton
fun provideReminderRepository(reminderDao: ReminderDao): ReminderRepository =
    ReminderRepositoryImpl(reminderDao)
```

Add these providers for `ReviewRepository` and `TodoRepository`:

```kotlin
@Provides
@Singleton
fun provideReviewRepository(
    reviewDao: ReviewDao
): ReviewRepository = ReviewRepositoryImpl(reviewDao, ReviewSchedulePolicy())

@Provides
@Singleton
fun provideTodoRepository(todoDao: TodoDao): TodoRepository = TodoRepositoryImpl(todoDao)
```

- [ ] **Step 6: Run repository and full unit tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.data.repository.ReminderRepositoryImplTest --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/example/zhilu/domain/repository app/src/main/java/com/example/zhilu/data/repository app/src/main/java/com/example/zhilu/di/AppModule.kt app/src/test/java/com/example/zhilu/data/repository
git commit -m "feat: add reminder repositories"
```

---

### Task 5: Review Controls In Knowledge Detail

**Files:**
- Modify: `NoteUiState.kt`, `NoteViewModel.kt`, `NoteEditScreen.kt`
- Create: `ReviewPanel.kt`
- Test: `app/src/test/java/com/example/zhilu/ui/note/NoteViewModelReviewTest.kt`

- [ ] **Step 1: Write ViewModel tests**

Test behavior:

```kotlin
@Test
fun startReviewCreatesPlanAndShowsNextReviewTime() = runTest(dispatcher) {
    val reviewRepository = FakeReviewRepository()
    val reminderRepository = FakeReminderRepository()
    val viewModel = NoteViewModel(
        noteRepository = FakeNoteRepository(note = Note(id = 3, title = "线代")),
        tagRepository = FakeTagRepository(emptyList()),
        reviewRepository = reviewRepository,
        reminderRepository = reminderRepository,
        savedStateHandle = SavedStateHandle(mapOf("noteId" to 3L))
    )

    advanceUntilIdle()
    viewModel.startReviewPlan(now = 1_000_000L)
    advanceUntilIdle()

    assertEquals(3L, viewModel.uiState.value.reviewPlan?.noteId)
    assertEquals(1_000_000L + 86_400_000L, viewModel.uiState.value.reviewPlan?.nextReviewAt)
    assertEquals(1, reminderRepository.scheduled.size)
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.ui.note.NoteViewModelReviewTest --no-daemon
```

Expected: compile failure for missing constructor dependencies and state properties.

- [ ] **Step 3: Extend UI state and ViewModel**

Add to `NoteUiState`:

```kotlin
val reviewPlan: ReviewPlan? = null,
val isReviewDue: Boolean = false
```

Inject `ReviewRepository` and `ReminderRepository` into `NoteViewModel`.

Add methods:

```kotlin
fun startReviewPlan(now: Long = System.currentTimeMillis()) { ... }
fun recordReview(rating: ReviewRating, now: Long = System.currentTimeMillis()) { ... }
fun disableReviewPlan() { ... }
```

For each plan with `nextReviewAt != null`, create a `ReminderInstance` with:

```kotlin
ReminderInstance(
    type = ReminderType.REVIEW,
    sourceId = plan.id,
    noteId = plan.noteId,
    dueAt = plan.nextReviewAt,
    notificationId = ("review-${plan.id}").hashCode()
)
```

- [ ] **Step 4: Create ReviewPanel**

`ReviewPanel.kt`:

```kotlin
@Composable
fun ReviewPanel(
    plan: ReviewPlan?,
    isDue: Boolean,
    onStart: () -> Unit,
    onDisable: () -> Unit,
    onRate: (ReviewRating) -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("复习计划", style = MaterialTheme.typography.titleMedium)
            if (plan == null || !plan.enabled) {
                Button(onClick = onStart) { Text("开启复习") }
            } else {
                Text("下一次复习：${formatReminderTime(plan.nextReviewAt)}")
                if (isDue) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onRate(ReviewRating.HARD) }) { Text("困难") }
                        TextButton(onClick = { onRate(ReviewRating.NORMAL) }) { Text("一般") }
                        Button(onClick = { onRate(ReviewRating.MASTERED) }) { Text("掌握") }
                    }
                }
                TextButton(onClick = onDisable) { Text("关闭复习") }
            }
        }
    }
}
```

Place helper `formatReminderTime` in a focused UI formatting file such as `ReminderTimeFormatter.kt`.

- [ ] **Step 5: Wire panel into read-only detail**

In `NoteEditScreen.kt`, insert `ReviewPanel` below `ReadOnlyHeader(state)` in read-only mode. Keep edit mode focused on content editing.

- [ ] **Step 6: Run tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.ui.note.NoteViewModelReviewTest --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/example/zhilu/ui/note app/src/test/java/com/example/zhilu/ui/note/NoteViewModelReviewTest.kt
git commit -m "feat: add review controls to note detail"
```

---

### Task 6: Note-Linked TODO Block

**Files:**
- Modify: `Block.kt`
- Modify: `NoteEditScreen.kt`, `NoteViewModel.kt`
- Create: `TodoBlock.kt`
- Test: `app/src/test/java/com/example/zhilu/domain/model/BlockTypeTest.kt`
- Test: `app/src/test/java/com/example/zhilu/ui/note/NoteViewModelTodoTest.kt`

- [ ] **Step 1: Extend BlockType test**

Add:

```kotlin
@Test
fun todoBlockTypeUsesStableValue() {
    assertEquals(BlockType.TODO, BlockType.fromValue(7))
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.domain.model.BlockTypeTest --no-daemon
```

Expected: compile failure for missing `BlockType.TODO`.

- [ ] **Step 3: Add BlockType.TODO**

In `Block.kt`:

```kotlin
TODO(7);
```

Update all exhaustive `when` branches in note UI and exporters. Markdown export for TODO blocks can output:

```markdown
- [ ] content
```

for incomplete items and:

```markdown
- [x] content
```

for completed items.

- [ ] **Step 4: Write ViewModel TODO test**

Test:

```kotlin
@Test
fun creatingTodoWithReminderSchedulesReminder() = runTest(dispatcher) {
    val todoRepository = FakeTodoRepository()
    val reminderRepository = FakeReminderRepository()
    val viewModel = buildNoteViewModel(todoRepository = todoRepository, reminderRepository = reminderRepository)

    viewModel.createTodo(content = "复习矩阵题", remindAt = 2_000L)
    advanceUntilIdle()

    assertEquals("复习矩阵题", todoRepository.items.single().content)
    assertEquals(ReminderType.TODO, reminderRepository.scheduled.single().type)
    assertEquals(2_000L, reminderRepository.scheduled.single().dueAt)
}
```

- [ ] **Step 5: Implement TODO state and actions**

Add to `NoteUiState`:

```kotlin
val todoItems: List<TodoItem> = emptyList(),
val showCompletedTodos: Boolean = false
```

Inject `TodoRepository` and `ReminderRepository` into `NoteViewModel`.

Add methods:

```kotlin
fun createTodo(content: String, remindAt: Long?)
fun updateTodo(todo: TodoItem)
fun completeTodo(todoId: Long)
fun toggleCompletedTodos()
```

When `remindAt != null`, schedule `ReminderType.TODO`.

- [ ] **Step 6: Create TODO block UI**

`TodoBlock.kt`:

```kotlin
@Composable
fun TodoBlock(
    items: List<TodoItem>,
    showCompleted: Boolean,
    onAdd: (String, Long?) -> Unit,
    onComplete: (Long) -> Unit,
    onToggleCompleted: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var remindAtText by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("待办") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = remindAtText,
            onValueChange = { value -> remindAtText = value.filter { it.isDigit() } },
            label = { Text("提醒时间戳") },
            supportingText = { Text("留空表示不提醒") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                onAdd(text, remindAtText.toLongOrNull())
                text = ""
                remindAtText = ""
            },
            enabled = text.isNotBlank()
        ) { Text("添加") }
        items.filter { showCompleted || !it.isCompleted }.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.isCompleted,
                    onCheckedChange = { if (!item.isCompleted) onComplete(item.id) }
                )
                Text(item.content, modifier = Modifier.weight(1f))
            }
        }
        TextButton(onClick = onToggleCompleted) {
            Text(if (showCompleted) "隐藏已完成" else "显示已完成")
        }
    }
}
```

For reminder time entry in this task, use the `提醒时间戳` epoch-millis field shown above. A date/time picker is outside this plan.

- [ ] **Step 7: Wire TODO button**

In `BlockToolbar`, add `onAddTodo` and button text `待办`. In edit rendering, `BlockType.TODO` renders `TodoBlock`.

- [ ] **Step 8: Run tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.domain.model.BlockTypeTest --tests com.example.zhilu.ui.note.NoteViewModelTodoTest --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 9: Commit**

```powershell
git add app/src/main/java/com/example/zhilu/domain/model app/src/main/java/com/example/zhilu/ui/note app/src/test/java/com/example/zhilu/ui/note app/src/test/java/com/example/zhilu/domain/model/BlockTypeTest.kt
git commit -m "feat: add note todo reminders"
```

---

### Task 7: Reminder Center Screen

**Files:**
- Create: `ReminderCenterUiState.kt`
- Create: `ReminderCenterViewModel.kt`
- Create: `ReminderCenterScreen.kt`
- Modify: `Destination.kt`, `AppNavHost.kt`, `HomeScreen.kt`, `SettingsScreen.kt`
- Test: `app/src/test/java/com/example/zhilu/ui/reminder/ReminderCenterViewModelTest.kt`

- [ ] **Step 1: Write ViewModel test**

```kotlin
@Test
fun reminderCenterGroupsReminderBuckets() = runTest(dispatcher) {
    val repository = FakeReminderRepository(
        reminders = listOf(
            reminder(id = 1, dueAt = 900),
            reminder(id = 2, dueAt = 1_100),
            reminder(id = 3, dueAt = 90_000_000)
        )
    )
    val viewModel = ReminderCenterViewModel(
        reminderRepository = repository,
        classifier = ReminderClassifier(),
        clock = { 1_200L },
        startOfToday = { 1_000L }
    )

    advanceUntilIdle()

    assertEquals(1, viewModel.uiState.value.overdue.size)
    assertEquals(1, viewModel.uiState.value.today.size)
    assertEquals(1, viewModel.uiState.value.future.size)
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.ui.reminder.ReminderCenterViewModelTest --no-daemon
```

Expected: compile failure for missing reminder center classes.

- [ ] **Step 3: Implement UiState and ViewModel**

```kotlin
data class ReminderCenterUiState(
    val today: List<ReminderInstance> = emptyList(),
    val overdue: List<ReminderInstance> = emptyList(),
    val future: List<ReminderInstance> = emptyList(),
    val completed: List<ReminderInstance> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)
```

ViewModel observes `ReminderRepository.observeAll()`, classifies with `ReminderClassifier`, and exposes actions:

```kotlin
fun markDone(reminder: ReminderInstance)
fun cancel(reminder: ReminderInstance)
```

- [ ] **Step 4: Implement ReminderCenterScreen**

Use Material 3 and existing `AppTopBar`. Keep layout dense and operational:

- Top bar title `提醒中心`.
- Sections: `逾期`, `今日复习`, `待办提醒`, `未来`, `已完成`.
- Each row has type label, due time, and action buttons.
- Clicking a row with `noteId != null` navigates to `Destination.NoteEdit.createRoute(noteId)`.

- [ ] **Step 5: Wire navigation and entries**

Add destination:

```kotlin
data object Reminders : Destination("reminders")
```

Add composable in `AppNavHost`.

Add homepage top action with notification/reminder icon text `提醒`. Add Settings row `提醒中心`.

- [ ] **Step 6: Run tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.ui.reminder.ReminderCenterViewModelTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/java/com/example/zhilu/ui/reminder app/src/main/java/com/example/zhilu/ui/navigation app/src/main/java/com/example/zhilu/ui/home app/src/main/java/com/example/zhilu/ui/settings app/src/test/java/com/example/zhilu/ui/reminder
git commit -m "feat: add reminder center"
```

---

### Task 8: WorkManager Notification Delivery

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/example/zhilu/reminder/ReminderNotifier.kt`
- Create: `app/src/main/java/com/example/zhilu/reminder/ReminderCheckWorker.kt`
- Create: `app/src/main/java/com/example/zhilu/reminder/ReminderScheduler.kt`
- Modify: `AppModule.kt`
- Test: `app/src/test/java/com/example/zhilu/reminder/ReminderSchedulerTest.kt`

- [ ] **Step 1: Add WorkManager dependency**

In `build.gradle.kts`:

```kotlin
implementation("androidx.work:work-runtime-ktx:2.9.1")
implementation("androidx.hilt:hilt-work:1.2.0")
kapt("androidx.hilt:hilt-compiler:1.2.0")
```

- [ ] **Step 2: Add manifest permissions**

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

- [ ] **Step 3: Write scheduler test**

Keep this test pure by wrapping WorkManager behind an interface:

```kotlin
@Test
fun scheduleRefreshEnqueuesUniqueReminderWork() {
    val enqueuer = RecordingWorkEnqueuer()
    val scheduler = ReminderScheduler(enqueuer)

    scheduler.schedulePeriodicChecks()
    scheduler.scheduleOneTimeCheck()

    assertEquals(listOf("periodic-reminder-check", "one-time-reminder-check"), enqueuer.names)
}
```

- [ ] **Step 4: Run test to verify it fails**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.reminder.ReminderSchedulerTest --no-daemon
```

Expected: compile failure for missing scheduler.

- [ ] **Step 5: Implement notifier**

`ReminderNotifier` responsibilities:

- Create notification channel `zhilu_reminders`.
- Check `POST_NOTIFICATIONS` permission on Android 13+ before calling `NotificationManagerCompat.notify`.
- Build PendingIntent to `MainActivity` with extras for `noteId` or reminder center fallback.

- [ ] **Step 6: Implement worker**

`ReminderCheckWorker`:

- Inject `ReminderRepository` and `ReminderNotifier`.
- Fetch `getDueReminders(System.currentTimeMillis())`.
- Notify each due reminder.
- Mark reminder as `FIRED` with `firedAt`.
- Return `Result.success()` if repository returns success; return `Result.retry()` on repository error.

- [ ] **Step 7: Implement scheduler**

Use unique work names:

```kotlin
const val PERIODIC_WORK = "periodic-reminder-check"
const val ONE_TIME_WORK = "one-time-reminder-check"
```

Schedule periodic checks with WorkManager minimum interval. Schedule one-time work after data mutations.

- [ ] **Step 8: Wire Hilt WorkManager**

Update `ZhiLuApplication` to implement `Configuration.Provider` with `HiltWorkerFactory`:

```kotlin
@HiltAndroidApp
class ZhiLuApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
```

- [ ] **Step 9: Run tests and assemble**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.reminder.ReminderSchedulerTest --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```powershell
git add app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/com/example/zhilu/reminder app/src/main/java/com/example/zhilu/ZhiLuApplication.kt app/src/test/java/com/example/zhilu/reminder
git commit -m "feat: deliver reminder notifications"
```

---

### Task 9: Notification Permission UX And Settings

**Files:**
- Modify: `SettingsScreen.kt`, `SettingsViewModel.kt`, `SettingsUiState.kt`
- Modify: `NoteEditScreen.kt`
- Create: `NotificationPermissionState.kt`
- Test: `app/src/test/java/com/example/zhilu/ui/settings/NotificationPermissionStateTest.kt`

- [ ] **Step 1: Write permission helper test**

```kotlin
@Test
fun sdkBelow33DoesNotRequirePostNotificationsRuntimePermission() {
    assertEquals(false, NotificationPermissionState.requiresRuntimePermission(sdkInt = 32))
}

@Test
fun sdk33RequiresPostNotificationsRuntimePermission() {
    assertEquals(true, NotificationPermissionState.requiresRuntimePermission(sdkInt = 33))
}
```

- [ ] **Step 2: Implement helper**

```kotlin
object NotificationPermissionState {
    fun requiresRuntimePermission(sdkInt: Int): Boolean = sdkInt >= 33
}
```

- [ ] **Step 3: Add settings UI**

Settings reminder section:

- `提醒中心`
- `通知权限：已开启/未开启`
- `打开系统通知设置`
- `提醒总开关`

Use Android intent:

```kotlin
Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
```

- [ ] **Step 4: Request permission at first reminder action**

In `NoteEditScreen`, when user taps “开启复习” or creates a TODO with `remindAt`, request `POST_NOTIFICATIONS` on Android 13+ if not granted. If denied, continue creating the reminder data and show Snackbar `通知权限未开启，可在提醒中心查看到期项目`.

- [ ] **Step 5: Run tests and assemble**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.example.zhilu.ui.settings.NotificationPermissionStateTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/example/zhilu/ui/settings app/src/main/java/com/example/zhilu/ui/note app/src/test/java/com/example/zhilu/ui/settings
git commit -m "feat: add reminder notification permission ux"
```

---

### Task 10: Final Integration And Regression Verification

**Files:**
- Modify: `CHANGELOG.md`
- Review: all files changed in previous tasks

- [ ] **Step 1: Run full unit tests**

```powershell
cd C:\codex-links\zhilu
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run clean debug build**

```powershell
.\gradlew.bat clean :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Manual smoke checklist**

Install `app/build/outputs/apk/debug/app-debug.apk` and verify:

- Open an existing knowledge point.
- Tap `开启复习`.
- See next review time in detail page.
- Force a due reminder in database or use a short test time and confirm reminder center shows it.
- Record review as `困难`, `一般`, and `掌握` in separate notes.
- Add TODO block in note.
- Add TODO item and complete it.
- Confirm completed TODO hides by default and appears after toggling `显示已完成`.
- Open `提醒中心` from Home and Settings.
- Deny notification permission and confirm app still works without crash.

- [ ] **Step 4: Update CHANGELOG**

Add:

```markdown
## 2026-07-06

- Added manually enabled Ebbinghaus review plans.
- Added note-linked TODO reminders.
- Added reminder center for review and TODO items.
- Added WorkManager-based reminder checks and notification permission fallback.
```

- [ ] **Step 5: Final status**

Run:

```powershell
git status --short
```

Report:

- Test commands run and results.
- APK path.
- Any skipped commits due to missing root commit or untracked project state.
