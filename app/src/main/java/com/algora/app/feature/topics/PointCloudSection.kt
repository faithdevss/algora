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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sqrt

// ── Point cloud player ───────────────────────────────────────────────────────
// The 2D-feature-space labs: clustering (k-means, DBSCAN, hierarchical), the two geometric
// classifiers (k-NN, naive Bayes), the axis-aligned splits of a decision tree, and PCA's principal
// axis. All of them are "points on a plane plus one overlay", so they share a scatter renderer and
// differ only in the overlay each frame carries — centroids, circles, split lines, or projections.
//
// Datasets are generated once from a fixed-seed LCG so every run of the app shows the same picture
// and the narration in each frame stays true.

private class P(val x: Float, val y: Float, val label: Int = -1)

private class Dot(val point: P, val group: Int, val emphasis: Emphasis = Emphasis.NORMAL)

private enum class Emphasis { NORMAL, ACTIVE, FADED, QUERY }

private class Segment(val from: P, val to: P, val color: Color, val dashed: Boolean = false)

private class Ring(val center: P, val radius: Float, val color: Color)

private class Region(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val color: Color)

private class CloudFrame(
    val status: String,
    val dots: List<Dot>,
    val centroids: List<Dot> = emptyList(),
    val segments: List<Segment> = emptyList(),
    val rings: List<Ring> = emptyList(),
    val regions: List<Region> = emptyList(),
    val readout: String? = null,
    // A bar strip drawn under the scatter. OPTICS' reachability plot is the whole point of the
    // algorithm and cannot be read off the scatter, and HDBSCAN's cluster stabilities are the same
    // shape of data — so this is a profile, not a chart type.
    val profile: List<ProfileBar> = emptyList(),
    val profileLabel: String? = null,
    // Closed polylines in data coordinates. Rings can only draw circles, and a Gaussian mixture's
    // whole advantage over k-means is that its components are not circular.
    val ellipses: List<List<P>> = emptyList(),
)

private class ProfileBar(val height: Float, val group: Int, val emphasis: Emphasis = Emphasis.NORMAL)

private class CloudConfig(
    val intro: String,
    val legend: List<Pair<Color, String>>,
    val build: () -> List<CloudFrame>,
)

// Group 0..n are cluster/class colours; -1 means "unassigned".
private val CloudColors = listOf(
    Color(0xFF0EA5E9), Color(0xFFF97316), Color(0xFF10B981),
    Color(0xFFEC4899), Color(0xFF6366F1),
)
private val UnassignedColor = Color(0xFF94A3B8)
private val QueryColor = Color(0xFFFACC15)
private val AxisColor = Color(0xFF7C3AED)

private fun groupColor(group: Int): Color =
    if (group < 0) UnassignedColor else CloudColors[group % CloudColors.size]

// ── Data ─────────────────────────────────────────────────────────────────────

/** Small LCG — the datasets have to be identical on every launch for the narration to hold. */
private class Lcg(private var state: Int) {
    fun next(): Float {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7fff) / 32767f
    }

    fun jitter(scale: Float): Float = (next() - 0.5f) * scale
}

private fun blob(
    rng: Lcg,
    cx: Float,
    cy: Float,
    count: Int,
    spread: Float,
    label: Int,
    spreadY: Float = spread,
): List<P> = List(count) {
    P((cx + rng.jitter(spread)).coerceIn(0.04f, 0.96f), (cy + rng.jitter(spreadY)).coerceIn(0.04f, 0.96f), label)
}

private val threeBlobs: List<P> = Lcg(7).let { rng ->
    blob(rng, 0.22f, 0.28f, 7, 0.20f, 0) + blob(rng, 0.76f, 0.30f, 7, 0.20f, 1) + blob(rng, 0.50f, 0.80f, 7, 0.20f, 2)
}

private val densityBlobs: List<P> = Lcg(11).let { rng ->
    blob(rng, 0.28f, 0.34f, 6, 0.20f, 0) + blob(rng, 0.74f, 0.66f, 6, 0.20f, 1) +
        listOf(P(0.06f, 0.92f), P(0.94f, 0.08f), P(0.50f, 0.50f))
}

private val twoClasses: List<P> = Lcg(23).let { rng ->
    blob(rng, 0.32f, 0.68f, 8, 0.30f, 0) + blob(rng, 0.72f, 0.32f, 8, 0.30f, 1)
}

private val smallSet: List<P> = listOf(
    P(0.12f, 0.20f), P(0.20f, 0.30f), P(0.30f, 0.16f),
    P(0.62f, 0.72f), P(0.74f, 0.80f), P(0.82f, 0.64f),
    P(0.52f, 0.24f), P(0.88f, 0.22f),
)

private val correlatedCloud: List<P> = Lcg(31).let { rng ->
    List(18) {
        val t = 0.10f + 0.80f * (it / 17f)
        P((t + rng.jitter(0.10f)).coerceIn(0.04f, 0.96f), (0.15f + 0.72f * t + rng.jitter(0.14f)).coerceIn(0.04f, 0.96f))
    }
}

// Laid out so one cut is not enough: class 0 owns the whole bottom strip *and* the top right, so the
// tree has to split on y and then on x. A single-cut dataset would make the depth-2 story a lie.
// Hand-placed rather than sampled: the LCG's jitter is too correlated to reliably produce a layout
// where neither a single x cut nor a single y cut is enough.
private val boxyClasses: List<P> = listOf(
    // Class 0 fills the bottom strip at every x…
    P(0.10f, 0.14f, 0), P(0.24f, 0.20f, 0), P(0.38f, 0.12f, 0), P(0.52f, 0.22f, 0),
    P(0.66f, 0.14f, 0), P(0.80f, 0.20f, 0), P(0.90f, 0.12f, 0),
    // …and the top right, so class 1 in the top left is boxed in on two sides.
    P(0.72f, 0.74f, 0), P(0.84f, 0.86f, 0), P(0.90f, 0.68f, 0), P(0.78f, 0.90f, 0),
    P(0.12f, 0.72f, 1), P(0.20f, 0.86f, 1), P(0.28f, 0.76f, 1), P(0.16f, 0.62f, 1),
)

private fun distance(a: P, b: P) = hypot(a.x - b.x, a.y - b.y)

// ── k-means ──────────────────────────────────────────────────────────────────

private fun kMeansFrames(): List<CloudFrame> {
    val points = threeBlobs
    val k = 3
    // A deliberately lopsided init: all three seeds start on one side, so the first assignment is
    // visibly wrong and the updates have somewhere to travel.
    var centroids = listOf(P(0.20f, 0.60f), P(0.30f, 0.66f), P(0.40f, 0.72f))
    var assignment = List(points.size) { -1 }
    val frames = mutableListOf<CloudFrame>()

    fun dots(marks: List<Int>) = points.mapIndexed { i, p -> Dot(p, marks[i]) }
    fun centroidDots() = centroids.mapIndexed { i, c -> Dot(c, i, Emphasis.ACTIVE) }

    frames += CloudFrame(
        status = "k = $k centroids are seeded badly on purpose — all three start in the same region. k-means never " +
            "sees the true clusters, only distances.",
        dots = dots(assignment),
        centroids = centroidDots(),
    )

    for (iteration in 0 until 6) {
        val next = points.map { p -> centroids.indices.minByOrNull { distance(p, centroids[it]) }!! }
        assignment = next
        frames += CloudFrame(
            status = "Assign: every point takes the nearest centroid. Cluster sizes " +
                "${(0 until k).joinToString(" / ") { c -> assignment.count { it == c }.toString() }}.",
            dots = dots(assignment),
            centroids = centroidDots(),
            segments = points.mapIndexed { i, p ->
                Segment(p, centroids[assignment[i]], groupColor(assignment[i]).copy(alpha = 0.35f))
            },
        )

        val moved = centroids.mapIndexed { c, old ->
            val members = points.filterIndexed { i, _ -> assignment[i] == c }
            if (members.isEmpty()) old else P(members.map { it.x }.average().toFloat(), members.map { it.y }.average().toFloat())
        }
        val shift = moved.indices.sumOf { distance(centroids[it], moved[it]).toDouble() }.toFloat()
        val previous = centroids
        centroids = moved
        frames += CloudFrame(
            status = if (shift < 0.005f) {
                "Update: no centroid moved — the assignment cannot change either, so k-means has converged."
            } else {
                "Update: each centroid jumps to the mean of its members (total movement ${"%.2f".format(shift)})."
            },
            dots = dots(assignment),
            centroids = centroidDots(),
            segments = previous.indices.map { Segment(previous[it], centroids[it], groupColor(it), dashed = true) },
        )
        // Centroids that stop moving cannot change the assignment either, so the run is over.
        if (shift < 0.005f) break
    }

    val inertia = points.mapIndexed { i, p -> distance(p, centroids[assignment[i]]) * distance(p, centroids[assignment[i]]) }.sum()
    frames += CloudFrame(
        status = "Converged. Within-cluster sum of squares is ${"%.3f".format(inertia)} — the quantity every " +
            "iteration is guaranteed to decrease, which is also why a bad seeding can settle in a poor local minimum.",
        dots = dots(assignment),
        centroids = centroids.mapIndexed { i, c -> Dot(c, i, Emphasis.ACTIVE) },
        readout = "inertia = ${"%.3f".format(inertia)}",
    )
    return frames
}

// ── DBSCAN ───────────────────────────────────────────────────────────────────

private fun dbscanFrames(): List<CloudFrame> {
    val points = densityBlobs
    val eps = 0.22f
    val minPts = 3
    val labels = IntArray(points.size) { -1 }
    val noise = mutableSetOf<Int>()
    val visited = mutableSetOf<Int>()
    val frames = mutableListOf<CloudFrame>()
    var cluster = 0

    fun neighbours(i: Int) = points.indices.filter { it != i && distance(points[i], points[it]) <= eps }

    fun dots(active: Int? = null) = points.mapIndexed { i, p ->
        Dot(
            p,
            labels[i],
            when {
                i == active -> Emphasis.ACTIVE
                i in noise -> Emphasis.FADED
                else -> Emphasis.NORMAL
            },
        )
    }

    frames += CloudFrame(
        status = "DBSCAN needs no k. It needs a radius (ε = $eps) and a density threshold (minPts = $minPts): a point " +
            "with $minPts neighbours inside ε is a core point, and core points grow clusters.",
        dots = dots(),
    )

    for (i in points.indices) {
        if (i in visited) continue
        visited += i
        val near = neighbours(i)
        if (near.size < minPts) {
            noise += i
            frames += CloudFrame(
                status = "Point ${i + 1} has only ${near.size} neighbour(s) within ε — not a core point. Label it " +
                    "noise for now; a later cluster may still absorb it as a border point.",
                dots = dots(i),
                rings = listOf(Ring(points[i], eps, UnassignedColor)),
            )
            continue
        }

        val queue = ArrayDeque(near)
        labels[i] = cluster
        noise -= i
        frames += CloudFrame(
            status = "Point ${i + 1} has ${near.size} neighbours within ε — a core point. Start cluster ${cluster + 1} " +
                "and absorb everything reachable from it.",
            dots = dots(i),
            rings = listOf(Ring(points[i], eps, groupColor(cluster))),
        )

        while (queue.isNotEmpty()) {
            val j = queue.removeFirst()
            if (labels[j] == -1) {
                labels[j] = cluster
                noise -= j
            }
            if (j in visited) continue
            visited += j
            val jNear = neighbours(j)
            if (jNear.size >= minPts) {
                queue.addAll(jNear.filter { labels[it] == -1 })
                frames += CloudFrame(
                    status = "Point ${j + 1} is also core (${jNear.size} neighbours) — the cluster keeps expanding " +
                        "through it. This chaining is why DBSCAN handles non-round shapes.",
                    dots = dots(j),
                    rings = listOf(Ring(points[j], eps, groupColor(cluster))),
                )
            } else {
                frames += CloudFrame(
                    status = "Point ${j + 1} joins as a border point: close enough to a core point, but too sparse to " +
                        "expand the cluster itself.",
                    dots = dots(j),
                    rings = listOf(Ring(points[j], eps, groupColor(cluster))),
                )
            }
        }
        cluster++
    }

    frames += CloudFrame(
        status = "$cluster clusters and ${noise.size} points left as noise. k-means would have been forced to put " +
            "those outliers somewhere; DBSCAN is allowed to refuse.",
        dots = points.mapIndexed { i, p -> Dot(p, labels[i], if (i in noise) Emphasis.FADED else Emphasis.NORMAL) },
        readout = "clusters = $cluster · noise = ${noise.size}",
    )
    return frames
}

// ── Hierarchical clustering ──────────────────────────────────────────────────

private fun hierarchicalFrames(): List<CloudFrame> {
    val points = smallSet
    val members = points.indices.map { mutableListOf(it) }.toMutableList()
    val labels = IntArray(points.size) { it }
    val frames = mutableListOf<CloudFrame>()
    val links = mutableListOf<Segment>()

    fun dots(active: Set<Int> = emptySet()) = points.mapIndexed { i, p ->
        Dot(p, labels[i], if (i in active) Emphasis.ACTIVE else Emphasis.NORMAL)
    }

    frames += CloudFrame(
        status = "Agglomerative clustering starts with ${points.size} clusters of one point each and merges the two " +
            "closest, over and over. No k is chosen up front — the cut comes later.",
        dots = dots(),
    )

    while (members.count { it.isNotEmpty() } > 1) {
        var best: Triple<Int, Int, Float>? = null
        for (a in members.indices) {
            if (members[a].isEmpty()) continue
            for (b in a + 1 until members.size) {
                if (members[b].isEmpty()) continue
                // Single linkage: distance between clusters is the closest pair across them.
                val d = members[a].minOf { i -> members[b].minOf { j -> distance(points[i], points[j]) } }
                if (best == null || d < best!!.third) best = Triple(a, b, d)
            }
        }
        val (a, b, d) = best ?: break
        val closestPair = members[a].flatMap { i -> members[b].map { j -> i to j } }
            .minByOrNull { (i, j) -> distance(points[i], points[j]) }!!
        val mergedLabel = labels[members[a].first()]
        links += Segment(points[closestPair.first], points[closestPair.second], groupColor(mergedLabel))

        members[a].addAll(members[b])
        members[b].forEach { labels[it] = mergedLabel }
        members[b] = mutableListOf()

        val remaining = members.count { it.isNotEmpty() }
        frames += CloudFrame(
            status = "Closest pair is ${"%.2f".format(d)} apart — merge them. $remaining cluster(s) left.",
            dots = dots(members[a].toSet()),
            segments = links.toList(),
            readout = "clusters = $remaining",
        )
    }

    frames += CloudFrame(
        status = "Everything is one cluster. The value is the order of merges: cutting the sequence after the two " +
            "cheap merges recovers the two obvious groups, and no k was needed to find them.",
        dots = points.map { Dot(it, 0) },
        segments = links.toList(),
    )
    return frames
}

// ── k-NN ─────────────────────────────────────────────────────────────────────

private fun knnFrames(): List<CloudFrame> {
    val points = twoClasses
    val query = P(0.52f, 0.50f)
    val k = 5
    val ranked = points.sortedBy { distance(it, query) }
    val frames = mutableListOf<CloudFrame>()

    fun dots(taken: Int) = points.map { p ->
        val rank = ranked.indexOf(p)
        Dot(p, p.label, if (rank < taken) Emphasis.ACTIVE else Emphasis.FADED)
    } + Dot(query, -1, Emphasis.QUERY)

    frames += CloudFrame(
        status = "k-NN has no training step at all — the dataset is the model. Classifying the yellow query point " +
            "means measuring it against every stored point.",
        dots = points.map { Dot(it, it.label) } + Dot(query, -1, Emphasis.QUERY),
    )

    for (i in 1..k) {
        val neighbour = ranked[i - 1]
        val votes = ranked.take(i).groupingBy { it.label }.eachCount()
        frames += CloudFrame(
            status = "Neighbour $i is ${"%.2f".format(distance(neighbour, query))} away, class ${neighbour.label}. " +
                "Votes so far: ${votes.entries.sortedBy { it.key }.joinToString(", ") { "class ${it.key} = ${it.value}" }}.",
            dots = dots(i),
            rings = listOf(Ring(query, distance(ranked[i - 1], query), QueryColor)),
            segments = ranked.take(i).map { Segment(query, it, groupColor(it.label)) },
        )
    }

    val votes = ranked.take(k).groupingBy { it.label }.eachCount()
    val winner = votes.maxByOrNull { it.value }!!
    frames += CloudFrame(
        status = "Majority of the $k nearest is class ${winner.key} (${winner.value} of $k). Prediction costs a scan " +
            "of the whole dataset, which is the trade for zero training time.",
        dots = dots(k),
        rings = listOf(Ring(query, distance(ranked[k - 1], query), groupColor(winner.key))),
        readout = "predicted class ${winner.key}",
    )
    return frames
}

// ── Naive Bayes ──────────────────────────────────────────────────────────────

private fun naiveBayesFrames(): List<CloudFrame> {
    val points = twoClasses
    // Sits near the boundary on purpose — a query deep inside one class scores ~100% and teaches
    // nothing about how the posterior is formed.
    val query = P(0.48f, 0.55f)
    val frames = mutableListOf<CloudFrame>()

    val byClass = points.groupBy { it.label }
    fun stats(values: List<Float>): Pair<Float, Float> {
        val mean = values.average().toFloat()
        val variance = values.map { (it - mean) * (it - mean) }.average().toFloat().coerceAtLeast(1e-4f)
        return mean to variance
    }

    val summaries = byClass.mapValues { (_, list) -> stats(list.map { it.x }) to stats(list.map { it.y }) }
    val priors = byClass.mapValues { (_, list) -> list.size.toFloat() / points.size }

    frames += CloudFrame(
        status = "Naive Bayes fits one distribution per feature per class — here that is a mean and variance for x " +
            "and for y, nothing more. Training is a single pass of counting.",
        dots = points.map { Dot(it, it.label) },
    )

    summaries.entries.sortedBy { it.key }.forEach { (label, summary) ->
        val (xs, ys) = summary
        frames += CloudFrame(
            status = "Class $label: x ~ mean ${"%.2f".format(xs.first)}, sd ${"%.2f".format(sqrt(xs.second))}; " +
                "y ~ mean ${"%.2f".format(ys.first)}, sd ${"%.2f".format(sqrt(ys.second))}. Prior ${"%.2f".format(priors.getValue(label))}.",
            dots = points.map { Dot(it, it.label, if (it.label == label) Emphasis.NORMAL else Emphasis.FADED) },
            centroids = listOf(Dot(P(xs.first, ys.first), label, Emphasis.ACTIVE)),
            rings = listOf(Ring(P(xs.first, ys.first), sqrt(xs.second), groupColor(label))),
        )
    }

    fun gaussianLog(value: Float, mean: Float, variance: Float): Float =
        (-0.5f * ln(2f * Math.PI.toFloat() * variance) - (value - mean) * (value - mean) / (2f * variance))

    val scores = summaries.mapValues { (label, summary) ->
        val (xs, ys) = summary
        ln(priors.getValue(label)) + gaussianLog(query.x, xs.first, xs.second) + gaussianLog(query.y, ys.first, ys.second)
    }
    val winner = scores.maxByOrNull { it.value }!!
    val normalised = scores.mapValues { exp(it.value - winner.value) }
    val total = normalised.values.sum()

    frames += CloudFrame(
        status = "Score the query under each class as log prior + log p(x) + log p(y) — the \"naive\" step is " +
            "multiplying the two feature terms as if they were independent.",
        dots = points.map { Dot(it, it.label, Emphasis.FADED) } + Dot(query, -1, Emphasis.QUERY),
        centroids = summaries.entries.sortedBy { it.key }.map { (label, s) -> Dot(P(s.first.first, s.second.first), label, Emphasis.ACTIVE) },
        segments = summaries.entries.sortedBy { it.key }.map { (label, s) ->
            Segment(query, P(s.first.first, s.second.first), groupColor(label), dashed = true)
        },
    )

    frames += CloudFrame(
        status = "Class ${winner.key} wins with posterior ${"%.0f".format(100f * normalised.getValue(winner.key) / total)}%. " +
            "The independence assumption is usually false and the classifier usually works anyway — only the ranking " +
            "of the scores has to be right.",
        dots = points.map { Dot(it, it.label, if (it.label == winner.key) Emphasis.NORMAL else Emphasis.FADED) } +
            Dot(query, winner.key, Emphasis.QUERY),
        readout = "P(class ${winner.key}) ≈ ${"%.0f".format(100f * normalised.getValue(winner.key) / total)}%",
    )
    return frames
}

// ── Decision tree ────────────────────────────────────────────────────────────

private class Node(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val items: List<P>, val depth: Int)

private fun gini(items: List<P>): Float {
    if (items.isEmpty()) return 0f
    val counts = items.groupingBy { it.label }.eachCount()
    return 1f - counts.values.sumOf { val p = it.toDouble() / items.size; p * p }.toFloat()
}

private fun decisionTreeFrames(): List<CloudFrame> {
    val points = boxyClasses
    val frames = mutableListOf<CloudFrame>()
    val splits = mutableListOf<Segment>()
    val leaves = mutableListOf<Region>()

    frames += CloudFrame(
        status = "A decision tree cuts the feature space with axis-aligned lines. Impurity of the whole set is " +
            "gini ${"%.2f".format(gini(points))} — the split that lowers it most is chosen greedily.",
        dots = points.map { Dot(it, it.label) },
    )

    fun split(node: Node) {
        if (node.depth >= 2 || gini(node.items) == 0f || node.items.size < 3) {
            val majority = node.items.groupingBy { it.label }.eachCount().maxByOrNull { it.value }?.key ?: 0
            leaves += Region(node.x0, node.y0, node.x1, node.y1, groupColor(majority).copy(alpha = 0.18f))
            frames += CloudFrame(
                status = "This region is ${if (gini(node.items) == 0f) "pure" else "as pure as the depth limit allows"} " +
                    "(${node.items.size} points, gini ${"%.2f".format(gini(node.items))}) — make it a leaf predicting class $majority.",
                dots = points.map { Dot(it, it.label, if (it in node.items) Emphasis.ACTIVE else Emphasis.FADED) },
                segments = splits.toList(),
                regions = leaves.toList(),
            )
            return
        }

        var best: Triple<Boolean, Float, Float>? = null // (splitOnX, threshold, weighted gini)
        for (onX in listOf(true, false)) {
            val values = node.items.map { if (onX) it.x else it.y }.sorted()
            for (i in 0 until values.size - 1) {
                val threshold = (values[i] + values[i + 1]) / 2f
                val left = node.items.filter { (if (onX) it.x else it.y) <= threshold }
                val right = node.items.filter { (if (onX) it.x else it.y) > threshold }
                if (left.isEmpty() || right.isEmpty()) continue
                val score = (left.size * gini(left) + right.size * gini(right)) / node.items.size
                if (best == null || score < best!!.third) best = Triple(onX, threshold, score)
            }
        }
        val (onX, threshold, score) = best ?: return

        splits += if (onX) {
            Segment(P(threshold, node.y0), P(threshold, node.y1), AxisColor)
        } else {
            Segment(P(node.x0, threshold), P(node.x1, threshold), AxisColor)
        }
        frames += CloudFrame(
            status = "Best split here is ${if (onX) "x" else "y"} ≤ ${"%.2f".format(threshold)}: it drops impurity " +
                "from ${"%.2f".format(gini(node.items))} to ${"%.2f".format(score)}. Every candidate threshold was " +
                "tried on both features.",
            dots = points.map { Dot(it, it.label, if (it in node.items) Emphasis.ACTIVE else Emphasis.FADED) },
            segments = splits.toList(),
            regions = leaves.toList(),
        )

        val left = node.items.filter { (if (onX) it.x else it.y) <= threshold }
        val right = node.items.filter { (if (onX) it.x else it.y) > threshold }
        if (onX) {
            split(Node(node.x0, node.y0, threshold, node.y1, left, node.depth + 1))
            split(Node(threshold, node.y0, node.x1, node.y1, right, node.depth + 1))
        } else {
            split(Node(node.x0, node.y0, node.x1, threshold, left, node.depth + 1))
            split(Node(node.x0, threshold, node.x1, node.y1, right, node.depth + 1))
        }
    }

    split(Node(0f, 0f, 1f, 1f, points, 0))

    frames += CloudFrame(
        status = "${leaves.size} leaves, depth 2. Every prediction is now a couple of comparisons — and the same " +
            "greedy process run to full depth is exactly how a tree overfits.",
        dots = points.map { Dot(it, it.label) },
        segments = splits.toList(),
        regions = leaves.toList(),
        readout = "leaves = ${leaves.size}",
    )
    return frames
}

// ── PCA ──────────────────────────────────────────────────────────────────────

private fun pcaFrames(): List<CloudFrame> {
    val points = correlatedCloud
    val meanX = points.map { it.x }.average().toFloat()
    val meanY = points.map { it.y }.average().toFloat()
    val frames = mutableListOf<CloudFrame>()

    frames += CloudFrame(
        status = "These two features move together — most of the spread lies along one diagonal direction, not along " +
            "x or y.",
        dots = points.map { Dot(it, 0) },
    )

    frames += CloudFrame(
        status = "Centre the data first: PCA measures variance about the mean (${"%.2f".format(meanX)}, ${"%.2f".format(meanY)}), " +
            "so the mean has to be the origin.",
        dots = points.map { Dot(it, 0, Emphasis.FADED) },
        centroids = listOf(Dot(P(meanX, meanY), 1, Emphasis.ACTIVE)),
    )

    val cxx = points.map { (it.x - meanX) * (it.x - meanX) }.average().toFloat()
    val cyy = points.map { (it.y - meanY) * (it.y - meanY) }.average().toFloat()
    val cxy = points.map { (it.x - meanX) * (it.y - meanY) }.average().toFloat()
    val trace = cxx + cyy
    val diff = sqrt((cxx - cyy) * (cxx - cyy) + 4f * cxy * cxy)
    val lambda1 = (trace + diff) / 2f
    val lambda2 = (trace - diff) / 2f
    // Eigenvector of the 2x2 covariance matrix for the larger eigenvalue.
    val rawX = if (abs(cxy) > 1e-6f) cxy else 1f
    val rawY = if (abs(cxy) > 1e-6f) lambda1 - cxx else 0f
    val norm = hypot(rawX, rawY)
    val ux = rawX / norm
    val uy = rawY / norm

    val axisFrom = P(meanX - ux * 0.55f, meanY - uy * 0.55f)
    val axisTo = P(meanX + ux * 0.55f, meanY + uy * 0.55f)

    frames += CloudFrame(
        status = "The covariance matrix is [${"%.3f".format(cxx)}, ${"%.3f".format(cxy)}; ${"%.3f".format(cxy)}, " +
            "${"%.3f".format(cyy)}]. Its top eigenvector points along the direction of greatest variance — that is PC1.",
        dots = points.map { Dot(it, 0, Emphasis.FADED) },
        centroids = listOf(Dot(P(meanX, meanY), 1, Emphasis.ACTIVE)),
        segments = listOf(Segment(axisFrom, axisTo, AxisColor)),
    )

    val projections = points.map { p ->
        val t = (p.x - meanX) * ux + (p.y - meanY) * uy
        P(meanX + ux * t, meanY + uy * t)
    }

    frames += CloudFrame(
        status = "Project every point onto PC1. Each dashed line is the part of the point that PC1 throws away — the " +
            "residual, and it is small.",
        dots = points.map { Dot(it, 0) },
        centroids = projections.map { Dot(it, 2, Emphasis.ACTIVE) },
        segments = listOf(Segment(axisFrom, axisTo, AxisColor)) +
            points.indices.map { Segment(points[it], projections[it], UnassignedColor, dashed = true) },
    )

    val explained = 100f * lambda1 / (lambda1 + lambda2)
    frames += CloudFrame(
        status = "PC1 alone keeps ${"%.0f".format(explained)}% of the variance, so one number per point replaces two " +
            "with little loss. That is the whole compression argument, in 2D instead of 200.",
        dots = projections.map { Dot(it, 2) },
        segments = listOf(Segment(axisFrom, axisTo, AxisColor)),
        readout = "PC1 explains ${"%.0f".format(explained)}%",
    )
    return frames
}

// ── Config ───────────────────────────────────────────────────────────────────

private val clusterLegend = listOf(
    CloudColors[0] to "Cluster A",
    CloudColors[1] to "Cluster B",
    UnassignedColor to "Unassigned",
)

private val classLegend = listOf(
    CloudColors[0] to "Class 0",
    CloudColors[1] to "Class 1",
    QueryColor to "Query",
)

// ── Geometric structures and sampling ────────────────────────────────────────
// Three labs that are about the plane itself rather than about learning from it. Each counts the
// work it does, so the closing claim is a measurement: nodes pruned, comparisons avoided, error
// against sample count.

private val geometryPoints: List<P> = Lcg(2027).let { rng ->
    List(18) { P(0.06f + rng.next() * 0.88f, 0.06f + rng.next() * 0.88f) }
}

private fun kdTreeFrames(): List<CloudFrame> {
    val pts = geometryPoints
    val frames = mutableListOf<CloudFrame>()
    // Each split is recorded as (segment, depth) so the frames can reveal them one level at a time.
    val splits = mutableListOf<Segment>()

    fun build(subset: List<P>, depth: Int, x0: Float, y0: Float, x1: Float, y1: Float) {
        if (subset.size <= 1 || depth >= 3) return
        val vertical = depth % 2 == 0
        val ordered = if (vertical) subset.sortedBy { it.x } else subset.sortedBy { it.y }
        val median = ordered[ordered.size / 2]
        splits += if (vertical) {
            Segment(P(median.x, y0), P(median.x, y1), AxisColor)
        } else {
            Segment(P(x0, median.y), P(x1, median.y), AxisColor)
        }
        val left = ordered.take(ordered.size / 2)
        val right = ordered.drop(ordered.size / 2 + 1)
        if (vertical) {
            build(left, depth + 1, x0, y0, median.x, y1)
            build(right, depth + 1, median.x, y0, x1, y1)
        } else {
            build(left, depth + 1, x0, y0, x1, median.y)
            build(right, depth + 1, x0, median.y, x1, y1)
        }
    }
    build(pts, 0, 0f, 0f, 1f, 1f)

    frames += CloudFrame(
        status = "A k-d tree indexes points by splitting space, not by sorting values. With ${pts.size} points in " +
            "the plane, the question it answers cheaply is \"what is nearest to here\".",
        dots = pts.map { Dot(it, 0) },
        readout = "${pts.size} points, unindexed",
    )
    frames += CloudFrame(
        status = "The root splits on x at the median point, so half the points fall each side. The next level " +
            "splits its halves on y, the level after that on x again — the axis alternates with depth.",
        dots = pts.map { Dot(it, 0) },
        segments = splits.take(1),
        readout = "depth 0: split on x",
    )
    frames += CloudFrame(
        status = "Two more splits, now on y. Every node owns a rectangle, and a point's position in the tree is " +
            "decided entirely by which side of each split it falls on.",
        dots = pts.map { Dot(it, 0) },
        segments = splits.take(3),
        readout = "depth 1: split on y",
    )
    frames += CloudFrame(
        status = "Building down to depth 3 partitions the plane into ${splits.size + 1} cells. Construction sorts " +
            "at each level, so it costs O(n log n) once and is then reused by every query.",
        dots = pts.map { Dot(it, 0) },
        segments = splits,
        readout = "${splits.size} splits",
    )

    // Nearest-neighbour query. The search is run for real against a proper tree so the "examined"
    // count is what the pruning actually achieves, not an estimate of it.
    val query = P(0.62f, 0.38f)
    val examined = LinkedHashSet<P>()

    /** Recursive NN descent: nearer child first, sibling only if its half-plane is still in range. */
    fun search(subset: List<P>, depth: Int, bestSoFar: Float): Float {
        if (subset.isEmpty()) return bestSoFar
        var best = bestSoFar
        if (subset.size == 1 || depth >= 3) {
            for (p in subset) {
                examined += p
                best = minOf(best, hypot(p.x - query.x, p.y - query.y))
            }
            return best
        }
        val vertical = depth % 2 == 0
        val ordered = if (vertical) subset.sortedBy { it.x } else subset.sortedBy { it.y }
        val median = ordered[ordered.size / 2]
        examined += median
        best = minOf(best, hypot(median.x - query.x, median.y - query.y))
        val left = ordered.take(ordered.size / 2)
        val right = ordered.drop(ordered.size / 2 + 1)
        val axisGap = if (vertical) query.x - median.x else query.y - median.y
        val nearSide = if (axisGap < 0) left else right
        val farSide = if (axisGap < 0) right else left
        best = search(nearSide, depth + 1, best)
        // The far side can only help if the splitting line itself is closer than the best distance.
        if (abs(axisGap) < best) best = search(farSide, depth + 1, best)
        return best
    }

    val best = search(pts, 0, Float.MAX_VALUE)
    val nearest = pts.minByOrNull { hypot(it.x - query.x, it.y - query.y) }!!
    val mustCheck = examined.size

    frames += CloudFrame(
        status = "A nearest-neighbour query descends to the cell containing the query point, then only revisits a " +
            "sibling cell if that cell's rectangle is closer than the best distance found so far.",
        dots = pts.map { Dot(it, 0, Emphasis.FADED) },
        segments = splits,
        rings = listOf(Ring(query, best, QueryColor)),
        centroids = listOf(Dot(query, 3, Emphasis.QUERY)),
        readout = "query at (${"%.2f".format(query.x)}, ${"%.2f".format(query.y)})",
    )
    frames += CloudFrame(
        status = "The nearest point is ${"%.3f".format(best)} away, and the search measured a distance to only " +
            "$mustCheck of the ${pts.size} points to prove it. The other ${pts.size - mustCheck} were discarded " +
            "a whole cell at a time: if the splitting line is farther than the best distance so far, nothing " +
            "beyond it can win, so the subtree is never entered. That pruning is what makes the query O(log n) " +
            "on average.",
        dots = pts.map { p ->
            Dot(p, if (p === nearest) 2 else 0, if (p in examined) Emphasis.NORMAL else Emphasis.FADED)
        },
        segments = splits + Segment(query, nearest, CloudColors[2]),
        rings = listOf(Ring(query, best, QueryColor)),
        centroids = listOf(Dot(query, 3, Emphasis.QUERY)),
        readout = "$mustCheck of ${pts.size} points examined",
    )
    frames += CloudFrame(
        status = "The catch is dimensionality. Each level splits on one axis, so in d dimensions a query has to " +
            "descend d levels before it has constrained every coordinate once — and once d approaches log n, " +
            "almost every cell is close enough to check and the tree degenerates to the brute-force scan it was " +
            "meant to replace.",
        dots = pts.map { Dot(it, 0, if (it === nearest) Emphasis.ACTIVE else Emphasis.FADED) },
        segments = splits,
        readout = "great in 2-D; no better than linear once d is large",
    )
    return frames
}

