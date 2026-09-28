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

internal val modularArithmeticContent = TopicContent(
    topicId = "modular_arithmetic",
    figure = Figure(
        caption = "Multiplying every residue by 3, mod 7. The result is a permutation of the residues — " +
            "nothing repeats and nothing is missed — so it hits 1 exactly once, at x = 5, and that is " +
            "what makes 5 the inverse of 3. Addition, subtraction and multiplication all survive the " +
            "collapse onto residues intact, which is why reducing at every step is legal and is what " +
            "keeps a long chain of operations inside a machine word. Division is the exception and the " +
            "reason the topic exists: an inverse exists exactly when gcd(a, m) = 1, so mod 4 the value " +
            "2 has none at all, since 2x is always even and can never be 1. Two traps in Kotlin and " +
            "Java: % takes the sign of the dividend, so −6 % 7 is −6 rather than the mathematical 1 " +
            "(use Math.floorMod), and a × b can overflow before the reduction runs, so a large modulus " +
            "needs a wider intermediate type.",
        shape = FigureShape.Strip(
            cells = listOf("0", "1", "2", "3", "4", "5", "6"),
            bands = listOf(FigureBand(5, 5, "3⁻¹ = 5", FigureTone.Accent)),
            aux = listOf("0", "3", "6", "2", "5", "1", "4"),
            auxLabel = "3 · x mod 7 — a permutation of the residues, landing on 1 exactly once",
        ),
    ),
    whatIsIt = listOf(
        "Modular arithmetic is arithmetic on a clock. Working modulo m, every integer collapses onto one of m residues, and two numbers are treated as the same when they differ by a multiple of m. Twelve-hour time is arithmetic mod 12; a hash bucket index is arithmetic mod the table size.",
        "Addition, subtraction and multiplication all survive the collapse intact — that is the point. Because (a + b) mod m depends only on a mod m and b mod m, you can reduce at every step instead of at the end, which is what keeps a long chain of operations inside a machine word. Mod 7: 17 ≡ 3 and 23 ≡ 2, so 17 + 23 = 40 ≡ 5 and 3 + 2 = 5 agree, and 17 × 23 = 391 ≡ 6 matches 3 × 2 = 6. The property has a name, congruence is compatible with the ring operations, and it is why competitive programming answers are given \"mod 10⁹ + 7\" without loss.",
        "Division is the exception and the reason this topic exists. There is no residue that always plays the role of ½: dividing by a means multiplying by an inverse a⁻¹ with a·a⁻¹ ≡ 1, and such an inverse exists exactly when gcd(a, m) = 1. Mod 7, 3⁻¹ = 5 because 15 ≡ 1. Mod 4, 2 has no inverse at all, since 2x is always even and can never be 1. Two practical traps follow. In Kotlin and Java, % takes the sign of the dividend, so −6 % 7 is −6 rather than the mathematical 1; use ((a % m) + m) % m or Math.floorMod. And a × b can overflow before the reduction happens, so intermediate products need a wider type when m is large.",
    ),
    steps = listOf(
        StepCard(1, "Collapse to Residues", "Every integer maps to one of 0 … m−1; numbers differing by a multiple of m are the same.", 0xFFF59E0B),
        StepCard(2, "Reduce as You Go", "Addition and multiplication commute with the reduction, so reduce at every step, not at the end.", 0xFF3B82F6),
        StepCard(3, "Fix the Sign", "% keeps the dividend's sign in Kotlin. Normalise with ((a % m) + m) % m before comparing.", 0xFFEF4444),
        StepCard(4, "Widen Before Multiplying", "Two values below m can still overflow Int when multiplied — promote to Long first.", 0xFFEC4899),
        StepCard(5, "Divide by Inverting", "a / b means a × b⁻¹, and b⁻¹ exists only when gcd(b, m) = 1.", 0xFF10B981),
        StepCard(6, "Find the Inverse", "Extended Euclid for any modulus; b^(m−2) by Fermat when m is prime.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Congruence", "a ≡ b (mod m) ⟺ m | (a − b)", "The definition everything else follows from."),
        FormulaEntry("Distributes over + − ×", "(a ∘ b) mod m = ((a mod m) ∘ (b mod m)) mod m", "Exactly the operations that survive."),
        FormulaEntry("Inverse", "a·a⁻¹ ≡ 1 (mod m)", "Exists ⟺ gcd(a, m) = 1."),
        FormulaEntry("Worked example", "3⁻¹ ≡ 5 (mod 7)", "3 × 5 = 15 = 2×7 + 1."),
        FormulaEntry("No inverse", "2 has none mod 4", "gcd(2, 4) = 2; 2x is always even."),
        FormulaEntry("Negative remainder", "((a % m) + m) % m", "Kotlin's % takes the dividend's sign: −6 % 7 = −6."),
    ),
    notationKey = listOf(
        NotationEntry("m", "the modulus — the size of the clock"),
        NotationEntry("residue", "the canonical representative in 0 … m−1"),
        NotationEntry("a ≡ b (mod m)", "a and b differ by a multiple of m"),
        NotationEntry("a⁻¹", "modular inverse — the residue with a·a⁻¹ ≡ 1"),
        NotationEntry("10⁹ + 7", "the usual prime modulus: prime, so every non-zero residue is invertible, and small enough that products fit in 64 bits"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Safe modular operations",
            accentColor = 0xFFF59E0B,
            code = """
                const val MOD = 1_000_000_007L

                // Kotlin's % takes the sign of the dividend: (-6) % 7 == -6, not 1.
                fun norm(a: Long, m: Long = MOD) = ((a % m) + m) % m

                fun addMod(a: Long, b: Long, m: Long = MOD) = norm(a + b, m)
                fun subMod(a: Long, b: Long, m: Long = MOD) = norm(a - b, m)
                fun mulMod(a: Long, b: Long, m: Long = MOD) = norm(a % m * (b % m), m)

                // Mod 7, with 17 ≡ 3 and 23 ≡ 2:
                addMod(17, 23, 7)   // 5  — and (3 + 2) % 7 = 5
                mulMod(17, 23, 7)   // 6  — and (3 × 2) % 7 = 6
                subMod(17, 23, 7)   // 1  — the raw -6 % 7 would have been -6
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Division, and when it is not available",
            accentColor = 0xFF10B981,
            code = """
                // Dividing by a means multiplying by a's inverse — which may not exist.
                fun modInverse(a: Long, m: Long): Long? {
                    // Extended Euclid: works for any modulus, prime or not.
                    var (oldR, r) = a % m to m
                    var (oldS, s) = 1L to 0L
                    while (r != 0L) {
                        val q = oldR / r
                        oldR = r.also { r = oldR - q * r }
                        oldS = s.also { s = oldS - q * s }
                    }
                    return if (oldR != 1L) null else ((oldS % m) + m) % m
                }

                modInverse(3, 7)    // 5     — 3 × 5 = 15 ≡ 1 (mod 7)
                modInverse(2, 4)    // null  — gcd(2, 4) = 2

                // When the modulus is prime, Fermat gives the inverse directly as a^(m-2),
                // which needs no extended Euclid — just modular exponentiation.
                fun inversePrime(a: Long, p: Long) = powMod(a, p - 2, p)

                // Overflow, the other everyday trap: with MOD near 10^9, two residues each
                // below MOD have a product near 10^18. That fits in Long and would not have
                // fit in Int, so the operands must be widened *before* the multiply.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFFF59E0B, "Hashing & Sharding", "Bucket selection, consistent hashing rings and checksum digits are all reductions mod a table size."),
        ApplicationCard("finance", 0xFF3B82F6, "Cryptography", "RSA, Diffie-Hellman and elliptic-curve arithmetic live entirely inside a modulus."),
        ApplicationCard("bulb", 0xFF10B981, "Overflow Control", "Answers reported \"mod 10⁹ + 7\" so an exponentially large count still fits in a machine word."),
    ),
    takeaways = listOf(
        "Addition, subtraction and multiplication commute with the reduction — so reduce at every step.",
        "Division does not: it means multiplying by an inverse, which exists only when gcd(a, m) = 1.",
        "Kotlin's % keeps the dividend's sign, so normalise with ((a % m) + m) % m before comparing or indexing.",
        "Widen to Long before multiplying: two residues below 10⁹ have a product near 10¹⁸.",
    ),
    crossLinks = listOf(
        CrossLink("euclid_gcd", "Euclid's GCD & LCM"),
        CrossLink("modular_exponentiation", "Modular Exponentiation"),
        CrossLink("hash_table", "Hash Table"),
    ),
)
