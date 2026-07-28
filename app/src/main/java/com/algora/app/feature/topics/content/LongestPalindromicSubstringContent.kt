package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val longestPalindromicSubstringContent = TopicContent(
    topicId = "longest_palindromic_substring",
    whatIsIt = listOf(
        "Given a string, find the longest contiguous run inside it that reads the same forwards and backwards. The brute-force reading of that sentence — enumerate all O(n²) substrings and check each in O(n) — costs O(n³), and the two standard improvements both come from noticing that a palindrome is defined by its centre rather than by its endpoints.",
        "Expand around centre is the one to reach for first. There are 2n − 1 possible centres: n characters, for odd-length palindromes, and n − 1 gaps between them, for even-length ones. From each, walk two pointers outward while the characters agree. That is O(n²) time and — importantly — O(1) extra space, which is what makes it preferable to the dynamic-programming formulation in practice. Forgetting the even-length centres is the classic bug: the code then reports \"a\" as the longest palindrome in \"abba\", and it is a bug that passes every odd-length test case you happen to write.",
        "The DP formulation is also O(n²) but costs O(n²) space: dp[i][j] is true when the substring from i to j is a palindrome, which holds when the ends match and the interior dp[i+1][j−1] is already true. It is worth knowing because it generalises — to counting all palindromic substrings, to palindromic partitioning — but for this specific question it is strictly worse than expansion. Manacher's algorithm is the linear-time answer, and it is essentially expand-around-centre with the Z-algorithm's mirror trick bolted on: it keeps the rightmost palindrome found so far and initialises each new centre's radius from its mirror inside that window, so the expansion work is amortised to O(n) overall.",
    ),
    steps = listOf(
        StepCard(1, "Think in Centres, Not Endpoints", "A palindrome is symmetric about a centre; enumerating centres is 2n − 1 candidates, not O(n²).", 0xFFEC4899),
        StepCard(2, "Take Odd Centres", "Each of the n characters is the centre of some odd-length palindrome, possibly of length 1.", 0xFF3B82F6),
        StepCard(3, "Take Even Centres Too", "Each of the n − 1 gaps between characters. Omitting these is the standard bug.", 0xFFEF4444),
        StepCard(4, "Expand While Symmetric", "Two pointers moving outward while s[left] == s[right] and both stay in bounds.", 0xFF10B981),
        StepCard(5, "Keep the Best Span", "Record the start and length whenever an expansion beats the incumbent.", 0xFFF59E0B),
        StepCard(6, "Reuse the Mirror for Linear Time", "Manacher initialises each radius from its mirror in the current rightmost palindrome — same idea, O(n).", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Centres", "2n − 1", "n characters plus n − 1 gaps."),
        FormulaEntry("Expand around centre", "O(n²) time, O(1) space", "The practical default."),
        FormulaEntry("DP recurrence", "dp[i][j] = (sᵢ = sⱼ) ∧ dp[i+1][j−1]", "Bases: length 1 always, length 2 when the pair matches."),
        FormulaEntry("DP cost", "O(n²) time, O(n²) space", "Same time, worse space — but it generalises further."),
        FormulaEntry("Manacher", "O(n) time, O(n) space", "Mirror initialisation makes the expansion amortised linear."),
        FormulaEntry("Brute force", "O(n³)", "All O(n²) substrings, each checked in O(n)."),
    ),
    notationKey = listOf(
        NotationEntry("centre", "a character (odd length) or a gap between two (even length)"),
        NotationEntry("radius", "how far the expansion reached from the centre before failing"),
        NotationEntry("dp[i][j]", "true when s[i..j] is a palindrome"),
        NotationEntry("mirror", "the position symmetric to the current one inside the rightmost known palindrome"),
        NotationEntry("amortised", "individual centres may expand far; Manacher's total expansion work is O(n)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Expand around centre",
            accentColor = 0xFFEC4899,
            code = """
                fun longestPalindrome(s: String): String {
                    if (s.length < 2) return s
                    var bestStart = 0
                    var bestLength = 1

                    fun expand(initialLeft: Int, initialRight: Int) {
                        var left = initialLeft
                        var right = initialRight
                        while (left >= 0 && right < s.length && s[left] == s[right]) {
                            left--
                            right++
                        }
                        // The loop exits one step past the palindrome, so the span is
                        // (left + 1 .. right - 1) and its length is right - left - 1.
                        val length = right - left - 1
                        if (length > bestLength) {
                            bestLength = length
                            bestStart = left + 1
                        }
                    }

                    for (centre in s.indices) {
                        expand(centre, centre)          // odd length
                        expand(centre, centre + 1)      // even length — omitting this line
                                                        // makes "abba" report "a"
                    }
                    return s.substring(bestStart, bestStart + bestLength)
                }

                longestPalindrome("abacabad")   // "abacaba", length 7
                longestPalindrome("abba")       // "abba"  — the even-centre case
                longestPalindrome("babad")      // "bab"   — "aba" is equally long; first wins
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The DP formulation, and what it buys",
            accentColor = 0xFF3B82F6,
            code = """
                // Same O(n²) time, O(n²) space. Worse for this question, better when the
                // question changes — counting every palindromic substring, or partitioning
                // the string into the fewest palindromic pieces, both read off this table.
                fun longestPalindromeDp(s: String): String {
                    val n = s.length
                    val dp = Array(n) { BooleanArray(n) }
                    var start = 0
                    var best = 1
                    for (i in 0 until n) dp[i][i] = true                    // length 1

                    // Fill by increasing length, so dp[i+1][j-1] is always already known.
                    for (length in 2..n) {
                        for (i in 0..n - length) {
                            val j = i + length - 1
                            dp[i][j] = s[i] == s[j] && (length == 2 || dp[i + 1][j - 1])
                            if (dp[i][j] && length > best) {
                                best = length
                                start = i
                            }
                        }
                    }
                    return s.substring(start, start + best)
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFFEC4899, "Bioinformatics", "Palindromic sequences in DNA mark restriction sites and hairpin structures."),
        ApplicationCard("browser", 0xFF3B82F6, "Text Processing", "Symmetry detection in tokenised text, and a standard building block in string-analysis toolkits."),
        ApplicationCard("bulb", 0xFF8B5CF6, "Interview Staple", "The canonical demonstration that changing what you enumerate — centres, not substrings — changes the complexity class."),
    ),
    takeaways = listOf(
        "Enumerate centres, not substrings: 2n − 1 candidates instead of O(n²).",
        "There are two kinds of centre, and forgetting the even ones is a bug every odd-length test case will miss.",
        "Expand-around-centre is O(n²) time and O(1) space — better than the equally quadratic DP for this exact question.",
        "Manacher reaches O(n) by initialising each radius from its mirror, which is the Z-algorithm's window trick applied to symmetry.",
    ),
    crossLinks = listOf(
        CrossLink("manacher", "Manacher's Algorithm"),
        CrossLink("z_algorithm", "Z Algorithm"),
        CrossLink("longest_common_substring", "Longest Common Substring"),
    ),
)
