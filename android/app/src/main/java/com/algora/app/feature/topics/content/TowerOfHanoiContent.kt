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

internal val towerOfHanoiContent = TopicContent(
    topicId = "tower_of_hanoi",
    figure = Figure(
        caption = "Every node splits into two recursive calls and exactly one real disk move (marked " +
            "\"mv\"). The roles of the three pegs rotate on the way down — the target of the outer call " +
            "is the spare peg of the inner one — and that rotation is the entire algorithm; nothing else " +
            "decides which disk goes where. Counting the mv nodes gives the move count: T(n) = 2T(n−1) + " +
            "1 = 2ⁿ − 1, so the recursion is short (depth n) but the output is exponential. Only the " +
            "first subcall is expanded here.",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("3 A→C", null, FigureTone.Primary),
                FigureNode("2 A→B", 0, FigureTone.Primary),
                FigureNode("1 A→C", 1),
                FigureNode("mv 2", 1, FigureTone.Accent),
                FigureNode("1 C→B", 1),
                FigureNode("mv 3", 0, FigureTone.Accent),
                FigureNode("2 B→C", 0, FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Tower of Hanoi moves a stack of differently-sized disks from a source peg to a target peg, one disk at a time, never placing a larger disk on a smaller one.",
        "It's the cleanest example of recursion reducing a problem to two smaller copies of itself: move the top n−1 disks aside, move the biggest, then move the n−1 back.",
    ),
    steps = listOf(
        StepCard(1, "Move n−1 Aside", "Recursively move the top n−1 disks from source to the auxiliary peg.", 0xFF10B981),
        StepCard(2, "Move the Largest", "Move the single biggest disk from source to target.", 0xFF3B82F6),
        StepCard(3, "Move n−1 Onto It", "Recursively move the n−1 disks from auxiliary to target.", 0xFFF59E0B),
        StepCard(4, "Base Case", "Moving zero disks does nothing — that stops the recursion.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Minimum moves", "2ⁿ − 1", "Provably optimal — no sequence is shorter."),
        FormulaEntry("Time", "O(2ⁿ)", "Each disk roughly doubles the move count."),
        FormulaEntry("Recurrence", "T(n) = 2·T(n−1) + 1", "Two subproblems of size n−1 plus one move."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of disks"),
        NotationEntry("source / target / aux", "the three pegs"),
        NotationEntry("2ⁿ − 1", "optimal number of moves"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Tower of Hanoi",
            accentColor = 0xFF6366F1,
            code = """
                fun hanoi(n: Int, source: Char, target: Char, aux: Char) {
                    if (n == 0) return                         // base case
                    hanoi(n - 1, source, aux, target)          // move n-1 aside
                    println("Move disk ${'$'}n from ${'$'}source to ${'$'}target")
                    hanoi(n - 1, aux, target, source)          // move n-1 onto target
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RecursionTreeVisualizer,
    applications = listOf(
        ApplicationCard("book", 0xFF10B981, "Teaching Recursion", "It shows a problem defined purely in terms of two smaller instances of itself."),
        ApplicationCard("history", 0xFF3B82F6, "Backup Rotation", "The 'Tower of Hanoi' backup scheme rotates tape/disk sets on a Hanoi-like schedule."),
        ApplicationCard("chip", 0xFFF59E0B, "Exponential Cost Intuition", "It makes 2ⁿ growth tangible — 64 disks would take longer than the age of the universe."),
    ),
    takeaways = listOf(
        "Hanoi reduces n disks to two subproblems of n−1 plus one move.",
        "The optimal solution takes exactly 2ⁿ − 1 moves — exponential and unavoidable.",
        "The recurrence T(n) = 2T(n−1) + 1 solves to that closed form.",
        "It's a legibility win for recursion, not an efficiency one — the cost is inherent to the puzzle.",
    ),
    crossLinks = listOf(
        CrossLink("factorial", "Factorial"),
        CrossLink("merge_sort", "Merge Sort"),
    ),
)
