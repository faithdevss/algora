package com.algora.app.feature.topics

import kotlin.math.exp
import kotlin.math.ln

// ── D1 · Text preprocessing math ─────────────────────────────────────────────
// The numbers the Stop Word Removal, Lowercasing & Cleaning, RegEx and N-Grams labs quote. Every
// figure in those topics' copy comes out of here, so a re-tune that makes a claim false fails
// `D1MathTest` instead of shipping. Nothing here is Android-specific — it all runs on the JVM.

// ── Stop words ───────────────────────────────────────────────────────────────

internal object StopWordLab {

    /**
     * The 40 most frequent entries of NLTK's English list, verbatim in the order NLTK stores them.
     * The negations are the ones that matter here: `not` and `no` are on the real list, and removing
     * them is what the lab's central frame is about.
     */
    val stopList = listOf(
        "i", "me", "my", "we", "our", "you", "your", "he", "him", "his",
        "she", "her", "it", "its", "they", "them", "their", "what", "which", "who",
        "this", "that", "these", "those", "am", "is", "are", "was", "were", "be",
        "a", "an", "the", "and", "but", "if", "or", "at", "by", "for",
        "with", "about", "into", "to", "from", "in", "out", "on", "off", "all",
        "any", "both", "each", "no", "nor", "not", "only", "own", "same", "so",
        "than", "too", "very", "can", "will", "just", "don", "should", "now", "of",
    ).toSet()

    /** Six product reviews — the corpus the removal rate is measured on. */
    val corpus = listOf(
        "the movie was not good at all",
        "the movie was good",
        "i would not recommend this to any of my friends",
        "the battery life is very poor and the screen is too dim",
        "this is the best phone i have ever owned",
        "it arrived on time but the box was damaged",
    )

    fun tokens(text: String): List<String> = text.split(" ").filter { it.isNotBlank() }

    fun keep(text: String): List<String> = tokens(text).filter { it !in stopList }

    val totalTokens: Int get() = corpus.sumOf { tokens(it).size }
    val keptTokens: Int get() = corpus.sumOf { keep(it).size }
    val removedTokens: Int get() = totalTokens - keptTokens
    val removalRate: Double get() = removedTokens.toDouble() / totalTokens

    /** Vocabulary (types), before and after — the index-size argument, not the token-count one. */
    val typesBefore: Int get() = corpus.flatMap { tokens(it) }.toSet().size
    val typesAfter: Int get() = corpus.flatMap { keep(it) }.toSet().size

    /** Bag of words, order-free — what a BoW/TF-IDF pipeline actually compares. */
    fun bag(text: String): Map<String, Int> = keep(text).groupingBy { it }.eachCount()

    /** The payoff: two reviews with opposite verdicts, identical after the list is applied. */
    val negationCollapse: Boolean get() = bag(corpus[0]) == bag(corpus[1])

    /** A query made entirely of stop words survives as nothing at all. */
    val hamletQuery = "to be or not to be"
    val hamletSurvivors: List<String> get() = keep(hamletQuery)

    /**
     * What TF-IDF does to the same words without a list: idf = ln(N / df), so a term in every
     * document is weighted exactly zero and one in a single document is weighted highest. The list
     * is a hard version of a soft thing the weighting already does.
     */
    fun idf(term: String): Double {
        val df = corpus.count { term in tokens(it) }
        return if (df == 0) 0.0 else ln(corpus.size.toDouble() / df)
    }
}

// ── Lowercasing & cleaning ───────────────────────────────────────────────────

internal object CleaningLab {

    val raw = listOf(
        "Apple's Q3 revenue ROSE 5.2% — see https://apple.com/ir 🚀",
        "apple pie recipes: 5 easy steps!!! (with photos)",
        "US markets closed; the deal is not done for us yet.",
        "Café  visits   rose 12% in Q3 (per @IMF).",
    )

    /** Unicode normalisation only — the em dash, the curly apostrophe and the accent survive. */
    fun normalizeUnicode(text: String): String = java.text.Normalizer
        .normalize(text, java.text.Normalizer.Form.NFKC)
        .replace('’', '\'')

    fun stripUrls(text: String): String = Regex("https?://\\S+").replace(text, " <url> ")

    fun stripEmoji(text: String): String =
        text.filter { it.code < 0x2190 || it.isLetterOrDigit() || it.isWhitespace() }

