package com.algora.app.feature.topics

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

// ── Naive Bayes variants, as arithmetic you can follow ───────────────────────
// The four discrete variants differ only in what they treat as a "feature": counts, presence,
// counts-of-every-other-class, or a category index. That is easiest to see on one worked example, so
// these share a tiny corpus and a single document, and each frame builder walks the same document
// through a different likelihood. Every number below is computed, never written into a string.

internal class NbDoc(val label: Int, val tokens: List<String>)

// Two-class corpus, deliberately tiny so the counts can be shown in full. Class 1 is "sports",
// class 0 is "politics". "great" is shared; "goal" and "vote" are the discriminating terms.
internal val nbCorpus = listOf(
    NbDoc(1, listOf("goal", "goal", "great", "match")),
    NbDoc(1, listOf("goal", "match", "match", "great")),
    NbDoc(1, listOf("goal", "team", "great")),
    NbDoc(0, listOf("vote", "vote", "great", "policy")),
    NbDoc(0, listOf("vote", "policy", "great")),
)

internal val nbVocabulary = listOf("goal", "match", "team", "vote", "policy", "great")

internal val nbTestDoc = listOf("goal", "goal", "great")

internal val nbClassNames = mapOf(1 to "sports", 0 to "politics")

private fun docsOf(label: Int, corpus: List<NbDoc>) = corpus.filter { it.label == label }

// ── Multinomial: counts, with Laplace smoothing ──────────────────────────────

internal class MultinomialModel(
    val logPrior: Map<Int, Double>,
    val logLikelihood: Map<Int, Map<String, Double>>,
    val counts: Map<Int, Map<String, Int>>,
    val totals: Map<Int, Int>,
)

internal fun fitMultinomial(corpus: List<NbDoc> = nbCorpus, alpha: Double = 1.0): MultinomialModel {
    val labels = corpus.map { it.label }.distinct().sorted()
    val counts = labels.associateWith { label ->
        val bag = docsOf(label, corpus).flatMap { it.tokens }
        nbVocabulary.associateWith { term -> bag.count { it == term } }
    }
    val totals = counts.mapValues { (_, m) -> m.values.sum() }
    val logLikelihood = labels.associateWith { label ->
        val total = totals.getValue(label)
        nbVocabulary.associateWith { term ->
            ln((counts.getValue(label).getValue(term) + alpha) / (total + alpha * nbVocabulary.size))
        }
    }
    val logPrior = labels.associateWith { label ->
        ln(docsOf(label, corpus).size.toDouble() / corpus.size)
    }
    return MultinomialModel(logPrior, logLikelihood, counts, totals)
}

// ── Bernoulli: presence and absence ──────────────────────────────────────────

internal class BernoulliModel(
    val logPrior: Map<Int, Double>,
    val presence: Map<Int, Map<String, Double>>, // P(term present | class)
    val docCounts: Map<Int, Map<String, Int>>,
    val docTotals: Map<Int, Int>,
)

internal fun fitBernoulli(corpus: List<NbDoc> = nbCorpus, alpha: Double = 1.0): BernoulliModel {
    val labels = corpus.map { it.label }.distinct().sorted()
    val docCounts = labels.associateWith { label ->
        val docs = docsOf(label, corpus)
        nbVocabulary.associateWith { term -> docs.count { term in it.tokens } }
    }
    val docTotals = labels.associateWith { docsOf(it, corpus).size }
    val presence = labels.associateWith { label ->
        val n = docTotals.getValue(label)
        nbVocabulary.associateWith { term ->
            (docCounts.getValue(label).getValue(term) + alpha) / (n + 2 * alpha)
        }
    }
    val logPrior = labels.associateWith { ln(docTotals.getValue(it).toDouble() / corpus.size) }
    return BernoulliModel(logPrior, presence, docCounts, docTotals)
}

// Bernoulli scores every term in the vocabulary, not merely the ones present — an absent term
// contributes log(1 − P(term|class)), which is where it differs from multinomial.
internal fun bernoulliScore(model: BernoulliModel, label: Int, doc: List<String>): Double {
    var score = model.logPrior.getValue(label)
    nbVocabulary.forEach { term ->
        val p = model.presence.getValue(label).getValue(term)
        score += if (term in doc) ln(p) else ln(1.0 - p)
    }
    return score
}

internal fun bernoulliTermContribution(model: BernoulliModel, label: Int, term: String, doc: List<String>): Double {
    val p = model.presence.getValue(label).getValue(term)
    return if (term in doc) ln(p) else ln(1.0 - p)
}

// ── Complement: estimate each class from everything that is NOT it ───────────

internal class ComplementModel(
    val weights: Map<Int, Map<String, Double>>,
    val complementCounts: Map<Int, Map<String, Int>>,
    val complementTotals: Map<Int, Int>,
)

