package com.algora.app.feature.topics

import java.text.Normalizer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

// ── Text preprocessing, statistical NLP and word-embedding storyboards ───────
// Thirteen labs drawn by NlpStoryLabs.kt. Everything on screen is run here: the regex patterns through the
// platform regex engine, NFKC through the platform normaliser, Porter's step 1 rules, the bigram counts,
// TF-IDF, cosine and Jaccard, Viterbi over a small HMM and a CBOW/skip-gram model trained on one sentence.
// NlpTextFrames.swift is the iOS port.

internal val nlpTextTopicIds = setOf(
    "n_grams", "lemmatization", "stemming", "stop_words", "regex_nlp", "text_cleaning", "bow_tfidf",
    "cosine_similarity", "jaccard_similarity", "hmm", "word_embeddings", "word2vec_cbow", "word2vec_skipgram",
    "rnn_lstm",
)

internal fun nlpTextLab(topicId: String): List<NbFrame>? = when (topicId) {
    "n_grams" -> nGramFrames()
    "lemmatization" -> lemmaFrames()
    "stemming" -> stemFrames()
    "stop_words" -> stopWordFrames()
    "regex_nlp" -> regexFrames()
    "text_cleaning" -> cleaningFrames()
    "bow_tfidf" -> bowFrames()
    "cosine_similarity" -> cosineFrames()
    "jaccard_similarity" -> jaccardFrames()
    "hmm" -> hmmFrames()
    "word_embeddings" -> embeddingFrames()
    "word2vec_cbow" -> cbowFrames()
    "word2vec_skipgram" -> skipGramFrames()
    "rnn_lstm" -> rnnFrames()
    else -> null
}

private fun f2(v: Double) = nbF(v, 2)

// ── N-grams ──

private fun nGramFrames(): List<NbFrame> {
    val padded = listOf("<s>", "i", "like", "nlp", "</s>")
    val bigrams = padded.zipWithNext()
    val use = listOf("first word", "—", "—", "ending")
    fun window(at: Int) = NbBlock.Toks(padded.mapIndexed { i, w ->
        nbTok(w, if (i == at || i == at + 1) NbTone.Hot else if (w.startsWith("<")) NbTone.Dim else NbTone.Plain)
    })
    fun table(at: Int) = NbBlock.Table(
        listOf("bigram", "position", "used for"), listOf(1f, 0.6f, 0.8f),
        bigrams.mapIndexed { i, (a, b) ->
            NbRow(listOf(nbName("($a, $b)"), nbCell("${i + 1}"), nbCell(use[i], if (use[i] == "—") null else NbInk.Indigo)), ring = i == at)
        },
    )
    val legend = listOf(nbLegend(NbInk.Yellow, "Window"), nbLegend(NbInk.Slate, "Boundary marker"))
    val tiles = NbBlock.Tiles(listOf(NbTile("4", "bigrams with padding", NbInk.Indigo), NbTile("2", "without — no start, no end", NbInk.Grey)))
    val windowText = listOf(
        "A bigram is a two-token window, slid one position at a time." to
            "Without <s> and </s> the model has nothing to condition the first word on and no way to end a sentence, so its probabilities would not sum to 1.",
        "Position 2: {(i, like)}." to "The window moves one token right. Every inner pair is counted exactly once.",
        "Position 3: {(like, nlp)}." to "Three words give two inner bigrams; the padding adds one at each end.",
        "Position 4: {(nlp, </s>)} — the ending." to "This window is what lets the model learn that \"nlp\" can finish a sentence. A sentence of n words always yields n + 1 bigrams.",
    )
    val frames = mutableListOf<NbFrame>()
    for (at in 0 until 4) {
        frames += NbFrame(
            listOf(NbBlock.Caption("\"i like nlp\", padded · bigram window at position ${at + 1}"), window(at), table(at)) + if (at == 0) listOf(tiles) else emptyList(),
            legend, windowText[at].first, windowText[at].second,
        )
    }
    // A three-sentence corpus, counted.
    val corpus = listOf("i like nlp", "i like cats", "you like nlp").map { listOf("<s>") + it.split(" ") + "</s>" }
    val counts = linkedMapOf<Pair<String, String>, Int>()
    val unigram = linkedMapOf<String, Int>()
    corpus.forEach { s ->
        s.dropLast(1).forEach { unigram[it] = (unigram[it] ?: 0) + 1 }
        s.zipWithNext().forEach { counts[it] = (counts[it] ?: 0) + 1 }
    }
    fun p(a: String, b: String) = (counts[a to b] ?: 0).toDouble() / (unigram[a] ?: 1)
    val corpusCaption = NbBlock.Caption("corpus · \"i like nlp\" · \"i like cats\" · \"you like nlp\"")
    val countRows = counts.entries.map { (k, c) ->
        NbRow(listOf(nbName("(${k.first}, ${k.second})"), nbCell("$c"), nbCell("${unigram[k.first]}"), nbCell(f2(c.toDouble() / unigram.getValue(k.first)), NbInk.Indigo)))
    }
    frames += NbFrame(
        listOf(corpusCaption, NbBlock.Table(listOf("bigram", "count", "c(first)", "P"), listOf(1.2f, 0.5f, 0.6f, 0.5f), countRows)),
        listOf(nbLegend(NbInk.Indigo, "P(second | first)")),
        "Count every window in the corpus.",
        "Three sentences give ${counts.values.sum()} bigram windows and ${counts.size} distinct pairs. Dividing each count by how often its first word starts a window gives P(second | first).",
        chips = listOf(nbChip("windows", "${counts.values.sum()}", true), nbChip("distinct", "${counts.size}")),
    )
    val pLike = p("i", "like")
    val pNlp = p("like", "nlp")
    frames += NbFrame(
        listOf(
            corpusCaption,
            NbBlock.Bars(
                listOf(
                    NbBar("P(nlp|like)", f2(pNlp), pNlp, NbInk.Indigo),
                    NbBar("P(cats|like)", f2(p("like", "cats")), p("like", "cats"), NbInk.Indigo),
                    NbBar("P(like|i)", f2(pLike), pLike, NbInk.Indigo),
                ),
            ),
        ),
        listOf(nbLegend(NbInk.Indigo, "Maximum-likelihood estimate")),
        "P(nlp | like) = c(like, nlp) / c(like) = {${f2(pNlp)}}.",
        "\"like\" starts three windows and two of them continue with \"nlp\". That ratio is the whole model: no parameters beyond the counts.",
        fx = listOf("P(nlp | like) = ${counts["like" to "nlp"]} / ${unigram["like"]} = {${f2(pNlp)}}"),
    )
    val sent = listOf("<s>", "i", "like", "nlp", "</s>").zipWithNext()
    val probs = sent.map { (a, b) -> p(a, b) }
    val total = probs.fold(1.0) { acc, v -> acc * v }
    frames += NbFrame(
        listOf(
            NbBlock.Caption("P(\"i like nlp\") as a chain of bigrams"),
            NbBlock.Table(listOf("bigram", "P"), listOf(1f, 0.5f), sent.mapIndexed { i, (a, b) -> NbRow(listOf(nbName("($a, $b)"), nbCell(f2(probs[i]), NbInk.Indigo))) }),
            NbBlock.Tiles(listOf(NbTile(f2(total), "P of the whole sentence", NbInk.Indigo))),
        ),
        emptyList(),
        "A sentence's probability is the product of its windows.",
        "The bigram assumption: each word depends only on the one before. ${sent.joinToString(" · ") { f2(p(it.first, it.second)) }} multiply to ${f2(total)}.",
        fx = listOf(probs.joinToString(" × ") { f2(it) } + " = {${f2(total)}}"),
    )
    val unseen = p("like", "dogs")
    frames += NbFrame(
        listOf(
            NbBlock.Caption("\"i like dogs\" · never seen in the corpus"),
            NbBlock.Toks(listOf("<s>", "i", "like", "dogs", "</s>").map { nbTok(it, if (it == "dogs") NbTone.Bad else NbTone.Plain) }),
            NbBlock.Callout(listOf("c(like, dogs) = 0  →  P(dogs | like) = {0}", "→  P(\"i like dogs\") = {0}"), warn = false),
        ),
        listOf(nbLegend(NbInk.Red, "Unseen bigram")),
        "One unseen pair zeroes the whole sentence.",
        "Maximum likelihood gives exactly ${f2(unseen)} to anything it never counted, so a perfectly normal sentence becomes impossible. Every real n-gram model needs smoothing.",
    )
    val v = unigram.keys.size + 1
    val add1 = (0.0 + 1) / (unigram.getValue("like") + v)
    val add1Nlp = (counts.getValue("like" to "nlp") + 1.0) / (unigram.getValue("like") + v)
    frames += NbFrame(
        listOf(
            NbBlock.Caption("add-one (Laplace) smoothing · V = $v word types"),
            NbBlock.Bars(
                listOf(
                    NbBar("P(nlp|like)", "${f2(pNlp)} → ${f2(add1Nlp)}", add1Nlp, NbInk.Indigo),
                    NbBar("P(dogs|like)", "0.00 → ${f2(add1)}", add1, NbInk.Green),
                ),
                labelWidth = 104,
            ),
        ),
        listOf(nbLegend(NbInk.Indigo, "Seen pair, discounted"), nbLegend(NbInk.Green, "Unseen pair, now non-zero")),
        "Add one to every count: {nothing is impossible}.",
        "Each pair gets a pseudo-count of 1 and the denominator grows by V. Seen pairs give up mass (${f2(pNlp)} → ${f2(add1Nlp)}) so unseen ones can have some.",
        fx = listOf("P = (c(like, w) + 1) / (c(like) + V) = (0 + 1) / (${unigram["like"]} + $v) = {${f2(add1)}}"),
    )
    val possible = v.toLong() * v
    frames += NbFrame(
        listOf(
            NbBlock.Caption("possible vs observed n-grams"),
            NbBlock.Tiles(listOf(NbTile("${counts.size}", "bigrams seen", NbInk.Indigo), NbTile("$possible", "possible with V = $v"), NbTile("${possible * v}", "possible trigrams", NbInk.Red))),
        ),
        emptyList(),
        "Longer windows, {sparser counts}.",
        "Each extra word of context multiplies the possible n-grams by V. With a 50,000-word vocabulary there are 2.5 billion bigrams — most never appear, which is why neural language models replaced counting.",
    )
    return frames
}

// ── Lemmatization ──

private class LemmaRow(val word: String, val pos: String, val stem: String, val lemma: String)

