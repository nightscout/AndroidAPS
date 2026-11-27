package app.aaps.core.interfaces.alerts

import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.flow.StateFlow

/**
 * How bad an alert is. The raiser picks it; the core picks the sound, the card level and whether the alarm screen
 * opens. A raiser never passes a notification level, and a driver never a sound. See `_docs/PUMP_DESIGN.md`, section 2,
 * and `_docs/ALARMS.md`, section 6.
 */
enum class AlertSeverity {

    /**
     * Insulin delivery has stopped or is not safe, a CGM gives no readings, or the glucose is urgent. Rings and opens
     * the alarm screen until the user acts.
     */
    CRITICAL,

    /** The user must act soon: low reservoir, low battery, the pump, patch or sensor expires. A card and one sound. */
    WARNING,

    /** For information only. A card, no sound. */
    INFO
}

/**
 * How an alert ends. The one real difference between a pump's alert and the app's own, and the reason both can use
 * this one path. See `_docs/PUMP_DESIGN.md`, section 8.
 */
enum class AlertLifetime {

    /**
     * The raiser owns it: it lasts until the pump drops it or its action clears it. Removing its card - Dismiss, a
     * swipe, "mute all alarms" - does not end it; it counts as a silence. A protected kind's card comes back at once
     * without sound, because the condition is still there; any other kind's card stays away until the silence ends
     * and comes back with the ring (decided 2026-11-25). Every pump alert is one of these.
     */
    CONDITION,

    /**
     * The user owns it: answering it ends it. A swipe, Mute, or Silence-and-close resolves it and it does not come
     * back. A bolus error is one of these - nobody will ever resolve it from the pump side - and so is the "time to
     * eat" timer. [Alerts.silence] resolves it rather than quieting it; [Alerts.snooze] still delays it, which
     * is what "Mute 5 min" is.
     */
    EVENT
}

/**
 * What raised an alert, which decides how its announcement is written (`_docs/ALARMS.md`, section 6). The core writes
 * one announcement per alert, on the master only.
 */
enum class AlertOrigin {

    /** A pump driver: the announcement is filed under the active pump, with its type and serial. */
    PUMP,

    /** The app itself (`"aaps"`): the announcement is the app's own, with no pump identity. */
    APP,

    /** A CGM driver (`"eversense"`): the announcement is written as the app's own, with no pump identity. */
    CGM,

    /**
     * Nightscout: the alert came from a record that is already there. Nothing is written, or the announcement would
     * come back as a new one.
     */
    NIGHTSCOUT
}

/**
 * A kind of alert. On each phone the user chooses [AlertChoice] for its [group], or for the kind itself when it has no
 * group or is an exception (`_docs/ALARMS.md`, section 6). Each raiser has its own catalogue: [CoreAlert] for the app,
 * one for each driver.
 */
interface AlertKind : AlertSetting {

    /** Unique in the whole app and stable: an exception the user set for it is stored under it. Never change it. */
    override val id: String

    /** The group whose choice and sound it follows, or null for a kind that is its own row in the settings. */
    val group: AlertGroup? get() = null

    /** Where the settings show it: its group's section, else the app's. */
    val section: AlertSection get() = group?.section ?: AlertSection.APP

    /** Who raises alerts of this kind: "aaps", or a driver's id ("carelevo"). */
    val source: String

    /** What kind of raiser that is; it decides how the announcement is written. */
    val origin: AlertOrigin

    /** Its name in the alert settings. */
    override val title: TextRef

    /**
     * A safety kind: urgent low, or a kind with an event that affects dosing ([DosingEffect.affectsDosing]: insulin
     * delivery stopped or not safe, no BG or a BG that cannot be trusted). It always rings: the user cannot set it to
     * Silent or Off. A ringing one is still silenced for a while, as every alert. The same as its group's.
     */
    override val isProtected: Boolean

    /**
     * Whether the core writes the announcement of an alert of this kind (on the master, while "Create announcements
     * from errors" is on). False for a BG alarm (every phone raises its own), an alert from Nightscout (its record is
     * there already) and carbs required (the loop writes its own, under its own setting).
     */
    val announced: Boolean get() = true
}

/** What the user chose for a kind of alert, on this phone only. Stored by name: never rename one. */
enum class AlertChoice {

    /** The default: as its severity says. */
    RING,

    /**
     * As [RING], with the phone and the watch vibrating instead of making a sound (decided 2026-11-26): an alarm
     * vibrates until it is answered, a chime once. Only where the phone can vibrate ([AlertPlatform.canVibrate]); where
     * it cannot, it rings.
     */
    VIBRATE,

