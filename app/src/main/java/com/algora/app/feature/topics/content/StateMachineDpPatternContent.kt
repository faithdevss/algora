package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

// Interview-prep pattern guide. The stock-trading family and every "you cannot do X twice in a row"
// question: a tiny finite-state machine advanced one index at a time.
internal val stateMachineDpPatternContent = TopicContent(
    topicId = "state_machine_dp_pattern",
    whatIsIt = listOf(
        "Some sequence problems carry a mode, not just a position: holding or not holding a stock, in a cooldown, having used one deletion. The DP state becomes (index, mode) and each step is a transition between modes.",
        "Drawing the machine — a few nodes and the moves between them — turns a confusing word problem into a handful of one-line recurrences, each usually rolled into a couple of scalars.",
    ),
    steps = listOf(
        StepCard(1, "Spot the Signal", "A per-element decision constrained by history: no two adjacent, alternate actions, at most k transactions, cooldown after acting.", 0xFFF59E0B),
        StepCard(2, "Enumerate the Modes", "List the situations you can be in after processing index i. Keep the list minimal — every mode costs a pass.", 0xFF3B82F6),
        StepCard(3, "Draw the Transitions", "For each mode, what can index i + 1 do, and what does it cost or earn? That drawing is the recurrence.", 0xFFEF4444),
        StepCard(4, "Roll to Scalars", "Each mode depends only on the previous index, so keep one variable per mode and update them from the old values in a fixed order.", 0xFF10B981),
    ),
    formulas = listOf(
        FormulaEntry("Buy / sell", "hold = max(hold, free - p); free = max(free, hold + p)", "Unlimited transactions: two modes, one pass."),
        FormulaEntry("Time / Space", "O(n · modes) / O(modes)", "Modes is a small constant, or k for k-transaction variants."),
        FormulaEntry("House robber", "take = skip + v; skip = max(skip, take_prev)", "The same shape: acting now forbids acting next."),
    ),
    notationKey = listOf(
        NotationEntry("hold", "best balance while holding an asset"),
        NotationEntry("free", "best balance while holding nothing"),
        NotationEntry("p", "price or value at the current index"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Two state machines (Python)",
            accentColor = 0xFFF59E0B,
            code = """
                def max_profit(prices):                    # unlimited buys and sells
                    hold, free = float('-inf'), 0
                    for p in prices:
                        hold = max(hold, free - p)         # buy today, or keep holding
                        free = max(free, hold + p)         # sell today, or stay out
                    return free

                def max_profit_cooldown(prices):           # one rest day after each sale
                    hold, free, cooling = float('-inf'), 0, float('-inf')
                    for p in prices:
                        prev_hold, prev_free, prev_cool = hold, free, cooling
                        hold = max(prev_hold, prev_free - p)
                        cooling = prev_hold + p            # sold today
                        free = max(prev_free, prev_cool)   # yesterday's sale has cleared
                    return max(free, cooling)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.DpGridVisualizer,
    applications = listOf(
        ApplicationCard("finance", 0xFF3B82F6, "Trading Rules", "Transaction limits, fees and cooldowns are each one extra mode or one extra term."),
        ApplicationCard("chip", 0xFF10B981, "Scheduling with Rest", "Jobs or shifts that cannot run back to back."),
        ApplicationCard("translate", 0xFF8B5CF6, "Constrained Strings", "Counting strings under adjacency rules, or matching with a limited edit budget."),
    ),
    takeaways = listOf(
        "Draw the machine before writing code — modes as nodes, allowed moves as edges.",
        "A hidden constraint (cooldown, limit, one-time use) is a state dimension, not an if-statement.",
        "Snapshot the previous values before updating, or one mode reads another's new value mid-step.",
        "Only the previous index matters, so O(modes) space suffices; the table is a debugging aid.",
    ),
    crossLinks = listOf(
        CrossLink("knapsack_dp_pattern", "0/1 Knapsack DP Pattern"),
        CrossLink("running_best_pattern", "Running Best (Kadane)"),
        CrossLink("fibonacci_dp", "Fibonacci DP (Algorithms)"),
    ),
)
