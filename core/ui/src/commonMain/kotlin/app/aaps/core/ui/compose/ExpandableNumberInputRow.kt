package app.aaps.core.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings

/**
 * A number that is usually only nudged, and typed only when it really has to be.
 *
 * ```
 * collapsed:  Label:  2.00 U     (-) (+) (pencil)
 * expanded:   Label:  [ 2.00 ]   (-) (+)          <- the full NumberInputRow
 * ```
 *
 * Use it for the secondary numbers on a screen and keep [NumberInputRow] for the one the screen is
 * about, which should stay open. The difference is not decoration: a second field that looks like
 * the primary one invites a number meant for the primary into it, which on the bolus wizard meant
 * grams typed into the correction and bolused as units.
 *
 * Collapsed it is the same header as every other collapsible row, so a screen that mixes the two
 * reads as one list. Expanded it *is* [NumberInputRow] - the text handling, the range message, the
 * error state and the mid-entry behaviour are that component's, not copied here.
 *
 * Expansion is remembered internally. Pass [expanded] and [onExpandedChange] when something else
 * has to open the row, for example a reading that is too old to trust.
 *
 * @param inputLabelRef label of the open field, when it is not the one in the header. Carb time
 *   heads the row "Carb time" and labels the field inside it "Time".
 * @param valueContent what the collapsed row shows after the label. The default is the value
 *   formatted exactly as the open row formats it. Pass your own when the row says more than the
 *   number, such as how old a reading is.
 * @param expandedContent anything that belongs under the field while the row is open, such as the
 *   alarm switch of carb time.
 */
@Composable
fun ExpandableNumberInputRow(
    labelRef: TextRef,
    value: Double,
    onValueChange: (Double) -> Unit,
    valueRange: ClosedFloatingPointRange<Double>,
    step: Double,
    modifier: Modifier = Modifier,
    unitLabel: TextRef? = null,
    valueFormat: NumberFormat? = null,
    decimalPlaces: Int = 0,
    enabled: Boolean = true,
    inputLabelRef: TextRef = labelRef,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    valueContent: (@Composable FlowRowScope.() -> Unit)? = null,
    expandedContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    // Saveable, so the row is still open after a rotation.
    var expandedInternally by rememberSaveable { mutableStateOf(false) }
    val isExpanded = expanded ?: expandedInternally
    val setExpanded = { open: Boolean -> onExpandedChange?.invoke(open) ?: run { expandedInternally = open } }

    val effectiveValueFormat = valueFormat ?: remember(decimalPlaces) { NumberFormat.withDecimals(decimalPlaces) }
    val label = stringResource(labelRef)
    val resolvedUnitLabel = unitLabel?.let { stringResource(it) } ?: ""

    // Same wording as the open row, so the two states speak alike.
    val stepNumber = effectiveValueFormat.format(step)
    val stepText =
        if (resolvedUnitLabel.isNotEmpty()) stringResource(CoreUiStrings.value_with_unit, stepNumber, resolvedUnitLabel)
        else stepNumber
    val decreaseDescription =
        if (label.isNotEmpty()) stringResource(CoreUiStrings.a11y_min_button_description, label, stepText)
        else stringResource(CoreUiStrings.decrement)
    val increaseDescription =
        if (label.isNotEmpty()) stringResource(CoreUiStrings.a11y_plus_button_description, label, stepText)
        else stringResource(CoreUiStrings.increment)

    Column(modifier = modifier) {
        ExpandableRowHeader(
            label = label,
            expanded = isExpanded,
            onExpand = { setExpanded(true) },
            actions = {
                // Only while closed: the open field brings its own +/-, and two pairs on one row
                // would be a puzzle.
                if (!isExpanded) {
                    // No field to read here, so stepping is simply from the value we were given.
                    StepButtons(
                        decreaseDescription = decreaseDescription,
                        increaseDescription = increaseDescription,
                        canDecrease = enabled && value > valueRange.start,
                        canIncrease = enabled && value < valueRange.endInclusive,
                        onStep = { direction ->
                            onValueChange(roundToStep(value + direction * step, step).coerceIn(valueRange))
                        }
                    )
                }
                IconButton(onClick = { setExpanded(!isExpanded) }, enabled = enabled) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        // Named, not a bare "Expand": a screen reader meets several of these on one
                        // screen, and the +/- beside them already say which field they belong to.
                        contentDescription = stringResource(
                            if (isExpanded) CoreUiStrings.a11y_collapse_button_description
                            else CoreUiStrings.a11y_expand_button_description,
                            label
                        )
                    )
                }
            }
        ) {
            if (valueContent != null) valueContent()
            else Text(
                text = formatSliderDisplayValue(
                    value = value,
                    unitLabel = unitLabel,
                    valueFormat = effectiveValueFormat
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                NumberInputRow(
                    labelRef = inputLabelRef,
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    step = step,
                    unitLabel = unitLabel,
                    valueFormat = valueFormat,
                    decimalPlaces = decimalPlaces,
                    enabled = enabled
                )
                expandedContent?.invoke(this)
            }
        }
    }
}
