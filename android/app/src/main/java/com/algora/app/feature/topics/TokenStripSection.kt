package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.algora.app.core.ui.theme.SimColors
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

// ── Token strip player ───────────────────────────────────────────────────────
// The text-side labs. Everything from tokenization to a decoding step is "a row of tokens plus one
// numeric view", so the frames share four building blocks and pick whichever they need:
//
//   chips  — the token row, each chip optionally carrying a sub-label (an id, a stem, a weight)
//   bars   — a labelled vector drawn as signed bars (hidden states, embeddings, probabilities)
//   heat   — a token x token matrix (attention weights), optionally with one query row called out
//   rows   — plain label/value lines for the arithmetic a frame is walking through
//
// Numbers are computed here, not hand-written into the strings, so the narration cannot drift from
// what is drawn.

private enum class ChipMark { IDLE, ACTIVE, RESULT, DIM }

private class Chip(val text: String, val sub: String? = null, val mark: ChipMark = ChipMark.IDLE)

private class BarRow(val label: String, val values: List<Float>, val color: Color, val captions: List<String> = emptyList())

private class Heat(
    val rowLabels: List<String>,
    val colLabels: List<String>,
    val values: List<List<Float>>,
    val focusRow: Int? = null,
)

private class TokenFrame(
    val status: String,
    val chips: List<Chip> = emptyList(),
    val chipsLabel: String? = null,
    val bars: List<BarRow> = emptyList(),
    val heat: Heat? = null,
    val rows: List<Pair<String, String>> = emptyList(),
    val readout: String? = null,
)

private class TokenConfig(
    val intro: String,
    val legend: List<Pair<Color, String>>,
    val build: () -> List<TokenFrame>,
)

private val ChipActive = SimColors.Active
private val ChipResult = Color(0xFF7C3AED)
private val BarPositive = SimColors.Blue
private val BarNegative = Color(0xFFEC4899)
private val HeatColor = Color(0xFF6366F1)

private fun softmax(scores: List<Float>, temperature: Float = 1f): List<Float> {
    val scaled = scores.map { it / temperature }
    val max = scaled.maxOrNull() ?: 0f
    val exps = scaled.map { exp(it - max) }
    val sum = exps.sum()
    return exps.map { it / sum }
}

private fun dot(a: List<Float>, b: List<Float>) = a.indices.sumOf { (a[it] * b[it]).toDouble() }.toFloat()

// ── Stemming ─────────────────────────────────────────────────────────────────

// ── Lemmatization ────────────────────────────────────────────────────────────

// ── Bag of words / TF-IDF ────────────────────────────────────────────────────

// ── Word embeddings ──────────────────────────────────────────────────────────

// ── RNN / LSTM ───────────────────────────────────────────────────────────────

// ── Attention ────────────────────────────────────────────────────────────────

private val attentionTokens = listOf("the", "cat", "sat", "on", "mat")

// 2-D query/key vectors per token, hand-set so "sat" attends to its subject and "mat" to "the".
private val queryVectors = mapOf(
    "the" to listOf(0.2f, 0.3f),
    "cat" to listOf(0.9f, 0.1f),
    "sat" to listOf(1.0f, 0.2f),
    "on" to listOf(0.2f, 0.8f),
    "mat" to listOf(0.3f, 0.9f),
)
private val keyVectors = mapOf(
    "the" to listOf(0.1f, 0.5f),
    "cat" to listOf(1.0f, 0.1f),
    "sat" to listOf(0.4f, 0.4f),
    "on" to listOf(0.1f, 0.7f),
    "mat" to listOf(0.2f, 1.0f),
)

// Trained q/k vectors have far larger magnitudes than the readable numbers above; without a scale
// the softmax comes out nearly uniform and the "sat attends to cat" story does not survive contact
// with the arithmetic.
private const val QK_SCALE = 4.8f

private fun attentionScores(query: String): List<Float> = attentionTokens.map { key ->
    QK_SCALE * dot(queryVectors.getValue(query), keyVectors.getValue(key)) / sqrt(2f)
}

