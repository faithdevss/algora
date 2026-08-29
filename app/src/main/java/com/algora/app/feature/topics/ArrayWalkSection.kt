package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors

// ── Array walk player ────────────────────────────────────────────────────────
// One-dimensional pointer walks: prefix sums, difference arrays, sliding windows, two-pointer scans,
// Kadane, top-k heaps, and the interview-prep pattern topics that are the same walks under a
// different name. Precomputed frames, same as the other players.
//
// The renderer is a row of cells plus optional extras that individual algorithms need: a second row
// (the prefix / difference / heap the walk maintains), pointer labels under the cells, a back-edge
// arc for cycle detection, and an interval track for merge-intervals. Each frame carries only the
// parts it uses.

private enum class CellMark { IDLE, DIM, WINDOW, ACTIVE, DONE, RESULT }

private class CellView(val text: String, val mark: CellMark = CellMark.IDLE)

private class IntervalView(val start: Int, val end: Int, val mark: CellMark)

private class WalkFrame(
    val status: String,
    val cells: List<CellView>,
    val pointers: Map<Int, String> = emptyMap(),
    val aux: List<CellView>? = null,
    val auxLabel: String? = null,
    val readout: String? = null,
    // Fast/slow pointer topics need the row to be a linked list with a cycle, drawn as an arc from
    // the tail back to the node it points at.
    val loopBack: Pair<Int, Int>? = null,
    val intervals: List<IntervalView>? = null,
)

private class WalkConfig(
    val intro: String,
    val legend: List<Pair<Color, String>>,
    val build: () -> List<WalkFrame>,
)

private val WindowFill = SimColors.Blue
private val ActiveFill = SimColors.Active
private val DoneFill = SimColors.Green
private val ResultFill = Color(0xFF7C3AED)

private val pointerLegend = listOf(
    ActiveFill to "Reading",
    WindowFill to "In window",
    ResultFill to "Answer",
)

private val heapLegend = listOf(
    ActiveFill to "Current",
    DoneFill to "In heap",
    ResultFill to "Answer",
)

// ── Builders ─────────────────────────────────────────────────────────────────

private fun prefixSumFrames(): List<WalkFrame> {
    val a = listOf(3, 1, 4, 1, 5, 9, 2, 6)
    val prefix = IntArray(a.size)
    val frames = mutableListOf<WalkFrame>()

    fun auxRow(filledUpTo: Int, active: Int? = null) = prefix.mapIndexed { i, v ->
        when {
            i == active -> CellView(v.toString(), CellMark.ACTIVE)
            i <= filledUpTo -> CellView(v.toString(), CellMark.WINDOW)
            else -> CellView("·", CellMark.DIM)
        }
    }

    frames += WalkFrame(
        status = "P[i] will hold the sum of everything up to and including a[i]. Building it costs one pass.",
        cells = a.map { CellView(it.toString()) },
        aux = auxRow(-1),
        auxLabel = "P (inclusive prefix)",
    )

    for (i in a.indices) {
        prefix[i] = if (i == 0) a[0] else prefix[i - 1] + a[i]
        frames += WalkFrame(
            status = if (i == 0) "P[0] = a[0] = ${a[0]}."
            else "P[$i] = P[${i - 1}] + a[$i] = ${prefix[i - 1]} + ${a[i]} = ${prefix[i]}.",
            cells = a.mapIndexed { j, v -> CellView(v.toString(), if (j == i) CellMark.ACTIVE else if (j < i) CellMark.WINDOW else CellMark.IDLE) },
            pointers = mapOf(i to "i"),
            aux = auxRow(i - 1, active = i),
            auxLabel = "P (inclusive prefix)",
        )
    }

    val l = 2
    val r = 5
    frames += WalkFrame(
        status = "Range sum a[$l..$r] = P[$r] − P[${l - 1}] = ${prefix[r]} − ${prefix[l - 1]} = ${prefix[r] - prefix[l - 1]}. " +
            "Two lookups and a subtraction — the width of the range never enters the cost.",
        cells = a.mapIndexed { j, v -> CellView(v.toString(), if (j in l..r) CellMark.RESULT else CellMark.DIM) },
        pointers = mapOf(l to "l", r to "r"),
        aux = prefix.mapIndexed { i, v ->
            CellView(v.toString(), if (i == r || i == l - 1) CellMark.ACTIVE else CellMark.DIM)
        },
        auxLabel = "P (inclusive prefix)",
        readout = "sum = ${prefix[r] - prefix[l - 1]}",
    )
    return frames
}

private fun differenceArrayFrames(): List<WalkFrame> {
    val n = 8
    val updates = listOf(Triple(1, 4, 3), Triple(3, 6, 2), Triple(0, 2, -1))
    val diff = IntArray(n)
    val frames = mutableListOf<WalkFrame>()

    fun diffRow(active: Set<Int> = emptySet()) = diff.mapIndexed { i, v ->
        CellView(v.toString(), if (i in active) CellMark.ACTIVE else CellMark.IDLE)
    }

    frames += WalkFrame(
        status = "Three range updates are coming. Applying each one element by element would cost O(n) apiece; " +
            "the difference array touches two cells per update instead.",
        cells = List(n) { CellView("0", CellMark.DIM) },
        aux = diffRow(),
        auxLabel = "D (difference)",
    )

    for ((l, r, value) in updates) {
        diff[l] += value
        val touched = mutableSetOf(l)
        if (r + 1 < n) {
            diff[r + 1] -= value
            touched += r + 1
        }
        frames += WalkFrame(
            status = "Add $value to a[$l..$r]: D[$l] += $value" +
                (if (r + 1 < n) ", D[${r + 1}] −= $value" else ", nothing to cancel past the end") + ".",
            cells = List(n) { i -> CellView("0", if (i in l..r) CellMark.WINDOW else CellMark.DIM) },
            pointers = mapOf(l to "l", r to "r"),
            aux = diffRow(touched),
            auxLabel = "D (difference)",
        )
    }

    val result = IntArray(n)
    for (i in 0 until n) {
        result[i] = if (i == 0) diff[0] else result[i - 1] + diff[i]
        frames += WalkFrame(
            status = "Running sum of D rebuilds the array: a[$i] = ${result[i]}.",
            cells = List(n) { j ->
                when {
                    j == i -> CellView(result[j].toString(), CellMark.ACTIVE)
                    j < i -> CellView(result[j].toString(), CellMark.WINDOW)
                    else -> CellView("·", CellMark.DIM)
                }
            },
            pointers = mapOf(i to "i"),
            aux = diffRow(setOf(i)),
            auxLabel = "D (difference)",
        )
    }

    frames += WalkFrame(
        status = "Three range updates cost 6 writes plus one final pass, instead of rewriting every covered element " +
            "three times.",
        cells = result.map { CellView(it.toString(), CellMark.RESULT) },
        aux = diffRow(),
        auxLabel = "D (difference)",
    )
    return frames
}

private fun slidingWindowFrames(): List<WalkFrame> {
    val a = listOf(2, 1, 5, 1, 3, 2, 7, 1)
    val k = 3
    val frames = mutableListOf<WalkFrame>()
    var sum = a.take(k).sum()
    var best = sum
    var bestStart = 0

    fun row(start: Int, active: Int? = null) = a.mapIndexed { i, v ->
        CellView(
            v.toString(),
            when {
                i == active -> CellMark.ACTIVE
                i in start until start + k -> CellMark.WINDOW
                else -> CellMark.IDLE
            },
        )
    }

    frames += WalkFrame(
        status = "First window a[0..${k - 1}] sums to $sum. Every later window shares $k−1 elements with the one " +
            "before it, so recomputing from scratch would redo work.",
        cells = row(0),
        pointers = mapOf(0 to "start", k - 1 to "end"),
        readout = "sum = $sum · best = $best",
    )

    for (start in 1..a.size - k) {
        val leaving = a[start - 1]
        val entering = a[start + k - 1]
        sum = sum - leaving + entering
        val improved = sum > best
        if (improved) {
            best = sum
            bestStart = start
        }
        frames += WalkFrame(
            status = "Slide: drop a[${start - 1}] = $leaving, take a[${start + k - 1}] = $entering → sum $sum" +
                if (improved) " — new best." else ".",
            cells = row(start, active = start + k - 1),
            pointers = mapOf(start to "start", start + k - 1 to "end"),
            readout = "sum = $sum · best = $best",
        )
    }

    frames += WalkFrame(
        status = "Best window is a[$bestStart..${bestStart + k - 1}] with sum $best, found in one pass: each element " +
            "is added once and removed once.",
        cells = a.mapIndexed { i, v ->
            CellView(v.toString(), if (i in bestStart until bestStart + k) CellMark.RESULT else CellMark.DIM)
        },
        readout = "best = $best",
    )
    return frames
}

private fun twoPointerFrames(): List<WalkFrame> {
    val a = listOf(1, 3, 4, 6, 8, 10, 13)
    val target = 14
    val frames = mutableListOf<WalkFrame>()
    val found = mutableSetOf<Int>()
    var lo = 0
    var hi = a.lastIndex

    fun row(active: Set<Int>) = a.mapIndexed { i, v ->
        CellView(
            v.toString(),
            when {
                i in found -> CellMark.DONE
                i in active -> CellMark.ACTIVE
                i in lo..hi -> CellMark.IDLE
                else -> CellMark.DIM
            },
        )
    }

    frames += WalkFrame(
        status = "Find every pair summing to $target. The array is sorted, which is what lets one pass replace the " +
            "nested loop.",
        cells = row(setOf(lo, hi)),
        pointers = mapOf(lo to "lo", hi to "hi"),
    )

    while (lo < hi) {
        val sum = a[lo] + a[hi]
        when {
            sum == target -> {
                found += lo
                found += hi
                frames += WalkFrame(
                    status = "${a[lo]} + ${a[hi]} = $target — a pair. Move both inward; nothing else pairs with " +
                        "either of them.",
                    cells = row(setOf(lo, hi)),
                    pointers = mapOf(lo to "lo", hi to "hi"),
                )
                lo++
                hi--
            }
            sum < target -> {
                frames += WalkFrame(
                    status = "${a[lo]} + ${a[hi]} = $sum, under $target. Only a larger left value can help, so lo moves right.",
                    cells = row(setOf(lo, hi)),
                    pointers = mapOf(lo to "lo", hi to "hi"),
                )
                lo++
            }
            else -> {
                frames += WalkFrame(
                    status = "${a[lo]} + ${a[hi]} = $sum, over $target. Only a smaller right value can help, so hi moves left.",
                    cells = row(setOf(lo, hi)),
                    pointers = mapOf(lo to "lo", hi to "hi"),
                )
                hi--
            }
        }
    }

    frames += WalkFrame(
        status = "Pointers met after ${a.size} steps total. Each comparison eliminates a whole row or column of the " +
            "pair table, which is why O(n) is enough.",
        cells = a.mapIndexed { i, v -> CellView(v.toString(), if (i in found) CellMark.RESULT else CellMark.DIM) },
    )
    return frames
}

private fun kadaneFrames(): List<WalkFrame> {
    val a = listOf(-2, 1, -3, 4, -1, 2, 1, -5, 4)
    val frames = mutableListOf<WalkFrame>()
    var current = a[0]
    var best = a[0]
    var start = 0
    var bestStart = 0
    var bestEnd = 0

    frames += WalkFrame(
        status = "Kadane keeps one number: the best sum of a subarray ending exactly here. Start with a[0] = ${a[0]}.",
        cells = a.mapIndexed { i, v -> CellView(v.toString(), if (i == 0) CellMark.ACTIVE else CellMark.IDLE) },
        pointers = mapOf(0 to "i"),
        readout = "current = $current · best = $best",
    )

    for (i in 1 until a.size) {
        val extended = current + a[i]
        val restart = extended < a[i]
        if (restart) {
            current = a[i]
            start = i
        } else {
            current = extended
        }
        if (current > best) {
            best = current
            bestStart = start
            bestEnd = i
        }
        frames += WalkFrame(
            status = if (restart) {
                "Extending would give $extended, worse than starting fresh at ${a[i]} — so the subarray restarts at index $i."
            } else {
                "Extending is better: current = ${current - a[i]} + ${a[i]} = $current."
            },
            cells = a.mapIndexed { j, v ->
                CellView(
                    v.toString(),
                    when {
                        j == i -> CellMark.ACTIVE
                        j in start until i -> CellMark.WINDOW
                        else -> CellMark.IDLE
                    },
                )
            },
            pointers = mapOf(i to "i"),
            readout = "current = $current · best = $best",
        )
    }

    frames += WalkFrame(
        status = "Maximum subarray is a[$bestStart..$bestEnd] summing to $best. One pass, one running value — no " +
            "need to remember any subarray but the current one.",
        cells = a.mapIndexed { i, v -> CellView(v.toString(), if (i in bestStart..bestEnd) CellMark.RESULT else CellMark.DIM) },
        readout = "best = $best",
    )
    return frames
}

private fun topKStreamFrames(): List<WalkFrame> {
    val stream = listOf(5, 1, 9, 3, 7, 2, 8)
    val k = 3
    val heap = sortedSetOf<Int>()
    val frames = mutableListOf<WalkFrame>()

    fun heapRow(active: Int? = null) = heap.map { CellView(it.toString(), if (it == active) CellMark.ACTIVE else CellMark.DONE) }
        .ifEmpty { listOf(CellView("empty", CellMark.DIM)) }

    frames += WalkFrame(
        status = "Values arrive one at a time and the top $k has to be current at every moment — sorting is not an " +
            "option, so keep a min-heap of size $k.",
        cells = stream.map { CellView(it.toString(), CellMark.DIM) },
        aux = heapRow(),
        auxLabel = "min-heap (size ≤ $k)",
    )

    stream.forEachIndexed { index, value ->
        val smallest = heap.firstOrNull()
        val status = when {
            heap.size < k -> {
                heap += value
                "Heap is not full yet — push $value."
            }
            value > smallest!! -> {
                heap.remove(smallest)
                heap += value
                "$value beats the heap minimum $smallest — evict $smallest, push $value."
            }
            else -> "$value is not larger than the heap minimum $smallest, so it can never be in the top $k. Discard it."
        }
        frames += WalkFrame(
            status = status,
            cells = stream.mapIndexed { i, v ->
                CellView(
                    v.toString(),
                    when {
                        i == index -> CellMark.ACTIVE
                        i < index -> CellMark.IDLE
                        else -> CellMark.DIM
                    },
                )
            },
            pointers = mapOf(index to "in"),
            aux = heapRow(active = if (value in heap) value else null),
            auxLabel = "min-heap (size ≤ $k)",
        )
    }

    frames += WalkFrame(
        status = "Top $k = ${heap.sortedDescending().joinToString(", ")}. Each arrival costs O(log $k), and only $k " +
            "values are ever stored — sorting the stream would need all of it in memory.",
        cells = stream.map { CellView(it.toString(), if (it in heap) CellMark.RESULT else CellMark.DIM) },
        aux = heap.map { CellView(it.toString(), CellMark.RESULT) },
        auxLabel = "min-heap (size ≤ $k)",
    )
    return frames
}

private fun topKFrequentFrames(): List<WalkFrame> {
    val a = listOf(1, 3, 1, 5, 3, 1, 7, 3, 5)
    val k = 2
    val frames = mutableListOf<WalkFrame>()
    val counts = LinkedHashMap<Int, Int>()

    fun countRow(active: Int? = null) = counts.entries.map { (value, count) ->
        CellView("$value×$count", if (value == active) CellMark.ACTIVE else CellMark.WINDOW)
    }.ifEmpty { listOf(CellView("empty", CellMark.DIM)) }

    frames += WalkFrame(
        status = "\"Top $k most frequent\" is two problems: count, then select. Only the second half is the heap pattern.",
        cells = a.map { CellView(it.toString(), CellMark.DIM) },
        aux = countRow(),
        auxLabel = "counts",
    )

    a.forEachIndexed { index, value ->
        counts[value] = (counts[value] ?: 0) + 1
        frames += WalkFrame(
            status = "Count pass: $value → ${counts.getValue(value)}.",
            cells = a.mapIndexed { i, v ->
                CellView(v.toString(), if (i == index) CellMark.ACTIVE else if (i < index) CellMark.IDLE else CellMark.DIM)
            },
            pointers = mapOf(index to "i"),
            aux = countRow(active = value),
            auxLabel = "counts",
        )
    }

    val heap = mutableListOf<Pair<Int, Int>>()
    for ((value, count) in counts) {
        val status: String
        if (heap.size < k) {
            heap += value to count
            status = "Heap not full — push $value (count $count)."
        } else {
            val weakest = heap.minByOrNull { it.second }!!
            if (count > weakest.second) {
                heap.remove(weakest)
                heap += value to count
                status = "$value appears $count times, more than ${weakest.first}'s ${weakest.second} — swap it in."
            } else {
                status = "$value appears only $count times, no better than the heap's weakest (${weakest.second}). Skip."
            }
        }
        frames += WalkFrame(
            status = status,
            cells = a.map { CellView(it.toString(), if (it == value) CellMark.ACTIVE else CellMark.DIM) },
            aux = heap.sortedByDescending { it.second }.map { CellView("${it.first}×${it.second}", CellMark.DONE) },
            auxLabel = "min-heap by count (size ≤ $k)",
        )
    }

    val winners = heap.map { it.first }.toSet()
    frames += WalkFrame(
        status = "Top $k = ${heap.sortedByDescending { it.second }.joinToString(", ") { "${it.first} (${it.second}×)" }}. " +
            "Sorting all counts would be O(m log m); the size-$k heap is O(m log $k).",
        cells = a.map { CellView(it.toString(), if (it in winners) CellMark.RESULT else CellMark.DIM) },
        aux = heap.sortedByDescending { it.second }.map { CellView("${it.first}×${it.second}", CellMark.RESULT) },
        auxLabel = "min-heap by count (size ≤ $k)",
    )
    return frames
}

private fun fastSlowFrames(): List<WalkFrame> {
    val nodes = listOf("A", "B", "C", "D", "E", "F", "G", "H")
    val loopEntry = 3
    fun next(i: Int) = if (i == nodes.lastIndex) loopEntry else i + 1
    val frames = mutableListOf<WalkFrame>()

    fun row(slow: Int, fast: Int) = nodes.mapIndexed { i, id ->
        CellView(
            id,
            when {
                i == slow && i == fast -> CellMark.RESULT
                i == fast -> CellMark.ACTIVE
                i == slow -> CellMark.WINDOW
                else -> CellMark.IDLE
            },
        )
    }

    // A cell can only carry one label, and "slow+fast" wraps to two lines in a cell-width slot.
    fun pointers(slow: Int, fast: Int) =
        if (slow == fast) mapOf(slow to "both") else mapOf(slow to "slow", fast to "fast")

    var slow = 0
    var fast = 0
    frames += WalkFrame(
        status = "This list ends by pointing back at ${nodes[loopEntry]}, so walking it never terminates. Two " +
            "pointers at different speeds settle it in O(1) extra space.",
        cells = row(slow, fast),
        pointers = pointers(slow, fast),
        loopBack = nodes.lastIndex to loopEntry,
    )

    do {
        slow = next(slow)
        fast = next(next(fast))
        frames += WalkFrame(
            status = "slow → ${nodes[slow]}, fast → ${nodes[fast]}." +
                if (slow == fast) " They are on the same node, which can only happen inside a loop." else "",
            cells = row(slow, fast),
            pointers = pointers(slow, fast),
            loopBack = nodes.lastIndex to loopEntry,
        )
    } while (slow != fast)

    frames += WalkFrame(
        status = "Meeting proves a cycle exists. To find where it starts, reset slow to the head and step both one " +
            "at a time.",
        cells = row(0, fast),
        pointers = mapOf(0 to "slow", fast to "fast"),
        loopBack = nodes.lastIndex to loopEntry,
    )

    slow = 0
    while (slow != fast) {
        slow = next(slow)
        fast = next(fast)
        frames += WalkFrame(
            status = "slow → ${nodes[slow]}, fast → ${nodes[fast]}.",
            cells = row(slow, fast),
            pointers = pointers(slow, fast),
            loopBack = nodes.lastIndex to loopEntry,
        )
    }

    frames += WalkFrame(
        status = "They meet again at ${nodes[slow]} — the entry point of the cycle. No visited set, no marking of " +
            "nodes, constant memory.",
        cells = nodes.mapIndexed { i, id -> CellView(id, if (i == slow) CellMark.RESULT else CellMark.DIM) },
        loopBack = nodes.lastIndex to loopEntry,
        readout = "cycle starts at ${nodes[slow]}",
    )
    return frames
}

private fun mergeIntervalsFrames(): List<WalkFrame> {
    val raw = listOf(1 to 3, 8 to 10, 2 to 6, 15 to 18, 9 to 12)
    val sorted = raw.sortedBy { it.first }
    val frames = mutableListOf<WalkFrame>()

    fun label(pair: Pair<Int, Int>) = "${pair.first}–${pair.second}"

    frames += WalkFrame(
        status = "Unsorted intervals: ${raw.joinToString(", ") { label(it) }}. Overlap is only a local question once " +
            "they are sorted by start.",
        cells = raw.map { CellView(label(it), CellMark.IDLE) },
        intervals = raw.map { IntervalView(it.first, it.second, CellMark.IDLE) },
    )
    frames += WalkFrame(
        status = "Sort by start: ${sorted.joinToString(", ") { label(it) }}. Now any interval can only overlap the " +
            "one being built.",
        cells = sorted.map { CellView(label(it), CellMark.WINDOW) },
        intervals = sorted.map { IntervalView(it.first, it.second, CellMark.WINDOW) },
    )

    val merged = mutableListOf<Pair<Int, Int>>()
    sorted.forEachIndexed { index, interval ->
        val open = merged.lastOrNull()
        val status: String
        if (open == null || interval.first > open.second) {
            merged += interval
            status = "${label(interval)} starts after ${if (open == null) "nothing is open" else "the open interval ends at ${open.second}"} — open a new interval."
        } else {
            merged[merged.lastIndex] = open.first to maxOf(open.second, interval.second)
            status = "${label(interval)} starts at ${interval.first}, inside the open ${label(open)} — extend it to " +
                "${label(merged.last())} instead of adding a row."
        }
        frames += WalkFrame(
            status = status,
            cells = sorted.mapIndexed { i, v ->
                CellView(label(v), if (i == index) CellMark.ACTIVE else if (i < index) CellMark.DIM else CellMark.IDLE)
            },
            pointers = mapOf(index to "i"),
            intervals = merged.map { IntervalView(it.first, it.second, CellMark.DONE) } +
                IntervalView(interval.first, interval.second, CellMark.ACTIVE),
        )
    }

    frames += WalkFrame(
        status = "${raw.size} intervals collapse to ${merged.size}: ${merged.joinToString(", ") { label(it) }}. The " +
            "sort dominates at O(n log n); the merge itself is one pass.",
        cells = merged.map { CellView(label(it), CellMark.RESULT) },
        intervals = merged.map { IntervalView(it.first, it.second, CellMark.RESULT) },
    )
    return frames
}

// Earliest-finish-first, then the same data under earliest-start-first, then the duration trap.
// The point of the topic is that three plausible sort keys are wrong, so the frames run them.
private fun activitySelectionFrames(): List<WalkFrame> {
    val raw = listOf(1 to 4, 3 to 5, 0 to 6, 5 to 7, 3 to 9, 5 to 9, 6 to 10, 8 to 11)
    val frames = mutableListOf<WalkFrame>()

    fun label(a: Pair<Int, Int>) = "${a.first}–${a.second}"

    // One greedy sweep under an arbitrary sort key, emitting a frame per decision.
    fun sweep(order: List<Pair<Int, Int>>, keyName: String, emitFrames: Boolean): List<Pair<Int, Int>> {
        val taken = mutableListOf<Pair<Int, Int>>()
        var lastFinish = Int.MIN_VALUE
        order.forEachIndexed { index, activity ->
            val fits = activity.first >= lastFinish
            if (fits) {
                taken += activity
                lastFinish = activity.second
            }
            if (emitFrames) {
                frames += WalkFrame(
                    status = if (fits) {
                        "${label(activity)} starts at ${activity.first} ≥ last finish " +
                            "${if (taken.size == 1) "(nothing taken yet)" else "${taken[taken.size - 2].second}"} — take it. Last finish is now ${activity.second}."
                    } else {
                        "${label(activity)} starts at ${activity.first}, before the last finish $lastFinish — it overlaps, so skip it and never look at it again."
                    },
                    cells = order.mapIndexed { i, a ->
                        CellView(
                            label(a),
                            when {
                                i == index -> if (fits) CellMark.DONE else CellMark.DIM
                                a in taken -> CellMark.DONE
                                i < index -> CellMark.DIM
                                else -> CellMark.IDLE
                            },
                        )
                    },
                    pointers = mapOf(index to keyName),
                    intervals = order.map {
                        IntervalView(it.first, it.second, if (it in taken) CellMark.DONE else if (it == activity) CellMark.ACTIVE else CellMark.IDLE)
                    },
                    readout = "taken: ${taken.size}",
                )
            }
        }
        return taken
    }

    frames += WalkFrame(
        status = "Eight activities on one resource. The bar chart is the overlap; the question is how many of them can run.",
        cells = raw.map { CellView(label(it), CellMark.IDLE) },
        intervals = raw.map { IntervalView(it.first, it.second, CellMark.IDLE) },
    )

    val byFinish = raw.sortedBy { it.second }
    frames += WalkFrame(
        status = "Sort by finish time: ${byFinish.joinToString(", ") { label(it) }}. Every decision from here is a single comparison against one number.",
        cells = byFinish.map { CellView(label(it), CellMark.WINDOW) },
        intervals = byFinish.map { IntervalView(it.first, it.second, CellMark.WINDOW) },
    )

    val chosen = sweep(byFinish, "f", emitFrames = true)
    frames += WalkFrame(
        status = "${chosen.size} activities selected: ${chosen.joinToString(", ") { label(it) }}. One pass, one variable, and this is provably a maximum-size set.",
        cells = chosen.map { CellView(label(it), CellMark.RESULT) },
        intervals = raw.map { IntervalView(it.first, it.second, if (it in chosen) CellMark.RESULT else CellMark.DIM) },
        readout = "optimum = ${chosen.size}",
    )

    // Same eight activities, sorted by start instead. The frames are suppressed — only the count matters.
    val byStart = raw.sortedBy { it.first }
    val startAnswer = sweep(byStart, "s", emitFrames = false)
    frames += WalkFrame(
        status = "Earliest start first on the identical set takes ${startAnswer.joinToString(", ") { label(it) }} — " +
            "${startAnswer.size} activities, not ${chosen.size}. 0–6 wins the sort and blocks three shorter activities behind it.",
        cells = byStart.map { CellView(label(it), if (it in startAnswer) CellMark.ACTIVE else CellMark.DIM) },
        intervals = byStart.map { IntervalView(it.first, it.second, if (it in startAnswer) CellMark.ACTIVE else CellMark.DIM) },
        readout = "earliest start = ${startAnswer.size}",
    )

    // Shortest duration needs its own three-activity counterexample; it happens to tie on the set above.
    val trap = listOf(0 to 5, 4 to 6, 5 to 10)
    val byDuration = trap.sortedBy { it.second - it.first }
    val durationAnswer = sweep(byDuration, "d", emitFrames = false)
    frames += WalkFrame(
        status = "Shortest duration first, on a set built to break it: 4–6 has length 2, wins the sort, and conflicts with " +
            "both 0–5 and 5–10. It selects ${durationAnswer.size}; finish-time order selects 2.",
        cells = byDuration.map { CellView(label(it), if (it in durationAnswer) CellMark.ACTIVE else CellMark.DIM) },
        intervals = byDuration.map { IntervalView(it.first, it.second, if (it in durationAnswer) CellMark.ACTIVE else CellMark.DIM) },
        readout = "shortest duration = ${durationAnswer.size} vs 2",
    )
    return frames
}

// Greedy and DP on the same {1,3,4} / target 6 instance, so the two-coin answer greed misses is
// visible rather than asserted.
private fun coinChangeGreedyFrames(): List<WalkFrame> {
    val coins = listOf(4, 3, 1)          // descending — greedy's consumption order
    val target = 6
    val frames = mutableListOf<WalkFrame>()

    // The row is the amount axis 0..6; the pointer is what greed still has left to make.
    fun amountRow(remaining: Int) = (0..target).map { a ->
        CellView(
            a.toString(),
            when {
                a == remaining -> CellMark.ACTIVE
                a > remaining -> CellMark.DIM
                else -> CellMark.IDLE
            },
        )
    }

    frames += WalkFrame(
        status = "On {1, 5, 10, 25}, greedy change is correct: 68¢ becomes 25+25+10+5+1+1+1 and no shorter answer exists. " +
            "That is a fact about the coins, not about the algorithm.",
        cells = (0..target).map { CellView(it.toString(), CellMark.IDLE) },
        readout = "now try {1, 3, 4} at 6",
    )

    var remaining = target
    val taken = mutableListOf<Int>()
    frames += WalkFrame(
        status = "Coins {1, 3, 4}, target 6. Greedy takes the largest coin that fits, repeatedly.",
        cells = amountRow(remaining),
        pointers = mapOf(remaining to "left"),
    )
    while (remaining > 0) {
        val before = remaining
        val coin = coins.first { it <= remaining }
        taken += coin
        remaining -= coin
        frames += WalkFrame(
            status = "Largest coin that fits in $before is $coin — take it. Remaining $remaining, coins used ${taken.size}.",
            cells = amountRow(remaining),
            pointers = mapOf(remaining to "left"),
            readout = "greedy: ${taken.joinToString(" + ")} = ${taken.size} coins",
        )
    }
    val greedyCount = taken.size

    // Same instance, tabulated.
    val unreachable = target + 1
    val dp = IntArray(target + 1) { unreachable }
    dp[0] = 0
    fun dpRow(upTo: Int, active: Int? = null) = (0..target).map { a ->
        CellView(
            if (dp[a] == unreachable) "∞" else dp[a].toString(),
            when {
                a == active -> CellMark.ACTIVE
                a <= upTo -> CellMark.WINDOW
                else -> CellMark.DIM
            },
        )
    }

    frames += WalkFrame(
        status = "Greed answered $greedyCount coins. Now tabulate: dp[a] is the fewest coins that make exactly a.",
        cells = (0..target).map { CellView(it.toString(), CellMark.IDLE) },
        aux = dpRow(0),
        auxLabel = "dp (fewest coins)",
    )
    for (a in 1..target) {
        var bestCoin = 0
        for (coin in coins) {
            if (coin <= a && dp[a - coin] + 1 < dp[a]) {
                dp[a] = dp[a - coin] + 1
                bestCoin = coin
            }
        }
        frames += WalkFrame(
            status = "dp[$a] = 1 + dp[${a - bestCoin}] = ${dp[a]}, taking a $bestCoin.",
            cells = (0..target).map { CellView(it.toString(), if (it == a) CellMark.ACTIVE else if (it < a) CellMark.WINDOW else CellMark.IDLE) },
            pointers = mapOf(a to "a"),
            aux = dpRow(a - 1, active = a),
            auxLabel = "dp (fewest coins)",
        )
    }

    frames += WalkFrame(
        status = "dp[6] = ${dp[target]} — the split 3 + 3. Greed took ${taken.joinToString(" + ")}, $greedyCount coins, because taking the 4 " +
            "left a remainder that only 1s can fill. The rule never had to be wrong; these denominations are simply not canonical.",
        cells = (0..target).map { CellView(it.toString(), if (it == target) CellMark.RESULT else CellMark.DIM) },
        aux = dpRow(target, active = target),
        auxLabel = "dp (fewest coins)",
        readout = "greedy $greedyCount coins · optimal ${dp[target]} coins",
    )
    return frames
}

// ── Math & number theory ─────────────────────────────────────────────────────

