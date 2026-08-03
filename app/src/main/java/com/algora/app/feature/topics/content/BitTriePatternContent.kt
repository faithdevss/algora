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

// Interview-prep pattern guide. A trie over bits rather than characters — the standard answer to
// every "maximum XOR" question, which brute force cannot reach.
internal val bitTriePatternContent = TopicContent(
    topicId = "bit_trie_pattern",
    figure = Figure(
        caption = "Insert every value into a binary trie, most significant bit first. A maximum-XOR " +
            "query then walks down taking the *opposite* branch whenever one exists — greedy is optimal " +
            "because one high bit outweighs every lower bit combined.",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("root", null),
                FigureNode("0", 0, FigureTone.Primary),
                FigureNode("1", 0, FigureTone.Accent),
                FigureNode("0", 1),
                FigureNode("1", 1, FigureTone.Primary),
                FigureNode("1", 2, FigureTone.Accent),
                FigureNode("5", 4, FigureTone.Primary),
                FigureNode("25", 5, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A binary trie stores each number as its bit string, most significant bit first. To maximise a XOR against a stored set, walk the trie greedily taking the *opposite* bit at every level — that fixes the highest possible bits first, which dominates every lower bit combined.",
        "The greedy is safe because one bit at position k outweighs all bits below it: 2^k > 2^k - 1. Each query is 32 steps regardless of how many numbers are stored, turning an O(n²) pairwise scan into O(n · 32).",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Maximum XOR of a pair, of a subarray, or against a query value — plus a count that makes the O(n²) pairwise loop impossible.", 0xFFF59E0B),
        StepCard(2, "Insert Bits, High to Low", "For bit 31 down to 0, walk or create the child for that bit. Fixed width means every path has the same length.", 0xFF3B82F6),
        StepCard(3, "Query the Opposite", "For each bit of the query, go to the opposite child if it exists — that sets a 1 in the result — otherwise follow the same bit.", 0xFFEF4444),
        StepCard(4, "Fold in Prefix XOR", "For subarray XOR, insert prefix values: xor(l..r) = prefix[r] ^ prefix[l-1]. Insert each prefix, then query it, exactly as with prefix sums.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n · B)", "B = 32 bits; insert and query are one walk each."),
        FormulaEntry("Space", "O(n · B)", "Up to B nodes per inserted number, shared prefixes aside."),
        FormulaEntry("Greedy validity", "2^k > Σ 2^i for i < k", "Fixing a higher bit always beats every lower bit combined."),
    ),
    notationKey = listOf(
        NotationEntry("B", "bit width, typically 32"),
        NotationEntry("bit", "(x >> k) & 1 — the k-th bit of x"),
        NotationEntry("prefix[i]", "XOR of everything up to index i"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Maximum XOR pair (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                B = 32

                class BitTrie:
                    def __init__(self):
                        self.child = [None, None]

                    def insert(self, x):
                        node = self
                        for k in range(B - 1, -1, -1):     # most significant bit first
                            b = (x >> k) & 1
                            if node.child[b] is None:
                                node.child[b] = BitTrie()
                            node = node.child[b]

                    def max_xor(self, x):
                        node, best = self, 0
                        for k in range(B - 1, -1, -1):
                            b = (x >> k) & 1
                            want = 1 - b                   # opposite bit sets this position
                            if node.child[want] is not None:
                                best |= 1 << k
                                node = node.child[want]
                            else:
                                node = node.child[b]
                        return best

                def max_pair_xor(nums):
                    trie, best = BitTrie(), 0
                    trie.insert(nums[0])
                    for x in nums[1:]:
                        best = max(best, trie.max_xor(x))  # query before insert: pairs only
                        trie.insert(x)
                    return best
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.BitBoardPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF3B82F6, "Maximum XOR Queries", "Best XOR pair, best subarray XOR, and XOR against an offline query set."),
        ApplicationCard("network", 0xFF10B981, "IP Longest-Prefix Match", "Routing tables are binary tries over address bits."),
        ApplicationCard("search", 0xFF8B5CF6, "Nearest Neighbour in Hamming Space", "Fingerprint and hash-similarity lookups over bit strings."),
    ),
    takeaways = listOf(
        "Go most-significant-bit first — the greedy only works because higher bits dominate.",
        "Pad to a fixed width so every number is the same depth; ragged paths break the walk.",
        "Query before inserting the current element when the answer must be a pair of distinct entries.",
        "Prefix XOR turns subarray questions into pair questions, exactly like prefix sums do for ranges.",
    ),
    crossLinks = listOf(
        CrossLink("xor_tricks", "XOR Tricks (Algorithms)"),
        CrossLink("trie", "Trie (Data Structures)"),
        CrossLink("bit_manipulation_pattern", "Bit Manipulation Pattern"),
    ),
)
