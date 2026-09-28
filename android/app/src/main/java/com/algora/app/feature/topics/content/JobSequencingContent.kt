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

internal val jobSequencingContent = TopicContent(
    topicId = "job_sequencing",
    figure = Figure(
        caption = "Three unit-length slots, jobs placed profit-first: J1 earns 100 by deadline 2, J2 " +
            "earns 60 by deadline 1, J4 earns 20 by deadline 3. J1 goes into slot 2, not slot 1 — as " +
            "late as its deadline permits — and that is the whole trick. Placed early it would have " +
            "occupied the only slot J2 can ever use, and 60 would have been lost for nothing. J3 (40 by " +
            "deadline 2) arrives to find both slots at or before 2 already filled and is dropped: a job " +
            "is skipped only when every slot it could legally use is gone. Total 180.",
        shape = FigureShape.Strip(
            cells = listOf("t1", "t2", "t3"),
            bands = listOf(
                FigureBand(0, 0, "J2 · 60"),
                FigureBand(1, 1, "J1 · 100", FigureTone.Accent),
                FigureBand(2, 2, "J4 · 20"),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Job sequencing with deadlines schedules unit-time jobs, each with a deadline and a profit, on a single machine to maximize total profit — every job earns its profit only if finished by its deadline.",
        "The greedy insight: consider jobs by profit high-to-low, and place each as late as its deadline allows, keeping late slots open for the profitable jobs still to come.",
    ),
    steps = listOf(
        StepCard(1, "Sort by Profit", "Order jobs from highest profit to lowest.", 0xFF10B981),
        StepCard(2, "Find a Late Slot", "For each job, look for a free time slot at or before its deadline, latest first.", 0xFF3B82F6),
        StepCard(3, "Schedule If Possible", "Place the job in that slot; if no free slot ≤ its deadline exists, skip it.", 0xFFF59E0B),
        StepCard(4, "Sum the Profit", "Total the profits of all successfully scheduled jobs.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Simple version", "O(n²)", "Each job scans backward for a free slot."),
        FormulaEntry("Union-find version", "O(n log n)", "Disjoint-set finds the latest free slot near-instantly."),
        FormulaEntry("Greedy key", "highest profit first", "Scheduled as late as its deadline permits."),
    ),
    notationKey = listOf(
        NotationEntry("dᵢ", "deadline of job i (integer time units)"),
        NotationEntry("pᵢ", "profit earned if job i meets its deadline"),
        NotationEntry("slot", "a unit time unit on the machine"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Job sequencing (greedy)",
            accentColor = 0xFF6366F1,
            code = """
                // jobs: (deadline, profit)
                fun jobSequencing(jobs: List<Pair<Int, Int>>): Int {
                    val maxDeadline = jobs.maxOf { it.first }
                    val slots = BooleanArray(maxDeadline + 1)   // 1..maxDeadline
                    var profit = 0
                    for ((deadline, p) in jobs.sortedByDescending { it.second }) {
                        var t = deadline
                        while (t >= 1) {                        // latest free slot first
                            if (!slots[t]) { slots[t] = true; profit += p; break }
                            t--
                        }
                    }
                    return profit
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("history", 0xFF10B981, "Deadline Scheduling", "Assign profitable tasks to a single worker or CPU where each must finish by a due time."),
        ApplicationCard("finance", 0xFF3B82F6, "Order Fulfillment", "Pick which time-sensitive orders to commit to when capacity is limited, maximizing revenue."),
        ApplicationCard("target", 0xFFF59E0B, "Ad Slot Allocation", "Fit the highest-value time-bounded campaigns into a limited set of delivery slots."),
    ),
    takeaways = listOf(
        "Schedule highest-profit jobs first, each as late as its deadline allows.",
        "Placing jobs late leaves earlier slots open for other deadline-bound jobs.",
        "The naive slot scan is O(n²); a union-find on slots brings it to O(n log n).",
        "It is a clean example of a greedy choice plus a smart data structure for the bottleneck.",
    ),
    crossLinks = listOf(
        CrossLink("fractional_knapsack", "Fractional Knapsack"),
        CrossLink("disjoint_set", "Disjoint Set (Union-Find)"),
    ),
)
