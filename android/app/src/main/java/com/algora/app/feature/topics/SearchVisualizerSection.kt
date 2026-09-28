package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import kotlin.math.ceil
import kotlin.math.ln

// ── Search visualizer ────────────────────────────────────────────────────────
// Built to docs/ios-design/Simulations iOS.html (Binary Search). One step per probe: the array with
// the probe in yellow, the live window in blue and ruled-out cells greyed, lo/mid/hi markers under the
// indices, a running list of every probe so far and its outcome, then chips, a headline naming the
// decision and a sentence on what it cost. One config per search algorithm, resolved by topicId.

// One row of the probe list: "a[7] = 61 > 50" and what it decided, "hi = 6".
private class ProbeLine(val number: Int, val expr: String, val outcome: String)

// Headline in three parts so the middle word can take the probe yellow (or the found green).
private class SearchHeadline(val lead: String, val emphasis: String, val tail: String) {
    val plain: String get() = lead + emphasis + tail
}

private data class SearchFrame(
    val probe: Int?,
    val window: Set<Int>,
    val eliminated: Set<Int>,
    val found: Int?,
    val status: String,
    // Index -> labels drawn under it (lo, mid, hi …); several can share one index.
    val pointers: Map<Int, List<String>> = emptyMap(),
    val log: List<ProbeLine> = emptyList(),
    // The index the following step probes, shown as a dimmed placeholder row under the list.
    val next: Int? = null,
    val chips: List<Pair<String, String>> = emptyList(),
    val headline: SearchHeadline? = null,
    val body: String? = null,
)

private class SearchConfig(
    val values: List<Int>,
    val target: Int,
    // Card heading; the size is appended, e.g. "SORTED ARRAY · N = 10".
    val title: String,
    val windowLabel: String,
    val build: () -> List<SearchFrame>,
)

private val ProbeCell = SimColors.Active
private val WindowCell = SimColors.Blue
private val FoundCell = SimColors.Green

// Dark text on the yellow probe cell: white on #F5C542 is unreadable.
private val OnProbeCell = Color(0xFF1F1A0A)

// Pointer labels that name the probe itself take its yellow; the bounds (lo, hi, from) take the
// window's blue.
private val ProbeLabels = setOf("mid", "pos", "i", "jump", "bound")

private val sortedInput = listOf(3, 8, 15, 27, 34, 42, 50, 61, 73, 88)
private const val SORTED_TARGET = 50

// Unsorted on purpose — linear search is the one algorithm with no ordering precondition.
private val unsortedInput = listOf(42, 8, 27, 61, 15, 34, 3, 50)
private const val UNSORTED_TARGET = 34

private val probeWords = listOf("One probe has", "Two probes have", "Three probes have", "Four probes have", "Five probes have")

private fun probesHave(k: Int) = probeWords.getOrNull(k - 1) ?: "$k probes have"

private fun cut(k: Int, n: Int, after: Int) = when (after) {
    0 -> "${probesHave(k)} emptied the window."
    else -> "${probesHave(k)} cut $n candidates down to $after."
}

private fun pointers(vararg marks: Pair<Int, String>): Map<Int, List<String>> =
    marks.groupBy({ it.first }, { it.second })

private class SearchBuilder(val values: List<Int>, val target: Int) {
    private val frames = mutableListOf<SearchFrame>()
    private val log = mutableListOf<ProbeLine>()
    val eliminated = mutableSetOf<Int>()

    // The number the probe being added will carry.
    val number: Int get() = log.size + 1

    fun probe(
        index: Int,
        window: Set<Int>,
        marks: Map<Int, List<String>>,
        expr: String,
        outcome: String,
        chips: List<Pair<String, String>>,
        headline: SearchHeadline,
        body: String,
        found: Boolean = false,
    ) {
        log += ProbeLine(log.size + 1, expr, outcome)
        frames += SearchFrame(
            probe = index,
            window = window,
            eliminated = eliminated.toSet(),
            found = if (found) index else null,
            status = headline.plain,
            pointers = marks,
            log = log.toList(),
            chips = chips,
            headline = headline,
            body = body,
        )
    }

    fun rule(range: IntRange) {
        eliminated.addAll(range)
    }

