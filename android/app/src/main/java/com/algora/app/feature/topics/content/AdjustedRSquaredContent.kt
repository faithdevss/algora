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

internal val adjustedRSquaredContent = TopicContent(
    topicId = "adjusted_r_squared",
    figure = Figure(
        caption = "The page's lab: 44 points with one real predictor, then pure-noise columns added " +
            "one at a time up to 20, with both scores computed on every fit. Plain R² (solid) " +
            "never falls — 0.698 to 0.808 — because least squares can always use a new column to " +
            "fit some of the noise, and nothing in R² charges for the column. Adjusted R² (dashed) " +
            "multiplies the unexplained share by (n − 1)/(n − p − 1), which grows with every " +
            "column, and ends at 0.625, 6.5 points below where it started. It is not monotone: on " +
            "six steps a noise column happens to earn more than its penalty and the dashed line " +
            "ticks up, most visibly at the 3rd and 4th columns. The gap between the two lines is " +
            "the part of R² that came from the extra columns rather than from the data.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "R²",
                    listOf(
                        FigurePoint(0.00f, 0.390f), FigurePoint(0.05f, 0.392f), FigurePoint(0.10f, 0.393f),
                        FigurePoint(0.15f, 0.454f), FigurePoint(0.20f, 0.514f), FigurePoint(0.25f, 0.514f),
                        FigurePoint(0.30f, 0.515f), FigurePoint(0.35f, 0.520f), FigurePoint(0.40f, 0.585f),
                        FigurePoint(0.45f, 0.638f), FigurePoint(0.50f, 0.650f), FigurePoint(0.55f, 0.655f),
                        FigurePoint(0.60f, 0.678f), FigurePoint(0.65f, 0.679f), FigurePoint(0.70f, 0.726f),
                        FigurePoint(0.75f, 0.734f), FigurePoint(0.80f, 0.753f), FigurePoint(0.85f, 0.769f),
                        FigurePoint(0.90f, 0.832f), FigurePoint(0.95f, 0.834f), FigurePoint(1.00f, 0.834f),
                    ),
                    tone = FigureTone.Warn,
                ),
                FigureSeries(
                    "adjusted R²",
                    listOf(
                        FigurePoint(0.00f, 0.362f), FigurePoint(0.05f, 0.333f), FigurePoint(0.10f, 0.302f),
                        FigurePoint(0.15f, 0.337f), FigurePoint(0.20f, 0.371f), FigurePoint(0.25f, 0.338f),
                        FigurePoint(0.30f, 0.304f), FigurePoint(0.35f, 0.272f), FigurePoint(0.40f, 0.316f),
                        FigurePoint(0.45f, 0.346f), FigurePoint(0.50f, 0.323f), FigurePoint(0.55f, 0.289f),
                        FigurePoint(0.60f, 0.278f), FigurePoint(0.65f, 0.234f), FigurePoint(0.70f, 0.257f),
                        FigurePoint(0.75f, 0.221f), FigurePoint(0.80f, 0.199f), FigurePoint(0.85f, 0.171f),
                        FigurePoint(0.90f, 0.224f), FigurePoint(0.95f, 0.167f), FigurePoint(1.00f, 0.102f),
                    ),
                    tone = FigureTone.Accent,
                    dashed = true,
                ),
            ),
            markers = listOf(
                FigurePoint(1f, 0.834f, "R² 0.808", FigureTone.Warn),
                FigurePoint(1f, 0.102f, "adjusted 0.625"),
            ),
            xLabel = "noise columns added, 0 → 20",
            yLabel = "score, 0.60 to 0.85",
        ),
    ),
    whatIsIt = listOf(
        "Adjusted R² is 1 − (1 − R²)(n − 1)/(n − p − 1), where p is the number of predictors. The extra term is a penalty that grows with p, built specifically to counteract R²'s inability to ever fall when a column is added — plain R² treats every new predictor as free information, and this doesn't.",
        "On the lab's noise-column experiment — 44 points, one real predictor, then up to 20 columns of pure random numbers added one at a time — plain R² never falls: it climbs from 0.698 to 0.808 on columns that carry no information at all, because least squares can always bend a new column towards the noise. Adjusted R² on the identical fits starts at 0.690 and ends at 0.625, a net drop of 6.5 points across the run.",
        "The drop isn't monotone: adjusted R² ticks up on six of the twenty steps — the 3rd and 4th columns take it from 0.676 to 0.693, briefly above where it started, and the 18th lifts it from 0.643 to 0.656. That's not a bug in the correction. A noise column can, by chance, explain more variance than the penalty charges for it. The useful reading isn't \"always falls\"; it's that unlike plain R², adjusted R² is willing to fall, and net does, when the added columns aren't earning their penalty.",
    ),
    steps = listOf(
        StepCard(1, "Take R² and n, p", "The plain score, sample size, and predictor count.", 0xFF0EA5E9),
        StepCard(2, "Apply the Penalty Term", "(n − 1) / (n − p − 1) — grows as p grows.", 0xFF3B82F6),
        StepCard(3, "Compute Adjusted R²", "1 − (1 − R²) × penalty.", 0xFF8B5CF6),
        StepCard(4, "Add Noise Columns", "R² never falls: 0.698 → 0.808 over 20 noise columns.", 0xFFF59E0B),
        StepCard(5, "Watch Adjusted R² Instead", "Starts 0.690, ends 0.625 — net down, unlike R².", 0xFFEC4899),
        StepCard(6, "Notice the Exceptions", "6 of 20 steps tick up — a lucky noise column can out-earn its penalty.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Adjusted R²", "1 − (1 − R²)(n − 1)/(n − p − 1)", "p is the number of predictors."),
        FormulaEntry("R² across 20 noise columns", "0.698 → 0.808", "Never falls — every column is free to plain R²."),
        FormulaEntry("Adjusted R², same run", "0.690 → 0.625", "Net falling — a 6.5-point drop."),
        FormulaEntry("Upticks", "6 of 20 steps", "e.g. 0.676 → 0.684 → 0.693 at the 3rd and 4th noise columns."),
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
                r2_by_q = [0.6976, 0.6979, 0.6982, 0.7136, 0.7285, 0.7285, 0.7287, 0.7299, 0.7462, 0.7594, 0.7624,
                           0.7637, 0.7694, 0.7697, 0.7814, 0.7835, 0.7882, 0.7922, 0.8080, 0.8084, 0.8084]
                # q noise columns on top of the one real predictor, so p = q + 1
                adjusted = [adjusted_r2(r2, n, q + 1) for q, r2 in enumerate(r2_by_q)]
                print(f"{adjusted[0]:.3f} -> {adjusted[-1]:.3f}")      # 0.690 -> 0.625
                #
                # R^2 never falls across the 20 noise columns. Adjusted R^2 falls net, with six
                # steps rising against the trend, which the penalty term does not prevent; it
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
        "Adding 20 columns of pure noise never lowers plain R²: 0.698 → 0.808.",
        "Adjusted R² on the identical fits nets a 6.5-point drop over the same run: 0.690 → 0.625.",
        "It isn't monotone — 6 of the 20 steps rise, when a noise column happens to earn more than its penalty.",
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
