package com.algora.app.feature.topics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// ── Pretrained models, prompting and transformer-internals storyboards ───────
// LLaMA, Mixtral, Claude & Gemini, RAG, prompting, chain of thought, Tree of Thoughts, vector databases,
// ReAct, agents, hallucination, the feed-forward block and positional encodings, drawn by
// NlpStoryLabs.kt. The prompting, reasoning, retrieval, agent and calibration numbers come from
// ModernLlmMath.kt; the rest is computed here. NlpModelFrames.swift is the iOS port.

internal val nlpModelTopicIds = setOf(
    "llama_vicuna", "mistral_mixtral", "claude_gemini", "rag", "prompt_engineering", "chain_of_thought",
    "tree_of_thoughts", "vector_databases", "react", "ai_agents", "hallucination_mitigation", "feed_forward",
    "positional_encodings",
)

internal fun nlpModelLab(topicId: String): List<NbFrame>? = when (topicId) {
    "llama_vicuna" -> llamaFrames()
    "mistral_mixtral" -> mixtralFrames()
    "claude_gemini" -> frontierFrames()
    "rag" -> ragFrames()
    "prompt_engineering" -> promptFrames()
    "chain_of_thought" -> cotFrames()
    "tree_of_thoughts" -> totFrames()
    "vector_databases" -> vectorDbFrames()
    "react" -> reactFrames()
    "ai_agents" -> agentFrames()
    "hallucination_mitigation" -> hallucinationFrames()
    "feed_forward" -> ffnFrames()
    "positional_encodings" -> positionalFrames()
    else -> null
}

private fun f2(v: Double) = nbF(v, 2)

private fun pct(v: Double, d: Int = 0) = nbF(v * 100, d) + "%"

private class ModelRng(seed: Long) {
    private var s = seed
    fun u(): Double { s = (s * 1103515245L + 12345L) and 0x7fffffffL; return s / 2147483648.0 }
    fun g(): Double { val a = max(u(), 1e-12); val b = u(); return sqrt(-2 * ln(a)) * cos(2 * PI * b) }
}

// ── LLaMA & Vicuna ──

private class OpenModel(val name: String, val params: Double, val tokens: Double)

private fun llamaFrames(): List<NbFrame> {
    val models = listOf(OpenModel("LLaMA-1 65B", 65e9, 1.4e12), OpenModel("LLaMA-3 70B", 70e9, 15e12), OpenModel("Mistral 7B", 7.3e9, 8e12))
    val logTop = 4.0
    fun frac(ratio: Double) = log10(ratio) / logTop
    val ratioBars = NbBlock.Bars(
        models.map { NbBar(it.name, nbGrouped(Math.round(it.tokens / it.params)), frac(it.tokens / it.params), NbInk.Sky) },
        ticks = listOf(0.0 to "1", 0.25 to "10", 0.5 to "100", 0.75 to "1k", 1.0 to "10k"),
        ref = frac(20.0) to "20 · Chinchilla", labelWidth = 104,
    )
    val multiples = models.joinToString(" · ") { nbF(it.tokens / it.params / 20, 1) + "×" }
    val small = OpenModel("7B", 7e9, 8e12)
    val big = OpenModel("70B", 70e9, 1.4e12)
    fun serve(m: OpenModel) = 2 * m.params
    fun train(m: OpenModel) = 6 * m.params * m.tokens
    val served = listOf(1e11, 1e12, 1e13)
    val lifetime = served.map { s -> (train(small) + serve(small) * s) to (train(big) + serve(big) * s) }
    val top = lifetime.maxOf { max(it.first, it.second) }
    val serveBars = NbBlock.Bars(listOf(
        NbBar("7B", lsSci(serve(small)), serve(small) / serve(big), NbInk.Pink),
        NbBar("70B", lsSci(serve(big)), 1.0, NbInk.Pink),
    ), labelWidth = 48)
    val boxes = NbBlock.Boxes(listOf(NbBox("training", listOf("paid once", "6·N·D"), lastInk = NbInk.Pink), NbBox("inference", listOf("paid per token", "2·N"), lastInk = NbInk.Pink)))
    return listOf(
        NbFrame(listOf(NbBlock.Caption("tokens per parameter · log scale"), ratioBars, NbBlock.Callout(listOf("vs compute-optimal: $multiples"))), emptyList(),
            "LLaMA's thesis inverts Chinchilla's.",
            "Chinchilla optimises the training budget. LLaMA assumes the model will be served millions of times, so it trains a smaller model far past compute-optimal."),
        NbFrame(listOf(NbBlock.Caption("inference FLOPs per token = 2N"), serveBars, boxes, NbBlock.Callout(listOf("D (training tokens) is absent from the serving cost"))), emptyList(),
            "The payoff shows up at serving time.",
            "A 7B model trained on 8T tokens costs ${lsSci(serve(small))} FLOPs per token to run; a 70B model costs 10× more, forever. Extra training is paid once."),
        NbFrame(listOf(NbBlock.Caption("lifetime FLOPs = 6·N·D + 2·N·served"), NbBlock.Table(listOf("tokens served", "7B · 8T", "70B · 1.4T"), listOf(1f, 0.8f, 0.8f), served.indices.map { i ->
            val (a, b) = lifetime[i]
            NbRow(listOf(nbName(lsSci(served[i])), nbCell(lsSci(a), if (a < b) NbInk.Green else null), nbCell(lsSci(b), if (b < a) NbInk.Green else null)))
        }), NbBlock.Bars(served.indices.map { NbBar(lsSci(served[it]), nbF(lifetime[it].second / lifetime[it].first, 1) + "×", lifetime[it].second / top, NbInk.Pink) }, labelWidth = 64)),
            listOf(nbLegend(NbInk.Green, "Cheaper overall")),
            "The more it is served, {the more the small model wins}.",
            "Training the 7B model longer costs ${lsSci(train(small))} FLOPs against ${lsSci(train(big))} for the 70B, and every served token is 10× cheaper. At ${lsSci(served.last())} tokens served the 70B costs ${nbF(lifetime.last().second / lifetime.last().first, 1)}× as much in total."),
        NbFrame(listOf(NbBlock.Caption("Vicuna · LLaMA-13B fine-tuned on chats"), NbBlock.Kv(listOf("data" to "~70K ShareGPT conversations", "training cost" to "about \$300 (reported)", "evaluation" to "GPT-4 as judge", "claim" to "~90% of ChatGPT quality (judged)"))), emptyList(),
            "Open weights made {cheap fine-tunes} possible.",
            "Vicuna took LLaMA's weights and fine-tuned on shared ChatGPT conversations. Its evaluation used GPT-4 as a judge — a method that later research found flatters chat-style answers."),
        NbFrame(listOf(NbBlock.Kv(listOf("LLaMA-1 (2023)" to "research licence, 7B–65B", "LLaMA-2" to "commercial use, chat models", "LLaMA-3" to "15T tokens, 8B–405B", "effect" to "Alpaca, Vicuna, a fine-tuning ecosystem"))), emptyList(),
            "Small, overtrained, open: {the LLaMA recipe}.",
            "Each release pushed tokens per parameter higher. The models are cheap to run, so they became the base for most open fine-tuning work."),
    )
}

// ── Mixtral (mixture of experts) ──

private fun softmax(xs: List<Double>): List<Double> {
    val m = xs.max()
    val e = xs.map { exp(it - m) }
    val z = e.sum()
    return e.map { it / z }
}

