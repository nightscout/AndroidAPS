package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import info.nightscout.shared.logging.LTag;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.PatchSelfTestResult;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetGlobalTime;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetTemperature;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetVoltageLevelB4Priming;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BatteryVoltageLevelPairingResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.GlobalTimeResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.TemperatureResponse;

import java.util.Arrays;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.Single;

@Singleton
public class SelfTestTask extends TaskBase {

    private GetTemperature TEMPERATURE_GET;
    private GetVoltageLevelB4Priming BATTERY_LEVEL_GET_BEFORE_PRIMING;
    private GetGlobalTime GET_GLOBAL_TIME;

    @Inject
    public SelfTestTask() {
        super(TaskFunc.SELF_TEST);

        TEMPERATURE_GET = new GetTemperature();
        BATTERY_LEVEL_GET_BEFORE_PRIMING = new GetVoltageLevelB4Priming();
        GET_GLOBAL_TIME = new GetGlobalTime();
    }

    public Single<PatchSelfTestResult> start() {
        Single<PatchSelfTestResult> tasks = Single.concat(Arrays.asList(
                TEMPERATURE_GET.get()
                        .map(TemperatureResponse::getResult)
                        .doOnSuccess(this::onTemperatureResult),
                BATTERY_LEVEL_GET_BEFORE_PRIMING.get()
                        .map(BatteryVoltageLevelPairingResponse::getResult)
                        .doOnSuccess(this::onBatteryResult),
                GET_GLOBAL_TIME.get(false)
                        .map(GlobalTimeResponse::getResult)
                        .doOnSuccess(this::onTimeResult)))
                .filter(result -> result != PatchSelfTestResult.TEST_SUCCESS)
                .first(PatchSelfTestResult.TEST_SUCCESS);

        return isReady()
                .concatMapSingle(v -> tasks)
                .firstOrError()
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, e.getMessage()));
    }

    private void onTemperatureResult(PatchSelfTestResult patchSelfTestResult) {
        if (patchSelfTestResult != PatchSelfTestResult.TEST_SUCCESS) {
        }
    }

    private void onBatteryResult(PatchSelfTestResult patchSelfTestResult) {
        if (patchSelfTestResult != PatchSelfTestResult.TEST_SUCCESS) {
        }
    }

    private void onTimeResult(PatchSelfTestResult patchSelfTestResult) {
    }
}
