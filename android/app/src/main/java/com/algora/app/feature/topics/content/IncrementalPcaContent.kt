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

internal val incrementalPcaContent = TopicContent(
    topicId = "incremental_pca",
    figure = Figure(
        caption = "The page's lab: 18 rows of two columns arrive 4 at a time, and the line is how " +
            "far the running first principal component sits from the one batch PCA finds on all " +
            "18. After the first batch it is 2.5° off, then 3.1° after 8 rows (an early estimate " +
            "can get worse before it settles), 1.3° after 12, 0.4° after 16 and 0.0° after all " +
            "18. Between batches the method keeps only the count, the mean and the summed " +
            "products, 6 numbers in all, against 36 for the whole matrix. Those sums are exact, " +
            "so the streamed answer is batch PCA's answer, not an approximation of it.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "running PC1 error",
                    listOf(
                        FigurePoint(0.222f, 0.806f), FigurePoint(0.444f, 1f), FigurePoint(0.667f, 0.419f),
                        FigurePoint(0.889f, 0.129f), FigurePoint(1f, 0f),
                    ),
                ),
            ),
            markers = listOf(
                FigurePoint(0.444f, 1f, "3.1°", FigureTone.Warn),
                FigurePoint(1f, 0f, "0.0°"),
            ),
            xLabel = "rows seen, 4 → 18",
            yLabel = "angle off batch PC1, 0 to 3.1°",
        ),
    ),
    whatIsIt = listOf(
        "Ordinary PCA asks for the whole data matrix at once: it forms the covariance from every row, then eigendecomposes it. On a dataset that fits in memory this is not worth a second thought. On ten million rows, or on a stream that has no end, it is the reason the job does not run — and the fix is not a faster machine but a different arrangement of the same arithmetic.",
        "Incremental PCA processes the data in batches and keeps only a fixed-size summary between them. The simplest form is exact: a running count, a running mean and a d×d matrix of summed products are enough to reconstruct the covariance of everything seen so far, and none of them grow with the number of rows. Memory becomes O(d²) regardless of n, and the components after the final batch are the components batch PCA would have produced — not an approximation of them.",
        "scikit-learn's `IncrementalPCA` takes a different route to the same place, merging an SVD per batch rather than accumulating second moments. That version is approximate, and deliberately: forming XᵀX squares the condition number, so on wide or badly scaled data the covariance route loses precision that the SVD route keeps. This is the usual shape of the trade — exactness on well-conditioned data against numerical robustness on everything else. Either way, the property that matters is the same: the estimate is usable at every point in the stream, and it converges toward the full-data answer as data arrives rather than only existing at the end.",
    ),
    steps = listOf(
        StepCard(1, "Fix the Batch Size", "Big enough to be more than the number of components you want; small enough to fit. This is the only real knob.", 0xFFEC4899),
        StepCard(2, "Update the Running Mean", "The mean shifts with every batch, and the centring has to shift with it — a stale mean quietly biases every component after it.", 0xFFF472B6),
        StepCard(3, "Accumulate the Second Moments", "A d×d sum of products, or a merged SVD. Either way the batch itself is discarded afterwards.", 0xFF8B5CF6),
        StepCard(4, "Decompose What You Have", "Components are available after every batch, not only at the end.", 0xFF6366F1),
        StepCard(5, "Watch the Estimate Settle", "Early batches give a component that swings; the angle to the final answer shrinks as coverage grows.", 0xFF10B981),
        StepCard(6, "Transform in the Same Streaming Way", "Projection is a matrix multiply per batch, so the reduced data never has to exist all at once either.", 0xFF14B8A6),
    ),
    formulas = listOf(
        FormulaEntry("Running mean", "μₙ = μₙ₋₁ + (xₙ − μₙ₋₁)/n", "Numerically better than dividing a running sum at the end."),
        FormulaEntry("Second moment", "S = Σᵢ xᵢxᵢᵀ", "d×d, and independent of how many rows contributed."),
        FormulaEntry("Covariance from the accumulator", "C = S/n − μμᵀ", "Exactly the batch covariance once every row has passed."),
        FormulaEntry("Components", "C = VΛVᵀ", "The same eigenproblem batch PCA solves."),
        FormulaEntry("Memory", "O(d²), independent of n", "The whole point. Batch PCA is O(nd) before it starts."),
        FormulaEntry("Cost per batch", "O(bd²)", "b rows per batch; the eigendecomposition is O(d³) and amortised."),
    ),
    notationKey = listOf(
        NotationEntry("b", "batch size — rows processed before the summary is updated"),
        NotationEntry("μₙ", "running mean after n rows"),
        NotationEntry("S", "accumulated matrix of summed outer products"),
        NotationEntry("partial_fit", "the streaming API: called once per batch, holding no data between calls"),
        NotationEntry("condition number", "σ₁/σₙ — squared by forming XᵀX, which is why the SVD route exists"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Streaming a dataset that never has to be resident",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np
                from sklearn.decomposition import PCA, IncrementalPCA

                X = np.random.default_rng(0).normal(size=(20_000, 50)) @ np.random.default_rng(1).normal(size=(50, 50))

                ipca = IncrementalPCA(n_components=10)
                for start in range(0, len(X), 1000):
                    ipca.partial_fit(X[start:start + 1000])   # one batch resident at a time

                batch = PCA(n_components=10).fit(X)

                # Components agree up to sign, which is all a component is defined up to.
                cos = np.abs(np.sum(ipca.components_ * batch.components_, axis=1))
                print(np.round(cos, 4))          # ~1.0 for the leading components

                print(ipca.explained_variance_ratio_[:3].round(4))
                print(batch.explained_variance_ratio_[:3].round(4))
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The exact accumulator, and why it is exact",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                class StreamingPCA:
                    def __init__(self, d):
                        self.n = 0
                        self.mean = np.zeros(d)
                        self.S = np.zeros((d, d))     # sum of outer products; never grows with n

                    def partial_fit(self, batch):
                        for x in batch:
                            self.n += 1
                            self.mean += (x - self.mean) / self.n
                            self.S += np.outer(x, x)
                        return self

                    def components(self, k):
                        C = self.S / self.n - np.outer(self.mean, self.mean)
                        vals, vecs = np.linalg.eigh(C)
                        return vecs[:, ::-1][:, :k].T, vals[::-1][:k]

                # After every row has passed, C is the batch covariance exactly — this is algebra,
                # not convergence. The caveat is conditioning: forming S squares the condition
                # number of X, so on wide or badly scaled data prefer the merged-SVD variant.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFFEC4899, "Out-of-Core Preprocessing", "Reducing a dataset larger than RAM before it reaches a model — the case `partial_fit` was added for."),
        ApplicationCard("globe", 0xFF8B5CF6, "Streaming Telemetry", "Sensor and clickstream pipelines where there is no final row to wait for and the projection has to be usable now."),
        ApplicationCard("Image", 0xFF6366F1, "Large Image Corpora", "Fitting a PCA basis over millions of patches without materialising the patch matrix."),
    ),
    takeaways = listOf(
        "Memory is O(d²) in the feature count and independent of the number of rows.",
        "The accumulator form is exact once every row has passed — the same components batch PCA gives.",
        "scikit-learn's merged-SVD variant trades that exactness for numerical robustness on wide data.",
        "Components exist after every batch, so a stream has a usable projection at all times.",
        "The running mean has to be updated with the data; centring against a stale mean biases everything downstream.",
    ),
    crossLinks = listOf(
        CrossLink("pca", "Principal Component Analysis (PCA)"),
        CrossLink("svd", "Singular Value Decomposition (SVD)"),
        CrossLink("birch", "BIRCH"),
        CrossLink("gradient_descent_variants", "Gradient Descent Variants"),
    ),
)
