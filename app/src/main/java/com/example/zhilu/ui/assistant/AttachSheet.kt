package com.example.zhilu.ui.assistant

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/** 相册网格取几张。两行 4 列，刚好把弹层高度压在「不用滚动」的范围内。 */
private const val RecentImageCount = 8

/** 相册网格列数。 */
private const val GalleryColumns = 4

/**
 * 附件弹层：顶部一排功能方块，下面是相册最近图片。
 *
 * 相比原来的下拉菜单，弹层能同时放下「去哪拿」和「直接拿什么」——
 * 大多数时候用户就是想发刚才那张截图，多一次跳转都嫌烦。
 *
 * [sheetState] 由调用方持有：关闭时需要先 `hide()` 等动画走完再移出组合，
 * 顺带在同一个回调里把焦点还给输入框（见 `AssistantScreen.restoreComposerFocus`）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(
    sheetState: SheetState,
    canReadGallery: Boolean,
    recentImages: List<Uri>,
    onRequestGalleryAccess: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onPickFile: () -> Unit,
    onPickRef: () -> Unit,
    onPickRecentImage: (Uri) -> Unit,
    onDismissRequest: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.PageGutter)
                .padding(bottom = Spacing.Xl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
            ) {
                AttachAction(
                    icon = Icons.Rounded.PhotoCamera,
                    label = "拍照",
                    onClick = onTakePhoto
                )
                AttachAction(
                    icon = Icons.Rounded.PhotoLibrary,
                    label = "相册",
                    onClick = onPickFromGallery
                )
                AttachAction(
                    icon = Icons.Rounded.FolderOpen,
                    label = "本地文件",
                    onClick = onPickFile
                )
                AttachAction(
                    icon = Icons.Rounded.Link,
                    label = "引用笔记",
                    onClick = onPickRef
                )
            }

            Spacer(modifier = Modifier.height(Spacing.Lg))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "最近图片",
                    style = ZhiLuType.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (!canReadGallery) {
                    TextButton(onClick = onRequestGalleryAccess) {
                        Text("开启访问", style = ZhiLuType.chip)
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.Sm))

            if (canReadGallery && recentImages.isNotEmpty()) {
                RecentImageGrid(
                    images = recentImages.take(RecentImageCount),
                    onPick = onPickRecentImage
                )
            } else {
                Text(
                    text = if (canReadGallery) {
                        "相册里还没有图片，用上面的「相册」挑一张吧"
                    } else {
                        "开启相册访问后，最近的截图和照片会直接出现在这里"
                    },
                    style = ZhiLuType.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.Sm)
                )
            }
        }
    }
}

@Composable
private fun RowScope.AttachAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(Radius.Card))
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.Xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(Radius.Card))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(Spacing.Xs))
        Text(
            text = label,
            style = ZhiLuType.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun RecentImageGrid(
    images: List<Uri>,
    onPick: (Uri) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
        images.chunked(GalleryColumns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                row.forEach { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(Radius.Field))
                            .clickable { onPick(uri) }
                    )
                }
                // 末行不满时补齐占位，否则剩下的图会被拉宽。
                repeat(GalleryColumns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
