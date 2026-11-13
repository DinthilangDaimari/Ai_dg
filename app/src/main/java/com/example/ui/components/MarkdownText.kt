package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface ContentBlock {
    data class TextBlock(val content: String) : ContentBlock
    data class CodeBlock(val language: String, val code: String) : ContentBlock
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (block in blocks) {
            when (block) {
                is ContentBlock.TextBlock -> {
                    RenderTextBlock(text = block.content, defaultColor = textColor)
                }
                is ContentBlock.CodeBlock -> {
                    RenderCodeBlock(language = block.language, code = block.code)
                }
            }
        }
    }
}

@Composable
private fun RenderTextBlock(text: String, defaultColor: Color) {
    val lines = text.split("\n")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("### ") -> {
                    Text(
                        text = trimmed.removePrefix("### "),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = trimmed.removePrefix("## "),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = trimmed.removePrefix("# "),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        val itemContent = trimmed.substring(2)
                        Text(
                            text = parseInlineStyles(itemContent, defaultColor),
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 22.sp
                        )
                    }
                }
                trimmed.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val dotIdx = trimmed.indexOf('.')
                    val number = trimmed.substring(0, dotIdx + 1)
                    val itemContent = trimmed.substring(dotIdx + 1).trim()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = number,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Text(
                            text = parseInlineStyles(itemContent, defaultColor),
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 22.sp
                        )
                    }
                }
                trimmed.isNotEmpty() -> {
                    Text(
                        text = parseInlineStyles(line, defaultColor),
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 22.sp
                    )
                }
                else -> {
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun RenderCodeBlock(language: String, code: String) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E222D),
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            // Header bar with language name & Copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF151821))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifBlank { "code" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("copy_code_button")
                ) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(code))
                            isCopied = true
                            Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                delay(2000L)
                                isCopied = false
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = if (isCopied) Color(0xFF34D399) else Color(0xFFCBD5E1),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (isCopied) {
                        Text(
                            text = "Copied!",
                            style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF34D399))
                        )
                    }
                }
            }

            // Code content with horizontal scroll
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}

private fun parseInlineStyles(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("(\\*{1,2})(.*?)\\1|`([^`]+)`")
        val matches = regex.findAll(text)

        for (match in matches) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }

            val fullMatch = match.value
            when {
                fullMatch.startsWith("`") && fullMatch.endsWith("`") -> {
                    val codeContent = fullMatch.removeSurrounding("`")
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            background = Color(0x3364748B),
                            color = Color(0xFF38BDF8)
                        )
                    )
                    append(" $codeContent ")
                    pop()
                }
                fullMatch.startsWith("**") && fullMatch.endsWith("**") -> {
                    val boldContent = fullMatch.removeSurrounding("**")
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor))
                    append(boldContent)
                    pop()
                }
                fullMatch.startsWith("*") && fullMatch.endsWith("*") -> {
                    val italicContent = fullMatch.removeSurrounding("*")
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor))
                    append(italicContent)
                    pop()
                }
                else -> {
                    append(fullMatch)
                }
            }
            cursor = match.range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

private fun parseBlocks(markdown: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val lines = markdown.split("\n")
    var inCodeBlock = false
    var currentLang = ""
    val codeBuilder = StringBuilder()
    val textBuilder = StringBuilder()

    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                // End of code block
                blocks.add(ContentBlock.CodeBlock(currentLang, codeBuilder.toString().trimEnd()))
                codeBuilder.clear()
                currentLang = ""
                inCodeBlock = false
            } else {
                // Start of code block
                if (textBuilder.isNotEmpty()) {
                    blocks.add(ContentBlock.TextBlock(textBuilder.toString().trimEnd()))
                    textBuilder.clear()
                }
                currentLang = line.trim().removePrefix("```").trim()
                inCodeBlock = true
            }
        } else {
            if (inCodeBlock) {
                codeBuilder.append(line).append("\n")
            } else {
                textBuilder.append(line).append("\n")
            }
        }
    }

    if (inCodeBlock && codeBuilder.isNotEmpty()) {
        blocks.add(ContentBlock.CodeBlock(currentLang, codeBuilder.toString().trimEnd()))
    } else if (textBuilder.isNotEmpty()) {
        blocks.add(ContentBlock.TextBlock(textBuilder.toString().trimEnd()))
    }

    return blocks
}
