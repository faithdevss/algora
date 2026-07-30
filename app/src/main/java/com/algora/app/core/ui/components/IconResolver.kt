package com.algora.app.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataArray
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector

// Central icon-name -> ImageVector mapping, shared by section headers and topic hero tiles.
// Names come from docs/design/Algora.dc.html's icon() calls (stack/share/chip/...) plus
// the Material-specific names Phase 1 introduced for Array (DataArray/Image/TableChart/...).
// The design mock's SVGs have no exact Material twins, so each maps to the nearest Material glyph;
// the full mock icon set is covered here so authored content never silently falls back to Circle.
fun resolveIcon(name: String): ImageVector = when (name) {
    "DataArray" -> Icons.Filled.DataArray
    "Image" -> Icons.Filled.Image
    "TableChart" -> Icons.Filled.TableChart
    "Memory", "chip" -> Icons.Filled.Memory
    "Layers", "stack" -> Icons.Filled.Layers
    "share", "network" -> Icons.Filled.Hub
    "globe" -> Icons.Filled.Public
    "browser" -> Icons.Filled.ViewModule
    "search" -> Icons.Filled.Search
    "trend" -> Icons.AutoMirrored.Filled.TrendingUp
    "map" -> Icons.Filled.Map
    "help" -> Icons.AutoMirrored.Filled.HelpOutline
    "history" -> Icons.Filled.History
    "robot" -> Icons.Filled.SmartToy
    "game" -> Icons.Filled.SportsEsports
    "users" -> Icons.Filled.Groups
    "link" -> Icons.Filled.Link
    "finance" -> Icons.Filled.AttachMoney
    // Remaining docs/design/Algora.dc.html SVG names (nav + chrome + app-card icons).
    "music" -> Icons.Filled.MusicNote
    "undo" -> Icons.AutoMirrored.Filled.Undo
    "book" -> Icons.AutoMirrored.Filled.MenuBook
    "flask" -> Icons.Filled.Science
    "target" -> Icons.Filled.TrackChanges
    "chart" -> Icons.Filled.BarChart
    "moon" -> Icons.Filled.DarkMode
    "sun" -> Icons.Filled.LightMode
    "flame" -> Icons.Filled.LocalFireDepartment
    "lock" -> Icons.Filled.Lock
    "chev" -> Icons.Filled.ChevronRight
    "back" -> Icons.AutoMirrored.Filled.ArrowBack
    "check" -> Icons.Filled.CheckCircle
    "crown" -> Icons.Filled.WorkspacePremium
    "bulb" -> Icons.Filled.Lightbulb
    "info" -> Icons.Filled.Info
    "menu" -> Icons.Filled.Menu
    // Practice tab's 4-card grid: concept-specific glyphs rather than the generic
    // chip/help/stack/target reused for the Learning-tab category cards.
    "code" -> Icons.Filled.Code
    "quiz" -> Icons.Filled.Quiz
    "cards" -> Icons.Filled.Style
    "mic" -> Icons.Filled.RecordVoiceOver
    // Home's Quick Access grid: Data Structures/Algorithms/NLP get glyphs tied to their
    // actual subject matter instead of the generic stack/chip/globe reused elsewhere.
    "tree" -> Icons.Filled.AccountTree
    "functions" -> Icons.Filled.Functions
    "translate" -> Icons.Filled.Translate
    else -> Icons.Filled.Circle
}
