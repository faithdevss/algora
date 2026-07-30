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

private val ChipActive = Color(0xFFFACC15)
private val ChipResult = Color(0xFF7C3AED)
private val BarPositive = SimColors.Blue
private val BarNegative = Color(0xFFEC4899)
private val HeatColor = Color(0xFF6366F1)

private val tokenLegend = listOf(
    ChipActive to "Current",
    ChipResult to "Output",
)

private val vectorLegend = listOf(
    BarPositive to "Positive",
    BarNegative to "Negative",
    ChipResult to "Result",
)

private val attentionLegend = listOf(
    ChipActive to "Query",
    HeatColor to "Weight",
    ChipResult to "Output",
)

private fun softmax(scores: List<Float>, temperature: Float = 1f): List<Float> {
    val scaled = scores.map { it / temperature }
    val max = scaled.maxOrNull() ?: 0f
    val exps = scaled.map { exp(it - max) }
    val sum = exps.sum()
    return exps.map { it / sum }
}

private fun dot(a: List<Float>, b: List<Float>) = a.indices.sumOf { (a[it] * b[it]).toDouble() }.toFloat()

private fun cosine(a: List<Float>, b: List<Float>) = dot(a, b) / (sqrt(dot(a, a)) * sqrt(dot(b, b)))

private fun pct(value: Float) = "${"%.0f".format(value * 100)}%"

// ── Tokenization ─────────────────────────────────────────────────────────────

private fun tokenizationFrames(): List<TokenFrame> {
    val raw = "unbelievable tokenizers split words"
    val words = raw.split(" ")
    // A toy merge table: the two long words are out of vocabulary and get broken up, the short ones
    // survive whole — which is the entire point of subword tokenization.
    val subwords = mapOf(
        "unbelievable" to listOf("un", "believ", "able"),
        "tokenizers" to listOf("token", "izer", "s"),
        "split" to listOf("split"),
        "words" to listOf("word", "s"),
    )
    val vocabulary = listOf("un", "believ", "able", "token", "izer", "s", "split", "word")
    val pieces = words.flatMap { subwords.getValue(it) }
    val frames = mutableListOf<TokenFrame>()

    frames += TokenFrame(
        status = "A model cannot consume a string. Everything below is the work of turning these ${raw.length} " +
            "characters into integers.",
        chips = listOf(Chip(raw)),
        chipsLabel = "raw text",
    )
    frames += TokenFrame(
        status = "Splitting on whitespace gives ${words.size} words. Simple, and it fails on the first word the " +
            "vocabulary has never seen.",
        chips = words.map { Chip(it) },
        chipsLabel = "whitespace tokens",
    )
    words.forEachIndexed { index, word ->
        val parts = subwords.getValue(word)
        frames += TokenFrame(
            status = if (parts.size == 1) {
                "\"$word\" is already in the vocabulary — one piece."
            } else {
                "\"$word\" is not in the vocabulary, so it is broken into ${parts.size} pieces that are: " +
                    "${parts.joinToString(" + ")}. Rare words cost more tokens; they never become unknown."
            },
            chips = words.mapIndexed { i, w -> Chip(w, mark = if (i == index) ChipMark.ACTIVE else ChipMark.DIM) },
            chipsLabel = "whitespace tokens",
            rows = listOf("subwords" to parts.joinToString(" · ")),
        )
    }
    frames += TokenFrame(
        status = "${words.size} words became ${pieces.size} subword tokens, each one an index into the vocabulary. " +
            "That integer sequence is what the model actually reads.",
        chips = pieces.map { Chip(it, sub = vocabulary.indexOf(it).toString(), mark = ChipMark.RESULT) },
        chipsLabel = "subword tokens (id below)",
        readout = "${words.size} words → ${pieces.size} tokens",
    )
    return frames
}

// ── Stemming ─────────────────────────────────────────────────────────────────

private class Rule(val suffix: String, val replacement: String, val note: String)

private fun stemmingFrames(): List<TokenFrame> {
    val words = listOf("running", "flies", "studies", "happily", "better")
    val rules = listOf(
        Rule("ing", "", "strip -ing"),
        Rule("ies", "i", "-ies → -i"),
        Rule("ily", "y", "-ily → -y"),
    )
    val stems = mutableListOf<String>()
    val frames = mutableListOf<TokenFrame>()

    frames += TokenFrame(
        status = "A stemmer is a list of suffix rules applied in order. It has no dictionary and no idea what any of " +
            "these words mean.",
        chips = words.map { Chip(it) },
        chipsLabel = "input",
    )

    words.forEachIndexed { index, word ->
        val rule = rules.firstOrNull { word.endsWith(it.suffix) }
        val stem = if (rule == null) word else word.dropLast(rule.suffix.length) + rule.replacement
        stems += stem
        frames += TokenFrame(
            status = if (rule == null) {
                "\"$word\" matches no rule, so it passes through unchanged — a stemmer cannot know that its root is " +
                    "\"good\"."
            } else {
                "\"$word\" ends in -${rule.suffix}: ${rule.note} → \"$stem\"."
            },
            chips = words.mapIndexed { i, w ->
                Chip(w, sub = stems.getOrNull(i), mark = if (i == index) ChipMark.ACTIVE else if (i < index) ChipMark.IDLE else ChipMark.DIM)
            },
            chipsLabel = "input (stem below)",
        )
    }

    frames += TokenFrame(
        status = "\"studi\" is not a word, and that is fine: stems only have to collide for related words, not to be " +
            "readable. The cost is that \"better\" never reaches \"good\".",
        chips = words.mapIndexed { i, w -> Chip(w, sub = stems[i], mark = ChipMark.RESULT) },
        chipsLabel = "input (stem below)",
        readout = "${words.size} words → ${stems.distinct().size} distinct stems",
    )
    return frames
}

// ── Lemmatization ────────────────────────────────────────────────────────────

private fun lemmatizationFrames(): List<TokenFrame> {
    val entries = listOf(
        Triple("running", "VERB", "run"),
        Triple("flies", "NOUN", "fly"),
        Triple("studies", "VERB", "study"),
        Triple("better", "ADJ", "good"),
        Triple("mice", "NOUN", "mouse"),
    )
    val stemmed = listOf("runn", "fli", "studi", "better", "mice")
    val frames = mutableListOf<TokenFrame>()

    frames += TokenFrame(
        status = "A lemmatizer needs two things a stemmer does not have: a dictionary, and the part of speech of the " +
            "word in this sentence.",
        chips = entries.map { Chip(it.first) },
        chipsLabel = "input",
    )

    entries.forEachIndexed { index, (word, pos, lemma) ->
        frames += TokenFrame(
            status = "\"$word\" tagged $pos → lemma \"$lemma\"" +
                if (word == "flies") ". Tagged VERB instead, the same string lemmatizes to \"fly\" the action — the " +
                    "POS tag is doing real work." else ".",
            chips = entries.mapIndexed { i, e ->
                Chip(
                    e.first,
                    sub = if (i <= index) e.third else null,
                    mark = if (i == index) ChipMark.ACTIVE else if (i < index) ChipMark.IDLE else ChipMark.DIM,
                )
            },
            chipsLabel = "input (lemma below)",
            rows = listOf("part of speech" to pos, "dictionary lemma" to lemma),
        )
    }

    frames += TokenFrame(
        status = "Side by side with a stemmer: \"better\" → \"good\" and \"mice\" → \"mouse\" are lookups no suffix " +
            "rule can reach, and every lemma is a real word. The price is the dictionary and the tagger.",
        chips = entries.mapIndexed { i, e -> Chip(e.first, sub = "${stemmed[i]} → ${e.third}", mark = ChipMark.RESULT) },
        chipsLabel = "input (stem → lemma)",
    )
    return frames
}

// ── Bag of words / TF-IDF ────────────────────────────────────────────────────

private fun bowTfidfFrames(): List<TokenFrame> {
    val docs = listOf(
        listOf("the", "cat", "sat", "on", "the", "mat"),
        listOf("the", "dog", "sat", "on", "the", "log"),
        listOf("the", "cat", "chased", "the", "dog"),
    )
    val vocabulary = listOf("the", "cat", "sat", "dog", "mat", "log")
    val frames = mutableListOf<TokenFrame>()

    frames += TokenFrame(
        status = "Three short documents, one shared vocabulary. Bag-of-words throws away order and keeps only counts.",
        chips = docs[0].map { Chip(it) },
        chipsLabel = "document 1",
    )

    val counts = docs.map { doc -> vocabulary.map { term -> doc.count { it == term }.toFloat() } }
    docs.indices.forEach { d ->
        frames += TokenFrame(
            status = "Document ${d + 1} as counts: ${vocabulary.indices.filter { counts[d][it] > 0 }
                .joinToString(", ") { "${vocabulary[it]}=${counts[d][it].toInt()}" }}.",
            chips = docs[d].map { Chip(it, mark = ChipMark.IDLE) },
            chipsLabel = "document ${d + 1}",
            bars = listOf(BarRow("counts", counts[d], BarPositive, vocabulary)),
        )
    }

    val df = vocabulary.indices.map { term -> docs.count { doc -> doc.any { it == vocabulary[term] } }.toFloat() }
    val idf = df.map { ln(docs.size / it) }
    frames += TokenFrame(
        status = "Document frequency counts how many documents each term appears in. \"the\" is in all " +
            "${docs.size}, so its idf = ln(${docs.size}/${docs.size}) = 0 — the most common word carries no signal.",
        bars = listOf(
            BarRow("df", df, BarPositive, vocabulary),
            BarRow("idf", idf, ChipResult, vocabulary),
        ),
    )

    val tfidf = vocabulary.indices.map { (counts[0][it] / docs[0].size) * idf[it] }
    frames += TokenFrame(
        status = "TF-IDF for document 1 is term frequency times idf. \"the\" appears twice and still scores 0; " +
            "\"mat\", which appears once and only here, scores highest.",
        chips = docs[0].map { Chip(it, mark = if (it == "mat") ChipMark.RESULT else ChipMark.DIM) },
        chipsLabel = "document 1",
        bars = listOf(BarRow("tf-idf", tfidf, ChipResult, vocabulary)),
        readout = "top term: ${vocabulary[tfidf.indices.maxByOrNull { tfidf[it] }!!]}",
    )
    return frames
}

// ── Word embeddings ──────────────────────────────────────────────────────────

private fun wordEmbeddingFrames(): List<TokenFrame> {
    // Four hand-set dimensions — royalty, male, female, human — chosen so the analogy arithmetic
    // lands exactly on the stored vector for "queen".
    val vectors = linkedMapOf(
        "king" to listOf(0.9f, 0.8f, 0.1f, 0.7f),
        "queen" to listOf(0.9f, 0.1f, 0.9f, 0.7f),
        "man" to listOf(0.2f, 0.9f, 0.1f, 0.7f),
        "woman" to listOf(0.2f, 0.2f, 0.9f, 0.7f),
        // Every component of the royalty vectors is positive, so an all-positive "unrelated" word
        // still scores a high cosine. Apple has to actively point away to be the contrast case.
        "apple" to listOf(-0.8f, 0.1f, 0.1f, -0.9f),
    )
    val dims = listOf("royal", "male", "female", "human")
    val frames = mutableListOf<TokenFrame>()

    frames += TokenFrame(
        status = "Each word is a dense vector. The dimensions are labelled here for readability; in a trained model " +
            "they are learned and nobody names them.",
        chips = vectors.keys.map { Chip(it) },
        bars = vectors.entries.take(2).map { BarRow(it.key, it.value, BarPositive, dims) },
    )

    frames += TokenFrame(
        status = "Similarity is the cosine between vectors: king·queen = ${"%.2f".format(cosine(vectors.getValue("king"), vectors.getValue("queen")))}, " +
            "but king·apple = ${"%.2f".format(cosine(vectors.getValue("king"), vectors.getValue("apple")))}. Nearness in this space is nearness in meaning.",
        chips = listOf(Chip("king", mark = ChipMark.ACTIVE), Chip("queen"), Chip("apple", mark = ChipMark.DIM)),
        bars = listOf(
            BarRow("king", vectors.getValue("king"), BarPositive, dims),
            BarRow("queen", vectors.getValue("queen"), BarPositive, dims),
            BarRow("apple", vectors.getValue("apple"), BarNegative, dims),
        ),
    )

    val analogy = vectors.getValue("king").indices.map {
        vectors.getValue("king")[it] - vectors.getValue("man")[it] + vectors.getValue("woman")[it]
    }
    frames += TokenFrame(
        status = "Subtracting \"man\" and adding \"woman\" moves along the gender direction and leaves everything " +
            "else alone — the arithmetic works because the directions are roughly independent.",
        chips = listOf(Chip("king", mark = ChipMark.ACTIVE), Chip("− man"), Chip("+ woman")),
        bars = listOf(BarRow("king − man + woman", analogy, ChipResult, dims)),
    )

    val ranked = vectors.entries.map { it.key to cosine(analogy, it.value) }.sortedByDescending { it.second }
    frames += TokenFrame(
        status = "The nearest stored vector to that result is \"${ranked.first().first}\" at cosine " +
            "${"%.2f".format(ranked.first().second)}. No rule for royalty or gender was ever written down; it fell " +
            "out of co-occurrence statistics.",
        chips = ranked.map { Chip(it.first, sub = "%.2f".format(it.second), mark = if (it == ranked.first()) ChipMark.RESULT else ChipMark.DIM) },
        chipsLabel = "cosine to king − man + woman",
        readout = "≈ ${ranked.first().first}",
    )
    return frames
}

// ── RNN / LSTM ───────────────────────────────────────────────────────────────

private fun rnnFrames(): List<TokenFrame> {
    val tokens = listOf("the", "movie", "was", "not", "good")
    // Toy 4-dim recurrence: h = tanh(x + 0.6 h). The input vectors are hand-set so "not" flips the
    // sentiment channel that "good" would otherwise raise.
    val inputs = mapOf(
        "the" to listOf(0.1f, 0.0f, 0.0f, 0.1f),
        "movie" to listOf(0.6f, 0.2f, 0.0f, 0.1f),
        "was" to listOf(0.1f, 0.1f, 0.0f, 0.2f),
        "not" to listOf(0.0f, 0.0f, -0.9f, 0.3f),
        "good" to listOf(0.0f, 0.7f, 0.5f, 0.1f),
    )
    var h = List(4) { 0f }
    val frames = mutableListOf<TokenFrame>()
    val dims = listOf("topic", "polarity", "negation", "syntax")

    frames += TokenFrame(
        status = "An RNN reads one token at a time and keeps a single hidden state. Everything it remembers about " +
            "the sentence so far has to fit in these four numbers.",
        chips = tokens.map { Chip(it, mark = ChipMark.DIM) },
        bars = listOf(BarRow("h₀", h, BarPositive, dims)),
    )

    tokens.forEachIndexed { index, token ->
        val x = inputs.getValue(token)
        val previous = h
        h = h.indices.map { tanh(x[it] + 0.6f * previous[it]) }
        frames += TokenFrame(
            status = "Read \"$token\": h = tanh(x + 0.6·h). " + when (token) {
                "not" -> "The negation channel goes sharply negative — and it has to survive the next step for the " +
                    "sentence to be read correctly."
                "good" -> "\"good\" pushes polarity up, but the negation carried over from the previous step is " +
                    "still in the state. That interaction is the whole reason order matters."
                else -> "The state drifts toward what this token contributes."
            },
            chips = tokens.mapIndexed { i, t ->
                Chip(t, mark = if (i == index) ChipMark.ACTIVE else if (i < index) ChipMark.IDLE else ChipMark.DIM)
            },
            bars = listOf(
                BarRow("x (\"$token\")", x, BarNegative, dims),
                BarRow("h${index + 1}", h, BarPositive, dims),
            ),
        )
    }

    val decay = (1..5).map { 0.6f.pow(it) }
    frames += TokenFrame(
        status = "The problem: each step multiplies the earlier signal by the recurrent weight again. After 5 steps " +
            "a 0.6 factor leaves ${"%.2f".format(decay.last())} of it — gradients to early tokens vanish, which is " +
            "exactly what LSTM gates were introduced to fix.",
        bars = listOf(BarRow("influence of h₀ after n steps", decay, BarNegative, (1..5).map { "t+$it" })),
    )

    val forget = 0.95f
    val gated = (1..5).map { forget.pow(it) }
    frames += TokenFrame(
        status = "An LSTM adds a cell state with a forget gate. With the gate near 1 (here $forget) the same five " +
            "steps keep ${"%.2f".format(gated.last())} of the signal — the path from early tokens to the loss stays " +
            "open, which is why LSTMs handle long sentences that plain RNNs drop.",
        bars = listOf(
            BarRow("plain RNN", decay, BarNegative, (1..5).map { "t+$it" }),
            BarRow("LSTM cell (forget ≈ $forget)", gated, ChipResult, (1..5).map { "t+$it" }),
        ),
        readout = "5 steps: ${"%.2f".format(decay.last())} vs ${"%.2f".format(gated.last())}",
    )
    return frames
}

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

private fun attentionMatrix(temperatureScale: Float = 1f): List<List<Float>> =
    attentionTokens.map { softmax(attentionScores(it), temperatureScale) }

private fun attentionFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val queryIndex = attentionTokens.indexOf("sat")
    val query = attentionTokens[queryIndex]
    val scores = attentionScores(query)
    val weights = softmax(scores)
    val matrix = attentionMatrix()

    frames += TokenFrame(
        status = "Attention lets every token look at every other one directly — no state to carry forward, no " +
            "distance penalty. Take \"$query\" as the query.",
        chips = attentionTokens.mapIndexed { i, t -> Chip(t, mark = if (i == queryIndex) ChipMark.ACTIVE else ChipMark.IDLE) },
    )
    frames += TokenFrame(
        status = "Score the query against every key: q·k / √d. Raw scores are unbounded and mean nothing on their own.",
        chips = attentionTokens.mapIndexed { i, t ->
            Chip(t, sub = "%.2f".format(scores[i]), mark = if (i == queryIndex) ChipMark.ACTIVE else ChipMark.IDLE)
        },
        chipsLabel = "score against \"$query\"",
        bars = listOf(BarRow("q·k / √d", scores, BarPositive, attentionTokens)),
    )
    frames += TokenFrame(
        status = "Softmax turns the scores into weights that sum to 1. \"$query\" puts ${pct(weights[attentionTokens.indexOf("cat")])} " +
            "of its attention on \"cat\" — the subject it needs, three tokens away, reached in one step.",
        chips = attentionTokens.mapIndexed { i, t ->
            Chip(t, sub = pct(weights[i]), mark = if (i == queryIndex) ChipMark.ACTIVE else ChipMark.IDLE)
        },
        chipsLabel = "attention from \"$query\"",
        bars = listOf(BarRow("softmax weights", weights, ChipResult, attentionTokens)),
        readout = "weights sum to ${"%.2f".format(weights.sum())}",
    )
    frames += TokenFrame(
        status = "Every token does the same thing at once, so the whole layer is one ${attentionTokens.size}×${attentionTokens.size} matrix. " +
            "Rows are queries, columns are keys, and each row sums to 1.",
        heat = Heat(attentionTokens, attentionTokens, matrix, focusRow = queryIndex),
    )
    frames += TokenFrame(
        status = "That matrix is also the cost: it grows with the square of the sequence length, which is why long " +
            "contexts are expensive and why so much research is about approximating this one product.",
        heat = Heat(attentionTokens, attentionTokens, matrix),
        readout = "${attentionTokens.size}² = ${attentionTokens.size * attentionTokens.size} pairwise scores",
    )
    return frames
}

// ── Transformers ─────────────────────────────────────────────────────────────

private fun transformerFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val layer1 = attentionMatrix()
    // A second block sharpens the same pattern — softmax over scaled scores concentrates the mass.
    val layer2 = attentionMatrix(temperatureScale = 0.5f)

    frames += TokenFrame(
        status = "A transformer block is attention plus a feed-forward network, wrapped in residual connections. " +
            "Stacking those blocks is the whole architecture.",
        chips = attentionTokens.map { Chip(it) },
    )
    frames += TokenFrame(
        status = "Block 1, attention: each token gathers a weighted mix of the others. Position is not implicit " +
            "anywhere here — it has to be added to the embeddings, or the block sees a bag of tokens.",
        heat = Heat(attentionTokens, attentionTokens, layer1),
    )
    frames += TokenFrame(
        status = "Then the residual: output = input + attention(input). The block only has to learn a correction, " +
            "which is what makes stacking dozens of them trainable.",
        chips = attentionTokens.map { Chip(it, sub = "x + Δ", mark = ChipMark.IDLE) },
        rows = listOf(
            "attention" to "mixes information across tokens",
            "feed-forward" to "transforms each token independently",
            "residual + norm" to "keeps the signal path short",
        ),
    )
    frames += TokenFrame(
        status = "Block 2 runs on the block-1 output, so its queries are already context-aware. The same picture " +
            "sharpens: attention concentrates on fewer, more relevant tokens.",
        heat = Heat(attentionTokens, attentionTokens, layer2),
    )
    frames += TokenFrame(
        status = "Depth buys composition, not new mechanisms — every block is the same two operations. Scale that " +
            "stack and the training set and you have an LLM.",
        heat = Heat(attentionTokens, attentionTokens, layer2, focusRow = attentionTokens.indexOf("sat")),
        readout = "2 blocks shown · real models stack 32–100+",
    )
    return frames
}

// ── LLM decoding ─────────────────────────────────────────────────────────────

private fun llmFrames(): List<TokenFrame> {
    val prompt = listOf("The", "capital", "of", "France", "is")
    val vocabulary = listOf("Paris", "London", "a", "the", "cheese")
    val logits = listOf(6.2f, 3.1f, 1.8f, 1.2f, 0.4f)
    val frames = mutableListOf<TokenFrame>()

    frames += TokenFrame(
        status = "An LLM does exactly one thing: given the tokens so far, score every token in the vocabulary as a " +
            "candidate for the next one.",
        chips = prompt.map { Chip(it) },
        chipsLabel = "context",
    )
    frames += TokenFrame(
        status = "The final layer produces one logit per vocabulary entry. These are unnormalised scores — " +
            "comparable to each other and to nothing else.",
        chips = prompt.map { Chip(it, mark = ChipMark.DIM) },
        chipsLabel = "context",
        bars = listOf(BarRow("logits", logits, BarPositive, vocabulary)),
    )

    val probs = softmax(logits)
    frames += TokenFrame(
        status = "Softmax turns them into a distribution: \"${vocabulary[0]}\" at ${pct(probs[0])}, " +
            "\"${vocabulary[1]}\" at ${pct(probs[1])}. Nothing here is a lookup — the model is not retrieving a " +
            "fact, it is ranking continuations.",
        bars = listOf(BarRow("p(next token)", probs, ChipResult, vocabulary)),
        readout = "argmax = ${vocabulary[0]}",
    )

    val cold = softmax(logits, temperature = 0.5f)
    val hot = softmax(logits, temperature = 1.8f)
    frames += TokenFrame(
        status = "Temperature rescales the logits before the softmax. At 0.5 the top token takes ${pct(cold[0])} and " +
            "the output is nearly deterministic; at 1.8 it drops to ${pct(hot[0])} and the tail becomes reachable.",
        bars = listOf(
            BarRow("T = 0.5", cold, BarPositive, vocabulary),
            BarRow("T = 1.8", hot, BarNegative, vocabulary),
        ),
    )

    frames += TokenFrame(
        status = "Append the chosen token and run the whole thing again. Every long answer is this loop — which is " +
            "also why cost grows with the number of tokens generated, not with the difficulty of the question.",
        chips = (prompt + vocabulary[0]).mapIndexed { i, t ->
            Chip(t, mark = if (i == prompt.size) ChipMark.RESULT else ChipMark.IDLE)
        },
        chipsLabel = "context after one step",
        readout = "1 token generated · repeat",
    )
    return frames
}

// ── Config ───────────────────────────────────────────────────────────────────

// ── Byte-pair encoding: learn the merges, then replay them ───────────────────

private fun bpeFrames(): List<TokenFrame> {
    // Word -> frequency. "low"/"lower"/"newest"/"widest" is the canonical BPE worked example.
    val corpus = linkedMapOf("low" to 5, "lower" to 2, "newest" to 6, "widest" to 3)
    var vocab = corpus.mapValues { it.value }
        .mapKeys { (word, _) -> word.map { it.toString() } + "</w>" }
    val merges = mutableListOf<Pair<String, String>>()
    val frames = mutableListOf<TokenFrame>()

    frames += TokenFrame(
        status = "Start from characters. The vocabulary is just the alphabet, so nothing can ever be out of " +
            "vocabulary — the question is only how many pieces a word costs.",
        chips = vocab.keys.first().map { Chip(it) },
        chipsLabel = "\"low\" as characters",
        rows = corpus.map { (word, freq) -> word to "×$freq" },
    )

    repeat(4) { round ->
        val pairs = mutableMapOf<Pair<String, String>, Int>()
        for ((symbols, freq) in vocab) {
            for (i in 0 until symbols.size - 1) {
                val pair = symbols[i] to symbols[i + 1]
                pairs[pair] = (pairs[pair] ?: 0) + freq
            }
        }
        val ranked = pairs.entries.sortedByDescending { it.value }
        val best = ranked.firstOrNull()?.key ?: return@repeat
        val bestCount = pairs.getValue(best)

        frames += TokenFrame(
            status = "Round ${round + 1}: count every adjacent pair, weighted by how often its word appears. " +
                "\"${best.first}${best.second}\" leads with $bestCount occurrences.",
            chips = ranked.take(4).map { (pair, count) ->
                Chip(pair.first + pair.second, sub = count.toString(), mark = if (pair == best) ChipMark.ACTIVE else ChipMark.IDLE)
            },
            chipsLabel = "top adjacent pairs",
            readout = "vocabulary size ${8 + round}",
        )

        merges += best
        vocab = vocab.mapKeys { (symbols, _) ->
            val out = mutableListOf<String>()
            var i = 0
            while (i < symbols.size) {
                if (i < symbols.size - 1 && symbols[i] == best.first && symbols[i + 1] == best.second) {
                    out += best.first + best.second
                    i += 2
                } else {
                    out += symbols[i]
                    i++
                }
            }
            out
        }

        val sample = vocab.keys.first { it.joinToString("").startsWith("newest") || it.size <= 4 }
        frames += TokenFrame(
            status = "Merge it everywhere. \"${best.first}${best.second}\" is now one symbol, and the vocabulary " +
                "grew by exactly one entry — that is the knob: one merge, one token.",
            chips = sample.map { Chip(it, mark = if (it == best.first + best.second) ChipMark.RESULT else ChipMark.IDLE) },
            chipsLabel = "a word after ${round + 1} merge${if (round == 0) "" else "s"}",
            rows = merges.mapIndexed { i, (a, b) -> "merge ${i + 1}" to "$a + $b → $a$b" },
        )
    }

    // Encoding an unseen word replays the merge list in order.
    var unseen = "lowest".map { it.toString() } + "</w>"
    frames += TokenFrame(
        status = "Encoding is not a fresh search: the learned merges are replayed in the order they were found. " +
            "Take \"lowest\" — a word the corpus never contained.",
        chips = unseen.map { Chip(it) },
        chipsLabel = "\"lowest\" as characters",
    )
    merges.forEachIndexed { index, (a, b) ->
        val out = mutableListOf<String>()
        var i = 0
        var applied = false
        while (i < unseen.size) {
            if (i < unseen.size - 1 && unseen[i] == a && unseen[i + 1] == b) {
                out += a + b
                applied = true
                i += 2
            } else {
                out += unseen[i]
                i++
            }
        }
        unseen = out
        if (applied) {
            frames += TokenFrame(
                status = "Merge ${index + 1} ($a + $b) applies, so \"lowest\" absorbs a piece the corpus learned " +
                    "from other words.",
                chips = unseen.map { Chip(it, mark = if (it == a + b) ChipMark.RESULT else ChipMark.IDLE) },
                chipsLabel = "encoding in progress",
            )
        }
    }

    frames += TokenFrame(
        status = "\"lowest\" ends as ${unseen.size} pieces built entirely from parts learned elsewhere. Frequent " +
            "words collapse to a single token, rare ones decompose — and the sequence length that follows is " +
            "what drives attention cost downstream.",
        chips = unseen.map { Chip(it, mark = ChipMark.RESULT) },
        chipsLabel = "final tokens",
        readout = "${unseen.size} tokens · nothing out-of-vocabulary",
    )
    return frames
}

// ── Named entity recognition: BIO tags and why transitions matter ────────────

