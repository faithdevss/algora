package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.LocalDarkTheme
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sqrt

// ── Geometry labs ────────────────────────────────────────────────────────────
// Segment Intersection, Rotating Calipers, Convex Hull and Area & Perimeter as tabbed storyboards on
// one plane: a dot grid, lettered points, segments and a filled polygon, with the value being computed
// riding on the figure as a bubble. Under it, a row of term tiles or a formula, then chips and a
// headline. Every number is computed from the coordinates.

private enum class GeoTone { Idle, Blue, Green, Active, Answer, Red }

private class GeoPt(val x: Double, val y: Double)

private class GeoPoint(
    val at: GeoPt,
    val label: String? = null,
    val tone: GeoTone = GeoTone.Idle,
    // Drawn as a red ✕ instead of a dot (a point Graham scan popped).
    val cross: Boolean = false,
    val dim: Boolean = false,
)

private class GeoSeg(
    val a: GeoPt,
    val b: GeoPt,
    val tone: GeoTone = GeoTone.Blue,
    val dashed: Boolean = false,
    val width: Float = 2.5f,
    // Run the line across the whole plane (a caliper).
    val extend: Boolean = false,
)

private class GeoBubble(val at: GeoPt, val text: String, val tone: GeoTone = GeoTone.Active, val dx: Float = 0f, val dy: Float = 0f)

private enum class GeoTileTone { Pending, Current, Done }
private class GeoTile(val top: String, val value: String, val tone: GeoTileTone)

private class GeoFrame(
    val headline: String,
    val body: String,
    val polygon: List<GeoPt> = emptyList(),
    val polygonTone: GeoTone = GeoTone.Blue,
    val segs: List<GeoSeg> = emptyList(),
    val points: List<GeoPoint> = emptyList(),
    val bubbles: List<GeoBubble> = emptyList(),
    val tiles: List<GeoTile> = emptyList(),
    val formula: List<String> = emptyList(),
    val chips: List<StoryChip> = emptyList(),
)

private class GeoTab(val label: String, val frames: List<GeoFrame>, val legend: List<Triple<Color, SwatchStyle, String>>)

private class GeoBounds(val minX: Double, val maxX: Double, val minY: Double, val maxY: Double)

private val GeoGrey = Color.Gray.copy(alpha = 0.4f)

// ── Shared math ──

private fun orient(a: GeoPt, b: GeoPt, c: GeoPt) = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
private fun dist(a: GeoPt, b: GeoPt) = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
private fun mid(a: GeoPt, b: GeoPt) = GeoPt((a.x + b.x) / 2, (a.y + b.y) / 2)

/** 25 → "25", 2.5 → "2.5", 5.656 → "5.66". */
private fun num(v: Double): String {
    if (abs(v - round(v)) < 1e-9) return "${round(v).toLong()}"
    val s = String.format(java.util.Locale.US, "%.2f", v)
    return if (s.endsWith("0")) s.dropLast(1) else s
}
private fun signed(v: Double) = when {
    v > 0 -> "+${num(v)}"
    v < 0 -> "−${num(-v)}"
    else -> "0"
}
/** A factor in a printed product, bracketed when negative: "(−2)". */
private fun factor(v: Double) = if (v < 0) "(−${num(-v)})" else num(v)
private fun coord(p: GeoPt) = "(${num(p.x)}, ${num(p.y)})"

// ── Segment intersection ──

