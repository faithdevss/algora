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

internal val naiveBayesContent = TopicContent(
    topicId = "naive_bayes",
    figure = Figure(
        caption = "The page's lab deciding one query point by Gaussian naive Bayes. Each class gets a " +
            "bell curve per feature, fitted to that class's values alone, and each curve is read " +
            "at the query. Along x the two classes are level, 0.11 each; along y, class 0's curve " +
            "is far higher, 0.23 against 0.03. The \"naive\" step multiplies those numbers as if x " +
            "and y were independent: with equal priors, class 0 scores 0.5 × 0.11 × 0.23 = 0.013 " +
            "and class 1 scores 0.001, which normalises to a 0.90 probability of class 0. The model " +
            "never looks at x and y together — its ovals are always axis-aligned — and it still " +
            "classifies well, because only the ranking of the scores has to be right.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0.5", "0.11", "0.23", "0.013"),
                listOf("0.5", "0.11", "0.03", "0.001"),
            ),
            rowHeaders = listOf("class 0", "class 1"),
            colHeaders = listOf("prior", "p(x | c)", "p(y | c)", "product"),
            marks = listOf(
                FigureCell(0, 2, FigureTone.Accent),
                FigureCell(0, 3, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Naive Bayes classifies by Bayes' rule with one simplifying assumption: given the class, every feature is independent of every other. P(class | features) ∝ P(class) · Πᵢ P(featureᵢ | class), so each feature's likelihood can be estimated on its own, from counts or from a fitted distribution, and the class with the largest product wins.",
        "The lab shows the Gaussian version deciding where a query point belongs. It fits one bell curve per class per feature and reads each at the query: along x the two classes' curves give 0.11 and 0.11, along y 0.23 for class 0 and 0.03 for class 1. With equal priors of 0.5, class 0 scores 0.5 × 0.11 × 0.23 = 0.013 and class 1 scores 0.001; normalised, that is a 0.90 probability of class 0. The dashed ovals stay axis-aligned, because the model never looks at x and y jointly — that is the \"naive\" part.",
        "The independence assumption is almost always false, and naive Bayes works anyway, because classification only needs the right class to have the largest score, not calibrated probabilities. It trains in one pass, handles thousands of features, and needs little data, which is why it remains a strong baseline for text. Its variants differ only in the per-feature distribution: Gaussian for continuous features, multinomial for counts, Bernoulli for presence and absence.",
    ),
    steps = listOf(
        StepCard(1, "Estimate Priors", "From training counts, compute P(class) for each class.", 0xFF818CF8),
        StepCard(2, "Estimate Likelihoods", "Compute P(featureᵢ | class) for each feature independently.", 0xFF60A5FA),
        StepCard(3, "Apply Bayes' Rule", "Multiply prior by all feature likelihoods to score each class.", 0xFF10B981),
        StepCard(4, "Pick the Argmax", "Predict the class with the highest posterior probability.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Bayes' theorem", "P(C|x) ∝ P(C)·Π P(xᵢ|C)", "Posterior from prior times likelihoods."),
        FormulaEntry("Prediction", "argmax_C P(C)·Π P(xᵢ|C)", "Most probable class."),
        FormulaEntry("Laplace smoothing", "add α to counts", "Prevents zero probabilities for unseen features."),
    ),
    notationKey = listOf(
        NotationEntry("P(C)", "prior probability of a class"),
        NotationEntry("P(xᵢ|C)", "likelihood of a feature given the class"),
        NotationEntry("α", "smoothing constant for unseen features"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Multinomial Naive Bayes (scikit-learn)",
            accentColor = 0xFF6366F1,
            code = """
                from sklearn.naive_bayes import MultinomialNB

                clf = MultinomialNB(alpha=1.0)   # Laplace smoothing
                clf.fit(X_counts, y_train)

                preds = clf.predict(X_test_counts)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("search", 0xFF818CF8, "Spam Filtering", "The classic use — scoring emails as spam/ham from word frequencies."),
        ApplicationCard("book", 0xFF60A5FA, "Text Classification", "Topic labeling and sentiment analysis where bag-of-words features are numerous; the independence assumption is plainly false for text, but NB works well despite strongly dependent features."),
        ApplicationCard("chart", 0xFF10B981, "Fast Baselines", "Trains in one pass, giving a strong, cheap baseline on high-dimensional data."),
    ),
    takeaways = listOf(
        "Naive Bayes multiplies a class prior by independent feature likelihoods via Bayes' rule.",
        "The independence assumption is unrealistic but keeps it fast and data-efficient.",
        "Laplace smoothing avoids zero probabilities for unseen feature values.",
        "It's a top choice for text where features are numerous — it works well despite strongly dependent features.",
        "In the lab the query scores 0.5 × 0.11 × 0.23 = 0.013 for class 0 against 0.001 for class 1 — a 0.90 probability once normalised.",
    ),
    crossLinks = listOf(
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
        CrossLink("logistic_regression", "Logistic Regression"),
    ),
)