private fun nerFrames(): List<TokenFrame> {
    val tokens = listOf("Ada", "Lovelace", "joined", "Analytical", "Engine", "Ltd", "in", "London")
    val gold = listOf("B-PER", "I-PER", "O", "B-ORG", "I-ORG", "I-ORG", "O", "B-LOC")
    val frames = mutableListOf<TokenFrame>()

    frames += TokenFrame(
        status = "The task is to find spans, but spans are awkward to predict directly. BIO tagging turns it into " +
            "ordinary per-token classification.",
        chips = tokens.map { Chip(it) },
        chipsLabel = "tokens",
    )

    frames += TokenFrame(
        status = "Three tag shapes: B- starts an entity, I- continues the one before it, O is outside any entity. " +
            "With 3 entity types that is 2×3 + 1 = 7 labels in total.",
        chips = tokens.mapIndexed { i, t -> Chip(t, sub = gold[i], mark = if (gold[i] == "O") ChipMark.DIM else ChipMark.IDLE) },
        chipsLabel = "gold BIO tags",
    )

    // Emission scores: what the encoder alone thinks, per token, before transitions are considered.
    frames += TokenFrame(
        status = "A BiLSTM or transformer encoder reads the whole sentence and emits a score per label per token. " +
            "\"Engine\" is genuinely ambiguous in isolation — it could start its own entity or continue one.",
        chips = tokens.mapIndexed { i, t -> Chip(t, mark = if (i == 4) ChipMark.ACTIVE else ChipMark.IDLE) },
        chipsLabel = "encoder pass",
        bars = listOf(
            BarRow("emissions for \"Engine\"", listOf(0.42f, 0.38f, 0.12f, 0.08f), HeatColor,
                captions = listOf("I-ORG", "B-ORG", "O", "I-PER")),
        ),
        readout = "argmax per token would take I-ORG here — narrowly",
    )

    frames += TokenFrame(
        status = "Taking the argmax token by token is what produces illegal output: O followed by I-PER, or an " +
            "I-ORG continuing a person. Nothing in a per-token decision knows the sequence has to be coherent.",
        chips = listOf(
            Chip("Ada", sub = "B-PER"), Chip("Lovelace", sub = "I-PER"), Chip("joined", sub = "O"),
            Chip("Analytical", sub = "O", mark = ChipMark.ACTIVE),
            Chip("Engine", sub = "I-ORG", mark = ChipMark.ACTIVE),
            Chip("Ltd", sub = "I-ORG"), Chip("in", sub = "O"), Chip("London", sub = "B-LOC"),
        ),
        chipsLabel = "greedy per-token argmax — invalid",
        readout = "O → I-ORG is not a legal transition",
    )

    frames += TokenFrame(
        status = "A CRF layer adds learned transition scores on top of the emissions, with impossible transitions " +
            "driven to −∞. Viterbi then decodes the best *whole sequence* rather than the best isolated guesses.",
        heat = Heat(
            rowLabels = listOf("O", "B-ORG", "I-ORG", "B-PER"),
            colLabels = listOf("O", "B-ORG", "I-ORG", "B-PER"),
            values = listOf(
                listOf(0.6f, 0.5f, 0.0f, 0.5f),
                listOf(0.4f, 0.1f, 0.9f, 0.1f),
                listOf(0.4f, 0.2f, 0.8f, 0.1f),
                listOf(0.3f, 0.2f, 0.0f, 0.1f),
            ),
            focusRow = 0,
        ),
        readout = "row O → column I-ORG scores 0: that path can never win",
    )

    frames += TokenFrame(
        status = "Decoded sequence: two multi-token entities and one single-token one. Spans are read off the " +
            "B/I runs — Ada Lovelace (PER), Analytical Engine Ltd (ORG), London (LOC).",
        chips = tokens.mapIndexed { i, t ->
            Chip(t, sub = gold[i], mark = if (gold[i] == "O") ChipMark.DIM else ChipMark.RESULT)
        },
        chipsLabel = "Viterbi output",
        readout = "3 entities · scored by exact-span F1, not per-token accuracy",
    )

    frames += TokenFrame(
        status = "That last detail matters: a model that tags \"Analytical Engine\" but drops \"Ltd\" scores well " +
            "per token and gets zero credit for that span. Boundaries are part of the answer.",
        chips = listOf(
            Chip("Analytical", sub = "B-ORG", mark = ChipMark.RESULT),
            Chip("Engine", sub = "I-ORG", mark = ChipMark.RESULT),
            Chip("Ltd", sub = "O", mark = ChipMark.ACTIVE),
        ),
        chipsLabel = "clipped span — counts as a miss",
        readout = "6 of 8 tokens right · 2 of 3 entities right",
    )
    return frames
}

// ── Retrieval-augmented generation ───────────────────────────────────────────

private fun ragFrames(): List<TokenFrame> {
    val query = "What is the refund window?"
    val chunks = listOf(
        "Refunds are accepted within 30 days of delivery." to 0.91f,
        "Returns must include the original packaging." to 0.74f,
        "Our warehouse ships orders within 2 business days." to 0.38f,
        "The 2019 refund policy allowed 14 days." to 0.69f,
    )
    val frames = mutableListOf<TokenFrame>()

    frames += TokenFrame(
        status = "The question is about a private, changeable policy. A model's weights cannot be trusted for " +
            "this: the answer was either never in the training data or has since changed.",
        chips = query.split(" ").map { Chip(it) },
        chipsLabel = "query",
    )

    frames += TokenFrame(
        status = "The corpus was chunked into passages ahead of time and each chunk embedded into a vector. " +
            "Chunk size and overlap are quietly the highest-leverage knobs here — cut mid-thought and the " +
            "retrieved passage answers nothing.",
        chips = chunks.map { Chip(it.first.take(22) + "…") },
        chipsLabel = "indexed chunks",
        readout = "one embedding per chunk, stored in an ANN index",
    )

    frames += TokenFrame(
        status = "Embed the query and score every chunk by cosine similarity. Dense retrieval catches paraphrase " +
            "— \"refund window\" matches \"within 30 days\" without sharing a word.",
        bars = listOf(
            BarRow("cosine similarity", chunks.map { it.second }, HeatColor,
                captions = listOf("30-day", "packaging", "shipping", "2019 policy")),
        ),
        readout = "top-k retrieval · often blended with BM25 keyword scores",
    )

    val ranked = chunks.sortedByDescending { it.second }.take(3)
    frames += TokenFrame(
        status = "The shortlist is then reranked by a cross-encoder, which reads query and passage together " +
            "instead of comparing two precomputed vectors. Slower per pair, much better at spotting that the " +
            "2019 chunk is about a superseded policy.",
        chips = ranked.mapIndexed { i, (text, score) ->
            Chip(text.take(20) + "…", sub = "%.2f".format(score), mark = if (i == 0) ChipMark.RESULT else ChipMark.IDLE)
        },
        chipsLabel = "reranked",
        readout = "bi-encoder for recall · cross-encoder for precision",
    )

    frames += TokenFrame(
        status = "The prompt carries the passages plus an instruction to answer only from them and cite the " +
            "source. Without that instruction the model happily blends retrieved text with remembered text.",
        rows = listOf(
            "system" to "Answer using ONLY the sources. Cite as [n].",
            "[1]" to ranked[0].first,
            "[2]" to ranked[1].first,
            "question" to query,
        ),
        readout = "k · chunk_size must fit the context window",
    )

    frames += TokenFrame(
        status = "Answer: \"Refunds are accepted within 30 days of delivery [1].\" The citation is what makes it " +
            "checkable — and if the right passage had not been retrieved, no amount of generation quality could " +
            "have recovered it. Retrieval quality caps answer quality.",
        chips = "Refunds are accepted within 30 days of delivery [1]".split(" ").map { Chip(it, mark = ChipMark.RESULT) },
        chipsLabel = "grounded answer",
        readout = "update the index, not the weights",
    )
    return frames
}

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

// ── k-modes: k-means for data that has no arithmetic ────────────────────────
// Categorical rows have no mean and no Euclidean distance, so k-modes swaps both: the centre is the
// per-attribute mode and the distance is a count of mismatches. Everything else is Lloyd's loop.

private val kModesAttributes = listOf("Colour", "Size", "Shape", "Finish")

private val kModesRows = listOf(
    listOf("red", "small", "round", "matte"),
    listOf("red", "small", "square", "matte"),
    listOf("red", "large", "round", "matte"),
    listOf("blue", "large", "square", "gloss"),
    listOf("blue", "large", "square", "matte"),
    listOf("blue", "small", "square", "gloss"),
    listOf("green", "large", "round", "gloss"),
    listOf("blue", "large", "round", "gloss"),
)

private fun hamming(a: List<String>, b: List<String>) = a.indices.count { a[it] != b[it] }

private fun modeOf(rows: List<List<String>>, attribute: Int): String =
    rows.map { it[attribute] }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "—"

private fun kModesFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    var centres = listOf(kModesRows[0], kModesRows[6])

    frames.add(
        TokenFrame(
            status = "Eight rows of purely categorical attributes. There is no mean of {red, blue, green}, and encoding them as 0/1/2 would invent an ordering — so k-means simply does not apply here.",
            chips = kModesAttributes.map { Chip(it) },
            chipsLabel = "Attributes",
            rows = kModesRows.mapIndexed { i, r -> "row ${i + 1}" to r.joinToString(" · ") },
        ),
    )

    frames.add(
        TokenFrame(
            status = "k-modes changes two things and keeps the rest of Lloyd's algorithm. Distance becomes the count of attributes that differ — the Hamming distance — and the centre becomes the most frequent value per attribute.",
            chips = centres[0].mapIndexed { i, v -> Chip(v, sub = kModesAttributes[i], mark = ChipMark.RESULT) },
            chipsLabel = "Initial centre 1",
            rows = kModesRows.mapIndexed { i, r ->
                "row ${i + 1}" to "d to c1 = ${hamming(r, centres[0])} · d to c2 = ${hamming(r, centres[1])}"
            },
        ),
    )

    var assignment = IntArray(kModesRows.size)
    repeat(3) { iteration ->
        assignment = IntArray(kModesRows.size) { i ->
            centres.indices.minByOrNull { hamming(kModesRows[i], centres[it]) } ?: 0
        }
        frames.add(
            TokenFrame(
                status = "Iteration ${iteration + 1} — assign: each row joins whichever centre it mismatches on fewest attributes. Cluster sizes ${assignment.count { it == 0 }} and ${assignment.count { it == 1 }}.",
                chips = kModesAttributes.map { Chip(it) },
                chipsLabel = "Attributes",
                rows = kModesRows.mapIndexed { i, r ->
                    "row ${i + 1} → c${assignment[i] + 1}" to r.joinToString(" · ")
                },
            ),
        )

        centres = centres.indices.map { k ->
            val members = kModesRows.filterIndexed { i, _ -> assignment[i] == k }
            if (members.isEmpty()) centres[k] else kModesAttributes.indices.map { modeOf(members, it) }
        }
        frames.add(
            TokenFrame(
                status = "Iteration ${iteration + 1} — update: each centre becomes the per-attribute mode of its members. Note the centre is a valid row of the same type as the data, which a mean would not have been.",
                chips = centres[0].mapIndexed { i, v -> Chip(v, sub = kModesAttributes[i], mark = ChipMark.RESULT) } +
                    listOf(Chip("|")) +
                    centres[1].mapIndexed { i, v -> Chip(v, sub = kModesAttributes[i], mark = ChipMark.ACTIVE) },
                chipsLabel = "Centre 1  |  Centre 2",
            ),
        )
    }

    val cost = kModesRows.indices.sumOf { hamming(kModesRows[it], centres[assignment[it]]) }
    frames.add(
        TokenFrame(
            status = "Converged at a total mismatch cost of $cost. Two caveats worth carrying: Hamming distance treats every attribute as equally important, which is rarely true; and for data with both categorical and numeric columns you need k-prototypes, which sums a Hamming term and a Euclidean one with a weight between them.",
            chips = centres[0].mapIndexed { i, v -> Chip(v, sub = kModesAttributes[i], mark = ChipMark.RESULT) } +
                listOf(Chip("|")) +
                centres[1].mapIndexed { i, v -> Chip(v, sub = kModesAttributes[i], mark = ChipMark.ACTIVE) },
            chipsLabel = "Final centres",
            rows = kModesRows.mapIndexed { i, r ->
                "row ${i + 1} → c${assignment[i] + 1}" to "mismatch ${hamming(r, centres[assignment[i]])}"
            },
            readout = "total cost $cost",
        ),
    )
    return frames
}

// ── D1 · Stop word removal ───────────────────────────────────────────────────

private fun stopWordFrames(): List<TokenFrame> {
    val negative = StopWordLab.corpus[0]
    val positive = StopWordLab.corpus[1]
    val frames = mutableListOf<TokenFrame>()

    val rawTokens = StopWordLab.tokens(negative)
    frames += TokenFrame(
        status = "One review, seven tokens. Five of them are on NLTK's English list — they carry almost no " +
            "information about *which* review this is, which is the entire argument for dropping them.",
        chips = rawTokens.map { Chip(it, mark = if (it in StopWordLab.stopList) ChipMark.ACTIVE else ChipMark.IDLE) },
        chipsLabel = "\"$negative\"",
        readout = "${rawTokens.count { it in StopWordLab.stopList }} of ${rawTokens.size} on the list",
    )

    frames += TokenFrame(
        status = "Dropped. Across the six-review corpus the list removes ${StopWordLab.removedTokens} of " +
            "${StopWordLab.totalTokens} tokens — ${"%.0f".format(StopWordLab.removalRate * 100)}% of the text — and takes the " +
            "vocabulary from ${StopWordLab.typesBefore} types to ${StopWordLab.typesAfter}. For an inverted index that is the whole benefit: " +
            "smaller postings lists, fewer useless matches.",
        chips = StopWordLab.keep(negative).map { Chip(it, mark = ChipMark.RESULT) },
        chipsLabel = "what survives",
        rows = listOf(
            "tokens" to "${StopWordLab.totalTokens} → ${StopWordLab.keptTokens}",
            "types" to "${StopWordLab.typesBefore} → ${StopWordLab.typesAfter}",
            "removed" to "${"%.1f".format(StopWordLab.removalRate * 100)}%",
        ),
    )

    frames += TokenFrame(
        status = "Now the same list on the opposite review. \"not\" is on it — NLTK's list contains not, no, " +
            "nor and against — so the negation goes with the articles.",
        chips = StopWordLab.tokens(positive).map {
            Chip(it, mark = if (it in StopWordLab.stopList) ChipMark.ACTIVE else ChipMark.IDLE)
        },
        chipsLabel = "\"$positive\"",
    )

    frames += TokenFrame(
        status = "Two reviews with opposite verdicts, one identical bag of words. Every model downstream of " +
            "this point — BoW, TF-IDF, naive Bayes — now sees a single document. No amount of training fixes " +
            "an input that no longer contains the answer.",
        chips = StopWordLab.bag(negative).keys.map { Chip(it, mark = ChipMark.RESULT) },
        chipsLabel = "both reviews, after removal",
        rows = listOf(
            "\"$negative\"" to StopWordLab.bag(negative).toString(),
            "\"$positive\"" to StopWordLab.bag(positive).toString(),
        ),
        readout = if (StopWordLab.negationCollapse) "identical vectors · sentiment destroyed" else "vectors still differ",
    )

    frames += TokenFrame(
        status = "The same failure in search: a phrase query made entirely of function words survives as " +
            "nothing at all. This is why Google stopped dropping stop words from queries.",
        chips = StopWordLab.tokens(StopWordLab.hamletQuery).map { Chip(it, mark = ChipMark.ACTIVE) },
        chipsLabel = "query \"${StopWordLab.hamletQuery}\"",
        readout = "${StopWordLab.hamletSurvivors.size} terms left",
    )

    val terms = listOf("the", "movie", "not", "life")
    frames += TokenFrame(
        status = "TF-IDF already does the soft version of this. idf = ln(N / df) falls to zero for a term in " +
            "every document, so a frequent word is down-weighted rather than deleted — and \"not\", which the " +
            "list deletes, keeps its full weight because only two reviews use it.",
        bars = listOf(
            BarRow(
                "idf over the 6 reviews",
                terms.map { StopWordLab.idf(it).toFloat() },
                BarPositive,
                terms.map { "$it ${"%.2f".format(StopWordLab.idf(it))}" },
            ),
        ),
        readout = "\"the\" ${"%.2f".format(StopWordLab.idf("the"))} vs \"movie\" ${"%.2f".format(StopWordLab.idf("movie"))}",
    )

    frames += TokenFrame(
        status = "So the rule is not \"always remove\": keep the list for topic models, keyword indexes and " +
            "document clustering, where function words are noise. Drop it for sentiment, question answering, " +
            "translation and anything transformer-based — those models want the whole string, negations included.",
        chips = listOf(
            Chip("topic models", sub = "remove", mark = ChipMark.RESULT),
            Chip("keyword search", sub = "remove", mark = ChipMark.RESULT),
            Chip("sentiment", sub = "keep", mark = ChipMark.ACTIVE),
            Chip("QA / LLMs", sub = "keep", mark = ChipMark.ACTIVE),
        ),
        chipsLabel = "when to apply it",
    )
    return frames
}

// ── D1 · Lowercasing and cleaning ────────────────────────────────────────────

private fun cleaningFrames(): List<TokenFrame> {
    val doc = CleaningLab.raw[3]
    val frames = mutableListOf<TokenFrame>()

    frames += TokenFrame(
        status = "Four raw strings off the web: a curly apostrophe, an em dash, a URL, an emoji, an accent, " +
            "runs of spaces, percentages and mixed case. The corpus has ${CleaningLab.typesRaw} distinct types before any " +
            "of it is touched.",
        chips = doc.split(" ").filter { it.isNotBlank() }.map { Chip(it) },
        chipsLabel = "one of the four documents, raw",
        readout = "${CleaningLab.typesRaw} types",
    )

    CleaningLab.stages.forEachIndexed { index, (name, _) ->
        val before = CleaningLab.typesAfter(index)
        val after = CleaningLab.typesAfter(index + 1)
        val text = CleaningLab.applyThrough(doc, index + 1)
        frames += TokenFrame(
            status = "$name. " + when (name) {
                "NFKC normalise" -> "Compatibility normalisation first, before anything measures a string: it folds the curly apostrophe onto the ASCII one, so \"Apple's\" and \"Apple's\" stop being two words."
                "Replace URLs" -> "URLs are replaced by a placeholder rather than deleted — each one is otherwise a unique type that will never be seen again, and its presence is a real signal."
                "Drop emoji" -> "Emoji go only because this is a news corpus. On social text they carry sentiment and dropping them is a mistake."
                "Lowercase" -> "The one stage that both helps and hurts, which the next two frames measure."
                "Strip punctuation" -> "Cheap and lossy: it splits clitics (\"apple's\" → apple, s) and destroys decimals. This is exactly the job the RegEx topic hands to a tokenizer instead."
                "Fold digits" -> "Every number becomes one token. \"12%\" and \"5.2%\" were distinct types with no shared meaning; the placeholder keeps the fact that a number was there."
                "Fold accents" -> "Café → cafe. It merges spellings users actually type, and it is wrong in languages where the accent is the word."
                else -> "Runs of whitespace collapse, so tokenisation by split is safe from here."
            },
            chips = text.split(" ").filter { it.isNotBlank() }.take(8).map { Chip(it, mark = ChipMark.RESULT) },
            chipsLabel = "after $name",
            rows = listOf("types" to if (before == after) "$after (unchanged)" else "$before → $after"),
        )
    }

    frames += TokenFrame(
        status = "What lowercasing bought, on this corpus: \"Apple's\" and \"apple\" were ${CleaningLab.appleTypesBefore} types and are now " +
            "${CleaningLab.appleTypesAfter}. Any model counting words now has twice the evidence for the same word.",
        chips = listOf(
            Chip("Apple's", sub = "raw", mark = ChipMark.ACTIVE),
            Chip("apple", sub = "raw", mark = ChipMark.ACTIVE),
            Chip("apple", sub = "cleaned", mark = ChipMark.RESULT),
        ),
        chipsLabel = "the merge that pays",
        readout = "${CleaningLab.appleTypesBefore} types → ${CleaningLab.appleTypesAfter}",
    )

    frames += TokenFrame(
        status = "And what it cost, in the same corpus: \"US\" the country and \"us\" the pronoun are now one " +
            "type. Truecasing — lowercase only sentence-initial words, or restore case with a model — is the " +
            "fix, and almost nobody applies it.",
        chips = listOf(
            Chip("US", sub = "country", mark = ChipMark.ACTIVE),
            Chip("us", sub = "pronoun", mark = ChipMark.ACTIVE),
            Chip("us", sub = "both", mark = ChipMark.RESULT),
        ),
        chipsLabel = "the collision it causes",
        readout = if (CleaningLab.casedPairCollapsed) "2 senses → 1 type" else "no collision in this corpus",
    )

    frames += TokenFrame(
        status = "End to end the pipeline takes the corpus from ${CleaningLab.typesRaw} types to ${CleaningLab.typesClean} — a " +
            "${"%.0f".format((1 - CleaningLab.typesClean.toDouble() / CleaningLab.typesRaw) * 100)}% smaller vocabulary, which is smaller embedding tables, fewer " +
            "singletons and better count estimates. The order is not arbitrary: normalise before you compare, " +
            "replace before you strip, and collapse whitespace last.",
        chips = CleaningLab.applyThrough(doc, CleaningLab.stages.size).split(" ").map { Chip(it, mark = ChipMark.RESULT) },
        chipsLabel = "the document, cleaned",
        rows = listOf(
            "types" to "${CleaningLab.typesRaw} → ${CleaningLab.typesClean}",
            "stages" to "${CleaningLab.stages.size}",
        ),
    )
    return frames
}

// ── D1 · Regular expressions ─────────────────────────────────────────────────

private fun regexFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val naive = RegexLab.naiveTokens
    val tuned = RegexLab.tunedTokens

    frames += TokenFrame(
        status = "The pattern everyone writes first: \\w+ over one ordinary sentence. It finds ${naive.size} " +
            "tokens where a linguist would count ${tuned.size}.",
        chips = naive.take(10).map { Chip(it) },
        chipsLabel = "\\w+ matches (first 10 of ${naive.size})",
        rows = listOf("input" to RegexLab.text),
    )

    frames += TokenFrame(
        status = "Look at what it did. \\w matches letters, digits and underscore — nothing else — so every " +
            "internal dot, hyphen and apostrophe is a token boundary. The e-mail address alone became four " +
            "tokens, none of which is an e-mail address.",
        chips = listOf("a", "smith", "x", "co").map { Chip(it, mark = ChipMark.ACTIVE) },
        chipsLabel = "\"a.smith@x.co\" under \\w+",
        rows = listOf(
            "Smith's" to "Smith · s",
            "e-mail" to "e · mail",
            "U.S." to "U · S",
            "3.5%" to "3 · 5",
            "2024-01-05" to "2024 · 01 · 05",
        ),
    )

    frames += TokenFrame(
        status = "The tuned pattern is one alternation with the specific branches first: e-mail, ISO date, " +
            "dotted abbreviation, hyphen/apostrophe word, number with optional percent, then plain word. Six " +
            "tokens the naive pattern shattered now survive whole.",
        chips = RegexLab.rescued.map { Chip(it, mark = ChipMark.RESULT) },
        chipsLabel = "rescued by the tuned pattern",
        readout = "${naive.size} tokens → ${tuned.size}",
    )

    val misordered = RegexLab.misorderedTokens
    frames += TokenFrame(
        status = "Branch order is the design, not a detail. Move the general letters branch to the front — one " +
            "line — and alternation, which takes the *first* branch that matches rather than the longest, eats " +
            "the prefix of the specific ones. Same six branches, ${misordered.size} tokens instead of ${tuned.size}, and the e-mail " +
            "comes back as \".smith@x.co\" — a token that looks like the pattern worked.",
        chips = misordered.take(10).map { Chip(it, mark = ChipMark.ACTIVE) },
        chipsLabel = "same branches, general one first",
        rows = listOf("tokens" to "${tuned.size} → ${misordered.size}"),
    )

    val ns = listOf(8, 12, 16, 20, 24)
    frames += TokenFrame(
        status = "The failure mode that takes services down. (a+)+\$ against a run of a's followed by anything " +
            "else has to try every way of splitting that run between the two quantifiers before it can report " +
            "failure — 2^(n−1) of them. These counts are the search walked explicitly, not a timing.",
        bars = listOf(
            BarRow(
                "backtracking attempts (log₂)",
                ns.map { (ln(RegexLab.backtrackAttempts(it).toDouble()) / ln(2.0)).toFloat() },
                BarNegative,
                ns.map { "n=$it" },
            ),
        ),
        rows = ns.map { "n=$it" to "${RegexLab.backtrackAttempts(it)} attempts vs ${RegexLab.linearAttempts(it)} linear" },
        readout = "n=24 → ${RegexLab.backtrackAttempts(24)} attempts",
    )

    frames += TokenFrame(
        status = "The fix is structural, not a longer timeout: remove the nested quantifier (a+\$), make the " +
            "group atomic ((?>a+)+\$) or possessive (a++\$), or use a backtracking-free engine — RE2, Rust's " +
            "regex, Go's regexp — which cannot express this shape at all. Then rejection is linear in n.",
        chips = listOf(
            Chip("(a+)+\$", sub = "2ⁿ⁻¹", mark = ChipMark.ACTIVE),
            Chip("a+\$", sub = "n", mark = ChipMark.RESULT),
            Chip("(?>a+)+\$", sub = "n", mark = ChipMark.RESULT),
            Chip("RE2", sub = "n", mark = ChipMark.RESULT),
        ),
        chipsLabel = "same intent, four costs",
        readout = "linear again",
    )
    return frames
}

// ── D1 · N-grams ─────────────────────────────────────────────────────────────

private fun nGramFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val padded = NGramLab.padded(NGramLab.corpus[0])

    for (start in 0 until padded.size - 1) {
        frames += TokenFrame(
            status = "A bigram is a two-token window, slid one position at a time. The <s> and </s> markers are " +
                "not decoration: without them the model has nothing to condition the first word on and no way " +
                "to end a sentence, and its probabilities would not sum to 1.",
            chips = padded.mapIndexed { i, token ->
                Chip(token, mark = if (i == start || i == start + 1) ChipMark.ACTIVE else ChipMark.IDLE)
            },
            chipsLabel = "\"${NGramLab.corpus[0]}\", padded",
            readout = "(${padded[start]}, ${padded[start + 1]})",
        )
    }

    val top = NGramLab.counts(2).entries.sortedByDescending { it.value }.take(6)
    frames += TokenFrame(
        status = "Counted over all ${NGramLab.corpus.size} sentences. That is the entire training procedure — " +
            "an n-gram model is a count table, and the maximum-likelihood estimate is one division: " +
            "P(w₂|w₁) = count(w₁w₂) / count(w₁).",
        chips = top.map { Chip(it.key.joinToString(" "), sub = "×${it.value}") },
        chipsLabel = "most frequent bigrams",
        rows = listOf(
            "bigram tokens" to "${NGramLab.tokenCount(2)}",
            "bigram types" to "${NGramLab.typeCount(2)}",
            "vocabulary" to "${NGramLab.vocabSize}",
        ),
    )

    val continuations = listOf("like", "love")
    frames += TokenFrame(
        status = "The distribution after \"i\": ${continuations.joinToString(" and ") { "P($it|i) = ${"%.2f".format(NGramLab.mle("i", it))}" }}. " +
            "Nothing else ever follows \"i\" in this corpus, so every other word gets exactly zero — which is " +
            "the model's defining weakness, not a rounding artefact.",
        bars = listOf(
            BarRow(
                "P(· | i)",
                continuations.map { NGramLab.mle("i", it).toFloat() },
                BarPositive,
                continuations.map { "$it ${"%.2f".format(NGramLab.mle("i", it))}" },
            ),
        ),
    )

    val unseen = NGramLab.unseenBigrams.first()
    frames += TokenFrame(
        status = "Score a held-out sentence and one bigram — (${unseen.first}, ${unseen.second}) — has never been " +
            "seen. Its MLE probability is 0, the sentence's probability is the product, and perplexity is " +
            "exp(−(1/N)Σ ln p). One zero makes the whole thing infinite: the model cannot rank two sentences " +
            "it has never seen.",
        chips = NGramLab.bigramsOf(NGramLab.heldOut).map { (a, b) ->
            Chip("$a $b", mark = if (a == unseen.first && b == unseen.second) ChipMark.ACTIVE else ChipMark.IDLE)
        },
        chipsLabel = "\"${NGramLab.heldOut}\"",
        readout = "perplexity = ∞",
    )

    val ks = listOf(1.0, 0.5, 0.1, 0.01)
    frames += TokenFrame(
        status = "Add-k smoothing moves a little mass to everything unseen: (count + k) / (count(w₁) + k·|V|). " +
            "k = 1 is Laplace and is far too heavy on a vocabulary of ${NGramLab.vocabSize}; k = 0.1 is best here at " +
            "${"%.2f".format(NGramLab.perplexity(NGramLab.heldOut, 0.1))}; k = 0.01 is worse again at ${"%.2f".format(NGramLab.perplexity(NGramLab.heldOut, 0.01))}, because too little mass is left for " +
            "the unseen bigram. k is a hyperparameter with an optimum, not a safety switch.",
        bars = listOf(
            BarRow(
                "held-out perplexity",
                ks.map { NGramLab.perplexity(NGramLab.heldOut, it).toFloat() },
                BarNegative,
                ks.map { "k=$it" },
            ),
        ),
        rows = ks.map { "k = $it" to "%.2f".format(NGramLab.perplexity(NGramLab.heldOut, it)) },
    )

    frames += TokenFrame(
        status = "Smoothing is not free. On a sentence the corpus *does* contain, MLE scores " +
            "${"%.2f".format(NGramLab.perplexity("i like nlp", 0.0))} and add-1 scores ${"%.2f".format(NGramLab.perplexity("i like nlp", 1.0))} — the mass taken from seen bigrams has to come from " +
            "somewhere. Every real smoothing method (Good-Turing, Kneser-Ney) is a better answer to the same " +
            "trade.",
        rows = listOf(
            "MLE, seen sentence" to "%.2f".format(NGramLab.perplexity("i like nlp", 0.0)),
            "add-1, seen sentence" to "%.2f".format(NGramLab.perplexity("i like nlp", 1.0)),
            "add-0.1, seen sentence" to "%.2f".format(NGramLab.perplexity("i like nlp", 0.1)),
        ),
    )

    val orders = listOf(1, 2, 3, 4)
    frames += TokenFrame(
        status = "And why n stays small: as the order grows, types climb toward tokens — at n = 4 this corpus " +
            "has ${NGramLab.typeCount(4)} distinct 4-grams over ${NGramLab.tokenCount(4)} occurrences, so almost every one was seen exactly " +
            "once. The table grows as |V|ⁿ while the evidence per cell collapses. Neural language models exist " +
            "because this wall is unclimbable by counting.",
        bars = listOf(
            BarRow(
                "types ÷ tokens",
                orders.map { NGramLab.typeCount(it).toFloat() / NGramLab.tokenCount(it) },
                BarPositive,
                orders.map { "n=$it" },
            ),
        ),
        rows = orders.map { "n = $it" to "${NGramLab.typeCount(it)} types / ${NGramLab.tokenCount(it)} tokens" },
    )
    return frames
}

