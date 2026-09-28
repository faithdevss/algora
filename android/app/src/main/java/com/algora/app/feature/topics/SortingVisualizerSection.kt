package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.SimColors

// ── Sorting visualizer ───────────────────────────────────────────────────────
// One reusable bar-chart player for every sorting algorithm: the algorithm runs once up front and
// records a snapshot per comparison / swap / write, exactly like GraphSimulationSection precomputes
// its BFS/DFS sequence. PlaybackTransport then steps through the frames.
//
// Roles colour the bars: compared pair, the element being moved, the region already sorted, and a
// secondary "range" highlight (merge window, current pivot partition, heap boundary, digit bucket).

// A piece of a narrated headline; a tone colours it to match the bar it names.
private enum class SortTone { COMPARE, PIVOT, PLACED, RANGE }

// How a cell in one of the rows under the bars (runs, output, counts, buckets) is drawn. DIM is a value
// already used up; EMPTY a slot still to fill, drawn dashed.
private enum class CellTone { IDLE, DIM, COMPARE, RANGE, PLACED, PIVOT, EMPTY }

// [digit] brightens one character of [text] and dims the rest: the digit a radix pass is reading.
private class SortCell(val text: String, val tone: CellTone = CellTone.IDLE, val digit: Int? = null)

// A labelled row of cells under the bars. [breaks] are indices that start a new group, with a wider gap
// before them (left run · right run).
private class SortRow(
    val label: String?,
    val cells: List<SortCell>,
    val note: String? = null,
    val headers: List<String>? = null,
    val breaks: Set<Int> = emptySet(),
)

// Radix sort's ten buckets, each a column of the values it holds so far.
private class DigitBuckets(val label: String, val active: Int?, val columns: List<List<SortCell>>)

// One of bucket sort's range boxes: its range, its values as small bars, and whether it is the one
// being worked on (outlined).
private class BucketBox(val range: String, val values: List<Int>, val tone: CellTone, val active: Boolean)

private class SortSpan(val text: String, val tone: SortTone? = null)

private class SortFrame(
    val values: List<Int>,
    val compared: Set<Int> = emptySet(),
    val moved: Set<Int> = emptySet(),
    val sorted: Set<Int> = emptySet(),
    val range: Set<Int> = emptySet(),
    val status: String,
    // The rest is the narrated design (docs/ios-design/Simulations iOS.html, Quick Sort). A sort that
    // leaves it empty still gets the bars; the headline falls back to `status`.
    val pivot: Int? = null,
    val lowSide: Set<Int> = emptySet(),   // scanned and ≤ pivot
    val highSide: Set<Int> = emptySet(),  // scanned and > pivot
    // When set, bars outside it (and not yet sorted) are faded: the partition being worked on.
    val focus: Set<Int>? = null,
    val pointers: Map<Int, List<String>> = emptyMap(),
    val chips: List<Pair<String, String>> = emptyList(),
    val title: String? = null,
    val headline: List<SortSpan>? = null,
    val body: String? = null,
    // Top-right of the card, per step ("adjacent swaps", "level 2 of 3").
    val note: String? = null,
    // An index drawn as an empty dashed bar holding [ghostValue]: insertion sort's gap.
    val ghost: Int? = null,
    val ghostValue: Int? = null,
    // Pointer label colours for this step, over the defaults ("i" is plain in selection sort).
    val pointerTones: Map<String, SortTone?> = emptyMap(),
    val rows: List<SortRow> = emptyList(),
    val digitBuckets: DigitBuckets? = null,
    val buckets: List<BucketBox> = emptyList(),
    val barMax: Int? = null,
    val legend: List<Pair<CellTone, String>>? = null,
    val showBars: Boolean = true,
    // Bars drawn as empty slots rather than values (counting sort's output before it is written).
    val emptyBars: Set<Int> = emptySet(),
)

private class SortConfig(
    val intro: String,
    val rangeLabel: String?,
    val build: () -> List<SortFrame>,
    // Shown top-right of a narrated sort's card, e.g. the partition scheme.
    val variant: String? = null,
)

private val ComparedBar = SimColors.Active
private val MovedBar = Color(0xFFF97316)
private val SortedBar = SimColors.Green
private val RangeBar = SimColors.Blue
private val PivotBar = SimColors.Answer

// Pointer labels under the bars take the colour of the role they point at.
private val PointerColors = mapOf("i" to RangeBar, "j" to ComparedBar, "pivot" to PivotBar)

// Small enough that every bar stays readable on a phone, big enough that O(n²) vs O(n log n) shows.
private val sampleInput = listOf(42, 8, 27, 61, 15, 34, 3, 50)

private class SortBuilder(initial: List<Int>) {
    val values = initial.toMutableList()
    val frames = mutableListOf<SortFrame>()

    fun frame(
        status: String,
        compared: Set<Int> = emptySet(),
        moved: Set<Int> = emptySet(),
        sorted: Set<Int> = emptySet(),
        range: Set<Int> = emptySet(),
    ) {
        frames.add(SortFrame(values.toList(), compared, moved, sorted, range, status))
    }

    fun done(status: String = "Sorted — every element is in its final position.") {
        frames.add(SortFrame(values.toList(), sorted = values.indices.toSet(), status = status))
    }
}

private fun bubbleSortFrames(): List<SortFrame> {
    val a = sampleInput.toMutableList()
    val n = a.size
    val frames = mutableListOf<SortFrame>()
    val legend = listOf(CellTone.COMPARE to "Comparing", CellTone.PLACED to "Sorted tail")
    var pass = 0
    do {
        pass++
        val end = n - pass
        val tail = (end + 1 until n).toSet()
        var swaps = 0
        val passMax = a.subList(0, end + 1).max()
        for (j in 0 until end) {
            val x = a[j]
            val y = a[j + 1]
            val swap = x > y
            if (swap) swaps++
            frames += SortFrame(
                a.toList(),
                compared = setOf(j, j + 1),
                sorted = tail,
                status = if (swap) "$x > $y, so they swap." else "$x ≤ $y, so they stay.",
                pointers = mapOf(j to listOf("j"), j + 1 to listOf("j+1")),
                pointerTones = mapOf("j+1" to SortTone.COMPARE),
                chips = listOf("pass" to "$pass", "swaps" to "$swaps", "compare" to if (swap) "$x > $y" else "$x ≤ $y"),
                title = "PASS $pass · a[0..$end]",
                note = "adjacent swaps",
                headline = listOf(SortSpan(if (swap) "$x > $y" else "$x ≤ $y", SortTone.COMPARE), SortSpan(if (swap) ", so they swap." else ", so they stay.")),
                body = if (swap) {
                    "The largest unsorted value moves right. At the end of this pass, $passMax joins the sorted tail."
                } else {
                    "Only neighbours are compared. The larger one, $y, carries on to the next comparison."
                },
                legend = legend,
            )
            if (swap) {
                a[j] = y
                a[j + 1] = x
            }
        }
    } while (swaps > 0 && pass < n - 1)
    frames += SortFrame(
        a.toList(),
        sorted = a.indices.toSet(),
        status = "Sorted after $pass passes.",
        chips = listOf("passes" to "$pass", "comparisons" to "${frames.size}"),
        title = "SORTED a[0..${n - 1}]",
        note = "adjacent swaps",
        headline = listOf(SortSpan("Sorted", SortTone.PLACED), SortSpan(" after $pass passes.")),
        body = "Bubble sort makes O(n²) comparisons. It stops early only when a whole pass makes no swap.",
        legend = legend,
    )
    return frames
}

