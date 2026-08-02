package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. The "design a structure with O(1) everything" round: no single
// structure does it, so pair two whose weaknesses cancel.
internal val compositeDesignPatternContent = TopicContent(
    topicId = "composite_design_pattern",
    whatIsIt = listOf(
        "Design questions ask for a structure where every operation is O(1) — insert, delete, get random, get min, evict least-recently-used. No single container offers all of that, so the answer is always two containers kept in sync.",
        "The recipe is fixed: a hash map for O(1) location, plus a second structure supplying the ordering the map lacks — a doubly linked list for recency, an array for random sampling, a parallel stack for running minima.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "\"Design a … with O(1) operations\", a cache with eviction, a set with random sampling, or a stack that also reports its minimum.", 0xFFF59E0B),
        StepCard(2, "Name the Missing Property", "A hash map gives lookup but no order. Write down exactly which property is missing: recency, position, or an extreme value.", 0xFF3B82F6),
        StepCard(3, "Pair a Second Structure", "Recency → doubly linked list. Random access → dynamic array with swap-and-pop. Running min/max → a parallel stack of prefix extremes.", 0xFFEF4444),
        StepCard(4, "Keep Both in Sync", "Every mutation touches both. The map stores a handle — node reference or array index — so the partner structure is edited in O(1) too.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("LRU cache", "get / put O(1)", "Map to nodes plus a doubly linked list ordered by recency."),
        FormulaEntry("Random set", "insert / remove / sample O(1)", "Map from value to index, array of values, swap-and-pop on delete."),
        FormulaEntry("Min stack", "push / pop / min O(1)", "Second stack holding the minimum so far at each depth."),
    ),
    notationKey = listOf(
        NotationEntry("handle", "node reference or index the map stores instead of the value"),
        NotationEntry("swap-and-pop", "move the last array element into the hole, then shrink"),
        NotationEntry("sentinel", "dummy head/tail nodes removing null checks from splices"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Insert / delete / getRandom in O(1) (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                import random

                class RandomSet:
                    def __init__(self):
                        self.items = []                    # dense array for sampling
                        self.index = {}                    # value -> position in items

                    def insert(self, value):
                        if value in self.index:
                            return False
                        self.index[value] = len(self.items)
                        self.items.append(value)
                        return True

                    def remove(self, value):
                        if value not in self.index:
                            return False
                        pos, last = self.index.pop(value), self.items[-1]
                        if last != value:
                            self.items[pos] = last         # swap the tail into the hole
                            self.index[last] = pos
                        self.items.pop()
                        return True

                    def get_random(self):
                        return random.choice(self.items)   # dense array keeps this uniform
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.LinkedStructurePlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF3B82F6, "Caches", "LRU and LFU eviction in databases, CDNs and page caches."),
        ApplicationCard("stack", 0xFF10B981, "Instrumented Stacks", "Min-stack and max-stack for streaming extremes and monotone bookkeeping."),
        ApplicationCard("flask", 0xFF8B5CF6, "Sampling", "Uniform random draws from a live, mutating candidate set."),
    ),
    takeaways = listOf(
        "One structure is never enough — pair the map with whatever supplies the missing order.",
        "Store handles, not copies, so the partner structure is also edited in O(1).",
        "Every mutation updates both sides; a single missed update silently corrupts the invariant.",
        "Say the invariant out loud before coding — it is what the interviewer is actually grading.",
    ),
    crossLinks = listOf(
        CrossLink("lru_cache", "LRU Cache (Data Structures)"),
        CrossLink("hash_table", "Hash Table (Data Structures)"),
        CrossLink("doubly_linked_list", "Doubly Linked List (Data Structures)"),
    ),
)
