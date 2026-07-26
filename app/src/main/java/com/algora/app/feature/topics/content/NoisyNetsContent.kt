package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val noisyNetsContent = TopicContent(
    topicId = "noisy_nets",
    whatIsIt = listOf(
        "Noisy Nets add learnable noise directly to a network's weights, so exploration comes from the network itself rather than a hand-tuned ε-greedy schedule.",
        "The agent learns how much to explore in each state — the noise scale is a parameter trained end-to-end and automatically shrinks as the policy sharpens.",
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
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("epsilon_greedy", "Epsilon-Greedy"),
    ),
)
