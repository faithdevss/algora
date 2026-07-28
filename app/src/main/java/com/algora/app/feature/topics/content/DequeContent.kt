package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val dequeContent = TopicContent(
    topicId = "deque",
    whatIsIt = listOf(
        "A deque — double-ended queue — allows insertion and removal at both the front and the back in O(1), making it a stack and a queue at the same time.",
        "It is usually built either on a doubly linked list or on a circular array with two moving indices. The array version is the one used in practice: contiguous memory, no per-element node allocation, and wraparound instead of shifting.",
    ),
    steps = listOf(
        StepCard(1, "Two Ends, Four Operations", "pushFront, pushBack, popFront, popBack — each touches only one end.", 0xFF10B981),
        StepCard(2, "Keep Head and Tail Indices", "A circular buffer stores head and size; the tail is derived, so nothing shifts.", 0xFF3B82F6),
        StepCard(3, "Wrap With Modulo", "Moving past either end wraps around the buffer: index = (head + i) mod capacity.", 0xFFF59E0B),
        StepCard(4, "Grow by Doubling", "When full, copy into a buffer twice the size — amortized O(1) per push.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Index mapping", "slot(i) = (head + i) mod capacity", "Logical position i to physical slot."),
        FormulaEntry("Push front", "head = (head − 1 + capacity) mod capacity", "The +capacity keeps the result non-negative."),
        FormulaEntry("Operations", "O(1) amortized", "All four ends operations; O(n) only on a resize."),
        FormulaEntry("Space", "O(capacity)", "Contiguous, with slack for growth."),
    ),
    notationKey = listOf(
        NotationEntry("head", "physical slot of the logical first element"),
        NotationEntry("size", "number of elements currently stored"),
        NotationEntry("capacity", "length of the backing array"),
        NotationEntry("wraparound", "the tail continuing at slot 0 once it passes the array's end"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Circular-buffer deque",
            accentColor = 0xFF6366F1,
            code = """
                class ArrayDequeInt(initialCapacity: Int = 8) {
                    private var buffer = IntArray(initialCapacity)
                    private var head = 0
                    var size = 0
                        private set

                    private fun slot(i: Int) = (head + i) % buffer.size

                    fun pushBack(value: Int) {
                        if (size == buffer.size) grow()
                        buffer[slot(size)] = value
                        size++
                    }

                    fun pushFront(value: Int) {
                        if (size == buffer.size) grow()
                        head = (head - 1 + buffer.size) % buffer.size
                        buffer[head] = value
                        size++
                    }

                    fun popFront(): Int {
                        check(size > 0) { "deque is empty" }
                        val value = buffer[head]
                        head = (head + 1) % buffer.size
                        size--
                        return value
                    }

                    fun popBack(): Int {
                        check(size > 0) { "deque is empty" }
                        size--
                        return buffer[slot(size)]
                    }

                    private fun grow() {
                        // Unroll into a fresh array so the new buffer starts at head = 0.
                        val bigger = IntArray(buffer.size * 2)
                        for (i in 0 until size) bigger[i] = buffer[slot(i)]
                        buffer = bigger
                        head = 0
                    }
                }
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Sliding-window maximum — the classic use",
            accentColor = 0xFF10B981,
            code = """
                // The deque holds indices whose values are strictly decreasing, so the front
                // is always the maximum of the current window.
                fun maxSlidingWindow(a: IntArray, k: Int): IntArray {
                    val dq = ArrayDeque<Int>()
                    val out = IntArray(maxOf(0, a.size - k + 1))
                    for (i in a.indices) {
                        if (dq.isNotEmpty() && dq.first() <= i - k) dq.removeFirst()   // fell out of the window
                        while (dq.isNotEmpty() && a[dq.last()] <= a[i]) dq.removeLast() // dominated
                        dq.addLast(i)
                        if (i >= k - 1) out[i - k + 1] = a[dq.first()]
                    }
                    return out
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.LinkedStructurePlayer,
    applications = listOf(
        ApplicationCard("history", 0xFF10B981, "Undo / Redo & History", "Browsers cap history by pushing new entries at one end and dropping stale ones at the other."),
        ApplicationCard("target", 0xFF3B82F6, "Sliding-Window Extremes", "A monotonic deque answers window maximum or minimum in O(n) total."),
        ApplicationCard("chip", 0xFFF59E0B, "Work Stealing", "Thread pools push and pop their own tasks at one end while other threads steal from the far end."),
    ),
    takeaways = listOf(
        "A deque is a stack and a queue at once — all four end operations are O(1).",
        "The circular buffer avoids shifting: only head and size change.",
        "Remember the `+ capacity` when decrementing head, or the index goes negative.",
        "Its killer application is the monotonic deque behind sliding-window maximum.",
    ),
    crossLinks = listOf(
        CrossLink("queue", "Queue"),
        CrossLink("stack", "Stack"),
        CrossLink("sliding_window", "Sliding Window"),
    ),
)
