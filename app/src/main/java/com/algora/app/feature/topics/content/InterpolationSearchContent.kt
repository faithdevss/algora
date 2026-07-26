package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val interpolationSearchContent = TopicContent(
    topicId = "interpolation_search",
    whatIsIt = listOf(
        "Interpolation search improves on binary search for uniformly distributed sorted data by estimating where the target likely sits instead of always probing the middle.",
        "It's how you find a name in a phone book: for 'Aaron' you open near the front, not the center — the probe position is guided by the target's value.",
    ),
    steps = listOf(
        StepCard(1, "Estimate the Position", "Interpolate a probe index from the target's value relative to the range's endpoints.", 0xFF10B981),
        StepCard(2, "Probe", "Compare the target to the element at the estimated index.", 0xFF3B82F6),
        StepCard(3, "Narrow the Range", "If the target is larger, search above the probe; if smaller, search below.", 0xFFF59E0B),
        StepCard(4, "Repeat", "Re-estimate within the shrunken range until the target is found or the range empties.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Probe formula", "lo + (t − a[lo])·(hi − lo) / (a[hi] − a[lo])", "Position estimated by linear interpolation on value."),
        FormulaEntry("Uniform data", "O(log log n)", "Near-instant on evenly spaced keys."),
        FormulaEntry("Worst case", "O(n)", "Skewed or clustered data degrades it to a linear scan."),
    ),
    notationKey = listOf(
        NotationEntry("t", "target value"),
        NotationEntry("lo, hi", "current range endpoints"),
        NotationEntry("pos", "the interpolated probe index"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Interpolation search",
            accentColor = 0xFF6366F1,
            code = """
                fun interpolationSearch(a: IntArray, t: Int): Int {
                    var lo = 0
                    var hi = a.size - 1
                    while (lo <= hi && t >= a[lo] && t <= a[hi]) {
                        if (a[hi] == a[lo]) return if (a[lo] == t) lo else -1
                        val pos = lo + ((t - a[lo]).toLong() * (hi - lo) / (a[hi] - a[lo])).toInt()
                        when {
                            a[pos] == t -> return pos
                            a[pos] < t -> lo = pos + 1
                            else -> hi = pos - 1
                        }
                    }
                    return -1
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SearchVisualizer,
    applications = listOf(
        ApplicationCard("book", 0xFF10B981, "Phone Book / Dictionary Lookup", "On evenly spread keys, jumping straight to the estimated spot beats halving repeatedly."),
        ApplicationCard("chart", 0xFF3B82F6, "Uniform Numeric Indexes", "Sorted sensor readings or timestamps spaced regularly are ideal for value-guided probing."),
        ApplicationCard("trend", 0xFFF59E0B, "Large Uniform Datasets", "The O(log log n) advantage grows with size — as long as the distribution stays uniform."),
    ),
    takeaways = listOf(
        "Interpolation search guesses the probe position from the target's value, not just the midpoint.",
        "On uniformly distributed data it reaches O(log log n) — faster than binary search.",
        "On skewed or clustered data it collapses to O(n), so distribution matters enormously.",
        "Use it only when data is sorted and roughly uniform; otherwise binary search's O(log n) is safer.",
    ),
    crossLinks = listOf(
        CrossLink("binary_search", "Binary Search"),
        CrossLink("exponential_search", "Exponential Search"),
    ),
)
