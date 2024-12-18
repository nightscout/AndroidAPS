package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.core.api.BolusFinishTimeGet
import app.aaps.pump.eopatch.core.code.BolusType
import app.aaps.pump.eopatch.core.response.BolusFinishTimeResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadBolusFinishTimeTask @Inject constructor() : BolusTask(TaskFunc.READ_BOLUS_FINISH_TIME) {

    private val BOLUS_FINISH_TIME_GET: BolusFinishTimeGet = BolusFinishTimeGet()

    fun read(): Single<BolusFinishTimeResponse> {
        return isReady()
            .concatMapSingle<BolusFinishTimeResponse>(Function { BOLUS_FINISH_TIME_GET.get() })
            .firstOrError()
            .doOnSuccess(Consumer { response: BolusFinishTimeResponse -> this.checkResponse(response) })
            .doOnSuccess(Consumer { response: BolusFinishTimeResponse -> this.onResponse(response) })
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "ReadBolusFinishTimeTask error") })
    }

    fun onResponse(response: BolusFinishTimeResponse) {
        val patchState = pm.patchState
        val bolusCurrent = pm.bolusCurrent
        val nowHistoryID = bolusCurrent.historyId(BolusType.NOW)
        val extHistoryID = bolusCurrent.historyId(BolusType.EXT)

        if (nowHistoryID > 0 && patchState.isBolusDone(BolusType.NOW) && response.nowBolusFinishTime > 0) {
            bolusCurrent.setEndTimeSynced(BolusType.NOW, true)
            enqueue(TaskFunc.STOP_NOW_BOLUS)
        }

        if (extHistoryID > 0 && patchState.isBolusDone(BolusType.EXT) && response.extBolusFinishTime > 0) {
            bolusCurrent.setEndTimeSynced(BolusType.EXT, true)
            enqueue(TaskFunc.STOP_EXT_BOLUS)
        }

        pm.flushBolusCurrent()
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
