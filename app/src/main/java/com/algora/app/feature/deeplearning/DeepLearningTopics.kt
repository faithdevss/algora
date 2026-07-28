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

private val fundamentals = DeepLearningCategories.fundamentals
private val architectures = DeepLearningCategories.architectures

private val fundamentalsTopics = listOf(
    // Cross-listed with ML (mock lists The Perceptron under both). Same id -> same detail/content.
    topic(
        "perceptron", "The Perceptron", fundamentals,
        "The first artificial neuron",
        iconName = "robot", difficulty = Difficulty.BEGINNER,
    ),
    topic("neural_network_basics", "Neural Network Basics", fundamentals, "Layers of neurons that learn features from data.", isPremium = true),
    topic("backpropagation", "Backpropagation", fundamentals, "The chain rule applied to train every weight in a network.", isPremium = true),
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
    val topics: List<Topic> = fundamentalsTopics + architecturesTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
