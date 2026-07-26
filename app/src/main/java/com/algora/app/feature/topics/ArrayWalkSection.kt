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
