package app.aaps.plugins.source.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.source.DexcomOnePlusWatchReport
import app.aaps.plugins.source.SourceStrings

/**
 * Direct-to-watch on the Status screen: what the watch last said. Drawn only while the feature is on
 * (its switch is in the settings) and the Wear plugin is on.
 *
 * @param available false while no production sensor can be handed over (none started, or this phone
 *   is not paired with it). The row then says the watch waits for a sensor, instead of "Off", which
 *   read as if the feature were off.
 */
@Composable
fun DexcomOnePlusWatchDirectCard(
    available: Boolean,
    report: DexcomOnePlusWatchReport?,
) {
    CgmCard {
        CgmCardHeader(stringResource(SourceStrings.dexcom_oneplus_watch_direct))
        // A plain line, not a label and a value: the card title already says what this is about,
        // and the longer states ("Pairing: accept the request on the watch") broke badly in a row.
        Text(
            text = stringResource(
                if (available) watchStateText(report?.status?.state) else SourceStrings.dexcom_oneplus_watch_state_waiting_sensor
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (available && report?.status?.batteryRestricted == true) {
            Text(
                text = stringResource(SourceStrings.dexcom_oneplus_watch_direct_battery),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The phrase for what the watch reports. Null: the watch has said nothing yet. */
internal fun watchStateText(state: EventData.CgmWatchState?): TextRef = when (state) {
    null                                    -> SourceStrings.dexcom_oneplus_watch_state_unknown
    EventData.CgmWatchState.OFF              -> SourceStrings.dexcom_oneplus_watch_state_off
    EventData.CgmWatchState.NOT_SUPPORTED    -> SourceStrings.dexcom_oneplus_watch_state_not_supported
    EventData.CgmWatchState.NEEDS_PERMISSION -> SourceStrings.dexcom_oneplus_watch_state_permission
    EventData.CgmWatchState.BLUETOOTH_OFF    -> SourceStrings.dexcom_oneplus_watch_state_bluetooth_off
    EventData.CgmWatchState.PAIRING          -> SourceStrings.dexcom_oneplus_watch_state_pairing
    EventData.CgmWatchState.WAITING          -> SourceStrings.dexcom_oneplus_watch_state_waiting
    EventData.CgmWatchState.CONNECTED        -> SourceStrings.dexcom_oneplus_watch_state_connected
    EventData.CgmWatchState.REFUSED          -> SourceStrings.dexcom_oneplus_watch_state_refused
}
