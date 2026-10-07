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
private val numberTheory = AlgorithmsCategories.math
private val strings = AlgorithmsCategories.strings
private val bits = AlgorithmsCategories.bits
private val geometry = AlgorithmsCategories.geometry
private val misc = AlgorithmsCategories.miscAdvanced

private val sortingTopics = listOf(
    topic("bubble_sort", "Bubble Sort", sorting, "Repeatedly swaps adjacent out-of-order elements."),
    topic("selection_sort", "Selection Sort", sorting, "Repeatedly selects the minimum remaining element.", isPremium = true),
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
    topic("closest_pair_of_points", "Closest Pair of Points", divideConquer, "Finds the nearest pair in a plane faster than brute force.", isPremium = true),
    topic("strassens_algorithm", "Strassen's Algorithm", divideConquer, "Faster-than-cubic matrix multiplication.", isPremium = true),
    topic("karatsubas_algorithm", "Karatsuba's Algorithm", divideConquer, "Sub-quadratic multiplication of large integers.", isPremium = true),
    topic("quickselect", "Quickselect", divideConquer, "Finds the k-th smallest element in linear expected time.", isPremium = true),
    topic("median_of_medians", "Median of Medians", divideConquer, "Guarantees linear worst-case selection.", isPremium = true),
    topic("tower_of_hanoi", "Tower of Hanoi", divideConquer, "Classic recursive disk-moving puzzle."),
)

private val greedyTopics = listOf(
    topic("fractional_knapsack", "Fractional Knapsack", greedy, "Greedy value-per-weight packing."),
    topic("huffman_coding", "Huffman Coding", greedy, "Builds an optimal prefix-free encoding.", isPremium = true),
    topic("kruskals_mst", "Kruskal's MST", greedy, "Builds a minimum spanning tree by adding cheapest edges.", isPremium = true),
    topic("prims_mst", "Prim's MST", greedy, "Grows a minimum spanning tree from a starting node.", isPremium = true),
    topic("dijkstras_algorithm", "Dijkstra's Algorithm", greedy, "Greedy shortest paths from a single source.", isPremium = true),
    topic("job_sequencing", "Job Sequencing with Deadlines", greedy, "Greedy scheduling to maximize total profit.", isPremium = true),
    topic("activity_selection", "Activity Selection", greedy, "Earliest-finish-first scheduling of non-overlapping intervals.", isPremium = true),
    topic("coin_change_greedy", "Coin Change (Greedy)", greedy, "Largest coin first — and the coin systems where that is wrong.", isPremium = true),
)

private val dpTopics = listOf(
    topic("longest_common_subsequence", "Longest Common Subsequence", dp, "Tabulates the longest shared subsequence of two strings.", isPremium = true),
    topic("knapsack_01", "0/1 Knapsack", dp, "Tabulated take-or-leave item packing.", isPremium = true),
    topic("edit_distance", "Edit Distance", dp, "Minimum edits to turn one string into another.", isPremium = true),
    topic("matrix_chain_multiplication", "Matrix Chain Multiplication", dp, "Optimal parenthesization to minimize multiplication cost.", isPremium = true),
    topic("longest_increasing_subsequence", "Longest Increasing Subsequence", dp, "Longest strictly increasing subsequence (not necessarily contiguous).", isPremium = true),
    topic("coin_change", "Coin Change", dp, "Minimum coins (or ways) to make a target amount.", isPremium = true),
    topic("fibonacci_dp", "Fibonacci (Dynamic Programming)", dp, "Memoized/tabulated Fibonacci — linear instead of exponential.", isPremium = true),
    topic("rod_cutting", "Rod Cutting", dp, "Optimal way to cut a rod to maximize revenue.", isPremium = true),
    topic("bitmask_dp", "Bitmask DP", dp, "Uses a subset bitmask as the DP state — the classic TSP formulation.", isPremium = true),
    topic("tree_dp", "Tree DP", dp, "Combines children's answers into a parent's during a post-order pass.", isPremium = true),
    topic("partition_problem", "Partition Problem", dp, "Splits a set into two halves of equal sum — subset-sum on half the total.", isPremium = true),
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
    topic("eulerian_path", "Eulerian Path & Circuit", graphAlgo, "Uses every edge exactly once — decided by degree parity alone.", isPremium = true),
    topic("hamiltonian_path", "Hamiltonian Path & Circuit", graphAlgo, "Visits every vertex exactly once — no cheap test, only search.", isPremium = true),
)

