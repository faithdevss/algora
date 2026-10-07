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

internal val worldModelsContent = TopicContent(
    topicId = "world_models",
    figure = Figure(
        caption = "The page's lab: a world model that is simply a table of what each state and action " +
            "led to, filled in from random real steps on the 4×4 grid, and a plan made inside it by " +
            "value iteration — free, with zero real steps — then executed in the real grid. After " +
            "8 real steps the model has seen 7 of the 52 state-action pairs and assumes every " +
            "unseen action goes nowhere. The plan is optimal for that model and useless in the " +
            "world: it gets stuck and times out after 20 steps. At 40 real steps, 40% of pairs " +
            "known, it still fails. At 120, 65% known, the model is right where the route needs it " +
            "to be, and the plan reaches the goal in 6 steps. Planning amplifies the model, gaps " +
            "included — coverage, or knowing where the model is unsure, decides whether it helps.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("7 / 52 · 13%", "stuck, timeout"),
                listOf("21 / 52 · 40%", "stuck, timeout"),
                listOf("34 / 52 · 65%", "goal in 6 steps"),
            ),
            rowHeaders = listOf("8 real steps", "40 real steps", "120 real steps"),
            colHeaders = listOf("pairs known", "plan, executed for real"),
            marks = listOf(
                FigureCell(0, 1, FigureTone.Warn),
                FigureCell(1, 1, FigureTone.Warn),
                FigureCell(2, 1, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A world model is a learned predictor of what happens next — the next observation and reward given the current one and an action. Once an agent has one, it can plan or practise inside it for free, without spending real experience. Ha and Schmidhuber's World Models compressed frames with a VAE, predicted the latent future with a recurrent network, and trained a tiny controller entirely inside that \"dream\".",
        "The lab shows both the promise and the catch with the simplest possible model: a table filled in from random real steps on the 4×4 grid, which knows only what it has seen. After 8 real steps it has observed 7 of the 52 state-action pairs (13%) and assumes every unknown action goes nowhere. Value iteration inside the model is free and produces a plan that is optimal for the model — and executed in the real grid it gets stuck and times out after 20 steps. After 40 real steps 40% of pairs are known; after 120, 65%, and the same planning now produces a route that reaches the goal for real.",
        "The lesson is that planning amplifies whatever the model believes, including its gaps: a plan is only as trustworthy as the model's coverage of the states the plan visits. More real data is one fix; the other is to make the model's uncertainty explicit and plan conservatively where it is unsure, which is what ensembles in MBPO and the latent models in Dreamer and MuZero are built to handle.",
    ),
    steps = listOf(
        StepCard(1, "Vision (V)", "A VAE compresses each high-dimensional observation into a small latent vector.", 0xFF818CF8),
        StepCard(2, "Memory (M)", "An RNN models how the latent evolves over time, predicting the next latent.", 0xFF60A5FA),
        StepCard(3, "Controller (C)", "A tiny policy maps latent + memory to actions.", 0xFF10B981),
        StepCard(4, "Train in the Dream", "Learn the controller inside the learned model, then deploy in the real environment.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("V", "z = encode(observation)", "Latent compression via a VAE."),
        FormulaEntry("M", "hₜ, ẑₜ₊₁ = RNN(zₜ, aₜ, hₜ₋₁)", "Predict the next latent state."),
        FormulaEntry("C", "aₜ = controller(zₜ, hₜ)", "A small, fast policy."),
    ),
    notationKey = listOf(
        NotationEntry("z", "latent observation code"),
        NotationEntry("V, M, C", "vision, memory, controller components"),
        NotationEntry("dreaming", "training inside the learned model"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "World Models pipeline (V-M-C)",
            accentColor = 0xFF6366F1,
            code = """
                z      = vae.encode(obs)              # V: compress
                h, z_next = rnn(z, action, h)         # M: predict dynamics
                action = controller(z, h)             # C: act
                # The controller is trained on rollouts imagined by V + M.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GameSearchPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF818CF8, "Learning in Imagination", "Trained agents that solved tasks largely inside their own dreamed environment."),
        ApplicationCard("robot", 0xFF60A5FA, "Sample-Efficient RL", "Reduces costly real interaction by planning in a learned model."),
        ApplicationCard("bulb", 0xFF10B981, "Latent Dynamics", "Pioneered compressing observations and learning their evolution for control."),
    ),
    takeaways = listOf(
        "World Models learn a generative simulator and train the policy inside it.",
        "The V-M-C split compresses observations, predicts dynamics, and acts.",
        "Learning in imagination cuts real-environment interaction.",
        "Dreamer built on this idea into a leading model-based RL family.",
        "In the lab a model built from 8 real steps (13% of pairs) gives a plan that fails in the real grid; at 120 steps (65%) the plan reaches the goal.",
    ),
    crossLinks = listOf(
        CrossLink("dreamer", "Dreamer (V1–V3)"),
        CrossLink("autoencoders", "Autoencoders"),
    ),
)
