package info.nightscout.androidaps.plugins.pump.eopatch.ble;

import android.content.Context;
import android.content.Intent;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.interfaces.pump.DetailedBolusInfo;
import app.aaps.core.interfaces.pump.PumpSync;
import app.aaps.core.interfaces.pump.defs.PumpType;
import app.aaps.core.interfaces.resources.ResourceHelper;
import app.aaps.core.interfaces.rx.AapsSchedulers;
import app.aaps.core.interfaces.rx.bus.RxBus;
import app.aaps.core.interfaces.rx.events.EventCustomActionsChanged;
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged;
import app.aaps.core.interfaces.rx.events.EventRefreshOverview;
import app.aaps.core.interfaces.sharedPreferences.SP;
import app.aaps.core.interfaces.utils.DateUtil;
import info.nightscout.androidaps.plugins.pump.eopatch.R;
import info.nightscout.androidaps.plugins.pump.eopatch.RxAction;
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode;
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.IAlarmRegistry;
import info.nightscout.androidaps.plugins.pump.eopatch.code.BolusExDuration;
import info.nightscout.androidaps.plugins.pump.eopatch.code.DeactivationStatus;
import info.nightscout.androidaps.plugins.pump.eopatch.code.PatchLifecycle;
import info.nightscout.androidaps.plugins.pump.eopatch.code.SettingKeys;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BasalScheduleSetResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BaseResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BolusResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.BolusStopResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.ComboBolusStopResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.PatchBooleanResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.TempBasalScheduleSetResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.response.TemperatureResponse;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.BleConnectionState;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.IPatchScanner;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.PatchScanner;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.PatchSelfTestResult;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.ScanList;
import info.nightscout.androidaps.plugins.pump.eopatch.event.EventPatchActivationNotComplete;
import info.nightscout.androidaps.plugins.pump.eopatch.ui.DialogHelperActivity;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.BolusCurrent;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.NormalBasal;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchConfig;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchLifecycleEvent;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.TempBasal;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

@Singleton
public class PatchManager implements IPatchManager {

    @Inject PatchManagerImpl patchManager;
    @Inject IPreferenceManager pm;
    @Inject ResourceHelper resourceHelper;
    @Inject RxBus rxBus;
    @Inject Context context;
    @Inject SP sp;
    @Inject PumpSync pumpSync;
    @Inject DateUtil dateUtil;
    @Inject RxAction rxAction;
    @Inject AapsSchedulers aapsSchedulers;
    @Inject IAlarmRegistry alarmRegistry;

    private IPatchScanner patchScanner;
    private final CompositeDisposable mCompositeDisposable = new CompositeDisposable();
    private Disposable mConnectingDisposable = null;

    @Inject
    public PatchManager() {
    }

