package app.aaps.plugins.calibration.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.plugins.calibration.CalibrationFit

/**
 * Draws nothing yet.
 *
 * A placeholder rather than a port: the Android chart positions its axis labels from
 * `Paint.FontMetrics`, and redrawing that with `TextMeasurer` moves the labels unless the baseline
 * arithmetic is redone and checked against the real chart. Left for a change that can look at it.
 *
 * The screen around it already lays out and behaves correctly, so this is an empty area rather than
 * a broken one.
 */
@Composable
internal actual fun CalibrationScatterChart(
    entries: List<CAL>,
    fit: CalibrationFit?,
    selectedEntryId: Long?,
    now: Long,
    glucoseUnit: GlucoseUnit,
    modifier: Modifier
) {
    // Intentionally empty - see the expect declaration.
}
