package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.rx.AapsSchedulers;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BolusStop;
import info.nightscout.androidaps.plugins.pump.eopatch.core.define.IPatchConstant;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BolusStopResponse;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class StopNowBolusTask extends BolusTask {
    private final BolusStop BOLUS_STOP;

    @Inject AapsSchedulers aapsSchedulers;

    @Inject
    public StopNowBolusTask() {
        super(TaskFunc.STOP_NOW_BOLUS);
        BOLUS_STOP = new BolusStop();
    }

    public Single<BolusStopResponse> stop() {
        return isReady()
                .observeOn(aapsSchedulers.getMain())
                .concatMapSingle(v -> stopJob()).firstOrError()
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "StopNowBolusTask error"));
    }

    public Single<BolusStopResponse> stopJob() {
        return BOLUS_STOP.stop(IPatchConstant.NOW_BOLUS_ID)
                .doOnSuccess(this::checkResponse)
                .doOnSuccess(this::onNowBolusStopped);
    }

    private void onNowBolusStopped(BolusStopResponse response) {
        updateNowBolusStopped(response.getInjectedBolusAmount());
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