// ── Transformers ─────────────────────────────────────────────────────────────

// ── LLM decoding ─────────────────────────────────────────────────────────────

// ── Config ───────────────────────────────────────────────────────────────────

// ── Byte-pair encoding: learn the merges, then replay them ───────────────────

// ── Named entity recognition: BIO tags and why transitions matter ────────────

// ── Retrieval-augmented generation ───────────────────────────────────────────

// ── Naive Bayes variants ─────────────────────────────────────────────────────
// The four discrete variants share one corpus and one test document, so what changes between them is
// only the likelihood. Arithmetic comes from NaiveBayesFrames.kt.

private fun nbDocChips(doc: List<String>, activeIndex: Int = -1) = doc.mapIndexed { i, term ->
    Chip(term, mark = if (i == activeIndex) ChipMark.ACTIVE else ChipMark.IDLE)
}

private fun multinomialFrames(): List<TokenFrame> {
    val model = fitMultinomial()
    val doc = nbTestDoc
    val frames = mutableListOf<TokenFrame>()

    frames.add(
        TokenFrame(
            status = "Five training documents, two classes. Multinomial NB treats a document as a bag of counts: how many times each vocabulary term occurred, with position discarded entirely.",
            chips = nbDocChips(doc),
            chipsLabel = "Document to classify",
            bars = listOf(
                BarRow("Counts · sports", nbVocabulary.map { model.counts.getValue(1).getValue(it).toFloat() }, BarPositive, nbVocabulary),
                BarRow("Counts · politics", nbVocabulary.map { model.counts.getValue(0).getValue(it).toFloat() }, BarNegative, nbVocabulary),
            ),
        ),
    )

    frames.add(
        TokenFrame(
            status = "Laplace smoothing adds α = 1 to every count before dividing. Without it, a term unseen in a class gives probability zero — and one zero anywhere annihilates the entire product, no matter how much other evidence there was.",
            chips = nbDocChips(doc),
            chipsLabel = "Document to classify",
            rows = nbVocabulary.map { term ->
                term to "sports ${nbFmt(exp(model.logLikelihood.getValue(1).getValue(term)))} · politics ${nbFmt(exp(model.logLikelihood.getValue(0).getValue(term)))}"
            },
        ),
    )

    var sports = model.logPrior.getValue(1)
    var politics = model.logPrior.getValue(0)
    frames.add(
        TokenFrame(
            status = "Start from the log priors: 3 of 5 documents are sports, 2 of 5 are politics. Logs throughout, because multiplying dozens of small probabilities underflows to zero in floating point.",
            chips = nbDocChips(doc),
            chipsLabel = "Document to classify",
            rows = listOf(
                "log P(sports)" to nbFmt(sports),
                "log P(politics)" to nbFmt(politics),
            ),
        ),
    )

    doc.forEachIndexed { i, term ->
        sports += model.logLikelihood.getValue(1).getValue(term)
        politics += model.logLikelihood.getValue(0).getValue(term)
        frames.add(
            TokenFrame(
                status = "Add log P(\"$term\" | class) for token ${i + 1}. Each occurrence counts separately — that is what makes it multinomial rather than Bernoulli.",
                chips = nbDocChips(doc, i),
                chipsLabel = "Document to classify",
                rows = listOf(
                    "+ log P($term | sports)" to nbFmt(model.logLikelihood.getValue(1).getValue(term)),
                    "+ log P($term | politics)" to nbFmt(model.logLikelihood.getValue(0).getValue(term)),
                    "running sports" to nbFmt(sports),
                    "running politics" to nbFmt(politics),
                ),
            ),
        )
    }

    val (pSports, pPolitics) = softmaxTwo(sports, politics)
    frames.add(
        TokenFrame(
            status = "Result: ${if (sports > politics) "sports" else "politics"}, at ${nbFmt(maxOf(pSports, pPolitics) * 100, 1)}% posterior. Note how extreme that confidence is on three tokens — naive Bayes ranks well but its probabilities are badly calibrated, because multiplying dependent evidence as if it were independent double-counts it.",
            chips = doc.map { Chip(it, mark = ChipMark.RESULT) },
            chipsLabel = "Document to classify",
            bars = listOf(BarRow("Posterior", listOf(pSports.toFloat(), pPolitics.toFloat()), BarPositive, listOf("sports", "politics"))),
            readout = "argmax = ${if (sports > politics) "sports" else "politics"}",
        ),
    )
    return frames
}

