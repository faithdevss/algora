package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val tokenizationContent = TopicContent(
    topicId = "tokenization",
    whatIsIt = listOf(
        "Tokenization splits raw text into the smaller units — tokens — that a model actually processes, whether whole words, subwords, or characters.",
        "It's the first step of every NLP pipeline: the choice of token granularity shapes vocabulary size, how rare words are handled, and how much text fits in a model's context.",
    ),
    steps = listOf(
        StepCard(1, "Choose Granularity", "Split into words, characters, or subwords — each trades vocabulary size against sequence length.", 0xFF818CF8),
        StepCard(2, "Handle the Messy Bits", "Punctuation, contractions, casing, and unknown symbols all need consistent rules.", 0xFF60A5FA),
        StepCard(3, "Subword Merging", "BPE/WordPiece build a vocabulary by merging frequent character pairs, so rare words become known pieces.", 0xFF10B981),
        StepCard(4, "Map to IDs", "Each token becomes an integer index the model can embed.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("BPE merge", "merge most frequent pair", "Repeatedly, until the vocab reaches target size."),
        FormulaEntry("Trade-off", "vocab size ↔ sequence length", "Smaller vocab means longer token sequences."),
        FormulaEntry("Coverage", "no true OOV with subwords", "Any word decomposes into known subword units."),
    ),
    notationKey = listOf(
        NotationEntry("token", "the atomic text unit fed to a model"),
        NotationEntry("BPE / WordPiece", "subword tokenization schemes"),
        NotationEntry("OOV", "out-of-vocabulary word"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Subword tokenization (HuggingFace)",
            accentColor = 0xFF6366F1,
            code = """
                from transformers import AutoTokenizer

                tok = AutoTokenizer.from_pretrained("bert-base-uncased")
                ids = tok("Tokenization splits text!")["input_ids"]
                print(tok.convert_ids_to_tokens(ids))   # subword pieces
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("book", 0xFF818CF8, "LLM Input Prep", "Every prompt to a language model is tokenized first; token count drives cost and context limits."),
        ApplicationCard("search", 0xFF60A5FA, "Search & Indexing", "Splitting documents into tokens underlies inverted indexes and matching."),
        ApplicationCard("globe", 0xFF10B981, "Multilingual NLP", "Subword tokenizers handle many scripts and morphologies with one shared vocabulary."),
    ),
    takeaways = listOf(
        "Tokenization converts text into the units a model processes — the pipeline's first step.",
        "Subword methods (BPE, WordPiece) balance vocabulary size against sequence length and kill true OOV.",
        "Token count directly determines LLM context usage and API cost.",
        "The tokenizer must match the model it was trained with.",
    ),
    crossLinks = listOf(
        CrossLink("bow_tfidf", "Bag-of-Words / TF-IDF"),
        CrossLink("word_embeddings", "Word Embeddings"),
    ),
)
