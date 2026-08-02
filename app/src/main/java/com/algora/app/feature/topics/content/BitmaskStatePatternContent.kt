package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. When n is suspiciously small (≤ 20), the subset itself is the DP
// state and an integer is the set.
internal val bitmaskStatePatternContent = TopicContent(
    topicId = "bitmask_state_pattern",
    whatIsIt = listOf(
        "When the constraint says n ≤ 20, the intended state is often the *set* of items already used. An integer's bits encode that set, so a whole subset is one array index and the DP table is 2^n wide.",
        "It replaces a factorial search over orderings with an exponential one over subsets: n! becomes 2^n · n, which is the difference between impossible and instant at n = 15.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "Tiny n with an assignment, ordering or covering question — travelling salesman, task-to-worker matching, partition into k groups.", 0xFFF59E0B),
        StepCard(2, "Let the Mask Be the Set", "Bit i set means item i is used. dp[mask] is the answer for exactly that set of used items.", 0xFF3B82F6),
        StepCard(3, "Transition by One Bit", "From dp[mask], try each unused i: dp[mask | (1 << i)] = best(that, dp[mask] + cost). popcount(mask) often tells you which slot i fills.", 0xFFEF4444),
        StepCard(4, "Read the Full Mask", "dp[(1 << n) - 1] is the answer with everything used. For TSP, keep the last-visited city as a second dimension.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Assignment DP", "O(2^n · n)", "2^n masks, n candidate next items each."),
        FormulaEntry("TSP", "O(2^n · n²)", "dp[mask][last] over all masks, ends and next cities."),
        FormulaEntry("Subset enumeration", "sub = (sub - 1) & mask", "Iterates every submask of mask in O(3^n) total."),
    ),
    notationKey = listOf(
        NotationEntry("mask", "integer whose set bits are the chosen items"),
        NotationEntry("1 << i", "the bit standing for item i"),
        NotationEntry("popcount", "number of set bits — how many items are used"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Minimum-cost assignment (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def min_assignment_cost(cost):             # cost[worker][task], n <= 20
                    n = len(cost)
                    full = (1 << n) - 1
                    dp = [float('inf')] * (1 << n)
                    dp[0] = 0
                    for mask in range(1 << n):
                        if dp[mask] == float('inf'):
                            continue
                        worker = bin(mask).count('1')      # tasks assigned so far = next worker
                        if worker == n:
                            continue
                        for task in range(n):
                            bit = 1 << task
                            if mask & bit:
                                continue                   # task already taken
                            nxt = mask | bit
                            dp[nxt] = min(dp[nxt], dp[mask] + cost[worker][task])
                    return dp[full]
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.BitBoardPlayer,
    applications = listOf(
        ApplicationCard("map", 0xFF3B82F6, "Travelling Salesman", "Held-Karp: dp[mask][last] over visited sets, exact for n around 20."),
        ApplicationCard("users", 0xFF10B981, "Assignment & Matching", "Workers to tasks, or students to seats, minimising total cost."),
        ApplicationCard("game", 0xFF8B5CF6, "Board States", "Compact occupancy for N-Queens, Sudoku candidates and tiling puzzles."),
    ),
    takeaways = listOf(
        "A small n bound in the constraints is the hint — 2^20 is a million states, 20! is not enumerable.",
        "The mask is the state; anything else you need (last item, remaining budget) becomes a second dimension.",
        "popcount(mask) usually replaces a separate index — the count of used items *is* the position.",
        "Watch the memory: 2^n longs at n = 24 is already 128 MB.",
    ),
    crossLinks = listOf(
        CrossLink("bitmask_dp", "Bitmask DP (Algorithms)"),
        CrossLink("subsets_bitmask", "Subsets using Bitmask (Algorithms)"),
        CrossLink("bit_manipulation_pattern", "Bit Manipulation Pattern"),
    ),
)
