package com.algora.app.feature.topics

// ── Association rule mining ──────────────────────────────────────────────────
// The shared basket database and the three algorithms behind the B7 Association Rules labs. All
// three find the *same* frequent itemsets — that is the point of running them on one database — and
// differ only in how they get there: Apriori by breadth-first candidate generation over a horizontal
// layout, Eclat by depth-first tid-list intersection over a vertical one, FP-Growth by building a
// compressed tree and mining it recursively.
//
// The database is small enough to check by hand and shaped so that every claim the labs make is
// actually exercised: one item falls below minimum support (so the pruning has something to prune),
// one 3-item candidate is eliminated by downward closure *without* a counting pass (which is the
// argument for the Apriori property), and one frequent rule has high confidence and lift below 1
// (which is the argument for not shipping confidence alone).

internal val basketItems = listOf("A", "B", "C", "D", "E")

/** What the single letters stand for, for the copy that needs to sound like a shop. */
internal val basketNames = mapOf(
    "A" to "bread", "B" to "milk", "C" to "eggs", "D" to "beer", "E" to "cereal",
)

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

internal fun tidList(item: String): List<Int> =
    transactions.indices.filter { item in transactions[it] }

internal fun itemsetLabel(itemset: Collection<String>): String = itemset.sorted().joinToString("")

// ── Apriori ──────────────────────────────────────────────────────────────────

internal class AprioriCandidate(
    val itemset: Set<String>,
    val support: Int,
    /** Non-null when downward closure killed it before any transaction was read. */
    val prunedBySubset: Set<String>? = null,
) {
    val frequent: Boolean get() = prunedBySubset == null && support >= MinSupport
    val label: String get() = itemsetLabel(itemset)
}

internal class AprioriPass(
    val k: Int,
    val candidates: List<AprioriCandidate>,
    /** Candidates that survived to be counted — the database passes this level actually cost. */
    val counted: Int,
) {
    val frequent: List<AprioriCandidate> get() = candidates.filter { it.frequent }
}

/**
 * Fₖ₋₁ × Fₖ₋₁ candidate generation: join two frequent (k−1)-itemsets that share their first k−2
 * items, then discard any candidate with an infrequent (k−1)-subset. That second step is the Apriori
 * property doing work — it removes candidates without touching the database.
 */
internal fun aprioriPasses(): List<AprioriPass> {
    val passes = mutableListOf<AprioriPass>()

    val level1 = basketItems.map { AprioriCandidate(setOf(it), support(setOf(it))) }
    passes += AprioriPass(1, level1, level1.size)

    var frequent = level1.filter { it.frequent }.map { it.itemset }
    var k = 2
    while (frequent.size >= 2) {
        val sortedSets = frequent.map { it.sorted() }
        val candidates = mutableListOf<AprioriCandidate>()
        val seen = mutableSetOf<Set<String>>()
        for (i in sortedSets.indices) {
            for (j in i + 1 until sortedSets.size) {
                val a = sortedSets[i]
                val b = sortedSets[j]
                if (a.dropLast(1) != b.dropLast(1)) continue
                val joined = (a + b).toSortedSet().toSet()
                if (joined.size != k || !seen.add(joined)) continue

                val badSubset = joined
                    .map { joined - it }
                    .firstOrNull { subset -> subset !in frequent.map { it.toSet() } }
                candidates += if (badSubset != null) {
                    AprioriCandidate(joined, 0, prunedBySubset = badSubset)
                } else {
                    AprioriCandidate(joined, support(joined))
                }
            }
        }
        if (candidates.isEmpty()) break
        passes += AprioriPass(k, candidates, candidates.count { it.prunedBySubset == null })
        frequent = candidates.filter { it.frequent }.map { it.itemset }
        k++
    }
    return passes
}

// ── Eclat ────────────────────────────────────────────────────────────────────

internal class EclatStep(
    val itemset: Set<String>,
    val tids: List<Int>,
    /** The two tid-lists that were intersected to produce this one; null for the 1-itemsets. */
    val from: Pair<Set<String>, Set<String>>? = null,
    val depth: Int,
) {
    val frequent: Boolean get() = tids.size >= MinSupport
    val label: String get() = itemsetLabel(itemset)
}

/**
 * Depth-first mining over the vertical layout. Support is `tids.size` — no counting pass, because
 * the intersection *is* the count. That is the whole trade against Apriori: memory for the tid-lists
 * in exchange for never scanning the database again after the first pass.
 */
internal fun eclatSteps(): List<EclatStep> {
    val steps = mutableListOf<EclatStep>()
    val roots = basketItems
        .map { it to tidList(it) }
        .filter { it.second.size >= MinSupport }
        .sortedByDescending { it.second.size }

    roots.forEach { (item, tids) -> steps += EclatStep(setOf(item), tids, depth = 0) }

    fun extend(prefix: Set<String>, prefixTids: List<Int>, rest: List<Pair<String, List<Int>>>, depth: Int) {
        rest.forEachIndexed { index, (item, tids) ->
            val merged = prefixTids.filter { it in tids }
            val itemset = prefix + item
            steps += EclatStep(itemset, merged, prefix to setOf(item), depth)
            if (merged.size >= MinSupport) {
                extend(itemset, merged, rest.drop(index + 1), depth + 1)
            }
        }
    }

    roots.forEachIndexed { index, (item, tids) ->
        extend(setOf(item), tids, roots.drop(index + 1), 1)
    }
    return steps
}

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

/** Every rule derivable from the frequent itemsets of size ≥ 2, ordered by lift. */
internal fun allRules(): List<AssociationRule> {
    val frequent = aprioriPasses().flatMap { pass -> pass.frequent.map { it.itemset } }.filter { it.size >= 2 }
    return frequent.flatMap { itemset ->
        itemset.map { consequent -> associationRule(itemset - consequent, setOf(consequent)) }
    }.sortedByDescending { it.lift }
}