// ── D1 · Hidden Markov model ─────────────────────────────────────────────────

private fun hmmFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val words = HmmLab.sentence

    frames += TokenFrame(
        status = "Three words, two readings. \"book\" is a verb (an instruction) or a noun (an object), and " +
            "\"that\" is a determiner or a complementiser. A tagger has to choose, and the words alone do not " +
            "decide it — the sequence does.",
        chips = words.map { Chip(it, mark = ChipMark.IDLE) },
        chipsLabel = "the sentence",
        rows = listOf(
            "reading 1" to "VB DT NN — \"book that flight\", an order",
            "reading 2" to "NN IN NN — \"book\" the object, \"that\" a complementiser",
        ),
    )

    frames += TokenFrame(
        status = "The model is two tables estimated by counting a tagged corpus. B(t → w) says how likely a " +
            "tag is to emit this word; note that P(book|VB) is higher than P(book|NN) — verbs are rarer, so " +
            "when one does appear it is more likely to be this word.",
        bars = HmmLab.tags.filter { tag -> words.any { HmmLab.b(tag, it) > 0 } }.map { tag ->
            BarRow(
                "B($tag → ·)",
                words.map { HmmLab.b(tag, it).toFloat() },
                BarPositive,
                words.map { "$it ${"%.3f".format(HmmLab.b(tag, it))}" },
            )
        },
    )

    frames += TokenFrame(
        status = "A(t → t′) is the other half: what follows what. A determiner is followed by a noun " +
            "${"%.0f".format(HmmLab.a("DT", "NN") * 100)}% of the time, which is the strongest constraint in the table — and the one that " +
            "will decide this sentence.",
        heat = Heat(
            rowLabels = HmmLab.tags,
            colLabels = HmmLab.tags + "</s>",
            values = HmmLab.tags.map { from -> (HmmLab.tags + "</s>").map { to -> HmmLab.a(from, to).toFloat() } },
        ),
        rows = listOf("read as" to "row = current tag, column = next tag"),
    )

    val forward = HmmLab.forward()
    frames += TokenFrame(
        status = "The forward pass fills one column per word, each cell summing every path that reaches it: " +
            "α_t(j) = Σ_i α_{t−1}(i)·A(i→j)·B(j→wₜ). Summing the last column against the stop transition gives " +
            "P(the sentence) = ${"%.2e".format(HmmLab.sentenceProbability())} — the probability of the words with the tags integrated out.",
        heat = Heat(
            rowLabels = HmmLab.tags,
            colLabels = words,
            values = HmmLab.tags.map { tag ->
                forward.map { column ->
                    val max = column.values.max()
                    if (max <= 0f) 0f else (column.getValue(tag) / max).toFloat()
                }
            },
        ),
        rows = forward.mapIndexed { i, column ->
            words[i] to column.filterValues { it > 0 }.entries.joinToString(" · ") { "${it.key} ${"%.1e".format(it.value)}" }
        },
        readout = "P(w) = ${"%.2e".format(HmmLab.sentenceProbability())}",
    )

    val greedy = HmmLab.greedy()
    frames += TokenFrame(
        status = "Greedy tagging goes left to right and commits. \"book\" scores highest as NN " +
            "(${"%.4f".format((HmmLab.start["NN"] ?: 0.0) * HmmLab.b("NN", "book"))} against VB's ${"%.4f".format((HmmLab.start["VB"] ?: 0.0) * HmmLab.b("VB", "book"))}), so NN it is — and from NN the best next tag is IN, and " +
            "from IN it is NN. Locally optimal at every step.",
        chips = words.mapIndexed { i, w -> Chip(w, sub = greedy.tags[i], mark = ChipMark.ACTIVE) },
        chipsLabel = "greedy",
        readout = "P = ${"%.2e".format(greedy.probability)}",
    )

    val viterbi = HmmLab.viterbi()
    frames += TokenFrame(
        status = "Viterbi keeps the best path *into every tag* instead of one path overall, so the NN opening " +
            "is still alive when the second word is scored — and the DT reading of \"that\", which greedy " +
            "discarded, pulls the whole sequence back to the imperative. It is " +
            "${"%.2f".format(viterbi.probability / greedy.probability)}× more probable than the greedy answer.",
        chips = words.mapIndexed { i, w -> Chip(w, sub = viterbi.tags[i], mark = ChipMark.RESULT) },
        chipsLabel = "Viterbi",
        rows = HmmLab.allPaths().take(4).map { it.tags.joinToString(" ") to "%.2e".format(it.probability) },
        readout = "P = ${"%.2e".format(viterbi.probability)} · ${"%.2f".format(viterbi.probability / greedy.probability)}× greedy",
    )

    frames += TokenFrame(
        status = "And it is cheap. Viterbi fills T·N² cells — ${HmmLab.viterbiOperations()} here — where enumerating tag " +
            "sequences costs N^T = ${HmmLab.bruteForcePaths()}. On a 20-word sentence that is ${HmmLab.viterbiOperations(20)} cells against " +
            "4²⁰ ≈ 1.1 × 10¹² paths. Same dynamic-programming trick as edit distance: the best path through a " +
            "state only needs the best path into it.",
        rows = listOf(
            "3 words" to "${HmmLab.viterbiOperations()} cells vs ${HmmLab.bruteForcePaths()} paths",
            "20 words" to "${HmmLab.viterbiOperations(20)} cells vs ≈1.1×10¹² paths",
            "complexity" to "O(T·N²) vs O(N^T)",
        ),
        readout = "generative: models P(w, t), not P(t | w)",
    )
    return frames
}

// ── D1 · Jaccard similarity ──────────────────────────────────────────────────

private fun jaccardFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val a = SimilarityLab.docA
    val b = SimilarityLab.docB
    val setA = SimilarityLab.setOf(a)
    val setB = SimilarityLab.setOf(b)
    val shared = setA.intersect(setB)
    val union = setA.union(setB)

    frames += TokenFrame(
        status = "Jaccard throws away counts and order and keeps only membership: a document is a set of " +
            "terms. Two documents, one a longer version of the other.",
        chips = a.split(" ").map { Chip(it, mark = if (it in setB) ChipMark.RESULT else ChipMark.ACTIVE) },
        chipsLabel = "A: \"$a\"",
        rows = listOf("B" to "\"$b\""),
    )

    frames += TokenFrame(
        status = "J(A,B) = |A ∩ B| / |A ∪ B| = ${shared.size}/${union.size} = ${"%.3f".format(SimilarityLab.jaccard(a, b))}. Note \"data\" appears twice in A and " +
            "counts once — the repetition is invisible to the set, and that is the design, not an oversight.",
        chips = union.map { Chip(it, mark = if (it in shared) ChipMark.RESULT else ChipMark.IDLE) },
        chipsLabel = "union, shared terms highlighted",
        rows = listOf(
            "|A|" to "${setA.size} terms",
            "|B|" to "${setB.size} terms",
            "|A ∩ B|" to "${shared.size}",
            "|A ∪ B|" to "${union.size}",
        ),
        readout = "J = ${"%.3f".format(SimilarityLab.jaccard(a, b))}",
    )

    frames += TokenFrame(
        status = "Cosine over the same two documents, on raw counts, gives ${"%.3f".format(SimilarityLab.cosineOfDocs(a, b))} — higher, because " +
            "\"data\" occurring twice in A and once in B still aligns the two vectors. The metrics answer " +
            "different questions: cosine asks how similar the emphasis is, Jaccard asks how much of the " +
            "material is shared.",
        bars = listOf(
            BarRow(
                "A vs B",
                listOf(SimilarityLab.jaccard(a, b).toFloat(), SimilarityLab.cosineOfDocs(a, b).toFloat()),
                BarPositive,
                listOf("Jaccard ${"%.3f".format(SimilarityLab.jaccard(a, b))}", "cosine ${"%.3f".format(SimilarityLab.cosineOfDocs(a, b))}"),
            ),
        ),
    )

    val c = SimilarityLab.docC
    frames += TokenFrame(
        status = "A paraphrase of A with different wording scores ${"%.3f".format(SimilarityLab.jaccard(a, c))} — both metrics fall, and " +
            "neither can see that \"neural model\" and \"model\" are related. Set and vector overlap are " +
            "surface measures; that limit is what word embeddings exist to fix.",
        chips = c.split(" ").map { Chip(it, mark = if (it in setA) ChipMark.RESULT else ChipMark.IDLE) },
        chipsLabel = "C: \"$c\"",
        rows = listOf(
            "J(A,C)" to "%.3f".format(SimilarityLab.jaccard(a, c)),
            "cos(A,C)" to "%.3f".format(SimilarityLab.cosineOfDocs(a, c)),
        ),
    )

    val shingleJ = SimilarityLab.jaccardShingles(a, b)
    frames += TokenFrame(
        status = "Near-duplicate detection does not use word sets — it uses character k-shingles, which keep " +
            "local word order. At k = 5 these documents have ${SimilarityLab.shingles(a).size} and ${SimilarityLab.shingles(b).size} shingles and score " +
            "${"%.3f".format(shingleJ)}. This is what web crawlers and plagiarism checkers actually compare.",
        chips = SimilarityLab.shingles(b).take(6).map { Chip("\"$it\"") },
        chipsLabel = "5-shingles of B (first 6 of ${SimilarityLab.shingles(b).size})",
        readout = "J₅ = ${"%.3f".format(shingleJ)}",
    )

    val ks = listOf(16, 64, 256)
    frames += TokenFrame(
        status = "The scaling trick: hash every shingle under k permutations, keep the minimum under each, and " +
            "the fraction of matching minima estimates Jaccard — because P(the minima agree) is exactly " +
            "|A ∩ B| / |A ∪ B|. Comparing ${SimilarityLab.exactComparisonSize(a, b)} shingles becomes comparing k integers, and the error " +
            "falls as 1/√k.",
        bars = listOf(
            BarRow(
                "MinHash estimate vs J₅ = ${"%.3f".format(shingleJ)}",
                ks.map { SimilarityLab.minHashEstimate(a, b, it).toFloat() },
                BarPositive,
                ks.map { "k=$it ${"%.3f".format(SimilarityLab.minHashEstimate(a, b, it))}" },
            ),
        ),
        rows = ks.map { "k = $it" to "estimate ${"%.3f".format(SimilarityLab.minHashEstimate(a, b, it))}, error ${"%.3f".format(abs(SimilarityLab.minHashEstimate(a, b, it) - shingleJ))}" },
        readout = "fixed-width signatures, LSH-ready",
    )
    return frames
}

// ── D4 · Positional encodings ────────────────────────────────────────────────

private fun positionalFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val tokens = listOf("the", "cat", "sat", "on", "the", "mat")

    frames += TokenFrame(
        status = "Self-attention is permutation-equivariant: shuffle the input and the outputs shuffle with " +
            "it, unchanged. \"the cat sat\" and \"sat cat the\" produce identical representations — a bag of " +
            "words with extra steps. Position has to be injected, because the mechanism cannot see it.",
        chips = tokens.mapIndexed { i, t -> Chip(t, sub = "pos $i") } + Chip("|") +
            tokens.reversed().mapIndexed { i, t -> Chip(t, sub = "pos $i", mark = ChipMark.DIM) },
        chipsLabel = "same tokens, same attention output",
    )

    frames += TokenFrame(
        status = "The original answer: add a fixed sinusoid per dimension pair, with wavelengths in geometric " +
            "progression from 2π to 10000·2π. Early dimensions cycle every few positions and late ones barely " +
            "move across the whole sequence — a positional signal at every scale at once.",
        heat = Heat(
            rowLabels = (0 until 8).map { "pos $it" },
            colLabels = (0 until PositionalLab.dim).map { "d$it" },
            values = (0 until 8).map { p -> PositionalLab.encoding(p).map { ((it + 1) / 2).toFloat() } },
        ),
        rows = (0..3).map { "pair $it wavelength" to "${"%.1f".format(PositionalLab.wavelength(it))} positions" },
    )

    frames += TokenFrame(
        status = "The property that makes it work: the dot product of two encodings depends only on the *offset* " +
            "between them, not on where the pair sits. Measured across absolute positions 0, 4, 8 and 12, the " +
            "spread for a fixed offset is ${"%.4f".format(PositionalLab.offsetInvariance(4))} — zero to floating-point precision. Relative distance " +
            "is available to attention without ever being stored.",
        bars = listOf(
            BarRow(
                "PE(0) · PE(offset)",
                PositionalLab.offsetProfile(8).map { it.second.toFloat() },
                BarPositive,
                PositionalLab.offsetProfile(8).map { "+${it.first}" },
            ),
        ),
        readout = "offset-invariant to ${"%.4f".format(PositionalLab.offsetInvariance(4))}",
    )

    val bumps = PositionalLab.nonMonotonicOffsets()
    frames += TokenFrame(
        status = "What that plot is *not*, though, is a clean decay. It falls to offset 3 and then goes back up " +
            "— at offsets ${bumps.joinToString(", ")} the similarity is higher than at the offset before. It is a sum of cosines " +
            "at different frequencies, so \"nearby positions look similar\" holds on average and not pointwise. " +
            "Textbook diagrams usually draw a smooth decay; the actual table does not have one.",
        rows = PositionalLab.offsetProfile(8).map { (offset, value) -> "offset $offset" to "%.2f".format(value) },
        readout = "${bumps.size} offsets where similarity rises",
    )

    frames += TokenFrame(
        status = "RoPE takes the relative property and makes it exact rather than incidental: rotate each 2-D " +
            "slice of the query and key by an angle proportional to position, and the dot product becomes a " +
            "function of the gap alone. Scoring the same gap at positions 0, 5, 10 and 20 gives a spread of " +
            "${"%.6f".format(PositionalLab.ropeRelativeError(3))} — it is an algebraic identity, not an approximation. Every recent open model uses it.",
        rows = listOf(
            "q@5 · k@0" to "%.4f".format(PositionalLab.ropeScore(5, 0)),
            "q@25 · k@20" to "%.4f".format(PositionalLab.ropeScore(25, 20)),
            "spread over 4 offsets" to "%.6f".format(PositionalLab.ropeRelativeError(3)),
        ),
        chips = listOf(
            Chip("rotate q by mθ", mark = ChipMark.ACTIVE),
            Chip("rotate k by nθ", mark = ChipMark.ACTIVE),
            Chip("q·k depends on m−n", mark = ChipMark.RESULT),
        ),
    )

    frames += TokenFrame(
        status = "ALiBi drops embeddings entirely and subtracts a per-head linear penalty from the attention " +
            "score: −slope × distance, with slopes forming a geometric series so different heads see different " +
            "horizons. Because the penalty *is* monotone in distance, extrapolating past the training length " +
            "degrades gracefully instead of falling off a cliff.",
        bars = listOf(
            BarRow(
                "ALiBi slopes by head",
                PositionalLab.alibiSlopes().map { it.toFloat() },
                BarNegative,
                (0 until 8).map { "h$it" },
            ),
        ),
        rows = listOf(
            "head 0, distance 10" to "%.2f".format(PositionalLab.alibiBias(0, 10)),
            "head 7, distance 10" to "%.3f".format(PositionalLab.alibiBias(7, 10)),
        ),
    )

    frames += TokenFrame(
        status = "And the option most early models actually shipped: a learned table, one row per position. " +
            "BERT's 512 × 768 table is ${"%,d".format(PositionalLab.learnedTableParameters(512))} parameters and works well inside the trained range — " +
            "with no row at position 513 at all. That hard wall, not accuracy, is why the field moved to RoPE " +
            "and ALiBi as context windows grew.",
        rows = listOf(
            "learned" to "${"%,d".format(PositionalLab.learnedTableParameters(512))} params, no extrapolation at all",
            "sinusoidal" to "0 params, offset-invariant, weak extrapolation",
            "RoPE" to "0 params, exact relative scores, extends with interpolation",
            "ALiBi" to "0 params, monotone penalty, extrapolates best",
        ),
        readout = "position is a design choice, not a given",
    )
    return frames
}

// ── D4 · BART: denoising autoencoding ────────────────────────────────────────

private fun bartFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()

    frames += TokenFrame(
        status = "BART is the obvious idea nobody had shipped: a full encoder-decoder transformer, pretrained " +
            "by corrupting text arbitrarily and asking it to reconstruct the original. BERT's encoder can only " +
            "fill blanks and GPT's decoder can only continue; BART does both, so it fine-tunes for " +
            "classification *and* generation without changing shape.",
        chips = BartLab.original.map { Chip(it) },
        chipsLabel = "the original document",
        rows = listOf(
            "BERT-large" to "${BartLab.bertLargeParameters / 1_000_000}M, encoder only",
            "GPT-2 large" to "${BartLab.gpt2LargeParameters / 1_000_000}M, decoder only",
            "BART-large" to "${BartLab.bartLargeParameters / 1_000_000}M, both — ~10% over BERT for the extra decoder",
        ),
    )

    BartLab.all().forEach { corruption ->
        frames += TokenFrame(
            status = "${corruption.name}. ${corruption.note} " + when {
                BartLab.tokensLost(corruption) > 0 && !BartLab.lengthKnown(corruption) ->
                    "${BartLab.tokensLost(corruption)} tokens are gone and the length no longer tells you how many."
                BartLab.tokensLost(corruption) > 0 ->
                    "${BartLab.tokensLost(corruption)} tokens are gone."
                else -> "Every token survives — only the order changed."
            },
            chips = corruption.result.map {
                Chip(it, mark = if (it == "[MASK]") ChipMark.ACTIVE else ChipMark.IDLE)
            },
            chipsLabel = corruption.name,
            rows = listOf(
                "tokens lost" to "${BartLab.tokensLost(corruption)}",
                "length preserved" to if (BartLab.lengthKnown(corruption)) "yes" else "no",
                "order changed" to if (BartLab.orderChanged(corruption)) "yes" else "no",
            ),
        )
    }

    frames += TokenFrame(
        status = "The five are not variations on one idea — they attack different things. Masking and deletion " +
            "differ on whether the *position* of the missing content is given away. Infilling is the strongest " +
            "single objective in the paper's ablation because one mask can stand for zero, one or many tokens, " +
            "so the model must predict how much is missing as well as what. Permutation and rotation move the " +
            "problem up to document level, where no token is lost at all.",
        rows = listOf(
            "token masking" to "position given, count given",
            "token deletion" to "position hidden, count hidden",
            "text infilling" to "position given, count hidden — the strongest",
            "sentence permutation" to "nothing lost, order destroyed",
            "document rotation" to "nothing lost, start hidden",
        ),
        chips = BartLab.all().map { Chip(it.name.split(" ").first(), mark = ChipMark.RESULT) },
        chipsLabel = "five corruptions, one model",
    )

    frames += TokenFrame(
        status = "Fine-tuning is where the shape pays off. Classification: feed the input to both stacks and " +
            "read the decoder's final token. Generation: the encoder takes the source and the decoder writes " +
            "the target — the same setup as translation, so summarisation needs no architectural surgery. " +
            "BART set the state of the art on CNN/DailyMail summarisation and matched RoBERTa on GLUE with " +
            "comparable training, which is the argument for the shape in one sentence.",
        chips = listOf(
            Chip("classification", sub = "read decoder end", mark = ChipMark.RESULT),
            Chip("generation", sub = "source → target", mark = ChipMark.RESULT),
            Chip("translation", sub = "+ small encoder", mark = ChipMark.RESULT),
        ),
        chipsLabel = "one pretrained model, three fine-tunes",
        readout = "denoising is the objective; the shape is the contribution",
    )
    return frames
}

// ── D4 · XLNet: permutation language modelling ──────────────────────────────

private fun xlnetFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()

    frames += TokenFrame(
        status = "XLNet starts from a specific complaint about BERT: masking corrupts the input with a [MASK] " +
            "token that appears in ${"%.0f".format(XlnetLab.bertMaskRate * 100)}% of pretraining positions and never once at fine-tuning time. " +
            "The model spends pretraining learning about a symbol its real inputs will never contain.",
        chips = XlnetLab.sentence.mapIndexed { i, t ->
            Chip(if (i in XlnetLab.maskedPositions) "[MASK]" else t, mark = if (i in XlnetLab.maskedPositions) ChipMark.ACTIVE else ChipMark.IDLE)
        },
        chipsLabel = "BERT's view of the sentence",
        rows = listOf("mask rate" to "${"%.0f".format(XlnetLab.bertMaskRate * 100)}% at pretraining, 0% downstream"),
    )

    frames += TokenFrame(
        status = "The second complaint is sharper. Masking both tokens of \"New York\" and predicting them " +
            "independently multiplies two marginals: ${"%.2f".format(XlnetLab.pNewGivenContext)} × ${"%.2f".format(XlnetLab.pYorkGivenContext)} = ${"%.3f".format(XlnetLab.independentJoint())}. The real joint factorises as " +
            "P(New) × P(York | New) = ${"%.2f".format(XlnetLab.pNewGivenContext)} × ${"%.2f".format(XlnetLab.pYorkGivenNew)} = ${"%.3f".format(XlnetLab.trueJoint())}, because seeing \"New\" nearly determines \"York\". " +
            "BERT's objective cannot represent that dependency at all — a ${"%.1f".format(XlnetLab.independenceGap())}× gap on this pair.",
        bars = listOf(
            BarRow(
                "joint probability of \"New York\"",
                listOf(XlnetLab.independentJoint().toFloat(), XlnetLab.trueJoint().toFloat()),
                BarPositive,
                listOf("independent ${"%.3f".format(XlnetLab.independentJoint())}", "true ${"%.3f".format(XlnetLab.trueJoint())}"),
            ),
        ),
        readout = "${"%.1f".format(XlnetLab.independenceGap())}× — the independence assumption, priced",
    )

    frames += TokenFrame(
        status = "XLNet's answer keeps autoregression and randomises the *order*. Predict the tokens of a " +
            "sequence one at a time, but in a permuted factorization order sampled per example — so every " +
            "token eventually gets to condition on every other, in some order, without any [MASK] ever entering " +
            "the input. A 4-token sequence has ${XlnetLab.orderCount(4)} orders; an 8-token one has ${"%,d".format(XlnetLab.orderCount(8))}.",
        chips = XlnetLab.factorizationOrders(4).take(4).map { Chip(it.joinToString("→"), mark = ChipMark.RESULT) },
        chipsLabel = "some factorization orders of a 4-token sequence",
        rows = listOf(
            "length 4" to "${XlnetLab.orderCount(4)} orders",
            "length 5" to "${XlnetLab.orderCount(5)} orders",
            "length 8" to "${"%,d".format(XlnetLab.orderCount(8))} orders",
        ),
    )

    val order = listOf(2, 0, 3, 1)
    frames += TokenFrame(
        status = "The implementation problem this creates: to predict position ${order[2]} at step 3, the model must know " +
            "*which* position it is predicting — but must not see what is there. One attention stream cannot do " +
            "both, so XLNet runs two: a content stream that sees the token, and a query stream that sees only " +
            "its position plus everything already generated in this order.",
        rows = (0..3).map { step ->
            "step ${step + 1}" to "content sees ${XlnetLab.contentStreamSees(order, step)} · query sees ${XlnetLab.queryStreamSees(order, step)}"
        },
        chips = order.map { Chip("pos $it", mark = ChipMark.ACTIVE) },
        chipsLabel = "one sampled order",
    )

    frames += TokenFrame(
        status = "Only the last portion of each order is predicted (about ${"%.0f".format(XlnetLab.xlnetPredictedPerSequence * 100)}% of positions), because a target " +
            "with almost no context is noise rather than signal — the same reason BERT predicts ${"%.0f".format(XlnetLab.bertPredictedPerSequence * 100)}%. XLNet also " +
            "inherits Transformer-XL's segment recurrence and relative encodings, which is what lets it handle " +
            "long documents.",
        rows = listOf(
            "predicted per sequence" to "${"%.0f".format(XlnetLab.xlnetPredictedPerSequence * 100)}% (XLNet) vs ${"%.0f".format(XlnetLab.bertPredictedPerSequence * 100)}% (BERT)",
            "input corruption" to "none vs [MASK]",
            "dependency modelling" to "full autoregressive factorisation vs independent",
            "cost" to "two attention streams; roughly 5× BERT's training compute",
        ),
    )

    frames += TokenFrame(
        status = "It beat BERT on twenty tasks in 2019 and then largely lost the argument anyway: RoBERTa showed " +
            "that most of BERT's gap was under-training rather than the objective, and the field went to " +
            "decoder-only scaling instead. XLNet is worth knowing for the analysis, not the architecture — " +
            "\"what exactly does masking assume, and what does it cost\" is a question that keeps recurring.",
        chips = listOf(
            Chip("XLNet 2019", sub = "beat BERT on 20 tasks", mark = ChipMark.RESULT),
            Chip("RoBERTa", sub = "same objective, more data", mark = ChipMark.ACTIVE),
            Chip("GPT-3 →", sub = "decoder-only wins", mark = ChipMark.ACTIVE),
        ),
        chipsLabel = "how it aged",
    )
    return frames
}

// ── D4 · Mixture of experts ─────────────────────────────────────────────────

private fun moeFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val routes = MoeLab.routes()

    frames += TokenFrame(
        status = "Mistral 7B's contribution was engineering discipline — grouped-query attention, sliding-window " +
            "attention, an aggressively over-trained 7B that beat models twice its size. Mixtral's is " +
            "structural: replace each block's feed-forward network with ${MoeLab.experts} of them and route every token to " +
            "just ${MoeLab.topK}.",
        chips = MoeLab.tokens.map { Chip(it) },
        chipsLabel = "a sequence about to be routed",
        rows = listOf(
            "experts per layer" to "${MoeLab.experts}",
            "active per token" to "${MoeLab.topK}",
            "total parameters" to "${MoeLab.totalParameters}B",
            "active parameters" to "${MoeLab.activeParameters}B",
        ),
    )

    routes.take(4).forEach { route ->
        frames += TokenFrame(
            status = "Routing \"${route.token}\": a linear router scores all ${MoeLab.experts} experts, the top ${MoeLab.topK} are kept, and their " +
                "softmax weights (${route.weights.joinToString(", ") { "%.2f".format(it) }}) mix the two outputs. The routing is per token and per layer — " +
                "the next token in the same sentence can go somewhere else entirely, and usually does.",
            chips = (0 until MoeLab.experts).map { e ->
                Chip("E$e", mark = if (e in route.experts) ChipMark.RESULT else ChipMark.DIM)
            },
            chipsLabel = "\"${route.token}\" → experts ${route.experts.joinToString(", ")}",
            bars = listOf(
                BarRow(
                    "router softmax",
                    MoeLab.softmax(MoeLab.routerLogits(route.token)).map { it.toFloat() },
                    BarPositive,
                    (0 until MoeLab.experts).map { "E$it" },
                ),
            ),
        )
    }

    frames += TokenFrame(
        status = "Across the whole strip the load is uneven — the busiest expert takes ${"%.1f".format(MoeLab.loadImbalance())}× the average share. " +
            "Left alone this collapses: a slightly favoured expert gets more gradient, improves, gets favoured " +
            "more, and the rest go dead. The auxiliary load-balancing loss (${"%.2f".format(MoeLab.auxiliaryLoss())} here, 1.0 at perfect balance) " +
            "is added to the training objective specifically to stop that feedback loop.",
        bars = listOf(
            BarRow(
                "tokens routed per expert",
                MoeLab.expertLoad().map { it.toFloat() },
                BarNegative,
                (0 until MoeLab.experts).map { "E$it" },
            ),
        ),
        readout = "imbalance ${"%.2f".format(MoeLab.loadImbalance())}× · aux loss ${"%.2f".format(MoeLab.auxiliaryLoss())}",
    )

    frames += TokenFrame(
        status = "The trade in one line: ${MoeLab.totalParameters}B parameters' worth of capacity at ${MoeLab.activeParameters}B parameters' worth of compute " +
            "— ${"%.0f".format(MoeLab.computeSaving() * 100)}% of the FLOPs a dense model of that size would need. What it does *not* save is memory: " +
            "every expert must be resident because any token might route to it, so serving needs ${"%.1f".format(MoeLab.memoryPenalty())}× the VRAM " +
            "of a dense 7B. MoE buys quality per FLOP, and pays for it in bytes.",
        rows = listOf(
            "compute" to "${MoeLab.activeParameters}B of ${MoeLab.totalParameters}B active — ${"%.0f".format(MoeLab.computeSaving() * 100)}% saved",
            "memory" to "all ${MoeLab.totalParameters}B resident — ${"%.1f".format(MoeLab.memoryPenalty())}× a dense 7B",
            "batching" to "harder: a batch can touch every expert",
            "fine-tuning" to "more prone to overfitting than a dense model of equal active size",
        ),
        readout = "quality per FLOP, paid for in VRAM",
    )
    return frames
}

// ── D4 · Frontier model families ────────────────────────────────────────────

