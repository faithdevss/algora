package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.sqrt

// ── D1 · Statistical NLP math ────────────────────────────────────────────────
// The HMM, PCFG, cosine and Jaccard labs run on these. Same rule as the preprocessing side: the
// copy quotes what this file computes, and `D1MathTest` pins the properties the copy leans on —
// notably that greedy tagging and Viterbi really do disagree on the lab's sentence, and that the
// PP-attachment parse really is the one CYK prefers.

// ── Hidden Markov Model ──────────────────────────────────────────────────────

internal object HmmLab {

    val tags = listOf("NN", "VB", "DT", "IN")
    val sentence = listOf("book", "that", "flight")

    /** π(t) — how often a sentence starts with each tag. */
    val start = mapOf("NN" to 0.35, "VB" to 0.25, "DT" to 0.30, "IN" to 0.10)

    /** A(t → t′). Rows sum to 1. */
    val transition = mapOf(
        "NN" to mapOf("NN" to 0.15, "VB" to 0.30, "DT" to 0.05, "IN" to 0.30, "</s>" to 0.20),
        "VB" to mapOf("NN" to 0.20, "VB" to 0.05, "DT" to 0.50, "IN" to 0.10, "</s>" to 0.15),
        "DT" to mapOf("NN" to 0.90, "VB" to 0.02, "DT" to 0.02, "IN" to 0.02, "</s>" to 0.04),
        "IN" to mapOf("NN" to 0.60, "VB" to 0.05, "DT" to 0.30, "IN" to 0.03, "</s>" to 0.02),
    )

    /** B(t → w). Only the three words the lab tags; every other word has probability 0 here. */
    val emission = mapOf(
        "NN" to mapOf("book" to 0.030, "that" to 0.000, "flight" to 0.020),
        "VB" to mapOf("book" to 0.040, "that" to 0.000, "flight" to 0.000),
        "DT" to mapOf("book" to 0.000, "that" to 0.150, "flight" to 0.000),
        "IN" to mapOf("book" to 0.000, "that" to 0.250, "flight" to 0.000),
    )

    fun a(from: String, to: String): Double = transition[from]?.get(to) ?: 0.0
    fun b(tag: String, word: String): Double = emission[tag]?.get(word) ?: 0.0

    /** P(w₁…w_t, tag_t = t) — the forward trellis, summing over every path into each cell. */
    fun forward(words: List<String> = sentence): List<Map<String, Double>> {
        val trellis = mutableListOf<Map<String, Double>>()
        var column = tags.associateWith { (start[it] ?: 0.0) * b(it, words[0]) }
        trellis += column
        for (i in 1 until words.size) {
            column = tags.associateWith { to ->
                tags.sumOf { from -> column.getValue(from) * a(from, to) } * b(to, words[i])
            }
            trellis += column
        }
        return trellis
    }

    /** P(w) — the sentence's total probability under the model, all paths included. */
    fun sentenceProbability(words: List<String> = sentence): Double =
        forward(words).last().entries.sumOf { (tag, p) -> p * a(tag, "</s>") }

    class Path(val tags: List<String>, val probability: Double)

    /** Every tag sequence, scored. Small enough here (4³ = 64) to enumerate, which is the point. */
    fun allPaths(words: List<String> = sentence): List<Path> {
        val out = mutableListOf<Path>()
        fun walk(prefix: List<String>, score: Double) {
            if (prefix.size == words.size) {
                out += Path(prefix, score * a(prefix.last(), "</s>"))
                return
            }
            val i = prefix.size
            for (tag in tags) {
                val step = if (i == 0) (start[tag] ?: 0.0) else a(prefix.last(), tag)
                walk(prefix + tag, score * step * b(tag, words[i]))
            }
        }
        walk(emptyList(), 1.0)
        return out.filter { it.probability > 0.0 }.sortedByDescending { it.probability }
    }

    /** Viterbi: the same recursion as forward with max in place of sum, plus backpointers. */
    fun viterbi(words: List<String> = sentence): Path {
        var column = tags.associateWith { (start[it] ?: 0.0) * b(it, words[0]) }
        val backpointers = mutableListOf<Map<String, String>>()
        for (i in 1 until words.size) {
            val back = mutableMapOf<String, String>()
            val next = tags.associateWith { to ->
                val best = tags.maxByOrNull { from -> column.getValue(from) * a(from, to) }!!
                back[to] = best
                column.getValue(best) * a(best, to) * b(to, words[i])
            }
            backpointers += back
            column = next
        }
        val finals = column.mapValues { (tag, p) -> p * a(tag, "</s>") }
        var tag = finals.maxByOrNull { it.value }!!.key
        val path = mutableListOf(tag)
        for (i in backpointers.indices.reversed()) {
            tag = backpointers[i].getValue(tag)
            path.add(0, tag)
        }
        return Path(path, finals.values.max())
    }

    /**
     * Left-to-right greedy tagging: commit to the best tag for each word given the one already
     * chosen, never revisiting. Cheap, and on this sentence wrong — which is the lab's whole point.
     */
    fun greedy(words: List<String> = sentence): Path {
        val path = mutableListOf<String>()
        for (i in words.indices) {
            val best = tags.maxByOrNull { tag ->
                val step = if (i == 0) (start[tag] ?: 0.0) else a(path.last(), tag)
                step * b(tag, words[i])
            }!!
            path += best
        }
        return Path(path, scoreOf(path, words))
    }

