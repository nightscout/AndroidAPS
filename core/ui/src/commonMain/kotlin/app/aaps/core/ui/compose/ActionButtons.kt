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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints

/**
 * An action of a screen, shown as a button by [ActionButtons]: refresh, pair, full sync, clear log.
 */
@Immutable
data class ScreenAction(
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val onClick: () -> Unit
)

/**
 * The action buttons at the bottom of a screen. Pump screens and the sync screens use it.
 *
 * Material says a button label is never cut and stays on one line, so the layout follows the labels:
 * - A button whose label fits in half the width shares a row with the next such button.
 * - A button whose label does not fit gets a full row of its own. A half-width button before it waits
 *   for the next one that fits, so a long label does not leave two half-empty rows.
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
            row.map { index -> measurables[index].measure(Constraints.fixedWidth(if (fitsHalf[index]) halfWidth else width)) }
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

@Composable
private fun ActionButton(action: ScreenAction) {
    FilledTonalButton(
        onClick = action.onClick,
        enabled = action.enabled,
        contentPadding = PaddingValues(horizontal = AapsSpacing.large, vertical = AapsSpacing.medium)
    ) {
        action.icon?.let {
            Icon(
                imageVector = it,
                // The text beside it names the button; naming the icon too read "Refresh Refresh".
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
                tint = Color.Unspecified
            )
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
