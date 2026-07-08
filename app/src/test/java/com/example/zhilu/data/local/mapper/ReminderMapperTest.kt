package com.example.zhilu.data.local.mapper

import com.example.zhilu.data.local.entity.ReminderInstanceEntity
import com.example.zhilu.domain.model.ReminderInstance
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

        assertEquals(
            ReminderInstance(
                id = 9,
                type = ReminderType.TODO,
                sourceId = 4,
                noteId = 7,
                dueAt = 100,
                status = ReminderStatus.FIRED,
                notificationId = 44,
                createdAt = 1,
                updatedAt = 2,
                firedAt = 3
            ),
            domain
        )
        assertEquals(entity, ReminderMapper.toEntity(domain))
    }
}