    fun notFound(detail: String) {
        val headline = SearchHeadline("The window is ", "empty", ": ${target} is not in the array.")
        frames += SearchFrame(null, emptySet(), eliminated.toSet(), null, headline.plain, log = log.toList(), headline = headline, body = detail)
    }

    fun build(): List<SearchFrame> = frames.mapIndexed { i, frame -> frame.copy(next = frames.getOrNull(i + 1)?.probe) }
}

// The discard-half loop, shared by binary search and the second phase of exponential search.
private fun SearchBuilder.binaryPhase(startLo: Int, startHi: Int, foundBody: (probes: Int) -> String) {
    var lo = startLo
    var hi = startHi
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val v = values[mid]
        val before = hi - lo + 1
        val window = (lo..hi).toSet()
        val marks = pointers(lo to "lo", mid to "mid", hi to "hi")
        when {
            v == target -> {
                probe(
                    mid, window, marks, "a[$mid] = $v", "found",
                    chips = listOf("lo" to "$lo", "hi" to "$hi", "mid" to "$mid"),
                    headline = SearchHeadline("a[$mid] is $target: ", "found", " on probe $number."),
                    body = foundBody(number),
                    found = true,
                )
                return
            }
            v < target -> {
                val after = hi - mid
                probe(
                    mid, window, marks, "a[$mid] = $v < $target", "lo = ${mid + 1}",
                    chips = listOf("lo" to "$lo", "hi" to "$hi", "mid" to "$mid", "window" to "$before → $after"),
                    headline = SearchHeadline("$v is smaller than $target, so the target is right of ", "mid", "."),
                    body = "lo moves to ${mid + 1}. ${cut(number, values.size, after)}",
                )
                rule(lo..mid)
                lo = mid + 1
            }
            else -> {
                val after = mid - lo
                probe(
                    mid, window, marks, "a[$mid] = $v > $target", "hi = ${mid - 1}",
                    chips = listOf("lo" to "$lo", "hi" to "$hi", "mid" to "$mid", "window" to "$before → $after"),
                    headline = SearchHeadline("$v is bigger than $target, so the target is left of ", "mid", "."),
                    body = "hi moves to ${mid - 1}. ${cut(number, values.size, after)}",
                )
                rule(mid..hi)
                hi = mid - 1
            }
        }
    }
    notFound("lo passed hi, so every candidate has been ruled out.")
}

private fun worstCaseProbes(n: Int) = ceil(ln(n + 1.0) / ln(2.0)).toInt()

private fun binarySearchFrames(): List<SearchFrame> {
    val b = SearchBuilder(sortedInput, SORTED_TARGET)
    val n = sortedInput.size
    b.binaryPhase(0, n - 1) { probes ->
        "⌈log₂(${n + 1})⌉ = ${worstCaseProbes(n)} probes is the worst case for $n elements; this took $probes. " +
            "A linear scan could have needed all $n."
    }
    return b.build()
}

private fun linearSearchFrames(): List<SearchFrame> {
    val values = unsortedInput
    val t = UNSORTED_TARGET
    val n = values.size
    val b = SearchBuilder(values, t)
    for (i in values.indices) {
        val v = values[i]
        val window = (i until n).toSet()
        val chips = listOf("i" to "$i", "checked" to "${i + 1} / $n")
        if (v == t) {
            b.probe(
                i, window, pointers(i to "i"), "a[$i] = $v", "found", chips,
                SearchHeadline("a[$i] is $t: ", "found", " after ${i + 1} checks."),
                "Linear search needs no ordering, but it pays for that: on average it checks half the array, and " +
                    "all of it when the target is missing.",
                found = true,
            )
            return b.build()
        }
        b.probe(
            i, window, pointers(i to "i"), "a[$i] = $v ≠ $t", "next", chips,
            SearchHeadline("$v is not $t, so ", "move on", "."),
            "Unsorted data gives no hint where $t might be. ${i + 1} of $n checked, ${n - i - 1} left.",
        )
        b.rule(i..i)
    }
    b.notFound("All $n elements checked — linear search only knows the target is missing after every one.")
    return b.build()
}

