package app.aaps.core.ui.compose

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Color scheme for snackbar messages.
 * Provides consistent colors for different message types (Error / Warning / Info / Success).
 */
data class SnackbarColors(
    val errorContainer: Color,
    val onErrorContainer: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val successContainer: Color,
    val onSuccessContainer: Color
)

/** Light mode snackbar colors. */
internal val LightSnackbarColors = SnackbarColors(
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    warningContainer = Color(0xFFFFE082),
    onWarningContainer = Color(0xFF3E2723),
    infoContainer = Color(0xFFBBDEFB),
    onInfoContainer = Color(0xFF0D47A1),
    successContainer = Color(0xFFC8E6C9),
    onSuccessContainer = Color(0xFF1B5E20)
)

/** Dark mode snackbar colors. */
internal val DarkSnackbarColors = SnackbarColors(
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    warningContainer = Color(0xFF5D4037),
    onWarningContainer = Color(0xFFFFE082),
    infoContainer = Color(0xFF1565C0),
    onInfoContainer = Color(0xFFBBDEFB),
    successContainer = Color(0xFF2E7D32),
    onSuccessContainer = Color(0xFFC8E6C9)
)

/** CompositionLocal providing snackbar colors based on current theme. */
internal val LocalSnackbarColors = compositionLocalOf { LightSnackbarColors }
