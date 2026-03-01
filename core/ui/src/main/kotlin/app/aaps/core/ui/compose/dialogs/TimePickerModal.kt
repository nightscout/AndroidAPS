package app.aaps.core.ui.compose.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.ui.R

/**
 * A modal time picker dialog.
 *
 * @param onTimeSelected Called with the selected hour and minute
 * @param onDismiss Called when dialog is dismissed
 * @param initialHour Initial hour (0-23)
 * @param initialMinute Initial minute (0-59)
 * @param is24Hour Whether to use 24-hour format
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerModal(
    onTimeSelected: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    initialHour: Int = 0,
    initialMinute: Int = 0,
    is24Hour: Boolean = true
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = is24Hour
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        text = { TimePicker(state = timePickerState) },
        confirmButton = {
            TextButton(onClick = {
                onTimeSelected(timePickerState.hour, timePickerState.minute)
                onDismiss()
            }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
    )
}

@Preview(showBackground = true)
@Composable
private fun TimePickerModalPreview() {
    MaterialTheme {
        TimePickerModal(
            onTimeSelected = { _, _ -> },
            onDismiss = {},
            initialHour = 14,
            initialMinute = 30
        )
    }
}
