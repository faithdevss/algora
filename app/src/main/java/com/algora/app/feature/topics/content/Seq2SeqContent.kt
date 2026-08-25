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

internal val seq2seqContent = TopicContent(
    topicId = "seq2seq",
    figure = Figure(
        caption = "The same 120 sequences decoded twice by the same model, sorted by what went " +
            "wrong. Widening the beam from 1 to 10 raises mean log-probability from −2.284 to " +
            "−1.994 and changes 45 of the 120 outputs — and moves exact match by one sequence, 35 " +
            "correct to 36. The split says why it could not do better. Score the gold sequence " +
            "under the model and every failure is either a search error, where the model ranked " +
            "gold above what was returned, or a model error, where it did not. There is exactly " +
            "one search error at width 1 and none at width 10, while the 84 model errors do not " +
            "move at all, because no beam width can return a sequence the model itself scores " +
            "lower. Seven of every ten outputs are outside the decoder's reach at any width. " +
            "Reversing the encoder's input — free at decode time — converts 31 of them.",
        shape = FigureShape.Plot(
            bars = listOf(
                FigureBar("✓ 35", 0.292f, FigureTone.Accent),
                FigureBar("search 1", 0.008f),
                FigureBar("model 84", 0.700f, FigureTone.Warn),
                FigureBar("✓ 36", 0.300f, FigureTone.Accent),
                FigureBar("search 0", 0.000f),
                FigureBar("model 84", 0.700f, FigureTone.Warn),
            ),
            xLabel = "greedy, k = 1 · then beam, k = 10",
            yLabel = "share of 120 outputs",
        ),
    ),
    whatIsIt = listOf(
        "A seq2seq model does not emit a sequence. It scores them — assigning a probability to every string the decoder could produce — and something else has to search that space for one to return. Greedy decoding takes the most probable next symbol at each step and never reconsiders; beam search keeps the k best partial sequences by summed log-probability and lets a hypothesis that looked second-best early come first at the end. Greedy is beam search with k = 1, and the gap between them is a search problem, not a modelling one.",
        "Beam search does what it promises here and almost nothing else. Widening it from 1 to 10 raises mean log-probability from −2.284 to −1.994 and changes 45 of 120 outputs — and moves exact match from 0.292 to 0.300, a single sequence. That is not a disappointment to shrug at; it is diagnosable. Score the gold sequence under the same model and every wrong output sorts into one of two kinds: the model preferred gold and the search lost it (a search error), or the model preferred its own wrong answer (a model error). At width 1 the split is 1 search error against 84 model errors, and at width 10 the search errors are gone. Eighty-four of 120 outputs are beyond any beam width, because the thing being searched is wrong.",
        "The same diagnosis says where to spend instead. Feeding the encoder the source backwards — no extra parameters, no extra decoding cost — converts 31 model errors into correct outputs, thirty times what widening the beam bought. Two other well-known knobs are reported here as they measured rather than as they are usually described: length normalization changes 10 of 120 outputs, lengthens them, and moves accuracy by 0.000, because on this task the source fixes the output length; and teacher forcing, which is how the model was trained, scores 49.5% next-symbol accuracy against 32.4% when the model reads its own output — the exposure-bias gap, on one trained model, in one number.",
    ),
    steps = listOf(
        StepCard(1, "Score, Don't Emit", "The model gives a distribution; decoding chooses.", 0xFF06B6D4),
        StepCard(2, "Greedy", "Argmax, feed back, never reconsider. Beam with k = 1.", 0xFF10B981),
        StepCard(3, "Beam", "Keep k partials by summed log-probability; prune the rest.", 0xFFF97316),
        StepCard(4, "Split the Errors", "Score gold: did the search lose it, or does the model prefer wrong?", 0xFF8B5CF6),
        StepCard(5, "Spend Where It Pays", "84 model errors; no width fixes those. Fix the model.", 0xFFEC4899),
        StepCard(6, "Know the Training Gap", "Teacher-forced 49.5% vs free-running 32.4%.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Sequence score", "log P(y|x) = Σₜ log P(yₜ | y<ₜ, x)", "What beam search sorts on."),
        FormulaEntry("Beam step", "keep top-k of |V|·k extensions", "Cost is k× a greedy decode, in time and memory."),
        FormulaEntry("Length normalization", "score / |y|", "Or /((5+|y|)/6)^α in the published NMT form."),
        FormulaEntry("Search error", "log P(gold) > log P(returned)", "The model knew; the search missed. Widen the beam."),
        FormulaEntry("Model error", "log P(returned) ≥ log P(gold)", "84 of 120 here. No width helps."),
        FormulaEntry("Exposure bias", "0.495 → 0.324", "Next-symbol accuracy with the gold prefix, then with its own."),
    ),
    notationKey = listOf(
        NotationEntry("k", "beam width — the number of partial hypotheses kept per step"),
        NotationEntry("y<ₜ", "everything decoded before step t; the decoder conditions on its own output"),
        NotationEntry("exact match", "the entire output sequence correct — no partial credit"),
        NotationEntry("search error", "a wrong output the model itself ranks below the right one"),
        NotationEntry("model error", "a wrong output the model ranks above the right one"),
        NotationEntry("teacher forcing", "training the decoder on gold prefixes rather than its own predictions"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Beam search, with the length rule made explicit",
            accentColor = 0xFFF97316,
            code = """
                import torch

                @torch.no_grad()
                def beam_search(model, context, k=5, max_len=50, alpha=0.0):
                    "alpha=0 sorts by total log-prob; alpha=1 sorts by log-prob per token."
                    beams = [([], 0.0, context)]           # tokens, score, decoder state
                    finished = []

                    for _ in range(max_len):
                        candidates = []
                        for tokens, score, state in beams:
                            logits, next_state = model.step(state, tokens[-1] if tokens else SOS)
                            for token, logp in enumerate(torch.log_softmax(logits, -1)):
                                if token == EOS:
                                    finished.append((tokens, score + logp.item()))
                                else:
                                    candidates.append((tokens + [token], score + logp.item(), next_state))
                        if not candidates:
                            break
                        beams = sorted(candidates, key=lambda c: -c[1])[:k]

                    finished += [(t, s) for t, s, _ in beams]
                    return max(finished, key=lambda c: c[1] / (len(c[0]) ** alpha if alpha else 1))

                # alpha is not a free win. It lengthens outputs, which helps when the model stops
                # too early and hurts when it does not -- so measure it on your own data rather
                # than copying a value out of a paper written about a different task.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "The diagnostic worth more than the beam width",
            accentColor = 0xFF8B5CF6,
            code = """
                @torch.no_grad()
                def error_split(model, dataset, k=5):
                    "Sort every failure into 'the search lost it' and 'the model prefers wrong'."
                    correct = search_errors = model_errors = 0
                    for source, gold in dataset:
                        predicted, score = beam_search(model, model.encode(source), k=k)
                        if predicted == gold:
                            correct += 1
                        elif model.score(source, gold) > score:
                            search_errors += 1        # widen the beam
                        else:
                            model_errors += 1         # a wider beam cannot help
                    return correct, search_errors, model_errors

                # On the lab's model: (35, 1, 84) at k=1 and (36, 0, 84) at k=10. Nine tenths of
                # the failures are the model's ranking, so the next hour belongs in the data, the
                # architecture or the objective -- not in the decoder.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("globe", 0xFF14B8A6, "Translation", "Where beam search was tuned, and where length normalization earns its keep."),
        ApplicationCard("music", 0xFFF97316, "Speech & OCR", "Decoding with a beam is standard; the error split tells you if it is helping."),
        ApplicationCard("flask", 0xFF8B5CF6, "Model Debugging", "Score the gold output — one line that says whether to widen or retrain."),
        ApplicationCard("finance", 0xFF10B981, "Inference Cost", "A width-k beam is k forward passes per step, for a measurable return."),
    ),
    takeaways = listOf(
        "The model scores sequences; decoding searches them. Greedy is beam search with width 1.",
        "Widening the beam 1 → 10 raised mean log-probability −2.284 → −1.994 and changed 45 of 120 outputs.",
        "It moved exact match by one sequence, 0.292 → 0.300 — more probable is not more correct.",
        "Scoring the gold sequence splits the failures: 1 search error against 84 model errors at width 1, and 0 against 84 at width 10.",
        "Reversing the encoder's input — free — converted 31 model errors, thirty times what beam width bought.",
        "Length normalization changed 10 of 120 outputs, lengthened them, and moved accuracy 0.000: the source fixes the length here.",
        "Teacher forcing scores 49.5% next-symbol accuracy against 32.4% free-running — the exposure-bias gap on one model.",
    ),
    crossLinks = listOf(
        CrossLink("encoder_decoder", "Encoder-Decoder Architecture"),
        CrossLink("attention", "Attention"),
        CrossLink("rnn_lstm", "RNN / LSTM"),
        CrossLink("llms", "LLMs"),
        CrossLink("bart", "BART"),
    ),
)
