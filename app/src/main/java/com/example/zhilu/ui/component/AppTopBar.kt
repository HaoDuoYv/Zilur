package com.example.zhilu.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.ZhiLuType

/** 顶栏标题规格：Compact 用页面标题（20 衬线），Large 用笔记标题（28 衬线）。 */
enum class AppTopBarVariant { Compact, Large }

/**
 * 统一顶栏：透明容器，滚动后浮出发丝底线；可选副标题。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    variant: AppTopBarVariant = AppTopBarVariant.Compact,
    scrolled: Boolean = false,
    actions: @Composable () -> Unit = {}
) {
    val titleStyle = when (variant) {
        AppTopBarVariant.Compact -> ZhiLuType.pageTitle
        AppTopBarVariant.Large -> ZhiLuType.noteTitle
    }

    Column {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = title,
                        style = titleStyle,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = ZhiLuType.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            navigationIcon = {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            actions = { actions() },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                scrolledContainerColor = MaterialTheme.colorScheme.background
            )
        )
        AnimatedVisibility(
            visible = scrolled,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            ZhiLuDivider()
        }
    }
}