private fun mixtralFrames(): List<NbFrame> {
    val experts = (0 until 8).map { "E$it" }
    // Illustrative router logits for two tokens.
    val theLogits = listOf(-0.6, 0.53, 2.8, 0.2, 0.53, 2.5, -1.7, 1.15)
    val parisLogits = listOf(2.1, -0.4, 0.3, 1.0, 2.6, -0.8, 0.5, 0.1)
    fun route(l: List<Double>): Triple<List<Double>, List<Int>, List<Double>> {
        val p = softmax(l)
        val top = p.indices.sortedByDescending { p[it] }.take(2).sorted()
        val s = top.sumOf { p[it] }
        return Triple(p, top, top.map { p[it] / s })
    }
    val (pThe, topThe, wThe) = route(theLogits)
    val (pParis, topParis, wParis) = route(parisLogits)
    fun cols(p: List<Double>, top: List<Int>) = NbBlock.Columns(p, experts, top.toSet(), p.max())
    fun mix(top: List<Int>, w: List<Double>) = "y = {${f2(w[0])}}·E${top[0]}(x) + {${f2(w[1])}}·E${top[1]}(x)"
    // Load over a batch: 64 tokens with random router logits.
    val r = ModelRng(17)
    val load = IntArray(8)
    repeat(64) {
        val l = List(8) { r.g() + if (it == 2) 0.9 else 0.0 }
        softmax(l).let { p -> p.indices.sortedByDescending { p[it] }.take(2).forEach { load[it]++ } }
    }
    val legend = listOf(nbLegend(NbInk.Violet, "Chosen (top 2)"), nbLegend(NbInk.Slate, "Not run"))
    return listOf(
        NbFrame(listOf(NbBlock.Caption("one transformer block, FFN replaced"), NbBlock.Kv(listOf("attention" to "shared by every token", "FFN" to "8 expert FFNs + a router", "per token" to "router picks 2 experts", "Mixtral 8×7B" to "47B total, ~13B active"))), emptyList(),
            "Mixtral swaps the FFN for {eight experts}.",
            "Attention stays shared. Each feed-forward layer becomes eight separate FFNs, and a small router decides which two each token uses."),
        NbFrame(listOf(NbBlock.Caption("router softmax for \"the\"", "top-2 of 8"), cols(pThe, topThe), NbBlock.Callout(listOf(mix(topThe, wThe), "${f2(pThe[topThe[0]])}, ${f2(pThe[topThe[1]])} renormalised over the kept pair")),
            NbBlock.Callout(listOf("active ≈ 13B of 47B parameters per token"))), legend,
            "A router scores all 8 experts; the top 2 run.",
            "Their weights are renormalised and mix the two outputs. Routing is per token and per layer — the next token usually goes elsewhere."),
        NbFrame(listOf(NbBlock.Caption("router softmax for \"Paris\"", "top-2 of 8"), cols(pParis, topParis), NbBlock.Callout(listOf(mix(topParis, wParis)))), legend,
            "A different token, {different experts}.",
            "\"Paris\" goes to E${topParis[0]} and E${topParis[1]}. Experts end up specialising loosely — on syntax, on token types — rather than on clean topics."),
        NbFrame(listOf(NbBlock.Caption("tokens routed to each expert · batch of 64"), NbBlock.Columns(load.map { it.toDouble() }, experts, setOf(load.indices.maxBy { load[it] }), load.max().toDouble()),
            NbBlock.Callout(listOf("ideal: ${64 * 2 / 8} per expert · busiest: {${load.max()}}"))), listOf(nbLegend(NbInk.Violet, "Overloaded expert")),
            "Left alone, the router {plays favourites}.",
            "A slight bias toward E2 sends it ${load.max()} of 128 slots against an even ${64 * 2 / 8}. Training adds a load-balancing loss so every expert keeps learning and no device sits idle."),
        NbFrame(listOf(NbBlock.Caption("parameters stored vs used per token"), NbBlock.Bars(listOf(
            NbBar("stored", "47B", 1.0, NbInk.Grey), NbBar("active", "13B", 13.0 / 47, NbInk.Violet), NbBar("dense 13B", "13B", 13.0 / 47, NbInk.Sky),
        ), labelWidth = 84), NbBlock.Tiles(listOf(NbTile("3.6×", "more knowledge stored", NbInk.Indigo), NbTile("1×", "compute of a 13B")))),
            listOf(nbLegend(NbInk.Violet, "Mixtral, per token")),
            "Big model's memory, {small model's compute}.",
            "Every expert must sit in GPU memory, but each token only pays for two. Mixtral matched or beat LLaMA-2 70B while running like a 13B."),
        NbFrame(listOf(NbBlock.Caption("Mistral 7B · sliding-window attention"), NbBlock.Tiles(listOf(NbTile("4,096", "tokens each layer attends to", NbInk.Indigo), NbTile("32", "layers"), NbTile("131K", "reach after 32 layers", NbInk.Green)))),
            emptyList(),
            "Mistral 7B: each layer looks back {4,096 tokens}.",
            "Information hops one window per layer, so 32 layers reach 32 × 4,096 ≈ 131K tokens back while each layer's attention cost stays fixed."),
        NbFrame(listOf(NbBlock.Kv(listOf("Mistral 7B" to "dense, sliding window, GQA", "Mixtral 8×7B" to "sparse MoE, top-2 routing", "cost" to "memory for all experts", "benefit" to "quality per FLOP"))), emptyList(),
            "Sparsity trades {memory for compute}.",
            "Mixture-of-experts is now standard in large models: parameters scale with experts, per-token cost with the number chosen."),
    )
}

// ── Claude & Gemini ──

private fun frontierFrames(): List<NbFrame> {
    val windows = listOf("Claude Opus" to 1e6, "Claude Sonnet" to 1e6, "Gemini" to 1e6, "GPT-4-class" to 128e3, "LLaMA" to 128e3)
    fun wf(v: Double) = (log10(v) - 3) / 3
    fun short(v: Double) = if (v >= 1e6) nbF(v / 1e6, 0) + "M" else nbF(v / 1e3, 0) + "K"
    val windowBars = NbBlock.Bars(windows.map { NbBar(it.first, short(it.second), wf(it.second), if (it.second >= 1e6) NbInk.Violet else NbInk.Sky) },
        ticks = listOf(0.0 to "1K", 1.0 / 3 to "10K", 2.0 / 3 to "100K", 1.0 to "1M"), labelWidth = 104)
    val artifacts = listOf("an email" to 500.0, "a long paper" to 15e3, "a novel" to 120e3, "a mid-size codebase" to 800e3)
    val pairs1 = 128e3 * 128e3
    val pairs2 = 1e6 * 1e6
    return listOf(
        NbFrame(listOf(NbBlock.Caption("two frontier families"), NbBlock.Table(listOf("", "Claude", "Gemini"), listOf(0.8f, 1f, 1f), listOf(
            NbRow(listOf(nbName("maker"), nbCell("Anthropic"), nbCell("Google DeepMind"))),
            NbRow(listOf(nbName("tiers"), nbCell("Opus · Sonnet · Haiku"), nbCell("Pro · Flash"))),
            NbRow(listOf(nbName("inputs"), nbCell("text, images, PDFs"), nbCell("text, images, audio, video"))),
            NbRow(listOf(nbName("weights"), nbCell("closed"), nbCell("closed"))),
        ))), emptyList(),
            "Two families, {tiers by size and price}.",
            "Both ship a large, a medium and a small model. Architectures and training data are not published, so comparisons are made on behaviour."),
        NbFrame(listOf(NbBlock.Caption("context window · log scale"), windowBars, NbBlock.Callout(listOf("1M tokens ≈ a large codebase, no chunking"))),
            listOf(nbLegend(NbInk.Violet, "1M-token window"), nbLegend(NbInk.Sky, "128K")),
            "Context is the headline number — and a real capability.",
            "A million tokens puts a codebase or hundreds of thousands of words straight into the prompt. GPT-4 set the 128K bar open models still target."),
        NbFrame(listOf(NbBlock.Caption("what fits · tokens, rough"), NbBlock.Bars(artifacts.map { NbBar(it.first, short(it.second).let { s -> if (it.second < 1e3) "${it.second.toInt()}" else s }, (log10(it.second) - 2) / 4, if (it.second <= 128e3) NbInk.Sky else NbInk.Violet) }, labelWidth = 128)),
            listOf(nbLegend(NbInk.Sky, "Fits in 128K"), nbLegend(NbInk.Violet, "Needs 1M")),
            "A novel fits in 128K; {a codebase needs 1M}.",
            "Long context replaces some retrieval: instead of choosing which chunks to show the model, show it everything."),
        NbFrame(listOf(NbBlock.Caption("self-attention pairs grow with the square"), NbBlock.Tiles(listOf(NbTile(lsSci(pairs1), "pairs at 128K"), NbTile(lsSci(pairs2), "pairs at 1M", NbInk.Red), NbTile(nbF(pairs2 / pairs1, 0) + "×", "more work", NbInk.Yellow)))),
            emptyList(),
            "8× the context, {${nbF(pairs2 / pairs1, 0)}×} the attention.",
            "Naive attention is quadratic, so million-token windows depend on memory-efficient kernels and caching. Long prompts are also slower and costlier per call."),
        NbFrame(listOf(NbBlock.Caption("needle in a haystack"), NbBlock.Kv(listOf("test" to "hide one fact in a long document", "ask" to "retrieve it", "measures" to "recall by depth and length", "doesn't measure" to "reasoning across the whole context"))), emptyList(),
            "Having a long window ≠ {using it well}.",
            "Both families report near-perfect needle retrieval. Tasks that need many facts combined from across the context are harder and less often reported."),
        NbFrame(listOf(NbBlock.Kv(listOf("pick by" to "task, latency and price tier", "long documents" to "1M-context models", "cheap volume" to "Haiku / Flash", "hardest reasoning" to "Opus / Pro"))), emptyList(),
            "Choose the tier, {then the family}.",
            "Within a family the small model is several times cheaper and faster. For most applications that choice matters more than which lab made it."),
    )
}

// ── RAG ──

