package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode;
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.IAlarmRegistry;
import info.nightscout.androidaps.plugins.pump.eopatch.ble.PatchStateManager;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.BasalResume;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BaseResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.PatchBooleanResponse;
import info.nightscout.rx.logging.LTag;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class ResumeBasalTask extends TaskBase {
    @Inject IAlarmRegistry alarmRegistry;
    @Inject StartNormalBasalTask startNormalBasalTask;
    @Inject PatchStateManager patchStateManager;

    private final BasalResume BASAL_RESUME;

    @Inject
    public ResumeBasalTask() {
        super(TaskFunc.RESUME_BASAL);
        BASAL_RESUME = new BasalResume();
    }

    public synchronized Single<? extends BaseResponse> resume() {
        if (pm.getPatchConfig().getNeedSetBasalSchedule()) {
            return startNormalBasalTask.start(pm.getNormalBasalManager().getNormalBasal());
        }

        return isReady().concatMapSingle(v -> BASAL_RESUME.resume())
                .doOnNext(this::checkResponse)
                .firstOrError()
                .doOnSuccess(v -> onResumeResponse(v))
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "ResumeBasalTask error"));
    }

    private void onResumeResponse(PatchBooleanResponse v) {
        if (v.isSuccess()) {
            patchStateManager.onBasalResumed(v.getTimestamp() + 1000);
            alarmRegistry.remove(AlarmCode.B001).subscribe();
        }
        enqueue(TaskFunc.UPDATE_CONNECTION);
    }

    @Override
    protected void preCondition() throws Exception {
        checkPatchActivated();
        checkPatchConnected();
    }

}
