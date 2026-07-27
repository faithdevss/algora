package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val strassensAlgorithmContent = TopicContent(
    topicId = "strassens_algorithm",
    whatIsIt = listOf(
        "Strassen's algorithm multiplies two matrices faster than the standard O(n³) triple loop by reducing the eight sub-block multiplications of a divide-and-conquer split to seven.",
        "It's the matrix analogue of Karatsuba's trick: clever additions of the block quadrants let seven products reconstruct the full result, saving one multiply at every level of recursion.",
    ),
    steps = listOf(
        StepCard(1, "Split into Quadrants", "Divide each n×n matrix into four n/2 × n/2 blocks.", 0xFF10B981),
        StepCard(2, "Form Seven Products", "Combine the quadrants into seven matrices M₁…M₇, each one recursive multiplication.", 0xFF3B82F6),
        StepCard(3, "Recombine", "Add and subtract the seven products to build the four result quadrants.", 0xFFF59E0B),
        StepCard(4, "Recurse", "Apply the same split to each sub-multiplication down to a small base case.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Recurrence", "T(n) = 7·T(n/2) + O(n²)", "Seven half-size products plus quadratic additions."),
        FormulaEntry("Time", "O(n^2.807)", "n^log₂7 — below the naive O(n³)."),
        FormulaEntry("Products", "8 → 7", "The saved multiplication is the whole gain."),
    ),
    notationKey = listOf(
        NotationEntry("n", "matrix dimension (n×n)"),
        NotationEntry("quadrant", "one of the four n/2 × n/2 sub-blocks"),
        NotationEntry("M₁…M₇", "the seven intermediate products"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Strassen — the seven products",
            accentColor = 0xFF6366F1,
            code = """
                // A, B split into quadrants A11..A22, B11..B22.
                // Seven recursive multiplications instead of eight:
                val m1 = mul(A11 + A22, B11 + B22)
                val m2 = mul(A21 + A22, B11)
                val m3 = mul(A11, B12 - B22)
                val m4 = mul(A22, B21 - B11)
                val m5 = mul(A11 + A12, B22)
                val m6 = mul(A21 - A11, B11 + B12)
                val m7 = mul(A12 - A22, B21 + B22)

                val C11 = m1 + m4 - m5 + m7
                val C12 = m3 + m5
                val C21 = m2 + m4
                val C22 = m1 - m2 + m3 + m6
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RecursionTreeVisualizer,
    applications = listOf(
        ApplicationCard("flask", 0xFF10B981, "Large Matrix Products", "Scientific computing and graphics libraries use Strassen for big dense multiplications."),
        ApplicationCard("robot", 0xFF3B82F6, "ML Linear Algebra", "Neural-network and simulation workloads are dominated by matrix multiplication cost."),
        ApplicationCard("book", 0xFFF59E0B, "Theory Milestone", "It broke the O(n³) barrier and launched the search for faster multiplication exponents."),
    ),
    takeaways = listOf(
        "Strassen's cuts block multiplications from 8 to 7, giving O(n^2.807).",
        "It's the matrix version of Karatsuba — trade multiplies for adds via clever combinations.",
        "Its overhead and numerical instability mean it only pays off on large matrices.",
        "It opened the theoretical race that later reached exponents near 2.37.",
    ),
    crossLinks = listOf(
        CrossLink("karatsubas_algorithm", "Karatsuba's Algorithm"),
        CrossLink("matrix_chain_multiplication", "Matrix Chain Multiplication"),
    ),
)