private fun selectionSortFrames(): List<SortFrame> {
    val a = sampleInput.toMutableList()
    val n = a.size
    val frames = mutableListOf<SortFrame>()
    val legend = listOf(CellTone.COMPARE to "Scanning", CellTone.RANGE to "Min so far", CellTone.PLACED to "Sorted")
    val plain = mapOf<String, SortTone?>("i" to null, "min" to SortTone.RANGE)
    for (i in 0 until n - 1) {
        var min = i
        val sorted = (0 until i).toSet()
        for (j in i + 1 until n) {
            val v = a[j]
            val smaller = v < a[min]
            val previous = a[min]
            frames += SortFrame(
                a.toList(),
                compared = setOf(j),
                range = setOf(min),
                sorted = sorted,
                status = if (smaller) "$v is smaller than $previous, so it becomes the minimum." else "$v is not smaller than $previous, so the minimum stays.",
                pointers = listOf(i to "i", min to "min", j to "j").groupBy({ it.first }, { it.second }),
                pointerTones = plain,
                chips = listOf("i" to "$i", "min" to "$previous", "j" to "$j"),
                title = "SCAN a[$i..${n - 1}]",
                note = "find the minimum",
                headline = if (smaller) {
                    listOf(SortSpan("$v", SortTone.COMPARE), SortSpan(" is smaller than "), SortSpan("$previous", SortTone.RANGE), SortSpan(", so it becomes the minimum."))
                } else {
                    listOf(SortSpan("$v", SortTone.COMPARE), SortSpan(" is not smaller than "), SortSpan("$previous", SortTone.RANGE), SortSpan(", so the minimum stays."))
                },
                body = if (j == n - 1) {
                    "That was the last value. ${if (smaller) v else previous} is the minimum of a[$i..${n - 1}]."
                } else {
                    "When the scan ends, the minimum swaps into position $i."
                },
                legend = legend,
            )
            if (smaller) min = j
        }
        val m = a[min]
        val displaced = a[i]
        a[min] = displaced
        a[i] = m
        frames += SortFrame(
            a.toList(),
            sorted = (0..i).toSet(),
            status = "$m moves into position $i.",
            pointers = mapOf(i to listOf("i")),
            pointerTones = plain,
            chips = listOf("i" to "$i", "min" to "$m", "swaps" to "${i + 1}"),
            title = "SCAN a[$i..${n - 1}]",
            note = "find the minimum",
            headline = listOf(SortSpan("$m", SortTone.PLACED), SortSpan(" moves into position $i.")),
            body = if (min == i) "It was already there, so the swap does nothing. a[0..$i] is final." else "$displaced goes to index $min in exchange. a[0..$i] is final.",
            legend = legend,
        )
    }
    frames += SortFrame(
        a.toList(),
        sorted = a.indices.toSet(),
        status = "Sorted with ${n - 1} swaps.",
        chips = listOf("swaps" to "${n - 1}"),
        title = "SORTED a[0..${n - 1}]",
        note = "find the minimum",
        headline = listOf(SortSpan("Sorted", SortTone.PLACED), SortSpan(" with ${n - 1} swaps.")),
        body = "Selection sort always scans the whole unsorted part, so it is O(n²) even on sorted input. Its strength is the small number of swaps.",
        legend = legend,
    )
    return frames
}

private fun insertionSortFrames(): List<SortFrame> {
    val a = sampleInput.toMutableList()
    val n = a.size
    val frames = mutableListOf<SortFrame>()
    val legend = listOf(CellTone.COMPARE to "Comparing", CellTone.RANGE to "Sorted prefix", CellTone.EMPTY to "Key in the gap")
    for (i in 1 until n) {
        val key = a[i]
        var gap = i
        var shifts = 0
        fun prefix() = (0..i).toSet() - gap
        val title = "INSERT a[$i] INTO a[0..${i - 1}]"
        frames += SortFrame(
            a.toList(),
            range = prefix(),
            status = "Take $key out of a[$i].",
            ghost = gap,
            ghostValue = key,
            pointers = mapOf(gap to listOf("gap")),
            pointerTones = mapOf("gap" to SortTone.COMPARE),
            chips = listOf("key" to "$key", "shifts" to "0"),
            title = title,
            note = "shift right",
            headline = listOf(SortSpan("Take "), SortSpan("$key", SortTone.COMPARE), SortSpan(" out of a[$i].")),
            body = "a[0..${i - 1}] is already sorted. Larger values shift right until the gap reaches the key's place.",
            legend = legend,
        )
        while (true) {
            val j = gap - 1
            if (j < 0 || a[j] <= key) break
            val v = a[j]
            frames += SortFrame(
                a.toList(),
                compared = setOf(j),
                range = prefix() - j,
                status = "$v > $key, so $v shifts right.",
                ghost = gap,
                ghostValue = key,
                pointers = mapOf(j to listOf("j"), gap to listOf("gap")),
                pointerTones = mapOf("gap" to SortTone.COMPARE),
                chips = listOf("key" to "$key", "shifts" to "$shifts"),
                title = title,
                note = "shift right",
                headline = listOf(SortSpan("$v > $key", SortTone.COMPARE), SortSpan(", so $v shifts right.")),
                body = when {
                    j == 0 -> "The gap moves to index 0, the front of the array, so the key lands there."
                    a[j - 1] <= key -> "The gap moves to index $j. ${a[j - 1]} is smaller than $key, so the key lands there."
                    else -> "The gap moves to index $j, and $key is compared with ${a[j - 1]} next."
                },
                legend = legend,
            )
            a[gap] = v
            gap = j
            shifts++
        }
        a[gap] = key
        frames += SortFrame(
            a.toList(),
            range = (0..i).toSet() - gap,
            sorted = setOf(gap),
            status = "$key lands at index $gap.",
            chips = listOf("key" to "$key", "shifts" to "$shifts"),
            title = title,
            note = "shift right",
            headline = listOf(SortSpan("$key", SortTone.PLACED), SortSpan(" lands at index $gap.")),
            body = if (shifts == 0) "Nothing before it was bigger, so it stays put. a[0..$i] is sorted." else "$shifts value${if (shifts == 1) "" else "s"} shifted to make room. a[0..$i] is sorted.",
            legend = listOf(CellTone.RANGE to "Sorted prefix", CellTone.PLACED to "Just placed"),
        )
    }
    frames += SortFrame(
        a.toList(),
        sorted = a.indices.toSet(),
        status = "Sorted.",
        title = "SORTED a[0..${n - 1}]",
        note = "shift right",
        headline = listOf(SortSpan("Sorted", SortTone.PLACED), SortSpan(": the prefix grew to the whole array.")),
        body = "Each key only shifts past bigger values, so nearly sorted input finishes in close to linear time.",
        legend = listOf(CellTone.PLACED to "Sorted"),
    )
    return frames
}

