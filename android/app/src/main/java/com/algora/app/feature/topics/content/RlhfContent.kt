package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val rlhfContent = TopicContent(
    topicId = "rlhf",
    whatIsIt = listOf(
        "Reinforcement Learning from Human Feedback (RLHF) aligns a model with human preferences by learning a reward model from human comparisons, then optimizing the policy against it.",
        "It's how a raw language model becomes a helpful assistant: humans rank outputs, a reward model learns those preferences, and RL (usually PPO) fine-tunes the model to score well.",
    ),
    steps = listOf(
        StepCard(1, "Collect Preferences", "Humans compare pairs of model outputs and pick the better one.", 0xFF818CF8),
        StepCard(2, "Train a Reward Model", "Fit a model to predict which output humans prefer.", 0xFF60A5FA),
        StepCard(3, "Optimize with RL", "Fine-tune the policy (PPO) to maximize the reward model's score.", 0xFF10B981),
        StepCard(4, "KL Anchor", "Penalize drifting too far from the original model to preserve fluency and avoid reward hacking.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Reward model", "P(y₁ ≻ y₂) = σ(r(y₁) − r(y₂))", "Bradley-Terry over preferences."),
        FormulaEntry("RL objective", "max E[r(y)] − β·KL(π ‖ π_ref)", "Reward minus divergence from the base model."),
        FormulaEntry("KL β", "anchors the policy", "Prevents reward hacking and degeneration."),
    ),
    notationKey = listOf(
        NotationEntry("reward model", "learned proxy for human preference"),
        NotationEntry("KL penalty", "keeps policy near the reference model"),
        NotationEntry("preference data", "human pairwise comparisons"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "RLHF objective (idea)",
            accentColor = 0xFF6366F1,
            code = """
                # 1) Reward model from human comparisons (Bradley-Terry loss):
                rm_loss = -torch.log(torch.sigmoid(r(preferred) - r(rejected))).mean()

                # 2) PPO fine-tune with a KL anchor to the reference model:
                reward = reward_model(response) - beta * kl(policy, ref_policy)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.OfflineRlPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Aligning LLMs", "Turns pretrained language models into instruction-following assistants."),
        ApplicationCard("book", 0xFF60A5FA, "Helpfulness & Safety", "Steers models toward helpful, harmless, honest responses."),
        ApplicationCard("target", 0xFF10B981, "Preference Optimization", "The template later simplified by methods like DPO."),
    ),
    takeaways = listOf(
        "RLHF aligns models by learning a reward from human preference comparisons.",
        "A reward model plus PPO fine-tunes the policy toward preferred outputs.",
        "A KL penalty to the base model curbs reward hacking and preserves fluency.",
        "It's the classic alignment recipe behind modern chat assistants; DPO streamlines it.",
    ),
    crossLinks = listOf(
        CrossLink("ppo", "PPO"),
        CrossLink("llms", "LLMs"),
    ),
)
