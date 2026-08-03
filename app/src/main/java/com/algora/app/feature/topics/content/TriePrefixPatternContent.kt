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

// Interview-prep pattern guide. When the question is about shared prefixes rather than whole keys,
// the trie replaces the hash map.
internal val triePrefixPatternContent = TopicContent(
    topicId = "trie_prefix_pattern",
    figure = Figure(
        caption = "One node per character, one path per word, so words sharing a prefix share nodes. " +
            "search and startsWith walk identically — only the terminal flag (●) separates them, and a " +
            "counter on each node turns the same tree into a prefix index.",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("·", null),
                FigureNode("a", 0, FigureTone.Primary),
                FigureNode("p", 1, FigureTone.Primary),
                FigureNode("p●", 2, FigureTone.Accent),
                FigureNode("t●", 2, FigureTone.Accent),
                FigureNode("l", 3),
                FigureNode("e●", 5, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "The trie pattern stores a set of strings as a tree of shared prefixes: one node per character, one path per word. Lookup costs O(L) in the word's length and is independent of how many words are stored.",
        "A hash map answers \"is this exact key present\". A trie answers the prefix questions a hash map cannot: every word starting with \"pre\", the longest stored prefix of a query, or matching a whole dictionary against a board in one traversal.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Autocomplete, prefix counting, a dictionary matched against a grid or stream, or shared-prefix structure over a fixed alphabet.", 0xFFF59E0B),
        StepCard(2, "Insert Character by Character", "Walk from the root, creating a child per missing character, and mark the final node as a word end.", 0xFF3B82F6),
        StepCard(3, "Search versus StartsWith", "Both walk the same path. Search demands the terminal flag; startsWith only demands that the path exists.", 0xFFEF4444),
        StepCard(4, "Carry Extra Data on Nodes", "A counter per node gives prefix frequencies; storing the word on its end node lets a DFS collect results without rebuilding strings.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Insert / search", "O(L)", "L is the word length — independent of the number of stored words."),
        FormulaEntry("Space", "O(total characters · Σ)", "Σ is the alphabet size; a hash-map child table keeps it sparse."),
        FormulaEntry("Prefix listing", "O(L + output)", "Walk to the prefix node, then DFS whatever hangs below it."),
    ),
    notationKey = listOf(
        NotationEntry("L", "length of the key being inserted or searched"),
        NotationEntry("Σ", "alphabet size (26 for lowercase English)"),
        NotationEntry("is_word", "flag marking a node as the end of a stored word"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Trie with prefix search (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                class Trie:
                    def __init__(self):
                        self.children = {}
                        self.is_word = False

                    def insert(self, word):
                        node = self
                        for ch in word:
                            node = node.children.setdefault(ch, Trie())
                        node.is_word = True

                    def _walk(self, prefix):
                        node = self
                        for ch in prefix:
                            node = node.children.get(ch)
                            if node is None:
                                return None
                        return node

                    def search(self, word):
                        node = self._walk(word)
                        return node is not None and node.is_word

                    def starts_with(self, prefix):
                        return self._walk(prefix) is not None
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("search", 0xFF3B82F6, "Autocomplete", "Type-ahead: walk to the prefix node, then DFS for the top completions."),
        ApplicationCard("map", 0xFF10B981, "Word Search II", "Push the whole dictionary into one trie and prune the board DFS the moment a path leaves it."),
        ApplicationCard("network", 0xFF8B5CF6, "IP Routing", "Longest-prefix match over routing tables is a trie walk on address bits."),
    ),
    takeaways = listOf(
        "Cost scales with key length, not dictionary size — that is the whole trade.",
        "Search needs the terminal flag; startsWith needs only the path. Mixing them up is the classic bug.",
        "One trie over the dictionary turns a per-word grid search into a single pruned DFS.",
        "Node counters give prefix frequencies for free; store the word on the end node to avoid rebuilding it.",
    ),
    crossLinks = listOf(
        CrossLink("trie", "Trie (Data Structures)"),
        CrossLink("aho_corasick", "Aho-Corasick (Algorithms)"),
        CrossLink("hash_table", "Hash Table (Data Structures)"),
    ),
)
