package com.algora.app.feature.datastructures

import com.algora.app.core.data.model.Category
import com.algora.app.core.data.model.Section

// Colors/icons pulled verbatim from docs/design/Algora.dc.html's cats() for Data Structures.
object DataStructuresCategories {
    val core = Category("ds_core", "Core", Section.DATA_STRUCTURES, 0xFF10B981, "stack")
    val nonLinear = Category("ds_nonlinear", "Non-Linear", Section.DATA_STRUCTURES, 0xFF3B82F6, "share")
    val advanced = Category("ds_advanced", "Advanced", Section.DATA_STRUCTURES, 0xFF8B5CF6, "chip")
    val specialized = Category("ds_specialized", "Specialized / Real-World", Section.DATA_STRUCTURES, 0xFFF59E0B, "globe")
    val adt = Category("ds_adt", "Abstract Data Types", Section.DATA_STRUCTURES, 0xFFEC4899, "browser")

    val all = listOf(core, nonLinear, advanced, specialized, adt)
}
