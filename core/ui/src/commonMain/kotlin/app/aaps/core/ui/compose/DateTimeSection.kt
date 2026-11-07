package app.aaps.core.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.ui.CoreUiStrings

/**
 * Space an OutlinedTextField with a trailing icon takes next to its text: 16 dp start padding,
 * 4 dp end padding and the 48 dp icon area (Material3 TextField layout), plus 2 dp for rounding.
 */
private val fieldChromeWidth = 70.dp

/**
 * Shared date/time picker row with two read-only OutlinedTextFields.
 * Tapping either field triggers the respective picker callback.
 *
 * The two fields sit side by side. When the date or the time does not fit into half the width (a
 * big font, or a long date format), the fields are put one under the other, so the text is never cut.
 *
 * @param dateString Formatted date string (e.g., "23/03/2026")
 * @param timeString Formatted time string (e.g., "14:30")
 * @param eventTimeChanged Whether the event time differs from the original
 * @param onDateClick Callback to open date picker
 * @param onTimeClick Callback to open time picker
 * @param modifier Modifier for the root layout
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
    val dateDescription = stringResource(InterfacesStrings.confirmation_line, stringResource(CoreUiStrings.date), dateString)
    val timeDescription = stringResource(InterfacesStrings.confirmation_line, stringResource(CoreUiStrings.time), timeString)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // OutlinedTextField draws its value with LocalTextStyle, so measure with the same style.
        val textMeasurer = rememberTextMeasurer()
        val textStyle = LocalTextStyle.current
        val longestText = maxOf(
            textMeasurer.measure(dateString, textStyle).size.width,
            textMeasurer.measure(timeString, textStyle).size.width
        )
        val halfWidth = (maxWidth - AapsSpacing.large) / 2
        val sideBySide = with(LocalDensity.current) { longestText.toDp() } + fieldChromeWidth <= halfWidth

        val dateField = @Composable { fieldModifier: Modifier ->
            PickerField(
                value = dateString,
                label = stringResource(CoreUiStrings.date),
                icon = Icons.Filled.DateRange,
                description = dateDescription,
                eventTimeChanged = eventTimeChanged,
                onClick = onDateClick,
                modifier = fieldModifier
            )
        }
        val timeField = @Composable { fieldModifier: Modifier ->
            PickerField(
                value = timeString,
                label = stringResource(CoreUiStrings.time),
                icon = Icons.Outlined.Schedule,
                description = timeDescription,
                eventTimeChanged = eventTimeChanged,
                onClick = onTimeClick,
                modifier = fieldModifier
            )
        }

        if (sideBySide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large)
            ) {
                dateField(Modifier.weight(1f))
                timeField(Modifier.weight(1f))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.medium)
            ) {
                dateField(Modifier.fillMaxWidth())
                timeField(Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * One read-only field that opens a picker when tapped.
 *
 * The field is wrapped in a clickable Box that carries the semantics, and the field itself has its
 * semantics cleared.
 *
 * The field is enabled = false on purpose: that is what makes it ignore the tap so the clickable
 * can open the picker, and the colours override every disabled* colour back to the enabled ones so
 * it still looks live. The cost was that Compose puts the outer clickable and BasicTextField's own
 * `if (!enabled) disabled()` on the SAME LayoutNode, which collapses into one semantics node holding
 * both an OnClick and Disabled. A screen reader therefore announced "Date, 23/09/2026, disabled" and
 * suppressed the activate hint, on a control that works - so a blind user was told the time of a
 * treatment could not be corrected.
 */
@Composable
private fun PickerField(
    value: String,
    label: String,
    icon: ImageVector,
    description: String,
    eventTimeChanged: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clickable { onClick() }
            .semantics { contentDescription = description }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    imageVector = icon,
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
