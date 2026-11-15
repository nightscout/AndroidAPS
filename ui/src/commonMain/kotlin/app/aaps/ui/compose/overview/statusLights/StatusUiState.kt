package app.aaps.ui.compose.overview.statusLights

import androidx.compose.runtime.Immutable

/**
 * UI state for Overview status section (sensor/insulin/cannula/battery)
 */
@Immutable
data class StatusUiState(
    val sensorStatus: StatusItem? = null,
    val insulinStatus: StatusItem? = null,
    val cannulaStatus: StatusItem? = null,
    val batteryStatus: StatusItem? = null,
    val showFill: Boolean = false,
    val showPumpBatteryChange: Boolean = false,
    val isPatchPump: Boolean = false
)