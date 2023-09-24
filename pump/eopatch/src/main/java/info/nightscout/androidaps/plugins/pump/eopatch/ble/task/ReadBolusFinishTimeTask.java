package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BolusFinishTimeGet;
import info.nightscout.androidaps.plugins.pump.eopatch.core.code.BolusType;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BolusFinishTimeResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.BolusCurrent;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class ReadBolusFinishTimeTask extends BolusTask {
    private final BolusFinishTimeGet BOLUS_FINISH_TIME_GET;

    @Inject
    public ReadBolusFinishTimeTask() {
        super(TaskFunc.READ_BOLUS_FINISH_TIME);
        BOLUS_FINISH_TIME_GET = new BolusFinishTimeGet();
    }

    Single<BolusFinishTimeResponse> read() {
        return isReady()
                .concatMapSingle(v -> BOLUS_FINISH_TIME_GET.get())
                .firstOrError()
                .doOnSuccess(this::checkResponse)
                .doOnSuccess(this::onResponse)
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "ReadBolusFinishTimeTask error"));
    }

    void onResponse(BolusFinishTimeResponse response) {
        PatchState patchState = pm.getPatchState();
        BolusCurrent bolusCurrent = pm.getBolusCurrent();
        long nowHistoryID = bolusCurrent.historyId(BolusType.NOW);
        long extHistoryID = bolusCurrent.historyId(BolusType.EXT);

        if (nowHistoryID > 0 && patchState.isBolusDone(BolusType.NOW) && response.getNowBolusFinishTime() > 0) {
            bolusCurrent.setEndTimeSynced(BolusType.NOW, true);
            enqueue(TaskFunc.STOP_NOW_BOLUS);
        }

        if (extHistoryID > 0 && patchState.isBolusDone(BolusType.EXT) && response.getExtBolusFinishTime() > 0) {
            bolusCurrent.setEndTimeSynced(BolusType.EXT, true);
            enqueue(TaskFunc.STOP_EXT_BOLUS);
        }

        pm.flushBolusCurrent();
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