private fun segmentTab(label: String, p1: GeoPt, p2: GeoPt, q1: GeoPt, q2: GeoPt, kind: Int): GeoTab {
    val o = listOf(orient(p1, p2, q1), orient(p1, p2, q2), orient(q1, q2, p1), orient(q1, q2, p2))
    val names = listOf("o₁", "o₂", "o₃", "o₄")
    val tops = listOf("o₁ p→q₁", "o₂ p→q₂", "o₃ q→p₁", "o₄ q→p₂")
    val args = listOf("p₁, p₂, q₁", "p₁, p₂, q₂", "q₁, q₂, p₁", "q₁, q₂, p₂")
    val tested = listOf(q1, q2, p1, p2)
    val testedName = listOf("q₁", "q₂", "p₁", "p₂")
    val lineOf = listOf("p", "p", "q", "q")
    fun bubble(k: Int, tone: GeoTone): GeoBubble {
        val at = tested[k]
        val right = at.x < 7.5 && (at.y >= 4.5 || at.y <= 0.5)
        val sign = if (o[k] > 0) "+" else if (o[k] < 0) "−" else "0"
        return GeoBubble(at, "${names[k]} $sign", tone, dx = if (right) 36f else 0f, dy = if (right) 0f else 22f)
    }
    fun frame(current: Int?, done: Int, headline: String, body: String, formula: List<String>, chips: List<StoryChip>, hit: GeoPt? = null): GeoFrame {
        val tones = mutableListOf(GeoTone.Blue, GeoTone.Blue, GeoTone.Green, GeoTone.Green)
        if (current != null) tones[listOf(2, 3, 0, 1)[current]] = GeoTone.Active
        val points = listOf(
            GeoPoint(p1, "p₁", tones[0]), GeoPoint(p2, "p₂", tones[1]), GeoPoint(q1, "q₁", tones[2]), GeoPoint(q2, "q₂", tones[3]),
        ) + listOfNotNull(hit?.let { GeoPoint(it, coord(it), GeoTone.Answer) })
        val bubbles = (0 until done).map { bubble(it, GeoTone.Green) } + listOfNotNull(current?.let { bubble(it, GeoTone.Active) })
        val tiles = (0 until 4).map { k ->
            GeoTile(
                tops[k], if (k < done || k == current) signed(o[k]) else "?",
                when {
                    k == current -> GeoTileTone.Current
                    k < done -> GeoTileTone.Done
                    else -> GeoTileTone.Pending
                },
            )
        }
        return GeoFrame(
            headline, body, segs = listOf(GeoSeg(p1, p2, GeoTone.Blue), GeoSeg(q1, q2, GeoTone.Green)), points = points,
            bubbles = bubbles, tiles = tiles, formula = formula, chips = chips,
        )
    }
    val frames = mutableListOf(
        frame(
            null, 0, "Two segments: p from p₁ to p₂, and q from q₁ to q₂.",
            "Four orientation tests decide whether they meet, using only multiplication and subtraction.",
            listOf("orient(a, b, c) = (b − a) × (c − a)"), listOf(StoryChip("tests", "0 of 4")),
        ),
    )
    for (k in 0 until 4) {
        val side = if (o[k] > 0) "left" else "right"
        val headline = if (o[k] == 0.0) "${testedName[k]} lies {exactly on} the line through ${lineOf[k]} (${names[k]} = 0)."
        else "${testedName[k]} is {$side} of ${lineOf[k]} (${names[k]} = ${signed(o[k])})."
        val body = when (k) {
            1 -> when {
                o[0] * o[1] < 0 -> "q's ends are on opposite sides of p, so q crosses p's line."
                o[1] == 0.0 -> "A zero means collinear: q₂ is on p's line, maybe on p itself."
                else -> "Both of q's ends are on one side of p."
            }
            3 -> when {
                o[2] * o[3] < 0 -> "p's ends are on opposite sides of q, so p crosses q's line."
                o[3] == 0.0 -> "p₂ is on q's line too."
                else -> "Both of p's ends are on one side of q."
            }
            else -> "The sign says which side of the line through ${lineOf[k]} the point is on: + left, − right, 0 on it."
        }
        frames += frame(k, k, headline, body, listOf("${names[k]} = orient(${args[k]}) = {${signed(o[k])}}"), listOf(StoryChip("tests", "${k + 1} of 4")))
    }
    val t = o[2] / (o[2] - o[3])
    val hit = GeoPt(p1.x + t * (p2.x - p1.x), p1.y + t * (p2.y - p1.y))
    when (kind) {
        0 -> {
            frames += frame(
                null, 4, "Both pairs of signs are opposite, so the segments {v:cross}.",
                "q's ends lie on opposite sides of p, and p's ends on opposite sides of q.",
                listOf("o₁ ≠ o₂ and o₃ ≠ o₄ → {v:intersect}"),
                listOf(StoryChip("tests", "4 of 4"), StoryChip("verdict", "cross", StoryTone.Answer)), hit,
            )
            frames += frame(
                null, 4, "The crossing point is {v:${coord(hit)}}.",
                "Four cross products and one division: O(1), with no slopes and no division by zero for vertical lines.",
                listOf("t = o₃ / (o₃ − o₄) = ${num(t)}", "p₁ + ${num(t)}·(p₂ − p₁) = {v:${coord(hit)}}"),
                listOf(StoryChip("point", coord(hit), StoryTone.Answer), StoryChip("cost", "O(1)")), hit,
            )
        }
        1 -> {
            frames += frame(
                null, 4, "o₂ is 0 and q₂ lies within p's range, so the segments {v:touch} at q₂.",
                "A zero only says collinear. The bounding-box check decides whether the point is actually on the segment.",
                listOf("o₂ = 0 and q₂ is inside p's box → {v:touch}"),
                listOf(StoryChip("tests", "4 of 4"), StoryChip("verdict", "touch", StoryTone.Answer)), q2,
            )
            frames += frame(
                null, 4, "They meet at q₂, {v:${coord(q2)}}, an endpoint.",
                "Skipping the zero case is the classic bug: touching segments would be reported as missing each other.",
                listOf("min(p.x) ≤ q₂.x ≤ max(p.x) → {v:${coord(q2)}}"),
                listOf(StoryChip("point", coord(q2), StoryTone.Answer), StoryChip("cost", "O(1)")), q2,
            )
        }
        else -> {
            val pr = listOf(minOf(p1.x, p2.x), maxOf(p1.x, p2.x))
            val qr = listOf(minOf(q1.x, q2.x), maxOf(q1.x, q2.x))
            frames += frame(
                null, 4, "All four signs are 0: the segments share a line but {w:do not overlap}.",
                "Collinear segments meet only if their ranges overlap, and here ${num(pr[1])} < ${num(qr[0])}.",
                listOf("all four = 0 → compare ranges", "[${num(pr[0])}, ${num(pr[1])}] vs [${num(qr[0])}, ${num(qr[1])}] → {w:apart}"),
                listOf(StoryChip("tests", "4 of 4"), StoryChip("verdict", "apart", StoryTone.Warn)),
            )
            frames += frame(
                null, 4, "Same line, different stretches of it: {w:no intersection}.",
                "Had the ranges overlapped, the overlap itself would be the answer, a segment rather than a point.",
                listOf("max(p.x) = ${num(pr[1])} < ${num(qr[0])} = min(q.x) → {w:no intersection}"),
                listOf(StoryChip("verdict", "none", StoryTone.Warn), StoryChip("cost", "O(1)")),
            )
        }
    }
    return GeoTab(
        label, frames,
        listOf(
            Triple(SimColors.Active, SwatchStyle.Fill, "Current test"),
            Triple(SimColors.Green, SwatchStyle.Fill, "Tested"),
            Triple(SimColors.Answer, SwatchStyle.Fill, "Intersection"),
        ),
    )
}

