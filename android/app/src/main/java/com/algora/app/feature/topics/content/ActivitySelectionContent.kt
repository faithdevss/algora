package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureSpan
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val activitySelectionContent = TopicContent(
    topicId = "activity_selection",
    figure = Figure(
        caption = "This is the input that kills the shortest-duration rule. The middle activity is only " +
            "two hours long and looks like the cheapest thing to commit to, but it starts before the " +
            "first one ends and ends after the last one starts, so taking it blocks both and the day " +
            "yields one activity instead of two. Earliest-finish-first takes 0–5, is free again at 5, " +
            "and takes 5–10. The same input also defeats earliest-start-first if the 0–5 activity ran " +
            "to 10 instead. Finishing early is the only property that matters, because it is the only " +
            "one that measures how much of the resource is left afterwards.",
        shape = FigureShape.Timeline(
            spans = listOf(
                FigureSpan(0, 5, "0–5 taken", FigureTone.Accent),
                FigureSpan(4, 6, "4–6 shortest, blocks both", FigureTone.Warn),
                FigureSpan(5, 10, "5–10 taken", FigureTone.Accent),
            ),
            axisMax = 11,
        ),
    ),
    whatIsIt = listOf(
        "Given a set of activities, each with a start and a finish time, activity selection asks for the largest number of them you can run on a single resource without any two overlapping. It is the interval-scheduling problem in its purest form: no weights, no priorities, just count.",
        "The answer is one line — sort by finish time, then sweep left to right taking any activity that starts at or after the last one you took. What makes this worth studying is not the code but why the code is correct, because the three obvious alternatives are all wrong. Earliest start first fails on a single activity that opens the day and closes it. Shortest duration first fails on a short activity wedged between two long ones it blocks. Fewest conflicts first survives longer but still has counterexamples. Earliest finish first has none.",
        "The proof is an exchange argument. Let a₁ be the activity that finishes first and let S be any optimal schedule. If S already contains a₁ there is nothing to show. If it does not, take S's own first activity and swap it for a₁: a₁ finishes no later, so it cannot conflict with anything else in S, and the swap leaves a schedule of the same size that does contain a₁. The remaining problem is the same problem on the activities that start after a₁ finishes, so induction closes it. That structure — a greedy choice that is always safe, plus a subproblem of the same shape — is exactly what the greedy method needs and what fractional knapsack has too.",
    ),
    steps = listOf(
        StepCard(1, "Sort by Finish Time", "Order every activity by when it ends, not when it begins. This is the entire algorithm's leverage.", 0xFF10B981),
        StepCard(2, "Take the First", "The activity that finishes earliest is always in some optimal schedule, so take it unconditionally.", 0xFF3B82F6),
        StepCard(3, "Track the Last Finish", "Keep one variable: the finish time of the most recently selected activity.", 0xFFF59E0B),
        StepCard(4, "Sweep and Test", "For each remaining activity in finish order, take it if start ≥ last finish, otherwise skip it.", 0xFFEC4899),
        StepCard(5, "Skip Cheaply", "A rejected activity is discarded outright — it is never revisited, which keeps the sweep linear.", 0xFF8B5CF6),
        StepCard(6, "Stop at the End", "One pass produces a maximum-size set. There is no backtracking and no second pass.", 0xFF06B6D4),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n log n)", "The sort dominates; the sweep is O(n)."),
        FormulaEntry("Space", "O(1) extra", "One variable holds the last finish time."),
        FormulaEntry("Greedy key", "finish time, ascending", "Start time and duration both give wrong answers."),
        FormulaEntry("Compatibility test", "startᵢ ≥ lastFinish", "Half-open intervals: touching is allowed."),
        FormulaEntry("Exchange argument", "|OPT with a₁| = |OPT|", "Swapping in the earliest finisher never shrinks a schedule."),
        FormulaEntry("Weighted variant", "O(n log n) DP", "Add profits and greed breaks — it becomes binary search + DP."),
    ),
    notationKey = listOf(
        NotationEntry("(sᵢ, fᵢ)", "start and finish time of activity i"),
        NotationEntry("lastFinish", "finish time of the most recently selected activity"),
        NotationEntry("compatible", "two activities that do not overlap: one starts at or after the other ends"),
        NotationEntry("a₁", "the activity with the smallest finish time — the safe greedy choice"),
        NotationEntry("OPT", "any maximum-size compatible set, not necessarily unique"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Activity selection (greedy)",
            accentColor = 0xFF10B981,
            code = """
                data class Activity(val name: String, val start: Int, val finish: Int)

                fun selectActivities(activities: List<Activity>): List<Activity> {
                    val chosen = mutableListOf<Activity>()
                    var lastFinish = Int.MIN_VALUE
                    // Finish time ascending — the only sort key that is provably safe.
                    for (a in activities.sortedBy { it.finish }) {
                        if (a.start >= lastFinish) {     // half-open: ending at 4 and starting at 4 is fine
                            chosen += a
                            lastFinish = a.finish
                        }
                    }
                    return chosen
                }
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why the tempting sort keys fail",
            accentColor = 0xFFEF4444,
            code = """
                // Earliest start first — one activity spanning the whole day wins the sort
                // and blocks everything else. Answer 1, optimum 2.
                val startTrap = listOf(
                    Activity("all-day", 0, 10),
                    Activity("morning", 1, 3),
                    Activity("evening", 4, 6),
                )

                // Shortest duration first — the short middle activity is taken and it
                // conflicts with both of the long ones. Answer 1, optimum 2.
                val durationTrap = listOf(
                    Activity("left",   0, 5),
                    Activity("middle", 4, 6),   // length 2, taken first, blocks both
                    Activity("right",  5, 10),
                )

                // Finish time first handles both: it takes "morning" then "evening",
                // and "left" then "right".
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Room & Machine Booking", "Fitting the most meetings into one room, or the most jobs onto one machine, is this problem verbatim."),
        ApplicationCard("globe", 0xFF3B82F6, "Broadcast & Satellite Windows", "Ground stations pick non-overlapping contact windows to maximise the number of passes served."),
        ApplicationCard("bulb", 0xFFF59E0B, "Greedy Correctness Teaching", "It is the standard example where three plausible greedy keys are wrong and one is provably right."),
    ),
    takeaways = listOf(
        "Sort by finish time and sweep — O(n log n), with the sort doing all the work.",
        "Earliest start and shortest duration both have small counterexamples; earliest finish does not.",
        "The exchange argument is the point: the greedy choice is safe and the remainder is the same problem.",
        "Add weights to the activities and greed collapses — the weighted version needs dynamic programming.",
    ),
    crossLinks = listOf(
        CrossLink("job_sequencing", "Job Sequencing with Deadlines"),
        CrossLink("fractional_knapsack", "Fractional Knapsack"),
        CrossLink("coin_change_greedy", "Coin Change (Greedy)"),
    ),
)
