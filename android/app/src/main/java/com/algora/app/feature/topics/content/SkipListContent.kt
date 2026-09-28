package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val skipListContent = TopicContent(
    topicId = "skip_list",
    figure = Figure(
        caption = "A sorted linked list with express lanes: each node is promoted to the next level with " +
            "probability ½, so the top lane skips about half the nodes, the one below a quarter, and a " +
            "search drops down whenever the next hop overshoots. Expected O(log n) with no rotations and " +
            "no rebalancing — the balance is probabilistic rather than maintained.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("1", "—", "—", "—", "—", "9"),
                listOf("1", "—", "4", "—", "—", "9"),
                listOf("1", "3", "4", "6", "7", "9"),
            ),
            rowHeaders = listOf("L2", "L1", "L0"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Accent),
                FigureCell(1, 2, FigureTone.Accent),
                FigureCell(2, 3, FigureTone.Primary),
                FigureCell(2, 4, FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A skip list is a sorted linked list with extra 'express lane' layers that let searches skip over many nodes, giving O(log n) expected search, insert, and delete.",
        "Each node is randomly promoted to higher levels, so the structure balances itself probabilistically — no rotations, far simpler than a balanced tree.",
    ),
    steps = listOf(
        StepCard(1, "Layered Lists", "The bottom layer holds all nodes in order; each higher layer is a sparse express lane over the one below.", 0xFF10B981),
        StepCard(2, "Search Top-Down", "Start at the top-left, move right while the next key is ≤ target, then drop down a level.", 0xFF3B82F6),
        StepCard(3, "Randomized Height", "On insert, flip coins to decide how many levels the new node rises to.", 0xFFF59E0B),
        StepCard(4, "Splice In/Out", "Insert and delete just relink pointers at each affected level — no rebalancing.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Search / insert / delete", "O(log n) expected", "Express lanes halve the search span per level."),
        FormulaEntry("Level probability", "p (often 1/2)", "Chance a node rises to the next level."),
        FormulaEntry("Space", "O(n) expected", "Extra pointers total to a constant factor of n."),
    ),
    notationKey = listOf(
        NotationEntry("level", "an express-lane layer above the base list"),
        NotationEntry("p", "promotion probability per level"),
        NotationEntry("n", "number of elements"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Skip list node and random level",
            accentColor = 0xFF6366F1,
            code = """
                class Node(val key: Int, level: Int) {
                    val forward = arrayOfNulls<Node>(level + 1)   // one pointer per level
                }

                // Coin-flip height: level 0 always, higher levels with probability p.
                fun randomLevel(maxLevel: Int, p: Double = 0.5): Int {
                    var lvl = 0
                    while (Math.random() < p && lvl < maxLevel) lvl++
                    return lvl
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.LinkedStructurePlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF10B981, "In-Memory Databases", "Redis uses skip lists to implement its sorted-set (ZSET) type."),
        ApplicationCard("share", 0xFF3B82F6, "Concurrent Structures", "Lock-free skip lists back Java's ConcurrentSkipListMap for scalable ordered access."),
        ApplicationCard("search", 0xFFF59E0B, "Ordered Ranges", "Like a balanced tree, they support fast range and successor queries on sorted keys."),
    ),
    takeaways = listOf(
        "A skip list layers express lanes over a sorted list for O(log n) expected operations.",
        "Randomized promotion balances it probabilistically — no rotations or rebalancing code.",
        "It rivals balanced BSTs in performance while being much simpler to implement.",
        "Its simplicity and concurrency-friendliness make it popular in real systems like Redis.",
    ),
    crossLinks = listOf(
        CrossLink("singly_linked_list", "Singly Linked List"),
        CrossLink("avl_red_black_tree", "AVL / Red-Black Tree"),
    ),
)
