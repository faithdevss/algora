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

internal val isolationForestContent = TopicContent(
    topicId = "isolation_forest",
    figure = Figure(
        caption = "s = 2^(−h/c(ψ)) at the default ψ = 256, where c(256) = 10.24 is the average path " +
            "length of an unsuccessful search in a binary search tree of that size. That constant is " +
            "the entire reason the score is comparable across sample sizes: the curve crosses 0.5 " +
            "exactly at h = c(ψ), so \"above 0.5\" means \"isolated faster than an average point\" " +
            "rather than any absolute depth. A point cut off in 3 splits scores 0.816; one that " +
            "survives 12 scores 0.444. The decay is not linear — the first few splits carry far more " +
            "signal than the last few.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "s(x)",
                    listOf(
                        FigurePoint(0f, 1f), FigurePoint(0.125f, 0.873f), FigurePoint(0.1875f, 0.816f),
                        FigurePoint(0.25f, 0.763f), FigurePoint(0.375f, 0.666f),
                        FigurePoint(0.5f, 0.582f), FigurePoint(0.625f, 0.508f),
                        FigurePoint(0.75f, 0.444f), FigurePoint(0.875f, 0.388f),
                        FigurePoint(1f, 0.339f),
                    ),
                ),
            ),
            markers = listOf(
                FigurePoint(0.1875f, 0.816f, "h=3", FigureTone.Warn),
                FigurePoint(0.640f, 0.500f, "h=c(ψ)"),
            ),
            xLabel = "path length h  (0 → 16)",
            yLabel = "anomaly score",
        ),
    ),
    whatIsIt = listOf(
        "Most anomaly detectors build a model of what normal looks like and flag whatever sits far from it. Isolation Forest inverts that: it never models normality at all, and instead asks how many random cuts it takes to separate each point from everything else.",
        "The insight is that anomalies are *few and different*, which makes them easy to isolate. A point in a sparse region gets cut off from the crowd almost immediately, while a point in the middle of a dense cluster survives many cuts because each one leaves neighbours on the same side. Build a tree by repeatedly picking a random feature and a random split value, and the depth at which a point ends up alone — its path length — is the anomaly score. Short path means anomalous. Average over many trees and the estimate stabilizes.",
        "Two consequences follow from never computing a distance. Training is O(n log n) with no pairwise comparisons, so it scales where distance- or density-based methods do not, and it sidesteps the curse of dimensionality that makes Euclidean distance meaningless in high dimensions. More surprisingly, subsampling *helps*: the original paper uses 256 rows per tree by default, because a smaller sample makes sparse regions sparser and stops dense clusters from being over-partitioned — larger samples make anomalies harder to isolate, not easier. The main caveat is the axis-aligned cuts, which produce rectangular artifacts and can miss anomalies that are only unusual along a diagonal; Extended Isolation Forest uses random hyperplanes to fix exactly that.",
    ),
    steps = listOf(
        StepCard(1, "Subsample", "256 rows per tree by default. Smaller genuinely works better here.", 0xFFF59E0B),
        StepCard(2, "Pick a Random Feature", "No label, no criterion — the cuts never look at a target.", 0xFF818CF8),
        StepCard(3, "Pick a Random Split Value", "Uniform between that feature's min and max within the node.", 0xFF60A5FA),
        StepCard(4, "Recurse Until Isolated", "Stop when a point is alone, or at the height limit. No purity criterion.", 0xFF10B981),
        StepCard(5, "Average the Path Lengths", "Across all trees, per point. Short means easy to isolate.", 0xFF14B8A6),
        StepCard(6, "Normalize to a Score", "s = 2^(−E[h(x)]/c(n)); above ~0.5 is anomalous.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Anomaly score", "s(x,n) = 2^(−E[h(x)]/c(n))", "Near 1 anomalous, near 0 normal."),
        FormulaEntry("Normalizer", "c(n) = 2H(n−1) − 2(n−1)/n", "Average path length of an unsuccessful BST search."),
        FormulaEntry("Path length", "h(x)", "Splits needed to isolate x in one tree."),
        FormulaEntry("Training cost", "O(t·ψ log ψ)", "t trees, ψ subsample size. No distances at all."),
        FormulaEntry("Default subsample", "ψ = 256", "Larger is usually worse, not better."),
        FormulaEntry("Height limit", "ceil(log₂ ψ)", "Beyond it, remaining depth is estimated rather than grown."),
    ),
    notationKey = listOf(
        NotationEntry("h(x)", "path length — splits to isolate x"),
        NotationEntry("ψ", "subsample size per tree"),
        NotationEntry("c(n)", "expected path length, used to normalize"),
        NotationEntry("contamination", "assumed anomaly fraction, which sets the threshold"),
        NotationEntry("swamping / masking", "normals flagged as anomalies / anomalies hidden by each other"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Fitting, and why the default subsample is small",
            accentColor = 0xFFF59E0B,
            code = """
                from sklearn.ensemble import IsolationForest
                import numpy as np

                model = IsolationForest(
                    n_estimators=100,
                    max_samples=256,      # the paper's default — see below
                    contamination="auto", # only sets the threshold, not the scoring
                    random_state=0,
                ).fit(X)

                scores = -model.score_samples(X)   # higher = more anomalous
                labels = model.predict(X)          # -1 anomaly, +1 normal

                # Counter-intuitive, and worth checking on your own data: bigger samples
                # often score WORSE. More rows fill in the sparse regions that made the
                # anomalies easy to isolate in the first place.
                for m in (64, 256, 1024, 4096):
                    fit = IsolationForest(max_samples=m, random_state=0).fit(X)
                    print(m, round(roc_auc_score(y_true, -fit.score_samples(X)), 4))
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The scoring function, and the axis-aligned blind spot",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np

                def c(n):
                    # Expected path length of an unsuccessful search in a BST of n nodes.
                    if n <= 1:
                        return 0.0
                    return 2 * (np.log(n - 1) + 0.5772156649) - 2 * (n - 1) / n

                def score(mean_path_length, n):
                    return 2 ** (-mean_path_length / c(n))

                for h in (3, 6, 9, 12):
                    print(h, round(score(h, 256), 3))
                # Short paths score near 1. The normalizer is what makes the number
                # comparable across different sample sizes.

                # The blind spot: cuts are axis-aligned, so a point that is unusual only
                # along a diagonal sits inside the same rectangles as normal points and
                # takes just as long to isolate. Extended Isolation Forest replaces the
                # axis-parallel cut with a random hyperplane for precisely this case.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("finance", 0xFFF59E0B, "Fraud Detection", "Unsupervised, so it finds novel patterns rather than only the fraud already labelled."),
        ApplicationCard("chip", 0xFF818CF8, "Infrastructure Monitoring", "Anomalous metric combinations across high-dimensional telemetry, at streaming rates."),
        ApplicationCard("flask", 0xFF10B981, "Data Quality", "A cheap first pass for corrupt rows before they reach a downstream model."),
    ),
    takeaways = listOf(
        "It isolates anomalies rather than modelling normality — path length is the score.",
        "No distance computations, so it is O(n log n) and survives high dimensions.",
        "Small subsamples work better, which is the opposite of the usual intuition.",
        "Axis-aligned cuts miss diagonal anomalies; Extended Isolation Forest uses random hyperplanes instead.",
    ),
    crossLinks = listOf(
        CrossLink("dbscan", "DBSCAN"),
        CrossLink("random_forest", "Random Forests"),
        CrossLink("extra_trees", "Extra Trees Classifier"),
        CrossLink("robust_regression", "Robust Regression (RANSAC)"),
    ),
)
