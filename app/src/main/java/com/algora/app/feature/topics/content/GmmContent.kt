package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val gmmContent = TopicContent(
    topicId = "gmm",
    whatIsIt = listOf(
        "A Gaussian mixture model treats the data as drawn from several Gaussians and fits them by expectation-maximization. Each point gets a *responsibility* toward every component rather than a single label — soft clustering — and each component carries a full covariance matrix, so it can be elongated and tilted rather than round.",
        "That covariance is the concrete advantage over k-means, and the relationship between the two is exact rather than analogous. Force every covariance to be spherical and equal, and drive the responsibilities to hard 0/1 assignments, and EM reduces to Lloyd's algorithm precisely. k-means is a special case of this model, which is why it insists on circular clusters — it has no parameter capable of expressing anything else.",
        "Being a genuine probabilistic model brings things clustering algorithms generally lack. It is generative, so you can sample new data from it; it gives a likelihood, so BIC or AIC can choose the number of components rather than a silhouette heuristic; and the responsibilities are real posteriors that quantify how ambiguous each point's membership is. The failure modes are equally specific: the likelihood is unbounded, so a component can collapse onto a single point with vanishing variance and drive the likelihood to infinity — which is what the regularization term in every implementation exists to prevent. EM only finds a local optimum, so initialization matters, and a full covariance costs O(d²) parameters per component, which becomes unaffordable in high dimensions and is why the diagonal and tied variants exist.",
    ),
    steps = listOf(
        StepCard(1, "Initialize the Components", "Means, covariances and mixing weights. k-means is the usual seeding.", 0xFF3B82F6),
        StepCard(2, "E Step", "Compute each point's responsibility toward each component — a posterior, not a label.", 0xFF818CF8),
        StepCard(3, "M Step", "Re-estimate each component as a responsibility-weighted mean and covariance.", 0xFF60A5FA),
        StepCard(4, "Iterate", "Log-likelihood increases monotonically. It never decreases, which makes convergence easy to check.", 0xFF10B981),
        StepCard(5, "Choose k by BIC", "A likelihood means model selection is principled rather than a heuristic.", 0xFF14B8A6),
        StepCard(6, "Guard the Singularity", "A component collapsing onto one point sends the likelihood to infinity. Regularize.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Model", "p(x) = Σₖ πₖ·N(x | μₖ, Σₖ)", "A weighted sum of Gaussians."),
        FormulaEntry("Responsibility", "γ(zₙₖ) = πₖN(xₙ|μₖ,Σₖ) / Σⱼ πⱼN(xₙ|μⱼ,Σⱼ)", "The E step."),
        FormulaEntry("M step mean", "μₖ = Σₙ γₙₖxₙ / Σₙ γₙₖ", "Weighted by responsibility."),
        FormulaEntry("M step covariance", "Σₖ = Σₙ γₙₖ(xₙ−μₖ)(xₙ−μₖ)ᵀ / Nₖ", "The parameter k-means lacks."),
        FormulaEntry("Reduction to k-means", "Σₖ = σ²I, σ² → 0", "Responsibilities become 0/1."),
        FormulaEntry("BIC", "−2·ln L̂ + p·ln n", "Model selection over k and covariance type."),
    ),
    notationKey = listOf(
        NotationEntry("πₖ", "mixing weight of component k; they sum to 1"),
        NotationEntry("γₙₖ", "responsibility of component k for point n"),
        NotationEntry("EM", "expectation-maximization"),
        NotationEntry("covariance_type", "full, tied, diagonal or spherical — a complexity dial"),
        NotationEntry("singularity", "a component collapsing onto one point, with infinite likelihood"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Choosing k and the covariance type together",
            accentColor = 0xFF3B82F6,
            code = """
                from sklearn.mixture import GaussianMixture
                import numpy as np

                # A likelihood means BIC applies, so k is chosen rather than guessed at.
                results = {}
                for k in range(1, 9):
                    for cov in ("full", "tied", "diag", "spherical"):
                        gmm = GaussianMixture(
                            n_components=k,
                            covariance_type=cov,
                            reg_covar=1e-6,      # the singularity guard — do not set it to 0
                            n_init=5,            # EM is local; restart it
                            random_state=0,
                        ).fit(X)
                        results[(k, cov)] = gmm.bic(X)

                best = min(results, key=results.get)
                print(best, round(results[best], 1))

                # And what k-means cannot give you at all:
                gmm = GaussianMixture(n_components=best[0]).fit(X)
                proba = gmm.predict_proba(X)                  # per-point posteriors
                ambiguous = X[proba.max(axis=1) < 0.8]        # points genuinely between clusters
                new_samples, _ = gmm.sample(500)              # it is generative
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The singularity, and why reg_covar exists",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np
                from scipy.stats import multivariate_normal

                # A component centred exactly on one point, with its variance shrinking:
                point = np.array([0.0, 0.0])
                for var in (1e-1, 1e-2, 1e-4, 1e-8):
                    density = multivariate_normal(point, np.eye(2) * var).pdf(point)
                    print(var, f"{density:.3e}")
                # 1e-1 -> 1.6e+00
                # 1e-8 -> 1.6e+07   and it keeps going

                # The likelihood is UNBOUNDED: EM can always increase it by collapsing a
                # component onto a single point. It is not a bug in the optimizer — the
                # objective genuinely has no maximum. reg_covar adds a small constant to
                # every covariance diagonal, which puts a floor under the variance and makes
                # the problem well-posed.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("music", 0xFF3B82F6, "Speaker Recognition", "GMMs over cepstral features were the standard voice model for two decades before deep learning."),
        ApplicationCard("browser", 0xFF818CF8, "Background Subtraction", "Per-pixel mixtures model a background that flickers, so moving objects stand out against it."),
        ApplicationCard("chart", 0xFF10B981, "Density Estimation", "Being generative makes it useful well beyond clustering — sampling, anomaly scoring, imputation."),
    ),
    takeaways = listOf(
        "Full covariances let components be elongated and tilted; k-means is the spherical, hard-assignment special case.",
        "Responsibilities are genuine posteriors, so points between clusters are identifiable as such.",
        "A likelihood makes BIC-based selection of k principled rather than heuristic.",
        "The likelihood is unbounded — a component can collapse onto one point — which is what reg_covar prevents.",
    ),
    crossLinks = listOf(
        CrossLink("kmeans", "K-Means Clustering"),
        CrossLink("qda", "Quadratic Discriminant Analysis (QDA)"),
        CrossLink("gaussian_nb", "Gaussian Naive Bayes"),
        CrossLink("mean_shift", "Mean Shift Clustering"),
    ),
)
