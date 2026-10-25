package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout

/**
 * Shows [long] when it fits on one line in the width it is given, and [short] when it does not.
 *
 * For content that has a short form, like "3 days 4 hours" and "3d4h": the long one reads better, the
 * short one is what is left when the room is not there. Decided by measuring, not by the kind of
 * device, so it holds on any screen size and with any system font size. It used to be a phone or
 * tablet switch, and a folding phone opened up got the tablet answer with less room than a phone.
 *
 * "Fits" means the widest one-line size of [long] is not wider than this. Only the chosen one is
 * composed into the screen, so a screen reader and a test see one text, not both.
 *
 * Not for use inside an intrinsic measurement (`IntrinsicSize`): a subcomposed layout cannot answer
 * one.
 */
@Composable
fun LongOrShort(
    long: @Composable () -> Unit,
    short: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    SubcomposeLayout(modifier) { constraints ->
        val longMeasurables = subcompose(LongOrShortSlot.Long, long)
        val fits = !constraints.hasBoundedWidth ||
            longMeasurables.all { it.maxIntrinsicWidth(constraints.maxHeight) <= constraints.maxWidth }
        val chosen = if (fits) longMeasurables else subcompose(LongOrShortSlot.Short, short)
        val placeables = chosen.map { it.measure(constraints) }
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placeables.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            placeables.forEach { it.placeRelative(0, 0) }
        }
    }
}

private enum class LongOrShortSlot { Long, Short }
