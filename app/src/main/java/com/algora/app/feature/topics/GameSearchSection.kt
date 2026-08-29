package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

// ── Game search player ───────────────────────────────────────────────────────
// Search and model-based RL. The search labs run on a real tic-tac-toe engine — minimax and
// alpha-beta count their own nodes, MCTS actually plays rollouts — and the model-based labs run on
// the same six-state chain the other RL players use, with a model estimated from a limited number of
// real transitions so its errors are real errors.
//
// Render parts: a board, labelled bar rows, and curve plots.

private class GsCurve(val label: String, val values: List<Float>, val color: Color)

private class GsPlot(
    val label: String,
    val curves: List<GsCurve>,
    val yRange: ClosedFloatingPointRange<Float>,
    val xLabel: String,
)

private class GsBar(val label: String, val values: List<Float>, val color: Color, val captions: List<String> = emptyList())

private class GsFrame(
    val status: String,
    val board: IntArray? = null,
    val boardMarks: Map<Int, Float> = emptyMap(),
    val plot: GsPlot? = null,
    val bars: List<GsBar> = emptyList(),
    val readout: String? = null,
)

private class GsConfig(
    val intro: String,
    val legend: List<Pair<Color, String>>,
    val build: () -> List<GsFrame>,
)

private val PlainTone = SimColors.Grey
private val GoodTone = SimColors.Green
private val BadTone = Color(0xFFEC4899)
private val MainTone = SimColors.Blue
private val DeepTone = Color(0xFF7C3AED)

private class GsRng(private var state: Int) {
    fun next(): Float {
        state = state * 1103515245 + 12345
        return ((state ushr 16) and 0x7fff) / 32767f
    }

    fun nextInt(bound: Int) = (next() * bound).toInt().coerceIn(0, bound - 1)
}

// ── Tic-tac-toe ──────────────────────────────────────────────────────────────
// X = 1, O = −1. Small enough to search exhaustively, big enough that the node counts mean
// something.

private val LINES = listOf(
    intArrayOf(0, 1, 2), intArrayOf(3, 4, 5), intArrayOf(6, 7, 8),
    intArrayOf(0, 3, 6), intArrayOf(1, 4, 7), intArrayOf(2, 5, 8),
    intArrayOf(0, 4, 8), intArrayOf(2, 4, 6),
)

private fun winnerOf(board: IntArray): Int {
    LINES.forEach { line ->
        val first = board[line[0]]
        if (first != 0 && first == board[line[1]] && first == board[line[2]]) return first
    }
    return 0
}

private fun legalMoves(board: IntArray) = board.indices.filter { board[it] == 0 }

private fun isTerminal(board: IntArray) = winnerOf(board) != 0 || legalMoves(board).isEmpty()

/**
 * The shared position: X to move with a fork available. X at 0 and 4, O at 8 and 2 — enough pieces
 * that the tree is small, few enough that there is still a real decision.
 */
private fun startPosition(): IntArray = intArrayOf(1, 0, -1, 0, 1, 0, 0, 0, -1)

private class SearchCount(var nodes: Int = 0)

private fun minimax(board: IntArray, player: Int, count: SearchCount): Int {
    count.nodes++
    val winner = winnerOf(board)
    if (winner != 0) return winner
    val moves = legalMoves(board)
    if (moves.isEmpty()) return 0

    var best = -player * 2
    for (move in moves) {
        board[move] = player
        val value = minimax(board, -player, count)
        board[move] = 0
        best = if (player == 1) maxOf(best, value) else minOf(best, value)
    }
    return best
}

private enum class Ordering { NONE, CENTRE_FIRST, TACTICAL }

private fun alphaBeta(board: IntArray, player: Int, alpha: Int, beta: Int, count: SearchCount, ordering: Ordering = Ordering.NONE): Int {
    count.nodes++
    val winner = winnerOf(board)
    if (winner != 0) return winner
    var moves = legalMoves(board)
    if (moves.isEmpty()) return 0
    moves = when (ordering) {
        Ordering.NONE -> moves
        // The plausible-looking heuristic: centre, then corners, then edges.
        Ordering.CENTRE_FIRST -> moves.sortedBy { if (it == 4) 0 else if (it % 2 == 0) 1 else 2 }
        // The one that actually helps: immediate wins first, then moves that block one.
        Ordering.TACTICAL -> moves.sortedByDescending { move ->
            board[move] = player
            val wins = if (winnerOf(board) == player) 1 else 0
            board[move] = -player
            val blocks = if (winnerOf(board) == -player) 1 else 0
            board[move] = 0
            2 * wins + blocks
        }
    }

    var a = alpha
    var b = beta
    var best = -player * 2
    for (move in moves) {
        board[move] = player
        val value = alphaBeta(board, -player, a, b, count, ordering)
        board[move] = 0
        if (player == 1) {
            best = maxOf(best, value)
            a = maxOf(a, best)
        } else {
            best = minOf(best, value)
            b = minOf(b, best)
        }
        if (b <= a) break
    }
    return best
}

private fun rootValues(board: IntArray, player: Int): Map<Int, Int> =
    legalMoves(board).associateWith { move ->
        board[move] = player
        val value = minimax(board, -player, SearchCount())
        board[move] = 0
        value
    }

// ── Minimax ──────────────────────────────────────────────────────────────────

