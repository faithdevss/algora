package com.algora.app.feature.topics

import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureShape
import com.algora.app.feature.algorithms.AlgorithmsTopics
import com.algora.app.feature.datastructures.DataStructuresTopics
import com.algora.app.feature.deeplearning.DeepLearningTopics
import com.algora.app.feature.interviewprep.InterviewPrepTopics
import com.algora.app.feature.machinelearning.MachineLearningTopics
import com.algora.app.feature.nlp.NlpTopics
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningTopics
import com.algora.app.feature.topics.content.TopicContentProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Shape conformance for **every** figure in the app, wherever its topic lives.
 *
 * Figures are static, so nothing about them is checked at runtime the way a simulation's frames are:
 * a band that runs past the end of its strip, or a span outside its axis, is simply drawn wrong — or
 * not at all — and looks plausible on the page. These checks started life inside
 * `FigureCoverageTest` when only the interview-prep pattern guides had figures; they moved here when
 * the layer began covering Data Structures and Algorithms, so a figure is held to the same rules no
 * matter which section authored it. `FigureCoverageTest` keeps the question of *which* topics are
 * expected to have one.
 */
class FigureShapeTest {

    // Same composition ContentCoverageTest uses — TopicRegistry keeps its own list private.
    private val everyTopic = DataStructuresTopics.topics + AlgorithmsTopics.topics +
        InterviewPrepTopics.topics + MachineLearningTopics.topics + DeepLearningTopics.topics +
        NlpTopics.topics + ReinforcementLearningTopics.topics

    private val figures: List<Pair<String, Figure>> = everyTopic
        .mapNotNull { topic -> TopicContentProvider.get(topic.id)?.figure?.let { topic.id to it } }

