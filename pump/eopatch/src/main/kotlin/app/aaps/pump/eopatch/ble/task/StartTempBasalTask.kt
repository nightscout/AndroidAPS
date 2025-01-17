package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.eopatch.core.api.TempBasalScheduleStart
import app.aaps.pump.eopatch.core.response.TempBasalScheduleSetResponse
import app.aaps.pump.eopatch.vo.TempBasal
import app.aaps.pump.eopatch.vo.TempBasalManager
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class StartTempBasalTask @Inject constructor(
    private val tempBasalManager: TempBasalManager,
    private val aapsSchedulers: AapsSchedulers
) : TaskBase(TaskFunc.START_TEMP_BASAL) {

    private val TEMP_BASAL_SCHEDULE_START: TempBasalScheduleStart = TempBasalScheduleStart()

    fun start(tempBasal: TempBasal): Single<TempBasalScheduleSetResponse> {
        return isReady()
            .concatMapSingle<TempBasalScheduleSetResponse>(Function { TEMP_BASAL_SCHEDULE_START.start(tempBasal.durationMinutes, tempBasal.doseUnitPerHour, tempBasal.percent) })
            .doOnNext(Consumer { response: TempBasalScheduleSetResponse -> this.checkResponse(response) })
            .firstOrError()
            .observeOn(aapsSchedulers.io)
            .doOnSuccess(Consumer { onTempBasalStarted(tempBasal) })
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "StartTempBasalTask error") })
    }

    private fun onTempBasalStarted(tempBasal: TempBasal) {
        tempBasalManager.updateBasalRunning(tempBasal)
        pm.flushTempBasalManager()
        enqueue(TaskFunc.UPDATE_CONNECTION)
    }

    @Throws(Exception::class) override fun preCondition() {
        checkPatchConnected()
    }
}
