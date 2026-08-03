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

// Interview-prep pattern guide. Borders — prefixes that are also suffixes — answer a surprising
// number of string questions once you can compute them in linear time.
internal val prefixFunctionPatternContent = TopicContent(
    topicId = "prefix_function_pattern",
    figure = Figure(
        caption = "The two shaded runs are the same two characters: \"ab\" both opens the string and closes " +
            "it, so pi[7] = 2. On a mismatch the candidate drops to pi[k−1] rather than to zero, which " +
            "is the fallback that keeps the whole build linear.",
        shape = FigureShape.Strip(
            cells = listOf("a", "b", "a", "c", "a", "b", "a", "b"),
            bands = listOf(
                FigureBand(0, 1, "prefix"),
                FigureBand(6, 7, "same suffix", FigureTone.Accent),
            ),
            aux = listOf("0", "0", "1", "0", "1", "2", "3", "2"),
            auxLabel = "pi — longest border of each prefix",
        ),
    ),
    whatIsIt = listOf(
        "The prefix function pi[i] is the length of the longest proper prefix of s[0..i] that is also a suffix of it — its longest *border*. Computing the whole table takes one linear pass.",
        "Borders answer more than string search. The shortest repeating unit of a string is n - pi[n-1] when that divides n; the shortest palindrome extendable from the front comes from a border of s + '#' + reverse(s). KMP is one application, not the whole pattern.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Periodicity, repeated blocks, prefix-suffix overlaps, or a substring search where a rolling hash's collision risk is unacceptable.", 0xFFF59E0B),
        StepCard(2, "Build the Table", "Carry a candidate border length k. On a mismatch fall back to pi[k-1] instead of restarting; on a match extend k by one.", 0xFF3B82F6),
        StepCard(3, "Separate with a Sentinel", "For search, run the table over pattern + '#' + text. A sentinel absent from both alphabets stops borders crossing the join.", 0xFFEF4444),
        StepCard(4, "Read the Structure", "pi[i] = m marks a full occurrence. n - pi[n-1] is the candidate period; it is a true repeat only when it divides n.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Build", "O(n)", "k never increases more than once per index, so total fallback work is linear."),
        FormulaEntry("Search", "O(n + m)", "Table over the pattern, then a single scan of the text."),
        FormulaEntry("Period", "n - pi[n-1]", "The string repeats a block of that length exactly when it divides n."),
    ),
    notationKey = listOf(
        NotationEntry("pi[i]", "longest proper border of the prefix ending at i"),
        NotationEntry("border", "a string that is both a proper prefix and a suffix"),
        NotationEntry("'#'", "sentinel character present in neither pattern nor text"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Prefix function, search and period (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def prefix_function(s):
                    pi = [0] * len(s)
                    k = 0
                    for i in range(1, len(s)):
                        while k and s[i] != s[k]:
                            k = pi[k - 1]                  # fall back to the next-longest border
                        if s[i] == s[k]:
                            k += 1
                        pi[i] = k
                    return pi

                def find_all(text, pattern):
                    joined = pattern + '#' + text          # sentinel blocks cross-boundary borders
                    pi = prefix_function(joined)
                    m = len(pattern)
                    return [i - 2 * m for i, v in enumerate(pi) if v == m]

                def shortest_repeat(s):                    # smallest block s is built from
                    period = len(s) - prefix_function(s)[-1]
                    return s[:period] if len(s) % period == 0 else s
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF3B82F6, "Exact Search", "Deterministic O(n + m) matching with no collision caveat."),
        ApplicationCard("translate", 0xFF10B981, "Periodicity", "Detect repeated blocks, rotations and the minimal generating unit of a string."),
        ApplicationCard("chip", 0xFF8B5CF6, "Stream Matching", "The automaton form matches over a stream with no backtracking into the buffer."),
    ),
    takeaways = listOf(
        "The prefix function is about borders; string search is one consequence of them.",
        "On mismatch fall back to pi[k-1] — never rescan the text pointer.",
        "The sentinel is mandatory; without it a border can straddle pattern and text.",
        "Deterministic linear time is the edge over rolling hashes, which need verification.",
    ),
    crossLinks = listOf(
        CrossLink("kmp", "KMP String Matching (Algorithms)"),
        CrossLink("z_algorithm", "Z Algorithm (Algorithms)"),
        CrossLink("rolling_hash_pattern", "Rolling Hash"),
    ),
)