private fun mergeSortFrames(): List<SortFrame> {
    val a = sampleInput.toMutableList()
    val n = a.size
    val frames = mutableListOf<SortFrame>()
    val levels = 3
    val legend = listOf(CellTone.COMPARE to "Comparing", CellTone.RANGE to "Active run", CellTone.PLACED to "Written")
    var width = 1
    var level = 0
    // Bottom-up: runs of 1 merge into runs of 2, then 4, then 8.
    while (width < n) {
        level++
        for (lo in 0 until n step 2 * width) {
            val mid = lo + width - 1
            val hi = minOf(lo + 2 * width - 1, n - 1)
            val left = a.subList(lo, mid + 1).toList()
            val right = a.subList(mid + 1, hi + 1).toList()
            val out = mutableListOf<Int>()
            var i = 0
            var j = 0
            val window = (lo..hi).toSet()
            fun rows(): List<SortRow> {
                fun run(values: List<Int>, used: Int, head: Boolean) = values.mapIndexed { k, v ->
                    SortCell("$v", if (k < used) CellTone.DIM else if (k == used && head) CellTone.COMPARE else CellTone.IDLE)
                }
                val both = i < left.size && j < right.size
                return listOf(
                    SortRow("LEFT RUN · RIGHT RUN", run(left, i, both) + run(right, j, both), breaks = setOf(left.size)),
                    SortRow("OUTPUT", (0..hi - lo).map { k -> if (k < out.size) SortCell("${out[k]}", CellTone.PLACED) else SortCell("", CellTone.EMPTY) }),
                )
            }
            while (i < left.size && j < right.size) {
                val x = left[i]
                val y = right[j]
                val takeLeft = x <= y
                val written = if (takeLeft) x else y
                val usedLeft = i + (if (takeLeft) 1 else 0)
                val usedRight = j + (if (takeLeft) 0 else 1)
                val after = when {
                    usedLeft == left.size -> "Then ${right.drop(usedRight).joinToString(", ")} ${if (right.size - usedRight == 1) "is" else "are"} copied over, and a[$lo..$hi] is sorted."
                    usedRight == right.size -> "Then ${left.drop(usedLeft).joinToString(", ")} ${if (left.size - usedLeft == 1) "is" else "are"} copied over, and a[$lo..$hi] is sorted."
                    else -> "Each run is sorted, so only the two heads can be the next smallest."
                }
                frames += SortFrame(
                    a.toList(),
                    compared = setOf(lo + i, mid + 1 + j),
                    range = (lo until lo + i).toSet() + (mid + 1 until mid + 1 + j).toSet(),
                    focus = window,
                    status = "${minOf(x, y)} < ${maxOf(x, y)}, so $written is written next.",
                    chips = listOf("run width" to "${hi - lo + 1}", "written" to "${out.size} of ${hi - lo + 1}"),
                    title = "MERGE a[$lo..$hi]",
                    note = "level $level of $levels",
                    headline = listOf(SortSpan("${minOf(x, y)} < ${maxOf(x, y)}", SortTone.COMPARE), SortSpan(", so $written is written next.")),
                    body = after,
                    rows = rows(),
                    legend = legend,
                )
                out += written
                if (takeLeft) i++ else j++
            }
            out += left.drop(i) + right.drop(j)
            i = left.size
            j = right.size
            out.forEachIndexed { k, v -> a[lo + k] = v }
        }
        width *= 2
    }
    frames += SortFrame(
        a.toList(),
        sorted = a.indices.toSet(),
        status = "Sorted after $levels levels of merging.",
        chips = listOf("levels" to "$levels", "comparisons" to "${frames.size}"),
        title = "SORTED a[0..${n - 1}]",
        note = "level $levels of $levels",
        headline = listOf(SortSpan("Sorted", SortTone.PLACED), SortSpan(" after $levels levels of merging.")),
        body = "Each level touches every value once, and there are log n levels, so merge sort is O(n log n) on any input.",
        legend = listOf(CellTone.PLACED to "Sorted"),
    )
    return frames
}

// Lomuto partition, narrated: the last element is the pivot, i marks the end of the ≤ pivot side, and
// j scans. One step per comparison (showing the state before its swap), one per real swap, and one
// for the pivot landing.
private fun quickSortFrames(): List<SortFrame> {
    val b = SortBuilder(sampleInput)
    val sorted = mutableSetOf<Int>()
    var swaps = 0
    var comparisons = 0

    fun pointerMap(vararg marks: Pair<Int, String>): Map<Int, List<String>> =
        marks.filter { it.first >= 0 }.groupBy({ it.first }, { it.second })

    fun partition(lo: Int, hi: Int): Int {
        val window = (lo..hi).toSet()
        val title = "PARTITION a[$lo..$hi]"
        val pivot = b.values[hi]
        var i = lo - 1
        b.frames += SortFrame(
            b.values.toList(),
            sorted = sorted.toSet(),
            status = "Partition a[$lo..$hi] around pivot $pivot",
            pivot = hi,
            focus = window,
            pointers = pointerMap(hi to "pivot"),
            chips = listOf("pivot" to "$pivot", "i" to "$i", "swaps" to "$swaps"),
            title = title,
            headline = listOf(SortSpan("Partition a[$lo..$hi] around pivot "), SortSpan("$pivot", SortTone.PIVOT), SortSpan(".")),
            body = "Lomuto takes the last element as the pivot. i marks the end of the ≤ $pivot side; it starts " +
                "just before index $lo, so that side is empty.",
        )
        for (j in lo until hi) {
            val v = b.values[j]
            comparisons++
            val fits = v <= pivot
            val body = when {
                !fits -> "$v > $pivot, so it stays on the right. Only j moves on."
                i + 1 == j -> "$v ≤ $pivot, so i moves to ${i + 1} and $v joins the left side. It is already in " +
                    "place, so the swap does nothing."
                else -> "$v ≤ $pivot, so i moves to ${i + 1} and $v swaps with a[${i + 1}] = ${b.values[i + 1]}."
            }
            b.frames += SortFrame(
                b.values.toList(),
                compared = setOf(j),
                sorted = sorted.toSet(),
                status = "Compare $v with pivot $pivot",
                pivot = hi,
                lowSide = (lo..i).toSet(),
                highSide = (i + 1 until j).toSet(),
                focus = window,
                pointers = pointerMap(i to "i", j to "j", hi to "pivot"),
                chips = listOf("pivot" to "$pivot", "i" to "$i", "j" to "$j", "swaps" to "$swaps"),
                title = title,
                headline = listOf(
                    SortSpan("Compare "), SortSpan("$v", SortTone.COMPARE),
                    SortSpan(" with pivot "), SortSpan("$pivot", SortTone.PIVOT), SortSpan("."),
                ),
                body = body,
            )
            if (fits) {
                i++
                swaps++
                if (i != j) {
                    val displaced = b.values[i]
                    b.values[j] = displaced
                    b.values[i] = v
                    b.frames += SortFrame(
                        b.values.toList(),
                        compared = setOf(i, j),
                        sorted = sorted.toSet(),
                        status = "Swap $v into index $i",
                        pivot = hi,
                        lowSide = (lo..i).toSet(),
                        highSide = (i + 1..j).toSet(),
                        focus = window,
                        pointers = pointerMap(i to "i", j to "j", hi to "pivot"),
                        chips = listOf("pivot" to "$pivot", "i" to "$i", "j" to "$j", "swaps" to "$swaps"),
                        title = title,
                        headline = listOf(SortSpan("Swap "), SortSpan("$v", SortTone.COMPARE), SortSpan(" onto the left side.")),
                        body = "$v and $displaced trade places: $v joins the ≤ $pivot side at index $i, and $displaced, " +
                            "already known to be bigger than $pivot, moves right to where the scan has been.",
                    )
                }
            }
        }
        val k = i + 1
        swaps++
        b.values[hi] = b.values[k]
        b.values[k] = pivot
        sorted.add(k)
        b.frames += SortFrame(
            b.values.toList(),
            sorted = sorted.toSet(),
            status = "Pivot $pivot lands at index $k — final position.",
            pivot = k,
            lowSide = (lo until k).toSet(),
            highSide = (k + 1..hi).toSet(),
            focus = window,
            pointers = pointerMap(k to "pivot"),
            chips = listOf("pivot" to "$pivot", "index" to "$k", "swaps" to "$swaps"),
            title = title,
            headline = listOf(SortSpan("Pivot "), SortSpan("$pivot", SortTone.PIVOT), SortSpan(" lands at index $k.")),
            body = "Swapping it with a[$k] puts everything ≤ $pivot to its left and everything bigger to its right, " +
                "so index $k is final. " + when {
                    k - 1 > lo && hi > k + 1 -> "Recurse on a[$lo..${k - 1}] and a[${k + 1}..$hi]."
                    k - 1 > lo -> "Recurse on a[$lo..${k - 1}]; the right side is ${if (k == hi) "empty" else "a single element"}."
                    hi > k + 1 -> "Recurse on a[${k + 1}..$hi]; the left side is ${if (k == lo) "empty" else "a single element"}."
                    else -> "Both sides are at most one element, so this branch is done."
                },
        )
        return k
    }

    fun sort(lo: Int, hi: Int) {
        if (lo >= hi) {
            if (lo == hi) sorted.add(lo)
            return
        }
        val p = partition(lo, hi)
        sort(lo, p - 1)
        sort(p + 1, hi)
    }

    sort(0, b.values.lastIndex)
    val n = b.values.size
    b.frames += SortFrame(
        b.values.toList(),
        sorted = b.values.indices.toSet(),
        status = "Sorted — every pivot landed in its final place.",
        chips = listOf("comparisons" to "$comparisons", "swaps" to "$swaps"),
        title = "SORTED a[0..${n - 1}]",
        headline = listOf(SortSpan("Sorted", SortTone.PLACED), SortSpan(": every pivot landed in its final place.")),
        body = "$comparisons comparisons and $swaps swaps for $n values. Quicksort averages O(n log n); a pivot that " +
            "is always the smallest or largest value makes it O(n²).",
    )
    return b.frames
}

