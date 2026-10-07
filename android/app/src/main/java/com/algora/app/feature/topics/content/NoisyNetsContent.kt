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

internal val noisyNetsContent = TopicContent(
    topicId = "noisy_nets",
    figure = Figure(
        caption = "The page's lab: visits per episode to each state of a chain whose goal is past s5, " +
            "starting at s0 with a 100-step limit, over 2,000 episodes. ε-greedy flips a fresh coin " +
            "every step, so random moves cancel and it lingers near the start, thinning out down " +
            "the chain (11.6 visits to s0, 1.9 to s5). It does get there — 94% of episodes — but takes " +
            "35.9 steps on average. A noisy net draws its weights once per episode, so whatever " +
            "direction the noise favours, it favours for the whole episode. When that is right it " +
            "marches straight through in 6.0 steps; when it is left it sits pressed against the " +
            "start (55.1 visits to s0) and only 33% of episodes arrive. Per-step noise explores in " +
            "place, per-episode noise explores in a direction — and because σ is learned, the " +
            "network can shrink the noise where committing stops paying.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("11.6", "55.1"),
                listOf("9.6", "5.5"),
                listOf("7.6", "3.0"),
                listOf("5.6", "2.1"),
                listOf("3.7", "1.8"),
                listOf("1.9", "1.0"),
                listOf("94% · 35.9 st", "33% · 6.0 st"),
            ),
            rowHeaders = listOf("s0", "s1", "s2", "s3", "s4", "s5", "reached"),
            colHeaders = listOf("ε-greedy", "noisy net"),
            marks = listOf(
                FigureCell(0, 1, FigureTone.Warn),
                FigureCell(6, 0, FigureTone.Primary),
                FigureCell(6, 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Noisy Nets move exploration into the network's weights. Each weight becomes w = μ + σ ⊙ ε — a learned mean, a learned noise scale, and fresh random noise — so the agent explores by acting greedily with respect to a randomly perturbed network. Because σ is trained by backpropagation like every other parameter, the network learns how much to explore and where, and there is no ε schedule to hand-tune.",
        "The lab shows the qualitative difference from ε-greedy on a chain where the goal is past s5, starting at s0 with a 100-step limit, over 2,000 episodes each. ε-greedy flips a fresh coin every step, so its random moves cancel out: it spends most of its time near the start (11.6 visits to s0 per episode, 1.9 to s5), reaches the goal in 94% of episodes, and takes 35.9 steps when it does. The noisy net draws its weights once per episode, so the perturbation points one way for the whole episode: when the draw favours right it marches straight through in 6.0 steps; when it favours left it stays pinned at the start (55.1 visits to s0) and never arrives — 33% of episodes reach the goal.",
        "That is the trade in one experiment: per-step noise explores in place, per-episode noise explores in a direction. A consistent push is what crosses long chains and deep mazes that dithering never reaches, at the cost of whole episodes spent committed to the wrong idea — which is why the noise scale being learnable matters, since σ shrinks where exploring stops paying. Noisy layers are one of the six ingredients of Rainbow DQN.",
    ),
    steps = listOf(
        StepCard(1, "Noisy Linear Layers", "Replace weights w with w = μ + σ⊙ε, where ε is random noise.", 0xFF818CF8),
        StepCard(2, "Learn the Noise Scale", "μ and σ are trainable; σ controls how much randomness each weight injects.", 0xFF60A5FA),
        StepCard(3, "State-Dependent Exploration", "The induced action randomness varies by state, not a global ε.", 0xFF10B981),
        StepCard(4, "Auto-Anneal", "As learning progresses, σ shrinks where the agent is confident, reducing exploration.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Noisy weight", "w = μ_w + σ_w ⊙ ε_w", "Mean plus scaled noise, per weight."),
        FormulaEntry("Learned exploration", "σ trained by backprop", "No ε schedule to tune."),
        FormulaEntry("Factorized noise", "ε = f(εᵢ)·f(εⱼ)", "Cheap noise generation for large layers."),
    ),
    notationKey = listOf(
        NotationEntry("μ, σ", "learnable mean and noise-scale parameters"),
        NotationEntry("ε", "sampled noise (reset per forward pass)"),
        NotationEntry("⊙", "element-wise product"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Noisy linear layer (forward)",
            accentColor = 0xFF6366F1,
            code = """
                # w = mu + sigma * eps ; eps resampled each forward pass.
                def forward(self, x):
                    w = self.weight_mu + self.weight_sigma * self.weight_eps
                    b = self.bias_mu + self.bias_sigma * self.bias_eps
                    return F.linear(x, w, b)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlTrainingPlayer,
    applications = listOf(
        ApplicationCard("bulb", 0xFF818CF8, "Learned Exploration", "Removes the need to hand-design ε-greedy schedules."),
        ApplicationCard("chip", 0xFF60A5FA, "Rainbow Component", "Supplies the exploration mechanism in Rainbow DQN."),
        ApplicationCard("robot", 0xFF10B981, "Hard-Exploration Tasks", "State-dependent noise explores more where the agent is uncertain."),
    ),
    takeaways = listOf(
        "Noisy Nets make exploration a learned, parametric part of the network.",
        "Weight noise w = μ + σ⊙ε replaces external ε-greedy randomness.",
        "The noise scale auto-anneals as the policy becomes confident.",
        "It's the exploration ingredient in Rainbow DQN.",
        "In the lab ε-greedy reaches the goal in 94% of episodes but takes 35.9 steps; the noisy net reaches it in 33% — in 6.0 steps when it does.",
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("epsilon_greedy", "Epsilon-Greedy"),
    ),
)