    /** A card only: no sound, no alarm screen, and the watch is not told. */
    SILENT,

    /** Not shown. It is still written to the log and the history. */
    OFF;

    /** It rings, by sound or by vibration: its alarm screen and chime come as usual. */
    val rings: Boolean get() = this == RING || this == VIBRATE
}

/** A step the user can start from the alert, for example "clear the alarm on the patch" or "+5 min". */
class AlertAction(
    val label: TextRef,
    /**
     * A pump driver runs it through the command queue, a CGM driver on its own thread. Returns true when the step was
     * done (or started, for a raiser that ends the alert itself).
     */
    val run: suspend () -> Boolean
)

class Alert(
    /** Which kind it is: the user's choice for that kind decides whether it rings, and it names the raiser. */
    val kind: AlertKind,
    /** Stable id inside [source]. Raising the same key again updates the alert; it does not ring again. */
    val key: String,
    val severity: AlertSeverity,
    /** Resources, not resolved text, so the core resolves them when it shows them. A pump value goes in as an argument. */
    val title: TextRef,
    val text: TextRef? = null,
    /**
     * When the pump raised it, from the pump or the driver's own store - never the time of the call. A driver that
     * raises it again after a restart passes the same time, so a silence the user gave it still holds. A new
     * occurrence of the same condition has a new time and rings at once.
     */
    val raisedAt: Long,
    val action: AlertAction? = null,
    /** Default [AlertLifetime.CONDITION], because a pump driver always raises one. */
    val lifetime: AlertLifetime = AlertLifetime.CONDITION,
    /**
     * The title of the system notification and of the watch's alarm. A pump alert leaves it null and is shown with
     * its [title]; the app's own alarms pass the title their caller gave, and then the card text is [text] alone.
     */
    val notificationTitle: String? = null,
    /**
     * Whether this alarm can be delayed: the "Mute 5 min" button on the alarm screen of an alarm that ends when it is
     * answered ([AlertLifetime.EVENT]; one that lasts has a button per silence length instead), and Mute on the watch.
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
    val sound: AlarmSound? = null,
    /**
     * How long a silence lasts where the user does not pick a length: a swipe, the watch and the card. The alarm screen
     * has a button per length instead. A BG alarm takes it from its kind (`BgAlert.silenceMinutes`: an urgent low short,
     * a high longer).
     */
    val silenceMinutes: Int = Alerts.SILENCE_MINUTES,
    /** Rings in Do Not Disturb and silent mode whatever the user's override setting says. Urgent low only. */
    val breakThroughDnd: Boolean = false
) {

    /** Who raised it: a driver ("carelevo", "eversense"), or "aaps" for the app's own alarms. */
    val source: String get() = kind.source

    /** What kind of raiser that is; it decides how the announcement is written. */
    val origin: AlertOrigin get() = kind.origin
}

/**
 * The one way to tell the user about something that needs an answer: a pump's alert, a CGM's, or the app's own. It
 * was named `PumpAlerts` while only pumps used it (renamed 2026-11-21). The core shows it, rings, silences, writes the
 * announcement and tells the watch, the same for every driver and every platform.
 */
interface Alerts {

    /**
     * Every alert raised and not resolved yet, in the order they were raised. Also one the user turned off
     * ([AlertChoice.OFF]): it is still there, only not shown, so its raiser resolves it as any other.
     */
    val active: StateFlow<List<Alert>>

    /**
     * The user's choices on this phone, by [AlertSetting.id]: of a group, of a kind with no group, and of a kind set
     * apart from its group (an exception, which may also be Ring). A kind does what its own entry, else its group's,
     * says, else its [AlertSetting.defaultChoice].
     */
    val choices: StateFlow<Map<String, AlertChoice>>

    /**
     * Sets what this phone does with alerts of [setting], and applies it at once to the ones that are active. For a
     * kind of a group it is an exception, kept until [clearChoice]. A protected one always rings, so the call is
     * ignored for it.
     */
    fun setChoice(setting: AlertSetting, choice: AlertChoice)

    /** Removes the exception of [kind]: it follows its group again, or rings when it has none. Applied at once. */
    fun clearChoice(kind: AlertKind)

    /**
     * The pauses on this phone: kind id to the end of its pause (epoch ms). A paused kind that rings is
     * [AlertChoice.SILENT] until then - its card is shown, nothing rings - and what is still active of it rings when
     * the pause ends. A pause that ended is gone from the map.
     */
    val pauses: StateFlow<Map<String, Long>>

