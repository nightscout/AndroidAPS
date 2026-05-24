package app.aaps.core.interfaces.pump

import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.resources.ResourceHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Core-controlled bolus progress state.
 *
 * Lifecycle is managed by the command queue (start/complete/clear),
 * pump drivers only report progress via [updateProgress].
 */
@Singleton
class BolusProgressData @Inject constructor(
    val ch: ConcentrationHelper,
    val rh: ResourceHelper,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private val _state = MutableStateFlow<BolusProgressState?>(null)
    val state: StateFlow<BolusProgressState?> = _state.asStateFlow()

    /** Generation counter — incremented on each [start]; guards the delayed clear in [completeAndAutoClear]. */
    private val generation = AtomicLong(0)

    /**
     * Called by CommandQueue before bolus delivery starts.
     */
    fun start(insulin: Double, isSMB: Boolean, isPriming: Boolean = false) {
        generation.incrementAndGet()
        _state.value = BolusProgressState(
            insulin = insulin,
            isSMB = isSMB,
            isPriming = isPriming,
            percent = 0,
            status = "",
            wearStatus = "",
            delivered = PumpInsulin(0.0),
            stopPressed = false,
            stopDeliveryEnabled = true
        )
    }

    /**
     * Called by pump drivers to report delivery progress.
     * Purely informational — does not control dialog lifecycle.
     */
    fun updateProgress(percent: Int, status: String, delivered: PumpInsulin = PumpInsulin(0.0)) {
        _state.update { it?.copy(percent = percent, status = status, wearStatus = status, delivered = delivered) }
    }

    /**
     * Called by pump drivers to report delivery progress.
     * Purely informational — does not control dialog lifecycle.
     */
    fun updateProgress(percent: Int) {
        _state.value?.let { state ->
            val insulin = state.insulin
            val delivered = if (state.isPriming) PumpInsulin(insulin * percent / 100)
                            else PumpInsulin(insulin / ch.concentration * percent / 100)
            val status = if (percent < 100) ch.bolusProgressString(delivered, state.isPriming)
                         else rh.gs(app.aaps.core.interfaces.R.string.bolus_delivered_successfully, insulin)
            val wearStatus = if (percent < 100) ch.bolusProgressString(delivered, insulin, state.isPriming)
                             else rh.gs(app.aaps.core.interfaces.R.string.bolus_delivered_successfully, insulin)
            _state.update { it?.copy(percent = percent, status = status, wearStatus = wearStatus, delivered = delivered) }
        }
    }

    /**
     * Called by pump drivers to report delivery progress.
     * Purely informational — does not control dialog lifecycle.
     */
    fun updateProgress(delivered: PumpInsulin) {
        _state.value?.let { state ->
            val insulin = state.insulin
            val percent = (ch.fromPump(delivered, state.isPriming) / insulin * 100).toInt().coerceAtMost(100)
            val status = ch.bolusProgressString(delivered, state.isPriming)
            val wearStatus = ch.bolusProgressString(delivered, insulin, state.isPriming)
            _state.update { it?.copy(percent = percent, status = status, wearStatus = wearStatus, delivered = delivered) }
        }
    }

    /**
     * Called by pump drivers to enable/disable the stop button.
     */
    fun enableStopDelivery(enabled: Boolean) {
        _state.update { it?.copy(stopDeliveryEnabled = enabled) }
    }

    /**
     * Called when user presses the stop button.
     */
    fun stopPressed() {
        _state.update { it?.copy(stopPressed = true) }
    }

    /**
     * Check if user requested stop. Used by pump drivers.
     */
    val isStopPressed: Boolean get() = _state.value?.stopPressed == true

    /**
     * Called by CommandQueue when pump reports delivery complete.
     *
     * Sets percent to 100 so the UI shows the success state, then auto-clears after [delayMs]
     * — but only if no newer bolus has started in the meantime (guarded by the generation
     * counter). The clear runs on the application scope so it survives the queue worker
     * finishing the current command.
     */
    fun completeAndAutoClear(delayMs: Long = AUTO_CLEAR_DELAY_MS) {
        _state.update { it?.copy(percent = 100) }
        val expectedGeneration = generation.get()
        appScope.launch {
            delay(delayMs)
            if (generation.get() == expectedGeneration) {
                _state.value = null
            }
        }
    }

    /**
     * Called by CommandQueue to dismiss the UI.
     * Sets state to null — no bolus in progress.
     */
    fun clear() {
        _state.value = null
    }

    companion object {
        private const val AUTO_CLEAR_DELAY_MS = 5_000L
    }
}

data class BolusProgressState(
    val insulin: Double,
    val isSMB: Boolean,
    val isPriming: Boolean,
    val percent: Int,
    val status: String,
    val wearStatus: String,
    val delivered: PumpInsulin,
    val stopPressed: Boolean,
    val stopDeliveryEnabled: Boolean
)
