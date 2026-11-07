package app.aaps.implementation.profile

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * One-shot flag used to suppress the central "Basal profile in pump updated" ([app.aaps.core.interfaces.notifications.NotificationId.PROFILE_SET_OK])
 * notification for a profile write that AAPS itself triggers internally and shouldn't announce — currently a
 * Scene applying or reverting its ProfileSwitch (issue #4959).
 *
 * Why a flag and not pure data: a scene END sends [app.aaps.core.interfaces.rx.events.EventProfileChangeRequested]
 * (which now carries `silent`), but a scene START only inserts a ProfileSwitch row and reaches the pump write via
 * `persistenceLayer.observeChanges(PS)` — that generic DB observer has no event to tag. So the scene marks the next
 * write silent here right before inserting its PS, and the observe-changes branch of the profile-change collector
 * consumes it. Serialized profile processing (collectResilient) makes the window between mark and consume tiny; a
 * lost race only mis-shows/mis-hides one informational notification — never a dosing or safety effect.
 */
/*
 * `@SingleIn(AppScope::class)`, and that scope is load-bearing. `SceneExecutor` sets the flag and
 * `CommandQueueImplementation` reads it; two instances mean the mark is never the one the queue reads,
 * the flag does nothing, and a scene profile switch shows the notification it exists to suppress.
 * There must be exactly one of these.
 */
// kotlin.concurrent.atomics rather than java.util.concurrent: same semantics, and it exists off the JVM.
@OptIn(ExperimentalAtomicApi::class)
@SingleIn(AppScope::class)
@Inject
class ProfileSwitchSilentGate() {

    private val silentNext = AtomicBoolean(false)

    // Profile switch id -> until when a write of it is silent. Guarded by [lock].
    private val lock = AapsLock()
    private val silentSwitches = mutableMapOf<Long, Long>()

    /** Mark the next profile write (the one this caller is about to trigger) as silent. */
    fun markNextSilent() {
        silentNext.store(true)
    }

    /** Read and clear the flag. Returns true exactly once after a [markNextSilent]. */
    fun consumeSilent(): Boolean = silentNext.exchange(false)

    /**
     * A write of the profile switch [psId] is silent for [SILENT_FOR] from [now], whichever change event leads to
     * it. The flag above rides on one event only. While the pump is busy, the events queue up, and the write that
     * follows can be made for an unrelated one, a Nightscout id arriving for example: on a Medtronic a scene that
     * ended during a 38 s write was announced so. The queue asks [isSilent] only after the pump write, and the scene
     * marks the switch right after writing it. For the event that the scene's own write causes, the flag above still
     * answers, so a pump that writes at once is covered too.
     */
    fun markSilent(psId: Long, now: Long) {
        lock.withLock {
            silentSwitches.entries.removeAll { it.value < now }
            silentSwitches[psId] = now + SILENT_FOR
        }
    }

    /** Whether a write of [psId] at [now] was marked silent with [markSilent]. */
    fun isSilent(psId: Long, now: Long): Boolean = lock.withLock {
        silentSwitches.entries.removeAll { it.value < now }
        silentSwitches.containsKey(psId)
    }

    companion object {

        /** As long as the queue waits for one profile write, so a mark cannot silence a later, unrelated write. */
        const val SILENT_FOR = 10L * 60L * 1000L
    }
}
