package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// ── D6 · NLP metrics ─────────────────────────────────────────────────────────
// Perplexity, WER, BLEU, ROUGE, METEOR and MMLU. Every score these six labs quote is computed here
// from real strings and real counts, and pinned by `D6MetricsTest`.
//
// B9 set the bar for a metrics batch and it is the bar here: each metric gets its own "when this one
// misleads you" case, scored rather than asserted. That is the difference between seventeen
// definitions and seventeen topics, and it is what stops a metrics batch reading as filler. The
// misleading cases here are a perplexity that ranks two tokenizers in the opposite order to bits per
// character, a WER that exceeds 100%, two hypotheses with identical WER and opposite meanings, a
// third whose meaning is reversed for a *third* of the error rate of a harmless one, a good
// paraphrase that BLEU scores zero, a do-nothing summary that scores 0.800 ROUGE-1 recall, a
// sentence shuffle that ROUGE-1 cannot see at all, and an MMLU subject ranking that is entirely
// sampling noise.
//
// Three claims in an earlier draft of this file were falsified by the probe before any copy was
// written, and the corrections are marked where they land: the test sentence has no unseen bigrams
// so unsmoothed perplexity is finite on it (a second sentence now carries that case), the two WER
// hypotheses that were to collide differ by 3× (a third case supplies the actual collision), and the
// whole-document ROUGE recall is 0.800 rather than the perfect score asserted.

private fun logSum(values: List<Double>) = values.sum()

/** A crude suffix stripper — enough for METEOR's stem-match stage on this vocabulary. */
internal fun crudeStem(word: String): String {
    val w = word.lowercase()
    listOf("ings", "ing", "edly", "ed", "es", "s", "ly").forEach { suffix ->
        if (w.length > suffix.length + 2 && w.endsWith(suffix)) return w.dropLast(suffix.length)
    }
    return w
}

internal fun words(text: String): List<String> =
    text.lowercase().split(Regex("[^a-z0-9']+")).filter { it.isNotEmpty() }

// ── Perplexity ───────────────────────────────────────────────────────────────

/**
 * A bigram language model with add-k smoothing, trained and scored for real, and then the same text
 * scored again by a *character*-level model of the same shape.
 *
 * The reason for the second model is the topic's central warning: perplexity is per token, so it is
 * a function of the tokenizer as much as of the model. Two systems with different vocabularies have
 * perplexities that cannot be compared at all, and the fix — bits per character — is computed here
 * alongside so the comparison can actually be made.
 *
 * On this corpus the two disagree outright, which is the topic's headline: the character model has
 * the *lower* perplexity (5.916 against 8.229) and the *higher* bits per character (3.470 against
 * 1.252). Read the perplexities and the character model wins; read the comparable number and the
 * word model wins by nearly 3×. The word model's advantage is partly that a closed 19-word
 * vocabulary never has to spell anything, which is the caveat that comes with bits per character
 * and is why [charVocabulary] and [vocabulary] are both exposed.
 */
internal object PerplexityLab {

    val corpus = listOf(
        "the cat sat on the mat",
        "the cat ate the fish",
        "a dog sat on the rug",
        "the dog ate the bone",
        "a cat chased the dog",
        "the fish swam in the bowl",
        "a bird sat on the branch",
        "the dog chased the bird",
    )

    val testSentence = "the cat sat on the rug"

    /**
     * A second sentence, every word of which the corpus contains, and exactly one of whose bigrams it
     * does not (`cat swam`). This is the sentence the unsmoothed model cannot score.
     *
     * It exists because the probe refused the first draft: `testSentence`'s bigrams are all attested,
     * so unsmoothed perplexity on it is finite — and the claim "one unseen bigram sends perplexity to
     * infinity" needed a sentence that actually has one.
     */
    val unseenSentence = "a cat swam in the bowl"

    private fun tokensOf(sentence: String) = listOf("<s>") + words(sentence) + listOf("</s>")

    val vocabulary: List<String> by lazy {
        (corpus.flatMap { words(it) } + listOf("<s>", "</s>")).distinct().sorted()
    }

    private val unigramCounts: Map<String, Int> by lazy {
        corpus.flatMap { tokensOf(it) }.groupingBy { it }.eachCount()
    }

    private val bigramCounts: Map<Pair<String, String>, Int> by lazy {
        corpus.flatMap { s -> tokensOf(s).zipWithNext() }.groupingBy { it }.eachCount()
    }

