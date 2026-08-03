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

// Interview-prep pattern guide. Topological order is not the answer, it is the *iteration order* —
// once you have it, DP over a DAG is a single relaxation sweep.
internal val dagDpPatternContent = TopicContent(
    topicId = "dag_dp_pattern",
    figure = Figure(
        caption = "Longest path is NP-hard in general and a single scan on a DAG: relaxing nodes in " +
            "topological order means every predecessor is finished before the node is read, so each " +
            "node is final the first time it is popped. Each edge is relaxed once — O(V + E).",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("A 0", 0.06f, 0.18f, FigureTone.Primary),
                FigureGraphNode("B 0", 0.06f, 0.82f, FigureTone.Primary),
                FigureGraphNode("C 6", 0.38f, 0.50f, FigureTone.Primary),
                FigureGraphNode("D 10", 0.70f, 0.16f),
                FigureGraphNode("E 8", 0.70f, 0.84f),
                FigureGraphNode("F 17", 0.96f, 0.50f, FigureTone.Accent),
            ),
            edges = listOf(
                FigureEdge(0, 2, "3", directed = true),
                FigureEdge(1, 2, "6", directed = true, tone = FigureTone.Accent),
                FigureEdge(2, 3, "4", directed = true, tone = FigureTone.Accent),
                FigureEdge(2, 4, "2", directed = true),
                FigureEdge(3, 5, "5", directed = true),
                FigureEdge(4, 5, "9", directed = true),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A DAG has no cycles, so its vertices can be linearised: every edge points forward. Sweep the vertices in that order and each one's answer is final by the time you reach it — the DP has no circular dependency to resolve.",
        "This is what makes longest path tractable on a DAG (NP-hard on general graphs) and what turns path counting, earliest/latest start times and grid DP with arbitrary moves into one linear pass.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Prerequisites or one-way dependencies plus a best/count/longest question — course chains, build times, path counts, matrix increasing-path length.", 0xFFF59E0B),
        StepCard(2, "Prove It Is Acyclic", "Kahn's algorithm produces a full order only if the graph is a DAG. A short order means a cycle, and the DP is undefined.", 0xFF3B82F6),
        StepCard(3, "Relax Forward in Order", "For each u in topological order and each edge u → v: dp[v] = best(dp[v], dp[u] + w). No vertex is read before it is settled.", 0xFFEF4444),
        StepCard(4, "Pick the Aggregate", "max for longest path, min for earliest completion, sum for counting paths. Only the combining operator changes.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time / Space", "O(V + E) / O(V)", "One topological sort plus one relaxation sweep."),
        FormulaEntry("Longest path", "dp[v] = max(dp[u] + w(u,v)) over incoming u", "NP-hard on general graphs, linear on a DAG."),
        FormulaEntry("Path counting", "ways[v] = Σ ways[u]", "Same sweep, sum instead of max — take it modulo when asked."),
    ),
    notationKey = listOf(
        NotationEntry("dp[v]", "best value of any path ending at v"),
        NotationEntry("indeg[v]", "remaining unprocessed incoming edges"),
        NotationEntry("order", "the topological linearisation"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Longest path and path count on a DAG (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                from collections import deque

                def dag_dp(n, edges):                      # edges: (u, v, weight)
                    adj = [[] for _ in range(n)]
                    indeg = [0] * n
                    for u, v, w in edges:
                        adj[u].append((v, w))
                        indeg[v] += 1

                    order, dq = [], deque(v for v in range(n) if indeg[v] == 0)
                    while dq:                              # Kahn's linearisation
                        u = dq.popleft()
                        order.append(u)
                        for v, _ in adj[u]:
                            indeg[v] -= 1
                            if indeg[v] == 0:
                                dq.append(v)
                    if len(order) < n:
                        raise ValueError("graph has a cycle — DP undefined")

                    longest = [0] * n
                    ways = [1] * n                         # paths ending at each source
                    for u in order:                        # every edge relaxed once, forward
                        for v, w in adj[u]:
                            longest[v] = max(longest[v], longest[u] + w)
                            ways[v] += ways[u]
                    return max(longest), ways
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF3B82F6, "Critical Path", "Project scheduling: longest chain of dependent tasks sets the deadline."),
        ApplicationCard("code", 0xFF10B981, "Build Systems", "Earliest start times and total build time across a dependency graph."),
        ApplicationCard("map", 0xFF8B5CF6, "Increasing Paths", "Longest increasing path in a matrix — a DAG implied by the value ordering."),
    ),
    takeaways = listOf(
        "The topological order is the iteration order; the DP itself is one relaxation sweep.",
        "Longest path is easy here and NP-hard in general — the acyclicity is doing the work.",
        "A truncated Kahn order means a cycle: report it rather than producing a wrong answer.",
        "Memoized DFS over the same graph is the equivalent top-down form when edges are implicit.",
    ),
    crossLinks = listOf(
        CrossLink("topological_sort", "Topological Sort (Algorithms)"),
        CrossLink("topological_sort_pattern", "Topological Sort Pattern"),
        CrossLink("memo_recursion_pattern", "Top-down Memoization"),
    ),
)