private val ragDocs = listOf(
    "The refund window is 45 days from delivery for unused items.",
    "Shipping is free on orders over 50 dollars within the country.",
    "Gift cards cannot be refunded or exchanged for cash.",
    "Support is available by chat from 9am to 6pm on weekdays.",
    "Damaged items can be returned for a full refund at any time.",
)
private val ragStop = setOf("the", "is", "are", "a", "an", "of", "for", "on", "or", "be", "can", "to", "by", "from", "at", "any", "what", "within", "over", "in", "cannot")

private fun ragTokens(s: String) = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() && it !in ragStop }.map { if (it.endsWith("s") && it.length > 3) it.dropLast(1) else it }

private fun ragScores(query: String): List<Double> {
    val docs = ragDocs.map { ragTokens(it) }
    val df = docs.flatMap { it.toSet() }.groupingBy { it }.eachCount()
    fun vec(t: List<String>) = t.groupingBy { it }.eachCount().mapValues { (w, c) -> c * (ln((docs.size + 1.0) / ((df[w] ?: 0) + 1.0)) + 1) }
    val q = vec(ragTokens(query))
    return docs.map { d ->
        val v = vec(d)
        val dot = q.keys.intersect(v.keys).sumOf { q.getValue(it) * v.getValue(it) }
        val n = sqrt(q.values.sumOf { it * it }) * sqrt(v.values.sumOf { it * it })
        if (n == 0.0) 0.0 else dot / n
    }
}

private fun ragFrames(): List<NbFrame> {
    val query = "What is the refund window?"
    val steps = listOf("Query", "Embed", "Search", "Augment", "Generate")
    fun pipe(i: Int) = NbBlock.Pipeline(steps, i, i)
    val scores = ragScores(query)
    val order = scores.indices.sortedByDescending { scores[it] }
    val top2 = order.take(2)
    val qBox = NbBlock.Text(listOf(NbSpan(query)))
    val qTokens = ragTokens(query)
    val miss = "Can I get my money back?"
    val missScores = ragScores(miss)
    return listOf(
        NbFrame(listOf(NbBlock.Caption("query"), qBox, NbBlock.Caption("pipeline"), pipe(0), NbBlock.Caption("closed-book answer"),
            NbBlock.Callout(listOf("\"Refunds are accepted within 30 days.\"", "{fluent · unsourced · possibly outdated}"))), emptyList(),
            "The question is about a private, changeable policy.",
            "The weights can't be trusted here: the answer was never in training data, or it has since changed."),
        NbFrame(listOf(NbBlock.Caption("query"), qBox, pipe(1), NbBlock.Caption("query terms after stop words · TF-IDF weighted"), NbBlock.Toks(qTokens.map { nbTok(it, NbTone.Pick) })), emptyList(),
            "Embed: turn the query into {a vector}.",
            "Here a TF-IDF vector over the ${ragDocs.size}-document store; production systems use a dense embedding model. Either way query and documents live in the same space."),
        NbFrame(listOf(pipe(2), NbBlock.Caption("cosine to every stored chunk"), NbBlock.Table(listOf("chunk", "score"), listOf(1.8f, 0.4f), order.map { i ->
            NbRow(listOf(NbCell(ragDocs[i], mono = false), nbCell(f2(scores[i]), if (i in top2) NbInk.Green else null, true)), ring = i == order.first())
        }, listOf(0, 2))), listOf(nbLegend(NbInk.Green, "Top 2 kept")),
            "Search: rank every chunk, {keep the top k}.",
            "The policy chunk wins with ${f2(scores[order[0]])}; \"damaged items … full refund\" shares only the word refund. k = 2 keeps one more for context; everything else stays out of the prompt."),
        NbFrame(listOf(pipe(3), NbBlock.Caption("the prompt the model actually sees"), NbBlock.Callout(listOf("Answer using only the sources.") + top2.mapIndexed { k, i -> "[${k + 1}] ${ragDocs[i]}" } + "Q: $query")), emptyList(),
            "Augment: {paste the evidence} into the prompt.",
            "The model is not retrained. Retrieval only changes what is in its context window — which is why updating a document updates the answer immediately."),
        NbFrame(listOf(pipe(4), NbBlock.Caption("grounded answer"), NbBlock.Callout(listOf("\"The refund window is {45 days} from delivery, for unused items [1].\"")),
            NbBlock.Tiles(listOf(NbTile("30 → 45", "days, closed-book vs grounded", NbInk.Green), NbTile("[1]", "citation a user can check", NbInk.Indigo)))), emptyList(),
            "Generate: the answer {cites its source}.",
            "The closed-book 30 was a plausible guess; the store says 45. The citation makes the claim checkable, and wrong answers traceable to a document."),
        NbFrame(listOf(NbBlock.Caption("\"$miss\" · keyword retrieval"), NbBlock.Table(listOf("chunk", "score"), listOf(1.8f, 0.4f), missScores.indices.sortedByDescending { missScores[it] }.take(3).map { i ->
            NbRow(listOf(NbCell(ragDocs[i], mono = false), nbCell(f2(missScores[i]), NbInk.Red, true)))
        }, listOf(0, 2))), emptyList(),
            "Same question, other words: {retrieval misses}.",
            "\"money back\" shares no term with \"refund\", so every keyword score is ${f2(missScores.max())}. Dense embeddings, hybrid search and query rewriting exist for exactly this — RAG is only as good as its retriever."),
    )
}

// ── Prompt engineering ──

private fun promptFrames(): List<NbFrame> {
    val L = PromptLab
    val pool = L.pool
    fun poolBlock(used: Int) = NbBlock.Toks(pool.mapIndexed { i, w -> if (i < used) nbTok("$w → ${L.label(w)}", NbTone.Plain) else nbTok("$w → ${L.label(w)}", NbTone.Empty) }, columns = 3, mono = true)
    fun rulesBlock(demos: List<String>): List<NbBlock> {
        val alive = L.consistent(demos).map { it.name }.toSet()
        val p = L.prediction(demos)
        return listOf(
            NbBlock.Caption("rules consistent with \"word → one letter\"", "${alive.size} fit"),
            NbBlock.Toks(L.rules.map { r -> nbTok(r.name.substringBefore(" letter").replace("most frequent", "freq").replace("alphabetically first", "alpha"), if (r.name in alive) NbTone.Blue else NbTone.Bad, if (r.name in alive) f2(1.0 / alive.size) else "out") }, columns = 5),
            NbBlock.Caption("answer for \"${L.query}\"", "correct: ${L.label(L.query)}"),
            NbBlock.Bars(p.entries.sortedByDescending { it.value }.map { (c, v) -> NbBar("'$c'", f2(v), v, if (c == L.label(L.query)) NbInk.Green else NbInk.Slate) }, labelWidth = 40),
        )
    }
    fun frame(k: Int, headline: String, body: String) = NbFrame(
        listOf(NbBlock.Caption("demonstration pool", "$k of ${pool.size} used"), poolBlock(k)) + rulesBlock(pool.take(k)),
        listOf(nbLegend(NbInk.Blue, "Still consistent"), nbLegend(NbInk.Green, "Correct answer")), headline, body,
    )
    val curve = L.eliminationCurve()
    val pairs = L.identifyingPairs().size
    val all = L.allPairs().size
    val instruction = L.instructionEliminates()
    val worth = L.demosToEliminate(instruction)
    return listOf(
        frame(0, "A prompt is an induction problem.",
            "Five simple rules all fit \"word → one letter\". Zero-shot asks the model to guess which one you meant — here it picks the right one ${pct(L.prediction(emptyList())[L.label(L.query)] ?: 0.0)} of the time."),
        frame(1, "One demonstration: {banana → n}.",
            "\"banana\" rules out first and last letter. ${curve[1].second} rules remain, and they still disagree about \"kayak\"."),
        frame(2, "Two demonstrations: {${curve[2].second} rules} left.",
            "\"adage\" agrees with the true rule but also with others. More examples of the same kind add little."),
        frame(3, "Three demonstrations, {still ambiguous}.",
            "The examples a person reaches for first all have the same letter in several positions. ${curve[3].second} hypotheses survive."),
        frame(4, "\"level\" settles it: {one rule left}.",
            "One well-chosen example removes what three ordinary ones couldn't. The answer for \"kayak\" is now certain: ${L.label(L.query)}."),
        NbFrame(listOf(NbBlock.Caption("pairs of demonstrations from the pool"), NbBlock.Tiles(listOf(NbTile("$pairs", "pairs that identify the rule", NbInk.Green), NbTile("$all", "possible pairs")))), emptyList(),
            "Which examples matters {more than how many}.",
            "Only $pairs of the $all two-example prompts pin the rule down. Few-shot prompting is choosing informative demonstrations."),
        NbFrame(listOf(NbBlock.Caption("instruction: \"a letter from the middle of the word\""), NbBlock.Toks(instruction.map { nbTok(it.name, NbTone.Bad) }), NbBlock.Tiles(listOf(NbTile("$worth", "demonstrations it replaces", NbInk.Indigo)))), emptyList(),
            "An instruction is {worth $worth demonstrations} here.",
            "Saying what you want removes \"first\" and \"last\" before any example — the same work as $worth demonstrations from the pool, at a fraction of the tokens."),
    )
}

