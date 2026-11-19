package app.aaps.core.interfaces.pump.alerts

import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.flow.StateFlow

/**
 * How bad a pump alert is. The driver picks it; the core picks the sound, the card level and whether the alarm screen
 * opens. A driver never passes a sound or a notification level. See `_docs/PUMP_DESIGN.md`, section 2.
 */
enum class PumpAlertSeverity {

    /** Insulin delivery has stopped or is not safe. Rings and opens the alarm screen until the user acts. */
    CRITICAL,

    /** The user must act soon: low reservoir, low battery, the pump or patch expires. A card and one sound. */
    WARNING,

    /** For information only. A card, no sound. */
    INFO
}

/**
 * How an alert ends. The one real difference between a pump's alert and the app's own, and the reason both can use
 * this one path. See `_docs/PUMP_DESIGN.md`, section 8.
 */
enum class PumpAlertLifetime {

    /**
     * The raiser owns it: it lasts until the pump drops it or its action clears it. Removing its card - a swipe,
     * "mute all alarms", the sheet closing - does not end it; it counts as a silence and the card comes back without
     * sound, because the condition is still there. Every pump alert is one of these.
     */
    CONDITION,

    /**
     * The user owns it: answering it ends it. A swipe, Mute, or Silence-and-close resolves it and it does not come
     * back. A bolus error is one of these - nobody will ever resolve it from the pump side - and so is the "time to
     * eat" timer. [PumpAlerts.silence] resolves it rather than quieting it; [PumpAlerts.snooze] still delays it, which
     * is what "Mute 5 min" is.
     */
    EVENT
}

/** A step on the pump that the user can start from the alert, for example "clear the alarm on the patch". */
class PumpAlertAction(
    val label: TextRef,
    /** Runs through the command queue. Returns true when the step was done (or started, for a driver that ends the alert itself). */
    val run: suspend () -> Boolean
)

class PumpAlert(
    /** The driver that raised it, for example "carelevo". */
    val source: String,
    /** Stable id inside [source]. Raising the same key again updates the alert; it does not ring again. */
    val key: String,
    val severity: PumpAlertSeverity,
    /** Resources, not resolved text, so the core resolves them when it shows them. A pump value goes in as an argument. */
    val title: TextRef,
    val text: TextRef? = null,
    /**
     * When the pump raised it, from the pump or the driver's own store - never the time of the call. A driver that
     * raises it again after a restart passes the same time, so a silence the user gave it still holds. A new
     * occurrence of the same condition has a new time and rings at once.
     */
    val raisedAt: Long,
    val action: PumpAlertAction? = null,
    /** Default [PumpAlertLifetime.CONDITION], because a pump driver always raises one. */
    val lifetime: PumpAlertLifetime = PumpAlertLifetime.CONDITION,
    /**
     * Shown on the card and sent to the watch. A pump alert leaves it null and the card is titled by its severity, as
     * every other notification is; the app's own alarms pass the title their caller gave, which the watch shows.
     */
    val notificationTitle: String? = null,
    /**
     * Whether this alarm can be delayed: the "Mute 5 min" button on the alarm screen, and Mute on the watch.
     *
     * False for an alarm whose own action is already the delay - the "time to eat" timer, where "+5 min" moves the
     * timer rather than ringing the same reminder again, and two buttons five minutes apart would only confuse.
     */
    val canMute: Boolean = true,
    /**
     * Whether the watch is told this alarm is ringing.
     *
     * False only for an alarm the watch rings by itself, which is the "time to eat" timer (`EatReminderWatch`).
     * Telling it as well would show the phone's alarm next to the watch's own for one reminder.
     */
    val tellWatch: Boolean = true,
    /**
     * Which sound a CRITICAL alert rings with, or null for the one its severity picks.
     *
     * **A pump driver leaves this null** - it says how bad the alert is and the core picks the sound, which is the
     * rule in `_docs/PUMP_DESIGN.md`. It is here for the app's own alarms, which have always chosen between a bolus
     * error, the eat reminder and the plain alarm, and would otherwise all sound the same.
     */
    val sound: AlarmSound? = null
)

/**
 * The one way a pump driver tells the user about an alert of its pump. The core shows it, rings, silences, writes the
 * announcement and tells the watch, the same for every driver and every platform.
 */
interface PumpAlerts {

    /** Every alert raised and not resolved yet, in the order they were raised. */
    val active: StateFlow<List<PumpAlert>>

    /**
     * The alert whose card is ringing now, or null.
     *
     * For the surfaces where the user cannot pick one - the watch's Silence and Mute, the Mute action on the
     * notification - which have to act on whatever is making the noise. It is the newest active alert that is
     * CRITICAL, has a card and is not silenced, which is how the notification list picks the card that owns the
     * sound. A sounding card that is not an alert, which a driver still posting its own alarm can leave, is not seen
     * here; `UiInteraction.stopAlarm` falls back to silencing everything in that case.
     */
    val sounding: PumpAlert?

    /** The pump reports this alert. */
    fun raise(alert: PumpAlert)

    /** The pump says it is gone, or the user's action cleared it. Stops the sound, removes the card. */
    fun resolve(source: String, key: String)

    /** The pump is removed, discarded or stopped: every alert of [source] ends. */
    fun resolveAll(source: String)

    /** Called by the alarm screen and the card, never by a driver. The alert stays; its sound stops for [SILENCE_MINUTES]. */
    fun silence(source: String, key: String)

    /** As [silence], for [minutes]. */
    fun snooze(source: String, key: String, minutes: Int)

    /** Called by the alarm screen: run the alert's action, as its button on the card does. */
    fun act(source: String, key: String)

    companion object {

        /** How long a silence lasts. When it ends and the alert is still active, it rings again (decided 2026-11-19). */
        const val SILENCE_MINUTES = 30
    }
}

/**
 * What a platform does for pump alerts beyond the notification list, which already owns the card and the sound.
 */
interface PumpAlertPlatform {

    /** A CRITICAL alert rings: bring up the alarm screen for it, also when the app is in the background. */
    fun showAlarmScreen(alert: PumpAlert)

    /** A WARNING alert: show it as a system notification with one sound. */
    fun chime(alert: PumpAlert)

    /**
     * The alert is silenced until [until] (epoch ms). The core rings it again itself when the time comes, but only
     * while its process runs: a platform that cannot run a timer in the background (iOS) schedules the ring here.
     */
    fun snoozed(alert: PumpAlert, until: Long)

    /** The alert ended, or rings again now: take back whatever [chime] or [snoozed] left for it. */
    fun withdrawn(source: String, key: String)
}
