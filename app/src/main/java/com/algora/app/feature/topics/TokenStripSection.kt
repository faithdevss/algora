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

private val tokenConfigs = mapOf(
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
