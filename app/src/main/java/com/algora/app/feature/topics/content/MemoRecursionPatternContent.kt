package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. The bridge from a brute-force recursion to a DP: same function,
// one cache. This is usually the fastest correct answer to give under time pressure.
internal val memoRecursionPatternContent = TopicContent(
    topicId = "memo_recursion_pattern",
    whatIsIt = listOf(
        "Top-down memoization keeps the brute-force recursion and caches its results by argument. Repeated subproblems collapse to one evaluation each, so exponential recursion becomes linear in the number of distinct states.",
        "It is the safest route to a DP in an interview: write the recursion you can reason about, prove it is correct, then add the cache. Converting to a bottom-up table afterwards is mechanical.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "A recursion whose call tree repeats arguments — overlapping subproblems plus an optimal-substructure question (best, count, reachable).", 0xFFF59E0B),
        StepCard(2, "Minimise the Arguments", "Cache keys are the arguments, so only pass what genuinely varies. Constants and the input array stay outside the signature.", 0xFF3B82F6),
        StepCard(3, "Cache Around the Body", "Return the cached value if present; otherwise compute, store, return. Base cases still return before the cache is consulted.", 0xFFEF4444),
        StepCard(4, "Convert if Depth Bites", "States × work-per-state gives the complexity. If recursion depth risks a stack overflow, replay the same recurrence bottom-up.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Time", "states × work per state", "Each distinct argument tuple is evaluated exactly once."),
        FormulaEntry("Space", "O(states + depth)", "The cache plus the recursion stack."),
        FormulaEntry("Without the cache", "O(branch^depth)", "Fibonacci-style recursion re-derives the same call millions of times."),
    ),
    notationKey = listOf(
        NotationEntry("memo", "map or array from argument tuple to answer"),
        NotationEntry("state", "one distinct argument tuple"),
        NotationEntry("depth", "longest chain of nested calls"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Memoized recursion (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                from functools import lru_cache

                def min_coins(coins, target):
                    @lru_cache(maxsize=None)               # cache keyed by `rest`
                    def best(rest):
                        if rest == 0:
                            return 0
                        if rest < 0:
                            return float('inf')
                        return min((1 + best(rest - c) for c in coins), default=float('inf'))

                    answer = best(target)
                    return -1 if answer == float('inf') else answer

                def grid_paths(rows, cols, blocked):       # explicit cache, two-argument state
                    memo = {}
                    def walk(r, c):
                        if r >= rows or c >= cols or (r, c) in blocked:
                            return 0
                        if (r, c) == (rows - 1, cols - 1):
                            return 1
                        if (r, c) not in memo:
                            memo[(r, c)] = walk(r + 1, c) + walk(r, c + 1)
                        return memo[(r, c)]
                    return walk(0, 0)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RecursionTreeVisualizer,
    applications = listOf(
        ApplicationCard("bulb", 0xFF3B82F6, "Interview DP", "Get to a correct exponential solution fast, then memoize it in two lines."),
        ApplicationCard("game", 0xFF10B981, "Game Search", "Minimax over repeated positions caches by board state — a transposition table."),
        ApplicationCard("code", 0xFF8B5CF6, "Parser Backtracking", "Regex and wildcard matching cache (text index, pattern index) pairs."),
    ),
    takeaways = listOf(
        "Recursion first, cache second — correctness before speed.",
        "The argument list *is* the state; every extra parameter multiplies the state space.",
        "Complexity = number of states × work per state. Say both numbers out loud when asked.",
        "Memoization needs a pure function: same arguments, same answer, no hidden mutation.",
    ),
    crossLinks = listOf(
        CrossLink("fibonacci_dp", "Fibonacci DP (Algorithms)"),
        CrossLink("coin_change", "Coin Change (Algorithms)"),
        CrossLink("backtracking_pattern", "Backtracking Pattern"),
    ),
)
