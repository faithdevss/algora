package com.algora.app.feature.deeplearning

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Section

// Colors/icons pulled verbatim from docs/design/Algora.dc.html's cats()['dl'].
// Phase 9's Track C expands this beyond the mock's two buckets, the same way Track B expanded ML:
// docs/topics.ai.md lists eleven DL sub-sections, and "Fundamentals" plus "Architectures" cannot
// hold 96 entries browsably. Categories arrive per batch as their topics land, and the two generic
// buckets are hollowed out rather than deleted up front so none is ever empty mid-phase.
object DeepLearningCategories {
    // C1. "robot" rather than "network": the block opens on the biological neuron and the
    // perceptron, and `perceptron` already carries that icon.
    val basics = Category("dl_basics", "Neural Network Basics", Section.DL, 0xFF06B6D4, "robot")
    // C2. "trend" is the curve every topic in the block is about, and it was unused in this section.
    val activations = Category("dl_activations", "Activation Functions", Section.DL, 0xFFF59E0B, "trend")
    // C3. "stack" is the layer stack every topic in the block is read as, and it was unused in this
    // section — `chip` already belongs to Architectures and would render the same glyph twice.
    val cnn = Category("dl_cnn", "Convolutional Networks (CNN)", Section.DL, 0xFF10B981, "stack")
    // C4. "target" is what a detector draws and what its metric is about; unused elsewhere in DL.
    val detection = Category("dl_detection", "Object Detection & Vision Tasks", Section.DL, 0xFF3B82F6, "target")
    val fundamentals = Category("dl_fundamentals", "Fundamentals", Section.DL, 0xFFEC4899, "network")
    val architectures = Category("dl_architectures", "Architectures", Section.DL, 0xFF8B5CF6, "chip")

    val all = listOf(basics, activations, cnn, detection, fundamentals, architectures)
}