private fun minimaxFrames(): List<GsFrame> {
    val board = startPosition()
    val values = rootValues(board.copyOf(), 1)
    val plain = SearchCount()
    minimax(board.copyOf(), 1, plain)
    val pruned = SearchCount()
    alphaBeta(board.copyOf(), 1, -2, 2, pruned)
    val orderedCount = SearchCount()
    alphaBeta(board.copyOf(), 1, -2, 2, orderedCount, Ordering.TACTICAL)
    val badOrder = SearchCount()
    alphaBeta(board.copyOf(), 1, -2, 2, badOrder, Ordering.CENTRE_FIRST)

    val moves = values.keys.sorted()
    val frames = mutableListOf<GsFrame>()

    frames += GsFrame(
        status = "X to move. Minimax assumes both players are perfect: X maximises the final result, O minimises it, " +
            "all the way down to positions that are already decided.",
        board = board,
    )
    frames += GsFrame(
        status = "Every legal move, scored by what perfect play leads to: " +
            moves.joinToString(", ") { "square $it → ${values.getValue(it)}" } +
            ". +1 is a win for X, 0 a draw, −1 a loss.",
        board = board,
        boardMarks = values.mapValues { it.value.toFloat() },
        bars = listOf(GsBar("minimax value per move", moves.map { values.getValue(it).toFloat() }, MainTone, moves.map { "sq $it" })),
        readout = "best: square ${moves.maxByOrNull { values.getValue(it) }} (value ${values.values.max()})",
    )
    frames += GsFrame(
        status = "Reaching those numbers took ${plain.nodes} node visits — the entire game tree below this position, " +
            "because plain minimax has no reason to stop early.",
        board = board,
        readout = "${plain.nodes} nodes",
    )
    frames += GsFrame(
        status = "Alpha-beta prunes any branch that cannot change the answer: once O has a reply at least as good as " +
            "something X already has elsewhere, the rest of that branch is irrelevant. Same values, " +
            "${pruned.nodes} nodes — ${"%.0f".format(100f - 100f * pruned.nodes / plain.nodes)}% fewer.",
        board = board,
        bars = listOf(
            GsBar(
                "nodes visited",
                listOf(plain.nodes.toFloat(), pruned.nodes.toFloat(), orderedCount.nodes.toFloat()),
                MainTone,
                listOf("minimax", "alpha-beta", "+ ordering"),
            ),
        ),
        readout = "${plain.nodes} → ${pruned.nodes} nodes, same values",
    )
    frames += GsFrame(
        status = "Pruning works only when a good move is examined early, so the order matters. Trying immediate wins " +
            "and blocks first drops the search to ${orderedCount.nodes} nodes — ${"%.0f".format(100f - 100f * orderedCount.nodes / plain.nodes)}% " +
            "less work than plain minimax, for the same answer.",
        board = board,
        boardMarks = values.mapValues { it.value.toFloat() },
        bars = listOf(
            GsBar(
                "nodes visited",
                listOf(plain.nodes.toFloat(), pruned.nodes.toFloat(), orderedCount.nodes.toFloat()),
                GoodTone,
                listOf("minimax", "alpha-beta", "tactical order"),
            ),
        ),
        readout = "${plain.nodes} → ${pruned.nodes} → ${orderedCount.nodes} nodes",
    )
    frames += GsFrame(
        status = "And a plausible ordering can make things worse: sorting centre-first — the standard tic-tac-toe " +
            "advice — takes ${badOrder.nodes} nodes, more than not sorting at all. Ordering heuristics are guesses " +
            "about where the best move is, and a wrong guess delays the cutoff instead of causing it.",
        bars = listOf(
            GsBar(
                "nodes visited",
                listOf(plain.nodes.toFloat(), pruned.nodes.toFloat(), badOrder.nodes.toFloat(), orderedCount.nodes.toFloat()),
                MainTone,
                listOf("minimax", "unordered", "centre-first", "tactical"),
            ),
        ),
        readout = "centre-first ${badOrder.nodes} vs unordered ${pruned.nodes} vs tactical ${orderedCount.nodes}",
    )
    return frames
}

// ── MCTS ─────────────────────────────────────────────────────────────────────

private class MctsNode(val board: IntArray, val player: Int, val move: Int, val parent: MctsNode?) {
    val children = mutableListOf<MctsNode>()
    var visits = 0
    var value = 0f
    var untried = legalMoves(board).toMutableList()
    var prior = 1f
}

private class MctsResult(val visits: Map<Int, Int>, val values: Map<Int, Float>)

/**
 * UCT, or PUCT when [priors] is supplied. [evaluate] replaces the random rollout with a positional
 * evaluation — the "value network" half of AlphaGo.
 */
private fun runMcts(
    root: IntArray,
    player: Int,
    simulations: Int,
    seed: Int,
    priors: Map<Int, Float>? = null,
    evaluate: ((IntArray) -> Float)? = null,
    exploration: Float = 1.4f,
): MctsResult {
    val rng = GsRng(seed)
    val rootNode = MctsNode(root.copyOf(), player, -1, null)
    priors?.forEach { (_, _) -> }

    repeat(simulations) {
        var node = rootNode
        val board = root.copyOf()
        var current = player

        // Selection
        while (node.untried.isEmpty() && node.children.isNotEmpty()) {
            val parentVisits = node.visits.coerceAtLeast(1)
            node = node.children.maxByOrNull { child ->
                val exploit = if (child.visits == 0) 0f else child.value / child.visits
                val explore = if (priors == null) {
                    exploration * sqrt(ln(parentVisits.toFloat()) / (child.visits + 1e-6f))
                } else {
                    exploration * child.prior * sqrt(parentVisits.toFloat()) / (1f + child.visits)
                }
                (if (current == 1) exploit else -exploit) + explore
            }!!
            board[node.move] = node.player
            current = -node.player
            if (isTerminal(board)) break
        }

        // Expansion
        if (node.untried.isNotEmpty() && !isTerminal(board)) {
            val move = node.untried.removeAt(rng.nextInt(node.untried.size))
            board[move] = current
            val child = MctsNode(board.copyOf(), current, move, node)
            child.prior = priors?.get(move) ?: 1f
            node.children += child
            node = child
            current = -current
        }

        // Evaluation: a random playout, or a positional evaluation if one was supplied.
        var outcome: Float
        if (evaluate != null) {
            outcome = if (isTerminal(board)) winnerOf(board).toFloat() else evaluate(board)
        } else {
            val playout = board.copyOf()
            var mover = current
            while (!isTerminal(playout)) {
                val moves = legalMoves(playout)
                playout[moves[rng.nextInt(moves.size)]] = mover
                mover = -mover
            }
            outcome = winnerOf(playout).toFloat()
        }

        // Backup
        var walker: MctsNode? = node
        while (walker != null) {
            walker.visits++
            walker.value += outcome
            walker = walker.parent
        }
    }

    return MctsResult(
        rootNode.children.associate { it.move to it.visits },
        rootNode.children.associate { it.move to if (it.visits == 0) 0f else it.value / it.visits },
    )
}