    /** `P(next | prev)` under add-k smoothing over the closed vocabulary. */
    fun probability(prev: String, next: String, k: Double): Double {
        val v = vocabulary.size
        val joint = bigramCounts[prev to next] ?: 0
        val context = unigramCounts[prev] ?: 0
        return (joint + k) / (context + k * v)
    }

    class TokenScore(val prev: String, val token: String, val probability: Double) {
        val surprisal: Double get() = -ln(probability) / ln(2.0)
    }

    fun scoreSentence(sentence: String, k: Double = 1.0): List<TokenScore> =
        tokensOf(sentence).zipWithNext().map { (prev, next) -> TokenScore(prev, next, probability(prev, next, k)) }

    fun perplexity(sentence: String, k: Double = 1.0): Double {
        val scores = scoreSentence(sentence, k)
        val meanNll = -logSum(scores.map { ln(it.probability) }) / scores.size
        return exp(meanNll)
    }

    /** Cross-entropy in bits per *character*, which is what makes two tokenizers comparable. */
    fun bitsPerCharacter(sentence: String, k: Double = 1.0): Double {
        val scores = scoreSentence(sentence, k)
        val characters = sentence.replace(" ", "").length
        return scores.sumOf { it.surprisal } / characters
    }

    /**
     * The same sentence with no smoothing at all. On a sentence whose bigrams are all attested this
     * is *lower* than the smoothed score — smoothing is a premium paid on every token. On
     * [unseenSentence] it is infinite, which is the one event the premium buys insurance against.
     */
    fun unsmoothedPerplexity(sentence: String): Double = perplexity(sentence, k = 0.0)

    fun unseenBigrams(sentence: String): List<Pair<String, String>> =
        tokensOf(sentence).zipWithNext().filter { (bigramCounts[it] ?: 0) == 0 }

    // ── The character-level model, for the tokenizer comparison ──────────────

    private fun charsOf(sentence: String) = listOf('^') + sentence.replace(" ", "_").toList() + listOf('$')

    private val charUnigrams: Map<Char, Int> by lazy {
        corpus.flatMap { charsOf(it) }.groupingBy { it }.eachCount()
    }

    private val charBigrams: Map<Pair<Char, Char>, Int> by lazy {
        corpus.flatMap { charsOf(it).zipWithNext() }.groupingBy { it }.eachCount()
    }

    val charVocabulary: List<Char> by lazy { corpus.flatMap { charsOf(it) }.distinct().sorted() }

    fun charProbability(prev: Char, next: Char, k: Double): Double {
        val v = charVocabulary.size
        return ((charBigrams[prev to next] ?: 0) + k) / ((charUnigrams[prev] ?: 0) + k * v)
    }

    fun characterPerplexity(sentence: String, k: Double = 1.0): Double {
        val pairs = charsOf(sentence).zipWithNext()
        val meanNll = -pairs.sumOf { (a, b) -> ln(charProbability(a, b, k)) } / pairs.size
        return exp(meanNll)
    }

    fun characterBitsPerCharacter(sentence: String, k: Double = 1.0): Double {
        val pairs = charsOf(sentence).zipWithNext()
        val characters = sentence.replace(" ", "").length
        return pairs.sumOf { (a, b) -> -ln(charProbability(a, b, k)) / ln(2.0) } / characters
    }

    /** A uniform model's perplexity is exactly the vocabulary size — the branching-factor reading. */
    fun uniformPerplexity(): Double = vocabulary.size.toDouble()

    /**
     * The add-k sweep, so the frame can report a real optimum rather than "smoothing helps".
     *
     * On [testSentence] the sweep is monotone the *wrong* way — every increase in k makes the score
     * worse, because there is nothing on this sentence for the smoothing to rescue. That is the
     * honest shape of add-k and it is why the sweep is reported over both sentences.
     */
    val smoothingSweep = listOf(0.001, 0.01, 0.1, 0.5, 1.0, 2.0)

    fun bestSmoothing(sentence: String = testSentence): Double =
        smoothingSweep.minBy { perplexity(sentence, it) }

    /** What add-1 costs on a sentence that never needed it: the ratio against the best k in the sweep. */
    fun smoothingPremium(sentence: String = testSentence): Double =
        perplexity(sentence, 1.0) / perplexity(sentence, bestSmoothing(sentence))
}

// ── WER (Word Error Rate) ────────────────────────────────────────────────────