private fun jumpSearchFrames(): List<SearchFrame> {
    val values = sortedInput
    val t = SORTED_TARGET
    val n = values.size
    val step = 3 // ≈ √10, rounded for a legible block size
    val b = SearchBuilder(values, t)
    var block = 0
    while (block < n) {
        val end = minOf(block + step - 1, n - 1)
        val v = values[end]
        val window = (block..end).toSet()
        val marks = pointers(block to "from", end to "jump")
        val chips = listOf("block" to "$block–$end", "step" to "$step")
        if (v == t) {
            b.probe(
                end, window, marks, "a[$end] = $v", "found", chips,
                SearchHeadline("The block ends on $t: ", "found", " on probe ${b.number}."),
                "Jumping $step at a time reached it without a scan.",
                found = true,
            )
            return b.build()
        }
        if (v < t) {
            b.probe(
                end, window, marks, "a[$end] = $v < $t", "skip → ${end + 1}", chips,
                SearchHeadline("$v is below $t, so ", "skip", " the whole block."),
                "Everything in [$block..$end] is at most $v. Jump $step ahead to index ${end + 1}.",
            )
            b.rule(block..end)
            block += step
            continue
        }
        b.probe(
            end, window, marks, "a[$end] = $v > $t", "scan $block–$end", chips,
            SearchHeadline("$v passes $t, so the target is ", "inside", " this block."),
            "Step back to index $block and walk the block one element at a time — at most ${end - block} more checks.",
        )
        for (i in block until end) {
            val vi = values[i]
            val scanChips = listOf("i" to "$i", "block" to "$block–$end")
            if (vi == t) {
                b.probe(
                    i, (i..end).toSet(), pointers(i to "i", end to "jump"), "a[$i] = $vi", "found", scanChips,
                    SearchHeadline("a[$i] is $t: ", "found", " on probe ${b.number}."),
                    "${b.number} probes: ${block / step + 1} jumps, then a short scan. About 2√n in the worst case, " +
                        "against n for a plain scan.",
                    found = true,
                )
                return b.build()
            }
            b.probe(
                i, (i..end).toSet(), pointers(i to "i", end to "jump"), "a[$i] = $vi < $t", "next", scanChips,
                SearchHeadline("$vi is below $t, keep ", "scanning", "."),
                "Inside the block the search is linear.",
            )
            b.rule(i..i)
        }
        break
    }
    b.notFound("No block can hold $t.")
    return b.build()
}

private fun interpolationSearchFrames(): List<SearchFrame> {
    val values = sortedInput
    val t = SORTED_TARGET
    val b = SearchBuilder(values, t)
    var lo = 0
    var hi = values.lastIndex
    while (lo <= hi && t >= values[lo] && t <= values[hi]) {
        val span = values[hi] - values[lo]
        val pos = if (span == 0) lo else lo + ((t - values[lo]).toLong() * (hi - lo) / span).toInt()
        val v = values[pos]
        val before = hi - lo + 1
        val window = (lo..hi).toSet()
        val marks = pointers(lo to "lo", pos to "pos", hi to "hi")
        val formula = "pos = $lo + ($t − ${values[lo]})·($hi − $lo) / (${values[hi]} − ${values[lo]}) = $pos."
        when {
            v == t -> {
                b.probe(
                    pos, window, marks, "a[$pos] = $v", "found",
                    listOf("lo" to "$lo", "hi" to "$hi", "pos" to "$pos"),
                    SearchHeadline("The estimate lands on $t: ", "found", " on probe ${b.number}."),
                    "$formula On evenly spread values the estimate lands close, which is why interpolation can beat " +
                        "binary search's ${worstCaseProbes(values.size)}.",
                    found = true,
                )
                return b.build()
            }
            v < t -> {
                val after = hi - pos
                b.probe(
                    pos, window, marks, "a[$pos] = $v < $t", "lo = ${pos + 1}",
                    listOf("lo" to "$lo", "hi" to "$hi", "pos" to "$pos", "window" to "$before → $after"),
                    SearchHeadline("The estimate reads $v, below $t, so search ", "right", "."),
                    "$formula lo moves to ${pos + 1}.",
                )
                b.rule(lo..pos)
                lo = pos + 1
            }
            else -> {
                val after = pos - lo
                b.probe(
                    pos, window, marks, "a[$pos] = $v > $t", "hi = ${pos - 1}",
                    listOf("lo" to "$lo", "hi" to "$hi", "pos" to "$pos", "window" to "$before → $after"),
                    SearchHeadline("The estimate reads $v, above $t, so search ", "left", "."),
                    "$formula hi moves to ${pos - 1}.",
                )
                b.rule(pos..hi)
                hi = pos - 1
            }
        }
    }
    b.notFound("$t is outside the remaining range.")
    return b.build()
}

