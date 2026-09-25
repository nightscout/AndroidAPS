package app.aaps.ui.compose.overview.statusLights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.statusLevelToColor
import app.aaps.core.ui.compose.statusLevelToDescription
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull
import app.aaps.ui.UiStrings

/**
 * Status rows content — sensor/insulin/cannula/battery with optional action buttons.
 * Does not include a card wrapper — caller provides the container.
 *
 * @see StatusSectionContentPreview
 */
@Composable
internal fun StatusSectionContent(
    sensorStatus: StatusItem?,
    insulinStatus: StatusItem?,
    cannulaStatus: StatusItem?,
    batteryStatus: StatusItem?,
    onSensorInsertClick: (() -> Unit)? = null,
    onFillClick: (() -> Unit)? = null,
    onInsulinChangeClick: (() -> Unit)? = null,
    onBatteryChangeClick: (() -> Unit)? = null
) {
    val addLabel = stringResource(CoreUiStrings.add)
    val fillLabel = stringResource(CoreUiStrings.prime_fill)

    cannulaStatus?.let {
        StatusRow(item = it, actionLabel = fillLabel, onActionClick = onFillClick)
    }
    if (cannulaStatus != null && insulinStatus != null) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    insulinStatus?.let {
        StatusRow(item = it, actionLabel = fillLabel, onActionClick = onInsulinChangeClick)
    }
    if (insulinStatus != null && sensorStatus != null) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    sensorStatus?.let {
        StatusRow(item = it, actionLabel = addLabel, onActionClick = onSensorInsertClick)
    }
    if (sensorStatus != null && batteryStatus != null) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    batteryStatus?.let {
        StatusRow(item = it, actionLabel = addLabel, onActionClick = onBatteryChangeClick)
    }
}

@Composable
private fun StatusRow(
    item: StatusItem,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    val ageColor = statusLevelToColor(item.ageStatus)
    val levelColor = statusLevelToColor(item.levelStatus)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon
        Icon(
            imageVector = item.icon,
            // Decorative: the Text right beside it already says the same word, so naming the icon
            // too had a screen reader announce "Cannula" twice before reaching the row's value.
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Label
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        // Age with vertical progress
        StatusValueWithProgress(
            value = item.age,
            valueColor = ageColor,
            progress = item.agePercent,
            progressColor = ageColor,
            levelDescription = stringResourceOrNull(statusLevelToDescription(item.ageStatus))
        )

        // Level with vertical progress (if available and allowed in expanded view)
        if (item.level != null && item.expandedLevel) {
            StatusValueWithProgress(
                value = item.level,
                valueColor = levelColor,
                progress = item.levelPercent,
                progressColor = levelColor,
                levelDescription = stringResourceOrNull(statusLevelToDescription(item.levelStatus))
            )
        }

        // Action button. Name it with the row it belongs to, because the same verb appears on more
        // than one row - "Prime/Fill" sits on both Cannula and Insulin, and "Add" on both Sensor and
        // Battery - while the row's own name is a sibling Text outside the button. A screen reader
        // therefore heard "Prime/Fill, button" twice with no way to tell which one primes the pump.
        //
        // Only the row name goes here, not the verb: a Button merges its children and a
        // contentDescription is inserted BEFORE them rather than replacing them, so this reads
        // "Insulin, Prime/Fill" and the visible Text still supplies the verb.
        if (actionLabel != null && onActionClick != null) {
            FilledTonalButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(32.dp)
                    .semantics { contentDescription = item.label }
            ) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun StatusValueWithProgress(
    value: String,
    valueColor: Color,
    progress: Float,
    progressColor: Color,
    /**
     * Spoken severity, or null when there is nothing to flag. The colour of [valueColor] used to be
     * the only sign that a cannula was past its change day or a reservoir nearly empty.
     */
    levelDescription: String? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        // Merged so the value and its severity are one item rather than two stops, and the progress
        // bar stops being announced separately - it shows the same thing the value already says.
        // The description carries only the severity: on a merging node it is added before the
        // children rather than replacing them, so this reads "Critical, 3 days".
        modifier = if (levelDescription != null) {
            Modifier.semantics(mergeDescendants = true) { contentDescription = levelDescription }
        } else {
            Modifier.semantics(mergeDescendants = true) { }
        }
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = valueColor
        )
        if (progress >= 0) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .width(56.dp)
                    .height(6.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round
            )
        }
    }
}
