package com.example.zhilu.ui.note.toolbar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun BlockToolbar(
    onAddText: () -> Unit,
    onAddCode: () -> Unit,
    onAddImage: () -> Unit,
    onPickImageFromGallery: () -> Unit,
    onAddLink: () -> Unit,
    onAddLatex: () -> Unit,
    onAddDivider: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = contentColorFor(MaterialTheme.colorScheme.surfaceVariant),
        tonalElevation = 0.dp,
        shadowElevation = 4.dp
    ) {
        AnimatedContent(
            targetState = expanded,
            transitionSpec = {
                fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) togetherWith
                    fadeOut(animationSpec = androidx.compose.animation.core.tween(180)) using
                    SizeTransform(clip = false)
            },
            label = "BlockToolbarExpand"
        ) { isExpanded ->
            if (isExpanded) {
                ExpandedToolbar(
                    onAddText = onAddText,
                    onAddCode = onAddCode,
                    onAddImage = onAddImage,
                    onPickImageFromGallery = onPickImageFromGallery,
                    onAddLink = onAddLink,
                    onAddLatex = onAddLatex,
                    onAddDivider = onAddDivider,
                    onCollapse = { expanded = false }
                )
            } else {
                CollapsedToolbar(
                    onAddText = onAddText,
                    onAddCode = onAddCode,
                    onAddImage = onAddImage,
                    onPickImageFromGallery = onPickImageFromGallery,
                    onExpand = { expanded = true }
                )
            }
        }
    }
}

@Composable
private fun CollapsedToolbar(
    onAddText: () -> Unit,
    onAddCode: () -> Unit,
    onAddImage: () -> Unit,
    onPickImageFromGallery: () -> Unit,
    onExpand: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolbarIconButton(
            onClick = onAddText,
            icon = Icons.Default.TextFields,
            contentDescription = "文字"
        )
        ToolbarIconButton(
            onClick = onAddCode,
            icon = Icons.Default.Code,
            contentDescription = "代码"
        )
        ImageSourceIconButton(
            onTakePhoto = onAddImage,
            onPickFromGallery = onPickImageFromGallery,
            contentDescription = "图片"
        )
        ToolbarIconButton(
            onClick = onExpand,
            icon = Icons.Default.MoreHoriz,
            contentDescription = "更多"
        )
    }
}

@Composable
private fun ExpandedToolbar(
    onAddText: () -> Unit,
    onAddCode: () -> Unit,
    onAddImage: () -> Unit,
    onPickImageFromGallery: () -> Unit,
    onAddLink: () -> Unit,
    onAddLatex: () -> Unit,
    onAddDivider: () -> Unit,
    onCollapse: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolbarIconButton(
            onClick = onAddText,
            icon = Icons.Default.TextFields,
            contentDescription = "文字"
        )
        ToolbarIconButton(
            onClick = onAddCode,
            icon = Icons.Default.Code,
            contentDescription = "代码"
        )
        ImageSourceIconButton(
            onTakePhoto = onAddImage,
            onPickFromGallery = onPickImageFromGallery,
            contentDescription = "图片"
        )
        ToolbarIconButton(
            onClick = onAddLink,
            icon = Icons.Default.Link,
            contentDescription = "链接"
        )
        ToolbarIconButton(
            onClick = onAddLatex,
            icon = Icons.Default.Functions,
            contentDescription = "公式"
        )
        ToolbarIconButton(
            onClick = onAddDivider,
            icon = Icons.Default.HorizontalRule,
            contentDescription = "分割线"
        )
        ToolbarIconButton(
            onClick = onCollapse,
            icon = Icons.Default.Close,
            contentDescription = "收起"
        )
    }
}

@Composable
private fun ToolbarIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val contentColor = if (isPressed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        interactionSource = interactionSource,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = contentColor
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(22.dp),
            tint = contentColor
        )
    }
}

@Composable
private fun ImageSourceIconButton(
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        ToolbarIconButton(
            onClick = { menuExpanded = true },
            icon = Icons.Default.Image,
            contentDescription = contentDescription
        )
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
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
                    menuExpanded = false
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
                    menuExpanded = false
                    onPickFromGallery()
                }
            )
        }
    }
}
