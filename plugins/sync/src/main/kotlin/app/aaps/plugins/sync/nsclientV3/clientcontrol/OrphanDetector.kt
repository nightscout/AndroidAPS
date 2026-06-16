package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.configuration.NSRunningConfiguration
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsclientV3.clientcontrol.OrphanDetector.Companion.POST_PAIRING_GRACE_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client-side detector for "this device's pairing has been revoked or master was wiped."
 *
 * Master republishes the active-clientId roster as `runningConfig.authorizedClients.clientIds`
 * on every change to the authorized list (and on every settings/aaps publish). When a settings
 * doc lands on the client, [onSettingsDoc] checks whether this device is still in the roster
 * and posts or dismisses the orphan notification accordingly.
 *
 * Three branches:
 * - **Block absent**: older master that doesn't publish the roster. No signal possible — skip.
 * - **Block present + our clientId in list**: authorized. Dismiss any prior orphan notification.
 * - **Block present + our clientId missing**: orphan candidate. Apply race-window guard before firing.
 *
 * **Race-window guard:** master debounces 5s after pairing-state changes; meanwhile a settings
 * doc published just before the new pair might be in-flight. Compare doc `srvModified` to local
 * `NsClientControlPairedAt`. If the doc predates pairing by less than [POST_PAIRING_GRACE_MS],
 * skip — master hasn't republished yet. The next doc with our clientId arrives and dismisses
 * any noise that snuck through.
 *
 * **Coverage gap by design:** an uninstalled / dead master never republishes, so this detector
 * cannot signal that case. A liveness mechanism would be needed; deferred per Phase-2 scope.
 */
@Singleton
class OrphanDetector @Inject constructor(
    private val pairingRepository: ClientPairingRepository,
    private val preferences: Preferences,
    private val notificationManager: NotificationManager,
    private val rh: ResourceHelper,
    private val config: Config,
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val appScope: CoroutineScope
) {

    private val _authorized = MutableStateFlow(true)

    init {
        // A pairing change (pair / re-pair / unpair writes NsClientControlClientId) clears the orphan
        // verdict: a freshly (re-)paired client is optimistically authorized again until a roster doc
        // proves otherwise. Without this, a once-orphaned Singleton stays authorized=false across a
        // re-pair within the same process and keeps masterReachable locked until the next roster doc.
        if (config.AAPSCLIENT) appScope.launch {
            // Reset only on a (re-)pair (non-empty clientId). Unpair writes an EMPTY clientId — don't
            // optimistically mark an unpaired device authorized (it's only masked today by the paired term).
            preferences.observe(StringNonKey.NsClientControlClientId).drop(1).collect { if (it.isNotEmpty()) _authorized.value = true }
        }
    }

    /**
     * `true` while the master still lists this device in its `authorizedClients` roster (or no
     * orphan signal is possible yet — older master, freshly paired within the race window, pristine).
     * Flips to `false` only once a settings doc is seen that excludes this clientId past the race
     * guard. Folded into [app.aaps.core.interfaces.sync.NsClient.masterReachable] so a revoked
     * client's edits are gated, not just notified. Optimistic default keeps first-ever pairing usable.
     *
     * @see app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin.masterReachable for where this term is
     *   combined (ws && fresh && paired && authorized) into the overall master-reachable gate.
     */
    val authorized: StateFlow<Boolean> = _authorized.asStateFlow()

    /**
     * Inspect a freshly-applied settings/aaps doc. Safe to call from any client load path
     * (catch-up worker, WS push) — no-op on master.
     *
     * [docSrvModified] is the doc's `srvModified` in ms; 0 if unknown (skip the race guard).
     */
    fun onSettingsDoc(configuration: NSRunningConfiguration, docSrvModified: Long) {
        if (!config.AAPSCLIENT) return
        val roster = configuration.authorizedClients ?: return
        val pairing = pairingRepository.currentPairing() ?: return
        if (pairing.clientId in roster.clientIds) {
            _authorized.value = true
            notificationManager.dismiss(NotificationId.NSCLIENT_PAIRING_ORPHAN)
            return
        }
        val pairedAt = preferences.get(LongNonKey.NsClientControlPairedAt)
        // Intent-first: defer the orphan verdict while this doc was modified before pairing + the grace
        // window. Equivalent to the original pairedAt > docSrvModified - POST_PAIRING_GRACE_MS, but reads
        // as "doc predates pairing + grace".
        if (pairedAt > 0L && docSrvModified > 0L && docSrvModified < pairedAt + POST_PAIRING_GRACE_MS) {
            aapsLogger.debug(
                LTag.NSCLIENT,
                "ClientControl: missing from authorizedClients but doc predates pairing (pairedAt=$pairedAt srvModified=$docSrvModified); deferring orphan signal"
            )
            return
        }
        _authorized.value = false
        aapsLogger.warn(LTag.NSCLIENT, "ClientControl: clientId=${pairing.clientId} not in master's authorizedClients — orphan")
        notificationManager.post(NotificationId.NSCLIENT_PAIRING_ORPHAN, rh.gs(R.string.clientcontrol_orphan_notification))
    }

    companion object {

        /**
         * Slack between pair and the master's roster republish landing on this client. Sized well
         * above the master's 5s debounce on purpose: it must also absorb HTTP/WS propagation delay,
         * publish retries, and clock skew between the two devices — any of which can push the roster
         * doc's srvModified later than a naive 5s estimate would allow.
         */
        const val POST_PAIRING_GRACE_MS = 60_000L
    }
}
