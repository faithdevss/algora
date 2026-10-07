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

internal val earlyStoppingContent = TopicContent(
    topicId = "early_stopping",
    figure = Figure(
        caption = "The page's lab: a linear model with 40 features fitted by gradient descent to 30 " +
            "training rows — more weights than equations, so it can fit the noise too — and scored " +
            "on 30 validation rows it never trains on. Training loss falls the whole way, 11.08 to " +
            "0.037 over 1,500 steps. Validation falls with it from 10.11 to its minimum, 4.89 at " +
            "step 583, and then climbs steadily to 5.47 at the end: from there on the model is " +
            "learning the training set's noise. Stopping at 583 keeps 11% of validation loss that " +
            "training to the end gives back. With the lab's default patience of 50, training halts " +
            "at step 633 — fifty steps without a new best — and restores the weights from 583, so " +
            "867 of the 1,500 steps are never run.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "validation",
                    listOf(
                        FigurePoint(0.000f, 0.911f), FigurePoint(0.033f, 0.684f), FigurePoint(0.067f, 0.601f),
                        FigurePoint(0.100f, 0.554f), FigurePoint(0.133f, 0.520f), FigurePoint(0.167f, 0.495f),
                        FigurePoint(0.200f, 0.476f), FigurePoint(0.233f, 0.462f), FigurePoint(0.267f, 0.452f),
                        FigurePoint(0.300f, 0.446f), FigurePoint(0.333f, 0.442f), FigurePoint(0.367f, 0.440f),
                        FigurePoint(0.400f, 0.440f), FigurePoint(0.433f, 0.441f), FigurePoint(0.467f, 0.443f),
                        FigurePoint(0.500f, 0.445f), FigurePoint(0.533f, 0.448f), FigurePoint(0.567f, 0.451f),
                        FigurePoint(0.600f, 0.454f), FigurePoint(0.633f, 0.457f), FigurePoint(0.667f, 0.461f),
                        FigurePoint(0.700f, 0.464f), FigurePoint(0.733f, 0.468f), FigurePoint(0.767f, 0.471f),
                        FigurePoint(0.800f, 0.474f), FigurePoint(0.833f, 0.478f), FigurePoint(0.867f, 0.481f),
                        FigurePoint(0.900f, 0.484f), FigurePoint(0.933f, 0.487f), FigurePoint(0.967f, 0.490f),
                        FigurePoint(1.000f, 0.493f),
                    ),
                    FigureTone.Primary,
                ),
                FigureSeries(
                    "training",
                    listOf(
                        FigurePoint(0.000f, 0.998f), FigurePoint(0.033f, 0.264f), FigurePoint(0.067f, 0.131f),
                        FigurePoint(0.100f, 0.085f), FigurePoint(0.133f, 0.062f), FigurePoint(0.167f, 0.048f),
                        FigurePoint(0.200f, 0.039f), FigurePoint(0.233f, 0.032f), FigurePoint(0.267f, 0.026f),
                        FigurePoint(0.300f, 0.022f), FigurePoint(0.333f, 0.019f), FigurePoint(0.367f, 0.017f),
                        FigurePoint(0.400f, 0.014f), FigurePoint(0.433f, 0.013f), FigurePoint(0.467f, 0.011f),
                        FigurePoint(0.500f, 0.010f), FigurePoint(0.533f, 0.009f), FigurePoint(0.567f, 0.008f),
                        FigurePoint(0.600f, 0.008f), FigurePoint(0.633f, 0.007f), FigurePoint(0.667f, 0.006f),
                        FigurePoint(0.700f, 0.006f), FigurePoint(0.733f, 0.005f), FigurePoint(0.767f, 0.005f),
                        FigurePoint(0.800f, 0.005f), FigurePoint(0.833f, 0.005f), FigurePoint(0.867f, 0.004f),
                        FigurePoint(0.900f, 0.004f), FigurePoint(0.933f, 0.004f), FigurePoint(0.967f, 0.004f),
                        FigurePoint(1.000f, 0.003f),
                    ),
                    FigureTone.Muted,
                    dashed = true,
                ),
            ),
            xLabel = "training step, 0 → 1,500",
            yLabel = "MSE, 0 → 11.1",
            markers = listOf(
                FigurePoint(0.389f, 0.440f, "val min 4.89, step 583", FigureTone.Accent),
                FigurePoint(1f, 0.493f, "final 5.47", FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Early stopping halts training at the point a held-out validation set says to, rather than running to a fixed epoch count. The lab makes the case with a model that can overfit on purpose: 40 features and only 30 training rows, so gradient descent has more weights than equations and will eventually fit the noise as well as the signal. Training loss falls for all 1,500 steps, from 11.08 to 0.037 — it always will, which is exactly why it cannot be the stopping signal.",
        "Validation loss, on 30 rows the model never updates on, tells the real story. It falls alongside training loss from 10.11 to a minimum of 4.89 at step 583, then climbs steadily to 5.47 by the end. Stopping at 583 keeps 11% of validation loss that training to completion gives back. In practice you do not know the minimum in advance, so you wait a set number of steps — the patience — for a new best: at the lab's default of 50, training halts at step 633 and restores the weights from 583; at 10 it halts at 593, at 200 at 783. Every setting restores the same weights here because this validation curve is smooth; on noisy curves a short patience stops on a blip.",
        "Validation loss is itself only an estimate of the thing early stopping protects — error on the function the data came from. The code example below measures that directly on a separate run, a degree-9 polynomial on 20 noisy points scored against the noise-free function: validation's minimum lands at step 120, true risk's at step 100, and by step 4,000 true risk is 37% worse than its floor while validation has moved by about 1%. The proxy's rough location is still worth far more than a training curve that promises more steps are always better.",
    ),
    steps = listOf(
        StepCard(1, "Split Off a Validation Set", "Held out from training, never used to update a single weight.", 0xFF64748B),
        StepCard(2, "Train and Score Every Few Steps", "Track training loss and validation loss on the same schedule.", 0xFF3B82F6),
        StepCard(3, "Watch Validation Loss, Not Training Loss", "Training loss falls the whole time — it always will.", 0xFFF59E0B),
        StepCard(4, "Stop at (or Near) Its Minimum", "Step 583 of 1,500 here; patience 50 halts at 633 and restores it.", 0xFF10B981),
        StepCard(5, "Check What You Actually Bought", "Validation 4.89 at step 583 against 5.47 at the end — 11% kept.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Training objective", "min_θ (1/n) Σ (f_θ(xᵢ) − yᵢ)²", "Minimized on the training split only."),
        FormulaEntry("Stopping rule", "t* = argmin_t L_val(θ_t)", "The step whose validation loss is lowest."),
        FormulaEntry("What you cannot see", "R_true(θ) = E[(f_θ(x) − f(x))²]", "Error against the clean function — validation only estimates it."),
        FormulaEntry("Measured in the lab", "4.89 (t=583) → 5.47 (t=1500)", "Validation loss at its minimum versus at the end of training — 11% kept by stopping."),
        FormulaEntry("Code example", "0.0047 (t=100) → 0.0064 (t=4000)", "True risk on the separate degree-9 run — 37% worse by the end."),
    ),
    notationKey = listOf(
        NotationEntry("L_val(θ_t)", "validation loss at training step t"),
        NotationEntry("t*", "the stopping step — where validation loss is minimized"),
        NotationEntry("R_true", "true risk — error against the underlying, noise-free function"),
        NotationEntry("burn-in", "the first few steps, before the model has learned anything useful yet"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A separate run, with the curve you cannot see",
            accentColor = 0xFF64748B,
            code = """
                import numpy as np

                # Degree-9 polynomial, standardized features, plain gradient descent.
                # Every 20 steps, three numbers are recorded from the same coefficients:
                for step in range(1, 4001):
                    coeffs -= learning_rate * gradient(coeffs, train_x, train_y_noisy)
                    if step % 20 == 0:
                        train_mse = mse(coeffs, train_x, train_y_noisy)          # keeps falling
                        val_mse   = mse(coeffs, val_x,   val_y_noisy)            # bottoms at step 120
                        true_risk = mse(coeffs, test_x,  test_y_clean)           # bottoms at step 100

                # step= 120  val=0.02061  true=0.00467  train=0.01110
                # step= 800  val=0.02080  true=0.00525  train=0.01061   <- val is noisy, not monotone
                # step=4000  val=0.02068  true=0.00639  train=0.01012   <- true risk: 37% worse than its floor
                #
                # Validation loss moves by about 1% across this whole stretch -- it would not look
                # dramatic on a chart. True risk against the function nobody gets to see moves by 37%.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF3B82F6, "Every Iterative Trainer", "Neural nets, gradient-boosted trees, anything fit step by step."),
        ApplicationCard("target", 0xFF10B981, "Cheap Regularization", "No penalty term, no architecture change — just a stopping rule."),
        ApplicationCard("crown", 0xFFF59E0B, "Paired With Checkpointing", "Save weights at the validation minimum, not just at the final step."),
        ApplicationCard("help", 0xFFEC4899, "The Honest Caveat", "Validation loss is an estimate; it can be noisy, and its minimum is not always the true one."),
    ),
    takeaways = listOf(
        "Training loss falls for the entire run — 11.08 to 0.037 — which is exactly why it cannot be the stopping signal.",
        "Validation loss bottoms at 4.89 at step 583 of 1,500, then climbs to 5.47 as the model fits the training noise.",
        "Stopping at the minimum keeps 11% of validation loss; patience 50 halts at step 633 and restores the step-583 weights.",
        "Patience trades compute for robustness: 10 halts at 593, 200 at 783, and on a noisy validation curve a short patience stops on a blip.",
        "Validation is an estimate of the thing that matters: on the code example's separate run, its minimum (step 120) sits near true risk's (step 100), and training on cost 37% in true risk.",
        "Early stopping is a bet that a noisy proxy's rough location beats training to completion — and here, measurably, it is a good bet.",
    ),
    crossLinks = listOf(
        CrossLink("data_augmentation", "Data Augmentation"),
        CrossLink("regularization", "L1 / L2 Regularization"),
        CrossLink("bias_variance", "Bias-Variance Tradeoff"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
