import SwiftUI

// Port of GameSearchSection.kt: search and model-based RL. The search labs run a real tic-tac-toe
// engine — minimax and alpha-beta count their own nodes, MCTS plays rollouts — and the model-based
// labs run on the six-state chain with a model estimated from a limited number of real transitions.

private struct GsFrame {
    let status: String
    var board: [Int]?
    var boardMarks: [Int: Float] = [:]
    var plot: FramePlot?
    var bars: [FrameBars] = []
    var readout: String?
}

private struct GsConfig {
    let intro: String
    let legend: [(Color, String)]
    let build: () -> [GsFrame]
}

private let plainTone = SimColors.grey
private let goodTone = SimColors.green
private let badTone = Color(hex: 0xEC4899)
private let mainTone = SimColors.blue
private let deepTone = Color(hex: 0x7C3AED)

private func f0(_ x: Float) -> String { fx(x, 0) }

// MARK: - Tic-tac-toe (X = 1, O = −1)

private let lines: [[Int]] = [[0, 1, 2], [3, 4, 5], [6, 7, 8], [0, 3, 6], [1, 4, 7], [2, 5, 8], [0, 4, 8], [2, 4, 6]]

private func winnerOf(_ board: [Int]) -> Int {
    for line in lines {
        let first = board[line[0]]
        if first != 0 && first == board[line[1]] && first == board[line[2]] { return first }
    }
    return 0
}

private func legalMoves(_ board: [Int]) -> [Int] { board.indices.filter { board[$0] == 0 } }

private func isTerminal(_ board: [Int]) -> Bool { winnerOf(board) != 0 || !board.contains(0) }

/// X to move with a fork available: X at 0 and 4, O at 8 and 2.
private func startPosition() -> [Int] { [1, 0, -1, 0, 1, 0, 0, 0, -1] }

private func minimax(_ board: inout [Int], _ player: Int, _ nodes: inout Int) -> Int {
    nodes += 1
    let winner = winnerOf(board)
    if winner != 0 { return winner }
    let moves = legalMoves(board)
    if moves.isEmpty { return 0 }
    var best = -player * 2
    for move in moves {
        board[move] = player
        let value = minimax(&board, -player, &nodes)
        board[move] = 0
        best = player == 1 ? max(best, value) : min(best, value)
    }
    return best
}

private enum Ordering { case none, centreFirst, tactical }

private func alphaBeta(_ board: inout [Int], _ player: Int, _ alpha: Int, _ beta: Int, _ nodes: inout Int, _ ordering: Ordering = .none) -> Int {
    nodes += 1
    let winner = winnerOf(board)
    if winner != 0 { return winner }
    var moves = legalMoves(board)
    if moves.isEmpty { return 0 }
    switch ordering {
    case .none: break
    case .centreFirst:
        // The plausible-looking heuristic: centre, then corners, then edges.
        moves = moves.sortedBy { $0 == 4 ? 0 : $0 % 2 == 0 ? 1 : 2 }
    case .tactical:
        // Immediate wins first, then moves that block one.
        let scores = Dictionary(uniqueKeysWithValues: moves.map { move -> (Int, Int) in
            board[move] = player
            let wins = winnerOf(board) == player ? 1 : 0
            board[move] = -player
            let blocks = winnerOf(board) == -player ? 1 : 0
            board[move] = 0
            return (move, 2 * wins + blocks)
        })
        moves = moves.sortedByDescending { scores[$0]! }
    }
    var a = alpha
    var b = beta
    var best = -player * 2
    for move in moves {
        board[move] = player
        let value = alphaBeta(&board, -player, a, b, &nodes, ordering)
        board[move] = 0
        if player == 1 {
            best = max(best, value)
            a = max(a, best)
        } else {
            best = min(best, value)
            b = min(b, best)
        }
        if b <= a { break }
    }
    return best
}

/// Minimax value of each legal move, in move order.
private func rootValues(_ start: [Int], _ player: Int) -> [(move: Int, value: Int)] {
    var board = start
    return legalMoves(board).map { move in
        board[move] = player
        var nodes = 0
        let value = minimax(&board, -player, &nodes)
        board[move] = 0
        return (move, value)
    }
}

/// Kotlin's `maxByOrNull { it.value }!!.key` over an insertion-ordered map.
private func bestMove(_ values: [(move: Int, value: Int)]) -> Int { values[argmaxFirst(values.map(\.value))].move }

private func sqCaps(_ moves: [Int]) -> [String] { moves.map { "sq \($0)" } }

// MARK: - Minimax