// ── Chain of thought ──

private fun cotFrames(): List<NbFrame> {
    val L = CotLab
    val q = L.directAccuracy
    val p = L.stepAccuracy
    val n = L.breakEvenSteps()
    fun plot(highlight: Int?) = NbBlock.Plot(
        1.0 to 10.0, 0.0 to 1.0, 170,
        lines = listOf(
            NbLine((1..10).map { NbP(it.toDouble(), L.chainAccuracy(it)) }, NbInk.Sky, dots = true, width = 2.2f),
            NbLine(listOf(NbP(1.0, q), NbP(10.0, q)), NbInk.Yellow, dashed = true, width = 1.6f),
        ),
        dots = listOfNotNull(highlight?.let { NbDot(NbP(it.toDouble(), L.chainAccuracy(it)), NbInk.Sky, 5f, ring = true, label = nbF(L.chainAccuracy(it), 3)) }),
        xTicks = (1..10).map { it.toDouble() to "$it" }, yTicks = listOf(0.0 to "0.0", 0.5 to "0.5", 1.0 to "1.0"),
        shade = Triple(n + 0.5, 10.0, ""),
    )
    val legend = listOf(nbLegend(NbInk.Sky, "Chain pⁿ", SwatchStyle.Dot), nbLegend(NbInk.Yellow, "Direct guess q", SwatchStyle.Dot), nbLegend(NbInk.Pink, "Chain loses"))
    val head = NbBlock.Caption("accuracy by chain length n", "p = ${f2(p)} per step")
    val spread = L.votingCurve(0.6, 4)
    val fixed = L.votingCurve(0.4, 1)
    return listOf(
        NbFrame(listOf(head, plot(1), NbBlock.Callout(listOf("one step: p = {${f2(p)}} vs direct q = ${f2(q)}"))), legend,
            "Each small step is {easier than the whole}.",
            "Answering in one shot is right ${pct(q)} of the time. Broken into steps, each is right ${pct(p)} — but all of them have to be right."),
        NbFrame(listOf(head, plot(n), NbBlock.Callout(listOf("break-even at n = $n"))), legend,
            "The curves cross at $n steps.",
            "At $n, decomposing still wins (${nbF(L.chainAccuracy(n), 3)} vs ${f2(q)}). Past it, each step is another chance to be wrong. \"Think step by step\" only helps while pⁿ stays above q."),
        NbFrame(listOf(head, plot(10)), legend,
            "Ten steps: {${nbF(L.chainAccuracy(10), 3)}} — worse than guessing.",
            "Errors compound multiplicatively. Long chains need either more reliable steps or a way to catch mistakes."),
        NbFrame(listOf(NbBlock.Caption("self-consistency · sample k chains, majority vote"), NbBlock.Table(listOf("k", "wrong answers scattered", "wrong answers agree"), listOf(0.3f, 1f, 1f), spread.indices.map { i ->
            NbRow(listOf(nbName("${spread[i].first}"), nbCell(f2(spread[i].second), NbInk.Green), nbCell(f2(fixed[i].second), NbInk.Red)))
        })), listOf(nbLegend(NbInk.Green, "p = 0.6, errors spread over 4"), nbLegend(NbInk.Red, "p = 0.4, one shared error")),
            "Voting helps {when errors disagree}.",
            "With p = 0.6 and scattered errors, 9 votes reach ${f2(spread.last().second)}. When the wrong chains all land on the same answer, voting entrenches it."),
        NbFrame(listOf(NbBlock.Caption("exact, not simulated"), NbBlock.Kv(listOf("chain" to "P(all n right) = pⁿ", "direct" to "q", "vote" to "multinomial over k samples", "ties" to "counted as failures"))), emptyList(),
            "Two formulas {explain most of CoT}.",
            "pⁿ says when to decompose; the plurality vote says when sampling more helps. Both assume steps fail independently — real errors correlate, which is the next lab's problem."),
        NbFrame(listOf(NbBlock.Kv(listOf("helps" to "multi-step arithmetic, logic", "hurts" to "long chains of shaky steps", "fix 1" to "self-consistency voting", "fix 2" to "search over chains (Tree of Thoughts)"))), emptyList(),
            "Chain of thought is {one path}; search explores many.",
            "When a single chain is likely to go wrong somewhere, keeping several partial chains alive and evaluating them is the next step."),
    )
}

// ── Tree of Thoughts ──

