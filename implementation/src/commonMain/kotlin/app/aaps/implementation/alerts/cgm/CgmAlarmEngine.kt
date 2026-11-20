package app.aaps.implementation.alerts.cgm

import app.aaps.core.interfaces.alerts.CgmAlarmGroup
import app.aaps.core.interfaces.alerts.CgmAlarmType
import app.aaps.core.interfaces.notifications.AlarmSound
import kotlinx.serialization.Serializable

/**
 * The settings of one CGM alarm, read from the preferences.
 *
 * @param delayMinutes the condition must last this long before the first alarm. 0 = at once.
 * @param repeatMinutes after an acknowledge, quiet this long. 0 = quiet until the condition is gone.
 * @param sound the sound of the alarm, null for a notification without sound.
 */
data class CgmAlarmSettings(
    val enabled: Boolean,
    val delayMinutes: Int = 0,
    val repeatMinutes: Int = 0,
    val sound: AlarmSound? = null,
)

/**
 * A condition of one alarm that holds now, before its delay.
 *
 * @param episode which occurrence it is. When it changes, the alarm starts over as a new alert, also
 *   when the old one was acknowledged (for example a new end of a new sensor).
 * @param sinceMs when the condition started, when the source knows it (the last reading before a
 *   signal loss). Null: from the first check that saw it.
 */
data class CgmAlarmCondition(val episode: String = ON, val sinceMs: Long? = null) {

    companion object {

        /** The episode of an alarm that has only one kind of occurrence. */
        const val ON = "on"
    }
}

/** Where one alarm stands. Times are epoch ms. */
@Serializable
data class CgmAlarmTrack(
    /** The episode of the condition, see [CgmAlarmCondition.episode]. */
    val episode: String? = null,
    /** Since when the condition has held, before any delay. Null while it does not hold. */
    val conditionSince: Long? = null,
    /** Since when the condition has not held. Only kept while [quietUntilCleared]. */
    val clearSince: Long? = null,
    /** The alarm is going off and nobody has acknowledged it yet. */
    val raisedAt: Long? = null,
    /** When the alarm last went off, for the reminder. */
    val lastAlertAt: Long? = null,
    /** Acknowledged: quiet until then, even if the condition lasts. */
    val snoozedUntil: Long? = null,
    /** Acknowledged with repeat 0: quiet until the condition has gone away. */
    val quietUntilCleared: Boolean = false,
    /** For an alarm that does not remind: the episode already shown, so it is not shown again. */
    val shownEpisode: String? = null,
) {

    val isRaised: Boolean get() = raisedAt != null
}

/** Where every alarm stands, by [CgmAlarmType.id] so the stored form stays readable and stable. */
@Serializable
data class CgmAlarmRuntime(val tracks: Map<String, CgmAlarmTrack> = emptyMap()) {

    operator fun get(type: CgmAlarmType): CgmAlarmTrack = tracks[type.id] ?: CgmAlarmTrack()

    /** Alarms going off now, most important first. */
    val raised: List<CgmAlarmType> get() = CgmAlarmType.entries.filter { this[it].isRaised }

    /** The same state as after an app restart: the notifications are gone, the acknowledges are kept. */
    fun afterRestart(): CgmAlarmRuntime = CgmAlarmRuntime(tracks.mapValues { it.value.copy(raisedAt = null) })
}

sealed class CgmAlarmAction {

    abstract val type: CgmAlarmType

    /** Go off: notification, sound and watch. [reminder] is true when it goes off again. */
    data class Raise(override val type: CgmAlarmType, val reminder: Boolean) : CgmAlarmAction()

    /** Stop: the condition has gone, the alarm was acknowledged, muted, or a more important one took over. */
    data class Clear(override val type: CgmAlarmType) : CgmAlarmAction()
}

data class CgmAlarmStep(
    val runtime: CgmAlarmRuntime,
    val actions: List<CgmAlarmAction>,
    /** When to look again even if nothing new arrives. Null when nothing is pending. */
    val nextCheckAt: Long?,
)

