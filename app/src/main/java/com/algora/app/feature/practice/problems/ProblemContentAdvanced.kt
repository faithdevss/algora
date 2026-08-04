package com.algora.app.feature.practice.problems

import com.algora.app.core.data.model.Difficulty

// Problems for the topics added after the original bank: string matching, the advanced graph
// algorithms, state-compression DP, and the evaluation/ensemble side of ML. Same shape as the other
// content files — ProblemRegistry aggregates them, nothing here is wired anywhere else.

internal val advancedPatterns = listOf(
    ProblemPattern(
        id = "string_matching",
        name = "String Matching",
        blurb = "Prefix tables, rolling hashes and palindromic radii — scanning text without ever backing up.",
        accentColor = 0xFF8B5CF6,
        topicId = "kmp",
        isPremium = true,
    ),
    ProblemPattern(
        id = "graph_advanced",
        name = "Advanced Graphs",
        blurb = "Ordering a DAG, saturating a network, and finding the edges a graph cannot afford to lose.",
        accentColor = 0xFF3B82F6,
        topicId = "topological_sort",
        isPremium = true,
    ),
    ProblemPattern(
        id = "state_compression",
        name = "State-Compression DP",
        blurb = "When the DP state is a set or a subtree rather than an index.",
        accentColor = 0xFFEC4899,
        topicId = "bitmask_dp",
        isPremium = true,
    ),
    ProblemPattern(
        id = "model_metrics",
        name = "Model Evaluation & Ensembles",
        blurb = "Scoring a classifier honestly, and combining weak models into a strong one.",
        accentColor = 0xFF6366F1,
        topicId = "model_evaluation",
        isPremium = true,
    ),
)

// ── String matching ──────────────────────────────────────────────────────────

