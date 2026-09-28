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

internal val permutationGenerationContent = TopicContent(
    topicId = "permutation_generation",
    figure = Figure(
        caption = "The branching factor shrinks by one at every level — three choices for the first slot, " +
            "two for the second, one for the last. That is why the tree has n! leaves and not nⁿ: a used " +
            "element is simply not offered again. Only the first branch is drawn out. The swap-back on " +
            "the way out of each node is what makes it work in place: without it the array would still " +
            "be permuted when the next sibling starts, and every later branch would be built on the " +
            "wrong arrangement.",
        shape = FigureShape.Tree(
            nodes = listOf(
                FigureNode("···", null, FigureTone.Primary),
                FigureNode("A··", 0, FigureTone.Primary),
                FigureNode("AB·", 1, FigureTone.Primary),
                FigureNode("ABC", 2, FigureTone.Accent),
                FigureNode("AC·", 1),
                FigureNode("B··", 0),
                FigureNode("C··", 0),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Permutation generation produces every possible ordering of a set of elements — n elements yield n! arrangements.",
        "The recursive approach fixes one element at each position and permutes the rest, using backtracking to swap choices in and out of place.",
    ),
    steps = listOf(
        StepCard(1, "Choose a Position", "Work left to right, deciding which element sits at the current index.", 0xFF10B981),
        StepCard(2, "Fix Each Candidate", "Swap each remaining element into the current position in turn.", 0xFF3B82F6),
        StepCard(3, "Recurse on the Rest", "Permute the suffix after the fixed position.", 0xFFF59E0B),
        StepCard(4, "Backtrack", "Swap the element back so the next candidate starts from the original arrangement.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Count", "n!", "There are n! permutations of n distinct elements."),
        FormulaEntry("Time", "O(n · n!)", "n! permutations, each costing O(n) to record."),
        FormulaEntry("Space", "O(n)", "Recursion depth plus the working arrangement (output aside)."),
    ),
    notationKey = listOf(
        NotationEntry("n!", "number of orderings of n elements"),
        NotationEntry("k", "current position being fixed"),
        NotationEntry("swap/undo", "the backtracking move restoring state"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Permutations via swap-backtrack",
            accentColor = 0xFF6366F1,
            code = """
                fun permutations(a: IntArray): List<List<Int>> {
                    val out = mutableListOf<List<Int>>()
                    fun permute(k: Int) {
                        if (k == a.size) { out.add(a.toList()); return }
                        for (i in k until a.size) {
                            a[k] = a[i].also { a[i] = a[k] }   // fix a[i] at position k
                            permute(k + 1)
                            a[k] = a[i].also { a[i] = a[k] }   // undo the swap
                        }
                    }
                    permute(0)
                    return out
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RecursionTreeVisualizer,
    applications = listOf(
        ApplicationCard("target", 0xFF10B981, "Brute-Force Search", "When n is small, enumerating all orderings solves problems like brute-force TSP exactly."),
        ApplicationCard("game", 0xFF3B82F6, "Puzzle & Anagram Generation", "Listing every arrangement drives anagram finders and combinatorial puzzle generators."),
        ApplicationCard("flask", 0xFFF59E0B, "Testing & Fuzzing", "Trying every input ordering surfaces order-dependent bugs in small test sets."),
    ),
    takeaways = listOf(
        "Fix one element per position and recurse on the rest to generate all n! permutations.",
        "Swap-then-undo backtracking generates them in place with O(n) extra space.",
        "The n! blowup means full enumeration is only viable for small n.",
        "For a single random ordering, use the Fisher–Yates shuffle instead of generating all.",
    ),
    crossLinks = listOf(
        CrossLink("subset_sum", "Subset Sum"),
        CrossLink("factorial", "Factorial"),
    ),
)
