package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val ldaContent = TopicContent(
    topicId = "lda",
    whatIsIt = listOf(
        "Linear Discriminant Analysis models each class as a Gaussian and classifies by asking which class density is higher at a point. Its one strong assumption is that every class shares the same covariance matrix — the clouds may sit in different places, but they must have the same shape and orientation.",
        "That assumption is what makes it linear, and the algebra is worth following once. Comparing two log-densities means subtracting them, and each contains a quadratic term −½xᵀΣ⁻¹x. When Σ is shared, those terms are identical and cancel exactly, leaving only terms linear in x. So the decision boundary is a hyperplane — not because anyone imposed linearity, but because it fell out of a shared covariance.",
        "LDA is generative where logistic regression is discriminative: it models how each class produced its data, rather than modelling the boundary directly. When the Gaussian assumption roughly holds, that extra structure makes it more statistically efficient — it needs less data to reach the same accuracy, and it degrades more gracefully on small samples. When the assumption fails, logistic regression is the safer choice because it never made the assumption. LDA is also a supervised dimensionality reducer: it projects onto at most k−1 directions that maximize between-class relative to within-class scatter, which is the operation PCA does not perform because PCA has never been told the labels.",
    ),
    steps = listOf(
        StepCard(1, "Estimate Class Means", "One mean vector per class — the centre of each cloud.", 0xFF8B5CF6),
        StepCard(2, "Pool One Covariance", "Average the per-class covariances, weighted by class size. This is the assumption.", 0xFF818CF8),
        StepCard(3, "Subtract the Log-Densities", "The shared quadratic terms cancel, and only linear terms survive.", 0xFF60A5FA),
        StepCard(4, "Get a Hyperplane", "The boundary is w = Σ⁻¹(μ₁ − μ₀), with priors setting the offset.", 0xFF10B981),
        StepCard(5, "Shrink if p Is Large", "With many features the pooled covariance is badly estimated; Ledoit-Wolf shrinkage fixes it.", 0xFFF59E0B),
        StepCard(6, "Use It to Project", "The same scatter ratio gives at most k−1 supervised discriminant directions.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Class model", "p(x|y=k) = N(μₖ, Σ)", "Same Σ for every class — the whole assumption."),
        FormulaEntry("Discriminant", "δₖ(x) = xᵀΣ⁻¹μₖ − ½μₖᵀΣ⁻¹μₖ + ln πₖ", "Linear in x."),
        FormulaEntry("Boundary", "w = Σ⁻¹(μ₁ − μ₀)", "Not the line between means unless Σ is spherical."),
        FormulaEntry("Pooled covariance", "Σ = Σₖ (nₖ−1)Σₖ / (n−K)", "Size-weighted average."),
        FormulaEntry("Parameters", "Kp + p(p+1)/2", "One covariance total, not one per class."),
        FormulaEntry("Fisher criterion", "max wᵀS_Bw / wᵀS_Ww", "Between-class over within-class scatter."),
    ),
    notationKey = listOf(
        NotationEntry("Σ", "the shared (pooled) covariance matrix"),
        NotationEntry("μₖ", "mean of class k"),
        NotationEntry("πₖ", "prior probability of class k"),
        NotationEntry("S_B, S_W", "between-class and within-class scatter"),
        NotationEntry("generative", "models p(x|y); discriminative models p(y|x) directly"),
        NotationEntry("shrinkage", "pulling the covariance estimate toward a scaled identity"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Classification and supervised projection",
            accentColor = 0xFF8B5CF6,
            code = """
                from sklearn.discriminant_analysis import LinearDiscriminantAnalysis
                import numpy as np

                # With p large relative to n, the pooled covariance is nearly singular and its
                # inverse amplifies noise. Ledoit-Wolf picks the shrinkage weight analytically.
                lda = LinearDiscriminantAnalysis(solver="lsqr", shrinkage="auto").fit(X, y)
                print(lda.score(X_test, y_test))

                # The same fit is also a supervised projection, capped at K-1 dimensions
                # because that is the rank of the between-class scatter matrix.
                projector = LinearDiscriminantAnalysis(n_components=2).fit(X, y)
                Z = projector.transform(X)     # separates classes; PCA would not have tried
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why the boundary is not the perpendicular bisector",
            accentColor = 0xFF10B981,
            code = """
                # A common misreading: "LDA splits halfway between the class means". It does
                # not — Sigma^-1 rotates the direction whenever features are correlated.
                mu0, mu1 = np.array([0.0, 0.0]), np.array([2.0, 2.0])
                Sigma = np.array([[1.0, 0.9],
                                  [0.9, 1.0]])      # strongly correlated features

                naive = mu1 - mu0                    # [2, 2] — the direction between means
                w = np.linalg.solve(Sigma, mu1 - mu0)
                print(naive, w)                      # w is [1.05, 1.05] here, but for an
                                                     # asymmetric Sigma it tilts substantially

                # The correlation structure decides which direction actually separates the
                # classes. Whitening first is what the inverse covariance is doing.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DecisionSurface,
    applications = listOf(
        ApplicationCard("flask", 0xFF8B5CF6, "Small-Sample Problems", "Medical and chemometric datasets with few samples per class, where the Gaussian structure pays for itself."),
        ApplicationCard("chart", 0xFF818CF8, "Supervised Dimensionality Reduction", "Projecting to K−1 dimensions that separate classes, which PCA does not attempt."),
        ApplicationCard("browser", 0xFF10B981, "Face Recognition", "\"Fisherfaces\" — LDA applied after PCA — beat plain eigenfaces precisely by using the labels."),
    ),
    takeaways = listOf(
        "LDA assumes all classes share one covariance; that cancellation is what makes the boundary linear.",
        "It is generative, so it is more data-efficient than logistic regression when the assumption roughly holds.",
        "The boundary direction is Σ⁻¹(μ₁ − μ₀), not the line between the means.",
        "It doubles as a supervised projection onto at most K−1 discriminant directions.",
    ),
    crossLinks = listOf(
        CrossLink("qda", "Quadratic Discriminant Analysis (QDA)"),
        CrossLink("logistic_regression", "Logistic Regression"),
        CrossLink("pca", "PCA"),
        CrossLink("naive_bayes", "Naive Bayes"),
    ),
)