// Rennie et al.'s weight for (class, term) is log of the term's smoothed frequency in every OTHER
// class. A term that is rare outside a class gets a strongly negative weight, so under the argmin
// decision rule it pulls that class's score down — the weight measures evidence *against*, and the
// least-evidence-against class wins. Weights are then L1-normalized per class, which is the step
// that removes the majority class's magnitude advantage.
internal fun fitComplement(corpus: List<NbDoc>, alpha: Double = 1.0): ComplementModel {
    val labels = corpus.map { it.label }.distinct().sorted()
    val complementCounts = labels.associateWith { label ->
        val others = corpus.filter { it.label != label }.flatMap { it.tokens }
        nbVocabulary.associateWith { term -> others.count { it == term } }
    }
    val complementTotals = complementCounts.mapValues { (_, m) -> m.values.sum() }
    val raw = labels.associateWith { label ->
        val total = complementTotals.getValue(label)
        nbVocabulary.associateWith { term ->
            ln((complementCounts.getValue(label).getValue(term) + alpha) / (total + alpha * nbVocabulary.size))
        }
    }
    val weights = raw.mapValues { (_, m) ->
        val norm = m.values.sumOf { abs(it) }.takeIf { it > 1e-12 } ?: 1.0
        m.mapValues { (_, v) -> v / norm }
    }
    return ComplementModel(weights, complementCounts, complementTotals)
}

// A deliberately lopsided corpus: 8 politics documents to 2 sports, so plain multinomial's prior and
// per-class totals both favour the majority.
internal val nbImbalancedCorpus: List<NbDoc> = buildList {
    add(NbDoc(1, listOf("goal", "goal", "match")))
    add(NbDoc(1, listOf("goal", "match", "great")))
    repeat(8) { i ->
        add(NbDoc(0, listOf("vote", "policy", "great", if (i % 2 == 0) "vote" else "policy")))
    }
}

// ── Categorical: one table per feature, features are unordered labels ────────

internal val catFeatureNames = listOf("Outlook", "Temp", "Humidity", "Windy")

internal val catFeatureValues = listOf(
    listOf("sunny", "overcast", "rain"),
    listOf("hot", "mild", "cool"),
    listOf("high", "normal"),
    listOf("false", "true"),
)

// The classic play-tennis table. Chosen because the answer is checkable by hand.
internal val catTraining: List<Pair<List<String>, Int>> = listOf(
    listOf("sunny", "hot", "high", "false") to 0,
    listOf("sunny", "hot", "high", "true") to 0,
    listOf("overcast", "hot", "high", "false") to 1,
    listOf("rain", "mild", "high", "false") to 1,
    listOf("rain", "cool", "normal", "false") to 1,
    listOf("rain", "cool", "normal", "true") to 0,
    listOf("overcast", "cool", "normal", "true") to 1,
    listOf("sunny", "mild", "high", "false") to 0,
    listOf("sunny", "cool", "normal", "false") to 1,
    listOf("rain", "mild", "normal", "false") to 1,
    listOf("sunny", "mild", "normal", "true") to 1,
    listOf("overcast", "mild", "high", "true") to 1,
    listOf("overcast", "hot", "normal", "false") to 1,
    listOf("rain", "mild", "high", "true") to 0,
)

internal val catTestRow = listOf("sunny", "cool", "high", "true")

internal class CategoricalModel(
    val logPrior: Map<Int, Double>,
    // [class][featureIndex][value] -> log P(value | class)
    val logTables: Map<Int, List<Map<String, Double>>>,
    val rawCounts: Map<Int, List<Map<String, Int>>>,
    val classCounts: Map<Int, Int>,
)

internal fun fitCategorical(alpha: Double = 1.0): CategoricalModel {
    val labels = listOf(0, 1)
    val classCounts = labels.associateWith { label -> catTraining.count { it.second == label } }
    val rawCounts = labels.associateWith { label ->
        catFeatureValues.mapIndexed { featureIndex, values ->
            values.associateWith { value ->
                catTraining.count { it.second == label && it.first[featureIndex] == value }
            }
        }
    }
    val logTables = labels.associateWith { label ->
        val n = classCounts.getValue(label)
        rawCounts.getValue(label).mapIndexed { featureIndex, counts ->
            val k = catFeatureValues[featureIndex].size
            counts.mapValues { (_, c) -> ln((c + alpha) / (n + alpha * k)) }
        }
    }
    val logPrior = labels.associateWith { ln(classCounts.getValue(it).toDouble() / catTraining.size) }
    return CategoricalModel(logPrior, logTables, rawCounts, classCounts)
}

// ── Shared helpers ───────────────────────────────────────────────────────────

internal fun softmaxTwo(scoreA: Double, scoreB: Double): Pair<Double, Double> {
    val peak = maxOf(scoreA, scoreB)
    val a = exp(scoreA - peak)
    val b = exp(scoreB - peak)
    val total = a + b
    return (a / total) to (b / total)
}

internal fun nbFmt(value: Double, digits: Int = 3) = "%.${digits}f".format(value)
