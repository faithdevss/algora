package com.algora.app.feature.topics.content

import com.algora.app.core.data.model.ApplicationCard
import com.algora.app.core.data.model.CodeBlock
import com.algora.app.core.data.model.CrossLink
import com.algora.app.core.data.model.FormulaEntry
import com.algora.app.core.data.model.NotationEntry
import com.algora.app.core.data.model.SimulationType
import com.algora.app.core.data.model.StepCard
import com.algora.app.core.data.model.TopicContent

internal val cnnContent = TopicContent(
    topicId = "cnn",
    whatIsIt = listOf(
        "Convolutional neural networks (CNNs) process grid-like data — especially images — by sliding small learnable filters across the input to detect local patterns.",
        "Two ideas make them efficient: weight sharing (the same filter everywhere) and translation invariance (a feature is detected wherever it appears).",
    ),
    steps = listOf(
        StepCard(1, "Convolution", "Slide filters over the input; each produces a feature map highlighting where its pattern occurs.", 0xFF818CF8),
        StepCard(2, "Non-Linearity", "Apply ReLU to each feature map.", 0xFF60A5FA),
        StepCard(3, "Pooling", "Downsample (e.g. max-pool) to shrink spatial size and add robustness to small shifts.", 0xFF10B981),
        StepCard(4, "Deepen & Classify", "Stack conv/pool blocks to build hierarchical features, then a dense head for the output.", 0xFFF59E0B),
    ),
    formulas = listOf(
        FormulaEntry("Convolution", "(I ∗ K)(i,j) = ΣΣ I(i+m, j+n)·K(m,n)", "Filter K applied across input I."),
        FormulaEntry("Output size", "(W − F + 2P)/S + 1", "From width W, filter F, padding P, stride S."),
        FormulaEntry("Weight sharing", "one kernel, whole image", "Far fewer parameters than a dense layer."),
    ),
    notationKey = listOf(
        NotationEntry("kernel / filter", "small learnable weight patch"),
        NotationEntry("feature map", "output of one filter over the input"),
        NotationEntry("stride / padding", "step size / border handling of the slide"),
    ),
    codeBlocks = listOf(
        CodeBlock(
            title = "A conv block (PyTorch)",
            accentColor = 0xFF6366F1,
            code = """
                import torch.nn as nn

                block = nn.Sequential(
                    nn.Conv2d(3, 32, kernel_size=3, padding=1),
                    nn.ReLU(),
                    nn.MaxPool2d(2),
                )
            """.trimIndent(),
        ),
    ),
    simulation = SimulationType.NeuralNetPlayer,
    applications = listOf(
        ApplicationCard("image", 0xFF818CF8, "Computer Vision", "Image classification, detection, and segmentation are CNN territory."),
        ApplicationCard("flask", 0xFF60A5FA, "Medical Imaging", "Detecting tumors and anomalies in scans from local visual patterns."),
        ApplicationCard("music", 0xFF10B981, "Audio & Signals", "1D convolutions capture local structure in audio and time series too."),
    ),
    takeaways = listOf(
        "CNNs slide shared filters to detect local patterns, giving translation invariance.",
        "Weight sharing makes them vastly more parameter-efficient than dense nets on images.",
        "Stacked conv/pool layers build a hierarchy from edges to objects.",
        "They dominated vision until Transformers began rivaling them at scale.",
    ),
    crossLinks = listOf(
        CrossLink("neural_network_basics", "Neural Network Basics"),
        CrossLink("transformers", "Transformers"),
    ),
)