private fun mctsFrames(): List<GsFrame> {
    val board = startPosition()
    val truth = rootValues(board.copyOf(), 1)
    val best = truth.maxByOrNull { it.value }!!.key
    val budgets = listOf(30, 120, 600)
    val runs = budgets.map { runMcts(board, 1, it, seed = 47) }
    val moves = truth.keys.sorted()
    val frames = mutableListOf<GsFrame>()

    frames += GsFrame(
        status = "MCTS never enumerates the tree. It plays random games from the current position and keeps score, " +
            "spending more of its next games on the moves that have been doing well.",
        board = board,
    )
    runs.forEachIndexed { index, run ->
        val share = run.visits[best]?.toFloat()?.div(budgets[index]) ?: 0f
        frames += GsFrame(
            status = "After ${budgets[index]} simulations the visit counts are " +
                moves.joinToString(", ") { "sq $it: ${run.visits[it] ?: 0}" } +
                ". ${"%.0f".format(share * 100)}% of the search went to square $best, which is the move minimax " +
                "proves best.",
            board = board,
            boardMarks = run.visits.mapValues { it.value.toFloat() },
            bars = listOf(GsBar("visits per move", moves.map { (run.visits[it] ?: 0).toFloat() }, MainTone, moves.map { "sq $it" })),
            readout = "${"%.0f".format(share * 100)}% of simulations on the best move",
        )
    }
    frames += GsFrame(
        status = "The selection rule is what makes that happen: UCT picks the child maximising average value plus " +
            "√(ln N / n), so a move that has been ignored eventually gets tried anyway. Exploitation and exploration, " +
            "in one line, with no evaluation function anywhere in the algorithm.",
        board = board,
        bars = listOf(
            GsBar("visit share, 600 sims", moves.map { (runs.last().visits[it] ?: 0).toFloat() }, GoodTone, moves.map { "sq $it" }),
            GsBar("true minimax value", moves.map { truth.getValue(it).toFloat() }, PlainTone, moves.map { "sq $it" }),
        ),
    )
    return frames
}

// ── AlphaGo ──────────────────────────────────────────────────────────────────

/** A crude positional evaluation, standing in for AlphaGo's value network. */
private fun positionalValue(board: IntArray): Float {
    var score = 0f
    LINES.forEach { line ->
        val values = line.map { board[it] }
        val x = values.count { it == 1 }
        val o = values.count { it == -1 }
        if (o == 0) score += x * x * 0.05f
        if (x == 0) score -= o * o * 0.05f
    }
    return score.coerceIn(-0.9f, 0.9f)
}

private fun alphaGoFrames(): List<GsFrame> {
    val board = startPosition()
    val truth = rootValues(board.copyOf(), 1)
    val best = truth.maxByOrNull { it.value }!!.key
    val moves = truth.keys.sorted()

    // A policy prior that concentrates on the centre-ish squares, the way a trained policy net does.
    val priors = moves.associateWith { if (it == best) 0.45f else 0.55f / (moves.size - 1) }

    val plain = runMcts(board, 1, 120, seed = 51)
    val withValue = runMcts(board, 1, 120, seed = 51, evaluate = ::positionalValue)
    val withBoth = runMcts(board, 1, 120, seed = 51, priors = priors, evaluate = ::positionalValue)
    val frames = mutableListOf<GsFrame>()

    frames += GsFrame(
        status = "AlphaGo is MCTS with two networks bolted on. Start from plain MCTS at a 120-simulation budget: " +
            "${"%.0f".format(100f * (plain.visits[best] ?: 0) / 120)}% of the search lands on the best move.",
        board = board,
        bars = listOf(GsBar("visits, plain MCTS", moves.map { (plain.visits[it] ?: 0).toFloat() }, PlainTone, moves.map { "sq $it" })),
        readout = "${"%.0f".format(100f * (plain.visits[best] ?: 0) / 120)}% on the best move",
    )
    frames += GsFrame(
        status = "The value network replaces the random playout: evaluate the position directly instead of finishing " +
            "the game at random. Here it barely moves the needle — ${"%.0f".format(100f * (withValue.visits[best] ?: 0) / 120)}% " +
            "against ${"%.0f".format(100f * (plain.visits[best] ?: 0) / 120)}% — because on a 3×3 board a random " +
            "playout is already a decent estimate. On a full-size board it is not, and this is where most of the " +
            "strength comes from.",
        board = board,
        bars = listOf(
            GsBar("plain MCTS", moves.map { (plain.visits[it] ?: 0).toFloat() }, PlainTone, moves.map { "sq $it" }),
            GsBar("+ value network", moves.map { (withValue.visits[it] ?: 0).toFloat() }, MainTone, moves.map { "sq $it" }),
        ),
    )
    frames += GsFrame(
        status = "The policy network shapes where the search looks in the first place: PUCT weights each child's " +
            "exploration term by its prior, so plausible moves get tried early and implausible ones are not expanded " +
            "at all. With both networks, ${"%.0f".format(100f * (withBoth.visits[best] ?: 0) / 120)}% of the budget " +
            "goes to the right move.",
        board = board,
        boardMarks = withBoth.visits.mapValues { it.value.toFloat() },
        bars = listOf(
            GsBar("prior π(a)", moves.map { priors.getValue(it) }, DeepTone, moves.map { "sq $it" }),
            GsBar("+ both networks", moves.map { (withBoth.visits[it] ?: 0).toFloat() }, GoodTone, moves.map { "sq $it" }),
        ),
        readout = "${"%.0f".format(100f * (withBoth.visits[best] ?: 0) / 120)}% on the best move",
    )
    frames += GsFrame(
        status = "On a 3×3 board this is a curiosity; on a 19×19 board it is the entire game. Go has ~250 legal moves " +
            "per position and games hundreds of moves long — no tree search reaches useful depth without a policy to " +
            "narrow the branching and a value function to stop the descent early.",
        bars = listOf(
            GsBar(
                "share of search on the best move",
                listOf(
                    100f * (plain.visits[best] ?: 0) / 120,
                    100f * (withValue.visits[best] ?: 0) / 120,
                    100f * (withBoth.visits[best] ?: 0) / 120,
                ),
                GoodTone,
                listOf("MCTS", "+value", "+policy"),
            ),
        ),
    )
    return frames
}

// ── AlphaZero ────────────────────────────────────────────────────────────────