/**
 * Word-level edit distance with a backtrace, so the substitutions, deletions and insertions are
 * counted separately rather than summed into a number.
 *
 * The two cases the lab is built around: WER has no upper bound, because insertions are counted
 * against a denominator that does not include them; and WER weights every word equally, so a
 * hypothesis that drops three function words scores three times worse than one that reverses the
 * meaning of the sentence — and exactly the same as a third that reverses it and mangles two
 * articles on the way.
 */
internal object WerLab {

    val reference = "the model did not converge on the second run"

    class Alignment(val substitutions: Int, val deletions: Int, val insertions: Int, val referenceLength: Int) {
        val errors: Int get() = substitutions + deletions + insertions
        val wer: Double get() = errors.toDouble() / referenceLength
    }

    fun align(reference: String, hypothesis: String): Alignment {
        val r = words(reference)
        val h = words(hypothesis)
        // Cost table plus a backtrace of which operation each cell chose.
        val cost = Array(r.size + 1) { IntArray(h.size + 1) }
        val op = Array(r.size + 1) { CharArray(h.size + 1) }
        for (i in 0..r.size) {
            cost[i][0] = i
            op[i][0] = 'd'
        }
        for (j in 0..h.size) {
            cost[0][j] = j
            op[0][j] = 'i'
        }
        op[0][0] = '='
        for (i in 1..r.size) {
            for (j in 1..h.size) {
                val match = if (r[i - 1] == h[j - 1]) 0 else 1
                val diagonal = cost[i - 1][j - 1] + match
                val delete = cost[i - 1][j] + 1
                val insert = cost[i][j - 1] + 1
                cost[i][j] = minOf(diagonal, delete, insert)
                op[i][j] = when (cost[i][j]) {
                    diagonal -> if (match == 0) '=' else 's'
                    delete -> 'd'
                    else -> 'i'
                }
            }
        }
        var i = r.size
        var j = h.size
        var s = 0
        var d = 0
        var ins = 0
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && op[i][j] == '=' -> { i--; j-- }
                i > 0 && j > 0 && op[i][j] == 's' -> { s++; i--; j-- }
                i > 0 && op[i][j] == 'd' -> { d++; i-- }
                else -> { ins++; j-- }
            }
        }
        return Alignment(s, d, ins, r.size)
    }

    class Case(val name: String, val hypothesis: String, val note: String)

    val cases = listOf(
        Case("clean", "the model did not converge on the second run", "identical to the reference"),
        Case(
            "dropped function words",
            "model did not converge second run",
            "three words gone, meaning intact",
        ),
        Case(
            "negation flipped",
            "the model did converge on the second run",
            "one word gone, meaning reversed",
        ),
        Case(
            "negation flipped, articles too",
            "a model did converge on a second run",
            "three errors, meaning still reversed",
        ),
        Case(
            "runaway insertion",
            "the the the model model did did not not converge converge on on the the second second run run and and so on and so on",
            "a stuck decoder — more words out than in",
        ),
    )

    fun alignmentFor(case: Case): Alignment = align(reference, case.hypothesis)

    /**
     * The pair WER *misranks*: the meaning-preserving hypothesis scores worse than the one that
     * reverses the sentence, and by a factor the metric presents as a quality gap.
     *
     * The first draft claimed these two collide. They do not — 0.333 against 0.111 — and the
     * measured version is the sharper claim, so [collidingCases] carries the actual tie separately.
     */
    fun misrankedCases(): Pair<Case, Case> = cases[1] to cases[2]

    /**
     * The pair that does collide: three deletions of function words against two substituted articles
     * and a deleted "not". Same substitutions-plus-deletions count, same denominator, same WER —
     * and one of them says the opposite of the reference.
     */
    fun collidingCases(): Pair<Case, Case> = cases[1] to cases[3]

    // ── Normalization ────────────────────────────────────────────────────────

    val rawReference = "The model did not converge, on the second run."
    val rawHypothesis = "the Model did not converge on the 2nd run"

    private fun normalize(text: String) =
        text.lowercase().replace(Regex("[^a-z0-9' ]"), "").replace("2nd", "second")

    /** Split on whitespace only: casing and punctuation stay attached to the token. */
    fun unnormalizedWer(): Double = alignRaw(
        rawReference.split(" ").filter { it.isNotEmpty() },
        rawHypothesis.split(" ").filter { it.isNotEmpty() },
    )

    private fun alignRaw(r: List<String>, h: List<String>): Double {
        val cost = Array(r.size + 1) { IntArray(h.size + 1) }
        for (i in 0..r.size) cost[i][0] = i
        for (j in 0..h.size) cost[0][j] = j
        for (i in 1..r.size) for (j in 1..h.size) {
            cost[i][j] = minOf(
                cost[i - 1][j - 1] + if (r[i - 1] == h[j - 1]) 0 else 1,
                cost[i - 1][j] + 1,
                cost[i][j - 1] + 1,
            )
        }
        return cost[r.size][h.size].toDouble() / r.size
    }

    fun normalizedWer(): Double = align(normalize(rawReference), normalize(rawHypothesis)).wer
}

