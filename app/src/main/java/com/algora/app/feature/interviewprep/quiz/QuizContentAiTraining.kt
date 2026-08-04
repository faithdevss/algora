package com.algora.app.feature.interviewprep.quiz

import com.algora.app.core.data.model.Difficulty

// Third batch of AI-mode quizzes: optimizers/training and activation functions, both zero-coverage
// categories despite 18 combined topics — every other DL area (backprop, CNNs, RNNs) already had a
// question in deepLearningQuiz, these two never did.

private const val AI_TIME_LIMIT = 240

internal val optimizersTrainingQuiz = Quiz(
    id = "optimizers_training_quiz",
    title = "Optimizers & Training",
    description = "Momentum, adaptive rates, schedules and the losses they minimize.",
    questions = listOf(
        QuizQuestion(
            prompt = "Momentum in SGD helps mainly by:",
            options = listOf(
                "Accumulating a velocity term that smooths oscillation across ravines and speeds convergence",
                "Increasing the learning rate every step",
                "Reducing the number of parameters",
                "Removing the need for a loss function",
            ),
            correctIndex = 0,
            patternTag = "Momentum",
            difficulty = Difficulty.BEGINNER,
            explanation = "A velocity term exponentially averages past gradients, damping oscillation across steep, narrow ravines and carrying speed through flat regions plain SGD would crawl across.",
            linkedTopicId = "momentum",
            linkedTopicLabel = "Momentum",
        ),
        QuizQuestion(
            prompt = "AdaGrad's per-parameter learning rate eventually stalls training because it:",
            options = listOf(
                "Accumulates squared gradients monotonically, so the effective rate only shrinks and never recovers",
                "Resets to the initial rate every epoch",
                "Ignores sparse gradients entirely",
                "Requires a fixed batch size",
            ),
            correctIndex = 0,
            patternTag = "AdaGrad",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "The denominator sums squared gradients since step one, so it grows without bound and the effective rate decays toward zero — fine for sparse features early on, fatal for long training runs.",
            linkedTopicId = "adagrad",
            linkedTopicLabel = "AdaGrad",
        ),
        QuizQuestion(
            prompt = "RMSprop fixes AdaGrad's vanishing rate by:",
            options = listOf(
                "Replacing the cumulative sum with an exponential moving average of squared gradients",
                "Removing the square root from the update",
                "Freezing the learning rate after warmup",
                "Averaging gradients across parameters instead of time",
            ),
            correctIndex = 0,
            patternTag = "RMSprop",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "A moving average forgets old gradients, so the accumulator can shrink again as gradients shrink, keeping the effective rate from decaying to zero the way AdaGrad's running sum does.",
            linkedTopicId = "rmsprop",
            linkedTopicLabel = "RMSprop",
        ),
        QuizQuestion(
            prompt = "Adam's bias-correction terms (dividing by 1 - β^t) matter most:",
            options = listOf(
                "In the first few steps, when the moving averages are still biased toward zero",
                "Only after the learning rate has decayed to zero",
                "Only when the batch size is 1",
                "Never — they are a no-op given the default betas",
            ),
            correctIndex = 0,
            patternTag = "Adam",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "Both moment estimates are initialised at zero, so early updates are biased small; the correction divides that bias out. By later steps 1 - β^t → 1 and the correction fades on its own.",
            linkedTopicId = "adam",
            linkedTopicLabel = "Adam (Adaptive Moment Estimation)",
        ),
        QuizQuestion(
            prompt = "AdamW differs from Adam with L2 regularisation added to the loss because it:",
            options = listOf(
                "Decouples weight decay from the gradient, so it isn't scaled by Adam's adaptive per-parameter rate",
                "Removes momentum entirely",
                "Uses a larger default learning rate",
                "Only decays weights in the last layer",
            ),
            correctIndex = 0,
            patternTag = "AdamW",
            difficulty = Difficulty.ADVANCED,
            explanation = "L2-in-the-loss gets folded into the gradient and then divided by Adam's adaptive denominator, so parameters with large gradient history decay less. AdamW applies decay directly to the weights, outside that division.",
            linkedTopicId = "adamw",
            linkedTopicLabel = "AdamW (Decoupled Weight Decay)",
        ),
        QuizQuestion(
            prompt = "For softmax + cross-entropy, the gradient of the loss with respect to the logits simplifies to:",
            options = listOf(
                "Predicted probabilities minus the one-hot label",
                "The label minus the prediction, squared",
                "The log of the predicted probability",
                "Zero at every logit except the true class",
            ),
            correctIndex = 0,
            patternTag = "Loss Functions",
            difficulty = Difficulty.ADVANCED,
            explanation = "Softmax's Jacobian and cross-entropy's log cancel almost entirely, leaving p - y at each logit — the exact reason the pairing is used instead of, say, softmax with MSE.",
            linkedTopicId = "cross_entropy_loss",
            linkedTopicLabel = "Cross-Entropy Loss",
        ),
    ),
)

