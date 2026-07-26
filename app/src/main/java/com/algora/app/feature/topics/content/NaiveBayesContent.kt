package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val naiveBayesContent = TopicContent(
    topicId = "naive_bayes",
    whatIsIt = listOf(
        "Naive Bayes is a probabilistic classifier that applies Bayes' theorem while naively assuming every feature is independent given the class.",
        "That assumption is usually false, yet the model is fast, needs little data, and works remarkably well — especially for text.",
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
        ApplicationCard("book", 0xFF60A5FA, "Text Classification", "Topic labeling and sentiment analysis where bag-of-words features are near-independent."),
        ApplicationCard("chart", 0xFF10B981, "Fast Baselines", "Trains in one pass, giving a strong, cheap baseline on high-dimensional data."),
    ),
    takeaways = listOf(
        "Naive Bayes multiplies a class prior by independent feature likelihoods via Bayes' rule.",
        "The independence assumption is unrealistic but keeps it fast and data-efficient.",
        "Laplace smoothing avoids zero probabilities for unseen feature values.",
        "It's a top choice for text where features are numerous and roughly independent.",
    ),
    crossLinks = listOf(
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
        CrossLink("logistic_regression", "Logistic Regression"),
    ),
)
