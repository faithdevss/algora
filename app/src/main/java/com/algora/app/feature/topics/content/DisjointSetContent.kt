package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val disjointSetContent = TopicContent(
    topicId = "disjoint_set",
    whatIsIt = listOf(
        "A disjoint set (union-find) tracks a partition of elements into non-overlapping groups, answering 'are these two in the same group?' and merging groups almost instantly.",
        "Each group is a tree with a representative root; two optimizations — path compression and union by rank — flatten those trees so operations run in near-constant amortized time.",
    ),
    steps = listOf(
        StepCard(1, "Each Element Its Own Set", "Initially every element is its own root, a singleton group.", 0xFF10B981),
        StepCard(2, "Find the Root", "Follow parent pointers to the representative; along the way, point nodes straight at the root (path compression).", 0xFF3B82F6),
        StepCard(3, "Union Two Sets", "Merge by attaching the smaller/shorter tree under the larger root (union by rank/size).", 0xFFF59E0B),
        StepCard(4, "Same-Set Test", "Two elements share a group iff their roots are identical.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Amortized op", "O(α(n))", "Inverse Ackermann — effectively constant."),
        FormulaEntry("Path compression", "flattens find paths", "Repeated finds become direct root links."),
        FormulaEntry("Union by rank", "attach shorter under taller", "Keeps trees shallow."),
    ),
    notationKey = listOf(
        NotationEntry("root", "the representative element of a set"),
        NotationEntry("rank/size", "tree-depth or count heuristic guiding unions"),
        NotationEntry("α(n)", "inverse Ackermann — ≤ 4 for any practical n"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Union-find with both optimizations",
            accentColor = 0xFF6366F1,
            code = """
                class DisjointSet(n: Int) {
                    private val parent = IntArray(n) { it }
                    private val rank = IntArray(n)

                    fun find(x: Int): Int {
                        if (parent[x] != x) parent[x] = find(parent[x])   // path compression
                        return parent[x]
                    }

                    fun union(a: Int, b: Int) {
                        val ra = find(a); val rb = find(b)
                        if (ra == rb) return
                        when {                                            // union by rank
                            rank[ra] < rank[rb] -> parent[ra] = rb
                            rank[ra] > rank[rb] -> parent[rb] = ra
                            else -> { parent[rb] = ra; rank[ra]++ }
                        }
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TreeVisualizer,
    applications = listOf(
        ApplicationCard("share", 0xFF10B981, "Kruskal's MST", "Union-find is the cycle-detection engine inside Kruskal's minimum-spanning-tree algorithm."),
        ApplicationCard("map", 0xFF3B82F6, "Connected Components", "Grouping connected cells, pixels, or network nodes is a sequence of unions."),
        ApplicationCard("chip", 0xFFF59E0B, "Dynamic Connectivity", "Percolation, image segmentation, and account-merge systems query and merge groups online."),
    ),
    takeaways = listOf(
        "Union-find maintains disjoint groups with near-constant find and union.",
        "Path compression plus union by rank give O(α(n)) amortized operations.",
        "It powers Kruskal's MST, connected components, and dynamic-connectivity queries.",
        "It answers 'same group?' and merges fast, but can't split a group back apart.",
    ),
    crossLinks = listOf(
        CrossLink("kruskals_mst", "Kruskal's MST"),
        CrossLink("job_sequencing", "Job Sequencing with Deadlines"),
    ),
)
