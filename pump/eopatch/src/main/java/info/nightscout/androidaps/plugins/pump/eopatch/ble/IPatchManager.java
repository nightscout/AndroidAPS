package info.nightscout.androidaps.plugins.pump.eopatch.ble;


import app.aaps.core.interfaces.pump.DetailedBolusInfo;
import info.nightscout.androidaps.plugins.pump.eopatch.code.BolusExDuration;
import info.nightscout.androidaps.plugins.pump.eopatch.code.DeactivationStatus;
import info.nightscout.androidaps.plugins.pump.eopatch.code.PatchLifecycle;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BasalScheduleSetResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BaseResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BolusResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BolusStopResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.ComboBolusStopResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.PatchBooleanResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.TempBasalScheduleSetResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.TemperatureResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.BleConnectionState;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.PatchSelfTestResult;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.ScanList;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.BolusCurrent;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.NormalBasal;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchConfig;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchLifecycleEvent;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.TempBasal;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

public interface IPatchManager {
    void init();

    IPreferenceManager getPreferenceManager();

    PatchConfig getPatchConfig();

    boolean isActivated();

    boolean isDeactivated();

    Single<? extends BaseResponse> resumeBasal();

    Observable<PatchLifecycle> observePatchLifeCycle();

    Observable<PatchState> observePatchState();

    BleConnectionState getPatchConnectionState();

    void connect();

    void disconnect();

    PatchState getPatchState();

    void updatePatchState(PatchState state);

    BolusCurrent getBolusCurrent();

    Single<DeactivationStatus> deactivate(long timeout, boolean force);

    Observable<BleConnectionState> observePatchConnectionState();

    Observable<BolusCurrent> observeBolusCurrent();

    void setConnection();

    Single<BolusStopResponse> stopNowBolus();

    Single<BolusStopResponse> stopExtBolus();

    Single<ComboBolusStopResponse> stopComboBolus();

    Single<? extends BolusResponse> startQuickBolus(float nowDoseU, float exDoseU, BolusExDuration exDuration);

    Single<? extends BolusResponse> startCalculatorBolus(DetailedBolusInfo detailedBolusInfo);


    Single<PatchBooleanResponse> infoReminderSet(boolean infoReminder);

    Single<PatchBooleanResponse> setLowReservoir(int doseUnit, int hours);

    Single<PatchState> updateConnection();

    long getPatchExpiredTime();

    Single<BasalScheduleSetResponse> startBasal(NormalBasal basal);

    void updatePatchLifeCycle(PatchLifecycleEvent event);

    Single<Boolean> startBond(String mac);

    Single<Boolean> getPatchInfo(long timeout);

    Single<PatchSelfTestResult> selfTest(long timeout);

    Observable<Long> startPriming(long timeout, long count);

    Single<Boolean> checkNeedleSensing(long timeout);

    Single<Boolean> patchActivation(long timeout);

    Single<PatchBooleanResponse> stopAeBeep(int aeCode);

    Single<TempBasalScheduleSetResponse> startTempBasal(TempBasal tempBasal);

    Single<? extends BaseResponse> pauseBasal(float pauseDurationHour);

    Single<ScanList> scan(long timeout);

    Single<PatchBooleanResponse> stopTempBasal();

    Single<TemperatureResponse> getTemperature();

    void addBolusToHistory(DetailedBolusInfo originalDetailedBolusInfo);

    void changeBuzzerSetting();

    void changeReminderSetting();

    void checkActivationProcess();
}
