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
private val ActiveFill = Color(0xFFFACC15)
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
        cells = charRow(s, active = mismatch, done = 0 until mismatch),
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

// ── Config ───────────────────────────────────────────────────────────────────

private val walkConfigs = mapOf(
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
)

private fun walkConfigFor(topicId: String): WalkConfig =
    walkConfigs[topicId] ?: walkConfigs.getValue("two_pointer")

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
        drawPath(path, color = stroke, style = Stroke(width = 3f))
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
