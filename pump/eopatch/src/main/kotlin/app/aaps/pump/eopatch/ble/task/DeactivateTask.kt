package app.aaps.pump.eopatch.ble.task

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.pump.eopatch.code.DeactivationStatus
import app.aaps.pump.eopatch.code.DeactivationStatus.Companion.of
import app.aaps.pump.eopatch.core.api.DeActivation
import app.aaps.pump.eopatch.core.code.BolusType
import app.aaps.pump.eopatch.core.response.PatchBooleanResponse
import app.aaps.pump.eopatch.vo.PatchLifecycleEvent.Companion.createShutdown
import app.aaps.pump.eopatch.vo.TempBasalManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

@SingleIn(AppScope::class)
@Inject
class DeactivateTask(
    private val stopBasalTask: StopBasalTask,
    private val tempBasalManager: TempBasalManager,
    private val aapsSchedulers: AapsSchedulers,
    private val pumpSync: PumpSync,
    private val rxBus: RxBus
) : TaskBase(TaskFunc.DEACTIVATE) {

    @Inject lateinit var deactivation: DeActivation

    fun run(forced: Boolean, timeout: Long): Single<DeactivationStatus> {
        return isReadyCheckActivated()
            .timeout(timeout, TimeUnit.MILLISECONDS)
            .concatMapSingle<PatchBooleanResponse>(Function {
                deactivation.start()
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
            patch.updateMacAddress("", false)
            if (patchConfig.lifecycleEvent.isShutdown) {
                return
            }
            cleanUpRepository()
            recordNoDelivery()
            normalBasalManager.updateForDeactivation()
            pm.updatePatchLifeCycle(createShutdown())
        }
        // The loop starts its "suspended by pump" mode on a pump status event. The disconnect above came before the
        // patch was marked as discarded, so its event still found the patch active.
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
    }

    /**
     * Without a patch no insulin is given. A 0 U/h PUMP_SUSPEND temp basal says so until the next patch is active:
     * its activation ends it (`connectNewPump`). Before, AAPS counted the scheduled basal as given between two
     * patches (#2552). Written with the serial of this patch, the active pump, before the shutdown clears it.
     */
    private fun recordNoDelivery() {
        val now = System.currentTimeMillis()
        runBlocking {
            pumpSync.syncTemporaryBasalWithPumpId(
                timestamp = now,
                rate = PumpRate(0.0),
                duration = T.hours(NO_PATCH_MAX_HOURS).msecs(),
                isAbsolute = true,
                type = PumpSync.TemporaryBasalType.PUMP_SUSPEND,
                pumpId = now,
                pumpType = PumpType.EOFLOW_EOPATCH2,
                pumpSerial = patchConfig.patchSerialNumber
            )
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

    companion object {

        /** Longer than a patch can be used. A new patch ends it much sooner. */
        private const val NO_PATCH_MAX_HOURS = 96L
    }
}
