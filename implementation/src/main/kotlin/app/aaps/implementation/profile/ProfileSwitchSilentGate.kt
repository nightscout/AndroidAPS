package app.aaps.implementation.profile

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

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
@Singleton
class ProfileSwitchSilentGate @Inject constructor() {

    private val silentNext = AtomicBoolean(false)

    /** Mark the next profile write (the one this caller is about to trigger) as silent. */
    fun markNextSilent() {
        silentNext.set(true)
    }

    /** Read and clear the flag. Returns true exactly once after a [markNextSilent]. */
    fun consumeSilent(): Boolean = silentNext.getAndSet(false)
}
