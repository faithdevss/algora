package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// ── Clustering algorithms behind the B5 labs ─────────────────────────────────
// Every clustering shown on those topics is produced by the real algorithm here rather than
// hand-placed. Datasets are 30-60 points, so the O(n²) distance matrices these need are free.

internal class Pt(val x: Float, val y: Float)

internal fun dist(a: Pt, b: Pt): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

internal fun manhattan(a: Pt, b: Pt): Float = abs(a.x - b.x) + abs(a.y - b.y)

// ── k-means vs k-medians ─────────────────────────────────────────────────────
// Same alternating assign/update loop; the only difference is what "centre" means and which
// distance drives the assignment. That difference is the entire topic.

internal class CentroidStep(val assignment: IntArray, val centres: List<Pt>)

internal fun lloyd(
    points: List<Pt>,
    initial: List<Pt>,
    iterations: Int,
    useMedian: Boolean,
): List<CentroidStep> {
    var centres = initial
    val steps = mutableListOf<CentroidStep>()

    repeat(iterations) {
        val assignment = IntArray(points.size) { i ->
            centres.indices.minByOrNull {
                if (useMedian) manhattan(points[i], centres[it]) else dist(points[i], centres[it])
            } ?: 0
        }
        steps.add(CentroidStep(assignment.copyOf(), centres.toList()))

        centres = centres.indices.map { k ->
            val members = points.filterIndexed { i, _ -> assignment[i] == k }
            if (members.isEmpty()) centres[k]
            else if (useMedian) Pt(median(members.map { it.x }), median(members.map { it.y }))
            else Pt(members.map { it.x }.average().toFloat(), members.map { it.y }.average().toFloat())
        }
    }
    val finalAssignment = IntArray(points.size) { i ->
        centres.indices.minByOrNull {
            if (useMedian) manhattan(points[i], centres[it]) else dist(points[i], centres[it])
        } ?: 0
    }
    steps.add(CentroidStep(finalAssignment, centres.toList()))
    return steps
}

internal fun median(values: List<Float>): Float {
    if (values.isEmpty()) return 0f
    val sorted = values.sorted()
    val mid = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2f
}

// ── Mean shift ───────────────────────────────────────────────────────────────
// Each point climbs the density gradient by repeatedly moving to the kernel-weighted mean of its
// neighbours. No k — the number of clusters is however many modes the points converge onto.

internal fun meanShiftStep(seeds: List<Pt>, points: List<Pt>, bandwidth: Float): List<Pt> =
    seeds.map { seed ->
        var sumX = 0.0
        var sumY = 0.0
        var weight = 0.0
        points.forEach { p ->
            val d = dist(seed, p)
            val w = exp(-(d * d) / (2.0 * bandwidth * bandwidth))
            sumX += w * p.x
            sumY += w * p.y
            weight += w
        }
        if (weight < 1e-9) seed else Pt((sumX / weight).toFloat(), (sumY / weight).toFloat())
    }

internal fun mergeModes(modes: List<Pt>, tolerance: Float): List<Int> {
    val labels = IntArray(modes.size) { -1 }
    val centres = mutableListOf<Pt>()
    modes.forEachIndexed { i, m ->
        val existing = centres.indexOfFirst { dist(it, m) < tolerance }
        if (existing >= 0) {
            labels[i] = existing
        } else {
            centres.add(m)
            labels[i] = centres.lastIndex
        }
    }
    return labels.toList()
}

// ── DBSCAN core machinery, shared by OPTICS and HDBSCAN ──────────────────────

internal fun coreDistance(points: List<Pt>, index: Int, minPts: Int): Float {
    val distances = points.indices.filter { it != index }.map { dist(points[index], points[it]) }.sorted()
    return if (distances.size >= minPts - 1) distances[minPts - 2] else Float.MAX_VALUE
}

internal class OpticsResult(
    val order: List<Int>,
    val reachability: List<Float>,
)