private func minimaxFrames() -> [GsFrame] {
    let board = startPosition()
    let values = rootValues(board, 1)
    let valueOf = Dictionary(uniqueKeysWithValues: values.map { ($0.move, $0.value) })
    func count(_ search: (inout [Int], inout Int) -> Void) -> Int {
        var b = board
        var nodes = 0
        search(&b, &nodes)
        return nodes
    }
    let plain = count { b, n in _ = minimax(&b, 1, &n) }
    let pruned = count { b, n in _ = alphaBeta(&b, 1, -2, 2, &n) }
    let ordered = count { b, n in _ = alphaBeta(&b, 1, -2, 2, &n, .tactical) }
    let badOrder = count { b, n in _ = alphaBeta(&b, 1, -2, 2, &n, .centreFirst) }
    let moves = values.map(\.move).sorted()
    let marks = valueOf.mapValues { Float($0) }
    let saved = { (nodes: Int) in f0(100 - 100 * Float(nodes) / Float(plain)) }

    return [
        GsFrame(status: "X to move. Minimax assumes both players are perfect: X maximises the final result, O minimises it, all the way down to positions that are already decided.",
                board: board),
        GsFrame(status: "Every legal move, scored by what perfect play leads to: " + moves.map { "square \($0) → \(valueOf[$0]!)" }.joined(separator: ", ") + ". +1 is a win for X, 0 a draw, −1 a loss.",
                board: board, boardMarks: marks,
                bars: [FrameBars(label: "minimax value per move", values: moves.map { Float(valueOf[$0]!) }, color: mainTone, captions: sqCaps(moves))],
                readout: "best: square \(moves[argmaxFirst(moves.map { valueOf[$0]! })]) (value \(values.map(\.value).max()!))"),
        GsFrame(status: "Reaching those numbers took \(plain) node visits — the entire game tree below this position, because plain minimax has no reason to stop early.",
                board: board, readout: "\(plain) nodes"),
        GsFrame(status: "Alpha-beta prunes any branch that cannot change the answer: once O has a reply at least as good as something X already has elsewhere, the rest of that branch is irrelevant. Same values, \(pruned) nodes — \(saved(pruned))% fewer.",
                board: board,
                bars: [FrameBars(label: "nodes visited", values: [Float(plain), Float(pruned), Float(ordered)], color: mainTone, captions: ["minimax", "alpha-beta", "+ ordering"])],
                readout: "\(plain) → \(pruned) nodes, same values"),
        GsFrame(status: "Pruning works only when a good move is examined early, so the order matters. Trying immediate wins and blocks first drops the search to \(ordered) nodes — \(saved(ordered))% less work than plain minimax, for the same answer.",
                board: board, boardMarks: marks,
                bars: [FrameBars(label: "nodes visited", values: [Float(plain), Float(pruned), Float(ordered)], color: goodTone, captions: ["minimax", "alpha-beta", "tactical order"])],
                readout: "\(plain) → \(pruned) → \(ordered) nodes"),
        GsFrame(status: "And a plausible ordering can make things worse: sorting centre-first — the standard tic-tac-toe advice — takes \(badOrder) nodes, more than not sorting at all. Ordering heuristics are guesses about where the best move is, and a wrong guess delays the cutoff instead of causing it.",
                bars: [FrameBars(label: "nodes visited", values: [Float(plain), Float(pruned), Float(badOrder), Float(ordered)], color: mainTone, captions: ["minimax", "unordered", "centre-first", "tactical"])],
                readout: "centre-first \(badOrder) vs unordered \(pruned) vs tactical \(ordered)"),
    ]
}

// MARK: - MCTS

private final class MctsNode {
    let board: [Int]
    let player: Int
    let move: Int
    weak var parent: MctsNode? // the root's children arrays own every node
    var children: [MctsNode] = []
    var visits = 0
    var value: Float = 0
    var untried: [Int]
    var prior: Float = 1

    init(board: [Int], player: Int, move: Int, parent: MctsNode?) {
        self.board = board
        self.player = player
        self.move = move
        self.parent = parent
        untried = legalMoves(board)
    }
}

/// Root children in expansion order, as Kotlin's `associate` keeps them.
private struct MctsResult {
    let order: [Int]
    let visits: [Int: Int]
    let values: [Int: Float]

    func visitsOf(_ move: Int) -> Int { visits[move] ?? 0 }

    /// `visits.maxByOrNull { it.value }?.key`: first maximum in expansion order.
    var mostVisited: Int? { order.isEmpty ? nil : order[argmaxFirst(order.map { visits[$0]! })] }
}

/// UCT, or PUCT when `priors` is supplied; `evaluate` replaces the random rollout.
private func runMcts(_ root: [Int], _ player: Int, _ simulations: Int, seed: Int,
                     priors: [Int: Float]? = nil, evaluate: (([Int]) -> Float)? = nil, exploration: Float = 1.4) -> MctsResult {
    var rng = Lcg(seed)
    let rootNode = MctsNode(board: root, player: player, move: -1, parent: nil)

    for _ in 0..<simulations {
        var node = rootNode
        var board = root
        var current = player

        // Selection
        while node.untried.isEmpty && !node.children.isEmpty {
            let parentVisits = max(node.visits, 1)
            let scores = node.children.map { child -> Float in
                let exploit: Float = child.visits == 0 ? 0 : child.value / Float(child.visits)
                let explore: Float = priors == nil
                    ? exploration * (logf(Float(parentVisits)) / (Float(child.visits) + 1e-6)).squareRoot()
                    : exploration * child.prior * Float(parentVisits).squareRoot() / (1 + Float(child.visits))
                return (current == 1 ? exploit : -exploit) + explore
            }
            node = node.children[argmaxFirst(scores)]
            board[node.move] = node.player
            current = -node.player
            if isTerminal(board) { break }
        }

        // Expansion
        if !node.untried.isEmpty && !isTerminal(board) {
            let move = node.untried.remove(at: rng.nextIntF(node.untried.count))
            board[move] = current
            let child = MctsNode(board: board, player: current, move: move, parent: node)
            child.prior = priors?[move] ?? 1
            node.children.append(child)
            node = child
            current = -current
        }

        // Evaluation: a random playout, or a positional evaluation if one was supplied.
        let outcome: Float
        if let evaluate {
            outcome = isTerminal(board) ? Float(winnerOf(board)) : evaluate(board)
        } else {
            var playout = board
            var mover = current
            while !isTerminal(playout) {
                let moves = legalMoves(playout)
                playout[moves[rng.nextIntF(moves.count)]] = mover
                mover = -mover
            }
            outcome = Float(winnerOf(playout))
        }

        // Backup
        var walker: MctsNode? = node
        while let w = walker {
            w.visits += 1
            w.value += outcome
            walker = w.parent
        }
    }
    let children = rootNode.children
    return MctsResult(
        order: children.map(\.move),
        visits: Dictionary(uniqueKeysWithValues: children.map { ($0.move, $0.visits) }),
        values: Dictionary(uniqueKeysWithValues: children.map { ($0.move, $0.visits == 0 ? 0 : $0.value / Float($0.visits)) })
    )
}

