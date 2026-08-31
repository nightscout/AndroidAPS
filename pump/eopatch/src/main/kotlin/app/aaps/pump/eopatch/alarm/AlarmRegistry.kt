package app.aaps.pump.eopatch.alarm

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.eopatch.EoPatchRxBus
import app.aaps.pump.eopatch.OsAlarmReceiver
import app.aaps.pump.eopatch.alarm.AlarmCode.Companion.getUri
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.code.PatchLifecycle
import app.aaps.pump.eopatch.core.code.PatchAeCode
import app.aaps.pump.eopatch.event.EventEoPatchAlarm
import app.aaps.pump.eopatch.vo.Alarms
import app.aaps.pump.eopatch.vo.PatchConfig
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlin.time.Duration.Companion.hours

/**
 * These were `@Inject lateinit var` fields filled after construction, with the setup below in an
 * `@Inject fun onInit()`. That is Dagger method injection, which Metro does not support - it crashes the
 * compiler on it (ZacSweers/metro#2735). Constructor parameters give the same objects at the same point
 * and let the setup move into `init`, where the fields are guaranteed to be there.
 *
 * Nothing outside this class read those fields; callers only use the [IAlarmRegistry] methods.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class AlarmRegistry @Inject constructor(
    private val mContext: Context,
    private val pm: PreferenceManager,
    private val patchConfig: PatchConfig,
    private val rxBus: RxBus,
    private val notificationManager: NotificationManager,
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    private val dateUtil: DateUtil,
    private val alarms: Alarms
) : IAlarmRegistry {

    // `by lazy`, not a direct assignment: Metro owns this class now, and a contributed class is built
    // for real in the plain-JVM graph tests, where `getSystemService` returns a stand-in that cannot be
    // cast ("java.lang.Object cannot be cast to android.app.AlarmManager"). Deferring to first use is
    // also simply better - building the DI graph should not be reaching for a system service.
    private val mOsAlarmManager: AlarmManager by lazy { mContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager }
    private var mDisposable: Disposable? = null
    private var compositeDisposable: CompositeDisposable = CompositeDisposable()

    init {
        mDisposable = pm.observePatchLifeCycle()
            .observeOn(aapsSchedulers.main)
            .subscribe {
                when (it) {
                    PatchLifecycle.REMOVE_NEEDLE_CAP -> {
                        val triggerAfter = patchConfig.patchWakeupTimestamp + 1.hours.inWholeMilliseconds - System.currentTimeMillis()
                        compositeDisposable.add(add(AlarmCode.A020, triggerAfter).subscribe())
                    }

                    PatchLifecycle.ACTIVATED         -> {}

                    PatchLifecycle.SHUTDOWN          -> {
                        val sources = ArrayList<Maybe<*>>()
                        sources.add(Maybe.just(true))
                        alarms.occurred.let { occurredAlarms ->
                            if (occurredAlarms.isNotEmpty()) {
                                sources.add(
                                    Maybe.just(true)
                                        .observeOn(aapsSchedulers.main)
                                        .doOnSuccess { notificationManager.dismiss(NotificationId.EOFLOW_PATCH_ALERT) }
                                )
                            }
                        }
                        alarms.registered.let { registeredAlarms ->
                            if (registeredAlarms.isNotEmpty()) {
                                registeredAlarms.keys.forEach { alarmCode ->
                                    sources.add(remove(alarmCode))
                                }
                            }
                        }
                        compositeDisposable.add(
                            Maybe.concat(sources)
                                .subscribe {
                                    alarms.clear()
                                    pm.flushAlarms()
                                }
                        )
                    }

                    else                             -> Unit
                }
            }
    }

    override fun add(alarmCode: AlarmCode, triggerAfter: Long, isFirst: Boolean): Maybe<AlarmCode> {
        if (alarms.occurred.containsKey(alarmCode)) {
            return Maybe.just(alarmCode)
        } else {
            val triggerTimeMilli = System.currentTimeMillis() + triggerAfter
            alarms.register(alarmCode, triggerAfter)
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
        return if (alarms.registered.containsKey(alarmCode)) {
            cancelOsAlarms(alarmCode)
                .doOnSuccess {
                    alarms.unregister(alarmCode)
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
        return PendingIntent.getBroadcast(mContext, 1, intent, PendingIntent.FLAG_IMMUTABLE or flag)
    }
}