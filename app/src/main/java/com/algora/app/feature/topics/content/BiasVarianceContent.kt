package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val biasVarianceContent = TopicContent(
    topicId = "bias_variance",
    whatIsIt = listOf(
        "Expected prediction error decomposes into three parts: bias (how wrong the model is on average), variance (how much it changes when the training set changes) and irreducible noise.",
        "Model capacity moves bias and variance in opposite directions. A straight line through curved data is high bias — wrong no matter which sample you train on. A degree-15 polynomial is high variance — it fits every sample perfectly and a different one perfectly differently. The best model sits where their sum bottoms out, not where either is minimized.",
    ),
    steps = listOf(
        StepCard(1, "Fix the Target, Vary the Sample", "Imagine retraining the same model on many datasets drawn from the same source.", 0xFF6366F1),
        StepCard(2, "Bias Is the Average Miss", "How far the mean prediction across those fits sits from the truth.", 0xFF3B82F6),
        StepCard(3, "Variance Is the Spread", "How much individual fits scatter around that mean.", 0xFFF59E0B),
        StepCard(4, "Read the Symptoms", "High train error and high test error = bias. Low train error with a large gap to test = variance.", 0xFF10B981),
        StepCard(5, "Treat the Right One", "Bias wants more capacity or better features; variance wants more data, regularization or averaging.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Decomposition", "E[(y − f̂(x))²] = Bias[f̂(x)]² + Var[f̂(x)] + σ²", "Squared-error loss, expectation over training sets."),
        FormulaEntry("Bias", "Bias[f̂(x)] = E[f̂(x)] − f(x)", "Systematic error of the average fit."),
        FormulaEntry("Variance", "Var[f̂(x)] = E[(f̂(x) − E[f̂(x)])²]", "Sensitivity to the particular training sample."),
        FormulaEntry("Irreducible", "σ²", "Label noise — no model can go below it."),
    ),
    notationKey = listOf(
        NotationEntry("f(x)", "the true function generating the labels"),
        NotationEntry("f̂(x)", "the model fitted on one particular training set"),
        NotationEntry("E[·]", "expectation over training sets drawn from the same distribution"),
        NotationEntry("σ²", "variance of the label noise"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Measuring the decomposition empirically",
            accentColor = 0xFF6366F1,
            code = """
                // Refit on many bootstrap samples and watch how the predictions at a fixed
                // point scatter. The spread is variance; the offset of their mean is bias.
                fun biasVariance(
                    fitModel: (Array<DoubleArray>, DoubleArray) -> (DoubleArray) -> Double,
                    samples: List<Pair<Array<DoubleArray>, DoubleArray>>,
                    probe: DoubleArray,
                    truth: Double,
                ): Triple<Double, Double, Double> {
                    val predictions = samples.map { (x, y) -> fitModel(x, y)(probe) }
                    val mean = predictions.average()

                    val bias = mean - truth
                    val variance = predictions.sumOf { (it - mean) * (it - mean) } / predictions.size
                    val error = predictions.sumOf { (truth - it) * (truth - it) } / predictions.size

                    return Triple(bias * bias, variance, error)   // bias² + variance ≈ error
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF6366F1, "Model Selection", "Validation curves against capacity are the practical form of this tradeoff."),
        ApplicationCard("target", 0xFF3B82F6, "Debugging a Model", "Train-vs-test gap tells you whether to add data or add capacity — the two fixes are opposites."),
        ApplicationCard("robot", 0xFF10B981, "Ensembling", "Bagging attacks variance, boosting attacks bias; knowing which you have picks the method."),
    ),
    takeaways = listOf(
        "Total error = bias² + variance + noise; only the first two are yours to control.",
        "More capacity always trades bias down for variance up — the minimum of the sum is the goal.",
        "Diagnose before treating: a large train-test gap is variance, uniformly high error is bias.",
        "More data reduces variance but never bias — a linear model on curved data stays wrong.",
    ),
    crossLinks = listOf(
        CrossLink("regularization", "Regularization (L1 / L2)"),
        CrossLink("model_evaluation", "Model Evaluation"),
        CrossLink("random_forest", "Random Forest"),
    ),
)
