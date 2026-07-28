package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val transferLearningContent = TopicContent(
    topicId = "transfer_learning",
    whatIsIt = listOf(
        "Transfer learning reuses a network already trained on a large dataset and adapts it to a new task, instead of training from random initialization.",
        "It works because early layers learn generic structure — edges, textures, syntax — that is not specific to the original labels. Replacing only the final head and training that on a few thousand examples routinely beats a from-scratch model trained on the same small dataset.",
    ),
    steps = listOf(
        StepCard(1, "Pick a Pretrained Backbone", "Choose a model whose pretraining domain resembles yours: ImageNet for vision, a language model for text.", 0xFFEC4899),
        StepCard(2, "Replace the Head", "Swap the final classifier for one shaped to your label set, randomly initialized.", 0xFF3B82F6),
        StepCard(3, "Freeze and Train the Head", "With the backbone frozen only the new layer learns — fast, and safe on tiny datasets.", 0xFFF59E0B),
        StepCard(4, "Unfreeze and Fine-Tune", "Release the upper layers and continue at a much smaller learning rate.", 0xFF10B981),
        StepCard(5, "Use Discriminative Rates", "Deeper layers hold the most general features, so give them the smallest learning rates.", 0xFF8B5CF6),
    ),
    formulas = listOf(
        FormulaEntry("Frozen features", "θ_backbone fixed, only θ_head updated", "Feature extraction — the cheapest mode."),
        FormulaEntry("Fine-tuning rate", "η_finetune ≈ η_scratch / 10 … /100", "Large steps destroy the pretrained weights."),
        FormulaEntry("Layer-wise decay", "η_layer = η_top · ξ^(depth from top),  ξ ≈ 0.8", "Earlier layers move less."),
        FormulaEntry("LoRA update", "W' = W + BA,  rank(BA) = r ≪ d", "Adapts a frozen weight matrix with a tiny trainable pair."),
    ),
    notationKey = listOf(
        NotationEntry("backbone", "pretrained feature extractor being reused"),
        NotationEntry("head", "task-specific final layer, trained from scratch"),
        NotationEntry("freeze", "mark parameters non-trainable so gradients skip them"),
        NotationEntry("catastrophic forgetting", "losing pretrained knowledge by fine-tuning too aggressively"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "Two-stage transfer: freeze, then fine-tune",
            accentColor = 0xFF6366F1,
            code = """
                class TransferModel(private val backbone: Network, private val head: Network) {

                    // Stage 1 — backbone frozen, only the new head learns.
                    fun trainHead(data: Dataset, epochs: Int = 5, lr: Double = 1e-3) {
                        backbone.trainable = false
                        for (batch in data.epochs(epochs)) {
                            val features = backbone.forward(batch.x, training = false)
                            val loss = head.trainStep(features, batch.y, lr)
                            data.log(loss)
                        }
                    }

                    // Stage 2 — unfreeze the top layers, tiny learning rate.
                    fun fineTune(data: Dataset, epochs: Int = 3, lr: Double = 1e-5, unfreezeTop: Int = 2) {
                        backbone.trainable = true
                        backbone.layers.dropLast(unfreezeTop).forEach { it.trainable = false }
                        for (batch in data.epochs(epochs)) {
                            val features = backbone.forward(batch.x, training = true)
                            val loss = head.trainStep(features, batch.y, lr)
                            backbone.backward(head.inputGradient, lr)   // same small rate
                            data.log(loss)
                        }
                    }
                }
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("image", 0xFFEC4899, "Domain-Specific Vision", "Medical and industrial-defect classifiers are built on ImageNet backbones with a few thousand labels."),
        ApplicationCard("robot", 0xFF3B82F6, "LLM Adaptation", "Instruction tuning and LoRA adapt a general model to a narrow domain for a fraction of pretraining cost."),
        ApplicationCard("globe", 0xFF10B981, "On-Device Models", "A shared frozen backbone with small per-task heads keeps several features inside one app's size budget."),
    ),
    takeaways = listOf(
        "Early layers are generic, late layers are task-specific — that gradient is what makes transfer work.",
        "Freeze first, fine-tune second; the reverse order wrecks pretrained weights with a random head's gradients.",
        "Fine-tune at a much lower learning rate than you would use from scratch.",
        "The further the target domain is from the pretraining domain, the more layers you need to unfreeze.",
    ),
    crossLinks = listOf(
        CrossLink("cnn", "CNNs"),
        CrossLink("transformers", "Transformers"),
        CrossLink("neural_network_basics", "Neural Network Basics"),
    ),
)
