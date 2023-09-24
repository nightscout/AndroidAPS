package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;


import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.TempBasalFinishTimeGet;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.TempBasalFinishTimeResponse;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class ReadTempBasalFinishTimeTask extends TaskBase {
    private final TempBasalFinishTimeGet TEMP_BASAL_FINISH_TIME_GET;

    @Inject
    public ReadTempBasalFinishTimeTask() {
        super(TaskFunc.READ_TEMP_BASAL_FINISH_TIME);
        TEMP_BASAL_FINISH_TIME_GET = new TempBasalFinishTimeGet();
    }

    public Single<TempBasalFinishTimeResponse> read() {
        return isReady()
                .concatMapSingle(v -> TEMP_BASAL_FINISH_TIME_GET.get())
                .firstOrError()
                .doOnSuccess(this::checkResponse)
                .doOnSuccess(this::onResponse)
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "ReadTempBasalFinishTimeTask error"));
    }

    private void onResponse(TempBasalFinishTimeResponse response) {
    }

    public synchronized void enqueue() {
        boolean ready = (disposable == null || disposable.isDisposed());

        if (ready) {
            disposable = read()
                    .timeout(TASK_ENQUEUE_TIME_OUT, TimeUnit.SECONDS)
                    .subscribe();
        }
    }
}
