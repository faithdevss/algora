package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBar
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val werContent = TopicContent(
    topicId = "wer",
    figure = Figure(
        caption = "Three transcripts of the same nine-word reference, scored by the metric that is " +
            "supposed to rank them. Dropping three function words leaves the meaning intact and " +
            "costs 0.333. Deleting the single word \"not\" reverses what was said and costs 0.111 — " +
            "three times better, because every word costs 1. Add two substituted articles to that " +
            "reversed hypothesis and it carries 2 substitutions plus 1 deletion: the same 3 errors " +
            "over the same 9-word denominator, an identical 0.333, and one of the two says the " +
            "opposite of the truth. Off this axis entirely is the stuck decoder that emits 17 " +
            "spurious words against a 9-word reference — 1.889, because insertions are counted " +
            "against a denominator that excludes them, which is why \"accuracy = 1 − WER\" reports " +
            "−88.9% there. This is what the definition measures, not a tuning problem.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("3 words dropped", 0.333f, FigureTone.Muted),
                FigureBar("\"not\" dropped", 0.111f, FigureTone.Warn),
                FigureBar("reversed + 2 subs", 0.333f, FigureTone.Warn),
            ),
            yLabel = "WER = (S+D+I)/N",
            xLabel = "same reference, three hypotheses",
        ),
    ),
    whatIsIt = listOf(
        "Word error rate is the word-level edit distance between a transcript and its reference, divided by the reference length: **(S + D + I) / N**. It is the standard score for speech recognition, and every one of its problems follows from two decisions baked into that formula — that all three error types cost exactly 1, and that the denominator counts the reference only.",
        "**WER has no upper bound.** Insertions are counted against a denominator that does not include them, so a decoder that gets stuck and emits 17 spurious words against a 9-word reference scores **1.889 — 188.9%**. Any tool reporting \"accuracy = 1 − WER\" produces −88.9% here, which is not a quantity. WER is an error rate, and rates above 1 are ordinary rather than a bug.",
        "**Every word costs the same, so the metric ranks failures backwards.** Dropping three function words leaves the meaning intact and scores 0.333. Deleting the single word \"not\" reverses the sentence entirely and scores **0.111 — three times better**. A system tuned to minimise WER is being told, in the only language it understands, to prefer the second failure.",
        "**And it can be made blind outright.** Add two substituted articles to that reversed hypothesis and it carries 2 substitutions plus 1 deletion — the same 3 errors as the harmless one's 3 deletions, over the same 9-word denominator. Identical WER of 0.333, and one of them says the opposite of what was said. This is not a shortcoming to be tuned away; it is what the definition measures. Meaning-sensitive evaluation needs a different metric, not a better-tuned WER.",
    ),
    steps = listOf(
        StepCard(1, "Align, Don't Just Count", "Levenshtein over words, with a backtrace so S, D and I are separated.", 0xFF0EA5E9),
        StepCard(2, "Divide By The Reference", "N is the reference length, which is why insertions can push the rate past 1.", 0xFF3B82F6),
        StepCard(3, "Read The Three Counts", "S, D and I fail differently. The summed rate hides which one you have.", 0xFF6366F1),
        StepCard(4, "Normalize First", "Casing, punctuation and numerals move the score more than most model changes.", 0xFF8B5CF6),
        StepCard(5, "Weight What Matters", "Entity or keyword error rate for the words a downstream system actually reads.", 0xFFEC4899),
        StepCard(6, "Report The Normalizer", "A WER without its text-normalization recipe is not reproducible.", 0xFFEF4444),
    ),
    formulas = listOf(
        FormulaEntry("WER", "(S + D + I) / N", "N counts the reference only — hence no upper bound."),
        FormulaEntry("Runaway insertion", "17 insertions / 9 words = 1.889", "188.9%, and \"1 − WER\" reports −88.9%."),
        FormulaEntry("The misranking", "0.333 vs 0.111", "Meaning intact scores 3× worse than meaning reversed."),
        FormulaEntry("The tie", "3 deletions vs 2 subs + 1 deletion", "Both 0.333; one reverses the sentence."),
        FormulaEntry("Normalization", "0.556 → 0.000", "Same transcript pair, lowercased and expanded."),
        FormulaEntry("Alternatives", "CER, keyword ER, entity ER", "When characters or specific words carry the meaning."),
    ),
    notationKey = listOf(
        NotationEntry("S / D / I", "substitutions, deletions, insertions from the alignment backtrace"),
        NotationEntry("N", "the reference's word count — the denominator, which excludes insertions"),
        NotationEntry("normalization", "lowercasing, punctuation stripping, numeral and abbreviation expansion"),
        NotationEntry("CER", "character error rate — the same formula over characters"),
        NotationEntry("hypothesis", "the system's transcript, scored against a human reference"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The alignment, with the three counts kept apart",
            accentColor = 0xFF0EA5E9,
            code = """
                import jiwer

                reference = "the model did not converge on the second run"

                out = jiwer.process_words(reference, hypothesis)
                print(out.wer, out.substitutions, out.deletions, out.insertions)

                # dropped function words -> S0 D3 I0 -> 0.333  (meaning intact)
                # negation deleted       -> S0 D1 I0 -> 0.111  (meaning reversed)
                # negation + articles    -> S2 D1 I0 -> 0.333  (an exact tie)
                # stuck decoder          -> S0 D0 I17 -> 1.889 (above 100%)
                #
                # The metric prefers the reversed sentence to the harmless one, and
                # cannot distinguish the third from the first at all.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Normalize before you compare anything",
            accentColor = 0xFF8B5CF6,
            code = """
                transform = jiwer.Compose([
                    jiwer.ToLowerCase(),
                    jiwer.RemovePunctuation(),
                    jiwer.ExpandCommonEnglishContractions(),
                    jiwer.RemoveMultipleSpaces(),
                    jiwer.Strip(),
                ])

                # "The model did not converge, on the second run."
                # vs "the Model did not converge on the 2nd run"
                #
                #   split on whitespace only : WER 0.556
                #   normalized + "2nd"->"second" : WER 0.000
                #
                # A 55.6-point swing with no model change. Most published WER
                # differences are smaller than the gap between two normalizers.

                # And weight what the downstream system reads:
                keyword_wer = errors_on(KEYWORDS) / len(KEYWORDS)
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("mic", 0xFF0EA5E9, "Speech Recognition", "The field's default score, quoted per corpus and per normalizer."),
        ApplicationCard("translate", 0xFF3B82F6, "Voice Interfaces", "Keyword error rate matters more than WER when a command triggers an action."),
        ApplicationCard("book", 0xFF10B981, "Captioning & Transcription", "Where deletions of negation and named entities cost far more than the rate implies."),
        ApplicationCard("help", 0xFFF59E0B, "Reporting Honestly", "Publish the normalizer, and never convert WER into an accuracy."),
    ),
    takeaways = listOf(
        "WER is (S + D + I) / N over a word alignment, and the reference-only denominator means it can exceed 1 — 1.889 in the lab's stuck-decoder case.",
        "\"Accuracy = 1 − WER\" is not a valid conversion; it produces negative accuracies on ordinary failures.",
        "Equal word weights misrank meaning: deleting \"not\" scores 0.111 while three harmless deletions score 0.333.",
        "Two hypotheses with opposite meanings can tie exactly at 0.333 — WER has no way to express the difference.",
        "Normalization moved the same transcript pair from 0.556 to 0.000, so a WER without its normalizer is unreproducible.",
    ),
    crossLinks = listOf(
        CrossLink("edit_distance", "Edit Distance"),
        CrossLink("perplexity", "Perplexity"),
        CrossLink("bleu", "BLEU Score"),
        CrossLink("tokenization", "Tokenization"),
    ),
)