private fun heapSortFrames(): List<SortFrame> {
    val b = SortBuilder(sampleInput)
    val n = b.values.size
    val sorted = mutableSetOf<Int>()
    b.frame("Start — build a max-heap, then repeatedly move the root to the end.")

    fun siftDown(root: Int, end: Int) {
        var parent = root
        while (true) {
            val left = 2 * parent + 1
            val right = left + 1
            if (left >= end) break
            var largest = parent
            b.frame("Compare parent ${b.values[parent]} with its children", compared = setOfNotNull(left, right.takeIf { it < end }), range = (0 until end).toSet(), sorted = sorted.toSet())
            if (b.values[left] > b.values[largest]) largest = left
            if (right < end && b.values[right] > b.values[largest]) largest = right
            if (largest == parent) break
            val tmp = b.values[parent]
            b.values[parent] = b.values[largest]
            b.values[largest] = tmp
            b.frame("Sift ${b.values[parent]} up — heap property restored here.", moved = setOf(parent, largest), range = (0 until end).toSet(), sorted = sorted.toSet())
            parent = largest
        }
    }

    for (i in n / 2 - 1 downTo 0) siftDown(i, n)
    b.frame("Max-heap built — the largest element is at the root.", range = (0 until n).toSet())
    for (end in n - 1 downTo 1) {
        val tmp = b.values[0]
        b.values[0] = b.values[end]
        b.values[end] = tmp
        sorted.add(end)
        b.frame("Move root ${b.values[end]} to index $end.", moved = setOf(0, end), sorted = sorted.toSet())
        siftDown(0, end)
    }
    b.done()
    return b.frames
}

private fun countingSortFrames(): List<SortFrame> {
    // Small values so the count array stays meaningful.
    val input = listOf(4, 2, 7, 1, 4, 6, 2, 5)
    val k = 8
    val n = input.size
    val counts = IntArray(k)
    val frames = mutableListOf<SortFrame>()
    val countLegend = listOf(CellTone.COMPARE to "Reading", CellTone.RANGE to "Counted")
    fun countRow(label: String, values: IntArray, tone: (Int) -> CellTone, note: String) =
        SortRow(label, (0 until k).map { SortCell("${values[it]}", tone(it)) }, note = note, headers = (0 until k).map { "$it" })

    input.forEachIndexed { i, v ->
        val before = counts[v]
        counts[v]++
        frames += SortFrame(
            input,
            compared = setOf(i),
            range = (0 until i).toSet(),
            status = "count[$v] becomes ${counts[v]}.",
            pointers = mapOf(i to listOf("i")),
            pointerTones = mapOf("i" to SortTone.COMPARE),
            chips = listOf("value" to "$v", "count[$v]" to "$before → ${counts[v]}"),
            title = "COUNT a[0..${n - 1}]",
            note = "values 0–${k - 1}",
            headline = if (before == 0) {
                listOf(SortSpan("Value $v, so "), SortSpan("count[$v]", SortTone.COMPARE), SortSpan(" becomes 1."))
            } else {
                listOf(SortSpan("Value $v again, so "), SortSpan("count[$v]", SortTone.COMPARE), SortSpan(" becomes ${counts[v]}."))
            },
            body = when {
                i == n - 1 -> "Every value is counted. Next, prefix sums over the counts give each value its output position."
                before == 0 -> "Each value only bumps its own slot. Nothing is compared with anything else."
                else -> "Duplicates just raise the count. The two ${v}s never meet."
            },
            rows = listOf(countRow("COUNT[v]", counts, { if (it == v) CellTone.COMPARE else CellTone.IDLE }, "no comparisons")),
            legend = countLegend,
        )
    }

    val start = IntArray(k)
    for (v in 1 until k) start[v] = start[v - 1] + counts[v - 1]
    val example = (0 until k).first { counts[it] > 1 }
    frames += SortFrame(
        input,
        range = input.indices.toSet(),
        status = "Prefix sums turn counts into start positions.",
        chips = listOf("start[$example]" to "${start[example]}"),
        title = "PREFIX SUMS",
        note = "values 0–${k - 1}",
        headline = listOf(SortSpan("Prefix sums turn counts into "), SortSpan("start positions", SortTone.COMPARE), SortSpan(".")),
        body = "start[v] is how many values are smaller than v. $example starts at ${start[example]} because ${start[example]} smaller values come first.",
        rows = listOf(
            countRow("COUNT[v]", counts, { CellTone.IDLE }, "no comparisons"),
            countRow("START[v]", start, { if (it == example) CellTone.COMPARE else CellTone.RANGE }, "running total"),
        ),
        legend = listOf(CellTone.COMPARE to "Example", CellTone.RANGE to "Start index"),
    )

    val output = IntArray(n)
    var written = 0
    for (v in 0 until k) {
        if (counts[v] == 0) continue
        val from = written
        repeat(counts[v]) { output[written++] = v }
        val slots = (from until written).toList()
        frames += SortFrame(
            output.toList(),
            compared = slots.toSet(),
            sorted = (0 until from).toSet(),
            emptyBars = (written until n).toSet(),
            barMax = k - 1,
            status = "Write $v at ${slots.joinToString(" and ")}.",
            chips = listOf("value" to "$v", "count" to "${counts[v]}", "index" to slots.joinToString(", ")),
            title = "WRITE OUTPUT",
            note = "values 0–${k - 1}",
            headline = if (slots.size == 1) {
                listOf(SortSpan("Write "), SortSpan("$v", SortTone.COMPARE), SortSpan(" at index ${slots[0]}."))
            } else {
                listOf(SortSpan("Write the ${slots.size} "), SortSpan("${v}s", SortTone.COMPARE), SortSpan(" at indices ${slots.joinToString(" and ")}."))
            },
            body = "Reading the counts in value order writes a sorted array without a single comparison.",
            rows = listOf(countRow("COUNT[v]", counts, { if (it == v) CellTone.COMPARE else if (it < v && counts[it] > 0) CellTone.PLACED else CellTone.IDLE }, "no comparisons")),
            legend = listOf(CellTone.COMPARE to "Writing", CellTone.PLACED to "Written"),
        )
    }
    frames += SortFrame(
        output.toList(),
        sorted = output.indices.toSet(),
        barMax = k - 1,
        status = "Sorted in O(n + k).",
        chips = listOf("n" to "$n", "k" to "$k", "comparisons" to "0"),
        title = "SORTED",
        note = "values 0–${k - 1}",
        headline = listOf(SortSpan("Sorted in "), SortSpan("O(n + k)", SortTone.PLACED), SortSpan(".")),
        body = "$n values, $k possible keys, zero comparisons. It only works because the values are small integers.",
        legend = listOf(CellTone.PLACED to "Sorted"),
    )
    return frames
}

