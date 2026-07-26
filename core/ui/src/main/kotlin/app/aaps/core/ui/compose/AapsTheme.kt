package app.aaps.core.ui.compose

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.core.view.WindowInsetsControllerCompat
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
 * Smallest-screen-width threshold (in dp) above which the device is treated as a tablet.
 * Drives both typography scaling in [AapsTheme] and the tablet layout dispatch in OverviewScreen.
 * Single source of truth — keep both call sites in sync by referencing this constant.
 */
const val TABLET_MIN_SW_DP = 600

/**
 * CompositionLocal providing the tablet typography/dimension scale factor.
 * 1f on phones, larger (e.g. 1.5f) on tablets. Use to scale dp dimensions
 * (icon sizes, custom shapes) so they match the scaled typography.
 */
val LocalAapsScale = compositionLocalOf { 1f }

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
 * CompositionLocal exposing whether the master phone is currently reachable (see
 * `NsClient.masterReachable`). Defaults to `true` so master, previews, and any non-client context
 * are never gated; the main activity provides the live value on a client. Used to disable
 * `Bidirectional` synced preference rows while the master is offline (their edits can't sync).
 */
val LocalMasterReachable = compositionLocalOf { true }

/**
 * Whether the master currently allows remote control (its stop/allow switch, see
 * `NsClient.masterControlAllowed`). Defaults to `true` (master/previews/unpaired never show the
 * distinct wording). When `false` on a client the offline banner shows a "remote control disabled"
 * message instead of the generic "unreachable" one. Editing is already gated via [LocalMasterReachable]
 * (which folds this in); this local only selects the banner wording.
 */
val LocalMasterControlAllowed = compositionLocalOf { true }

/**
 * Whether the user may make changes that need the master *right now*. On a master always `true` (no
 * gating). On a client, `true` only while [LocalMasterReachable] is `true` — i.e. the master is
 * reachable for the signed Client-Control channel that carries both config edits and remote actions.
 * Single source for the per-screen offline gate (`!(AAPSCLIENT && !masterReachable)`); gates BOTH
 * synced-config edits AND master-bound action buttons, so screens call this instead of re-spelling it.
 * Returns `true` in `@Preview`/inspection — [LocalConfig] has no default (it would throw), and previews
 * should render the enabled, un-gated state anyway (matching the `true` defaults of the other locals).
 */
@Composable
fun masterEditingEnabled(): Boolean =
    LocalInspectionMode.current || !(LocalConfig.current.AAPSCLIENT && !LocalMasterReachable.current)

/**
 * CompositionLocal providing access to ProfileUtil for glucose unit conversions.
 * Avoids threading profileUtil through multiple composable layers.
 */
val LocalProfileUtil = compositionLocalOf<ProfileUtil> { error("No ProfileUtil provided") }

/**
 * CompositionLocal providing access to SnackbarHostState for showing snackbars.
 * Provided by Scaffold hosts (MainScreen, PluginContent) so any descendant
 * composable can show snackbars without parameter threading.
 *
 * Throws if consumed outside a provider — this is intentional to prevent
 * silent snackbar drops. If your screen is outside a Scaffold tree, prefer
 * passing the state as a parameter or hoisting events via a ViewModel flow.
 */
val LocalSnackbarHostState = compositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided — wrap caller in a Scaffold that provides LocalSnackbarHostState, or pass the state as a parameter")
}

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

    // Keep system bar icon color in sync with the AAPS-effective theme so
    // status/nav bar icons stay legible against the bar scrims (which use
    // colorScheme.surface). Reactive — no activity recreate needed.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowInsetsControllerCompat(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    val scheme = if (isDark) darkColors else lightColors
    val profileViewerColors = if (isDark) DarkProfileHelperColors else LightProfileHelperColors
    val treatmentIconColors = if (isDark) DarkElementColors else LightElementColors
    val generalColors = if (isDark) DarkGeneralColors else LightGeneralColors
    val snackbarColors = if (isDark) DarkSnackbarColors else LightSnackbarColors

    // Scale typography up on tablets. Orientation-independent (smallest-width signal).
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= TABLET_MIN_SW_DP
    val typographyScale = if (isTablet) 1.5f else 1f
    val scaledMaterialTypography = remember(typographyScale) { Typography().withoutFontPadding().scaled(typographyScale) }

    CompositionLocalProvider(
        LocalProfileHelperColors provides profileViewerColors,
        LocalElementColors provides treatmentIconColors,
        LocalGeneralColors provides generalColors,
        LocalSnackbarColors provides snackbarColors,
        LocalAapsScale provides typographyScale,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = scaledMaterialTypography,
        ) {
            CompositionLocalProvider(
                LocalAapsTypography provides aapsTypography(typographyScale),
                // Bare `Text()` (e.g. the overview chips) reads LocalTextStyle, not the typography,
                // so disable font padding here too — keeps the default size, just metric-centers glyphs.
                LocalTextStyle provides LocalTextStyle.current.copy(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None
                    )
                ),
                content = content
            )
        }
    }
}