internal val activationFunctionsQuiz = Quiz(
    id = "activation_functions_quiz",
    title = "Activation Functions",
    description = "ReLU, sigmoid, GELU and the saturation, dead-unit trade-offs.",
    questions = listOf(
        QuizQuestion(
            prompt = "Sigmoid's derivative is at most 0.25, which means:",
            options = listOf(
                "Stacking many sigmoid layers multiplies gradients below 1 repeatedly, vanishing in deep networks",
                "Sigmoid outputs can exceed 1",
                "Sigmoid is unbounded",
                "The gradient is constant regardless of input",
            ),
            correctIndex = 0,
            patternTag = "Sigmoid",
            difficulty = Difficulty.BEGINNER,
            explanation = "σ'(x) peaks at 0.25 when x = 0 and shrinks toward both tails. Chain-ruling through many such layers multiplies several numbers under 1, so early-layer gradients vanish — the classic reason deep nets avoided sigmoid hidden layers.",
            linkedTopicId = "sigmoid",
            linkedTopicLabel = "Sigmoid",
        ),
        QuizQuestion(
            prompt = "Tanh is generally preferred over sigmoid in hidden layers because it:",
            options = listOf(
                "Is zero-centred, so gradients on the next layer's weights aren't all pushed the same sign",
                "Has no saturating regions",
                "Is cheaper to compute",
                "Outputs only positive values",
            ),
            correctIndex = 0,
            patternTag = "Tanh",
            difficulty = Difficulty.BEGINNER,
            explanation = "Sigmoid's always-positive output means every weight gradient in the next layer shares the same sign, forcing a zig-zag update path. Tanh's zero-centred range avoids that, though it still saturates at both ends.",
            linkedTopicId = "tanh",
            linkedTopicLabel = "Tanh (Hyperbolic Tangent)",
        ),
        QuizQuestion(
            prompt = "A 'dead' ReLU unit is one that:",
            options = listOf(
                "Outputs zero for every input in the training set, so its gradient is permanently zero",
                "Has a negative weight",
                "Only activates for negative inputs",
                "Has been pruned from the network",
            ),
            correctIndex = 0,
            patternTag = "ReLU",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "If a large gradient step pushes a unit's pre-activation negative for every training example, ReLU's flat zero region on that side means the gradient is exactly zero and no update can ever revive it.",
            linkedTopicId = "relu",
            linkedTopicLabel = "ReLU (Rectified Linear Unit)",
        ),
        QuizQuestion(
            prompt = "Leaky ReLU addresses dying units by:",
            options = listOf(
                "Giving negative inputs a small non-zero slope instead of flattening to exactly zero",
                "Clipping positive inputs at 1",
                "Adding a learnable bias to every neuron",
                "Replacing the negative branch with an exponential",
            ),
            correctIndex = 0,
            patternTag = "Leaky ReLU",
            difficulty = Difficulty.BEGINNER,
            explanation = "A small fixed slope (e.g. 0.01) on the negative side keeps the gradient non-zero there, so a unit pushed negative can still receive updates and recover. PReLU makes that slope learnable instead of fixed.",
            linkedTopicId = "leaky_relu",
            linkedTopicLabel = "Leaky ReLU",
        ),
        QuizQuestion(
            prompt = "GELU differs from ReLU mainly in that it:",
            options = listOf(
                "Weights each input by its probability under a Gaussian, giving a smooth, non-monotone curve near zero",
                "Is a hard threshold at zero",
                "Has no negative outputs at all",
                "Requires no learned parameters, unlike ReLU",
            ),
            correctIndex = 0,
            patternTag = "GELU",
            difficulty = Difficulty.INTERMEDIATE,
            explanation = "GELU multiplies the input by the standard normal CDF evaluated at that input, so small negative values pass through partially rather than being hard-zeroed — the smooth curve transformers default to.",
            linkedTopicId = "gelu",
            linkedTopicLabel = "GELU (Gaussian Error Linear Unit)",
        ),
        QuizQuestion(
            prompt = "Unlike ReLU or sigmoid, softmax needs a full Jacobian matrix (not a single derivative) for backprop because:",
            options = listOf(
                "Each output depends on every input logit, not just the corresponding one",
                "Softmax has no derivative",
                "Softmax is applied before the loss, never after",
                "It has more parameters than other activations",
            ),
            correctIndex = 0,
            patternTag = "Softmax",
            difficulty = Difficulty.ADVANCED,
            explanation = "Every softmax output is normalised by the sum over all logits, so changing one logit shifts every output, not just its own — the gradient is an n×n matrix, though it collapses to a simple form when paired with cross-entropy.",
            linkedTopicId = "softmax",
            linkedTopicLabel = "Softmax (Output Layer)",
        ),
    ),
)
