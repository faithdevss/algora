package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Two-colouring and cycle detection are the same traversal with a
// different rejection rule — and the directed and undirected rules differ.
internal val graphColoringPatternContent = TopicContent(
    topicId = "graph_coloring_pattern",
    whatIsIt = listOf(
        "Two-colouring walks the graph assigning alternating colours. If an edge ever joins two same-coloured vertices, the graph contains an odd cycle and no valid split exists — that is exactly the bipartite test.",
        "Cycle detection is the same traversal with a different rejection rule. Undirected: any edge to a visited vertex that is not the parent. Directed: an edge back into a vertex still on the current recursion stack — a visited-but-finished vertex is harmless.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Split into two opposing groups, \"can everyone be seated at two tables\", conflict graphs, or a plain \"does a cycle exist\".", 0xFFF59E0B),
        StepCard(2, "Traverse Every Component", "Loop over all vertices and start a traversal from each uncoloured one — a disconnected component is the classic missed case.", 0xFF3B82F6),
        StepCard(3, "Colour and Check", "Assign the opposite colour to each neighbour. A neighbour already holding your own colour is the contradiction.", 0xFFEF4444),
        StepCard(4, "Use the Right Cycle Rule", "Undirected: ignore the edge you came from. Directed: keep an on-stack set and clear it when the call returns.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(V + E)", "Each vertex and edge examined once."),
        FormulaEntry("Space", "O(V)", "Colour array plus the queue or recursion stack."),
        FormulaEntry("Bipartite ⇔", "no odd-length cycle", "Even cycles two-colour fine; a single odd cycle kills it."),
    ),
    notationKey = listOf(
        NotationEntry("color[v]", "0 / 1, or -1 for not yet visited"),
        NotationEntry("on_stack", "vertices in the current directed DFS path"),
        NotationEntry("parent", "the vertex an undirected edge was entered from"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Bipartite check and directed cycle detection (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                from collections import deque

                def is_bipartite(graph, n):
                    color = [-1] * n
                    for start in range(n):
                        if color[start] != -1:
                            continue                       # component already done
                        color[start] = 0
                        dq = deque([start])
                        while dq:
                            u = dq.popleft()
                            for v in graph[u]:
                                if color[v] == -1:
                                    color[v] = 1 - color[u]
                                    dq.append(v)
                                elif color[v] == color[u]:
                                    return False           # odd cycle
                    return True

                def has_directed_cycle(graph, n):
                    state = [0] * n                        # 0 new, 1 on stack, 2 done
                    def dfs(u):
                        state[u] = 1
                        for v in graph[u]:
                            if state[v] == 1:
                                return True                # back edge into the live path
                            if state[v] == 0 and dfs(v):
                                return True
                        state[u] = 2
                        return False
                    return any(state[u] == 0 and dfs(u) for u in range(n))
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("users", 0xFF3B82F6, "Conflict Splitting", "Two-team assignment, dislike graphs and seating arrangements."),
        ApplicationCard("chip", 0xFF10B981, "Deadlock & Build Cycles", "Circular dependencies in build graphs, imports or lock acquisition."),
        ApplicationCard("network", 0xFF8B5CF6, "Matching Prep", "Bipartite structure is the precondition for maximum-matching algorithms."),
    ),
    takeaways = listOf(
        "Colouring and cycle detection are one traversal with different rejection rules.",
        "Loop over every vertex — components you never start from hide the counterexample.",
        "Directed cycles need on-stack state, not just visited; undirected ones need the parent check.",
        "Bipartite exactly means no odd cycle; say that when asked why the check works.",
    ),
    crossLinks = listOf(
        CrossLink("dfs", "Depth-First Search (Algorithms)"),
        CrossLink("bfs", "Breadth-First Search (Algorithms)"),
        CrossLink("topological_sort_pattern", "Topological Sort Pattern"),
    ),
)
