package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureNode
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. Two-player DP: the opponent is not an obstacle to work around, it
// is a minimising layer in the recurrence.
internal val gameTheoryDpPatternContent = TopicContent(
    topicId = "game_theory_dp_pattern",
    figure = Figure(
        caption = "The levels alternate: your move maximises, the reply minimises. Define the value as " +
            "the score *difference* from the mover's side and the turn flag disappears — every level " +
            "becomes max over moves of (gain − best(rest)).",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("max +7", null, FigureTone.Accent),
                FigureNode("min −8", 0, FigureTone.Warn),
                FigureNode("min +5", 0, FigureTone.Primary),
                FigureNode("take 9", 1, FigureTone.Muted),
                FigureNode("take 2", 1, FigureTone.Muted),
                FigureNode("take 3", 2, FigureTone.Muted),
                FigureNode("take 1", 2, FigureTone.Muted),
            ),
        ),
    ),
    whatIsIt = listOf(
        "In a two-player game with perfect information, both sides play optimally — so the recurrence alternates: your move maximises, their reply minimises. Modelling the opponent as adversarial rather than passive is the entire difference from ordinary DP.",
        "The clean formulation drops the turn flag: define the answer as the *score difference* from the mover's point of view. Then every level is a max over moves of (gain − best(rest)), because the opponent's advantage is your loss.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "\"Can the first player win\", \"both play optimally\", stone/coin removal games, Nim piles, or turn-based board scoring.", 0xFFF59E0B),
        StepCard(2, "Define the State", "What remains of the game: a range, a pile count, a mask of used items. Add whose turn it is only if the relative-score trick does not apply.", 0xFF3B82F6),
        StepCard(3, "Negate Across the Turn", "best(state) = max over moves of (gain(move) − best(next state)). The subtraction is what makes the opponent optimal.", 0xFFEF4444),
        StepCard(4, "Read the Verdict", "For win/lose questions, a state is winning if *any* move leads to a losing state for the opponent. For scores, first player wins when best(start) > 0.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Relative score", "best(s) = max over moves (gain - best(s'))", "One recurrence, no turn parameter."),
        FormulaEntry("Win / lose", "win(s) = OR over moves of not win(s')", "A state is losing when every move hands the opponent a win."),
        FormulaEntry("Nim", "first player wins iff XOR of pile sizes ≠ 0", "Sprague-Grundy: the XOR of the Grundy values."),
    ),
    notationKey = listOf(
        NotationEntry("best(s)", "mover's score minus opponent's, played optimally from s"),
        NotationEntry("s'", "state after the chosen move"),
        NotationEntry("Grundy", "value making an impartial game equivalent to a Nim pile"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Stone game as a score difference (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                from functools import lru_cache

                def first_player_wins(piles):
                    @lru_cache(maxsize=None)
                    def best(i, j):                        # mover's lead over the range i..j
                        if i > j:
                            return 0
                        take_left = piles[i] - best(i + 1, j)     # opponent's lead is my loss
                        take_right = piles[j] - best(i, j - 1)
                        return max(take_left, take_right)
                    return best(0, len(piles) - 1) > 0

                def can_win_take_1_to_3(n):                # remove 1..3 stones, taking the last wins
                    win = [False] * (n + 1)
                    for total in range(1, n + 1):
                        win[total] = any(not win[total - take]
                                         for take in (1, 2, 3) if take <= total)
                    return win[n]                          # losing exactly when n % 4 == 0
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.GameSearchPlayer,
    applications = listOf(
        ApplicationCard("game", 0xFF3B82F6, "Turn-based Games", "Stone, coin and card games where both sides play perfectly."),
        ApplicationCard("robot", 0xFF10B981, "Adversarial Search", "The DP form of minimax; alpha-beta prunes the same tree."),
        ApplicationCard("target", 0xFF8B5CF6, "Worst-case Planning", "Bounding cost when the environment can behave adversarially."),
    ),
    takeaways = listOf(
        "The opponent is a minimising layer, not an obstacle — negate across the turn boundary.",
        "Scoring as a difference removes the turn flag and halves the state space.",
        "For win/lose questions: winning means *some* move leaves the opponent in a losing state.",
        "Impartial games collapse to Nim via Grundy numbers — mention it if the piles are independent.",
    ),
    crossLinks = listOf(
        CrossLink("minimax", "Minimax (Reinforcement Learning)"),
        CrossLink("memo_recursion_pattern", "Top-down Memoization"),
        CrossLink("interval_dp_pattern", "Interval DP Pattern"),
    ),
)