private func visitMarks(_ r: MctsResult) -> [Int: Float] { r.visits.mapValues { Float($0) } }

private func mctsFrames() -> [GsFrame] {
    let board = startPosition()
    let truth = rootValues(board, 1)
    let best = bestMove(truth)
    let budgets = [30, 120, 600]
    let runs = budgets.map { runMcts(board, 1, $0, seed: 47) }
    let moves = truth.map(\.move).sorted()
    let valueOf = Dictionary(uniqueKeysWithValues: truth.map { ($0.move, $0.value) })

    var frames = [GsFrame(status: "MCTS never enumerates the tree. It plays random games from the current position and keeps score, spending more of its next games on the moves that have been doing well.", board: board)]
    for (index, run) in runs.enumerated() {
        let share = Float(run.visitsOf(best)) / Float(budgets[index])
        frames.append(GsFrame(
            status: "After \(budgets[index]) simulations the visit counts are " + moves.map { "sq \($0): \(run.visitsOf($0))" }.joined(separator: ", ") + ". \(f0(share * 100))% of the search went to square \(best), which is the move minimax proves best.",
            board: board, boardMarks: visitMarks(run),
            bars: [FrameBars(label: "visits per move", values: moves.map { Float(run.visitsOf($0)) }, color: mainTone, captions: sqCaps(moves))],
            readout: "\(f0(share * 100))% of simulations on the best move"
        ))
    }
    frames.append(GsFrame(
        status: "The selection rule is what makes that happen: UCT picks the child maximising average value plus √(ln N / n), so a move that has been ignored eventually gets tried anyway. Exploitation and exploration, in one line, with no evaluation function anywhere in the algorithm.",
        board: board,
        bars: [
            FrameBars(label: "visit share, 600 sims", values: moves.map { Float(runs.last!.visitsOf($0)) }, color: goodTone, captions: sqCaps(moves)),
            FrameBars(label: "true minimax value", values: moves.map { Float(valueOf[$0]!) }, color: plainTone, captions: sqCaps(moves)),
        ]
    ))
    return frames
}

// MARK: - AlphaGo

/// A crude positional evaluation, standing in for AlphaGo's value network.
private func positionalValue(_ board: [Int]) -> Float {
    var score: Float = 0
    for line in lines {
        let values = line.map { board[$0] }
        let x = Float(values.filter { $0 == 1 }.count)
        let o = Float(values.filter { $0 == -1 }.count)
        if o == 0 { score += x * x * 0.05 }
        if x == 0 { score -= o * o * 0.05 }
    }
    return min(max(score, -0.9), 0.9)
}

private func alphaGoFrames() -> [GsFrame] {
    let board = startPosition()
    let truth = rootValues(board, 1)
    let best = bestMove(truth)
    let moves = truth.map(\.move).sorted()
    // A policy prior concentrated on the best move, the way a trained policy net does.
    let priors = Dictionary(uniqueKeysWithValues: moves.map { ($0, $0 == best ? Float(0.45) : 0.55 / Float(moves.count - 1)) })
    let plain = runMcts(board, 1, 120, seed: 51)
    let withValue = runMcts(board, 1, 120, seed: 51, evaluate: positionalValue)
    let withBoth = runMcts(board, 1, 120, seed: 51, priors: priors, evaluate: positionalValue)
    func pct(_ r: MctsResult) -> Float { 100 * Float(r.visitsOf(best)) / 120 }
    func visitBars(_ label: String, _ r: MctsResult, _ color: Color) -> FrameBars {
        FrameBars(label: label, values: moves.map { Float(r.visitsOf($0)) }, color: color, captions: sqCaps(moves))
    }
    return [
        GsFrame(status: "AlphaGo is MCTS with two networks bolted on. Start from plain MCTS at a 120-simulation budget: \(f0(pct(plain)))% of the search lands on the best move.",
                board: board, bars: [visitBars("visits, plain MCTS", plain, plainTone)],
                readout: "\(f0(pct(plain)))% on the best move"),
        GsFrame(status: "The value network replaces the random playout: evaluate the position directly instead of finishing the game at random. Here it barely moves the needle — \(f0(pct(withValue)))% against \(f0(pct(plain)))% — because on a 3×3 board a random playout is already a decent estimate. On a full-size board it is not, and this is where most of the strength comes from.",
                board: board, bars: [visitBars("plain MCTS", plain, plainTone), visitBars("+ value network", withValue, mainTone)]),
        GsFrame(status: "The policy network shapes where the search looks in the first place: PUCT weights each child's exploration term by its prior, so plausible moves get tried early and implausible ones are not expanded at all. With both networks, \(f0(pct(withBoth)))% of the budget goes to the right move.",
                board: board, boardMarks: visitMarks(withBoth),
                bars: [FrameBars(label: "prior π(a)", values: moves.map { priors[$0]! }, color: deepTone, captions: sqCaps(moves)), visitBars("+ both networks", withBoth, goodTone)],
                readout: "\(f0(pct(withBoth)))% on the best move"),
        GsFrame(status: "On a 3×3 board this is a curiosity; on a 19×19 board it is the entire game. Go has ~250 legal moves per position and games hundreds of moves long — no tree search reaches useful depth without a policy to narrow the branching and a value function to stop the descent early.",
                bars: [FrameBars(label: "share of search on the best move", values: [pct(plain), pct(withValue), pct(withBoth)], color: goodTone, captions: ["MCTS", "+value", "+policy"])]),
    ]
}

