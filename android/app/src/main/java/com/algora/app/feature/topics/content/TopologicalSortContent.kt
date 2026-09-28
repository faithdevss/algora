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

internal val topologicalSortContent = TopicContent(
    topicId = "topological_sort",
    figure = Figure(
        caption = "In-degree counts over time for the DAG A→C, B→C, C→D, C→E, D→E. A node is emitted " +
            "the moment its count reaches zero, which is the moment its last dependency was emitted — " +
            "and C waiting on both A and B is why this has to be a count rather than a flag. The " +
            "starting row already has two zeros, so A and B are interchangeable and the output order is " +
            "not unique; any topological sort is one of several valid answers. The cycle check falls out " +
            "for free: if the queue empties with nodes left over, every remaining count is at least one, " +
            "so each of those nodes is waiting on another node that is also waiting — which is a cycle, " +
            "and no valid order exists. O(V+E).",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0", "0", "2", "1", "2"),
                listOf("—", "0", "1", "1", "2"),
                listOf("—", "—", "0", "1", "2"),
                listOf("—", "—", "—", "0", "1"),
                listOf("—", "—", "—", "—", "0"),
            ),
            rowHeaders = listOf("start", "pop A", "pop B", "pop C", "pop D"),
            colHeaders = listOf("A", "B", "C", "D", "E"),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Accent),
                FigureCell(0, 1, FigureTone.Accent),
                FigureCell(2, 2, FigureTone.Accent),
                FigureCell(3, 3, FigureTone.Accent),
                FigureCell(4, 4, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A topological sort linearises a directed acyclic graph so that every edge u → v places u before v — an order in which no task starts before its dependencies finish.",
        "Kahn's algorithm builds that order by repeatedly taking a node with no unmet dependencies; if it ever runs out of such nodes while some remain, the graph has a cycle and no valid order exists.",
    ),
    steps = listOf(
        StepCard(1, "Count In-Degrees", "For each node, count how many edges point at it — its number of unmet dependencies.", 0xFF3B82F6),
        StepCard(2, "Seed the Queue", "Every node with in-degree 0 is ready immediately; push them all.", 0xFF10B981),
        StepCard(3, "Pop, Emit, Relax", "Pop a ready node, append it to the order, and decrement the in-degree of each neighbour.", 0xFFF59E0B),
        StepCard(4, "Newly Freed Nodes Join", "A neighbour whose in-degree hits 0 becomes ready and is pushed.", 0xFF8B5CF6),
        StepCard(5, "Check the Count", "If fewer than V nodes were emitted, the leftovers sit on a cycle.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Validity", "for every edge (u, v): pos[u] < pos[v]", "The defining property of a topological order."),
        FormulaEntry("Cycle test", "emitted < V ⟺ the graph has a cycle", "Kahn's algorithm detects cycles for free."),
        FormulaEntry("Time", "O(V + E)", "Each node and edge is processed once."),
        FormulaEntry("Uniqueness", "unique ⟺ queue size is 1 at every step", "Otherwise several valid orders exist."),
    ),
    notationKey = listOf(
        NotationEntry("V, E", "node and edge counts"),
        NotationEntry("in-degree", "number of incoming edges still unresolved"),
        NotationEntry("DAG", "directed acyclic graph — the only kind that can be sorted"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Kahn's algorithm",
            accentColor = 0xFF6366F1,
            code = """
                fun topologicalSort(n: Int, edges: List<Pair<Int, Int>>): List<Int>? {
                    val adj = Array(n) { mutableListOf<Int>() }
                    val inDegree = IntArray(n)
                    for ((u, v) in edges) {
                        adj[u] += v
                        inDegree[v]++
                    }

                    val ready = ArrayDeque<Int>()
                    for (v in 0 until n) if (inDegree[v] == 0) ready += v

                    val order = mutableListOf<Int>()
                    while (ready.isNotEmpty()) {
                        val u = ready.removeFirst()
                        order += u
                        for (v in adj[u]) {
                            if (--inDegree[v] == 0) ready += v
                        }
                    }

                    // Anything left behind is part of a cycle.
                    return if (order.size == n) order else null
                }
            """.trimIndent(),
        ),
        CodeBlock(
            title = "DFS variant — reverse postorder",
            accentColor = 0xFF10B981,
            code = """
                fun topoDfs(n: Int, adj: Array<List<Int>>): List<Int> {
                    val state = IntArray(n)           // 0 = unseen, 1 = on stack, 2 = done
                    val out = mutableListOf<Int>()

                    fun dfs(u: Int) {
                        state[u] = 1
                        for (v in adj[u]) {
                            check(state[v] != 1) { "cycle through node ${'$'}v" }
                            if (state[v] == 0) dfs(v)
                        }
                        state[u] = 2
                        out += u                      // finished after all descendants
                    }

                    for (v in 0 until n) if (state[v] == 0) dfs(v)
                    return out.reversed()
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("stack", 0xFF3B82F6, "Build Systems", "Gradle, Make and Bazel topologically order targets so nothing compiles before what it depends on."),
        ApplicationCard("browser", 0xFF10B981, "Course Scheduling", "Prerequisite chains — including this app's own topic graph — resolve to a legal study order."),
        ApplicationCard("chip", 0xFFF59E0B, "Spreadsheet Recalculation", "Cell formulas form a DAG; a topological pass evaluates each cell after its inputs."),
    ),
    takeaways = listOf(
        "Only a DAG has a topological order — Kahn's algorithm reports the cycle instead of failing silently.",
        "In-degree zero means \"all dependencies satisfied\", which is the entire intuition.",
        "The DFS variant produces reverse postorder and is the basis of Tarjan's SCC algorithm.",
        "Multiple valid orders normally exist; swap the queue for a priority queue to get the lexicographically smallest.",
    ),
    crossLinks = listOf(
        CrossLink("bfs", "Breadth-First Search (BFS)"),
        CrossLink("dfs", "Depth-First Search (DFS)"),
        CrossLink("tarjans_algorithm", "Tarjan's Algorithm"),
    ),
)
