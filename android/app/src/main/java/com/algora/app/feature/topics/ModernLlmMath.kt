package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// ── D5 · Modern LLM technique math ───────────────────────────────────────────
// Prompting, chain-of-thought and self-consistency, Tree of Thoughts, vector search, the ReAct loop,
// tool-using agents, and hallucination mitigation. Everything the seven labs quote is computed here
// and pinned by `D5MathTest`.
//
// One honesty note that the copy repeats: none of these labs run a language model. What they do run
// is the *problem the technique is solving* — a rule-induction problem for prompting, an exact
// plurality-vote calculation for self-consistency, a real search over Game of 24 for Tree of
// Thoughts, a real index over real distances for vector databases, and a real retriever over a real
// corpus for ReAct. Where a model's behaviour has to be stood in for, it is stood in for by a stated
// parameter (a per-step accuracy, a number of distinct wrong answers) rather than by a number typed
// in to make the story work.

// ── Prompt engineering ───────────────────────────────────────────────────────

/**
 * A prompt is an induction problem: the demonstrations have to pick one rule out of the space of
 * rules consistent with them. This lab makes that space explicit and small enough to enumerate, so
 * "few-shot works" becomes "k demonstrations left h hypotheses standing".
 *
 * The task is character extraction, chosen because several plausible rules agree on most words and
 * disagree on a few — which is the whole phenomenon.
 */
internal object PromptLab {

    class Rule(val name: String, val apply: (String) -> Char)

    val rules = listOf(
        Rule("first letter") { it.first() },
        Rule("last letter") { it.last() },
        Rule("middle letter") { it[it.length / 2] },
        Rule("most frequent letter") { word ->
            word.toList().groupBy { it }.entries
                .sortedWith(compareByDescending<Map.Entry<Char, List<Char>>> { it.value.size }.thenBy { it.key })
                .first().key
        },
        Rule("alphabetically first letter") { it.toList().min() },
    )

    /** The rule the demonstrations were actually generated from. */
    val trueRule = rules.first { it.name == "middle letter" }

    /**
     * The pool a prompt author picks demonstrations from, ordered so the reading path is the
     * interesting one: the first three are the words a person would reach for first, and they leave
     * the rule underdetermined. `level`, `melon` and `sonar` each identify it on their own.
     */
    val pool = listOf("banana", "adage", "otter", "level", "melon", "sonar")

    val query = "kayak"

    fun label(word: String) = trueRule.apply(word)

    fun consistent(demos: List<String>): List<Rule> =
        rules.filter { rule -> demos.all { rule.apply(it) == label(it) } }

    /**
     * What the learner would answer given the surviving hypotheses, as a distribution: each rule
     * votes for its own output with equal weight (a uniform posterior over the version space).
     */
    fun prediction(demos: List<String>): Map<Char, Double> {
        val survivors = consistent(demos)
        return survivors.groupBy { it.apply(query) }
            .mapValues { (_, group) -> group.size.toDouble() / survivors.size }
    }

    fun predictedCorrectly(demos: List<String>): Boolean {
        val posterior = prediction(demos)
        val best = posterior.maxByOrNull { it.value } ?: return false
        val tied = posterior.filterValues { it == best.value }
        return tied.size == 1 && best.key == label(query)
    }

    /** The prompt author's real choice: which demonstrations, not how many. */
    fun identifyingPairs(): List<Pair<String, String>> =
        pool.indices.flatMap { i -> (i + 1 until pool.size).map { j -> pool[i] to pool[j] } }
            .filter { (a, b) -> consistent(listOf(a, b)).size == 1 }

    fun allPairs(): List<Pair<String, String>> =
        pool.indices.flatMap { i -> (i + 1 until pool.size).map { j -> pool[i] to pool[j] } }

    /** Survivors after taking the pool in its given order, one demonstration at a time. */
    fun eliminationCurve(order: List<String> = pool): List<Pair<Int, Int>> =
        (0..order.size).map { k -> k to consistent(order.take(k)).size }

    /**
     * An instruction ("the answer is a letter from the middle of the word") does not add data — it
     * puts prior mass on some hypotheses and none on others. Priced in demonstrations: how many
     * examples would it take to eliminate the same rules?
     */
    fun instructionEliminates(): List<Rule> =
        rules.filter { it.name.contains("first") || it.name.contains("last") }

    fun demosToEliminate(target: List<Rule>, order: List<String> = pool): Int {
        val names = target.map { it.name }.toSet()
        for (k in 0..order.size) {
            val survivors = consistent(order.take(k)).map { it.name }.toSet()
            if (names.none { it in survivors }) return k
        }
        return order.size
    }
}

// ── Chain of thought and self-consistency ────────────────────────────────────

