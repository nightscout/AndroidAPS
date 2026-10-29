package app.aaps.implementation.alerts

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef.Companion.withArgs
import app.aaps.core.objects.extensions.asAnnouncement
import app.aaps.core.ui.CoreUiStrings
import app.aaps.implementation.alerts.keys.LocalAlertLongKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Created by adrian on 17/12/17.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class LocalAlertUtilsImpl(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val rh: TextResolver,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val smsCommunicator: SmsCommunicator,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val notificationManager: NotificationManager,
    private val appScope: CoroutineScope
) : LocalAlertUtils {

    init {
        preferences.registerPreferences(LocalAlertLongKey.entries)
    }

    private fun missedReadingsThreshold(): Long {
        return T.mins(preferences.get(IntKey.AlertsStaleDataThreshold).toLong()).msecs()
    }

    private fun pumpUnreachableThreshold(): Long {
        return T.mins(preferences.get(IntKey.AlertsPumpUnreachableThreshold).toLong()).msecs()
    }

    override fun checkPumpUnreachableAlarm(lastConnection: Long, isStatusOutdated: Boolean, isDisconnected: Boolean) {
        val alarmTimeoutExpired = isAlarmTimeoutExpired(lastConnection, pumpUnreachableThreshold())
        val nextAlarmOccurrenceReached = preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm) < dateUtil.now()
        if (config.APS && isStatusOutdated && alarmTimeoutExpired && nextAlarmOccurrenceReached && !isDisconnected) {
            if (preferences.get(BooleanKey.AlertPumpUnreachable)) {
                aapsLogger.debug(LTag.CORE, "Generating pump unreachable alarm. lastConnection: " + dateUtil.dateAndTimeString(lastConnection) + " isStatusOutdated: true")
                preferences.put(LocalAlertLongKey.NextPumpDisconnectedAlarm, dateUtil.now() + pumpUnreachableThreshold())
                notificationManager.post(NotificationId.PUMP_UNREACHABLE, CoreUiStrings.pump_unreachable, sound = AlarmSound.ALARM)
                if (preferences.get(BooleanKey.NsClientCreateAnnouncementsFromErrors) && config.APS)
                    appScope.launch {
                        persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                            therapyEvent = TE.asAnnouncement(rh.gs(CoreUiStrings.pump_unreachable)),
                            timestamp = dateUtil.now(),
                            action = Action.CAREPORTAL,
                            source = Sources.Aaps,
                            note = rh.gs(CoreUiStrings.pump_unreachable),
                            listValues = listOf(ValueWithUnit.TEType(TE.Type.ANNOUNCEMENT))
                        )
                    }
            }
            if (preferences.get(BooleanKey.SmsReportPumpUnreachable))
                smsCommunicator.sendNotificationToAllNumbers(rh.gs(CoreUiStrings.pump_unreachable))
        }
        if (!isStatusOutdated && !alarmTimeoutExpired) notificationManager.dismiss(NotificationId.PUMP_UNREACHABLE)
    }

    private fun isAlarmTimeoutExpired(lastConnection: Long, unreachableThreshold: Long): Boolean {
        return if (activePlugin.activePump.pumpDescription.hasCustomUnreachableAlertCheck) {
            activePlugin.activePump.isUnreachableAlertTimeoutExceeded(unreachableThreshold)
        } else {
            lastConnection + pumpUnreachableThreshold() < dateUtil.now()
        }
    }

    /*Pre-snoozes the alarms with 5 minutes if no snooze exists.
     * Call only at startup!
     */
    override fun preSnoozeAlarms() {
        if (preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm) < dateUtil.now()) {
            preferences.put(LocalAlertLongKey.NextMissedReadingsAlarm, dateUtil.now() + 5 * 60 * 1000)
        }
        if (preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm) < dateUtil.now()) {
            preferences.put(LocalAlertLongKey.NextPumpDisconnectedAlarm, dateUtil.now() + 5 * 60 * 1000)
        }
    }

    override fun shortenSnoozeInterval() { //shortens alarm times in case of setting changes or future data
        var nextMissedReadingsAlarm = preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm)
        nextMissedReadingsAlarm = min(dateUtil.now() + missedReadingsThreshold(), nextMissedReadingsAlarm)
        preferences.put(LocalAlertLongKey.NextMissedReadingsAlarm, nextMissedReadingsAlarm)
        var nextPumpDisconnectedAlarm = preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm)
        nextPumpDisconnectedAlarm = min(dateUtil.now() + pumpUnreachableThreshold(), nextPumpDisconnectedAlarm)
        preferences.put(LocalAlertLongKey.NextPumpDisconnectedAlarm, nextPumpDisconnectedAlarm)
        // The glucose alarm holds too. A clock that went back would otherwise leave a hold far in
        // the future, and the alarm silent for that long. Each hold is cut to the longest it can
        // really be: the low alarm 20 min ("hypo treated"), the high 30 min, the rapid fall 15 min.
        for ((key, maxHoldMs) in GLUCOSE_ALARM_MAX_HOLDS) {
            val limit = dateUtil.now() + maxHoldMs
            if (preferences.get(key) > limit) preferences.put(key, limit)
        }
    }

    override suspend fun reportPumpStatusRead() {
        val pump = activePlugin.activePump
        val profile = profileFunction.getProfile()
        if (profile != null) {
            val lastConnection = pump.lastDataTime.value
            val earliestAlarmTime = lastConnection + pumpUnreachableThreshold()
            if (preferences.get(LocalAlertLongKey.NextPumpDisconnectedAlarm) < earliestAlarmTime) {
                preferences.put(LocalAlertLongKey.NextPumpDisconnectedAlarm, earliestAlarmTime)
            }
        }
    }

    /**
     * The missed readings alarm is on when the user switched it on, and also when any glucose alarm
     * (low, high, rapid fall) is on. Those alarms say nothing when the readings stop, so without it
     * a silent sensor would leave the user blind without knowing.
     */
    private fun missedReadingsAlarmWanted(): Boolean =
        preferences.get(BooleanKey.AlertMissedBgReading) ||
            preferences.get(BooleanKey.AlertHypo) ||
            preferences.get(BooleanKey.AlertHyper) ||
            preferences.get(BooleanKey.AlertRapidFall)

    override suspend fun checkStaleBGAlert() {
        val bgReading = persistenceLayer.getLastGlucoseValue() ?: return
        if (missedReadingsAlarmWanted()
            && bgReading.timestamp + missedReadingsThreshold() < dateUtil.now()
            && preferences.get(LocalAlertLongKey.NextMissedReadingsAlarm) < dateUtil.now()
        ) {
            preferences.put(LocalAlertLongKey.NextMissedReadingsAlarm, dateUtil.now() + missedReadingsThreshold())
            notificationManager.post(NotificationId.BG_READINGS_MISSED, CoreUiStrings.missed_bg_readings, sound = AlarmSound.ALARM)
            if (preferences.get(BooleanKey.NsClientCreateAnnouncementsFromErrors) && config.APS) {
                appScope.launch {
                    persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                        therapyEvent = TE.asAnnouncement(rh.gs(CoreUiStrings.missed_bg_readings)),
                        timestamp = dateUtil.now(),
                        action = Action.CAREPORTAL,
                        source = Sources.Aaps,
                        note = rh.gs(CoreUiStrings.missed_bg_readings),
                        listValues = listOf(ValueWithUnit.TEType(TE.Type.ANNOUNCEMENT))
                    )
                }
            }
        } else if (dateUtil.isOlderThan(bgReading.timestamp, 5).not()) {
            notificationManager.dismiss(NotificationId.BG_READINGS_MISSED)
        }
    }

    /**
     * Alarm threshold in mg/dL, the unit of every glucose value in this class.
     *
     * Reads the stored value with [Preferences.getRaw], not `get`: `get` converts to the display
     * units, so on an mmol/L phone a 250 mg/dL high threshold would come back as 13.9 and every
     * reading would look high. `convertToMgdlDetect` also accepts a value that was stored in mmol/L.
     */
    private fun thresholdMgdl(key: UnitDoubleKey): Double = profileUtil.convertToMgdlDetect(preferences.getRaw(key))

    /**
     * Two callers can run the check at the same time: the periodic pass and the "new reading" event.
     * Without the lock both could read the same "next alarm" time before either writes it, and post
     * the same alarm twice. [snoozeHypoAfterTreatment] takes it too.
     */
    private val glucoseAlertsLock = Mutex()

    /**
     * The newest reading the low and rapid fall alarms may use: not older than 15 min, and not
     * older than the missed readings limit when that one is shorter. An older low is not a low of
     * now. The high alarm keeps the missed readings limit alone.
     */
    private fun lowAndFallFreshLimit(): Long = min(LOW_AND_FALL_MAX_AGE_MS, missedReadingsThreshold())

    override suspend fun checkGlucoseAlerts() = glucoseAlertsLock.withLock {
        val last = persistenceLayer.getLastGlucoseValue() ?: return@withLock
        val now = dateUtil.now()
        val ageMs = now - last.timestamp
        // Never alarm on stale data: the missed readings alarm owns that case. A reading exactly at
        // a limit still counts as fresh.
        if (ageMs > missedReadingsThreshold()) return@withLock
        val age = rh.gs(CoreUiStrings.format_mins, T.msecs(ageMs).mins().toInt())
        val lowAndFallFresh = ageMs <= lowAndFallFreshLimit()

        if (lowAndFallFresh) checkHypoAlert(last.value, age, now)
        checkHyperAlert(last.value, age, now)
        if (lowAndFallFresh) checkRapidFallAlert(age, now)
    }

    /**
     * @param age how old the reading is, already formatted (for example "4 min"), for the message.
     */
    private fun checkHypoAlert(bgMgdl: Double, age: String, now: Long) {
        if (!preferences.get(BooleanKey.AlertHypo)) return
        val threshold = thresholdMgdl(UnitDoubleKey.AlertHypoThreshold)
        if (bgMgdl <= threshold) {
            val nextAlarm = preferences.get(LocalAlertLongKey.NextHypoAlarm)
            // The "hypo treated" hold is meant for a low that carbs are fixing. A low that keeps
            // falling is not being fixed, so it breaks the hold.
            val treatedAtMgdl = preferences.get(LocalAlertLongKey.HypoTreatedGlucose)
            val holdBroken = treatedAtMgdl > 0L && nextAlarm >= now && bgMgdl < treatedAtMgdl - HYPO_TREATED_BREAK_MGDL
            if (nextAlarm < now || holdBroken) {
                if (holdBroken) aapsLogger.debug(LTag.CORE, "Hypo treated hold broken: $bgMgdl mg/dL, was $treatedAtMgdl at the tap")
                preferences.put(LocalAlertLongKey.NextHypoAlarm, now + HYPO_REALARM_MS)
                preferences.put(LocalAlertLongKey.HypoTreatedGlucose, 0L)
                notificationManager.post(
                    NotificationId.BG_HYPO,
                    CoreUiStrings.alert_hypo_message.withArgs(profileUtil.fromMgdlToStringWithUnits(bgMgdl), age),
                    sound = AlarmSound.ALARM,
                    actions = listOf(
                        // "Hypo treated": the user took carbs, so hold the alarm long enough for them
                        // to work, instead of alarming again after the shorter HYPO_REALARM_MS.
                        NotificationAction(CoreUiStrings.alert_hypo_treated) { snoozeHypoAfterTreatment() },
                        // Plain snooze: close this alarm. It comes back after HYPO_REALARM_MS if
                        // glucose is still low.
                        NotificationAction(CoreUiStrings.snooze) { }
                    )
                )
            }
        } else if (bgMgdl >= threshold + HYPO_HYSTERESIS_MGDL) {
            notificationManager.dismiss(NotificationId.BG_HYPO)
            preferences.put(LocalAlertLongKey.NextHypoAlarm, 0L)
            preferences.put(LocalAlertLongKey.HypoTreatedGlucose, 0L)
        }
    }

    /**
     * "Hypo treated" action: hold the low alarm for [HYPO_TREATED_SNOOZE_MS] (about the time carbs
     * need to raise glucose) and clear the current alarm.
     *
     * This only silences. The alarm comes back by itself when the hold ends and glucose is still
     * low, and [checkHypoAlert] clears the hold as soon as glucose is back above the threshold.
     * The glucose at the tap is kept with the hold: a reading more than [HYPO_TREATED_BREAK_MGDL]
     * below it breaks the hold, because the low is then getting worse, not better.
     *
     * Runs under [glucoseAlertsLock] on the app scope, because the caller (a button tap) cannot wait.
     * Without the lock a check that runs at the moment of the tap could write its shorter "next
     * alarm" time after this one.
     */
    fun snoozeHypoAfterTreatment() {
        appScope.launch {
            glucoseAlertsLock.withLock {
                val atTapMgdl = persistenceLayer.getLastGlucoseValue()?.value?.roundToLong() ?: 0L
                preferences.put(LocalAlertLongKey.HypoTreatedGlucose, atTapMgdl)
                preferences.put(LocalAlertLongKey.NextHypoAlarm, dateUtil.now() + HYPO_TREATED_SNOOZE_MS)
                notificationManager.dismiss(NotificationId.BG_HYPO)
                aapsLogger.debug(LTag.CORE, "Hypo alarm snoozed after treatment for ${T.msecs(HYPO_TREATED_SNOOZE_MS).mins()} min")
            }
        }
    }

    /** @param age how old the reading is, already formatted, for the message. */
    private fun checkHyperAlert(bgMgdl: Double, age: String, now: Long) {
        if (!preferences.get(BooleanKey.AlertHyper)) return
        val threshold = thresholdMgdl(UnitDoubleKey.AlertHyperThreshold)
        if (bgMgdl >= threshold) {
            if (preferences.get(LocalAlertLongKey.NextHyperAlarm) < now) {
                preferences.put(LocalAlertLongKey.NextHyperAlarm, now + HYPER_REALARM_MS)
                notificationManager.post(
                    NotificationId.BG_HYPER,
                    CoreUiStrings.alert_hyper_message.withArgs(profileUtil.fromMgdlToStringWithUnits(bgMgdl), age)
                )
            }
        } else if (bgMgdl <= threshold - HYPER_HYSTERESIS_MGDL) {
            notificationManager.dismiss(NotificationId.BG_HYPER)
            preferences.put(LocalAlertLongKey.NextHyperAlarm, 0L)
        }
    }

    /**
     * The drop is the first reading of the window minus the last one. It is not the largest fall
     * inside the window: a dip that recovered before the last reading does not count.
     *
     * @param age how old the newest reading is, already formatted, for the message.
     */
    private suspend fun checkRapidFallAlert(age: String, now: Long) {
        if (!preferences.get(BooleanKey.AlertRapidFall)) return
        val windowMinutes = preferences.get(IntKey.AlertRapidFallWindow)
        val readings: List<GV> = persistenceLayer.getBgReadingsDataFromTime(now - T.mins(windowMinutes.toLong()).msecs(), ascending = true)
        // Need at least two readings in the window, otherwise a data gap makes the drop meaningless.
        if (readings.size < 2) return
        val drop = readings.first().value - readings.last().value
        // Always mg/dL, for both unit settings: the drop is a difference, not a glucose level.
        val dropThreshold = preferences.get(IntKey.AlertRapidFallDrop).toDouble()
        if (drop >= dropThreshold) {
            if (preferences.get(LocalAlertLongKey.NextRapidFallAlarm) < now) {
                preferences.put(LocalAlertLongKey.NextRapidFallAlarm, now + RAPID_FALL_REALARM_MS)
                notificationManager.post(
                    NotificationId.BG_RAPID_FALL,
                    CoreUiStrings.alert_rapid_fall_message.withArgs(profileUtil.fromMgdlToStringWithUnits(drop), windowMinutes, age),
                    sound = AlarmSound.ALARM
                )
            }
        } else if (drop <= dropThreshold / 2.0) {
            // The fall has flattened: clear and re-arm for the next fall.
            notificationManager.dismiss(NotificationId.BG_RAPID_FALL)
            preferences.put(LocalAlertLongKey.NextRapidFallAlarm, 0L)
        }
    }

    companion object {

        // While a condition lasts, post the same alarm again at most this often.
        private val HYPO_REALARM_MS = T.mins(15).msecs()

        /** Hold after the user said they treated a hypo: about how long carbs need to work. */
        private val HYPO_TREATED_SNOOZE_MS = T.mins(20).msecs()

        private val HYPER_REALARM_MS = T.mins(30).msecs()
        private val RAPID_FALL_REALARM_MS = T.mins(15).msecs()

        // Hysteresis (mg/dL): dismiss only once the value has clearly left the alarm zone.
        private const val HYPO_HYSTERESIS_MGDL = 5.0
        private const val HYPER_HYSTERESIS_MGDL = 10.0

        /** A reading this far (mg/dL) below the value at the "hypo treated" tap breaks the hold. */
        private const val HYPO_TREATED_BREAK_MGDL = 10.0

        /** The low and rapid fall alarms use no reading older than this. */
        private val LOW_AND_FALL_MAX_AGE_MS = T.mins(15).msecs()

        /** The longest each glucose alarm hold can really be, see [shortenSnoozeInterval]. */
        private val GLUCOSE_ALARM_MAX_HOLDS = listOf(
            LocalAlertLongKey.NextHypoAlarm to maxOf(HYPO_REALARM_MS, HYPO_TREATED_SNOOZE_MS),
            LocalAlertLongKey.NextHyperAlarm to HYPER_REALARM_MS,
            LocalAlertLongKey.NextRapidFallAlarm to RAPID_FALL_REALARM_MS,
        )
    }
}