private fun lemmaFrames(): List<NbFrame> {
    val rows = listOf(
        LemmaRow("running", "VBG", porterStem("running"), "run"),
        LemmaRow("flies", "NNS", porterStem("flies"), "fly"),
        LemmaRow("studies", "NNS", porterStem("studies"), "study"),
        LemmaRow("better", "JJR", porterStem("better"), "good"),
        LemmaRow("mice", "NNS", porterStem("mice"), "mouse"),
    )
    val dictionary = rows.map { it.lemma }.toSet()
    fun table(ring: String? = null) = NbBlock.Table(
        listOf("word", "POS", "stem", "lemma"), listOf(1f, 0.5f, 0.7f, 0.7f),
        rows.map { r ->
            NbRow(
                listOf(nbName(r.word), nbCell(r.pos), nbCell(r.stem, if (r.stem == r.lemma) NbInk.Green else NbInk.Red), nbCell(r.lemma, NbInk.Green)),
                ring = r.word == ring,
            )
        },
    )
    val stemsOk = rows.count { it.stem in dictionary && it.stem == it.lemma }
    val legend = listOf(nbLegend(NbInk.Green, "Dictionary word"), nbLegend(NbInk.Red, "Not a word / wrong form"))
    val head = NbBlock.Caption("same five words · Porter stemmer vs WordNet lemmatizer")
    val score = NbBlock.Tiles(listOf(NbTile("$stemsOk / 5", "stems equal the dictionary form", NbInk.Red), NbTile("5 / 5", "lemmas, given the POS tag", NbInk.Green)))
    val saw = NbBlock.Table(
        listOf("sentence", "POS", "lemma"), listOf(1.6f, 0.5f, 0.6f),
        listOf(
            NbRow(listOf(nbName("I {saw} the film".replace("{", "").replace("}", "")), nbCell("VBD"), nbCell("see", NbInk.Green))),
            NbRow(listOf(nbName("a saw cuts wood"), nbCell("NN"), nbCell("saw", NbInk.Green))),
            NbRow(listOf(nbName("the meeting ran long"), nbCell("NN"), nbCell("meeting", NbInk.Green))),
            NbRow(listOf(nbName("we are meeting at 5"), nbCell("VBG"), nbCell("meet", NbInk.Green))),
        ),
    )
    return listOf(
        NbFrame(listOf(head, table(), score), legend,
            "A lemmatizer needs two things a stemmer lacks.",
            "A dictionary, and the part of speech of the word in this sentence. \"better\" only becomes \"good\" because it is tagged as a comparative adjective."),
        NbFrame(listOf(head, table("running")), legend,
            "\"running\" → {run} both ways.",
            "Regular inflection is where the two agree: strip -ing, undouble the n. The stemmer gets there by rule, the lemmatizer by lookup."),
        NbFrame(listOf(head, table("flies")), legend,
            "\"flies\" → stem {fli}, lemma {m:fly}.",
            "The stemmer's rule IES → I is right for the suffix and wrong for the word. The lemmatizer knows the noun \"fly\" and its plural."),
        NbFrame(listOf(head, table("better")), legend,
            "\"better\" → stem {better}, lemma {m:good}.",
            "No suffix rule can reach \"good\" — the form is irregular. The lemmatizer only gets it because the tag says JJR, a comparative adjective; as a verb (\"to better oneself\") the lemma is \"better\"."),
        NbFrame(listOf(head, table("mice")), legend,
            "\"mice\" → stem {mice}, lemma {m:mouse}.",
            "Irregular plurals have no suffix to strip, so the stemmer passes them through. A dictionary of exceptions is the only way."),
        NbFrame(listOf(NbBlock.Caption("same spelling, different lemma · the POS decides"), saw), listOf(nbLegend(NbInk.Green, "Lemma for this tag")),
            "The tag, not the spelling, picks the lemma.",
            "\"saw\" is the past of \"see\" or a tool; \"meeting\" is an event or the verb \"meet\". A lemmatizer is only as good as the tagger in front of it."),
        NbFrame(listOf(head, score, NbBlock.Kv(listOf("stemmer" to "fast, no dictionary, rough", "lemmatizer" to "needs POS + lexicon, exact", "search index" to "stems are fine", "text analysis" to "lemmas read as words"))), legend,
            "Stem for recall, {lemmatize} for meaning.",
            "A search engine only needs related forms to collide, and \"fli\" does that. Anything a person reads, or that counts real words, needs the lemma."),
    )
}

// ── Stemming (Porter step 1) ──

private class StemStep(val rule: String, val result: String, val title: String, val why: String)

private fun isCons(w: String, i: Int): Boolean = when (w[i]) {
    'a', 'e', 'i', 'o', 'u' -> false
    'y' -> i == 0 || !isCons(w, i - 1)
    else -> true
}

/** Porter's m: the number of vowel–consonant sequences. */
private fun measure(w: String): Int {
    var m = 0
    var i = 0
    while (i < w.length && isCons(w, i)) i++
    while (i < w.length) {
        while (i < w.length && !isCons(w, i)) i++
        if (i >= w.length) break
        while (i < w.length && isCons(w, i)) i++
        m++
    }
    return m
}

private fun hasVowel(w: String) = w.indices.any { !isCons(w, it) }

private fun endsDouble(w: String) = w.length >= 2 && w[w.length - 1] == w[w.length - 2] && isCons(w, w.length - 1)

private fun cvc(w: String) = w.length >= 3 && isCons(w, w.length - 3) && !isCons(w, w.length - 2) && isCons(w, w.length - 1) &&
    w.last() !in "wxy"

/** Porter's step 1 (1a, 1b, 1c) with a trace of the rules that fired. */
private fun porterTrace(word: String): List<StemStep> {
    var w = word
    val out = mutableListOf<StemStep>()
    when {
        w.endsWith("sses") -> { w = w.dropLast(2); out += StemStep("1a", w, "-sses → -ss", "plural of -ss") }
        w.endsWith("ies") -> { w = w.dropLast(2); out += StemStep("1a", w, "-ies → -i", "no check on what is left") }
        w.endsWith("ss") -> {}
        w.endsWith("s") -> { w = w.dropLast(1); out += StemStep("1a", w, "-s removed", "plural") }
    }
    var second = false
    if (w.endsWith("eed")) {
        if (measure(w.dropLast(3)) > 0) { w = w.dropLast(1); out += StemStep("1b", w, "-eed → -ee", "m > 0") }
    } else if (w.endsWith("ed") && hasVowel(w.dropLast(2))) {
        w = w.dropLast(2); second = true; out += StemStep("1b", w, "-ed removed", "stem \"$w\" has a vowel")
    } else if (w.endsWith("ing") && hasVowel(w.dropLast(3))) {
        w = w.dropLast(3); second = true; out += StemStep("1b", w, "-ing removed", "stem \"$w\" has a vowel")
    }
    if (second) {
        when {
            w.endsWith("at") || w.endsWith("bl") || w.endsWith("iz") -> { w += "e"; out += StemStep("1b+", w, "add -e", "ends in at/bl/iz") }
            endsDouble(w) && w.last() !in "lsz" -> { w = w.dropLast(1); out += StemStep("1b+", w, "undouble", "ends in double consonant, not l/s/z") }
            measure(w) == 1 && cvc(w) -> { w += "e"; out += StemStep("1b+", w, "add -e", "short stem, consonant-vowel-consonant") }
        }
    }
    if (w.endsWith("y") && hasVowel(w.dropLast(1))) { w = w.dropLast(1) + "i"; out += StemStep("1c", w, "-y → -i", "stem has a vowel") }
    return out
}

internal fun porterStem(word: String): String = porterTrace(word).lastOrNull()?.result ?: word

private fun stemFrames(): List<NbFrame> {
    val words = listOf("running", "flies", "studies", "happily", "better")
    fun chips(cur: String?) = NbBlock.Toks(words.map { w -> nbTok(w, if (w == cur) NbTone.Hot else NbTone.Plain, (if (w == cur) "→ " else "") + porterStem(w)) })
    fun trace(word: String, upTo: Int? = null): NbBlock.Table {
        val steps = porterTrace(word)
        val shown = steps.take(upTo ?: steps.size)
        return NbBlock.Table(
            emptyList(), listOf(0.35f, 0.7f, 1.4f), aligns = listOf(0, 0, 0),
            rows = listOf(NbRow(listOf(nbCell("input"), nbCell(word, bold = true), nbCell("")))) +
                shown.mapIndexed { i, s ->
                    NbRow(
                        listOf(nbCell(s.rule), nbCell(s.result, if (i == steps.lastIndex) NbInk.Indigo else null, bold = true), NbCell(s.title, sub = s.why, mono = false)),
                        ring = upTo != null && i == shown.lastIndex,
                    )
                } + if (steps.isEmpty()) listOf(NbRow(listOf(nbCell("—"), nbCell(word, NbInk.Indigo, true), NbCell("no rule fires", sub = "no step-1 suffix matches", mono = false)))) else emptyList(),
        )
    }
    val legend = listOf(nbLegend(NbInk.Yellow, "Current word"), nbLegend(NbInk.Indigo, "Final stem"))
    val head = NbBlock.Caption("Porter stemmer · 5 words, current one traced")
    return listOf(
        NbFrame(listOf(head, chips(null), NbBlock.Caption("rule trace for \"running\""), trace("running", 0)), legend,
            "A stemmer chops suffixes by rule — {no dictionary}.",
            "Porter's algorithm is a list of suffix rules with conditions on what remains. Each word below shows its final stem; the trace walks step 1, which does most of the work."),
        NbFrame(listOf(head, chips("running"), NbBlock.Caption("rule trace for \"running\""), trace("running", 1)), legend,
            "\"running\" ends in -ing: strip it → \"runn\".",
            "Step 1b only strips -ing when what remains still contains a vowel. The next rule undoubles the final \"nn\", so the stem settles on \"run\"."),
        NbFrame(listOf(head, chips("running"), NbBlock.Caption("rule trace for \"running\""), trace("running")), legend,
            "Undouble: \"runn\" → {p:run}.",
            "A double consonant left by removing -ing or -ed is reduced to one, except l, s and z (\"fall\", \"miss\", \"buzz\")."),
        NbFrame(listOf(head, chips("flies"), NbBlock.Caption("rule trace for \"flies\""), trace("flies")), legend,
            "\"flies\" → {p:fli}.",
            "Rule 1a turns -ies into -i unconditionally. Good for grouping \"flies\" and \"flied\"; the stem is not a word, and was never meant to be."),
        NbFrame(listOf(head, chips("studies"), NbBlock.Caption("rule trace for \"studies\""), trace("studies")), legend,
            "\"studies\" → {p:studi}.",
            "Same rule. \"study\" itself becomes \"studi\" by 1c (-y → -i), so all three forms collide on one key — which is the point."),
        NbFrame(listOf(head, chips("happily"), NbBlock.Caption("rule trace for \"happily\""), trace("happily")), legend,
            "\"happily\" → {p:happili}.",
            "Only -y → -i fires in step 1. Porter's later steps handle derivational suffixes; -ly after a vowel stem is left alone."),
        NbFrame(listOf(head, chips("better"), NbBlock.Caption("rule trace for \"better\""), trace("better")), legend,
            "\"better\" passes through {unchanged}.",
            "No step-1 suffix matches, and step 4's -er rule needs a longer stem. Irregular forms are invisible to a stemmer — that is the lemmatizer's job."),
    )
}

// ── Stop words ──

/** The NLTK English stop words that matter for the sentences here (the full list has 179). */
private val nltkStops = setOf(
    "i", "me", "my", "we", "our", "you", "your", "he", "him", "his", "she", "her", "it", "its", "they", "them", "their",
    "what", "which", "who", "this", "that", "these", "those", "am", "is", "are", "was", "were", "be", "been", "being",
    "have", "has", "had", "do", "does", "did", "a", "an", "the", "and", "but", "if", "or", "because", "as", "until",
    "while", "of", "at", "by", "for", "with", "about", "against", "between", "into", "through", "to", "from", "in",
    "out", "on", "off", "over", "under", "again", "then", "once", "here", "there", "when", "where", "why", "how",
    "all", "any", "both", "each", "few", "more", "most", "other", "some", "such", "no", "nor", "not", "only", "own",
    "same", "so", "than", "too", "very", "can", "will", "just", "don't", "should", "now",
)