private fun radixSortFrames(): List<SortFrame> {
    var order = listOf(170, 45, 75, 90, 802, 24, 2, 66)
    val frames = mutableListOf<SortFrame>()
    val places = listOf("ONES", "TENS", "HUNDREDS")
    val legend = listOf(CellTone.COMPARE to "Placing", CellTone.RANGE to "Placed")
    fun pad(v: Int) = v.toString().padStart(3, '0')
    for (pass in 0 until 3) {
        val exp = listOf(1, 10, 100)[pass]
        val charIndex = 2 - pass
        val buckets = List(10) { mutableListOf<Int>() }
        val rowLabel = if (pass == 0) "INPUT" else "AFTER PASS $pass"
        val place = places[pass].lowercase()
        order.forEachIndexed { i, v ->
            val digit = (v / exp) % 10
            buckets[digit] += v
            frames += SortFrame(
                order,
                showBars = false,
                status = "${pad(v)} has $place digit $digit, so it goes into bucket $digit.",
                chips = listOf("pass" to "${pass + 1} of 3", "digit" to "$digit"),
                title = "PASS ${pass + 1} · ${places[pass]} DIGIT",
                note = "stable",
                headline = listOf(SortSpan(pad(v), SortTone.COMPARE), SortSpan(" has $place digit $digit, so it goes into bucket $digit.")),
                body = if (i == 0) {
                    "Only one digit is read per pass, least significant first."
                } else {
                    "Buckets are read back from 0 to 9. Order within a bucket is kept, and that is what makes the next pass work."
                },
                rows = listOf(SortRow(rowLabel, order.mapIndexed { k, x ->
                    SortCell(pad(x), if (k < i) CellTone.RANGE else if (k == i) CellTone.COMPARE else CellTone.DIM, charIndex)
                })),
                digitBuckets = DigitBuckets(
                    "BUCKETS BY ${places[pass]} DIGIT",
                    digit,
                    buckets.map { b -> b.map { x -> SortCell(pad(x), if (x == v) CellTone.COMPARE else CellTone.RANGE) } },
                ),
                legend = legend,
            )
        }
        order = buckets.flatten()
        frames += SortFrame(
            order,
            showBars = false,
            status = "Reading the buckets from 0 to 9 gives the order for the next pass.",
            chips = listOf("pass" to "${pass + 1} of 3"),
            title = "PASS ${pass + 1} · ${places[pass]} DIGIT",
            note = "stable",
            headline = if (pass < 2) {
                listOf(SortSpan("Reading buckets 0 to 9 gives the "), SortSpan("next order", SortTone.RANGE), SortSpan("."))
            } else {
                listOf(SortSpan("Sorted", SortTone.PLACED), SortSpan(" after 3 passes, one per digit."))
            },
            body = if (pass < 2) {
                "The values are now ordered by their last ${pass + 1} digit${if (pass == 0) "" else "s"}. Ties keep their previous order."
            } else {
                "Each pass is O(n + 10), so the whole sort is O(d·n) for d digits, with no comparisons at all."
            },
            rows = listOf(SortRow("AFTER PASS ${pass + 1}", order.map { SortCell(pad(it), if (pass < 2) CellTone.RANGE else CellTone.PLACED, charIndex) })),
            digitBuckets = DigitBuckets(
                "BUCKETS BY ${places[pass]} DIGIT",
                null,
                buckets.map { b -> b.map { x -> SortCell(pad(x), CellTone.RANGE) } },
            ),
            legend = if (pass < 2) listOf(CellTone.RANGE to "Placed") else listOf(CellTone.PLACED to "Sorted"),
        )
    }
    return frames
}

private fun bucketSortFrames(): List<SortFrame> {
    val input = listOf(33, 12, 77, 41, 5, 68, 29, 54)
    val width = 20
    val count = 4
    val n = input.size
    val buckets = List(count) { mutableListOf<Int>() }
    val output = mutableListOf<Int>()
    val done = mutableSetOf<Int>()
    val frames = mutableListOf<SortFrame>()
    fun range(b: Int) = "${b * width}–${(b + 1) * width - 1}"

    fun frame(
        inputTone: (Int) -> CellTone,
        active: Int?,
        chips: List<Pair<String, String>>,
        headline: List<SortSpan>,
        body: String,
        legend: List<Pair<CellTone, String>>,
    ) {
        frames += SortFrame(
            input,
            showBars = false,
            status = headline.joinToString("") { it.text },
            chips = chips,
            title = "$count BUCKETS · WIDTH $width",
            note = "values 0–${count * width - 1}",
            headline = headline,
            body = body,
            rows = listOf(
                SortRow("INPUT", input.mapIndexed { i, v -> SortCell("$v", inputTone(i)) }),
                SortRow("OUTPUT", (0 until n).map { k -> if (k < output.size) SortCell("${output[k]}", CellTone.PLACED) else SortCell("", CellTone.EMPTY) }),
            ),
            buckets = buckets.mapIndexed { b, values ->
                BucketBox(
                    range(b),
                    values.toList(),
                    when {
                        b in done -> CellTone.PLACED
                        b == active -> CellTone.COMPARE
                        else -> CellTone.IDLE
                    },
                    b == active,
                )
            },
            barMax = count * width - 1,
            legend = legend,
        )
    }

    input.forEachIndexed { i, v ->
        val b = v / width
        buckets[b] += v
        frame(
            { if (it < i) CellTone.DIM else if (it == i) CellTone.COMPARE else CellTone.IDLE },
            b,
            listOf("value" to "$v", "bucket" to "$v ÷ $width = $b"),
            listOf(SortSpan("$v", SortTone.COMPARE), SortSpan(" goes into bucket ${range(b)}.")),
            if (i == n - 1) "Every value is placed in one pass, without comparing any two of them." else "The bucket is value ÷ $width, so placing needs no comparisons.",
            listOf(CellTone.COMPARE to "Placing"),
        )
    }

    val sortLegend = listOf(CellTone.COMPARE to "Comparing", CellTone.PLACED to "Sorted and written")
    buckets.forEachIndexed { b, values ->
        if (values.size > 1) {
            val x = values[0]
            val y = values[1]
            val swap = x > y
            frame(
                { CellTone.IDLE },
                b,
                listOf("bucket" to "${b + 1} of $count"),
                listOf(
                    SortSpan("In bucket ${range(b)}, "),
                    SortSpan(if (swap) "$x > $y" else "$x < $y", SortTone.COMPARE),
                    SortSpan(if (swap) ", so they swap." else ", so they stay."),
                ),
                "Each bucket is sorted on its own, then joined in order.",
                sortLegend,
            )
            values.sort()
        }
        output += values
        done += b
        frame(
            { CellTone.IDLE },
            null,
            listOf("bucket" to "${b + 1} of $count", "written" to "${output.size} of $n"),
            listOf(SortSpan("Bucket ${range(b)} is written out: "), SortSpan(values.joinToString(", "), SortTone.PLACED), SortSpan(".")),
            if (b == count - 1) "Buckets cover increasing ranges, so joining them in order is already sorted." else "Every value in a later bucket is bigger, so nothing written moves again.",
            sortLegend,
        )
    }
    return frames
}