private fun totFrames(): List<NbFrame> {
    val L = TotLab
    val root = L.puzzle
    val succ = L.successors(root)
    fun exprFor(state: List<Double>) = succ.first { L.label(it.state) == L.label(state) }.let { "${it.expression}=${L.label(listOf(it.result))}" }.replace(" ", "")
    val ranked = L.rankedFrontier(1)
    val greedy = L.beam(1)
    val width = L.widthNeeded(1)
    val width2 = L.widthNeeded(2)
    val wide = L.beam(width)
    val (expanded, solutions) = L.exhaustive()
    val (firstRank, _, frontier) = L.evaluatorQuality(width)
    val top3 = ranked.take(3)
    val solutionText = "(10−4)×(13−9)=24"
    fun tree(chain: Int): NbBlock.Tree {
        val nodes = mutableListOf(NbNode(0.5f, 0.1f, L.label(root), tone = NbTone.Blue))
        val edges = mutableListOf<NbEdge>()
        top3.forEachIndexed { i, t ->
            nodes += NbNode(0.18f + 0.32f * i, 0.32f, exprFor(t.first), tone = if (i == 0) NbTone.Hot else NbTone.Plain, below = "h ${nbF(t.second, 1)}", belowInk = if (i == 0) NbInk.Yellow else null)
            edges += NbEdge(0, nodes.lastIndex)
        }
        var parent = 1
        greedy.trace.drop(1).take(chain).forEachIndexed { k, step ->
            val last = k == greedy.trace.size - 2
            nodes += NbNode(0.18f, 0.55f + 0.22f * k, step.replace(" ", ""), tone = if (last && !greedy.solved) NbTone.Bad else NbTone.Plain, below = if (last && !greedy.solved) "≠ 24 · dead end" else "", belowInk = NbInk.Pink)
            edges += NbEdge(parent, nodes.lastIndex)
            parent = nodes.lastIndex
        }
        if (chain >= 2) nodes += NbNode(0.66f, 0.68f, "never explored:", solutionText, dashed = true)
        return NbBlock.Tree(nodes, edges, 230)
    }
    val legend = listOf(nbLegend(NbInk.Yellow, "Committed"), nbLegend(NbInk.Pink, "Failure"), nbLegend(NbInk.Green, "Missed solution"))
    val head = NbBlock.Caption("Game of 24 · greedy chain = beam width 1")
    return listOf(
        NbFrame(listOf(NbBlock.Caption("Game of 24 · combine all four numbers into 24"), NbBlock.Toks(root.map { nbTok(L.label(listOf(it)), NbTone.Blue) }),
            NbBlock.Callout(listOf("first moves available: {${succ.size}}", "distinct states after one move: {${ranked.size}}"))), emptyList(),
            "A puzzle where {the first move decides}.",
            "Pick two numbers, apply + − × ÷, repeat until one number is left. Most first moves can never reach 24."),
        NbFrame(listOf(head, tree(1)), legend,
            "A chain of thought is beam width 1.",
            "The cheap evaluator likes ${exprFor(top3[0].first)} and commits. The solution was one sibling away."),
        NbFrame(listOf(head, tree(greedy.trace.size - 1)), legend,
            "The greedy chain {${if (greedy.solved) "solves it" else "dead-ends"}}.",
            if (greedy.solved) "This time the top-ranked move worked." else "Every later step was the best local choice, and the final number is not 24. There is no way back — a chain never revisits."),
        NbFrame(listOf(NbBlock.Caption("the evaluator's ranking of all ${frontier} first moves"), NbBlock.Table(listOf("state", "h", "can reach 24"), listOf(1f, 0.4f, 0.7f), ranked.take(6).map { (s, h, ok) ->
            NbRow(listOf(nbName(exprFor(s)), nbCell(nbF(h, 1)), nbCell(if (ok) "yes" else "no", if (ok) NbInk.Green else NbInk.Red, true)))
        })), listOf(nbLegend(NbInk.Green, "Solvable")),
            "The evaluator {ranks the first solvable move #$firstRank}.",
            "h is \"how close could one more operation get to 24\" — cheap and optimistic. Its favourites look close and lead nowhere."),
        NbFrame(listOf(NbBlock.Caption("beam search · keep the best w states each level"), NbBlock.Table(listOf("width", "solved", "states expanded"), listOf(0.5f, 0.6f, 0.9f), listOf(1, 2, width).distinct().map { w ->
            val r = L.beam(w)
            NbRow(listOf(nbName("$w"), nbCell(if (r.solved) "yes" else "no", if (r.solved) NbInk.Green else NbInk.Red, true), nbCell("${r.expanded}")))
        })), emptyList(),
            "Keep more branches: {width $width} finds it.",
            "Tree of Thoughts keeps several partial solutions alive and lets the evaluator prune. Width substitutes for a better evaluator."),
        NbFrame(listOf(NbBlock.Caption("the solving beam's path"), NbBlock.Table(emptyList(), listOf(0.2f, 1.4f), wide.trace.mapIndexed { i, s -> NbRow(listOf(nbCell("${i + 1}"), nbCell(s, if (i == wide.trace.lastIndex) NbInk.Green else null, true)), ring = i == wide.trace.lastIndex) }, listOf(0, 0))),
            listOf(nbLegend(NbInk.Green, "Reaches 24")),
            "At width $width: {${wide.trace.last()}}.",
            "The path starts from a move the evaluator ranked below its favourite — exactly the sibling a single chain discards."),
        NbFrame(listOf(NbBlock.Caption("width needed vs evaluator look-ahead"), NbBlock.Tiles(listOf(NbTile("$width", "width · 1-step evaluator", NbInk.Yellow), NbTile("$width2", "width · 2-step evaluator", NbInk.Green)))), emptyList(),
            "A smarter evaluator needs {a narrower beam}.",
            "Looking one more move ahead before scoring cuts the width needed from $width to $width2 — paid for in evaluator calls instead of beam slots."),
        NbFrame(listOf(NbBlock.Caption("evaluator calls to solve"), NbBlock.Table(listOf("setting", "solved", "evaluator work"), listOf(1.2f, 0.5f, 0.8f), listOf(
            Triple("1-step, width $width", L.beam(width, 1), ""), Triple("2-step, width $width2", L.beam(width2, 2), ""),
        ).map { (name, r, _) -> NbRow(listOf(nbName(name), nbCell(if (r.solved) "yes" else "no", if (r.solved) NbInk.Green else NbInk.Red), nbCell(nbGrouped(r.evaluatorCalls.toLong())))) })), emptyList(),
            "Every option {costs model calls}.",
            "In the paper each evaluation is an LLM call. ToT solved 74% of Game of 24 against 4% for chain of thought — at roughly a hundred times the calls."),
        NbFrame(listOf(NbBlock.Caption("the whole tree, searched exhaustively"), NbBlock.Tiles(listOf(NbTile(nbGrouped(expanded.toLong()), "states", NbInk.Grey), NbTile("$solutions", "paths reaching 24", NbInk.Green)))), emptyList(),
            "Exhaustive search: {${nbGrouped(expanded.toLong())} states}.",
            "Brute force always works on a puzzle this small. Search with an evaluator matters when the tree is too big to enumerate — which is every interesting problem."),
        NbFrame(listOf(NbBlock.Caption("never explored by the greedy chain"), NbBlock.Callout(listOf(solutionText)), NbBlock.Tiles(listOf(NbTile("13−9=4", "a move ranked low", NbInk.Grey), NbTile("10−4=6", "another", NbInk.Grey), NbTile("4×6=24", "together", NbInk.Green)))), emptyList(),
            "The solution needs {two unglamorous moves}.",
            "Neither 13 − 9 nor 10 − 4 looks close to 24 on its own. Only a search that keeps them alive can combine them."),
        NbFrame(listOf(NbBlock.Kv(listOf("chain of thought" to "one path, width 1", "self-consistency" to "many full paths, then vote", "Tree of Thoughts" to "partial paths + evaluator + backtracking", "cost" to "evaluator calls × width × depth"))), emptyList(),
            "ToT = {search + a learned evaluator}.",
            "It is classic beam or best-first search with the LLM proposing moves and scoring states. It pays off when one early mistake ruins a chain."),
    )
}

// ── Vector databases ──

private fun vectorDbFrames(): List<NbFrame> {
    val L = VectorDbLab
    val q = L.query
    val exact = L.exactNeighbours()
    val exactIds = exact.map { it.id }.toSet()
    fun scatter(cells: Boolean, showQuery: Boolean = true, found: Set<Int> = emptySet(), centroids: Boolean = false, probed: Set<Int> = emptySet()) = NbBlock.Plot(
        0.0 to 1.0, 0.0 to 1.0, 210,
        dots = L.corpus.map { d ->
            val cell = L.ivf.assignment[d.id]
            NbDot(NbP(d.x, d.y), NbInk.Grey, if (d.id in found) 4.5f else 2.6f, ring = d.id in found, cat = if (cells) (if (probed.isEmpty() || cell in probed) cell else null) else d.cluster)
        } + (if (centroids) L.ivf.centroids.map { NbDot(NbP(it.first, it.second), r = 5f, hollow = true) } else emptyList()) +
            (if (showQuery) listOf(NbDot(NbP(q.first, q.second), NbInk.Yellow, 6f, ring = true)) else emptyList()),
        grid = false,
    )
    val ivfLegend = listOf(nbLegend(NbInk.Grey, "Centroid", SwatchStyle.Ring), nbLegend(NbInk.Indigo, "Vector, coloured by cell"))
    val (one, c1) = L.ivfSearch(1)
    val probes = listOf(1, 2, 3, 4)
    val sw = L.smallWorldGraph
    val stranded = L.strandedFraction(L.knnGraph)
    val g1 = L.graphSearch(2)
    val g2 = L.graphSearch(8)
    val dims = listOf(2, 10, 100, 1000)
    val contrast = dims.map { L.contrastRatio(it) }
    val levels = listOf(256, 16, 4)
    val ordered = L.ivf.centroids.indices.sortedBy { (q.first - L.ivf.centroids[it].first).pow(2) + (q.second - L.ivf.centroids[it].second).pow(2) }
    return listOf(
        NbFrame(listOf(NbBlock.Caption("${L.corpusSize} stored vectors · 2-D for drawing"), scatter(false, showQuery = false)), listOf(nbLegend(NbInk.Indigo, "Vector, coloured by topic")),
            "A vector database stores {embeddings}.",
            "Every document becomes a point; similar meaning means nearby points. The job is finding the nearest ones to a query, fast."),
        NbFrame(listOf(NbBlock.Caption("brute force · compare the query to every vector"), scatter(false, found = exactIds), NbBlock.Callout(listOf("${L.corpusSize} distance computations · recall {1.00}"))),
            listOf(nbLegend(NbInk.Yellow, "Query"), nbLegend(NbInk.Grey, "True top-5", SwatchStyle.Ring)),
            "Exact search: {every vector, every query}.",
            "Always right, and linear in the corpus. At a billion vectors that is a billion distances per query — too slow."),
        NbFrame(listOf(NbBlock.Caption("IVF index · k-means on stored vectors", "k = ${L.ivf.centroids.size}"), scatter(true, showQuery = false, centroids = true), NbBlock.Callout(listOf("${L.ivf.centroids.size} cells · ${L.corpusSize} vectors · one assignment pass"))), ivfLegend,
            "IVF partitions the corpus first.",
            "k-means gives ${L.ivf.centroids.size} coarse cells and tags each vector with one. A query scans only the cells it probes — fast to build, and its failure mode is geometric."),
        NbFrame(listOf(NbBlock.Caption("probe the nearest cell only · nprobe = 1"), scatter(true, found = one.map { it.id }.toSet(), centroids = true, probed = ordered.take(1).toSet()),
            NbBlock.Tiles(listOf(NbTile(nbF(L.recall(one), 1), "recall@5", if (L.recall(one) < 1) NbInk.Red else NbInk.Green), NbTile("$c1", "distances computed")))), ivfLegend,
            "The query sits {on a cell boundary}.",
            "Its true neighbours are split across two cells. Probing only the nearest cell finds ${(L.recall(one) * 5).toInt()} of 5 — the geometric failure."),
        NbFrame(listOf(NbBlock.Caption("more probes · recall vs work"), NbBlock.Table(listOf("nprobe", "recall@5", "distances"), listOf(0.6f, 0.7f, 0.7f), probes.map { n ->
            val (f, c) = L.ivfSearch(n)
            NbRow(listOf(nbName("$n"), nbCell(nbF(L.recall(f), 1), if (L.recall(f) == 1.0) NbInk.Green else NbInk.Red), nbCell("$c")))
        } + NbRow(listOf(nbName("exact"), nbCell("1.0", NbInk.Green), nbCell("${L.corpusSize}"))))), emptyList(),
            "nprobe is {the recall dial}.",
            "Each extra cell costs more distances and recovers more neighbours. Production systems tune it per workload."),
        NbFrame(listOf(NbBlock.Caption("graph index · each vector linked to its 6 nearest"), NbBlock.Tiles(listOf(NbTile(pct(stranded), "start points that can't reach the answer", NbInk.Red), NbTile("${L.componentCount(L.knnGraph)}", "disconnected islands")))), emptyList(),
            "A plain k-NN graph {strands} some searches.",
            "Clusters link only to themselves. A greedy walk that starts on the wrong island never reaches the query's neighbours, whatever its budget."),
        NbFrame(listOf(NbBlock.Caption("+2 random long links per node (small world)"), NbBlock.Table(listOf("ef", "recall@5", "distances", "hops"), listOf(0.4f, 0.6f, 0.6f, 0.4f), listOf(2 to g1, 8 to g2).map { (ef, r) ->
            NbRow(listOf(nbName("$ef"), nbCell(nbF(L.recall(r.found), 1), if (L.recall(r.found) == 1.0) NbInk.Green else NbInk.Yellow), nbCell("${r.comparisons}"), nbCell("${r.hops}")))
        }), NbBlock.Callout(listOf("islands: {${L.componentCount(sw)}}"))), emptyList(),
            "Long links fix it: {the HNSW idea}.",
            "A few random edges join every island. Greedy search then reaches the answer from anywhere; ef, the candidate list size, trades work for recall."),
        NbFrame(listOf(NbBlock.Caption("contrast (d_max − d_min) / d_min · random points"), NbBlock.Bars(dims.indices.map { NbBar("d = ${dims[it]}", f2(contrast[it]), log10(1 + contrast[it]) / log10(1 + contrast.max()), NbInk.Violet) }, labelWidth = 72)), emptyList(),
            "In high dimensions {everything is about equally far}.",
            "With 1,000 dimensions the nearest and farthest points differ by only ${pct(contrast.last())}. Embeddings work because real data clusters on a low-dimensional surface — uniform data would be hopeless."),
        NbFrame(listOf(NbBlock.Caption("scalar quantization · levels per dimension"), NbBlock.Table(listOf("levels", "bits", "recall@5", "bytes · 768-d"), listOf(0.5f, 0.4f, 0.6f, 0.8f), levels.map { lv ->
            val bits = (ln(lv.toDouble()) / ln(2.0)).toInt()
            val r = L.quantizedRecall(lv)
            NbRow(listOf(nbName("$lv"), nbCell("$bits"), nbCell(nbF(r, 1), if (r == 1.0) NbInk.Green else NbInk.Red), nbCell(nbGrouped(L.bytesPerVector(768, bits).toLong()))))
        })), emptyList(),
            "Compress the vectors: {memory vs accuracy}.",
            "8 bits per dimension keeps the ranking; at 2 bits the grid is coarser than the gap between neighbours and recall falls. 768 floats take 3,072 bytes uncompressed."),
        NbFrame(listOf(NbBlock.Kv(listOf("IVF" to "cluster, probe a few cells", "HNSW" to "navigable small-world graph", "PQ / quantization" to "smaller vectors", "filters" to "metadata alongside vectors"))), emptyList(),
            "Every index is {approximate on purpose}.",
            "Vector databases (FAISS, pgvector, Pinecone, Milvus) combine these: an index for speed, quantization for memory, and recall as the price."),
    )
}

