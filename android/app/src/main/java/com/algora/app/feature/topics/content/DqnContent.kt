package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val dqnContent = TopicContent(
    topicId = "dqn",
    whatIsIt = listOf(
        "A Deep Q-Network (DQN) scales Q-learning to high-dimensional inputs by replacing the Q-table with a neural network that predicts action-values from raw states like pixels.",
        "It was the breakthrough that learned to play Atari games from screen images alone, stabilized by two key tricks: experience replay and a target network.",
    ),
    steps = listOf(
        StepCard(1, "Q-Network", "A neural net maps a state to a Q-value for every action.", 0xFF818CF8),
        StepCard(2, "Experience Replay", "Store transitions in a buffer and train on random minibatches to break correlation.", 0xFF60A5FA),
        StepCard(3, "Target Network", "A periodically-frozen copy provides stable TD targets.", 0xFF10B981),
        StepCard(4, "Minimize TD Loss", "Train the network to reduce the squared error between prediction and target.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("TD target", "y = r + γ·maxₐ′ Q(s′,a′; θ⁻)", "Uses the frozen target params θ⁻."),
        FormulaEntry("Loss", "L = (y − Q(s,a; θ))²", "Squared TD error over replayed batches."),
        FormulaEntry("Stabilizers", "replay + target net", "The two ingredients that made it work."),
    ),
    notationKey = listOf(
        NotationEntry("θ, θ⁻", "online and target network parameters"),
        NotationEntry("replay buffer", "stored past transitions to sample from"),
        NotationEntry("TD target", "bootstrapped learning signal"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "DQN loss (PyTorch sketch)",
            accentColor = 0xFF6366F1,
            code = """
                # Sample a minibatch (s, a, r, s2, done) from the replay buffer.
                q      = policy_net(s).gather(1, a)                    # Q(s,a; θ)
                with torch.no_grad():
                    target = r + gamma * target_net(s2).max(1)[0] * (1 - done)
                loss = F.mse_loss(q.squeeze(), target)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlTrainingPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Atari from Pixels", "DQN reached human-level play on many Atari 2600 games from raw frames."),
        ApplicationCard("robot", 0xFF60A5FA, "Discrete Control", "Any discrete-action task with high-dimensional observations."),
        ApplicationCard("chip", 0xFF10B981, "Deep RL Baseline", "The starting point that the whole Rainbow family of improvements builds on."),
    ),
    takeaways = listOf(
        "DQN approximates Q-values with a neural network for high-dimensional states.",
        "Experience replay and a target network are what stabilize training.",
        "It launched deep RL by mastering Atari from pixels.",
        "It handles discrete actions; continuous control needs DDPG/SAC-style methods.",
    ),
    crossLinks = listOf(
        CrossLink("q_learning", "Q-Learning (off-policy)"),
        CrossLink("rainbow_dqn", "Rainbow DQN"),
    ),
)
