package app.aaps.pump.equil.events

import app.aaps.core.interfaces.rx.events.Event

/**
 * A pump alarm read from the pump history.
 *
 * @param sound false for an old alarm that is only read now (AAPS was off or out of range), so it
 * does not sound as if it had just happened
 */
data class EventEquilAlarm(var tips: String, val sound: Boolean = true) : Event()
