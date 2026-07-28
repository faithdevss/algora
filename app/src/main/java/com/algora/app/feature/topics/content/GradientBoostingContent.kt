package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val gradientBoostingContent = TopicContent(
    topicId = "gradient_boosting",
    whatIsIt = listOf(
        "Gradient boosting builds an ensemble one weak learner at a time, each new tree trained to correct what the ensemble so far still gets wrong.",
        "The \"gradient\" is literal: the target each new tree fits is the negative gradient of the loss with respect to the current prediction. For squared error that is exactly the residual, which is why the algorithm is usually first explained as \"fit the errors\". XGBoost, LightGBM and CatBoost are engineering refinements of this loop.",
    ),
    steps = listOf(
        StepCard(1, "Start With a Constant", "F₀(x) is the value that minimizes the loss overall — the mean for squared error.", 0xFF6366F1),
        StepCard(2, "Compute Pseudo-Residuals", "rᵢ = −∂L(yᵢ, F(xᵢ))/∂F(xᵢ) — what the ensemble is still missing on each row.", 0xFF3B82F6),
        StepCard(3, "Fit a Shallow Tree to Them", "Depth 3–6 is typical: each learner should be weak, correcting a little.", 0xFFF59E0B),
        StepCard(4, "Shrink and Add", "F_m = F_{m−1} + ν·h_m, with learning rate ν ≈ 0.1 so no single tree dominates.", 0xFF10B981),
        StepCard(5, "Stop Early", "Watch validation loss — unlike a forest, more rounds eventually overfit.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Additive model", "F_m(x) = F_{m−1}(x) + ν·h_m(x)", "One shrunken learner per round."),
        FormulaEntry("Pseudo-residual", "rᵢ = −∂L(yᵢ, F(xᵢ)) / ∂F(xᵢ)", "For L = ½(y−F)² this is simply y − F."),
        FormulaEntry("Regularized objective", "Σ L(yᵢ, ŷᵢ) + Σ [γT + ½λ‖w‖²]", "XGBoost penalizes leaf count T and leaf weights w."),
        FormulaEntry("Rounds vs rate", "M ∝ 1/ν", "Halving the learning rate roughly doubles the trees needed."),
    ),
    notationKey = listOf(
        NotationEntry("F_m", "ensemble prediction after m rounds"),
        NotationEntry("h_m", "the weak learner added at round m"),
        NotationEntry("ν", "learning rate / shrinkage"),
        NotationEntry("L", "differentiable loss — squared error, logistic, ranking…"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Gradient boosting for squared error",
            accentColor = 0xFF6366F1,
            code = """
                class GradientBoosting(
                    private val rounds: Int = 200,
                    private val learningRate: Double = 0.1,
                    private val maxDepth: Int = 3,
                ) {
                    private var base = 0.0
                    private val trees = mutableListOf<RegressionTree>()

                    fun fit(x: Array<DoubleArray>, y: DoubleArray) {
                        base = y.average()                       // F0: the constant that minimizes MSE
                        val prediction = DoubleArray(y.size) { base }

                        repeat(rounds) {
                            // For squared error the negative gradient IS the residual.
                            val residuals = DoubleArray(y.size) { i -> y[i] - prediction[i] }

                            val tree = RegressionTree(maxDepth).apply { fit(x, residuals) }
                            trees += tree

                            for (i in x.indices) prediction[i] += learningRate * tree.predict(x[i])
                        }
                    }

                    fun predict(sample: DoubleArray): Double =
                        base + trees.sumOf { learningRate * it.predict(sample) }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF6366F1, "Tabular Competitions", "Gradient-boosted trees remain the default winner on structured data, ahead of deep nets."),
        ApplicationCard("target", 0xFF3B82F6, "Search Ranking", "LambdaMART — boosting with a ranking loss — powers learning-to-rank systems."),
        ApplicationCard("globe", 0xFF10B981, "Credit & Churn Models", "Strong accuracy with per-feature attribution that a risk team can defend."),
    ),
    takeaways = listOf(
        "Boosting reduces bias by fitting errors sequentially; bagging reduces variance by averaging in parallel.",
        "Residuals are a special case — the general target is the negative gradient of the loss.",
        "Learning rate and round count trade off directly; small ν with early stopping is the safe recipe.",
        "Trees must stay shallow: strong learners defeat the point of boosting and overfit fast.",
    ),
    crossLinks = listOf(
        CrossLink("random_forest", "Random Forest"),
        CrossLink("decision_trees", "Decision Trees"),
        CrossLink("gradient_descent_variants", "Gradient Descent Variants"),
    ),
)
