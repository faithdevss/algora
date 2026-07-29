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
private val activations = DeepLearningCategories.activations
private val convolutional = DeepLearningCategories.cnn
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

// The doc's Activation Functions block in full, led by the existing activation_functions umbrella
// moved over from dl_fundamentals. Ordered as the history actually went: the two saturating ones
// that came first, the rectifier family that replaced them and the repairs to its one failure, then
// the two smooth self-gated functions that won the transformer era — and softmax last, because it
// is an output layer rather than a hidden non-linearity.
private val activationTopics = listOf(
    topic("activation_functions", "Activation Functions", activations, "Non-linearities (ReLU, sigmoid, tanh) that give networks their power.", isPremium = true),
    topic("sigmoid", "Sigmoid", activations, "The original, its 0.25 derivative ceiling, and where it still belongs.", difficulty = Difficulty.BEGINNER),
    topic("tanh", "Tanh (Hyperbolic Tangent)", activations, "Zero-centred, four times the gradient — and still saturating.", isPremium = true, difficulty = Difficulty.BEGINNER),
    topic("relu", "ReLU (Rectified Linear Unit)", activations, "Derivative exactly 1 where active, and the units that die when the step is too big.", difficulty = Difficulty.BEGINNER),
    topic("leaky_relu", "Leaky ReLU", activations, "One character of difference, and the dying problem is gone.", isPremium = true, difficulty = Difficulty.BEGINNER),
    topic("prelu", "Parametric ReLU (PReLU)", activations, "Stop guessing the negative slope and learn it.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("elu", "ELU (Exponential Linear Unit)", activations, "A smooth negative branch that saturates, for a mean nearer zero.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("selu", "SELU (Scaled ELU)", activations, "Two constants that make a deep stack normalise itself — under one exact condition.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("swish", "Swish (by Google)", activations, "Self-gated and non-monotone, with a derivative that can exceed 1.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("gelu", "GELU (Gaussian Error Linear Unit)", activations, "Dropout and ReLU merged into one deterministic function — the transformer default.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("softmax", "Softmax (Output Layer)", activations, "The one that maps a vector to a vector, with a Jacobian instead of a derivative.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
)

// The doc's CNN block in full, led by the existing `cnn` umbrella moved over from dl_architectures.
// Ordered as the mechanics first and then the architectures in the order they were published, since
// every one of them is an answer to a limit of the one before it: AlexNet is LeNet at scale, VGG is
// AlexNet made uniform and deep, Inception is VGG made affordable, ResNet is depth made trainable,
// DenseNet is ResNet's shortcut with concatenation, MobileNet and EfficientNet are the same models
// made small, and ViT is the argument that none of the convolution was load-bearing.
private val cnnTopics = listOf(
    topic("cnn", "CNNs", convolutional, "Convolutional networks that exploit spatial structure in images.", isPremium = true),
    topic("conv_layers", "Convolution Layers", convolutional, "Nine weights, reused everywhere — and what that buys over a dense layer.", difficulty = Difficulty.BEGINNER),
    topic("pooling_layers", "Pooling Layers (Max/Average)", convolutional, "Downsampling with no parameters, and how much shift-tolerance it really buys.", difficulty = Difficulty.BEGINNER),
    topic("padding_strides", "Padding & Strides", convolutional, "One formula for output size, and the border pixels nobody reads.", isPremium = true, difficulty = Difficulty.BEGINNER),
    topic("lenet5", "LeNet-5 (The Original)", convolutional, "61,706 parameters that read cheques for a decade.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("alexnet", "AlexNet (The Breakthrough)", convolutional, "The 2012 result that restarted the field — and where its 62M parameters sit.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("vgg", "VGG-16 / VGG-19", convolutional, "One kernel size everywhere, 138M parameters, and why two 3×3s beat a 5×5.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("inception", "Inception (GoogLeNet)", convolutional, "Every kernel size at once, made affordable by the 1×1 bottleneck.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("resnet", "ResNet (Residual Connections)", convolutional, "One addition, and depth stopped hurting.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("densenet", "DenseNet", convolutional, "Concatenate instead of add: every layer sees every earlier one.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("mobilenet", "MobileNet (Lightweight)", convolutional, "Split filtering from mixing and the layer gets 8× cheaper.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("efficientnet", "EfficientNet", convolutional, "Depth, width and resolution scaled together by one exponent.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("vit", "Vision Transformers (ViT)", convolutional, "An image as 196 tokens, and no convolution anywhere.", isPremium = true, difficulty = Difficulty.ADVANCED),
)

private val fundamentalsTopics = listOf(
    topic("gradient_descent_variants", "Gradient Descent Variants", fundamentals, "SGD, Momentum, RMSProp, Adam and friends.", isPremium = true),
    topic("batch_normalization", "Batch Normalization", fundamentals, "Re-centre and re-scale activations so deep stacks stay trainable.", isPremium = true),
    topic("dropout", "Dropout", fundamentals, "Randomly silence neurons during training so none becomes indispensable.", isPremium = true),
    topic("transfer_learning", "Transfer Learning", fundamentals, "Reuse a pretrained network's features and retrain only the head.", isPremium = true),
)

private val architecturesTopics = listOf(
    topic("rnn", "RNNs", architectures, "Recurrent networks that carry state across a sequence.", isPremium = true),
    topic("lstm_gru", "LSTMs / GRUs", architectures, "Gated recurrent cells that remember long-range dependencies.", isPremium = true),
    topic("autoencoders", "Autoencoders", architectures, "Encode-then-reconstruct networks for compression and denoising.", isPremium = true),
    topic("gans", "GANs", architectures, "A generator and discriminator locked in an adversarial game.", isPremium = true),
    topic("transformers", "Transformers", architectures, "Self-attention architecture behind modern LLMs.", isPremium = true),
    topic("diffusion_models", "Diffusion Models", architectures, "Learn to reverse a noising process and sample images from noise.", isPremium = true),
)

object DeepLearningTopics {
    val topics: List<Topic> = basicsTopics + activationTopics + cnnTopics + fundamentalsTopics + architecturesTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
