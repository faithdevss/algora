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
internal val unionFindPatternContent = TopicContent(
    topicId = "union_find_pattern",
    figure = Figure(
        caption = "Components are stored as trees pointing at their roots, so \"are these connected\" " +
            "is two finds and a comparison. An edge whose endpoints already share a root would close a " +
            "cycle — that check is how Kruskal rejects edges and how cycle detection is written.",
        shape = FigureShape.Graph(
            nodes = listOf(
                FigureGraphNode("A", 0.10f, 0.20f, FigureTone.Accent),
                FigureGraphNode("B", 0.10f, 0.80f, FigureTone.Primary),
                FigureGraphNode("C", 0.40f, 0.50f, FigureTone.Primary),
                FigureGraphNode("D", 0.70f, 0.20f, FigureTone.Accent),
                FigureGraphNode("E", 0.70f, 0.80f, FigureTone.Primary),
                FigureGraphNode("F", 0.96f, 0.50f, FigureTone.Primary),
            ),
            edges = listOf(
                FigureEdge(1, 0, directed = true, tone = FigureTone.Primary),
                FigureEdge(2, 0, directed = true, tone = FigureTone.Primary),
                FigureEdge(1, 2, tone = FigureTone.Warn),
                FigureEdge(4, 3, directed = true, tone = FigureTone.Primary),
                FigureEdge(5, 3, directed = true, tone = FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Union-Find answers \"are these two in the same group?\" and \"merge these groups\" in near-constant time, by keeping each group as a tree and caring only about which root it hangs from.",
        "It beats BFS/DFS whenever the edges arrive one at a time — connectivity as a stream — because re-running a traversal after every new edge would be O(E) each time.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Merging groups, counting components, or detecting a cycle as undirected edges are added.", 0xFFF59E0B),
        StepCard(2, "Find the Root", "Follow parent pointers to the representative. Path compression re-points every node visited straight at the root.", 0xFF3B82F6),
        StepCard(3, "Union by Rank/Size", "Attach the smaller tree under the larger one so depth stays shallow.", 0xFF8B5CF6),
        StepCard(4, "Read the Counter", "Start with n components and decrement on each successful union. A union that finds equal roots is a cycle.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Per operation", "O(α(n))", "Inverse Ackermann — under 5 for any n you will ever see."),
        FormulaEntry("Space", "O(n)", "One parent and one rank entry per element."),
        FormulaEntry("Components", "n − successful unions", "Every real merge removes exactly one group."),
    ),
    notationKey = listOf(
        NotationEntry("parent[x]", "x's parent; a root points at itself"),
        NotationEntry("rank[x]", "upper bound on the tree's height"),
        NotationEntry("α(n)", "inverse Ackermann, effectively constant"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Union-Find with both optimisations (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                class DSU:
                    def __init__(self, n):
                        self.parent = list(range(n))
                        self.rank = [0] * n
                        self.count = n                 # components

                    def find(self, x):
                        while self.parent[x] != x:
                            self.parent[x] = self.parent[self.parent[x]]   # path compression
                            x = self.parent[x]
                        return x

                    def union(self, a, b):
                        ra, rb = self.find(a), self.find(b)
                        if ra == rb:
                            return False               # already together -> cycle edge
                        if self.rank[ra] < self.rank[rb]:
                            ra, rb = rb, ra
                        self.parent[rb] = ra
                        self.rank[ra] += self.rank[ra] == self.rank[rb]
                        self.count -= 1
                        return True
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GraphAlgorithmPlayer,
    applications = listOf(
        ApplicationCard("network", 0xFF3B82F6, "Connectivity Queries", "Number of provinces, redundant connection, accounts merge."),
        ApplicationCard("chip", 0xFF10B981, "MST Construction", "Kruskal uses exactly this to reject edges that would close a cycle."),
        ApplicationCard("users", 0xFF8B5CF6, "Entity Resolution", "Clustering duplicate records or friend groups as matches stream in."),
    ),
    takeaways = listOf(
        "Both optimisations matter: path compression alone or rank alone is asymptotically worse.",
        "A union whose two finds agree is exactly a cycle in an undirected graph.",
        "Keep a live component counter instead of recounting roots.",
        "It cannot un-merge — if the problem removes edges, run it backwards in time.",
    ),
    crossLinks = listOf(
        CrossLink("disjoint_set", "Disjoint Set / Union-Find (Data Structures)"),
        CrossLink("kruskals_mst", "Kruskal's MST (Algorithms)"),
        CrossLink("matrix_islands_pattern", "Matrix Traversal (Islands)"),
    ),
)
