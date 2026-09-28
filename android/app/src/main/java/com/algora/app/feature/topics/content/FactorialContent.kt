package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureStack
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val factorialContent = TopicContent(
    topicId = "factorial",
    figure = Figure(
        caption = "Nothing is multiplied on the way down. Each call defers its multiply and pushes " +
            "another frame, so the base case at the top is the first thing that can return anything at " +
            "all — and the arithmetic then happens in reverse, one frame at a time on the way out. " +
            "That deferred half is the cost: a loop computes the same number in O(1) space, recursion " +
            "holds n frames, and a large enough n overflows the stack rather than returning a wrong answer.",
        shape = FigureShape.Stacks(
            columns = listOf(
                FigureStack(
                    label = "frames (top first)",
                    entries = listOf("fact(0)", "fact(1)", "fact(2)", "fact(3)"),
                    note = "pushed on the way down",
                ),
                FigureStack(
                    label = "returns",
                    entries = listOf("1", "1 × 1 = 1", "2 × 1 = 2", "3 × 2 = 6"),
                    tone = FigureTone.Accent,
                    note = "resolved on the way back up",
                ),
            ),
        ),
    ),
    whatIsIt = listOf(
        "The factorial of n (written n!) is the product of all positive integers up to n, and it's the classic first example of recursion: n! = n × (n−1)!.",
        "It shows the two ingredients every recursive function needs — a base case that stops the recursion (0! = 1) and a recursive case that shrinks the problem toward it.",
    ),
    steps = listOf(
        StepCard(1, "Base Case", "Define the stopping point: 0! = 1 (and 1! = 1). Without it the recursion never ends.", 0xFF10B981),
        StepCard(2, "Recursive Case", "Express the answer in terms of a smaller one: n! = n × (n−1)!.", 0xFF3B82F6),
        StepCard(3, "Wind Down", "Each call defers its multiplication and calls itself with n−1, stacking frames until the base case.", 0xFFF59E0B),
        StepCard(4, "Unwind", "Hitting the base case, the stack resolves back up, multiplying results on the way out.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Definition", "n! = n × (n−1)!, 0! = 1", "Recursive case plus base case."),
        FormulaEntry("Time", "O(n)", "One multiplication per level, n levels deep."),
        FormulaEntry("Space", "O(n)", "Recursion stacks n frames; an iterative loop is O(1)."),
    ),
    notationKey = listOf(
        NotationEntry("n!", "product 1 × 2 × … × n"),
        NotationEntry("base case", "the input that returns directly, without recursing"),
        NotationEntry("call stack", "frames held while deeper calls run"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Factorial — recursive and iterative",
            accentColor = 0xFF6366F1,
            code = """
                fun factorial(n: Int): Long =
                    if (n <= 1) 1L                 // base case
                    else n * factorial(n - 1)      // recursive case

                // Iterative equivalent — O(1) stack space.
                fun factorialIterative(n: Int): Long {
                    var result = 1L
                    for (i in 2..n) result *= i
                    return result
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RecursionTreeVisualizer,
    applications = listOf(
        ApplicationCard("chart", 0xFF10B981, "Combinatorics", "Permutation and combination counts (nPr, nCr) are built directly from factorials."),
        ApplicationCard("book", 0xFF3B82F6, "Learning Recursion", "It's the canonical base-case + recursive-case example every course starts with."),
        ApplicationCard("flask", 0xFFF59E0B, "Probability & Series", "Factorials appear throughout probability, Taylor series, and the gamma function."),
    ),
    takeaways = listOf(
        "Every recursion needs a base case and a smaller recursive case — factorial is the minimal example.",
        "It runs in O(n) time and O(n) stack space; the iterative version is O(1) space.",
        "Factorials grow explosively, overflowing 64-bit integers past 20! — mind the type.",
        "The same recursive shape generalizes to any 'solve smaller, combine' problem.",
    ),
    crossLinks = listOf(
        CrossLink("fibonacci_recursive", "Fibonacci"),
        CrossLink("permutation_generation", "Permutation Generation"),
    ),
)
