package com.example.zhilu.ui.search

/**
 * 搜索框里的 `#标签名` 语法——**输入补全与提交归一共用这一套解析**，
 * 两处各写一份正则迟早会分叉（补全认得、提交认不得，或反过来）。
 *
 * 规则：**以空白分隔的最后一个词，若以 `#` 开头，就是一个标签引用**。
 * 这一条同时覆盖了两种输入：
 * - 整串就是 `#计算机`（词首即串首）；
 * - `论文 #计算机`（词尾即串尾）。
 *
 * 取"最后一个词"而不是"任意位置的 `#`"，是为了别把正文里的井号当语法：
 * `C#` 的最后一个词是 `C#`，不以 `#` 开头 → 不是标签引用。
 * 反过来，形如 `foo#bar` 的粘连写法也不认——`#` 必须是词首，
 * 这样用户想搜带井号的正文时不会被悄悄吞掉。
 *
 * 解析出 token 后，**能不能匹配到标签**由调用方决定：匹配不到时原样保留查询，
 * 回退全文检索（既有语义，`HomeViewModel.search` 依赖这一点）。
 */
object TagQueryParser {

    /** 标签引用的起始字符。 */
    const val PREFIX: Char = '#'

    /** 命中的标签引用：原文里的 `[start, end)` 区间，以及去掉 `#` 后的名字。 */
    data class Token(val name: String, val start: Int, val end: Int)

    /**
     * 取末尾的标签引用；没有就返回 null。
     *
     * 末尾的空白先跳掉：输入法常常在词后补一个空格，那不该让候选当场消失
     * （`论文 #计算 ` 与 `论文 #计算` 必须解析成同一个 token）。
     *
     * 只打了一个 `#` 也算命中（[Token.name] 为空串）——那是"我要挑个标签"的手势，
     * 调用方据此弹出完整候选列表。
     */
    fun trailingToken(query: String): Token? {
        var end = query.length
        while (end > 0 && query[end - 1].isWhitespace()) end--
        if (end == 0) return null

        var start = end
        while (start > 0 && !query[start - 1].isWhitespace()) start--
        if (query[start] != PREFIX) return null
        return Token(name = query.substring(start + 1, end), start = start, end = end)
    }

    /**
     * 摘掉 [token] 之后的查询，并把两侧残余的空白收干净。
     *
     * `论文 #计` → `论文`；`#计` → 空串（此时搜索态由选中标签单独撑着）。
     */
    fun strip(query: String, token: Token): String =
        (query.substring(0, token.start) + query.substring(token.end)).trim()
}
