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

internal val confusionMatrixContent = TopicContent(
    topicId = "confusion_matrix",
    figure = Figure(
        caption = "At the default threshold of 0.5, the lab's 1,000-case problem sorts into 35 true " +
            "positives, 53 false negatives, 0 false positives and 912 true negatives — 91.2% of the " +
            "table in that one true-negative cell, with the two decision-relevant errors holding 53 " +
            "cases between them. Moving the threshold to 0.2 slides the same boundary to 75/85/13/827: " +
            "40 more positives caught at 85 more false alarms, from the same fitted model.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("35", "53"),
                listOf("0", "912"),
            ),
            rowHeaders = listOf("Actual +", "Actual −"),
            colHeaders = listOf("Pred +", "Pred −"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Primary),
                FigureCell(0, 1, FigureTone.Warn),
                FigureCell(1, 0, FigureTone.Warn),
                FigureCell(1, 1, FigureTone.Muted),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A confusion matrix is the four counts every classification metric is a function of: true positives, false positives, false negatives, true negatives. It is the only object in this category that loses no information — accuracy, precision, recall, F1 and kappa are all summaries of these four numbers, and each one throws something away to get to a single figure.",
        "On the lab's problem — 1,000 cases, 88 of them positive, one fitted logistic model at the default threshold of 0.5 — the matrix is 35 true positives, 0 false positives, 53 false negatives, 912 true negatives. The asymmetry is the thing to notice: 91% of the table sits in one cell, so any metric that averages over all four is dominated by it, while the two cells a decision actually turns on hold 53 cases between them.",
        "The other thing a matrix cannot be read without is the threshold that produced it. A logistic boundary is w₀ + w₁x + w₂y = logit(t), so moving the threshold slides the same line rather than changing the model: at t = 0.2 the same scores give 75 / 85 / 13 / 827 — 40 more positives found at 85 more false alarms. \"The model has 53 misses\" is not a property of the model, it is a property of the model at 0.5, and the ROC and precision-recall curves are exactly the matrix at every threshold at once.",
    ),
    steps = listOf(
        StepCard(1, "Fix a Threshold", "Nothing below exists until you choose one.", 0xFF0EA5E9),
        StepCard(2, "Count Four Cells", "TP, FP, FN, TN — 35 / 0 / 53 / 912 here.", 0xFF3B82F6),
        StepCard(3, "Look at the Shape", "912 of 1,000 in one cell; the errors are 53.", 0xFF8B5CF6),
        StepCard(4, "Name the Two Errors", "False alarms and misses cost different amounts.", 0xFFEC4899),
        StepCard(5, "Move the Threshold", "Same model, different matrix: 75 / 85 / 13 / 827.", 0xFFF59E0B),
        StepCard(6, "Summarise Deliberately", "Every metric below drops one of these cells.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("The table", "[[TN, FP], [FN, TP]]", "Rows are truth, columns are prediction — check which convention a library uses."),
        FormulaEntry("At t = 0.5", "35 / 0 / 53 / 912", "TP / FP / FN / TN on the lab's 1,000 cases."),
        FormulaEntry("At t = 0.2", "75 / 85 / 13 / 827", "The same model and the same scores."),
        FormulaEntry("Error share", "53 of 1,000 = 5.3%", "And 91.2% of the table is one cell."),
        FormulaEntry("Boundary", "w₀ + w₁x + w₂y = logit(t)", "Why the threshold slides the line rather than bending it."),
        FormulaEntry("Everything else", "f(TP, FP, FN, TN)", "Accuracy uses four cells, precision two, F1 three."),
    ),
    notationKey = listOf(
        NotationEntry("TP", "predicted positive and positive"),
        NotationEntry("FP", "predicted positive and negative — a false alarm"),
        NotationEntry("FN", "predicted negative and positive — a miss"),
        NotationEntry("TN", "predicted negative and negative — usually the biggest cell"),
        NotationEntry("threshold", "the score above which a case is called positive; 0.5 is a default, not a property"),
        NotationEntry("base rate", "the share of cases that are truly positive — 8.8% here"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Print it with the threshold, always",
            accentColor = 0xFF0EA5E9,
            code = """
                from sklearn.metrics import confusion_matrix, classification_report

                probabilities = model.predict_proba(X_test)[:, 1]

                for threshold in (0.5, 0.34, 0.2):
                    tn, fp, fn, tp = confusion_matrix(y_test, probabilities >= threshold).ravel()
                    print(f"t={threshold}: TP {tp} FP {fp} FN {fn} TN {tn}")

                # t=0.5:  TP 35 FP  0  FN 53 TN 912
                # t=0.34: TP 57 FP  2 FN 31 TN 910
                # t=0.2:  TP 75 FP 85 FN 13 TN 827
                #
                # A report quoting one matrix without its threshold is quoting one row of this table
                # and calling it the model. Note sklearn's ravel order -- tn, fp, fn, tp -- which is
                # not the order most textbooks draw.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Attach costs and the choice makes itself",
            accentColor = 0xFFEC4899,
            code = """
                import numpy as np

                def expected_cost(y_true, scores, threshold, cost_fp=1.0, cost_fn=20.0):
                    predicted = scores >= threshold
                    fp = np.sum(predicted & (y_true == 0))
                    fn = np.sum(~predicted & (y_true == 1))
                    return cost_fp * fp + cost_fn * fn

                grid = np.linspace(0.01, 0.99, 99)
                costs = [expected_cost(y_test, probabilities, t) for t in grid]
                print(f"cheapest threshold: {grid[int(np.argmin(costs))]:.2f}")

                # If a miss costs 20 false alarms, the best threshold is nowhere near 0.5 -- and no
                # threshold-free metric can tell you that, because the cost ratio is not in the data.
                # This is the calculation F1 and accuracy are both standing in for, badly.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("check", 0xFF0EA5E9, "Every Classifier", "The first thing to print, before any single-number metric."),
        ApplicationCard("finance", 0xFFEC4899, "Cost Analysis", "Attach a price to FP and FN and the threshold follows."),
        ApplicationCard("flask", 0xFF8B5CF6, "Error Analysis", "The two error cells are where the examples worth reading live."),
        ApplicationCard("help", 0xFF10B981, "Multi-Class", "The same idea as a k×k table; the diagonal is what you got right."),
    ),
    takeaways = listOf(
        "Four counts, and every classification metric in this category is a function of them.",
        "At t = 0.5 the lab's model gives 35 / 0 / 53 / 912 — 91% of the table in one cell.",
        "The two error cells hold 53 cases, and they are the only ones a decision turns on.",
        "Moving the threshold slides the same boundary: 75 / 85 / 13 / 827 at t = 0.2.",
        "So a matrix without its threshold is not a statement about the model.",
        "Check the cell order your library uses — sklearn ravels to tn, fp, fn, tp.",
        "Attach costs to FP and FN and the threshold choice becomes arithmetic instead of taste.",
    ),
    crossLinks = listOf(
        CrossLink("accuracy", "Accuracy"),
        CrossLink("precision_recall", "Precision & Recall"),
        CrossLink("roc_curve", "ROC Curve"),
        CrossLink("cohens_kappa", "Cohen's Kappa"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
