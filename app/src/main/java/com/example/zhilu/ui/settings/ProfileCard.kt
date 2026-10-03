package com.example.zhilu.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.content.Context
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/**
 * 「我的」页顶部的身份卡（设计原型 `.profile`）。
 *
 * 知录没有账号体系，所以这张卡不假装是用户资料：它显示的是**这份本地知识库自己**的
 * 两个数字 —— 一共记录过多少天、连续多少天。放这里而不是塞进「数据」组的统计行，
 * 是因为它是打开这一页第一眼该看到的东西（"我这段时间有没有在坚持"）。
 */
@Composable
fun ProfileCard(
    noteCount: Int,
    tagCount: Int,
    recordedDays: Int,
    streakDays: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Md, vertical = Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(Spacing.Sm))
        Column {
            Text(
                text = "我的知识库",
                style = ZhiLuType.rowTitle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = buildString {
                    // 一句话说清"积累"与"坚持"：总数用记了多少天，不是记了多少条
                    if (recordedDays > 0) append("已记录 $recordedDays 天") else append("还没开始记录")
                    if (streakDays > 1) append(" · 连续 $streakDays 天")
                    append(" · $noteCount 条笔记 · $tagCount 个标签")
                },
                style = ZhiLuType.meta,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