private val stringMatchingProblems = listOf(
    PracticeProblem(
        id = "implement_strstr",
        title = "Find the First Occurrence",
        patternId = "string_matching",
        difficulty = Difficulty.BEGINNER,
        prompt = "Return the index of the first occurrence of `needle` in `haystack`, or -1 if it is not present. Solve it in O(n + m) — the naive nested loop is O(n·m) and is not accepted.",
        examples = listOf(
            ProblemExample("haystack = \"sadbutsad\", needle = \"sad\"", "0", "The later occurrence at index 6 is also a match, but the first one wins."),
            ProblemExample("haystack = \"leetcode\", needle = \"leeto\"", "-1", ""),
            ProblemExample("haystack = \"aaaaab\", needle = \"aaab\"", "2", "The case where naive matching wastes the most work."),
        ),
        constraints = listOf(
            "1 ≤ haystack.length, needle.length ≤ 10⁴.",
            "Lowercase English letters only.",
        ),
        hints = listOf(
            "When a match breaks after j characters, those j characters are already known — the naive loop throws that knowledge away and rereads them.",
            "Precompute, for each prefix of the needle, the longest proper prefix of it that is also a suffix. That number is how much of the match survives a mismatch.",
            "With that table, the haystack pointer never has to move backwards: only the needle pointer falls back.",
        ),
        approach = listOf(
            "Build the LPS table over the needle in O(m) — it is KMP run against itself.",
            "Scan the haystack with i, tracking j = how many needle characters currently match.",
            "On a mismatch with j > 0, set j = lps[j−1] and retry the same i; with j == 0, just advance i.",
            "When j reaches m, the match started at i − m + 1.",
        ),
        timeComplexity = "O(n + m)",
        spaceComplexity = "O(m) for the LPS table",
        solutionCode = """
            fun strStr(haystack: String, needle: String): Int {
                if (needle.isEmpty()) return 0
                val lps = IntArray(needle.length)
                var len = 0
                var k = 1
                while (k < needle.length) {
                    when {
                        needle[k] == needle[len] -> { len++; lps[k] = len; k++ }
                        len > 0 -> len = lps[len - 1]
                        else -> { lps[k] = 0; k++ }
                    }
                }

                var j = 0
                for (i in haystack.indices) {
                    while (j > 0 && haystack[i] != needle[j]) j = lps[j - 1]
                    if (haystack[i] == needle[j]) j++
                    if (j == needle.length) return i - j + 1
                }
                return -1
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("kmp", "KMP String Matching", "The LPS table and the fallback rule are the whole solution."),
            ProblemPrereq("string", "String", "Indexing and comparison costs on a character sequence."),
        ),
        linkedTopicId = "kmp",
        linkedTopicLabel = "KMP String Matching",
    ),
    PracticeProblem(
        id = "repeated_substring_pattern",
        title = "Built From a Repeated Block",
        patternId = "string_matching",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Given a string s, decide whether it can be constructed by taking some proper substring of it and concatenating that substring two or more times.",
        examples = listOf(
            ProblemExample("s = \"abab\"", "true", "\"ab\" twice."),
            ProblemExample("s = \"aba\"", "false", ""),
            ProblemExample("s = \"abcabcabcabc\"", "true", "\"abc\" four times — but \"abcabc\" twice also works."),
        ),
        constraints = listOf(
            "1 ≤ s.length ≤ 10⁴.",
            "Aim for O(n); trying every divisor length is O(n·√n) at best and O(n²) written naively.",
        ),
        hints = listOf(
            "If s is a block repeated k times, then s overlaps itself when shifted by one block length.",
            "The LPS table's last entry is the longest proper prefix that is also a suffix — call it L. The candidate block length is n − L.",
            "s is periodic exactly when n − L divides n evenly and L > 0.",
        ),
        approach = listOf(
            "Build the LPS table for s.",
            "Let L = lps[n−1]; the smallest period is n − L.",
            "Return L > 0 && n % (n − L) == 0.",
            "The alternative trick — search for s inside (s + s) with the first and last characters removed — works for the same reason.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(n)",
        solutionCode = """
            fun repeatedSubstringPattern(s: String): Boolean {
                val n = s.length
                val lps = IntArray(n)
                var len = 0
                var k = 1
                while (k < n) {
                    when {
                        s[k] == s[len] -> { len++; lps[k] = len; k++ }
                        len > 0 -> len = lps[len - 1]
                        else -> { lps[k] = 0; k++ }
                    }
                }

                val longestBorder = lps[n - 1]
                val period = n - longestBorder
                // Periodic iff the border is non-empty and the period tiles the string exactly.
                return longestBorder > 0 && n % period == 0
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("kmp", "KMP String Matching", "The border/period relationship the LPS table encodes."),
            ProblemPrereq("rabin_karp", "Rabin-Karp", "The hashing alternative, and why it needs verification."),
        ),
        linkedTopicId = "kmp",
        linkedTopicLabel = "KMP String Matching",
    ),
    PracticeProblem(
        id = "longest_palindromic_substring_problem",
        title = "Longest Palindromic Substring",
        patternId = "string_matching",
        difficulty = Difficulty.ADVANCED,
        prompt = "Return the longest contiguous substring of s that reads the same forwards and backwards. Give the O(n²) centre-expansion solution first, then the linear one.",
        examples = listOf(
            ProblemExample("s = \"babad\"", "\"bab\"", "\"aba\" is equally long and also accepted."),
            ProblemExample("s = \"cbbd\"", "\"bb\"", "An even-length palindrome — the case that breaks a naive odd-only loop."),
            ProblemExample("s = \"aaaaa\"", "\"aaaaa\"", "Where centre expansion does the most redundant work."),
        ),
        constraints = listOf(
            "1 ≤ s.length ≤ 10⁵ for the linear solution.",
            "Return any one answer if several are tied.",
        ),
        hints = listOf(
            "Every palindrome has a centre — but with 2n−1 centres, since an even-length one is centred between two characters.",
            "Interleaving a separator ('#a#b#a#') makes every palindrome odd-length, collapsing the two cases into one loop.",
            "Inside an already-known palindrome, the mirror position on its left half has the same radius — start from that value instead of zero.",
        ),
        approach = listOf(
            "Transform s by inserting '#' between every character and at both ends.",
            "Track c and r: the centre and right edge of the palindrome reaching furthest right.",
            "For each i < r, seed p[i] = min(r − i, p[2c − i]); then expand by direct comparison past the seed.",
            "If i + p[i] passes r, re-centre on i. The right edge only moves right, which bounds the total work at O(n).",
        ),
        timeComplexity = "O(n) with Manacher, O(n²) with plain centre expansion",
        spaceComplexity = "O(n)",
        solutionCode = """
            fun longestPalindrome(s: String): String {
                if (s.isEmpty()) return ""
                val t = buildString {
                    append('#')
                    for (ch in s) { append(ch); append('#') }
                }

                val p = IntArray(t.length)
                var c = 0
                var r = 0
                for (i in t.indices) {
                    if (i < r) p[i] = minOf(r - i, p[2 * c - i])      // reuse the mirror
                    while (i - p[i] - 1 >= 0 && i + p[i] + 1 < t.length &&
                        t[i - p[i] - 1] == t[i + p[i] + 1]
                    ) p[i]++
                    if (i + p[i] > r) { c = i; r = i + p[i] }
                }

                val best = p.indices.maxBy { p[it] }
                val start = (best - p[best]) / 2
                return s.substring(start, start + p[best])
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("manacher", "Manacher's Algorithm", "The mirror seed and the monotonic right edge."),
            ProblemPrereq("two_pointer", "Two Pointer Technique", "Centre expansion is a two-pointer walk outward."),
        ),
        linkedTopicId = "manacher",
        linkedTopicLabel = "Manacher's Algorithm",
    ),
)

// ── Advanced graphs ──────────────────────────────────────────────────────────

private val advancedGraphProblems = listOf(
    PracticeProblem(
        id = "course_schedule_order",
        title = "Course Schedule Order",
        patternId = "graph_advanced",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "There are n courses labelled 0..n−1 and a list of prerequisite pairs [a, b] meaning b must be taken before a. Return any valid ordering of all courses, or an empty list if none exists.",
        examples = listOf(
            ProblemExample("n = 4, prerequisites = [[1,0],[2,0],[3,1],[3,2]]", "[0,1,2,3]", "[0,2,1,3] is equally valid."),
            ProblemExample("n = 2, prerequisites = [[1,0],[0,1]]", "[]", "A cycle — neither course can go first."),
        ),
        constraints = listOf(
            "1 ≤ n ≤ 2000, 0 ≤ prerequisites.length ≤ 5000.",
            "No duplicate pairs.",
        ),
        hints = listOf(
            "A course is takeable when every course pointing at it is already done — that count is its in-degree.",
            "Start from the courses with in-degree 0 and remove them, decrementing their neighbours.",
            "You do not need a separate cycle check: count what you emitted.",
        ),
        approach = listOf(
            "Build the adjacency list and the in-degree array in one pass over the pairs.",
            "Seed a queue with every node whose in-degree is 0.",
            "Pop, append to the order, and decrement each neighbour; push any that hit 0.",
            "If the order is shorter than n, the remaining nodes sit on a cycle — return empty.",
        ),
        timeComplexity = "O(V + E)",
        spaceComplexity = "O(V + E)",
        solutionCode = """
            fun findOrder(n: Int, prerequisites: Array<IntArray>): IntArray {
                val adj = Array(n) { mutableListOf<Int>() }
                val inDegree = IntArray(n)
                for ((course, prereq) in prerequisites.map { it[0] to it[1] }) {
                    adj[prereq] += course
                    inDegree[course]++
                }

                val ready = ArrayDeque<Int>()
                for (v in 0 until n) if (inDegree[v] == 0) ready += v

                val order = mutableListOf<Int>()
                while (ready.isNotEmpty()) {
                    val u = ready.removeFirst()
                    order += u
                    for (v in adj[u]) if (--inDegree[v] == 0) ready += v
                }

                // Fewer than n emitted means a cycle swallowed the rest.
                return if (order.size == n) order.toIntArray() else IntArray(0)
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("topological_sort", "Topological Sort", "Kahn's algorithm, including the emitted-count cycle test."),
            ProblemPrereq("queue", "Queue", "The ready set is a FIFO queue."),
            ProblemPrereq("graph", "Graph", "Adjacency-list representation of the prerequisite pairs."),
        ),
        linkedTopicId = "topological_sort",
        linkedTopicLabel = "Topological Sort",
    ),
    PracticeProblem(
        id = "critical_connections",
        title = "Critical Connections in a Network",
        patternId = "graph_advanced",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given an undirected connected network of n servers and a list of connections, return all critical connections — the edges whose removal would disconnect some server from the rest.",
        examples = listOf(
            ProblemExample("n = 4, connections = [[0,1],[1,2],[2,0],[1,3]]", "[[1,3]]", "The triangle 0-1-2 has no critical edge; each of its sides is bypassed by the other two."),
            ProblemExample("n = 2, connections = [[0,1]]", "[[0,1]]", "The only edge is by definition critical."),
        ),
        constraints = listOf(
            "2 ≤ n ≤ 10⁵, n−1 ≤ connections.length ≤ 10⁵.",
            "The graph is connected and has no repeated edges.",
        ),
        hints = listOf(
            "An edge is safe exactly when some other path bypasses it — that is, when a back edge climbs over it.",
            "Stamp each node with its DFS discovery time, and compute how far up the tree its subtree can reach using one back edge.",
            "Edge u–v is critical when v's subtree cannot reach at or above u: low[v] > disc[u]. Note the strict >.",
        ),
        approach = listOf(
            "Run one DFS, assigning disc[u] in visit order and initialising low[u] = disc[u].",
            "For a back edge u → w, pull low[u] down to disc[w].",
            "After recursing into a child v, pull low[u] down to low[v], then test low[v] > disc[u].",
            "Skip only the edge you arrived on — with parallel edges, skip by edge id rather than by parent vertex.",
        ),
        timeComplexity = "O(V + E)",
        spaceComplexity = "O(V + E)",
        solutionCode = """
            fun criticalConnections(n: Int, connections: List<List<Int>>): List<List<Int>> {
                val adj = Array(n) { mutableListOf<Int>() }
                for (edge in connections) {
                    adj[edge[0]] += edge[1]
                    adj[edge[1]] += edge[0]
                }

                val disc = IntArray(n) { -1 }
                val low = IntArray(n)
                val bridges = mutableListOf<List<Int>>()
                var timer = 0

                fun dfs(u: Int, parent: Int) {
                    disc[u] = timer
                    low[u] = timer
                    timer++
                    for (v in adj[u]) {
                        if (v == parent) continue
                        if (disc[v] != -1) {
                            low[u] = minOf(low[u], disc[v])      // back edge
                        } else {
                            dfs(v, u)
                            low[u] = minOf(low[u], low[v])
                            // Strict >: nothing in v's subtree bypasses this edge.
                            if (low[v] > disc[u]) bridges += listOf(u, v)
                        }
                    }
                }

                dfs(0, -1)
                return bridges
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("articulation_points", "Articulation Points & Bridges", "The disc/low computation and the strict > bridge test."),
            ProblemPrereq("dfs", "Depth-First Search (DFS)", "Tree edges versus back edges is the distinction the whole method rests on."),
        ),
        linkedTopicId = "articulation_points",
        linkedTopicLabel = "Articulation Points & Bridges",
    ),
    PracticeProblem(
        id = "bipartite_matching_flow",
        title = "Maximum Bipartite Matching",
        patternId = "graph_advanced",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given m applicants, n jobs, and a list of which applicant can do which job, assign as many applicants as possible so that no applicant has two jobs and no job has two applicants. Return the size of the largest assignment.",
        examples = listOf(
            ProblemExample("applicants = 3, jobs = 3, edges = [[0,0],[0,1],[1,0],[2,1]]", "2", "Applicant 2 can only do job 1, which forces applicant 0 onto job 0 and leaves applicant 1 unassigned."),
            ProblemExample("applicants = 2, jobs = 2, edges = [[0,0],[1,1]]", "2", ""),
        ),
        constraints = listOf(
            "1 ≤ m, n ≤ 500.",
            "A greedy first-come assignment is wrong — it must be able to reassign earlier choices.",
        ),
        hints = listOf(
            "Add a source pointing at every applicant and a sink every job points at, all capacities 1. Max flow is then exactly the matching size.",
            "The augmenting-path search is what lets an earlier assignment be undone — that is the residual edge doing its job.",
            "Written directly, this is the Hungarian/Kuhn's algorithm: for each applicant, DFS for an augmenting path, bumping already-matched applicants along the way.",
        ),
        approach = listOf(
            "For each applicant in turn, try to find an augmenting path with a fresh `seen` array.",
            "For each job the applicant can do: if it is free, take it; if it is taken, recursively ask its current holder to move elsewhere.",
            "Every successful search increases the matching by exactly one.",
            "Unit capacities make this O(V·E) — the specialisation of Ford-Fulkerson to bipartite graphs.",
        ),
        timeComplexity = "O(V·E) — one augmenting search per applicant",
        spaceComplexity = "O(V + E)",
        solutionCode = """
            fun maxMatching(applicants: Int, jobs: Int, edges: List<Pair<Int, Int>>): Int {
                val canDo = Array(applicants) { mutableListOf<Int>() }
                for ((a, j) in edges) canDo[a] += j

                val matchedTo = IntArray(jobs) { -1 }        // job -> applicant, or -1

                fun augment(applicant: Int, seen: BooleanArray): Boolean {
                    for (job in canDo[applicant]) {
                        if (seen[job]) continue
                        seen[job] = true
                        // Free job, or its holder can be pushed somewhere else.
                        if (matchedTo[job] == -1 || augment(matchedTo[job], seen)) {
                            matchedTo[job] = applicant
                            return true
                        }
                    }
                    return false
                }

                var total = 0
                for (a in 0 until applicants) {
                    if (augment(a, BooleanArray(jobs))) total++
                }
                return total
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("max_flow", "Max Flow (Ford-Fulkerson)", "Matching is unit-capacity max flow; augmenting paths are the reassignment mechanism."),
            ProblemPrereq("dfs", "Depth-First Search (DFS)", "The augmenting-path search itself."),
        ),
        linkedTopicId = "max_flow",
        linkedTopicLabel = "Max Flow (Ford-Fulkerson)",
    ),
)

