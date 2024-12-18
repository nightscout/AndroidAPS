package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.core.api.SetLowReservoirLevelAndExpireAlert
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
class SetLowReservoirTask @Inject constructor() : TaskBase(TaskFunc.LOW_RESERVOIR) {

    private val SET_LOW_RESERVOIR_N_EXPIRE_ALERT: SetLowReservoirLevelAndExpireAlert = SetLowReservoirLevelAndExpireAlert()

    fun set(doseUnit: Int, hours: Int): Single<PatchBooleanResponse> {
        return isReady()
            .concatMapSingle<PatchBooleanResponse>(Function { SET_LOW_RESERVOIR_N_EXPIRE_ALERT.set(doseUnit, hours) })
            .doOnNext(Consumer { response: PatchBooleanResponse -> this.checkResponse(response) })
            .firstOrError()
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "SetLowReservoirTask error") })
    }

    @Synchronized override fun enqueue() {
        val alertTime = patchConfig.patchExpireAlertTime
        val alertSetting = patchConfig.lowReservoirAlertAmount

        val ready = (disposable == null || disposable?.isDisposed == true)

        if (ready) {
            disposable = set(alertSetting, alertTime)
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe()
        }
    }

    @Throws(Exception::class) override fun preCondition() {
        checkPatchConnected()
    }
}