private fun exponentialSearchFrames(): List<SearchFrame> {
    val values = sortedInput
    val t = SORTED_TARGET
    val n = values.size
    val b = SearchBuilder(values, t)
    if (values[0] == t) {
        b.probe(
            0, setOf(0), pointers(0 to "bound"), "a[0] = ${values[0]}", "found", listOf("bound" to "0"),
            SearchHeadline("a[0] is $t: ", "found", " on the first probe."), "No doubling needed.", found = true,
        )
        return b.build()
    }
    var bound = 1
    while (bound < n && values[bound] < t) {
        b.probe(
            bound, (0..bound).toSet(), pointers(bound to "bound"), "a[$bound] = ${values[bound]} < $t", "bound = ${bound * 2}",
            listOf("bound" to "$bound"),
            SearchHeadline("${values[bound]} is below $t, so ", "double", " the bound."),
            "Everything up to index $bound is too small. The next check is at ${bound * 2}.",
        )
        b.rule(0..bound)
        bound *= 2
    }
    val lo = bound / 2
    val hi = minOf(bound, n - 1)
    // The last doubling's own index is back inside the bracket.
    b.eliminated.removeAll((lo..hi).toSet())
    val edge = values[hi]
    if (edge == t) {
        b.probe(
            hi, (lo..hi).toSet(), pointers(hi to "bound"), "a[$hi] = $edge", "found", listOf("bound" to "$hi"),
            SearchHeadline("a[$hi] is $t: ", "found", " on probe ${b.number}."), "The doubling landed on it.", found = true,
        )
        return b.build()
    }
    b.probe(
        hi, (lo..hi).toSet(), pointers(lo to "lo", hi to "bound"),
        if (bound >= n) "bound $bound > end" else "a[$hi] = $edge > $t", "range $lo–$hi",
        listOf("lo" to "$lo", "bound" to "$hi"),
        SearchHeadline(
            if (bound >= n) "The bound ran past the end, so the target is " else "$edge passes $t, so the target is ",
            "bracketed",
            ".",
        ),
        "It must lie between index $lo and $hi — binary search just that range.",
    )
    val bracketProbes = b.number - 1
    // a[hi] was only compared when the bound stayed inside the array; past the end it is still a candidate.
    b.binaryPhase(lo, if (bound >= n) hi else hi - 1) { probes ->
        "$bracketProbes probes to bracket it, ${probes - bracketProbes} inside. Doubling costs log of the target's " +
            "position, not of the array — which is why this works on unbounded input."
    }
    return b.build()
}

// ── Interview-prep pattern guide ─────────────────────────────────────────────
// Rotated sorted array: the array is not sorted, but one half of every window always is, and that is
// enough to keep discarding half.
private val rotatedInput = listOf(27, 34, 42, 50, 61, 73, 88, 3, 8, 15)
private const val ROTATED_TARGET = 8