private fun stopWordFrames(): List<NbFrame> {
    val review = "the movie was not good at all".split(" ")
    val kept = review.filter { it !in nltkStops }
    val listed = review.count { it in nltkStops }
    val head = NbBlock.Caption("\"${review.joinToString(" ")}\" · NLTK English list")
    val flagged = NbBlock.Toks(review.map { nbTok(it, if (it in nltkStops) NbTone.Hot else NbTone.Plain) })
    val survives = NbBlock.Toks(review.map { nbTok(it, if (it in nltkStops) NbTone.Bad else NbTone.Good) })
    val legend = listOf(nbLegend(NbInk.Yellow, "On the list"), nbLegend(NbInk.Red, "Removed"), nbLegend(NbInk.Green, "Survives"))
    val other = "the movie was good".split(" ")
    val otherKept = other.filter { it !in nltkStops }
    val corpus = listOf(
        "the movie was not good at all", "i loved the acting and the music", "it was a waste of time",
        "the plot is thin but the cast is great", "not my kind of film",
    )
    val tokens = corpus.flatMap { it.split(" ") }
    val stopShare = tokens.count { it in nltkStops }.toDouble() / tokens.size
    val negations = setOf("not", "no", "nor")
    val custom = review.filter { it !in nltkStops || it in negations }
    return listOf(
        NbFrame(listOf(head, flagged, NbBlock.Caption("what survives"), survives, NbBlock.Banner("\"${kept.joinToString(" ")}\"", "\"not\" is on the list — the sentiment flips.", NbInk.Red)), legend,
            "One review, seven tokens.",
            "Five are on NLTK's English list. They carry little about which review this is — but one of them carries whether it's positive.",
            chips = listOf(nbChip("on list", "$listed of ${review.size}", true), nbChip("kept", "${kept.size}"))),
        NbFrame(listOf(NbBlock.Caption("5 short reviews · ${tokens.size} tokens"), NbBlock.Bars(listOf(
            NbBar("stop words", "${tokens.count { it in nltkStops }}", stopShare, NbInk.Yellow),
            NbBar("content", "${tokens.count { it !in nltkStops }}", 1 - stopShare, NbInk.Green),
        ))), listOf(nbLegend(NbInk.Yellow, "On the list"), nbLegend(NbInk.Green, "Content words")),
            "About {${nbF(stopShare * 100, 0)}%} of running text is stop words.",
            "They are the most frequent words in any corpus and the least informative about topic, so removing them shrinks bag-of-words vectors a lot."),
        NbFrame(listOf(NbBlock.Caption("two reviews after removal"), NbBlock.Toks(kept.map { nbTok(it, NbTone.Good) }, label = "review 1"), NbBlock.Toks(otherKept.map { nbTok(it, NbTone.Good) }, label = "review 2"),
            NbBlock.Callout(listOf("\"${review.joinToString(" ")}\" → {${kept.joinToString(" ")}}", "\"${other.joinToString(" ")}\" → {${otherKept.joinToString(" ")}}"))), listOf(nbLegend(NbInk.Green, "Survives")),
            "A negative and a positive review {become identical}.",
            "After removal both are \"movie good\". Any classifier fed these tokens cannot tell them apart."),
        NbFrame(listOf(head, NbBlock.Toks(review.map { nbTok(it, if (it in negations) NbTone.Good else if (it in nltkStops) NbTone.Bad else NbTone.Good) }),
            NbBlock.Callout(listOf("list − {not, no, nor} → {${custom.joinToString(" ")}}"))), listOf(nbLegend(NbInk.Red, "Removed"), nbLegend(NbInk.Green, "Survives")),
            "Fix: keep {negations} for sentiment.",
            "The list is a default, not a law. Sentiment work drops \"not\", \"no\" and \"nor\" from it; \"movie not good\" survives."),
        NbFrame(listOf(NbBlock.Caption("search: query \"the cast of the film\""), NbBlock.Toks("the cast of the film".split(" ").map { nbTok(it, if (it in nltkStops) NbTone.Bad else NbTone.Good) }),
            NbBlock.Tiles(listOf(NbTile("5 → 2", "query terms to match", NbInk.Green), NbTile("${nltkStops.size}", "words in this list"))), NbBlock.Callout(listOf("index entries for \"the\" ≈ every document"))),
            listOf(nbLegend(NbInk.Red, "Removed"), nbLegend(NbInk.Green, "Matched")),
            "For keyword search, removal {saves work}.",
            "\"the\" and \"of\" appear in nearly every document, so matching them ranks nothing. Dropping them shrinks the index and the query."),
        NbFrame(listOf(NbBlock.Caption("same review · subword tokens for a transformer"), NbBlock.Toks(review.map { nbTok(it) }), NbBlock.Callout(listOf("tokens kept: {${review.size} of ${review.size}}"))), emptyList(),
            "Transformers keep {every token}.",
            "Attention learns how much each word matters in context — including \"not\". Removing stop words before a BERT-style model only deletes information it would have used."),
        NbFrame(listOf(NbBlock.Kv(listOf("bag-of-words, TF-IDF" to "remove — shrinks vectors", "keyword search" to "remove — faster index", "sentiment" to "keep negations", "transformers" to "keep everything"))), emptyList(),
            "Whether to remove them {depends on the model}.",
            "Stop-word lists come from the count-based era. They help models that weigh every word equally and hurt models that learn weights themselves."),
    )
}

// ── Regular expressions ──

private const val REGEX_TEXT = "Dr. Smith's e-mail is a.smith@x.co, the U.S. GDP rose 3.5% on 2024-01-05."

private fun regexSpans(pattern: String): Pair<List<NbSpan>, Int> {
    val spans = mutableListOf<NbSpan>()
    var at = 0
    var k = 0
    Regex(pattern).findAll(REGEX_TEXT).forEach { m ->
        if (m.range.first > at) spans += NbSpan(REGEX_TEXT.substring(at, m.range.first), NbTone.Dim)
        spans += NbSpan(m.value, if (k % 2 == 0) NbTone.Blue else NbTone.Pick)
        at = m.range.last + 1
        k++
    }
    if (at < REGEX_TEXT.length) spans += NbSpan(REGEX_TEXT.substring(at), NbTone.Dim)
    return spans to k
}

private fun regexFrames(): List<NbFrame> {
    val units = listOf("Smith's", "e-mail", "a.smith@x.co", "U.S.", "3.5%", "2024-01-05")
    val p1 = "\\w+"
    val p2 = "\\w+(?:['@.\\-]\\w+)*"
    val p3 = "(?:[A-Z]\\.)+|\\w+(?:['@.\\-]\\w+)*%?"
    val p4 = "Dr\\.|(?:[A-Z]\\.)+|\\w+(?:['@.\\-]\\w+)*%?|[^\\w\\s]"
    val linguist = 12
    fun pieces(u: String, p: String) = Regex(p).findAll(u).count()
    fun splitTable(p: String) = NbBlock.Toks(units.map { u -> val n = pieces(u, p); nbTok("$u  → $n", if (n > 1) NbTone.Hot else NbTone.Good) }, columns = 2, mono = true, start = true)
    val legend = listOf(nbLegend(NbInk.Violet, "Match (alternating shades)"), nbLegend(NbInk.Grey, "Dropped characters"), nbLegend(NbInk.Yellow, "Broken unit"))
    fun frame(p: String, extra: List<NbBlock>, headline: String, body: String, chipsFor: Boolean = true): NbFrame {
        val (spans, count) = regexSpans(p)
        return NbFrame(
            listOf(NbBlock.Caption("$p over one sentence · $count matches"), NbBlock.Text(spans, mono = true)) + extra,
            legend, headline, body,
            chips = if (chipsFor) listOf(nbChip(p.take(14) + if (p.length > 14) "…" else "", "$count"), nbChip("linguist", "$linguist", true)) else emptyList(),
        )
    }
    val n1 = regexSpans(p1).second
    val n2 = regexSpans(p2).second
    val n3 = regexSpans(p3).second
    val n4 = regexSpans(p4).second
    val dates = Regex("\\d{4}-\\d{2}-\\d{2}").findAll(REGEX_TEXT).map { it.value }.toList()
    val emails = Regex("[\\w.]+@\\w+(?:\\.\\w+)+").findAll(REGEX_TEXT).map { it.value }.toList()
    return listOf(
        frame(p1, listOf(NbBlock.Caption("units that got split"), splitTable(p1)),
            "The pattern everyone writes first.",
            "\\w+ finds $n1 tokens where a linguist would count $linguist: every apostrophe, dot, hyphen and @ becomes a split point."),
        frame(p2, listOf(NbBlock.Caption("units that got split"), splitTable(p2)),
            "Allow {inner punctuation}: ${n1} → ${n2} matches.",
            "(?:['@.-]\\w+)* lets a token continue through an apostrophe, hyphen, dot or @ when a word character follows. Smith's, e-mail and the address hold together."),
        frame(p3, listOf(NbBlock.Caption("units that got split"), splitTable(p3)),
            "Abbreviations and percentages: {every unit whole}.",
            "(?:[A-Z]\\.)+ catches U.S. with its final dot, and %? keeps 3.5% together. All six tricky units are one match each."),
        frame(p4, listOf(NbBlock.Callout(listOf("punctuation becomes its own token", "\"Dr.\" listed explicitly — no rule can tell it from a sentence end"))),
            "Punctuation as tokens: {$n4} matches.",
            "Commas and the final period are now kept as tokens, which a parser needs. \"Dr.\" has to be listed by name: a dot after a capitalised word is also how sentences end."),
        NbFrame(listOf(NbBlock.Caption("extraction, not tokenization"), NbBlock.Table(listOf("pattern", "finds"), listOf(1.4f, 1f), listOf(
            NbRow(listOf(nbCell("\\d{4}-\\d{2}-\\d{2}"), nbCell(dates.joinToString(), NbInk.Green))),
            NbRow(listOf(nbCell("[\\w.]+@\\w+(\\.\\w+)+"), nbCell(emails.joinToString(), NbInk.Green))),
        ), listOf(0, 2))), listOf(nbLegend(NbInk.Green, "Extracted")),
            "Regexes shine at {fixed formats}.",
            "Dates, e-mails, phone numbers and IDs follow a grammar a regex can state exactly. Free text doesn't."),
        NbFrame(listOf(NbBlock.Caption("where rules run out"), NbBlock.Kv(listOf("\"Dr.\" vs sentence end" to "needs a list", "\"U.S.\" at sentence end" to "one dot or two?", "don't → do + n't" to "language-specific", "Chinese, Japanese" to "no spaces at all"))), emptyList(),
            "Every regex tokenizer becomes {a list of exceptions}.",
            "That is why modern pipelines learn subword tokenizers (BPE, WordPiece) from data instead — and keep regexes for the formats they handle perfectly.",
            chips = listOf(nbChip("\\w+", "$n1"), nbChip("final pattern", "$n3", true))),
    )
}

// ── Lowercasing and cleaning ──

private fun nfkc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC)

