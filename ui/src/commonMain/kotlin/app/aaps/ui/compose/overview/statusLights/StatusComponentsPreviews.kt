package app.aaps.ui.compose.overview.statusLights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcGenericCgm
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcPumpCartridge

@Preview(showBackground = true)
@Composable
internal fun StatusSectionContentPreview() {
    MaterialTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusSectionContent(
                sensorStatus = StatusItem(
                    label = "Sensor",
                    age = "5d12h",
                    ageStatus = StatusLevel.NORMAL,
                    agePercent = 0.55f,
                    level = "Signal OK",
                    levelStatus = StatusLevel.NORMAL,
                    levelPercent = 0.2f,
                    icon = IcCgmInsert
                ),
                insulinStatus = StatusItem(
                    label = "Insulin",
                    age = "2d3h",
                    ageStatus = StatusLevel.WARNING,
                    agePercent = 0.75f,
                    level = "86 U",
                    levelStatus = StatusLevel.NORMAL,
                    levelPercent = -1f,
                    icon = IcPumpCartridge
                ),
                cannulaStatus = StatusItem(
                    label = "Cannula",
                    age = "1d18h",
                    ageStatus = StatusLevel.NORMAL,
                    agePercent = 0.6f,
                    icon = IcCannulaChange
                ),
                batteryStatus = StatusItem(
                    label = "Battery",
                    age = "14d3h",
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

/**
 * The two CGM rows: a sensor that still warms up, and a second sensor that is ready to be used by the
 * loop. Only sources that report them get these rows.
 */
@Preview(showBackground = true)
@Composable
internal fun StatusSectionContentCgmRowsPreview() {
    MaterialTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusSectionContent(
                sensorStatus = StatusItem(
                    label = "Sensor",
                    age = "9d4h",
                    ageStatus = StatusLevel.WARNING,
                    agePercent = 0.9f,
                    icon = IcCgmInsert,
                    compactLevel = false
                ),
                insulinStatus = null,
                cannulaStatus = null,
                batteryStatus = null,
                warmUpStatus = StatusItem(
                    label = "Warm-up",
                    age = "23 min",
                    agePercent = 0.24f,
                    icon = Icons.Filled.HourglassTop,
                    compactLevel = false
                ),
                secondSensorStatus = StatusItem(
                    label = "New sensor",
                    age = "Ready to switch",
                    ageStatus = StatusLevel.NORMAL,
                    level = "Readings: 72",
                    icon = IcGenericCgm,
                    compactLevel = false
                ),
                onSensorInsertClick = {},
                onPromoteSecondSensorClick = {}
            )
        }
    }
}
