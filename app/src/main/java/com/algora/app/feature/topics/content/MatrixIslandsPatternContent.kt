package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Uses the standard 7-section template; cross-links to the Algorithms
// topic that implements the technique.
internal val matrixIslandsPatternContent = TopicContent(
    topicId = "matrix_islands_pattern",
    whatIsIt = listOf(
        "A grid is a graph whose nodes are cells and whose edges are the four (sometimes eight) neighbours. Once you see it that way, \"count the islands\" is just \"count the connected components\".",
        "The pattern is an outer scan that starts a flood fill on every unvisited qualifying cell. Each fill consumes one whole region, so the number of fills started is the answer.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "A 2-D grid plus regions, spreading, enclosure or reachability.", 0xFFF59E0B),
        StepCard(2, "Scan for Seeds", "Sweep every cell; when an unvisited land cell appears, increment the count and flood from it.", 0xFF3B82F6),
        StepCard(3, "Flood the Region", "DFS or BFS over neighbours, bounds-checking first, marking visited as you enqueue — not as you pop.", 0xFFEF4444),
        StepCard(4, "Choose Your Marking", "A visited set is O(rc) extra; overwriting land with water is O(1) but mutates the input. Say which you're doing.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(r · c)", "Every cell is examined a constant number of times."),
        FormulaEntry("Space", "O(r · c)", "Worst case the whole grid is one region on the stack or queue."),
        FormulaEntry("Multi-source BFS", "O(r · c)", "Seed every source at once — rotting oranges, nearest-zero distance."),
    ),
    notationKey = listOf(
        NotationEntry("r, c", "rows and columns"),
        NotationEntry("4-dir", "up/down/left/right; 8-dir adds the diagonals"),
        NotationEntry("(i, j)", "the cell being expanded"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Count islands with iterative flood fill (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                from collections import deque

                def count_islands(grid):
                    if not grid:
                        return 0
                    rows, cols = len(grid), len(grid[0])
                    islands = 0
                    for i in range(rows):
                        for j in range(cols):
                            if grid[i][j] != "1":
                                continue
                            islands += 1
                            q = deque([(i, j)])
                            grid[i][j] = "0"           # mark on enqueue, never on pop
                            while q:
                                r, c = q.popleft()
                                for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                                    nr, nc = r + dr, c + dc
                                    if 0 <= nr < rows and 0 <= nc < cols and grid[nr][nc] == "1":
                                        grid[nr][nc] = "0"
                                        q.append((nr, nc))
                    return islands
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.PathfindingGrid,
    applications = listOf(
        ApplicationCard("map", 0xFF3B82F6, "Region Counting", "Number of islands, max area, closed regions, surrounded zones."),
        ApplicationCard("flame", 0xFF10B981, "Spread Simulation", "Rotting oranges, fire or infection spreading a ring per time step."),
        ApplicationCard("chip", 0xFF8B5CF6, "Image Segmentation", "Connected-component labelling on a bitmap or occupancy grid."),
    ),
    takeaways = listOf(
        "A grid is a graph — components, not geometry.",
        "Mark visited when you enqueue; marking on pop lets a cell enter the queue several times.",
        "Bounds-check before reading the neighbour, not after.",
        "Multi-source BFS (all sources seeded at level 0) answers \"time for everything to be reached\" in one pass.",
    ),
    crossLinks = listOf(
        CrossLink("bfs", "Breadth-First Search (Algorithms)"),
        CrossLink("dfs", "Depth-First Search (Algorithms)"),
        CrossLink("union_find_pattern", "Union-Find Pattern"),
    ),
)
