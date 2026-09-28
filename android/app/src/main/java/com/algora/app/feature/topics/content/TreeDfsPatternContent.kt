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

// Interview-prep pattern guide. Uses the standard 7-section template; cross-links to the Algorithms
// topic that implements the technique.
internal val treeDfsPatternContent = TopicContent(
    topicId = "tree_dfs_pattern",
    figure = Figure(
        caption = "State travels *down* as an argument — the budget left after subtracting this node — " +
            "and the path is popped on the way back up, so siblings never inherit each other's state. " +
            "5 → 4 → 11 → 2 spends exactly 22.",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("5", null, FigureTone.Accent),
                FigureNode("4", 0, FigureTone.Accent),
                FigureNode("8", 0),
                FigureNode("11", 1, FigureTone.Accent),
                FigureNode("13", 2),
                FigureNode("4", 2),
                FigureNode("7", 3, FigureTone.Warn),
                FigureNode("2", 3, FigureTone.Accent),
                FigureNode("1", 5),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Tree DFS follows one root-to-leaf path to its end before backtracking. The recursion carries state down (a running sum, the path so far) and returns answers up (a height, a best value).",
        "Most tree questions are really about which direction the information flows. \"Does a path with sum X exist?\" pushes down; \"what is the diameter?\" pulls up; path-printing questions need both plus an un-choose on the way out.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Root-to-leaf paths, subtree properties, or anything needing a value computed from both children.", 0xFFF59E0B),
        StepCard(2, "Decide the Direction", "Down: pass accumulated state as an argument. Up: return the subtree's answer and combine at the parent.", 0xFF3B82F6),
        StepCard(3, "Recurse Both Children", "Handle the null child as the base case; it's where the wrong answer usually hides.", 0xFF8B5CF6),
        StepCard(4, "Un-choose on Exit", "When collecting paths, pop the node after both recursions so siblings see a clean path.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n)", "Each node is entered once; work per node is O(1) if you avoid copying."),
        FormulaEntry("Space", "O(h)", "Call stack depth = tree height: log n balanced, n degenerate."),
        FormulaEntry("Path collection", "O(n · h)", "Copying each completed path out dominates."),
    ),
    notationKey = listOf(
        NotationEntry("h", "height of the tree"),
        NotationEntry("running", "state pushed down, e.g. target − values so far"),
        NotationEntry("path", "the current root-to-node list, mutated in place"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "All root-to-leaf paths hitting a target (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def path_sum(root, target):
                    out, path = [], []

                    def walk(node, remaining):
                        if not node:
                            return
                        path.append(node.val)
                        remaining -= node.val
                        if not node.left and not node.right and remaining == 0:
                            out.append(path[:])       # leaf and budget exactly spent
                        else:
                            walk(node.left, remaining)
                            walk(node.right, remaining)
                        path.pop()                    # un-choose

                    walk(root, target)
                    return out
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("tree", 0xFF3B82F6, "Path Questions", "Path sum, longest path, sum of root-to-leaf numbers."),
        ApplicationCard("check", 0xFF10B981, "Subtree Properties", "Height, balance check, diameter, validating a BST's range invariant."),
        ApplicationCard("code", 0xFF8B5CF6, "Structure Serialisation", "Pre-order serialise/deserialise and tree-shaped expression evaluation."),
    ),
    takeaways = listOf(
        "Ask first whether state flows down (arguments) or up (return values) — the code follows.",
        "Null is a base case, not an error; leaf means both children are null.",
        "Stack depth is the height, so a skewed tree is the O(n)-space case.",
        "Mutate one shared path list and pop on exit rather than copying at every node.",
    ),
    crossLinks = listOf(
        CrossLink("dfs", "Depth-First Search (Algorithms)"),
        CrossLink("tree", "Tree (Data Structures)"),
        CrossLink("tree_bfs_pattern", "Tree BFS (Level Order)"),
    ),
)
