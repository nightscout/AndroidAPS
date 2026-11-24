package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection

// How the overview chips show their state. A chip has no fill. Its state is in its icon and text: the
// colour of the temp target icon, the profile star and its percentage, the TBR direction and percent.
//
// A chip that runs out (a temp target, a profile or a running mode with an end, a TBR) gets a faint
// neutral track over the whole chip, and a darker shade grows over it from the start (the right in a
// right-to-left language) with the time that has passed, like a progress bar. A chip with no end gets
// neither: the box means one thing only, that
// this runs out. Only a problem gets a filled colour: no profile, a suspended or disconnected pump.
//
// Before, every chip with a value had a fill in its own colour, five or six tinted boxes next to the BG
// circle, and the chip that was really temporary did not stand out. A shade for the time left was tried
// and read the wrong way round: most people read a filled part as the part that is done. A track for a
// chip with no end looked like an empty timer.

/** Strength of the darker shade for the time passed: weak enough that the text reads the same on both sides of its edge. */
private const val CHIP_SHADE_ALPHA = 0.1f

/** Strength of the track over the whole chip, which shows its full length. */
private const val CHIP_TRACK_ALPHA = 0.04f

/**
 * The track and the shade of a chip that runs out. [progress] is the share of its time that has passed;
 * 0 for a chip with no end, which then gets nothing. Put it before any padding, so it covers the whole chip.
 */
@Composable
internal fun Modifier.chipShade(progress: Float): Modifier {
    if (progress <= 0f) return this
    val onSurface = MaterialTheme.colorScheme.onSurface
    val track = onSurface.copy(alpha = CHIP_TRACK_ALPHA)
    val shade = onSurface.copy(alpha = CHIP_SHADE_ALPHA)
    val passed = progress.coerceAtMost(1f)
    return drawBehind {
        drawRect(color = track)
        val width = size.width * passed
        // From the start of the reading direction
        val left = if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f
        drawRect(color = shade, topLeft = Offset(left, 0f), size = Size(width, size.height))
    }
}

/** How strong the filled background of a chip that shows a problem is, see [chipShade]. */
internal const val CHIP_PROBLEM_FILL_ALPHA = 0.2f
