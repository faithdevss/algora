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

internal val isotonicRegressionContent = TopicContent(
    topicId = "isotonic_regression",
    figure = Figure(
        caption = "pava([1, 3, 2, 4]) returns [1, 2.5, 2.5, 4]. The 3 and the 2 are the violation — index 2 " +
            "is lower than index 1 — so PAVA pools them into their weighted mean, 2.5, and the pair " +
            "moves together from then on. The 1 and the 4 already obeyed the ordering, so they pass " +
            "through untouched; only the violating pair was ever adjusted.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "raw",
                    listOf(
                        FigurePoint(0f, 0f), FigurePoint(0.33f, 0.667f),
                        FigurePoint(0.67f, 0.333f), FigurePoint(1f, 1f),
                    ),
                    tone = FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "PAVA fit",
                    listOf(
                        FigurePoint(0f, 0f), FigurePoint(0.33f, 0.5f),
                        FigurePoint(0.67f, 0.5f), FigurePoint(1f, 1f),
                    ),
                ),
            ),
            xLabel = "index",
            yLabel = "value (1 → 4 normalized)",
        ),
    ),
    whatIsIt = listOf(
        "Isotonic regression finds the least-squares fit subject to one constraint: the fitted values must never decrease. No functional form is assumed — not linear, not polynomial, not smooth — only monotonicity. The result is a step function that is flat wherever the data disagreed with the ordering.",
        "The algorithm is Pool Adjacent Violators (PAVA), and it is genuinely simple. Walk left to right; whenever the current value is lower than the block before it, that pair violates monotonicity, so merge them and replace both with their weighted average. Merging can create a new violation with the block before that, so keep merging backwards until the sequence is non-decreasing again. Each point is merged at most once, so the whole thing runs in O(n) after sorting — and it returns the exact global optimum, not an approximation.",
        "Its most common use today is probability calibration. A model's raw scores often rank well while being badly miscalibrated — a gradient-boosted classifier's 0.9 might correspond to a true rate of 0.6. Isotonic regression maps scores to calibrated probabilities using only the assumption that a higher score should mean a higher probability, which is exactly the assumption you are willing to make. Compared to Platt scaling it is far more flexible, since it fits any monotone shape rather than a sigmoid, but that flexibility costs data: it overfits on small validation sets, where Platt's two parameters are the safer choice.",
    ),
    steps = listOf(
        StepCard(1, "Sort by the Predictor", "PAVA operates on an ordered sequence, so ordering comes first.", 0xFF6366F1),
        StepCard(2, "Walk Forward", "Take each value in turn as a new block of weight one.", 0xFF818CF8),
        StepCard(3, "Detect a Violation", "The new block is lower than the previous one — the ordering has been broken.", 0xFF60A5FA),
        StepCard(4, "Pool the Pair", "Replace both with their weighted mean, which is the best constrained fit for them.", 0xFF10B981),
        StepCard(5, "Cascade Backwards", "The merge may violate against the block before it. Keep merging until non-decreasing.", 0xFFF59E0B),
        StepCard(6, "Read Off the Steps", "Each surviving block is one flat step. Fewer blocks means the constraint bound harder.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "min Σ wᵢ(yᵢ − ŷᵢ)² s.t. ŷ₁ ≤ ŷ₂ ≤ … ≤ ŷₙ", "Least squares under an order constraint."),
        FormulaEntry("Pooled value", "(w₁y₁ + w₂y₂)/(w₁ + w₂)", "The weighted mean of a merged block."),
        FormulaEntry("Complexity", "O(n) after sorting", "Each element is merged at most once."),
        FormulaEntry("Optimality", "exact global minimum", "PAVA is not a heuristic."),
        FormulaEntry("Degrees of freedom", "number of blocks", "Flexibility is data-determined, not chosen."),
        FormulaEntry("Platt alternative", "p = 1/(1 + exp(As + B))", "Two parameters, for when data is scarce."),
    ),
    notationKey = listOf(
        NotationEntry("isotonic", "non-decreasing"),
        NotationEntry("antitonic", "non-increasing — the same algorithm on negated values"),
        NotationEntry("PAVA", "Pool Adjacent Violators Algorithm"),
        NotationEntry("block", "a set of pooled points sharing one fitted value"),
        NotationEntry("calibration", "making predicted probabilities match observed frequencies"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "PAVA in full",
            accentColor = 0xFF6366F1,
            code = """
                def pava(y, w=None):
                    \"\"\"Exact solution, O(n). Each point joins a block at most once, so the
                    inner while loop is amortized constant.\"\"\"
                    y = list(y)
                    w = [1.0] * len(y) if w is None else list(w)
                    values, weights = [], []

                    for value, weight in zip(y, w):
                        # Merge backwards while the previous block sits ABOVE this one.
                        while values and values[-1] > value:
                            v, wt = values.pop(), weights.pop()
                            value = (value * weight + v * wt) / (weight + wt)
                            weight += wt
                        values.append(value)
                        weights.append(weight)

                    out = []
                    for v, wt in zip(values, weights):
                        out.extend([v] * int(wt))
                    return out

                print(pava([1, 3, 2, 4]))     # [1, 2.5, 2.5, 4] — the 3 and 2 pooled
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Calibrating a classifier, and when not to",
            accentColor = 0xFF10B981,
            code = """
                from sklearn.calibration import CalibratedClassifierCV
                from sklearn.metrics import brier_score_loss

                raw = GradientBoostingClassifier().fit(X_train, y_train)

                # Isotonic: any monotone mapping. Powerful, and hungry for data — with fewer
                # than ~1000 calibration samples it will overfit the calibration set itself.
                iso = CalibratedClassifierCV(raw, method="isotonic", cv=5).fit(X_train, y_train)

                # Platt: a two-parameter sigmoid. Less flexible, far more stable when small.
                platt = CalibratedClassifierCV(raw, method="sigmoid", cv=5).fit(X_train, y_train)

                for name, model in [("raw", raw), ("isotonic", iso), ("platt", platt)]:
                    p = model.predict_proba(X_test)[:, 1]
                    print(name, round(brier_score_loss(y_test, p), 4))
                # Note the ranking is unchanged by calibration — AUC is identical. Only the
                # numbers attached to the ranking move.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("chart", 0xFF6366F1, "Probability Calibration", "The dominant use: turning a well-ranked but miscalibrated score into an honest probability."),
        ApplicationCard("flask", 0xFF818CF8, "Dose-Response", "Higher dose should not reduce response; isotonic encodes that without assuming a curve shape."),
        ApplicationCard("trend", 0xFF10B981, "Ranking & Ordinal Data", "Monotone constraints in learning-to-rank, where the ordering is the only thing you trust."),
    ),
    takeaways = listOf(
        "Least squares subject to monotonicity — no functional form assumed beyond direction.",
        "PAVA solves it exactly in O(n) after sorting, by pooling adjacent violators backwards.",
        "The fit is a step function whose block count is the model's data-determined flexibility.",
        "It is the flexible choice for probability calibration, but overfits on small sets where Platt scaling is safer.",
    ),
    crossLinks = listOf(
        CrossLink("model_evaluation", "Model Evaluation"),
        CrossLink("logistic_regression", "Logistic Regression"),
        CrossLink("gradient_boosting", "Gradient Boosting"),
        CrossLink("linear_regression", "Linear Regression"),
    ),
)