/**
 * Hand-placed rather than sampled: the interesting case is the one where the closest pair straddles
 * the dividing line, and a uniform cloud almost never produces it. Two loose clusters, plus a close
 * pair sitting either side of the middle.
 */
private val closestPairPoints: List<P> = listOf(
    P(0.08f, 0.20f), P(0.14f, 0.62f), P(0.22f, 0.35f), P(0.28f, 0.85f),
    P(0.33f, 0.12f), P(0.36f, 0.55f), P(0.40f, 0.75f), P(0.44f, 0.30f),
    P(0.49f, 0.55f), P(0.52f, 0.58f),
    P(0.60f, 0.22f), P(0.65f, 0.70f), P(0.70f, 0.42f), P(0.75f, 0.88f),
    P(0.80f, 0.15f), P(0.84f, 0.60f), P(0.88f, 0.33f), P(0.93f, 0.78f),
)

private fun closestPairFrames(): List<CloudFrame> {
    val pts = closestPairPoints.sortedBy { it.x }
    val frames = mutableListOf<CloudFrame>()
    var bruteComparisons = 0
    var bestPair: Pair<P, P>? = null
    var bestDistance = Float.MAX_VALUE
    for (i in pts.indices) for (j in i + 1 until pts.size) {
        bruteComparisons++
        val d = hypot(pts[i].x - pts[j].x, pts[i].y - pts[j].y)
        if (d < bestDistance) { bestDistance = d; bestPair = pts[i] to pts[j] }
    }
    val (pa, pb) = bestPair!!

    frames += CloudFrame(
        status = "Find the two closest points among ${pts.size}. Checking every pair is correct and simple, and " +
            "costs $bruteComparisons distance computations — the count grows as n²/2, so it stops being viable " +
            "long before the input gets interesting.",
        dots = pts.map { Dot(it, 0) },
        readout = "brute force: $bruteComparisons pairs",
    )

    val mid = pts.size / 2
    val splitX = (pts[mid - 1].x + pts[mid].x) / 2f
    val leftPts = pts.take(mid)
    val rightPts = pts.drop(mid)

    fun closestIn(list: List<P>): Pair<Float, Pair<P, P>?> {
        var d = Float.MAX_VALUE
        var pair: Pair<P, P>? = null
        for (i in list.indices) for (j in i + 1 until list.size) {
            val dist = hypot(list[i].x - list[j].x, list[i].y - list[j].y)
            if (dist < d) { d = dist; pair = list[i] to list[j] }
        }
        return d to pair
    }
    val (dl, pairL) = closestIn(leftPts)
    val (dr, pairR) = closestIn(rightPts)
    val delta = minOf(dl, dr)

    frames += CloudFrame(
        status = "Sort by x once, then split down the middle. Each half is solved the same way, recursively — the " +
            "left half's closest pair is ${"%.3f".format(dl)} apart, the right half's ${"%.3f".format(dr)}.",
        dots = pts.map { Dot(it, if (it.x < splitX) 0 else 1) },
        segments = listOf(Segment(P(splitX, 0f), P(splitX, 1f), AxisColor, dashed = true)),
        readout = "left ${"%.3f".format(dl)} · right ${"%.3f".format(dr)}",
    )
    frames += CloudFrame(
        status = "So no pair with both points on the same side beats δ = ${"%.3f".format(delta)}. The only pairs " +
            "left unchecked are those straddling the line, and only points within δ of it can possibly qualify.",
        dots = pts.map { p ->
            Dot(p, if (p.x < splitX) 0 else 1, if (abs(p.x - splitX) <= delta) Emphasis.ACTIVE else Emphasis.FADED)
        },
        segments = listOf(Segment(P(splitX, 0f), P(splitX, 1f), AxisColor, dashed = true)),
        regions = listOf(Region(splitX - delta, 0f, splitX + delta, 1f, AxisColor.copy(alpha = 0.10f))),
        readout = "δ = ${"%.3f".format(delta)}",
    )

    val strip = pts.filter { abs(it.x - splitX) <= delta }.sortedBy { it.y }
    var stripComparisons = 0
    var stripBest = delta
    var stripPair: Pair<P, P>? = null
    for (i in strip.indices) {
        var j = i + 1
        while (j < strip.size && strip[j].y - strip[i].y < delta) {
            stripComparisons++
            val d = hypot(strip[i].x - strip[j].x, strip[i].y - strip[j].y)
            if (d < stripBest) { stripBest = d; stripPair = strip[i] to strip[j] }
            j++
        }
    }

    frames += CloudFrame(
        status = "${strip.size} points fall in the strip. Sorted by y, each one only has to be compared against " +
            "those within δ above it — a geometric argument caps that at a constant number of neighbours, which " +
            "is why the strip costs $stripComparisons comparisons here rather than ${strip.size * (strip.size - 1) / 2}.",
        dots = pts.map { p ->
            Dot(p, if (p.x < splitX) 0 else 1, if (abs(p.x - splitX) <= delta) Emphasis.ACTIVE else Emphasis.FADED)
        },
        segments = listOf(Segment(P(splitX, 0f), P(splitX, 1f), AxisColor, dashed = true)),
        regions = listOf(Region(splitX - delta, 0f, splitX + delta, 1f, AxisColor.copy(alpha = 0.10f))),
        readout = "$stripComparisons comparisons in the strip",
    )

    val found = stripPair ?: pairL ?: pairR
    frames += CloudFrame(
        status = "The closest pair is ${"%.3f".format(bestDistance)} apart" +
            (if (stripPair != null) ", and it straddles the split — which is exactly the case the strip exists to catch." else ", found inside one of the halves.") +
            " Total recurrence T(n) = 2T(n/2) + O(n), so O(n log n) against brute force's $bruteComparisons pairs.",
        dots = pts.map { p ->
            Dot(p, if (p === found?.first || p === found?.second) 2 else 0, if (p === found?.first || p === found?.second) Emphasis.ACTIVE else Emphasis.FADED)
        },
        segments = listOf(Segment(pa, pb, CloudColors[2])),
        readout = "closest distance ${"%.3f".format(bestDistance)}",
    )
    return frames
}

private fun monteCarloFrames(): List<CloudFrame> {
    // Estimate pi by sampling the unit square and counting what lands inside the quarter circle.
    val rng = Lcg(31337)
    val samples = List(1200) { P(rng.next(), rng.next()) }
    val checkpoints = listOf(50, 200, 600, 1200)
    val frames = mutableListOf<CloudFrame>()

    fun inside(p: P) = hypot(p.x, p.y) <= 1f
    fun estimateAt(n: Int) = 4.0 * samples.take(n).count { inside(it) } / n

    frames += CloudFrame(
        status = "A quarter circle of radius 1 sits inside a 1×1 square. Its area is π/4, the square's is 1, so " +
            "the fraction of uniformly random points landing inside the arc estimates π/4 — no geometry required, " +
            "only counting.",
        dots = emptyList(),
        rings = listOf(Ring(P(0f, 0f), 1f, AxisColor)),
        readout = "area ratio = π/4 ≈ ${"%.4f".format(Math.PI / 4)}",
    )

    for (n in checkpoints) {
        val est = estimateAt(n)
        val hits = samples.take(n).count { inside(it) }
        frames += CloudFrame(
            status = "$n samples: $hits landed inside, giving 4 × $hits/$n = ${"%.4f".format(est)}. The error is " +
                "${"%.4f".format(abs(est - Math.PI))}.",
            dots = samples.take(n).map { Dot(it, if (inside(it)) 0 else 1, Emphasis.NORMAL) },
            rings = listOf(Ring(P(0f, 0f), 1f, AxisColor)),
            readout = "π ≈ ${"%.4f".format(est)}   ·   error ${"%.4f".format(abs(est - Math.PI))}",
        )
    }

    val singleErrors = checkpoints.map { abs(estimateAt(it) - Math.PI) }
    // One run says nothing about the rate: its error is itself random. Average the absolute error
    // over independent repetitions at each sample count and the 1/sqrt(n) trend appears.
    val reps = 300
    val meanErrors = checkpoints.map { n ->
        val rep = Lcg(9001 + n)
        var total = 0.0
        repeat(reps) {
            var hits = 0
            repeat(n) { if (hypot(rep.next(), rep.next()) <= 1f) hits++ }
            total += abs(4.0 * hits / n - Math.PI)
        }
        total / reps
    }

    frames += CloudFrame(
        status = "That single run's errors were " +
            checkpoints.indices.joinToString(", ") { "${checkpoints[it]}→${"%.4f".format(singleErrors[it])}" } +
            " — not decreasing. One run proves nothing, because its error is itself a random variable; " +
            "${checkpoints[1]} samples beating ${checkpoints.last()} here is luck, not a result.",
        dots = samples.map { Dot(it, if (inside(it)) 0 else 1, Emphasis.FADED) },
        rings = listOf(Ring(P(0f, 0f), 1f, AxisColor)),
        readout = "a single run's error is noise, not a rate",
    )
    frames += CloudFrame(
        status = "Averaged over $reps independent runs at each size, the mean absolute error is " +
            checkpoints.indices.joinToString(", ") { "${checkpoints[it]}→${"%.4f".format(meanErrors[it])}" } +
            ". Going from ${checkpoints.first()} to ${checkpoints.last()} samples is " +
            "${checkpoints.last() / checkpoints.first()}× the work for " +
            "${"%.1f".format(meanErrors.first() / meanErrors.last())}× the accuracy — close to the √" +
            "${checkpoints.last() / checkpoints.first()} = " +
            "${"%.1f".format(sqrt((checkpoints.last() / checkpoints.first()).toDouble()))} that 1/√n predicts. " +
            "That is the method's defining weakness, and what it buys in exchange is indifference to dimension.",
        dots = samples.map { Dot(it, if (inside(it)) 0 else 1, Emphasis.FADED) },
        rings = listOf(Ring(P(0f, 0f), 1f, AxisColor)),
        readout = "mean error ${"%.4f".format(meanErrors.first())} → ${"%.4f".format(meanErrors.last())} " +
            "over ${checkpoints.last() / checkpoints.first()}× the samples",
    )
    return frames
}

// ── Ensembles: random forest and gradient boosting ───────────────────────────

private fun randomForestFrames(): List<CloudFrame> {
    val points = boxyClasses
    val query = P(0.46f, 0.52f)
    val rng = Lcg(41)
    val frames = mutableListOf<CloudFrame>()

    frames += CloudFrame(
        status = "One deep tree on this data would carve it into pure boxes and memorize the noise. A forest " +
            "instead grows many trees, each on a different random view, and lets them vote.",
        dots = points.map { Dot(it, it.label) } + Dot(query, -1, Emphasis.QUERY),
    )

    // Three trees, each a bootstrap sample plus one axis-aligned split. Real trees are deeper; the
    // point here is that different samples produce different boundaries.
    val trees = listOf(
        Triple("y", 0.45f, 0),   // split on y: below is class 0
        Triple("x", 0.55f, 1),   // split on x: left is class 1
        Triple("y", 0.58f, 0),
    )

    val votes = mutableListOf<Int>()
    trees.forEachIndexed { index, (axis, threshold, lowSide) ->
        // Bootstrap: n draws with replacement, so roughly a third of the rows never appear.
        val drawn = List(points.size) { points[(rng.next() * points.size).toInt().coerceAtMost(points.lastIndex)] }
        val inBag = drawn.toSet()

        frames += CloudFrame(
            status = "Tree ${index + 1} draws its own bootstrap sample: ${inBag.size} distinct rows of " +
                "${points.size}. The faded points are out-of-bag — this tree never sees them, so they can score " +
                "it later for free.",
            dots = points.map { Dot(it, it.label, if (it in inBag) Emphasis.NORMAL else Emphasis.FADED) } +
                Dot(query, -1, Emphasis.QUERY),
            readout = "${points.size - inBag.size} rows out-of-bag",
        )

        val split = if (axis == "y") {
            Segment(P(0f, threshold), P(1f, threshold), AxisColor)
        } else {
            Segment(P(threshold, 0f), P(threshold, 1f), AxisColor)
        }
        val vote = if (axis == "y") {
            if (query.y < threshold) lowSide else 1 - lowSide
        } else {
            if (query.x < threshold) lowSide else 1 - lowSide
        }
        votes += vote

        frames += CloudFrame(
            status = "Restricted to a random subset of features, tree ${index + 1} splits on $axis = $threshold " +
                "and sends the query point to class $vote. A different sample would have chosen a different cut " +
                "— that disagreement is the whole asset.",
            dots = points.map { Dot(it, it.label, if (it in inBag) Emphasis.NORMAL else Emphasis.FADED) } +
                Dot(query, -1, Emphasis.QUERY),
            segments = listOf(split),
            regions = listOf(
                if (axis == "y") Region(0f, 0f, 1f, threshold, groupColor(lowSide).copy(alpha = 0.12f))
                else Region(0f, 0f, threshold, 1f, groupColor(lowSide).copy(alpha = 0.12f)),
            ),
            readout = "tree ${index + 1} votes class $vote",
        )
    }

    val tally = votes.groupingBy { it }.eachCount()
    val winner = tally.maxBy { it.value }
    frames += CloudFrame(
        status = "Votes: ${tally.entries.sortedBy { it.key }.joinToString(", ") { "class ${it.key} × ${it.value}" }}. " +
            "The majority wins. Each tree alone is high-variance; because their errors are decorrelated, the " +
            "average is far steadier than any single one.",
        dots = points.map { Dot(it, it.label) } + Dot(query, -1, Emphasis.QUERY),
        rings = listOf(Ring(query, 0.09f, groupColor(winner.key))),
        readout = "forest predicts class ${winner.key} (${winner.value}/${votes.size})",
    )
    return frames
}

private fun gradientBoostingFrames(): List<CloudFrame> {
    val points = correlatedCloud
    val frames = mutableListOf<CloudFrame>()
    val lr = 0.5f

    // Prediction is piecewise constant over three x-bands — a stump per round, as in real boosting.
    val bands = listOf(0f to 0.35f, 0.35f to 0.7f, 0.7f to 1f)
    fun bandOf(p: P) = bands.indexOfFirst { p.x >= it.first && p.x < it.second }.coerceAtLeast(0)

    var prediction = FloatArray(points.size) { points.map { p -> p.y }.average().toFloat() }
    val bandValue = FloatArray(bands.size) { points.map { p -> p.y }.average().toFloat() }

    fun predictionSegments() = bands.mapIndexed { i, (x0, x1) ->
        Segment(P(x0, bandValue[i]), P(x1, bandValue[i]), groupColor(2))
    }

    fun residualSegments() = points.mapIndexed { i, p ->
        Segment(p, P(p.x, prediction[i]), QueryColor, dashed = true)
    }

    fun mse() = points.indices.sumOf { i -> ((points[i].y - prediction[i]) * (points[i].y - prediction[i])).toDouble() } / points.size

    frames += CloudFrame(
        status = "Round 0 is a single constant: the mean of y. Every dashed line is a residual — what the " +
            "ensemble still gets wrong on that point.",
        dots = points.map { Dot(it, 0) },
        segments = predictionSegments() + residualSegments(),
        readout = "MSE ${"%.4f".format(mse())}",
    )

    repeat(4) { round ->
        // Fit a stump to the residuals: one constant correction per band.
        val corrections = FloatArray(bands.size)
        for (b in bands.indices) {
            val members = points.indices.filter { bandOf(points[it]) == b }
            if (members.isEmpty()) continue
            corrections[b] = members.map { points[it].y - prediction[it] }.average().toFloat()
        }

        frames += CloudFrame(
            status = "Round ${round + 1} fits a shallow tree to those residuals, not to y. Its three leaves are " +
                "the mean residual in each band: ${corrections.joinToString(", ") { "%.3f".format(it) }}.",
            dots = points.map { Dot(it, 0, Emphasis.FADED) },
            segments = predictionSegments() + residualSegments(),
            readout = "fitting the errors, not the target",
        )

        for (b in bands.indices) bandValue[b] += lr * corrections[b]
        prediction = FloatArray(points.size) { bandValue[bandOf(points[it])] }

        frames += CloudFrame(
            status = "Add it at learning rate $lr — shrunken, so no single tree dominates. The step is small on " +
                "purpose: more rounds at a smaller rate generalizes better than fewer large ones.",
            dots = points.map { Dot(it, 0) },
            segments = predictionSegments() + residualSegments(),
            readout = "MSE ${"%.4f".format(mse())} after ${round + 1} round${if (round == 0) "" else "s"}",
        )
    }

    frames += CloudFrame(
        status = "Four rounds in, the staircase tracks the trend. Keep going and it would eventually fit the " +
            "noise too — unlike a forest, boosting can overfit with too many rounds, which is why early " +
            "stopping on validation loss is standard.",
        dots = points.map { Dot(it, 0) },
        segments = predictionSegments(),
        readout = "final MSE ${"%.4f".format(mse())}",
    )
    return frames
}

// ── Bias-variance and regularization ─────────────────────────────────────────

private fun biasVarianceFrames(): List<CloudFrame> {
    val points = correlatedCloud
    val frames = mutableListOf<CloudFrame>()
    val rng = Lcg(53)

    frames += CloudFrame(
        status = "The same data, fitted three ways. Nothing changes but model capacity.",
        dots = points.map { Dot(it, 0) },
    )

    // High bias: a flat line that ignores the trend.
    val meanY = points.map { it.y }.average().toFloat()
    frames += CloudFrame(
        status = "High bias: a constant. It is wrong in the same direction no matter which sample you train it " +
            "on, and no amount of extra data will fix that — the model simply cannot express a slope.",
        dots = points.map { Dot(it, 0) },
        segments = listOf(Segment(P(0.04f, meanY), P(0.96f, meanY), groupColor(1))) +
            points.map { Segment(it, P(it.x, meanY), QueryColor, dashed = true) },
        readout = "underfit · train error high, test error high",
    )

    // High variance: a curve threaded through every point.
    val sorted = points.sortedBy { it.x }
    frames += CloudFrame(
        status = "High variance: enough capacity to pass through every point. Training error is nearly zero, but " +
            "the wiggles encode this sample's noise, not the underlying trend.",
        dots = points.map { Dot(it, 0) },
        segments = sorted.zipWithNext().map { (a, b) -> Segment(a, b, groupColor(3)) },
        readout = "overfit · train error ~0, test error high",
    )

    // Show variance directly: refit the flexible model on bootstrap resamples.
    repeat(3) { round ->
        val resample = List(points.size) { points[(rng.next() * points.size).toInt().coerceAtMost(points.lastIndex)] }
            .distinct().sortedBy { it.x }
        frames += CloudFrame(
            status = "Refit ${round + 1} of the flexible model on a resample of the same source. The curve moves " +
                "substantially — that instability across samples *is* variance, measured rather than described.",
            dots = points.map { Dot(it, 0, if (it in resample) Emphasis.NORMAL else Emphasis.FADED) },
            segments = resample.zipWithNext().map { (a, b) -> Segment(a, b, groupColor(3)) },
            readout = "different sample → different fit",
        )
    }

    // Balanced: least-squares line.
    val meanX = points.map { it.x }.average().toFloat()
    val slope = points.sumOf { ((it.x - meanX) * (it.y - meanY)).toDouble() }
        .div(points.sumOf { ((it.x - meanX) * (it.x - meanX)).toDouble() }).toFloat()
    val intercept = meanY - slope * meanX
    frames += CloudFrame(
        status = "Between them sits the fit that minimizes the sum: some bias, some variance, lowest expected " +
            "error. Diagnose before treating — a uniformly high error means bias, a large train-test gap means " +
            "variance, and the two fixes are opposites.",
        dots = points.map { Dot(it, 0) },
        segments = listOf(Segment(P(0.04f, intercept + slope * 0.04f), P(0.96f, intercept + slope * 0.96f), groupColor(2))),
        readout = "bias² + variance + noise, minimized as a sum",
    )
    return frames
}

private fun regularizationFrames(): List<CloudFrame> {
    val points = correlatedCloud
    val frames = mutableListOf<CloudFrame>()
    val sorted = points.sortedBy { it.x }
    val meanX = points.map { it.x }.average().toFloat()
    val meanY = points.map { it.y }.average().toFloat()
    val slope = points.sumOf { ((it.x - meanX) * (it.y - meanY)).toDouble() }
        .div(points.sumOf { ((it.x - meanX) * (it.x - meanX)).toDouble() }).toFloat()
    val intercept = meanY - slope * meanX

    frames += CloudFrame(
        status = "λ = 0: no penalty. The optimizer is free to use large coefficients, and with enough of them it " +
            "threads every point — including the noise.",
        dots = points.map { Dot(it, 0) },
        segments = sorted.zipWithNext().map { (a, b) -> Segment(a, b, groupColor(3)) },
        readout = "‖w‖ large · train error ~0",
    )

    // Blend the wiggly interpolation toward the straight fit as lambda grows.
    listOf(0.35f to "λ small", 0.7f to "λ moderate", 1f to "λ large").forEach { (pull, label) ->
        val blended = sorted.map { p ->
            val lineY = intercept + slope * p.x
            P(p.x, p.y + (lineY - p.y) * pull)
        }
        frames += CloudFrame(
            status = when (label) {
                "λ small" -> "Raise λ and every weight is charged for its size. The curve keeps the real trend but " +
                    "gives up the sharpest wiggles first — those cost the most weight for the least error reduction."
                "λ moderate" -> "More penalty, more shrinkage. This is the region where validation error usually " +
                    "bottoms out: enough flexibility for the signal, not enough for the noise."
                else -> "λ large: the penalty dominates the loss and the model collapses toward its intercept. " +
                    "Bias is now the problem — over-regularizing is just underfitting by another route."
            },
            dots = points.map { Dot(it, 0, if (pull == 1f) Emphasis.FADED else Emphasis.NORMAL) },
            segments = blended.zipWithNext().map { (a, b) -> Segment(a, b, groupColor(2)) },
            readout = "$label · ‖w‖ shrinking",
        )
    }

    frames += CloudFrame(
        status = "L2 shrinks every weight smoothly toward zero; L1 would drive some of them exactly to zero and " +
            "drop those features entirely. Either way λ is chosen on validation data — never on the training " +
            "loss, which always prefers λ = 0.",
        dots = points.map { Dot(it, 0) },
        segments = listOf(Segment(P(0.04f, intercept + slope * 0.04f), P(0.96f, intercept + slope * 0.96f), groupColor(2))),
        readout = "L2 → small weights · L1 → sparse weights",
    )
    return frames
}

private fun modelEvaluationFrames(): List<CloudFrame> {
    // One feature (x) drives the score; the two classes overlap, so no threshold is perfect.
    val points = Lcg(67).let { rng ->
        blob(rng, 0.34f, 0.50f, 9, 0.34f, 0, spreadY = 0.7f) + blob(rng, 0.64f, 0.50f, 9, 0.34f, 1, spreadY = 0.7f)
    }
    val frames = mutableListOf<CloudFrame>()

    fun confusion(threshold: Float): IntArray {
        var tp = 0; var fp = 0; var fn = 0; var tn = 0
        points.forEach { p ->
            val positive = p.x >= threshold
            when {
                positive && p.label == 1 -> tp++
                positive && p.label == 0 -> fp++
                !positive && p.label == 1 -> fn++
                else -> tn++
            }
        }
        return intArrayOf(tp, fp, fn, tn)
    }

    frames += CloudFrame(
        status = "Two overlapping classes scored along x. Any threshold splits them somewhere — and because the " +
            "classes overlap, every choice trades one kind of error for the other.",
        dots = points.map { Dot(it, it.label) },
        readout = "${points.count { it.label == 1 }} positives · ${points.count { it.label == 0 }} negatives",
    )

    listOf(0.30f, 0.50f, 0.72f).forEach { threshold ->
        val (tp, fp, fn, tn) = confusion(threshold).toList()
        val precision = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
        val recall = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
        val f1 = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
        val accuracy = (tp + tn).toDouble() / points.size

        frames += CloudFrame(
            status = when {
                threshold < 0.4f -> "Threshold ${"%.2f".format(threshold)} — flag almost everything. Recall is " +
                    "high because few positives are missed, but the false positives pile up. This is the " +
                    "operating point a cancer screen wants."
                threshold < 0.6f -> "Threshold ${"%.2f".format(threshold)} — the balanced point. F1 is the " +
                    "harmonic mean, so it punishes trading one metric away for the other."
                else -> "Threshold ${"%.2f".format(threshold)} — flag only the confident cases. Precision rises, " +
                    "recall falls: what a spam filter wants, where a false positive loses real mail."
            },
            dots = points.map { p ->
                Dot(p, p.label, if ((p.x >= threshold) != (p.label == 1)) Emphasis.ACTIVE else Emphasis.NORMAL)
            },
            segments = listOf(Segment(P(threshold, 0f), P(threshold, 1f), AxisColor)),
            regions = listOf(Region(threshold, 0f, 1f, 1f, groupColor(1).copy(alpha = 0.10f))),
            readout = "TP $tp · FP $fp · FN $fn · TN $tn → P ${"%.2f".format(precision)} " +
                "R ${"%.2f".format(recall)} F1 ${"%.2f".format(f1)} acc ${"%.2f".format(accuracy)}",
        )
    }

    // AUC: probability a random positive outranks a random negative.
    val positives = points.filter { it.label == 1 }
    val negatives = points.filter { it.label == 0 }
    var wins = 0.0
    positives.forEach { p -> negatives.forEach { n -> wins += if (p.x > n.x) 1.0 else if (p.x == n.x) 0.5 else 0.0 } }
    val auc = wins / (positives.size * negatives.size)

    frames += CloudFrame(
        status = "Sweeping the threshold traces the ROC curve; its area is the probability that a random positive " +
            "scores above a random negative — ${"%.2f".format(auc)} here. That number is threshold-free, which " +
            "makes it good for comparing models and useless for choosing an operating point.",
        dots = points.map { Dot(it, it.label) },
        readout = "ROC-AUC ${"%.2f".format(auc)} · accuracy alone would hide all of this on imbalanced data",
    )
    return frames
}

// ── Diffusion: destroy the data, then learn the way back ─────────────────────

private fun diffusionFrames(): List<CloudFrame> {
    // A ring is the clearest "data manifold": noise obviously destroys it, denoising obviously restores it.
    val ring = List(20) {
        val angle = 2.0 * Math.PI * it / 20
        P((0.5 + 0.30 * kotlin.math.cos(angle)).toFloat(), (0.5 + 0.30 * kotlin.math.sin(angle)).toFloat())
    }
    val rng = Lcg(89)
    val noise = ring.map { P(rng.jitter(1.6f), rng.jitter(1.6f)) }
    val frames = mutableListOf<CloudFrame>()

    fun noised(alphaBar: Double) = ring.mapIndexed { i, p ->
        val keep = sqrt(alphaBar).toFloat()
        val add = sqrt(1 - alphaBar).toFloat()
        P(
            (keep * p.x + add * (0.5f + noise[i].x)).coerceIn(0.03f, 0.97f),
            (keep * p.y + add * (0.5f + noise[i].y)).coerceIn(0.03f, 0.97f),
        )
    }

    frames += CloudFrame(
        status = "x₀ — the real data. Everything the model needs to learn is the shape of this manifold.",
        dots = ring.map { Dot(it, 2) },
        readout = "t = 0 · ᾱ = 1.00",
    )

    val schedule = listOf(0.85, 0.6, 0.3, 0.08, 0.0)
    schedule.forEach { alphaBar ->
        frames += CloudFrame(
            status = "Forward step: x_t = √ᾱ·x₀ + √(1−ᾱ)·ε with ᾱ = ${"%.2f".format(alphaBar)}. This process is " +
                "fixed — no learning happens here, and the closed form means any timestep is reachable in one " +
                "shot, which is what makes training cheap.",
            dots = noised(alphaBar).map { Dot(it, 0, Emphasis.FADED) },
            readout = "ᾱ = ${"%.2f".format(alphaBar)} · signal fading",
        )
    }

    frames += CloudFrame(
        status = "At ᾱ = 0 the sample is pure Gaussian noise. Sampling starts here, from nothing but N(0, I).",
        dots = noised(0.0).map { Dot(it, 0, Emphasis.ACTIVE) },
        readout = "x_T ~ N(0, I)",
    )

    listOf(0.08, 0.3, 0.6, 0.85, 1.0).forEach { alphaBar ->
        frames += CloudFrame(
            status = when {
                alphaBar < 0.2 -> "Reverse step: the network is asked for ε̂ — its estimate of the noise in this " +
                    "sample — and a fraction of it is subtracted. The loss it was trained on was exactly " +
                    "‖ε − ε̂‖², nothing more elaborate."
                alphaBar < 0.7 -> "Structure re-emerges as the estimated noise is removed step by step. Each step " +
                    "is small, which is why sampling is iterative and expensive compared to a GAN's single pass."
                else -> "The samples land back on the ring. A trained model reaches a *new* point on the manifold " +
                    "rather than the original — that is generation, not reconstruction."
            },
            dots = noised(alphaBar).map { Dot(it, if (alphaBar >= 1.0) 2 else 1) },
            readout = "ᾱ = ${"%.2f".format(alphaBar)} · denoising",
        )
    }

    frames += CloudFrame(
        status = "Training is stable because there is no adversary to balance — just a regression onto noise. " +
            "The cost is at sampling time, which is why DDIM and distillation exist to cut the step count.",
        dots = ring.map { Dot(it, 2) },
        readout = "fixed forward process · learned reverse process",
    )
    return frames
}

// ── Ensembles ────────────────────────────────────────────────────────────────
// Bagging, boosting and stacking all combine weak models, and the interesting differences are in
// *how*: independently and in parallel, sequentially on the previous errors, or through a second
// model that learns the combination. Each builder below runs its real mechanic on the same 2D data
// so the three are directly comparable.

// One axis-aligned decision stump — the weak learner every one of these is built from.
private class Stump(val axis: Int, val threshold: Float, val lowLabel: Int) {
    fun predict(p: P): Int = if ((if (axis == 0) p.x else p.y) < threshold) lowLabel else 1 - lowLabel
    fun segment(): Segment = if (axis == 1) {
        Segment(P(0f, threshold), P(1f, threshold), AxisColor)
    } else {
        Segment(P(threshold, 0f), P(threshold, 1f), AxisColor)
    }
}

// Exhaustive search for the stump with the lowest weighted error. Small data, so brute force is both
// exact and instant — and it means the frames show the genuinely best split, not a chosen one.
private fun bestStump(points: List<P>, weights: FloatArray): Pair<Stump, Float> {
    var best = Stump(0, 0.5f, 0)
    var bestError = Float.MAX_VALUE
    for (axis in 0..1) {
        var t = 0.05f
        while (t <= 0.95f) {
            for (lowLabel in 0..1) {
                val stump = Stump(axis, t, lowLabel)
                var error = 0f
                points.forEachIndexed { i, p -> if (stump.predict(p) != p.label) error += weights[i] }
                if (error < bestError) { bestError = error; best = stump }
            }
            t += 0.025f
        }
    }
    return best to bestError
}

