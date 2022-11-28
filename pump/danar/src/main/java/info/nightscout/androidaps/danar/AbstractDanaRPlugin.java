package info.nightscout.androidaps.danar;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.danar.services.AbstractDanaRExecutionService;
import info.nightscout.interfaces.constraints.Constraint;
import info.nightscout.interfaces.constraints.Constraints;
import info.nightscout.interfaces.notifications.Notification;
import info.nightscout.interfaces.plugin.ActivePlugin;
import info.nightscout.interfaces.plugin.OwnDatabasePlugin;
import info.nightscout.interfaces.plugin.PluginDescription;
import info.nightscout.interfaces.plugin.PluginType;
import info.nightscout.interfaces.profile.Profile;
import info.nightscout.interfaces.pump.Dana;
import info.nightscout.interfaces.pump.Pump;
import info.nightscout.interfaces.pump.PumpEnactResult;
import info.nightscout.interfaces.pump.PumpPluginBase;
import info.nightscout.interfaces.pump.PumpSync;
import info.nightscout.interfaces.pump.defs.ManufacturerType;
import info.nightscout.interfaces.pump.defs.PumpDescription;
import info.nightscout.interfaces.queue.CommandQueue;
import info.nightscout.interfaces.ui.UiInteraction;
import info.nightscout.interfaces.utils.DecimalFormatter;
import info.nightscout.interfaces.utils.Round;
import info.nightscout.pump.dana.DanaFragment;
import info.nightscout.pump.dana.DanaPump;
import info.nightscout.pump.dana.comm.RecordTypes;
import info.nightscout.pump.dana.database.DanaHistoryDatabase;
import info.nightscout.rx.AapsSchedulers;
import info.nightscout.rx.bus.RxBus;
import info.nightscout.rx.events.EventConfigBuilderChange;
import info.nightscout.rx.events.EventDismissNotification;
import info.nightscout.rx.events.EventPreferenceChange;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;
import info.nightscout.shared.interfaces.ResourceHelper;
import info.nightscout.shared.sharedPreferences.SP;
import info.nightscout.shared.utils.DateUtil;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Created by mike on 28.01.2018.
 */

public abstract class AbstractDanaRPlugin extends PumpPluginBase implements Pump, Dana, Constraints, OwnDatabasePlugin {
    protected AbstractDanaRExecutionService sExecutionService;

    protected CompositeDisposable disposable = new CompositeDisposable();

    protected boolean useExtendedBoluses = false;

