package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val zAlgorithmContent = TopicContent(
    topicId = "z_algorithm",
    whatIsIt = listOf(
        "The Z-array of a string answers one question at every position: how many characters starting here also match the beginning of the string? Z[i] is the length of the longest common prefix of the whole string and the suffix starting at i. Computing the whole array takes one linear pass, and that array turns out to answer a startling number of string questions directly.",
        "The trick that makes it linear is a window [l, r] — the interval with the largest right endpoint that is known to match a prefix of the string. When a new index i falls inside that window, its position has an exact mirror at i − l near the front of the string, and Z[i − l] was computed already. If that mirrored value is small enough to stay inside the window it can be copied outright, with no character comparisons at all. Only when it reaches the window's edge does the algorithm compare characters, and every such comparison pushes r further right. Since r only ever increases and is bounded by n, the total comparison work across the whole array is O(n).",
        "Pattern matching then needs no separate machinery: build S = pattern + separator + text, where the separator is a character in neither, and compute the Z-array of S. Every index where Z equals the pattern's length is an occurrence. That gives O(n + m) matching from one general-purpose array. It is the same asymptotic guarantee KMP offers, and the two are close relatives — a Z-array can be converted to KMP's prefix function and back — but the Z-array is usually the easier of the two to derive from scratch under time pressure, because the window invariant is one idea rather than a table whose recurrence has to be remembered. The separator matters: omit it and a pattern that overlaps the boundary produces a Z value that spans both halves and reports a match that is not there.",
    ),
    steps = listOf(
        StepCard(1, "Define Z[i]", "The length of the longest prefix of S that also starts at position i. Z[0] is left undefined by convention.", 0xFFEC4899),
        StepCard(2, "Track the Window [l, r)", "The known prefix-match interval with the furthest right edge seen so far.", 0xFF3B82F6),
        StepCard(3, "Copy Inside the Window", "For i < r, start from min(Z[i − l], r − i) — the mirror already did this work.", 0xFF10B981),
        StepCard(4, "Extend Past the Edge", "Only when the copied value reaches r do characters get compared, and each comparison moves r right.", 0xFFF59E0B),
        StepCard(5, "Slide the Window", "If the match ran past r, set l = i and r to the new right edge.", 0xFF8B5CF6),
        StepCard(6, "Match with a Separator", "Z-array of pattern + '$' + text; every Z = |pattern| is an occurrence.", 0xFF06B6D4),
    ),
    formulas = listOf(
        FormulaEntry("Definition", "Z[i] = max{k : S[0..k) = S[i..i+k)}", "Z[0] is conventionally left unset or set to n."),
        FormulaEntry("Time & space", "O(n) / O(n)", "r never decreases, which caps the total comparison work."),
        FormulaEntry("Copy rule", "Z[i] ← min(Z[i − l], r − i)", "The mirror's value, truncated at the window edge (r exclusive)."),
        FormulaEntry("Matching", "S = P + '$' + T, Z[i] = |P|", "Separator must appear in neither P nor T."),
        FormulaEntry("Match cost", "O(n + m)", "Same guarantee as KMP, from a general-purpose array."),
        FormulaEntry("KMP relation", "convertible both ways", "Z-array and prefix function carry the same information."),
    ),
    notationKey = listOf(
        NotationEntry("Z[i]", "longest common prefix length of S and the suffix at i"),
        NotationEntry("[l, r)", "the Z-box — the prefix-matching interval with the furthest right edge, r exclusive"),
        NotationEntry("mirror", "index i − l, whose Z value was computed earlier in the same pass"),
        NotationEntry("separator", "a sentinel character in neither pattern nor text, e.g. '$'"),
        NotationEntry("amortised", "individual steps may compare many characters; the total across all i is O(n)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Building the Z-array",
            accentColor = 0xFFEC4899,
            code = """
                fun zArray(s: String): IntArray {
                    val n = s.length
                    val z = IntArray(n)
                    var l = 0
                    var r = 0
                    for (i in 1 until n) {
                        if (i < r) {
                            // Inside the window: the mirror already answered this, up to the edge.
                            z[i] = minOf(r - i, z[i - l])
                        }
                        // Extend by explicit comparison. Every iteration of this loop advances r,
                        // and r never moves left, so the loop runs O(n) times across all of i.
                        while (i + z[i] < n && s[z[i]] == s[i + z[i]]) z[i]++
                        if (i + z[i] > r) {
                            l = i
                            r = i + z[i]
                        }
                    }
                    return z
                }

                zArray("aabcaabxaaz").joinToString()
                // 0, 1, 0, 0, 3, 1, 0, 0, 2, 1, 0
                //          ↑ position 4 starts "aab", matching the string's own first three
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Matching, and why the separator is not optional",
            accentColor = 0xFF06B6D4,
            code = """
                fun findAll(text: String, pattern: String): List<Int> {
                    val combined = pattern + '$' + text          // '$' occurs in neither
                    val z = zArray(combined)
                    return z.indices
                        .filter { z[it] == pattern.length }
                        .map { it - pattern.length - 1 }         // back to a text index
                }

                findAll("aabxaabxcaabxaabxay", "aabxaaby")       // []
                findAll("aabxaabxcaabxaabxay", "aabxaab")        // [0, 9]

                // Without the separator, "aa" + "aa" concatenates to "aaaa" and position 1 gets
                // Z = 3 — a run that starts inside the pattern and spills into the text. The
                // sentinel is what forces every reported match to lie wholly on the text side.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFFEC4899, "Pattern Matching", "Linear-time search with no pattern-specific table — one array serves any query on the same string."),
        ApplicationCard("chip", 0xFF3B82F6, "Periodicity & Borders", "Smallest period, longest border and string-repetition tests all read straight off the Z-array."),
        ApplicationCard("bulb", 0xFF10B981, "Competitive Programming", "Usually quicker to derive correctly under time pressure than KMP's prefix function."),
    ),
    takeaways = listOf(
        "Z[i] is how far the suffix at i agrees with the string's own prefix — one array, computed in one pass.",
        "The [l, r] window lets most positions be copied from a mirror instead of compared.",
        "Linearity is amortised: r only moves right, so all the character comparisons together are O(n).",
        "Concatenating pattern + separator + text turns the array into an O(n + m) matcher, and dropping the separator produces false matches across the join.",
    ),
    crossLinks = listOf(
        CrossLink("kmp", "KMP String Matching"),
        CrossLink("naive_string_search", "Naive Pattern Search"),
        CrossLink("manacher", "Manacher's Algorithm"),
    ),
)
