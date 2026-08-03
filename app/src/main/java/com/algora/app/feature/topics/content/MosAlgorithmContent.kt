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

internal val mosAlgorithmContent = TopicContent(
    topicId = "mos_algorithm",
    figure = Figure(
        caption = "Four range queries, answered in the numbered order rather than the order they " +
            "arrived — which is only legal because nothing updates the array in between, and that " +
            "offline requirement is the technique's whole precondition. Sorting by √n-sized blocks of " +
            "the left endpoint, then by the right endpoint within a block, is what bounds the pointer " +
            "movement: the left pointer moves O(√n) per query and the right pointer sweeps forward " +
            "O(n) per block, giving O((n + q)√n) against the naive O(n·q). The second precondition is " +
            "that a range's answer can be repaired in O(1) by adding or removing a single element at " +
            "one end — distinct-value counts and frequency queries qualify, and anything needing a " +
            "recomputation from scratch does not, no matter how the queries are ordered.",
        shape = FigureShape.Timeline(
            spans = listOf(
                FigureSpan(1, 5, "1st 1–5", FigureTone.Accent),
                FigureSpan(2, 9, "2nd 2–9", FigureTone.Accent),
                FigureSpan(3, 7, "3rd 3–7", FigureTone.Primary),
                FigureSpan(6, 10, "4th 6–10", FigureTone.Primary),
            ),
            axisMax = 11,
        ),
    ),
    whatIsIt = listOf(
        "Mo's algorithm answers many range queries offline by reordering them so that moving from one query's range to the next requires as few element additions and removals as possible.",
        "It works when there's no update between queries and when a range's answer can be adjusted incrementally by adding or removing one element at an end.",
    ),
    steps = listOf(
        StepCard(1, "Require Offline + Incremental", "All queries are known up front, and add/remove-one-element must update the answer cheaply.", 0xFF10B981),
        StepCard(2, "Block-Sort the Queries", "Divide indices into √n blocks; sort queries by left-block, then by right endpoint.", 0xFF3B82F6),
        StepCard(3, "Slide the Window", "Move the current [l, r] toward each query, adding/removing elements as the ends shift.", 0xFFF59E0B),
        StepCard(4, "Record Answers", "After the window matches a query's range, store its current answer.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Block size", "√n", "Balances left-pointer and right-pointer movement."),
        FormulaEntry("Total time", "O((n + q)·√n)", "Amortized pointer moves across all q queries."),
        FormulaEntry("Requires", "no updates, reversible add/remove", "Offline queries with an incremental answer state."),
    ),
    notationKey = listOf(
        NotationEntry("n, q", "array length and number of queries"),
        NotationEntry("l, r", "current window boundaries"),
        NotationEntry("block", "√n-sized bucket used for sorting queries"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Mo's — query ordering",
            accentColor = 0xFF6366F1,
            code = """
                // Sort queries so pointer movement is minimized.
                val block = Math.sqrt(n.toDouble()).toInt().coerceAtLeast(1)
                queries.sortWith(compareBy({ it.l / block }, { it.r }))

                var curL = 0; var curR = -1
                for (q in queries) {
                    while (curR < q.r) add(a[++curR])
                    while (curL > q.l) add(a[--curL])
                    while (curR > q.r) remove(a[curR--])
                    while (curL < q.l) remove(a[curL++])
                    answers[q.id] = currentAnswer()
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF10B981, "Range Distinct Counts", "Answering many 'how many distinct values in [l, r]?' queries offline is Mo's signature use."),
        ApplicationCard("search", 0xFF3B82F6, "Frequency Queries", "Mode, frequency-of-frequencies, and similar range statistics fit its add/remove model."),
        ApplicationCard("target", 0xFFF59E0B, "Competitive Programming", "It's a staple for offline range problems that segment trees can't express incrementally."),
    ),
    takeaways = listOf(
        "Mo's algorithm reorders offline queries to minimize window movement, giving O((n+q)√n).",
        "It needs queries known in advance and an answer updatable by adding/removing one element.",
        "√n block sorting is what bounds the total pointer travel.",
        "Reach for it when a range statistic is easy to maintain incrementally but hard for a segment tree.",
    ),
    crossLinks = listOf(
        CrossLink("sliding_window", "Sliding Window"),
        CrossLink("segment_tree", "Segment Tree"),
    ),
)
