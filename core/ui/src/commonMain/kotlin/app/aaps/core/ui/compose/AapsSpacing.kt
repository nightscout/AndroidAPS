package app.aaps.core.ui.compose

import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsSpacing.extraLarge
import app.aaps.core.ui.compose.AapsSpacing.extraSmall
import app.aaps.core.ui.compose.AapsSpacing.large
import app.aaps.core.ui.compose.AapsSpacing.medium
import app.aaps.core.ui.compose.AapsSpacing.small
import app.aaps.core.ui.compose.AapsSpacing.xxLarge

/**
 * Centralized spacing and dimension constants for AAPS Compose UI.
 *
 * **Generic spacing scale** — use for padding, margins, arrangement spacing:
 * - [extraSmall] (2.dp), [small] (4.dp), [medium] (8.dp),
 *   [large] (12.dp), [extraLarge] (16.dp), [xxLarge] (24.dp)
 *
 * **Domain-specific dimensions** — named constants for specific UI elements.
 *
 * This is a plain object (no CompositionLocal) because spacing values are
 * static and theme-independent.
 *
 * **Usage:**
 * ```kotlin
 * Modifier.padding(AapsSpacing.medium)  // 8.dp
 * Modifier.size(AapsSpacing.bgCircleSize)  // 126.dp
 * ```
 */
object AapsSpacing {

    // Generic spacing scale
    val extraSmall = 2.dp
    val small = 4.dp
    val medium = 8.dp
    val large = 12.dp
    val extraLarge = 16.dp
    val xxLarge = 24.dp

    // BG circle dimensions
    val bgCircleSize = 126.dp
    val bgRingStrokeWidth = 8.dp

    // Trend arrow drawn next to a BG value
    val trendArrowSize = 20.dp

    // Space below the last item of a list on a screen with a FloatingActionButton (56 dp button, 16 dp margin,
    // 16 dp gap), so the button never covers that item when the list is scrolled to the end
    val fabClearance = 88.dp

    // Ring drawn inside the small FAB that shows pump communication. Smaller than the 40 dp button,
    // so it sits between the button edge and the 24 dp icon without touching either
    val pumpFabRingSize = 36.dp
    val pumpFabRingStrokeWidth = 2.dp

    // Chip dimensions
    val chipCornerRadius = 8.dp
    val chipHeight = 35.dp
    val chipIconSize = 24.dp

    // Icon button on a list card with several actions side by side. Smaller than the 48 dp default so
    // the card text keeps its width
    val compactIconButtonSize = 36.dp

    // Widest the value column of a food card may be (portion and carbs); a longer unit is cut with "…"
    val foodValueMaxWidth = 80.dp

    // Product photo of a food: small on a list card, larger beside the name in the editors (two icon
    // buttons high, so the photo does not make the editor longer)
    val foodThumbnailSize = 48.dp
    val foodPhotoSize = 96.dp
}
