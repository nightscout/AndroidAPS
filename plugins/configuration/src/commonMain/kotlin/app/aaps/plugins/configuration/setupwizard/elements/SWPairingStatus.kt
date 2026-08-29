package app.aaps.plugins.configuration.setupwizard.elements

import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.configuration.ConfigurationStrings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.Inject

/**
 * Live pairing-status line for the SetupWizard master/client steps. Reads reactive [NsClient] flows so
 * it reflects pair/unpair changes without leaving the wizard:
 * - Master ([Config.AAPSCLIENT] false): "Paired clients: N" from [NsClient.pairedClientCountFlow].
 * - Client: paired/not-paired from [NsClient.masterOrPairedClientFlow].
 */
class SWPairingStatus @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    rxBus: RxBus,
    preferences: Preferences,
    passwordCheck: PasswordCheck,
    private val config: Config,
    private val nsClient: NsClient
) : SWItem(aapsLogger, rh, rxBus, preferences, passwordCheck) {

    @Composable
    override fun Compose() {
        if (config.AAPSCLIENT) {
            val paired by nsClient.masterOrPairedClientFlow.collectAsState()
            Text(
                text = stringResource(
                    if (paired) ConfigurationStrings.setupwizard_pairing_status_client_paired
                    else ConfigurationStrings.setupwizard_pairing_status_client_not_paired
                )
            )
        } else {
            val count by nsClient.pairedClientCountFlow.collectAsState()
            Text(
                text = if (count > 0) stringResource(ConfigurationStrings.setupwizard_pairing_status_master, count)
                else stringResource(ConfigurationStrings.setupwizard_pairing_status_master_none)
            )
        }
    }
}
