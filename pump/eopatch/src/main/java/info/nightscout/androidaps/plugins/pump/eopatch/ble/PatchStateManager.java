package info.nightscout.androidaps.plugins.pump.eopatch.ble;

import java.util.stream.Stream;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.FetchAlarmTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.InternalSuspendedTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.ReadBolusFinishTimeTask;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.task.ReadTempBasalFinishTimeTask;
import info.nightscout.androidaps.plugins.pump.eopatch.core.code.BolusType;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.BolusCurrent;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.NormalBasal;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.TempBasal;
import info.nightscout.interfaces.queue.CommandQueue;
import info.nightscout.rx.AapsSchedulers;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.schedulers.Schedulers;


@Singleton
public class PatchStateManager {

    @Inject IPreferenceManager pm;
    @Inject ReadBolusFinishTimeTask readBolusFinishTimeTask;
    @Inject ReadTempBasalFinishTimeTask readTempBasalFinishTimeTask;
    @Inject InternalSuspendedTask internalSuspendedTask;
    @Inject FetchAlarmTask FETCH_ALARM;
    @Inject CommandQueue commandQueue;
    @Inject AAPSLogger aapsLogger;
    @Inject AapsSchedulers aapsSchedulers;

    @Inject
    public PatchStateManager() {

    }

    public synchronized void updatePatchState(PatchState newState) {
        Maybe.fromCallable(() -> newState).observeOn(Schedulers.single())
                .doOnSuccess(patchState -> updatePatchStateInner(patchState))
                .observeOn(aapsSchedulers.getMain())
                .doOnSuccess(patchState -> aapsLogger.debug(LTag.PUMP, patchState.toString()))
                .subscribe();
    }

    /* Schedulers.io() */
    public synchronized void updatePatchStateInner(PatchState newState) {

        final PatchState oldState = pm.getPatchState();

        int diff = newState.currentTime() - oldState.currentTime();
        if (0 <= diff && diff < 10) {
            /* 10초 안에 같은 PatchState update 시 skip */
            if (oldState.equalState(newState)) {
                return;
            }
        } else if (-5 < diff && diff < 0) {
            /* 이전 State 가 새로운 State 를 덮어 쓰는 것을 방지 -4초 까지 */
            return;
        }

        newState.setUpdatedTimestamp(System.currentTimeMillis());

        if (newState.isNewAlertAlarm()) {
            FETCH_ALARM.enqueue();
        }

        if (newState.isPatchInternalSuspended()){
            onPatchInternalSuspended(newState);
        }

        /* Normal Basal --------------------------------------------------------------------------------------------- */

        if (newState.isNormalBasalAct()) {
            if (oldState.isNormalBasalPaused()) {
                // Resume --> onBasalResume
                onBasalResumeState();

            } else if (!oldState.isNormalBasalAct()) {
                // Start --> onBasalStarted
            }
        } else if (!oldState.isNormalBasalPaused() && newState.isNormalBasalPaused()) {
            if (newState.isTempBasalAct()) {
            } else {
                // pause

            }
        }

        /* Temp Basal ------------------------------------------------------------------------------------------- */
        if (newState.isTempBasalAct()) {
            if (!oldState.isTempBasalAct()) {
                // Start
                onTempBasalStartState();
            }
        }

        boolean tempBasalStopped = false;
        boolean tempBasalFinished = newState.isTempBasalDone() && !newState.isPatchInternalSuspended();

        if (!oldState.isTempBasalDone()) {
            if (newState.isTempBasalDone()) {
                tempBasalStopped = true;

                onTempBasalDoneState();
            } else if (oldState.isTempBasalAct() && !newState.isTempBasalAct()) {
                tempBasalStopped = true;

                onTempBasalCancelState();
            }
        }

        if (tempBasalStopped) {
            if (newState.isNormalBasalAct()) {
                if (!newState.isPatchInternalSuspended()) {
                    onNormalBasalResumed(tempBasalFinished);
                }
            }
        }

        if (!newState.isTempBasalAct() && pm.getTempBasalManager().getStartedBasal() != null) {
            pm.getTempBasalManager().updateBasalStopped();
        }

        /* Now Bolus -------------------------------------------------------------------------------------------- */
        if (!oldState.isNowBolusRegAct() && newState.isNowBolusRegAct()) {
            // Start
        } else if (!oldState.isNowBolusDone()) {
            if (oldState.isNowBolusRegAct() && !newState.isNowBolusRegAct()) {
                // Cancel
            } else if (newState.isNowBolusDone()) {
                // Done
            }
        }

        BolusCurrent bolusCurrent = pm.getBolusCurrent();

        if (!newState.isNowBolusRegAct() && bolusCurrent.historyId(BolusType.NOW) > 0
                && bolusCurrent.endTimeSynced(BolusType.NOW)) {
            bolusCurrent.clearBolus(BolusType.NOW);
        }

        /* Extended Bolus --------------------------------------------------------------------------------------- */
        if (!oldState.isExtBolusRegAct() && newState.isExtBolusRegAct()) {
            // Start
        } else if (!oldState.isExtBolusDone()) {
            if (oldState.isExtBolusRegAct() && !newState.isExtBolusRegAct()) {
                // Cancel
            } else if (newState.isExtBolusDone()) {
                // Done
            }
        }

        if (!newState.isExtBolusRegAct() && bolusCurrent.historyId(BolusType.EXT) > 0
                && bolusCurrent.endTimeSynced(BolusType.EXT)) {
            bolusCurrent.clearBolus(BolusType.EXT);
        }

        /* Finish Time Sync and remained insulin update*/
        /* Bolus Done -> update finish time */
        if (Stream.of(BolusType.NOW, BolusType.EXT).anyMatch(type ->
                newState.isBolusDone(type) && !bolusCurrent.endTimeSynced(type))) {
            readBolusFinishTime();
        }

        /* TempBasal Done -> update finish time */
        if (tempBasalFinished) {
            readTempBasalFinishTime();
        }

        /* Remained Insulin update */
        if (newState.getRemainedInsulin() != oldState.getRemainedInsulin()) {
            pm.getPatchConfig().setRemainedInsulin(newState.getRemainedInsulin());
            pm.flushPatchConfig();
        }

        pm.getPatchState().update(newState);
        pm.flushPatchState();
    }