/**
 * Two exact calculations, no sampling anywhere.
 *
 * The first is the arithmetic of decomposition: a chain of n steps each correct with probability p
 * succeeds with probability pⁿ, and it is only worth doing when that beats answering in one shot.
 * The second is self-consistency's plurality vote, computed exactly by enumerating the multinomial
 * rather than by simulating draws — which is what makes the lab's central finding checkable.
 */
internal object CotLab {

    /** Direct answering: one hard step. */
    const val directAccuracy = 0.55

    /** Per-step accuracy once the problem is decomposed — each step is easier than the whole. */
    const val stepAccuracy = 0.92

    fun chainAccuracy(steps: Int, p: Double = stepAccuracy) = p.pow(steps)

    /** The chain is worth it while pⁿ > q. Returns the last n at which decomposition still wins. */
    fun breakEvenSteps(p: Double = stepAccuracy, q: Double = directAccuracy): Int {
        var n = 1
        while (chainAccuracy(n + 1, p) > q) n++
        return n
    }

    /**
     * Exact probability that the plurality of `samples` independent answers is the correct one, with
     * the incorrect mass spread evenly over `distinctWrong` distinct wrong answers. Ties count as
     * failures, which is the conservative reading and is stated in the lab.
     */
    fun pluralityAccuracy(p: Double, samples: Int, distinctWrong: Int): Double {
        require(distinctWrong >= 1)
        val wrongEach = (1.0 - p) / distinctWrong
        var total = 0.0

        fun logFactorial(n: Int): Double = (1..n).sumOf { ln(it.toDouble()) }

        // Enumerate every count vector (correct, wrong_1 .. wrong_d) summing to `samples`.
        fun walk(index: Int, remaining: Int, counts: IntArray) {
            if (index == distinctWrong) {
                counts[index] = remaining
                val correct = counts[0]
                val topWrong = (1..distinctWrong).maxOf { counts[it] }
                if (correct > topWrong) {
                    var logProb = logFactorial(samples)
                    counts.forEach { logProb -= logFactorial(it) }
                    logProb += counts[0] * ln(p)
                    for (i in 1..distinctWrong) logProb += counts[i] * ln(wrongEach)
                    total += kotlin.math.exp(logProb)
                }
                return
            }
            for (c in 0..remaining) {
                counts[index] = c
                walk(index + 1, remaining - c, counts)
            }
        }

        walk(0, samples, IntArray(distinctWrong + 1))
        return total
    }

    /** The odd-sample sweep the lab plots, at a fixed p, for one wrong-answer spread. */
    fun votingCurve(p: Double, distinctWrong: Int, maxSamples: Int = 9): List<Pair<Int, Double>> =
        (1..maxSamples step 2).map { it to pluralityAccuracy(p, it, distinctWrong) }

    /**
     * Confidence as self-consistency reports it: the expected share of samples landing on the modal
     * answer. This is the number that gets used as a hallucination signal, and the point of
     * computing it exactly is that it is *high* precisely when the wrong answers agree with each
     * other — the systematic-error case.
     */
    fun modalShare(p: Double, samples: Int, distinctWrong: Int): Double {
        val wrongEach = (1.0 - p) / distinctWrong

        fun logFactorial(n: Int): Double = (1..n).sumOf { ln(it.toDouble()) }

        var expected = 0.0

        fun walk(index: Int, remaining: Int, counts: IntArray) {
            if (index == distinctWrong) {
                counts[index] = remaining
                var logProb = logFactorial(samples)
                counts.forEach { logProb -= logFactorial(it) }
                if (counts[0] > 0) logProb += counts[0] * ln(p)
                for (i in 1..distinctWrong) if (counts[i] > 0) logProb += counts[i] * ln(wrongEach)
                val prob = kotlin.math.exp(logProb)
                expected += prob * counts.max().toDouble() / samples
                return
            }
            for (c in 0..remaining) {
                counts[index] = c
                walk(index + 1, remaining - c, counts)
            }
        }

        walk(0, samples, IntArray(distinctWrong + 1))
        return expected
    }
}

// ── Tree of Thoughts ─────────────────────────────────────────────────────────

/**
 * A real search over Game of 24 — the benchmark the Tree of Thoughts paper leads with. A state is a
 * multiset of numbers; a step replaces two of them with the result of an operation; the goal is the
 * single number 24.
 *
 * Nothing here is scripted. The exhaustive search, the greedy path and the beam are the same code
 * run at different widths, and the heuristic evaluator standing in for the paper's "value prompt" is
 * deliberately cheap and imperfect — its quality is measured rather than assumed.
 */
internal object TotLab {

    val puzzle = listOf(4.0, 9.0, 10.0, 13.0)
    const val target = 24.0
    private const val eps = 1e-6

    class Step(val expression: String, val result: Double, val state: List<Double>)