// OPTICS produces an ordering plus a reachability value per point. Valleys in that sequence are
// clusters, and — unlike DBSCAN — clusters at different densities appear as valleys of different
// depths rather than forcing one eps to serve both.
internal fun optics(points: List<Pt>, minPts: Int, eps: Float): OpticsResult {
    val n = points.size
    val processed = BooleanArray(n)
    val reachability = FloatArray(n) { Float.MAX_VALUE }
    val order = mutableListOf<Int>()
    val outReach = mutableListOf<Float>()

    fun neighbours(i: Int) = (0 until n).filter { it != i && dist(points[i], points[it]) <= eps }

    for (start in 0 until n) {
        if (processed[start]) continue
        val seeds = sortedSetOf<Int>(compareBy({ reachability[it] }, { it }))

        var current = start
        while (true) {
            processed[current] = true
            order.add(current)
            outReach.add(if (reachability[current] == Float.MAX_VALUE) 0f else reachability[current])

            val core = coreDistance(points, current, minPts)
            if (core != Float.MAX_VALUE) {
                neighbours(current).forEach { nb ->
                    if (processed[nb]) return@forEach
                    val newReach = max(core, dist(points[current], points[nb]))
                    if (newReach < reachability[nb]) {
                        seeds.remove(nb)
                        reachability[nb] = newReach
                        seeds.add(nb)
                    }
                }
            }
            if (seeds.isEmpty()) break
            current = seeds.first()
            seeds.remove(current)
        }
    }
    return OpticsResult(order, outReach)
}

// ── Hierarchical clustering, both directions ─────────────────────────────────

internal class MergeStep(val left: List<Int>, val right: List<Int>, val distance: Float)

// Agglomerative: start with singletons, merge the closest pair. Complete linkage keeps the clusters
// compact, which makes the dendrogram heights easy to read.
internal fun agglomerative(points: List<Pt>): List<MergeStep> {
    var clusters = points.indices.map { listOf(it) }
    val steps = mutableListOf<MergeStep>()

    while (clusters.size > 1) {
        var bestA = 0
        var bestB = 1
        var bestD = Float.MAX_VALUE
        for (a in clusters.indices) {
            for (b in a + 1 until clusters.size) {
                val d = clusters[a].maxOf { i -> clusters[b].maxOf { j -> dist(points[i], points[j]) } }
                if (d < bestD) { bestD = d; bestA = a; bestB = b }
            }
        }
        steps.add(MergeStep(clusters[bestA], clusters[bestB], bestD))
        val merged = clusters[bestA] + clusters[bestB]
        clusters = clusters.filterIndexed { i, _ -> i != bestA && i != bestB } + listOf(merged)
    }
    return steps
}

internal class SplitStep(val parent: List<Int>, val left: List<Int>, val right: List<Int>, val diameter: Float)

// Divisive: start with everything in one cluster and split the least cohesive one. The exact version
// is exponential, so real implementations approximate — here by 2-means on the chosen cluster, which
// is exactly what DIANA-style practical implementations do.
internal fun divisive(points: List<Pt>, splits: Int): List<SplitStep> {
    var clusters = listOf(points.indices.toList())
    val steps = mutableListOf<SplitStep>()

    fun diameter(cluster: List<Int>): Float {
        if (cluster.size < 2) return 0f
        var d = 0f
        for (a in cluster.indices) {
            for (b in a + 1 until cluster.size) {
                d = max(d, dist(points[cluster[a]], points[cluster[b]]))
            }
        }
        return d
    }

    repeat(splits) {
        val target = clusters.filter { it.size > 1 }.maxByOrNull { diameter(it) } ?: return@repeat
        // Seed the 2-means with the two furthest-apart members of the cluster.
        var seedA = target[0]
        var seedB = target[1]
        var best = 0f
        for (a in target.indices) {
            for (b in a + 1 until target.size) {
                val d = dist(points[target[a]], points[target[b]])
                if (d > best) { best = d; seedA = target[a]; seedB = target[b] }
            }
        }
        var centreA = points[seedA]
        var centreB = points[seedB]
        var left = listOf<Int>()
        var right = listOf<Int>()
        repeat(12) {
            left = target.filter { dist(points[it], centreA) <= dist(points[it], centreB) }
            right = target.filter { it !in left }
            if (left.isNotEmpty()) {
                centreA = Pt(left.map { points[it].x }.average().toFloat(), left.map { points[it].y }.average().toFloat())
            }
            if (right.isNotEmpty()) {
                centreB = Pt(right.map { points[it].x }.average().toFloat(), right.map { points[it].y }.average().toFloat())
            }
        }
        if (left.isEmpty() || right.isEmpty()) return@repeat
        steps.add(SplitStep(target, left, right, diameter(target)))
        clusters = clusters.filter { it != target } + listOf(left, right)
    }
    return steps
}

