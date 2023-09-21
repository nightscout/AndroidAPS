package com.microtechmd.equil;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.common.Scopes;
import com.microtechmd.equil.data.BolusProfile;
import com.microtechmd.equil.data.RunMode;
import com.microtechmd.equil.data.database.BolusType;
import com.microtechmd.equil.data.database.EquilBolusRecord;
import com.microtechmd.equil.data.database.EquilHistoryPump;
import com.microtechmd.equil.data.database.EquilHistoryRecord;
import com.microtechmd.equil.data.database.EquilHistoryRecordDao;
import com.microtechmd.equil.data.database.EquilTempBasalRecord;
import com.microtechmd.equil.events.EventEquilDataChanged;
import com.microtechmd.equil.manager.EquilManager;
import com.microtechmd.equil.manager.command.BaseCmd;
import com.microtechmd.equil.manager.command.CmdBasalSet;
import com.microtechmd.equil.manager.command.CmdHistoryGet;
import com.microtechmd.equil.manager.command.CmdModelGet;
import com.microtechmd.equil.manager.command.CmdSettingGet;
import com.microtechmd.equil.service.EquilService;

import org.joda.time.Duration;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.database.entities.GlucoseValue;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventAppInitialized;
import info.nightscout.androidaps.extensions.PumpStateExtensionKt;
import info.nightscout.androidaps.interfaces.ActivePlugin;
import info.nightscout.androidaps.interfaces.BgSource;
import info.nightscout.androidaps.interfaces.CommandQueue;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.Profile;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.interfaces.Pump;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpPluginBase;
import info.nightscout.androidaps.interfaces.PumpSync;
import info.nightscout.androidaps.interfaces.ResourceHelper;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.commands.CustomCommand;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.TimeChangeType;
import info.nightscout.androidaps.utils.rx.AapsSchedulers;
import info.nightscout.shared.logging.AAPSLogger;
import info.nightscout.shared.logging.LTag;
import info.nightscout.shared.sharedPreferences.SP;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import kotlin.jvm.internal.Intrinsics;


/**
 *
 */
@Singleton
public class EquilPumpPlugin extends PumpPluginBase implements Pump, BgSource {
    public static final String VERSION = "2023_09_20_01";
    public static final Duration BASAL_STEP_DURATION = Duration.standardMinutes(30);
    private final ProfileFunction profileFunction;
    private final AAPSLogger aapsLogger;
    private final AapsSchedulers aapsSchedulers;
    private final RxBus rxBus;
    private final ActivePlugin activePlugin;
    private final Context context;
    private final FabricPrivacy fabricPrivacy;
    private final ResourceHelper rh;
    private final SP sp;
    private final DateUtil dateUtil;
    private final PumpDescription pumpDescription;
    private final PumpType pumpType = PumpType.EQUIL;
    public final PumpSync pumpSync;

    private final CompositeDisposable disposable = new CompositeDisposable();

    // variables for handling statuses and history
    private boolean firstRun = true;
    private boolean hasTimeDateOrTimeZoneChanged = false;
    private Instant lastTimeDateOrTimeZoneUpdate = Instant.ofEpochSecond(0L);
    private final boolean displayConnectionMessages = false;
    private boolean busy = false;
    private HandlerThread handlerThread;
    public Handler loopHandler;

    private final ServiceConnection serviceConnection;
    private EquilService equilService;
    BolusProfile bolusProfile;
    EquilManager equilManager;
    public EquilHistoryRecordDao equilHistoryRecordDao;
    Thread readStatus;

