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

internal val svmRbfContent = TopicContent(
    topicId = "svm_rbf",
    figure = Figure(
        caption = "K(x, x′) = exp(−γ‖x − x′‖²) at three γ, over distances 0 to 3. γ is not a " +
            "strength dial, it is a radius: a point stops voting once K falls to about 0.05, which " +
            "happens at distance 0.77 for γ = 5 and 1.73 for γ = 1. Raising γ 50× shrinks that " +
            "radius by √50 ≈ 7×. At γ = 0.1 the kernel is still 0.41 at distance 3 — every point " +
            "influences every other, and the fit behaves almost linearly; at γ = 5 the influence is " +
            "gone within one unit, so the boundary becomes islands drawn around individual training " +
            "points. That is the same curve explaining both failure modes.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "γ = 0.1",
                    listOf(
                        FigurePoint(0f, 1f), FigurePoint(0.125f, 0.986f), FigurePoint(0.25f, 0.945f),
                        FigurePoint(0.375f, 0.881f), FigurePoint(0.5f, 0.799f),
                        FigurePoint(0.625f, 0.704f), FigurePoint(0.75f, 0.603f),
                        FigurePoint(0.875f, 0.502f), FigurePoint(1f, 0.407f),
                    ),
                    tone = FigureTone.Muted,
                ),
                FigureSeries(
                    "γ = 1",
                    listOf(
                        FigurePoint(0f, 1f), FigurePoint(0.125f, 0.869f), FigurePoint(0.25f, 0.570f),
                        FigurePoint(0.375f, 0.282f), FigurePoint(0.5f, 0.105f),
                        FigurePoint(0.625f, 0.030f), FigurePoint(0.75f, 0.006f),
                        FigurePoint(0.875f, 0.001f), FigurePoint(1f, 0f),
                    ),
                ),
                FigureSeries(
                    "γ = 5",
                    listOf(
                        FigurePoint(0f, 1f), FigurePoint(0.125f, 0.495f), FigurePoint(0.25f, 0.060f),
                        FigurePoint(0.375f, 0.002f), FigurePoint(0.5f, 0f),
                        FigurePoint(0.75f, 0f), FigurePoint(1f, 0f),
                    ),
                    tone = FigureTone.Warn,
                ),
            ),
            markers = listOf(
                FigurePoint(0.258f, 0.05f, "0.77"),
                FigurePoint(0.577f, 0.05f, "1.73"),
            ),
            xLabel = "‖x − x′‖  (0 to 3)",
            yLabel = "K(x, x′)",
        ),
    ),
    whatIsIt = listOf(
        "A linear SVM can only draw a hyperplane, which is useless on data like two concentric rings. The fix is to map the points into a higher-dimensional space where they *are* linearly separable, and draw the hyperplane there — its preimage back in the original space is a curve.",
        "The kernel trick is that you never build that space. Written in its dual form, the SVM touches the data only through inner products xᵢᵀxⱼ, so replacing every inner product with a kernel function K(xᵢ,xⱼ) is enough to fit in the mapped space without ever computing a mapped coordinate. For the RBF kernel that space is infinite-dimensional, and the computation stays an O(n²) pass over pairs of points.",
        "Two hyperparameters control it and they interact, which is why they must be tuned jointly on a grid rather than one at a time. γ sets how far a single training point's influence reaches: small γ makes the kernel so smooth it behaves nearly linearly, large γ shrinks each point's influence to its immediate neighbourhood so the boundary becomes islands around individual training points — memorization wearing a curve's clothing. C sets how much margin violation is tolerated: large C insists on classifying training points correctly, small C accepts errors for a wider margin. High γ with high C is the classic overfitting corner.",
    ),
    steps = listOf(
        StepCard(1, "Write the Dual", "Reformulate so the data appears only inside inner products xᵢᵀxⱼ.", 0xFF8B5CF6),
        StepCard(2, "Swap in a Kernel", "Replace every inner product with K(xᵢ,xⱼ). Nothing else in the algorithm changes.", 0xFF818CF8),
        StepCard(3, "Solve the QP", "SMO optimizes pairs of dual variables in closed form until the KKT conditions hold.", 0xFF60A5FA),
        StepCard(4, "Keep the Support Vectors", "Only points with αᵢ > 0 matter. The rest can be discarded entirely.", 0xFF10B981),
        StepCard(5, "Predict by Similarity", "Sum each support vector's label, weighted by α and by its kernel similarity to the query.", 0xFFF59E0B),
        StepCard(6, "Tune γ and C Together", "They interact, so a joint grid is required — sweeping one at a time finds the wrong corner.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("RBF kernel", "K(x,x′) = exp(−γ‖x − x′‖²)", "Similarity decaying with distance."),
        FormulaEntry("Dual objective", "max Σαᵢ − ½ΣΣ αᵢαⱼyᵢyⱼK(xᵢ,xⱼ)", "Data enters only through K."),
        FormulaEntry("Constraints", "0 ≤ αᵢ ≤ C, Σαᵢyᵢ = 0", "The box, and the balance condition."),
        FormulaEntry("Decision", "f(x) = Σ αᵢyᵢK(xᵢ,x) + b", "A weighted vote of the support vectors."),
        FormulaEntry("γ heuristic", "γ = 1/(p·Var(X))", "sklearn's 'scale' default, and a sane starting point."),
        FormulaEntry("Mercer's condition", "K positive semi-definite", "What makes a function a valid kernel."),
    ),
    notationKey = listOf(
        NotationEntry("γ", "kernel width — how far one point's influence reaches"),
        NotationEntry("C", "penalty for margin violation"),
        NotationEntry("αᵢ", "dual variable; non-zero exactly for support vectors"),
        NotationEntry("support vector", "a training point that actually shapes the boundary"),
        NotationEntry("SMO", "Sequential Minimal Optimization — the standard solver"),
        NotationEntry("Mercer kernel", "a kernel corresponding to some inner product space"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Tuning the two hyperparameters jointly",
            accentColor = 0xFF8B5CF6,
            code = """
                from sklearn.svm import SVC
                from sklearn.pipeline import make_pipeline
                from sklearn.preprocessing import StandardScaler
                from sklearn.model_selection import GridSearchCV
                import numpy as np

                # Scaling is not optional: the RBF kernel is a function of Euclidean distance,
                # so a feature with a larger range silently dominates every similarity.
                grid = GridSearchCV(
                    make_pipeline(StandardScaler(), SVC(kernel="rbf")),
                    {
                        "svc__gamma": np.logspace(-4, 2, 13),
                        "svc__C": np.logspace(-2, 4, 13),
                    },
                    cv=5,
                )
                grid.fit(X, y)
                print(grid.best_params_)
                print(grid.best_estimator_[-1].n_support_)   # support vectors per class
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The trick itself, in three lines",
            accentColor = 0xFF10B981,
            code = """
                # A degree-2 polynomial kernel, done both ways.
                x, z = np.array([1.0, 2.0]), np.array([3.0, 4.0])

                # Explicitly: build the feature map, then take an inner product.
                def phi(v):
                    a, b = v
                    return np.array([a*a, b*b, np.sqrt(2)*a*b])
                print(phi(x) @ phi(z))          # 3 multiplications became 9

                # Implicitly: the kernel gives the identical number without phi existing.
                print((x @ z) ** 2)             # same value, 2 multiplications

                # For the RBF kernel phi is infinite-dimensional, so the left column is not
                # merely slower — it cannot be written down at all. Only the right one exists.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DecisionSurface,
    applications = listOf(
        ApplicationCard("flask", 0xFF8B5CF6, "Bioinformatics", "Protein and gene classification, where sample counts are small and SVMs remain competitive with deep models."),
        ApplicationCard("browser", 0xFF818CF8, "Image Classification", "The default before deep learning, and still the sensible choice on a few hundred labelled images."),
        ApplicationCard("chart", 0xFF10B981, "Where It Runs Out", "The kernel matrix is n×n, so training is O(n²)–O(n³) — impractical much past ~100k samples."),
    ),
    takeaways = listOf(
        "The dual touches data only through inner products, so swapping in a kernel fits a curved boundary for free.",
        "The RBF kernel's implicit feature space is infinite-dimensional and is never constructed.",
        "Only support vectors matter — the trained model can discard everything else.",
        "γ and C interact and must be tuned jointly; high γ with high C is the overfitting corner.",
    ),
    crossLinks = listOf(
        CrossLink("svm", "Support Vector Machines (Linear)"),
        CrossLink("nu_svc", "Nu-Support Vector Classification"),
        CrossLink("knn", "k-Nearest Neighbors"),
        CrossLink("pca", "PCA"),
    ),
)