private fun cleaningFrames(): List<NbFrame> {
    val pairs = listOf(
        Triple("Café", "Café", "é vs e + combining accent"),
        Triple("ﬁle", "file", "fi ligature"),
        Triple("１２%", "12%", "full-width digits"),
        Triple("Ｑ3", "Q3", "full-width 3"),
        Triple("Apple’s", "Apple's", "curly vs straight apostrophe"),
    )
    fun pairTable(mapQuotes: Boolean) = NbBlock.Table(
        emptyList(), listOf(1.3f, 0.8f, 0.6f),
        pairs.map { (a, b, why) ->
            var x = nfkc(a)
            var y = nfkc(b)
            if (mapQuotes) { x = x.replace('’', '\''); y = y.replace('’', '\'') }
            val merged = x == y
            NbRow(
                listOf(NbCell("$a · $b", sub = why), nbCell(if (merged) x else "$x · $y", if (merged) NbInk.Green else NbInk.Yellow), nbCell(if (merged) "merged" else "still 2", if (merged) NbInk.Green else NbInk.Yellow, bold = true)),
                tint = if (merged) null else NbInk.Yellow,
            )
        },
    )
    val types0 = pairs.flatMap { listOf(it.first, it.second) }.toSet().size
    val types1 = pairs.flatMap { listOf(nfkc(it.first), nfkc(it.second)) }.toSet().size
    val types2 = pairs.flatMap { listOf(nfkc(it.first), nfkc(it.second)) }.map { it.replace('’', '\'') }.toSet().size
    val legend = listOf(nbLegend(NbInk.Green, "Folded to one form"), nbLegend(NbInk.Yellow, "NFKC leaves it"))
    val raw = "<p>Visit https://shop.ex/deals NOW!!   Prices   from \$5</p>"
    val noHtml = raw.replace(Regex("<[^>]+>"), "")
    val noUrl = noHtml.replace(Regex("https?://\\S+"), "<URL>")
    val spaced = noUrl.replace(Regex("\\s+"), " ").trim()
    val lower = spaced.lowercase()
    val steps = listOf("raw" to raw, "strip HTML" to noHtml, "URL → <URL>" to noUrl, "collapse spaces" to spaced, "lowercase" to lower)
    fun pipeline(upTo: Int) = NbBlock.Table(emptyList(), listOf(0.5f, 1.5f), aligns = listOf(0, 0),
        rows = steps.take(upTo + 1).mapIndexed { i, (k, v) -> NbRow(listOf(nbCell(k), NbCell(v, if (i == upTo) NbInk.Yellow else null)), ring = i == upTo) })
    val caseWords = listOf("US", "us", "Apple", "apple", "WHO", "who")
    return listOf(
        NbFrame(listOf(NbBlock.Caption("5 look-alike pairs · raw strings"), NbBlock.Table(emptyList(), listOf(1.3f, 1f), pairs.map { (a, b, why) ->
            NbRow(listOf(NbCell("$a · $b", sub = why), nbCell(if (a == b) "equal" else "different", if (a == b) NbInk.Green else NbInk.Red, bold = true)))
        })), listOf(nbLegend(NbInk.Red, "Different strings")),
            "Same text to a reader, {different strings} to a program.",
            "Each pair looks identical on screen and fails ==. A vocabulary built on raw strings counts every variant as its own word: $types0 types here.",
            chips = listOf(nbChip("types", "$types0", true))),
        NbFrame(listOf(NbBlock.Caption("5 look-alike pairs · NFKC applied to each"), pairTable(false)), legend,
            "NFKC normalise — first, before anything counts a string.",
            "Compatibility normalisation folds accents, ligatures and full-width forms: $types0 types become $types1. The curly apostrophe needs its own mapping step.",
            chips = listOf(nbChip("types", "$types0 → $types1", true))),
        NbFrame(listOf(NbBlock.Caption("NFKC, then map ’ → '"), pairTable(true)), legend,
            "Punctuation folding is {a separate table}.",
            "Unicode treats ’ and ' as different characters by design, so normalisation keeps them apart. A small mapping of quotes and dashes finishes the job: $types1 → $types2 types.",
            chips = listOf(nbChip("types", "$types0 → $types2", true))),
        NbFrame(listOf(NbBlock.Caption("a scraped snippet · step by step"), pipeline(1)), listOf(nbLegend(NbInk.Yellow, "Current step")),
            "Strip markup {before} tokenizing.",
            "Tags like <p> are page structure, not text. Left in, they become tokens that appear in every document."),
        NbFrame(listOf(NbBlock.Caption("a scraped snippet · step by step"), pipeline(2)), listOf(nbLegend(NbInk.Yellow, "Current step")),
            "Replace URLs with {a placeholder}.",
            "Every URL is unique, so each would be its own rare token. <URL> keeps the fact that a link was there without the vocabulary cost."),
        NbFrame(listOf(NbBlock.Caption("a scraped snippet · step by step"), pipeline(3)), listOf(nbLegend(NbInk.Yellow, "Current step")),
            "Collapse whitespace.",
            "Runs of spaces, tabs and newlines become one space, so splitting on spaces never produces empty tokens."),
        NbFrame(listOf(NbBlock.Caption("a scraped snippet · step by step"), pipeline(4)), listOf(nbLegend(NbInk.Yellow, "Current step")),
            "Lowercase: \"NOW\" and \"now\" {become one word}.",
            "For counting models this halves the variants of every sentence-initial word. It also erases information, which the next step shows."),
        NbFrame(listOf(NbBlock.Caption("lowercasing merges these pairs"), NbBlock.Table(listOf("raw", "lower", "meaning lost"), listOf(0.6f, 0.6f, 1.4f),
            caseWords.chunked(2).map { (a, b) -> NbRow(listOf(nbName(a), nbCell(a.lowercase(), NbInk.Red), NbCell(when (a) { "US" -> "country vs pronoun"; "Apple" -> "company vs fruit"; else -> "organisation vs pronoun" }, mono = false))) }, listOf(0, 0, 0))),
            listOf(nbLegend(NbInk.Red, "Two words collapse")),
            "Lowercasing {deletes} entities.",
            "\"US\" and \"us\", \"Apple\" and \"apple\": case was carrying the meaning. NER and cased transformers keep case; bag-of-words models usually drop it."),
        NbFrame(listOf(NbBlock.Kv(listOf("1 · normalise" to "NFKC + quote/dash map", "2 · strip" to "HTML, control characters", "3 · placeholders" to "URLs, e-mails, numbers", "4 · whitespace" to "collapse", "5 · case" to "only if the model ignores it"))), emptyList(),
            "Order matters: {normalise first}.",
            "Every later step compares strings. Doing them before NFKC means a full-width URL or a ligature slips through every regex."),
    )
}

// ── Bag-of-words / TF-IDF ──

private fun bowFrames(): List<NbFrame> {
    val d1 = "the cat sat on the mat".split(" ")
    val d2 = "the dog sat on the log".split(" ")
    val vocab = listOf("the", "cat", "sat", "on", "mat", "dog", "log")
    val c1 = vocab.map { w -> d1.count { it == w } }
    val c2 = vocab.map { w -> d2.count { it == w } }
    val df = vocab.indices.map { (if (c1[it] > 0) 1 else 0) + (if (c2[it] > 0) 1 else 0) }
    val n = 2.0
    val idf = df.map { ln((1 + n) / (1 + it)) + 1 }
    val t1 = c1.indices.map { c1[it] * idf[it] }
    val t2 = c2.indices.map { c2[it] * idf[it] }
    val top = (t1 + t2).max()
    fun countGrid(hot: Int?) = NbBlock.Grid(
        vocab, listOf("doc 1", "doc 2", "df"),
        listOf(
            c1.map { NbGCell("$it", if (it == 0) 0.0 else 0.35 + 0.3 * it, NbInk.Blue) },
            c2.map { NbGCell("$it", if (it == 0) 0.0 else 0.35 + 0.3 * it, NbInk.Blue) },
            df.map { NbGCell("$it", 0.4 + 0.25 * it, NbInk.Violet) },
        ),
        hotRow = hot,
    )
    fun tfidfGrid() = NbBlock.Grid(
        vocab, listOf("doc 1", "doc 2"),
        listOf(t1.map { NbGCell(f2(it), if (it == 0.0) 0.0 else it / top, NbInk.Blue) }, t2.map { NbGCell(f2(it), if (it == 0.0) 0.0 else it / top, NbInk.Blue) }),
    )
    val legend = listOf(nbLegend(NbInk.Blue, "Term count"), nbLegend(NbInk.Violet, "Document frequency"))
    fun cos(a: List<Double>, b: List<Double>) = a.indices.sumOf { a[it] * b[it] } / (sqrt(a.sumOf { it * it }) * sqrt(b.sumOf { it * it }))
    val cosCount = cos(c1.map { it.toDouble() }, c2.map { it.toDouble() })
    val cosTfidf = cos(t1, t2)
    return listOf(
        NbFrame(listOf(NbBlock.Caption("vocabulary from both documents · ${vocab.size} terms"), NbBlock.Toks(d1.map { nbTok(it, NbTone.Hot) }), NbBlock.Toks(d2.map { nbTok(it) }),
            NbBlock.Toks(vocab.map { nbTok(it, NbTone.Pick) })), listOf(nbLegend(NbInk.Yellow, "Document 1"), nbLegend(NbInk.Violet, "Vocabulary")),
            "Build the vocabulary from {every document}.",
            "Each distinct word gets one column. Two six-word sentences share four words, so the vocabulary has ${vocab.size} entries."),
        NbFrame(listOf(NbBlock.Caption("vocabulary from both documents · ${vocab.size} terms"), NbBlock.Toks(d1.map { nbTok(it, NbTone.Hot) }), countGrid(0)), legend,
            "Document 1 as counts.",
            "the = 2, cat = 1, sat = 1, on = 1, mat = 1. The df row previews TF-IDF: words in both documents will be weighted down next.",
            fx = listOf("doc 1 = [${c1.joinToString(", ")}] · ${d1.size} tokens, ${d1.toSet().size} distinct", "order is gone: \"mat sat the on the cat\" → same vector")),
        NbFrame(listOf(NbBlock.Caption("vocabulary from both documents · ${vocab.size} terms"), NbBlock.Toks(d2.map { nbTok(it, NbTone.Hot) }), countGrid(1)), legend,
            "Document 2: four columns {overlap}.",
            "the, sat and on appear in both. By raw counts the two documents look ${nbF(cosCount * 100, 0)}% similar (cosine), mostly because of \"the\".",
            chips = listOf(nbChip("cosine, counts", f2(cosCount), true))),
        NbFrame(listOf(NbBlock.Caption("inverse document frequency · N = 2, smoothed"), NbBlock.Bars(vocab.indices.map { NbBar(vocab[it], f2(idf[it]), idf[it] / idf.max(), if (df[it] == 2) NbInk.Grey else NbInk.Violet) }, labelWidth = 56)),
            listOf(nbLegend(NbInk.Violet, "In one document"), nbLegend(NbInk.Grey, "In both")),
            "IDF rewards {rare} words.",
            "idf = ln((1 + N) / (1 + df)) + 1. A word in every document gets the floor of 1; one that singles out a document gets ${f2(idf[1])}.",
            fx = listOf("idf(the) = ln(3 / 3) + 1 = {${f2(idf[0])}}", "idf(cat) = ln(3 / 2) + 1 = {${f2(idf[1])}}")),
        NbFrame(listOf(NbBlock.Caption("TF-IDF = count × idf"), tfidfGrid()), listOf(nbLegend(NbInk.Blue, "TF-IDF weight")),
            "\"the\" is still counted twice — but {cat and mat now stand out}.",
            "Words that distinguish the documents get heavier; shared ones stay at their count. Cosine similarity drops from ${f2(cosCount)} to ${f2(cosTfidf)}.",
            chips = listOf(nbChip("cosine, counts", f2(cosCount)), nbChip("cosine, TF-IDF", f2(cosTfidf), true))),
        NbFrame(listOf(NbBlock.Caption("what the vector cannot see"), NbBlock.Callout(listOf("\"dog bites man\" = [1, 1, 1]", "\"man bites dog\" = [1, 1, 1]", "→ {identical} vectors")), NbBlock.Tiles(listOf(NbTile("${vocab.size}", "dimensions here"), NbTile("~10⁵", "in a real corpus", NbInk.Red)))),
            emptyList(),
            "A bag of words {forgets order} and grows with the vocabulary.",
            "Fast, interpretable and still a strong baseline for search and classification. Word embeddings fix the sparsity; sequence models fix the order."),
    )
}

// ── Cosine similarity ──

