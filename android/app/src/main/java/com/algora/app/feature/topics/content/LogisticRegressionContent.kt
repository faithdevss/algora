package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val logisticRegressionContent = TopicContent(
    topicId = "logistic_regression",
    figure = Figure(
        caption = "P(class = 1) = σ(4x₁ + 4x₂ − 4) over the unit square. The outlined cells are the " +
            "ones where the linear score is exactly zero — they lie on a straight line, x₁ + x₂ = 1, " +
            "because z is linear and σ is monotone, so the boundary can only ever be a hyperplane. " +
            "What is not straight is the confidence: 0.018 in the bottom-left corner, 0.982 in the " +
            "top-right, and 0.31 one grid step off the line. That graded field is what separates this " +
            "from a bare line — the model reports how far from the boundary a point sits, in " +
            "probability, not just which side it fell on.",
        shape = FigureShape.Heatmap(
            values = listOf(
                listOf(0.500f, 0.690f, 0.832f, 0.917f, 0.961f, 0.982f),
                listOf(0.310f, 0.500f, 0.690f, 0.832f, 0.917f, 0.961f),
                listOf(0.168f, 0.310f, 0.500f, 0.690f, 0.832f, 0.917f),
                listOf(0.083f, 0.168f, 0.310f, 0.500f, 0.690f, 0.832f),
                listOf(0.039f, 0.083f, 0.168f, 0.310f, 0.500f, 0.690f),
                listOf(0.018f, 0.039f, 0.083f, 0.168f, 0.310f, 0.500f),
            ),
            rowLabels = listOf("1.0", "0.8", "0.6", "0.4", "0.2", "0.0"),
            colLabels = listOf("0.0", "0.2", "0.4", "0.6", "0.8", "1.0"),
            marks = listOf(
                FigureCell(0, 0), FigureCell(1, 1), FigureCell(2, 2),
                FigureCell(3, 3), FigureCell(4, 4), FigureCell(5, 5),
            ),
            legend = "x₂ down the side, x₁ along the top; 1.0 = certain class 1",
        ),
    ),
    whatIsIt = listOf(
        "Logistic regression predicts the probability that an input belongs to a class by passing a linear score through the sigmoid function, squashing it into the 0–1 range.",
        "Despite the name it's a classifier: it draws a linear decision boundary and outputs calibrated probabilities rather than a raw line.",
    ),
    steps = listOf(
        StepCard(1, "Linear Score", "Compute z = w·x + b, a weighted sum of the features.", 0xFF818CF8),
        StepCard(2, "Sigmoid Squash", "Map z to a probability with σ(z) = 1 / (1 + e^(−z)).", 0xFF60A5FA),
        StepCard(3, "Cross-Entropy Loss", "Penalize confident wrong predictions with log loss, not squared error.", 0xFF10B981),
        StepCard(4, "Gradient Descent", "Nudge the weights downhill on the loss until the boundary settles.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Hypothesis", "ŷ = σ(w·x + b)", "Probability of the positive class."),
        FormulaEntry("Sigmoid", "σ(z) = 1 / (1 + e^(−z))", "Maps any real number to (0, 1)."),
        FormulaEntry("Log loss", "J = −Σ[y·ln ŷ + (1−y)·ln(1−ŷ)]", "Cross-entropy between labels and predictions."),
    ),
    notationKey = listOf(
        NotationEntry("w, b", "weight vector and bias"),
        NotationEntry("σ", "the sigmoid (logistic) function"),
        NotationEntry("ŷ", "predicted probability of class 1"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Logistic regression (scikit-learn)",
            accentColor = 0xFF6366F1,
            code = """
                from sklearn.linear_model import LogisticRegression

                clf = LogisticRegression()
                clf.fit(X_train, y_train)

                probs = clf.predict_proba(X_test)[:, 1]   # P(class = 1)
                preds = clf.predict(X_test)               # thresholded at 0.5
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ClassifierPlayground,
    applications = listOf(
        ApplicationCard("finance", 0xFF818CF8, "Credit & Risk Scoring", "Estimating default probability from applicant features is a textbook logistic-regression task."),
        ApplicationCard("flask", 0xFF60A5FA, "Medical Diagnosis", "Predicting disease presence from measurements, with interpretable odds ratios."),
        ApplicationCard("search", 0xFF10B981, "Spam & Click Prediction", "Binary yes/no scoring at scale — spam filters and click-through models."),
    ),
    takeaways = listOf(
        "Logistic regression is linear classification via the sigmoid, outputting probabilities.",
        "It optimizes cross-entropy (log loss), not mean squared error.",
        "Its weights are interpretable as log-odds contributions of each feature.",
        "The decision boundary is linear; use kernels or trees when classes aren't linearly separable.",
    ),
    crossLinks = listOf(
        CrossLink("linear_regression", "Linear Regression"),
        CrossLink("svm", "Support Vector Machines"),
    ),
)
