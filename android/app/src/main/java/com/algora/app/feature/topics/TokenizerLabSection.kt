package com.algora.app.feature.topics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.algora.app.core.ui.theme.IBMPlexMono
import com.algora.app.core.ui.theme.SimColors

// ── Tokenizer lab ────────────────────────────────────────────────────────────
// The tokenization topics (word / character / subword splitting, BPE, and the Hugging Face trio) on
// one layout: the input text with its split points marked, a divider naming the method, the tokens
// it produced with their ids, then readout chips, narration and the transport. Frames are plain data
// built here from the real algorithms (PretrainMath.kt's TokenizerLab for the trio) and exported to
// the iOS app as JSON, so both platforms play the exact same numbers.

enum class TokKind { VOCAB, UNKNOWN, NEW, PAIR, DIM }

class TokChip(val text: String, val sub: String? = null, val kind: TokKind = TokKind.VOCAB)

class TokRow(val label: String, val value: String)

enum class TokLegendKind { SPLIT, VOCAB, UNKNOWN, NEW }

class TokLegend(val kind: TokLegendKind, val label: String)

class TokFrame(
    /** The text in the INPUT panel. */
    val input: String,
    /** Character offsets where a split point is drawn. A split at a space replaces the space. */
    val splits: List<Int>,
    /** The divider caption: how this frame split the input. */
    val method: String,
    val tokensLabel: String = "TOKENS",
    val tokens: List<TokChip>,
    val rows: List<TokRow> = emptyList(),
    /** "tokens = 4 · unknown = 1" as chips; a nonzero unknown chip is drawn in red. */
    val readout: String,
    val headline: String,
    /** A phrase of [headline] tinted in the accent. */
    val highlight: String? = null,
    val detail: String,
)

class TokenizerLabConfig(val intro: String, val legend: List<TokLegend>, val frames: List<TokFrame>)

private val splitVocabUnknown = listOf(
    TokLegend(TokLegendKind.SPLIT, "Split point"),
    TokLegend(TokLegendKind.VOCAB, "In vocabulary"),
    TokLegend(TokLegendKind.UNKNOWN, "Unknown"),
)

/** Offsets between consecutive pieces of [pieces] laid over [text] (spaces between words included). */
private fun pieceSplits(words: List<List<String>>): List<Int> {
    val out = mutableListOf<Int>()
    var at = 0
    words.forEachIndexed { w, pieces ->
        pieces.forEachIndexed { p, piece ->
            at += piece.length
            if (p < pieces.lastIndex) out += at
        }
        if (w < words.lastIndex) { out += at; at += 1 }
    }
    return out
}

private fun stripEnd(piece: String) = piece.removeSuffix("</w>")

// ── Tokenization: words, characters, subwords, ids ──────────────────────────