private fun rotatedSearchFrames(): List<SearchFrame> {
    val values = rotatedInput
    val t = ROTATED_TARGET
    val b = SearchBuilder(values, t)
    var lo = 0
    var hi = values.lastIndex
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        val v = values[mid]
        val before = hi - lo + 1
        val window = (lo..hi).toSet()
        val marks = pointers(lo to "lo", mid to "mid", hi to "hi")
        if (v == t) {
            b.probe(
                mid, window, marks, "a[$mid] = $v", "found",
                listOf("lo" to "$lo", "hi" to "$hi", "mid" to "$mid"),
                SearchHeadline("a[$mid] is $t: ", "found", " on probe ${b.number}."),
                "Same O(log n) as an unrotated search — only the branch condition changed.",
                found = true,
            )
            return b.build()
        }
        val leftSorted = values[lo] <= v
        val inLeft = leftSorted && t >= values[lo] && t < v
        val inRight = !leftSorted && t > v && t <= values[hi]
        val goLeft = inLeft || (!leftSorted && !inRight)
        val sortedSide = if (leftSorted) "left" else "right"
        val range = if (leftSorted) "[${values[lo]}, $v]" else "[$v, ${values[hi]}]"
        val holds = inLeft || inRight
        val newLo = if (goLeft) lo else mid + 1
        val newHi = if (goLeft) mid - 1 else hi
        b.probe(
            mid, window, marks,
            "a[$mid] = $v, $sortedSide sorted",
            if (goLeft) "hi = ${mid - 1}" else "lo = ${mid + 1}",
            listOf("lo" to "$lo", "hi" to "$hi", "mid" to "$mid", "window" to "$before → ${newHi - newLo + 1}"),
            SearchHeadline(
                "The $sortedSide half is sorted and $t ${if (holds) "falls inside it" else "is not in it"}, so go ",
                if (goLeft) "left" else "right",
                ".",
            ),
            "The $sortedSide side runs $range in order, so a range check is enough to tell whether $t is there. " +
                if (goLeft) "hi moves to ${mid - 1}." else "lo moves to ${mid + 1}.",
        )
        if (goLeft) b.rule(mid..hi) else b.rule(lo..mid)
        lo = newLo
        hi = newHi
    }
    b.notFound("The window closed.")
    return b.build()
}

private val searchConfigs = mapOf(
    "modified_binary_search_pattern" to SearchConfig(
        values = rotatedInput,
        target = ROTATED_TARGET,
        title = "ROTATED SORTED ARRAY",
        windowLabel = "Live window",
        build = ::rotatedSearchFrames,
    ),
    "linear_search" to SearchConfig(
        values = unsortedInput,
        target = UNSORTED_TARGET,
        title = "UNSORTED ARRAY",
        windowLabel = "Still to check",
        build = ::linearSearchFrames,
    ),
    "binary_search" to SearchConfig(
        values = sortedInput,
        target = SORTED_TARGET,
        title = "SORTED ARRAY",
        windowLabel = "Live window",
        build = ::binarySearchFrames,
    ),
    "jump_search" to SearchConfig(
        values = sortedInput,
        target = SORTED_TARGET,
        title = "SORTED ARRAY",
        windowLabel = "Current block",
        build = ::jumpSearchFrames,
    ),
    "interpolation_search" to SearchConfig(
        values = sortedInput,
        target = SORTED_TARGET,
        title = "SORTED ARRAY",
        windowLabel = "Live window",
        build = ::interpolationSearchFrames,
    ),
    "exponential_search" to SearchConfig(
        values = sortedInput,
        target = SORTED_TARGET,
        title = "SORTED ARRAY",
        windowLabel = "Live window",
        build = ::exponentialSearchFrames,
    ),
)

private fun searchConfigFor(topicId: String): SearchConfig =
    searchConfigs[topicId] ?: searchConfigs.getValue("binary_search")

// Exposed so PatternCoverageTest can tell a topic that configured this widget from one that only
// inherits the fallback above.
internal val searchVisualizerTopicIds: Set<String> get() = searchConfigs.keys