// ── BLEU ─────────────────────────────────────────────────────────────────────

/**
 * Modified n-gram precision with clipping, the brevity penalty, and the geometric mean over orders
 * 1–4 — the actual definition, not a bag-of-words approximation of it.
 *
 * Two things the lab is here to show. Clipping is not a detail: without it a degenerate output that
 * repeats one frequent word scores a perfect unigram precision. And the geometric mean means a
 * single empty order zeroes the whole score, which is why sentence-level BLEU on a *good* paraphrase
 * is routinely 0.
 */
internal object BleuLab {

    val reference = "the committee approved the revised budget on friday"

    class Candidate(val name: String, val text: String, val note: String)

    val candidates = listOf(
        Candidate("close match", "the committee approved the revised budget on friday", "identical"),
        Candidate("one word off", "the committee approved the revised budget on monday", "one substitution"),
        Candidate("good paraphrase", "on friday the panel signed off on the amended budget", "same meaning, different words"),
        Candidate("degenerate", "the the the the the the the the", "a stuck decoder emitting one frequent word"),
        Candidate("short but exact", "the revised budget", "a correct fragment"),
    )

    fun ngrams(tokens: List<String>, n: Int): List<List<String>> =
        if (tokens.size < n) emptyList() else (0..tokens.size - n).map { tokens.subList(it, it + n) }

    /** Precision at order `n`, with each candidate n-gram capped by its count in the reference. */
    fun modifiedPrecision(candidate: String, n: Int): Pair<Int, Int> {
        val c = ngrams(words(candidate), n)
        if (c.isEmpty()) return 0 to 0
        val refCounts = ngrams(words(reference), n).groupingBy { it }.eachCount()
        val candCounts = c.groupingBy { it }.eachCount()
        val clipped = candCounts.entries.sumOf { (gram, count) -> min(count, refCounts[gram] ?: 0) }
        return clipped to c.size
    }

    /** The same precision with no clipping — the bug clipping exists to prevent. */
    fun unclippedPrecision(candidate: String, n: Int): Pair<Int, Int> {
        val c = ngrams(words(candidate), n)
        if (c.isEmpty()) return 0 to 0
        val refCounts = ngrams(words(reference), n).groupingBy { it }.eachCount()
        val matched = c.count { (refCounts[it] ?: 0) > 0 }
        return matched to c.size
    }

    fun brevityPenalty(candidate: String): Double {
        val c = words(candidate).size
        val r = words(reference).size
        return if (c > r) 1.0 else exp(1.0 - r.toDouble() / c)
    }

    /** `smoothing` is add-1 on the numerator and denominator (Chen & Cherry method 1). */
    fun bleu(candidate: String, maxOrder: Int = 4, smoothing: Boolean = false): Double {
        val logs = (1..maxOrder).map { n ->
            val (clipped, total) = modifiedPrecision(candidate, n)
            when {
                total == 0 -> return 0.0
                clipped == 0 && !smoothing -> return 0.0
                clipped == 0 -> ln(1.0 / (total + 1.0))
                else -> ln(clipped.toDouble() / total)
            }
        }
        return brevityPenalty(candidate) * exp(logs.sum() / maxOrder)
    }

    /** Which orders came back empty — the reason a sentence-level score is zero. */
    fun emptyOrders(candidate: String, maxOrder: Int = 4): List<Int> =
        (1..maxOrder).filter { modifiedPrecision(candidate, it).first == 0 }
}

// ── ROUGE ────────────────────────────────────────────────────────────────────

/**
 * ROUGE-1, ROUGE-2 and ROUGE-L over a real four-sentence document.
 *
 * The two cases: ROUGE is recall-first, so copying the whole document — summarising nothing at all —
 * scores 0.800 recall against the reference, which is the degenerate behaviour a recall-only report
 * rewards. (0.800 rather than 1.000 only because the reference paraphrases two words; the point
 * stands at three-and-a-bit times the compression rate.) And ROUGE-1 is a bag of words, so a summary
 * whose clauses have been reordered scores *identically* on it while ROUGE-2 and ROUGE-L both fall.
 */