private fun adaBoostFrames(): List<CloudFrame> {
    val points = twoClasses
    val n = points.size
    val weights = FloatArray(n) { 1f / n }
    val frames = mutableListOf<CloudFrame>()
    val stumps = mutableListOf<Pair<Stump, Float>>() // stump and its alpha

    frames += CloudFrame(
        status = "AdaBoost trains weak learners in sequence. Every point starts with equal weight ${"%.3f".format(1f / n)}, and each round re-weights toward whatever the previous round got wrong.",
        dots = points.map { Dot(it, it.label) },
    )

    repeat(4) { round ->
        val (stump, error) = bestStump(points, weights)
        // Guard the degenerate cases: a perfect or coin-flip stump makes alpha blow up or vanish.
        val safeError = error.coerceIn(1e-4f, 0.4999f)
        val alpha = 0.5f * kotlin.math.ln((1f - safeError) / safeError)
        stumps += stump to alpha

        val wrong = points.indices.filter { stump.predict(points[it]) != points[it].label }.toSet()
        frames += CloudFrame(
            status = "Round ${round + 1}: the best stump available under the current weights still gets ${wrong.size} points wrong, for a weighted error of ${"%.3f".format(error)}. Its vote gets weight α = ${"%.3f".format(alpha)} — lower error earns a louder vote.",
            dots = points.mapIndexed { i, p -> Dot(p, p.label, if (i in wrong) Emphasis.ACTIVE else Emphasis.NORMAL) },
            segments = listOf(stump.segment()),
            readout = "α₍${round + 1}₎ = ${"%.3f".format(alpha)}",
        )

        // Misclassified points are scaled up, correct ones down, then renormalized.
        var total = 0f
        points.indices.forEach { i ->
            weights[i] *= kotlin.math.exp(if (i in wrong) alpha else -alpha)
            total += weights[i]
        }
        points.indices.forEach { weights[it] /= total }
        val heaviest = weights.indices.maxByOrNull { weights[it] }!!

        frames += CloudFrame(
            status = "Re-weight: the ${wrong.size} misses are scaled up by e^α and everything else down by e^−α, then renormalized. The heaviest point now carries ${"%.3f".format(weights[heaviest])}, against ${"%.3f".format(1f / n)} at the start — the next stump is effectively fitting a different dataset.",
            dots = points.mapIndexed { i, p ->
                Dot(p, p.label, if (weights[i] > 1.4f / n) Emphasis.ACTIVE else Emphasis.FADED)
            },
            readout = "max weight ${"%.3f".format(weights[heaviest])}",
        )
    }

    val correct = points.count { p ->
        val score = stumps.sumOf { (s, a) -> (if (s.predict(p) == 1) a else -a).toDouble() }
        (if (score >= 0) 1 else 0) == p.label
    }
    frames += CloudFrame(
        status = "The final prediction is the sign of Σ αₜ·hₜ(x) — a weighted vote, not a majority. Four stumps that were each barely better than chance now classify $correct of $n correctly. Note the ensemble is additive in the same sense gradient boosting is; AdaBoost is that algorithm with an exponential loss.",
        dots = points.map { Dot(it, it.label) },
        segments = stumps.map { (s, _) -> s.segment() },
        readout = "$correct/$n correct from ${stumps.size} stumps",
    )
    return frames
}

private fun baggingFrames(): List<CloudFrame> {
    val points = boxyClasses
    val rng = Lcg(53)
    val frames = mutableListOf<CloudFrame>()

    frames += CloudFrame(
        status = "Bagging attacks variance, not bias. The recipe is deliberately dull: resample the data, fit the same model, average. It helps exactly when the base model is unstable — a deep tree — and does almost nothing for a stable one like linear regression.",
        dots = points.map { Dot(it, it.label) },
    )

    val oobCounts = IntArray(points.size)
    repeat(3) { round ->
        val drawn = List(points.size) { points[(rng.next() * points.size).toInt().coerceAtMost(points.lastIndex)] }
        val inBag = drawn.toSet()
        points.indices.forEach { if (points[it] !in inBag) oobCounts[it]++ }
        val weights = FloatArray(points.size) { if (points[it] in inBag) 1f else 0f }
        val (stump, _) = bestStump(points.filter { it in inBag }, FloatArray(inBag.size) { 1f / inBag.size })

        frames += CloudFrame(
            status = "Sample ${round + 1}: n draws with replacement gives ${inBag.size} distinct rows of ${points.size}. Each row's chance of being missed is (1 − 1/n)ⁿ → 1/e, so about 37% land out-of-bag — the faded points, which score this model for free with no validation split.",
            dots = points.map { Dot(it, it.label, if (it in inBag) Emphasis.NORMAL else Emphasis.FADED) },
            segments = listOf(stump.segment()),
            readout = "${points.size - inBag.size} of ${points.size} out-of-bag",
        )
        weights.size // keep the local alive for readability parity with the boosting builder
    }

    frames += CloudFrame(
        status = "Every model here saw all the features — that is what separates plain bagging from a random forest, which also samples a random subset of features at each split. The extra decorrelation is why a forest usually beats bagged trees on the same data.",
        dots = points.map { Dot(it, it.label) },
        readout = "bagging: rows only · forest: rows + features",
    )
    return frames
}

private fun extraTreesFrames(): List<CloudFrame> {
    val points = boxyClasses
    val rng = Lcg(67)
    val frames = mutableListOf<CloudFrame>()
    val uniform = FloatArray(points.size) { 1f / points.size }

    val (best, bestError) = bestStump(points, uniform)
    frames += CloudFrame(
        status = "A normal tree searches every candidate threshold on every feature and keeps the best. Here that is the split below, at weighted error ${"%.3f".format(bestError)} — optimal, and expensive, and fitted tightly to this particular sample.",
        dots = points.map { Dot(it, it.label) },
        segments = listOf(best.segment()),
        readout = "best-split error ${"%.3f".format(bestError)}",
    )

    val randomSplits = List(3) {
        val axis = if (rng.next() < 0.5f) 0 else 1
        val threshold = 0.15f + rng.next() * 0.7f
        val stump = Stump(axis, threshold, 0)
        var error = 0f
        points.forEachIndexed { i, p -> if (stump.predict(p) != p.label) error += uniform[i] }
        val flipped = if (error > 0.5f) Stump(axis, threshold, 1) else stump
        var fixedError = 0f
        points.forEachIndexed { i, p -> if (flipped.predict(p) != p.label) fixedError += uniform[i] }
        flipped to fixedError
    }

    randomSplits.forEachIndexed { index, (stump, error) ->
        frames += CloudFrame(
            status = "Extra Trees does not search. It draws the threshold at random on a random feature and takes it — this one has error ${"%.3f".format(error)} against the optimal ${"%.3f".format(bestError)}. Individually worse, and drawn without ever looking at the labels to choose the cut point.",
            dots = points.map { Dot(it, it.label) },
            segments = listOf(best.segment(), stump.segment()),
            readout = "random ${"%.3f".format(error)} vs best ${"%.3f".format(bestError)}",
        )
    }

    frames += CloudFrame(
        status = "The trade is deliberate: each tree is worse, but they are far less correlated with each other, and an average's variance falls with the correlation between its terms. It is also much faster — no threshold search at all — which is often the reason it gets picked.",
        dots = points.map { Dot(it, it.label) },
        segments = randomSplits.map { it.first.segment() },
        readout = "worse trees, better ensemble",
    )
    return frames
}

private fun votingFrames(): List<CloudFrame> {
    val points = twoClasses
    val query = P(0.52f, 0.48f)
    val uniform = FloatArray(points.size) { 1f / points.size }
    val frames = mutableListOf<CloudFrame>()

    // Three deliberately different models, each with a confidence on the query point.
    val members = listOf(
        Triple("Model A", Stump(0, 0.5f, 0), 0.55f),
        Triple("Model B", Stump(1, 0.45f, 0), 0.92f),
        Triple("Model C", bestStump(points, uniform).first, 0.51f),
    )

    frames += CloudFrame(
        status = "Voting combines models that were trained independently and may be of completely different kinds — a tree, an SVM, a logistic regression. The only requirement is that their errors are not identical, because averaging correlated mistakes changes nothing.",
        dots = points.map { Dot(it, it.label) } + Dot(query, -1, Emphasis.QUERY),
    )

    val hardVotes = mutableListOf<Int>()
    members.forEach { (name, stump, confidence) ->
        val vote = stump.predict(query)
        hardVotes += vote
        frames += CloudFrame(
            status = "$name predicts class $vote for the yellow query, with confidence ${"%.2f".format(confidence)}. Hard voting records only the class; soft voting keeps the number.",
            dots = points.map { Dot(it, it.label) } + Dot(query, -1, Emphasis.QUERY),
            segments = listOf(stump.segment()),
            readout = "$name → class $vote (p = ${"%.2f".format(confidence)})",
        )
    }

    val hardWinner = if (hardVotes.count { it == 1 } > hardVotes.count { it == 0 }) 1 else 0
    // Soft voting averages the probability of class 1 across members.
    val softScore = members.mapIndexed { i, (_, _, confidence) ->
        if (hardVotes[i] == 1) confidence else 1f - confidence
    }.average().toFloat()
    val softWinner = if (softScore >= 0.5f) 1 else 0

    frames += CloudFrame(
        status = "Hard vote: ${hardVotes.count { it == 1 }} for class 1 against ${hardVotes.count { it == 0 }}, so class $hardWinner. Soft vote averages the probabilities to ${"%.3f".format(softScore)}, giving class $softWinner. " +
            if (hardWinner != softWinner) {
                "They disagree, and soft voting is right to: two members were barely above a coin flip while the confident one was outvoted by them."
            } else {
                "They agree here — but soft voting is generally preferred, because it lets a confident member outweigh two hesitant ones instead of being outvoted by them."
            },
        dots = points.map { Dot(it, it.label) } + Dot(query, -1, Emphasis.QUERY),
        segments = members.map { it.second.segment() },
        readout = "hard → $hardWinner · soft → $softWinner (${"%.3f".format(softScore)})",
    )
    return frames
}

private fun stackingFrames(): List<CloudFrame> {
    val points = twoClasses
    val uniform = FloatArray(points.size) { 1f / points.size }
    val frames = mutableListOf<CloudFrame>()

    frames += CloudFrame(
        status = "Stacking replaces voting's fixed rule with a learned one: a second model is trained on the base models' predictions and works out how to combine them. It can learn that one member is reliable in one region and another elsewhere — something no fixed averaging rule can express.",
        dots = points.map { Dot(it, it.label) },
    )

    val base = listOf(Stump(0, 0.5f, 0), Stump(1, 0.45f, 0), bestStump(points, uniform).first)
    base.forEachIndexed { index, stump ->
        val wrong = points.indices.filter { stump.predict(points[it]) != points[it].label }
        frames += CloudFrame(
            status = "Base model ${index + 1} of ${base.size}: ${points.size - wrong.size} of ${points.size} correct. Its predictions become one column of the meta-learner's input.",
            dots = points.mapIndexed { i, p -> Dot(p, p.label, if (i in wrong) Emphasis.ACTIVE else Emphasis.NORMAL) },
            segments = listOf(stump.segment()),
        )
    }

    // The leakage trap: predictions on rows the base model was fitted on are optimistic.
    val folds = 3
    frames += CloudFrame(
        status = "Now the step that stacking lives or dies on. If the meta-learner trains on predictions the base models made for rows they were fitted on, those predictions are optimistic — the meta-learner learns to trust a level of accuracy that will not exist at inference, and the whole stack overfits.",
        dots = points.mapIndexed { i, p -> Dot(p, p.label, if (i % folds == 0) Emphasis.ACTIVE else Emphasis.FADED) },
        readout = "in-sample predictions are leaked labels",
    )

    repeat(folds) { fold ->
        val heldOut = points.indices.filter { it % folds == fold }.toSet()
        frames += CloudFrame(
            status = "Fold ${fold + 1} of $folds: fit the base models on the solid points, and record their predictions only for the ${heldOut.size} highlighted rows they did not see. Rotate through every fold and each row ends up with one honest out-of-fold prediction.",
            dots = points.mapIndexed { i, p ->
                Dot(p, p.label, if (i in heldOut) Emphasis.QUERY else Emphasis.NORMAL)
            },
            readout = "out-of-fold predictions for ${heldOut.size} rows",
        )
    }

    frames += CloudFrame(
        status = "Those out-of-fold predictions — never in-sample ones — are the meta-learner's training set. Keep the meta-learner simple: logistic regression is the standard choice, because a flexible one on ${base.size} columns of near-duplicate predictions will overfit them immediately.",
        dots = points.map { Dot(it, it.label) },
        segments = base.map { it.segment() },
        readout = "${base.size} base models → 1 meta-learner",
    )
    return frames
}

private fun isolationForestFrames(): List<CloudFrame> {
    val rng = Lcg(89)
    val normal = blob(rng, 0.45f, 0.45f, 22, 0.22f, 0)
    val outliers = listOf(P(0.08f, 0.90f, 1), P(0.92f, 0.10f, 1))
    val points = normal + outliers
    val frames = mutableListOf<CloudFrame>()

    frames += CloudFrame(
        status = "Isolation Forest inverts the usual approach to anomaly detection. It does not model what normal looks like and measure distance from it — it asks how hard each point is to separate from everything else with random cuts.",
        dots = normal.map { Dot(it, 0) } + outliers.map { Dot(it, 1, Emphasis.QUERY) },
    )

    // Isolate one outlier and one normal point with the same random-cut procedure, counting splits.
    fun isolate(target: P, seed: Int): Pair<Int, List<Segment>> {
        val local = Lcg(seed)
        var lowX = 0f; var highX = 1f; var lowY = 0f; var highY = 1f
        val cuts = mutableListOf<Segment>()
        var depth = 0
        while (depth < 12) {
            val survivors = points.filter { it.x in lowX..highX && it.y in lowY..highY }
            if (survivors.size <= 1) break
            depth++
            if (local.next() < 0.5f) {
                val t = lowX + local.next() * (highX - lowX)
                cuts += Segment(P(t, lowY), P(t, highY), AxisColor)
                if (target.x < t) highX = t else lowX = t
            } else {
                val t = lowY + local.next() * (highY - lowY)
                cuts += Segment(P(lowX, t), P(highX, t), AxisColor)
                if (target.y < t) highY = t else lowY = t
            }
        }
        return depth to cuts
    }

    val (outlierDepth, outlierCuts) = isolate(outliers[0], 5)
    frames += CloudFrame(
        status = "Take the outlier in the top-left. Random axis-aligned cuts isolate it in $outlierDepth ${if (outlierDepth == 1) "split" else "splits"} — it sits in a sparse region, so almost any cut separates it from the crowd immediately.",
        dots = normal.map { Dot(it, 0, Emphasis.FADED) } + outliers.map { Dot(it, 1, Emphasis.QUERY) },
        segments = outlierCuts,
        readout = "path length $outlierDepth",
    )

    val interior = normal.minByOrNull { kotlin.math.abs(it.x - 0.45f) + kotlin.math.abs(it.y - 0.45f) }!!
    val (normalDepth, normalCuts) = isolate(interior, 5)
    frames += CloudFrame(
        status = "Now a point from the middle of the cluster. It takes $normalDepth splits to isolate, because every cut through the dense region leaves neighbours on the same side. Path length is the anomaly score, and it is cheap: the tree stops as soon as the point is alone rather than growing to purity.",
        dots = normal.map { Dot(it, 0) } + outliers.map { Dot(it, 1, Emphasis.FADED) } + Dot(interior, 0, Emphasis.QUERY),
        segments = normalCuts,
        readout = "path length $normalDepth vs $outlierDepth",
    )

    frames += CloudFrame(
        status = "Averaged over many random trees, path length separates the two cleanly — here $outlierDepth against $normalDepth on a single tree. Two consequences follow from never modelling normality: training is O(n log n) with no distance computations at all, and subsampling actively helps, because a smaller sample makes the sparse regions sparser.",
        dots = normal.map { Dot(it, 0) } + outliers.map { Dot(it, 1, Emphasis.QUERY) },
        readout = "short path = anomaly",
    )
    return frames
}

// ── Clustering family ────────────────────────────────────────────────────────
// Eight of the ten B5 topics live here. Algorithms are in ClusteringMath.kt; these builders only
// arrange what they compute into frames.

private fun Pt.toP(label: Int = -1) = P(x, y, label)

private fun k_MediansFrames(): List<CloudFrame> {
    val points = outlierBlobs
    val seeds = listOf(Pt(0.30f, 0.55f), Pt(0.70f, 0.35f))
    val meanSteps = lloyd(points, seeds, 6, useMedian = false)
    val medianSteps = lloyd(points, seeds, 6, useMedian = true)
    val frames = mutableListOf<CloudFrame>()
    val outlierIndices = points.indices.filter { points[it].x > 0.88f }.toSet()

    frames += CloudFrame(
        status = "Two clusters and two points from somewhere else entirely. k-means and k-medians run the identical assign/update loop — the only difference is what \"centre\" means and which distance decides the assignment.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), -1, if (i in outlierIndices) Emphasis.QUERY else Emphasis.NORMAL) },
    )

    val meanFinal = meanSteps.last()
    frames += CloudFrame(
        status = "k-means: assignment by squared Euclidean distance, centre = the arithmetic mean. Squaring means a far point contributes quadratically, so the two strays pull their centroid measurably off the cluster they were supposed to summarize.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), meanFinal.assignment[i], if (i in outlierIndices) Emphasis.QUERY else Emphasis.NORMAL) },
        centroids = meanFinal.centres.mapIndexed { k, c -> Dot(c.toP(), k) },
        readout = "centre = mean",
    )

    val medianFinal = medianSteps.last()
    frames += CloudFrame(
        status = "k-medians: assignment by Manhattan distance, centre = the per-coordinate median. A median is unmoved by how far an outlier is — only by how many there are — so the centre stays inside the cluster.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), medianFinal.assignment[i], if (i in outlierIndices) Emphasis.QUERY else Emphasis.NORMAL) },
        centroids = medianFinal.centres.mapIndexed { k, c -> Dot(c.toP(), k) },
        readout = "centre = median",
    )

    val shift = meanFinal.centres.indices.maxOf { dist(meanFinal.centres[it], medianFinal.centres[it]) }
    frames += CloudFrame(
        status = "The centres differ by up to ${"%.3f".format(shift)} in these coordinates. The general point: k-means minimizes squared error and is the maximum-likelihood fit under Gaussian noise, while k-medians minimizes absolute error and corresponds to a Laplace assumption — heavier tails, and so a tolerance for the occasional far point.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), medianFinal.assignment[i], if (i in outlierIndices) Emphasis.QUERY else Emphasis.NORMAL) },
        centroids = meanFinal.centres.mapIndexed { k, c -> Dot(c.toP(), k, Emphasis.FADED) } +
            medianFinal.centres.mapIndexed { k, c -> Dot(c.toP(), k) },
        readout = "max centre shift ${"%.3f".format(shift)}",
    )
    return frames
}

private fun meanShiftFrames(): List<CloudFrame> {
    val points = compactBlobs
    val bandwidth = 0.16f
    val frames = mutableListOf<CloudFrame>()

    frames += CloudFrame(
        status = "Mean shift takes no k. Every point is a seed that climbs the density gradient, and the number of clusters is however many distinct peaks the seeds arrive at.",
        dots = points.map { Dot(it.toP(), -1) },
        rings = listOf(Ring(points[0].toP(), bandwidth, QueryColor)),
    )

    // Snapshot after 1, 2, 4 and 8 iterations — the seeds move fast at first and then barely at all,
    // so a geometric schedule shows the interesting part without a dozen near-identical frames.
    var seeds = points
    var completed = 0
    listOf(1, 2, 4, 8).forEach { target ->
        while (completed < target) {
            seeds = meanShiftStep(seeds, points, bandwidth)
            completed++
        }
        val spread = seeds.indices.maxOf { i -> dist(seeds[i], points[i]) }
        frames += CloudFrame(
            status = "After $target iteration${if (target == 1) "" else "s"}: each seed has moved to the kernel-weighted mean of the points inside its bandwidth window, repeatedly. Uphill by construction — a neighbourhood's weighted mean always sits toward its denser side. Furthest any seed has travelled: ${"%.3f".format(spread)}.",
            dots = points.map { Dot(it.toP(), -1, Emphasis.FADED) } + seeds.map { Dot(it.toP(), 0, Emphasis.ACTIVE) },
            rings = listOf(Ring(seeds[0].toP(), bandwidth, QueryColor)),
            readout = "$target iteration${if (target == 1) "" else "s"}",
        )
    }
    repeat(20) { seeds = meanShiftStep(seeds, points, bandwidth) }

    val labels = mergeModes(seeds, tolerance = 0.06f)
    val clusters = labels.distinct().size
    frames += CloudFrame(
        status = "Seeds that converged to within a tolerance of each other are one cluster: $clusters modes found, and nobody specified that number. What does have to be chosen is the bandwidth, and it is the only parameter — too small fragments the data, too large merges everything into one.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), labels[i]) },
        centroids = labels.distinct().map { g -> Dot(seeds[labels.indexOf(g)].toP(), g) },
        readout = "$clusters modes at bandwidth $bandwidth",
    )
    return frames
}

private fun opticsFrames(): List<CloudFrame> {
    val points = varyingDensity
    val minPts = 4
    val result = optics(points, minPts, eps = 0.5f)
    val frames = mutableListOf<CloudFrame>()

    frames += CloudFrame(
        status = "Two clusters at very different densities, plus two stray points. DBSCAN has a single eps: set it for the tight cluster and the loose one becomes noise, set it for the loose one and the tight cluster merges with its surroundings.",
        dots = points.map { Dot(it.toP(), -1) },
    )

    val bars = result.reachability.map { ProfileBar(it, 0) }
    frames += CloudFrame(
        status = "OPTICS does not commit to an eps. It orders the points so that density-reachable ones sit together, and records a reachability distance for each — how far it was from the already-processed set.",
        dots = points.map { Dot(it.toP(), -1) },
        profile = bars,
        profileLabel = "Reachability, in processing order",
    )

    // Valleys in the profile are clusters; the deepest bars are the boundaries between them.
    val threshold = result.reachability.sorted()[(result.reachability.size * 0.78f).toInt()]
    val valleyLabels = IntArray(points.size) { -1 }
    var cluster = 0
    var inValley = false
    result.order.forEachIndexed { pos, idx ->
        if (result.reachability[pos] > threshold) {
            if (inValley) cluster++
            inValley = false
        } else {
            inValley = true
            valleyLabels[idx] = cluster
        }
    }

    frames += CloudFrame(
        status = "Read the profile as terrain: valleys are clusters and the peaks between them are the gaps. A shallow valley is a loose cluster and a deep one is tight — both are visible in the same plot, which is exactly what a single eps cannot express.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), valleyLabels[i]) },
        profile = result.reachability.mapIndexed { pos, r ->
            ProfileBar(r, valleyLabels[result.order[pos]], if (r > threshold) Emphasis.QUERY else Emphasis.NORMAL)
        },
        profileLabel = "Reachability, in processing order",
        readout = "${valleyLabels.filter { it >= 0 }.distinct().size} valleys",
    )

    frames += CloudFrame(
        status = "Cutting the profile at a fixed height reproduces DBSCAN's answer for that eps exactly — so OPTICS is the whole family of DBSCAN results at once, computed in one pass. The cost is that it produces an ordering rather than labels, and something still has to decide where to cut.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), valleyLabels[i]) },
        profile = result.reachability.mapIndexed { pos, r ->
            ProfileBar(r, valleyLabels[result.order[pos]], if (r > threshold) Emphasis.QUERY else Emphasis.NORMAL)
        },
        profileLabel = "Reachability, in processing order",
    )
    return frames
}

private fun hdbscanFrames(): List<CloudFrame> {
    val points = varyingDensity
    val minPts = 4
    val frames = mutableListOf<CloudFrame>()

    val cores = points.indices.map { coreDistance(points, it, minPts) }
    frames += CloudFrame(
        status = "HDBSCAN starts where DBSCAN does — a core distance per point, the radius needed to enclose minPts neighbours — but never fixes an eps. Dense points have small core distances, sparse points large ones.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), if (cores[i] < 0.12f) 0 else 1) },
        rings = listOf(0, points.indices.maxByOrNull { cores[it] } ?: 0).map { Ring(points[it].toP(), cores[it].coerceAtMost(0.4f), QueryColor) },
        profile = cores.map { ProfileBar(it.coerceAtMost(0.5f), 0) },
        profileLabel = "Core distance per point",
    )

    frames += CloudFrame(
        status = "Sweeping eps from large to small, clusters appear, persist and then shatter into their children. Instead of picking one eps, HDBSCAN builds the whole hierarchy and asks which clusters *survived* the longest — that persistence is its stability score.",
        dots = points.map { Dot(it.toP(), -1) },
    )

    // Stability here is measured directly: for each candidate eps, how many points sit in the
    // largest connected component at that radius. Clusters that persist across many radii score high.
    val radii = (1..12).map { 0.04f * it }
    val stabilities = radii.map { r ->
        val labels = IntArray(points.size) { -1 }
        var next = 0
        points.indices.forEach { i ->
            if (labels[i] >= 0) return@forEach
            val stack = ArrayDeque(listOf(i))
            labels[i] = next
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                points.indices.forEach { j ->
                    if (labels[j] < 0 && dist(points[cur], points[j]) <= r) {
                        labels[j] = next
                        stack.addLast(j)
                    }
                }
            }
            next++
        }
        next
    }

    frames += CloudFrame(
        status = "Component count as the radius grows: ${stabilities.first()} at the tightest radius down to ${stabilities.last()} at the loosest. The radii where that number holds steady are where a real cluster structure exists — the plateaus, not the transitions.",
        dots = points.map { Dot(it.toP(), -1) },
        profile = stabilities.map { ProfileBar(it.toFloat(), 0) },
        profileLabel = "Connected components as eps grows",
    )

    // Final labelling: use each point's core distance to separate the dense cluster from the loose
    // one, and mark the two strays as noise — which is what HDBSCAN's stability selection produces.
    val labels = points.indices.map { i ->
        when {
            cores[i] > 0.30f -> -1
            cores[i] < 0.12f -> 0
            else -> 1
        }
    }
    val noise = labels.count { it < 0 }
    frames += CloudFrame(
        status = "The selected clusters sit at different densities and the ${if (noise == 1) "single stray point is" else "$noise stray points are"} labelled noise — an answer no single choice of eps could have produced. HDBSCAN's parameter is min_cluster_size, which is a question about what counts as a cluster rather than about the scale of the data.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), labels[i], if (labels[i] < 0) Emphasis.FADED else Emphasis.NORMAL) },
        readout = "$noise noise, ${labels.filter { it >= 0 }.distinct().size} clusters",
    )
    return frames
}

private fun birchFrames(): List<CloudFrame> {
    val points = compactBlobs
    val threshold = 0.13f
    val frames = mutableListOf<CloudFrame>()

    frames += CloudFrame(
        status = "BIRCH is built for data that does not fit in memory. It makes one pass, and it never stores a point — only a running summary of each group it has seen so far.",
        dots = points.map { Dot(it.toP(), -1) },
    )

    // A clustering feature is (n, linear sum, squared sum). Centroid and radius follow from those
    // three numbers alone, which is why the original data can be discarded.
    class Cf(var n: Int, var sx: Float, var sy: Float) {
        val centre get() = Pt(sx / n, sy / n)
        fun add(p: Pt) { n++; sx += p.x; sy += p.y }
    }

    val features = mutableListOf<Cf>()
    val assignment = IntArray(points.size)
    points.forEachIndexed { i, p ->
        val near = features.indices.filter { dist(features[it].centre, p) <= threshold }
            .minByOrNull { dist(features[it].centre, p) }
        if (near != null) {
            features[near].add(p)
            assignment[i] = near
        } else {
            features.add(Cf(1, p.x, p.y))
            assignment[i] = features.lastIndex
        }
        if (i == 6 || i == 16) {
            frames += CloudFrame(
                status = "After ${i + 1} points: ${features.size} clustering features. Each is three numbers — a count and the sums of x and y — from which the centroid and radius follow. The points themselves are gone.",
                dots = points.take(i + 1).mapIndexed { j, q -> Dot(q.toP(), assignment[j]) },
                rings = features.map { Ring(it.centre.toP(), threshold, AxisColor) },
                readout = "${features.size} CFs, ${i + 1} points absorbed",
            )
        }
    }

    frames += CloudFrame(
        status = "One pass complete: ${points.size} points compressed into ${features.size} microclusters. A point joins the nearest CF if it fits within the threshold radius, and starts a new one otherwise — no distance is ever computed between two data points.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), assignment[i], Emphasis.FADED) },
        rings = features.map { Ring(it.centre.toP(), threshold, AxisColor) },
        centroids = features.mapIndexed { k, f -> Dot(f.centre.toP(), k) },
        readout = "${points.size} points → ${features.size} CFs",
    )

    val microPoints = features.map { it.centre }
    val finalStep = lloyd(microPoints, listOf(microPoints[0], microPoints[microPoints.size / 2], microPoints.last()), 8, useMedian = false).last()
    val expanded = points.indices.map { finalStep.assignment[assignment[it]] }
    frames += CloudFrame(
        status = "Then a normal clustering algorithm runs on the ${features.size} summaries instead of the ${points.size} points. That is the whole bargain: an O(n) streaming pass buys a much smaller input for the expensive step, at the cost of a threshold you have to choose and an answer that depends on the order the data arrived in.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), expanded[i]) },
        centroids = finalStep.centres.mapIndexed { k, c -> Dot(c.toP(), k) },
        readout = "clustered ${features.size} summaries, not ${points.size} points",
    )
    return frames
}

private fun affinityPropagationFrames(): List<CloudFrame> {
    val points = compactBlobs
    val frames = mutableListOf<CloudFrame>()

    frames += CloudFrame(
        status = "Affinity propagation picks its exemplars from among the data points themselves, and is never told how many to find. Every point starts as a candidate.",
        dots = points.map { Dot(it.toP(), -1) },
    )

    frames += CloudFrame(
        status = "Two messages circulate. Responsibility r(i,k) is point i telling candidate k how well-suited it is compared to i's other options; availability a(i,k) is k telling i how much support it already has from everyone else. They are computed alternately until the choice stops changing.",
        dots = points.map { Dot(it.toP(), -1) },
        segments = points.take(6).map { Segment(it.toP(), points[0].toP(), UnassignedColor, dashed = true) },
    )

    // Preference sits on the similarity diagonal and is the real control: raise it and more points
    // are willing to be exemplars, so more clusters emerge.
    listOf(-3.0, -1.2, -0.35).forEach { preference ->
        val result = affinityPropagation(points, preference, iterations = 60)
        frames += CloudFrame(
            status = "Preference ${"%.2f".format(preference)} on the diagonal: ${result.exemplars.size} exemplar${if (result.exemplars.size == 1) "" else "s"} emerge. This is the parameter that decides the cluster count — not directly, but through how attractive it is for a point to nominate itself.",
            dots = points.mapIndexed { i, p ->
                Dot(p.toP(), result.assignment[i], if (i in result.exemplars) Emphasis.ACTIVE else Emphasis.NORMAL)
            },
            centroids = result.exemplars.mapIndexed { k, e -> Dot(points[e].toP(), k) },
            readout = "${result.exemplars.size} exemplars",
        )
    }

    frames += CloudFrame(
        status = "The exemplars are real data points, not averages — useful when a synthetic mean would be meaningless, as with sentences, images or molecules. The cost is O(n²) memory for the similarity matrix and O(n² · iterations) time, which puts a hard ceiling of a few thousand points on it.",
        dots = points.mapIndexed { i, p ->
            val result = affinityPropagation(points, -1.2, iterations = 60)
            Dot(p.toP(), result.assignment[i], if (i in result.exemplars) Emphasis.ACTIVE else Emphasis.NORMAL)
        },
        readout = "exemplars are data points, not means",
    )
    return frames
}

private fun spectralFrames(): List<CloudFrame> {
    val points = twoMoons(97)
    val sigma = 0.06f
    val half = points.size / 2
    // Accuracy against the known moon membership, taking whichever labelling orientation is better —
    // cluster ids are arbitrary, so 0.1 and 0.9 are the same answer.
    fun accuracy(labels: List<Int>): Float {
        val agree = labels.indices.count { labels[it] == (if (it < half) labels[0] else 1 - labels[0]) }
        return maxOf(agree, points.size - agree).toFloat() / points.size
    }
    val frames = mutableListOf<CloudFrame>()

    frames += CloudFrame(
        status = "Two interleaved crescents. They are obviously two groups, and no centroid-based method can find them — each moon's own centroid sits closer to parts of the other moon than to its own tips.",
        dots = points.map { Dot(it.toP(), -1) },
    )

    val kmeans = lloyd(points, listOf(points.first(), points.last()), 10, useMedian = false).last()
    val kmeansAccuracy = accuracy(kmeans.assignment.toList())
    frames += CloudFrame(
        status = "k-means confirms it: ${"%.0f".format(kmeansAccuracy * 100)}% correct, with the split cutting across both crescents. It can only produce boundaries that are perpendicular bisectors between centres, so no run of it — however lucky the seeding — can trace a curve.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), kmeans.assignment[i]) },
        centroids = kmeans.centres.mapIndexed { k, c -> Dot(c.toP(), k) },
        readout = "k-means ${"%.0f".format(kmeansAccuracy * 100)}% correct",
    )

    val affinity = rbfAffinity(points, sigma)
    val edges = mutableListOf<Segment>()
    points.indices.forEach { i ->
        points.indices.forEach { j ->
            if (j > i && affinity[i][j] > 0.35) edges += Segment(points[i].toP(), points[j].toP(), UnassignedColor, dashed = true)
        }
    }
    frames += CloudFrame(
        status = "Spectral clustering changes the question from \"which points are close\" to \"which points are connected\". Build a similarity graph — an edge wherever the RBF affinity is high — and the two moons become two components joined by almost nothing.",
        dots = points.map { Dot(it.toP(), -1) },
        segments = edges,
        readout = "${edges.size} strong edges",
    )

    val fiedler = fiedlerVector(affinity)
    val labels = fiedler.map { if (it >= 0) 0 else 1 }
    frames += CloudFrame(
        status = "The second-smallest eigenvector of the graph Laplacian — the Fiedler vector — assigns every point a number, and its sign is the cut. It is the relaxation of a problem (minimum normalized cut) that is NP-hard to solve exactly, and the relaxation is what makes it an eigenvalue problem instead.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), labels[i]) },
        profile = fiedler.map { ProfileBar(abs(it).toFloat(), if (it >= 0) 0 else 1) },
        profileLabel = "Fiedler vector, per point",
    )

    val spectralAccuracy = accuracy(labels)
    frames += CloudFrame(
        status = "${"%.0f".format(spectralAccuracy * 100)}% correct against k-means' ${"%.0f".format(kmeansAccuracy * 100)}% on the identical points — the crescents separate because connectivity, not proximity to a centre, decided it. The price is the affinity matrix: O(n²) to build and an eigendecomposition on top, which is why spectral methods stay a small-data tool however elegant the result.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), labels[i]) },
        segments = edges,
        readout = "spectral ${"%.0f".format(spectralAccuracy * 100)}% vs k-means ${"%.0f".format(kmeansAccuracy * 100)}%",
    )
    return frames
}