// The remainder chain as a growing row, then the back-substitution that turns it into Bézout
// coefficients — because the coefficients are what modular inverses and CRT actually consume.
private fun euclidGcdFrames(): List<WalkFrame> {
    val a0 = 252L
    val b0 = 105L
    val frames = mutableListOf<WalkFrame>()
    val chain = mutableListOf(a0, b0)
    val steps = mutableListOf<Triple<Long, Long, Long>>()   // (dividend, quotient, remainder)

    frames += WalkFrame(
        status = "gcd($a0, $b0). The identity that drives everything: gcd(a, b) = gcd(b, a mod b) — both pairs have " +
            "exactly the same common divisors, not merely the same greatest one.",
        cells = chain.map { CellView(it.toString(), CellMark.ACTIVE) },
    )

    var a = a0
    var b = b0
    while (b != 0L) {
        val q = a / b
        val r = a % b
        steps += Triple(a, q, r)
        chain += r
        frames += WalkFrame(
            status = "$a = $q × $b + $r." + if (r == 0L) " Remainder zero — the previous value, $b, is the answer." else " Replace the pair with ($b, $r) and repeat.",
            cells = chain.mapIndexed { i, v ->
                CellView(
                    v.toString(),
                    when {
                        i == chain.lastIndex -> if (r == 0L) CellMark.DIM else CellMark.ACTIVE
                        i == chain.lastIndex - 1 -> if (r == 0L) CellMark.RESULT else CellMark.WINDOW
                        else -> CellMark.DIM
                    },
                )
            },
            readout = "step ${steps.size}",
        )
        a = b
        b = r
    }
    val g = a

    frames += WalkFrame(
        status = "gcd = $g in ${steps.size} divisions. Lamé's bound is five times the digit count of the smaller input, and the " +
            "worst case is a pair of consecutive Fibonacci numbers — every remainder then lands exactly on the previous one.",
        cells = chain.map { CellView(it.toString(), if (it == g) CellMark.RESULT else CellMark.DIM) },
        readout = "gcd($a0, $b0) = $g",
    )
    frames += WalkFrame(
        status = "lcm = $a0 / $g × $b0 = ${a0 / g * b0}. Divide before multiplying: $a0 × $b0 can overflow for inputs whose " +
            "lcm comfortably would not.",
        cells = chain.map { CellView(it.toString(), CellMark.DIM) },
        readout = "lcm($a0, $b0) = ${a0 / g * b0}",
    )

    // Back-substitution: walk the division steps in reverse, rewriting g as a combination.
    var x = 1L
    var y = 0L
    // Every division step contributes one unwind, including the last one — dropping it silently
    // produces coefficients for the wrong pair.
    for ((dividend, q, _) in steps.reversed()) {
        val newX = y
        val newY = x - q * y
        x = newX
        y = newY
        frames += WalkFrame(
            status = "Back-substitute through $dividend = $q × … : $g = $a0 × $x + $b0 × $y." +
                if (x == 1L && y == 0L) "" else " Check: ${a0 * x} + ${b0 * y} = ${a0 * x + b0 * y}.",
            cells = chain.mapIndexed { i, v -> CellView(v.toString(), if (i == chain.lastIndex - 1) CellMark.RESULT else CellMark.DIM) },
            readout = "$a0·$x + $b0·$y = ${a0 * x + b0 * y}",
        )
    }

    // The frames state these numbers, so pin them: an off-by-one in the unwind produces coefficients
    // for the wrong pair and every caption above stays plausible.
    require(a0 * x + b0 * y == g) { "euclid_gcd Bézout is wrong: $a0·$x + $b0·$y != $g" }
    require(x == -2L && y == 5L) { "euclid_gcd expected (x, y) = (-2, 5), got ($x, $y)" }

    frames += WalkFrame(
        status = "Bézout: $a0 × $x + $b0 × $y = $g. Those coefficients are the whole reason the extended version exists — " +
            "when gcd is 1 the coefficient of a is a's modular inverse, and that is where CRT and RSA key generation start.",
        cells = chain.map { CellView(it.toString(), if (it == g) CellMark.RESULT else CellMark.DIM) },
        readout = "x = $x, y = $y",
    )
    return frames
}

// Residue rows: the multiples of a under a modulus. Whether 1 ever appears in that row is exactly
// whether a is invertible, so the inverse is something seen rather than asserted.
private fun modularArithmeticFrames(): List<WalkFrame> {
    val frames = mutableListOf<WalkFrame>()

    fun residueRow(m: Int) = (0 until m).map { CellView(it.toString(), CellMark.IDLE) }

    fun multiplesRow(a: Int, m: Int, highlightOne: Boolean) = (0 until m).map { k ->
        val v = a * k % m
        CellView(v.toString(), if (highlightOne && v == 1) CellMark.RESULT else if (v == 0) CellMark.DIM else CellMark.WINDOW)
    }

    frames += WalkFrame(
        status = "Working mod 7, every integer collapses onto one of seven residues. 17 ≡ 3 and 23 ≡ 2, because each differs " +
            "from its residue by a multiple of 7.",
        cells = residueRow(7),
        pointers = mapOf(3 to "17", 2 to "23"),
    )
    frames += WalkFrame(
        status = "Addition survives the collapse: 17 + 23 = 40 ≡ ${40 % 7}, and (3 + 2) mod 7 = ${(3 + 2) % 7}. They agree, which is " +
            "what lets you reduce at every step instead of at the end.",
        cells = residueRow(7).mapIndexed { i, c -> if (i == 40 % 7) CellView(c.text, CellMark.RESULT) else c },
        readout = "17 + 23 ≡ ${40 % 7} (mod 7)",
    )
    frames += WalkFrame(
        status = "Multiplication too: 17 × 23 = 391 ≡ ${391 % 7}, and (3 × 2) mod 7 = ${3 * 2 % 7}.",
        cells = residueRow(7).mapIndexed { i, c -> if (i == 391 % 7) CellView(c.text, CellMark.RESULT) else c },
        readout = "17 × 23 ≡ ${391 % 7} (mod 7)",
    )
    frames += WalkFrame(
        status = "Subtraction survives mathematically but not in Kotlin: 17 − 23 = −6, and −6 % 7 is −6, because % takes the " +
            "dividend's sign. The residue wanted is ${((-6 % 7) + 7) % 7}, so normalise with ((a % m) + m) % m.",
        cells = residueRow(7).mapIndexed { i, c -> if (i == ((-6 % 7) + 7) % 7) CellView(c.text, CellMark.RESULT) else c },
        readout = "−6 % 7 = −6 in code · ${((-6 % 7) + 7) % 7} in maths",
    )

    frames += WalkFrame(
        status = "Division is the operation that does not survive. Dividing by 3 means multiplying by some 3⁻¹ with 3 × 3⁻¹ ≡ 1. " +
            "The row below is 3k mod 7 for k = 0 … 6 — the question is whether 1 appears in it.",
        cells = residueRow(7),
        aux = multiplesRow(3, 7, highlightOne = false),
        auxLabel = "3k mod 7",
    )
    frames += WalkFrame(
        status = "It does, at k = 5: 3 × 5 = 15 = 2×7 + 1. So 3⁻¹ ≡ 5 (mod 7). The row hits every residue exactly once, which is " +
            "what gcd(3, 7) = 1 guarantees.",
        cells = residueRow(7).mapIndexed { i, c -> if (i == 5) CellView(c.text, CellMark.ACTIVE) else c },
        pointers = mapOf(5 to "k"),
        aux = multiplesRow(3, 7, highlightOne = true),
        auxLabel = "3k mod 7",
        readout = "3⁻¹ ≡ 5 (mod 7)",
    )
    frames += WalkFrame(
        status = "Now mod 4, with a = 2. gcd(2, 4) = 2, and the row is 2k mod 4 — every entry is even, so 1 never appears and 2 has " +
            "no inverse at all. An inverse exists exactly when gcd(a, m) = 1; this is that condition made visible.",
        cells = residueRow(4),
        aux = multiplesRow(2, 4, highlightOne = true),
        auxLabel = "2k mod 4",
        readout = "no inverse: gcd(2, 4) = 2",
    )
    return frames
}

private fun sieveFrames(): List<WalkFrame> {
    val n = 30
    val frames = mutableListOf<WalkFrame>()
    val isPrime = BooleanArray(n + 1) { it >= 2 }
    val split = 16   // first row 2..16, aux row 17..30 — one row of 29 cells would be unreadable

    fun rowFor(range: IntRange, active: Set<Int>, struck: Set<Int>) = range.map { v ->
        CellView(
            v.toString(),
            when {
                v in active -> CellMark.ACTIVE
                v in struck -> CellMark.RESULT
                !isPrime[v] -> CellMark.DIM
                else -> CellMark.WINDOW
            },
        )
    }

    fun frame(status: String, active: Set<Int> = emptySet(), struck: Set<Int> = emptySet(), readout: String? = null) {
        frames += WalkFrame(
            status = status,
            cells = rowFor(2..split, active, struck),
            aux = rowFor((split + 1)..n, active, struck),
            auxLabel = "${split + 1} … $n",
            readout = readout,
        )
    }

    frame("Everything from 2 to $n starts as a candidate. Nothing here will ever be divided — composites are removed by marking.")

    var p = 2
    while (p * p <= n) {
        if (isPrime[p]) {
            // Start at p², not 2p: every k·p with k < p already went when k's own prime factors did.
            val struck = mutableSetOf<Int>()
            var multiple = p * p
            while (multiple <= n) {
                if (isPrime[multiple]) struck += multiple
                isPrime[multiple] = false
                multiple += p
            }
            frame(
                "$p survives, so it is prime. Strike its multiples starting at ${p * p} — not at ${2 * p}, because every k·$p with " +
                    "k < $p was already removed when k's own prime factors were processed. " +
                    if (struck.isEmpty()) "Nothing new was left to strike." else "Struck ${struck.sorted().joinToString(", ")}.",
                active = setOf(p),
                struck = struck,
                readout = "p = $p, marking from ${p * p}",
            )
        }
        p++
    }

    val primes = (2..n).filter { isPrime[it] }
    require(primes == listOf(2, 3, 5, 7, 11, 13, 17, 19, 23, 29)) { "sieve to $n produced $primes" }
    frame(
        "The outer loop stopped at p·p > $n, so only ${(2..n).filter { isPrime[it] && it * it <= n }.joinToString(", ")} ever marked " +
            "anything — a composite below $n must have a factor at most √$n. Everything still standing is prime: " +
            "${primes.joinToString(", ")}.",
        readout = "${primes.size} primes below $n · O(n log log n)",
    )
    return frames
}

private fun fermatFrames(): List<WalkFrame> {
    val p = 7
    val frames = mutableListOf<WalkFrame>()

    fun powerRow(a: Int, upTo: Int) = (1..p - 1).map { e ->
        var v = 1
        repeat(e) { v = v * a % p }
        CellView(
            if (e <= upTo) v.toString() else "·",
            when {
                e > upTo -> CellMark.DIM
                v == 1 && e == p - 1 -> CellMark.RESULT
                e == upTo -> CellMark.ACTIVE
                else -> CellMark.WINDOW
            },
        )
    }

    frames += WalkFrame(
        status = "Fermat's claim: for a prime p and any a it does not divide, a^(p−1) ≡ 1 (mod $p). The row is the exponent, " +
            "1 through ${p - 1}.",
        cells = (1..p - 1).map { CellView(it.toString(), CellMark.IDLE) },
        aux = powerRow(3, 0),
        auxLabel = "3^e mod $p",
    )
    for (e in 1..p - 1) {
        var v = 1
        repeat(e) { v = v * 3 % p }
        frames += WalkFrame(
            status = "3^$e ≡ $v (mod $p)." + if (e == p - 1) " There it is — and note the row hit every non-zero residue exactly once on the way, which makes 3 a primitive root mod $p." else "",
            cells = (1..p - 1).map { CellView(it.toString(), if (it == e) CellMark.ACTIVE else if (it < e) CellMark.WINDOW else CellMark.IDLE) },
            pointers = mapOf(e - 1 to "e"),
            aux = powerRow(3, e),
            auxLabel = "3^e mod $p",
            readout = "3^$e ≡ $v",
        )
    }
    frames += WalkFrame(
        status = "Base 2 lands on 1 every three steps rather than every six, but still lands on 1 at exponent ${p - 1} — an element's " +
            "order always divides p − 1, which is why the theorem holds for every base at once.",
        cells = (1..p - 1).map { CellView(it.toString(), CellMark.DIM) },
        aux = powerRow(2, p - 1),
        auxLabel = "2^e mod $p",
        readout = "order of 2 is 3, and 3 divides ${p - 1}",
    )

    var inverse = 1
    repeat(p - 2) { inverse = inverse * 3 % p }
    frames += WalkFrame(
        status = "Drop the exponent by one and you have division: 3^${p - 2} ≡ $inverse, and 3 × $inverse = ${3 * inverse} ≡ ${3 * inverse % p} (mod $p). " +
            "That is how a fraction is computed \"mod 10⁹ + 7\" — the modulus is prime, so every non-zero residue is invertible.",
        cells = (1..p - 1).map { CellView(it.toString(), if (it == p - 2) CellMark.RESULT else CellMark.DIM) },
        aux = powerRow(3, p - 1),
        auxLabel = "3^e mod $p",
        readout = "3⁻¹ ≡ $inverse (mod $p)",
    )

    // Read backwards it is a primality test, and read backwards it is unreliable.
    val carmichael = 561
    val factors = listOf(3, 11, 17)
    frames += WalkFrame(
        status = "Reversed, this is a primality test: a^(n−1) ≢ 1 proves n composite. But it never proves the converse. " +
            "$carmichael = ${factors.joinToString(" × ")} passes for every base coprime to it, because it is squarefree and " +
            "${factors.joinToString(", ") { "${it - 1} | ${carmichael - 1}" }}. Carmichael numbers are infinite in supply, so " +
            "Miller-Rabin — which also checks square roots of 1 — is what actually gets used.",
        cells = factors.map { CellView(it.toString(), CellMark.RESULT) } + CellView("= $carmichael", CellMark.DIM),
        readout = "561 is composite and passes every Fermat round",
    )
    return frames
}

// The three congruences as successive filters over 0 … 31, so the unique survivor below the product
// is watched appearing rather than produced by a formula.
private fun crtFrames(): List<WalkFrame> {
    val remainders = listOf(2, 3, 2)
    val moduli = listOf(3, 5, 7)
    val product = moduli.reduce(Int::times)
    val shown = 32
    val split = 15
    val frames = mutableListOf<WalkFrame>()
    var alive = (0 until shown).toSet()

    fun rowFor(range: IntRange, live: Set<Int>, justCut: Set<Int>) = range.map { v ->
        CellView(
            v.toString(),
            when {
                v in justCut -> CellMark.DIM
                live.size == 1 && v in live -> CellMark.RESULT
                v in live -> CellMark.WINDOW
                else -> CellMark.DIM
            },
        )
    }

    fun frame(status: String, live: Set<Int>, justCut: Set<Int> = emptySet(), readout: String? = null) {
        frames += WalkFrame(
            status = status,
            cells = rowFor(0..split, live, justCut),
            aux = rowFor((split + 1) until shown, live, justCut),
            auxLabel = "${split + 1} … ${shown - 1}",
            readout = readout,
        )
    }

    frame(
        "Sunzi's puzzle: a number leaving remainder 2 under 3, 3 under 5 and 2 under 7. The moduli are pairwise coprime, so a " +
            "solution exists and is unique modulo 3 × 5 × 7 = $product.",
        alive,
    )

    for (i in moduli.indices) {
        val survivors = alive.filter { it % moduli[i] == remainders[i] }.toSet()
        val cut = alive - survivors
        alive = survivors
        frame(
            "x ≡ ${remainders[i]} (mod ${moduli[i]}) leaves ${alive.sorted().joinToString(", ")}. " +
                if (alive.size == 1) "One survivor below $product, exactly as the theorem promises." else "${alive.size} candidates remain in this window.",
            alive,
            cut,
            readout = "after ${i + 1} congruence${if (i == 0) "" else "s"}: ${alive.size} left",
        )
    }

    // The construction, which finds the same answer without any filtering.
    val answer = alive.single()
    require(answer == 23) { "CRT filter left $answer, expected 23" }
    val partials = moduli.map { product / it }
    val inverses = moduli.mapIndexed { i, m -> (1 until m).first { partials[i] % m * it % m == 1 } }
    val terms = remainders.indices.map { remainders[it] * partials[it] * inverses[it] }
    // Same answer from the closed form; if the two ever disagree the caption below is a lie.
    require(terms.sum() % product == answer) { "CRT construction gave ${terms.sum() % product}, filter gave $answer" }

    frame(
        "The construction gets there without scanning. Mᵢ = $product / mᵢ is ${partials.joinToString(", ")} — each divisible by every " +
            "modulus but its own — and multiplying by Mᵢ⁻¹ mod mᵢ (${inverses.joinToString(", ")}) makes each term 1 in its own modulus and 0 " +
            "in the others. ${terms.joinToString(" + ")} = ${terms.sum()}, and ${terms.sum()} mod $product = ${terms.sum() % product}.",
        alive,
        readout = "x ≡ $answer (mod $product)",
    )
    frame(
        "Coprimality is doing all the work. With moduli 4 and 6, x ≡ 1 (mod 4) and x ≡ 2 (mod 6) have no solution at all: the first " +
            "forces x odd, the second forces it even. The general form is solvable exactly when the remainders agree modulo each pair's gcd.",
        alive,
        readout = "unique mod $product · $answer, 128, 233, …",
    )
    return frames
}

// ── String algorithms ────────────────────────────────────────────────────────

// The aux row is the pattern drawn where it currently sits, so the shift of one — and the
// re-comparison it forces — is a movement on screen rather than a claim in the caption.
private fun naiveSearchFrames(): List<WalkFrame> {
    val text = "ABCABCABD"
    val pattern = "ABCABD"
    val frames = mutableListOf<WalkFrame>()

    fun patternRow(shift: Int, upTo: Int, failed: Int?) = List(text.length) { i ->
        val j = i - shift
        when {
            j < 0 || j >= pattern.length -> CellView("·", CellMark.DIM)
            j == failed -> CellView(pattern[j].toString(), CellMark.RESULT)
            j < upTo -> CellView(pattern[j].toString(), CellMark.DONE)
            j == upTo -> CellView(pattern[j].toString(), CellMark.ACTIVE)
            else -> CellView(pattern[j].toString(), CellMark.IDLE)
        }
    }

    frames += WalkFrame(
        status = "Text \"$text\", pattern \"$pattern\". No preprocessing and no extra memory — align, compare, shift by one.",
        cells = text.map { CellView(it.toString()) },
        aux = patternRow(0, 0, null),
        auxLabel = "pattern",
    )

    var comparisons = 0
    val hits = mutableListOf<Int>()
    for (shift in 0..text.length - pattern.length) {
        var j = 0
        while (j < pattern.length && text[shift + j] == pattern[j]) {
            comparisons++
            j++
            frames += WalkFrame(
                status = "Shift $shift: '${pattern[j - 1]}' matches text[${shift + j - 1}]. $j of ${pattern.length} characters agree.",
                cells = text.mapIndexed { i, c ->
                    CellView(c.toString(), if (i in shift until shift + j) CellMark.WINDOW else if (i < shift) CellMark.DIM else CellMark.IDLE)
                },
                pointers = mapOf(shift + j - 1 to "i"),
                aux = patternRow(shift, j, null),
                auxLabel = "pattern",
                readout = "comparisons: $comparisons",
            )
        }
        if (j == pattern.length) {
            hits += shift
            frames += WalkFrame(
                status = "Shift $shift: the pattern ran out — every character agreed, so this is a match.",
                cells = text.mapIndexed { i, c -> CellView(c.toString(), if (i in shift until shift + pattern.length) CellMark.RESULT else CellMark.DIM) },
                aux = patternRow(shift, pattern.length, null),
                auxLabel = "pattern",
                readout = "match at $shift · comparisons: $comparisons",
            )
        } else {
            comparisons++
            frames += WalkFrame(
                status = "Shift $shift: '${pattern[j]}' ≠ text[${shift + j}] = '${text[shift + j]}'. Mismatch — slide the pattern one position right. " +
                    (if (j > 0) "The $j character${if (j == 1) "" else "s"} that just matched are discarded, and $j of them will be compared again." else "Nothing was learned to discard here."),
                cells = text.mapIndexed { i, c ->
                    CellView(c.toString(), if (i == shift + j) CellMark.RESULT else if (i in shift until shift + j) CellMark.WINDOW else CellMark.DIM)
                },
                pointers = mapOf(shift + j to "i"),
                aux = patternRow(shift, j, j),
                auxLabel = "pattern",
                readout = "comparisons: $comparisons",
            )
        }
    }

    frames += WalkFrame(
        status = "Found at ${hits.joinToString(", ")} in $comparisons comparisons over ${text.length - pattern.length + 1} alignments. " +
            "On ordinary text most mismatches land on the first character, so this is close to linear in practice.",
        cells = text.mapIndexed { i, c -> CellView(c.toString(), if (hits.any { i in it until it + pattern.length }) CellMark.RESULT else CellMark.DIM) },
        readout = "$comparisons comparisons",
    )

    // The worst case, counted rather than described.
    val badText = "AAAAAAAAAB"
    val badPattern = "AAAB"
    var badComparisons = 0
    for (shift in 0..badText.length - badPattern.length) {
        var j = 0
        while (j < badPattern.length && badText[shift + j] == badPattern[j]) { badComparisons++; j++ }
        if (j < badPattern.length) badComparisons++
    }
    frames += WalkFrame(
        status = "Now the pathological input: text \"$badText\", pattern \"$badPattern\". Every alignment matches three characters " +
            "and fails on the fourth — $badComparisons comparisons for a ${badText.length}-character text, which is (n − m + 1)·m almost exactly. " +
            "KMP's prefix table exists to record what those matched characters already established.",
        cells = badText.map { CellView(it.toString(), CellMark.DIM) },
        aux = List(badText.length) { i -> if (i < badPattern.length) CellView(badPattern[i].toString(), CellMark.RESULT) else CellView("·", CellMark.DIM) },
        auxLabel = "pattern",
        readout = "$badComparisons comparisons · n·m = ${badText.length * badPattern.length}",
    )
    return frames
}

// Z[i] is how far the suffix at i agrees with the string's own prefix. The [l, r) window is drawn as
// the pointer row, because the whole claim of linearity is that r only ever moves right.
private fun zAlgorithmFrames(): List<WalkFrame> {
    val s = "aabcaabxaaz"
    val n = s.length
    val z = IntArray(n)
    val frames = mutableListOf<WalkFrame>()

    fun zRow(upTo: Int, active: Int? = null) = List(n) { i ->
        when {
            i == 0 -> CellView("–", CellMark.DIM)
            i == active -> CellView(z[i].toString(), CellMark.ACTIVE)
            i <= upTo -> CellView(z[i].toString(), CellMark.WINDOW)
            else -> CellView("·", CellMark.DIM)
        }
    }

    frames += WalkFrame(
        status = "Z[i] is the length of the longest run starting at i that also matches the start of the string. Z[0] is left undefined.",
        cells = s.map { CellView(it.toString()) },
        aux = zRow(0),
        auxLabel = "Z",
    )

    var l = 0
    var r = 0
    var copied = 0
    var compared = 0
    for (i in 1 until n) {
        val insideWindow = i < r
        val mirror = i - l
        if (insideWindow) {
            z[i] = minOf(r - i, z[mirror])
            copied++
        }
        val startedAt = z[i]
        while (i + z[i] < n && s[z[i]] == s[i + z[i]]) {
            z[i]++
            compared++
        }
        val extended = z[i] - startedAt
        val grew = i + z[i] > r
        if (grew) {
            l = i
            r = i + z[i]
        }
        frames += WalkFrame(
            status = buildString {
                append("i = $i: ")
                if (insideWindow) {
                    append("inside the known window, so the mirror at $mirror supplies a starting value of $startedAt without a single comparison. ")
                } else {
                    append("outside any known window, so compare from scratch. ")
                }
                append(if (extended == 0) "No extension." else "Extended $extended character${if (extended == 1) "" else "s"}.")
                append(" Z[$i] = ${z[i]}.")
                if (grew) append(" That pushed the right edge to ${r} — it never moves left, which is why the total comparison work is O(n).")
            },
            cells = s.mapIndexed { j, c ->
                CellView(
                    c.toString(),
                    when {
                        j in i until i + z[i] -> CellMark.RESULT
                        j < z[i] -> CellMark.DONE
                        j == i -> CellMark.ACTIVE
                        else -> CellMark.IDLE
                    },
                )
            },
            pointers = buildMap {
                put(i, "i")
                if (l != r) {
                    put(l, "l")
                    put((r - 1).coerceAtLeast(l), "r")
                }
            },
            aux = zRow(i - 1, active = i),
            auxLabel = "Z",
            readout = "copied from mirror: $copied · characters compared: $compared",
        )
    }

    frames += WalkFrame(
        status = "Z = [${z.indices.joinToString(", ") { if (it == 0) "–" else z[it].toString() }}]. $compared character comparisons for " +
            "an $n-character string. Concatenate pattern + '\$' + text and every Z equal to the pattern's length is an occurrence — " +
            "that is O(n + m) matching out of a general-purpose array.",
        cells = s.map { CellView(it.toString(), CellMark.DIM) },
        aux = zRow(n - 1),
        auxLabel = "Z",
        readout = "$compared comparisons for n = $n",
    )
    return frames
}

// Expand around centre. Both kinds of centre get their own frames, because skipping the even ones is
// the bug the topic is mostly about.
private fun longestPalindromeFrames(): List<WalkFrame> {
    val s = "abacabad"
    val frames = mutableListOf<WalkFrame>()
    var bestStart = 0
    var bestLength = 1

    frames += WalkFrame(
        status = "A palindrome is defined by its centre, not its endpoints — so enumerate centres. There are " +
            "${2 * s.length - 1} of them here: ${s.length} characters and ${s.length - 1} gaps.",
        cells = s.map { CellView(it.toString()) },
        readout = "${2 * s.length - 1} centres",
    )

    for (centre in s.indices) {
        for (even in listOf(false, true)) {
            var left = centre
            var right = if (even) centre + 1 else centre
            // An even centre whose two characters differ spans nothing at all; it gets no frame so
            // the playback stays about expansions that actually happen.
            if (even && (right >= s.length || s[left] != s[right])) continue
            while (left >= 0 && right < s.length && s[left] == s[right]) {
                left--
                right++
            }
            val length = right - left - 1
            val start = left + 1
            val improved = length > bestLength
            if (improved) {
                bestLength = length
                bestStart = start
            }
            frames += WalkFrame(
                status = "${if (even) "Even" else "Odd"} centre at ${if (even) "the gap after index $centre" else "index $centre"}: " +
                    "expanded to \"${s.substring(start, start + length)}\", length $length. " +
                    if (improved) "New best." else "Shorter than the best so far ($bestLength).",
                cells = s.mapIndexed { i, c ->
                    CellView(
                        c.toString(),
                        when {
                            i in start until start + length -> if (improved) CellMark.RESULT else CellMark.WINDOW
                            i in bestStart until bestStart + bestLength -> CellMark.DONE
                            else -> CellMark.IDLE
                        },
                    )
                },
                pointers = buildMap {
                    put(start, "l")
                    put(start + length - 1, "r")
                },
                readout = "best: \"${s.substring(bestStart, bestStart + bestLength)}\" ($bestLength)",
            )
        }
    }

    frames += WalkFrame(
        status = "Longest palindromic substring: \"${s.substring(bestStart, bestStart + bestLength)}\", length $bestLength. " +
            "${2 * s.length - 1} centres each expanding at most n/2 gives O(n²) time in O(1) space. Manacher reaches O(n) by " +
            "initialising each centre's radius from its mirror — the same window trick the Z-algorithm uses.",
        cells = s.mapIndexed { i, c ->
            CellView(c.toString(), if (i in bestStart until bestStart + bestLength) CellMark.RESULT else CellMark.DIM)
        },
        readout = "\"${s.substring(bestStart, bestStart + bestLength)}\" · O(n²) time, O(1) space",
    )
    return frames
}

private fun longestUniqueWindowFrames(): List<WalkFrame> {
    val s = "abcabcbb".toList()
    val frames = mutableListOf<WalkFrame>()
    val seen = mutableMapOf<Char, Int>()
    var start = 0
    var best = 0
    var bestStart = 0

    frames += WalkFrame(
        status = "Longest substring without a repeated character. A window that only ever grows on the right and " +
            "shrinks on the left visits each index twice at most.",
        cells = s.map { CellView(it.toString(), CellMark.IDLE) },
        readout = "best = 0",
    )

    s.forEachIndexed { end, ch ->
        val previous = seen[ch]
        val moved = previous != null && previous >= start
        if (moved) start = previous!! + 1
        seen[ch] = end
        val length = end - start + 1
        val improved = length > best
        if (improved) {
            best = length
            bestStart = start
        }
        frames += WalkFrame(
            status = if (moved) {
                "'$ch' repeats (last seen at index $previous) — pull start to ${start} so the window stays unique. Length $length."
            } else {
                "'$ch' is new to the window — extend right. Length $length" + if (improved) ", a new best." else "."
            },
            cells = s.mapIndexed { i, c ->
                CellView(
                    c.toString(),
                    when {
                        i == end -> CellMark.ACTIVE
                        i in start until end -> CellMark.WINDOW
                        else -> CellMark.IDLE
                    },
                )
            },
            pointers = mapOf(start to "start", end to "end"),
            readout = "window = $length · best = $best",
        )
    }

    frames += WalkFrame(
        status = "Longest unique window is \"${s.subList(bestStart, bestStart + best).joinToString("")}\" at length " +
            "$best. Neither pointer ever moves backwards, which is what keeps it linear.",
        cells = s.mapIndexed { i, c ->
            CellView(c.toString(), if (i in bestStart until bestStart + best) CellMark.RESULT else CellMark.DIM)
        },
        readout = "best = $best",
    )
    return frames
}

private fun dedupeInPlaceFrames(): List<WalkFrame> {
    val a = mutableListOf(1, 1, 2, 2, 3, 4, 4, 5)
    val frames = mutableListOf<WalkFrame>()
    var write = 0

    fun row(read: Int) = a.mapIndexed { i, v ->
        CellView(
            v.toString(),
            when {
                i == read && i == write -> CellMark.ACTIVE
                i == read -> CellMark.ACTIVE
                i == write -> CellMark.DONE
                i < write -> CellMark.WINDOW
                else -> CellMark.IDLE
            },
        )
    }

    frames += WalkFrame(
        status = "Remove duplicates from a sorted array in place. Here the two pointers move the same direction at " +
            "different rates: one reads, one writes.",
        cells = row(0),
        pointers = mapOf(0 to "both"),
    )

    for (read in 1 until a.size) {
        val duplicate = a[read] == a[write]
        if (!duplicate) {
            write++
            a[write] = a[read]
        }
        frames += WalkFrame(
            status = if (duplicate) {
                "a[$read] = ${a[read]} equals the last kept value — read moves on, write stays put."
            } else {
                "a[$read] = ${a[read]} is new — copy it to index $write."
            },
            cells = row(read),
            pointers = if (read == write) mapOf(read to "both") else mapOf(write to "write", read to "read"),
        )
    }

    frames += WalkFrame(
        status = "First ${write + 1} slots hold the distinct values; everything past them is stale. No extra array " +
            "was allocated.",
        cells = a.mapIndexed { i, v -> CellView(v.toString(), if (i <= write) CellMark.RESULT else CellMark.DIM) },
        readout = "length = ${write + 1}",
    )
    return frames
}

// ── Selection and randomised walks ───────────────────────────────────────────
// These four count something as they go — comparisons, pointer moves, selection frequencies — so
// the closing claim of each lab is a number the walk itself produced.

/** Deterministic LCG, so the sampling labs quote the same figures on every launch. */
private class WalkRng(private var state: Int) {
    fun next(): Double {
        state = state * 1103515245 + 12345
        return (((state ushr 16) and 0x7fff).toDouble()) / 32767.0
    }
}

