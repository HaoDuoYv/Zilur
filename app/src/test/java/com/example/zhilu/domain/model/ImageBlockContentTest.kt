package com.example.zhilu.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageBlockContentTest {
    @Test
    fun storesMediaIdAndUriInOneEmbeddableReference() {
        val content = ImageBlockContent.fromMedia(id = 42L, uri = "file:///tmp/a.jpg")

        assertEquals("42|file:///tmp/a.jpg", content)
        assertEquals(42L, ImageBlockContent.mediaId(content))
        assertEquals("file:///tmp/a.jpg", ImageBlockContent.displayUri(content))
    }

    @Test
    fun keepsOldUriOnlyImageBlocksDisplayable() {
        val content = "file:///tmp/legacy.jpg"

        assertNull(ImageBlockContent.mediaId(content))
        assertEquals("file:///tmp/legacy.jpg", ImageBlockContent.displayUri(content))
    }

    private val mediaById = mapOf(
        "3" to Media(id = 3L, uri = "file:///tmp/three.jpg"),
        "7" to Media(id = 7L, uri = "file:///tmp/seven.jpg")
    )

    @Test
    fun resolveMedia_prefersRegisteredMediaId() {
        val resolved = ImageBlockContent.resolveMedia("7|file:///tmp/seven.jpg", mediaById)
        assertEquals(7L, resolved?.id)
    }

    /**
     * 回归：AI 助手早期写入的图片块存的是裸 URI（没有 mediaId 前缀）。
     * 若只认 mediaId，这类图片在导出/分享时会被静默丢掉——必须能按内嵌 URI 反查到媒体。
     */
    @Test
    fun resolveMedia_fallsBackToUriForBareUriBlocks() {
        val resolved = ImageBlockContent.resolveMedia("file:///tmp/seven.jpg", mediaById)
        assertEquals(7L, resolved?.id)
    }

    @Test
    fun resolveMedia_returnsNullWhenNothingMatches() {
        assertNull(ImageBlockContent.resolveMedia("file:///tmp/missing.jpg", mediaById))
    }
}
