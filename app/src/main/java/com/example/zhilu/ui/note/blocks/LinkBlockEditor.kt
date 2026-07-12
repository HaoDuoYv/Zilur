package com.example.zhilu.ui.note.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

private const val LINK_SEPARATOR = "|"

@Composable
fun LinkBlockEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val (title, url) = remember(value) { parseLinkContent(value) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LinkTextField(
            value = title,
            placeholder = "链接标题",
            textStyle = MaterialTheme.typography.bodyLarge,
            onValueChange = { newTitle ->
                onValueChange(formatLinkContent(newTitle, url))
            }
        )
        LinkTextField(
            value = url,
            placeholder = "https://…",
            textStyle = MaterialTheme.typography.bodyMedium,
            onValueChange = { newUrl ->
                onValueChange(formatLinkContent(title, newUrl))
            }
        )
    }
}

@Composable
private fun LinkTextField(
    value: String,
    placeholder: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val mergedStyle = textStyle.merge(
        TextStyle(color = MaterialTheme.colorScheme.onSurface)
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = mergedStyle,
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = mergedStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            innerTextField()
        }
    )
}

private fun parseLinkContent(content: String): Pair<String, String> {
    val parts = content.split(LINK_SEPARATOR, limit = 2)
    return when (parts.size) {
        2 -> parts[0].trim() to parts[1].trim()
        else -> "" to content.trim()
    }
}

private fun formatLinkContent(title: String, url: String): String {
    val trimmedTitle = title.trim()
    val trimmedUrl = url.trim()
    return when {
        trimmedTitle.isBlank() && trimmedUrl.isBlank() -> ""
        trimmedTitle.isBlank() -> trimmedUrl
        trimmedUrl.isBlank() -> trimmedTitle
        else -> "$trimmedTitle$LINK_SEPARATOR$trimmedUrl"
    }
}
