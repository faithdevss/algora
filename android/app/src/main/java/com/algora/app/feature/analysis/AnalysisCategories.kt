package com.algora.app.feature.analysis

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Section

// Colors/icons pulled verbatim from docs/design/Algora.dc.html's cats() for Analysis.
// Topic content itself is Phase 4's job (Algorithm Analysis Module) — this phase only builds
// the browser chrome + row metadata.
object AnalysisCategories {
    val fundamentals = Category("analysis_fundamentals", "Fundamentals", Section.ANALYSIS, 0xFF8B5CF6, "chip")
    val growthVisualization = Category("analysis_growth", "Growth Visualization", Section.ANALYSIS, 0xFF3B82F6, "trend")
    val caseAnalysis = Category("analysis_case", "Case Analysis", Section.ANALYSIS, 0xFF10B981, "stack")
    val empiricalAnalysis = Category("analysis_empirical", "Empirical Analysis", Section.ANALYSIS, 0xFF06B6D4, "search")
    val advanced = Category("analysis_advanced", "Advanced", Section.ANALYSIS, 0xFFF59E0B, "chip")
    val explorationGames = Category("analysis_exploration", "Exploration & Games", Section.ANALYSIS, 0xFFEC4899, "game")

    val all = listOf(fundamentals, growthVisualization, caseAnalysis, empiricalAnalysis, advanced, explorationGames)
}