    /**
     * Pauses [kinds] on this phone until [until] (`_docs/ALARMS.md`, step 2d): before sport or a meal. Pausing a
     * kind again moves the end. A protected kind is never paused; "until switched on again" is the Silent choice.
     */
    fun pause(kinds: Collection<AlertKind>, until: Long)

    /** Ends the pause of [kinds] now: what is active of them rings, when the kind rings. */
    fun resume(kinds: Collection<AlertKind>)

    /**
     * The sound the user chose on this phone (`_docs/ALARMS.md`, step 2e), by [AlertSetting.id]: a kind of a group
     * plays its group's, any other kind its own. One that is not in it plays its alert's own sound. Each phone its own,
     * as the choice to ring.
     */
    val sounds: StateFlow<Map<String, AlertSound>>

    /** Sets the sound of [setting] on this phone; null: the alert's own sound again. It applies from the next ring. */
    fun setSound(setting: AlertSetting, sound: AlertSound?)

    /**
     * Plays [kind]'s sound on this phone (its group's, when it has one) as an alert of [severity] would ring, for
     * [TEST_SECONDS]: CRITICAL the alarm sound, WARNING the chime. The user asked for it, so it sounds also in Do Not
     * Disturb, whatever the override says (decided 2026-11-26). [vibrate]: it vibrates instead, with no sound, as a
     * kind set to Vibrate rings; where the phone cannot vibrate it plays the sound. Nothing is played while an alarm
     * is sounding: a test must never stop a real one.
     *
     * @return false when nothing was played because an alarm is sounding
     */
    fun test(kind: AlertKind, severity: AlertSeverity, vibrate: Boolean = false): Boolean

    /**
     * "Silence everywhere" (`_docs/ALARMS.md`, step 2f): silences the alert on this phone at once, as [silence] does,
     * and then for every other phone - the master records it for its clients, a client sends it to the master. Not
     * waiting for the other phones: a client that is not connected still stops ringing.
     */
    fun silenceEverywhere(source: String, key: String, minutes: Int)

    /** Whether [alert] can be silenced everywhere from this phone: a BG alarm, and the link to the other phones is on. */
    fun canSilenceEverywhere(alert: Alert): Boolean

    /**
     * A silence given on another phone for every phone. It silences the matching active alert until its end, and is
     * kept for a while for an alert this phone has not raised yet. A silence that is over, from a clock far ahead, or
     * shorter than this phone's own silence of it changes nothing.
     */
    fun applySharedSilence(silence: SharedSilence)

    /**
     * Until when (epoch ms) the active alert of [source] and [key] is silenced, or 0 when it is not silenced or not
     * active. For a raiser that extends a silence itself, as the BG alarms' smart silence does.
     */
    fun silencedUntil(source: String, key: String): Long

    /**
     * The alert whose card is ringing now, or null.
     *
     * For the surfaces where the user cannot pick one - the watch's Silence and Mute, the Mute action on the
     * notification - which have to act on whatever is making the noise. It is the newest active alert that is
     * CRITICAL, has a card and is not silenced, which is how the notification list picks the card that owns the
     * sound. A sounding card that is not an alert, which a driver still posting its own alarm can leave, is not seen
     * here; `UiInteraction.stopAlarm` falls back to silencing everything in that case.
     */
    val sounding: Alert?

    /** The pump reports this alert. */
    fun raise(alert: Alert)

    /** The pump says it is gone, or the user's action cleared it. Stops the sound, removes the card. */
    fun resolve(source: String, key: String)

    /** The pump is removed, discarded or stopped: every alert of [source] ends. */
    fun resolveAll(source: String)

    /**
     * The user answered the alarm. Called by the alarm screen, the card and the watch, never by a driver. An
     * [AlertLifetime.CONDITION] stays and its sound stops for [minutes], or its own [Alert.silenceMinutes] when null;
     * an [AlertLifetime.EVENT] ends.
     */
    fun silence(source: String, key: String, minutes: Int? = null)

    /**
     * The sound stops for [minutes], whatever the lifetime: "Mute 5 min", and the silence of a CONDITION. A protected
     * kind is silenced for [PROTECTED_SILENCE_MAX_MINUTES] at most.
     */
    fun snooze(source: String, key: String, minutes: Int)

    /** Called by the alarm screen: run the alert's action, as its button on the card does. */
    fun act(source: String, key: String)

