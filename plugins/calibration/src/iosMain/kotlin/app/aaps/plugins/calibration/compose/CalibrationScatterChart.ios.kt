package app.aaps.plugins.calibration.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import app.aaps.core.data.model.CAL
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.plugins.calibration.CalibrationFit

/**
 * Says it is missing rather than drawing nothing.
 *
 * An empty box looks exactly like a chart that failed to render, so it names itself instead. The
 * Android chart positions its axis labels from `Paint.FontMetrics`; redrawing that with
 * `TextMeasurer` moves the labels unless the baseline arithmetic is redone and checked against the
 * real chart, so the port waits for a change that can look at it.
 *
 * The text is deliberately not translated: it is a developer placeholder that should never reach a
 * user, and adding it to `strings.xml` would push it to every translator.
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
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Calibration chart is not implemented on this platform yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