// MARK: - AlphaZero

private func alphaZeroFrames() -> [GsFrame] {
    let board = startPosition()
    let truth = rootValues(board, 1)
    let best = bestMove(truth)
    let moves = truth.map(\.move).sorted()
    // A mediocre prior, as an early-training network would produce.
    let prior = Dictionary(uniqueKeysWithValues: moves.map { ($0, $0 == best ? Float(0.22) : 0.78 / Float(moves.count - 1)) })
    let search = runMcts(board, 1, 400, seed: 63, priors: prior, evaluate: positionalValue)
    let total = Float(search.visits.values.reduce(0, +))
    let improved = Dictionary(uniqueKeysWithValues: moves.map { ($0, Float(search.visitsOf($0)) / total) })
    return [
        GsFrame(status: "AlphaZero drops the human games AlphaGo learned from and keeps only the loop. Start with a network that knows nothing useful: this prior puts \(f0(prior[best]! * 100))% on the move that happens to be best, barely above the others.",
                board: board, bars: [FrameBars(label: "network prior π", values: moves.map { prior[$0]! }, color: plainTone, captions: sqCaps(moves))]),
        GsFrame(status: "Run 400 simulations of PUCT guided by that prior. The visit distribution comes out at \(f0(improved[best]! * 100))% on the best move — search has taken a mediocre policy and produced a better one.",
                board: board, boardMarks: visitMarks(search),
                bars: [
                    FrameBars(label: "prior π", values: moves.map { prior[$0]! }, color: plainTone, captions: sqCaps(moves)),
                    FrameBars(label: "visit distribution after search", values: moves.map { improved[$0]! }, color: goodTone, captions: sqCaps(moves)),
                ],
                readout: "\(f0(prior[best]! * 100))% → \(f0(improved[best]! * 100))% on the best move"),
        GsFrame(status: "That improvement is the training signal: the network is trained to predict the *search's* distribution, not a human's move. Search is a policy improvement operator, and the network distils its output back into the priors that guide the next search.",
                bars: [FrameBars(label: "training target = visit counts", values: moves.map { improved[$0]! }, color: deepTone, captions: sqCaps(moves))]),
        GsFrame(status: "Iterate: play games against yourself using search, train on the results, search again with the improved network. No opening book, no human games, no hand-written evaluation — the same loop learned chess, shogi and Go.",
                board: board, boardMarks: visitMarks(search)),
    ]
}

// MARK: - MuZero

private func muZeroFrames() -> [GsFrame] {
    let board = startPosition()
    let truth = rootValues(board, 1)
    let best = bestMove(truth)
    // With probability p the model's evaluation of a leaf is corrupted, terminal positions included.
    let errorRates: [Float] = [0, 0.2, 0.4, 0.6, 0.8]
    let accuracy: [Float] = errorRates.map { rate in
        let trials = 30
        var hits = 0
        for trial in 0..<trials {
            var rng = Lcg(900 + trial * 13)
            let result = runMcts(board, 1, 30, seed: 700 + trial * 7, evaluate: { position in
                if rng.nextF() < rate { return rng.nextF() * 2 - 1 }
                return isTerminal(position) ? Float(winnerOf(position)) : positionalValue(position)
            })
            if result.mostVisited == best { hits += 1 }
        }
        return Float(hits) / Float(trials)
    }
    let pairs = errorRates.indices.map { "\(f0(errorRates[$0] * 100))% → \(f0(accuracy[$0] * 100))%" }.joined(separator: ", ")
    return [
        GsFrame(status: "AlphaZero needs the rules: to search, it has to be able to apply a move and see the resulting position. MuZero drops that requirement and learns a model instead — one that only has to predict reward, value and policy, not reconstruct the actual state.",
                board: board),
        GsFrame(status: "Corrupt a fraction of the model's evaluations and re-run the search 30 times at each level: \(pairs) of runs still pick the best move. Search is remarkably tolerant — averaging over many rollouts cancels a lot of model noise — but past a point the plan is only as good as the model it was made in.",
                plot: FramePlot(label: "chance the search picks the best move", curves: [FrameCurve(label: "accuracy", values: accuracy, color: mainTone)],
                                yRange: 0...1.1, xLabel: "model error 0% · 20% · 40% · 60% · 80%"),
                readout: "\(f0(accuracy.first! * 100))% at a perfect model → \(f0(accuracy.last! * 100))% at 80% corruption"),
        GsFrame(status: "MuZero's answer to that is what it chooses to model. It never predicts the next board — only the quantities the search consumes. A model that cannot draw the position but ranks moves correctly is enough, and it is far easier to learn.",
                bars: [FrameBars(label: "what each method needs", values: [3, 2, 1], color: deepTone, captions: ["AlphaZero: rules", "World model: pixels", "MuZero: value/policy"])]),
        GsFrame(status: "That is why the same algorithm covers board games and Atari: in Go the rules were available and unused, and in Atari there were none to have. The search is identical; only the model it searches in changed.",
                board: board, boardMarks: Dictionary(uniqueKeysWithValues: truth.map { ($0.move, Float($0.value)) })),
    ]
}

