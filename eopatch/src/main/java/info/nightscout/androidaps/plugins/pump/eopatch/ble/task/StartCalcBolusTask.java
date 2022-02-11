package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.code.BolusExDuration;

import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BolusStart;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.ComboBolusStart;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.ExtBolusStart;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BolusResponse;
import io.reactivex.Single;

@Singleton
public class StartCalcBolusTask extends BolusTask {

    private BolusStart NOW_BOLUS_START;
    private ExtBolusStart EXT_BOLUS_START;
    private ComboBolusStart COMBO_BOLUS_START;

    @Inject
    public StartCalcBolusTask() {
        super(TaskFunc.START_CALC_BOLUS);

        NOW_BOLUS_START = new BolusStart();
        EXT_BOLUS_START = new ExtBolusStart();
        COMBO_BOLUS_START = new ComboBolusStart();
    }

    public Single<? extends BolusResponse> start(DetailedBolusInfo detailedBolusInfo) {
        return isReady().concatMapSingle(v -> startBolusImpl((float)detailedBolusInfo.insulin, 0f, BolusExDuration.OFF))
                .doOnNext(this::checkResponse)
                .firstOrError()
                .doOnSuccess(v -> onSuccess((float)detailedBolusInfo.insulin, (float)detailedBolusInfo.insulin, 0f, BolusExDuration.OFF))
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, e.getMessage()));
    }

    private Single<? extends BolusResponse> startBolusImpl(float nowDoseU, float exDoseU,
                                                           BolusExDuration exDuration) {
        if (nowDoseU > 0 && exDoseU > 0) {
            return COMBO_BOLUS_START.start(nowDoseU, exDoseU, exDuration.getMinute());
        } else if (exDoseU > 0) {
            return EXT_BOLUS_START.start(exDoseU, exDuration.getMinute());
        } else {
            return NOW_BOLUS_START.start(nowDoseU);
        }
    }

    private void onSuccess(float nowDoseU, float correctionBolus, float exDoseU, BolusExDuration exDuration) {
        onCalcBolusStarted(nowDoseU, correctionBolus, exDoseU, exDuration);
        enqueue(TaskFunc.UPDATE_CONNECTION);
    }

    @Override
    protected void preCondition() throws Exception {
        //checkPatchActivated();
        checkPatchConnected();
    }
}
