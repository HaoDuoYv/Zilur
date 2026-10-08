package com.example.zhilu.data.ai

import com.example.zhilu.domain.ai.model.AiRef

/**
 * 组装发给模型的 **user 消息正文**（纯函数，便于单测）。
 *
 * ## 为什么引用/引用消息要进 user 消息，而不是 system prompt
 *
 * 曾经的实现把引用内容拼在超长 system prompt 的末尾，模型常把它当背景噪音，
 * 与用户问题的关联很弱（真机反馈「引用后 AI 识别不到引用意图」）。
 * 主流做法是把上下文**与提问放在同一条 user 消息里**（ChatGPT 的附件、Cursor 的
 * `@file`、IM 的引用回复都是这个形态）：
 * - 关联性强：模型知道这段内容是"这条消息"的一部分；
 * - 可追溯：消息落库后，历史回看与后续追问轮都能重放同一条消息的上下文。
 *
 * 组装顺序固定为：**引用内容 → 引用的消息 → 用户问题 → 附件内容**。
 * 问题跟在上下文后面（贴近生成位置），附件保持既有语义放在最后。
 */
object AiUserMessageText {

    /** 单条消息里全部引用快照的总字符上限（超出按序截断，防止多条引用撑爆请求）。 */
    const val REF_TOTAL_MAX_CHARS = 24_000

    /** 引用消息（引用回复）的字符上限。 */
    const val QUOTED_MAX_CHARS = 1_000

    const val TRUNCATED_SUFFIX = "…（已截断）"
    const val OMITTED_SUFFIX = "…（其余引用内容已省略）"
    const val UNAVAILABLE = "（引用的内容已不可用）"

    /**
     * @param base 用户输入的正文
     * @param fileText 文本附件内容（既有语义，放在最后）
     * @param refs 本条消息附带的引用（读各自的 [AiRef.snapshot]）
     * @param quotedText 被引用消息的正文（同会话里按 `quotedMessageId` 解析；null = 无引用）
     */
    fun build(
        base: String,
        fileText: String?,
        refs: List<AiRef>,
        quotedText: String?
    ): String {
        val sections = mutableListOf<String>()
        buildRefsSection(refs)?.let(sections::add)
        buildQuotedSection(quotedText)?.let(sections::add)
        if (base.isNotBlank()) sections.add(base)
        if (!fileText.isNullOrBlank()) sections.add("【附件内容】\n$fileText")
        return sections.joinToString("\n\n")
    }

    private fun buildRefsSection(refs: List<AiRef>): String? {
        if (refs.isEmpty()) return null
        val builder = StringBuilder("【用户引用的知识内容】")
        var remaining = REF_TOTAL_MAX_CHARS
        var truncated = false
        refs.forEach { ref ->
            if (remaining <= 0) {
                truncated = true
                return@forEach
            }
            val text = ref.snapshot.trim().ifEmpty { UNAVAILABLE }
            val piece = if (text.length <= remaining) {
                text
            } else {
                truncated = true
                text.take(remaining)
            }
            remaining -= piece.length
            builder.append("\n\n").append(piece)
        }
        if (truncated) builder.append("\n\n").append(OMITTED_SUFFIX)
        return builder.toString()
    }

    private fun buildQuotedSection(quotedText: String?): String? {
        val text = quotedText?.trim().orEmpty()
        if (text.isEmpty()) return null
        val piece = if (text.length <= QUOTED_MAX_CHARS) {
            text
        } else {
            text.take(QUOTED_MAX_CHARS) + TRUNCATED_SUFFIX
        }
        return "【引用的消息】\n$piece"
    }
}
