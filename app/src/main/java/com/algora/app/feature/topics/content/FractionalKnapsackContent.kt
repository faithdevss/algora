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

internal val fractionalKnapsackContent = TopicContent(
    topicId = "fractional_knapsack",
    figure = Figure(
        caption = "Capacity 50 kg, and three items taken in value-per-kilogram order: A at 6/kg fills " +
            "10 kg, B at 5/kg fills 20, and C at 4/kg only partly fits — so two thirds of it goes in " +
            "and the bag closes exactly full, for 60 + 100 + 80 = 240. The slice is the entire reason " +
            "greed is exact here: the bag never ends with wasted capacity, so no other arrangement can " +
            "beat filling it with the densest material available. Forbid the slice and the argument " +
            "collapses immediately — that is 0/1 knapsack, and it needs a table. Everything here is one " +
            "sort: O(n log n).",
        shape = FigureShape.Strip(
            cells = listOf("10", "20", "30", "40", "50"),
            bands = listOf(
                FigureBand(0, 0, "A · 6/kg"),
                FigureBand(1, 2, "B · 5/kg"),
                FigureBand(3, 4, "⅔ of C · 4/kg", FigureTone.Accent),
            ),
            pointers = listOf(FigurePointer(4, "bag full")),
        ),
    ),
    whatIsIt = listOf(
        "The fractional knapsack problem fills a weight-limited bag to maximize value, where items may be taken in any fraction — half an item is allowed.",
        "That divisibility is what makes it greedy-solvable: always take from the item with the best value-per-weight first, unlike the all-or-nothing 0/1 knapsack which needs dynamic programming.",
    ),
    steps = listOf(
        StepCard(1, "Compute Value Density", "For each item calculate value ÷ weight — its worth per unit of capacity.", 0xFF10B981),
        StepCard(2, "Sort by Density", "Order items from highest value-per-weight to lowest.", 0xFF3B82F6),
        StepCard(3, "Take Whole Items", "Add items fully while they still fit in the remaining capacity.", 0xFFF59E0B),
        StepCard(4, "Take a Fraction", "When the next item won't fully fit, take just the fraction that fills the bag.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n log n)", "Dominated by sorting items by value density."),
        FormulaEntry("Greedy key", "value / weight", "Highest density first is provably optimal here."),
        FormulaEntry("Divisibility", "fractions allowed", "Removing it turns this into NP-hard 0/1 knapsack."),
    ),
    notationKey = listOf(
        NotationEntry("W", "the bag's weight capacity"),
        NotationEntry("vᵢ, wᵢ", "value and weight of item i"),
        NotationEntry("density", "value ÷ weight, the greedy sort key"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Fractional knapsack (greedy)",
            accentColor = 0xFF6366F1,
            code = """
                fun fractionalKnapsack(capacity: Double, items: List<Pair<Double, Double>>): Double {
                    // items: (value, weight)
                    var remaining = capacity
                    var total = 0.0
                    for ((value, weight) in items.sortedByDescending { it.first / it.second }) {
                        if (remaining <= 0) break
                        val take = minOf(weight, remaining)
                        total += value * (take / weight)   // fraction of the item taken
                        remaining -= take
                    }
                    return total
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("finance", 0xFF10B981, "Resource Allocation", "Divisible budgets — spread money across investments by best return per dollar."),
        ApplicationCard("chip", 0xFF3B82F6, "Bandwidth & Cargo", "Allocating continuous resources like bandwidth or bulk cargo to maximize value fits directly."),
        ApplicationCard("bulb", 0xFFF59E0B, "Greedy Teaching Example", "It's the textbook case where a simple greedy choice is provably optimal."),
    ),
    takeaways = listOf(
        "Fractional knapsack is optimally solved by greed: highest value-per-weight first.",
        "It runs in O(n log n), all in the sort — the fill pass is linear.",
        "Allowing fractions is essential; the 0/1 version is NP-hard and needs DP.",
        "It is a canonical example that greed works only when the problem has the right structure.",
    ),
    crossLinks = listOf(
        CrossLink("knapsack_01", "0/1 Knapsack"),
        CrossLink("job_sequencing", "Job Sequencing with Deadlines"),
    ),
)
