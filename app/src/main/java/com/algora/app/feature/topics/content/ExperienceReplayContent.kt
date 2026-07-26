package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val experienceReplayContent = TopicContent(
    topicId = "experience_replay",
    whatIsIt = listOf(
        "Experience replay stores an agent's past transitions in a buffer and trains on random samples from it, instead of learning only from the latest experience.",
        "It fixes two problems: consecutive experiences are highly correlated (bad for SGD), and each experience is otherwise seen once then thrown away.",
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
    ),
    crossLinks = listOf(
        CrossLink("dqn", "DQN"),
        CrossLink("prioritized_replay", "Prioritized Experience Replay"),
    ),
)
