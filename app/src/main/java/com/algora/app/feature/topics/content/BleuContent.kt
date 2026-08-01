package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bleuContent = TopicContent(
    topicId = "bleu",
    whatIsIt = listOf(
        "BLEU scores a translation by n-gram overlap with a reference: what fraction of the candidate's unigrams, bigrams, trigrams and 4-grams appear in the reference, combined as a geometric mean and multiplied by a length penalty. It is a **precision** metric — it asks what fraction of what you produced was warranted, and never whether you produced enough.",
        "**Clipping is not a detail, it is the entire defence.** A stuck decoder emitting \"the the the the the the the the\" has an unclipped unigram precision of 8/8 — perfect, because \"the\" really is in the reference. Cap each n-gram by how many times the reference actually contains it and the same count falls to 2/8. Without that cap, BLEU pays for repetition.",
        "**The brevity penalty handles the opposite exploit,** and it is blunt. \"the revised budget\" is three words, all correct — 3/3 at unigrams, 2/2 at bigrams — and a useless translation of an 8-word sentence. BP = exp(1 − r/c) multiplies it by 0.189. Recall is never measured; the penalty is a stand-in for it.",
        "**Then the case BLEU is famous for getting wrong.** \"on friday the panel signed off on the amended budget\" is a correct translation in different words: unigram precision 5/10, one matching bigram, and **zero** trigrams or 4-grams. The combination is a geometric mean, so one empty order zeroes the product — BLEU **0.0000**, the same score as the stuck decoder. That zero is why sentence-level BLEU is not usable and corpus-level BLEU is: pooling counts across a test set before taking the mean means no single sentence can zero the corpus.",
    ),
    steps = listOf(
        StepCard(1, "Count Matching N-Grams", "Orders 1 through 4, candidate against reference.", 0xFF0EA5E9),
        StepCard(2, "Clip Every Count", "Cap each n-gram at its reference count, or repetition scores perfectly.", 0xFF3B82F6),
        StepCard(3, "Take The Geometric Mean", "Which makes one empty order fatal — deliberately.", 0xFF6366F1),
        StepCard(4, "Apply The Brevity Penalty", "exp(1 − r/c) when the candidate is shorter than the reference.", 0xFF8B5CF6),
        StepCard(5, "Pool Across The Corpus", "Aggregate counts first, then average. Never score one sentence.", 0xFFEC4899),
        StepCard(6, "Publish The Signature", "Tokenizer, reference count, smoothing and casing all move the number.", 0xFFEF4444),
    ),
    formulas = listOf(
        FormulaEntry("BLEU", "BP · exp(Σₙ wₙ log pₙ)", "Geometric mean of clipped precisions, wₙ = 1/4."),
        FormulaEntry("Modified precision", "pₙ = Σ min(cand, ref) / Σ cand", "The clip is the min."),
        FormulaEntry("Clipping in action", "8/8 → 2/8", "The stuck decoder's unigram precision, before and after."),
        FormulaEntry("Brevity penalty", "BP = exp(1 − r/c) if c ≤ r else 1", "0.189 for a 3-word candidate against 8."),
        FormulaEntry("The paraphrase", "5/10 · 1/9 · 0/8 · 0/7 → 0", "One empty order zeroes the product."),
        FormulaEntry("Smoothed", "0.0000 → 0.1667", "Add-1 on empty orders; it orders, it does not separate."),
    ),
    notationKey = listOf(
        NotationEntry("modified precision", "n-gram precision with each count clipped at its reference count"),
        NotationEntry("BP", "brevity penalty — the only place candidate length enters the score"),
        NotationEntry("c, r", "candidate and reference lengths in tokens"),
        NotationEntry("smoothing", "a pseudo-count on empty orders so sentence-level scores are not all 0"),
        NotationEntry("sacreBLEU", "the reference implementation that emits a signature making a score reproducible"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Clipping, and what happens without it",
            accentColor = 0xFF0EA5E9,
            code = """
                from collections import Counter

                def modified_precision(candidate, reference, n):
                    cand = Counter(ngrams(candidate, n))
                    ref  = Counter(ngrams(reference, n))
                    clipped = sum(min(c, ref[g]) for g, c in cand.items())
                    return clipped, sum(cand.values())

                # "the the the the the the the the" against an 8-word reference:
                #   unclipped unigrams  8/8   <- perfect, and meaningless
                #   clipped unigrams    2/8   <- the reference contains "the" twice
                #
                # "on friday the panel signed off on the amended budget":
                #   1-gram 5/10   2-gram 1/9   3-gram 0/8   4-gram 0/7
                #   -> geometric mean = 0. A correct translation, scored 0.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Use it at corpus level, with a signature",
            accentColor = 0xFFEC4899,
            code = """
                import sacrebleu

                # Right: one score over the whole test set, counts pooled first.
                bleu = sacrebleu.corpus_bleu(hypotheses, [references])
                print(bleu.score)              # e.g. 34.2
                print(bleu.get_signature())    # tokenizer, smoothing, version

                # Wrong: a per-sentence number used as a quality judgement.
                #   sentence_bleu(hyp, ref)    # 0 is routine for good output

                # If a per-sentence score is unavoidable, smooth it -- but know
                # what smoothing buys: the paraphrase goes 0.0000 -> 0.1667 and
                # the stuck decoder goes 0.0000 -> 0.1652. Ordered, not separated.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("translate", 0xFF0EA5E9, "Machine Translation", "The field's default system-level score for three decades."),
        ApplicationCard("chart", 0xFF3B82F6, "A/B System Comparison", "Valid use: two systems, one test set, one tokenizer, thousands of sentences."),
        ApplicationCard("code", 0xFF10B981, "Code Generation", "CodeBLEU adapts it with AST and dataflow matching, because token overlap alone is worse here."),
        ApplicationCard("help", 0xFFF59E0B, "What Not To Do", "One sentence, across test sets, or across papers — all invalid."),
    ),
    takeaways = listOf(
        "BLEU is clipped n-gram precision at orders 1–4, geometrically averaged, times a brevity penalty — precision only, never recall.",
        "Clipping takes the stuck decoder's unigram precision from a perfect 8/8 to 2/8; without it, repetition is rewarded.",
        "The geometric mean makes one empty order fatal: a correct paraphrase scores exactly 0.0000, the same as the degenerate output.",
        "That is why BLEU is a corpus statistic — pool counts across the test set, and never quote a per-sentence score as quality.",
        "Tokenizer, reference count, smoothing and casing move BLEU by more than the system differences usually claimed, so publish the signature.",
    ),
    crossLinks = listOf(
        CrossLink("n_grams", "N-Grams"),
        CrossLink("meteor", "METEOR"),
        CrossLink("rouge", "ROUGE Score"),
        CrossLink("seq2seq", "Sequence-to-Sequence"),
    ),
)