private fun segmentTabs() = listOf(
    segmentTab("Crossing", GeoPt(1.0, 1.0), GeoPt(8.0, 4.0), GeoPt(2.0, 5.0), GeoPt(7.0, 0.0), 0),
    segmentTab("Touching", GeoPt(1.0, 1.0), GeoPt(7.0, 4.0), GeoPt(3.0, 5.0), GeoPt(5.0, 3.0), 1),
    segmentTab("Collinear", GeoPt(1.0, 1.0), GeoPt(3.0, 2.0), GeoPt(5.0, 3.0), GeoPt(7.0, 4.0), 2),
)

// ── Hull shared by calipers and convex hull ──

private val hullPts = listOf(GeoPt(3.0, 0.0), GeoPt(7.0, 1.0), GeoPt(9.0, 3.0), GeoPt(6.0, 5.0), GeoPt(3.0, 5.0), GeoPt(1.0, 4.0), GeoPt(0.0, 2.0))
private val hullNames = listOf("A", "B", "C", "D", "E", "F", "G")
private val innerPts = listOf(GeoPt(2.0, 3.0), GeoPt(4.0, 2.0), GeoPt(6.0, 3.0), GeoPt(5.0, 4.0))
private val innerNames = listOf("H", "I", "J", "K")

// ── Rotating calipers ──

private fun calipersTabs(): List<GeoTab> {
    val h = hullPts
    val n = h.size
    fun area(i: Int, j: Int) = abs(orient(h[i % n], h[(i + 1) % n], h[j % n]))
    fun label(i: Int) = hullNames[i % n]
    fun points(lit: Set<Int>) =
        h.indices.map { GeoPoint(h[it], hullNames[it], if (it in lit) GeoTone.Active else GeoTone.Blue) } +
            innerPts.map { GeoPoint(it, dim = true) }
    // Antipodal vertex per edge, advanced monotonically: the caliper never goes back.
    val anti = mutableListOf<Int>()
    var j = 1
    for (i in 0 until n) {
        while (area(i, j + 1) > area(i, j)) j++
        anti += j % n
    }
    fun calipers(i: Int, a: Int): List<GeoSeg> {
        val p = h[i]
        val q = h[(i + 1) % n]
        val d = GeoPt(q.x - p.x, q.y - p.y)
        return listOf(
            GeoSeg(p, q, GeoTone.Active, width = 4f),
            GeoSeg(p, q, GeoTone.Active, dashed = true, width = 1.5f, extend = true),
            GeoSeg(h[a], GeoPt(h[a].x + d.x, h[a].y + d.y), GeoTone.Active, dashed = true, width = 1.5f, extend = true),
        )
    }
    val hullSegs = h.indices.map { GeoSeg(h[it], h[(it + 1) % n], GeoTone.Blue) }
    fun foot(e: Int, v: Int): GeoPt {
        val p = h[e]
        val q = h[(e + 1) % n]
        val d = GeoPt(q.x - p.x, q.y - p.y)
        val t = ((h[v].x - p.x) * d.x + (h[v].y - p.y) * d.y) / (d.x * d.x + d.y * d.y)
        return GeoPt(p.x + t * d.x, p.y + t * d.y)
    }

    val dia = mutableListOf(
        GeoFrame(
            "The diameter is the farthest pair of points, and both ends are always hull vertices.",
            "Rotating calipers checks O(n) antipodal pairs instead of all n² pairs.",
            polygon = h, segs = hullSegs, points = points(emptySet()), chips = listOf(StoryChip("hull", "$n vertices")),
        ),
    )
    var best = Triple(0, 0, 0.0)
    for (i in 0 until n) {
        val a = anti[i]
        val c1 = Triple(i, a, dist(h[i], h[a]))
        val c2 = Triple((i + 1) % n, a, dist(h[(i + 1) % n], h[a]))
        val win = if (c1.third >= c2.third) c1 else c2
        val lose = if (c1.third >= c2.third) c2 else c1
        val improved = win.third > best.third
        if (improved) best = win
        val pair = "${label(best.first)}–${label(best.second)}"
        val e = "${label(i)}${label(i + 1)}"
        dia += GeoFrame(
            "${label(a)} is farthest from edge $e, so try ${label(i)}–${label(a)} and ${label(i + 1)}–${label(a)}. " +
                "{v:${label(win.first)}–${label(win.second)}} is longer.",
            if (improved) "Only the $n hull vertices can be farthest apart. Each edge moves the opposite caliper forward, never back."
            else "Still shorter than $pair, so the best pair stands.",
            polygon = h,
            segs = hullSegs + calipers(i, a) + listOf(
                GeoSeg(h[lose.first], h[lose.second], GeoTone.Idle, dashed = true, width = 1.5f),
                GeoSeg(h[best.first], h[best.second], GeoTone.Answer, width = 3f),
            ),
            points = points(setOf(i, (i + 1) % n, a)),
            bubbles = listOf(
                GeoBubble(mid(h[lose.first], h[lose.second]), num(lose.third), GeoTone.Idle),
                GeoBubble(mid(h[best.first], h[best.second]), num(best.third), GeoTone.Answer),
            ),
            formula = listOf(
                "|$e × ${label(i)}${label(a)}| = {${num(area(i, a))}}",
                "> |$e × ${label(i)}${label(a + 1)}| = ${num(area(i, a + 1))} → stay on ${label(a)}",
            ),
            chips = listOf(StoryChip("best", "$pair ${num(best.third)}", StoryTone.Answer), StoryChip("edge", "${i + 1} of $n")),
        )
    }
    val pair = "${label(best.first)}–${label(best.second)}"
    dia += GeoFrame(
        "The diameter is {v:$pair = ${num(best.third)}}.",
        "One lap of the calipers: O(n) once the hull is built, against n² for every pair of points.",
        polygon = h, segs = hullSegs + GeoSeg(h[best.first], h[best.second], GeoTone.Answer, width = 3f),
        points = points(setOf(best.first, best.second)),
        bubbles = listOf(GeoBubble(mid(h[best.first], h[best.second]), num(best.third), GeoTone.Answer)),
        formula = listOf("diameter = |$pair| = {v:${num(best.third)}}"),
        chips = listOf(StoryChip("best", "$pair ${num(best.third)}", StoryTone.Answer), StoryChip("edges", "$n of $n")),
    )

    val wid = mutableListOf(
        GeoFrame(
            "The width is the narrowest gap between two parallel lines that hold the hull.",
            "One of the two lines always lies along a hull edge, so trying each edge is enough.",
            polygon = h, segs = hullSegs, points = points(emptySet()), chips = listOf(StoryChip("hull", "$n vertices")),
        ),
    )
    var bestW = Triple(0, 0, Double.POSITIVE_INFINITY)
    for (i in 0 until n) {
        val a = anti[i]
        val len = dist(h[i], h[(i + 1) % n])
        val w = area(i, a) / len
        val improved = w < bestW.third
        if (improved) bestW = Triple(i, a, w)
        val bf = foot(bestW.first, bestW.second)
        val e = "${label(i)}${label(i + 1)}"
        wid += GeoFrame(
            "Edge $e with ${label(a)} opposite: the calipers are {${num(w)}} apart.",
            if (improved) "That is the narrowest so far. The same antipodal walk as the diameter, measuring a height instead of a distance."
            else "Wider than ${num(bestW.third)}, so the narrowest stays.",
            polygon = h,
            segs = hullSegs + calipers(i, a) + listOf(
                GeoSeg(h[a], foot(i, a), GeoTone.Idle, dashed = true, width = 1.5f),
                GeoSeg(h[bestW.second], bf, GeoTone.Answer, width = 3f),
            ),
            points = points(setOf(i, (i + 1) % n, a)),
            bubbles = listOf(GeoBubble(mid(h[bestW.second], bf), num(bestW.third), GeoTone.Answer)),
            formula = listOf("|$e × ${label(i)}${label(a)}| / |$e|", "= ${num(area(i, a))} / ${num(len)} = {${num(w)}}"),
            chips = listOf(StoryChip("narrowest", num(bestW.third), StoryTone.Answer), StoryChip("edge", "${i + 1} of $n")),
        )
    }
    val bf = foot(bestW.first, bestW.second)
    val be = "${label(bestW.first)}${label(bestW.first + 1)}"
    wid += GeoFrame(
        "The width is {v:${num(bestW.third)}}, set by edge $be and ${label(bestW.second)}.",
        "It is the narrowest slot the shape could slide through, found in one O(n) lap.",
        polygon = h, segs = hullSegs + calipers(bestW.first, bestW.second) + GeoSeg(h[bestW.second], bf, GeoTone.Answer, width = 3f),
        points = points(setOf(bestW.first, (bestW.first + 1) % n, bestW.second)),
        bubbles = listOf(GeoBubble(mid(h[bestW.second], bf), num(bestW.third), GeoTone.Answer)),
        formula = listOf("width = {v:${num(bestW.third)}}, edge $be to ${label(bestW.second)}"),
        chips = listOf(StoryChip("width", num(bestW.third), StoryTone.Answer), StoryChip("edges", "$n of $n")),
    )
    val legend = listOf(
        Triple(SimColors.Blue, SwatchStyle.Fill, "Hull"),
        Triple(SimColors.Active, SwatchStyle.Fill, "Caliper pair"),
        Triple(SimColors.Answer, SwatchStyle.Fill, "Best so far"),
    )
    return listOf(GeoTab("Diameter", dia, legend), GeoTab("Width", wid, legend))
}

