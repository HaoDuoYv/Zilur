package com.example.zhilu.data.ai

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 钉住 SSE 流的「结束判定」与「产出校验」。
 *
 * 回归背景：`chatStream` 原先用 `readUtf8Line() ?: break` 收尾，连接被提前关闭时
 * 循环正常退出，空文本被当成成功返回 —— 真机表现为一条 0 长度的 ASSISTANT 消息
 * 加一个空白气泡，且界面不报错。现在提前断开与空产出都必须抛错走失败路径。
 */
class SseStreamAssemblerTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun assembler() = SseStreamAssembler(json)

    /** 逐行喂给聚合器，收集本次产出的增量文本。 */
    private fun collect(a: SseStreamAssembler, vararg lines: String): String {
        val emitted = StringBuilder()
        lines.forEach { a.accept(it)?.let(emitted::append) }
        return emitted.toString()
    }

    /** 只带正文增量的分片行。 */
    private fun textLine(content: String) =
        """data: {"choices":[{"delta":{"content":"$content"}}]}"""

    /** 只带结束信号的收尾分片行（正文与 finish_reason 也可能同片，见下方用例）。 */
    private fun stopLine(reason: String) =
        """data: {"choices":[{"delta":{},"finish_reason":"$reason"}]}"""

    @Test
    fun `分片逐段拼接 且收到 DONE 哨兵才算正常结束`() {
        val a = assembler()
        val emitted = collect(
            a,
            """data: {"choices":[{"delta":{"role":"assistant"}}]}""",
            textLine("你好"),
            textLine("，世界"),
            "data: [DONE]"
        )
        assertEquals("你好，世界", emitted)
        assertTrue(a.terminated)
        assertEquals("你好，世界", a.finish().text)
    }

    @Test
    fun `没有结束信号就断开时抛错 —— 不再把提前 EOF 当成功`() {
        val a = assembler()
        collect(a, textLine("半截回答"))
        assertFalse(a.terminated)
        val e = assertThrows(LlmApiException::class.java) { a.finish() }
        assertTrue("错误要说清是中断，供用户重试", e.message!!.contains("中断"))
    }

    @Test
    fun `finish_reason 可替代 DONE 哨兵作为结束信号`() {
        val a = assembler()
        collect(a, stopLine("stop"))
        assertTrue(a.terminated)
        // 空文本但正常结束且无工具调用，同样不许当成功（见下一条用例）
        assertThrows(LlmApiException::class.java) { a.finish() }
    }

    @Test
    fun `正常结束但一字未出时抛错 —— 不再产出 0 长度回复`() {
        val a = assembler()
        collect(a, "data: [DONE]")
        val e = assertThrows(LlmApiException::class.java) { a.finish() }
        assertTrue(e.message!!.contains("没有返回任何内容"))
    }

    @Test
    fun `被 max_tokens 截断时抛错 且提示能照着调参`() {
        // 真机实证：推理模型（reasoning_content）思考 token 也计入 max_tokens，
        // 思考吃满额度后正文只剩开头几个字，形如「以下是」。
        val a = assembler()
        collect(
            a,
            textLine("以下是"),
            """data: {"choices":[{"delta":{},"finish_reason":"length"}]}""",
            "data: [DONE]"
        )
        val e = assertThrows(LlmApiException::class.java) { a.finish() }
        assertTrue("提示里要给出可执行的下一步", e.message!!.contains("max_tokens"))
    }

    @Test
    fun `截断且一字未出时 优先报截断而不是空回复`() {
        val a = assembler()
        collect(a, """data: {"choices":[{"delta":{},"finish_reason":"length"}]}""", "data: [DONE]")
        val e = assertThrows(LlmApiException::class.java) { a.finish() }
        assertTrue(e.message!!.contains("max_tokens"))
    }

    @Test
    fun `正文与 finish_reason 同片时 两者都生效`() {
        val a = assembler()
        collect(a, """data: {"choices":[{"delta":{"content":"完整"},"finish_reason":"stop"}]}""")
        assertTrue(a.terminated)
        assertEquals("完整", a.finish().text)
    }

    @Test
    fun `只有工具调用没有正文时不视为空回复`() {
        val a = assembler()
        val emitted = collect(
            a,
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"search_notes","arguments":"{\"query\":"}}]}}]}""",
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"二次型\"}"}}]},"finish_reason":"tool_calls"}]}""",
            "data: [DONE]"
        )
        assertEquals("工具调用轮不该往外吐文本", "", emitted)
        val outcome = a.finish()
        assertEquals("", outcome.text)
        assertEquals(1, outcome.toolCalls.size)
        assertEquals("call_1", outcome.toolCalls[0].id)
        assertEquals("search_notes", outcome.toolCalls[0].function.name)
        assertEquals("""{"query":"二次型"}""", outcome.toolCalls[0].function.arguments)
    }

    @Test
    fun `工具调用分片按 index 聚合 且乱序到达也按序输出`() {
        val a = assembler()
        collect(
            a,
            """data: {"choices":[{"delta":{"tool_calls":[{"index":1,"id":"call_2","function":{"name":"get_note","arguments":"{}"}}]}}]}""",
            """data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"list_notes","arguments":"{}"}}]}}]}""",
            "data: [DONE]"
        )
        val outcome = a.finish()
        assertEquals(listOf("call_1", "call_2"), outcome.toolCalls.map { it.id })
    }

    @Test
    fun `流内错误对象直接透出服务端原话`() {
        val a = assembler()
        val e = assertThrows(LlmApiException::class.java) {
            a.accept("""data: {"error":{"message":"Free quota exhausted. Please add funds."}}""")
        }
        assertEquals("Free quota exhausted. Please add funds.", e.message)
    }

    @Test
    fun `忽略注释行 空行与空 data 行`() {
        val a = assembler()
        val emitted = collect(
            a,
            ": keep-alive",
            "",
            "data:",
            textLine("答"),
            "data: [DONE]"
        )
        assertEquals("答", emitted)
        assertEquals("答", a.finish().text)
    }
}
