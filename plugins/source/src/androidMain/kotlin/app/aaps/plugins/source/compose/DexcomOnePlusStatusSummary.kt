package app.aaps.plugins.source.compose

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.interfaces.source.CgmSensorLifecycle
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.source.SourceStrings

/**
 * The card for the sensor that feeds the loop.
 *
 * Accented so it is never confused with the pre-soak card. [extra] is for the actions below the
 * facts - the "correct insertion date" action of the status screen.
 */
@Composable
fun DexcomOnePlusProductionCard(
    phaseLabel: String,
    phaseState: CgmUiState,
    sessionUp: Boolean,
    message: String,
    lifecycle: CgmSensorLifecycle?,
    newestGlucose: GV?,
    formatGlucose: (Double) -> String,
    formatTime: (Long) -> String,
    formatAge: (Long) -> String,
    extra: @Composable ColumnScope.() -> Unit = {},
) {
    CgmCard(accent = true) {
        CgmCardHeader(stringResource(SourceStrings.dexcom_oneplus_production_heading)) {
            CgmStateChip(state = phaseState, label = phaseLabel)
        }
        // Only this driver's own readings are shown here: the newest value in the database can come
        // from another source, and labelling someone else's reading as this sensor's would be a lie.
        val ownReading = newestGlucose?.takeIf { it.sourceSensor == SourceSensor.DEXCOM_ONEPLUS_NATIVE }
        if (ownReading != null) {
            Text(
                text = stringResource(
                    SourceStrings.dexcom_oneplus_staging_last_reading,
                    formatGlucose(ownReading.value),
                    formatTime(ownReading.timestamp),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        } else {
            Text(
                text = stringResource(SourceStrings.dexcom_oneplus_production_no_reading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CgmKeyValueRow(
            label = stringResource(SourceStrings.cgm_session_label),
            value = stringResource(
                if (sessionUp) SourceStrings.dexcom_oneplus_session_up else SourceStrings.dexcom_oneplus_session_down,
            ),
        )
        lifecycle?.ageMs?.let { age ->
            Text(
                text = stringResource(SourceStrings.dexcom_oneplus_sensor_age, formatAge(age)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        extra()
    }
}
