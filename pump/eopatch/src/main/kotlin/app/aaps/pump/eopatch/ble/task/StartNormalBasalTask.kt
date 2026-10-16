package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.eopatch.ble.PatchStateManager
import app.aaps.pump.eopatch.core.api.BasalScheduleSetBig
import app.aaps.pump.eopatch.core.response.BasalScheduleSetResponse
import app.aaps.pump.eopatch.vo.NormalBasal
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function

@SingleIn(AppScope::class)
@Inject
class StartNormalBasalTask(
    val patchStateManager: PatchStateManager,
    val aapsSchedulers: AapsSchedulers,
) : TaskBase(TaskFunc.START_NORMAL_BASAL) {

    @Inject lateinit var basalScheduleSetBig: BasalScheduleSetBig

    fun start(basal: NormalBasal): Single<BasalScheduleSetResponse> {
        return isReady().concatMapSingle<BasalScheduleSetResponse>(Function { startJob(basal) }).firstOrError()
    }

    fun startJob(basal: NormalBasal): Single<BasalScheduleSetResponse> {
        val doses = basal.doseUnitPerSegmentArray
        return basalScheduleSetBig.set(doses)
            .doOnSuccess(Consumer { response: BasalScheduleSetResponse -> this.checkResponse(response) })
            .observeOn(aapsSchedulers.io)
            .doOnSuccess(Consumer { v: BasalScheduleSetResponse -> onStartNormalBasalResponse(v, basal, doses) })
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "StartNormalBasalTask error") })
    }

    private fun onStartNormalBasalResponse(response: BasalScheduleSetResponse, basal: NormalBasal, doses: FloatArray) {
        val timeStamp = response.timestamp
        patchStateManager.onBasalStarted(basal, timeStamp + 1000)

        // What the patch runs now, which AAPS reports as the running basal (#2454)
        basal.sentDoses = doses
        normalBasalManager.normalBasal = basal
        pm.flushNormalBasalManager()
        enqueue(TaskFunc.UPDATE_CONNECTION)
    }

    @Throws(Exception::class) override fun preCondition() {
        checkPatchConnected()
    }
}
