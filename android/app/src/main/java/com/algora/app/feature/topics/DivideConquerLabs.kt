package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.hypot

// ── Divide and conquer story labs ────────────────────────────────────────────
// Strassen, Karatsuba and Closest Pair drawn as story cards (docs mocks): the products a level makes
// and which of them are done, instead of the whole call tree, and the strip that closest pair checks.

/** One step of a story lab that is not an array walk: the card, and the caption for the step list. */
internal class StoryStep(val story: GreedyStory) {
    val status: String get() = story.status
}

// ── Strassen ─────────────────────────────────────────────────────────────────

internal val StrassenSizes = listOf(2, 4, 8)

private val strassenProducts = listOf(
    "(A11 + A22)(B11 + B22)",
    "(A21 + A22) B11",
    "A11 (B12 − B22)",
    "A22 (B21 − B11)",
    "(A11 + A12) B22",
    "(A21 − A11)(B11 + B12)",
    "(A12 − A22)(B21 + B22)",
)

// The four blocks of C, as sums of the seven products (index into strassenProducts, and its sign).
private val strassenBlocks = listOf(
    "C11" to listOf(0 to 1, 3 to 1, 4 to -1, 6 to 1),
    "C12" to listOf(2 to 1, 4 to 1),
    "C21" to listOf(1 to 1, 3 to 1),
    "C22" to listOf(0 to 1, 1 to -1, 2 to 1, 5 to 1),
)

private fun blockFormula(terms: List<Pair<Int, Int>>): String =
    terms.mapIndexed { i, (m, sign) ->
        (if (i == 0) (if (sign < 0) "−" else "") else if (sign < 0) " − " else " + ") + "M${m + 1}"
    }.joinToString("")

// Scalar multiplications for an s×s product: plain at 2×2 and below, seven half-size products above.
private fun strassenMults(s: Int): Int = if (s <= 2) s * s * s else 7 * strassenMults(s / 2)

private val ordinalWords = listOf("first", "second", "third", "fourth", "fifth", "sixth", "seventh")

internal fun strassenSteps(n: Int): List<StoryStep> {
    val h = n / 2
    val mults = 7 * strassenMults(h)
    val plain = n * n * n
    val steps = mutableListOf<StoryStep>()
    val bodies = listOf(
        "A sum of blocks times a sum of blocks: one multiplication doing the work of several.",
        "Only the multiplications set the exponent. The block additions are cheap by comparison.",
        "Seven products instead of eight cut the exponent from 3 to 2.81.",
        "The additions grow as n², so they never decide the exponent.",
        "Each product feeds more than one block of C, which is how seven cover all four.",
        "Subtracting blocks before multiplying is where the saving comes from.",
        "That is the last product. Nothing else gets multiplied.",
    )

    fun step(states: (Int) -> StoryTone, chips: List<StoryChip>, headline: String, body: String) {
        val rows = strassenProducts.mapIndexed { i, f -> StoryTableRow(listOf("M${i + 1}", f), states(i)) }
        steps += StoryStep(
            GreedyStory(
                title = "",
                note = "",
                sections = listOf(
                    StorySection(
                        label = "7 BLOCK PRODUCTS",
                        note = "instead of 8",
                        table = StoryTable(emptyList(), listOf(1f, 5f), rows, listOf(ColAlign.Start, ColAlign.End)),
                    ),
                ),
                legend = listOf(StoryTone.Active to "Computing", StoryTone.Done to "Returned"),
                chips = chips,
                headline = headline,
                body = body,
            ),
        )
    }

    step(
        { StoryTone.Idle },
        listOf(StoryChip("plain", "8 products")),
        "Split A and B into four {$h × $h} blocks each.",
        "The plain method multiplies 8 pairs of blocks. Strassen gets by with 7 cleverly chosen products.",
    )
    for (i in strassenProducts.indices) {
        val (name, terms) = strassenBlocks.filter { (_, t) -> t.any { it.first == i } }.minBy { it.second.size }
        val chip = StoryChip(name, blockFormula(terms))
        step(
            { j -> if (j < i) StoryTone.Done else if (j == i) StoryTone.Active else StoryTone.Idle },
            listOf(chip),
            "{M${i + 1}} = ${strassenProducts[i]}, the ${ordinalWords[i]} of seven.",
            bodies[i],
        )
        val users = strassenBlocks.filter { (_, t) -> t.any { it.first == i } }.map { it.first }
        step(
            { j -> if (j <= i) StoryTone.Done else StoryTone.Idle },
            listOf(chip),
            "{m:M${i + 1}} is done. ${users.joinToString(" and ")} will use it.",
            if (h == 1) "Each block is a single number here, so M${i + 1} costs one multiplication."
            else "It multiplies two $h × $h blocks: ${strassenMults(h)} scalar multiplications" +
                if (h > 2) ", by splitting the same way again." else ", the plain way.",
        )
    }
    step(
        { StoryTone.Done },
        listOf(StoryChip("mults", "$mults vs $plain", StoryTone.Done), StoryChip("block adds", "18")),
        "Add the products into {m:C}: C12 = M3 + M5, and so on.",
        "$mults scalar multiplications against $plain for the plain method, paid for with 18 block additions.",
    )
    return steps
}