    fun successors(state: List<Double>): List<Step> {
        val out = mutableListOf<Step>()
        for (i in state.indices) for (j in state.indices) {
            if (i >= j) continue
            val a = state[i]
            val b = state[j]
            val rest = state.filterIndexed { index, _ -> index != i && index != j }
            val combos = mutableListOf<Pair<String, Double>>(
                "${fmt(a)} + ${fmt(b)}" to a + b,
                "${fmt(a)} × ${fmt(b)}" to a * b,
                "${fmt(a)} − ${fmt(b)}" to a - b,
                "${fmt(b)} − ${fmt(a)}" to b - a,
            )
            if (abs(b) > eps) combos += "${fmt(a)} ÷ ${fmt(b)}" to a / b
            if (abs(a) > eps) combos += "${fmt(b)} ÷ ${fmt(a)}" to b / a
            combos.forEach { (expr, value) ->
                out += Step(expr, value, (rest + value).sortedDescending())
            }
        }
        return out
    }

    fun solved(state: List<Double>) = state.size == 1 && abs(state[0] - target) < eps

    private fun fmt(x: Double) = if (abs(x - Math.round(x)) < eps) Math.round(x).toString() else "%.2f".format(x)

    /** A state as the labs draw it. */
    fun label(state: List<Double>) = state.joinToString(" ") { fmt(it) }

    /** The frontier an evaluator ranks, with what it scored and whether it can actually reach 24. */
    fun rankedFrontier(depth: Int = 1, state: List<Double> = puzzle): List<Triple<List<Double>, Double, Boolean>> =
        successors(state)
            .map { it.state }
            .distinctBy { label(it) }
            .map { Triple(it, heuristic(it, depth), reachable(it)) }
            .sortedBy { it.second }

    class SearchResult(val solved: Boolean, val expanded: Int, val evaluatorCalls: Int, val trace: List<String>)

    /** Every reachable state, for the node count and the number of distinct solution paths. */
    fun exhaustive(state: List<Double> = puzzle): Pair<Int, Int> {
        var expanded = 0
        var solutions = 0

        fun walk(current: List<Double>) {
            expanded++
            if (current.size == 1) {
                if (solved(current)) solutions++
                return
            }
            successors(current).forEach { walk(it.state) }
        }

        walk(state)
        return expanded to solutions
    }

    /**
     * The stand-in for the paper's value prompt, with its quality as a parameter. `depth` is how
     * many operations it looks ahead before falling back on the cheap proxy "could one more
     * operation on some pair land on 24?", which ignores the numbers left over and is therefore
     * optimistic. Lower is better.
     *
     * depth 1 is that proxy applied directly; depth 2 sees one operation further; depth 3 is exact
     * from a four-number state. The lab measures the beam width each one needs, which is the whole
     * argument for search: width substitutes for evaluator quality.
     */
    fun heuristic(state: List<Double>, depth: Int = 1): Double {
        if (state.size == 1) return abs(state[0] - target)
        val next = successors(state)
        if (depth <= 1) return next.minOf { abs(it.result - target) }
        return next.minOf { heuristic(it.state, depth - 1) }
    }

    /** Successor generations spent by one evaluator call — what a deeper evaluator costs. */
    fun evaluatorCost(state: List<Double>, depth: Int = 1): Int {
        if (state.size == 1) return 0
        val next = successors(state)
        if (depth <= 1) return 1
        return 1 + next.sumOf { evaluatorCost(it.state, depth - 1) }
    }

    /** Whether a state can still reach 24 — used only to score the heuristic, never to search. */
    fun reachable(state: List<Double>): Boolean {
        if (state.size == 1) return solved(state)
        return successors(state).any { reachable(it.state) }
    }

    /** Beam search at width `width`, ranking by the evaluator. Width 1 is the greedy CoT path. */
    fun beam(width: Int, depth: Int = 1, state: List<Double> = puzzle): SearchResult {
        var expanded = 0
        var calls = 0
        var frontier = listOf(state to emptyList<String>())
        repeat(state.size - 1) {
            val scored = mutableListOf<Triple<List<Double>, List<String>, Double>>()
            frontier.forEach { (current, trace) ->
                expanded++
                successors(current).forEach { step ->
                    calls += evaluatorCost(step.state, depth)
                    scored += Triple(step.state, trace + "${step.expression} = ${fmt(step.result)}", heuristic(step.state, depth))
                }
            }
            frontier = scored.sortedBy { it.third }.take(width).map { it.first to it.second }
        }
        val win = frontier.firstOrNull { solved(it.first) }
        return SearchResult(win != null, expanded, calls, win?.second ?: frontier.first().second)
    }

    /** The smallest width at which this evaluator finds a solution — the number the lab compares. */
    fun widthNeeded(depth: Int, maxWidth: Int = 40): Int =
        (1..maxWidth).firstOrNull { beam(it, depth).solved } ?: -1