    @Inject
    public EquilPumpPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            AapsSchedulers aapsSchedulers,
            RxBus rxBus,
            Context context,
            ResourceHelper rh,
            ActivePlugin activePlugin,
            SP sp,
            CommandQueue commandQueue,
            FabricPrivacy fabricPrivacy,
            DateUtil dateUtil,
            ProfileFunction profileFunction,
            PumpSync pumpSync,
            EquilManager equilManager,
            EquilHistoryRecordDao equilHistoryRecordDao
    ) {
        super(new PluginDescription() //
                        .mainType(PluginType.PUMP) //
                        .fragmentClass(EquilFragment.class.getName()) //
                        .pluginIcon(R.drawable.ic_pod_128)
                        .pluginName(R.string.equil_name) //
                        .shortName(R.string.equil_name) //
                        .preferencesId(R.xml.pref_equil) //
                        .description(R.string.equil_pump_description), //
                injector, aapsLogger, rh, commandQueue);
        this.aapsLogger = aapsLogger;
        this.aapsSchedulers = aapsSchedulers;
        this.rxBus = rxBus;
        this.activePlugin = activePlugin;
        this.context = context;
        this.fabricPrivacy = fabricPrivacy;
        this.rh = rh;
        this.sp = sp;
        this.dateUtil = dateUtil;
        this.profileFunction = profileFunction;
        this.pumpSync = pumpSync;
        this.equilManager = equilManager;
        this.bolusProfile = new BolusProfile();
        this.equilHistoryRecordDao = equilHistoryRecordDao;
        this.serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                EquilService.LocalBinder mLocalBinder = (EquilService.LocalBinder) service;
                equilService = mLocalBinder.getServiceInstance();
//                equilService.setNotInPreInit();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                aapsLogger.debug(LTag.EQUILBLE, "EquilPumpPlugin is disconnected");
                equilService = null;
            }
        };
