package com.algora.app.feature.algorithms

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Topic

private fun topic(
    id: String,
    name: String,
    category: Category,
    tagline: String,
    isPremium: Boolean = false,
    iconName: String = category.iconName,
    accentColor: Long = category.accentColor,
) = Topic(
    id = id,
    name = name,
    categoryId = category.id,
    tagline = tagline,
    description = tagline,
    iconName = iconName,
    accentColor = accentColor,
    isPremium = isPremium,
)

private val sorting = AlgorithmsCategories.sorting
private val searching = AlgorithmsCategories.searching
private val recursion = AlgorithmsCategories.recursion
private val divideConquer = AlgorithmsCategories.divideConquer
private val greedy = AlgorithmsCategories.greedy
private val dp = AlgorithmsCategories.dynamicProgramming
private val graphAlgo = AlgorithmsCategories.graphAlgorithms
private val pathfinding = AlgorithmsCategories.pathfinding
private val misc = AlgorithmsCategories.miscAdvanced

private val sortingTopics = listOf(
    topic("bubble_sort", "Bubble Sort", sorting, "Repeatedly swaps adjacent out-of-order elements."),
    topic("selection_sort", "Selection Sort", sorting, "Repeatedly selects the minimum remaining element."),
    topic("insertion_sort", "Insertion Sort", sorting, "Builds a sorted prefix one element at a time."),
    topic("merge_sort", "Merge Sort", sorting, "Divide-and-conquer sort that merges sorted halves.", isPremium = true),
    topic("quick_sort", "Quick Sort", sorting, "Divide-and-conquer sort that partitions around a pivot.", isPremium = true),
    topic("heap_sort", "Heap Sort", sorting, "Sorts by repeatedly extracting the max from a heap.", isPremium = true),
    topic("counting_sort", "Counting Sort", sorting, "Non-comparison sort counting occurrences per key.", isPremium = true),
    topic("radix_sort", "Radix Sort", sorting, "Non-comparison sort processing digits/keys in passes.", isPremium = true),
    topic("bucket_sort", "Bucket Sort", sorting, "Scatters values into buckets, sorts each, then concatenates.", isPremium = true),
)

private val searchingTopics = listOf(
    topic("linear_search", "Linear Search", searching, "Checks every element in sequence."),
    topic("binary_search", "Binary Search", searching, "Halves a sorted search space each step."),
    topic("jump_search", "Jump Search", searching, "Skips ahead in fixed blocks, then scans linearly.", isPremium = true),
    topic("interpolation_search", "Interpolation Search", searching, "Estimates position from value distribution in sorted data.", isPremium = true),
    topic("exponential_search", "Exponential Search", searching, "Finds a bounding range, then binary searches within it.", isPremium = true),
)

private val recursionTopics = listOf(
    topic("factorial", "Factorial", recursion, "Classic base-case + recursive-case warm-up."),
    topic("fibonacci_recursive", "Fibonacci", recursion, "Naive recursive definition — and why it's exponential."),
    topic("n_queens", "N-Queens", recursion, "Backtracking placement of non-attacking queens.", isPremium = true),
    topic("sudoku_solver", "Sudoku Solver", recursion, "Constraint-based backtracking search.", isPremium = true),
    topic("subset_sum", "Subset Sum", recursion, "Backtracking search for a target-sum subset.", isPremium = true),
    topic("permutation_generation", "Permutation Generation", recursion, "Recursively builds every ordering of a set.", isPremium = true),
)

private val divideConquerTopics = listOf(
    topic("closest_pair_of_points", "Closest Pair of Points", divideConquer, "Finds the nearest pair in a plane faster than brute force."),
    topic("strassens_algorithm", "Strassen's Algorithm", divideConquer, "Faster-than-cubic matrix multiplication.", isPremium = true),
    topic("karatsubas_algorithm", "Karatsuba's Algorithm", divideConquer, "Sub-quadratic multiplication of large integers.", isPremium = true),
    topic("quickselect", "Quickselect", divideConquer, "Finds the k-th smallest element in linear expected time.", isPremium = true),
    topic("median_of_medians", "Median of Medians", divideConquer, "Guarantees linear worst-case selection.", isPremium = true),
    topic("tower_of_hanoi", "Tower of Hanoi", divideConquer, "Classic recursive disk-moving puzzle."),
)

private val greedyTopics = listOf(
    topic("fractional_knapsack", "Fractional Knapsack", greedy, "Greedy value-per-weight packing."),
    topic("huffman_coding", "Huffman Coding", greedy, "Builds an optimal prefix-free encoding."),
    topic("kruskals_mst", "Kruskal's MST", greedy, "Builds a minimum spanning tree by adding cheapest edges.", isPremium = true),
    topic("prims_mst", "Prim's MST", greedy, "Grows a minimum spanning tree from a starting node.", isPremium = true),
    topic("dijkstras_algorithm", "Dijkstra's Algorithm", greedy, "Greedy shortest paths from a single source.", isPremium = true),
    topic("job_sequencing", "Job Sequencing with Deadlines", greedy, "Greedy scheduling to maximize completed jobs.", isPremium = true),
)

