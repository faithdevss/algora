package com.algora.app.feature.topics

import com.algora.app.feature.deeplearning.DeepLearningTopics
import com.algora.app.feature.machinelearning.MachineLearningTopics
import com.algora.app.feature.nlp.NlpTopics
import com.algora.app.feature.reinforcementlearning.ReinforcementLearningTopics
import com.algora.app.feature.topics.content.TopicContentProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 12 coverage, and the inverse of [DsaFigureCoverageTest]: AI coverage is partial *by design*.
 *
 * The DSA test can ask "does every topic have one" because that phase finished. This phase is a
 * 30-topic pilot against 323 AI topics, so the same question would be false for the whole phase and
 * useless for the part of it that is done. What is worth guarding instead is the pilot list itself —
 * that each id in it really is drawn, and that each id is a real topic rather than a rename that
 * quietly stopped resolving. Shape conformance lives in [FigureShapeTest], which already sweeps every
 * figure in the app regardless of section.
 */
class AiFigureCoverageTest {

    private val aiTopicIds = (
        MachineLearningTopics.topics + DeepLearningTopics.topics +
            NlpTopics.topics + ReinforcementLearningTopics.topics
        ).map { it.id }.toSet()

    /** The pilot, batch by batch. Grows as A2–A5 land; the count assertion below moves with it. */
    private val pilotFigures = listOf(
        // A1 — optimisers, on the new Plot shape.
        "adam", "adamw", "momentum", "rmsprop", "adagrad", "gradient_descent_variants",
        // A2 — activations and losses, still on Plot.
        "relu", "leaky_relu", "sigmoid", "softmax", "activation_functions",
        "cross_entropy_loss", "mse", "hinge_loss",
        // A3 — architectures, on the new LayerStack shape.
        "mlp", "cnn", "transformers", "backpropagation", "batch_normalization", "dropout", "gans", "vae",
        // A4 — attention, on the new Heatmap shape.
        "attention", "multi_head_attention", "self_cross_attention", "positional_encodings",
    )

    @Test
    fun `every pilot topic has a figure`() {
        val missing = pilotFigures.filter { TopicContentProvider.get(it)?.figure == null }
        assertTrue("Pilot topics with no figure: $missing", missing.isEmpty())
    }

    @Test
    fun `every pilot id is a real AI topic`() {
        val unknown = pilotFigures.filterNot { it in aiTopicIds }
        assertTrue("Pilot ids that match no AI topic: $unknown", unknown.isEmpty())
    }

    @Test
    fun `the pilot is the size the phase was scoped against`() {
        // Keeps the batch table in docs/plan/phase-12-ai-figures.md verifiable rather than folklore.
        assertEquals(26, pilotFigures.size)
    }
}
