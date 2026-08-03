package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureEdge
import com.algora.app.core.data.model.FigureGraphNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Uses the standard 7-section template; cross-links to the Algorithms
// topic that implements the technique.
internal val topologicalSortPatternContent = TopicContent(
    topicId = "topological_sort_pattern",
    figure = Figure(
        caption = "In-degree is the count of unmet prerequisites. Nodes at zero are ready now; emitting " +
            "one decrements its neighbours and may free them. If fewer than n nodes are emitted, the " +
            "leftovers are sitting on a cycle — the count *is* the cycle check.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("A 0", 0.06f, 0.18f, FigureTone.Accent),
                FigureGraphNode("B 0", 0.06f, 0.82f, FigureTone.Accent),
                FigureGraphNode("C 2", 0.38f, 0.50f, FigureTone.Primary),
                FigureGraphNode("D 1", 0.70f, 0.16f),
                FigureGraphNode("E 1", 0.70f, 0.84f),
                FigureGraphNode("F 2", 0.96f, 0.50f),
            ),
            edges = listOf(
                FigureEdge(0, 2, directed = true, tone = FigureTone.Accent),
                FigureEdge(1, 2, directed = true, tone = FigureTone.Accent),
                FigureEdge(2, 3, directed = true),
                FigureEdge(2, 4, directed = true),
                FigureEdge(3, 5, directed = true),
                FigureEdge(4, 5, directed = true),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Topological sort orders the nodes of a directed acyclic graph so every edge points forward. In interviews it arrives disguised: course schedules, build targets, task dependencies, alien dictionaries.",
        "Kahn's BFS form is the one to reach for, because the count of emitted nodes doubles as the cycle check — if fewer than n came out, the remaining nodes are locked in a cycle.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "\"Before / requires / depends on\", or ordering under prerequisite constraints.", 0xFFF59E0B),
        StepCard(2, "Build Graph + In-degrees", "Edge u → v means u comes first. Count how many prerequisites each node still has.", 0xFF3B82F6),
        StepCard(3, "Queue the Zeroes", "Every node with in-degree 0 is ready now. They can be emitted in any order.", 0xFF8B5CF6),
        StepCard(4, "Pop, Emit, Decrement", "Popping a node releases its successors; any that hits in-degree 0 joins the queue. Fewer than n emitted ⇒ cycle.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(V + E)", "Each node dequeued once, each edge relaxed once."),
        FormulaEntry("Space", "O(V + E)", "Adjacency lists, the in-degree table and the queue."),
        FormulaEntry("Cycle test", "emitted < V", "The leftovers all still have an unmet prerequisite."),
    ),
    notationKey = listOf(
        NotationEntry("V, E", "nodes (tasks) and edges (dependencies)"),
        NotationEntry("indeg[v]", "unmet prerequisites remaining for v"),
        NotationEntry("u → v", "u must come before v"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Course schedule order, Kahn's algorithm (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                from collections import deque

                def course_order(n, prereqs):
                    adj = [[] for _ in range(n)]
                    indeg = [0] * n
                    for course, need in prereqs:      # need -> course
                        adj[need].append(course)
                        indeg[course] += 1

                    q = deque(v for v in range(n) if indeg[v] == 0)
                    order = []
                    while q:
                        u = q.popleft()
                        order.append(u)
                        for v in adj[u]:
                            indeg[v] -= 1
                            if indeg[v] == 0:
                                q.append(v)
                    return order if len(order) == n else []   # [] means cyclic
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFF3B82F6, "Build & Task Ordering", "Compiling modules, running migrations, scheduling jobs with dependencies."),
        ApplicationCard("book", 0xFF10B981, "Course Schedules", "Whether a curriculum is completable, and in which order."),
        ApplicationCard("translate", 0xFF8B5CF6, "Order Inference", "Alien dictionary — deriving a letter order from sorted words."),
    ),
    takeaways = listOf(
        "Only DAGs have a topological order; the emitted count is the cycle detector.",
        "Multiple valid orders usually exist — say so before the interviewer asks.",
        "A min-heap instead of a queue gives the lexicographically smallest order.",
        "The DFS form works too, but reverse-postorder needs its own cycle colouring.",
    ),
    crossLinks = listOf(
        CrossLink("topological_sort", "Topological Sort (Algorithms)"),
        CrossLink("bfs", "Breadth-First Search (Algorithms)"),
        CrossLink("faang_set", "Practice: FAANG Set"),
    ),
)