private fun alphaZeroFrames(): List<GsFrame> {
    val board = startPosition()
    val truth = rootValues(board.copyOf(), 1)
    val best = truth.maxByOrNull { it.value }!!.key
    val moves = truth.keys.sorted()
    // A mediocre prior, as an early-training network would produce: slightly wrong, fairly flat.
    val prior = moves.associateWith { if (it == best) 0.22f else 0.78f / (moves.size - 1) }
    val search = runMcts(board, 1, 400, seed = 63, priors = prior, evaluate = ::positionalValue)
    val total = search.visits.values.sum().toFloat()
    val improved = moves.associateWith { (search.visits[it] ?: 0) / total }
    val frames = mutableListOf<GsFrame>()

    frames += GsFrame(
        status = "AlphaZero drops the human games AlphaGo learned from and keeps only the loop. Start with a network " +
            "that knows nothing useful: this prior puts ${"%.0f".format(prior.getValue(best) * 100)}% on the move that " +
            "happens to be best, barely above the others.",
        board = board,
        bars = listOf(GsBar("network prior π", moves.map { prior.getValue(it) }, PlainTone, moves.map { "sq $it" })),
    )
    frames += GsFrame(
        status = "Run 400 simulations of PUCT guided by that prior. The visit distribution comes out at " +
            "${"%.0f".format(improved.getValue(best) * 100)}% on the best move — search has taken a mediocre policy " +
            "and produced a better one.",
        board = board,
        boardMarks = search.visits.mapValues { it.value.toFloat() },
        bars = listOf(
            GsBar("prior π", moves.map { prior.getValue(it) }, PlainTone, moves.map { "sq $it" }),
            GsBar("visit distribution after search", moves.map { improved.getValue(it) }, GoodTone, moves.map { "sq $it" }),
        ),
        readout = "${"%.0f".format(prior.getValue(best) * 100)}% → ${"%.0f".format(improved.getValue(best) * 100)}% on the best move",
    )
    frames += GsFrame(
        status = "That improvement is the training signal: the network is trained to predict the *search's* " +
            "distribution, not a human's move. Search is a policy improvement operator, and the network distils its " +
            "output back into the priors that guide the next search.",
        bars = listOf(
            GsBar("training target = visit counts", moves.map { improved.getValue(it) }, DeepTone, moves.map { "sq $it" }),
        ),
    )
    frames += GsFrame(
        status = "Iterate: play games against yourself using search, train on the results, search again with the " +
            "improved network. No opening book, no human games, no hand-written evaluation — the same loop learned " +
            "chess, shogi and Go.",
        board = board,
        boardMarks = search.visits.mapValues { it.value.toFloat() },
    )
    return frames
}

// ── MuZero ───────────────────────────────────────────────────────────────────

private fun muZeroFrames(): List<GsFrame> {
    val board = startPosition()
    val truth = rootValues(board.copyOf(), 1)
    val best = truth.maxByOrNull { it.value }!!.key
    val moves = truth.keys.sorted()

    // Planning quality as a function of how wrong the learned model is: with probability p the
    // model's evaluation of a leaf is corrupted, which is what an imperfect learned dynamics /
    // reward model does to a search.
    val errorRates = listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f)
    val accuracy = errorRates.map { rate ->
        val trials = 30
        val hits = (0 until trials).count { trial ->
            val rng = GsRng(900 + trial * 13)
            // Corrupt the evaluation of every leaf, terminal positions included — a learned model
            // gets the game's outcomes wrong too, not just its heuristics.
            val result = runMcts(board, 1, 30, seed = 700 + trial * 7, evaluate = { position ->
                if (rng.next() < rate) rng.next() * 2f - 1f
                else if (isTerminal(position)) winnerOf(position).toFloat() else positionalValue(position)
            })
            result.visits.maxByOrNull { it.value }?.key == best
        }
        hits.toFloat() / trials
    }

    val frames = mutableListOf<GsFrame>()
    frames += GsFrame(
        status = "AlphaZero needs the rules: to search, it has to be able to apply a move and see the resulting " +
            "position. MuZero drops that requirement and learns a model instead — one that only has to predict " +
            "reward, value and policy, not reconstruct the actual state.",
        board = board,
    )
    frames += GsFrame(
        status = "Corrupt a fraction of the model's evaluations and re-run the search 30 times at each level: " +
            "${errorRates.indices.joinToString(", ") { "${"%.0f".format(errorRates[it] * 100)}% → ${"%.0f".format(accuracy[it] * 100)}%" }} " +
            "of runs still pick the best move. Search is remarkably tolerant — averaging over many rollouts cancels " +
            "a lot of model noise — but past a point the plan is only as good as the model it was made in.",
        plot = GsPlot(
            "chance the search picks the best move",
            listOf(GsCurve("accuracy", accuracy, MainTone)),
            0f..1.1f,
            "model error 0% · 20% · 40% · 60% · 80%",
        ),
        readout = "${"%.0f".format(accuracy.first() * 100)}% at a perfect model → ${"%.0f".format(accuracy.last() * 100)}% at 80% corruption",
    )
    frames += GsFrame(
        status = "MuZero's answer to that is what it chooses to model. It never predicts the next board — only the " +
            "quantities the search consumes. A model that cannot draw the position but ranks moves correctly is " +
            "enough, and it is far easier to learn.",
        bars = listOf(
            GsBar(
                "what each method needs",
                listOf(3f, 2f, 1f),
                DeepTone,
                listOf("AlphaZero: rules", "World model: pixels", "MuZero: value/policy"),
            ),
        ),
    )
    frames += GsFrame(
        status = "That is why the same algorithm covers board games and Atari: in Go the rules were available and " +
            "unused, and in Atari there were none to have. The search is identical; only the model it searches in " +
            "changed.",
        board = board,
        boardMarks = truth.mapValues { it.value.toFloat() },
    )
    return frames
}

// ── Self-play ────────────────────────────────────────────────────────────────

/**
 * Fictitious play on rock-paper-scissors: each side best-responds to the opponent's empirical
 * distribution. Self-play at its most transparent — including the cycling that makes it unstable.
 */
