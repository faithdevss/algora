package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val binarySearchTreeContent = TopicContent(
    topicId = "binary_search_tree",
    figure = Figure(
        caption = "The invariant is about whole subtrees, not neighbours: everything left of a node is " +
            "smaller, everything right is larger. That is what lets a search discard half the tree per " +
            "step — O(height), which is O(log n) only while the tree stays balanced. Insert 1,2,3,4,5 in " +
            "order and it degenerates into a linked list with O(n) lookups.",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("8", null, FigureTone.Accent),
                FigureNode("3", 0, FigureTone.Accent),
                FigureNode("12", 0, FigureTone.Muted),
                FigureNode("1", 1, FigureTone.Muted),
                FigureNode("6", 1, FigureTone.Accent),
                FigureNode("10", 2, FigureTone.Muted),
                FigureNode("14", 2, FigureTone.Muted),
                FigureNode("4", 4, FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A binary search tree (BST) is a binary tree where every node's left subtree holds smaller values and its right subtree holds larger values.",
        "That ordering invariant is what turns a tree shape into a fast search structure — at each node you know which side to descend into.",
    ),
    steps = listOf(
        StepCard(1, "Ordering Invariant", "left.value < node.value < right.value holds at every node, recursively.", 0xFF3B82F6),
        StepCard(2, "Search", "Compare the target to the current node and descend left or right — halving the remaining space each step, if balanced.", 0xFF8B5CF6),
        StepCard(3, "Insert", "Walk down as if searching for the value, then attach a new node where the search would have failed.", 0xFFF59E0B),
        StepCard(4, "Balance Matters", "An unbalanced BST (e.g. inserted in sorted order) degrades to a linked list — O(n) instead of O(log n).", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Search / Insert / Delete (balanced)", "O(log n)", "Each comparison eliminates roughly half the remaining tree."),
        FormulaEntry("Search / Insert / Delete (worst case)", "O(n)", "An unbalanced tree can degrade to a straight chain."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of nodes in the tree"),
        NotationEntry("h", "height of the tree — log₂n when balanced, n when degenerate"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Binary Search Tree — search & insert",
            accentColor = 0xFF6366F1,
            code = """
                class BstNode(var value: Int, var left: BstNode? = null, var right: BstNode? = null)

                fun insert(root: BstNode?, value: Int): BstNode {
                    if (root == null) return BstNode(value)
                    if (value < root.value) root.left = insert(root.left, value)
                    else if (value > root.value) root.right = insert(root.right, value)
                    return root
                }

                fun search(root: BstNode?, value: Int): Boolean {
                    var node = root
                    while (node != null) {
                        when {
                            value == node.value -> return true
                            value < node.value -> node = node.left
                            else -> node = node.right
                        }
                    }
                    return false
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("stack", 0xFF3B82F6, "Ordered Sets & Maps", "TreeSet/TreeMap-style structures use a balanced BST to keep keys sorted while staying O(log n)."),
        ApplicationCard("chip", 0xFF8B5CF6, "Database Indexes", "B-Trees (BST cousins) back most database indexes for fast range and equality lookups."),
        ApplicationCard("search", 0xFFF59E0B, "Autocomplete Ranges", "Range queries like 'all names between M and P' fall out naturally from in-order traversal."),
    ),
    takeaways = listOf(
        "A BST's speed comes from its ordering invariant, not from any special node type.",
        "Balanced BSTs give O(log n) search/insert/delete; unbalanced ones can degrade to O(n).",
        "Self-balancing variants (AVL, Red-Black) exist specifically to guarantee the balanced case.",
        "In-order traversal of a BST always yields values in sorted order.",
    ),
)
