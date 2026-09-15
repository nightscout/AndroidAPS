package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Status severity level for theming.
 * Used across pump overview, actions screen, and status components.
 */
enum class StatusLevel {

    NORMAL,
    WARNING,
    CRITICAL,
    UNSPECIFIED
}

/**
 * Maps a StatusLevel to the appropriate color from the theme.
 */
@Composable
fun statusLevelToColor(status: StatusLevel): Color {
    val colors = AapsTheme.generalColors
    return when (status) {
        StatusLevel.NORMAL      -> MaterialTheme.colorScheme.onSurface
        StatusLevel.WARNING     -> colors.statusWarning
        StatusLevel.CRITICAL    -> colors.statusCritical
        StatusLevel.UNSPECIFIED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
