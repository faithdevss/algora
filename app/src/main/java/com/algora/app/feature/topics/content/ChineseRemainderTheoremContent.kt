package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val chineseRemainderTheoremContent = TopicContent(
    topicId = "chinese_remainder_theorem",
    whatIsIt = listOf(
        "The Chinese Remainder Theorem says that a system of congruences with pairwise coprime moduli always has a solution, and that the solution is unique modulo the product of those moduli. Sunzi's third-century puzzle is the standard statement of it: a number leaves remainder 2 when divided by 3, 3 when divided by 5, and 2 when divided by 7 — what is it? The answer is 23, and every other answer differs from 23 by a multiple of 105.",
        "The construction is direct rather than a search. With M = 3 × 5 × 7 = 105, form Mᵢ = M / mᵢ for each modulus: 35, 21 and 15. Each Mᵢ is divisible by every modulus except its own, so multiplying it by the inverse of Mᵢ modulo mᵢ produces a number that is 1 mod mᵢ and 0 mod all the others — a basis vector, in effect. Here 35 ≡ 2 (mod 3) and 2⁻¹ ≡ 2, while 21 ≡ 1 (mod 5) and 15 ≡ 1 (mod 7), so the sum is 2·35·2 + 3·21·1 + 2·15·1 = 140 + 63 + 30 = 233, and 233 mod 105 = 23. Each term contributes its own remainder to its own modulus and contributes nothing anywhere else.",
        "The deeper reading is that CRT is an isomorphism: the residues mod M correspond one-to-one with tuples of residues mod each mᵢ, and addition and multiplication agree on both sides. That makes it a tool for splitting a big computation into small independent ones. RSA implementations decrypt modulo p and modulo q separately and recombine, which is roughly four times faster than working modulo pq directly. Coprimality is the hypothesis that carries everything: with moduli 4 and 6 the theorem does not apply, and x ≡ 1 (mod 4) with x ≡ 2 (mod 6) has no solution at all, since the first forces x odd and the second forces it even. The general version handles non-coprime moduli by requiring the remainders to agree on each pair's gcd, and merges them one at a time.",
    ),
    steps = listOf(
        StepCard(1, "Check Pairwise Coprimality", "Every pair of moduli must have gcd 1, or the theorem does not apply at all.", 0xFFF59E0B),
        StepCard(2, "Form the Product M", "M = ∏ mᵢ. The answer will be unique modulo M and no smaller.", 0xFF3B82F6),
        StepCard(3, "Build Mᵢ = M / mᵢ", "Divisible by every modulus except its own — that is what isolates each congruence.", 0xFF10B981),
        StepCard(4, "Invert Mᵢ Modulo mᵢ", "Extended Euclid gives yᵢ with Mᵢ·yᵢ ≡ 1 (mod mᵢ).", 0xFF06B6D4),
        StepCard(5, "Sum the Basis Terms", "x = Σ aᵢ·Mᵢ·yᵢ. Each term is aᵢ mod mᵢ and 0 mod everything else.", 0xFF8B5CF6),
        StepCard(6, "Reduce Modulo M", "233 mod 105 = 23 — the unique representative below M.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Statement", "x ≡ aᵢ (mod mᵢ) has a unique solution mod ∏ mᵢ", "Provided the mᵢ are pairwise coprime."),
        FormulaEntry("Construction", "x = Σ aᵢ · Mᵢ · yᵢ mod M", "Mᵢ = M / mᵢ, yᵢ = Mᵢ⁻¹ mod mᵢ."),
        FormulaEntry("Sunzi's example", "x ≡ 2 (3), 3 (5), 2 (7) → x ≡ 23 (mod 105)", "2·35·2 + 3·21·1 + 2·15·1 = 233 ≡ 23."),
        FormulaEntry("Cost", "O(k log M)", "One extended-Euclid inverse per congruence."),
        FormulaEntry("Isomorphism", "ℤ/M ≅ ℤ/m₁ × … × ℤ/m_k", "Addition and multiplication agree on both sides."),
        FormulaEntry("Non-coprime case", "solvable ⟺ aᵢ ≡ aⱼ (mod gcd(mᵢ, mⱼ))", "x ≡ 1 (4), x ≡ 2 (6) fails: one forces odd, the other even."),
    ),
    notationKey = listOf(
        NotationEntry("mᵢ, aᵢ", "the moduli and the remainders they are paired with"),
        NotationEntry("M", "the product of all the moduli — the period of the solution"),
        NotationEntry("Mᵢ", "M / mᵢ — zero modulo every other modulus"),
        NotationEntry("yᵢ", "the inverse of Mᵢ modulo mᵢ, from extended Euclid"),
        NotationEntry("pairwise coprime", "every pair has gcd 1 — stronger than the whole set having gcd 1"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The classical construction",
            accentColor = 0xFFF59E0B,
            code = """
                /** Solves x ≡ remainders[i] (mod moduli[i]); the moduli must be pairwise coprime. */
                fun crt(remainders: List<Long>, moduli: List<Long>): Long {
                    val product = moduli.reduce(Long::times)
                    var x = 0L
                    for (i in moduli.indices) {
                        val partial = product / moduli[i]                  // 0 mod every other modulus
                        val inverse = modInverse(partial % moduli[i], moduli[i])!!
                        x = (x + remainders[i] * partial % product * inverse) % product
                    }
                    return (x % product + product) % product
                }

                crt(listOf(2, 3, 2), listOf(3, 5, 7))   // 23
                // M = 105;  M₁ = 35, M₂ = 21, M₃ = 15
                // 35 ≡ 2 (mod 3), 2⁻¹ ≡ 2   →  2 · 35 · 2 = 140
                // 21 ≡ 1 (mod 5), 1⁻¹ = 1   →  3 · 21 · 1 =  63
                // 15 ≡ 1 (mod 7), 1⁻¹ = 1   →  2 · 15 · 1 =  30
                // 140 + 63 + 30 = 233,  233 mod 105 = 23
                // And 23 mod 3 = 2, 23 mod 5 = 3, 23 mod 7 = 2.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Merging two at a time, coprime or not",
            accentColor = 0xFF06B6D4,
            code = """
                /**
                 * Merges x ≡ a1 (mod m1) and x ≡ a2 (mod m2) without assuming coprimality.
                 * Returns (remainder, lcm) or null when the two congruences contradict.
                 */
                fun merge(a1: Long, m1: Long, a2: Long, m2: Long): Pair<Long, Long>? {
                    val (g, p, _) = extendedGcd(m1, m2)
                    // Solvable exactly when the two remainders already agree modulo gcd(m1, m2).
                    if ((a2 - a1) % g != 0L) return null

                    val lcm = m1 / g * m2
                    val step = (a2 - a1) / g
                    val k = (step % (m2 / g)) * (p % (m2 / g)) % (m2 / g)
                    val result = ((a1 + m1 * k) % lcm + lcm) % lcm
                    return result to lcm
                }

                merge(2, 3, 3, 5)   // (8, 15)   — 8 mod 3 = 2, 8 mod 5 = 3
                merge(1, 4, 2, 6)   // null      — gcd(4, 6) = 2, and 2 - 1 = 1 is not a multiple of 2.
                                    //             The first forces x odd, the second forces it even.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFFF59E0B, "RSA Decryption", "Decrypting mod p and mod q separately and recombining is roughly four times faster than working mod pq."),
        ApplicationCard("stack", 0xFF06B6D4, "Big-Integer Arithmetic", "Residue number systems compute on several small moduli in parallel and reassemble at the end."),
        ApplicationCard("bulb", 0xFF3B82F6, "Cycle Alignment", "When several periodic schedules next coincide is the same system of congruences."),
    ),
    takeaways = listOf(
        "Pairwise coprime moduli always admit a solution, unique modulo their product — 23 mod 105 for Sunzi's puzzle.",
        "Each Mᵢ = M / mᵢ is zero modulo every other modulus, which is what lets the terms be summed independently.",
        "It is an isomorphism, so a computation mod M can be split into independent computations mod each mᵢ.",
        "Drop coprimality and solvability becomes conditional: the remainders must agree modulo each pair's gcd.",
    ),
    crossLinks = listOf(
        CrossLink("euclid_gcd", "Euclid's GCD & LCM"),
        CrossLink("modular_arithmetic", "Modular Arithmetic"),
        CrossLink("fermats_little_theorem", "Fermat's Little Theorem"),
    ),
)
