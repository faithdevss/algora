package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. The most-reached-for pattern in a real interview: trade memory for
// a nested loop, either by counting or by remembering what a complement would look like.
internal val hashCountingPatternContent = TopicContent(
    topicId = "hash_counting_pattern",
    whatIsIt = listOf(
        "The hashing pattern removes an inner loop by remembering what has already been seen. Two shapes cover almost every use: count occurrences in a map, or store each element so a future element can look up its complement in O(1).",
        "The strongest version pairs a map with a running prefix value — subarrays summing to k, or matching parity or remainder — turning \"check every subarray\" from O(n²) into one pass.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Anagrams, duplicates, frequency questions, \"has this pair/complement appeared\", or counting subarrays with a fixed sum.", 0xFFF59E0B),
        StepCard(2, "Choose the Key", "The key is the thing that must be equal: the value itself, a sorted or counted signature, a remainder, or a running prefix.", 0xFF3B82F6),
        StepCard(3, "Look Up Before Insert", "Query the map for what would complete the answer, then insert the current element. Reversing the order lets an element pair with itself.", 0xFFEF4444),
        StepCard(4, "Seed the Empty Case", "For prefix-sum counting, start with {0: 1} so a subarray beginning at index 0 is counted.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n)", "One pass with O(1) expected map operations."),
        FormulaEntry("Space", "O(n)", "The memory you buy the nested loop out with."),
        FormulaEntry("Subarray sum", "count += seen[prefix - k]", "Every earlier prefix differing by k ends a valid subarray here."),
    ),
    notationKey = listOf(
        NotationEntry("seen", "map from key to count or index"),
        NotationEntry("prefix", "running sum of everything up to the current index"),
        NotationEntry("k", "the target sum or difference"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Complement lookup and prefix counting (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def two_sum(a, target):
                    seen = {}                              # value -> index
                    for i, x in enumerate(a):
                        if target - x in seen:             # look up before inserting
                            return [seen[target - x], i]
                        seen[x] = i
                    return []

                def subarrays_with_sum(a, k):
                    counts = {0: 1}                        # empty prefix, so a[0..i] can count
                    prefix = total = 0
                    for x in a:
                        prefix += x
                        total += counts.get(prefix - k, 0)
                        counts[prefix] = counts.get(prefix, 0) + 1
                    return total
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.HashingVisualizer,
    applications = listOf(
        ApplicationCard("search", 0xFF3B82F6, "Deduplication", "First duplicate, distinct counts and set intersections over a stream."),
        ApplicationCard("translate", 0xFF10B981, "Anagram Grouping", "Group by a canonical signature — sorted letters or a 26-slot count tuple."),
        ApplicationCard("chart", 0xFF8B5CF6, "Analytics Rollups", "Frequency tables feeding top-k, mode and histogram queries."),
    ),
    takeaways = listOf(
        "Whenever a nested loop rescans earlier elements, ask what a map could have remembered instead.",
        "Naming the key is the real work — equality of the key must mean equality of the thing you care about.",
        "Look up before you insert, or an element matches itself.",
        "Prefix + map counts subarrays in one pass; seed {0: 1} or you drop every prefix-length answer.",
    ),
    crossLinks = listOf(
        CrossLink("hash_table", "Hash Table (Data Structures)"),
        CrossLink("prefix_sum_pattern", "Prefix Sum Pattern"),
        CrossLink("top_k_pattern", "Top-K Pattern"),
    ),
)
