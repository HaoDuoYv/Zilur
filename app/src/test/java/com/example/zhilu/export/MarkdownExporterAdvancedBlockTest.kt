package com.example.zhilu.export

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.TodoItem
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownExporterAdvancedBlockTest {
    @Test
    fun exportsLatexAndCodeBlocks() {
        val note = Note(
            title = "Advanced",
            blocks = listOf(
                Block(type = BlockType.LATEX, content = "\\frac{a}{b}", sortOrder = 0),
                Block(type = BlockType.CODE, content = "fun main() {\n    println(\"hi\")\n}", sortOrder = 1)
            )
        )

        val markdown = MarkdownExporter.exportNote(note)

        assertTrue(markdown.contains("$$\n\\frac{a}{b}\n$$"))
        assertTrue(markdown.contains("```text\nfun main() {\n    println(\"hi\")\n}\n```"))
    }

    @Test
    fun exportsEmbeddedImageReferenceFromMediaContent() {
        val note = Note(
            title = "Image",
            blocks = listOf(
                Block(type = BlockType.IMAGE, content = ImageBlockContent.fromMedia(3L, "file:///tmp/captured.jpg"))
            )
        )
        val media = listOf(Media(id = 3L, uri = "file:///tmp/captured.jpg", size = 10L))

        val markdown = MarkdownExporter.exportNote(note, media)

        assertTrue(markdown.contains("![](file:///tmp/captured.jpg)"))
    }

    @Test
    fun exportsLinkedTodoItems() {
        val note = Note(
            id = 5L,
            title = "Todos",
            blocks = listOf(Block(type = BlockType.TODO, sortOrder = 0))
        )
        val todos = listOf(
            TodoItem(id = 1L, noteId = 5L, content = "x"),
            TodoItem(id = 2L, noteId = 5L, content = "y", completedAt = 100L)
        )

        val markdown = MarkdownExporter.exportNote(note = note, todoItems = todos)

        assertTrue(markdown.contains("- [ ] x"))
        assertTrue(markdown.contains("- [x] y"))
    }
}