private val pathfindingTopics = listOf(
    topic("a_star_search", "A* Search", pathfinding, "Heuristic-guided shortest-path search.", isPremium = true),
    topic("d_star_algorithm", "D* Algorithm", pathfinding, "Incremental replanning search for changing environments.", isPremium = true),
    topic("uniform_cost_search", "Uniform Cost Search", pathfinding, "Dijkstra as a goal-directed search over a generated state space.", isPremium = true),
    topic("ida_star", "IDA*", pathfinding, "A*'s answer on a stack instead of a frontier — linear memory.", isPremium = true),
)

// The doc lists "Fast Power / Binary Exponentiation" and "Modular Exponentiation" as separate
// entries and they stay separate here: fast_power is the halving idea and its matrix-power use,
// modular_exponentiation is that idea under a modulus plus the inverse it enables.
private val mathTopics = listOf(
    topic("euclid_gcd", "Euclid's GCD & LCM", numberTheory, "Repeated remainder — the oldest algorithm still in daily use."),
    topic("modular_arithmetic", "Modular Arithmetic", numberTheory, "Arithmetic that wraps, and the one operation that does not survive it.", isPremium = true),
    topic("fast_power", "Fast Power", numberTheory, "Halve the exponent, square the base — O(log n) multiplications.", isPremium = true),
    topic("modular_exponentiation", "Modular Exponentiation", numberTheory, "Fast power under a modulus, and the modular inverse it buys.", isPremium = true),
    topic("sieve_of_eratosthenes", "Sieve of Eratosthenes", numberTheory, "Marks composites in passes to list every prime below n.", isPremium = true),
    topic("fermats_little_theorem", "Fermat's Little Theorem", numberTheory, "aᵖ⁻¹ ≡ 1 mod p — inverses, primality tests, and the numbers that fool them.", isPremium = true),
    topic("chinese_remainder_theorem", "Chinese Remainder Theorem", numberTheory, "Reassembles one number from its remainders under coprime moduli.", isPremium = true),
)

// kmp, rabin_karp and manacher moved here from algo_misc. Their ids are unchanged, so no content
// file, cross-link, prerequisite or problem-bank reference had to move with them — only categoryId.
private val stringTopics = listOf(
    topic("naive_string_search", "Naive Pattern Search", strings, "Shift by one and compare — the baseline everything else beats."),
    topic("kmp", "KMP String Matching", strings, "Reuses a prefix table so the text pointer never backs up.", isPremium = true),
    topic("rabin_karp", "Rabin-Karp", strings, "Rolling hash turns substring comparison into arithmetic.", isPremium = true),
    topic("z_algorithm", "Z Algorithm", strings, "One array of prefix-match lengths, computed in a single linear pass.", isPremium = true),
    topic("longest_common_substring", "Longest Common Substring", strings, "The longest contiguous run two strings share.", isPremium = true),
    topic("longest_palindromic_substring", "Longest Palindromic Substring", strings, "Expand around every centre — the O(n²) answer Manacher improves on.", isPremium = true),
    topic("manacher", "Manacher's Algorithm", strings, "Finds the longest palindromic substring in linear time.", isPremium = true),
    topic("aho_corasick", "Aho-Corasick", strings, "Matches every pattern in a dictionary in one pass over the text.", isPremium = true),
)

private val bitTopics = listOf(
    topic("bit_basics", "Bit Basics", bits, "Parity, powers of two, and the n & (n−1) trick."),
    topic("count_set_bits", "Count Set Bits", bits, "Brian Kernighan's loop runs once per set bit, not once per position.", isPremium = true),
    topic("subsets_bitmask", "Subsets using Bitmask", bits, "Counting to 2ⁿ enumerates every subset exactly once.", isPremium = true),
    topic("xor_tricks", "XOR Tricks", bits, "Its own inverse — single number, swap without a temp, and prefix ranges.", isPremium = true),
)

// closest_pair_of_points stays in Divide and Conquer — the doc lists it under both headings — and
// cross-links here instead of moving.
private val geometryTopics = listOf(
    topic("polygon_area", "Area & Perimeter", geometry, "Shoelace formula: a polygon's area from its vertices alone."),
    topic("convex_hull", "Convex Hull", geometry, "The tightest enclosing polygon — Graham scan and Jarvis march.", isPremium = true),
    topic("line_intersection", "Line Segment Intersection", geometry, "Four orientation tests, plus the collinear cases they miss.", isPremium = true),
    topic("rotating_calipers", "Rotating Calipers", geometry, "Sweeps antipodal pairs around a hull for the diameter in linear time.", isPremium = true),
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
)

object AlgorithmsTopics {
    val topics: List<Topic> =
        sortingTopics + searchingTopics + recursionTopics + divideConquerTopics + greedyTopics +
            dpTopics + graphAlgoTopics + pathfindingTopics + mathTopics + stringTopics +
            bitTopics + geometryTopics + miscTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
