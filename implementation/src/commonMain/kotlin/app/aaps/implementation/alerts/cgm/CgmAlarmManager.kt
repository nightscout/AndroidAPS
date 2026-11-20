package app.aaps.implementation.alerts.cgm

import app.aaps.core.data.model.GV
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.alerts.CgmAlarmGroup
import app.aaps.core.interfaces.alerts.CgmAlarmType
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.source.CgmSensorAlertProvider
import app.aaps.core.interfaces.source.CgmSensorAlertState
import app.aaps.core.interfaces.source.CgmSensorFailure
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.alerts.title
import app.aaps.implementation.alerts.keys.LocalAlertLongKey
import app.aaps.implementation.alerts.keys.LocalAlertStringKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/**
 * Runs the CGM alarms: reads glucose and sensor state, asks [CgmAlarmEngine] what to do, and turns
 * its answer into notifications and watch messages.
 *
 * This is the one alarm layer for every BG source: the glucose alarms read the stored glucose, the
 * sensor alarms read the active source when it is a [CgmSensorAlertProvider]. The sound and the Do
 * Not Disturb rules are those of every AAPS alarm ([NotificationManager]): an alarm with a sound is
 * posted URGENT, which loops its sound until the notification goes.
 *
 * It only notifies. It reads glucose and never writes it, and it reaches neither the loop nor dosing.
 */
