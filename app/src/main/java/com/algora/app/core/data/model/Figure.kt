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

    /**
     * A small graph with hand-placed nodes. Unlike [Tree] there is no layout to derive — the point of
     * most graph figures is a specific arrangement (the two halves of a bipartite check, the layers
     * of a DAG), so positions are part of the spec.
     */
    data class Graph(
        val nodes: List<FigureGraphNode>,
        val edges: List<FigureEdge>,
    ) : FigureShape

    /**
     * Axes with either curves or bars on them. The first shape the AI topics needed that the DSA six
     * could not fake: a loss falling over steps, an activation's kink at zero, the two arms of the
     * bias-variance trade-off, a softmax distribution. All of those are a quantity read against a
     * scale, which none of the cell-and-node shapes can say.
     *
     * [series] and [bars] are alternatives, not layers — a curve and a distribution are different
     * questions, and drawing both on one axis makes neither legible. Exactly one is populated, which
     * `FigureShapeTest` enforces since the type cannot.
     */
    data class Plot(
        val series: List<FigureSeries> = emptyList(),
        val bars: List<FigureBar> = emptyList(),
        val xLabel: String? = null,
        val yLabel: String? = null,
        /** Called-out points: the minimum, the operating point, the knee of the curve. */
        val markers: List<FigurePoint> = emptyList(),
    ) : FigureShape

    /**
     * Ordered blocks with the signal running through them. A network architecture is the one thing an
     * AI page almost always draws on a whiteboard and never had a way to draw here: [Stacks] is the
     * wrong shape for it, because a call stack's entries are the same kind of thing and a network's
     * layers deliberately are not.
     */
    data class LayerStack(
        val layers: List<FigureLayer>,
        /** Left to right instead of top to bottom — an unrolled RNN, an encoder beside its decoder. */
        val horizontal: Boolean = false,
        /** When set, a return arrow runs back along the stack carrying this label — "∂L/∂w". */
        val backwardLabel: String? = null,
    ) : FigureShape
}

data class FigureLayer(
    val label: String,
    /** The shape line under the name: "784 → 128", "3×3×64, stride 1". */
    val detail: String? = null,
    val tone: FigureTone = FigureTone.Muted,
)

data class FigureSeries(
    val label: String,
    /** Draw order, left to right. */
    val points: List<FigurePoint>,
    val tone: FigureTone = FigureTone.Primary,
    /** For the baseline a curve is being compared against — plain SGD under Adam, chance under ROC. */
    val dashed: Boolean = false,
)

/**
 * A point in plot space. Both axes run 0f..1f, and **y is measured up from the axis** — the renderer
 * flips it. Authoring a loss curve with y descending would otherwise mean writing every value
 * upside down.
 */
data class FigurePoint(
    val x: Float,
    val y: Float,
    /** Set only on a [FigureShape.Plot.markers] entry; points inside a series are not labelled. */
    val label: String? = null,
    val tone: FigureTone = FigureTone.Accent,
)

data class FigureBar(
    val label: String,
    /** 0f..1f of the axis height. */
    val value: Float,
    val tone: FigureTone = FigureTone.Primary,
)

data class FigureGraphNode(
    val label: String,
    /** Position inside the card, 0f..1f in both axes. */
    val x: Float,
    val y: Float,
    val tone: FigureTone = FigureTone.Muted,
)

data class FigureEdge(
    val from: Int,
    val to: Int,
    val label: String? = null,
    val directed: Boolean = false,
    val tone: FigureTone = FigureTone.Muted,
)

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
    /** Names the transition. Needed once a cell has more than two arrows into it to tell apart. */
    val label: String? = null,
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
