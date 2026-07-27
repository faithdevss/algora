package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val huffmanCodingContent = TopicContent(
    topicId = "huffman_coding",
    whatIsIt = listOf(
        "Huffman coding compresses data by giving frequent symbols short bit codes and rare symbols long ones, minimizing the total encoded length.",
        "It greedily builds a binary tree from the bottom up, always merging the two least-frequent symbols, so the resulting prefix-free code is provably optimal for symbol-by-symbol encoding.",
    ),
    steps = listOf(
        StepCard(1, "Count Frequencies", "Tally how often each symbol appears in the input.", 0xFF10B981),
        StepCard(2, "Merge the Two Smallest", "Repeatedly take the two lowest-frequency nodes from a min-heap and join them under a new parent.", 0xFF3B82F6),
        StepCard(3, "Build the Tree", "The merged parent's frequency is the sum; reinsert it and repeat until one root remains.", 0xFFF59E0B),
        StepCard(4, "Read Off Codes", "Label left edges 0 and right edges 1; each leaf's root-to-leaf path is its code.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n log n)", "n symbols, each heap push/pop is O(log n)."),
        FormulaEntry("Prefix-free", "no code is a prefix of another", "Guarantees unambiguous decoding."),
        FormulaEntry("Optimality", "minimizes Σ freqᵢ · lenᵢ", "Shortest possible per-symbol code."),
    ),
    notationKey = listOf(
        NotationEntry("freqᵢ", "how often symbol i occurs"),
        NotationEntry("lenᵢ", "bit-length of symbol i's code"),
        NotationEntry("prefix-free", "codes where none is a prefix of another"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Building the Huffman tree",
            accentColor = 0xFF6366F1,
            code = """
                class Node(val freq: Int, val left: Node? = null, val right: Node? = null)

                fun buildHuffman(freqs: Map<Char, Int>): Node {
                    val pq = java.util.PriorityQueue<Node>(compareBy { it.freq })
                    for ((_, f) in freqs) pq.add(Node(f))
                    while (pq.size > 1) {
                        val a = pq.poll()
                        val b = pq.poll()
                        pq.add(Node(a.freq + b.freq, a, b))   // merge two smallest
                    }
                    return pq.poll()
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "File Compression", "ZIP, gzip, and PNG use Huffman coding (often after other passes) to shrink data losslessly."),
        ApplicationCard("image", 0xFF3B82F6, "Media Codecs", "JPEG and MP3 entropy-code their quantized coefficients with Huffman tables."),
        ApplicationCard("link", 0xFFF59E0B, "Protocol Header Compression", "HTTP/2's HPACK uses a static Huffman code to shrink repeated header strings."),
    ),
    takeaways = listOf(
        "Huffman coding assigns shorter codes to more frequent symbols, minimizing total bits.",
        "It greedily merges the two least-frequent nodes, building an optimal prefix-free tree.",
        "The prefix-free property is what lets a decoder split the bitstream unambiguously.",
        "It is optimal per-symbol; adaptive and arithmetic coders can beat it by modeling context.",
    ),
    crossLinks = listOf(
        CrossLink("heap", "Heap (Min / Max)"),
        CrossLink("trie", "Trie (Prefix Tree)"),
    ),
)
