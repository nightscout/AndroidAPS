package app.aaps.implementation.alerts

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.EatReminder
import app.aaps.core.interfaces.alerts.EatReminderState
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.math.max

/**
 * The "time to eat" timer.
 *
 * The timer is saved in [StringNonKey.EatReminder], so it survives a restart, and the running-configuration
 * publisher sends every change of that key to the clients. Ringing is the job of [EatReminderAlarm].
 *
 * Two kinds of "rung". The screen goes by the clock ([EatReminderState.hasRung]), so the master and a client
 * show the same thing without sending anything at the moment it rings. The master's decisions go by
 * [EatReminderState.alarmRang], set when the alarm really rang: the alarm can come a little after the end time,
 * and an alarm stopped in that gap was not this one.
 *
 * [UiInteraction] comes through a provider because `UiInteraction.stopAlarm` calls back into
 * [onAlarmStopped], so each needs the other.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class EatReminderManager(
    private val preferences: Preferences,
    private val alarm: EatReminderAlarm,
    private val uiInteraction: () -> UiInteraction,
    private val dateUtil: DateUtil,
    private val config: Config,
    private val aapsLogger: AAPSLogger
) : EatReminder {

    private val lock = AapsLock()
    private val _state = MutableStateFlow(load())
    override val state: StateFlow<EatReminderState?> = _state.asStateFlow()

    override fun start(durationMs: Long) {
        if (!isMaster("start") || durationMs <= 0L) return
        val now = dateUtil.now()
        val new = EatReminderState(startedAt = now, endsAt = now + durationMs, durationMs = durationMs)
        lock.withLock {
            if (!alarm.schedule(new.startedAt, new.endsAt)) return
            save(new)
        }
        aapsLogger.debug(LTag.AUTOMATION, "Eat reminder started for ${T.msecs(durationMs).mins()} min")
    }

    override fun stop() {
        if (!isMaster("stop")) return
        val rang = lock.withLock {
            val current = _state.value ?: return
            alarm.cancel()
            save(null)
            current.alarmRang
        }
        aapsLogger.debug(LTag.AUTOMATION, "Eat reminder stopped")
        if (rang) uiInteraction().stopAlarm("Eat reminder stopped")
    }

    override fun extend() {
        if (!isMaster("extend")) return
        val rang = lock.withLock {
            val current = _state.value ?: return
            val now = dateUtil.now()
            // After the end time, +5 min means 5 minutes from now, not from the time that has passed.
            val newEnd = max(current.endsAt, now) + T.mins(EatReminder.EXTEND_MINUTES.toLong()).msecs()
            // The whole wait grows with the new end, so the bar and the countdown notification show the same number.
            val updated = current.copy(endsAt = newEnd, durationMs = newEnd - current.startedAt, alarmRang = false)
            if (!alarm.schedule(updated.startedAt, updated.endsAt)) return
            save(updated)
            current.alarmRang
        }
        aapsLogger.debug(LTag.AUTOMATION, "Eat reminder extended by ${EatReminder.EXTEND_MINUTES} min")
        // The state changed first: the stopAlarm below comes back to onAlarmStopped, which must find a timer
        // that has not rung yet and leave it alone.
        if (rang) uiInteraction().stopAlarm("Eat reminder extended")
    }

    override fun onRang() {
        if (config.AAPSCLIENT) return
        lock.withLock {
            val current = _state.value ?: return
            if (!current.alarmRang) save(current.copy(alarmRang = true))
        }
    }

    override fun onAlarmStopped() {
        if (config.AAPSCLIENT) return
        lock.withLock {
            val current = _state.value ?: return
            if (!current.alarmRang) return
            alarm.cancel()
            save(null)
        }
        aapsLogger.debug(LTag.AUTOMATION, "Eat reminder ended with its alarm")
    }

    override fun restore() {
        if (config.AAPSCLIENT) return
        lock.withLock {
            val current = _state.value ?: return
            val now = dateUtil.now()
            when {
                current.endsAt > now                  ->
                    if (!alarm.schedule(current.startedAt, current.endsAt)) save(null)

                // Rang long ago and nobody ended it, or ran out while the phone was off: no use any more.
                now - current.endsAt > STALE_AFTER_MS -> save(null)

                // Ended a short time ago (it rang, or the phone was off). The bar shows it; ringing late, maybe a
                // second time, is not wanted.
                else                                  -> Unit
            }
        }
    }

    override fun applyRemote(remote: EatReminderState?) {
        if (!config.AAPSCLIENT) {
            aapsLogger.error(LTag.AUTOMATION, "applyRemote called on a master - ignored")
            return
        }
        lock.withLock {
            if (_state.value != remote) save(remote)
        }
    }

    private fun isMaster(action: String): Boolean {
        if (!config.AAPSCLIENT) return true
        // A client sends its actions to the master; changing its copy here would only be undone by the next sync.
        aapsLogger.error(LTag.AUTOMATION, "Eat reminder $action called on a client - ignored")
        return false
    }

    private fun save(new: EatReminderState?) {
        _state.value = new
        preferences.put(
            StringNonKey.EatReminder,
            new?.let {
                buildJsonObject {
                    put(KEY_STARTED_AT, it.startedAt)
                    put(KEY_ENDS_AT, it.endsAt)
                    put(KEY_DURATION, it.durationMs)
                    put(KEY_ALARM_RANG, it.alarmRang)
                }.toString()
            } ?: ""
        )
    }

    /** Null for nothing saved, and for anything unreadable: no timer is better than a wrong one. */
    private fun load(): EatReminderState? {
        val raw = preferences.get(StringNonKey.EatReminder)
        if (raw.isEmpty()) return null
        return try {
            val json = Json.parseToJsonElement(raw).jsonObject
            fun long(key: String) = (json[key] as? JsonPrimitive)?.content?.toLongOrNull()
            EatReminderState(
                startedAt = long(KEY_STARTED_AT) ?: return null,
                endsAt = long(KEY_ENDS_AT) ?: return null,
                durationMs = long(KEY_DURATION) ?: return null,
                alarmRang = (json[KEY_ALARM_RANG] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
            )
        } catch (_: Exception) {
            null
        }
    }

    private companion object {

        const val KEY_STARTED_AT = "startedAt"
        const val KEY_ENDS_AT = "endsAt"
        const val KEY_DURATION = "durationMs"
        const val KEY_ALARM_RANG = "alarmRang"

        /** A timer this long past its end is dropped at app start. */
        val STALE_AFTER_MS = T.hours(1).msecs()
    }
}