private fun quickselectFrames(): List<WalkFrame> {
    val a = intArrayOf(7, 2, 9, 4, 1, 8, 3, 6).copyOf()
    val target = 3   // 0-indexed: the 4th smallest
    val sorted = a.sortedArray()
    val frames = mutableListOf<WalkFrame>()
    var comparisons = 0

    // `lo`..`hi` is the live range; everything outside it has been ruled out for good.
    fun row(lo: Int, hi: Int, pivotIndex: Int?, scan: Int?, boundary: Int?, done: Int? = null) =
        a.mapIndexed { i, v ->
            CellView(
                v.toString(),
                when {
                    i == done -> CellMark.RESULT
                    i < lo || i > hi -> CellMark.DIM
                    i == pivotIndex -> CellMark.RESULT
                    i == scan -> CellMark.ACTIVE
                    boundary != null && i < boundary -> CellMark.WINDOW
                    else -> CellMark.IDLE
                },
            )
        }

    frames += WalkFrame(
        status = "Find the ${target + 1}th smallest value without sorting. Sorting would order all ${a.size} " +
            "elements; quickselect only ever descends into the side that can still contain the answer.",
        cells = row(0, a.lastIndex, null, null, null),
        readout = "target: rank ${target + 1} of ${a.size}",
    )

    var lo = 0
    var hi = a.lastIndex
    var answer = -1
    while (lo <= hi) {
        val pivot = a[hi]
        frames += WalkFrame(
            status = "Partition a[$lo..$hi] around the pivot ${pivot}. Everything smaller is swapped to the front.",
            cells = row(lo, hi, hi, null, null),
            pointers = mapOf(hi to "pivot"),
        )
        var boundary = lo
        for (j in lo until hi) {
            comparisons++
            val smaller = a[j] < pivot
            if (smaller) {
                val t = a[boundary]; a[boundary] = a[j]; a[j] = t
                boundary++
            }
            frames += WalkFrame(
                status = if (smaller) {
                    "${a[boundary - 1]} < $pivot — swap it into the smaller-than-pivot block, which now holds " +
                        "${boundary - lo}."
                } else {
                    "${a[j]} ≥ $pivot — leave it where it is."
                },
                cells = row(lo, hi, hi, j, boundary),
                pointers = mapOf(j to "j", hi to "pivot"),
            )
        }
        val t = a[boundary]; a[boundary] = a[hi]; a[hi] = t

        when {
            boundary == target -> {
                answer = a[boundary]
                frames += WalkFrame(
                    status = "The pivot lands at index $boundary, which is exactly the rank we wanted. Its final " +
                        "position is its answer — no further work, and the two sides were never sorted.",
                    cells = row(lo, hi, null, null, null, done = boundary),
                    readout = "${target + 1}th smallest = $answer, found in $comparisons comparisons",
                )
                lo = boundary + 1
                hi = boundary   // ends the loop
            }
            boundary > target -> {
                frames += WalkFrame(
                    status = "The pivot settles at index $boundary, above the rank we want, so the answer lies to " +
                        "its left. Indices $boundary..$hi are discarded and never looked at again.",
                    cells = row(lo, boundary - 1, null, null, null),
                    readout = "${boundary - lo} of ${a.size} still in play",
                )
                hi = boundary - 1
            }
            else -> {
                frames += WalkFrame(
                    status = "The pivot settles at index $boundary, below the rank we want, so the answer lies to " +
                        "its right. Everything from $lo up to and including $boundary is discarded.",
                    cells = row(boundary + 1, hi, null, null, null),
                    readout = "${hi - boundary} of ${a.size} still in play",
                )
                lo = boundary + 1
            }
        }
    }

    // What the same array costs to sort, counted the same way.
    var sortComparisons = 0
    run {
        val b = intArrayOf(7, 2, 9, 4, 1, 8, 3, 6)
        for (i in 1 until b.size) {
            var j = i
            while (j > 0) {
                sortComparisons++
                if (b[j - 1] <= b[j]) break
                val t2 = b[j - 1]; b[j - 1] = b[j]; b[j] = t2
                j--
            }
        }
    }

    frames += WalkFrame(
        status = "Quickselect answered in $comparisons comparisons. Sorting the same array to read off index " +
            "$target takes $sortComparisons — and sorting computes the other ${a.size - 1} ranks nobody asked " +
            "for. Expected cost is linear because each round discards a constant fraction: n + n/2 + n/4 … ≈ 2n.",
        cells = sorted.mapIndexed { i, v -> CellView(v.toString(), if (i == target) CellMark.RESULT else CellMark.DIM) },
        readout = "$comparisons comparisons vs $sortComparisons to sort",
    )
    return frames
}

private fun medianOfMediansFrames(): List<WalkFrame> {
    val a = listOf(12, 3, 17, 8, 1, 20, 6, 14, 9, 2, 18, 11, 5, 15, 7)
    val groups = a.chunked(5)
    val medians = groups.map { it.sorted()[it.size / 2] }
    val pivot = medians.sorted()[medians.size / 2]
    val below = a.count { it < pivot }
    val above = a.count { it > pivot }
    val frames = mutableListOf<WalkFrame>()

    fun row(mark: (Int) -> CellMark) = a.mapIndexed { i, v -> CellView(v.toString(), mark(i)) }

    frames += WalkFrame(
        status = "Quickselect is linear *on average*, but a badly chosen pivot splits off one element at a time " +
            "and costs O(n²). Median of medians picks a pivot with a guaranteed split, making the worst case " +
            "linear too.",
        cells = row { CellMark.IDLE },
        readout = "${a.size} elements, in groups of 5",
    )
    frames += WalkFrame(
        status = "Split into ${groups.size} groups of 5. Each group is small and fixed-size, so sorting one is " +
            "constant work — ${groups.size} groups is O(n) in total.",
        cells = row { if ((it / 5) % 2 == 0) CellMark.WINDOW else CellMark.IDLE },
        aux = groups.flatMap { g -> g.sorted().map { CellView(it.toString(), CellMark.DIM) } },
        auxLabel = "each group, sorted",
    )
    frames += WalkFrame(
        status = "Take each group's median: ${medians.joinToString(", ")}. These ${medians.size} values are the " +
            "only ones that matter for choosing the pivot.",
        cells = row { CellMark.DIM },
        aux = groups.flatMap { g ->
            g.sorted().mapIndexed { i, v ->
                CellView(v.toString(), if (i == g.size / 2) CellMark.ACTIVE else CellMark.DIM)
            }
        },
        auxLabel = "group medians highlighted",
    )
    frames += WalkFrame(
        status = "The pivot is the median of those medians: $pivot. Finding it is a recursive quickselect on a " +
            "list one fifth the size, which is what keeps the recursion affordable.",
        cells = row { if (a[it] == pivot) CellMark.RESULT else CellMark.DIM },
        aux = medians.map { CellView(it.toString(), if (it == pivot) CellMark.RESULT else CellMark.WINDOW) },
        auxLabel = "the ${medians.size} medians",
        readout = "pivot = $pivot",
    )
    frames += WalkFrame(
        status = "Partitioning on $pivot puts $below elements below it and $above above — the smaller side is " +
            "${"%.0f".format(100.0 * minOf(below, above) / a.size)}% of the array, so that is what gets thrown " +
            "away this round. Half the groups have a median on each side of the pivot, and in each of those at " +
            "least three of five elements fall the same way, so at least 3n/10 is discarded no matter what the " +
            "input is. That floor is what turns the worst case linear.",
        cells = row {
            when {
                a[it] == pivot -> CellMark.RESULT
                a[it] < pivot -> CellMark.WINDOW
                else -> CellMark.IDLE
            }
        },
        readout = "$below below · $above above — guaranteed floor is ${3 * a.size / 10}",
    )
    frames += WalkFrame(
        status = "The catch is the constant. Grouping, sorting each group and recursing to find the pivot all cost " +
            "real time, so in practice a random pivot is faster and median of medians is reserved for when a " +
            "worst-case bound actually has to hold.",
        cells = row { if (a[it] == pivot) CellMark.RESULT else CellMark.DIM },
        readout = "guaranteed O(n) — at a constant factor you pay on every input",
    )
    return frames
}

private fun mosAlgorithmFrames(): List<WalkFrame> {
    val a = listOf(4, 1, 7, 2, 9, 3, 6, 5, 8, 2, 4, 1)
    val queries = listOf(0 to 4, 6 to 11, 1 to 3, 5 to 9, 2 to 7)
    val block = 3

    fun movesFor(order: List<Pair<Int, Int>>): Int {
        var l = 0
        var r = -1
        var moves = 0
        for ((ql, qr) in order) {
            moves += kotlin.math.abs(ql - l) + kotlin.math.abs(qr - r)
            l = ql
            r = qr
        }
        return moves
    }

    val sorted = queries.sortedWith(compareBy({ it.first / block }, { it.second }))
    val naiveMoves = movesFor(queries)
    val mosMoves = movesFor(sorted)
    val frames = mutableListOf<WalkFrame>()

    fun row(l: Int, r: Int, active: Int? = null) = a.mapIndexed { i, v ->
        CellView(
            v.toString(),
            when {
                i == active -> CellMark.ACTIVE
                i in l..r -> CellMark.WINDOW
                else -> CellMark.DIM
            },
        )
    }

    frames += WalkFrame(
        status = "Five range-sum queries over ${a.size} elements, and all of them are known up front. That is the " +
            "one assumption Mo's algorithm needs: the queries are offline, so we may answer them in whatever " +
            "order is cheapest.",
        cells = a.map { CellView(it.toString()) },
        readout = queries.joinToString("  ") { "[${it.first},${it.second}]" },
    )
    frames += WalkFrame(
        status = "Answering them in the order asked drags the two pointers back and forth across the array: " +
            "$naiveMoves pointer moves in total. Nothing is wrong with the answers — the cost is purely the " +
            "travel between consecutive ranges.",
        cells = row(queries[0].first, queries[0].second),
        readout = "$naiveMoves pointer moves in query order",
    )
    frames += WalkFrame(
        status = "Sort the queries instead by which block of ${block} their left end falls in, breaking ties by " +
            "right end. Within a block the right pointer only ever advances, and the left pointer stays inside a " +
            "window of ${block}.",
        cells = a.mapIndexed { i, v -> CellView(v.toString(), if ((i / block) % 2 == 0) CellMark.WINDOW else CellMark.IDLE) },
        readout = sorted.joinToString("  ") { "[${it.first},${it.second}]" },
    )

    var l = 0
    var r = -1
    var sum = 0
    var moves = 0
    for ((qi, q) in sorted.withIndex()) {
        val (ql, qr) = q
        while (r < qr) { r++; sum += a[r]; moves++ }
        while (l > ql) { l--; sum += a[l]; moves++ }
        while (r > qr) { sum -= a[r]; r--; moves++ }
        while (l < ql) { sum -= a[l]; l++; moves++ }
        frames += WalkFrame(
            status = "Query ${qi + 1} of ${sorted.size}: [$ql, $qr] in block ${ql / block}. The window is adjusted " +
                "one element at a time rather than rebuilt, so the running sum is reused.",
            cells = row(l, r),
            pointers = mapOf(l to "L", r to "R"),
            readout = "sum = $sum   ·   $moves moves so far",
        )
    }

    frames += WalkFrame(
        status = "All five answered in $moves pointer moves against $naiveMoves in the order they arrived. The " +
            "ordering is the whole algorithm: with a block size of about √n the total travel is O((n + q)√n), " +
            "which beats recomputing each range from scratch whenever the ranges are long.",
        cells = a.map { CellView(it.toString(), CellMark.DONE) },
        readout = "$naiveMoves → $moves pointer moves",
    )
    return frames
}

private fun reservoirSamplingFrames(): List<WalkFrame> {
    val stream = listOf(41, 17, 63, 8, 92, 25, 54, 39, 71, 6, 88, 30)
    val k = 3
    val rng = WalkRng(20240727)
    val reservoir = IntArray(k) { stream[it] }
    val frames = mutableListOf<WalkFrame>()

    fun streamRow(upTo: Int, active: Int?, kept: Set<Int>) = stream.mapIndexed { i, v ->
        CellView(
            v.toString(),
            when {
                i == active -> CellMark.ACTIVE
                i in kept -> CellMark.RESULT
                i <= upTo -> CellMark.DIM
                else -> CellMark.IDLE
            },
        )
    }

    var keptIndices = (0 until k).toMutableSet()

    frames += WalkFrame(
        status = "Pick $k items uniformly at random from a stream whose length is unknown until it ends, storing " +
            "only the $k. The first $k items go straight in — at that point they are the whole stream.",
        cells = streamRow(k - 1, null, keptIndices),
        aux = reservoir.map { CellView(it.toString(), CellMark.RESULT) },
        auxLabel = "reservoir",
        readout = "reservoir filled with the first $k items",
    )

    for (i in k until stream.size) {
        val n = i + 1
        val roll = rng.next()
        val threshold = k.toDouble() / n
        val accepted = roll < threshold
        var replacedSlot = -1
        if (accepted) {
            replacedSlot = (rng.next() * k).toInt().coerceIn(0, k - 1)
            val evicted = reservoir[replacedSlot]
            keptIndices = keptIndices.filterNot { stream[it] == evicted }.toMutableSet()
            keptIndices += i
            reservoir[replacedSlot] = stream[i]
        }
        frames += WalkFrame(
            status = if (accepted) {
                "Item ${i + 1} is ${stream[i]}. It is accepted with probability $k/${n} = " +
                    "${"%.2f".format(threshold)}; the draw was ${"%.2f".format(roll)}, so it enters and evicts " +
                    "slot $replacedSlot."
            } else {
                "Item ${i + 1} is ${stream[i]}. Accepted with probability $k/${n} = ${"%.2f".format(threshold)}, " +
                    "but the draw was ${"%.2f".format(roll)} — it is discarded and never stored."
            },
            cells = streamRow(i, i, keptIndices),
            aux = reservoir.mapIndexed { s, v ->
                CellView(v.toString(), if (s == replacedSlot) CellMark.ACTIVE else CellMark.RESULT)
            },
            auxLabel = "reservoir",
            readout = "accept probability $k/$n = ${"%.2f".format(threshold)}",
        )
    }

    // The claim is uniformity, so measure it rather than assert it.
    val trials = 20000
    val hits = IntArray(stream.size)
    val trialRng = WalkRng(987654321)
    repeat(trials) {
        val res = IntArray(k) { it }
        for (i in k until stream.size) {
            if (trialRng.next() < k.toDouble() / (i + 1)) {
                res[(trialRng.next() * k).toInt().coerceIn(0, k - 1)] = i
            }
        }
        for (idx in res) hits[idx]++
    }
    val rates = hits.map { it.toDouble() / trials }
    val expected = k.toDouble() / stream.size

    frames += WalkFrame(
        status = "The stream is done and the reservoir holds ${reservoir.joinToString(", ")} — using memory for $k " +
            "items, never ${stream.size}. The claim that matters is that every item had an equal chance of " +
            "ending up there, so run it $trials times and count.",
        cells = streamRow(stream.lastIndex, null, keptIndices),
        aux = reservoir.map { CellView(it.toString(), CellMark.RESULT) },
        auxLabel = "final reservoir",
        readout = "selection rate ranged ${"%.3f".format(rates.min())}–${"%.3f".format(rates.max())} " +
            "against the expected ${"%.3f".format(expected)}",
    )
    frames += WalkFrame(
        status = "Every position lands within ${"%.3f".format(rates.maxOf { kotlin.math.abs(it - expected) })} of " +
            "$k/${stream.size} = ${"%.3f".format(expected)}, including the very first item — which survives only " +
            "by never being evicted — and the last, which walks in with probability $k/${stream.size}. The " +
            "shrinking accept probability is exactly what keeps those two equal.",
        cells = stream.mapIndexed { i, _ ->
            CellView("%.2f".format(rates[i]), if (kotlin.math.abs(rates[i] - expected) < 0.01) CellMark.DONE else CellMark.ACTIVE)
        },
        auxLabel = "measured selection rate per position",
        readout = "expected ${"%.3f".format(expected)} everywhere",
    )
    return frames
}

// A Fenwick tree is an array whose index arithmetic is the whole data structure, so the walk player
// is the honest renderer for it: the cells are tree[], and the algorithm is which cells you step to.
private fun lowbit(i: Int): Int = i and (-i)

private fun fenwickFrames(): List<WalkFrame> {
    val a = intArrayOf(3, 1, 4, 1, 5, 9, 2, 6)
    val n = a.size
    val tree = IntArray(n + 1)

    fun updatePath(pos: Int): List<Int> {
        val path = mutableListOf<Int>()
        var i = pos
        while (i <= n) { path += i; i += lowbit(i) }
        return path
    }

    fun queryPath(pos: Int): List<Int> {
        val path = mutableListOf<Int>()
        var i = pos
        while (i > 0) { path += i; i -= lowbit(i) }
        return path
    }

    a.forEachIndexed { idx, v -> updatePath(idx + 1).forEach { tree[it] += v } }

    val frames = mutableListOf<WalkFrame>()
    fun treeRow(active: Set<Int> = emptySet(), done: Set<Int> = emptySet()) =
        (1..n).map { i ->
            CellView(
                tree[i].toString(),
                when {
                    i in active -> CellMark.ACTIVE
                    i in done -> CellMark.DONE
                    else -> CellMark.IDLE
                },
            )
        }

    fun sourceRow(range: IntRange? = null) = a.mapIndexed { idx, v ->
        CellView(v.toString(), if (range != null && idx + 1 in range) CellMark.WINDOW else CellMark.DIM)
    }

    frames += WalkFrame(
        status = "tree[i] holds the sum of a[i − lowbit(i) + 1 .. i], where lowbit(i) is the lowest set bit of i. " +
            "So tree[4] covers four cells (${a.take(4).joinToString("+")} = ${tree[4]}) and tree[8] covers all " +
            "eight, while every odd index covers exactly one.",
        cells = treeRow(active = setOf(4, 8)),
        aux = sourceRow(1..8),
        auxLabel = "a (the array being summed)",
    )

    val qPath = queryPath(7)
    var running = 0
    qPath.forEachIndexed { step, i ->
        running += tree[i]
        frames += WalkFrame(
            status = "prefix(7): read tree[$i] = ${tree[i]}, which covers a[${i - lowbit(i) + 1}..$i]. " +
                "Strip the lowest set bit: $i − ${lowbit(i)} = ${i - lowbit(i)}" +
                if (i - lowbit(i) == 0) ", which ends the walk." else ", so that is the next index.",
            cells = treeRow(active = setOf(i), done = qPath.take(step).toSet()),
            pointers = mapOf(i - 1 to "i"),
            aux = sourceRow((i - lowbit(i) + 1)..i),
            auxLabel = "range this node covers",
            readout = "running sum = $running",
        )
    }
    frames += WalkFrame(
        status = "prefix(7) = $running in ${qPath.size} reads — the indices ${qPath.joinToString(" → ")}, which are " +
            "the set bits of 7 written as 4 + 2 + 1. A plain loop over a[0..6] would have read 7 cells.",
        cells = treeRow(done = qPath.toSet()),
        aux = sourceRow(1..7),
        auxLabel = "a[1..7] — the same sum the loop would compute",
        readout = "prefix(7) = $running",
    )

    val pos = 3
    val delta = 5
    val uPath = updatePath(pos)
    uPath.forEachIndexed { step, i ->
        tree[i] += delta
        frames += WalkFrame(
            status = "a[$pos] += $delta. tree[$i] covers a[$pos], so it moves to ${tree[i]}. Add the lowest set bit: " +
                "$i + ${lowbit(i)} = ${i + lowbit(i)}" +
                if (i + lowbit(i) > n) ", which is past the end — done." else ", the next node that also covers a[$pos].",
            cells = treeRow(active = setOf(i), done = uPath.take(step).toSet()),
            pointers = mapOf(i - 1 to "i"),
            readout = "${step + 1} of ${uPath.size} nodes rewritten",
        )
    }
    frames += WalkFrame(
        status = "One element changed and exactly ${uPath.size} nodes moved (${uPath.joinToString(" → ")}). Every " +
            "other node's range excludes a[$pos], so leaving them alone is correct, not lazy.",
        cells = treeRow(done = uPath.toSet()),
        readout = "update cost = ${uPath.size} writes",
    )

    var fenQuery = 0
    var loopQuery = 0
    var fenUpdate = 0
    var prefixUpdate = 0
    for (i in 1..n) {
        fenQuery += queryPath(i).size
        loopQuery += i
        fenUpdate += updatePath(i).size
        prefixUpdate += n - i + 1
    }
    frames += WalkFrame(
        status = "Summed over all $n prefixes and all $n single-element updates: Fenwick pays $fenQuery reads and " +
            "$fenUpdate writes. A raw array pays $loopQuery reads but only 1 write; a precomputed prefix array " +
            "pays $n reads but $prefixUpdate writes. Fenwick is the structure that refuses to be terrible at " +
            "either one.",
        cells = treeRow(),
        aux = (1..n).map { CellView(Integer.bitCount(it).toString(), CellMark.WINDOW) },
        auxLabel = "reads per prefix(i) — the popcount of i, never above log₂ $n = 3",
        readout = "queries $fenQuery vs $loopQuery · updates $fenUpdate vs $prefixUpdate",
    )
    return frames
}

// The string lab is about the array underneath: contiguous storage buys O(1) indexing, and
// immutability turns "s = s + x" in a loop into a quadratic copy.
private fun stringFrames(): List<WalkFrame> {
    val s = "algora"
    val other = "algebra"
    val frames = mutableListOf<WalkFrame>()

    fun charRow(text: String, active: Int? = null, done: IntRange? = null, dimFrom: Int = text.length) =
        text.mapIndexed { i, c ->
            CellView(
                c.toString(),
                when {
                    i == active -> CellMark.ACTIVE
                    done != null && i in done -> CellMark.DONE
                    i >= dimFrom -> CellMark.DIM
                    else -> CellMark.IDLE
                },
            )
        }

    frames += WalkFrame(
        status = "A string is a contiguous block of code units. s[3] is one multiply-and-add on the base address — " +
            "the same cost as a[3] on any array, and the reason indexing never appears in a complexity analysis.",
        cells = charRow(s, active = 3),
        pointers = mapOf(3 to "s[3]"),
        readout = "s[3] = '${s[3]}'",
    )

    val mismatch = s.zip(other).indexOfFirst { (x, y) -> x != y }
    frames += WalkFrame(
        status = "Comparing \"$s\" with \"$other\" stops at index $mismatch ('${s[mismatch]}' vs " +
            "'${other[mismatch]}'), after ${mismatch + 1} character comparisons rather than ${s.length}. " +
            "Equality is worst-case O(n) but usually leaves early; sorting strings is why that worst case matters.",
        // Both rows are padded to the longer word. Without this the 6-cell row sits above a 7-cell
        // row at a different width, so the character-by-character comparison the frame is *about*
        // does not line up on screen — found by the frame guard, which D6 taught to check the arity.
        cells = charRow(s, active = mismatch, done = 0 until mismatch) +
            List(other.length - s.length) { CellView("·", CellMark.DIM) },
        aux = charRow(other, active = mismatch, done = 0 until mismatch),
        auxLabel = "the string being compared against",
        readout = "${mismatch + 1} comparisons, verdict: \"$s\" > \"$other\"",
    )

    var copies = 0
    var built = ""
    s.forEach { c ->
        copies += built.length + 1
        built += c
        val existing = built.length - 1
        frames += WalkFrame(
            status = "s = s + '$c'. The result is a new string, so the " +
                (if (existing == 1) "1 character" else "$existing characters") +
                " already there must be copied into fresh storage before '$c' is written. " +
                "Total copied so far: $copies.",
            cells = charRow(built + "·".repeat(s.length - built.length), active = built.length - 1, dimFrom = built.length),
            readout = "characters copied: $copies",
        )
    }

    fun builderWrites(count: Int, startCapacity: Int): Int {
        var capacity = startCapacity
        var size = 0
        var growthCopies = 0
        repeat(count) {
            if (size == capacity) { growthCopies += size; capacity *= 2 }
            size++
        }
        return size + growthCopies
    }

    val builderSmall = builderWrites(s.length, 4)
    frames += WalkFrame(
        status = "Six characters cost $copies character writes that way. A mutable builder writes each character " +
            "once into a buffer it owns and only copies when the buffer fills — $builderSmall writes for the same " +
            "result. At this size the difference is a rounding error, which is exactly why the bug survives review.",
        cells = charRow(built, done = built.indices),
        readout = "concat $copies writes · builder $builderSmall writes",
    )

    val big = 1000
    val naiveBig = big.toLong() * (big + 1) / 2
    val builderBig = builderWrites(big, 16)
    frames += WalkFrame(
        status = "Run the same loop to length $big and the gap is the whole point: concatenation writes " +
            "$naiveBig characters because step i copies i of them — 1 + 2 + … + $big. The builder writes " +
            "$builderBig, since doubling makes the copies a geometric series that sums to under 2n. " +
            "Quadratic against linear, from one operator.",
        cells = charRow(built, done = built.indices),
        readout = "n = $big → $naiveBig vs $builderBig writes (${naiveBig / builderBig}×)",
    )
    frames += WalkFrame(
        status = "Immutability is not the mistake — it is what makes strings safe to share, hash once, and use as " +
            "map keys. Building them in a loop with + is the mistake. Use a builder while assembling, freeze to a " +
            "string when done.",
        cells = charRow(s, done = s.indices),
    )
    return frames
}

// The list ADT is a contract, so the lab runs one script of operations against two implementations and
// counts what each actually pays.
private fun listAdtFrames(): List<WalkFrame> {
    val start = listOf(10, 20, 30, 40, 50)
    val array = start.toMutableList()
    val linked = start.toMutableList()
    var shifts = 0
    var indexReads = 0
    var hops = 0
    var pointerWrites = 0
    val frames = mutableListOf<WalkFrame>()

    fun row(list: List<Int>, marked: Set<Int>, mark: CellMark) =
        list.mapIndexed { i, v -> CellView(v.toString(), if (i in marked) mark else CellMark.IDLE) }

    frames += WalkFrame(
        status = "The contract is the same for both rows: ordered, indexed, duplicates allowed, get / insert / " +
            "remove at any position. Nothing below changes what the list means — only what each call costs.",
        cells = row(array, emptySet(), CellMark.IDLE),
        aux = row(linked, emptySet(), CellMark.IDLE),
        auxLabel = "linked implementation",
    )

    val getIndex = 3
    indexReads++
    hops += getIndex + 1
    frames += WalkFrame(
        status = "get($getIndex): the array computes base + $getIndex × width and reads once. The linked list has " +
            "no address arithmetic to do — it follows ${getIndex + 1} next pointers to reach the same value.",
        cells = row(array, setOf(getIndex), CellMark.ACTIVE),
        pointers = mapOf(getIndex to "read"),
        aux = row(linked, (0..getIndex).toSet(), CellMark.WINDOW),
        auxLabel = "linked implementation — ${getIndex + 1} hops",
        readout = "array 1 read · linked ${getIndex + 1} hops",
    )

    val shiftCount = array.size
    shifts += shiftCount
    array.add(0, 5)
    linked.add(0, 5)
    pointerWrites += 2
    frames += WalkFrame(
        status = "insert(0, 5): the array must move every one of $shiftCount elements up a slot before the new " +
            "head has anywhere to live. The linked list allocates a node and writes 2 pointers — position 0 " +
            "costs it nothing.",
        cells = row(array, setOf(0), CellMark.RESULT),
        aux = row(linked, setOf(0), CellMark.RESULT),
        auxLabel = "linked implementation — 2 pointer writes",
        readout = "array $shiftCount shifts · linked 2 pointer writes",
    )

    array.add(60)
    linked.add(60)
    pointerWrites += 2
    frames += WalkFrame(
        status = "append(60): both are cheap. The array writes into spare capacity (and occasionally pays a " +
            "doubling copy, amortised to O(1)); the linked list splices at a tail pointer it already keeps.",
        cells = row(array, setOf(array.lastIndex), CellMark.RESULT),
        aux = row(linked, setOf(linked.lastIndex), CellMark.RESULT),
        auxLabel = "linked implementation",
        readout = "array ~1 write · linked 2 pointer writes",
    )

    val removeIndex = 2
    array.removeAt(removeIndex)
    val removeShifts = array.size - removeIndex
    shifts += removeShifts
    hops += removeIndex
    pointerWrites += 1
    linked.removeAt(removeIndex)
    frames += WalkFrame(
        status = "remove($removeIndex): the array closes the hole by shifting $removeShifts elements down. The " +
            "linked list hops $removeIndex nodes to find the predecessor, then unlinks with a single pointer " +
            "write — the traversal, not the removal, is what it pays for.",
        cells = row(array, setOf(removeIndex), CellMark.DONE),
        aux = row(linked, (0 until removeIndex).toSet(), CellMark.WINDOW),
        auxLabel = "linked implementation — $removeIndex hops + 1 write",
        readout = "array $removeShifts shifts · linked $removeIndex hops",
    )

    val lastGet = 4
    indexReads++
    hops += lastGet + 1
    frames += WalkFrame(
        status = "One more get($lastGet) to close the script. Both lists hold ${array.joinToString(", ")} — " +
            "identical contents, identical answers to every query the interface exposes.",
        cells = row(array, setOf(lastGet), CellMark.ACTIVE),
        aux = row(linked, (0..lastGet).toSet(), CellMark.WINDOW),
        auxLabel = "linked implementation — ${lastGet + 1} hops",
        readout = "array 1 read · linked ${lastGet + 1} hops",
    )

    frames += WalkFrame(
        status = "Whole script: the array moved $shifts elements and did $indexReads O(1) index reads; the linked " +
            "list walked $hops nodes and wrote $pointerWrites pointers. Pick by the mix you actually run — " +
            "index-heavy work wants the array, splice-heavy work with a node already in hand wants the links. " +
            "\"Linked lists are faster at inserts\" is only true once you are standing at the insertion point.",
        cells = row(array, array.indices.toSet(), CellMark.DONE),
        aux = row(linked, linked.indices.toSet(), CellMark.DONE),
        auxLabel = "linked implementation — same contents",
        readout = "array: $shifts shifts · linked: $hops hops, $pointerWrites pointer writes",
    )
    return frames
}

private class Job(val id: String, val deadline: Int, val profit: Int)

private class Schedule(val profit: Int, val slots: Array<String?>, val log: List<Pair<Job, Int?>>)

private fun runSchedule(jobs: List<Job>, slotCount: Int): Schedule {
    val slots = arrayOfNulls<String>(slotCount + 1)
    var profit = 0
    val log = mutableListOf<Pair<Job, Int?>>()
    jobs.forEach { job ->
        var placed: Int? = null
        for (s in minOf(job.deadline, slotCount) downTo 1) {
            if (slots[s] == null) { slots[s] = job.id; profit += job.profit; placed = s; break }
        }
        log += job to placed
    }
    return Schedule(profit, slots, log)
}