private val dpTopics = listOf(
    topic("longest_common_subsequence", "Longest Common Subsequence", dp, "Tabulates the longest shared subsequence of two strings.", isPremium = true),
    topic("knapsack_01", "0/1 Knapsack", dp, "Tabulated take-or-leave item packing.", isPremium = true),
    topic("edit_distance", "Edit Distance", dp, "Minimum edits to turn one string into another.", isPremium = true),
    topic("matrix_chain_multiplication", "Matrix Chain Multiplication", dp, "Optimal parenthesization to minimize multiplication cost.", isPremium = true),
    topic("longest_increasing_subsequence", "Longest Increasing Subsequence", dp, "Longest strictly increasing run within a sequence.", isPremium = true),
    topic("coin_change", "Coin Change", dp, "Minimum coins (or ways) to make a target amount.", isPremium = true),
    topic("fibonacci_dp", "Fibonacci (Dynamic Programming)", dp, "Memoized/tabulated Fibonacci — linear instead of exponential.", isPremium = true),
    topic("rod_cutting", "Rod Cutting", dp, "Optimal way to cut a rod to maximize revenue.", isPremium = true),
    topic("bitmask_dp", "Bitmask DP", dp, "Uses a subset bitmask as the DP state — the classic TSP formulation.", isPremium = true),
    topic("tree_dp", "Tree DP", dp, "Combines children's answers into a parent's during a post-order pass.", isPremium = true),
)

private val graphAlgoTopics = listOf(
    topic("bfs", "Breadth-First Search (BFS)", graphAlgo, "Explores a graph level by level from a source."),
    topic("dfs", "Depth-First Search (DFS)", graphAlgo, "Explores a graph by going as deep as possible first."),
    topic("bellman_ford", "Bellman-Ford Algorithm", graphAlgo, "Shortest paths that tolerate negative edge weights.", isPremium = true),
    topic("floyd_warshall", "Floyd-Warshall Algorithm", graphAlgo, "All-pairs shortest paths via dynamic programming.", isPremium = true),
    topic("tarjans_algorithm", "Tarjan's Algorithm", graphAlgo, "Finds strongly connected components in one DFS pass.", isPremium = true),
    topic("kosarajus_algorithm", "Kosaraju's Algorithm", graphAlgo, "Finds strongly connected components via two DFS passes.", isPremium = true),
    topic("topological_sort", "Topological Sort", graphAlgo, "Orders a DAG so every edge points forward.", isPremium = true),
    topic("max_flow", "Max Flow (Ford-Fulkerson)", graphAlgo, "Pushes augmenting paths until the network saturates.", isPremium = true),
    topic("articulation_points", "Articulation Points & Bridges", graphAlgo, "Finds the vertices and edges whose removal disconnects a graph.", isPremium = true),
    topic("lca", "Lowest Common Ancestor", graphAlgo, "The deepest node that is an ancestor of two given nodes.", isPremium = true),
)

private val pathfindingTopics = listOf(
    topic("a_star_search", "A* Search", pathfinding, "Heuristic-guided shortest-path search.", isPremium = true),
    topic("d_star_algorithm", "D* Algorithm", pathfinding, "Incremental replanning search for changing environments.", isPremium = true),
)

private val miscTopics = listOf(
    topic("top_k_elements", "Top-K Elements", misc, "Finds the k largest/smallest elements efficiently.", isPremium = true),
    topic("sliding_window", "Sliding Window", misc, "Maintains a moving subrange to avoid recomputation.", isPremium = true),
    topic("two_pointer", "Two Pointer Technique", misc, "Scans with two indices moving toward or with each other.", isPremium = true),
    topic("prefix_sum", "Prefix Sum", misc, "Precomputes running totals for O(1) range-sum queries.", isPremium = true),
    topic("kadanes_algorithm", "Kadane's Algorithm", misc, "Linear-time maximum subarray sum.", isPremium = true),
    topic("reservoir_sampling", "Reservoir Sampling", misc, "Uniform random sampling from a stream of unknown length.", isPremium = true),
    topic("monte_carlo_method", "Monte Carlo Method", misc, "Randomized sampling to approximate a numeric answer.", isPremium = true),
    topic("mos_algorithm", "Mo's Algorithm", misc, "Offline query reordering for efficient range queries.", isPremium = true),
    topic("difference_array", "Difference Array", misc, "Applies range updates in O(1), resolved with a prefix sum.", isPremium = true),
    topic("kmp", "KMP String Matching", misc, "Reuses a prefix table so the text pointer never backs up.", isPremium = true),
    topic("rabin_karp", "Rabin-Karp", misc, "Rolling hash turns substring comparison into arithmetic.", isPremium = true),
    topic("manacher", "Manacher's Algorithm", misc, "Finds the longest palindromic substring in linear time.", isPremium = true),
)

object AlgorithmsTopics {
    val topics: List<Topic> =
        sortingTopics + searchingTopics + recursionTopics + divideConquerTopics + greedyTopics +
            dpTopics + graphAlgoTopics + pathfindingTopics + miscTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