private fun selfPlayFrames(): List<GsFrame> {
    val counts = intArrayOf(1, 0, 0)
    val history = mutableListOf<FloatArray>()
    val exploitCurrent = mutableListOf<Float>()
    val exploitAverage = mutableListOf<Float>()
    // payoff[a][b] = result for player playing a against b
    val payoff = arrayOf(
        floatArrayOf(0f, -1f, 1f),
        floatArrayOf(1f, 0f, -1f),
        floatArrayOf(-1f, 1f, 0f),
    )

    repeat(40) {
        val total = counts.sum().toFloat()
        val average = FloatArray(3) { counts[it] / total }
        history += average

        // Best response to the running average, and how much the *current* pure strategy loses to
        // its own best response — the standard measure of exploitability.
        val best = (0..2).maxByOrNull { a -> (0..2).sumOf { (payoff[a][it] * average[it]).toDouble() } }!!
        counts[best]++

        val currentPure = FloatArray(3) { if (it == best) 1f else 0f }
        exploitCurrent += (0..2).maxOf { a -> (0..2).sumOf { (payoff[a][it] * currentPure[it]).toDouble() }.toFloat() }
        exploitAverage += (0..2).maxOf { a -> (0..2).sumOf { (payoff[a][it] * average[it]).toDouble() }.toFloat() }
    }

    val finalAverage = history.last()
    val frames = mutableListOf<GsFrame>()

    frames += GsFrame(
        status = "Self-play in its simplest form: rock-paper-scissors, where each side repeatedly best-responds to " +
            "what the opponent has been doing. There is no external opponent to measure against — the curriculum is " +
            "entirely internal.",
        bars = listOf(GsBar("play frequency after 40 rounds", finalAverage.toList(), MainTone, listOf("rock", "paper", "scissors"))),
    )
    frames += GsFrame(
        status = "The current strategy never stops being exploitable: best-responding to rock invites paper, which " +
            "invites scissors, which invites rock. Exploitability of the latest pure strategy stays at " +
            "${"%.1f".format(exploitCurrent.last())} — the maximum — forever.",
        plot = GsPlot(
            "how much the strategy loses to its own best response",
            listOf(GsCurve("latest strategy", exploitCurrent, BadTone)),
            -0.1f..1.2f, "round",
        ),
        readout = "cycling, not converging",
    )
    frames += GsFrame(
        status = "The *average* of everything played does converge: its exploitability falls to " +
            "${"%.2f".format(exploitAverage.last())} and keeps dropping towards the Nash equilibrium of one-third each. " +
            "The learning is real — it is just not in the latest iterate.",
        plot = GsPlot(
            "exploitability",
            listOf(
                GsCurve("latest strategy", exploitCurrent, BadTone),
                GsCurve("average strategy", exploitAverage, GoodTone),
            ),
            -0.1f..1.2f, "round",
        ),
        readout = "average → ${finalAverage.joinToString(", ") { "%.2f".format(it) }} (Nash is 0.33 each)",
    )
    frames += GsFrame(
        status = "This is why serious self-play systems never train against only the newest opponent: AlphaStar kept " +
            "a league of past agents, and TD-Gammon and AlphaZero average over huge pools of self-play games. Beating " +
            "your latest self is not the same as getting better.",
        bars = listOf(
            GsBar("average strategy", finalAverage.toList(), GoodTone, listOf("rock", "paper", "scissors")),
        ),
    )
    return frames
}

// ── Model-based RL on the chain ──────────────────────────────────────────────

private const val GS_STATES = 6
private const val GS_GOAL = GS_STATES - 1
private const val GS_GAMMA = 0.95f

private class LearnedModel(val reward: Array<FloatArray>, val next: Array<IntArray>, val counts: Array<IntArray>)

/** Estimate a tabular model from [transitions] real environment steps under a random policy. */
private fun learnModel(seed: Int, transitions: Int): LearnedModel {
    val rng = GsRng(seed)
    val reward = Array(GS_STATES) { FloatArray(2) }
    val next = Array(GS_STATES) { IntArray(2) { -1 } }
    val counts = Array(GS_STATES) { IntArray(2) }
    var state = 0
    repeat(transitions) {
        val action = rng.nextInt(2)
        val n = if (action == 1) minOf(state + 1, GS_GOAL) else maxOf(state - 1, 0)
        val r = if (n == GS_GOAL) 1f else 0f
        reward[state][action] = r
        next[state][action] = n
        counts[state][action]++
        state = if (n == GS_GOAL) 0 else n
    }
    return LearnedModel(reward, next, counts)
}

/** Fraction of the model's (state, action) entries that were ever observed. */
private fun modelCoverage(model: LearnedModel): Float {
    val total = GS_GOAL * 2
    val known = (0 until GS_GOAL).sumOf { s -> (0..1).count { model.counts[s][it] > 0 } }
    return known.toFloat() / total
}

private fun worldModelFrames(): List<GsFrame> {
    val budgets = listOf(8, 20, 40, 120)
    val models = budgets.map { learnModel(seed = 5, transitions = it) }
    val coverage = models.map { modelCoverage(it) }

    // Compounding error: roll the model forward k steps from the start and compare the state it
    // predicts against the state the real environment reaches.
    val full = learnModel(seed = 5, transitions = 120)
    val sparse = learnModel(seed = 9, transitions = 10)
    fun rolloutError(model: LearnedModel, horizon: Int): List<Float> {
        var predicted = 0
        var actual = 0
        return (1..horizon).map {
            val action = 1
            predicted = if (model.counts[predicted][action] > 0) model.next[predicted][action] else predicted
            actual = minOf(actual + 1, GS_GOAL)
            abs(predicted - actual).toFloat()
        }
    }

    val frames = mutableListOf<GsFrame>()
    frames += GsFrame(
        status = "A world model is a learned stand-in for the environment: give it a state and an action, and it " +
            "predicts what happens next. Here it is a table, learned from a limited number of real transitions.",
        bars = listOf(GsBar("fraction of the model that was ever observed", coverage, MainTone, budgets.map { "$it steps" })),
        readout = "${budgets.first()} real steps cover ${"%.0f".format(coverage.first() * 100)}% of it",
    )
    frames += GsFrame(
        status = "The value of a model is that it is cheap to query: an agent can take thousands of imagined steps " +
            "per real one. The danger is that imagined steps are only as good as the model, and errors compound as " +
            "the rollout gets longer.",
        plot = GsPlot(
            "state prediction error over a rollout",
            listOf(
                GsCurve("model from 120 steps", rolloutError(full, 8), GoodTone),
                GsCurve("model from 12 steps", rolloutError(sparse, 8), BadTone),
            ),
            0f..5f, "imagined step",
        ),
        readout = "a sparse model is wrong by ${"%.0f".format(rolloutError(sparse, 8).last())} states after 8 steps",
    )
    frames += GsFrame(
        status = "That compounding is the central difficulty of model-based RL, and it explains the architecture " +
            "choices: latent state spaces that only keep what matters for prediction, ensembles that disagree where " +
            "the data was thin, and short rollouts.",
        bars = listOf(
            GsBar("prediction error by imagined step", rolloutError(sparse, 8), BadTone, (1..8).map { "$it" }),
        ),
    )
    frames += GsFrame(
        status = "Coverage is what buys accuracy, and it comes from real interaction: ${budgets.last()} real steps " +
            "get this model to ${"%.0f".format(coverage.last() * 100)}% coverage. The model does not remove the need " +
            "for data — it multiplies what each datum is worth.",
        bars = listOf(GsBar("model coverage", coverage, MainTone, budgets.map { "$it steps" })),
    )
    return frames
}

