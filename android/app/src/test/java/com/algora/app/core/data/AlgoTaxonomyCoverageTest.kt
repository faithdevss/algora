package com.algora.app.core.data

import com.algora.app.feature.algorithms.AlgorithmsTopics
import com.algora.app.feature.datastructures.DataStructuresTopics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Makes `docs/topics.algo.md` executable.
 *
 * The phase-10 plan opened with a coverage table asserting which doc entries the app satisfies. A
 * table in a markdown file rots the moment a topic id is renamed or a category is reorganised, and
 * nothing notices. This is that table as code: every entry the doc lists, mapped to the topic id or
 * ids that satisfy it, with the mapping checked against the live registry.
 *
 * An entry may map to several topics (the doc's "Subset Sum / Partition Problem" is two topics here)
 * and a topic may satisfy several entries (Dijkstra is listed under both Greedy and Graph Algorithms;
 * Merge Sort under both Divide and Conquer and Sorting). Cross-section reuse is deliberate and
 * allowed — the doc's graph block leans on `disjoint_set` from Data Structures, and its String block
 * on `trie` and `suffix_tree`.
 *
 * Keys are the doc's own headings and entry names, verbatim, so a reader can diff this file against
 * the doc by eye.
 */
class AlgoTaxonomyCoverageTest {

    private val taxonomy: Map<String, Map<String, List<String>>> = linkedMapOf(
        "Recursion & Backtracking" to linkedMapOf(
            "Factorial" to listOf("factorial"),
            "Fibonacci" to listOf("fibonacci_recursive"),
            "N-Queens Problem" to listOf("n_queens"),
            "Sudoku Solver" to listOf("sudoku_solver"),
            "Subset Sum" to listOf("subset_sum"),
            "Permutation Generation" to listOf("permutation_generation"),
        ),
        "Divide and Conquer" to linkedMapOf(
            "Merge Sort" to listOf("merge_sort"),
            "Quick Sort" to listOf("quick_sort"),
            "Binary Search" to listOf("binary_search"),
            "Closest Pair of Points" to listOf("closest_pair_of_points"),
            "Strassen's Matrix Multiplication" to listOf("strassens_algorithm"),
            "Karatsuba's Algorithm" to listOf("karatsubas_algorithm"),
        ),
        "Greedy Algorithms" to linkedMapOf(
            "Fractional Knapsack" to listOf("fractional_knapsack"),
            "Huffman Coding" to listOf("huffman_coding"),
            "Kruskal's MST" to listOf("kruskals_mst"),
            "Prim's MST" to listOf("prims_mst"),
            "Dijkstra's Shortest Path" to listOf("dijkstras_algorithm"),
            "Job Sequencing with Deadlines" to listOf("job_sequencing"),
            "Activity Selection Problem" to listOf("activity_selection"),
            "Coin Change (Greedy)" to listOf("coin_change_greedy"),
        ),
        "Dynamic Programming (DP)" to linkedMapOf(
            "Fibonacci (Top-down & Bottom-up)" to listOf("fibonacci_dp"),
            "Longest Common Subsequence (LCS)" to listOf("longest_common_subsequence"),
            "Longest Increasing Subsequence (LIS)" to listOf("longest_increasing_subsequence"),
            "0/1 Knapsack" to listOf("knapsack_01"),
            "Matrix Chain Multiplication" to listOf("matrix_chain_multiplication"),
            "Coin Change (DP)" to listOf("coin_change"),
            "Edit Distance" to listOf("edit_distance"),
            // One doc entry naming two problems; the app ships both.
            "Subset Sum / Partition Problem" to listOf("subset_sum", "partition_problem"),
            "Rod Cutting" to listOf("rod_cutting"),
            "Floyd–Warshall Algorithm" to listOf("floyd_warshall"),
            "Bellman–Ford Algorithm" to listOf("bellman_ford"),
        ),
        "Graph Algorithms" to linkedMapOf(
            "BFS" to listOf("bfs"),
            "DFS" to listOf("dfs"),
            "Dijkstra's" to listOf("dijkstras_algorithm"),
            "Bellman–Ford" to listOf("bellman_ford"),
            "Floyd–Warshall" to listOf("floyd_warshall"),
            "A* Search" to listOf("a_star_search"),
            "Prim's" to listOf("prims_mst"),
            "Kruskal's" to listOf("kruskals_mst"),
            "Topological Sorting" to listOf("topological_sort"),
            "Union-Find/Disjoint Set" to listOf("disjoint_set"),
            "Tarjan's Algorithm (SCC, Bridges, Articulation Points)" to listOf("tarjans_algorithm", "articulation_points"),
            "Kosaraju's Algorithm (SCC)" to listOf("kosarajus_algorithm"),
            // No standalone topic: topological_sort's simulation *is* Kahn's in-degree sweep, and
            // its content names it against the DFS alternative. A second topic would duplicate a page.
            "Kahn's Algorithm (Topological Sort)" to listOf("topological_sort"),
            "Euler & Hamiltonian Paths" to listOf("eulerian_path", "hamiltonian_path"),
        ),
        "Sorting" to linkedMapOf(
            "Bubble" to listOf("bubble_sort"),
            "Selection" to listOf("selection_sort"),
            "Insertion" to listOf("insertion_sort"),
            "Merge" to listOf("merge_sort"),
            "Quick" to listOf("quick_sort"),
            "Heap" to listOf("heap_sort"),
            "Counting" to listOf("counting_sort"),
            "Radix" to listOf("radix_sort"),
            "Bucket Sort" to listOf("bucket_sort"),
        ),
        "Searching" to linkedMapOf(
            "Linear" to listOf("linear_search"),
            "Binary" to listOf("binary_search"),
            "Jump" to listOf("jump_search"),
            "Interpolation" to listOf("interpolation_search"),
            "Exponential Search" to listOf("exponential_search"),
        ),
        "Math & Number Theory" to linkedMapOf(
            "Euclid's GCD/LCM" to listOf("euclid_gcd"),
            "Modular Arithmetic" to listOf("modular_arithmetic"),
            "Modular Exponentiation" to listOf("modular_exponentiation"),
            "Sieve of Eratosthenes" to listOf("sieve_of_eratosthenes"),
            "Fermat's Little Theorem" to listOf("fermats_little_theorem"),
            "Chinese Remainder Theorem" to listOf("chinese_remainder_theorem"),
            "Fast Power/Binary Exponentiation" to listOf("fast_power"),
        ),
        "String Algorithms" to linkedMapOf(
            "Naive Search" to listOf("naive_string_search"),
            "KMP" to listOf("kmp"),
            "Rabin–Karp" to listOf("rabin_karp"),
            "Z Algorithm" to listOf("z_algorithm"),
            "Longest Common Substring" to listOf("longest_common_substring"),
            "Longest Palindromic Substring" to listOf("longest_palindromic_substring"),
            "Manacher's Algorithm" to listOf("manacher"),
            // Satisfied from Data Structures — the trie is a structure topic that the doc lists
            // here as a string algorithm. Cross-section reuse, not a gap.
            "Trie Construction & Search" to listOf("trie"),
            "Aho–Corasick Algorithm" to listOf("aho_corasick"),
            "Suffix Array & Suffix Tree" to listOf("suffix_tree"),
        ),
        "Bit Manipulation" to linkedMapOf(
            "Check Odd/Even & Power of 2" to listOf("bit_basics"),
            "Count Set Bits (Brian Kernighan's)" to listOf("count_set_bits"),
            "Subsets using Bitmask" to listOf("subsets_bitmask"),
            "XOR-based problems" to listOf("xor_tricks"),
        ),
        "Computational Geometry" to linkedMapOf(
            "Convex Hull (Graham's Scan, Jarvis March)" to listOf("convex_hull"),
            "Line Intersection" to listOf("line_intersection"),
            // Listed under Divide and Conquer too; the topic lives there and cross-links here.
            "Closest Pair of Points" to listOf("closest_pair_of_points"),
            "Rotating Calipers" to listOf("rotating_calipers"),
            "Area/Perimeter Calculation" to listOf("polygon_area"),
        ),
        "Graph Search & Pathfinding (AI Pathfinding)" to linkedMapOf(
            "A* Search" to listOf("a_star_search"),
            "IDA* (Iterative Deepening A*)" to listOf("ida_star"),
            "Uniform Cost Search" to listOf("uniform_cost_search"),
            "D* Algorithm" to listOf("d_star_algorithm"),
        ),
    )

    // Counted off docs/topics.algo.md bullet by bullet. The phase-10 plan claimed 92; the doc
    // actually lists 89 distinct entries (it over-counted Graph Algorithms and String Algorithms).
    private val expectedEntryCount = 89

    private val dsaTopicIds: Set<String> =
        (DataStructuresTopics.topics + AlgorithmsTopics.topics).map { it.id }.toSet()

    private val allEntries: List<Pair<String, List<String>>>
        get() = taxonomy.flatMap { (heading, entries) ->
            entries.map { (entry, ids) -> "$heading :: $entry" to ids }
        }

    @Test
    fun `the map covers every entry the doc lists`() {
        assertEquals(
            "docs/topics.algo.md entry count changed — update the map, not this number",
            expectedEntryCount,
            allEntries.size,
        )
    }

    @Test
    fun `every doc entry maps to at least one topic`() {
        val unmapped = allEntries.filter { (_, ids) -> ids.isEmpty() }.map { it.first }
        assertTrue("Doc entries with no topic behind them: $unmapped", unmapped.isEmpty())
    }

    @Test
    fun `every mapped topic id resolves in the registry`() {
        val broken = allEntries.flatMap { (entry, ids) ->
            ids.filter { TopicRegistry.find(it) == null }.map { "$entry -> $it" }
        }
        assertTrue("Taxonomy entries pointing at topics that do not exist: $broken", broken.isEmpty())
    }

    @Test
    fun `every mapped topic is a DSA topic`() {
        // A DSA doc entry satisfied by an AI topic would mean the map drifted, not that coverage
        // improved — the id would resolve but the topic would be in the wrong mode's browser.
        val misplaced = allEntries.flatMap { (entry, ids) ->
            ids.filter { it !in dsaTopicIds }.map { "$entry -> $it" }
        }
        assertTrue("Taxonomy entries satisfied by non-DSA topics: $misplaced", misplaced.isEmpty())
    }

    @Test
    fun `the app is a superset of the doc`() {
        // The app ships topics the doc never lists (quickselect, tower_of_hanoi, max_flow, lca and
        // the whole algo_misc group). That is fine and deliberate; this pins the direction of the
        // gap so a future edit cannot quietly delete app topics to make a coverage number look good.
        val mapped = allEntries.flatMap { it.second }.toSet()
        val appOnly = AlgorithmsTopics.topics.map { it.id }.filter { it !in mapped }
        assertTrue(
            "Expected the app to carry topics beyond the doc, found none — did topics get deleted?",
            appOnly.isNotEmpty(),
        )
    }
}
