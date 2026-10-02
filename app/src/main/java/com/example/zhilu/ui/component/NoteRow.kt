package com.example.zhilu.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.zhilu.domain.model.BlockType
import com.example.zhilu.domain.model.Note
import com.example.zhilu.ui.home.formatTime
import com.example.zhilu.ui.home.notePreviewText
import com.example.zhilu.ui.theme.SemanticColors
import com.example.zhilu.ui.theme.Spacing
import com.example.zhilu.ui.theme.ZhiLuType

/** 列表行形态：文档行无卡片外观，纸卡带柔和阴影与更「纸」的衬线标题。 */
enum class NoteRowVariant { Document, Card }

/**
 * 全站统一的笔记列表行：标题 + 摘要 + 标签 + 元信息行。
 * Home / Tags / 搜索结果共用。
 *
 * @param variant Document 为纯文档行（无卡片、无描边、无阴影，左缘带通高色书脊）；
 *   Card 为圆角纸卡——书脊在卡片上会被圆角切成一枚两头收窄的细条，看着像渲染瑕疵，
 *   因此卡片形态不带书脊，标签色在标签行里照样看得到。
 * @param accentColor 书脊颜色，默认取首个标签色；仅 Document 形态使用。
 * @param dense 紧凑模式：摘要收为单行、隐藏标签与元信息。
 */
@Composable
fun NoteRow(
    note: Note,
    modifier: Modifier = Modifier,
    variant: NoteRowVariant = NoteRowVariant.Document,
    highlightQuery: String = "",
    accentColor: Color? = null,
    dense: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    val titleStyle = when (variant) {
        NoteRowVariant.Document -> ZhiLuType.rowTitle
        NoteRowVariant.Card -> ZhiLuType.cardTitle
    }

    if (variant == NoteRowVariant.Card) {
        AppCard(
            modifier = modifier,
            onClick = onClick,
            onLongClick = onLongClick,
            contentPadding = PaddingValues(Spacing.CardPadding)
        ) {
            NoteRowBody(
                note = note,
                titleStyle = titleStyle,
                highlightQuery = highlightQuery,
                dense = dense,
                trailing = trailing
            )
        }
    } else {
        val accent = rememberTagAccent(
            tagColor = note.tags.firstOrNull()?.color,
            colorOverride = accentColor
        )
        DocumentRow(
            accent = accent,
            modifier = modifier,
            onClick = onClick,
            onLongClick = onLongClick
        ) {
            NoteRowBody(
                note = note,
                titleStyle = titleStyle,
                highlightQuery = highlightQuery,
                dense = dense,
                trailing = trailing
            )
        }
    }
}

@Composable
private fun NoteRowBody(
    note: Note,
    titleStyle: TextStyle,
    highlightQuery: String,
    dense: Boolean,
    trailing: @Composable (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val preview = notePreviewText(note).orEmpty()

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            HighlightedText(
                text = note.title.ifBlank { "未命名知识" },
                query = highlightQuery,
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (trailing != null) {
                Spacer(Modifier.width(Spacing.Sm))
                trailing()
            }
        }
        // 摘要为空时不再渲染空行，避免「只有标题和标签」的行出现一段无意义留白。
        if (preview.isNotBlank()) {
            HighlightedText(
                text = preview,
                query = highlightQuery,
                style = ZhiLuType.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (dense) 1 else 2,
                modifier = Modifier.padding(top = Spacing.RowGapTight)
            )
        }
        if (!dense && note.tags.isNotEmpty()) {
            NoteTagsLine(tags = note.tags, modifier = Modifier.padding(top = Spacing.RowGapGroup))
        }
        if (!dense) {
            MetaLine(parts = noteMetaParts(note), modifier = Modifier.padding(top = Spacing.RowGapMeta))
        }
    }
}

private fun noteMetaParts(note: Note): List<String> = buildList {
    // contentBlocks 而非 blocks：有知识卡片的笔记，直接读 blocks 会让图/链计数恒为 0。
    val blocks = note.contentBlocks
    val imageCount = blocks.count { it.type == BlockType.IMAGE }
    val linkCount = blocks.count { it.type == BlockType.LINK }
    if (imageCount > 0) add("图 $imageCount")
    if (linkCount > 0) add("链 $linkCount")
    add(formatTime(note.updatedAt))
}

@Composable
private fun HighlightedText(
    text: String,
    query: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE
) {
    val annotated = remember(text, query) { highlightMatches(text, query) }
    Text(
        text = annotated,
        style = style,
        fontWeight = fontWeight,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

fun highlightMatches(text: String, query: String): AnnotatedString {
    val trimmed = query.trim().removePrefix("#")
    if (trimmed.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var start = 0
        val lowerText = text.lowercase()
        val lowerQuery = trimmed.lowercase()
        while (true) {
            val index = lowerText.indexOf(lowerQuery, start)
            if (index < 0) {
                append(text.substring(start))
                break
            }
            append(text.substring(start, index))
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = HighlightColor))
            append(text.substring(index, index + trimmed.length))
            pop()
            start = index + trimmed.length
        }
    }
}

private val HighlightColor = SemanticColors.Highlight