private fun jobSequencingFrames(): List<WalkFrame> {
    val jobs = listOf(
        Job("J1", 2, 100),
        Job("J2", 1, 19),
        Job("J3", 2, 27),
        Job("J4", 1, 25),
        Job("J5", 3, 15),
    )
    val slotCount = jobs.maxOf { it.deadline }
    val byProfit = jobs.sortedByDescending { it.profit }
    val frames = mutableListOf<WalkFrame>()

    fun jobRow(current: Int, taken: Set<String>, rejected: Set<String>) =
        byProfit.mapIndexed { i, job ->
            CellView(
                job.profit.toString(),
                when {
                    i == current -> CellMark.ACTIVE
                    job.id in taken -> CellMark.DONE
                    job.id in rejected -> CellMark.DIM
                    else -> CellMark.IDLE
                },
            )
        }

    fun slotRow(slots: Array<String?>, active: Int? = null) =
        (1..slotCount).map { s ->
            CellView(slots[s] ?: "·", if (s == active) CellMark.ACTIVE else if (slots[s] != null) CellMark.RESULT else CellMark.DIM)
        }

    frames += WalkFrame(
        status = "Five jobs, each with a deadline and a profit; one unit of time per job and $slotCount slots. " +
            "Sorted by profit, highest first — the greedy claim is that considering them in this order is enough.",
        cells = jobRow(-1, emptySet(), emptySet()),
        pointers = byProfit.indices.associateWith { "d${byProfit[it].deadline}" },
        aux = slotRow(arrayOfNulls(slotCount + 1)),
        auxLabel = "slots 1..$slotCount",
    )

    val slots = arrayOfNulls<String>(slotCount + 1)
    val taken = mutableSetOf<String>()
    val rejected = mutableSetOf<String>()
    var profit = 0
    byProfit.forEachIndexed { i, job ->
        var placed: Int? = null
        for (s in minOf(job.deadline, slotCount) downTo 1) {
            if (slots[s] == null) { slots[s] = job.id; profit += job.profit; placed = s; break }
        }
        if (placed != null) taken += job.id else rejected += job.id
        frames += WalkFrame(
            status = if (placed != null) {
                "${job.id} (profit ${job.profit}, deadline ${job.deadline}) goes in slot $placed — the latest free " +
                    "slot at or before its deadline. Taking the latest one keeps the early slots open for jobs " +
                    "that have no other option."
            } else {
                "${job.id} (profit ${job.profit}, deadline ${job.deadline}) is dropped: every slot up to " +
                    "${job.deadline} is already held by a job worth more. Nothing later can rescue it, so the " +
                    "decision is final."
            },
            cells = jobRow(i, taken, rejected),
            pointers = mapOf(i to "d${job.deadline}"),
            aux = slotRow(slots, placed),
            auxLabel = "slots 1..$slotCount",
            readout = "profit = $profit",
        )
    }

    val deadlineFirst = runSchedule(jobs.sortedBy { it.deadline }, slotCount)
    frames += WalkFrame(
        status = "Greedy by profit finishes at $profit, running ${taken.sorted().joinToString(", ")}. The ordering " +
            "is doing real work here — the same algorithm fed jobs sorted by deadline instead scores " +
            "${deadlineFirst.profit}, because it spends slot 1 on ${deadlineFirst.slots[1]} before it has seen " +
            "what else wants that slot.",
        cells = jobRow(-1, taken, rejected),
        aux = slotRow(deadlineFirst.slots),
        auxLabel = "deadline-first schedule — profit ${deadlineFirst.profit}",
        readout = "profit-first $profit vs deadline-first ${deadlineFirst.profit}",
    )

    var best = 0
    val ids = jobs.indices.toList()
    fun permute(chosen: List<Job>, remaining: List<Int>) {
        val result = runSchedule(chosen, slotCount)
        if (result.log.all { it.second != null }) best = maxOf(best, result.profit)
        remaining.forEach { idx -> permute(chosen + jobs[idx], remaining - idx) }
    }
    permute(emptyList(), ids)
    frames += WalkFrame(
        status = "Checked against brute force — every subset in every order, keeping only the ones where each job " +
            "lands in a slot: the optimum is $best. Greedy matched it. That is the exchange argument in action, " +
            "and it holds for every instance, not just this one.",
        cells = jobRow(-1, taken, rejected),
        aux = slotRow(slots),
        auxLabel = "greedy schedule — profit $profit",
        readout = "greedy $profit · brute-force optimum $best",
    )
    return frames
}

private fun fractionalKnapsackFrames(): List<WalkFrame> {
    val values = listOf(60, 100, 120)
    val weights = listOf(10, 20, 30)
    val capacity = 50
    val order = values.indices.sortedByDescending { values[it].toDouble() / weights[it] }
    val frames = mutableListOf<WalkFrame>()

    fun itemRow(current: Int, taken: Set<Int>, partial: Int? = null) =
        order.mapIndexed { pos, idx ->
            CellView(
                "${values[idx]}/${weights[idx]}",
                when {
                    pos == current -> CellMark.ACTIVE
                    idx == partial -> CellMark.RESULT
                    idx in taken -> CellMark.DONE
                    else -> CellMark.IDLE
                },
            )
        }

    val ratioPointers = order.indices.associateWith { "%.0f".format(values[order[it]].toDouble() / weights[order[it]]) }

    frames += WalkFrame(
        status = "Three items and a sack that holds $capacity kg. Sorted by value per kg — " +
            order.joinToString(", ") { "%.0f".format(values[it].toDouble() / weights[it]) } +
            " — because when items divide, density is the only thing worth ranking on.",
        cells = itemRow(-1, emptySet()),
        pointers = ratioPointers,
        readout = "capacity $capacity kg",
    )

    var left = capacity.toDouble()
    var total = 0.0
    val taken = mutableSetOf<Int>()
    order.forEachIndexed { pos, idx ->
        val take = minOf(weights[idx].toDouble(), left)
        val gained = values[idx] * take / weights[idx]
        total += gained
        left -= take
        val whole = take == weights[idx].toDouble()
        if (whole) taken += idx
        frames += WalkFrame(
            status = if (whole) {
                "Item ${idx + 1} is worth ${values[idx]} at ${weights[idx]} kg — take all of it. " +
                    "Remaining capacity ${"%.0f".format(left)} kg."
            } else {
                "Only ${"%.0f".format(left + take)} kg of room is left and item ${idx + 1} weighs " +
                    "${weights[idx]} kg, so take the fraction ${"%.0f".format(take)}/${weights[idx]} of it for " +
                    "${"%.0f".format(gained)}. This step is the one 0/1 knapsack is not allowed to make."
            },
            cells = itemRow(pos, taken, partial = if (whole) null else idx),
            pointers = ratioPointers,
            readout = "value ${"%.0f".format(total)} · ${"%.0f".format(left)} kg left",
        )
    }

    frames += WalkFrame(
        status = "Total ${"%.0f".format(total)} with the sack exactly full. No exchange can improve it: swapping " +
            "any kilogram for one from a lower-density item strictly loses value, and the sack is never left " +
            "with unused room. That is the proof, not a spot check.",
        cells = itemRow(-1, taken, partial = order.last()),
        readout = "optimal value ${"%.0f".format(total)}",
    )

    var greedy01 = 0
    var room = capacity
    val taken01 = mutableSetOf<Int>()
    order.forEach { idx ->
        if (weights[idx] <= room) { greedy01 += values[idx]; room -= weights[idx]; taken01 += idx }
    }
    val dp = IntArray(capacity + 1)
    values.indices.forEach { i ->
        for (c in capacity downTo weights[i]) dp[c] = maxOf(dp[c], dp[c - weights[i]] + values[i])
    }
    frames += WalkFrame(
        status = "Forbid the fraction and the same ordering breaks. Density-greedy takes items " +
            taken01.sorted().joinToString(", ") { "${it + 1}" } +
            " for $greedy01 and leaves $room kg unusable; the DP optimum is ${dp[capacity]}, from items 2 and 3. " +
            "Greedy is not \"usually close\" here — it is wrong by ${dp[capacity] - greedy01}.",
        cells = order.mapIndexed { pos, idx ->
            CellView("${values[idx]}/${weights[idx]}", if (idx in taken01) CellMark.DONE else CellMark.DIM)
        },
        pointers = ratioPointers,
        aux = listOf(
            CellView(greedy01.toString(), CellMark.ACTIVE),
            CellView(dp[capacity].toString(), CellMark.RESULT),
        ),
        auxLabel = "0/1 greedy vs 0/1 optimum",
        readout = "divisible → greedy optimal · indivisible → greedy off by ${dp[capacity] - greedy01}",
    )
    return frames
}

// ── String matching ──────────────────────────────────────────────────────────

private fun kmpFrames(): List<WalkFrame> {
    val text = "ABABDABABC"
    val pattern = "ABABC"
    val frames = mutableListOf<WalkFrame>()
    val m = pattern.length
    val lps = IntArray(m)

    fun lpsRow(filledUpTo: Int, active: Int? = null) = lps.mapIndexed { i, v ->
        when {
            i == active -> CellView(v.toString(), CellMark.ACTIVE)
            i <= filledUpTo -> CellView(v.toString(), CellMark.WINDOW)
            else -> CellView("·", CellMark.DIM)
        }
    }

    fun patternRow(matched: Int, active: Int? = null) = pattern.mapIndexed { i, c ->
        CellView(
            c.toString(),
            when {
                i == active -> CellMark.ACTIVE
                i < matched -> CellMark.WINDOW
                else -> CellMark.IDLE
            },
        )
    }

    frames += WalkFrame(
        status = "Phase 1 builds the LPS table over the pattern alone: for each position, how long is the longest " +
            "prefix that is also a suffix ending there.",
        cells = pattern.map { CellView(it.toString()) },
        aux = lpsRow(-1),
        auxLabel = "LPS",
    )

    var len = 0
    var k = 1
    while (k < m) {
        if (pattern[k] == pattern[len]) {
            len++
            lps[k] = len
            frames += WalkFrame(
                status = "P[$k] = '${pattern[k]}' matches P[${len - 1}] = '${pattern[len - 1]}', so the shared " +
                    "prefix grows to $len. lps[$k] = $len.",
                cells = patternRow(matched = len, active = k),
                pointers = mapOf(k to "k", (len - 1) to "len"),
                aux = lpsRow(k - 1, active = k),
                auxLabel = "LPS",
            )
            k++
        } else if (len > 0) {
            frames += WalkFrame(
                status = "P[$k] = '${pattern[k]}' breaks the run. Fall back to lps[${len - 1}] = ${lps[len - 1]} " +
                    "instead of restarting — the shorter prefix may still extend.",
                cells = patternRow(matched = len, active = k),
                pointers = mapOf(k to "k"),
                aux = lpsRow(k - 1),
                auxLabel = "LPS",
            )
            len = lps[len - 1]
        } else {
            lps[k] = 0
            frames += WalkFrame(
                status = "No prefix of the pattern ends at P[$k], so lps[$k] = 0.",
                cells = patternRow(matched = 0, active = k),
                pointers = mapOf(k to "k"),
                aux = lpsRow(k, active = k),
                auxLabel = "LPS",
            )
            k++
        }
    }

    frames += WalkFrame(
        status = "Table complete: [${lps.joinToString(", ")}]. Phase 2 now scans the text — and i will never " +
            "move backward.",
        cells = text.map { CellView(it.toString()) },
        aux = lpsRow(m - 1),
        auxLabel = "LPS",
    )

    var j = 0
    for (i in text.indices) {
        while (j > 0 && text[i] != pattern[j]) {
            val fallback = lps[j - 1]
            frames += WalkFrame(
                status = "Mismatch: T[$i] = '${text[i]}' ≠ P[$j] = '${pattern[j]}'. The first $fallback " +
                    "characters are already matched, so j drops to $fallback and i stays put.",
                cells = text.mapIndexed { p, c ->
                    CellView(c.toString(), if (p == i) CellMark.ACTIVE else if (p in (i - j) until i) CellMark.WINDOW else CellMark.IDLE)
                },
                pointers = mapOf(i to "i"),
                aux = patternRow(matched = fallback, active = fallback),
                auxLabel = "pattern (j = $fallback after fallback)",
                readout = "text pointer stays at $i",
            )
            j = fallback
        }
        if (text[i] == pattern[j]) j++
        frames += WalkFrame(
            status = if (j == 0) {
                "T[$i] = '${text[i]}' does not start the pattern. Move on."
            } else {
                "T[$i] = '${text[i]}' matches P[${j - 1}] — $j character${if (j == 1) "" else "s"} of the pattern now aligned."
            },
            cells = text.mapIndexed { p, c ->
                CellView(c.toString(), if (p == i) CellMark.ACTIVE else if (p > i - j && p < i) CellMark.WINDOW else CellMark.IDLE)
            },
            pointers = mapOf(i to "i"),
            aux = patternRow(matched = j, active = if (j < m) j else null),
            auxLabel = "pattern (j = $j)",
        )
        if (j == m) {
            val start = i - m + 1
            frames += WalkFrame(
                status = "Full match at index $start. j falls back to lps[${m - 1}] = ${lps[m - 1]} so overlapping " +
                    "occurrences are still found.",
                cells = text.mapIndexed { p, c ->
                    CellView(c.toString(), if (p in start..i) CellMark.RESULT else CellMark.DIM)
                },
                pointers = mapOf(start to "hit"),
                aux = patternRow(matched = m),
                auxLabel = "pattern matched",
                readout = "match at $start · text scanned once",
            )
            j = lps[j - 1]
        }
    }

    return frames
}

private fun rabinKarpFrames(): List<WalkFrame> {
    val text = "31415926"
    val pattern = "415"
    val base = 10L
    val mod = 13L
    val m = pattern.length
    val frames = mutableListOf<WalkFrame>()

    var high = 1L
    repeat(m - 1) { high = high * base % mod }

    var patternHash = 0L
    for (c in pattern) patternHash = (patternHash * base + (c - '0')) % mod

    frames += WalkFrame(
        status = "Hash the pattern once: \"$pattern\" as a base-$base number mod $mod is $patternHash. Every text " +
            "window will be compared against this single number.",
        cells = text.map { CellView(it.toString(), CellMark.IDLE) },
        aux = pattern.map { CellView(it.toString(), CellMark.RESULT) },
        auxLabel = "pattern hash = $patternHash",
    )

    var windowHash = 0L
    for (i in 0 until m) windowHash = (windowHash * base + (text[i] - '0')) % mod

    for (i in 0..text.length - m) {
        val window = text.substring(i, i + m)
        val collision = windowHash == patternHash && window != pattern
        val hit = window == pattern
        frames += WalkFrame(
            status = when {
                hit -> "Window \"$window\" hashes to $windowHash — equal to the pattern's. Verified character by " +
                    "character: a real match at index $i."
                collision -> "Window \"$window\" also hashes to $windowHash. Equal hashes are not equal strings, " +
                    "so the check rejects it — this is why verification is mandatory."
                else -> "Window \"$window\" hashes to $windowHash ≠ $patternHash. Skip it without comparing a " +
                    "single character."
            },
            cells = text.mapIndexed { p, c ->
                CellView(
                    c.toString(),
                    when {
                        hit && p in i until i + m -> CellMark.RESULT
                        p in i until i + m -> CellMark.WINDOW
                        else -> CellMark.DIM
                    },
                )
            },
            pointers = mapOf(i to "i"),
            readout = "hash $windowHash vs $patternHash" + if (hit) " · match" else "",
        )

        if (i < text.length - m) {
            val outgoing = text[i] - '0'
            val incoming = text[i + m] - '0'
            windowHash = (windowHash - outgoing * high % mod + mod) % mod
            windowHash = (windowHash * base + incoming) % mod
            frames += WalkFrame(
                status = "Roll: drop '$outgoing' from the front, shift, add '$incoming' at the back. One " +
                    "subtraction and one multiply — the window's hash never gets recomputed from scratch.",
                cells = text.mapIndexed { p, c ->
                    CellView(
                        c.toString(),
                        when (p) {
                            i -> CellMark.ACTIVE
                            i + m -> CellMark.ACTIVE
                            in i + 1 until i + m -> CellMark.WINDOW
                            else -> CellMark.DIM
                        },
                    )
                },
                pointers = mapOf(i to "out", (i + m) to "in"),
                readout = "new hash $windowHash",
            )
        }
    }

    return frames
}

private fun manacherFrames(): List<WalkFrame> {
    val s = "abacaba"
    val t = buildString {
        append('#')
        for (ch in s) { append(ch); append('#') }
    }
    val p = IntArray(t.length)
    val frames = mutableListOf<WalkFrame>()

    fun radiusRow(upTo: Int, active: Int? = null) = p.mapIndexed { i, v ->
        when {
            i == active -> CellView(v.toString(), CellMark.ACTIVE)
            i <= upTo -> CellView(v.toString(), CellMark.WINDOW)
            else -> CellView("·", CellMark.DIM)
        }
    }

    frames += WalkFrame(
        status = "\"$s\" becomes \"$t\". With separators every palindrome has odd length, so one loop handles " +
            "both the even and odd cases.",
        cells = t.map { CellView(it.toString()) },
        aux = radiusRow(-1),
        auxLabel = "p (radius)",
    )

    var c = 0
    var r = 0
    for (i in t.indices) {
        val mirror = 2 * c - i
        val seeded = i < r
        if (seeded) p[i] = minOf(r - i, p[mirror])
        val seed = p[i]

        while (i - p[i] - 1 >= 0 && i + p[i] + 1 < t.length && t[i - p[i] - 1] == t[i + p[i] + 1]) p[i]++

        val grown = p[i] - seed
        frames += WalkFrame(
            status = when {
                seeded && grown == 0 ->
                    "i = $i sits inside the palindrome centred at $c, so its mirror at $mirror hands over " +
                        "radius $seed for free. Direct comparison adds nothing."
                seeded ->
                    "Mirror at $mirror seeds radius $seed; expanding past the right edge adds $grown more, " +
                        "so p[$i] = ${p[i]}."
                else ->
                    "i = $i is outside every known palindrome, so it expands from scratch to radius ${p[i]}."
            },
            cells = t.mapIndexed { pos, ch ->
                CellView(
                    ch.toString(),
                    when {
                        pos == i -> CellMark.ACTIVE
                        pos in (i - p[i])..(i + p[i]) -> CellMark.WINDOW
                        else -> CellMark.IDLE
                    },
                )
            },
            pointers = buildMap {
                put(i, "i")
                if (seeded) put(mirror, "mirror")
            },
            aux = radiusRow(i - 1, active = i),
            auxLabel = "p (radius)",
            readout = "right edge r = $r",
        )

        if (i + p[i] > r) {
            c = i
            r = i + p[i]
        }
    }

    val best = p.indices.maxBy { p[it] }
    val start = (best - p[best]) / 2
    frames += WalkFrame(
        status = "Largest radius is ${p[best]} at centre $best, which maps back to \"${s.substring(start, start + p[best])}\" " +
            "in the original string. The right edge only ever moved right, so the whole scan was linear.",
        cells = t.mapIndexed { pos, ch ->
            CellView(ch.toString(), if (pos in (best - p[best])..(best + p[best])) CellMark.RESULT else CellMark.DIM)
        },
        pointers = mapOf(best to "centre"),
        aux = radiusRow(t.length - 1, active = best),
        auxLabel = "p (radius)",
        readout = "longest palindrome: ${s.substring(start, start + p[best])}",
    )

    return frames
}

private fun sparseTableFrames(): List<WalkFrame> {
    val a = listOf(5, 2, 4, 7, 1, 3, 6, 8)
    val n = a.size
    val levels = 4                                   // log2(8) + 1
    val table = Array(levels) { IntArray(n) }
    val frames = mutableListOf<WalkFrame>()

    for (i in 0 until n) table[0][i] = a[i]

    frames += WalkFrame(
        status = "Level 0 is the array itself: every interval of length 1. Each level above combines two blocks " +
            "from the level below.",
        cells = a.map { CellView(it.toString()) },
        aux = a.map { CellView(it.toString(), CellMark.WINDOW) },
        auxLabel = "table[0] — blocks of length 1",
    )

    for (k in 1 until levels) {
        val len = 1 shl k
        val half = len shr 1
        for (i in 0..n - len) table[k][i] = minOf(table[k - 1][i], table[k - 1][i + half])
        frames += WalkFrame(
            status = "Level $k covers blocks of length $len: table[$k][i] = min(table[${k - 1}][i], " +
                "table[${k - 1}][i + $half]). Only ${n - len + 1} starting positions still fit.",
            cells = a.mapIndexed { i, v ->
                CellView(v.toString(), if (i <= n - len) CellMark.WINDOW else CellMark.DIM)
            },
            aux = (0 until n).map { i ->
                if (i <= n - len) CellView(table[k][i].toString(), CellMark.WINDOW) else CellView("·", CellMark.DIM)
            },
            auxLabel = "table[$k] — min over blocks of length $len",
        )
    }

    val l = 2
    val r = 6
    val length = r - l + 1
    var k = 0
    while ((1 shl (k + 1)) <= length) k++
    val leftBlock = table[k][l]
    val rightStart = r - (1 shl k) + 1
    val rightBlock = table[k][rightStart]

    frames += WalkFrame(
        status = "Query [$l, $r] spans $length elements. The largest power of two that fits is ${1 shl k}, so " +
            "k = $k — two blocks of that size cover the range.",
        cells = a.mapIndexed { i, v -> CellView(v.toString(), if (i in l..r) CellMark.WINDOW else CellMark.DIM) },
        pointers = mapOf(l to "l", r to "r"),
        auxLabel = "k = $k",
        aux = (0 until n).map { i ->
            if (i <= n - (1 shl k)) CellView(table[k][i].toString(), CellMark.WINDOW) else CellView("·", CellMark.DIM)
        },
    )

    frames += WalkFrame(
        status = "Block A starts at $l, block B ends at $r. They overlap at index" +
            (if (rightStart <= l + (1 shl k) - 1) "es ${rightStart}–${l + (1 shl k) - 1}" else " nothing") +
            " — harmless, because min(x, x) = x. That overlap is exactly why sums cannot use this structure.",
        cells = a.mapIndexed { i, v ->
            CellView(
                v.toString(),
                when {
                    i in l until l + (1 shl k) && i in rightStart..r -> CellMark.ACTIVE
                    i in l until l + (1 shl k) -> CellMark.WINDOW
                    i in rightStart..r -> CellMark.WINDOW
                    else -> CellMark.DIM
                },
            )
        },
        pointers = mapOf(l to "A", rightStart to "B"),
        readout = "min(A = $leftBlock, B = $rightBlock)",
    )

    frames += WalkFrame(
        status = "Answer = min($leftBlock, $rightBlock) = ${minOf(leftBlock, rightBlock)} — two array lookups, " +
            "regardless of how wide the range was. Updating any element, though, means rebuilding every level.",
        cells = a.mapIndexed { i, v ->
            CellView(v.toString(), if (v == minOf(leftBlock, rightBlock) && i in l..r) CellMark.RESULT else if (i in l..r) CellMark.WINDOW else CellMark.DIM)
        },
        pointers = mapOf(l to "l", r to "r"),
        readout = "min over [$l, $r] = ${minOf(leftBlock, rightBlock)} in O(1)",
    )

    return frames
}

// ── Config ───────────────────────────────────────────────────────────────────

// ── Association rules (phase 9, batch B7) ────────────────────────────────────
// Two of the three mining algorithms fit the cell row exactly, because both are really about a list
// of things being marked kept or discarded: Apriori marks candidate itemsets, Eclat marks
// transaction ids. Both run on the shared database in AssociationMath.kt, so the frequent itemsets
// they end on are the same and every count is computed rather than written down.

private fun aprioriFrames(): List<WalkFrame> {
    val frames = mutableListOf<WalkFrame>()
    val passes = aprioriPasses()

    frames += WalkFrame(
        status = "${transactions.size} baskets over ${basketItems.size} items, minimum support ${MinSupport} of " +
            "${transactions.size}. The cells are the ${basketItems.size} single items; the row beneath is how many " +
            "baskets each appears in.",
        cells = basketItems.map { CellView(it) },
        aux = basketItems.map { CellView(support(setOf(it)).toString(), CellMark.DIM) },
        auxLabel = "support",
    )

    passes.forEach { pass ->
        val labels = pass.candidates.map { it.label }
        val counting = pass.candidates.map { c ->
            CellView(c.label, if (c.prunedBySubset != null) CellMark.DIM else CellMark.ACTIVE)
        }

        if (pass.k > 1) {
            val pruned = pass.candidates.filter { it.prunedBySubset != null }
            frames += WalkFrame(
                status = if (pruned.isEmpty()) {
                    "Level ${pass.k}: join the frequent ${pass.k - 1}-itemsets pairwise to get ${labels.size} " +
                        "candidates. Every ${pass.k - 1}-subset of every one of them is frequent, so none can be " +
                        "ruled out in advance — all ${labels.size} have to be counted."
                } else {
                    "Level ${pass.k}: joining gives ${labels.size} candidates, but " +
                        "${pruned.joinToString { it.label }} contains the subset " +
                        "${pruned.joinToString { itemsetLabel(it.prunedBySubset!!) }}, which level ${pass.k - 1} " +
                        "already found infrequent. **A superset of an infrequent set cannot be frequent** — so it is " +
                        "discarded without reading a single basket. That is the Apriori property, and it is the whole " +
                        "algorithm."
                },
                cells = pass.candidates.map { c ->
                    CellView(c.label, if (c.prunedBySubset != null) CellMark.DIM else CellMark.IDLE)
                },
                aux = pass.candidates.map { c ->
                    CellView(if (c.prunedBySubset != null) "✗" else "?", CellMark.DIM)
                },
                auxLabel = "before counting",
                readout = "${pass.counted} of ${pass.candidates.size} need a database pass",
            )
        }

        frames += WalkFrame(
            status = "Counting pass ${pass.k}: read all ${transactions.size} baskets once and tally the " +
                "${pass.counted} surviving candidate${if (pass.counted == 1) "" else "s"}. " +
                pass.candidates.filter { it.prunedBySubset == null && !it.frequent }
                    .let { below ->
                        if (below.isEmpty()) "All of them clear support ${MinSupport}."
                        else "${below.joinToString { "${it.label} (${it.support})" }} " +
                            "${if (below.size == 1) "falls" else "fall"} below ${MinSupport} and " +
                            "${if (below.size == 1) "is" else "are"} dropped."
                    },
            cells = counting,
            aux = pass.candidates.map { c ->
                CellView(
                    if (c.prunedBySubset != null) "—" else c.support.toString(),
                    when {
                        c.prunedBySubset != null -> CellMark.DIM
                        c.frequent -> CellMark.DONE
                        else -> CellMark.IDLE
                    },
                )
            },
            auxLabel = "support",
            readout = "L${pass.k} = { ${pass.frequent.joinToString { it.label }} }",
        )
    }

    val last = passes.last()
    frames += WalkFrame(
        status = "No pair of ${last.k}-itemsets shares a prefix, so nothing can be joined and the search stops. " +
            "The frequent sets are everything marked green along the way — found in ${passes.size} database passes, " +
            "one per level, which is Apriori's cost and its weakness on long itemsets.",
        cells = last.frequent.map { CellView(it.label, CellMark.RESULT) }.ifEmpty { listOf(CellView("—")) },
        aux = last.frequent.map { CellView(it.support.toString(), CellMark.RESULT) }.ifEmpty { listOf(CellView("—")) },
        auxLabel = "support",
        readout = "${passes.size} database passes",
    )

    // Rules, and the reason confidence alone is not enough.
    val rules = allRules()
    val strongest = rules.first()
    val misleading = rules.filter { it.lift < 1.0 }.maxByOrNull { it.confidence }
    frames += WalkFrame(
        status = "Frequent itemsets are not rules yet. Split each one every way and score the split: confidence is " +
            "P(consequent | antecedent), lift is that divided by P(consequent) on its own. " +
            "${strongest.label} has confidence ${"%.2f".format(strongest.confidence)} and lift " +
            "${"%.2f".format(strongest.lift)}.",
        cells = rules.take(6).map { CellView(it.label.replace(" → ", "→"), CellMark.IDLE) },
        aux = rules.take(6).map { CellView("%.2f".format(it.lift), if (it.lift > 1.0) CellMark.DONE else CellMark.IDLE) },
        auxLabel = "lift",
    )

    if (misleading != null) {
        frames += WalkFrame(
            status = "And the trap. ${misleading.label} has confidence ${"%.2f".format(misleading.confidence)} — a " +
                "rule that fires ${"%.0f".format(misleading.confidence * 100)}% of the time, which sounds like a " +
                "finding. But ${itemsetLabel(misleading.consequent)} appears in " +
                "${support(misleading.consequent)} of ${transactions.size} baskets anyway, so lift is " +
                "${"%.2f".format(misleading.lift)}: buying ${itemsetLabel(misleading.antecedent)} makes " +
                "${itemsetLabel(misleading.consequent)} *less* likely, not more. Confidence cannot see that, because " +
                "it never looks at how common the consequent is.",
            // Top eight by confidence — enough cells to make the point, few enough to stay legible.
            cells = rules.sortedByDescending { it.confidence }.take(8)
                .map { CellView(it.label.replace(" → ", "→"), if (it === misleading) CellMark.ACTIVE else CellMark.DIM) },
            aux = rules.sortedByDescending { it.confidence }.take(8)
                .map { CellView("%.2f".format(it.confidence), if (it === misleading) CellMark.ACTIVE else CellMark.DIM) },
            auxLabel = "confidence",
            readout = "conf ${"%.2f".format(misleading.confidence)} · lift ${"%.2f".format(misleading.lift)}",
        )
    }
    return frames
}

private fun eclatFrames(): List<WalkFrame> {
    val frames = mutableListOf<WalkFrame>()
    val order = basketItems.filter { support(setOf(it)) >= MinSupport }.sortedByDescending { support(setOf(it)) }

    fun tidRow(tids: Collection<Int>, mark: CellMark = CellMark.WINDOW) = transactions.indices.map { t ->
        CellView("${t + 1}", if (t in tids) mark else CellMark.DIM)
    }

    frames += WalkFrame(
        status = "Apriori stores the database by row: basket 1 holds these items, basket 2 holds those. Eclat turns it " +
            "on its side and stores it by column — for each item, the set of basket ids containing it. Same data, and " +
            "the whole algorithm follows from the layout.",
        cells = transactions.mapIndexed { t, items -> CellView("${t + 1}") },
        aux = transactions.map { CellView(it.sorted().joinToString(""), CellMark.IDLE) },
        auxLabel = "items",
    )

    order.forEach { item ->
        val tids = tidList(item)
        frames += WalkFrame(
            status = "The tid-list for $item (${basketNames[item]}): baskets " +
                "${tids.joinToString { "${it + 1}" }}. Its support is not counted — it is the length of this list, " +
                "${tids.size}. That is the trade Eclat makes: hold the lists in memory and never scan the database " +
                "again.",
            cells = tidRow(tids),
            aux = transactions.indices.map { CellView(if (it < tids.size) "•" else "", CellMark.DIM) },
            auxLabel = "|t($item)| = ${tids.size}",
            readout = "support($item) = ${tids.size}",
        )
    }

    val a = order[0]
    val b = order[1]
    val ta = tidList(a)
    val tb = tidList(b)
    val intersection = ta.filter { it in tb }
    frames += WalkFrame(
        status = "Extending $a with $b is a set intersection: t($a) ∩ t($b). The cells show t($a) in blue; the row " +
            "beneath marks which of those also appear in t($b). ${intersection.size} survive, so support($a$b) = " +
            "${intersection.size} — again with no counting pass, because the intersection *is* the count.",
        cells = tidRow(ta),
        aux = transactions.indices.map { t ->
            CellView(if (t in intersection) "✓" else if (t in ta) "✗" else "", if (t in intersection) CellMark.DONE else CellMark.DIM)
        },
        auxLabel = "also in t($b)",
        readout = "support($a$b) = ${intersection.size}",
    )

    val steps = eclatSteps().filter { it.depth > 0 }
    val deepest = steps.maxByOrNull { it.itemset.size }
    steps.take(6).forEach { step ->
        frames += WalkFrame(
            status = "Depth ${step.depth}: ${step.label}, ${step.tids.size} tids. " +
                if (step.frequent) {
                    "Still frequent, so the search goes deeper along this branch before backtracking — depth-first, " +
                        "unlike Apriori's level-by-level sweep."
                } else {
                    "Below support ${MinSupport}, so this branch is abandoned immediately and nothing under it is " +
                        "ever generated."
                },
            cells = tidRow(step.tids, if (step.frequent) CellMark.DONE else CellMark.IDLE),
            aux = transactions.indices.map { CellView("", CellMark.DIM) },
            auxLabel = step.label,
            readout = "support(${step.label}) = ${step.tids.size}",
        )
    }

    val frequent = eclatSteps().filter { it.frequent && it.itemset.size >= 2 }
    frames += WalkFrame(
        status = "The frequent itemsets Eclat ends with are exactly the ones Apriori ends with — " +
            "${frequent.joinToString { it.label }} — which is the point: these are different searches of the same " +
            "space, not different definitions of the answer. Eclat wins when tid-lists are short (sparse data, high " +
            "support) and loses when they are long, because a tid-list for a common item is nearly the whole database " +
            "and intersecting two of them is expensive. ${deepest?.let { "The deepest branch reached ${it.label}." } ?: ""}",
        cells = frequent.map { CellView(it.label, CellMark.RESULT) },
        aux = frequent.map { CellView(it.tids.size.toString(), CellMark.RESULT) },
        auxLabel = "support",
        readout = "${frequent.size} frequent itemsets of size ≥ 2",
    )
    return frames
}

// ── Data preprocessing (phase 9, batch B8) ───────────────────────────────────
// Five of the nine preprocessing topics are column transforms, and a column is exactly what this
// widget draws: the cell row is the raw column, the aux row is what the transform did to it. Every
// number comes from PreprocessMath.kt, which scores each rule against a model rather than asserting
// it.

