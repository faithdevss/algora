package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val modularExponentiationContent = TopicContent(
    topicId = "modular_exponentiation",
    whatIsIt = listOf(
        "Modular exponentiation computes aⁿ mod m without ever forming aⁿ. It is binary exponentiation with one reduction added after every multiplication, and that single addition is what turns an impossible computation into a cheap one — 2^2048 has more digits than there are atoms in the observable universe, while 2^2048 mod m is a few dozen multiplications of numbers no larger than m².",
        "The reduction is legitimate because multiplication commutes with taking remainders: (x·y) mod m depends only on x mod m and y mod m. So every intermediate value can be kept inside 0 … m−1 rather than growing. Following 3¹³ mod 17: 3¹ = 3, 3² = 9, 3⁴ = 9² = 81 ≡ 13, 3⁸ = 13² = 169 ≡ 16. Since 13 = 1101₂, the answer is 3⁸ · 3⁴ · 3¹ = 16 · 13 · 3, and reducing as you go gives 16·13 = 208 ≡ 4, then 4·3 = 12. The unreduced 3¹³ is 1594323, and 1594323 mod 17 is indeed 12 — but nothing above ever held a number larger than 208.",
        "The second thing this unlocks is division. Fermat's little theorem says that for prime p and a not divisible by p, a^(p−1) ≡ 1, so a^(p−2) is a's modular inverse — computable by exactly this routine and nothing else. Mod 17, 3^15 ≡ 6, and 3 × 6 = 18 ≡ 1, so 6 is 3⁻¹. That makes modular exponentiation the workhorse behind binomial coefficients mod a prime, fraction arithmetic in competitive programming, and RSA itself, where encryption and decryption are both a single call to it. Two engineering notes: the intermediate product needs a type twice the modulus's width, since two values just under m multiply to nearly m², and cryptographic implementations must run in constant time, because a version that skips work on zero bits leaks the exponent through timing.",
    ),
    steps = listOf(
        StepCard(1, "Reduce the Base First", "a mod m before anything else, so the very first square is already small.", 0xFFF59E0B),
        StepCard(2, "Square and Reduce", "Each level squares the running base and immediately takes the remainder.", 0xFF3B82F6),
        StepCard(3, "Fold In on Set Bits", "When the exponent's current bit is 1, multiply it into the result and reduce again.", 0xFF10B981),
        StepCard(4, "Halve the Exponent", "Shift right and repeat — ⌊log₂ n⌋ + 1 iterations total.", 0xFF8B5CF6),
        StepCard(5, "Keep Products in a Wider Type", "Two residues near m have a product near m², so the intermediate needs double the width.", 0xFFEC4899),
        StepCard(6, "Invert via Fermat", "For prime m, a⁻¹ = a^(m−2) mod m — one more call to this same routine.", 0xFF06B6D4),
    ),
    formulas = listOf(
        FormulaEntry("Reduction identity", "(x·y) mod m = ((x mod m)·(y mod m)) mod m", "Why reducing early is not an approximation."),
        FormulaEntry("Cost", "O(log n) multiplications", "Each on numbers below m, with products below m²."),
        FormulaEntry("Worked example", "3¹³ ≡ 12 (mod 17)", "3⁴ ≡ 13, 3⁸ ≡ 16; 16·13·3 ≡ 12."),
        FormulaEntry("Fermat inverse", "a⁻¹ ≡ a^(p−2) (mod p)", "Prime p only; 3¹⁵ ≡ 6 mod 17, and 3·6 = 18 ≡ 1."),
        FormulaEntry("Euler's generalisation", "a^φ(m) ≡ 1 (mod m)", "For gcd(a, m) = 1; a⁻¹ = a^(φ(m)−1) when m is composite."),
        FormulaEntry("Intermediate width", "m² must fit", "m near 10⁹ needs 64-bit products; 2048-bit m needs bignum."),
    ),
    notationKey = listOf(
        NotationEntry("a, n, m", "base, exponent and modulus"),
        NotationEntry("φ(m)", "Euler's totient — how many residues below m are coprime to it"),
        NotationEntry("modular inverse", "the a⁻¹ with a·a⁻¹ ≡ 1 (mod m)"),
        NotationEntry("constant-time", "an implementation whose running time does not depend on the exponent's bits"),
        NotationEntry("Montgomery / Barrett", "reduction schemes that replace the division inside the mod with multiplications"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Modular exponentiation",
            accentColor = 0xFFF59E0B,
            code = """
                fun powMod(base: Long, exponent: Long, mod: Long): Long {
                    if (mod == 1L) return 0
                    var result = 1L
                    var b = base % mod            // reduce up front — the first square is then small
                    var e = exponent
                    while (e > 0) {
                        if (e and 1L == 1L) result = result * b % mod
                        b = b * b % mod           // b < mod, so b * b < mod² — must fit the type
                        e = e shr 1
                    }
                    return result
                }

                powMod(3, 13, 17)   // 12
                // 3¹ = 3, 3² = 9, 3⁴ ≡ 13, 3⁸ ≡ 16.  13 = 1101₂, so 3⁸ · 3⁴ · 3¹:
                //   16 × 13 = 208 ≡ 4,  4 × 3 = 12.
                // The unreduced 3¹³ is 1594323; nothing above ever held a value over 208.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The inverse, and what it is for",
            accentColor = 0xFF06B6D4,
            code = """
                const val P = 1_000_000_007L   // prime, so every non-zero residue is invertible

                fun inverse(a: Long, p: Long = P) = powMod(a, p - 2, p)   // Fermat

                inverse(3, 17)   // 6  — because 3¹⁵ ≡ 6 (mod 17) and 3 × 6 = 18 ≡ 1

                // The everyday use: binomial coefficients mod a prime. C(n, k) is a division,
                // and division mod p is multiplication by an inverse.
                fun binomial(n: Int, k: Int): Long {
                    if (k < 0 || k > n) return 0
                    var numerator = 1L
                    var denominator = 1L
                    for (i in 0 until k) {
                        numerator = numerator * ((n - i).toLong() % P) % P
                        denominator = denominator * ((i + 1).toLong() % P) % P
                    }
                    return numerator * inverse(denominator) % P
                }

                // A caution for cryptographic code, not for this: `if (bit set)` makes the
                // running time depend on the exponent, and an attacker timing the operation
                // can read the private key off that. Real implementations do the multiply
                // unconditionally and discard the result on a zero bit.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RecursionTreeVisualizer,
    applications = listOf(
        ApplicationCard("chip", 0xFFF59E0B, "RSA & Diffie-Hellman", "Encryption, decryption and key agreement are each one call to this routine with a huge exponent."),
        ApplicationCard("stack", 0xFF06B6D4, "Combinatorics mod p", "Binomial coefficients, Catalan numbers and any counting answer reported mod 10⁹ + 7."),
        ApplicationCard("bulb", 0xFF3B82F6, "Primality Testing", "Miller-Rabin and the Fermat test are both a sequence of modular exponentiations."),
    ),
    takeaways = listOf(
        "It is binary exponentiation with a reduction after every multiply — that is the whole difference.",
        "Nothing ever grows: every intermediate stays below m, and every product below m².",
        "3¹³ mod 17 = 12, computed without the number 1594323 appearing anywhere.",
        "For prime m, a^(m−2) is the modular inverse, which is how division mod p is done at all.",
    ),
    crossLinks = listOf(
        CrossLink("fast_power", "Fast Power"),
        CrossLink("fermats_little_theorem", "Fermat's Little Theorem"),
        CrossLink("modular_arithmetic", "Modular Arithmetic"),
    ),
)
