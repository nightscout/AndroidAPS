package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints

/**
 * An action of a screen, shown as a button by [ActionButtons]: refresh, pair, full sync, clear log.
 *
 * @param iconKeepsColours the icon has colours of its own (Equil's amber suspend and green resume): drawn as it is,
 *   faded when the button is disabled. Otherwise the icon is in the colour of the button's text, and greyed with it.
 */
@Immutable
data class ScreenAction(
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val iconKeepsColours: Boolean = false,
    val onClick: () -> Unit
)

/**
 * The action buttons at the bottom of a screen. Pump screens and the sync screens use it.
 *
 * Material says a button label is never cut and stays on one line, so the layout follows the labels:
 * - A button whose label fits in half the width shares a row with the next such button.
 * - A button whose label does not fit gets a full row of its own. A half-width button before it waits
 *   for the next one that fits, so a long label does not leave two half-empty rows.
 * - A button that is left without a partner takes the whole row, so no row is half empty: "Pause all
 *   for…" sat alone on a half row over a full-width "Reset rules to default" (iOS, 2026-11-27).
 * - If a label does not fit even in the full width (a very large font), it goes to a second line.
 *   Losing a part of the label is worse than wrapping it.
 *
 * Before, every button got half the width and a long label lost its end without any sign: French
 * "Changer le patch" showed as "Changer le" (#5339).
 */
@Composable
fun ActionButtons(actions: List<ScreenAction>, modifier: Modifier = Modifier) {
    val visible = actions.filter { it.visible }
    if (visible.isEmpty()) return

    Layout(
        content = { visible.forEach { ActionButton(it) } },
        modifier = modifier
    ) { measurables, constraints ->
        check(constraints.hasBoundedWidth) { "ActionButtons needs a bounded width" }
        val width = constraints.maxWidth
        val gap = AapsSpacing.large.roundToPx()
        val halfWidth = (width - gap) / 2
        // The max intrinsic width is the width of the label on one line, with the icon and the padding
        val fitsHalf = measurables.map { it.maxIntrinsicWidth(Constraints.Infinity) <= halfWidth }

        // Indexes of the buttons in each row
        val rows = mutableListOf<MutableList<Int>>()
        var waitingRow: MutableList<Int>? = null
        for (index in measurables.indices) {
            val waiting = waitingRow
            when {
                !fitsHalf[index] -> rows += mutableListOf(index)

                waiting != null  -> {
                    waiting += index
                    waitingRow = null
                }

                else             -> {
                    val row = mutableListOf(index)
                    rows += row
                    waitingRow = row
                }
            }
        }

        val placeables: List<List<Placeable>> = rows.map { row ->
            // Half only beside a partner; alone, also a short one takes the row
            row.map { index -> measurables[index].measure(Constraints.fixedWidth(if (row.size > 1) halfWidth else width)) }
        }
        val height = placeables.sumOf { row -> row.maxOf { it.height } }
        layout(width, height) {
            var y = 0
            placeables.forEach { row ->
                var x = 0
                row.forEach { placeable ->
                    placeable.placeRelative(x, y)
                    x += placeable.width + gap
                }
                y += row.maxOf { it.height }
            }
        }
    }
}

/** How strong the colours of a disabled button's own-coloured icon are, as Material fades disabled content. */
private const val DISABLED_ICON_ALPHA = 0.38f

@Composable
private fun ActionButton(action: ScreenAction) {
    FilledTonalButton(
        onClick = action.onClick,
        enabled = action.enabled,
        contentPadding = PaddingValues(horizontal = AapsSpacing.large, vertical = AapsSpacing.medium)
    ) {
        action.icon?.let {
            // In the text's colour, greyed with it: with no tint a Material icon was black, and stayed black on a
            // disabled button. One with colours of its own keeps them, faded as a disabled button's text is.
            if (action.iconKeepsColours) Icon(
                imageVector = it,
                // The text beside it names the button; naming the icon too read "Refresh Refresh".
                contentDescription = null,
                modifier = Modifier
                    .size(ButtonDefaults.IconSize)
                    .alpha(if (action.enabled) 1f else DISABLED_ICON_ALPHA),
                tint = Color.Unspecified
            )
            else Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        }
        Text(
            text = action.label,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