private fun labelEncodingFrames(): List<WalkFrame> {
    val frames = mutableListOf<WalkFrame>()
    val sample = EncodingLab.rows.take(10)
    val codes = sample.map { EncodingLab.labelCodes.getValue(it.category) }

    frames += WalkFrame(
        status = "Label encoding replaces each category with an integer. Ten rows of a colour column, and the " +
            "codes ${EncodingLab.categories.withIndex().joinToString(", ") { (i, c) -> "$c=$i" }}.",
        cells = sample.map { CellView(it.category) },
        aux = codes.map { CellView(it.toInt().toString(), CellMark.WINDOW) },
        auxLabel = "label code",
        readout = "one column in, one column out",
    )

    frames += WalkFrame(
        status = "What that buys is width: one column instead of ${EncodingLab.categories.size}. What it costs is " +
            "an ordering and a spacing the categories do not have. Under these codes \"red\" is " +
            "${EncodingLab.codeDistance("red", "yellow").toInt()} away from \"yellow\" and " +
            "${EncodingLab.codeDistance("red", "green").toInt()} from \"green\" — but the colours are not ordered " +
            "at all, and one-hot puts every pair at the same distance, √2 = ${"%.3f".format(EncodingLab.oneHotDistance())}.",
        cells = EncodingLab.categories.map { CellView(it) },
        aux = EncodingLab.categories.map { CellView(EncodingLab.labelCodes.getValue(it).toInt().toString(), CellMark.WINDOW) },
        auxLabel = "implied position on a line",
        readout = "red→yellow ${EncodingLab.codeDistance("red", "yellow").toInt()}, red→green ${EncodingLab.codeDistance("red", "green").toInt()}",
    )

    val linear = EncodingLab.linearFits
    val label = linear.first { it.name == "label encoding" }
    val oneHot = linear.first { it.name == "one-hot" }
    frames += WalkFrame(
        status = "So score it. The true effect per category here is non-monotone in the code order " +
            "(${EncodingLab.categories.joinToString(", ") { "$it ${EncodingLab.effects.getValue(it).toInt()}" }}), which " +
            "is the case the rule is really about. A least-squares fit on the label code lands at MSE " +
            "${"%.2f".format(label.error)}; on one-hot, ${"%.3f".format(oneHot.error)} — " +
            "${"%.0f".format(label.error / oneHot.error)}× worse.",
        cells = EncodingLab.categories.map { CellView(it) },
        aux = EncodingLab.categories.map { CellView(EncodingLab.effects.getValue(it).toInt().toString(), CellMark.ACTIVE) },
        auxLabel = "true effect",
        readout = "MSE ${"%.2f".format(label.error)} vs ${"%.3f".format(oneHot.error)}",
    )

    val depths = listOf(0, 1, 2, 3)
    frames += WalkFrame(
        status = "But that verdict is about the *model*, not the encoding. A tree never reads the code as a " +
            "number, only as somewhere to split — and grown on the same label-coded column it reaches MSE " +
            "${"%.3f".format(EncodingLab.treeError(2))} at depth 2, which is one-hot's number to three decimals. " +
            "Label encoding is free for trees and expensive for anything that multiplies the code by a weight.",
        cells = depths.map { CellView("depth $it") },
        aux = depths.map { CellView("%.2f".format(EncodingLab.treeError(it)), if (it >= 2) CellMark.DONE else CellMark.DIM) },
        auxLabel = "tree MSE on the label code",
        readout = "depth 2 = ${"%.3f".format(EncodingLab.treeError(2))} = one-hot",
    )

    frames += WalkFrame(
        status = "Which makes the rule conditional rather than absolute: label-encode for trees and boosted " +
            "ensembles, one-hot for linear models, distance-based models and anything that reads the number. And " +
            "if the categories *are* ordered — small, medium, large — the code is the right representation and " +
            "one-hot throws information away.",
        cells = listOf(
            CellView("trees", CellMark.DONE),
            CellView("linear", CellMark.ACTIVE),
            CellView("k-NN", CellMark.ACTIVE),
            CellView("ordinal data", CellMark.DONE),
        ),
        aux = listOf(
            CellView("free", CellMark.DONE),
            CellView("${"%.0f".format(label.error / oneHot.error)}× worse", CellMark.ACTIVE),
            CellView("distorted", CellMark.ACTIVE),
            CellView("correct", CellMark.DONE),
        ),
        auxLabel = "label encoding is",
    )
    return frames
}

private fun imputationFrames(): List<WalkFrame> {
    val frames = mutableListOf<WalkFrame>()
    val show = 12
    val values = ImputationLab.complete.take(show)
    val holes = ImputationLab.missing.take(show)

    fun row(fill: Double?) = values.indices.map { i ->
        when {
            !holes[i] -> CellView("%.1f".format(values[i]))
            fill == null -> CellView("—", CellMark.ACTIVE)
            else -> CellView("%.1f".format(fill), CellMark.RESULT)
        }
    }

    frames += WalkFrame(
        status = "${ImputationLab.missing.count { it }} of ${ImputationLab.complete.size} values in this column are " +
            "missing — ${"%.0f".format(ImputationLab.MISSING_RATE * 100)}%, completely at random. Twelve rows of it, " +
            "with the holes marked.",
        cells = row(null),
        readout = "${ImputationLab.observedCount} observed, ${ImputationLab.missing.count { it }} missing",
    )

    frames += WalkFrame(
        status = "The cheapest fix is a constant: the mean of what is observed, " +
            "${"%.3f".format(ImputationLab.meanFill)}. Nothing crashes, no rows are lost, and every downstream " +
            "model runs. The column, however, is not the column any more.",
        cells = row(ImputationLab.meanFill),
        aux = row(null),
        auxLabel = "before",
        readout = "fill = ${"%.3f".format(ImputationLab.meanFill)}",
    )

    val complete = ImputationLab.effects.first { it.name == "complete data" }
    val meanFill = ImputationLab.effects.first { it.name == "mean imputation" }
    frames += WalkFrame(
        status = "Here is what it did, measured against the complete data it came from. Variance falls from " +
            "${"%.3f".format(complete.variance)} to ${"%.3f".format(meanFill.variance)} — a ratio of " +
            "${"%.3f".format(meanFill.variance / complete.variance)}, against the " +
            "${"%.2f".format(ImputationLab.predictedVarianceRatio())} that the missing rate alone predicts, since " +
            "the filled values contribute nothing to the spread.",
        cells = ImputationLab.effects.map { CellView(it.name.take(12)) },
        aux = ImputationLab.effects.map { CellView("%.2f".format(it.variance), if (it.name == "complete data") CellMark.DONE else CellMark.ACTIVE) },
        auxLabel = "variance",
        readout = "${"%.3f".format(meanFill.variance / complete.variance)} of the original spread",
    )

    frames += WalkFrame(
        status = "And the damage is not confined to one column. This column correlates with another at " +
            "${"%.3f".format(complete.correlation)} in the complete data; after mean imputation, " +
            "${"%.3f".format(meanFill.correlation)} — because ${"%.0f".format(ImputationLab.MISSING_RATE * 100)}% of " +
            "the rows now carry a value that has nothing to do with their partner. Imputation attenuates every " +
            "relationship the column was in.",
        cells = ImputationLab.effects.map { CellView(it.name.take(12)) },
        aux = ImputationLab.effects.map { CellView("%.3f".format(it.correlation), if (it.name == "complete data") CellMark.DONE else CellMark.ACTIVE) },
        auxLabel = "correlation with partner column",
        readout = "${"%.3f".format(complete.correlation)} → ${"%.3f".format(meanFill.correlation)}",
    )

    val dropped = ImputationLab.effects.first { it.name == "drop the rows" }
    frames += WalkFrame(
        status = "Dropping the rows instead keeps the column honest — variance ${"%.3f".format(dropped.variance)} " +
            "and correlation ${"%.3f".format(dropped.correlation)}, both essentially the originals — and costs " +
            "${ImputationLab.complete.size - dropped.rows} of ${ImputationLab.complete.size} rows. That trade is the " +
            "actual decision, and it is only this clean because the values here are missing *at random*.",
        cells = ImputationLab.effects.map { CellView(it.name.take(12)) },
        aux = ImputationLab.effects.map { CellView(it.rows.toString(), if (it.name == "drop the rows") CellMark.RESULT else CellMark.DIM) },
        auxLabel = "rows surviving",
        readout = "unbiased, at ${"%.0f".format((1 - dropped.rows.toDouble() / ImputationLab.complete.size) * 100)}% of the data",
    )

    frames += WalkFrame(
        status = "One more choice the constant hides. On a skewed column — nine tenths small values, one tenth " +
            "large — the mean is ${"%.1f".format(ImputationLab.skewedCentre.mean)} and the median " +
            "${"%.1f".format(ImputationLab.skewedCentre.median)}. Filling with the mean inserts a value that " +
            "almost no real row has. Median for skewed columns, mode for categorical ones, and the mean only when " +
            "the column is roughly symmetric.",
        cells = listOf(CellView("mean"), CellView("median")),
        aux = listOf(
            CellView("%.1f".format(ImputationLab.skewedCentre.mean), CellMark.ACTIVE),
            CellView("%.1f".format(ImputationLab.skewedCentre.median), CellMark.DONE),
        ),
        auxLabel = "centre of the skewed column",
        readout = "${"%.1f".format(ImputationLab.skewedCentre.mean)} vs ${"%.1f".format(ImputationLab.skewedCentre.median)}",
    )
    return frames
}

private fun outlierFrames(): List<WalkFrame> {
    val frames = mutableListOf<WalkFrame>()

    fun cellsFor(data: List<Double>, flagged: Set<Double>) = data.sorted().takeLast(14).map { value ->
        CellView(
            if (value >= 1000) "%.0f".format(value) else "%.1f".format(value),
            if (value in flagged) CellMark.RESULT else CellMark.IDLE,
        )
    }

    val cleanZ = OutlierLab.zScoreFlags(OutlierLab.clean)
    val cleanIqr = OutlierLab.iqrFlags(OutlierLab.clean)
    frames += WalkFrame(
        status = "A clean column: ${OutlierLab.clean.size} values, no outliers. Both rules agree — z-score flags " +
            "${cleanZ.flagged.size}, the IQR rule flags ${cleanIqr.flagged.size}. The fences are " +
            "${cleanIqr.threshold}. Agreement on easy data is not evidence that two rules are equivalent.",
        cells = cellsFor(OutlierLab.clean, emptySet()),
        readout = "largest 14 values shown",
    )

    val z = OutlierLab.zScoreFlags(OutlierLab.contaminated)
    val iqr = OutlierLab.iqrFlags(OutlierLab.contaminated)
    frames += WalkFrame(
        status = "Add one extreme value, ${"%.0f".format(OutlierLab.EXTREME)}. Both rules catch it: its z-score is " +
            "${"%.2f".format(OutlierLab.selfZScore())}, far past ${OutlierLab.Z_THRESHOLD}, and it is outside the " +
            "IQR fences too. Note what the extreme value did to the statistics it is being judged by, though — the " +
            "mean moved to ${"%.1f".format(OutlierLab.contaminated.average())} and σ to " +
            "${"%.1f".format(kotlin.math.sqrt(OutlierLab.contaminated.let { d -> val m = d.average(); d.sumOf { (it - m) * (it - m) } / (d.size - 1) }))}.",
        cells = cellsFor(OutlierLab.contaminated, z.flagged.toSet()),
        aux = listOf(
            CellView("z flags ${z.flagged.size}", CellMark.RESULT),
            CellView("IQR flags ${iqr.flagged.size}", CellMark.RESULT),
        ),
        auxLabel = "verdicts",
        readout = "its own z = ${"%.2f".format(OutlierLab.selfZScore())}",
    )

    val maskedZ = OutlierLab.zScoreFlags(OutlierLab.masked)
    val maskedIqr = OutlierLab.iqrFlags(OutlierLab.masked)
    val maskedMad = OutlierLab.modifiedZFlags(OutlierLab.masked)
    frames += WalkFrame(
        status = "Now the failure. Outliers that arrive together hide each other: each one inflates σ for the " +
            "rest. Adding extremes until the largest z-score drops under the threshold takes " +
            "${OutlierLab.maskingCount} of them — ${"%.1f".format(OutlierLab.maskingShare * 100)}% of the sample — " +
            "and at that point the z-score rule flags ${maskedZ.flagged.size}. The IQR rule flags all " +
            "${maskedIqr.flagged.size}, because the quartiles have not moved.",
        cells = cellsFor(OutlierLab.masked, maskedIqr.flagged.toSet()),
        aux = listOf(
            CellView("z flags ${maskedZ.flagged.size}", CellMark.DIM),
            CellView("IQR flags ${maskedIqr.flagged.size}", CellMark.RESULT),
            CellView("MAD flags ${maskedMad.flagged.size}", CellMark.RESULT),
        ),
        auxLabel = "verdicts",
        readout = "largest z now ${"%.2f".format(OutlierLab.selfZScore(OutlierLab.masked))} — under ${OutlierLab.Z_THRESHOLD}",
    )

    val sizes = listOf(5, 10, 11, 20, 61)
    frames += WalkFrame(
        status = "There is a second, quieter failure that has nothing to do with masking. A single point in a " +
            "sample of n can never have |z| above (n−1)/√n, because it is inside the mean and the σ being used to " +
            "judge it. Below n = ${OutlierLab.smallestUsableSample} that ceiling is under " +
            "${OutlierLab.Z_THRESHOLD}, so the rule cannot fire at all — it is not strict on small samples, it is " +
            "inert.",
        cells = sizes.map { CellView("n=$it") },
        aux = sizes.map {
            CellView(
                "%.2f".format(OutlierLab.maxPossibleZ(it)),
                if (OutlierLab.maxPossibleZ(it) > OutlierLab.Z_THRESHOLD) CellMark.DONE else CellMark.DIM,
            )
        },
        auxLabel = "max possible |z|",
        readout = "|z| > 3 is unreachable below n = ${OutlierLab.smallestUsableSample}",
    )

    val rules = OutlierLab.breakdownPoints.entries.toList()
    frames += WalkFrame(
        status = "Both failures are the same property: the z-score rule estimates its threshold from statistics " +
            "the outliers are inside. The robust alternatives estimate from statistics they are not — quartiles " +
            "tolerate a quarter of the sample being contaminated, the median and MAD tolerate half. That number " +
            "is the breakdown point, and it is what to check before trusting any outlier rule.",
        cells = rules.map { CellView(it.key.take(16)) },
        aux = rules.map {
            CellView("${"%.0f".format(it.value * 100)}%", if (it.value > 0) CellMark.DONE else CellMark.ACTIVE)
        },
        auxLabel = "breakdown point",
        readout = "use IQR or MAD; keep z-scores for clean, large, symmetric columns",
    )
    return frames
}

private fun chiSquareFrames(): List<WalkFrame> {
    val frames = mutableListOf<WalkFrame>()
    val ranking = FeatureSelectionLab.chiSquareRanking()
    val usefulTable = FeatureSelectionLab.chiSquare(FeatureSelectionLab.data, 0)

    frames += WalkFrame(
        status = "Chi-square feature selection scores every feature against the target on its own, from a " +
            "contingency table. For \"useful\" the table over ${FeatureSelectionLab.data.size} rows is " +
            "${usefulTable.counts.joinToString(" / ") { it.joinToString(",") }} — observed counts by feature value " +
            "and label.",
        cells = listOf("f=0,y=0", "f=0,y=1", "f=1,y=0", "f=1,y=1").mapIndexed { index, label ->
            CellView(label)
        },
        aux = listOf(
            CellView(usefulTable.counts[0][0].toString(), CellMark.WINDOW),
            CellView(usefulTable.counts[0][1].toString(), CellMark.WINDOW),
            CellView(usefulTable.counts[1][0].toString(), CellMark.WINDOW),
            CellView(usefulTable.counts[1][1].toString(), CellMark.WINDOW),
        ),
        auxLabel = "observed",
        readout = "χ² = Σ (observed − expected)² / expected = ${"%.1f".format(usefulTable.chiSquare)}",
    )

    frames += WalkFrame(
        status = "Run over all ${FeatureSelectionLab.featureNames.size} features it ranks them without fitting a " +
            "single model — which is the whole appeal. \"useful\" scores ${"%.0f".format(ranking.first().second)}, " +
            "its near-copy \"duplicate\" ${"%.0f".format(ranking[1].second)}, and the rest are noise-level.",
        cells = ranking.map { CellView(it.first) },
        aux = ranking.map {
            CellView("%.1f".format(it.second), if (it.second > 50) CellMark.DONE else CellMark.DIM)
        },
        auxLabel = "χ²",
        readout = "${FeatureSelectionLab.chiSquareFits()} model fits required",
    )

    frames += WalkFrame(
        status = "It also cannot tell a useful feature from a copy of one. \"duplicate\" agrees with \"useful\" " +
            "90% of the time, scores ${"%.0f".format(ranking[1].second)}, and adds nothing a model does not " +
            "already have — a univariate score has no way to notice, because it never looks at two features " +
            "together.",
        cells = listOf(CellView("useful", CellMark.DONE), CellView("duplicate", CellMark.ACTIVE)),
        aux = listOf(
            CellView("%.0f".format(ranking.first().second), CellMark.DONE),
            CellView("%.0f".format(ranking[1].second), CellMark.ACTIVE),
        ),
        auxLabel = "χ²",
        readout = "redundant, and ranked second",
    )

    val xorRanking = FeatureSelectionLab.chiSquareRanking(FeatureSelectionLab.xorData)
    frames += WalkFrame(
        status = "And here is the blind spot that matters. On data where the label is exactly xorA ⊕ xorB — the " +
            "pair determines it perfectly, with no noise — chi-square ranks " +
            "\"${xorRanking.first().first}\" first at ${"%.1f".format(xorRanking.first().second)} and puts the two " +
            "features that *are* the signal at ${xorRanking.filter { it.first.startsWith("xor") }.joinToString(" and ") { "%.1f".format(it.second) }}. " +
            "A one-at-a-time score cannot see an interaction, and this is what that looks like.",
        cells = xorRanking.map { CellView(it.first) },
        aux = xorRanking.map {
            CellView("%.1f".format(it.second), if (it.first.startsWith("xor")) CellMark.ACTIVE else CellMark.DIM)
        },
        auxLabel = "χ² on XOR-labelled data",
        readout = "the signal ranks below the noise",
    )

    frames += WalkFrame(
        status = "So it is a filter, not a decision: use it to drop obviously-dead columns cheaply on wide data, " +
            "and do not use it to choose between features that might interact. It also requires non-negative " +
            "counts — chi-square on a scaled or centred numeric column is a category error, not a weak result.",
        cells = listOf(CellView("wide data"), CellView("interactions"), CellView("redundancy"), CellView("negatives")),
        aux = listOf(
            CellView("good", CellMark.DONE),
            CellView("blind", CellMark.ACTIVE),
            CellView("blind", CellMark.ACTIVE),
            CellView("invalid", CellMark.ACTIVE),
        ),
        auxLabel = "chi-square is",
    )
    return frames
}

private fun rfeFrames(): List<WalkFrame> {
    val frames = mutableListOf<WalkFrame>()
    val rounds = FeatureSelectionLab.recursiveElimination()

    frames += WalkFrame(
        status = "Recursive feature elimination goes the other way from a filter: fit the model on everything, " +
            "drop the weakest coefficient, refit, repeat. It sees whatever the model can express, and it costs " +
            "${FeatureSelectionLab.fitsRequired(FeatureSelectionLab.featureNames.size)} fits instead of " +
            "${FeatureSelectionLab.chiSquareFits()}.",
        cells = FeatureSelectionLab.featureNames.map { CellView(it) },
        aux = rounds.first().remaining.map {
            CellView("%.3f".format(rounds.first().coefficients.getValue(it)), CellMark.WINDOW)
        },
        auxLabel = "coefficient, all features in",
        readout = "MSE ${"%.4f".format(rounds.first().error)}",
    )

    rounds.forEach { round ->
        frames += WalkFrame(
            status = "Round ${rounds.indexOf(round) + 1}: the smallest coefficient belongs to " +
                "\"${round.dropped}\" (${"%.3f".format(round.coefficients.getValue(round.dropped))}), so it goes. " +
                "MSE is ${"%.4f".format(round.error)} — dropping it costs " +
                (if (rounds.indexOf(round) == rounds.size - 1) "nothing measurable" else "essentially nothing") + ".",
            cells = round.remaining.map {
                CellView(it, if (it == round.dropped) CellMark.ACTIVE else CellMark.IDLE)
            },
            aux = round.remaining.map {
                CellView(
                    "%.3f".format(round.coefficients.getValue(it)),
                    if (it == round.dropped) CellMark.ACTIVE else CellMark.WINDOW,
                )
            },
            auxLabel = "coefficient",
            readout = "MSE ${"%.4f".format(round.error)}",
        )
    }

    val survivor = rounds.last().remaining.first { it != rounds.last().dropped }
    frames += WalkFrame(
        status = "It ends on \"$survivor\" alone, having dropped the noise, the redundant copy and both XOR " +
            "features — and the MSE barely moved across the whole elimination " +
            "(${"%.4f".format(rounds.first().error)} → ${"%.4f".format(rounds.last().error)}), which is the honest " +
            "signal that those four columns were carrying nothing this model could use.",
        cells = listOf(CellView(survivor, CellMark.RESULT)),
        aux = listOf(CellView("%.4f".format(rounds.last().error), CellMark.RESULT)),
        auxLabel = "MSE with one feature",
        readout = "${rounds.size} rounds, ${rounds.size} fits",
    )

    val xorRounds = FeatureSelectionLab.recursiveElimination(FeatureSelectionLab.xorData)
    frames += WalkFrame(
        status = "RFE is not a cure for the interaction blindness, though — it inherits its model's. On the " +
            "XOR-labelled data it drops \"${xorRounds.first().dropped}\" first and never finds the pair either, " +
            "because a linear model cannot express XOR and so gives both features a coefficient near zero. Both " +
            "selectors fail on that data, for different reasons: chi-square because it looks one at a time, RFE " +
            "because its model cannot see it.",
        cells = xorRounds.map { CellView(it.dropped) },
        aux = xorRounds.map { CellView("%.4f".format(it.error), CellMark.DIM) },
        auxLabel = "dropped, in order · MSE",
        readout = "swap the estimator for a tree and this changes",
    )
    return frames
}

// ── Interview-prep pattern walks ─────────────────────────────────────────────
// The pattern topics are the interview framing of algorithms that already have a walk, so each one
// runs the variant an interviewer actually asks for rather than repeating the textbook version:
// prefix sums against a hash map instead of a range query, binary search over an answer range
// instead of over the array.

private fun prefixSumPatternFrames(): List<WalkFrame> {
    val a = listOf(3, 4, 7, 2, -3, 1, 4, 2)
    val k = 7
    val seen = LinkedHashMap<Int, Int>()
    seen[0] = 1
    var total = 0
    var count = 0
    val frames = mutableListOf<WalkFrame>()

    fun mapRow(hit: Int? = null) = seen.entries.map { (prefix, times) ->
        CellView("$prefix×$times", if (prefix == hit) CellMark.ACTIVE else CellMark.WINDOW)
    }

    frames += WalkFrame(
        status = "Counting subarrays that sum to $k. The map is seeded with prefix 0 seen once, so a subarray " +
            "starting at index 0 needs no special case. Note the negative value — a sliding window would break here.",
        cells = a.map { CellView(it.toString(), CellMark.DIM) },
        aux = mapRow(),
        auxLabel = "prefix totals seen",
        readout = "count = 0",
    )

    a.forEachIndexed { i, x ->
        total += x
        val need = total - k
        val hits = seen[need] ?: 0
        count += hits
        frames += WalkFrame(
            status = "total = $total after a[$i] = $x. A subarray ending here sums to $k exactly when some earlier " +
                "prefix equals $need — " +
                if (hits > 0) "that prefix was seen $hits time(s), so $hits subarray(s) end here."
                else "no prefix of $need has been seen, so none end here.",
            cells = a.mapIndexed { j, v ->
                CellView(
                    v.toString(),
                    when {
                        j == i -> CellMark.ACTIVE
                        j < i -> CellMark.WINDOW
                        else -> CellMark.IDLE
                    },
                )
            },
            pointers = mapOf(i to "i"),
            aux = mapRow(hit = if (hits > 0) need else null),
            auxLabel = "prefix totals seen",
            readout = "count = $count",
        )
        seen[total] = (seen[total] ?: 0) + 1
    }

    frames += WalkFrame(
        status = "$count subarrays sum to $k, found in one pass. The map holds totals, not indices — that is what " +
            "makes counting O(n) instead of checking every (l, r) pair.",
        cells = a.map { CellView(it.toString(), CellMark.RESULT) },
        aux = mapRow(),
        auxLabel = "prefix totals seen",
        readout = "count = $count",
    )
    return frames
}

private fun binarySearchAnswerFrames(): List<WalkFrame> {
    val weights = listOf(3, 2, 2, 4, 1, 4)
    val days = 3
    val frames = mutableListOf<WalkFrame>()

    // Day index each package lands on under a given capacity; also the feasibility answer.
    fun pack(cap: Int): List<Int> {
        val assigned = mutableListOf<Int>()
        var day = 1
        var load = 0
        for (w in weights) {
            if (load + w > cap) {
                day++
                load = 0
            }
            load += w
            assigned += day
        }
        return assigned
    }

    fun dayRow(assigned: List<Int>) = assigned.map { CellView("d$it", CellMark.WINDOW) }

    var lo = weights.max()
    var hi = weights.sum()

    frames += WalkFrame(
        status = "Ship these packages in order within $days days, minimising the ship's capacity. The array is not " +
            "sorted and never will be — what is monotone is the question \"does capacity x work?\".",
        cells = weights.map { CellView(it.toString(), CellMark.IDLE) },
        readout = "lo = $lo (largest package) · hi = $hi (all in one day)",
    )

    while (lo < hi) {
        val mid = (lo + hi) / 2
        val assigned = pack(mid)
        val used = assigned.last()
        val ok = used <= days
        frames += WalkFrame(
            status = "Probe capacity $mid: packing left to right needs $used day(s). " +
                if (ok) "That fits in $days — keep $mid as a candidate and search below it (hi = mid)."
                else "That exceeds $days — $mid is too small, so every capacity ≤ $mid is too (lo = mid + 1).",
            cells = weights.mapIndexed { i, w ->
                CellView(w.toString(), if (assigned[i] % 2 == 1) CellMark.WINDOW else CellMark.ACTIVE)
            },
            aux = dayRow(assigned),
            auxLabel = "day each package sails on",
            readout = "lo = $lo · mid = $mid · hi = $hi · feasible = $ok",
        )
        if (ok) hi = mid else lo = mid + 1
    }

    val assigned = pack(lo)
    frames += WalkFrame(
        status = "lo and hi meet at $lo — the smallest capacity that still fits in $days days. ${weights.size} packages " +
            "were rescanned once per probe: O(n log R) where R is the width of the answer range, not the array.",
        cells = weights.mapIndexed { i, w ->
            CellView(w.toString(), if (assigned[i] % 2 == 1) CellMark.RESULT else CellMark.DONE)
        },
        aux = dayRow(assigned),
        auxLabel = "day each package sails on",
        readout = "answer = $lo",
    )
    return frames
}

private fun monotonicStackFrames(): List<WalkFrame> {
    val a = listOf(2, 1, 5, 6, 2, 3)
    val nge = IntArray(a.size) { -1 }
    val st = ArrayDeque<Int>()
    val frames = mutableListOf<WalkFrame>()

    fun stackRow() = st.map { CellView("a[$it]=${a[it]}", CellMark.WINDOW) }
        .ifEmpty { listOf(CellView("empty", CellMark.DIM)) }

    fun row(current: Int) = a.mapIndexed { i, v ->
        CellView(
            v.toString(),
            when {
                i == current -> CellMark.ACTIVE
                nge[i] != -1 -> CellMark.DONE
                st.contains(i) -> CellMark.WINDOW
                else -> CellMark.IDLE
            },
        )
    }

    frames += WalkFrame(
        status = "Next greater element for every index. The stack will hold indices whose answer is still unknown, " +
            "kept in decreasing value order — anything smaller than the incoming element cannot stay.",
        cells = a.map { CellView(it.toString(), CellMark.IDLE) },
        aux = stackRow(),
        auxLabel = "stack (indices, values decreasing)",
    )

    a.forEachIndexed { i, x ->
        val resolved = mutableListOf<Int>()
        while (st.isNotEmpty() && a[st.last()] < x) {
            val idx = st.removeLast()
            nge[idx] = x
            resolved += idx
        }
        st.addLast(i)
        frames += WalkFrame(
            status = "a[$i] = $x. " +
                if (resolved.isEmpty()) "Nothing on the stack is smaller, so nothing is resolved — push $i and wait."
                else "It beats ${resolved.joinToString(", ") { "a[$it]=${a[it]}" }}, so $x is their next greater " +
                    "element. Pop them, then push $i.",
            cells = row(i),
            pointers = mapOf(i to "i"),
            aux = stackRow(),
            auxLabel = "stack (indices, values decreasing)",
        )
    }

    frames += WalkFrame(
        status = "The ${st.size} index(es) still on the stack have nothing greater to their right, so they keep −1. " +
            "Every index was pushed once and popped at most once — O(n), not the O(n²) of scanning right each time.",
        cells = nge.mapIndexed { i, v ->
            CellView(if (v == -1) "−1" else v.toString(), if (v == -1) CellMark.DIM else CellMark.RESULT)
        },
        aux = a.map { CellView(it.toString(), CellMark.IDLE) },
        auxLabel = "input",
        readout = "row above = next greater per index",
    )
    return frames
}

private fun cyclicSortFrames(): List<WalkFrame> {
    val a = intArrayOf(3, 1, 5, 4, 3)
    val n = a.size
    val frames = mutableListOf<WalkFrame>()

    fun row(current: Int, settledUpTo: Int) = a.mapIndexed { i, v ->
        CellView(
            v.toString(),
            when {
                i == current -> CellMark.ACTIVE
                v == i + 1 && i < settledUpTo -> CellMark.DONE
                else -> CellMark.IDLE
            },
        )
    }

    frames += WalkFrame(
        status = "$n values that should be 1..$n, one of them repeated. Because every value knows the index it " +
            "belongs at, sorting needs no comparisons — only swaps.",
        cells = a.map { CellView(it.toString(), CellMark.IDLE) },
        aux = List(n) { CellView("${it + 1}", CellMark.DIM) },
        auxLabel = "index i wants value i+1",
    )

    var i = 0
    while (i < n) {
        val home = a[i] - 1
        if (a[i] != a[home]) {
            val moved = a[i]
            val displaced = a[home]
            a[i] = displaced
            a[home] = moved
            frames += WalkFrame(
                status = "a[$i] = $moved belongs at index $home. Swap it there; index $i now holds $displaced and " +
                    "still has to be placed, so i does not advance.",
                cells = row(i, i),
                pointers = mapOf(i to "i", home to "home"),
                aux = List(n) { CellView("${it + 1}", if (it == home) CellMark.DONE else CellMark.DIM) },
                auxLabel = "index i wants value i+1",
            )
        } else {
            frames += WalkFrame(
                status = if (a[i] == i + 1) "a[$i] = ${a[i]} is already home. Advance."
                else "a[$i] = ${a[i]}, but index $home already holds ${a[home]} — a duplicate, so nothing can be " +
                    "placed here. Advance and let the final scan report it.",
                cells = row(i, i + 1),
                pointers = mapOf(i to "i"),
                aux = List(n) { CellView("${it + 1}", if (it <= i) CellMark.DONE else CellMark.DIM) },
                auxLabel = "index i wants value i+1",
            )
            i++
        }
    }

    val bad = (0 until n).firstOrNull { a[it] != it + 1 }
    frames += WalkFrame(
        status = if (bad == null) "Every value sits at its own index — nothing missing, nothing duplicated."
        else "Index $bad holds ${a[bad]} instead of ${bad + 1}: ${a[bad]} is the duplicate and ${bad + 1} is missing. " +
            "At most $n swaps, no hash set, O(1) extra space.",
        cells = a.mapIndexed { j, v ->
            CellView(v.toString(), if (j == bad) CellMark.RESULT else CellMark.DONE)
        },
        aux = List(n) { CellView("${it + 1}", if (it == bad) CellMark.RESULT else CellMark.DIM) },
        auxLabel = "index i wants value i+1",
        readout = bad?.let { "duplicate = ${a[it]} · missing = ${it + 1}" },
    )
    return frames
}

