package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val decisionTransformerContent = TopicContent(
    topicId = "decision_transformer",
    whatIsIt = listOf(
        "The Decision Transformer reframes offline RL as sequence modeling: a Transformer is trained to predict the next action given a history of returns, states, and actions.",
        "No value functions, no Bellman backups — you feed it a desired return ('reward-to-go') and it generates actions that achieve it, treating control like language modeling.",
    ),
    steps = listOf(
        StepCard(1, "Tokenize Trajectories", "Represent each trajectory as a sequence of (return-to-go, state, action) tokens.", 0xFF818CF8),
        StepCard(2, "Autoregressive Training", "Train a causal Transformer to predict actions from the preceding tokens.", 0xFF60A5FA),
        StepCard(3, "Condition on Return", "At test time, prompt it with a target return-to-go.", 0xFF10B981),
        StepCard(4, "Generate Actions", "It outputs actions that, per the training data, tend to reach that return.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Sequence", "(R̂₁,s₁,a₁, R̂₂,s₂,a₂, …)", "Return-to-go, state, action tokens."),
        FormulaEntry("Objective", "predict aₜ | tokens<ₜ", "Supervised action prediction."),
        FormulaEntry("Control via prompt", "condition on target R̂", "Desired return steers behavior."),
    ),
    notationKey = listOf(
        NotationEntry("return-to-go", "sum of future rewards, R̂ₜ"),
        NotationEntry("causal Transformer", "attends only to past tokens"),
        NotationEntry("sequence modeling", "RL cast as next-token prediction"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Decision Transformer input",
            accentColor = 0xFF6366F1,
            code = """
                # Interleave returns-to-go, states, actions as one token sequence.
                tokens = interleave(returns_to_go, states, actions)
                pred_actions = transformer(tokens)          # causal, predicts next action
                # At test: set the first return-to-go to the desired target.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.OfflineRlPlayer,
    applications = listOf(
        ApplicationCard("robot", 0xFF818CF8, "Offline Control", "Competitive on offline-RL benchmarks with no dynamic programming."),
        ApplicationCard("book", 0xFF60A5FA, "RL-as-Sequence", "Brings the Transformer toolbox and scaling to decision making."),
        ApplicationCard("chart", 0xFF10B981, "Return-Conditioned Behavior", "Dial performance by choosing the target return."),
    ),
    takeaways = listOf(
        "Decision Transformer treats RL as return-conditioned sequence modeling.",
        "It predicts actions autoregressively — no value functions or Bellman updates.",
        "Prompting with a target return-to-go steers the generated behavior.",
        "It ports Transformer scaling and simplicity into offline RL.",
    ),
    crossLinks = listOf(
        CrossLink("transformers", "Transformers"),
        CrossLink("offline_rl", "Offline RL"),
    ),
)
