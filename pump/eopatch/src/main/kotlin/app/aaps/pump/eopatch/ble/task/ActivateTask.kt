package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.core.api.SetKey
import app.aaps.pump.eopatch.core.response.BasalScheduleSetResponse
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import app.aaps.pump.eopatch.vo.PatchLifecycleEvent.Companion.createActivated
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class ActivateTask @Inject constructor(
    val startBasalTask: StartNormalBasalTask
) : TaskBase(TaskFunc.ACTIVATE) {

    private val SET_KEY = SetKey()

    fun start(): Single<Boolean> {
        return isReady()
            .concatMapSingle<PatchBooleanResponse>(Function { SET_KEY.setKey() })
            .doOnNext(Consumer { response: PatchBooleanResponse -> this.checkResponse(response) })
            .firstOrError()
            .observeOn(Schedulers.io())
            .flatMap<BasalScheduleSetResponse>(Function { startBasalTask.start(normalBasalManager.normalBasal) })
            .doOnSuccess(Consumer { onActivated() })
            .map<Boolean>(Function { obj -> obj.isSuccess })
            .doOnError(Consumer { e: Throwable? -> aapsLogger.error(LTag.PUMPCOMM, e?.message ?: "ActivateTask error") })
    }

    private fun onActivated() {
        pm.updatePatchLifeCycle(createActivated())
    }
}