private fun inPlaceReversalFrames(): List<WalkFrame> {
    val nodes = listOf("A", "B", "C", "D", "E")
    val frames = mutableListOf<WalkFrame>()
    var prev = -1
    var cur = 0

    fun chainRow(): List<CellView> {
        val chain = mutableListOf<CellView>()
        var walk = prev
        while (walk >= 0) {
            chain += CellView(nodes[walk], CellMark.DONE)
            walk--
        }
        return chain.ifEmpty { listOf(CellView("empty", CellMark.DIM)) }
    }

    fun row() = nodes.indices.map { i ->
        CellView(
            nodes[i],
            when {
                i == cur -> CellMark.ACTIVE
                i < cur -> CellMark.DONE
                else -> CellMark.IDLE
            },
        )
    }

    fun pointerRow(): Map<Int, String> {
        val labels = mutableMapOf<Int, String>()
        if (prev >= 0) labels[prev] = "prev"
        if (cur in nodes.indices) labels[cur] = "cur"
        return labels
    }

    frames += WalkFrame(
        status = "A → B → C → D → E, to be reversed without allocating a second list. prev starts null, cur starts " +
            "at the head; the reversed part grows behind cur.",
        cells = row(),
        pointers = pointerRow(),
        aux = chainRow(),
        auxLabel = "reversed so far (head first)",
    )

    while (cur in nodes.indices) {
        val next = cur + 1
        val nextLabel = if (next in nodes.indices) nodes[next] else "null"
        val prevLabel = if (prev >= 0) nodes[prev] else "null"
        val moved = nodes[cur]
        prev = cur
        cur = next
        frames += WalkFrame(
            status = "Save next = $nextLabel first — the instant $moved.next is reassigned, the rest of the list is " +
                "unreachable. Then $moved.next = $prevLabel, and both pointers slide right.",
            cells = row(),
            pointers = pointerRow(),
            aux = chainRow(),
            auxLabel = "reversed so far (head first)",
            readout = "prev = ${nodes[prev]} · cur = ${if (cur in nodes.indices) nodes[cur] else "null"}",
        )
    }

    frames += WalkFrame(
        status = "cur ran off the end, so prev — ${nodes[prev]} — is the new head. One pass, three references, no " +
            "extra list: O(n) time and O(1) space.",
        cells = nodes.indices.reversed().map { CellView(nodes[it], CellMark.RESULT) },
        aux = chainRow(),
        auxLabel = "reversed so far (head first)",
        readout = "new head = ${nodes[prev]}",
    )
    return frames
}

private fun kWayMergeFrames(): List<WalkFrame> {
    val lists = listOf(
        listOf(2, 6, 8),
        listOf(3, 6, 7),
        listOf(1, 3, 4),
    )
    val k = lists.size
    val total = lists.sumOf { it.size }
    val cursor = IntArray(k)
    val out = mutableListOf<Int>()
    val frames = mutableListOf<WalkFrame>()

    // Heap entry per list: the head still unmerged. Kept as a sorted view — the point is its size, k.
    fun heapEntries() = (0 until k).filter { cursor[it] < lists[it].size }
        .map { it to lists[it][cursor[it]] }
        .sortedBy { it.second }

    fun heapRow(popped: Int? = null) = heapEntries().map { (list, value) ->
        CellView("$value·L${list + 1}", if (list == popped) CellMark.ACTIVE else CellMark.DONE)
    }.ifEmpty { listOf(CellView("empty", CellMark.DIM)) }

    fun outRow() = List(total) { i ->
        if (i < out.size) CellView(out[i].toString(), CellMark.WINDOW) else CellView("·", CellMark.DIM)
    }

    frames += WalkFrame(
        status = "Three sorted lists: ${lists.joinToString("  ") { it.joinToString(",") }}. Concatenating and sorting " +
            "throws the existing order away; a heap of one head per list keeps it.",
        cells = outRow(),
        aux = heapRow(),
        auxLabel = "min-heap of list heads (size ≤ $k)",
    )

    while (out.size < total) {
        val (list, value) = heapEntries().first()
        out += value
        cursor[list]++
        val refill = if (cursor[list] < lists[list].size) lists[list][cursor[list]] else null
        frames += WalkFrame(
            status = "Smallest head is $value from L${list + 1} — pop it into the output, then " +
                (refill?.let { "push L${list + 1}'s next element, $it." } ?: "L${list + 1} is exhausted, so the heap shrinks."),
            cells = outRow(),
            aux = heapRow(popped = list),
            auxLabel = "min-heap of list heads (size ≤ $k)",
            readout = "merged ${out.size} of $total",
        )
    }

    frames += WalkFrame(
        status = "$total elements merged with a heap that never held more than $k entries: O(n log $k). Sorting the " +
            "concatenation would have been O(n log n) and would have ignored the sortedness you were handed.",
        cells = out.map { CellView(it.toString(), CellMark.RESULT) },
        aux = heapRow(),
        auxLabel = "min-heap of list heads (size ≤ $k)",
        readout = "merged = ${out.joinToString(", ")}",
    )
    return frames
}

private fun greedyIntervalsFrames(): List<WalkFrame> {
    val raw = listOf(1 to 4, 2 to 3, 3 to 5, 0 to 7, 6 to 8, 5 to 9)
    val sorted = raw.sortedBy { it.second }
    val frames = mutableListOf<WalkFrame>()

    fun label(iv: Pair<Int, Int>) = "${iv.first}–${iv.second}"

    frames += WalkFrame(
        status = "Six meetings, one room: keep as many as possible. Sorting by start or by duration both have " +
            "counterexamples — sort by end time, because finishing early is what frees the room.",
        cells = raw.map { CellView(label(it), CellMark.IDLE) },
        intervals = raw.map { IntervalView(it.first, it.second, CellMark.IDLE) },
    )
    frames += WalkFrame(
        status = "Sorted by end: ${sorted.joinToString(", ") { label(it) }}. Now one scan decides everything.",
        cells = sorted.map { CellView(label(it), CellMark.WINDOW) },
        intervals = sorted.map { IntervalView(it.first, it.second, CellMark.WINDOW) },
    )

    val kept = mutableListOf<Pair<Int, Int>>()
    var last = Int.MIN_VALUE
    sorted.forEachIndexed { index, iv ->
        val take = iv.first >= last
        if (take) {
            kept += iv
            last = iv.second
        }
        frames += WalkFrame(
            status = if (take) "${label(iv)} starts at ${iv.first}, at or after the room frees at " +
                (if (kept.size == 1) "the start of the day" else "${kept[kept.size - 2].second}") +
                " — keep it. The room is now busy until ${iv.second}."
            else "${label(iv)} starts at ${iv.first}, before the room frees at $last — it clashes, so drop it. " +
                "Nothing kept so far needs revisiting.",
            cells = sorted.mapIndexed { i, v ->
                CellView(
                    label(v),
                    when {
                        i == index -> CellMark.ACTIVE
                        v in kept -> CellMark.DONE
                        i < index -> CellMark.DIM
                        else -> CellMark.IDLE
                    },
                )
            },
            pointers = mapOf(index to "i"),
            intervals = kept.map { IntervalView(it.first, it.second, CellMark.DONE) } +
                IntervalView(iv.first, iv.second, if (take) CellMark.DONE else CellMark.ACTIVE),
        )
    }

    frames += WalkFrame(
        status = "${kept.size} of ${raw.size} meetings fit: ${kept.joinToString(", ") { label(it) }}. The same scan " +
            "answers \"minimum removals\" — ${raw.size - kept.size} — because the two questions are complements.",
        cells = kept.map { CellView(label(it), CellMark.RESULT) },
        intervals = kept.map { IntervalView(it.first, it.second, CellMark.RESULT) },
        readout = "kept ${kept.size} · removed ${raw.size - kept.size}",
    )
    return frames
}

// Decoding "3[a2[bc]]" — the nesting problem in miniature. Each '[' pushes the context that must be
// restored, each ']' pops one and folds the finished piece into its parent.
private fun expressionStackFrames(): List<WalkFrame> {
    val source = "3[a2[bc]]"
    val frames = mutableListOf<WalkFrame>()
    val countStack = ArrayDeque<Int>()
    val textStack = ArrayDeque<String>()
    var current = ""
    var number = 0

    fun stackRow() = countStack.indices.map { i ->
        CellView("${countStack.elementAt(i)}×\"${textStack.elementAt(i)}\"", CellMark.WINDOW)
    }.ifEmpty { listOf(CellView("empty", CellMark.DIM)) }

    fun cells(at: Int) = source.mapIndexed { i, c ->
        CellView(
            c.toString(),
            when {
                i == at -> CellMark.ACTIVE
                i < at -> CellMark.DONE
                else -> CellMark.IDLE
            },
        )
    }

    frames += WalkFrame(
        status = "Decode \"$source\". Nesting is the signal: a stack holds exactly what a recursive parser would " +
            "keep in its call frames — the repeat count and the text built so far — without the depth limit.",
        cells = source.map { CellView(it.toString()) },
        aux = stackRow(),
        auxLabel = "stack: pending count × text",
    )

    source.forEachIndexed { i, c ->
        val status: String
        when {
            c.isDigit() -> {
                number = number * 10 + (c - '0')
                status = "'$c' is a digit — accumulate it into the repeat count ($number). Multi-digit counts are " +
                    "why this is accumulated rather than read once."
            }
            c == '[' -> {
                countStack.addLast(number)
                textStack.addLast(current)
                number = 0
                current = ""
                status = "'[' opens a context: push the count ${countStack.last()} and the text built so far " +
                    "(\"${textStack.last()}\"), then start fresh. Pushing *before* resetting is the whole trick."
            }
            c == ']' -> {
                val repeat = countStack.removeLast()
                val parent = textStack.removeLast()
                current = parent + current.repeat(repeat)
                status = "']' closes it: pop the count $repeat and the parent text, repeat the finished piece, and " +
                    "fold it back into the parent. current = \"$current\"."
            }
            else -> {
                current += c
                status = "'$c' is literal — append it to the piece being built at this depth: \"$current\"."
            }
        }
        frames += WalkFrame(
            status = status,
            cells = cells(i),
            pointers = mapOf(i to "i"),
            aux = stackRow(),
            auxLabel = "stack: pending count × text",
            readout = "current = \"$current\"" + if (number > 0) " · count $number" else "",
        )
    }

    frames += WalkFrame(
        status = "\"$current\" — ${current.length} characters, one pass, and the stack never held more than the " +
            "nesting depth. Balanced brackets, directory paths and arithmetic with parentheses are the same loop " +
            "with a different thing pushed.",
        cells = current.map { CellView(it.toString(), CellMark.RESULT) },
        aux = stackRow(),
        auxLabel = "stack: pending count × text",
        readout = "decoded = \"$current\"",
    )
    return frames
}

private fun heapSchedulingFrames(): List<WalkFrame> {
    val meetings = listOf(0 to 30, 5 to 10, 6 to 12, 15 to 20, 25 to 35)
    val sorted = meetings.sortedBy { it.first }
    val heap = mutableListOf<Int>()
    val frames = mutableListOf<WalkFrame>()
    var peak = 0

    fun label(m: Pair<Int, Int>) = "${m.first}–${m.second}"

    fun heapRow(active: Int? = null) = heap.sorted()
        .map { CellView(it.toString(), if (it == active) CellMark.ACTIVE else CellMark.DONE) }
        .ifEmpty { listOf(CellView("empty", CellMark.DIM)) }

    frames += WalkFrame(
        status = "Five meetings, one calendar: how many rooms run at once? The arrival order is fixed by sorting on " +
            "start time; the heap decides the one thing left to choose — which room is free.",
        cells = sorted.map { CellView(label(it), CellMark.IDLE) },
        aux = heapRow(),
        auxLabel = "min-heap of end times (rooms in use)",
        intervals = sorted.map { IntervalView(it.first, it.second, CellMark.IDLE) },
    )

    sorted.forEachIndexed { i, m ->
        val (start, end) = m
        val earliest = heap.minOrNull()
        var reused = false
        if (earliest != null && earliest <= start) {
            heap.remove(earliest)
            reused = true
        }
        heap += end
        peak = maxOf(peak, heap.size)
        frames += WalkFrame(
            status = "${label(m)} starts at $start. " + when {
                earliest == null -> "Nothing is running yet — open the first room, busy until $end."
                reused -> "The heap's root says a room frees at $earliest ≤ $start, so pop it and reuse that room. " +
                    "It is now busy until $end, and the room count did not grow."
                else -> "The earliest room is busy until $earliest, after $start — nothing has retired, so this " +
                    "meeting needs a room of its own."
            },
            cells = sorted.mapIndexed { j, v ->
                CellView(
                    label(v),
                    when {
                        j == i -> CellMark.ACTIVE
                        j < i -> CellMark.DONE
                        else -> CellMark.IDLE
                    },
                )
            },
            pointers = mapOf(i to "now"),
            aux = heapRow(active = end),
            auxLabel = "min-heap of end times (rooms in use)",
            intervals = sorted.take(i + 1).map {
                IntervalView(it.first, it.second, if (it == m) CellMark.ACTIVE else CellMark.WINDOW)
            },
            readout = "in use = ${heap.size} · peak = $peak",
        )
    }

    frames += WalkFrame(
        status = "The heap never held more than $peak entries, so $peak rooms cover the day. Heap size *is* the " +
            "concurrency — the same number a sweep line reports as its maximum overlap, reached from the other side.",
        cells = sorted.map { CellView(label(it), CellMark.RESULT) },
        aux = heapRow(),
        auxLabel = "min-heap of end times (rooms in use)",
        intervals = sorted.map { IntervalView(it.first, it.second, CellMark.RESULT) },
        readout = "rooms needed = $peak",
    )
    return frames
}

private fun lisTailsFrames(): List<WalkFrame> {
    val a = listOf(10, 9, 2, 5, 3, 7, 101, 18)
    val tails = mutableListOf<Int>()
    val frames = mutableListOf<WalkFrame>()

    fun tailsRow(active: Int? = null) = tails
        .mapIndexed { i, v -> CellView(v.toString(), if (i == active) CellMark.ACTIVE else CellMark.WINDOW) }
        .ifEmpty { listOf(CellView("empty", CellMark.DIM)) }

    frames += WalkFrame(
        status = "tails[k] will hold the smallest value any increasing subsequence of length k+1 can end on. It is " +
            "not the subsequence — it is the best possible ending for each length seen so far.",
        cells = a.map { CellView(it.toString(), CellMark.IDLE) },
        aux = tailsRow(),
        auxLabel = "tails[k] = smallest tail of a chain of length k+1",
    )

    a.forEachIndexed { i, x ->
        val found = tails.indexOfFirst { it >= x }
        val pos = if (found == -1) tails.size else found
        val appended = pos == tails.size
        val replaced = if (appended) null else tails[pos]
        if (appended) tails += x else tails[pos] = x
        frames += WalkFrame(
            status = if (appended) {
                "$x is larger than every tail, so no existing chain can absorb it — append. The longest chain is now " +
                    "${tails.size}."
            } else {
                "Binary search lands on tails[$pos] = $replaced, the first tail ≥ $x. Overwrite it: a length-${pos + 1} " +
                    "chain ending on $x leaves more room for what follows than one ending on $replaced. The length is unchanged."
            },
            cells = a.mapIndexed { j, v ->
                CellView(
                    v.toString(),
                    when {
                        j == i -> CellMark.ACTIVE
                        j < i -> CellMark.DIM
                        else -> CellMark.IDLE
                    },
                )
            },
            pointers = mapOf(i to "x"),
            aux = tailsRow(active = pos),
            auxLabel = "tails[k] = smallest tail of a chain of length k+1",
            readout = "LIS length so far = ${tails.size}",
        )
    }

    frames += WalkFrame(
        status = "tails = ${tails.joinToString(", ")} — length ${tails.size}, and that length is the answer. The row " +
            "itself is not a subsequence of the input: reconstructing one needs a parent index recorded per element.",
        cells = a.map { CellView(it.toString(), CellMark.DIM) },
        aux = tails.map { CellView(it.toString(), CellMark.RESULT) },
        auxLabel = "tails[k] = smallest tail of a chain of length k+1",
        readout = "LIS = ${tails.size} · one binary search per element, so O(n log n)",
    )
    return frames
}

private fun windowMaxDequeFrames(): List<WalkFrame> {
    val a = listOf(1, 3, -1, -3, 5, 3, 6, 7)
    val k = 3
    val dq = ArrayDeque<Int>()
    val out = mutableListOf<Int>()
    val frames = mutableListOf<WalkFrame>()

    fun dequeRow() = dq.map { CellView("a[$it]=${a[it]}", CellMark.WINDOW) }
        .ifEmpty { listOf(CellView("empty", CellMark.DIM)) }

    frames += WalkFrame(
        status = "Maximum of every window of width $k. The deque will hold indices whose values decrease front to " +
            "back, so its front is always the current window's maximum.",
        cells = a.map { CellView(it.toString(), CellMark.IDLE) },
        aux = dequeRow(),
        auxLabel = "deque of indices, values decreasing",
    )

    a.forEachIndexed { i, x ->
        val expired = dq.firstOrNull()?.takeIf { it <= i - k }
        if (expired != null) dq.removeFirst()
        val dominated = mutableListOf<Int>()
        while (dq.isNotEmpty() && a[dq.last()] <= x) dominated += dq.removeLast()
        dq.addLast(i)
        if (i >= k - 1) out += a[dq.first()]

        val windowStart = maxOf(0, i - k + 1)
        frames += WalkFrame(
            status = buildString {
                append("a[$i] = $x. ")
                if (expired != null) append("Index $expired has slid out of the window, so drop it from the front. ")
                if (dominated.isEmpty()) {
                    append("Nothing at the back is smaller, so $x just joins it.")
                } else {
                    append(
                        "It dominates ${dominated.joinToString(", ") { "a[$it]=${a[it]}" }} — older *and* smaller can " +
                            "never be the max again, so pop from the back before pushing.",
                    )
                }
                if (i >= k - 1) append(" Window [$windowStart..$i] max = ${a[dq.first()]}, read straight off the front.")
            },
            cells = a.mapIndexed { j, v ->
                CellView(
                    v.toString(),
                    when {
                        j == i -> CellMark.ACTIVE
                        i >= k - 1 && j == dq.first() -> CellMark.RESULT
                        j in windowStart..i -> CellMark.WINDOW
                        else -> CellMark.IDLE
                    },
                )
            },
            pointers = mapOf(i to "i"),
            aux = dequeRow(),
            auxLabel = "deque of indices, values decreasing",
            readout = if (out.isEmpty()) null else "maxima: ${out.joinToString(", ")}",
        )
    }

    frames += WalkFrame(
        status = "${out.size} window maxima in ${a.size} steps. Every index was pushed once and popped at most once, " +
            "so the whole scan is O(n) — a heap would be O(n log k) and would still need stale entries filtered out.",
        cells = out.map { CellView(it.toString(), CellMark.RESULT) },
        aux = dequeRow(),
        auxLabel = "deque of indices, values decreasing",
        readout = "window maxima = ${out.joinToString(", ")}",
    )
    return frames
}

private fun palindromeExpansionFrames(): List<WalkFrame> {
    val s = "abbanana"
    val frames = mutableListOf<WalkFrame>()

    // Inclusive span; an even centre that fails on its first comparison comes back empty (second < first).
    fun expand(lo: Int, hi: Int): Pair<Int, Int> {
        var l = lo
        var r = hi
        while (l >= 0 && r < s.length && s[l] == s[r]) {
            l--
            r++
        }
        return (l + 1) to (r - 1)
    }

    fun width(span: Pair<Int, Int>) = span.second - span.first + 1
    fun text(span: Pair<Int, Int>) = if (width(span) <= 0) "" else s.substring(span.first, span.second + 1)

    var best = 0 to 0
    var total = 0

    frames += WalkFrame(
        status = "\"$s\" has ${2 * s.length - 1} centres: ${s.length} characters and ${s.length - 1} gaps between " +
            "them. Every palindrome is symmetric about one of them, so enumerating centres finds all of them.",
        cells = s.map { CellView(it.toString(), CellMark.IDLE) },
    )

    s.indices.forEach { i ->
        val odd = expand(i, i)
        val even = expand(i, i + 1)
        total += (width(odd) + 1) / 2 + (width(even) + 1) / 2
        val local = if (width(even) > width(odd)) even else odd
        if (width(local) > width(best)) best = local

        frames += WalkFrame(
            status = "Centre $i: expanding around the character gives \"${text(odd)}\" (${width(odd)}). " +
                if (width(even) <= 0) {
                    "The gap between $i and ${i + 1} fails on its first comparison — skipping it is the classic bug, " +
                        "not skipping it costs nothing."
                } else {
                    "The gap between $i and ${i + 1} gives \"${text(even)}\" (${width(even)}) — the even case earns its keep here."
                },
            cells = s.mapIndexed { j, c ->
                CellView(
                    c.toString(),
                    when {
                        width(local) > 1 && j in local.first..local.second -> CellMark.WINDOW
                        j == i -> CellMark.ACTIVE
                        else -> CellMark.IDLE
                    },
                )
            },
            pointers = mapOf(i to "centre"),
            aux = (best.first..best.second).map { CellView(s[it].toString(), CellMark.RESULT) },
            auxLabel = "longest palindrome so far",
            readout = "best = \"${text(best)}\" (${width(best)}) · palindromic substrings so far = $total",
        )
    }

    frames += WalkFrame(
        status = "Longest is \"${text(best)}\", and the same sweep counted $total palindromic substrings — every " +
            "expansion step is one more of them. ${2 * s.length - 1} centres × O(n) growth = O(n²) time, O(1) space.",
        cells = s.mapIndexed { j, c ->
            CellView(c.toString(), if (j in best.first..best.second) CellMark.RESULT else CellMark.DIM)
        },
        readout = "longest = \"${text(best)}\" · count = $total",
    )
    return frames
}

private fun prefixFunctionFrames(): List<WalkFrame> {
    val s = "abacabab"
    val pi = IntArray(s.length)
    val frames = mutableListOf<WalkFrame>()

    fun piRow(active: Int? = null, filledUpTo: Int) = pi.mapIndexed { i, v ->
        when {
            i == active -> CellView(v.toString(), CellMark.ACTIVE)
            i <= filledUpTo -> CellView(v.toString(), CellMark.WINDOW)
            else -> CellView("·", CellMark.DIM)
        }
    }

    frames += WalkFrame(
        status = "pi[i] is the length of the longest proper prefix of \"$s\"[0..i] that is also a suffix of it — its " +
            "longest border. pi[0] is 0 by definition: a string cannot be its own proper prefix.",
        cells = s.map { CellView(it.toString(), CellMark.IDLE) },
        aux = piRow(filledUpTo = 0),
        auxLabel = "pi (longest border per prefix)",
    )

    var k = 0
    for (i in 1 until s.length) {
        val fallbacks = mutableListOf<Int>()
        while (k > 0 && s[i] != s[k]) {
            k = pi[k - 1]
            fallbacks += k
        }
        val matched = s[i] == s[k]
        if (matched) k++
        pi[i] = k
        frames += WalkFrame(
            status = buildString {
                append("s[$i] = '${s[i]}' against the border candidate s[${if (matched) k - 1 else k}] = '${s[if (matched) k - 1 else k]}'. ")
                if (fallbacks.isNotEmpty()) {
                    append(
                        "Mismatch, so fall back to pi of the border — ${fallbacks.joinToString(" → ")} — instead of " +
                            "restarting at 0. That fallback is why the whole build stays linear. ",
                    )
                }
                append(
                    if (matched) "Match: the border extends to length $k, so pi[$i] = $k."
                    else "No border survives here, so pi[$i] = 0.",
                )
            },
            cells = s.mapIndexed { j, c ->
                CellView(
                    c.toString(),
                    when {
                        j == i -> CellMark.ACTIVE
                        j < k -> CellMark.WINDOW
                        else -> CellMark.IDLE
                    },
                )
            },
            pointers = mapOf(i to "i") + if (k > 0) mapOf(k - 1 to "border") else emptyMap(),
            aux = piRow(active = i, filledUpTo = i - 1),
            auxLabel = "pi (longest border per prefix)",
            readout = "border length k = $k",
        )
    }

    val period = s.length - pi[s.lastIndex]
    frames += WalkFrame(
        status = "pi = ${pi.joinToString(", ")}. Candidate period = n − pi[n−1] = ${s.length} − ${pi[s.lastIndex]} = " +
            "$period, but $period does not divide ${s.length}, so \"$s\" is not a repeated block — the divisibility " +
            "check is the half of the rule people forget. Run the same table over pattern + '#' + text and every " +
            "pi[i] = m marks a full occurrence.",
        cells = s.mapIndexed { j, c ->
            CellView(c.toString(), if (j < pi[s.lastIndex]) CellMark.RESULT else CellMark.DIM)
        },
        aux = pi.map { CellView(it.toString(), CellMark.WINDOW) },
        auxLabel = "pi (longest border per prefix)",
        readout = "longest border = ${pi[s.lastIndex]} · period = $period (does not divide ${s.length})",
    )
    return frames
}

private fun fisherYatesFrames(): List<WalkFrame> {
    val a = mutableListOf("A", "B", "C", "D", "E", "F")
    val n = a.size
    // Fixed draws stand in for uniform(i, n-1). A seeded RNG would work too, but the frames have to be
    // identical every run — SimulationFrameTest and the playback scrubber both replay them.
    val draws = listOf(3, 1, 5, 4, 5)
    val frames = mutableListOf<WalkFrame>()

    fun rangeRow(i: Int) = List(n) { j ->
        if (j >= i) CellView(j.toString(), CellMark.WINDOW) else CellView("·", CellMark.DIM)
    }

    frames += WalkFrame(
        status = "Fisher-Yates shuffles in place. At step i the legal draw is any index in [i, ${n - 1}] — the " +
            "*unprocessed* suffix. Drawing from [0, ${n - 1}] instead gives nⁿ equally likely paths onto n! " +
            "permutations, which cannot divide evenly, so some orders come out more often than others.",
        cells = a.map { CellView(it, CellMark.IDLE) },
        aux = rangeRow(0),
        auxLabel = "legal draws for i — never [0, n)",
    )

    draws.forEachIndexed { i, j ->
        val moved = a[j]
        val displaced = a[i]
        a[i] = moved
        a[j] = displaced
        frames += WalkFrame(
            status = "i = $i draws j = $j from [$i, ${n - 1}] and swaps: $moved is now fixed at position $i" +
                if (i == j) " — drawing itself is a legal outcome, and forbidding it biases the result."
                else ", $displaced falls back into the suffix.",
            cells = a.mapIndexed { p, v ->
                CellView(
                    v,
                    when {
                        p == i -> CellMark.DONE
                        p == j -> CellMark.ACTIVE
                        p < i -> CellMark.DONE
                        else -> CellMark.IDLE
                    },
                )
            },
            pointers = mapOf(i to "i", j to "j"),
            aux = rangeRow(i),
            auxLabel = "legal draws for i — never [0, n)",
        )
    }

    frames += WalkFrame(
        status = "${a.joinToString("")} — one pass, one swap per position, every permutation equally likely. " +
            "Reservoir sampling is the streaming twin of the same idea: keep k items and replace one with " +
            "probability k/i as the i-th arrives, and each of the n items ends up kept with probability k/n.",
        cells = a.map { CellView(it, CellMark.RESULT) },
        aux = rangeRow(n),
        auxLabel = "legal draws for i — never [0, n)",
        readout = "shuffled = ${a.joinToString(" ")}",
    )
    return frames
}

private fun fenwickRangeQueryFrames(): List<WalkFrame> {
    val a = intArrayOf(3, 1, 4, 1, 5, 9, 2, 6)
    val n = a.size
    val tree = IntArray(n + 1)
    val frames = mutableListOf<WalkFrame>()

    fun add(index: Int, delta: Int): List<Int> {
        val path = mutableListOf<Int>()
        var i = index + 1
        while (i <= n) {
            tree[i] += delta
            path += i
            i += i and -i
        }
        return path
    }

    fun prefixPath(index: Int): List<Int> {
        val path = mutableListOf<Int>()
        var i = index + 1
        while (i > 0) {
            path += i
            i -= i and -i
        }
        return path
    }

    fun prefixSum(index: Int) = prefixPath(index).sumOf { tree[it] }

    fun treeRow(marked: Set<Int> = emptySet()) = (1..n).map {
        CellView(tree[it].toString(), if (it in marked) CellMark.ACTIVE else CellMark.WINDOW)
    }

    a.indices.forEach { add(it, a[it]) }

    val plainPrefix = IntArray(n)
    a.indices.forEach { plainPrefix[it] = a[it] + if (it == 0) 0 else plainPrefix[it - 1] }

    frames += WalkFrame(
        status = "Static prefix sums answer any range in O(1): sum[l..r] = P[r] − P[l−1]. The cost is hidden in the " +
            "other operation — a single write to a[2] invalidates every prefix from index 2 rightward.",
        cells = a.map { CellView(it.toString(), CellMark.IDLE) },
        aux = plainPrefix.map { CellView(it.toString(), CellMark.WINDOW) },
        auxLabel = "P (inclusive prefix sums)",
    )
    frames += WalkFrame(
        status = "a[2] += 3, and six of the eight prefixes have to be recomputed. One write costs O(n); a thousand " +
            "interleaved writes and queries cost O(n²). That is the moment prefix sums stop being the answer.",
        cells = a.mapIndexed { i, v -> CellView(v.toString(), if (i == 2) CellMark.ACTIVE else CellMark.IDLE) },
        pointers = mapOf(2 to "write"),
        aux = plainPrefix.mapIndexed { i, v ->
            CellView(v.toString(), if (i >= 2) CellMark.ACTIVE else CellMark.WINDOW)
        },
        auxLabel = "P (inclusive prefix sums) — everything from index 2 is now stale",
    )
    frames += WalkFrame(
        status = "The Fenwick tree stores block aggregates instead. tree[i] covers the (i & −i) elements ending at i: " +
            "tree[4] holds a[0..3], tree[6] holds a[4..5], tree[8] holds the whole array. No cell depends on more " +
            "than log n others, which is what makes writes cheap.",
        cells = a.map { CellView(it.toString(), CellMark.DIM) },
        aux = treeRow(),
        auxLabel = "tree[i] = aggregate of the block ending at i (1-indexed)",
    )

    val updatePath = add(2, 3)
    a[2] += 3
    frames += WalkFrame(
        status = "The same a[2] += 3 as a Fenwick update: start at index 3 (1-indexed) and jump with i += i & −i, " +
            "hitting ${updatePath.joinToString(" → ")}. Three cells touched instead of six, and the count is log n " +
            "no matter how long the array gets.",
        cells = a.mapIndexed { i, v -> CellView(v.toString(), if (i == 2) CellMark.ACTIVE else CellMark.IDLE) },
        pointers = mapOf(2 to "write"),
        aux = treeRow(updatePath.toSet()),
        auxLabel = "tree[i] = aggregate of the block ending at i (1-indexed)",
        readout = "update path = ${updatePath.joinToString(" → ")}",
    )

    val queryPath = prefixPath(5)
    frames += WalkFrame(
        status = "prefix(5) walks the other way — i −= i & −i — visiting ${queryPath.joinToString(" → ")} and adding " +
            "those blocks: ${queryPath.joinToString(" + ") { tree[it].toString() }} = ${prefixSum(5)}. Each step " +
            "strips one set bit, so the walk is as long as the index has bits.",
        cells = a.mapIndexed { i, v -> CellView(v.toString(), if (i <= 5) CellMark.WINDOW else CellMark.IDLE) },
        pointers = mapOf(5 to "r"),
        aux = treeRow(queryPath.toSet()),
        auxLabel = "tree[i] = aggregate of the block ending at i (1-indexed)",
        readout = "prefix(5) = ${prefixSum(5)}",
    )

    val rangeSum = prefixSum(5) - prefixSum(1)
    frames += WalkFrame(
        status = "sum[2..5] = prefix(5) − prefix(1) = ${prefixSum(5)} − ${prefixSum(1)} = $rangeSum. Both halves are " +
            "O(log n), and so was the update — the trade that prefix sums could not make. Sums with point updates " +
            "want a Fenwick; min, max or gcd, or range updates, want a segment tree.",
        cells = a.mapIndexed { i, v -> CellView(v.toString(), if (i in 2..5) CellMark.RESULT else CellMark.DIM) },
        pointers = mapOf(2 to "l", 5 to "r"),
        aux = treeRow((queryPath + prefixPath(1)).toSet()),
        auxLabel = "tree[i] = aggregate of the block ending at i (1-indexed)",
        readout = "sum[2..5] = $rangeSum",
    )
    return frames
}

