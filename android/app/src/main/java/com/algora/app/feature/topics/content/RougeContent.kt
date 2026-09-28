package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val rougeContent = TopicContent(
    topicId = "rouge",
    whatIsIt = listOf(
        "ROUGE is BLEU's mirror image. Summarisation's failure mode is leaving things out rather than making things up, so ROUGE reports **recall**: what fraction of the reference summary's n-grams the candidate managed to include. ROUGE-1 counts unigrams, ROUGE-2 bigrams, and ROUGE-L uses the longest common subsequence — which needs no fixed n and rewards keeping content in order.",
        "**Recall-first has an obvious exploit, and the field walked into it for years.** Submit the entire source document — 34 words, no summarisation performed at all — and ROUGE-1 recall is **0.800**. Nothing was selected, compressed or decided; the words are simply all still there. Any system reporting recall alone is being scored against a do-nothing baseline that is genuinely hard to beat.",
        "**Precision closes it decisively:** the whole document's ROUGE-1 precision is 0.235, so F1 lands at 0.364 against the focused summary's 1.000. Report F1, or report recall under a length budget — which is exactly what the original DUC evaluations did, and why they were harder to game than much of what followed.",
        "**Two blind spots remain.** ROUGE-1 is a bag of words, so a summary with its clauses swapped — saying the board's review *followed* the announcement rather than preceded it — scores 1.000, identical to the correct summary, while ROUGE-2 falls to 0.778 and ROUGE-L to 0.500. And a correct abstractive summary that shares almost no vocabulary scores 0.105. Optimising ROUGE therefore teaches a model to *extract* and copy the reference's phrasing, which is precisely the behaviour abstractive summarisation exists to escape: **the metric selects against the capability it is used to measure.**",
    ),
    steps = listOf(
        StepCard(1, "Count Reference N-Grams Covered", "Recall is the primary direction — what the summary kept.", 0xFF0EA5E9),
        StepCard(2, "Compute Precision Too", "Without it, submitting the whole document wins.", 0xFF3B82F6),
        StepCard(3, "Report F1", "Or recall under a fixed length budget. Never recall alone.", 0xFF6366F1),
        StepCard(4, "Add ROUGE-2 And ROUGE-L", "Bigrams and the LCS are where word order finally registers.", 0xFF8B5CF6),
        StepCard(5, "Read The Disagreements", "A ROUGE-1/ROUGE-L gap is an ordering problem; a recall/precision gap is a length problem.", 0xFFEC4899),
        StepCard(6, "Use Multiple References", "One reference makes a single phrasing arbitrarily correct.", 0xFFEF4444),
    ),
    formulas = listOf(
        FormulaEntry("ROUGE-N recall", "matched n-grams / reference n-grams", "The primary direction, and the exploitable one."),
        FormulaEntry("The do-nothing baseline", "recall 0.800, precision 0.235", "Submitting the whole 34-word document."),
        FormulaEntry("F1", "2PR / (P + R)", "0.364 for the whole document against 1.000 for a real summary."),
        FormulaEntry("ROUGE-L", "LCS(candidate, reference) / lengths", "No fixed n, and it notices order."),
        FormulaEntry("The reorder", "R1 1.000 · R2 0.778 · RL 0.500", "Same words, reversed claim — R1 cannot see it."),
        FormulaEntry("The abstractive case", "R1 F1 0.105, R2 0.000", "Correct, well-written, and unrewarded."),
    ),
    notationKey = listOf(
        NotationEntry("ROUGE-N", "n-gram overlap with the reference, reported as recall, precision or F1"),
        NotationEntry("ROUGE-L", "longest common subsequence — matches in order, with gaps allowed"),
        NotationEntry("extractive", "a summary built by selecting source sentences verbatim"),
        NotationEntry("abstractive", "a summary written in new words — what ROUGE penalises"),
        NotationEntry("length budget", "a word or byte cap that makes a recall-only score meaningful"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The do-nothing baseline, scored",
            accentColor = 0xFF0EA5E9,
            code = """
                from rouge_score import rouge_scorer

                scorer = rouge_scorer.RougeScorer(
                    ["rouge1", "rouge2", "rougeL"], use_stemmer=True
                )

                # A focused summary:      R1 p=1.000 r=1.000 f=1.000
                # The whole document:     R1 p=0.235 r=0.800 f=0.364
                #                                    ^^^^^^^
                #   No summarisation performed, four fifths of the reference recalled.
                #
                # Report f, not r. Or cap the length and then report r --
                # which is what DUC did, and why it was harder to game.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why one variant is never enough",
            accentColor = 0xFFEC4899,
            code = """
                # Reference: "the board reviewed quarterly results and announced
                #             a dividend increase"
                # Candidate: "announced a dividend increase and the board reviewed
                #             quarterly results"      <- clauses swapped

                #   ROUGE-1 F1 = 1.000   <- a bag of words has no order to lose
                #   ROUGE-2 F1 = 0.778
                #   ROUGE-L F1 = 0.500

                # And an abstractive summary that is simply correct:
                # "directors approved a larger payout after a strong quarter"
                #   ROUGE-1 F1 = 0.105   ROUGE-2 F1 = 0.000

                # Diagnosis, not just a score:
                #   R1 high, RL low        -> right words, wrong order
                #   recall high, prec low  -> too long
                #   all three low          -> abstractive, or bad. ROUGE cannot say.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("book", 0xFF0EA5E9, "Summarisation Benchmarks", "CNN/DailyMail, XSum and the rest are all reported in ROUGE F1."),
        ApplicationCard("search", 0xFF3B82F6, "Extractive Systems", "Where ROUGE is at its most valid — the vocabulary is shared by construction."),
        ApplicationCard("robot", 0xFF10B981, "LLM Evaluation", "Increasingly replaced by model-graded scoring, because abstractive output is what ROUGE penalises."),
        ApplicationCard("help", 0xFFF59E0B, "Metric Gaming", "Recall-only reporting rewards copying; length budgets and F1 are the fix."),
    ),
    takeaways = listOf(
        "ROUGE reports recall because summarisation's failure is omission — and that direction is exactly what makes it gameable.",
        "Copying the whole document scores 0.800 recall for summarising nothing; its precision of 0.235 is the only thing that stops it.",
        "ROUGE-1 cannot see word order: a summary with its clauses swapped scores 1.000, identical to the correct one, while ROUGE-L halves to 0.500.",
        "A correct abstractive summary sharing little vocabulary scores 0.105, so optimising ROUGE trains models to extract rather than abstract.",
        "Quote ROUGE-1/2/L together as F1, on one test set, with multiple references — the disagreements between them are the diagnostic.",
    ),
    crossLinks = listOf(
        CrossLink("bleu", "BLEU Score"),
        CrossLink("meteor", "METEOR"),
        CrossLink("n_grams", "N-Grams"),
        CrossLink("seq2seq", "Sequence-to-Sequence"),
    ),
)
