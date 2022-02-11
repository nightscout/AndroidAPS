package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.PatchStateManager;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BasalResume;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BaseResponse;

import java.sql.SQLException;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.core.response.PatchBooleanResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.NormalBasal;
import io.reactivex.Single;

@Singleton
public class ResumeBasalTask extends TaskBase {
    @Inject
    StartNormalBasalTask startNormalBasalTask;

    @Inject
    PatchStateManager patchStateManager;

    private BasalResume BASAL_RESUME;

    @Inject
    public ResumeBasalTask() {
        super(TaskFunc.RESUME_BASAL);
        BASAL_RESUME = new BasalResume();
    }

    public synchronized Single<? extends BaseResponse> resume() {

        if (pm.getPatchConfig().getNeedSetBasalSchedule()) {
            NormalBasal normalBasal = pm.getNormalBasalManager().getNormalBasal();

            if (normalBasal != null) {
                return startNormalBasalTask.start(normalBasal, true);
            }
        }

        return isReady().concatMapSingle(v -> BASAL_RESUME.resume())
                .doOnNext(this::checkResponse)
                .firstOrError()
                .doOnSuccess(v -> onResumeResponse(v))
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, e.getMessage()));
    }

    private void onResumeResponse(PatchBooleanResponse v) throws SQLException {
        if (v.isSuccess()) {
            patchStateManager.onBasalResumed(v.getTimestamp() + 1000);
        }
        enqueue(TaskFunc.UPDATE_CONNECTION);
    }

    @Override
    protected void preCondition() throws Exception {
        checkPatchActivated();
        checkPatchConnected();
    }

}