// ── ReAct ──

private fun reactFrames(): List<NbFrame> {
    val L = ReActLab
    val (top3, found) = L.singleShot(3)
    val head = NbBlock.Caption("\"${L.question}\"")
    val turns = L.trajectory
    fun turnTable(upTo: Int) = NbBlock.Table(emptyList(), listOf(0.35f, 1.6f), turns.take(upTo + 1).flatMapIndexed { i, t ->
        listOf(
            NbRow(listOf(nbCell("thought"), NbCell(t.thought, mono = false))),
            NbRow(listOf(nbCell("act", NbInk.Indigo), nbCell(t.action, NbInk.Indigo)), ring = i == upTo),
            NbRow(listOf(nbCell("obs", NbInk.Green), NbCell(t.observation, NbInk.Green, mono = false))),
        )
    }, listOf(0, 0))
    return listOf(
        NbFrame(listOf(head, NbBlock.Caption("one retrieval with the question as the query · top 3"), NbBlock.Table(listOf("passage", "score"), listOf(1.3f, 0.4f), top3.map { p ->
            NbRow(listOf(nbName(p.title, if (p.id == L.hop2PassageId) NbInk.Green else null), nbCell(f2(RetrievalLab.score(L.question, p)))))
        }), NbBlock.Callout(listOf("answer passage (C, 1972) in top 3: {${if (found) "yes" else "no"}} · its rank: ${RetrievalLab.rankOf(L.question, L.hop2PassageId)}"))), emptyList(),
            "A two-hop question {defeats one retrieval}.",
            "The passage with the year never mentions Linux, so searching with the question ranks it #${RetrievalLab.rankOf(L.question, L.hop2PassageId)}. The first hop's answer is needed to find the second."),
        NbFrame(listOf(NbBlock.Caption("\"When was it first released?\" · three ways to answer"), NbBlock.Table(emptyList(), listOf(0.8f, 1f, 0.6f), listOf(
            NbRow(listOf(nbName("closed book"), NbCell("guess from weights", mono = false), nbCell("${L.closedBookAnswer} ✗", NbInk.Pink, true))),
            NbRow(listOf(nbName("reason only"), NbCell("longer guess", mono = false), nbCell("${L.closedBookAnswer} ✗", NbInk.Pink, true))),
            NbRow(listOf(nbName("ReAct"), NbCell("search → read → answer", mono = false), nbCell("${L.groundedAnswer} ✓", NbInk.Green, true)), tint = NbInk.Violet),
        )), NbBlock.Kv(listOf("reasoning fixes" to "steps you can derive", "retrieval fixes" to "facts you do not hold", "this question needs" to "both, interleaved"))), emptyList(),
            "More thinking does not recover a missing fact.",
            "Closed-book gives \"${L.closedBookAnswer}\" — fluent and wrong. Reasoning harder only lengthens the guess. ReAct interleaves thought with action."),
        NbFrame(listOf(head, turnTable(0)), listOf(nbLegend(NbInk.Indigo, "Action"), nbLegend(NbInk.Green, "Observation")),
            "Turn 1: {find the language}.",
            "The thought plans the decomposition; the action is a tool call; the observation is what the tool returned — passage \"${RetrievalLab.corpus[turns[0].retrievedId!!].title}\"."),
        NbFrame(listOf(head, turnTable(1)), listOf(nbLegend(NbInk.Indigo, "Action"), nbLegend(NbInk.Green, "Observation")),
            "Turn 2: {a new query} from what was learned.",
            "\"C language first released\" was impossible to write before turn 1. It retrieves the passage the original question ranked #${RetrievalLab.rankOf(L.question, L.hop2PassageId)}."),
        NbFrame(listOf(head, turnTable(2)), listOf(nbLegend(NbInk.Indigo, "Action"), nbLegend(NbInk.Green, "Observation")),
            "Turn 3: {finish(\"${L.groundedAnswer}\")}.",
            "Every claim in the answer traces to an observation. If a search had failed, the next thought could have tried another query."),
        NbFrame(listOf(NbBlock.Kv(listOf("loop" to "thought → action → observation", "actions" to "search, lookup, finish", "strength" to "grounded, inspectable", "cost" to "one model call per turn"))), emptyList(),
            "ReAct is {the loop under most agents}.",
            "Reason about what is missing, act to get it, read the result, repeat. Tool-using agents generalise the action set beyond search."),
    )
}

// ── Agents ──

