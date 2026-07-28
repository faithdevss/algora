package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val kmpContent = TopicContent(
    topicId = "kmp",
    whatIsIt = listOf(
        "Knuth-Morris-Pratt finds every occurrence of a pattern inside a text in O(n + m) by never re-reading a text character it has already matched.",
        "The trick is a precomputed prefix table (the LPS array) that answers, for each pattern position, \"if the match breaks here, how much of what I matched is still usable?\" — so the pattern slides forward instead of the text pointer sliding back.",
    ),
    steps = listOf(
        StepCard(1, "Build the LPS Table", "For each prefix of the pattern, record the length of the longest proper prefix that is also a suffix.", 0xFF8B5CF6),
        StepCard(2, "Scan the Text Once", "Walk the text with pointer i and the pattern with pointer j; matching characters advance both.", 0xFF3B82F6),
        StepCard(3, "On Mismatch, Fall Back in the Pattern", "Set j = lps[j−1] instead of resetting to 0 — the shared prefix is already matched, so re-checking it is wasted work.", 0xFFF59E0B),
        StepCard(4, "Report a Hit", "When j reaches m, record a match at i − m and fall back to lps[m−1] to keep finding overlapping occurrences.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("LPS definition", "lps[k] = max{ l < k+1 : P[0..l−1] = P[k−l+1..k] }", "Longest proper prefix that is also a suffix of P[0..k]."),
        FormulaEntry("Mismatch rule", "j ← lps[j−1]", "The only fallback ever needed; i never decreases."),
        FormulaEntry("Time", "O(n + m)", "Each of i and j moves forward at most n and m times."),
        FormulaEntry("Space", "O(m)", "One table the size of the pattern."),
    ),
    notationKey = listOf(
        NotationEntry("T, n", "text and its length"),
        NotationEntry("P, m", "pattern and its length"),
        NotationEntry("i", "index into the text — never moves backward"),
        NotationEntry("j", "how many pattern characters currently match"),
        NotationEntry("lps[k]", "longest proper prefix of P[0..k] that is also its suffix"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "LPS table + search",
            accentColor = 0xFF6366F1,
            code = """
                fun buildLps(p: String): IntArray {
                    val lps = IntArray(p.length)
                    var len = 0
                    var k = 1
                    while (k < p.length) {
                        if (p[k] == p[len]) {
                            len++
                            lps[k] = len
                            k++
                        } else if (len > 0) {
                            len = lps[len - 1]   // fall back, do not restart
                        } else {
                            lps[k] = 0
                            k++
                        }
                    }
                    return lps
                }

                fun kmpSearch(text: String, pattern: String): List<Int> {
                    if (pattern.isEmpty()) return emptyList()
                    val lps = buildLps(pattern)
                    val hits = mutableListOf<Int>()
                    var j = 0
                    for (i in text.indices) {
                        while (j > 0 && text[i] != pattern[j]) j = lps[j - 1]
                        if (text[i] == pattern[j]) j++
                        if (j == pattern.length) {
                            hits += i - j + 1
                            j = lps[j - 1]   // allow overlapping matches
                        }
                    }
                    return hits
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF8B5CF6, "Text Search", "Editors and `grep`-style tools locate a literal pattern in one streaming pass over a file."),
        ApplicationCard("chip", 0xFF3B82F6, "Streaming Protocols", "Frame delimiters are detected in network streams without buffering or rewinding."),
        ApplicationCard("globe", 0xFF10B981, "Bioinformatics", "Exact motif search over DNA strings, where the text is far too long to allow backtracking."),
    ),
    takeaways = listOf(
        "KMP is linear because the text pointer never moves backward — only the pattern pointer falls back.",
        "The LPS table is the whole algorithm: it encodes how much of a broken match is still valid.",
        "Building the table is itself KMP run against the pattern.",
        "Falling back to lps[m−1] after a hit is what makes overlapping matches work.",
    ),
    crossLinks = listOf(
        CrossLink("rabin_karp", "Rabin-Karp"),
        CrossLink("trie", "Trie (Prefix Tree)"),
        CrossLink("manacher", "Manacher's Algorithm"),
    ),
)