//        sp.putDouble("statuslights_cage_warning",
//                System.currentTimeMillis()-10000000);
        pumpDescription = new PumpDescription(pumpType);
        readStatus = new Thread() {
            @Override public void run() {
                super.run();
                aapsLogger.debug(LTag.EQUILBLE, "readStatus========" + VERSION);
                equilManager.readStatus();
                loopHandler.postDelayed(readStatus, 3 * 1000 * 60);
            }
        };

    }



    @Override
    protected void onStart() {
        super.onStart();
        if (handlerThread == null) {
            handlerThread = new HandlerThread(EquilPumpPlugin.class.getSimpleName());
            handlerThread.start();
            loopHandler = new Handler(handlerThread.getLooper());
            loopHandler.postDelayed(readStatus, 3 * 1000 * 60);
        }
        Intent intent = new Intent(context, EquilService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        loopHandler.postDelayed(new Runnable() {
            @Override public void run() {
                getCommandQueue().customCommand(new CmdModelGet(), new Callback() {
                    @Override public void run() {
                        if (result.getSuccess()) {
                            SystemClock.sleep(50);
                            CmdSettingGet cmdSettingGet = new CmdSettingGet();
                            getCommandQueue().customCommand(cmdSettingGet, new Callback() {
                                @Override public void run() {
                                    if (result.getSuccess()) {
//                                        settingProfile.setCloseTime(cmdSettingGet.getCloseTime());
//                                        settingProfile.setLowAlarm(cmdSettingGet.getLowAlarm());
//                                        settingProfile.setLargeFastAlarm(cmdSettingGet.getLargefastAlarm());
//                                        settingProfile.setStopAlarm(cmdSettingGet.getStopAlarm());
//                                        settingProfile.setInfusionUnit(cmdSettingGet.getInfusionUnit());
//                                        settingProfile.setBasalAlarm(cmdSettingGet.getBasalAlarm());
//                                        settingProfile.setLargeAlarm(cmdSettingGet.getLargeAlarm());
//                                        equilManager.saveSettingProfile(settingProfile);

                                    }

                                }
                            });
                        }

                    }
                });
            }
        }, 1000);

        disposable.add(rxBus
                .toObservable(EventAppExit.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> context.unbindService(serviceConnection), fabricPrivacy::logException)
        );

//        disposable.add(rxBus
//                .toObservable(EventPreferenceChange.class)
//                .observeOn(aapsSchedulers.getIo())
//                .subscribe(event -> {
//                    if (event.isChanged(getRh(), R.string.equil_key_device_address)) {
//                        connectNewPump();
//                        getCommandQueue().readStatus(rh.gs(R.string.device_changed),
//                                null);
//                    }
//                }, fabricPrivacy::logException)
//        );
        disposable.add(rxBus
                .toObservable(EventAppInitialized.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> {

                }, fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventEquilDataChanged.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> {
                    int battery = equilManager.getBattery();
                    int insulin = equilManager.getCurrentInsulin();
                    if (battery <= 10) {
                        boolean alarmBattery10 =
                                sp.getBoolean(EquilConst.Prefs.Equil_ALARM_BATTERY_10, false);
                        if (!alarmBattery10) {
                            equilManager.showNotification(Notification.FAILED_UPDATE_PROFILE,
                                    rh.gs(R.string.equil_lowbattery) + battery + "%",
                                    Notification.NORMAL, R.raw.alarm);
                            sp.putBoolean(EquilConst.Prefs.Equil_ALARM_BATTERY_10, true);
                        } else {
                            if (battery < 5) {
                                equilManager.showNotification(Notification.FAILED_UPDATE_PROFILE,
                                        rh.gs(R.string.equil_lowbattery) + battery + "%",
                                        Notification.URGENT, R.raw.alarm);
                            }
                        }

                    }
                    if (equilManager.getRunMode() == RunMode.RUN) {
                        if (insulin <= 10 && insulin > 5) {
                            boolean alarmInsulin10 =
                                    sp.getBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_10, false);
                            if (!alarmInsulin10) {
                                equilManager.showNotification(Notification.FAILED_UPDATE_PROFILE,
                                        rh.gs(R.string.equil_low_insulin) + insulin + "U",
                                        Notification.NORMAL, R.raw.alarm);
                                sp.putBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_10, true);
                            }
                        } else if (insulin <= 5 && insulin > 2) {
                            boolean alarmInsulin5 =
                                    sp.getBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_5, false);
                            if (!alarmInsulin5) {
                                equilManager.showNotification(Notification.FAILED_UPDATE_PROFILE,
                                        rh.gs(R.string.equil_low_insulin) + insulin + "U",
                                        Notification.NORMAL, R.raw.alarm);
                                sp.putBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_5, true);
                            }
                        } else if (insulin <= 2) {
                            equilManager.showNotification(Notification.FAILED_UPDATE_PROFILE,
                                    rh.gs(R.string.equil_low_insulin) + insulin + "U",
                                    Notification.URGENT, R.raw.alarm);
                        }
                    }
                }, fabricPrivacy::logException)
        );
    }


    @Override
    protected void onStop() {
        super.onStop();
        aapsLogger.error(LTag.EQUILBLE, "OmnipodPumpPlugin.onStop()");

        context.unbindService(serviceConnection);
        disposable.clear();
    }

    public EquilManager getEquilManager() {
        return equilManager;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public boolean isConnecting() {
        return false;
    }

    @Override
    public boolean isHandshakeInProgress() {
        if (displayConnectionMessages) {
            aapsLogger.debug(LTag.EQUILBLE, "isHandshakeInProgress [OmnipodPumpPlugin] - default " +
                    "(empty) implementation.");
        }
        return false;
    }

    // TODO is this correct?
    @Override
    public boolean isBusy() {
        return busy;
    }


    @Override
    public boolean isSuspended() {
        return equilManager.getRunMode() == RunMode.SUSPEND;
    }


    @Override
    public void getPumpStatus(@NonNull String reason) {
    }


    @NonNull
    @Override
    public PumpEnactResult setNewBasalProfile(@NonNull Profile profile) {
        RunMode mode = equilManager.getRunMode();
        if (mode == RunMode.RUN || mode == RunMode.SUSPEND) {
            PumpEnactResult pumpEnactResult = equilManager.executeCmd(new CmdBasalSet(profile));
            if (pumpEnactResult.getSuccess()) {
                addBasalProfileLog();
            }
            return pumpEnactResult;
        }
        return new PumpEnactResult(getInjector()).enacted(false).success(false).comment(rh.gs(R.string.equil_pump_not_run));
    }

    @Override
    public boolean isThisProfileSet(@NonNull Profile profile) {

//        PumpEnactResult pumpEnactResult = equilManager.getBasal(profile);
//        aapsLogger.error(LTag.EQUILBLE, "isThisProfileSet====" + pumpEnactResult.getSuccess());
//        return pumpEnactResult.getSuccess();
        RunMode mode = equilManager.getRunMode();
        if (mode == RunMode.RUN || mode == RunMode.SUSPEND) {
            return sp.getBoolean(EquilConst.Prefs.EQUIL_BASAL_SET, false);
        }
        return true;
    }

    @Override
    public long lastDataTime() {
        aapsLogger.debug(LTag.EQUILBLE,
                "lastDataTime==" +
                        "====" + VERSION + "===" +
                        android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss",
                                equilManager.getLastDataTime()));
        return equilManager.getLastDataTime();
    }

    @Override
    public double getBaseBasalRate() {
        return equilManager.getRate();
    }

    @Override
    public double getReservoirLevel() {
        return equilManager.getCurrentInsulin();
    }

    @Override
    public int getBatteryLevel() {
        return equilManager.getBattery();
    }

    @NonNull @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        if (detailedBolusInfo.insulin == 0 && detailedBolusInfo.carbs == 0) {
            // neither carbs nor bolus requested
            aapsLogger.debug("deliverTreatment: Invalid input: neither carbs nor insulin are set in treatment");
            return new PumpEnactResult(getInjector()).success(false).enacted(false).bolusDelivered(0d).carbsDelivered(0d)
                    .comment(R.string.invalidinput);
        }
        RunMode mode = equilManager.getRunMode();
        if (mode !=RunMode.RUN) {
            return new PumpEnactResult(getInjector()).enacted(false).success(false).comment(rh.gs(R.string.equil_pump_not_run));
        }
        int lastInsulin = equilManager.getCurrentInsulin();
        if (detailedBolusInfo.insulin > lastInsulin) {
            return new PumpEnactResult(getInjector())
                    .success(false)
                    .enacted(false)
                    .bolusDelivered(0d)
                    .comment(R.string.equil_not_enough_insulin);
        }
        bolusProfile.setInsulin(detailedBolusInfo.insulin);
        long time = System.currentTimeMillis();
        PumpEnactResult pumpEnactResult = equilManager.bolus(detailedBolusInfo, bolusProfile);
        if (pumpEnactResult.getSuccess()) {
            pumpSync.syncBolusWithPumpId(time,
                    pumpEnactResult.getBolusDelivered(),
                    detailedBolusInfo.getBolusType(),
                    detailedBolusInfo.timestamp,
                    PumpType.EQUIL,
                    serialNumber());
            EquilBolusRecord equilBolusRecord =
                    new EquilBolusRecord(pumpEnactResult.getBolusDelivered(),
                            BolusType.SMB, time);
            equilManager.setBolusRecord(equilBolusRecord);
            EquilHistoryRecord equilHistoryRecord = new EquilHistoryRecord(time,
                    null,
                    equilBolusRecord,
                    EquilHistoryRecord.EventType.SET_BOLUS,
                    time,
                    serialNumber()
            );
            long t = equilHistoryRecordDao.insert(equilHistoryRecord);
        }


        return pumpEnactResult;
    }

    public PumpEnactResult loadHistory() {
        PumpEnactResult pumpEnactResult = new PumpEnactResult(getInjector());
        EquilHistoryPump equilHistoryPump2 = equilHistoryRecordDao.last();
        int startIndex = 1;
        if (equilHistoryPump2 != null) {
            startIndex = equilHistoryPump2.getEventIndex();
        }
        int index = equilManager.loadHistory(0);
        if (index == -1) {
            return pumpEnactResult.success(false);
        }
        while (startIndex != index) {
            startIndex++;
            if (startIndex > 2000) {
                startIndex = 1;
            }
            equilManager.loadHistory(startIndex);
        }
        return pumpEnactResult.success(true);
    }

    @Override
    public void stopBolusDelivering() {
        PumpEnactResult result = equilManager.stopBolus(bolusProfile);
        if (result.getSuccess()) {
            long time = System.currentTimeMillis();
            EquilHistoryRecord equilHistoryRecord = new EquilHistoryRecord(time,
                    null,
                    null,
                    EquilHistoryRecord.EventType.CANCEL_BOLUS,
                    time,
                    serialNumber()
            );
            equilHistoryRecordDao.insert(equilHistoryRecord);
        }
    }

    @Override
    @NonNull
    public PumpEnactResult setTempBasalAbsolute(double absoluteRate, int durationInMinutes, @NonNull Profile profile,
                                                boolean enforceNew, @NonNull PumpSync.TemporaryBasalType tbrType) {

        if (durationInMinutes <= 0 || durationInMinutes % BASAL_STEP_DURATION.getStandardMinutes() != 0) {
            return new PumpEnactResult(getInjector()).success(false).comment(rh.gs(R.string.equil_error_set_temp_basal_failed_validation, BASAL_STEP_DURATION.getStandardMinutes()));
        }
        RunMode mode = equilManager.getRunMode();
        if (mode !=RunMode.RUN) {
            return new PumpEnactResult(getInjector()).enacted(false).success(false).comment(rh.gs(R.string.equil_pump_not_run));
        }
        PumpEnactResult pumpEnactResult = equilManager.setTempBasal(absoluteRate, durationInMinutes);
        if (pumpEnactResult.getSuccess()) {
            long time = System.currentTimeMillis();
            pumpSync.syncTemporaryBasalWithPumpId(
                    time,
                    absoluteRate,
                    durationInMinutes * 60 * 1000,
                    true,
                    PumpSync.TemporaryBasalType.NORMAL,
                    time,
                    pumpType,
                    serialNumber()
            );
            EquilTempBasalRecord tempBasalRecord =
                    new EquilTempBasalRecord(durationInMinutes * 60 * 1000,
                            absoluteRate, time);
            equilManager.setTempBasal(tempBasalRecord);
            EquilHistoryRecord equilHistoryRecord = new EquilHistoryRecord(time,
                    tempBasalRecord,
                    null,
                    EquilHistoryRecord.EventType.SET_TEMPORARY_BASAL,
                    time,
                    serialNumber()
            );
            equilHistoryRecordDao.insert(equilHistoryRecord);
            pumpEnactResult.setTempCancel(false);
            pumpEnactResult.setDuration(durationInMinutes);
            pumpEnactResult.setPercent(false);
            pumpEnactResult.setAbsolute(absoluteRate);
        }

        return pumpEnactResult;
    }

    @Override
    @NonNull
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        PumpEnactResult pumpEnactResult = equilManager.setTempBasal(0, 0);
        if (pumpEnactResult.getSuccess()) {
            long time = System.currentTimeMillis();
            EquilHistoryRecord equilHistoryRecord = new EquilHistoryRecord(time,
                    null,
                    null,
                    EquilHistoryRecord.EventType.CANCEL_TEMPORARY_BASAL,
                    time,
                    serialNumber()
            );
            equilHistoryRecordDao.insert(equilHistoryRecord);
            pumpEnactResult.setTempCancel(true);
            pumpSync.syncStopTemporaryBasalWithPumpId(
                    time,
                    time,
                    pumpType,
                    serialNumber()
            );
        }

        return pumpEnactResult;
    }

    // TODO improve (i8n and more)
    @NonNull @Override
    public JSONObject getJSONStatus(@NonNull Profile profile, @NonNull String profileName, @NonNull String version) {

        Intrinsics.checkNotNullParameter(profile, Scopes.PROFILE);
        Intrinsics.checkNotNullParameter(profileName, "profileName");
        Intrinsics.checkNotNullParameter(version, "version");
        long currentTimeMillis = System.currentTimeMillis();
        try {
            if (!isConnected()) {
                JSONObject jSONObject = new JSONObject();
                JSONObject jSONObject2 = new JSONObject();
                jSONObject2.put("status", "泵没有连接");
                jSONObject.put("status", jSONObject2);
                return jSONObject;
            }
            JSONObject jSONObject3 = new JSONObject();
            JSONObject jSONObject4 = new JSONObject();
            JSONObject jSONObject5 = new JSONObject();
            JSONObject jSONObject6 = new JSONObject();
            try {
//                jSONObject4.put("percent", this.pump.getMonitorInfo().getBatteryForReadable());
//                jSONObject5.put("status", this.pump.getMode().getHolder().name());
//                jSONObject5.put(ServerValues.NAME_OP_TIMESTAMP, this.dateUtil.toISOString(getLastDataTime()));
                jSONObject6.put("Version", version);
                PumpSync.PumpState.Bolus bolus = this.pumpSync.expectedPumpState().getBolus();
                if (bolus != null) {
                    jSONObject6.put("LastBolus", this.dateUtil.dateAndTimeString(bolus.getTimestamp()));
                    jSONObject6.put("LastBolusAmount", bolus.getAmount());
                }
                PumpSync.PumpState expectedPumpState = this.pumpSync.expectedPumpState();
                PumpSync.PumpState.TemporaryBasal component1 = expectedPumpState.component1();
                PumpSync.PumpState.ExtendedBolus component2 = expectedPumpState.component2();
                if (component1 != null) {
                    jSONObject6.put("TempBasalAbsoluteRate", PumpStateExtensionKt.convertedToAbsolute(component1, currentTimeMillis, profile));
                    jSONObject6.put("TempBasalStart", this.dateUtil.dateAndTimeString(component1.getTimestamp()));
                    jSONObject6.put("TempBasalRemaining", PumpStateExtensionKt.getPlannedRemainingMinutes(component1));
                }
                if (component2 != null) {
                    jSONObject6.put("ExtendedBolusAbsoluteRate", component2.getRate());
                    jSONObject6.put("ExtendedBolusStart", this.dateUtil.dateAndTimeString(component2.getTimestamp()));
                    jSONObject6.put("ExtendedBolusRemaining", PumpStateExtensionKt.getPlannedRemainingMinutes(component2));
                }
                jSONObject6.put("BaseBasalRate", getBaseBasalRate());
                try {
                    jSONObject6.put("ActiveProfile", profileName);
                } catch (Exception unused) {
                }
                jSONObject3.put("battery", jSONObject4);
                jSONObject3.put("status", jSONObject5);
                jSONObject3.put("extended", jSONObject6);
                jSONObject3.put("reservoir", getReservoirLevel());
                DateUtil dateUtil = this.dateUtil;
                jSONObject3.put("clock", dateUtil.toISOString(dateUtil.now()));
                return jSONObject3;
            } catch (JSONException e) {
                JSONObject jSONObject7 = new JSONObject();
                jSONObject7.put("status", "获取泵状态异常->" + e.getMessage());
                jSONObject3.put("status", jSONObject7);
                getAapsLogger().error("Unhandled exception", e);
                return jSONObject3;

            }
        } catch (Exception e) {

        }

        return null;
    }

    @Override @NonNull public ManufacturerType manufacturer() {
        return pumpType.getManufacturer();
    }

    @Override @NonNull
    public PumpType model() {
        return pumpType;
    }

    @NonNull
    @Override
    public String serialNumber() {
        return equilManager.getSerialNumber();
    }

    @Override @NonNull public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @NonNull @Override
    public String shortStatus(boolean veryShort) {
        return null;
    }

    @Override
    public void executeCustomAction(@NonNull CustomActionType customActionType) {
        aapsLogger.error(LTag.PUMP, "Unknown custom action: " + customActionType);
    }

    @Override
    public PumpEnactResult executeCustomCommand(@NonNull CustomCommand command) {
        if (command instanceof CmdHistoryGet) {
            return loadHistory();
        }
        if (command instanceof BaseCmd) {
            return equilManager.executeCmd((BaseCmd) command);
        }
        return equilManager.executeCmd((BaseCmd) command);
    }


    @Override
    public void timezoneOrDSTChanged(TimeChangeType timeChangeType) {

        Instant now = Instant.now();

        aapsLogger.info(LTag.PUMP, "DST and/or TimeZone changed event will be consumed by driver");
        lastTimeDateOrTimeZoneUpdate = now;
        hasTimeDateOrTimeZoneChanged = true;
    }

    @Override
    public boolean isUnreachableAlertTimeoutExceeded(long unreachableTimeoutMilliseconds) {

        return false;
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @Override public boolean canHandleDST() {
        return false;
    }

    @Override
    public void finishHandshaking() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "finishHandshaking [OmnipodPumpPlugin] - default (empty) implementation.");
    }

    @Override public void connect(@NonNull String reason) {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "connect (reason={}) [PumpPluginAbstract] - default (empty) implementation." + reason);
    }

    @Override public int waitForDisconnectionInSeconds() {
        return 0;
    }

    @Override public void disconnect(@NonNull String reason) {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "disconnect (reason={}) [PumpPluginAbstract] - default (empty) implementation." + reason);
    }

    @Override public void stopConnecting() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "stopConnecting [PumpPluginAbstract] - default (empty) implementation.");
    }

    @NonNull @Override public PumpEnactResult setTempBasalPercent(int percent, int durationInMinutes, @NonNull Profile profile, boolean enforceNew, @NonNull PumpSync.TemporaryBasalType tbrType) {

        if (percent == 0) {
            return setTempBasalAbsolute(0.0d, durationInMinutes, profile, enforceNew, tbrType);
        } else {
            double absoluteValue = profile.getBasal() * (percent / 100.0d);
            absoluteValue = pumpDescription.getPumpType().determineCorrectBasalSize(absoluteValue);
            return setTempBasalAbsolute(absoluteValue, durationInMinutes, profile, enforceNew, tbrType);
        }
    }

    @NonNull @Override public PumpEnactResult setExtendedBolus(double insulin, int durationInMinutes) {
        PumpEnactResult pumpEnactResult = equilManager.setExtendedBolus(insulin, durationInMinutes);
        if (pumpEnactResult.getSuccess()) {
            long time = System.currentTimeMillis();
            pumpSync.syncExtendedBolusWithPumpId(
                    time,
                    insulin,
                    durationInMinutes * 60 * 1000,
                    true,
                    time,
                    pumpType,
                    serialNumber()
            );
            pumpEnactResult.setTempCancel(false);
            pumpEnactResult.setDuration(durationInMinutes);
            pumpEnactResult.setPercent(false);
            pumpEnactResult.setAbsolute(insulin);
        }
        return pumpEnactResult;
    }

    @NonNull @Override public PumpEnactResult cancelExtendedBolus() {
//        return getOperationNotSupportedWithCustomText(info.nightscout.androidaps.plugins.pump.common.R.string.pump_operation_not_supported_by_pump_driver);

        PumpEnactResult pumpEnactResult = equilManager.setExtendedBolus(0, 0);
        if (pumpEnactResult.getSuccess()) {
            long time = System.currentTimeMillis();
            pumpSync.syncStopExtendedBolusWithPumpId(
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    pumpType,
                    serialNumber()
            );
        }
        return pumpEnactResult;
    }

    @NonNull @Override public PumpEnactResult loadTDDs() {
        aapsLogger.debug(LTag.EQUILBLE, "loadTDDs [OmnipodPumpPlugin] - Not implemented.");
        return getOperationNotSupportedWithCustomText(info.nightscout.androidaps.plugins.pump.common.R.string.pump_operation_not_supported_by_pump_driver);
    }


    @Override public boolean isBatteryChangeLoggingEnabled() {
        return false;
    }


    @NonNull private PumpEnactResult deliverBolus(final DetailedBolusInfo detailedBolusInfo) {
        return new PumpEnactResult(getInjector()).success(false).enacted(false);
    }


    @Nullable private PumpSync.PumpState.TemporaryBasal readTBR() {
        return pumpSync.expectedPumpState().getTemporaryBasal();
    }

    public void connectNewPump() {
        pumpSync.connectNewPump();
    }

    private PumpEnactResult getOperationNotSupportedWithCustomText(int resourceId) {
        return new PumpEnactResult(getInjector()).success(false).enacted(false).comment(resourceId);
    }

    public void showToast(String s) {
        loopHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, s, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void resetData() {
        sp.putBoolean(EquilConst.Prefs.Equil_ALARM_BATTERY_10, false);
        sp.putBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_10, false);
        sp.putBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_5, false);
        sp.putBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_5, false);
        sp.putBoolean(EquilConst.Prefs.EQUIL_BASAL_SET, false);
    }

    public void clearData() {
        resetData();
        equilManager.clearPodState();
        sp.putString(EquilConst.Prefs.EQUIL_DEVICES, "");
        sp.putString(EquilConst.Prefs.EQUIL_PASSWORD, "");
    }

    @Override public boolean advancedFilteringSupported() {
        return true;
    }

    @Override public int getSensorBatteryLevel() {
        return 10;
    }

    @Override public boolean shouldUploadToNs(@NonNull GlucoseValue glucoseValue) {
        return false;
    }

    public void addBasalProfileLog() {
        long time = System.currentTimeMillis();
        EquilHistoryRecord equilHistoryRecord = new EquilHistoryRecord(time,
                null,
                null,
                EquilHistoryRecord.EventType.SET_BASAL_PROFILE,
                time,
                serialNumber()
        );
        equilHistoryRecordDao.insert(equilHistoryRecord);
    }
}