// MARK: - Self-play

/// Fictitious play on rock-paper-scissors: each side best-responds to the opponent's empirical mix.
private func selfPlayFrames() -> [GsFrame] {
    var counts = [1, 0, 0]
    var history: [[Float]] = []
    var exploitCurrent: [Float] = []
    var exploitAverage: [Float] = []
    let payoff: [[Float]] = [[0, -1, 1], [1, 0, -1], [-1, 1, 0]]
    func value(_ a: Int, _ mix: [Float]) -> Double { (0...2).reduce(0.0) { $0 + Double(payoff[a][$1] * mix[$1]) } }

    for _ in 0..<40 {
        let total = Float(counts.reduce(0, +))
        let average = counts.map { Float($0) / total }
        history.append(average)
        let best = argmaxFirst((0...2).map { value($0, average) })
        counts[best] += 1
        let currentPure = (0...2).map { $0 == best ? Float(1) : 0 }
        exploitCurrent.append((0...2).map { Float(value($0, currentPure)) }.max()!)
        exploitAverage.append((0...2).map { Float(value($0, average)) }.max()!)
    }
    let finalAverage = history.last!
    let rps = ["rock", "paper", "scissors"]
    let exploitLabel = "how much the strategy loses to its own best response"
    return [
        GsFrame(status: "Self-play in its simplest form: rock-paper-scissors, where each side repeatedly best-responds to what the opponent has been doing. There is no external opponent to measure against — the curriculum is entirely internal.",
                bars: [FrameBars(label: "play frequency after 40 rounds", values: finalAverage, color: mainTone, captions: rps)]),
        GsFrame(status: "The current strategy never stops being exploitable: best-responding to rock invites paper, which invites scissors, which invites rock. Exploitability of the latest pure strategy stays at \(fx(exploitCurrent.last!, 1)) — the maximum — forever.",
                plot: FramePlot(label: exploitLabel, curves: [FrameCurve(label: "latest strategy", values: exploitCurrent, color: badTone)], yRange: -0.1...1.2, xLabel: "round"),
                readout: "cycling, not converging"),
        GsFrame(status: "The *average* of everything played does converge: its exploitability falls to \(fx(exploitAverage.last!)) and keeps dropping towards the Nash equilibrium of one-third each. The learning is real — it is just not in the latest iterate.",
                plot: FramePlot(label: "exploitability", curves: [
                    FrameCurve(label: "latest strategy", values: exploitCurrent, color: badTone),
                    FrameCurve(label: "average strategy", values: exploitAverage, color: goodTone),
                ], yRange: -0.1...1.2, xLabel: "round"),
                readout: "average → \(finalAverage.map { fx($0) }.joined(separator: ", ")) (Nash is 0.33 each)"),
        GsFrame(status: "This is why serious self-play systems never train against only the newest opponent: AlphaStar kept a league of past agents, and TD-Gammon and AlphaZero average over huge pools of self-play games. Beating your latest self is not the same as getting better.",
                bars: [FrameBars(label: "average strategy", values: finalAverage, color: goodTone, captions: rps)]),
    ]
}

// MARK: - Model-based RL on the chain

private let gsStates = 6
private let gsGoal = gsStates - 1
private let gsGamma: Float = 0.95

private struct LearnedModel { var reward: [[Float]]; var next: [[Int]]; var counts: [[Int]] }

private func emptyModel() -> LearnedModel {
    LearnedModel(reward: [[Float]](repeating: [0, 0], count: gsStates), next: [[Int]](repeating: [-1, -1], count: gsStates), counts: [[Int]](repeating: [0, 0], count: gsStates))
}

/// A tabular model from `transitions` real steps under a random policy.
private func learnModel(seed: Int, transitions: Int) -> LearnedModel {
    var rng = Lcg(seed)
    var model = emptyModel()
    var state = 0
    for _ in 0..<transitions {
        let action = rng.nextIntF(2)
        let n = action == 1 ? min(state + 1, gsGoal) : max(state - 1, 0)
        model.reward[state][action] = n == gsGoal ? 1 : 0
        model.next[state][action] = n
        model.counts[state][action] += 1
        state = n == gsGoal ? 0 : n
    }
    return model
}

private func modelCoverage(_ model: LearnedModel) -> Float {
    let known = (0..<gsGoal).reduce(0) { acc, s in acc + (0...1).filter { model.counts[s][$0] > 0 }.count }
    return Float(known) / Float(gsGoal * 2)
}

/// Roll the model forward always moving right, and compare against the real chain.
private func rolloutError(_ model: LearnedModel, _ horizon: Int) -> [Float] {
    var predicted = 0
    var actual = 0
    return (1...horizon).map { _ in
        predicted = model.counts[predicted][1] > 0 ? model.next[predicted][1] : predicted
        actual = min(actual + 1, gsGoal)
        return Float(abs(predicted - actual))
    }
}

