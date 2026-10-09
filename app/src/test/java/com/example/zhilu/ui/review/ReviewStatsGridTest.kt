package com.example.zhilu.ui.review

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 复习概览统计格的纯函数：毕业率文案。
 *
 * （近 7 天 / 今日 / 已掌握三个数字的口径测试在 `ReviewCenterViewModelTest`，
 * 这里只钉格式化。）
 */
class ReviewStatsGridTest {

    @Test
    fun `ratio rounds to the nearest whole percent`() {
        assertEquals("33%", formatRatio(1, 3))
        assertEquals("67%", formatRatio(2, 3))
        assertEquals("50%", formatRatio(1, 2))
    }

    @Test
    fun `nothing graduated is a real zero`() {
        assertEquals("0%", formatRatio(0, 1))
    }

    @Test
    fun `no plans at all gets a dash rather than a fake zero`() {
        // 没有分母时「0%」是句假话（调用方本就不该显示这一项，这是兜底）
        assertEquals("—", formatRatio(0, 0))
    }
}
