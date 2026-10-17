package com.example.zhilu.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.component.AppCard
import com.example.zhilu.ui.component.SectionHeader
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/** 设置分组：分节标题 + 一张卡片承载若干 [SettingsRow]。 */
@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = title)
        // 设置分组是「孤立的面」，正好用手作圆角（动森下生效，纸墨不受影响）
        AppCard(irregular = true) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Md)
            ) {
                content()
            }
        }
    }
}

/**
 * 设置行：图标 + 标题 + 说明 + 尾部（开关 / 按钮 / 箭头）。
 * 传入 [onClick] 时整行可点。
 */
@Composable
fun SettingsRow(
    title: String,
    description: String? = null,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.RowMinHeight)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.Md))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = Spacing.Xs)
        ) {
            Text(
                text = title,
                style = ZhiLuType.rowTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (description != null) {
                Text(
                    text = description,
                    style = ZhiLuType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * 设置行右端的「当前值」摘要（如「动森 · 浅色」「未连接」）。
 *
 * 用行尾的值替代第二行描述，是为了让设置行保持单行高 —— 「我的」页靠它把
 * 四块内容压进一屏。值本身限一行、超长省略，不会把行撑高。
 */
@Composable
fun SettingsRowValue(text: String) {
    Text(
        text = text,
        style = ZhiLuType.meta,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 150.dp)
    )
}

/** 设置行的通用右箭头（统一 tint 与尺寸，避免各页各写一遍）。 */
@Composable
fun SettingsChevron() {
    Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}