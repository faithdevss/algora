package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val bertContent = TopicContent(
    topicId = "bert",
    whatIsIt = listOf(
        "BERT is a transformer encoder trained by filling in blanks. Fifteen percent of the positions in each sequence are selected, corrupted, and predicted from everything around them — no causal mask anywhere, so every position sees every other one. That is only possible because BERT is never asked to continue text, only to reconstruct it, and it is the reason BERT cannot generate: there is no next-token objective anywhere in the model.",
        "The trade that objective makes is countable, and both sides of it are worth having in mind. On the lab's corpus a causal objective turns all 64 tokens into training targets; masking at 15% turns 10 of them into targets — the same forward pass for 6.4× less signal. In exchange each prediction gets twice the context: averaged over the corpus, a causal prediction sees 2.72 tokens and a masked one 5.44, because it can read to the right as well as the left. Fewer, richer predictions, which is why BERT-style models need more passes over their data than their parameter count suggests.",
        "The 80/10/10 rule is the part most summaries skip, and it exists to patch a mismatch that is easy to state as a number: [MASK] appears on 12% of input positions during pre-training and on 0% of them afterwards. A model allowed to key off that token would learn a feature that vanishes the moment it is used. So of the selected positions, 80% become [MASK], 10% become a random token and 10% are left exactly as they were — and all of them are still scored, which means the model can never fully trust an unmasked token either. Summed from its own config, BERT-base is 109,482,240 parameters: 23,837,184 of embeddings, twelve layers of 7,087,872 each, and a 590,592-parameter pooler.",
    ),
    steps = listOf(
        StepCard(1, "Select 15%", "Choose the positions to predict, at random, per sequence.", 0xFF3B82F6),
        StepCard(2, "Corrupt 80/10/10", "[MASK], random token, or leave it — all three still scored.", 0xFFF59E0B),
        StepCard(3, "Encode Bidirectionally", "Every position attends to every other one.", 0xFF6366F1),
        StepCard(4, "Predict the Originals", "Softmax over the vocabulary at the selected positions.", 0xFF10B981),
        StepCard(5, "Count What It Cost", "6.4× fewer targets per pass, 2× the context each.", 0xFF8B5CF6),
        StepCard(6, "Fine-Tune a Head", "One layer on top; the encoder does the work.", 0xFFEC4899),
    ),
    formulas = listOf(
        FormulaEntry("Objective", "maximise Σ log P(xᵢ | x∖M)", "Over the masked set M only."),
        FormulaEntry("Targets per pass", "0.15·n vs n", "10 against 64 on the lab's corpus — 6.4×."),
        FormulaEntry("Context per prediction", "n−1 vs (n−1)/2", "5.44 against 2.72 tokens, measured."),
        FormulaEntry("[MASK] exposure", "0.15 × 0.80 = 12%", "Of pre-training positions; 0% at fine-tuning."),
        FormulaEntry("BERT-base size", "109,482,240", "Summed from the config, not quoted."),
        FormulaEntry("Layer split", "attention 33% · FFN 67%", "2,362,368 against 4,722,432 per layer."),
    ),
    notationKey = listOf(
        NotationEntry("M", "the masked set — the positions selected for prediction"),
        NotationEntry("[MASK]", "the placeholder token, on 12% of pre-training positions and none afterwards"),
        NotationEntry("[CLS]", "the prepended token whose final state is used for sequence classification"),
        NotationEntry("NSP", "next-sentence prediction, BERT's second objective — which RoBERTa showed does nothing"),
        NotationEntry("pooler", "the 590,592-parameter layer over [CLS], unused by most fine-tuning recipes"),
        NotationEntry("WordPiece", "BERT's tokenizer — 30,522 pieces, and [UNK] when nothing matches"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "The 80/10/10 rule, written out",
            accentColor = 0xFFF59E0B,
            code = """
                import torch

                def mask_tokens(inputs, tokenizer, rate=0.15):
                    labels = inputs.clone()
                    selected = torch.rand(inputs.shape) < rate
                    selected &= ~torch.isin(inputs, torch.tensor(tokenizer.all_special_ids))
                    labels[~selected] = -100          # -100 = "not scored" in HF losses

                    draw = torch.rand(inputs.shape)
                    inputs[selected & (draw < 0.8)] = tokenizer.mask_token_id
                    random_slot = selected & (draw >= 0.8) & (draw < 0.9)
                    inputs[random_slot] = torch.randint(len(tokenizer), (random_slot.sum(),))
                    # The remaining 10% keep their original token -- and are still in `labels`,
                    # so the model is scored on positions that look untouched. That is what stops
                    # it from treating "not [MASK]" as "already correct".
                    return inputs, labels
            """.trimIndent(),
        ),
        CodeBlock(
            title = "Sum the parameter table before quoting a headline",
            accentColor = 0xFF3B82F6,
            code = """
                def bert_parameters(vocab=30522, d=768, layers=12, ffn=3072, positions=512, types=2):
                    embeddings = vocab * d + positions * d + types * d + 2 * d      # + embedding LN
                    attention = 4 * (d * d + d)                                     # Q, K, V, O
                    feed_forward = (d * ffn + ffn) + (ffn * d + d)
                    per_layer = attention + feed_forward + 2 * (2 * d)              # two layer norms
                    pooler = d * d + d
                    return embeddings + layers * per_layer + pooler

                print(bert_parameters())        # 109482240  -- the paper's "110M", in full
                # embeddings         23,837,184   (21.8% of the model)
                # 12 layers          85,054,464   (attention 33%, feed-forward 67% of each)
                # pooler                590,592
                #
                # The split is the design argument: two thirds of every layer is the position-wise
                # feed-forward network, and a fifth of the whole model is the vocabulary.
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.TokenStripPlayer,
    applications = listOf(
        ApplicationCard("browser", 0xFF3B82F6, "Classification & Tagging", "Sentiment, NER, intent — one head on a frozen or fine-tuned encoder."),
        ApplicationCard("search", 0xFF6366F1, "Retrieval & Reranking", "Cross-encoder scoring is still a BERT-shaped job."),
        ApplicationCard("map", 0xFF10B981, "Sentence Embeddings", "Sentence-BERT and its descendants, for similarity at scale."),
        ApplicationCard("help", 0xFFEC4899, "Not For Generation", "No next-token objective exists — reach for a decoder instead."),
    ),
    takeaways = listOf(
        "A transformer encoder trained to fill in blanks: 15% of positions selected, corrupted and predicted.",
        "Bidirectional by construction, which is exactly why it cannot generate.",
        "Counted on one corpus: 10 masked targets against 64 causal ones — 6.4× less signal per pass.",
        "And 2× the context per prediction, 5.44 tokens against 2.72, because it reads both directions.",
        "80/10/10 exists because [MASK] covers 12% of pre-training positions and 0% of fine-tuning ones.",
        "Summed from the config: 109,482,240 parameters — embeddings 21.8%, and two thirds of each layer is the feed-forward block.",
        "Its descendants are recipe changes, not architecture changes: RoBERTa retrains it, DistilBERT halves it.",
    ),
    crossLinks = listOf(
        CrossLink("transformers", "Transformers"),
        CrossLink("gpt", "GPT"),
        CrossLink("roberta", "RoBERTa"),
        CrossLink("distilbert", "DistilBERT"),
        CrossLink("bidirectional_rnn", "Bidirectional RNNs"),
    ),
)