// ── Convex hull ──

private fun hullTabs(): List<GeoTab> {
    val all = hullPts + innerPts
    val names = hullNames + innerNames
    val pivot = 0
    val order = listOf(pivot) + all.indices.filter { it != pivot }.sortedWith(
        compareBy({ atan2(all[it].y - all[pivot].y, all[it].x - all[pivot].x) }, { dist(all[pivot], all[it]) }),
    )
    fun crossText(a: Int, b: Int, c: Int): String {
        val p = all[a]
        val q = all[b]
        val r = all[c]
        val v = orient(p, q, r)
        return "cross(${names[a]}, ${names[b]}, ${names[c]}) = ${factor(q.x - p.x)}${factor(r.y - p.y)} − " +
            "${factor(q.y - p.y)}${factor(r.x - p.x)} = {${if (v > 0) "+" else ""}${num(v)}} ${if (v > 0) ">" else "≤"} 0"
    }
    fun pts(stack: List<Int>, popped: Set<Int>, testing: Int?, reached: Set<Int>) = all.indices.map { i ->
        when {
            i in popped -> GeoPoint(all[i], names[i], GeoTone.Red, cross = true)
            i == testing -> GeoPoint(all[i], names[i], GeoTone.Active)
            i in stack -> GeoPoint(all[i], names[i], GeoTone.Blue)
            else -> GeoPoint(all[i], names[i], GeoTone.Idle, dim = i !in reached)
        }
    }
    fun chain(stack: List<Int>) = stack.zipWithNext { a, b -> GeoSeg(all[a], all[b], GeoTone.Blue) }
    fun stackChip(stack: List<Int>) = StoryChip("stack", stack.joinToString(" ") { names[it] })

    val graham = mutableListOf(
        GeoFrame(
            "Start from the lowest point, {${names[pivot]}}, and sort the rest by angle around it.",
            "Walking the points in that order, the hull only ever turns left.",
            points = pts(listOf(pivot), emptySet(), null, setOf(pivot)),
            formula = listOf("sorted: " + order.drop(1).joinToString(" ") { names[it] }),
            chips = listOf(StoryChip("stack", names[pivot]), StoryChip("popped", "0")),
        ),
    )
    val stack = mutableListOf(order[0], order[1])
    val popped = mutableSetOf<Int>()
    val reached = mutableSetOf(order[0], order[1])
    for (k in 2 until order.size) {
        val p = order[k]
        reached += p
        while (stack.size >= 2) {
            val a = stack[stack.size - 2]
            val b = stack[stack.size - 1]
            val v = orient(all[a], all[b], all[p])
            val segs = chain(stack) + GeoSeg(all[b], all[p], GeoTone.Active, dashed = true, width = 2f)
            val at = mid(all[b], all[p])
            if (v > 0) {
                stack += p
                graham += GeoFrame(
                    "{+${num(v)}} is a left turn, so ${names[p]} is pushed onto the stack.",
                    if (popped.isEmpty()) "A left turn keeps the chain convex, so ${names[p]} might be on the hull."
                    else "${popped.size} inner point${if (popped.size == 1) " was" else "s were"} already popped on right turns. The chain now has ${stack.size} vertices.",
                    segs = segs, points = pts(stack, popped, p, reached),
                    bubbles = listOf(GeoBubble(at, "+${num(v)} left", GeoTone.Active, dy = 16f)),
                    formula = listOf(crossText(a, b, p)), chips = listOf(stackChip(stack), StoryChip("popped", "${popped.size}")),
                )
                break
            }
            popped += b
            stack.removeAt(stack.lastIndex)
            graham += GeoFrame(
                "{w:${signed(v)}} is a right turn, so ${names[b]} is popped.",
                "${names[b]} sits inside the triangle ${names[a]}, ${names[p]} and the pivot, so it cannot be on the hull.",
                segs = segs, points = pts(stack + b, popped, p, reached),
                bubbles = listOf(GeoBubble(at, "${signed(v)} right", GeoTone.Red, dy = 16f)),
                formula = listOf(crossText(a, b, p)), chips = listOf(stackChip(stack), StoryChip("popped", "${popped.size}")),
            )
        }
    }
    val hullOrder = stack.joinToString(" ") { names[it] }
    graham += GeoFrame(
        "Close the stack back to ${names[pivot]}: the hull is {v:$hullOrder}.",
        "Every point is pushed once and popped at most once, so after the O(n log n) sort the scan is O(n).",
        polygon = stack.map { all[it] }, segs = chain(stack + stack[0]), points = pts(stack, popped, null, all.indices.toSet()),
        formula = listOf("hull = {v:$hullOrder}"),
        chips = listOf(StoryChip("hull", hullOrder, StoryTone.Answer), StoryChip("popped", "${popped.size}")),
    )

    val n = all.size
    val everyone = all.indices.toSet()
    val jarvis = mutableListOf(
        GeoFrame(
            "Gift wrapping starts at the lowest point, {${names[pivot]}}, which must be on the hull.",
            "From each hull point it finds the next by checking every other point.",
            points = pts(listOf(pivot), emptySet(), pivot, everyone), formula = listOf("start = lowest point = ${names[pivot]}"),
            chips = listOf(StoryChip("hull", names[pivot]), StoryChip("checks", "0")),
        ),
    )
    val hull = mutableListOf(pivot)
    var cur = pivot
    var checks = 0
    while (hull.size <= n) {
        var next = (cur + 1) % n
        for (c in 0 until n) if (c != cur && orient(all[cur], all[next], all[c]) < 0) next = c
        checks += n - 1
        val rays = all.indices.filter { it != cur }.map { GeoSeg(all[cur], all[it], GeoTone.Idle, dashed = true, width = 1f) }
        val closing = next == pivot
        jarvis += GeoFrame(
            if (closing) "From ${names[cur]}, the wrap comes back to {${names[next]}}: the hull is closed."
            else "From ${names[cur]}, every point is left of ${names[cur]}→${names[next]}, so {${names[next]}} is next.",
            "Each hull vertex costs one pass over all n points, so the march is O(nh).",
            segs = rays + chain(hull) + GeoSeg(all[cur], all[next], GeoTone.Active, width = 3f),
            points = pts(hull, emptySet(), cur, everyone),
            formula = listOf("checked ${n - 1} points → next = {${names[next]}}"),
            chips = listOf(StoryChip("hull", hull.joinToString(" ") { names[it] }), StoryChip("checks", "$checks")),
        )
        if (closing) break
        hull += next
        cur = next
    }
    val jOrder = hull.joinToString(" ") { names[it] }
    jarvis += GeoFrame(
        "Same hull, {v:$jOrder}, in ${hull.size} passes.",
        "O(nh) beats Graham's O(n log n) when the hull has few vertices, and loses when most points are on it.",
        polygon = hull.map { all[it] }, segs = chain(hull + pivot), points = pts(hull, emptySet(), null, everyone),
        formula = listOf("${hull.size} passes × ${n - 1} checks = {v:$checks}"),
        chips = listOf(StoryChip("hull", jOrder, StoryTone.Answer), StoryChip("checks", "$checks")),
    )
    return listOf(
        GeoTab(
            "Graham scan", graham,
            listOf(
                Triple(SimColors.Blue, SwatchStyle.Fill, "Stack (hull so far)"),
                Triple(SimColors.Active, SwatchStyle.Fill, "Testing"),
                Triple(SimColors.Red, SwatchStyle.Fill, "Popped"),
                Triple(GeoGrey, SwatchStyle.Fill, "Not reached"),
            ),
        ),
        GeoTab(
            "Jarvis march", jarvis,
            listOf(
                Triple(SimColors.Blue, SwatchStyle.Fill, "Hull so far"),
                Triple(SimColors.Active, SwatchStyle.Fill, "Current"),
                Triple(GeoGrey, SwatchStyle.Fill, "Checked"),
            ),
        ),
    )
}

