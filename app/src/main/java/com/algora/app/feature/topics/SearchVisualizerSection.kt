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

// ── Search visualizer ────────────────────────────────────────────────────────
// Same precomputed-snapshot model as the sorting player, but the array is fixed and what animates is
// the search state: the live window (lo..hi / current block), the probe being tested, eliminated
// cells, and the hit. One config per search algorithm, resolved by topicId.

private class SearchFrame(
    val probe: Int?,
    val window: Set<Int>,
    val eliminated: Set<Int>,
    val found: Int?,
    val status: String,
)

private class SearchConfig(
    val values: List<Int>,
    val target: Int,
    val intro: String,
    val windowLabel: String,
    val build: () -> List<SearchFrame>,
)

private val ProbeCell = Color(0xFFFACC15)
private val WindowCell = Color(0xFF3B82F6)
private val FoundCell = SimColors.Green
private val EliminatedAlpha = 0.18f

private val sortedInput = listOf(3, 8, 15, 27, 34, 42, 50, 61, 73, 88)
private const val SORTED_TARGET = 50

// Unsorted on purpose — linear search is the one algorithm with no ordering precondition.
private val unsortedInput = listOf(42, 8, 27, 61, 15, 34, 3, 50)
private const val UNSORTED_TARGET = 34

private fun linearSearchFrames(): List<SearchFrame> {
    val frames = mutableListOf<SearchFrame>()
    val eliminated = mutableSetOf<Int>()
    frames.add(SearchFrame(null, unsortedInput.indices.toSet(), emptySet(), null, "Start — check every element left to right until $UNSORTED_TARGET turns up."))
    for (i in unsortedInput.indices) {
        val value = unsortedInput[i]
        if (value == UNSORTED_TARGET) {
            frames.add(SearchFrame(i, emptySet(), eliminated.toSet(), i, "Found $UNSORTED_TARGET at index $i after ${i + 1} comparisons."))
            return frames
        }
        frames.add(SearchFrame(i, (i until unsortedInput.size).toSet(), eliminated.toSet(), null, "index $i: $value ≠ $UNSORTED_TARGET → keep going"))
        eliminated.add(i)
    }
    frames.add(SearchFrame(null, emptySet(), eliminated.toSet(), null, "$UNSORTED_TARGET is not in the array — linear search only knows that after all n checks."))
    return frames
}

private fun binarySearchFrames(): List<SearchFrame> {
    val frames = mutableListOf<SearchFrame>()
    var lo = 0
    var hi = sortedInput.lastIndex
    var probes = 0
    val eliminated = mutableSetOf<Int>()
    frames.add(SearchFrame(null, (lo..hi).toSet(), emptySet(), null, "Start — the array is sorted, so half of it can be discarded per probe."))
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val value = sortedInput[mid]
        probes++
        frames.add(SearchFrame(mid, (lo..hi).toSet(), eliminated.toSet(), null, "lo=$lo hi=$hi → probe mid=$mid ($value)"))
        when {
            value == SORTED_TARGET -> {
                frames.add(SearchFrame(mid, emptySet(), eliminated.toSet(), mid, "Found $SORTED_TARGET at index $mid — $probes probes for ${sortedInput.size} elements."))
                return frames
            }
            value < SORTED_TARGET -> {
                (lo..mid).forEach { eliminated.add(it) }
                lo = mid + 1
                frames.add(SearchFrame(null, (lo..hi).toSet(), eliminated.toSet(), null, "$value < $SORTED_TARGET → discard the left half, lo becomes $lo"))
            }
            else -> {
                (mid..hi).forEach { eliminated.add(it) }
                hi = mid - 1
                frames.add(SearchFrame(null, (lo..hi).toSet(), eliminated.toSet(), null, "$value > $SORTED_TARGET → discard the right half, hi becomes $hi"))
            }
        }
    }
    frames.add(SearchFrame(null, emptySet(), eliminated.toSet(), null, "Window is empty — $SORTED_TARGET is not present."))
    return frames
}

