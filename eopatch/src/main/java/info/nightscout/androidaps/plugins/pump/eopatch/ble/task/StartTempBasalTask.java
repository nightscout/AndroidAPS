package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager;
import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.TempBasalScheduleStart;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.core.response.TempBasalScheduleSetResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.TempBasal;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

@Singleton
public class StartTempBasalTask extends TaskBase {
    @Inject IPreferenceManager pm;
    private final TempBasalScheduleStart TEMP_BASAL_SCHEDULE_START;

    @Inject
    public StartTempBasalTask() {
        super(TaskFunc.START_TEMP_BASAL);

        TEMP_BASAL_SCHEDULE_START = new TempBasalScheduleStart();
    }

    public Single<TempBasalScheduleSetResponse> start(TempBasal tempBasal) {
        return isReady()
                .concatMapSingle(v -> TEMP_BASAL_SCHEDULE_START.start(tempBasal.getDurationMinutes(), tempBasal.getDoseUnitPerHour(), tempBasal.getPercent()))
                .doOnNext(this::checkResponse)
                .firstOrError()
                .observeOn(Schedulers.io())
                .doOnSuccess(v -> onTempBasalStarted(tempBasal))
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "StartTempBasalTask error"));
    }

    private void onTempBasalStarted(TempBasal tempBasal) {
        pm.getTempBasalManager().updateBasalRunning(tempBasal);
        pm.flushTempBasalManager();
        enqueue(TaskFunc.UPDATE_CONNECTION);
    }

    @Override
    protected void preCondition() throws Exception {
        checkPatchConnected();
    }
}
