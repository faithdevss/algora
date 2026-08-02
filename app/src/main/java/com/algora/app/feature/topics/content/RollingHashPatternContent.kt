package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. The substring-comparison family: hash a window in O(1) per slide,
// with the collision caveat stated up front.
internal val rollingHashPatternContent = TopicContent(
    topicId = "rolling_hash_pattern",
    whatIsIt = listOf(
        "A rolling hash treats a window of characters as a number in base b modulo a large prime. Sliding by one costs O(1): drop the leading character's contribution, shift, add the new character.",
        "That turns \"compare every length-m substring\" from O(n·m) into O(n + m). Hashes can collide, so a hit is a candidate — confirm it with a direct comparison unless approximate matching is acceptable.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Repeated substring comparison: find a pattern, detect duplicate blocks, or binary-search the longest repeated substring length.", 0xFFF59E0B),
        StepCard(2, "Pick Base and Modulus", "Base above the alphabet size, modulus a large prime. For adversarial inputs, randomise the base or hash under two moduli.", 0xFF3B82F6),
        StepCard(3, "Roll the Window", "h = (h - a[i] · b^(m-1)) · b + a[i+m], all mod M. Precompute b^(m-1) once.", 0xFFEF4444),
        StepCard(4, "Verify the Hit", "Equal hashes mean probable equality. Compare the characters before reporting a match — that check is O(m) but rare.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Window hash", "h = Σ a[i] · b^(m-1-i) mod M", "Polynomial hash of a length-m window."),
        FormulaEntry("Roll", "h' = ((h - a[l] · b^(m-1)) · b + a[r+1]) mod M", "O(1) per slide once b^(m-1) is precomputed."),
        FormulaEntry("Time", "O(n + m) expected", "Worst case O(n·m) if every hash collides — rare with a good modulus."),
    ),
    notationKey = listOf(
        NotationEntry("b", "base, larger than the alphabet size"),
        NotationEntry("M", "large prime modulus keeping the hash bounded"),
        NotationEntry("m", "pattern / window length"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Rabin-Karp search (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def rabin_karp(text, pattern):
                    n, m = len(text), len(pattern)
                    if m > n:
                        return []
                    B, M = 257, (1 << 61) - 1
                    high = pow(B, m - 1, M)            # weight of the leading character

                    def h(s):
                        v = 0
                        for ch in s:
                            v = (v * B + ord(ch)) % M
                        return v

                    target, cur, hits = h(pattern), h(text[:m]), []
                    for i in range(n - m + 1):
                        if cur == target and text[i:i + m] == pattern:
                            hits.append(i)             # verify: hashes can collide
                        if i + m < n:
                            cur = ((cur - ord(text[i]) * high) * B + ord(text[i + m])) % M
                    return hits
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF3B82F6, "Plagiarism Detection", "Hash every k-gram of two documents and intersect the fingerprint sets."),
        ApplicationCard("code", 0xFF10B981, "Duplicate Blocks", "Find repeated file chunks or code fragments without pairwise comparison."),
        ApplicationCard("translate", 0xFF8B5CF6, "Longest Repeated Substring", "Binary-search the length; each check is a rolling-hash sweep."),
    ),
    takeaways = listOf(
        "O(1) per slide is the whole point — never rehash the window from scratch.",
        "Always verify a hash hit unless a false positive is genuinely acceptable.",
        "A fixed base and modulus can be attacked; randomise or use double hashing for hostile input.",
        "KMP gives a deterministic O(n + m) for a single pattern; rolling hashes win when many substrings are compared.",
    ),
    crossLinks = listOf(
        CrossLink("rabin_karp", "Rabin-Karp (Algorithms)"),
        CrossLink("kmp", "KMP String Matching (Algorithms)"),
        CrossLink("hash_table", "Hash Table (Data Structures)"),
    ),
)
