package app.aaps.core.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.UiMode
import app.aaps.core.ui.compose.navigation.DarkElementColors
import app.aaps.core.ui.compose.navigation.ElementColors
import app.aaps.core.ui.compose.navigation.LightElementColors
import app.aaps.core.ui.compose.navigation.LocalElementColors

/**
 * CompositionLocal providing access to user preferences for theme configuration.
 * Used to retrieve dark mode setting and react to preference changes.
 */
val LocalPreferences = compositionLocalOf<Preferences> { error("No Preferences provided") }

/**
 * CompositionLocal providing access to DateUtil for date/time formatting.
 * Avoids threading dateUtil through multiple composable layers.
 */
val LocalDateUtil = compositionLocalOf<DateUtil> { error("No DateUtil provided") }

/**
 * CompositionLocal providing access to app configuration (build flavors, feature flags).
 * Avoids threading config through multiple composable layers.
 */
val LocalConfig = compositionLocalOf<Config> { error("No Config provided") }

/**
 * CompositionLocal providing access to ProfileUtil for glucose unit conversions.
 * Avoids threading profileUtil through multiple composable layers.
 */
val LocalProfileUtil = compositionLocalOf<ProfileUtil> { error("No ProfileUtil provided") }

/**
 * CompositionLocal providing access to SnackbarHostState for showing snackbars.
 * Provided by Scaffold hosts (MainScreen, PluginContent) so any descendant
 * composable can show snackbars without parameter threading.
 */
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> { SnackbarHostState() }

/**
 * AndroidAPS theme object providing access to custom theme colors and extensions.
 * Supplements Material 3 theme with AndroidAPS-specific color schemes.
 *
 * **Available Color Schemes:**
 * - profileHelperColors: Colors for profile viewer and comparison screens
 * - elementColors: Colors for treatment tab icons and elements
 * - generalColors: Colors for general UI elements (IOB, COB, etc.)
 *
 * **Usage:**
 * ```kotlin
 * @Composable
 * fun MyProfileGraph() {
 *     val colors = AapsTheme.profileHelperColors
 *     LineChart(color = colors.profile1)  // Use blue for primary profile
 * }
 *
 * @Composable
 * fun MyTreatmentTab() {
 *     val colors = AapsTheme.elementColors
 *     Icon(tint = colors.bolusCarbs)  // Use orange for bolus/carbs icon
 * }
 *
 * @Composable
 * fun MyOverviewScreen() {
 *     val colors = AapsTheme.generalColors
 *     Text(color = colors.activeInsulinText)  // Use IOB color for active insulin
 * }
 * ```
 */
object AapsTheme {

    /**
     * Color scheme for profile helper, profile viewer, and profile comparison screens.
     * Provides consistent blue/red color coding for distinguishing between two profiles.
     *
     * Automatically adapts to light/dark mode based on current theme.
     *
     * @see ProfileHelperColors for detailed color assignments
     */
    val profileHelperColors: ProfileHelperColors
        @Composable
        @ReadOnlyComposable
        get() = LocalProfileHelperColors.current

    /**
     * Color scheme for basic elements.
     *
     * Automatically adapts to light/dark mode based on current theme.
     *
     */
    val elementColors: ElementColors
        @Composable
        @ReadOnlyComposable
        get() = LocalElementColors.current

    /**
     * Color scheme for general UI elements.
     * Provides colors for common elements like IOB, COB, etc.
     *
     * Automatically adapts to light/dark mode based on current theme.
     *
     * @see GeneralColors for detailed color assignments
     */
    val generalColors: GeneralColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGeneralColors.current

    /**
     * Color scheme for snackbar messages.
     * Provides colors for error, warning, info, and success messages.
     *
     * Automatically adapts to light/dark mode based on current theme.
     *
     * @see SnackbarColors for detailed color assignments
     */
    val snackbarColors: SnackbarColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSnackbarColors.current

    /**
     * Custom typography scale for domain-specific text styles.
     * Provides styles for BG display, treatments, chips, and section headers.
     *
     * @see AapsTypography for available text styles
     */
    val typography: AapsTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalAapsTypography.current

    /**
     * Centralized spacing and dimension constants.
     * Provides generic spacing scale and domain-specific dimensions.
     *
     * @see AapsSpacing for available values
     */
    val spacing: AapsSpacing get() = AapsSpacing
}

/**
 * Main AndroidAPS theme wrapper that applies Material 3 theming with custom extensions.
 * Wraps content with Material 3 ColorScheme and provides AndroidAPS-specific theme values.
 *
 * **Features:**
 * - Material 3 color scheme (light/dark mode)
 * - User preference-based theme selection (Light, Dark, System)
 * - Reactive theme switching (listens to preference changes via Flow)
 * - Custom AndroidAPS color schemes (ProfileHelperColors)
 *
 * **Theme Modes:**
 * - LIGHT: Always use light theme
 * - DARK: Always use dark theme
 * - SYSTEM: Follow system dark mode setting
 *
 * The theme automatically updates when user changes dark mode preference in settings.
 *
 * **Usage:**
 * ```kotlin
 * setContent {
 *     AapsTheme {
 *         MyScreen()
 *     }
 * }
 * ```
 *
 * @param content The composable content to wrap with the theme
 */
@Composable
fun AapsTheme(
    content: @Composable () -> Unit
) {
    val preferences = LocalPreferences.current
    val darkModeValue by preferences.observe(StringKey.GeneralDarkMode).collectAsState()
    val uiMode = UiMode.fromString(darkModeValue)

    val lightColors = lightColorScheme()
    val darkColors = darkColorScheme(
        secondaryContainer = Color(0xFF635F6A)
    )

    val isDark = when (uiMode) {
        UiMode.LIGHT  -> false
        UiMode.DARK   -> true
        UiMode.SYSTEM -> isSystemInDarkTheme()
    }

    val scheme = if (isDark) darkColors else lightColors
    val profileViewerColors = if (isDark) DarkProfileHelperColors else LightProfileHelperColors
    val treatmentIconColors = if (isDark) DarkElementColors else LightElementColors
    val generalColors = if (isDark) DarkGeneralColors else LightGeneralColors
    val snackbarColors = if (isDark) DarkSnackbarColors else LightSnackbarColors

    CompositionLocalProvider(
        LocalProfileHelperColors provides profileViewerColors,
        LocalElementColors provides treatmentIconColors,
        LocalGeneralColors provides generalColors,
        LocalSnackbarColors provides snackbarColors
    ) {
        MaterialTheme(
            colorScheme = scheme,
        ) {
            CompositionLocalProvider(
                LocalAapsTypography provides aapsTypography(),
                content = content
            )
        }
    }
}
