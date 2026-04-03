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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.R
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcPumpCartridge
import app.aaps.core.ui.compose.statusLevelToColor

/**
 * Status rows content — sensor/insulin/cannula/battery with optional action buttons.
 * Does not include a card wrapper — caller provides the container.
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
    val addLabel = stringResource(R.string.add)
    val fillLabel = stringResource(R.string.prime_fill)

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
            contentDescription = item.label,
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
            progressColor = ageColor
        )

        // Level with vertical progress (if available and allowed in expanded view)
        if (item.level != null && item.expandedLevel) {
            StatusValueWithProgress(
                value = item.level,
                valueColor = levelColor,
                progress = item.levelPercent,
                progressColor = levelColor
            )
        }

        // Action button
        if (actionLabel != null && onActionClick != null) {
            FilledTonalButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
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
    progressColor: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
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

@Preview(showBackground = true)
@Composable
private fun StatusSectionContentPreview() {
    MaterialTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusSectionContent(
                sensorStatus = StatusItem(
                    label = "Sensor",
                    age = "5d 12h",
                    ageStatus = StatusLevel.NORMAL,
                    agePercent = 0.55f,
                    level = "Signal OK",
                    levelStatus = StatusLevel.NORMAL,
                    levelPercent = 0.2f,
                    icon = IcCgmInsert
                ),
                insulinStatus = StatusItem(
                    label = "Insulin",
                    age = "2d 3h",
                    ageStatus = StatusLevel.WARNING,
                    agePercent = 0.75f,
                    level = "86 U",
                    levelStatus = StatusLevel.NORMAL,
                    levelPercent = -1f,
                    icon = IcPumpCartridge
                ),
                cannulaStatus = StatusItem(
                    label = "Cannula",
                    age = "1d 18h",
                    ageStatus = StatusLevel.NORMAL,
                    agePercent = 0.6f,
                    icon = IcCannulaChange
                ),
                batteryStatus = StatusItem(
                    label = "Battery",
                    age = "14d",
                    ageStatus = StatusLevel.CRITICAL,
                    agePercent = 0.95f,
                    level = "12%",
                    levelStatus = StatusLevel.CRITICAL,
                    levelPercent = 0.88f,
                    icon = IcPumpBattery
                ),
                onSensorInsertClick = {},
                onFillClick = {},
                onInsulinChangeClick = {},
                onBatteryChangeClick = {}
            )
        }
    }
}
