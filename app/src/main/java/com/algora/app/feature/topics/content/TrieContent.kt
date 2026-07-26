package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val trieContent = TopicContent(
    topicId = "trie",
    whatIsIt = listOf(
        "A trie (prefix tree) stores a set of strings as a tree of characters: each edge is one character, and each root-to-node path spells out a prefix.",
        "Words that share a prefix share the same path, so lookups depend on the length of the word — not on how many words the trie holds.",
    ),
    steps = listOf(
        StepCard(1, "Character Edges", "Each node has one child per possible next character, keyed by that character.", 0xFF10B981),
        StepCard(2, "Insert a Word", "Walk down from the root creating nodes for each character, then mark the final node as end-of-word.", 0xFF3B82F6),
        StepCard(3, "Search / Prefix", "Follow the characters from the root; if the path exists you found the prefix — check the end flag for a full word.", 0xFFF59E0B),
        StepCard(4, "Shared Prefixes", "Common beginnings are stored once, which is what makes prefix queries fast.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Insert / Search", "O(L)", "L is the length of the word — independent of how many keys are stored."),
        FormulaEntry("Prefix query", "O(L)", "Walking to the end of the prefix takes one step per character."),
        FormulaEntry("Space", "O(total characters · alphabet)", "Nodes hold a slot per possible next character."),
    ),
    notationKey = listOf(
        NotationEntry("L", "length of the query word or prefix"),
        NotationEntry("isEnd", "flag marking a node as a complete word"),
        NotationEntry("alphabet", "the set of possible characters at each node"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Trie insert and prefix check",
            accentColor = 0xFF6366F1,
            code = """
                class Trie {
                    private class Node {
                        val next = HashMap<Char, Node>()
                        var isEnd = false
                    }
                    private val root = Node()

                    fun insert(word: String) {
                        var node = root
                        for (c in word) node = node.next.getOrPut(c) { Node() }
                        node.isEnd = true
                    }

                    fun startsWith(prefix: String): Boolean {
                        var node = root
                        for (c in prefix) node = node.next[c] ?: return false
                        return true
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("search", 0xFF10B981, "Autocomplete", "Type-ahead suggestions walk to the prefix node, then list every word beneath it."),
        ApplicationCard("check", 0xFF3B82F6, "Spell Check & Dictionaries", "Membership tests and prefix validation run in time proportional to word length."),
        ApplicationCard("map", 0xFFF59E0B, "IP Routing", "Routers use trie variants (radix trees) to match the longest network prefix for an address."),
    ),
    takeaways = listOf(
        "A trie keys strings by character path, so lookup is O(L) regardless of how many words it holds.",
        "Shared prefixes are stored once, making prefix and autocomplete queries its signature strength.",
        "The cost is memory: each node reserves room for its possible next characters.",
        "Use a trie for prefix-heavy work; use a hash table when you only need exact-key lookup.",
    ),
    crossLinks = listOf(
        CrossLink("string", "String"),
        CrossLink("tree", "Tree"),
    ),
)