private fun tokenizationLab(): TokenizerLabConfig {
    val raw = "unbelievable tokenizers split words"
    val words = raw.split(" ")
    // A word-level vocabulary that happens not to contain "unbelievable".
    val wordIds = mapOf("tokenizers" to 812, "split" to 2041, "words" to 1733)
    val subwords = mapOf(
        "unbelievable" to listOf("un", "believ", "able"),
        "tokenizers" to listOf("token", "izer", "s"),
        "split" to listOf("split"),
        "words" to listOf("word", "s"),
    )
    val subwordIds = mapOf(
        "un" to 403, "believ" to 6011, "able" to 540, "token" to 3200,
        "izer" to 7641, "s" to 82, "split" to 2041, "word" to 1178,
    )
    val pieces = words.flatMap { subwords.getValue(it) }
    val characters = raw.filter { it != ' ' }
    val unknown = words.count { it !in wordIds }
    val frames = mutableListOf<TokFrame>()

    frames += TokFrame(
        input = raw,
        splits = pieceSplits(words.map { listOf(it) }),
        method = "split on spaces",
        tokens = words.map { w ->
            wordIds[w]?.let { TokChip(w, it.toString()) } ?: TokChip(w, "[UNK]", TokKind.UNKNOWN)
        },
        readout = "tokens = ${words.size} · unknown = $unknown",
        headline = "Splitting on spaces gives ${words.size} tokens.",
        highlight = "${words.size} tokens",
        detail = "It's simple, but \"unbelievable\" isn't in the vocabulary, so it becomes [UNK] and its meaning is lost.",
    )
    frames += TokFrame(
        input = raw,
        splits = pieceSplits(words.map { w -> w.map { it.toString() } }),
        method = "split into characters",
        tokens = characters.map { TokChip(it.toString()) },
        readout = "tokens = ${characters.length} · unknown = 0",
        headline = "Characters never go unknown, but the same text costs ${characters.length} tokens.",
        highlight = "${characters.length} tokens",
        detail = "Every letter is in a tiny vocabulary. The price is length: ${characters.length / words.size}× the " +
            "words, and attention cost grows with its square.",
    )
    frames += TokFrame(
        input = raw,
        splits = pieceSplits(words.map { subwords.getValue(it) }),
        method = "split into subwords",
        tokens = pieces.map { TokChip(it, subwordIds.getValue(it).toString()) },
        readout = "tokens = ${pieces.size} · unknown = 0",
        headline = "Subwords give ${pieces.size} tokens and nothing is unknown.",
        highlight = "${pieces.size} tokens",
        detail = "\"unbelievable\" becomes un + believ + able, pieces the vocabulary already has. Common words " +
            "stay whole; rare ones cost a few more.",
    )
    frames += TokFrame(
        input = raw,
        splits = pieceSplits(words.map { subwords.getValue(it) }),
        method = "look up each piece",
        tokensLabel = "IDS",
        tokens = pieces.map { TokChip(subwordIds.getValue(it).toString(), it) },
        readout = "ids = ${pieces.size} · unknown = 0",
        headline = "The model never sees text, only these ${pieces.size} ids.",
        highlight = "${pieces.size} ids",
        detail = "Each id picks a row of the embedding table. The tokenizer is fixed before training, so this " +
            "split is permanent.",
    )
    return TokenizerLabConfig(
        intro = "One sentence tokenized three ways: whole words, single characters, and subwords. Watch what " +
            "happens to the word the vocabulary has never seen.",
        legend = splitVocabUnknown,
        frames = frames,
    )
}

// ── BPE: learn merges, then replay them on an unseen word ───────────────────

private fun applyMerge(symbols: List<String>, pair: Pair<String, String>): List<String> {
    val out = mutableListOf<String>()
    var i = 0
    while (i < symbols.size) {
        if (i < symbols.size - 1 && symbols[i] == pair.first && symbols[i + 1] == pair.second) {
            out += pair.first + pair.second
            i += 2
        } else {
            out += symbols[i]
            i++
        }
    }
    return out
}