    fun stripPunctuation(text: String): String = Regex("[^\\p{L}\\p{N}<> ]+").replace(text, " ")

    fun foldDigits(text: String): String = Regex("\\d+(?:\\.\\d+)?").replace(text, "<num>")

    fun collapseSpace(text: String): String = Regex("\\s+").replace(text, " ").trim()

    fun foldAccents(text: String): String = java.text.Normalizer
        .normalize(text, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    /** The pipeline, stage by stage, in the order the lab plays them. */
    val stages: List<Pair<String, (String) -> String>> = listOf(
        "NFKC normalise" to ::normalizeUnicode,
        "Replace URLs" to ::stripUrls,
        "Drop emoji" to ::stripEmoji,
        "Lowercase" to { t: String -> t.lowercase() },
        "Strip punctuation" to ::stripPunctuation,
        "Fold digits" to ::foldDigits,
        "Fold accents" to ::foldAccents,
        "Collapse space" to ::collapseSpace,
    )

    fun applyThrough(text: String, stageCount: Int): String =
        stages.take(stageCount).fold(text) { acc, (_, f) -> f(acc) }

    fun tokensAfter(stageCount: Int): List<String> =
        raw.flatMap { applyThrough(it, stageCount).split(" ") }.filter { it.isNotBlank() }

    /** Vocabulary size after each stage — the whole reason the pipeline exists. */
    fun typesAfter(stageCount: Int): Int = tokensAfter(stageCount).toSet().size

    val typesRaw: Int get() = typesAfter(0)
    val typesClean: Int get() = typesAfter(stages.size)

    /**
     * The cost, on the same corpus: lowercasing merges `US` (the country) into `us` (the pronoun),
     * so one type now carries two senses. Measured by asking whether both spellings existed before
     * the fold and only one after.
     */
    val casedPairCollapsed: Boolean
        get() {
            val before = raw.flatMap { normalizeUnicode(it).split(Regex("[^\\p{L}]+")) }.filter { it.isNotBlank() }
            return "US" in before && "us" in before && before.map { it.lowercase() }.count { it == "us" } == 2
        }

    /** The merge that pays for itself in the same corpus: `Apple's` and `apple` become one type. */
    val appleTypesBefore: Int
        get() = raw.flatMap { normalizeUnicode(it).split(Regex("[^\\p{L}']+")) }
            .filter { it.startsWith("Apple") || it.startsWith("apple") }
            .toSet().size

    val appleTypesAfter: Int
        get() = tokensAfter(stages.size).filter { it.startsWith("apple") }.toSet().size
}

// ── Regular expressions ──────────────────────────────────────────────────────

internal object RegexLab {

    val text = "Dr. Smith's e-mail is a.smith@x.co, the U.S. GDP rose 3.5% on 2024-01-05."

    /** The pattern everyone writes first. */
    val naive = Regex("\\w+")

    /**
     * The pattern after the failures are looked at: abbreviations with internal dots, hyphenated
     * words, clitics, decimals with a percent sign, ISO dates and e-mail addresses, each as one
     * token. Alternation order is the whole design — the longest, most specific branch has to come
     * first or a shorter one consumes its prefix.
     */
    val tuned = Regex(
        "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}" +   // e-mail, first or it is eaten
            "|\\d{4}-\\d{2}-\\d{2}" +                          // ISO date
            "|(?:[A-Za-z]\\.){2,}" +                           // U.S., e.g.
            "|[A-Za-z]+(?:[-'][A-Za-z]+)+" +                   // e-mail, Smith's
            "|\\d+(?:\\.\\d+)?%?" +                            // 3.5%, 2024
            "|[A-Za-z]+",                                      // everything else
    )

    /**
     * The same branches with the general one moved to the front. Alternation is first-match, not
     * longest-match, so this reordering — a single line edit — silently shatters everything the
     * specific branches were written for.
     */
    val misordered = Regex(
        "[A-Za-z]+" +
            "|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}" +
            "|\\d{4}-\\d{2}-\\d{2}" +
            "|(?:[A-Za-z]\\.){2,}" +
            "|[A-Za-z]+(?:[-'][A-Za-z]+)+" +
            "|\\d+(?:\\.\\d+)?%?",
    )