private func worldModelFrames() -> [GsFrame] {
    let budgets = [8, 20, 40, 120]
    let coverage = budgets.map { modelCoverage(learnModel(seed: 5, transitions: $0)) }
    let full = learnModel(seed: 5, transitions: 120)
    let sparse = learnModel(seed: 9, transitions: 10)
    let sparseError = rolloutError(sparse, 8)
    let budgetCaps = budgets.map { "\($0) steps" }
    return [
        GsFrame(status: "A world model is a learned stand-in for the environment: give it a state and an action, and it predicts what happens next. Here it is a table, learned from a limited number of real transitions.",
                bars: [FrameBars(label: "fraction of the model that was ever observed", values: coverage, color: mainTone, captions: budgetCaps)],
                readout: "\(budgets.first!) real steps cover \(f0(coverage.first! * 100))% of it"),
        GsFrame(status: "The value of a model is that it is cheap to query: an agent can take thousands of imagined steps per real one. The danger is that imagined steps are only as good as the model, and errors compound as the rollout gets longer.",
                plot: FramePlot(label: "state prediction error over a rollout", curves: [
                    FrameCurve(label: "model from 120 steps", values: rolloutError(full, 8), color: goodTone),
                    FrameCurve(label: "model from 12 steps", values: sparseError, color: badTone),
                ], yRange: 0...5, xLabel: "imagined step"),
                readout: "a sparse model is wrong by \(f0(sparseError.last!)) states after 8 steps"),
        GsFrame(status: "That compounding is the central difficulty of model-based RL, and it explains the architecture choices: latent state spaces that only keep what matters for prediction, ensembles that disagree where the data was thin, and short rollouts.",
                bars: [FrameBars(label: "prediction error by imagined step", values: sparseError, color: badTone, captions: (1...8).map { "\($0)" })]),
        GsFrame(status: "Coverage is what buys accuracy, and it comes from real interaction: \(budgets.last!) real steps get this model to \(f0(coverage.last! * 100))% coverage. The model does not remove the need for data — it multiplies what each datum is worth.",
                bars: [FrameBars(label: "model coverage", values: coverage, color: mainTone, captions: budgetCaps)]),
    ]
}

/// Dyna-style agent: real steps plus `planningSteps` imagined updates from the learned model.
private func dynaRun(seed: Int, planningSteps: Int, episodes: Int = 40) -> [Float] {
    var rng = Lcg(seed)
    var q = [[Float]](repeating: [0, 0], count: gsStates)
    var model = emptyModel()
    var seen: [(Int, Int)] = []
    var error: [Float] = []
    let optimal = (0..<gsStates).map { Float(pow(Double(gsGamma), Double(gsGoal - $0 - 1))) }

    for _ in 0..<episodes {
        var state = 0
        var steps = 0
        while steps < 40 {
            steps += 1
            let action = rng.nextF() < 0.3 ? rng.nextIntF(2) : (q[state][1] >= q[state][0] ? 1 : 0)
            let next = action == 1 ? min(state + 1, gsGoal) : max(state - 1, 0)
            let reward: Float = next == gsGoal ? 1 : 0
            let done = next == gsGoal
            q[state][action] += 0.5 * (reward + (done ? 0 : gsGamma * max(q[next][0], q[next][1])) - q[state][action])

            if model.counts[state][action] == 0 { seen.append((state, action)) }
            model.reward[state][action] = reward
            model.next[state][action] = next
            model.counts[state][action] += 1

            for _ in 0..<planningSteps where !seen.isEmpty {
                let (s, a) = seen[rng.nextIntF(seen.count)]
                let n = model.next[s][a]
                let r = model.reward[s][a]
                q[s][a] += 0.5 * (r + (n == gsGoal ? 0 : gsGamma * max(q[n][0], q[n][1])) - q[s][a])
            }
            state = next
            if done { break }
        }
        error.append(Float((0..<gsGoal).map { abs(q[$0][1] - optimal[$0]) }.average))
    }
    return error
}

private func converged(_ error: [Float]) -> Int { error.firstIndex { $0 < 0.05 }.map { $0 + 1 } ?? error.count }

private let qErrorLabel = "mean |Q − Q*|"

private func dreamerFrames() -> [GsFrame] {
    let modelFree = dynaRun(seed: 17, planningSteps: 0)
    let imagined = dynaRun(seed: 17, planningSteps: 20)
    let (cf, ci) = (converged(modelFree), converged(imagined))
    return [
        GsFrame(status: "Dreamer's premise: once you have a model, most learning can happen inside it. The agent acts in the real environment only to improve the model, and improves its behaviour by training on imagined rollouts.",
                plot: FramePlot(label: qErrorLabel, curves: [FrameCurve(label: "model-free", values: modelFree, color: plainTone)], yRange: 0...0.6, xLabel: "episode"),
                readout: "model-free: \(cf) episodes"),
        GsFrame(status: "Add 20 imagined updates per real step, drawn from the learned model, and the same environment interaction converges in \(ci) episodes instead of \(cf). The real steps are unchanged — only the amount of thinking per step went up.",
                plot: FramePlot(label: qErrorLabel, curves: [
                    FrameCurve(label: "model-free", values: modelFree, color: plainTone),
                    FrameCurve(label: "20 imagined steps", values: imagined, color: goodTone),
                ], yRange: 0...0.6, xLabel: "episode"),
                readout: "\(cf) → \(ci) episodes"),
        GsFrame(status: "That is the whole promise of model-based RL: sample efficiency. Where real experience is expensive — a physical robot, a slow simulator — trading compute for interaction is exactly the trade you want.",
                bars: [FrameBars(label: "episodes to converge", values: [Float(cf), Float(ci)], color: mainTone, captions: ["model-free", "with imagination"])]),
        GsFrame(status: "Dreamer adds two things this toy cannot show: the model is latent (it predicts a compact state, not pixels) and the policy is trained by backpropagating through the imagined rollout itself, so the gradient flows through the model rather than around it.",
                bars: [FrameBars(label: "imagined updates per real step", values: [0, 20], color: deepTone, captions: ["model-free", "Dreamer-style"])]),
    ]
}