private fun frontierFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()

    frames += TokenFrame(
        status = "The frontier families differ far less in architecture than the marketing suggests — all of " +
            "them are decoder-only transformers with RoPE-family position handling, RLHF-style post-training, " +
            "and a mixture-of-experts variant somewhere in the line-up. What actually separates them is the " +
            "context window, the modality story, and how they were aligned.",
        rows = ContextLab.families.map { it.name to "${"%,d".format(it.contextTokens)} tokens · ${it.vendor}" },
        chips = ContextLab.families.map { Chip(it.vendor, mark = ChipMark.RESULT) },
        chipsLabel = "who ships what",
    )

    frames += TokenFrame(
        status = "Context is the headline number, and it is a real capability rather than a spec-sheet entry: a " +
            "million tokens is a large codebase or a few hundred thousand words of documents in the prompt, " +
            "with no retrieval step and no chunking. Anthropic's Claude Opus/Sonnet tier and Google's Gemini " +
            "both sit at 1M; the GPT-4 generation set the 128K expectation that the open models still target.",
        bars = listOf(
            BarRow(
                "context window (log₁₀ tokens)",
                ContextLab.families.map { (ln(it.contextTokens.toDouble()) / ln(10.0)).toFloat() },
                BarPositive,
                ContextLab.families.map { it.name.split(" ").first() },
            ),
        ),
    )

    frames += TokenFrame(
        status = "What a long context costs, computed. Attention is quadratic, so the score matrix at 1M tokens " +
            "is ${"%,.0f".format(ContextLab.relativeAttentionCost(1_000_000))}× the work it is at 4K. The harder limit at serving time is the KV cache: at " +
            "${"%,d".format(ContextLab.kvCacheBytesPerToken())} bytes per token for a 70B-class model, a full 1M-token context needs " +
            "${"%,.0f".format(ContextLab.kvCacheGb(1_000_000))} GB of it. Grouped-query attention — sharing one K/V pair across ${ContextLab.heads / ContextLab.kvGroups} query heads — " +
            "cuts that to ${"%,.0f".format(ContextLab.gqaCacheGb(1_000_000))} GB, which is the difference between impossible and merely expensive.",
        rows = ContextLab.contexts.map { n ->
            val attention = "%,.0f".format(ContextLab.relativeAttentionCost(n))
            val cache = "%,.0f".format(ContextLab.kvCacheGb(n))
            val gqa = "%,.1f".format(ContextLab.gqaCacheGb(n))
            "${"%,d".format(n)} tokens" to "attention ${attention}x · KV $cache GB · with GQA $gqa GB"
        },
        readout = "the window is an engineering achievement, not a config value",
    )

    frames += TokenFrame(
        status = "Alignment is where the families genuinely diverge. The common baseline is RLHF: collect human " +
            "preference comparisons, fit a reward model, optimise the policy against it. Anthropic's " +
            "Constitutional AI replaces much of the human labelling with a written set of principles the model " +
            "critiques and revises its own outputs against — cheaper to scale and, more importantly, auditable, " +
            "because the rules are a document you can read rather than a distribution over annotator opinions.",
        chips = listOf(
            Chip("RLHF", sub = "human comparisons", mark = ChipMark.ACTIVE),
            Chip("Constitutional AI", sub = "written principles", mark = ChipMark.RESULT),
            Chip("DPO", sub = "no reward model", mark = ChipMark.ACTIVE),
        ),
        chipsLabel = "how preferences get in",
    )

    frames += TokenFrame(
        status = "Gemini's distinguishing claim is native multimodality: text, images, audio and video are " +
            "interleaved from pretraining rather than bolted on with an adapter afterwards, and it runs on " +
            "Google's TPUs with a mixture-of-experts design. Claude's is the opposite emphasis — a written " +
            "constitution, extended reasoning that the model decides how much of to spend, and a tier structure " +
            "(a small fast model, a balanced one, a frontier one) so the model choice is a cost decision.",
        rows = listOf(
            "Gemini" to "natively multimodal, MoE, TPU-trained, 1M context",
            "Claude" to "Constitutional AI, adaptive reasoning depth, 1M context across the main tier",
            "GPT-4 class" to "the generation that normalised 128K and tool use",
            "open weights" to "LLaMA and Mistral — you can run, inspect and fine-tune them",
        ),
    )

    frames += TokenFrame(
        status = "Two cautions worth carrying. First, published numbers move fast enough that any capability or " +
            "price table is stale within months — treat the *shape* of the trade (context vs cost, open vs " +
            "hosted, tier vs single model) as the durable part. Second, benchmark scores between families are " +
            "close enough that the deciding factors in practice are usually the boring ones: latency, context " +
            "window, tool-calling reliability, deployment region and price per token.",
        chips = listOf(
            Chip("durable", sub = "architecture, trade-offs", mark = ChipMark.RESULT),
            Chip("volatile", sub = "scores, prices, model names", mark = ChipMark.ACTIVE),
        ),
        chipsLabel = "what to remember",
        readout = "pick on constraints, not leaderboards",
    )
    return frames
}

// ── D2 · Part-of-speech tagging ──────────────────────────────────────────────

private fun posTaggingFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val ambiguous = PosLab.test[1]

    frames += TokenFrame(
        status = "Tagging assigns each token a syntactic category. It looks like a lookup until you meet a word " +
            "with more than one: \"man\" is a noun in one of these sentences and a verb in the other, and no " +
            "property of the word itself decides which.",
        chips = listOf("the", "old", "man", "chased").mapIndexed { i, w ->
            Chip(w, sub = if (w == "man") "NN?" else null, mark = if (i == 2) ChipMark.ACTIVE else ChipMark.IDLE)
        } + Chip("|") + listOf("they", "man", "a", "boat").mapIndexed { i, w ->
            Chip(w, sub = if (w == "man") "VBP?" else null, mark = if (i == 1) ChipMark.ACTIVE else ChipMark.IDLE)
        },
        chipsLabel = "the same word, two categories",
        rows = PosLab.tagsOf("man").map { "man as ${it.key}" to "${it.value} in training" },
    )

    frames += TokenFrame(
        status = "In this ${PosLab.train.flatten().size}-token treebank only ${PosLab.ambiguousTypes.size} of ${PosLab.typeCount} types is ambiguous — but the ratio is " +
            "misleading, and on real corpora it is the tokens that matter: about 40% of Brown corpus *tokens* " +
            "are ambiguous even though only ~11% of its types are, because the ambiguous words are the common " +
            "ones.",
        rows = listOf(
            "types" to "${PosLab.typeCount}",
            "ambiguous types" to PosLab.ambiguousTypes.joinToString(", "),
            "ambiguous tokens" to "${"%.1f".format(PosLab.ambiguousTokenShare * 100)}% here",
            "tagset" to PosLab.tagset.joinToString(" "),
        ),
        chips = PosLab.tagset.map { Chip(it) },
        chipsLabel = "the tagset used here (Penn Treebank uses 45)",
    )

    val words = ambiguous.map { it.first }
    val baseline = PosLab.baselineTags(words)
    frames += TokenFrame(
        status = "The baseline every tagger is measured against: give each word its most frequent tag in " +
            "training, and give unknown words NN. It is not a strawman — it reaches about 90% on English, which " +
            "is why a tagger reporting 92% has barely earned its complexity.",
        chips = words.mapIndexed { i, w ->
            Chip(w, sub = baseline[i], mark = if (baseline[i] != ambiguous[i].second) ChipMark.ACTIVE else ChipMark.IDLE)
        },
        chipsLabel = "most-frequent-tag baseline",
        rows = listOf("gold" to ambiguous.joinToString(" ") { it.second }),
        readout = "${PosLab.baselineScore.correct}/${PosLab.baselineScore.total} = ${"%.1f".format(PosLab.baselineScore.accuracy * 100)}% over the held-out sentences",
    )

    val viterbi = PosLab.viterbiTags(words)
    frames += TokenFrame(
        status = "A bigram HMM estimated from the same training text gets it right: \"they\" is a pronoun, and " +
            "PRP is far more often followed by a verb than by a noun, so the sequence pulls \"man\" to VBP. " +
            "Context is doing what per-word frequency cannot.",
        chips = words.mapIndexed { i, w -> Chip(w, sub = viterbi[i], mark = ChipMark.RESULT) },
        chipsLabel = "Viterbi over the estimated HMM",
        rows = listOf(
            "P(VBP | PRP)" to "%.2f".format(PosLab.a("PRP", "VBP")),
            "P(NN | PRP)" to "%.2f".format(PosLab.a("PRP", "NN")),
        ),
        readout = "${PosLab.viterbiScore.correct}/${PosLab.viterbiScore.total} = ${"%.1f".format(PosLab.viterbiScore.accuracy * 100)}%",
    )

    val unknown = PosLab.test[2]
    val unknownWords = unknown.map { it.first }
    frames += TokenFrame(
        status = "The harder case is a word training never contained. \"walk\" is unseen, so the baseline falls " +
            "back to NN and is wrong; the HMM has no emission evidence either, but the tags around it — NNS " +
            "before, RB after — leave VBP as the only sequence that fits.",
        chips = unknownWords.mapIndexed { i, w ->
            Chip(
                w,
                sub = "${PosLab.baselineTags(unknownWords)[i]} → ${PosLab.viterbiTags(unknownWords)[i]}",
                mark = if (w == "walk") ChipMark.RESULT else ChipMark.IDLE,
            )
        },
        chipsLabel = "baseline → Viterbi",
        rows = listOf("gold" to unknown.joinToString(" ") { it.second }),
        readout = "unknown words are where taggers earn their keep",
    )

    frames += TokenFrame(
        status = "Both errors the baseline makes are the same error twice: a verb read as a noun, because nouns " +
            "are more frequent. Real taggers add suffix features (-ing, -ed, -ly), capitalisation and a " +
            "left-context window, which is how they reach 97% — and the remaining 3% is mostly noun/verb and " +
            "adjective/participle, the same two confusions, plus genuine annotator disagreement.",
        chips = PosLab.baselineErrors().map { (word, gold, predicted) -> Chip(word, sub = "$predicted ≠ $gold", mark = ChipMark.ACTIVE) },
        chipsLabel = "baseline errors",
        rows = listOf(
            "baseline" to "${"%.1f".format(PosLab.baselineScore.accuracy * 100)}%",
            "HMM + Viterbi" to "${"%.1f".format(PosLab.viterbiScore.accuracy * 100)}%",
            "real English" to "~90% baseline, ~97% modern taggers",
            "human ceiling" to "~97% agreement between annotators",
        ),
    )
    return frames
}

// ── D2 · Chunking ────────────────────────────────────────────────────────────

private fun chunkingFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val chunks = ChunkLab.chunk()
    val bio = ChunkLab.bio()

    frames += TokenFrame(
        status = "Chunking — shallow parsing — finds the flat phrases in a sentence without building a tree. " +
            "It runs over POS tags, not words, which is why one regular expression per phrase type is enough: " +
            "NP = DT? JJ* NN+, VP = a verb, PP = a preposition.",
        chips = ChunkLab.sentence.mapIndexed { i, w -> Chip(w, sub = ChunkLab.tags[i]) },
        chipsLabel = "tagged input",
    )

    chunks.forEachIndexed { index, (type, start, end) ->
        frames += TokenFrame(
            status = "Chunk ${index + 1}: \"${ChunkLab.sentence.subList(start, end).joinToString(" ")}\" is a $type, matched by " + when (type) {
                "NP" -> "DT? JJ* NN+ — an optional determiner, any adjectives, then the nouns."
                "VP" -> "a single verb tag; chunk grammars deliberately do not nest a VP over its object."
                else -> "a preposition on its own; the noun phrase after it is a separate chunk."
            } + " Chunks never nest and never overlap, which is the whole simplification.",
            chips = ChunkLab.sentence.mapIndexed { i, w ->
                Chip(w, sub = ChunkLab.tags[i], mark = if (i in start until end) ChipMark.ACTIVE else ChipMark.DIM)
            },
            chipsLabel = "$type [$start, $end)",
        )
    }

    frames += TokenFrame(
        status = "The output is usually written as BIO tags so a sequence labeller can learn it: B- starts a " +
            "chunk, I- continues one, O is outside. That single encoding turns a span problem into a per-token " +
            "classification — the same trick NER uses, and the reason both tasks share their model.",
        chips = ChunkLab.sentence.mapIndexed { i, w -> Chip(w, sub = bio[i], mark = if (bio[i].startsWith("B")) ChipMark.RESULT else ChipMark.IDLE) },
        chipsLabel = "BIO encoding",
        rows = listOf("spans" to "${chunks.size}", "tokens" to "${ChunkLab.sentence.size}"),
    )

    val perfect = ChunkLab.evaluate()
    frames += TokenFrame(
        status = "Scored against the gold chunks this grammar is exactly right — precision ${"%.2f".format(perfect.precision)}, recall " +
            "${"%.2f".format(perfect.recall)}, F1 ${"%.2f".format(perfect.f1)}. Which is what a hand-written chunker looks like on the sentence it was " +
            "written for, and why the next frame matters more than this one.",
        chips = chunks.map { Chip("${it.first} [${it.second},${it.third})", mark = ChipMark.RESULT) },
        chipsLabel = "predicted spans, all correct",
        readout = "F1 ${"%.2f".format(perfect.f1)}",
    )

    val broken = ChunkLab.evaluate(ChunkLab.boundaryError)
    frames += TokenFrame(
        status = "Now one boundary moves: the first NP starts at \"small\" instead of \"the\". Two of its three " +
            "tokens are still right, and the span scores **zero** — exact-match evaluation gives no partial " +
            "credit. Precision and recall fall to ${"%.2f".format(broken.precision)}, F1 to ${"%.2f".format(broken.f1)}, while per-token accuracy only falls to " +
            "${"%.2f".format(ChunkLab.tokenAccuracy(ChunkLab.boundaryError))}. Quoting the token number is how chunkers get oversold.",
        chips = ChunkLab.sentence.mapIndexed { i, w ->
            Chip(w, sub = ChunkLab.bio(ChunkLab.boundaryError)[i], mark = if (i == 0 || i == 1) ChipMark.ACTIVE else ChipMark.IDLE)
        },
        chipsLabel = "one token's boundary wrong",
        rows = listOf(
            "span F1" to "${"%.2f".format(perfect.f1)} → ${"%.2f".format(broken.f1)}",
            "token accuracy" to "1.00 → ${"%.2f".format(ChunkLab.tokenAccuracy(ChunkLab.boundaryError))}",
            "overlap on the bad span" to "2 of 3 tokens, scored 0",
        ),
    )

    frames += TokenFrame(
        status = "Why stop at flat phrases: chunking is one linear pass — ${ChunkLab.chunkOperations()} steps for this sentence — where " +
            "a full constituency parse is cubic, ${ChunkLab.parseOperations()}. For information extraction, question answering over " +
            "templates or feeding an NP list to a search index, the tree was never needed. Chunk when you want " +
            "the phrases; parse when you need what attaches to what.",
        rows = listOf(
            "chunking" to "O(n) — ${ChunkLab.chunkOperations()} steps",
            "constituency parse" to "O(n³) — ${ChunkLab.parseOperations()} steps",
            "gives you" to "flat phrases, no attachment",
            "does not give you" to "PP attachment, nesting, long-range structure",
        ),
        readout = "shallow on purpose",
    )
    return frames
}

// ── D2 · Coreference resolution ─────────────────────────────────────────────

private fun corefFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()

    frames += TokenFrame(
        status = "Coreference asks which mentions point at the same entity. Step one is finding the mentions at " +
            "all — every noun phrase and pronoun is a candidate, including the ones that refer to nothing.",
        chips = CorefLab.easyMentions.map { Chip(it.text, sub = "${it.number}/${it.gender}") },
        chipsLabel = "mentions in the document",
        rows = listOf("document" to CorefLab.easyDocument),
    )

    frames += TokenFrame(
        status = "Agreement filters the candidates cheaply: a singular feminine pronoun cannot refer to " +
            "\"Charles Babbage\" or to \"London\". On this document that is enough — \"She\" resolves to Ada " +
            "Lovelace and \"his\" to Charles Babbage with no learning of any kind.",
        chips = listOf(
            Chip("She", mark = ChipMark.ACTIVE),
            Chip("Ada Lovelace", sub = "sg/fem ✓", mark = ChipMark.RESULT),
            Chip("Charles Babbage", sub = "gender ✗", mark = ChipMark.DIM),
            Chip("London", sub = "animacy ✗", mark = ChipMark.DIM),
        ),
        chipsLabel = "candidates for \"She\"",
    )

    frames += TokenFrame(
        status = "But the immediate link a mention-pair model returns is often another pronoun: \"her\" resolves " +
            "to \"She\", not to Ada Lovelace. Scored on immediate antecedents that is ${"%.0f".format(CorefLab.pairAccuracy() * 100)}%; scored on the " +
            "entity each chain resolves to — the transitive closure, which is what the task actually asks for — " +
            "it is ${"%.0f".format(CorefLab.easyAccuracy() * 100)}%. Reporting the first number is a real evaluation mistake, not a hypothetical one.",
        chips = listOf(3, 4, 5).map { i ->
            Chip(CorefLab.easyMentions[i].text, sub = "${CorefLab.resolveEasy(i)} → ${CorefLab.resolveChain(i)}", mark = ChipMark.RESULT)
        },
        chipsLabel = "immediate link → chain entity",
        rows = CorefLab.chains().map { (entity, mentions) -> entity to mentions.joinToString(" ← ") },
        readout = "pair ${"%.0f".format(CorefLab.pairAccuracy() * 100)}% · chain ${"%.0f".format(CorefLab.easyAccuracy() * 100)}%",
    )

    frames += TokenFrame(
        status = "Now the sentence agreement cannot touch. \"The city council refused the demonstrators a permit " +
            "because they feared violence\" — who is \"they\"? Both candidates are plural, both are animate " +
            "enough, and the answer is the council.",
        chips = CorefLab.winogradA.split(" ").map {
            Chip(it, mark = if (it == "they") ChipMark.ACTIVE else if (it == "feared") ChipMark.RESULT else ChipMark.IDLE)
        },
        chipsLabel = "Winograd schema, variant A",
        rows = listOf("gold" to CorefLab.goldA),
    )

    frames += TokenFrame(
        status = "Change one word — feared to advocated — and the answer flips to the demonstrators. Nothing " +
            "syntactic changed. Any system relying on recency picks the same candidate both times and is " +
            "therefore right exactly ${"%.0f".format(CorefLab.baselineAccuracyOnPair() * 100)}% of the time on the pair, which is the score of guessing.",
        chips = CorefLab.winogradB.split(" ").map {
            Chip(it, mark = if (it == "they") ChipMark.ACTIVE else if (it == "advocated") ChipMark.RESULT else ChipMark.IDLE)
        },
        chipsLabel = "variant B",
        rows = listOf(
            "gold A" to CorefLab.goldA,
            "gold B" to CorefLab.goldB,
            "recency answers" to CorefLab.recencyBaseline(),
            "accuracy on the pair" to "${"%.0f".format(CorefLab.baselineAccuracyOnPair() * 100)}%",
        ),
        readout = "world knowledge, not grammar",
    )

    frames += TokenFrame(
        status = "The modern shape of the task: score every candidate pair — ${CorefLab.candidatePairs()} of them for ${CorefLab.easyMentions.size} mentions, " +
            "quadratic in document length — or rank antecedents per mention with a neural encoder, then take " +
            "the transitive closure. Large language models finally pushed Winograd-style accuracy past 90%, and " +
            "they did it with the world knowledge the schema was designed to require rather than with a better " +
            "syntactic feature.",
        rows = listOf(
            "mentions" to "${CorefLab.easyMentions.size}",
            "candidate pairs" to "${CorefLab.candidatePairs()} — O(m²)",
            "metrics" to "MUC, B³, CEAF — averaged as CoNLL F1",
            "why three" to "each one alone can be gamed by over- or under-merging chains",
        ),
    )
    return frames
}

// ── D2 · Lexicon sentiment ───────────────────────────────────────────────────

private fun sentimentLexiconFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val simple = SentimentLexiconLab.testSet[0].first
    val negated = SentimentLexiconLab.testSet[1].first

    frames += TokenFrame(
        status = "A sentiment lexicon is a dictionary from word to valence — this one holds ${SentimentLexiconLab.lexiconSize} entries scored " +
            "from −3 to +3. Scoring a sentence is a sum. No training data, no model, and the result is entirely " +
            "explainable: you can point at the words that produced it.",
        chips = simple.split(" ").map { w ->
            Chip(w, sub = SentimentLexiconLab.lexicon[w]?.let { if (it > 0) "+$it" else "$it" }, mark = if (w in SentimentLexiconLab.lexicon) ChipMark.RESULT else ChipMark.DIM)
        },
        chipsLabel = "\"$simple\"",
        readout = "score ${"%+.1f".format(SentimentLexiconLab.plainScore(simple))} → positive",
    )

    frames += TokenFrame(
        status = "Then the sentence everyone hits in week one. \"$negated\" contains the same positive word, and " +
            "the plain sum returns ${"%+.1f".format(SentimentLexiconLab.plainScore(negated))} — positive, for a negative review. The lexicon has no " +
            "concept of the word \"not\".",
        chips = negated.split(" ").map { w ->
            Chip(
                w,
                sub = SentimentLexiconLab.lexicon[w]?.let { if (it > 0) "+$it" else "$it" },
                mark = if (w in SentimentLexiconLab.negators) ChipMark.ACTIVE else if (w in SentimentLexiconLab.lexicon) ChipMark.RESULT else ChipMark.DIM,
            )
        },
        chipsLabel = "the negation the sum ignores",
        readout = "plain ${"%+.1f".format(SentimentLexiconLab.plainScore(negated))} · gold negative",
    )

    frames += TokenFrame(
        status = "So every lexicon system grows the same three rules: a negator flips the polarity of scored " +
            "words within ${SentimentLexiconLab.negationWindow} tokens (damped, because \"not good\" is milder than \"bad\"), intensifiers " +
            "and diminishers scale the value, and a clause after \"but\" outweighs what came before it. With " +
            "them the same sentence scores ${"%+.1f".format(SentimentLexiconLab.ruleScore(negated))}.",
        chips = listOf(
            Chip("not good", sub = "${"%+.2f".format(-2 * 0.75)}", mark = ChipMark.RESULT),
            Chip("very good", sub = "${"%+.1f".format(2 * 1.5)}", mark = ChipMark.RESULT),
            Chip("slightly slow", sub = "${"%+.1f".format(-1 * 0.5)}", mark = ChipMark.RESULT),
            Chip("… but excellent", sub = "×1.5", mark = ChipMark.RESULT),
        ),
        chipsLabel = "the three rules",
        rows = listOf(
            "negation window" to "${SentimentLexiconLab.negationWindow} tokens, ×−0.75",
            "intensifiers" to SentimentLexiconLab.intensifiers.entries.joinToString(", ") { "${it.key} ×${it.value}" },
            "contrast" to "before \"but\" ×0.5, after ×1.5",
        ),
    )

    val cases = SentimentLexiconLab.testSet.take(6)
    frames += TokenFrame(
        status = "Scored over the whole labelled set, the rules take accuracy from ${"%.0f".format(SentimentLexiconLab.plainAccuracy * 100)}% to " +
            "${"%.0f".format(SentimentLexiconLab.ruleAccuracy * 100)}% — and all three of the plain sum's errors are negation, which is the single highest-value " +
            "rule in the family.",
        bars = listOf(
            BarRow(
                "plain sum",
                cases.map { SentimentLexiconLab.plainScore(it.first).toFloat() },
                BarPositive,
                cases.map { if (it.second > 0) "pos" else "neg" },
            ),
            BarRow(
                "with rules",
                cases.map { SentimentLexiconLab.ruleScore(it.first).toFloat() },
                ChipResult,
                cases.map { if (it.second > 0) "pos" else "neg" },
            ),
        ),
        rows = listOf(
            "plain accuracy" to "${"%.0f".format(SentimentLexiconLab.plainAccuracy * 100)}%",
            "rule accuracy" to "${"%.0f".format(SentimentLexiconLab.ruleAccuracy * 100)}%",
            "plain errors" to SentimentLexiconLab.errors(SentimentLexiconLab::plainScore).joinToString("; ") { "\"${it.first}\"" },
        ),
    )

    val remaining = SentimentLexiconLab.errors(SentimentLexiconLab::ruleScore).first()
    frames += TokenFrame(
        status = "The error the rules cannot fix is the rules' own doing: in \"${remaining.first}\", \"never\" is a " +
            "negator sitting three tokens before \"awful\", so the flip turns a negative review positive " +
            "(${"%+.2f".format(remaining.second)}). A fixed window has no idea what a negator scopes over — that needs a parse, and " +
            "at that point a classifier trained on labelled reviews is cheaper and better.",
        chips = remaining.first.split(" ").map { w ->
            Chip(
                w,
                sub = SentimentLexiconLab.lexicon[w]?.let { if (it > 0) "+$it" else "$it" },
                mark = if (w in SentimentLexiconLab.negators) ChipMark.ACTIVE else if (w in SentimentLexiconLab.lexicon) ChipMark.RESULT else ChipMark.DIM,
            )
        },
        chipsLabel = "the rule firing where it should not",
        readout = "${"%+.2f".format(remaining.second)} · gold negative",
    )

    frames += TokenFrame(
        status = "And the structural limit: the lexicon has an opinion about only ${"%.0f".format(SentimentLexiconLab.coverage() * 100)}% of the tokens in this " +
            "test set, and none at all about sarcasm, comparison (\"better than their last one\") or domain " +
            "words — \"unpredictable\" is praise for a thriller and a complaint about a car. Lexicons stay " +
            "useful where labels do not exist, where the output must be auditable, and as a feature inside a " +
            "trained model rather than instead of one.",
        rows = listOf(
            "lexicon coverage" to "${"%.0f".format(SentimentLexiconLab.coverage() * 100)}% of tokens",
            "handles" to "explicit polarity words, negation, intensity",
            "misses" to "sarcasm, comparison, domain sense, implicature",
            "use it when" to "no labelled data, or the score must be explainable",
        ),
        readout = "VADER and AFINN are this, tuned for decades",
    )
    return frames
}

// ── D3 · word2vec: CBOW ──────────────────────────────────────────────────────

private fun w2vSentence() = Word2VecLab.sentences[0]

private fun cbowFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val tokens = w2vSentence()
    val centre = 2
    val contextIds = (centre - Word2VecLab.window..centre + Word2VecLab.window)
        .filter { it != centre && it in tokens.indices }

    frames += TokenFrame(
        status = "CBOW turns the corpus into a fill-in-the-blank task: hide the centre word, keep its " +
            "${Word2VecLab.window * 2}-word window, and train a model to guess what was removed. No labels are needed — the text " +
            "labels itself, which is why this scales to whatever text you have.",
        chips = tokens.mapIndexed { i, t ->
            Chip(t, mark = if (i == centre) ChipMark.RESULT else if (i in contextIds) ChipMark.ACTIVE else ChipMark.DIM)
        },
        chipsLabel = "context → centre",
        rows = listOf(
            "context" to contextIds.joinToString(", ") { tokens[it] },
            "target" to tokens[centre],
        ),
    )

    frames += TokenFrame(
        status = "The context vectors are *averaged* into one hidden vector before the prediction — bag of " +
            "words, hence the name. Word order inside the window is thrown away, and the whole window costs one " +
            "update. That is the difference from skip-gram, and everything else follows from it.",
        chips = contextIds.map { Chip(tokens[it], mark = ChipMark.ACTIVE) } + Chip("mean", mark = ChipMark.RESULT),
        chipsLabel = "h = mean of the context vectors",
        rows = listOf(
            "updates / epoch" to "${Word2VecLab.cbowUpdatesPerEpoch()} (one per position)",
            "skip-gram" to "${Word2VecLab.skipGramUpdatesPerEpoch()} (one per pair)",
        ),
    )

    frames += TokenFrame(
        status = "Scoring the true centre against the whole vocabulary would cost a softmax over every word. " +
            "Negative sampling replaces it with ${Word2VecLab.negatives + 1} binary decisions: pull the real target closer, push " +
            "${Word2VecLab.negatives} words drawn from the noise distribution away.",
        chips = listOf(Chip(tokens[centre], sub = "+1", mark = ChipMark.RESULT)) +
            listOf("dog", "book", "water", "works", "young").map { Chip(it, sub = "0", mark = ChipMark.ACTIVE) },
        chipsLabel = "one positive, ${Word2VecLab.negatives} negatives",
        rows = listOf(
            "full softmax" to "${Word2VecLab.softmaxCost()} multiply-adds",
            "negative sampling" to "${Word2VecLab.negativeSamplingCost()} multiply-adds",
        ),
    )

    val cbow = Word2VecLab.cbow
    val sg = Word2VecLab.skipGram
    frames += TokenFrame(
        status = "Trained for ${Word2VecLab.epochs} epochs on ${Word2VecLab.corpus.size} sentences. The loss falls from " +
            "${"%.2f".format(cbow.losses.first())} to ${"%.2f".format(cbow.losses.last())} — lower than skip-gram reaches on the same corpus " +
            "(${"%.2f".format(sg.losses.last())}), because predicting one word from an averaged window is an easier problem than " +
            "predicting each context word from one centre.",
        bars = listOf(
            BarRow(
                "CBOW loss",
                cbow.losses.filterIndexed { i, _ -> i % 4 == 0 },
                BarNegative,
                cbow.losses.filterIndexed { i, _ -> i % 4 == 0 }.mapIndexed { i, _ -> "e${i * 40}" },
            ),
        ),
        readout = "loss ${"%.2f".format(cbow.losses.first())} → ${"%.2f".format(cbow.losses.last())}",
    )

    val nearest = Word2VecLab.nearest(cbow, "king", 4)
    frames += TokenFrame(
        status = "What it learned: nothing in the corpus says king and queen are related, and no rule was " +
            "written. They share contexts — rules, wears, sits, leads — and sharing contexts is what the " +
            "objective rewards.",
        chips = nearest.map { Chip(it.first, sub = "%.2f".format(it.second), mark = ChipMark.RESULT) },
        chipsLabel = "nearest to \"king\" (cosine)",
        rows = listOf(
            "king · queen" to "%.2f".format(Word2VecLab.similarity(cbow, "king", "queen")),
            "king · dog" to "%.2f".format(Word2VecLab.similarity(cbow, "king", "dog")),
        ),
    )

    frames += TokenFrame(
        status = "Against skip-gram on the same corpus, measured rather than assumed. CBOW is smoother: it " +
            "scores king·queen ${"%.2f".format(Word2VecLab.similarity(cbow, "king", "queen"))} against skip-gram's ${"%.2f".format(Word2VecLab.similarity(sg, "king", "queen"))}, but also king·man ${"%.2f".format(Word2VecLab.similarity(cbow, "king", "man"))} against " +
            "${"%.2f".format(Word2VecLab.similarity(sg, "king", "man"))} — it compresses distinctions as well as similarities, and its related-minus-unrelated " +
            "gap is the smaller of the two (${"%.2f".format(Word2VecLab.contrast(cbow))} vs ${"%.2f".format(Word2VecLab.contrast(sg))}).",
        bars = listOf(
            BarRow(
                "CBOW",
                listOf(
                    Word2VecLab.similarity(cbow, "king", "queen"),
                    Word2VecLab.similarity(cbow, "king", "man"),
                    Word2VecLab.similarity(cbow, "king", "dog"),
                ),
                BarPositive,
                listOf("king·queen", "king·man", "king·dog"),
            ),
            BarRow(
                "skip-gram",
                listOf(
                    Word2VecLab.similarity(sg, "king", "queen"),
                    Word2VecLab.similarity(sg, "king", "man"),
                    Word2VecLab.similarity(sg, "king", "dog"),
                ),
                ChipResult,
                listOf("king·queen", "king·man", "king·dog"),
            ),
        ),
    )

    frames += TokenFrame(
        status = "And the rare word, where the textbook expects CBOW to lose: \"monarch\" occurs twice against " +
            "king's five, and here CBOW places it nearer king (${"%.2f".format(Word2VecLab.similarity(cbow, "monarch", "king"))}) than skip-gram does " +
            "(${"%.2f".format(Word2VecLab.similarity(sg, "monarch", "king"))}). On ${Word2VecLab.sentences.sumOf { it.size }} tokens the averaging that costs CBOW resolution also " +
            "lends a rare word its neighbours' evidence. The paper's advantage for skip-gram is a claim about " +
            "billions of tokens — which is the real lesson: an embedding comparison is a corpus-size claim.",
        chips = Word2VecLab.nearest(cbow, "monarch", 3).map { Chip(it.first, sub = "%.2f".format(it.second), mark = ChipMark.RESULT) },
        chipsLabel = "CBOW nearest to \"monarch\"",
        rows = listOf(
            "monarch count" to "${Word2VecLab.counts.getValue("monarch")}",
            "king count" to "${Word2VecLab.counts.getValue("king")}",
            "CBOW monarch·king" to "%.2f".format(Word2VecLab.similarity(cbow, "monarch", "king")),
            "skip-gram monarch·king" to "%.2f".format(Word2VecLab.similarity(sg, "monarch", "king")),
        ),
    )
    return frames
}