// ── State-compression DP ─────────────────────────────────────────────────────

private val stateCompressionProblems = listOf(
    PracticeProblem(
        id = "tree_house_robber",
        title = "Rob the Tree",
        patternId = "state_compression",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Each node of a tree holds an amount of money. You may not take money from two nodes joined by an edge. Return the maximum total you can take.",
        examples = listOf(
            ProblemExample("root = [3,2,3,null,3,null,1]", "7", "3 + 3 + 1 — the root plus two grandchildren."),
            ProblemExample("root = [3,4,5,1,3,null,1]", "9", "4 + 5 — skipping the root beats taking it."),
        ),
        constraints = listOf(
            "1 ≤ node count ≤ 10⁴.",
            "Values are non-negative.",
            "Recomputing a subtree for both parent states is O(2ⁿ) — return both answers at once instead.",
        ),
        hints = listOf(
            "The only thing a parent needs to know about a child's subtree is two numbers: the best with the child taken, and the best without.",
            "Taking a node forbids taking any of its children; skipping it leaves each child free to do whichever is better.",
            "Compute children before parents — a post-order traversal.",
        ),
        approach = listOf(
            "Define dfs(node) returning the pair (bestWithoutNode, bestWithNode).",
            "withNode = value + Σ child.without.",
            "withoutNode = Σ max(child.with, child.without).",
            "The answer is max of the pair at the root. Every subtree is visited once, so this is O(n).",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(h) for the recursion stack",
        solutionCode = """
            class TreeNode(val value: Int, val left: TreeNode? = null, val right: TreeNode? = null)

            fun rob(root: TreeNode?): Int {
                // Returns (best if this node is NOT taken, best if it IS taken).
                fun dfs(node: TreeNode?): Pair<Int, Int> {
                    if (node == null) return 0 to 0
                    val (leftSkip, leftTake) = dfs(node.left)
                    val (rightSkip, rightTake) = dfs(node.right)

                    val take = node.value + leftSkip + rightSkip
                    val skip = maxOf(leftSkip, leftTake) + maxOf(rightSkip, rightTake)
                    return skip to take
                }

                val (skip, take) = dfs(root)
                return maxOf(skip, take)
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("tree_dp", "Tree DP", "The taken/not-taken state and the post-order combine."),
            ProblemPrereq("tree", "Tree", "Parent/child structure is what makes the subproblems well-founded."),
        ),
        linkedTopicId = "tree_dp",
        linkedTopicLabel = "Tree DP",
    ),
    PracticeProblem(
        id = "tsp_bitmask_problem",
        title = "Shortest Tour Visiting Every City",
        patternId = "state_compression",
        difficulty = Difficulty.ADVANCED,
        prompt = "Given an n × n matrix of travel costs, find the cheapest tour that starts at city 0, visits every city exactly once, and returns to city 0.",
        examples = listOf(
            ProblemExample("cost = [[0,10,15,20],[10,0,35,25],[15,35,0,30],[20,25,30,0]]", "80", "0 → 1 → 3 → 2 → 0."),
            ProblemExample("n = 2, cost = [[0,5],[5,0]]", "10", "Out and back."),
        ),
        constraints = listOf(
            "2 ≤ n ≤ 20 — beyond that even 2ⁿ·n² is too large.",
            "Costs are non-negative and need not be symmetric.",
        ),
        hints = listOf(
            "Enumerating tours is O(n!). But two partial tours visiting the same set of cities and ending at the same city are interchangeable from here on.",
            "So the state is (set of visited cities, current city) — and a set of at most 20 elements fits in an Int.",
            "Iterate masks in increasing numeric order: adding a city only ever makes the mask larger, so dependencies are already resolved.",
        ),
        approach = listOf(
            "dp[mask][last] = cheapest way to have visited exactly `mask` and be standing at `last`.",
            "Base case dp[1][0] = 0 — only city 0 visited, standing there.",
            "Transition: from (mask, last) to (mask | 1<<next, next) for every next not yet in mask.",
            "Answer = min over last of dp[full][last] + cost[last][0].",
        ),
        timeComplexity = "O(2ⁿ · n²)",
        spaceComplexity = "O(2ⁿ · n)",
        solutionCode = """
            fun shortestTour(cost: Array<IntArray>): Int {
                val n = cost.size
                val full = (1 shl n) - 1
                val inf = Int.MAX_VALUE / 2
                val dp = Array(1 shl n) { IntArray(n) { inf } }
                dp[1][0] = 0                                   // start at city 0

                for (mask in 1..full) {
                    if (mask and 1 == 0) continue              // every tour includes city 0
                    for (last in 0 until n) {
                        val here = dp[mask][last]
                        if (here == inf || mask and (1 shl last) == 0) continue
                        for (next in 0 until n) {
                            if (mask and (1 shl next) != 0) continue
                            val nextMask = mask or (1 shl next)
                            val candidate = here + cost[last][next]
                            if (candidate < dp[nextMask][next]) dp[nextMask][next] = candidate
                        }
                    }
                }

                return (0 until n).minOf { last -> dp[full][last] + cost[last][0] }
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("bitmask_dp", "Bitmask DP", "The subset-as-index encoding and the mask-ordering argument."),
            ProblemPrereq("knapsack_01", "0/1 Knapsack", "The take-or-leave transition, generalised from an index to a set."),
        ),
        linkedTopicId = "bitmask_dp",
        linkedTopicLabel = "Bitmask DP",
    ),
)

// ── Model evaluation and ensembles ───────────────────────────────────────────

private val modelMetricProblems = listOf(
    PracticeProblem(
        id = "confusion_matrix_metrics",
        title = "Precision, Recall and F1 From Scores",
        patternId = "model_metrics",
        difficulty = Difficulty.BEGINNER,
        prompt = "Given predicted scores in [0,1], binary labels, and a decision threshold, compute the confusion matrix and return accuracy, precision, recall and F1.",
        examples = listOf(
            ProblemExample("scores = [0.9,0.4,0.8,0.2], labels = [1,0,0,0], threshold = 0.5", "TP=1 FP=1 FN=0 TN=2 → P=0.50 R=1.00 F1=0.67 acc=0.75", ""),
            ProblemExample("all labels 0, threshold = 0.5, no score above it", "P=0 R=0 F1=0 acc=1.00", "The degenerate case your code must not divide by zero on."),
        ),
        constraints = listOf(
            "Return 0 for any metric whose denominator is zero rather than throwing.",
            "A prediction is positive when score ≥ threshold.",
        ),
        hints = listOf(
            "Every metric here is a ratio of the same four counts — build those first and derive the rest.",
            "Precision divides by what you predicted positive; recall divides by what actually was positive. Mixing them up is the classic error.",
            "F1 is the harmonic mean, not the arithmetic one, so a model that scores 1.0 and 0.0 gets 0, not 0.5.",
        ),
        approach = listOf(
            "Loop once, bucketing each sample into TP / FP / FN / TN.",
            "precision = TP/(TP+FP), recall = TP/(TP+FN), guarding both denominators.",
            "F1 = 2PR/(P+R), guarded the same way.",
            "Report accuracy too, but remember it is the metric that hides imbalance.",
        ),
        timeComplexity = "O(n)",
        spaceComplexity = "O(1)",
        solutionCode = """
            data class Metrics(val tp: Int, val fp: Int, val fn: Int, val tn: Int) {
                val accuracy get() = safeDiv(tp + tn, tp + fp + fn + tn)
                val precision get() = safeDiv(tp, tp + fp)
                val recall get() = safeDiv(tp, tp + fn)
                val f1 get() = if (precision + recall == 0.0) 0.0
                    else 2 * precision * recall / (precision + recall)

                private fun safeDiv(a: Int, b: Int) = if (b == 0) 0.0 else a.toDouble() / b
            }

            fun evaluate(scores: DoubleArray, labels: IntArray, threshold: Double): Metrics {
                var tp = 0; var fp = 0; var fn = 0; var tn = 0
                for (i in scores.indices) {
                    val predictedPositive = scores[i] >= threshold
                    when {
                        predictedPositive && labels[i] == 1 -> tp++
                        predictedPositive && labels[i] == 0 -> fp++
                        !predictedPositive && labels[i] == 1 -> fn++
                        else -> tn++
                    }
                }
                return Metrics(tp, fp, fn, tn)
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("model_evaluation", "Model Evaluation", "The confusion matrix and every ratio built on it."),
            ProblemPrereq("logistic_regression", "Logistic Regression", "Where the scores being thresholded come from."),
        ),
        linkedTopicId = "model_evaluation",
        linkedTopicLabel = "Model Evaluation",
    ),
    PracticeProblem(
        id = "roc_auc_from_scores",
        title = "ROC-AUC Without Sweeping Thresholds",
        patternId = "model_metrics",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Compute the area under the ROC curve from scores and binary labels — without enumerating thresholds. Handle tied scores correctly.",
        examples = listOf(
            ProblemExample("scores = [0.9,0.8,0.3,0.1], labels = [1,0,1,0]", "0.75", "3 of 4 positive/negative pairs are correctly ordered."),
            ProblemExample("scores = [0.5,0.5], labels = [1,0]", "0.5", "A tie counts as half a win — a coin flip."),
        ),
        constraints = listOf(
            "At least one positive and one negative; otherwise AUC is undefined — return 0.5.",
            "Aim for O(n log n), not the O(p·n) double loop.",
        ),
        hints = listOf(
            "AUC has a probabilistic reading: the chance a random positive scores above a random negative.",
            "That makes it a counting problem over pairs, and pair-counting over a sorted list is a rank problem.",
            "Sum the ranks of the positives, subtract the ranks they would have had if they were all at the bottom.",
        ),
        approach = listOf(
            "Sort by score ascending and assign ranks 1..n, giving tied scores their average rank.",
            "AUC = (Σ ranks of positives − P(P+1)/2) / (P·N), the Mann-Whitney U statistic.",
            "Averaging ranks across ties is exactly what makes a tie count as half a win.",
            "Note AUC is threshold-free — good for comparing models, useless for choosing an operating point.",
        ),
        timeComplexity = "O(n log n)",
        spaceComplexity = "O(n)",
        solutionCode = """
            fun rocAuc(scores: DoubleArray, labels: IntArray): Double {
                val positives = labels.count { it == 1 }
                val negatives = labels.size - positives
                if (positives == 0 || negatives == 0) return 0.5

                val order = scores.indices.sortedBy { scores[it] }
                val rank = DoubleArray(scores.size)

                // Tied scores share the average of the ranks they span.
                var i = 0
                while (i < order.size) {
                    var j = i
                    while (j + 1 < order.size && scores[order[j + 1]] == scores[order[i]]) j++
                    val averageRank = (i + j + 2) / 2.0        // ranks are 1-based
                    for (k in i..j) rank[order[k]] = averageRank
                    i = j + 1
                }

                val positiveRankSum = scores.indices.filter { labels[it] == 1 }.sumOf { rank[it] }
                return (positiveRankSum - positives * (positives + 1) / 2.0) / (positives.toDouble() * negatives)
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("model_evaluation", "Model Evaluation", "What ROC-AUC measures and when it misleads."),
            ProblemPrereq("merge_sort", "Merge Sort", "The O(n log n) sort that turns pair-counting into rank arithmetic."),
        ),
        linkedTopicId = "model_evaluation",
        linkedTopicLabel = "Model Evaluation",
    ),
    PracticeProblem(
        id = "bagging_vote_oob",
        title = "Bagging Vote with Out-of-Bag Scoring",
        patternId = "model_metrics",
        difficulty = Difficulty.INTERMEDIATE,
        prompt = "Implement bootstrap aggregation: draw B bootstrap samples, fit a weak learner on each, predict by majority vote, and report the out-of-bag accuracy — using, for each row, only the learners that never saw it.",
        examples = listOf(
            ProblemExample("B = 100 on a 50-row dataset", "each learner omits ~37% of rows", "The (1 − 1/n)ⁿ → 1/e limit."),
            ProblemExample("a row seen by every learner", "excluded from the OOB estimate", "There is no unbiased prediction available for it."),
        ),
        constraints = listOf(
            "Bootstrap sampling is with replacement, n draws from n rows.",
            "OOB accuracy must never use a learner that saw the row.",
        ),
        hints = listOf(
            "Record, per learner, which row indices it was trained on — the complement is its out-of-bag set.",
            "For a row's OOB prediction, poll only the learners whose bag excludes it.",
            "Skip rows no learner missed; averaging them in would leak training data into the estimate.",
        ),
        approach = listOf(
            "For each of B rounds: draw n indices with replacement, fit the learner, store the index set.",
            "Predict by majority vote across all B learners.",
            "For OOB accuracy, iterate rows; for each, vote among learners that excluded it, and compare to the true label.",
            "This gives validation for free — no held-out split needed, which is one of bagging's quiet advantages.",
        ),
        timeComplexity = "O(B · fit(n)) to train, O(B) per prediction",
        spaceComplexity = "O(B · n) for the bag membership sets",
        solutionCode = """
            class Bagging<M>(
                private val rounds: Int,
                private val fit: (List<Int>) -> M,
                private val predict: (M, Int) -> Int,
            ) {
                private val models = mutableListOf<M>()
                private val bags = mutableListOf<Set<Int>>()

                fun train(n: Int) {
                    repeat(rounds) {
                        val drawn = List(n) { (0 until n).random() }   // with replacement
                        models += fit(drawn)
                        bags += drawn.toSet()
                    }
                }

                fun vote(row: Int): Int =
                    models.map { predict(it, row) }
                        .groupingBy { it }.eachCount()
                        .maxBy { it.value }.key

                // Only learners that never saw `row` may score it.
                fun outOfBagAccuracy(n: Int, labels: IntArray): Double {
                    var scored = 0
                    var correct = 0
                    for (row in 0 until n) {
                        val judges = models.indices.filter { row !in bags[it] }
                        if (judges.isEmpty()) continue                 // seen by everyone: no honest vote
                        val prediction = judges.map { predict(models[it], row) }
                            .groupingBy { it }.eachCount()
                            .maxBy { it.value }.key
                        scored++
                        if (prediction == labels[row]) correct++
                    }
                    return if (scored == 0) 0.0 else correct.toDouble() / scored
                }
            }
        """.trimIndent(),
        prerequisites = listOf(
            ProblemPrereq("random_forest", "Random Forest", "Bagging, out-of-bag rows and majority voting."),
            ProblemPrereq("bias_variance", "Bias-Variance Tradeoff", "Why averaging decorrelated learners cuts variance without adding bias."),
        ),
        linkedTopicId = "random_forest",
        linkedTopicLabel = "Random Forest",
    ),
)

internal val advancedProblems: List<PracticeProblem> =
    stringMatchingProblems + advancedGraphProblems + stateCompressionProblems + modelMetricProblems
