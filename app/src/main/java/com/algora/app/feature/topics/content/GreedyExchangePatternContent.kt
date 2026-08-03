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

// Interview-prep pattern guide. Not an algorithm — the argument. Greedy answers are cheap to write
// and easy to get wrong, so the interview value is in justifying or rejecting them.
internal val greedyExchangePatternContent = TopicContent(
    topicId = "greedy_exchange_pattern",
    figure = Figure(
        caption = "The exchange argument *is* the proof: if any adjacent out-of-order pair can be " +
            "swapped without making the solution worse, an optimal solution can be rewritten into the " +
            "greedy one one swap at a time. Here 7 before 2 costs 5 extra waiting; swapping never loses.",
        shape = FigureShape.Strip(
            cells = listOf("4", "1", "7", "2"),
            bands = listOf(FigureBand(2, 3, "out of order — exchange", FigureTone.Warn)),
            pointers = listOf(FigurePointer(2, "swap"), FigurePointer(3, "with")),
            aux = listOf("1", "2", "4", "7"),
            auxLabel = "shortest-job-first — total wait 4 + 6 + 10 + 17 → 1 + 3 + 7 + 14",
        ),
    ),
    whatIsIt = listOf(
        "A greedy algorithm commits to the locally best choice and never revisits it. That is only correct when a local choice cannot foreclose a better global outcome — and interviews are full of problems where it quietly can.",
        "The exchange argument is the proof: take any optimal solution, show it can be rewritten to start with your greedy choice without getting worse, and induct. If you cannot make that rewrite, you have found the counterexample instead, and the answer is DP.",
    ),
    steps = listOf(
        StepCard(1, "Propose the Ordering", "Name the quantity you sort by — earliest end time, largest ratio, shortest job, most constrained slot. That ordering *is* the algorithm.", 0xFFF59E0B),
        StepCard(2, "Attempt the Exchange", "Assume an optimal solution disagrees with the first greedy pick. Swap the greedy choice in. Is the result still feasible, and no worse?", 0xFF3B82F6),
        StepCard(3, "Hunt the Counterexample", "If the swap can hurt, build the smallest instance where it does — coins {1, 3, 4} with target 6 is the classic greedy failure.", 0xFFEF4444),
        StepCard(4, "Fall Back to DP", "Greedy fails when a choice constrains the future in a way the local score does not capture. That constraint is the missing DP state.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Typical cost", "O(n log n)", "One sort plus a linear scan — the cheapest correct answer when it works."),
        FormulaEntry("Exchange argument", "OPT → OPT' with greedy's choice, value(OPT') ≥ value(OPT)", "Then induct on the remaining subproblem."),
        FormulaEntry("Greedy failure", "min coins for 6 with {1, 3, 4}", "Greedy takes 4 + 1 + 1 = 3 coins; optimal is 3 + 3 = 2."),
    ),
    notationKey = listOf(
        NotationEntry("OPT", "any optimal solution assumed to differ from greedy"),
        NotationEntry("exchange", "rewriting OPT to include the greedy choice"),
        NotationEntry("matroid", "the structure where greedy is always optimal (MSTs, for example)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A greedy that works, and one that does not (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def max_meetings(intervals):               # provably optimal
                    intervals.sort(key=lambda x: x[1])     # earliest finish first
                    count, last_end = 0, float('-inf')
                    for start, end in intervals:
                        if start >= last_end:              # exchange argument: finishing
                            count += 1                     # earliest never blocks a better plan
                            last_end = end
                    return count

                def greedy_coins(coins, target):           # WRONG for {1, 3, 4}, target 6
                    used = 0
                    for coin in sorted(coins, reverse=True):
                        used += target // coin
                        target %= coin
                    return used if target == 0 else -1     # use knapsack DP instead
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.SortingVisualizer,
    applications = listOf(
        ApplicationCard("history", 0xFF3B82F6, "Scheduling", "Interval selection, deadline jobs and CPU ordering by shortest processing time."),
        ApplicationCard("network", 0xFF10B981, "Spanning Trees", "Kruskal and Prim are greedy and provably optimal — the matroid case."),
        ApplicationCard("finance", 0xFF8B5CF6, "Rationing", "Fractional knapsack sorts by value density; the 0/1 version does not and needs DP."),
    ),
    takeaways = listOf(
        "Greedy is a claim, not a method — state the ordering and then justify it.",
        "The exchange argument either proves the algorithm or hands you the counterexample.",
        "Fractional problems are usually greedy; indivisible ones usually are not.",
        "When a choice constrains the future beyond its local score, that constraint is the DP state you need.",
    ),
    crossLinks = listOf(
        CrossLink("activity_selection", "Activity Selection (Algorithms)"),
        CrossLink("fractional_knapsack", "Fractional Knapsack (Algorithms)"),
        CrossLink("greedy_intervals_pattern", "Greedy Intervals"),
    ),
)