    /**
     * How good an evaluator actually is, measured on the frontier it ranks: the rank it gives the
     * best genuinely-solvable state, and how many of its top `width` can still reach 24.
     */
    fun evaluatorQuality(width: Int, depth: Int = 1): Triple<Int, Int, Int> {
        val ranked = successors(puzzle)
            .map { it.state to heuristic(it.state, depth) }
            .distinctBy { it.first.joinToString(",") { v -> "%.4f".format(v) } }
            .sortedBy { it.second }
        val firstSolvableRank = ranked.indexOfFirst { reachable(it.first) } + 1
        val solvableInTop = ranked.take(width).count { reachable(it.first) }
        return Triple(firstSolvableRank, solvableInTop, ranked.size)
    }
}

// ── Vector databases ─────────────────────────────────────────────────────────

private class LcgRandom(private var state: Long) {
    fun nextFloat(): Double {
        state = (state * 6364136223846793005L + 1442695040888963407L)
        return ((state ushr 11).toDouble() / (1L shl 53).toDouble()).let { if (it < 0) it + 1 else it }
    }

    fun nextGaussian(): Double {
        val u1 = nextFloat().coerceAtLeast(1e-9)
        val u2 = nextFloat()
        return sqrt(-2.0 * ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }
}

/**
 * A real index over real vectors. The 2-D corpus is what the lab draws; the high-dimensional
 * measurements (contrast collapse, quantization error) are computed on their own point sets, since
 * the whole point of those claims is that they are about dimension.
 */
internal object VectorDbLab {

    const val corpusSize = 180
    const val clusters = 6
    const val k = 5

    class Doc(val id: Int, val x: Double, val y: Double, val cluster: Int)

    val corpus: List<Doc> by lazy {
        val rng = LcgRandom(20260729L)
        val centres = (0 until clusters).map {
            0.18 + 0.64 * rng.nextFloat() to 0.18 + 0.64 * rng.nextFloat()
        }
        (0 until corpusSize).map { i ->
            val c = i % clusters
            val (cx, cy) = centres[c]
            Doc(
                i,
                (cx + 0.055 * rng.nextGaussian()).coerceIn(0.02, 0.98),
                (cy + 0.055 * rng.nextGaussian()).coerceIn(0.02, 0.98),
                c,
            )
        }
    }

    /**
     * The query sits on a cell boundary — halfway between the two closest coarse centroids. That is
     * not a trick: it is the case an IVF index is actually bad at, and putting the query in the
     * middle of a cell would have measured nothing.
     */
    val query: Pair<Double, Double> by lazy {
        val pairs = ivf.centroids.indices.flatMap { i ->
            (i + 1 until ivf.centroids.size).map { j -> i to j }
        }
        val (i, j) = pairs.minByOrNull { (a, b) ->
            (ivf.centroids[a].first - ivf.centroids[b].first).pow(2) +
                (ivf.centroids[a].second - ivf.centroids[b].second).pow(2)
        }!!
        (ivf.centroids[i].first + ivf.centroids[j].first) / 2 to
            (ivf.centroids[i].second + ivf.centroids[j].second) / 2
    }

    fun distance(doc: Doc, q: Pair<Double, Double> = query) =
        sqrt((doc.x - q.first).pow(2) + (doc.y - q.second).pow(2))

    /** Brute force: every vector compared, exact answer, cost linear in the corpus. */
    fun exactNeighbours(topK: Int = k, q: Pair<Double, Double> = query): List<Doc> =
        corpus.sortedBy { distance(it, q) }.take(topK)

    // ── IVF ──────────────────────────────────────────────────────────────────
    // Lloyd's algorithm over the corpus gives the coarse quantizer; a query probes the `nprobe`
    // nearest cells and scans only those. Recall is measured against the exact answer above.

    class Ivf(val centroids: List<Pair<Double, Double>>, val assignment: IntArray)

    fun buildIvf(cells: Int = 12): Ivf {
        val rng = LcgRandom(4242L)
        var centroids = (0 until cells).map {
            val doc = corpus[(rng.nextFloat() * corpus.size).toInt().coerceIn(0, corpus.size - 1)]
            doc.x to doc.y
        }
        val assignment = IntArray(corpus.size)
        repeat(25) {
            corpus.forEachIndexed { i, doc ->
                assignment[i] = centroids.indices.minByOrNull {
                    (doc.x - centroids[it].first).pow(2) + (doc.y - centroids[it].second).pow(2)
                }!!
            }
            centroids = centroids.indices.map { c ->
                val members = corpus.filterIndexed { i, _ -> assignment[i] == c }
                if (members.isEmpty()) centroids[c]
                else members.sumOf { it.x } / members.size to members.sumOf { it.y } / members.size
            }
        }
        return Ivf(centroids, assignment)
    }

