package app.aaps.ui.compose.overview.statusLights

import androidx.compose.runtime.Immutable

/**
 * UI state for Overview status section (sensor/insulin/cannula/battery).
 *
 * [warmUpStatus] and [secondSensorStatus] only exist while a CGM source reports a warm-up, or a
 * second sensor that warms up next to the one in use. Both stay null for every other source, so the
 * section looks exactly as before.
 *
 * @param canPromoteSecondSensor the second sensor is ready and the loop may switch to it, so its row
 *   offers the switch. False while it still warms up or settles: the source would refuse anyway.
 * @param promotionMessage result of the last switch (done, done with a problem, or why it was
 *   refused). Shown once, then cleared by [StatusViewModel.clearPromotionMessage].
 */
@Immutable
data class StatusUiState(
    val sensorStatus: StatusItem? = null,
    val warmUpStatus: StatusItem? = null,
    val secondSensorStatus: StatusItem? = null,
    val canPromoteSecondSensor: Boolean = false,
    val promotionMessage: String? = null,
    val insulinStatus: StatusItem? = null,
    val cannulaStatus: StatusItem? = null,
    val batteryStatus: StatusItem? = null,
    val showFill: Boolean = false,
    val showPumpBatteryChange: Boolean = false,
    val isPatchPump: Boolean = false
)