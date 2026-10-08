package com.example.zhilu.ui.reminder

/** 提醒「延后」的两个快捷选项（延后时间由 ViewModel 按当前时刻计算）。 */
enum class SnoozeOption(val label: String) {
    OneHourLater("1 小时后"),
    TomorrowMorning("明天上午")
}
