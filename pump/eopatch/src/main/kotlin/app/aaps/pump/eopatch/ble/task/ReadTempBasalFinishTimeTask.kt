package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.core.api.TempBasalFinishTimeGet
import app.aaps.pump.eopatch.core.response.TempBasalFinishTimeResponse
import app.aaps.pump.eopatch.vo.TempBasalManager
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadTempBasalFinishTimeTask @Inject constructor() : TaskBase(TaskFunc.READ_TEMP_BASAL_FINISH_TIME) {

    @Inject lateinit var tempBasalFinishTimeGet: TempBasalFinishTimeGet
    @Inject lateinit var tempBasalManager: TempBasalManager

    fun read(): Single<TempBasalFinishTimeResponse> {
        return isReady()
            .concatMapSingle<TempBasalFinishTimeResponse>(Function { tempBasalFinishTimeGet.get() })
            .firstOrError()
            .doOnSuccess(Consumer { response: TempBasalFinishTimeResponse -> this.checkResponse(response) })
            .doOnSuccess(Consumer { response: TempBasalFinishTimeResponse -> this.onResponse(response) })
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "ReadTempBasalFinishTimeTask error") })
    }

    private fun onResponse(response: TempBasalFinishTimeResponse) {
        aapsLogger.debug(LTag.PUMPCOMM, "TempBasal finish time: ${response.tempBasalFinishTime}, startedBasal: ${tempBasalManager.startedBasal}")
    }

    @Synchronized override fun enqueue() {
        val ready = (disposable == null || disposable?.isDisposed == true)

        if (ready) {
            disposable = read()
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe()
        }
    }
}
