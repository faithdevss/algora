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

internal val closestPairOfPointsContent = TopicContent(
    topicId = "closest_pair_of_points",
    figure = Figure(
        caption = "Points sorted by x and split down the middle: a, b, c on the left, d, e, f on the " +
            "right. Each half returns its own closest pair, and δ is the smaller of the two. Neither is " +
            "the answer — c and d straddle the split and beat both. The recursion alone would miss them, " +
            "so the combine step re-checks the band within δ of the dividing line, and that band is why " +
            "this is O(n log n) rather than O(n²): walking it in y order, a point can have at most a " +
            "handful of others closer than δ before they would have been closer to each other, so the " +
            "check is a constant number of comparisons per point, not all of them.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("a", 0.10f, 0.25f),
                FigureGraphNode("b", 0.22f, 0.72f),
                FigureGraphNode("c", 0.40f, 0.40f, FigureTone.Accent),
                FigureGraphNode("d", 0.60f, 0.34f, FigureTone.Accent),
                FigureGraphNode("e", 0.76f, 0.76f),
                FigureGraphNode("f", 0.92f, 0.20f),
            ),
            edges = listOf(
                FigureEdge(0, 2, "δ left", tone = FigureTone.Primary),
                FigureEdge(3, 5, "δ right", tone = FigureTone.Primary),
                // No label: the pair is the shortest edge on the card by construction, and a name at
                // that length lands on both circles rather than between them.
                FigureEdge(2, 3, tone = FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Given points in a plane, the closest-pair problem finds the two with the smallest distance between them — the brute-force answer is O(n²), checking every pair.",
        "A divide-and-conquer approach reaches O(n log n) by splitting the points, solving each half, and cleverly checking only a thin strip along the dividing line for cross-pairs.",
    ),
    steps = listOf(
        StepCard(1, "Sort & Split", "Sort points by x, then divide them into a left and right half.", 0xFF10B981),
        StepCard(2, "Conquer Each Half", "Recursively find the closest pair within the left and within the right; take the smaller distance δ.", 0xFF3B82F6),
        StepCard(3, "Check the Strip", "A closer pair could straddle the split — but only within δ of the dividing line, so scan that strip.", 0xFFF59E0B),
        StepCard(4, "Bounded Strip Work", "Sorted by y, each strip point compares to only a constant number of neighbors, keeping the merge linear.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Brute force", "O(n²)", "Distance of every pair."),
        FormulaEntry("Divide & conquer", "O(n log n)", "T(n) = 2T(n/2) + O(n)."),
        FormulaEntry("Strip bound", "≤ 7 comparisons/point", "Geometry limits how many points fit in a δ×2δ strip."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of points"),
        NotationEntry("δ (delta)", "best distance found so far"),
        NotationEntry("strip", "points within δ of the dividing line"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Closest pair — structure",
            accentColor = 0xFF6366F1,
            code = """
                // Points pre-sorted by x. Returns the smallest pairwise distance.
                fun closest(px: List<Point>): Double {
                    if (px.size <= 3) return bruteForce(px)      // base case
                    val mid = px.size / 2
                    val midX = px[mid].x
                    val dl = closest(px.subList(0, mid))
                    val dr = closest(px.subList(mid, px.size))
                    var d = minOf(dl, dr)
                    // Only points within d of the split line can beat d. Sorting the strip here
                    // makes this version O(n log² n); to reach O(n log n), keep a y-sorted copy
                    // and merge it on the way back up so the combine step stays linear.
                    val strip = px.filter { kotlin.math.abs(it.x - midX) < d }
                        .sortedBy { it.y }
                    for (i in strip.indices) {
                        var j = i + 1
                        while (j < strip.size && strip[j].y - strip[i].y < d) {
                            d = minOf(d, dist(strip[i], strip[j]))
                            j++
                        }
                    }
                    return d
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("map", 0xFF10B981, "Collision Detection", "Games and simulations find the nearest pair of objects to test for imminent collisions."),
        ApplicationCard("chart", 0xFF3B82F6, "Clustering & Nearest Neighbor", "Spotting the two most similar data points seeds hierarchical clustering."),
        ApplicationCard("share", 0xFFF59E0B, "Geospatial Queries", "Finding the closest pair of locations underlies proximity and routing features."),
    ),
    takeaways = listOf(
        "Divide and conquer cuts closest-pair from O(n²) to O(n log n).",
        "The key trick is that cross-boundary pairs live only in a thin δ-wide strip.",
        "Sorting the strip by y limits each point to a constant number of comparisons.",
        "It's a classic example of geometry taming a merge step to keep the recurrence linear.",
    ),
    crossLinks = listOf(
        CrossLink("merge_sort", "Merge Sort"),
        CrossLink("kd_tree", "K-D Tree / Quad Tree / Octree"),
        // The taxonomy lists this under Divide and Conquer and under Computational Geometry; the
        // topic stays here and points at the geometry group rather than being duplicated into it.
        CrossLink("convex_hull", "Convex Hull"),
        CrossLink("rotating_calipers", "Rotating Calipers"),
    ),
)
