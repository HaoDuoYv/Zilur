package com.example.zhilu.data.ai

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 系统提示词的关键约定。
 *
 * 提示词是**逐轮不变的稳定前缀**（prompt cache 的命中前提），因此：
 * - 引用、附件、图片说明这些随消息变化的内容只能挂在 user 消息尾部（见 `buildMessages`）；
 * - 但它们使用的「标签词」仍必须在 system prompt 里解释清楚，否则模型认不出来。
 *
 * 这几条都是"改一句话就静默失效"的类型（不会编译报错、单测也不会红），只能钉在这里。
 */
class AiSystemPromptTest {

    private val prompt = AiAssistantRepositoryImpl.SYSTEM_PROMPT

    @Test
    fun `检索一节要求把问题改写成关键词`() {
        assertTrue("SYSTEM_PROMPT 缺少【检索】一节", prompt.contains("【检索】"))
        assertTrue("【检索】应要求给出关键词而不是整句输入", prompt.contains("关键词"))
    }

    @Test
    fun `引用与图片的标签词仍在 system prompt 里有解释`() {
        // 标签词本身写在 user 消息里（随轮次变化），解释必须留在稳定前缀中
        assertTrue("缺少「【用户引用的知识内容】」的解释", prompt.contains("【用户引用的知识内容】"))
        assertTrue("缺少「【引用的消息】」的解释", prompt.contains("【引用的消息】"))
        assertTrue("缺少「本次附带的图片」的解释", prompt.contains("本次附带的图片"))
    }
}