// ── Gaussian mixture, fitted by EM ───────────────────────────────────────────

internal class GmmComponent(
    val meanX: Double,
    val meanY: Double,
    val varX: Double,
    val varY: Double,
    val covXY: Double,
    val weight: Double,
) {
    private val det = max(1e-9, varX * varY - covXY * covXY)

    fun density(x: Float, y: Float): Double {
        val dx = x - meanX
        val dy = y - meanY
        val quad = (varY * dx * dx - 2 * covXY * dx * dy + varX * dy * dy) / det
        return weight * exp(-0.5 * quad) / (2 * Math.PI * sqrt(det))
    }

    // Closed-form 2x2 eigendecomposition, as a closed polyline at the given Mahalanobis radius.
    fun ellipse(radius: Double, segments: Int = 48): List<Pt> {
        val trace = varX + varY
        val disc = sqrt(max(0.0, trace * trace / 4.0 - det))
        val l1 = trace / 2.0 + disc
        val l2 = trace / 2.0 - disc
        val angle = if (abs(covXY) < 1e-12) 0.0 else Math.atan2(l1 - varX, covXY)
        val rx = radius * sqrt(max(1e-9, l1))
        val ry = radius * sqrt(max(1e-9, l2))
        return (0..segments).map { i ->
            val t = 2.0 * Math.PI * i / segments
            val px = rx * cos(t)
            val py = ry * sin(t)
            Pt(
                (meanX + px * cos(angle) - py * sin(angle)).toFloat(),
                (meanY + px * sin(angle) + py * cos(angle)).toFloat(),
            )
        }
    }
}

internal class GmmStep(val components: List<GmmComponent>, val responsibilities: Array<DoubleArray>)

internal fun fitGmm(points: List<Pt>, initial: List<Pt>, iterations: Int): List<GmmStep> {
    var components = initial.map {
        GmmComponent(it.x.toDouble(), it.y.toDouble(), 0.02, 0.02, 0.0, 1.0 / initial.size)
    }
    val steps = mutableListOf<GmmStep>()

    repeat(iterations) {
        // E step: responsibility of each component for each point.
        val resp = Array(points.size) { i ->
            val densities = components.map { it.density(points[i].x, points[i].y) }
            val total = densities.sum().takeIf { it > 1e-300 } ?: 1.0
            DoubleArray(components.size) { k -> densities[k] / total }
        }
        steps.add(GmmStep(components, resp))

        // M step: weighted mean and full covariance per component — the covariance is what k-means
        // has no equivalent of, and why a mixture can fit an elongated cluster.
        components = components.indices.map { k ->
            var nk = 0.0
            var mx = 0.0
            var my = 0.0
            points.indices.forEach { i ->
                nk += resp[i][k]
                mx += resp[i][k] * points[i].x
                my += resp[i][k] * points[i].y
            }
            nk = max(1e-9, nk)
            mx /= nk
            my /= nk
            var vx = 0.0
            var vy = 0.0
            var cxy = 0.0
            points.indices.forEach { i ->
                val dx = points[i].x - mx
                val dy = points[i].y - my
                vx += resp[i][k] * dx * dx
                vy += resp[i][k] * dy * dy
                cxy += resp[i][k] * dx * dy
            }
            GmmComponent(mx, my, max(1e-4, vx / nk), max(1e-4, vy / nk), cxy / nk, nk / points.size)
        }
    }
    val finalResp = Array(points.size) { i ->
        val densities = components.map { it.density(points[i].x, points[i].y) }
        val total = densities.sum().takeIf { it > 1e-300 } ?: 1.0
        DoubleArray(components.size) { k -> densities[k] / total }
    }
    steps.add(GmmStep(components, finalResp))
    return steps
}

