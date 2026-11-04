package app.aaps.core.interfaces.alerts

import kotlinx.coroutines.flow.StateFlow

/**
 * The "time to eat" timer that the bolus wizard and the carbs dialog start when the carbs come later.
 *
 * It is owned by the master, because that is the phone near the person who eats. A client only shows a copy,
 * received with the running configuration, and sends its Stop and +5 min to the master.
 *
 * There is one timer at a time. Starting a new one replaces the old one.
 */
interface EatReminder {

    /** The running timer, or null. On a client this is the copy of the master's timer. */
    val state: StateFlow<EatReminderState?>

    /**
     * Master: rings in [durationMs] from now and replaces a running timer. Nothing changes when the platform
     * cannot set the alarm (the user is told), so the screen never shows a countdown that will not ring.
     */
    fun start(durationMs: Long)

    /**
     * Master: ends the timer. Before it rang this cancels the alarm, after it rang this also silences it - only its
     * own alarm, never another alarm (#5408). Every Stop and Silence comes here: the bar, the alarm screen, the
     * notifications, a client and the watch, so they all do the same.
     */
    fun stop()

    /**
     * Master: +5 min, "remind me in [EXTEND_MINUTES] minutes", the same from every place that offers it. A running
     * timer ends [EXTEND_MINUTES] later; after it rang the new end is counted from now, and its alarm is silenced.
     * The whole wait grows with the new end. With no timer - it was ended, for example with Silence on its alarm -
     * a new one of [EXTEND_MINUTES] starts.
     *
     * @return false when the alarm could not be set. Then nothing changed: a ringing alarm keeps ringing, and the
     *   caller must not report success (the user is told the alarm could not be set).
     */
    fun extend(): Boolean

    /** Master: the platform calls this when the timer's alarm rings, just before it shows the alarm. */
    fun onRang()

    /** Master, once at app start: sets the alarm again from the saved timer. A reboot or a force stop removes it. */
    fun restore()

    /** Client: takes the master's timer, received with the running configuration. Null ends it. */
    fun applyRemote(remote: EatReminderState?)

    companion object {

        /** What +5 min adds. */
        const val EXTEND_MINUTES = 5
    }
}

/**
 * A running "time to eat" timer.
 *
 * @param startedAt when the timer was started
 * @param endsAt when it rings; later after each +5 min
 * @param durationMs the whole wait, from [startedAt] until it rings; longer after each +5 min
 * @param alarmRang master only: its alarm really rang ([EatReminder.onRang]). Not sent to clients.
 */
data class EatReminderState(
    val startedAt: Long,
    val endsAt: Long,
    val durationMs: Long,
    val alarmRang: Boolean = false
) {

    /** True from the end time on. What the screen shows; [alarmRang] is what the master acts on. */
    fun hasRung(now: Long): Boolean = now >= endsAt

    fun remainingMs(now: Long): Long = (endsAt - now).coerceAtLeast(0L)
}

/** What a client may ask the master to do with its timer. */
enum class EatReminderAction {

    STOP,
    EXTEND
}
