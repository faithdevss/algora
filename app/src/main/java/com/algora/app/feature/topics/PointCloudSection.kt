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

// ── MCMC: Metropolis-Hastings walking a banana-shaped posterior ──────────────
// A correlated, curved target, because a spherical Gaussian would make every proposal look good and
// hide the two things worth seeing: burn-in, and what happens when the step size is wrong.

private fun bananaLogDensity(x: Float, y: Float): Double {
    // Rosenbrock-style: narrow curved ridge. Hard for a naive random walk, which is the point.
    val a = (x - 0.0) / 1.4
    val b = (y - 0.35 * (x * x - 2.0)) / 0.55
    return -0.5 * (a * a + b * b)
}

private fun mcmcFrames(): List<CloudFrame> {
    val rng = Lcg(20240719)
    val frames = mutableListOf<CloudFrame>()

    // Background contour: sample the target on a lattice and keep the high-density cells, so the
    // chain has something visible to be exploring.
    val contour = buildList {
        var gx = -3.4f
        while (gx <= 3.4f) {
            var gy = -2.6f
            while (gy <= 3.4f) {
                if (bananaLogDensity(gx, gy) > -2.2) add(Dot(P(gx, gy), -1, Emphasis.FADED))
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
            dots = contour + Dot(start, 0, Emphasis.QUERY),
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
                dots = contour + chain.take(step).map { Dot(it, 0, Emphasis.NORMAL) },
                centroids = listOf(Dot(proposed, if (moved) 2 else 3, Emphasis.QUERY)),
                segments = listOf(Segment(current, proposed, if (moved) CloudColors[2] else UnassignedColor, dashed = !moved)),
            ),
        )
    }

    val burnIn = 60
    frames.add(
        CloudFrame(
            status = "After $burnIn steps the chain has found the ridge, but those early samples were drawn while it was still travelling. They are not from the posterior and have to be discarded — that is burn-in, and forgetting it biases everything downstream.",
            dots = contour + chain.take(burnIn).map { Dot(it, 3, Emphasis.FADED) },
            segments = chain.take(burnIn).zipWithNext().map { (a, b) -> Segment(a, b, UnassignedColor) },
            readout = "burn-in: $burnIn samples discarded",
        ),
    )

    frames.add(
        CloudFrame(
            status = "Post burn-in, the chain traces the target's shape. Acceptance rate over the whole run was ${"%.0f".format(accepted * 100f / accepts.size)}% — for a random-walk proposal the rule of thumb is roughly 25%, and being far from it in either direction means the step size is wrong.",
            dots = contour + chain.drop(burnIn).map { Dot(it, 0, Emphasis.NORMAL) },
            segments = chain.drop(burnIn).zipWithNext().map { (a, b) -> Segment(a, b, CloudColors[0]) },
            readout = "acceptance ${"%.0f".format(accepted * 100f / accepts.size)}%",
        ),
    )

    val (tiny, tinyAccepted, tinyAccepts) = runChain(0.06f, 200, P(0f, -0.6f))
    frames.add(
        CloudFrame(
            status = "Step size too small: ${"%.0f".format(tinyAccepted * 100f / tinyAccepts.size)}% of proposals accepted, which sounds excellent and is not. The chain barely moves, consecutive samples are almost identical, and the effective sample size is a small fraction of the ${tiny.size} iterations.",
            dots = contour + tiny.map { Dot(it, 1, Emphasis.NORMAL) },
            segments = tiny.zipWithNext().map { (a, b) -> Segment(a, b, CloudColors[1]) },
            readout = "acceptance ${"%.0f".format(tinyAccepted * 100f / tinyAccepts.size)}% · barely explores",
        ),
    )

    val (huge, hugeAccepted, hugeAccepts) = runChain(8.0f, 200, P(0f, -0.6f))
    frames.add(
        CloudFrame(
            status = "Step size too large: ${"%.0f".format(hugeAccepted * 100f / hugeAccepts.size)}% accepted. Almost every proposal lands somewhere implausible and is rejected, so the chain sticks in place for long stretches — the opposite failure, with the same symptom of highly correlated samples.",
            dots = contour + huge.map { Dot(it, 3, Emphasis.NORMAL) },
            segments = huge.zipWithNext().map { (a, b) -> Segment(a, b, CloudColors[3]) },
            readout = "acceptance ${"%.0f".format(hugeAccepted * 100f / hugeAccepts.size)}% · sticks",
        ),
    )

    frames.add(
        CloudFrame(
            status = "This is why tuning matters and why modern samplers avoid it: Hamiltonian Monte Carlo uses the gradient to propose distant points that are still likely, and NUTS picks its own trajectory length. Both are the same accept/reject skeleton with a better proposal.",
            dots = contour + chain.drop(burnIn).map { Dot(it, 0, Emphasis.NORMAL) },
            readout = "${chain.size - burnIn} usable samples",
        ),
    )
    return frames
}

private val cloudConfigs = mapOf(
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
