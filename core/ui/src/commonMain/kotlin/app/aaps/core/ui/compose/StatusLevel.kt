package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings

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

/**
 * What a [StatusLevel] means in words, for a screen reader.
 *
 * Until this existed, [statusLevelToColor] was the only thing that read a StatusLevel at all, so a
 * cannula past its change day or a nearly empty reservoir was red text and nothing else. Anyone who
 * cannot see the colour - or cannot tell red from grey - got the number with no hint that it needed
 * attention.
 *
 * Lives beside the colour for the same reason the trend arrow's description lives beside its icon:
 * turning a classification into something a person can perceive is the drawing layer's job. Returns
 * a [TextRef] so a Composable can resolve it with `stringResource(...)` and other code with
 * `rh.gs(...)`.
 *
 * NORMAL and UNSPECIFIED return null on purpose. Saying "normal" after every value on a status list
 * would make the ordinary case the noisiest thing on the screen, and the point is to mark what is
 * NOT ordinary.
 */
fun statusLevelToDescription(status: StatusLevel): TextRef? = when (status) {
    StatusLevel.WARNING                         -> CoreUiStrings.warning
    StatusLevel.CRITICAL                        -> CoreUiStrings.critical
    StatusLevel.NORMAL, StatusLevel.UNSPECIFIED -> null
}
