package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val convexHullContent = TopicContent(
    topicId = "convex_hull",
    whatIsIt = listOf(
        "The convex hull of a point set is the smallest convex polygon containing all of it — the shape a rubber band snaps to when released around a board of pins. Every input point is either a vertex of that polygon or strictly inside it, and there is exactly one such polygon for any set.",
        "Graham scan gets there by sorting. Pick the lowest point, sort the rest by polar angle around it, then walk the sorted list maintaining a stack of the hull so far. At each new point, check the turn the last two stack entries and the new point make: a left turn is convex and the point is pushed, a right turn means the middle point was a mistake, so pop it and re-check. Each point is pushed once and popped at most once, so the scan is linear and the O(n log n) total is entirely the sort's.",
        "Jarvis march — gift wrapping — takes the opposite trade. Start at the leftmost point and repeatedly find the point that is most counter-clockwise from the current one, which is one linear sweep per hull vertex. That is O(n·h) where h is the number of hull vertices, so it beats Graham scan whenever the hull is small relative to the input: a thousand points with a five-point hull costs 5,000 tests rather than a full sort. When almost every point is on the hull it degrades to O(n²). Chan's algorithm combines the two into O(n log h), which is optimal. The one thing all of them share is the orientation test, and the one thing that breaks all of them is degeneracy — three collinear points on a hull edge, which every implementation has to decide to keep or discard, consistently.",
    ),
    steps = listOf(
        StepCard(1, "Anchor at the Lowest Point", "Smallest y, ties broken by smallest x. It is guaranteed to be on the hull.", 0xFF06B6D4),
        StepCard(2, "Sort by Polar Angle", "Order the rest by the angle they make with the anchor. This is the O(n log n) term.", 0xFF3B82F6),
        StepCard(3, "Push the First Two", "Seed the stack; a turn test needs three points before it means anything.", 0xFFF59E0B),
        StepCard(4, "Test the Turn", "Cross product of the last two stack entries against the candidate: positive is a left turn.", 0xFF10B981),
        StepCard(5, "Pop on a Non-Left Turn", "A right turn or a straight line means the middle point is interior — discard it and re-test.", 0xFFEF4444),
        StepCard(6, "Contrast with Gift Wrapping", "Jarvis march skips the sort and pays one sweep per hull vertex — better when the hull is small.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Orientation", "(b−a) × (c−a)", "Positive → left turn, negative → right, zero → collinear."),
        FormulaEntry("Graham scan", "O(n log n)", "The sort dominates; the stack pass is O(n) amortised."),
        FormulaEntry("Jarvis march", "O(n · h)", "One linear sweep per hull vertex; O(n²) when h ≈ n."),
        FormulaEntry("Chan's algorithm", "O(n log h)", "Output-sensitive and optimal — Graham on groups, then a wrapped march."),
        FormulaEntry("Lower bound", "Ω(n log n)", "Sorting reduces to hull construction, so no comparison method beats it in general."),
        FormulaEntry("Hull size", "h ≤ n", "Equality when every point is in convex position, e.g. on a circle."),
    ),
    notationKey = listOf(
        NotationEntry("h", "number of vertices on the hull — the output size Jarvis pays for"),
        NotationEntry("orientation / cross", "the sign of (b−a) × (c−a), the only geometric primitive used"),
        NotationEntry("polar angle", "the angle a point makes with the anchor, the Graham scan sort key"),
        NotationEntry("convex position", "a set where every point is a hull vertex"),
        NotationEntry("degeneracy", "collinear or duplicate points — the case that breaks naive implementations"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Graham scan",
            accentColor = 0xFF06B6D4,
            code = """
                data class Pt(val x: Double, val y: Double)

                // > 0 left turn, < 0 right turn, == 0 collinear. Every routine here uses only this.
                fun cross(a: Pt, b: Pt, c: Pt) = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

                fun grahamScan(points: List<Pt>): List<Pt> {
                    if (points.size < 3) return points
                    val anchor = points.minWith(compareBy({ it.y }, { it.x }))   // always on the hull
                    val sorted = (points - anchor).sortedWith(
                        compareBy(
                            { kotlin.math.atan2(it.y - anchor.y, it.x - anchor.x) },
                            // Ties are collinear with the anchor: nearest first, so the far one survives.
                            { kotlin.math.hypot(it.x - anchor.x, it.y - anchor.y) },
                        ),
                    )

                    val stack = mutableListOf(anchor)
                    for (p in sorted) {
                        // Pop while the top two plus p do not turn left. Each point is popped
                        // at most once overall, which is what makes the scan linear.
                        while (stack.size >= 2 && cross(stack[stack.size - 2], stack.last(), p) <= 0) {
                            stack.removeAt(stack.lastIndex)
                        }
                        stack += p
                    }
                    return stack
                }
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Jarvis march, and when to prefer it",
            accentColor = 0xFF8B5CF6,
            code = """
                fun jarvisMarch(points: List<Pt>): List<Pt> {
                    if (points.size < 3) return points
                    val start = points.minWith(compareBy({ it.x }, { it.y }))
                    val hull = mutableListOf<Pt>()
                    var current = start
                    do {
                        hull += current
                        // The most counter-clockwise point from here — one full sweep per vertex.
                        var candidate = points.first { it != current }
                        for (p in points) {
                            if (p == current) continue
                            val turn = cross(current, candidate, p)
                            if (turn < 0 ||
                                (turn == 0.0 && hypotTo(current, p) > hypotTo(current, candidate))
                            ) {
                                candidate = p
                            }
                        }
                        current = candidate
                    } while (current != start)
                    return hull
                }

                // No sort at all: O(n·h). With n = 1000 points whose hull has h = 5 vertices,
                // that is 5000 orientation tests against Graham's ~10000-comparison sort. Flip
                // it so every point is on a circle and h = n, and it becomes O(n²).
                fun hypotTo(a: Pt, b: Pt) = kotlin.math.hypot(b.x - a.x, b.y - a.y)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF06B6D4, "Collision Bounds", "Physics engines wrap meshes in convex hulls because convex-convex intersection is cheap and general intersection is not."),
        ApplicationCard("map", 0xFF3B82F6, "Spatial Extent", "The footprint of a GPS track, a cell tower's served area, or a cluster's boundary in a scatter plot."),
        ApplicationCard("bulb", 0xFF8B5CF6, "Geometric Building Block", "Diameter, width, minimum enclosing rectangle and Delaunay triangulation all start from the hull."),
    ),
    takeaways = listOf(
        "The whole construction rests on one primitive: the sign of a cross product, which says left, right or straight.",
        "Graham scan is O(n log n) and all of it is the sort — the stack pass is amortised linear because each point pops once.",
        "Jarvis march is O(n·h): better than Graham when the hull is small, quadratic when it is not.",
        "Collinear points on a hull edge are the degeneracy every implementation has to handle deliberately, one way or the other.",
    ),
    crossLinks = listOf(
        CrossLink("rotating_calipers", "Rotating Calipers"),
        CrossLink("polygon_area", "Area & Perimeter"),
        CrossLink("closest_pair_of_points", "Closest Pair of Points"),
    ),
)