private fun bpeLab(): TokenizerLabConfig {
    // Word -> frequency. "low"/"lower"/"newest"/"widest" is the canonical BPE worked example.
    val corpus = linkedMapOf("low" to 5, "lower" to 2, "newest" to 6, "widest" to 3)
    var vocab: Map<List<String>, Int> = corpus.entries.associate { (word, freq) -> (word.map { it.toString() } + "</w>") to freq }
    val alphabet = vocab.keys.flatten().toSet()
    var vocabularySize = alphabet.size
    val merges = mutableListOf<Pair<String, String>>()
    val frames = mutableListOf<TokFrame>()
    val corpusRows = corpus.map { (word, freq) -> TokRow(word, "×$freq") }

    fun wordFrameInput(symbols: List<String>) = symbols.joinToString("") { stripEnd(it) }
    fun splitsOf(symbols: List<String>) = pieceSplits(listOf(symbols.map { stripEnd(it) }.filter { it.isNotEmpty() }))

    val first = vocab.keys.first { it.joinToString("").startsWith("newest") }
    frames += TokFrame(
        input = wordFrameInput(first),
        splits = splitsOf(first),
        method = "start from characters",
        tokens = first.map { TokChip(it) },
        rows = corpusRows,
        readout = "vocabulary = $vocabularySize · merges = 0",
        headline = "Start from single characters, so nothing is ever unknown.",
        highlight = "nothing is ever unknown",
        detail = "The vocabulary is just the ${alphabet.size} symbols in the corpus. BPE's only question is how " +
            "many pieces a word costs.",
    )

    repeat(4) { round ->
        val pairs = linkedMapOf<Pair<String, String>, Int>()
        for ((symbols, freq) in vocab) {
            for (i in 0 until symbols.size - 1) {
                val pair = symbols[i] to symbols[i + 1]
                pairs[pair] = (pairs[pair] ?: 0) + freq
            }
        }
        val ranked = pairs.entries.sortedByDescending { it.value }
        val best = ranked.firstOrNull()?.key ?: return@repeat
        val bestCount = pairs.getValue(best)
        val bestText = best.first + best.second
        val sample = vocab.keys.first { symbols -> (0 until symbols.size - 1).any { symbols[it] to symbols[it + 1] == best } }

        frames += TokFrame(
            input = wordFrameInput(sample),
            splits = splitsOf(sample),
            method = "count adjacent pairs",
            tokensLabel = "TOP PAIRS",
            tokens = ranked.take(4).map { (pair, count) ->
                TokChip("${pair.first} + ${pair.second}", "×$count", if (pair == best) TokKind.NEW else TokKind.PAIR)
            },
            rows = corpusRows,
            readout = "vocabulary = $vocabularySize · merges = $round",
            headline = "Round ${round + 1}: \"$bestText\" is the most frequent pair, $bestCount times.",
            highlight = "\"$bestText\"",
            detail = "Every adjacent pair is counted once per word, weighted by how often that word appears.",
        )

        merges += best
        vocabularySize += 1
        vocab = vocab.mapKeys { (symbols, _) -> applyMerge(symbols, best) }
        val after = applyMerge(sample, best)
        frames += TokFrame(
            input = wordFrameInput(after),
            splits = splitsOf(after),
            method = "merge ${best.first} + ${best.second} everywhere",
            tokens = after.map { TokChip(it, kind = if (it == best.first + best.second) TokKind.NEW else TokKind.VOCAB) },
            rows = merges.mapIndexed { i, (a, b) -> TokRow("merge ${i + 1}", "$a + $b → $a$b") },
            readout = "vocabulary = $vocabularySize · merges = ${round + 1}",
            headline = "Merge \"$bestText\" everywhere, adding exactly one token.",
            highlight = "\"$bestText\"",
            detail = "One merge, one new vocabulary entry: that is the only knob BPE turns.",
        )
    }

    // Encoding an unseen word replays the merge list in order.
    var unseen = "lowest".map { it.toString() } + "</w>"
    frames += TokFrame(
        input = "lowest",
        splits = splitsOf(unseen),
        method = "replay the merges in order",
        tokens = unseen.map { TokChip(it) },
        rows = merges.mapIndexed { i, (a, b) -> TokRow("merge ${i + 1}", "$a + $b → $a$b") },
        readout = "tokens = ${unseen.size} · unknown = 0",
        headline = "\"lowest\" never appeared in the corpus.",
        highlight = "\"lowest\"",
        detail = "Encoding doesn't search for a split. It replays the learned merges, in the order they were learned.",
    )
    merges.forEachIndexed { index, (a, b) ->
        val next = applyMerge(unseen, a to b)
        if (next != unseen) {
            unseen = next
            frames += TokFrame(
                input = "lowest",
                splits = splitsOf(unseen),
                method = "merge ${index + 1}: $a + $b",
                tokens = unseen.map { TokChip(it, kind = if (it == a + b) TokKind.NEW else TokKind.VOCAB) },
                readout = "tokens = ${unseen.size} · unknown = 0",
                headline = "Merge ${index + 1}, $a + $b, applies to \"lowest\".",
                highlight = "Merge ${index + 1}",
                detail = "It reuses a piece learned from other words, which is how an unseen word still gets short.",
            )
        }
    }
    frames += TokFrame(
        input = "lowest",
        splits = splitsOf(unseen),
        method = "final tokens",
        tokens = unseen.map { TokChip(it) },
        readout = "tokens = ${unseen.size} · unknown = 0",
        headline = "\"lowest\" ends as ${unseen.size} tokens, none unknown.",
        highlight = "${unseen.size} tokens",
        detail = "Frequent words collapse to one token and rare ones split. Sequence length then drives attention " +
            "cost downstream.",
    )
    return TokenizerLabConfig(
        intro = "Four merge rounds learned from a tiny corpus, then those same merges replayed to encode a word " +
            "the corpus never contained.",
        legend = listOf(
            TokLegend(TokLegendKind.SPLIT, "Split point"),
            TokLegend(TokLegendKind.VOCAB, "In vocabulary"),
            TokLegend(TokLegendKind.NEW, "Merged"),
        ),
        frames = frames,
    )
}

