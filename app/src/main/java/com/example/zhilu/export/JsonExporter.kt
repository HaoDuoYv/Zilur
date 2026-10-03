package com.example.zhilu.export

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.EmphasisTone
import com.example.zhilu.domain.model.KnowledgeCard
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
    /**
     * 备份内容。
     *
     * [mediaData] 是 `媒体 id → base64 data URL` 的映射，与 [media] 分开是刻意的：
     * base64 是**传输形态**，塞进 domain 的 `Media` 会让"媒体"这个概念背着几 MB 字符串到处跑。
     */
    data class Backup(
        val notes: List<Note>,
        val media: List<Media>,
        val mediaData: Map<Long, String> = emptyMap()
    )

    fun exportNote(
        note: Note,
        media: List<Media> = emptyList(),
        dtkVersion: Int? = null
    ): String = exportNotes(listOf(note), media, emptyMap(), dtkVersion)

    /**
     * @param mediaData 媒体 id → base64 data URL。**JSON 备份必须传**：不内嵌字节的话，
     *   备份文件里只剩一个指向本机内部存储的 uri，换台设备/清过数据后就永远恢复不出图
     *   （真机反馈"json 备份导入时图片无法导入"）。`.dtk` 不传 —— 它把图片作为文件放进 zip。
     * @param dtkVersion 只有 `.dtk` 传：规范要求它的 note.json 带这个字段，导入端按它拒收高版本。
     *   整体备份是另一个格式，版本号用顶层的 `version`。
     */
    fun exportNotes(
        notes: List<Note>,
        media: List<Media> = emptyList(),
        mediaData: Map<Long, String> = emptyMap(),
        dtkVersion: Int? = null
    ): String = buildString {
        append("{")
        if (dtkVersion != null) append("\"dtkVersion\":").append(dtkVersion).append(",")
        append("\"version\":1,")
        append("\"exportedAt\":").append(System.currentTimeMillis()).append(",")
        append("\"notes\":")
        appendArray(notes) { appendNote(it) }
        append(",\"media\":")
        appendArray(media) { appendMedia(it, mediaData[it.id]) }
        append("}")
    }

    fun importBackup(json: String): Backup {
        val root = Json.parseToJsonElement(json).jsonObject
        val notes = root["notes"] as? JsonArray ?: return Backup(emptyList(), emptyList())
        val parsedNotes = notes.mapNotNull { element ->
            runCatching { element.jsonObject.toNote() }.getOrNull()
        }
        val mediaElements = root.array("media").mapNotNull { it as? JsonObject }
        val parsedMedia = mediaElements.mapNotNull { element ->
            runCatching { element.toMedia() }.getOrNull()
        }
        val mediaData = mediaElements.mapNotNull { element ->
            val data = element["data"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val id = element.nullableLong("id") ?: return@mapNotNull null
            id to data
        }.toMap()
        return Backup(notes = parsedNotes, media = parsedMedia, mediaData = mediaData)
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
        // **小节结构必须原样写下来**。只写扁平的 contentBlocks 会把"六小节的笔记"
        // 备份成一串没有边界的块，恢复后 hydrate 把它们全并进一张卡 —— 这正是
        // .dtk 导入"小节全丢"的根因，JSON 备份当时也一样（真机反馈）。
        if (note.cards.isNotEmpty()) {
            append("\"cards\":")
            appendArray(note.cards) { appendCard(it) }
            append(",\"blocks\":[]")
        } else {
            append("\"blocks\":")
            // contentBlocks 而非 blocks：导出的笔记来自 getAllNotes()，是有卡片结构的对象，
            // 直接读 blocks 会让「有知识卡片的笔记」在 JSON 备份里块全丢。
            appendArray(note.contentBlocks) { appendBlock(it) }
        }
        append(",\"tags\":")
        appendArray(note.tags) { appendTag(it) }
        append("}")
    }

    private fun StringBuilder.appendCard(card: KnowledgeCard) {
        append("{")
        appendJsonField("title", card.title).append(",")
        // null = 用户没改过身份色，按序号回退；写 null 才能保住这个语义
        append("\"accent\":").append(card.accent ?: "null").append(",")
        append("\"blocks\":")
        appendArray(card.blocks) { appendBlock(it) }
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
        // 分支子块的父指针：不写它，恢复后子块会全部变成顶层块
        append("\"parentBranchId\":").append(block.parentBranchId ?: "null").append(",")
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

    private fun StringBuilder.appendMedia(media: Media, data: String? = null) {
        append("{")
        appendJsonField("id", media.id).append(",")
        appendJsonField("uri", media.uri).append(",")
        append("\"width\":").append(media.width ?: "null").append(",")
        append("\"height\":").append(media.height ?: "null").append(",")
        appendJsonField("size", media.size).append(",")
        appendJsonField("createdAt", media.createdAt)
        // 图片字节内嵌在备份里（见 exportNotes 的说明）。没有数据的（例如 .dtk 路径）不写这个字段。
        if (!data.isNullOrBlank()) {
            append(",")
            appendJsonField("data", data)
        }
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
        val cards = array("cards").mapNotNull { element ->
            runCatching { element.jsonObject.toCard() }.getOrNull()
        }
        val blocks = array("blocks").mapNotNull { element ->
            runCatching { element.jsonObject.toBlock() }.getOrNull()
        }.sortedBy { it.sortOrder }
        return Note(
            id = long("id"),
            title = string("title"),
            createdAt = long("createdAt", System.currentTimeMillis()),
            updatedAt = long("updatedAt", System.currentTimeMillis()),
            isFavorite = boolean("isFavorite"),
            deletedAt = nullableLong("deletedAt"),
            // 有 cards 时 blocks 必须留空：Note.contentBlocks 的语义是"blocks 非空就只用 blocks"
            blocks = if (cards.isEmpty()) blocks else emptyList(),
            cards = cards,
            tags = array("tags").mapNotNull { element ->
                runCatching { element.jsonObject.toTag() }.getOrNull()
            }
        )
    }

    private fun JsonObject.toCard(): KnowledgeCard = KnowledgeCard(
        title = string("title"),
        // 缺失或 null → null（"用户没改过"），不要写成 0
        accent = nullableInt("accent"),
        blocks = array("blocks").mapNotNull { element ->
            runCatching { element.jsonObject.toBlock() }.getOrNull()
        }.sortedBy { it.sortOrder }
    )

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
            // 老备份没有这些字段 → 空 / 未标记，语义与"没设过"一致
            parentBranchId = nullableLong("parentBranchId"),
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
