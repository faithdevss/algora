package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Kadane generalised: one pass, O(1) state, "best ending here" plus
// "best seen so far".
internal val runningBestPatternContent = TopicContent(
    topicId = "running_best_pattern",
    whatIsIt = listOf(
        "The running-best pattern carries two numbers through one pass: the best subarray *ending at* the current index, and the best seen anywhere so far. Kadane's algorithm is the canonical instance.",
        "The insight is that a prefix with negative sum can never help what follows — restart instead of carrying it. That collapses an O(n²) scan over all subarrays into O(n) with O(1) memory.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Best contiguous run — maximum sum, maximum product, longest streak, best single buy/sell — with no window width given.", 0xFFF59E0B),
        StepCard(2, "Define \"Ending Here\"", "cur = the best value for a run that must include index i. Everything hangs on that word *must*.", 0xFF3B82F6),
        StepCard(3, "Extend or Restart", "cur = max(a[i], cur + a[i]). If the carried prefix is worse than nothing, drop it and start fresh at i.", 0xFFEF4444),
        StepCard(4, "Track the Global Best", "best = max(best, cur) every step — the optimum may have ended long before the array does.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Kadane", "cur = max(a[i], cur + a[i]); best = max(best, cur)", "One extend-or-restart decision per element."),
        FormulaEntry("Time / Space", "O(n) / O(1)", "Single pass, two scalars — no table."),
        FormulaEntry("Max product variant", "track both max and min", "A negative times the running minimum can become the new maximum."),
    ),
    notationKey = listOf(
        NotationEntry("cur", "best run ending exactly at the current index"),
        NotationEntry("best", "best run seen anywhere so far"),
        NotationEntry("a[i]", "the element being folded in"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Kadane and the product variant (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def max_subarray(a):
                    cur = best = a[0]
                    for x in a[1:]:
                        cur = max(x, cur + x)        # extend the run, or restart at x
                        best = max(best, cur)
                    return best

                def max_product(a):
                    cur_max = cur_min = best = a[0]
                    for x in a[1:]:
                        candidates = (x, cur_max * x, cur_min * x)
                        cur_max, cur_min = max(candidates), min(candidates)
                        best = max(best, cur_max)    # a negative x can flip min into max
                    return best
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("finance", 0xFF3B82F6, "Best Trade Window", "Max profit from one buy/sell is Kadane over consecutive price differences."),
        ApplicationCard("chart", 0xFF10B981, "Signal Bursts", "Strongest sustained interval in a telemetry or sensor series."),
        ApplicationCard("target", 0xFF8B5CF6, "Max Submatrix", "Fix a row pair, compress the columns, and run Kadane on the result."),
    ),
    takeaways = listOf(
        "Two variables, one pass: best-ending-here and best-overall. Never confuse the two.",
        "A negative running prefix is worth discarding — that restart is the entire trick.",
        "Track indices alongside the values when the question asks which subarray, not just its value.",
        "Products and circular arrays need extra state: min alongside max, or total minus the minimum subarray.",
    ),
    crossLinks = listOf(
        CrossLink("kadanes_algorithm", "Kadane's Algorithm (Algorithms)"),
        CrossLink("prefix_sum_pattern", "Prefix Sum Pattern"),
        CrossLink("sliding_window_pattern", "Sliding Window Pattern"),
    ),
)
