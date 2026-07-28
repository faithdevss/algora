package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val factorAnalysisContent = TopicContent(
    topicId = "factor_analysis",
    whatIsIt = listOf(
        "Factor analysis and PCA are routinely used interchangeably and are not the same thing. PCA is a transformation: it rewrites the data in a new basis and makes no claim about how the data came to be. Factor analysis is a generative model — it asserts that each observed variable is a weighted sum of a few unobserved common factors plus a noise term private to that variable, x = Λf + ε, and then estimates Λ and the noise variances from the data.",
        "That private noise term is the whole difference, and it is not cosmetic. PCA has no ε, so its first component must account for every variable's total variance, including the part that is pure measurement error and shared with nothing. Factor analysis is allowed to say \"most of variable 2 is its own noise\" and set that variable's uniqueness high, leaving the factor to describe only what the variables genuinely have in common. Put another way: PCA explains variance, factor analysis explains covariance.",
        "This matters most where the variables are measurements of something with error — psychometric items, survey responses, sensor arrays, biomarker panels — which is exactly the tradition factor analysis grew up in. It also brings the model's own baggage. The solution is only determined up to a rotation of the factors (any orthogonal rotation of Λ fits identically, which is why varimax and oblimin exist and why the loadings you publish are a choice), and identification requires enough variables per factor. With three variables and one factor the system is exactly determined, which sounds convenient and in practice means the estimate has no averaging to stabilise it — a real caveat, visible in the simulation, and worth more than a footnote.",
    ),
    steps = listOf(
        StepCard(1, "Standardise the Variables", "Factor analysis is usually run on the correlation matrix, so units cannot decide which variable dominates.", 0xFFEC4899),
        StepCard(2, "Choose the Number of Factors", "Scree plot, parallel analysis, or theory. Over-extracting invents factors from noise.", 0xFFF472B6),
        StepCard(3, "Fit Λ and Ψ", "Maximum likelihood or principal axis factoring, so that ΛΛᵀ + Ψ reproduces the observed correlations.", 0xFF8B5CF6),
        StepCard(4, "Read the Uniquenesses", "ψᵢ is the share of variable i's variance that no factor explains — the diagnostic PCA cannot produce.", 0xFF6366F1),
        StepCard(5, "Rotate for Interpretability", "Varimax for uncorrelated factors, oblimin when factors may correlate. The fit does not change; the story does.", 0xFF10B981),
        StepCard(6, "Score the Cases", "Factor scores are estimated, not computed — unlike PCA scores, which are an exact projection.", 0xFF14B8A6),
    ),
    formulas = listOf(
        FormulaEntry("The model", "x = Λf + ε", "f ~ N(0, I), ε ~ N(0, Ψ) with Ψ diagonal."),
        FormulaEntry("Implied covariance", "Σ = ΛΛᵀ + Ψ", "The off-diagonals come only from Λ — Ψ is diagonal by assumption."),
        FormulaEntry("Communality", "hᵢ² = Σₖ λᵢₖ²", "Share of variable i explained by the common factors."),
        FormulaEntry("Uniqueness", "ψᵢ = 1 − hᵢ²", "On standardised variables. The part that is variable i's own."),
        FormulaEntry("One factor, three variables", "λ₁² = r₁₂r₁₃/r₂₃", "Exactly identified — and a ratio of small numbers, so high variance."),
        FormulaEntry("Rotational indeterminacy", "Λ and ΛR fit identically for any orthogonal R", "The loadings you report are a choice, not a discovery."),
        FormulaEntry("PCA by contrast", "Σ ≈ ΛΛᵀ", "No Ψ. The diagonal has to be absorbed by the components."),
    ),
    notationKey = listOf(
        NotationEntry("Λ", "loading matrix — how strongly each variable reflects each factor"),
        NotationEntry("f", "the common factors: latent, unobserved, standardised"),
        NotationEntry("Ψ", "diagonal matrix of uniquenesses — per-variable private variance"),
        NotationEntry("communality", "the complement of uniqueness; variance shared with the factors"),
        NotationEntry("varimax", "an orthogonal rotation chosen to make loadings near 0 or near 1"),
        NotationEntry("Heywood case", "a fitted uniqueness that comes out negative — a sign of misspecification"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The same data through both, and the number that separates them",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np
                from sklearn.decomposition import FactorAnalysis, PCA

                rng = np.random.default_rng(0)
                f = rng.normal(size=(2000, 1))
                loadings = np.array([[0.9, 0.55, 0.8, 0.85]])
                noise = np.array([0.10, 0.95, 0.16, 0.20])          # variable 2 is mostly noise
                X = f @ loadings + rng.normal(size=(2000, 4)) * np.sqrt(noise)

                fa = FactorAnalysis(n_components=1, random_state=0).fit(X)
                pca = PCA(n_components=1).fit(X)

                R = np.corrcoef(X, rowvar=False)
                def off_diag_error(L):
                    M = L.T @ L
                    return np.abs(M - R)[~np.eye(4, dtype=bool)].sum()

                # Score both on the thing they are both modelling: the correlations BETWEEN
                # variables. FA reproduces them; PCA's component is pulled off by the diagonal.
                Lfa = fa.components_ / np.sqrt(X.var(axis=0))
                Lpca = pca.components_ * np.sqrt(pca.explained_variance_) / np.sqrt(X.var(axis=0))
                print(round(off_diag_error(Lfa), 3))     # small
                print(round(off_diag_error(Lpca), 3))    # several times larger

                print(np.round(fa.noise_variance_ / X.var(axis=0), 2))   # uniqueness per variable
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Rotation changes the story without changing the fit",
            accentColor = 0xFF10B981,
            code = """
                from sklearn.decomposition import FactorAnalysis
                import numpy as np

                unrotated = FactorAnalysis(n_components=3, random_state=0).fit(X)
                varimax = FactorAnalysis(n_components=3, rotation="varimax", random_state=0).fit(X)

                # Identical fit — the log-likelihood is invariant to an orthogonal rotation of Λ.
                print(round(unrotated.score(X), 6), round(varimax.score(X), 6))

                # Different loadings, and it is the rotated ones that get interpreted and named.
                # Worth being explicit about: the factor labels in a published table are a
                # consequence of a rotation criterion someone chose, not something the data fixed.
                print(np.round(unrotated.components_, 2))
                print(np.round(varimax.components_, 2))
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("users", 0xFFEC4899, "Psychometrics", "The field it was invented for: Spearman's g, the Big Five, and every questionnaire whose items are noisy measurements of a construct."),
        ApplicationCard("finance", 0xFF8B5CF6, "Risk Models", "Decomposing asset returns into common factor exposure and idiosyncratic risk — the split that hedging depends on."),
        ApplicationCard("flask", 0xFF6366F1, "Biomarker Panels", "Assays with real measurement error, where treating that error as signal would distort every downstream conclusion."),
    ),
    takeaways = listOf(
        "Factor analysis is a generative model with a per-variable noise term; PCA is a transformation with none.",
        "PCA explains total variance, factor analysis explains shared variance — hence Σ = ΛΛᵀ + Ψ.",
        "Uniquenesses are the useful output: they say which variables are mostly their own noise.",
        "The solution is only identified up to rotation, so published loadings reflect a chosen criterion.",
        "Exact identification (three variables, one factor) is not the same as a stable estimate.",
    ),
    crossLinks = listOf(
        CrossLink("pca", "Principal Component Analysis (PCA)"),
        CrossLink("ica", "Independent Component Analysis (ICA)"),
        CrossLink("bias_variance", "Bias-Variance Tradeoff"),
        CrossLink("gmm", "Gaussian Mixture Models (GMM)"),
    ),
)
