package app.aaps.ui.widget

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Holds widget updates back while no display is on. Nobody can see a home screen widget then, and each
 * update composes every placed widget and hands it to the launcher (#5246). The skipped update is not
 * lost: it runs as soon as a display turns on, so "time ago" and the BG are right when the user looks.
 */
internal class ScreenOffGate {

    private val pending = AtomicBoolean(false)

    /** Returns true when the update must run now. Otherwise it is kept for [displayTurnedOn]. */
    fun request(displayOn: Boolean): Boolean {
        if (displayOn) {
            pending.set(false)
            return true
        }
        pending.set(true)
        return false
    }

    /** Returns true when an update was held back and must run now. */
    fun displayTurnedOn(): Boolean = pending.getAndSet(false)
}