internal fun gmmLogLikelihood(points: List<Pt>, components: List<GmmComponent>): Double {
    var total = 0.0
    points.forEach { p ->
        val mixture = components.sumOf { it.density(p.x, p.y) }
        total += ln(max(1e-300, mixture))
    }
    return total / points.size
}

// ── Affinity propagation ─────────────────────────────────────────────────────
// Points exchange two kinds of message until a set of exemplars emerges. The number of clusters is
// never specified — it falls out of the preference on the similarity diagonal.

internal class ApResult(val exemplars: List<Int>, val assignment: IntArray)

internal fun affinityPropagation(
    points: List<Pt>,
    preference: Double,
    iterations: Int,
    damping: Double = 0.7,
): ApResult {
    val n = points.size
    val s = Array(n) { i -> DoubleArray(n) { j -> if (i == j) preference else -(dist(points[i], points[j]).toDouble() * 10.0).let { it * it } } }
    val r = Array(n) { DoubleArray(n) }
    val a = Array(n) { DoubleArray(n) }

    repeat(iterations) {
        // Responsibility: how well-suited k is as an exemplar for i, against the competition.
        for (i in 0 until n) {
            val combined = DoubleArray(n) { k -> a[i][k] + s[i][k] }
            for (k in 0 until n) {
                val maxOther = combined.indices.filter { it != k }.maxOfOrNull { combined[it] } ?: Double.NEGATIVE_INFINITY
                r[i][k] = damping * r[i][k] + (1 - damping) * (s[i][k] - maxOther)
            }
        }
        // Availability: how appropriate it would be for i to pick k, given others' support for k.
        for (k in 0 until n) {
            var positiveSum = 0.0
            for (i in 0 until n) if (i != k) positiveSum += max(0.0, r[i][k])
            for (i in 0 until n) {
                val value = if (i == k) {
                    positiveSum
                } else {
                    minOf(0.0, r[k][k] + positiveSum - max(0.0, r[i][k]))
                }
                a[i][k] = damping * a[i][k] + (1 - damping) * value
            }
        }
    }

    val exemplars = (0 until n).filter { r[it][it] + a[it][it] > 0 }
    val chosen = exemplars.ifEmpty { listOf((0 until n).maxByOrNull { r[it][it] + a[it][it] } ?: 0) }
    val assignment = IntArray(n) { i -> chosen.indices.minByOrNull { dist(points[i], points[chosen[it]]) } ?: 0 }
    return ApResult(chosen, assignment)
}

// ── Spectral clustering ──────────────────────────────────────────────────────
// Build a similarity graph, then cut it using the second-smallest eigenvector of the Laplacian. The
// point is that connectivity, not compactness, decides the clusters.

internal fun rbfAffinity(points: List<Pt>, sigma: Float): Array<DoubleArray> {
    val n = points.size
    return Array(n) { i ->
        DoubleArray(n) { j ->
            if (i == j) 0.0 else {
                val d = dist(points[i], points[j]).toDouble()
                exp(-(d * d) / (2.0 * sigma * sigma))
            }
        }
    }
}

// The Fiedler vector by inverse-free power iteration on (cI − L): the largest eigenvector of that
// shifted matrix is the smallest of L, and deflating the known constant vector leaves the second.
//
// The iteration count is not arbitrary. c must exceed λ_max to keep (cI − L) positive definite, and
// that same requirement squeezes (c−λ₃)/(c−λ₂) toward 1, so convergence is slow by construction. At
// n = 60 this was checked against a full eigendecomposition: 400 and 2000 iterations agree with it
// on only 95% of signs, while 6000 agrees exactly. It runs once per frame build, so the cost is fine.
internal fun fiedlerVector(affinity: Array<DoubleArray>, iterations: Int = 6000): DoubleArray {
    val n = affinity.size
    val degree = DoubleArray(n) { i -> affinity[i].sum() }
    val shift = degree.max() * 2.0 + 1.0

    var v = DoubleArray(n) { if (it % 2 == 0) 1.0 else -1.0 }

    fun deflate(vec: DoubleArray) {
        val mean = vec.average()
        for (i in 0 until n) vec[i] -= mean
        val norm = sqrt(vec.sumOf { it * it }).takeIf { it > 1e-12 } ?: 1.0
        for (i in 0 until n) vec[i] /= norm
    }
    deflate(v)

    repeat(iterations) {
        // (cI − L)v where L = D − W, so (cI − D + W)v.
        val next = DoubleArray(n) { i ->
            var sum = (shift - degree[i]) * v[i]
            for (j in 0 until n) sum += affinity[i][j] * v[j]
            sum
        }
        v = next
        deflate(v)
    }
    return v
}

