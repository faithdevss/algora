package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val monteCarloMethodContent = TopicContent(
    topicId = "monte_carlo_method",
    figure = Figure(
        caption = "Sixteen samples in the unit square, thirteen of them inside the quarter circle, so " +
            "π ≈ 4 × 13/16 = 3.25. The estimate converges as 1/√N, which is both the property that " +
            "matters and the one that disappoints: a hundred times more samples buys one more decimal " +
            "digit, so six-figure accuracy is out of reach by this route. What makes it worth using " +
            "anyway is that the rate does not depend on the number of dimensions. Grid-based numerical " +
            "integration costs exponentially more per dimension added and Monte Carlo costs the same, " +
            "which is why it is the standard tool for high-dimensional integrals in physics and " +
            "finance — and why it is a poor choice for the one-dimensional ones a quadrature rule " +
            "would nail exactly. It is also why the answer is a confidence interval rather than a " +
            "number, and why a run is only reproducible if the seed is recorded.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("•", "•", "·", "·"),
                listOf("•", "•", "•", "·"),
                listOf("•", "•", "•", "•"),
                listOf("•", "•", "•", "•"),
            ),
            marks = listOf(
                FigureCell(0, 2, FigureTone.Warn),
                FigureCell(0, 3, FigureTone.Warn),
                FigureCell(1, 3, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "The Monte Carlo method estimates a numeric answer by running many random trials and averaging the outcomes — trading exactness for a probabilistic approximation.",
        "It shines when a problem is too complex to solve analytically but easy to sample: the more random draws you take, the tighter the estimate converges.",
    ),
    steps = listOf(
        StepCard(1, "Model as Random Trials", "Frame the quantity as the expected outcome of a random experiment.", 0xFF10B981),
        StepCard(2, "Sample Repeatedly", "Draw many independent random inputs and evaluate each.", 0xFF3B82F6),
        StepCard(3, "Aggregate", "Average the results (or count successes) to form the estimate.", 0xFFF59E0B),
        StepCard(4, "Converge with N", "Error shrinks as 1/√N — quadrupling the samples halves the error.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Estimate", "≈ (1/N) · Σ f(xᵢ)", "Sample mean approximates the true expectation."),
        FormulaEntry("Error", "O(1/√N)", "Independent of dimension — its key advantage over grids."),
        FormulaEntry("π example", "4 · (inside circle / total)", "Fraction of random points in the unit circle."),
    ),
    notationKey = listOf(
        NotationEntry("N", "number of random trials"),
        NotationEntry("f(x)", "the quantity evaluated per sample"),
        NotationEntry("1/√N", "convergence rate of the estimate"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Estimating π by sampling",
            accentColor = 0xFF6366F1,
            code = """
                fun estimatePi(samples: Int): Double {
                    var inside = 0
                    repeat(samples) {
                        val x = Math.random()
                        val y = Math.random()
                        if (x * x + y * y <= 1.0) inside++   // inside the unit quarter-circle
                    }
                    return 4.0 * inside / samples
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PointCloudPlayer,
    applications = listOf(
        ApplicationCard("finance", 0xFF10B981, "Risk & Pricing", "Options pricing and portfolio risk simulate thousands of market paths to estimate outcomes."),
        ApplicationCard("game", 0xFF3B82F6, "Game AI (MCTS)", "Monte Carlo Tree Search powers strong Go and board-game engines by sampling playouts."),
        ApplicationCard("flask", 0xFFF59E0B, "Physics & Integration", "High-dimensional integrals and particle simulations rely on Monte Carlo sampling."),
    ),
    takeaways = listOf(
        "Monte Carlo approximates hard quantities by averaging many random trials.",
        "Its error falls as 1/√N, independent of dimensionality — a huge win in high dimensions.",
        "It gives approximate answers, so accuracy is bought with more samples.",
        "Contrast with Las Vegas algorithms, which are always correct but have random runtime.",
    ),
    crossLinks = listOf(
        CrossLink("reservoir_sampling", "Reservoir Sampling"),
        CrossLink("linear_regression", "Linear Regression"),
    ),
)
