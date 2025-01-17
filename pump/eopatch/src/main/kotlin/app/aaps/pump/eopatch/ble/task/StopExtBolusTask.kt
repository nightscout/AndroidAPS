package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
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
class StopExtBolusTask @Inject constructor() : BolusTask(TaskFunc.STOP_EXT_BOLUS) {

    private val BOLUS_STOP: BolusStop = BolusStop()

    fun stop(): Single<BolusStopResponse> {
        return isReady().concatMapSingle<BolusStopResponse>(Function { stopJob() }).firstOrError()
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "StopExtBolusTask error") })
    }

    fun stopJob(): Single<BolusStopResponse> {
        return BOLUS_STOP.stop(IPatchConstant.EXT_BOLUS_ID.toInt())
            .doOnSuccess(Consumer { response: BolusStopResponse -> this.checkResponse(response) })
            .doOnSuccess(Consumer { response: BolusStopResponse -> this.onExtBolusStopped(response) })
    }

    private fun onExtBolusStopped(response: BolusStopResponse) {
        updateExtBolusStopped(response.injectedBolusAmount)
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
        //checkPatchActivated();
        checkPatchConnected()
    }
}