    @Test
    fun `there are figures to check`() {
        // A refactor that stopped resolving content would make every test below vacuously pass.
        assertTrue("No topic carries a figure — the enumeration is broken", figures.isNotEmpty())
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
                if (strip.aux.isNotEmpty() && strip.auxLabel.isNullOrBlank()) {
                    add("an aux row with no label — nothing says what the second row is")
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

    // A band's label is laid out over exactly the cells the band covers, so a long one on a narrow
    // band wraps into a four-line column above the strip. Roughly eleven characters fit per cell.
    @Test
    fun `band and arrow labels fit the space they are drawn in`() {
        val problems = figures.flatMap { (id, figure) ->
            when (val shape = figure.shape) {
                is FigureShape.Strip -> shape.bands.mapNotNull { band ->
                    val span = band.to - band.from + 1
                    val budget = 11 * span
                    if (band.label.length <= budget) null
                    else "$id: band \"${band.label}\" is ${band.label.length} chars over $span cell(s), budget $budget"
                } + shape.pointers.mapNotNull { pointer ->
                    // A pointer hangs under exactly one cell, so it gets one cell's worth of room.
                    if (pointer.label.length <= 11) null
                    else "$id: pointer \"${pointer.label}\" is ${pointer.label.length} chars under one cell"
                }
                is FigureShape.Grid -> shape.arrows.mapNotNull { arrow ->
                    val label = arrow.label ?: return@mapNotNull null
                    when {
                        label.isBlank() -> "$id: an arrow has a blank label"
                        // An arrow one cell long has no room beside it for a name.
                        maxOf(
                            abs(arrow.toRow - arrow.fromRow),
                            abs(arrow.toCol - arrow.fromCol),
                        ) < 2 -> "$id: labelled arrow \"$label\" spans one cell — the label lands on the value"
                        else -> null
                    }
                }
                else -> emptyList()
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `every grid figure is rectangular and addresses real cells`() {
        val problems = figures.mapNotNull { (id, figure) ->
            val grid = figure.shape as? FigureShape.Grid ?: return@mapNotNull null
            val rows = grid.rows.size
            val cols = grid.rows.firstOrNull()?.size ?: 0
            val faults = buildList {
                if (rows == 0 || cols == 0) add("empty grid")
                if (grid.rows.any { it.size != cols }) add("ragged rows: ${grid.rows.map { it.size }}")
                if (grid.rowHeaders.isNotEmpty() && grid.rowHeaders.size != rows) {
                    add("${grid.rowHeaders.size} row headers for $rows rows")
                }
                if (grid.colHeaders.isNotEmpty() && grid.colHeaders.size != cols) {
                    add("${grid.colHeaders.size} column headers for $cols columns")
                }
                grid.marks.forEach {
                    if (it.row !in 0 until rows || it.col !in 0 until cols) {
                        add("mark at (${it.row}, ${it.col}) outside ${rows}×$cols")
                    }
                }
                grid.arrows.forEach {
                    if (it.fromRow !in 0 until rows || it.fromCol !in 0 until cols ||
                        it.toRow !in 0 until rows || it.toCol !in 0 until cols
                    ) {
                        add("arrow (${it.fromRow}, ${it.fromCol}) → (${it.toRow}, ${it.toCol}) outside ${rows}×$cols")
                    }
                    if (it.fromRow == it.toRow && it.fromCol == it.toCol) add("arrow to itself")
                }
            }
            if (faults.isEmpty()) null else "$id: ${faults.joinToString("; ")}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // The layout walks the node list in reverse to centre parents over children, which only works
    // because a parent always sits at a smaller index — a forward reference silently loses a node.
    @Test
    fun `every tree figure is a real tree in parents-first order`() {
        val problems = figures.mapNotNull { (id, figure) ->
            val tree = figure.shape as? FigureShape.Tree ?: return@mapNotNull null
            val faults = buildList {
                if (tree.nodes.isEmpty()) add("no nodes")
                val roots = tree.nodes.count { it.parent == null }
                if (roots != 1) add("$roots roots")
                tree.nodes.forEachIndexed { index, node ->
                    val parent = node.parent ?: return@forEachIndexed
                    if (parent !in tree.nodes.indices) add("node $index has parent $parent, which does not exist")
                    else if (parent >= index) add("node $index points forward to parent $parent")
                }
                if (tree.nodes.any { it.label.isBlank() }) add("a node has no label")
            }
            if (faults.isEmpty()) null else "$id: ${faults.joinToString("; ")}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    // Graph positions are hand-placed rather than derived, so a coordinate outside 0..1 puts a node
    // half off the card — which looks like a layout bug rather than a spec one.
    @Test
    fun `every graph figure places its nodes on the card`() {
        val problems = figures.mapNotNull { (id, figure) ->
            val graph = figure.shape as? FigureShape.Graph ?: return@mapNotNull null
            val faults = buildList {
                if (graph.nodes.isEmpty()) add("no nodes")
                graph.nodes.forEach { node ->
                    if (node.x !in 0f..1f || node.y !in 0f..1f) {
                        add("node \"${node.label}\" at (${node.x}, ${node.y}) is off the card")
                    }
                    if (node.label.isBlank()) add("a node has no label")
                }
                graph.edges.forEach { edge ->
                    if (edge.from !in graph.nodes.indices || edge.to !in graph.nodes.indices) {
                        add("edge ${edge.from} → ${edge.to} of ${graph.nodes.size} nodes")
                    }
                    if (edge.from == edge.to) add("self-loop on node ${edge.from}")
                }
            }
            if (faults.isEmpty()) null else "$id: ${faults.joinToString("; ")}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `every stack figure has labelled, non-empty columns`() {
        val problems = figures.mapNotNull { (id, figure) ->
            val stacks = figure.shape as? FigureShape.Stacks ?: return@mapNotNull null
            val faults = buildList {
                if (stacks.columns.isEmpty()) add("no columns")
                if (stacks.columns.size > 2) add("${stacks.columns.size} columns — the card fits two")
                stacks.columns.forEach { column ->
                    if (column.entries.isEmpty()) add("column \"${column.label}\" is empty")
                    if (column.label.isBlank()) add("a column has no label")
                }
            }
            if (faults.isEmpty()) null else "$id: ${faults.joinToString("; ")}"
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

    // A plot is the one shape whose values are read against a scale rather than compared to their
    // neighbours, so a point outside 0..1 is not merely clipped — it silently rescales nothing and
    // draws off the canvas while every other point still looks right.
    @Test
    fun `every plot stays inside its axes`() {
        val problems = figures.mapNotNull { (id, figure) ->
            val plot = figure.shape as? FigureShape.Plot ?: return@mapNotNull null
            val faults = buildList {
                // Curves and bars answer different questions; one axis cannot carry both legibly.
                if (plot.series.isEmpty() && plot.bars.isEmpty()) add("neither series nor bars")
                if (plot.series.isNotEmpty() && plot.bars.isNotEmpty()) {
                    add("${plot.series.size} series and ${plot.bars.size} bars on one axis")
                }
                plot.series.forEach { series ->
                    if (series.label.isBlank()) add("a series has no label")
                    if (series.points.size < 2) {
                        add("series \"${series.label}\" has ${series.points.size} point(s) — nothing to draw")
                    }
                    series.points.forEach { point ->
                        if (point.x !in 0f..1f || point.y !in 0f..1f) {
                            add("series \"${series.label}\" leaves the axes at (${point.x}, ${point.y})")
                        }
                    }
                }
                plot.bars.forEach { bar ->
                    if (bar.label.isBlank()) add("a bar has no label")
                    if (bar.value !in 0f..1f) add("bar \"${bar.label}\" is ${bar.value} of the axis")
                }
                plot.markers.forEach { marker ->
                    if (marker.x !in 0f..1f || marker.y !in 0f..1f) {
                        add("marker at (${marker.x}, ${marker.y}) is off the axes")
                    }
                    // An unlabelled marker is a dot with nothing to say — the callout is the point.
                    if (marker.label.isNullOrBlank()) add("a marker has no label")
                }
            }
            if (faults.isEmpty()) null else "$id: ${faults.joinToString("; ")}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `every layer stack fits the card`() {
        val problems = figures.mapNotNull { (id, figure) ->
            val stack = figure.shape as? FigureShape.LayerStack ?: return@mapNotNull null
            val faults = buildList {
                // One block is a box, not an architecture — the flow between them is the figure.
                if (stack.layers.size < 2) add("${stack.layers.size} layer(s)")
                // Four horizontal blocks was the emulator's answer, not a guess: five put
                // "discriminator" into three broken lines on a 1080px screen.
                val limit = if (stack.horizontal) 4 else 8
                if (stack.layers.size > limit) {
                    add("${stack.layers.size} layers — ${if (stack.horizontal) "a row" else "a column"} fits $limit")
                }
                stack.layers.forEach { layer ->
                    if (layer.label.isBlank()) add("a layer has no label")
                    if (layer.detail?.isBlank() == true) add("layer \"${layer.label}\" has an empty detail line")
                }
                if (stack.backwardLabel?.isBlank() == true) add("an empty backward label")
            }
            if (faults.isEmpty()) null else "$id: ${faults.joinToString("; ")}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `every heatmap is a rectangle of intensities`() {
        val problems = figures.mapNotNull { (id, figure) ->
            val heatmap = figure.shape as? FigureShape.Heatmap ?: return@mapNotNull null
            val faults = buildList {
                if (heatmap.values.isEmpty()) add("no rows")
                val width = heatmap.values.firstOrNull()?.size ?: 0
                if (width == 0) add("no columns")
                if (heatmap.values.any { it.size != width }) add("ragged rows — the cells would not line up")
                // The ramp has no scale of its own, so a value above 1 is not "hotter", it is clamped
                // and silently equal to every other value above 1.
                heatmap.values.flatten().filterNot { it in 0f..1f }.forEach {
                    add("value $it is outside the 0..1 ramp")
                }
                if (heatmap.rowLabels.isNotEmpty() && heatmap.rowLabels.size != heatmap.values.size) {
                    add("${heatmap.rowLabels.size} row labels for ${heatmap.values.size} rows")
                }
                if (heatmap.colLabels.isNotEmpty() && heatmap.colLabels.size != width) {
                    add("${heatmap.colLabels.size} column labels for $width columns")
                }
                heatmap.marks.forEach { mark ->
                    if (mark.row !in heatmap.values.indices || mark.col !in 0 until width) {
                        add("mark at (${mark.row}, ${mark.col}) is off the matrix")
                    }
                }
            }
            if (faults.isEmpty()) null else "$id: ${faults.joinToString("; ")}"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}
