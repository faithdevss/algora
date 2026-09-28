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

internal val lleContent = TopicContent(
    topicId = "lle",
    figure = Figure(
        caption = "A 120-point folded curve in the plane, with k swept from 3 to 26. Purity is the " +
            "share of each point's k neighbours that are genuinely nearby *along* the curve; the " +
            "short-circuit count is the number of neighbour links that jump between folds, " +
            "normalised against its maximum of 676. Both are flat until k = 6 and then go in " +
            "opposite directions — one link at k = 8, ten at k = 10, and 142 at k = 14, by which " +
            "point purity has fallen to 0.89 and the weights are describing a shortcut through empty " +
            "space rather than the surface. Nothing about the embedding warns you: the eigenproblem " +
            "solves exactly as cleanly on a short-circuited graph as on a good one.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "neighbourhood purity",
                    listOf(
                        FigurePoint(0f, 1f), FigurePoint(0.043f, 1f), FigurePoint(0.087f, 1f),
                        FigurePoint(0.130f, 1f), FigurePoint(0.217f, 0.999f),
                        FigurePoint(0.304f, 0.988f), FigurePoint(0.478f, 0.889f),
                        FigurePoint(0.739f, 0.750f), FigurePoint(1f, 0.664f),
                    ),
                ),
                FigureSeries(
                    "short circuits",
                    listOf(
                        FigurePoint(0f, 0f), FigurePoint(0.130f, 0f), FigurePoint(0.217f, 0.001f),
                        FigurePoint(0.304f, 0.015f), FigurePoint(0.478f, 0.210f),
                        FigurePoint(0.739f, 0.607f), FigurePoint(1f, 1f),
                    ),
                    tone = FigureTone.Warn,
                ),
            ),
            markers = listOf(
                FigurePoint(0.130f, 0f, "k=6: none"),
                FigurePoint(0.478f, 0.210f, "k=14: 142", FigureTone.Warn),
            ),
            xLabel = "k  (3 → 26)",
            yLabel = "purity / links",
        ),
    ),
    whatIsIt = listOf(
        "Locally Linear Embedding rests on one observation: a curved surface is flat if you look at a small enough piece of it. So although the data as a whole is not linear, each point *is* approximately a linear combination of its few nearest neighbours — and the coefficients of that combination describe the local geometry without reference to any coordinate system.",
        "That last part is what makes the method work. The reconstruction weights are constrained to sum to one, which makes them invariant to translating, rotating and rescaling the patch they were computed in. They therefore describe the patch's shape rather than its position, and the same weights remain meaningful in a completely different space. LLE finds low-dimensional coordinates in which each point is still reconstructed by its neighbours using those same weights — which turns out to be an eigenvector problem, solvable in closed form with no gradient descent, no learning rate and no random restarts.",
        "The eigenvector detail is worth knowing because it is a common source of confusion. You take the *bottom* eigenvectors of M = (I−W)ᵀ(I−W), not the top, since you are minimising a quadratic form rather than maximising one; and the very bottom one is always the constant vector with eigenvalue zero, which carries no information and is discarded. The failure mode is equally worth knowing: everything depends on k, and if the neighbourhoods short-circuit — connecting parts of the manifold that are close in the ambient space but far along the surface — the weights stop describing a curve and the embedding collapses. This topic's simulation shows exactly that, with the number of short-circuit links and the recovered rank correlation both measured at two values of k.",
    ),
    steps = listOf(
        StepCard(1, "Find k Neighbours", "The only geometry the algorithm ever sees. Too small and the graph disconnects; too large and it short-circuits.", 0xFFEC4899),
        StepCard(2, "Solve the Weights", "Minimise ‖xᵢ − Σⱼwᵢⱼxⱼ‖² subject to Σⱼwᵢⱼ = 1, per point, from the local Gram matrix.", 0xFFF472B6),
        StepCard(3, "Regularise the Gram Matrix", "When k exceeds the ambient dimension it is singular; add a small multiple of its trace to the diagonal.", 0xFF8B5CF6),
        StepCard(4, "Build M", "M = (I−W)ᵀ(I−W). Sparse, symmetric, positive semi-definite.", 0xFF6366F1),
        StepCard(5, "Take the Bottom Eigenvectors", "Discard the constant one at eigenvalue zero; the next d are the embedding.", 0xFF10B981),
        StepCard(6, "Check Against the Truth", "If a ground-truth parameter exists, score the recovered ordering — the picture alone will not tell you.", 0xFF14B8A6),
    ),
    formulas = listOf(
        FormulaEntry("Reconstruction error", "ε(W) = Σᵢ ‖xᵢ − Σⱼ wᵢⱼxⱼ‖²", "Only over the k neighbours; wᵢⱼ = 0 otherwise."),
        FormulaEntry("Constraint", "Σⱼ wᵢⱼ = 1", "What buys invariance to translation, rotation and scale."),
        FormulaEntry("Local Gram matrix", "Cⱼₖ = (xᵢ−xⱼ)·(xᵢ−xₖ)", "The weights come from solving Cw = 1 and renormalising."),
        FormulaEntry("Regularisation", "C ← C + δ·tr(C)·I", "Needed whenever k > d, where C is singular."),
        FormulaEntry("Embedding cost", "Φ(Y) = Σᵢ ‖yᵢ − Σⱼ wᵢⱼyⱼ‖² = tr(YᵀMY)", "Same weights, new coordinates."),
        FormulaEntry("The eigenproblem", "M = (I−W)ᵀ(I−W)", "Bottom d+1 eigenvectors; drop the constant one."),
        FormulaEntry("Cost", "O(n k³) for weights, plus a sparse eigensolve", "Sparse M is what keeps the eigenstep affordable."),
    ),
    notationKey = listOf(
        NotationEntry("W", "reconstruction weights — sparse, rows summing to one"),
        NotationEntry("M", "(I−W)ᵀ(I−W); the matrix whose bottom eigenvectors are the embedding"),
        NotationEntry("short-circuit", "a neighbour link across a fold, joining points far apart along the manifold"),
        NotationEntry("manifold", "a surface that is locally flat even where it is globally curved"),
        NotationEntry("swiss roll", "the standard test manifold — a rolled sheet with a known intrinsic parameter"),
        NotationEntry("bottom eigenvector", "smallest eigenvalue; here a minimisation, not a maximisation"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Unrolling a swiss roll, and scoring the result",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np
                from sklearn.datasets import make_swiss_roll
                from sklearn.manifold import LocallyLinearEmbedding
                from sklearn.decomposition import PCA
                from scipy.stats import spearmanr

                X, t = make_swiss_roll(n_samples=1500, noise=0.05, random_state=0)

                lle = LocallyLinearEmbedding(n_neighbors=12, n_components=2, random_state=0)
                Z = lle.fit_transform(X)
                P = PCA(n_components=2).fit_transform(X)

                # t is the true position along the roll. A method that unrolled it recovers t;
                # one that projected it does not. Score, do not eyeball.
                print(round(abs(spearmanr(Z[:, 0], t).statistic), 3))   # high
                print(round(abs(spearmanr(P[:, 0], t).statistic), 3))   # low — PCA folds the roll
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The weights step, and the failure k causes",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np
                from sklearn.neighbors import NearestNeighbors

                def lle_weights(X, k, reg=1e-3):
                    n = len(X)
                    idx = NearestNeighbors(n_neighbors=k + 1).fit(X).kneighbors(X)[1][:, 1:]
                    W = np.zeros((n, n))
                    for i in range(n):
                        Z = X[idx[i]] - X[i]
                        C = Z @ Z.T
                        C += np.eye(k) * reg * np.trace(C)   # singular whenever k > d
                        w = np.linalg.solve(C, np.ones(k))
                        W[i, idx[i]] = w / w.sum()           # the sum-to-one constraint
                    return W, idx

                # k is the whole method. Too large and neighbourhoods reach across a fold:
                for k in (6, 12, 40):
                    _, idx = lle_weights(X, k)
                    # On the swiss roll, |Δt| between linked points reveals short-circuits.
                    jumps = np.abs(t[idx] - t[:, None])
                    print(k, "median link |Δt|", round(float(np.median(jumps)), 2),
                          "worst", round(float(jumps.max()), 2))
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("Image", 0xFFEC4899, "Pose and Appearance Manifolds", "Images of one object under varying viewpoint or lighting lie on a low-dimensional surface, and LLE recovers its coordinates."),
        ApplicationCard("music", 0xFF8B5CF6, "Speech Feature Analysis", "Articulatory trajectories are smooth curves in a high-dimensional feature space."),
        ApplicationCard("flask", 0xFF6366F1, "Molecular Conformations", "Reaction coordinates as a manifold through configuration space — the intrinsic parameter chemists actually want."),
    ),
    takeaways = listOf(
        "It assumes only local linearity: each point is a weighted combination of its neighbours.",
        "The sum-to-one constraint makes the weights invariant to translation, rotation and scale, which is what lets them transfer to a new space.",
        "Closed form via eigenvectors — no learning rate, no initialisation, no restarts.",
        "Take the *bottom* eigenvectors of (I−W)ᵀ(I−W) and discard the constant one.",
        "k decides everything: short-circuits across a fold collapse the embedding, and the plot will not warn you.",
    ),
    crossLinks = listOf(
        CrossLink("tsne", "t-SNE"),
        CrossLink("umap", "UMAP"),
        CrossLink("pca", "Principal Component Analysis (PCA)"),
        CrossLink("knn", "k-Nearest Neighbors"),
    ),
)