// ── Interview-prep pattern guides ────────────────────────────────────────────

// Merge sort, but the merge is counting: every element taken from the right half while the left half
// still holds values proves that many inversions at once.
private val inversionInput = listOf(5, 3, 8, 1, 9, 2)

private fun divideConquerPatternFrames(): List<SortFrame> {
    val b = SortBuilder(inversionInput)
    var inversions = 0

    b.frame(
        "Count the pairs that are out of order in ${inversionInput.joinToString(", ")}. Comparing every pair is " +
            "O(n²); the answers that span a split fall out of a merge sort that has to happen anyway.",
    )

    fun sort(lo: Int, hi: Int) {
        if (hi - lo < 2) return
        val mid = (lo + hi) / 2
        b.frame(
            "Split [$lo, ${hi - 1}] at $mid. Inversions inside each half are counted recursively; only the pairs " +
                "that straddle the split are left for the combine step.",
            range = (lo until hi).toSet(),
            compared = setOf(mid),
        )
        sort(lo, mid)
        sort(mid, hi)

        val merged = mutableListOf<Int>()
        var i = lo
        var j = mid
        while (i < mid || j < hi) {
            val takeLeft = j >= hi || (i < mid && b.values[i] <= b.values[j])
            if (takeLeft) {
                merged += b.values[i]
                i++
            } else {
                val crossing = mid - i
                inversions += crossing
                b.frame(
                    "${b.values[j]} moves ahead of ${if (crossing == 0) "nothing" else "${crossing} still-unmerged left-half value(s)"}" +
                        if (crossing == 0) " — no inversion here." else ": that is $crossing inversion(s) at once, " +
                            "found without comparing them individually. Running total $inversions.",
                    compared = setOf(j),
                    moved = (i until mid).toSet(),
                    range = (lo until hi).toSet(),
                )
                merged += b.values[j]
                j++
            }
        }
        merged.forEachIndexed { offset, v -> b.values[lo + offset] = v }
        b.frame(
            "[$lo, ${hi - 1}] merged: ${merged.joinToString(", ")}. Linear combine work per level, log n levels — " +
                "T(n) = 2T(n/2) + O(n) = O(n log n).",
            range = (lo until hi).toSet(),
            sorted = (lo until hi).toSet(),
        )
    }

    sort(0, b.values.size)
    b.done("Sorted, and $inversions inversions counted along the way. The sort was a side effect — the combine step was the algorithm.")
    return b.frames
}

// Three-way partition. Values 1 / 2 / 3 stand in for the flag's three colours.
private val dutchFlagInput = listOf(3, 1, 3, 2, 2, 1, 3, 1, 2)

private fun dutchFlagPatternFrames(): List<SortFrame> {
    val b = SortBuilder(dutchFlagInput)
    val pivot = 2
    var low = 0
    var mid = 0
    var high = b.values.lastIndex

    fun classified() = (0 until low).toSet() + ((high + 1)..b.values.lastIndex).toSet()

    b.frame(
        "Three categories — below $pivot, equal to $pivot, above $pivot — sorted in one pass with no extra array. " +
            "low and high mark the settled regions; mid scans the unclassified middle.",
        range = (mid..high).toSet(),
    )

    while (mid <= high) {
        val v = b.values[mid]
        when {
            v < pivot -> {
                b.values[mid] = b.values[low]
                b.values[low] = v
                b.frame(
                    "${v} < $pivot: swap it down to index $low. The value it displaced was already scanned and known " +
                        "to equal $pivot, so mid can advance too.",
                    moved = setOf(low, mid),
                    sorted = classified(),
                    range = (mid + 1..high).toSet(),
                )
                low++
                mid++
            }
            v == pivot -> {
                b.frame(
                    "$v == $pivot: it already belongs in the middle region, so only mid advances.",
                    compared = setOf(mid),
                    sorted = classified(),
                    range = (mid..high).toSet(),
                )
                mid++
            }
            else -> {
                b.values[mid] = b.values[high]
                b.values[high] = v
                b.frame(
                    "$v > $pivot: swap it up to index $high. mid does *not* advance — the value swapped in has never " +
                        "been looked at, and advancing here is the classic bug.",
                    moved = setOf(mid, high),
                    sorted = classified(),
                    range = (mid..high - 1).toSet(),
                )
                high--
            }
        }
    }

    b.done(
        "One pass, ${b.values.size} elements, O(1) extra space. Two pointers that partition rather than converge — " +
            "the same engine quicksort uses, and the reason it handles duplicate keys without quadratic blowup.",
    )
    return b.frames
}

// Shortest-job-first, proved by the exchange itself: swap any adjacent out-of-order pair and watch
// the objective fall.
private val greedyJobs = listOf(4, 1, 7, 2)

private fun greedyExchangePatternFrames(): List<SortFrame> {
    val b = SortBuilder(greedyJobs)

    fun cost() = b.values.mapIndexed { i, d -> (b.values.size - i) * d }.sum()

    fun costBreakdown() = b.values
        .runningFold(0) { acc, d -> acc + d }
        .drop(1)
        .joinToString(" + ")

    b.frame(
        "Four jobs on one machine; minimise the total time customers wait. Order ${b.values.joinToString(", ")} " +
            "gives completion times ${costBreakdown()} = ${cost()}. A job's duration is paid by everyone still queued " +
            "behind it, which is the hint.",
        range = b.values.indices.toSet(),
    )

    var swapped = true
    var exchanges = 0
    while (swapped) {
        swapped = false
        for (i in 0 until b.values.lastIndex) {
            if (b.values[i] > b.values[i + 1]) {
                val before = cost()
                val longer = b.values[i]
                val shorter = b.values[i + 1]
                b.values[i] = shorter
                b.values[i + 1] = longer
                exchanges++
                swapped = true
                b.frame(
                    "Adjacent pair $longer before $shorter is out of greedy order. Exchange them: everything outside " +
                        "the pair is unaffected, and the total drops from $before to ${cost()} — a difference of " +
                        "${before - cost()}, exactly $longer − $shorter. Never worse, so no optimal solution is lost.",
                    moved = setOf(i, i + 1),
                    range = b.values.indices.toSet(),
                )
            }
        }
    }

    b.frame(
        "No out-of-order adjacent pair remains, so the order is sorted by duration and the cost is ${cost()} after " +
            "$exchanges exchange(s). That is the whole argument: any optimal schedule can be rewritten into this one " +
            "one swap at a time without getting worse, so this one is optimal too.",
        sorted = b.values.indices.toSet(),
    )
    b.done(
        "Shortest-job-first, justified rather than guessed. When the same exchange *can* make things worse — items " +
            "with weights and a capacity, say — the argument fails and the answer is DP instead.",
    )
    return b.frames
}