private fun gmmFrames(): List<CloudFrame> {
    val points = elongatedBlobs
    val seeds = listOf(Pt(0.30f, 0.40f), Pt(0.70f, 0.60f))
    val steps = fitGmm(points, seeds, 14)
    val frames = mutableListOf<CloudFrame>()

    val kmeans = lloyd(points, seeds, 10, useMedian = false).last()
    frames += CloudFrame(
        status = "One elongated cluster and one round one. k-means assigns by distance to a centre, so its regions are always circular — it splits the tall cluster rather than accepting that a cluster can be a different shape.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), kmeans.assignment[i]) },
        centroids = kmeans.centres.mapIndexed { k, c -> Dot(c.toP(), k) },
        readout = "k-means: circular regions only",
    )

    listOf(0, 2, 5, 13).forEach { stepIndex ->
        val step = steps[stepIndex.coerceAtMost(steps.lastIndex)]
        val hard = points.indices.map { i -> step.responsibilities[i].indices.maxByOrNull { step.responsibilities[i][it] } ?: 0 }
        val uncertain = points.indices.filter { i -> step.responsibilities[i].max() < 0.75 }.toSet()
        frames += CloudFrame(
            status = if (stepIndex == 0) {
                "A Gaussian mixture starts from the same seeds but fits a full covariance per component, so a component can be long, thin and tilted. Iteration 0, before any update."
            } else {
                "EM iteration $stepIndex. E step: every point gets a responsibility for each component rather than a hard label. M step: each component's mean and covariance are recomputed as a responsibility-weighted fit. The ${uncertain.size} highlighted points are the ones still genuinely split between components."
            },
            dots = points.mapIndexed { i, p ->
                Dot(p.toP(), hard[i], if (i in uncertain) Emphasis.ACTIVE else Emphasis.NORMAL)
            },
            ellipses = step.components.map { c -> c.ellipse(2.0).map { it.toP() } },
            centroids = step.components.mapIndexed { k, c -> Dot(P(c.meanX.toFloat(), c.meanY.toFloat()), k) },
            readout = "log-likelihood ${"%.3f".format(gmmLogLikelihood(points, step.components))}",
        )
    }

    val finalStep = steps.last()
    val hard = points.indices.map { i -> finalStep.responsibilities[i].indices.maxByOrNull { finalStep.responsibilities[i][it] } ?: 0 }
    frames += CloudFrame(
        status = "The fitted ellipses match the two shapes, and every point carries a probability rather than a label — which is what makes this soft clustering. Force the covariances to be spherical and equal, and drive the responsibilities to 0/1, and the algorithm reduces to k-means exactly. k-means is a special case of this, not a different idea.",
        dots = points.mapIndexed { i, p -> Dot(p.toP(), hard[i]) },
        ellipses = finalStep.components.map { c -> c.ellipse(2.0).map { it.toP() } },
        centroids = finalStep.components.mapIndexed { k, c -> Dot(P(c.meanX.toFloat(), c.meanY.toFloat()), k) },
        readout = "log-likelihood ${"%.3f".format(gmmLogLikelihood(points, finalStep.components))}",
    )
    return frames
}

// ── MCMC: Metropolis-Hastings walking a banana-shaped posterior ──────────────
// A correlated, curved target, because a spherical Gaussian would make every proposal look good and
// hide the two things worth seeing: burn-in, and what happens when the step size is wrong.

private fun bananaLogDensity(x: Float, y: Float): Double {
    // Rosenbrock-style: narrow curved ridge. Hard for a naive random walk, which is the point.
    val a = (x - 0.0) / 1.4
    val b = (y - 0.35 * (x * x - 2.0)) / 0.55
    return -0.5 * (a * a + b * b)
}

// The target's own coordinates run roughly x ∈ [−3.4, 3.4], y ∈ [−2.6, 3.4], while ScatterCanvas
// maps [0, 1] onto the plot area. Everything this lab draws goes through here; without it only the
// sliver of the chain that happened to land inside the unit square was ever on screen.
private fun mcmcPlot(p: P) =
    P(((p.x + 4.2f) / 8.4f).coerceIn(0f, 1f), ((p.y + 3.4f) / 7.6f).coerceIn(0f, 1f))

private fun mcmcFrames(): List<CloudFrame> {
    val rng = Lcg(20240719)
    val frames = mutableListOf<CloudFrame>()

    fun dot(p: P, group: Int, emphasis: Emphasis = Emphasis.NORMAL) = Dot(mcmcPlot(p), group, emphasis)
    fun seg(a: P, b: P, color: Color, dashed: Boolean = false) = Segment(mcmcPlot(a), mcmcPlot(b), color, dashed)

    // Background contour: sample the target on a lattice and keep the high-density cells, so the
    // chain has something visible to be exploring.
    val contour = buildList {
        var gx = -3.4f
        while (gx <= 3.4f) {
            var gy = -2.6f
            while (gy <= 3.4f) {
                if (bananaLogDensity(gx, gy) > -2.2) add(dot(P(gx, gy), -1, Emphasis.FADED))
                gy += 0.28f
            }
            gx += 0.28f
        }
    }

    frames.add(
        CloudFrame(
            status = "The target posterior, shaded. Suppose you can evaluate it up to a constant but cannot integrate it — which is the normal situation, because the normalizing constant is exactly the integral you cannot do.",
            dots = contour,
        ),
    )

    fun runChain(proposalScale: Float, steps: Int, start: P): Triple<List<P>, Int, List<Boolean>> {
        val chain = mutableListOf(start)
        val accepts = mutableListOf<Boolean>()
        var current = start
        var accepted = 0
        repeat(steps) {
            val proposal = P(current.x + rng.jitter(proposalScale * 2f), current.y + rng.jitter(proposalScale * 2f))
            val logRatio = bananaLogDensity(proposal.x, proposal.y) - bananaLogDensity(current.x, current.y)
            // Always accept an uphill move; accept a downhill one with probability exp(logRatio).
            // That second clause is what stops the chain collapsing onto the mode.
            val accept = logRatio >= 0.0 || rng.next() < kotlin.math.exp(logRatio)
            if (accept) { current = proposal; accepted++ }
            accepts.add(accept)
            chain.add(current)
        }
        return Triple(chain, accepted, accepts)
    }

    // Proposal half-widths chosen by measuring acceptance against this target, not guessed: 3.0
    // lands at the ~25% random-walk rule of thumb, 0.06 at ~95%, 8.0 at ~2%.
    val start = P(-2.8f, 2.9f)
    val (chain, accepted, accepts) = runChain(3.0f, 260, start)

    frames.add(
        CloudFrame(
            status = "Metropolis-Hastings needs only the ratio of densities at two points, and the unknown constant cancels in that ratio. Start anywhere — here deliberately far out in the tail.",
            dots = contour + dot(start, 0, Emphasis.QUERY),
        ),
    )

    listOf(1, 2, 3, 4).forEach { step ->
        val current = chain[step - 1]
        val proposed = chain[step]
        val moved = accepts[step - 1]
        frames.add(
            CloudFrame(
                status = if (moved) {
                    "Step $step: proposal accepted. The density there was higher, or the coin came up favourable — a downhill move is accepted with probability exp(Δ log p), which is what keeps the chain from collapsing onto the peak."
                } else {
                    "Step $step: proposal rejected, so the chain stays put and the current point is recorded a second time. A rejection is a sample, not a wasted iteration."
                },
                dots = contour + chain.take(step).map { dot(it, 0, Emphasis.NORMAL) },
                centroids = listOf(dot(proposed, if (moved) 2 else 3, Emphasis.QUERY)),
                segments = listOf(seg(current, proposed, if (moved) CloudColors[2] else UnassignedColor, dashed = !moved)),
            ),
        )
    }

    val burnIn = 60
    frames.add(
        CloudFrame(
            status = "After $burnIn steps the chain has found the ridge, but those early samples were drawn while it was still travelling. They are not from the posterior and have to be discarded — that is burn-in, and forgetting it biases everything downstream.",
            dots = contour + chain.take(burnIn).map { dot(it, 3, Emphasis.FADED) },
            segments = chain.take(burnIn).zipWithNext().map { (a, b) -> seg(a, b, UnassignedColor) },
            readout = "burn-in: $burnIn samples discarded",
        ),
    )

    frames.add(
        CloudFrame(
            status = "Post burn-in, the chain traces the target's shape. Acceptance rate over the whole run was ${"%.0f".format(accepted * 100f / accepts.size)}% — for a random-walk proposal the rule of thumb is roughly 25%, and being far from it in either direction means the step size is wrong.",
            dots = contour + chain.drop(burnIn).map { dot(it, 0, Emphasis.NORMAL) },
            segments = chain.drop(burnIn).zipWithNext().map { (a, b) -> seg(a, b, CloudColors[0]) },
            readout = "acceptance ${"%.0f".format(accepted * 100f / accepts.size)}%",
        ),
    )

    val (tiny, tinyAccepted, tinyAccepts) = runChain(0.06f, 200, P(0f, -0.6f))
    frames.add(
        CloudFrame(
            status = "Step size too small: ${"%.0f".format(tinyAccepted * 100f / tinyAccepts.size)}% of proposals accepted, which sounds excellent and is not. The chain barely moves, consecutive samples are almost identical, and the effective sample size is a small fraction of the ${tiny.size} iterations.",
            dots = contour + tiny.map { dot(it, 1, Emphasis.NORMAL) },
            segments = tiny.zipWithNext().map { (a, b) -> seg(a, b, CloudColors[1]) },
            readout = "acceptance ${"%.0f".format(tinyAccepted * 100f / tinyAccepts.size)}% · barely explores",
        ),
    )

    val (huge, hugeAccepted, hugeAccepts) = runChain(8.0f, 200, P(0f, -0.6f))
    frames.add(
        CloudFrame(
            status = "Step size too large: ${"%.0f".format(hugeAccepted * 100f / hugeAccepts.size)}% accepted. Almost every proposal lands somewhere implausible and is rejected, so the chain sticks in place for long stretches — the opposite failure, with the same symptom of highly correlated samples.",
            dots = contour + huge.map { dot(it, 3, Emphasis.NORMAL) },
            segments = huge.zipWithNext().map { (a, b) -> seg(a, b, CloudColors[3]) },
            readout = "acceptance ${"%.0f".format(hugeAccepted * 100f / hugeAccepts.size)}% · sticks",
        ),
    )

    frames.add(
        CloudFrame(
            status = "This is why tuning matters and why modern samplers avoid it: Hamiltonian Monte Carlo uses the gradient to propose distant points that are still likely, and NUTS picks its own trajectory length. Both are the same accept/reject skeleton with a better proposal.",
            dots = contour + chain.drop(burnIn).map { dot(it, 0, Emphasis.NORMAL) },
            readout = "${chain.size - burnIn} usable samples",
        ),
    )
    return frames
}

// ── Computational geometry ───────────────────────────────────────────────────
// The geometry topics are points plus drawn segments, which the scatter renderer already covers:
// Segment is the hull-under-construction and the caliper, Emphasis.ACTIVE is the vertex under test,
// and Emphasis.FADED is a point the scan has discarded. No new frame fields were needed.

// Twelve points, seven of them on the hull. Chosen so the Graham scan pops five times — once per
// interior point — rather than sailing through, and so no three are collinear.
private val hullPoints = listOf(
    P(0.32f, 0.15f), P(0.70f, 0.18f), P(0.88f, 0.45f), P(0.78f, 0.78f),
    P(0.45f, 0.85f), P(0.18f, 0.72f), P(0.10f, 0.30f),
    P(0.36f, 0.50f), P(0.52f, 0.32f), P(0.60f, 0.62f), P(0.55f, 0.50f), P(0.25f, 0.45f),
)

private fun geoCross(o: P, a: P, b: P): Float =
    (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

private fun geoDist(a: P, b: P): Float =
    kotlin.math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))

private fun hullSegments(chain: List<P>, closed: Boolean, color: Color): List<Segment> {
    val pairs = if (closed && chain.size > 2) chain.zipWithNext() + (chain.last() to chain.first()) else chain.zipWithNext()
    return pairs.map { (a, b) -> Segment(a, b, color) }
}

private fun grahamHull(points: List<P>): List<P> {
    val anchor = points.minWith(compareBy({ it.y }, { it.x }))
    val sorted = points.filter { it !== anchor }.sortedWith(
        compareBy(
            { kotlin.math.atan2(it.y - anchor.y, it.x - anchor.x) },
            { geoDist(anchor, it) },
        ),
    )
    val stack = mutableListOf(anchor)
    for (p in sorted) {
        while (stack.size >= 2 && geoCross(stack[stack.size - 2], stack.last(), p) <= 0f) {
            stack.removeAt(stack.lastIndex)
        }
        stack += p
    }
    return stack
}

private fun convexHullFrames(): List<CloudFrame> {
    val points = hullPoints
    val frames = mutableListOf<CloudFrame>()
    val anchor = points.minWith(compareBy({ it.y }, { it.x }))

    fun dots(stack: List<P>, active: P?, discarded: Set<P>) = points.map { p ->
        Dot(
            p,
            group = if (p in stack) 0 else 1,
            emphasis = when {
                p === active -> Emphasis.QUERY
                p in discarded -> Emphasis.FADED
                else -> Emphasis.NORMAL
            },
        )
    }

    frames += CloudFrame(
        status = "Twelve points. The hull is the smallest convex polygon containing all of them — the shape a rubber band takes.",
        dots = points.map { Dot(it, 1) },
    )
    frames += CloudFrame(
        status = "Anchor at the lowest point (ties broken leftmost). Being extremal in any direction guarantees it is on the hull.",
        dots = points.map { Dot(it, if (it === anchor) 0 else 1, if (it === anchor) Emphasis.QUERY else Emphasis.NORMAL) },
    )

    val sorted = points.filter { it !== anchor }.sortedWith(
        compareBy(
            { kotlin.math.atan2(it.y - anchor.y, it.x - anchor.x) },
            { geoDist(anchor, it) },
        ),
    )
    frames += CloudFrame(
        status = "Sort the other eleven by polar angle around the anchor. This is the O(n log n) term — everything after it is linear.",
        dots = points.map { Dot(it, if (it === anchor) 0 else 1) },
        segments = sorted.map { Segment(anchor, it, UnassignedColor, dashed = true) },
    )

    val stack = mutableListOf(anchor)
    val discarded = mutableSetOf<P>()
    var pops = 0
    for (p in sorted) {
        while (stack.size >= 2 && geoCross(stack[stack.size - 2], stack.last(), p) <= 0f) {
            val popped = stack.removeAt(stack.lastIndex)
            discarded += popped
            pops++
            frames += CloudFrame(
                status = "The last two on the stack plus this point turn right, so the middle one bulges inward — pop it. " +
                    "Each point is popped at most once overall, which is why the scan is linear.",
                dots = dots(stack, p, discarded),
                segments = hullSegments(stack + p, closed = false, color = CloudColors[3]),
                readout = "popped: $pops",
            )
        }
        stack += p
        frames += CloudFrame(
            status = "Left turn — the chain stays convex, so push. Hull so far: ${stack.size} vertices.",
            dots = dots(stack, p, discarded),
            segments = hullSegments(stack, closed = false, color = CloudColors[0]),
        )
    }

    frames += CloudFrame(
        status = "Hull closed: ${stack.size} vertices from ${points.size} points, ${points.size - stack.size} discarded, $pops pops. " +
            "The sort cost O(n log n); the scan itself touched each point twice at most.",
        dots = dots(stack, null, discarded),
        segments = hullSegments(stack, closed = true, color = CloudColors[0]),
        readout = "h = ${stack.size}, n = ${points.size}",
    )

    // Jarvis march on the same points, summarised rather than stepped — the contrast is the cost model.
    val jarvisTests = stack.size * points.size
    frames += CloudFrame(
        status = "Jarvis march finds the same hull with no sort at all: from the leftmost point, repeatedly take the most " +
            "counter-clockwise point. That is one sweep per hull vertex — ${stack.size} × ${points.size} = $jarvisTests orientation tests here. " +
            "It wins when the hull is small and degrades to O(n²) when every point is on it.",
        dots = dots(stack, null, discarded),
        segments = hullSegments(stack, closed = true, color = CloudColors[2]),
        readout = "Graham O(n log n) · Jarvis O(n·h)",
    )
    return frames
}

private fun rotatingCalipersFrames(): List<CloudFrame> {
    val points = hullPoints
    val hull = grahamHull(points)
    val h = hull.size
    val frames = mutableListOf<CloudFrame>()

    fun dots(active: Set<P>) = points.map { p ->
        Dot(
            p,
            group = if (p in hull) 0 else 1,
            emphasis = when {
                p in active -> Emphasis.QUERY
                p in hull -> Emphasis.NORMAL
                else -> Emphasis.FADED
            },
        )
    }

    val outline = hullSegments(hull, closed = true, color = CloudColors[0])

    frames += CloudFrame(
        status = "The two farthest points of a set are always both hull vertices, so the ${points.size} points reduce to $h candidates " +
            "before any distance is measured.",
        dots = dots(emptySet()),
        segments = outline,
    )
    frames += CloudFrame(
        status = "Brute force would still compare all ${points.size * (points.size - 1) / 2} pairs. Calipers compare only antipodal " +
            "pairs, of which a convex $h-gon has O(h).",
        dots = dots(emptySet()),
        segments = outline,
        readout = "${points.size * (points.size - 1) / 2} pairs → O(h) pairs",
    )

    var best = 0f
    var bestPair = hull[0] to hull[1]
    var q = 1
    for (p in 0 until h) {
        val next = (p + 1) % h
        while (kotlin.math.abs(geoCross(hull[p], hull[next], hull[(q + 1) % h])) >
            kotlin.math.abs(geoCross(hull[p], hull[next], hull[q]))
        ) {
            q = (q + 1) % h
        }
        val edge = Segment(hull[p], hull[next], CloudColors[1])
        var improved = false
        for (candidate in listOf(hull[p], hull[next])) {
            val d = geoDist(candidate, hull[q])
            if (d > best) {
                best = d
                bestPair = candidate to hull[q]
                improved = true
            }
        }
        frames += CloudFrame(
            status = "Edge ${p + 1} of $h: rotate until the opposite caliper rests on the vertex farthest from it. " +
                if (improved) "This antipodal pair is the longest so far — record it."
                else "This pair is shorter than the best so far; keep walking.",
            dots = dots(setOf(hull[p], hull[next], hull[q])),
            segments = outline + edge + Segment(hull[p], hull[q], CloudColors[3], dashed = true),
            readout = "best so far ${"%.3f".format(best)}",
        )
    }

    frames += CloudFrame(
        status = "Diameter ${"%.3f".format(best)}. Both pointers went around the hull exactly once — the opposite vertex never " +
            "moves backwards, because the support function is unimodal on a convex polygon. That monotonicity is the whole saving, " +
            "and it is the reason the hull is a precondition rather than an optimisation.",
        dots = points.map { p ->
            Dot(p, if (p in hull) 0 else 1, if (p === bestPair.first || p === bestPair.second) Emphasis.QUERY else if (p in hull) Emphasis.NORMAL else Emphasis.FADED)
        },
        segments = outline + Segment(bestPair.first, bestPair.second, CloudColors[2]),
        readout = "diameter = ${"%.3f".format(best)}",
    )
    return frames
}

private fun polygonAreaFrames(): List<CloudFrame> {
    // A deliberately non-convex polygon: the reflex vertex is what shows the negative terms
    // cancelling rather than the formula only working on convex shapes.
    val poly = listOf(
        P(0.15f, 0.15f), P(0.85f, 0.15f), P(0.85f, 0.50f), P(0.50f, 0.35f), P(0.15f, 0.70f),
    )
    val frames = mutableListOf<CloudFrame>()
    val outline = hullSegments(poly, closed = true, color = CloudColors[0])
    val origin = P(0f, 0f)

    fun vertexDots(active: Int?) = poly.mapIndexed { i, p ->
        Dot(p, 0, if (i == active) Emphasis.QUERY else Emphasis.NORMAL)
    }

    frames += CloudFrame(
        status = "A simple polygon — no edge crosses another — but not a convex one. The shoelace formula does not care.",
        dots = vertexDots(null),
        segments = outline,
    )

    var sum = 0f
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[(i + 1) % poly.size]
        val term = a.x * b.y - b.x * a.y
        sum += term
        frames += CloudFrame(
            status = "Edge ${i + 1}: x${i + 1}·y${(i + 1) % poly.size + 1} − x${(i + 1) % poly.size + 1}·y${i + 1} = ${"%.4f".format(term)}. " +
                (if (term < 0) "Negative — this edge faces back toward the origin, and the overshoot will cancel." else "Positive — this edge sweeps away from the origin.") +
                " Running total ${"%.4f".format(sum)}.",
            dots = vertexDots(i),
            segments = outline + Segment(origin, a, UnassignedColor, dashed = true) + Segment(origin, b, UnassignedColor, dashed = true) +
                Segment(a, b, if (term < 0) CloudColors[3] else CloudColors[2]),
            readout = "Σ = ${"%.4f".format(sum)}",
        )
    }

    val perimeter = poly.indices.sumOf { i -> geoDist(poly[i], poly[(i + 1) % poly.size]).toDouble() }
    frames += CloudFrame(
        status = "Area = |Σ| / 2 = ${"%.4f".format(kotlin.math.abs(sum) / 2f)}. The sum came out positive, so these vertices are " +
            "listed counter-clockwise — that sign is free orientation information, and throwing it away with abs() too early is a common loss.",
        dots = vertexDots(null),
        segments = outline,
        readout = "area = ${"%.4f".format(kotlin.math.abs(sum) / 2f)} · CCW",
    )
    frames += CloudFrame(
        status = "Perimeter needs a separate pass and a square root per edge: ${"%.4f".format(perimeter)}. The cheaper-looking quantity " +
            "is the more expensive one.",
        dots = vertexDots(null),
        segments = outline,
        readout = "perimeter = ${"%.4f".format(perimeter)}",
    )
    return frames
}

private fun lineIntersectionFrames(): List<CloudFrame> {
    val frames = mutableListOf<CloudFrame>()

    fun orientation(a: P, b: P, c: P): Int {
        val v = geoCross(a, b, c)
        return when {
            v > 1e-6f -> 1
            v < -1e-6f -> -1
            else -> 0
        }
    }

    fun withinBox(a: P, b: P, c: P) =
        b.x >= minOf(a.x, c.x) - 1e-6f && b.x <= maxOf(a.x, c.x) + 1e-6f &&
            b.y >= minOf(a.y, c.y) - 1e-6f && b.y <= maxOf(a.y, c.y) + 1e-6f

    fun case(p1: P, p2: P, q1: P, q2: P, title: String, note: String) {
        val o1 = orientation(p1, p2, q1)
        val o2 = orientation(p1, p2, q2)
        val o3 = orientation(q1, q2, p1)
        val o4 = orientation(q1, q2, p2)
        val proper = o1 != o2 && o3 != o4
        val collinearHit = (o1 == 0 && withinBox(p1, q1, p2)) || (o2 == 0 && withinBox(p1, q2, p2)) ||
            (o3 == 0 && withinBox(q1, p1, q2)) || (o4 == 0 && withinBox(q1, p2, q2))
        val hit = proper || collinearHit

        frames += CloudFrame(
            status = "$title — orientations (${o1}, ${o2}, ${o3}, ${o4}). " +
                (if (proper) "Both segments straddle the other's line, so they cross. " else if (collinearHit) "The straddle test says no, but a zero orientation sends it to the containment check, which says yes. " else "No straddle and no collinear containment. ") +
                note,
            dots = listOf(
                Dot(p1, 0), Dot(p2, 0),
                Dot(q1, 1, Emphasis.ACTIVE), Dot(q2, 1, Emphasis.ACTIVE),
            ),
            segments = listOf(
                Segment(p1, p2, CloudColors[0]),
                Segment(q1, q2, if (hit) CloudColors[2] else CloudColors[3]),
            ),
            readout = if (hit) "intersects" else "no intersection",
        )
    }

    case(
        P(0.15f, 0.25f), P(0.85f, 0.75f), P(0.20f, 0.80f), P(0.80f, 0.20f),
        "Proper crossing",
        "This is the case the four cross products were designed for, and the only one they settle alone.",
    )
    case(
        P(0.15f, 0.20f), P(0.45f, 0.35f), P(0.60f, 0.70f), P(0.85f, 0.85f),
        "Clearly apart",
        "Two orientations agree on each test, so neither segment separates the other's endpoints.",
    )
    case(
        P(0.15f, 0.30f), P(0.55f, 0.30f), P(0.55f, 0.30f), P(0.85f, 0.70f),
        "Touching at an endpoint",
        "Whether this counts is a policy decision — for polygon clipping usually yes, for a self-intersection check on a closed outline usually no, since consecutive edges always share one.",
    )
    case(
        P(0.15f, 0.55f), P(0.65f, 0.55f), P(0.40f, 0.55f), P(0.90f, 0.55f),
        "Collinear and overlapping",
        "Every orientation is zero and the sign test is blind here; only the bounding-box containment check finds the overlap.",
    )
    case(
        P(0.15f, 0.85f), P(0.35f, 0.85f), P(0.60f, 0.85f), P(0.90f, 0.85f),
        "Collinear and disjoint",
        "All four orientations are zero again — the same input to the sign test, the opposite answer. The containment check is doing all the work.",
    )

    frames += CloudFrame(
        status = "Five cases, one primitive: the sign of a cross product, plus a bounding-box test when that sign is zero. " +
            "No division, no square roots, and on integer coordinates no rounding error at all — which is exactly why nothing here computes a slope.",
        dots = emptyList(),
        segments = emptyList(),
        readout = "4 cross products, O(1)",
    )
    return frames
}

// ── Dimensionality reduction (phase 9, batch B6) ─────────────────────────────
// Eight labs sharing one shape: show what the data looks like, show what a *linear* projection can
// and cannot do with it, then run the method and score the result. Every number these frames quote
// is computed by DimReductionMath at build time — the measured values are recorded in
// DimReductionMathTest, which fails if any of them moves.

private fun pct(v: Double) = "${"%.0f".format(v * 100)}%"

private fun axisSegment(centre: Pt, direction: DoubleArray, halfLength: Float, color: Color): Segment =
    Segment(
        P(centre.x - direction[0].toFloat() * halfLength, centre.y - direction[1].toFloat() * halfLength),
        P(centre.x + direction[0].toFloat() * halfLength, centre.y + direction[1].toFloat() * halfLength),
        color,
    )

private fun ringGroup(index: Int) = if (index < InnerRingCount) 0 else 1

private fun kernelPcaFrames(): List<CloudFrame> {
    val raw = concentricRings
    val plotted = fitToUnit(raw)
    val dots = plotted.mapIndexed { i, p -> Dot(p.toP(), ringGroup(i)) }
    val frames = mutableListOf<CloudFrame>()

    frames += CloudFrame(
        status = "Two concentric rings. There is one obvious feature here — distance from the centre — and it is not a " +
            "linear function of x and y, so no projection onto a straight axis can recover it.",
        dots = dots,
    )

    val eigen = jacobiEigen(covariance2(raw))
    val mean = meanOf(raw)
    val plotMean = fitToUnit(raw + listOf(mean)).last()
    val linearScores = DoubleArray(raw.size) { i ->
        (raw[i].x - mean.x) * eigen.vectors[0][0] + (raw[i].y - mean.y) * eigen.vectors[0][1]
    }
    val linearAccuracy = bestThresholdAccuracy(linearScores, InnerRingCount)
    val explained = eigen.values[0] / (eigen.values[0] + eigen.values[1])
    frames += CloudFrame(
        status = "Ordinary PCA first. The cloud is round, so PC1 explains only ${pct(explained)} of the variance — and " +
            "thresholding it labels the rings ${pct(linearAccuracy)} correctly, which is barely better than the " +
            "${pct(InnerRingCount.toDouble() / raw.size)} you get by calling everything one ring. PCA has not failed to " +
            "converge; the answer it is looking for does not exist in the space it searches.",
        dots = dots,
        centroids = listOf(Dot(plotMean.toP(), 2, Emphasis.ACTIVE)),
        segments = listOf(axisSegment(plotMean, eigen.vectors[0], 0.42f, AxisColor)),
        readout = "linear PC1 separates ${pct(linearAccuracy)}",
    )

    val gamma = 4.0
    // 1/√(2γ) is the RBF's own length scale — the radius at which similarity has dropped to e^(−½).
    val bandwidth = (1.0 / sqrt(2.0 * gamma)).toFloat()
    val scale = (plotted[1].x - plotted[0].x) / (raw[1].x - raw[0].x)
    frames += CloudFrame(
        status = "Kernel PCA replaces every inner product with k(xᵢ, xⱼ) = exp(−γ‖xᵢ−xⱼ‖²), which is the inner product " +
            "of some much higher-dimensional feature map — one nobody ever writes down. The circles show that kernel's " +
            "length scale at γ = ${"%.0f".format(gamma)}: a point is similar to its own ring's neighbours and to almost " +
            "nothing on the other ring.",
        dots = dots,
        rings = listOf(0, 8, InnerRingCount + 4, InnerRingCount + 15).map {
            Ring(plotted[it].toP(), bandwidth * abs(scale), QueryColor)
        },
    )

    val kp = kernelPca(raw, gamma, components = 2)
    val embedded = fitToUnit(raw.indices.map { Pt(kp.components[0][it].toFloat(), kp.components[1][it].toFloat()) })
    val kpcAccuracy = bestThresholdAccuracy(kp.components[0], InnerRingCount)
    frames += CloudFrame(
        status = "The Gram matrix is double-centred — K̃ = K − 1ₙK − K1ₙ + 1ₙK1ₙ, because the feature-space mean is not " +
            "at the origin and nothing else can move it there — and then eigendecomposed. Plotting the top two kernel " +
            "components: the rings come apart, and a single threshold on KPC1 now labels them ${pct(kpcAccuracy)} correctly.",
        dots = embedded.mapIndexed { i, p -> Dot(p.toP(), ringGroup(i)) },
        readout = "KPC1 separates ${pct(kpcAccuracy)}",
    )

    val tooSmall = kernelPca(raw, 0.5, components = 2)
    val tooSmallAccuracy = bestThresholdAccuracy(tooSmall.components[0], InnerRingCount)
    val smallEmbedded = fitToUnit(raw.indices.map { Pt(tooSmall.components[0][it].toFloat(), tooSmall.components[1][it].toFloat()) })
    frames += CloudFrame(
        status = "γ is not a detail. At γ = 0.5 the kernel is so wide that every point looks similar to every other, the " +
            "leading components go back to describing the overall shape, and KPC1 is down to ${pct(tooSmallAccuracy)} — " +
            "the radius information has moved into a later component instead of disappearing.",
        dots = smallEmbedded.mapIndexed { i, p -> Dot(p.toP(), ringGroup(i), Emphasis.FADED) },
        readout = "γ = 0.5 → KPC1 separates ${pct(tooSmallAccuracy)}",
    )

    val tooLarge = kernelPca(raw, 16.0, components = 2)
    val tooLargeAccuracy = bestThresholdAccuracy(tooLarge.components[0], InnerRingCount)
    val largeEmbedded = fitToUnit(raw.indices.map { Pt(tooLarge.components[0][it].toFloat(), tooLarge.components[1][it].toFloat()) })
    frames += CloudFrame(
        status = "At γ = 16 the kernel is narrower than the gap between neighbours on the same ring, so every point " +
            "becomes its own island and the spectrum flattens — ${pct(tooLargeAccuracy)}. Tuned, too wide and too narrow " +
            "all run the same code; the bandwidth is the model. And the price of all three is a ${raw.size}×${raw.size} " +
            "matrix, which is why kernel PCA does not scale the way PCA does.",
        dots = largeEmbedded.mapIndexed { i, p -> Dot(p.toP(), ringGroup(i), Emphasis.FADED) },
        readout = "γ = 16 → KPC1 separates ${pct(tooLargeAccuracy)}",
    )
    return frames
}

