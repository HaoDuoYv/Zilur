package com.example.zhilu.ui.assistant

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.AiService
import com.example.zhilu.domain.model.AiSettings
import com.example.zhilu.ui.component.AppSheetAction
import com.example.zhilu.ui.component.ProviderAvatar
import com.example.zhilu.ui.theme.LocalThemePalette
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType
import com.example.zhilu.ui.theme.palettePaint

/**
 * 助手页的「当前 AI」条。
 *
 * 放在顶栏**下方**而不是挤进标题：主流对话应用（ChatGPT / Claude / DeepSeek）都是
 * 这个位置 —— 标题只承载"这是哪个页面"，模型是可切换的**状态**，混在一起用户会以为
 * 模型名是标题的一部分。
 *
 * 形态是**整宽圆角框 + 内容居中**，不是一行左对齐的文字：居中胶囊读起来就是一个
 * "可点开的控件"，左对齐的一行更像标签。点一下弹切换层。
 */
@Composable
fun AiModelBar(
    settings: AiSettings,
    onSwitch: (String) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = settings.resolveActive()
    var sheetOpen by remember { mutableStateOf(false) }
    val paint = palettePaint(LocalThemePalette.current)
    val bordered = paint.componentBorder != Color.Unspecified

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = { sheetOpen = true },
            enabled = settings.enabledServices.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.PageGutter)
                .padding(vertical = Spacing.Xs),
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                width = if (bordered) 2.dp else 1.dp,
                color = if (bordered) paint.componentBorder else MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Md, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (active != null) {
                    ProviderAvatar(
                        label = active.provider.ifBlank { active.displayName },
                        size = 28.dp
                    )
                    Spacer(Modifier.width(Spacing.Sm))
                }
                Text(
                    text = active?.displayName ?: "未配置 AI",
                    style = ZhiLuType.chip,
                    color = if (active != null) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                // 供应商与模型名都显示：只显示其中一个时，用户看不出"这个名字对应哪个真模型"。
                // 与显示名重复的不再写一遍（迁移过来的旧配置常见），免得出现「qwen qwen」。
                val detail = active?.let { service ->
                    listOf(service.provider, service.model)
                        .filter { it.isNotBlank() && it != service.displayName }
                        .distinct()
                        .joinToString(" · ")
                }.orEmpty()
                if (detail.isNotBlank()) {
                    Spacer(Modifier.width(Spacing.Xs))
                    Text(
                        text = detail,
                        style = ZhiLuType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(Spacing.Xs))
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = "切换 AI",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        // 「管理」**不在这里**：这一行的职责只有"显示当前是哪个 AI + 点开切换"。
        // 管理入口放在切换层选项的**下方居中**（见 AiSwitchSheet）。
    }

    if (sheetOpen) {
        AiSwitchSheet(
            settings = settings,
            onSwitch = {
                onSwitch(it)
                sheetOpen = false
            },
            onManage = {
                // 先收起弹层再跳转：留在栈上会让返回时又弹一次，像是"没生效"。
                sheetOpen = false
                onManage()
            },
            onDismiss = { sheetOpen = false }
        )
    }
}

/**
 * 「切换 AI」底部弹层：选项在上，**整宽居中的管理入口在下**。
 *
 * 管理按钮不放右上角：一是拇指够不到那个角，二是容易被当成"完成/关闭"的语义；
 * 做成列表下方的一条，读起来就是"这些是能选的，想改去这儿"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSwitchSheet(
    settings: AiSettings,
    onSwitch: (String) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Md)
                .padding(bottom = Spacing.Lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.Xs)
        ) {
            Text(
                text = "切换 AI",
                style = ZhiLuType.rowTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "点击即可切换，新消息会用选中的这个",
                style = ZhiLuType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.Xs))
            val activeId = settings.resolveActive()?.id
            settings.enabledServices.forEach { service ->
                AiSwitchRow(
                    service = service,
                    isActive = service.id == activeId,
                    onClick = { onSwitch(service.id) }
                )
            }
            if (settings.enabledServices.isEmpty()) {
                Text(
                    text = "还没有可用的 AI。点下面的按钮去添加或启用一个。",
                    style = ZhiLuType.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(Spacing.Sm))
            AppSheetAction(
                text = "管理 AI 配置",
                onClick = onManage,
                icon = Icons.Default.Settings
            )
        }
    }
}

/**
 * 切换层里的一行：**图标块 + 两行文字**，整体居中。
 *
 * 选中项除了打勾，还**整行加一层极淡的主色底**并描边 —— 勾在最右边，
 * 一眼扫过去时底色比一个小图标更容易定位到"我现在用的是哪个"。
 */
@Composable
private fun AiSwitchRow(
    service: AiService,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val paint = palettePaint(LocalThemePalette.current)
    val bordered = paint.componentBorder != Color.Unspecified
    val shape = RoundedCornerShape(if (bordered) Radius.CardAnimalIsland else Radius.Field)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            Color.Transparent
        },
        border = if (isActive && bordered) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Sm, vertical = Spacing.Sm),
            // 整宽单段居中：图标、文字、勾是**一组**，一起居中。
            // 早先试过"两侧等宽占位配平"，但勾（图标 + 间距）比所谓占位更宽，
            // 整组被推向左边 —— 与其算配平，不如让整组作为一个单位居中。
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProviderAvatar(
                label = service.provider.ifBlank { service.displayName },
                size = 40.dp
            )
            Spacer(Modifier.width(Spacing.Sm))
            // 限宽而不是 horizontalScroll：居中对齐下横向滚动会把溢出部分推到屏幕外
            // 且滚不回来（滚动起点在内容中间），长名字会直接消失。
            Column(
                modifier = Modifier.widthIn(max = 220.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = service.displayName,
                        style = ZhiLuType.rowTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                    if (isActive) {
                        Spacer(Modifier.width(Spacing.Xs))
                        Text(
                            text = "使用中",
                            style = ZhiLuType.label,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = listOf(service.provider.ifBlank { "自定义" }, service.model)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" · "),
                    style = ZhiLuType.meta,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (isActive) {
                Spacer(Modifier.width(Spacing.Sm))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "当前使用",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
