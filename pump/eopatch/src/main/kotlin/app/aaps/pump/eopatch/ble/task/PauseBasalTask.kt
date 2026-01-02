package app.aaps.pump.eopatch.ble.task

import android.os.SystemClock
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.eopatch.alarm.AlarmCode
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.core.api.BasalPause
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.functions.Function3
import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName", "SpellCheckingInspection")
@Singleton
class PauseBasalTask @Inject constructor(
    private val alarmRegistry: IAlarmRegistry,
    private val commandQueue: CommandQueue,
    private val pumpSync: PumpSync,
    private val uel: UserEntryLogger
) : BolusTask(TaskFunc.PAUSE_BASAL) {

    private val BASAL_PAUSE: BasalPause = BasalPause()

    private val bolusCheckSubject = BehaviorSubject.create<Boolean>()
    private val extendedBolusCheckSubject = BehaviorSubject.create<Boolean>()
    private val basalCheckSubject = BehaviorSubject.create<Boolean>()

    private fun getBolusSubject(): Observable<Boolean> {
        return bolusCheckSubject.hide()
    }

    private fun getExtendedBolusSubject(): Observable<Boolean> {
        return extendedBolusCheckSubject.hide()
    }

    private fun getBasalSubject(): Observable<Boolean> {
        return basalCheckSubject.hide()
    }

    fun pause(pauseDurationHour: Float, pausedTimestamp: Long, alarmCode: AlarmCode?): Single<PatchBooleanResponse> {
        val patchState = pm.patchState

        if (patchState.isNormalBasalPaused) return Single.just(PatchBooleanResponse(true))

        enqueue(TaskFunc.UPDATE_CONNECTION)

        if (commandQueue.isRunning(Command.CommandType.BOLUS)) {
            uel.log(Action.CANCEL_BOLUS, Sources.EOPatch2, "", ArrayList<ValueWithUnit>())
            commandQueue.cancelAllBoluses(null)
            SystemClock.sleep(650)
        }
        bolusCheckSubject.onNext(true)

        if (pumpSync.expectedPumpState().extendedBolus != null) {
            uel.log(Action.CANCEL_EXTENDED_BOLUS, Sources.EOPatch2, "", ArrayList<ValueWithUnit>())
            commandQueue.cancelExtended(object : Callback() {
                override fun run() {
                    extendedBolusCheckSubject.onNext(true)
                }
            })
        } else {
            extendedBolusCheckSubject.onNext(true)
        }

        if (pumpSync.expectedPumpState().temporaryBasal != null) {
            uel.log(Action.CANCEL_TEMP_BASAL, Sources.EOPatch2, "", ArrayList<ValueWithUnit>())
            commandQueue.cancelTempBasal(true, callback = object : Callback() {
                override fun run() {
                    basalCheckSubject.onNext(true)
                }
            })
        } else {
            basalCheckSubject.onNext(true)
        }

        return Observable.zip<Boolean, Boolean, Boolean, Boolean>(getBolusSubject(), getExtendedBolusSubject(), getBasalSubject(),
                                                                  Function3 { bolusReady: Boolean, extendedBolusReady: Boolean, basalReady: Boolean -> (bolusReady && extendedBolusReady && basalReady) })
            .filter(Predicate { ready: Boolean -> ready })
            .flatMap<TaskFunc>(Function { isReady() })
            .concatMapSingle<Long>(Function { getSuspendedTime(pausedTimestamp) })
            .concatMapSingle<PatchBooleanResponse>(Function { pauseBasal(pauseDurationHour, alarmCode) })
            .firstOrError()
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "PauseBasalTask error") })
    }

    private fun getSuspendedTime(pausedTimestamp: Long): Single<Long> {
        return Single.just(pausedTimestamp)
    }

    private fun pauseBasal(pauseDurationHour: Float, alarmCode: AlarmCode?): Single<PatchBooleanResponse> {
        if (alarmCode == null) {
            return BASAL_PAUSE.pause(pauseDurationHour)
                .doOnSuccess(Consumer { response: PatchBooleanResponse -> this.checkResponse(response) })
                .doOnSuccess(Consumer { onBasalPaused(pauseDurationHour, null) })
        }

        // 정지 알람 발생 시 basal pause 커맨드 전달하지 않음 - 주입 정지 이력만 생성
        onBasalPaused(pauseDurationHour, alarmCode)

        return Single.just(PatchBooleanResponse(true))
    }

    private fun onBasalPaused(pauseDurationHour: Float, alarmCode: AlarmCode?) {
        if (!normalBasalManager.isSuspended()) {
            if (alarmCode != null) {
                patchConfig.updateNormalBasalPausedSilently()
            } else {
                patchConfig.updateNormalBasalPaused(pauseDurationHour)
            }
            normalBasalManager.updateBasalSuspended()

            pm.flushNormalBasalManager()
            pm.flushPatchConfig()

            if ((alarmCode == null || alarmCode.type == AlarmCode.TYPE_ALERT) && pauseDurationHour != 0f) alarmRegistry.add(AlarmCode.B001, TimeUnit.MINUTES.toMillis((pauseDurationHour * 60).toLong()), false).subscribe()
        }

        enqueue(TaskFunc.UPDATE_CONNECTION)
    }

    @Synchronized fun enqueue(pauseDurationHour: Float, pausedTime: Long, alarmCode: AlarmCode) {
        val ready = (disposable == null || disposable?.isDisposed == true)

        if (ready) {
            disposable = pause(pauseDurationHour, pausedTime, alarmCode)
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe(Consumer {
                    bolusCheckSubject.onNext(false)
                    extendedBolusCheckSubject.onNext(false)
                    basalCheckSubject.onNext(false)
                })
        }
    }
}
