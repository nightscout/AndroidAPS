package app.aaps.core.ui.compose

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Color scheme for profile viewer, profile helper, and profile comparison screens.
 * Provides consistent color coding for distinguishing between two profiles in comparison mode.
 *
 * **Usage:**
 * - Profile viewer activities and dialogs
 * - Profile helper activity (3-tab interface for creating and comparing profiles)
 * - Profile comparison graphs (Basal, IC, ISF, Target BG)
 * - Profile comparison tables
 *
 * **Color Assignment:**
 * - profile1: Blue (#2196F3) - Primary profile or first comparison profile
 * - profile2: Red (#F44336) - Second comparison profile
 *
 * Colors are chosen for:
 * - High contrast and easy distinction
 * - Medical/clinical clarity (avoiding confusion between profiles)
 * - Accessibility (distinguishable by most users including those with color vision deficiencies)
 *
 * @property profile1 Color for the primary or first profile (blue)
 * @property profile2 Color for the second profile in comparison mode (red)
 */
data class ProfileHelperColors(
    val profile1: Color,
    val profile2: Color
)

/**
 * Light mode color scheme for profile helper screens.
 * Uses Material Design Blue 500 and Red 500 for optimal contrast on light backgrounds.
 */
internal val LightProfileHelperColors = ProfileHelperColors(
    profile1 = Color(0xFF2196F3), // Material Blue 500
    profile2 = Color(0xFFF44336)  // Material Red 500
)

/**
 * Dark mode color scheme for profile helper screens.
 * Currently uses the same colors as light mode for consistency.
 * Future enhancement: May adjust brightness for better dark mode visibility.
 */
internal val DarkProfileHelperColors = ProfileHelperColors(
    profile1 = Color(0xFF2196F3), // Material Blue 500 (same as light mode)
    profile2 = Color(0xFFF44336)  // Material Red 500 (same as light mode)
)

/**
 * CompositionLocal providing profile helper colors based on current theme (light/dark).
 * Accessed via AapsTheme.profileHelperColors in composables.
 */
internal val LocalProfileHelperColors = compositionLocalOf { LightProfileHelperColors }
