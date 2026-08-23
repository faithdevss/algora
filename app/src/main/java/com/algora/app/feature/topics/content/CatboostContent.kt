package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val catboostContent = TopicContent(
    topicId = "catboost",
    figure = Figure(
        caption = "The page's six rows, both encodings computed. City C appears exactly once, so its " +
            "naive target encoding is 1.00 — the outlined pair is the same number twice, the feature " +
            "and the label it is supposed to help predict. A model handed that column reads the " +
            "answer off it and scores beautifully until inference, where the encoding is built from " +
            "rows it has never seen. The ordered statistic uses only the prefix, with the prior 0.5 " +
            "standing in for an empty one, so C's first appearance encodes to 0.50 and every first " +
            "occurrence does the same: A's third appearance can reach 0.83 because by then there is " +
            "genuinely history to average.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("1", "0", "1", "1", "0", "0"),
                listOf("0.67", "0.00", "0.67", "1.00", "0.00", "0.67"),
                listOf("0.50", "0.50", "0.75", "0.50", "0.25", "0.83"),
            ),
            rowHeaders = listOf("y", "naive", "ordered"),
            colHeaders = listOf("A", "B", "A", "C", "B", "A"),
            marks = listOf(
                FigureCell(0, 3, FigureTone.Warn),
                FigureCell(1, 3, FigureTone.Warn),
                FigureCell(2, 3),
            ),
        ),
    ),
    whatIsIt = listOf(
        "CatBoost's headline feature is handling categorical variables without one-hot expansion, and its real contribution is doing so without leaking the target. Plain target encoding — replacing a category with the mean label of the rows sharing it — is enormously effective and quietly broken: each row contributes to its own encoding, so a category appearing once encodes to exactly that row's label, and the model reads the answer straight off the feature.",
        "Ordered target statistics fix it with a permutation. Fix a random ordering of the rows, and encode each row using only the rows *before* it. A row therefore never contributes to its own encoding, which removes the leak while keeping the compactness that makes target encoding attractive. CatBoost applies the same idea to boosting itself — ordered boosting computes each row's residual from a model trained only on preceding rows — which addresses a subtler bias that affects every gradient-boosting implementation, not just categorical ones.",
        "Its third distinguishing choice is structural: trees are *oblivious*, meaning every node at a given depth tests the identical condition. The tree is then fully described by one (feature, threshold) pair per level, and prediction becomes evaluating each level to a bit, concatenating them into an index, and looking the leaf up directly — no branching, no pointer chasing, which is why CatBoost's inference is unusually fast. The symmetry is also a regularizer: a balanced tree forced to reuse conditions is much less expressive than LightGBM's leaf-wise growth, so it overfits less on small data and can cost accuracy on large data with complex interactions.",
    ),
    steps = listOf(
        StepCard(1, "Permute the Rows", "A random ordering. Several are used, to reduce the variance of any single one.", 0xFFF59E0B),
        StepCard(2, "Encode From the Prefix Only", "A category's value uses the target mean of preceding rows, never the row itself.", 0xFF818CF8),
        StepCard(3, "Smooth With a Prior", "(sum + a·prior)/(count + a), so a category seen once is not trusted outright.", 0xFF60A5FA),
        StepCard(4, "Combine Categories", "Feature combinations are generated greedily during training, not by hand.", 0xFF10B981),
        StepCard(5, "Grow Oblivious Trees", "One condition per level, applied across the whole level.", 0xFF14B8A6),
        StepCard(6, "Predict by Index", "Level bits concatenate into a leaf index — a lookup, not a traversal.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Naive target encoding", "mean(y | category)", "Includes the row itself — leaks."),
        FormulaEntry("Ordered statistic", "(Σ_{j<i, same cat} yⱼ + a·p) / (count_{j<i} + a)", "Prefix only, plus a prior."),
        FormulaEntry("Prior p", "usually the global target mean", "What an unseen category falls back to."),
        FormulaEntry("Oblivious tree", "same (feature, threshold) per level", "Depth d ⟹ exactly d conditions."),
        FormulaEntry("Leaf lookup", "index = Σ bitₖ·2ᵏ", "Prediction is arithmetic, not traversal."),
        FormulaEntry("Leaf count", "2^depth", "Always full and balanced."),
    ),
    notationKey = listOf(
        NotationEntry("target leakage", "a feature carrying information about its own row's label"),
        NotationEntry("ordered boosting", "residuals computed from a model that excluded the row"),
        NotationEntry("oblivious tree", "same test at every node of a level; also called a decision table"),
        NotationEntry("a", "smoothing weight on the prior"),
        NotationEntry("feature combination", "a generated interaction between categoricals"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Categoricals passed straight through",
            accentColor = 0xFFF59E0B,
            code = """
                from catboost import CatBoostClassifier, Pool

                # No encoding step, no one-hot expansion — column names go in as they are,
                # and high-cardinality columns are the case this is built for.
                categorical = ["city", "device", "referrer", "user_id"]

                train = Pool(X_train, y_train, cat_features=categorical)
                valid = Pool(X_valid, y_valid, cat_features=categorical)

                model = CatBoostClassifier(
                    iterations=3000,
                    learning_rate=0.05,
                    depth=6,                    # oblivious, so this is exactly 2**6 leaves
                    l2_leaf_reg=3.0,
                    early_stopping_rounds=100,
                    verbose=0,
                ).fit(train, eval_set=valid)

                print(model.get_best_iteration())
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The leak, and what ordering fixes",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np, pandas as pd

                df = pd.DataFrame({"city": ["A", "B", "A", "C", "B", "A"],
                                   "y":    [1, 0, 1, 1, 0, 0]})

                # Naive: each row's own label is inside its own encoding.
                naive = df.groupby("city")["y"].transform("mean")
                print(naive.tolist())
                # City C appears once, so its encoding is 1.0 — which IS that row's label.
                # A model given this feature can read the target directly.

                # Ordered: prefix only, with a prior for the empty prefix.
                prior, a = df["y"].mean(), 1.0
                seen_sum, seen_count, ordered = {}, {}, []
                for city, y in zip(df["city"], df["y"]):
                    s, c = seen_sum.get(city, 0.0), seen_count.get(city, 0)
                    ordered.append((s + a * prior) / (c + a))    # computed BEFORE the update
                    seen_sum[city], seen_count[city] = s + y, c + 1
                print([round(v, 3) for v in ordered])
                # The first occurrence of any city falls back to the prior — it cannot
                # encode a label it has not been allowed to see.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("globe", 0xFFF59E0B, "High-Cardinality Categoricals", "User ids, product ids, cities — where one-hot is infeasible and target encoding leaks."),
        ApplicationCard("chip", 0xFF818CF8, "Low-Latency Inference", "Oblivious trees make prediction a branchless index lookup, which matters at request scale."),
        ApplicationCard("chart", 0xFF10B981, "Small Datasets", "The symmetry constraint plus ordered boosting make it the least overfit-prone of the three."),
    ),
    takeaways = listOf(
        "Target encoding leaks because a row contributes to its own encoding; ordered statistics use only preceding rows.",
        "Ordered boosting applies the same permutation trick to residuals, addressing a bias all GBMs share.",
        "Oblivious trees reuse one condition per level, making prediction an index lookup rather than a traversal.",
        "That symmetry regularizes — better on small data, potentially limiting on large data with complex interactions.",
    ),
    crossLinks = listOf(
        CrossLink("xgboost", "XGBoost (Extreme Gradient Boosting)"),
        CrossLink("lightgbm", "LightGBM"),
        CrossLink("categorical_nb", "Categorical Naive Bayes"),
        CrossLink("gradient_boosting", "Gradient Boosting Machines (GBM)"),
    ),
)
