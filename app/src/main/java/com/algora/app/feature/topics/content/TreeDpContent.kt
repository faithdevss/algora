package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val treeDpContent = TopicContent(
    topicId = "tree_dp",
    whatIsIt = listOf(
        "Tree DP is dynamic programming where the subproblems are subtrees: each node's answer is assembled from its children's answers during a post-order traversal.",
        "Because a tree has no cycles, every subtree is solved exactly once and the whole DP costs O(n). The recurring pattern is a small state per node — most often \"best answer if this node is used\" versus \"best if it is not\".",
    ),
    steps = listOf(
        StepCard(1, "Root the Tree", "Pick any root; parent/child direction is what turns the graph into subproblems.", 0xFF3B82F6),
        StepCard(2, "Define the State", "dp[v][s] = the answer for v's subtree given that v is in state s (taken / not taken, coloured, matched…).", 0xFF10B981),
        StepCard(3, "Recurse Post-Order", "Solve every child before the parent — the parent's transition reads only finished values.", 0xFFF59E0B),
        StepCard(4, "Combine the Children", "Sum or max over children according to what the parent's state permits.", 0xFF8B5CF6),
        StepCard(5, "Read the Root", "The overall answer is the best state at the root — or, for path problems, a maximum tracked during the merge.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Max independent set", "dp[v][1] = w(v) + Σ dp[c][0];  dp[v][0] = Σ max(dp[c][0], dp[c][1])", "Taking v forbids taking any child."),
        FormulaEntry("Subtree size", "size[v] = 1 + Σ size[c]", "The simplest tree DP there is."),
        FormulaEntry("Tree diameter", "answer = max over v of (h₁ + h₂)", "The two deepest child heights meeting at v."),
        FormulaEntry("Time", "O(n · states)", "Each edge contributes to exactly one merge."),
    ),
    notationKey = listOf(
        NotationEntry("dp[v][s]", "best value for the subtree rooted at v with v in state s"),
        NotationEntry("c", "a child of v"),
        NotationEntry("post-order", "children finished before the parent is computed"),
        NotationEntry("rerooting", "a second pass that computes answers for every root in O(n)"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Maximum-weight independent set on a tree",
            accentColor = 0xFF6366F1,
            code = """
                // dp[v][0] = best for v's subtree with v NOT taken
                // dp[v][1] = best for v's subtree with v taken
                fun maxWeightIndependentSet(n: Int, adj: Array<List<Int>>, weight: IntArray): Int {
                    val dp = Array(n) { IntArray(2) }

                    fun dfs(u: Int, parent: Int) {
                        dp[u][1] = weight[u]
                        for (v in adj[u]) {
                            if (v == parent) continue
                            dfs(v, u)
                            dp[u][0] += maxOf(dp[v][0], dp[v][1])   // child is free to do either
                            dp[u][1] += dp[v][0]                    // u taken => children cannot be
                        }
                    }

                    dfs(0, -1)
                    return maxOf(dp[0][0], dp[0][1])
                }
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Diameter in one post-order pass",
            accentColor = 0xFF10B981,
            code = """
                fun diameter(n: Int, adj: Array<List<Int>>): Int {
                    var best = 0

                    // Returns the height of u's subtree in edges.
                    fun height(u: Int, parent: Int): Int {
                        var first = 0
                        var second = 0                     // two deepest child heights
                        for (v in adj[u]) {
                            if (v == parent) continue
                            val h = height(v, u) + 1
                            if (h > first) { second = first; first = h } else if (h > second) second = h
                        }
                        best = maxOf(best, first + second) // longest path bending at u
                        return first
                    }

                    height(0, -1)
                    return best
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("share", 0xFF3B82F6, "Network Placement", "Choosing servers or sensors so no two neighbours are both selected is a max independent set on a tree."),
        ApplicationCard("browser", 0xFF10B981, "UI Layout", "Measure passes size a view tree bottom-up, which is exactly a tree DP over subtree extents."),
        ApplicationCard("chip", 0xFFF59E0B, "Compilers", "Optimal register allocation and instruction selection on expression trees are classic tree DPs."),
    ),
    takeaways = listOf(
        "Subtrees are the subproblems; post-order is what guarantees the children are ready.",
        "Most tree DPs need only a two-state \"taken / not taken\" per node.",
        "Path problems are solved by combining the two best child branches at each node.",
        "When every node must serve as the root, use rerooting to keep the total at O(n) instead of O(n²).",
    ),
    crossLinks = listOf(
        CrossLink("tree", "Tree"),
        CrossLink("dfs", "Depth-First Search (DFS)"),
        CrossLink("lca", "Lowest Common Ancestor"),
    ),
)