private fun bernoulliFrames(): List<TokenFrame> {
    val model = fitBernoulli()
    val multinomial = fitMultinomial()
    val doc = nbTestDoc
    val frames = mutableListOf<TokenFrame>()

    frames.add(
        TokenFrame(
            status = "Bernoulli NB asks a different question of each term: not \"how many times\", but \"present or not\". The document below contains \"goal\" twice, and Bernoulli will record that as simply present.",
            chips = nbDocChips(doc),
            chipsLabel = "Document to classify",
            bars = listOf(
                BarRow("P(term present | sports)", nbVocabulary.map { model.presence.getValue(1).getValue(it).toFloat() }, BarPositive, nbVocabulary),
                BarRow("P(term present | politics)", nbVocabulary.map { model.presence.getValue(0).getValue(it).toFloat() }, BarNegative, nbVocabulary),
            ),
        ),
    )

    frames.add(
        TokenFrame(
            status = "The consequence, and the real difference: Bernoulli scores every term in the vocabulary, including the ones that are absent. An absent term contributes log(1 − P(term | class)) — so \"vote\" not appearing is itself evidence for sports.",
            chips = nbVocabulary.map { term ->
                Chip(term, sub = if (term in doc) "present" else "absent", mark = if (term in doc) ChipMark.ACTIVE else ChipMark.DIM)
            },
            chipsLabel = "Whole vocabulary, scored",
        ),
    )

    var sports = model.logPrior.getValue(1)
    var politics = model.logPrior.getValue(0)
    nbVocabulary.forEachIndexed { i, term ->
        val sportsTerm = bernoulliTermContribution(model, 1, term, doc)
        val politicsTerm = bernoulliTermContribution(model, 0, term, doc)
        sports += sportsTerm
        politics += politicsTerm
        val present = term in doc
        frames.add(
            TokenFrame(
                status = if (present) {
                    "\"$term\" is present. Add log P(present | class) — once, however many times it occurred."
                } else {
                    "\"$term\" is absent, and multinomial NB would ignore it completely. Bernoulli adds log(1 − P(present | class)), so its absence moves the score by ${nbFmt(sportsTerm - politicsTerm)} in favour of ${if (sportsTerm > politicsTerm) "sports" else "politics"}."
                },
                chips = nbVocabulary.mapIndexed { j, t ->
                    Chip(t, sub = if (t in doc) "present" else "absent", mark = if (j == i) ChipMark.ACTIVE else if (t in doc) ChipMark.IDLE else ChipMark.DIM)
                },
                chipsLabel = "Whole vocabulary, scored",
                rows = listOf(
                    "sports contribution" to nbFmt(sportsTerm),
                    "politics contribution" to nbFmt(politicsTerm),
                    "running sports" to nbFmt(sports),
                    "running politics" to nbFmt(politics),
                ),
            ),
        )
    }

    var mSports = multinomial.logPrior.getValue(1)
    var mPolitics = multinomial.logPrior.getValue(0)
    doc.forEach { term ->
        mSports += multinomial.logLikelihood.getValue(1).getValue(term)
        mPolitics += multinomial.logLikelihood.getValue(0).getValue(term)
    }
    val (bSports, _) = softmaxTwo(sports, politics)
    val (mnSports, _) = softmaxTwo(mSports, mPolitics)
    frames.add(
        TokenFrame(
            status = "Both variants pick ${if (sports > politics) "sports" else "politics"} here, but from different evidence: Bernoulli ${nbFmt(bSports * 100, 1)}% against multinomial's ${nbFmt(mnSports * 100, 1)}%. Bernoulli suits short documents with a small vocabulary, where absence is informative; multinomial suits longer text, where repetition carries the signal.",
            chips = doc.map { Chip(it, mark = ChipMark.RESULT) },
            chipsLabel = "Document to classify",
            bars = listOf(
                BarRow("P(sports)", listOf(bSports.toFloat(), mnSports.toFloat()), BarPositive, listOf("Bernoulli", "Multinomial")),
            ),
        ),
    )
    return frames
}

