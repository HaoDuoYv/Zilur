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
}
