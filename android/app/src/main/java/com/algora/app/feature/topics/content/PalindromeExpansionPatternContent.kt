package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigurePointer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Palindromes are defined by their centre, so enumerate centres
// rather than substrings.
internal val palindromeExpansionPatternContent = TopicContent(
    topicId = "palindrome_expansion_pattern",
    figure = Figure(
        caption = "Both centre kinds are drawn here: the gap between indices 1 and 2 grows outward into " +
            "\"abba\", the character at 5 into \"anana\". Enumerating gaps as well as characters is the " +
            "half people skip, and it costs nothing — O(1) space, no table at all.",
        shape = FigureShape.Strip(
            cells = listOf("a", "b", "b", "a", "n", "a", "n", "a"),
            bands = listOf(
                FigureBand(0, 3, "even centre", FigureTone.Primary),
                FigureBand(4, 7, "odd centre", FigureTone.Accent),
            ),
            pointers = listOf(FigurePointer(5, "centre")),
        ),
    ),
    whatIsIt = listOf(
        "Every palindrome has a centre — a character for odd lengths, a gap between characters for even ones. There are 2n - 1 centres, and growing outward from each finds every palindromic substring in O(n²) with no table.",
        "It beats the O(n³) check-every-substring approach and uses O(1) space, unlike the DP table. Manacher's algorithm gets to O(n) by reusing earlier radii, but expansion is what you write under time pressure.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Longest palindromic substring, counting palindromic substrings, or checking near-palindromes with one allowed deletion.", 0xFFF59E0B),
        StepCard(2, "Enumerate Both Centre Types", "For each i, expand around (i, i) for odd lengths and (i, i + 1) for even ones. Forgetting the even case is the standard bug.", 0xFF3B82F6),
        StepCard(3, "Grow While Symmetric", "Walk left and right outward while both are in bounds and the characters match. Stop at the first mismatch.", 0xFFEF4444),
        StepCard(4, "Convert Back to a Span", "After the loop the pointers sit one step too wide: the palindrome is s[left + 1 : right], of length right - left - 1.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time / Space", "O(n²) / O(1)", "2n - 1 centres, each expanding at most n/2 steps."),
        FormulaEntry("Centres", "2n - 1", "n character centres plus n - 1 gaps."),
        FormulaEntry("Manacher", "O(n)", "Reuses the mirror radius inside the current rightmost palindrome."),
    ),
    notationKey = listOf(
        NotationEntry("centre", "index or gap the palindrome is symmetric about"),
        NotationEntry("left, right", "expanding pointers, one step past the match after the loop"),
        NotationEntry("radius", "how far the palindrome reaches from its centre"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Longest palindromic substring and count (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def expand(s, left, right):
                    while left >= 0 and right < len(s) and s[left] == s[right]:
                        left -= 1
                        right += 1
                    return left + 1, right                 # pointers overshot by one

                def longest_palindrome(s):
                    best = (0, 0)
                    for i in range(len(s)):
                        for lo, hi in (expand(s, i, i), expand(s, i, i + 1)):
                            if hi - lo > best[1] - best[0]:
                                best = (lo, hi)
                    return s[best[0]:best[1]]

                def count_palindromes(s):
                    total = 0
                    for i in range(len(s)):
                        for lo, hi in (expand(s, i, i), expand(s, i, i + 1)):
                            total += (hi - lo + 1) // 2    # every nested centre counts
                    return total
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("translate", 0xFF3B82F6, "Text Analysis", "Longest and count-of palindromic substrings, and near-palindrome checks."),
        ApplicationCard("flask", 0xFF10B981, "Bioinformatics", "Reverse-complement palindromes mark restriction sites in DNA."),
        ApplicationCard("code", 0xFF8B5CF6, "Sequence Symmetry", "Detecting mirrored blocks in logs, token streams and edit histories."),
    ),
    takeaways = listOf(
        "Enumerate centres, not substrings — 2n - 1 of them, both parities.",
        "O(1) space beats the O(n²) DP table for the same time bound.",
        "After expanding, the pointers are one too wide; the span is [left + 1, right).",
        "Palindromic *subsequences* are a different problem — that one needs interval DP.",
    ),
    crossLinks = listOf(
        CrossLink("longest_palindromic_substring", "Longest Palindromic Substring (Algorithms)"),
        CrossLink("manacher", "Manacher's Algorithm (Algorithms)"),
        CrossLink("interval_dp_pattern", "Interval DP Pattern"),
    ),
)
