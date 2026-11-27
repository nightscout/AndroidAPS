package app.aaps.ui.compose.overview

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.compose.overview.statusLights.StatusItem

private val previewStatusItems = Triple(
    StatusItem(
        label = "Cannula",
        age = "16h",
        ageStatus = StatusLevel.NORMAL,
        level = null,
        icon = Icons.Default.Circle
    ),
    StatusItem(
        label = "Insulin",
        age = "16h",
        ageStatus = StatusLevel.NORMAL,
        level = "10 U",
        levelStatus = StatusLevel.NORMAL,
        icon = Icons.Default.Circle
    ),
    StatusItem(
        label = "Sensor",
        age = "3d5h",
        ageStatus = StatusLevel.WARNING,
        level = "82%",
        levelStatus = StatusLevel.NORMAL,
        icon = Icons.Default.Circle
    )
)

private val previewBatteryItem = StatusItem(
    label = "Battery",
    age = "2d7h",
    ageStatus = StatusLevel.NORMAL,
    level = "68%",
    levelStatus = StatusLevel.NORMAL,
    icon = Icons.Default.Circle
)

private val previewStatusLightsDef = PreferenceSubScreenDef(
    key = "preview",
    titleResId = 0
)

@Preview(showBackground = true, widthDp = 400)
@Composable
internal fun OverviewStatusSectionCollapsedPreview() {
    MaterialTheme {
        val (cannula, insulin, sensor) = previewStatusItems
        OverviewStatusSection(
            sensorStatus = sensor,
            insulinStatus = insulin,
            cannulaStatus = cannula,
            batteryStatus = previewBatteryItem,
            showFill = true,
            showPumpBatteryChange = true,
            onNavigate = {},
            statusLightsDef = previewStatusLightsDef,
            expanded = false,
            onExpandedChange = {}
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
internal fun OverviewStatusSectionExpandedPreview() {
    MaterialTheme {
        val (cannula, insulin, sensor) = previewStatusItems
        OverviewStatusSection(
            sensorStatus = sensor,
            insulinStatus = insulin,
            cannulaStatus = cannula,
            batteryStatus = previewBatteryItem,
            showFill = true,
            showPumpBatteryChange = true,
            onNavigate = {},
            statusLightsDef = previewStatusLightsDef,
            expanded = true,
            onExpandedChange = {}
        )
    }
}

/** Expanded card with a sensor that still warms up and a second sensor that is ready to switch to. */
@Preview(showBackground = true, widthDp = 400)
@Composable
internal fun OverviewStatusSectionCgmRowsPreview() {
    MaterialTheme {
        val (cannula, insulin, sensor) = previewStatusItems
        OverviewStatusSection(
            sensorStatus = sensor,
            insulinStatus = insulin,
            cannulaStatus = cannula,
            batteryStatus = previewBatteryItem,
            warmUpStatus = StatusItem(
                label = "Warm-up",
                age = "23 min",
                agePercent = 0.24f,
                icon = Icons.Default.Circle,
                compactLevel = false
            ),
            secondSensorStatus = StatusItem(
                label = "New sensor",
                age = "Ready to switch",
                ageStatus = StatusLevel.NORMAL,
                level = "Readings: 72",
                icon = Icons.Default.Circle,
                compactLevel = false
            ),
            canPromoteSecondSensor = true,
            showFill = true,
            showPumpBatteryChange = true,
            onNavigate = {},
            statusLightsDef = previewStatusLightsDef,
            expanded = true,
            onExpandedChange = {}
        )
    }
}
