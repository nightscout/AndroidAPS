package app.aaps.cgm.dexcomg7.watch

import app.aaps.cgm.dexcomg7.data.G7CommLog
import app.aaps.cgm.dexcomg7.data.G7State
import app.aaps.cgm.dexcomg7.data.G7StateStore
import app.aaps.cgm.dexcomg7.protocol.G7DisplayType
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class G7BooleanNonKey(
    override val key: String,
    override val defaultValue: Boolean
) : BooleanNonPreferenceKey {

    /** Direct-to-watch: the watch also reads the sensor itself, in the sensor's watch slot. */
    WatchDirect("dexcom_g7_watch_direct", false)
}

/** What the watch last said about its own sensor connection, and when. */
data class G7WatchReport(val status: EventData.G7WatchStatus, val receivedAt: Long)

/**
 * Direct-to-watch, phone side. When it is on, the watch gets the pairing code and the sensor's address
 * and connects to the sensor itself, in the sensor's watch slot. The sensor serves the phone and the
 * watch at the same time, each with its own key, so the phone's link is not touched.
 *
 * Messages to the watch can be lost (watch out of range, watch app restarting). So the phone sends the
 * wanted setting again whenever the watch asks for data, and whenever the watch reports a state that does
 * not match it.
 */
@SingleIn(AppScope::class)
@Inject
class G7WatchHandover(
    private val preferences: Preferences,
    private val store: G7StateStore,
    private val rxBus: RxBus,
    private val commLog: G7CommLog
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var job: Job? = null
    private var lastResendAt = 0L

    private val _enabled = MutableStateFlow(preferences.get(G7BooleanNonKey.WatchDirect))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _report = MutableStateFlow<G7WatchReport?>(null)
    val report: StateFlow<G7WatchReport?> = _report.asStateFlow()

    /** Only the phone that holds the phone slot can hand the sensor to the watch. */
    fun isAvailable(state: G7State): Boolean = state.isPaired && state.pairingCode != null && state.displayType == G7DisplayType.PHONE

    fun start() {
        job?.cancel()
        job = scope.launch {
            launch { rxBus.toFlow(EventData.G7WatchStatus::class).collect { onStatus(it) } }
            // The watch app (re)started, or a watch connected: it may have missed the last message.
            launch { rxBus.toFlow(EventData.ActionResendData::class).collect { send("watch asked for data") } }
            // A new sensor, or the same one paired again: the watch must follow.
            launch {
                store.state.map { Triple(it.address, it.pairingCode, it.displayType) }.distinctUntilChanged().drop(1)
                    .collect { if (_enabled.value) send("sensor changed") }
            }
            send("start")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun setEnabled(on: Boolean) {
        preferences.put(G7BooleanNonKey.WatchDirect, on)
        _enabled.value = on
        commLog.add(if (on) "Direct to watch turned on" else "Direct to watch turned off")
        scope.launch { send(if (on) "turned on" else "turned off") }
    }

    private fun wanted(): EventData.G7WatchDirect {
        val state = store.value
        return if (_enabled.value && isAvailable(state))
            EventData.G7WatchDirect(active = true, pairingCode = state.pairingCode, address = state.address, serial = state.serial, sensorName = state.sensorName)
        else EventData.G7WatchDirect(active = false)
    }

    private fun send(reason: String) {
        val message = wanted()
        // Never logs the pairing code.
        commLog.add("Watch: sending direct-to-watch ${if (message.active) "on (${message.sensorName ?: message.address})" else "off"} ($reason)")
        rxBus.send(EventMobileToWear(message))
    }

    private fun onStatus(status: EventData.G7WatchStatus) {
        val previous = _report.value?.status
        _report.value = G7WatchReport(status, System.currentTimeMillis())
        if (previous?.state != status.state || previous.detail != status.detail) commLog.add("Watch reports ${status.state}" + (status.detail?.let { ": $it" } ?: ""))
        if (!matches(wanted(), status)) {
            val now = System.currentTimeMillis()
            if (now - lastResendAt < RESEND_INTERVAL_MS) return
            lastResendAt = now
            send("watch is out of step")
        }
    }

    companion object {

        /** At most one corrective message a minute, so a watch that cannot follow is not flooded. */
        const val RESEND_INTERVAL_MS = 60_000L

        /** True when the watch already does what the phone wants. */
        fun matches(wanted: EventData.G7WatchDirect, status: EventData.G7WatchStatus): Boolean = when {
            // A watch that cannot run it has nothing to change.
            status.state == EventData.G7WatchState.NOT_SUPPORTED -> true
            wanted.active                                         -> status.state != EventData.G7WatchState.OFF && status.address == wanted.address
            else                                                  -> status.state == EventData.G7WatchState.OFF
        }
    }
}
