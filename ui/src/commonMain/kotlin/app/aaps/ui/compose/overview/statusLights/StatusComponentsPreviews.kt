package app.aaps.ui.compose.overview.statusLights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.icons.IcCannulaChange
import app.aaps.core.ui.compose.icons.IcCgmInsert
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
