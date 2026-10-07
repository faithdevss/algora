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

internal val opticsContent = TopicContent(
    topicId = "optics",
    figure = Figure(
        caption = "The lab's 36 points in OPTICS' processing order, each carrying the reachability " +
            "distance it was reached at — the algorithm's actual output, before anything has been " +
            "called a cluster. The terrain reads directly: the first twenty points are the tight " +
            "cluster and never rise above 0.030, the thirteen after the peak are the loose one and " +
            "sit between 0.062 and 0.112, and the 0.410 spike between them is the gap. Both " +
            "densities are on the same axis, which is the thing one eps cannot express. The dashed " +
            "line is a flat cut at 0.05, and that cut reproduces DBSCAN(0.05) up to border-point assignment: the tight cluster " +
            "survives whole and all fourteen loose points are above the line, so they become noise. " +
            "Nothing below 0.112 keeps the loose cluster intact, and that is nearly four times the " +
            "tight cluster's deepest internal distance. The final bar is 0 because the last stray " +
            "restarts the ordering — a restart, not a valley.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "a flat cut at 0.05",
                    listOf(FigurePoint(0f, 0.119f), FigurePoint(1f, 0.119f)),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "reachability",
                    listOf(
                        FigurePoint(0.000f, 0.000f), FigurePoint(0.029f, 0.060f),
                        FigurePoint(0.057f, 0.045f), FigurePoint(0.086f, 0.045f),
                        FigurePoint(0.114f, 0.048f), FigurePoint(0.143f, 0.048f),
                        FigurePoint(0.171f, 0.058f), FigurePoint(0.200f, 0.042f),
                        FigurePoint(0.229f, 0.042f), FigurePoint(0.257f, 0.030f),
                        FigurePoint(0.286f, 0.025f), FigurePoint(0.314f, 0.025f),
                        FigurePoint(0.343f, 0.036f), FigurePoint(0.371f, 0.039f),
                        FigurePoint(0.400f, 0.039f), FigurePoint(0.429f, 0.049f),
                        FigurePoint(0.457f, 0.068f), FigurePoint(0.486f, 0.073f),
                        FigurePoint(0.514f, 0.073f), FigurePoint(0.543f, 0.073f),
                        FigurePoint(0.571f, 0.673f), FigurePoint(0.600f, 0.976f),
                        FigurePoint(0.629f, 0.268f), FigurePoint(0.657f, 0.148f),
                        FigurePoint(0.686f, 0.148f), FigurePoint(0.714f, 0.148f),
                        FigurePoint(0.743f, 0.151f), FigurePoint(0.771f, 0.151f),
                        FigurePoint(0.800f, 0.152f), FigurePoint(0.829f, 0.162f),
                        FigurePoint(0.857f, 0.183f), FigurePoint(0.886f, 0.188f),
                        FigurePoint(0.914f, 0.188f), FigurePoint(0.943f, 0.171f),
                        FigurePoint(0.971f, 0.171f), FigurePoint(1.000f, 0.000f),
                    ),
                ),
            ),
            markers = listOf(
                FigurePoint(0.286f, 0.025f, "tight · 0.010"),
                FigurePoint(0.600f, 0.976f, "the gap · 0.410", FigureTone.Warn),
                FigurePoint(0.771f, 0.151f, "loose · 0.064"),
            ),
            xLabel = "processing order, 36 points",
            yLabel = "reachability distance, 0 → 0.42",
        ),
    ),
    whatIsIt = listOf(
        "DBSCAN needs one eps, and one eps cannot serve clusters of different densities. Set it for the tight cluster and the loose one dissolves into noise; set it for the loose one and the tight cluster merges with everything around it. OPTICS removes the choice rather than automating it.",
        "It produces an ordering instead of labels. Points are processed so that density-reachable ones end up adjacent, and each point records a reachability distance — how far it was from the already-processed set when it was reached. Plot those distances in processing order and you get a profile that reads like terrain: valleys are clusters, and the peaks between them are the gaps. A shallow valley is a loose cluster and a deep one is tight, and both appear in the same plot, which is precisely what a single eps cannot express.",
        "The relationship to DBSCAN is exact for core points: cutting the reachability profile at a fixed height reproduces DBSCAN's labelling for that eps (border-point assignment can differ), so OPTICS is the entire family of DBSCAN results computed in one pass. The eps parameter still exists, but only as an upper bound for efficiency — set it generously and it barely affects the answer. What OPTICS does not do is hand you clusters. Something still has to decide where to cut, and if you want that decided automatically the ξ-method or HDBSCAN's stability criterion are the answers.",
    ),
    steps = listOf(
        StepCard(1, "Compute Core Distances", "The radius enclosing minPts neighbours. Small means dense.", 0xFF3B82F6),
        StepCard(2, "Process in Priority Order", "Always expand toward the nearest unprocessed point, via a priority queue.", 0xFF818CF8),
        StepCard(3, "Record Reachability", "max(core distance, actual distance) — how easily this point was reached.", 0xFF60A5FA),
        StepCard(4, "Emit the Ordering", "The output is a sequence plus one number per point, not a labelling.", 0xFF10B981),
        StepCard(5, "Read the Profile", "Valleys are clusters; peak height is the gap between them.", 0xFF14B8A6),
        StepCard(6, "Extract Clusters", "A flat cut gives DBSCAN's answer; ξ-extraction finds valleys of differing depth.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Core distance", "distance to the minPts-th nearest neighbour", "Undefined if fewer than minPts within eps."),
        FormulaEntry("Reachability", "max(core-dist(p), d(p,q))", "Never smaller than p's own core distance."),
        FormulaEntry("Flat cut", "reachability ≤ ε′ ⟹ DBSCAN(ε′)", "Matches DBSCAN(ε′) up to border-point assignment; valid for ε′ ≤ the eps used to build the ordering."),
        FormulaEntry("Complexity", "O(n log n) with an index, O(n²) without", "Same as DBSCAN."),
        FormulaEntry("eps", "an upper bound only", "Set it generously; it is not the density parameter."),
        FormulaEntry("minPts", "the real parameter", "Controls what counts as dense enough."),
    ),
    notationKey = listOf(
        NotationEntry("reachability distance", "the y-axis of the profile"),
        NotationEntry("core distance", "radius enclosing minPts neighbours"),
        NotationEntry("ordering", "OPTICS' actual output"),
        NotationEntry("ξ-extraction", "finding clusters from relative dips in the profile"),
        NotationEntry("density-reachable", "connected through a chain of dense neighbourhoods"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The profile, and both ways to cut it",
            accentColor = 0xFF3B82F6,
            code = """
                import numpy as np
                import matplotlib.pyplot as plt
                from sklearn.cluster import OPTICS, cluster_optics_dbscan

                # xi finds clusters from RELATIVE dips, so clusters at different densities can
                # all be found — which is the whole reason to use OPTICS over DBSCAN.
                model = OPTICS(min_samples=10, xi=0.05, min_cluster_size=0.05).fit(X)

                order = model.ordering_
                reach = model.reachability_[order]
                plt.bar(range(len(reach)), reach)     # valleys are clusters

                # And the DBSCAN equivalence (up to border points, for eps <= model.max_eps) — no refitting, just a cut:
                for eps in (0.3, 0.5, 0.8):
                    labels = cluster_optics_dbscan(
                        reachability=model.reachability_,
                        core_distances=model.core_distances_,
                        ordering=model.ordering_,
                        eps=eps,
                    )
                    print(eps, len(set(labels) - {-1}), (labels == -1).sum())
                # Matches running DBSCAN(eps) directly (core points identical; some border points can differ), from one OPTICS pass.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why one eps cannot serve two densities",
            accentColor = 0xFFEC4899,
            code = """
                from sklearn.cluster import DBSCAN
                import numpy as np
                rng = np.random.default_rng(0)

                tight = rng.normal([0, 0], 0.15, size=(120, 2))
                loose = rng.normal([1.5, 1.5], 0.90, size=(120, 2))
                X = np.vstack([tight, loose])

                for eps in (0.3, 0.6, 1.2):
                    labels = DBSCAN(eps=eps, min_samples=8).fit_predict(X)
                    n_clusters = len(set(labels) - {-1})
                    noise = (labels == -1).sum()
                    print(f"eps={eps}: {n_clusters} clusters, {noise} noise")
                # eps=0.3: 4 clusters, 83 noise -- the loose blob shatters into fragments and noise.
                # eps=0.6: 1 cluster,  12 noise -- the blobs already bridge into one.
                # eps=1.2: 1 cluster,   3 noise
                # No value separates the two blobs AND keeps the loose one whole: there is no
                # single density.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("globe", 0xFF3B82F6, "Spatial Data", "City centres and rural areas have genuinely different densities, and both are real clusters."),
        ApplicationCard("flask", 0xFF818CF8, "Astronomy", "Star fields where cluster density varies by orders of magnitude across the same image."),
        ApplicationCard("chart", 0xFF10B981, "Exploratory Analysis", "The profile is a picture of the data's cluster structure before any parameter has been committed to."),
    ),
    takeaways = listOf(
        "OPTICS orders points and records reachability rather than assigning labels.",
        "Valleys in the profile are clusters, and their depth is the cluster's density.",
        "A flat cut reproduces DBSCAN's answer up to border points, so OPTICS is every eps at once.",
        "It does not choose clusters for you — ξ-extraction or HDBSCAN's stability criterion does that.",
    ),
    crossLinks = listOf(
        CrossLink("dbscan", "DBSCAN"),
        CrossLink("hdbscan", "HDBSCAN"),
        CrossLink("hierarchical_clustering", "Hierarchical (Agglomerative)"),
        CrossLink("kd_tree", "K-D Tree (DSA)"),
    ),
)
