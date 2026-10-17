package com.example.zhilu.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import android.content.Context
import coil.compose.AsyncImage
import com.example.zhilu.domain.model.CloudUser
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 「我的」页顶部的身份卡（设计原型 `.profile`）。
 *
 * ## 显示什么，取决于有没有登录云端
 *
 * - **未登录**（默认）：显示的是**这份本地知识库自己**的数字 —— 一共记录过多少天、
 *   连续多少天。它回答的是"我这段时间有没有在坚持"，不需要账号也能看。
 * - **已登录**：头像 / 用户名 / 邮箱占掉前两行，数字退到第三行。这里本来就是
 *   "我是谁"的位置，账号接进来比另开一行更顺；点卡片直接进云端页（用户中心）。
 *
 * 云端是可选功能，所以未登录时这张卡**不能点** —— 点一个"我的知识库"却跳去打地址
 * 的页面，会让人以为本地数据也要联网。
 */
@Composable
fun ProfileCard(
    noteCount: Int,
    tagCount: Int,
    recordedDays: Int,
    streakDays: Int,
    modifier: Modifier = Modifier,
    /** 已登录的账号；null = 未登录（或没配云端），卡片退化成"我的知识库"。 */
    account: CloudUser? = null,
    /** 点卡片去哪；null 表示不可点。 */
    onAccountClick: (() -> Unit)? = null
) {
    val clickable = if (account != null && onAccountClick != null) {
        Modifier.clickable(onClick = onAccountClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // clickable 放在 padding 之前：水波纹覆盖整行，而不是只覆盖文字那一块
            .then(clickable)
            .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileAvatar(account)
        Spacer(modifier = Modifier.width(Spacing.Sm))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            Text(
                text = buildString {
                    // 一句话说清"积累"与"坚持"：总数用记了多少天，不是记了多少条
                    if (recordedDays > 0) append("已记录 $recordedDays 天") else append("还没开始记录")
                    if (streakDays > 1) append(" · 连续 $streakDays 天")
                    append(" · $noteCount 条笔记 · $tagCount 个标签")
                },
                style = ZhiLuType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 有头像用头像，登录了没头像用「人」，纯本地用「知识库」书本。 */
@Composable
private fun ProfileAvatar(account: CloudUser?) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(48.dp)
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
                    .size(48.dp)
                    .clip(shape)
            )

            account != null -> Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )

            else -> Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/** 「关于」组里显示的版本号，取自 PackageManager。 */
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