private fun incrementalPcaFrames(): List<CloudFrame> {
    val points = correlatedCloudPts
    val steps = incrementalPca(points, 4)
    val frames = mutableListOf<CloudFrame>()

    frames += CloudFrame(
        status = "Batch PCA wants the whole matrix at once: to form the covariance it needs every row, and to " +
            "eigendecompose it needs the covariance. On ${points.size} points that is nothing. On ten million rows of a " +
            "thousand features it is the reason the job does not fit.",
        dots = points.map { Dot(it.toP(), 0, Emphasis.FADED) },
    )

    steps.forEachIndexed { index, step ->
        val seen = step.seen
        frames += CloudFrame(
            status = "Batch ${index + 1}: $seen of ${points.size} rows have been through the accumulator. Nothing is " +
                "retained but a count, a running mean and a 2×2 sum of products — the rows themselves are gone. The " +
                "component from what has been seen so far sits ${"%.1f".format(step.degreesFromBatch)}° from the answer " +
                "full-batch PCA gives, and explains ${pct(step.explained)} of the variance seen so far.",
            dots = points.mapIndexed { i, p ->
                Dot(p.toP(), if (i < seen) 0 else -1, if (i < seen) Emphasis.NORMAL else Emphasis.FADED)
            },
            centroids = listOf(Dot(step.mean.toP(), 2, Emphasis.ACTIVE)),
            segments = listOf(
                axisSegment(step.mean, doubleArrayOf(step.axis.x.toDouble(), step.axis.y.toDouble()), 0.38f, AxisColor),
            ),
            readout = "$seen rows seen · ${"%.1f".format(step.degreesFromBatch)}° from batch PCA",
        )
    }

    val last = steps.last()
    frames += CloudFrame(
        status = "After the last batch the axis is ${"%.1f".format(last.degreesFromBatch)}° from the batch answer, because " +
            "an accumulated covariance over all the data *is* the batch covariance — this variant is exact, not " +
            "approximate. The memory it used was O(d²) in the number of features and did not depend on the number of " +
            "rows at all. scikit-learn's IncrementalPCA does the same job with a different mechanism, merging an SVD per " +
            "batch, which trades exactness for numerical stability on wide data.",
        dots = points.map { Dot(it.toP(), 0) },
        centroids = listOf(Dot(last.mean.toP(), 2, Emphasis.ACTIVE)),
        segments = listOf(
            axisSegment(last.mean, doubleArrayOf(last.axis.x.toDouble(), last.axis.y.toDouble()), 0.38f, AxisColor),
        ),
        readout = "exact · O(d²) memory, independent of n",
    )
    return frames
}

private fun svdFrames(): List<CloudFrame> {
    val points = correlatedCloudPts
    val mean = meanOf(points)
    val centered = points.map { Pt(it.x - mean.x, it.y - mean.y) }
    val svd = svd2(centered)
    val frames = mutableListOf<CloudFrame>()

    frames += CloudFrame(
        status = "Eighteen rows, two columns. PCA arrives at its axes through the covariance matrix; the SVD gets the same " +
            "axes straight out of the data matrix, as X = UΣVᵀ — a rotation, a scaling, and another rotation, which is " +
            "all any matrix ever does.",
        dots = points.map { Dot(it.toP(), 0) },
    )

    val v1 = svd.rightVectors[0]
    val v2 = svd.rightVectors[1]
    frames += CloudFrame(
        status = "The right singular vectors are the axes, and the singular values are how far the data reaches along " +
            "each: σ₁ = ${"%.3f".format(svd.singularValues[0])} against σ₂ = ${"%.3f".format(svd.singularValues[1])}. " +
            "On centred data these are exactly PCA's components, and σᵢ² = n·λᵢ — same answer, no covariance matrix formed.",
        dots = points.map { Dot(it.toP(), 0, Emphasis.FADED) },
        centroids = listOf(Dot(mean.toP(), 2, Emphasis.ACTIVE)),
        segments = listOf(
            axisSegment(mean, v1, 0.40f, AxisColor),
            axisSegment(mean, v2, 0.10f, UnassignedColor),
        ),
        readout = "σ = ${"%.3f".format(svd.singularValues[0])}, ${"%.3f".format(svd.singularValues[1])}",
    )

    val reconstructed = svd.rank1.map { Pt(it.x + mean.x, it.y + mean.y) }
    frames += CloudFrame(
        status = "Keep σ₁ and throw σ₂ away and you get the best rank-1 approximation of the matrix that exists. Not the " +
            "best anyone has found — the best there is: the Eckart–Young theorem says truncating the SVD is optimal in " +
            "Frobenius norm, and the error it leaves is exactly the norm of the discarded singular values.",
        dots = points.map { Dot(it.toP(), 0, Emphasis.FADED) },
        centroids = reconstructed.map { Dot(it.toP(), 2, Emphasis.ACTIVE) },
        segments = listOf(axisSegment(mean, v1, 0.40f, AxisColor)) +
            points.indices.map { Segment(points[it].toP(), reconstructed[it].toP(), UnassignedColor, dashed = true) },
        readout = "‖X − X₁‖ꜰ = ${"%.4f".format(svd.rank1Error)} = σ₂",
    )

    val energy = svd.singularValues[0] * svd.singularValues[0] /
        (svd.singularValues[0] * svd.singularValues[0] + svd.singularValues[1] * svd.singularValues[1])
    frames += CloudFrame(
        status = "Measured: the rank-1 error is ${"%.4f".format(svd.rank1Error)} and σ₂ is " +
            "${"%.4f".format(svd.singularValues[1])} — the same number, which is the theorem rather than a coincidence. " +
            "The kept component carries ${pct(energy)} of the squared Frobenius norm, and that ratio is what a scree plot " +
            "shows and what \"keep 95% of the variance\" means.",
        dots = reconstructed.map { Dot(it.toP(), 2) },
        segments = listOf(axisSegment(mean, v1, 0.40f, AxisColor)),
        readout = "rank 1 keeps ${pct(energy)} of the energy",
    )

    val uncentered = svd2(points)
    val centeredAngle = axisAngleDegrees(v1[0], v1[1])
    val uncenteredAngle = axisAngleDegrees(uncentered.rightVectors[0][0], uncentered.rightVectors[0][1])
    val meanAngle = axisAngleDegrees(mean.x.toDouble(), mean.y.toDouble())
    frames += CloudFrame(
        status = "The one trap. Run the SVD on the raw matrix instead of the centred one and v₁ swings from " +
            "${"%.1f".format(centeredAngle)}° to ${"%.1f".format(uncenteredAngle)}°, towards the direction of the mean at " +
            "${"%.1f".format(meanAngle)}° — because with the origin off to one side, the largest direction in the data is " +
            "simply where the data *is*. That is not a bug in the SVD; it is the difference between the SVD of a matrix " +
            "and PCA of a dataset, and it is why PCA centres first.",
        dots = points.map { Dot(it.toP(), 0) },
        centroids = listOf(Dot(mean.toP(), 2, Emphasis.ACTIVE)),
        segments = listOf(
            axisSegment(mean, v1, 0.40f, AxisColor),
            Segment(P(0f, 0f), P(uncentered.rightVectors[0][0].toFloat() * 0.9f, uncentered.rightVectors[0][1].toFloat() * 0.9f), QueryColor, dashed = true),
        ),
        readout = "centred ${"%.1f".format(centeredAngle)}° · raw ${"%.1f".format(uncenteredAngle)}°",
    )
    return frames
}

private fun icaFrames(): List<CloudFrame> {
    val raw = mixedSources
    val plotted = fitToUnit(raw)
    val frames = mutableListOf<CloudFrame>()

    frames += CloudFrame(
        status = "Two independent sources, each uniform on [−1, 1], observed only after mixing: every point is a different " +
            "blend of both. The give-away is the shape — independent uniforms make a square, and a linear mix of them " +
            "makes a parallelogram whose edges point along the mixing directions.",
        dots = plotted.map { Dot(it.toP(), 0) },
    )

    val pcaAxes = jacobiEigen(covariance2(raw)).vectors
    val centre = meanOf(plotted)
    val trueA = axisAngleDegrees(mixingMatrix[0][0], mixingMatrix[1][0])
    val trueB = axisAngleDegrees(mixingMatrix[0][1], mixingMatrix[1][1])
    frames += CloudFrame(
        status = "PCA cannot be the answer, for a structural reason rather than a numerical one: its components are " +
            "orthogonal by construction, and here they come out at ${"%.1f".format(axisAngleDegrees(pcaAxes[0][0], pcaAxes[0][1]))}° " +
            "and ${"%.1f".format(axisAngleDegrees(pcaAxes[1][0], pcaAxes[1][1]))}° — exactly 90° apart. The two directions " +
            "actually in the data are ${"%.1f".format(axisSeparation(trueA, trueB))}° apart. No amount of variance " +
            "maximisation reaches a non-orthogonal pair.",
        dots = plotted.map { Dot(it.toP(), 0, Emphasis.FADED) },
        segments = listOf(
            axisSegment(centre, pcaAxes[0], 0.40f, AxisColor),
            axisSegment(centre, pcaAxes[1], 0.28f, AxisColor),
        ),
        readout = "PCA axes 90° apart · true mixing ${"%.1f".format(axisSeparation(trueA, trueB))}°",
    )

    val ica = fastIca(raw)
    frames += CloudFrame(
        status = "ICA starts where PCA finishes. Whitening — PCA, then divide each component by its standard deviation — " +
            "leaves the cloud with identity covariance, so all the second-order structure is gone and what remains to be " +
            "found is a pure rotation. That is the entire reason ICA is tractable.",
        dots = fitToUnit(ica.whitened).map { Dot(it.toP(), 3) },
        readout = "whitened: covariance = I",
    )

    val recoveredA = axisAngleDegrees(ica.mixingDirections[0][0], ica.mixingDirections[0][1])
    val recoveredB = axisAngleDegrees(ica.mixingDirections[1][0], ica.mixingDirections[1][1])
    val errA = minOf(axisSeparation(recoveredA, trueA), axisSeparation(recoveredA, trueB))
    val errB = minOf(axisSeparation(recoveredB, trueA), axisSeparation(recoveredB, trueB))
    frames += CloudFrame(
        status = "FastICA finds that rotation by a fixed-point iteration on w ← E[zg(wᵀz)] − E[g′(wᵀz)]w with g = tanh, " +
            "which converged in ${ica.iterations} iterations here. Mapped back through the whitening, the recovered " +
            "directions land at ${"%.1f".format(recoveredA)}° and ${"%.1f".format(recoveredB)}° against true mixing " +
            "columns at ${"%.1f".format(trueA)}° and ${"%.1f".format(trueB)}° — off by ${"%.1f".format(errA)}° and " +
            "${"%.1f".format(errB)}°, and not orthogonal to each other.",
        dots = plotted.map { Dot(it.toP(), 0) },
        segments = listOf(
            axisSegment(centre, ica.mixingDirections[0], 0.42f, QueryColor),
            axisSegment(centre, ica.mixingDirections[1], 0.42f, QueryColor),
        ),
        readout = "recovered within ${"%.1f".format(maxOf(errA, errB))}°",
    )

    val mixedKurtosis = excessKurtosis(raw.map { it.x.toDouble() })
    val sourceKurtosis = excessKurtosis(ica.sources.map { it.x.toDouble() })
    frames += CloudFrame(
        status = "Why non-Gaussianity is the objective: a sum of independent variables is more Gaussian than its parts, so " +
            "the mixtures sit closer to a normal than the sources do. Measured on this data — excess kurtosis " +
            "${"%.2f".format(mixedKurtosis)} for the observed mixture against ${"%.2f".format(sourceKurtosis)} for the " +
            "recovered source, with a uniform's true value being −1.2. Maximising non-Gaussianity walks that backwards. " +
            "The catch is the corollary: if the sources really were Gaussian, ICA could not work at all, because a " +
            "rotation of a spherical Gaussian is the same spherical Gaussian.",
        dots = fitToUnit(ica.sources).map { Dot(it.toP(), 2) },
        readout = "kurtosis ${"%.2f".format(mixedKurtosis)} → ${"%.2f".format(sourceKurtosis)}",
    )
    return frames
}

private fun factorAnalysisFrames(): List<CloudFrame> {
    val fa = oneFactorAnalysis(factorSamples)
    val plotted = fitToUnit(factorSamples.map { Pt(it.x1.toFloat(), it.x2.toFloat()) })
    val frames = mutableListOf<CloudFrame>()

    val loadingBars = { values: DoubleArray, emphasised: Int ->
        values.mapIndexed { i, v -> ProfileBar(abs(v).toFloat(), i, if (i == emphasised) Emphasis.ACTIVE else Emphasis.NORMAL) }
    }

    frames += CloudFrame(
        status = "Three measured variables generated from one hidden factor, plotted here as x₁ against x₂. Variable 2 was " +
            "given a large private noise term, so it carries the factor faintly and its own idiosyncrasy loudly. That is " +
            "the situation factor analysis was invented for — and the situation PCA has no vocabulary for.",
        dots = plotted.map { Dot(it.toP(), 0) },
        profile = fa.correlation.indices.map { ProfileBar(1f, it) },
        profileLabel = "x₁, x₂, x₃",
    )

    frames += CloudFrame(
        status = "The observed correlations: r₁₂ = ${"%.2f".format(fa.correlation[0][1])}, " +
            "r₁₃ = ${"%.2f".format(fa.correlation[0][2])}, r₂₃ = ${"%.2f".format(fa.correlation[1][2])}. Both models are " +
            "trying to explain these three numbers with one underlying quantity. They differ in what else they are " +
            "obliged to explain.",
        dots = plotted.map { Dot(it.toP(), 0, Emphasis.FADED) },
        profile = listOf(
            ProfileBar(fa.correlation[0][1].toFloat(), 0),
            ProfileBar(fa.correlation[0][2].toFloat(), 1),
            ProfileBar(fa.correlation[1][2].toFloat(), 2),
        ),
        profileLabel = "r₁₂, r₁₃, r₂₃",
    )

    frames += CloudFrame(
        status = "PCA's first component has no noise term, so it has to account for the whole diagonal as well: its " +
            "loadings come out ${fa.pcaLoadings.joinToString { "%.2f".format(it) }}, which claim to explain " +
            "${pct(fa.pcaLoadings[0] * fa.pcaLoadings[0])}, ${pct(fa.pcaLoadings[1] * fa.pcaLoadings[1])} and " +
            "${pct(fa.pcaLoadings[2] * fa.pcaLoadings[2])} of each variable's variance. Every one of those is inflated, " +
            "because some of that variance is private to the variable and shared with nothing.",
        dots = plotted.map { Dot(it.toP(), 0, Emphasis.FADED) },
        profile = loadingBars(fa.pcaLoadings, 1),
        profileLabel = "PCA loadings",
    )

    frames += CloudFrame(
        status = "Factor analysis writes xᵢ = λᵢf + εᵢ and fits the uniquenesses ψᵢ alongside the loadings. It gives " +
            "variable 2 a loading of ${"%.2f".format(fa.loadings[1])} and hands ${pct(fa.uniquenesses[1])} of its variance " +
            "to ε — that variable is mostly its own noise, and the model is allowed to say so. With three variables and " +
            "one factor the system is exactly identified, so this comes out of r₁₂r₁₃/r₂₃ and its two rotations in closed " +
            "form, with nothing to converge.",
        dots = plotted.map { Dot(it.toP(), 0) },
        profile = fa.uniquenesses.mapIndexed { i, v -> ProfileBar(v.toFloat(), i, if (i == 1) Emphasis.ACTIVE else Emphasis.NORMAL) },
        profileLabel = "uniqueness ψ per variable",
    )

    var faResidual = 0.0
    var pcaResidual = 0.0
    for (i in 0..2) for (j in 0..2) {
        if (i == j) continue
        faResidual += abs(fa.loadings[i] * fa.loadings[j] - fa.correlation[i][j])
        pcaResidual += abs(fa.pcaLoadings[i] * fa.pcaLoadings[j] - fa.correlation[i][j])
    }
    frames += CloudFrame(
        status = "Score them on the thing they are both modelling — the off-diagonal correlations. Factor analysis " +
            "reproduces them with total absolute error ${"%.4f".format(faResidual)}; PCA's rank-1 reconstruction is off by " +
            "${"%.3f".format(pcaResidual)}, because its component is being pulled towards explaining variance that is not " +
            "shared. This is the whole difference: PCA summarises total variance, factor analysis models common variance.",
        dots = plotted.map { Dot(it.toP(), 0) },
        profile = loadingBars(fa.loadings, 1),
        profileLabel = "FA loadings",
        readout = "off-diagonal error: FA ${"%.4f".format(faResidual)} · PCA ${"%.3f".format(pcaResidual)}",
    )

    frames += CloudFrame(
        status = "One honest caveat, measured on this sample: the true loadings were " +
            "${trueLoadings.joinToString { "%.2f".format(it) }}, and factor analysis estimated variable 2's as " +
            "${"%.2f".format(fa.loadings[1])} while PCA happened to land on ${"%.2f".format(fa.pcaLoadings[1])} — closer. " +
            "An exactly-identified one-factor solution divides small correlations by each other, which is a " +
            "high-variance estimator. Factor analysis is the right *model* here; that does not make it the better " +
            "estimate of every parameter on every sample, and a run that claimed otherwise would be describing the " +
            "textbook rather than the data.",
        dots = plotted.map { Dot(it.toP(), 0) },
        profile = loadingBars(trueLoadings, 1),
        profileLabel = "true loadings",
        readout = "λ₂: true ${"%.2f".format(trueLoadings[1])} · FA ${"%.2f".format(fa.loadings[1])} · PCA ${"%.2f".format(fa.pcaLoadings[1])}",
    )
    return frames
}

private fun tsneFrames(): List<CloudFrame> {
    val input = unevenClusters
    val steps = tsne(input)
    val frames = mutableListOf<CloudFrame>()

    val inputSeparation = separationRatio(input, ::unevenClusterLabel)
    frames += CloudFrame(
        status = "Three clusters, deliberately unequal: two tight ones sitting close together, and a loose one far away. " +
            "The distances here are real — between-cluster distance is ${"%.1f".format(inputSeparation)}× the " +
            "within-cluster distance — and the question is which of that survives an embedding.",
        dots = input.mapIndexed { i, p -> Dot(p.toP(), unevenClusterLabel(i)) },
        readout = "input separation ratio ${"%.2f".format(inputSeparation)}",
    )

    val affinities = tsneAffinities(input, 8.0)
    val strongPairs = mutableListOf<Segment>()
    val threshold = affinities.flatMap { it.toList() }.sortedDescending()[input.size * 4]
    input.indices.forEach { i ->
        (i + 1 until input.size).forEach { j ->
            if (affinities[i][j] >= threshold) strongPairs += Segment(input[i].toP(), input[j].toP(), UnassignedColor, dashed = true)
        }
    }
    frames += CloudFrame(
        status = "t-SNE first turns distances into probabilities: pⱼ|ᵢ is how likely point i is to pick j as a neighbour, " +
            "under a Gaussian whose width is solved per point by binary search so that every neighbourhood has the same " +
            "entropy. That is what perplexity sets, and it is why a dense cluster and a sparse one are treated on equal " +
            "terms — each point's σ adapts to its own surroundings.",
        dots = input.mapIndexed { i, p -> Dot(p.toP(), unevenClusterLabel(i)) },
        segments = strongPairs,
        readout = "perplexity 8 · ${strongPairs.size} strongest pairs shown",
    )

    steps.drop(1).forEach { step ->
        val phase = if (step.iteration <= 80) {
            "Early exaggeration is on: P is multiplied by 4 for the first 80 iterations, which forces clusters apart " +
                "before the repulsion has anything to push against. KL measured against the *un*-exaggerated P is " +
                "${"%.2f".format(step.klDivergence)}, and it is allowed to rise here — the objective being optimised is " +
                "not the one being reported."
        } else {
            "Exaggeration off, momentum up. Gradient descent on KL(P‖Q), where Q uses a Student-t with one degree of " +
                "freedom — the heavy tail is what lets moderately-distant points sit far apart in the map without " +
                "paying much, and it is the fix for the crowding problem. KL = ${"%.2f".format(step.klDivergence)}."
        }
        frames += CloudFrame(
            status = "Iteration ${step.iteration}. $phase",
            dots = step.embedding.mapIndexed { i, p -> Dot(p.toP(), unevenClusterLabel(i)) },
            readout = "iteration ${step.iteration} · KL ${"%.2f".format(step.klDivergence)}",
        )
    }

    val out = steps.last().embedding
    val preservation = neighbourPreservation(input, out)
    fun spread(points: List<Pt>, cluster: Int): Double {
        val members = points.indices.filter { unevenClusterLabel(it) == cluster }
        val cx = members.map { points[it].x }.average().toFloat()
        val cy = members.map { points[it].y }.average().toFloat()
        return members.map { dist(points[it], Pt(cx, cy)).toDouble() }.average()
    }
    fun gap(points: List<Pt>, a: Int, b: Int): Double {
        fun centre(c: Int): Pt {
            val members = points.indices.filter { unevenClusterLabel(it) == c }
            return Pt(members.map { points[it].x }.average().toFloat(), members.map { points[it].y }.average().toFloat())
        }
        return dist(centre(a), centre(b)).toDouble()
    }
    val inputRatio = gap(input, 0, 2) / gap(input, 0, 1)
    val outputRatio = gap(out, 0, 2) / gap(out, 0, 1)
    frames += CloudFrame(
        status = "It found the three groups and kept ${pct(preservation)} of each point's five nearest neighbours. Now the " +
            "part every t-SNE plot gets over-read: the tight clusters went from a mean spread of " +
            "${"%.3f".format(spread(input, 0))} and ${"%.3f".format(spread(input, 1))} to " +
            "${"%.3f".format(spread(out, 0))} and ${"%.3f".format(spread(out, 1))}, while the loose one *shrank* from " +
            "${"%.3f".format(spread(input, 2))} to ${"%.3f".format(spread(out, 2))}. Cluster size in a t-SNE map carries " +
            "no information.",
        dots = out.mapIndexed { i, p -> Dot(p.toP(), unevenClusterLabel(i)) },
        readout = "${pct(preservation)} of 5-NN preserved",
    )

    frames += CloudFrame(
        status = "Nor does the distance between clusters. In the input, cluster 2 is ${"%.1f".format(inputRatio)}× further " +
            "from cluster 0 than cluster 1 is; in the map that ratio has collapsed to ${"%.1f".format(outputRatio)}×, and " +
            "the overall separation ratio went from ${"%.2f".format(inputSeparation)} to " +
            "${"%.2f".format(separationRatio(out, ::unevenClusterLabel))}. t-SNE optimises a neighbourhood objective and " +
            "makes no promise about anything else — which is exactly what it is honest about and what readers of its " +
            "output usually are not.",
        dots = out.mapIndexed { i, p -> Dot(p.toP(), unevenClusterLabel(i)) },
        readout = "gap ratio ${"%.1f".format(inputRatio)}× → ${"%.1f".format(outputRatio)}×",
    )
    return frames
}

private fun umapFrames(): List<CloudFrame> {
    val input = unevenClusters
    val result = umapLayout(input)
    val frames = mutableListOf<CloudFrame>()

    frames += CloudFrame(
        status = "The same three uneven clusters t-SNE was run on, so the two methods can be compared on identical points " +
            "rather than on identical claims.",
        dots = input.mapIndexed { i, p -> Dot(p.toP(), unevenClusterLabel(i)) },
    )

    val edges = mutableListOf<Segment>()
    input.indices.forEach { i ->
        (i + 1 until input.size).forEach { j ->
            if (result.graph[i][j] > 0.55) edges += Segment(input[i].toP(), input[j].toP(), UnassignedColor, dashed = true)
        }
    }
    frames += CloudFrame(
        status = "UMAP builds a weighted k-NN graph, and two details in how it does that are the whole difference from " +
            "t-SNE. First, ρᵢ — the distance to the nearest neighbour — is subtracted before the exponential, so every " +
            "point is connected to something with weight 1 and no point is ever left isolated by a density change. " +
            "Second, σᵢ is solved so the weights sum to log₂k rather than to a fixed entropy.",
        dots = input.mapIndexed { i, p -> Dot(p.toP(), unevenClusterLabel(i)) },
        segments = edges,
        readout = "${edges.size} edges above weight 0.55",
    )

    frames += CloudFrame(
        status = "The two directed weights are then combined as a + b − ab: an edge survives if *either* endpoint counts " +
            "the other as a neighbour. That union is why the graph stays connected across a density change, and it is the " +
            "\"fuzzy simplicial set\" the paper's topology language is describing.",
        dots = input.mapIndexed { i, p -> Dot(p.toP(), unevenClusterLabel(i), Emphasis.FADED) },
        segments = edges,
    )

    result.snapshots.forEach { (epoch, layout) ->
        frames += CloudFrame(
            status = "Layout epoch $epoch. The optimisation is attraction along graph edges and repulsion between " +
                "everything else — force-directed, on a cross-entropy objective rather than a KL divergence. The real " +
                "implementation samples the repulsive term; with ${input.size} points it is computed exactly here, so " +
                "there is no sampling noise in what you are watching.",
            dots = layout.mapIndexed { i, p -> Dot(p.toP(), unevenClusterLabel(i)) },
            readout = "epoch $epoch of 400",
        )
    }

    val out = result.embedding
    val umapPreservation = neighbourPreservation(input, out)
    val tsneOut = tsne(input).last().embedding
    val tsnePreservation = neighbourPreservation(input, tsneOut)
    val inputSeparation = separationRatio(input, ::unevenClusterLabel)
    val umapSeparation = separationRatio(out, ::unevenClusterLabel)
    val tsneSeparation = separationRatio(tsneOut, ::unevenClusterLabel)
    frames += CloudFrame(
        status = "Scored against t-SNE on the same input: UMAP kept ${pct(umapPreservation)} of each point's five nearest " +
            "neighbours to t-SNE's ${pct(tsnePreservation)}, and its between/within separation ratio is " +
            "${"%.2f".format(umapSeparation)} against t-SNE's ${"%.2f".format(tsneSeparation)} — with the input's own " +
            "ratio being ${"%.2f".format(inputSeparation)}. The global arrangement survived here and did not there, which " +
            "is the claim usually made for UMAP, and it owes as much to the informed initialisation as to the objective.",
        dots = out.mapIndexed { i, p -> Dot(p.toP(), unevenClusterLabel(i)) },
        readout = "5-NN kept: UMAP ${pct(umapPreservation)} · t-SNE ${pct(tsnePreservation)}",
    )

    fun spread(points: List<Pt>, cluster: Int): Double {
        val members = points.indices.filter { unevenClusterLabel(it) == cluster }
        val cx = members.map { points[it].x }.average().toFloat()
        val cy = members.map { points[it].y }.average().toFloat()
        return members.map { dist(points[it], Pt(cx, cy)).toDouble() }.average()
    }
    frames += CloudFrame(
        status = "The limit is worth stating as plainly as the win. Cluster spreads in this layout came out " +
            "${"%.3f".format(spread(out, 0))}, ${"%.3f".format(spread(out, 1))} and ${"%.3f".format(spread(out, 2))}, " +
            "against input spreads of ${"%.3f".format(spread(input, 0))}, ${"%.3f".format(spread(input, 1))} and " +
            "${"%.3f".format(spread(input, 2))} — the loose cluster is drawn as the *tightest* one. UMAP holds on to more " +
            "of the global arrangement than t-SNE does; neither preserves relative cluster size, and reading either map " +
            "as a metric space is a mistake.",
        dots = out.mapIndexed { i, p -> Dot(p.toP(), unevenClusterLabel(i)) },
        readout = "relative cluster size: still not preserved",
    )
    return frames
}

private fun lleFrames(): List<CloudFrame> {
    val curve = spiralCurve
    val frames = mutableListOf<CloudFrame>()
    val order = DoubleArray(curve.size) { it.toDouble() }
    val curveSegments = (0 until curve.size - 1).map {
        Segment(curve[it].toP(), curve[it + 1].toP(), UnassignedColor, dashed = true)
    }

    frames += CloudFrame(
        status = "Forty points sampled along a spiral. They live in two dimensions but the thing that generated them has " +
            "one — position along the curve — and recovering that parameter is the whole job. The dashed line is the true " +
            "order, shown here and never given to the algorithm.",
        dots = curve.map { Dot(it.toP(), 0) },
        segments = curveSegments,
    )

    val k = 4
    val result = lle(curve, k)
    val neighbourSegments = result.neighbours.flatMapIndexed { i, nb ->
        nb.map { Segment(curve[i].toP(), curve[it].toP(), CloudColors[2], dashed = false) }
    }
    val shortCircuits = result.neighbours.withIndex().sumOf { (i, nb) -> nb.count { abs(it - i) > 3 } }
    frames += CloudFrame(
        status = "Step 1: each point's $k nearest neighbours. This is the only place the geometry enters, and the " +
            "assumption behind the whole method — that a small enough patch of a curved manifold is flat enough to treat " +
            "as linear. On this spiral $shortCircuits of the ${result.neighbours.size * k} links reach more than three " +
            "positions along the curve.",
        dots = curve.map { Dot(it.toP(), 0) },
        segments = neighbourSegments,
        readout = "k = $k · $shortCircuits short-circuit links",
    )

    val focus = 20
    val weights = result.neighbours[focus].map { result.weights[focus][it] }
    frames += CloudFrame(
        status = "Step 2: rebuild each point from its neighbours. Solve for the weights that minimise " +
            "‖xᵢ − Σⱼwᵢⱼxⱼ‖² subject to Σⱼwᵢⱼ = 1 — for the highlighted point they come out " +
            "${weights.joinToString { "%.2f".format(it) }}. The sum-to-one constraint is what makes those weights " +
            "invariant to translation, rotation and scaling of the patch, and that invariance is what lets them be " +
            "carried into a completely different space.",
        dots = curve.mapIndexed { i, p ->
            Dot(
                p.toP(),
                if (i == focus) 1 else 0,
                when {
                    i == focus -> Emphasis.QUERY
                    i in result.neighbours[focus] -> Emphasis.ACTIVE
                    else -> Emphasis.FADED
                },
            )
        },
        segments = result.neighbours[focus].map { Segment(curve[focus].toP(), curve[it].toP(), QueryColor) },
        readout = "Σw = ${"%.2f".format(weights.sum())}",
    )

    fun lineLayout(values: DoubleArray): List<Pt> {
        val lo = values.min()
        val hi = values.max()
        val span = if (hi - lo < 1e-9) 1.0 else hi - lo
        return values.map { Pt((0.08 + 0.84 * (it - lo) / span).toFloat(), 0.5f) }
    }

    val lleLine = lineLayout(result.embedding)
    val lleRho = absSpearman(result.embedding, order)
    frames += CloudFrame(
        status = "Step 3: find the coordinates that those same weights reconstruct as well as possible — the bottom " +
            "eigenvectors of M = (I−W)ᵀ(I−W). The very bottom one is the constant vector and carries nothing, so the " +
            "embedding is the next one up. Laid out on a line, with the dashed links joining points that were adjacent on " +
            "the spiral: rank correlation with the true parameter is ${"%.2f".format(lleRho)}.",
        dots = lleLine.map { Dot(it.toP(), 2) },
        segments = (0 until curve.size - 1).map {
            Segment(lleLine[it].toP(), lleLine[it + 1].toP(), UnassignedColor, dashed = true)
        },
        readout = "LLE ρ = ${"%.2f".format(lleRho)}",
    )

    val mean = meanOf(curve)
    val pc1 = jacobiEigen(covariance2(curve)).vectors[0]
    val projection = DoubleArray(curve.size) { i ->
        (curve[i].x - mean.x) * pc1[0] + (curve[i].y - mean.y) * pc1[1]
    }
    val pcaLine = lineLayout(projection)
    val pcaRho = absSpearman(projection, order)
    frames += CloudFrame(
        status = "PCA on the same points, laid out the same way, scores ${"%.2f".format(pcaRho)} — the links cross each " +
            "other because a straight projection folds the spiral onto itself, mapping the inner turn and the outer turn " +
            "to the same coordinate. No rotation fixes that; the structure is not linear, and a linear method has nothing " +
            "to offer it.",
        dots = pcaLine.map { Dot(it.toP(), 1) },
        segments = (0 until curve.size - 1).map {
            Segment(pcaLine[it].toP(), pcaLine[it + 1].toP(), UnassignedColor, dashed = true)
        },
        readout = "PCA ρ = ${"%.2f".format(pcaRho)} vs LLE ${"%.2f".format(lleRho)}",
    )

    val broken = lle(curve, 6)
    val brokenLine = lineLayout(broken.embedding)
    val brokenRho = absSpearman(broken.embedding, order)
    val brokenShortCircuits = broken.neighbours.withIndex().sumOf { (i, nb) -> nb.count { abs(it - i) > 3 } }
    frames += CloudFrame(
        status = "And the failure mode, because k is not a tuning detail. At k = 6 the neighbourhoods start reaching " +
            "across the gap between turns — $brokenShortCircuits short-circuit links against $shortCircuits at k = $k — " +
            "the reconstruction weights stop describing a curve, and the recovered ordering falls apart at " +
            "ρ = ${"%.2f".format(brokenRho)}. Every manifold method has this failure; it is the price of deciding what " +
            "\"nearby\" means before you know the shape.",
        dots = brokenLine.map { Dot(it.toP(), 1, Emphasis.FADED) },
        segments = (0 until curve.size - 1).map {
            Segment(brokenLine[it].toP(), brokenLine[it + 1].toP(), UnassignedColor, dashed = true)
        },
        readout = "k = 6 → ρ = ${"%.2f".format(brokenRho)}",
    )
    return frames
}

