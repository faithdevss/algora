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

internal val adaBoostContent = TopicContent(
    topicId = "adaboost",
    figure = Figure(
        caption = "α = ½ln((1−ε)/ε) against a learner's weighted error, with the axis centred on " +
            "α = 0. The curve is steep at the ends and nearly flat in the middle: a stump at 10% " +
            "error earns 1.10, one at 45% earns 0.10 — a tenth as loud — and one at exactly 50% " +
            "earns zero, which is the algorithm refusing to listen to a coin flip. Past 0.5 the " +
            "weight goes negative, so a learner that is reliably wrong is inverted and used anyway; " +
            "at ε = 0.9 its vote is worth exactly as much as a 10%-error learner's, pointed the " +
            "other way.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "α",
                    listOf(
                        FigurePoint(0.05f, 0.991f), FigurePoint(0.1f, 0.866f),
                        FigurePoint(0.2f, 0.731f), FigurePoint(0.3f, 0.641f),
                        FigurePoint(0.4f, 0.568f), FigurePoint(0.45f, 0.533f),
                        FigurePoint(0.5f, 0.5f), FigurePoint(0.55f, 0.467f),
                        FigurePoint(0.6f, 0.432f), FigurePoint(0.7f, 0.359f),
                        FigurePoint(0.8f, 0.269f), FigurePoint(0.9f, 0.134f),
                        FigurePoint(0.95f, 0.009f),
                    ),
                ),
            ),
            markers = listOf(
                FigurePoint(0.1f, 0.866f, "1.10"),
                FigurePoint(0.5f, 0.5f, "0 — silent"),
                FigurePoint(0.9f, 0.134f, "−1.10", FigureTone.Warn),
            ),
            xLabel = "weighted error ε",
            yLabel = "α  (0 at mid-axis)",
        ),
    ),
    whatIsIt = listOf(
        "AdaBoost trains weak learners in sequence, each one on a re-weighted version of the data. Points the previous round got wrong have their weight increased, so the next learner is effectively fitting a different dataset — one concentrated on the hard cases.",
        "Two weightings are at work and they are easy to conflate. Sample weights decide what each learner sees. Learner weights — the α values — decide how loudly each one votes in the final prediction, and α = ½ln((1−ε)/ε) makes that vote a function of measured error: a stump at 10% error gets α ≈ 1.10, one at 45% gets α ≈ 0.10, and one at exactly 50% gets α = 0, which is the algorithm declining to listen to a coin flip. A learner *worse* than chance gets a negative α, meaning its prediction is inverted and still used.",
        "Its historical significance is that it answered an open theoretical question — whether \"weakly learnable\" and \"strongly learnable\" are the same thing — with a constructive algorithm. Later work reframed it as gradient boosting with an exponential loss, which explains its one real weakness: exp(−y·f(x)) grows without bound as a point is misclassified more confidently, so a mislabelled example accumulates enormous weight and drags the whole ensemble toward it. That is why AdaBoost is notably sensitive to label noise, and why gradient boosting with a robust loss usually replaces it now.",
    ),
    steps = listOf(
        StepCard(1, "Weight Every Point Equally", "wᵢ = 1/n. The first learner sees the plain dataset.", 0xFFF59E0B),
        StepCard(2, "Fit a Weak Learner", "Usually a depth-1 stump. It only has to beat chance.", 0xFF818CF8),
        StepCard(3, "Measure Weighted Error", "ε = Σ wᵢ over the misclassified points, not a raw count.", 0xFF60A5FA),
        StepCard(4, "Compute Its Vote", "α = ½ln((1−ε)/ε). Low error earns a loud vote; ε = 0.5 earns silence.", 0xFF10B981),
        StepCard(5, "Re-weight and Renormalize", "Misses scale by e^α, hits by e^−α, then rescale to sum to 1.", 0xFF14B8A6),
        StepCard(6, "Predict by Weighted Sign", "sign(Σ αₜhₜ(x)). A weighted vote, not a majority.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Weighted error", "εₜ = Σᵢ wᵢ·1[hₜ(xᵢ) ≠ yᵢ]", "Weighted, so hard points count more."),
        FormulaEntry("Learner weight", "αₜ = ½ln((1−εₜ)/εₜ)", "Zero at ε = 0.5; negative above it."),
        FormulaEntry("Re-weight", "wᵢ ← wᵢ·exp(±αₜ), then normalize", "Up for misses, down for hits."),
        FormulaEntry("Prediction", "H(x) = sign(Σₜ αₜhₜ(x))", "Weighted vote over all rounds."),
        FormulaEntry("Loss", "Σ exp(−yᵢf(xᵢ))", "Exponential — the source of its noise sensitivity."),
        FormulaEntry("Training error bound", "≤ ∏ₜ 2√(εₜ(1−εₜ))", "Falls exponentially while each εₜ < 0.5."),
    ),
    notationKey = listOf(
        NotationEntry("εₜ", "weighted error of round t's learner"),
        NotationEntry("αₜ", "that learner's vote weight"),
        NotationEntry("weak learner", "any model reliably better than chance"),
        NotationEntry("stump", "a depth-1 decision tree — the usual choice"),
        NotationEntry("exponential loss", "exp(−y·f(x)), which AdaBoost is greedily minimizing"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "AdaBoost from scratch",
            accentColor = 0xFFF59E0B,
            code = """
                import numpy as np
                from sklearn.tree import DecisionTreeClassifier

                def adaboost(X, y, rounds=50):      # y in {-1, +1}
                    n = len(y)
                    w = np.full(n, 1 / n)
                    models, alphas = [], []

                    for _ in range(rounds):
                        stump = DecisionTreeClassifier(max_depth=1)
                        stump.fit(X, y, sample_weight=w)     # weights enter HERE
                        pred = stump.predict(X)

                        err = w[pred != y].sum()             # weighted, not a raw count
                        err = np.clip(err, 1e-10, 1 - 1e-10) # guard the perfect stump
                        alpha = 0.5 * np.log((1 - err) / err)

                        w *= np.exp(-alpha * y * pred)       # misses up, hits down
                        w /= w.sum()

                        models.append(stump)
                        alphas.append(alpha)

                    return models, alphas

                def predict(X, models, alphas):
                    return np.sign(sum(a * m.predict(X) for m, a in zip(models, alphas)))
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why one bad label does so much damage",
            accentColor = 0xFFEC4899,
            code = """
                # The exponential loss is unbounded, so a point that is confidently wrong
                # accumulates weight without limit — and the next learner is then fitting
                # almost nothing else.
                import numpy as np

                w = 1 / 100                       # one point out of 100
                for round_ in range(1, 8):
                    alpha = 0.5 * np.log((1 - 0.2) / 0.2)   # a steady 20%-error learner
                    w *= np.exp(alpha)                      # missed every round
                    print(round_, round(w, 4))
                # After 7 rounds this single point's unnormalized weight is ~15x its start.
                # If its label was simply wrong, the ensemble has spent 7 rounds chasing it.

                # Gradient boosting with a Huber or logistic loss grows linearly instead of
                # exponentially in the residual, which is why it tolerates noisy labels.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFFF59E0B, "Viola-Jones Face Detection", "The 2001 cascade that put real-time face detection in every camera was AdaBoost over Haar features."),
        ApplicationCard("history", 0xFF818CF8, "A Theoretical Answer", "It resolved whether weak and strong learnability are equivalent — constructively, with a working algorithm."),
        ApplicationCard("chart", 0xFF10B981, "Interpretable Ensembles", "Depth-1 stumps make the fitted model a readable list of single-feature rules with weights."),
    ),
    takeaways = listOf(
        "Sample weights decide what each learner sees; α weights decide how loudly it votes.",
        "α = ½ln((1−ε)/ε) silences a coin-flip learner and inverts a worse-than-chance one.",
        "It is gradient boosting under an exponential loss — that identification explains its behaviour.",
        "The unbounded loss makes it markedly sensitive to mislabelled data.",
    ),
    crossLinks = listOf(
        CrossLink("gradient_boosting", "Gradient Boosting Machines (GBM)"),
        CrossLink("decision_trees", "Decision Trees"),
        CrossLink("bagging", "Bagging (Bootstrap Aggregating)"),
        CrossLink("bias_variance", "Bias-Variance Tradeoff"),
    ),
)