/**
 * Decides when the CGM alarms go off. The rules are the ones of the Dexcom G7 app, as nightscout/AndroidAPS
 * pull request 5397 has them, with its two open review points fixed:
 *
 * - An alarm goes off when its condition holds (for its delay, if it has one).
 * - Not acknowledged: it goes off again every [REMINDER_MS], with sound, until acknowledged or the
 *   condition goes away. A dismissed notification is not an acknowledge, so the alarm keeps coming
 *   back (the first review point). The sound itself loops until the notification goes.
 * - Acknowledged: quiet for the alarm's repeat time. If the condition still holds after that, it
 *   goes off again as a new alert. Repeat 0 keeps it quiet until the condition has gone away for
 *   [CLEAR_MS].
 * - The mute timer ([step] `mutedUntil`) keeps every alarm quiet until it ends, except urgent low
 *   ([CgmAlarmType.neverMuted]): the second review point. A muted alarm that still holds goes off
 *   when the mute ends.
 * - Only the most important alarm of a group goes off at a time ([CgmAlarmGroup]).
 * - An information ([CgmAlarmType.reminds] false) is shown once per episode and stays until it is
 *   acknowledged, a new episode replaces it, or a more important alarm of its group takes over.
 *
 * Pure: no platform code, no clock of its own, so every rule is tested. It only decides about
 * notifications; nothing here can reach dosing.
 */
object CgmAlarmEngine {

    /** Alarms that are not acknowledged go off again this often. */
    const val REMINDER_MS: Long = 5 * 60_000L

    /** How long a condition must be gone before a "repeat 0" alarm may go off again. */
    const val CLEAR_MS: Long = 15 * 60_000L

    /** Whether the mute timer keeps [type] quiet at [now]. Never for urgent low. */
    fun isMuted(type: CgmAlarmType, now: Long, mutedUntil: Long): Boolean = !type.neverMuted && now < mutedUntil

    fun step(
        conditions: Map<CgmAlarmType, CgmAlarmCondition>,
        settings: (CgmAlarmType) -> CgmAlarmSettings,
        runtime: CgmAlarmRuntime,
        now: Long,
        mutedUntil: Long = 0L,
    ): CgmAlarmStep {
        val actions = mutableListOf<CgmAlarmAction>()
        val tracks = mutableMapOf<String, CgmAlarmTrack>()
        val next = mutableListOf<Long>()
        val activeGroups = mutableSetOf<CgmAlarmGroup>()
        if (mutedUntil > now) next += mutedUntil

        for (type in CgmAlarmType.entries) {
            val cfg = settings(type)
            var track = runtime[type]
            if (!cfg.enabled) {
                if (track.isRaised) actions += CgmAlarmAction.Clear(type)
                continue
            }
            val condition = conditions[type]
            if (condition != null && track.episode != null && track.episode != condition.episode) {
                // The next occurrence (a new sensor end, a new signal loss): a new alert, whatever was
                // acknowledged before. The notification of the old one is replaced by the new one.
                track = CgmAlarmTrack(raisedAt = track.raisedAt, shownEpisode = track.shownEpisode)
            }
            track = if (condition != null) {
                track.copy(episode = condition.episode, conditionSince = track.conditionSince ?: condition.sinceMs ?: now, clearSince = null)
            } else {
                track.copy(conditionSince = null, clearSince = if (track.quietUntilCleared) track.clearSince ?: now else null)
            }
            if (condition == null && track.quietUntilCleared) {
                val clearedAt = (track.clearSince ?: now) + CLEAR_MS
                if (now >= clearedAt) track = track.copy(quietUntilCleared = false, clearSince = null) else next += clearedAt
            }
            track.snoozedUntil?.let { if (it <= now) track = track.copy(snoozedUntil = null) }

            val delayEndsAt = track.conditionSince?.plus(cfg.delayMinutes * 60_000L)
            val active = delayEndsAt != null && delayEndsAt <= now
            if (delayEndsAt != null && !active) next += delayEndsAt

            val suppressed = active && type.group != CgmAlarmGroup.INFO && type.group in activeGroups
            if (active) activeGroups += type.group
            val muted = isMuted(type, now, mutedUntil)

            if (!type.reminds) {
                when {
                    suppressed                                     -> if (track.isRaised) {
                        actions += CgmAlarmAction.Clear(type)
                        track = track.copy(raisedAt = null)
                    }

                    active && !muted && track.shownEpisode != track.episode -> {
                        actions += CgmAlarmAction.Raise(type, reminder = false)
                        track = track.copy(raisedAt = now, lastAlertAt = now, shownEpisode = track.episode)
                    }
                    // Otherwise it stays as it is: shown until acknowledged, or not shown.
                }
            } else {
                when {
                    !active || suppressed      -> if (track.isRaised) {
                        actions += CgmAlarmAction.Clear(type)
                        track = track.copy(raisedAt = null)
                    }

                    muted                      -> if (track.isRaised) {
                        // Stop the sound for the mute. It goes off as a new alert when the mute ends.
                        actions += CgmAlarmAction.Clear(type)
                        track = track.copy(raisedAt = null)
                    }

                    track.snoozedUntil != null -> next += track.snoozedUntil ?: now
                    track.quietUntilCleared    -> Unit

                    !track.isRaised            -> {
                        actions += CgmAlarmAction.Raise(type, reminder = false)
                        track = track.copy(raisedAt = now, lastAlertAt = now)
                        next += now + REMINDER_MS
                    }

                    else                       -> {
                        val reminderAt = (track.lastAlertAt ?: now) + REMINDER_MS
                        if (now >= reminderAt) {
                            actions += CgmAlarmAction.Raise(type, reminder = true)
                            track = track.copy(lastAlertAt = now)
                            next += now + REMINDER_MS
                        } else next += reminderAt
                    }
                }
            }
            if (track != CgmAlarmTrack()) tracks[type.id] = track
        }
        return CgmAlarmStep(CgmAlarmRuntime(tracks), actions, next.minOrNull())
    }