// ── BPE vs WordPiece vs Unigram, trained on one corpus ──────────────────────

/** Built once; also exported for iOS by IosContentExportTest. */
internal val tokenizerLabConfigs: Map<String, TokenizerLabConfig> by lazy {
    linkedMapOf(
        "tokenization" to tokenizationLab(),
        "bpe" to bpeLab(),
    )
}

internal val tokenizerLabTopicIds: Set<String> = setOf("tokenization", "bpe")

// ── UI ───────────────────────────────────────────────────────────────────────

@Composable
fun TokenizerLabSection(topicId: String) {
    val config = remember(topicId) { tokenizerLabConfigs.getValue(topicId) }
    val playback = rememberPlaybackState(key = config, stepCount = config.frames.size, initialSpeedMs = 1400f)
    val frame = config.frames[playback.index.coerceIn(0, config.frames.lastIndex)]
    val dock = LocalLabDock.current

    LabIntro(config.intro)
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                TokenizerStage(frame)
                TokenizerLegend(config.legend, Modifier.padding(top = 16.dp))
                if (dock == null) {
                    TokenizerReadout(frame, Modifier.padding(top = 16.dp))
                    TokenizerNarration(frame, Modifier.padding(top = 14.dp))
                }
                PlaybackTransport(playback, captions = config.frames.map { "${it.headline} ${it.detail}" })
            }
        }
        if (dock != null) {
            TokenizerReadout(frame, Modifier.padding(top = 14.dp))
            TokenizerNarration(frame, Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp))
        }
    }
}

private val UnknownRed = SimColors.Red

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A split point: a small yellow tab between two pieces of the input. */
@Composable
private fun SplitMark() {
    Box(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .size(width = 12.dp, height = 24.dp)
            .background(SimColors.Active, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text("·", fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1A1A1A))
    }
}

