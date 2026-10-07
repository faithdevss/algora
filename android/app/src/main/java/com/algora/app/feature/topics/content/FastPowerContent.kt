package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val fastPowerContent = TopicContent(
    topicId = "fast_power",
    figure = Figure(
        caption = "13 in binary is 1101, so 3¹³ = 3⁸ · 3⁴ · 3¹ = 6561 · 81 · 3 = 1594323 — one squaring " +
            "per bit and one extra multiply per bit that is set, five multiplications rather than " +
            "twelve. The recursive reading is the same thing said differently: an even power is the " +
            "square of the half-power, an odd power is that times one more copy of the base. What lifts " +
            "this above a micro-optimisation is that it works in any monoid, anything with an " +
            "associative operation and an identity. Substitute 2×2 matrix multiplication and the nth " +
            "Fibonacci number falls out in O(log n); substitute modular multiplication and you have the " +
            "routine RSA and Diffie-Hellman are built on. For general real exponents numerics " +
            "libraries use exp(y · ln a) instead — and for plain integers the result overflows long " +
            "before log n starts to matter, which is why the modular version is the one that gets used.",
        shape = FigureShape.Strip(
            cells = listOf("1", "1", "0", "1"),
            bands = listOf(
                FigureBand(0, 1, "multiply in", FigureTone.Accent),
                FigureBand(2, 2, "skip", FigureTone.Warn),
                FigureBand(3, 3, "in", FigureTone.Accent),
            ),
            aux = listOf("3⁸", "3⁴", "3²", "3¹"),
            auxLabel = "the squarings, most significant bit first — 3² is computed and then not used",
        ),
    ),
    whatIsIt = listOf(
        "Fast power — binary exponentiation, or exponentiation by squaring — computes aⁿ in O(log n) multiplications instead of the n − 1 a naive loop needs. The idea is one line: to raise something to an even power, square the half-power; to raise it to an odd power, do that and multiply by one more copy of the base.",
        "Following it on 3¹³ shows where the saving comes from. 13 is odd, so 3¹³ = 3 · 3¹²; 12 is even, so 3¹² = (3⁶)²; 6 is even, so 3⁶ = (3³)²; 3 is odd, so 3³ = 3 · 3²; and 3² = (3¹)². That is five multiplications rather than twelve, and the recursion depth is ⌊log₂ 13⌋ + 1 = 4. The iterative form makes the same computation look like binary: 13 is 1101₂, so 3¹³ = 3⁸ · 3⁴ · 3¹ = 6561 · 81 · 3 = 1594323, one squaring per bit and one extra multiply per set bit.",
        "What makes this more than a micro-optimisation is that it works in any monoid — anything with an associative operation and an identity. Substituting 2×2 matrix multiplication for integer multiplication computes the nth Fibonacci number in O(log n) operations, because [[1,1],[1,0]]ⁿ has F(n) in its off-diagonal. Substituting modular multiplication gives modular exponentiation, which is what makes RSA and Diffie-Hellman computable at all. The one caveat is that the naive loop is not always the thing being beaten: for general real exponents a numerics library will use exp(y · ln a) (computed in extended precision) instead. And for integers the answer overflows long before n is large enough for the log to matter, which is precisely why the modular version is the one that gets used.",
    ),
    steps = listOf(
        StepCard(1, "Split on Parity", "Even exponent → square the half-power. Odd → peel off one factor and recurse on n − 1.", 0xFFF59E0B),
        StepCard(2, "Recurse on n / 2", "Each level halves the exponent, so the depth is ⌊log₂ n⌋ + 1.", 0xFF3B82F6),
        StepCard(3, "Square, Do Not Recompute", "Compute the half-power once and square it. Calling twice is the bug that keeps it O(n).", 0xFFEF4444),
        StepCard(4, "Bottom Out at n = 0", "a⁰ = 1, the identity — which is what generalises the algorithm to matrices.", 0xFF10B981),
        StepCard(5, "Read It as Binary", "Iteratively: square the base each step, and multiply into the result on every set bit.", 0xFF8B5CF6),
        StepCard(6, "Swap the Operation", "Matrices give Fibonacci in O(log n); modular multiplication gives RSA.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Recurrence", "aⁿ = (a^(n/2))² if n even, a · a^(n−1) if odd", "One multiplication per level, two on odd levels."),
        FormulaEntry("Cost", "O(log n) multiplications", "Against n − 1 for the naive loop."),
        FormulaEntry("Exact count", "⌊log₂ n⌋ squarings + (popcount(n) − 1) multiplies", "3¹³: 3 squarings, 2 multiplies."),
        FormulaEntry("Worked example", "3¹³ = 3⁸ · 3⁴ · 3¹ = 1594323", "13 = 1101₂; 6561 × 81 × 3."),
        FormulaEntry("Matrix form", "[[1,1],[1,0]]ⁿ = [[F(n+1), F(n)], [F(n), F(n−1)]]", "Fibonacci in O(log n) matrix multiplications."),
        FormulaEntry("Requirement", "an associative operation with an identity", "Any monoid — integers, matrices, permutations, residues."),
    ),
    notationKey = listOf(
        NotationEntry("n", "the exponent; the cost is logarithmic in it, not linear"),
        NotationEntry("popcount(n)", "number of 1 bits — how many extra multiplies the iterative form does"),
        NotationEntry("monoid", "a set with an associative operation and an identity element"),
        NotationEntry("squaring", "the step that halves the remaining work"),
        NotationEntry("identity", "a⁰ = 1 for integers, the identity matrix for matrices"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Recursive and iterative",
            accentColor = 0xFFF59E0B,
            code = """
                fun power(base: Long, exponent: Int): Long {
                    if (exponent == 0) return 1
                    val half = power(base, exponent / 2)   // computed ONCE …
                    val squared = half * half              // … then squared
                    return if (exponent % 2 == 0) squared else squared * base
                }

                // Writing `power(base, e/2) * power(base, e/2)` instead computes the same
                // subproblem twice and the whole thing collapses back to O(n).

                fun powerIterative(base: Long, exponent: Int): Long {
                    var result = 1L
                    var b = base
                    var e = exponent
                    while (e > 0) {
                        if (e and 1 == 1) result *= b   // this bit is set: fold in the current power
                        b *= b                          // 3, 9, 81, 6561, …
                        e = e shr 1
                    }
                    return result
                }

                power(3, 13)            // 1594323
                // 13 = 1101₂ → 3⁸ · 3⁴ · 3¹ = 6561 · 81 · 3
                // 3 squarings and 2 multiplies, against 12 multiplies for a naive loop.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Same algorithm, matrices — Fibonacci in O(log n)",
            accentColor = 0xFFEC4899,
            code = """
                // Nothing about the exponentiation changes; only the operation and the identity do.
                private fun mul(x: Array<LongArray>, y: Array<LongArray>) = arrayOf(
                    longArrayOf(x[0][0]*y[0][0] + x[0][1]*y[1][0], x[0][0]*y[0][1] + x[0][1]*y[1][1]),
                    longArrayOf(x[1][0]*y[0][0] + x[1][1]*y[1][0], x[1][0]*y[0][1] + x[1][1]*y[1][1]),
                )

                fun fibonacci(n: Int): Long {
                    if (n == 0) return 0
                    var result = arrayOf(longArrayOf(1, 0), longArrayOf(0, 1))   // identity matrix
                    var m = arrayOf(longArrayOf(1, 1), longArrayOf(1, 0))
                    var e = n
                    while (e > 0) {
                        if (e and 1 == 1) result = mul(result, m)
                        m = mul(m, m)
                        e = e shr 1
                    }
                    // [[1,1],[1,0]]^n = [[F(n+1), F(n)], [F(n), F(n-1)]]
                    return result[0][1]
                }

                fibonacci(10)   // 55 — O(log n) matrix multiplications instead of n additions,
                                //      which is what makes F(10^18) mod p answerable at all
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RecursionTreeVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFFF59E0B, "Cryptography", "RSA private-key operations and Diffie-Hellman are one modular exponentiation with an exponent of hundreds to thousands of bits (RSA public keys usually use e = 65537)."),
        ApplicationCard("stack", 0xFFEC4899, "Linear Recurrences", "Any constant-coefficient recurrence becomes a matrix power, and then O(log n) instead of O(n)."),
        ApplicationCard("bulb", 0xFF3B82F6, "Any Associative Operation", "Composing permutations, transition matrices or graph adjacency n times reuses the same skeleton."),
    ),
    takeaways = listOf(
        "Even exponents square the half-power, odd ones peel off one factor — O(log n) multiplications either way.",
        "Compute the half-power once. Two recursive calls on the same subproblem puts you back at O(n).",
        "The iterative form is the exponent read as binary: one squaring per bit, one multiply per set bit.",
        "It needs only associativity and an identity, which is why matrices give Fibonacci in logarithmic time.",
    ),
    crossLinks = listOf(
        CrossLink("modular_exponentiation", "Modular Exponentiation"),
        CrossLink("fibonacci_dp", "Fibonacci (Dynamic Programming)"),
        CrossLink("strassens_algorithm", "Strassen's Algorithm"),
    ),
)
