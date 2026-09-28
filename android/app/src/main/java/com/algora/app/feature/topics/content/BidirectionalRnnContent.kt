package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bidirectionalRnnContent = TopicContent(
    topicId = "bidirectional_rnn",
    whatIsIt = listOf(
        "A bidirectional RNN runs two separate recurrent layers over the same sequence — one left to right, one right to left — and labels each position from both states concatenated. It is not a smarter cell and it is not a deeper stack. It is the observation that a left-to-right state at position i contains the words up to i and nothing else, and that for a great many labelling problems the information that settles position i is to the right of it.",
        "That claim can be counted rather than argued. The lab's corpus is twelve sentences built around garden-path minimal pairs — \"the horse raced past the barn\" against \"the horse raced past the barn fell\", where \"raced\" is the main verb in one and a reduced relative in the other. Twelve of its 59 tagged positions sit where two sentences share a prefix and disagree on the tag; zero are ambiguous once the whole sentence is visible. Those twelve fix a ceiling of 53/59 = 0.8983 for *any* left-to-right tagger, and the ceiling is enumerated from the corpus before a model exists.",
        "Trained, the forward tagger scores 0.8983 — the ceiling, to four decimals — and produces a 0.500/0.500 split at every disputed position, because identical prefixes give it identical states and it has learned the only thing available: how often each tag follows. The bidirectional tagger scores 1.000. What that costs is exact and worth stating: 1,799 parameters against 903, two passes instead of one, and no output at all until the sequence ends. This is precisely why BERT is bidirectional and a text-generating decoder cannot be.",
    ),
    steps = listOf(
        StepCard(1, "Find the Ambiguity", "Positions whose tag needs a word further right.", 0xFF06B6D4),
        StepCard(2, "Count It", "12 of 59 tokens here — enumerated, no model involved.", 0xFF3B82F6),
        StepCard(3, "Derive the Ceiling", "53/59 = 0.8983 for any left-to-right tagger.", 0xFF8B5CF6),
        StepCard(4, "Run the Backward Pass", "A second layer over the reversed sequence.", 0xFFF97316),
        StepCard(5, "Concatenate", "[h→ ; h←] per position, then one shared output layer.", 0xFF10B981),
        StepCard(6, "Pay for It", "2× parameters, 2 passes, and no streaming — ever.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Forward state", "h→ᵢ = f(h→ᵢ₋₁, xᵢ)", "Words 1..i."),
        FormulaEntry("Backward state", "h←ᵢ = f(h←ᵢ₊₁, xᵢ)", "Words i..n — a separate layer with its own weights."),
        FormulaEntry("Tagging read-out", "yᵢ = softmax(W[h→ᵢ ; h←ᵢ] + b)", "One output layer over a vector of twice the width."),
        FormulaEntry("Left-to-right ceiling", "0.8983 = 53/59", "Unambiguous positions, plus the majority tag in each ambiguous group."),
        FormulaEntry("Measured, forward", "0.8983 overall · 0.500 ambiguous", "Exactly the ceiling. The optimizer was never the problem."),
        FormulaEntry("Measured, bidirectional", "1.000 overall · 1.000 ambiguous", "At 1,799 parameters against 903."),
    ),
    notationKey = listOf(
        NotationEntry("h→", "the forward layer's state — everything up to and including this position"),
        NotationEntry("h←", "the backward layer's state — this position and everything after it"),
        NotationEntry("[a ; b]", "concatenation, so the output layer sees 2H numbers per position"),
        NotationEntry("ambiguous position", "one where two corpus sentences share a prefix and disagree on the tag"),
        NotationEntry("ceiling", "the best score the architecture can reach, computed from the corpus alone"),
        NotationEntry("garden path", "a sentence whose natural reading is revised by a later word"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "One flag, and where it stops being allowed",
            accentColor = 0xFF10B981,
            code = """
                import torch.nn as nn

                tagger = nn.LSTM(
                    input_size=100, hidden_size=128,
                    bidirectional=True,      # the whole change
                    batch_first=True,
                )
                head = nn.Linear(128 * 2, num_tags)   # 2x: the two states are concatenated

                # What the flag actually did:
                #   - a second LSTM with its own weights, run over the reversed sequence
                #   - the output width doubled, so the head doubled with it
                #   - the layer now needs the ENTIRE sequence before any position is final
                #
                # That last point is not a performance note. A bidirectional tagger cannot label
                # a word as it is typed, transcribed or streamed in -- there is no partial answer
                # to give. If the product needs output before the sentence ends, this flag is off
                # the table no matter what it does to the score.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Compute the ceiling before you train anything",
            accentColor = 0xFF8B5CF6,
            code = """
                from collections import defaultdict

                def left_context_ceiling(corpus):
                    "Best possible accuracy for any model that reads left to right."
                    groups = defaultdict(list)
                    for words, tags in corpus:
                        for i, tag in enumerate(tags):
                            groups[tuple(words[:i + 1])].append(tag)   # the prefix IS the state

                    best = sum(max(tags.count(t) for t in set(tags)) for tags in groups.values())
                    return best / sum(len(tags) for _, tags in corpus)

                # Run this before reaching for a bigger model. If the ceiling is 0.90 and your
                # unidirectional tagger scores 0.89, the missing point is not in the optimizer,
                # the embeddings or the layer count -- it is in the direction. No amount of
                # capacity buys a distinction the input to the classifier does not contain.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFF3B82F6, "Tagging & Parsing", "POS, NER and chunking, where the whole sentence is already in hand."),
        ApplicationCard("crown", 0xFF8B5CF6, "Encoders", "BERT's masked objective exists to make a bidirectional encoder trainable."),
        ApplicationCard("music", 0xFFF97316, "Offline Speech", "Transcribing a recorded file — both directions available, so use both."),
        ApplicationCard("help", 0xFFEC4899, "Where It Cannot Go", "Live captioning, autocomplete, generation: no right context exists yet."),
    ),
    takeaways = listOf(
        "Two layers over the same sequence in opposite directions, tagged from both states concatenated.",
        "The corpus decides the gap: 12 of 59 positions are ambiguous from the left, 0 from both sides.",
        "That yields an enumerated ceiling of 0.8983 for any left-to-right tagger — computed before training.",
        "The trained forward tagger hits 0.8983 exactly and sits at 0.500/0.500 on every disputed position.",
        "The bidirectional tagger reaches 1.000, at 1,799 parameters against 903 and two passes instead of one.",
        "The real cost is latency, not compute: no position is final until the sequence ends, so streaming is impossible.",
        "It is a labelling tool. Anything that generates left to right — a decoder, an autocomplete — cannot use it.",
    ),
    crossLinks = listOf(
        CrossLink("rnn", "RNNs"),
        CrossLink("lstm_gru", "LSTMs / GRUs"),
        CrossLink("pos_tagging", "Part-of-Speech Tagging"),
        CrossLink("elmo", "ELMo"),
        CrossLink("encoder_decoder", "Encoder-Decoder Architecture"),
    ),
)
