package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val extraTreesContent = TopicContent(
    topicId = "extra_trees",
    whatIsIt = listOf(
        "Extremely Randomized Trees take a random forest's randomness one step further. A forest samples which features to consider at each split but still searches for the best threshold among them; Extra Trees draws the threshold at random too, and simply keeps the best of those random candidates.",
        "That sounds like vandalism and is a deliberate trade. Each tree is worse in isolation — it is not making the locally optimal cut — but the trees are much less correlated with each other, and bagging's variance formula ρσ² + (1−ρ)σ²/B is floored by exactly that ρ. Lowering the correlation lowers the floor, which more models never can. The second effect is bias: random thresholds bias each tree slightly, so Extra Trees typically has marginally higher bias and lower variance than a forest, and which wins depends on the dataset.",
        "The other reason it gets used is speed. Threshold search is the expensive part of growing a tree — sorting each feature's values at every node — and Extra Trees skips it entirely, often training several times faster on the same data. One implementation detail worth knowing: scikit-learn's default is `bootstrap=False`, so unlike a random forest each tree sees the *whole* dataset, and all the decorrelation comes from the random cuts rather than from resampling. That also means there is no out-of-bag score unless you turn bootstrapping on.",
    ),
    steps = listOf(
        StepCard(1, "Take the Full Dataset", "No bootstrap by default — the randomness comes from the splits instead.", 0xFFF59E0B),
        StepCard(2, "Sample Features at the Node", "Same as a random forest: consider a random subset, not all of them.", 0xFF818CF8),
        StepCard(3, "Draw Thresholds at Random", "One random cut point per candidate feature, drawn without consulting the labels.", 0xFF60A5FA),
        StepCard(4, "Keep the Best of Those", "Score the random candidates and take the best. It is still a choice, just from a random menu.", 0xFF10B981),
        StepCard(5, "Grow Fully and Repeat", "Deep trees, many of them, each one worse and far less correlated.", 0xFF14B8A6),
        StepCard(6, "Average", "The lower correlation lowers the variance floor that more trees could never reach.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Ensemble variance", "ρσ² + (1−ρ)σ²/B", "Extra Trees attacks ρ, not B."),
        FormulaEntry("Forest split", "argmax over all thresholds of the sampled features", "Exhaustive within the subset."),
        FormulaEntry("Extra Trees split", "argmax over K random thresholds", "One random cut per sampled feature."),
        FormulaEntry("Cost per node", "O(K) vs O(p·n log n)", "No sorting — this is where the speed is."),
        FormulaEntry("Bias / variance", "slightly higher bias, lower variance", "The direction of the trade."),
        FormulaEntry("Default bootstrap", "False in scikit-learn", "So no OOB score unless enabled."),
    ),
    notationKey = listOf(
        NotationEntry("K", "features considered per split (max_features)"),
        NotationEntry("ρ", "correlation between trees in the ensemble"),
        NotationEntry("decorrelation", "making ensemble members disagree more"),
        NotationEntry("randomized threshold", "a cut point drawn without looking at the labels"),
        NotationEntry("OOB", "out-of-bag; unavailable here without bootstrap=True"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Comparing the two on time as well as accuracy",
            accentColor = 0xFFF59E0B,
            code = """
                import time
                from sklearn.ensemble import ExtraTreesClassifier, RandomForestClassifier
                from sklearn.model_selection import cross_val_score

                for name, model in (
                    ("forest", RandomForestClassifier(n_estimators=300, n_jobs=-1)),
                    ("extra ", ExtraTreesClassifier(n_estimators=300, n_jobs=-1)),
                ):
                    start = time.perf_counter()
                    score = cross_val_score(model, X, y, cv=5).mean()
                    print(name, round(score, 4), f"{time.perf_counter() - start:.2f}s")
                # Accuracy is usually within noise of each other; the wall-clock gap is not.

                # bootstrap defaults to False here, which is the opposite of RandomForest.
                # Turn it on if you want the out-of-bag estimate:
                ExtraTreesClassifier(bootstrap=True, oob_score=True)
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Measuring the decorrelation directly",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                def mean_tree_correlation(ensemble, X):
                    # Each tree's predictions as one column, then the average off-diagonal
                    # correlation — this is the rho in the variance formula.
                    preds = np.column_stack([t.predict(X) for t in ensemble.estimators_])
                    corr = np.corrcoef(preds, rowvar=False)
                    off = corr[~np.eye(len(corr), dtype=bool)]
                    return np.nanmean(off)

                forest = RandomForestClassifier(n_estimators=100).fit(X, y)
                extra = ExtraTreesClassifier(n_estimators=100).fit(X, y)
                print(round(mean_tree_correlation(forest, X), 4),
                      round(mean_tree_correlation(extra, X), 4))
                # Extra Trees should come out lower, which is the entire mechanism —
                # and it is the variance floor, so it is what more trees cannot buy.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFFF59E0B, "Large Feature Sets", "No threshold sorting means the per-node cost stops scaling with the number of distinct values."),
        ApplicationCard("flask", 0xFF818CF8, "High-Noise Data", "Random cuts are less able to latch onto noise in a particular sample than an optimized cut is."),
        ApplicationCard("chart", 0xFF10B981, "Fast Baselines", "Often a few times quicker than a forest at comparable accuracy — a cheap second opinion."),
    ),
    takeaways = listOf(
        "Random forests randomize which features are considered; Extra Trees randomizes the threshold too.",
        "Individually worse trees, but lower correlation — and correlation is the variance floor more trees cannot beat.",
        "Skipping threshold search is where the speed advantage comes from.",
        "scikit-learn defaults to bootstrap=False, so each tree sees all the data and there is no OOB score.",
    ),
    crossLinks = listOf(
        CrossLink("random_forest", "Random Forests"),
        CrossLink("bagging", "Bagging (Bootstrap Aggregating)"),
        CrossLink("decision_trees", "Decision Trees"),
        CrossLink("isolation_forest", "Isolation Forest (Anomaly Detection)"),
    ),
)
