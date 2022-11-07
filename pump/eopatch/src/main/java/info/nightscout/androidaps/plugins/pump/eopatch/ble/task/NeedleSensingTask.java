package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode;
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.IAlarmRegistry;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.StartNeedleCheck;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.UpdateConnection;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState;
import info.nightscout.rx.logging.LTag;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class NeedleSensingTask extends TaskBase {
    @Inject IAlarmRegistry alarmRegistry;

    StartNeedleCheck START_NEEDLE_CHECK;
    UpdateConnection UPDATE_CONNECTION;

    @Inject
    public NeedleSensingTask() {
        super(TaskFunc.NEEDLE_SENSING);
        START_NEEDLE_CHECK = new StartNeedleCheck();
        UPDATE_CONNECTION = new UpdateConnection();
    }

    public Single<Boolean> start() {

        return isReady()
                .concatMapSingle(v -> START_NEEDLE_CHECK.start())
                .doOnNext(this::checkResponse)
                .concatMapSingle(v -> UPDATE_CONNECTION.get())
                .doOnNext(this::checkResponse)
                .map(updateConnectionResponse -> PatchState.Companion.create(updateConnectionResponse.getPatchState(), System.currentTimeMillis()))
                .doOnNext(this::onResponse)
                .map(patchState -> !patchState.isNeedNeedleSensing())
                .firstOrError()
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "NeedleSensingTask error"));
    }

    private void onResponse(PatchState v) {
        if (v.isNeedNeedleSensing()) {
            alarmRegistry.add(AlarmCode.A016, 0, false).subscribe();
        } else {
            alarmRegistry.remove(AlarmCode.A016).subscribe();
        }
    }
}