    fun matches(pattern: Regex): List<String> = pattern.findAll(text).map { it.value }.toList()

    val naiveTokens: List<String> get() = matches(naive)
    val tunedTokens: List<String> get() = matches(tuned)
    val misorderedTokens: List<String> get() = matches(misordered)

    /** Tokens the tuned pattern keeps whole that the naive one shatters. */
    val rescued: List<String>
        get() = tunedTokens.filter { token -> token !in naiveTokens && token.any { !it.isLetterOrDigit() } }

    /**
     * Catastrophic backtracking, counted rather than timed. `(a+)+$` against `aaaa…!` has to try
     * every way of splitting the run of a's between the inner and outer `+` before it can conclude
     * the match is impossible — and the number of ways to cut a run of n into ordered non-empty
     * groups is 2^(n-1). This walks that search explicitly, so the count is the engine's work, not
     * an estimate of it.
     */
    fun backtrackAttempts(n: Int): Long {
        var attempts = 0L
        fun split(remaining: Int) {
            if (remaining == 0) {
                attempts++      // one complete partition tried, then rejected by `$` vs '!'
                return
            }
            for (take in 1..remaining) split(remaining - take)
        }
        split(n)
        return attempts
    }

    /** The safe rewrite: possessive/atomic grouping, or just `a+$`, which is linear. */
    fun linearAttempts(n: Int): Long = n.toLong()
}

// ── N-grams ──────────────────────────────────────────────────────────────────

internal object NGramLab {

    val corpus = listOf(
        "i like nlp", "i like deep learning", "i love nlp", "i like machine learning",
        "deep learning is fun", "machine learning is fun", "nlp is fun", "i love machine learning",
    )

    /** Sentences padded with boundary markers, which is what makes a bigram model a distribution. */
    fun padded(sentence: String): List<String> = listOf("<s>") + sentence.split(" ") + listOf("</s>")

    val tokens: List<String> get() = corpus.flatMap { padded(it) }

    fun grams(n: Int): List<List<String>> = corpus.flatMap { s ->
        val t = padded(s)
        if (t.size < n) emptyList() else (0..t.size - n).map { t.subList(it, it + n) }
    }

    fun counts(n: Int): Map<List<String>, Int> = grams(n).groupingBy { it }.eachCount()

    /** Vocabulary: the sparsity argument, measured. Types climb toward tokens as n grows. */
    fun typeCount(n: Int): Int = counts(n).size
    fun tokenCount(n: Int): Int = grams(n).size

    val vocabulary: Set<String> get() = tokens.toSet()
    val vocabSize: Int get() = vocabulary.size

    /** Maximum-likelihood bigram probability: count(w₁w₂) / count(w₁). */
    fun mle(first: String, second: String): Double {
        val bigram = counts(2)[listOf(first, second)] ?: 0
        val unigram = counts(1)[listOf(first)] ?: 0
        return if (unigram == 0) 0.0 else bigram.toDouble() / unigram
    }

    /** Add-k (Lidstone) smoothing over the whole vocabulary. */
    fun smoothed(first: String, second: String, k: Double): Double {
        val bigram = counts(2)[listOf(first, second)] ?: 0
        val unigram = counts(1)[listOf(first)] ?: 0
        return (bigram + k) / (unigram + k * vocabSize)
    }

    /** Held-out sentence containing one bigram the corpus never shows. */
    val heldOut = "i love deep learning"

    fun bigramsOf(sentence: String): List<Pair<String, String>> =
        padded(sentence).zipWithNext()

    val unseenBigrams: List<Pair<String, String>>
        get() = bigramsOf(heldOut).filter { (a, b) -> counts(2)[listOf(a, b)] == null }

    /**
     * Perplexity = exp(−(1/N) Σ ln p). Returns [Double.POSITIVE_INFINITY] the moment any bigram has
     * probability zero, which is exactly what happens to the MLE model on the held-out sentence.
     */
    fun perplexity(sentence: String, k: Double): Double {
        val pairs = bigramsOf(sentence)
        var logSum = 0.0
        for ((a, b) in pairs) {
            val p = if (k == 0.0) mle(a, b) else smoothed(a, b, k)
            if (p <= 0.0) return Double.POSITIVE_INFINITY
            logSum += ln(p)
        }
        return exp(-logSum / pairs.size)
    }
}
