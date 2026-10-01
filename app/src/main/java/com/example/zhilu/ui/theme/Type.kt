package com.example.zhilu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 知录字阶（精炼纸墨）。
 *
 * 衬线只用于 4 类标题（[ZhiLuType.noteTitle] / [pageTitle] / [cardTitle] / [sectionTitle]），
 * 其余一律无衬线；正文行高放宽到 26sp 以保证长文阅读舒适。
 *
 * 组件应直接使用 [ZhiLuType] 的语义样式，而非 M3 的模糊槽位名；
 * [Typography] 保留并按本表映射，让 M3 内置组件自动跟随新字阶。
 */
object ZhiLuType {
    // —— 衬线标题 ——
    val noteTitle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.2).sp
    )
    val pageTitle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    )
    val cardTitle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )
    val sectionTitle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    )

    // —— 无衬线界面与正文 ——
    val rowTitle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp
    )
    val body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    )
    val bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    )
    val meta = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    )
    val label = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.6.sp
    )
    val chip = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    )
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    )
}

val Typography = Typography(
    displayLarge = ZhiLuType.noteTitle,
    displayMedium = ZhiLuType.noteTitle,
    displaySmall = ZhiLuType.pageTitle,
    headlineLarge = ZhiLuType.noteTitle,
    headlineMedium = ZhiLuType.pageTitle,
    headlineSmall = ZhiLuType.pageTitle,
    titleLarge = ZhiLuType.pageTitle,
    titleMedium = ZhiLuType.cardTitle,
    titleSmall = ZhiLuType.sectionTitle,
    bodyLarge = ZhiLuType.body,
    bodyMedium = ZhiLuType.bodySmall,
    bodySmall = ZhiLuType.meta,
    labelLarge = ZhiLuType.bodySmall,
    labelMedium = ZhiLuType.chip,
    labelSmall = ZhiLuType.label
)