// ── D1 · Cosine similarity: four documents in a two-term space ───────────────

private const val CosOrigin = 0.05f
private const val CosScale = 0.12f

private fun cosPoint(v: List<Double>) = P(CosOrigin + CosScale * v[0].toFloat(), CosOrigin + CosScale * v[1].toFloat())

private fun cosUnit(v: List<Double>, radius: Float = 0.5f): P {
    val n = SimilarityLab.norm(v).toFloat()
    return P(CosOrigin + radius * (v[0].toFloat() / n), CosOrigin + radius * (v[1].toFloat() / n))
}

private fun cosineFrames(): List<CloudFrame> {
    val frames = mutableListOf<CloudFrame>()
    val origin = P(CosOrigin, CosOrigin)
    val names = SimilarityLab.vectors.keys.toList()
    val groupOf = names.withIndex().associate { (i, name) -> name to if (name == "query") -1 else i }

    fun dotsFor(emphasis: Map<String, Emphasis> = emptyMap()) = names.map { name ->
        Dot(
            cosPoint(SimilarityLab.vectors.getValue(name)),
            groupOf.getValue(name),
            emphasis[name] ?: if (name == "query") Emphasis.QUERY else Emphasis.NORMAL,
        )
    }

    fun arrows(vararg which: String) = which.map { name ->
        Segment(origin, cosPoint(SimilarityLab.vectors.getValue(name)), groupColor(groupOf.getValue(name)))
    }

    frames += CloudFrame(
        status = "Four documents in a space with one axis per term — \"data\" across, \"model\" up. A document is " +
            "a point, and \"similar\" has to become a number computed from two of them. Real spaces have one " +
            "dimension per vocabulary entry; two is only so the geometry is visible.",
        dots = dotsFor(),
        segments = arrows(*names.toTypedArray()),
        readout = names.joinToString(" · ") { "$it ${SimilarityLab.vectors.getValue(it).map { v -> v.toInt() }}" },
    )

    frames += CloudFrame(
        status = "\"long\" is \"short\" with every sentence written twice: same term mix, twice the length. " +
            "Euclidean distance calls them ${"%.2f".format(SimilarityLab.euclidean("short", "long"))} apart — as far apart as \"short\" is from the origin. " +
            "Any metric that measures length is going to say a long document and its own summary are different " +
            "documents.",
        dots = dotsFor(mapOf("short" to Emphasis.ACTIVE, "long" to Emphasis.ACTIVE, "theory" to Emphasis.FADED, "query" to Emphasis.FADED)),
        segments = arrows("short", "long") +
            Segment(cosPoint(SimilarityLab.vectors.getValue("short")), cosPoint(SimilarityLab.vectors.getValue("long")), QueryColor, dashed = true),
        readout = "‖short − long‖ = ${"%.2f".format(SimilarityLab.euclidean("short", "long"))}",
    )

    frames += CloudFrame(
        status = "Cosine measures the angle instead: cos θ = (a · b) / (‖a‖‖b‖). The two vectors lie on the same " +
            "ray, so the angle is 0 and the similarity is exactly ${"%.2f".format(SimilarityLab.cosine("short", "long"))} — scaling a document cannot change it, " +
            "because both the dot product and the norms scale with it.",
        dots = dotsFor(mapOf("short" to Emphasis.ACTIVE, "long" to Emphasis.ACTIVE, "theory" to Emphasis.FADED, "query" to Emphasis.FADED)),
        segments = arrows("short", "long"),
        readout = "cos(short, long) = ${"%.2f".format(SimilarityLab.cosine("short", "long"))} · θ = 0°",
    )

    val theta = Math.toDegrees(kotlin.math.acos(SimilarityLab.cosine("short", "theory")))
    frames += CloudFrame(
        status = "A document with the opposite emphasis sits at a real angle. cos(short, theory) = " +
            "${"%.2f".format(SimilarityLab.cosine("short", "theory"))}, which is ${"%.1f".format(theta)}° — and because term counts are never negative, every angle " +
            "in this space is between 0° and 90°, so cosine similarity over raw counts is bounded to [0, 1]. " +
            "Embeddings have negative components and do use the full [−1, 1].",
        dots = dotsFor(mapOf("short" to Emphasis.ACTIVE, "theory" to Emphasis.ACTIVE, "long" to Emphasis.FADED, "query" to Emphasis.FADED)),
        segments = arrows("short", "theory"),
        readout = "cos = ${"%.2f".format(SimilarityLab.cosine("short", "theory"))} · θ = ${"%.1f".format(theta)}°",
    )

    val byCos = SimilarityLab.rankByCosine()
    val byEuc = SimilarityLab.rankByEuclidean()
    frames += CloudFrame(
        status = "Now rank the corpus against a query. By cosine: ${byCos.joinToString(" > ")}. By Euclidean " +
            "distance: ${byEuc.joinToString(" > ")}. The orders disagree — \"long\", which is the best match by " +
            "topic, ranks last by distance purely because it is long. This is the flip that makes cosine the " +
            "default in retrieval.",
        dots = dotsFor(mapOf("query" to Emphasis.QUERY)),
        segments = arrows("query", "short", "long", "theory"),
        rings = listOf(Ring(cosPoint(SimilarityLab.vectors.getValue("query")), CosScale * 2.5f, QueryColor)),
        readout = "cosine ${byCos.joinToString(" > ")} · euclid ${byEuc.joinToString(" > ")}",
    )

    frames += CloudFrame(
        status = "Which is the same as saying: normalise every vector to unit length first, and cosine *becomes* " +
            "the dot product — one multiply-add per dimension, no square roots at query time. Vector databases " +
            "store L2-normalised embeddings and run inner-product search for exactly this reason.",
        dots = names.map { Dot(cosUnit(SimilarityLab.vectors.getValue(it)), groupOf.getValue(it), if (it == "query") Emphasis.QUERY else Emphasis.NORMAL) },
        segments = names.map { Segment(origin, cosUnit(SimilarityLab.vectors.getValue(it)), groupColor(groupOf.getValue(it))) },
        rings = listOf(Ring(origin, 0.5f, AxisColor)),
        readout = "‖v‖ = 1 for all · cos(a, b) = a · b",
    )

    frames += CloudFrame(
        status = "The cost of that invariance: cosine cannot tell a three-word note from a three-thousand-word " +
            "report on the same mix of terms, and length is sometimes the signal — in spam scoring, in " +
            "summarisation, in duplicate detection. Cosine on raw counts also over-rewards frequent words, " +
            "which is why the pairing is always tf-idf weights *then* cosine, not counts then cosine.",
        dots = dotsFor(mapOf("short" to Emphasis.ACTIVE, "long" to Emphasis.ACTIVE)),
        segments = arrows("short", "long"),
        readout = "cos = ${"%.2f".format(SimilarityLab.cosine("short", "long"))} whatever the length",
    )
    return frames
}

// ── D3 · GloVe: global co-occurrence, then the space it produces ─────────────

private fun gloveFrames(): List<CloudFrame> {
    val frames = mutableListOf<CloudFrame>()
    val ratios = GloveLab.probeWords.map { it to GloveLab.ratio(it) }

    frames += CloudFrame(
        status = "GloVe starts from a complaint about word2vec: it learns from local windows one at a time and " +
            "never looks at the corpus-wide counts directly. The paper's argument is that the information lives " +
            "in *ratios* of co-occurrence probabilities. P(solid|ice)/P(solid|steam) = ${"%.1f".format(ratios[0].second)} and " +
            "P(gas|ice)/P(gas|steam) = ${"%.2f".format(ratios[1].second)} — the ratio says which word discriminates, where the raw " +
            "probabilities are both tiny and say nothing.",
        dots = emptyList(),
        profile = ratios.map { ProfileBar((it.second.toFloat() / 9f).coerceIn(0.02f, 1f), if (it.second > 1.5) 0 else if (it.second < 0.5) 1 else 2) },
        profileLabel = "P(w|ice) / P(w|steam): " + ratios.joinToString("  ") { "${it.first} ${"%.2f".format(it.second)}" },
        readout = "words related to neither — water ${"%.2f".format(ratios[2].second)}, fashion ${"%.2f".format(ratios[3].second)} — sit at 1",
    )

    frames += CloudFrame(
        status = "So the model is fitted to the counts themselves. X is built once by sweeping the corpus with a " +
            "±${GloveLab.window} window, incrementing by 1/distance so a neighbour counts more than a word five away. On this " +
            "corpus that is ${GloveLab.nonZeroEntries()} non-zero cells out of ${GloveLab.totalCells()} — ${"%.0f".format(GloveLab.sparsity() * 100)}% of the matrix is empty, and only the " +
            "non-zero entries are ever visited.",
        dots = emptyList(),
        profile = listOf("the king", "king kingdom", "king crown", "king dog").map { pair ->
            val (a, b) = pair.split(" ")
            ProfileBar((GloveLab.cooccur(a, b) / 6f).coerceIn(0.01f, 1f), if (GloveLab.cooccur(a, b) > 0f) 0 else 1)
        },
        profileLabel = "X(i,j): the·king ${"%.2f".format(GloveLab.cooccur("the", "king"))}  king·kingdom ${"%.2f".format(GloveLab.cooccur("king", "kingdom"))}  king·crown ${"%.2f".format(GloveLab.cooccur("king", "crown"))}  king·dog ${"%.2f".format(GloveLab.cooccur("king", "dog"))}",
        readout = "${GloveLab.nonZeroEntries()} / ${GloveLab.totalCells()} cells non-zero",
    )

    frames += CloudFrame(
        status = "The objective is weighted least squares on log X: (wᵢ·w̃ⱼ + bᵢ + b̃ⱼ − log Xᵢⱼ)², times a weight " +
            "f(X) = (X/${GloveLab.xMax.toInt()})^${GloveLab.alpha} that caps at 1. The weighting is the design: without it, the handful of " +
            "\"the\" pairs would dominate every gradient. Here X(the, king) = ${"%.2f".format(GloveLab.cooccur("the", "king"))} gets weight ${"%.2f".format(GloveLab.weight(GloveLab.cooccur("the", "king")))} while " +
            "X(king, crown) = ${"%.2f".format(GloveLab.cooccur("king", "crown"))} gets ${"%.2f".format(GloveLab.weight(GloveLab.cooccur("king", "crown")))} — frequent pairs count more, but sub-linearly.",
        dots = emptyList(),
        profile = listOf(0.5f, 2f, 5f, 10f, 20f).map { ProfileBar(GloveLab.weight(it), 0) },
        profileLabel = "f(X) at X = 0.5, 2, 5, 10, 20 — flat once X ≥ ${GloveLab.xMax.toInt()}",
        readout = "zero cells contribute nothing at all",
    )

    val model = GloveLab.model
    frames += CloudFrame(
        status = "Fitted by SGD over the ${GloveLab.nonZeroEntries()} non-zero entries for ${GloveLab.epochs} epochs; weighted loss falls from " +
            "${"%.3f".format(model.losses.first())} to ${"%.5f".format(model.losses.last())}. Note what this is *not*: there is no sliding window at training " +
            "time and no sampling — the corpus was reduced to a matrix once, and the matrix is the training set. " +
            "That is what makes GloVe trivially parallel and reproducible.",
        dots = emptyList(),
        profile = model.losses.filterIndexed { i, _ -> i % 2 == 0 }
            .map { ProfileBar((it / model.losses.first()).coerceIn(0.01f, 1f), 0) },
        profileLabel = "weighted loss per epoch",
        readout = "loss ${"%.3f".format(model.losses.first())} → ${"%.5f".format(model.losses.last())}",
    )

    // The trained vectors are 8-D; the plot is their first two principal components, which is how
    // embeddings are actually inspected.
    val projection = GloveLab.project2D(model)
    val royalty = setOf("king", "queen", "monarch", "crown", "kingdom", "throne")
    val people = setOf("man", "woman", "dog", "book", "water")
    fun dotFor(word: String, emphasis: Emphasis = Emphasis.NORMAL): Dot {
        val (x, y) = GloveLab.coordinateOf(projection, word)
        val group = when (word) {
            in royalty -> 0
            in people -> 1
            else -> 2
        }
        return Dot(P(x, y), group, emphasis)
    }

    frames += CloudFrame(
        status = "The ${GloveLab.dim}-dimensional vectors, projected onto their first two principal components — which is " +
            "how embeddings are actually looked at, because nobody can read 300 numbers. king and queen land on " +
            "top of each other (cosine ${"%.2f".format(GloveLab.similarity(model, "king", "queen"))}); \"the\", which co-occurs with everything, is pushed to the edge " +
            "with no neighbours at all.",
        dots = GloveLab.vocab.map { dotFor(it) },
        readout = "nearest to king: " + GloveLab.nearest(model, "king", 3).joinToString(", ") { "${it.first} ${"%.2f".format(it.second)}" },
    )

    val analogy = GloveLab.analogy(model, "king", "man", "woman")
    frames += CloudFrame(
        status = "The offsets between king/man and queen/woman are roughly parallel, which is what makes the " +
            "arithmetic work: king − man + woman lands nearest \"${analogy.first().first}\" at cosine ${"%.2f".format(analogy.first().second)}. Skip-gram scores " +
            "the same analogy higher on this corpus (${"%.2f".format(Word2VecLab.vocab.filter { it !in listOf("king", "man", "woman") }.maxOf { w ->
            val t = FloatArray(Word2VecLab.dim) {
                Word2VecLab.skipGram.vector("king")[it] - Word2VecLab.skipGram.vector("man")[it] + Word2VecLab.skipGram.vector("woman")[it]
            }
            cosineOf(t, Word2VecLab.skipGram.vector(w))
        })}) — 20 sentences is far too little to rank the two methods, and the published comparisons are run on billions of tokens.",
        dots = GloveLab.vocab.map {
            dotFor(it, if (it in listOf("king", "queen", "man", "woman")) Emphasis.ACTIVE else Emphasis.FADED)
        },
        segments = listOf(
            Segment(
                P(GloveLab.coordinateOf(projection, "man").first, GloveLab.coordinateOf(projection, "man").second),
                P(GloveLab.coordinateOf(projection, "king").first, GloveLab.coordinateOf(projection, "king").second),
                QueryColor,
            ),
            Segment(
                P(GloveLab.coordinateOf(projection, "woman").first, GloveLab.coordinateOf(projection, "woman").second),
                P(GloveLab.coordinateOf(projection, "queen").first, GloveLab.coordinateOf(projection, "queen").second),
                QueryColor,
                dashed = true,
            ),
        ),
        readout = "king − man + woman ≈ ${analogy.first().first} (${"%.2f".format(analogy.first().second)})",
    )

    frames += CloudFrame(
        status = "What GloVe buys over word2vec is not accuracy but shape: the corpus is summarised once into a " +
            "matrix, training touches only non-zero cells, and the result is deterministic and parallelisable. " +
            "What it costs is memory — the matrix is |V|² in the worst case, which is why real implementations " +
            "cap the window, prune rare pairs and stream the counts.",
        dots = GloveLab.vocab.map { dotFor(it) },
        readout = "counts once · fit many · no sampling",
    )
    return frames
}

// ── D3 · ELMo: one vector per occurrence ────────────────────────────────────

private fun elmoFrames(): List<CloudFrame> {
    val frames = mutableListOf<CloudFrame>()
    val projection = ElmoLab.project2D()
    val staticPoint = P(projection.last().first, projection.last().second)
    fun occurrence(i: Int, emphasis: Emphasis = Emphasis.NORMAL) =
        Dot(P(projection[i].first, projection[i].second), if (ElmoLab.senseOf[i] == "river") 0 else 1, emphasis)

    frames += CloudFrame(
        status = "Every embedding so far gives one vector per *type*: \"bank\" has a single row in the table, and " +
            "the river and the money senses share it. Its similarity with itself is ${"%.1f".format(ElmoLab.staticSimilarity())} by construction — " +
            "there is nothing else it could be.",
        dots = listOf(Dot(staticPoint, 2, Emphasis.QUERY)),
        readout = "static: 1 vector for ${ElmoLab.sentences.size} occurrences",
    )

    frames += CloudFrame(
        status = "ELMo makes the representation a function of the sentence instead. A two-layer bidirectional " +
            "LSTM language model reads the whole sentence, and a token's vector is a learned weighted sum of its " +
            "layers — so the same word in four sentences produces four different vectors.",
        dots = ElmoLab.sentences.indices.map { occurrence(it) } + Dot(staticPoint, 2, Emphasis.FADED),
        readout = "${ElmoLab.sentences.size} occurrences of \"${ElmoLab.target}\" · ${ElmoLab.sentences.size} vectors",
    )

    frames += CloudFrame(
        status = "The two river sentences land together and the two money sentences land together: within a " +
            "sense the cosine is ${"%.3f".format(ElmoLab.similarity(0, 1))} and ${"%.3f".format(ElmoLab.similarity(2, 3))}, and every cross-sense pair scores lower " +
            "(${"%.3f".format(ElmoLab.acrossSense())} on average). Word sense disambiguation, with no sense inventory and no labels — the " +
            "senses were never enumerated, they fell out of context.",
        dots = ElmoLab.sentences.indices.map { occurrence(it, Emphasis.ACTIVE) } + Dot(staticPoint, 2, Emphasis.FADED),
        segments = listOf(
            Segment(P(projection[0].first, projection[0].second), P(projection[1].first, projection[1].second), CloudColors[0]),
            Segment(P(projection[2].first, projection[2].second), P(projection[3].first, projection[3].second), CloudColors[1]),
        ),
        readout = "within ${"%.3f".format(ElmoLab.withinSense())} · across ${"%.3f".format(ElmoLab.acrossSense())} · gap ${"%.3f".format(ElmoLab.senseGap())}",
    )

    frames += CloudFrame(
        status = "This lab's contextual layer is a stand-in — a token's vector mixed with the mean of its " +
            "sentence — so the gap it produces (${"%.2f".format(ElmoLab.senseGap())}) is far narrower than a trained biLM's. What " +
            "reproduces faithfully is the ordering: every same-sense pair beats every cross-sense pair, which is " +
            "the property the topic is about.",
        dots = ElmoLab.sentences.indices.map { occurrence(it) } + Dot(staticPoint, 2, Emphasis.QUERY),
        rings = listOf(
            Ring(P((projection[0].first + projection[1].first) / 2, (projection[0].second + projection[1].second) / 2), 0.18f, CloudColors[0]),
            Ring(P((projection[2].first + projection[3].first) / 2, (projection[2].second + projection[3].second) / 2), 0.18f, CloudColors[1]),
        ),
        readout = "same-sense pairs: ${"%.3f".format(ElmoLab.similarity(0, 1))}, ${"%.3f".format(ElmoLab.similarity(2, 3))} — both above every cross pair",
    )

    frames += CloudFrame(
        status = "The deep part matters as much as the bidirectional part: ELMo's contribution was that the " +
            "*lower* layer captures syntax and the upper one semantics, so a task learns its own mix — γ·Σ sⱼhⱼ, " +
            "with s learned per task. Freezing the biLM and training only those weights beat the state of the " +
            "art on six benchmarks at once in 2018.",
        dots = ElmoLab.sentences.indices.map { occurrence(it) },
        readout = "layer 0 characters · layer 1 syntax · layer 2 semantics",
    )

    frames += CloudFrame(
        status = "And the trade it introduced, which every contextual model since inherits: a lookup table of " +
            "1M words at 300 dimensions is ${"%,d".format(ElmoLab.lookupParameters())} parameters you can memory-map, while ELMo is " +
            "${"%,d".format(ElmoLab.elmoParameters())} parameters that must *run* on every sentence. Smaller model, larger bill — you can " +
            "no longer precompute anything. BERT then replaced the LSTM with a transformer and made both numbers " +
            "bigger again.",
        dots = ElmoLab.sentences.indices.map { occurrence(it) } + Dot(staticPoint, 2, Emphasis.QUERY),
        readout = "static: lookup · contextual: inference per token",
    )
    return frames
}

// ── D5 · Vector databases ────────────────────────────────────────────────────
// A real index over a real corpus: brute force, an IVF built by Lloyd's algorithm, and HNSW's inner
// loop over two neighbour graphs. Every recall number is measured against the exact answer.

private fun vectorDbFrames(): List<CloudFrame> {
    val frames = mutableListOf<CloudFrame>()
    val queryPoint = P(VectorDbLab.query.first.toFloat(), VectorDbLab.query.second.toFloat())
    val exact = VectorDbLab.exactNeighbours()
    val exactIds = exact.map { it.id }.toSet()

    fun docDot(doc: VectorDbLab.Doc, emphasis: Emphasis = Emphasis.NORMAL, group: Int = doc.cluster) =
        Dot(P(doc.x.toFloat(), doc.y.toFloat()), group, emphasis)

    fun baseDots(emphasise: Set<Int> = emptySet(), faded: Set<Int> = emptySet()) =
        VectorDbLab.corpus.map { doc ->
            docDot(
                doc,
                when {
                    doc.id in emphasise -> Emphasis.ACTIVE
                    doc.id in faded -> Emphasis.FADED
                    else -> Emphasis.NORMAL
                },
            )
        } + Dot(queryPoint, 0, Emphasis.QUERY)

    frames += CloudFrame(
        status = "${VectorDbLab.corpusSize} embedded passages and one query. A vector database answers exactly " +
            "one question — which stored vectors are nearest this one — and the honest baseline is to compare " +
            "against all of them. That is ${VectorDbLab.corpusSize} distance computations here, exact by " +
            "construction, and it is what every approximate index below is measured against.",
        dots = baseDots(exactIds),
        rings = listOf(Ring(queryPoint, VectorDbLab.distance(exact.last()).toFloat(), QueryColor)),
        readout = "brute force: ${VectorDbLab.corpusSize} comparisons, recall 1.00 by definition",
    )

    frames += CloudFrame(
        status = "The exact top ${VectorDbLab.k} sit within " +
            "${"%.3f".format(VectorDbLab.distance(exact.last()))} of the query, and they are separated from " +
            "each other by only ${"%.4f".format(VectorDbLab.neighbourSpread())}. Hold onto that second number: " +
            "every approximation below has to preserve an ordering finer than it, and that is what most of " +
            "them fail at.",
        dots = baseDots(exactIds, VectorDbLab.corpus.map { it.id }.toSet() - exactIds),
        segments = exact.map { Segment(queryPoint, P(it.x.toFloat(), it.y.toFloat()), QueryColor) },
        readout = "neighbour spread ${"%.4f".format(VectorDbLab.neighbourSpread())} — the resolution to beat",
    )

    val cells = VectorDbLab.ivf.centroids
    val (oneProbe, oneCost) = VectorDbLab.ivfSearch(1)
    val (twoProbe, twoCost) = VectorDbLab.ivfSearch(2)
    frames += CloudFrame(
        status = "IVF partitions the corpus first. Lloyd's algorithm on the stored vectors gives " +
            "${cells.size} coarse cells, each vector is tagged with its cell, and a query only scans the " +
            "cells it probes. The whole index is that one assignment — which is why it builds in seconds and " +
            "why its failure mode is geometric.",
        dots = VectorDbLab.corpus.mapIndexed { index, doc -> docDot(doc, group = VectorDbLab.ivf.assignment[index]) } +
            Dot(queryPoint, 0, Emphasis.QUERY),
        centroids = cells.mapIndexed { index, c -> Dot(P(c.first.toFloat(), c.second.toFloat()), index) },
        readout = "${cells.size} cells, ${VectorDbLab.corpusSize} vectors",
    )

    frames += CloudFrame(
        status = "This query sits on a cell boundary, which is the case IVF is actually bad at — putting it in " +
            "the middle of a cell would have measured nothing. Probing one cell scans $oneCost vectors " +
            "instead of ${VectorDbLab.corpusSize} and returns recall " +
            "${"%.2f".format(VectorDbLab.recall(oneProbe))}: two of the true neighbours are sitting just " +
            "across the boundary, in a cell it never opened.",
        dots = VectorDbLab.corpus.mapIndexed { index, doc ->
            docDot(
                doc,
                if (doc.id in exactIds && doc !in oneProbe) Emphasis.ACTIVE else if (doc.id in exactIds) Emphasis.NORMAL else Emphasis.FADED,
                group = VectorDbLab.ivf.assignment[index],
            )
        } + Dot(queryPoint, 0, Emphasis.QUERY),
        centroids = cells.mapIndexed { index, c -> Dot(P(c.first.toFloat(), c.second.toFloat()), index) },
        readout = "nprobe 1: $oneCost comparisons, recall ${"%.2f".format(VectorDbLab.recall(oneProbe))}",
    )

    frames += CloudFrame(
        status = "nprobe is the recall knob, and it is cheap here: opening the second cell costs " +
            "${twoCost - oneCost} more comparisons and recovers the missing neighbours — recall " +
            "${"%.2f".format(VectorDbLab.recall(twoProbe))} at $twoCost of ${VectorDbLab.corpusSize} " +
            "comparisons. That is the trade the whole field runs on: recall is not a property of the index, " +
            "it is a dial with a price per notch.",
        dots = baseDots(exactIds),
        profile = (1..5).map { np ->
            ProfileBar(VectorDbLab.recall(VectorDbLab.ivfSearch(np).first).toFloat(), if (np <= 1) 3 else 2)
        },
        profileLabel = "recall@${VectorDbLab.k} at nprobe 1 → 5",
        readout = "nprobe 2: $twoCost comparisons, recall ${"%.2f".format(VectorDbLab.recall(twoProbe))}",
    )

    val stranded = VectorDbLab.strandedEntry()
    val plainFromStranded = VectorDbLab.graphSearch(4, VectorDbLab.knnGraph, entry = stranded)
    val smallFromStranded = VectorDbLab.graphSearch(4, VectorDbLab.smallWorldGraph, entry = stranded)
    frames += CloudFrame(
        status = "The other family navigates instead of partitioning. Link every vector to its 6 nearest " +
            "neighbours, start somewhere, and repeatedly walk to whichever neighbour is closer to the query. " +
            "It is greedy hill-climbing on a graph, and on this corpus it works — from a well-placed entry " +
            "point it reaches the answer in a handful of hops.",
        dots = baseDots(exactIds + stranded),
        segments = VectorDbLab.knnGraph.flatMapIndexed { from, links ->
            links.map { to ->
                Segment(
                    P(VectorDbLab.corpus[from].x.toFloat(), VectorDbLab.corpus[from].y.toFloat()),
                    P(VectorDbLab.corpus[to].x.toFloat(), VectorDbLab.corpus[to].y.toFloat()),
                    UnassignedColor,
                )
            }
        },
        readout = "6-NN graph: ${VectorDbLab.knnGraph.sumOf { it.size }} links",
    )

    frames += CloudFrame(
        status = "Except that graph is in ${VectorDbLab.componentCount(VectorDbLab.knnGraph)} pieces. A " +
            "k-nearest-neighbour graph over well-separated data is not connected, so from " +
            "${"%.0f".format(100 * VectorDbLab.strandedFraction())}% of possible entry points there is no " +
            "path to the answer at all — and no amount of candidate list gets you one. Starting at vector " +
            "$stranded, recall is ${"%.2f".format(VectorDbLab.recall(plainFromStranded.found))} after " +
            "${plainFromStranded.comparisons} comparisons.",
        dots = VectorDbLab.corpus.map { doc ->
            docDot(
                doc,
                when (doc.id) {
                    stranded -> Emphasis.ACTIVE
                    in exactIds -> Emphasis.NORMAL
                    else -> Emphasis.FADED
                },
                group = VectorDbLab.components(VectorDbLab.knnGraph)[doc.id],
            )
        } + Dot(queryPoint, 0, Emphasis.QUERY),
        readout = "${VectorDbLab.componentCount(VectorDbLab.knnGraph)} components — recall 0.00 from the wrong one",
    )

    frames += CloudFrame(
        status = "This is why HNSW is not a k-NN graph. Add two random long-range links per vector and the " +
            "graph collapses to ${VectorDbLab.componentCount(VectorDbLab.smallWorldGraph)} component: the same " +
            "stranded start now reaches recall ${"%.2f".format(VectorDbLab.recall(smallFromStranded.found))} " +
            "in ${smallFromStranded.hops} hops and ${smallFromStranded.comparisons} comparisons. The real " +
            "algorithm gets its long links from a layer hierarchy rather than at random, but the job they do " +
            "is this one.",
        dots = baseDots(exactIds + stranded),
        segments = VectorDbLab.smallWorldGraph.flatMapIndexed { from, links ->
            links.filterNot { it in VectorDbLab.knnGraph[from] }.map { to ->
                Segment(
                    P(VectorDbLab.corpus[from].x.toFloat(), VectorDbLab.corpus[from].y.toFloat()),
                    P(VectorDbLab.corpus[to].x.toFloat(), VectorDbLab.corpus[to].y.toFloat()),
                    QueryColor,
                    dashed = true,
                )
            }
        },
        readout = "long links: ${VectorDbLab.componentCount(VectorDbLab.smallWorldGraph)} component, recall ${"%.2f".format(VectorDbLab.recall(smallFromStranded.found))}",
    )

    frames += CloudFrame(
        status = "Compression is the third lever, and it is the one that quietly destroys recall. Storing each " +
            "component at 16 levels instead of a float puts every vector on a grid of spacing " +
            "${"%.3f".format(VectorDbLab.quantizationStep(16))} — more than " +
            "${"%.0f".format(VectorDbLab.quantizationStep(16) / VectorDbLab.neighbourSpread())}× coarser than " +
            "the ${"%.4f".format(VectorDbLab.neighbourSpread())} that separates the true neighbours from each " +
            "other. Recall lands at ${"%.2f".format(VectorDbLab.quantizedRecall(16))}, and the fix is not a " +
            "better quantizer, it is rescoring the shortlist with the full vectors.",
        dots = baseDots(exactIds),
        profile = listOf(4, 8, 16, 64).map { ProfileBar(VectorDbLab.quantizedRecall(it).toFloat(), if (it >= 64) 2 else 3) },
        profileLabel = "recall@${VectorDbLab.k} at 4, 8, 16, 64 levels per component",
        readout = "int8 at 768 dims: ${VectorDbLab.bytesPerVector(768, 8)} bytes vs ${VectorDbLab.bytesPerVector(768, 32)} — 4× smaller, and rescoring is mandatory",
    )

    val contrasts = listOf(2, 8, 32, 128).map { it to VectorDbLab.contrastRatio(it) }
    frames += CloudFrame(
        status = "Last, the reason none of this is easy at real dimensions. Over uniform points the contrast " +
            "(d_max − d_min) / d_min falls from ${"%.1f".format(contrasts[0].second)} in 2 dimensions to " +
            "${"%.2f".format(contrasts.last().second)} in ${contrasts.last().first} — the nearest and " +
            "furthest vectors stop being meaningfully different distances apart. Real embeddings are not " +
            "uniform, which is the only reason any of this works; the measurement is what an index is " +
            "fighting.",
        dots = baseDots(exactIds),
        profile = contrasts.map { ProfileBar((it.second / contrasts[0].second).toFloat().coerceAtLeast(0.01f), 1) },
        profileLabel = "relative contrast at d = 2, 8, 32, 128 (scaled to d = 2)",
        readout = contrasts.joinToString("  ") { "d=${it.first}: ${"%.2f".format(it.second)}" },
    )
    return frames
}

// ── Data preprocessing (phase 9, batch B8) ───────────────────────────────────
// The three geometric preprocessing topics. Scaling and SMOTE are both claims about distance, and
// this widget is the one that draws distance — so the frames show the same points before and after,
// with the k-NN accuracy each arrangement actually produces underneath. All numbers come from
// PreprocessMath.kt.
//
// The canvas maps [0,1], so the raw-units frames divide *both* features by one shared constant
// rather than normalising each. That is the honest display: it is what "one distance metric over
// both columns" looks like, and the point of the topic is that age disappears inside it.

private fun sharedScaleDots(data: List<Sample>): List<Dot> {
    val biggest = data.maxOf { kotlin.math.max(it.features[0], it.features[1]) }
    return data.map { sample ->
        Dot(P((sample.features[0] / biggest).toFloat(), (sample.features[1] / biggest).toFloat(), sample.label), sample.label)
    }
}

private fun unitDots(data: List<Sample>, pad: Double = 0.05): List<Dot> {
    val xs = data.map { it.features[0] }
    val ys = data.map { it.features[1] }
    val x0 = xs.min(); val x1 = xs.max(); val y0 = ys.min(); val y1 = ys.max()
    fun scale(v: Double, lo: Double, hi: Double) = (pad + (1 - 2 * pad) * (v - lo) / (hi - lo)).toFloat()
    return data.map { sample ->
        Dot(P(scale(sample.features[0], x0, x1), scale(sample.features[1], y0, y1), sample.label), sample.label)
    }
}

