package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val categoricalNbContent = TopicContent(
    topicId = "categorical_nb",
    whatIsIt = listOf(
        "Categorical naive Bayes handles features whose values are unordered labels — colour, region, browser, weather. Each feature gets its own probability table, one row per class and one column per possible value, estimated by counting.",
        "It exists because the alternatives quietly encode a lie. Label-encoding {sunny, overcast, rain} as {0, 1, 2} and feeding that to Gaussian NB asserts that overcast lies numerically between sunny and rain, and that the gap from sunny to rain is twice the gap from sunny to overcast. Neither is true, and the model will act on both. One-hot encoding avoids the false ordering but then hands Bernoulli NB a set of indicators it treats as independent, when exactly one of them is guaranteed to be 1 — a hard dependency the model has no way to know about. Categorical NB models the feature as what it is: a single draw from a categorical distribution.",
        "The practical limits are cardinality and unseen values. Parameters scale as K·Σⱼ|values(j)|, so a feature with ten thousand distinct values needs ten thousand cells per class and most will be estimated from almost nothing. And a value absent from training has no column at all — sklearn's implementation will raise rather than guess, so unseen categories need an explicit \"other\" bucket decided in advance. Laplace smoothing handles the softer version of the same problem, where a value appears in training but never alongside a particular class.",
    ),
    steps = listOf(
        StepCard(1, "Confirm the Features Are Nominal", "Unordered labels. If they have a natural order, an ordinal encoding is more informative.", 0xFF14B8A6),
        StepCard(2, "Build One Table per Feature", "Rows are classes, columns are that feature's values. Fill by counting.", 0xFF818CF8),
        StepCard(3, "Smooth Every Cell", "Add α, so a value never seen with a class does not zero the whole product.", 0xFF60A5FA),
        StepCard(4, "Multiply Across Features", "Sum the logs — the naive assumption is what allows one table per feature instead of a joint one.", 0xFF10B981),
        StepCard(5, "Add the Prior and Take the Argmax", "Same final step as every other variant.", 0xFFF59E0B),
        StepCard(6, "Plan for Unseen Values", "A category absent from training has no column. Bucket rare values before fitting.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Likelihood", "P(xⱼ=v | c) = (N_{j,v,c} + α) / (Nc + α·|values(j)|)", "One table per feature."),
        FormulaEntry("Score", "log P(c) + Σⱼ log P(xⱼ | c)", "Sum over features."),
        FormulaEntry("Parameters", "K · Σⱼ |values(j)|", "Linear in cardinality, not multiplicative."),
        FormulaEntry("Full joint instead", "K · ∏ⱼ |values(j)| − 1", "The combinatorial cost the assumption avoids."),
        FormulaEntry("Smoothing denominator", "+α·|values(j)|", "Per-feature, not vocabulary-wide."),
        FormulaEntry("Unseen value", "no column exists", "A hard failure, unlike an unseen word."),
    ),
    notationKey = listOf(
        NotationEntry("nominal", "categorical with no meaningful order"),
        NotationEntry("ordinal", "categorical with a meaningful order"),
        NotationEntry("cardinality", "number of distinct values a feature takes"),
        NotationEntry("N_{j,v,c}", "rows with feature j = v in class c"),
        NotationEntry("one-hot", "expanding one categorical into several binary indicators"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Fitting, and handling unseen categories",
            accentColor = 0xFF14B8A6,
            code = """
                from sklearn.preprocessing import OrdinalEncoder
                from sklearn.naive_bayes import CategoricalNB
                from sklearn.pipeline import make_pipeline

                # OrdinalEncoder here is a storage format, not a claim about order —
                # CategoricalNB reads the integers as table indices and never compares them.
                # handle_unknown matters: without it, a category the training set never
                # contained raises at predict time rather than degrading.
                model = make_pipeline(
                    OrdinalEncoder(handle_unknown="use_encoded_value", unknown_value=-1),
                    CategoricalNB(alpha=1.0),
                ).fit(X_train, y_train)

                # min_categories reserves table columns for values you know exist but that
                # happen not to appear in this particular training split.
                CategoricalNB(alpha=1.0, min_categories=[3, 3, 2, 2])
            """.trimIndent(),
        ),
        CodeBlock(
            title = "What the naive assumption saves, counted",
            accentColor = 0xFF10B981,
            code = """
                import numpy as np

                cardinalities = [3, 3, 2, 2]      # outlook, temp, humidity, windy
                classes = 2

                naive = classes * sum(cardinalities)
                joint = classes * (np.prod(cardinalities) - 1)
                print(naive, joint)               # 20 vs 70

                # With 14 training rows, 70 parameters is hopeless and 20 is merely thin.
                # Scale it up and the gap stops being a convenience:
                cardinalities = [10] * 8
                print(classes * sum(cardinalities),               # 160
                      classes * (np.prod(cardinalities) - 1))     # 200,000,000
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF14B8A6, "Survey & Questionnaire Data", "Responses drawn from fixed option sets, which is exactly the categorical case."),
        ApplicationCard("browser", 0xFF818CF8, "Web Analytics", "Browser, device, referrer and country — high-cardinality nominal fields."),
        ApplicationCard("flask", 0xFF10B981, "Decision-Tree Baseline", "Tree models handle categoricals natively too; CategoricalNB is the one-pass number to beat first."),
    ),
    takeaways = listOf(
        "Each nominal feature gets its own count table, so no false ordering is invented.",
        "Label-encoding into a Gaussian asserts an order that does not exist; one-hot into Bernoulli hides a guaranteed dependency.",
        "Parameters grow linearly with cardinality rather than multiplicatively — that is what the naive assumption buys.",
        "An unseen category has no column at all, so plan an \"other\" bucket before fitting.",
    ),
    crossLinks = listOf(
        CrossLink("naive_bayes", "Naive Bayes"),
        CrossLink("bernoulli_nb", "Bernoulli Naive Bayes"),
        CrossLink("decision_trees", "Decision Trees"),
        CrossLink("gaussian_nb", "Gaussian Naive Bayes"),
    ),
)
