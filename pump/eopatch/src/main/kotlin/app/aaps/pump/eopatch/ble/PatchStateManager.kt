package app.aaps.pump.eopatch.ble

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.eopatch.ble.task.FetchAlarmTask
import app.aaps.pump.eopatch.ble.task.InternalSuspendedTask
import app.aaps.pump.eopatch.ble.task.ReadBolusFinishTimeTask
import app.aaps.pump.eopatch.ble.task.ReadTempBasalFinishTimeTask
import app.aaps.pump.eopatch.core.code.BolusType
import app.aaps.pump.eopatch.vo.NormalBasal
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import app.aaps.pump.eopatch.vo.PatchState
import app.aaps.pump.eopatch.vo.TempBasalManager
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.Callable
import java.util.stream.Stream
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class PatchStateManager @Inject constructor(
    private val pm: PreferenceManager,
    private val patchConfig: PatchConfig,
    private val tempBasalManager: TempBasalManager,
    private val normalBasalManager: NormalBasalManager,
    private val readBolusFinishTimeTask: ReadBolusFinishTimeTask,
    private val readTempBasalFinishTimeTask: ReadTempBasalFinishTimeTask,
    private val internalSuspendedTask: InternalSuspendedTask,
    private val FETCH_ALARM: FetchAlarmTask,
    private val aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers
) {

    @Synchronized fun updatePatchState(newState: PatchState) {
        Maybe.fromCallable<PatchState>(Callable { newState }).observeOn(Schedulers.single())
            .doOnSuccess(Consumer { newState: PatchState -> this.updatePatchStateInner(newState) })
            .observeOn(aapsSchedulers.main)
            .doOnSuccess(Consumer { patchState: PatchState -> aapsLogger.debug(LTag.PUMP, patchState.toString()) })
            .subscribe()
    }

    /* Schedulers.io() */
    @Synchronized fun updatePatchStateInner(newState: PatchState) {
        val oldState = pm.patchState

        val diff = newState.currentTime() - oldState.currentTime()
        if (0 <= diff && diff < 10) {
            /* 10초 안에 같은 PatchState update 시 skip */
            if (oldState.equalState(newState)) {
                return
            }
        } else if (-5 < diff && diff < 0) {
            /* 이전 State 가 새로운 State 를 덮어 쓰는 것을 방지 -4초 까지 */
            return
        }

        newState.updatedTimestamp = System.currentTimeMillis()

        if (newState.isNewAlertAlarm) {
            FETCH_ALARM.enqueue()
        }

        if (newState.isPatchInternalSuspended) {
            onPatchInternalSuspended(newState)
        }

        /* Normal Basal --------------------------------------------------------------------------------------------- */
        if (newState.isNormalBasalAct) {
            if (oldState.isNormalBasalPaused) {
                // Resume --> onBasalResume
                onBasalResumeState()
            } else if (!oldState.isNormalBasalAct) {
                // Start --> onBasalStarted
            }
        } else if (!oldState.isNormalBasalPaused && newState.isNormalBasalPaused) {
            if (newState.isTempBasalAct) {
            } else {
                // pause
            }
        }

        /* Temp Basal ------------------------------------------------------------------------------------------- */
        if (newState.isTempBasalAct) {
            if (!oldState.isTempBasalAct) {
                // Start
                onTempBasalStartState()
            }
        }

        var tempBasalStopped = false
        val tempBasalFinished = newState.isTempBasalDone && !newState.isPatchInternalSuspended

        if (!oldState.isTempBasalDone) {
            if (newState.isTempBasalDone) {
                tempBasalStopped = true

                onTempBasalDoneState()
            } else if (oldState.isTempBasalAct && !newState.isTempBasalAct) {
                tempBasalStopped = true

                onTempBasalCancelState()
            }
        }

        if (tempBasalStopped) {
            if (newState.isNormalBasalAct) {
                if (!newState.isPatchInternalSuspended) {
                    onNormalBasalResumed()
                }
            }
        }

        if (!newState.isTempBasalAct && tempBasalManager.startedBasal != null) {
            tempBasalManager.updateBasalStopped()
        }

        /* Now Bolus -------------------------------------------------------------------------------------------- */
        if (!oldState.isNowBolusRegAct && newState.isNowBolusRegAct) {
            // Start
        } else if (!oldState.isNowBolusDone) {
            if (oldState.isNowBolusRegAct && !newState.isNowBolusRegAct) {
                // Cancel
            } else if (newState.isNowBolusDone) {
                // Done
            }
        }

        val bolusCurrent = pm.bolusCurrent

        if (!newState.isNowBolusRegAct && bolusCurrent.historyId(BolusType.NOW) > 0 && bolusCurrent.endTimeSynced(BolusType.NOW)) {
            bolusCurrent.clearBolus(BolusType.NOW)
        }

        /* Extended Bolus --------------------------------------------------------------------------------------- */
        if (!oldState.isExtBolusRegAct && newState.isExtBolusRegAct) {
            // Start
        } else if (!oldState.isExtBolusDone) {
            if (oldState.isExtBolusRegAct && !newState.isExtBolusRegAct) {
                // Cancel
            } else if (newState.isExtBolusDone) {
                // Done
            }
        }

        if (!newState.isExtBolusRegAct && bolusCurrent.historyId(BolusType.EXT) > 0 && bolusCurrent.endTimeSynced(BolusType.EXT)) {
            bolusCurrent.clearBolus(BolusType.EXT)
        }

        /* Finish Time Sync and remained insulin update*/
        /* Bolus Done -> update finish time */
        if (Stream.of<BolusType>(BolusType.NOW, BolusType.EXT).anyMatch { type: BolusType -> newState.isBolusDone(type) && !bolusCurrent.endTimeSynced(type) }) {
            readBolusFinishTime()
        }

        /* TempBasal Done -> update finish time */
        if (tempBasalFinished) {
            readTempBasalFinishTime()
        }

        /* Remained Insulin update */
        if (newState.remainedInsulin != oldState.remainedInsulin) {
            patchConfig.remainedInsulin = newState.remainedInsulin
            pm.flushPatchConfig()
        }

        pm.patchState.update(newState)
        pm.flushPatchState()
    }

    private fun onTempBasalStartState() {
        val tempBasal = tempBasalManager.startedBasal

        if (tempBasal != null) {
            patchConfig.updateTempBasalStarted()

            normalBasalManager.updateBasalPaused()

            pm.flushPatchConfig()
            pm.flushNormalBasalManager()
        }
    }

    fun onTempBasalDoneState() {
        val tempBasal = tempBasalManager.startedBasal

        if (tempBasal != null) {
            tempBasalManager.updateBasalStopped()
            pm.flushTempBasalManager()
        }
    }

    private fun onTempBasalCancelState() {
        val tempBasal = tempBasalManager.startedBasal

        if (tempBasal != null) {
            tempBasalManager.updateBasalStopped()
            pm.flushTempBasalManager()
        }
    }

    private fun readBolusFinishTime() {
        readBolusFinishTimeTask.enqueue()
    }

    private fun readTempBasalFinishTime() {
        readTempBasalFinishTimeTask.enqueue()
    }

    @Synchronized private fun onBasalResumeState() {
        if (!normalBasalManager.isStarted) {
            val timestamp = System.currentTimeMillis()
            onBasalResumed(timestamp + 1000)
        }
    }

    fun onNormalBasalResumed() {
        val normalBasal = normalBasalManager.normalBasal
        normalBasalManager.updateBasalStarted()
        normalBasal.updateNormalBasalIndex()
        pm.flushNormalBasalManager()
    }

    @Synchronized fun onBasalResumed(timestamp: Long) {
        if (!normalBasalManager.isStarted) {
            normalBasalManager.updateBasalStarted()

            patchConfig.updateNormalBasalStarted()
            patchConfig.needSetBasalSchedule = false

            val basal = normalBasalManager.normalBasal

            basal.updateNormalBasalIndex()

            pm.flushPatchConfig()
            pm.flushNormalBasalManager()
        }
    }

    @Synchronized fun onBasalStarted(basal: NormalBasal, timestamp: Long) {
        normalBasalManager.updateBasalStarted()
        basal.updateNormalBasalIndex()

        patchConfig.updateNormalBasalStarted() // updateNormalBasalStarted 도 동일함...
        patchConfig.needSetBasalSchedule = false

        pm.flushPatchConfig()
        pm.flushNormalBasalManager()
    }

    private fun onPatchInternalSuspended(state: PatchState) {
        val isNowBolusActive = state.isNowBolusActive
        val isExtBolusActive = state.isExtBolusActive
        val isTempBasalActive = state.isTempBasalActive

        if (isNowBolusActive || isExtBolusActive || isTempBasalActive) {
            internalSuspendedTask.enqueue(isNowBolusActive, isExtBolusActive, isTempBasalActive)
        }
    }
}
