package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors

// ── Sorting visualizer ───────────────────────────────────────────────────────
// One reusable bar-chart player for every sorting algorithm: the algorithm runs once up front and
// records a snapshot per comparison / swap / write, exactly like GraphSimulationSection precomputes
// its BFS/DFS sequence. PlaybackTransport then steps through the frames.
//
// Roles colour the bars: compared pair, the element being moved, the region already sorted, and a
// secondary "range" highlight (merge window, current pivot partition, heap boundary, digit bucket).

private class SortFrame(
    val values: List<Int>,
    val compared: Set<Int> = emptySet(),
    val moved: Set<Int> = emptySet(),
    val sorted: Set<Int> = emptySet(),
    val range: Set<Int> = emptySet(),
    val status: String,
)

private class SortConfig(
    val intro: String,
    val rangeLabel: String?,
    val build: () -> List<SortFrame>,
)

private val ComparedBar = Color(0xFFFACC15)
private val MovedBar = Color(0xFFF97316)
private val SortedBar = SimColors.Green
private val RangeBar = Color(0xFF3B82F6)

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
    val b = SortBuilder(sampleInput)
    val n = b.values.size
    val sorted = mutableSetOf<Int>()
    b.frame("Start — compare each adjacent pair, bubbling the largest value to the end.")
    for (pass in 0 until n - 1) {
        var swapped = false
        for (i in 0 until n - 1 - pass) {
            b.frame("Compare ${b.values[i]} and ${b.values[i + 1]}", compared = setOf(i, i + 1), sorted = sorted.toSet())
            if (b.values[i] > b.values[i + 1]) {
                val tmp = b.values[i]
                b.values[i] = b.values[i + 1]
                b.values[i + 1] = tmp
                swapped = true
                b.frame("${b.values[i + 1]} > ${b.values[i]} → swap", moved = setOf(i, i + 1), sorted = sorted.toSet())
            }
        }
        sorted.add(n - 1 - pass)
        b.frame("Pass ${pass + 1} done — ${b.values[n - 1 - pass]} is in place.", sorted = sorted.toSet())
        if (!swapped) break
    }
    b.done()
    return b.frames
}

private fun selectionSortFrames(): List<SortFrame> {
    val b = SortBuilder(sampleInput)
    val n = b.values.size
    val sorted = mutableSetOf<Int>()
    b.frame("Start — repeatedly select the smallest remaining element.")
    for (i in 0 until n - 1) {
        var min = i
        b.frame("Assume ${b.values[i]} is the minimum of the unsorted part.", moved = setOf(i), sorted = sorted.toSet())
        for (j in i + 1 until n) {
            b.frame("Compare ${b.values[j]} with current min ${b.values[min]}", compared = setOf(j, min), sorted = sorted.toSet())
            if (b.values[j] < b.values[min]) {
                min = j
                b.frame("New minimum: ${b.values[min]}", moved = setOf(min), sorted = sorted.toSet())
            }
        }
        if (min != i) {
            val tmp = b.values[i]
            b.values[i] = b.values[min]
            b.values[min] = tmp
            b.frame("Swap ${b.values[i]} into position $i", moved = setOf(i, min), sorted = sorted.toSet())
        }
        sorted.add(i)
        b.frame("Position $i fixed at ${b.values[i]}.", sorted = sorted.toSet())
    }
    b.done()
    return b.frames
}

private fun insertionSortFrames(): List<SortFrame> {
    val b = SortBuilder(sampleInput)
    val n = b.values.size
    b.frame("Start — grow a sorted prefix one element at a time.")
    for (i in 1 until n) {
        val key = b.values[i]
        var j = i - 1
        b.frame("Take ${key} and insert it into the sorted prefix.", moved = setOf(i), range = (0 until i).toSet())
        while (j >= 0 && b.values[j] > key) {
            b.frame("${b.values[j]} > $key → shift right", compared = setOf(j, j + 1), range = (0 until i).toSet())
            b.values[j + 1] = b.values[j]
            j--
        }
        b.values[j + 1] = key
        b.frame("Place $key at index ${j + 1}", moved = setOf(j + 1), sorted = (0..i).toSet())
    }
    b.done()
    return b.frames
}

