package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.aaps.core.ui.CoreUiStrings

/**
 * Header of a row whose input opens when the user taps "Change":
 *
 * `Label: value                  [Change]`
 *
 * The label and the values are in a FlowRow. With a big font, a value that does not fit moves to
 * the next line as a whole. In a plain Row it was squeezed until it wrapped one letter per line.
 *
 * @param label the field name. A colon is added after it.
 * @param expanded true while the input is open. The "Change" button is hidden then.
 * @param onExpand called when the user taps "Change"
 * @param content the value, shown after the label
 */
@Composable
fun ExpandableRowHeader(
    label: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
    ) {
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(CoreUiStrings.label_with_colon, label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
        if (!expanded) {
            FilledTonalButton(onClick = onExpand) {
                Text(stringResource(CoreUiStrings.change))
            }
        }
    }
}
