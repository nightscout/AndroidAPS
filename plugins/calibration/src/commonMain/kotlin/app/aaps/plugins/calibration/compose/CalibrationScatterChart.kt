package app.aaps.plugins.calibration.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.plugins.calibration.CalibrationFit

/**
 * Fingerstick against sensor, with the identity line and the fitted regression.
 *
 * Platform specific for one reason only: the axis labels are drawn straight onto the canvas, and the
 * Android implementation does that with `android.graphics.Paint` and `nativeCanvas.drawText`, using
 * `FontMetrics` to work out the baselines. The multiplatform way is `TextMeasurer` plus
 * `DrawScope.drawText`, which computes those baselines differently - a change to label POSITIONING,
 * which needs the chart in front of you to check rather than a compile.
 *
 * So the Android chart is unchanged and the other platforms draw nothing for now. Everything around
 * it - the screen, the view model, the plugin - is shared, which is what this split was for.
 */
@Composable
internal expect fun CalibrationScatterChart(
    entries: List<CAL>,
    fit: CalibrationFit?,
    selectedEntryId: Long?,
    now: Long,
    glucoseUnit: GlucoseUnit,
    modifier: Modifier = Modifier
)
