package com.algora.app.feature.topics

import com.algora.app.feature.algorithms.AlgorithmsTopics
import com.algora.app.feature.datastructures.DataStructuresTopics
import com.algora.app.feature.topics.content.TopicContentProvider
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 11 coverage: which Data Structures and Algorithms topics carry a figure yet.
 *
 * The list below is the batch tracker — it starts at all 122 topics and shrinks by one batch per
 * commit. `no pending entry already has a figure` is the half that matters: it fails the moment a
 * figure is authored and its id is left behind here, so the list cannot quietly drift out of date.
 * Shape conformance for those figures lives in [FigureShapeTest].
 */
class DsaFigureCoverageTest {

    private val dsaTopics = DataStructuresTopics.topics + AlgorithmsTopics.topics

    // Batch F1 (Data Structures — linear + ADTs) is done; F2..F7 remain.
    private val pendingFigures = setOf(
        "a_star_search",
        "activity_selection",
        "aho_corasick",
        "articulation_points",
        "avl_red_black_tree",
        "b_tree",
        "bellman_ford",
        "bfs",
        "binary_search",
        "binary_search_tree",
        "bit_basics",
        "bitmask_dp",
        "bloom_filter",
        "bubble_sort",
        "bucket_sort",
        "chinese_remainder_theorem",
        "closest_pair_of_points",
        "coin_change",
        "coin_change_greedy",
        "convex_hull",
        "count_set_bits",
        "counting_sort",
        "d_star_algorithm",
        "dfs",
        "difference_array",
        "dijkstras_algorithm",
        "disjoint_set",
        "edit_distance",
        "euclid_gcd",
        "eulerian_path",
        "exponential_search",
        "factorial",
        "fast_power",
        "fenwick_tree",
        "fermats_little_theorem",
        "fibonacci_dp",
        "fibonacci_recursive",
        "floyd_warshall",
        "fractional_knapsack",
        "graph",
        "graph_variants",
        "hamiltonian_path",
        "heap",
        "heap_sort",
        "huffman_coding",
        "ida_star",
        "insertion_sort",
        "interpolation_search",
        "job_sequencing",
        "jump_search",
        "kadanes_algorithm",
        "karatsubas_algorithm",
        "kd_tree",
        "kmp",
        "knapsack_01",
        "kosarajus_algorithm",
        "kruskals_mst",
        "lca",
        "line_intersection",
        "linear_search",
        "longest_common_subsequence",
        "longest_common_substring",
        "longest_increasing_subsequence",
        "longest_palindromic_substring",
        "lru_cache",
        "manacher",
        "matrix_chain_multiplication",
        "max_flow",
        "median_of_medians",
        "merge_sort",
        "modular_arithmetic",
        "modular_exponentiation",
        "monte_carlo_method",
        "mos_algorithm",
        "n_queens",
        "naive_string_search",
        "partition_problem",
        "permutation_generation",
        "polygon_area",
        "prefix_sum",
        "prims_mst",
        "quick_sort",
        "quickselect",
        "rabin_karp",
        "radix_sort",
        "reservoir_sampling",
        "rod_cutting",
        "rotating_calipers",
        "segment_tree",
        "selection_sort",
        "sieve_of_eratosthenes",
        "skip_list",
        "sliding_window",
        "sparse_table",
        "strassens_algorithm",
        "subset_sum",
        "subsets_bitmask",
        "sudoku_solver",
        "suffix_tree",
        "tarjans_algorithm",
        "top_k_elements",
        "topological_sort",
        "tower_of_hanoi",
        "tree",
        "tree_dp",
        "trie",
        "two_pointer",
        "uniform_cost_search",
        "xor_tricks",
        "z_algorithm",
    )

    @Test
    fun `every finished DSA topic has a figure`() {
        val missing = dsaTopics.map { it.id }
            .filterNot { it in pendingFigures }
            .filter { TopicContentProvider.get(it)?.figure == null }
        assertTrue("DSA topics expected to have a figure but do not: $missing", missing.isEmpty())
    }

    @Test
    fun `no pending entry already has a figure`() {
        val stale = pendingFigures.filter { TopicContentProvider.get(it)?.figure != null }
        assertTrue("Figured but still listed as pending: $stale", stale.isEmpty())
    }

    @Test
    fun `every pending entry is a real DSA topic`() {
        val ids = dsaTopics.map { it.id }.toSet()
        val unknown = pendingFigures.filterNot { it in ids }
        assertTrue("Pending ids that are not DSA topics: $unknown", unknown.isEmpty())
    }
}