private fun jumpSearchFrames(): List<SearchFrame> {
    val frames = mutableListOf<SearchFrame>()
    val n = sortedInput.size
    val step = 3 // ≈ √10, rounded for a legible block size
    val eliminated = mutableSetOf<Int>()
    frames.add(SearchFrame(null, sortedInput.indices.toSet(), emptySet(), null, "Start — jump ahead in blocks of $step (≈√n), then walk back linearly."))
    var block = 0
    while (block < n) {
        val probe = minOf(block + step - 1, n - 1)
        val value = sortedInput[probe]
        frames.add(SearchFrame(probe, (block..probe).toSet(), eliminated.toSet(), null, "Block [$block..$probe]: last value $value"))
        if (value >= SORTED_TARGET) {
            for (i in block..probe) {
                if (sortedInput[i] == SORTED_TARGET) {
                    frames.add(SearchFrame(i, emptySet(), eliminated.toSet(), i, "Found $SORTED_TARGET at index $i inside the block."))
                    return frames
                }
                frames.add(SearchFrame(i, (block..probe).toSet(), eliminated.toSet(), null, "Linear scan inside block: ${sortedInput[i]} ≠ $SORTED_TARGET"))
            }
            break
        }
        frames.add(SearchFrame(null, emptySet(), eliminated.toSet(), null, "$value < $SORTED_TARGET → skip this whole block"))
        (block..probe).forEach { eliminated.add(it) }
        block += step
    }
    frames.add(SearchFrame(null, emptySet(), eliminated.toSet(), null, "$SORTED_TARGET is not present."))
    return frames
}

private fun interpolationSearchFrames(): List<SearchFrame> {
    val frames = mutableListOf<SearchFrame>()
    var lo = 0
    var hi = sortedInput.lastIndex
    val eliminated = mutableSetOf<Int>()
    frames.add(SearchFrame(null, (lo..hi).toSet(), emptySet(), null, "Start — estimate where $SORTED_TARGET should sit rather than always probing the middle."))
    while (lo <= hi && SORTED_TARGET >= sortedInput[lo] && SORTED_TARGET <= sortedInput[hi]) {
        val span = sortedInput[hi] - sortedInput[lo]
        val probe = if (span == 0) lo else lo + ((SORTED_TARGET - sortedInput[lo]).toLong() * (hi - lo) / span).toInt()
        val value = sortedInput[probe]
        frames.add(SearchFrame(probe, (lo..hi).toSet(), eliminated.toSet(), null, "Interpolate: probe=$probe ($value) from lo=${sortedInput[lo]}, hi=${sortedInput[hi]}"))
        when {
            value == SORTED_TARGET -> {
                frames.add(SearchFrame(probe, emptySet(), eliminated.toSet(), probe, "Found $SORTED_TARGET at index $probe — on uniform data this beats binary search."))
                return frames
            }
            value < SORTED_TARGET -> {
                (lo..probe).forEach { eliminated.add(it) }
                lo = probe + 1
                frames.add(SearchFrame(null, (lo..hi).toSet(), eliminated.toSet(), null, "$value < $SORTED_TARGET → search right, lo becomes $lo"))
            }
            else -> {
                (probe..hi).forEach { eliminated.add(it) }
                hi = probe - 1
                frames.add(SearchFrame(null, (lo..hi).toSet(), eliminated.toSet(), null, "$value > $SORTED_TARGET → search left, hi becomes $hi"))
            }
        }
    }
    frames.add(SearchFrame(null, emptySet(), eliminated.toSet(), null, "$SORTED_TARGET is outside the remaining range — not present."))
    return frames
}