private fun rollingHashPatternFrames(): List<WalkFrame> {
    val text = "abracadabra"
    val pattern = "abra"
    val base = 31L
    val mod = 1009L
    val m = pattern.length
    val frames = mutableListOf<WalkFrame>()

    fun code(c: Char) = (c - 'a' + 1).toLong()
    fun hashOf(s: String) = s.fold(0L) { acc, c -> (acc * base + code(c)) % mod }

    val high = (1 until m).fold(1L) { acc, _ -> acc * base % mod }
    val target = hashOf(pattern)
    var h = hashOf(text.substring(0, m))
    var comparisons = 0
    val hits = mutableListOf<Int>()

    fun patternRow() = pattern.map { CellView(it.toString(), CellMark.DONE) }

    fun textRow(start: Int, mark: CellMark) = text.mapIndexed { i, c ->
        CellView(c.toString(), if (i in start until start + m) mark else CellMark.IDLE)
    }

    frames += WalkFrame(
        status = "Base $base, modulus $mod, a = 1 … z = 26. The pattern hashes to $target once; the point is that " +
            "every window of the text can then be hashed in O(1) instead of O(m).",
        cells = text.map { CellView(it.toString(), CellMark.IDLE) },
        aux = patternRow(),
        auxLabel = "pattern \"$pattern\" · hash = $target",
    )

    for (start in 0..text.length - m) {
        if (start > 0) {
            val outgoing = text[start - 1]
            val incoming = text[start + m - 1]
            val before = h
            h = ((h - code(outgoing) * high % mod + mod * mod) % mod * base + code(incoming)) % mod
            frames += WalkFrame(
                status = "Slide to $start: drop '$outgoing' (weight b^${m - 1} = $high), shift left by one base, add " +
                    "'$incoming'. $before → $h, three operations regardless of how wide the window is.",
                cells = textRow(start, CellMark.WINDOW),
                pointers = mapOf(start to "l", start + m - 1 to "r"),
                aux = patternRow(),
                auxLabel = "pattern \"$pattern\" · hash = $target",
                readout = "h = $h · target = $target",
            )
        }
        if (h == target) {
            comparisons++
            val real = text.substring(start, start + m) == pattern
            if (real) hits += start
            frames += WalkFrame(
                status = "Hashes match at $start. That is a *candidate*, not a match — compare the ${m} characters " +
                    "directly: \"${text.substring(start, start + m)}\" " +
                    (if (real) "== \"$pattern\", a real occurrence." else "≠ \"$pattern\", a collision. Reporting it unverified is the bug."),
                cells = textRow(start, if (real) CellMark.RESULT else CellMark.ACTIVE),
                pointers = mapOf(start to "l", start + m - 1 to "r"),
                aux = patternRow(),
                auxLabel = "pattern \"$pattern\" · hash = $target",
                readout = "verified hits: ${hits.joinToString(", ").ifEmpty { "none yet" }}",
            )
        }
    }

    frames += WalkFrame(
        status = "Occurrences at ${hits.joinToString(", ")} after $comparisons character comparison(s) instead of " +
            "${text.length - m + 1}. O(n + m) expected; against an adversary who can see your base, randomise it or " +
            "hash under two moduli.",
        cells = text.mapIndexed { i, c ->
            CellView(c.toString(), if (hits.any { i in it until it + m }) CellMark.RESULT else CellMark.DIM)
        },
        aux = patternRow(),
        auxLabel = "pattern \"$pattern\" · hash = $target",
        readout = "hits = ${hits.joinToString(", ")}",
    )
    return frames
}

private fun runningBestFrames(): List<WalkFrame> {
    val a = listOf(-2, 1, -3, 4, -1, 2, 1, -5, 4)
    val curRow = IntArray(a.size)
    val frames = mutableListOf<WalkFrame>()

    fun curCells(upTo: Int, active: Int) = curRow.mapIndexed { i, v ->
        when {
            i == active -> CellView(v.toString(), CellMark.ACTIVE)
            i <= upTo -> CellView(v.toString(), CellMark.WINDOW)
            else -> CellView("·", CellMark.DIM)
        }
    }

    var cur = a[0]
    var best = a[0]
    var bestStart = 0
    var bestEnd = 0
    var start = 0
    curRow[0] = cur

    frames += WalkFrame(
        status = "Two scalars carry the whole scan: cur, the best run that *must* end at the current index, and " +
            "best, the best run seen anywhere. cur starts at a[0] = ${a[0]}.",
        cells = a.mapIndexed { i, v -> CellView(v.toString(), if (i == 0) CellMark.ACTIVE else CellMark.IDLE) },
        pointers = mapOf(0 to "i"),
        aux = curCells(-1, 0),
        auxLabel = "cur = best run ending here",
        readout = "cur = $cur · best = $best",
    )

    for (i in 1 until a.size) {
        val extend = cur + a[i]
        val restarted = a[i] > extend
        cur = maxOf(a[i], extend)
        if (restarted) start = i
        curRow[i] = cur
        val improved = cur > best
        if (improved) {
            best = cur
            bestStart = start
            bestEnd = i
        }
        frames += WalkFrame(
            status = "a[$i] = ${a[i]}: extending gives $extend, restarting gives ${a[i]}. " +
                (if (restarted) "The carried prefix has gone negative, so it can only hurt what follows — drop it and start fresh at $i. "
                else "Extending wins, so the run grows. ") +
                (if (improved) "cur = $cur beats the old best, so best moves up." else "best stays at $best — the optimum may have ended earlier."),
            cells = a.mapIndexed { j, v ->
                CellView(
                    v.toString(),
                    when {
                        j == i -> CellMark.ACTIVE
                        j in start..i -> CellMark.WINDOW
                        else -> CellMark.IDLE
                    },
                )
            },
            pointers = mapOf(i to "i", start to "run"),
            aux = curCells(i - 1, i),
            auxLabel = "cur = best run ending here",
            readout = "cur = $cur · best = $best",
        )
    }

    frames += WalkFrame(
        status = "best = $best, from a[$bestStart..$bestEnd]. One pass, two scalars, no table — and the run that won " +
            "ended before the array did, which is exactly why best is tracked separately from cur.",
        cells = a.mapIndexed { j, v ->
            CellView(v.toString(), if (j in bestStart..bestEnd) CellMark.RESULT else CellMark.DIM)
        },
        aux = curRow.map { CellView(it.toString(), CellMark.WINDOW) },
        auxLabel = "cur = best run ending here",
        readout = "max subarray sum = $best",
    )

    val p = listOf(-2, 3, -4)
    var curMax = p[0]
    var curMin = p[0]
    var bestProduct = p[0]
    val maxRow = IntArray(p.size)
    val minRow = IntArray(p.size)
    maxRow[0] = curMax
    minRow[0] = curMin
    var minBeforeLast = curMin
    for (i in 1 until p.size) {
        if (i == p.lastIndex) minBeforeLast = curMin
        val candidates = listOf(p[i], curMax * p[i], curMin * p[i])
        curMax = candidates.maxOrNull()!!
        curMin = candidates.minOrNull()!!
        maxRow[i] = curMax
        minRow[i] = curMin
        bestProduct = maxOf(bestProduct, curMax)
    }
    frames += WalkFrame(
        status = "The product variant needs one more scalar. On ${p.joinToString(", ")} the running *minimum* reaches " +
            "$minBeforeLast, and $minBeforeLast × ${p.last()} = $bestProduct is the answer — it comes out of the " +
            "minimum, not the maximum, because a negative flips the two. Track both or the negatives beat you.",
        cells = p.map { CellView(it.toString(), CellMark.RESULT) },
        aux = maxRow.map { CellView(it.toString(), CellMark.WINDOW) },
        auxLabel = "cur_max ending here (cur_min: ${minRow.joinToString(", ")})",
        readout = "max product = $bestProduct",
    )
    return frames
}

private fun sweepLineFrames(): List<WalkFrame> {
    val intervals = listOf(1 to 5, 2 to 7, 4 to 6, 8 to 10, 9 to 12)
    // Ties: an interval ending at x frees the point before one starting at x claims it, so −1 sorts first.
    val events = intervals.flatMap { listOf(it.first to 1, it.second to -1) }
        .sortedWith(compareBy({ it.first }, { it.second }))
    val frames = mutableListOf<WalkFrame>()

    fun eventLabel(e: Pair<Int, Int>) = "${e.first}${if (e.second > 0) "+" else "−"}"

    frames += WalkFrame(
        status = "${intervals.size} intervals become ${events.size} endpoints and nothing else: +1 where one opens, " +
            "−1 where one closes. The intervals themselves are never looked at again.",
        cells = events.map { CellView(eventLabel(it), CellMark.IDLE) },
        intervals = intervals.map { IntervalView(it.first, it.second, CellMark.IDLE) },
    )

    var active = 0
    var peak = 0
    var peakAt = events.first().first
    val activeRow = IntArray(events.size)

    events.forEachIndexed { i, e ->
        active += e.second
        activeRow[i] = active
        if (active > peak) {
            peak = active
            peakAt = e.first
        }
        frames += WalkFrame(
            status = "x = ${e.first}: " + (if (e.second > 0) "an interval opens" else "an interval closes") +
                ", so active ${if (e.second > 0) "+" else "−"} 1 = $active." +
                if (active == peak && e.second > 0) " That is a new maximum overlap." else "",
            cells = events.mapIndexed { j, v ->
                CellView(
                    eventLabel(v),
                    when {
                        j == i -> CellMark.ACTIVE
                        j < i -> CellMark.DONE
                        else -> CellMark.IDLE
                    },
                )
            },
            pointers = mapOf(i to "sweep"),
            aux = activeRow.mapIndexed { j, v ->
                if (j <= i) CellView(v.toString(), if (v == peak) CellMark.RESULT else CellMark.WINDOW)
                else CellView("·", CellMark.DIM)
            },
            auxLabel = "active count after each event",
            intervals = intervals.map {
                IntervalView(it.first, it.second, if (e.first in it.first until it.second) CellMark.WINDOW else CellMark.IDLE)
            },
            readout = "active = $active · peak = $peak at x = $peakAt",
        )
    }

    frames += WalkFrame(
        status = "Maximum overlap $peak, first reached at x = $peakAt — the sort dominates at O(n log n), the sweep " +
            "itself is one pass. A difference array is the same trick on a fixed index range: d[l] += v, d[r+1] −= v " +
            "per update, then one prefix sum materialises every value.",
        cells = events.map { CellView(eventLabel(it), CellMark.DIM) },
        aux = activeRow.map { CellView(it.toString(), if (it == peak) CellMark.RESULT else CellMark.WINDOW) },
        auxLabel = "active count after each event",
        intervals = intervals.map {
            IntervalView(it.first, it.second, if (peakAt in it.first until it.second) CellMark.RESULT else CellMark.DIM)
        },
        readout = "max overlap = $peak at x = $peakAt",
    )
    return frames
}

private fun twoHeapsFrames(): List<WalkFrame> {
    val stream = listOf(5, 15, 1, 3, 8, 7, 9, 10)
    val lo = mutableListOf<Int>()   // smaller half, max-heap: root is the largest
    val hi = mutableListOf<Int>()   // larger half, min-heap: root is the smallest
    val frames = mutableListOf<WalkFrame>()

    fun median(): Double =
        if (lo.size > hi.size) lo.max().toDouble() else (lo.max() + hi.min()) / 2.0

    fun show(v: Double) = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    fun heapRow() = lo.sortedDescending().map { CellView(it.toString(), CellMark.WINDOW) } +
        hi.sorted().map { CellView(it.toString(), CellMark.DONE) }

    frames += WalkFrame(
        status = "A running median over a stream. lo is a max-heap of the smaller half, hi a min-heap of the larger " +
            "half; every value in lo is ≤ every value in hi, so the middle of the data is always a root away.",
        cells = stream.map { CellView(it.toString(), CellMark.IDLE) },
        aux = listOf(CellView("empty", CellMark.DIM)),
        auxLabel = "lo (larger-first) | hi (smaller-first)",
    )

    stream.forEachIndexed { i, x ->
        lo += x
        val promoted = lo.max()
        lo.remove(promoted)
        hi += promoted
        var demoted: Int? = null
        if (hi.size > lo.size) {
            demoted = hi.min()
            hi.remove(demoted)
            lo += demoted
        }
        frames += WalkFrame(
            status = "Insert $x: push it into lo, then move lo's largest ($promoted) into hi — that single hand-off " +
                "is what keeps every lo value below every hi value. " +
                (if (demoted != null) "hi is now the bigger half, so its smallest ($demoted) comes back to lo."
                else "The sizes are already legal, so nothing comes back.") +
                " Median = ${show(median())}" +
                if (lo.size > hi.size) ", read straight off lo's root." else ", the mean of the two roots.",
            cells = stream.mapIndexed { j, v ->
                CellView(
                    v.toString(),
                    when {
                        j == i -> CellMark.ACTIVE
                        j < i -> CellMark.DONE
                        else -> CellMark.IDLE
                    },
                )
            },
            pointers = mapOf(i to "x"),
            aux = heapRow(),
            auxLabel = "lo (larger-first) | hi (smaller-first) · |lo| = ${lo.size}, |hi| = ${hi.size}",
            readout = "median = ${show(median())}",
        )
    }

    frames += WalkFrame(
        status = "${stream.size} inserts, each O(log n), and every median was O(1) — both candidates were always " +
            "roots. Re-sorting after each insert would have been O(n² log n) for the same answers.",
        cells = stream.map { CellView(it.toString(), CellMark.DIM) },
        aux = heapRow(),
        auxLabel = "lo (larger-first) | hi (smaller-first)",
        readout = "final median = ${show(median())}",
    )
    return frames
}

// ── D6 · Flash Attention ─────────────────────────────────────────────────────
// The tiled softmax drawn as what it is: a left-to-right scan over key blocks, carrying three
// numbers. The cells are the blocks, the aux row is the running denominator, and the rescale that
// happens when a block raises the running maximum is a visible change to cells already passed.
// Numbers come from FlashLab, which runs both algorithms on the same 512 scores.

private fun flashAttentionFrames(): List<WalkFrame> {
    val frames = mutableListOf<WalkFrame>()
    val blockSize = 64
    val tiles = FlashLab.tileTrace(FlashLab.demoScores, blockSize)
    val blocks = tiles.size

    fun blockCells(upTo: Int, active: Int?) = (0 until blocks).map { i ->
        CellView(
            "b$i",
            when {
                i == active -> CellMark.ACTIVE
                i < upTo -> CellMark.DONE
                else -> CellMark.DIM
            },
        )
    }

    fun sumRow(upTo: Int) = (0 until blocks).map { i ->
        if (i <= upTo) CellView("%.0f".format(tiles[i].runningSum), CellMark.WINDOW) else CellView("·", CellMark.DIM)
    }

    frames += WalkFrame(
        status = "${FlashLab.demoScores.size} keys in $blocks blocks of $blockSize. The textbook softmax needs every " +
            "score at once, because the denominator is a sum over all of them. This scan never holds more than one " +
            "block — it carries a running maximum m, a running denominator ℓ, and a running output.",
        cells = blockCells(0, null),
        aux = sumRow(-1),
        auxLabel = "ℓ (running denominator)",
    )

    tiles.forEach { tile ->
        val raised = tile.rescale < 1.0
        frames += WalkFrame(
            status = if (raised) {
                "Block ${tile.index} contains a score of ${"%.2f".format(tile.blockMax)}, above the running maximum. " +
                    "Everything accumulated so far was scaled against the old maximum, so it is corrected by " +
                    "×${"%.4f".format(tile.rescale)} before this block is added. m becomes ${"%.2f".format(tile.runningMax)}."
            } else {
                "Block ${tile.index} peaks at ${"%.2f".format(tile.blockMax)}, below the running maximum of " +
                    "${"%.2f".format(tile.runningMax)}. Nothing is rescaled — the block is added and the scan moves on."
            },
            cells = blockCells(tile.index, tile.index),
            pointers = mapOf(tile.index to "m=${"%.1f".format(tile.runningMax)}"),
            aux = sumRow(tile.index),
            auxLabel = "ℓ (running denominator)",
            readout = if (raised) "rescale ×${"%.4f".format(tile.rescale)}" else null,
        )
    }

    frames += WalkFrame(
        status = "The scan's output against the textbook softmax on the same scores: they differ by " +
            "${"%.1e".format(FlashLab.maxDifference(blockSize))}, which is floating-point noise. This is the part " +
            "worth being clear about — Flash Attention is not an approximation. It computes the same function, and " +
            "the block size changes nothing about the answer.",
        cells = blockCells(blocks, null),
        aux = sumRow(blocks - 1),
        auxLabel = "ℓ (final denominator)",
        readout = "max difference ${"%.1e".format(FlashLab.maxDifference(blockSize))}",
    )

    frames += WalkFrame(
        status = "The running maximum is not an optimisation, it is what makes the sum finite. Without it every term " +
            "is exp(score), which overflows a float64 above ${"%.1f".format(FlashLab.overflowScore)} — and attention " +
            "logits routinely reach that in long-context models. A peak score of 500 survives; 710 returns NaN.",
        cells = listOf(100.0, 300.0, 500.0, 700.0, 710.0, 800.0).map {
            CellView("%.0f".format(it), if (FlashLab.overflows(it)) CellMark.ACTIVE else CellMark.DONE)
        },
        auxLabel = "peak score",
        readout = "overflow above ${"%.2f".format(FlashLab.overflowScore)}",
    )

    val lengths = listOf(1_024L, 4_096L, 16_384L, 65_536L)
    frames += WalkFrame(
        status = "What it buys is memory. The N×N score matrix is never written: at N = 65,536 over 32 heads that " +
            "matrix alone is ${bytesToGb(FlashLab.scoreMatrixBytes(65_536))} GB of activations, and the tiled kernel " +
            "allocates none of it. This is the saving that scales — it is the whole reason 128k-token training fits.",
        cells = lengths.map { CellView("${it / 1024}k", CellMark.WINDOW) },
        aux = lengths.map { CellView("${bytesToGb(FlashLab.scoreMatrixBytes(it))}G", CellMark.RESULT) },
        auxLabel = "score matrix, 32 heads",
        readout = "never allocated",
    )

    val tileSizes = listOf(64L, 128L, 256L)
    frames += WalkFrame(
        status = "The traffic saving is smaller than the folklore, and it has no N in it. Standard attention moves " +
            "about 4N² elements; the tiled kernel re-reads K and V once per query block, which is 2N²·d/Br. The ratio " +
            "is exactly 2·Br/d — a property of the tile and the head dimension, not of the sequence. At Br = 128 and " +
            "d = 64 that is ${"%.0f".format(FlashLab.asymptoticTrafficRatio())}×, and it is ${"%.2f".format(FlashLab.trafficRatio(65_536))}× " +
            "measured at N = 65,536.",
        cells = tileSizes.map { CellView("Br=$it", CellMark.WINDOW) },
        aux = tileSizes.map { CellView("${"%.0f".format(FlashLab.asymptoticTrafficRatio(64, it))}×", CellMark.RESULT) },
        auxLabel = "traffic ratio at d = 64",
        readout = "exactly 2·Br/d",
    )

    frames += WalkFrame(
        status = "And it costs arithmetic. The backward pass has no stored score matrix to read, so it recomputes " +
            "QKᵀ — ${"%.1f".format((FlashLab.flopOverhead(4_096) - 1) * 100)}% more FLOPs across forward and backward. " +
            "It is faster anyway, which is the lesson: on this hardware the arithmetic is nearly free and the memory " +
            "traffic is not.",
        cells = listOf(
            CellView("fwd", CellMark.DONE),
            CellView("bwd", CellMark.DONE),
            CellView("+recompute", CellMark.ACTIVE),
        ),
        readout = "${"%.3f".format(FlashLab.flopOverhead(4_096))}× the FLOPs, and still faster",
    )
    return frames
}

// ── D6 · State Space Models ──────────────────────────────────────────────────
// One diagonal LTI system, walked as the recurrence and then rebuilt as a convolution. The equality
// between the two is the point, so the lab runs both on the same input and reports the gap.

private fun ssmFrames(): List<WalkFrame> {
    val frames = mutableListOf<WalkFrame>()
    val steps = 8
    val input = DoubleArray(steps) { SsmLab.demoInput[it] }
    val trace = SsmLab.stateTrace(input)
    val output = SsmLab.recurrent(input)

    fun inputRow(active: Int?) = (0 until steps).map { i ->
        CellView(
            "%.1f".format(input[i]),
            when {
                i == active -> CellMark.ACTIVE
                active != null && i < active -> CellMark.DONE
                else -> CellMark.IDLE
            },
        )
    }

    frames += WalkFrame(
        status = "A state space model is a linear recurrence: h ← Ā·h + B̄·x, y = C·h. Ā is diagonal here, so the " +
            "state is ${SsmLab.stateDim} independent channels with decay rates " +
            "${SsmLab.aBar.joinToString(", ") { "%.3f".format(it) }} — deliberately spread over decades, so one state " +
            "carries several timescales at once.",
        cells = inputRow(null),
        aux = (0 until steps).map { CellView("·", CellMark.DIM) },
        auxLabel = "y (output)",
    )

    for (t in 0 until steps) {
        frames += WalkFrame(
            status = "Step $t: every channel decays by its own Ā and takes in B̄·x. State is now " +
                "(${trace[t].joinToString(", ") { "%.2f".format(it) }}); the read-out C·h gives " +
                "y = ${"%.3f".format(output[t])}. One multiply-add per channel per token — the whole cost of decoding.",
            cells = inputRow(t),
            pointers = mapOf(t to "h"),
            aux = (0 until steps).map { i ->
                if (i <= t) CellView("%.2f".format(output[i]), CellMark.WINDOW) else CellView("·", CellMark.DIM)
            },
            auxLabel = "y (output)",
        )
    }

    val kernel = SsmLab.kernel(steps)
    frames += WalkFrame(
        status = "Now the other form. Because the system is linear and time-invariant, its whole behaviour is one " +
            "impulse response: K[t] = C·Āᵗ·B̄. Convolving the input with K has to produce the same outputs the " +
            "recurrence just produced — not approximately, identically.",
        cells = kernel.map { CellView("%.2f".format(it), CellMark.WINDOW) },
        auxLabel = "K (convolution kernel)",
        aux = (0 until steps).map { CellView("K$it", CellMark.DIM) },
    )

    val conv = SsmLab.convolutional(input)
    frames += WalkFrame(
        status = "Run over the full ${SsmLab.demoInput.size}-token input, the two forms differ by " +
            "${"%.1e".format(SsmLab.formEquivalenceGap())}. That is the family's entire structural argument: train " +
            "with the convolution, which is parallel over the sequence, then decode with the recurrence, which is " +
            "O(1) memory per token. Attention has no second form to switch into.",
        cells = (0 until steps).map { CellView("%.2f".format(output[it]), CellMark.DONE) },
        aux = (0 until steps).map { CellView("%.2f".format(conv[it]), CellMark.RESULT) },
        auxLabel = "convolution output",
        readout = "gap ${"%.1e".format(SsmLab.formEquivalenceGap())}",
    )

    frames += WalkFrame(
        status = "What the state remembers is set by the decay rates, and it is a half-life. Channel 0 keeps half of " +
            "a token's contribution ${"%.0f".format(SsmLab.halfLife(0))} tokens later; channel 3 keeps half of it for " +
            "${"%.1f".format(SsmLab.halfLife(3))}. Together the impulse response is still above 1% of its peak at " +
            "token ${SsmLab.effectiveHorizon()} — a real memory, and a fixed one.",
        cells = (0 until SsmLab.stateDim).map { CellView("ch$it", CellMark.WINDOW) },
        aux = (0 until SsmLab.stateDim).map { CellView("%.0f".format(SsmLab.halfLife(it)), CellMark.RESULT) },
        auxLabel = "half-life, in tokens",
        readout = "effective horizon ${SsmLab.effectiveHorizon()} tokens",
    )

    val costs = SsmLab.costs()
    frames += WalkFrame(
        status = "And the cost is linear. At 1M tokens attention does " +
            "${"%.0f".format(costs.last().attentionOps.toDouble() / costs.last().ssmRecurrentOps)}× the arithmetic " +
            "this recurrence does. The scan is also associative — (a₂,b₂)∘(a₁,b₁) = (a₂a₁, a₂b₁+b₂) — so training " +
            "parallelises to depth ${costs.last().ssmScanDepth} instead of 1,048,576 sequential steps.",
        cells = costs.map { CellView("${it.length / 1024}k", CellMark.WINDOW) },
        aux = costs.map { CellView("${"%.0f".format(it.attentionOps.toDouble() / it.ssmRecurrentOps)}×", CellMark.RESULT) },
        auxLabel = "attention ops ÷ SSM ops",
        readout = "scan depth ${costs.last().ssmScanDepth} at 1M tokens",
    )
    return frames
}

// ── D6 · Mamba ───────────────────────────────────────────────────────────────
// The selective-copying task on three one-channel systems. The aux row is the state, which is where
// the whole argument is visible: two of the three lose the signal, for opposite reasons.

private fun mambaFrames(): List<WalkFrame> {
    val frames = mutableListOf<WalkFrame>()
    val fillers = 7
    val seq = MambaLab.sequence(fillers)

    fun tokenRow(active: Int?) = seq.indices.map { i ->
        CellView(
            if (i == 0) "SIG" else "·",
            when {
                i == active -> CellMark.ACTIVE
                i == 0 -> CellMark.RESULT
                else -> CellMark.IDLE
            },
        )
    }

    frames += WalkFrame(
        status = "The selective-copying task. One token worth remembering (SIG, value ${MambaLab.signalValue}) arrives " +
            "first, then $fillers filler tokens carrying ${MambaLab.fillerValue} each. At the end the model is asked " +
            "for the signal. A one-channel recurrence h ← a·h + b·x has to hold it through the fillers.",
        cells = tokenRow(0),
        aux = seq.map { CellView("%.2f".format(it), CellMark.DIM) },
        auxLabel = "token value",
    )

    MambaLab.arms.forEach { arm ->
        val trace = arm.trace(seq)
        frames += WalkFrame(
            status = when (arm.short) {
                "decaying" -> "Arm one: a fixed decay of 0.90. It can forget the fillers, which is what you want — but " +
                    "it forgets on a timer, so it forgets the signal at the same rate. After $fillers fillers the state " +
                    "is ${"%.3f".format(trace.last())}, and only ${"%.1e".format(MambaLab.signalContribution(arm, fillers))} " +
                    "of that came from SIG."
                "lossless" -> "Arm two: no decay at all, a = 1.00. Now nothing is forgotten — including every filler. " +
                    "The state reaches ${"%.1f".format(trace.last())}, of which the signal is a fixed " +
                    "${"%.1f".format(MambaLab.signalContribution(arm, fillers))} and the rest is noise it had no way " +
                    "to refuse."
                else -> "Arm three: Δ depends on the token. SIG gets Δ = 4, so a = e⁻⁴ and b = 1 − a — the state is " +
                    "overwritten with it. Filler gets Δ = 0, so a = 1 and b = 0 — the state is held exactly and " +
                    "nothing is written. The state stays at ${"%.4f".format(trace.last())} for as long as you like."
            },
            cells = tokenRow(null),
            aux = trace.map { CellView("%.2f".format(it), if (arm.short == "selective") CellMark.RESULT else CellMark.WINDOW) },
            auxLabel = "h (state) — ${arm.name}",
            readout = "signal contribution ${"%.3f".format(MambaLab.signalContribution(arm, fillers))}",
        )
    }

    frames += WalkFrame(
        status = "Stretched out, the two time-invariant arms fail in opposite directions and the gap is not close. " +
            "The decaying system's signal contribution falls from ${"%.3f".format(MambaLab.signalContribution(MambaLab.arms[0], 0))} " +
            "to ${"%.1e".format(MambaLab.signalContribution(MambaLab.arms[0], 100))} over 100 fillers — a factor of " +
            "${"%.0f".format(MambaLab.signalContribution(MambaLab.arms[0], 0) / MambaLab.signalContribution(MambaLab.arms[0], 100))}. " +
            "The selective system holds ${"%.4f".format(MambaLab.signalContribution(MambaLab.arms[2], 100))} at every " +
            "distance, because holding costs it nothing.",
        cells = MambaLab.fillerCounts.map { CellView("$it", CellMark.WINDOW) },
        aux = MambaLab.fillerCounts.map {
            CellView("%.0e".format(MambaLab.signalContribution(MambaLab.arms[0], it)), CellMark.ACTIVE)
        },
        auxLabel = "decaying arm: signal contribution by filler count",
        readout = "selective holds ${"%.4f".format(MambaLab.signalContribution(MambaLab.arms[2], 100))} throughout",
    )

    frames += WalkFrame(
        status = "Selectivity is not free: it costs the convolution. An LTI system is one fixed kernel, which is why " +
            "SSMs can train in parallel. A selective one has a different kernel at every position — the best single " +
            "fixed kernel fitted to this system's own outputs still leaves a residual of " +
            "${"%.3f".format(MambaLab.bestFixedKernelResidual())}. That is why Mamba needs a hardware-aware parallel " +
            "scan instead: the scan survives input-dependence, the FFT convolution does not.",
        cells = listOf(
            CellView("LTI", CellMark.DONE),
            CellView("conv ✓", CellMark.DONE),
            CellView("selective", CellMark.ACTIVE),
            CellView("conv ✗", CellMark.ACTIVE),
            CellView("scan ✓", CellMark.RESULT),
        ),
        readout = "fixed-kernel residual ${"%.3f".format(MambaLab.bestFixedKernelResidual())}",
    )

    frames += WalkFrame(
        status = "What it buys at inference is a state that does not grow. Mamba carries " +
            "${MambaLab.mambaStateBytes() / 1024} KB per layer whatever the sequence length; one transformer layer's " +
            "KV cache at 1M tokens is ${MambaLab.transformerCacheBytes(1_048_576) / (1024 * 1024)} MB and climbing " +
            "linearly. That ratio — ${"%.0f".format(MambaLab.transformerCacheBytes(1_048_576).toDouble() / MambaLab.mambaStateBytes())}× " +
            "— is the argument for the whole family.",
        cells = listOf(1_024L, 32_768L, 1_048_576L).map { CellView("${it / 1024}k", CellMark.WINDOW) },
        aux = listOf(1_024L, 32_768L, 1_048_576L).map {
            CellView("${"%.0f".format(MambaLab.transformerCacheBytes(it).toDouble() / MambaLab.mambaStateBytes())}×", CellMark.RESULT)
        },
        auxLabel = "KV cache ÷ Mamba state, one layer",
        readout = "Mamba's state is constant in the sequence length",
    )
    return frames
}

// ── D6 · RWKV ────────────────────────────────────────────────────────────────
// The WKV operator as a decaying weighted average, then the retrieval experiment that prices what
// "linear attention" gives up. The needle numbers come from RwkvLab, which scores the same needle
// under WKV and under a softmax attention whose query is aimed straight at it.

