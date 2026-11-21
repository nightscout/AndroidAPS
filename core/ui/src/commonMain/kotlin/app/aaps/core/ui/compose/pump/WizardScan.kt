package app.aaps.core.ui.compose.pump

import app.aaps.core.interfaces.pump.ble.AdapterState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Why a Bluetooth scan cannot find anything now. [BleScanStep] says it in words. */
enum class ScanProblem { BLUETOOTH_OFF, NOT_ALLOWED }

/** Why a scan on this adapter cannot find anything, or null when it can (or when that is not known). */
val AdapterState.scanProblem: ScanProblem?
    get() = when (this) {
        AdapterState.OFF, AdapterState.UNSUPPORTED -> ScanProblem.BLUETOOTH_OFF
        AdapterState.UNAUTHORIZED                  -> ScanProblem.NOT_ALLOWED
        AdapterState.ON, AdapterState.UNKNOWN      -> null
    }

/**
 * The scan of a pairing wizard, with an end. It runs for [duration] and then stops, so [BleScanStep] can say what it
 * found, or why it found nothing, and offer "Scan again". A scan with no end showed "Scanning…" for ever, also with
 * Bluetooth off. A scan that cannot run (Bluetooth off, not allowed) ends at once.
 *
 * Give the step [state]'s `scanning` and `problem`, [start] as `onStartScan` and `onScanAgain`, and [stop] as
 * `onStopScan`.
 *
 * @param onStart starts the platform scan
 * @param onStop stops it: at the end, on [stop], and when the step closes
 */
class WizardScan(
    private val scope: CoroutineScope,
    private val adapterState: StateFlow<AdapterState>,
    private val onStart: () -> Unit,
    private val onStop: () -> Unit,
    private val duration: Duration = DEFAULT_DURATION
) {

    data class State(val scanning: Boolean = false, val problem: ScanProblem? = null)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var timer: Job? = null

    fun start() {
        timer?.cancel()
        adapterState.value.scanProblem?.let { problem ->
            _state.value = State(scanning = false, problem = problem)
            return
        }
        _state.value = State(scanning = true)
        onStart()
        timer = scope.launch {
            delay(duration)
            onStop()
            _state.value = State(scanning = false, problem = adapterState.value.scanProblem)
        }
    }

    fun stop() {
        timer?.cancel()
        timer = null
        onStop()
        _state.update { it.copy(scanning = false) }
    }

    companion object {

        /** Long enough for a pump that has to be woken up or put on its base first; "Scan again" starts another. */
        val DEFAULT_DURATION = 30.seconds
    }
}
