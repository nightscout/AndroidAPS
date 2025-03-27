package app.aaps.pump.eopatch.alarm

import android.content.Context
import android.content.Intent
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
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
import app.aaps.pump.eopatch.ui.AlarmHelperActivity
import app.aaps.pump.eopatch.vo.Alarms
import app.aaps.pump.eopatch.vo.PatchConfig
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import java.text.SimpleDateFormat
import java.util.Locale
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
    @Inject lateinit var context: Context
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var pm: PreferenceManager
    @Inject lateinit var patchManagerExecutor: PatchManagerExecutor
    @Inject lateinit var mAlarmRegistry: IAlarmRegistry
    @Inject lateinit var patchConfig: PatchConfig
    @Inject lateinit var alarms: Alarms

    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var pumpSync: PumpSync

    private lateinit var mAlarmProcess: AlarmProcess

    private var compositeDisposable: CompositeDisposable = CompositeDisposable()
    private var alarmDisposable: Disposable? = null

    @Suppress("unused")
    @Inject
    fun onInit() {
        mAlarmProcess = AlarmProcess(patchManager, patchManagerExecutor, rxBus)
    }

    override fun init() {
        alarmDisposable = EoPatchRxBus.listen(EventEoPatchAlarm::class.java)
            .map { it.alarmCodes }
            .doOnNext { aapsLogger.info(LTag.PUMP, "EventEoPatchAlarm Received") }
            .concatMap {
                Observable.fromArray(it)
                    .observeOn(aapsSchedulers.io)
                    .subscribeOn(aapsSchedulers.main)
                    .doOnNext { alarmCodes ->
                        alarmCodes.forEach { alarmCode ->
                            aapsLogger.info(LTag.PUMP, "alarmCode: ${alarmCode.name}")
                            val valid = isValid(alarmCode)
                            if (valid) {
                                if (alarmCode.alarmCategory == AlarmCategory.ALARM || alarmCode == B012) {
                                    showAlarmDialog(alarmCode)
                                } else {
                                    showNotification(alarmCode)
                                }

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

    private fun showAlarmDialog(alarmCode: AlarmCode) {
        val i = Intent(context, AlarmHelperActivity::class.java)
        i.putExtra("soundid", app.aaps.core.ui.R.raw.error)
        i.putExtra("code", alarmCode.name)
        i.putExtra("status", resourceHelper.gs(alarmCode.resId))
        i.putExtra("title", resourceHelper.gs(R.string.string_alarm))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    private fun showNotification(alarmCode: AlarmCode) {
        var alarmMsg = resourceHelper.gs(alarmCode.resId)
        if (alarmCode == B000) {
            val expireTimeValue = pm.getPatchWakeupTimestamp() + TimeUnit.HOURS.toMillis(84)
            val expireTimeString = SimpleDateFormat(resourceHelper.gs(R.string.date_format_yyyy_m_d_e_a_hh_mm_comma), Locale.US).format(expireTimeValue)
            alarmMsg = resourceHelper.gs(alarmCode.resId, expireTimeString)
        }
        uiInteraction.addNotificationWithAction(
            id = Notification.EOFLOW_PATCH_ALERTS + (alarmCode.aeCode + 10000),
            text = alarmMsg,
            level = Notification.URGENT,
            buttonText = (alarmCode == B001).takeOne(R.string.string_resume, R.string.confirm),
            action = {
                compositeDisposable.add(
                    Single.just(isValid(alarmCode))
                        .observeOn(aapsSchedulers.main)  //don't remove
                        .flatMap { isValid ->
                            return@flatMap if (isValid) mAlarmProcess.doAction(context, alarmCode)
                            else Single.just(IAlarmProcess.ALARM_HANDLED)
                        }
                        .subscribe { ret ->
                            when (ret) {
                                IAlarmProcess.ALARM_HANDLED -> {
                                    if (alarmCode == B001) {
                                        pumpSync.syncStopTemporaryBasalWithPumpId(
                                            timestamp = dateUtil.now(),
                                            endPumpId = dateUtil.now(),
                                            pumpType = PumpType.EOFLOW_EOPATCH2,
                                            pumpSerial = patchConfig.patchSerialNumber
                                        )
                                    }
                                    updateState(alarmCode, AlarmState.HANDLE)
                                }

                                IAlarmProcess.ALARM_HANDLED_BUT_NEED_STOP_BEEP -> {
                                    alarms.needToStopBeep.add(alarmCode)
                                    updateState(alarmCode, AlarmState.HANDLE)
                                }

                                else -> showNotification(alarmCode)
                            }
                        }
                )
            },
            validityCheck = null,
            soundId = app.aaps.core.ui.R.raw.error,
            date = alarms.getOccuredAlarmTimestamp(alarmCode)
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