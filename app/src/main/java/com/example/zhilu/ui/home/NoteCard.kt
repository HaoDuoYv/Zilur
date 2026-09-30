package com.example.zhilu.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Note
import com.example.zhilu.ui.component.AppCardStyle
import com.example.zhilu.ui.component.TagChip
import com.example.zhilu.ui.theme.LocalReducedMotion
import com.example.zhilu.ui.theme.MotionDuration
import com.example.zhilu.ui.theme.motionEnterTween
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    val firstTagColor = note.tags.firstOrNull()?.color ?: 0xFF6B6B6B.toInt()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 6.dp)
            .drawBehind {
                drawRect(
                    color = Color(firstTagColor),
                    topLeft = androidx.compose.ui.geometry.Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)
                )
            },
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = AppCardStyle.borderWidth,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = AppCardStyle.elevation)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = note.title.ifBlank { "未命名知识" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                ActionIconButton(
                    icon = if (note.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (note.isFavorite) "取消收藏" else "收藏",
                    onClick = onToggleFavorite
                )
                ActionIconButton(
                    icon = Icons.Default.DeleteOutline,
                    contentDescription = "删除",
                    onClick = onDelete
                )
            }

            notePreviewText(note)?.let { preview ->
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                )
            }

            if (note.tags.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    note.tags.take(3).forEach { tag ->
                        TagChip(tag = tag, enabled = false, onClick = {})
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val metaParts = buildList {
                    val imageCount = note.blocks.count { it.type == BlockType.IMAGE }
                    val linkCount = note.blocks.count { it.type == BlockType.LINK }
                    if (imageCount > 0) add("图 $imageCount")
                    if (linkCount > 0) add("链 $linkCount")
                    add(formatTime(note.updatedAt))
                }

                metaParts.forEachIndexed { index, part ->
                    Text(
                        text = part,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (index < metaParts.lastIndex) {
                        Text(
                            text = " · ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun ActionIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = motionEnterTween(MotionDuration.Short, enabled = !reducedMotion),
        label = "note_card_action_scale"
    )

    Box(modifier = modifier.scale(scale)) {
        IconButton(
            onClick = onClick,
            interactionSource = interactionSource
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        hours < 24 -> "${hours}小时前"
        days < 7 -> "${days}天前"
        days < 365 -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}

fun notePreviewText(note: Note): String? {
    note.blocks.firstOrNull { it.type == BlockType.TEXT && it.content.isNotBlank() }?.let {
        return it.content.trim()
    }
    note.blocks.firstOrNull { it.type == BlockType.BRANCH && it.content.isNotBlank() }?.let {
        return it.content.trim()
    }
    note.blocks.firstOrNull { it.type == BlockType.CODE && it.content.isNotBlank() }?.let {
        return it.content.trim().lineSequence().first().take(80)
    }
    note.blocks.firstOrNull { it.type == BlockType.LINK && it.content.isNotBlank() }?.let {
        return it.content.trim()
    }
    note.blocks.firstOrNull { it.type == BlockType.LATEX && it.content.isNotBlank() }?.let {
        return it.content.trim()
    }
    return null
}

data class TimelineGroup(
    val label: String,
    val notes: List<Note>
)

fun groupNotesByTimeline(notes: List<Note>): List<TimelineGroup> {
    if (notes.isEmpty()) return emptyList()
    val sorted = notes.sortedByDescending { it.updatedAt }
    val now = System.currentTimeMillis()
    val dayMs = 86_400_000L
    return sorted.groupBy { note ->
        val days = (now - note.updatedAt) / dayMs
        when {
            days < 1 -> "今天"
            days < 2 -> "昨天"
            days < 7 -> "本周"
            days < 30 -> "本月"
            days < 365 -> "今年"
            else -> "更早"
        }
    }.map { (label, groupNotes) -> TimelineGroup(label, groupNotes) }
        .sortedBy { groupOrder(it.label) }
}

private fun groupOrder(label: String): Int = when (label) {
    "今天" -> 0
    "昨天" -> 1
    "本周" -> 2
    "本月" -> 3
    "今年" -> 4
    else -> 5
}