// ── Karatsuba ────────────────────────────────────────────────────────────────

internal val KaratsubaDigits = listOf(2, 3, 4)

private val karatsubaOperands = mapOf(2 to (47L to 82L), 3 to (471L to 823L), 4 to (1234L to 5678L))

// One-digit multiplications the full recursion makes: the leaves of Karatsuba's call tree.
private fun karatsubaLeaves(a: Long, b: Long, width: Int): Int {
    if (a < 10 || b < 10) return 1
    val half = width / 2
    var p = 1L
    repeat(half) { p *= 10 }
    return karatsubaLeaves(a / p, b / p, width - half) + karatsubaLeaves(a % p, b % p, half) +
        karatsubaLeaves(a / p + a % p, b / p + b % p, maxOf(width - half, half) + 1)
}

private fun superscript(n: Int) = n.toString().map { "⁰¹²³⁴⁵⁶⁷⁸⁹"[it - '0'] }.joinToString("")

internal fun karatsubaSteps(digits: Int): List<StoryStep> {
    val (x, y) = karatsubaOperands.getValue(digits)
    val half = digits / 2
    var p = 1L
    repeat(half) { p *= 10 }
    val a = x / p
    val b = x % p
    val c = y / p
    val d = y % p
    val ac = a * c
    val bd = b * d
    val sums = (a + b) * (c + d)
    val cross = sums - ac - bd
    val product = x * y
    val leaves = karatsubaLeaves(x, y, digits)
    val schoolbook = digits * digits
    val steps = mutableListOf<StoryStep>()
    val labels = listOf("$a × $c", "$b × $d", "${a + b} × ${c + d}")
    val values = listOf(ac, bd, sums)

    // done: products finished; current: the one being computed; crossed: the middle term has been reduced.
    fun step(root: StoryTone, done: Int, current: Int?, crossed: Boolean, chips: List<StoryChip>, headline: String, body: String) {
        fun tone(i: Int) = when {
            i < done -> StoryTone.Done
            i == current -> StoryTone.Active
            else -> StoryTone.Idle
        }
        fun known(i: Int) = i < done || i == current
        val children = labels.mapIndexed { i, label -> StoryPill(label, tone(i), if (known(i)) "= ${values[i]}" else null) }
        val rows = listOf(
            StoryTableRow(listOf("ac", labels[0], if (known(0)) "$ac" else "…"), tone(0)),
            StoryTableRow(listOf("bd", labels[1], if (known(1)) "$bd" else "…"), tone(1)),
            StoryTableRow(
                listOf(
                    "ad + bc",
                    if (crossed) "$sums − $ac − $bd" else "${labels[2]} − …",
                    if (crossed) "$cross" else "…",
                ),
                if (crossed) StoryTone.Done else tone(2),
            ),
        )
        steps += StoryStep(
            GreedyStory(
                title = "",
                note = "",
                sections = listOf(
                    StorySection(fan = StoryFan(StoryPill("$x × $y", root), children)),
                    StorySection(table = StoryTable(emptyList(), listOf(1.3f, 2.8f, 1.3f), rows, listOf(ColAlign.Start, ColAlign.Center, ColAlign.End))),
                ),
                legend = listOf(StoryTone.Active to "Computing", StoryTone.Path to "Waiting", StoryTone.Done to "Returned"),
                chips = chips,
                headline = headline,
                body = body,
            ),
        )
    }
    val three = listOf(StoryChip("products", "3, not 4"))

    step(
        StoryTone.Active, 0, null, false, listOf(StoryChip("schoolbook", "4 products")),
        "Split both numbers in half: {$x = $a | $b}.",
        "And $y = $c | $d. Multiplying the halves the schoolbook way needs ac, ad, bc and bd: four products.",
    )
    step(
        StoryTone.Path, 0, 0, false, three,
        "{${labels[0]}} = $ac, the first of three products.",
        "ac is the high part of the answer. It gets shifted ${2 * half} places left at the end.",
    )
    step(
        StoryTone.Path, 1, 1, false, three,
        "{${labels[1]}} = $bd, the second of three products.",
        "The middle term reuses them, so each level needs 3 multiplications, not 4.",
    )
    step(
        StoryTone.Path, 2, 2, false, three,
        "{${labels[2]}} = $sums, the third product.",
        "It multiplies the sums, (a + b)(c + d), which is ac + ad + bc + bd all at once.",
    )
    step(
        StoryTone.Path, 3, null, true, three,
        "Subtract ac and bd to leave {m:ad + bc = $cross}.",
        "No fourth multiplication: the cross terms fall out of one product and two subtractions.",
    )
    step(
        StoryTone.Done, 3, null, true,
        listOf(StoryChip("one-digit mults", "$leaves vs $schoolbook", if (leaves < schoolbook) StoryTone.Done else StoryTone.Warn)),
        "{m:$product} = $ac·10${superscript(2 * half)} + $cross·10${superscript(half)} + $bd.",
        "Each product recurses the same way down to single digits: $leaves one-digit multiplications against " +
            "$schoolbook for the schoolbook method" + if (leaves >= schoolbook) ". At this size the bookkeeping still wins." else ".",
    )
    return steps
}

