package com.algora.app.feature.nlp

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Section

// Colors/icons pulled verbatim from docs/design/Algora.dc.html's cats()['nlp'].
// Phase 9's Track D expands this past the mock's two buckets the same way Tracks B and C expanded ML
// and DL: docs/topics.ai.md lists eleven NLP sub-sections, and "Preprocessing" plus "Modeling"
// cannot hold 77 entries browsably. Categories arrive per batch as their topics land.
object NlpCategories {
    val preprocessing = Category("nlp_preprocessing", "Text Preprocessing", Section.NLP, 0xFF14B8A6, "globe")

    // D1. "chart" is the counting these methods all reduce to, and it was unused in this section.
    val statistical = Category("nlp_statistical", "Statistical NLP", Section.NLP, 0xFF8B5CF6, "chart")

    val modeling = Category("nlp_modeling", "Modeling", Section.NLP, 0xFF3B82F6, "network")

    val all = listOf(preprocessing, statistical, modeling)
}
