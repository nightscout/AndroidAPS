package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetFirmwareVersion;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetLOT;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetModelName;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetPumpDuration;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetSerialNumber;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetWakeUpTime;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.SetGlobalTime;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BaseResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.FirmwareVersionResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.LotNumberResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.ModelNameResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.PumpDurationResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.SerialNumberResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.WakeUpTimeResponse;

import java.util.Arrays;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;

@Singleton
public class GetPatchInfoTask extends TaskBase {

    @Inject
    UpdateConnectionTask updateConnectionTask;

    private SetGlobalTime SET_GLOBAL_TIME;
    private GetSerialNumber SERIAL_NUMBER_GET;
    private GetLOT LOT_NUMBER_GET;
    private GetFirmwareVersion FIRMWARE_VERSION_GET;
    private GetWakeUpTime WAKE_UP_TIME_GET;
    private GetPumpDuration PUMP_DURATION_GET;
    private GetModelName GET_MODEL_NAME;

    @Inject
    public GetPatchInfoTask() {
        super(TaskFunc.GET_PATCH_INFO);

        SET_GLOBAL_TIME = new SetGlobalTime();
        SERIAL_NUMBER_GET = new GetSerialNumber();
        LOT_NUMBER_GET = new GetLOT();
        FIRMWARE_VERSION_GET = new GetFirmwareVersion();
        WAKE_UP_TIME_GET = new GetWakeUpTime();
        PUMP_DURATION_GET = new GetPumpDuration();
        GET_MODEL_NAME = new GetModelName();
    }

    public Single<Boolean> get() {
        Single<Boolean> tasks = Single.concat(Arrays.asList(
                SET_GLOBAL_TIME.set(),
                SERIAL_NUMBER_GET.get().doOnSuccess(this::onSerialNumberResponse),
                LOT_NUMBER_GET.get().doOnSuccess(this::onLotNumberResponse),
                FIRMWARE_VERSION_GET.get().doOnSuccess(this::onFirmwareResponse),
                WAKE_UP_TIME_GET.get().doOnSuccess(this::onWakeupTimeResponse),
                PUMP_DURATION_GET.get().doOnSuccess(this::onPumpDurationResponse),
                GET_MODEL_NAME.get().doOnSuccess(this::onModelNameResponse)))
                .map(BaseResponse::isSuccess)
                .filter(v -> !v) // fail 시 false 가 아래로 내려간다.
                .first(true);

        return isReady()
                .concatMapSingle(it -> tasks)
                .firstOrError()
//                .flatMap(v -> updateConnectionTask.update()).map(v -> true)
                .observeOn(Schedulers.io())
                .doOnSuccess(this::onPatchWakeupSuccess)
                .doOnError(this::onPatchWakeupFailed)
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, e.getMessage()));
    }

    private void onSerialNumberResponse(SerialNumberResponse v) {
        pm.getPatchConfig().setPatchSerialNumber(v.getSerialNumber());
    }

    private void onLotNumberResponse(LotNumberResponse v) {
        pm.getPatchConfig().setPatchLotNumber(v.getLotNumber());
    }

    private void onFirmwareResponse(FirmwareVersionResponse v) {
        pm.getPatchConfig().setPatchFirmwareVersion(v.getFirmwareVersionString());
    }

    private void onWakeupTimeResponse(WakeUpTimeResponse v) {
        pm.getPatchConfig().setPatchWakeupTimestamp(v.getTimeInMillis());
    }

    private void onPumpDurationResponse(PumpDurationResponse v) {
        pm.getPatchConfig().setPumpDurationLargeMilli(v.getDurationL() * 100); // 0.1 초 단위
        pm.getPatchConfig().setPumpDurationMediumMilli(v.getDurationM() * 100);
        pm.getPatchConfig().setPumpDurationSmallMilli(v.getDurationS() * 100);
    }

    private void onModelNameResponse(ModelNameResponse modelNameResponse) {
        pm.getPatchConfig().setPatchModelName(modelNameResponse.getModelName());
    }

    /* Schedulers.io() */
    private void onPatchWakeupSuccess(Boolean result) {
        synchronized (lock) {
        	pm.flushPatchConfig();
        }
    }

    /* Schedulers.io() */
    private void onPatchWakeupFailed(Throwable e) {
        patch.setSeq(-1);
        pm.getPatchConfig().updateDeactivated();
        pm.flushPatchConfig();
    }
}