    @Inject
    void onInit() {
        patchScanner = new PatchScanner(context);

        mCompositeDisposable.add(observePatchConnectionState()
                .subscribe(bleConnectionState -> {
                    switch (bleConnectionState) {
                        case DISCONNECTED:
                            rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED));
                            rxBus.send(new EventRefreshOverview("Eopatch connection state: " + bleConnectionState.name(), true));
                            rxBus.send(new EventCustomActionsChanged());
                            stopObservingConnection();
                            break;

                        case CONNECTED:
                            rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED));
                            rxBus.send(new EventRefreshOverview("Eopatch connection state: " + bleConnectionState.name(), true));
                            rxBus.send(new EventCustomActionsChanged());
                            stopObservingConnection();
                            break;

                        case CONNECTING:
                            mConnectingDisposable = Observable.interval(0, 1, TimeUnit.SECONDS)
                                    .observeOn(aapsSchedulers.getMain())
                                    .takeUntil(n -> getPatchConnectionState().isConnected() || n > 10 * 60)
                                    .subscribe(n -> rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, n.intValue())));
                            break;

                        default:
                            stopObservingConnection();
                    }
                })
        );
        mCompositeDisposable.add(rxBus
                .toObservable(EventPatchActivationNotComplete.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribeOn(aapsSchedulers.getMain())
                .subscribe(eventPatchActivationNotComplete -> {
                    Intent i = new Intent(context, DialogHelperActivity.class);
                    i.putExtra("title", resourceHelper.gs(R.string.patch_activate_reminder_title));
                    i.putExtra("message", resourceHelper.gs(R.string.patch_activate_reminder_desc));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);
                })
        );

    }

    @Override
    public void init() {
        setConnection();
    }

    private void stopObservingConnection() {
        if (mConnectingDisposable != null) {
            mConnectingDisposable.dispose();
            mConnectingDisposable = null;
        }
    }

    @Override
    public IPreferenceManager getPreferenceManager() {
        return pm;
    }

    @Override
    public PatchConfig getPatchConfig() {
        return pm.getPatchConfig();
    }

    @Override
    public Observable<PatchLifecycle> observePatchLifeCycle() {
        return pm.observePatchLifeCycle();
    }

    @Override
    public synchronized void updatePatchLifeCycle(PatchLifecycleEvent event) {
        pm.updatePatchLifeCycle(event);
    }

    @Override
    public BleConnectionState getPatchConnectionState() {
        return patchManager.getPatchConnectionState();
    }

    @Override
    public Observable<BleConnectionState> observePatchConnectionState() {
        return patchManager.observePatchConnectionState();
    }

    @Override
    public PatchState getPatchState() {
        return pm.getPatchState();
    }

    @Override
    public void updatePatchState(PatchState state) {
        pm.getPatchState().update(state);
        pm.flushPatchState();
    }

    @Override
    public Observable<PatchState> observePatchState() {
        return pm.observePatchState();
    }

    @Override
    public long getPatchExpiredTime() {
        return pm.getPatchConfig().getPatchExpiredTime();
    }

    @Override
    public BolusCurrent getBolusCurrent() {
        return pm.getBolusCurrent();
    }

    @Override
    public Observable<BolusCurrent> observeBolusCurrent() {
        return pm.observeBolusCurrent();
    }


    public void connect() {
        // Nothing (Auto Connect mode)
    }

    public void disconnect() {
        // Nothing (Auto Connect mode)
    }

    @Override
    public void setConnection() {
        if (pm.getPatchConfig().hasMacAddress()) {
            patchManager.updateMacAddress(pm.getPatchConfig().getMacAddress(), false);
        }
    }

    public boolean isActivated() {
        return pm.getPatchConfig().isActivated();
    }

    public boolean isDeactivated() {
        return pm.getPatchConfig().isDeactivated();
    }

    public Single<Boolean> startBond(String mac) {
        return patchManager.startBond(mac);
    }

    public Single<Boolean> getPatchInfo(long timeout) {
        return patchManager.getPatchInfo(timeout);
    }

    public Single<PatchSelfTestResult> selfTest(long timeout) {
        return patchManager.selfTest(timeout);
    }

    public Single<TemperatureResponse> getTemperature() {
        return patchManager.getTemperature();
    }

    public Observable<Long> startPriming(long timeout, long count) {
        return patchManager.startPriming(timeout, count);
    }

    public Single<Boolean> checkNeedleSensing(long timeout) {
        return patchManager.checkNeedleSensing(timeout);
    }

    public Single<Boolean> patchActivation(long timeout) {
        return patchManager.patchActivation(timeout)
                .doOnSuccess(success -> {
                    if (success) {
                        pumpSync.connectNewPump(true);
                        Thread.sleep(1000);
                        pumpSync.insertTherapyEventIfNewWithTimestamp(
                                System.currentTimeMillis(),
                                DetailedBolusInfo.EventType.CANNULA_CHANGE,
                                null,
                                null,
                                PumpType.EOFLOW_EOPATCH2,
                                getPatchConfig().getPatchSerialNumber()
                        );
                        pumpSync.insertTherapyEventIfNewWithTimestamp(
                                System.currentTimeMillis(),
                                DetailedBolusInfo.EventType.INSULIN_CHANGE,
                                null,
                                null,
                                PumpType.EOFLOW_EOPATCH2,
                                getPatchConfig().getPatchSerialNumber()
                        );
                    }
                });
    }

    public Single<BasalScheduleSetResponse> startBasal(NormalBasal basal) {
        return patchManager.startBasal(basal);
    }

    public Single<? extends BaseResponse> resumeBasal() {
        return patchManager.resumeBasal();
    }


    public Single<? extends BaseResponse> pauseBasal(float pauseDurationHour) {
        return patchManager.pauseBasal(pauseDurationHour);
    }

    //==============================================================================================
    // IPatchManager interface [TEMP BASAL]
    //==============================================================================================

    public Single<TempBasalScheduleSetResponse> startTempBasal(TempBasal tempBasal) {
        return patchManager.startTempBasal(tempBasal);
    }

    // 템프베이젤 주입 정지
    // 템프베이젤이 정지되면 자동으로 노멀베이젤이 활성화된다
    // 외부에서 호출된다. 즉 명시적으로 tempBasal 정지. 이 때는 normalBasal resume 은 PatchState 보고 처리.

    public Single<PatchBooleanResponse> stopTempBasal() {
        return patchManager.stopTempBasal();
    }


    public Single<? extends BolusResponse> startQuickBolus(float nowDoseU, float exDoseU,
                                                           BolusExDuration exDuration) {
        return patchManager.startQuickBolus(nowDoseU, exDoseU, exDuration);
    }


    public Single<? extends BolusResponse> startCalculatorBolus(DetailedBolusInfo detailedBolusInfo) {
        return patchManager.startCalculatorBolus(detailedBolusInfo);
    }


    public Single<BolusStopResponse> stopNowBolus() {
        return patchManager.stopNowBolus();
    }


    public Single<BolusStopResponse> stopExtBolus() {
        return patchManager.stopExtBolus();
    }


    public Single<ComboBolusStopResponse> stopComboBolus() {
        return patchManager.stopComboBolus();
    }

    public Single<DeactivationStatus> deactivate(long timeout, boolean force) {
        return patchManager.deactivate(timeout, force);
    }

    public Single<PatchBooleanResponse> infoReminderSet(boolean infoReminder) {
        return patchManager.infoReminderSet(infoReminder);
    }

    public Single<PatchBooleanResponse> setLowReservoir(int doseUnit, int hours) {
        return patchManager.setLowReservoir(doseUnit, hours);
    }

    public Single<PatchState> updateConnection() {
        return patchManager.updateConnection();
    }

    public Single<PatchBooleanResponse> stopAeBeep(int aeCode) {
        return patchManager.stopAeBeep(aeCode);
    }

    @Override
    public Single<ScanList> scan(long timeout) {
        patchManager.updateMacAddress("", false);
        pm.getPatchConfig().setMacAddress("");
        return patchScanner.scan(timeout);
    }

    @Override
    public void addBolusToHistory(DetailedBolusInfo originalDetailedBolusInfo) {
        DetailedBolusInfo detailedBolusInfo = originalDetailedBolusInfo.copy();

        if (detailedBolusInfo.insulin > 0) {
            pumpSync.syncBolusWithPumpId(
                    dateUtil.now(), // Use real timestamp to have it different from carbs (otherwise NS sync fail)
                    detailedBolusInfo.insulin,
                    detailedBolusInfo.getBolusType(),
                    dateUtil.now(),
                    PumpType.EOFLOW_EOPATCH2,
                    patchManager.pm.getPatchSerial()
            );
        }
    }

    @Override
    public void changeBuzzerSetting() {
        boolean buzzer = sp.getBoolean(SettingKeys.Companion.getBUZZER_REMINDERS(), false);
        if (pm.getPatchConfig().getInfoReminder() != buzzer) {
            if (isActivated()) {
                mCompositeDisposable.add(infoReminderSet(buzzer)
                        .observeOn(aapsSchedulers.getMain())
                        .subscribe(patchBooleanResponse -> {
                            pm.getPatchConfig().setInfoReminder(buzzer);
                            pm.flushPatchConfig();
                        }));
            } else {
                pm.getPatchConfig().setInfoReminder(buzzer);
                pm.flushPatchConfig();
            }
        }
    }

    @Override
    public void changeReminderSetting() {
        int doseUnit = sp.getInt(SettingKeys.Companion.getLOW_RESERVOIR_REMINDERS(), 0);
        int hours = sp.getInt(SettingKeys.Companion.getEXPIRATION_REMINDERS(), 0);
        PatchConfig pc = pm.getPatchConfig();
        if (pc.getLowReservoirAlertAmount() != doseUnit || pc.getPatchExpireAlertTime() != hours) {
            if (isActivated()) {
                mCompositeDisposable.add(setLowReservoir(doseUnit, hours)
                        .observeOn(aapsSchedulers.getMain())
                        .doOnSubscribe(disposable -> {
                            if (pc.getPatchExpireAlertTime() != hours) {
                                Maybe.just(AlarmCode.B000)
                                        .flatMap(alarmCode -> alarmRegistry.remove(alarmCode))
                                        .flatMap(alarmCode -> alarmRegistry.add(alarmCode, (pc.getExpireTimestamp() - System.currentTimeMillis() - TimeUnit.HOURS.toMillis(hours)), false))
                                        .subscribe();
                            }
                        })
                        .subscribe(patchBooleanResponse -> {
                            pc.setLowReservoirAlertAmount(doseUnit);
                            pc.setPatchExpireAlertTime(hours);
                            pm.flushPatchConfig();
                        }));
            } else {
                pc.setLowReservoirAlertAmount(doseUnit);
                pc.setPatchExpireAlertTime(hours);
                pm.flushPatchConfig();
            }
        }
    }

    @Override
    public void checkActivationProcess() {
        if (getPatchConfig().getLifecycleEvent().isSubStepRunning()
                && !pm.getAlarms().isOccurring(AlarmCode.A005)
                && !pm.getAlarms().isOccurring(AlarmCode.A020)) {
            rxAction.runOnMainThread(() -> rxBus.send(new EventPatchActivationNotComplete()));
        }
    }
}
