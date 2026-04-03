package app.aaps.core.interfaces.pump

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
class BolusProgressData @Inject constructor() {

    private val _state = MutableStateFlow<BolusProgressState?>(null)
    val state: StateFlow<BolusProgressState?> = _state.asStateFlow()

    /** Generation counter — incremented on each [start], used to guard delayed [clearIfSameGeneration]. */
    private val generation = AtomicLong(0)

    /** Returns the current generation. Use with [clearIfSameGeneration] for delayed cleanup. */
    val currentGeneration: Long get() = generation.get()

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
            delivered = 0.0,
            stopPressed = false,
            stopDeliveryEnabled = true
        )
    }

    /**
     * Called by pump drivers to report delivery progress.
     * Purely informational — does not control dialog lifecycle.
     */
    fun updateProgress(percent: Int, status: String, delivered: Double = 0.0) {
        _state.update { it?.copy(percent = percent, status = status, delivered = delivered) }
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
     * Sets percent to 100. UI should show completion state.
     * Call [clear] after a delay to dismiss.
     */
    fun complete() {
        _state.update { it?.copy(percent = 100) }
    }

    /**
     * Called by CommandQueue to dismiss the UI.
     * Sets state to null — no bolus in progress.
     */
    fun clear() {
        _state.value = null
    }

    /**
     * Clears state only if the generation hasn't changed since [expectedGeneration] was captured.
     * Used for delayed cleanup after bolus completion to avoid clearing a subsequent bolus's state.
     */
    fun clearIfSameGeneration(expectedGeneration: Long) {
        if (generation.get() == expectedGeneration) {
            _state.value = null
        }
    }
}

data class BolusProgressState(
    val insulin: Double,
    val isSMB: Boolean,
    val isPriming: Boolean,
    val percent: Int,
    val status: String,
    val delivered: Double,
    val stopPressed: Boolean,
    val stopDeliveryEnabled: Boolean
)
