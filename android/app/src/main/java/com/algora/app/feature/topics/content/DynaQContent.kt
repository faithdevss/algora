package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBar
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val dynaQContent = TopicContent(
    topicId = "dyna_q",
    figure = Figure(
        caption = "The page's lab: Dyna-Q on the 4×4 grid world, every setting given exactly the same " +
            "real experience, and the mean error of its values against the true optimum after five " +
            "episodes. Plain Q-learning learns only from real steps, and value creeps back from the " +
            "goal one state per real visit: 0.630. Each real step also records what happened in a " +
            "learned model; replaying 5 imagined transitions from that model per real step cuts " +
            "the error to 0.161, and 50 cut it to 0.020. Not one extra real step was taken — the " +
            "planning turned compute into learning. The catch is that every imagined update is " +
            "only as true as the model, so a model learned from too little data, or in a world " +
            "that has changed, plans confidently towards the wrong thing.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("no planning", 1f, FigureTone.Warn),
                FigureBar("plan × 5", 0.256f, FigureTone.Primary),
                FigureBar("plan × 50", 0.032f, FigureTone.Accent),
            ),
            yLabel = "mean value error after 5 episodes, 0 to 0.630",
        ),
    ),
    whatIsIt = listOf(
        "Dyna-Q combines learning and planning in one loop. Every real step does two things: an ordinary Q-learning update, and an entry in a learned model recording what that state and action led to — the next state and the reward. Then the agent replays n imagined steps sampled from that model, each giving another Q-learning update, before it takes its next real action.",
        "The lab runs it on the 4×4 grid world with the same real experience for every setting and compares the mean error of max Q against the true values after 5 episodes. Plain Q-learning (no planning) is still at 0.630 — value creeps back from the goal one state per real visit. With 5 planning updates per real step the error is 0.161; with 50, it is 0.020. The real steps were identical; the extra learning came entirely from replaying the model.",
        "Planning substitutes compute for experience, which is exactly what you want when real steps are expensive — a robot, a slow simulator, a live system. But every imagined update is only as good as the model it came from: a model learned from little data, or in a changing environment, plans confidently towards things that are no longer true. Dyna-Q+ adds a bonus for transitions not tried recently to cope with that, and MBPO, Dreamer and MuZero are this same loop with a learned neural model.",
    ),
    steps = listOf(
        StepCard(1, "Act & Learn Directly", "Take a real step and apply the standard Q-learning update.", 0xFF818CF8),
        StepCard(2, "Update the Model", "Record the observed (s,a) → (r, s′) so the model can reproduce it.", 0xFF60A5FA),
        StepCard(3, "Plan with Simulated Steps", "Sample past (s,a) pairs from the model and do Q-updates on the imagined outcomes.", 0xFF10B981),
        StepCard(4, "Repeat n Planning Steps", "More simulated updates per real step means faster convergence.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Direct RL", "Q(s,a) += α[r + γ maxQ(s′,·) − Q(s,a)]", "Real-experience Q-learning."),
        FormulaEntry("Model", "Model(s,a) → (r, s′)", "Learned from observed transitions."),
        FormulaEntry("Planning", "n simulated updates / real step", "Same update, imagined data."),
    ),
    notationKey = listOf(
        NotationEntry("model", "learned map (s,a) → (r, s′)"),
        NotationEntry("n", "planning steps per real step"),
        NotationEntry("planning", "Q-updates on simulated experience"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Dyna-Q planning loop",
            accentColor = 0xFF6366F1,
            code = """
                # 1) Direct RL from the real step, then update the model:
                q_update(s, a, r, s2)
                model[(s, a)] = (r, s2)

                # 2) Planning: n simulated updates from remembered transitions.
                for _ in range(n):
                    (ps, pa) = random.choice(list(model.keys()))
                    (pr, ps2) = model[(ps, pa)]
                    q_update(ps, pa, pr, ps2)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlGridWorld,
    applications = listOf(
        ApplicationCard("chart", 0xFF818CF8, "Sample Efficiency", "Extracts far more learning per real interaction, vital when data is costly."),
        ApplicationCard("map", 0xFF60A5FA, "Navigation & Mazes", "Classic gridworld planning where a learned model speeds route-finding."),
        ApplicationCard("robot", 0xFF10B981, "Robotics", "Fewer real trials needed when a model can be replayed."),
    ),
    takeaways = listOf(
        "Dyna-Q integrates learning, model-building, and planning in one loop.",
        "Simulated experience from the learned model amplifies each real step.",
        "More planning steps trade compute for fewer real interactions.",
        "It's the bridge between tabular model-free and full model-based RL.",
        "In the lab, after 5 episodes the value error is 0.630 with no planning, 0.161 with 5 imagined updates per step and 0.020 with 50.",
    ),
    crossLinks = listOf(
        CrossLink("q_learning", "Q-Learning (off-policy)"),
        CrossLink("mbpo", "MBPO"),
    ),
)
