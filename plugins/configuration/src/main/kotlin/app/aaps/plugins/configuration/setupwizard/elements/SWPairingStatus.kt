package app.aaps.plugins.configuration.setupwizard.elements

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.PasswordCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.configuration.R
import javax.inject.Inject

/**
 * Live pairing-status line for the SetupWizard master/client steps. Reads reactive [NsClient] flows so
 * it reflects pair/unpair changes without leaving the wizard:
 * - Master ([Config.AAPSCLIENT] false): "Paired clients: N" from [NsClient.pairedClientCountFlow].
 * - Client: paired/not-paired from [NsClient.masterOrPairedClientFlow].
 */
class SWPairingStatus @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
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
                    if (paired) R.string.setupwizard_pairing_status_client_paired
                    else R.string.setupwizard_pairing_status_client_not_paired
                )
            )
        } else {
            val count by nsClient.pairedClientCountFlow.collectAsState()
            Text(
                text = if (count > 0) stringResource(R.string.setupwizard_pairing_status_master, count)
                else stringResource(R.string.setupwizard_pairing_status_master_none)
            )
        }
    }
}
