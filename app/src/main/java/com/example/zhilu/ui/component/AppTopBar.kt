package com.example.zhilu.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.palettePaint

/** 顶栏标题规格：Compact 用页面标题（20 衬线），Large 用笔记标题（28 衬线）。 */
enum class AppTopBarVariant { Compact, Large }

/** 动森顶栏的外形：底部两角圆角 —— 参考仓库 AnimalTabs 容器的 20dp 圆角语言。 */
private val AnimalTopBarShape = RoundedCornerShape(
    bottomStart = Radius.CardAnimalIsland,
    bottomEnd = Radius.CardAnimalIsland
)

/**
 * 统一顶栏。两套外观两种形态：
 * - **纸墨**：透明容器，滚动后浮出发丝底线；
 * - **动森**：surface 顶栏板 + 底部两角圆角 + `componentBorder` 暖褐描边（底边线常显，
 *   此时不再叠加滚动分隔线 —— 描边本身就是那条线）—— 参考仓库 `AnimalTabs` 的容器语言。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    variant: AppTopBarVariant = AppTopBarVariant.Compact,
    scrolled: Boolean = false,
    /** 自定义左侧入口（如助手页的 ☰ 历史抽屉）。给了它就不再画返回键。 */
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    val titleStyle = when (variant) {
        AppTopBarVariant.Compact -> ZhiLuType.pageTitle
        AppTopBarVariant.Large -> ZhiLuType.noteTitle
    }
    val paint = palettePaint(LocalThemePalette.current)
    val bordered = paint.componentBorder != Color.Unspecified
    val darkTheme = LocalExtendedColors.current.isDark
    val barColors = if (bordered) {
        // 板色由外层 Box 画（要带圆角），顶栏本身透明
        TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        )
    } else {
        TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.background
        )
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (bordered) {
                        Modifier
                            .background(MaterialTheme.colorScheme.surface, AnimalTopBarShape)
                            .border(
                                width = 2.dp,
                                color = paint.componentBorder.copy(alpha = if (darkTheme) 0.7f else 1f),
                                shape = AnimalTopBarShape
                            )
                    } else {
                        Modifier
                    }
                )
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = title,
                            style = titleStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                            // 必须限一行：动作位一多（查找 / 目录 / 分享 / 编辑 + 状态胶囊），
                            // 标题列会被压窄，不限行就会折成两行把顶栏撑高（真机踩过）。
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                    if (leading != null) {
                        leading()
                    } else if (onBack != null) {
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
                colors = barColors
            )
        }
        if (!bordered) {
            AnimatedVisibility(
                visible = scrolled,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                ZhiLuDivider()
            }
        }
    }
}
