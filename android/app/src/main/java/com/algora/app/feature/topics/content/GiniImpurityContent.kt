package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val giniImpurityContent = TopicContent(
    topicId = "gini_impurity",
    whatIsIt = listOf(
        "Gini impurity is a splitting criterion a decision tree uses while growing, not a metric reported once a model is finished: 1 − Σp², the chance two randomly drawn labels from a node disagree. It is a different measure from the economics Gini coefficient of inequality — both are named after the statistician Corrado Gini (impurity is his \"mutability\" / Gini–Simpson index), but one measures heterogeneity and the other inequality.",
        "On a parent node of 400 examples split evenly (200/200), gini is 0.500 — the maximum for two classes — and entropy is 1.000, also its maximum. Four candidate splits of that node score: a perfect split (200/0 · 0/200) maxes every criterion at once — gini gain 0.5000, entropy gain 1.0000, error-rate gain 0.5000. The other three splits disagree with each other in a way that matters for which one a tree picks.",
        "Splits A (150/50 · 50/150) and C (200/100 · 0/100) get identical misclassification-rate gain — 0.2500 for both — but Gini and entropy both correctly prefer C (gini gain 0.1667 vs 0.1250, entropy 0.3113 vs 0.1887). Misclassification rate is blind to a real difference the smoother criteria can see, which is the actual reason trees are grown on Gini or entropy and not on error rate directly.",
    ),
    steps = listOf(
        StepCard(1, "Score the Parent", "200/200 node: gini 0.500, entropy 1.000 — both at maximum.", 0xFF0EA5E9),
        StepCard(2, "Score Each Candidate Split", "Weighted impurity of the two children.", 0xFF3B82F6),
        StepCard(3, "Take the Gain", "Parent impurity minus the weighted child impurity.", 0xFF8B5CF6),
        StepCard(4, "Find the Perfect Split", "200/0 · 0/200 maxes gini, entropy and error gain alike.", 0xFFF59E0B),
        StepCard(5, "Find the Tie", "A and C: both 0.2500 error gain — indistinguishable by error rate.", 0xFFEC4899),
        StepCard(6, "See Gini Break the Tie", "Gini gain 0.1250 vs 0.1667 — correctly prefers C.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Gini", "1 − Σp²", "Max 0.5 for two classes, at p=0.5."),
        FormulaEntry("Entropy", "−Σp·log₂p", "Max 1.0 for two classes, at p=0.5."),
        FormulaEntry("Perfect split", "gini 0.5000 · entropy 1.0000 · error 0.5000", "All three agree when a split is flawless."),
        FormulaEntry("Split A vs C, error gain", "0.2500 = 0.2500", "Identical — misclassification rate cannot separate them."),
        FormulaEntry("Split A vs C, gini gain", "0.1250 vs 0.1667", "Gini correctly prefers C."),
        FormulaEntry("Weakest split (D)", "gini 0.0134 · entropy 0.0262 · error 0.0366", "All three agree it's the worst candidate."),
    ),
    notationKey = listOf(
        NotationEntry("impurity", "how mixed the classes are at a node — 0 is pure"),
        NotationEntry("gain", "parent impurity minus the split's weighted child impurity"),
        NotationEntry("misclassification rate", "1 − max(p) — the criterion that ties on A vs C"),
        NotationEntry("weighted", "each child's impurity scaled by its share of the parent's examples"),
        NotationEntry("splitting criterion", "what a tree optimizes while growing, distinct from a reported evaluation metric"),
        NotationEntry("Gini coefficient", "the economics measure of inequality, from the same statistician — a different measure, not this"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Gini and entropy break a tie error rate can't see",
            accentColor = 0xFF0EA5E9,
            code = """
                def gini(pos, neg):
                    n = pos + neg
                    if n == 0: return 0.0
                    p = pos / n
                    return 1 - p * p - (1 - p) * (1 - p)

                def misclassification(pos, neg):
                    n = pos + neg
                    if n == 0: return 0.0
                    return 1 - max(pos, neg) / n

                # Splits of a 200/200 parent:
                split_a = (150, 50, 50, 150)  # gini gain 0.1250, error gain 0.2500
                split_c = (200, 100, 0, 100)    # gini gain 0.1667, error gain 0.2500
                #
                # Error-rate gain ties at 0.2500 for both splits. Gini gain does not -- and Gini's
                # preference for C is why trees are grown on Gini or entropy, not error rate.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "sklearn's two criteria, same tree structure most of the time",
            accentColor = 0xFF10B981,
            code = """
                from sklearn.tree import DecisionTreeClassifier

                gini_tree = DecisionTreeClassifier(criterion="gini")
                entropy_tree = DecisionTreeClassifier(criterion="entropy")
                # They usually agree, because Gini and entropy are both strictly concave and
                # near-identical in shape -- the tie-breaking case above (A vs C) is where they
                # can, in principle, diverge from each other too, though it's rare in practice.
                # "criterion" is never "misclassification_rate" in sklearn's tree API for exactly
                # this reason: it does not reliably distinguish good splits from mediocre ones.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF0EA5E9, "Decision Trees", "The default split criterion CART and sklearn both use."),
        ApplicationCard("chart", 0xFF3B82F6, "Random Forests", "Feature importance is often computed from total Gini gain."),
        ApplicationCard("check", 0xFF8B5CF6, "Gradient Boosting", "Tree-based boosters inherit the same splitting logic per stage."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "It's not a report-time evaluation metric — use accuracy, F1 or AUC for that."),
    ),
    takeaways = listOf(
        "Gini impurity, 1 − Σp², is what a tree optimizes while splitting — not a final report metric.",
        "A balanced 200/200 parent scores the maximum: gini 0.500, entropy 1.000.",
        "A perfect split maxes gini, entropy and error-rate gain alike — 0.5000 / 1.0000 / 0.5000.",
        "Splits A and C tie on misclassification-rate gain at 0.2500 — indistinguishable by that criterion.",
        "Gini gain tells them apart: 0.1250 vs 0.1667, correctly preferring C, and entropy agrees.",
        "That blind spot is the actual reason trees split on Gini or entropy, not on error rate.",
        "Not to be confused with the economics Gini coefficient — same statistician, different measure.",
    ),
    crossLinks = listOf(
        CrossLink("random_forest", "Random Forests"),
        CrossLink("gradient_boosting", "Gradient Boosting Machines (GBM)"),
        CrossLink("confusion_matrix", "Confusion Matrix"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
