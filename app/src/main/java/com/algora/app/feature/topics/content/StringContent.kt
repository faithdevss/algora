package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val stringContent = TopicContent(
    topicId = "string",
    whatIsIt = listOf(
        "A string is a sequence of characters stored much like an array — each character sits at a known index and is reachable directly.",
        "In many languages strings are immutable: operations that look like edits actually build a brand-new string rather than changing the original.",
    ),
    steps = listOf(
        StepCard(1, "Index Access", "Read the character at any position in O(1), exactly like an array element.", 0xFF10B981),
        StepCard(2, "Concatenation", "Joining two strings copies both into a new buffer — cost grows with total length.", 0xFF3B82F6),
        StepCard(3, "Substring / Slice", "Extract a contiguous range of characters, typically by copying that range out.", 0xFFF59E0B),
        StepCard(4, "Immutability", "Because the buffer can't change in place, repeated edits should use a mutable builder instead.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Index / Length", "O(1)", "Character lookup and length are direct reads."),
        FormulaEntry("Concatenation", "O(n + m)", "Both strings are copied into the new buffer."),
        FormulaEntry("Search (naive)", "O(n · m)", "Checking every start position for a pattern of length m."),
    ),
    notationKey = listOf(
        NotationEntry("n", "length of the string"),
        NotationEntry("m", "length of a pattern or second string"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Building strings efficiently",
            accentColor = 0xFF6366F1,
            code = """
                // Naive: each += copies the whole string so far — O(n^2) overall.
                var slow = ""
                for (c in chars) slow += c

                // Builder: appends into one growable buffer — O(n) overall.
                val fast = buildString {
                    for (c in chars) append(c)
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF10B981, "Text Search", "Find/replace, autocomplete, and log grepping all reduce to substring and pattern matching over strings."),
        ApplicationCard("link", 0xFF3B82F6, "Parsing & Tokenizing", "Compilers, URL routers, and CSV readers slice strings into meaningful tokens."),
        ApplicationCard("chip", 0xFFF59E0B, "Serialization", "JSON, logs, and network payloads are exchanged as strings before being parsed back into data."),
    ),
    takeaways = listOf(
        "A string behaves like an immutable array of characters — index access is O(1).",
        "Repeated concatenation in a loop is O(n²); use a string builder for O(n).",
        "Substring and concatenation copy characters, so their cost scales with length.",
        "Most real work on strings is search and parsing, which build on array-style traversal.",
    ),
    crossLinks = listOf(
        CrossLink("array", "Array"),
        CrossLink("trie", "Trie (Prefix Tree)"),
    ),
)
