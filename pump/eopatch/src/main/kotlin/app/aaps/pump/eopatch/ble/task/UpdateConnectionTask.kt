package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.ble.PatchStateManager
import app.aaps.pump.eopatch.core.api.UpdateConnection
import app.aaps.pump.eopatch.core.response.UpdateConnectionResponse
import app.aaps.pump.eopatch.vo.PatchState
import app.aaps.pump.eopatch.vo.PatchState.Companion.create
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class UpdateConnectionTask @Inject constructor(
    private val patchStateManager: PatchStateManager
) : TaskBase(TaskFunc.UPDATE_CONNECTION) {

    private val UPDATE_CONNECTION: UpdateConnection = UpdateConnection()

    fun update(): Single<PatchState> {
        return isReady().concatMapSingle<PatchState>(Function { updateJob() }).firstOrError()
    }

    fun updateJob(): Single<PatchState> {
        return UPDATE_CONNECTION.get()
            .doOnSuccess(Consumer { response: UpdateConnectionResponse -> this.checkResponse(response) })
            .map<ByteArray>(Function { obj: UpdateConnectionResponse -> obj.getPatchState() })
            .map<PatchState>(Function { bytes: ByteArray -> create(bytes, System.currentTimeMillis()) })
            .doOnSuccess(Consumer { patchState: PatchState -> this.onUpdateConnection(patchState) })
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "UpdateConnectionTask error") })
    }

    private fun onUpdateConnection(patchState: PatchState) {
        patchStateManager.updatePatchState(patchState)
    }

    @Synchronized override fun enqueue() {
        val ready = (disposable == null || disposable?.isDisposed == true)

        if (ready) {
            disposable = update()
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe()
        }
    }
}
