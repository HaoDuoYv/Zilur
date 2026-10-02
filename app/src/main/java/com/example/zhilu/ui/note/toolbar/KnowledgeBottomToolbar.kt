package com.example.zhilu.ui.note.toolbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.EmphasisTone
import com.example.zhilu.ui.component.ZhiLuDivider
import com.example.zhilu.ui.component.pressScale
import com.example.zhilu.ui.theme.AlphaTokens
import com.example.zhilu.ui.theme.LocalAccessibleEmphasis
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.emphasisToneColor

/**
 * 知识卡片编辑态底部工具栏：6 个 48dp 图标（无文字标签），
 * 未选中卡片时整体降透明度，用视觉而非文案表达可用状态。
 */
@Composable
fun KnowledgeBottomToolbar(
    activeCardId: Long?,
    onAddText: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickImageFromGallery: () -> Unit,
    onAddLatex: () -> Unit,
    onAddCode: () -> Unit,
    onAddLink: () -> Unit,
    onAddBranch: () -> Unit,
    /** 选了一个语义角色 → 让当前块的编辑器进入「标记中」（§3.8）。 */
    onPickMarkTone: (EmphasisTone) -> Unit = {},
    /** 取消「标记中」。 */
    onClearMark: () -> Unit = {},
    /** 没有可标记的目标块时置灰，避免点了没反应。 */
    markEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    val enabled = activeCardId != null
    val contentAlpha = if (enabled) 1f else AlphaTokens.Disabled

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            ZhiLuDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(contentAlpha)
                    .padding(horizontal = Spacing.Sm, vertical = Spacing.Xs),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolbarIcon(
                    onClick = onAddText,
                    enabled = enabled,
                    contentDescription = "文本块"
                ) {
                    Text(
                        text = "Aa",
                        style = ZhiLuType.cardTitle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                ImageToolbarItem(
                    onTakePhoto = onTakePhoto,
                    onPickFromGallery = onPickImageFromGallery,
                    enabled = enabled
                )

                ToolbarIcon(
                    onClick = onAddLatex,
                    enabled = enabled,
                    contentDescription = "公式块",
                    icon = Icons.Default.Functions
                )

                ToolbarIcon(
                    onClick = onAddCode,
                    enabled = enabled,
                    contentDescription = "代码块",
                    icon = Icons.Default.Code
                )

                ToolbarIcon(
                    onClick = onAddLink,
                    enabled = enabled,
                    contentDescription = "链接块",
                    icon = Icons.Default.Link
                )

                ToolbarIcon(
                    onClick = onAddBranch,
                    enabled = enabled,
                    contentDescription = "折叠分支",
                    icon = Icons.Default.Folder
                )

                // 第 7 格：「标记」（§3.8）。
                // 这条路径的价值是"**不必先选中文字**"——正文旁那条划词工具条要先聚焦、
                // 再点色块才能起标记，对"我刚写完一句想补个角色"的场景多一步。
                // 这里的代价是屏幕层看不到编辑器的「标记中」状态，所以不做回显。
                MarkToolbarItem(
                    enabled = enabled && markEnabled,
                    onPickTone = onPickMarkTone,
                    onClearMark = onClearMark
                )
            }
        }
    }
}

@Composable
private fun ToolbarIcon(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    content: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .pressScale(interactionSource, pressedScale = 0.88f, enabled = enabled),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface
            )
        } else {
            content?.invoke()
        }
    }
}

@Composable
private fun MarkToolbarItem(
    enabled: Boolean,
    onPickTone: (EmphasisTone) -> Unit,
    onClearMark: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val darkTheme = LocalExtendedColors.current.isDark

    Box(modifier = modifier) {
        ToolbarIcon(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            contentDescription = "标记",
            icon = Icons.Default.Brush
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (tone in EmphasisTone.entries) {
                DropdownMenuItem(
                    text = { Text("标记为${tone.label}") },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(
                                    color = emphasisToneColor(
                                        tone,
                                        darkTheme,
                                        LocalAccessibleEmphasis.current
                                    ),
                                    shape = CircleShape
                                )
                        )
                    },
                    onClick = {
                        expanded = false
                        onPickTone(tone)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("取消标记中") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onClearMark()
                }
            )
        }
    }
}

@Composable
private fun ImageToolbarItem(
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        ToolbarIcon(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            contentDescription = "图片块",
            icon = Icons.Default.Image
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("拍照") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null
                    )
                },
                onClick = {
                    expanded = false
                    onTakePhoto()
                }
            )
            DropdownMenuItem(
                text = { Text("从相册选择") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null
                    )
                },
                onClick = {
                    expanded = false
                    onPickFromGallery()
                }
            )
        }
    }
}

