package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigurePoint
import com.algora.app.core.data.model.FigureSeries
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val perplexityContent = TopicContent(
    topicId = "perplexity",
    figure = Figure(
        caption = "The add-k sweep over both test sentences, scored against the ceiling a model " +
            "that learned nothing would hit — uniform over 19 words is perplexity exactly 19, so " +
            "1.0 here is \"knows nothing\". On \"the cat sat on the rug\", whose bigrams were all " +
            "attested, smoothing only takes mass away from events that happened: the curve is " +
            "monotone the wrong way, 2.371 at k=0.001 up to 8.229 at k=1, so add-1 costs 3.47× and " +
            "the unsmoothed model is better still at 2.358. Swap in \"a cat swam in the bowl\" and " +
            "the unsmoothed model reports infinity — not a bad score, a claim that the sentence is " +
            "impossible — and only then does an interior optimum appear, at k=0.01. Whether " +
            "smoothing helps is a property of the test set, not of the smoother.",
        shape = FigureShape.Plot(
            series = listOf(
                FigureSeries(
                    "all bigrams attested",
                    listOf(
                        FigurePoint(0f, 0.125f), FigurePoint(0.303f, 0.131f),
                        FigurePoint(0.606f, 0.180f), FigurePoint(0.818f, 0.322f),
                        FigurePoint(0.909f, 0.433f), FigurePoint(1f, 0.568f),
                    ),
                    FigureTone.Primary,
                ),
                FigureSeries(
                    "one unseen bigram",
                    listOf(
                        FigurePoint(0f, 0.324f), FigurePoint(0.303f, 0.253f),
                        FigurePoint(0.606f, 0.295f), FigurePoint(0.818f, 0.481f),
                        FigurePoint(0.909f, 0.604f), FigurePoint(1f, 0.727f),
                    ),
                    FigureTone.Warn,
                ),
            ),
            xLabel = "add-k, log scale (0.001 → 2)",
            yLabel = "PPL ÷ 19",
            markers = listOf(
                FigurePoint(0.303f, 0.253f, "optimum, k=0.01", FigureTone.Accent),
                FigurePoint(0f, 0.125f, "less k is better", FigureTone.Accent),
            ),
        ),
    ),
    whatIsIt = listOf(
        "Perplexity is the only metric in this category that needs no reference answer — just a model and some text. Score every token by how surprised the model was to see it, average the surprisal, exponentiate, and read the result as a branching factor: perplexity 8.229 means the model was as uncertain as one choosing uniformly among 8.2 words at each step.",
        "That reading gives it a ceiling worth knowing. A model that has learned nothing and spreads probability evenly over the lab's 19-word vocabulary has perplexity exactly 19 — the vocabulary size. The trained bigram model's 8.229 is therefore a claim that corpus statistics cut the effective choice by **2.3×**, and nothing more than that.",
        "**Smoothing is not free, which the lab demonstrates rather than asserts.** On a test sentence whose bigrams were all attested, add-k only takes probability mass away from events that happened: the sweep runs monotonically the wrong way, 2.371 at k=0.001 up to 8.229 at k=1, so add-1 costs **3.47×** on that sentence. Swap in a sentence containing one unattested bigram (`cat swam`) and the unsmoothed model reports **infinity** — it does not score the sentence badly, it declares it impossible. Only then does the sweep acquire an interior optimum, at k=0.01. Whether smoothing helps is a property of the test set, not of the smoother.",
        "**And the trap that makes published perplexities unusable: it is a per-token number, so it is a property of the tokenizer as much as the model.** The same corpus scored by a character model reports perplexity 5.916 against the word model's 8.229 — apparently better. Convert both to bits per character, which *is* comparable, and the ranking inverts: 1.252 against 3.470, the word model ahead by 2.8×. Two models, two numbers, opposite conclusions.",
    ),
    steps = listOf(
        StepCard(1, "Score Each Token", "Take −log P(token | context) under the model. This is the surprisal, in bits.", 0xFF0EA5E9),
        StepCard(2, "Average, Then Exponentiate", "Perplexity is exp of the mean negative log-likelihood — the geometric mean of 1/P.", 0xFF3B82F6),
        StepCard(3, "Compare To The Vocabulary", "A uniform model scores exactly |V|. Everything below that is what the model learned.", 0xFF6366F1),
        StepCard(4, "Check The Zeros", "One unseen n-gram makes an unsmoothed score infinite. Smoothing is the insurance premium.", 0xFF8B5CF6),
        StepCard(5, "Convert Before Comparing", "Different tokenizer means different units. Bits per character is the common one.", 0xFFEC4899),
        StepCard(6, "Never Quote It Across Papers", "The tokenizer, the vocabulary and the test set all move it more than model quality does.", 0xFFEF4444),
    ),
    formulas = listOf(
        FormulaEntry("Perplexity", "PP(W) = exp(−(1/N) Σ ln P(wᵢ | context))", "The geometric mean of the inverse probabilities."),
        FormulaEntry("Surprisal", "−log₂ P(wᵢ | context)", "Bits. The lab's hardest token costs 4.000."),
        FormulaEntry("Uniform bound", "PP = |V| = 19", "What a model that learned nothing scores."),
        FormulaEntry("Add-k smoothing", "P(w|c) = (count(c,w) + k) / (count(c) + k|V|)", "Costs 3.47× where no zero needed rescuing."),
        FormulaEntry("Bits per character", "Σ surprisal / characters", "Word 1.252, character 3.470 — the comparable number."),
        FormulaEntry("Relation", "PP = 2^(bits per token)", "Which is why the per-token unit has to be stated."),
    ),
    notationKey = listOf(
        NotationEntry("surprisal", "how unexpected one token was, in bits — its negative log probability"),
        NotationEntry("branching factor", "the equivalent number of equally likely choices per step"),
        NotationEntry("add-k / Laplace", "reserve mass for unseen events by adding k to every count"),
        NotationEntry("bits per character", "cross-entropy normalised by characters instead of tokens"),
        NotationEntry("closed vocabulary", "a model that cannot represent a word it never saw — the word model here"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Perplexity, and the zero it cannot survive",
            accentColor = 0xFF0EA5E9,
            code = """
                import math

                def perplexity(model, tokens, k=1.0):
                    total = 0.0
                    for prev, nxt in zip(tokens, tokens[1:]):
                        total += math.log(model.prob(prev, nxt, k))
                    return math.exp(-total / (len(tokens) - 1))

                # Every bigram attested -> smoothing is a pure cost:
                #   k=0.001 -> 2.371     k=0.5 -> 6.115     k=1.0 -> 8.229
                #
                # One unattested bigram ("cat swam") -> k=0 is infinite, and the
                # optimum moves inside the sweep:
                #   k=0.001 -> 6.153     k=0.01 -> 4.798    k=1.0 -> 11.471
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Why two perplexities cannot be compared",
            accentColor = 0xFFEC4899,
            code = """
                # Same corpus, same model shape, two tokenizers.
                word_ppl = 8.229        # 19-word vocabulary
                char_ppl = 5.916        # 21-symbol vocabulary

                # The character model looks better. Normalise to bits per character:
                word_bpc = 1.252
                char_bpc = 3.470        # the ranking inverts

                # In PyTorch, perplexity is exp of the mean cross-entropy:
                loss = F.cross_entropy(logits.view(-1, V), targets.view(-1))
                ppl  = torch.exp(loss)

                # Report the tokenizer with it, or report bits per character --
                # and check both models can encode the same text before you do.
                # A closed 19-word vocabulary never has to spell anything.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("chart", 0xFF0EA5E9, "Training Curves", "The number watched during pretraining, where the tokenizer is fixed and comparison is valid."),
        ApplicationCard("target", 0xFF3B82F6, "Domain Fit", "Perplexity on held-out domain text says whether a model has seen this kind of writing."),
        ApplicationCard("search", 0xFF10B981, "Data Filtering", "Low-perplexity-under-a-reference-model is a common quality filter for pretraining corpora."),
        ApplicationCard("help", 0xFFF59E0B, "What It Cannot Do", "It does not measure usefulness, truth or instruction-following — only next-token fit."),
    ),
    takeaways = listOf(
        "Perplexity is the exponentiated average surprisal, read as a branching factor — 8.229 here against a uniform bound of exactly 19.",
        "Smoothing is a premium, not a free improvement: it costs 3.47× on a sentence with no unseen bigrams and saves you from infinity on one that has them.",
        "The optimum k is a property of the test set — the floor of the sweep on attested text, an interior 0.01 when a zero appears.",
        "Perplexity is per token, so two tokenizers give incomparable numbers: 5.916 beats 8.229 while 3.470 loses to 1.252, same two models.",
        "Compare perplexities only within one tokenizer, bits per character across tokenizers, and never across papers.",
    ),
    crossLinks = listOf(
        CrossLink("n_grams", "N-Grams"),
        CrossLink("llms", "LLMs"),
        CrossLink("bpe", "Byte-Pair Encoding"),
        CrossLink("log_loss", "Log Loss (Cross-Entropy)"),
    ),
)
