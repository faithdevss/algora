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

internal val fpGrowthContent = TopicContent(
    topicId = "fp_growth",
    figure = Figure(
        caption = "The lab's ten baskets, complete, as one tree. D is dropped first — support 2 " +
            "against a threshold of 3 — which takes 26 item slots down to 24, and those 24 are " +
            "held in 8 nodes. B occurs in 9 of the 10 baskets and is a single node carrying a " +
            "counter, which is the compression: descending-frequency order puts the common items " +
            "at the front, so the baskets share prefixes and the prefixes share nodes. The two " +
            "highlighted C nodes are the header chain for C: two nodes, five occurrences, because " +
            "a chain's length counts distinct contexts and not support. Reading upward from them " +
            "gives C's conditional pattern base directly — BA×3 and B×2 — with no candidate " +
            "generated and no pass over the database.",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("root", null),
                FigureNode("B:9", 0, FigureTone.Primary),
                FigureNode("E:1", 0),
                FigureNode("A:6", 1, FigureTone.Primary),
                FigureNode("C:2", 1, FigureTone.Accent),
                FigureNode("E:1", 1),
                FigureNode("C:3", 3, FigureTone.Accent),
                FigureNode("E:1", 4),
                FigureNode("E:1", 6),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Apriori's cost is candidate generation: it proposes itemsets and then reads the database to find out whether they exist. FP-Growth removes that step entirely. It never generates a candidate — it compresses the database into a prefix tree and then reads the frequent itemsets directly out of the tree's structure.",
        "The compression comes from a single ordering decision. Count items once, drop the infrequent ones, and sort every remaining transaction by descending global frequency. Common items now appear at the front of most transactions, so they share prefixes, so they share tree nodes; a node is stored once with a counter rather than once per transaction. A header table keeps a linked list through every node holding a given item, which is how the mining step finds them all without traversing the tree.",
        "Mining is recursive and elegant. For an item, walk its header chain, read the path from each node up to the root, and weight each path by that node's count — that collection is the item's conditional pattern base. Build a smaller FP-tree over it, and repeat. Two database passes total, and everything after that happens in memory. The cost is the tree: on data where transactions share little prefix structure it compresses badly and can exceed the size of the database itself, and then the honest answer is that FP-Growth was the wrong choice.",
    ),
    steps = listOf(
        StepCard(1, "Pass 1 — Count Items", "Single-item support, then discard everything below the threshold.", 0xFF06B6D4),
        StepCard(2, "Order by Descending Count", "The decision the whole method rests on: frequent items first means shared prefixes.", 0xFF22D3EE),
        StepCard(3, "Pass 2 — Build the Tree", "Insert each sorted transaction; existing prefixes increment a counter instead of allocating.", 0xFF8B5CF6),
        StepCard(4, "Link the Header Table", "One chain per item through every node holding it. The mining step's index.", 0xFF6366F1),
        StepCard(5, "Read Conditional Pattern Bases", "For an item, the path above each of its nodes, weighted by that node's count.", 0xFF10B981),
        StepCard(6, "Recurse on Conditional Trees", "Build a tree over the base and mine it the same way. No candidates, ever.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Database passes", "2, regardless of itemset length", "Against Apriori's one per level."),
        FormulaEntry("Node count", "≤ Σ|Tᵢ| after infrequent items are dropped", "Equality only when no two transactions share a prefix."),
        FormulaEntry("Conditional pattern base", "{(path(v), count(v)) : v ∈ header(i)}", "Every context in which item i occurred."),
        FormulaEntry("Conditional support", "Σ over paths containing j of count(v)", "How support is computed in the recursion."),
        FormulaEntry("Single-path shortcut", "2ᵏ − 1 itemsets from a k-node path", "If a conditional tree is one path, enumerate directly and stop recursing."),
        FormulaEntry("Header chain length", "the number of distinct contexts for that item", "Not its support — one node can carry many transactions."),
    ),
    notationKey = listOf(
        NotationEntry("FP-tree", "frequent-pattern tree: a prefix tree of ordered transactions with counts"),
        NotationEntry("header table", "item → linked list of the nodes holding it"),
        NotationEntry("conditional pattern base", "the weighted set of paths above one item's nodes"),
        NotationEntry("conditional FP-tree", "the tree built over that base, mined recursively"),
        NotationEntry("prefix sharing", "the reason the tree is smaller than the database"),
        NotationEntry("item order", "descending global frequency — the choice that makes prefixes shared"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Building the tree, where the compression happens",
            accentColor = 0xFF06B6D4,
            code = """
                from collections import Counter, defaultdict

                class Node:
                    def __init__(self, item, parent):
                        self.item, self.parent = item, parent
                        self.count, self.children = 1, {}

                def build_tree(transactions, minsup_count):
                    counts = Counter(i for t in transactions for i in t)
                    order = [i for i, c in counts.most_common() if c >= minsup_count]
                    rank = {item: r for r, item in enumerate(order)}

                    root, header = Node(None, None), defaultdict(list)
                    for t in transactions:
                        # Drop infrequent items, then sort by GLOBAL frequency. This ordering is
                        # the entire reason the tree compresses -- sort by anything else and
                        # transactions stop sharing prefixes.
                        items = sorted((i for i in t if i in rank), key=lambda i: rank[i])
                        node = root
                        for item in items:
                            if item in node.children:
                                node.children[item].count += 1     # no allocation at all
                            else:
                                child = Node(item, node)
                                node.children[item] = child
                                header[item].append(child)
                            node = node.children[item]
                    return root, header, order
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Mining it: conditional pattern bases, recursively",
            accentColor = 0xFF10B981,
            code = """
                def mine(transactions, minsup_count, suffix=frozenset()):
                    root, header, order = build_tree(transactions, minsup_count)
                    found = {}

                    # Rarest first: mining an item removes it from the conditional trees below,
                    # so starting at the bottom of the header table keeps them small.
                    for item in reversed(order):
                        support = sum(n.count for n in header[item])
                        pattern = suffix | {item}
                        found[frozenset(pattern)] = support

                        # The conditional pattern base: the path above each node, repeated as
                        # many times as that node's count.
                        base = []
                        for node in header[item]:
                            path, cur = [], node.parent
                            while cur and cur.item is not None:
                                path.append(cur.item)
                                cur = cur.parent
                            base.extend([path[::-1]] * node.count)

                        if base:
                            found.update(mine(base, minsup_count, pattern))
                    return found

                # Two passes over the real database, then this recursion runs entirely in memory.
                # The tree is the risk: with little shared prefix structure it can be bigger than
                # the data it replaced.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("browser", 0xFF06B6D4, "Large-Scale Retail Mining", "The default choice at scale, and the algorithm behind Spark MLlib's `FPGrowth`."),
        ApplicationCard("history", 0xFF8B5CF6, "Sequential Pattern Mining", "PrefixSpan and its relatives generalise the conditional-database idea to ordered sequences."),
        ApplicationCard("chip", 0xFF6366F1, "Log and Alarm Correlation", "Repeated event sets in telemetry, where transactions share long prefixes and the tree compresses well."),
    ),
    takeaways = listOf(
        "No candidate generation at all — the frequent sets are read out of the tree's structure.",
        "Exactly two database passes, regardless of how long the frequent itemsets get.",
        "Sorting by descending global frequency is what makes prefixes shared and the tree small.",
        "The header table is what lets mining find every occurrence of an item without a traversal.",
        "With little shared structure the tree can exceed the database, and then Apriori or Eclat is the better tool.",
    ),
    crossLinks = listOf(
        CrossLink("apriori", "Apriori Algorithm"),
        CrossLink("eclat", "Eclat Algorithm"),
        CrossLink("trie", "Trie (DSA)"),
        CrossLink("huffman_coding", "Huffman Coding (DSA)"),
    ),
)
