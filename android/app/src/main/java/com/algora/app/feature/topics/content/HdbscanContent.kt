package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureSpan
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val hdbscanContent = TopicContent(
    topicId = "hdbscan",
    figure = Figure(
        caption = "Persistence, on the same 36 points OPTICS orders — computed from the minimum " +
            "spanning tree over mutual reachability, whose edge weights are exactly the ε values " +
            "at which things join. The axis is ε × 1000. The tight cluster is fully connected at " +
            "ε = 0.038 and the loose one not until 0.113; both stop being separate clusters at " +
            "0.410, where the single edge between them closes. So in ε the two lifetimes look " +
            "comparable, 0.372 against 0.297 — and that is why HDBSCAN measures on λ = 1/ε " +
            "instead, where they are 23.8 and 6.4 and the tight cluster is the more persistent " +
            "one by 3.7×. Neither stray is ever a cluster: the first attaches at 0.294 and the " +
            "second not until 0.591, and a single point joining is a point falling out rather " +
            "than a split, which is exactly what min_cluster_size prunes away.",
        shape = FigureShape.Timeline(
            spans = listOf(
                FigureSpan(38, 410, "tight cluster · λ-span 23.8"),
                FigureSpan(112, 410, "loose cluster · λ-span 6.4", FigureTone.Accent),
                FigureSpan(410, 600, "one merged blob", FigureTone.Warn),
            ),
            axisMax = 600,
            marker = 294,
            markerLabel = "first stray attaches",
        ),
    ),
    whatIsIt = listOf(
        "HDBSCAN takes OPTICS' insight — that there is no single correct eps — and finishes the job. Rather than producing a profile for a human to cut, it builds the full hierarchy of DBSCAN results across every eps and then selects, automatically, the clusters that persisted longest across that range.",
        "The mechanism is a change of metric followed by a change of question. Distances are replaced by mutual reachability, max(core(a), core(b), d(a,b)), which inflates distances in sparse regions and leaves dense ones alone — this is what stops a single sparse point bridging two clusters. A minimum spanning tree over that metric, cut in decreasing weight order, is exactly the DBSCAN hierarchy. Then instead of cutting at one height, HDBSCAN measures each candidate cluster's *stability*: how much total eps-range its points survived before shattering into children. Clusters with high stability are selected, and a cluster is never chosen alongside its own descendants.",
        "The practical consequence is that the parameter changes character. DBSCAN's eps is a question about the scale of your data, which you generally cannot answer without looking; HDBSCAN's `min_cluster_size` is a question about what you would be willing to call a cluster, which you usually can answer. It also inherits DBSCAN's genuine strengths — arbitrary cluster shapes and an explicit noise label rather than forcing every point into a group. The costs: it is slower, it still struggles when clusters genuinely overlap rather than merely differing in density, and the soft-membership extension is not the same thing as a probabilistic model like a Gaussian mixture.",
    ),
    steps = listOf(
        StepCard(1, "Compute Core Distances", "Distance to the k-th nearest neighbour, per point.", 0xFF3B82F6),
        StepCard(2, "Switch to Mutual Reachability", "max(core(a), core(b), d(a,b)) — sparse regions get pushed apart.", 0xFF818CF8),
        StepCard(3, "Build the MST", "A minimum spanning tree over that metric is the whole density hierarchy.", 0xFF60A5FA),
        StepCard(4, "Condense the Tree", "Drop splits smaller than min_cluster_size — they are points falling out, not real splits.", 0xFF10B981),
        StepCard(5, "Measure Stability", "How much eps-range each candidate survived before breaking up.", 0xFF14B8A6),
        StepCard(6, "Select the Persistent Ones", "Choose high-stability clusters, never together with their own descendants.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Core distance", "core_k(x) = d(x, k-th nearest)", "Small in dense regions."),
        FormulaEntry("Mutual reachability", "max(core(a), core(b), d(a,b))", "Never smaller than either core distance."),
        FormulaEntry("λ", "1/distance", "The scale at which a point joins or leaves a cluster."),
        FormulaEntry("Stability", "Σ_{p∈C} (λ_p − λ_birth)", "Total persistence of a candidate cluster."),
        FormulaEntry("Selection", "keep C if stability(C) > Σ stability(children)", "Otherwise take the children."),
        FormulaEntry("Complexity", "O(n log n) typical", "Via a space tree; O(n²) in the worst case."),
    ),
    notationKey = listOf(
        NotationEntry("mutual reachability", "the inflated distance metric HDBSCAN clusters over"),
        NotationEntry("condensed tree", "the hierarchy after small splits are pruned away"),
        NotationEntry("stability", "persistence of a cluster across the eps range"),
        NotationEntry("min_cluster_size", "the primary parameter — what counts as a cluster"),
        NotationEntry("λ", "inverse distance; the axis the hierarchy is measured along"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Using it, and reading what it found",
            accentColor = 0xFF3B82F6,
            code = """
                from sklearn.cluster import HDBSCAN   # also available as the hdbscan package
                import numpy as np

                model = HDBSCAN(
                    min_cluster_size=15,    # THE parameter: smaller groups are noise
                    min_samples=None,       # defaults to min_cluster_size; lower = less noise
                ).fit(X)

                labels = model.labels_                  # -1 is noise, and that is a real answer
                print(len(set(labels) - {-1}), "clusters", (labels == -1).sum(), "noise")

                # probabilities_ is a strength-of-membership, not a posterior. A point deep in
                # a cluster scores near 1 and one at the fringe near 0 — useful for filtering
                # to confident members, but it is not a probabilistic model.
                confident = X[model.probabilities_ > 0.9]
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Mutual reachability, and why it stops bridging",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                def core_distance(X, i, k):
                    d = np.sort(np.linalg.norm(X - X[i], axis=1))
                    return d[k]        # d[0] is the point itself

                def mutual_reachability(X, i, j, k):
                    return max(core_distance(X, i, k),
                               core_distance(X, j, k),
                               np.linalg.norm(X[i] - X[j]))

                # A lone point sitting between two dense clusters has a LARGE core distance,
                # because its k-th neighbour is far away. Mutual reachability therefore
                # reports it as far from everything, even from whatever it is nearest to —
                # so it cannot act as a bridge that chains the two clusters into one.
                # Plain single-linkage on raw distances is exactly what does chain there.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("globe", 0xFF3B82F6, "Geospatial Clustering", "Urban and rural point densities differ by orders of magnitude within one dataset."),
        ApplicationCard("chip", 0xFF818CF8, "Embedding Exploration", "The standard partner to UMAP for finding structure in learned embeddings."),
        ApplicationCard("flask", 0xFF10B981, "Single-Cell Genomics", "Cell populations of very different abundance, where forcing every cell into a type is wrong."),
    ),
    takeaways = listOf(
        "It builds the whole DBSCAN hierarchy and selects clusters by how long they persisted, rather than fixing one eps.",
        "Mutual reachability inflates distances in sparse regions, which is what prevents a lone point bridging two clusters.",
        "min_cluster_size asks what counts as a cluster — an easier question than what scale the data is at.",
        "It keeps DBSCAN's arbitrary shapes and explicit noise label, and still struggles with genuinely overlapping clusters.",
    ),
    crossLinks = listOf(
        CrossLink("dbscan", "DBSCAN"),
        CrossLink("optics", "OPTICS"),
        CrossLink("hierarchical_clustering", "Hierarchical (Agglomerative)"),
        CrossLink("prims_mst", "Prim's MST (DSA)"),
    ),
)