private fun cosineFrames(): List<NbFrame> {
    val query = NbP(2.0, 1.0)
    val docs = listOf(Triple("short", NbP(2.0, 1.2), NbInk.Sky), Triple("long", NbP(8.0, 5.0), NbInk.Orange), Triple("theory", NbP(1.0, 3.0), NbInk.Green))
    fun cos(a: NbP, b: NbP) = (a.x * b.x + a.y * b.y) / (hypot(a.x, a.y) * hypot(b.x, b.y))
    fun dist(a: NbP, b: NbP) = hypot(a.x - b.x, a.y - b.y)
    val cosRank = docs.sortedByDescending { cos(query, it.second) }.map { it.first }
    val distRank = docs.sortedBy { dist(query, it.second) }.map { it.first }
    fun plot(show: Set<String>, distances: Boolean = false, angle: String? = null) = NbBlock.Plot(
        0.0 to 9.0, 0.0 to 6.0, 190,
        lines = listOf(NbLine(listOf(NbP(0.0, 0.0), query), NbInk.Yellow, width = 2.5f)) +
            docs.filter { it.first in show }.map { NbLine(listOf(NbP(0.0, 0.0), it.second), it.third, width = 2.5f) },
        dots = listOf(NbDot(query, NbInk.Yellow, 4.5f, label = "query")) + docs.filter { it.first in show }.map { NbDot(it.second, it.third, 4.5f, label = it.first) },
        segs = if (distances) docs.filter { it.first in show }.map { NbSeg(query, it.second, NbInk.Grey, dashed = true, width = 1.2f) } else emptyList(),
        xTicks = listOf(0.0 to "0", 3.0 to "3", 6.0 to "6", 9.0 to "9"),
        yTicks = listOf(0.0 to "0", 2.0 to "2", 4.0 to "4", 6.0 to "6"),
        xLabel = "\"learning\" →", yLabel = "↑ \"proof\"",
    )
    fun table(ring: String?) = NbBlock.Table(
        listOf("doc", "cosine", "#", "distance", "#"), listOf(1f, 0.8f, 0.3f, 0.8f, 0.3f),
        docs.map { (name, p, ink) ->
            val cr = cosRank.indexOf(name) + 1
            val dr = distRank.indexOf(name) + 1
            NbRow(listOf(nbName(name, ink), nbCell(nbF(cos(query, p), 3)), nbCell("$cr", if (cr == dr) NbInk.Green else NbInk.Red), nbCell(f2(dist(query, p))), nbCell("$dr", if (cr == dr) NbInk.Green else NbInk.Red)), ring = name == ring)
        },
    )
    val legend = listOf(nbLegend(NbInk.Yellow, "Query"), nbLegend(NbInk.Sky, "short"), nbLegend(NbInk.Orange, "long"), nbLegend(NbInk.Green, "theory"))
    val head = NbBlock.Caption("term space · counts of two words per document")
    val cLong = cos(query, docs[1].second)
    return listOf(
        NbFrame(listOf(head, plot(emptySet())), listOf(nbLegend(NbInk.Yellow, "Query")),
            "A document is {an arrow} in term space.",
            "Each axis counts one word. The query mentions \"learning\" twice and \"proof\" once, so it points to (2, 1).",
            fx = listOf("query = (2, 1)")),
        NbFrame(listOf(head, plot(setOf("short"))), legend.take(2),
            "\"short\" points {almost the same way}.",
            "(2, 1.2) is nearly parallel to the query: same topic, slightly more \"proof\".",
            fx = listOf("cos = (2·2 + 1·1.2) / (|q|·|d|) = {${nbF(cos(query, docs[0].second), 3)}}")),
        NbFrame(listOf(head, plot(setOf("short", "long"))), legend.take(3),
            "\"long\" is four times bigger — {same direction}.",
            "(8, 5) is the same mix of words written at length. Cosine ignores length, so it scores ${nbF(cLong, 3)}.",
            fx = listOf("cos(query, long) = {${nbF(cLong, 3)}}")),
        NbFrame(listOf(head, plot(setOf("short", "long", "theory"))), legend,
            "\"theory\" leans toward the other word.",
            "(1, 3) is mostly \"proof\". The angle to the query is 45°, cosine ${nbF(cos(query, docs[2].second), 3)}.",
            fx = listOf("cos(query, theory) = 5 / (√5·√10) = {${nbF(cos(query, docs[2].second), 3)}}")),
        NbFrame(listOf(head, plot(setOf("short", "long", "theory"), distances = true), table("long")), legend + nbLegend(NbInk.Grey, "Distance", SwatchStyle.DashedLine),
            "Now rank the corpus against a query.",
            "\"long\" points the query's way — cosine ranks it ${cosRank.indexOf("long") + 1}${if (cosRank.indexOf("long") == 1) "nd" else "th"} — but distance ranks it last just for being long. That flip is why retrieval uses cosine."),
        NbFrame(listOf(NbBlock.Caption("normalise first · every vector to length 1"), NbBlock.Table(listOf("doc", "unit vector", "distance"), listOf(0.8f, 1.2f, 0.7f), docs.map { (name, p, ink) ->
            val l = hypot(p.x, p.y)
            val u = NbP(p.x / l, p.y / l)
            val q = NbP(query.x / hypot(query.x, query.y), query.y / hypot(query.x, query.y))
            NbRow(listOf(nbName(name, ink), nbCell("(${f2(u.x)}, ${f2(u.y)})"), nbCell(nbF(dist(q, u), 3))))
        })), legend.drop(1),
            "On unit vectors, distance and cosine {agree}.",
            "|a − b|² = 2 − 2·cos(a, b) when both have length 1. Vector databases normalise once and then use whichever is faster."),
        NbFrame(listOf(NbBlock.Kv(listOf("range" to "−1 to 1 (0 to 1 for counts)", "ignores" to "length — how much is written", "measures" to "direction — what it is about", "used in" to "search, embeddings, deduplication"))), emptyList(),
            "Cosine compares {what} a document says, not how much.",
            "That is the right notion for text, where a long article and a short note on the same topic should match."),
    )
}

// ── Jaccard ──

private fun jaccardFrames(): List<NbFrame> {
    val a = "the model learns from data and more data".split(" ")
    val b = "the model learns from data".split(" ")
    val sa = a.toSet()
    val sb = b.toSet()
    val inter = sa intersect sb
    val union = sa union sb
    val j = inter.size.toDouble() / union.size
    fun aToks(): NbBlock.Toks {
        val seen = mutableSetOf<String>()
        return NbBlock.Toks(a.map { w -> if (!seen.add(w)) nbTok(w, NbTone.Dim, "dup") else nbTok(w, if (w in sb) NbTone.Pick else NbTone.Hot) })
    }
    val bToks = NbBlock.Toks(b.map { nbTok(it, NbTone.Pick) })
    val counts = NbBlock.Tiles(listOf(NbTile("${sa.size}", "|A|"), NbTile("${sb.size}", "|B|"), NbTile("${inter.size}", "|A ∩ B|", NbInk.Indigo), NbTile("${union.size}", "|A ∪ B|")))
    val legend = listOf(nbLegend(NbInk.Violet, "Shared"), nbLegend(NbInk.Yellow, "Only in A"), nbLegend(NbInk.Slate, "Repeat — ignored"))
    val head = NbBlock.Caption("two sentences as sets")
    val c = "the model learns".split(" ").toSet()
    val d = "a network is trained".split(" ").toSet()
    val jcd = (c intersect d).size.toDouble() / (c union d).size
    fun shingles(s: String) = s.windowed(3).toSet()
    val s1 = shingles("learning")
    val s2 = shingles("learner")
    val js = (s1 intersect s2).size.toDouble() / (s1 union s2).size
    return listOf(
        NbFrame(listOf(head, NbBlock.Caption("A · ${a.size} tokens"), aToks(), NbBlock.Caption("B · ${b.size} tokens"), bToks), legend,
            "Treat each sentence as {a set of words}.",
            "Order and counts are dropped; only which words appear matters. A has ${a.size} tokens but ${sa.size} distinct words."),
        NbFrame(listOf(head, NbBlock.Caption("A · ${a.size} tokens"), aToks(), NbBlock.Caption("B · ${b.size} tokens"), bToks, counts,
            NbBlock.Banner(nbF(j, 3), "J = ${inter.size} / ${union.size}", NbInk.Violet, bigRight = true)), legend,
            "J(A, B) = |A ∩ B| / |A ∪ B|.",
            "\"data\" appears twice in A and counts once. The repetition is invisible to the set — that is the design, not an oversight."),
        NbFrame(listOf(NbBlock.Caption("same meaning, no shared words"), NbBlock.Toks(c.map { nbTok(it, NbTone.Hot) }, label = "C"), NbBlock.Toks(d.map { nbTok(it, NbTone.Hot) }, label = "D"),
            NbBlock.Banner(nbF(jcd, 3), "J = 0 / ${(c union d).size}", NbInk.Red, bigRight = true)), listOf(nbLegend(NbInk.Yellow, "No overlap")),
            "Paraphrases score {zero}.",
            "\"the model learns\" and \"a network is trained\" say the same thing with different words. Jaccard only sees surface overlap."),
        NbFrame(listOf(NbBlock.Caption("character 3-grams · \"learning\" vs \"learner\""), NbBlock.Toks(s1.map { nbTok(it, if (it in s2) NbTone.Pick else NbTone.Hot) }, mono = true),
            NbBlock.Toks(s2.map { nbTok(it, if (it in s1) NbTone.Pick else NbTone.Hot) }, mono = true),
            NbBlock.Banner(nbF(js, 3), "J = ${(s1 intersect s2).size} / ${(s1 union s2).size}", NbInk.Violet, bigRight = true)), legend.take(2),
            "On character shingles it {catches spelling variants}.",
            "Splitting words into 3-grams gives typos and inflections partial credit — the basis of fuzzy matching."),
        NbFrame(listOf(NbBlock.Caption("near-duplicate detection"), NbBlock.Kv(listOf("each document" to "set of 5-word shingles", "MinHash" to "estimates J without comparing sets", "threshold" to "J > 0.8 → duplicate", "used for" to "deduplicating training corpora"))), emptyList(),
            "Its real home: {finding near-duplicates} at scale.",
            "Web-scale corpora are deduplicated by Jaccard on shingles, estimated with MinHash so billions of pairs never have to be compared directly."),
        NbFrame(listOf(NbBlock.Table(listOf("", "Jaccard", "cosine"), listOf(0.8f, 1f, 1f), listOf(
            NbRow(listOf(nbName("input"), nbCell("sets"), nbCell("vectors (counts)"))),
            NbRow(listOf(nbName("counts"), nbCell("ignored", NbInk.Yellow), nbCell("used", NbInk.Green))),
            NbRow(listOf(nbName("length"), nbCell("hurts (bigger union)"), nbCell("ignored", NbInk.Green))),
            NbRow(listOf(nbName("A vs B"), nbCell(nbF(j, 3), NbInk.Violet), nbCell(nbF(run {
                val vocab = union.toList()
                val va = vocab.map { w -> a.count { it == w }.toDouble() }
                val vb = vocab.map { w -> b.count { it == w }.toDouble() }
                va.indices.sumOf { va[it] * vb[it] } / (sqrt(va.sumOf { it * it }) * sqrt(vb.sumOf { it * it }))
            }, 3), NbInk.Violet))),
        ))), emptyList(),
            "Jaccard for sets, {cosine} for weighted vectors.",
            "On the same two sentences cosine also counts the repeated \"data\" — the measures agree on the ranking but not the scale."),
    )
}

// ── Hidden Markov models ──

private val hmmTags = listOf("NN", "VB", "DT", "IN")
private val hmmWords = listOf("book", "that", "flight")
private val hmmPi = doubleArrayOf(0.5, 0.2, 0.2, 0.1)
private val hmmA = arrayOf(
    doubleArrayOf(0.2, 0.3, 0.1, 0.4),
    doubleArrayOf(0.1, 0.05, 0.6, 0.25),
    doubleArrayOf(0.9, 0.05, 0.0, 0.05),
    doubleArrayOf(0.3, 0.05, 0.6, 0.05),
)
private val hmmB = arrayOf(
    doubleArrayOf(0.03, 0.0, 0.02),
    doubleArrayOf(0.04, 0.0, 0.0),
    doubleArrayOf(0.0, 0.15, 0.0),
    doubleArrayOf(0.0, 0.25, 0.0),
)

