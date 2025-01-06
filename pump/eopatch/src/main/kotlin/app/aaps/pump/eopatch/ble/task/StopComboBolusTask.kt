package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.core.api.BolusStop
import app.aaps.pump.eopatch.core.code.PatchBleResultCode
import app.aaps.pump.eopatch.core.define.IPatchConstant
import app.aaps.pump.eopatch.core.response.BolusStopResponse
import app.aaps.pump.eopatch.core.response.ComboBolusStopResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.BiFunction
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.lang.Exception
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class StopComboBolusTask @Inject constructor() : BolusTask(TaskFunc.STOP_COMBO_BOLUS) {

    private val BOLUS_STOP: BolusStop = BolusStop()

    fun stop(): Single<ComboBolusStopResponse> {
        return isReady()
            .concatMapSingle<ComboBolusStopResponse>(Function { stopJob() })
            .firstOrError()
            .doOnSuccess(Consumer { response: ComboBolusStopResponse -> this.checkResponse(response) })
            .doOnSuccess(Consumer { response: ComboBolusStopResponse -> this.onComboBolusStopped(response) })
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "StopComboBolusTask error") })
    }

    fun stopJob(): Single<ComboBolusStopResponse> {
        return Single.zip<BolusStopResponse, BolusStopResponse, ComboBolusStopResponse>(
            BOLUS_STOP.stop(IPatchConstant.EXT_BOLUS_ID.toInt()),
            BOLUS_STOP.stop(IPatchConstant.NOW_BOLUS_ID.toInt()),
            BiFunction { ext: BolusStopResponse, now: BolusStopResponse -> createStopComboBolusResponse(now, ext) })
    }

    private fun createStopComboBolusResponse(now: BolusStopResponse, ext: BolusStopResponse): ComboBolusStopResponse {
        val idNow = (if (now.isSuccess) IPatchConstant.NOW_BOLUS_ID else 0).toInt()
        val idExt = (if (ext.isSuccess) IPatchConstant.EXT_BOLUS_ID else 0).toInt()

        val injectedAmount = now.injectedBolusAmount
        val injectingAmount = now.injectingBolusAmount

        val injectedExAmount = ext.injectedBolusAmount
        val injectingExAmount = ext.injectingBolusAmount

        if (idNow == 0 && idExt == 0) {
            return ComboBolusStopResponse(IPatchConstant.NOW_BOLUS_ID.toInt(), PatchBleResultCode.BOLUS_UNKNOWN_ID)
        }

        return ComboBolusStopResponse(idNow, injectedAmount, injectingAmount, idExt, injectedExAmount, injectingExAmount)
    }

    private fun onComboBolusStopped(response: ComboBolusStopResponse) {
        if (response.id != 0) updateNowBolusStopped(response.injectedBolusAmount)

        if (response.extId != 0) updateExtBolusStopped(response.injectedExBolusAmount)

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