private fun exponentialSearchFrames(): List<SearchFrame> {
    val frames = mutableListOf<SearchFrame>()
    val n = sortedInput.size
    val eliminated = mutableSetOf<Int>()
    frames.add(SearchFrame(null, sortedInput.indices.toSet(), emptySet(), null, "Start — double the bound until it overshoots the target, then binary search that range."))
    if (sortedInput[0] == SORTED_TARGET) {
        frames.add(SearchFrame(0, emptySet(), emptySet(), 0, "Found $SORTED_TARGET at index 0."))
        return frames
    }
    var bound = 1
    while (bound < n && sortedInput[bound] < SORTED_TARGET) {
        frames.add(SearchFrame(bound, (0..bound).toSet(), eliminated.toSet(), null, "bound=$bound: ${sortedInput[bound]} < $SORTED_TARGET → double it"))
        (0..bound).forEach { eliminated.add(it) }
        bound *= 2
    }
    val lo0 = bound / 2
    val hi0 = minOf(bound, n - 1)
    eliminated.removeAll((lo0..hi0).toSet())
    frames.add(SearchFrame(minOf(bound, n - 1), (lo0..hi0).toSet(), eliminated.toSet(), null, "Overshot — the target must lie in [$lo0..$hi0]. Binary search that window."))
    var lo = lo0
    var hi = hi0
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val value = sortedInput[mid]
        frames.add(SearchFrame(mid, (lo..hi).toSet(), eliminated.toSet(), null, "Probe mid=$mid ($value)"))
        when {
            value == SORTED_TARGET -> {
                frames.add(SearchFrame(mid, emptySet(), eliminated.toSet(), mid, "Found $SORTED_TARGET at index $mid."))
                return frames
            }
            value < SORTED_TARGET -> { (lo..mid).forEach { eliminated.add(it) }; lo = mid + 1 }
            else -> { (mid..hi).forEach { eliminated.add(it) }; hi = mid - 1 }
        }
    }
    frames.add(SearchFrame(null, emptySet(), eliminated.toSet(), null, "$SORTED_TARGET is not present."))
    return frames
}

// ── Interview-prep pattern guide ─────────────────────────────────────────────
// Rotated sorted array: the array is not sorted, but one half of every window always is, and that is
// enough to keep discarding half.
private val rotatedInput = listOf(27, 34, 42, 50, 61, 73, 88, 3, 8, 15)
private const val ROTATED_TARGET = 8

private fun rotatedSearchFrames(): List<SearchFrame> {
    val frames = mutableListOf<SearchFrame>()
    var lo = 0
    var hi = rotatedInput.lastIndex
    var probes = 0
    val eliminated = mutableSetOf<Int>()

    frames.add(
        SearchFrame(
            null, (lo..hi).toSet(), emptySet(), null,
            "The array is sorted, then rotated — so `values[mid] > target` no longer tells you which way to go. " +
                "What survives the rotation: at least one side of any window is still sorted.",
        ),
    )

    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val value = rotatedInput[mid]
        probes++
        if (value == ROTATED_TARGET) {
            frames.add(
                SearchFrame(
                    mid, emptySet(), eliminated.toSet(), mid,
                    "values[$mid] = $value — found after $probes probes. Same O(log n) as an unrotated search; only " +
                        "the branch condition changed.",
                ),
            )
            return frames
        }

        val leftSorted = rotatedInput[lo] <= value
        val inLeft = leftSorted && ROTATED_TARGET >= rotatedInput[lo] && ROTATED_TARGET < value
        val inRight = !leftSorted && ROTATED_TARGET > value && ROTATED_TARGET <= rotatedInput[hi]
        frames.add(
            SearchFrame(
                mid, (lo..hi).toSet(), eliminated.toSet(), null,
                "lo=$lo hi=$hi, probe $mid = $value. " +
                    if (leftSorted) {
                        "values[$lo]=${rotatedInput[lo]} ≤ $value, so the left side is the sorted one — and " +
                            "$ROTATED_TARGET " + (if (inLeft) "lies inside [${rotatedInput[lo]}, $value), so keep it."
                        else "is outside [${rotatedInput[lo]}, $value), so the answer can only be on the right.")
                    } else {
                        "values[$lo]=${rotatedInput[lo]} > $value, so the *right* side is the sorted one — and " +
                            "$ROTATED_TARGET " + (if (inRight) "lies inside ($value, ${rotatedInput[hi]}], so keep it."
                        else "is outside ($value, ${rotatedInput[hi]}], so search left.")
                    },
            ),
        )

        if (inLeft || inRight) {
            if (inLeft) {
                (mid..hi).forEach { eliminated.add(it) }
                hi = mid - 1
            } else {
                (lo..mid).forEach { eliminated.add(it) }
                lo = mid + 1
            }
        } else if (leftSorted) {
            (lo..mid).forEach { eliminated.add(it) }
            lo = mid + 1
        } else {
            (mid..hi).forEach { eliminated.add(it) }
            hi = mid - 1
        }
    }

    frames.add(
        SearchFrame(null, emptySet(), eliminated.toSet(), null, "$ROTATED_TARGET is not present — the window closed."),
    )
    return frames
}

