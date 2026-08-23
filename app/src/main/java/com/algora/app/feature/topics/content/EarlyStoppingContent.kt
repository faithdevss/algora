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
        caption = "One 4,000-step run of the lab's degree-9 fit, all three curves on one scale " +
            "(MSE ÷ 0.025) against a log step axis, because everything worth seeing happens in the " +
            "first 3% of training. Training loss falls the entire way — 0.01254 at step 20 to " +
            "0.01012 at step 4,000 — while true risk, measured against the clean function nobody " +
            "gets to see during training, bottoms out at step 100 and then climbs 37% above its " +
            "floor by the end. That divergence is overfitting, and it is the only place it is " +
            "visible. Validation loss is the proxy you actually have: its own minimum lands at " +
            "step 120, close to true risk's 100, and then it jitters within 0.3% of that minimum " +
            "for the remaining 3,880 steps. The bet early stopping makes is that a noisy estimate " +
            "of roughly the right place beats a clean training curve's promise that more is better.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "validation",
                    listOf(
                        FigurePoint(0.361f, 0.935f), FigurePoint(0.445f, 0.861f),
                        FigurePoint(0.494f, 0.836f), FigurePoint(0.555f, 0.825f),
                        FigurePoint(0.577f, 0.824f), FigurePoint(0.639f, 0.827f),
                        FigurePoint(0.722f, 0.830f), FigurePoint(0.806f, 0.832f),
                        FigurePoint(0.890f, 0.830f), FigurePoint(1f, 0.827f),
                    ),
                    FigureTone.Primary,
                ),
                FigureSeries(
                    "training",
                    listOf(
                        FigurePoint(0.361f, 0.502f), FigurePoint(0.445f, 0.463f),
                        FigurePoint(0.494f, 0.452f), FigurePoint(0.555f, 0.446f),
                        FigurePoint(0.577f, 0.444f), FigurePoint(0.639f, 0.440f),
                        FigurePoint(0.722f, 0.434f), FigurePoint(0.806f, 0.424f),
                        FigurePoint(0.890f, 0.414f), FigurePoint(1f, 0.405f),
                    ),
                    FigureTone.Muted,
                    dashed = true,
                ),
                FigureSeries(
                    "true risk",
                    listOf(
                        FigurePoint(0.361f, 0.257f), FigurePoint(0.445f, 0.206f),
                        FigurePoint(0.494f, 0.191f), FigurePoint(0.555f, 0.186f),
                        FigurePoint(0.577f, 0.187f), FigurePoint(0.639f, 0.191f),
                        FigurePoint(0.722f, 0.199f), FigurePoint(0.806f, 0.210f),
                        FigurePoint(0.890f, 0.226f), FigurePoint(1f, 0.256f),
                    ),
                    FigureTone.Warn,
                ),
            ),
            xLabel = "training step, log scale (20 → 4,000)",
            yLabel = "MSE",
            markers = listOf(
                FigurePoint(0.577f, 0.824f, "val min, step 120", FigureTone.Accent),
                FigurePoint(0.555f, 0.186f, "true min, step 100", FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Early stopping halts training at the point a held-out validation set says to, rather than running to a fixed epoch count. The usual picture is a clean U: validation loss falls, bottoms out, then rises as the model starts fitting noise the validation set does not share. Fit here for real — a degree-9 polynomial trained by gradient descent on 20 noisy points, watched against a 20-point validation set and scored, separately, against the noise-free function underneath both — the picture is messier and more informative than the textbook curve. Validation loss reaches its minimum at step 120 of a 4,000-step run, then wanders within about 0.3% of that minimum for the rest of training: it neither collapses nor recovers cleanly, it jitters, because 20 points is a small and noisy sample of the true generalization error.",
        "The true-risk curve — error against the clean function the training and validation noise were both added to, which no real deployment ever gets to measure directly but which is exactly what early stopping is trying to protect — tells a cleaner story: it falls from step 20 to a true minimum at step 100, then climbs monotonically for the rest of the run, ending 37% higher at step 4,000 than at its floor. Training loss, over the same stretch, keeps falling the entire time (0.0111 at step 120 down to 0.0101 at step 4,000) — the model is still improving on the data it can see while getting worse on the function that data was drawn from. That divergence, not any single number, is overfitting.",
        "Validation loss's own minimum (step 120) lands close to but not exactly at true risk's minimum (step 100) — it is an estimate of the quantity that actually matters, built from 20 noisy points, and estimates have their own noise. What makes it useful anyway is asymmetric: stopping a little early or a little late near a shallow validation minimum costs almost nothing, because true risk is nearly flat near its own floor, while training to the end costs a measured 37% in the metric nobody can directly observe during training. Early stopping is a bet that a noisy proxy's rough location is worth far more than a clean training curve's false promise that more steps are always better.",
    ),
    steps = listOf(
        StepCard(1, "Split Off a Validation Set", "Held out from training, never used to update a single weight.", 0xFF64748B),
        StepCard(2, "Train and Score Every Few Steps", "Track training loss and validation loss on the same schedule.", 0xFF3B82F6),
        StepCard(3, "Watch Validation Loss, Not Training Loss", "Training loss falls the whole time — it always will.", 0xFFF59E0B),
        StepCard(4, "Stop at (or Near) Its Minimum", "Step 120 of 4,000 here — a small fraction of the full budget.", 0xFF10B981),
        StepCard(5, "Check What You Actually Bought", "True risk at that point: 0.0047. At step 4,000: 0.0064 — 37% worse.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Training objective", "min_θ (1/n) Σ (f_θ(xᵢ) − yᵢ)²", "Minimized on the training split only."),
        FormulaEntry("Stopping rule", "t* = argmin_t L_val(θ_t)", "The step whose validation loss is lowest."),
        FormulaEntry("What you cannot see", "R_true(θ) = E[(f_θ(x) − f(x))²]", "Error against the clean function — validation only estimates it."),
        FormulaEntry("Measured", "0.0047 (t=100) → 0.0064 (t=4000)", "True risk's floor versus its value at the end of training — 37% worse."),
    ),
    notationKey = listOf(
        NotationEntry("L_val(θ_t)", "validation loss at training step t"),
        NotationEntry("t*", "the stopping step — where validation loss is minimized"),
        NotationEntry("R_true", "true risk — error against the underlying, noise-free function"),
        NotationEntry("burn-in", "the first few steps, before the model has learned anything useful yet"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Three curves, one training run",
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
                # Validation loss moves by about 0.3% across this whole stretch -- it would not look
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
        "Training loss falls for the entire run — 0.0111 to 0.0101 — which is exactly why it cannot be the stopping signal.",
        "Validation loss bottoms at step 120 of 4,000, then jitters within about 0.3% of that floor for the rest of training.",
        "True risk — error against the noise-free function, never directly observable during training — bottoms at step 100 and then climbs monotonically.",
        "By step 4,000, true risk is 37% worse than at its own floor, even though validation loss barely moved.",
        "The two minima (validation at 120, true risk at 100) are close but not identical — validation is an estimate of the thing that matters, not the thing itself.",
        "Early stopping is a bet that a noisy proxy's rough location beats training to completion — and here, measurably, it is a good bet.",
    ),
    crossLinks = listOf(
        CrossLink("data_augmentation", "Data Augmentation"),
        CrossLink("regularization", "L1 / L2 Regularization"),
        CrossLink("bias_variance", "Bias-Variance Tradeoff"),
        CrossLink("model_evaluation", "Model Evaluation"),
    ),
)
