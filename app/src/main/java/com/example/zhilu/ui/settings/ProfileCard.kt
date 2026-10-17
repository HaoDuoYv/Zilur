package com.example.zhilu.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.zhilu.domain.model.CloudUser
import com.example.zhilu.ui.component.AppCard
import com.example.zhilu.ui.component.ZhiLuDivider
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 「我的」页顶部的**身份 Hero 卡**。
 *
 * ## 从「裸身份行」到「一张卡」
 *
 * 早先这里是直接摊在页面上的 Row（没有容器、没有底色），夹在顶栏与第一张设置卡
 * 之间显得悬空；现在收进与下方分组同一套 `AppCard(irregular = true)` —— 动森自动
 * 拿到手作圆角 + 暖褐投影，纸墨自动无描边，两种外观都不需要额外分支。
 *
 * ## 为什么把数字做成「磁贴」
 *
 * 身份行只回答"我是谁"和"坚持得怎么样"（记录天数 / 连续天数）；笔记 / 标签 / 图片
 * 三个计数下沉成一排磁贴。这样**同一批数字全页只出现一次** —— 之前身份行与
 * 「数据」组各写一遍「N 条笔记 · M 个标签」，是重复。
 *
 * ## 显示什么，取决于有没有登录云端
 *
 * - **未登录**（默认）：名字退化成「我的知识库」，只显示坚持天数与磁贴。此时整卡
 *   **不可点** —— 点一个"我的知识库"却跳去打地址的页面，会让人以为本地数据也要联网。
 * - **已登录**：头像 / 用户名 / 邮箱占前两行，卡片可点，直接进云端页（用户中心）。
 */
@Composable
fun ProfileCard(
    noteCount: Int,
    tagCount: Int,
    mediaCount: Int,
    recordedDays: Int,
    streakDays: Int,
    modifier: Modifier = Modifier,
    /** 已登录的账号；null = 未登录（或没配云端），卡片退化成"我的知识库"。 */
    account: CloudUser? = null,
    /** 点卡片去哪；null 表示不可点。 */
    onAccountClick: (() -> Unit)? = null
) {
    val clickable = account != null && onAccountClick != null

    AppCard(
        modifier = modifier,
        onClick = if (clickable) onAccountClick else null,
        irregular = true,
        // 0 内边距交给内部自排：分割线要通到卡片两边，身份行与磁贴各自留白。
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(account)
            Spacer(modifier = Modifier.width(Spacing.Md))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = account?.username?.takeIf { it.isNotBlank() } ?: "我的知识库",
                    style = ZhiLuType.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (account != null) {
                    Text(
                        text = account.email,
                        style = ZhiLuType.meta,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // 一句话说清"坚持"：总数用记了多少天，不是记了多少条
                Text(
                    text = buildString {
                        if (recordedDays > 0) append("已记录 $recordedDays 天") else append("还没开始记录")
                        if (streakDays > 1) append(" · 连续 $streakDays 天")
                    },
                    style = ZhiLuType.meta,
                    // 用主色：这是卡片里唯一一句"会变好"的话，值得比邮箱重一档
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 未登录不可点，也就不画箭头 —— 否则像"点进去还有内容"
            if (clickable) SettingsChevron()
        }

        // 身份 ↔ 统计：一条发丝线就够，不再叠底色或描边
        ZhiLuDivider()

        Row(modifier = Modifier.fillMaxWidth()) {
            StatTile(label = "笔记", value = noteCount, modifier = Modifier.weight(1f))
            StatTile(label = "标签", value = tagCount, modifier = Modifier.weight(1f))
            StatTile(label = "图片", value = mediaCount, modifier = Modifier.weight(1f))
        }
    }
}

/** 统计磁贴：上数字下标签，三枚等宽平分卡片。 */
@Composable
private fun StatTile(
    label: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = Spacing.Md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString(),
            style = ZhiLuType.cardTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = label,
            style = ZhiLuType.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** 有头像用头像，登录了没头像用「人」，纯本地用「知识库」书本。 */
@Composable
private fun ProfileAvatar(account: CloudUser?) {
    val size = 56.dp
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        when {
            account?.avatarUrl != null -> AsyncImage(
                model = account.avatarUrl,
                contentDescription = "头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(shape)
            )

            account != null -> Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )

            else -> Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/** 「关于」里的版本号（现在降级为「我的」页脚的一行小字）。 */
@Composable
fun appVersionLabel(context: Context = LocalContext.current): String {
    val packageName = context.packageName
    return remember(context, packageName) {
        runCatching {
            val info = context.packageManager.getPackageInfo(packageName, 0)
            "v${info.versionName}"
        }.getOrDefault("v1.0.0")
    }
}
