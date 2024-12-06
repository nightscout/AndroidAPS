package info.nightscout.androidaps.plugins.pump.eopatch.ble;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.data.model.TE;
import app.aaps.core.data.pump.defs.PumpType;
import app.aaps.core.interfaces.pump.DetailedBolusInfo;
import app.aaps.core.interfaces.pump.PumpSync;
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
import info.nightscout.androidaps.plugins.pump.eopatch.code.SettingKeys;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.IPatchScanner;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.PatchScanner;
import info.nightscout.androidaps.plugins.pump.eopatch.core.scan.ScanList;
import info.nightscout.androidaps.plugins.pump.eopatch.event.EventPatchActivationNotComplete;
import info.nightscout.androidaps.plugins.pump.eopatch.ui.DialogHelperActivity;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.Alarms;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchConfig;
import info.nightscout.androidaps.plugins.pump.eopatch.vo.PatchState;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

@Singleton
public class PatchManager implements IPatchManager {

    private final CompositeDisposable mCompositeDisposable = new CompositeDisposable();
    @Inject PatchManagerExecutor aapsPatchManager;
    @Inject PreferenceManager pm;
    @Inject Alarms alarms;
    @Inject PatchConfig patchConfig;
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
    @Nullable private Disposable mConnectingDisposable = null;

    @Inject
    public PatchManager() {
    }

    @Inject
    void onInit() {
        patchScanner = new PatchScanner(context);

        mCompositeDisposable.add(aapsPatchManager.observePatchConnectionState()
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
                                    .takeUntil(n -> aapsPatchManager.getPatchConnectionState().isConnected() || n > 10 * 60)
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
    public void updatePatchState(@NonNull PatchState state) {
        pm.getPatchState().update(state);
        pm.flushPatchState();
    }

    @Override
    public void setConnection() {
        if (patchConfig.hasMacAddress()) {
            aapsPatchManager.updateMacAddress(patchConfig.getMacAddress(), false);
        }
    }

    @NonNull public Single<Boolean> patchActivation(long timeout) {
        return aapsPatchManager.patchActivation(timeout)
                .doOnSuccess(success -> {
                    if (success) {
                        pumpSync.connectNewPump(true);
                        Thread.sleep(1000);
                        pumpSync.insertTherapyEventIfNewWithTimestamp(
                                System.currentTimeMillis(),
                                TE.Type.CANNULA_CHANGE,
                                null,
                                null,
                                PumpType.EOFLOW_EOPATCH2,
                                patchConfig.getPatchSerialNumber()
                        );
                        pumpSync.insertTherapyEventIfNewWithTimestamp(
                                System.currentTimeMillis(),
                                TE.Type.INSULIN_CHANGE,
                                null,
                                null,
                                PumpType.EOFLOW_EOPATCH2,
                                patchConfig.getPatchSerialNumber()
                        );
                    }
                });
    }

    @NonNull @Override
    public Single<ScanList> scan(long timeout) {
        aapsPatchManager.updateMacAddress("", false);
        patchConfig.setMacAddress("");
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
                    patchConfig.getPatchSerialNumber()
            );
        }
    }

    @Override
    public void changeBuzzerSetting() {
        boolean buzzer = sp.getBoolean(SettingKeys.Companion.getBUZZER_REMINDERS(), false);
        if (patchConfig.getInfoReminder() != buzzer) {
            if (patchConfig.isActivated()) {
                mCompositeDisposable.add(aapsPatchManager.infoReminderSet(buzzer)
                        .observeOn(aapsSchedulers.getMain())
                        .subscribe(patchBooleanResponse -> {
                            patchConfig.setInfoReminder(buzzer);
                            pm.flushPatchConfig();
                        }));
            } else {
                patchConfig.setInfoReminder(buzzer);
                pm.flushPatchConfig();
            }
        }
    }

    @Override
    public void changeReminderSetting() {
        int doseUnit = sp.getInt(SettingKeys.Companion.getLOW_RESERVOIR_REMINDERS(), 0);
        int hours = sp.getInt(SettingKeys.Companion.getEXPIRATION_REMINDERS(), 0);
        PatchConfig pc = patchConfig;
        if (pc.getLowReservoirAlertAmount() != doseUnit || pc.getPatchExpireAlertTime() != hours) {
            if (patchConfig.isActivated()) {
                mCompositeDisposable.add(aapsPatchManager.setLowReservoir(doseUnit, hours)
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
        if (patchConfig.getLifecycleEvent().isSubStepRunning()
                && !alarms.isOccurring(AlarmCode.A005)
                && !alarms.isOccurring(AlarmCode.A020)) {
            rxAction.runOnMainThread(() -> rxBus.send(new EventPatchActivationNotComplete()));
        }
    }
}
