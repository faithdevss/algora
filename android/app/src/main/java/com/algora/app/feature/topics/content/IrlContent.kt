package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val irlContent = TopicContent(
    topicId = "irl",
    whatIsIt = listOf(
        "Inverse Reinforcement Learning (IRL) infers the reward function an expert seems to be optimizing, rather than cloning their actions directly.",
        "Recovering the intent behind the behavior generalizes better than imitation: with the reward in hand, an agent can plan optimally even in new situations the expert never demonstrated.",
    ),
    steps = listOf(
        StepCard(1, "Observe the Expert", "Collect demonstrations assumed to be (near-)optimal.", 0xFF818CF8),
        StepCard(2, "Hypothesize a Reward", "Parameterize a reward function, often over state features.", 0xFF60A5FA),
        StepCard(3, "Match Behavior", "Adjust the reward so its optimal policy reproduces the expert's feature statistics.", 0xFF10B981),
        StepCard(4, "Resolve Ambiguity", "Max-entropy IRL chooses the maximum-entropy distribution over trajectories (P(τ) ∝ exp R(τ)) that matches the demos' feature counts, which pins down a reward.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Feature matching", "E_π[φ] = E_expert[φ]", "Match expected feature counts."),
        FormulaEntry("Ill-posed", "many rewards fit", "Multiple rewards explain the same behavior."),
        FormulaEntry("MaxEnt IRL", "max-entropy trajectory distribution", "P(τ) ∝ exp R(τ), matching expert feature counts; resolves the ambiguity in a principled way."),
    ),
    notationKey = listOf(
        NotationEntry("reward function", "the inferred objective R(s)"),
        NotationEntry("φ(s)", "state features the reward is built from"),
        NotationEntry("ill-posed", "many rewards explain the same demos"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Max-entropy IRL (idea)",
            accentColor = 0xFF6366F1,
            code = """
                # Learn reward weights so the optimal policy matches expert feature counts.
                for _ in range(iters):
                    policy = solve_mdp(reward_weights)              # inner RL
                    grad = expert_feature_counts - policy_feature_counts(policy)
                    reward_weights += lr * grad                    # feature matching
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.OfflineRlPlayer,
    applications = listOf(
        ApplicationCard("map", 0xFF818CF8, "Driving & Navigation", "Recovering the cost function behind human route choices."),
        ApplicationCard("robot", 0xFF60A5FA, "Robot Skill Transfer", "Inferring goals so robots generalize demonstrated tasks."),
        ApplicationCard("bulb", 0xFF10B981, "Reward Design", "Learning hard-to-specify rewards from examples instead of hand-coding them."),
    ),
    takeaways = listOf(
        "IRL infers the reward an expert optimizes, not just their actions.",
        "Knowing the reward generalizes better than cloning behavior.",
        "It's ill-posed — max-entropy IRL resolves the reward ambiguity.",
        "GAIL sidesteps recovering an explicit reward while capturing the intent.",
    ),
    crossLinks = listOf(
        CrossLink("imitation_learning", "Imitation Learning"),
        CrossLink("gail", "GAIL"),
    ),
)
