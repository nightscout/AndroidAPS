package app.aaps.core.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.ui.CoreUiStrings

/**
 * Shared date/time picker row with two read-only OutlinedTextFields.
 * Tapping either field triggers the respective picker callback.
 *
 * @param dateString Formatted date string (e.g., "23/03/2026")
 * @param timeString Formatted time string (e.g., "14:30")
 * @param eventTimeChanged Whether the event time differs from the original
 * @param onDateClick Callback to open date picker
 * @param onTimeClick Callback to open time picker
 * @param modifier Modifier for the root Row
 */
@Composable
fun DateTimeSection(
    dateString: String,
    timeString: String,
    eventTimeChanged: Boolean,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Each field is wrapped in a clickable Box that carries the semantics, and the field itself has
    // its semantics cleared.
    //
    // The fields are enabled = false on purpose: that is what makes them ignore the tap so the
    // clickable can open the picker, and the colours override every disabled* colour back to the
    // enabled ones so it still looks live. The cost was that Compose puts the outer clickable and
    // BasicTextField's own `if (!enabled) disabled()` on the SAME LayoutNode, which collapses into
    // one semantics node holding both an OnClick and Disabled. A screen reader therefore announced
    // "Date, 23/09/2026, disabled" and suppressed the activate hint, on a control that works - so a
    // blind user was told the time of a treatment could not be corrected.
    val dateDescription = stringResource(InterfacesStrings.confirmation_line, stringResource(CoreUiStrings.date), dateString)
    val timeDescription = stringResource(InterfacesStrings.confirmation_line, stringResource(CoreUiStrings.time), timeString)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable { onDateClick() }
                .semantics { contentDescription = dateDescription }
        ) {
            OutlinedTextField(
                value = dateString,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(CoreUiStrings.date)) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = null,
                        tint = if (eventTimeChanged) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { },
                singleLine = true
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clickable { onTimeClick() }
                .semantics { contentDescription = timeDescription }
        ) {
            OutlinedTextField(
                value = timeString,
                onValueChange = {},
                readOnly = true,
                enabled = false,
                label = { Text(stringResource(CoreUiStrings.time)) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = if (eventTimeChanged) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { },
                singleLine = true
            )
        }
    }
}
