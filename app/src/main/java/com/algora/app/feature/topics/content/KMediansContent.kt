package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val kMediansContent = TopicContent(
    topicId = "k_medians",
    whatIsIt = listOf(
        "K-medians runs the identical alternating loop as k-means — assign every point to a centre, recompute the centres, repeat — and changes only two things. Distance becomes Manhattan rather than squared Euclidean, and the centre becomes the per-coordinate median rather than the mean.",
        "Those two changes are the same change viewed twice. The mean is the value minimizing squared error and the median is the value minimizing absolute error, so choosing L1 as the distance forces the median as the centre. The consequence is robustness: doubling how far an outlier sits moves a mean proportionally and moves a median not at all, because a median only counts how many points are on each side, not how far away they are.",
        "The trade is not free. Squared error has a closed-form minimizer and is differentiable, which is why k-means is fast and why so much theory attaches to it; the median has neither, so k-medians is slower per iteration. It is also worth separating from k-medoids, which is often confused with it: k-medoids requires the centre to be an actual data point, works with any distance matrix at all, and is more expensive again. K-medians computes a per-coordinate median, which is generally not a point in the dataset.",
    ),
    steps = listOf(
        StepCard(1, "Pick k Initial Centres", "Same initialization problem as k-means — k-means++ style seeding helps here too.", 0xFF3B82F6),
        StepCard(2, "Assign by Manhattan Distance", "Σ|xᵢ − cᵢ| rather than the squared Euclidean sum.", 0xFF818CF8),
        StepCard(3, "Take Per-Coordinate Medians", "Each dimension independently. The result is usually not a data point.", 0xFF60A5FA),
        StepCard(4, "Repeat to Convergence", "Same alternating structure, same guarantee of a monotone decrease in cost.", 0xFF10B981),
        StepCard(5, "Check Against k-means", "If the two agree, the data had no outliers worth worrying about.", 0xFF14B8A6),
        StepCard(6, "Do Not Confuse It With k-medoids", "Medoids must be actual data points and accept arbitrary distances; medians need not and do not.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("k-means objective", "min Σ ‖x − c‖²", "Minimized by the mean."),
        FormulaEntry("k-medians objective", "min Σ ‖x − c‖₁", "Minimized by the median."),
        FormulaEntry("Manhattan distance", "Σⱼ |xⱼ − cⱼ|", "Sums coordinates rather than squaring them."),
        FormulaEntry("Breakdown point", "median 50%, mean 0%", "Half the data can be corrupted before a median fails."),
        FormulaEntry("Probabilistic reading", "Laplace vs Gaussian noise", "L1 is ML under Laplace, L2 under Gaussian."),
        FormulaEntry("Complexity", "O(nkd) per iteration plus a sort", "Slightly worse than k-means, same order."),
    ),
    notationKey = listOf(
        NotationEntry("L1 / L2", "absolute versus squared error"),
        NotationEntry("breakdown point", "the fraction of corrupted data an estimator survives"),
        NotationEntry("k-medoids / PAM", "centres constrained to be actual data points"),
        NotationEntry("Lloyd's algorithm", "the assign/update loop shared by all of these"),
        NotationEntry("marginal median", "the per-coordinate median, which is what this uses"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The whole difference, in one loop",
            accentColor = 0xFF3B82F6,
            code = """
                import numpy as np

                def lloyd(X, k, use_median, iters=50, seed=0):
                    rng = np.random.default_rng(seed)
                    centres = X[rng.choice(len(X), k, replace=False)]

                    for _ in range(iters):
                        if use_median:
                            d = np.abs(X[:, None, :] - centres[None]).sum(axis=2)   # L1
                        else:
                            d = ((X[:, None, :] - centres[None]) ** 2).sum(axis=2)  # L2
                        labels = d.argmin(axis=1)

                        for j in range(k):
                            members = X[labels == j]
                            if len(members):
                                # The only other line that differs.
                                centres[j] = (np.median(members, axis=0) if use_median
                                              else members.mean(axis=0))
                    return labels, centres

                # Two lines out of a dozen. Everything structural is shared.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why the median does not move",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                clean = np.array([1.0, 2.0, 3.0, 4.0, 5.0])
                for outlier in (6, 60, 600, 6000):
                    data = np.append(clean, outlier)
                    print(outlier, round(data.mean(), 2), round(np.median(data), 2))
                # 6    -> mean 3.50, median 3.5
                # 60   -> mean 12.5, median 3.5
                # 600  -> mean 102.5, median 3.5
                # 6000 -> mean 1002.5, median 3.5

                # The mean tracks the outlier without bound. The median only ever cares how
                # many points sit on each side of it, which is why its breakdown point is 50%
                # and the mean's is zero — a single point can take the mean anywhere.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("finance", 0xFF3B82F6, "Financial Segmentation", "Income and spend distributions have long right tails, where a mean centre sits above almost every member."),
        ApplicationCard("map", 0xFF818CF8, "Facility Location", "Manhattan distance is literal on a street grid, and the cost being minimized is real travel."),
        ApplicationCard("flask", 0xFF10B981, "Sensor Data", "Occasional wild readings are expected, and a median centre absorbs them without a cleaning step."),
    ),
    takeaways = listOf(
        "Manhattan distance and the median are the same choice stated twice — L1 is minimized by the median.",
        "A median has a 50% breakdown point against the mean's zero, which is the whole robustness argument.",
        "The cost is speed: no closed form, not differentiable, slower per iteration.",
        "k-medoids is a different algorithm — its centres must be real data points and it accepts any distance.",
    ),
    crossLinks = listOf(
        CrossLink("kmeans", "K-Means Clustering"),
        CrossLink("k_modes", "K-Modes"),
        CrossLink("quantile_regression", "Quantile Regression"),
        CrossLink("robust_regression", "Robust Regression (RANSAC)"),
    ),
)
