package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val sieveOfEratosthenesContent = TopicContent(
    topicId = "sieve_of_eratosthenes",
    figure = Figure(
        caption = "Sieving to 19. Take the smallest unmarked number, cross out its multiples, repeat; " +
            "whatever survives is prime, because a composite has a factor and gets struck the moment " +
            "that factor's turn comes. Two details make it efficient rather than merely correct. " +
            "Marking for a prime p can start at p² — every smaller multiple k·p with k < p was already " +
            "struck when the prime factors of k were processed — and the outer loop stops once p² > n. " +
            "Here that means only 2 and 3 ever run, and 5, 7, 11, 13, 17 and 19 are left standing " +
            "without being examined at all. O(n log log n), close enough to linear that the log log is " +
            "usually invisible. What ends it is memory rather than time: a plain sieve to 10⁹ is a " +
            "gigabyte of booleans, so every practical variant attacks space — a bitset, skipping evens, " +
            "or a segmented sieve running over cache-sized blocks with only the primes below √n.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("2", "3", "4", "5", "6", "7"),
                listOf("8", "9", "10", "11", "12", "13"),
                listOf("14", "15", "16", "17", "18", "19"),
            ),
            marks = listOf(
                FigureCell(0, 0, FigureTone.Accent),
                FigureCell(0, 1, FigureTone.Accent),
                FigureCell(0, 2, FigureTone.Warn),
                FigureCell(0, 3, FigureTone.Accent),
                FigureCell(0, 4, FigureTone.Warn),
                FigureCell(0, 5, FigureTone.Accent),
                FigureCell(1, 0, FigureTone.Warn),
                FigureCell(1, 1, FigureTone.Warn),
                FigureCell(1, 2, FigureTone.Warn),
                FigureCell(1, 3, FigureTone.Accent),
                FigureCell(1, 4, FigureTone.Warn),
                FigureCell(1, 5, FigureTone.Accent),
                FigureCell(2, 0, FigureTone.Warn),
                FigureCell(2, 1, FigureTone.Warn),
                FigureCell(2, 2, FigureTone.Warn),
                FigureCell(2, 3, FigureTone.Accent),
                FigureCell(2, 4, FigureTone.Warn),
                FigureCell(2, 5, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "The sieve lists every prime below n by elimination rather than by testing. Write out the integers, take the smallest unmarked one, cross out all of its multiples, and repeat. Whatever survives is prime, because a composite has a factor and would have been crossed out when that factor's turn came.",
        "Two details make it efficient rather than merely correct. Marking for a prime p can start at p² instead of 2p, because every smaller multiple k·p with k < p was already struck out when the prime factors of k were processed. And the outer loop only needs to run while p·p ≤ n, since any composite below n has a factor no larger than √n — so sieving to 30 means marking multiples of 2, 3 and 5 only, and 7, 11, 13, 17, 19, 23 and 29 are left standing without ever being examined. The result is O(n log log n) time, which is close enough to linear that the log log term is usually invisible in practice.",
        "Where it stops being the right tool is memory. A plain sieve to 10⁹ is a gigabyte of booleans, so the practical variants all attack space rather than time: a bitset cuts it eightfold, skipping even numbers halves it again, and a segmented sieve processes the range in cache-sized blocks using only the primes below √n, which is what makes 10¹² reachable. There is also a linear O(n) sieve that marks each composite exactly once by its smallest prime factor and hands you that factorisation for free — worth it when you need factorisations rather than just a primality flag, though its extra bookkeeping often makes it slower in wall-clock terms than the classic sieve it improves on asymptotically.",
    ),
    steps = listOf(
        StepCard(1, "Assume Everything Is Prime", "A boolean array over 2 … n, all true. 0 and 1 are struck out by definition.", 0xFFF59E0B),
        StepCard(2, "Take the Next Survivor", "The smallest unmarked number is prime — nothing below it divides it.", 0xFF3B82F6),
        StepCard(3, "Mark Multiples from p²", "Every multiple k·p with k < p was already removed by k's own prime factors.", 0xFF10B981),
        StepCard(4, "Stop the Outer Loop at √n", "A composite below n must have a factor ≤ √n, so no larger p can strike anything new.", 0xFF8B5CF6),
        StepCard(5, "Collect the Survivors", "Everything still unmarked is prime; no number was ever divided.", 0xFFEC4899),
        StepCard(6, "Segment When It Will Not Fit", "For huge n, sieve blocks at a time using only the primes below √n.", 0xFF06B6D4),
    ),
    formulas = listOf(
        FormulaEntry("Time", "O(n log log n)", "Σ n/p over primes p ≤ n grows like n log log n."),
        FormulaEntry("Space", "O(n) bits", "A bitset to 10⁹ is 125 MB; a boolean array is a gigabyte."),
        FormulaEntry("Start marking at", "p²", "Smaller multiples were already struck by smaller primes."),
        FormulaEntry("Outer bound", "p·p ≤ n", "To 30, only 2, 3 and 5 ever mark anything."),
        FormulaEntry("Prime counting", "π(n) ≈ n / ln n", "n/ln n gives ≈ 8.8 for n = 30; the actual count is 10 (the estimate undercounts)."),
        FormulaEntry("Linear sieve", "O(n)", "Marks each composite once via its smallest prime factor, and returns it."),
    ),
    notationKey = listOf(
        NotationEntry("π(n)", "the number of primes not exceeding n"),
        NotationEntry("composite", "an integer with a factor other than 1 and itself"),
        NotationEntry("smallest prime factor", "what the linear sieve records per composite, giving factorisation for free"),
        NotationEntry("segmented sieve", "sieving a window [lo, hi] using only the primes below √hi"),
        NotationEntry("bitset", "one bit per candidate instead of one byte — an eightfold memory saving"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The sieve",
            accentColor = 0xFFF59E0B,
            code = """
                fun primesUpTo(n: Int): List<Int> {
                    if (n < 2) return emptyList()
                    val isPrime = BooleanArray(n + 1) { it >= 2 }

                    var p = 2
                    // p * p <= n, not p <= n: beyond √n nothing new can be struck out.
                    while (p.toLong() * p <= n) {
                        if (isPrime[p]) {
                            // Start at p², not 2p — every k·p with k < p already went when k did.
                            var multiple = p * p
                            while (multiple <= n) {
                                isPrime[multiple] = false
                                multiple += p
                            }
                        }
                        p++
                    }
                    return (2..n).filter { isPrime[it] }
                }

                primesUpTo(30)
                // [2, 3, 5, 7, 11, 13, 17, 19, 23, 29]
                // Only 2, 3 and 5 ever marked anything: 7² = 49 is already past 30.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Linear sieve — factorisations for free",
            accentColor = 0xFF06B6D4,
            code = """
                /** Returns (primes, spf) where spf[x] is x's smallest prime factor. */
                fun linearSieve(n: Int): Pair<List<Int>, IntArray> {
                    val spf = IntArray(n + 1)
                    val primes = mutableListOf<Int>()
                    for (i in 2..n) {
                        if (spf[i] == 0) {
                            spf[i] = i
                            primes += i
                        }
                        for (p in primes) {
                            // The break is what makes it linear: each composite is written
                            // exactly once, by its own smallest prime factor.
                            if (p > spf[i] || i.toLong() * p > n) break
                            spf[i * p] = p
                        }
                    }
                    return primes to spf
                }

                // With spf in hand, factorising is a loop of divisions and no trial division:
                fun factorise(x: Int, spf: IntArray): List<Int> {
                    var v = x
                    val factors = mutableListOf<Int>()
                    while (v > 1) {
                        factors += spf[v]
                        v /= spf[v]
                    }
                    return factors
                }
                // factorise(360, spf) → [2, 2, 2, 3, 3, 5]
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.ArrayWalkPlayer,
    applications = listOf(
        ApplicationCard("chip", 0xFFF59E0B, "Cryptographic Key Search", "Small-prime sieving is the first filter before an expensive probabilistic primality test."),
        ApplicationCard("stack", 0xFF06B6D4, "Number-Theoretic Precomputation", "Totients, divisor counts, Möbius values and factorisation tables are all built on one sieve pass."),
        ApplicationCard("bulb", 0xFF3B82F6, "Elimination over Testing", "The template for any \"mark what cannot be\" algorithm, against \"test each candidate\"."),
    ),
    takeaways = listOf(
        "Eliminate rather than test: cross out multiples and whatever survives is prime, with no division anywhere.",
        "Start marking at p² and stop the outer loop at √n — both follow from what smaller primes already removed.",
        "O(n log log n) time and O(n) bits, and it is the memory that limits it in practice, not the time.",
        "Segment for huge ranges; use the linear sieve when you want smallest-prime-factor tables, not just a flag.",
    ),
    crossLinks = listOf(
        CrossLink("fermats_little_theorem", "Fermat's Little Theorem"),
        CrossLink("euclid_gcd", "Euclid's GCD & LCM"),
        CrossLink("bloom_filter", "Bloom Filter"),
    ),
)
