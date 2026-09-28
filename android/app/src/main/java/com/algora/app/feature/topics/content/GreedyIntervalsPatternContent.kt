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

// Interview-prep pattern guide. Uses the standard 7-section template; cross-links to the Algorithms
// topic that implements the technique.
internal val greedyIntervalsPatternContent = TopicContent(
    topicId = "greedy_intervals_pattern",
    figure = Figure(
        caption = "Sort by *end* time, not start or duration — both of those have counterexamples. " +
            "Finishing early is what frees the room, so taking the earliest finisher can never rule out " +
            "a better answer.",
        shape = FigureShape.Timeline(
            spans = listOf(
                FigureSpan(1, 3, "1–3 kept", FigureTone.Accent),
                FigureSpan(2, 5, "2–5 clashes, dropped", FigureTone.Warn),
                FigureSpan(4, 7, "4–7 kept", FigureTone.Accent),
            ),
            axisMax = 8,
        ),
    ),
    whatIsIt = listOf(
        "Interval scheduling asks for the largest set of non-overlapping intervals — or the fewest removals that make a set non-overlapping, which is the same question inverted.",
        "Sorting by end time and always taking the earliest finisher is optimal, and the exchange argument for why is a standard follow-up: swapping any optimal solution's first pick for the earliest finisher never makes it worse.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Meetings, jobs or bookings with start/end times, and a max-count or min-removal objective.", 0xFFF59E0B),
        StepCard(2, "Sort by End", "Ascending end time. Sorting by start or by duration both produce counterexamples.", 0xFF3B82F6),
        StepCard(3, "Take the Earliest Finisher", "Keep an interval if its start ≥ the last kept end; it frees the resource soonest.", 0xFF8B5CF6),
        StepCard(4, "Count or Remove", "Kept count is the answer to \"max non-overlapping\"; total − kept answers \"minimum removals\".", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n log n)", "Dominated by the sort; the scan itself is O(n)."),
        FormulaEntry("Space", "O(1)", "Beyond the sort — one end-time and one counter."),
        FormulaEntry("Min meeting rooms", "O(n log n)", "Different objective: a min-heap of end times, sized by peak overlap."),
    ),
    notationKey = listOf(
        NotationEntry("[s, e)", "an interval, half-open so touching ends don't clash"),
        NotationEntry("last", "end time of the most recently kept interval"),
        NotationEntry("n", "number of intervals"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Max non-overlapping intervals (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def max_non_overlapping(intervals):
                    intervals.sort(key=lambda iv: iv[1])   # by end time
                    kept, last = 0, float("-inf")
                    for s, e in intervals:
                        if s >= last:                      # no clash with the previous keep
                            kept, last = kept + 1, e
                    return kept                            # removals = len(intervals) - kept
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("history", 0xFF3B82F6, "Meeting Rooms", "Fitting the most bookings into one room, or counting rooms needed."),
        ApplicationCard("chip", 0xFF10B981, "Job Scheduling", "Maximising jobs run on a single machine with fixed windows."),
        ApplicationCard("target", 0xFF8B5CF6, "Minimum Arrows / Removals", "Bursting balloons or deleting the fewest overlapping ranges."),
    ),
    takeaways = listOf(
        "Sort by end time. Sorting by start or duration is the classic wrong answer.",
        "Greedy is provable here by exchange argument — say so when asked why it works.",
        "Max-keep and min-remove are complements of the same scan.",
        "\"Minimum rooms\" is a different objective: peak overlap, solved with a heap of end times.",
    ),
    crossLinks = listOf(
        CrossLink("activity_selection", "Activity Selection (Algorithms)"),
        CrossLink("job_sequencing", "Job Sequencing (Algorithms)"),
        CrossLink("merge_intervals_pattern", "Merge Intervals"),
    ),
)
