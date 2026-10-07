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

internal val imitationLearningContent = TopicContent(
    topicId = "imitation_learning",
    figure = Figure(
        caption = "The page's lab: an expert that never slips demonstrates only the 7 states along " +
            "the middle row of a grid, and a behaviour-cloned policy then runs with 10% random " +
            "slips. One slip puts it in a state it has no label for; it keeps pressing on along " +
            "the wrong row and pins itself against the far wall. By step 15, 31% of its probability " +
            "is off the demonstrated row, against 0.1% for the expert under the same slips — errors " +
            "compound instead of averaging out — and it reaches the goal 69% of the time against " +
            "the expert's 100%. DAgger rolls out the learner, asks the expert to label the states " +
            "it actually visits, and retrains: after four rounds it knows 23 states and matches the " +
            "expert. With 20% slips cloning manages 68% and DAgger 92%.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("69%", "100%"),
                listOf("68%", "92%"),
                listOf("7", "23"),
            ),
            rowHeaders = listOf("success, 10% slip", "success, 20% slip", "states labelled"),
            colHeaders = listOf("behaviour cloning", "DAgger"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Warn),
                FigureCell(0, 1, FigureTone.Accent),
                FigureCell(1, 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Imitation learning learns a policy from an expert's demonstrations instead of from rewards. The simplest form, behaviour cloning, is plain supervised learning: states in, the expert's actions out. It is effective when the demonstrations cover the states the learner will meet — and fails in a characteristic way when they do not.",
        "The lab makes the failure visible. A clean expert never slips, so its demonstrations cover only the 7 states along the middle row of the grid, all labelled →. The clone then runs with 10% slips. One slip puts it in a state it was never shown, where it falls back on its majority label, keeps pressing → along the wrong row and pins itself against the far wall. It reaches the goal in 15 steps only 69% of the time, against the expert's 100% under the same slips, and by step 15, 31% of its probability mass is off the demonstrated row versus 0.1% for the expert — errors compound instead of averaging out.",
        "DAgger fixes it by labelling the states the learner actually reaches: roll out the current policy, ask the expert what it would have done in each visited state, add those labels, retrain, repeat. After four rounds the lab's learner knows 23 states instead of 7 and matches the expert's 100%; with 20% slips, cloning manages 68% and DAgger 92%. The catch is that DAgger needs an expert who can be queried on demand; GAIL and inverse RL are the alternatives when only fixed demonstrations exist.",
    ),
    steps = listOf(
        StepCard(1, "Collect Demonstrations", "Gather state-action pairs from an expert.", 0xFF818CF8),
        StepCard(2, "Supervised Fit", "Train a policy to predict the expert's action for each state.", 0xFF60A5FA),
        StepCard(3, "Watch for Drift", "Small errors push the agent into states the expert never visited (covariate shift).", 0xFF10B981),
        StepCard(4, "Correct with DAgger", "Iteratively query the expert on the agent's own states to fix compounding errors.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Behavioral cloning", "min E[ℓ(π(s), a_expert)]", "Supervised action prediction."),
        FormulaEntry("Covariate shift", "errors compound off-distribution", "The core failure mode."),
        FormulaEntry("DAgger", "aggregate expert labels on π's states", "Iterative correction."),
    ),
    notationKey = listOf(
        NotationEntry("behavioral cloning", "supervised state → action fitting"),
        NotationEntry("covariate shift", "test states differ from demo states"),
        NotationEntry("DAgger", "Dataset Aggregation algorithm"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Behavioral cloning (supervised)",
            accentColor = 0xFF6366F1,
            code = """
                # Treat (state, expert_action) pairs as a supervised dataset.
                pred = policy(states)
                loss = F.cross_entropy(pred, expert_actions)   # or MSE for continuous
                loss.backward(); optimizer.step()
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.OfflineRlPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Robot Teaching", "Learning manipulation from human teleoperation demos."),
        ApplicationCard("map", 0xFF60A5FA, "Autonomous Driving", "Cloning expert driving from recorded human trajectories."),
        ApplicationCard("bulb", 0xFF10B981, "Reward-Free Learning", "When demonstrating is easier than designing a reward."),
    ),
    takeaways = listOf(
        "Imitation learning copies expert behavior instead of maximizing a reward.",
        "Behavioral cloning is simple supervised learning but suffers covariate shift.",
        "DAgger corrects compounding errors by relabeling the agent's own states.",
        "Inverse RL and GAIL go further by recovering the expert's intent.",
        "In the lab behaviour cloning reaches the goal 69% of the time under slips; DAgger, after labelling 23 states instead of 7, reaches 100%.",
    ),
    crossLinks = listOf(
        CrossLink("irl", "IRL"),
        CrossLink("gail", "GAIL"),
    ),
)
