package com.algora.app.feature.topics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.algora.app.core.data.model.CodeBlock as CodeBlockModel
import com.algora.app.core.ui.theme.AlgoraCodeStyle
import com.algora.app.core.ui.theme.CategoryAccents

// Code chrome is fixed-dark regardless of app theme, matching the design mock's <pre> block.
private val CodeBg = Color(0xFF0F1523)
private val CodeDefaultText = Color(0xFFE6E9F0)
private val CodeCommentText = Color(0xFF9AA1B1)

// Deliberately cross-language: covers Kotlin plus the Python/Java/JS keyword sets and both // and #
// comment styles, so the same highlighter serves every multi-language variant (Phase 7).
private val kotlinTokenPattern = Regex(
    "(?<comment>//.*|#.*)" +
        "|(?<string>\"[^\"]*\"|'[^']*')" +
        "|(?<keyword>\\b(fun|val|var|if|else|elif|for|while|class|private|public|static|void|override|return|require|in|until|downTo|object|internal|const|def|function|let|new|import|self|this|true|false|True|False|None|null)\\b)" +
        "|(?<type>\\b[A-Z][A-Za-z0-9_]*\\b)" +
        "|(?<number>\\b\\d+\\b)",
)

fun highlightKotlin(
    code: String,
    defaultColor: Color,
    keywordColor: Color,
    typeColor: Color,
    stringColor: Color,
    commentColor: Color,
    numberColor: Color,
): AnnotatedString = buildAnnotatedString {
    var lastIndex = 0
    for (match in kotlinTokenPattern.findAll(code)) {
        if (match.range.first > lastIndex) {
            withStyle(SpanStyle(color = defaultColor)) {
                append(code.substring(lastIndex, match.range.first))
            }
        }
        val color = when {
            match.groups["comment"] != null -> commentColor
            match.groups["string"] != null -> stringColor
            match.groups["keyword"] != null -> keywordColor
            match.groups["type"] != null -> typeColor
            match.groups["number"] != null -> numberColor
            else -> defaultColor
        }
        withStyle(SpanStyle(color = color)) { append(match.value) }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < code.length) {
        withStyle(SpanStyle(color = defaultColor)) { append(code.substring(lastIndex)) }
    }
}

@Composable
fun CodeBlockCard(block: CodeBlockModel, initiallyExpanded: Boolean = false) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var selectedLang by remember(block) { mutableStateOf(0) }
    val colorScheme = MaterialTheme.colorScheme
    val accent = Color(block.accentColor)
    val shownCode = if (block.variants.isEmpty()) block.code else block.variants[selectedLang].code

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colorScheme.surface,
        border = BorderStroke(1.dp, colorScheme.outline),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(accent),
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 15.dp, vertical = 11.dp),
                ) {
                    Text(
                        text = block.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = colorScheme.onSurfaceVariant,
                    )
                }
            }

            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column {
                    if (block.variants.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CodeBg)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            block.variants.forEachIndexed { index, variant ->
                                val selected = index == selectedLang
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .background(
                                            if (selected) accent else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(8.dp),
                                        )
                                        .clickable { selectedLang = index }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        variant.language,
                                        style = AlgoraCodeStyle,
                                        color = if (selected) Color.White else CodeCommentText,
                                    )
                                }
                            }
                        }
                    }
                    val highlighted = remember(shownCode) {
                        highlightKotlin(
                            code = shownCode,
                            defaultColor = CodeDefaultText,
                            keywordColor = colorScheme.primary,
                            typeColor = colorScheme.tertiary,
                            stringColor = CategoryAccents.DarkGreen,
                            commentColor = CodeCommentText,
                            numberColor = CategoryAccents.Amber,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CodeBg)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                    ) {
                        Text(text = highlighted, style = AlgoraCodeStyle)
                    }
                }
            }
        }
    }
}
