package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.eopatch.core.api.BolusStop
import app.aaps.pump.eopatch.core.define.IPatchConstant
import app.aaps.pump.eopatch.core.response.BolusStopResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.lang.Exception
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class StopNowBolusTask @Inject constructor(
    private val aapsSchedulers: AapsSchedulers
) : BolusTask(TaskFunc.STOP_NOW_BOLUS) {

    private val BOLUS_STOP: BolusStop = BolusStop()

    fun stop(): Single<BolusStopResponse> {
        return isReady()
            .observeOn(aapsSchedulers.main)
            .concatMapSingle<BolusStopResponse>(Function { stopJob() }).firstOrError()
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "StopNowBolusTask error") })
    }

    fun stopJob(): Single<BolusStopResponse> {
        return BOLUS_STOP.stop(IPatchConstant.NOW_BOLUS_ID.toInt())
            .doOnSuccess(Consumer { response: BolusStopResponse -> this.checkResponse(response) })
            .doOnSuccess(Consumer { response: BolusStopResponse -> this.onNowBolusStopped(response) })
    }

    private fun onNowBolusStopped(response: BolusStopResponse) {
        updateNowBolusStopped(response.injectedBolusAmount)
        enqueue(TaskFunc.UPDATE_CONNECTION)
    }

    @Synchronized override fun enqueue() {
        val ready = (disposable == null || disposable?.isDisposed == true)

        if (ready) {
            disposable = stop()
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe()
        }
    }

    @Throws(Exception::class) override fun preCondition() {
        checkPatchConnected()
    }
}