    val ivf: Ivf by lazy { buildIvf() }

    fun ivfSearch(nprobe: Int, topK: Int = k, q: Pair<Double, Double> = query): Pair<List<Doc>, Int> {
        val order = ivf.centroids.indices.sortedBy {
            (q.first - ivf.centroids[it].first).pow(2) + (q.second - ivf.centroids[it].second).pow(2)
        }.take(nprobe).toSet()
        val scanned = corpus.filterIndexed { i, _ -> ivf.assignment[i] in order }
        val comparisons = ivf.centroids.size + scanned.size
        return scanned.sortedBy { distance(it, q) }.take(topK) to comparisons
    }

    fun recall(found: List<Doc>, topK: Int = k, q: Pair<Double, Double> = query): Double {
        val truth = exactNeighbours(topK, q).map { it.id }.toSet()
        return found.count { it.id in truth }.toDouble() / topK
    }

    // ── Graph search ─────────────────────────────────────────────────────────
    // HNSW's inner loop without the layers: greedy best-first over a neighbour graph with a
    // candidate list of size `ef`. Two graphs, because the difference between them is the whole
    // reason HNSW is not a plain k-NN graph.

    /** The obvious graph: every vector linked to its `degree` nearest neighbours. */
    val knnGraph: List<List<Int>> by lazy { buildKnnGraph(6) }

    private fun buildKnnGraph(degree: Int): List<List<Int>> = corpus.map { doc ->
        corpus.filter { it.id != doc.id }
            .sortedBy { sqrt((it.x - doc.x).pow(2) + (it.y - doc.y).pow(2)) }
            .take(degree).map { it.id }
    }

    /**
     * The same graph plus two random long-range links per node. This is the "small world" half of
     * the name, and the measurement below is why it is there rather than for elegance.
     */
    val smallWorldGraph: List<List<Int>> by lazy {
        val rng = LcgRandom(777L)
        knnGraph.mapIndexed { index, near ->
            val extra = (0 until 2).map { (rng.nextFloat() * corpus.size).toInt().coerceIn(0, corpus.size - 1) }
            (near + extra).filter { it != index }.distinct()
        }
    }

    /** Component label per node, links treated as undirected — a search cannot leave its own. */
    fun components(graph: List<List<Int>>): IntArray {
        val adjacency = Array(graph.size) { mutableSetOf<Int>() }
        graph.forEachIndexed { from, links -> links.forEach { adjacency[from] += it; adjacency[it] += from } }
        val label = IntArray(graph.size) { -1 }
        var next = 0
        graph.indices.forEach { start ->
            if (label[start] < 0) {
                val stack = ArrayDeque(listOf(start))
                label[start] = next
                while (stack.isNotEmpty()) {
                    val node = stack.removeLast()
                    adjacency[node].forEach { if (label[it] < 0) { label[it] = next; stack.addLast(it) } }
                }
                next++
            }
        }
        return label
    }

    /**
     * An entry point in a different component from the answer. On a plain k-NN graph these exist,
     * and from one of them no amount of `ef` can help — which is what the extra links fix.
     */
    fun strandedEntry(graph: List<List<Int>> = knnGraph): Int {
        val label = components(graph)
        val targetComponent = label[exactNeighbours(1).first().id]
        return label.indices.first { label[it] != targetComponent }
    }

    /** Share of starting points that cannot reach the answer at all on this graph. */
    fun strandedFraction(graph: List<List<Int>> = knnGraph): Double {
        val label = components(graph)
        val targetComponent = label[exactNeighbours(1).first().id]
        return label.count { it != targetComponent }.toDouble() / label.size
    }

    /** Connected components, treating links as undirected — a graph search cannot leave its own. */
    fun componentCount(graph: List<List<Int>>): Int {
        val adjacency = Array(graph.size) { mutableSetOf<Int>() }
        graph.forEachIndexed { from, links -> links.forEach { adjacency[from] += it; adjacency[it] += from } }
        val seen = BooleanArray(graph.size)
        var components = 0
        graph.indices.forEach { start ->
            if (!seen[start]) {
                components++
                val stack = ArrayDeque(listOf(start))
                seen[start] = true
                while (stack.isNotEmpty()) {
                    val node = stack.removeLast()
                    adjacency[node].forEach { if (!seen[it]) { seen[it] = true; stack.addLast(it) } }
                }
            }
        }
        return components
    }

    class GraphResult(val found: List<Doc>, val comparisons: Int, val hops: Int)

