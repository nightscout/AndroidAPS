package info.nightscout.implementation

import info.nightscout.core.events.EventNewNotification
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.InsertTherapyEventAnnouncementTransaction
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Constants
import info.nightscout.interfaces.LocalAlertUtils
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.smsCommunicator.SmsCommunicator
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventDismissNotification
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Created by adrian on 17/12/17.
 */
@Singleton
class LocalAlertUtilsImpl @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val rxBus: RxBus,
    private val rh: ResourceHelper,
    private val activePlugin: ActivePlugin,
    private val profileFunction: ProfileFunction,
    private val smsCommunicator: SmsCommunicator,
    private val config: Config,
    private val repository: AppRepository,
    private val dateUtil: DateUtil,
    private val uel: UserEntryLogger
) : LocalAlertUtils {

    private val disposable = CompositeDisposable()

    private fun missedReadingsThreshold(): Long {
        return T.mins(sp.getInt(info.nightscout.core.utils.R.string.key_missed_bg_readings_threshold_minutes, Constants.DEFAULT_MISSED_BG_READINGS_THRESHOLD_MINUTES).toLong()).msecs()
    }

    private fun pumpUnreachableThreshold(): Long {
        return T.mins(sp.getInt(info.nightscout.core.utils.R.string.key_pump_unreachable_threshold_minutes, Constants.DEFAULT_PUMP_UNREACHABLE_THRESHOLD_MINUTES).toLong()).msecs()
    }

    override fun checkPumpUnreachableAlarm(lastConnection: Long, isStatusOutdated: Boolean, isDisconnected: Boolean) {
        val alarmTimeoutExpired = isAlarmTimeoutExpired(lastConnection, pumpUnreachableThreshold())
        val nextAlarmOccurrenceReached = sp.getLong("nextPumpDisconnectedAlarm", 0L) < System.currentTimeMillis()
        if (config.APS && isStatusOutdated && alarmTimeoutExpired && nextAlarmOccurrenceReached && !isDisconnected) {
            if (sp.getBoolean(info.nightscout.core.utils.R.string.key_enable_pump_unreachable_alert, true)) {
                aapsLogger.debug(LTag.CORE, "Generating pump unreachable alarm. lastConnection: " + dateUtil.dateAndTimeString(lastConnection) + " isStatusOutdated: " + isStatusOutdated)
                sp.putLong("nextPumpDisconnectedAlarm", System.currentTimeMillis() + pumpUnreachableThreshold())
                rxBus.send(EventNewNotification(Notification(Notification.PUMP_UNREACHABLE, rh.gs(info.nightscout.core.ui.R.string.pump_unreachable), Notification.URGENT).also { it.soundId = info.nightscout.core.ui.R.raw.alarm }))
                uel.log(Action.CAREPORTAL, Sources.Aaps, rh.gs(info.nightscout.core.ui.R.string.pump_unreachable), ValueWithUnit.TherapyEventType(TherapyEvent.Type.ANNOUNCEMENT))
                if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_create_announcements_from_errors, true))
                    disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(rh.gs(info.nightscout.core.ui.R.string.pump_unreachable))).subscribe()
            }
            if (sp.getBoolean(info.nightscout.core.utils.R.string.key_smscommunicator_report_pump_unreachable, true))
                smsCommunicator.sendNotificationToAllNumbers(rh.gs(info.nightscout.core.ui.R.string.pump_unreachable))
        }
        if (!isStatusOutdated && !alarmTimeoutExpired) rxBus.send(EventDismissNotification(Notification.PUMP_UNREACHABLE))
    }

    private fun isAlarmTimeoutExpired(lastConnection: Long, unreachableThreshold: Long): Boolean {
        return if (activePlugin.activePump.pumpDescription.hasCustomUnreachableAlertCheck) {
            activePlugin.activePump.isUnreachableAlertTimeoutExceeded(unreachableThreshold)
        } else {
            lastConnection + pumpUnreachableThreshold() < System.currentTimeMillis()
        }
    }

    /*Pre-snoozes the alarms with 5 minutes if no snooze exists.
     * Call only at startup!
     */
    override fun preSnoozeAlarms() {
        if (sp.getLong("nextMissedReadingsAlarm", 0L) < System.currentTimeMillis()) {
            sp.putLong("nextMissedReadingsAlarm", System.currentTimeMillis() + 5 * 60 * 1000)
        }
        if (sp.getLong("nextPumpDisconnectedAlarm", 0L) < System.currentTimeMillis()) {
            sp.putLong("nextPumpDisconnectedAlarm", System.currentTimeMillis() + 5 * 60 * 1000)
        }
    }

    override fun shortenSnoozeInterval() { //shortens alarm times in case of setting changes or future data
        var nextMissedReadingsAlarm = sp.getLong("nextMissedReadingsAlarm", 0L)
        nextMissedReadingsAlarm = min(System.currentTimeMillis() + missedReadingsThreshold(), nextMissedReadingsAlarm)
        sp.putLong("nextMissedReadingsAlarm", nextMissedReadingsAlarm)
        var nextPumpDisconnectedAlarm = sp.getLong("nextPumpDisconnectedAlarm", 0L)
        nextPumpDisconnectedAlarm = min(System.currentTimeMillis() + pumpUnreachableThreshold(), nextPumpDisconnectedAlarm)
        sp.putLong("nextPumpDisconnectedAlarm", nextPumpDisconnectedAlarm)
    }

    override fun notifyPumpStatusRead() { //TODO: persist the actual time the pump is read and simplify the whole logic when to alarm
        val pump = activePlugin.activePump
        val profile = profileFunction.getProfile()
        if (profile != null) {
            val lastConnection = pump.lastDataTime()
            val earliestAlarmTime = lastConnection + pumpUnreachableThreshold()
            if (sp.getLong("nextPumpDisconnectedAlarm", 0L) < earliestAlarmTime) {
                sp.putLong("nextPumpDisconnectedAlarm", earliestAlarmTime)
            }
        }
    }

    override fun checkStaleBGAlert() {
        val bgReadingWrapped = repository.getLastGlucoseValueWrapped().blockingGet()
        val bgReading = if (bgReadingWrapped is ValueWrapper.Existing) bgReadingWrapped.value else return
        if (sp.getBoolean(info.nightscout.core.utils.R.string.key_enable_missed_bg_readings_alert, false)
            && bgReading.timestamp + missedReadingsThreshold() < System.currentTimeMillis()
            && sp.getLong("nextMissedReadingsAlarm", 0L) < System.currentTimeMillis()
        ) {
            val n = Notification(Notification.BG_READINGS_MISSED, rh.gs(info.nightscout.core.ui.R.string.missed_bg_readings), Notification.URGENT)
            n.soundId = info.nightscout.core.ui.R.raw.alarm
            sp.putLong("nextMissedReadingsAlarm", System.currentTimeMillis() + missedReadingsThreshold())
            rxBus.send(EventNewNotification(n))
            uel.log(Action.CAREPORTAL, Sources.Aaps, rh.gs(info.nightscout.core.ui.R.string.missed_bg_readings), ValueWithUnit.TherapyEventType(TherapyEvent.Type.ANNOUNCEMENT))
            if (sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_create_announcements_from_errors, true)) {
                disposable += repository.runTransaction(InsertTherapyEventAnnouncementTransaction(n.text)).subscribe()
            }
        } else if (dateUtil.isOlderThan(bgReading.timestamp, 5).not()) {
            rxBus.send(EventDismissNotification(Notification.BG_READINGS_MISSED))
        }
    }
}