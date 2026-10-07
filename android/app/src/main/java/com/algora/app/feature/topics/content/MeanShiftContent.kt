package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val meanShiftContent = TopicContent(
    topicId = "mean_shift",
    figure = Figure(
        caption = "Mean shift run for real on 80 one-dimensional points drawn as three groups around " +
            "0.20, 0.35 and 0.75, with the bandwidth swept from 0.02 to 0.40; the axis top is 11 " +
            "clusters. The count falls 11, 10, 5, 3, 3, 2, 2, 2, 1 — the truth is recovered only in " +
            "the narrow window around h = 0.06 to 0.08, where the two nearby groups are still " +
            "resolved and the far one has not yet merged. Below it every small fluctuation in the " +
            "density estimate becomes its own mode; above it the estimate smooths into one hill. " +
            "\"No k required\" is accurate and is not the same as no choice required.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "clusters found",
                    listOf(
                        FigurePoint(0.05f, 1f), FigurePoint(0.075f, 0.909f),
                        FigurePoint(0.1f, 0.455f), FigurePoint(0.15f, 0.273f),
                        FigurePoint(0.2f, 0.273f), FigurePoint(0.3f, 0.182f),
                        FigurePoint(0.4f, 0.182f), FigurePoint(0.6f, 0.182f),
                        FigurePoint(1f, 0.091f),
                    ),
                ),
            ),
            markers = listOf(
                FigurePoint(0.15f, 0.273f, "h=0.06 → 3"),
                FigurePoint(1f, 0.091f, "h=0.4 → 1", FigureTone.Warn),
            ),
            xLabel = "bandwidth h  (0 → 0.4)",
            yLabel = "clusters  (0 → 11)",
        ),
    ),
    whatIsIt = listOf(
        "Mean shift treats the data as samples from an underlying density and looks for that density's peaks. Every point is a seed that repeatedly moves to the kernel-weighted mean of its neighbours, and since a neighbourhood's weighted mean always sits toward its denser side, each seed climbs uphill without any gradient ever being computed.",
        "Seeds that arrive at the same peak form a cluster, so the number of clusters is discovered rather than specified. That makes it genuinely different from k-means, which cannot tell you k, and it also produces arbitrarily-shaped clusters because nothing constrains the basin of attraction of a mode to be convex or round.",
        "The parameter did not disappear, it moved. Bandwidth — the width of the kernel window — controls everything: too small and the density estimate becomes bumpy so every small group becomes its own mode, too large and the estimate smooths into a single hill and everything merges. There are heuristics (`estimate_bandwidth` uses a quantile of pairwise distances) but no principled answer, and the result is quite sensitive to it. The other limit is cost: each iteration is O(n²) because every seed consults every point, which puts a practical ceiling of a few thousand points on it unless you subsample the seeds or use a spatial index.",
    ),
    steps = listOf(
        StepCard(1, "Choose a Bandwidth", "The kernel width. This is the only parameter, and it decides the answer.", 0xFF3B82F6),
        StepCard(2, "Seed Everywhere", "Every data point starts as its own candidate, or a subsample for speed.", 0xFF818CF8),
        StepCard(3, "Compute the Weighted Mean", "Of all points within the window, weighted by the kernel.", 0xFF60A5FA),
        StepCard(4, "Move the Seed There", "The shift vector points uphill by construction — no gradient is needed.", 0xFF10B981),
        StepCard(5, "Iterate to a Mode", "Repeat until the seed stops moving. It has found a density peak.", 0xFF14B8A6),
        StepCard(6, "Merge Nearby Modes", "Seeds converging within a tolerance are one cluster. That count is the answer.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Mean shift vector", "m(x) = Σ K(xᵢ−x)xᵢ / Σ K(xᵢ−x) − x", "Points uphill in the density."),
        FormulaEntry("Gaussian kernel", "K(u) = exp(−‖u‖²/2h²)", "h is the bandwidth."),
        FormulaEntry("Flat kernel", "K(u) = 1 if ‖u‖ ≤ h", "The simple window; convergence is finite-step."),
        FormulaEntry("Update", "x ← x + m(x)", "Guaranteed non-decreasing in density."),
        FormulaEntry("Complexity", "O(n²) per iteration", "Every seed consults every point."),
        FormulaEntry("Clusters", "= number of distinct modes", "Discovered, not specified."),
    ),
    notationKey = listOf(
        NotationEntry("bandwidth h", "kernel width; the sole parameter"),
        NotationEntry("mode", "a local maximum of the density"),
        NotationEntry("basin of attraction", "the set of seeds converging to one mode"),
        NotationEntry("KDE", "kernel density estimate — the surface being climbed"),
        NotationEntry("CAMShift", "the adaptive-bandwidth variant used for video tracking"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Bandwidth is the whole story",
            accentColor = 0xFF3B82F6,
            code = """
                from sklearn.cluster import MeanShift, estimate_bandwidth
                import numpy as np

                # The heuristic: a quantile of the pairwise distance distribution. It is a
                # reasonable starting point and not a principled answer.
                h = estimate_bandwidth(X, quantile=0.2, n_samples=500)
                print(round(h, 3))

                for q in (0.05, 0.1, 0.2, 0.4, 0.6):
                    bw = estimate_bandwidth(X, quantile=q, n_samples=500)
                    labels = MeanShift(bandwidth=bw, bin_seeding=True).fit_predict(X)
                    print(q, round(bw, 3), len(set(labels)), "clusters")
                # The cluster count generally decreases as the bandwidth grows, and moves a lot. "No k
                # required" is true, and it is not the same as "no choice required".
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The update, and why it climbs",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                def mean_shift_step(seeds, X, h):
                    # Weighted mean of the neighbourhood, per seed.
                    d2 = ((seeds[:, None, :] - X[None]) ** 2).sum(axis=2)
                    w = np.exp(-d2 / (2 * h * h))
                    return (w @ X) / w.sum(axis=1, keepdims=True)

                seeds = X.copy()
                for _ in range(30):
                    moved = mean_shift_step(seeds, X, h=0.3)
                    if np.abs(moved - seeds).max() < 1e-4:
                        break
                    seeds = moved

                # No derivative appears anywhere above. The shift vector is provably
                # proportional to the normalized gradient of the kernel density estimate,
                # so this IS gradient ascent — with an automatically chosen step size.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFF3B82F6, "Image Segmentation", "Clustering pixels in colour-plus-position space, where the region count is not known in advance."),
        ApplicationCard("target", 0xFF818CF8, "Object Tracking", "CAMShift follows a mode across video frames, adapting the window as the target changes size."),
        ApplicationCard("chart", 0xFF10B981, "Mode Finding", "Locating peaks in a distribution when the number of peaks is the thing you want to learn."),
    ),
    takeaways = listOf(
        "Each point climbs to a density peak; the number of peaks is the number of clusters.",
        "The shift vector is gradient ascent on a kernel density estimate, with no derivative computed.",
        "It needs no k, but bandwidth controls the answer and there is no principled way to set it.",
        "O(n²) per iteration caps it at a few thousand points without subsampling or a spatial index.",
    ),
    crossLinks = listOf(
        CrossLink("kmeans", "K-Means Clustering"),
        CrossLink("dbscan", "DBSCAN"),
        CrossLink("gmm", "Gaussian Mixture Models (GMM)"),
        CrossLink("knn", "k-Nearest Neighbors"),
    ),
)