private func mbpoFrames() -> [GsFrame] {
    let errors = rolloutError(learnModel(seed: 9, transitions: 10), 10)
    let short = dynaRun(seed: 23, planningSteps: 5)
    let long = dynaRun(seed: 23, planningSteps: 40)
    let (cs, cl) = (converged(short), converged(long))
    let lengthBars = FrameBars(label: "error by rollout length", values: Array(errors.prefix(6)), color: badTone, captions: (1...6).map { "\($0)" })
    return [
        GsFrame(status: "MBPO's question is not whether to use a model but how far to trust it. Rolled out from the start state, this model's error grows with every imagined step: \(errors.prefix(5).map { f0($0) }.joined(separator: ", ")) after 1–5 steps.",
                plot: FramePlot(label: "model error over an imagined rollout", curves: [FrameCurve(label: "error", values: errors, color: badTone)], yRange: 0...5, xLabel: "imagined step"),
                readout: "error compounds with rollout length"),
        GsFrame(status: "So do not roll out from the start. MBPO branches short rollouts from states the agent actually visited — the model only has to be right for a few steps from a state where it has data, which is the regime where it is right.",
                bars: [lengthBars]),
        GsFrame(status: "Planning volume is a different knob, and on a model this small more of it keeps helping: 5 imagined updates per step converge in \(cs) episodes, 40 in \(cl). There is nothing to overfit to here — a six-state table is learned almost exactly, so extra planning is free accuracy.",
                plot: FramePlot(label: qErrorLabel, curves: [
                    FrameCurve(label: "5 imagined updates", values: short, color: goodTone),
                    FrameCurve(label: "40 imagined updates", values: long, color: mainTone),
                ], yRange: 0...0.6, xLabel: "episode"),
                readout: "\(cs) vs \(cl) episodes"),
        GsFrame(status: "That is exactly what stops being true at scale, and it is the gap MBPO addresses: with a learned neural model the imagined data is wrong in ways the previous frame shows, so the length of each rollout matters far more than the number of them.",
                bars: [lengthBars]),
        GsFrame(status: "MBPO's actual contribution is making that trade explicit: a model ensemble whose disagreement flags where the model is unreliable, and a rollout length chosen so the imagined data stays inside the region the ensemble agrees on.",
                bars: [FrameBars(label: "episodes to converge", values: [Float(cs), Float(cl)], color: mainTone, captions: ["k = 5", "k = 40"])]),
    ]
}

// MARK: - Interview-prep pattern guide: the stone game

// dp[i][j] is the score *difference* the mover can force on piles i..j.
private let stonePiles = [3, 9, 1, 2]

private func gameTheoryDpFrames() -> [GsFrame] {
    let n = stonePiles.count
    var dp = [[Int]](repeating: [Int](repeating: 0, count: n), count: n)
    let pileBars = FrameBars(label: "piles", values: stonePiles.map { Float($0) }, color: plainTone, captions: stonePiles.map { "\($0)" })
    var frames = [GsFrame(
        status: "Piles \(stonePiles.map(String.init).joined(separator: ", ")), players alternate taking from either end, both play optimally. Modelling the opponent as adversarial rather than passive is the entire difference from ordinary DP — and dp[i][j] holds the score *difference* the mover can force, not their score.",
        bars: [pileBars], readout: "total on the table = \(stonePiles.reduce(0, +))"
    )]
    for i in 0..<n { dp[i][i] = stonePiles[i] }
    frames.append(GsFrame(
        status: "Single piles are the base case: with one pile left the mover takes it, so the difference is the pile itself. Every longer range will be built from shorter ones, so ranges are filled by length.",
        bars: [FrameBars(label: "length 1", values: (0..<n).map { Float(dp[$0][$0]) }, color: mainTone, captions: (0..<n).map { "\($0)..\($0)" })],
        readout: "base case filled"
    ))
    for len in 2...n {
        let ranges = (0...(n - len)).map { ($0, $0 + len - 1) }
        for (i, j) in ranges { dp[i][j] = max(stonePiles[i] - dp[i + 1][j], stonePiles[j] - dp[i][j - 1]) }
        let detail = ranges.map { i, j in
            "[\(i)..\(j)]: take \(stonePiles[i]) → \(stonePiles[i]) − \(dp[i + 1][j]) = \(stonePiles[i] - dp[i + 1][j]), take \(stonePiles[j]) → \(stonePiles[j]) − \(dp[i][j - 1]) = \(stonePiles[j] - dp[i][j - 1])"
        }.joined(separator: "; ")
        frames.append(GsFrame(
            status: "Length \(len). Each entry is max over the two ends of (gain − best(rest)) — the subtraction *is* the opponent: whatever they can force on the remainder is a loss to the mover. \(detail).",
            bars: [FrameBars(label: "length \(len)", values: ranges.map { Float(dp[$0.0][$0.1]) }, color: mainTone, captions: ranges.map { "\($0.0)..\($0.1)" })],
            readout: ranges.map { "dp[\($0.0)][\($0.1)] = \(dp[$0.0][$0.1])" }.joined(separator: " · ")
        ))
    }
    let answer = dp[0][n - 1]
    frames.append(GsFrame(
        status: "dp[0][\(n - 1)] = \(answer) > 0, so the first player wins by \(answer). Note the trap: taking the larger visible end first (\(stonePiles.first!) vs \(stonePiles.last!) — here the greedy grab is the 3) loses, because it hands over the 9. O(n²) states, O(1) per state, and no turn flag anywhere.",
        bars: [pileBars, FrameBars(label: "first-player margin", values: [Float(answer)], color: answer > 0 ? goodTone : badTone, captions: ["dp[0][\(n - 1)]"])],
        readout: "first player wins by \(answer)"
    ))
    return frames
}

