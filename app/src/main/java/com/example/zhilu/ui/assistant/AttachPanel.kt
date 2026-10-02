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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.zhilu.ui.theme.LocalExtendedColors
import com.example.zhilu.ui.theme.Radius
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/** 相册网格取几张。两行 4 列，超出面板高度的部分靠滚动取用。 */
private const val RecentImageCount = 8

/** 相册网格列数。 */
private const val GalleryColumns = 4

/**
 * 附件面板：顶部一排功能方块，下面是相册最近图片。
 *
 * ## 它不是 BottomSheet
 *
 * 早先这里是 `ModalBottomSheet`。弹层是**独立于键盘的一层**：它自己贴着屏幕底部往上长，
 * 与键盘的高度、动画都没有关系，于是「点加号」必然经历一次「键盘先收 → 弹层再起」，
 * 输入框在这中间被甩上又甩下。
 *
 * 现在它只是一块**高度恒等于键盘高度**的普通容器，由 `AssistantScreen` 放在输入栏正下方：
 * 键盘在时它不占地（输入栏本来就贴在键盘上沿）；键盘收起时它顶上同样的高度，
 * 于是输入栏在两种状态之间**一格都不动**。
 *
 * 因此这里**不要**给它任何固定高度或 `wrapContentHeight`——高度由外层容器决定，
 * 内容超了就滚动。固定高度会破坏「与键盘等高」这条契约。
 */
@Composable
fun AttachPanel(
    canReadGallery: Boolean,
    recentImages: List<Uri>,
    onRequestGalleryAccess: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickFromGallery: () -> Unit,
    onPickFile: () -> Unit,
    onPickRef: () -> Unit,
    onPickRecentImage: (Uri) -> Unit
) {
    val hairline = LocalExtendedColors.current.hairline
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 键盘位是一个「下沉」的界面：用 sunken 纸色而不是纯白卡片，
            // 让它在视觉上属于「底部那一块」，而不是又一张浮起来的卡片。
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clipToBounds()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 与上方输入栏之间的发丝线：面板底色与页面底色接近，没有这条线会糊成一片。
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(hairline)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.PageGutter, vertical = Spacing.Sm)
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

                Spacer(modifier = Modifier.height(Spacing.Md))

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
                .background(MaterialTheme.colorScheme.surface),
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
