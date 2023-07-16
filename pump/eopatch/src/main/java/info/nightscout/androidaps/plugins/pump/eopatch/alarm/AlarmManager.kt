package info.nightscout.androidaps.plugins.pump.eopatch.alarm

import android.content.Context
import android.content.Intent
import info.nightscout.androidaps.plugins.pump.eopatch.EoPatchRxBus
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode.A005
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode.A016
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode.A020
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode.B000
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode.B001
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode.B012
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPatchManager
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager
import info.nightscout.androidaps.plugins.pump.eopatch.code.AlarmCategory
import info.nightscout.androidaps.plugins.pump.eopatch.event.EventEoPatchAlarm
import info.nightscout.androidaps.plugins.pump.eopatch.extension.takeOne
import info.nightscout.androidaps.plugins.pump.eopatch.ui.AlarmHelperActivity
import info.nightscout.androidaps.plugins.pump.eopatch.vo.Alarms
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.notifications.Notification
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
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

interface IAlarmManager {
    fun init()
    fun restartAll()
}

@Singleton
class AlarmManager @Inject constructor() : IAlarmManager {
    @Inject lateinit var patchManager: IPatchManager
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var sp: SP
    @Inject lateinit var context: Context
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var pm: IPreferenceManager
    @Inject lateinit var mAlarmRegistry: IAlarmRegistry

    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var pumpSync: PumpSync

    private lateinit var mAlarmProcess: AlarmProcess

    private var compositeDisposable: CompositeDisposable = CompositeDisposable()
    private var alarmDisposable: Disposable? = null

    @Suppress("unused")
    @Inject
    fun onInit() {
        mAlarmProcess = AlarmProcess(patchManager, rxBus)
    }

    override fun init(){
        alarmDisposable = EoPatchRxBus.listen(EventEoPatchAlarm::class.java)
            .map { it.alarmCodes }
            .doOnNext { aapsLogger.info(LTag.PUMP,"EventEoPatchAlarm Received") }
            .concatMap {
                Observable.fromArray(it)
                    .observeOn(aapsSchedulers.io)
                    .subscribeOn(aapsSchedulers.main)
                    .doOnNext { alarmCodes ->
                        alarmCodes.forEach { alarmCode ->
                            aapsLogger.info(LTag.PUMP,"alarmCode: ${alarmCode.name}")
                            val valid = isValid(alarmCode)
                            if (valid) {
                                if (alarmCode.alarmCategory == AlarmCategory.ALARM || alarmCode == B012) {
                                    showAlarmDialog(alarmCode)
                                } else {
                                    showNotification(alarmCode)
                                }

                                updateState(alarmCode, AlarmState.FIRED)
                            }else{
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
        val occurredAlarm= pm.getAlarms().occurred.clone() as HashMap<AlarmCode, Alarms.AlarmItem>
        @Suppress("UNCHECKED_CAST")
        val registeredAlarm = pm.getAlarms().registered.clone() as HashMap<AlarmCode, Alarms.AlarmItem>
        compositeDisposable.clear()
        if(occurredAlarm.isNotEmpty()){
            EoPatchRxBus.publish(EventEoPatchAlarm(occurredAlarm.keys))
        }

        if(registeredAlarm.isNotEmpty()){
            registeredAlarm.forEach { raEntry ->
                compositeDisposable.add(
                    mAlarmRegistry.add(raEntry.key, max(OS_REGISTER_GAP, raEntry.value.triggerTimeMilli - now))
                        .subscribe()
                )
            }
        }
    }

    private fun isValid(code: AlarmCode): Boolean{
        return when(code){
            A005, A016, A020, B012 -> {
                aapsLogger.info(LTag.PUMP,"Is $code valid? ${pm.getPatchConfig().hasMacAddress() && pm.getPatchConfig().lifecycleEvent.isSubStepRunning}")
                pm.getPatchConfig().hasMacAddress() && pm.getPatchConfig().lifecycleEvent.isSubStepRunning
            }
            else -> {
                aapsLogger.info(LTag.PUMP,"Is $code valid? ${pm.getPatchConfig().isActivated}")
                pm.getPatchConfig().isActivated
            }
        }
    }

    private fun showAlarmDialog(alarmCode: AlarmCode){
        val i = Intent(context, AlarmHelperActivity::class.java)
        i.putExtra("soundid", info.nightscout.core.ui.R.raw.error)
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
            id = Notification.EOELOW_PATCH_ALERTS + (alarmCode.aeCode + 10000),
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
                                IAlarmProcess.ALARM_HANDLED                    -> {
                                    if (alarmCode == B001) {
                                        pumpSync.syncStopTemporaryBasalWithPumpId(
                                            timestamp = dateUtil.now(),
                                            endPumpId = dateUtil.now(),
                                            pumpType = PumpType.EOFLOW_EOPATCH2,
                                            pumpSerial = patchManager.patchConfig.patchSerialNumber
                                        )
                                    }
                                    updateState(alarmCode, AlarmState.HANDLE)
                                }

                                IAlarmProcess.ALARM_HANDLED_BUT_NEED_STOP_BEEP -> {
                                    pm.getAlarms().needToStopBeep.add(alarmCode)
                                    updateState(alarmCode, AlarmState.HANDLE)
                                }

                                else                                           -> showNotification(alarmCode)
                            }
                        }
                )
            },
            soundId = info.nightscout.core.ui.R.raw.error,
            date = pm.getAlarms().getOccuredAlarmTimestamp(alarmCode)
        )

    }

    private fun updateState(alarmCode: AlarmCode, state: AlarmState){
        when(state){
            AlarmState.REGISTER -> pm.getAlarms().register(alarmCode, 0)
            AlarmState.FIRED    -> pm.getAlarms().occurred(alarmCode)
            AlarmState.HANDLE   -> pm.getAlarms().handle(alarmCode)
        }
        pm.flushAlarms()
    }

    companion object {

        private const val OS_REGISTER_GAP = 3 * 1000L
    }
}