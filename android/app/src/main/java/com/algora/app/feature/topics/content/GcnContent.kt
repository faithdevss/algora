package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val gcnContent = TopicContent(
    topicId = "gcn",
    whatIsIt = listOf(
        "A Graph Convolutional Network layer replaces a normal layer's fixed input pattern with a graph: every node updates its features by averaging its neighbors' features, weighted by degree, then applying a shared weight matrix. That averaging step — multiplying the feature matrix by a degree-normalized adjacency matrix — is the entire mechanism, and stacking layers means running it repeatedly. Repeated averaging is graph Laplacian smoothing, and its long-run behavior is exact linear algebra rather than a vague warning: on a connected graph, the normalized adjacency matrix has one eigenvalue equal to 1, with an eigenvector proportional to each node's square-root degree, and every other eigenvalue has magnitude strictly less than 1.",
        "That single fact predicts the entire depth curve, measured here on a graph of two triangles joined by one bridge edge: with distinct initial features per triangle, one layer already blurs the split (a between-triangle-to-within-triangle distance ratio of 10.7 drops to 6.9), and by roughly 50 layers the ratio has collapsed to 0.82, converging to exactly 2/3 by around 100 layers and staying there through 400. Not 1 — two-thirds. The reason is in the eigenvector: every component that once distinguished the triangles decays away, and what survives is proportional to the square root of each node's degree, not to which triangle it sits in.",
        "This graph's own degrees make that concrete rather than abstract: the two bridge nodes each have one extra neighbor, giving them degree 4 while every other node has degree 3. At full convergence, both bridge nodes converge to the identical embedding — distance essentially zero — despite sitting in different triangles, because they share a degree. A same-triangle pair of different degrees never converges to the same point. Oversmoothing does not make a GCN blind; it makes it blind to everything except the one structural quantity — degree — that the propagation rule's fixed point is built from, which is why real GCNs stay shallow, typically two or three layers, rather than stacking dozens the way a CNN does.",
    ),
    steps = listOf(
        StepCard(1, "Build the Normalized Adjacency", "D⁻¹ᐟ² (A + I) D⁻¹ᐟ² — self-loops included, weighted by degree.", 0xFF14B8A6),
        StepCard(2, "One Layer: Aggregate, Then Transform", "H' = σ(A_norm H W) — average neighbors first, then apply a learned weight.", 0xFF3B82F6),
        StepCard(3, "Stack Layers to Reach Farther Neighbors", "Depth 1 reads one hop away; depth k reads k hops away.", 0xFF10B981),
        StepCard(4, "Watch Separation Collapse With Depth", "Cross-triangle vs. within-triangle distance: 10.7 → 6.9 → ... → 0.667.", 0xFFF59E0B),
        StepCard(5, "The Limit Is Provable: 2/3, Not 1", "The surviving signal is √degree, and this graph's bridge nodes share a degree.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Normalized adjacency", "A_norm = D̂⁻¹ᐟ² (A + I) D̂⁻¹ᐟ²", "D̂ is the degree matrix of A + I (self-loops included)."),
        FormulaEntry("GCN layer", "H⁽ˡ⁺¹⁾ = σ(A_norm H⁽ˡ⁾ W⁽ˡ⁾)", "Aggregate neighbors, then apply a shared linear map."),
        FormulaEntry("Fixed point", "A_norm v = v,  v ∝ √degree", "The top eigenvector — what survives infinite depth."),
        FormulaEntry("Measured limit", "separation → 2/3", "Not 1 — the two bridge nodes (same degree, different triangles) converge to the same point."),
    ),
    notationKey = listOf(
        NotationEntry("A, D̂", "adjacency matrix (with self-loops) and its degree matrix"),
        NotationEntry("A_norm", "the degree-normalized adjacency — one fixed weight per edge"),
        NotationEntry("H⁽ˡ⁾", "node feature matrix at layer l"),
        NotationEntry("oversmoothing", "node embeddings converging toward each other as depth grows"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Repeated aggregation, and the exact limit it converges to",
            accentColor = 0xFF14B8A6,
            code = """
                import numpy as np

                def normalized_adjacency(adj):
                    a_hat = adj + np.eye(len(adj))            # self-loops
                    d_hat = a_hat.sum(axis=1)
                    d_inv_sqrt = np.diag(1.0 / np.sqrt(d_hat))
                    return d_inv_sqrt @ a_hat @ d_inv_sqrt

                a_norm = normalized_adjacency(two_triangles_one_bridge)

                h = initial_features            # (6 nodes, 2 dims) -- two visibly different groups
                for depth in range(401):
                    if depth in (0, 1, 2, 5, 10, 20, 50, 100, 400):
                        print(depth, separation_ratio(h))   # depth = layers applied so far

                    h = a_norm @ h               # identity W, no nonlinearity: pure smoothing

                # depth   0: 10.67   (well separated)
                # depth   1:  6.93   (one layer already blurs it)
                # depth  20:  8.07   (a weak bridge slows the mixing rate)
                # depth  50:  0.82
                # depth 100:  0.667  <- converges here, exactly 2/3, and stays
                #
                # bridge_node_2 and bridge_node_3 (different triangles, same degree) end up at
                # essentially the same embedding -- verified, not eyeballed off the ratio alone.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("share", 0xFF3B82F6, "Social & Citation Networks", "Node classification where a node's label correlates with its neighbors' labels."),
        ApplicationCard("finance", 0xFF10B981, "Fraud & Recommendation", "Graphs of transactions or user-item interactions."),
        ApplicationCard("chip", 0xFFF59E0B, "Molecules", "Atoms as nodes, bonds as edges — property prediction over molecular graphs."),
        ApplicationCard("help", 0xFFEC4899, "Why GCNs Stay Shallow", "More layers means more oversmoothing, not more capacity, past a point measured here at just a few layers."),
    ),
    takeaways = listOf(
        "A GCN layer averages each node's neighbors (degree-weighted) and applies a shared weight — stacking layers reaches farther neighbors.",
        "Repeated averaging is graph Laplacian smoothing, and its fixed point is exact linear algebra: proportional to √degree, for a connected graph.",
        "Measured on two triangles joined by a bridge: separation drops from 10.7 (depth 0) through 6.9, 8.1, 0.82, converging to exactly 2/3 by depth ~100.",
        "The limit is 2/3, not 1 — the two bridge nodes share a degree despite sitting in different triangles, and converge to nearly the same embedding.",
        "This is the literal, computed reason GCNs are typically kept to two or three layers rather than stacked dozens deep.",
    ),
    crossLinks = listOf(
        CrossLink("gat", "Graph Attention Networks (GAT)"),
        CrossLink("graph", "Graphs"),
        CrossLink("graph_variants", "Graph Variants"),
        CrossLink("bayesian_networks", "Bayesian Networks"),
    ),
)
