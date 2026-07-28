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
    val fundamentals = Category("dl_fundamentals", "Fundamentals", Section.DL, 0xFFEC4899, "network")
    val architectures = Category("dl_architectures", "Architectures", Section.DL, 0xFF8B5CF6, "chip")

    val all = listOf(basics, fundamentals, architectures)
}
