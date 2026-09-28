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

internal val cohensKappaContent = TopicContent(
    topicId = "cohens_kappa",
    figure = Figure(
        caption = "The same model at two thresholds, with the four cells that produce both verdicts. " +
            "At t = 0.9 it predicts negative 1,000 times out of 1,000 — the top row is a model that " +
            "does nothing — and accuracy still reads 0.912, because 912 of the cases are negative. " +
            "Kappa reads 0.000: the marginals say chance agreement is already 0.912, so there is " +
            "nothing left to credit. At t = 0.5 the model finds 35 of the 88 positives with no false " +
            "alarms, accuracy moves 3.5 points to 0.947, and kappa moves 0.546 — the outlined cells " +
            "are where those two metrics disagree about how much happened.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0", "0", "88", "912", "0.912", "0.000"),
                listOf("35", "0", "53", "912", "0.947", "0.546"),
            ),
            rowHeaders = listOf("t=0.9", "t=0.5"),
            colHeaders = listOf("TP", "FP", "FN", "TN", "acc", "κ"),
            marks = listOf(
                FigureCell(0, 5, FigureTone.Warn),
                FigureCell(1, 5),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Cohen's kappa corrects accuracy for the agreement chance alone would produce, given how imbalanced the classes are: κ = (observed − expected) / (1 − expected), where expected comes from the row and column marginals of the confusion matrix rather than from the diagonal.",
        "The lab's model at t = 0.9 predicts negative for every one of the 1,000 cases — tp 0, fp 0, fn 88, tn 912. Accuracy is 0.912, which sounds like a strong model. But 0.912 is also exactly the majority-class baseline: predicting \"negative\" for everyone, with no model at all, scores the same. Kappa knows this: it comes out to 0.000, correctly reporting zero agreement beyond what the base rate hands you for free.",
        "At t = 0.5 the model is doing real work — tp 35, fp 0, fn 53, tn 912 — and accuracy climbs to 0.947. Kappa is 0.546: still a real number above zero, but a substantial discount from what 0.947 alone implies, because a large share of that accuracy is still the free 91.2% base rate. Matthews correlation coefficient (0.613 here) is a cousin built from the same four cells with a different formula, correlated with kappa but not identical to it — useful as a second opinion when the two disagree.",
    ),
    steps = listOf(
        StepCard(1, "Compute Observed Accuracy", "(TP+TN)/total, from the confusion matrix.", 0xFF0EA5E9),
        StepCard(2, "Compute Expected Accuracy", "From the row/column marginals, as if by chance.", 0xFF3B82F6),
        StepCard(3, "Apply the Formula", "(observed − expected) / (1 − expected).", 0xFF8B5CF6),
        StepCard(4, "The Degenerate Case", "t=0.9: accuracy 0.912 = majority baseline, kappa 0.000.", 0xFFF59E0B),
        StepCard(5, "The Working Case", "t=0.5: accuracy 0.947, kappa 0.546 — a real discount.", 0xFFEC4899),
        StepCard(6, "Cross-Check with MCC", "0.613 at t=0.5 — a related but distinct correction.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Kappa", "(pₒ − pₑ) / (1 − pₑ)", "pₒ observed, pₑ expected-by-chance accuracy."),
        FormulaEntry("At t = 0.9", "acc 0.912, κ 0.000", "Predicting the majority class for everyone."),
        FormulaEntry("At t = 0.5", "acc 0.947, κ 0.546", "The same model doing real, discounted work."),
        FormulaEntry("Majority baseline", "0.912", "What accuracy gives away for free on this data."),
        FormulaEntry("MCC", "(TP·TN − FP·FN) / √[(TP+FP)(TP+FN)(TN+FP)(TN+FN)]", "0.613 at t=0.5 — a cousin correction."),
        FormulaEntry("Range", "-1 to 1", "0 is chance-level agreement, 1 is perfect."),
    ),
    notationKey = listOf(
        NotationEntry("κ", "Cohen's kappa"),
        NotationEntry("pₒ", "observed agreement — plain accuracy"),
        NotationEntry("pₑ", "expected agreement from the marginals alone"),
        NotationEntry("majority baseline", "the accuracy of always predicting the more common class"),
        NotationEntry("MCC", "Matthews correlation coefficient, a related chance-corrected score"),
        NotationEntry("marginals", "the row and column totals of the confusion matrix"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Accuracy 0.912, kappa 0.000 — same predictions",
            accentColor = 0xFF0EA5E9,
            code = """
                from sklearn.metrics import accuracy_score, cohen_kappa_score

                # t = 0.9: the model predicts negative for every case
                predicted = probabilities >= 0.9
                print(accuracy_score(y_test, predicted))     # 0.912
                print(cohen_kappa_score(y_test, predicted))  # 0.000
                #
                # 0.912 is also what predicting "negative" with zero information gets you -- the
                # base rate. Kappa correctly reports that this model has contributed nothing.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "A working threshold, honestly discounted",
            accentColor = 0xFFEC4899,
            code = """
                from sklearn.metrics import matthews_corrcoef

                predicted = probabilities >= 0.5
                print(accuracy_score(y_test, predicted))      # 0.947
                print(cohen_kappa_score(y_test, predicted))   # 0.546
                print(matthews_corrcoef(y_test, predicted))   # 0.613
                #
                # Kappa and MCC agree the model is doing real work here (both well above 0), and
                # both discount the headline 0.947 substantially -- report one of them alongside
                # accuracy whenever the base rate is far from 50/50.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("check", 0xFF0EA5E9, "Imbalanced Classification", "The chance-corrected number to report alongside accuracy."),
        ApplicationCard("users", 0xFF3B82F6, "Inter-Rater Agreement", "Kappa's original use — two human raters, corrected for chance."),
        ApplicationCard("finance", 0xFF8B5CF6, "Model Audits", "Exposes a model that is quietly just predicting the majority class."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Balanced classes with equal costs — plain accuracy already tells the story."),
    ),
    takeaways = listOf(
        "Kappa discounts accuracy by the agreement chance alone would produce, from the marginals.",
        "At t = 0.9 the model predicts one class for everyone: accuracy 0.912, kappa exactly 0.000.",
        "0.912 is also the majority-class baseline — kappa correctly says this model added nothing.",
        "At t = 0.5, accuracy 0.947 and kappa 0.546 — real work, but a substantial discount.",
        "Matthews correlation (0.613 here) is a related but distinct chance-corrected cousin.",
        "The same underlying predictions can look excellent by accuracy and unremarkable by kappa.",
        "Report kappa or MCC whenever the base rate is far from balanced.",
    ),
    crossLinks = listOf(
        CrossLink("confusion_matrix", "Confusion Matrix"),
        CrossLink("accuracy", "Accuracy"),
        CrossLink("f1_score", "F1 Score"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
