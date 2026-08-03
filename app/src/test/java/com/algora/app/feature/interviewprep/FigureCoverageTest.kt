package com.algora.app.feature.interviewprep

import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureShape
import com.algora.app.feature.topics.content.TopicContentProvider
import org.junit.Assert.assertTrue
import org.junit.Test

// Figures are static, so nothing about them is checked at runtime the way a simulation's frames are:
// a band that runs past the end of its strip, or a span outside its axis, is simply drawn wrong (or
// not at all) and looks plausible on the page. These are the same shape-conformance guards
// SimulationFrameTest applies to the labs.
class FigureCoverageTest {

    private val patternTopics = InterviewPrepTopics.topics.filter {
        it.categoryId == InterviewPrepCategories.patterns.id
    }

    private val figures: List<Pair<String, Figure>> = patternTopics.mapNotNull { topic ->
        TopicContentProvider.get(topic.id)?.figure?.let { topic.id to it }
    }

    // Guides still waiting for a figure, authored batch by batch alongside the primitives they need.
    // The list only shrinks — `no pending entry already has a figure` fails if one is left behind.
    private val pendingFigures = setOf(
        "backtracking_pattern",
        "two_heaps_pattern",
        "binary_lifting_pattern",
        "bit_manipulation_pattern",
        "bit_trie_pattern",
        "bitmask_state_pattern",
        "bst_inorder_pattern",
        "composite_design_pattern",
        "dag_dp_pattern",
        "divide_conquer_pattern",
        "expression_stack_pattern",
        "game_theory_dp_pattern",
        "graph_coloring_pattern",
        "greedy_exchange_pattern",
        "grid_dp_pattern",
        "hash_counting_pattern",
        "interval_dp_pattern",
        "knapsack_dp_pattern",
        "matrix_islands_pattern",
        "matrix_transform_pattern",
        "meet_in_middle_pattern",
        "memo_recursion_pattern",
        "monotonic_stack_pattern",
        "multi_source_bfs_pattern",
        "prefix_2d_pattern",
        "shortest_path_pattern",
        "state_machine_dp_pattern",
        "subsets_pattern",
        "topological_sort_pattern",
        "tree_bfs_pattern",
        "tree_dfs_pattern",
        "tree_dp_pattern",
        "trie_prefix_pattern",
        "union_find_pattern",
    )

    @Test
    fun `every pattern guide has a figure`() {
        val missing = patternTopics.map { it.id }
            .filterNot { it in pendingFigures }
            .filter { TopicContentProvider.get(it)?.figure == null }
        assertTrue("Pattern guides with no figure: $missing", missing.isEmpty())
    }

    @Test
    fun `no pending entry already has a figure`() {
        val stale = pendingFigures.filter { TopicContentProvider.get(it)?.figure != null }
        assertTrue("Figured but still listed as pending: $stale", stale.isEmpty())
    }

    @Test
    fun `every pending entry is a real pattern topic`() {
        val ids = patternTopics.map { it.id }.toSet()
        assertTrue(
            "Pending ids that are not pattern topics: ${pendingFigures.filterNot { it in ids }}",
            pendingFigures.all { it in ids },
        )
    }

    @Test
    fun `every caption says something`() {
        val blank = figures.filter { (_, figure) -> figure.caption.isBlank() }.map { it.first }
        assertTrue("Figures with a blank caption: $blank", blank.isEmpty())
    }

    @Test
    fun `every strip figure marks cells that exist`() {
        val problems = figures.mapNotNull { (id, figure) ->
            val strip = figure.shape as? FigureShape.Strip ?: return@mapNotNull null
            val faults = buildList {
                if (strip.cells.isEmpty()) add("no cells")
                if (strip.aux.size > strip.cells.size) {
                    add("${strip.aux.size} aux cells under ${strip.cells.size} cells")
                }
                strip.bands.forEach { band ->
                    if (band.from > band.to) add("band \"${band.label}\" runs backwards")
                    if (band.from !in strip.cells.indices || band.to !in strip.cells.indices) {
                        add("band \"${band.label}\" spans ${band.from}..${band.to} of ${strip.cells.size} cells")
                    }
                }
                strip.pointers.forEach { pointer ->
                    if (pointer.index !in strip.cells.indices) {
                        add("pointer \"${pointer.label}\" at ${pointer.index} of ${strip.cells.size} cells")
                    }
                }
            }
            if (faults.isEmpty()) null else "$id: ${faults.joinToString("; ")}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // Bands are drawn by walking the strip left to right and asking which band covers each cell, so
    // two bands over the same cell would silently drop one of them.
    @Test
    fun `strip bands do not overlap`() {
        val problems = figures.mapNotNull { (id, figure) ->
            val strip = figure.shape as? FigureShape.Strip ?: return@mapNotNull null
            val seen = mutableSetOf<Int>()
            val clashes = strip.bands.filter { band ->
                val cells = (band.from..band.to).toSet()
                val clash = cells.any { it in seen }
                seen += cells
                clash
            }
            if (clashes.isEmpty()) null else "$id: overlapping bands ${clashes.map { it.label }}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `every timeline span fits inside its axis`() {
        val problems = figures.mapNotNull { (id, figure) ->
            val timeline = figure.shape as? FigureShape.Timeline ?: return@mapNotNull null
            val faults = buildList {
                if (timeline.spans.isEmpty()) add("no spans")
                if (timeline.axisMax <= 0) add("axisMax ${timeline.axisMax}")
                timeline.spans.forEach { span ->
                    if (span.start >= span.end) add("span \"${span.label}\" is empty or backwards")
                    if (span.start < 0 || span.end > timeline.axisMax) {
                        add("span \"${span.label}\" (${span.start}..${span.end}) escapes axis 0..${timeline.axisMax}")
                    }
                }
                timeline.marker?.let {
                    if (it < 0 || it > timeline.axisMax) add("marker at $it is off the axis")
                }
            }
            if (faults.isEmpty()) null else "$id: ${faults.joinToString("; ")}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
