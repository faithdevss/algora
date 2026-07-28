package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val polygonAreaContent = TopicContent(
    topicId = "polygon_area",
    whatIsIt = listOf(
        "The shoelace formula computes the area of any simple polygon from its vertex list alone — no triangulation, no decomposition into shapes, and no requirement that the polygon be convex. One pass over the vertices, two multiplications each.",
        "The mechanism is a signed sum of cross products. Take consecutive vertices in order and add xᵢ·yᵢ₊₁ − xᵢ₊₁·yᵢ; half the absolute value of that total is the area. Each term is twice the signed area of the triangle formed by the origin and that edge, so the sum sweeps a fan of triangles out from the origin. Where the polygon bulges away from the origin the terms are positive, where it doubles back they are negative, and the cancellation is exact — which is why the origin can sit anywhere, inside the polygon or a mile away, without changing the answer.",
        "The sign is not waste: it is the polygon's orientation. A positive sum means the vertices are listed counter-clockwise, a negative one means clockwise, and that single bit is what almost every other geometric routine wants before it starts — point-in-polygon tests, convex hull direction, normal vectors. Two caveats bound the formula's reach. It assumes the polygon is simple, meaning no edge crosses another; a self-intersecting outline returns the difference of the regions rather than their sum. And perimeter, unlike area, needs a square root per edge, so it is the more expensive of the two quantities despite looking like the simpler one. For polygons whose vertices are all integer lattice points, Pick's theorem gives a third route entirely: A = i + b/2 − 1, counting interior and boundary lattice points instead of measuring anything.",
    ),
    steps = listOf(
        StepCard(1, "List the Vertices in Order", "Around the boundary, either direction — the formula records which one you chose in its sign.", 0xFF06B6D4),
        StepCard(2, "Cross Each Consecutive Pair", "For edge i→i+1 accumulate xᵢ·yᵢ₊₁ − xᵢ₊₁·yᵢ.", 0xFF3B82F6),
        StepCard(3, "Wrap the Last to the First", "The final edge closes the loop; forgetting it is the single most common bug here.", 0xFFF59E0B),
        StepCard(4, "Halve the Absolute Value", "Area = |Σ| / 2. Each term was twice a signed triangle area.", 0xFF10B981),
        StepCard(5, "Read the Sign as Orientation", "Positive is counter-clockwise, negative is clockwise — keep it before taking the absolute value.", 0xFF8B5CF6),
        StepCard(6, "Sum Edge Lengths for Perimeter", "A separate pass with a square root per edge — the costlier of the two quantities.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Shoelace", "A = ½ |Σᵢ (xᵢ·yᵢ₊₁ − xᵢ₊₁·yᵢ)|", "Indices wrap: the last vertex pairs with the first."),
        FormulaEntry("Orientation", "sign(Σ)", "Positive → counter-clockwise, negative → clockwise."),
        FormulaEntry("Perimeter", "Σᵢ √((xᵢ₊₁−xᵢ)² + (yᵢ₊₁−yᵢ)²)", "One square root per edge."),
        FormulaEntry("Time & space", "O(n) time, O(1) extra", "One accumulator, one pass."),
        FormulaEntry("Triangle case", "½|x₁(y₂−y₃) + x₂(y₃−y₁) + x₃(y₁−y₂)|", "The n = 3 expansion, and the cross-product orientation test."),
        FormulaEntry("Pick's theorem", "A = i + b/2 − 1", "Lattice polygons only: i interior points, b boundary points."),
    ),
    notationKey = listOf(
        NotationEntry("simple polygon", "a closed outline whose edges never cross each other"),
        NotationEntry("signed area", "the shoelace sum before the absolute value — carries orientation"),
        NotationEntry("cross product", "xᵢ·yᵢ₊₁ − xᵢ₊₁·yᵢ, twice the signed area of the origin triangle"),
        NotationEntry("CCW / CW", "counter-clockwise / clockwise vertex order"),
        NotationEntry("lattice polygon", "one whose vertices all have integer coordinates — Pick's precondition"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Shoelace area, orientation and perimeter",
            accentColor = 0xFF06B6D4,
            code = """
                data class Pt(val x: Double, val y: Double)

                fun signedArea(poly: List<Pt>): Double {
                    var sum = 0.0
                    for (i in poly.indices) {
                        val a = poly[i]
                        val b = poly[(i + 1) % poly.size]   // wrap — the closing edge counts
                        sum += a.x * b.y - b.x * a.y
                    }
                    return sum / 2.0                        // keep the sign; the caller decides
                }

                fun area(poly: List<Pt>) = kotlin.math.abs(signedArea(poly))
                fun isCounterClockwise(poly: List<Pt>) = signedArea(poly) > 0

                fun perimeter(poly: List<Pt>): Double =
                    poly.indices.sumOf { i ->
                        val a = poly[i]
                        val b = poly[(i + 1) % poly.size]
                        kotlin.math.hypot(b.x - a.x, b.y - a.y)   // the square root area never needs
                    }

                val tri = listOf(Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(0.0, 3.0))
                area(tri)                 // 6.0  — ½ · 4 · 3
                isCounterClockwise(tri)   // true
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The origin does not matter, and Pick's theorem",
            accentColor = 0xFF10B981,
            code = """
                // Every term is twice the signed area of the triangle (origin, vᵢ, vᵢ₊₁). Edges that
                // face away from the origin contribute positively, edges that face back contribute
                // negatively, and the overshoot cancels exactly — so translating the polygon leaves
                // the total unchanged.
                val far = tri.map { Pt(it.x + 1000.0, it.y + 1000.0) }
                area(far)   // still 6.0

                // Pick's theorem, for polygons on the integer lattice: count instead of measure.
                //   A = i + b/2 - 1
                // The same triangle: boundary points b = gcd(4,0) + gcd(4,3) + gcd(0,3) = 4 + 1 + 3 = 8,
                // so i = A - b/2 + 1 = 6 - 4 + 1 = 3, and those three are (1,1), (1,2), (2,1).
                fun boundaryPoints(poly: List<Pt>): Int = poly.indices.sumOf { i ->
                    val a = poly[i]
                    val b = poly[(i + 1) % poly.size]
                    gcd(kotlin.math.abs(b.x - a.x).toLong(), kotlin.math.abs(b.y - a.y).toLong()).toInt()
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("map", 0xFF06B6D4, "GIS & Land Measurement", "Parcel areas, catchment sizes and map-tile coverage are computed from vertex lists exactly this way."),
        ApplicationCard("chip", 0xFF3B82F6, "Graphics & Collision", "Winding order decides which faces are front-facing; the signed sum is how a renderer finds out."),
        ApplicationCard("bulb", 0xFF10B981, "Geometry Primitive", "The n = 3 case is the orientation test that convex hull, segment intersection and calipers all sit on."),
    ),
    takeaways = listOf(
        "One pass over the vertices, two multiplications per edge, and any simple polygon's area falls out.",
        "The sign is the orientation — do not throw it away before you have read it.",
        "The origin's position is irrelevant: the positive and negative triangle contributions cancel exactly.",
        "Self-intersecting outlines break the formula, and perimeter costs a square root per edge that area never needs.",
    ),
    crossLinks = listOf(
        CrossLink("convex_hull", "Convex Hull"),
        CrossLink("line_intersection", "Line Segment Intersection"),
        CrossLink("monte_carlo_method", "Monte Carlo Method"),
    ),
)
