package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureArrow
import com.algora.app.core.data.model.FigureCell
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bidirectionalRnnContent = TopicContent(
    topicId = "bidirectional_rnn",
    figure = Figure(
        caption = "The lab's garden-path sentence under both readings, tagged. Up to \"barn\" the " +
            "two are word-for-word identical, and \"raced\" is a main verb in the first and a " +
            "reduced relative — a past participle — in the second. Nothing to its left " +
            "distinguishes them, so a left-to-right RNN reaches the marked column in exactly the " +
            "same 128-dimensional state both times, and one state cannot produce two different " +
            "tags. The evidence is \"fell\", four words later. The bidirectional RNN adds a second " +
            "pass from the right, so its backward state at \"raced\" has already read \"fell\" " +
            "(the arrow), and the tagger reads both states joined into 256 dimensions. The cost is " +
            "twice the recurrent parameters, two passes, and no output until the sentence ends — " +
            "fine for labelling a finished sentence, impossible for generating one.",
        shape = FigureShape.Grid(
            rows = listOf(
                listOf("DT", "NN", "VBD", "IN", "DT", "NN", "—"),
                listOf("DT", "NN", "VBN", "IN", "DT", "NN", "VBD"),
            ),
            rowHeaders = listOf("…barn", "…barn fell"),
            colHeaders = listOf("the", "horse", "raced", "past", "the", "barn", "fell"),
            marks = listOf(
                FigureCell(0, 2, FigureTone.Warn),
                FigureCell(1, 2, FigureTone.Warn),
                FigureCell(1, 6, FigureTone.Accent),
            ),
            arrows = listOf(
                FigureArrow(1, 6, 1, 2, FigureTone.Accent, "backward"),
            ),
        ),
    ),
    whatIsIt = listOf(
        "A bidirectional RNN runs two separate recurrent layers over the same sequence — one left to right, one right to left — and labels each position from both states concatenated. It is not a smarter cell and it is not a deeper stack. It is the observation that a left-to-right state at position i contains the words up to i and nothing else, and that for a great many labelling problems the information that settles position i is to the right of it.",
        "The lab walks one garden-path sentence word by word: \"the horse raced past the barn fell\". Read up to \"barn\" it looks complete, with \"raced\" as the main verb. \"fell\" reveals the other reading — \"the horse [that was] raced past the barn fell\" — so \"raced\" was a past participle all along. At \"raced\" a forward RNN has seen three words, and those three words are identical under both readings, so its 128-dimensional state is identical too. No amount of training can make one state produce two different tags; the information is four words to the right.",
        "The bidirectional version adds a second RNN reading right to left, so at \"raced\" its backward state has already seen \"fell\", and the tagger reads both states concatenated — 2 × 128 = 256 dimensions per word. That is the whole mechanism, and its cost is exact: twice the recurrent parameters, two passes instead of one, and no output at any position until the sequence has ended. Labelling a finished sentence can afford that, which is why taggers and BERT-style encoders read both ways; a text generator cannot, because the words to the right have not been written yet.",
    ),
    steps = listOf(
        StepCard(1, "Find the Ambiguity", "Positions whose tag needs a word further right.", 0xFF06B6D4),
        StepCard(2, "Run Forward", "At \"raced\" the forward state has seen 3 words — the same 3 under both readings.", 0xFF3B82F6),
        StepCard(3, "Hit the Wall", "Identical state, two possible tags: the forward RNN cannot tell them apart.", 0xFF8B5CF6),
        StepCard(4, "Run the Backward Pass", "A second layer over the reversed sequence.", 0xFFF97316),
        StepCard(5, "Concatenate", "[h→ ; h←] per position, then one shared output layer.", 0xFF10B981),
        StepCard(6, "Pay for It", "2× parameters, 2 passes, and no streaming — ever.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Forward state", "h→ᵢ = f(h→ᵢ₋₁, xᵢ)", "Words 1..i."),
        FormulaEntry("Backward state", "h←ᵢ = f(h←ᵢ₊₁, xᵢ)", "Words i..n — a separate layer with its own weights."),
        FormulaEntry("Tagging read-out", "yᵢ = softmax(W[h→ᵢ ; h←ᵢ] + b)", "One output layer over a vector of twice the width."),
        FormulaEntry("State width", "128 → 2 × 128 = 256", "Each direction keeps its own 128-dim state; the read-out sees both."),
        FormulaEntry("Context at word i", "→: words 1..i · ←: words i..n", "At \"raced\" (i = 3 of 7): forward 3 words, backward 5 — including \"fell\"."),
        FormulaEntry("Cost", "2× recurrent parameters · 2 passes", "And no output until the sequence ends."),
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
        "At \"raced\" a forward RNN's state is identical under both readings of the garden-path sentence — the deciding word, \"fell\", is 4 words to the right.",
        "When two inputs share a prefix but need different labels, no left-to-right model can separate them; the limit is the direction, not the capacity.",
        "The backward state at \"raced\" has already seen \"fell\"; concatenating the two (256 dims) gives the tagger that context.",
        "The price is 2× the recurrent parameters and two passes per sequence.",
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