/** Dyna-style agent: real steps plus [planningSteps] imagined updates from the learned model. */
private fun dynaRun(seed: Int, planningSteps: Int, episodes: Int = 40): Pair<List<Float>, Int> {
    val rng = GsRng(seed)
    val q = Array(GS_STATES) { FloatArray(2) }
    val model = LearnedModel(Array(GS_STATES) { FloatArray(2) }, Array(GS_STATES) { IntArray(2) { -1 } }, Array(GS_STATES) { IntArray(2) })
    val seen = mutableListOf<Pair<Int, Int>>()
    val error = mutableListOf<Float>()
    var realSteps = 0
    val optimal = (0 until GS_STATES).map { s -> Math.pow(GS_GAMMA.toDouble(), (GS_GOAL - s - 1).toDouble()).toFloat() }

    repeat(episodes) {
        var state = 0
        var steps = 0
        while (steps < 40) {
            steps++
            realSteps++
            val action = if (rng.next() < 0.3f) rng.nextInt(2) else if (q[state][1] >= q[state][0]) 1 else 0
            val next = if (action == 1) minOf(state + 1, GS_GOAL) else maxOf(state - 1, 0)
            val reward = if (next == GS_GOAL) 1f else 0f
            val done = next == GS_GOAL
            q[state][action] += 0.5f * (reward + (if (done) 0f else GS_GAMMA * maxOf(q[next][0], q[next][1])) - q[state][action])

            if (model.counts[state][action] == 0) seen += state to action
            model.reward[state][action] = reward
            model.next[state][action] = next
            model.counts[state][action]++

            repeat(planningSteps) {
                if (seen.isEmpty()) return@repeat
                val (s, a) = seen[rng.nextInt(seen.size)]
                val n = model.next[s][a]
                val r = model.reward[s][a]
                q[s][a] += 0.5f * (r + (if (n == GS_GOAL) 0f else GS_GAMMA * maxOf(q[n][0], q[n][1])) - q[s][a])
            }

            state = next
            if (done) break
        }
        error += (0 until GS_GOAL).map { abs(q[it][1] - optimal[it]) }.average().toFloat()
    }
    return error to realSteps
}

private fun dreamerFrames(): List<GsFrame> {
    val modelFree = dynaRun(seed = 17, planningSteps = 0)
    val imagined = dynaRun(seed = 17, planningSteps = 20)
    fun converged(error: List<Float>) = error.indexOfFirst { it < 0.05f }.let { if (it < 0) error.size else it + 1 }
    val frames = mutableListOf<GsFrame>()

    frames += GsFrame(
        status = "Dreamer's premise: once you have a model, most learning can happen inside it. The agent acts in the " +
            "real environment only to improve the model, and improves its behaviour by training on imagined rollouts.",
        plot = GsPlot(
            "mean |Q − Q*|",
            listOf(GsCurve("model-free", modelFree.first, PlainTone)),
            0f..0.6f, "episode",
        ),
        readout = "model-free: ${converged(modelFree.first)} episodes",
    )
    frames += GsFrame(
        status = "Add 20 imagined updates per real step, drawn from the learned model, and the same environment " +
            "interaction converges in ${converged(imagined.first)} episodes instead of ${converged(modelFree.first)}. " +
            "The real steps are unchanged — only the amount of thinking per step went up.",
        plot = GsPlot(
            "mean |Q − Q*|",
            listOf(
                GsCurve("model-free", modelFree.first, PlainTone),
                GsCurve("20 imagined steps", imagined.first, GoodTone),
            ),
            0f..0.6f, "episode",
        ),
        readout = "${converged(modelFree.first)} → ${converged(imagined.first)} episodes",
    )
    frames += GsFrame(
        status = "That is the whole promise of model-based RL: sample efficiency. Where real experience is expensive " +
            "— a physical robot, a slow simulator — trading compute for interaction is exactly the trade you want.",
        bars = listOf(
            GsBar(
                "episodes to converge",
                listOf(converged(modelFree.first).toFloat(), converged(imagined.first).toFloat()),
                MainTone,
                listOf("model-free", "with imagination"),
            ),
        ),
    )
    frames += GsFrame(
        status = "Dreamer adds two things this toy cannot show: the model is latent (it predicts a compact state, " +
            "not pixels) and the policy is trained by backpropagating through the imagined rollout itself, so the " +
            "gradient flows through the model rather than around it.",
        bars = listOf(
            GsBar("imagined updates per real step", listOf(0f, 20f), DeepTone, listOf("model-free", "Dreamer-style")),
        ),
    )
    return frames
}

private fun mbpoFrames(): List<GsFrame> {
    val sparse = learnModel(seed = 9, transitions = 10)
    fun compounding(horizon: Int): List<Float> {
        var predicted = 0
        var actual = 0
        return (1..horizon).map {
            predicted = if (sparse.counts[predicted][1] > 0) sparse.next[predicted][1] else predicted
            actual = minOf(actual + 1, GS_GOAL)
            abs(predicted - actual).toFloat()
        }
    }

    val errors = compounding(10)
    val short = dynaRun(seed = 23, planningSteps = 5)
    val long = dynaRun(seed = 23, planningSteps = 40)
    fun converged(error: List<Float>) = error.indexOfFirst { it < 0.05f }.let { if (it < 0) error.size else it + 1 }
    val frames = mutableListOf<GsFrame>()

    frames += GsFrame(
        status = "MBPO's question is not whether to use a model but how far to trust it. Rolled out from the start " +
            "state, this model's error grows with every imagined step: ${errors.take(5).joinToString(", ") { "%.0f".format(it) }} " +
            "after 1–5 steps.",
        plot = GsPlot(
            "model error over an imagined rollout",
            listOf(GsCurve("error", errors, BadTone)),
            0f..5f, "imagined step",
        ),
        readout = "error compounds with rollout length",
    )
    frames += GsFrame(
        status = "So do not roll out from the start. MBPO branches short rollouts from states the agent actually " +
            "visited — the model only has to be right for a few steps from a state where it has data, which is the " +
            "regime where it is right.",
        bars = listOf(
            GsBar("error by rollout length", errors.take(6), BadTone, (1..6).map { "$it" }),
        ),
    )
    frames += GsFrame(
        status = "Planning volume is a different knob, and on a model this small more of it keeps helping: 5 imagined " +
            "updates per step converge in ${converged(short.first)} episodes, 40 in ${converged(long.first)}. There " +
            "is nothing to overfit to here — a six-state table is learned almost exactly, so extra planning is free " +
            "accuracy.",
        plot = GsPlot(
            "mean |Q − Q*|",
            listOf(
                GsCurve("5 imagined updates", short.first, GoodTone),
                GsCurve("40 imagined updates", long.first, MainTone),
            ),
            0f..0.6f, "episode",
        ),
        readout = "${converged(short.first)} vs ${converged(long.first)} episodes",
    )
    frames += GsFrame(
        status = "That is exactly what stops being true at scale, and it is the gap MBPO addresses: with a learned " +
            "neural model the imagined data is wrong in ways the previous frame shows, so the length of each rollout " +
            "matters far more than the number of them.",
        bars = listOf(
            GsBar("error by rollout length", errors.take(6), BadTone, (1..6).map { "$it" }),
        ),
    )
    frames += GsFrame(
        status = "MBPO's actual contribution is making that trade explicit: a model ensemble whose disagreement " +
            "flags where the model is unreliable, and a rollout length chosen so the imagined data stays inside the " +
            "region the ensemble agrees on.",
        bars = listOf(
            GsBar(
                "episodes to converge",
                listOf(converged(short.first).toFloat(), converged(long.first).toFloat()),
                MainTone,
                listOf("k = 5", "k = 40"),
            ),
        ),
    )
    return frames
}

