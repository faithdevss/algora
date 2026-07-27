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
)

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

private val cloudConfigs = mapOf(
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
)

private fun cloudConfigFor(topicId: String): CloudConfig =
    cloudConfigs[topicId] ?: cloudConfigs.getValue("kmeans")

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
}
