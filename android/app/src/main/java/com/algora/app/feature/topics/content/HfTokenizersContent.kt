package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val hfTokenizersContent = TopicContent(
    topicId = "hf_tokenizers",
    whatIsIt = listOf(
        "The tokenizers library ships three training algorithms, and the choice between them is usually made by copying whatever a model card said. They differ in what they optimise. BPE merges the most frequent adjacent pair, repeatedly. WordPiece merges the pair that most increases corpus likelihood — score freq(ab)/(freq(a)·freq(b)), which prefers pairs whose halves are rare apart. Unigram goes the other way entirely: start from a large candidate set, score every piece by how much likelihood would be lost without it, and prune. All three are trained in this lab on one corpus, to one vocabulary budget, and compared on held-out text.",
        "The number that matters is fertility — pieces per word — because sequence length is attention cost. On the held-out sentence, BPE gives 1.667 pieces per word, WordPiece 1.667, and Unigram 1.500. Where they disagree is more instructive than the average: \"gardener\" comes out as one piece under BPE, \"garden ##er\" under WordPiece and \"garden er\" under Unigram, because BPE replays a fixed merge list, WordPiece takes the longest match from the left, and Unigram searches for the most probable segmentation — the only one of the three that can revise an early choice. On a word the corpus never contained, BPE falls back to characters, Unigram to its smallest pieces, and WordPiece emits [UNK] and loses the word outright.",
        "One measured detail explains a hyper-parameter nobody reads. WordPiece's score is maximal — exactly 1 — when both halves of a pair occur once, so on a small corpus the trainer spends its whole budget on one-off letter pairs: fertility comes out at 4.333 pieces per word, *worse than characters*. With a minimum pair frequency of 4 it reaches 1.667 with a 28% smaller vocabulary than BPE needed. The real fix is billions of tokens, where singleton pairs are rare — but the floor is what makes the criterion behave at any scale below that, and fertility being worse than the baseline is how the lab's first run caught its own bug.",
    ),
    steps = listOf(
        StepCard(1, "Pre-Tokenize", "Split on whitespace and punctuation before any of this starts.", 0xFF14B8A6),
        StepCard(2, "Train BPE", "Merge the most frequent pair, repeatedly, to a vocabulary budget.", 0xFF3B82F6),
        StepCard(3, "Train WordPiece", "Merge by freq(ab)/(freq(a)·freq(b)) — with a frequency floor.", 0xFFF59E0B),
        StepCard(4, "Train Unigram", "Start large, prune what likelihood can most afford to lose.", 0xFF8B5CF6),
        StepCard(5, "Measure Fertility", "Pieces per word on held-out text: 1.667 / 1.667 / 1.500.", 0xFF10B981),
        StepCard(6, "Price It", "Attention is n² — fertility is a permanent tax.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("BPE score", "freq(ab)", "Merge the most common adjacent pair."),
        FormulaEntry("WordPiece score", "freq(ab) / (freq(a)·freq(b))", "Maximal at 1 when both halves occur once."),
        FormulaEntry("Unigram objective", "maximise Σ log P(segmentation)", "Pruned by likelihood loss, decoded by Viterbi."),
        FormulaEntry("Fertility", "pieces / words", "BPE 1.667 · WordPiece 1.667 · Unigram 1.500."),
        FormulaEntry("No floor", "4.333 pieces per word", "Worse than characters — the reason min_frequency exists."),
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
        "Trained on one corpus to one budget, fertility came out 1.667 / 1.667 / 1.500 pieces per word.",
        "Only Unigram can revise an early choice — it decodes by Viterbi rather than replaying merges.",
        "On an unseen word, BPE and Unigram fall back to small pieces; WordPiece emits [UNK] and loses it.",
        "WordPiece's score is maximal for singleton pairs, so without a frequency floor fertility is 4.333 — worse than characters.",
        "With the floor at 4 it matches BPE's fertility using a 28% smaller vocabulary.",
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