// ── Config ───────────────────────────────────────────────────────────────────

private val searchLegend = listOf(
    PlainTone to "Baseline",
    GoodTone to "Improved",
    BadTone to "Cost / error",
)

// ── Interview-prep pattern guide ─────────────────────────────────────────────
// Stone game: dp[i][j] is the score *difference* the mover can force on piles i..j. Dropping the turn
// flag in favour of a difference is what collapses two recurrences into one.
private val stonePiles = listOf(3, 9, 1, 2)

private fun gameTheoryDpFrames(): List<GsFrame> {
    val n = stonePiles.size
    val dp = Array(n) { IntArray(n) }
    val frames = mutableListOf<GsFrame>()

    frames += GsFrame(
        status = "Piles ${stonePiles.joinToString(", ")}, players alternate taking from either end, both play " +
            "optimally. Modelling the opponent as adversarial rather than passive is the entire difference from " +
            "ordinary DP — and dp[i][j] holds the score *difference* the mover can force, not their score.",
        bars = listOf(
            GsBar("piles", stonePiles.map { it.toFloat() }, PlainTone, stonePiles.map { it.toString() }),
        ),
        readout = "total on the table = ${stonePiles.sum()}",
    )

    for (i in 0 until n) dp[i][i] = stonePiles[i]
    frames += GsFrame(
        status = "Single piles are the base case: with one pile left the mover takes it, so the difference is the " +
            "pile itself. Every longer range will be built from shorter ones, so ranges are filled by length.",
        bars = listOf(
            GsBar("length 1", (0 until n).map { dp[it][it].toFloat() }, MainTone, (0 until n).map { "$it..$it" }),
        ),
        readout = "base case filled",
    )

    for (len in 2..n) {
        val ranges = (0..n - len).map { it to it + len - 1 }
        ranges.forEach { (i, j) ->
            val takeLeft = stonePiles[i] - dp[i + 1][j]
            val takeRight = stonePiles[j] - dp[i][j - 1]
            dp[i][j] = maxOf(takeLeft, takeRight)
        }
        val detail = ranges.joinToString("; ") { (i, j) ->
            "[$i..$j]: take ${stonePiles[i]} → ${stonePiles[i]} − ${dp[i + 1][j]} = ${stonePiles[i] - dp[i + 1][j]}, " +
                "take ${stonePiles[j]} → ${stonePiles[j]} − ${dp[i][j - 1]} = ${stonePiles[j] - dp[i][j - 1]}"
        }
        frames += GsFrame(
            status = "Length $len. Each entry is max over the two ends of (gain − best(rest)) — the subtraction *is* " +
                "the opponent: whatever they can force on the remainder is a loss to the mover. $detail.",
            bars = listOf(
                GsBar(
                    "length $len",
                    ranges.map { (i, j) -> dp[i][j].toFloat() },
                    MainTone,
                    ranges.map { (i, j) -> "$i..$j" },
                ),
            ),
            readout = ranges.joinToString(" · ") { (i, j) -> "dp[$i][$j] = ${dp[i][j]}" },
        )
    }

    val answer = dp[0][n - 1]
    frames += GsFrame(
        status = "dp[0][${n - 1}] = $answer > 0, so the first player wins by $answer. Note the trap: taking the " +
            "larger visible end first (${stonePiles.first()} vs ${stonePiles.last()} — here the greedy grab is the " +
            "3) loses, because it hands over the 9. O(n²) states, O(1) per state, and no turn flag anywhere.",
        bars = listOf(
            GsBar("piles", stonePiles.map { it.toFloat() }, PlainTone, stonePiles.map { it.toString() }),
            GsBar(
                "first-player margin",
                listOf(answer.toFloat()),
                if (answer > 0) GoodTone else BadTone,
                listOf("dp[0][${n - 1}]"),
            ),
        ),
        readout = "first player wins by $answer",
    )
    return frames
}

private val gsConfigs = mapOf(
    "game_theory_dp_pattern" to GsConfig(
        intro = "The stone game filled by range length. Bars are dp[i][j] — the score difference the mover can " +
            "force — so a negative bar is a range you do not want to be handed.",
        legend = listOf(
            MainTone to "dp[i][j]",
            GoodTone to "Mover ahead",
            BadTone to "Mover behind",
        ),
        build = ::gameTheoryDpFrames,
    ),
    "minimax" to GsConfig(
        intro = "A real tic-tac-toe position searched three ways — plain minimax, alpha-beta, and alpha-beta with " +
            "move ordering — with the node counts measured, not quoted.",
        legend = searchLegend,
        build = ::minimaxFrames,
    ),
    "mcts" to GsConfig(
        intro = "MCTS actually playing random games from the same position, at three simulation budgets. The visit " +
            "counts are what the search produced.",
        legend = searchLegend,
        build = ::mctsFrames,
    ),
    "alphago" to GsConfig(
        intro = "The same search with a value function replacing the rollout, then a policy prior narrowing the " +
            "branching — measured as the share of the budget that reaches the best move.",
        legend = searchLegend,
        build = ::alphaGoFrames,
    ),
    "alphazero" to GsConfig(
        intro = "Search as a policy improvement operator: a deliberately mediocre prior in, a sharper visit " +
            "distribution out, and that distribution as the training target.",
        legend = searchLegend,
        build = ::alphaZeroFrames,
    ),
    "muzero" to GsConfig(
        intro = "What happens to planning when the model is wrong, measured by corrupting the evaluation at four " +
            "error rates — and what MuZero chooses to model instead.",
        legend = searchLegend,
        build = ::muZeroFrames,
    ),
    "self_play" to GsConfig(
        intro = "Fictitious play on rock-paper-scissors: the latest strategy cycles forever while the average " +
            "converges to Nash. Both curves are measured exploitability.",
        legend = searchLegend,
        build = ::selfPlayFrames,
    ),
    "world_models" to GsConfig(
        intro = "A tabular model learned from a handful of real transitions, and the rate at which its errors " +
            "compound over an imagined rollout.",
        legend = searchLegend,
        build = ::worldModelFrames,
    ),
    "dreamer" to GsConfig(
        intro = "Learning from imagined rollouts against learning from real ones only — same environment " +
            "interaction, different amount of thinking per step.",
        legend = searchLegend,
        build = ::dreamerFrames,
    ),
    "mbpo" to GsConfig(
        intro = "How far to trust a learned model: compounding error by rollout length, and what more planning " +
            "actually buys.",
        legend = searchLegend,
        build = ::mbpoFrames,
    ),
)

