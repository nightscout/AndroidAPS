package app.aaps.core.interfaces.pump.alerts

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
    val action: PumpAlertAction? = null
)

/**
 * The one way a pump driver tells the user about an alert of its pump. The core shows it, rings, silences, writes the
 * announcement and tells the watch, the same for every driver and every platform.
 */
interface PumpAlerts {

    /** Every alert raised and not resolved yet, in the order they were raised. */
    val active: StateFlow<List<PumpAlert>>

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