// ── Area & perimeter ──

private fun areaTabs(): List<GeoTab> {
    val pts = listOf(GeoPt(1.0, 4.0), GeoPt(4.0, 2.0), GeoPt(7.0, 3.0), GeoPt(7.0, 0.0), GeoPt(1.0, 0.0))
    val names = listOf("A", "B", "C", "D", "E")
    // Walked E → D → C → B → A → E, the order the terms are listed in.
    val walk = listOf(4, 3, 2, 1, 0)
    val edges = walk.indices.map { walk[it] to walk[(it + 1) % walk.size] }
    fun pointsFor(lit: Set<Int>) = pts.indices.map {
        GeoPoint(pts[it], "${names[it]} (${num(pts[it].x)},${num(pts[it].y)})", if (it in lit) GeoTone.Active else GeoTone.Idle)
    }
    fun segs(done: Int, current: Int?) = edges.indices.map { k ->
        val (a, b) = edges[k]
        when {
            k == current -> GeoSeg(pts[a], pts[b], GeoTone.Active, width = 3.5f)
            k < done -> GeoSeg(pts[a], pts[b], GeoTone.Green)
            else -> GeoSeg(pts[a], pts[b], GeoTone.Idle, dashed = true, width = 1.5f)
        }
    }
    fun edgeName(k: Int) = "${names[edges[k].first]}→${names[edges[k].second]}"
    fun tone(k: Int, done: Int, current: Int?) = when {
        k == current -> GeoTileTone.Current
        k < done -> GeoTileTone.Done
        else -> GeoTileTone.Pending
    }
    val terms = edges.map { (a, b) -> pts[a].x * pts[b].y - pts[b].x * pts[a].y }
    fun areaTiles(done: Int, current: Int?) = edges.indices.map { GeoTile(edgeName(it), signed(terms[it]), tone(it, done, current)) }

    val area = mutableListOf(
        GeoFrame(
            "The shoelace formula adds one cross term per edge, walking around the polygon.",
            "Each term is twice the signed area of the triangle an edge makes with the origin.",
            polygon = pts, polygonTone = GeoTone.Green, segs = segs(0, null), points = pointsFor(emptySet()), tiles = areaTiles(0, null),
            formula = listOf("Σ (xᵢ·yᵢ₊₁ − xᵢ₊₁·yᵢ) over every edge"), chips = listOf(StoryChip("sum", "0")),
        ),
    )
    var sum = 0.0
    for (k in edges.indices) {
        val (a, b) = edges[k]
        sum += terms[k]
        val pa = pts[a]
        val pb = pts[b]
        area += GeoFrame(
            "Edge ${edgeName(k)} adds {${signed(terms[k])}}, so the running sum is ${num(sum)}.",
            when {
                k == edges.lastIndex -> "That closes the loop, and the negative term takes back area that was counted twice."
                a == 1 || b == 1 -> "B is a dent, yet the signed terms still work out. Area = |Σ| ÷ 2 once A→E is added."
                else -> "Terms can be negative. They cancel area counted twice. Area = |Σ| ÷ 2 at the end."
            },
            polygon = pts, polygonTone = GeoTone.Green, segs = segs(k, k), points = pointsFor(setOf(a, b)),
            bubbles = listOf(GeoBubble(mid(pa, pb), signed(terms[k]), GeoTone.Active, dy = -14f)),
            tiles = areaTiles(k, k),
            formula = listOf("x${names[a]}·y${names[b]} − x${names[b]}·y${names[a]} = ${num(pa.x)}·${num(pb.y)} − ${num(pb.x)}·${num(pa.y)} = {${signed(terms[k])}}"),
            chips = listOf(StoryChip("term", signed(terms[k])), StoryChip("sum", num(sum))),
        )
    }
    area += GeoFrame(
        "Area = |${num(sum)}| ÷ 2 = {v:${num(abs(sum) / 2)}} square units.",
        "n terms, O(n), and it works for any simple polygon, dents included.",
        polygon = pts, polygonTone = GeoTone.Green, segs = segs(edges.size, null), points = pointsFor(emptySet()), tiles = areaTiles(edges.size, null),
        formula = listOf("area = |${num(sum)}| ÷ 2 = {v:${num(abs(sum) / 2)}}"),
        chips = listOf(StoryChip("sum", num(sum)), StoryChip("area", num(abs(sum) / 2), StoryTone.Answer)),
    )

    val lens = edges.map { (a, b) -> dist(pts[a], pts[b]) }
    fun lenTiles(done: Int, current: Int?) = edges.indices.map { GeoTile(edgeName(it), num(lens[it]), tone(it, done, current)) }
    val perim = mutableListOf(
        GeoFrame(
            "The perimeter adds up the length of each edge.", "Each length is Pythagoras on the edge's run and rise.",
            polygon = pts, polygonTone = GeoTone.Green, segs = segs(0, null), points = pointsFor(emptySet()), tiles = lenTiles(0, null),
            formula = listOf("Σ √(Δx² + Δy²) over every edge"), chips = listOf(StoryChip("total", "0")),
        ),
    )
    var total = 0.0
    for (k in edges.indices) {
        val (a, b) = edges[k]
        total += lens[k]
        val dx = abs(pts[b].x - pts[a].x)
        val dy = abs(pts[b].y - pts[a].y)
        perim += GeoFrame(
            "Edge ${edgeName(k)} is {${num(lens[k])}} long, so the total is ${num(total)}.",
            if (dx == 0.0 || dy == 0.0) "An axis-aligned edge needs no square root: its length is just the run or the rise."
            else "A slanted edge is the hypotenuse of its run and rise.",
            polygon = pts, polygonTone = GeoTone.Green, segs = segs(k, k), points = pointsFor(setOf(a, b)),
            bubbles = listOf(GeoBubble(mid(pts[a], pts[b]), num(lens[k]), GeoTone.Active, dy = -14f)),
            tiles = lenTiles(k, k),
            formula = listOf("|${edgeName(k)}| = √(${num(dx)}² + ${num(dy)}²) = {${num(lens[k])}}"),
            chips = listOf(StoryChip("edge", num(lens[k])), StoryChip("total", num(total))),
        )
    }
    perim += GeoFrame(
        "Perimeter = {v:${num(total)}} units.", "Unlike area, perimeter never cancels: every edge counts in full.",
        polygon = pts, polygonTone = GeoTone.Green, segs = segs(edges.size, null), points = pointsFor(emptySet()), tiles = lenTiles(edges.size, null),
        formula = listOf("perimeter = {v:${num(total)}}"), chips = listOf(StoryChip("perimeter", num(total), StoryTone.Answer)),
    )
    return listOf(
        GeoTab(
            "Area", area,
            listOf(
                Triple(SimColors.Active, SwatchStyle.Fill, "Current edge"),
                Triple(SimColors.Green, SwatchStyle.Fill, "Term added"),
                Triple(GeoGrey, SwatchStyle.Fill, "Still to add"),
            ),
        ),
        GeoTab(
            "Perimeter", perim,
            listOf(
                Triple(SimColors.Active, SwatchStyle.Fill, "Current edge"),
                Triple(SimColors.Green, SwatchStyle.Fill, "Length added"),
                Triple(GeoGrey, SwatchStyle.Fill, "Still to add"),
            ),
        ),
    )
}