@SingleIn(AppScope::class)
@Inject
class CgmAlarmManager(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rh: TextResolver,
    private val activePlugin: ActivePlugin,
    private val profileUtil: ProfileUtil,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val notificationManager: NotificationManager,
    private val rxBus: RxBus,
    private val appScope: CoroutineScope,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The periodic pass, the "new reading" event, the timer, the buttons and the watch can all come at
     * the same time. Without the lock two of them could read the same state and post the same alarm
     * twice, or an acknowledge could be overwritten by a check that started before it.
     */
    private val lock = Mutex()

    /** False until the first check of this run: the notifications of the last run are gone. */
    private var started = false

    private var timer: Job? = null

    /** What the alarms said last, for the text of a reminder. */
    private var lastGlucose: CgmGlucoseInput? = null
    private var lastSensor: CgmSensorAlertState = CgmSensorAlertState.Ok

    private val _mutedUntil = MutableStateFlow(0L)
    val mutedUntil: StateFlow<Long> = _mutedUntil.asStateFlow()

    init {
        preferences.registerPreferences(LocalAlertStringKey.entries)
        _mutedUntil.value = preferences.get(LocalAlertLongKey.CgmAlarmsMutedUntil)
        rxBus.toFlow(EventData.CgmAlarmAcknowledge::class).collectResilient(appScope, aapsLogger, LTag.CORE) { message ->
            CgmAlarmType.byId(message.id)?.let { acknowledge(it) }
        }
    }

    /** One check of every CGM alarm. */
    suspend fun check() = lock.withLock {
        val now = dateUtil.now()
        var runtime = loadRuntime()
        if (!started) {
            started = true
            runtime = runtime.afterRestart()
        }
        val provider = activePlugin.activeBgSource as? CgmSensorAlertProvider
        val source = activePlugin.activeBgSource::class.simpleName ?: ""
        if (source != preferences.get(LocalAlertStringKey.CgmAlarmSource)) {
            // Another BG source: its sensor is not the one the old alarms were about.
            val sensorTypes = CgmAlarmType.entries.filter { !it.isGlucose }.toSet()
            runtime.raised.filter { it in sensorTypes }.forEach { clear(it) }
            runtime = CgmAlarmRuntime(runtime.tracks.filterKeys { id -> CgmAlarmType.byId(id)?.isGlucose == true })
            preferences.put(LocalAlertStringKey.CgmAlarmSource, source)
        }
        val leadMs = T.hours(preferences.get(IntKey.AlertCgmExpiringLeadHours).toLong()).msecs()
        val sensor = try {
            provider?.sensorAlertState(now, leadMs) ?: CgmSensorAlertState.Ok
        } catch (e: Exception) {
            // A broken source must not stop the glucose alarms.
            aapsLogger.error(LTag.CORE, "CGM alarms: the sensor state could not be read", e)
            CgmSensorAlertState.Ok
        }
        val glucose = glucoseInput(now)
        lastGlucose = glucose
        lastSensor = sensor
        val held = CgmAlarmType.entries.filter { runtime[it].conditionSince != null }.toSet()
        val conditions = CgmAlarmConditions.evaluate(glucose, levels(), sensor, held, now)
        val step = CgmAlarmEngine.step(conditions, ::settings, runtime, now, _mutedUntil.value)
        apply(step)
        saveRuntime(step.runtime)
        schedule(step.nextCheckAt, now)
    }

    /**
     * The user acknowledged [type], or every alarm going off when it is null. Runs in the background,
     * after a check that is running at that moment, so the check cannot overwrite it.
     */
    fun acknowledge(type: CgmAlarmType? = null) {
        appScope.launch {
            lock.withLock {
                val now = dateUtil.now()
                val step = CgmAlarmEngine.acknowledge(loadRuntime(), ::settings, type, now)
                aapsLogger.debug(LTag.CORE, "CGM alarms acknowledged: ${step.actions.map { it.type.id }}")
                apply(step)
                saveRuntime(step.runtime)
            }
        }
    }

    /**
     * "Hypo treated": the user took carbs. The low alarms stop and stay quiet for [snoozeMs], about the
     * time carbs need to work. Urgent low comes back after its own repeat time at most.
     */
    fun acknowledgeHypoTreated(snoozeMs: Long) {
        appScope.launch {
            lock.withLock {
                val lowGroup = CgmAlarmType.entries.filter { it.group == CgmAlarmGroup.LOW }.toSet()
                val runtime = loadRuntime()
                val now = dateUtil.now()
                // Also when the low alarm is not ringing right now (dismissed, or between two
                // reminders): the hold is what the user asked for.
                val raisedLows = CgmAlarmRuntime(runtime.tracks.mapValues { (id, track) ->
                    val t = CgmAlarmType.byId(id)
                    if (t in lowGroup && track.conditionSince != null && !track.isRaised) track.copy(raisedAt = now) else track
                })
                val step = CgmAlarmEngine.acknowledge(raisedLows, ::settings, null, now, snoozeMs = snoozeMs, only = lowGroup)
                lowGroup.forEach { notificationManager.dismiss(it.notificationId) }
                apply(step)
                saveRuntime(step.runtime)
                aapsLogger.debug(LTag.CORE, "Low alarms held after treatment for ${T.msecs(snoozeMs).mins()} min")
            }
        }
    }

    /** Mute every CGM alarm but urgent low for [minutes]. 0 ends the mute. */
    fun mute(minutes: Int) {
        val until = if (minutes > 0) dateUtil.now() + T.mins(minutes.toLong()).msecs() else 0L
        preferences.put(LocalAlertLongKey.CgmAlarmsMutedUntil, until)
        _mutedUntil.value = until
        aapsLogger.debug(LTag.CORE, "CGM alarms muted until ${if (until == 0L) "-" else dateUtil.dateAndTimeString(until)}")
        // Stop what is ringing now (or bring back what the mute held), without waiting for the next reading.
        appScope.launch { check() }
    }

    /** Play [type] once, as the real alarm would, until the user dismisses it. Changes no alarm state. */
    fun test(type: CgmAlarmType) {
        val sound = settings(type).sound
        notificationManager.post(
            NotificationId.CGM_ALARM_TEST,
            CoreUiStrings.cgm_alarm_test_message.withArgs(rh.gs(type.title)),
            level = if (sound != null) NotificationLevel.URGENT else NotificationLevel.NORMAL,
            sound = sound
        )
    }

    private fun settings(type: CgmAlarmType): CgmAlarmSettings = CgmAlarmKeys.settings(preferences, type)

    /** The levels in mg/dL. `getRaw` on purpose: `get` would convert them to the display unit. */
    private fun levels() = CgmAlarmLevels(
        lowMgdl = preferences.getRaw(UnitDoubleKey.AlertHypoThreshold),
        highMgdl = preferences.getRaw(UnitDoubleKey.AlertHyperThreshold),
        fallDropMgdl = preferences.get(IntKey.AlertRapidFallDrop).toDouble(),
        riseMgdl = preferences.get(IntKey.AlertRapidRiseAmount).toDouble(),
    )

    /** The value of a stored reading, in mg/dL. The alarms only read it, they never change it. */
    private fun shownValue(gv: GV): Double = gv.value

    private suspend fun glucoseInput(now: Long): CgmGlucoseInput {
        val last = persistenceLayer.getLastGlucoseValue() ?: return CgmGlucoseInput(null, null, null, null, null)
        val fallWindow = preferences.get(IntKey.AlertRapidFallWindow)
        val riseWindow = preferences.get(IntKey.AlertRapidRiseWindow)
        val longest = maxOf(fallWindow, riseWindow, RATE_WINDOW_MIN)
        // One read of the stored readings for all three windows.
        val readings = persistenceLayer.getBgReadingsDataFromTime(now - T.mins(longest.toLong()).msecs(), ascending = true)

        fun window(minutes: Int) = readings.filter { it.timestamp >= now - T.mins(minutes.toLong()).msecs() }
        val fall = window(fallWindow).takeIf { it.size >= 2 }?.let { shownValue(it.first()) - shownValue(it.last()) }
        val rise = window(riseWindow).takeIf { it.size >= 2 }?.let { shownValue(it.last()) - shownValue(it.first()) }
        val rateReadings = window(RATE_WINDOW_MIN)
        val rate = if (rateReadings.size >= 2) {
            val spanMin = (rateReadings.last().timestamp - rateReadings.first().timestamp) / 60_000.0
            if (spanMin >= RATE_MIN_SPAN_MIN) (shownValue(rateReadings.last()) - shownValue(rateReadings.first())) / spanMin else null
        } else null
        return CgmGlucoseInput(latestMgdl = shownValue(last), latestAt = last.timestamp, ratePerMinute = rate, fallMgdl = fall, riseMgdl = rise)
    }

    private fun apply(step: CgmAlarmStep) {
        for (action in step.actions) when (action) {
            is CgmAlarmAction.Raise -> raise(action.type, action.reminder)
            is CgmAlarmAction.Clear -> clear(action.type)
        }
    }

    private fun raise(type: CgmAlarmType, reminder: Boolean) {
        val sound = settings(type).sound
        val text = CgmAlarmTexts.message(type, lastGlucose, lastSensor, profileUtil, dateUtil, preferences) ?: type.title
        aapsLogger.debug(LTag.CORE, "CGM alarm ${type.id} ${if (reminder) "again" else "raised"}, sound=${sound?.name}")
        notificationManager.post(
            type.notificationId,
            text,
            level = if (sound != null) NotificationLevel.URGENT else type.notificationId.defaultLevel,
            sound = sound,
            actions = actionsFor(type)
        )
        if (type.reminds) {
            val urgent = type.group == CgmAlarmGroup.LOW || type == CgmAlarmType.SENSOR_FAILED || type == CgmAlarmType.SENSOR_ENDED
            rxBus.send(
                EventMobileToWear(
                    EventData.CgmAlarm(
                        id = type.id,
                        title = rh.gs(type.title),
                        message = rh.gs(text),
                        vibrationSeconds = if (urgent) WATCH_VIBRATION_URGENT_S else WATCH_VIBRATION_S,
                        urgent = urgent
                    )
                )
            )
        }
    }

    private fun clear(type: CgmAlarmType) {
        notificationManager.dismiss(type.notificationId)
        if (type.reminds) rxBus.send(EventMobileToWear(EventData.CgmAlarmCancel(type.id)))
    }

    private fun actionsFor(type: CgmAlarmType): List<NotificationAction> = buildList {
        add(NotificationAction(CoreUiStrings.cgm_alarm_acknowledge) { acknowledge(type) })
        if (type.group == CgmAlarmGroup.LOW) add(NotificationAction(CoreUiStrings.alert_hypo_treated) { acknowledgeHypoTreated(HYPO_TREATED_SNOOZE_MS) })
    }

    /** Look again at [nextCheckAt] even when no reading comes, for the delays, reminders and the mute end. */
    private fun schedule(nextCheckAt: Long?, now: Long) {
        timer?.cancel()
        if (nextCheckAt == null) return
        val wait = (nextCheckAt - now).coerceIn(MIN_TIMER_MS, MAX_TIMER_MS)
        timer = appScope.launch {
            delay(wait)
            try {
                check()
            } catch (e: Exception) {
                aapsLogger.error(LTag.CORE, "CGM alarms: timed check failed", e)
            }
        }
    }

    private fun loadRuntime(): CgmAlarmRuntime {
        val stored = preferences.get(LocalAlertStringKey.CgmAlarmRuntime)
        if (stored.isEmpty()) return CgmAlarmRuntime()
        return try {
            json.decodeFromString(CgmAlarmRuntime.serializer(), stored)
        } catch (e: Exception) {
            // A broken state starts over: every alarm that still holds goes off again, never the other way round.
            aapsLogger.error(LTag.CORE, "CGM alarms: stored state unreadable, starting over", e)
            CgmAlarmRuntime()
        }
    }

    private fun saveRuntime(runtime: CgmAlarmRuntime) {
        preferences.put(LocalAlertStringKey.CgmAlarmRuntime, json.encodeToString(CgmAlarmRuntime.serializer(), runtime))
    }

    companion object {

        /** "Hypo treated": about how long carbs need to raise glucose. */
        val HYPO_TREATED_SNOOZE_MS: Long = T.mins(20).msecs()

        /** The rate for urgent low soon is taken over this many minutes. */
        const val RATE_WINDOW_MIN: Int = 15

        /** Shortest span of readings that gives a rate. */
        const val RATE_MIN_SPAN_MIN: Double = 4.0

        private const val MIN_TIMER_MS = 15_000L
        private const val MAX_TIMER_MS = 5 * 60_000L

        const val WATCH_VIBRATION_URGENT_S = 10
        const val WATCH_VIBRATION_S = 5
    }
}