private fun sci(v: Double): String = if (v == 0.0) "0" else lsSci(v)

private fun hmmFrames(): List<NbFrame> {
    val k = hmmTags.size
    val t = hmmWords.size
    val v = Array(t) { DoubleArray(k) }
    val back = Array(t) { IntArray(k) }
    for (s in 0 until k) v[0][s] = hmmPi[s] * hmmB[s][0]
    for (i in 1 until t) for (s in 0 until k) {
        var best = 0.0
        var arg = 0
        for (p in 0 until k) { val c = v[i - 1][p] * hmmA[p][s]; if (c > best) { best = c; arg = p } }
        v[i][s] = best * hmmB[s][i]
        back[i][s] = arg
    }
    var last = (0 until k).maxBy { v[t - 1][it] }
    val path = IntArray(t)
    path[t - 1] = last
    for (i in t - 1 downTo 1) { last = back[i][last]; path[i - 1] = last }
    // Greedy: best tag at each word given only the previous choice.
    val greedy = IntArray(t)
    var gScore = 1.0
    for (i in 0 until t) {
        val scores = (0 until k).map { s -> (if (i == 0) hmmPi[s] else hmmA[greedy[i - 1]][s]) * hmmB[s][i] }
        greedy[i] = scores.indices.maxBy { scores[it] }
        gScore *= scores[greedy[i]]
    }
    val best = v[t - 1][path[t - 1]]
    val bMax = hmmB.maxOf { it.max() }
    val emission = NbBlock.Grid(hmmWords, hmmTags, hmmB.mapIndexed { s, row -> row.mapIndexed { w, p -> NbGCell(if (p == 0.0) "0" else nbF(p, 3), if (p == 0.0) 0.0 else 0.3 + 0.7 * p / bMax, NbInk.Blue, ring = w == 0 && p > 0) } })
    val aMax = hmmA.maxOf { it.max() }
    val transition = NbBlock.Grid(hmmTags, hmmTags.map { "$it →" }, hmmA.map { row -> row.map { p -> NbGCell(f2(p), if (p == 0.0) 0.0 else 0.25 + 0.7 * p / aMax, NbInk.Violet) } }, cellHeight = 26)
    fun lattice(upTo: Int, mark: IntArray?) = NbBlock.Grid(
        hmmWords, hmmTags,
        (0 until k).map { s -> (0 until t).map { i -> if (i > upTo) NbGCell("", 0.0) else NbGCell(sci(v[i][s]), if (v[i][s] == 0.0) 0.0 else 0.35 + 0.6 * (v[i][s] / v[i].max()), NbInk.Violet, ring = mark?.get(i) == s) } },
    )
    val candidates = NbBlock.Table(hmmWords, hmmWords.map { 1f }, listOf(NbRow(hmmWords.indices.map { w -> nbCell(hmmTags.filterIndexed { s, _ -> hmmB[s][w] > 0 }.joinToString(" · ")) })), listOf(1, 1, 1))
    val tagStr = { p: IntArray -> p.joinToString(" ") { hmmTags[it] } }
    return listOf(
        NbFrame(listOf(NbBlock.Caption("\"book that flight\" · the tags are hidden"), NbBlock.Toks(hmmWords.map { nbTok(it, NbTone.Plain, "?") }), NbBlock.Caption("tags each word can take (B > 0)"), candidates),
            emptyList(),
            "Words are observed; {tags are hidden}.",
            "\"book\" can be a noun or a verb, \"that\" a determiner or a preposition. An HMM picks the tag sequence that best explains the words."),
        NbFrame(listOf(NbBlock.Caption("emission table B(tag → word) · from a tagged corpus"), emission, NbBlock.Caption("tags each word can take (B > 0)"), candidates),
            listOf(nbLegend(NbInk.Blue, "P(word | tag)"), nbLegend(NbInk.Yellow, "Comparison in caption", SwatchStyle.Ring)),
            "The model is two tables estimated by counting.",
            "This is the emission half. P(book | VB) beats P(book | NN): verbs are rarer, so when one appears it's more likely to be this word. Transitions come next."),
        NbFrame(listOf(NbBlock.Caption("transition table A(previous → next) and start π"), transition, NbBlock.Callout(listOf("π = " + hmmTags.indices.joinToString(" · ") { "${hmmTags[it]} ${f2(hmmPi[it])}" }))),
            listOf(nbLegend(NbInk.Violet, "P(tag | previous tag)")),
            "The other half: {which tag follows which}.",
            "A determiner is followed by a noun 90% of the time. Sentences usually start with a noun here (π = 0.5), which is what misleads a greedy tagger."),
        NbFrame(listOf(NbBlock.Caption("greedy · best tag per word, left to right"), NbBlock.Toks(hmmWords.indices.map { nbTok(hmmWords[it], NbTone.Hot, hmmTags[greedy[it]]) }),
            NbBlock.Callout(listOf("book: NN ${nbF(hmmPi[0] * hmmB[0][0], 3)} vs VB ${nbF(hmmPi[1] * hmmB[1][0], 3)} → {NN}", "score = {${sci(gScore)}}"))),
            listOf(nbLegend(NbInk.Yellow, "Greedy choice")),
            "Greedy commits to {${tagStr(greedy)}}.",
            "At \"book\", the strong noun start wins locally (0.015 vs 0.008). Every later choice is stuck with it."),
        NbFrame(listOf(NbBlock.Caption("Viterbi lattice · best path probability into each cell"), lattice(0, null)), listOf(nbLegend(NbInk.Violet, "v(t, tag)")),
            "Viterbi keeps {every tag} alive at each word.",
            "Column 1 is π × B. Nothing is decided yet — VB stays in the running with ${sci(v[0][1])}.",
            fx = listOf("v(book, VB) = π(VB)·B(VB, book) = 0.2 × 0.04 = {${sci(v[0][1])}}")),
        NbFrame(listOf(NbBlock.Caption("Viterbi lattice · best path probability into each cell"), lattice(2, null)), listOf(nbLegend(NbInk.Violet, "v(t, tag)")),
            "Each cell takes {the best way in}.",
            "v(t, s) = max over previous tags of v(t − 1, p)·A(p, s), times B(s, word). Back-pointers remember which p won.",
            fx = listOf("v(that, DT) = max(…, v(book, VB)·0.6)·0.15 = {${sci(v[1][2])}}")),
        NbFrame(listOf(NbBlock.Caption("Viterbi lattice · best path ringed"), lattice(2, path),
            NbBlock.Tiles(listOf(NbTile(tagStr(path), "Viterbi · ${sci(best)}", NbInk.Green), NbTile(tagStr(greedy), "greedy · ${sci(gScore)}", NbInk.Red)))),
            listOf(nbLegend(NbInk.Violet, "v(t, tag)"), nbLegend(NbInk.Yellow, "Best path", SwatchStyle.Ring)),
            "Follow the back-pointers: {${tagStr(path)}}.",
            "The full path beats greedy's by ${nbF(best / gScore, 2)}×. Viterbi is exact and costs T·K² — ${t}·${k}² = ${t * k * k} steps here instead of ${k}^${t} = ${Math.round(Math.pow(k.toDouble(), t.toDouble()))} paths."),
    )
}

// ── Word embeddings ──

private val embDims = listOf("royal", "male", "female", "human", "fruit")
private val embWords = listOf("king", "queen", "man", "woman", "apple")
private val embVec = listOf(
    doubleArrayOf(0.95, 0.90, 0.05, 0.80, 0.00),
    doubleArrayOf(0.95, 0.05, 0.90, 0.80, 0.00),
    doubleArrayOf(0.10, 0.90, 0.05, 0.90, 0.00),
    doubleArrayOf(0.10, 0.05, 0.90, 0.90, 0.00),
    doubleArrayOf(0.00, 0.00, 0.00, 0.05, 0.95),
)

