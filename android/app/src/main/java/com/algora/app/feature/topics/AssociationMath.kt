package com.algora.app.feature.topics

// ── Association rule mining ──────────────────────────────────────────────────
// The shared basket database and the FP-tree behind the association-rule storyboards
// (SeriesStoryLabs). The database is small enough to check by hand and shaped so that one item falls
// below minimum support and one 3-item candidate is pruned by downward closure.

internal val basketItems = listOf("A", "B", "C", "D", "E")

internal val transactions: List<Set<String>> = listOf(
    setOf("A", "B", "C"),
    setOf("A", "B"),
    setOf("A", "B", "D"),
    setOf("B", "C"),
    setOf("A", "B", "C", "E"),
    setOf("D", "E"),
    setOf("A", "B", "C"),
    setOf("B", "C", "E"),
    setOf("B", "E"),
    setOf("A", "B"),
)

/** Absolute minimum support. 3 of 10 transactions — chosen so exactly one 1-itemset falls below it. */
internal const val MinSupport = 3

internal fun support(itemset: Set<String>): Int = transactions.count { it.containsAll(itemset) }

internal fun itemsetLabel(itemset: Collection<String>): String = itemset.sorted().joinToString("")

// ── FP-Growth ────────────────────────────────────────────────────────────────

/** Frequent items, most frequent first — the order the tree is built in and the reason it compresses. */
internal fun fpItemOrder(): List<String> =
    basketItems
        .map { it to support(setOf(it)) }
        .filter { it.second >= MinSupport }
        .sortedByDescending { it.second }
        .map { it.first }

/** A transaction with infrequent items dropped and the rest sorted into the header order. */
internal fun fpSortedTransaction(transaction: Set<String>): List<String> {
    val order = fpItemOrder()
    return order.filter { it in transaction }
}

internal class FpNode(
    val id: Int,
    val item: String,
    val parent: Int?,
    var count: Int,
)

internal class FpTree(
    val nodes: List<FpNode>,
    /** item → the node ids holding it, in insertion order. The header table's linked list. */
    val header: Map<String, List<Int>>,
)

internal fun buildFpTree(upTo: Int = transactions.size): FpTree {
    val nodes = mutableListOf<FpNode>()
    val children = mutableMapOf<Pair<Int?, String>, Int>()
    val header = linkedMapOf<String, MutableList<Int>>()

    transactions.take(upTo).forEach { transaction ->
        var parent: Int? = null
        fpSortedTransaction(transaction).forEach { item ->
            val key = parent to item
            val existing = children[key]
            if (existing != null) {
                nodes[existing].count++
                parent = existing
            } else {
                val node = FpNode(nodes.size, item, parent, 1)
                nodes += node
                children[key] = node.id
                header.getOrPut(item) { mutableListOf() }.add(node.id)
                parent = node.id
            }
        }
    }
    return FpTree(nodes, header)
}

internal class ConditionalPattern(val path: List<String>, val count: Int)

/**
 * The conditional pattern base for one item: every root-ward path above a node holding it, weighted
 * by that node's count. Mining the item's own tree over these is the recursive step.
 */
internal fun conditionalPatternBase(tree: FpTree, item: String): List<ConditionalPattern> =
    tree.header[item].orEmpty().map { id ->
        val path = mutableListOf<String>()
        var current = tree.nodes[id].parent
        while (current != null) {
            path += tree.nodes[current].item
            current = tree.nodes[current].parent
        }
        ConditionalPattern(path.reversed(), tree.nodes[id].count)
    }

// ── Rules ────────────────────────────────────────────────────────────────────

internal class AssociationRule(
    val antecedent: Set<String>,
    val consequent: Set<String>,
    val support: Int,
    val confidence: Double,
    val lift: Double,
) {
    val label: String get() = "${itemsetLabel(antecedent)} → ${itemsetLabel(consequent)}"
}

internal fun associationRule(antecedent: Set<String>, consequent: Set<String>): AssociationRule {
    val n = transactions.size.toDouble()
    val both = support(antecedent + consequent)
    val confidence = both.toDouble() / support(antecedent)
    val lift = confidence / (support(consequent) / n)
    return AssociationRule(antecedent, consequent, both, confidence, lift)
}