private fun complementFrames(): List<TokenFrame> {
    val corpus = nbImbalancedCorpus
    val multinomial = fitMultinomial(corpus)
    val complement = fitComplement(corpus)
    val doc = listOf("goal", "match")
    val frames = mutableListOf<TokenFrame>()

    val sportsDocs = corpus.count { it.label == 1 }
    val politicsDocs = corpus.count { it.label == 0 }

    frames.add(
        TokenFrame(
            status = "The same task with a lopsided corpus: $politicsDocs politics documents against $sportsDocs sports. Imbalance hurts multinomial NB twice over — through the prior, and through the per-class totals that its likelihood divides by.",
            chips = nbDocChips(doc),
            chipsLabel = "Document to classify",
            rows = listOf(
                "log P(sports)" to nbFmt(multinomial.logPrior.getValue(1)),
                "log P(politics)" to nbFmt(multinomial.logPrior.getValue(0)),
                "tokens seen · sports" to "${multinomial.totals.getValue(1)}",
                "tokens seen · politics" to "${multinomial.totals.getValue(0)}",
            ),
        ),
    )

    frames.add(
        TokenFrame(
            status = "Complement NB inverts the estimation. For each class it counts how often a term appears in every OTHER class, then negates — a term common outside the class is evidence against it. Because every class's complement is drawn from a similarly large pool, the majority's size advantage disappears.",
            chips = nbDocChips(doc),
            chipsLabel = "Document to classify",
            bars = listOf(
                BarRow("Complement counts · sports", nbVocabulary.map { complement.complementCounts.getValue(1).getValue(it).toFloat() }, BarPositive, nbVocabulary),
                BarRow("Complement counts · politics", nbVocabulary.map { complement.complementCounts.getValue(0).getValue(it).toFloat() }, BarNegative, nbVocabulary),
            ),
        ),
    )

    frames.add(
        TokenFrame(
            status = "Weights are then L1-normalized per class. That second correction is what stops a class with more training tokens accumulating larger magnitudes purely by having been seen more.",
            chips = nbDocChips(doc),
            chipsLabel = "Document to classify",
            bars = listOf(
                BarRow("Normalized weights · sports", nbVocabulary.map { complement.weights.getValue(1).getValue(it).toFloat() }, BarPositive, nbVocabulary),
                BarRow("Normalized weights · politics", nbVocabulary.map { complement.weights.getValue(0).getValue(it).toFloat() }, BarNegative, nbVocabulary),
            ),
        ),
    )

    var mSports = multinomial.logPrior.getValue(1)
    var mPolitics = multinomial.logPrior.getValue(0)
    var cSports = 0.0
    var cPolitics = 0.0
    doc.forEach { term ->
        mSports += multinomial.logLikelihood.getValue(1).getValue(term)
        mPolitics += multinomial.logLikelihood.getValue(0).getValue(term)
        cSports += complement.weights.getValue(1).getValue(term)
        cPolitics += complement.weights.getValue(0).getValue(term)
    }

    // CNB assigns the class with the LOWEST complement score — least evidence against it.
    val cnbPick = if (cSports < cPolitics) "sports" else "politics"
    val mnbPick = if (mSports > mPolitics) "sports" else "politics"

    frames.add(
        TokenFrame(
            status = "The document is unambiguously sports. Multinomial says $mnbPick, complement says $cnbPick. " +
                when {
                    mnbPick == "sports" && cnbPick == "sports" ->
                        "Both get it right: a 4-to-1 skew on five vocabulary terms is not yet enough to flip multinomial. The correction matters at real corpus scale, where the rare class's smoothing denominator swamps its signal. Note the two scores are not comparable — MNB's is a log-posterior, CNB's a sum of normalized weights, and only the ordering within each row means anything."
                    cnbPick == "sports" ->
                        "The majority class dragged multinomial to the wrong answer, and CNB's complement estimation plus per-class normalization is what recovered it."
                    else ->
                        "CNB is the one that got it wrong here — the correction is aimed at large skewed corpora, and on five terms and ten documents it has too little to work with."
                },
            chips = doc.map { Chip(it, mark = ChipMark.RESULT) },
            chipsLabel = "Document to classify",
            rows = listOf(
                "MNB · sports" to nbFmt(mSports),
                "MNB · politics" to nbFmt(mPolitics),
                "CNB · sports (lower wins)" to nbFmt(cSports),
                "CNB · politics (lower wins)" to nbFmt(cPolitics),
            ),
            readout = "MNB → $mnbPick · CNB → $cnbPick",
        ),
    )
    return frames
}