private fun gsConfigFor(topicId: String): GsConfig = gsConfigs[topicId] ?: gsConfigs.getValue("minimax")

// Exposed so PatternCoverageTest can tell a topic that configured this widget from one that only
// inherits the fallback above.
internal val gameSearchTopicIds: Set<String> get() = gsConfigs.keys

/** Boards are 9 squares, bar captions have to line up with their bars, and plots have to stay in range. */
internal fun gameSearchFrameCount(topicId: String): Int {
    val frames = gsConfigFor(topicId).build()
    frames.forEachIndexed { index, frame ->
        require(frame.status.isNotBlank()) { "$topicId frame $index has no status line" }
        frame.board?.let { require(it.size == 9) { "$topicId frame $index has a ${it.size}-square board" } }
        frame.boardMarks.keys.forEach {
            require(it in 0..8) { "$topicId frame $index marks square $it" }
        }
        frame.bars.forEach { bar ->
            require(bar.values.isNotEmpty()) { "$topicId frame $index has an empty bar row \"${bar.label}\"" }
            require(bar.captions.isEmpty() || bar.captions.size == bar.values.size) {
                "$topicId frame $index bar \"${bar.label}\" has ${bar.captions.size} captions for ${bar.values.size} bars"
            }
        }
        frame.plot?.curves?.forEach { curve ->
            require(curve.values.isNotEmpty()) { "$topicId frame $index plots an empty curve \"${curve.label}\"" }
        }
    }
    return frames.size
}

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun GameSearchSection(topicId: String) {
    val config = remember(topicId) { gsConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 1200f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                config.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            frame.board?.let { BoardView(it, frame.boardMarks, modifier = Modifier.padding(top = 12.dp)) }
            frame.plot?.let { GsPlotCanvas(it, modifier = Modifier.padding(top = 12.dp)) }
            frame.bars.forEach { bar -> GsBars(bar, modifier = Modifier.padding(top = 12.dp)) }

            frame.readout?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Text(
                frame.status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                config.legend.forEach { (color, label) -> GsLegend(color, label) }
            }

            PlaybackTransport(playback)
        }
    }
}

@Composable
private fun GsLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun BoardView(board: IntArray, marks: Map<Int, Float>, modifier: Modifier = Modifier) {
    val peak = marks.values.maxOfOrNull { abs(it) }?.coerceAtLeast(0.001f) ?: 1f
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.62f)
                .aspectRatio(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (row in 0 until 3) {
                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (column in 0 until 3) {
                        val index = row * 3 + column
                        val mark = marks[index]
                        val background = when {
                            mark != null -> MainTone.copy(alpha = 0.15f + 0.6f * (abs(mark) / peak))
                            board[index] != 0 -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(background, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    when (board[index]) {
                                        1 -> "X"
                                        -1 -> "O"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (board[index] == 0 && mark != null) {
                                    Text(
                                        if (mark == mark.toInt().toFloat()) mark.toInt().toString() else "%.1f".format(mark),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GsPlotCanvas(plot: GsPlot, modifier: Modifier = Modifier) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val span = plot.curves.maxOfOrNull { it.values.size } ?: 1

    Column(modifier = modifier.fillMaxWidth()) {
        Text(plot.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(8.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                fun place(i: Int, value: Float): Offset {
                    val x = if (span <= 1) size.width / 2f else i.toFloat() / (span - 1) * size.width
                    val ny = (value - plot.yRange.start) / (plot.yRange.endInclusive - plot.yRange.start)
                    return Offset(x, size.height - ny.coerceIn(-0.05f, 1.05f) * size.height)
                }
                if (0f in plot.yRange) {
                    val zero = place(0, 0f)
                    drawLine(axisColor, Offset(0f, zero.y), Offset(size.width, zero.y), strokeWidth = 1.5f)
                }
                plot.curves.forEach { curve ->
                    curve.values.forEachIndexed { i, value ->
                        drawCircle(curve.color, radius = 5f, center = place(i, value))
                        if (i == 0) return@forEachIndexed
                        drawLine(
                            color = curve.color,
                            start = place(i - 1, curve.values[i - 1]),
                            end = place(i, value),
                            strokeWidth = 4f,
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            plot.curves.forEach { curve ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(curve.color, CircleShape))
                    Text(
                        curve.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 3.dp),
                    )
                }
            }
            Text(
                plot.xLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun GsBars(bar: GsBar, modifier: Modifier = Modifier) {
    val axis = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val peak = bar.values.maxOfOrNull { abs(it) }?.coerceAtLeast(0.001f) ?: 1f

    Column(modifier = modifier.fillMaxWidth()) {
        Text(bar.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(modifier = Modifier.fillMaxWidth().height(46.dp).padding(top = 4.dp)) {
            val slot = size.width / bar.values.size
            val mid = size.height / 2f
            drawLine(axis, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.5f)
            bar.values.forEachIndexed { index, value ->
                val height = (abs(value) / peak) * (size.height / 2f - 2f)
                drawRoundRect(
                    color = if (value >= 0f) bar.color else BadTone,
                    topLeft = Offset(index * slot + slot * 0.2f, if (value >= 0f) mid - height else mid),
                    size = Size(slot * 0.6f, height.coerceAtLeast(1.5f)),
                    cornerRadius = CornerRadius(3f, 3f),
                )
            }
        }
        if (bar.captions.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                bar.captions.forEach { caption ->
                    Text(
                        caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
