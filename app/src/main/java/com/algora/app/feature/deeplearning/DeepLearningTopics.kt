package com.algora.app.feature.deeplearning

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Difficulty
import com.algora.app.core.data.model.Topic

private fun topic(
    id: String,
    name: String,
    category: Category,
    tagline: String,
    isPremium: Boolean = false,
    iconName: String = category.iconName,
    accentColor: Long = category.accentColor,
    difficulty: Difficulty? = null,
) = Topic(
    id = id,
    name = name,
    categoryId = category.id,
    tagline = tagline,
    description = tagline,
    iconName = iconName,
    accentColor = accentColor,
    isPremium = isPremium,
    difficulty = difficulty,
)

private val basics = DeepLearningCategories.basics
private val fundamentals = DeepLearningCategories.fundamentals
private val architectures = DeepLearningCategories.architectures

// The doc's Neural Network Basics block, in full. Ordered as a reading path rather than by when
// each topic was written: what a neuron is, what one artificial unit can do, what a layer of them
// adds, how the whole stack is trained, and the two ways that training fails with depth.
private val basicsTopics = listOf(
    topic("biological_neuron", "The Biological Neuron", basics, "What the metaphor was taken from — and how much of it was left behind.", difficulty = Difficulty.BEGINNER),
    // Cross-listed with ML (mock lists The Perceptron under both). Same id -> same detail/content.
    topic(
        "perceptron", "The Perceptron", basics,
        "The first artificial neuron",
        iconName = "robot", difficulty = Difficulty.BEGINNER,
    ),
    topic("mlp", "Multi-Layer Perceptron (MLP)", basics, "One hidden layer, and the problem a single unit provably cannot solve.", difficulty = Difficulty.BEGINNER),
    topic("neural_network_basics", "Feedforward Networks", basics, "Layers of neurons that learn features from data.", isPremium = true),
    topic("backpropagation", "Backpropagation Algorithm", basics, "The chain rule applied to train every weight in a network.", isPremium = true),
    topic("vanishing_gradient", "The Vanishing Gradient Problem", basics, "Why the layers nearest the input learn slowest — measured, not asserted.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("exploding_gradient", "The Exploding Gradient Problem", basics, "The same multiplication running the other way, and why clipping works.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
)

private val fundamentalsTopics = listOf(
    topic("activation_functions", "Activation Functions", fundamentals, "Non-linearities (ReLU, sigmoid, tanh) that give networks their power.", isPremium = true),
    topic("gradient_descent_variants", "Gradient Descent Variants", fundamentals, "SGD, Momentum, RMSProp, Adam and friends.", isPremium = true),
    topic("batch_normalization", "Batch Normalization", fundamentals, "Re-centre and re-scale activations so deep stacks stay trainable.", isPremium = true),
    topic("dropout", "Dropout", fundamentals, "Randomly silence neurons during training so none becomes indispensable.", isPremium = true),
    topic("transfer_learning", "Transfer Learning", fundamentals, "Reuse a pretrained network's features and retrain only the head.", isPremium = true),
)

private val architecturesTopics = listOf(
    topic("cnn", "CNNs", architectures, "Convolutional networks that exploit spatial structure in images.", isPremium = true),
    topic("rnn", "RNNs", architectures, "Recurrent networks that carry state across a sequence.", isPremium = true),
    topic("lstm_gru", "LSTMs / GRUs", architectures, "Gated recurrent cells that remember long-range dependencies.", isPremium = true),
    topic("autoencoders", "Autoencoders", architectures, "Encode-then-reconstruct networks for compression and denoising.", isPremium = true),
    topic("gans", "GANs", architectures, "A generator and discriminator locked in an adversarial game.", isPremium = true),
    topic("transformers", "Transformers", architectures, "Self-attention architecture behind modern LLMs.", isPremium = true),
    topic("diffusion_models", "Diffusion Models", architectures, "Learn to reverse a noising process and sample images from noise.", isPremium = true),
)

object DeepLearningTopics {
    val topics: List<Topic> = basicsTopics + fundamentalsTopics + architecturesTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
