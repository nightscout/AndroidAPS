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
 * Says it is missing rather than drawing nothing: an empty box looks exactly like a chart that failed
 * to render.
 *
 * Desktop *could* draw this - Compose Desktop has the same Canvas and `TextMeasurer` the shared code
 * would use, so unlike the Apple actual nothing about the platform is in the way. What is missing is
 * verification: the Android chart positions its axis labels from `Paint.FontMetrics`, and redrawing
 * that with `TextMeasurer` moves the labels unless the baseline arithmetic is redone and compared
 * against the real chart side by side. That comparison needs someone who can look at both.
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
