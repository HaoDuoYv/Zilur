package com.example.zhilu.export

import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.ImageBlockContent
import com.example.zhilu.domain.model.Media
import com.example.zhilu.domain.model.Note

/**
 * 挑出被这些笔记**真正引用**的媒体。
 *
 * 三处导出（单篇分享 / 全量备份 / 选中项备份）都必须先把媒体收窄再交给 [DtkExporter]：
 * `MediaRepository.getAllMedia()` 给的是全库媒体，直接塞进包会让一份单篇分享
 * 背上整个图库。
 *
 * 匹配口径是**精确匹配**而不是子串：优先按块里登记的 mediaId，其次按块内容是否内嵌
 * 该媒体的 uri。早期在分享路径里用的是 `content.contains(media.id.toString())` ——
 * 一旦某个 uri 里碰巧出现了相同的数字，就会误收无关媒体，而真正引用的反而可能漏掉。
 *
 * 纯函数，不依赖 Android，可直接单测。
 */
fun collectReferencedMedia(notes: List<Note>, allMedia: List<Media>): List<Media> {
    if (notes.isEmpty() || allMedia.isEmpty()) return emptyList()
    val imageContents = notes.asSequence()
        .flatMap { it.contentBlocks.asSequence() }
        .filter { it.type == BlockType.IMAGE }
        .map { it.content }
        .toList()
    if (imageContents.isEmpty()) return emptyList()

    val declaredIds = imageContents.mapNotNull { ImageBlockContent.mediaId(it) }.toSet()
    return allMedia.filter { media ->
        media.id in declaredIds ||
            // uri 为空串时 `contains("")` 恒为 true，会把整个图库都收进来
            (media.uri.isNotBlank() && imageContents.any { it.contains(media.uri) })
    }
}
