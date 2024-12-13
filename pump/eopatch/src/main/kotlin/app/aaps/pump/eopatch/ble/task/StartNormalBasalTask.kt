package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.eopatch.ble.PatchStateManager
import app.aaps.pump.eopatch.core.api.BasalScheduleSetBig
import app.aaps.pump.eopatch.core.response.BasalScheduleSetResponse
import app.aaps.pump.eopatch.vo.NormalBasal
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class StartNormalBasalTask @Inject constructor(
    val patchStateManager: PatchStateManager,
    val aapsSchedulers: AapsSchedulers,
) : TaskBase(TaskFunc.START_NORMAL_BASAL) {

    private val BASAL_SCHEDULE_SET_BIG: BasalScheduleSetBig = BasalScheduleSetBig()

    fun start(basal: NormalBasal): Single<BasalScheduleSetResponse> {
        return isReady().concatMapSingle<BasalScheduleSetResponse>(Function { startJob(basal) }).firstOrError()
    }

    fun startJob(basal: NormalBasal): Single<BasalScheduleSetResponse> {
        return BASAL_SCHEDULE_SET_BIG.set(basal.doseUnitPerSegmentArray)
            .doOnSuccess(Consumer { response: BasalScheduleSetResponse -> this.checkResponse(response) })
            .observeOn(aapsSchedulers.io)
            .doOnSuccess(Consumer { v: BasalScheduleSetResponse -> onStartNormalBasalResponse(v, basal) })
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "StartNormalBasalTask error") })
    }

    private fun onStartNormalBasalResponse(response: BasalScheduleSetResponse, basal: NormalBasal) {
        val timeStamp = response.getTimestamp()
        patchStateManager.onBasalStarted(basal, timeStamp + 1000)

        normalBasalManager.normalBasal = basal
        pm.flushNormalBasalManager()
        enqueue(TaskFunc.UPDATE_CONNECTION)
    }

    @Throws(Exception::class) override fun preCondition() {
        checkPatchConnected()
    }
}
