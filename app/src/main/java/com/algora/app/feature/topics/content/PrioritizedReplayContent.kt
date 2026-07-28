package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val prioritizedReplayContent = TopicContent(
    topicId = "prioritized_replay",
    whatIsIt = listOf(
        "Prioritized Experience Replay samples transitions in proportion to how much the agent can learn from them, measured by their TD error, instead of uniformly.",
        "Surprising transitions — where the prediction was most wrong — are replayed more often, speeding up learning, with importance-sampling weights to correct the resulting bias.",
        "Two details make or break it. New transitions must enter at maximum priority, or a transition whose first TD error happened to be small is never sampled again and never gets a chance to correct itself. And the bias correction must be annealed: heavy prioritization early is what accelerates learning, but the final policy has to be evaluated under something close to the true distribution.",
    ),
    steps = listOf(
        StepCard(1, "Score by TD Error", "Each transition's priority is its absolute TD error — how surprising it was.", 0xFF818CF8),
        StepCard(2, "Sample Proportionally", "Draw transitions with probability proportional to priority^α.", 0xFF60A5FA),
        StepCard(3, "Correct the Bias", "Non-uniform sampling skews the gradient; importance-sampling weights undo it.", 0xFF10B981),
        StepCard(4, "Normalize the Weights", "Divide by max w so the correction only ever scales updates down — otherwise it doubles as a learning-rate increase.", 0xFF8B5CF6),
        StepCard(5, "Update Priorities", "After training, refresh each sampled transition's priority with its new TD error.", 0xFFF59E0B),
        StepCard(6, "Admit New Data at Max Priority", "An unseen transition has no TD error yet; give it the highest so it is guaranteed one replay.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Priority", "pᵢ = |δᵢ| + ε", "Absolute TD error, ε keeps it non-zero."),
        FormulaEntry("Sample prob", "P(i) = pᵢ^α / Σ pⱼ^α", "α tunes how much prioritization applies."),
        FormulaEntry("IS weight", "wᵢ = (1/(N·P(i)))^β", "Corrects the sampling bias in the update."),
        FormulaEntry("Normalized weight", "wᵢ / max_j wⱼ", "Keeps the largest update at scale 1."),
        FormulaEntry("β schedule", "β: 0.4 → 1.0 over training", "Full correction only by the end."),
        FormulaEntry("Sum-tree query", "O(log N) per sample", "A segment tree over priorities makes proportional sampling cheap."),
    ),
    notationKey = listOf(
        NotationEntry("δ", "TD error of a transition"),
        NotationEntry("α", "prioritization strength (0 = uniform)"),
        NotationEntry("β", "importance-sampling correction, annealed to 1"),
        NotationEntry("sum tree", "segment tree of priorities supporting prefix-sum sampling"),
        NotationEntry("N", "number of transitions currently in the buffer"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Priority and IS weight",
            accentColor = 0xFF6366F1,
            code = """
                priority = (abs(td_error) + eps) ** alpha
                prob = priority / total_priority
                is_weight = (N * prob) ** (-beta)     # bias correction, applied to the loss
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Sum-tree buffer: O(log N) sampling",
            accentColor = 0xFF10B981,
            code = """
                class SumTree(private val capacity: Int) {
                    // Internal nodes hold the sum of their children; leaves hold priorities.
                    private val tree = DoubleArray(2 * capacity)
                    private var write = 0
                    var maxPriority = 1.0
                        private set

                    fun add(priority: Double) {
                        update(write, priority)
                        write = (write + 1) % capacity          // ring buffer
                    }

                    fun update(index: Int, priority: Double) {
                        maxPriority = maxOf(maxPriority, priority)
                        var node = index + capacity
                        val delta = priority - tree[node]
                        while (node >= 1) {                     // push the change up to the root
                            tree[node] += delta
                            node /= 2
                        }
                    }

                    // Walk down choosing the child whose subtree contains `value`.
                    fun sample(value: Double): Int {
                        var node = 1
                        var remaining = value
                        while (node < capacity) {
                            val left = 2 * node
                            if (remaining <= tree[left]) {
                                node = left
                            } else {
                                remaining -= tree[left]
                                node = left + 1
                            }
                        }
                        return node - capacity
                    }

                    val total: Double get() = tree[1]
                }

                // New transitions enter at max priority so they are guaranteed at least one replay.
                fun store(transition: Transition) {
                    buffer.add(transition)
                    tree.add(tree.maxPriority)
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RlTrainingPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF818CF8, "Faster Learning", "Focusing on informative transitions cuts the samples needed to learn."),
        ApplicationCard("chip", 0xFF60A5FA, "Rainbow Component", "One of the six ingredients combined in Rainbow DQN."),
        ApplicationCard("robot", 0xFF10B981, "Sparse-Reward Tasks", "Rare, high-error transitions get replayed instead of drowned out."),
        ApplicationCard("stack", 0xFFF59E0B, "Segment Trees in Practice", "The buffer is a working example of the same structure used for range queries in DSA."),
    ),
    takeaways = listOf(
        "Prioritized replay samples high-TD-error transitions more often to learn faster.",
        "Importance-sampling weights correct the bias non-uniform sampling introduces, annealed from β = 0.4 to 1.",
        "A sum-tree data structure makes proportional sampling efficient — O(log N) per draw and per update.",
        "New transitions must enter at max priority, or an unlucky first TD error buries them permanently.",
        "Too much prioritization overfits the buffer's noisiest transitions; α ≈ 0.6 is the usual compromise.",
        "It's a drop-in upgrade to uniform experience replay in off-policy agents.",
    ),
    crossLinks = listOf(
        CrossLink("experience_replay", "Experience Replay"),
        CrossLink("rainbow_dqn", "Rainbow DQN"),
        CrossLink("segment_tree", "Segment Tree"),
        CrossLink("dqn", "DQN"),
    ),
)
