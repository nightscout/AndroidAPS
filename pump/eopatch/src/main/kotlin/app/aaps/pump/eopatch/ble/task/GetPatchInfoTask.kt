package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.core.api.GetFirmwareVersion
import app.aaps.pump.eopatch.core.api.GetLOT
import app.aaps.pump.eopatch.core.api.GetModelName
import app.aaps.pump.eopatch.core.api.GetPumpDuration
import app.aaps.pump.eopatch.core.api.GetSerialNumber
import app.aaps.pump.eopatch.core.api.GetWakeUpTime
import app.aaps.pump.eopatch.core.api.SetGlobalTime
import app.aaps.pump.eopatch.core.response.BaseResponse
import app.aaps.pump.eopatch.core.response.FirmwareVersionResponse
import app.aaps.pump.eopatch.core.response.LotNumberResponse
import app.aaps.pump.eopatch.core.response.ModelNameResponse
import app.aaps.pump.eopatch.core.response.PumpDurationResponse
import app.aaps.pump.eopatch.core.response.SerialNumberResponse
import app.aaps.pump.eopatch.core.response.WakeUpTimeResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.functions.Predicate
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class GetPatchInfoTask @Inject constructor(
    val updateConnectionTask: UpdateConnectionTask
) : TaskBase(TaskFunc.GET_PATCH_INFO) {

    private val SET_GLOBAL_TIME: SetGlobalTime = SetGlobalTime()
    private val SERIAL_NUMBER_GET: GetSerialNumber = GetSerialNumber()
    private val LOT_NUMBER_GET: GetLOT = GetLOT()
    private val FIRMWARE_VERSION_GET: GetFirmwareVersion = GetFirmwareVersion()
    private val WAKE_UP_TIME_GET: GetWakeUpTime = GetWakeUpTime()
    private val PUMP_DURATION_GET: GetPumpDuration = GetPumpDuration()
    private val GET_MODEL_NAME: GetModelName = GetModelName()

    fun get(): Single<Boolean> {
        val tasks: Single<Boolean> = Single.concat<BaseResponse>(
            listOf<Single<out BaseResponse>>(
                SET_GLOBAL_TIME.set(),
                SERIAL_NUMBER_GET.get().doOnSuccess(Consumer { v: SerialNumberResponse -> this.onSerialNumberResponse(v) }),
                LOT_NUMBER_GET.get().doOnSuccess(Consumer { v: LotNumberResponse -> this.onLotNumberResponse(v) }),
                FIRMWARE_VERSION_GET.get().doOnSuccess(Consumer { v: FirmwareVersionResponse -> this.onFirmwareResponse(v) }),
                WAKE_UP_TIME_GET.get().doOnSuccess(Consumer { v: WakeUpTimeResponse -> this.onWakeupTimeResponse(v) }),
                PUMP_DURATION_GET.get().doOnSuccess(Consumer { v: PumpDurationResponse -> this.onPumpDurationResponse(v) }),
                GET_MODEL_NAME.get().doOnSuccess(Consumer { modelNameResponse: ModelNameResponse -> this.onModelNameResponse(modelNameResponse) })
            )
        )
            .map<Boolean>(Function { obj: BaseResponse -> obj.isSuccess })
            .filter(Predicate { v: Boolean -> !v })
            .first(true)

        return isReady()
            .concatMapSingle<Boolean>(Function { tasks })
            .firstOrError()
            .observeOn(Schedulers.io())
            .doOnSuccess(Consumer { this.onPatchWakeupSuccess() })
            .doOnError(Consumer { this.onPatchWakeupFailed() })
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "GetPatchInfoTask error") })
    }

    private fun onSerialNumberResponse(v: SerialNumberResponse) {
        patchConfig.patchSerialNumber = v.serialNumber
    }

    private fun onLotNumberResponse(v: LotNumberResponse) {
        patchConfig.patchLotNumber = v.lotNumber
    }

    private fun onFirmwareResponse(v: FirmwareVersionResponse) {
        patchConfig.patchFirmwareVersion = v.firmwareVersionString
    }

    private fun onWakeupTimeResponse(v: WakeUpTimeResponse) {
        patchConfig.patchWakeupTimestamp = v.timeInMillis
    }

    private fun onPumpDurationResponse(v: PumpDurationResponse) {
        patchConfig.pumpDurationLargeMilli = v.durationL * 100L
        patchConfig.pumpDurationMediumMilli = v.durationM * 100L
        patchConfig.pumpDurationSmallMilli = v.durationS * 100L
    }

    private fun onModelNameResponse(modelNameResponse: ModelNameResponse) {
        patchConfig.patchModelName = modelNameResponse.modelName
    }

    private fun onPatchWakeupSuccess() {
        synchronized(lock) {
            pm.flushPatchConfig()
        }
    }

    private fun onPatchWakeupFailed() {
        patch.setSeq(-1)
        patchConfig.updateDeactivated()
        pm.flushPatchConfig()
    }
}
