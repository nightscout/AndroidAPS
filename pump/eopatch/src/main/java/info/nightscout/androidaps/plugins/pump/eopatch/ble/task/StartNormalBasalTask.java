package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.rx.AapsSchedulers;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.PatchStateManager;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BasalScheduleSetBig;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BasalScheduleSetResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.NormalBasal;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class StartNormalBasalTask extends TaskBase {
    private final BasalScheduleSetBig BASAL_SCHEDULE_SET_BIG;

    @Inject PatchStateManager patchStateManager;
    @Inject AapsSchedulers aapsSchedulers;

    @Inject
    public StartNormalBasalTask() {
        super(TaskFunc.START_NORMAL_BASAL);
        BASAL_SCHEDULE_SET_BIG = new BasalScheduleSetBig();
    }

    public Single<BasalScheduleSetResponse> start(NormalBasal basal) {
        return isReady().concatMapSingle(v -> startJob(basal)).firstOrError();
    }

    public Single<BasalScheduleSetResponse> startJob(NormalBasal basal) {
        return BASAL_SCHEDULE_SET_BIG.set(basal.getDoseUnitPerSegmentArray())
                .doOnSuccess(this::checkResponse)
                .observeOn(aapsSchedulers.getIo())
                .doOnSuccess(v -> onStartNormalBasalResponse(v, basal))
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "StartNormalBasalTask error"));
    }

    private void onStartNormalBasalResponse(BasalScheduleSetResponse response, NormalBasal basal) {

        long timeStamp = response.getTimestamp();
        patchStateManager.onBasalStarted(basal, timeStamp + 1000);

        pm.getNormalBasalManager().setNormalBasal(basal);
        pm.flushNormalBasalManager();
        enqueue(TaskFunc.UPDATE_CONNECTION);
    }

    @Override
    protected void preCondition() throws Exception {
        checkPatchConnected();
    }
}
