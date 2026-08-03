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

    // F1-F2 (all 29 Data Structures), F3 (sorting + searching) and F4 (recursion, backtracking,
    // divide & conquer) are done. F5..F7 remain.
    private val pendingFigures = setOf(
        "a_star_search",
        "activity_selection",
        "aho_corasick",
        "articulation_points",
        "bellman_ford",
        "bfs",
        "bit_basics",
        "bitmask_dp",
        "chinese_remainder_theorem",
        "coin_change",
        "coin_change_greedy",
        "convex_hull",
        "count_set_bits",
        "d_star_algorithm",
        "dfs",
        "difference_array",
        "dijkstras_algorithm",
        "edit_distance",
        "euclid_gcd",
        "eulerian_path",
        "fast_power",
        "fermats_little_theorem",
        "fibonacci_dp",
        "floyd_warshall",
        "fractional_knapsack",
        "hamiltonian_path",
        "huffman_coding",
        "ida_star",
        "job_sequencing",
        "kadanes_algorithm",
        "kmp",
        "knapsack_01",
        "kosarajus_algorithm",
        "kruskals_mst",
        "lca",
        "line_intersection",
        "longest_common_subsequence",
        "longest_common_substring",
        "longest_increasing_subsequence",
        "longest_palindromic_substring",
        "manacher",
        "matrix_chain_multiplication",
        "max_flow",
        "modular_arithmetic",
        "modular_exponentiation",
        "monte_carlo_method",
        "mos_algorithm",
        "naive_string_search",
        "partition_problem",
        "polygon_area",
        "prefix_sum",
        "prims_mst",
        "rabin_karp",
        "reservoir_sampling",
        "rod_cutting",
        "rotating_calipers",
        "sieve_of_eratosthenes",
        "sliding_window",
        "subsets_bitmask",
        "tarjans_algorithm",
        "top_k_elements",
        "topological_sort",
        "tree_dp",
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
