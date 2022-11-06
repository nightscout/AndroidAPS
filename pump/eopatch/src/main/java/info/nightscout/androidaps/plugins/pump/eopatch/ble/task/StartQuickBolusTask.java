package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.code.BolusExDuration;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BolusStart;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.ComboBolusStart;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.ExtBolusStart;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BolusResponse;
import info.nightscout.rx.logging.LTag;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class StartQuickBolusTask extends BolusTask {
    private final BolusStart NOW_BOLUS_START;
    private final ExtBolusStart EXT_BOLUS_START;
    private final ComboBolusStart COMBO_BOLUS_START;

    @Inject
    public StartQuickBolusTask() {
        super(TaskFunc.START_QUICK_BOLUS);

        NOW_BOLUS_START = new BolusStart();
        EXT_BOLUS_START = new ExtBolusStart();
        COMBO_BOLUS_START = new ComboBolusStart();
    }

    public Single<? extends BolusResponse> start(float nowDoseU, float exDoseU,
                                                 BolusExDuration exDuration) {
        return isReady().concatMapSingle(v -> startBolusImpl(nowDoseU, exDoseU, exDuration))
                .doOnNext(this::checkResponse)
                .firstOrError()
                .doOnSuccess(v -> onSuccess(nowDoseU, exDoseU, exDuration))
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "StartQuickBolusTask error"));
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

    private void onSuccess(float nowDoseU, float exDoseU, BolusExDuration exDuration) {
        onQuickBolusStarted(nowDoseU, exDoseU, exDuration);
        enqueue(TaskFunc.UPDATE_CONNECTION);
    }

    @Override
    protected void preCondition() throws Exception {
        //checkPatchActivated();
        checkPatchConnected();
    }
}
