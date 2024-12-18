package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.alarm.AlarmCode
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.core.api.StartNeedleCheck
import app.aaps.pump.eopatch.core.api.UpdateConnection
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import app.aaps.pump.eopatch.core.response.UpdateConnectionResponse
import app.aaps.pump.eopatch.vo.PatchState
import app.aaps.pump.eopatch.vo.PatchState.Companion.create
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PropertyName")
@Singleton
class NeedleSensingTask @Inject constructor(
    private val alarmRegistry: IAlarmRegistry
) : TaskBase(TaskFunc.NEEDLE_SENSING) {

    var START_NEEDLE_CHECK: StartNeedleCheck = StartNeedleCheck()
    var UPDATE_CONNECTION: UpdateConnection = UpdateConnection()

    fun start(): Single<Boolean> {
        return isReady()
            .concatMapSingle<PatchBooleanResponse>(Function { START_NEEDLE_CHECK.start() })
            .doOnNext(Consumer { response: PatchBooleanResponse -> this.checkResponse(response) })
            .concatMapSingle<UpdateConnectionResponse>(Function { UPDATE_CONNECTION.get() })
            .doOnNext(Consumer { response: UpdateConnectionResponse -> this.checkResponse(response) })
            .map<PatchState>(Function { updateConnectionResponse: UpdateConnectionResponse -> create(updateConnectionResponse.getPatchState(), System.currentTimeMillis()) })
            .doOnNext(Consumer { v: PatchState -> this.onResponse(v) })
            .map<Boolean>(Function { patchState: PatchState -> !patchState.isNeedNeedleSensing })
            .firstOrError()
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "NeedleSensingTask error") })
    }

    private fun onResponse(v: PatchState) {
        if (v.isNeedNeedleSensing) {
            alarmRegistry.add(AlarmCode.A016, 0, false).subscribe()
        } else {
            alarmRegistry.remove(AlarmCode.A016).subscribe()
        }
    }
}
