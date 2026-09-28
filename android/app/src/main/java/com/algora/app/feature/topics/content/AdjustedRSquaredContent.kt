package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val adjustedRSquaredContent = TopicContent(
    topicId = "adjusted_r_squared",
    whatIsIt = listOf(
        "Adjusted R² is 1 − (1 − R²)(n − 1)/(n − p − 1), where p is the number of predictors. The extra term is a penalty that grows with p, built specifically to counteract R²'s inability to ever fall when a column is added — plain R² treats every new predictor as free information, and this doesn't.",
        "On the lab's noise-column experiment — the same 8 additions of pure random-number columns that made R² climb every single step (0.6106 → 0.6453) — adjusted R² tells a different story: it starts at 0.6014 and ends at 0.5514, a net drop of 5 points across the run, even though plain R² rose the entire time on the identical fits.",
        "The drop isn't perfectly monotone: from the 3rd to the 4th noise column, adjusted R² actually ticks up, 0.5792 → 0.5875, before resuming its decline. That's not a bug in the correction — it reflects that one particular noise column happened to explain a bit more variance than the penalty cost that step. The useful reading isn't \"always falls\"; it's that unlike plain R², adjusted R² is willing to fall, and net does, when the added columns aren't earning their penalty.",
    ),
    steps = listOf(
        StepCard(1, "Take R² and n, p", "The plain score, sample size, and predictor count.", 0xFF0EA5E9),
        StepCard(2, "Apply the Penalty Term", "(n − 1) / (n − p − 1) — grows as p grows.", 0xFF3B82F6),
        StepCard(3, "Compute Adjusted R²", "1 − (1 − R²) × penalty.", 0xFF8B5CF6),
        StepCard(4, "Add Noise Columns", "R² rises every step: 0.6106 → 0.6453.", 0xFFF59E0B),
        StepCard(5, "Watch Adjusted R² Instead", "Starts 0.6014, ends 0.5514 — net down, unlike R².", 0xFFEC4899),
        StepCard(6, "Notice the One Exception", "+3 → +4 columns: 0.5792 → 0.5875 — a real, occasional uptick.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Adjusted R²", "1 − (1 − R²)(n − 1)/(n − p − 1)", "p is the number of predictors."),
        FormulaEntry("R² across 8 noise columns", "0.6106 → 0.6453", "Strictly rising the whole way."),
        FormulaEntry("Adjusted R², same run", "0.6014 → 0.5514", "Net falling — a 5-point drop."),
        FormulaEntry("The one uptick", "0.5792 → 0.5875", "Adding the 4th noise column, against the general trend."),
        FormulaEntry("Penalty term", "(n − 1)/(n − p − 1)", "Grows as p grows — the source of the correction."),
        FormulaEntry("n, base case", "44 points", "Same dataset MSE/MAE/R² use."),
    ),
    notationKey = listOf(
        NotationEntry("p", "the number of predictors in the model"),
        NotationEntry("n", "the number of observations"),
        NotationEntry("penalty term", "(n−1)/(n−p−1), the correction R² lacks"),
        NotationEntry("net trend", "the overall direction across a run, distinct from every single step"),
        NotationEntry("R²", "the unadjusted version — see that topic"),
        NotationEntry("overfitting", "what adding non-informative predictors risks, and this metric partially detects"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "R² only rises; adjusted R² doesn't have to",
            accentColor = 0xFF0EA5E9,
            code = """
                def adjusted_r2(r2, n, p):
                    return 1 - (1 - r2) * (n - 1) / (n - p - 1)

                n = 44
                r2_by_step =       [0.6106, 0.6135, 0.6168, 0.6184, 0.6355, 0.6357, 0.6399, 0.6450, 0.6453]
                adjusted_by_step = [adjusted_r2(r2, n, p) for p, r2 in enumerate(r2_by_step, start=1)]
                print([f"{v:.4f}" for v in adjusted_by_step])
                # ['0.6014', '0.5947', '0.5880', '0.5792', '0.5875', '0.5766', '0.5699', '0.5638', '0.5514']
                #
                # R^2 rises at all 8 steps. Adjusted R^2 falls net -- 0.6014 to 0.5514 -- with one
                # step (p=4) rising against the trend, which the penalty term does not prevent; it
                # only makes an unhelpful column *usually* cost more than it earns.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Use it to compare models with different predictor counts",
            accentColor = 0xFFEC4899,
            code = """
                # Two models on the same target, different feature sets:
                small_model_r2, small_p = 0.60, 2
                large_model_r2, large_p = 0.62, 9   # more predictors, higher plain R^2

                print(adjusted_r2(small_model_r2, n=44, p=small_p))   # higher
                print(adjusted_r2(large_model_r2, n=44, p=large_p))   # can come out lower
                #
                # Plain R^2 alone would always favor the larger model. Adjusted R^2 can flip that
                # verdict once the extra predictors' marginal contribution is smaller than what the
                # penalty term charges for them.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RegressionLab,
    applications = listOf(
        ApplicationCard("chart", 0xFF0EA5E9, "Feature Selection", "Penalizes predictors that aren't earning their place."),
        ApplicationCard("check", 0xFF3B82F6, "Model Comparison", "The right R² variant when predictor counts differ."),
        ApplicationCard("finance", 0xFF8B5CF6, "Reporting", "A more honest headline number than plain R² for multi-feature models."),
        ApplicationCard("help", 0xFFEC4899, "When Not To", "Comparing models with the same p — reduces to plain R² anyway."),
    ),
    takeaways = listOf(
        "Adjusted R² adds a penalty term, (n−1)/(n−p−1), that grows with the predictor count p.",
        "Adding 8 columns of pure noise makes plain R² rise every single step: 0.6106 → 0.6453.",
        "Adjusted R² on the identical fits nets a 5-point drop over the same run: 0.6014 → 0.5514.",
        "It isn't perfectly monotone — one step (p=4) actually rises, 0.5792 → 0.5875.",
        "The lesson is \"can fall, and net does,\" not \"always falls at every step.\"",
        "Plain R² cannot fall when a predictor is added, no matter how useless; adjusted R² can.",
        "Use it, not plain R², whenever comparing models with different numbers of predictors.",
    ),
    crossLinks = listOf(
        CrossLink("r_squared", "R-Squared (R²)"),
        CrossLink("mse", "Mean Squared Error (MSE)"),
        CrossLink("rfe", "Recursive Feature Elimination"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
