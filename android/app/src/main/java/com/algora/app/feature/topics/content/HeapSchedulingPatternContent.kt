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

// Interview-prep pattern guide. Sort by when work arrives, use a heap to decide what runs next —
// the shape behind meeting rooms, CPU scheduling and most simulation questions.
internal val heapSchedulingPatternContent = TopicContent(
    topicId = "heap_scheduling_pattern",
    figure = Figure(
        caption = "Two orderings at once: tasks arrive sorted by start, and a min-heap of end times " +
            "decides what has already finished. The heap's *size* is the concurrency — for meeting " +
            "rooms, its peak is the whole answer.",
        shape = FigureShape.Timeline(
            spans = listOf(
                FigureSpan(0, 30, "0–30"),
                FigureSpan(5, 10, "5–10"),
                FigureSpan(6, 12, "6–12"),
                FigureSpan(15, 20, "15–20", FigureTone.Accent),
            ),
            axisMax = 32,
            marker = 8,
            markerLabel = "t = 8: three rooms in use — the heap holds three end times",
        ),
    ),
    whatIsIt = listOf(
        "Scheduling questions have two orderings at once: tasks arrive in one order and are chosen in another. Sort by arrival, then keep a heap of everything currently available and pop whichever the rule prefers — earliest finisher, shortest job, highest frequency.",
        "The heap is the set of live resources or ready tasks. Meeting rooms is the minimal case: a min-heap of end times, where popping every room that has freed up before the next meeting starts leaves the heap size as the room count.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Concurrent resources, CPU or task ordering with cooldowns, or \"process events in time order\" where the next choice depends on what is currently active.", 0xFFF59E0B),
        StepCard(2, "Sort by Arrival", "Order tasks by start or availability time. That ordering is fixed by the problem; the heap handles the ordering you control.", 0xFF3B82F6),
        StepCard(3, "Retire the Finished", "Before choosing, pop everything from the heap whose end time is ≤ the current moment. Those resources are free again.", 0xFFEF4444),
        StepCard(4, "Push the Choice", "Push the new task's end time (or its remaining count). The heap's size is the concurrency; its root is the next event to occur.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time / Space", "O(n log n) / O(n)", "One sort plus one push and pop per task."),
        FormulaEntry("Meeting rooms", "answer = max heap size", "Equivalent to the sweep-line overlap peak."),
        FormulaEntry("Task scheduler", "idle slots = (maxCount - 1) · (cooldown + 1)", "Closed form when one task dominates the schedule."),
    ),
    notationKey = listOf(
        NotationEntry("pq", "min-heap of end times, or max-heap of remaining counts"),
        NotationEntry("now", "current simulated time"),
        NotationEntry("concurrency", "heap size — how much is running at once"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Meeting rooms and cooldown scheduling (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                import heapq
                from collections import Counter

                def min_rooms(intervals):
                    intervals.sort()                       # by start time
                    ends = []                              # min-heap of end times
                    for start, end in intervals:
                        if ends and ends[0] <= start:
                            heapq.heappop(ends)            # a room freed up in time
                        heapq.heappush(ends, end)
                    return len(ends)                       # peak concurrency

                def schedule_with_cooldown(tasks, cooldown):
                    counts = [-c for c in Counter(tasks).values()]   # max-heap via negation
                    heapq.heapify(counts)
                    time = 0
                    while counts:
                        cycle, waiting = [], cooldown + 1
                        while waiting and counts:
                            remaining = heapq.heappop(counts) + 1    # run one instance
                            if remaining:
                                cycle.append(remaining)    # still has work left
                            time += 1
                            waiting -= 1
                        for remaining in cycle:
                            heapq.heappush(counts, remaining)
                        if counts:
                            time += waiting                # idle out the rest of the cooldown
                    return time
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("history", 0xFF3B82F6, "Room & Resource Booking", "Minimum rooms, servers or machines to run every booking."),
        ApplicationCard("chip", 0xFF10B981, "CPU Scheduling", "Shortest-job-first and cooldown-constrained task ordering."),
        ApplicationCard("users", 0xFF8B5CF6, "Event Simulation", "Any discrete-event loop where the heap root is the next thing to happen.",),
    ),
    takeaways = listOf(
        "Two orderings: arrival fixes the loop, the heap decides what runs next.",
        "Retire finished work before choosing, or concurrency is overcounted.",
        "Heap size is the answer for resource-count questions; the root is the next event time.",
        "Sweep-line gives the same overlap count when you never need to know *which* resource is which.",
    ),
    crossLinks = listOf(
        CrossLink("heap", "Heap (Data Structures)"),
        CrossLink("greedy_intervals_pattern", "Greedy Intervals"),
        CrossLink("sweep_line_pattern", "Line Sweep & Difference Array"),
    ),
)
