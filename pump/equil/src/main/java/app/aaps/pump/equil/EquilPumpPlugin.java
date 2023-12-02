package app.aaps.pump.equil;


import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.common.Scopes;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;

import app.aaps.core.data.plugin.PluginDescription;
import app.aaps.core.data.plugin.PluginType;
import app.aaps.core.data.pump.defs.ManufacturerType;
import app.aaps.core.data.pump.defs.PumpDescription;
import app.aaps.core.data.pump.defs.PumpType;
import app.aaps.core.data.pump.defs.TimeChangeType;
import app.aaps.core.interfaces.logging.AAPSLogger;
import app.aaps.core.interfaces.logging.LTag;
import app.aaps.core.interfaces.notifications.Notification;
import app.aaps.core.interfaces.objects.Instantiator;
import app.aaps.core.interfaces.profile.Profile;
import app.aaps.core.interfaces.profile.ProfileFunction;
import app.aaps.core.interfaces.pump.DetailedBolusInfo;
import app.aaps.core.interfaces.pump.Pump;
import app.aaps.core.interfaces.pump.PumpEnactResult;
import app.aaps.core.interfaces.pump.PumpPluginBase;
import app.aaps.core.interfaces.pump.PumpSync;
import app.aaps.core.interfaces.pump.actions.CustomActionType;
import app.aaps.core.interfaces.pump.defs.PumpDescriptionExtensionKt;
import app.aaps.core.interfaces.pump.defs.PumpTypeExtensionKt;
import app.aaps.core.interfaces.queue.CommandQueue;
import app.aaps.core.interfaces.queue.CustomCommand;
import app.aaps.core.interfaces.resources.ResourceHelper;
import app.aaps.core.interfaces.rx.AapsSchedulers;
import app.aaps.core.interfaces.rx.bus.RxBus;
import app.aaps.core.interfaces.rx.events.EventAppInitialized;
import app.aaps.core.interfaces.sharedPreferences.SP;
import app.aaps.core.interfaces.utils.DateUtil;
import app.aaps.core.interfaces.utils.DecimalFormatter;
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy;
import app.aaps.pump.equil.data.BolusProfile;
import app.aaps.pump.equil.data.RunMode;
import app.aaps.pump.equil.data.database.EquilHistoryPump;
import app.aaps.pump.equil.data.database.EquilHistoryRecordDao;
import app.aaps.pump.equil.driver.definition.ActivationProgress;
import app.aaps.pump.equil.driver.definition.BasalSchedule;
import app.aaps.pump.equil.events.EventEquilDataChanged;
import app.aaps.pump.equil.manager.EquilManager;
import app.aaps.pump.equil.manager.command.BaseCmd;
import app.aaps.pump.equil.manager.command.CmdBasalSet;
import app.aaps.pump.equil.manager.command.CmdHistoryGet;
import app.aaps.pump.equil.manager.command.CmdStatusGet;
import app.aaps.pump.equil.manager.command.CmdTimeSet;
import app.aaps.pump.equil.manager.command.PumpEvent;
import dagger.android.HasAndroidInjector;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import kotlin.jvm.internal.Intrinsics;


/**
 *
 */
@Singleton
public class EquilPumpPlugin extends PumpPluginBase implements Pump {
    public static final String VERSION = "2023_09_20_01";
    private static final long RILEY_LINK_CONNECT_TIMEOUT_MILLIS = 3 * 60 * 1_000L; // 3 minutes
    private static final long STATUS_CHECK_INTERVAL_MILLIS = 60 * 3_000L; // 1 minute
    public static final Duration BASAL_STEP_DURATION = Duration.standardMinutes(30);
    private final ProfileFunction profileFunction;
    private final AAPSLogger aapsLogger;
    private final AapsSchedulers aapsSchedulers;
    private final RxBus rxBus;
    private final Context context;
    private final FabricPrivacy fabricPrivacy;
    private final ResourceHelper rh;
    private final SP sp;
    private final DateUtil dateUtil;
    private final PumpDescription pumpDescription;
    private final PumpType pumpType = PumpType.EQUIL;
    private final DecimalFormatter decimalFormatter;

    public final PumpSync pumpSync;
    public final Instantiator instantiator;

    private final CompositeDisposable disposable = new CompositeDisposable();