private fun agentFrames(): List<NbFrame> {
    val L = AgentLab
    val total = L.trajectory.sumOf { it.tokens }
    val maxT = L.trajectory.maxOf { it.tokens }.toDouble()
    fun log(highlightFailed: Boolean) = NbBlock.Table(emptyList(), listOf(0.15f, 1.4f, 0.6f, 0.35f), L.trajectory.mapIndexed { i, s ->
        NbRow(listOf(nbCell("${i + 1}"), nbCell(s.label, if (highlightFailed && s.failed) NbInk.Yellow else null, true), NbCell("", NbInk.Sky.takeIf { !(highlightFailed && s.failed) } ?: NbInk.Yellow, bar = s.tokens / maxT), nbCell("${s.tokens}")), tint = if (highlightFailed && s.failed) NbInk.Yellow else null)
    }, listOf(0, 0, 0, 2))
    val requests = L.trajectory.indices.filter { it % 2 == 0 }
    val contexts = requests.map { L.contextAt(it + 1) }
    return listOf(
        NbFrame(listOf(NbBlock.Caption("three tools · schemas sent with every request"), NbBlock.Table(listOf("tool", "latency", "schema tokens"), listOf(0.8f, 0.6f, 0.7f), L.tools.map { NbRow(listOf(nbName(it.name), nbCell("${it.latencyMs} ms"), nbCell("${it.schemaTokens}"))) }),
            NbBlock.Callout(listOf("system prompt ${L.systemTokens} + schemas ${L.schemaTokens()} = {${L.systemTokens + L.schemaTokens()}} tokens before any work"))), emptyList(),
            "An agent is a model {plus tools it can call}.",
            "Each tool is described by a schema the model reads on every turn. Those tokens are paid before the task even starts."),
        NbFrame(listOf(NbBlock.Caption("trajectory · tokens added per entry", "Σ $total"), log(true), NbBlock.Callout(listOf("the error came back as an observation"))), listOf(nbLegend(NbInk.Yellow, "Failure and retry")),
            "The trajectory, one entry per turn.",
            "Step 4 is a real failure: the tool rejected \"mi\" and the model retried with \"mile\". Recovery is worth having — but it is not free."),
        NbFrame(listOf(NbBlock.Caption("context resent on each model request"), NbBlock.Bars(requests.indices.map { NbBar("request ${it + 1}", "${contexts[it]}", contexts[it].toDouble() / contexts.max(), NbInk.Sky) }, labelWidth = 84),
            NbBlock.Tiles(listOf(NbTile(nbGrouped(L.billedTokens().toLong()), "tokens billed", NbInk.Red), NbTile("${L.finalContext()}", "final transcript"), NbTile(nbF(L.billingMultiple(), 1) + "×", "billing multiple", NbInk.Yellow)))), emptyList(),
            "Every turn {resends everything before it}.",
            "The model is stateless, so request k carries the whole transcript. Billed tokens grow roughly with the square of the number of steps."),
        NbFrame(listOf(NbBlock.Caption("the same task without the failed call"), NbBlock.Tiles(listOf(NbTile(nbGrouped(L.billedTokens().toLong()), "with retry"), NbTile(nbGrouped(L.billedTokens(L.cleanTrajectory).toLong()), "clean", NbInk.Green), NbTile("+" + nbGrouped(L.retryOverhead().toLong()), "cost of one error", NbInk.Red)))), emptyList(),
            "One retried call cost {${nbGrouped(L.retryOverhead().toLong())} tokens}.",
            "The two failure entries are only ${L.trajectory.filter { it.failed }.sumOf { it.tokens }} tokens in the transcript, but they are resent on every later request. Clear tool errors and good schemas pay for themselves."),
        NbFrame(listOf(NbBlock.Caption("three independent tool calls"), NbBlock.Bars(listOf(
            NbBar("one per turn", "${L.sequentialLatency()} ms", 1.0, NbInk.Red), NbBar("parallel", "${L.parallelLatency()} ms", L.parallelLatency().toDouble() / L.sequentialLatency(), NbInk.Green),
        ), labelWidth = 96)), emptyList(),
            "Issue independent calls {in one turn}.",
            "Parallel tool calls wait only for the slowest (${L.parallelLatency()} ms) and save two round trips of resent context."),
    )
}

// ── Hallucination ──

private fun hallucinationFrames(): List<NbFrame> {
    val L = HallucinationLab
    val qs = L.questions
    fun dumbbell(pop: List<HallucinationLab.Question>) = NbBlock.Plot(
        0.5 to qs.size + 0.5, 0.0 to 1.0, 170,
        segs = pop.mapIndexed { i, q -> NbSeg(NbP(i + 1.0, L.confidence(q)), NbP(i + 1.0, L.accuracy(q)), if (abs(L.confidence(q) - L.accuracy(q)) > 0.3) NbInk.Pink else NbInk.Slate, width = 2f) },
        dots = pop.flatMapIndexed { i, q -> listOf(NbDot(NbP(i + 1.0, L.confidence(q)), NbInk.Violet, 4f), NbDot(NbP(i + 1.0, L.accuracy(q)), NbInk.Sky, 4f)) },
        xTicks = (1..qs.size).map { it.toDouble() to "Q$it" }, yTicks = listOf(0.0 to "0.0", 0.5 to "0.5", 1.0 to "1.0"),
        shade = Triple(qs.count { it.supported } + 0.5, qs.size + 0.5, "unsupported"),
        groups = listOf(3.5 to "supported", 8.5 to "unsupported"),
    )
    val legend = listOf(nbLegend(NbInk.Violet, "Confidence (5-chain agreement)", SwatchStyle.Dot), nbLegend(NbInk.Sky, "Accuracy", SwatchStyle.Dot))
    val ece = L.expectedCalibrationError()
    val eceScattered = L.expectedCalibrationError(L.scatteredQuestions)
    val sweep = L.abstentionSweep()
    val unsup = qs.filter { !it.supported }
    return listOf(
        NbFrame(listOf(NbBlock.Caption("10 questions · accuracy of a 5-sample majority"), NbBlock.Bars(qs.mapIndexed { i, q -> NbBar("Q${i + 1}", f2(L.accuracy(q)), L.accuracy(q), if (q.supported) NbInk.Sky else NbInk.Pink) }, labelWidth = 36)),
            listOf(nbLegend(NbInk.Sky, "Supported by the documents"), nbLegend(NbInk.Pink, "Not answerable from them")),
            "Some questions {can't be answered} from what the model knows.",
            "The six supported questions are mostly right. On the four unsupported ones the model is wrong in a consistent way — the definition of a hallucination.",
            chips = listOf(nbChip("overall accuracy", f2(L.overallAccuracy()), true))),
        NbFrame(listOf(NbBlock.Caption("confidence vs accuracy · per question", "ECE ${f2(ece)}"), dumbbell(qs)), legend,
            "Self-agreement is the first fix people reach for.",
            "It fails here: the four unsupported questions report ${f2(unsup.minOf { L.confidence(it) })}–${f2(unsup.maxOf { L.confidence(it) })} confidence while almost never being right."),
        NbFrame(listOf(NbBlock.Caption("control: the same model, errors scattered", "ECE ${f2(eceScattered)}"), dumbbell(L.scatteredQuestions)), legend,
            "When wrong answers {disagree}, confidence drops.",
            "Spread the errors over four different wrong answers and agreement falls, so confidence becomes a warning sign. Hallucinations are dangerous because they are systematic."),
        NbFrame(listOf(NbBlock.Caption("answer only above a confidence threshold"), NbBlock.Table(listOf("threshold", "answered", "accuracy"), listOf(0.6f, 0.6f, 0.6f), sweep.map { (t, c, a) ->
            NbRow(listOf(nbName(f2(t)), nbCell(pct(c)), nbCell(f2(a), if (a > 0.8) NbInk.Green else NbInk.Red)))
        })), emptyList(),
            "Thresholding confidence {can't separate} them.",
            "The unsupported questions are as confident as the real ones, so any threshold that drops them also drops good answers. Confidence measures agreement, not truth."),
        NbFrame(listOf(NbBlock.Caption("grounding: answer only when retrieval supports it"), NbBlock.Tiles(listOf(NbTile(pct(L.groundedCoverage()), "questions answered", NbInk.Indigo), NbTile(f2(L.groundedSelectiveAccuracy()), "accuracy when answering", NbInk.Green), NbTile(f2(L.overallAccuracy()), "answering everything", NbInk.Red)))), emptyList(),
            "Check the {evidence}, not the model's agreement with itself.",
            "Refusing when no retrieved passage supports the answer drops exactly the four unsupported questions. Accuracy on what remains is ${f2(L.groundedSelectiveAccuracy())}."),
        NbFrame(listOf(NbBlock.Kv(listOf("self-consistency" to "catches random errors only", "confidence threshold" to "fails on systematic errors", "retrieval + citation" to "checks against a source", "abstention" to "\"I don't know\" as a valid answer"))), emptyList(),
            "Mitigation means {grounding and abstaining}.",
            "A model that agrees with itself can still be wrong. External evidence and the option to decline are what reduce hallucinations in practice."),
    )
}

// ── Feed-forward network ──