private fun categoricalFrames(): List<TokenFrame> {
    val model = fitCategorical()
    val row = catTestRow
    val frames = mutableListOf<TokenFrame>()

    frames.add(
        TokenFrame(
            status = "Categorical NB handles features that are unordered labels — \"sunny\" is not greater than \"rain\", and encoding them as 0/1/2 and reaching for a Gaussian would invent an ordering that does not exist. Instead each feature gets its own table.",
            chips = row.mapIndexed { i, v -> Chip(v, sub = catFeatureNames[i]) },
            chipsLabel = "Row to classify",
            rows = listOf(
                "training rows" to "${catTraining.size}",
                "play = yes" to "${model.classCounts.getValue(1)}",
                "play = no" to "${model.classCounts.getValue(0)}",
            ),
        ),
    )

    catFeatureNames.forEachIndexed { featureIndex, name ->
        val values = catFeatureValues[featureIndex]
        frames.add(
            TokenFrame(
                status = "Table for $name: a count for every value, in every class. With ${values.size} values and 2 classes that is ${values.size * 2} parameters for this feature alone — and the parameter count grows with the number of distinct values, which is why high-cardinality features need care.",
                chips = row.mapIndexed { i, v ->
                    Chip(v, sub = catFeatureNames[i], mark = if (i == featureIndex) ChipMark.ACTIVE else ChipMark.IDLE)
                },
                chipsLabel = "Row to classify",
                bars = listOf(
                    BarRow("$name · yes", values.map { model.rawCounts.getValue(1)[featureIndex].getValue(it).toFloat() }, BarPositive, values),
                    BarRow("$name · no", values.map { model.rawCounts.getValue(0)[featureIndex].getValue(it).toFloat() }, BarNegative, values),
                ),
            ),
        )
    }

    var yes = model.logPrior.getValue(1)
    var no = model.logPrior.getValue(0)
    row.forEachIndexed { featureIndex, value ->
        val yesTerm = model.logTables.getValue(1)[featureIndex].getValue(value)
        val noTerm = model.logTables.getValue(0)[featureIndex].getValue(value)
        yes += yesTerm
        no += noTerm
        val rawYes = model.rawCounts.getValue(1)[featureIndex].getValue(value)
        frames.add(
            TokenFrame(
                status = if (rawYes == 0) {
                    "${catFeatureNames[featureIndex]} = \"$value\" never co-occurs with play=yes in the training data. Unsmoothed that would be P = 0, which would zero out the whole product regardless of the other three features. Laplace smoothing makes it small instead of fatal."
                } else {
                    "${catFeatureNames[featureIndex]} = \"$value\": add log P(value | class) for each class. Features are combined as if independent given the class — that is the naive assumption, and it is why only one table per feature is needed rather than a joint table over all four."
                },
                chips = row.mapIndexed { i, v ->
                    Chip(v, sub = catFeatureNames[i], mark = if (i == featureIndex) ChipMark.ACTIVE else if (i < featureIndex) ChipMark.RESULT else ChipMark.IDLE)
                },
                chipsLabel = "Row to classify",
                rows = listOf(
                    "+ log P($value | yes)" to nbFmt(yesTerm),
                    "+ log P($value | no)" to nbFmt(noTerm),
                    "running yes" to nbFmt(yes),
                    "running no" to nbFmt(no),
                ),
            ),
        )
    }

    val (pYes, pNo) = softmaxTwo(yes, no)
    frames.add(
        TokenFrame(
            status = "Prediction: play = ${if (yes > no) "yes" else "no"}, at ${nbFmt(maxOf(pYes, pNo) * 100, 1)}%. A joint table over all four features would need ${catFeatureValues.fold(1) { acc, v -> acc * v.size }} cells per class and ${catTraining.size} training rows to fill them; the naive assumption reduces that to ${catFeatureValues.sumOf { it.size }} per class.",
            chips = row.mapIndexed { i, v -> Chip(v, sub = catFeatureNames[i], mark = ChipMark.RESULT) },
            chipsLabel = "Row to classify",
            bars = listOf(BarRow("Posterior", listOf(pYes.toFloat(), pNo.toFloat()), BarPositive, listOf("yes", "no"))),
            readout = "argmax = play ${if (yes > no) "yes" else "no"}",
        ),
    )
    return frames
}

