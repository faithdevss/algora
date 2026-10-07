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

internal val gailContent = TopicContent(
    topicId = "gail",
    figure = Figure(
        caption = "The page's lab: GAIL learning to imitate an expert who, under 10% slips, recovers " +
            "by stepping back to the middle row. Measured by the total-variation distance between " +
            "the policy's and the expert's state occupancy, behaviour cloning starts at 0.262 and " +
            "succeeds 69% of the time. GAIL trains a discriminator to tell expert visits from " +
            "policy visits and uses its output as the reward, so moves back towards the middle — " +
            "which the expert makes — score high. The first update overshoots: the policy flees " +
            "the penalised states, the distance jumps to 0.438 and success falls to 31%. Then it " +
            "settles, 0.203 after two iterations and 0.061 after five, with success at 92%. " +
            "Matching where the expert goes, rather than what it does in each state, is what lets " +
            "it recover from its own mistakes.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "distance to expert occupancy",
                    listOf(FigurePoint(0f, 0.524f), FigurePoint(0.2f, 0.876f), FigurePoint(0.4f, 0.406f), FigurePoint(1f, 0.122f)),
                    tone = FigureTone.Accent,
                ),
            ),
            markers = listOf(
                FigurePoint(0f, 0.524f, "clone 0.262 · 69%", FigureTone.Muted),
                FigurePoint(0.2f, 0.876f, "overshoot 0.438", FigureTone.Warn),
                FigurePoint(1f, 0.122f, "0.061 · 92%"),
            ),
            xLabel = "GAIL iteration 0 · 1 · 2 · 5",
            yLabel = "total variation, 0 to 0.5",
        ),
    ),
    whatIsIt = listOf(
        "GAIL — Generative Adversarial Imitation Learning — imitates an expert by matching where it goes, not what it does in each state. A discriminator is trained to tell the expert's state-action pairs from the policy's, and its output becomes the reward: r = log D − log(1 − D) is high wherever the policy behaves like the expert. The policy is then trained by ordinary RL on that reward, so it learns to recover from its own mistakes the way the expert does.",
        "The lab compares it with behaviour cloning on a grid where the expert, under 10% slips, recovers by stepping back to the middle row — so its occupancy spills into the outer rows and returns. The clone's occupancy is in the wrong places, 0.262 away from the expert's in total variation distance, and it reaches the goal 69% of the time. The discriminator's reward scores moves back towards the middle highly (the expert makes them) and pressing on along a wrong row low.",
        "Training is adversarial, and the lab shows it: the first update overshoots — the policy flees the penalised states and its distance jumps to 0.438 with success falling to 31% — then it settles: 0.203 after two iterations, 0.061 after five, with success at 92%. Matching occupancy rather than actions is what lets GAIL generalise where cloning compounds errors, at the price of an RL loop and a GAN-style training instability.",
    ),
    steps = listOf(
        StepCard(1, "Discriminator", "Train a classifier to distinguish expert (s,a) pairs from agent-generated ones.", 0xFF818CF8),
        StepCard(2, "Reward from the Discriminator", "The agent's reward is how expert-like the discriminator judges its behavior.", 0xFF60A5FA),
        StepCard(3, "Policy Update", "Optimize the policy with RL (typically TRPO/PPO) on that reward.", 0xFF10B981),
        StepCard(4, "Adversarial Convergence", "As both improve, the agent's occupancy matches the expert's distribution.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Adversarial objective", "min_π max_D E_π[log D] + E_E[log(1−D)]", "GAN over state-action pairs."),
        FormulaEntry("Surrogate reward", "r = −log(D(s,a))", "Higher when D says \"agent\" with low probability, i.e. when the agent looks expert-like."),
        FormulaEntry("No explicit reward", "matches occupancy directly", "Skips IRL's reward recovery."),
    ),
    notationKey = listOf(
        NotationEntry("D", "discriminator: D(s,a) = probability the pair came from the agent (1 = agent, 0 = expert)"),
        NotationEntry("occupancy", "state-action visitation distribution"),
        NotationEntry("surrogate reward", "discriminator-derived signal for RL"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "GAIL discriminator reward",
            accentColor = 0xFF6366F1,
            code = """
                # Discriminator (original GAIL convention): D(s,a) = P(pair came from the AGENT),
                # so 1 = agent, 0 = expert.
                d_loss = bce(D(agent_sa), ones) + bce(D(expert_sa), zeros)
                # D is small when the agent looks expert-like, so -log D is large there:
                reward = -torch.log(D(agent_sa) + 1e-8)
                policy.update(agent_sa, reward)     # via PPO/TRPO
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.OfflineRlPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Robotic Imitation", "Learning complex motor skills from a handful of demos."),
        ApplicationCard("game", 0xFF60A5FA, "Human-like Agents", "Producing behavior that mimics human play styles."),
        ApplicationCard("bulb", 0xFF10B981, "Reward-Free Imitation", "Matches expert behavior without designing or recovering a reward."),
    ),
    takeaways = listOf(
        "GAIL casts imitation as a GAN: fool a discriminator that spots non-expert behavior.",
        "The discriminator supplies the reward; RL optimizes the policy against it.",
        "It matches the expert's occupancy without recovering an explicit reward.",
        "It avoids behavioral cloning's covariate shift by learning on-policy.",
        "In the lab GAIL cuts the distance to the expert's occupancy from 0.262 to 0.061 and raises success from 69% to 92%, after an initial overshoot.",
    ),
    crossLinks = listOf(
        CrossLink("gans", "GANs"),
        CrossLink("irl", "IRL"),
    ),
)
