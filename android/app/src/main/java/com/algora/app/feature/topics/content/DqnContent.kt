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

internal val dqnContent = TopicContent(
    topicId = "dqn",
    figure = Figure(
        caption = "The page's lab: the same Q-learning update on a six-state chain whose true values " +
            "are 0.9^(5−s), with three choices of what the \"network\" sees, and the largest error " +
            "against those values after 100, 1,000 and 5,000 updates. One-hot features are just a " +
            "table — 12 weights, one per state and action — and converge exactly. A straight line in " +
            "s shares 4 weights across every state, so one update moves them all and it learns " +
            "fastest at first (0.082 after 100), but a line cannot bend to fit 0.9^(5−s) and it " +
            "stalls at 0.019 forever. Adding s² gives 6 weights that fit the curve to 0.002. That is " +
            "the bargain DQN makes at scale: shared weights generalise across states, and the same " +
            "sharing lets an error spread — which is what replay and a target network are there to " +
            "contain.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("12", "0.768", "0.085", "0.000"),
                listOf("4", "0.082", "0.019", "0.019"),
                listOf("6", "0.167", "0.008", "0.002"),
            ),
            rowHeaders = listOf("table", "linear", "quadratic"),
            colHeaders = listOf("weights", "100 upd.", "1,000", "5,000"),
            marks = listOf(
                FigureCell(0, 3, FigureTone.Accent),
                FigureCell(1, 3, FigureTone.Warn),
                FigureCell(1, 1, FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A Deep Q-Network (DQN) is Q-learning with the table replaced by a function. Instead of storing one number per state and action, a network maps a state to a value for every action, Q(s, a) ≈ w_a · φ(s), and learns by nudging its weights toward the same target Q-learning uses: r + γ·max Q(s′, a′). That one change is what let a single algorithm learn dozens of Atari games directly from screen pixels, where no table could ever hold every state.",
        "The lab makes the change small enough to watch every weight move: a six-state chain with a reward of 1 for stepping right off the end, so the true values are Q*(s, right) = 0.9^(5−s) — 0.59 at the start rising to 1.00. With one-hot features the \"network\" is just a table, and it converges to Q* exactly: the largest error falls 0.768 after 100 updates, 0.085 after 1,000, 0.000 after 5,000. Swap in features that share weights and the trade appears. A straight line [1, s] can never bend to fit 0.9^(5−s) and stalls at an error of 0.019; adding s² fits the curve to 0.002 with only three weights per action.",
        "That sharing is the whole point and the whole risk. One update now moves the estimate for many states at once, which is what lets the network generalise to states it has never seen — and also what lets an error in one place spread everywhere, while the target it chases is computed from the same network that is moving. DQN's two stabilisers exist for exactly that: an experience replay buffer that breaks the correlation between consecutive samples, and a frozen target network so the target stays still long enough to be learned. It handles discrete actions only; continuous control needs the DDPG and SAC family.",
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
        "In the lab, one-hot features converge exactly, a linear fit stalls at an error of 0.019, and a quadratic reaches 0.002 with three weights per action.",
    ),
    crossLinks = listOf(
        CrossLink("q_learning", "Q-Learning (off-policy)"),
        CrossLink("rainbow_dqn", "Rainbow DQN"),
    ),
)
