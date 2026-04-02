package app.aaps.pump.eopatch.alarm

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.pump.eopatch.EoPatchRxBus
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.alarm.AlarmCode.A005
import app.aaps.pump.eopatch.alarm.AlarmCode.A016
import app.aaps.pump.eopatch.alarm.AlarmCode.A020
import app.aaps.pump.eopatch.alarm.AlarmCode.B000
import app.aaps.pump.eopatch.alarm.AlarmCode.B001
import app.aaps.pump.eopatch.alarm.AlarmCode.B012
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.code.AlarmCategory
import app.aaps.pump.eopatch.event.EventEoPatchAlarm
import app.aaps.pump.eopatch.extension.takeOne
import app.aaps.pump.eopatch.vo.Alarms
import app.aaps.pump.eopatch.vo.PatchConfig
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class AlarmManager @Inject constructor() : IAlarmManager {

    @Inject lateinit var patchManager: IPatchManager
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var pm: PreferenceManager
    @Inject lateinit var patchManagerExecutor: PatchManagerExecutor
    @Inject lateinit var mAlarmRegistry: IAlarmRegistry
    @Inject lateinit var patchConfig: PatchConfig
    @Inject lateinit var alarms: Alarms
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var pumpSync: PumpSync

    private lateinit var mAlarmProcess: AlarmProcess
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var compositeDisposable: CompositeDisposable = CompositeDisposable()
    private var alarmDisposable: Disposable? = null

    @Suppress("unused")
    @Inject
    fun onInit() {
        mAlarmProcess = AlarmProcess(patchManager, patchManagerExecutor)
    }

    override fun init() {
        alarmDisposable = EoPatchRxBus.listen(EventEoPatchAlarm::class.java)
            .map { it.alarmCodes }
            .doOnNext { aapsLogger.info(LTag.PUMP, "EventEoPatchAlarm Received") }
            .concatMap {
                Observable.fromArray(it)
                    .subscribeOn(aapsSchedulers.io)
                    .doOnNext { alarmCodes ->
                        alarmCodes.forEach { alarmCode ->
                            aapsLogger.info(LTag.PUMP, "alarmCode: ${alarmCode.name}")
                            val valid = isValid(alarmCode)
                            if (valid) {
                                val isCritical = alarmCode.alarmCategory == AlarmCategory.ALARM || alarmCode == B012
                                showNotification(alarmCode, isCritical)
                                updateState(alarmCode, AlarmState.FIRED)
                            } else {
                                updateState(alarmCode, AlarmState.HANDLE)
                            }
                        }
                    }
            }
            .subscribe({}, { throwable: Throwable -> fabricPrivacy.logException(throwable) })
    }

    override fun restartAll() {
        val now = System.currentTimeMillis()

        @Suppress("UNCHECKED_CAST")
        val occurredAlarm = alarms.occurred.clone() as HashMap<AlarmCode, Alarms.AlarmItem>

        @Suppress("UNCHECKED_CAST")
        val registeredAlarm = alarms.registered.clone() as HashMap<AlarmCode, Alarms.AlarmItem>
        compositeDisposable.clear()
        if (occurredAlarm.isNotEmpty()) {
            EoPatchRxBus.publish(EventEoPatchAlarm(occurredAlarm.keys))
        }

        if (registeredAlarm.isNotEmpty()) {
            registeredAlarm.forEach { raEntry ->
                compositeDisposable.add(
                    mAlarmRegistry.add(raEntry.key, max(OS_REGISTER_GAP, raEntry.value.triggerTimeMilli - now))
                        .subscribe()
                )
            }
        }
    }

    private fun isValid(code: AlarmCode): Boolean {
        return when (code) {
            A005, A016, A020, B012 -> {
                aapsLogger.info(LTag.PUMP, "Is $code valid? ${patchConfig.hasMacAddress() && patchConfig.lifecycleEvent.isSubStepRunning}")
                patchConfig.hasMacAddress() && patchConfig.lifecycleEvent.isSubStepRunning
            }

            else                   -> {
                aapsLogger.info(LTag.PUMP, "Is $code valid? ${patchConfig.isActivated}")
                patchConfig.isActivated
            }
        }
    }

    private fun showNotification(alarmCode: AlarmCode, isCritical: Boolean = false) {
        var alarmMsg = resourceHelper.gs(alarmCode.resId)
        if (alarmCode == B000) {
            val expireTimeValue = pm.getPatchWakeupTimestamp() + TimeUnit.HOURS.toMillis(84)
            alarmMsg = resourceHelper.gs(alarmCode.resId, dateUtil.dateAndTimeString(expireTimeValue))
        }

        // Critical alarms trigger the global alarm sound overlay
        if (isCritical) {
            uiInteraction.runAlarm(alarmMsg, resourceHelper.gs(app.aaps.core.ui.R.string.alarm), app.aaps.core.ui.R.raw.error)
        }

        notificationManager.post(
            id = NotificationId.EOFLOW_PATCH_ALERT,
            text = alarmMsg,
            level = if (isCritical) NotificationLevel.URGENT else NotificationLevel.INFO,
            date = alarms.getOccuredAlarmTimestamp(alarmCode),
            soundRes = if (!isCritical) app.aaps.core.ui.R.raw.error else null,
            actions = listOf(NotificationAction(
                when (alarmCode) {
                    B001            -> app.aaps.core.ui.R.string.pump_resume
                    AlarmCode.A007  -> app.aaps.core.ui.R.string.retry
                    else            -> app.aaps.core.ui.R.string.confirm
                }
            ) {
                compositeDisposable.add(
                    Single.just(isValid(alarmCode))
                        .subscribeOn(aapsSchedulers.io)
                        .observeOn(aapsSchedulers.io)
                        .flatMap { isValid ->
                            return@flatMap if (isValid) mAlarmProcess.doAction(alarmCode)
                            else Single.just(IAlarmProcess.ALARM_HANDLED)
                        }
                        .subscribe { ret ->
                            when (ret) {
                                IAlarmProcess.ALARM_HANDLED                    -> {
                                    if (alarmCode == B001) {
                                        scope.launch {
                                            pumpSync.syncStopTemporaryBasalWithPumpId(
                                                timestamp = dateUtil.now(),
                                                endPumpId = dateUtil.now(),
                                                pumpType = PumpType.EOFLOW_EOPATCH2,
                                                pumpSerial = patchConfig.patchSerialNumber
                                            )
                                        }
                                    }
                                    if (alarmCode.alarmCategory == AlarmCategory.ALARM) {
                                        pm.flushPatchConfig()
                                    }
                                    updateState(alarmCode, AlarmState.HANDLE)
                                }

                                IAlarmProcess.ALARM_HANDLED_BUT_NEED_STOP_BEEP -> {
                                    alarms.needToStopBeep.add(alarmCode)
                                    updateState(alarmCode, AlarmState.HANDLE)
                                }

                                else                                           -> showNotification(alarmCode)
                            }
                        }
                )
            })
        )
    }

    private fun updateState(alarmCode: AlarmCode, state: AlarmState) {
        when (state) {
            AlarmState.REGISTER -> alarms.register(alarmCode, 0)
            AlarmState.FIRED    -> alarms.occurred(alarmCode)
            AlarmState.HANDLE   -> alarms.handle(alarmCode)
        }
        pm.flushAlarms()
    }

    companion object {

        private const val OS_REGISTER_GAP = 3 * 1000L
    }
}