// ── D1 · Stop word removal ───────────────────────────────────────────────────

// ── D1 · Lowercasing and cleaning ────────────────────────────────────────────

// ── D1 · Regular expressions ─────────────────────────────────────────────────

// ── D1 · N-grams ─────────────────────────────────────────────────────────────

// ── D1 · Hidden Markov model ─────────────────────────────────────────────────

// ── D1 · Jaccard similarity ──────────────────────────────────────────────────

// ── D4 · Positional encodings ────────────────────────────────────────────────

// ── D4 · BART: denoising autoencoding ────────────────────────────────────────

// ── D4 · XLNet: permutation language modelling ──────────────────────────────

// ── D4 · Mixture of experts ─────────────────────────────────────────────────

// ── D4 · Frontier model families ────────────────────────────────────────────

// ── D2 · Part-of-speech tagging ──────────────────────────────────────────────

// ── D2 · Chunking ────────────────────────────────────────────────────────────

// ── D2 · Coreference resolution ─────────────────────────────────────────────

// ── D2 · Lexicon sentiment ───────────────────────────────────────────────────

// ── D3 · word2vec: CBOW ──────────────────────────────────────────────────────

// ── D3 · word2vec: skip-gram ────────────────────────────────────────────────

// ── D3 · FastText: subword composition ──────────────────────────────────────

// ── D5 · Prompt engineering ──────────────────────────────────────────────────

// ── D5 · ReAct ───────────────────────────────────────────────────────────────

// ── D5 · Agents and tool use ─────────────────────────────────────────────────

// ── C5 · Bidirectional RNNs ──────────────────────────────────────────────────
// `BiRnnLab` trains two taggers on a corpus of garden-path minimal pairs. What makes the comparison
// worth drawing is that the left-to-right model's ceiling is enumerated from the corpus before
// either model exists, so the frames below are checking a prediction rather than reporting a score.

// ── C5 · Encoder-decoder ─────────────────────────────────────────────────────
// `Seq2SeqLab` trains two identical models on a copy task. The only difference between them is the
// order the encoder reads the source, which is Sutskever et al.'s reversal trick — and on a copy
// task it is exactly the right experiment, because nothing else in the setup moves.

// ── C5 · Seq2Seq decoding ────────────────────────────────────────────────────
// The same trained model as the encoder-decoder lab, decoded three ways. The lab's argument is the
// error split: separating "the search missed it" from "the model prefers the wrong answer" turns
// beam width from a knob you turn hopefully into one with a measurable ceiling.

// ── C6 · Self- and cross-attention ───────────────────────────────────────────
// The centrepiece is the model C5's encoder-decoder topic ended by pointing at: the same task, the
// same data and the same budget, with cross-attention instead of a single context vector — plus a
// fixed-vector model widened until its parameter count matches, so the result cannot be explained
// by size.

// ── C6 · Multi-head attention ────────────────────────────────────────────────

// ── C6 · BERT ────────────────────────────────────────────────────────────────

