package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.PatchStateManager;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BasalScheduleSetBig;

import java.sql.SQLException;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BasalScheduleSetResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.NormalBasal;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

@Singleton
public class StartNormalBasalTask extends TaskBase {

    private BasalScheduleSetBig BASAL_SCHEDULE_SET_BIG;

    @Inject
    PatchStateManager patchStateManager;

    @Inject
    public StartNormalBasalTask() {
        super(TaskFunc.START_NORMAL_BASAL);
        BASAL_SCHEDULE_SET_BIG = new BasalScheduleSetBig();
    }

    public Single<BasalScheduleSetResponse> start(NormalBasal basal, boolean resume) {
        return isReady().concatMapSingle(v -> startJob(basal, resume)).firstOrError();
    }

    public Single<BasalScheduleSetResponse> startJob(NormalBasal basal, boolean resume) {
        return BASAL_SCHEDULE_SET_BIG.set(basal.getDoseUnitPerSegmentArray())
                   .doOnSuccess(this::checkResponse)
                   .observeOn(Schedulers.io())
                   .doOnSuccess(v -> onStartNormalBasalResponse(v, basal, resume))
                   .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, e.getMessage()));
    }

    private void onStartNormalBasalResponse(BasalScheduleSetResponse response,
            NormalBasal basal, boolean resume) throws SQLException {

        long timeStamp = response.getTimestamp();
        patchStateManager.onBasalStarted(basal, timeStamp+1000);
        enqueue(TaskFunc.UPDATE_CONNECTION);
    }

    @Override
    protected void preCondition() throws Exception {
        checkPatchConnected();
    }
}
