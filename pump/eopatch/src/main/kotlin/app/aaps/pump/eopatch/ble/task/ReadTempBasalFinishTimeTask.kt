package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.core.api.TempBasalFinishTimeGet
import app.aaps.pump.eopatch.core.response.TempBasalFinishTimeResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class ReadTempBasalFinishTimeTask @Inject constructor() : TaskBase(TaskFunc.READ_TEMP_BASAL_FINISH_TIME) {

    private val TEMP_BASAL_FINISH_TIME_GET: TempBasalFinishTimeGet = TempBasalFinishTimeGet()

    fun read(): Single<TempBasalFinishTimeResponse> {
        return isReady()
            .concatMapSingle<TempBasalFinishTimeResponse>(Function { TEMP_BASAL_FINISH_TIME_GET.get() })
            .firstOrError()
            .doOnSuccess(Consumer { response: TempBasalFinishTimeResponse -> this.checkResponse(response) })
            .doOnSuccess(Consumer { this.onResponse() })
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "ReadTempBasalFinishTimeTask error") })
    }

    private fun onResponse() {
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