    fun graphSearch(
        ef: Int,
        graph: List<List<Int>> = smallWorldGraph,
        topK: Int = k,
        entry: Int = 0,
        q: Pair<Double, Double> = query,
    ): GraphResult {
        val visited = mutableSetOf(entry)
        var candidates = listOf(entry)
        var best = listOf(entry)
        var hops = 0
        var comparisons = 1
        while (candidates.isNotEmpty()) {
            hops++
            val expanded = candidates.flatMap { graph[it] }.filter { visited.add(it) }
            comparisons += expanded.size
            if (expanded.isEmpty()) break
            val next = (best + expanded).distinct().sortedBy { distance(corpus[it], q) }.take(max(ef, topK))
            if (next == best) break
            candidates = expanded.sortedBy { distance(corpus[it], q) }.take(ef)
            best = next
        }
        return GraphResult(best.take(topK).map { corpus[it] }, comparisons, hops)
    }

    // ── Dimension ────────────────────────────────────────────────────────────

    /**
     * The contrast ratio (d_max − d_min) / d_min over uniform points. It is the reason approximate
     * search gets harder with dimension: when every distance is nearly the same, "nearest" stops
     * being a discriminating question.
     */
    fun contrastRatio(dimension: Int, points: Int = 400, queries: Int = 20, seed: Long = 991L): Double {
        val rng = LcgRandom(seed)
        val data = (0 until points).map { DoubleArray(dimension) { rng.nextFloat() } }
        // Averaged over queries: in two dimensions a single query that lands almost on top of a
        // point sends this statistic into the hundreds, which says nothing about the dimension.
        return (0 until queries).map {
            val q = DoubleArray(dimension) { rng.nextFloat() }
            val distances = data.map { p -> sqrt(p.indices.sumOf { (p[it] - q[it]).pow(2) }) }
            (distances.max() - distances.min()) / distances.min()
        }.sorted()[queries / 2]
    }

    /** Scalar quantization to `levels` per dimension: recall kept, bytes saved. */
    fun quantizedRecall(levels: Int, topK: Int = k): Double {
        fun quant(v: Double) = Math.round(v * (levels - 1)).toDouble() / (levels - 1)
        val approx = corpus.sortedBy {
            sqrt((quant(it.x) - query.first).pow(2) + (quant(it.y) - query.second).pow(2))
        }.take(topK)
        return recall(approx, topK)
    }

    /** The grid spacing quantization imposes — the number to compare against the one below. */
    fun quantizationStep(levels: Int) = 1.0 / (levels - 1)

    /** How far apart the true neighbours are. Quantization destroys a ranking finer than its step. */
    fun neighbourSpread(topK: Int = k): Double {
        val near = exactNeighbours(topK)
        return distance(near.last()) - distance(near.first())
    }

    fun bytesPerVector(dimension: Int, bitsPerComponent: Int) = dimension * bitsPerComponent / 8
}

// ── Retrieval, ReAct and agents ──────────────────────────────────────────────

/**
 * A real corpus and a real retriever, so "the second hop cannot be retrieved by the original query"
 * is a measurement rather than a claim. Scoring is TF-IDF cosine over the corpus's own vocabulary.
 */
internal object RetrievalLab {

    class Passage(val id: Int, val title: String, val text: String)

    val corpus = listOf(
        Passage(0, "Linux kernel", "The Linux kernel is written mainly in the C programming language, with a small amount of assembly."),
        Passage(1, "C language", "C was designed by Dennis Ritchie and first released in 1972 at Bell Labs."),
        Passage(2, "Python", "Python is an interpreted language first released in 1991 by Guido van Rossum."),
        Passage(3, "Rust", "Rust is a systems programming language first released in 2015 and used for kernel modules."),
        Passage(4, "Git", "Git is a distributed version control system written in C and created to host the Linux kernel."),
        Passage(5, "Assembly", "Assembly language is a low level notation released with the earliest machines and still used in kernels."),
        Passage(6, "Bell Labs", "Bell Labs is a research organisation where Unix and the C language were developed."),
        Passage(7, "Unix", "Unix is an operating system written in C and developed at Bell Labs in the early 1970s."),
    )

    private val stop = setOf("the", "is", "a", "an", "of", "in", "and", "with", "was", "were", "at", "to", "for", "by", "it", "that", "as", "on", "used", "still")

    fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() && it !in stop }

    private val df: Map<String, Int> by lazy {
        val counts = mutableMapOf<String, Int>()
        corpus.forEach { p -> tokens(p.text).toSet().forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        counts
    }

    fun vector(text: String): Map<String, Double> {
        val tf = tokens(text).groupingBy { it }.eachCount()
        return tf.mapValues { (term, count) ->
            val idf = ln((corpus.size + 1.0) / ((df[term] ?: 0) + 1.0)) + 1.0
            count * idf
        }
    }

    fun score(query: String, passage: Passage): Double {
        val q = vector(query)
        val d = vector(passage.text)
        val dot = q.keys.intersect(d.keys).sumOf { q.getValue(it) * d.getValue(it) }
        val nq = sqrt(q.values.sumOf { it * it })
        val nd = sqrt(d.values.sumOf { it * it })
        return if (nq == 0.0 || nd == 0.0) 0.0 else dot / (nq * nd)
    }

    fun ranked(query: String): List<Pair<Passage, Double>> =
        corpus.map { it to score(query, it) }.sortedByDescending { it.second }

    fun rankOf(query: String, passageId: Int): Int =
        ranked(query).indexOfFirst { it.first.id == passageId } + 1
}

/**
 * The ReAct loop, run over the retriever above. The question is deliberately two-hop: the passage
 * holding the answer shares almost no vocabulary with the question, so one retrieval cannot find it
 * and the loop's rewritten query can.
 */
internal object ReActLab {

