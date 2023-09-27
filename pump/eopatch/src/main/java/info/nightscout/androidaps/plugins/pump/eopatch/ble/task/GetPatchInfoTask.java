package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import java.util.Arrays;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.logging.LTag;
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
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

@Singleton
public class GetPatchInfoTask extends TaskBase {
    @Inject UpdateConnectionTask updateConnectionTask;

    private final SetGlobalTime SET_GLOBAL_TIME;
    private final GetSerialNumber SERIAL_NUMBER_GET;
    private final GetLOT LOT_NUMBER_GET;
    private final GetFirmwareVersion FIRMWARE_VERSION_GET;
    private final GetWakeUpTime WAKE_UP_TIME_GET;
    private final GetPumpDuration PUMP_DURATION_GET;
    private final GetModelName GET_MODEL_NAME;

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
                .filter(v -> !v)
                .first(true);

        return isReady()
                .concatMapSingle(it -> tasks)
                .firstOrError()
                .observeOn(Schedulers.io())
                .doOnSuccess(this::onPatchWakeupSuccess)
                .doOnError(this::onPatchWakeupFailed)
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "GetPatchInfoTask error"));
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
        pm.getPatchConfig().setPumpDurationLargeMilli(v.getDurationL() * 100L);
        pm.getPatchConfig().setPumpDurationMediumMilli(v.getDurationM() * 100L);
        pm.getPatchConfig().setPumpDurationSmallMilli(v.getDurationS() * 100L);
    }

    private void onModelNameResponse(ModelNameResponse modelNameResponse) {
        pm.getPatchConfig().setPatchModelName(modelNameResponse.getModelName());
    }

    private void onPatchWakeupSuccess(Boolean result) {
        synchronized (lock) {
            pm.flushPatchConfig();
        }
    }

    private void onPatchWakeupFailed(Throwable e) {
        patch.setSeq(-1);
        pm.getPatchConfig().updateDeactivated();
        pm.flushPatchConfig();
    }
}