private fun cosV(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { a[it] * b[it] } / (sqrt(a.sumOf { it * it }) * sqrt(b.sumOf { it * it }))

private fun embeddingFrames(): List<NbFrame> {
    fun grid(hot: Int? = null, extra: Pair<String, DoubleArray>? = null) = NbBlock.Grid(
        embDims, embWords + listOfNotNull(extra?.first),
        (embVec + listOfNotNull(extra?.second)).map { v -> v.map { NbGCell(nbDot2(it), it.coerceAtLeast(0.0), NbInk.Blue) } },
        hotRow = hot,
    )
    val king = embVec[0]
    val sims = (1 until 5).map { embWords[it] to cosV(king, embVec[it]) }.sortedByDescending { it.second }
    val simBars = NbBlock.Bars(sims.map { NbBar(it.first, f2(it.second), it.second, NbInk.Violet) }, labelWidth = 64)
    val target = DoubleArray(5) { king[it] - embVec[2][it] + embVec[3][it] }
    val ranked = embWords.indices.filter { it !in setOf(0, 2, 3) }.map { embWords[it] to cosV(target, embVec[it]) }.sortedByDescending { it.second }
    val oneHot = embWords.indices.map { i -> DoubleArray(5) { if (it == i) 1.0 else 0.0 } }
    return listOf(
        NbFrame(listOf(NbBlock.Caption("5 words × 5 dimensions"), grid(0), NbBlock.Caption("cosine similarity to \"king\""), simBars),
            listOf(nbLegend(NbInk.Blue, "Dimension value"), nbLegend(NbInk.Violet, "Similarity")),
            "Each word is a dense vector.",
            "Words that share dimensions point the same way: queen and man each share two of king's three strong features; apple shares none. Labels are for readability — trained dimensions have no names."),
        NbFrame(listOf(NbBlock.Caption("one-hot · each word its own axis"), NbBlock.Grid(embWords, embWords, oneHot.map { v -> v.map { NbGCell(if (it == 1.0) "1" else "0", it, NbInk.Grey) } }),
            NbBlock.Callout(listOf("cos(king, queen) = {0}", "cos(king, apple) = {0}"))), listOf(nbLegend(NbInk.Grey, "One-hot")),
            "One-hot vectors make {every pair equally unrelated}.",
            "With one axis per word, any two different words are orthogonal. Dense vectors share axes, so similarity can be graded."),
        NbFrame(listOf(NbBlock.Caption("king − man + woman"), grid(5, "result" to target),
            NbBlock.Bars(ranked.map { NbBar(it.first, f2(it.second), it.second.coerceAtLeast(0.0), if (it == ranked.first()) NbInk.Green else NbInk.Violet) }, labelWidth = 64)),
            listOf(nbLegend(NbInk.Blue, "Dimension value"), nbLegend(NbInk.Green, "Nearest remaining word")),
            "Vector arithmetic: king − man + woman ≈ {m:${ranked.first().first}}.",
            "Subtracting man removes \"male\" and adds nothing royal; adding woman puts \"female\" back. The nearest word left is ${ranked.first().first} (cosine ${f2(ranked.first().second)})."),
        NbFrame(listOf(NbBlock.Kv(listOf("this toy" to "5 hand-set, named dimensions", "word2vec / GloVe" to "100–300 learned dimensions", "dimensions" to "unnamed, rotated, mixed", "where it comes from" to "words in similar contexts"))), emptyList(),
            "Real embeddings are {learned from context}.",
            "Nobody sets the numbers. Training nudges words that appear in similar contexts toward similar vectors — CBOW and skip-gram are the two classic ways."),
    )
}

// ── Word2Vec: one sentence, a 4-word vocabulary, a model trained here ──

private val w2vSentence = listOf("the", "king", "rules", "the", "kingdom")
private val w2vVocab = listOf("the", "king", "rules", "kingdom")

private class W2vRng(seed: Long) {
    private var s = seed
    fun u(): Double { s = (s * 1103515245L + 12345L) and 0x7fffffffL; return s / 2147483648.0 }
}

/** CBOW examples: the context words within ±2 and the centre word. */
private fun cbowExamples() = w2vSentence.indices.map { c ->
    val ctx = (c - 2..c + 2).filter { it != c && it in w2vSentence.indices }.map { w2vSentence[it] }
    ctx to w2vSentence[c]
}

/** Skip-gram pairs: centre → each context word, with its offset. */
private fun skipPairs() = w2vSentence.indices.flatMap { c -> (c - 2..c + 2).filter { it != c && it in w2vSentence.indices }.map { Triple(w2vSentence[c], w2vSentence[it], it - c) } }

private class W2v(val inV: Array<DoubleArray>, val outV: Array<DoubleArray>) {
    fun probs(h: DoubleArray): DoubleArray {
        val s = DoubleArray(outV.size) { o -> h.indices.sumOf { h[it] * outV[o][it] } }
        val m = s.max()
        val e = s.map { exp(it - m) }
        val z = e.sum()
        return DoubleArray(s.size) { e[it] / z }
    }
    fun hidden(ctx: List<String>): DoubleArray {
        val ids = ctx.map { w2vVocab.indexOf(it) }
        return DoubleArray(inV[0].size) { d -> ids.sumOf { inV[it][d] } / ids.size }
    }
}

private fun w2vInit(): W2v {
    val r = W2vRng(5)
    return W2v(Array(4) { DoubleArray(3) { (r.u() - 0.5) * 0.2 } }, Array(4) { DoubleArray(3) { (r.u() - 0.5) * 0.2 } })
}

/** Full-softmax SGD over the examples; [cbow] averages the context, otherwise each pair is its own example. */
private fun w2vTrain(cbow: Boolean, epochs: Int, rate: Double = 0.5): W2v {
    val m = w2vInit()
    val examples: List<Pair<List<String>, String>> = if (cbow) cbowExamples() else skipPairs().map { listOf(it.first) to it.second }
    repeat(epochs) {
        examples.forEach { (ctx, target) ->
            val h = m.hidden(ctx)
            val p = m.probs(h)
            val t = w2vVocab.indexOf(target)
            val grad = DoubleArray(3)
            for (o in 0 until 4) {
                val e = p[o] - if (o == t) 1.0 else 0.0
                for (d in 0 until 3) { grad[d] += e * m.outV[o][d]; m.outV[o][d] -= rate * e * h[d] }
            }
            ctx.forEach { w -> val i = w2vVocab.indexOf(w); for (d in 0 until 3) m.inV[i][d] -= rate * grad[d] / ctx.size }
        }
    }
    return m
}

private fun cbowFrames(): List<NbFrame> {
    val ex = cbowExamples()
    val head = NbBlock.Caption("\"the king rules the kingdom\" · window ±2")
    fun sentence(centre: Int) = NbBlock.Toks(w2vSentence.mapIndexed { i, w -> if (i == centre) nbTok("_____", NbTone.Empty) else nbTok(w, if (kotlin.math.abs(i - centre) <= 2) NbTone.Hot else NbTone.Plain) })
    fun table(ring: Int) = NbBlock.Table(listOf("context (input)", "target"), listOf(1.6f, 0.7f), ex.mapIndexed { i, (c, t) -> NbRow(listOf(nbName(c.joinToString(", ")), nbCell(t, if (i == ring) NbInk.Indigo else null)), ring = i == ring) })
    val flow = NbBlock.Flow(listOf(listOf(nbTok("the", NbTone.Hot), nbTok("king", NbTone.Hot)), listOf(nbTok("the", NbTone.Hot), nbTok("kingdom", NbTone.Hot))), "average\n→ softmax", nbTok("rules", NbTone.Pick))
    val legend = listOf(nbLegend(NbInk.Yellow, "Context"), nbLegend(NbInk.Violet, "Target"))
    val m0 = w2vInit()
    val trained = w2vTrain(true, 200)
    val ctx = ex[2].first
    val p0 = m0.probs(m0.hidden(ctx))
    val p1 = trained.probs(trained.hidden(ctx))
    val ri = w2vVocab.indexOf("rules")
    fun probBars(p: DoubleArray) = NbBlock.Bars(w2vVocab.indices.map { NbBar(w2vVocab[it], f2(p[it]), p[it], if (it == ri) NbInk.Violet else NbInk.Grey) }, labelWidth = 72)
    val h = trained.hidden(ctx)
    fun lossOf(m: W2v) = ex.sumOf { (c, t) -> -ln(m.probs(m.hidden(c))[w2vVocab.indexOf(t)]) } / ex.size
    val loss0 = lossOf(m0)
    val loss1 = lossOf(trained)
    val mid = w2vTrain(true, 20)
    return listOf(
        NbFrame(listOf(head, sentence(2), flow, NbBlock.Caption("all ${ex.size} training examples from this sentence"), table(2)), legend,
            "CBOW turns the corpus into fill-in-the-blank.",
            "Hide the centre word and guess it from its window. The text labels itself, so no annotation is needed."),
        NbFrame(listOf(head, sentence(0), table(0)), legend,
            "Near the edge the window is {shorter}.",
            "\"the\" at position 1 has only two words to its right. CBOW averages however many context words there are, so every position yields one example."),
        NbFrame(listOf(NbBlock.Caption("context vectors averaged · 3 dimensions"), NbBlock.Table(listOf("word", "v₁", "v₂", "v₃"), listOf(1f, 0.6f, 0.6f, 0.6f),
            ctx.distinct().map { w -> val v = trained.inV[w2vVocab.indexOf(w)]; NbRow(listOf(nbName(w), nbCell(f2(v[0])), nbCell(f2(v[1])), nbCell(f2(v[2])))) } +
                NbRow(listOf(nbName("average", NbInk.Yellow), nbCell(f2(h[0]), NbInk.Yellow), nbCell(f2(h[1]), NbInk.Yellow), nbCell(f2(h[2]), NbInk.Yellow))))), legend,
            "The four context vectors are {averaged} into one.",
            "\"the\" appears twice in the window and counts twice. Averaging throws away order — the \"bag\" in continuous bag-of-words."),
        NbFrame(listOf(NbBlock.Caption("softmax over the vocabulary · before training"), probBars(p0)), listOf(nbLegend(NbInk.Violet, "Target \"rules\"")),
            "Untrained, every word is {about equally likely}.",
            "Small random vectors give near-uniform scores: P(rules) = ${f2(p0[ri])} out of 4 words. The loss is −log P(target) = ${f2(-ln(p0[ri]))}."),
        NbFrame(listOf(NbBlock.Caption("softmax over the vocabulary · after 20 passes"), probBars(mid.probs(mid.hidden(ctx)))), listOf(nbLegend(NbInk.Violet, "Target \"rules\"")),
            "Each update {pulls the target up}.",
            "The gradient raises the target's score and lowers the others', and moves the context vectors toward the target's output vector."),
        NbFrame(listOf(NbBlock.Caption("softmax over the vocabulary · after 200 passes"), probBars(p1), NbBlock.Tiles(listOf(NbTile(f2(loss0), "mean loss, start", NbInk.Grey), NbTile(f2(loss1), "after 200 passes", NbInk.Indigo)))),
            listOf(nbLegend(NbInk.Violet, "Target \"rules\"")),
            "Trained: P(rules | context) = {${f2(p1[ri])}}.",
            "The loss fell from ${f2(loss0)} to ${f2(loss1)}. The two \"the\" examples can't both be perfect — they have different contexts but the same answer, and different answers for similar contexts."),
        NbFrame(listOf(NbBlock.Kv(listOf("vocabulary" to "4 here · 10⁵–10⁶ real", "softmax cost" to "one score per vocabulary word", "fix" to "negative sampling: 5–20 random words", "kept after training" to "the input vectors"))), emptyList(),
            "At real scale, {negative sampling} replaces the softmax.",
            "Scoring every word for every example is too slow with a big vocabulary. Word2vec scores the target against a handful of random words instead; the input vectors are the embeddings."),
    )
}

private fun skipGramFrames(): List<NbFrame> {
    val pairs = skipPairs()
    val cbow = cbowExamples().size
    val head = NbBlock.Caption("\"the king rules the kingdom\" · window ±2")
    fun sentence(centre: Int) = NbBlock.Toks(w2vSentence.mapIndexed { i, w -> nbTok(w, if (i == centre) NbTone.Pick else if (kotlin.math.abs(i - centre) <= 2) NbTone.Hot else NbTone.Plain) })
    fun pairTable(centre: Int) = NbBlock.Table(emptyList(), listOf(0.7f, 0.3f, 1f, 0.4f), aligns = listOf(0, 1, 0, 2),
        rows = (centre - 2..centre + 2).filter { it != centre && it in w2vSentence.indices }.map { Triple(w2vSentence[centre], w2vSentence[it], it - centre) }
            .map { (c, t, o) -> NbRow(listOf(nbName(c, NbInk.Indigo), nbCell("→"), nbName(t, NbInk.Yellow), nbCell(if (o > 0) "+$o" else "−${-o}"))) })
    val legend = listOf(nbLegend(NbInk.Violet, "Centre (input)"), nbLegend(NbInk.Yellow, "Context (predicted)"))
    val counts = NbBlock.Tiles(listOf(NbTile("$cbow", "CBOW examples from this sentence", NbInk.Grey), NbTile("${pairs.size}", "Skip-gram pairs from this sentence", NbInk.Indigo)))
    val trained = w2vTrain(false, 200)
    val ri = w2vVocab.indexOf("rules")
    val p = trained.probs(trained.inV[ri])
    val freq = w2vVocab.map { w -> pairs.count { it.first == "rules" && it.second == w }.toDouble() / pairs.count { it.first == "rules" } }
    return listOf(
        NbFrame(listOf(head, sentence(2), NbBlock.Caption("one centre word → four separate predictions"), pairTable(2), counts), legend,
            "Skip-gram inverts CBOW.",
            "One centre word, and the model predicts each neighbour separately. The window that gave CBOW one example gives skip-gram 4; the whole sentence gives ${pairs.size}."),
        NbFrame(listOf(head, sentence(1), pairTable(1)), legend,
            "Centre \"king\": {three} pairs.",
            "Position 2 has one word to its left. Edge words make fewer pairs, so ${pairs.size} rather than 5 × 4 = 20."),
        NbFrame(listOf(head, sentence(4), pairTable(4)), legend,
            "Centre \"kingdom\": two pairs, both {to the left}.",
            "Offsets are recorded for clarity only — the model ignores them. A neighbour two away is predicted exactly like one next door."),
        NbFrame(listOf(NbBlock.Caption("all ${pairs.size} pairs"), NbBlock.Toks(pairs.map { nbTok("${it.first}→${it.second}", if (it.first == "rules") NbTone.Pick else NbTone.Plain) }, mono = true)), legend,
            "Every pair is {its own training example}.",
            "Where CBOW blurs four context words into one average, skip-gram gives each a separate gradient. Rare words get more updates, which is why skip-gram represents them better."),
        NbFrame(listOf(NbBlock.Caption("P(context | \"rules\") after 200 passes"), NbBlock.Bars(w2vVocab.indices.map { NbBar(w2vVocab[it], f2(p[it]), p[it], if (it == ri) NbInk.Grey else NbInk.Yellow) }, labelWidth = 72),
            NbBlock.Callout(listOf("pair frequencies: " + w2vVocab.indices.filter { freq[it] > 0 }.joinToString(" · ") { "${w2vVocab[it]} ${f2(freq[it])}" }))),
            listOf(nbLegend(NbInk.Yellow, "Predicted neighbour")),
            "The model learns the {neighbour distribution}.",
            "\"rules\" sees \"the\" twice and king, kingdom once each, so the target is 0.50 / 0.25 / 0.25. After 200 passes it gives " +
                "${f2(p[0])} / ${f2(p[1])} / ${f2(p[3])}: the same output vectors also serve the other four centre words, so it can only get close."),
        NbFrame(listOf(NbBlock.Table(listOf("", "CBOW", "skip-gram"), listOf(0.9f, 1f, 1f), listOf(
            NbRow(listOf(nbName("predicts"), nbCell("centre from context"), nbCell("context from centre"))),
            NbRow(listOf(nbName("examples"), nbCell("$cbow"), nbCell("${pairs.size}", NbInk.Indigo))),
            NbRow(listOf(nbName("speed"), nbCell("faster", NbInk.Green), nbCell("slower"))),
            NbRow(listOf(nbName("rare words"), nbCell("averaged away"), nbCell("better", NbInk.Green))),
        ))), emptyList(),
            "CBOW is faster; skip-gram {handles rare words better}.",
            "Same vectors, same window, opposite direction. Mikolov et al. found skip-gram stronger on semantic analogies and CBOW quicker to train."),
        NbFrame(listOf(NbBlock.Kv(listOf("negative sampling" to "k random 'not neighbours' per pair", "subsampling" to "drop frequent words like 'the'", "window" to "random 1…5, nearer words count more", "output" to "input vectors = embeddings"))), emptyList(),
            "Two tricks make it scale: {negative sampling and subsampling}.",
            "Frequent words like \"the\" are mostly dropped before pairing, and each pair is scored against a few random words instead of the whole vocabulary."),
    )
}


// ── RNN / LSTM ──
// A 4-unit recurrence h = tanh(x + 0.6·h) over hand-set input vectors, run here step by step. The
// gradient figures are the real Jacobian products along this sentence: 0.6·(1 − h²) per step.

private val rnnTokens = listOf("the", "movie", "was", "not", "good")
private val rnnDims = listOf("topic", "polarity", "negation", "syntax")
private val rnnInputs = mapOf(
    "the" to doubleArrayOf(0.1, 0.0, 0.0, 0.1),
    "movie" to doubleArrayOf(0.6, 0.2, 0.0, 0.1),
    "was" to doubleArrayOf(0.1, 0.1, 0.0, 0.2),
    "not" to doubleArrayOf(0.0, 0.0, -0.9, 0.3),
    "good" to doubleArrayOf(0.0, 0.7, 0.5, 0.1),
)
private const val RNN_U = 0.6

private fun rnnRun(tokens: List<String>): List<DoubleArray> {
    var h = DoubleArray(4)
    return tokens.map { t ->
        val x = rnnInputs.getValue(t)
        h = DoubleArray(4) { kotlin.math.tanh(x[it] + RNN_U * h[it]) }
        h
    }
}

private fun signedCell(v: Double) = NbGCell(nbF(v, 2), abs(v).coerceAtMost(1.0), if (v < 0) NbInk.Pink else NbInk.Blue)

private fun rnnFrames(): List<NbFrame> {
    val states = rnnRun(rnnTokens)
    val reversed = rnnRun(rnnTokens.reversed())
    fun grid(upTo: Int) = NbBlock.Grid(
        rnnTokens.indices.map { "h${it + 1}" }, rnnDims,
        rnnDims.indices.map { d -> rnnTokens.indices.map { t -> if (t > upTo) NbGCell("", 0.0) else signedCell(states[t][d]) } },
        cellHeight = 26,
    )
    fun toks(cur: Int) = NbBlock.Toks(rnnTokens.mapIndexed { i, w -> nbTok(w, if (i == cur) NbTone.Hot else if (i < cur) NbTone.Plain else NbTone.Dim) })
    val legend = listOf(nbLegend(NbInk.Blue, "Positive"), nbLegend(NbInk.Pink, "Negative"), nbLegend(NbInk.Yellow, "Current token"))
    val head = NbBlock.Caption("\"the movie was not good\" · h = tanh(x + 0.6·h)", "4 units")
    val notes = mapOf(
        "the" to ("Read \"the\": {a faint start}." to "Almost nothing to remember yet. The state is a small copy of the first input."),
        "movie" to ("Read \"movie\": {topic} rises." to "The new state mixes this token's vector with 0.6 of the previous state. \"the\" is still in there, faded."),
        "was" to ("Read \"was\": the state {drifts}." to "A function word adds little. Topic decays toward its new input — each step keeps only part of the past."),
        "not" to ("Read \"not\": {w:negation} goes sharply negative." to "For the sentence to be read correctly, this has to survive the next step — the RNN has no other place to keep it."),
        "good" to ("Read \"good\": polarity rises, {negation is cancelled}." to
            "\"good\" pushes the negation unit up by 0.5 while 0.6 × (${nbF(states[3][2], 2)}) carried from \"not\" pulls it down: it lands at ${nbF(states[4][2], 2)}. The state now holds the interaction — which is why word order matters."),
    )
    val frames = mutableListOf<NbFrame>()
    frames += NbFrame(
        listOf(head, toks(-1), grid(-1), NbBlock.Kv(listOf("input" to "one token vector x", "memory" to "the previous state h", "update" to "h = tanh(W·x + U·h)", "here" to "W = I, U = 0.6·I"))),
        legend, "An RNN reads one token at a time and keeps {a single hidden state}.",
        "Everything it remembers about the sentence so far has to fit in these four numbers. The same weights are reused at every step.",
    )
    rnnTokens.forEachIndexed { t, w ->
        val x = rnnInputs.getValue(w)
        val prev = if (t == 0) DoubleArray(4) else states[t - 1]
        val table = NbBlock.Table(listOf("unit", "x", "0.6·h", "h new"), listOf(0.9f, 0.6f, 0.6f, 0.6f), rnnDims.indices.map { d ->
            NbRow(listOf(nbName(rnnDims[d]), nbCell(nbF(x[d], 2)), nbCell(nbF(RNN_U * prev[d], 2)), nbCell(nbF(states[t][d], 2), if (states[t][d] < 0) NbInk.Pink else NbInk.Blue, true)))
        })
        val (hl, body) = notes.getValue(w)
        frames += NbFrame(listOf(head, toks(t), grid(t), table), legend, hl, body,
            fx = listOf("h${t + 1} = tanh(x(\"$w\") + 0.6·h$t)"))
    }
    val diff = states.last().indices.maxBy { abs(states.last()[it] - reversed.last()[it]) }
    frames += NbFrame(
        listOf(
            NbBlock.Caption("final state · same words, opposite order"),
            NbBlock.Grid(listOf("forward", "reversed"), rnnDims, rnnDims.indices.map { d -> listOf(signedCell(states.last()[d]), signedCell(reversed.last()[d])) }, cellHeight = 26),
            NbBlock.Toks(rnnTokens.reversed().map { nbTok(it) }, label = "reversed"),
        ),
        legend.take(2), "Reverse the words, {get a different state}.",
        "Read backwards, \"not\" comes after \"good\" and the units end up elsewhere — ${rnnDims[diff]} differs most (${nbF(states.last()[diff], 2)} vs ${nbF(reversed.last()[diff], 2)}). A bag of words could never tell these apart.",
    )
    // ∂h5/∂h_t per unit: product over later steps of 0.6·(1 − h²); averaged over the four units.
    val influence = rnnTokens.indices.map { t ->
        (0 until 4).map { d -> ((t + 1) until rnnTokens.size).fold(1.0) { acc, k -> acc * RNN_U * (1 - states[k][d] * states[k][d]) } }.average()
    }
    frames += NbFrame(
        listOf(NbBlock.Caption("how much h5 depends on each earlier state · |∂h5/∂h_t|"),
            NbBlock.Bars(rnnTokens.indices.map { NbBar("h${it + 1} \"${rnnTokens[it]}\"", nbF(influence[it], 3), influence[it], if (it == 0) NbInk.Red else NbInk.Sky) }, labelWidth = 104)),
        listOf(nbLegend(NbInk.Sky, "Gradient reaching that step"), nbLegend(NbInk.Red, "First token")),
        "Signal from early tokens {fades geometrically}.",
        "Each step multiplies the path back by 0.6 × tanh′ ≤ 0.6. After four steps \"the\" contributes ${nbF(influence[0], 3)} — so the gradient that should teach the model about early words all but vanishes.",
        fx = listOf("∂h_t/∂h_(t−1) = 0.6 · (1 − h_t²)  ≤ 0.6"),
    )
    val steps = (1..20).toList()
    val forget = 0.95
    frames += NbFrame(
        listOf(NbBlock.Caption("signal kept after n steps"), NbBlock.Plot(
            1.0 to 20.0, 0.0 to 1.0, 170,
            lines = listOf(NbLine(steps.map { NbP(it.toDouble(), RNN_U.pow(it)) }, NbInk.Red), NbLine(steps.map { NbP(it.toDouble(), forget.pow(it)) }, NbInk.Green)),
            xTicks = listOf(1.0 to "1", 5.0 to "5", 10.0 to "10", 15.0 to "15", 20.0 to "20"), yTicks = listOf(0.0 to "0", 0.5 to "0.5", 1.0 to "1"), xLabel = "steps",
        ), NbBlock.Tiles(listOf(NbTile(nbF(RNN_U.pow(20), 5), "plain RNN after 20", NbInk.Red), NbTile(nbF(forget.pow(20), 2), "LSTM cell, forget 0.95", NbInk.Green)))),
        listOf(nbLegend(NbInk.Red, "RNN · 0.6ⁿ", SwatchStyle.Line), nbLegend(NbInk.Green, "LSTM cell · 0.95ⁿ", SwatchStyle.Line)),
        "An LSTM's cell state {keeps the path open}.",
        "The cell is updated by addition, scaled only by a forget gate. With the gate near 1 the signal after 20 steps is ${nbF(forget.pow(20), 2)}, not ${lsSci(RNN_U.pow(20))}.",
        fx = listOf("c_t = f·c_(t−1) + i·g     ∂c_t/∂c_(t−1) = f"),
    )
    frames += NbFrame(
        listOf(NbBlock.Caption("the LSTM's three gates · each a sigmoid in 0…1"), NbBlock.Boxes(listOf(
            NbBox("forget f", listOf("how much old cell to keep", "σ(W_f·[h, x])"), NbInk.Green, NbInk.Green),
            NbBox("input i", listOf("how much new content to write", "σ(W_i·[h, x])"), NbInk.Sky, NbInk.Sky),
            NbBox("output o", listOf("how much cell to expose as h", "σ(W_o·[h, x])"), NbInk.Violet, NbInk.Violet),
        )), NbBlock.Callout(listOf("c_t = f ⊙ c_(t−1) + i ⊙ tanh(W_g·[h, x])", "h_t = o ⊙ tanh(c_t)"))),
        emptyList(), "Gates decide what to {forget, write and show}.",
        "The network learns when to hold \"not\" in the cell and when to let it go. A GRU merges forget and input into one update gate with similar results and fewer weights.",
    )
    frames += NbFrame(
        listOf(NbBlock.Kv(listOf("RNN" to "one state, fades fast", "LSTM / GRU" to "gated cell, long memory", "still sequential" to "token t waits for t − 1", "replaced by" to "attention: every token sees every other"))), emptyList(),
        "LSTMs carried NLP until {attention} took over.",
        "Gating fixed the memory problem but not the speed problem: a recurrence can't be parallelised over the sequence. Transformers drop the recurrence entirely.",
    )
    return frames
}