// ── D3 · word2vec: skip-gram ────────────────────────────────────────────────

private fun skipGramFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val tokens = w2vSentence()
    val centre = 2
    val contextIds = (centre - Word2VecLab.window..centre + Word2VecLab.window)
        .filter { it != centre && it in tokens.indices }

    frames += TokenFrame(
        status = "Skip-gram inverts CBOW: one centre word, and the model has to predict each word around it " +
            "separately. The same window that gave CBOW one training example gives skip-gram ${contextIds.size}.",
        chips = tokens.mapIndexed { i, t ->
            Chip(t, mark = if (i == centre) ChipMark.RESULT else if (i in contextIds) ChipMark.ACTIVE else ChipMark.DIM)
        },
        chipsLabel = "centre → context",
        rows = contextIds.map { "pair" to "(${tokens[centre]}, ${tokens[it]})" },
    )

    frames += TokenFrame(
        status = "Over the whole corpus that is ${Word2VecLab.pairs.size} training pairs from ${Word2VecLab.sentences.sumOf { it.size }} tokens — " +
            "${"%.1f".format(Word2VecLab.pairs.size.toFloat() / Word2VecLab.cbowUpdatesPerEpoch())}× the updates CBOW makes per epoch. Slower per pass, and each occurrence of a " +
            "word gets its own gradient rather than being averaged into a group.",
        rows = listOf(
            "tokens" to "${Word2VecLab.sentences.sumOf { it.size }}",
            "window" to "±${Word2VecLab.window}",
            "skip-gram pairs" to "${Word2VecLab.pairs.size}",
            "CBOW updates" to "${Word2VecLab.cbowUpdatesPerEpoch()}",
        ),
        chips = Word2VecLab.pairs.take(6).map { (c, o) -> Chip("${Word2VecLab.vocab[c]}→${Word2VecLab.vocab[o]}") },
        chipsLabel = "first pairs of the corpus",
    )

    val probes = listOf("the", "king", "monarch")
    frames += TokenFrame(
        status = "Negatives are not drawn uniformly. Counts are raised to the power 3/4 first, which is the " +
            "paper's one unexplained-but-it-works constant: it flattens \"the\" from ${"%.1f".format(Word2VecLab.unigramShare("the") * 100)}% of draws to " +
            "${"%.1f".format(Word2VecLab.noiseShare("the") * 100)}% and lifts \"monarch\" from ${"%.1f".format(Word2VecLab.unigramShare("monarch") * 100)}% to ${"%.1f".format(Word2VecLab.noiseShare("monarch") * 100)}%. Frequent words still dominate, just less.",
        bars = listOf(
            BarRow("unigram", probes.map { Word2VecLab.unigramShare(it).toFloat() }, BarPositive, probes),
            BarRow("unigram^0.75", probes.map { Word2VecLab.noiseShare(it).toFloat() }, ChipResult, probes),
        ),
    )

    frames += TokenFrame(
        status = "Cost per example, counted: a full softmax scores the centre against all ${Word2VecLab.vocab.size} words at " +
            "${Word2VecLab.dim} dimensions — ${Word2VecLab.softmaxCost()} multiply-adds — while ${Word2VecLab.negatives} negatives plus the target cost ${Word2VecLab.negativeSamplingCost()}. " +
            "That ratio is ${"%.1f".format(Word2VecLab.softmaxCost().toFloat() / Word2VecLab.negativeSamplingCost())}× here and 166,000× on a million-word vocabulary at 300 dimensions, which is why " +
            "word2vec could be trained on a billion words in 2013 on one machine.",
        rows = listOf(
            "this corpus" to "${Word2VecLab.softmaxCost()} → ${Word2VecLab.negativeSamplingCost()}",
            "|V| = 1M, d = 300" to "300,000,000 → 1,800",
            "alternative" to "hierarchical softmax, log₂|V| binary decisions",
        ),
        readout = "${"%.1f".format(Word2VecLab.softmaxCost().toFloat() / Word2VecLab.negativeSamplingCost())}× cheaper per example, here",
    )

    val sg = Word2VecLab.skipGram
    frames += TokenFrame(
        status = "After ${Word2VecLab.epochs} epochs the neighbours are the words that share contexts. king·queen is " +
            "${"%.2f".format(Word2VecLab.similarity(sg, "king", "queen"))} and king·dog is ${"%.2f".format(Word2VecLab.similarity(sg, "king", "dog"))} — the corpus never states the relationship, and the objective " +
            "never sees a definition.",
        chips = Word2VecLab.nearest(sg, "king", 4).map { Chip(it.first, sub = "%.2f".format(it.second), mark = ChipMark.RESULT) },
        chipsLabel = "nearest to \"king\"",
        bars = listOf(
            BarRow(
                "skip-gram loss",
                sg.losses.filterIndexed { i, _ -> i % 4 == 0 },
                BarNegative,
                sg.losses.filterIndexed { i, _ -> i % 4 == 0 }.mapIndexed { i, _ -> "e${i * 40}" },
            ),
        ),
    )

    val analogy = Word2VecLab.vocab.filter { it !in listOf("king", "man", "woman") }.map { w ->
        val t = FloatArray(Word2VecLab.dim) {
            sg.vector("king")[it] - sg.vector("man")[it] + sg.vector("woman")[it]
        }
        w to cosineOf(t, sg.vector(w))
    }.sortedByDescending { it.second }

    frames += TokenFrame(
        status = "And the result the paper is remembered for, computed on the vectors this lab just trained: " +
            "king − man + woman lands nearest \"${analogy.first().first}\" at cosine ${"%.2f".format(analogy.first().second)}. Directions in the space " +
            "encode relations, and nobody put them there.",
        chips = analogy.take(4).map { Chip(it.first, sub = "%.2f".format(it.second), mark = if (it == analogy.first()) ChipMark.RESULT else ChipMark.DIM) },
        chipsLabel = "nearest to king − man + woman",
        readout = "≈ ${analogy.first().first}",
    )

    frames += TokenFrame(
        status = "What the space cannot do is separate senses: \"bank\" gets one vector however it was used, " +
            "and every occurrence of it in a corpus votes on the same 300 numbers. That single limitation is " +
            "what ELMo and then BERT were built to remove.",
        chips = listOf(
            Chip("one vector", sub = "per type", mark = ChipMark.ACTIVE),
            Chip("river bank", sub = "same vector", mark = ChipMark.DIM),
            Chip("savings bank", sub = "same vector", mark = ChipMark.DIM),
        ),
        chipsLabel = "the static-embedding ceiling",
    )
    return frames
}

// ── D3 · FastText: subword composition ──────────────────────────────────────

private fun fastTextFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val model = FastTextLab.model

    frames += TokenFrame(
        status = "FastText keeps word2vec's objective and changes what a word *is*: a bag of character " +
            "n-grams of length ${FastTextLab.minN}–${FastTextLab.maxN}, taken from the word wrapped in boundary markers, plus the whole " +
            "word as one more token. The markers are what let a prefix and a suffix be distinguished from the " +
            "same letters in the middle.",
        chips = FastTextLab.subwords("king").map { Chip(it) },
        chipsLabel = "subwords of \"king\" (${FastTextLab.subwords("king").size})",
        rows = listOf("word types" to "${Word2VecLab.vocab.size}", "distinct n-grams" to "${FastTextLab.subwordVocabularySize()}"),
    )

    frames += TokenFrame(
        status = "A word's vector is the sum of its subwords' vectors. That single change means morphology is " +
            "shared: \"king\", \"kingdom\" and \"kings\" overlap in the n-grams they are built from, so evidence " +
            "for one is partial evidence for all — which matters most in Turkish, Finnish or German, where a " +
            "lemma has hundreds of surface forms.",
        chips = listOf("king", "kingdom").flatMap { w ->
            FastTextLab.subwords(w).take(4).map { Chip(it, sub = w) }
        },
        chipsLabel = "shared n-grams",
        readout = "v(w) = Σ v(g) over g ∈ subwords(w)",
    )

    val oov = "kings"
    val (known, total) = model.coverage(oov)
    frames += TokenFrame(
        status = "Here is the payoff. \"$oov\" never appears in the corpus, so word2vec has no vector for it at " +
            "all — the lookup misses and the word becomes <unk>. FastText builds one from the $known of its $total " +
            "n-grams that *were* seen, and the result sits at cosine ${"%.2f".format(FastTextLab.similarityToKnown(model, oov, "king"))} to \"king\".",
        chips = FastTextLab.subwords(oov).map {
            Chip(it, mark = if (it in model.subwordVectors) ChipMark.RESULT else ChipMark.DIM)
        },
        chipsLabel = "\"$oov\" — highlighted n-grams were seen in training",
        rows = listOf(
            "in vocabulary?" to if (FastTextLab.isOov(oov)) "no — word2vec returns <unk>" else "yes",
            "n-grams known" to "$known of $total",
            "cos($oov, king)" to "%.2f".format(FastTextLab.similarityToKnown(model, oov, "king")),
        ),
    )

    val probe = "monarchy"
    frames += TokenFrame(
        status = "It generalises past inflection to derivation: \"$probe\" is also unseen, shares no whole word " +
            "with the corpus, and still lands near both \"monarch\"-adjacent regions — cosine " +
            "${"%.2f".format(FastTextLab.similarityToKnown(model, probe, "king"))} to \"king\" and ${"%.2f".format(FastTextLab.similarityToKnown(model, probe, "kingdom"))} to \"kingdom\", against ${"%.2f".format(FastTextLab.similarityToKnown(model, probe, "dog"))} to \"dog\".",
        bars = listOf(
            BarRow(
                "cosine from \"$probe\"",
                listOf("king", "kingdom", "queen", "dog").map { FastTextLab.similarityToKnown(model, probe, it) },
                BarPositive,
                listOf("king", "kingdom", "queen", "dog"),
            ),
        ),
    )

    frames += TokenFrame(
        status = "One honest caveat about this lab: real FastText trains subword vectors jointly with the " +
            "objective, while these are derived from the trained word vectors — so an unseen word whose known " +
            "n-grams all come from one word reproduces that word exactly (\"kingdoms\" scores " +
            "${"%.2f".format(FastTextLab.similarityToKnown(model, "kingdoms", "kingdom"))} against \"kingdom\"). What the lab demonstrates is composition, not the training of it.",
        chips = listOf("kingdoms", "queenly").map {
            Chip(it, sub = "${model.coverage(it).first}/${model.coverage(it).second}", mark = ChipMark.ACTIVE)
        },
        chipsLabel = "composed from n-grams alone",
        rows = listOf(
            "kingdoms · kingdom" to "%.2f".format(FastTextLab.similarityToKnown(model, "kingdoms", "kingdom")),
            "queenly · queen" to "%.2f".format(FastTextLab.similarityToKnown(model, "queenly", "queen")),
        ),
    )

    frames += TokenFrame(
        status = "The cost is size: ${FastTextLab.subwordVocabularySize()} n-gram vectors for ${Word2VecLab.vocab.size} words here, and on a real corpus " +
            "millions more. Production FastText hashes n-grams into a fixed 2M-bucket table and accepts the " +
            "collisions — the model stays a lookup table plus a sum, which is why it still runs where a " +
            "transformer cannot.",
        rows = listOf(
            "n-gram vectors" to "${FastTextLab.subwordVocabularySize()}",
            "word vectors" to "${Word2VecLab.vocab.size}",
            "production trick" to "hash to 2,000,000 buckets",
            "inference" to "one sum per word — no network runs",
        ),
        readout = "OOV solved, memory paid",
    )
    return frames
}

// ── D5 · Prompt engineering ──────────────────────────────────────────────────

private val ruleCaptions = listOf("first", "last", "middle", "freq", "alpha")

private fun versionSpaceBar(demos: List<String>): BarRow {
    val survivors = PromptLab.consistent(demos).map { it.name }.toSet()
    val mass = if (survivors.isEmpty()) 0f else 1f / survivors.size
    return BarRow(
        "posterior over rules (${survivors.size} still fit)",
        PromptLab.rules.map { if (it.name in survivors) mass else 0f },
        BarPositive,
        ruleCaptions,
    )
}

private fun answerBar(demos: List<String>): BarRow {
    val posterior = PromptLab.prediction(demos)
    val letters = posterior.keys.sorted()
    return BarRow(
        "answer for \"${PromptLab.query}\" (correct is '${PromptLab.label(PromptLab.query)}')",
        letters.map { posterior.getValue(it).toFloat() },
        ChipResult,
        letters.map { "'$it'" },
    )
}

private fun demoChips(demos: List<String>, upTo: Int): List<Chip> =
    demos.mapIndexed { index, word ->
        Chip(
            word,
            "→ ${PromptLab.label(word)}",
            when {
                index == upTo - 1 -> ChipMark.ACTIVE
                index < upTo -> ChipMark.RESULT
                else -> ChipMark.DIM
            },
        )
    }

private fun promptEngineeringFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val pool = PromptLab.pool

    frames += TokenFrame(
        status = "A prompt is an induction problem. The task here is \"word → one letter\", and before any " +
            "example is given, ${PromptLab.rules.size} simple rules are all consistent with that description — " +
            "first letter, last letter, middle letter, most frequent letter, alphabetically first letter. " +
            "Zero-shot means asking the model to guess which one you meant.",
        chips = demoChips(pool, 0),
        chipsLabel = "the demonstration pool, none used yet",
        bars = listOf(versionSpaceBar(emptyList()), answerBar(emptyList())),
        readout = "5 rules fit the instruction; only one is yours",
    )

    listOf(1, 2, 3, 4).forEach { k ->
        val demos = pool.take(k)
        val survivors = PromptLab.consistent(demos)
        val status = when (k) {
            1 -> "One demonstration, banana → a. That eliminates \"first letter\" and nothing else: banana's " +
                "last, middle, most frequent and alphabetically first letters are all 'a'. A demonstration is " +
                "worth exactly the hypotheses it kills, and this one killed one."
            2 -> "adage → a leaves three rules standing, and now the model has enough to answer confidently and " +
                "wrongly: two of the three surviving rules say 'a' for kayak, so the majority answer is 'a' " +
                "where the truth is 'y'. More examples did not mean closer to right."
            3 -> "otter → t removes the alphabetical rule, and the version space is down to two — but they " +
                "disagree on the query and each holds half the mass. This is the honest state of a three-shot " +
                "prompt on this task: not wrong, undetermined."
            else -> "level → v is the demonstration that does the work. Only \"middle letter\" survives it, and " +
                "the answer collapses onto 'y'. Notice which example that was: not the fourth one, the " +
                "*discriminating* one."
        }
        frames += TokenFrame(
            status = status,
            chips = demoChips(pool, k),
            chipsLabel = "$k demonstration${if (k == 1) "" else "s"} in the prompt",
            bars = listOf(versionSpaceBar(demos), answerBar(demos)),
            rows = listOf(
                "rules left" to "${survivors.size} — ${survivors.joinToString { it.name }}",
                "answer" to if (PromptLab.predictedCorrectly(demos)) "'y' ✓" else "not yet determined",
            ),
            readout = "${survivors.size} of ${PromptLab.rules.size} rules survive",
        )
    }

    val identifying = PromptLab.identifyingPairs()
    val failing = PromptLab.allPairs().filterNot { it in identifying }
    frames += TokenFrame(
        status = "Which demonstrations, not how many. Of the ${PromptLab.allPairs().size} two-example prompts " +
            "this pool allows, ${identifying.size} pin the rule exactly and ${failing.size} do not — and the " +
            "${failing.size} that fail are made only of ${failing.flatMap { listOf(it.first, it.second) }.distinct().joinToString()}, " +
            "the words whose letters happen to coincide. Two well-chosen examples beat the four we just spent.",
        chips = listOf(
            Chip("level", "→ v", ChipMark.RESULT),
            Chip("sonar", "→ n", ChipMark.RESULT),
            Chip("banana", "→ a", ChipMark.DIM),
            Chip("adage", "→ a", ChipMark.DIM),
        ),
        chipsLabel = "two that identify the rule; two that do not",
        bars = listOf(versionSpaceBar(listOf("level", "sonar")), answerBar(listOf("level", "sonar"))),
        rows = listOf(
            "identifying pairs" to "${identifying.size} of ${PromptLab.allPairs().size}",
            "{level, sonar}" to "${PromptLab.consistent(listOf("level", "sonar")).size} rule left",
            "{banana, adage}" to "${PromptLab.consistent(listOf("banana", "adage")).size} rules left",
        ),
        readout = "example selection is the tuning knob",
    )

    val eliminated = PromptLab.instructionEliminates()
    frames += TokenFrame(
        status = "And an instruction is not data — it is a prior. \"Take a letter from the middle of the word\" " +
            "adds no examples, but it puts zero mass on ${eliminated.size} of the five rules " +
            "(${eliminated.joinToString { it.name }}). Reaching the same state by demonstration alone takes " +
            "${PromptLab.demosToEliminate(eliminated)} examples from this pool, which is what \"instruction plus " +
            "one example\" is buying over \"five examples\".",
        chips = listOf(
            Chip("instruction", "prior", ChipMark.ACTIVE),
            Chip("examples", "data", ChipMark.IDLE),
        ),
        bars = listOf(
            BarRow(
                "rules ruled out",
                PromptLab.rules.map { rule -> if (eliminated.any { it.name == rule.name }) 1f else 0f },
                ChipResult,
                ruleCaptions,
            ),
        ),
        rows = listOf(
            "instruction eliminates" to "${eliminated.size} rules",
            "same by example" to "${PromptLab.demosToEliminate(eliminated)} demonstrations",
            "what neither fixes" to "a rule outside the space you imagined",
        ),
        readout = "one sentence did the work of ${PromptLab.demosToEliminate(eliminated)} examples",
    )
    return frames
}

// ── D5 · ReAct ───────────────────────────────────────────────────────────────

private fun reactFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val ranked = RetrievalLab.ranked(ReActLab.question)
    val answerRank = RetrievalLab.rankOf(ReActLab.question, ReActLab.hop2PassageId)
    val (top, contains) = ReActLab.singleShot()

    frames += TokenFrame(
        status = "The question is two-hop: \"${ReActLab.question}\" The passage holding the answer is about C, " +
            "not about Linux, so it shares almost no vocabulary with the question as asked. Retrieval is real " +
            "here — TF-IDF cosine over the eight-passage corpus — so what it ranks is measured, not narrated.",
        chips = ranked.take(4).map { (passage, score) ->
            Chip(passage.title, "%.3f".format(score), if (passage.id == ReActLab.hop2PassageId) ChipMark.RESULT else ChipMark.IDLE)
        },
        chipsLabel = "top passages for the question, verbatim",
        bars = listOf(
            BarRow(
                "cosine score",
                ranked.map { it.second.toFloat() },
                BarPositive,
                ranked.map { it.first.title.take(6) },
            ),
        ),
        rows = listOf(
            "answer passage" to "\"${RetrievalLab.corpus[ReActLab.hop2PassageId].title}\", rank $answerRank of ${ranked.size}",
            "retrieved (top 3)" to top.joinToString { it.title },
            "answer present" to if (contains) "yes" else "no",
        ),
        readout = "one retrieval cannot reach the second hop",
    )

    frames += TokenFrame(
        status = "The two failure modes this sits between. Answering closed-book produces " +
            "\"${ReActLab.closedBookAnswer}\" — fluent, specific, and wrong. Reasoning harder without acting " +
            "produces a longer version of the same guess, because the missing thing is not inference, it is a " +
            "fact the model does not have. ReAct's claim is that thought and action have to interleave.",
        chips = listOf(
            Chip("closed book", ReActLab.closedBookAnswer, ChipMark.ACTIVE),
            Chip("reason only", ReActLab.closedBookAnswer, ChipMark.ACTIVE),
            Chip("ReAct", ReActLab.groundedAnswer, ChipMark.RESULT),
        ),
        chipsLabel = "three ways to answer, one of them right",
        rows = listOf(
            "what reasoning fixes" to "steps you can derive",
            "what retrieval fixes" to "facts you do not hold",
            "this question needs" to "both, in that order",
        ),
        readout = "more thinking does not recover a fact you never had",
    )

    ReActLab.trajectory.forEachIndexed { index, turn ->
        val retrieved = turn.retrievedId?.let { RetrievalLab.corpus[it] }
        val queryText = turn.action.substringAfter("(\"").substringBefore("\")")
        frames += TokenFrame(
            status = "Turn ${index + 1}. ${turn.thought}",
            chips = listOf(
                Chip("Thought", "step ${index + 1}", ChipMark.IDLE),
                Chip("Action", turn.action.substringBefore("("), ChipMark.ACTIVE),
                Chip("Observation", retrieved?.title ?: "final", ChipMark.RESULT),
            ),
            chipsLabel = "thought → action → observation",
            rows = listOfNotNull(
                "action" to turn.action,
                "observation" to turn.observation,
                retrieved?.let { "rank of this hit" to "${RetrievalLab.rankOf(queryText, it.id)} for \"$queryText\"" },
            ),
            readout = if (turn.retrievedId == null) "answer: ${turn.observation}" else "observation feeds the next thought",
        )
    }

    frames += TokenFrame(
        status = "What the loop actually bought, in one number: the answer passage sits at rank $answerRank " +
            "under the question as asked and at rank " +
            "${RetrievalLab.rankOf("C language first released", ReActLab.hop2PassageId)} under the query the " +
            "second thought wrote. The rewrite is the work. That also names the loop's failure modes — a " +
            "thought that writes a bad query, and a loop with no step cap that reissues it forever.",
        bars = listOf(
            BarRow(
                "rank of the answer passage (lower is better)",
                listOf(answerRank.toFloat(), RetrievalLab.rankOf("C language first released", ReActLab.hop2PassageId).toFloat()),
                ChipResult,
                listOf("original question", "rewritten query"),
            ),
        ),
        rows = listOf(
            "hops needed" to "2",
            "step cap" to "required — a stuck loop repeats its own action",
            "what to log" to "every action and observation, or nothing is debuggable",
        ),
        readout = "rank $answerRank → rank ${RetrievalLab.rankOf("C language first released", ReActLab.hop2PassageId)}, from one rewrite",
    )
    return frames
}

// ── D5 · Agents and tool use ─────────────────────────────────────────────────

private fun agentFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()

    frames += TokenFrame(
        status = "An agent is a loop with three parts: a model that emits a tool call, a runtime that executes " +
            "it, and a transcript that carries the result back. The tools are declared as typed schemas, and " +
            "those schemas are in the context on every single turn — before any work happens, this agent is " +
            "paying ${AgentLab.schemaTokens()} tokens per request just to describe what it can do.",
        chips = AgentLab.tools.map { Chip(it.name, "${it.schemaTokens} tok", ChipMark.IDLE) },
        chipsLabel = "declared tools",
        bars = listOf(
            BarRow(
                "schema tokens",
                AgentLab.tools.map { it.schemaTokens.toFloat() },
                BarPositive,
                AgentLab.tools.map { it.name },
            ),
        ),
        rows = listOf(
            "system prompt" to "${AgentLab.systemTokens} tokens",
            "tool schemas" to "${AgentLab.schemaTokens()} tokens",
            "fixed overhead" to "${AgentLab.systemTokens + AgentLab.schemaTokens()} tokens, every turn",
        ),
        readout = "the tool list is a per-turn cost, not a one-off",
    )

    frames += TokenFrame(
        status = "The trajectory, one entry per turn. Step 4 is a real failure: the unit tool rejected \"mi\", " +
            "the error came back as an observation, and the model reissued the call with \"mile\". That " +
            "recovery is the behaviour worth having — but it is not free, and the next frames price it.",
        chips = AgentLab.trajectory.map {
            Chip(it.label.substringBefore("(").take(12), "${it.tokens}", if (it.failed) ChipMark.ACTIVE else ChipMark.IDLE)
        },
        chipsLabel = "eight transcript entries; the highlighted two are the failed call and its retry",
        bars = listOf(
            BarRow(
                "tokens added per entry",
                AgentLab.trajectory.map { it.tokens.toFloat() },
                BarPositive,
                AgentLab.trajectory.indices.map { "${it + 1}" },
            ),
        ),
        readout = "an error is an observation, not an exception",
    )

    frames += TokenFrame(
        status = "Here is the part that surprises people. The transcript is stateless: every model turn resends " +
            "the whole thing. Context grows from ${AgentLab.contextAt(1)} tokens to " +
            "${AgentLab.finalContext()}, but the *bill* is the sum of every request, which comes to " +
            "${AgentLab.billedTokens()} tokens — ${"%.1f".format(AgentLab.billingMultiple())}× the final " +
            "context. Cost is quadratic in the number of steps while the transcript is only linear.",
        bars = listOf(
            BarRow(
                "context size at each model turn",
                AgentLab.trajectory.indices.filter { it % 2 == 0 }.map { AgentLab.contextAt(it + 1).toFloat() },
                ChipResult,
                AgentLab.trajectory.indices.filter { it % 2 == 0 }.map { "turn ${it / 2 + 1}" },
            ),
        ),
        rows = listOf(
            "final context" to "${AgentLab.finalContext()} tokens",
            "actually billed" to "${AgentLab.billedTokens()} tokens",
            "multiple" to "${"%.2f".format(AgentLab.billingMultiple())}×",
        ),
        readout = "linear transcript, quadratic bill",
    )

    frames += TokenFrame(
        status = "So a retry costs far more than the error message. Dropping the failed call and its reissue " +
            "leaves a trajectory that bills ${AgentLab.billedTokens(AgentLab.cleanTrajectory)} tokens against " +
            "this one's ${AgentLab.billedTokens()} — the two extra entries added " +
            "${AgentLab.retryOverhead()} billed tokens, or " +
            "${"%.0f".format(100.0 * AgentLab.retryOverhead() / AgentLab.billedTokens(AgentLab.cleanTrajectory))}%, " +
            "because everything after them is resent with them attached.",
        bars = listOf(
            BarRow(
                "billed tokens",
                listOf(AgentLab.billedTokens(AgentLab.cleanTrajectory).toFloat(), AgentLab.billedTokens().toFloat()),
                ChipResult,
                listOf("clean run", "with one retry"),
            ),
        ),
        rows = listOf(
            "retry overhead" to "${AgentLab.retryOverhead()} tokens",
            "why so large" to "the retry is resent on every later turn too",
            "the lever" to "tool schemas that make the bad call impossible",
        ),
        readout = "a strict enum in the schema is cheaper than a retry",
    )

    frames += TokenFrame(
        status = "The other lever is shape rather than size. These three calls do not depend on each other, so " +
            "issuing them in one turn costs ${AgentLab.parallelLatency()} ms — the slowest tool — instead of " +
            "${AgentLab.sequentialLatency()} ms of round trips, and it collapses three model turns into one. " +
            "Sequential turns are only required when a later call needs an earlier observation, which is " +
            "exactly the case ReAct is about.",
        bars = listOf(
            BarRow(
                "latency (ms)",
                listOf(AgentLab.sequentialLatency().toFloat(), AgentLab.parallelLatency().toFloat()),
                BarPositive,
                listOf("sequential ${AgentLab.sequentialLatency()}", "parallel ${AgentLab.parallelLatency()}"),
            ),
        ),
        rows = listOf(
            "independent calls" to "batch them into one turn",
            "dependent calls" to "must interleave — that is the ReAct loop",
            "step cap" to "the only thing between a loop and an unbounded bill",
        ),
        readout = "${AgentLab.sequentialLatency()} ms → ${AgentLab.parallelLatency()} ms, and two fewer billed turns",
    )
    return frames
}

