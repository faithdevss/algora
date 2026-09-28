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

internal val karatsubasAlgorithmContent = TopicContent(
    topicId = "karatsubas_algorithm",
    figure = Figure(
        caption = "Split 1234 into a = 12 and b = 34 (and the other operand into c and d). The obvious " +
            "expansion wants four half-size products — ac, ad, bc, bd — and T(n) = 4T(n/2) + O(n) is " +
            "still O(n²), so splitting alone changes nothing. Karatsuba never computes ad and bc " +
            "separately: their sum falls out of (a+b)(c+d) once ac and bd are subtracted off, which " +
            "leaves three recursive multiplies and a handful of additions, giving n^log₂3 ≈ n^1.585. The " +
            "extra additions are why it only pays above a few hundred digits — below that the schoolbook " +
            "loop wins.",
        shape = FigureShape.Strip(
            cells = listOf("1", "2", "3", "4"),
            bands = listOf(
                FigureBand(0, 1, "a = 12"),
                FigureBand(2, 3, "b = 34", FigureTone.Accent),
            ),
            aux = listOf("ac", "(a+b)(c+d)", "bd"),
            auxLabel = "three products — ad + bc is recovered as (a+b)(c+d) − ac − bd, never multiplied for",
        ),
    ),
    whatIsIt = listOf(
        "Karatsuba's algorithm multiplies two large numbers faster than the schoolbook O(n²) method by cutting the count of recursive multiplications from four to three.",
        "Splitting each number into high and low halves would normally need four sub-products; Karatsuba's insight computes the cross term from just one extra multiplication, giving sub-quadratic time.",
    ),
    steps = listOf(
        StepCard(1, "Split in Half", "Write x = a·B + b and y = c·B + d, where B is the base raised to half the digit count.", 0xFF10B981),
        StepCard(2, "Three Products", "Compute ac, bd, and (a+b)(c+d) — three multiplications instead of four.", 0xFF3B82F6),
        StepCard(3, "Recover the Middle", "The cross term ad + bc = (a+b)(c+d) − ac − bd, no fourth product needed.", 0xFFF59E0B),
        StepCard(4, "Combine", "x·y = ac·B² + (ad+bc)·B + bd; recurse on the three subproducts.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Recurrence", "T(n) = 3·T(n/2) + O(n)", "Three half-size multiplications plus linear glue."),
        FormulaEntry("Time", "O(n^1.585)", "n^log₂3 — sub-quadratic, beating schoolbook O(n²)."),
        FormulaEntry("Middle term", "(a+b)(c+d) − ac − bd", "The trick that removes the fourth multiply."),
    ),
    notationKey = listOf(
        NotationEntry("n", "number of digits/bits in each operand"),
        NotationEntry("B", "the split base (half-length shift)"),
        NotationEntry("a,b / c,d", "high and low halves of x and y"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Karatsuba multiplication",
            accentColor = 0xFF6366F1,
            code = """
                fun karatsuba(x: Long, y: Long): Long {
                    if (x < 10 || y < 10) return x * y          // base case
                    val n = maxOf(x.toString().length, y.toString().length)
                    val half = n / 2
                    val p = Math.pow(10.0, half.toDouble()).toLong()
                    val a = x / p; val b = x % p
                    val c = y / p; val d = y % p
                    val ac = karatsuba(a, c)
                    val bd = karatsuba(b, d)
                    val cross = karatsuba(a + b, c + d) - ac - bd
                    return ac * p * p + cross * p + bd
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.RecursionTreeVisualizer,
    applications = listOf(
        ApplicationCard("finance", 0xFF10B981, "Big-Integer Libraries", "Arbitrary-precision arithmetic (crypto keys, GMP, Java BigInteger) uses Karatsuba for mid-size numbers."),
        ApplicationCard("lock", 0xFF3B82F6, "Cryptography", "RSA and other schemes multiply thousand-bit integers where the speedup matters."),
        ApplicationCard("flask", 0xFFF59E0B, "Polynomial Multiplication", "The same three-product split accelerates multiplying polynomials, not just integers."),
    ),
    takeaways = listOf(
        "Karatsuba trades one multiplication for a few additions, cutting 4 subproducts to 3.",
        "That gives O(n^1.585), the first practical sub-quadratic multiplication.",
        "Its recurrence T(n)=3T(n/2)+O(n) is a textbook Master-Theorem example.",
        "For very large numbers, FFT-based methods overtake it — Karatsuba wins the middle range.",
    ),
    crossLinks = listOf(
        CrossLink("strassens_algorithm", "Strassen's Algorithm"),
        CrossLink("merge_sort", "Merge Sort"),
    ),
)
