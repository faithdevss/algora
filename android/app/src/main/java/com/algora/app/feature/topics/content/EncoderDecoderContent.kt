package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val encoderDecoderContent = TopicContent(
    topicId = "encoder_decoder",
    figure = Figure(
        caption = "Two networks, one vector between them, and a measurement of what that vector " +
            "kept. The encoder reads six symbols over a six-symbol alphabet — 15.5 bits — and " +
            "hands the decoder 12 tanh-squashed numbers; a linear probe fitted from those numbers " +
            "to one source position, encoder frozen, says which positions survived the handover. " +
            "Fed forward, the vector holds the end of the source: position 6 is recoverable 85% " +
            "of the time and position 1 — the symbol the decoder needs first — 34%. Feed the same " +
            "encoder the same sources backwards and the profile flips to 98% and 33%, taking " +
            "exact match from 0.292 to 0.542 with 571 parameters either way. Reversal does not " +
            "widen the channel, it reorders what goes through it, which is why the copy task " +
            "still collapses to zero by length four in both directions. Attention removes the " +
            "channel instead.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("0.34", "0.85", "0.292"),
                listOf("0.98", "0.33", "0.542"),
            ),
            rowHeaders = listOf("forward", "reversed"),
            colHeaders = listOf("probe p₁", "probe p₆", "exact match"),
            marks = listOf(
                FigureCell(0, 1),
                FigureCell(1, 0),
                FigureCell(1, 2, FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "An encoder-decoder is two recurrent networks joined by a single vector. The encoder reads the input and its final hidden state — the context vector — becomes the decoder's initial state; the decoder then generates until it emits an end symbol. The arrangement is what let sequence models produce sequences of a different length from their input, which is the whole of machine translation, summarisation and speech transcription. Everything the decoder will ever know about the source has to be in that one vector.",
        "The lab measures what that costs by asking for the least it possibly could: copy the source. No transformation, no alignment, no vocabulary mismatch — so every failure is the vector losing the input rather than the model failing to compute something. With 12 hidden units and 571 parameters, exact-match accuracy is 100% at one symbol, 48% at two, and zero from four on. A six-symbol source over a six-symbol alphabet is 15.5 bits; the context vector has 12 tanh-squashed numbers to hold them in, and the accuracy curve is what that mismatch looks like from the outside.",
        "Which part of the source survives is measurable too. Freeze the encoder, fit a linear read-out from the context vector to the symbol at one position, and score it on held-out sources: position 6 is recoverable 85% of the time and position 1 — the one the decoder needs first — 34%. The vector remembers what it read last. Feed the same encoder the same sources backwards and the profile flips to 98% at position 1, taking exact match from 0.292 to 0.542 with no extra parameters. That is Sutskever et al.'s 2014 reversal trick, and its mechanism is visible rather than asserted. It also does not fix the bottleneck — it moves it, which is why attention replaced the whole arrangement.",
    ),
    steps = listOf(
        StepCard(1, "Encode", "Read the source; keep the final state.", 0xFF06B6D4),
        StepCard(2, "Hand Over", "That state is the decoder's h₀ — the only channel between them.", 0xFFF97316),
        StepCard(3, "Decode", "Emit a symbol, feed it back, repeat until EOS.", 0xFF10B981),
        StepCard(4, "Watch Length Bite", "100% at one symbol, 48% at two, 0% at four.", 0xFFEC4899),
        StepCard(5, "Probe the Vector", "A linear read-out says position 6 survives, position 1 does not.", 0xFF8B5CF6),
        StepCard(6, "Reverse the Source", "Free, and it nearly doubles exact match — by reordering, not enlarging.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Context vector", "c = h_enc(T)", "The encoder's final state, fixed width whatever T is."),
        FormulaEntry("Decoder init", "h_dec(0) = c", "The entire interface between the two networks."),
        FormulaEntry("Decoder step", "yₜ = softmax(W·h_dec(t) + b)", "Conditioned on c and on everything emitted so far."),
        FormulaEntry("Source capacity", "L·log₂|V| = 15.5 bits at L = 6", "Against 12 real numbers in the context vector."),
        FormulaEntry("Measured, forward-fed", "0.292 exact · probe p1 0.34 / p6 0.85", "The vector holds the end of the source."),
        FormulaEntry("Measured, reverse-fed", "0.542 exact · probe p1 0.98 / p6 0.33", "Same parameters, reading order reversed."),
    ),
    notationKey = listOf(
        NotationEntry("c", "the context vector — the encoder's last state, the decoder's first"),
        NotationEntry("T", "source length; the context vector's width does not depend on it"),
        NotationEntry("EOS", "the end symbol the decoder emits to stop; without it there is no stopping rule"),
        NotationEntry("probe", "a linear classifier fitted from c to one source position, encoder frozen"),
        NotationEntry("exact match", "the whole output correct — no partial credit, which is the honest metric here"),
        NotationEntry("reversal", "feeding the encoder the source backwards, so the first symbol is read last"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The whole architecture, and the one line that is the bottleneck",
            accentColor = 0xFFF97316,
            code = """
                import torch
                import torch.nn as nn

                class EncoderDecoder(nn.Module):
                    def __init__(self, vocab, hidden):
                        super().__init__()
                        self.encoder = nn.GRU(vocab, hidden, batch_first=True)
                        self.decoder = nn.GRU(vocab, hidden, batch_first=True)
                        self.out = nn.Linear(hidden, vocab)

                    def forward(self, source, target_in):
                        _, context = self.encoder(source)      # <- everything, in one vector
                        states, _ = self.decoder(target_in, context)
                        return self.out(states)

                # Sutskever et al. (2014) reversed the source and gained several BLEU. It costs
                # one call and changes no parameter count:
                #     source = torch.flip(source, dims=[1])
                # It works by shortening the distance between the source tokens the decoder needs
                # first and the handover -- so it helps most when the alignment is roughly
                # monotonic, and does nothing when it is not.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Probe the context vector instead of guessing what it kept",
            accentColor = 0xFF8B5CF6,
            code = """
                import torch
                import torch.nn as nn

                @torch.no_grad()
                def contexts(model, sources):
                    return torch.cat([model.encoder(s)[1].squeeze(0) for s in sources])

                def probe(model, sources, position, hidden, vocab, steps=400):
                    "How recoverable is source[position] from the context vector alone?"
                    x = contexts(model, sources)                 # encoder frozen: no grad above
                    y = torch.tensor([s[0, position].argmax() for s in sources])
                    n = int(0.8 * len(y))                        # fit on 80%, score on HELD-OUT 20%
                    readout = nn.Linear(hidden, vocab)
                    optimizer = torch.optim.Adam(readout.parameters(), lr=0.05)
                    for _ in range(steps):
                        loss = nn.functional.cross_entropy(readout(x[:n]), y[:n])
                        optimizer.zero_grad(); loss.backward(); optimizer.step()
                    return (readout(x[n:]).argmax(1) == y[n:]).float().mean().item()

                # Run it per position and read the shape, not the average. A profile that slopes
                # up toward the end of the source is recency -- the vector is holding the tail.
                # That is the diagnosis reversal treats, and the one attention removes outright.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("globe", 0xFF14B8A6, "Machine Translation", "The architecture NMT was built on, and the one attention was added to."),
        ApplicationCard("music", 0xFFF97316, "Speech Recognition", "Audio frames in, characters out — different lengths, different rates."),
        ApplicationCard("book", 0xFF8B5CF6, "Summarisation", "Long source, short target: exactly where a fixed vector hurts most."),
        ApplicationCard("chip", 0xFF3B82F6, "What Came Next", "Attention, then the transformer — both are answers to this one vector."),
    ),
    takeaways = listOf(
        "Two networks joined by one vector: the encoder's final state is the decoder's initial state.",
        "It is what let sequence models emit outputs of a different length from their input.",
        "On the weakest possible task — copying — exact match runs 100% at one symbol, 48% at two, 0% by four.",
        "Stated as capacity: a 6-symbol source is 15.5 bits and the context vector is 12 squashed numbers.",
        "A linear probe of the frozen encoder shows the vector holds the *end* of the source: p6 0.85, p1 0.34.",
        "Reversing the source flips that to p1 0.98 and takes exact match 0.292 → 0.542, for no extra parameters.",
        "Reversal moves the bottleneck rather than removing it — length 6 stays near zero either way. Attention removes it.",
    ),
    crossLinks = listOf(
        CrossLink("seq2seq", "Seq2Seq Models"),
        CrossLink("attention", "Attention"),
        CrossLink("lstm_gru", "LSTMs / GRUs"),
        CrossLink("transformers", "Transformers"),
        CrossLink("autoencoders", "Autoencoders"),
    ),
)
