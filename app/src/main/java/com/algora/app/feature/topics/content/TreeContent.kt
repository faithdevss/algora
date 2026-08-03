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

internal val treeContent = TopicContent(
    topicId = "tree",
    figure = Figure(
        caption = "One parent per node and no cycles, which is the whole definition — and it forces the " +
            "edge count to be exactly n − 1. Everything else is vocabulary: depth counts down from the " +
            "root, height counts up from the deepest leaf, and a node's subtree is a tree in its own right.",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("A", null, FigureTone.Accent),
                FigureNode("B", 0, FigureTone.Primary),
                FigureNode("C", 0, FigureTone.Primary),
                FigureNode("D", 1),
                FigureNode("E", 1),
                FigureNode("F", 2),
                FigureNode("G", 4),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A tree is a hierarchical structure of nodes: one root at the top, and every other node linked to exactly one parent, forming branches down to leaves.",
        "Trees have no cycles — there is exactly one path between any two nodes — which is what separates a tree from a general graph.",
    ),
    steps = listOf(
        StepCard(1, "Root & Children", "Start from the root; each node points to zero or more child nodes.", 0xFF10B981),
        StepCard(2, "Depth & Height", "Depth counts edges from the root down; height counts edges from a node to its deepest leaf.", 0xFF3B82F6),
        StepCard(3, "Traversal", "Visit nodes in a defined order — pre/in/post-order (DFS) or level-order (BFS).", 0xFFF59E0B),
        StepCard(4, "Recursion", "Most tree operations are naturally recursive: solve for the children, then combine at the parent.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Traversal", "O(n)", "Every node is visited exactly once."),
        FormulaEntry("Height (balanced)", "O(log n)", "A balanced tree of n nodes is about log n levels tall."),
        FormulaEntry("Height (degenerate)", "O(n)", "A tree shaped like a chain is as tall as it is large."),
    ),
    notationKey = listOf(
        NotationEntry("root", "the single top node with no parent"),
        NotationEntry("leaf", "a node with no children"),
        NotationEntry("n", "total number of nodes"),
        NotationEntry("h", "height — longest root-to-leaf path"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Node and recursive traversal",
            accentColor = 0xFF6366F1,
            code = """
                class TreeNode<T>(val value: T) {
                    val children = mutableListOf<TreeNode<T>>()
                }

                // Depth-first pre-order: visit a node, then recurse into each child.
                fun <T> visit(node: TreeNode<T>, action: (T) -> Unit) {
                    action(node.value)
                    for (child in node.children) visit(child, action)
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("map", 0xFF10B981, "File Systems", "Folders and files form a tree: each directory is a node whose children are its contents."),
        ApplicationCard("browser", 0xFF3B82F6, "DOM & UI Trees", "An HTML page is a tree of nested elements; UI frameworks render and diff that hierarchy."),
        ApplicationCard("share", 0xFFF59E0B, "Org Charts & Taxonomies", "Any parent-child hierarchy — company reporting lines, biological classification — is a tree."),
    ),
    takeaways = listOf(
        "A tree is an acyclic hierarchy: one root, one parent per node, exactly one path between any two nodes.",
        "Traversal touches every node once (O(n)); most tree logic is naturally recursive.",
        "A balanced tree is ~log n tall, which is what makes tree-based lookups fast.",
        "Let a tree degenerate into a chain and its height — and its operations — slide back to O(n).",
    ),
    crossLinks = listOf(
        CrossLink("binary_search_tree", "Binary Search Tree"),
        CrossLink("graph", "Graph"),
    ),
)
