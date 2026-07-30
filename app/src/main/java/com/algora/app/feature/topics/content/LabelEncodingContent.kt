package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val labelEncodingContent = TopicContent(
    topicId = "label_encoding",
    whatIsIt = listOf(
        "Label encoding replaces each category with an integer: red 0, green 1, blue 2, yellow 3. It costs one column instead of four and it is the fastest thing you can do to a categorical feature. The received rule is that you should not do it, and the rule is half right in a way worth being precise about, because the other half is used by every gradient-boosting library in production.",
        "What the encoding introduces is a geometry the categories do not have. Under those codes red sits 3 away from yellow and 1 away from green, and a model that multiplies the code by a weight reads that literally. Scored on data whose true effect per category is non-monotone in the code order, a least-squares fit reaches MSE 11.26 on the label code against 0.280 on one-hot — 40× worse — because it has to pass one straight line through four unordered levels.",
        "A tree never reads the code as a number, only as somewhere to split. Grown on the same label-coded column it reaches MSE 0.280 at depth 2 — one-hot's number to three decimals — because two splits are enough to separate four categories. So the rule is conditional, not absolute: label-encode for trees and boosted ensembles, one-hot for linear and distance-based models. And if the categories genuinely are ordered — small, medium, large — the code is the correct representation and one-hot is the one throwing information away.",
    ),
    steps = listOf(
        StepCard(1, "Map Categories to Integers", "One column in, one column out.", 0xFFF97316),
        StepCard(2, "Notice the Geometry", "red→yellow is 3, red→green is 1. Neither is true.", 0xFF8B5CF6),
        StepCard(3, "Score a Linear Model", "MSE 11.26 against one-hot's 0.280.", 0xFFEC4899),
        StepCard(4, "Score a Tree", "Depth 2 reaches 0.280 — identical to one-hot.", 0xFF10B981),
        StepCard(5, "Check for Real Order", "Ordinal categories should keep the code.", 0xFF3B82F6),
        StepCard(6, "Pin the Mapping", "The same category must get the same code at inference.", 0xFF6366F1),
    ),
    formulas = listOf(
        FormulaEntry("Label encoding", "category → integer index", "One column, whatever the level count."),
        FormulaEntry("Implied distance", "|code(a) − code(b)|", "3 for red→yellow, 1 for red→green."),
        FormulaEntry("True distance", "√2 for every pair", "What one-hot encodes, and what is actually true here."),
        FormulaEntry("Linear model", "MSE 11.26 vs 0.280", "40× worse on a non-monotone target."),
        FormulaEntry("Tree, depth 2", "MSE 0.280", "Identical to one-hot — the code costs nothing."),
        FormulaEntry("Columns", "1 vs k", "The reason it is still used on high-cardinality features."),
    ),
    notationKey = listOf(
        NotationEntry("nominal", "unordered categories — where the code invents structure"),
        NotationEntry("ordinal", "genuinely ordered categories — where the code is correct"),
        NotationEntry("cardinality", "how many levels a categorical column has"),
        NotationEntry("non-monotone target", "the effect per category does not follow the code order"),
        NotationEntry("split", "how a tree consumes the code — a threshold, not a multiplier"),
        NotationEntry("unseen category", "a level absent at fit time; every encoder needs a policy for it"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Encode by model family, not by habit",
            accentColor = 0xFFF97316,
            code = """
                from sklearn.compose import ColumnTransformer
                from sklearn.ensemble import HistGradientBoostingRegressor
                from sklearn.linear_model import Ridge
                from sklearn.pipeline import make_pipeline
                from sklearn.preprocessing import OneHotEncoder, OrdinalEncoder

                # Linear model: one-hot, or the coefficients are fitted to an invented ordering.
                linear = make_pipeline(
                    ColumnTransformer([("cat", OneHotEncoder(handle_unknown="ignore"), cat_cols)],
                                      remainder="passthrough"),
                    Ridge(),
                )

                # Tree ensemble: ordinal codes are fine, and often better -- one-hot spreads a
                # single categorical signal across k columns, so each split sees a fraction of it.
                trees = make_pipeline(
                    ColumnTransformer([("cat", OrdinalEncoder(handle_unknown="use_encoded_value",
                                                              unknown_value=-1), cat_cols)],
                                      remainder="passthrough"),
                    HistGradientBoostingRegressor(categorical_features=cat_cols),
                )
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Measure it on your own data — it takes four lines",
            accentColor = 0xFF10B981,
            code = """
                from sklearn.model_selection import cross_val_score

                for name, pipeline in [("label", label_pipeline), ("one-hot", onehot_pipeline)]:
                    scores = cross_val_score(pipeline, X, y, cv=5, scoring="neg_mean_squared_error")
                    print(f"{name:8} MSE {-scores.mean():.3f}")

                # On the lab's data with a linear model: label 11.26, one-hot 0.280.
                # With a tree at depth >= 2:            label  0.280, one-hot 0.280.
                #
                # The encoding is not a property of the data alone -- it is a property of the pair
                # (data, model). Guessing which side of that you are on is unnecessary when the
                # answer costs one cross_val_score.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("stack", 0xFF10B981, "Gradient Boosting", "LightGBM and CatBoost take integer codes by design."),
        ApplicationCard("trend", 0xFFEC4899, "Ordinal Features", "Sizes, grades and ratings should keep their order."),
        ApplicationCard("finance", 0xFFF97316, "High Cardinality", "One column instead of thousands, when width is the constraint."),
        ApplicationCard("help", 0xFF8B5CF6, "Linear Models", "Here the invented ordering costs 40× the error."),
    ),
    takeaways = listOf(
        "One integer per category: one column instead of k, and the cheapest possible encoding.",
        "It invents an order and a spacing — red is 3 from yellow and 1 from green under these codes.",
        "A linear model reads that literally: MSE 11.26 against one-hot's 0.280 on a non-monotone target.",
        "A tree does not: at depth 2 it reaches 0.280 on the same label-coded column — identical to one-hot.",
        "So the rule is conditional. Label-encode for trees, one-hot for linear and distance-based models.",
        "For genuinely ordinal categories the code is the correct representation and one-hot loses information.",
        "Pin the mapping and decide what an unseen category becomes before serving anything.",
    ),
    crossLinks = listOf(
        CrossLink("one_hot_encoding", "One-Hot Encoding"),
        CrossLink("decision_trees", "Decision Trees"),
        CrossLink("lightgbm", "LightGBM"),
        CrossLink("catboost", "CatBoost"),
        CrossLink("linear_regression", "Linear Regression"),
    ),
)
