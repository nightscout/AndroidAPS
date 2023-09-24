package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BolusStop;
import info.nightscout.androidaps.plugins.pump.eopatch.core.define.IPatchConstant;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BolusStopResponse;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class StopExtBolusTask extends BolusTask {
    private final BolusStop BOLUS_STOP;

    @Inject
    public StopExtBolusTask() {
        super(TaskFunc.STOP_EXT_BOLUS);
        BOLUS_STOP = new BolusStop();
    }

    public Single<BolusStopResponse> stop() {
        return isReady().concatMapSingle(v -> stopJob()).firstOrError()
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "StopExtBolusTask error"));
    }

    public Single<BolusStopResponse> stopJob() {
        return BOLUS_STOP.stop(IPatchConstant.EXT_BOLUS_ID)
                .doOnSuccess(this::checkResponse)
                .doOnSuccess(this::onExtBolusStopped);
    }


    private void onExtBolusStopped(BolusStopResponse response) {
        updateExtBolusStopped(response.getInjectedBolusAmount());
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
        //checkPatchActivated();
        checkPatchConnected();
    }
}
