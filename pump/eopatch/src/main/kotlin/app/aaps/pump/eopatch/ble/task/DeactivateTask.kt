package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.eopatch.code.DeactivationStatus
import app.aaps.pump.eopatch.code.DeactivationStatus.Companion.of
import app.aaps.pump.eopatch.core.api.DeActivation
import app.aaps.pump.eopatch.core.code.BolusType
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import app.aaps.pump.eopatch.vo.PatchLifecycleEvent.Companion.createShutdown
import app.aaps.pump.eopatch.vo.TempBasalManager
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import java.lang.Exception
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class DeactivateTask @Inject constructor(
    private val stopBasalTask: StopBasalTask,
    private val tempBasalManager: TempBasalManager,
    private val aapsSchedulers: AapsSchedulers
) : TaskBase(TaskFunc.DEACTIVATE) {

    private val DEACTIVATION: DeActivation = DeActivation()

    fun run(forced: Boolean, timeout: Long): Single<DeactivationStatus> {
        return isReadyCheckActivated()
            .timeout(timeout, TimeUnit.MILLISECONDS)
            .concatMapSingle<PatchBooleanResponse>(Function {
                DEACTIVATION.start()
                    .doOnSuccess(Consumer { response: PatchBooleanResponse -> this.checkResponse(response) })
                    .observeOn(aapsSchedulers.io)
                    .doOnSuccess(Consumer { onDeactivated() })
            })
            .map<DeactivationStatus>(Function { response: PatchBooleanResponse -> of(response.isSuccess, forced) })
            .firstOrError()
            .doOnError(Consumer { e: Throwable -> aapsLogger.error(LTag.PUMPCOMM, e.message ?: "DeactivateTask error") })
            .onErrorResumeNext(Function { e: Throwable ->
                if (forced) {
                    try {
                        onDeactivated()
                    } catch (_: Exception) {
                        aapsLogger.error(LTag.PUMPCOMM, e.message ?: "DeactivateTask error")
                    }
                }
                Single.just<DeactivationStatus>(of(false, forced))
            })
    }

    private fun isReadyCheckActivated(): Observable<TaskFunc> {
        if (patchConfig.isActivated) {
            enqueue(TaskFunc.UPDATE_CONNECTION)

            stopBasalTask.enqueue()

            return isReady2()
        }

        return isReady()
    }

    private fun onDeactivated() {
        synchronized(lock) {
            patch.updateMacAddress(null, false)
            if (patchConfig.lifecycleEvent.isShutdown) {
                return
            }
            cleanUpRepository()
            normalBasalManager.updateForDeactivation()
            pm.updatePatchLifeCycle(createShutdown())
        }
    }

    private fun cleanUpRepository() {
        updateNowBolusStopped()
        updateExtBolusStopped()
        updateTempBasalStopped()
    }

    private fun updateTempBasalStopped() {
        val tempBasal = tempBasalManager.startedBasal

        if (tempBasal != null) {
            tempBasalManager.updateBasalStopped()
            pm.flushTempBasalManager()
        }
    }

    private fun updateNowBolusStopped() {
        val bolusCurrent = pm.bolusCurrent
        val nowID = bolusCurrent.historyId(BolusType.NOW)

        if (nowID > 0 && !bolusCurrent.endTimeSynced(BolusType.NOW)) {
            bolusCurrent.setEndTimeSynced(BolusType.NOW, true)
            pm.flushBolusCurrent()
        }
    }

    private fun updateExtBolusStopped() {
        val bolusCurrent = pm.bolusCurrent
        val extID = bolusCurrent.historyId(BolusType.EXT)

        if (extID > 0 && !bolusCurrent.endTimeSynced(BolusType.EXT)) {
            bolusCurrent.setEndTimeSynced(BolusType.EXT, true)
            pm.flushBolusCurrent()
        }
    }
}
