package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val fermatsLittleTheoremContent = TopicContent(
    topicId = "fermats_little_theorem",
    whatIsIt = listOf(
        "Fermat's little theorem states that for a prime p and any a not divisible by p, a^(p−1) ≡ 1 (mod p). Mod 7 that means 2⁶, 3⁶, 4⁶, 5⁶ and 6⁶ are all congruent to 1 — 3⁶ is 729, which is 7 × 104 + 1. It is a small statement with two large consequences: it is how division works modulo a prime, and it is the seed of every practical primality test.",
        "The proof is a counting argument that explains why the exponent is p − 1 specifically. Multiplying every non-zero residue by a permutes them: the list a·1, a·2, …, a·(p−1) contains no repeats and no zero, so it is the same set as 1, 2, …, p−1 in some order. Multiply each list out and the products must agree, giving a^(p−1)·(p−1)! ≡ (p−1)!. Since (p−1)! is invertible mod p — none of its factors is divisible by p — it cancels, leaving a^(p−1) ≡ 1. Dividing by a once more gives the form that gets used in code: a^(p−2) is a's modular inverse. Mod 7, 3⁵ = 243 ≡ 5, and 3 × 5 = 15 ≡ 1.",
        "Read backwards, the theorem is a primality test — if a^(n−1) ≢ 1 (mod n) then n is definitely composite — and read backwards it is also unreliable. Composites exist that pass for every base coprime to them, and the smallest is 561 = 3 × 11 × 17. Korselt's criterion explains it: 561 is squarefree and p − 1 divides 560 for each of its prime factors, since 560 is divisible by 2, 10 and 16. Such numbers are called Carmichael numbers, there are infinitely many of them, and no amount of trying more bases will catch one. Miller-Rabin fixes this by extracting square roots of 1 along the way — a prime has only ±1 as square roots of 1, so a non-trivial one is a proof of compositeness — and no Carmichael-like universal liar survives it. Euler's theorem generalises Fermat's to composite moduli: a^φ(m) ≡ 1 whenever gcd(a, m) = 1, which is the version RSA's correctness actually rests on.",
    ),
    steps = listOf(
        StepCard(1, "Require a Prime Modulus", "p prime and p ∤ a. Both conditions are load-bearing.", 0xFFF59E0B),
        StepCard(2, "Multiply the Residues by a", "a·1 … a·(p−1) is a permutation of 1 … p−1 — no repeats, no zero.", 0xFF3B82F6),
        StepCard(3, "Cancel the Factorial", "Both products equal (p−1)!, which is invertible, so a^(p−1) ≡ 1.", 0xFF10B981),
        StepCard(4, "Divide Once More for the Inverse", "a^(p−2) ≡ a⁻¹, computed by modular exponentiation in O(log p).", 0xFF06B6D4),
        StepCard(5, "Use It as a Composite Test", "a^(n−1) ≢ 1 proves n composite. The converse proves nothing.", 0xFF8B5CF6),
        StepCard(6, "Know Where It Fails", "561 = 3·11·17 passes for every coprime base. Use Miller-Rabin instead.", 0xFFEF4444),
    ),
    formulas = listOf(
        FormulaEntry("Fermat", "a^(p−1) ≡ 1 (mod p)", "p prime, p ∤ a. 3⁶ = 729 = 7·104 + 1."),
        FormulaEntry("Alternative form", "aᵖ ≡ a (mod p)", "Holds for every a, including multiples of p."),
        FormulaEntry("Inverse", "a⁻¹ ≡ a^(p−2) (mod p)", "3⁵ ≡ 5 mod 7, and 3 × 5 = 15 ≡ 1."),
        FormulaEntry("Euler", "a^φ(m) ≡ 1 (mod m)", "For gcd(a, m) = 1 — the composite-modulus version RSA uses."),
        FormulaEntry("Carmichael", "561 = 3 × 11 × 17", "Squarefree, and 2 | 560, 10 | 560, 16 | 560 — Korselt's criterion."),
        FormulaEntry("Miller-Rabin", "error ≤ 4^(−k)", "k random bases; no composite survives it the way Carmichaels survive Fermat."),
    ),
    notationKey = listOf(
        NotationEntry("p ∤ a", "p does not divide a — the theorem's precondition"),
        NotationEntry("φ(m)", "Euler's totient: how many of 1 … m are coprime to m; φ(p) = p − 1"),
        NotationEntry("Fermat witness", "a base a proving n composite because a^(n−1) ≢ 1"),
        NotationEntry("Carmichael number", "a composite passing the Fermat test for every coprime base"),
        NotationEntry("Korselt's criterion", "n is Carmichael ⟺ n is squarefree and p − 1 | n − 1 for every prime p | n"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The theorem, and the inverse it gives",
            accentColor = 0xFFF59E0B,
            code = """
                // Mod 7, every non-zero base raised to the 6th comes back to 1:
                (2..6).map { powMod(it.toLong(), 6, 7) }   // [1, 1, 1, 1, 1]
                //   3⁶ = 729 = 7 × 104 + 1
                //   5⁶ = 15625 = 7 × 2232 + 1

                // Drop the exponent by one and you have division mod a prime.
                fun inverse(a: Long, p: Long) = powMod(a, p - 2, p)

                inverse(3, 7)    // 5  — 3 × 5 = 15 ≡ 1 (mod 7)
                inverse(3, 17)   // 6  — 3 × 6 = 18 ≡ 1 (mod 17)

                // This is the only reason a competitive-programming answer can be a fraction
                // "mod 10⁹ + 7": the modulus is prime, so every non-zero residue is invertible.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The primality test, and the number that defeats it",
            accentColor = 0xFFEF4444,
            code = """
                fun fermatTest(n: Long, rounds: Int = 20): Boolean {
                    if (n < 4) return n == 2L || n == 3L
                    repeat(rounds) {
                        val a = (2 until n - 1).random()
                        if (powMod(a, n - 1, n) != 1L) return false   // a proof of compositeness
                    }
                    return true                                       // NOT a proof of primality
                }

                // 561 = 3 × 11 × 17 passes for every base coprime to it, no matter how many
                // rounds you run. Korselt: 561 is squarefree, and each prime factor's p - 1
                // divides 560 — 2 | 560, 10 | 560, 16 | 560.
                fermatTest(561)   // true. It is composite.

                // Miller-Rabin closes the hole by looking at square roots of 1 on the way up:
                // modulo a prime the only square roots of 1 are ±1, so finding any other one
                // is a certificate of compositeness that no Carmichael number can dodge.
                fun millerRabin(n: Long, rounds: Int = 20): Boolean {
                    if (n < 2) return false
                    for (p in listOf(2L, 3L, 5L, 7L, 11L, 13L)) {
                        if (n % p == 0L) return n == p
                    }
                    var d = n - 1
                    var r = 0
                    while (d % 2 == 0L) { d /= 2; r++ }        // n - 1 = d · 2^r, d odd
                    repeat(rounds) {
                        val a = (2 until n - 1).random()
                        var x = powMod(a, d, n)
                        if (x == 1L || x == n - 1) return@repeat
                        var composite = true
                        repeat(r - 1) {
                            x = x * x % n
                            if (x == n - 1) { composite = false; return@repeat }
                        }
                        if (composite) return false
                    }
                    return true
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFFF59E0B, "RSA", "Correctness of RSA decryption is Euler's theorem, the composite-modulus form of this one."),
        ApplicationCard("stack", 0xFF06B6D4, "Division mod a Prime", "Modular inverses for binomial coefficients, probabilities and any fraction reported mod 10⁹ + 7."),
        ApplicationCard("bulb", 0xFFEF4444, "Primality Screening", "A cheap first filter before an expensive test — and a cautionary tale about one-way implications."),
    ),
    takeaways = listOf(
        "a^(p−1) ≡ 1 mod a prime p, because multiplying by a merely permutes the non-zero residues.",
        "One exponent lower gives the modular inverse: a^(p−2) ≡ a⁻¹, which is how division mod p is performed.",
        "Failing the test proves compositeness; passing it proves nothing — 561 passes for every coprime base.",
        "Carmichael numbers are infinite in supply, so use Miller-Rabin, which tests square roots of 1 as well.",
    ),
    crossLinks = listOf(
        CrossLink("modular_exponentiation", "Modular Exponentiation"),
        CrossLink("sieve_of_eratosthenes", "Sieve of Eratosthenes"),
        CrossLink("chinese_remainder_theorem", "Chinese Remainder Theorem"),
    ),
)
