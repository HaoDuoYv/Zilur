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

    /**
     * 找出图片块真正引用的媒体记录。
     *
     * 优先按块里登记的 mediaId 查；查不到再退回「块内容里内嵌了哪个媒体的 URI」。
     * 兜底是必需的：AI 助手早期写入的图片块存的是裸 URI（没有 mediaId 前缀），
     * 只认 mediaId 会让这类图片在导出/分享时被静默丢掉。
     */
    fun resolveMedia(content: String, mediaById: Map<String, Media>): Media? {
        val id = mediaId(content)?.toString()
        return id?.let { mediaById[it] } ?: mediaById.values.firstOrNull { content.contains(it.uri) }
    }
}
