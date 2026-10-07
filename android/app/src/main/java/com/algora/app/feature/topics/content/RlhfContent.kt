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

internal val rlhfContent = TopicContent(
    topicId = "rlhf",
    figure = Figure(
        caption = "The page's lab: a reward model fitted to raters' pairwise comparisons, against the " +
            "true reward 2·goal − 0.1·steps − 1.5·vase. The raters could see whether the goal was " +
            "reached and how long it took, so those weights come out right — 2.06 and −0.10. They " +
            "could not see the vase, so its learned weight is 0 where the truth is −1.5. On that " +
            "proxy the shortcut that breaks the vase scores 1.46 against the careful path's 1.06, and " +
            "a policy optimised with a weak KL leash (β = 0.1) puts 96% on the shortcut: proxy reward " +
            "1.44, true return −0.05. Tightening the leash keeps the policy near the reference and " +
            "the true return at 0.476 (β = 0.5) or 0.427 (β = 2). Reward hacking lives exactly in the " +
            "cells the feedback never covered.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("2.06", "2.0"),
                listOf("−0.10", "−0.10"),
                listOf("0.00", "−1.50"),
            ),
            rowHeaders = listOf("goal reached", "per step", "vase broken"),
            colHeaders = listOf("learned reward", "true reward"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Accent),
                FigureCell(2, 0, FigureTone.Warn),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Reinforcement learning from human feedback turns human preferences into a training signal. Raters compare pairs of model outputs, a reward model is fitted to predict which one they prefer, and the policy is then optimised against that learned reward — with a KL penalty keeping it close to the original model so it cannot drift into strange outputs that happen to score well.",
        "The lab shows the central risk in a tiny world with three behaviours: a careful path, a shortcut that breaks a vase, and wandering. The true reward is 2·goal − 0.1·steps − 1.5·vase, but the raters can see whether the goal was reached and how long it took — not the vase. The fitted reward model is right about everything it was shown (goal weight 2.06 against 2.0, steps −0.10) and blind to what it was not: its vase weight is 0. On that proxy the shortcut scores 1.46 against the careful path's 1.06, so with a weak KL leash (β = 0.1) the policy puts 96% on the shortcut: proxy reward 1.44, true return −0.05 — paid for breaking the vase.",
        "The KL penalty is the guard against that reward hacking. With β = 0.5 the policy stays close enough to the reference that its true return is 0.476; at β = 2 it is 0.427, safer but improving less. Reward hacking is not a bug in an implementation but a property of optimising any learned proxy hard; what limits it is the KL leash, better feedback, and checking behaviour against what raters could not see.",
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
        "In the lab a reward model blind to the vase drives a weakly leashed policy (β 0.1) to a true return of −0.05; β = 0.5 keeps it at 0.476.",
    ),
    crossLinks = listOf(
        CrossLink("ppo", "PPO"),
        CrossLink("llms", "LLMs"),
    ),
)