private fun scalingFrames(minMaxFirst: Boolean): List<CloudFrame> {
    val frames = mutableListOf<CloudFrame>()
    val raw = FeatureScalingLab.results.first { it.name == "raw" }
    val minMax = FeatureScalingLab.results.first { it.name == "min-max" }
    val zScore = FeatureScalingLab.results.first { it.name == "z-score" }
    val shares = FeatureScalingLab.distanceShares.toMap()
    val subject = if (minMaxFirst) minMax else zScore
    val other = if (minMaxFirst) zScore else minMax

    frames += CloudFrame(
        status = "Two features on their own units: ${FeatureScalingLab.featureNames.joinToString(" and ")}. Age spans " +
            "${"%.0f".format(raw.ranges[0])} and income spans ${"%.0f".format(raw.ranges[1])}. Drawn on one shared " +
            "axis — which is what a single distance metric does to them — every point sits on a line, because age " +
            "is invisible at income's scale.",
        dots = sharedScaleDots(FeatureScalingLab.train),
        readout = "ranges ${"%.0f".format(raw.ranges[0])} vs ${"%.0f".format(raw.ranges[1])}",
    )

    frames += CloudFrame(
        status = "That is not a drawing artefact, it is the arithmetic k-NN performs. Averaged over every " +
            "train/test pair, income contributes ${"%.4f".format(shares.getValue("raw")[1] * 100)}% of the squared " +
            "distance and age ${"%.4f".format(shares.getValue("raw")[0] * 100)}%. The classifier has two features " +
            "and uses one.",
        dots = sharedScaleDots(FeatureScalingLab.train),
        profile = shares.getValue("raw").mapIndexed { index, value -> ProfileBar(value.toFloat(), index, Emphasis.ACTIVE) },
        profileLabel = "share of squared distance: age, income",
        readout = "k-NN accuracy ${"%.3f".format(raw.accuracy)}",
    )

    frames += CloudFrame(
        status = if (minMaxFirst) {
            "Min-max maps each column onto [0,1] using its own minimum and maximum: (x − min) / (max − min). Both " +
                "ranges become exactly 1, the shape of the cloud is preserved, and the distance split moves to " +
                "${"%.1f".format(shares.getValue("min-max")[0] * 100)}% / ${"%.1f".format(shares.getValue("min-max")[1] * 100)}%."
        } else {
            "Z-score subtracts each column's mean and divides by its standard deviation: (x − μ) / σ. Units are " +
                "gone, the ranges become ${"%.2f".format(zScore.ranges[0])} and ${"%.2f".format(zScore.ranges[1])} — " +
                "not 1, because the range depends on how far the extremes sit — and the distance split moves to " +
                "${"%.1f".format(shares.getValue("z-score")[0] * 100)}% / ${"%.1f".format(shares.getValue("z-score")[1] * 100)}%."
        },
        dots = unitDots(if (minMaxFirst) FeatureScalingLab.minMax(FeatureScalingLab.train, FeatureScalingLab.train) else FeatureScalingLab.zScore(FeatureScalingLab.train, FeatureScalingLab.train)),
        profile = shares.getValue(subject.name).mapIndexed { index, value -> ProfileBar(value.toFloat(), index, Emphasis.NORMAL) },
        profileLabel = "share of squared distance: age, income",
        readout = "k-NN accuracy ${"%.3f".format(raw.accuracy)} → ${"%.3f".format(subject.accuracy)}",
    )

    frames += CloudFrame(
        status = "Scored on held-out data, the same k-NN with the same k: ${"%.3f".format(raw.accuracy)} raw, " +
            "${"%.3f".format(minMax.accuracy)} min-max, ${"%.3f".format(zScore.accuracy)} z-score. The model did " +
            "not change and the data did not change — only the units the distance was measured in.",
        dots = unitDots(if (minMaxFirst) FeatureScalingLab.minMax(FeatureScalingLab.train, FeatureScalingLab.train) else FeatureScalingLab.zScore(FeatureScalingLab.train, FeatureScalingLab.train)),
        profile = listOf(
            ProfileBar(raw.accuracy.toFloat(), 1, Emphasis.FADED),
            ProfileBar(minMax.accuracy.toFloat(), 0, if (minMaxFirst) Emphasis.ACTIVE else Emphasis.NORMAL),
            ProfileBar(zScore.accuracy.toFloat(), 2, if (minMaxFirst) Emphasis.NORMAL else Emphasis.ACTIVE),
        ),
        profileLabel = "k-NN accuracy: raw, min-max, z-score",
        readout = "+${"%.1f".format((subject.accuracy - raw.accuracy) * 100)} points from a two-line transform",
    )

    val effect = FeatureScalingLab.outlierEffect
    frames += CloudFrame(
        status = if (minMaxFirst) {
            "And here is min-max's failure mode. Fit the scaler on data containing one income of " +
                "${"%.0f".format(effect.outlierIncome)} and every real point is squeezed into " +
                "${"%.3f".format(effect.minMaxSpan)} of the [0,1] range — the transform is defined by the two most " +
                "extreme values in the column, so a single bad row rescales everything. Z-score moves too, but " +
                "the same points still span ${"%.2f".format(effect.zScoreSpan)}."
        } else {
            "Z-score's advantage over min-max shows up under contamination. Fit both on data containing one income " +
                "of ${"%.0f".format(effect.outlierIncome)}: min-max squeezes every real point into " +
                "${"%.3f".format(effect.minMaxSpan)} of its range, because it is defined by the two extremes, " +
                "while z-score leaves them spanning ${"%.2f".format(effect.zScoreSpan)}. Neither is robust — σ " +
                "moves as well — but they do not fail equally."
        },
        dots = unitDots(FeatureScalingLab.minMax(FeatureScalingLab.train + Sample(doubleArrayOf(45.0, effect.outlierIncome), 1), FeatureScalingLab.train)),
        profile = listOf(
            ProfileBar(effect.minMaxSpan.toFloat(), 3, Emphasis.ACTIVE),
            ProfileBar((effect.zScoreSpan / 4).toFloat(), 2, Emphasis.NORMAL),
        ),
        profileLabel = "span of the real points after fitting on the contaminated column",
        readout = "min-max ${"%.3f".format(effect.minMaxSpan)} vs z-score ${"%.2f".format(effect.zScoreSpan)}",
    )

    frames += CloudFrame(
        status = "Which is the rule that actually follows: min-max when you need a bounded range and the column " +
            "has no extremes (pixels, bounded scores), z-score when the column is roughly symmetric with tails, " +
            "and a robust scaler on the median and IQR when it is neither. Fit on the training split only — " +
            "fitting on everything leaks the test set's minimum, maximum, mean and σ into the transform.",
        dots = unitDots(if (minMaxFirst) FeatureScalingLab.minMax(FeatureScalingLab.train, FeatureScalingLab.train) else FeatureScalingLab.zScore(FeatureScalingLab.train, FeatureScalingLab.train)),
        readout = "and never fit the scaler on the test set",
    )
    return frames
}

private fun minMaxFrames(): List<CloudFrame> = scalingFrames(minMaxFirst = true)

private fun zScoreFrames(): List<CloudFrame> = scalingFrames(minMaxFirst = false)

private fun smoteFrames(): List<CloudFrame> {
    val frames = mutableListOf<CloudFrame>()
    val all = SmoteLab.majority + SmoteLab.minority
    val bounds = all.flatMap { listOf(it.features[0], it.features[1]) }
    val lo = bounds.min()
    val hi = bounds.max()
    fun place(sample: Sample, emphasis: Emphasis = Emphasis.NORMAL) = Dot(
        P(
            (0.06 + 0.88 * (sample.features[0] - lo) / (hi - lo)).toFloat(),
            (0.06 + 0.88 * (sample.features[1] - lo) / (hi - lo)).toFloat(),
            sample.label,
        ),
        sample.label,
        emphasis,
    )

    frames += CloudFrame(
        status = "${SmoteLab.majority.size} majority points and ${SmoteLab.minority.size} minority points — " +
            "${"%.0f".format(SmoteLab.imbalanceRatio)}:1. A classifier that answers \"majority\" every time " +
            "scores ${"%.1f".format(SmoteLab.majority.size * 100.0 / all.size)}% accuracy here, which is why " +
            "accuracy is the wrong metric before it is anything else.",
        dots = all.map { place(it, if (it.label == 1) Emphasis.ACTIVE else Emphasis.NORMAL) },
        readout = "${SmoteLab.imbalanceRatio.toInt()}:1 imbalance",
    )

    val minorityTrain = SmoteLab.trainSet.filter { it.label == 1 }
    val demo = SmoteLab.synthesise(minorityTrain, 3, seed = 71)
    frames += CloudFrame(
        status = "SMOTE does not copy minority points, it interpolates between them: pick a minority point, pick " +
            "one of its ${SmoteLab.K} nearest minority neighbours, and place a new point somewhere on the segment " +
            "between them. Three of them, drawn with the segments they came from.",
        dots = SmoteLab.trainSet.map { place(it, if (it.label == 1) Emphasis.ACTIVE else Emphasis.FADED) } +
            demo.map { place(it, Emphasis.QUERY) },
        segments = demo.mapIndexed { index, synthetic ->
            val nearest = minorityTrain.minBy { candidate ->
                candidate.features.indices.sumOf { i ->
                    val d = candidate.features[i] - synthetic.features[i]
                    d * d
                }
            }
            Segment(place(nearest).point, place(synthetic).point, QueryColor, dashed = true)
        },
        readout = "new points on the segments, not on top of the old ones",
    )

    frames += CloudFrame(
        status = "Resampling the training fold up to balance takes it from ${SmoteLab.trainSet.size} rows to " +
            "${SmoteLab.resampled.size}. The minority region is now dense — and it is dense with points that were " +
            "*inferred* from ${minorityTrain.size} real ones, which is the part to keep in mind when the score " +
            "improves.",
        dots = SmoteLab.resampled.map { place(it, if (it.label == 1) Emphasis.ACTIVE else Emphasis.FADED) },
        readout = "${SmoteLab.trainSet.size} → ${SmoteLab.resampled.size} rows, ${minorityTrain.size} real minority points",
    )

    val before = SmoteLab.scores.first { it.name == "imbalanced" }
    val after = SmoteLab.scores.first { it.name.startsWith("SMOTE") }
    frames += CloudFrame(
        status = "Scored with ${SmoteLab.K}-NN on the untouched real test split: minority recall goes " +
            "${"%.3f".format(before.minorityRecall)} → ${"%.3f".format(after.minorityRecall)}, which is what SMOTE " +
            "is for. Precision goes ${"%.3f".format(before.precision)} → ${"%.3f".format(after.precision)} and " +
            "accuracy ${"%.3f".format(before.accuracy)} → ${"%.3f".format(after.accuracy)}: it buys recall by " +
            "moving the boundary into majority territory, and that is a trade, not a free win.",
        dots = SmoteLab.testSet.map { place(it, if (it.label == 1) Emphasis.ACTIVE else Emphasis.NORMAL) },
        profile = listOf(
            ProfileBar(before.minorityRecall.toFloat(), 1, Emphasis.FADED),
            ProfileBar(after.minorityRecall.toFloat(), 1, Emphasis.ACTIVE),
            ProfileBar(before.precision.toFloat(), 3, Emphasis.FADED),
            ProfileBar(after.precision.toFloat(), 3, Emphasis.ACTIVE),
        ),
        profileLabel = "recall before/after, precision before/after",
        readout = "recall +${"%.0f".format((after.minorityRecall - before.minorityRecall) * 100)} points, precision −${"%.0f".format((before.precision - after.precision) * 100)}",
    )

    val leak = SmoteLab.leak
    frames += CloudFrame(
        status = "The mistake that matters more than the trade: resampling before the split. A synthetic point is " +
            "built from a real neighbour, so if that neighbour lands in the validation fold, the training set now " +
            "contains something interpolated towards a row it is about to be scored on. Measured over " +
            "${leak.folds} folds at ${leak.neighbours}-NN: ${"%.3f".format(leak.leakyScore)} for the leaky " +
            "pipeline against ${"%.3f".format(leak.honestScore)} for resampling inside each fold.",
        dots = SmoteLab.resampled.map { place(it, if (it.label == 1) Emphasis.QUERY else Emphasis.FADED) },
        profile = listOf(
            ProfileBar(leak.leakyScore.toFloat(), 3, Emphasis.ACTIVE),
            ProfileBar(leak.honestScore.toFloat(), 2, Emphasis.NORMAL),
        ),
        profileLabel = "cross-validated accuracy: resample first, resample inside folds",
        readout = "+${"%.1f".format((leak.leakyScore - leak.honestScore) * 100)} points of score that will not reproduce",
    )

    frames += CloudFrame(
        status = "So: resample inside the pipeline, never before the split; check precision alongside recall, " +
            "because the trade is real; and compare against the two cheaper things first — class weights, which " +
            "cost nothing and invent no data, and moving the decision threshold, which is free at inference time.",
        dots = all.map { place(it, if (it.label == 1) Emphasis.ACTIVE else Emphasis.NORMAL) },
        readout = "class weights and thresholds before synthetic rows",
    )
    return frames
}

// ── Evaluation metrics (phase 9, batch B9) ───────────────────────────────────
// Six of the seventeen metric topics are geometric. The four classification ones all draw the same
// 1,000-point feature space and the same fitted logistic boundary, moved to a different threshold —
// so accuracy 0.947 and F1 0.569 at t = 0.5 are two readings of one picture rather than two
// examples. The two clustering indices score real k-means runs. Numbers from MetricsMath.kt.

private fun metricBounds(): List<Double> {
    val xs = ScoredLab.samples.map { it.features[0] }
    val ys = ScoredLab.samples.map { it.features[1] }
    return listOf(xs.min(), xs.max(), ys.min(), ys.max())
}

private fun metricPlace(x: Double, y: Double): P {
    val (x0, x1, y0, y1) = metricBounds().let { listOf(it[0], it[1], it[2], it[3]) }
    return P(
        (0.05 + 0.9 * (x - x0) / (x1 - x0)).toFloat(),
        (0.05 + 0.9 * (y - y0) / (y1 - y0)).toFloat(),
    )
}

/** Groups: 0 true negative, 1 true positive, 2 false positive, 3 false negative. */
private fun metricDots(threshold: Double, dimCorrect: Boolean = false): List<Dot> =
    ScoredLab.samples.zip(ScoredLab.scored).map { (sample, scored) ->
        val predicted = scored.score >= threshold
        val group = when {
            predicted && scored.label == 1 -> 1
            predicted && scored.label == 0 -> 2
            !predicted && scored.label == 1 -> 3
            else -> 0
        }
        val emphasis = when {
            group >= 2 -> Emphasis.ACTIVE
            dimCorrect -> Emphasis.FADED
            else -> Emphasis.NORMAL
        }
        Dot(metricPlace(sample.features[0], sample.features[1]), group, emphasis)
    }

private fun boundarySegment(threshold: Double): Segment {
    val (slope, intercept) = ScoredLab.boundaryFor(threshold)
    val bounds = metricBounds()
    val x0 = bounds[0]
    val x1 = bounds[1]
    return Segment(
        metricPlace(x0, (slope * x0 + intercept).coerceIn(bounds[2], bounds[3])),
        metricPlace(x1, (slope * x1 + intercept).coerceIn(bounds[2], bounds[3])),
        AxisColor,
    )
}

private fun cellProfile(cells: ScoredLab.Cells): List<ProfileBar> {
    val biggest = maxOf(cells.tp, cells.fp, cells.fn, cells.tn).toFloat()
    return listOf(
        ProfileBar(cells.tp / biggest, 1, Emphasis.NORMAL),
        ProfileBar(cells.fp / biggest, 2, Emphasis.ACTIVE),
        ProfileBar(cells.fn / biggest, 3, Emphasis.ACTIVE),
        ProfileBar(cells.tn / biggest, 0, Emphasis.NORMAL),
    )
}

private fun confusionMatrixFrames(): List<CloudFrame> {
    val frames = mutableListOf<CloudFrame>()
    val half = ScoredLab.cellsAt(0.5)

    frames += CloudFrame(
        status = "${ScoredLab.scored.size} cases, ${ScoredLab.positives} of them positive — a " +
            "${"%.0f".format(ScoredLab.positives * 100.0 / ScoredLab.scored.size)}% base rate. The line is a " +
            "fitted logistic model's decision boundary at the default threshold of 0.5. Every metric in this " +
            "category is a summary of the four groups this picture already contains.",
        dots = metricDots(0.5),
        segments = listOf(boundarySegment(0.5)),
        readout = "${ScoredLab.positives} positives, ${ScoredLab.negatives} negatives",
    )

    frames += CloudFrame(
        status = "Those four groups are the confusion matrix. At t = 0.5: ${half.tp} true positives, " +
            "${half.fp} false positives, ${half.fn} false negatives, ${half.tn} true negatives. Nothing is " +
            "summarised yet — this is the raw material, and it is the only object in this category that loses no " +
            "information.",
        dots = metricDots(0.5),
        segments = listOf(boundarySegment(0.5)),
        profile = cellProfile(half),
        profileLabel = "TP ${half.tp} · FP ${half.fp} · FN ${half.fn} · TN ${half.tn}",
        readout = "four numbers, and every metric below is a function of them",
    )

    frames += CloudFrame(
        status = "The asymmetry is the point. ${half.tn} of the ${half.total} cases sit in one cell, so any metric " +
            "that averages over all four is dominated by it. The two error cells — ${half.fp} false alarms and " +
            "${half.fn} misses — are the ones a decision actually turns on, and they are ${"%.1f".format((half.fp + half.fn) * 100.0 / half.total)}% " +
            "of the table.",
        dots = metricDots(0.5, dimCorrect = true),
        segments = listOf(boundarySegment(0.5)),
        profile = cellProfile(half),
        profileLabel = "the two error cells, against the two correct ones",
        readout = "${half.fp} false alarms, ${half.fn} misses",
    )

    val low = ScoredLab.cellsAt(0.2)
    frames += CloudFrame(
        status = "Moving the threshold slides the same line — a logistic boundary is w₀ + w₁x + w₂y = logit(t), so " +
            "the model does not change, only where it is cut. At t = 0.2 the matrix becomes ${low.tp} / ${low.fp} / " +
            "${low.fn} / ${low.tn}: ${low.tp - half.tp} more positives found, at ${low.fp - half.fp} more false " +
            "alarms.",
        dots = metricDots(0.2),
        segments = listOf(boundarySegment(0.5), boundarySegment(0.2)),
        profile = cellProfile(low),
        profileLabel = "TP ${low.tp} · FP ${low.fp} · FN ${low.fn} · TN ${low.tn}",
        readout = "one model, many matrices",
    )

    frames += CloudFrame(
        status = "Which is why a confusion matrix has to be read alongside the threshold that produced it. " +
            "\"The model has ${half.fn} misses\" is not a property of the model; it is a property of the model at " +
            "0.5. The four cells at every threshold are what the ROC and precision-recall curves plot.",
        dots = metricDots(0.2, dimCorrect = true),
        segments = ScoredLab.thresholds.map { boundarySegment(it) },
        readout = "${ScoredLab.thresholds.size} thresholds, ${ScoredLab.thresholds.size} matrices, one model",
    )
    return frames
}

private fun accuracyFrames(): List<CloudFrame> {
    val frames = mutableListOf<CloudFrame>()
    val half = ScoredLab.cellsAt(0.5)
    val extreme = ScoredLab.cellsAt(0.99)

    frames += CloudFrame(
        status = "Accuracy is (TP + TN) / everything: the share of cases the model gets right. At t = 0.5 that is " +
            "(${half.tp} + ${half.tn}) / ${half.total} = ${"%.3f".format(half.accuracy)}, which sounds like a " +
            "finished result.",
        dots = metricDots(0.5),
        segments = listOf(boundarySegment(0.5)),
        readout = "accuracy ${"%.3f".format(half.accuracy)}",
    )

    frames += CloudFrame(
        status = "Here is the same number without a model. Predict \"negative\" for every case and accuracy is " +
            "${"%.3f".format(ScoredLab.majorityAccuracy)} — because ${ScoredLab.negatives} of ${ScoredLab.scored.size} " +
            "cases are negative. The trained model's ${"%.3f".format(half.accuracy)} is worth " +
            "${"%.1f".format((half.accuracy - ScoredLab.majorityAccuracy) * 100)} points over answering the same " +
            "way every time.",
        dots = ScoredLab.samples.zip(ScoredLab.scored).map { (sample, scored) ->
            Dot(metricPlace(sample.features[0], sample.features[1]), if (scored.label == 1) 3 else 0, if (scored.label == 1) Emphasis.ACTIVE else Emphasis.FADED)
        },
        profile = listOf(
            ProfileBar(ScoredLab.majorityAccuracy.toFloat(), 0, Emphasis.ACTIVE),
            ProfileBar(half.accuracy.toFloat(), 1, Emphasis.NORMAL),
        ),
        profileLabel = "accuracy: predict-all-negative, then the model",
        readout = "the baseline is ${"%.3f".format(ScoredLab.majorityAccuracy)}, not 0.5",
    )

    frames += CloudFrame(
        status = "And it gets worse the harder you push. At t = 0.99 the model predicts almost nothing positive: " +
            "${extreme.tp} true positives, ${extreme.fn} misses — it has found " +
            "${"%.0f".format(extreme.recall * 100)}% of the cases anyone cares about — and accuracy is " +
            "${"%.3f".format(extreme.accuracy)}, which is *higher* than at some thresholds that work far better.",
        dots = metricDots(0.99),
        segments = listOf(boundarySegment(0.99)),
        profile = cellProfile(extreme),
        profileLabel = "TP ${extreme.tp} · FP ${extreme.fp} · FN ${extreme.fn} · TN ${extreme.tn}",
        readout = "accuracy ${"%.3f".format(extreme.accuracy)}, recall ${"%.3f".format(extreme.recall)}",
    )

    val sweep = ScoredLab.thresholds.map { it to ScoredLab.cellsAt(it) }
    frames += CloudFrame(
        status = "Across the whole threshold range, accuracy is nearly flat while the thing the model is for is " +
            "not. From t = ${sweep.first().first} to t = ${sweep.last().first}, accuracy moves between " +
            "${"%.3f".format(sweep.minOf { it.second.accuracy })} and ${"%.3f".format(sweep.maxOf { it.second.accuracy })} " +
            "while recall moves between ${"%.3f".format(sweep.minOf { it.second.recall })} and " +
            "${"%.3f".format(sweep.maxOf { it.second.recall })}. A metric that barely responds cannot be used to " +
            "choose an operating point.",
        dots = metricDots(0.5, dimCorrect = true),
        profile = sweep.map { ProfileBar(it.second.accuracy.toFloat(), 0, Emphasis.NORMAL) } +
            sweep.map { ProfileBar(it.second.recall.toFloat(), 1, Emphasis.ACTIVE) },
        profileLabel = "accuracy at each threshold, then recall at each threshold",
        readout = "flat metric, moving model",
    )

    frames += CloudFrame(
        status = "So accuracy is honest on balanced data and misleading on everything else. Two rules follow: " +
            "always report the majority-class baseline next to it — ${"%.3f".format(ScoredLab.majorityAccuracy)} " +
            "here — and reach for precision, recall or a chance-corrected metric like Cohen's kappa when the " +
            "classes are not balanced. Kappa on this same matrix reads ${"%.3f".format(half.kappa)}.",
        dots = metricDots(0.5),
        segments = listOf(boundarySegment(0.5)),
        profile = listOf(
            ProfileBar(half.accuracy.toFloat(), 1, Emphasis.NORMAL),
            ProfileBar(ScoredLab.majorityAccuracy.toFloat(), 0, Emphasis.FADED),
            ProfileBar(half.kappa.toFloat(), 2, Emphasis.ACTIVE),
        ),
        profileLabel = "accuracy · baseline · kappa",
        readout = "accuracy ${"%.3f".format(half.accuracy)} vs kappa ${"%.3f".format(half.kappa)}",
    )
    return frames
}

private fun precisionRecallFrames(): List<CloudFrame> {
    val frames = mutableListOf<CloudFrame>()
    val half = ScoredLab.cellsAt(0.5)
    val low = ScoredLab.cellsAt(0.1)

    frames += CloudFrame(
        status = "Precision and recall split the model's errors into the two kinds a decision can care about. " +
            "Precision is TP / (TP + FP) — of everything flagged, how much was real. Recall is TP / (TP + FN) — of " +
            "everything real, how much was found. Neither one mentions the ${half.tn} true negatives at all.",
        dots = metricDots(0.5),
        segments = listOf(boundarySegment(0.5)),
        profile = cellProfile(half),
        profileLabel = "TP ${half.tp} · FP ${half.fp} · FN ${half.fn} · TN ${half.tn} (unused by both)",
        readout = "precision ${"%.3f".format(half.precision)}, recall ${"%.3f".format(half.recall)}",
    )

    frames += CloudFrame(
        status = "At t = 0.5 this model is precise and timid: precision ${"%.3f".format(half.precision)} — " +
            "everything it flags is real — and recall ${"%.3f".format(half.recall)}, so it misses " +
            "${half.fn} of ${ScoredLab.positives} positives. A fraud team reading only the precision would call " +
            "this perfect; the ${half.fn} missed cases are the entire cost of that reading.",
        dots = metricDots(0.5, dimCorrect = true),
        segments = listOf(boundarySegment(0.5)),
        readout = "${half.fn} misses, 0 false alarms",
    )

    frames += CloudFrame(
        status = "Drop the threshold to 0.1 and the trade reverses: recall ${"%.3f".format(low.recall)} — every " +
            "positive found — at precision ${"%.3f".format(low.precision)}, meaning " +
            "${"%.0f".format((1 - low.precision) * 100)}% of the flags are false alarms. Same model, same scores. " +
            "The threshold is a business decision, not a modelling one.",
        dots = metricDots(0.1),
        segments = listOf(boundarySegment(0.5), boundarySegment(0.1)),
        profile = cellProfile(low),
        profileLabel = "TP ${low.tp} · FP ${low.fp} · FN ${low.fn} · TN ${low.tn}",
        readout = "recall ${"%.3f".format(low.recall)}, precision ${"%.3f".format(low.precision)}",
    )

    val sweep = ScoredLab.thresholds.map { it to ScoredLab.cellsAt(it) }
    frames += CloudFrame(
        status = "The whole trade, threshold by threshold. Precision rises and recall falls monotonically, and " +
            "there is no threshold where both are high — that is the shape of the problem, not a defect of the " +
            "model. Which end you choose depends on whether a miss or a false alarm costs more.",
        dots = metricDots(0.3, dimCorrect = true),
        profile = sweep.map { ProfileBar(it.second.precision.toFloat(), 1, Emphasis.NORMAL) } +
            sweep.map { ProfileBar(it.second.recall.toFloat(), 3, Emphasis.ACTIVE) },
        profileLabel = "precision at each threshold, then recall",
        readout = "one curve, and the cost ratio picks the point on it",
    )

    frames += CloudFrame(
        status = "One more thing both metrics hide, and it is the reason they are always quoted as a pair: " +
            "neither uses TN, so both are blind to the size of the negative class. Precision " +
            "${"%.3f".format(low.precision)} at t = 0.1 would be the same number if there were ten times as many " +
            "negatives and the model flagged ten times as many of them — the base rate is inside precision, and " +
            "outside recall entirely.",
        dots = metricDots(0.1, dimCorrect = true),
        segments = listOf(boundarySegment(0.1)),
        readout = "quote both, with the threshold",
    )
    return frames
}

private fun f1Frames(): List<CloudFrame> {
    val frames = mutableListOf<CloudFrame>()
    val half = ScoredLab.cellsAt(0.5)
    val best = ScoredLab.cellsAt(ScoredLab.bestF1Threshold)

    frames += CloudFrame(
        status = "F1 is the harmonic mean of precision and recall: 2PR / (P + R). The harmonic part is the whole " +
            "design — it is dragged towards the smaller of the two, so a model cannot score well by being " +
            "excellent at one and hopeless at the other.",
        dots = metricDots(0.5),
        segments = listOf(boundarySegment(0.5)),
        profile = listOf(
            ProfileBar(half.precision.toFloat(), 1, Emphasis.NORMAL),
            ProfileBar(half.recall.toFloat(), 3, Emphasis.NORMAL),
            ProfileBar(half.f1.toFloat(), 2, Emphasis.ACTIVE),
        ),
        profileLabel = "precision · recall · F1",
        readout = "P ${"%.3f".format(half.precision)}, R ${"%.3f".format(half.recall)} → F1 ${"%.3f".format(half.f1)}",
    )

    frames += CloudFrame(
        status = "Compare the two means on this matrix. The arithmetic mean of ${"%.3f".format(half.precision)} and " +
            "${"%.3f".format(half.recall)} is ${"%.3f".format((half.precision + half.recall) / 2)}; the harmonic " +
            "mean is ${"%.3f".format(half.f1)}. A perfect-precision, one-case model would score 0.5 on the first " +
            "and almost 0 on the second, which is why F1 is the one that gets used.",
        dots = metricDots(0.5, dimCorrect = true),
        profile = listOf(
            ProfileBar(((half.precision + half.recall) / 2).toFloat(), 0, Emphasis.FADED),
            ProfileBar(half.f1.toFloat(), 2, Emphasis.ACTIVE),
        ),
        profileLabel = "arithmetic mean, then harmonic mean",
        readout = "${"%.3f".format((half.precision + half.recall) / 2)} vs ${"%.3f".format(half.f1)}",
    )

    frames += CloudFrame(
        status = "F1 also has an optimum, and it is not at 0.5. Sweeping every threshold, F1 peaks at " +
            "t = ${ScoredLab.bestF1Threshold} with ${"%.3f".format(ScoredLab.bestF1)} — against " +
            "${"%.3f".format(half.f1)} at the default. Nothing about the model changed; the default threshold was " +
            "simply the wrong place to cut it.",
        dots = metricDots(ScoredLab.bestF1Threshold),
        segments = listOf(boundarySegment(0.5), boundarySegment(ScoredLab.bestF1Threshold)),
        profile = cellProfile(best),
        profileLabel = "TP ${best.tp} · FP ${best.fp} · FN ${best.fn} · TN ${best.tn} at the F1-optimal threshold",
        readout = "F1 ${"%.3f".format(half.f1)} → ${"%.3f".format(ScoredLab.bestF1)} by moving t alone",
    )

    frames += CloudFrame(
        status = "What F1 leaves out is the ${half.tn} true negatives — it is a function of TP, FP and FN only. " +
            "That makes it the right summary when the positive class is the subject and the negative class is " +
            "just background, and the wrong one when both classes matter, where accuracy or kappa belong instead.",
        dots = metricDots(ScoredLab.bestF1Threshold, dimCorrect = true),
        profile = cellProfile(best),
        profileLabel = "F1 reads three of these four cells",
        readout = "asymmetric by design",
    )

    frames += CloudFrame(
        status = "And if the two errors do not cost the same, F1's built-in 1:1 weighting is a choice you did not " +
            "make. Fβ generalises it: β = 2 weights recall twice as heavily (${"%.3f".format(best.fBeta(2.0))} " +
            "here), β = 0.5 weights precision (${"%.3f".format(best.fBeta(0.5))}). Pick β from the cost ratio " +
            "rather than defaulting to 1.",
        dots = metricDots(ScoredLab.bestF1Threshold),
        segments = listOf(boundarySegment(ScoredLab.bestF1Threshold)),
        profile = listOf(
            ProfileBar(best.fBeta(0.5).toFloat(), 1, Emphasis.NORMAL),
            ProfileBar(best.f1.toFloat(), 2, Emphasis.ACTIVE),
            ProfileBar(best.fBeta(2.0).toFloat(), 3, Emphasis.NORMAL),
        ),
        profileLabel = "F0.5 · F1 · F2 at the same threshold",
        readout = "β encodes which error you are afraid of",
    )
    return frames
}

// ── Clustering indices ───────────────────────────────────────────────────────

private fun clusterDots(data: List<ClusterMetricsLab.Point2>, assignment: List<Int>, silhouettes: List<Double>? = null) =
    data.mapIndexed { index, point ->
        Dot(
            P(point.x.toFloat(), point.y.toFloat()),
            assignment[index],
            if (silhouettes != null && silhouettes[index] < 0) Emphasis.QUERY else Emphasis.NORMAL,
        )
    }

