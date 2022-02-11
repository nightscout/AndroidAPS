package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager;
import info.nightscout.androidaps.plugins.pump.eopatch.core.code.BolusType;
import info.nightscout.androidaps.plugins.pump.eopatch.code.DeactivationStatus;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.core.api.DeActivation;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.BolusCurrent;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchLifecycleEvent;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.TempBasal;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

@Singleton
public class DeactivateTask extends TaskBase {

    @Inject
    StopBasalTask stopBasalTask;

    @Inject
    IPreferenceManager pm;

    private DeActivation DEACTIVATION;

    @Inject
    public DeactivateTask() {
        super(TaskFunc.DEACTIVATE);
        DEACTIVATION = new DeActivation();
    }

    public Single<DeactivationStatus> run(boolean forced, long timeout) {
        return isReadyCheckActivated()
                .timeout(timeout, TimeUnit.MILLISECONDS)
                .concatMapSingle(v ->
                        DEACTIVATION.start()
                                .doOnSuccess(this::checkResponse)
                                .observeOn(Schedulers.io())
                                .doOnSuccess(response -> onDeactivated(false)))
                .map(response -> DeactivationStatus.of(response.isSuccess(), forced))
                .firstOrError()
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, e.getMessage()))
                .onErrorResumeNext(e -> {
                    if (forced) {
                        try {
                            onDeactivated(true);
                        } catch (Exception t) {
                            aapsLogger.error(LTag.PUMPCOMM, e.getMessage());
                        }
                    }

                    return Single.just(DeactivationStatus.of(false, forced));
                });
    }

    private Observable<TaskFunc> isReadyCheckActivated() {
        if (pm.getPatchConfig().isActivated()) {
            enqueue(TaskFunc.UPDATE_CONNECTION);

            stopBasalTask.enqueue();

            return isReady2();
        }

        return isReady();
    }

    /* Schedulers.io() */
    private void onDeactivated(boolean forced) throws SQLException {
        synchronized (lock) {
            patch.updateMacAddress(null, false);

            if (pm.getPatchConfig().getLifecycleEvent().isShutdown()) {
                return;
            }

            cleanUpRepository();

            pm.getNormalBasalManager().updateForDeactivation();

            pm.updatePatchLifeCycle(PatchLifecycleEvent.createShutdown());

        }
    }

    private void cleanUpRepository() throws SQLException {
        updateNowBolusStopped();
        updateExtBolusStopped();
        updateTempBasalStopped();
    }

    private void updateTempBasalStopped() throws SQLException {
        TempBasal tempBasal = pm.getTempBasalManager().getStartedBasal();

        if (tempBasal != null) {
            pm.getTempBasalManager().updateBasalStopped();
            pm.flushTempBasalManager();
        }
    }

    /* copied from BolusTask. */
    private void updateNowBolusStopped() {
        BolusCurrent bolusCurrent = pm.getBolusCurrent();
        long nowID = bolusCurrent.historyId(BolusType.NOW);

        if (nowID > 0 && !bolusCurrent.endTimeSynced(BolusType.NOW)) {
            bolusCurrent.setEndTimeSynced(BolusType.NOW, true);
            pm.flushBolusCurrent();
        }
    }

    /* copied from BolusTask. */
    private void updateExtBolusStopped() {
        BolusCurrent bolusCurrent = pm.getBolusCurrent();
        long extID = bolusCurrent.historyId(BolusType.EXT);

        if (extID > 0 && !bolusCurrent.endTimeSynced(BolusType.EXT)) {
            bolusCurrent.setEndTimeSynced(BolusType.EXT, true);
            pm.flushBolusCurrent();
        }
    }


}