@Composable
private fun TokenizerStage(frame: TokFrame) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Split the input into runs at the split points; a split on a space swallows the space.
    val runs = remember(frame) {
        val out = mutableListOf<String>()
        var start = 0
        frame.splits.sorted().forEach { at ->
            out += frame.input.substring(start, at)
            start = if (frame.input[at] == ' ') at + 1 else at
        }
        out += frame.input.substring(start)
        out
    }
    Column {
        SectionLabel("INPUT")
        FlowRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            runs.forEachIndexed { i, run ->
                // A split inside a run of text keeps the word visually whole around its mark.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    run.split(' ').forEachIndexed { k, word ->
                        if (k > 0) Text(" ", fontFamily = IBMPlexMono, fontSize = 21.sp)
                        Text(word, fontFamily = IBMPlexMono, fontSize = 21.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    if (i < runs.lastIndex) SplitMark()
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
            Text("${frame.method}  ↓", fontSize = 14.sp, color = muted, modifier = Modifier.padding(horizontal = 12.dp))
            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline)
        }
        SectionLabel(frame.tokensLabel)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            frame.tokens.forEach { TokenChip(it) }
        }
        if (frame.rows.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                frame.rows.forEach { row ->
                    Row {
                        Text(row.label, fontSize = 14.sp, color = muted, modifier = Modifier.widthIn(min = 92.dp))
                        Text(row.value, fontFamily = IBMPlexMono, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TokenChip(chip: TokChip) {
    val accent = SimColors.Answer
    val (fill, border, text) = when (chip.kind) {
        TokKind.VOCAB -> Triple(accent.copy(alpha = 0.32f), null, MaterialTheme.colorScheme.onSurface)
        TokKind.UNKNOWN -> Triple(UnknownRed.copy(alpha = 0.14f), UnknownRed, MaterialTheme.colorScheme.onSurface)
        TokKind.NEW -> Triple(SimColors.Green, null, Color.White)
        TokKind.PAIR -> Triple(SimColors.Tint, null, MaterialTheme.colorScheme.onSurface)
        TokKind.DIM -> Triple(SimColors.Tint.copy(alpha = 0.3f), null, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .height(44.dp)
                .widthIn(min = 40.dp)
                .background(fill, RoundedCornerShape(12.dp))
                .then(if (border != null) Modifier.border(2.dp, border, RoundedCornerShape(12.dp)) else Modifier)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(chip.text, fontFamily = IBMPlexMono, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = text)
        }
        chip.sub?.let {
            Text(
                it,
                fontFamily = IBMPlexMono,
                fontSize = 13.sp,
                color = if (chip.kind == TokKind.UNKNOWN) UnknownRed.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun TokenizerLegend(items: List<TokLegend>, modifier: Modifier) {
    FlowRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val shape = RoundedCornerShape(3.dp)
                Box(
                    modifier = when (item.kind) {
                        TokLegendKind.SPLIT -> Modifier.size(10.dp).background(SimColors.Active, shape)
                        TokLegendKind.VOCAB -> Modifier.size(10.dp).background(SimColors.Answer, shape)
                        TokLegendKind.UNKNOWN -> Modifier.size(10.dp).border(2.dp, UnknownRed, shape)
                        TokLegendKind.NEW -> Modifier.size(10.dp).background(SimColors.Green, shape)
                    },
                )
                Text(
                    item.label,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TokenizerReadout(frame: TokFrame, modifier: Modifier) {
    val parts = readoutParts(frame.readout)
    // A nonzero unknown count is the frame's warning; it is drawn in red.
    val warned = parts.indices.filter { parts[it].first == "unknown" && parts[it].second != "0" }.toSet()
    ReadoutChips(parts, modifier, warned = warned)
}

@Composable
private fun TokenizerNarration(frame: TokFrame, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val headline = buildAnnotatedString {
        val at = frame.highlight?.let { frame.headline.indexOf(it) } ?: -1
        if (at < 0) {
            append(frame.headline)
        } else {
            append(frame.headline.substring(0, at))
            withStyle(SpanStyle(color = accent)) { append(frame.highlight!!) }
            append(frame.headline.substring(at + frame.highlight!!.length))
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(headline, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
        Text(
            frame.detail,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Frame guard: split points must fall inside the input, and every frame must draw tokens. */
internal fun tokenizerLabFrameCount(topicId: String): Int {
    val config = tokenizerLabConfigs.getValue(topicId)
    config.frames.forEachIndexed { i, frame ->
        require(frame.tokens.isNotEmpty()) { "$topicId frame $i has no tokens" }
        require(frame.splits.all { it in 1 until frame.input.length }) { "$topicId frame $i splits outside \"${frame.input}\"" }
        require(frame.highlight == null || frame.headline.contains(frame.highlight)) { "$topicId frame $i highlights text its headline lacks" }
    }
    return config.frames.size
}
