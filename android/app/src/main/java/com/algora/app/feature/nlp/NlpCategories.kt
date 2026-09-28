package com.algora.app.feature.nlp

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Section

// Colors/icons pulled verbatim from docs/design/Algora.dc.html's cats()['nlp'].
// Phase 9's Track D expands this past the mock's two buckets the same way Tracks B and C expanded ML
// and DL: docs/topics.ai.md lists eleven NLP sub-sections, and "Preprocessing" plus "Modeling"
// cannot hold 77 entries browsably. Categories arrive per batch as their topics land.
object NlpCategories {
    val preprocessing = Category("nlp_preprocessing", "Text Preprocessing", Section.NLP, 0xFF14B8A6, "globe")

    // D1. "chart" is the counting these methods all reduce to, and it was unused in this section.
    val statistical = Category("nlp_statistical", "Statistical NLP", Section.NLP, 0xFF8B5CF6, "chart")

    // D2. "browser" reads as structure over text, and it is unused elsewhere in this section.
    val syntax = Category("nlp_syntax", "Syntactic & Semantic Analysis", Section.NLP, 0xFFF59E0B, "browser")

    // D3. "map" is what a projected embedding space is read as, and it is unused in this section.
    val embeddings = Category("nlp_embeddings", "Word Embeddings", Section.NLP, 0xFF6366F1, "map")

    // D4. "chip" is the block diagram these topics live inside; unused elsewhere in this section.
    val transformer = Category("nlp_transformer", "The Transformer Architecture", Section.NLP, 0xFF3B82F6, "chip")

    // D4. "crown" for the frontier models — the flagship tier, and unused in this section.
    val pretrained = Category("nlp_pretrained", "Pre-trained Language Models", Section.NLP, 0xFFEC4899, "crown")

    // D5. "robot" is the agent loop these techniques are built around, and it is unused in this
    // section — dl_basics uses it, but icons only have to be unique within a browser.
    val modernLlm = Category("nlp_modern_llm", "Modern LLM Techniques", Section.NLP, 0xFF10B981, "robot")

    // C5. Same icon and colour as `dl_rnn`, because it is the same subject read from the other
    // section — the doc lists this block under both Deep Learning and NLP.
    val rnn = Category("nlp_rnn", "Recurrent Models", Section.NLP, 0xFFF97316, "history")

    // D6. "flame" for the training run itself — unused in this section.
    val fineTuning = Category("nlp_finetuning", "Fine-Tuning & Optimization", Section.NLP, 0xFFEF4444, "flame")

    // D6. "flask" for the architectures still being argued about, and unused in this section.
    val beyondTransformers = Category("nlp_beyond", "Beyond Transformers", Section.NLP, 0xFF06B6D4, "flask")

    // D6. Shares `ml_metrics`' sky blue because it is the same job read from the other section — the
    // precedent is `nlp_rnn` taking `dl_rnn`'s colour in C5. The icon differs on purpose: ML's
    // metrics check a prediction against a label, and these six score a string against a reference,
    // which is what "target" reads as. Unused in this section either way.
    val metrics = Category("nlp_metrics", "NLP Metrics", Section.NLP, 0xFF0EA5E9, "target")

    // `nlp_modeling` is gone. It was the mock's catch-all, and C5 took its last topic (`rnn_lstm`)
    // into the category above — the same hollowing-out that retired `ml_unsupervised` in B6.
    val all = listOf(
        preprocessing, statistical, syntax, embeddings, rnn, transformer, pretrained, modernLlm,
        fineTuning, beyondTransformers, metrics,
    )
}
