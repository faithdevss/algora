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

internal val muZeroContent = TopicContent(
    topicId = "muzero",
    figure = Figure(
        caption = "MuZero's learned model, as the page's lab unrolls it. A representation function " +
            "turns the board into a hidden state h₀; a dynamics function takes a hidden state and an " +
            "action and returns the next hidden state and a reward; a prediction function reads a " +
            "policy and a value off any hidden state. Search runs entirely on these learned states — " +
            "no board is ever reconstructed and no rules are consulted — so each state only has to " +
            "predict what the search will ask for. The lab tests how forgiving that is: 40 searches " +
            "of 120 simulations each, every value drawn from a deliberately imperfect model, and with " +
            "model errors of 0%, 10% and 30% the search picks the best move 100% of the time. That " +
            "tolerance is what let MuZero match AlphaZero without the rules and extend to Atari.",
        shape = FigureShape.LayerStack(
            layers = listOf(
                FigureLayer("observation", "the board", FigureTone.Muted),
                FigureLayer("h₀ = repr(obs)", "hidden state", FigureTone.Primary),
                FigureLayer("h₁, r = dyn(h₀, a)", "imagined step", FigureTone.Accent),
                FigureLayer("p, v = pred(h)", "what search needs", FigureTone.Primary),
            ),
            horizontal = true,
        ),
    ),
    whatIsIt = listOf(
        "MuZero searches without being told the rules. AlphaZero needs a simulator to know what each move does; MuZero learns a model instead — but not a model of the board. It learns three functions: a representation that turns the observation into a hidden state, a dynamics function that maps a hidden state and an action to the next hidden state and a reward, and a prediction function that outputs a policy and value from any hidden state. Search runs entirely on those learned states.",
        "The hidden state never has to reconstruct the board; it only has to be good enough to predict what the search will ask for — reward, value and policy. The lab tests how much error search tolerates: 40 searches of 120 simulations each, with every value coming from a deliberately imperfect model. With a model error rate of 0%, 10% or even 30%, search still picks the best move 100% of the time on this position — the many simulations average out much of the model's error.",
        "That robustness is why the approach scales. MuZero matched AlphaZero on Go, chess and shogi without the rules, and extended to Atari, where there are no rules to search with at all. The lesson it shares with Dreamer is that a model for planning needs to be accurate about what matters for decisions, not about every pixel.",
    ),
    steps = listOf(
        StepCard(1, "Representation", "Encode the observation history into an initial latent state.", 0xFF818CF8),
        StepCard(2, "Dynamics", "A learned model predicts the next latent state and reward for a hypothetical action.", 0xFF60A5FA),
        StepCard(3, "Prediction", "From any latent state, output a policy and a value.", 0xFF10B981),
        StepCard(4, "Plan in Latent Space", "Run MCTS entirely through these learned functions — no real simulator needed.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Three functions", "h (encode), g (dynamics), f (predict)", "Learned model components."),
        FormulaEntry("Latent rollout", "sᵏ, rᵏ = g(sᵏ⁻¹, aᵏ)", "Imagined transitions in latent space."),
        FormulaEntry("Trained to match", "policy, value, reward", "Only decision-relevant quantities."),
    ),
    notationKey = listOf(
        NotationEntry("h, g, f", "representation, dynamics, prediction networks"),
        NotationEntry("latent state", "abstract internal state used for planning"),
        NotationEntry("learned model", "dynamics inferred from experience"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "MuZero's learned model (structure)",
            accentColor = 0xFF6366F1,
            code = """
                s0        = h(observation_history)   # representation
                p0, v0    = f(s0)                     # prediction (policy, value)
                s1, r1    = g(s0, action)             # dynamics (next latent, reward)
                # MCTS unrolls g/f in latent space — the true rules are never given.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GameSearchPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Games + Atari", "Mastered Go, chess, shogi, and Atari with one algorithm, no rules given."),
        ApplicationCard("chip", 0xFF60A5FA, "Real-World Control", "Applied to video compression and other systems where dynamics are unknown."),
        ApplicationCard("bulb", 0xFF10B981, "Model Learning", "Shows planning works on a learned, decision-focused model."),
    ),
    takeaways = listOf(
        "MuZero learns its own dynamics model and plans without knowing the rules.",
        "It plans in latent space, predicting only reward, value, and policy.",
        "It generalizes AlphaZero to environments with unknown dynamics, including Atari.",
        "Not reconstructing full observations is what keeps its model efficient.",
    ),
    crossLinks = listOf(
        CrossLink("alphazero", "AlphaZero"),
        CrossLink("world_models", "World Models"),
    ),
)