    /**
     * The user acknowledged [type], or every alarm going off when it is null.
     *
     * @param snoozeMs when not null, the quiet time instead of the repeat time (the "hypo treated"
     *   button holds the low alarms for the time carbs need). Urgent low never gets more than its own
     *   repeat time, so it always comes back within 30 minutes.
     */
    fun acknowledge(
        runtime: CgmAlarmRuntime,
        settings: (CgmAlarmType) -> CgmAlarmSettings,
        type: CgmAlarmType?,
        now: Long,
        snoozeMs: Long? = null,
        only: Set<CgmAlarmType>? = null,
    ): CgmAlarmStep {
        val actions = mutableListOf<CgmAlarmAction>()
        val tracks = runtime.tracks.mapValues { (id, track) ->
            val t = CgmAlarmType.byId(id) ?: return@mapValues track
            if (!track.isRaised || (type != null && t != type) || (only != null && t !in only)) return@mapValues track
            actions += CgmAlarmAction.Clear(t)
            if (!t.reminds) return@mapValues track.copy(raisedAt = null)
            val repeatMs = settings(t).repeatMinutes * 60_000L
            val quietMs = when {
                snoozeMs == null -> repeatMs
                t.neverMuted     -> minOf(snoozeMs, repeatMs)
                else             -> snoozeMs
            }
            if (quietMs > 0) track.copy(raisedAt = null, snoozedUntil = now + quietMs)
            else track.copy(raisedAt = null, quietUntilCleared = true)
        }
        return CgmAlarmStep(CgmAlarmRuntime(tracks), actions, tracks.values.mapNotNull { it.snoozedUntil }.minOrNull())
    }

    /** Stop every alarm and forget everything, for a new BG source. */
    fun clearAll(runtime: CgmAlarmRuntime): CgmAlarmStep =
        CgmAlarmStep(CgmAlarmRuntime(), runtime.raised.map { CgmAlarmAction.Clear(it) }, null)
}
