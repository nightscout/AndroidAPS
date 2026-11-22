package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.alerts.SharedSilence
import app.aaps.core.interfaces.alerts.SharedSilenceLink
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientControlMessage
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * "Silence everywhere" over the client-control link (`_docs/ALARMS.md`, step 2f).
 *
 * A client sends its silence to the master as a signed [ClientControlMessage.AlertSilence]; the master keeps it with
 * its own in its list of shared silences and its hot status doc takes them to every client. The phone that silences
 * has already done it on itself, so the send is fire-and-forget: when it does not reach the master, only the other
 * phones go on ringing.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class SharedSilenceLinkImpl(
    private val config: Config,
    private val preferences: Preferences,
    private val pairingRepository: ClientPairingRepository,
    private val authorizedRepository: AuthorizedClientsRepository,
    // Providers: the publisher and the Nightscout client reach much of the app, and the alert core holds this link
    private val publisher: () -> ClientControlPublisher,
    private val nsClient: () -> NsClient,
    private val nsClientRepository: NSClientRepository,
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val appScope: CoroutineScope
) : SharedSilenceLink {

    /** Master: whether a client is paired and active now, kept from the roster (asked for on the alarm screen). */
    @Volatile private var hasActiveClient = false

    init {
        if (!config.AAPSCLIENT) appScope.launch {
            authorizedRepository.observe().collect { clients -> hasActiveClient = clients.any { it.state == ClientState.Active } }
        }
    }

    override fun available(): Boolean {
        if (!preferences.hardenedMode) return false
        return if (config.AAPSCLIENT) pairingRepository.isPaired() && nsClient().clientRole.value.allows(ClientRole.MealAssistant)
        else hasActiveClient
    }

    override fun send(silence: SharedSilence) {
        if (!config.AAPSCLIENT) {
            aapsLogger.error(LTag.NSCLIENT, "Shared silence sent on the master - ignored, the master keeps its own list")
            return
        }
        appScope.launch {
            val result = publisher().publish(
                ClientControlMessage.AlertSilence(
                    source = silence.source, key = silence.key, raisedAt = silence.raisedAt, until = silence.until, sentAt = silence.sentAt
                )
            )
            nsClientRepository.addLog("► CLIENTCTL", "alert_silence ${silence.key}: ${result::class.simpleName}")
        }
    }
}
