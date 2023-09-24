package info.nightscout.androidaps.plugins.pump.eopatch.alarm

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.utils.DateUtil
import info.nightscout.androidaps.plugins.pump.eopatch.EoPatchRxBus
import info.nightscout.androidaps.plugins.pump.eopatch.OsAlarmReceiver
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode.Companion.getUri
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager
import info.nightscout.androidaps.plugins.pump.eopatch.code.PatchLifecycle
import info.nightscout.androidaps.plugins.pump.eopatch.core.code.PatchAeCode
import info.nightscout.androidaps.plugins.pump.eopatch.event.EventEoPatchAlarm
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface IAlarmRegistry {

    fun add(alarmCode: AlarmCode, triggerAfter: Long, isFirst: Boolean = false): Maybe<AlarmCode>
    fun add(patchAeCodes: Set<PatchAeCode>)
    fun remove(alarmCode: AlarmCode): Maybe<AlarmCode>
}

@Singleton
class AlarmRegistry @Inject constructor() : IAlarmRegistry {

    @Inject lateinit var mContext: Context
    @Inject lateinit var pm: IPreferenceManager
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var dateUtil: DateUtil

    private lateinit var mOsAlarmManager: AlarmManager
    private var mDisposable: Disposable? = null
    private var compositeDisposable: CompositeDisposable = CompositeDisposable()

    @Inject fun onInit() {
        mOsAlarmManager = mContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        mDisposable = pm.observePatchLifeCycle()
            .observeOn(aapsSchedulers.main)
            .subscribe {
                when (it) {
                    PatchLifecycle.REMOVE_NEEDLE_CAP -> {
                        val triggerAfter = pm.getPatchConfig().patchWakeupTimestamp + TimeUnit.HOURS.toMillis(1) - System.currentTimeMillis()
                        compositeDisposable.add(add(AlarmCode.A020, triggerAfter).subscribe())
                    }

                    PatchLifecycle.ACTIVATED         -> {

                    }

                    PatchLifecycle.SHUTDOWN          -> {
                        val sources = ArrayList<Maybe<*>>()
                        sources.add(Maybe.just(true))
                        pm.getAlarms().occurred.let { occurredAlarms ->
                            if (occurredAlarms.isNotEmpty()) {
                                occurredAlarms.keys.forEach { alarmCode ->
                                    sources.add(
                                        Maybe.just(alarmCode)
                                            .observeOn(aapsSchedulers.main)
                                            .doOnSuccess { rxBus.send(EventDismissNotification(Notification.EOELOW_PATCH_ALERTS + (alarmCode.aeCode + 10000))) }
                                    )
                                }
                            }
                        }
                        pm.getAlarms().registered.let { registeredAlarms ->
                            if (registeredAlarms.isNotEmpty()) {
                                registeredAlarms.keys.forEach { alarmCode ->
                                    sources.add(remove(alarmCode))
                                }
                            }
                        }
                        compositeDisposable.add(Maybe.concat(sources)
                                                    .subscribe {
                                                        pm.getAlarms().clear()
                                                        pm.flushAlarms()
                                                    }
                        )
                    }

                    else                             -> Unit
                }
            }
    }

    override fun add(alarmCode: AlarmCode, triggerAfter: Long, isFirst: Boolean): Maybe<AlarmCode> {
        if (pm.getAlarms().occurred.containsKey(alarmCode)) {
            return Maybe.just(alarmCode)
        } else {
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
                .filter { patchAeCodeItem -> AlarmCode.findByPatchAeCode(patchAeCodeItem.aeValue) != null }
                .observeOn(aapsSchedulers.main)
                .filter { aeCodes -> AlarmCode.findByPatchAeCode(aeCodes.aeValue) != null }
                .flatMapMaybe { aeCodeResponse -> add(AlarmCode.findByPatchAeCode(aeCodeResponse.aeValue)!!, 0L, true) }
                .subscribe()
        )
    }

    private fun registerOsAlarm(alarmCode: AlarmCode, triggerTime: Long): Maybe<AlarmCode> {
        return Maybe.fromCallable {
            cancelOsAlarmInternal(alarmCode)
            createPendingIntent(alarmCode, 0)?.let { pendingIntent ->
                aapsLogger.debug("[${alarmCode}] OS Alarm added. ${dateUtil.toISOString(triggerTime)}")
                mOsAlarmManager.setAlarmClock(AlarmClockInfo(triggerTime, pendingIntent), pendingIntent)
                alarmCode
            }
        }
    }

    override fun remove(alarmCode: AlarmCode): Maybe<AlarmCode> {
        return if (pm.getAlarms().registered.containsKey(alarmCode)) {
            cancelOsAlarms(alarmCode)
                .doOnSuccess {
                    pm.getAlarms().unregister(alarmCode)
                    pm.flushAlarms()
                }
                .map { alarmCode }
        } else {
            Maybe.just(alarmCode)
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

    private fun createPendingIntent(alarmCode: AlarmCode, flag: Int): PendingIntent? {
        val intent = Intent(mContext, OsAlarmReceiver::class.java).setData(getUri(alarmCode))
        return PendingIntent.getBroadcast(mContext, 1, intent, flag)
    }
}