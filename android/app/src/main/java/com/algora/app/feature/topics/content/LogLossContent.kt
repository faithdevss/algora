package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBar
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val logLossContent = TopicContent(
    topicId = "log_loss",
    figure = Figure(
        caption = "The same model's scores pushed toward 0 and 1 by a monotone transform — the " +
            "ranking is untouched, so AUC stays at 0.9692 and every threshold metric on this page's " +
            "siblings reports no change at all. The two proper scoring rules do notice, and they " +
            "disagree about how much: log loss rises 0.1750 → 0.2876, up 64%, while Brier rises " +
            "0.0437 → 0.0556, up 27%. The axis top is 0.30, so the height difference between the " +
            "pairs is also the point — Brier squares the error and log loss takes its logarithm, " +
            "which is unbounded as p → 0. Choosing between them is choosing how expensive a " +
            "confident mistake should be.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("log", 0.583f, FigureTone.Primary),
                FigureBar("log′", 0.959f, FigureTone.Warn),
                FigureBar("Brier", 0.146f, FigureTone.Primary),
                FigureBar("Brier′", 0.185f, FigureTone.Accent),
            ),
            yLabel = "loss  (0 → 0.30)",
        ),
    ),
    whatIsIt = listOf(
        "Log loss reads the probability a model states, not the label it would produce past a threshold — which is what separates it from every metric in this category built on a confusion matrix. It is the negative log-likelihood of the true labels under the model's own stated probabilities: -1/n Σ [y·ln(p) + (1-y)·ln(1-p)]. On the lab's 1,000 predictions it averages 0.1750.",
        "That average hides how unevenly the penalty falls. The single worst prediction — the model said 0.109 for a case that was actually positive — contributes 2.214 to the sum, against a mean per-example contribution of 0.1750. One confidently wrong call costs about 13× an average one, because -ln(p) grows without bound as p → 0 while a merely mediocre call near p = 0.5 costs only about -ln(0.5) ≈ 0.69.",
        "Compare it to Brier score, its squared-error cousin: pushing the same model's scores toward 0 and 1 with a monotone transform (unchanged ranking, wrong probabilities) moves log loss from 0.1750 to 0.2876 — a 64% jump — while Brier only moves from 0.0437 to 0.0556, a 27% jump. Squaring the error, as Brier does, is gentler on confident mistakes than the logarithm is; log loss is the sharper instrument when a wrong, confident probability needs to be expensive.",
    ),
    steps = listOf(
        StepCard(1, "Take the Stated Probability", "Not a thresholded label — the raw score.", 0xFF0EA5E9),
        StepCard(2, "Apply -ln(p) or -ln(1-p)", "Depending on the true label.", 0xFF3B82F6),
        StepCard(3, "Average Over Every Case", "0.1750 on the lab's model.", 0xFF8B5CF6),
        StepCard(4, "Find the Worst Offender", "Score 0.109 on a true positive: contributes 2.214.", 0xFFF59E0B),
        StepCard(5, "Compare to the Mean", "2.214 against 0.1750 — about 13× an average case.", 0xFFEC4899),
        StepCard(6, "Contrast with Brier", "0.1750→0.2876 (log loss) vs 0.0437→0.0556 (Brier) under the same distortion.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Log loss", "-1/n Σ [y ln p + (1-y) ln(1-p)]", "Measured 0.1750."),
        FormulaEntry("Worst case", "-ln(0.109) = 2.214", "Against a mean contribution of 0.1750."),
        FormulaEntry("Ratio", "2.214 / 0.1750 ≈ 12.7", "One confident miss costs roughly 13 average ones."),
        FormulaEntry("Brier score", "1/n Σ (p - y)²", "The squared-error cousin — gentler on confident errors."),
        FormulaEntry("Overconfident model", "log loss 0.1750→0.2876", "AUC is unchanged at 0.9692 under the same distortion."),
        FormulaEntry("Same distortion, Brier", "0.0437 → 0.0556", "A smaller relative jump than log loss's."),
    ),
    notationKey = listOf(
        NotationEntry("p", "the model's stated probability for the positive class"),
        NotationEntry("y", "the true label, 0 or 1"),
        NotationEntry("clipping", "coercing p away from exactly 0 or 1 to keep ln finite"),
        NotationEntry("Brier score", "the squared-error alternative — see the code below"),
        NotationEntry("calibration", "whether stated probabilities match observed frequencies"),
        NotationEntry("cross-entropy", "log loss's other name, from information theory"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Where the average comes from",
            accentColor = 0xFF0EA5E9,
            code = """
                import numpy as np
                from sklearn.metrics import log_loss

                print(log_loss(y_test, probabilities))  # 0.1750

                contributions = -(y_test * np.log(probabilities.clip(1e-15, 1 - 1e-15)) +
                                   (1 - y_test) * np.log(1 - probabilities.clip(1e-15, 1 - 1e-15)))
                worst = contributions.argmax()
                print(f"worst: {contributions[worst]:.3f} at p={probabilities[worst]:.4f} "
                      f"(mean {contributions.mean():.4f})")
                # worst: 2.214 at p=0.1093 (mean 0.1750) -- about 12.7x the average example
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Sharper than Brier on the same distortion",
            accentColor = 0xFFEC4899,
            code = """
                from sklearn.metrics import log_loss, brier_score_loss

                overconfident = np.where(probabilities >= 0.5,
                                          0.5 + (probabilities - 0.5) * 1.98,
                                          probabilities * 0.02)

                print(log_loss(y_test, probabilities), log_loss(y_test, overconfident))
                # 0.1750 0.2876  (+64%)
                print(brier_score_loss(y_test, probabilities), brier_score_loss(y_test, overconfident))
                # 0.0437 0.0556  (+27%)
                #
                # Same monotone distortion, same unchanged AUC (0.9692) -- but log loss reacts about
                # 2.4x harder than Brier does, because -ln(p) is unbounded and (p-y)^2 is not.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF0EA5E9, "Training Objective", "The loss neural nets and logistic regression actually minimize."),
        ApplicationCard("finance", 0xFFEC4899, "Calibrated Risk", "Where the probability itself feeds a downstream cost formula."),
        ApplicationCard("check", 0xFF3B82F6, "Model Selection", "Catches miscalibration that AUC and accuracy both miss."),
        ApplicationCard("help", 0xFF10B981, "When Not To", "If only the ranking matters and probabilities are never used directly, AUC is simpler."),
    ),
    takeaways = listOf(
        "Reads the stated probability, not a thresholded label — no confusion matrix underneath it.",
        "Measured 0.1750 on the lab's model; the worst single case contributes 2.214.",
        "2.214 against a mean of 0.1750 is roughly 13× — one confident miss costs that many average ones.",
        "It punishes confident wrongness harder than Brier score does, because -ln(p) is unbounded.",
        "A monotone rescoring that leaves AUC at 0.9692 still moves log loss from 0.1750 to 0.2876.",
        "The same rescoring only moves Brier from 0.0437 to 0.0556 — log loss is the sharper instrument.",
        "Use it whenever the probability itself, not just the ranking, will be acted on.",
    ),
    crossLinks = listOf(
        CrossLink("auc", "AUC Score"),
        CrossLink("logistic_regression", "Logistic Regression"),
        CrossLink("confusion_matrix", "Confusion Matrix"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