    fun scoreOf(path: List<String>, words: List<String> = sentence): Double {
        var p = start[path[0]] ?: 0.0
        p *= b(path[0], words[0])
        for (i in 1 until path.size) p *= a(path[i - 1], path[i]) * b(path[i], words[i])
        return p * a(path.last(), "</s>")
    }

    /** Trellis cells Viterbi fills vs paths a brute-force search would score: T·N² vs N^T. */
    fun viterbiOperations(length: Int = sentence.size): Int = length * tags.size * tags.size
    fun bruteForcePaths(length: Int = sentence.size): Int {
        var n = 1
        repeat(length) { n *= tags.size }
        return n
    }
}

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

// ── Cosine and Jaccard ───────────────────────────────────────────────────────

internal object SimilarityLab {

    val terms = listOf("data", "model")

    /**
     * Four documents in a two-term space, so the geometry is drawable. `long` is `short` with every
     * sentence written twice — same topic mix, twice the length, which is the case that separates
     * the two metrics.
     */
    val vectors = linkedMapOf(
        "short" to listOf(3.0, 1.0),
        "long" to listOf(6.0, 2.0),
        "theory" to listOf(1.0, 3.0),
        "query" to listOf(2.0, 1.0),
    )

    fun dot(a: List<Double>, b: List<Double>) = a.indices.sumOf { a[it] * b[it] }
    fun norm(a: List<Double>) = sqrt(dot(a, a))
    fun cosine(a: List<Double>, b: List<Double>) = dot(a, b) / (norm(a) * norm(b))
    fun euclidean(a: List<Double>, b: List<Double>) =
        sqrt(a.indices.sumOf { (a[it] - b[it]) * (a[it] - b[it]) })

    fun cosine(a: String, b: String) = cosine(vectors.getValue(a), vectors.getValue(b))
    fun euclidean(a: String, b: String) = euclidean(vectors.getValue(a), vectors.getValue(b))

    /** Ranking a query against the corpus by each metric. The two orders differ. */
    fun rankByCosine(query: String = "query") =
        vectors.keys.filter { it != query }.sortedByDescending { cosine(query, it) }

    fun rankByEuclidean(query: String = "query") =
        vectors.keys.filter { it != query }.sortedBy { euclidean(query, it) }

    val lengthInvariant: Boolean get() = abs(cosine("short", "long") - 1.0) < 1e-9

    // ── Jaccard, on the same pair of documents ───────────────────────────────

    val docA = "the model learns from data and more data"
    val docB = "the model learns from data"
    val docC = "a neural model learns representations from raw data"

    fun setOf(doc: String): Set<String> = doc.split(" ").toSet()

    fun jaccard(a: String, b: String): Double {
        val x = setOf(a)
        val y = setOf(b)
        return x.intersect(y).size.toDouble() / x.union(y).size
    }

    fun counts(doc: String): Map<String, Int> = doc.split(" ").groupingBy { it }.eachCount()

    /** Cosine over the same two documents, on raw counts rather than sets. */
    fun cosineOfDocs(a: String, b: String): Double {
        val ca = counts(a)
        val cb = counts(b)
        val vocab = (ca.keys + cb.keys).toList()
        val va = vocab.map { (ca[it] ?: 0).toDouble() }
        val vb = vocab.map { (cb[it] ?: 0).toDouble() }
        return cosine(va, vb)
    }

    /** Character k-shingles — what near-duplicate detection actually compares. */
    fun shingles(doc: String, k: Int = 5): Set<String> =
        doc.windowed(k, 1).toSet()

    fun jaccardShingles(a: String, b: String, k: Int = 5): Double {
        val x = shingles(a, k)
        val y = shingles(b, k)
        return x.intersect(y).size.toDouble() / x.union(y).size
    }

    /**
     * MinHash: hash every shingle under `permutations` different hash functions and keep the
     * smallest value under each. The fraction of signature positions that agree estimates Jaccard,
     * because P(min agrees) = |A ∩ B| / |A ∪ B| exactly.
     */
    fun signature(set: Set<String>, permutations: Int): List<Int> =
        (0 until permutations).map { seed ->
            set.minOf { shingle -> hash(shingle, seed) }
        }

    private fun hash(value: String, seed: Int): Int {
        var h = 2166136261L.toInt() xor (seed * 0x9E3779B1.toInt())
        for (c in value) {
            h = h xor c.code
            h *= 16777619
        }
        return h and 0x7fffffff
    }

    fun minHashEstimate(a: String, b: String, permutations: Int, k: Int = 5): Double {
        val sa = signature(shingles(a, k), permutations)
        val sb = signature(shingles(b, k), permutations)
        return sa.indices.count { sa[it] == sb[it] }.toDouble() / permutations
    }

    /** Comparison cost: a signature is fixed-width, the sets are not. */
    fun exactComparisonSize(a: String, b: String, k: Int = 5): Int =
        shingles(a, k).size + shingles(b, k).size
}
