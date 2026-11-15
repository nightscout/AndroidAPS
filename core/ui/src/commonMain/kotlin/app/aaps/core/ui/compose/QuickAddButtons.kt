package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import app.aaps.core.ui.CoreUiStrings

/**
 * @see QuickAddButtonsPreview
 */
@Composable
fun QuickAddButtons(
    increment1: Int,
    increment2: Int,
    increment3: Int,
    onAddCarbs: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val increments = listOf(increment1, increment2, increment3).filter { it > 0 }
    if (increments.isEmpty()) return

    val focusManager = LocalFocusManager.current

    // A FlowRow, so that with a big font a button that does not fit moves to the next line
    // instead of being squeezed until its text breaks.
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        increments.forEach { amount ->
            FilledTonalButton(onClick = {
                focusManager.clearFocus()
                onAddCarbs(amount)
            }) {
                Text(stringResource(CoreUiStrings.value_with_plus_sign, amount.toString()))
            }
        }
    }
}
