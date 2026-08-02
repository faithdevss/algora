package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. One BFS from many starts, instead of one BFS per start — the
// difference between O(V + E) and O(k(V + E)).
internal val multiSourceBfsPatternContent = TopicContent(
    topicId = "multi_source_bfs_pattern",
    whatIsIt = listOf(
        "Multi-source BFS seeds the queue with every starting cell at once, all at distance 0. The wave spreads from all of them simultaneously, so the first time a cell is reached, it is reached by its nearest source.",
        "It replaces \"run BFS from each source and take the minimum\", turning O(k(V + E)) into a single O(V + E) sweep. The trick is purely in the initialisation — the loop body is ordinary BFS.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Nearest-of-many: distance to the closest gate, rotten orange, water cell or infected node — on an unweighted graph or grid.", 0xFFF59E0B),
        StepCard(2, "Seed Every Source", "Push all sources before the loop starts and mark them visited with distance 0. Never enqueue a source twice.", 0xFF3B82F6),
        StepCard(3, "Expand Level by Level", "Pop, visit unvisited neighbours, set dist[nbr] = dist[cur] + 1, mark visited at push time so a cell never enters the queue twice.", 0xFFEF4444),
        StepCard(4, "Read the Frontier", "The number of levels processed is the time for the whole grid to be covered; unvisited cells at the end are unreachable.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(V + E)", "On an R×C grid, O(R·C) — each cell is dequeued once with 4 neighbour checks."),
        FormulaEntry("Naive alternative", "O(k(V + E))", "One BFS per source, then a minimum — the same answer, k times the work."),
        FormulaEntry("Space", "O(V)", "Queue plus distance/visited grid."),
    ),
    notationKey = listOf(
        NotationEntry("k", "number of sources"),
        NotationEntry("dist[v]", "steps from the nearest source to v"),
        NotationEntry("frontier", "all cells at the current distance"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Nearest source on a grid (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                from collections import deque

                def nearest_source(grid, sources):
                    rows, cols = len(grid), len(grid[0])
                    dist = [[-1] * cols for _ in range(rows)]
                    dq = deque()
                    for r, c in sources:                     # every source starts at 0
                        dist[r][c] = 0
                        dq.append((r, c))

                    while dq:
                        r, c = dq.popleft()
                        for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                            nr, nc = r + dr, c + dc
                            if 0 <= nr < rows and 0 <= nc < cols \
                               and dist[nr][nc] == -1 and grid[nr][nc] != '#':
                                dist[nr][nc] = dist[r][c] + 1
                                dq.append((nr, nc))
                    return dist                              # -1 means unreachable
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PathfindingGrid,
    applications = listOf(
        ApplicationCard("map", 0xFF3B82F6, "Rotting Oranges", "Minutes until every fresh cell is reached — the level count of the wave."),
        ApplicationCard("target", 0xFF10B981, "Walls and Gates", "Fill every room with its distance to the nearest gate in one pass."),
        ApplicationCard("network", 0xFF8B5CF6, "Service Coverage", "Distance from every node to its closest data centre or cache."),
    ),
    takeaways = listOf(
        "The whole pattern is the initialisation — seed all sources at distance 0, then run plain BFS.",
        "Mark visited when you enqueue, not when you dequeue, or cells get queued repeatedly.",
        "Valid only for unit edge costs; with weights it becomes Dijkstra from a virtual super-source.",
        "Cells still unvisited when the queue drains are genuinely unreachable — report them, do not loop forever.",
    ),
    crossLinks = listOf(
        CrossLink("bfs", "Breadth-First Search (Algorithms)"),
        CrossLink("matrix_islands_pattern", "Matrix Traversal (Islands)"),
        CrossLink("queue", "Queue (Data Structures)"),
    ),
)