// ── C6 · GPT ─────────────────────────────────────────────────────────────────

// ── C6 · T5 ──────────────────────────────────────────────────────────────────

// ── C6 · RoBERTa ─────────────────────────────────────────────────────────────

// ── C6 · DistilBERT ──────────────────────────────────────────────────────────

// ── C6 · Hugging Face tokenizers ─────────────────────────────────────────────

private val nbLegend = listOf(
    ChipActive to "Current",
    ChipResult to "Scored",
)

private val tokenConfigs = mapOf(

    "multinomial_nb" to TokenConfig(
        intro = "One document classified end to end: counts, Laplace smoothing, log priors, then a running sum per class.",
        legend = nbLegend,
        build = ::multinomialFrames,
    ),
    "bernoulli_nb" to TokenConfig(
        intro = "The same document under a presence/absence likelihood — including the terms that are not there, which is the whole difference.",
        legend = nbLegend,
        build = ::bernoulliFrames,
    ),
    "complement_nb" to TokenConfig(
        intro = "An 8-to-2 imbalanced corpus, scored by multinomial and complement side by side.",
        legend = nbLegend,
        build = ::complementFrames,
    ),
    "categorical_nb" to TokenConfig(
        intro = "Unordered discrete features: one count table per feature, Laplace smoothing, and a zero cell that would otherwise be fatal.",
        legend = nbLegend,
        build = ::categoricalFrames,
    ),

)

private fun tokenConfigFor(topicId: String): TokenConfig =
    tokenConfigs[topicId] ?: tokenConfigs.values.first()

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun TokenStripSection(topicId: String) {
    val config = remember(topicId) { tokenConfigFor(topicId) }
    val frames = remember(config) { config.build() }
    val playback = rememberPlaybackState(key = config, stepCount = frames.size, initialSpeedMs = 900f)
    val frame = frames[playback.index.coerceIn(0, frames.lastIndex)]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            LabIntro(config.intro)

            if (frame.chips.isNotEmpty()) {
                frame.chipsLabel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                ChipStrip(frame.chips, modifier = Modifier.padding(top = 6.dp))
            }

            frame.bars.forEach { bar -> VectorBars(bar, modifier = Modifier.padding(top = 12.dp)) }

            frame.heat?.let { HeatGrid(it, modifier = Modifier.padding(top = 12.dp)) }

            frame.rows.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(120.dp),
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }

            frame.readout?.let { ReadoutChips(it, Modifier.padding(top = 14.dp)) }

            LabCaption(frame.status, Modifier.padding(top = 14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                config.legend.forEach { (color, label) -> TokenLegend(color, label) }
            }

            PlaybackTransport(playback, captions = frames.map { it.status })
        }
    }
}

