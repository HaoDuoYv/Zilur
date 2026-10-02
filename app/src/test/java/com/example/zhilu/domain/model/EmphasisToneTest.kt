package com.example.zhilu.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmphasisToneTest {

    @Test
    fun `持久化值稳定且从 1 开始`() {
        // 0 保留给"未标记"，因此角色必须从 1 起，且不可改序
        assertEquals(1, EmphasisTone.KEY.value)
        assertEquals(2, EmphasisTone.IDEA.value)
        assertEquals(3, EmphasisTone.WARN.value)
        assertEquals(4, EmphasisTone.TODO.value)
        assertNull(EmphasisTone.fromValue(EmphasisTone.NONE_VALUE))
    }

    @Test
    fun `从名字解析时忽略大小写`() {
        // AI 工具传的是小写（key / idea / warn / todo），领域枚举名是大写
        for (tone in EmphasisTone.entries) {
            assertEquals(tone, EmphasisTone.fromName(tone.name.lowercase()))
            assertEquals(tone, EmphasisTone.fromName(tone.name))
            assertEquals(tone, EmphasisTone.fromName(" ${tone.name.lowercase()} "))
        }
        assertNull("none 不是角色，应落到 null（= 不标记）", EmphasisTone.fromName("none"))
        assertNull(EmphasisTone.fromName("unknown"))
    }

    @Test
    fun `角色字母与领域值一一对应`() {
        assertEquals(EmphasisTone.KEY, EmphasisTone.fromCode('k'))
        assertEquals(EmphasisTone.IDEA, EmphasisTone.fromCode('i'))
        assertEquals(EmphasisTone.WARN, EmphasisTone.fromCode('w'))
        assertEquals(EmphasisTone.TODO, EmphasisTone.fromCode('t'))
        assertNull(EmphasisTone.fromCode('K'))
    }

    @Test
    fun `toValue 把未标记写成 0`() {
        assertEquals(0, EmphasisTone.toValue(null))
        assertEquals(EmphasisTone.WARN.value, EmphasisTone.toValue(EmphasisTone.WARN))
    }
}
