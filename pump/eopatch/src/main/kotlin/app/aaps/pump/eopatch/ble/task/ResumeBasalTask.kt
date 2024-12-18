package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.alarm.AlarmCode
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.ble.PatchStateManager
import app.aaps.pump.eopatch.core.api.BasalResume
import app.aaps.pump.eopatch.core.response.BaseResponse
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class ResumeBasalTask @Inject constructor(
    val alarmRegistry: IAlarmRegistry,
    val startNormalBasalTask: StartNormalBasalTask,
    val patchStateManager: PatchStateManager
) : TaskBase(TaskFunc.RESUME_BASAL) {

    private val BASAL_RESUME: BasalResume = BasalResume()

    @Synchronized fun resume(): Single<out BaseResponse> {
        if (patchConfig.needSetBasalSchedule) {
            return startNormalBasalTask.start(normalBasalManager.normalBasal)
        }

        return isReady().concatMapSingle<PatchBooleanResponse>(Function { BASAL_RESUME.resume() })
            .doOnNext(Consumer { response: PatchBooleanResponse -> this.checkResponse(response) })
            .firstOrError()
            .doOnSuccess(Consumer { v: PatchBooleanResponse -> this.onResumeResponse(v) })
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "ResumeBasalTask error") })
    }

    private fun onResumeResponse(v: PatchBooleanResponse) {
        if (v.isSuccess) {
            patchStateManager.onBasalResumed(v.getTimestamp() + 1000)
            alarmRegistry.remove(AlarmCode.B001).subscribe()
        }
        enqueue(TaskFunc.UPDATE_CONNECTION)
    }

    @Throws(Exception::class) override fun preCondition() {
        checkPatchActivated()
        checkPatchConnected()
    }
}
