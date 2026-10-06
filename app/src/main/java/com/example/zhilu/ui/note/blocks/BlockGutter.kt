package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.EmphasisTone
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.LocalAccessibleEmphasis
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.emphasisToneColor
import com.example.zhilu.ui.theme.inkOnFill
import com.example.zhilu.ui.theme.LocalThemePalette

/** gutter 宽度。序号与标记圆点都落在这条固定列里，内容区因此左缘对齐。 */
val BlockGutterWidth = 28.dp

/**
 * 小点的左侧 gutter：序号 + 块级语义标记入口。
 *
 * 为什么把「标记」放在 gutter 而不是块内右上角（设计文档 §7）：
 * 块内右上角是内容区，放把手/圆点会挤占正文；gutter 本来就是"结构列"，
 * 序号在这里、标记也在这里，一列管到底。
 *
 * 序号同时承担三重含义（§4.2、§4.6）：
 * - 未激活：`meta` 字号、卡片强调色 40%；
 * - 已标记：用该语义角色的颜色；
 * - 当前编辑块：实心圆（焦点反馈的第一重）。
 */
@Composable
fun BlockGutter(
    label: String,
    isActive: Boolean,
    emphasis: EmphasisTone?,
    accent: Color,
    darkTheme: Boolean,
    /** 只有编辑态、且卡片已聚焦时才可交互。 */
    canActivate: Boolean,
    onActivate: () -> Unit,
    /** 只有编辑态、且本块已激活时才露出标记入口。 */
    showMarker: Boolean,
    onSetEmphasis: (EmphasisTone?) -> Unit,
    modifier: Modifier = Modifier
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val toneColor = emphasis?.let {
        emphasisToneColor(it, darkTheme, LocalAccessibleEmphasis.current, LocalThemePalette.current)
    }

    Column(
        modifier = modifier.width(BlockGutterWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isActive -> accent
                        toneColor != null -> toneColor.copy(alpha = 0.16f)
                        else -> Color.Transparent
                    }
                )
                // 点序号 = 激活本块。激活放在 gutter 而不是块内：块体外面是滑动删除手势，
                // 内层再抢一次点击会让横向滑动错乱（真机踩过）。
                .then(
                    if (canActivate) {
                        Modifier.clickable(onClick = onActivate)
                    } else {
                        Modifier
                    }
                )
                .semantics {
                    contentDescription = if (isActive) "第 $label 条，已激活" else "第 $label 条"
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = ZhiLuType.meta,
                textAlign = TextAlign.Center,
                color = when {
                    // 激活态用**当前主题的主色对比色**，不是写死的白。
                    // 实测写死白的代价：深色模式下动森的嫩叶绿上只有 **1.68**、纸墨的
                    // 浅墨蓝上是 2.01 —— 序号直接糊掉；换成 onPrimary 后是 8.25 / 7.19。
                    // （浅色模式下两者恰好都是白，所以这个 bug 只在深色模式露出来。）
                    isActive -> inkOnFill(accent)
                    toneColor != null -> toneColor
                    else -> accent.copy(alpha = 0.4f)
                }
            )
        }

        if (showMarker) {
            Spacer(Modifier.height(5.dp))
            Box {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .clickable { pickerOpen = true }
                        .semantics {
                            contentDescription = emphasis
                                ?.let { "语义标记：${it.label}，点击修改" }
                                ?: "设置语义标记"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 已标记 = 实心圆点；未标记 = 空心描边点
                    Box(
                        modifier = Modifier
                            .size(if (emphasis != null) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(toneColor ?: Color.Transparent)
                            .then(
                                if (emphasis == null) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
                DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                    if (emphasis != null) {
                        DropdownMenuItem(
                            text = { Text("取消标记") },
                            onClick = {
                                pickerOpen = false
                                onSetEmphasis(null)
                            }
                        )
                    }
                    for (tone in EmphasisTone.entries) {
                        DropdownMenuItem(
                            enabled = tone != emphasis,
                            text = { Text("标记为${tone.label}") },
                            onClick = {
                                pickerOpen = false
                                onSetEmphasis(tone)
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 顶层小点的序号：`①`…`⑳`，超过 20 回落为数字。纯视觉派生，不落库。 */
fun blockIndexLabel(index: Int): String =
    if (index in 0 until 20) (0x2460 + index).toChar().toString() else (index + 1).toString()
