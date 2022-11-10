package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.TempBasalScheduleStart;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.TempBasalScheduleSetResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.TempBasal;
import info.nightscout.rx.AapsSchedulers;
import info.nightscout.rx.logging.LTag;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class StartTempBasalTask extends TaskBase {
    @Inject IPreferenceManager pm;
    @Inject AapsSchedulers aapsSchedulers;

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
                .observeOn(aapsSchedulers.getIo())
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
