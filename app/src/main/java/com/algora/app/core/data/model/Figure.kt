package com.algora.app.core.data.model

/**
 * A static diagram drawn above a topic's How-It-Works steps: the shape of the pattern at a glance,
 * before the prose walks through it. The simulation below the fold animates one worked example; this
 * answers "what am I looking at" without anyone pressing play.
 *
 * Data only, like [SimulationType]'s configs — no Compose types — so the specs stay unit-testable and
 * the renderer (`feature/topics/FigureCard.kt`) owns every colour and dimension.
 */
data class Figure(
    val caption: String,
    val shape: FigureShape,
)

/**
 * Colour roles rather than colours. The step cards carry raw `0xFF…` accents because the mock
 * specifies them per card; a figure is drawn on a canvas that has to read in both themes, so it names
 * the role and lets the renderer resolve it against the current scheme.
 */
enum class FigureTone {
    /** The structure being maintained — a window, a live range, the current subtree. */
    Primary,

    /** The thing the pattern is doing right now, or the answer it produces. */
    Accent,

    /** Context that is present but not the point: already-settled values, unvisited cells. */
    Muted,

    /** The case the pattern excludes — a rejected candidate, an invalid state, an evicted entry. */
    Warn,
}

sealed interface FigureShape {

    /**
     * A single row of labelled cells: arrays, strings, bit rows, and every pattern whose state is a
     * range over a sequence. [bands] draw behind a cell range, [pointers] hang below one cell.
     */
    data class Strip(
        val cells: List<String>,
        val bands: List<FigureBand> = emptyList(),
        val pointers: List<FigurePointer> = emptyList(),
        /** Optional second row under the first — a prefix table, a deque, an accumulator. */
        val aux: List<String> = emptyList(),
        val auxLabel: String? = null,
    ) : FigureShape

    /**
     * Intervals on a shared axis. Everything whose input is a set of ranges — merging, scheduling,
     * sweeping — is the same picture with a different question asked of it.
     */
    data class Timeline(
        val spans: List<FigureSpan>,
        val axisMax: Int,
        /** A vertical line: the sweep position, or the moment being asked about. */
        val marker: Int? = null,
        val markerLabel: String? = null,
    ) : FigureShape

    /**
     * A table: DP over two indices, a matrix, or a grid of cells. [arrows] draw the dependency the
     * recurrence has — which is the part of a DP that prose struggles to say and a picture does not.
     */
    data class Grid(
        val rows: List<List<String>>,
        val rowHeaders: List<String> = emptyList(),
        val colHeaders: List<String> = emptyList(),
        val marks: List<FigureCell> = emptyList(),
        val arrows: List<FigureArrow> = emptyList(),
    ) : FigureShape

    /**
     * One or two vertical stacks side by side — a call stack, a monotonic stack, the two heaps of a
     * running median. Entries are listed top-first, the way the structure is talked about.
     */
    data class Stacks(
        val columns: List<FigureStack>,
    ) : FigureShape

    /**
     * A rooted tree: real trees, tries, recursion trees, game trees. Nodes are ordered parents-first
     * (a node's parent is always an earlier index), which is also how they are laid out — leaves take
     * sequential slots and parents centre over their children.
     */
    data class Tree(
        val nodes: List<FigureNode>,
    ) : FigureShape
}

data class FigureNode(
    val label: String,
    /** Index of the parent in the same list, or null for the root. Must be smaller than this node's. */
    val parent: Int?,
    val tone: FigureTone = FigureTone.Muted,
)

data class FigureCell(
    val row: Int,
    val col: Int,
    val tone: FigureTone = FigureTone.Primary,
)

data class FigureArrow(
    val fromRow: Int,
    val fromCol: Int,
    val toRow: Int,
    val toCol: Int,
    val tone: FigureTone = FigureTone.Accent,
)

data class FigureStack(
    val label: String,
    /** Top of the stack first. */
    val entries: List<String>,
    val tone: FigureTone = FigureTone.Primary,
    val note: String? = null,
)

data class FigureBand(
    val from: Int,
    val to: Int,
    val label: String,
    val tone: FigureTone = FigureTone.Primary,
)

data class FigurePointer(
    val index: Int,
    val label: String,
    val tone: FigureTone = FigureTone.Accent,
)

data class FigureSpan(
    val start: Int,
    val end: Int,
    val label: String,
    val tone: FigureTone = FigureTone.Primary,
)
