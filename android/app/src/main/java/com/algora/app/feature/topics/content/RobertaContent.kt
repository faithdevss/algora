package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val robertaContent = TopicContent(
    topicId = "roberta",
    whatIsIt = listOf(
        "RoBERTa changed no part of BERT's architecture. Same layer count, same width, same masked-language-modelling objective — the paper is a list of things BERT's *training run* did that turned out to be suboptimal. Dynamic masking instead of static, no next-sentence prediction, batches of 8,000 sequences instead of 256, 160GB of text instead of 16GB, and a 50,265-piece byte-level BPE vocabulary instead of 30,522 WordPiece pieces. Every gain came from the recipe, which is the most useful kind of result: it says the reported numbers were a training run and not a ceiling on the architecture.",
        "The masking change is the cheapest one and the easiest to state exactly. BERT baked its masks into the data — duplicate the corpus ten times with a different mask each time, then train for forty epochs, so every mask pattern is seen four times. A token is selected with probability 0.15 per masking, so after k independent maskings the chance it was never selected at all is 0.85ᵏ. Under ten static masks that is 19.7% of tokens never predicted; under forty dynamic maskings it is 0.15%. One in five against one in 650, from a change that costs a line of data loading.",
        "The vocabulary change is the one that shows up in the parameter count. Byte-level BPE at 50,265 entries against WordPiece at 30,522 makes the embedding table 39,000,576 parameters against BERT's 23,837,184 — 64% more — while the twelve layers are byte-for-byte identical, taking the model from 109,482,240 to 124,645,632. What it buys is the elimination of [UNK] entirely: with the 256 bytes as the base vocabulary, every string is representable. What it costs is fertility, more pieces per word on anything unusual, and since attention is quadratic in sequence length that cost is paid on every forward pass for the life of the model.",
    ),
    steps = listOf(
        StepCard(1, "Keep the Architecture", "Nothing about the model changes. Nothing.", 0xFF3B82F6),
        StepCard(2, "Re-Mask Every Epoch", "Dynamic masking: 0.85⁴⁰ against 0.85¹⁰.", 0xFFF97316),
        StepCard(3, "Drop NSP", "The second objective was doing no measurable work.", 0xFF8B5CF6),
        StepCard(4, "Scale the Batch and Data", "256 → 8,000 sequences, 16GB → 160GB.", 0xFF10B981),
        StepCard(5, "Switch the Tokenizer", "Byte-level BPE: no [UNK], 64% larger embedding table.", 0xFF6366F1),
        StepCard(6, "Re-Read the Baseline", "BERT's numbers were a run, not a limit.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Never predicted", "0.85ᵏ after k maskings", "19.7% at k = 10, 0.15% at k = 40."),
        FormulaEntry("Static schedule", "10 masks × 4 epochs each", "40 epochs, 10 distinct patterns."),
        FormulaEntry("Dynamic schedule", "40 masks, one per epoch", "Same compute, four times the mask variety."),
        FormulaEntry("Embedding table", "50,265 × 768 = 38,603,520", "Against BERT's 30,522 × 768."),
        FormulaEntry("Total size", "124,645,632", "Layers unchanged at 85,054,464."),
        FormulaEntry("What byte-BPE removes", "[UNK]", "256 bytes as base — every string is representable."),
    ),
    notationKey = listOf(
        NotationEntry("static masking", "masks fixed in the pre-processed data and reused across epochs"),
        NotationEntry("dynamic masking", "a fresh mask drawn every time a sequence is seen"),
        NotationEntry("k", "number of independent maskings a sequence receives"),
        NotationEntry("NSP", "next-sentence prediction — BERT's second objective, removed here"),
        NotationEntry("byte-level BPE", "BPE over raw bytes, so no input is ever unrepresentable"),
        NotationEntry("fertility", "pieces per word — what a larger vocabulary is trying to reduce"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Static and dynamic masking, side by side",
            accentColor = 0xFFF97316,
            code = """
                # BERT: mask once, at pre-processing time, and reuse.
                def build_static_dataset(corpus, duplicates=10):
                    return [mask_tokens(seq.clone(), tokenizer) for _ in range(duplicates) for seq in corpus]
                # 40 epochs over this sees each mask pattern 4 times, and any token that was never
                # selected in those 10 draws is never predicted at all.

                # RoBERTa: mask in the collator, so every epoch is a new draw.
                class DynamicMaskingCollator:
                    def __call__(self, batch):
                        return mask_tokens(torch.stack(batch).clone(), tokenizer)

                print(f"never predicted, 10 static masks:  {0.85 ** 10:.4f}")   # 0.1969
                print(f"never predicted, 40 dynamic masks: {0.85 ** 40:.4f}")   # 0.0015
                # Same compute budget. One in five tokens against one in 650.
            """.trimIndent(),
        ),
        CodeBlock(
            title = "What the vocabulary change costs in parameters",
            accentColor = 0xFF6366F1,
            code = """
                def embedding_parameters(vocab, d=768, positions=512, types=2, norm=True):
                    return vocab * d + positions * d + types * d + (2 * d if norm else 0)

                bert = embedding_parameters(30522)                       # 23,837,184
                roberta = embedding_parameters(50265, positions=514, types=1)   # 39,000,576
                print(f"{roberta / bert - 1:+.1%}")                      # +63.6%

                # The twelve layers are identical -- 85,054,464 in both models. Every parameter of
                # the difference is vocabulary. Worth knowing before choosing a tokenizer for a
                # model you intend to fit on a particular GPU: this is the one part of the size
                # you decide before training and cannot revise afterwards.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("crown", 0xFF3B82F6, "A Better BERT", "A drop-in replacement wherever BERT-base was the encoder."),
        ApplicationCard("flask", 0xFF8B5CF6, "How to Ablate", "The template for isolating recipe effects from architecture effects."),
        ApplicationCard("chart", 0xFF10B981, "Benchmark Reading", "A reminder that a published score is one training run."),
        ApplicationCard("finance", 0xFFEC4899, "Vocabulary Budget", "50k pieces is 15M extra parameters before any layer exists."),
    ),
    takeaways = listOf(
        "Not an architecture paper: same layers, same width, same objective, better training.",
        "Static masking reuses 10 patterns across 40 epochs, so 0.85¹⁰ = 19.7% of tokens are never predicted.",
        "Dynamic masking makes that 0.85⁴⁰ = 0.15% for the same compute — one in 650 instead of one in five.",
        "NSP was removed because the ablation showed it contributing nothing.",
        "Byte-level BPE at 50,265 pieces grows the embedding table 64%, to 39,000,576, with the layers unchanged.",
        "Total size 109,482,240 → 124,645,632, and [UNK] disappears entirely.",
        "The general lesson: check whether a baseline's number is a limit of the architecture or of the run that produced it.",
    ),
    crossLinks = listOf(
        CrossLink("bert", "BERT"),
        CrossLink("distilbert", "DistilBERT"),
        CrossLink("hf_tokenizers", "Hugging Face Tokenizers"),
        CrossLink("bpe", "Byte-Pair Encoding"),
        CrossLink("transformers", "Transformers"),
    ),
)
