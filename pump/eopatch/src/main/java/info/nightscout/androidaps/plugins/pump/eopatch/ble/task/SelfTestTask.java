package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import java.util.Arrays;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetGlobalTime;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetTemperature;
import info.nightscout.androidaps.plugins.pump.eopatch.core.api.GetVoltageLevelB4Priming;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BatteryVoltageLevelPairingResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.GlobalTimeResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.TemperatureResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.PatchSelfTestResult;
import info.nightscout.rx.logging.LTag;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class SelfTestTask extends TaskBase {
    private final GetTemperature TEMPERATURE_GET;
    private final GetVoltageLevelB4Priming BATTERY_LEVEL_GET_BEFORE_PRIMING;
    private final GetGlobalTime GET_GLOBAL_TIME;

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
                        .map(TemperatureResponse::getResult),
                BATTERY_LEVEL_GET_BEFORE_PRIMING.get()
                        .map(BatteryVoltageLevelPairingResponse::getResult),
                GET_GLOBAL_TIME.get(false)
                        .map(GlobalTimeResponse::getResult)))
                .filter(result -> result != PatchSelfTestResult.TEST_SUCCESS)
                .first(PatchSelfTestResult.TEST_SUCCESS);

        return isReady()
                .concatMapSingle(v -> tasks)
                .firstOrError()
                .doOnError(e -> aapsLogger.error(LTag.PUMPCOMM, (e.getMessage() != null) ? e.getMessage() : "SelfTestTask error"));
    }
}
