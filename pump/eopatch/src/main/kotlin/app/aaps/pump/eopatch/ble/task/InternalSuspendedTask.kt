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
import app.aaps.pump.eopatch.core.api.GetInternalSuspendTime
import app.aaps.pump.eopatch.core.response.PatchInternalSuspendTimeResponse
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

@Suppress("PrivatePropertyName")
@Singleton
class InternalSuspendedTask @Inject constructor(
    private val commandQueue: CommandQueue,
    private val pumpSync: PumpSync,
    private val uel: UserEntryLogger
) : BolusTask(TaskFunc.INTERNAL_SUSPEND) {

    private val INTERNAL_SUSPEND_TIME_GET: GetInternalSuspendTime = GetInternalSuspendTime()
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

    fun start(isNowBolusActive: Boolean, isExtBolusActive: Boolean, isTempBasalActive: Boolean): Single<Long> {
        if (isNowBolusActive || isExtBolusActive) {
            enqueue(TaskFunc.READ_BOLUS_FINISH_TIME)
        }

        if (isTempBasalActive) {
            enqueue(TaskFunc.READ_TEMP_BASAL_FINISH_TIME)
        }

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
            commandQueue.cancelTempBasal(enforceNew = true, callback = object : Callback() {
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
            .concatMapSingle<Long>(Function { getInternalSuspendTime() })
            .firstOrError()
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "InternalSuspendedTask error") })
    }

    private fun getInternalSuspendTime(): Single<Long> {
        return INTERNAL_SUSPEND_TIME_GET.get()
            .doOnSuccess(Consumer { response: PatchInternalSuspendTimeResponse -> this.checkResponse(response) })
            .map(Function { obj: PatchInternalSuspendTimeResponse -> obj.totalSeconds })
    }

    @Synchronized fun enqueue(isNowBolusActive: Boolean, isExtBolusActive: Boolean, isTempBasalActive: Boolean) {
        val ready = (disposable == null || disposable?.isDisposed == true)

        if (ready) {
            disposable = start(isNowBolusActive, isExtBolusActive, isTempBasalActive)
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe(Consumer {
                    bolusCheckSubject.onNext(false)
                    extendedBolusCheckSubject.onNext(false)
                    basalCheckSubject.onNext(false)
                })
        }
    }
}
