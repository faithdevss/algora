package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val oneHotEncodingContent = TopicContent(
    topicId = "one_hot_encoding",
    whatIsIt = listOf(
        "One-hot encoding turns one categorical column into k binary ones: a column per level, a single 1 per row. It is the encoding that makes no claim about the categories beyond which one each row belongs to — every pair of levels ends up exactly √2 = 1.414 apart, which is the truth about unordered categories and precisely what a label code gets wrong when it puts red 3 away from yellow and 1 away from green.",
        "Scored on data whose true effect per category is non-monotone in the label-code order, a least-squares fit reaches MSE 0.280 on the one-hot matrix and 11.26 on the label code — 40× worse — because the one-hot fit can give each level its own coefficient while the label fit has to pass one straight line through all four. That is the entire argument for it, and it applies to every model that multiplies a feature by a weight or measures a distance.",
        "Two costs come with it. The first is collinearity: the k columns always sum to 1, so they are linearly dependent on the intercept and the coefficients are not identifiable — any constant can be moved from the intercept into all k. Dropping one level fixes it and costs nothing measurable (MSE 0.280 either way, on 3 columns instead of 4), with the dropped level becoming the baseline the others are measured against. The second is width, and it is linear in cardinality: four colours is four columns, a postcode column with 5,000 levels is 5,000, almost all of them zero in any given row. That is where target encoding, hashing or a learned embedding take over, each trading this matrix's honesty for width.",
    ),
    steps = listOf(
        StepCard(1, "One Column Per Level", "k binary columns, a single 1 per row.", 0xFFF97316),
        StepCard(2, "Check the Geometry", "Every pair of levels √2 apart — no invented order.", 0xFF6366F1),
        StepCard(3, "Score It", "MSE 0.280 against a label code's 11.26.", 0xFF10B981),
        StepCard(4, "Notice the Collinearity", "The k columns sum to 1, so they duplicate the intercept.", 0xFF8B5CF6),
        StepCard(5, "Drop a Level", "3 columns, same MSE, coefficients identifiable again.", 0xFF3B82F6),
        StepCard(6, "Watch the Width", "5,000 levels is 5,000 columns — switch representations.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("One-hot", "x → eᵢ", "The i-th basis vector for level i."),
        FormulaEntry("Pairwise distance", "‖eᵢ − eⱼ‖ = √2 = 1.414", "Identical for every pair — the honest geometry."),
        FormulaEntry("Linear fit", "MSE 0.280 vs 11.26", "Against the same data label-encoded."),
        FormulaEntry("Dummy trap", "Σⱼ xⱼ = 1", "Collinear with the intercept; drop one level."),
        FormulaEntry("Dropping costs", "MSE 0.280 either way", "3 columns instead of 4."),
        FormulaEntry("Width", "k columns per feature", "4 for colours, 5,000 for postcodes."),
    ),
    notationKey = listOf(
        NotationEntry("eᵢ", "the indicator vector: 1 in position i, 0 everywhere else"),
        NotationEntry("dummy variable trap", "keeping all k columns alongside an intercept"),
        NotationEntry("baseline level", "the dropped level; other coefficients are differences from it"),
        NotationEntry("cardinality", "the level count — what the column cost is linear in"),
        NotationEntry("sparse matrix", "the storage format that makes wide one-hot affordable"),
        NotationEntry("handle_unknown", "the policy for a level unseen at fit time"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Sparse output, and a policy for unseen levels",
            accentColor = 0xFFF97316,
            code = """
                from sklearn.preprocessing import OneHotEncoder

                encoder = OneHotEncoder(
                    drop="first",              # avoid the dummy trap for linear models
                    handle_unknown="ignore",   # an unseen level becomes all zeros, not a crash
                    sparse_output=True,        # 5,000 columns of mostly zeros should not be dense
                )
                encoder.fit(X_train[cat_cols])

                # drop="first" for OLS and any unregularised linear model; keep all columns for
                # ridge, lasso and trees, where the redundancy is harmless and the symmetry is
                # easier to read. handle_unknown is not optional in production: real serving traffic
                # contains categories the training set did not.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "When k is too large, and what replaces it",
            accentColor = 0xFFEC4899,
            code = """
                import pandas as pd

                print(X[cat_cols].nunique().sort_values(ascending=False))
                # postcode      5213    <- one-hot would add 5,213 columns
                # product_id     842
                # colour           4    <- one-hot, obviously

                # Above a few hundred levels the alternatives are:
                #   target encoding  -- one column of the per-level target mean. Needs out-of-fold
                #                       computation, or it leaks the target straight into a feature.
                #   hashing          -- fixed width, collisions you cannot inspect afterwards.
                #   embeddings       -- learned, dense, and needs a model to learn them.
                #
                # All three trade one-hot's exactness for width. None of them is a free upgrade.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFFF97316, "Linear Models", "Each level gets its own coefficient, which is the point."),
        ApplicationCard("map", 0xFF6366F1, "Distance Models", "Every pair of categories equidistant, as they should be."),
        ApplicationCard("browser", 0xFF10B981, "Tabular Pipelines", "The default categorical transform in every framework."),
        ApplicationCard("help", 0xFFEC4899, "High Cardinality", "Linear in level count — switch representations above a few hundred."),
    ),
    takeaways = listOf(
        "A column per level, one 1 per row, and no claim about the categories beyond membership.",
        "Every pair of levels lands √2 apart, which is what a label code gets wrong.",
        "Measured: MSE 0.280 against the label code's 11.26 on a non-monotone target — 40×.",
        "The k columns sum to 1, so with an intercept the coefficients are not identifiable.",
        "Dropping one level fixes that at no cost — 0.280 on 3 columns instead of 4.",
        "Cost is linear in cardinality: 4 colours is 4 columns, 5,000 postcodes is 5,000.",
        "Above a few hundred levels, target encoding, hashing or embeddings take over — each with its own price.",
    ),
    crossLinks = listOf(
        CrossLink("label_encoding", "Label Encoding"),
        CrossLink("word_embeddings", "Word Embeddings"),
        CrossLink("linear_regression", "Linear Regression"),
        CrossLink("regularization", "Regularization (L1 / L2)"),
        CrossLink("knn", "K-Nearest Neighbors"),
    ),
)
