package com.example.zhilu.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `AiSettings` 的服务解析与回退链。
 *
 * 这两件事都是**静默失败**型的：解析错一个分支，表现是"切了没反应"或"当前使用：X
 * 但请求发到了 Y"，而不会报错。所以门槛写死在这里。
 */
class AiSettingsTest {

    private fun service(
        id: String,
        enabled: Boolean = true,
        configured: Boolean = true
    ) = AiService(
        id = id,
        name = id,
        endpoint = if (configured) "https://$id.example.com/v1" else "",
        apiKey = if (configured) "sk-$id" else "",
        model = if (configured) "model-$id" else "",
        enabled = enabled
    )

    @Test
    fun resolveActivePrefersTheExplicitChoice() {
        val settings = AiSettings(
            services = listOf(service("a"), service("b"), service("c")),
            activeId = "b"
        )
        assertEquals("b", settings.resolveActive()?.id)
    }

    @Test
    fun resolveActiveFallsBackToFirstEnabledWhenActiveIsDeleted() {
        // 用户删掉了当前使用的服务 → activeId 变成悬空引用。
        // 若不回退，界面会显示"当前使用：某某"而实际一个请求都发不出去。
        val settings = AiSettings(
            services = listOf(service("a"), service("c")),
            activeId = "deleted"
        )
        assertEquals("a", settings.resolveActive()?.id)
    }

    @Test
    fun resolveActiveSkipsDisabledAndUnconfiguredOnes() {
        val settings = AiSettings(
            services = listOf(
                service("a", enabled = false),
                service("b", configured = false),
                service("c")
            ),
            activeId = "a"
        )
        assertEquals("c", settings.resolveActive()?.id)
    }

    @Test
    fun resolveActiveIsNullWhenNothingIsUsable() {
        val settings = AiSettings(
            services = listOf(service("a", enabled = false), service("b", configured = false)),
            activeId = "a"
        )
        assertNull(settings.resolveActive())
        assertFalse(settings.hasUsable)
        assertTrue(settings.hasAny)
    }

    @Test
    fun fallbackChainWrapsAroundAndExcludesTheActiveOne() {
        // 顺序：b 是当前使用 → 先试它后面的 c、d，再绕回它前面的 a
        val settings = AiSettings(
            services = listOf(service("a"), service("b"), service("c"), service("d")),
            activeId = "b"
        )
        assertEquals(listOf("c", "d", "a"), settings.fallbackChain().map { it.id })
    }

    @Test
    fun fallbackChainIsEmptyWhenDisabled() {
        val settings = AiSettings(
            services = listOf(service("a"), service("b")),
            activeId = "a",
            fallbackOnFailure = false
        )
        assertTrue(settings.fallbackChain().isEmpty())
    }

    @Test
    fun fallbackChainIsEmptyWithOnlyOneService() {
        val settings = AiSettings(services = listOf(service("a")), activeId = "a")
        assertTrue(settings.fallbackChain().isEmpty())
    }

    @Test
    fun fallbackChainOnlyContainsUsableServices() {
        val settings = AiSettings(
            services = listOf(
                service("a"),
                service("b"),
                service("c", enabled = false),
                service("d", configured = false)
            ),
            activeId = "b"
        )
        assertEquals(listOf("a"), settings.fallbackChain().map { it.id })
    }

    @Test
    fun displayNameFallsBackThroughNameThenModel() {
        assertEquals("我的 AI", service("x").copy(name = "我的 AI").displayName)
        assertEquals("model-x", service("x").copy(name = "").displayName)
        assertEquals("未命名", service("x").copy(name = "", model = "").displayName)
    }

    @Test
    fun serviceWithoutModelIsNotConfigured() {
        // 模型是必填项：一个只会单模型的服务没有"用默认模型"这回事
        // （需求明确不要预设默认模型）。
        assertFalse(service("x").copy(model = "").isConfigured)
    }
}
