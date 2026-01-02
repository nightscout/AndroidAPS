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
import app.aaps.pump.eopatch.core.api.BasalStop
import app.aaps.pump.eopatch.core.response.BasalStopResponse
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
class StopBasalTask @Inject constructor(
    private val commandQueue: CommandQueue,
    private val pumpSync: PumpSync,
    private val uel: UserEntryLogger,
    private val updateConnectionTask: UpdateConnectionTask
) : TaskBase(TaskFunc.STOP_BASAL) {

    private val BASAL_STOP: BasalStop = BasalStop()
    private val bolusCheckSubject = BehaviorSubject.create<Boolean>()
    private val extBolusCheckSubject = BehaviorSubject.create<Boolean>()
    private val basalCheckSubject = BehaviorSubject.create<Boolean>()

    private fun getBolusSubject(): Observable<Boolean> {
        return bolusCheckSubject.hide()
    }

    private fun getExtBolusSubject(): Observable<Boolean> {
        return extBolusCheckSubject.hide()
    }

    private fun getBasalSubject(): Observable<Boolean> {
        return basalCheckSubject.hide()
    }

    fun stop(): Single<BasalStopResponse> {
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
                    extBolusCheckSubject.onNext(true)
                }
            })
        } else {
            extBolusCheckSubject.onNext(true)
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

        return Observable.zip<Boolean, Boolean, Boolean, Boolean>(
            getBolusSubject(),
            getExtBolusSubject(),
            getBasalSubject(),
            Function3 { bolusReady: Boolean, extBolusReady: Boolean, basalReady: Boolean -> (bolusReady && extBolusReady && basalReady) })
            .filter(Predicate { ready: Boolean -> ready })
            .flatMap<TaskFunc>(Function { isReady() })
            .concatMapSingle<BasalStopResponse>(Function { BASAL_STOP.stop() })
            .doOnNext(Consumer { response: BasalStopResponse -> this.checkResponse(response) })
            .doOnNext(Consumer { updateConnectionTask.enqueue() })
            .firstOrError()
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "StopBasalTask error") })
    }

    @Synchronized override fun enqueue() {
        val ready = (disposable == null || disposable?.isDisposed == true)

        if (ready) {
            disposable = stop()
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe(Consumer {
                    bolusCheckSubject.onNext(false)
                    extBolusCheckSubject.onNext(false)
                    basalCheckSubject.onNext(false)
                })
        }
    }

    @Throws(Exception::class) override fun preCondition() {
        checkPatchConnected()
    }
}
