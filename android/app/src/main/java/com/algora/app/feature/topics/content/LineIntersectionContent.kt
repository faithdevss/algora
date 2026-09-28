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

internal val lineIntersectionContent = TopicContent(
    topicId = "line_intersection",
    figure = Figure(
        caption = "The tempting approach — compute slopes, solve for the intersection point, check it " +
            "lies inside both segments — divides by zero the moment a segment is vertical. The robust " +
            "test never computes a point at all. Take the orientation of the triple (p₁, p₂, q₁) and " +
            "of (p₁, p₂, q₂): if the two differ, q's endpoints lie on opposite sides of the line " +
            "through p. Do the same the other way round, and when both pairs straddle, the segments " +
            "must cross. Four cross products, multiplication and subtraction only, no division and no " +
            "square roots — exact on integer inputs. The degenerate cases are where implementations " +
            "actually fail: an orientation of zero means three collinear points, and the answer then " +
            "turns on a bounding-box containment check rather than a sign. That single branch handles " +
            "endpoint touches, T-junctions and partial overlaps, all of them legitimate intersections " +
            "the straddle test alone reports as misses.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("p₁", 0.08f, 0.24f, FigureTone.Primary),
                FigureGraphNode("p₂", 0.92f, 0.70f, FigureTone.Primary),
                FigureGraphNode("q₁", 0.24f, 0.86f, FigureTone.Accent),
                FigureGraphNode("q₂", 0.80f, 0.12f, FigureTone.Accent),
            ),
            edges = listOf(
                FigureEdge(0, 1, tone = FigureTone.Primary),
                FigureEdge(2, 3, tone = FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Two line segments either cross or they do not, and deciding which is one of the most-used primitives in computational geometry. The temptation is to compute slopes, solve for the intersection point, and check that it lies within both segments — which works until one segment is vertical, and then divides by zero.",
        "The robust formulation never computes an intersection point at all. Take the orientation of the triple (p₁, p₂, q₁) and of (p₁, p₂, q₂): if they differ, then q₁ and q₂ lie on opposite sides of the line through p₁p₂. Do the same for (q₁, q₂, p₁) and (q₁, q₂, p₂). When both pairs straddle, the segments must cross, and the whole test is four cross products — multiplication and subtraction only, no division and no square roots. With integer inputs it is exact.",
        "That covers the general case; the degenerate cases are where implementations actually fail. When an orientation comes back zero the three points are collinear, and the answer depends on a containment check rather than a sign: does the third point lie within the bounding box of the other two? That single branch handles endpoint touches, T-junctions, partial overlaps and full containment, and every one of them is a legitimate intersection that the straddle test alone reports as a miss. Whether a shared endpoint should count as an intersection is a decision the caller must make explicitly — for polygon clipping it usually should, for a self-intersection check on a closed outline it usually should not, because consecutive edges always share one.",
    ),
    steps = listOf(
        StepCard(1, "Orient Four Triples", "o₁ = (p₁,p₂,q₁), o₂ = (p₁,p₂,q₂), o₃ = (q₁,q₂,p₁), o₄ = (q₁,q₂,p₂).", 0xFF06B6D4),
        StepCard(2, "Check the Straddle", "o₁ ≠ o₂ and o₃ ≠ o₄ means each segment separates the other's endpoints — they cross.", 0xFF10B981),
        StepCard(3, "Spot the Zeros", "An orientation of zero means three collinear points; the sign test cannot decide those.", 0xFFF59E0B),
        StepCard(4, "Fall Back to Containment", "For each zero, test whether the third point lies inside the other two's bounding box.", 0xFF8B5CF6),
        StepCard(5, "Decide on Touching", "A shared endpoint is collinear-and-contained. Whether that counts is the caller's call, not the primitive's.", 0xFFEF4444),
        StepCard(6, "Compute the Point Only if Asked", "The parametric formula gives the crossing point — but only after the test has said there is one.", 0xFF3B82F6),
    ),
    formulas = listOf(
        FormulaEntry("Orientation", "sign((b−a) × (c−a))", "+1 counter-clockwise, −1 clockwise, 0 collinear."),
        FormulaEntry("Proper crossing", "o₁ ≠ o₂ ∧ o₃ ≠ o₄", "Both segments straddle the other's line."),
        FormulaEntry("Collinear case", "o = 0 ∧ point within bounding box", "Covers touching, overlap and containment."),
        FormulaEntry("Time", "O(1)", "Four cross products; no division, no square root."),
        FormulaEntry("Intersection point", "p₁ + t·(p₂−p₁), t = ((q₁−p₁) × d₂) / (d₁ × d₂)", "Only valid when d₁ × d₂ ≠ 0 — parallel segments have no unique point."),
        FormulaEntry("All pairs", "O((n + k) log n) sweep", "Bentley–Ottmann, for k intersections among n segments, against the O(n²) brute force."),
    ),
    notationKey = listOf(
        NotationEntry("p₁p₂, q₁q₂", "the two segments, each given by its endpoints"),
        NotationEntry("orientation", "the sign of a cross product — the only primitive the test needs"),
        NotationEntry("straddle", "two points lying on opposite sides of a line"),
        NotationEntry("proper vs improper", "crossing at an interior point vs touching at an endpoint or overlapping"),
        NotationEntry("sweep line", "Bentley–Ottmann's moving vertical line, which keeps only nearby segments comparable"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The full test, degeneracies included",
            accentColor = 0xFF06B6D4,
            code = """
                data class Pt(val x: Long, val y: Long)   // integers keep the cross products exact

                fun orientation(a: Pt, b: Pt, c: Pt): Int {
                    val v = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
                    return when {
                        v > 0 -> 1
                        v < 0 -> -1
                        else -> 0
                    }
                }

                // Only called when a, b, c are known collinear: is b inside a–c's bounding box?
                private fun onSegment(a: Pt, b: Pt, c: Pt) =
                    b.x in minOf(a.x, c.x)..maxOf(a.x, c.x) && b.y in minOf(a.y, c.y)..maxOf(a.y, c.y)

                fun segmentsIntersect(p1: Pt, p2: Pt, q1: Pt, q2: Pt): Boolean {
                    val o1 = orientation(p1, p2, q1)
                    val o2 = orientation(p1, p2, q2)
                    val o3 = orientation(q1, q2, p1)
                    val o4 = orientation(q1, q2, p2)

                    if (o1 != o2 && o3 != o4) return true      // the general case, and the easy one

                    // Everything below is degenerate: at least one triple is collinear.
                    if (o1 == 0 && onSegment(p1, q1, p2)) return true
                    if (o2 == 0 && onSegment(p1, q2, p2)) return true
                    if (o3 == 0 && onSegment(q1, p1, q2)) return true
                    if (o4 == 0 && onSegment(q1, p2, q2)) return true
                    return false
                }
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The point itself — and why it comes second",
            accentColor = 0xFF3B82F6,
            code = """
                // Only after segmentsIntersect has said yes, and only when the caller needs
                // coordinates. This is the step that introduces division and therefore the
                // step that introduces floating-point error into an otherwise exact test.
                fun intersectionPoint(p1: Pt, p2: Pt, q1: Pt, q2: Pt): Pair<Double, Double>? {
                    val d1x = (p2.x - p1.x).toDouble(); val d1y = (p2.y - p1.y).toDouble()
                    val d2x = (q2.x - q1.x).toDouble(); val d2y = (q2.y - q1.y).toDouble()

                    val denom = d1x * d2y - d1y * d2x
                    if (denom == 0.0) return null   // parallel or collinear: no single point

                    val t = ((q1.x - p1.x) * d2y - (q1.y - p1.y) * d2x) / denom
                    return (p1.x + t * d1x) to (p1.y + t * d1y)
                }

                // Slope-based tests fail here, which is why nothing above computes a slope:
                //   vertical segment  → (y2 - y1) / (x2 - x1) divides by zero
                //   near-vertical     → an enormous slope that swamps the comparison
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("map", 0xFF06B6D4, "Map Overlay & Clipping", "Polygon clipping, route-crosses-boundary queries and GIS layer intersection are all this test in a loop."),
        ApplicationCard("chip", 0xFF3B82F6, "Collision & Ray Casting", "Whether a ray hits an edge, and whether a moving object's path crosses a wall."),
        ApplicationCard("bulb", 0xFFF59E0B, "Robustness Teaching", "The standard demonstration that avoiding division buys you an exact answer on integer input."),
    ),
    takeaways = listOf(
        "Four orientation tests decide the general case with no division and no square roots.",
        "A zero orientation is not a rounding artefact — it is the collinear case, and it needs a bounding-box containment check instead.",
        "Whether a shared endpoint counts as an intersection is a policy decision, and a closed polygon's consecutive edges make it a consequential one.",
        "Compute the intersection point only when asked; that step is where floating-point error enters.",
    ),
    crossLinks = listOf(
        CrossLink("convex_hull", "Convex Hull"),
        CrossLink("polygon_area", "Area & Perimeter"),
        CrossLink("closest_pair_of_points", "Closest Pair of Points"),
    ),
)
