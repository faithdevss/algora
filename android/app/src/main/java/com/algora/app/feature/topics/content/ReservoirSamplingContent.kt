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

internal val reservoirSamplingContent = TopicContent(
    topicId = "reservoir_sampling",
    figure = Figure(
        caption = "One slot, k = 1. The i-th arrival replaces whatever is held with probability 1/i, and " +
            "the two effects cancel exactly: an early item's chance of surviving falls at precisely the " +
            "rate a late item's chance of being chosen rises, so every element ends up at 1/n whatever " +
            "n turns out to be. That is what makes the stream length irrelevant — no count in advance, " +
            "no second pass, no storing the input, O(k) memory and one pass. The general k is the same " +
            "statement with k/i in place of 1/i and a random slot chosen for the eviction. Getting the " +
            "probability wrong in either direction biases the sample towards the head or the tail of " +
            "the stream, and the failure is invisible on any single run — which is why this is one of " +
            "the few algorithms worth testing statistically rather than by example.",
        shape = FigureShape.Strip(
            cells = listOf("1st", "2nd", "3rd", "4th", "5th"),
            bands = listOf(FigureBand(0, 4, "each ends at 1/5", FigureTone.Accent)),
            aux = listOf("1", "1/2", "1/3", "1/4", "1/5"),
            auxLabel = "chance this arrival takes the slot — shrinking is what keeps it uniform",
        ),
    ),
    whatIsIt = listOf(
        "Reservoir sampling draws k items uniformly at random from a stream of unknown, possibly enormous length, using only O(k) memory and a single pass.",
        "The magic is that you never need to know the total count in advance: each new element gets a fair chance to replace one already held, keeping every item equally likely.",
    ),
    steps = listOf(
        StepCard(1, "Fill the Reservoir", "Put the first k elements straight into the reservoir.", 0xFF10B981),
        StepCard(2, "Roll for Each New Item", "For the i-th element (i > k), pick a random index j in [0, i).", 0xFF3B82F6),
        StepCard(3, "Replace on a Hit", "If j < k, overwrite reservoir[j] with the new element; otherwise discard it.", 0xFFF59E0B),
        StepCard(4, "Uniform Guarantee", "At the end every seen element sits in the reservoir with equal probability k/n.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Keep probability", "k / i", "The i-th element enters the reservoir with this chance."),
        FormulaEntry("Final probability", "k / n", "Every element is equally likely — provably uniform."),
        FormulaEntry("Time / Space", "O(n) / O(k)", "One pass; memory independent of stream length."),
    ),
    notationKey = listOf(
        NotationEntry("k", "sample size (reservoir capacity)"),
        NotationEntry("n", "stream length — unknown in advance"),
        NotationEntry("i", "1-based index of the current element"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Reservoir sampling (k items)",
            accentColor = 0xFF6366F1,
            code = """
                fun reservoirSample(stream: Iterator<Int>, k: Int): List<Int> {
                    val reservoir = ArrayList<Int>(k)
                    var i = 0
                    for (x in stream) {
                        i++
                        if (reservoir.size < k) {
                            reservoir.add(x)
                        } else {
                            val j = (Math.random() * i).toInt()   // 0 until i
                            if (j < k) reservoir[j] = x
                        }
                    }
                    return reservoir
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF10B981, "Stream Sampling", "Sample log lines or events from an unbounded feed without buffering all of them."),
        ApplicationCard("flask", 0xFF3B82F6, "Big-Data Analytics", "Draw a fair random subset from a dataset too large to fit in memory in one pass."),
        ApplicationCard("target", 0xFFF59E0B, "A/B & Monitoring", "Randomly retain a representative slice of traffic for inspection or testing."),
    ),
    takeaways = listOf(
        "Reservoir sampling picks k uniform items from an unknown-length stream in O(k) space.",
        "Each element i is kept with probability k/i, yielding a uniform k/n at the end.",
        "It needs only one pass and never the total count — ideal for streaming data.",
        "Weighted variants (A-Res) extend it to non-uniform sampling.",
    ),
    crossLinks = listOf(
        CrossLink("monte_carlo_method", "Monte Carlo Method"),
        CrossLink("permutation_generation", "Permutation Generation"),
    ),
)