    /**
     * Books [alert] to be raised at [at] (epoch ms), also when the app does not run then: the platform rings or wakes
     * the app at that time (`_docs/ALARMS.md`, step 1e). Booking the same source and key again moves it. A booking
     * found more than [LATE_LIMIT_MINUTES] after its time (the phone was off) is dropped, not rung late.
     *
     * The booking is saved, so it rings also after the process ended, but its [Alert.action] is not: a raiser whose
     * alert has an action books it again at every app start, as the "time to eat" timer does.
     *
     * @return false when this phone cannot book it (Android: exact alarms not allowed). Nothing is booked then, and
     *   the caller tells the user.
     */
    fun schedule(alert: Alert, at: Long): Boolean

    /** Takes back a booking that has not rung yet. Harmless when there is none. An alert already raised stays. */
    fun unschedule(source: String, key: String)

    companion object {

        /** A booking this late is dropped: a reminder for something long past is no use. */
        const val LATE_LIMIT_MINUTES = 2

        /** How long [test] plays an alarm sound. */
        const val TEST_SECONDS = 5

        /**
         * How long a silence lasts when the user does not choose: a swipe, the watch, Silence on the card. When it ends
         * and the alert is still active, it rings again (decided 2026-11-19).
         */
        const val SILENCE_MINUTES = 30

        /** The longest silence of a protected kind: insulin delivery stopped or not safe must not wait long. */
        const val PROTECTED_SILENCE_MAX_MINUTES = 30

        /**
         * The silence lengths the alarm screen offers, in minutes: one button each, one tap silences and closes
         * (decided 2026-11-26; it was a row of five to pick from, then a button).
         */
        private val SILENCE_LENGTHS = listOf(15, 30, 60)

        /** How long "Silence all BG alarms" on the alarm screen pauses them, in minutes (decided 2026-11-26). */
        const val SILENCE_ALL_MINUTES = 60

        /**
         * The silence buttons on the notification of a WARNING alert that lasts (CONDITION), in minutes: the three
         * "ignore" buttons the loop's carbs notification had, for every warning (decided 2026-11-24). An EVENT
         * has none: answering it ends it, whatever the length.
         */
        val NOTIFICATION_SILENCE_MINUTES = listOf(5, 15, 30)

        /** The silence lengths the alarm screen offers for an alert of [kind]: a protected kind only the short ones. */
        fun silenceLengths(kind: AlertKind): List<Int> =
            if (kind.isProtected) SILENCE_LENGTHS.filter { it <= PROTECTED_SILENCE_MAX_MINUTES } else SILENCE_LENGTHS
    }
}

/**
 * What a platform does for alerts beyond the notification list, which already owns the card and the sound.
 */
interface AlertPlatform {

    /**
     * Whether the phone can vibrate without a sound, also from the background, for [AlertChoice.VIBRATE]. Android
     * can. iOS cannot (a notification without a sound does not vibrate) and the desktop has nothing to vibrate, so
     * they do not offer the choice, and a Vibrate stored on another phone rings there.
     */
    val canVibrate: Boolean get() = false

    /** A CRITICAL alert rings: bring up the alarm screen for it, also when the app is in the background. */
    fun showAlarmScreen(alert: Alert)

    /**
     * A WARNING alert: show it as a system notification with one sound: [sound], or on Android the user's ringtone
     * [ringtoneUri], with [sound] as its fallback. [vibrateOnly]: it vibrates once instead, with no sound.
     * [breakThroughDnd]: it sounds in Do Not Disturb whatever the override says, for a test the user asked for.
     */
    fun chime(alert: Alert, sound: AlarmSound, ringtoneUri: String? = null, vibrateOnly: Boolean = false, breakThroughDnd: Boolean = false)

    /**
     * The alert is silenced until [until] (epoch ms). The core rings it again itself when the time comes, but only
     * while its process runs: a platform that cannot run a timer in the background (iOS) schedules the ring here.
     */
    fun snoozed(alert: Alert, until: Long)

    /** The alert ended, or rings again now: take back whatever [chime] or [snoozed] left for it. */
    fun withdrawn(source: String, key: String)

    /**
     * [alert] is booked for [at] (epoch ms) and must happen also when the app does not run then. Android wakes the
     * app with an exact alarm, and the core raises it. iOS cannot run the app at a time, so it books a notification
     * that rings by itself ([ring] false: no sound, for a kind the user made silent); the core raises the alert when
     * the app runs next.
     *
     * @return false when the phone cannot book it
     */
    fun book(alert: Alert, at: Long, ring: Boolean): Boolean

    /** The booking of [source]/[key] is taken back, or it was raised: take back what [book] left. */
    fun unbook(source: String, key: String)
}