// ── C5 · Bidirectional RNNs ──────────────────────────────────────────────────
// `BiRnnLab` trains two taggers on a corpus of garden-path minimal pairs. What makes the comparison
// worth drawing is that the left-to-right model's ceiling is enumerated from the corpus before
// either model exists, so the frames below are checking a prediction rather than reporting a score.

private fun bidirectionalFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val short = BiRnnLab.corpus[4]
    val long = BiRnnLab.corpus[5]
    val disputed = 2
    val forward = BiRnnLab.forwardTagger
    val bi = BiRnnLab.biTagger

    fun chipsFor(sentence: BiRnnLab.Sentence, highlight: Int, upTo: Int = sentence.words.size) =
        sentence.words.mapIndexed { index, word ->
            Chip(
                word,
                sentence.labels[index],
                when {
                    index == highlight -> ChipMark.ACTIVE
                    index < upTo -> ChipMark.IDLE
                    else -> ChipMark.DIM
                },
            )
        }

    frames += TokenFrame(
        status = "Two sentences that share their first ${disputed + 1} words. In the first, \"raced\" is the main " +
            "verb. In the second it is a reduced relative — \"the horse [that was] raced past the barn\" — and the " +
            "main verb is \"fell\", six words later. Nothing before \"raced\" distinguishes them.",
        chips = chipsFor(short, disputed),
        chipsLabel = "\"${short.words.joinToString(" ")}\"",
        rows = listOf(
            "second reading" to "\"${long.words.joinToString(" ")}\"",
            "shared prefix" to "\"${short.words.take(disputed + 1).joinToString(" ")}\"",
            "decided by" to "\"${long.words.last()}\", ${long.words.size - 1 - disputed} words to the right",
        ),
    )

    frames += TokenFrame(
        status = "A left-to-right tagger reads position ${disputed + 1} with only the words up to it in its state. " +
            "Those words are identical in both sentences, so its state is identical, so its prediction is " +
            "identical — before any training, and whatever the training does. That is not a weakness of the fit; " +
            "it is what the architecture can represent.",
        chips = chipsFor(long, disputed, upTo = disputed + 1),
        chipsLabel = "what the forward pass has seen at \"${long.words[disputed]}\"",
        readout = "same prefix → same hidden state → same tag",
    )

    val ambiguous = BiRnnLab.leftAmbiguous
    frames += TokenFrame(
        status = "How much of the corpus that costs is countable, and counting it needs no model at all. Of " +
            "${BiRnnLab.tokenCount} tagged tokens, ${ambiguous.size} sit at positions where two sentences share a " +
            "prefix and disagree on the tag. With the whole sentence visible, ${BiRnnLab.fullyAmbiguous.size} do. " +
            "The best any left-to-right tagger can score here is therefore " +
            "${"%.1f".format(BiRnnLab.forwardCeiling * 100)}%.",
        rows = listOf(
            "tokens" to "${BiRnnLab.tokenCount}",
            "ambiguous from the left" to "${ambiguous.size} (${"%.1f".format(ambiguous.size * 100.0 / BiRnnLab.tokenCount)}%)",
            "ambiguous from both sides" to "${BiRnnLab.fullyAmbiguous.size}",
            "enumerated ceiling" to "${"%.4f".format(BiRnnLab.forwardCeiling)} = ${(BiRnnLab.forwardCeiling * BiRnnLab.tokenCount).toInt()}/${BiRnnLab.tokenCount}",
        ),
        readout = "the ceiling is a property of the corpus, not the run",
    )

    val forwardShort = forward.tagDistribution(short.words, disputed)
    val forwardLong = forward.tagDistribution(long.words, disputed)
    frames += TokenFrame(
        status = "Trained, the forward tagger does exactly that. Its distribution over tags at \"raced\" is the " +
            "same in both sentences and it is a coin flip: VERB " +
            "${"%.3f".format(forwardShort[BiRnnLab.tags.indexOf("VERB")])}, PART " +
            "${"%.3f".format(forwardShort[BiRnnLab.tags.indexOf("PART")])}. It has learned the only thing available " +
            "— that this prefix is followed by one tag half the time and the other half the time.",
        bars = listOf(
            BarRow("forward tagger, sentence 1", forwardShort.map { it.toFloat() }, BarPositive, BiRnnLab.tags),
            BarRow("forward tagger, sentence 2", forwardLong.map { it.toFloat() }, BarNegative, BiRnnLab.tags),
        ),
        readout = "two sentences, one distribution",
    )

    frames += TokenFrame(
        status = "A bidirectional layer adds a second pass over the same sentence, right to left, and tags each " +
            "position from both states joined. At \"raced\", the backward state has already read " +
            "\"${long.words.drop(disputed + 1).joinToString(" ")}\" — including the word that settles it.",
        chips = long.words.mapIndexed { index, word ->
            Chip(
                word,
                if (index > disputed) "read" else null,
                when {
                    index == disputed -> ChipMark.ACTIVE
                    index == long.words.size - 1 -> ChipMark.RESULT
                    index > disputed -> ChipMark.IDLE
                    else -> ChipMark.DIM
                },
            )
        },
        chipsLabel = "what the backward pass has seen at \"${long.words[disputed]}\"",
        readout = "the deciding token is to the right",
    )

    val biShort = bi.tagDistribution(short.words, disputed)
    val biLong = bi.tagDistribution(long.words, disputed)
    frames += TokenFrame(
        status = "With both directions, the same position gets two different answers in the two sentences: VERB " +
            "${"%.3f".format(biShort[BiRnnLab.tags.indexOf("VERB")])} in the first, PART " +
            "${"%.3f".format(biLong[BiRnnLab.tags.indexOf("PART")])} in the second. Every one of the " +
            "${ambiguous.size} ambiguous positions goes the same way.",
        bars = listOf(
            BarRow("bidirectional, sentence 1", biShort.map { it.toFloat() }, BarPositive, BiRnnLab.tags),
            BarRow("bidirectional, sentence 2", biLong.map { it.toFloat() }, BarNegative, BiRnnLab.tags),
        ),
        readout = "0.5/0.5 → 1.0/0.0, in both directions",
    )

    frames += TokenFrame(
        status = "Scored over the corpus: the forward tagger lands on ${"%.4f".format(forward.accuracy)} — the " +
            "enumerated ceiling to four decimals, and ${"%.1f".format(forward.accuracyAt(ambiguous) * 100)}% on the " +
            "ambiguous positions, which is the coin flip. The bidirectional tagger scores " +
            "${"%.1f".format(bi.accuracy * 100)}%. The gap is not the optimizer; it was fixed before training " +
            "started.",
        bars = listOf(
            BarRow(
                "accuracy",
                listOf(BiRnnLab.forwardCeiling.toFloat(), forward.accuracy.toFloat(), bi.accuracy.toFloat()),
                ChipResult,
                listOf("ceiling", "forward", "bi"),
            ),
            BarRow(
                "on the ${ambiguous.size} ambiguous positions",
                listOf(forward.accuracyAt(ambiguous).toFloat(), bi.accuracyAt(ambiguous).toFloat()),
                BarPositive,
                listOf("forward", "bi"),
            ),
        ),
        readout = "predicted ${"%.4f".format(BiRnnLab.forwardCeiling)}, measured ${"%.4f".format(forward.accuracy)}",
    )

    frames += TokenFrame(
        status = "What it costs: ${bi.parameterCount} parameters against ${forward.parameterCount}, two passes " +
            "instead of one, and — the part that decides real systems — no output at all until the sentence ends. " +
            "A bidirectional tagger cannot label a word as it is typed or transcribed. That is why BERT is " +
            "bidirectional and a decoder is not.",
        rows = listOf(
            "parameters" to "${forward.parameterCount} → ${bi.parameterCount} (${"%.2f".format(bi.parameterCount.toDouble() / forward.parameterCount)}×)",
            "passes per sentence" to "1 → 2",
            "streaming" to "possible → impossible",
            "use it when" to "the whole sequence is already in hand",
        ),
        readout = "accuracy bought with latency",
    )
    return frames
}

// ── C5 · Encoder-decoder ─────────────────────────────────────────────────────
// `Seq2SeqLab` trains two identical models on a copy task. The only difference between them is the
// order the encoder reads the source, which is Sutskever et al.'s reversal trick — and on a copy
// task it is exactly the right experiment, because nothing else in the setup moves.

private fun encoderDecoderFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val model = Seq2SeqLab.forwardFed
    val reversed = Seq2SeqLab.reverseFed
    val example = Seq2SeqLab.testSet.first { it.source.size == Seq2SeqLab.MAX_LENGTH }
    val lengths = (1..Seq2SeqLab.MAX_LENGTH).map { "L$it" }
    val positions = (1..Seq2SeqLab.MAX_LENGTH).map { "p$it" }

    frames += TokenFrame(
        status = "An encoder-decoder is two recurrent networks joined by one vector. The encoder reads the source " +
            "and its final state — the context vector — is the decoder's initial state. That vector is the entire " +
            "channel between them: whatever the source contains has to be in it, or it is gone.",
        chips = example.source.map { Chip(Seq2SeqLab.symbolNames[it]) },
        chipsLabel = "source (${example.source.size} symbols)",
        rows = listOf(
            "encoder" to "${Seq2SeqLab.HIDDEN}-unit RNN, reads left to right",
            "context vector" to "${Seq2SeqLab.HIDDEN} numbers",
            "decoder" to "${Seq2SeqLab.HIDDEN}-unit RNN, emits until EOS",
            "task" to "copy the source — no transformation at all",
        ),
    )

    frames += TokenFrame(
        status = "Here is that vector for the source above. The task is the weakest thing that can be asked of a " +
            "sequence model — reproduce the input — which is what makes it a clean test: every failure below is " +
            "the vector losing the source, not the model failing to compute something.",
        bars = listOf(BarRow("context vector", model.context(example.source).map { it.toFloat() }, BarPositive)),
        readout = "${Seq2SeqLab.HIDDEN} numbers, whatever the source length",
    )

    val byLength = model.exactMatchByLength(Seq2SeqLab.testSet)
    frames += TokenFrame(
        status = "Exact-match accuracy against source length, on held-out sources. One symbol: " +
            "${"%.0f".format(byLength.first().second * 100)}%. Two: " +
            "${"%.0f".format(byLength[1].second * 100)}%. By four it is gone. Every length was trained on the same " +
            "way — what changes is how much has to fit through the vector.",
        bars = listOf(
            BarRow("exact match by source length", byLength.map { it.second.toFloat() }, ChipResult, lengths),
        ),
        readout = "the bottleneck has a length, and it is short",
    )

    frames += TokenFrame(
        status = "Stated as capacity: each symbol is one of ${Seq2SeqLab.SYMBOLS}, so a ${Seq2SeqLab.MAX_LENGTH}-symbol " +
            "source is ${"%.1f".format(Seq2SeqLab.sourceBits(Seq2SeqLab.MAX_LENGTH))} bits, and the context vector " +
            "has ${Seq2SeqLab.HIDDEN} tanh-squashed numbers to hold them in. The bound is soft — real numbers are " +
            "not bits — but the direction is the point: the source grows and the vector does not.",
        bars = listOf(
            BarRow(
                "bits in the source",
                (1..Seq2SeqLab.MAX_LENGTH).map { Seq2SeqLab.sourceBits(it).toFloat() },
                BarNegative,
                lengths,
            ),
        ),
        rows = listOf(
            "source at length ${Seq2SeqLab.MAX_LENGTH}" to "${"%.1f".format(Seq2SeqLab.sourceBits(Seq2SeqLab.MAX_LENGTH))} bits",
            "context vector" to "${Seq2SeqLab.HIDDEN} dimensions, fixed",
        ),
    )

    val forwardProbe = Seq2SeqLab.probeProfile(model)
    frames += TokenFrame(
        status = "Which part of the source survives? Freeze the encoder, fit a linear read-out from the context " +
            "vector to the symbol at one position, and score it on held-out sources. Position " +
            "${Seq2SeqLab.MAX_LENGTH} is recoverable ${"%.0f".format(forwardProbe.last().second * 100)}% of the " +
            "time; position 1 — the one the decoder needs first — " +
            "${"%.0f".format(forwardProbe.first().second * 100)}%. The vector remembers what it read last.",
        bars = listOf(
            BarRow("recoverable from the context vector", forwardProbe.map { it.second.toFloat() }, BarPositive, positions),
        ),
        readout = "recency, measured on a frozen encoder",
    )

    val reverseProbe = Seq2SeqLab.probeProfile(reversed)
    frames += TokenFrame(
        status = "So feed the encoder the source backwards. Same data, same parameter count, same budget — only " +
            "the reading order changes, which puts the symbol the decoder emits first nearest the handover. The " +
            "probe flips: position 1 is now ${"%.0f".format(reverseProbe.first().second * 100)}% recoverable. This " +
            "is the trick Sutskever et al. reported in 2014, and it costs nothing.",
        bars = listOf(
            BarRow("forward-fed encoder", forwardProbe.map { it.second.toFloat() }, BarNegative, positions),
            BarRow("reverse-fed encoder", reverseProbe.map { it.second.toFloat() }, BarPositive, positions),
        ),
        readout = "p1 ${"%.2f".format(forwardProbe.first().second)} → ${"%.2f".format(reverseProbe.first().second)}",
    )

    val forwardExact = model.exactMatch(Seq2SeqLab.testSet)
    val reverseExact = reversed.exactMatch(Seq2SeqLab.testSet)
    frames += TokenFrame(
        status = "End to end, that reordering takes exact match from ${"%.1f".format(forwardExact * 100)}% to " +
            "${"%.1f".format(reverseExact * 100)}% and pushes the collapse two lengths further right. Nothing was " +
            "added: the same ${model.parameterCount} parameters, differently ordered input.",
        bars = listOf(
            BarRow("forward-fed, by length", byLength.map { it.second.toFloat() }, BarNegative, lengths),
            BarRow("reverse-fed, by length", reversed.exactMatchByLength(Seq2SeqLab.testSet).map { it.second.toFloat() }, BarPositive, lengths),
        ),
        readout = "${"%.3f".format(forwardExact)} → ${"%.3f".format(reverseExact)} exact match",
    )

    frames += TokenFrame(
        status = "But look at what reversal did not fix: length ${Seq2SeqLab.MAX_LENGTH} is still near zero. " +
            "Reordering moves which end of the source survives; it does not make the vector bigger. Every fix that " +
            "actually removed this bottleneck — attention, then the transformer — did the same thing instead: keep " +
            "*all* the encoder states and let the decoder choose among them at each step.",
        rows = listOf(
            "reversal" to "changes which positions survive",
            "a bigger vector" to "buys length, and costs quadratically to train",
            "attention" to "removes the single-vector constraint entirely",
            "still true" to "at length ${Seq2SeqLab.MAX_LENGTH}: ${"%.0f".format(byLength.last().second * 100)}% vs ${"%.0f".format(reversed.exactMatchByLength(Seq2SeqLab.testSet).last().second * 100)}%",
        ),
        readout = "one vector is the constraint attention deleted",
    )
    return frames
}

// ── C5 · Seq2Seq decoding ────────────────────────────────────────────────────
// The same trained model as the encoder-decoder lab, decoded three ways. The lab's argument is the
// error split: separating "the search missed it" from "the model prefers the wrong answer" turns
// beam width from a knob you turn hopefully into one with a measurable ceiling.

private fun seq2seqFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val model = Seq2SeqLab.forwardFed
    val width = 5
    // An example where the search actually changes the answer — picked by running both, not chosen
    // by hand, so a re-tune cannot leave the frame narrating a difference that is no longer there.
    val example = Seq2SeqLab.testSet.firstOrNull { item ->
        val greedy = model.greedy(item.source)
        val beam = model.beam(item.source, width)
        !greedy.tokens.contentEquals(beam.tokens) && beam.logProbability > greedy.logProbability
    } ?: Seq2SeqLab.testSet.first()
    val greedy = model.greedy(example.source)
    val beam = model.beam(example.source, width)
    val symbols = Seq2SeqLab.symbolNames + listOf("EOS")

    frames += TokenFrame(
        status = "Decoding is a search. At each step the decoder gives a distribution over the next symbol, and " +
            "something has to pick one — the model does not emit a sequence, it scores them.",
        chips = example.source.map { Chip(Seq2SeqLab.symbolNames[it]) },
        chipsLabel = "source",
        rows = listOf(
            "gold output" to example.text,
            "candidates of length ≤ ${Seq2SeqLab.MAX_LENGTH}" to "${(1..Seq2SeqLab.MAX_LENGTH).sumOf { l -> Math.pow(Seq2SeqLab.SYMBOLS.toDouble(), l.toDouble()) }.toLong()}",
        ),
    )

    model.decodeTrace(example.source).forEachIndexed { index, step ->
        val emitted = if (step.emitted == Seq2SeqLab.EOS) "EOS" else Seq2SeqLab.symbolNames[step.emitted]
        frames += TokenFrame(
            status = "Greedy step ${index + 1}: take the argmax (${emitted}, " +
                "p = ${"%.3f".format(step.distribution[step.emitted])}), feed it back in, and never reconsider it. " +
                (if (step.emitted == Seq2SeqLab.EOS) "EOS ends the sequence." else "A step taken here is a step the rest of the sequence is conditioned on."),
            chips = (0 until index).map { Chip(Seq2SeqLab.symbolNames[model.decodeTrace(example.source)[it].emitted]) } +
                listOf(Chip(emitted, "%.3f".format(step.distribution[step.emitted]), ChipMark.RESULT)),
            chipsLabel = "output so far",
            bars = listOf(BarRow("next-symbol distribution", step.distribution.map { it.toFloat() }, BarPositive, symbols)),
        )
    }

    val trace = model.beamTrace(example.source, width)
    frames += TokenFrame(
        status = "Beam search keeps the best $width partial sequences instead of one, scoring each by the sum of " +
            "its log-probabilities. Here is the beam after each step. A hypothesis that looked second-best early " +
            "can end up first, which is the whole reason to keep it.",
        rows = trace.mapIndexed { index, live ->
            "step ${index + 1}" to live.joinToString("  ") { "${it.text.ifEmpty { "∅" }} ${"%.2f".format(it.score)}" }
        },
        readout = "greedy is beam search with width 1",
    )

    frames += TokenFrame(
        status = "On this source they disagree. Greedy returns \"${greedy.text}\" at p = " +
            "${"%.4f".format(greedy.probability)}; the width-$width beam returns \"${beam.text}\" at p = " +
            "${"%.4f".format(beam.probability)} — ${"%.1f".format(beam.probability / greedy.probability)}× more " +
            "probable under the same model. The gold answer is \"${example.text}\".",
        chips = listOf(
            Chip(greedy.text.ifEmpty { "∅" }, "greedy", if (greedy.tokens.contentEquals(example.target)) ChipMark.RESULT else ChipMark.DIM),
            Chip(beam.text.ifEmpty { "∅" }, "beam $width", if (beam.tokens.contentEquals(example.target)) ChipMark.RESULT else ChipMark.DIM),
            Chip(example.text, "gold", ChipMark.ACTIVE),
        ),
        rows = listOf(
            "log P(greedy)" to "%.4f".format(greedy.logProbability),
            "log P(beam $width)" to "%.4f".format(beam.logProbability),
            "log P(gold)" to "%.4f".format(model.sequenceLogProbability(example.source, example.target)),
        ),
    )

    val sweep = Seq2SeqLab.beamSweep(model, listOf(1, 2, 3, 5, 10))
    frames += TokenFrame(
        status = "Across the held-out set, widening the beam does exactly what it promises: mean log-probability " +
            "rises from ${"%.3f".format(sweep.first().meanLogProbability)} at width 1 to " +
            "${"%.3f".format(sweep.last().meanLogProbability)} at width 10, and " +
            "${sweep.last().changed} of ${Seq2SeqLab.testSet.size} outputs change. Exact match moves from " +
            "${"%.3f".format(sweep.first().exactMatch)} to ${"%.3f".format(sweep.last().exactMatch)} — one " +
            "sequence.",
        bars = listOf(
            BarRow("mean log-probability", sweep.map { it.meanLogProbability.toFloat() }, BarNegative, sweep.map { "k${it.width}" }),
            BarRow("exact match", sweep.map { it.exactMatch.toFloat() }, ChipResult, sweep.map { "k${it.width}" }),
        ),
        readout = "more probable, not more correct",
    )

    val narrow = Seq2SeqLab.errorSplit(model, 1)
    val wide = Seq2SeqLab.errorSplit(model, 10)
    frames += TokenFrame(
        status = "Which is worth taking apart rather than shrugging at. Score the gold sequence under the same " +
            "model and every wrong output falls into one of two kinds: the model preferred gold and the search " +
            "lost it, or the model preferred its own answer. At width 1 that split is " +
            "${narrow.searchError} search errors against ${narrow.modelError} model errors. At width 10 the search " +
            "errors are ${wide.searchError}.",
        bars = listOf(
            BarRow(
                "width 1",
                listOf(narrow.correct.toFloat(), narrow.searchError.toFloat(), narrow.modelError.toFloat()),
                BarPositive,
                listOf("correct", "search", "model"),
            ),
            BarRow(
                "width 10",
                listOf(wide.correct.toFloat(), wide.searchError.toFloat(), wide.modelError.toFloat()),
                BarNegative,
                listOf("correct", "search", "model"),
            ),
        ),
        readout = "${wide.modelError} of ${Seq2SeqLab.testSet.size} are beyond any beam width",
    )

    val reversedModel = Seq2SeqLab.reverseFed
    val reversedSplit = Seq2SeqLab.errorSplit(reversedModel, width)
    frames += TokenFrame(
        status = "And that is the argument for where to spend. Beam width, taken to 10, bought " +
            "${wide.correct - narrow.correct} sequence${if (wide.correct - narrow.correct == 1) "" else "s"}. " +
            "Feeding the encoder the source backwards — no extra parameters, no extra decoding cost — bought " +
            "${reversedSplit.correct - wide.correct}, by converting model errors rather than search errors.",
        bars = listOf(
            BarRow(
                "correct sequences",
                listOf(narrow.correct.toFloat(), wide.correct.toFloat(), reversedSplit.correct.toFloat()),
                ChipResult,
                listOf("greedy", "beam 10", "reversed + beam $width"),
            ),
            BarRow(
                "model errors",
                listOf(narrow.modelError.toFloat(), wide.modelError.toFloat(), reversedSplit.modelError.toFloat()),
                BarNegative,
                listOf("greedy", "beam 10", "reversed + beam $width"),
            ),
        ),
        readout = "fix the model, then widen the search",
    )

    val effect = Seq2SeqLab.normalizationEffect(model, width)
    frames += TokenFrame(
        status = "One more knob, reported as it measured rather than as it is usually described. A beam scored by " +
            "summed log-probability prefers short sequences, and dividing by length is the standard repair. Here " +
            "it changes ${effect.changed} of ${Seq2SeqLab.testSet.size} outputs and lengthens them " +
            "(${"%.2f".format(effect.plainLength)} → ${"%.2f".format(effect.normalizedLength)} symbols) while " +
            "moving exact match by ${"%+.3f".format(effect.exactMatchDelta)}. On this task the source fixes the " +
            "length, so there is nothing for it to fix — it earns its keep in translation and summarisation, where " +
            "the model chooses when to stop.",
        rows = listOf(
            "outputs changed" to "${effect.changed} of ${Seq2SeqLab.testSet.size}",
            "mean length" to "${"%.2f".format(effect.plainLength)} → ${"%.2f".format(effect.normalizedLength)}",
            "exact match" to "%+.3f".format(effect.exactMatchDelta),
            "where it matters" to "open-ended output, not fixed-length output",
        ),
    )

    val teacher = model.teacherForcedAccuracy(Seq2SeqLab.testSet)
    val free = model.freeRunningAccuracy(Seq2SeqLab.testSet)
    frames += TokenFrame(
        status = "Finally, the gap training hides. Scored with the gold prefix handed to it at every step — which " +
            "is how it was trained — the model gets ${"%.1f".format(teacher * 100)}% of next symbols right. " +
            "Reading its own output, as it must at inference, ${"%.1f".format(free * 100)}%. Same weights, same " +
            "data: one wrong symbol puts the state somewhere training never visited.",
        bars = listOf(
            BarRow(
                "next-symbol accuracy",
                listOf(teacher.toFloat(), free.toFloat()),
                ChipResult,
                listOf("teacher-forced", "free-running"),
            ),
        ),
        readout = "exposure bias: ${"%.3f".format(teacher - free)} on this model",
    )
    return frames
}

// ── C6 · Self- and cross-attention ───────────────────────────────────────────
// The centrepiece is the model C5's encoder-decoder topic ended by pointing at: the same task, the
// same data and the same budget, with cross-attention instead of a single context vector — plus a
// fixed-vector model widened until its parameter count matches, so the result cannot be explained
// by size.

private fun selfCrossAttentionFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val sentence = MaskLab.sentence
    val n = sentence.size

    frames += TokenFrame(
        status = "Attention is one operation: score every query against every key, softmax the scores, and return " +
            "that weighted mixture of the values. What changes between its two uses is only where the queries and " +
            "the keys come from.",
        chips = sentence.map { Chip(it) },
        chipsLabel = "one sequence",
        rows = listOf(
            "self-attention" to "Q, K, V all from the same sequence",
            "cross-attention" to "Q from the target, K and V from the source",
            "the operation" to "softmax(QKᵀ/√d)·V — identical in both",
        ),
    )

    frames += TokenFrame(
        status = "Self-attention over ${n} tokens scores ${MaskLab.pairs(n)} pairs — every position against every " +
            "position, including itself. Cross-attention scores ${MaskLab.crossPairs(4, MaskLab.source.size)} for a " +
            "${MaskLab.source.size}-token source and a 4-token target: the two sides do not have to be the same " +
            "length, which is exactly what a translation model needs.",
        chips = MaskLab.source.map { Chip(it, "source") },
        chipsLabel = "the other sequence, in cross-attention",
        rows = listOf(
            "self, ${n} tokens" to "${MaskLab.pairs(n)} scores",
            "cross, ${MaskLab.source.size} → 4" to "${MaskLab.crossPairs(4, MaskLab.source.size)} scores",
            "causal self" to "${MaskLab.causalPairs(n)} scores — half the matrix is masked away",
        ),
    )

    val comparison = AttentionLab.comparison
    val fixed = comparison.first { it.name == "fixed vector" }
    val matched = comparison.first { it.name == "fixed vector, widened to match" }
    val attention = comparison.first { it.name == "cross-attention" }
    frames += TokenFrame(
        status = "The encoder-decoder topic ends on the claim that attention removes the fixed-vector bottleneck. " +
            "Here is that claim on the same task, the same 120 training pairs and the same 100 epochs: exact match " +
            "${"%.3f".format(fixed.exactMatch)} with one context vector, ${"%.3f".format(attention.exactMatch)} " +
            "with cross-attention.",
        bars = listOf(
            BarRow(
                "exact match",
                comparison.map { it.exactMatch.toFloat() },
                ChipResult,
                listOf("fixed", "reversed", "widened", "attention"),
            ),
        ),
        rows = comparison.map { it.name to "${"%.3f".format(it.exactMatch)} · ${it.parameters} parameters" },
        readout = "${"%.3f".format(fixed.exactMatch)} → ${"%.3f".format(attention.exactMatch)}",
    )

    frames += TokenFrame(
        status = "And it is not the parameter count. Widening the fixed-vector model to " +
            "${matched.parameters} parameters — within ${attention.parameters - matched.parameters} of the " +
            "attention model — scores ${"%.3f".format(matched.exactMatch)}, barely above the narrow one. The " +
            "collapse with length is still there: " +
            matched.byLength.joinToString(", ") { "%.2f".format(it.second) } + ".",
        bars = listOf(
            BarRow("widened fixed vector, by length", matched.byLength.map { it.second.toFloat() }, BarNegative, (1..6).map { "L$it" }),
            BarRow("cross-attention, by length", attention.byLength.map { it.second.toFloat() }, BarPositive, (1..6).map { "L$it" }),
        ),
        readout = "capacity was never the missing thing",
    )

    val model = AttentionLab.single
    val example = Seq2SeqLab.testSet.first { it.source.size == 5 && model.decode(it.source).tokens.contentEquals(it.target) }
    val alignment = model.alignmentMatrix(example.source)
    frames += TokenFrame(
        status = "What the decoder learned to do is visible. Each row is one decoder step and each column a source " +
            "position; the mass sits on the diagonal because the task is to copy, and nobody told it that — the " +
            "alignment is what the gradient found.",
        heat = Heat(
            alignment.indices.map { "step ${it + 1}" },
            example.source.map { Seq2SeqLab.symbolNames[it] },
            alignment.map { row -> row.map { it.toFloat() } },
        ),
        readout = "${example.text} → ${model.decode(example.source).text}",
    )

    frames += TokenFrame(
        status = "Scored over the whole held-out set rather than one picture: the attention mass landing on the " +
            "position the step should be reading is ${"%.3f".format(model.diagonalMass())}, and the average " +
            "distance between where the mass sits and where it belongs is " +
            "${"%.3f".format(model.diagonalOffset())} positions.",
        bars = listOf(
            BarRow("mass on the right position", listOf(model.diagonalMass().toFloat()), ChipResult, listOf("mean")),
            BarRow("offset, in positions", listOf(model.diagonalOffset().toFloat()), BarNegative, listOf("mean")),
        ),
        readout = "the alignment is learned, not supplied",
    )

    frames += TokenFrame(
        status = "The gradients through this attention block are derived by hand, so they are checked against " +
            "finite differences rather than trusted: the worst relative disagreement over sampled coordinates in " +
            "every parameter is ${"%.1e".format(AttentionLab.single.gradientCheck())}. The first run of that check " +
            "reported a uniform 1 − 1/T error, which was the loss being averaged on one side and summed on the " +
            "other — a scale bug, not a derivation bug, and worth being able to tell apart.",
        rows = listOf(
            "analytic vs numeric" to "%.1e".format(AttentionLab.single.gradientCheck()),
            "what a derivation bug looks like" to "one parameter block wrong, the rest exact",
            "what a scale bug looks like" to "every block wrong by the same factor",
        ),
    )

    frames += TokenFrame(
        status = "One thing the frames above do not show: cost. Self-attention over n tokens is n² scores and the " +
            "whole matrix has to exist, so doubling the sequence quadruples the work — the price paid for the " +
            "single-step path between any two positions that the recurrent models could not offer.",
        bars = listOf(
            BarRow(
                "pairwise scores",
                listOf(8f, 16f, 32f, 64f).map { it * it },
                BarNegative,
                listOf("n=8", "n=16", "n=32", "n=64"),
            ),
        ),
        rows = listOf(
            "recurrent path length" to "O(n) steps between distant tokens",
            "attention path length" to "1 step, at O(n²) cost",
        ),
    )
    return frames
}

