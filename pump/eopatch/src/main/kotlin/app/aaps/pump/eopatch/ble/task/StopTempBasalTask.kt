package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.core.api.TempBasalScheduleStop
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.lang.Exception
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class StopTempBasalTask @Inject constructor() : TaskBase(TaskFunc.STOP_TEMP_BASAL) {

    private val TEMP_BASAL_SCHEDULE_STOP: TempBasalScheduleStop = TempBasalScheduleStop()

    fun stop(): Single<PatchBooleanResponse> {
        return isReady().concatMapSingle<PatchBooleanResponse>(Function { stopJob() }).firstOrError()
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "StopTempBasalTask error") })
    }

    fun stopJob(): Single<PatchBooleanResponse> {
        return TEMP_BASAL_SCHEDULE_STOP.stop()
            .doOnSuccess(Consumer { response: PatchBooleanResponse -> this.checkResponse(response) })
            .doOnSuccess(Consumer { onTempBasalCanceled() })
    }

    private fun onTempBasalCanceled() {
        enqueue(TaskFunc.UPDATE_CONNECTION)
    }

    @Synchronized
    override fun enqueue() {
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
