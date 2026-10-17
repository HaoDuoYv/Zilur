package com.example.zhilu.data.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 服务地址的规范化。用户会填出各种写法，而拼错一个斜杠就是 404，
 * 表现像"服务器挂了"，所以这里逐条钉住。
 */
class CloudApiClientTest {

    @Test
    fun addsHttpSchemeWhenMissing() {
        assertEquals(
            "http://192.168.1.10:8787",
            CloudApiClient.normalizeServerUrl("192.168.1.10:8787")
        )
        assertEquals("http://example.com", CloudApiClient.normalizeServerUrl("example.com"))
    }

    @Test
    fun keepsExistingScheme() {
        assertEquals(
            "https://zhilu.example.com",
            CloudApiClient.normalizeServerUrl("https://zhilu.example.com")
        )
        assertEquals(
            "http://example.com",
            CloudApiClient.normalizeServerUrl("HTTP://example.com")
        )
    }

    @Test
    fun stripsTrailingSlashes() {
        // 留一个末尾斜杠，拼出来就是 `//api/health` —— 部分反代会 404
        assertEquals(
            "https://example.com",
            CloudApiClient.normalizeServerUrl("https://example.com/")
        )
        assertEquals(
            "https://example.com/api",
            CloudApiClient.normalizeServerUrl("  https://example.com/api///  ")
        )
    }

    @Test
    fun blankMeansDisabled() {
        assertEquals("", CloudApiClient.normalizeServerUrl(""))
        assertEquals("", CloudApiClient.normalizeServerUrl("   "))
    }
}
