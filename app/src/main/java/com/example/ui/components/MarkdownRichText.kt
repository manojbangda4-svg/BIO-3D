package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun MarkdownRichText(
    markdown: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val lines = markdown.lines()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        lines.forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> {
                    Spacer(modifier = Modifier.height(2.dp))
                }
                line.startsWith("### ") || line.startsWith("## ") || line.startsWith("# ") -> {
                    val heading = line.removePrefix("### ").removePrefix("## ").removePrefix("# ").replace("**", "")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = heading,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
                line.startsWith("**") && line.endsWith("**") && line.length > 4 -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = line.removeSurrounding("**"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> {
                    val body = line.drop(2)
                    Row(modifier = Modifier.padding(start = 4.dp)) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineBold(body, MaterialTheme.colorScheme.onSurface),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                else -> {
                    Text(
                        text = parseInlineBold(line, MaterialTheme.colorScheme.onSurface),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun parseInlineBold(text: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        while (idx < text.length) {
            val start = text.indexOf("**", idx)
            if (start == -1) {
                append(text.substring(idx))
                break
            }
            if (start > idx) {
                append(text.substring(idx, start))
            }
            val end = text.indexOf("**", start + 2)
            if (end == -1) {
                append(text.substring(start))
                break
            }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = baseColor)) {
                append(text.substring(start + 2, end))
            }
            idx = end + 2
        }
    }
}
