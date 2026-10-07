package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val gailContent = TopicContent(
    topicId = "gail",
    whatIsIt = listOf(
        "Generative Adversarial Imitation Learning (GAIL) imitates an expert without recovering a reward, by framing imitation as a GAN: a discriminator tries to tell agent behavior from expert behavior.",
        "The agent is trained (with RL) to fool the discriminator, so its state-action distribution converges to the expert's — matching intent without the covariate shift of behavioral cloning.",
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
    ),
    crossLinks = listOf(
        CrossLink("gans", "GANs"),
        CrossLink("irl", "IRL"),
    ),
)