private val sortConfigs = mapOf(
    "divide_conquer_pattern" to SortConfig(
        intro = "Merge sort counting inversions. The interesting frames are the merges: taking one right-half value " +
            "settles several pairs at once, which is the cross-boundary work the pattern is really about.",
        rangeLabel = "Current range",
        build = ::divideConquerPatternFrames,
    ),
    "dutch_flag_pattern" to SortConfig(
        intro = "Three-way partition around 2, values 1/2/3 standing in for the flag's colours. Watch mid stall " +
            "after a swap with high — the incoming value has not been classified yet.",
        rangeLabel = "Unclassified",
        build = ::dutchFlagPatternFrames,
    ),
    "greedy_exchange_pattern" to SortConfig(
        intro = "The exchange argument run as an experiment: four jobs, and every adjacent swap that puts the " +
            "shorter one first lowers the total wait. The proof is the algorithm.",
        rangeLabel = "Schedule",
        build = ::greedyExchangePatternFrames,
    ),
    "bubble_sort" to SortConfig(
        intro = "Bubble sort on 8 values. Each pass walks the array swapping out-of-order neighbours, so the largest remaining value bubbles to the end — that tail is locked in green.",
        rangeLabel = null,
        build = ::bubbleSortFrames,
    ),
    "selection_sort" to SortConfig(
        intro = "Selection sort scans the unsorted region for its minimum, then swaps that value into place. Exactly n−1 swaps regardless of input order.",
        rangeLabel = null,
        build = ::selectionSortFrames,
    ),
    "insertion_sort" to SortConfig(
        intro = "Insertion sort keeps a sorted prefix (blue) and inserts the next element into it by shifting larger values right. Near-sorted input finishes in almost linear time.",
        rangeLabel = "Sorted prefix",
        build = ::insertionSortFrames,
    ),
    "merge_sort" to SortConfig(
        intro = "Merge sort splits down to single elements, then merges sorted runs pairwise. The blue window is the run being merged right now.",
        rangeLabel = "Active run",
        build = ::mergeSortFrames,
    ),
    "quick_sort" to SortConfig(
        intro = "Quicksort partitions the blue window around a pivot (the last element), locking the pivot into its final index, then recurses on each side.",
        rangeLabel = "Partition",
        build = ::quickSortFrames,
        variant = "Lomuto",
    ),
    "heap_sort" to SortConfig(
        intro = "Heap sort first sifts the array into a max-heap, then repeatedly swaps the root to the end and re-heapifies the shrinking blue region.",
        rangeLabel = "Heap region",
        build = ::heapSortFrames,
    ),
    "counting_sort" to SortConfig(
        intro = "Counting sort never compares two elements. It tallies how often each value occurs, then writes the values back in ascending order — O(n + k).",
        rangeLabel = null,
        build = ::countingSortFrames,
    ),
    "radix_sort" to SortConfig(
        intro = "Radix sort runs one stable bucket pass per digit, least significant first. After the last pass the array is fully ordered.",
        rangeLabel = "Pass result",
        build = ::radixSortFrames,
    ),
    "bucket_sort" to SortConfig(
        intro = "Bucket sort scatters 8 values into 8 range-buckets, sorts each with insertion sort, then concatenates. The blue window is the bucket just written back.",
        rangeLabel = "Bucket written",
        build = ::bucketSortFrames,
    ),
)

private fun sortConfigFor(topicId: String): SortConfig =
    sortConfigs[topicId] ?: sortConfigs.getValue("bubble_sort")

// Exposed so PatternCoverageTest can tell a topic that configured this widget from one that only
// inherits the fallback above.
internal val sortingVisualizerTopicIds: Set<String> get() = sortConfigs.keys

/**
 * Every highlight in a frame is a bar index, and the renderer silently drops one that is off the end
 * — so a partition or merge whose bounds are one past the array looks fine until you compare it with
 * the status line. Checked here instead.
 */
internal fun sortingFrameCount(topicId: String): Int {
    val frames = sortConfigFor(topicId).build()
    frames.forEachIndexed { index, frame ->
        require(frame.values.isNotEmpty()) { "$topicId frame $index draws no bars" }
        require(frame.status.isNotBlank()) { "$topicId frame $index has no status line" }
        (
            frame.compared + frame.moved + frame.sorted + frame.range + frame.lowSide + frame.highSide +
                frame.pointers.keys + listOfNotNull(frame.pivot) + frame.focus.orEmpty()
            ).forEach { bar ->
            require(bar in frame.values.indices) {
                "$topicId frame $index highlights bar $bar of ${frame.values.size}"
            }
        }
    }
    return frames.size
}

// Built to docs/ios-design/Simulations iOS.html: bars in a card under a heading naming the range being
// worked on, index and pointer labels under them, rows of cells for whatever the sort keeps beside the
// array (runs, counts, buckets, output), then chips, a headline whose numbers take their bar's colour,
// and a sentence on what the step decided. Sorts without narration keep the same card and bars, with
// their one-line status as the headline.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortingVisualizerSection(topicId: String) {
    val config = remember(topicId) { sortConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 450f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val peak = remember(frames) { frames.first().values.max() }
    val narrated = remember(frames) { frames.any { it.headline != null } }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // The mock's yellow and violet are for a dark card; on white they need to be darker as text.
    val toneColors = mapOf(
        SortTone.COMPARE to if (dark) ComparedBar else Color(0xFFB7791F),
        SortTone.PIVOT to if (dark) Color(0xFFA78BFA) else PivotBar,
        SortTone.PLACED to SortedBar,
        SortTone.RANGE to RangeBar,
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        if (!narrated) {
            LabIntro(config.intro)
            Spacer(modifier = Modifier.height(12.dp))
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val note = frame.note ?: config.variant
                if (frame.title != null || note != null) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            frame.title.orEmpty(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                            color = muted,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = muted, maxLines = 1) }
                    }
                }

                if (frame.showBars) {
                    BarChart(frame = frame, peak = frame.barMax ?: peak, toneColors = toneColors, modifier = Modifier.padding(top = 12.dp))
                }

                // Bucket sort draws its input, then the buckets, then its output.
                frame.rows.forEachIndexed { i, row ->
                    if (frame.buckets.isNotEmpty() && i == 1) BucketBoxes(frame.buckets, frame.barMax ?: peak)
                    SortCellRow(row, dark)
                }
                frame.digitBuckets?.let { DigitBucketGrid(it, dark) }

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val legend = frame.legend
                    when {
                        legend != null -> legend.forEach { (tone, label) -> SortLegend(cellFill(tone, muted), label, dashed = tone == CellTone.EMPTY) }
                        narrated -> {
                            SortLegend(ComparedBar, "Comparing")
                            SortLegend(RangeBar, "≤ pivot")
                            SortLegend(muted.copy(alpha = 0.4f), "> pivot")
                            SortLegend(PivotBar, "Pivot")
                            SortLegend(SortedBar, "Placed")
                            SortLegend(muted.copy(alpha = 0.16f), "Not scanned")
                        }
                        else -> {
                            SortLegend(ComparedBar, "Comparing")
                            SortLegend(MovedBar, "Moving")
                            SortLegend(SortedBar, "Sorted")
                            config.rangeLabel?.let { SortLegend(RangeBar, it) }
                        }
                    }
                }
            }
        }

        if (frame.chips.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                frame.chips.forEach { (label, value) -> SortChip(label, value) }
            }
        }

        val headline = frame.headline
        if (headline != null) {
            Text(
                buildAnnotatedString {
                    headline.forEach { span ->
                        val tone = span.tone
                        if (tone == null) append(span.text) else withStyle(SpanStyle(color = toneColors.getValue(tone))) { append(span.text) }
                    }
                },
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                color = onSurface,
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            // Sorts without narration show their status line as the headline, a size down — several
            // run to two sentences.
            Text(
                frame.status,
                fontSize = 16.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                color = onSurface,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        frame.body?.let { body ->
            Text(body, fontSize = 15.sp, lineHeight = 22.sp, color = muted, modifier = Modifier.padding(top = 8.dp))
        }

        PlaybackTransport(playback, captions = frames.map { it.status })
    }
}

private fun cellFill(tone: CellTone, muted: Color): Color = when (tone) {
    CellTone.IDLE -> muted.copy(alpha = 0.2f)
    CellTone.DIM -> muted.copy(alpha = 0.09f)
    CellTone.COMPARE -> ComparedBar
    CellTone.RANGE -> RangeBar
    CellTone.PLACED -> SortedBar
    CellTone.PIVOT -> PivotBar
    CellTone.EMPTY -> Color.Transparent
}

@Composable
private fun cellInk(tone: CellTone): Color = when (tone) {
    CellTone.COMPARE -> Color(0xFF1F1A0A)
    CellTone.RANGE, CellTone.PLACED, CellTone.PIVOT -> Color.White
    CellTone.DIM -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    else -> MaterialTheme.colorScheme.onSurface
}

// A dashed slot still to fill.
private fun Modifier.dashedSlot(color: Color, radius: Float = 8f): Modifier = drawBehind {
    drawRoundRect(
        color,
        cornerRadius = CornerRadius(radius.dp.toPx()),
        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

@Composable
private fun SortSectionLabel(label: String, note: String?) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, color = muted, modifier = Modifier.weight(1f))
        note?.let { Text(it, fontSize = 13.sp, color = muted, maxLines = 1) }
    }
}