/** A story lab with a size stepper: the steps are rebuilt, and playback restarts, whenever the size moves. */
@Composable
private fun SizedStoryLab(
    intro: String,
    label: String,
    sizes: List<Int>,
    initial: Int,
    build: (Int) -> List<StoryStep>,
) {
    var size by remember { mutableIntStateOf(initial) }
    val steps = remember(size) { build(size) }
    val playback = rememberPlaybackState(key = size, stepCount = steps.size)
    val step = steps[playback.index.coerceIn(0, steps.lastIndex)]
    val at = sizes.indexOf(size)
    Column(modifier = Modifier.fillMaxWidth()) {
        LabIntro(intro, Modifier.padding(bottom = 12.dp))
        GreedyStoryLab(
            step.story,
            playback,
            steps.map { it.status },
            stepper = StoryStepper(label, size, at > 0, at < sizes.lastIndex) { delta -> size = sizes[at + delta] },
        )
    }
}

@Composable
internal fun StrassenLab() = SizedStoryLab(
    intro = "Seven block products instead of eight. Each row is one product of sums of blocks; watch them return, " +
        "then combine into the four blocks of C.",
    label = "matrix size",
    sizes = StrassenSizes,
    initial = 4,
    build = ::strassenSteps,
)

@Composable
internal fun KaratsubaLab() = SizedStoryLab(
    intro = "Three products instead of four. The middle term comes from multiplying the sums and subtracting the " +
        "two products already made.",
    label = "digits",
    sizes = KaratsubaDigits,
    initial = 4,
    build = ::karatsubaSteps,
)

// ── Closest pair ─────────────────────────────────────────────────────────────

private class PairPt(val x: Float, val y: Float)

private val pairPoints = listOf(
    PairPt(0.08f, 0.20f), PairPt(0.14f, 0.62f), PairPt(0.22f, 0.35f), PairPt(0.28f, 0.85f),
    PairPt(0.33f, 0.12f), PairPt(0.36f, 0.55f), PairPt(0.40f, 0.75f), PairPt(0.44f, 0.30f),
    PairPt(0.49f, 0.55f), PairPt(0.52f, 0.58f),
    PairPt(0.60f, 0.22f), PairPt(0.65f, 0.70f), PairPt(0.70f, 0.42f), PairPt(0.75f, 0.88f),
    PairPt(0.80f, 0.15f), PairPt(0.84f, 0.60f), PairPt(0.88f, 0.33f), PairPt(0.93f, 0.78f),
).sortedBy { it.x }