    val question = "In what year was the language the Linux kernel is written in first released?"

    const val hop1PassageId = 0
    const val hop2PassageId = 1

    class Turn(val thought: String, val action: String, val observation: String, val retrievedId: Int?)

    /** Single-shot retrieval: one query, top-k passages, and whether the answer passage is in them. */
    fun singleShot(topK: Int = 3): Pair<List<RetrievalLab.Passage>, Boolean> {
        val top = RetrievalLab.ranked(question).take(topK).map { it.first }
        return top to top.any { it.id == hop2PassageId }
    }

    val trajectory: List<Turn> by lazy {
        val firstQuery = "Linux kernel written language"
        val firstHit = RetrievalLab.ranked(firstQuery).first().first
        val secondQuery = "C language first released"
        val secondHit = RetrievalLab.ranked(secondQuery).first().first
        listOf(
            Turn(
                "The question has two parts. I need the language first, then that language's release year.",
                "search(\"$firstQuery\")",
                firstHit.text,
                firstHit.id,
            ),
            Turn(
                "The language is C. The observation does not give a year, so the original question cannot be answered yet.",
                "search(\"$secondQuery\")",
                secondHit.text,
                secondHit.id,
            ),
            Turn(
                "The observation gives 1972 for C, and the first hop established that the kernel is written in C.",
                "finish(\"1972\")",
                "1972",
                null,
            ),
        )
    }

    /** What a chain-of-thought answer has to do instead: commit without checking. */
    val closedBookAnswer = "1970"
    val groundedAnswer = "1972"
}

/**
 * Tool-using agents, priced. The loop is real (three tools, a real trajectory including one failed
 * call), and the cost model is the one that actually surprises people: the whole trajectory is
 * resent every turn, so the tokens billed grow quadratically in the number of steps.
 */
internal object AgentLab {

    class Tool(val name: String, val latencyMs: Int, val schemaTokens: Int)

    val tools = listOf(
        Tool("search", 240, 38),
        Tool("calculator", 15, 22),
        Tool("units", 12, 26),
    )

    class Step(val label: String, val tokens: Int, val failed: Boolean = false)

    /**
     * A real six-step trajectory. Step 4 is a tool call that came back as an error and had to be
     * reissued, which is what makes the retry's cost measurable rather than asserted: the clean
     * trajectory is this list with the two failure steps removed.
     */
    val trajectory = listOf(
        Step("thought + search(\"…\")", 48),
        Step("observation: 3 passages", 96),
        Step("thought + units(\"mi\", \"km\")", 61),
        Step("observation: error, unknown unit \"mi\"", 34, failed = true),
        Step("thought + units(\"mile\", \"km\")", 54, failed = true),
        Step("observation: 1.609", 24),
        Step("thought + calculator(\"…\")", 43),
        Step("observation: 386.2 → final answer", 74),
    )

    const val systemTokens = 180

    fun schemaTokens() = tools.sumOf { it.schemaTokens }

    fun contextAt(step: Int, steps: List<Step> = trajectory) =
        systemTokens + schemaTokens() + steps.take(step).sumOf { it.tokens }

    /** What the provider bills: every turn resends everything before it. */
    fun billedTokens(steps: List<Step> = trajectory) =
        // One request per model turn — the odd-indexed entries are tool output, not a new request.
        steps.indices.filter { it % 2 == 0 }.sumOf { contextAt(it + 1, steps) }

    fun finalContext(steps: List<Step> = trajectory) = contextAt(steps.size, steps)

    fun billingMultiple(steps: List<Step> = trajectory) =
        billedTokens(steps).toDouble() / finalContext(steps)

    val cleanTrajectory get() = trajectory.filterNot { it.failed }

    /** The retry's real cost: what the failure added to the bill, not just to the transcript. */
    fun retryOverhead() = billedTokens() - billedTokens(cleanTrajectory)

    /** Three independent calls: the difference between issuing them in one turn and in three. */
    fun sequentialLatency() = tools.sumOf { it.latencyMs }

