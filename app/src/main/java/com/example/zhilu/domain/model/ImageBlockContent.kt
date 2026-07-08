package com.example.zhilu.domain.model

object ImageBlockContent {
    private const val separator = "|"

    fun fromMedia(id: Long, uri: String): String = "$id$separator$uri"

    fun mediaId(content: String): Long? {
        val candidate = if (content.contains(separator)) {
            content.substringBefore(separator)
        } else {
            content
        }
        return candidate.toLongOrNull()
    }

    fun displayUri(content: String): String {
        val id = mediaId(content)
        return if (id != null && content.startsWith("$id$separator")) {
            content.substringAfter(separator)
        } else {
            content
        }
    }

    fun resolveUri(content: String, mediaById: Map<String, Media>): String {
        val id = mediaId(content)?.toString()
        return id?.let { mediaById[it]?.uri } ?: displayUri(content)
    }
}