private fun ffnFrames(): List<NbFrame> {
    val x = listOf(0.4, 0.5, 0.6, 0.7)
    val r = ModelRng(31)
    val w1 = List(8) { List(4) { r.g() * 0.7 } }
    val b1 = List(8) { r.g() * 0.2 }
    val w2 = List(4) { List(8) { r.g() * 0.5 } }
    val pre = w1.indices.map { j -> b1[j] + x.indices.sumOf { w1[j][it] * x[it] } }
    val hidden = pre.map { max(it, 0.0) }
    val out = w2.map { row -> row.indices.sumOf { row[it] * hidden[it] } }
    val zeroed = hidden.count { it == 0.0 }
    val inks = listOf(NbInk.Sky, NbInk.Sky, NbInk.Violet)
    val footers = listOf("token" to "d = 768", "hidden · ReLU" to "4d = 3072", "output" to "d = 768")
    val legend = listOf(nbLegend(NbInk.Sky, "Active", SwatchStyle.Dot), nbLegend(NbInk.Slate, "Zeroed by ReLU", SwatchStyle.Dot), nbLegend(NbInk.Violet, "Output", SwatchStyle.Dot))
    fun net(showHidden: Boolean, relu: Boolean, showOut: Boolean) = NbBlock.Net(
        listOf(x, if (!showHidden) List(8) { null } else if (relu) hidden.map { if (it == 0.0) null else it } else pre, if (showOut) out else List(4) { null }),
        inks, footers,
    )
    val head = NbBlock.Caption("one position, expand → ReLU → project", "shown 4 → 8 → 4")
    val d = 768L
    val ffnParams = 2 * d * 4 * d + 4 * d + d
    val attnParams = 4 * d * d + 4 * d
    return listOf(
        NbFrame(listOf(head, net(true, true, true)), legend,
            "Attention, then a position-wise FFN.",
            "Two linear layers with a ReLU, applied to each token alone: 768 → 3072 → 768. No token talks to another here — attention already did that."),
        NbFrame(listOf(head, net(true, false, false), NbBlock.Callout(listOf("h = W₁x + b₁ · ${pre.count { it < 0 }} of 8 pre-activations negative"))), legend,
            "Expand: {4× wider}.",
            "W₁ projects the token into a larger space. Some of the 8 values come out negative: ${pre.joinToString(" ") { f2(it) }}."),
        NbFrame(listOf(head, net(true, true, false)), legend,
            "ReLU zeroes {$zeroed of 8} units.",
            "max(0, h) switches off every negative unit for this token. Which units fire depends on the token — the FFN behaves like a lookup keyed on the input."),
        NbFrame(listOf(head, net(true, true, true), NbBlock.Callout(listOf("y = W₂·ReLU(h) → back to d = 768"))), legend,
            "Project back {to the model width}.",
            "W₂ combines only the active units. The result is added to the residual stream and passed to the next block."),
        NbFrame(listOf(NbBlock.Caption("parameters per block · d = 768"), NbBlock.Bars(listOf(
            NbBar("FFN", nbGrouped(ffnParams), 1.0, NbInk.Violet), NbBar("attention", nbGrouped(attnParams), attnParams.toDouble() / ffnParams, NbInk.Sky),
        ), labelWidth = 84), NbBlock.Tiles(listOf(NbTile(pct(ffnParams.toDouble() / (ffnParams + attnParams)), "of a block's weights", NbInk.Indigo)))), emptyList(),
            "The FFN holds {two-thirds} of the weights.",
            "8d² against attention's 4d². Much of what a transformer \"knows\" is stored here — interpretability work finds facts recalled through FFN key-value patterns."),
        NbFrame(listOf(NbBlock.Kv(listOf("original" to "ReLU, 4× expansion", "GPT-2 / BERT" to "GELU", "LLaMA, Mistral" to "SwiGLU, ~2.7× with a gate", "MoE" to "many FFNs, router picks a few"))), emptyList(),
            "Modern models {gate} the FFN.",
            "SwiGLU multiplies a second projection into the hidden layer before projecting back. Mixture-of-experts replaces the single FFN with several."),
    )
}

// ── Positional encodings ──

private fun pe(pos: Int, i: Int, d: Int = 16): Double {
    val k = i / 2
    val angle = pos / 10000.0.pow(2.0 * k / d)
    return if (i % 2 == 0) sin(angle) else cos(angle)
}

private fun positionalFrames(): List<NbFrame> {
    val d = 16
    val grid = NbBlock.Grid(
        (0 until d).map { "$it" }, (0 until 8).map { "p$it" },
        (0 until 8).map { p -> (0 until d).map { i -> NbGCell("", (pe(p, i, d) + 1) / 2, NbInk.Indigo) } },
        text = false, cellHeight = 16, scale = "−1" to "+1",
    )
    val waves = (0 until 4).map { k -> 2 * PI * 10000.0.pow(2.0 * k / d) }
    val waveTiles = NbBlock.Tiles(waves.mapIndexed { k, w -> NbTile(nbF(w, 1), "dims ${2 * k}–${2 * k + 1}") })
    fun dot(a: Int, b: Int) = (0 until d).sumOf { pe(a, it, d) * pe(b, it, d) }
    val offsets = listOf(1, 2, 4)
    val starts = listOf(0, 3, 10)
    return listOf(
        NbFrame(listOf(NbBlock.Caption("attention sees a set, not a sequence"), NbBlock.Toks("dog bites man".split(" ").map { nbTok(it) }, label = "A"), NbBlock.Toks("man bites dog".split(" ").map { nbTok(it) }, label = "B"),
            NbBlock.Callout(listOf("same tokens → same attention scores, permuted"))), emptyList(),
            "Self-attention is {order-blind}.",
            "Shuffling the tokens just shuffles the outputs. Without extra information \"dog bites man\" and \"man bites dog\" look the same."),
        NbFrame(listOf(NbBlock.Caption("PE(pos, dim) · 8 positions × 16 dims"), grid, NbBlock.Caption("wavelength per dimension pair"), waveTiles), emptyList(),
            "A fixed sinusoid per dimension pair.",
            "Wavelengths grow geometrically from 2π to 10000·2π: early dims cycle every few positions, late ones barely move."),
        NbFrame(listOf(NbBlock.Caption("dims 0 (sin) and 1 (cos) across positions"), NbBlock.Plot(0.0 to 7.0, -1.0 to 1.0, 150,
            lines = listOf(NbLine((0..70).map { NbP(it / 10.0, sin(it / 10.0)) }, NbInk.Indigo), NbLine((0..70).map { NbP(it / 10.0, cos(it / 10.0)) }, NbInk.Sky, dashed = true)),
            dots = (0..7).map { NbDot(NbP(it.toDouble(), sin(it.toDouble())), NbInk.Indigo, 3.5f) },
            xTicks = (0..7).map { it.toDouble() to "p$it" }, yTicks = listOf(-1.0 to "−1", 0.0 to "0", 1.0 to "1"))),
            listOf(nbLegend(NbInk.Indigo, "sin(pos)", SwatchStyle.Line), nbLegend(NbInk.Sky, "cos(pos)", SwatchStyle.DashedLine)),
            "Each pair is {a point on a circle}.",
            "(sin, cos) at a given frequency rotates as position grows. Every position gets a unique combination of angles across the 8 pairs."),
        NbFrame(listOf(NbBlock.Caption("PE(p) · PE(p + k) for different starting p"), NbBlock.Table(listOf("offset k", "p = 0", "p = 3", "p = 10"), listOf(0.6f, 0.6f, 0.6f, 0.6f), offsets.map { k ->
            NbRow(listOf(nbName("$k"), nbCell(f2(dot(starts[0], starts[0] + k)), NbInk.Indigo), nbCell(f2(dot(starts[1], starts[1] + k)), NbInk.Indigo), nbCell(f2(dot(starts[2], starts[2] + k)), NbInk.Indigo)))
        })), emptyList(),
            "The dot product depends {only on the offset}.",
            "Shifting by k rotates each pair by a fixed angle, so PE(p)·PE(p+k) is the same wherever p starts. Relative position is visible to attention."),
        NbFrame(listOf(NbBlock.Caption("added to the token embedding"), NbBlock.Callout(listOf("x_p = embedding(token) + PE(p)", "same width d · no parameters")), NbBlock.Tiles(listOf(NbTile("0", "learned parameters", NbInk.Green), NbTile("any", "length, in principle")))), emptyList(),
            "The encoding is {added, not concatenated}.",
            "Each position's vector is summed into the token embedding before the first layer. Sinusoids need no training and are defined for any position."),
        NbFrame(listOf(NbBlock.Caption("learned positions · BERT, GPT-2"), NbBlock.Kv(listOf("table" to "one trained vector per position", "max length" to "512 (BERT) · 1,024 (GPT-2)", "beyond it" to "no vector exists", "quality" to "about the same as sinusoids"))), emptyList(),
            "Learned tables are simpler — {and capped}.",
            "BERT and GPT-2 train a vector per position. They match sinusoids in quality but stop at the table's length."),
        NbFrame(listOf(NbBlock.Kv(listOf("RoPE (LLaMA, Mistral)" to "rotate q and k by position", "ALiBi" to "penalise attention by distance", "why" to "relative position, longer contexts", "extension" to "rescale RoPE frequencies"))), emptyList(),
            "Modern models {rotate} queries and keys.",
            "RoPE applies the same sin/cos rotation inside attention instead of adding it to the input, so q·k depends directly on relative position — and stretches to longer contexts."),
    )
}
