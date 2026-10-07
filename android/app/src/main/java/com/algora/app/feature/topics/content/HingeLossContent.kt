package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val hingeLossContent = TopicContent(
    topicId = "hinge_loss",
    figure = Figure(
        caption = "Past margin 1 hinge loss is exactly zero, not merely small — and so is its gradient. " +
            "That flat region is why an SVM's solution depends only on the points near the boundary: on " +
            "1,000 margins only 110 contribute anything at all. Logistic loss has no flat region. At " +
            "margin 5 its gradient is still 6.69×10⁻³, at margin 10 still 4.54×10⁻⁵ — it never stops " +
            "asking an already-correct point to move further.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    label = "hinge",
                    points = listOf(
                        FigurePoint(0f, 1f),
                        FigurePoint(0.25f, 0.5f),
                        FigurePoint(0.5f, 0f),
                        FigurePoint(1f, 0f),
                    ),
                ),
                FigureSeries(
                    label = "logistic",
                    points = listOf(
                        FigurePoint(0f, 0.657f),
                        FigurePoint(0.25f, 0.347f),
                        FigurePoint(0.5f, 0.157f),
                        FigurePoint(0.75f, 0.063f),
                        FigurePoint(1f, 0.0245f),
                    ),
                    tone = FigureTone.Warn,
                ),
            ),
            xLabel = "margin y·f(x), −1 → 3",
            yLabel = "loss",
            markers = listOf(
                FigurePoint(0.5f, 0f, "zero from here on", FigureTone.Primary),
                FigurePoint(1f, 0.0245f, "still 0.049", FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Hinge loss is max(0, 1 − margin), where margin is y·f(x) — positive when a point is on the correct side of the decision boundary, negative when it's on the wrong side. Past margin 1, the loss is exactly zero, not merely small. That flat zero region is the entire design: it is what makes a support vector machine's solution depend only on the points near the boundary, ignoring everything already safely classified.",
        "On the lab's 1,000 margins, hinge loss is nonzero for only 110 of them — the points inside the margin, which are support vectors (as are any points exactly on it). The other 890 sit past the margin and contribute nothing at all to the loss or its gradient: hingeGradient is exactly 0.0 for any margin ≥ 1. Logistic loss, evaluated on the same 1,000 margins, is nonzero for all 1,000 — it has no flat region and never fully lets a point go.",
        "That difference shows up in the gradient, not just the loss value. At margin 5, hinge's gradient is exactly 0 while logistic's is still 6.69×10⁻³; at margin 10, hinge is still exactly 0 but logistic's gradient is 4.54×10⁻⁵ — vanishingly small, but never zero. Hinge loss stops asking a confidently-correct point to move further; logistic loss keeps asking, forever, just more quietly."
    ),
    steps = listOf(
        StepCard(1, "Compute the Margin", "y·f(x) — positive means correctly classified.", 0xFF0EA5E9),
        StepCard(2, "Apply max(0, 1 − margin)", "Zero once the margin passes 1.", 0xFF3B82F6),
        StepCard(3, "Count the Active Set", "110 of 1,000 margins are inside the hinge — the support vectors.", 0xFF8B5CF6),
        StepCard(4, "Compare to Logistic Loss", "Nonzero for all 1,000 — no flat region at all.", 0xFFF59E0B),
        StepCard(5, "Check the Gradients Directly", "Hinge grad = 0 past margin 1; logistic's is 4.54e-5 even at margin 10.", 0xFFEC4899),
        StepCard(6, "See What That Buys", "A model defined entirely by its ~110 support vectors.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Hinge", "max(0, 1 − margin)", "Exactly 0 past margin 1."),
        FormulaEntry("Logistic", "ln(1 + e⁻ᵐᵃʳᵍⁱⁿ)", "Never reaches exactly 0."),
        FormulaEntry("Active examples", "110 of 1,000 (hinge)", "890 contribute nothing to loss or gradient."),
        FormulaEntry("Active examples", "1,000 of 1,000 (logistic)", "Every example still has some pull."),
        FormulaEntry("Hinge gradient at margin 5", "0.0", "Flat — no pull past the margin."),
        FormulaEntry("Logistic gradient at margin 10", "4.54 × 10⁻⁵", "Nonzero, however small."),
    ),
    notationKey = listOf(
        NotationEntry("margin", "y·f(x); positive means correctly classified"),
        NotationEntry("support vector", "a point on or inside the margin (y·f(x) ≤ 1), including misclassified points"),
        NotationEntry("active set", "the examples still contributing to the loss's gradient"),
        NotationEntry("flat region", "margin ≥ 1, where hinge loss is exactly zero"),
        NotationEntry("squared hinge", "max(0, 1−margin)², a smoother variant used by some SVM solvers"),
        NotationEntry("logistic loss", "hinge's smooth cousin — see Log Loss"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "890 of 1,000 examples stop mattering",
            accentColor = 0xFF0EA5E9,
            code = """
                import numpy as np

                def hinge(margin): return np.maximum(0, 1 - margin)
                def hinge_gradient(margin): return np.where(margin < 1, -1.0, 0.0)

                margins = y_true * decision_function(X)  # y in {-1, +1}
                active = np.sum(hinge(margins) > 1e-9)
                print(f"active: {active} of {len(margins)}")
                # active: 110 of 1000
                #
                # These 110 are the support vectors -- an SVM's decision boundary is a function of
                # only these points. Retraining after deleting any of the other 890 leaves the
                # boundary unchanged, because they contributed zero gradient in the first place.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Logistic loss never lets go",
            accentColor = 0xFFEC4899,
            code = """
                def logistic(margin): return np.log1p(np.exp(-margin))
                def logistic_gradient(margin): return -1 / (1 + np.exp(margin))

                for m in (1.0, 2.0, 5.0, 10.0):
                    print(f"margin {m}: hinge grad {hinge_gradient(m):.1f}  "
                          f"logistic grad {logistic_gradient(m):.2e}")
                # margin  1.0: hinge grad 0.0  logistic grad -2.69e-01
                # margin  2.0: hinge grad 0.0  logistic grad -1.19e-01
                # margin  5.0: hinge grad 0.0  logistic grad -6.69e-03
                # margin 10.0: hinge grad 0.0  logistic grad -4.54e-05
                #
                # Hinge is flat-zero from margin 1 on. Logistic keeps a nonzero, shrinking pull
                # forever -- which is why logistic regression's weights keep growing on separable
                # data unless regularized, while an SVM's do not.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF0EA5E9, "Support Vector Machines", "The loss an SVM directly minimizes."),
        ApplicationCard("chip", 0xFF3B82F6, "Sparse Solutions", "Only the active set matters — a compact model description."),
        ApplicationCard("check", 0xFF8B5CF6, "Max-Margin Classifiers", "Anywhere the goal is confident separation, not calibrated probability."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Need a probability, not just a label — use logistic loss instead."),
    ),
    takeaways = listOf(
        "Hinge loss is max(0, 1 − margin) — exactly zero once a point is safely past the boundary.",
        "On the lab's 1,000 margins, only 110 have nonzero hinge loss — the points inside the margin, all support vectors.",
        "The other 890 contribute exactly zero to both the loss and its gradient.",
        "Logistic loss, on the same margins, is nonzero for all 1,000 — no flat region exists.",
        "At margin 10, hinge's gradient is exactly 0 while logistic's is still 4.54×10⁻⁵.",
        "That's the mechanism behind \"support vector\": the model is defined by the active few.",
        "Choose hinge for max-margin separation; choose logistic when a probability is needed.",
    ),
    crossLinks = listOf(
        CrossLink("svm", "Support Vector Machines (Linear)"),
        CrossLink("log_loss", "Log Loss (Cross-Entropy)"),
        CrossLink("logistic_regression", "Logistic Regression"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
