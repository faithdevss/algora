package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val kosarajusAlgorithmContent = TopicContent(
    topicId = "kosarajus_algorithm",
    whatIsIt = listOf(
        "Kosaraju's algorithm finds the strongly connected components of a directed graph with two depth-first passes — one on the graph, one on its reverse.",
        "It's less subtle than Tarjan's: the trick is that finishing-time order from the first pass, replayed on the transposed graph, cleanly separates each SCC.",
    ),
    steps = listOf(
        StepCard(1, "First DFS — Order by Finish", "Run DFS on the original graph, pushing each node onto a stack as it finishes.", 0xFF10B981),
        StepCard(2, "Transpose the Graph", "Reverse the direction of every edge.", 0xFF3B82F6),
        StepCard(3, "Second DFS in Finish Order", "Pop nodes off the stack; each DFS on the reversed graph from an unvisited node yields one SCC.", 0xFFF59E0B),
        StepCard(4, "Collect Components", "Every tree of that second pass is exactly one strongly connected component.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(V + E)", "Two linear DFS passes plus building the transpose."),
        FormulaEntry("Space", "O(V + E)", "Stores the reversed graph and a finish-order stack."),
        FormulaEntry("Key property", "reverse-graph SCCs = forward SCCs", "Transposing preserves strong connectivity."),
    ),
    notationKey = listOf(
        NotationEntry("Gᵀ", "the transpose (all edges reversed)"),
        NotationEntry("finish order", "order in which the first DFS completes nodes"),
        NotationEntry("SCC", "maximal mutually-reachable node set"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Kosaraju — two passes",
            accentColor = 0xFF6366F1,
            code = """
                fun kosaraju(n: Int, adj: List<List<Int>>): List<List<Int>> {
                    val visited = BooleanArray(n)
                    val order = ArrayDeque<Int>()          // finish-time stack

                    fun dfs1(u: Int) {
                        visited[u] = true
                        for (v in adj[u]) if (!visited[v]) dfs1(v)
                        order.addLast(u)                    // push on finish
                    }
                    for (u in 0 until n) if (!visited[u]) dfs1(u)

                    val rev = List(n) { mutableListOf<Int>() }
                    for (u in 0 until n) for (v in adj[u]) rev[v].add(u)

                    val comp = IntArray(n) { -1 }
                    val sccs = mutableListOf<MutableList<Int>>()
                    fun dfs2(u: Int, id: Int) {
                        comp[u] = id; sccs[id].add(u)
                        for (v in rev[u]) if (comp[v] == -1) dfs2(v, id)
                    }
                    while (order.isNotEmpty()) {
                        val u = order.removeLast()
                        if (comp[u] == -1) { sccs.add(mutableListOf()); dfs2(u, sccs.size - 1) }
                    }
                    return sccs
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("share", 0xFF10B981, "Dependency Condensation", "Grouping mutually dependent modules into SCCs exposes the underlying build DAG."),
        ApplicationCard("users", 0xFF3B82F6, "Social & Web Clusters", "Mutually reachable users or pages form SCCs used in community and link analysis."),
        ApplicationCard("book", 0xFFF59E0B, "Teaching SCCs", "Its two clear DFS passes make it the most intuitive SCC algorithm to learn."),
    ),
    takeaways = listOf(
        "Kosaraju's finds SCCs with two DFS passes: finish-order on G, then DFS on Gᵀ.",
        "Transposing the graph preserves strong connectivity, which makes the second pass separate components.",
        "It matches Tarjan's O(V+E) but is simpler to reason about, at the cost of a second traversal.",
        "SCC decomposition feeds condensation DAGs, 2-SAT, and cluster analysis.",
    ),
    crossLinks = listOf(
        CrossLink("tarjans_algorithm", "Tarjan's Algorithm"),
        CrossLink("dfs", "Depth-First Search (DFS)"),
    ),
)
