package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.core.api.GetGlobalTime
import app.aaps.pump.eopatch.core.api.GetTemperature
import app.aaps.pump.eopatch.core.api.GetVoltageLevelB4Priming
import app.aaps.pump.eopatch.core.response.BatteryVoltageLevelPairingResponse
import app.aaps.pump.eopatch.core.response.GlobalTimeResponse
import app.aaps.pump.eopatch.core.response.TemperatureResponse
import app.aaps.pump.eopatch.core.scan.PatchSelfTestResult
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.functions.Predicate
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class SelfTestTask @Inject constructor() : TaskBase(TaskFunc.SELF_TEST) {

    private val TEMPERATURE_GET: GetTemperature = GetTemperature()
    private val BATTERY_LEVEL_GET_BEFORE_PRIMING: GetVoltageLevelB4Priming = GetVoltageLevelB4Priming()
    private val GET_GLOBAL_TIME: GetGlobalTime = GetGlobalTime()

    fun start(): Single<PatchSelfTestResult> {
        val tasks: Single<PatchSelfTestResult> = Single.concat<PatchSelfTestResult>(
            listOf<Single<PatchSelfTestResult>>(
                TEMPERATURE_GET.get().map<PatchSelfTestResult>(Function { obj: TemperatureResponse -> obj.result }),
                BATTERY_LEVEL_GET_BEFORE_PRIMING.get().map<PatchSelfTestResult>(Function { obj: BatteryVoltageLevelPairingResponse -> obj.result }),
                GET_GLOBAL_TIME.get(false).map<PatchSelfTestResult>(Function { obj: GlobalTimeResponse -> obj.result })
            )
        )
            .filter(Predicate { result: PatchSelfTestResult -> result != PatchSelfTestResult.TEST_SUCCESS })
            .first(PatchSelfTestResult.TEST_SUCCESS)

        return isReady()
            .concatMapSingle<PatchSelfTestResult>(Function { tasks })
            .firstOrError()
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "SelfTestTask error") })
    }
}
