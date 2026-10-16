package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The +/- pair used by [NumberInputRow] and by the collapsed state of [ExpandableNumberInputRow].
 *
 * ```
 * (-) (+)
 * ```
 *
 * What a step *means* is the caller's, not this component's: [NumberInputRow] steps from the text
 * currently in its field, so that typing which has not reached the parent yet is not thrown away,
 * while a row with no field steps from the value it was given. Those are different on purpose, so
 * only the parts that must look and sound the same live here - the icons, their size, the hold to
 * repeat, which button is disabled at the edge of the range, and the spoken names.
 *
 * Emits the two buttons straight into the caller's row, so it takes no modifier of its own.
 *
 * @param decreaseDescription spoken name of the - button, e.g. "decrement Bolus by 0.1 U"
 * @param increaseDescription spoken name of the + button
 * @param canDecrease false at the bottom of the range, which disables the - button
 * @param canIncrease false at the top of the range, which disables the + button
 * @param onStep called with -1 or 1 on a tap and on every repeat while held
 */
@Composable
fun StepButtons(
    decreaseDescription: String,
    increaseDescription: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onStep: (direction: Int) -> Unit
) {
    RepeatingIconButton(
        onClick = { onStep(-1) },
        enabled = canDecrease
    ) {
        Icon(
            imageVector = Icons.Filled.Remove,
            contentDescription = decreaseDescription,
            modifier = Modifier.size(20.dp)
        )
    }
    RepeatingIconButton(
        onClick = { onStep(1) },
        enabled = canIncrease
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = increaseDescription,
            modifier = Modifier.size(20.dp)
        )
    }
}