    fun parallelLatency() = tools.maxOf { it.latencyMs }
}

// ── Hallucination mitigation ─────────────────────────────────────────────────

/**
 * The measured version of "the model is confident and wrong". Each question carries a per-chain
 * accuracy and a spread of wrong answers; confidence is what self-consistency would report
 * (`CotLab.modalShare`) and accuracy is what it would actually get right. Calibration error, the
 * abstention sweep and the retrieval comparison all fall out of that one table.
 */
internal object HallucinationLab {

    class Question(
        val text: String,
        val chainAccuracy: Double,
        val distinctWrong: Int,
        val supported: Boolean,
    )

    const val samples = 5

    /**
     * The construction, stated because the conclusion depends on it: the four unanswerable questions
     * are ones the model is *systematically* wrong about — every chain lands on the same wrong
     * answer (`distinctWrong = 1`). That is what a hallucination is, as opposed to a coin flip, and
     * it is what makes agreement between samples worthless as a signal here. `scatteredQuestions`
     * below is the same population with that one property changed, so the mechanism is visible
     * rather than assumed.
     */
    val questions = listOf(
        Question("Which language is the Linux kernel written in?", 0.94, 4, true),
        Question("When was C first released?", 0.90, 4, true),
        Question("Who developed Unix?", 0.88, 4, true),
        Question("Which organisation employed Dennis Ritchie?", 0.82, 3, true),
        Question("What year was Python released?", 0.78, 3, true),
        Question("What is Rust's release year?", 0.70, 3, true),
        Question("Which release introduced the parser rewrite?", 0.30, 1, false),
        Question("What is the current maintainer's tenure?", 0.22, 1, false),
        Question("How many modules shipped in the last release?", 0.18, 1, false),
        Question("What is the internal name of the scheduler patch?", 0.12, 1, false),
    )

    /** The control: the same questions, but the model's errors are scattered rather than fixed. */
    val scatteredQuestions = questions.map {
        if (it.supported) it else Question(it.text, it.chainAccuracy, 4, false)
    }

    fun confidence(q: Question) = CotLab.modalShare(q.chainAccuracy, samples, q.distinctWrong)

    fun accuracy(q: Question) = CotLab.pluralityAccuracy(q.chainAccuracy, samples, q.distinctWrong)

    fun overallAccuracy(population: List<Question> = questions) =
        population.sumOf { accuracy(it) } / population.size

    /** Expected calibration error over `bins` equal-width confidence bins. */
    fun expectedCalibrationError(population: List<Question> = questions, bins: Int = 5): Double {
        val binned = population.groupBy { ((confidence(it) * bins).toInt()).coerceIn(0, bins - 1) }
        return binned.values.sumOf { group ->
            val conf = group.sumOf { confidence(it) } / group.size
            val acc = group.sumOf { accuracy(it) } / group.size
            group.size.toDouble() / population.size * abs(conf - acc)
        }
    }

    /** Answer only above a confidence threshold: coverage, and accuracy on what was answered. */
    fun abstentionSweep(population: List<Question> = questions): List<Triple<Double, Double, Double>> =
        listOf(0.0, 0.5, 0.6, 0.7, 0.8, 0.9).map { threshold ->
            val answered = population.filter { confidence(it) >= threshold }
            val coverage = answered.size.toDouble() / population.size
            val selective = if (answered.isEmpty()) 1.0 else answered.sumOf { accuracy(it) } / answered.size
            Triple(threshold, coverage, selective)
        }

    /** The best selective accuracy a confidence threshold can buy, and what it costs in coverage. */
    fun bestThreshold(population: List<Question> = questions): Triple<Double, Double, Double> =
        abstentionSweep(population).filter { it.second >= 0.5 }.maxByOrNull { it.third }!!

    /**
     * Grounding: refuse unless the retrieved passages support the claim. A different mechanism from
     * thresholding on confidence — it looks at the evidence rather than at the model's agreement
     * with itself — and on this population it is the one that works.
     */
    fun groundedCoverage(population: List<Question> = questions) =
        population.count { it.supported }.toDouble() / population.size

    fun groundedSelectiveAccuracy(population: List<Question> = questions): Double {
        val supported = population.filter { it.supported }
        return supported.sumOf { accuracy(it) } / supported.size
    }

    /** The systematic-error case: high reported confidence, low accuracy. */
    fun confidentlyWrong(population: List<Question> = questions) =
        population.filter { confidence(it) > 0.55 && accuracy(it) < 0.5 }

    fun minSupportedConfidence(population: List<Question> = questions) =
        population.filter { it.supported }.minOf { confidence(it) }

    fun maxUnsupportedConfidence(population: List<Question> = questions) =
        population.filter { !it.supported }.maxOf { confidence(it) }

    fun confidenceSeparates(population: List<Question> = questions) =
        minSupportedConfidence(population) > maxUnsupportedConfidence(population)

    fun spread(population: List<Question> = questions) = population.map { confidence(it) to accuracy(it) }
}