// ── Lab ──

internal val geometryTopicIds = setOf("line_intersection", "rotating_calipers", "convex_hull", "polygon_area")

private fun geometryLab(topicId: String): Pair<GeoBounds, List<GeoTab>> = when (topicId) {
    "line_intersection" -> GeoBounds(0.0, 8.0, 0.0, 5.0) to segmentTabs()
    "rotating_calipers" -> GeoBounds(0.0, 9.0, 0.0, 5.0) to calipersTabs()
    "convex_hull" -> GeoBounds(0.0, 9.0, 0.0, 5.0) to hullTabs()
    else -> GeoBounds(0.0, 8.0, 0.0, 4.5) to areaTabs()
}

internal fun geometryFrameCount(topicId: String): Int = geometryLab(topicId).second.sumOf { it.frames.size }

@Composable
internal fun GeometryLabSection(topicId: String) {
    val (bounds, tabs) = remember(topicId) { geometryLab(topicId) }
    var tab by rememberSaveable(topicId) { mutableIntStateOf(0) }
    val frames = tabs[tab].frames
    val playback = rememberPlaybackState(key = topicId to tab, stepCount = frames.size, initialSpeedMs = 1000f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                LabSegments(tabs.map { it.label }, tab) { tab = it }
                GeoPlane(
                    bounds, frame,
                    Modifier
                        .padding(top = 14.dp)
                        .fillMaxWidth()
                        .height(196.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.16f)),
                )
                if (frame.tiles.isNotEmpty()) {
                    Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        frame.tiles.forEach { GeoTileView(it, Modifier.weight(1f)) }
                    }
                }
                if (frame.formula.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .background(SimColors.Tint, RoundedCornerShape(10.dp))
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        frame.formula.forEach { line ->
                            Text(
                                storyAnnotated(line), fontFamily = IBMPlexMono, fontSize = 13.sp, color = onSurface.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center, maxLines = 2,
                            )
                        }
                    }
                }
                StoryLegendRow(tabs[tab].legend, Modifier.padding(top = 14.dp))
            }
        }
        StoryChips(frame.chips, Modifier.padding(top = 16.dp))
        LabStoryNarration(frame.headline, frame.body, Modifier.padding(top = 16.dp))
        PlaybackTransport(playback, captions = frames.map { storyPlain(it.headline) })
    }
}

