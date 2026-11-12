package app.aaps.plugins.source.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.source.DexcomOnePlusWatchReport
import app.aaps.plugins.source.SourceStrings

/**
 * Direct-to-watch on the Status screen: the switch, and what the watch last said.
 *
 * Shown only on a phone that has a watch link. A host without one does not show it, see
 * `DexcomOnePlusWatchTransport`.
 *
 * @param available false while this phone does not hold the phone slot of the production sensor.
 *   The switch can still be turned on; nothing is handed over until the phone is paired.
 */
@Composable
fun DexcomOnePlusWatchDirectCard(
    enabled: Boolean,
    available: Boolean,
    report: DexcomOnePlusWatchReport?,
    onEnabledChange: (Boolean) -> Unit,
) {
    CgmCard {
        CgmCardHeader(stringResource(SourceStrings.dexcom_oneplus_watch_direct))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(SourceStrings.dexcom_oneplus_watch_direct_summary),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled && !available) {
            Text(
                text = stringResource(SourceStrings.dexcom_oneplus_watch_direct_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (enabled) {
            CgmKeyValueRow(
                label = stringResource(SourceStrings.dexcom_oneplus_watch_direct_watch_label),
                value = stringResource(watchStateText(report?.status?.state)),
            )
            if (report?.status?.batteryRestricted == true) {
                Text(
                    text = stringResource(SourceStrings.dexcom_oneplus_watch_direct_battery),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
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
