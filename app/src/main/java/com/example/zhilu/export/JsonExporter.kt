package com.example.zhilu.export

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.EmphasisTone
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note
import com.example.zhilu.domain.model.Tag
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object JsonExporter {
    data class Backup(
        val notes: List<Note>,
        val media: List<Media>
    )

    fun exportNote(note: Note, media: List<Media> = emptyList()): String = exportNotes(listOf(note), media)

    fun exportNotes(notes: List<Note>, media: List<Media> = emptyList()): String = buildString {
        append("{")
        append("\"version\":1,")
        append("\"exportedAt\":").append(System.currentTimeMillis()).append(",")
        append("\"notes\":")
        appendArray(notes) { appendNote(it) }
        append(",\"media\":")
        appendArray(media) { appendMedia(it) }
        append("}")
    }

    fun importBackup(json: String): Backup {
        val root = Json.parseToJsonElement(json).jsonObject
        val notes = root["notes"] as? JsonArray ?: return Backup(emptyList(), emptyList())
        val parsedNotes = notes.mapNotNull { element ->
            runCatching { element.jsonObject.toNote() }.getOrNull()
        }
        val parsedMedia = root.array("media").mapNotNull { element ->
            runCatching { element.jsonObject.toMedia() }.getOrNull()
        }
        return Backup(notes = parsedNotes, media = parsedMedia)
    }

    fun importNotes(json: String): List<Note> {
        return importBackup(json).notes
    }

    private fun StringBuilder.appendNote(note: Note) {
        append("{")
        appendJsonField("id", note.id).append(",")
        appendJsonField("title", note.title).append(",")
        appendJsonField("createdAt", note.createdAt).append(",")
        appendJsonField("updatedAt", note.updatedAt).append(",")
        appendJsonField("isFavorite", note.isFavorite).append(",")
        append("\"deletedAt\":").append(note.deletedAt ?: "null").append(",")
        append("\"blocks\":")
        // contentBlocks 而非 blocks：导出的笔记来自 getAllNotes()，是有卡片结构的对象，
        // 直接读 blocks 会让「有知识卡片的笔记」在 JSON 备份里块全丢。
        appendArray(note.contentBlocks) { appendBlock(it) }
        append(",\"tags\":")
        appendArray(note.tags) { appendTag(it) }
        append("}")
    }

    private fun StringBuilder.appendBlock(block: Block) {
        append("{")
        appendJsonField("id", block.id).append(",")
        appendJsonField("noteId", block.noteId).append(",")
        appendJsonField("type", block.type.name).append(",")
        appendJsonField("content", block.content).append(",")
        appendJsonField("language", block.language).append(",")
        appendJsonField("sortOrder", block.sortOrder).append(",")
        // 块级语义标记。行内标记（L2）在 content 里，天然随这一行一起走。
        appendJsonField("emphasis", EmphasisTone.toValue(block.emphasis))
        append("}")
    }

    private fun StringBuilder.appendTag(tag: Tag) {
        append("{")
        appendJsonField("id", tag.id).append(",")
        appendJsonField("name", tag.name).append(",")
        appendJsonField("color", tag.color)
        append("}")
    }

    private fun StringBuilder.appendMedia(media: Media) {
        append("{")
        appendJsonField("id", media.id).append(",")
        appendJsonField("uri", media.uri).append(",")
        append("\"width\":").append(media.width ?: "null").append(",")
        append("\"height\":").append(media.height ?: "null").append(",")
        appendJsonField("size", media.size).append(",")
        appendJsonField("createdAt", media.createdAt)
        append("}")
    }

    private fun <T> StringBuilder.appendArray(items: List<T>, appendItem: StringBuilder.(T) -> Unit) {
        append("[")
        items.forEachIndexed { index, item ->
            if (index > 0) append(",")
            appendItem(item)
        }
        append("]")
    }

    private fun StringBuilder.appendJsonField(name: String, value: String): StringBuilder =
        append("\"").append(name).append("\":\"").append(value.escapeJson()).append("\"")

    private fun StringBuilder.appendJsonField(name: String, value: Long): StringBuilder =
        append("\"").append(name).append("\":").append(value)

    private fun StringBuilder.appendJsonField(name: String, value: Int): StringBuilder =
        append("\"").append(name).append("\":").append(value)

    private fun StringBuilder.appendJsonField(name: String, value: Boolean): StringBuilder =
        append("\"").append(name).append("\":").append(value)

    private fun String.escapeJson(): String = buildString {
        this@escapeJson.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

    private fun JsonObject.toNote(): Note {
        val blocks = array("blocks").mapNotNull { element ->
            runCatching { element.jsonObject.toBlock() }.getOrNull()
        }.sortedBy { it.sortOrder }
        val tags = array("tags").mapNotNull { element ->
            runCatching { element.jsonObject.toTag() }.getOrNull()
        }
        return Note(
            id = long("id"),
            title = string("title"),
            createdAt = long("createdAt", System.currentTimeMillis()),
            updatedAt = long("updatedAt", System.currentTimeMillis()),
            isFavorite = boolean("isFavorite"),
            deletedAt = nullableLong("deletedAt"),
            blocks = blocks,
            tags = tags
        )
    }

    private fun JsonObject.toBlock(): Block {
        val typeName = string("type")
        return Block(
            id = long("id"),
            noteId = long("noteId"),
            type = BlockType.entries.firstOrNull { it.name == typeName }
                ?: BlockType.fromValue(int("type", BlockType.TEXT.value)),
            content = string("content"),
            language = string("language"),
            sortOrder = int("sortOrder"),
            // 老备份没有这个字段 → 取 0 → 未标记
            emphasis = EmphasisTone.fromValue(int("emphasis", EmphasisTone.NONE_VALUE))
        )
    }

    private fun JsonObject.toTag(): Tag = Tag(
        id = long("id"),
        name = string("name"),
        color = int("color", 0xFF49454F.toInt())
    )

    private fun JsonObject.toMedia(): Media = Media(
        id = long("id"),
        uri = string("uri"),
        width = nullableInt("width"),
        height = nullableInt("height"),
        size = long("size"),
        createdAt = long("createdAt", System.currentTimeMillis())
    )

    private fun JsonObject.array(name: String): List<JsonElement> =
        (get(name) as? JsonArray)?.jsonArray.orEmpty()

    private fun JsonObject.string(name: String, default: String = ""): String =
        get(name)?.jsonPrimitive?.content ?: default

    private fun JsonObject.long(name: String, default: Long = 0L): Long =
        get(name)?.jsonPrimitive?.longOrNull ?: default

    private fun JsonObject.nullableLong(name: String): Long? =
        get(name)?.jsonPrimitive?.longOrNull

    private fun JsonObject.nullableInt(name: String): Int? =
        get(name)?.jsonPrimitive?.intOrNull

    private fun JsonObject.int(name: String, default: Int = 0): Int =
        get(name)?.jsonPrimitive?.intOrNull ?: default

    private fun JsonObject.boolean(name: String, default: Boolean = false): Boolean =
        get(name)?.jsonPrimitive?.booleanOrNull ?: default
}