    private void onTempBasalStartState() {
        TempBasal tempBasal = pm.getTempBasalManager().getStartedBasal();

        if (tempBasal != null) {
            pm.getPatchConfig().updateTempBasalStarted();

            NormalBasal normalBasal = pm.getNormalBasalManager().getNormalBasal();

            if (normalBasal != null) {
                pm.getNormalBasalManager().updateBasalPaused();
            }

            pm.flushPatchConfig();
            pm.flushNormalBasalManager();
        }
    }

    void onTempBasalDoneState() {
        TempBasal tempBasal = pm.getTempBasalManager().getStartedBasal();

        if (tempBasal != null) {
            pm.getTempBasalManager().updateBasalStopped();
            pm.flushTempBasalManager();
        }
    }

    private void onTempBasalCancelState()  {
        TempBasal tempBasal = pm.getTempBasalManager().getStartedBasal();

        if (tempBasal != null) {
            pm.getTempBasalManager().updateBasalStopped();
            pm.flushTempBasalManager();
        }
    }


    private void readBolusFinishTime() {
        readBolusFinishTimeTask.enqueue();
    }

    private void readTempBasalFinishTime() {
        readTempBasalFinishTimeTask.enqueue();
    }

    private synchronized void onBasalResumeState() {

        if (!pm.getNormalBasalManager().isStarted()) {
            long timestamp = System.currentTimeMillis();
            onBasalResumed(timestamp + 1000);
        }
    }

    void onNormalBasalResumed(boolean tempBasalFinished)  {
        NormalBasal normalBasal = pm.getNormalBasalManager().getNormalBasal();
        if (normalBasal != null) {
            pm.getNormalBasalManager().updateBasalStarted();
            normalBasal.updateNormalBasalIndex();
            pm.flushNormalBasalManager();
        }
    }

    public synchronized void onBasalResumed(long timestamp) {
        if (!pm.getNormalBasalManager().isStarted()) {
            pm.getNormalBasalManager().updateBasalStarted();

            pm.getPatchConfig().updateNormalBasalStarted();
            pm.getPatchConfig().setNeedSetBasalSchedule(false);

            NormalBasal basal = pm.getNormalBasalManager().getNormalBasal();

            if (basal != null) {
                basal.updateNormalBasalIndex();
            }

            pm.flushPatchConfig();
            pm.flushNormalBasalManager();
        }
    }

    public synchronized void onBasalStarted(NormalBasal basal, long timestamp) {
        if (basal != null) {
            pm.getNormalBasalManager().updateBasalStarted();
            basal.updateNormalBasalIndex();
        }

        pm.getPatchConfig().updateNormalBasalStarted(); // updateNormalBasalStarted 도 동일함...
        pm.getPatchConfig().setNeedSetBasalSchedule(false);

        pm.flushPatchConfig();
        pm.flushNormalBasalManager();
    }

    private void onPatchInternalSuspended(PatchState state) {
        boolean isNowBolusActive = state.isNowBolusActive();
        boolean isExtBolusActive = state.isExtBolusActive();
        boolean isTempBasalActive = state.isTempBasalActive();

        if (isNowBolusActive || isExtBolusActive || isTempBasalActive) {
            internalSuspendedTask.enqueue(isNowBolusActive, isExtBolusActive, isTempBasalActive);
        }
    }
}
