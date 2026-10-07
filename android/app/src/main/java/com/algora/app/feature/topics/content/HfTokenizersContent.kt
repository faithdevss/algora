package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.Figure
import com.algora.app.core.data.model.FigureBand
import com.algora.app.core.data.model.FigureShape
import com.algora.app.core.data.model.FigureTone
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val hfTokenizersContent = TopicContent(
    topicId = "hf_tokenizers",
    figure = Figure(
        caption = "The page's lab: BPE trained on fifteen sentences — 40 merges, growing the " +
            "vocabulary from single characters to 61 symbols, the first few being e+· (32 times), " +
            "h+e· and t+he· (27 each) — then applied to a held-out sentence. Common words have " +
            "become whole pieces: \"the\", \"gardener\", \"watched\", \"bird\", each ending in the " +
            "word-end marker ·. \"smallest\" never appeared, so it is spelled from what the merges " +
            "did learn — small, e, st and a bare word end — four pieces where a seen word takes " +
            "one. Nothing is ever unknown, because BPE starts from characters. The sentence " +
            "costs 9 tokens for 6 words, 1.50 per word; more merges would shorten it further at " +
            "the price of a bigger embedding table and rarer pieces to learn.",
        shape = FigureShape.Strip(
            cells = listOf("the·", "gardener·", "watched·", "the·", "small", "e", "st", "·", "bird·"),
            bands = listOf(
                FigureBand(0, 3, "whole learned pieces", FigureTone.Primary),
                FigureBand(4, 7, "\"smallest\": 4 pieces", FigureTone.Warn),
                FigureBand(8, 8, "whole", FigureTone.Primary),
            ),
        ),
    ),
    whatIsIt = listOf(
        "The tokenizers library ships three training algorithms, and the choice between them is usually made by copying whatever a model card said. They differ in what they optimise. BPE merges the most frequent adjacent pair, repeatedly. WordPiece merges the pair that most increases corpus likelihood — score freq(ab)/(freq(a)·freq(b)), which prefers pairs whose halves are rare apart. Unigram goes the other way entirely: start from a large candidate set, score every piece by how much likelihood would be lost without it, and prune. The lab trains the first of them, BPE, on fifteen sentences and applies it to a held-out one.",
        "The number that matters is fertility — pieces per word — because sequence length is attention cost. In the lab, 40 merges grow the vocabulary from single characters to 61 symbols, and the held-out sentence \"the gardener watched the smallest bird\" comes out as 9 tokens for 6 words, 1.50 pieces per word, with nothing unknown. Common words such as \"the\" and \"gardener\" have become single pieces; \"smallest\" never appeared, so it falls back to smaller fragments — BPE starts from characters, so it can always spell a word. The three algorithms disagree most on exactly those words: where BPE replays its fixed merge list, WordPiece takes the longest match from the left (\"garden ##er\") and Unigram searches for the most probable segmentation (\"garden er\") — the only one of the three that can revise an early choice.",
        "One detail explains a hyper-parameter nobody reads. WordPiece's score is maximal — exactly 1 — when both halves of a pair occur once, so on a small corpus the trainer spends its budget on one-off letter pairs and can end up with more pieces per word than plain characters. A minimum pair frequency is the floor that stops it. The real fix is billions of tokens, where singleton pairs are rare — but the floor is what makes the criterion behave at any scale below that.",
    ),
    steps = listOf(
        StepCard(1, "Pre-Tokenize", "Split on whitespace and punctuation before any of this starts.", 0xFF14B8A6),
        StepCard(2, "Train BPE", "Merge the most frequent pair, repeatedly, to a vocabulary budget.", 0xFF3B82F6),
        StepCard(3, "Train WordPiece", "Merge by freq(ab)/(freq(a)·freq(b)) — with a frequency floor.", 0xFFF59E0B),
        StepCard(4, "Train Unigram", "Start large, prune what likelihood can most afford to lose.", 0xFF8B5CF6),
        StepCard(5, "Measure Fertility", "Pieces per word on held-out text: 9 / 6 = 1.50 for the lab's BPE.", 0xFF10B981),
        StepCard(6, "Price It", "Attention is n² — fertility is a permanent tax.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("BPE score", "freq(ab)", "Merge the most common adjacent pair."),
        FormulaEntry("WordPiece score", "freq(ab) / (freq(a)·freq(b))", "Maximal at 1 when both halves occur once."),
        FormulaEntry("Unigram objective", "maximise Σ log P(segmentation)", "Pruned by likelihood loss, decoded by Viterbi."),
        FormulaEntry("Fertility", "pieces / words", "9 tokens / 6 words = 1.50 for the lab's BPE after 40 merges."),
        FormulaEntry("No floor", "singleton pairs score a perfect 1", "Which can leave WordPiece worse than characters — the reason min_frequency exists."),
        FormulaEntry("Attention cost", "n² scores for n pieces", "100 against 81 for 10 pieces against 9."),
    ),
    notationKey = listOf(
        NotationEntry("fertility", "average pieces per word — the sequence-length multiplier"),
        NotationEntry("##", "WordPiece's continuation marker: \"##er\" is a piece that cannot start a word"),
        NotationEntry("</w>", "BPE's end-of-word marker, so \"er\" inside and at the end differ"),
        NotationEntry("Viterbi", "the search Unigram uses to find the most probable segmentation"),
        NotationEntry("[UNK]", "what WordPiece emits when no piece matches; byte-level BPE has none"),
        NotationEntry("min_frequency", "the pair-count floor that stops the likelihood score chasing singletons"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Train all three and compare them on your own text",
            accentColor = 0xFF3B82F6,
            code = """
                from tokenizers import Tokenizer, models, trainers, pre_tokenizers

                def train(model, trainer, files):
                    tok = Tokenizer(model)
                    tok.pre_tokenizer = pre_tokenizers.Whitespace()
                    tok.train(files, trainer)
                    return tok

                V = 8000
                bpe = train(models.BPE(unk_token="[UNK]"),
                            trainers.BpeTrainer(vocab_size=V, min_frequency=2), files)
                wordpiece = train(models.WordPiece(unk_token="[UNK]"),
                                  trainers.WordPieceTrainer(vocab_size=V, min_frequency=2), files)
                unigram = train(models.Unigram(),
                                trainers.UnigramTrainer(vocab_size=V, unk_token="[UNK]"), files)

                def fertility(tok, text):
                    words = text.split()
                    return len(tok.encode(text).tokens) / len(words)

                for name, tok in [("bpe", bpe), ("wordpiece", wordpiece), ("unigram", unigram)]:
                    print(name, f"{fertility(tok, held_out):.3f}",
                          tok.encode("gardener").tokens)

                # Twenty minutes of work that decides your sequence lengths permanently. Note
                # min_frequency: leave it at 0 with the WordPiece trainer on a small corpus and the
                # likelihood score will spend the whole vocabulary on one-off pairs.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Measure the tax before you commit to a tokenizer",
            accentColor = 0xFFEC4899,
            code = """
                def sequence_cost(tok, corpus, max_len=512):
                    lengths = [len(tok.encode(line).tokens) for line in corpus]
                    return {
                        "mean_length": sum(lengths) / len(lengths),
                        "attention_pairs": sum(n * n for n in lengths),
                        "truncated": sum(1 for n in lengths if n > max_len),
                    }

                # Two things this catches that fertility alone does not:
                #   1. attention is quadratic, so a 10% longer mean sequence is ~20% more attention
                #      work on every forward pass, forever;
                #   2. truncation. A fertile tokenizer pushes real documents past the context limit,
                #      and the content that falls off the end is invisible in any loss curve.
                #
                # Run it on YOUR corpus. Fertility on English news says nothing about fertility on
                # code, chemistry, or a morphologically rich language -- which is exactly where the
                # copied-from-a-model-card choice goes wrong.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("globe", 0xFF14B8A6, "Multilingual Models", "Fertility varies enormously by language; measure per language."),
        ApplicationCard("finance", 0xFF10B981, "Cost Control", "Sequence length is the attention bill and the API bill both."),
        ApplicationCard("browser", 0xFF3B82F6, "Domain Adaptation", "Code and chemistry tokenize badly under a news-trained vocabulary."),
        ApplicationCard("help", 0xFFEC4899, "Irreversible Choices", "The tokenizer is fixed before pre-training and cannot be revised after."),
    ),
    takeaways = listOf(
        "Three algorithms, three objectives: frequency (BPE), likelihood (WordPiece), pruning (Unigram).",
        "In the lab, 40 BPE merges (61 symbols) write a held-out 6-word sentence in 9 tokens: 1.50 pieces per word, nothing unknown.",
        "Only Unigram can revise an early choice — it decodes by Viterbi rather than replaying merges.",
        "On an unseen word, all three fall back to smaller pieces; WordPiece emits [UNK] for the whole word only if some part of it cannot be matched (e.g. an unseen character), and character-level BPE does the same for an unseen character.",
        "WordPiece's score is maximal for singleton pairs, so without a frequency floor it can do worse than characters.",
        "A minimum pair frequency fixes it on small corpora; at scale, singleton pairs are rare anyway.",
        "Fertility is a permanent tax: attention is quadratic in length, and the tokenizer is fixed before pre-training begins.",
    ),
    crossLinks = listOf(
        CrossLink("bpe", "Byte-Pair Encoding"),
        CrossLink("tokenization", "Tokenization"),
        CrossLink("roberta", "RoBERTa"),
        CrossLink("bert", "BERT"),
        CrossLink("transformers", "Transformers"),
    ),
)
