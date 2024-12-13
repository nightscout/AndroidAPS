package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.pump.eopatch.core.api.BolusStart
import app.aaps.pump.eopatch.core.response.BaseResponse
import app.aaps.pump.eopatch.core.response.BolusResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class StartCalcBolusTask @Inject constructor() : BolusTask(TaskFunc.START_CALC_BOLUS) {

    private val NOW_BOLUS_START: BolusStart = BolusStart()

    fun start(detailedBolusInfo: DetailedBolusInfo): Single<out BolusResponse> {
        return isReady().concatMapSingle(Function { startBolusImpl(detailedBolusInfo.insulin.toFloat()) })
            .doOnNext { response: BaseResponse -> this.checkResponse(response) }
            .firstOrError()
            .doOnSuccess { onSuccess(detailedBolusInfo.insulin.toFloat()) }
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "StartCalcBolusTask error") })
    }

    private fun startBolusImpl(nowDoseU: Float): Single<out BolusResponse> {
        return NOW_BOLUS_START.start(nowDoseU)
    }

    private fun onSuccess(nowDoseU: Float) {
        onCalcBolusStarted(nowDoseU)
        enqueue(TaskFunc.UPDATE_CONNECTION)
    }

    @Throws(Exception::class) override fun preCondition() {
        checkPatchConnected()
    }
}
