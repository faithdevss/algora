package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val lightgbmContent = TopicContent(
    topicId = "lightgbm",
    whatIsIt = listOf(
        "LightGBM optimizes the same objective as XGBoost and changes two things about how trees are built. Both changes are about speed, and one of them also changes the shape of the resulting model.",
        "The first is leaf-wise growth. XGBoost grows level-wise — every node at a depth is split before descending — which keeps trees balanced and spends effort on nodes with little left to gain. LightGBM instead splits whichever leaf anywhere in the tree offers the largest gain, so for the same number of leaves it reaches lower loss but produces deep, asymmetric trees. That is why `num_leaves` and `min_data_in_leaf` are the parameters that matter here, and why `max_depth` alone will not stop it overfitting a small dataset.",
        "The second is histogram binning: continuous features are bucketed into about 255 bins once, up front, so split search costs O(bins) instead of O(sorted values) and the binned data fits in far less memory. Two further tricks build on it — GOSS keeps all the large-gradient rows and subsamples the small-gradient ones, since rows the model already fits well contribute little to the next split; and EFB bundles mutually exclusive sparse features into a single column, which is what makes one-hot-heavy data cheap. The result is typically several times faster than XGBoost at comparable accuracy, with a stronger tendency to overfit small data.",
    ),
    steps = listOf(
        StepCard(1, "Bin the Features Once", "~255 buckets per feature, computed up front and reused every tree.", 0xFFF59E0B),
        StepCard(2, "Build Histograms per Node", "Accumulate gradients per bin — this is the split search.", 0xFF818CF8),
        StepCard(3, "Subtract for the Sibling", "A child's histogram is the parent's minus its sibling's. Half the work is free.", 0xFF60A5FA),
        StepCard(4, "Split the Best Leaf Anywhere", "Not the next level — the single highest-gain leaf in the whole tree.", 0xFF10B981),
        StepCard(5, "Constrain by Leaves, Not Depth", "num_leaves and min_data_in_leaf are the real controls.", 0xFF14B8A6),
        StepCard(6, "Sample and Bundle", "GOSS keeps the large gradients; EFB merges mutually exclusive sparse features.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Leaf-wise", "split argmax over all current leaves", "Depth is whatever results."),
        FormulaEntry("Level-wise", "split every node at depth d first", "XGBoost's default — balanced trees."),
        FormulaEntry("Histogram cost", "O(#bins) per feature per node", "Against O(n log n) for exact sorting."),
        FormulaEntry("Histogram subtraction", "hist(child) = hist(parent) − hist(sibling)", "Only one child is ever built."),
        FormulaEntry("GOSS", "keep top a% by |gradient|, sample b% of the rest", "Small-gradient rows are already well fitted."),
        FormulaEntry("Leaf budget", "num_leaves ≤ 2^max_depth", "Exceeding it is how leaf-wise overfits."),
    ),
    notationKey = listOf(
        NotationEntry("leaf-wise", "best-first growth, ignoring depth"),
        NotationEntry("num_leaves", "the primary complexity control"),
        NotationEntry("GOSS", "Gradient-based One-Side Sampling"),
        NotationEntry("EFB", "Exclusive Feature Bundling"),
        NotationEntry("bin", "a bucket of a continuous feature's range"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Tuning it as a leaf-wise learner, not a depth-wise one",
            accentColor = 0xFFF59E0B,
            code = """
                import lightgbm as lgb

                model = lgb.LGBMClassifier(
                    n_estimators=3000,
                    learning_rate=0.05,
                    # THE parameter here. Coming from XGBoost's max_depth=6, the equivalent
                    # is 2**6 = 64 leaves — setting num_leaves=255 out of habit is the
                    # standard way people overfit with LightGBM.
                    num_leaves=63,
                    min_data_in_leaf=50,      # the real guard against deep thin branches
                    max_bin=255,
                    feature_fraction=0.8,
                    bagging_fraction=0.8, bagging_freq=1,
                )
                model.fit(
                    X_train, y_train,
                    eval_set=[(X_valid, y_valid)],
                    callbacks=[lgb.early_stopping(100), lgb.log_evaluation(0)],
                )

                # Categoricals are handled natively — no one-hot expansion needed:
                model.fit(X_train, y_train, categorical_feature=["city", "device"])
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why leaf-wise wins for a fixed budget",
            accentColor = 0xFF10B981,
            code = """
                # Three splits, four leaves, either way. Each label is the gain that
                # splitting THAT node would realize:
                #            root
                #           /    \
                #        4.0      0.3
                #       /   \    /   \
                #     2.1   1.8 0.2  0.1

                # Level-wise: split the root, then BOTH nodes at the next level.
                level_wise = 4.0 + 0.3                 # = 4.3
                # Leaf-wise: split the root, then the 4.0 node, then the best remaining
                # leaf — which is the 2.1, not the 0.3.
                leaf_wise = 4.0 + 2.1                  # = 6.1

                print(level_wise, leaf_wise)           # 4.3 vs 6.1
                # Same three splits and the same four leaves. Level-wise was obliged to
                # spend one of them on the 0.3 node because it sat at the right depth;
                # leaf-wise left it alone and went deeper down the productive branch.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFFF59E0B, "Large Tabular Data", "Millions of rows where XGBoost's exact split search becomes the bottleneck."),
        ApplicationCard("target", 0xFF818CF8, "Competition Default", "Largely displaced XGBoost as the first thing tried on tabular problems, on speed."),
        ApplicationCard("search", 0xFF10B981, "Ranking at Scale", "LambdaRank support plus histogram speed makes it standard in production search stacks."),
    ),
    takeaways = listOf(
        "Leaf-wise growth reaches lower loss per leaf than level-wise, at the cost of deep asymmetric trees.",
        "num_leaves and min_data_in_leaf are the controls — max_depth alone will not prevent overfitting.",
        "Histogram binning plus sibling subtraction is where most of the speed comes from.",
        "GOSS subsamples rows the model already fits; EFB bundles mutually exclusive sparse features.",
    ),
    crossLinks = listOf(
        CrossLink("xgboost", "XGBoost (Extreme Gradient Boosting)"),
        CrossLink("gradient_boosting", "Gradient Boosting Machines (GBM)"),
        CrossLink("catboost", "CatBoost"),
        CrossLink("decision_trees", "Decision Trees"),
    ),
)
