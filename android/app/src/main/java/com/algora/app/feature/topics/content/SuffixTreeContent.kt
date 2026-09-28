package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val suffixTreeContent = TopicContent(
    topicId = "suffix_tree",
    figure = Figure(
        caption = "Sort every suffix of the text once, and substring search becomes binary search over " +
            "that order — O(m log n) with two arrays instead of a tree of nodes and edges. The companion " +
            "LCP array records how much each neighbouring pair shares, which is what turns the array back " +
            "into the suffix tree's answers: longest repeated substring is simply its maximum.",
        shape = FigureShape.Strip(
            cells = listOf("5", "3", "1", "0", "4", "2"),
            bands = listOf(FigureBand(1, 2, "share \"ana\"", FigureTone.Accent)),
            aux = listOf("—", "0", "3", "1", "0", "2"),
            auxLabel = "LCP with the previous suffix — its maximum, 3, is the longest repeat",
        ),
    ),
    whatIsIt = listOf(
        "A suffix tree is a compressed trie of every suffix of a string, enabling substring search and many string queries in time proportional to the pattern, not the text.",
        "A suffix array is its space-lean cousin — a sorted list of suffix start positions — that trades a little query speed for far less memory, which is why it's used more in practice.",
    ),
    steps = listOf(
        StepCard(1, "Enumerate Suffixes", "Consider every suffix of the text (n of them for length n).", 0xFF10B981),
        StepCard(2, "Build the Structure", "A suffix tree merges shared prefixes into edges; a suffix array sorts the suffixes lexicographically.", 0xFF3B82F6),
        StepCard(3, "Search a Pattern", "Walk the tree by the pattern's characters, or binary-search the sorted suffix array.", 0xFFF59E0B),
        StepCard(4, "Answer Rich Queries", "Longest repeated substring, longest common substring, and counts fall out of the structure.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Tree build", "O(n)", "Ukkonen's algorithm builds it in linear time."),
        FormulaEntry("Pattern search", "O(m)", "m is the pattern length — independent of text size."),
        FormulaEntry("Array search", "O(m log n)", "Binary search over sorted suffixes; O(n) space."),
    ),
    notationKey = listOf(
        NotationEntry("n", "length of the text"),
        NotationEntry("m", "length of the query pattern"),
        NotationEntry("suffix array", "sorted list of suffix starting indices"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Suffix array — simple construction",
            accentColor = 0xFF6366F1,
            code = """
                // O(n^2 log n) reference build — real code uses O(n log n) or O(n).
                fun suffixArray(s: String): IntArray =
                    (s.indices)
                        .sortedBy { s.substring(it) }   // sort suffixes lexicographically
                        .toIntArray()
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("flask", 0xFF10B981, "Bioinformatics", "Genome assembly and DNA substring search rely on suffix structures over huge sequences."),
        ApplicationCard("search", 0xFF3B82F6, "Full-Text Indexing", "Fast substring and phrase search in documents and code builds on suffix arrays."),
        ApplicationCard("chip", 0xFFF59E0B, "Data Compression", "The Burrows-Wheeler transform behind bzip2 is computed from the suffix array."),
    ),
    takeaways = listOf(
        "A suffix tree indexes every suffix, giving O(m) substring search after linear-time build.",
        "Suffix arrays store the same information in far less space at O(m log n) search.",
        "They answer longest-repeated and longest-common-substring queries elegantly.",
        "Suffix arrays dominate in practice; the tree is the conceptual foundation.",
    ),
    crossLinks = listOf(
        CrossLink("trie", "Trie (Prefix Tree)"),
        CrossLink("string", "String"),
    ),
)
