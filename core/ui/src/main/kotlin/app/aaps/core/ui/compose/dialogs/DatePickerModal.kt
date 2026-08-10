package app.aaps.core.ui.compose.dialogs

import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.UiStrings
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import app.aaps.core.ui.R

/**
 * A modal date picker dialog.
 *
 * @param onDateSelected Called with the selected date in milliseconds (UTC)
 * @param onDismiss Called when dialog is dismissed
 * @param initialDateMillis Initial date to display (UTC milliseconds)
 *
 * @see DatePickerModalPreview
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerModal(
    onDateSelected: (Long?) -> Unit,
    onDismiss: () -> Unit,
    initialDateMillis: Long? = null
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDateSelected(datePickerState.selectedDateMillis)
                onDismiss()
            }) {
                Text(stringResource(UiStrings.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(UiStrings.cancel))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}
