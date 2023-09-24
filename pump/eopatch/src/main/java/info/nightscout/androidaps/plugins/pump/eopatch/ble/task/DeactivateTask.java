package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.rx.AapsSchedulers;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager;
import info.nightscout.androidaps.plugins.pump.eopatch.code.DeactivationStatus;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.DeActivation;
import info.nightscout.androidaps.plugins.pump.eopatch.core.code.BolusType;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.BolusCurrent;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchLifecycleEvent;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.TempBasal;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class DeactivateTask extends TaskBase {
    @Inject StopBasalTask stopBasalTask;
    @Inject IPreferenceManager pm;
    @Inject AapsSchedulers aapsSchedulers;

    private final DeActivation DEACTIVATION;

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
                                .observeOn(aapsSchedulers.getIo())
                                .doOnSuccess(response -> onDeactivated()))
                .map(response -> DeactivationStatus.of(response.isSuccess(), forced))
                .firstOrError()
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "DeactivateTask error"))
                .onErrorResumeNext(e -> {
                    if (forced) {
                        try {
                            onDeactivated();
                        } catch (Exception t) {
                            aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "DeactivateTask error");
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

    private void onDeactivated() {
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

    private void cleanUpRepository() {
        updateNowBolusStopped();
        updateExtBolusStopped();
        updateTempBasalStopped();
    }

    private void updateTempBasalStopped() {
        TempBasal tempBasal = pm.getTempBasalManager().getStartedBasal();

        if (tempBasal != null) {
            pm.getTempBasalManager().updateBasalStopped();
            pm.flushTempBasalManager();
        }
    }

    private void updateNowBolusStopped() {
        BolusCurrent bolusCurrent = pm.getBolusCurrent();
        long nowID = bolusCurrent.historyId(BolusType.NOW);

        if (nowID > 0 && !bolusCurrent.endTimeSynced(BolusType.NOW)) {
            bolusCurrent.setEndTimeSynced(BolusType.NOW, true);
            pm.flushBolusCurrent();
        }
    }

    private void updateExtBolusStopped() {
        BolusCurrent bolusCurrent = pm.getBolusCurrent();
        long extID = bolusCurrent.historyId(BolusType.EXT);

        if (extID > 0 && !bolusCurrent.endTimeSynced(BolusType.EXT)) {
            bolusCurrent.setEndTimeSynced(BolusType.EXT, true);
            pm.flushBolusCurrent();
        }
    }


}
