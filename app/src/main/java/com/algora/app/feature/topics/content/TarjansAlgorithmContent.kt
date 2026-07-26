package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val tarjansAlgorithmContent = TopicContent(
    topicId = "tarjans_algorithm",
    whatIsIt = listOf(
        "Tarjan's algorithm finds the strongly connected components (SCCs) of a directed graph — maximal groups where every node can reach every other — in a single DFS pass.",
        "It tracks each node's discovery time and a 'low-link' value (the earliest node reachable from its subtree), and pops a full SCC off a stack whenever a node's low-link equals its own index.",
    ),
    steps = listOf(
        StepCard(1, "DFS with Timestamps", "Assign each node an increasing discovery index as DFS visits it, and push it onto a stack.", 0xFF10B981),
        StepCard(2, "Track Low-Links", "A node's low-link is the smallest index reachable from it, including via back-edges to stacked nodes.", 0xFF3B82F6),
        StepCard(3, "Update on Return", "After recursing into a child, pull its low-link back up into the parent's.", 0xFFF59E0B),
        StepCard(4, "Pop an SCC", "When a node's low-link equals its own index, pop the stack down to it — that set is one SCC.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(V + E)", "A single DFS touches each node and edge once."),
        FormulaEntry("SCC root", "low[v] == index[v]", "v is the entry point of a strongly connected component."),
        FormulaEntry("Space", "O(V)", "Index, low-link, and on-stack arrays."),
    ),
    notationKey = listOf(
        NotationEntry("index[v]", "DFS discovery order of v"),
        NotationEntry("low[v]", "earliest-indexed node reachable from v's subtree"),
        NotationEntry("SCC", "maximal set where all nodes are mutually reachable"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Tarjan's SCC (core recursion)",
            accentColor = 0xFF6366F1,
            code = """
                fun strongConnect(v: Int) {
                    index[v] = counter; low[v] = counter; counter++
                    stack.push(v); onStack[v] = true
                    for (w in adj[v]) {
                        if (index[w] == -1) {           // unvisited
                            strongConnect(w)
                            low[v] = minOf(low[v], low[w])
                        } else if (onStack[w]) {        // back-edge into current SCC
                            low[v] = minOf(low[v], index[w])
                        }
                    }
                    if (low[v] == index[v]) {           // v is an SCC root
                        val scc = mutableListOf<Int>()
                        do {
                            val w = stack.pop(); onStack[w] = false; scc.add(w)
                        } while (w != v)
                        sccs.add(scc)
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("share", 0xFF10B981, "Cycle & Dependency Analysis", "Collapsing SCCs reveals cyclic module or package dependencies that block a clean build order."),
        ApplicationCard("chip", 0xFF3B82F6, "2-SAT Solving", "Boolean satisfiability with two literals per clause is decided by SCCs of an implication graph."),
        ApplicationCard("map", 0xFFF59E0B, "Condensation Graphs", "Contracting each SCC to a node yields a DAG used for higher-level reasoning."),
    ),
    takeaways = listOf(
        "Tarjan's finds all SCCs in one O(V+E) DFS using discovery indices and low-links.",
        "A node with low-link equal to its index is the root of an SCC on the stack.",
        "It's a single-pass alternative to Kosaraju's two-pass method.",
        "SCC decomposition underlies 2-SAT, deadlock detection, and dependency condensation.",
    ),
    crossLinks = listOf(
        CrossLink("kosarajus_algorithm", "Kosaraju's Algorithm"),
        CrossLink("dfs", "Depth-First Search (DFS)"),
    ),
)
