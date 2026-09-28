package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureEdge
import com.algora.app.core.data.model.FigureGraphNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val rotatingCalipersContent = TopicContent(
    topicId = "rotating_calipers",
    figure = Figure(
        caption = "The two farthest points in a set are both hull vertices and are antipodal — touched " +
            "by the same pair of parallel supporting lines somewhere in their rotation — and an " +
            "h-vertex hull has only O(h) antipodal pairs, against the n²/2 pairs brute force would " +
            "compare. So after the hull is built, two pointers walk it once: advance the second while " +
            "the triangle area it forms keeps growing, then advance the first. The sweep is O(h) and " +
            "the hull's O(n log n) dominates. What licenses the single pass is that the support " +
            "function is unimodal around a convex polygon, so the opposite contact vertex advances " +
            "monotonically and never backs up — the same argument that licenses two pointers on a " +
            "sorted array, and the reason a non-convex polygon breaks it immediately rather than " +
            "gradually. The same machinery is reused unchanged for the minimum-area enclosing " +
            "rectangle, which always has a side flush with a hull edge, and for the width of a point " +
            "set.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("A", 0.06f, 0.50f, FigureTone.Accent),
                FigureGraphNode("B", 0.30f, 0.12f),
                FigureGraphNode("C", 0.70f, 0.12f),
                FigureGraphNode("D", 0.96f, 0.50f, FigureTone.Accent),
                FigureGraphNode("E", 0.70f, 0.88f),
                FigureGraphNode("F", 0.30f, 0.88f),
            ),
            edges = listOf(
                FigureEdge(0, 1),
                FigureEdge(1, 2),
                FigureEdge(2, 3),
                FigureEdge(3, 4),
                FigureEdge(4, 5),
                FigureEdge(5, 0),
                FigureEdge(0, 3, "diameter", tone = FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Rotating calipers is a technique, not a single algorithm: imagine a pair of parallel lines gripping a convex polygon from opposite sides, then rotating them together through a full turn. The pairs of vertices they touch along the way are the antipodal pairs, and a surprising number of extremal questions are answered by looking at exactly those pairs and nothing else.",
        "The diameter — the greatest distance between any two points in a set — is the canonical example. Brute force compares all n²/2 pairs. But the two farthest points must both be hull vertices, and they must be antipodal, and there are only O(h) antipodal pairs on an h-vertex hull. So after building the hull you walk two pointers around it: advance the second pointer while the triangle area it forms keeps growing, then advance the first. Each pointer travels around the hull once, giving O(h) for the sweep. The hull construction is O(n log n) and dominates.",
        "The reason the two-pointer walk works is that the support function is unimodal around a convex polygon: as one caliper's contact vertex advances, the opposite contact vertex advances monotonically too, never going backwards. That monotonicity is what turns a nested loop into a single pass, and it is the same argument that licenses the two-pointer technique on a sorted array. It also fails immediately on a non-convex polygon, which is why the hull is a hard prerequisite rather than an optimisation. Once the machinery exists it is reused unchanged for the minimum-area and minimum-perimeter enclosing rectangles — Toussaint's result that the optimal rectangle has a side flush with a hull edge — for the width of a point set, and for the closest pair of two disjoint convex polygons.",
    ),
    steps = listOf(
        StepCard(1, "Build the Hull First", "The farthest pair is always two hull vertices, and the whole method assumes convexity.", 0xFF06B6D4),
        StepCard(2, "Place the Calipers", "Two parallel supporting lines on opposite sides, touching one vertex each.", 0xFF3B82F6),
        StepCard(3, "Rotate to the Next Event", "Turn both lines until one becomes flush with a hull edge — that is the next antipodal pair.", 0xFFF59E0B),
        StepCard(4, "Advance by Triangle Area", "In practice: advance the opposite pointer while the triangle it forms with the current edge grows.", 0xFF10B981),
        StepCard(5, "Measure at Every Pair", "Record the distance at each antipodal pair; the maximum over them is the diameter.", 0xFF8B5CF6),
        StepCard(6, "Stop After One Turn", "Both pointers have circled the hull exactly once — O(h) pairs examined, not O(h²).", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Total time", "O(n log n)", "Hull construction; the caliper sweep itself is O(h)."),
        FormulaEntry("Antipodal pairs", "O(h)", "Not O(h²) — which is the entire saving."),
        FormulaEntry("Brute force", "O(n²)", "All pairwise distances, the thing this replaces."),
        FormulaEntry("Advance rule", "while area(pᵢ, pᵢ₊₁, q₊₁) ≥ area(pᵢ, pᵢ₊₁, q)", "Cross products only — no angles, no trigonometry."),
        FormulaEntry("Min-area rectangle", "flush with a hull edge", "Toussaint: check h orientations, one per edge."),
        FormulaEntry("Width", "min over edges of max vertex distance", "The same sweep, minimised instead of maximised."),
    ),
    notationKey = listOf(
        NotationEntry("antipodal pair", "two hull vertices admitting parallel supporting lines through both"),
        NotationEntry("supporting line", "a line touching the hull with the whole polygon on one side"),
        NotationEntry("diameter", "the greatest distance between any two points of the set"),
        NotationEntry("width", "the smallest distance between two parallel supporting lines"),
        NotationEntry("unimodality", "the property that makes the opposite pointer advance monotonically"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Hull diameter by rotating calipers",
            accentColor = 0xFF06B6D4,
            code = """
                data class Pt(val x: Double, val y: Double)

                private fun cross(o: Pt, a: Pt, b: Pt) =
                    (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

                private fun dist2(a: Pt, b: Pt) =
                    (a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y)

                /** [hull] must be in counter-clockwise order — build it with a Graham scan first. */
                fun diameter(hull: List<Pt>): Pair<Pt, Pt> {
                    val h = hull.size
                    if (h < 2) return hull.first() to hull.first()
                    if (h == 2) return hull[0] to hull[1]

                    var best = 0.0
                    var pair = hull[0] to hull[1]
                    var q = 1
                    for (p in 0 until h) {
                        val next = (p + 1) % h
                        // Advance the opposite vertex while the triangle on edge p→p+1 keeps
                        // growing. |cross| is twice that triangle's area, so no trigonometry
                        // and no square roots enter the comparison.
                        while (kotlin.math.abs(cross(hull[p], hull[next], hull[(q + 1) % h])) >
                            kotlin.math.abs(cross(hull[p], hull[next], hull[q]))
                        ) {
                            q = (q + 1) % h
                        }
                        // p and q are now antipodal; both endpoints of the edge are candidates.
                        for (candidate in listOf(hull[p], hull[next])) {
                            val d = dist2(candidate, hull[q])
                            if (d > best) {
                                best = d
                                pair = candidate to hull[q]
                            }
                        }
                    }
                    return pair
                }
                // q never moves backwards across the whole outer loop, so it circles the hull
                // once in total — that is what makes this O(h) rather than O(h²).
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The same sweep, minimum-area enclosing rectangle",
            accentColor = 0xFF8B5CF6,
            code = """
                // Toussaint's theorem: the minimum-area rectangle enclosing a convex polygon has
                // one side collinear with a hull edge. So there are only h orientations to try,
                // and each one needs the extreme vertex in four directions — which is exactly
                // what a set of rotating calipers tracks.
                fun minAreaRectangle(hull: List<Pt>): Double {
                    val h = hull.size
                    var best = Double.MAX_VALUE
                    for (i in 0 until h) {
                        val a = hull[i]
                        val b = hull[(i + 1) % h]
                        val len = kotlin.math.hypot(b.x - a.x, b.y - a.y)
                        val ux = (b.x - a.x) / len          // edge direction …
                        val uy = (b.y - a.y) / len
                        // … and the perpendicular. Project every vertex onto both axes.
                        val along = hull.map { it.x * ux + it.y * uy }
                        val across = hull.map { -it.x * uy + it.y * ux }
                        val area = (along.max() - along.min()) * (across.max() - across.min())
                        best = minOf(best, area)
                    }
                    return best
                }
                // Written with a projection per edge this is O(h²); advancing four calipers
                // instead of re-projecting brings it back to O(h).
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF06B6D4, "Bounding Boxes", "Oriented bounding boxes for collision detection come straight out of the minimum-area rectangle sweep."),
        ApplicationCard("map", 0xFF3B82F6, "Shape Measurement", "The diameter and width of a scanned part, a particle in a microscope image, or a GPS track's extent."),
        ApplicationCard("bulb", 0xFF8B5CF6, "One Technique, Many Answers", "Diameter, width, enclosing rectangles and closest pair between two convex polygons all reuse the same walk."),
    ),
    takeaways = listOf(
        "The farthest pair is always two hull vertices and always antipodal, and there are only O(h) antipodal pairs.",
        "The advance rule is a cross-product comparison of triangle areas — no angles and no square roots.",
        "Total cost is O(n log n), all of it the hull; the caliper sweep is linear because the opposite pointer never backs up.",
        "Convexity is a precondition, not a convenience: the monotonicity the two-pointer walk depends on fails without it.",
    ),
    crossLinks = listOf(
        CrossLink("convex_hull", "Convex Hull"),
        CrossLink("two_pointer", "Two Pointer Technique"),
        CrossLink("closest_pair_of_points", "Closest Pair of Points"),
    ),
)
