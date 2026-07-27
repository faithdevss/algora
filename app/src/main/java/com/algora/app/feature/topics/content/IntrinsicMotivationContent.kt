package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val intrinsicMotivationContent = TopicContent(
    topicId = "intrinsic_motivation",
    whatIsIt = listOf(
        "Intrinsic motivation gives an agent its own internal rewards — curiosity, novelty, surprise — so it explores meaningfully even when external rewards are sparse or absent.",
        "It's the antidote to hard-exploration problems, where random exploration almost never stumbles onto the rare rewarding states.",
    ),
    steps = listOf(
        StepCard(1, "Define an Intrinsic Signal", "Reward the agent for novelty, prediction error, or reaching new states.", 0xFF818CF8),
        StepCard(2, "Add to the Reward", "Combine intrinsic reward with the (possibly zero) extrinsic reward.", 0xFF60A5FA),
        StepCard(3, "Explore the Unknown", "The agent is drawn toward unfamiliar, informative parts of the environment.", 0xFF10B981),
        StepCard(4, "Fade as Mastery Grows", "Novelty diminishes as states become familiar, naturally reducing the bonus.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Combined reward", "r = r_extrinsic + β·r_intrinsic", "β weights curiosity."),
        FormulaEntry("Novelty forms", "count-based / prediction-error", "Fewer visits or higher surprise → more reward."),
        FormulaEntry("Diminishing", "bonus → 0 as familiarity grows", "Self-limiting exploration."),
    ),
    notationKey = listOf(
        NotationEntry("intrinsic reward", "self-generated exploration signal"),
        NotationEntry("β", "weight on the intrinsic term"),
        NotationEntry("hard exploration", "sparse-reward tasks that defeat random search"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Combining intrinsic and extrinsic reward",
            accentColor = 0xFF6366F1,
            code = """
                intrinsic = novelty_bonus(state)         # e.g. 1/sqrt(visit_count)
                total_reward = extrinsic + beta * intrinsic
                # The agent optimizes total_reward, so curiosity drives exploration.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ExplorationPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Sparse-Reward Games", "Cracked hard-exploration Atari games like Montezuma's Revenge."),
        ApplicationCard("robot", 0xFF60A5FA, "Open-Ended Learning", "Robots acquire skills before any task reward exists."),
        ApplicationCard("bulb", 0xFF10B981, "Skill Discovery", "Curiosity seeds a repertoire of behaviors for later reuse."),
    ),
    takeaways = listOf(
        "Intrinsic motivation supplies internal rewards to drive exploration.",
        "It's essential when extrinsic rewards are sparse or missing.",
        "Novelty and prediction-error are the two dominant signal families.",
        "ICM and RND are concrete, widely-used instantiations.",
    ),
    crossLinks = listOf(
        CrossLink("icm", "Curiosity-Driven (ICM)"),
        CrossLink("rnd", "Random Network Distillation"),
    ),
)