private fun dist(a: PairPt, b: PairPt) = hypot(a.x - b.x, a.y - b.y)

/** A pair drawn joined by a line, in its tone's colour. */
private class PairLine(val a: Int, val b: Int, val tone: StoryTone)

private class PairFrame(
    val tones: Map<Int, StoryTone>,
    val lines: List<PairLine>,
    val split: Float?,
    val delta: Float?,
    val chips: List<StoryChip>,
    val headline: String,
    val body: String,
) {
    val status: String get() = storyPlain(headline) + " " + body
}

private fun f3(v: Float) = "%.3f".format(v)

private fun closestPairFrames(): List<PairFrame> {
    val pts = pairPoints
    val n = pts.size
    val pairs = n * (n - 1) / 2
    fun best(ids: List<Int>): Pair<Float, Pair<Int, Int>> {
        var d = Float.MAX_VALUE
        var pair = -1 to -1
        for (i in ids.indices) for (j in i + 1 until ids.size) {
            val e = dist(pts[ids[i]], pts[ids[j]])
            if (e < d) { d = e; pair = ids[i] to ids[j] }
        }
        return d to pair
    }
    val mid = n / 2
    val split = (pts[mid - 1].x + pts[mid].x) / 2f
    val (dl, pl) = best((0 until mid).toList())
    val (dr, pr) = best((mid until n).toList())
    val delta = minOf(dl, dr)
    val deltaPair = if (dl <= dr) pl else pr
    val strip = pts.indices.filter { abs(pts[it].x - split) <= delta }.sortedBy { pts[it].y }
    var stripBest = delta
    var stripPair: Pair<Int, Int>? = null
    var checks = 0
    for (i in strip.indices) {
        var j = i + 1
        while (j < strip.size && pts[strip[j]].y - pts[strip[i]].y < delta) {
            checks++
            val e = dist(pts[strip[i]], pts[strip[j]])
            if (e < stripBest) { stripBest = e; stripPair = strip[i] to strip[j] }
            j++
        }
    }
    val (bestD, bestPair) = best(pts.indices.toList())
    val stripTones = strip.associateWith { StoryTone.Path }
    val deltaTones = mapOf(deltaPair.first to StoryTone.Answer, deltaPair.second to StoryTone.Answer)

    val frames = mutableListOf<PairFrame>()
    frames += PairFrame(
        emptyMap(), emptyList(), null, null,
        listOf(StoryChip("points", "$n"), StoryChip("all pairs", "$pairs")),
        "Find the two closest of {$n} points.",
        "Checking every pair costs $pairs distances. Split in half and most pairs never need checking.",
    )
    frames += PairFrame(
        deltaTones,
        listOf(PairLine(deltaPair.first, deltaPair.second, StoryTone.Answer)),
        split, null,
        listOf(StoryChip("left", f3(dl), StoryTone.Answer), StoryChip("right", f3(dr))),
        "Split at the median x. The left half's best is {v:${f3(dl)}}.",
        "The right half's best is ${f3(dr)}, so δ = ${f3(delta)}: no pair on one side beats it.",
    )
    frames += PairFrame(
        stripTones + deltaTones,
        listOf(PairLine(deltaPair.first, deltaPair.second, StoryTone.Answer)),
        split, delta,
        listOf(StoryChip("δ", f3(delta)), StoryChip("strip", "${strip.size} points", StoryTone.Path)),
        "Only points within {p:δ} of the line can beat it.",
        "${strip.size} of $n points fall in the strip. Sorted by y, each is compared only with the few just above it.",
    )
    val sp = stripPair ?: bestPair
    frames += PairFrame(
        stripTones + deltaTones + mapOf(sp.first to StoryTone.Active, sp.second to StoryTone.Active),
        listOf(PairLine(deltaPair.first, deltaPair.second, StoryTone.Answer), PairLine(sp.first, sp.second, StoryTone.Active)),
        split, delta,
        listOf(StoryChip("δ", f3(delta)), StoryChip("pair", f3(stripBest), StoryTone.Active), StoryChip("strip", "${strip.size} points")),
        "This pair straddles the line at {${f3(stripBest)}}, less than δ.",
        "So δ drops to ${f3(stripBest)}. Only points within δ of the line are ever checked across it.",
    )
    frames += PairFrame(
        mapOf(bestPair.first to StoryTone.Answer, bestPair.second to StoryTone.Answer),
        listOf(PairLine(bestPair.first, bestPair.second, StoryTone.Answer)),
        split, null,
        listOf(StoryChip("closest", f3(bestD), StoryTone.Answer), StoryChip("strip checks", "$checks"), StoryChip("brute force", "$pairs")),
        "The closest pair is {v:${f3(bestD)}} apart.",
        "It straddles the split, the case the strip exists to catch. The strip took $checks checks; overall " +
            "T(n) = 2T(n/2) + O(n), so O(n log n) against $pairs pairs.",
    )
    return frames
}

