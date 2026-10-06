package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.sqrt

// ── D1 · Statistical NLP math ────────────────────────────────────────────────
// The PCFG lab runs on this: the copy quotes what this file computes, and `D1MathTest` pins the
// property the copy leans on — that the PP-attachment parse really is the one CYK prefers.

// ── Probabilistic context-free grammar ───────────────────────────────────────

internal object PcfgLab {

    val sentence = listOf("she", "saw", "the", "man", "with", "the", "telescope")

    /** Binary rules A → B C, in Chomsky Normal Form, with their probabilities. */
    val binary: List<Triple<String, Pair<String, String>, Double>> = listOf(
        Triple("S", "NP" to "VP", 1.0),
        Triple("VP", "V" to "NP", 0.70),
        Triple("VP", "VP" to "PP", 0.30),
        Triple("NP", "Det" to "N", 0.40),
        Triple("NP", "NP" to "PP", 0.20),
        Triple("PP", "P" to "NP", 1.00),
    )

    /** Lexical rules A → word. */
    val lexical: List<Triple<String, String, Double>> = listOf(
        Triple("NP", "she", 0.40),
        Triple("V", "saw", 1.00),
        Triple("Det", "the", 1.00),
        Triple("N", "man", 0.50),
        Triple("N", "telescope", 0.50),
        Triple("P", "with", 1.00),
    )

    class Parse(val label: String, val probability: Double, val left: Parse? = null, val right: Parse? = null, val word: String? = null)

    /** The bracketed form the lab prints, so two readings can be compared as text. */
    fun bracket(parse: Parse): String =
        if (parse.word != null) "(${parse.label} ${parse.word})"
        else "(${parse.label} ${bracket(parse.left!!)} ${bracket(parse.right!!)})"

    /**
     * Probabilistic CYK. `chart[i][j]` holds the best parse of words[i until j] for each label —
     * the same table shape as the DSA taxonomy's matrix-chain DP, with max in place of min.
     */
    fun cyk(words: List<String> = sentence): Array<Array<MutableMap<String, Parse>>> {
        val n = words.size
        val chart = Array(n) { Array(n + 1) { mutableMapOf<String, Parse>() } }
        for (i in 0 until n) {
            for ((label, word, p) in lexical) {
                if (word == words[i]) chart[i][i + 1][label] = Parse(label, p, word = word)
            }
        }
        for (span in 2..n) {
            for (i in 0..n - span) {
                val j = i + span
                for (split in i + 1 until j) {
                    for ((label, children, p) in binary) {
                        val left = chart[i][split][children.first] ?: continue
                        val right = chart[split][j][children.second] ?: continue
                        val score = p * left.probability * right.probability
                        val existing = chart[i][j][label]
                        if (existing == null || score > existing.probability) {
                            chart[i][j][label] = Parse(label, score, left, right)
                        }
                    }
                }
            }
        }
        return chart
    }

    /** Cells the table actually fills — the O(n³·|G|) cost, counted. */
    fun filledCells(words: List<String> = sentence): Int =
        cyk(words).sumOf { row -> row.count { it.isNotEmpty() } }

    fun splitsConsidered(n: Int = sentence.size): Int =
        (2..n).sumOf { span -> (0..n - span).sumOf { span - 1 } }

    fun best(words: List<String> = sentence): Parse? = cyk(words)[0][words.size]["S"]

    /**
     * The two readings of the ambiguous sentence, built by hand so both can be scored — CYK only
     * keeps the winner, and the losing parse is half of what the topic is about.
     *
     * `vpAttachment`: she saw [the man] [with the telescope] — the seeing was done with it.
     * `npAttachment`: she saw [the man with the telescope] — the man had it.
     */
    fun vpAttachment(): Parse {
        val np = Parse("NP", 0.40, word = "she")
        val theMan = det("man")
        val theTelescope = det("telescope")
        val pp = Parse("PP", 1.00 * 1.00 * theTelescope.probability, Parse("P", 1.0, word = "with"), theTelescope)
        val vpCore = Parse("VP", 0.70 * 1.00 * theMan.probability, Parse("V", 1.0, word = "saw"), theMan)
        val vp = Parse("VP", 0.30 * vpCore.probability * pp.probability, vpCore, pp)
        return Parse("S", 1.0 * np.probability * vp.probability, np, vp)
    }

    fun npAttachment(): Parse {
        val subject = Parse("NP", 0.40, word = "she")
        val theMan = det("man")
        val theTelescope = det("telescope")
        val pp = Parse("PP", 1.00 * 1.00 * theTelescope.probability, Parse("P", 1.0, word = "with"), theTelescope)
        val bigNp = Parse("NP", 0.20 * theMan.probability * pp.probability, theMan, pp)
        val vp = Parse("VP", 0.70 * 1.00 * bigNp.probability, Parse("V", 1.0, word = "saw"), bigNp)
        return Parse("S", 1.0 * subject.probability * vp.probability, subject, vp)
    }

    private fun det(noun: String): Parse = Parse(
        "NP",
        0.40 * 1.00 * 0.50,
        Parse("Det", 1.0, word = "the"),
        Parse("N", 0.50, word = noun),
    )

    /** How much more probable the winning reading is. Computed, not asserted. */
    val attachmentRatio: Double get() = vpAttachment().probability / npAttachment().probability
}