    // variables for handling statuses and history
    private boolean hasTimeDateOrTimeZoneChanged = false;
    private Instant lastTimeDateOrTimeZoneUpdate = Instant.ofEpochSecond(0L);
    private final boolean displayConnectionMessages = false;
    private final boolean busy = false;
    private HandlerThread handlerThread;
    public Handler loopHandler;

    BolusProfile bolusProfile;
    EquilManager equilManager;
    public EquilHistoryRecordDao equilHistoryRecordDao;
    private final Runnable statusChecker;

    @Inject
    public EquilPumpPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            AapsSchedulers aapsSchedulers,
            RxBus rxBus,
            Context context,
            ResourceHelper rh,
            SP sp,
            CommandQueue commandQueue,
            FabricPrivacy fabricPrivacy,
            DateUtil dateUtil,
            ProfileFunction profileFunction,
            PumpSync pumpSync,
            EquilManager equilManager,
            EquilHistoryRecordDao equilHistoryRecordDao,
            DecimalFormatter decimalFormatter,
            Instantiator instantiator
    ) {
        super(new PluginDescription() //
                        .mainType(PluginType.PUMP) //
                        .fragmentClass(EquilFragment.class.getName()) //
                        .pluginIcon(R.drawable.ic_pod_128)
                        .pluginName(R.string.equil_name) //
                        .shortName(R.string.equil_name) //
                        .preferencesId(R.xml.pref_equil) //
                        .description(R.string.equil_pump_description), //
                aapsLogger, rh, commandQueue);
        this.aapsLogger = aapsLogger;
        this.aapsSchedulers = aapsSchedulers;
        this.rxBus = rxBus;
        this.context = context;
        this.fabricPrivacy = fabricPrivacy;
        this.rh = rh;
        this.sp = sp;
        this.dateUtil = dateUtil;
        this.profileFunction = profileFunction;
        this.pumpSync = pumpSync;
        this.equilManager = equilManager;
        this.bolusProfile = new BolusProfile();
        this.decimalFormatter = decimalFormatter;
        this.equilHistoryRecordDao = equilHistoryRecordDao;
        this.instantiator = instantiator;
        pumpDescription = PumpDescriptionExtensionKt.fillFor(new PumpDescription(), pumpType);
        statusChecker = new Runnable() {
            @Override public void run() {
                if (commandQueue.size() == 0 &&
                        commandQueue.performing() == null) {
                    if (equilManager.isActivationCompleted()) {
//                        getCommandQueue().customCommand(new CmdHistoryGet(), null);
                        getCommandQueue().customCommand(new CmdStatusGet(), null);
                    }

                } else {
                    aapsLogger.debug(LTag.PUMPCOMM, "Skipping Pod status check because command queue is" +
                            " not empty");
                }
                loopHandler.postDelayed(this, STATUS_CHECK_INTERVAL_MILLIS);
            }
        };
        PumpEvent.init(rh);

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (handlerThread == null) {
            handlerThread = new HandlerThread(EquilPumpPlugin.class.getSimpleName());
            handlerThread.start();
            loopHandler = new Handler(handlerThread.getLooper());
        }

        loopHandler.postDelayed(statusChecker, STATUS_CHECK_INTERVAL_MILLIS);

        disposable.add(rxBus
                .toObservable(EventAppInitialized.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> {

                }, fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventEquilDataChanged.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> playAlarm(), fabricPrivacy::logException)
        );
    }

    public ActivationProgress tempActivationProgress = ActivationProgress.NONE;

    @Override
    protected void onStop() {
        super.onStop();
        aapsLogger.debug(LTag.PUMPCOMM, "EquilPumpPlugin.onStop()");

        disposable.clear();
    }

    public EquilManager getEquilManager() {
        return equilManager;
    }

    @Override
    public boolean isInitialized() {
        //boolean flag = equilManager.isActivationInitialized();
//        return flag;
        return true;
    }

    @Override
    public boolean isConnected() {
        return isInitialized();
    }

    @Override
    public boolean isConnecting() {
        return false;

    }

    // TODO is this correct?
    @Override
    public boolean isBusy() {
        aapsLogger.debug(LTag.PUMPCOMM, "isBusy  flag: {}");
        return false;

    }

    @Override
    public boolean isHandshakeInProgress() {
//        return  equilManager.getBluetoothConnectionState() == BluetoothConnectionState.CONNECTED;
        return false;
    }


    @Override
    public boolean isSuspended() {
        RunMode runMode = equilManager.getRunMode();
        if (equilManager.isActivationCompleted()) {
            return runMode == RunMode.SUSPEND || runMode == RunMode.STOP;
        }
        return true;
    }


    @Override
    public void getPumpStatus(@NonNull String reason) {
    }


    @NonNull
    @Override
    public PumpEnactResult setNewBasalProfile(@NonNull Profile profile) {
        aapsLogger.debug(LTag.PUMPCOMM, "setNewBasalProfile");
        RunMode mode = equilManager.getRunMode();
        if (mode == RunMode.RUN || mode == RunMode.SUSPEND) {
            BasalSchedule basalSchedule = BasalSchedule.mapProfileToBasalSchedule(profile);
            if (basalSchedule.getEntries() == null || basalSchedule.getEntries().size() < 24) {
                return instantiator.providePumpEnactResult().enacted(false).success(false).comment("No profile active");
            }
            PumpEnactResult pumpEnactResult =
                    equilManager.executeCmd(new CmdBasalSet(basalSchedule, profile));
            if (pumpEnactResult.getSuccess()) {
                equilManager.setBasalSchedule(basalSchedule);
            }
            return pumpEnactResult;
        }
        return instantiator.providePumpEnactResult().enacted(false).success(false).comment(rh.gs(R.string.equil_pump_not_run));
    }

    @Override
    public boolean isThisProfileSet(@NonNull Profile profile) {
        if (!equilManager.isActivationCompleted()) {
            // When no Pod is active, return true here in order to prevent AAPS from setting a profile
            // When we activate a new Pod, we just use ProfileFunction to set the currently active profile
            return true;
        }
        return Objects.equals(equilManager.getBasalSchedule(),
                BasalSchedule.mapProfileToBasalSchedule(profile));
    }

    @Override
    public long lastDataTime() {
        aapsLogger.debug(LTag.PUMPCOMM,
                "lastDataTime==" +
                        "====" + VERSION + "===" +
                        android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss",
                                equilManager.getLastDataTime()));
        return equilManager.getLastDataTime();
    }

    public static Duration toDuration(DateTime dateTime) {
        if (dateTime == null) {
            throw new IllegalArgumentException("dateTime can not be null");
        }
        return new Duration(dateTime.toLocalTime().getMillisOfDay());
    }

    @Override
    public double getBaseBasalRate() {
        if (isSuspended()) {
            return 0.0d;
        }
        aapsLogger.debug(LTag.PUMPCOMM, "getBaseBasalRate======");
        BasalSchedule schedule = equilManager.getBasalSchedule();
        if (schedule != null) return schedule.rateAt(toDuration(DateTime.now()));
        else return 0;
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
            aapsLogger.error("deliverTreatment: Invalid input: neither carbs nor insulin are set in treatment");
            return instantiator.providePumpEnactResult().success(false).enacted(false)
                    .bolusDelivered(0d).comment("Invalid input");
        }
        RunMode mode = equilManager.getRunMode();
        if (mode != RunMode.RUN) {
            return instantiator.providePumpEnactResult().enacted(false).success(false)
                    .bolusDelivered(0d).comment(rh.gs(R.string.equil_pump_not_run));
        }
        int lastInsulin = equilManager.getCurrentInsulin();
        if (detailedBolusInfo.insulin > lastInsulin) {
            return instantiator.providePumpEnactResult()
                    .success(false)
                    .enacted(false)
                    .bolusDelivered(0d)
                    .comment(R.string.equil_not_enough_insulin);
        }
        return deliverBolus(detailedBolusInfo);
    }

    public PumpEnactResult loadHistory() {
        PumpEnactResult pumpEnactResult = instantiator.providePumpEnactResult();
        EquilHistoryPump equilHistoryLast = equilHistoryRecordDao.last(serialNumber());
        int startIndex;
        startIndex = equilHistoryLast.getEventIndex();
        int index = equilManager.getHistoryIndex();
        aapsLogger.debug(LTag.PUMPCOMM, "return ===" + index + "====" + startIndex);
        if (index == -1) {
            return pumpEnactResult.success(false);
        }
        while (startIndex != index) {
            startIndex++;
            if (startIndex > 2000) {
                startIndex = 1;
            }
            aapsLogger.debug(LTag.PUMPCOMM, "while index===" + startIndex + "===" + index);
            equilManager.loadHistory(startIndex);

        }
        return pumpEnactResult.success(true);
    }

    @Override
    public void stopBolusDelivering() {
        equilManager.stopBolus(bolusProfile);
        aapsLogger.debug(LTag.PUMPCOMM, "stopBolusDelivering=====");
    }

    @Override
    @NonNull
    public PumpEnactResult setTempBasalAbsolute(double absoluteRate, int durationInMinutes, @NonNull Profile profile,
                                                boolean enforceNew, @NonNull PumpSync.TemporaryBasalType tbrType) {

        aapsLogger.debug(LTag.PUMPCOMM,
                "setTempBasalAbsolute=====" + absoluteRate
                        + "====" + durationInMinutes + "===" + enforceNew);
        if (durationInMinutes <= 0 || durationInMinutes % BASAL_STEP_DURATION.getStandardMinutes() != 0) {
            return instantiator.providePumpEnactResult().success(false).comment(rh.gs(R.string.equil_error_set_temp_basal_failed_validation, BASAL_STEP_DURATION.getStandardMinutes()));
        }
        RunMode mode = equilManager.getRunMode();
        if (mode != RunMode.RUN) {
            return instantiator.providePumpEnactResult().enacted(false).success(false).comment(rh.gs(R.string.equil_pump_not_run));
        }
        PumpEnactResult pumpEnactResult = instantiator.providePumpEnactResult();
        pumpEnactResult.success(false);
        pumpEnactResult = equilManager.getTempBasalPump();
        if (pumpEnactResult.getSuccess()) {
            if (pumpEnactResult.getEnacted()) {
                pumpEnactResult = cancelTempBasal(true);
            }
            if (pumpEnactResult.getSuccess()) {
                pumpEnactResult = equilManager.setTempBasal(absoluteRate,
                        durationInMinutes, false);
                if (pumpEnactResult.getSuccess()) {
                    pumpEnactResult.setTempCancel(false);
                    pumpEnactResult.setDuration(durationInMinutes);
                    pumpEnactResult.setPercent(false);
                    pumpEnactResult.setAbsolute(absoluteRate);
                }
            }
        }
        return pumpEnactResult;
    }

    @Override
    @NonNull
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        aapsLogger.debug(LTag.PUMPCOMM, "cancelTempBasal=====" + enforceNew);
        PumpEnactResult pumpEnactResult = equilManager.setTempBasal(0, 0, true);
        if (pumpEnactResult.getSuccess()) {
            pumpEnactResult.setTempCancel(true);
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
                jSONObject2.put("status", "no active Pod");
                jSONObject.put("status", jSONObject2);
                return jSONObject;
            }
            JSONObject jSONObject3 = new JSONObject();
            JSONObject jSONObject4 = new JSONObject();
            JSONObject jSONObject5 = new JSONObject();
            JSONObject jSONObject6 = new JSONObject();
            try {
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
                    jSONObject6.put("TempBasalAbsoluteRate", component1.convertedToAbsolute(currentTimeMillis, profile));
                    jSONObject6.put("TempBasalStart", this.dateUtil.dateAndTimeString(component1.getTimestamp()));
                    jSONObject6.put("TempBasalRemaining", component1.getPlannedRemainingMinutes());
                }
                if (component2 != null) {
                    jSONObject6.put("ExtendedBolusAbsoluteRate", component2.getRate());
                    jSONObject6.put("ExtendedBolusStart", this.dateUtil.dateAndTimeString(component2.getTimestamp()));
                    jSONObject6.put("ExtendedBolusRemaining", component2.getPlannedRemainingMinutes());
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
                jSONObject7.put("status", "error" + e.getMessage());
                jSONObject3.put("status", jSONObject7);
                getAapsLogger().error("Unhandled exception", e);
                return jSONObject3;

            }
        } catch (Exception e) {

        }

        return new JSONObject();
    }

    @Override @NonNull public ManufacturerType manufacturer() {
        return Objects.requireNonNull(pumpType.getManufacturer());
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
        if (!equilManager.isActivationCompleted()) {
            return rh.gs(R.string.equil_init_insulin_error);
        }
        String ret = "";
        if (lastDataTime() != 0) {
            long agoMsec = System.currentTimeMillis() - lastDataTime();
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += rh.gs(R.string.equil_common_short_status_last_connection, agoMin) + "\n";
        }
        if (equilManager.getBolusRecord() != null) {
            ret += rh.gs(R.string.equil_common_short_status_last_bolus,
                    decimalFormatter.to2Decimal(equilManager.getBolusRecord().getAmout()),
                    android.text.format.DateFormat.format("HH:mm",
                            equilManager.getBolusRecord().getStartTime())) +
                    "\n";
        }
        PumpSync.PumpState pumpState = pumpSync.expectedPumpState();
        if (pumpState.getTemporaryBasal() != null && pumpState.getProfile() != null) {
            ret += rh.gs(R.string.equil_common_short_status_temp_basal, pumpState.getTemporaryBasal().toStringFull(dateUtil, decimalFormatter) + "\n");
        }
        if (pumpState.getExtendedBolus() != null) {
            ret += rh.gs(R.string.equil_common_short_status_extended_bolus, pumpState.getExtendedBolus().toStringFull(dateUtil, decimalFormatter) + "\n");
        }
        ret += rh.gs(R.string.equil_common_short_status_reservoir, (getReservoirLevel()));
        return ret.trim();
    }

    @Override
    public void executeCustomAction(@NonNull CustomActionType customActionType) {
        aapsLogger.debug(LTag.PUMPCOMM, "Unknown custom action: " + customActionType);
    }

    @Override
    public PumpEnactResult executeCustomCommand(@NonNull CustomCommand command) {
        aapsLogger.debug(LTag.PUMPCOMM, "executeCustomCommand " + command);
        PumpEnactResult pumpEnactResult = null;
        if (command instanceof CmdHistoryGet) {
            pumpEnactResult = loadHistory();
        }
        if (command instanceof BaseCmd) {
            pumpEnactResult = equilManager.executeCmd((BaseCmd) command);
        }
        if (command instanceof CmdStatusGet) {
            pumpEnactResult = equilManager.readEquilStatus();
        }

        return pumpEnactResult;
    }


    @Override
    public void timezoneOrDSTChanged(@NonNull TimeChangeType timeChangeType) {

        Instant now = Instant.now();
        aapsLogger.debug(LTag.PUMP, "DST and/or TimeZone changed event will be consumed by driver");
        lastTimeDateOrTimeZoneUpdate = now;
        hasTimeDateOrTimeZoneChanged = true;
        getCommandQueue().customCommand(new CmdTimeSet(), null);
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
//        if (displayConnectionMessages)
        aapsLogger.debug(LTag.PUMPCOMM, "finishHandshaking [OmnipodPumpPlugin] - default (empty) " +
                "implementation.");
    }

    @Override public void connect(@NonNull String reason) {

    }


    @Override public void disconnect(@NonNull String reason) {
        aapsLogger.debug(LTag.PUMP, "disconnect (reason={}) [PumpPluginAbstract] - default (empty) implementation." + reason);
    }

    @Override public void stopConnecting() {
        aapsLogger.debug(LTag.PUMP, "stopConnecting [PumpPluginAbstract] - default (empty) implementation.");
    }

    @NonNull @Override public PumpEnactResult setTempBasalPercent(int percent, int durationInMinutes, @NonNull Profile profile, boolean enforceNew, @NonNull PumpSync.TemporaryBasalType tbrType) {
        aapsLogger.debug(LTag.PUMPCOMM, "setTempBasalPercent [OmnipodPumpPlugin] ");
        if (percent == 0) {
            return setTempBasalAbsolute(0.0d, durationInMinutes, profile, enforceNew, tbrType);
        } else {
            double absoluteValue = profile.getBasal() * (percent / 100.0d);
            absoluteValue = PumpTypeExtensionKt.determineCorrectBasalSize(pumpDescription.getPumpType(), absoluteValue);
            return setTempBasalAbsolute(absoluteValue, durationInMinutes, profile, enforceNew, tbrType);
        }
    }

    @NonNull @Override public PumpEnactResult setExtendedBolus(double insulin, int durationInMinutes) {
        aapsLogger.debug(LTag.PUMPCOMM,
                "setExtendedBolus [OmnipodPumpPlugin] - Not implemented." + insulin + "====" + durationInMinutes);
        PumpEnactResult pumpEnactResult = equilManager.setExtendedBolus(insulin,
                durationInMinutes, false);
        if (pumpEnactResult.getSuccess()) {

            pumpEnactResult.setTempCancel(false);
            pumpEnactResult.setDuration(durationInMinutes);
            pumpEnactResult.setPercent(false);
            pumpEnactResult.setAbsolute(insulin);
        }
        return pumpEnactResult;
    }

    @NonNull @Override public PumpEnactResult cancelExtendedBolus() {
        aapsLogger.debug(LTag.PUMPCOMM, "cancelExtendedBolus [OmnipodPumpPlugin] - Not implemented.");
//        return getOperationNotSupportedWithCustomText(info.nightscout.androidaps.plugins.pump.common.R.string.pump_operation_not_supported_by_pump_driver);
        return equilManager.setExtendedBolus(0, 0, true);
    }

    @NonNull @Override public PumpEnactResult loadTDDs() {
        aapsLogger.debug(LTag.PUMPCOMM, "loadTDDs [OmnipodPumpPlugin] - Not implemented.");
        return instantiator.providePumpEnactResult().success(false).enacted(false);
    }


    @Override public boolean isBatteryChangeLoggingEnabled() {
        return false;
    }


    @NonNull private PumpEnactResult deliverBolus(final DetailedBolusInfo detailedBolusInfo) {
        aapsLogger.debug(LTag.PUMPCOMM, "deliverBolus");
        bolusProfile.setInsulin(detailedBolusInfo.insulin);
        return equilManager.bolus(detailedBolusInfo, bolusProfile);
    }


    public void showToast(String s) {
        loopHandler.post(() -> Toast.makeText(context, s, Toast.LENGTH_SHORT).show());
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

    public boolean checkProfile() {
        Profile profile = profileFunction.getProfile();
        if (profile == null) {
            return true;
        }
        return true;
    }

    public void playAlarm() {
        int battery = equilManager.getBattery();
        int insulin = equilManager.getCurrentInsulin();
        boolean alarmBattery = sp.getBoolean(EquilConst.Prefs.EQUIL_ALARM_BATTERY, true);
        boolean alarmInsulin = sp.getBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN, true);
        if (battery <= 10 && alarmBattery) {
            boolean alarmBattery10 =
                    sp.getBoolean(EquilConst.Prefs.Equil_ALARM_BATTERY_10, false);
            if (!alarmBattery10) {
                equilManager.showNotification(Notification.FAILED_UPDATE_PROFILE,
                        rh.gs(R.string.equil_lowbattery) + battery + "%",
                        Notification.NORMAL, app.aaps.core.ui.R.raw.alarm);
                sp.putBoolean(EquilConst.Prefs.Equil_ALARM_BATTERY_10, true);
            } else {
                if (battery < 5) {
                    equilManager.showNotification(Notification.FAILED_UPDATE_PROFILE,
                            rh.gs(R.string.equil_lowbattery) + battery + "%",
                            Notification.URGENT, app.aaps.core.ui.R.raw.alarm);
                }
            }
        }
        if (equilManager.getRunMode() == RunMode.RUN && alarmInsulin && equilManager.isActivationCompleted()) {
            if (insulin <= 10 && insulin > 5) {
                boolean alarmInsulin10 =
                        sp.getBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_10, false);
                if (!alarmInsulin10) {
                    equilManager.showNotification(Notification.FAILED_UPDATE_PROFILE,
                            rh.gs(R.string.equil_low_insulin) + insulin + "U",
                            Notification.NORMAL, app.aaps.core.ui.R.raw.alarm);
                    sp.putBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_10, true);
                }
            } else if (insulin <= 5 && insulin > 2) {
                boolean alarmInsulin5 =
                        sp.getBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_5, false);
                if (!alarmInsulin5) {
                    equilManager.showNotification(Notification.FAILED_UPDATE_PROFILE,
                            rh.gs(R.string.equil_low_insulin) + insulin + "U",
                            Notification.NORMAL, app.aaps.core.ui.R.raw.alarm);
                    sp.putBoolean(EquilConst.Prefs.EQUIL_ALARM_INSULIN_5, true);
                }
            } else if (insulin <= 2) {
                equilManager.showNotification(Notification.FAILED_UPDATE_PROFILE,
                        rh.gs(R.string.equil_low_insulin) + insulin + "U",
                        Notification.URGENT, app.aaps.core.ui.R.raw.alarm);
            }
        }
    }
}