internal object RougeLab {

    val document = listOf(
        "the board met on tuesday to review the quarterly results",
        "revenue grew by eleven percent against a flat market",
        "the chief executive announced a dividend increase",
        "shares rose four percent in after hours trading",
    )

    val referenceSummary = "the board reviewed quarterly results and announced a dividend increase"

    class Score(val precision: Double, val recall: Double) {
        val f1: Double get() = if (precision + recall == 0.0) 0.0 else 2 * precision * recall / (precision + recall)
    }

    private fun overlap(candidate: List<List<String>>, reference: List<List<String>>): Score {
        if (candidate.isEmpty() || reference.isEmpty()) return Score(0.0, 0.0)
        val refCounts = reference.groupingBy { it }.eachCount().toMutableMap()
        var matched = 0
        candidate.forEach { gram ->
            val remaining = refCounts[gram] ?: 0
            if (remaining > 0) {
                matched++
                refCounts[gram] = remaining - 1
            }
        }
        return Score(matched.toDouble() / candidate.size, matched.toDouble() / reference.size)
    }

    fun rougeN(candidate: String, n: Int, reference: String = referenceSummary): Score =
        overlap(BleuLab.ngrams(words(candidate), n), BleuLab.ngrams(words(reference), n))

    private fun lcsLength(a: List<String>, b: List<String>): Int {
        val table = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in 1..a.size) for (j in 1..b.size) {
            table[i][j] = if (a[i - 1] == b[j - 1]) table[i - 1][j - 1] + 1 else max(table[i - 1][j], table[i][j - 1])
        }
        return table[a.size][b.size]
    }

    fun rougeL(candidate: String, reference: String = referenceSummary): Score {
        val c = words(candidate)
        val r = words(reference)
        val lcs = lcsLength(c, r)
        return Score(lcs.toDouble() / c.size, lcs.toDouble() / r.size)
    }

    class Candidate(val name: String, val text: String, val note: String)

    val candidates: List<Candidate> by lazy {
        listOf(
            Candidate(
                "focused summary",
                "the board reviewed quarterly results and announced a dividend increase",
                "what a good summary looks like",
            ),
            Candidate(
                "whole document",
                document.joinToString(" "),
                "no summarisation at all — and it still recalls four fifths of the reference",
            ),
            Candidate(
                "reordered",
                "announced a dividend increase and the board reviewed quarterly results",
                "the same words, in an order that changes what was said",
            ),
            Candidate(
                "abstractive",
                "directors approved a larger payout after a strong quarter",
                "correct, and it shares almost no vocabulary",
            ),
        )
    }
}

// ── METEOR ───────────────────────────────────────────────────────────────────

/**
 * METEOR's three parts, computed: a unigram alignment that matches exact words first and stems
 * second, a recall-weighted harmonic mean, and a fragmentation penalty driven by how many contiguous
 * chunks the alignment breaks into.
 *
 * The reason it exists is the case BLEU cannot score: a correct paraphrase with no matching 4-gram.
 * The reason the penalty exists is the case an unordered F-score cannot see: the same words in the
 * wrong order.
 */
internal object MeteorLab {

    val reference = BleuLab.reference

    const val alpha = 0.9
    const val gamma = 0.5
    const val beta = 3.0

    class Match(val candidateIndex: Int, val referenceIndex: Int, val exact: Boolean)

    /** Stage one exact, stage two on stems — METEOR's actual matching order. */
    fun alignment(candidate: String): List<Match> {
        val c = words(candidate)
        val r = words(reference)
        val used = BooleanArray(r.size)
        val matches = mutableListOf<Match>()
        c.indices.forEach { i ->
            val exact = r.indices.firstOrNull { !used[it] && r[it] == c[i] }
            if (exact != null) {
                used[exact] = true
                matches += Match(i, exact, true)
            }
        }
        c.indices.forEach { i ->
            if (matches.any { it.candidateIndex == i }) return@forEach
            val stem = r.indices.firstOrNull { !used[it] && crudeStem(r[it]) == crudeStem(c[i]) }
            if (stem != null) {
                used[stem] = true
                matches += Match(i, stem, false)
            }
        }
        return matches.sortedBy { it.candidateIndex }
    }

