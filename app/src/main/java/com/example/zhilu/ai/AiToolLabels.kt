package com.example.zhilu.ai

/**
 * 工具名 → 友好中文。
 *
 * 助手页的状态条 / 工具徽章 / 后台通知都要展示同一份文案，所以放在这里共用，
 * 而不是留在 UI 层让通知去 import 界面代码。
 */
fun toolNameLabel(toolName: String): String = when (toolName) {
    "list_notes" -> "读取笔记列表"
    "search_notes" -> "搜索笔记"
    "get_note" -> "读取笔记"
    "create_note" -> "创建笔记"
    "update_note" -> "修改笔记"
    "add_tags" -> "更新标签"
    else -> toolName
}
