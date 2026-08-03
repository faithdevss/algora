package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val decisionTreesContent = TopicContent(
    topicId = "decision_trees",
    figure = Figure(
        caption = "Every internal node is one threshold on one feature, chosen because it separates the " +
            "classes better than any other threshold on any other feature — that is all Gini impurity " +
            "and information gain are for. The path to a leaf is the prediction *and* its explanation, " +
            "which no other model here gives for free. Left unpruned, the same greedy rule keeps " +
            "splitting until every leaf holds one point, and the tree has memorised rather than learned.",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("x₁ < 2.5", null, FigureTone.Primary),
                FigureNode("x₂ < 1.0", 0, FigureTone.Primary),
                FigureNode("B", 0, FigureTone.Accent),
                FigureNode("A", 1, FigureTone.Accent),
                FigureNode("B", 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A decision tree classifies or predicts by asking a sequence of yes/no questions about the features, splitting the data at each node until it reaches a leaf with an answer.",
        "Each split is chosen to make the resulting groups as pure as possible, measured by Gini impurity or information gain (entropy).",
    ),
    steps = listOf(
        StepCard(1, "Pick the Best Split", "Try each feature/threshold; keep the split that most reduces impurity.", 0xFF818CF8),
        StepCard(2, "Partition the Data", "Send samples down the branch matching their answer.", 0xFF60A5FA),
        StepCard(3, "Recurse", "Repeat on each child until nodes are pure or a stopping rule triggers.", 0xFF10B981),
        StepCard(4, "Predict at Leaves", "A new sample follows the questions to a leaf and takes its majority label (or mean value).", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Gini impurity", "1 − Σ pᵢ²", "Chance of mislabeling a random sample in the node."),
        FormulaEntry("Entropy", "−Σ pᵢ·log₂ pᵢ", "Bits of disorder in the node's labels."),
        FormulaEntry("Information gain", "H(parent) − Σ (nᵢ/n)·H(childᵢ)", "Impurity reduction from a split."),
    ),
    notationKey = listOf(
        NotationEntry("pᵢ", "fraction of class i in a node"),
        NotationEntry("impurity", "how mixed a node's labels are"),
        NotationEntry("leaf", "terminal node giving the prediction"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Decision tree (scikit-learn)",
            accentColor = 0xFF6366F1,
            code = """
                from sklearn.tree import DecisionTreeClassifier

                clf = DecisionTreeClassifier(criterion="gini", max_depth=4)
                clf.fit(X_train, y_train)

                preds = clf.predict(X_test)
                # max_depth limits growth to curb overfitting.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("book", 0xFF818CF8, "Interpretable Rules", "Their if/then paths make decisions auditable — valued in medicine, credit, and policy."),
        ApplicationCard("chart", 0xFF60A5FA, "Feature Importance", "Split frequency and gain reveal which features drive predictions."),
        ApplicationCard("chip", 0xFF10B981, "Ensemble Building Blocks", "Random forests and gradient boosting combine many trees into strong models."),
    ),
    takeaways = listOf(
        "A decision tree splits on features to maximize node purity, giving human-readable rules.",
        "Gini and entropy/information gain are the standard split criteria.",
        "Single trees overfit easily — limit depth or prune to generalize.",
        "Ensembles of trees (forests, boosting) trade interpretability for much higher accuracy.",
    ),
    crossLinks = listOf(
        CrossLink("tree", "Tree"),
        CrossLink("knn", "k-Nearest Neighbors"),
    ),
)
