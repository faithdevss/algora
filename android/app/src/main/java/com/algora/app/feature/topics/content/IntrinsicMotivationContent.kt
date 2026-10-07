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

internal val intrinsicMotivationContent = TopicContent(
    topicId = "intrinsic_motivation",
    figure = Figure(
        caption = "The page's lab: a 5 × 8 open maze with a reward of +1 only at the goal, eleven steps " +
            "from the start, and 60 episodes of 40 steps per run. The same Q-learning agent is run " +
            "30 times with and without an exploration bonus. Without one, ε = 0.1 random steps " +
            "rarely add up to a trip outward: the agent sees 9.6 cells on average, the reward " +
            "signal is never triggered, and none of the 30 runs ever reaches the goal. With a count " +
            "bonus of 0.3/√N(s′), every unvisited cell pays more than anywhere the agent has " +
            "already been, so exploration becomes directed: 38.3 cells on average, and 21 of 30 " +
            "runs find the goal. Only the bonus differs. Counting needs states that repeat, which " +
            "is why image-based agents use learned novelty instead — ICM and RND.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0 / 30", "21 / 30"),
                listOf("9.6", "38.3"),
            ),
            rowHeaders = listOf("runs reaching G", "cells visited"),
            colHeaders = listOf("ε-greedy only", "+ count bonus"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Warn),
                FigureCell(0, 1, FigureTone.Accent),
                FigureCell(1, 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Intrinsic motivation adds a reward the agent generates for itself — for novelty, surprise or learning progress — on top of the environment's reward: r⁺ = r + β·r_intrinsic. It exists for sparse-reward problems, where the environment says nothing until a goal is reached and random exploration rarely reaches it, so there is nothing to learn from.",
        "The lab makes the problem concrete: a 5 × 8 open maze with a reward of +1 only at the goal, eleven steps from the start, and a budget of 60 episodes of 40 steps. Greedy Q-learning with ε = 0.1 dithers around the start — a random step 10% of the time rarely adds up to a trip outward — and in 30 runs it never reaches the goal once, seeing 9.6 cells on average. Add a count-based bonus, β/√N(s′) with β = 0.3, and cells never entered pay the full 0.30 while well-trodden ones pay almost nothing; the same agent with the same budget reaches the goal in 21 of 30 runs and covers 38.3 cells.",
        "Counting needs states that repeat, and in image-based tasks no frame is ever seen twice. So deep RL replaces the count with a learned stand-in: prediction error of a forward model (ICM), distillation error against a random network (RND), or density-model pseudo-counts. Each has to generalise novelty to states it has not seen, and each has failure modes — a noisy TV is endlessly surprising to a naive predictor — which is why the choice of novelty signal matters as much as having one.",
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
        "In the lab ε-greedy reaches the goal in 0 of 30 runs; the same agent with a count bonus reaches it in 21, covering 38.3 cells instead of 9.6.",
    ),
    crossLinks = listOf(
        CrossLink("icm", "Curiosity-Driven (ICM)"),
        CrossLink("rnd", "Random Network Distillation"),
    ),
)
