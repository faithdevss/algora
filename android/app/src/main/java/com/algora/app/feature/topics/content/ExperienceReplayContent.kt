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

internal val experienceReplayContent = TopicContent(
    topicId = "experience_replay",
    figure = Figure(
        caption = "The page's lab: random behaviour on a six-state chain whose only reward sits past " +
            "the far end, so value has to travel back six states from rare arrivals — and the " +
            "largest error against the true values after 200 environment steps, for three amounts " +
            "of replay. Learning online, each transition updates the table once and is discarded, " +
            "and the reward creeps back one state per lucky arrival: 0.332. Replaying four stored " +
            "transitions per step pushes it back through the chain without any new experience, to " +
            "0.038; sixteen replays reach 0.000. Same environment, same 200 steps — the only " +
            "difference is how many times each piece of experience is used. With a neural network " +
            "the second benefit matters as much: random replay breaks the correlation between " +
            "consecutive steps that would otherwise destabilise training.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("online", 1f, FigureTone.Warn),
                FigureBar("replay × 4", 0.114f, FigureTone.Primary),
                FigureBar("replay × 16", 0f, FigureTone.Accent),
            ),
            yLabel = "largest error after 200 steps, 0 to 0.332",
        ),
    ),
    whatIsIt = listOf(
        "Experience replay stores the agent's transitions — (s, a, r, s′, done) — in a buffer and trains on samples drawn from it, instead of learning from each step once and throwing it away. It fixes two problems at once: consecutive steps are strongly correlated, which breaks the independence stochastic gradient descent assumes, and rare informative transitions would otherwise be used a single time.",
        "The lab shows the data-efficiency half directly. Behaviour is random on a six-state chain whose only reward sits past the far end, so value has to travel back six states from rare arrivals. Learning online, each transition is used once and the reward creeps back one state per arrival: after 200 environment steps the largest error against the true values is still 0.332. Replaying four stored transitions per step cuts that to 0.038 from the same experience; replaying sixteen takes it to 0.000.",
        "Replay is only safe for off-policy learners, because the stored transitions were produced by older versions of the policy — Q-learning and DQN can use them, plain policy-gradient methods cannot without correction. Its refinements follow from what uniform sampling wastes: prioritized replay samples surprising transitions more often, and the buffer size trades memory against how stale the oldest experience is allowed to be.",
    ),
    steps = listOf(
        StepCard(1, "Store Transitions", "Save each (state, action, reward, next-state, done) tuple in a fixed-size buffer.", 0xFF818CF8),
        StepCard(2, "Sample Randomly", "Draw a minibatch uniformly at random for each training step.", 0xFF60A5FA),
        StepCard(3, "Break Correlation", "Random sampling decorrelates the training data, stabilizing gradient descent.", 0xFF10B981),
        StepCard(4, "Reuse Data", "Each transition can train the network many times, improving sample efficiency.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Buffer", "D = {(s,a,r,s′,done)}", "A ring buffer of recent transitions."),
        FormulaEntry("Sampling", "batch ~ Uniform(D)", "Uncorrelated minibatches for SGD."),
        FormulaEntry("Benefit", "reuse + decorrelation", "Sample efficiency and training stability."),
    ),
    notationKey = listOf(
        NotationEntry("D", "the replay buffer"),
        NotationEntry("transition", "one (s,a,r,s′,done) tuple"),
        NotationEntry("capacity", "max stored transitions before overwrite"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Replay buffer",
            accentColor = 0xFF6366F1,
            code = """
                from collections import deque
                import random

                class ReplayBuffer:
                    def __init__(self, capacity):
                        self.buffer = deque(maxlen=capacity)
                    def push(self, transition):
                        self.buffer.append(transition)   # (s, a, r, s2, done)
                    def sample(self, batch_size):
                        return random.sample(self.buffer, batch_size)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlTrainingPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF818CF8, "Off-Policy Deep RL", "DQN, DDPG, TD3, and SAC all rely on a replay buffer to train stably."),
        ApplicationCard("chart", 0xFF60A5FA, "Sample Efficiency", "Reusing experience matters when environment interaction is expensive."),
        ApplicationCard("robot", 0xFF10B981, "Robotics", "Real-robot data is costly, so squeezing many updates from each transition is vital."),
    ),
    takeaways = listOf(
        "Experience replay trains on random past transitions from a buffer.",
        "It decorrelates data for stable SGD and reuses each transition many times.",
        "Only off-policy algorithms can safely learn from stored old experience.",
        "Prioritized replay extends it by sampling important transitions more often.",
        "In the lab, after 200 steps the error is 0.332 learning online, 0.038 replaying 4 transitions per step, and 0.000 replaying 16.",
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("prioritized_replay", "Prioritized Experience Replay"),
    ),
)
