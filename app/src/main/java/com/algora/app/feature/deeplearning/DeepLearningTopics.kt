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
private val detection = DeepLearningCategories.detection
private val recurrent = DeepLearningCategories.rnn
private val transformers = DeepLearningCategories.transformers
private val generative = DeepLearningCategories.generative
private val regularization = DeepLearningCategories.regularization
private val specialized = DeepLearningCategories.specialized
private val fundamentals = DeepLearningCategories.fundamentals

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

// The doc's Object Detection & Vision Tasks block in full. Ordered as the history went, because
// each architecture is an answer to the previous one's bottleneck: the two-stage line from R-CNN to
// Faster R-CNN, then the one-stage detectors that removed the proposal step, then the two
// segmentation architectures and the task distinction they turn on.
private val detectionTopics = listOf(
    topic("rcnn", "R-CNN", detection, "2,000 region proposals, one CNN pass each, and 47 seconds per image.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("fast_rcnn", "Fast R-CNN", detection, "Share the feature map, pool the regions out of it, train it as one model.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("faster_rcnn", "Faster R-CNN", detection, "Anchors and a proposal network: the last hand-written stage becomes learned.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("yolo", "YOLO (V1–V8)", detection, "One grid, one forward pass, 98 boxes — and what the grid costs.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("ssd", "SSD (Single Shot Detector)", detection, "8,732 default boxes across six scales, counted level by level.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("retinanet", "RetinaNet (Focal Loss)", detection, "One factor in the loss, and one-stage detection caught up.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("unet", "U-Net (Medical Segmentation)", detection, "Contract, expand, and concatenate what pooling threw away.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("mask_rcnn", "Mask R-CNN (Instance Seg.)", detection, "One more head, and the quantisation bug the masks exposed.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("segmentation_types", "Semantic vs Instance Segmentation", detection, "Two touching sheep: one region, or two objects.", difficulty = Difficulty.BEGINNER),
)

// The doc's Recurrent Neural Networks block in full, led by the `rnn` and `lstm_gru` topics moved
// over from dl_architectures. Ordered as the problem is met and then answered: what a recurrent
// layer is, what training one costs, the gated cells that were the answer to that cost, the second
// reading direction, and then the two-network arrangement that turns a sequence model into a
// sequence *transducer* — which is where the transformer picks up.
private val rnnTopics = listOf(
    topic("rnn", "RNNs", recurrent, "Recurrent networks that carry state across a sequence.", isPremium = true),
    topic("bptt", "BPTT (Backprop Through Time)", recurrent, "Unroll, walk back, and find out what the window really bounds.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("lstm_gru", "LSTMs / GRUs", recurrent, "Gated recurrent cells that remember long-range dependencies.", isPremium = true),
    topic("bidirectional_rnn", "Bidirectional RNNs", recurrent, "A second pass right to left, and the ceiling it lifts — counted before it is trained.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("encoder_decoder", "Encoder-Decoder Architecture", recurrent, "Two networks, one vector between them, and what that vector drops.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("seq2seq", "Seq2Seq Models", recurrent, "Greedy, beam and the two kinds of error only one of them fixes.", isPremium = true, difficulty = Difficulty.ADVANCED),
)

// The doc's Transformers & LLMs block in full. `attention` is cross-listed from NLP and `transformers`
// moves here from Architectures, both as landing topics. Ordered as the mechanism is assembled and
// then as the families were published: the operation, the two ways it is wired, the head split, the
// block itself, then encoder-only, decoder-only, encoder-decoder, and the two models that are
// arguments about BERT's recipe rather than its architecture. `hf_tokenizers` sits last because it
// is the one thing on this list you choose *before* any of the rest exists.
private val transformerTopics = listOf(
    topic("attention", "Attention", transformers, "Let the model weigh every token against every other.", isPremium = true),
    topic("self_cross_attention", "Self- vs Cross-Attention", transformers, "One operation, two wirings — and the experiment that ends the RNN bottleneck story.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("multi_head_attention", "Multi-Head Attention", transformers, "Free in parameters, and what it actually buys is simultaneous reads.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("transformers", "Transformers", transformers, "Self-attention architecture behind modern LLMs.", isPremium = true),
    topic("bert", "BERT (Bidirectional Encoder)", transformers, "Fill in the blanks — 2× the context per prediction, 6.4× fewer of them.", difficulty = Difficulty.INTERMEDIATE),
    topic("gpt", "GPT (Decoder-Only)", transformers, "One triangular mask, and everything that follows from it.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("t5", "T5 (Text-to-Text)", transformers, "Every task as text, and span corruption priced at 512 tokens.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("roberta", "RoBERTa", transformers, "Same architecture, better recipe — and 0.85ᵏ is the whole masking argument.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("distilbert", "DistilBERT", transformers, "Half the layers, 40% smaller — and why it is not 50%.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("hf_tokenizers", "Hugging Face Tokenizers", transformers, "BPE, WordPiece and Unigram trained side by side on one corpus.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
)

private val fundamentalsTopics = listOf(
    topic("gradient_descent_variants", "Gradient Descent Variants", fundamentals, "SGD, Momentum, RMSProp, Adam and friends.", isPremium = true),
    // D6 cross-lists this into `nlp_finetuning`, where it is that category's free entry because the
    // NLP doc carries no lock on it. One id cannot be gated two ways, so this row is free too.
    topic("transfer_learning", "Transfer Learning", fundamentals, "Reuse a pretrained network's features and retrain only the head.", difficulty = Difficulty.INTERMEDIATE),
)

// The doc's Regularization Techniques block in full, led by `regularization` and `dropout` moved
// over from dl_fundamentals and `batch_normalization` moved over from the same place — dropout's
// batch-mate since Phase 9's 2026-07-28 expansion, batch norm since the app's earliest content.
// Ordered as a pipeline of restraints: penalize the weights directly, transform the data a model
// sees, normalize what flows between layers (batch axis, then feature axis, then the axis in
// between), then stop training before the gap between train and test opens up.
private val regularizationTopics = listOf(
    topic("regularization", "L1 / L2 Regularization", regularization, "Penalize large weights directly, in the loss.", isPremium = true),
    topic("data_augmentation", "Data Augmentation", regularization, "One canonical pose taught centroid recognises little else — augmenting the poses fixes it, measured.", difficulty = Difficulty.BEGINNER),
    topic("dropout", "Dropout", regularization, "Randomly silence neurons during training so none becomes indispensable.", isPremium = true),
    topic("batch_normalization", "Batch Normalization", regularization, "Re-centre and re-scale activations so deep stacks stay trainable.", isPremium = true),
    topic("layer_normalization", "Layer Normalization", regularization, "Normalize across features instead of across the batch — and batch size 1 stops being degenerate.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("group_normalization", "Group Normalization", regularization, "LayerNorm and InstanceNorm are its two endpoints — checked as an identity, not claimed.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("early_stopping", "Early Stopping", regularization, "Stop where validation loss says to, and true generalization error is measurably better for it.", difficulty = Difficulty.BEGINNER),
)

// The doc's Specialized & Graph Networks block in full — six architectures that are not a stack of
// dense or convolutional layers. Ordered as a reading path: a metric-learning objective bolted onto
// an ordinary network first, then the two graph architectures side by side (fixed structural
// weights, then learned feature-dependent ones), then the two ideas that replace a layer's usual
// contract outright — capsules replace a scalar activation with a vector, ODEs replace a finite
// stack with a continuum — and KAN last, which replaces the learnable weight itself.
private val specializedTopics = listOf(
    topic("siamese_networks", "Siamese Networks", specialized, "One embedding, trained on pairs, that generalizes to a class it never saw.", difficulty = Difficulty.INTERMEDIATE),
    topic("gcn", "Graph Convolutional Networks (GCN)", specialized, "Repeated neighbor averaging — fixed weights, oversmoothing measured to an exact limit.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("gat", "Graph Attention Networks (GAT)", specialized, "The same neighborhood, weighted by what the features say instead of by degree alone.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("capsule_networks", "Capsule Networks", specialized, "Routing-by-agreement: a vote that disagrees gets voted out, round by round.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("neural_odes", "Neural ODEs", specialized, "A ResNet's depth taken to the limit — a solver stands in for the layer stack.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("kan", "Kolmogorov-Arnold Networks (KAN)", specialized, "The learnable part moves from the weight to the activation function itself.", isPremium = true, difficulty = Difficulty.ADVANCED),
)

// The doc's Generative Deep Learning block in full, led by the three topics moved over from
// dl_architectures — which retires that category, the last of the mock's two generic DL buckets to
// go. Ordered so each topic answers the previous one's limit: the deterministic bottleneck, the one
// change that makes it generative, the adversarial alternative, that alternative made convolutional,
// then unpaired translation and controllable synthesis. Diffusion arrives as the third paradigm and
// Stable Diffusion is what made it affordable. Style transfer sits late on purpose — it predates all
// of them and is still the clearest statement of what "style" is being taken to mean, which is what
// DeepFakes then needs in order to be about something other than the software.
private val generativeTopics = listOf(
    topic("autoencoders", "Autoencoders", generative, "Encode-then-reconstruct networks for compression and denoising.", isPremium = true),
    topic("vae", "Variational Autoencoders (VAE)", generative, "Two changes to a bottleneck, and the units the KL term switches off.", difficulty = Difficulty.INTERMEDIATE),
    topic("gans", "GANs", generative, "A generator and discriminator locked in an adversarial game.", isPremium = true),
    topic("dcgan", "DCGAN (Deep Convolutional GAN)", generative, "The rule that is arithmetic rather than folklore: kernel divisible by stride.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
    topic("cyclegan", "CycleGAN (Image-to-Image)", generative, "720 mappings satisfy the loss and one is right — cycle consistency removes none of them.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("stylegan", "StyleGAN", generative, "AdaIN exactly replaces the statistics, and the mapping network is measured rather than argued.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("diffusion_models", "Diffusion Models", generative, "Learn to reverse a noising process and sample images from noise.", isPremium = true),
    topic("stable_diffusion", "Stable Diffusion Architecture", generative, "48× fewer elements, 4096× fewer attention pairs — the whole argument is a division.", isPremium = true, difficulty = Difficulty.ADVANCED),
    topic("neural_style_transfer", "Neural Style Transfer", generative, "The Gram matrix is exactly blind to position, and that is provable rather than approximate.", difficulty = Difficulty.BEGINNER),
    topic("deepfakes", "DeepFakes (Concept)", generative, "One shared encoder, two decoders — necessary, and measurably not sufficient.", isPremium = true, difficulty = Difficulty.INTERMEDIATE),
)

object DeepLearningTopics {
    val topics: List<Topic> = basicsTopics + activationTopics + cnnTopics + detectionTopics +
        rnnTopics + transformerTopics + generativeTopics + regularizationTopics + specializedTopics + fundamentalsTopics

    fun find(topicId: String): Topic? = topics.find { it.id == topicId }
}
