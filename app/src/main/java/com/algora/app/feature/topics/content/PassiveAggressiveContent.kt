package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val passiveAggressiveContent = TopicContent(
    topicId = "passive_aggressive",
    whatIsIt = listOf(
        "Passive-Aggressive is an online classifier whose name is a literal description of its update rule. On an example it already classifies correctly with enough margin, it is passive: the weights do not move at all. On an example it gets wrong, it is aggressive: the weights move exactly as far as needed to classify that example correctly with margin 1, and no further.",
        "Both halves come from one constrained optimization. Each step asks for the smallest change to the current weights that satisfies the hinge constraint on the current example — minimize ‖w − wₜ‖² subject to loss = 0. That has a closed-form solution, τ = loss/‖x‖², so there is no learning rate to tune. Contrast this with the perceptron, which moves a fixed amount on every mistake regardless of how badly it missed, and with SGD, whose step size is a schedule you have to choose.",
        "The unbounded version is dangerous for the same reason it is elegant. Because it insists on fixing the current example completely, a single mislabelled point can force an arbitrarily large step and destroy a boundary that was already good — the simulation on this page has one deliberately flipped label so you can watch it happen. PA-I caps τ at C, and PA-II softens it by adding 1/2C to the denominator so it shrinks smoothly rather than clipping. Both bound how much damage one bad example can do, and in any setting with noisy labels one of them is what you actually want.",
    ),
    steps = listOf(
        StepCard(1, "Receive One Example", "Online: examples arrive one at a time, and each is seen once.", 0xFF8B5CF6),
        StepCard(2, "Compute the Hinge Loss", "max(0, 1 − y·(wᵀx)). Zero means correct with margin to spare.", 0xFF818CF8),
        StepCard(3, "Be Passive at Zero Loss", "τ = 0 and the weights do not move. Nothing to learn from this one.", 0xFF60A5FA),
        StepCard(4, "Be Aggressive Otherwise", "Take the smallest step that makes the loss zero: τ = loss/‖x‖².", 0xFF10B981),
        StepCard(5, "Note the Missing Hyperparameter", "The step size is derived, not scheduled. There is no learning rate.", 0xFFF59E0B),
        StepCard(6, "Bound the Step", "PA-I caps τ at C; PA-II damps it. Either one contains the damage from a bad label.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Update problem", "wₜ₊₁ = argmin ½‖w − wₜ‖² s.t. ℓ(w) = 0", "Smallest change that fixes this example."),
        FormulaEntry("Hinge loss", "ℓ = max(0, 1 − y(wᵀx))", "Zero outside the margin."),
        FormulaEntry("Step (hard)", "τ = ℓ / ‖x‖²", "Closed form — no learning rate."),
        FormulaEntry("PA-I", "τ = min(C, ℓ/‖x‖²)", "Hard cap on the step size."),
        FormulaEntry("PA-II", "τ = ℓ / (‖x‖² + 1/2C)", "Smooth damping instead of a cap."),
        FormulaEntry("Update", "w ← w + τ·y·x", "Applied only when ℓ > 0."),
    ),
    notationKey = listOf(
        NotationEntry("τ", "step size, computed per example"),
        NotationEntry("C", "aggressiveness — the cap in PA-I, the damping in PA-II"),
        NotationEntry("online learning", "one example at a time, no stored dataset"),
        NotationEntry("passive", "zero loss, zero update"),
        NotationEntry("regret bound", "worst-case gap against the best fixed weights in hindsight"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The whole algorithm",
            accentColor = 0xFF8B5CF6,
            code = """
                import numpy as np

                def passive_aggressive(stream, variant="PA-I", C=1.0, dim=2):
                    w = np.zeros(dim + 1)                    # last slot is the bias
                    for x_raw, y in stream:                  # y in {-1, +1}
                        x = np.append(x_raw, 1.0)
                        loss = max(0.0, 1.0 - y * (w @ x))

                        if loss == 0.0:
                            continue                         # PASSIVE: no update at all

                        norm_sq = x @ x
                        if variant == "hard":
                            tau = loss / norm_sq             # unbounded — dangerous on noise
                        elif variant == "PA-I":
                            tau = min(C, loss / norm_sq)     # capped
                        else:                                # PA-II
                            tau = loss / (norm_sq + 1.0 / (2.0 * C))

                        w += tau * y * x                     # AGGRESSIVE
                    return w

                # Note what is absent: a learning rate, an epoch count, and the dataset. Each
                # example is consumed once and discarded.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Against the perceptron, on the same stream",
            accentColor = 0xFF10B981,
            code = """
                from sklearn.linear_model import PassiveAggressiveClassifier, Perceptron

                # partial_fit is the point: this is a model you can keep training forever as
                # data arrives, without ever holding the history in memory.
                pa = PassiveAggressiveClassifier(C=0.1, loss="hinge")     # PA-I
                for X_batch, y_batch in stream_batches():
                    pa.partial_fit(X_batch, y_batch, classes=[0, 1])

                # The perceptron's update is w += y*x — a fixed step, regardless of whether
                # the example was missed by a hair or by a mile. PA scales the step to the
                # size of the mistake, which is why it converges faster on separable data.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DecisionSurface,
    applications = listOf(
        ApplicationCard("globe", 0xFF8B5CF6, "Streaming Classification", "Spam and abuse filters that must adapt continuously without retraining from scratch."),
        ApplicationCard("chip", 0xFF818CF8, "Memory-Bounded Learning", "Datasets too large to hold at once — each example is consumed once and dropped."),
        ApplicationCard("trend", 0xFF10B981, "Concept Drift", "When the target distribution moves, an online learner tracks it where a batch model goes stale."),
    ),
    takeaways = listOf(
        "Passive on correctly-classified examples, aggressive enough to exactly fix the ones it misses.",
        "The step size is the closed-form solution to a constrained problem, so there is no learning rate.",
        "Unbounded PA lets one mislabelled example destroy a good boundary.",
        "PA-I caps the step and PA-II damps it — either is what you want with noisy labels.",
    ),
    crossLinks = listOf(
        CrossLink("perceptron", "The Perceptron"),
        CrossLink("svm", "Support Vector Machines (Linear)"),
        CrossLink("gradient_descent_variants", "Gradient Descent Variants"),
        CrossLink("logistic_regression", "Logistic Regression"),
    ),
)
