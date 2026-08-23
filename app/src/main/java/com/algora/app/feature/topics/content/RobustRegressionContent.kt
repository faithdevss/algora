package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBar
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val robustRegressionContent = TopicContent(
    topicId = "robust_regression",
    figure = Figure(
        caption = "Breakdown point is the contamination fraction that defeats an estimator entirely. " +
            "OLS is at 0% — one arbitrarily bad point out of a million can drag the fit arbitrarily " +
            "far, since squaring the residual squares its influence too. Theil-Sen, the median of " +
            "pairwise slopes, holds to 29%. RANSAC's consensus-set refit passes 50%: it can be right " +
            "even when more than half the data is contamination, because it never averages outliers in.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("OLS", 0.0f, FigureTone.Warn),
                FigureBar("Theil-Sen", 0.29f, FigureTone.Muted),
                FigureBar("RANSAC", 0.55f),
            ),
            yLabel = "breakdown point",
        ),
    ),
    whatIsIt = listOf(
        "Least squares has a fatal property when data is contaminated: because error is squared, a point ten units off the line contributes a hundred times more than a point one unit off. A single arbitrarily bad observation can therefore drag the fit arbitrarily far. In the formal language, OLS has a breakdown point of 0% — one bad point out of a million is enough.",
        "RANSAC attacks this by refusing to average outliers in at all. It repeatedly samples the minimum number of points needed to define a model (two, for a line), fits that model, and counts how many of the remaining points fall within a threshold of it. The sample producing the largest consensus set wins, and the model is then refitted on that consensus set alone. Points outside it are never weighted down — they are excluded. Its breakdown point can exceed 50%, so it works even when most of the data is contamination.",
        "It is not the only option, and the choice matters. M-estimators (Huber, Tukey) reweight rather than exclude — smooth, deterministic, efficient when contamination is mild, but they inherit a low breakdown point. Theil-Sen takes the median of pairwise slopes and breaks down at 29%. RANSAC is randomized, so it is not reproducible without a fixed seed, and it needs a threshold you must set from knowledge of the measurement noise. The rule of thumb: mild fat tails call for Huber; a genuine second population in the data calls for RANSAC.",
    ),
    steps = listOf(
        StepCard(1, "Sample a Minimal Set", "Two points define a line. Sample the fewest that determine the model.", 0xFF6366F1),
        StepCard(2, "Fit That Sample Exactly", "No least squares yet — this candidate only exists to nominate inliers.", 0xFF818CF8),
        StepCard(3, "Count the Consensus", "Every point within the threshold of the candidate is an inlier.", 0xFF60A5FA),
        StepCard(4, "Keep the Best Consensus", "Repeat for enough trials that a clean sample is near-certain.", 0xFF10B981),
        StepCard(5, "Refit on Inliers Only", "Least squares on the consensus set. The outliers never enter the sum.", 0xFFF59E0B),
        StepCard(6, "Set the Threshold Honestly", "It encodes what counts as measurement noise. Too wide admits outliers; too narrow rejects real data.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("OLS loss", "Σ rᵢ²", "Quadratic — an outlier's influence grows without limit."),
        FormulaEntry("Huber loss", "r²/2 for |r| ≤ δ, else δ(|r| − δ/2)", "Quadratic near zero, linear in the tails."),
        FormulaEntry("Breakdown point", "OLS 0%, Theil-Sen 29%, RANSAC > 50%", "Fraction of contamination tolerable."),
        FormulaEntry("Trials needed", "N = log(1−p)/log(1−wˢ)", "p confidence, w inlier fraction, s sample size."),
        FormulaEntry("Inlier test", "|yᵢ − ŷᵢ| ≤ t", "Threshold t comes from the noise scale."),
        FormulaEntry("MAD scale", "σ̂ = 1.4826 · median|rᵢ − median(r)|", "A robust noise estimate for choosing t."),
    ),
    notationKey = listOf(
        NotationEntry("breakdown point", "the contamination fraction at which an estimator fails entirely"),
        NotationEntry("consensus set", "the inliers agreeing with a candidate model"),
        NotationEntry("t", "inlier distance threshold"),
        NotationEntry("M-estimator", "a robust fit that reweights rather than excludes"),
        NotationEntry("MAD", "median absolute deviation — a robust spread estimate"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "RANSAC, and how many trials it needs",
            accentColor = 0xFF6366F1,
            code = """
                import numpy as np

                def trials_needed(inlier_fraction, sample_size=2, confidence=0.99):
                    # Probability a given sample is all-inliers is w**s; solve for the number
                    # of draws that makes at least one clean sample near-certain.
                    return int(np.ceil(np.log(1 - confidence) /
                                       np.log(1 - inlier_fraction ** sample_size)))

                print(trials_needed(0.8))   # 3   — light contamination is cheap
                print(trials_needed(0.5))   # 16
                print(trials_needed(0.3))   # 49  — still entirely tractable

                from sklearn.linear_model import RANSACRegressor, LinearRegression
                fit = RANSACRegressor(
                    LinearRegression(),
                    residual_threshold=1.0,     # in the units of y — set it from the noise
                    random_state=0,             # randomized: pin the seed or results move
                ).fit(X, y)
                print(fit.inlier_mask_.sum(), "inliers of", len(y))
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Choosing between Huber and RANSAC",
            accentColor = 0xFF10B981,
            code = """
                from sklearn.linear_model import HuberRegressor, RANSACRegressor

                # Huber: every point contributes, but large residuals contribute linearly
                # instead of quadratically. Deterministic, smooth, statistically efficient
                # when the noise is merely heavy-tailed.
                huber = HuberRegressor(epsilon=1.35).fit(X, y)   # 1.35 ~= 95% efficiency

                # RANSAC: outliers are excluded outright. The right choice when the bad
                # points come from a genuinely different process — a second surface, a
                # mislabelled batch, a sensor glitch — rather than a fat tail.
                ransac = RANSACRegressor(random_state=0).fit(X, y)

                # Rule of thumb: fat tails -> Huber. A second population -> RANSAC.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("browser", 0xFF6366F1, "Computer Vision", "RANSAC's original home: estimating a homography from feature matches where most matches are wrong."),
        ApplicationCard("map", 0xFF818CF8, "LiDAR & Point Clouds", "Fitting the ground plane when a third of the returns are vehicles, foliage and noise."),
        ApplicationCard("finance", 0xFF10B981, "Sensor Fusion", "GPS multipath produces occasional wild readings that a squared-error fit would follow straight into a wall."),
    ),
    takeaways = listOf(
        "Squared error gives a single outlier unbounded influence — OLS breaks down at 0% contamination.",
        "RANSAC samples minimal subsets, keeps the largest consensus set, and refits on inliers alone.",
        "Its breakdown point exceeds 50%, but it is randomized and needs a noise-scale threshold.",
        "Huber and other M-estimators reweight instead of excluding: better for fat tails, worse for a genuine second population.",
    ),
    crossLinks = listOf(
        CrossLink("linear_regression", "Linear Regression"),
        CrossLink("quantile_regression", "Quantile Regression"),
        CrossLink("dbscan", "DBSCAN"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