// ── C6 · Multi-head attention ────────────────────────────────────────────────

private fun multiHeadFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val d = MultiHeadLab.MODEL_DIM

    frames += TokenFrame(
        status = "Multi-head attention splits the d-dimensional space into h slices and runs the same operation " +
            "in each, then concatenates. The first thing to be clear about is what it costs: nothing. Q, K, V and " +
            "the output projection are four d×d matrices at every head count — ${MultiHeadLab.attentionParameters()} " +
            "parameters at d = $d, whether h is 1 or 16.",
        bars = listOf(
            BarRow(
                "attention parameters",
                MultiHeadLab.headCounts.map { MultiHeadLab.attentionParameters().toFloat() },
                ChipResult,
                MultiHeadLab.headCounts.map { "h=$it" },
            ),
            BarRow(
                "dimensions per head",
                MultiHeadLab.headCounts.map { MultiHeadLab.headDim(it).toFloat() },
                BarPositive,
                MultiHeadLab.headCounts.map { "h=$it" },
            ),
        ),
        readout = "same parameters, differently partitioned",
    )

    val read = MultiHeadLab.simultaneousRead()
    frames += TokenFrame(
        status = "So what does the split buy? Not rank, which is the usual story — a single head at d = $d can " +
            "represent any pattern this length. What one head cannot do is read two places at once: it emits *one* " +
            "distribution, so attending to two positions means splitting the mass between them and receiving a " +
            "blend of both values.",
        rows = listOf(
            "target" to "position a into one half of the output, position b into the other",
            "best single head" to "α = ${"%.2f".format(read.bestAlpha)}, error ${"%.3f".format(read.singleHeadError)}",
            "two heads" to "error ${"%.3f".format(read.twoHeadError)} — exact, by construction",
        ),
        bars = listOf(
            BarRow(
                "reconstruction error",
                listOf(read.singleHeadError.toFloat(), read.twoHeadError.toFloat()),
                BarNegative,
                listOf("1 head", "2 heads"),
            ),
        ),
        readout = "measured over 200 random value pairs",
    )

    frames += TokenFrame(
        status = "The rank argument does bite, but at the other end. One head's score matrix is Q·Kᵀ with an inner " +
            "dimension of d/h, so it cannot have rank above d/h however it is trained. Four alignment patterns " +
            "summed need rank ${MultiHeadLab.combinedRank()}; at h = 8 each head has ${MultiHeadLab.headDim(8)} " +
            "dimensions and the best it can do is " +
            "${"%.1f".format(MultiHeadLab.rankError(MultiHeadLab.combinedPatterns(), MultiHeadLab.headDim(8)) * 100)}% " +
            "error. Too many heads is a real failure mode, not a hypothetical one.",
        bars = listOf(
            BarRow(
                "error of the best rank-(d/h) fit",
                MultiHeadLab.headCounts.map {
                    MultiHeadLab.rankError(MultiHeadLab.combinedPatterns(), MultiHeadLab.headDim(it)).toFloat()
                },
                BarNegative,
                MultiHeadLab.headCounts.map { "h=$it" },
            ),
        ),
        readout = "heads get thinner as they get more numerous",
    )

    val single = AttentionLab.single
    val multi = AttentionLab.multi
    frames += TokenFrame(
        status = "On the copy task from the previous topic, trained identically at 1 head and at 4: exact match " +
            "${"%.3f".format(single.exactMatch())} and ${"%.3f".format(multi.exactMatch())}. Four heads is slightly " +
            "*worse*, and that is the honest result — this task has one alignment to learn, so there is nothing for " +
            "the other three heads to do but split the width.",
        bars = listOf(
            BarRow(
                "exact match",
                listOf(single.exactMatch().toFloat(), multi.exactMatch().toFloat()),
                ChipResult,
                listOf("1 head", "4 heads"),
            ),
        ),
        rows = listOf(
            "parameters" to "${single.parameterCount} both",
            "dimensions per head" to "${AttentionLab.HIDDEN} → ${AttentionLab.HIDDEN / 4}",
        ),
        readout = "heads help when there is more than one thing to attend to",
    )

    frames += TokenFrame(
        status = "The four heads did not collapse into copies of each other, though. Measured on the held-out set, " +
            "the mass each head puts on the position the step is copying ranges from " +
            "${"%.2f".format((0..3).minOf { multi.diagonalMass(it) })} to " +
            "${"%.2f".format((0..3).maxOf { multi.diagonalMass(it) })}: one head carries the alignment and the " +
            "others drift off it. Head specialisation is real; it is just not free accuracy.",
        bars = listOf(
            BarRow(
                "mass on the copied position",
                (0..3).map { multi.diagonalMass(it).toFloat() },
                BarPositive,
                (0..3).map { "head $it" },
            ),
            BarRow(
                "offset, in positions",
                (0..3).map { multi.diagonalOffset(it).toFloat() },
                BarNegative,
                (0..3).map { "head $it" },
            ),
        ),
        readout = "one head aligned, three doing something else",
    )

    frames += TokenFrame(
        status = "Which is the rule of thumb worth keeping: the head count is a partition of a fixed budget. More " +
            "heads means more simultaneous reads and thinner ones; the published transformers sit at 64 dimensions " +
            "per head almost regardless of size, which is what the two measurements above jointly recommend.",
        rows = listOf(
            "BERT-base" to "d 768, 12 heads → 64 per head",
            "GPT-2 small" to "d 768, 12 heads → 64 per head",
            "GPT-3 175B" to "d 12288, 96 heads → 128 per head",
            "the trade" to "simultaneous reads against expressiveness per read",
        ),
    )
    return frames
}

// ── C6 · BERT ────────────────────────────────────────────────────────────────

private fun bertFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val sentence = PretrainCorpus.tokens[0]
    val masked = PretrainLab.maskSentence(sentence)

    frames += TokenFrame(
        status = "BERT is a transformer encoder trained by filling in blanks. Every position sees every other one " +
            "— no causal mask anywhere — which is only possible because it is never asked to continue text, only " +
            "to reconstruct it.",
        chips = masked.map { (token, kind) ->
            Chip(token, kind, if (kind == "kept") ChipMark.IDLE else ChipMark.ACTIVE)
        },
        chipsLabel = "one masked input",
        rows = listOf(
            "objective" to "predict the tokens at the selected positions",
            "context per prediction" to "every other token in the sequence",
            "what it cannot do" to "generate — there is no next-token objective",
        ),
    )

    frames += TokenFrame(
        status = "Selection is 15% of positions, and what happens to a selected position is 80/10/10: replaced " +
            "with [MASK], replaced with a random token, or left exactly as it was. On this ${PretrainCorpus.tokenCount}-token " +
            "corpus that is ${PretrainLab.corruption.masked} masked, ${PretrainLab.corruption.randomised} " +
            "randomised and ${PretrainLab.corruption.kept} untouched — all ${PretrainLab.maskedTargets} of them " +
            "still scored.",
        bars = listOf(
            BarRow(
                "positions",
                listOf(
                    PretrainLab.corruption.masked.toFloat(),
                    PretrainLab.corruption.randomised.toFloat(),
                    PretrainLab.corruption.kept.toFloat(),
                ),
                ChipResult,
                listOf("[MASK] 80%", "random 10%", "kept 10%"),
            ),
        ),
        readout = "${PretrainLab.maskedTargets} targets from ${PretrainCorpus.tokenCount} tokens",
    )

    frames += TokenFrame(
        status = "The 10% random and 10% unchanged branches exist because of a mismatch that is easy to state as a " +
            "number: [MASK] appears on ${"%.0f".format(PretrainLab.maskTokenShare * 100)}% of positions during " +
            "pre-training and on 0% of them during fine-tuning. A model allowed to key off the token itself would " +
            "learn a feature that disappears the moment it is used.",
        bars = listOf(
            BarRow(
                "[MASK] share of input positions",
                listOf((PretrainLab.maskTokenShare * 100).toFloat(), 0f),
                BarNegative,
                listOf("pre-training", "fine-tuning"),
            ),
        ),
        rows = listOf(
            "why 10% random" to "the model cannot trust an unmasked token either",
            "why 10% unchanged" to "it must keep a good representation of every position",
        ),
    )

    frames += TokenFrame(
        status = "What bidirectionality buys, counted on the same corpus: a causal model's prediction at position " +
            "i sees i tokens, averaging ${"%.2f".format(PretrainLab.causalContext)} tokens of context per " +
            "prediction. A masked model's prediction sees every other position — " +
            "${"%.2f".format(PretrainLab.maskedContext)}, exactly twice as much.",
        bars = listOf(
            BarRow(
                "context tokens per prediction",
                listOf(PretrainLab.causalContext.toFloat(), PretrainLab.maskedContext.toFloat()),
                BarPositive,
                listOf("causal", "masked"),
            ),
        ),
        readout = "2× the context per prediction",
    )

    frames += TokenFrame(
        status = "And what it costs, counted the same way: a causal objective turns every one of the " +
            "${PretrainLab.causalTargets} tokens into a target, and masking at 15% turns " +
            "${PretrainLab.maskedTargets} of them into targets. Same forward pass, " +
            "${"%.1f".format(PretrainLab.signalRatio)}× less signal — which is why BERT-style models need more " +
            "passes over the data than their parameter count suggests.",
        bars = listOf(
            BarRow(
                "targets per pass over the corpus",
                listOf(PretrainLab.causalTargets.toFloat(), PretrainLab.maskedTargets.toFloat()),
                ChipResult,
                listOf("causal", "masked 15%"),
            ),
        ),
        readout = "${"%.1f".format(PretrainLab.signalRatio)}× fewer targets per pass",
    )

    val config = ModelTableLab.bertBase
    val breakdown = ModelTableLab.breakdown(config)
    frames += TokenFrame(
        status = "BERT-base's size, summed from its own config rather than quoted: " +
            "${breakdown.total} parameters. ${config.layers} layers of " +
            "${ModelTableLab.encoderLayer(config)} each, an embedding table of ${breakdown.embeddings}, and a " +
            "${breakdown.head}-parameter pooler. The paper's \"110M\" is this number rounded.",
        bars = listOf(
            BarRow(
                "parameters (millions)",
                listOf(
                    (breakdown.embeddings / 1e6).toFloat(),
                    (breakdown.encoder / 1e6).toFloat(),
                    (breakdown.head / 1e6).toFloat(),
                ),
                BarPositive,
                listOf("embeddings", "12 layers", "pooler"),
            ),
        ),
        rows = listOf(
            "attention block per layer" to "${ModelTableLab.attentionBlock(config)}",
            "feed-forward per layer" to "${ModelTableLab.ffnBlock(config)}",
            "embeddings' share" to "${"%.1f".format(ModelTableLab.embeddingShare(config) * 100)}%",
            "total" to "${breakdown.total}",
        ),
        readout = "109,482,240 — the \"110M\" in full",
    )

    frames += TokenFrame(
        status = "The shape of that table is the argument for the encoder-only design. Two thirds of a layer is " +
            "the feed-forward block, not attention, and a fifth of the whole model is the embedding table — which " +
            "is where RoBERTa's larger vocabulary and DistilBERT's layer cut both land.",
        rows = listOf(
            "attention" to "${"%.0f".format(ModelTableLab.attentionBlock(config) * 100.0 / ModelTableLab.encoderLayer(config))}% of a layer",
            "feed-forward" to "${"%.0f".format(ModelTableLab.ffnBlock(config) * 100.0 / ModelTableLab.encoderLayer(config))}% of a layer",
            "use it for" to "classification, tagging, retrieval, reranking",
            "do not use it for" to "generation of any kind",
        ),
    )
    return frames
}

// ── C6 · GPT ─────────────────────────────────────────────────────────────────

private fun gptFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val sentence = MaskLab.sentence
    val n = sentence.size
    val causal = List(n) { i -> List(n) { j -> if (j <= i) 1f / (i + 1) else 0f } }

    frames += TokenFrame(
        status = "GPT is the same transformer block with one line added to the attention: positions to the right " +
            "of the query are set to −∞ before the softmax. That single mask is the whole difference between a " +
            "model that reconstructs text and one that continues it.",
        chips = sentence.mapIndexed { index, token ->
            Chip(token, if (index <= 2) "visible" else "masked", if (index <= 2) ChipMark.IDLE else ChipMark.DIM)
        },
        chipsLabel = "what position 3 can see",
        readout = "causal mask: attend to j ≤ i only",
    )

    frames += TokenFrame(
        status = "Drawn as a matrix it is exactly triangular. Bidirectional attention over $n tokens scores " +
            "${MaskLab.pairs(n)} pairs; the causal version scores ${MaskLab.causalPairs(n)} — a little over half " +
            "— and the first position attends only to itself, which is why the first token of a sequence carries " +
            "no information about anything.",
        heat = Heat(sentence, sentence, causal, focusRow = 3),
        readout = "${MaskLab.causalPairs(n)} of ${MaskLab.pairs(n)} pairs survive the mask",
    )

    frames += TokenFrame(
        status = "The payoff is that every position is a training target. The same forward pass that gives a " +
            "masked model ${PretrainLab.maskedTargets} predictions on this corpus gives a causal model " +
            "${PretrainLab.causalTargets} — ${"%.1f".format(PretrainLab.signalRatio)}× more — and the objective is " +
            "exactly the task the model will be asked to do at inference.",
        bars = listOf(
            BarRow(
                "targets per pass",
                listOf(PretrainLab.causalTargets.toFloat(), PretrainLab.maskedTargets.toFloat()),
                ChipResult,
                listOf("causal", "masked"),
            ),
            BarRow(
                "context per prediction",
                listOf(PretrainLab.causalContext.toFloat(), PretrainLab.maskedContext.toFloat()),
                BarPositive,
                listOf("causal", "masked"),
            ),
        ),
        readout = "more targets, each with less context",
    )

    frames += TokenFrame(
        status = "The mask also makes generation cheap in a way a bidirectional model can never be. Because no " +
            "position attends to the right, the keys and values of the tokens already generated never change — " +
            "cache them and each new token costs one row of scores instead of a whole matrix. Over a " +
            "${n}-token generation that is ${MaskLab.scoresWithCache(n)} score computations instead of " +
            "${MaskLab.scoresWithoutCache(n)}.",
        bars = listOf(
            BarRow(
                "score computations over the generation",
                listOf(MaskLab.scoresWithoutCache(n).toFloat(), MaskLab.scoresWithCache(n).toFloat()),
                BarNegative,
                listOf("recompute", "KV cache"),
            ),
        ),
        rows = listOf(
            "cache size" to "2 · layers · d · tokens",
            "GPT-2 small at 1024 tokens" to "${MaskLab.kvCacheValues(12, 768, 1024)} values",
            "why it works" to "nothing to the left ever changes",
        ),
    )

    val config = ModelTableLab.gpt2Small
    val breakdown = ModelTableLab.breakdown(config)
    val published = ModelTableLab.published.getValue(config.name)
    frames += TokenFrame(
        status = "GPT-2 small, summed from its config: ${breakdown.total} parameters. The paper reports 117M. The " +
            "gap is not rounding — it is ${"%.1f".format((breakdown.total - published) * 100.0 / published)}%, and " +
            "the released checkpoint has the larger number. Sum the table before quoting a headline figure.",
        bars = listOf(
            BarRow(
                "parameters (millions)",
                listOf((breakdown.total / 1e6).toFloat(), (published / 1e6).toFloat()),
                ChipResult,
                listOf("summed", "reported"),
            ),
        ),
        rows = listOf(
            "embeddings" to "${breakdown.embeddings} (${"%.1f".format(ModelTableLab.embeddingShare(config) * 100)}%)",
            "12 layers" to "${breakdown.encoder}",
            "final layer norm" to "${breakdown.head}",
            "summed total" to "${breakdown.total}",
        ),
        readout = "124,439,808 against a reported 117M",
    )

    frames += TokenFrame(
        status = "Note where GPT-2's parameters sit against BERT's. Its vocabulary is byte-level BPE at " +
            "${config.vocab} against BERT's ${ModelTableLab.bertBase.vocab} WordPiece, and its context is " +
            "${config.positions} against ${ModelTableLab.bertBase.positions} — so the embedding table is " +
            "${"%.1f".format(ModelTableLab.embeddingShare(config) * 100)}% of the model rather than " +
            "${"%.1f".format(ModelTableLab.embeddingShare(ModelTableLab.bertBase) * 100)}%. The layers are " +
            "identical in size.",
        bars = listOf(
            BarRow(
                "embedding share of the model",
                listOf(
                    (ModelTableLab.embeddingShare(ModelTableLab.bertBase) * 100).toFloat(),
                    (ModelTableLab.embeddingShare(config) * 100).toFloat(),
                ),
                BarPositive,
                listOf("BERT-base", "GPT-2 small"),
            ),
        ),
        readout = "same layers, different vocabulary bill",
    )
    return frames
}

// ── C6 · T5 ──────────────────────────────────────────────────────────────────

private fun t5Frames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val corrupted = SpanCorruptionLab.corrupt()
    val scaled = SpanCorruptionLab.atScale()

    frames += TokenFrame(
        status = "T5's argument is that every NLP task is text in, text out — so classification, translation, " +
            "summarisation and regression all become one problem with a task prefix on the front, trained by one " +
            "encoder-decoder with one objective.",
        chips = listOf(
            Chip("translate English to German:", "prefix", ChipMark.ACTIVE),
            Chip("that is good", "input"),
        ),
        chipsLabel = "one task, framed as text",
        rows = listOf(
            "classification" to "\"cola sentence: …\" → \"acceptable\"",
            "similarity" to "\"stsb sentence1: … sentence2: …\" → \"3.8\"",
            "why it matters" to "one model, one loss, one decoding path for all of them",
        ),
    )

    frames += TokenFrame(
        status = "The pre-training objective corrupts *spans*, not single tokens. Each contiguous run of dropped " +
            "tokens is replaced by one sentinel in the input, and the target is only the dropped runs, each " +
            "introduced by its sentinel.",
        chips = SpanCorruptionLab.sentence.map { Chip(it) },
        chipsLabel = "original (${SpanCorruptionLab.sentence.size} tokens)",
    )

    frames += TokenFrame(
        status = "On this sentence at the published settings — 15% corrupted, mean span 3 — the input keeps " +
            "${corrupted.input.size} tokens with ${corrupted.spans} sentinel, and the target is " +
            "${corrupted.target.size} tokens: the dropped span, its sentinel, and a final sentinel to stop on.",
        chips = corrupted.input.map { Chip(it, mark = if (it.startsWith("<X")) ChipMark.ACTIVE else ChipMark.IDLE) },
        chipsLabel = "encoder input (${corrupted.input.size})",
        rows = listOf(
            "target" to corrupted.target.joinToString(" "),
            "target length" to "${corrupted.target.size} tokens",
            "corrupted" to "${corrupted.corruptedTokens} tokens in ${corrupted.spans} span",
        ),
    )

    frames += TokenFrame(
        status = "At the length these models actually run, that shape is the point. 512 tokens, 15% corrupted, " +
            "mean span 3: the encoder reads ${scaled.inputLength} tokens and the decoder emits " +
            "${scaled.targetLength}. BERT's objective makes the model produce an output at all " +
            "${scaled.maskedOutputLength} positions — so T5 pays " +
            "${"%.1f".format(scaled.maskedOutputLength.toDouble() / scaled.targetLength)}× fewer decoder steps for " +
            "the same corruption budget.",
        bars = listOf(
            BarRow(
                "output positions per pass",
                listOf(scaled.maskedOutputLength.toFloat(), scaled.targetLength.toFloat()),
                ChipResult,
                listOf("masked LM", "span corruption"),
            ),
        ),
        rows = listOf(
            "tokens" to "${scaled.tokens}",
            "corrupted" to "${scaled.corrupted} in ${scaled.spans} spans",
            "encoder input" to "${scaled.inputLength}",
            "decoder target" to "${scaled.targetLength}",
        ),
        readout = "short targets are the whole economy of it",
    )

    val config = ModelTableLab.t5Base
    val breakdown = ModelTableLab.breakdown(config)
    frames += TokenFrame(
        status = "T5-base summed from its config: ${breakdown.total} parameters, against a reported 220M. Note " +
            "where they are — the decoder is ${breakdown.decoder} against the encoder's ${breakdown.encoder}, " +
            "because every decoder layer carries a cross-attention block the encoder layers do not have.",
        bars = listOf(
            BarRow(
                "parameters (millions)",
                listOf(
                    (breakdown.embeddings / 1e6).toFloat(),
                    (breakdown.encoder / 1e6).toFloat(),
                    (breakdown.decoder / 1e6).toFloat(),
                ),
                BarPositive,
                listOf("embeddings", "encoder", "decoder"),
            ),
        ),
        rows = listOf(
            "encoder layer" to "${ModelTableLab.encoderLayer(config)}",
            "decoder layer" to "${ModelTableLab.decoderLayer(config)} — self, cross, and feed-forward",
            "embeddings' share" to "${"%.1f".format(ModelTableLab.embeddingShare(config) * 100)}%",
            "no biases anywhere" to "T5 drops them; that is ~${config.layers * 4 * config.dim} parameters saved per stack",
        ),
        readout = "the decoder is the expensive half",
    )

    frames += TokenFrame(
        status = "Which is the trade against an encoder-only or decoder-only model of the same width: T5 pays for " +
            "two stacks and cross-attention, and gets a model that can read bidirectionally *and* generate. BERT " +
            "cannot generate; GPT reads only leftwards. This is the architecture that does both, at roughly double " +
            "the layer bill.",
        rows = listOf(
            "BERT-base" to "${ModelTableLab.total(ModelTableLab.bertBase)} — encoder only, no generation",
            "GPT-2 small" to "${ModelTableLab.total(ModelTableLab.gpt2Small)} — decoder only, one direction",
            "T5-base" to "${ModelTableLab.total(config)} — both, at the cost of both",
        ),
    )
    return frames
}

// ── C6 · RoBERTa ─────────────────────────────────────────────────────────────

private fun robertaFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val epochs = listOf(1, 4, 10, PretrainLab.EPOCHS)

    frames += TokenFrame(
        status = "RoBERTa changed no part of BERT's architecture. Same layers, same width, same objective — the " +
            "paper is a list of things the original training run did that turned out to be suboptimal, which makes " +
            "it the most useful kind of ablation study.",
        rows = listOf(
            "architecture" to "unchanged",
            "dynamic masking" to "a fresh mask every epoch",
            "NSP" to "removed",
            "batch size" to "256 → 8,000 sequences",
            "data" to "16GB → 160GB",
            "vocabulary" to "30,522 WordPiece → 50,265 byte-level BPE",
        ),
    )

    frames += TokenFrame(
        status = "Static masking, as BERT did it: duplicate the corpus ${PretrainLab.STATIC_DUPLICATES}× with a " +
            "different mask each time, then train ${PretrainLab.EPOCHS} epochs — so every mask pattern is seen " +
            "${PretrainLab.staticReuse} times and a token that was never selected in those " +
            "${PretrainLab.STATIC_DUPLICATES} draws is never predicted at all.",
        bars = listOf(
            BarRow(
                "distinct masks per sequence",
                listOf(PretrainLab.staticDistinctMasks.toFloat(), PretrainLab.dynamicDistinctMasks.toFloat()),
                ChipResult,
                listOf("static", "dynamic"),
            ),
        ),
        readout = "${PretrainLab.staticDistinctMasks} patterns reused ${PretrainLab.staticReuse}× each",
    )

    frames += TokenFrame(
        status = "How much that leaves untouched is a probability, not an opinion. A token is selected with " +
            "probability 0.15 per masking, so after k independent maskings the chance it was never selected is " +
            "0.85ᵏ: ${"%.1f".format(PretrainLab.neverMasked(PretrainLab.STATIC_DUPLICATES) * 100)}% of tokens are " +
            "never predicted under ${PretrainLab.STATIC_DUPLICATES} static masks, against " +
            "${"%.2f".format(PretrainLab.neverMasked(PretrainLab.EPOCHS) * 100)}% under " +
            "${PretrainLab.EPOCHS} dynamic ones.",
        bars = listOf(
            BarRow(
                "never predicted",
                epochs.map { PretrainLab.neverMasked(it).toFloat() },
                BarNegative,
                epochs.map { "k=$it" },
            ),
        ),
        rows = epochs.map { "after $it maskings" to "${"%.2f".format(PretrainLab.neverMasked(it) * 100)}% never selected" },
        readout = "one in five tokens, versus one in 650",
    )

    val roberta = ModelTableLab.robertaBase
    val bert = ModelTableLab.bertBase
    frames += TokenFrame(
        status = "The vocabulary change is the one that shows up in the parameter count. Byte-level BPE at " +
            "${roberta.vocab} entries against WordPiece at ${bert.vocab} makes the embedding table " +
            "${ModelTableLab.breakdown(roberta).embeddings} against ${ModelTableLab.breakdown(bert).embeddings} — " +
            "${"%.0f".format((ModelTableLab.breakdown(roberta).embeddings.toDouble() / ModelTableLab.breakdown(bert).embeddings - 1) * 100)}% " +
            "more — while the twelve layers are byte-for-byte identical.",
        bars = listOf(
            BarRow(
                "parameters (millions)",
                listOf(
                    (ModelTableLab.breakdown(bert).embeddings / 1e6).toFloat(),
                    (ModelTableLab.breakdown(roberta).embeddings / 1e6).toFloat(),
                ),
                BarPositive,
                listOf("BERT emb", "RoBERTa emb"),
            ),
            BarRow(
                "total (millions)",
                listOf((ModelTableLab.total(bert) / 1e6).toFloat(), (ModelTableLab.total(roberta) / 1e6).toFloat()),
                ChipResult,
                listOf("BERT", "RoBERTa"),
            ),
        ),
        readout = "${ModelTableLab.total(bert)} → ${ModelTableLab.total(roberta)}",
    )

    frames += TokenFrame(
        status = "Byte-level BPE also removes the [UNK] token entirely: every string is representable, because " +
            "the base vocabulary is the 256 bytes. What it costs is fertility — more pieces per word on anything " +
            "unusual — which is the trade the tokenizer topic measures directly.",
        rows = listOf(
            "WordPiece" to "unseen word → [UNK], if no piece matches",
            "byte-level BPE" to "unseen word → several pieces, never [UNK]",
            "the cost" to "longer sequences, and attention is quadratic in length",
        ),
    )

    frames += TokenFrame(
        status = "The lesson RoBERTa is actually about is not any single change on this list. It is that BERT's " +
            "reported numbers were a *training run*, not a ceiling on the architecture — and that a careful " +
            "ablation of the recipe beat a year of architecture search on the same benchmarks.",
        rows = listOf(
            "same architecture" to "every gain came from the recipe",
            "biggest single change" to "more data and more steps",
            "cheapest change" to "dynamic masking — one line",
            "what it retired" to "NSP, which the paper shows was doing nothing",
        ),
    )
    return frames
}

// ── C6 · DistilBERT ──────────────────────────────────────────────────────────

