package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val icaContent = TopicContent(
    topicId = "ica",
    whatIsIt = listOf(
        "Independent Component Analysis answers a different question from PCA, and the difference is not a matter of degree. PCA asks which directions carry the most variance, and its answer is an orthogonal basis because that is what maximising variance under an orthogonality constraint produces. ICA asks which directions carry statistically independent signals — and there is no reason those should be at right angles to each other.",
        "The canonical setting is the cocktail party: several microphones each pick up a different linear mixture of several speakers, and the task is to recover the speakers. Written as X = AS, with S the sources and A an unknown mixing matrix, ICA estimates an unmixing matrix W ≈ A⁻¹ without ever seeing S or A. PCA cannot do this even in principle: the mixing directions are generally not orthogonal, and PCA's are orthogonal by construction, so the true answer is not in the set of answers PCA can return.",
        "What makes it possible is non-Gaussianity, and that is worth stating as a hard condition rather than an assumption. By the central limit theorem, a sum of independent variables is more Gaussian than its parts — so among all the linear combinations of the observed signals, the ones that are *least* Gaussian are the ones closest to being a single source. Maximising non-Gaussianity therefore unmixes. The corollary is exact and unforgiving: if the sources really are Gaussian, ICA cannot work at all, because a rotation of a spherical Gaussian is the same spherical Gaussian and there is nothing left to distinguish one rotation from another.",
    ),
    steps = listOf(
        StepCard(1, "Centre the Data", "Subtract the mean. ICA is about shape, and a constant offset is not shape.", 0xFFEC4899),
        StepCard(2, "Whiten It", "PCA, then divide each component by its standard deviation. Covariance becomes the identity.", 0xFFF472B6),
        StepCard(3, "Notice What's Left", "After whitening, every remaining candidate unmixing is a pure rotation — a d(d−1)/2 parameter search instead of d².", 0xFF8B5CF6),
        StepCard(4, "Maximise Non-Gaussianity", "Fixed-point iteration on w ← E[zg(wᵀz)] − E[g′(wᵀz)]w, with g = tanh or u³.", 0xFF6366F1),
        StepCard(5, "Deflate and Repeat", "Orthogonalise each new component against the ones already found, until you have as many as you asked for.", 0xFF10B981),
        StepCard(6, "Accept the Ambiguities", "Scale and order are unidentifiable. Never read a component's magnitude or its index as meaningful.", 0xFF14B8A6),
    ),
    formulas = listOf(
        FormulaEntry("Model", "X = AS", "A is the unknown mixing matrix, S the independent sources."),
        FormulaEntry("Goal", "S ≈ WX,  W ≈ A⁻¹", "Estimated without observing A or S."),
        FormulaEntry("Whitening", "Z = Λ^(−½)EᵀX", "E and Λ from the eigendecomposition of the covariance."),
        FormulaEntry("FastICA update", "w⁺ = E[zg(wᵀz)] − E[g′(wᵀz)]w", "Then normalise. g(u) = tanh(u) is the robust default."),
        FormulaEntry("Negentropy", "J(y) = H(y_gauss) − H(y)", "The principled objective; kurtosis is its cheap, outlier-sensitive proxy."),
        FormulaEntry("Excess kurtosis", "κ = E[y⁴]/E[y²]² − 3", "Zero for a Gaussian; −1.2 for a uniform, positive for a heavy tail."),
        FormulaEntry("Scale ambiguity", "A·S = (A/c)·(cS)", "Why component magnitude carries no information."),
    ),
    notationKey = listOf(
        NotationEntry("A", "mixing matrix — what the world did to the sources"),
        NotationEntry("W", "unmixing matrix — what ICA estimates"),
        NotationEntry("whitening", "the PCA step that reduces the search to a rotation"),
        NotationEntry("g(u)", "the nonlinearity — tanh, u³ or a Gaussian derivative"),
        NotationEntry("negentropy", "distance from Gaussianity; the quantity being maximised"),
        NotationEntry("deflation", "finding components one at a time, orthogonalising against the previous ones"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Unmixing three signals, and PCA failing on the same data",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np
                from sklearn.decomposition import FastICA, PCA
                from scipy import signal

                t = np.linspace(0, 8, 2000)
                S = np.c_[np.sin(2 * t), signal.sawtooth(3 * t), np.sign(np.cos(5 * t))]
                S += 0.15 * np.random.default_rng(0).normal(size=S.shape)

                A = np.array([[1.0, 1.0, 0.5], [0.5, 2.0, 1.0], [1.5, 1.0, 2.0]])   # not orthogonal
                X = S @ A.T

                ica = FastICA(n_components=3, whiten="unit-variance", random_state=0).fit(X)
                S_hat = ica.transform(X)
                pca = PCA(n_components=3).fit_transform(X)

                def best_match(est):
                    # Correlate every estimate against every true source; order and sign are
                    # ambiguous, so score by the best absolute correlation per source.
                    C = np.abs(np.corrcoef(est.T, S.T)[:3, 3:])
                    return C.max(axis=1).round(3)

                print(best_match(S_hat))   # ~[0.99 0.99 0.99]
                print(best_match(pca))     # much lower — PCA's axes are orthogonal, A's are not
            """.trimIndent(),
        ),
        CodeBlock(
            title = "FastICA by hand: whiten, then rotate",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                def fast_ica(X, n_components, iters=200, tol=1e-10):
                    X = X - X.mean(axis=0)
                    cov = np.cov(X, rowvar=False)
                    vals, vecs = np.linalg.eigh(cov)
                    W_white = (vecs / np.sqrt(vals)).T          # covariance becomes I
                    Z = X @ W_white.T

                    W = []
                    for _ in range(n_components):
                        w = np.random.default_rng(0).normal(size=Z.shape[1])
                        w /= np.linalg.norm(w)
                        for _ in range(iters):
                            u = Z @ w
                            g, g_prime = np.tanh(u), 1 - np.tanh(u) ** 2
                            w_new = (Z * g[:, None]).mean(axis=0) - g_prime.mean() * w
                            for prev in W:                       # deflation
                                w_new -= (w_new @ prev) * prev
                            w_new /= np.linalg.norm(w_new)
                            if abs(abs(w_new @ w) - 1) < tol:
                                w = w_new
                                break
                            w = w_new
                        W.append(w)
                    return Z @ np.array(W).T

                # If the sources are Gaussian this returns an arbitrary rotation and nothing more.
                # That is not a bug in the implementation — it is the identifiability condition.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("flask", 0xFFEC4899, "EEG and MEG Artifact Removal", "Eye blinks and heartbeat are independent of brain activity, so ICA isolates them into their own components, which are then zeroed and the signal rebuilt. Standard practice in neuroscience labs."),
        ApplicationCard("music", 0xFF8B5CF6, "Blind Source Separation", "The cocktail-party problem: several microphones, several speakers, no knowledge of the room."),
        ApplicationCard("finance", 0xFF6366F1, "Factor Extraction", "Driving factors behind correlated asset returns, where the interesting structure is not the orthogonal one."),
        ApplicationCard("Image", 0xFF10B981, "Natural Image Statistics", "ICA on image patches recovers oriented edge filters resembling V1 receptive fields — a result that mattered well beyond machine learning."),
    ),
    takeaways = listOf(
        "PCA finds uncorrelated (orthogonal) directions; ICA finds statistically independent ones, which are generally not orthogonal.",
        "Whitening reduces the problem to a rotation search — that is why ICA starts with PCA.",
        "Non-Gaussianity is the objective, because a mixture is more Gaussian than its parts.",
        "If the sources are Gaussian, ICA is impossible, not merely difficult.",
        "Component scale and order are unidentifiable — never read magnitude or index as meaning.",
    ),
    crossLinks = listOf(
        CrossLink("pca", "Principal Component Analysis (PCA)"),
        CrossLink("factor_analysis", "Factor Analysis"),
        CrossLink("kernel_pca", "Kernel PCA"),
        CrossLink("autoencoders", "Autoencoders"),
    ),
)
