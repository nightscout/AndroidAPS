package app.aaps.core.ui.compose.pump

/**
 * Counts who wants the screen kept on, for a platform where one app-wide switch does it.
 *
 * On iOS that switch is `UIApplication.idleTimerDisabled`, and two places want it: the "Keep screen on"
 * setting and a pump wizard. If each set and cleared it on its own, leaving a wizard would turn the
 * setting off, or turning the setting off would let the phone lock in the middle of an activation. With
 * a count, the screen stays on while anybody still holds it.
 *
 * [apply] gets true when the first hold comes and false when the last one goes.
 */
class ScreenAwakeHolds(private val apply: (keepOn: Boolean) -> Unit) {

    private var holds = 0

    fun hold() {
        holds++
        if (holds == 1) apply(true)
    }

    /** A release without a hold is ignored, so the count can never go below zero. */
    fun release() {
        if (holds == 0) return
        holds--
        if (holds == 0) apply(false)
    }
}