@Composable
private fun SortCellRow(row: SortRow, dark: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    row.label?.let { SortSectionLabel(it, row.note) }
    row.headers?.let { headers ->
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            headers.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = muted, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) }
        }
    }
    Row(modifier = Modifier.fillMaxWidth().height(44.dp)) {
        row.cells.forEachIndexed { i, cell ->
            if (i > 0) Spacer(Modifier.width(if (i in row.breaks) 14.dp else 6.dp))
            val shape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(cellFill(cell.tone, muted), shape)
                    .then(if (cell.tone == CellTone.EMPTY) Modifier.dashedSlot(muted.copy(alpha = 0.35f), 10f) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                val ink = cellInk(cell.tone)
                val digit = cell.digit
                Text(
                    if (digit == null) {
                        AnnotatedString(cell.text)
                    } else {
                        buildAnnotatedString {
                            cell.text.forEachIndexed { k, ch ->
                                withStyle(SpanStyle(color = if (k == digit) ink else ink.copy(alpha = 0.45f))) { append(ch) }
                            }
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = if (cell.text.length > 2) 14.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    maxLines = 1,
                )
            }
        }
    }
}

// Ten columns, one per digit, each stacking the values dropped into it so far.
@Composable
private fun DigitBucketGrid(grid: DigitBuckets, dark: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val yellow = if (dark) ComparedBar else Color(0xFFB7791F)
    SortSectionLabel(grid.label, null)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        grid.columns.forEachIndexed { d, _ ->
            Text(
                "$d",
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = if (d == grid.active) FontWeight.Bold else FontWeight.Normal,
                color = if (d == grid.active) yellow else muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    val tallest = maxOf(2, grid.columns.maxOf { it.size })
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        grid.columns.forEach { column ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height((tallest * 30 + 8).dp)
                    .background(muted.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .padding(3.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                column.forEach { cell ->
                    Box(
                        modifier = Modifier.fillMaxWidth().height(26.dp).background(cellFill(cell.tone, muted), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(cell.text, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = cellInk(cell.tone), maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

// Bucket sort's range boxes: a caption, then each value as a bar sized against the whole value range.
@Composable
private fun BucketBoxes(boxes: List<BucketBox>, max: Int) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val yellow = if (dark) ComparedBar else Color(0xFFB7791F)
    SortSectionLabel("BUCKETS", null)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        boxes.forEach { box ->
            val shape = RoundedCornerShape(12.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(150.dp)
                    .background(muted.copy(alpha = 0.08f), shape)
                    .then(if (box.active) Modifier.border(1.5.dp, ComparedBar, shape) else Modifier)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(box.range, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = if (box.active) yellow else muted)
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    box.values.forEach { v ->
                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$v", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (box.tone == CellTone.IDLE) muted else MaterialTheme.colorScheme.onSurface)
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .fillMaxWidth()
                                    .height((6 + 60f * v / max).dp)
                                    .background(cellFill(box.tone, muted), RoundedCornerShape(5.dp)),
                            )
                        }
                    }
                    // Keep one value from stretching across the whole box.
                    repeat(maxOf(0, 2 - box.values.size)) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SortLegend(color: Color, label: String, dashed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .then(if (dashed) Modifier.dashedSlot(ComparedBar, 3f) else Modifier.background(color, RoundedCornerShape(3.dp))),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}

@Composable
private fun SortChip(label: String, value: String) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private val BarAreaHeight = 200.dp

@Composable
private fun BarChart(frame: SortFrame, peak: Int, toneColors: Map<SortTone, Color>, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val gap = 8.dp
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(BarAreaHeight),
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Bottom,
        ) {
            frame.values.forEachIndexed { index, value ->
                val faded = frame.focus != null && index !in frame.focus && index !in frame.sorted
                val ghost = index == frame.ghost
                val empty = index in frame.emptyBars
                val color = when {
                    index == frame.pivot -> PivotBar
                    index in frame.moved -> MovedBar
                    index in frame.compared -> ComparedBar
                    index in frame.sorted -> SortedBar
                    index in frame.lowSide -> RangeBar
                    index in frame.highSide -> muted.copy(alpha = 0.4f)
                    index in frame.range -> RangeBar
                    else -> null
                }
                val shown = if (ghost) frame.ghostValue ?: value else value
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!empty) {
                        Text(
                            shown.toString(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                ghost -> toneColors.getValue(SortTone.COMPARE)
                                faded -> muted.copy(alpha = 0.45f)
                                color != null -> onSurface
                                else -> muted
                            },
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    val height = if (empty) 34.dp else (10 + (150f * shown / peak)).dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 10dp floor keeps small values visible as bars rather than slivers.
                            .height(height)
                            .then(
                                when {
                                    ghost -> Modifier.dashedSlot(ComparedBar)
                                    empty -> Modifier.dashedSlot(muted.copy(alpha = 0.35f))
                                    else -> Modifier.background(color ?: muted.copy(alpha = if (faded) 0.08f else 0.2f), RoundedCornerShape(8.dp))
                                },
                            ),
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            frame.values.indices.forEach { index ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$index", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = muted)
                    val labels = frame.pointers[index].orEmpty()
                    if (labels.isNotEmpty()) {
                        Text(
                            buildAnnotatedString {
                                labels.forEachIndexed { i, label ->
                                    if (i > 0) append("·")
                                    val color = if (label in frame.pointerTones) {
                                        frame.pointerTones[label]?.let { toneColors.getValue(it) } ?: onSurface
                                    } else {
                                        when (label) {
                                            "j" -> toneColors.getValue(SortTone.COMPARE)
                                            "pivot" -> toneColors.getValue(SortTone.PIVOT)
                                            else -> PointerColors[label] ?: muted
                                        }
                                    }
                                    withStyle(SpanStyle(color = color)) { append(label) }
                                }
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
