package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val ahoCorasickContent = TopicContent(
    topicId = "aho_corasick",
    whatIsIt = listOf(
        "Aho-Corasick searches a text for every pattern in a dictionary at once, in a single pass, in time independent of how many patterns there are. Running KMP k times costs O(k·n); Aho-Corasick costs O(n + m + z), where m is the total length of the dictionary and z the number of matches actually reported. With a thousand patterns that is the difference between a thousand scans and one.",
        "It is KMP generalised from a string to a trie. Build a trie of all the patterns, then give every node a failure link pointing at the longest proper suffix of that node's string which is also a node in the trie — exactly KMP's prefix function, except the fallback target lives in a different branch rather than earlier in the same string. Matching is then a single walk: on each text character, follow the child edge if it exists; if it does not, follow failure links until one does or until you reach the root. The text pointer never moves backwards, which is the same guarantee KMP makes and the same reason the scan is linear.",
        "The failure links are computed by breadth-first search, because a node's failure target is always shallower than the node itself, so processing by depth means the answer is ready when it is needed. One extra piece is easy to miss and produces silently incomplete output without it: output links. When the automaton is sitting on the node for \"she\", it has also just matched \"he\", because \"he\" is a suffix of \"she\" and is itself a pattern. Following the failure chain from the current node and reporting every terminal node along it is what catches those. Omit it and the algorithm finds only the longest match at each position — which is a plausible-looking answer and a wrong one.",
    ),
    steps = listOf(
        StepCard(1, "Build the Trie", "Insert every pattern; shared prefixes share nodes. Mark the node where each pattern ends.", 0xFFEC4899),
        StepCard(2, "Root's Children Fail to Root", "A one-character string has no proper suffix in the trie beyond the empty one.", 0xFF3B82F6),
        StepCard(3, "Compute Failures by BFS", "A node's failure target is strictly shallower, so processing in depth order means it is always already known.", 0xFF10B981),
        StepCard(4, "Follow the Parent's Chain", "For child c of node u, walk u's failure chain until a node has a c-edge; that target is the child's failure.", 0xFFF59E0B),
        StepCard(5, "Add Output Links", "A node inherits the matches of its failure target — \"she\" also reports \"he\".", 0xFF8B5CF6),
        StepCard(6, "Walk the Text Once", "Advance on a child edge, or follow failures until one exists. The text pointer never backs up.", 0xFF06B6D4),
    ),
    formulas = listOf(
        FormulaEntry("Total time", "O(n + m + z)", "Text length, total dictionary length, and matches reported."),
        FormulaEntry("Against k × KMP", "O(k · n + m)", "Aho-Corasick removes k from the scan term entirely."),
        FormulaEntry("Space", "O(m · |Σ|) or O(m)", "Array-per-node against map-per-node — a real memory/constant trade."),
        FormulaEntry("Failure link", "longest proper suffix that is a trie node", "KMP's prefix function, generalised to a trie."),
        FormulaEntry("Construction", "O(m) by BFS", "Amortised, by the same argument that makes KMP's table linear."),
        FormulaEntry("Output links", "report along the failure chain", "Without them only the longest match at each position is found."),
    ),
    notationKey = listOf(
        NotationEntry("n, m, k", "text length, total pattern length, number of patterns"),
        NotationEntry("z", "number of matches reported — output is unavoidably part of the cost"),
        NotationEntry("failure link", "pointer to the node for the longest proper suffix present in the trie"),
        NotationEntry("output link", "shortcut to the nearest terminal node up the failure chain"),
        NotationEntry("goto automaton", "the trie with failure links resolved into direct transitions"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Building the automaton",
            accentColor = 0xFFEC4899,
            code = """
                class AhoCorasick(patterns: List<String>) {
                    private class Node {
                        val next = HashMap<Char, Int>()
                        var fail = 0
                        var output = mutableListOf<String>()
                        var outputLink = -1          // nearest terminal up the failure chain
                    }

                    private val nodes = mutableListOf(Node())   // 0 is the root

                    init {
                        for (p in patterns) {
                            var current = 0
                            for (c in p) {
                                current = nodes[current].next.getOrPut(c) {
                                    nodes += Node()
                                    nodes.lastIndex
                                }
                            }
                            nodes[current].output += p
                        }
                        buildFailures()
                    }

                    // BFS, because a node's failure target is always shallower than the node.
                    private fun buildFailures() {
                        val queue = ArrayDeque<Int>()
                        for (child in nodes[0].next.values) {
                            nodes[child].fail = 0        // depth-1 nodes fail to the root
                            queue += child
                        }
                        while (queue.isNotEmpty()) {
                            val u = queue.removeFirst()
                            for ((c, v) in nodes[u].next) {
                                var f = nodes[u].fail
                                while (f != 0 && c !in nodes[f].next) f = nodes[f].fail
                                nodes[v].fail = if (c in nodes[f].next && nodes[f].next[c] != v) {
                                    nodes[f].next.getValue(c)
                                } else {
                                    0
                                }
                                val fail = nodes[v].fail
                                nodes[v].outputLink =
                                    if (nodes[fail].output.isNotEmpty()) fail else nodes[fail].outputLink
                                queue += v
                            }
                        }
                    }
                }
            """.trimIndent(),
        ),
        CodeBlock(
            title = "One pass over the text — and what output links catch",
            accentColor = 0xFF8B5CF6,
            code = """
                fun search(text: String): List<Pair<Int, String>> {
                    val hits = mutableListOf<Pair<Int, String>>()
                    var current = 0
                    for ((i, c) in text.withIndex()) {
                        // Follow failure links until a c-edge exists, or we are back at the root.
                        while (current != 0 && c !in nodes[current].next) current = nodes[current].fail
                        current = nodes[current].next[c] ?: 0

                        // The node's own matches …
                        nodes[current].output.forEach { hits += (i - it.length + 1) to it }
                        // … and every shorter pattern that is a suffix of what we just matched.
                        var link = nodes[current].outputLink
                        while (link != -1) {
                            nodes[link].output.forEach { hits += (i - it.length + 1) to it }
                            link = nodes[link].outputLink
                        }
                    }
                    return hits
                }

                // Dictionary {"he", "she", "his", "hers"}, text "ushers":
                //   position 3 lands on the node for "she" — which also reports "he",
                //     because "he" is a suffix of "she" and is itself a pattern
                //   position 5 reports "hers"
                // Drop the outputLink loop and "he" silently disappears from the results.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFFEC4899, "Intrusion Detection", "Snort and similar engines match thousands of signatures against every packet in one pass."),
        ApplicationCard("browser", 0xFF3B82F6, "Content Filtering", "Keyword blocklists, spam term matching and profanity filters over streaming text."),
        ApplicationCard("bulb", 0xFF8B5CF6, "Bioinformatics & Search", "Scanning sequences for a library of motifs, and dictionary-based tokenisation in NLP pipelines."),
    ),
    takeaways = listOf(
        "One pass finds every dictionary pattern: O(n + m + z), with the pattern count gone from the scan term.",
        "A failure link is KMP's prefix function generalised — the longest proper suffix that is still a node in the trie.",
        "BFS order is what makes construction work, because a failure target is always shallower than its node.",
        "Output links are not optional: without them the automaton reports only the longest match and drops every pattern that is a suffix of it.",
    ),
    crossLinks = listOf(
        CrossLink("trie", "Trie"),
        CrossLink("kmp", "KMP String Matching"),
        CrossLink("bfs", "Breadth-First Search (BFS)"),
    ),
)
