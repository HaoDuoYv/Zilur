package com.example.zhilu.export

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Test

class JsonExporterTest {
    @Test
    fun exportAndImportBackupKeepsNotesAndMedia() {
        val note = Note(
            id = 7,
            title = "Calculus",
            blocks = listOf(
                Block(id = 1, noteId = 7, type = BlockType.TEXT, content = "Limits", sortOrder = 0),
                Block(id = 2, noteId = 7, type = BlockType.IMAGE, content = "3", sortOrder = 1)
            ),
            tags = listOf(Tag(id = 2, name = "math", color = 0xFF0061A4.toInt()))
        )
        val media = Media(id = 3, uri = "file:///tmp/image.jpg", size = 42)

        val backup = JsonExporter.importBackup(JsonExporter.exportNotes(listOf(note), listOf(media)))

        assertEquals(1, backup.notes.size)
        assertEquals("Calculus", backup.notes.first().title)
        assertEquals(BlockType.IMAGE, backup.notes.first().blocks[1].type)
        assertEquals("3", backup.notes.first().blocks[1].content)
        assertEquals(1, backup.media.size)
        assertEquals("file:///tmp/image.jpg", backup.media.first().uri)
    }
}
