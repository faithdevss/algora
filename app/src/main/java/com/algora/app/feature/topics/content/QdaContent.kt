package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val qdaContent = TopicContent(
    topicId = "qda",
    figure = Figure(
        caption = "Two classes with the same mean and different spreads — σ₀ = 0.6 inside σ₁ = 1.5 — " +
            "over the square from −2.4 to 2.4. P(class 1) is 0.138 at the origin and 1.000 at every " +
            "corner, and the densities are equal on the circle r = 0.886: a closed boundary. The " +
            "outlined block is the region QDA assigns to the tight class. LDA cannot draw this at " +
            "all — pooling one covariance forces the −½xᵀΣ⁻¹x terms to cancel, and every boundary it " +
            "can express is a straight line, which here would have to cut the ring somewhere and be " +
            "wrong on the rest.",
        shape = FigureShape.Heatmap(
            values = listOf(
                listOf(1.000f, 1.000f, 0.996f, 0.993f, 0.996f, 1.000f, 1.000f),
                listOf(1.000f, 0.984f, 0.870f, 0.760f, 0.870f, 0.984f, 1.000f),
                listOf(0.996f, 0.870f, 0.416f, 0.252f, 0.416f, 0.870f, 0.996f),
                listOf(0.993f, 0.760f, 0.252f, 0.138f, 0.252f, 0.760f, 0.993f),
                listOf(0.996f, 0.870f, 0.416f, 0.252f, 0.416f, 0.870f, 0.996f),
                listOf(1.000f, 0.984f, 0.870f, 0.760f, 0.870f, 0.984f, 1.000f),
                listOf(1.000f, 1.000f, 0.996f, 0.993f, 0.996f, 1.000f, 1.000f),
            ),
            rowLabels = listOf("2.4", "1.6", "0.8", "0", "−0.8", "−1.6", "−2.4"),
            colLabels = listOf("−2.4", "−1.6", "−0.8", "0", "0.8", "1.6", "2.4"),
            marks = listOf(
                FigureCell(2, 2), FigureCell(2, 3), FigureCell(2, 4),
                FigureCell(3, 2), FigureCell(3, 3), FigureCell(3, 4),
                FigureCell(4, 2), FigureCell(4, 3), FigureCell(4, 4),
            ),
            legend = "1.0 = certain the broad class; outlined = assigned to the tight one",
        ),
    ),
    whatIsIt = listOf(
        "QDA is LDA with one assumption removed: each class gets its own covariance matrix instead of sharing a pooled one. Everything else is identical — still Gaussian per class, still classify by the higher density.",
        "Removing the shared covariance changes the boundary's type. In LDA the quadratic terms −½xᵀΣ⁻¹x cancel when the two log-densities are subtracted, because they are the same term. With Σ₀ ≠ Σ₁ they do not cancel, and what survives is quadratic in x — so the boundary becomes a conic: an ellipse, parabola or hyperbola depending on the two shapes. One class tightly clustered inside a broader one produces a closed elliptical boundary that no linear model can express.",
        "The cost is parameters, and it grows fast. Each covariance holds p(p+1)/2 free values, so K classes need K·p(p+1)/2 covariance parameters against LDA's single p(p+1)/2. At p = 50 that is 1,275 per class, and any class with fewer than p samples has a singular covariance whose inverse does not exist. This is the concrete bias-variance tradeoff: LDA is biased when the shapes genuinely differ, QDA has high variance when data per class is thin, and Regularized Discriminant Analysis interpolates between them with a shrinkage parameter you can cross-validate.",
    ),
    steps = listOf(
        StepCard(1, "Estimate a Mean per Class", "Same as LDA — one centre per cloud.", 0xFF8B5CF6),
        StepCard(2, "Estimate a Covariance per Class", "The one change. Each class keeps its own shape and orientation.", 0xFF818CF8),
        StepCard(3, "Note What No Longer Cancels", "Different Σₖ means the quadratic terms survive the subtraction.", 0xFF60A5FA),
        StepCard(4, "Get a Conic Boundary", "Ellipse, parabola or hyperbola, set by how the two covariances relate.", 0xFF10B981),
        StepCard(5, "Count the Cost", "K·p(p+1)/2 covariance parameters. Thin classes give singular estimates.", 0xFFF59E0B),
        StepCard(6, "Regularize Between the Two", "RDA shrinks each Σₖ toward the pooled Σ — a dial from QDA to LDA.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Class model", "p(x|y=k) = N(μₖ, Σₖ)", "Per-class covariance."),
        FormulaEntry("Discriminant", "δₖ(x) = −½ln|Σₖ| − ½(x−μₖ)ᵀΣₖ⁻¹(x−μₖ) + ln πₖ", "Quadratic in x."),
        FormulaEntry("Boundary", "xᵀAx + bᵀx + c = 0", "A conic section."),
        FormulaEntry("Parameters", "K·p(p+1)/2 + Kp", "Against LDA's p(p+1)/2 + Kp."),
        FormulaEntry("Singularity risk", "nₖ < p ⟹ Σₖ not invertible", "A hard failure, not a soft one."),
        FormulaEntry("RDA", "Σₖ(α) = αΣₖ + (1−α)Σ_pooled", "α = 1 is QDA, α = 0 is LDA."),
    ),
    notationKey = listOf(
        NotationEntry("Σₖ", "covariance of class k"),
        NotationEntry("|Σₖ|", "determinant — the log-det term LDA does not have"),
        NotationEntry("conic", "ellipse, parabola or hyperbola"),
        NotationEntry("RDA", "Regularized Discriminant Analysis"),
        NotationEntry("Mahalanobis distance", "(x−μ)ᵀΣ⁻¹(x−μ), distance in units of spread"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "QDA, and the regularization it usually needs",
            accentColor = 0xFF8B5CF6,
            code = """
                from sklearn.discriminant_analysis import QuadraticDiscriminantAnalysis
                from sklearn.model_selection import cross_val_score
                import numpy as np

                # reg_param shrinks each class covariance toward a scaled identity. Without it,
                # any class with fewer samples than features has a singular covariance and
                # sklearn emits a collinearity warning before producing nonsense.
                for reg in (0.0, 0.01, 0.1, 0.5, 1.0):
                    score = cross_val_score(
                        QuadraticDiscriminantAnalysis(reg_param=reg), X, y, cv=5
                    ).mean()
                    print(reg, round(score, 4))
                # reg=1.0 makes every covariance spherical, which is close to Gaussian
                # naive Bayes; reg=0 is unregularized QDA.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "When LDA wins despite being wrong",
            accentColor = 0xFFEC4899,
            code = """
                from sklearn.discriminant_analysis import (
                    LinearDiscriminantAnalysis, QuadraticDiscriminantAnalysis)
                rng = np.random.default_rng(0)

                # Genuinely different covariances, so LDA's assumption is FALSE and QDA's
                # is TRUE. QDA still loses at small n, because estimating two full
                # covariances from very few points is worse than pooling a wrong one.
                def sample(n):
                    a = rng.multivariate_normal([0, 0], [[1, 0], [0, 1]], n)
                    b = rng.multivariate_normal([2, 2], [[4, 1.8], [1.8, 1]], n)
                    return np.vstack([a, b]), np.r_[np.zeros(n), np.ones(n)]

                for n in (15, 40, 200, 1000):
                    X, y = sample(n)
                    l = cross_val_score(LinearDiscriminantAnalysis(), X, y, cv=5).mean()
                    q = cross_val_score(QuadraticDiscriminantAnalysis(), X, y, cv=5).mean()
                    print(n, round(l, 3), round(q, 3))
                # The crossover point is the bias-variance tradeoff, made concrete.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DecisionSurface,
    applications = listOf(
        ApplicationCard("flask", 0xFF8B5CF6, "Heteroscedastic Classes", "Disease groups that genuinely vary more than healthy ones — a difference LDA cannot represent."),
        ApplicationCard("map", 0xFF818CF8, "Remote Sensing", "Land-cover classes with distinct spectral spreads, and enough pixels to estimate each covariance well."),
        ApplicationCard("chart", 0xFF10B981, "Bias-Variance, Concretely", "LDA versus QDA is the textbook tradeoff with an actual crossover you can measure."),
    ),
    takeaways = listOf(
        "Per-class covariances stop the quadratic terms cancelling, so the boundary becomes a conic.",
        "Cost is K·p(p+1)/2 covariance parameters, and a class with fewer than p samples is singular.",
        "QDA can lose to LDA even when its assumption is the correct one, if data per class is thin.",
        "RDA shrinks toward the pooled covariance and gives a cross-validatable dial between the two.",
    ),
    crossLinks = listOf(
        CrossLink("lda", "Linear Discriminant Analysis (LDA)"),
        CrossLink("bias_variance", "Bias-Variance Tradeoff"),
        CrossLink("naive_bayes", "Naive Bayes"),
        CrossLink("regularization", "Regularization (L1 / L2)"),
    ),
)
