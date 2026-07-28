package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val manacherContent = TopicContent(
    topicId = "manacher",
    whatIsIt = listOf(
        "Manacher's algorithm finds the longest palindromic substring in O(n) by computing, for every centre, how far the palindrome around it reaches.",
        "The speed comes from reuse: inside an already-known palindrome, a centre's mirror on the left half has the same radius, so the expansion can start from that value instead of from zero.",
    ),
    steps = listOf(
        StepCard(1, "Interleave Separators", "Insert '#' between every character so even- and odd-length palindromes are handled by one loop.", 0xFF8B5CF6),
        StepCard(2, "Track the Rightmost Palindrome", "Keep the centre c and right edge r of the palindrome that currently reaches furthest right.", 0xFF3B82F6),
        StepCard(3, "Seed From the Mirror", "For position i inside that palindrome, start with p[i] = min(r − i, p[2c − i]) — the mirror already did this work.", 0xFFF59E0B),
        StepCard(4, "Expand and Re-centre", "Grow past the seed by direct comparison; if the new palindrome passes r, make i the new centre.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Mirror index", "mirror = 2c − i", "Reflection of i across the current centre."),
        FormulaEntry("Seed", "p[i] = min(r − i, p[mirror])", "Only valid up to the right edge; beyond it must be verified."),
        FormulaEntry("Answer length", "max p[i]", "In the transformed string, p[i] equals the real palindrome length."),
        FormulaEntry("Time", "O(n)", "r only ever moves right, so total expansion work is linear."),
    ),
    notationKey = listOf(
        NotationEntry("p[i]", "radius of the palindrome centred at i in the transformed string"),
        NotationEntry("c", "centre of the palindrome reaching furthest right so far"),
        NotationEntry("r", "right edge of that palindrome"),
        NotationEntry("#", "separator inserted between characters"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Longest palindromic substring in O(n)",
            accentColor = 0xFF6366F1,
            code = """
                fun longestPalindrome(s: String): String {
                    if (s.isEmpty()) return ""
                    // "aba" -> "#a#b#a#": every palindrome now has an odd length.
                    val t = buildString {
                        append('#')
                        for (ch in s) { append(ch); append('#') }
                    }

                    val p = IntArray(t.length)
                    var c = 0
                    var r = 0
                    for (i in t.indices) {
                        if (i < r) p[i] = minOf(r - i, p[2 * c - i])   // reuse the mirror
                        while (i - p[i] - 1 >= 0 && i + p[i] + 1 < t.length &&
                            t[i - p[i] - 1] == t[i + p[i] + 1]
                        ) p[i]++
                        if (i + p[i] > r) { c = i; r = i + p[i] }
                    }

                    val best = p.indices.maxBy { p[it] }
                    val start = (best - p[best]) / 2
                    return s.substring(start, start + p[best])
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("globe", 0xFF8B5CF6, "Genome Analysis", "Palindromic DNA sequences mark restriction sites and hairpin structures worth locating in linear time."),
        ApplicationCard("browser", 0xFF3B82F6, "Text Tooling", "Editors and puzzle engines highlight the longest mirrored run without the quadratic centre-expansion scan."),
        ApplicationCard("chip", 0xFF10B981, "Compression Preprocessing", "Repeated mirrored structure is a signal some encoders exploit before choosing a model."),
    ),
    takeaways = listOf(
        "The '#' interleaving collapses the even/odd palindrome cases into one loop.",
        "The mirror seed is why the algorithm is linear — work already done on the left half is never repeated.",
        "The right edge r is monotonically non-decreasing, which bounds total expansion at O(n).",
        "Naive centre expansion is O(n²) and is usually good enough; reach for Manacher when n is large.",
    ),
    crossLinks = listOf(
        CrossLink("kmp", "KMP String Matching"),
        CrossLink("two_pointer", "Two Pointer Technique"),
        CrossLink("longest_common_subsequence", "Longest Common Subsequence"),
    ),
)
