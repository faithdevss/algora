package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. One fact — in-order on a BST is sorted — plus the iterative
// traversal that lets you stop early or stream the result.
internal val bstInorderPatternContent = TopicContent(
    topicId = "bst_inorder_pattern",
    whatIsIt = listOf(
        "In-order traversal of a binary search tree emits its keys in sorted order. That single fact answers validation, k-th smallest, closest value, successor and range queries — each becomes a question about a sorted sequence you never have to materialise.",
        "Doing it iteratively with an explicit stack adds the ability to stop early and to resume: the same loop body powers a BST iterator with O(height) memory rather than O(n).",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "A BST plus an order question — k-th smallest, validate, successor, count within a range, or convert to a sorted list.", 0xFFF59E0B),
        StepCard(2, "Push the Left Spine", "From the current node, push it and every left child. The stack top is always the next key in sorted order.", 0xFF3B82F6),
        StepCard(3, "Visit, then Go Right", "Pop and emit, then move to the popped node's right child and push its left spine. Each node is pushed and popped once.", 0xFFEF4444),
        StepCard(4, "Prune with the Invariant", "For validation, carry (low, high) bounds down — comparing only against the parent misses violations two levels up. For range queries, skip subtrees that fall outside.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Traversal", "O(n) time, O(height) space", "Balanced: O(log n) stack. Skewed: O(n)."),
        FormulaEntry("k-th smallest", "stop after k pops", "O(height + k), not O(n) — the reason to go iterative."),
        FormulaEntry("Validation bound", "low < node.val < high", "Bounds tighten as you descend; parent-only comparison is wrong."),
    ),
    notationKey = listOf(
        NotationEntry("left spine", "the chain of leftmost descendants from a node"),
        NotationEntry("(low, high)", "open interval a subtree's keys must lie in"),
        NotationEntry("successor", "next key in sorted order after a given node"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Iterative in-order and validation (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def kth_smallest(root, k):
                    stack, node = [], root
                    while stack or node:
                        while node:                        # push the left spine
                            stack.append(node)
                            node = node.left
                        node = stack.pop()
                        k -= 1
                        if k == 0:
                            return node.val                # stop early — no full traversal
                        node = node.right
                    return None

                def is_valid_bst(root, low=float('-inf'), high=float('inf')):
                    if not root:
                        return True
                    if not low < root.val < high:
                        return False                       # bounds, not just the parent
                    return (is_valid_bst(root.left, low, root.val)
                            and is_valid_bst(root.right, root.val, high))
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("chart", 0xFF3B82F6, "Ordered Queries", "Rank, range counts and nearest-key lookups over a live ordered set."),
        ApplicationCard("browser", 0xFF10B981, "Streaming Iterators", "A BST iterator exposing next()/hasNext() in O(height) memory."),
        ApplicationCard("check", 0xFF8B5CF6, "Structure Validation", "Confirming a tree really satisfies the BST invariant after edits."),
    ),
    takeaways = listOf(
        "In-order on a BST is sorted — turn the tree question into a sorted-sequence question.",
        "Validate with descending (low, high) bounds; comparing against the parent alone accepts invalid trees.",
        "The iterative form can stop early and resume, which recursion cannot do cheaply.",
        "All of it degrades to O(n) on a skewed tree — that is what balancing buys.",
    ),
    crossLinks = listOf(
        CrossLink("binary_search_tree", "Binary Search Tree (Data Structures)"),
        CrossLink("avl_red_black_tree", "AVL / Red-Black Tree (Data Structures)"),
        CrossLink("tree_dfs_pattern", "Tree DFS (Path Sum)"),
    ),
)
