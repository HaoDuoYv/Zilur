package com.example.zhilu.domain.usecase.clipboard

import com.example.zhilu.domain.model.Block
import com.example.zhilu.domain.model.BlockType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Clipboard representation of a [Block], detached from persistent identifiers.
 *
 * [children] is only meaningful for [BlockType.BRANCH] blocks and is kept out of
 * the domain [Block] data class so that the database model stays unchanged.
 */
data class BlockClipboardData(
    val block: Block,
    val children: List<BlockClipboardData> = emptyList()
) {
    val type: BlockType get() = block.type
    val content: String get() = block.content
    val language: String get() = block.language
}

@Serializable
private data class ClipboardBlockDto(
    val version: Int = 1,
    val type: BlockType,
    val content: String,
    val language: String,
    val children: List<ClipboardBlockDto> = emptyList()
)

object BlockClipboardSerializer {
    const val CLIPBOARD_PREFIX = "zhilu-block:"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun toJson(data: BlockClipboardData): String {
        val dto = data.toDto()
        return CLIPBOARD_PREFIX + json.encodeToString(ClipboardBlockDto.serializer(), dto)
    }

    fun toJson(block: Block, children: List<Block> = emptyList()): String {
        return toJson(
            BlockClipboardData(
                block = block,
                children = children.map { BlockClipboardData(it) }
            )
        )
    }

    fun isBlockClipboardData(raw: String): Boolean = raw.startsWith(CLIPBOARD_PREFIX)

    fun fromJson(jsonString: String): BlockClipboardData? {
        if (!jsonString.startsWith(CLIPBOARD_PREFIX)) return null
        val payload = jsonString.removePrefix(CLIPBOARD_PREFIX)
        return try {
            json.decodeFromString(ClipboardBlockDto.serializer(), payload).toDomain()
        } catch (_: Exception) {
            null
        }
    }
}

private fun BlockClipboardData.toDto(): ClipboardBlockDto = ClipboardBlockDto(
    type = block.type,
    content = block.content,
    language = block.language,
    children = children.map { it.toDto() }
)

private fun ClipboardBlockDto.toDomain(): BlockClipboardData = BlockClipboardData(
    block = Block(
        id = 0L,
        noteId = 0L,
        cardId = null,
        type = type,
        content = content,
        language = language,
        sortOrder = 0,
        parentBranchId = null
    ),
    children = children.map { it.toDomain() }
)