private fun mergeSortFrames(): List<SortFrame> {
    val b = SortBuilder(sampleInput)
    b.frame("Start — split the array down to single elements, then merge sorted runs back up.")

    fun merge(lo: Int, mid: Int, hi: Int) {
        val window = (lo..hi).toSet()
        val merged = mutableListOf<Int>()
        var i = lo
        var j = mid + 1
        while (i <= mid && j <= hi) {
            b.frame("Merge [$lo..$hi]: compare ${b.values[i]} and ${b.values[j]}", compared = setOf(i, j), range = window)
            if (b.values[i] <= b.values[j]) merged.add(b.values[i++]) else merged.add(b.values[j++])
        }
        while (i <= mid) merged.add(b.values[i++])
        while (j <= hi) merged.add(b.values[j++])
        merged.forEachIndexed { offset, value -> b.values[lo + offset] = value }
        b.frame("Run [$lo..$hi] merged into sorted order.", sorted = window, range = window)
    }

    fun sort(lo: Int, hi: Int) {
        if (lo >= hi) return
        val mid = (lo + hi) / 2
        b.frame("Split [$lo..$hi] at $mid", range = (lo..hi).toSet())
        sort(lo, mid)
        sort(mid + 1, hi)
        merge(lo, mid, hi)
    }

    sort(0, b.values.lastIndex)
    b.done()
    return b.frames
}

