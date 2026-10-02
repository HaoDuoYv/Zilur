package com.example.zhilu.ui.note.find

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.component.ElevationTokens
import com.example.zhilu.ui.component.ZhiLuDivider
import com.example.zhilu.ui.theme.AlphaTokens
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 页内查找条（设计文档 §6.4）。
 *
 * 紧贴顶栏下方常驻，`输入框 · 3/7 · ↑ · ↓ · ✕`。打开即自动聚焦并弹输入法 ——
 * 查找是一个"打开就想打字"的动作，多一次点击都是浪费。
 */
@Composable
fun NoteFindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    currentIndex: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = ElevationTokens.Raised
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = ZhiLuType.body.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
                        MaterialTheme.colorScheme.primary
                    ),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = "在本文中查找…",
                                style = ZhiLuType.body,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = AlphaTokens.Hint)
                            )
                        }
                        innerTextField()
                    }
                )

                Text(
                    text = when {
                        query.isBlank() -> ""
                        total == 0 -> "无结果"
                        else -> "${currentIndex + 1}/$total"
                    },
                    style = ZhiLuType.meta,
                    color = if (query.isNotBlank() && total == 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                IconButton(
                    onClick = onPrev,
                    enabled = total > 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "上一处",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onNext,
                    enabled = total > 1,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "下一处",
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭查找",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            ZhiLuDivider()
        }
    }
}