@Composable
private fun geoColor(tone: GeoTone): Color = when (tone) {
    GeoTone.Idle -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    GeoTone.Blue -> SimColors.Blue
    GeoTone.Green -> SimColors.Green
    GeoTone.Active -> SimColors.Active
    GeoTone.Answer -> SimColors.Answer
    GeoTone.Red -> SimColors.Red
}

@Composable
private fun geoInk(tone: GeoTone): Color = when (tone) {
    GeoTone.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    GeoTone.Blue -> StoryTone.Path.ink()
    GeoTone.Green -> StoryTone.Done.ink()
    GeoTone.Active -> StoryTone.Active.ink()
    GeoTone.Answer -> StoryTone.Answer.ink()
    GeoTone.Red -> StoryTone.Warn.ink()
}

@Composable
private fun GeoPlane(bounds: GeoBounds, frame: GeoFrame, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val colors = GeoTone.entries.associateWith { geoColor(it) }
    val inks = GeoTone.entries.associateWith { geoInk(it) }
    val labelStyle = TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 11.sp)
    val bubbleStyle = TextStyle(fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    Canvas(modifier) {
        val pad = 26.dp.toPx()
        fun at(p: GeoPt) = Offset(
            (pad + (p.x - bounds.minX) / (bounds.maxX - bounds.minX) * (size.width - 2 * pad)).toFloat(),
            (size.height - pad - (p.y - bounds.minY) / (bounds.maxY - bounds.minY) * (size.height - 2 * pad)).toFloat(),
        )
        // Dot grid on the integer lattice.
        for (x in ceil(bounds.minX).toInt()..floor(bounds.maxX).toInt()) {
            for (y in ceil(bounds.minY).toInt()..floor(bounds.maxY).toInt()) {
                drawCircle(muted.copy(alpha = 0.35f), 1.dp.toPx(), at(GeoPt(x.toDouble(), y.toDouble())))
            }
        }
        if (frame.polygon.isNotEmpty()) {
            val path = Path().apply {
                val first = at(frame.polygon[0])
                moveTo(first.x, first.y)
                frame.polygon.drop(1).forEach { val c = at(it); lineTo(c.x, c.y) }
                close()
            }
            drawPath(path, colors.getValue(frame.polygonTone).copy(alpha = 0.12f))
        }
        frame.segs.forEach { seg ->
            var a = at(seg.a)
            var b = at(seg.b)
            if (seg.extend) {
                val d = b - a
                val len = maxOf(d.getDistance(), 0.001f)
                val reach = size.width * 2
                a -= d / len * reach
                b += d / len * reach
            }
            drawLine(
                colors.getValue(seg.tone), a, b, seg.width.dp.toPx(), StrokeCap.Round,
                pathEffect = if (seg.dashed) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null,
            )
        }
        frame.points.forEach { point ->
            val c = at(point.at)
            val color = colors.getValue(point.tone)
            if (point.cross) {
                val r = 6.dp.toPx()
                drawLine(SimColors.Red, c + Offset(-r, -r), c + Offset(r, r), 2.dp.toPx())
                drawLine(SimColors.Red, c + Offset(r, -r), c + Offset(-r, r), 2.dp.toPx())
            } else {
                val r = if (point.tone == GeoTone.Idle) 3.5.dp.toPx() else 5.5.dp.toPx()
                drawCircle(if (point.dim) color.copy(alpha = color.alpha * 0.5f) else color, r, c)
            }
            point.label?.let { text ->
                val ink = if (point.dim) muted.copy(alpha = 0.5f) else inks.getValue(point.tone)
                val layout = measurer.measure(text, labelStyle.copy(color = ink))
                // Labels sit outside the figure: left of points on the left half, right on the right half.
                val left = point.at.x < (bounds.minX + bounds.maxX) / 2
                val below = point.at.y <= bounds.minY + 0.3
                val x = if (left) c.x - 9.dp.toPx() - layout.size.width else c.x + 9.dp.toPx()
                val y = c.y + (if (below) 12.dp.toPx() else -10.dp.toPx()) - layout.size.height / 2f
                drawText(layout, topLeft = Offset(x, y))
            }
        }
        frame.bubbles.forEach { bubble ->
            val c = at(bubble.at)
            val (fill, ink) = when (bubble.tone) {
                GeoTone.Active -> SimColors.Active to Color(0xFF1F1A0A)
                GeoTone.Answer -> SimColors.Answer to Color.White
                GeoTone.Green -> SimColors.Green.copy(alpha = 0.3f) to inks.getValue(GeoTone.Green)
                GeoTone.Red -> SimColors.Red to Color.White
                else -> surface.copy(alpha = 0.9f) to onSurface.copy(alpha = 0.8f)
            }
            val layout = measurer.measure(bubble.text, bubbleStyle.copy(color = ink))
            val m = Offset(c.x + bubble.dx.dp.toPx(), c.y + bubble.dy.dp.toPx())
            val padX = 6.dp.toPx()
            val padY = 3.dp.toPx()
            drawRoundRect(
                fill, Offset(m.x - layout.size.width / 2f - padX, m.y - layout.size.height / 2f - padY),
                Size(layout.size.width + 2 * padX, layout.size.height + 2 * padY), CornerRadius(6.dp.toPx()),
            )
            drawText(layout, topLeft = Offset(m.x - layout.size.width / 2f, m.y - layout.size.height / 2f))
        }
    }
}

@Composable
private fun GeoTileView(tile: GeoTile, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (fill, ink) = when (tile.tone) {
        GeoTileTone.Pending -> muted.copy(alpha = 0.08f) to muted.copy(alpha = 0.6f)
        GeoTileTone.Current -> SimColors.Active to Color(0xFF1F1A0A)
        GeoTileTone.Done -> SimColors.Green.copy(alpha = if (LocalDarkTheme.current) 0.22f else 0.16f) to StoryTone.Done.ink()
    }
    Column(
        modifier = modifier.height(50.dp).background(fill, RoundedCornerShape(8.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(tile.top, fontFamily = IBMPlexMono, fontSize = 10.sp, color = ink.copy(alpha = 0.85f), maxLines = 1)
        Text(tile.value, fontFamily = IBMPlexMono, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
    }
}