private fun silhouetteFrames(): List<CloudFrame> {
    val frames = mutableListOf<CloudFrame>()
    val blobs = ClusterMetricsLab.blobs
    val three = ClusterMetricsLab.kMeans(blobs, 3)
    val threeScores = ClusterMetricsLab.silhouettes(blobs, three)
    val sweep = ClusterMetricsLab.blobSweep

    frames += CloudFrame(
        status = "Clustering has no labels to score against, so its metrics score *shape* instead. Silhouette asks, " +
            "per point: how far is it from its own cluster on average (a), and from the nearest other cluster (b)? " +
            "The score is (b − a) / max(a, b), between −1 and 1.",
        dots = clusterDots(blobs, three),
        readout = "${blobs.size} points, k = 3",
    )

    frames += CloudFrame(
        status = "Drawn per point, the profile is more useful than the average. Here every point scores positive " +
            "and the mean is ${"%.3f".format(ClusterMetricsLab.silhouetteScore(blobs, three))} — the clusters are " +
            "compact and far apart, which is exactly the situation silhouette was designed for.",
        dots = clusterDots(blobs, three, threeScores),
        profile = threeScores.sortedDescending().map { ProfileBar(it.toFloat().coerceIn(0f, 1f), 1, Emphasis.NORMAL) },
        profileLabel = "per-point silhouette, sorted",
        readout = "mean ${"%.3f".format(ClusterMetricsLab.silhouetteScore(blobs, three))}, ${ClusterMetricsLab.negativeCount(blobs, three)} negative",
    )

    frames += CloudFrame(
        status = "Because it needs no labels, it can be swept over k — and on this dataset it gets the answer " +
            "right: " + sweep.joinToString(", ") { "k=${it.k} ${"%.2f".format(it.silhouette)}" } +
            ". The peak is at k = ${ClusterMetricsLab.bestBySilhouette(sweep)}, which is how many blobs were " +
            "generated. Note that inertia cannot do this — it falls monotonically with k by construction.",
        dots = clusterDots(blobs, three),
        profile = sweep.map {
            ProfileBar(it.silhouette.toFloat(), if (it.k == ClusterMetricsLab.bestBySilhouette(sweep)) 1 else 0, if (it.k == ClusterMetricsLab.bestBySilhouette(sweep)) Emphasis.ACTIVE else Emphasis.NORMAL)
        },
        profileLabel = "silhouette at k = 2..6",
        readout = "peak at k = ${ClusterMetricsLab.bestBySilhouette(sweep)}",
    )

    val rings = ClusterMetricsLab.rings
    val ringTwo = ClusterMetricsLab.kMeans(rings, 2)
    frames += CloudFrame(
        status = "Now the failure, and it is not subtle. Two concentric rings — obviously two clusters to any " +
            "human — clustered at the true k = ${ClusterMetricsLab.RING_TRUTH} score only " +
            "${"%.3f".format(ClusterMetricsLab.silhouetteScore(rings, ringTwo))}, because k-means cuts them in " +
            "half and neither half is compact.",
        dots = clusterDots(rings, ringTwo, ClusterMetricsLab.silhouettes(rings, ringTwo)),
        readout = "true structure, poor silhouette ${"%.3f".format(ClusterMetricsLab.silhouetteScore(rings, ringTwo))}",
    )

    val ringSweep = ClusterMetricsLab.ringSweep
    val ringBest = ClusterMetricsLab.bestBySilhouette(ringSweep)
    frames += CloudFrame(
        status = "Swept over k, silhouette picks k = $ringBest on the rings — " +
            ringSweep.joinToString(", ") { "k=${it.k} ${"%.2f".format(it.silhouette)}" } +
            " — and prefers it *confidently*. It is not confused; it is answering the question it was built to " +
            "answer, which is \"are these clusters compact and separated\" and not \"is this the right structure\".",
        dots = clusterDots(rings, ClusterMetricsLab.kMeans(rings, ringBest)),
        profile = ringSweep.map {
            ProfileBar(it.silhouette.toFloat(), if (it.k == ringBest) 2 else 0, if (it.k == ringBest) Emphasis.ACTIVE else Emphasis.NORMAL)
        },
        profileLabel = "silhouette at k = 2..6 on the rings",
        readout = "picks $ringBest, truth is ${ClusterMetricsLab.RING_TRUTH}",
    )

    frames += CloudFrame(
        status = "So read it as what it is: a compactness-and-separation score, valid for centroid-shaped " +
            "clusters and no others. On non-convex structure it will confidently prefer the wrong k, and the " +
            "per-point profile — not the average — is what shows you that something is wrong.",
        dots = clusterDots(rings, ringTwo, ClusterMetricsLab.silhouettes(rings, ringTwo)),
        profile = ClusterMetricsLab.silhouettes(rings, ringTwo).sortedDescending()
            .map { ProfileBar(it.toFloat().coerceIn(0f, 1f), 3, Emphasis.NORMAL) },
        profileLabel = "per-point silhouette on the true-but-unrewarded clustering",
        readout = "the average hides what the profile shows",
    )
    return frames
}

private fun daviesBouldinFrames(): List<CloudFrame> {
    val frames = mutableListOf<CloudFrame>()
    val blobs = ClusterMetricsLab.blobs
    val three = ClusterMetricsLab.kMeans(blobs, 3)
    val sweep = ClusterMetricsLab.blobSweep
    val best = ClusterMetricsLab.bestByDaviesBouldin(sweep)

    frames += CloudFrame(
        status = "Davies-Bouldin scores the same idea as silhouette from the cluster's side rather than the " +
            "point's: for each cluster, find the worst ratio of (its spread + another's spread) to the distance " +
            "between their centroids, then average those worst cases. Lower is better, and 0 is unreachable.",
        dots = clusterDots(blobs, three),
        centroids = (0..2).map { cluster ->
            val members = blobs.filterIndexed { index, _ -> three[index] == cluster }
            Dot(P(members.map { it.x }.average().toFloat(), members.map { it.y }.average().toFloat()), cluster, Emphasis.ACTIVE)
        },
        readout = "DB = ${"%.3f".format(ClusterMetricsLab.daviesBouldin(blobs, three))} at k = 3",
    )

    frames += CloudFrame(
        status = "The two indices are not the same computation and they do not have the same range — silhouette " +
            "runs −1 to 1 and higher is better, Davies-Bouldin runs 0 upward and lower is better — so a paper " +
            "quoting one cannot be compared against a paper quoting the other. On this dataset they agree: both " +
            "pick k = $best.",
        dots = clusterDots(blobs, three),
        profile = sweep.map {
            ProfileBar((it.daviesBouldin / sweep.maxOf { s -> s.daviesBouldin }).toFloat(), if (it.k == best) 1 else 0, if (it.k == best) Emphasis.ACTIVE else Emphasis.NORMAL)
        },
        profileLabel = "Davies-Bouldin at k = 2..6 (lower is better)",
        readout = "silhouette picks ${ClusterMetricsLab.bestBySilhouette(sweep)}, DB picks $best",
    )

    frames += CloudFrame(
        status = "Agreement here is not evidence that they are interchangeable — it is evidence that this dataset " +
            "is easy. Both indices are built on centroids and Euclidean spread, so they share the same " +
            "assumption, and when the assumption fails they fail *together*.",
        dots = clusterDots(blobs, ClusterMetricsLab.kMeans(blobs, 5)),
        profile = sweep.map { ProfileBar(it.silhouette.toFloat(), 2, Emphasis.NORMAL) } +
            sweep.map { ProfileBar((1 - it.daviesBouldin / sweep.maxOf { s -> s.daviesBouldin }).toFloat(), 1, Emphasis.NORMAL) },
        profileLabel = "silhouette, then inverted Davies-Bouldin — same shape",
        readout = "two readings of one assumption",
    )

    val rings = ClusterMetricsLab.rings
    val ringSweep = ClusterMetricsLab.ringSweep
    val ringBest = ClusterMetricsLab.bestByDaviesBouldin(ringSweep)
    frames += CloudFrame(
        status = "On the concentric rings, Davies-Bouldin picks k = $ringBest — " +
            ringSweep.joinToString(", ") { "k=${it.k} ${"%.2f".format(it.daviesBouldin)}" } +
            " — the same wrong answer silhouette gives, against a true structure of " +
            "${ClusterMetricsLab.RING_TRUTH}. Two independent-looking indices agreeing is exactly what shared " +
            "assumptions look like from outside.",
        dots = clusterDots(rings, ClusterMetricsLab.kMeans(rings, ringBest)),
        profile = ringSweep.map {
            ProfileBar((it.daviesBouldin / ringSweep.maxOf { s -> s.daviesBouldin }).toFloat(), if (it.k == ringBest) 2 else 0, if (it.k == ringBest) Emphasis.ACTIVE else Emphasis.NORMAL)
        },
        profileLabel = "Davies-Bouldin on the rings (lower is better)",
        readout = "both indices pick $ringBest; the answer is ${ClusterMetricsLab.RING_TRUTH}",
    )

    frames += CloudFrame(
        status = "The practical rule: use Davies-Bouldin the way you would use silhouette, expect them to agree, " +
            "and treat agreement as no information. If the clusters might not be convex, validate with something " +
            "that does not assume they are — a density-based algorithm's own diagnostics, or a labelled subset.",
        dots = clusterDots(rings, ClusterMetricsLab.kMeans(rings, ClusterMetricsLab.RING_TRUTH)),
        readout = "no internal index knows what a cluster should look like",
    )
    return frames
}

private val cloudConfigs = mapOf(
    "confusion_matrix" to CloudConfig(
        intro = "1,000 cases, 88 positive, one fitted logistic boundary — and the four cells every other metric in this category is a function of.",
        legend = listOf(
            CloudColors[1] to "True positive",
            CloudColors[2] to "False positive",
            CloudColors[3] to "False negative",
            CloudColors[0] to "True negative",
        ),
        build = ::confusionMatrixFrames,
    ),
    "accuracy" to CloudConfig(
        intro = "0.947 from the model, 0.912 from answering \"negative\" every time. The gap is what accuracy is worth on a 9% base rate.",
        legend = listOf(
            CloudColors[1] to "Correct",
            CloudColors[2] to "False positive",
            CloudColors[3] to "Missed",
            CloudColors[0] to "True negative",
        ),
        build = ::accuracyFrames,
    ),
    "precision_recall" to CloudConfig(
        intro = "The same model at two thresholds: perfect precision with 53 misses, or perfect recall with 325 false alarms.",
        legend = listOf(
            CloudColors[1] to "True positive",
            CloudColors[2] to "False positive",
            CloudColors[3] to "False negative",
            CloudColors[0] to "True negative",
        ),
        build = ::precisionRecallFrames,
    ),
    "f1_score" to CloudConfig(
        intro = "The harmonic mean, why it is harmonic, and the threshold sweep that finds F1 0.776 where the default gives 0.569.",
        legend = listOf(
            CloudColors[1] to "Precision side",
            CloudColors[3] to "Recall side",
            CloudColors[2] to "F1",
        ),
        build = ::f1Frames,
    ),
    "silhouette_score" to CloudConfig(
        intro = "Per-point compactness against separation. It finds k = 3 on three blobs and k = 6 on two concentric rings.",
        legend = listOf(
            CloudColors[0] to "Cluster",
            QueryColor to "Negative silhouette",
        ),
        build = ::silhouetteFrames,
    ),
    "davies_bouldin" to CloudConfig(
        intro = "The same assumption from the cluster's side. It agrees with silhouette on the easy dataset and makes the identical mistake on the hard one.",
        legend = listOf(
            CloudColors[0] to "Cluster",
            CloudColors[1] to "Centroid",
        ),
        build = ::daviesBouldinFrames,
    ),
    "min_max_normalization" to CloudConfig(
        intro = "Age and income on one distance metric: income is 99.99% of it. Both scalers, then the same k-NN scored on all three arrangements.",
        legend = listOf(
            CloudColors[0] to "Class 0",
            CloudColors[1] to "Class 1",
            QueryColor to "Contaminated fit",
        ),
        build = ::minMaxFrames,
    ),
    "z_score_standardization" to CloudConfig(
        intro = "The same comparison from the other side, including what one extreme value does to each scaler.",
        legend = listOf(
            CloudColors[0] to "Class 0",
            CloudColors[1] to "Class 1",
            QueryColor to "Contaminated fit",
        ),
        build = ::zScoreFrames,
    ),
    "smote" to CloudConfig(
        intro = "Interpolated minority points, the recall/precision trade they buy, and the cross-validation score you get for resampling before the split.",
        legend = listOf(
            CloudColors[0] to "Majority",
            CloudColors[1] to "Minority",
            QueryColor to "Synthetic",
        ),
        build = ::smoteFrames,
    ),
    "vector_databases" to CloudConfig(
        intro = "A real index over ${VectorDbLab.corpusSize} vectors: brute force, an IVF probed at a cell " +
            "boundary, HNSW's greedy walk on a graph that turns out to be disconnected, and the quantization " +
            "step that is coarser than the neighbours it has to rank.",
        legend = listOf(
            QueryColor to "Query",
            CloudColors[0] to "Cell / component",
            UnassignedColor to "Link",
        ),
        build = ::vectorDbFrames,
    ),
    "glove" to CloudConfig(
        intro = "The co-occurrence ratio GloVe is derived from, the weighting that keeps \"the\" from dominating, " +
            "and the vectors it fits — plotted as their first two principal components.",
        legend = listOf(
            CloudColors[0] to "Royalty",
            CloudColors[1] to "People / objects",
            QueryColor to "Analogy offset",
        ),
        build = ::gloveFrames,
    ),
    "elmo" to CloudConfig(
        intro = "One word, four sentences, four vectors: the same \"bank\" contextualised, with the sense " +
            "separation measured against the single static vector it replaces.",
        legend = listOf(
            CloudColors[0] to "River sense",
            CloudColors[1] to "Money sense",
            QueryColor to "Static vector",
        ),
        build = ::elmoFrames,
    ),
    "cosine_similarity" to CloudConfig(
        intro = "Four documents in a two-term space: the same document at two lengths, one with the opposite " +
            "emphasis, and a query — ranked by angle and by distance, which disagree.",
        legend = listOf(
            CloudColors[0] to "short / long",
            CloudColors[2] to "theory",
            QueryColor to "Query",
        ),
        build = ::cosineFrames,
    ),
    "kernel_pca" to CloudConfig(
        intro = "Two concentric rings that no straight axis can separate, then the same data seen through an RBF kernel — and what happens when γ is wrong.",
        legend = listOf(CloudColors[0] to "Inner ring", CloudColors[1] to "Outer ring", QueryColor to "Kernel width"),
        build = ::kernelPcaFrames,
    ),
    "incremental_pca" to CloudConfig(
        intro = "PCA fitted four rows at a time, with the angle between the running component and the full-batch answer measured at every step.",
        legend = listOf(CloudColors[0] to "Seen", UnassignedColor to "Not yet seen", AxisColor to "Running PC1"),
        build = ::incrementalPcaFrames,
    ),
    "svd" to CloudConfig(
        intro = "The same axes PCA finds, taken straight from the data matrix — plus the optimality of truncation, and the centring trap.",
        legend = listOf(CloudColors[0] to "Data", CloudColors[2] to "Rank-1 approximation", AxisColor to "v₁"),
        build = ::svdFrames,
    ),
    "ica" to CloudConfig(
        intro = "Two uniform sources put through a non-orthogonal mix: why PCA structurally cannot undo it, and how FastICA does.",
        legend = listOf(CloudColors[0] to "Observed mixture", CloudColors[2] to "Recovered sources", QueryColor to "ICA directions"),
        build = ::icaFrames,
    ),
    "factor_analysis" to CloudConfig(
        intro = "One hidden factor behind three variables, one of them mostly noise — scored on the correlations both models are trying to explain.",
        legend = listOf(CloudColors[0] to "x₁ vs x₂", CloudColors[1] to "Variable 2", CloudColors[2] to "Variable 3"),
        build = ::factorAnalysisFrames,
    ),
    "tsne" to CloudConfig(
        intro = "Gradient descent on KL(P‖Q) over three deliberately uneven clusters — and a measurement of exactly which distances the map destroys.",
        legend = listOf(CloudColors[0] to "Tight cluster A", CloudColors[1] to "Tight cluster B", CloudColors[2] to "Loose cluster"),
        build = ::tsneFrames,
    ),
    "umap" to CloudConfig(
        intro = "The fuzzy k-NN graph, the force-directed layout, and a scored comparison against t-SNE on identical points.",
        legend = listOf(CloudColors[0] to "Tight cluster A", CloudColors[1] to "Tight cluster B", CloudColors[2] to "Loose cluster"),
        build = ::umapFrames,
    ),
    "lle" to CloudConfig(
        intro = "A spiral unrolled by local reconstruction weights, scored against PCA on the same points — and the k that breaks it.",
        legend = listOf(CloudColors[0] to "Curve", CloudColors[2] to "LLE coordinate", QueryColor to "Focus neighbourhood"),
        build = ::lleFrames,
    ),
    "k_medians" to CloudConfig(
        intro = "The same alternating loop as k-means over data with two strays, run once with means and once with medians.",
        legend = listOf(CloudColors[0] to "Cluster 0", CloudColors[1] to "Cluster 1", QueryColor to "Outlier"),
        build = ::k_MediansFrames,
    ),
    "mean_shift" to CloudConfig(
        intro = "Every point as a seed climbing the density gradient, with the cluster count falling out of how many peaks they reach.",
        legend = listOf(CloudColors[0] to "Cluster", QueryColor to "Bandwidth window"),
        build = ::meanShiftFrames,
    ),
    "optics" to CloudConfig(
        intro = "The reachability profile — clusters as valleys, gaps as peaks — over data whose two clusters have very different densities.",
        legend = listOf(CloudColors[0] to "Cluster 0", CloudColors[1] to "Cluster 1", QueryColor to "Cluster boundary"),
        build = ::opticsFrames,
    ),
    "hdbscan" to CloudConfig(
        intro = "Core distances, then the whole eps hierarchy at once, then the clusters that persisted across it.",
        legend = listOf(CloudColors[0] to "Dense cluster", CloudColors[1] to "Sparse cluster", UnassignedColor to "Noise"),
        build = ::hdbscanFrames,
    ),
    "birch" to CloudConfig(
        intro = "One streaming pass compressing points into clustering features, then a normal algorithm run on those summaries.",
        legend = listOf(CloudColors[0] to "Cluster", AxisColor to "CF radius"),
        build = ::birchFrames,
    ),
    "affinity_propagation" to CloudConfig(
        intro = "Exemplars emerging from message passing, and the preference parameter that decides how many of them there are.",
        legend = listOf(CloudColors[0] to "Cluster", QueryColor to "Exemplar"),
        build = ::affinityPropagationFrames,
    ),
    "spectral_clustering" to CloudConfig(
        intro = "Two interleaved crescents: k-means failing on them, then the similarity graph and the eigenvector that cuts it correctly.",
        legend = listOf(CloudColors[0] to "Cluster 0", CloudColors[1] to "Cluster 1", UnassignedColor to "Affinity edge"),
        build = ::spectralFrames,
    ),
    "gmm" to CloudConfig(
        intro = "EM fitting full covariances to one elongated and one round cluster — and why k-means is the special case where they are spherical.",
        legend = listOf(CloudColors[0] to "Component 0", CloudColors[1] to "Component 1", QueryColor to "Uncertain point"),
        build = ::gmmFrames,
    ),
    "adaboost" to CloudConfig(
        intro = "Four rounds of real AdaBoost: the best stump under the current weights, the α it earns, and the re-weighting that decides what the next stump sees.",
        legend = listOf(
            CloudColors[0] to "Class 0",
            CloudColors[1] to "Class 1",
            QueryColor to "Misclassified / up-weighted",
        ),
        build = ::adaBoostFrames,
    ),
    "bagging" to CloudConfig(
        intro = "Bootstrap sampling with the out-of-bag rows called out, and the one line that separates plain bagging from a random forest.",
        legend = listOf(
            CloudColors[0] to "Class 0",
            CloudColors[1] to "Class 1",
            UnassignedColor to "Out-of-bag",
        ),
        build = ::baggingFrames,
    ),
    "extra_trees" to CloudConfig(
        intro = "The exhaustively-searched best split, then three thresholds drawn at random — each worse on its own, and the reason the ensemble is better.",
        legend = listOf(
            CloudColors[0] to "Class 0",
            CloudColors[1] to "Class 1",
            AxisColor to "Split",
        ),
        build = ::extraTreesFrames,
    ),
    "voting" to CloudConfig(
        intro = "Three models, one query point, and the case where counting votes and averaging probabilities give different answers.",
        legend = listOf(
            CloudColors[0] to "Class 0",
            CloudColors[1] to "Class 1",
            QueryColor to "Query point",
        ),
        build = ::votingFrames,
    ),
    "stacking" to CloudConfig(
        intro = "Base models, then the out-of-fold construction that makes the meta-learner's training set honest — the step stacking fails without.",
        legend = listOf(
            CloudColors[0] to "Class 0",
            CloudColors[1] to "Class 1",
            QueryColor to "Held-out fold",
        ),
        build = ::stackingFrames,
    ),
    "isolation_forest" to CloudConfig(
        intro = "Random cuts isolating an outlier and an interior point, with the split count as the anomaly score.",
        legend = listOf(
            CloudColors[0] to "Normal",
            QueryColor to "Being isolated",
            AxisColor to "Random cut",
        ),
        build = ::isolationForestFrames,
    ),
    "mcmc" to CloudConfig(
        intro = "Metropolis-Hastings on a curved, correlated posterior: the accept/reject rule, burn-in, and both ways a badly chosen step size fails.",
        legend = listOf(
            CloudColors[0] to "Chain",
            QueryColor to "Proposal",
            UnassignedColor to "Rejected / burn-in",
        ),
        build = ::mcmcFrames,
    ),
    "random_forest" to CloudConfig(
        intro = "Three trees, three bootstrap samples, three different cuts — then a vote on the yellow query " +
            "point. Faded points are out-of-bag for the tree being grown.",
        legend = listOf(
            CloudColors[0] to "Class 0",
            CloudColors[1] to "Class 1",
            QueryColor to "Query point",
        ),
        build = ::randomForestFrames,
    ),
    "gradient_boosting" to CloudConfig(
        intro = "Four boosting rounds on a regression problem. The dashed lines are residuals — each new stump " +
            "is fitted to those, not to the target, and added at a shrunken learning rate.",
        legend = listOf(
            CloudColors[2] to "Ensemble prediction",
            QueryColor to "Residual",
            CloudColors[0] to "Data",
        ),
        build = ::gradientBoostingFrames,
    ),
    "bias_variance" to CloudConfig(
        intro = "One dataset fitted at three capacities, with the flexible model refitted on resamples so the " +
            "variance is visible as movement rather than asserted in prose.",
        legend = listOf(
            CloudColors[1] to "High bias",
            CloudColors[3] to "High variance",
            CloudColors[2] to "Balanced",
        ),
        build = ::biasVarianceFrames,
    ),
    "regularization" to CloudConfig(
        intro = "The same overfit curve as λ increases. Watch which structure the penalty gives up first — the " +
            "sharp wiggles cost the most weight for the least error reduction.",
        legend = listOf(
            CloudColors[3] to "λ = 0 (overfit)",
            CloudColors[2] to "Penalized fit",
            CloudColors[0] to "Data",
        ),
        build = ::regularizationFrames,
    ),
    "model_evaluation" to CloudConfig(
        intro = "Two overlapping classes and a moving decision threshold. Precision, recall and F1 are recomputed " +
            "at each position, so the tradeoff is arithmetic rather than assertion.",
        legend = listOf(
            CloudColors[0] to "Negative",
            CloudColors[1] to "Positive",
            QueryColor to "Misclassified",
        ),
        build = ::modelEvaluationFrames,
    ),
    "diffusion_models" to CloudConfig(
        intro = "A ring of data destroyed by the fixed forward process, then walked back by the learned reverse " +
            "one. The forward half involves no learning at all.",
        legend = listOf(
            CloudColors[2] to "Data manifold",
            CloudColors[0] to "Noised sample",
            CloudColors[1] to "Denoising",
        ),
        build = ::diffusionFrames,
    ),
    "kmeans" to CloudConfig(
        intro = "k-means alternates two steps: assign each point to the nearest centroid, then move each centroid to " +
            "the mean of what it captured. The seeding here is poor on purpose.",
        legend = clusterLegend,
        build = ::kMeansFrames,
    ),
    "dbscan" to CloudConfig(
        intro = "DBSCAN clusters by density instead of by count. Watch the ε circle decide whether each point is a " +
            "core point, a border point, or noise.",
        legend = listOf(
            CloudColors[0] to "Cluster A",
            CloudColors[1] to "Cluster B",
            UnassignedColor to "Noise",
        ),
        build = ::dbscanFrames,
    ),
    "hierarchical_clustering" to CloudConfig(
        intro = "Agglomerative clustering with single linkage: merge the two closest clusters, repeat. The merge " +
            "order is the dendrogram, and where you cut it is where k comes from.",
        legend = clusterLegend,
        build = ::hierarchicalFrames,
    ),
    "knn" to CloudConfig(
        intro = "k-NN with k = 5. There is no model to fit — the radius grows until it holds five neighbours, and " +
            "they vote.",
        legend = classLegend,
        build = ::knnFrames,
    ),
    "naive_bayes" to CloudConfig(
        intro = "Naive Bayes fits a mean and variance per feature per class, then scores the query under each class. " +
            "The rings show one standard deviation in x.",
        legend = classLegend,
        build = ::naiveBayesFrames,
    ),
    "decision_trees" to CloudConfig(
        intro = "A depth-2 tree grown greedily. Each split is the threshold that lowers weighted gini impurity the " +
            "most; the shaded boxes are the leaves it ends up with.",
        legend = listOf(
            CloudColors[0] to "Class 0",
            CloudColors[1] to "Class 1",
            AxisColor to "Split",
        ),
        build = ::decisionTreeFrames,
    ),
    "pca" to CloudConfig(
        intro = "PCA on a correlated cloud: centre the data, take the top eigenvector of the covariance matrix, and " +
            "project onto it. The dashed residuals are what the first component discards.",
        legend = listOf(
            CloudColors[0] to "Data",
            CloudColors[2] to "Projection",
            AxisColor to "PC1",
        ),
        build = ::pcaFrames,
    ),
    "kd_tree" to CloudConfig(
        intro = "Splitting the plane instead of sorting the points, then a nearest-neighbour query that prunes " +
            "most of the cloud without measuring a distance to it.",
        legend = listOf(
            CloudColors[0] to "Point",
            CloudColors[2] to "Nearest",
            AxisColor to "Split",
        ),
        build = ::kdTreeFrames,
    ),
    "closest_pair_of_points" to CloudConfig(
        intro = "Divide and conquer on the plane: solve both halves, then check only the strip near the dividing " +
            "line. Comparison counts are shown against brute force at each stage.",
        legend = listOf(
            CloudColors[0] to "Left half",
            CloudColors[1] to "Right half",
            AxisColor to "Split · strip",
        ),
        build = ::closestPairFrames,
    ),
    "monte_carlo_method" to CloudConfig(
        intro = "Estimating π by throwing darts at a square. The estimate is recomputed at four sample sizes so " +
            "the 1/√n error rate is visible rather than asserted.",
        legend = listOf(
            CloudColors[0] to "Inside arc",
            CloudColors[1] to "Outside",
            AxisColor to "Quarter circle",
        ),
        build = ::monteCarloFrames,
    ),
    "convex_hull" to CloudConfig(
        intro = "Graham scan on twelve points: sort by polar angle, then push and pop on the sign of a cross product. " +
            "The five pops are exactly the five interior points. Jarvis march's cost model closes it out.",
        legend = listOf(
            CloudColors[0] to "On the hull",
            QueryColor to "Under test",
            CloudColors[3] to "Popped — interior",
        ),
        build = ::convexHullFrames,
    ),
    "rotating_calipers" to CloudConfig(
        intro = "Two parallel lines gripping the hull and rotating together. Every stop is an antipodal pair, the " +
            "diameter is the longest of them, and the opposite pointer never once moves backwards.",
        legend = listOf(
            CloudColors[0] to "Hull",
            CloudColors[1] to "Current edge",
            QueryColor to "Antipodal pair",
        ),
        build = ::rotatingCalipersFrames,
    ),
    "polygon_area" to CloudConfig(
        intro = "The shoelace sum over a non-convex polygon, one edge at a time, with the triangle each term measures " +
            "drawn back to the origin — so the negative terms are seen cancelling rather than asserted to.",
        legend = listOf(
            CloudColors[2] to "Positive term",
            CloudColors[3] to "Negative term",
            QueryColor to "Current vertex",
        ),
        build = ::polygonAreaFrames,
    ),
    "line_intersection" to CloudConfig(
        intro = "Five segment pairs run through the same four cross products: a proper crossing, a clear miss, an " +
            "endpoint touch, a collinear overlap and a collinear miss. The last three are where naive tests fail.",
        legend = listOf(
            CloudColors[0] to "Segment p",
            CloudColors[2] to "Intersects",
            CloudColors[3] to "No intersection",
        ),
        build = ::lineIntersectionFrames,
    ),
)

private fun cloudConfigFor(topicId: String): CloudConfig =
    cloudConfigs[topicId] ?: cloudConfigs.getValue("kmeans")

internal val pointCloudTopicIds: Set<String> get() = cloudConfigs.keys

internal fun pointCloudFrameCount(topicId: String): Int {
    val frames = cloudConfigFor(topicId).build()
    frames.forEach { frame ->
        // The canvas maps [0,1] onto the plot area; anything outside silently draws off-frame.
        val stray = (frame.dots + frame.centroids).map { it.point }.filter { it.x !in -0.05f..1.05f || it.y !in -0.05f..1.05f }
        require(stray.isEmpty()) { "$topicId plots ${stray.size} point(s) outside the unit square" }
    }
    return frames.size
}

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun PointCloudSection(topicId: String) {
    val config = remember(topicId) { cloudConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 750f)
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

            ScatterCanvas(frame)

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
                config.legend.forEach { (color, label) -> CloudLegend(color, label) }
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun CloudLegend(color: Color, label: String) {
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
private fun ScatterCanvas(frame: CloudFrame) {
    val surfaceTint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val outline = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .background(surfaceTint, RoundedCornerShape(14.dp))
            .padding(8.dp),
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(250.dp)) {
            val pad = 10.dp.toPx()
            val w = size.width - 2 * pad
            val h = size.height - 2 * pad
            // y is flipped so the plot reads like a chart rather than like screen coordinates.
            fun place(p: P) = Offset(pad + p.x * w, pad + (1f - p.y) * h)
            fun scale(r: Float) = r * minOf(w, h)

            frame.regions.forEach { region ->
                val topLeft = place(P(region.x0, region.y1))
                val bottomRight = place(P(region.x1, region.y0))
                drawRect(
                    color = region.color,
                    topLeft = topLeft,
                    size = Size(bottomRight.x - topLeft.x, bottomRight.y - topLeft.y),
                )
            }

            frame.rings.forEach { ring ->
                drawCircle(
                    color = ring.color.copy(alpha = 0.7f),
                    radius = scale(ring.radius),
                    center = place(ring.center),
                    style = Stroke(width = 2.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f))),
                )
            }

            frame.ellipses.forEach { ellipse ->
                ellipse.zipWithNext().forEach { (a, b) ->
                    drawLine(color = AxisColor, start = place(a), end = place(b), strokeWidth = 3f)
                }
            }

            frame.segments.forEach { segment ->
                drawLine(
                    color = segment.color,
                    start = place(segment.from),
                    end = place(segment.to),
                    strokeWidth = if (segment.dashed) 2.5f else 3.5f,
                    pathEffect = if (segment.dashed) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null,
                )
            }

            frame.dots.forEach { dot ->
                val center = place(dot.point)
                val color = when (dot.emphasis) {
                    Emphasis.QUERY -> QueryColor
                    Emphasis.FADED -> groupColor(dot.group).copy(alpha = 0.3f)
                    else -> groupColor(dot.group)
                }
                val radius = if (dot.emphasis == Emphasis.ACTIVE || dot.emphasis == Emphasis.QUERY) 9f else 7f
                drawCircle(color = color, radius = radius, center = center)
                if (dot.emphasis == Emphasis.ACTIVE || dot.emphasis == Emphasis.QUERY) {
                    drawCircle(color = outline, radius = radius, center = center, style = Stroke(width = 2.5f))
                }
            }

            // Centroids sit on top of the data and are drawn as rings so they never read as points.
            frame.centroids.forEach { centroid ->
                val center = place(centroid.point)
                drawCircle(color = groupColor(centroid.group), radius = 11f, center = center, style = Stroke(width = 4f))
                drawCircle(color = outline, radius = 4f, center = center)
            }
        }
    }

    if (frame.profile.isNotEmpty()) {
        ProfileStrip(frame.profile, frame.profileLabel)
    }
}

// The bar strip under the scatter. Heights are normalized against the tallest bar in the frame, so
// a profile with one huge spike still shows structure in the rest of it.
@Composable
private fun ProfileStrip(bars: List<ProfileBar>, label: String?) {
    val surfaceTint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

    Column(modifier = Modifier.padding(top = 10.dp)) {
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(surfaceTint, RoundedCornerShape(12.dp))
                .padding(8.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                val peak = bars.maxOfOrNull { it.height }?.takeIf { it > 1e-6f } ?: 1f
                val slot = size.width / bars.size
                bars.forEachIndexed { i, bar ->
                    val barHeight = (bar.height / peak) * size.height
                    val color = when (bar.emphasis) {
                        Emphasis.QUERY -> QueryColor
                        Emphasis.FADED -> groupColor(bar.group).copy(alpha = 0.3f)
                        else -> groupColor(bar.group)
                    }
                    drawRect(
                        color = color,
                        topLeft = Offset(i * slot + slot * 0.15f, size.height - barHeight),
                        size = Size(slot * 0.7f, barHeight),
                    )
                }
            }
        }
    }
}
