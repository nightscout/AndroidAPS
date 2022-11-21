package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.interfaces.pump.DetailedBolusInfo;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BolusStart;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BolusResponse;
import info.nightscout.rx.logging.LTag;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class StartCalcBolusTask extends BolusTask {
    private final BolusStart NOW_BOLUS_START;

    @Inject
    public StartCalcBolusTask() {
        super(TaskFunc.START_CALC_BOLUS);

        NOW_BOLUS_START = new BolusStart();
    }

    public Single<? extends BolusResponse> start(DetailedBolusInfo detailedBolusInfo) {
        return isReady().concatMapSingle(v -> startBolusImpl((float)detailedBolusInfo.insulin))
                .doOnNext(this::checkResponse)
                .firstOrError()
                .doOnSuccess(v -> onSuccess((float)detailedBolusInfo.insulin))
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "StartCalcBolusTask error"));
    }

    private Single<? extends BolusResponse> startBolusImpl(float nowDoseU) {
        return NOW_BOLUS_START.start(nowDoseU);
    }

    private void onSuccess(float nowDoseU) {
        onCalcBolusStarted(nowDoseU);
        enqueue(TaskFunc.UPDATE_CONNECTION);
    }

    @Override
    protected void preCondition() throws Exception {
        checkPatchConnected();
    }
}
