package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Uses the standard 7-section template; cross-links to the Algorithms
// topic that implements the technique.
internal val treeBfsPatternContent = TopicContent(
    topicId = "tree_bfs_pattern",
    whatIsIt = listOf(
        "Tree BFS visits nodes level by level using a queue. The trick that makes it an interview pattern is snapshotting the queue's size at the top of each iteration — that count is exactly one level.",
        "Anything phrased in terms of depth (level averages, right-side view, minimum depth, zigzag order) is a BFS question, because depth is the order BFS produces for free.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "\"Level order\", \"per level\", \"shallowest\", \"right side view\" — the answer is grouped by depth.", 0xFFF59E0B),
        StepCard(2, "Seed the Queue", "Push the root (guard the empty tree). The queue always holds one contiguous frontier.", 0xFF3B82F6),
        StepCard(3, "Freeze the Level Size", "size = queue.size, then pop exactly that many — those nodes are the current level.", 0xFF8B5CF6),
        StepCard(4, "Enqueue Children", "Push each popped node's children; they form the next level. Repeat until the queue empties.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n)", "Every node is enqueued and dequeued once."),
        FormulaEntry("Space", "O(w)", "w = the widest level; for a balanced tree that's about n/2."),
        FormulaEntry("Minimum depth", "first leaf popped", "BFS reaches it before any deeper node — DFS would explore a long branch first."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of nodes"),
        NotationEntry("w", "maximum level width"),
        NotationEntry("size", "frozen queue length = nodes on this level"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Level order traversal (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                from collections import deque

                def level_order(root):
                    if not root:
                        return []
                    q, levels = deque([root]), []
                    while q:
                        size = len(q)                 # freeze before pushing children
                        level = []
                        for _ in range(size):
                            node = q.popleft()
                            level.append(node.val)
                            if node.left:
                                q.append(node.left)
                            if node.right:
                                q.append(node.right)
                        levels.append(level)
                    return levels
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("tree", 0xFF3B82F6, "Level-Grouped Output", "Level averages, zigzag order, right-side view, level-order serialisation."),
        ApplicationCard("network", 0xFF10B981, "Shortest Hops", "Minimum depth, and the unweighted shortest path once the tree becomes a graph."),
        ApplicationCard("chip", 0xFF8B5CF6, "UI & Org Trees", "Rendering a hierarchy tier by tier, or expanding a menu one depth at a time."),
    ),
    takeaways = listOf(
        "Freeze the queue size per iteration — that's how levels stay separated.",
        "Peak memory is the widest level, not the height; a wide tree is the expensive case.",
        "BFS finds the shallowest match first; DFS does not.",
        "Zigzag is the same traversal with the level reversed on alternate depths, not a new algorithm.",
    ),
    crossLinks = listOf(
        CrossLink("bfs", "Breadth-First Search (Algorithms)"),
        CrossLink("queue", "Queue (Data Structures)"),
        CrossLink("tree_dfs_pattern", "Tree DFS (Path Sum)"),
    ),
)
