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

    // Everything except F7 (strings, math & number theory, bits, geometry) is done: F1-F2 covered
    // all 29 Data Structures, F3 sorting and searching, F4 recursion and divide & conquer,
    // F5 graph algorithms and pathfinding, F6 dynamic programming and greedy.
    private val pendingFigures = setOf(
        "aho_corasick",
        "bit_basics",
        "chinese_remainder_theorem",
        "convex_hull",
        "count_set_bits",
        "difference_array",
        "euclid_gcd",
        "fast_power",
        "fermats_little_theorem",
        "kadanes_algorithm",
        "kmp",
        "line_intersection",
        "longest_common_substring",
        "longest_palindromic_substring",
        "manacher",
        "modular_arithmetic",
        "modular_exponentiation",
        "monte_carlo_method",
        "mos_algorithm",
        "naive_string_search",
        "polygon_area",
        "prefix_sum",
        "rabin_karp",
        "reservoir_sampling",
        "rotating_calipers",
        "sieve_of_eratosthenes",
        "sliding_window",
        "subsets_bitmask",
        "top_k_elements",
        "two_pointer",
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