private fun quickSortFrames(): List<SortFrame> {
    val b = SortBuilder(sampleInput)
    val sorted = mutableSetOf<Int>()
    b.frame("Start — partition around a pivot (last element), then recurse on both sides.")

    fun partition(lo: Int, hi: Int): Int {
        val window = (lo..hi).toSet()
        val pivot = b.values[hi]
        b.frame("Partition [$lo..$hi] around pivot $pivot", moved = setOf(hi), range = window)
        var i = lo
        for (j in lo until hi) {
            b.frame("Compare ${b.values[j]} with pivot $pivot", compared = setOf(j, hi), range = window, sorted = sorted.toSet())
            if (b.values[j] < pivot) {
                val tmp = b.values[i]
                b.values[i] = b.values[j]
                b.values[j] = tmp
                b.frame("${b.values[i]} < $pivot → move left", moved = setOf(i, j), range = window, sorted = sorted.toSet())
                i++
            }
        }
        val tmp = b.values[i]
        b.values[i] = b.values[hi]
        b.values[hi] = tmp
        sorted.add(i)
        b.frame("Pivot $pivot lands at index $i — final position.", sorted = sorted.toSet(), range = window)
        return i
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
    b.done()
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
    // Small values so the count array stays meaningful in the status line.
    val input = listOf(4, 2, 7, 1, 4, 6, 2, 5)
    val b = SortBuilder(input)
    val max = input.max()
    val counts = IntArray(max + 1)
    b.frame("Start — no comparisons: count how often each value occurs.")
    input.forEachIndexed { index, value ->
        counts[value]++
        b.frame("count[$value] = ${counts[value]}", compared = setOf(index))
    }
    b.frame("Counts tallied: ${counts.indices.filter { counts[it] > 0 }.joinToString { "$it×${counts[it]}" }}")
    var write = 0
    for (value in 0..max) {
        repeat(counts[value]) {
            b.values[write] = value
            b.frame("Write $value to index $write", moved = setOf(write), sorted = (0 until write).toSet())
            write++
        }
    }
    b.done()
    return b.frames
}

private fun radixSortFrames(): List<SortFrame> {
    val input = listOf(170, 45, 75, 90, 802, 24, 2, 66)
    val b = SortBuilder(input)
    b.frame("Start — sort by each digit position, least significant first, using a stable pass.")
    var exp = 1
    while (input.max() / exp > 0) {
        val place = when (exp) {
            1 -> "ones"
            10 -> "tens"
            else -> "hundreds"
        }
        val buckets = List(10) { mutableListOf<Int>() }
        b.values.forEachIndexed { index, value ->
            val digit = (value / exp) % 10
            buckets[digit].add(value)
            b.frame("$place pass: $value → bucket $digit", compared = setOf(index))
        }
        val flattened = buckets.flatten()
        flattened.forEachIndexed { index, value -> b.values[index] = value }
        b.frame("Buckets emptied back in order — array is now sorted by its $place digit.", range = b.values.indices.toSet())
        exp *= 10
    }
    b.done()
    return b.frames
}

private fun bucketSortFrames(): List<SortFrame> {
    // Values spread across 0..79 so the scatter into 8 buckets stays roughly even.
    val input = listOf(29, 5, 68, 41, 12, 77, 33, 54)
    val b = SortBuilder(input)
    val n = input.size
    val min = input.min()
    val max = input.max()
    val buckets = List(n) { mutableListOf<Int>() }

    b.frame("Start — $n buckets for $n values, each covering an equal slice of the range $min–$max.")

    input.forEachIndexed { index, value ->
        val slot = ((value - min).toLong() * n / (max - min + 1L)).toInt()
        buckets[slot].add(value)
        b.frame(
            "$value → bucket $slot (range ${min + slot * (max - min + 1) / n}–${min + (slot + 1) * (max - min + 1) / n - 1}). " +
                "One pass, no comparisons yet.",
            compared = setOf(index),
        )
    }

    b.frame(
        "Scatter done: " + buckets.mapIndexed { i, bucket -> "b$i=${bucket.ifEmpty { "–" }}" }
            .joinToString(" ") { it.replace(" ", "") },
    )

    var write = 0
    buckets.forEachIndexed { slot, bucket ->
        if (bucket.isEmpty()) return@forEachIndexed
        // Insertion sort inside the bucket — cheap because buckets are tiny.
        for (i in 1 until bucket.size) {
            val value = bucket[i]
            var j = i - 1
            while (j >= 0 && bucket[j] > value) {
                bucket[j + 1] = bucket[j]
                j--
            }
            bucket[j + 1] = value
        }
        val start = write
        for (value in bucket) {
            b.values[write] = value
            write++
        }
        b.frame(
            "Bucket $slot sorted internally (${bucket.joinToString(", ")}) and copied out. Buckets are already " +
                "in value order, so concatenating them needs no merge.",
            moved = (start until write).toSet(),
            sorted = (0 until start).toSet(),
            range = (start until write).toSet(),
        )
    }

    b.done("Sorted. Linear on evenly spread input — but pile every value into one bucket and this degrades to the insertion sort inside it.")
    return b.frames
}

private val sortConfigs = mapOf(
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

@Composable
fun SortingVisualizerSection(topicId: String) {
    val config = remember(topicId) { sortConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 450f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val peak = remember(frames) { frames.first().values.max() }

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

            BarChart(frame = frame, peak = peak, modifier = Modifier.padding(top = 14.dp))

            Text(
                frame.status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SortLegend(ComparedBar, "Comparing")
                SortLegend(MovedBar, "Moving")
                SortLegend(SortedBar, "Sorted")
                config.rangeLabel?.let { SortLegend(RangeBar, it) }
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun SortLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

@Composable
private fun BarChart(frame: SortFrame, peak: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(170.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        frame.values.forEachIndexed { index, value ->
            val color = when {
                index in frame.moved -> MovedBar
                index in frame.compared -> ComparedBar
                index in frame.sorted -> SortedBar
                index in frame.range -> RangeBar
                else -> RangeBar.copy(alpha = 0.28f)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    value.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // 18dp floor keeps small values visible as bars rather than slivers.
                        .height((18 + (128f * value / peak)).dp)
                        .background(color, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                )
            }
        }
    }
}