// MARK: - Config

private let searchLegend: [(Color, String)] = [(plainTone, "Baseline"), (goodTone, "Improved"), (badTone, "Cost / error")]

private let gsConfigs: [String: GsConfig] = [
    "game_theory_dp_pattern": GsConfig(intro: "The stone game filled by range length. Bars are dp[i][j] — the score difference the mover can force — so a negative bar is a range you do not want to be handed.",
                                       legend: [(mainTone, "dp[i][j]"), (goodTone, "Mover ahead"), (badTone, "Mover behind")], build: gameTheoryDpFrames),
    "minimax": GsConfig(intro: "A real tic-tac-toe position searched three ways — plain minimax, alpha-beta, and alpha-beta with move ordering — with the node counts measured, not quoted.",
                        legend: searchLegend, build: minimaxFrames),
    "mcts": GsConfig(intro: "MCTS actually playing random games from the same position, at three simulation budgets. The visit counts are what the search produced.",
                     legend: searchLegend, build: mctsFrames),
    "alphago": GsConfig(intro: "The same search with a value function replacing the rollout, then a policy prior narrowing the branching — measured as the share of the budget that reaches the best move.",
                        legend: searchLegend, build: alphaGoFrames),
    "alphazero": GsConfig(intro: "Search as a policy improvement operator: a deliberately mediocre prior in, a sharper visit distribution out, and that distribution as the training target.",
                          legend: searchLegend, build: alphaZeroFrames),
    "muzero": GsConfig(intro: "What happens to planning when the model is wrong, measured by corrupting the evaluation at four error rates — and what MuZero chooses to model instead.",
                       legend: searchLegend, build: muZeroFrames),
    "self_play": GsConfig(intro: "Fictitious play on rock-paper-scissors: the latest strategy cycles forever while the average converges to Nash. Both curves are measured exploitability.",
                          legend: searchLegend, build: selfPlayFrames),
    "world_models": GsConfig(intro: "A tabular model learned from a handful of real transitions, and the rate at which its errors compound over an imagined rollout.",
                             legend: searchLegend, build: worldModelFrames),
    "dreamer": GsConfig(intro: "Learning from imagined rollouts against learning from real ones only — same environment interaction, different amount of thinking per step.",
                        legend: searchLegend, build: dreamerFrames),
    "mbpo": GsConfig(intro: "How far to trust a learned model: compounding error by rollout length, and what more planning actually buys.",
                     legend: searchLegend, build: mbpoFrames),
]

// MARK: - UI

struct GameSearchLab: View {
    private let config: GsConfig
    private let key: String
    @Environment(\.palette) private var palette

    init(topicId: String) {
        key = gsConfigs[topicId] == nil ? "minimax" : topicId
        config = gsConfigs[key]!
    }

    var body: some View {
        let key = self.key
        AsyncFrameLab(key: "gamesearch:\(key)", speedMs: 1200, build: { gsConfigs[key]!.build() }) { frames, playback in
            content(frames, playback)
        }
    }

    @ViewBuilder
    private func content(_ frames: [GsFrame], _ playback: PlaybackState) -> some View {
        let frame = frames[min(playback.index, frames.count - 1)]
        LabCard {
            LabIntro(text: config.intro)
            if let board = frame.board { BoardView(board: board, marks: frame.boardMarks).padding(.top, 12) }
            if let plot = frame.plot { FramePlotView(plot: plot, dots: true).padding(.top, 12) }
            ForEach(frame.bars.indices, id: \.self) { FrameBarsView(bar: frame.bars[$0], negativeColor: badTone).padding(.top, 12) }
            FramePlayerFooter(readout: frame.readout, status: frame.status, legend: config.legend, playback: playback, captions: frames.map(\.status))
        }
    }
}

private struct BoardView: View {
    let board: [Int]
    let marks: [Int: Float]
    @Environment(\.palette) private var palette

    var body: some View {
        let peak = max(marks.values.map { abs($0) }.max() ?? 1, 0.001)
        GeometryReader { geo in
            let side = geo.size.width * 0.62
            let cell = (side - 8) / 3
            VStack(spacing: 4) {
                ForEach(0..<3, id: \.self) { row in
                    HStack(spacing: 4) {
                        ForEach(0..<3, id: \.self) { column in
                            let index = row * 3 + column
                            let mark = marks[index]
                            let background: Color = mark.map { mainTone.opacity(Double(0.15 + 0.6 * abs($0) / peak)) }
                                ?? (board[index] != 0 ? palette.outlineVariant : palette.outlineVariant.opacity(0.35))
                            VStack(spacing: 0) {
                                Text(board[index] == 1 ? "X" : board[index] == -1 ? "O" : "").font(AppFont.grotesk(22, .bold))
                                if board[index] == 0, let mark {
                                    Text(mark == mark.rounded() ? "\(Int(mark))" : fx(mark, 1)).font(AppFont.sans(11, .bold))
                                }
                            }
                            .frame(width: cell, height: cell)
                            .background(background, in: RoundedRectangle(cornerRadius: 10))
                        }
                    }
                }
            }
            .frame(width: geo.size.width)
        }
        .aspectRatio(1 / 0.62, contentMode: .fit)
    }
}