@Composable
private fun TokenLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ChipStrip(chips: List<Chip>, modifier: Modifier = Modifier) {
    // No FlowRow dependency: chunk instead. Rows are balanced rather than greedy — 5 chips read
    // better as 3 + 2 than as 4 + 1.
    val rowCount = ((chips.size + 3) / 4).coerceAtLeast(1)
    val perRow = ((chips.size + rowCount - 1) / rowCount).coerceAtLeast(1)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.chunked(perRow).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { chip ->
                    val background = when (chip.mark) {
                        ChipMark.ACTIVE -> ChipActive
                        ChipMark.RESULT -> ChipResult
                        ChipMark.DIM -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        ChipMark.IDLE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                    val foreground = when (chip.mark) {
                        ChipMark.RESULT -> Color.White
                        ChipMark.ACTIVE -> Color(0xFF1F2937)
                        ChipMark.DIM -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ChipMark.IDLE -> MaterialTheme.colorScheme.onSurface
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(background, RoundedCornerShape(8.dp))
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            chip.text,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = foreground,
                            textAlign = TextAlign.Center,
                        )
                        chip.sub?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = foreground.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                repeat(perRow - row.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun VectorBars(bar: BarRow, modifier: Modifier = Modifier) {
    val axis = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val peak = bar.values.maxOfOrNull { abs(it) }?.coerceAtLeast(0.001f) ?: 1f

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            bar.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(modifier = Modifier.fillMaxWidth().height(46.dp).padding(top = 4.dp)) {
            val slot = size.width / bar.values.size
            val mid = size.height / 2f
            drawLine(color = axis, start = Offset(0f, mid), end = Offset(size.width, mid), strokeWidth = 1.5.dp.toPx())
            bar.values.forEachIndexed { index, value ->
                val height = (abs(value) / peak) * (size.height / 2f - 2f)
                val left = index * slot + slot * 0.2f
                val width = slot * 0.6f
                val top = if (value >= 0f) mid - height else mid
                drawRoundRect(
                    color = if (value >= 0f) bar.color else BarNegative,
                    topLeft = Offset(left, top),
                    size = Size(width, height.coerceAtLeast(1.5f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                )
            }
        }
        if (bar.captions.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                bar.captions.forEach { caption ->
                    Text(
                        caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatGrid(heat: Heat, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            HeatCell("", Modifier.weight(1f))
            heat.colLabels.forEach { HeatCell(it, Modifier.weight(1f), header = true) }
        }
        heat.values.forEachIndexed { r, row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                HeatCell(heat.rowLabels[r], Modifier.weight(1f), header = true, focused = heat.focusRow == r)
                row.forEach { value ->
                    HeatCell(
                        "%.2f".format(value),
                        Modifier.weight(1f),
                        // Weight drives the fill so a row reads at a glance; the number is there for
                        // the frames that talk about a specific one.
                        fill = HeatColor.copy(alpha = 0.12f + 0.78f * value.coerceIn(0f, 1f)),
                        strong = value > 0.45f,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatCell(
    text: String,
    modifier: Modifier = Modifier,
    header: Boolean = false,
    focused: Boolean = false,
    fill: Color? = null,
    strong: Boolean = false,
) {
    val background = when {
        focused -> ChipActive
        header -> MaterialTheme.colorScheme.surfaceVariant
        else -> fill ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    Box(
        modifier = modifier
            .padding(1.5.dp)
            .background(background, RoundedCornerShape(5.dp))
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (header || strong) FontWeight.Bold else FontWeight.Normal,
            color = when {
                focused -> Color(0xFF1F2937)
                strong -> Color.White
                else -> MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.Center,
        )
    }
}

internal val tokenStripTopicIds: Set<String> get() = tokenConfigs.keys

/**
 * Frame guard, added with D1 — this widget carries a third of the AI section's labs and had none.
 * Beyond "the builder runs", it checks the two things this renderer silently swallows: a bar row
 * whose captions do not line up with its values (the caption is dropped, so the wrong number is
 * read off the wrong bar), and a heat grid whose row/column labels do not match its matrix.
 */
internal fun tokenStripFrameCount(topicId: String): Int {
    val frames = tokenConfigFor(topicId).build()
    frames.forEachIndexed { index, frame ->
        frame.bars.forEach { bar ->
            require(bar.values.isNotEmpty()) { "$topicId frame $index draws an empty bar row '${bar.label}'" }
            require(bar.captions.isEmpty() || bar.captions.size == bar.values.size) {
                "$topicId frame $index has ${bar.captions.size} captions for ${bar.values.size} bars in '${bar.label}'"
            }
            require(bar.values.all { it.isFinite() }) { "$topicId frame $index has a non-finite bar in '${bar.label}'" }
        }
        frame.heat?.let { heat ->
            require(heat.values.size == heat.rowLabels.size) {
                "$topicId frame $index has ${heat.values.size} heat rows for ${heat.rowLabels.size} labels"
            }
            require(heat.values.all { it.size == heat.colLabels.size }) {
                "$topicId frame $index has heat rows that do not match its ${heat.colLabels.size} column labels"
            }
            heat.focusRow?.let { row ->
                require(row in heat.values.indices) { "$topicId frame $index focuses heat row $row, which does not exist" }
            }
        }
        require(frame.status.isNotBlank()) { "$topicId frame $index has no status line" }
    }
    return frames.size
}