/** Messages of the CGM alarms. Pure, so each message is tested. The titles are `CgmAlarmType.title` in :core:ui. */
object CgmAlarmTexts {

    /** The message of [type] from the last values, or null when the values are not there. */
    fun message(
        type: CgmAlarmType,
        glucose: CgmGlucoseInput?,
        sensor: CgmSensorAlertState,
        profileUtil: ProfileUtil,
        dateUtil: DateUtil,
        preferences: Preferences,
    ): TextRef? {
        val bg = glucose?.latestMgdl?.let { profileUtil.fromMgdlToStringWithUnits(it) }
        return when (type) {
            CgmAlarmType.URGENT_LOW          -> bg?.let { CoreUiStrings.cgm_alarm_urgent_low_message.withArgs(it) }
            CgmAlarmType.URGENT_LOW_SOON     -> bg?.let { CoreUiStrings.cgm_alarm_urgent_low_soon_message.withArgs(it) }
            CgmAlarmType.LOW                 -> bg?.let { CoreUiStrings.alert_hypo_message.withArgs(it) }
            CgmAlarmType.HIGH                -> bg?.let { CoreUiStrings.alert_hyper_message.withArgs(it) }
            CgmAlarmType.FALL_RATE           -> glucose?.fallMgdl?.let {
                CoreUiStrings.alert_rapid_fall_message.withArgs(profileUtil.fromMgdlToStringWithUnits(it), preferences.get(IntKey.AlertRapidFallWindow))
            }

            CgmAlarmType.RISE_RATE           -> glucose?.riseMgdl?.let {
                CoreUiStrings.cgm_alarm_rapid_rise_message.withArgs(profileUtil.fromMgdlToStringWithUnits(it), preferences.get(IntKey.AlertRapidRiseWindow))
            }

            CgmAlarmType.SENSOR_FAILED       -> (sensor as? CgmSensorAlertState.Failed)?.let {
                when (it.reason) {
                    CgmSensorFailure.SENSOR_ERROR -> CoreUiStrings.cgm_alert_failed_sensor_error
                    CgmSensorFailure.ENDED_EARLY  -> CoreUiStrings.cgm_alert_failed_ended_early
                    CgmSensorFailure.OTHER        -> CoreUiStrings.cgm_alert_failed_other
                }
            }

            CgmAlarmType.SENSOR_ENDED        -> (sensor as? CgmSensorAlertState.Expired)?.let { CoreUiStrings.cgm_alert_expired.withArgs(dateUtil.dateAndTimeString(it.atEpochMs)) }
            CgmAlarmType.SENSOR_ENDING_SOON  -> (sensor as? CgmSensorAlertState.Expiring)?.let { CoreUiStrings.cgm_alert_expiring.withArgs(dateUtil.dateAndTimeString(it.endsAtEpochMs)) }
            CgmAlarmType.SIGNAL_LOSS         -> (sensor as? CgmSensorAlertState.SignalLost)?.let { CoreUiStrings.cgm_alert_signal_lost.withArgs(dateUtil.timeString(it.sinceEpochMs)) }
            CgmAlarmType.SENSOR_ISSUE        -> (sensor as? CgmSensorAlertState.SensorIssue)?.let { CoreUiStrings.cgm_alert_sensor_issue.withArgs(dateUtil.timeString(it.sinceEpochMs)) }
            CgmAlarmType.SECOND_SENSOR_READY -> (sensor as? CgmSensorAlertState.SecondSensorReady)?.let { CoreUiStrings.cgm_alert_second_sensor_ready.withArgs(dateUtil.timeString(it.atEpochMs)) }
            CgmAlarmType.WARMUP_DONE         -> (sensor as? CgmSensorAlertState.WarmupDone)?.let { CoreUiStrings.cgm_alert_warmup_done.withArgs(dateUtil.timeString(it.atEpochMs)) }
        }
    }
}