private val searchConfigs = mapOf(
    "modified_binary_search_pattern" to SearchConfig(
        values = rotatedInput,
        target = ROTATED_TARGET,
        intro = "Binary search on a rotated array. Every probe first asks which half is sorted, then whether the " +
            "target lies in that half — the same discard-half loop with one extra question. The other members of " +
            "this family (first/last occurrence, search on the answer space) change only the same two lines.",
        windowLabel = "Live range",
        build = ::rotatedSearchFrames,
    ),
    "linear_search" to SearchConfig(
        values = unsortedInput,
        target = UNSORTED_TARGET,
        intro = "Linear search on unsorted data — the only search here that needs no ordering. Every element is checked until the target appears.",
        windowLabel = "Still to check",
        build = ::linearSearchFrames,
    ),
    "binary_search" to SearchConfig(
        values = sortedInput,
        target = SORTED_TARGET,
        intro = "Binary search halves the live window (blue) on every probe. Greyed cells are eliminated — 10 elements need at most 4 probes.",
        windowLabel = "Live window",
        build = ::binarySearchFrames,
    ),
    "jump_search" to SearchConfig(
        values = sortedInput,
        target = SORTED_TARGET,
        intro = "Jump search skips ahead in fixed blocks of ≈√n, then scans linearly inside the block that must contain the target.",
        windowLabel = "Current block",
        build = ::jumpSearchFrames,
    ),
    "interpolation_search" to SearchConfig(
        values = sortedInput,
        target = SORTED_TARGET,
        intro = "Interpolation search guesses the probe position from the target's value relative to the window's endpoints — near O(log log n) on uniform data.",
        windowLabel = "Live window",
        build = ::interpolationSearchFrames,
    ),
    "exponential_search" to SearchConfig(
        values = sortedInput,
        target = SORTED_TARGET,
        intro = "Exponential search doubles a bound until it passes the target, then binary searches the bracketed range — useful when the array is unbounded.",
        windowLabel = "Live window",
        build = ::exponentialSearchFrames,
    ),
)

private fun searchConfigFor(topicId: String): SearchConfig =
    searchConfigs[topicId] ?: searchConfigs.getValue("binary_search")

// Exposed so PatternCoverageTest can tell a topic that configured this widget from one that only
// inherits the fallback above.
internal val searchVisualizerTopicIds: Set<String> get() = searchConfigs.keys

/** Probes, windows and hits are all indices into the config's own array; an off-array one is invisible. */
internal fun searchFrameCount(topicId: String): Int {
    val config = searchConfigFor(topicId)
    val frames = config.build()
    frames.forEachIndexed { index, frame ->
        require(frame.status.isNotBlank()) { "$topicId frame $index has no status line" }
        (frame.window + frame.eliminated + listOfNotNull(frame.probe, frame.found)).forEach { cell ->
            require(cell in config.values.indices) {
                "$topicId frame $index touches cell $cell of ${config.values.size}"
            }
        }
    }
    return frames.size
}

@Composable
fun SearchVisualizerSection(topicId: String) {
    val config = remember(topicId) { searchConfigFor(topicId) }
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

            Text(
                "Target: ${config.target}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
            )

            CellStrip(values = config.values, frame = frame, modifier = Modifier.padding(top = 10.dp))

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
                SearchLegend(ProbeCell, "Probe")
                SearchLegend(WindowCell, config.windowLabel)
                SearchLegend(FoundCell, "Found")
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun SearchLegend(color: Color, label: String) {
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
private fun CellStrip(values: List<Int>, frame: SearchFrame, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            values.forEachIndexed { index, value ->
                val color = when {
                    frame.found == index -> FoundCell
                    frame.probe == index -> ProbeCell
                    index in frame.window -> WindowCell
                    index in frame.eliminated -> WindowCell.copy(alpha = EliminatedAlpha)
                    else -> WindowCell.copy(alpha = 0.35f)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .background(color, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        value.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            values.indices.forEach { index ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        index.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