private fun rwkvFrames(): List<WalkFrame> {
    val frames = mutableListOf<WalkFrame>()
    val span = 8
    val keys = DoubleArray(span) { RwkvLab.demoKeys[it] }
    val values = DoubleArray(span) { RwkvLab.demoValues[it] }

    fun keyRow(active: Int?) = (0 until span).map { i ->
        CellView(
            "%.1f".format(keys[i]),
            when {
                i == active -> CellMark.ACTIVE
                active != null && i < active -> CellMark.DONE
                else -> CellMark.IDLE
            },
        )
    }

    frames += WalkFrame(
        status = "RWKV replaces attention with a weighted average that has no query in it. A token's weight is " +
            "exp(k) — how much it asked to be remembered — times exp(−w·distance), a decay the model learns once per " +
            "channel. There is no per-read choice anywhere: the weights are fixed before the reader exists.",
        cells = keyRow(null),
        aux = (0 until span).map { CellView("%.1f".format(values[it]), CellMark.DIM) },
        auxLabel = "v (values)",
    )

    for (t in 1 until span) {
        frames += WalkFrame(
            status = "At position $t the operator is a ratio of two running sums — numerator Σ e^{k−w·d}·v and " +
                "denominator Σ e^{k−w·d} — plus a bonus u = ${"%.1f".format(RwkvLab.bonus)} on the current token, " +
                "so the present is not drowned by the past. The read-out is ${"%.3f".format(RwkvLab.wkvStable(keys, values, t))}.",
            cells = keyRow(t),
            pointers = mapOf(t to "t"),
            aux = (0 until span).map { i ->
                if (i <= t) CellView("%.2f".format(RwkvLab.wkvStable(keys, values, i)), CellMark.WINDOW)
                else CellView("·", CellMark.DIM)
            },
            auxLabel = "wkv output",
        )
    }

    frames += WalkFrame(
        status = "Both sums are carried in one state and rescaled by a running maximum, exactly the trick Flash " +
            "Attention uses. Written the textbook way it agrees to ${"%.0e".format(RwkvLab.stabilityGap())} — and it " +
            "returns NaN outright once a key reaches 720, because e^720 does not fit in a float64. The decay also has " +
            "to be applied before the new token is folded in, not after; the two orders differ by 5e-02.",
        cells = listOf(10.0, 100.0, 500.0, 700.0, 720.0, 800.0).map {
            CellView("%.0f".format(it), if (RwkvLab.naiveOverflowsAt(it)) CellMark.ACTIVE else CellMark.DONE)
        },
        auxLabel = "key magnitude",
        readout = "stable form agrees to ${"%.0e".format(RwkvLab.stabilityGap())}",
    )

    frames += WalkFrame(
        status = "What it gives up is retrieval at distance. Plant a needle with a strong key and ask how much of the " +
            "read-out it accounts for. Softmax attention can aim its query at that key and holds " +
            "${"%.0f".format(RwkvLab.needle(100).attentionShare * 100)}% of the weight even 100 tokens back. RWKV's " +
            "weight was decided when the needle was written, so it falls with distance whatever the reader wants: " +
            "${"%.0f".format(RwkvLab.needle(5).rwkvShare * 100)}% at 5 tokens, " +
            "${"%.1f".format(RwkvLab.needle(50).rwkvShare * 100)}% at 50, " +
            "${"%.2f".format(RwkvLab.needle(100).rwkvShare * 100)}% at 100.",
        cells = RwkvLab.needleDistances.map { CellView("$it", CellMark.WINDOW) },
        aux = RwkvLab.needleDistances.map { CellView("%.0e".format(RwkvLab.needle(it).rwkvShare), CellMark.ACTIVE) },
        auxLabel = "needle's share of the RWKV read-out",
        readout = "attention holds ${"%.0f".format(RwkvLab.needle(500).attentionShare * 100)}% at 500 tokens",
    )

    frames += WalkFrame(
        status = "And what it buys is the other side of that trade. RWKV's state is (a, b, p) per channel — " +
            "${RwkvLab.stateBytes() / 1024} KB, fixed — while a transformer's KV cache at 1M tokens is " +
            "${bytesToGb(RwkvLab.cacheBytes(1_048_576))} GB. Constant memory per token and no quadratic prefill, " +
            "bought by giving up the query. Which side of that trade is right is a question about the workload.",
        cells = listOf(1_024L, 32_768L, 1_048_576L).map { CellView("${it / 1024}k", CellMark.WINDOW) },
        aux = listOf(1_024L, 32_768L, 1_048_576L).map { CellView("${bytesToGb(RwkvLab.cacheBytes(it))}G", CellMark.RESULT) },
        auxLabel = "transformer KV cache (RWKV: ${RwkvLab.stateBytes() / 1024} KB at every length)",
        readout = "${"%.0f".format(RwkvLab.cacheBytes(1_048_576).toDouble() / RwkvLab.stateBytes())}× at 1M tokens",
    )
    return frames
}

private val walkConfigs = mapOf(
    "flash_attention" to WalkConfig(
        intro = "The tiled softmax as a scan over key blocks, carrying a running maximum and denominator. It computes " +
            "the same function as the textbook version — the last frames price what that buys and what it costs.",
        legend = listOf(
            ActiveFill to "Current block",
            DoneFill to "Folded in",
            ResultFill to "Result",
        ),
        build = ::flashAttentionFrames,
    ),
    "ssm" to WalkConfig(
        intro = "One diagonal linear recurrence, walked token by token and then rebuilt as a convolution kernel. The " +
            "two forms are the same function, and the lab reports the gap rather than asserting it.",
        legend = listOf(
            ActiveFill to "Current token",
            WindowFill to "Computed",
            ResultFill to "Convolution",
        ),
        build = ::ssmFrames,
    ),
    "mamba" to WalkConfig(
        intro = "The selective-copying task on three one-channel systems. The aux row is the state, which is where " +
            "the argument lives: a fixed decay forgets the signal, no decay cannot ignore the filler.",
        legend = listOf(
            ActiveFill to "Failing arm",
            WindowFill to "State",
            ResultFill to "Selective",
        ),
        build = ::mambaFrames,
    ),
    "rwkv" to WalkConfig(
        intro = "A weighted average with no query in it — a learned decay and a per-token key. The closing frames " +
            "price both sides of that trade: a fixed-size state, and a needle it cannot reach for.",
        legend = listOf(
            ActiveFill to "Current token",
            WindowFill to "Read-out",
            ResultFill to "Cost",
        ),
        build = ::rwkvFrames,
    ),
    "label_encoding" to WalkConfig(
        intro = "A colour column encoded two ways, then scored: 40x the error in a linear model, and exactly nothing in a tree.",
        legend = listOf(
            ActiveFill to "Cost",
            DoneFill to "Free",
            ResultFill to "Filled",
        ),
        build = ::labelEncodingFrames,
    ),
    "missing_value_imputation" to WalkConfig(
        intro = "30% of a column missing. What a constant fill does to its variance and to every correlation it was in, measured against the complete data.",
        legend = listOf(
            ActiveFill to "Missing / changed",
            DoneFill to "Original",
            ResultFill to "Imputed",
        ),
        build = ::imputationFrames,
    ),
    "outlier_detection" to WalkConfig(
        intro = "The z-score rule against the IQR rule, including the sample where the z-score rule flags nothing at all and the sample sizes where it cannot fire.",
        legend = listOf(
            ResultFill to "Flagged",
            ActiveFill to "Missed",
            DoneFill to "Robust",
        ),
        build = ::outlierFrames,
    ),
    "chi_square_selection" to WalkConfig(
        intro = "Contingency tables and chi-square scores over five features — including XOR-labelled data, where it ranks the noise above the signal.",
        legend = listOf(
            DoneFill to "Selected",
            ActiveFill to "Blind spot",
            WindowFill to "Counts",
        ),
        build = ::chiSquareFrames,
    ),
    "rfe" to WalkConfig(
        intro = "Fit, drop the weakest coefficient, refit. Four rounds on five features, then the same run on data its model cannot express.",
        legend = listOf(
            ActiveFill to "Being dropped",
            WindowFill to "Coefficient",
            ResultFill to "Survivor",
        ),
        build = ::rfeFrames,
    ),
    "apriori" to WalkConfig(
        intro = "Ten baskets, five items, minimum support 3. Level by level: generate candidates, prune what cannot possibly be frequent, then count only what is left.",
        legend = listOf(
            ActiveFill to "Being counted",
            DoneFill to "Frequent",
            ResultFill to "Answer",
        ),
        build = ::aprioriFrames,
    ),
    "eclat" to WalkConfig(
        intro = "The same database stored by column instead of by row, so support becomes the length of a set intersection rather than the result of a counting pass.",
        legend = listOf(
            WindowFill to "In tid-list",
            DoneFill to "In intersection",
            ResultFill to "Answer",
        ),
        build = ::eclatFrames,
    ),
    "kmp" to WalkConfig(
        intro = "First the LPS table is built from the pattern alone, then the text is scanned. Watch the text " +
            "pointer i on the second phase: it only ever moves forward, even on a mismatch.",
        legend = pointerLegend,
        build = ::kmpFrames,
    ),
    "rabin_karp" to WalkConfig(
        intro = "Base-10 hashes mod 13 over a digit string. Most windows are rejected on a number comparison " +
            "alone — and one collision shows why a hash hit still has to be verified.",
        legend = pointerLegend,
        build = ::rabinKarpFrames,
    ),
    "manacher" to WalkConfig(
        intro = "Radii for every centre of \"#a#b#a#c#a#b#a#\". Centres inside a known palindrome inherit their " +
            "mirror's radius instead of expanding from zero.",
        legend = pointerLegend,
        build = ::manacherFrames,
    ),
    "sparse_table" to WalkConfig(
        intro = "Four levels of power-of-two minima are built, then one query is answered with two overlapping " +
            "blocks. The overlap is what restricts the structure to idempotent operations.",
        legend = pointerLegend,
        build = ::sparseTableFrames,
    ),
    "prefix_sum" to WalkConfig(
        intro = "One pass builds P, then every range sum is a single subtraction. Watch the query at the end pay " +
            "nothing for the width of the range.",
        legend = pointerLegend,
        build = ::prefixSumFrames,
    ),
    "difference_array" to WalkConfig(
        intro = "The mirror image of prefix sum: two writes record a whole range update, and one prefix pass at the " +
            "end turns all of them into the final array.",
        legend = pointerLegend,
        build = ::differenceArrayFrames,
    ),
    "sliding_window" to WalkConfig(
        intro = "A fixed window of 3 over the array. Each slide subtracts what leaves and adds what enters, so the " +
            "sum is maintained rather than recomputed.",
        legend = pointerLegend,
        build = ::slidingWindowFrames,
    ),
    "two_pointer" to WalkConfig(
        intro = "Pair sums on a sorted array. Each comparison rules out an entire row or column of the pair table, " +
            "which is why one pass replaces the nested loop.",
        legend = pointerLegend,
        build = ::twoPointerFrames,
    ),
    "kadanes_algorithm" to WalkConfig(
        intro = "Kadane in one pass. The only state is \"best sum ending here\" — the moment extending is worse than " +
            "starting over, the subarray restarts.",
        legend = pointerLegend,
        build = ::kadaneFrames,
    ),
    "top_k_elements" to WalkConfig(
        intro = "Top-3 over a stream using a min-heap of size 3. The heap's smallest element is the admission price: " +
            "anything below it can be discarded on sight.",
        legend = heapLegend,
        build = ::topKStreamFrames,
    ),
    "sliding_window_pattern" to WalkConfig(
        intro = "The pattern applied to a variable-length window: longest substring with no repeated character. The " +
            "window grows on the right and only ever shrinks from the left.",
        legend = pointerLegend,
        build = ::longestUniqueWindowFrames,
    ),
    "two_pointer_pattern" to WalkConfig(
        intro = "The same-direction variant of the pattern: a read pointer scans while a write pointer trails, " +
            "compacting a sorted array in place.",
        legend = pointerLegend,
        build = ::dedupeInPlaceFrames,
    ),
    "fast_slow_pointers" to WalkConfig(
        intro = "A linked list whose tail points back into itself. One pointer moves one step, the other two — where " +
            "they meet, and what to do next, is the whole pattern.",
        legend = listOf(
            WindowFill to "slow",
            ActiveFill to "fast",
            ResultFill to "Meeting point",
        ),
        build = ::fastSlowFrames,
    ),
    "merge_intervals_pattern" to WalkConfig(
        intro = "Sort by start, then keep one open interval and either extend it or close it. The track underneath " +
            "shows what the merged set looks like at each step.",
        legend = listOf(
            ActiveFill to "Considering",
            DoneFill to "Merged",
            ResultFill to "Final",
        ),
        build = ::mergeIntervalsFrames,
    ),
    "top_k_pattern" to WalkConfig(
        intro = "Top-2 most frequent. Counting comes first; the heap only decides the selection, and its size caps " +
            "the log factor at log k rather than log m.",
        legend = heapLegend,
        build = ::topKFrequentFrames,
    ),
    "prefix_sum_pattern" to WalkConfig(
        intro = "The interview form of prefix sums: counting subarrays that hit a target, with a map of totals " +
            "already seen. The negative value in the input is there to show why a window cannot do this.",
        legend = listOf(
            ActiveFill to "Reading",
            WindowFill to "In prefix",
            ResultFill to "Answer",
        ),
        build = ::prefixSumPatternFrames,
    ),
    "binary_search_answer" to WalkConfig(
        intro = "Binary search where the array is unsorted and the search runs over candidate answers instead. Each " +
            "probe repacks the ships; the row underneath is which day every package sails on.",
        legend = listOf(
            ActiveFill to "Even-numbered day",
            WindowFill to "Odd-numbered day",
            ResultFill to "Final packing",
        ),
        build = ::binarySearchAnswerFrames,
    ),
    "monotonic_stack_pattern" to WalkConfig(
        intro = "Next greater element. Watch the aux row stay in decreasing order — every pop is one index learning " +
            "its answer, which is why the whole scan is O(n).",
        legend = listOf(
            ActiveFill to "Incoming",
            WindowFill to "On stack",
            DoneFill to "Answered",
        ),
        build = ::monotonicStackFrames,
    ),
    "cyclic_sort_pattern" to WalkConfig(
        intro = "Values 1..n placed by swapping each one to the index it names. The array ends sorted with a single " +
            "mismatch, and that mismatch is both the duplicate and the missing value.",
        legend = listOf(
            ActiveFill to "Being placed",
            DoneFill to "Home",
            ResultFill to "Mismatch",
        ),
        build = ::cyclicSortFrames,
    ),
    "in_place_reversal_pattern" to WalkConfig(
        intro = "Reversing a linked list with prev / cur / next. The aux row is the reversed chain as it grows — no " +
            "second list is ever allocated.",
        legend = listOf(
            ActiveFill to "cur",
            DoneFill to "Reversed",
            ResultFill to "Final order",
        ),
        build = ::inPlaceReversalFrames,
    ),
    "k_way_merge_pattern" to WalkConfig(
        intro = "Three sorted lists merged through a heap that never holds more than one head per list. The output " +
            "row fills left to right; the heap row is what makes each choice O(log k).",
        legend = listOf(
            ActiveFill to "Popped",
            DoneFill to "In heap",
            ResultFill to "Merged",
        ),
        build = ::kWayMergeFrames,
    ),
    "greedy_intervals_pattern" to WalkConfig(
        intro = "Earliest-finish-first on one room. The track underneath shows the kept set growing, and the count " +
            "of drops is the answer to the \"minimum removals\" phrasing of the same problem.",
        legend = listOf(
            ActiveFill to "Considering",
            DoneFill to "Kept",
            ResultFill to "Final set",
        ),
        build = ::greedyIntervalsFrames,
    ),
    "expression_stack_pattern" to WalkConfig(
        intro = "Decoding \"3[a2[bc]]\". The aux row is the stack of suspended contexts — each '[' pushes one, each " +
            "']' pops one and folds the finished piece into its parent.",
        legend = listOf(
            ActiveFill to "Current token",
            WindowFill to "Suspended context",
            ResultFill to "Decoded output",
        ),
        build = ::expressionStackFrames,
    ),
    "heap_scheduling_pattern" to WalkConfig(
        intro = "Meeting rooms: sorted by start, with a min-heap of end times underneath. The heap's size is the " +
            "answer — watch it grow only when nothing has retired in time.",
        legend = listOf(
            ActiveFill to "Starting now",
            DoneFill to "Room in use",
            ResultFill to "Final schedule",
        ),
        build = ::heapSchedulingFrames,
    ),
    "lis_patience_pattern" to WalkConfig(
        intro = "The tails array doing its work. Most elements overwrite a tail rather than extend it — the row's " +
            "length only grows when an element beats every chain so far.",
        legend = listOf(
            ActiveFill to "Current element",
            WindowFill to "tails",
            ResultFill to "Answer",
        ),
        build = ::lisTailsFrames,
    ),
    "monotonic_deque_pattern" to WalkConfig(
        intro = "Sliding-window maximum with k = 3. Two rules fire on every element: expire the front, pop the " +
            "dominated from the back. What is left in front is the answer.",
        legend = listOf(
            ActiveFill to "Incoming",
            WindowFill to "In window / deque",
            ResultFill to "Window max",
        ),
        build = ::windowMaxDequeFrames,
    ),
    "palindrome_expansion_pattern" to WalkConfig(
        intro = "Both centre types tried at every index of \"abbanana\" — the even centres are the ones that find " +
            "\"abba\", which is why forgetting them is the standard bug.",
        legend = listOf(
            ActiveFill to "Centre",
            WindowFill to "Palindrome here",
            ResultFill to "Longest so far",
        ),
        build = ::palindromeExpansionFrames,
    ),
    "prefix_function_pattern" to WalkConfig(
        intro = "The pi table built one character at a time. The interesting frames are the mismatches, where k " +
            "falls back to pi[k−1] instead of restarting at zero.",
        legend = listOf(
            ActiveFill to "Current index",
            WindowFill to "Border prefix",
            ResultFill to "Final border",
        ),
        build = ::prefixFunctionFrames,
    ),
    "randomized_pattern" to WalkConfig(
        intro = "Fisher-Yates with the draw range drawn underneath. Every swap picks from [i, n) — the aux row is " +
            "the legal range, and widening it to [0, n) is exactly the bias people ship.",
        legend = listOf(
            ActiveFill to "Drawn index j",
            DoneFill to "Fixed",
            WindowFill to "Legal draws",
        ),
        build = ::fisherYatesFrames,
    ),
    "range_query_pattern" to WalkConfig(
        intro = "The same array under both structures: first a prefix-sum rebuild after one write, then the Fenwick " +
            "update and query walks that replace it. i & −i is doing all the work.",
        legend = listOf(
            ActiveFill to "On the walk",
            WindowFill to "Blocks / prefixes",
            ResultFill to "Queried range",
        ),
        build = ::fenwickRangeQueryFrames,
    ),
    "rolling_hash_pattern" to WalkConfig(
        intro = "\"abra\" in \"abracadabra\" with a base-31 hash mod 1009. Each slide is three operations, and every " +
            "hash hit still gets a character-by-character verification.",
        legend = listOf(
            ActiveFill to "Candidate",
            WindowFill to "Current window",
            ResultFill to "Verified match",
        ),
        build = ::rollingHashPatternFrames,
    ),
    "running_best_pattern" to WalkConfig(
        intro = "Kadane with the \"best ending here\" row exposed. The last frame switches to the product variant, " +
            "where the running minimum is what produces the answer.",
        legend = listOf(
            ActiveFill to "Current element",
            WindowFill to "Current run",
            ResultFill to "Best subarray",
        ),
        build = ::runningBestFrames,
    ),
    "sweep_line_pattern" to WalkConfig(
        intro = "Five intervals reduced to ten endpoints. The track underneath still shows the intervals, but the " +
            "sweep never consults them again — only the ±1 events.",
        legend = listOf(
            ActiveFill to "Current event",
            WindowFill to "Active",
            ResultFill to "Peak overlap",
        ),
        build = ::sweepLineFrames,
    ),
    "two_heaps_pattern" to WalkConfig(
        intro = "A running median over eight values. The aux row is both heaps side by side — lo largest-first, hi " +
            "smallest-first — so the median is always the pair in the middle.",
        legend = listOf(
            ActiveFill to "Arriving",
            WindowFill to "lo (smaller half)",
            DoneFill to "hi (larger half)",
        ),
        build = ::twoHeapsFrames,
    ),
    "quickselect" to WalkConfig(
        intro = "Partition, then recurse into one side only. The comparison count at the end is the argument for " +
            "why selecting is cheaper than sorting.",
        legend = listOf(
            ActiveFill to "Comparing",
            WindowFill to "Below pivot",
            ResultFill to "Pivot · answer",
        ),
        build = ::quickselectFrames,
    ),
    "median_of_medians" to WalkConfig(
        intro = "A pivot chosen so its split is guaranteed rather than hoped for — and the measured split it " +
            "produces on this input.",
        legend = listOf(
            ActiveFill to "Group median",
            WindowFill to "Below pivot",
            ResultFill to "Pivot",
        ),
        build = ::medianOfMediansFrames,
    ),
    "mos_algorithm" to WalkConfig(
        intro = "The same five range queries answered in two different orders, with the pointer travel counted " +
            "both times. The ordering is the entire algorithm.",
        legend = listOf(
            WindowFill to "Current window",
            ActiveFill to "Pointer",
            DoneFill to "Answered",
        ),
        build = ::mosAlgorithmFrames,
    ),
    "reservoir_sampling" to WalkConfig(
        intro = "One pass, constant memory, and a uniform sample from a stream of unknown length — with the " +
            "uniformity measured over 20,000 runs rather than asserted.",
        legend = listOf(
            ActiveFill to "Current item",
            ResultFill to "In reservoir",
            DoneFill to "Within tolerance",
        ),
        build = ::reservoirSamplingFrames,
    ),
    "fenwick_tree" to WalkConfig(
        intro = "The tree is an array and the algorithm is index arithmetic: add the lowest set bit to update, " +
            "strip it to query. Both walks are counted against the alternatives at the end.",
        legend = listOf(
            ActiveFill to "Node being touched",
            WindowFill to "Range it covers",
            DoneFill to "Already visited",
        ),
        build = ::fenwickFrames,
    ),
    "string" to WalkConfig(
        intro = "Characters in contiguous storage. Indexing and comparison are the easy half; the interesting half " +
            "is what immutability does to a loop that builds a string with +.",
        legend = listOf(
            ActiveFill to "Current character",
            DoneFill to "Settled",
            ResultFill to "Result",
        ),
        build = ::stringFrames,
    ),
    "list_adt" to WalkConfig(
        intro = "One script of list operations run against two implementations at once. The contract is identical " +
            "at every step; the counters underneath are not.",
        legend = listOf(
            ActiveFill to "Touched",
            WindowFill to "Traversed",
            ResultFill to "Written",
        ),
        build = ::listAdtFrames,
    ),
    "job_sequencing" to WalkConfig(
        intro = "Highest profit first, each job dropped into the latest slot that still meets its deadline. The " +
            "final frames check the greedy answer against every other schedule.",
        legend = listOf(
            ActiveFill to "Considering",
            DoneFill to "Scheduled",
            ResultFill to "Slot filled",
        ),
        build = ::jobSequencingFrames,
    ),
    "fractional_knapsack" to WalkConfig(
        intro = "Sort by value per kilogram, fill greedily, split the last item. Then the same greedy is turned " +
            "loose on the indivisible version to show exactly where the argument breaks.",
        legend = listOf(
            ActiveFill to "Considering",
            DoneFill to "Taken whole",
            ResultFill to "Taken in part",
        ),
        build = ::fractionalKnapsackFrames,
    ),
    "activity_selection" to WalkConfig(
        intro = "Sort by finish time, sweep once, take anything that starts after the last thing taken. The last two " +
            "frames run earliest-start and shortest-duration on the same idea so the wrong sort keys are seen losing.",
        legend = listOf(
            ActiveFill to "Considering",
            DoneFill to "Selected",
            ResultFill to "Final schedule",
        ),
        build = ::activitySelectionFrames,
    ),
    "coin_change_greedy" to WalkConfig(
        intro = "Greedy change-making and the DP table, on the same {1, 3, 4} coins and the same target of 6. Greed " +
            "commits to the 4 and pays three coins; the table finds 3 + 3.",
        legend = listOf(
            ActiveFill to "Remaining amount",
            WindowFill to "Tabulated",
            ResultFill to "Answer",
        ),
        build = ::coinChangeGreedyFrames,
    ),
    "naive_string_search" to WalkConfig(
        intro = "The pattern drawn where it currently sits, one frame per character comparison. The shift of one is a " +
            "movement on screen, and the last frame counts the worst case rather than describing it.",
        legend = listOf(
            ActiveFill to "Comparing",
            WindowFill to "Matched this alignment",
            ResultFill to "Mismatch / match",
        ),
        build = ::naiveSearchFrames,
    ),
    "z_algorithm" to WalkConfig(
        intro = "The Z-array built left to right. The pointers are the window [l, r) whose right edge never moves left — " +
            "the readout counts how many positions were copied from a mirror against how many characters were actually compared.",
        legend = listOf(
            ActiveFill to "Current index",
            DoneFill to "Prefix it matches",
            ResultFill to "The run found here",
        ),
        build = ::zAlgorithmFrames,
    ),
    "longest_palindromic_substring" to WalkConfig(
        intro = "Expand around every centre — the character centres and the gap centres both, since dropping the gaps is " +
            "the bug that reports \"a\" as the longest palindrome in \"abba\".",
        legend = listOf(
            WindowFill to "Expanded here",
            DoneFill to "Best so far",
            ResultFill to "New best",
        ),
        build = ::longestPalindromeFrames,
    ),
    "euclid_gcd" to WalkConfig(
        intro = "The remainder chain for gcd(252, 105) as a growing row, then the back-substitution that turns it into " +
            "Bézout coefficients — which is the part modular inverses, CRT and RSA key generation actually consume.",
        legend = listOf(
            ActiveFill to "Current remainder",
            WindowFill to "Previous pair",
            ResultFill to "gcd / result",
        ),
        build = ::euclidGcdFrames,
    ),
    "modular_arithmetic" to WalkConfig(
        intro = "Residues mod 7, and the one operation that does not survive the collapse. The aux row is a·k mod m — whether " +
            "1 ever appears in it is exactly whether a has an inverse.",
        legend = listOf(
            ActiveFill to "Current",
            WindowFill to "Residue reached",
            ResultFill to "Answer / inverse",
        ),
        build = ::modularArithmeticFrames,
    ),
    "sieve_of_eratosthenes" to WalkConfig(
        intro = "2 through 30 across two rows. Each pass strikes one prime's multiples starting at p², and the outer loop stops " +
            "at √30 — so only 2, 3 and 5 ever mark anything and nothing is ever divided.",
        legend = listOf(
            ActiveFill to "Prime being processed",
            WindowFill to "Still standing",
            ResultFill to "Struck this pass",
        ),
        build = ::sieveFrames,
    ),
    "fermats_little_theorem" to WalkConfig(
        intro = "Powers of 3 mod 7, one exponent at a time, arriving at 1 exactly at exponent 6. Then the inverse that follows " +
            "from it, and the composite that passes the test anyway.",
        legend = listOf(
            ActiveFill to "Current exponent",
            WindowFill to "Computed",
            ResultFill to "≡ 1 / result",
        ),
        build = ::fermatFrames,
    ),
    "chinese_remainder_theorem" to WalkConfig(
        intro = "0 through 31 across two rows, with each congruence applied as a filter. One number survives all three, and the " +
            "closing frames show the construction that finds it without scanning at all.",
        legend = listOf(
            WindowFill to "Still possible",
            ResultFill to "The unique solution",
            ActiveFill to "Eliminated",
        ),
        build = ::crtFrames,
    ),
)

private fun walkConfigFor(topicId: String): WalkConfig =
    walkConfigs[topicId] ?: walkConfigs.getValue("two_pointer")

// Frames are precomputed and Compose-free, so SimulationFrameTest can run every configured builder
// on the JVM. Without this a builder that throws only surfaces by opening the topic in the app.
internal val arrayWalkTopicIds: Set<String> get() = walkConfigs.keys

/**
 * Extended with D6, the first batch to put AI labs on this widget. Beyond "the builder runs", it
 * checks what the renderer swallows in silence: pointers and loop-back edges are drawn by index, so
 * a stale one lands under a cell that is not there.
 *
 * The aux row is checked only for being *longer* than the cell row. Both rows are drawn
 * `fillMaxWidth` with equal-weight cells, so a shorter aux is a legitimate and common choice — it is
 * a separate structure (a heap, a merge buffer, a pattern being slid) rather than a per-index
 * annotation, and eleven existing labs use it that way. A *longer* aux has no reading at all: it
 * makes the aux cells narrower than the cells above them, so nothing lines up with anything.
 */
internal fun arrayWalkFrameCount(topicId: String): Int {
    val frames = walkConfigFor(topicId).build()
    frames.forEachIndexed { index, frame ->
        require(frame.cells.isNotEmpty()) { "$topicId frame $index draws no cells" }
        require(frame.status.isNotBlank()) { "$topicId frame $index has no status line" }
        frame.aux?.let {
            require(it.size <= frame.cells.size) {
                "$topicId frame $index has ${it.size} aux cells under only ${frame.cells.size} cells — the two rows " +
                    "are drawn at different widths and nothing lines up"
            }
        }
        frame.pointers.keys.forEach {
            require(it in frame.cells.indices) { "$topicId frame $index points at cell $it of ${frame.cells.size}" }
        }
        frame.loopBack?.let { (from, to) ->
            require(from in frame.cells.indices && to in frame.cells.indices) {
                "$topicId frame $index draws a loop-back edge $from -> $to outside its ${frame.cells.size} cells"
            }
        }
    }
    return frames.size
}

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun ArrayWalkSection(topicId: String) {
    val config = remember(topicId) { walkConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 650f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                config.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CellRow(frame.cells, modifier = Modifier.padding(top = 14.dp))
            PointerRow(frame.pointers, frame.cells.size)
            frame.loopBack?.let { LoopBackArc(it, frame.cells.size) }

            frame.aux?.let { aux ->
                Text(
                    frame.auxLabel.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                CellRow(aux, modifier = Modifier.padding(top = 4.dp))
            }

            frame.intervals?.let { IntervalTrack(it, modifier = Modifier.padding(top = 12.dp)) }

            frame.readout?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Text(
                frame.status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                config.legend.forEach { (color, label) -> WalkLegend(color, label) }
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun WalkLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun cellBackground(mark: CellMark): Color = when (mark) {
    CellMark.IDLE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    CellMark.DIM -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    CellMark.WINDOW -> WindowFill.copy(alpha = 0.28f)
    CellMark.ACTIVE -> ActiveFill
    CellMark.DONE -> DoneFill
    CellMark.RESULT -> ResultFill
}

@Composable
private fun cellForeground(mark: CellMark): Color = when (mark) {
    CellMark.DONE, CellMark.RESULT -> Color.White
    CellMark.ACTIVE -> Color(0xFF1F2937)
    CellMark.DIM -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    else -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun CellRow(cells: List<CellView>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        cells.forEach { cell ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .background(cellBackground(cell.mark), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    cell.text,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = cellForeground(cell.mark),
                )
            }
        }
    }
}

@Composable
private fun PointerRow(pointers: Map<Int, String>, count: Int) {
    if (pointers.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (i in 0 until count) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                pointers[i]?.let {
                    Text(
                        "▲$it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

/** The tail-to-node arc that makes the row read as a linked list with a cycle rather than an array. */
@Composable
private fun LoopBackArc(edge: Pair<Int, Int>, count: Int) {
    val stroke = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(26.dp)) {
        val slot = size.width / count
        val from = slot * (edge.first + 0.5f)
        val to = slot * (edge.second + 0.5f)
        val path = Path().apply {
            moveTo(from, 0f)
            cubicTo(from, size.height * 1.6f, to, size.height * 1.6f, to, 0f)
        }
        drawPath(path, color = stroke, style = Stroke(width = 3.dp.toPx()))
        // Arrowhead pointing back up into the target cell.
        drawPath(
            Path().apply {
                moveTo(to, 0f)
                lineTo(to - 7f, 9f)
                lineTo(to + 7f, 9f)
                close()
            },
            color = stroke,
        )
    }
}

@Composable
private fun IntervalTrack(intervals: List<IntervalView>, modifier: Modifier = Modifier) {
    val fills = intervals.map { cellBackground(it.mark) }
    val minStart = intervals.minOfOrNull { it.start } ?: 0
    val maxEnd = intervals.maxOfOrNull { it.end } ?: 1
    val span = (maxEnd - minStart).coerceAtLeast(1).toFloat()
    val rowHeight = 16.dp

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight * intervals.size + 4.dp),
    ) {
        val barHeight = size.height / intervals.size
        intervals.forEachIndexed { index, interval ->
            val left = (interval.start - minStart) / span * size.width
            val right = (interval.end - minStart) / span * size.width
            drawRoundRect(
                color = fills[index],
                topLeft = Offset(left, index * barHeight + barHeight * 0.15f),
                size = Size((right - left).coerceAtLeast(6f), barHeight * 0.7f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            )
        }
    }
}
