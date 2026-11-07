package app.aaps.plugins.source

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import app.aaps.plugins.source.keys.DexcomOnePlusBooleanKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/**
 * How a phone sends direct-to-watch messages to its watch.
 *
 * On Android that is the Wear OS data layer. A host without a watch link returns no transport, and
 * the feature is then not offered at all.
 */
fun interface DexcomOnePlusWatchTransport {

    fun send(message: EventData.CgmWatchDirect)
}

/** What the watch last said about its own sensor connection, and when. */
data class DexcomOnePlusWatchReport(val status: EventData.CgmWatchStatus, val receivedAtMs: Long)

/**
 * Direct-to-watch, phone side. When it is on, the watch gets the pairing code and the address of the
 * PRODUCTION sensor and connects to it itself, in the sensor's watch slot. The phone's own link is
 * not touched, and the phone stays the master:
 *
 * - only the production sensor is handed over, never the pre-soak one;
 * - after a promotion the new production sensor is sent again ([resend]);
 * - turning the switch off, or a production sensor that is gone or changed, sends an inactive
 *   message, so the watch lets the old sensor go;
 * - it is offered only while this phone holds the phone slot of the sensor (it has a key for it).
 *
 * Messages to the watch can be lost (out of range, watch app restarting), so the wanted state is
 * sent again whenever the watch asks for data, and whenever the watch reports a state that does not
 * match it, at most once a minute.
 *
 * The watch's readings never come back here. Nothing in this class receives a glucose value.
 *
 * Behaviour follows `G7WatchHandover` in nightscout/AndroidAPS pull request 5397.
 *
 * @param transport null on a phone without a watch link, which makes every call here a no-op.
 * @param productionSession reads the production slot's stored sensor, live, on every call.
 */
class DexcomOnePlusWatchHandover(
    private val preferences: Preferences,
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger,
    private val transport: DexcomOnePlusWatchTransport?,
    private val productionSession: () -> OnePlusStoredSession?,
    private val nowMs: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private var job: Job? = null

    @Volatile private var lastSent: EventData.CgmWatchDirect? = null

    @Volatile private var lastCorrectionAtMs = 0L

    private val _report = MutableStateFlow<DexcomOnePlusWatchReport?>(null)

    /** The watch's last report, for the Status screen. Null until the watch has said anything. */
    val report: StateFlow<DexcomOnePlusWatchReport?> = _report.asStateFlow()

    /** False on a phone with no watch link. The feature is then not shown at all. */
    val isSupported: Boolean get() = transport != null

    val isEnabled: Boolean get() = preferences.get(DexcomOnePlusBooleanKey.WatchDirect)

    /** True when the production sensor can be handed to the watch right now. */
    fun isAvailable(): Boolean = DexcomOnePlusWatchDirectRules.isAvailable(DexcomOnePlusWatchDirectRules.sensorOf(productionSession()))

    fun start(scope: CoroutineScope) {
        if (transport == null) return
        job?.cancel()
        job = scope.launch {
            launch { rxBus.toFlow(EventData.CgmWatchStatus::class).collect { onStatus(it) } }
            // The watch app (re)started, or a watch connected: it may have missed the last message.
            launch { rxBus.toFlow(EventData.ActionResendData::class).collect { resend("watch asked for data") } }
            // The switch, from the Status screen or the settings
            launch { preferences.observe(DexcomOnePlusBooleanKey.WatchDirect).drop(1).collect { resend(if (it) "turned on" else "turned off") } }
            resend("start")
        }
    }

    /**
     * The plugin stops. The watch lets the sensor go: with the plugin off the phone no longer
     * follows the sensor, and the watch must not keep a sensor the phone has stopped caring about.
     */
    fun stop() {
        job?.cancel()
        job = null
        if (transport != null && lastSent?.active == true) send(EventData.CgmWatchDirect(active = false), "plugin stopped")
    }

    fun setEnabled(on: Boolean) {
        // The preference observer sends the message.
        preferences.put(DexcomOnePlusBooleanKey.WatchDirect, on)
    }

    /** Sends the wanted state only when it changed: a new sensor, a new key, a sensor gone. */
    fun refresh(reason: String) {
        if (transport == null) return
        val wanted = wanted()
        if (DexcomOnePlusWatchDirectRules.changed(lastSent, wanted)) send(wanted, reason)
    }

    /** Sends the wanted state, also when it did not change (a promotion, a watch that asked). */
    fun resend(reason: String) {
        if (transport == null) return
        send(wanted(), reason)
    }

    private fun wanted(): EventData.CgmWatchDirect =
        DexcomOnePlusWatchDirectRules.wanted(isEnabled, DexcomOnePlusWatchDirectRules.sensorOf(productionSession()))

    private fun send(message: EventData.CgmWatchDirect, reason: String) {
        val link = transport ?: return
        lastSent = message
        // The message's own toString never prints the pairing code.
        aapsLogger.info(LTag.BGSOURCE, "DEXCOM_ONEPLUS_WATCH: send ${if (message.active) "on" else "off"} ($reason) $message")
        link.send(message)
    }

    private fun onStatus(status: EventData.CgmWatchStatus) {
        val now = nowMs()
        val previous = _report.value?.status
        _report.value = DexcomOnePlusWatchReport(status, now)
        if (previous?.state != status.state) aapsLogger.info(LTag.BGSOURCE, "DEXCOM_ONEPLUS_WATCH: watch reports ${status.state}")
        if (DexcomOnePlusWatchDirectRules.shouldCorrect(wanted(), status, lastCorrectionAtMs, now)) {
            lastCorrectionAtMs = now
            resend("watch is out of step")
        }
    }
}
