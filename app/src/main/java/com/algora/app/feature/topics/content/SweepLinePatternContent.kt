package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureSpan
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Intervals stop being intervals once you only keep their endpoints;
// the difference array is the same idea on a fixed index range.
internal val sweepLinePatternContent = TopicContent(
    topicId = "sweep_line_pattern",
    figure = Figure(
        caption = "Throw the intervals away and keep only their endpoints: +1 where one opens, −1 " +
            "where one closes. Sorting those events and running a counter gives the number of active " +
            "intervals everywhere in one pass; its peak is the maximum overlap.",
        shape = FigureShape.Timeline(
            spans = listOf(
                FigureSpan(1, 5, "1–5"),
                FigureSpan(2, 7, "2–7"),
                FigureSpan(4, 6, "4–6"),
            ),
            axisMax = 8,
            marker = 4,
            markerLabel = "sweep at x = 4: active = 3, the peak",
        ),
    ),
    whatIsIt = listOf(
        "A sweep line throws away the intervals and keeps only their endpoints: +1 where one starts, −1 where one ends. Sorting those events and running a counter gives the number of active intervals at every point in one pass.",
        "The difference array is the same trick on a fixed index range: record d[l] += v and d[r+1] -= v per update, then one prefix sum materialises every value. Both turn a per-interval loop into an endpoint scan.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Many range updates with one read at the end, or a maximum-overlap / minimum-rooms question over intervals.", 0xFFF59E0B),
        StepCard(2, "Emit Events", "Each interval [l, r) becomes (l, +1) and (r, −1). Carry the payload if you need more than a count.", 0xFF3B82F6),
        StepCard(3, "Sort and Sweep", "Sort by coordinate, and on ties process ends before starts for touching-is-not-overlapping semantics. Running-sum the deltas.", 0xFFEF4444),
        StepCard(4, "Read the Extremes", "The running counter's peak is the maximum overlap; the coordinate where it peaks is when it happens.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Sweep", "O(n log n)", "Dominated by sorting the 2n events; the sweep itself is O(n)."),
        FormulaEntry("Difference array", "O(1) per update, O(n) to finalise", "Beats O(n) per update whenever queries come after all updates."),
        FormulaEntry("Reconstruction", "a[i] = a[i-1] + d[i]", "One prefix sum turns deltas back into values."),
    ),
    notationKey = listOf(
        NotationEntry("d[]", "delta array holding only the changes"),
        NotationEntry("(x, ±1)", "an event: an interval opening or closing at x"),
        NotationEntry("active", "running count of intervals covering the current point"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Max overlap and range updates (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def max_overlap(intervals):
                    events = []
                    for start, end in intervals:
                        events.append((start, 1))            # opening
                        events.append((end, -1))             # closing
                    events.sort()                            # ties: -1 sorts before +1
                    active = best = 0
                    for _, delta in events:
                        active += delta
                        best = max(best, active)
                    return best

                def apply_range_updates(n, updates):         # updates: (l, r, value) inclusive
                    d = [0] * (n + 1)
                    for l, r, value in updates:
                        d[l] += value
                        d[r + 1] -= value
                    out, running = [], 0
                    for i in range(n):
                        running += d[i]
                        out.append(running)
                    return out
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("users", 0xFF3B82F6, "Meeting Rooms", "Minimum rooms needed is the peak of the running overlap counter."),
        ApplicationCard("chart", 0xFF10B981, "Booking & Capacity", "Flight bookings, seat reservations and car-pool capacity checks."),
        ApplicationCard("map", 0xFF8B5CF6, "Skyline & Coverage", "Building outlines and total covered length, sweeping x with a multiset of heights."),
    ),
    takeaways = listOf(
        "Keep the endpoints, drop the interiors — the answer only ever changes at an endpoint.",
        "Tie-breaking is the specification: end-before-start means touching intervals do not overlap.",
        "Difference arrays are for update-heavy, query-once workloads; prefix sums are the read-heavy mirror image.",
        "Sparse or huge coordinates? Sweep a sorted event list or a coordinate-compressed map instead of an array.",
    ),
    crossLinks = listOf(
        CrossLink("difference_array", "Difference Array (Algorithms)"),
        CrossLink("merge_intervals_pattern", "Merge Intervals"),
        CrossLink("prefix_sum_pattern", "Prefix Sum Pattern"),
    ),
)
