package info.nightscout.androidaps.plugins.pump.eopatch.alarm

import android.app.AlarmManager
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode.Companion.getUri
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.eopatch.event.EventEoPatchAlarm
import android.app.PendingIntent
import android.app.AlarmManager.AlarmClockInfo
import android.content.Context
import android.content.Intent
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.eopatch.EoPatchRxBus
import info.nightscout.androidaps.plugins.pump.eopatch.OsAlarmReceiver
import info.nightscout.androidaps.plugins.pump.eopatch.code.PatchLifecycle
import info.nightscout.androidaps.plugins.pump.eopatch.core.code.PatchAeCode
import info.nightscout.androidaps.plugins.pump.eopatch.extension.observeOnMainThread
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface IAlarmRegistry {
    fun add(alarmCode: AlarmCode, triggerAfter: Long, isFirst: Boolean = false): Maybe<AlarmCode>
    fun add(patchAeCodes: Set<PatchAeCode>)
    fun remove(alarmKey: AlarmCode): Maybe<AlarmCode>
}

@Singleton
class AlarmRegistry @Inject constructor() : IAlarmRegistry {
    @Inject lateinit var mContext: Context
    @Inject lateinit var pm: IPreferenceManager
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger

    private lateinit var mOsAlarmManager: AlarmManager
    private var mDisposable: Disposable? = null
    private var compositeDisposable: CompositeDisposable = CompositeDisposable()

    @Inject fun onInit() {
        mOsAlarmManager = mContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        mDisposable = pm.observePatchLifeCycle()
            .observeOnMainThread()
            .subscribe {
                when(it){
                    PatchLifecycle.REMOVE_NEEDLE_CAP -> {
                        val triggerAfter = pm.getPatchConfig().patchWakeupTimestamp + TimeUnit.HOURS.toMillis(1) - System.currentTimeMillis()
                        compositeDisposable.add(add(AlarmCode.A020, triggerAfter).subscribe())
                    }
                    PatchLifecycle.ACTIVATED -> {

                    }
                    PatchLifecycle.SHUTDOWN -> {
                        val sources = ArrayList<Maybe<*>>()
                        sources.add(Maybe.just(true))
                        pm.getAlarms().occured.let{
                            if(it.isNotEmpty()){
                                it.keys.forEach {
                                    sources.add(
                                        Maybe.just(it)
                                            .observeOnMainThread()
                                            .doOnSuccess { rxBus.send(EventDismissNotification(Notification.EOELOW_PATCH_ALERTS + (it.aeCode + 10000))) }
                                    )
                                }
                            }
                        }
                        pm.getAlarms().registered.let{
                            if(it.isNotEmpty()){
                                it.keys.forEach {
                                    sources.add(remove(it))
                                }
                            }
                        }
                        Maybe.concat(sources)
                            .subscribe {
                                pm.getAlarms().clear()
                                pm.flushAlarms()
                            }
                    }

                    else -> Unit
                }
            }
    }

    override fun add(alarmCode: AlarmCode, triggerAfter: Long, isFirst: Boolean): Maybe<AlarmCode> {
        if(pm.getAlarms().occured.containsKey(alarmCode)){
            return Maybe.just(alarmCode)
        }else {
            val triggerTimeMilli = System.currentTimeMillis() + triggerAfter
            pm.getAlarms().register(alarmCode, triggerAfter)
            pm.flushAlarms()
            if (triggerAfter <= 0L) {
                EoPatchRxBus.publish(EventEoPatchAlarm(HashSet<AlarmCode>().apply { add(alarmCode) }, isFirst))
                return Maybe.just(alarmCode)
            }
            return registerOsAlarm(alarmCode, triggerTimeMilli)
        }
    }

    override fun add(patchAeCodes: Set<PatchAeCode>) {
        compositeDisposable.add(
            Observable.fromIterable(patchAeCodes)
               .filter{patchAeCodeItem ->  AlarmCode.Companion.findByPatchAeCode(patchAeCodeItem.getAeValue()) != null}
               .observeOn(AndroidSchedulers.mainThread())
               .filter { patchAeCodes -> AlarmCode.findByPatchAeCode(patchAeCodes.getAeValue()) != null }
               .flatMapMaybe{aeCodeResponse -> add(AlarmCode.findByPatchAeCode(aeCodeResponse.getAeValue())!!,0L, true)}
               .subscribe()
        )
    }

    private fun registerOsAlarm(alarmCode: AlarmCode, triggerTime: Long): Maybe<AlarmCode> {
        return Maybe.fromCallable {
            cancelOsAlarmInternal(alarmCode)
            val pendingIntent = createPendingIntent(alarmCode, 0)
            val now = System.currentTimeMillis()
            mOsAlarmManager.setAlarmClock(AlarmClockInfo(triggerTime, pendingIntent), pendingIntent)
            alarmCode
        }
    }

    override fun remove(alarmCode: AlarmCode): Maybe<AlarmCode> {
        if(pm.getAlarms().registered.containsKey(alarmCode)) {
            return cancelOsAlarms(alarmCode)
                .doOnSuccess {
                    pm.getAlarms().unregister(alarmCode)
                    pm.flushAlarms()
                }
                .map { integer: Int? -> alarmCode }
        }else{
            return Maybe.just(alarmCode)
        }
    }

    private fun cancelOsAlarms(vararg alarmCodes: AlarmCode): Maybe<Int> {
        return Observable.fromArray(*alarmCodes)
            .map(this::cancelOsAlarmInternal)
            .reduce(Integer::sum)
    }

    private fun cancelOsAlarmInternal(alarmCode: AlarmCode): Int {
        val old = createPendingIntent(alarmCode, PendingIntent.FLAG_NO_CREATE)
        return if (old != null) {
            mOsAlarmManager.cancel(old)
            old.cancel()
            aapsLogger.debug("[${alarmCode}] OS Alarm canceled.")
            1
        } else {
            aapsLogger.debug("[${alarmCode}] OS Alarm not canceled, not registered.")
            0
        }
    }

    private fun createPendingIntent(alarmCode: AlarmCode, flag: Int): PendingIntent {
        val intent = Intent(mContext, OsAlarmReceiver::class.java).setData(getUri(alarmCode))
        return PendingIntent.getBroadcast(mContext, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }
}