    protected PumpDescription pumpDescription = new PumpDescription();
    protected DanaPump danaPump;
    protected Constraints constraintChecker;
    protected RxBus rxBus;
    protected ActivePlugin activePlugin;
    protected SP sp;
    protected DateUtil dateUtil;
    protected AapsSchedulers aapsSchedulers;
    protected PumpSync pumpSync;
    protected UiInteraction uiInteraction;
    protected DanaHistoryDatabase danaHistoryDatabase;
    protected AbstractDanaRPlugin(
            HasAndroidInjector injector,
            DanaPump danaPump,
            ResourceHelper rh,
            Constraints constraintChecker,
            AAPSLogger aapsLogger,
            AapsSchedulers aapsSchedulers,
            CommandQueue commandQueue,
            RxBus rxBus,
            ActivePlugin activePlugin,
            SP sp,
            DateUtil dateUtil,
            PumpSync pumpSync,
            UiInteraction uiInteraction,
            DanaHistoryDatabase danaHistoryDatabase
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.PUMP)
                        .fragmentClass(DanaFragment.class.getName())
                        .pluginIcon(R.drawable.ic_danars_128)
                        .pluginName(R.string.danarspump)
                        .shortName(R.string.danarpump_shortname)
                        .preferencesId(R.xml.pref_danar)
                        .description(R.string.description_pump_dana_r),
                injector, aapsLogger, rh, commandQueue
        );
        this.danaPump = danaPump;
        this.constraintChecker = constraintChecker;
        this.rxBus = rxBus;
        this.activePlugin = activePlugin;
        this.sp = sp;
        this.dateUtil = dateUtil;
        this.aapsSchedulers = aapsSchedulers;
        this.pumpSync = pumpSync;
        this.uiInteraction = uiInteraction;
        this.danaHistoryDatabase = danaHistoryDatabase;
    }

    @Override protected void onStart() {
        super.onStart();
        disposable.add(rxBus
                .toObservable(EventConfigBuilderChange.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> danaPump.reset())
        );
        disposable.add(rxBus
                .toObservable(EventPreferenceChange.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> {
                    if (event.isChanged(getRh().gs(R.string.key_danar_bt_name))) {
                        danaPump.reset();
                        pumpSync.connectNewPump(true);
                        getCommandQueue().readStatus(getRh().gs(R.string.device_changed), null);
                    }
                })
        );
    }

    @Override protected void onStop() {
        super.onStop();
        disposable.clear();
    }

    @Override
    public boolean isSuspended() {
        return danaPump.getPumpSuspended();
    }

    @Override
    public boolean isBusy() {
        if (sExecutionService == null) return false;
        return sExecutionService.isConnected() || sExecutionService.isConnecting();
    }

    // Pump interface
    @NonNull @Override
    public PumpEnactResult setNewBasalProfile(@NonNull Profile profile) {
        PumpEnactResult result = new PumpEnactResult(getInjector());

        if (sExecutionService == null) {
            getAapsLogger().error("setNewBasalProfile sExecutionService is null");
            result.comment("setNewBasalProfile sExecutionService is null");
            return result;
        }
        if (!isInitialized()) {
            getAapsLogger().error("setNewBasalProfile not initialized");
            uiInteraction.addNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED, getRh().gs(R.string.pump_not_initialized_profile_not_set), Notification.URGENT);
            result.comment(R.string.pump_not_initialized_profile_not_set);
            return result;
        } else {
            rxBus.send(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
        }
        if (!sExecutionService.updateBasalsInPump(profile)) {
            uiInteraction.addNotification(Notification.FAILED_UPDATE_PROFILE, getRh().gs(R.string.failed_update_basal_profile), Notification.URGENT);
            result.comment(R.string.failed_update_basal_profile);
        } else {
            rxBus.send(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
            rxBus.send(new EventDismissNotification(Notification.FAILED_UPDATE_PROFILE));
            uiInteraction.addNotificationValidFor(Notification.PROFILE_SET_OK, getRh().gs(R.string.profile_set_ok), Notification.INFO, 60);
            result.success(true).enacted(true).comment("OK");
        }
        return result;
    }

    @Override
    public boolean isThisProfileSet(@NonNull Profile profile) {
        if (!isInitialized())
            return true;
        if (danaPump.getPumpProfiles() == null)
            return true;
        int basalValues = danaPump.getBasal48Enable() ? 48 : 24;
        int basalIncrement = danaPump.getBasal48Enable() ? 30 * 60 : 60 * 60;
        for (int h = 0; h < basalValues; h++) {
            Double pumpValue = danaPump.getPumpProfiles()[danaPump.getActiveProfile()][h];
            Double profileValue = profile.getBasalTimeFromMidnight(h * basalIncrement);
            if (Math.abs(pumpValue - profileValue) > getPumpDescription().getBasalStep()) {
                getAapsLogger().debug(LTag.PUMP, "Diff found. Hour: " + h + " Pump: " + pumpValue + " Profile: " + profileValue);
                return false;
            }
        }
        return true;
    }

    @Override
    public long lastDataTime() {
        return danaPump.getLastConnection();
    }

    @Override
    public double getBaseBasalRate() {
        return danaPump.getCurrentBasal();
    }

    @Override
    public double getReservoirLevel() {
        return danaPump.getReservoirRemainingUnits();
    }

    @Override
    public int getBatteryLevel() {
        return danaPump.getBatteryRemaining();
    }

    @Override
    public void stopBolusDelivering() {
        if (sExecutionService == null) {
            getAapsLogger().error("stopBolusDelivering sExecutionService is null");
            return;
        }
        sExecutionService.bolusStop();
    }

    @NonNull @Override
    public PumpEnactResult setTempBasalPercent(int percent, int durationInMinutes, @NonNull Profile profile, boolean enforceNew, @NonNull PumpSync.TemporaryBasalType tbrType) {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        percent = constraintChecker.applyBasalPercentConstraints(new Constraint<>(percent), profile).value();
        if (percent < 0) {
            result.isTempCancel(false).enacted(false).success(false).comment(R.string.invalid_input);
            getAapsLogger().error("setTempBasalPercent: Invalid input");
            return result;
        }
        if (percent > getPumpDescription().getMaxTempPercent())
            percent = getPumpDescription().getMaxTempPercent();
        if (danaPump.isTempBasalInProgress() && danaPump.getTempBasalPercent() == percent && danaPump.getTempBasalRemainingMin() > 4 && !enforceNew) {
            result.enacted(false).success(true).isTempCancel(false)
                    .comment(R.string.ok)
                    .duration(danaPump.getTempBasalRemainingMin())
                    .percent(danaPump.getTempBasalPercent())
                    .isPercent(true);
            getAapsLogger().debug(LTag.PUMP, "setTempBasalPercent: Correct value already set");
            return result;
        }
        int durationInHours = Math.max(durationInMinutes / 60, 1);
        boolean connectionOK = sExecutionService.tempBasal(percent, durationInHours);
        if (connectionOK && danaPump.isTempBasalInProgress() && danaPump.getTempBasalPercent() == percent) {
            result.enacted(true)
                    .success(true)
                    .comment(R.string.ok)
                    .isTempCancel(false)
                    .duration((int) danaPump.getTempBasalDuration())
                    .percent(danaPump.getTempBasalPercent())
                    .isPercent(true);
            getAapsLogger().debug(LTag.PUMP, "setTempBasalPercent: OK");
            pumpSync.syncTemporaryBasalWithPumpId(
                    danaPump.getTempBasalStart(),
                    danaPump.getTempBasalPercent(),
                    danaPump.getTempBasalDuration(),
                    false,
                    tbrType,
                    danaPump.getTempBasalStart(),
                    getPumpDescription().getPumpType(),
                    serialNumber()
            );
            return result;
        }
        result.enacted(false).success(false).comment(R.string.temp_basal_delivery_error);
        getAapsLogger().error("setTempBasalPercent: Failed to set temp basal");
        return result;
    }

    @NonNull @Override
    public PumpEnactResult setExtendedBolus(double insulin, int durationInMinutes) {
        insulin = constraintChecker.applyExtendedBolusConstraints(new Constraint<>(insulin)).value();
        // needs to be rounded
        int durationInHalfHours = Math.max(durationInMinutes / 30, 1);
        insulin = Round.INSTANCE.roundTo(insulin, getPumpDescription().getExtendedBolusStep());

        PumpEnactResult result = new PumpEnactResult(getInjector());
        if (danaPump.isExtendedInProgress() && Math.abs(danaPump.getExtendedBolusAmount() - insulin) < getPumpDescription().getExtendedBolusStep()) {
            result.enacted(false)
                    .success(true)
                    .comment(R.string.ok)
                    .duration(danaPump.getExtendedBolusRemainingMinutes())
                    .absolute(danaPump.getExtendedBolusAbsoluteRate())
                    .isPercent(false)
                    .isTempCancel(false);
            getAapsLogger().debug(LTag.PUMP, "setExtendedBolus: Correct extended bolus already set. Current: " + danaPump.getExtendedBolusAmount() + " Asked: " + insulin);
            return result;
        }
        if (danaPump.isExtendedInProgress()) {
            cancelExtendedBolus();
            if (danaPump.isExtendedInProgress()) {
                result.enacted(false).success(false);
                getAapsLogger().debug(LTag.PUMP, "cancelExtendedBolus failed. aborting setExtendedBolus");
                return result;
            }
        }
        boolean connectionOK = sExecutionService.extendedBolus(insulin, durationInHalfHours);
        if (connectionOK && danaPump.isExtendedInProgress() && Math.abs(danaPump.getExtendedBolusAmount() - insulin) < getPumpDescription().getExtendedBolusStep()) {
            result.enacted(true)
                    .success(true)
                    .comment(R.string.ok)
                    .isTempCancel(false)
                    .duration(danaPump.getExtendedBolusRemainingMinutes())
                    .absolute(danaPump.getExtendedBolusAbsoluteRate())
                    .isPercent(false);
            if (!sp.getBoolean("danar_useextended", false))
                result.bolusDelivered(danaPump.getExtendedBolusAmount());
            pumpSync.syncExtendedBolusWithPumpId(
                    danaPump.getExtendedBolusStart(),
                    danaPump.getExtendedBolusAmount(),
                    danaPump.getExtendedBolusDuration(),
                    sp.getBoolean("danar_useextended", false),
                    danaPump.getExtendedBolusStart(),
                    getPumpDescription().getPumpType(),
                    serialNumber()
            );
            getAapsLogger().debug(LTag.PUMP, "setExtendedBolus: OK");
            return result;
        }
        result.enacted(false).success(false).comment(R.string.danar_valuenotsetproperly);
        getAapsLogger().error("setExtendedBolus: Failed to extended bolus");
        getAapsLogger().error("inProgress: " + danaPump.isExtendedInProgress() + " start: " + danaPump.getExtendedBolusStart() + " amount: " + danaPump.getExtendedBolusAmount() + " duration: " + danaPump.getExtendedBolusDuration());
        return result;
    }

    @NonNull @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        if (danaPump.isExtendedInProgress()) {
            sExecutionService.extendedBolusStop();
            if (!danaPump.isExtendedInProgress()) {
                result.success(true).enacted(true).isTempCancel(true);
                pumpSync.syncStopExtendedBolusWithPumpId(
                        dateUtil.now(),
                        dateUtil.now(),
                        getPumpDescription().getPumpType(),
                        serialNumber()
                );
            } else
                result.success(false).enacted(false).isTempCancel(true);
        } else {
            result.success(true).comment(R.string.ok).isTempCancel(true);
            getAapsLogger().debug(LTag.PUMP, "cancelExtendedBolus: OK");
        }
        return result;
    }

    @Override
    public void connect(@NonNull String from) {
        if (sExecutionService != null) {
            sExecutionService.connect();
            pumpDescription.setBasalStep(danaPump.getBasalStep());
            pumpDescription.setBolusStep(danaPump.getBolusStep());
        }
    }

    @Override
    public boolean isConnected() {
        return sExecutionService != null && sExecutionService.isConnected();
    }

    @Override
    public boolean isConnecting() {
        return sExecutionService != null && sExecutionService.isConnecting();
    }

    @Override
    public void disconnect(@NonNull String from) {
        if (sExecutionService != null) sExecutionService.disconnect(from);
    }

    @Override
    public void stopConnecting() {
        if (sExecutionService != null) sExecutionService.stopConnecting();
    }

    @Override
    public void getPumpStatus(@NonNull String reason) {
        if (sExecutionService != null) {
            sExecutionService.getPumpStatus();
            pumpDescription.setBasalStep(danaPump.getBasalStep());
            pumpDescription.setBolusStep(danaPump.getBolusStep());
        }
    }

    @NonNull @Override
    public JSONObject getJSONStatus(@NonNull Profile profile, @NonNull String profileName, @NonNull String version) {
        DanaPump pump = danaPump;
        long now = System.currentTimeMillis();
        if (pump.getLastConnection() + 60 * 60 * 1000L < System.currentTimeMillis()) {
            return new JSONObject();
        }
        JSONObject pumpJson = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", pump.getBatteryRemaining());
            status.put("status", pump.getPumpSuspended() ? "suspended" : "normal");
            status.put("timestamp", dateUtil.toISOString(pump.getLastConnection()));
            extended.put("Version", version);
            if (pump.getLastBolusTime() != 0) {
                extended.put("LastBolus", dateUtil.dateAndTimeString(pump.getLastBolusTime()));
                extended.put("LastBolusAmount", pump.getLastBolusAmount());
            }
            PumpSync.PumpState pumpState = pumpSync.expectedPumpState();
            if (pumpState.getTemporaryBasal() != null) {
                extended.put("TempBasalAbsoluteRate", pumpState.getTemporaryBasal().convertedToAbsolute(now, profile));
                extended.put("TempBasalStart", dateUtil.dateAndTimeString(pumpState.getTemporaryBasal().getTimestamp()));
                extended.put("TempBasalRemaining", pumpState.getTemporaryBasal().getPlannedRemainingMinutes());
            }
            if (pumpState.getExtendedBolus() != null) {
                extended.put("ExtendedBolusAbsoluteRate", pumpState.getExtendedBolus().getRate());
                extended.put("ExtendedBolusStart", dateUtil.dateAndTimeString(pumpState.getExtendedBolus().getTimestamp()));
                extended.put("ExtendedBolusRemaining", pumpState.getExtendedBolus().getPlannedRemainingMinutes());
            }
            extended.put("BaseBasalRate", getBaseBasalRate());
            try {
                extended.put("ActiveProfile", profileName);
            } catch (Exception ignored) {
            }

            pumpJson.put("battery", battery);
            pumpJson.put("status", status);
            pumpJson.put("extended", extended);
            pumpJson.put("reservoir", (int) pump.getReservoirRemainingUnits());
            pumpJson.put("clock", dateUtil.toISOString(dateUtil.now()));
        } catch (JSONException e) {
            getAapsLogger().error("Unhandled exception", e);
        }
        return pumpJson;
    }

    @NonNull @Override
    public ManufacturerType manufacturer() {
        return ManufacturerType.Sooil;
    }

    @NonNull @Override
    public String serialNumber() {
        return danaPump.getSerialNumber();
    }

    @NonNull @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    /**
     * DanaR interface
     */

    @NonNull @Override
    public PumpEnactResult loadHistory(byte type) {
        return sExecutionService.loadHistory(type);
    }

    /**
     * Constraint interface
     */

    @NonNull @Override
    public Constraint<Double> applyBasalConstraints(Constraint<Double> absoluteRate, @NonNull Profile profile) {
        absoluteRate.setIfSmaller(getAapsLogger(), danaPump.getMaxBasal(), getRh().gs(R.string.limitingbasalratio, danaPump.getMaxBasal(), getRh().gs(R.string.pumplimit)), this);
        return absoluteRate;
    }

    @NonNull @Override
    public Constraint<Integer> applyBasalPercentConstraints(Constraint<Integer> percentRate, @NonNull Profile profile) {
        percentRate.setIfGreater(getAapsLogger(), 0, getRh().gs(R.string.limitingpercentrate, 0, getRh().gs(R.string.itmustbepositivevalue)), this);
        percentRate.setIfSmaller(getAapsLogger(), getPumpDescription().getMaxTempPercent(), getRh().gs(R.string.limitingpercentrate, getPumpDescription().getMaxTempPercent(), getRh().gs(R.string.pumplimit)), this);

        return percentRate;
    }

    @NonNull @Override
    public Constraint<Double> applyBolusConstraints(Constraint<Double> insulin) {
        insulin.setIfSmaller(getAapsLogger(), danaPump.getMaxBolus(), getRh().gs(R.string.limitingbolus, danaPump.getMaxBolus(), getRh().gs(R.string.pumplimit)), this);
        return insulin;
    }

    @NonNull @Override
    public Constraint<Double> applyExtendedBolusConstraints(@NonNull Constraint<Double> insulin) {
        return applyBolusConstraints(insulin);
    }

    @NonNull @Override
    public PumpEnactResult loadTDDs() {
        return loadHistory(RecordTypes.RECORD_TYPE_DAILY);
    }

    // Reply for sms communicator
    @NonNull public String shortStatus(boolean veryShort) {
        String ret = "";
        if (danaPump.getLastConnection() != 0) {
            long agoMilliseconds = System.currentTimeMillis() - danaPump.getLastConnection();
            int agoMin = (int) (agoMilliseconds / 60d / 1000d);
            ret += "LastConn: " + agoMin + " min ago\n";
        }
        if (danaPump.getLastBolusTime() != 0) {
            ret += "LastBolus: " + DecimalFormatter.INSTANCE.to2Decimal(danaPump.getLastBolusAmount()) + "U @" + android.text.format.DateFormat.format("HH:mm", danaPump.getLastBolusTime()) + "\n";
        }
        PumpSync.PumpState pumpState = pumpSync.expectedPumpState();
        if (pumpState.getTemporaryBasal() != null) {
            ret += "Temp: " + pumpState.getTemporaryBasal().toStringFull(dateUtil) + "\n";
        }
        if (pumpState.getExtendedBolus() != null) {
            ret += "Extended: " + pumpState.getExtendedBolus().toStringFull(dateUtil) + "\n";
        }
        if (!veryShort) {
            ret += "TDD: " + DecimalFormatter.INSTANCE.to0Decimal(danaPump.getDailyTotalUnits()) + " / " + danaPump.getMaxDailyTotalUnits() + " U\n";
        }
        ret += "Reserv: " + DecimalFormatter.INSTANCE.to0Decimal(danaPump.getReservoirRemainingUnits()) + "U\n";
        ret += "Batt: " + danaPump.getBatteryRemaining() + "\n";
        return ret;
    }
    // TODO: daily total constraint

    @Override
    public boolean canHandleDST() {
        return false;
    }

    @Override public void clearPairing() {
    }

    @Override public void clearAllTables() {
        danaHistoryDatabase.clearAllTables();
    }
}
