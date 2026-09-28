package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val nerContent = TopicContent(
    topicId = "ner",
    whatIsIt = listOf(
        "Named entity recognition labels the spans of a sentence that refer to real-world things — people, organizations, locations, dates, amounts — and says which type each one is.",
        "It is framed as per-token tagging using the BIO scheme: B- starts an entity, I- continues it, O is outside any entity. That turns a span problem into a sequence-labelling problem, which a CRF, a BiLSTM or a transformer encoder can all learn.",
    ),
    steps = listOf(
        StepCard(1, "Tokenize and Align", "Split into tokens and keep a mapping back to character offsets in the original text.", 0xFF3B82F6),
        StepCard(2, "Tag With BIO", "Each token gets one of B-TYPE, I-TYPE or O — the label set is 2·|types| + 1.", 0xFF10B981),
        StepCard(3, "Encode the Context", "A BiLSTM or transformer gives every token a representation informed by the whole sentence.", 0xFFF59E0B),
        StepCard(4, "Decode a Valid Sequence", "A CRF layer scores label transitions so illegal sequences like O → I-PER cannot win.", 0xFF8B5CF6),
        StepCard(5, "Merge Spans and Score", "Contiguous B/I runs become entities; evaluation is exact-span F1, not per-token accuracy.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Label space", "{O} ∪ {B-t, I-t : t ∈ types}", "BIO tagging over the entity types."),
        FormulaEntry("CRF score", "s(x,y) = Σ emit(yᵢ|xᵢ) + Σ trans(yᵢ₋₁→yᵢ)", "Emissions from the encoder, transitions learned."),
        FormulaEntry("Decoding", "argmax_y s(x,y) via Viterbi", "O(n·|L|²) dynamic programming over the tag sequence."),
        FormulaEntry("Entity F1", "harmonic mean of span precision and recall", "A span counts only if both boundaries and type are right."),
    ),
    notationKey = listOf(
        NotationEntry("B- / I- / O", "begin / inside / outside an entity"),
        NotationEntry("PER, ORG, LOC, MISC", "the classic CoNLL-2003 entity types"),
        NotationEntry("emission", "score the encoder assigns to a label for one token"),
        NotationEntry("transition", "learned cost of following one label with another"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Viterbi decoding over BIO tags",
            accentColor = 0xFF6366F1,
            code = """
                // emissions[token][label], transitions[from][to] — both log-scores.
                fun viterbi(emissions: Array<DoubleArray>, transitions: Array<DoubleArray>): IntArray {
                    val n = emissions.size
                    val labels = emissions[0].size
                    val best = Array(n) { DoubleArray(labels) { Double.NEGATIVE_INFINITY } }
                    val backpointer = Array(n) { IntArray(labels) }

                    emissions[0].copyInto(best[0])

                    for (t in 1 until n) {
                        for (cur in 0 until labels) {
                            var bestScore = Double.NEGATIVE_INFINITY
                            var bestPrev = 0
                            for (prev in 0 until labels) {
                                // An illegal transition (O -> I-PER) carries a large negative score,
                                // so it can never win here.
                                val score = best[t - 1][prev] + transitions[prev][cur]
                                if (score > bestScore) { bestScore = score; bestPrev = prev }
                            }
                            best[t][cur] = bestScore + emissions[t][cur]
                            backpointer[t][cur] = bestPrev
                        }
                    }

                    val path = IntArray(n)
                    path[n - 1] = best[n - 1].indices.maxBy { best[n - 1][it] }
                    for (t in n - 1 downTo 1) path[t - 1] = backpointer[t][path[t]]
                    return path
                }

                // B-PER I-PER O B-LOC  ->  [(0..1, PER), (3..3, LOC)]
                fun spansOf(tags: List<String>): List<Triple<Int, Int, String>> {
                    val out = mutableListOf<Triple<Int, Int, String>>()
                    var start = -1
                    var type = ""
                    for (i in tags.indices) {
                        val tag = tags[i]
                        when {
                            tag.startsWith("B-") -> {
                                if (start >= 0) out += Triple(start, i - 1, type)
                                start = i; type = tag.substring(2)
                            }
                            tag == "O" || (tag.startsWith("I-") && tag.substring(2) != type) -> {
                                if (start >= 0) out += Triple(start, i - 1, type)
                                start = -1; type = ""
                            }
                        }
                    }
                    if (start >= 0) out += Triple(start, tags.size - 1, type)
                    return out
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFF3B82F6, "Document Understanding", "Contracts and invoices are parsed into parties, dates and amounts for downstream systems."),
        ApplicationCard("search", 0xFF10B981, "Search & Knowledge Graphs", "Linking mentions to entities is what turns a text index into a structured graph."),
        ApplicationCard("globe", 0xFFF59E0B, "PII Redaction", "Names, addresses and account numbers are located before logs or transcripts are shared."),
    ),
    takeaways = listOf(
        "BIO tagging converts span extraction into ordinary per-token classification.",
        "A CRF layer matters because it enforces globally legal tag sequences, not just locally likely ones.",
        "Evaluate on exact-span F1 — per-token accuracy flatters a model that clips boundaries.",
        "Subword tokenizers need careful alignment: label the first subword and mask the rest.",
    ),
    crossLinks = listOf(
        CrossLink("tokenization", "Tokenization"),
        CrossLink("rnn_lstm", "RNN / LSTM"),
        CrossLink("transformers", "Transformers"),
    ),
)
