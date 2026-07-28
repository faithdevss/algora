package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val euclidGcdContent = TopicContent(
    topicId = "euclid_gcd",
    whatIsIt = listOf(
        "Euclid's algorithm finds the greatest common divisor of two integers by replacing the larger with its remainder against the smaller, over and over, until one of them is zero. The other is the answer. It appears in the Elements around 300 BC and is the oldest algorithm still in everyday use.",
        "The correctness rests on one identity: gcd(a, b) = gcd(b, a mod b). Any number dividing both a and b also divides a − qb for any q, so the pair (a, b) and the pair (b, a mod b) have exactly the same set of common divisors — not merely the same greatest one. Each step therefore preserves the answer while shrinking the numbers, and since remainders strictly decrease, the process terminates. gcd(252, 105) goes 252, 105 → 105, 42 → 42, 21 → 21, 0, so the answer is 21.",
        "It is also fast, and the reason is worth knowing. Two successive remainders always sum to at most the original larger value, which forces the pair to at least halve every two steps, so the step count is O(log min(a, b)). Lamé's theorem sharpens that: the number of divisions never exceeds five times the number of decimal digits in the smaller input, and the worst case is exactly a pair of consecutive Fibonacci numbers — gcd(F(n+1), F(n)) takes n − 1 steps, because each remainder step lands on the previous Fibonacci number. Extending the algorithm to also track coefficients gives Bézout's identity, integers x and y with ax + by = gcd(a, b), and that extension is what produces modular inverses and drives the Chinese Remainder Theorem and RSA key generation.",
    ),
    steps = listOf(
        StepCard(1, "Take the Remainder", "Replace (a, b) with (b, a mod b). The set of common divisors is unchanged.", 0xFFF59E0B),
        StepCard(2, "Repeat Until Zero", "Remainders strictly decrease, so this terminates — and quickly.", 0xFF3B82F6),
        StepCard(3, "Read the Non-Zero Value", "When b hits zero, a is the greatest common divisor.", 0xFF10B981),
        StepCard(4, "Derive the LCM", "lcm(a, b) = a / gcd(a, b) × b. Divide before multiplying to avoid overflow.", 0xFF8B5CF6),
        StepCard(5, "Track Coefficients for Bézout", "Carrying x and y through the recursion yields ax + by = gcd(a, b).", 0xFFEC4899),
        StepCard(6, "Invert When Coprime", "If gcd(a, m) = 1, Bézout's x is a's modular inverse mod m.", 0xFF06B6D4),
    ),
    formulas = listOf(
        FormulaEntry("Identity", "gcd(a, b) = gcd(b, a mod b)", "Both pairs have the same common divisors, not just the same greatest."),
        FormulaEntry("Base case", "gcd(a, 0) = a", "Every integer divides 0."),
        FormulaEntry("Steps", "O(log min(a, b))", "The pair at least halves every two steps."),
        FormulaEntry("Lamé's bound", "≤ 5 × decimal digits of min(a, b)", "Worst case is consecutive Fibonacci numbers."),
        FormulaEntry("LCM", "a / gcd(a, b) × b", "Divide first; a × b can overflow when the result does not."),
        FormulaEntry("Bézout", "ax + by = gcd(a, b)", "252·(−2) + 105·5 = 21."),
    ),
    notationKey = listOf(
        NotationEntry("gcd(a, b)", "greatest common divisor — the largest integer dividing both"),
        NotationEntry("lcm(a, b)", "least common multiple"),
        NotationEntry("coprime", "gcd = 1; the condition for a modular inverse to exist"),
        NotationEntry("Bézout coefficients", "the x, y with ax + by = gcd(a, b)"),
        NotationEntry("a mod b", "the remainder of a divided by b"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Euclid's algorithm, and the LCM that follows",
            accentColor = 0xFFF59E0B,
            code = """
                tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)

                // Divide before multiplying: a and b can each fit in a Long while a * b does not,
                // and the division is always exact because gcd(a, b) divides a.
                fun lcm(a: Long, b: Long): Long = a / gcd(a, b) * b

                gcd(252, 105)   // 21   — 252,105 → 105,42 → 42,21 → 21,0
                lcm(252, 105)   // 1260 — 252 / 21 * 105

                // The worst case is consecutive Fibonacci numbers, because every remainder step
                // lands exactly on the previous one: gcd(F(n+1), F(n)) takes n - 1 divisions.
                gcd(89, 55)     // 1, in 9 steps — no smaller pair needs that many
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Extended Euclid and the modular inverse",
            accentColor = 0xFF06B6D4,
            code = """
                /** Returns (g, x, y) with a·x + b·y = g = gcd(a, b). */
                fun extendedGcd(a: Long, b: Long): Triple<Long, Long, Long> {
                    if (b == 0L) return Triple(a, 1, 0)
                    val (g, x1, y1) = extendedGcd(b, a % b)
                    // Unwinding: a·x + b·y = b·x1 + (a - (a/b)·b)·y1
                    return Triple(g, y1, x1 - (a / b) * y1)
                }

                extendedGcd(252, 105)   // (21, -2, 5)  →  252·(-2) + 105·5 = -504 + 525 = 21

                /** a's inverse mod m, or null when gcd(a, m) != 1 and no inverse exists. */
                fun modInverse(a: Long, m: Long): Long? {
                    val (g, x, _) = extendedGcd(a, m)
                    return if (g != 1L) null else ((x % m) + m) % m   // normalise into 0 until m
                }

                modInverse(3, 7)    // 5  — 3·5 = 15 ≡ 1 (mod 7)
                modInverse(2, 4)    // null — gcd(2, 4) = 2, so 2x is never odd mod 4
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFFF59E0B, "Cryptography", "RSA key generation needs the modular inverse that extended Euclid produces, and needs it fast."),
        ApplicationCard("finance", 0xFF3B82F6, "Exact Fractions", "Reducing a rational to lowest terms is a gcd, which is why every arbitrary-precision library ships one."),
        ApplicationCard("bulb", 0xFF10B981, "Scheduling & Periodicity", "When two cycles realign is an LCM; gear ratios, calendar cycles and signal periods all reduce to it."),
    ),
    takeaways = listOf(
        "gcd(a, b) = gcd(b, a mod b) — each step keeps the whole set of common divisors and shrinks the numbers.",
        "O(log min(a, b)) steps, with consecutive Fibonacci numbers as the exact worst case.",
        "Compute the LCM by dividing before multiplying; a × b overflows for inputs whose LCM does not.",
        "The extended version returns Bézout coefficients, and that is where modular inverses, CRT and RSA all start.",
    ),
    crossLinks = listOf(
        CrossLink("modular_arithmetic", "Modular Arithmetic"),
        CrossLink("chinese_remainder_theorem", "Chinese Remainder Theorem"),
        CrossLink("fibonacci_recursive", "Fibonacci"),
    ),
)