private fun distilBertFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val teacher = DistillLab.teacher
    val softened = DistillLab.soften(teacher, DistillLab.TEMPERATURE)
    val labels = Seq2SeqLab.symbolNames + listOf("EOS")

    frames += TokenFrame(
        status = "Distillation trains a small model on a large one's *distribution* rather than on the labels. The " +
            "distribution below is a real one — a decoding step from a trained model, taken at the step it was " +
            "least certain about — and the point is what a hard label would throw away.",
        bars = listOf(BarRow("teacher distribution", teacher.map { it.toFloat() }, BarPositive, labels)),
        rows = listOf(
            "top choice" to "%.3f".format(teacher.max()),
            "everything else" to "${"%.3f".format(DistillLab.offTopMass(teacher))} of the mass",
            "runner-up / top" to "%.3f".format(DistillLab.runnerUpRatio(teacher)),
        ),
        readout = "a hard label keeps ${"%.0f".format(teacher.max() * 100)}% and discards the rest",
    )

    frames += TokenFrame(
        status = "Temperature is what makes that structure trainable. Dividing the logits by T = " +
            "${DistillLab.TEMPERATURE.toInt()} flattens the distribution — entropy " +
            "${"%.3f".format(DistillLab.entropy(teacher))} → ${"%.3f".format(DistillLab.entropy(softened))} nats, " +
            "top choice ${"%.3f".format(teacher.max())} → ${"%.3f".format(softened.max())} — so the gradient " +
            "carries information about the ranking of the alternatives, not only about the winner.",
        bars = listOf(
            BarRow("T = 1", teacher.map { it.toFloat() }, BarPositive, labels),
            BarRow("T = ${DistillLab.TEMPERATURE.toInt()}", softened.map { it.toFloat() }, ChipResult, labels),
        ),
        rows = listOf(
            "entropy" to "${"%.3f".format(DistillLab.entropy(teacher))} → ${"%.3f".format(DistillLab.entropy(softened))}",
            "off-top mass" to "${"%.3f".format(DistillLab.offTopMass(teacher))} → ${"%.3f".format(DistillLab.offTopMass(softened))}",
            "loss is scaled by T²" to "${DistillLab.gradientScale().toInt()}× — soft-target gradients shrink as 1/T²",
        ),
    )

    val bert = ModelTableLab.bertBase
    val distil = ModelTableLab.distilBert
    frames += TokenFrame(
        status = "DistilBERT's recipe is short: take every second layer of BERT-base, keep the embeddings, drop " +
            "the pooler, and train on the teacher's distributions plus the masked-LM loss plus a cosine term on " +
            "the hidden states. Summed from the config that is ${ModelTableLab.total(distil)} parameters against " +
            "${ModelTableLab.total(bert)} — ${"%.1f".format((1 - DistillLab.sizeRatio) * 100)}% smaller.",
        bars = listOf(
            BarRow(
                "parameters (millions)",
                listOf((ModelTableLab.total(bert) / 1e6).toFloat(), (ModelTableLab.total(distil) / 1e6).toFloat()),
                ChipResult,
                listOf("BERT-base", "DistilBERT"),
            ),
            BarRow(
                "layers",
                listOf(bert.layers.toFloat(), distil.layers.toFloat()),
                BarPositive,
                listOf("BERT-base", "DistilBERT"),
            ),
        ),
        readout = "${ModelTableLab.total(bert)} → ${ModelTableLab.total(distil)}",
    )

    frames += TokenFrame(
        status = "Half the layers, but nothing like half the parameters — because the embedding table does not " +
            "shrink. It is ${ModelTableLab.breakdown(distil).embeddings} parameters in both models, which is " +
            "${"%.0f".format(ModelTableLab.embeddingShare(distil) * 100)}% of DistilBERT against " +
            "${"%.0f".format(ModelTableLab.embeddingShare(bert) * 100)}% of BERT. Depth is what compression " +
            "reaches; vocabulary is what it cannot.",
        bars = listOf(
            BarRow(
                "embedding share",
                listOf(
                    (ModelTableLab.embeddingShare(bert) * 100).toFloat(),
                    (ModelTableLab.embeddingShare(distil) * 100).toFloat(),
                ),
                BarNegative,
                listOf("BERT-base", "DistilBERT"),
            ),
        ),
        rows = listOf(
            "layers removed" to "${bert.layers - distil.layers} of ${bert.layers}",
            "layer parameters saved" to "${ModelTableLab.breakdown(bert).encoder - ModelTableLab.breakdown(distil).encoder}",
            "embedding parameters saved" to "0",
        ),
        readout = "50% of the layers, ${"%.0f".format((1 - DistillLab.sizeRatio) * 100)}% of the size",
    )

    frames += TokenFrame(
        status = "The published result is 97% of BERT's GLUE score at 60% of the inference time, and the shape of " +
            "the table above is why that is believable: the layers are where the compute goes, and the layers are " +
            "what was halved. It is also why the same trick applied to the vocabulary would not have worked.",
        rows = listOf(
            "reported GLUE retention" to "97%",
            "reported speed-up" to "60% faster inference",
            "what was halved" to "layers — the compute",
            "what was kept" to "embeddings — the memory",
            "the general rule" to "distil depth, not width, when the table looks like this",
        ),
    )
    return frames
}

// ── C6 · Hugging Face tokenizers ─────────────────────────────────────────────

private fun tokenizerFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val fertilities = TokenizerLab.fertilities
    val word = "gardener"
    val unseen = "unbelievable"

    frames += TokenFrame(
        status = "Three algorithms, one library, one corpus. All three are trained here on the same " +
            "${TokenizerLab.corpus.size} sentences and compared on held-out text — because the choice between them " +
            "is usually made by copying whatever the model card said, and it has measurable consequences.",
        chips = TokenizerLab.heldOut.map { Chip(it) },
        chipsLabel = "held-out text",
        rows = listOf(
            "BPE" to "merge the most frequent adjacent pair, repeatedly",
            "WordPiece" to "merge the pair that most increases corpus likelihood",
            "Unigram" to "start large, prune the pieces you can most afford to lose",
        ),
    )

    frames += TokenFrame(
        status = "Fertility — pieces per word — is the number that matters, because sequence length is attention " +
            "cost. On the held-out sentence: " +
            fertilities.filter { !it.name.contains("floor") }
                .joinToString(", ") { "${it.name} ${"%.3f".format(it.perWord)}" } + ".",
        bars = listOf(
            BarRow(
                "pieces per word",
                fertilities.filter { !it.name.contains("floor") }.map { it.perWord.toFloat() },
                ChipResult,
                fertilities.filter { !it.name.contains("floor") }.map { it.name },
            ),
        ),
        rows = fertilities.filter { !it.name.contains("floor") }
            .map { it.name to "${it.pieces} pieces from ${it.words} words, vocabulary ${it.vocabulary}" },
        readout = "Unigram is shortest here, at the same vocabulary budget",
    )

    frames += TokenFrame(
        status = "Where they disagree is more instructive than the average. \"$word\" is a word the corpus " +
            "contains, and the three still split it differently — BPE replays its merge list, WordPiece takes the " +
            "longest match from the left, and Unigram searches for the most probable segmentation, which is the " +
            "only one of the three that can revise an early choice.",
        rows = TokenizerLab.segmentations(word).map { (name, pieces) -> name to pieces.joinToString(" · ") },
        chips = TokenizerLab.unigramEncode(word).map { Chip(it, "unigram") },
        chipsLabel = "\"$word\"",
    )

    frames += TokenFrame(
        status = "On a word the corpus never contained the difference is not cosmetic. BPE falls back to " +
            "characters, Unigram to its own smallest pieces — and WordPiece emits [UNK], losing the word " +
            "outright, because greedy longest-match has no fallback when no piece matches.",
        rows = TokenizerLab.segmentations(unseen).map { (name, pieces) ->
            name to pieces.joinToString(" · ").take(70)
        },
        readout = "this is why byte-level BPE has no [UNK] at all",
    )

    val floored = fertilities.first { it.name == "WordPiece" }
    val unfloored = fertilities.first { it.name.contains("floor") }
    frames += TokenFrame(
        status = "One measured detail that explains a hyper-parameter nobody reads. WordPiece scores a pair by " +
            "freq(ab)/(freq(a)·freq(b)), which is maximal — exactly 1 — when both halves occur once. Without a " +
            "minimum-frequency floor the trainer spends its whole budget on one-off letter pairs: fertility " +
            "${"%.3f".format(unfloored.perWord)} pieces per word, worse than characters. With the floor at " +
            "${TokenizerLab.MIN_PAIR_FREQUENCY}, ${"%.3f".format(floored.perWord)}.",
        bars = listOf(
            BarRow(
                "pieces per word",
                listOf(unfloored.perWord.toFloat(), floored.perWord.toFloat()),
                BarNegative,
                listOf("no floor", "floor ${TokenizerLab.MIN_PAIR_FREQUENCY}"),
            ),
        ),
        rows = listOf(
            "no floor" to "vocabulary ${unfloored.vocabulary}, fertility ${"%.3f".format(unfloored.perWord)}",
            "floor ${TokenizerLab.MIN_PAIR_FREQUENCY}" to "vocabulary ${floored.vocabulary}, fertility ${"%.3f".format(floored.perWord)}",
            "the real fix" to "billions of tokens, where singleton pairs are rare",
        ),
        readout = "the floor is not a detail on a small corpus",
    )

    val bpePieces = fertilities.first { it.name == "BPE" }.pieces
    val unigramPieces = fertilities.first { it.name == "Unigram" }.pieces
    frames += TokenFrame(
        status = "What fertility costs, in the only currency a transformer has: attention is quadratic in " +
            "sequence length, so ${bpePieces} pieces cost ${TokenizerLab.attentionCost(bpePieces)} pairwise scores " +
            "and ${unigramPieces} cost ${TokenizerLab.attentionCost(unigramPieces)}. A 10% fertility difference is " +
            "a 20% attention bill on every sequence, for the life of the model.",
        bars = listOf(
            BarRow(
                "pairwise scores",
                listOf(TokenizerLab.attentionCost(bpePieces).toFloat(), TokenizerLab.attentionCost(unigramPieces).toFloat()),
                BarNegative,
                listOf("BPE", "Unigram"),
            ),
        ),
        rows = listOf(
            "why it is permanent" to "the tokenizer is fixed before pre-training starts",
            "what a bad choice costs" to "every sequence, every forward pass, forever",
        ),
    )
    return frames
}

// ── B8 · One-hot encoding ────────────────────────────────────────────────────
// The one preprocessing topic whose subject is a *matrix* rather than a column, which is what this
// widget's heat grid draws. Numbers from PreprocessMath.kt's EncodingLab, which fits the same data
// under both encodings and scores them.

private fun oneHotFrames(): List<TokenFrame> {
    val frames = mutableListOf<TokenFrame>()
    val rows = EncodingLab.rows.take(6)
    val categories = EncodingLab.categories

    fun matrix(dropFirst: Boolean) = rows.map { row ->
        EncodingLab.oneHotEncoded(row, dropFirst).map { it.toFloat() }
    }

    frames += TokenFrame(
        status = "One-hot encoding turns one categorical column into ${categories.size} binary ones — a column per " +
            "level, a single 1 per row. Six rows of the colour column, and the matrix they become.",
        chips = rows.map { Chip(it.category) },
        chipsLabel = "the categorical column",
        heat = Heat(rows.indices.map { "row ${it + 1}" }, categories, matrix(dropFirst = false)),
    )

    frames += TokenFrame(
        status = "What that fixes is the geometry. A label code puts \"red\" " +
            "${EncodingLab.codeDistance("red", "yellow").toInt()} away from \"yellow\" and " +
            "${EncodingLab.codeDistance("red", "green").toInt()} from \"green\", inventing an order and a spacing. " +
            "One-hot puts every pair at exactly √2 = ${"%.3f".format(EncodingLab.oneHotDistance())} apart, which is " +
            "the truth about unordered categories.",
        rows = listOf(
            "label code: red → green" to "${EncodingLab.codeDistance("red", "green").toInt()}",
            "label code: red → yellow" to "${EncodingLab.codeDistance("red", "yellow").toInt()}",
            "one-hot: any pair" to "%.3f".format(EncodingLab.oneHotDistance()),
        ),
        bars = listOf(
            BarRow(
                "distance from \"red\"",
                categories.map { EncodingLab.codeDistance("red", it).toFloat() },
                BarNegative,
                categories,
            ),
            BarRow(
                "one-hot distance from \"red\"",
                categories.map { if (it == "red") 0f else EncodingLab.oneHotDistance().toFloat() },
                BarPositive,
                categories,
            ),
        ),
    )

    val fits = EncodingLab.linearFits
    val label = fits.first { it.name == "label encoding" }
    val oneHot = fits.first { it.name == "one-hot" }
    frames += TokenFrame(
        status = "Scored, on data whose true effect per category is non-monotone in the code order: a " +
            "least-squares fit reaches MSE ${"%.3f".format(oneHot.error)} on the one-hot matrix and " +
            "${"%.2f".format(label.error)} on the label code — ${"%.0f".format(label.error / oneHot.error)}× worse. " +
            "The one-hot fit can give each level its own coefficient; the label fit has to pass one straight line " +
            "through all four.",
        bars = listOf(
            BarRow(
                "mean squared error",
                fits.map { it.error.toFloat() },
                ChipResult,
                listOf("label", "one-hot", "one-hot −1"),
            ),
        ),
        rows = fits.map { it.name to "MSE ${"%.3f".format(it.error)} · ${it.columns} column${if (it.columns == 1) "" else "s"}" },
        readout = "${"%.0f".format(label.error / oneHot.error)}× the error, from the encoding alone",
    )

    val dropped = fits.first { it.name.contains("dropped") }
    frames += TokenFrame(
        status = "The full matrix has a defect worth knowing: its ${categories.size} columns always sum to 1, so " +
            "they are collinear with the intercept and the coefficients are not identifiable — any constant can be " +
            "moved from the intercept into all four. Dropping one level fixes it and costs nothing: MSE " +
            "${"%.3f".format(dropped.error)} against ${"%.3f".format(oneHot.error)}, on ${dropped.columns} columns " +
            "instead of ${oneHot.columns}.",
        heat = Heat(rows.indices.map { "row ${it + 1}" }, categories.drop(1), matrix(dropFirst = true)),
        rows = listOf(
            "full matrix" to "rows sum to 1 — collinear with the intercept",
            "first level dropped" to "the dropped level is the baseline the others are measured against",
            "MSE" to "${"%.3f".format(oneHot.error)} → ${"%.3f".format(dropped.error)}",
        ),
        readout = "drop one level for linear models; keep all for trees and regularised fits",
    )

    val levels = listOf(4, 12, 50, 5_000)
    frames += TokenFrame(
        status = "What it costs is width, and the cost is linear in the number of levels. Four colours is four " +
            "columns; a postcode column with 5,000 levels is 5,000, most of them almost always zero. That is where " +
            "one-hot stops being the answer — target encoding, hashing or a learned embedding replace it, and each " +
            "one trades the honesty of this matrix for width.",
        bars = listOf(
            BarRow("columns added", levels.map { it.toFloat() }, BarNegative, levels.map { "$it levels" }),
        ),
        rows = listOf(
            "one-hot" to "one column per level, exact and sparse",
            "target encoding" to "one column, and a leak risk that needs cross-fitting",
            "hashing" to "fixed width, with collisions you cannot inspect",
            "embedding" to "learned width, and it needs a model to learn it",
        ),
        readout = "exact, sparse, and linear in the level count",
    )
    return frames
}

private val nbLegend = listOf(
    ChipActive to "Current",
    ChipResult to "Scored",
)

private val tokenConfigs = mapOf(
    "prompt_engineering" to TokenConfig(
        intro = "A prompt written as the induction problem it is: five rules fit the instruction, and each " +
            "demonstration kills the ones it contradicts — including the two-shot prompt that answers " +
            "confidently and wrongly.",
        legend = listOf(
            ChipActive to "Newest demo",
            ChipResult to "Used",
        ),
        build = ::promptEngineeringFrames,
    ),
    "react" to TokenConfig(
        intro = "One two-hop question through a real retriever: the answer passage ranks 5th for the question " +
            "as asked and 1st for the query the second thought writes.",
        legend = listOf(
            ChipActive to "Action",
            ChipResult to "Observation",
        ),
        build = ::reactFrames,
    ),
    "ai_agents" to TokenConfig(
        intro = "A real eight-entry trajectory with one failed tool call, priced: the transcript grows " +
            "linearly, the bill grows quadratically, and the retry costs far more than its error message.",
        legend = listOf(
            ChipActive to "Failure / retry",
            ChipResult to "Context",
        ),
        build = ::agentFrames,
    ),
    "positional_encodings" to TokenConfig(
        intro = "The sinusoidal table as a heat grid, its offset-invariance measured, the non-monotone decay " +
            "textbook diagrams smooth over, and RoPE's exact relative identity.",
        legend = listOf(
            ChipActive to "Position signal",
            ChipResult to "Relative score",
        ),
        build = ::positionalFrames,
    ),
    "bart" to TokenConfig(
        intro = "One document through all five of BART's corruptions, each classified by what it actually " +
            "destroys — tokens, length, or order.",
        legend = listOf(
            ChipActive to "Corrupted",
            ChipResult to "Objective",
        ),
        build = ::bartFrames,
    ),
    "xlnet" to TokenConfig(
        intro = "BERT's independence assumption priced on \"New York\", then permutation language modelling and " +
            "the two attention streams it forces.",
        legend = listOf(
            ChipActive to "Masked / cost",
            ChipResult to "Permutation order",
        ),
        build = ::xlnetFrames,
    ),
    "mistral_mixtral" to TokenConfig(
        intro = "A router sending each token to 2 of 8 experts, the load imbalance that follows, and the " +
            "compute-versus-memory trade in numbers.",
        legend = listOf(
            ChipActive to "Router scores",
            ChipResult to "Chosen expert",
        ),
        build = ::moeFrames,
    ),
    "claude_gemini" to TokenConfig(
        intro = "What actually separates the frontier families: context window, the KV cache it costs, the " +
            "modality story, and how each one is aligned.",
        legend = listOf(
            ChipActive to "Volatile",
            ChipResult to "Durable",
        ),
        build = ::frontierFrames,
    ),
    "pos_tagging" to TokenConfig(
        intro = "A most-frequent-tag baseline and a bigram HMM, both estimated from the same mini treebank and " +
            "both scored on held-out sentences — including a word training never contained.",
        legend = listOf(
            ChipActive to "Ambiguous / wrong",
            ChipResult to "Resolved",
        ),
        build = ::posTaggingFrames,
    ),
    "chunking" to TokenConfig(
        intro = "A regex-over-tags chunker run on one sentence, encoded as BIO, then scored — and one boundary " +
            "moved to show what exact-match span evaluation does to a nearly-right answer.",
        legend = listOf(
            ChipActive to "Current chunk",
            ChipResult to "Chunk start",
        ),
        build = ::chunkingFrames,
    ),
    "coreference" to TokenConfig(
        intro = "Agreement filtering, chains as a transitive closure, and the Winograd pair where one word flips " +
            "the answer and every syntactic heuristic scores 50%.",
        legend = listOf(
            ChipActive to "Pronoun / trigger",
            ChipResult to "Antecedent",
        ),
        build = ::corefFrames,
    ),
    "sentiment_lexicon" to TokenConfig(
        intro = "A 20-word lexicon scored over ten labelled reviews, plain and then with negation, intensity and " +
            "contrast rules — including the sentence the rules themselves get wrong.",
        legend = listOf(
            ChipActive to "Negator",
            ChipResult to "Scored word",
        ),
        build = ::sentimentLexiconFrames,
    ),
    "word2vec_cbow" to TokenConfig(
        intro = "A window predicting its own missing centre word, trained for real on a 20-sentence corpus — " +
            "then measured against skip-gram on the same text, including the rare word where the textbook " +
            "expects CBOW to lose.",
        legend = listOf(
            ChipActive to "Context",
            ChipResult to "Target / result",
        ),
        build = ::cbowFrames,
    ),
    "word2vec_skipgram" to TokenConfig(
        intro = "One centre word predicting each of its neighbours: the pair count, the 3/4-power noise " +
            "distribution, the cost negative sampling avoids, and king − man + woman computed on vectors this " +
            "lab trains.",
        legend = listOf(
            ChipActive to "Context",
            ChipResult to "Centre / result",
        ),
        build = ::skipGramFrames,
    ),
    "fasttext" to TokenConfig(
        intro = "Words as bags of character n-grams, and four words the corpus never contained getting vectors " +
            "anyway — with the coverage each one was built from.",
        legend = listOf(
            ChipActive to "Composed",
            ChipResult to "Seen in training",
        ),
        build = ::fastTextFrames,
    ),
    "stop_words" to TokenConfig(
        intro = "One review through a real NLTK stop list, then the same list applied to its opposite — and the " +
            "two reviews arriving as the same vector.",
        legend = listOf(
            ChipActive to "On the list",
            ChipResult to "Survives",
        ),
        build = ::stopWordFrames,
    ),
    "text_cleaning" to TokenConfig(
        intro = "Eight cleaning stages in order, with the corpus vocabulary measured after each one — including " +
            "the two stages that merge words you wanted merged and the one that merges words you did not.",
        legend = listOf(
            ChipActive to "Before",
            ChipResult to "After",
        ),
        build = ::cleaningFrames,
    ),
    "regex_nlp" to TokenConfig(
        intro = "\\w+ against a sentence with an e-mail, an abbreviation and a date in it, then the pattern that " +
            "survives them — and the nested quantifier whose backtracking is counted, not timed.",
        legend = listOf(
            ChipActive to "Broken / risky",
            ChipResult to "Kept whole",
        ),
        build = ::regexFrames,
    ),
    "n_grams" to TokenConfig(
        intro = "A bigram window sliding over a padded sentence, counted into a model, then scored on held-out " +
            "text where one unseen bigram sends perplexity to infinity.",
        legend = listOf(
            ChipActive to "Window / unseen",
            ChipResult to "Counted",
        ),
        build = ::nGramFrames,
    ),
    "hmm" to TokenConfig(
        intro = "\"book that flight\" tagged twice: greedily, and by Viterbi. They disagree, and the trellis " +
            "shows exactly where the greedy path threw away the answer.",
        legend = listOf(
            ChipActive to "Greedy",
            ChipResult to "Viterbi",
        ),
        build = ::hmmFrames,
    ),
    "jaccard_similarity" to TokenConfig(
        intro = "Two documents as sets, scored by Jaccard and by cosine side by side, then re-scored on " +
            "character shingles and estimated by MinHash.",
        legend = listOf(
            ChipActive to "Only in A",
            ChipResult to "Shared",
        ),
        build = ::jaccardFrames,
    ),
    "k_modes" to TokenConfig(
        intro = "Lloyd's algorithm on purely categorical rows: Hamming distance in place of Euclidean, per-attribute mode in place of the mean.",
        legend = nbLegend,
        build = ::kModesFrames,
    ),
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
    "bpe" to TokenConfig(
        intro = "Four merge rounds learned from a tiny corpus, then those same merges replayed to encode a word " +
            "the corpus never contained.",
        legend = listOf(
            ChipActive to "Winning pair",
            ChipResult to "Merged token",
        ),
        build = ::bpeFrames,
    ),
    "ner" to TokenConfig(
        intro = "BIO tagging over one sentence: emissions from the encoder, why greedy per-token argmax produces " +
            "illegal sequences, and what the CRF transition matrix fixes.",
        legend = listOf(
            ChipActive to "Ambiguous / wrong",
            ChipResult to "Entity token",
        ),
        build = ::nerFrames,
    ),
    "rag" to TokenConfig(
        intro = "One question through the whole pipeline: chunk, embed, retrieve, rerank, prompt, cite — " +
            "including the retrieved chunk that is relevant but out of date.",
        legend = listOf(
            ChipActive to "Candidate",
            ChipResult to "Used in answer",
        ),
        build = ::ragFrames,
    ),
    "tokenization" to TokenConfig(
        intro = "From a raw string to integer ids. Watch the two words the vocabulary has never seen get broken into " +
            "pieces instead of becoming <unk>.",
        legend = tokenLegend,
        build = ::tokenizationFrames,
    ),
    "stemming" to TokenConfig(
        intro = "Suffix rules applied in order, with no dictionary anywhere. Fast, crude, and the output is not " +
            "required to be a real word.",
        legend = tokenLegend,
        build = ::stemmingFrames,
    ),
    "lemmatization" to TokenConfig(
        intro = "The same words through a lemmatizer: a dictionary lookup that needs the part of speech. Compare the " +
            "last frame against the stemmer's output.",
        legend = tokenLegend,
        build = ::lemmatizationFrames,
    ),
    "bow_tfidf" to TokenConfig(
        intro = "Three documents to counts, then to weights. The point of idf is visible in one number: the most " +
            "frequent word in the corpus ends up weighted zero.",
        legend = vectorLegend,
        build = ::bowTfidfFrames,
    ),
    "word_embeddings" to TokenConfig(
        intro = "Words as dense vectors, with cosine similarity and the king − man + woman analogy computed on the " +
            "vectors shown — not asserted.",
        legend = vectorLegend,
        build = ::wordEmbeddingFrames,
    ),
    "self_cross_attention" to TokenConfig(
        intro = "The same operation with the queries moved. Includes the experiment C5's encoder-decoder topic " +
            "pointed at: cross-attention against a fixed context vector, same task, same budget, parameters matched.",
        legend = attentionLegend,
        build = ::selfCrossAttentionFrames,
    ),
    "multi_head_attention" to TokenConfig(
        intro = "What splitting the width into heads buys — measured as simultaneous reads rather than as the rank " +
            "story — and what it costs when the heads get thin.",
        legend = attentionLegend,
        build = ::multiHeadFrames,
    ),
    "bert" to TokenConfig(
        intro = "Masked language modelling counted on a real corpus: targets per pass, context per prediction, the " +
            "80/10/10 corruption, and 109,482,240 parameters summed from the config.",
        legend = listOf(
            ChipActive to "Selected",
            ChipResult to "Predicted",
        ),
        build = ::bertFrames,
    ),
    "gpt" to TokenConfig(
        intro = "One triangular mask, and everything that follows from it: more targets, less context, and a KV " +
            "cache a bidirectional model can never have.",
        legend = attentionLegend,
        build = ::gptFrames,
    ),
    "t5" to TokenConfig(
        intro = "Every task as text-to-text, and span corruption priced at the length these models really run — " +
            "512 tokens in, 104 out.",
        legend = tokenLegend,
        build = ::t5Frames,
    ),
    "roberta" to TokenConfig(
        intro = "The same architecture, trained properly. Static masking leaves one token in five never predicted; " +
            "the arithmetic is 0.85ᵏ.",
        legend = vectorLegend,
        build = ::robertaFrames,
    ),
    "distilbert" to TokenConfig(
        intro = "A real teacher distribution, softened by temperature, and the parameter table that shows why " +
            "halving the layers does not halve the model.",
        legend = vectorLegend,
        build = ::distilBertFrames,
    ),
    "hf_tokenizers" to TokenConfig(
        intro = "BPE, WordPiece and Unigram trained here on one corpus and compared on held-out text — fertility, " +
            "unknown words, and what a tokenizer choice costs a transformer forever.",
        legend = tokenLegend,
        build = ::tokenizerFrames,
    ),
    "one_hot_encoding" to TokenConfig(
        intro = "One categorical column as a matrix, the false geometry it removes, and the collinearity that makes people drop a level.",
        legend = vectorLegend,
        build = ::oneHotFrames,
    ),
    "bidirectional_rnn" to TokenConfig(
        intro = "Two taggers on a corpus of garden-path minimal pairs. The left-to-right model's ceiling is " +
            "enumerated from the corpus before either model is trained — then measured against it.",
        legend = listOf(
            ChipActive to "Disputed word",
            ChipResult to "Decides it",
            BarPositive to "Tag mass",
        ),
        build = ::bidirectionalFrames,
    ),
    "encoder_decoder" to TokenConfig(
        intro = "Two RNNs joined by one vector, trained to copy. Accuracy against source length, a linear probe of " +
            "what the vector kept, and the reversal trick that costs nothing.",
        legend = vectorLegend,
        build = ::encoderDecoderFrames,
    ),
    "seq2seq" to TokenConfig(
        intro = "The same trained model decoded greedily, by beam, and with length normalization — then every " +
            "error sorted into the two kinds, only one of which a wider beam can fix.",
        legend = vectorLegend,
        build = ::seq2seqFrames,
    ),
    "rnn_lstm" to TokenConfig(
        intro = "One hidden state, updated token by token. The last two frames show the decay that kills plain RNNs " +
            "and the gate that fixes it.",
        legend = vectorLegend,
        build = ::rnnFrames,
    ),
    "attention" to TokenConfig(
        intro = "One query token scored against every key, softmaxed into weights, then the whole layer as a matrix. " +
            "The n² in the last frame is the cost that defines the architecture.",
        legend = attentionLegend,
        build = ::attentionFrames,
    ),
    "transformers" to TokenConfig(
        intro = "Attention plus feed-forward plus residual, stacked. Two blocks are enough to show what depth buys: " +
            "the second block's queries are already context-aware.",
        legend = attentionLegend,
        build = ::transformerFrames,
    ),
    "llms" to TokenConfig(
        intro = "One decoding step end to end: logits, softmax, temperature, append, repeat. Everything an LLM does " +
            "in conversation is this loop.",
        legend = vectorLegend,
        build = ::llmFrames,
    ),
)

private fun tokenConfigFor(topicId: String): TokenConfig =
    tokenConfigs[topicId] ?: tokenConfigs.getValue("tokenization")

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
            Text(
                config.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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

            frame.readout?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            Text(
                frame.status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 10.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                config.legend.forEach { (color, label) -> TokenLegend(color, label) }
            }

            PlaybackTransport(playback)
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
            drawLine(color = axis, start = Offset(0f, mid), end = Offset(size.width, mid), strokeWidth = 1.5f)
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
