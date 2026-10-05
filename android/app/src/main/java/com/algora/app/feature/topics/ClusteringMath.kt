package com.algora.app.feature.topics

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ── Shared 2-D points ────────────────────────────────────────────────────────
// The float point type and distance the point-cloud and dimensionality-reduction labs share, and the
// two-moons dataset they draw. The clustering labs themselves live in ClusterStoryLabs.kt.

internal class Pt(val x: Float, val y: Float)

internal fun dist(a: Pt, b: Pt): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
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