// Two interleaved half-moons: the canonical case where k-means fails and spectral succeeds, because
// the clusters are connected but not compact.
//
// This is scikit-learn's make_moons geometry rather than an approximation of it. A first attempt
// used arcs with hand-picked centres and radii, and the moons came out too weakly interleaved for
// spectral clustering to beat k-means by any meaningful margin — 0.67 against 0.65, which would have
// made the topic's whole payoff a claim rather than a demonstration. With the standard construction
// it is 0.98 against 0.85.
internal fun twoMoons(seed: Int, perMoon: Int = 30): List<Pt> {
    var state = seed
    fun rnd(): Float {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7fff) / 32767f
    }
    // Outer arc (cos, sin) and inner arc (1−cos, 1−sin−0.5), then mapped into the unit square.
    fun place(rawX: Double, rawY: Double): Pt = Pt(
        (((rawX + 1.0) / 3.0).toFloat() + (rnd() - 0.5f) * 0.07f),
        (((rawY + 0.5) / 1.5).toFloat() + (rnd() - 0.5f) * 0.07f),
    )
    val outer = (0 until perMoon).map { i ->
        val t = Math.PI * i / (perMoon - 1)
        place(cos(t), sin(t))
    }
    val inner = (0 until perMoon).map { i ->
        val t = Math.PI * i / (perMoon - 1)
        place(1.0 - cos(t), 1.0 - sin(t) - 0.5)
    }
    return outer + inner
}

// ── Shared datasets ──────────────────────────────────────────────────────────

internal class ClusterRng(private var state: Int) {
    fun next(): Float {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7fff) / 32767f
    }

    fun jitter(scale: Float) = (next() - 0.5f) * scale
}

internal fun clusterBlob(rng: ClusterRng, cx: Float, cy: Float, count: Int, spread: Float, spreadY: Float = spread) =
    List(count) { Pt((cx + rng.jitter(spread)).coerceIn(0.04f, 0.96f), (cy + rng.jitter(spreadY)).coerceIn(0.04f, 0.96f)) }

// Two blobs plus a far outlier — the mean is dragged toward it and the median is not.
internal val outlierBlobs: List<Pt> = ClusterRng(13).let { rng ->
    clusterBlob(rng, 0.28f, 0.30f, 12, 0.16f) +
        clusterBlob(rng, 0.62f, 0.62f, 12, 0.16f) +
        listOf(Pt(0.95f, 0.06f), Pt(0.92f, 0.10f))
}

// Deliberately unequal densities: DBSCAN with a single eps cannot serve both.
internal val varyingDensity: List<Pt> = ClusterRng(29).let { rng ->
    clusterBlob(rng, 0.24f, 0.72f, 20, 0.10f) +
        clusterBlob(rng, 0.70f, 0.34f, 14, 0.26f) +
        listOf(Pt(0.50f, 0.94f), Pt(0.06f, 0.10f))
}

// Elongated and spherical side by side: k-means insists on circles, a mixture does not.
internal val elongatedBlobs: List<Pt> = ClusterRng(37).let { rng ->
    clusterBlob(rng, 0.32f, 0.50f, 22, 0.10f, spreadY = 0.44f) +
        clusterBlob(rng, 0.74f, 0.52f, 18, 0.18f)
}

internal val compactBlobs: List<Pt> = ClusterRng(41).let { rng ->
    clusterBlob(rng, 0.24f, 0.28f, 10, 0.14f) +
        clusterBlob(rng, 0.72f, 0.30f, 10, 0.14f) +
        clusterBlob(rng, 0.48f, 0.76f, 10, 0.14f)
}
