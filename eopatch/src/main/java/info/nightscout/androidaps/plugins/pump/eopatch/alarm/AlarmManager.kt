package info.nightscout.androidaps.plugins.pump.eopatch.alarm

import android.content.Context
import android.content.Intent
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueue
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode.*
import info.nightscout.androidaps.plugins.pump.eopatch.EONotification
import info.nightscout.androidaps.plugins.pump.eopatch.EoPatchRxBus
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPatchManager
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager
import info.nightscout.androidaps.plugins.pump.eopatch.code.AlarmCategory
import info.nightscout.androidaps.plugins.pump.eopatch.event.EventEoPatchAlarm
import info.nightscout.androidaps.plugins.pump.eopatch.ui.AlarmHelperActivity
import info.nightscout.androidaps.plugins.pump.eopatch.vo.Alarms
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface IAlarmManager {
    fun init()
    fun restartAll()
}

@Singleton
class AlarmManager @Inject constructor() : IAlarmManager {
    @Inject lateinit var patchManager: IPatchManager
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var sp: SP
    @Inject lateinit var context: Context

    @Inject lateinit var pm: IPreferenceManager
    @Inject lateinit var mAlarmRegistry: IAlarmRegistry

    private lateinit var mAlarmProcess: AlarmProcess

    private var compositeDisposable: CompositeDisposable = CompositeDisposable()

    @Inject
    fun onInit() {
        mAlarmProcess = AlarmProcess(patchManager, rxBus)
    }

    override fun init(){
        EoPatchRxBus.listen(EventEoPatchAlarm::class.java)
            .map { it -> it.alarmCodes }
            .doOnNext { aapsLogger.info(LTag.PUMP,"EventEoPatchAlarm Received") }
            .concatMap {
                Observable.fromArray(it)
                    .observeOn(Schedulers.io())
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .doOnNext { alarmCodes ->
                        alarmCodes.forEach {
                            aapsLogger.info(LTag.PUMP,"alarmCode: ${it.name}")
                            val valid = isValid(it)
                            if (valid) {
                                if (it.alarmCategory == AlarmCategory.ALARM || it == B012) {
                                    showAlarmDialog(it)
                                } else {
                                    showNotification(it)
                                }

                                updateState(it, AlarmState.FIRED)
                            }else{
                                updateState(it, AlarmState.HANDLE)
                            }
                        }
                    }

            }
            .subscribe({}, { throwable: Throwable -> fabricPrivacy.logException(throwable) })
    }

    override fun restartAll() {
        val now = System.currentTimeMillis()
        val occuredAlarm = pm.getAlarms().occured.clone() as HashMap<AlarmCode, Alarms.AlarmItem>
        val registeredAlarm = pm.getAlarms().registered.clone() as HashMap<AlarmCode, Alarms.AlarmItem>
        compositeDisposable.clear()
        if(occuredAlarm.isNotEmpty()){
            EoPatchRxBus.publish(EventEoPatchAlarm(occuredAlarm.keys))
        }

        if(registeredAlarm.isNotEmpty()){
            registeredAlarm.forEach { raEntry ->
                compositeDisposable.add(
                    mAlarmRegistry.add(raEntry.key, Math.max(OS_REGISTER_GAP, raEntry.value.triggerTimeMilli - now))
                        .subscribe()
                )
            }
        }
    }

    private fun isValid(code: AlarmCode): Boolean{
        return when(code){
            A005, A016, A020, B012 -> {
                aapsLogger.info(LTag.PUMP,"Is ${code} valid? ${pm.getPatchConfig().hasMacAddress() && pm.getPatchConfig().lifecycleEvent.isSubStepRunning}")
                pm.getPatchConfig().hasMacAddress() && pm.getPatchConfig().lifecycleEvent.isSubStepRunning
            }
            else -> {
                aapsLogger.info(LTag.PUMP,"Is ${code} valid? ${pm.getPatchConfig().isActivated}")
                pm.getPatchConfig().isActivated
            }
        }
    }

    private fun showAlarmDialog(alarmCode: AlarmCode){
        val i = Intent(context, AlarmHelperActivity::class.java)
        i.putExtra("soundid", R.raw.error)
        i.putExtra("code", alarmCode.name)
        i.putExtra("status", resourceHelper.gs(alarmCode.resId))
        i.putExtra("title", resourceHelper.gs(R.string.string_alarm))
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    private fun showNotification(alarmCode: AlarmCode, timeOffset: Long = 0L){
        var occurredTimestamp: Long = pm.getPatchConfig().patchWakeupTimestamp + TimeUnit.SECONDS.toMillis(timeOffset)
        val notification = EONotification(Notification.EOELOW_PATCH_ALERTS + (alarmCode.aeCode + 10000), resourceHelper.gs(alarmCode.resId), Notification.URGENT)

        notification.action(R.string.confirm) {
            Single.just(isValid(alarmCode))
                .flatMap { isValid ->
                    return@flatMap if(isValid) mAlarmProcess.doAction(context, alarmCode)
                    else Single.just(IAlarmProcess.ALARM_HANDLED)
                }
                .subscribe { ret ->
                    if(ret == IAlarmProcess.ALARM_HANDLED){
                        updateState(alarmCode, AlarmState.HANDLE)
                    }else{
                        rxBus.send(EventNewNotification(notification))
                    }
                }
        }
        notification.soundId = R.raw.error
        notification.date = occurredTimestamp
        rxBus.send(EventNewNotification(notification))
    }

    private fun updateState(alarmCode: AlarmCode, state: AlarmState){
        when(state){
            AlarmState.REGISTER -> pm.getAlarms().register(alarmCode, 0)
            AlarmState.FIRED    -> pm.getAlarms().occured(alarmCode)
            AlarmState.HANDLE   -> pm.getAlarms().handle(alarmCode)
        }
        pm.flushAlarms()
    }

    companion object {

        private const val OS_REGISTER_GAP = 3 * 1000L
    }
}