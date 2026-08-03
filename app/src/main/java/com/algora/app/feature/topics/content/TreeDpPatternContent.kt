package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Post-order aggregation: each node returns what its parent needs,
// while the global answer is updated on the way up.
internal val treeDpPatternContent = TopicContent(
    topicId = "tree_dp_pattern",
    figure = Figure(
        caption = "Each node returns its longest *downward* path, but the best path *through* it joins " +
            "two children and is only recorded — a parent cannot use a path that bends here. Confusing " +
            "the returned value with the recorded one is the classic bug.",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("5 ↓4", null, FigureTone.Primary),
                FigureNode("4 ↓3", 0, FigureTone.Accent),
                FigureNode("8 ↓3", 0, FigureTone.Accent),
                FigureNode("11 ↓2", 1, FigureTone.Accent),
                FigureNode("13 ↓1", 2),
                FigureNode("4 ↓2", 2, FigureTone.Accent),
                FigureNode("7 ↓1", 3, FigureTone.Accent),
                FigureNode("2 ↓1", 3),
                FigureNode("1 ↓1", 5, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Tree DP computes each node's answer from its children in one post-order pass. The recursion returns what the *parent* needs, while the answer that involves the whole subtree is recorded on the way up.",
        "Diameter is the canonical example: a node returns its longest downward path, but the best path *through* it — left + right — is combined locally and never returned. Confusing those two values is the single most common bug.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "A best/count question over a tree where a node's answer depends on its subtrees — diameter, house robber on a tree, subtree sums, independent sets.", 0xFFF59E0B),
        StepCard(2, "Split Return from Record", "Decide what the parent can legally use (a single downward path, or a per-state pair) versus what only the current node can combine.", 0xFF3B82F6),
        StepCard(3, "Recurse Post-order", "Compute all children first, then combine. Nothing about a node is knowable before its subtrees are done.", 0xFFEF4444),
        StepCard(4, "Return a State Tuple", "When a choice constrains the parent — taken versus not taken — return both values so the parent picks correctly.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time / Space", "O(n) / O(height)", "Each node visited once; the stack is the tree's height."),
        FormulaEntry("Diameter", "best = max(best, down[l] + down[r]); return 1 + max(down[l], down[r])", "Combine locally, return only one branch."),
        FormulaEntry("Tree robber", "take = v + skip_l + skip_r; skip = max(take_l, skip_l) + max(take_r, skip_r)", "Two states per node."),
    ),
    notationKey = listOf(
        NotationEntry("down[v]", "best value along a single downward path from v"),
        NotationEntry("(take, skip)", "per-node state pair returned to the parent"),
        NotationEntry("post-order", "children fully processed before the node"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Diameter and tree robber (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def diameter(root):
                    best = 0
                    def down(node):                        # longest downward path from node
                        nonlocal best
                        if not node:
                            return 0
                        l, r = down(node.left), down(node.right)
                        best = max(best, l + r)            # path through node — never returned
                        return 1 + max(l, r)               # parent can use only one branch
                    down(root)
                    return best

                def rob_tree(root):
                    def solve(node):                       # (rob this node, skip this node)
                        if not node:
                            return 0, 0
                        lt, ls = solve(node.left)
                        rt, rs = solve(node.right)
                        take = node.val + ls + rs          # children must be skipped
                        skip = max(lt, ls) + max(rt, rs)
                        return take, skip
                    return max(solve(root))
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("tree", 0xFF3B82F6, "Network Diameter", "Longest hop distance in a tree-shaped topology or org chart."),
        ApplicationCard("users", 0xFF10B981, "Constrained Selection", "Maximum-weight independent set: employees with no direct manager also chosen."),
        ApplicationCard("chart", 0xFF8B5CF6, "Subtree Aggregates", "Rolling up sizes, sums and counts for reporting or rendering."),
    ),
    takeaways = listOf(
        "Post-order or nothing: a node's answer needs its children first.",
        "Separate the value returned to the parent from the value combined locally.",
        "Return a state tuple when the choice constrains the parent — it beats a second traversal.",
        "Deep skewed trees blow the stack; convert to an explicit stack or iterative post-order when n is large.",
    ),
    crossLinks = listOf(
        CrossLink("tree_dp", "Tree DP (Algorithms)"),
        CrossLink("tree_dfs_pattern", "Tree DFS (Path Sum)"),
        CrossLink("lca", "Lowest Common Ancestor (Algorithms)"),
    ),
)
