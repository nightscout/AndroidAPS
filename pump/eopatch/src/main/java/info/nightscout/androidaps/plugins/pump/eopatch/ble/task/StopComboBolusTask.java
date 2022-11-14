package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BolusStop;
import info.nightscout.androidaps.plugins.pump.eopatch.core.code.PatchBleResultCode;
import info.nightscout.androidaps.plugins.pump.eopatch.core.define.IPatchConstant;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BolusStopResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.ComboBolusStopResponse;
import info.nightscout.rx.logging.LTag;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class StopComboBolusTask extends BolusTask {
    private final BolusStop BOLUS_STOP;

    @Inject
    public StopComboBolusTask() {
        super(TaskFunc.STOP_COMBO_BOLUS);
        BOLUS_STOP = new BolusStop();
    }

    public Single<ComboBolusStopResponse> stop() {
        return isReady()
                .concatMapSingle(v -> stopJob())
                .firstOrError()
                .doOnSuccess(this::checkResponse)
                .doOnSuccess(this::onComboBolusStopped)
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "StopComboBolusTask error"));
    }

    public Single<ComboBolusStopResponse> stopJob() {
        return Single.zip(
                    BOLUS_STOP.stop(IPatchConstant.EXT_BOLUS_ID),
                    BOLUS_STOP.stop(IPatchConstant.NOW_BOLUS_ID),
                    (ext, now) -> createStopComboBolusResponse(now, ext));
    }

    private ComboBolusStopResponse createStopComboBolusResponse(BolusStopResponse now, BolusStopResponse ext) {
        int idNow = now.isSuccess() ? IPatchConstant.NOW_BOLUS_ID : 0;
        int idExt = ext.isSuccess() ? IPatchConstant.EXT_BOLUS_ID : 0;

        int injectedAmount = now.getInjectedBolusAmount();
        int injectingAmount = now.getInjectingBolusAmount();

        int injectedExAmount = ext.getInjectedBolusAmount();
        int injectingExAmount = ext.getInjectingBolusAmount();

        if (idNow == 0 && idExt == 0) {
            return new ComboBolusStopResponse(IPatchConstant.NOW_BOLUS_ID, PatchBleResultCode.BOLUS_UNKNOWN_ID);
        }

        return new ComboBolusStopResponse(idNow, injectedAmount, injectingAmount, idExt, injectedExAmount, injectingExAmount);
    }

    private void onComboBolusStopped(ComboBolusStopResponse response) {
        if (response.getId() != 0)
            updateNowBolusStopped(response.getInjectedBolusAmount());

        if (response.getExtId() != 0)
            updateExtBolusStopped(response.getInjectedExBolusAmount());

        enqueue(TaskFunc.UPDATE_CONNECTION);
    }

    public synchronized void enqueue() {
        boolean ready = (disposable == null || disposable.isDisposed());

        if (ready) {
            disposable = stop()
                .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                .subscribe();
        }
    }

    @Override
    protected void preCondition() throws Exception {
        checkPatchConnected();
    }
}
