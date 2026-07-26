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

    /** Generation counter — incremented on each [start]; guards the delayed clear in [completeAndAutoClear]
     *  and the stall flag in [markStalled]. */
    private val generation = AtomicLong(0)

    /** Snapshot of the current generation, captured by the client watchdog when it arms (see [markStalled]). */
    val currentGeneration: Long get() = generation.get()

    /**
     * Called by CommandQueue before bolus delivery starts.
     *
     * Returns the generation token assigned to this bolus. Callers should keep it and pass it to the
     * generation-scoped [clear] at the end of their command so a finished/cancelled bolus can never
     * wipe the progress state of a NEWER bolus that has already started (see [clear]).
     */
    fun start(insulin: Double, isSMB: Boolean, isPriming: Boolean = false): Long {
        val gen = generation.incrementAndGet()
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
        return gen
    }

    /**
     * Called by pump drivers to report delivery progress.
     * Purely informational — does not control dialog lifecycle.
     */
    fun updateProgress(percent: Int, status: String, delivered: PumpInsulin = PumpInsulin(0.0)) {
        // A fresh frame proves liveness → clear any prior stall flag (lets the client dialog recover).
        _state.update { it?.copy(percent = percent, status = status, wearStatus = status, delivered = delivered, stalled = false) }
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
            _state.update { it?.copy(percent = percent, status = status, wearStatus = wearStatus, delivered = delivered, stalled = false) }
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
            _state.update { it?.copy(percent = percent, status = status, wearStatus = wearStatus, delivered = delivered, stalled = false) }
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
     * Unconditional completion — stamp percent=100 (UI success state) then auto-clear after [AUTO_CLEAR_DELAY_MS].
     *
     * Use ONLY where frame ORDERING already guarantees no newer bolus can be displaced — the client progress
     * mirror, whose relayed frames are timestamp-ordered. A per-command MASTER bolus MUST use the generation-scoped
     * [completeAndAutoClear] overload instead (same rationale as [clear] vs [clear]).
     */
    fun completeAndAutoClear() = completeAndAutoClear(generation.get())

    /**
     * Generation-scoped completion for a single master bolus command (mirror of [clear]). Guards BOTH the immediate
     * percent=100 stamp AND the delayed null-clear against [expectedGeneration] (this command's [start] token): if a
     * NEWER bolus has begun (generation bumped past the token), a finishing/older bolus can neither stamp completion
     * onto NOR clear the newer bolus's progress state. The stamp also clears any stall flag (a terminal Complete with
     * no intervening Active frame must not keep the "connection lost" UI). Check-and-mutate is atomic via [update].
     */
    fun completeAndAutoClear(expectedGeneration: Long) {
        _state.update { current ->
            if (current != null && generation.get() == expectedGeneration) current.copy(percent = 100, stalled = false) else current
        }
        appScope.launch {
            delay(AUTO_CLEAR_DELAY_MS)
            if (generation.get() == expectedGeneration) _state.value = null
        }
    }

    /**
     * Called by CommandQueue to dismiss the UI.
     * Sets state to null — no bolus in progress.
     *
     * Unconditional: use only on genuine abort-everything paths (connection timeout, cancelAllBoluses,
     * remote Cleared frame). A per-bolus command MUST use the generation-scoped [clear] overload instead.
     */
    fun clear() {
        _state.value = null
    }

    /**
     * Generation-scoped clear for a single bolus command at the end of execute()/on cancel().
     *
     * Only nulls the state when [expectedGeneration] (the token returned by this command's [start]) is
     * still the current generation. If a newer bolus has begun in the meantime, this is a no-op so the
     * finishing/cancelled command cannot wipe the newer bolus's progress state.
     *
     * Why this matters: [start] is called at ENQUEUE time, so an SMB queued just before a manual bolus
     * (while the pump is reconnecting) gets generation N and the manual bolus generation N+1 — both before
     * either executes. The SMB then executes first and, without this guard, its terminal unconditional
     * [clear] would null the state the still-pending manual bolus depends on. Every subsequent
     * [updateProgress] frame would then be a no-op (state == null), so the driver's deliverTreatment reads
     * delivered = 0 and raises a false BOLUS_DELIVERY_FAILED even though the pump delivered in full.
     *
     * [start] (enqueue thread, under the queue's lock) and this clear (queue-worker thread) can run
     * concurrently, so the check-and-null is done atomically via [MutableStateFlow.update]: the generation
     * is re-read inside the CAS loop, so a newer bolus's [start] that bumps the generation turns this into
     * a no-op instead of wiping the state it just installed.
     */
    fun clear(expectedGeneration: Long) {
        _state.update { current -> if (generation.get() == expectedGeneration) null else current }
    }

    /**
     * Called by the client/follower remote-progress watchdog when no progress frame has arrived from
     * the master for too long (lost connection / relay outage) before a terminal frame. Flags the
     * existing state so the client dialog can surface a "connection lost" message + manual dismiss.
     *
     * [expectedGeneration] is the generation captured when the watchdog armed (see [currentGeneration]).
     * The watchdog runs on a pool thread and is only cooperatively cancellable, so a cancel() racing in
     * right after its delay() resumes cannot stop this call. The guards make a stray fire a no-op:
     *  - generation mismatch → a newer bolus has begun; don't flag it,
     *  - percent >= 100 → the bolus already completed; don't flip the success view to "connection lost",
     *  - null → already dismissed/cleared.
     * Never invoked on the master — a local bolus is driven straight by [updateProgress].
     */
    fun markStalled(expectedGeneration: Long) {
        _state.update {
            if (it != null && it.percent < 100 && generation.get() == expectedGeneration) it.copy(stalled = true)
            else it
        }
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
    val stopDeliveryEnabled: Boolean,
    /**
     * Client/follower only: set by the remote progress watchdog when progress frames from the master
     * stop arriving (lost connection / relay outage) before a terminal frame. Drives the dialog's
     * "connection lost" state + manual dismiss. Always false for a local (master) bolus.
     */
    val stalled: Boolean = false
)
