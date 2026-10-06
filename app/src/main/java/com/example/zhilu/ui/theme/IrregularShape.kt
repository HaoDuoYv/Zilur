package com.example.zhilu.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * 「手作圆角」——参考仓库 `AnimalIslandUI` 里最有个性的一招。
 *
 * 它的观感不是来自"画曲线"，而是来自**四个角半径刻意不相等**：参考仓库的
 * `CardType.TITLE` 是 40 / 35 / 45 / 38dp。远看是个圆角块，近看"不是用尺子量的" ——
 * 那点不规整正是这套 UI 显得手作、显得像游戏界面的原因。
 *
 * ## 曾经走错的一版（别再走回去）
 *
 * 第一版我照参考仓库 `Modal.kt` 的 `AnimalModalShape` 自己写了一整圈三次贝塞尔
 * （四条边带弧度 + 四角不等），结果**四个角渲染成内凹的尖角**，卡片像被咬了一口。
 * 原因是那段代码里的圆弧方向约定我没有完全还原，而 `GenericShape` 画错方向不会报错，
 * 只会安静地画出一个错形状 —— 真机截图才发现。
 *
 * 现在改用 Compose 自己的 [RoundedCornerShape] 逐角指定：**观感来源相同**
 * （不等的角半径），但路径由框架生成，不存在方向写反的可能。
 * 参考仓库"边缘也微微外凸"那一点没有还原 —— 它对观感贡献很小、对风险贡献很大。
 */
fun animalHandDrawnShape(@Suppress("UNUSED_PARAMETER") density: Density): Shape =
    RoundedCornerShape(
        topStart = 30.dp,
        topEnd = 24.dp,
        bottomEnd = 34.dp,
        bottomStart = 26.dp
    )
