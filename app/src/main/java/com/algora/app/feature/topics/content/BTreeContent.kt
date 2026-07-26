package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bTreeContent = TopicContent(
    topicId = "b_tree",
    whatIsIt = listOf(
        "A B-tree is a balanced multi-way search tree where each node holds many keys and has many children, keeping the tree short and wide to minimize slow disk reads.",
        "Its B+ variant stores all data in the leaves and links them in a list, making it the backbone of databases and filesystems where each node maps to a disk block.",
    ),
    steps = listOf(
        StepCard(1, "Many Keys per Node", "A node of order m holds up to m−1 sorted keys and up to m child pointers.", 0xFF10B981),
        StepCard(2, "Search by Range", "Within a node, binary-search the keys to pick which child subtree to descend into.", 0xFF3B82F6),
        StepCard(3, "Split on Overflow", "Inserting into a full node splits it, pushing the median key up to the parent.", 0xFFF59E0B),
        StepCard(4, "Merge on Underflow", "Deletion borrows from a sibling or merges nodes to keep each at least half full.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Height", "O(log_m n)", "High branching factor m makes the tree very shallow."),
        FormulaEntry("Search / insert / delete", "O(log n)", "With few node accesses — the disk-cost that matters."),
        FormulaEntry("Fill invariant", "≥ ⌈m/2⌉−1 keys", "Every non-root node stays at least half full."),
    ),
    notationKey = listOf(
        NotationEntry("m", "order — max children per node"),
        NotationEntry("node = block", "one node sized to a disk/page block"),
        NotationEntry("B+ leaves", "linked leaf list for fast range scans"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "B-tree node shape",
            accentColor = 0xFF6366F1,
            code = """
                // Order-m node: keys sorted, children one more than keys (unless leaf).
                class BTreeNode(val order: Int, val isLeaf: Boolean) {
                    val keys = ArrayList<Int>()            // up to order - 1
                    val children = ArrayList<BTreeNode>()  // up to order

                    fun isFull() = keys.size == order - 1   // triggers a split
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "Database Indexes", "B+ trees are the default index structure in MySQL, PostgreSQL, and most RDBMSs."),
        ApplicationCard("map", 0xFF3B82F6, "Filesystems", "NTFS, HFS+, and ext4 store directory and file metadata in B-tree variants."),
        ApplicationCard("search", 0xFFF59E0B, "Range Scans", "Linked B+ leaves make 'all keys between X and Y' a fast sequential read."),
    ),
    takeaways = listOf(
        "B-trees stay short and wide, minimizing the disk/page accesses that dominate cost.",
        "High branching factor gives O(log_m n) height — far shallower than a binary tree.",
        "Splits and merges keep every node at least half full, bounding the height.",
        "B+ trees, with data in linked leaves, power database indexes and filesystems.",
    ),
    crossLinks = listOf(
        CrossLink("binary_search_tree", "Binary Search Tree"),
        CrossLink("avl_red_black_tree", "AVL / Red-Black Tree"),
    ),
)
