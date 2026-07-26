package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val avlRedBlackTreeContent = TopicContent(
    topicId = "avl_red_black_tree",
    whatIsIt = listOf(
        "AVL and red-black trees are self-balancing binary search trees: they rearrange themselves during inserts and deletes so their height stays O(log n), preventing the degenerate chains a plain BST can become.",
        "AVL trees keep stricter balance (faster lookups), while red-black trees balance more loosely (faster updates) — the tradeoff behind why each is chosen where it is.",
    ),
    steps = listOf(
        StepCard(1, "Insert Like a BST", "Place the new key by ordinary binary-search-tree descent.", 0xFF10B981),
        StepCard(2, "Detect Imbalance", "AVL checks per-node height differences; red-black checks color rules along the path.", 0xFF3B82F6),
        StepCard(3, "Rotate to Rebalance", "Single or double rotations restructure the offending subtree in O(1) each.", 0xFFF59E0B),
        StepCard(4, "Restore Invariants", "AVL keeps balance factors in {−1,0,1}; red-black repaints and rotates to satisfy its color properties.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Search / insert / delete", "O(log n)", "Guaranteed by the height bound."),
        FormulaEntry("AVL balance factor", "|h(left) − h(right)| ≤ 1", "Strict height balance."),
        FormulaEntry("Red-black height", "≤ 2·log₂(n+1)", "Looser balance, fewer rotations on update."),
    ),
    notationKey = listOf(
        NotationEntry("balance factor", "left-height minus right-height (AVL)"),
        NotationEntry("rotation", "O(1) local restructuring that preserves BST order"),
        NotationEntry("color", "red/black label enforcing red-black balance"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "AVL right rotation",
            accentColor = 0xFF6366F1,
            code = """
                // Rotate right around y to fix a left-heavy imbalance.
                fun rotateRight(y: Node): Node {
                    val x = y.left!!
                    y.left = x.right
                    x.right = y
                    y.height = 1 + maxOf(height(y.left), height(y.right))
                    x.height = 1 + maxOf(height(x.left), height(x.right))
                    return x                 // x is the new subtree root
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Language Ordered Maps", "Red-black trees back Java's TreeMap and C++'s std::map for sorted key-value storage."),
        ApplicationCard("history", 0xFF3B82F6, "Kernel Schedulers", "The Linux CFS scheduler tracks runnable tasks in a red-black tree."),
        ApplicationCard("search", 0xFFF59E0B, "Read-Heavy Indexes", "AVL's tighter balance favors workloads dominated by lookups."),
    ),
    takeaways = listOf(
        "Self-balancing BSTs guarantee O(log n) by keeping height logarithmic via rotations.",
        "AVL is more rigidly balanced (faster search); red-black rotates less (faster insert/delete).",
        "Both keep sorted order, unlike a hash table — enabling range and successor queries.",
        "Red-black trees are the common choice inside standard-library ordered maps and sets.",
    ),
    crossLinks = listOf(
        CrossLink("binary_search_tree", "Binary Search Tree"),
        CrossLink("skip_list", "Skip List"),
    ),
)
