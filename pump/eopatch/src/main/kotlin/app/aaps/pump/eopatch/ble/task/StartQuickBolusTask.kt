package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.code.BolusExDuration
import app.aaps.pump.eopatch.core.api.BolusStart
import app.aaps.pump.eopatch.core.api.ComboBolusStart
import app.aaps.pump.eopatch.core.api.ExtBolusStart
import app.aaps.pump.eopatch.core.response.BolusResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class StartQuickBolusTask @Inject constructor() : BolusTask(TaskFunc.START_QUICK_BOLUS) {

    private val NOW_BOLUS_START: BolusStart = BolusStart()
    private val EXT_BOLUS_START: ExtBolusStart = ExtBolusStart()
    private val COMBO_BOLUS_START: ComboBolusStart = ComboBolusStart()

    fun start(
        nowDoseU: Float, exDoseU: Float,
        exDuration: BolusExDuration
    ): Single<out BolusResponse> {
        return isReady().concatMapSingle(Function { startBolusImpl(nowDoseU, exDoseU, exDuration) })
            .doOnNext { response -> this.checkResponse(response) }
            .firstOrError()
            .doOnSuccess { onSuccess(nowDoseU, exDoseU, exDuration) }
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "StartQuickBolusTask error") })
    }

    private fun startBolusImpl(
        nowDoseU: Float, exDoseU: Float,
        exDuration: BolusExDuration
    ): Single<out BolusResponse> {
        return if (nowDoseU > 0 && exDoseU > 0) {
            COMBO_BOLUS_START.start(nowDoseU, exDoseU, exDuration.minute)
        } else if (exDoseU > 0) {
            EXT_BOLUS_START.start(exDoseU, exDuration.minute)
        } else {
            NOW_BOLUS_START.start(nowDoseU)
        }
    }

    private fun onSuccess(nowDoseU: Float, exDoseU: Float, exDuration: BolusExDuration) {
        onQuickBolusStarted(nowDoseU, exDoseU, exDuration)
        enqueue(TaskFunc.UPDATE_CONNECTION)
    }

    @Throws(Exception::class) override fun preCondition() {
        //checkPatchActivated();
        checkPatchConnected()
    }
}