internal fun closestPairFrameCount(): Int {
    val frames = closestPairFrames()
    frames.forEach { f ->
        require(f.tones.keys.all { it in pairPoints.indices }) { "closest pair colours a point that is not there" }
        require(f.lines.all { it.a in pairPoints.indices && it.b in pairPoints.indices }) { "closest pair joins a missing point" }
    }
    return frames.size
}

@Composable
internal fun ClosestPairLab() {
    val frames = remember { closestPairFrames() }
    val playback = rememberPlaybackState(key = frames, stepCount = frames.size, initialSpeedMs = 900f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val present = frame.tones.values.toSet() + frame.lines.map { it.tone }
    Column(modifier = Modifier.fillMaxWidth()) {
        LabIntro(
            "Divide and conquer on the plane: solve both halves, then check only the strip near the dividing line.",
            Modifier.padding(bottom = 12.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StoryHeader("SPLIT AT MEDIAN X", "strip ± δ")
                Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1.35f).padding(top = 12.dp)) {
                    val pad = 10.dp.toPx()
                    fun at(p: PairPt) = Offset(pad + p.x * (size.width - 2 * pad), pad + (1f - p.y) * (size.height - 2 * pad - 14.dp.toPx()))
                    val bottom = size.height - 14.dp.toPx()
                    frame.split?.let { sx ->
                        val x = pad + sx * (size.width - 2 * pad)
                        frame.delta?.let { d ->
                            val w = d * (size.width - 2 * pad)
                            drawRoundRect(
                                SimColors.Blue.copy(alpha = 0.12f),
                                Offset(x - w, 0f),
                                Size(2 * w, bottom),
                                androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                            )
                            // δ, measured from the strip's left edge to the line.
                            val y = bottom - 6.dp.toPx()
                            drawLine(muted, Offset(x - w, y), Offset(x, y), 1.dp.toPx())
                            val label = textMeasurer.measure("δ", TextStyle(fontFamily = IBMPlexMono, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                            drawText(label, color = muted, topLeft = Offset(x - w / 2 - label.size.width / 2, y - label.size.height - 1.dp.toPx()))
                        }
                        drawLine(
                            muted.copy(alpha = 0.7f),
                            Offset(x, 0f),
                            Offset(x, bottom),
                            1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                        )
                    }
                    frame.lines.forEach { line ->
                        drawLine(line.tone.color(), at(pairPoints[line.a]), at(pairPoints[line.b]), 2.5.dp.toPx())
                    }
                    pairPoints.forEachIndexed { i, p ->
                        val tone = frame.tones[i]
                        val color = tone?.takeIf { it != StoryTone.Idle }?.color() ?: muted.copy(alpha = 0.55f)
                        drawCircle(color, radius = (if (tone == null || tone == StoryTone.Idle) 5 else 7).dp.toPx(), center = at(p))
                    }
                }
                StoryLegendRow(
                    listOf(StoryTone.Active to "Comparing", StoryTone.Path to "In strip", StoryTone.Answer to "Best so far")
                        .filter { it.first in present }
                        .map { (tone, label) -> Triple(tone.color(), SwatchStyle.Dot, label) },
                    Modifier.padding(top = 14.dp),
                )
            }
        }
        StoryChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        PlaybackTransport(playback, captions = frames.map { it.status })
    }
}