    /** Contiguous runs in both sequences at once — the unit the penalty counts. */
    fun chunks(candidate: String): Int {
        val ordered = alignment(candidate)
        if (ordered.isEmpty()) return 0
        var count = 1
        ordered.zipWithNext().forEach { (a, b) ->
            if (b.candidateIndex != a.candidateIndex + 1 || b.referenceIndex != a.referenceIndex + 1) count++
        }
        return count
    }

    fun precision(candidate: String) = alignment(candidate).size.toDouble() / words(candidate).size

    fun recall(candidate: String) = alignment(candidate).size.toDouble() / words(reference).size

    fun fMean(candidate: String): Double {
        val p = precision(candidate)
        val r = recall(candidate)
        if (p + r == 0.0) return 0.0
        return p * r / (alpha * p + (1 - alpha) * r)
    }

    fun penalty(candidate: String): Double {
        val matched = alignment(candidate).size
        if (matched == 0) return 0.0
        return gamma * (chunks(candidate).toDouble() / matched).pow(beta)
    }

    fun score(candidate: String): Double = fMean(candidate) * (1 - penalty(candidate))

    /** The same words in a different order — same alignment size, more chunks. */
    val shuffled = "friday on budget revised the approved committee the"
}

// ── MMLU ─────────────────────────────────────────────────────────────────────

/**
 * MMLU as a measurement rather than a leaderboard row: the chance floor, the standard error at the
 * benchmark's real size and at one subject's, what contamination does to a reported score, and the
 * gap between macro and micro averaging over unequal subjects.
 *
 * The claim the lab is built to support is that most published MMLU comparisons are within noise of
 * each other at the subject level, and that this is arithmetic rather than an opinion.
 */
internal object MmluLab {

    const val options = 4
    val chance = 1.0 / options

    /** The benchmark's real size, and a representative subject's. */
    const val totalQuestions = 14_042
    const val subjectQuestions = 100

    fun standardError(accuracy: Double, n: Int): Double = sqrt(accuracy * (1 - accuracy) / n)

    /** How many standard errors apart two reported scores are, at a given sample size. */
    fun separation(a: Double, b: Double, n: Int): Double =
        abs(a - b) / sqrt(standardError(a, n).pow(2) + standardError(b, n).pow(2))

    /** Rescaled so 0 is guessing and 1 is perfect — what a multiple-choice score actually means. */
    fun chanceCorrected(accuracy: Double): Double = (accuracy - chance) / (1 - chance)

    class Model(val name: String, val reported: Double)

    val models = listOf(
        Model("Model A", 0.702),
        Model("Model B", 0.694),
        Model("Model C", 0.658),
        Model("Model D", 0.310),
    )

    /**
     * A reported score under contamination: a fraction `c` of questions were memorised and are
     * answered perfectly, the rest at the model's real ability.
     */
    fun contaminatedScore(trueAbility: Double, contamination: Double): Double =
        trueAbility * (1 - contamination) + contamination

    /** Invert it: the ability that actually produced a reported score at a given contamination. */
    fun trueAbility(reported: Double, contamination: Double): Double =
        (reported - contamination) / (1 - contamination)

    class Subject(val name: String, val questions: Int, val accuracy: Double)

    /** Subject sizes in MMLU differ by more than an order of magnitude, which is why the average matters. */
    val subjects = listOf(
        Subject("professional law", 1_534, 0.481),
        Subject("moral scenarios", 895, 0.398),
        Subject("miscellaneous", 783, 0.812),
        Subject("professional psychology", 612, 0.706),
        Subject("high school psychology", 545, 0.834),
        Subject("high school world history", 237, 0.789),
        Subject("marketing", 234, 0.876),
        Subject("virology", 166, 0.524),
        Subject("global facts", 100, 0.390),
        Subject("abstract algebra", 100, 0.330),
    )

    /** Every question weighs the same — big subjects dominate. */
    fun microAverage(): Double =
        subjects.sumOf { it.accuracy * it.questions } / subjects.sumOf { it.questions }

    /** Every subject weighs the same — small subjects dominate. */
    fun macroAverage(): Double = subjects.map { it.accuracy }.average()

    /** Subject pairs whose accuracy gap is inside 2 standard errors — i.e. not a ranking at all. */
    fun indistinguishableSubjectPairs(): List<Pair<Subject, Subject>> =
        subjects.flatMap { a -> subjects.filter { it.questions <= a.questions && it !== a }.map { a to it } }
            .filter { (a, b) ->
                val se = sqrt(standardError(a.accuracy, a.questions).pow(2) + standardError(b.accuracy, b.questions).pow(2))
                abs(a.accuracy - b.accuracy) < 2 * se
            }
}