/** Probes, windows, pointers and hits are all indices into the config's own array; an off-array one is invisible. */
internal fun searchFrameCount(topicId: String): Int {
    val config = searchConfigFor(topicId)
    val frames = config.build()
    frames.forEachIndexed { index, frame ->
        require(frame.status.isNotBlank()) { "$topicId frame $index has no status line" }
        (frame.window + frame.eliminated + frame.pointers.keys + listOfNotNull(frame.probe, frame.found, frame.next))
            .forEach { cell ->
                require(cell in config.values.indices) {
                    "$topicId frame $index touches cell $cell of ${config.values.size}"
                }
            }
    }
    return frames.size
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchVisualizerSection(topicId: String) {
    val config = remember(topicId) { searchConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 650f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // The mock's yellow is for a dark card; on white it needs to be darker to stay legible as text.
    val highlight = if (dark) ProbeCell else Color(0xFFB7791F)

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${config.title} · N = ${config.values.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        color = muted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "target ${config.target}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (dark) Color(0xFFC4B5FD) else SimColors.Answer,
                        modifier = Modifier
                            .background(SimColors.Answer.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                CellStrip(values = config.values, frame = frame, highlight = highlight, modifier = Modifier.padding(top = 12.dp))

                if (frame.log.isNotEmpty()) {
                    Text(
                        "PROBES",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = muted,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        frame.log.forEachIndexed { i, line ->
                            ProbeRow(line, current = i == frame.log.lastIndex, highlight = highlight)
                        }
                        frame.next?.let { next ->
                            ProbeRow(ProbeLine(frame.log.size + 1, "a[$next] …", ""), current = false, highlight = highlight, pending = true)
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SearchLegend(ProbeCell, "Probe")
                    SearchLegend(WindowCell, config.windowLabel)
                    SearchLegend(muted.copy(alpha = 0.16f), "Ruled out")
                    if (frame.found != null) SearchLegend(FoundCell, "Found")
                }
            }
        }

        if (frame.chips.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                frame.chips.forEach { (label, value) -> SearchChip(label, value) }
            }
        }

        val headline = frame.headline
        Text(
            if (headline == null) {
                buildAnnotatedString { append(frame.status) }
            } else {
                buildAnnotatedString {
                    append(headline.lead)
                    withStyle(SpanStyle(color = if (frame.found != null) FoundCell else highlight)) { append(headline.emphasis) }
                    append(headline.tail)
                }
            },
            fontSize = 20.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Bold,
            color = onSurface,
            modifier = Modifier.padding(top = 16.dp),
        )
        frame.body?.let { body ->
            Text(body, fontSize = 15.sp, lineHeight = 22.sp, color = muted, modifier = Modifier.padding(top = 8.dp))
        }

        PlaybackTransport(playback, captions = frames.map { it.status })
    }
}

@Composable
private fun SearchLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(11.dp).background(color, RoundedCornerShape(3.dp)))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 7.dp),
        )
    }
}

@Composable
private fun SearchChip(label: String, value: String) {
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

// One line of the probe list. The current probe is outlined in yellow with its outcome in yellow; the
// placeholder for the next probe is an empty outline with dimmed text.
@Composable
private fun ProbeRow(line: ProbeLine, current: Boolean, highlight: Color, pending: Boolean = false) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .then(
                when {
                    current -> Modifier.background(ProbeCell.copy(alpha = 0.12f), shape).border(1.5.dp, ProbeCell, shape)
                    pending -> Modifier.border(1.dp, muted.copy(alpha = 0.25f), shape)
                    else -> Modifier.background(muted.copy(alpha = 0.10f), shape)
                },
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${line.number}",
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (current) highlight else muted.copy(alpha = if (pending) 0.5f else 1f),
            modifier = Modifier.width(28.dp),
        )
        Text(
            line.expr,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            color = if (pending) muted.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (line.outcome.isNotEmpty()) {
            Text(
                line.outcome,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                color = if (current) highlight else muted,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun CellStrip(values: List<Int>, frame: SearchFrame, highlight: Color, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val gap = 4.dp
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            values.forEachIndexed { index, value ->
                val fill: Color
                val text: Color
                when {
                    frame.found == index -> { fill = FoundCell; text = Color.White }
                    frame.probe == index -> { fill = ProbeCell; text = OnProbeCell }
                    index in frame.window -> { fill = WindowCell; text = Color.White }
                    index in frame.eliminated -> { fill = muted.copy(alpha = 0.12f); text = muted.copy(alpha = 0.5f) }
                    // Not ruled out, but outside what this step looks at (jump search's later blocks).
                    else -> { fill = muted.copy(alpha = 0.12f); text = onSurface.copy(alpha = 0.8f) }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .background(fill, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(value.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = text, maxLines = 1)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            values.indices.forEach { index ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$index", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = muted)
                    val labels = frame.pointers[index].orEmpty()
                    Text(
                        buildAnnotatedString {
                            labels.forEachIndexed { i, label ->
                                if (i > 0) append("·")
                                withStyle(SpanStyle(color = if (label in ProbeLabels) highlight else WindowCell)) { append(label) }
                            }
                        },
                        fontSize = 12.sp,
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
