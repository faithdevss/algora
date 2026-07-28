package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val rabinKarpContent = TopicContent(
    topicId = "rabin_karp",
    whatIsIt = listOf(
        "Rabin-Karp compares a pattern against every window of the text by comparing numbers instead of strings: each window gets a hash, and only windows whose hash matches the pattern's are checked character by character.",
        "A rolling hash makes each window's hash cost O(1) to derive from the previous one — remove the outgoing character, shift, add the incoming one — so the whole scan is linear on average.",
    ),
    steps = listOf(
        StepCard(1, "Hash the Pattern", "Treat the pattern as a base-b number modulo a large prime q.", 0xFF8B5CF6),
        StepCard(2, "Hash the First Window", "Compute the same value for T[0..m−1].", 0xFF3B82F6),
        StepCard(3, "Roll the Window", "Subtract the leading character's contribution, multiply by the base, add the new trailing character — all mod q.", 0xFFF59E0B),
        StepCard(4, "Verify Every Hash Hit", "Equal hashes may be a collision, so confirm with a direct character comparison before reporting a match.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Window hash", "h = (T[i]·b^(m−1) + T[i+1]·b^(m−2) + … + T[i+m−1]) mod q", "Polynomial hash of one window."),
        FormulaEntry("Roll", "h' = ((h − T[i]·b^(m−1))·b + T[i+m]) mod q", "O(1) update per shift."),
        FormulaEntry("Average time", "O(n + m)", "Assuming few collisions."),
        FormulaEntry("Worst time", "O(n·m)", "Adversarial input where every window collides."),
    ),
    notationKey = listOf(
        NotationEntry("b", "base of the polynomial hash — usually the alphabet size"),
        NotationEntry("q", "large prime modulus that keeps the hash in machine-word range"),
        NotationEntry("h", "hash of the current text window"),
        NotationEntry("m, n", "pattern and text lengths"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Rolling-hash search",
            accentColor = 0xFF6366F1,
            code = """
                fun rabinKarp(text: String, pattern: String, base: Long = 256, mod: Long = 1_000_000_007): List<Int> {
                    val n = text.length
                    val m = pattern.length
                    if (m == 0 || m > n) return emptyList()

                    // high = base^(m-1) mod q, the weight of the outgoing character.
                    var high = 1L
                    repeat(m - 1) { high = high * base % mod }

                    var patternHash = 0L
                    var windowHash = 0L
                    for (i in 0 until m) {
                        patternHash = (patternHash * base + pattern[i].code) % mod
                        windowHash = (windowHash * base + text[i].code) % mod
                    }

                    val hits = mutableListOf<Int>()
                    for (i in 0..n - m) {
                        // Hash equality is necessary but not sufficient — verify.
                        if (windowHash == patternHash && text.regionMatches(i, pattern, 0, m)) hits += i
                        if (i < n - m) {
                            windowHash = (windowHash - text[i].code * high % mod + mod) % mod
                            windowHash = (windowHash * base + text[i + m].code) % mod
                        }
                    }
                    return hits
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFF8B5CF6, "Plagiarism Detection", "Documents are fingerprinted by rolling hashes so shared passages surface without pairwise string comparison."),
        ApplicationCard("stack", 0xFF3B82F6, "Deduplication & rsync", "Content-defined chunking rolls a hash over a file to find block boundaries that survive insertions."),
        ApplicationCard("search", 0xFF10B981, "Multi-Pattern Search", "One pass can look for many patterns at once by keeping their hashes in a set."),
    ),
    takeaways = listOf(
        "Rabin-Karp replaces string comparison with arithmetic, and the rolling hash makes each shift O(1).",
        "A hash match is only a candidate — always verify, or you will report false positives.",
        "Its worst case is quadratic; KMP's linear bound is unconditional.",
        "It shines where KMP does not: searching for many patterns simultaneously.",
    ),
    crossLinks = listOf(
        CrossLink("kmp", "KMP String Matching"),
        CrossLink("hash_table", "Hash Table / Hash Map"),
        CrossLink("sliding_window", "Sliding Window"),
    ),
)
