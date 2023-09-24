package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.TempBasalScheduleStop;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.PatchBooleanResponse;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class StopTempBasalTask extends TaskBase {
    private final TempBasalScheduleStop TEMP_BASAL_SCHEDULE_STOP;

    @Inject
    public StopTempBasalTask() {
        super(TaskFunc.STOP_TEMP_BASAL);

        TEMP_BASAL_SCHEDULE_STOP = new TempBasalScheduleStop();
    }

    public Single<PatchBooleanResponse> stop() {
        return isReady().concatMapSingle(v -> stopJob()).firstOrError()
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "StopTempBasalTask error"));
    }

    public Single<PatchBooleanResponse> stopJob() {
        return TEMP_BASAL_SCHEDULE_STOP.stop()
                .doOnSuccess(this::checkResponse)
                .doOnSuccess(v -> onTempBasalCanceled());
    }

    private void onTempBasalCanceled() {
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
