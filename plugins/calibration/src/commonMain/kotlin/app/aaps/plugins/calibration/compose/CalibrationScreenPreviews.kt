package app.aaps.plugins.calibration.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.plugins.calibration.CalibrationFit
import app.aaps.plugins.calibration.FitMode

@Preview(showBackground = true, name = "Calibration applied")
@Composable
internal fun CalibrationScreenContentPreview() {
    val now = 1_700_000_000_000L
    val hour = 3_600_000L
    val entries = listOf(
        CAL(id = 1, timestamp = now - 5 * hour, fingerstickMgdl = 120.0, sensorMgdlAtPairing = 110.0),
        CAL(id = 2, timestamp = now - 3 * hour, fingerstickMgdl = 150.0, sensorMgdlAtPairing = 145.0),
        CAL(id = 3, timestamp = now - 1 * hour, fingerstickMgdl = 95.0, sensorMgdlAtPairing = 90.0)
    )
    MaterialTheme {
        CalibrationScreenContent(
            state = CalibrationUiState(
                sessionStart = now - 6 * hour,
                warmUpEndsAt = now - 4 * hour,
                isInWarmUp = false,
                entries = entries,
                fit = CalibrationFit(slope = 1.05, offset = 2.0, mode = FitMode.Full),
                now = now,
                selectedEntryId = 3,
                glucoseUnit = GlucoseUnit.MGDL
            ),
            formatDateTime = { "01 Jan 12:00" },
            formatTime = { "14:00" },
            onMarkSensorChange = {},
            onAddCalibration = {},
            onSelectEntry = {},
            onDeleteEntry = {}
        )
    }
}

@Preview(showBackground = true, name = "No session")
@Composable
internal fun CalibrationScreenContentNoSessionPreview() {
    MaterialTheme {
        CalibrationScreenContent(
            state = CalibrationUiState(),
            formatDateTime = { "" },
            formatTime = { "" },
            onMarkSensorChange = {},
            onAddCalibration = {},
            onSelectEntry = {},
            onDeleteEntry = {}
        )
    }
}
