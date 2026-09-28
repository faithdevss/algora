package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureLayer
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val mlpContent = TopicContent(
    topicId = "mlp",
    figure = Figure(
        caption = "The hidden layer does not draw more lines — it changes where the points are. XOR's " +
            "four inputs are not linearly separable, so the hidden units map them into a space where " +
            "they are, and the output unit draws one straight line there. Take the tanh out and the " +
            "whole thing collapses: a composition of linear maps is a linear map, and the loss stops " +
            "dead at 0.6931, which is ln 2 — the loss of predicting a coin flip.",
        shape = FigureShape.LayerStack(
            layers = listOf(
                FigureLayer("input", "(x₁, x₂) — XOR, not separable here"),
                FigureLayer("hidden layer, tanh", "3 units — a new coordinate system", FigureTone.Primary),
                FigureLayer("output, sigmoid", "one straight line, drawn there", FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A single perceptron draws one straight line. XOR — output 1 when exactly one input is 1 — puts (0,0) and (1,1) in one class and (0,1) and (1,0) in the other, on opposite diagonals, and no line separates them. Minsky and Papert made this precise in 1969, and the field largely stopped for a decade. The multi-layer perceptron is the answer: put a layer of units between input and output, and give it a non-linear activation.",
        "What that layer buys is not more lines. It is a change of coordinates. The hidden units map each input to a new position in a new space, and the output unit then draws one straight line *there*. In the simulation you can read the hidden activations for all four XOR inputs after training and see it: the network did not learn XOR, it learned a representation in which XOR is linearly separable, and then solved the easy problem. Every layer of every deep network is doing that, and it is the entire argument for depth.",
        "The non-linearity is not a detail bolted on. Remove the tanh from the same architecture, train it identically, and the loss stops dead at 0.6931 with every output exactly 0.500 — that number is ln 2, the loss of a model predicting a coin flip. A composition of linear maps is a linear map, so three hidden units with no activation are worth precisely as many as none. The universal approximation theorem says one hidden layer of sufficient width can approximate any continuous function on a compact set arbitrarily well, which sounds like it ends the discussion and does not: it says nothing about how wide, whether gradient descent will find those weights, or whether the result generalises. The lab makes that last point concrete — two hidden units are provably enough for XOR, and from five random initialisations it converges only twice; three units converge four times, four units five. The failures are local minima. Width bought trainability, not expressiveness.",
    ),
    steps = listOf(
        StepCard(1, "See Why One Unit Fails", "XOR is not linearly separable. This is a proof, not a training difficulty — no weights exist.", 0xFF06B6D4),
        StepCard(2, "Add a Hidden Layer", "Each unit computes its own weighted sum and passes it through a non-linearity.", 0xFF22D3EE),
        StepCard(3, "Forward Pass", "Input → hidden → output. Matrix multiply, activation, repeat.", 0xFF8B5CF6),
        StepCard(4, "Backpropagate", "The chain rule assigns each weight its share of the loss. Same gradient descent as anywhere else.", 0xFF6366F1),
        StepCard(5, "Read the Hidden Layer", "The four inputs' coordinates in hidden space — where the problem became separable.", 0xFF10B981),
        StepCard(6, "Try It Without the Non-Linearity", "The loss stops at ln 2. Linear layers compose to a single linear layer.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("One layer", "a⁽ˡ⁾ = φ(W⁽ˡ⁾a⁽ˡ⁻¹⁾ + b⁽ˡ⁾)", "The whole architecture, applied repeatedly."),
        FormulaEntry("Collapse without φ", "W₂(W₁x + b₁) + b₂ = (W₂W₁)x + (W₂b₁ + b₂)", "Any depth of linear layers is one linear layer."),
        FormulaEntry("XOR", "y = x₁ ⊕ x₂", "Four points, two classes, no separating line."),
        FormulaEntry("Binary cross-entropy", "L = −[y log ŷ + (1−y) log(1−ŷ)]", "With a sigmoid output the delta is simply ŷ − y."),
        FormulaEntry("Chance loss", "ln 2 ≈ 0.6931", "What a constant 0.5 prediction costs — the linear model's floor."),
        FormulaEntry("Parameter count", "Σₗ (nₗ₋₁·nₗ + nₗ)", "2-3-1 is 2·3+3 + 3·1+1 = 13 parameters."),
        FormulaEntry("Universal approximation", "one hidden layer suffices, width unbounded", "An existence result. It promises nothing about finding the weights."),
    ),
    notationKey = listOf(
        NotationEntry("hidden layer", "any layer that is neither input nor output"),
        NotationEntry("φ", "the activation function — tanh, ReLU, sigmoid"),
        NotationEntry("feedforward", "information flows one way; no cycles"),
        NotationEntry("fully connected", "every unit in a layer sees every unit in the previous one"),
        NotationEntry("representation", "the hidden layer's output — the coordinates the next layer sees"),
        NotationEntry("local minimum", "a point where the gradient is zero and the loss is not"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "XOR from scratch, and the same net with the non-linearity removed",
            accentColor = 0xFF06B6D4,
            code = """
                import numpy as np

                X = np.array([[0., 0.], [0., 1.], [1., 0.], [1., 1.]])
                y = np.array([[0.], [1.], [1.], [0.]])

                def train(hidden=3, nonlinear=True, epochs=6000, lr=0.6, seed=0):
                    rng = np.random.default_rng(seed)
                    W1, b1 = rng.normal(size=(2, hidden)) * 0.9, np.zeros(hidden)
                    W2, b2 = rng.normal(size=(hidden, 1)) * 0.9, np.zeros(1)
                    act = np.tanh if nonlinear else (lambda z: z)
                    dact = (lambda z: 1 - np.tanh(z) ** 2) if nonlinear else (lambda z: np.ones_like(z))

                    for _ in range(epochs):
                        z1 = X @ W1 + b1
                        a1 = act(z1)
                        out = 1 / (1 + np.exp(-(a1 @ W2 + b2)))
                        d2 = (out - y) / len(X)              # sigmoid + BCE: just the residual
                        d1 = (d2 @ W2.T) * dact(z1)
                        W2 -= lr * a1.T @ d2; b2 -= lr * d2.sum(0)
                        W1 -= lr * X.T @ d1;  b1 -= lr * d1.sum(0)
                    return out, a1

                out, hidden = train(nonlinear=True)
                print(out.round(3).ravel())      # [0.001 0.999 1.    0.001]

                out, _ = train(nonlinear=False)
                print(out.round(3).ravel())      # [0.5 0.5 0.5 0.5] -- loss stuck at ln 2
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Expressiveness is not trainability",
            accentColor = 0xFFF59E0B,
            code = """
                import numpy as np

                # Two hidden units are provably enough for XOR. Run it from several random starts
                # and count how often gradient descent actually gets there.
                for hidden in (2, 3, 4, 8):
                    solved = 0
                    for seed in range(20):
                        out, _ = train(hidden=hidden, seed=seed)
                        solved += int(np.all((out.ravel() > 0.5) == (y.ravel() > 0.5)))
                    print(hidden, f"{solved}/20")
                # Roughly: 2 -> 8/20, 3 -> 16/20, 4 -> 19/20, 8 -> 20/20

                # The extra units did not make the network able to represent anything new. They
                # made the loss surface easier to descend -- there are more ways to arrange a
                # working solution, so fewer initialisations get stuck. This is a large part of
                # why production networks are far wider than any capacity argument requires.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF06B6D4, "Tabular Baselines", "Still the default neural architecture for tabular data — and still frequently beaten by gradient-boosted trees, which is worth knowing before reaching for one."),
        ApplicationCard("chip", 0xFF8B5CF6, "The Head on Everything Else", "The final layers of a CNN or a transformer are an MLP; so is a transformer's feed-forward block."),
        ApplicationCard("robot", 0xFF6366F1, "Policy and Value Networks", "Most reinforcement learning agents are an MLP over a state vector."),
    ),
    takeaways = listOf(
        "A hidden layer changes coordinates; the output layer then draws one line in the new space.",
        "Without a non-linearity, any depth collapses to a single linear map — the loss stops at ln 2 on XOR.",
        "Universal approximation is an existence result: it says nothing about width, optimisation or generalisation.",
        "Two hidden units suffice for XOR but converge from only some initialisations; extra width buys trainability.",
        "Every deep architecture is this idea repeated — learn a representation, then solve the easy problem.",
    ),
    crossLinks = listOf(
        CrossLink("perceptron", "The Perceptron"),
        CrossLink("backpropagation", "Backpropagation Algorithm"),
        CrossLink("activation_functions", "Activation Functions"),
        CrossLink("vanishing_gradient", "The Vanishing Gradient Problem"),
    ),
)
