package info.nightscout.androidaps.plugins.pump.danaR;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.List;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.DanaRInterface;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.PumpPluginBase;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.danaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.pump.danaR.services.AbstractDanaRExecutionService;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.TimeChangeType;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

/**
 * Created by mike on 28.01.2018.
 */

public abstract class AbstractDanaRPlugin extends PumpPluginBase implements PumpInterface, DanaRInterface, ConstraintsInterface {
    protected AbstractDanaRExecutionService sExecutionService;

    protected boolean useExtendedBoluses = false;

    protected PumpDescription pumpDescription = new PumpDescription();
    protected DanaRPump danaRPump;
    protected ConstraintChecker constraintChecker;
    protected RxBusWrapper rxBus;
    protected TreatmentsPlugin treatmentsPlugin;
    protected SP sp;

    protected AbstractDanaRPlugin(
            HasAndroidInjector injector,
            DanaRPump danaRPump,
            ResourceHelper resourceHelper,
            ConstraintChecker constraintChecker,
            AAPSLogger aapsLogger,
            CommandQueueProvider commandQueue,
            RxBusWrapper rxBus,
            TreatmentsPlugin treatmentsPlugin,
            SP sp
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.PUMP)
                        .fragmentClass(DanaRFragment.class.getName())
                        .pluginName(R.string.danarspump)
                        .shortName(R.string.danarpump_shortname)
                        .preferencesId(R.xml.pref_danars)
                        .description(R.string.description_pump_dana_r),
                injector, aapsLogger, resourceHelper, commandQueue
        );
        this.danaRPump = danaRPump;
        this.constraintChecker = constraintChecker;
        this.rxBus = rxBus;
        this.treatmentsPlugin = treatmentsPlugin;
        this.sp = sp;
    }

    @Override
    public boolean isSuspended() {
        return danaRPump.getPumpSuspended();
    }

    @Override
    public boolean isBusy() {
        if (sExecutionService == null) return false;
        return sExecutionService.isConnected() || sExecutionService.isConnecting();
    }

    // Pump interface
    @NonNull @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        PumpEnactResult result = new PumpEnactResult(getInjector());

        if (sExecutionService == null) {
            getAapsLogger().error("setNewBasalProfile sExecutionService is null");
            result.comment = "setNewBasalProfile sExecutionService is null";
            return result;
        }
        if (!isInitialized()) {
            getAapsLogger().error("setNewBasalProfile not initialized");
            Notification notification = new Notification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED, getResourceHelper().gs(R.string.pumpNotInitializedProfileNotSet), Notification.URGENT);
            rxBus.send(new EventNewNotification(notification));
            result.comment = getResourceHelper().gs(R.string.pumpNotInitializedProfileNotSet);
            return result;
        } else {
            rxBus.send(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
        }
        if (!sExecutionService.updateBasalsInPump(profile)) {
            Notification notification = new Notification(Notification.FAILED_UDPATE_PROFILE, getResourceHelper().gs(R.string.failedupdatebasalprofile), Notification.URGENT);
            rxBus.send(new EventNewNotification(notification));
            result.comment = getResourceHelper().gs(R.string.failedupdatebasalprofile);
            return result;
        } else {
            rxBus.send(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
            rxBus.send(new EventDismissNotification(Notification.FAILED_UDPATE_PROFILE));
            Notification notification = new Notification(Notification.PROFILE_SET_OK, getResourceHelper().gs(R.string.profile_set_ok), Notification.INFO, 60);
            rxBus.send(new EventNewNotification(notification));
            result.success = true;
            result.enacted = true;
            result.comment = "OK";
            return result;
        }
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        if (!isInitialized())
            return true; // TODO: not sure what's better. so far TRUE to prevent too many SMS
        if (danaRPump.getPumpProfiles() == null)
            return true; // TODO: not sure what's better. so far TRUE to prevent too many SMS
        int basalValues = danaRPump.getBasal48Enable() ? 48 : 24;
        int basalIncrement = danaRPump.getBasal48Enable() ? 30 * 60 : 60 * 60;
        for (int h = 0; h < basalValues; h++) {
            Double pumpValue = danaRPump.getPumpProfiles()[danaRPump.getActiveProfile()][h];
            Double profileValue = profile.getBasalTimeFromMidnight(h * basalIncrement);
            if (Math.abs(pumpValue - profileValue) > getPumpDescription().basalStep) {
                getAapsLogger().debug(LTag.PUMP, "Diff found. Hour: " + h + " Pump: " + pumpValue + " Profile: " + profileValue);
                return false;
            }
        }
        return true;
    }

    @Override
    public long lastDataTime() {
        return danaRPump.getLastConnection();
    }

    @Override
    public double getBaseBasalRate() {
        return danaRPump.getCurrentBasal();
    }

    @Override
    public double getReservoirLevel() {
        return danaRPump.getReservoirRemainingUnits();
    }

    @Override
    public int getBatteryLevel() {
        return danaRPump.getBatteryRemaining();
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
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        DanaRPump pump = danaRPump;
        PumpEnactResult result = new PumpEnactResult(getInjector());
        percent = constraintChecker.applyBasalPercentConstraints(new Constraint<>(percent), profile).value();
        if (percent < 0) {
            result.isTempCancel = false;
            result.enacted = false;
            result.success = false;
            result.comment = getResourceHelper().gs(R.string.danar_invalidinput);
            getAapsLogger().error("setTempBasalPercent: Invalid input");
            return result;
        }
        if (percent > getPumpDescription().maxTempPercent)
            percent = getPumpDescription().maxTempPercent;
        long now = System.currentTimeMillis();
        TemporaryBasal activeTemp = treatmentsPlugin.getRealTempBasalFromHistory(now);
        if (activeTemp != null && activeTemp.percentRate == percent && activeTemp.getPlannedRemainingMinutes() > 4 && !enforceNew) {
            result.enacted = false;
            result.success = true;
            result.isTempCancel = false;
            result.comment = getResourceHelper().gs(R.string.virtualpump_resultok);
            result.duration = pump.getTempBasalRemainingMin();
            result.percent = pump.getTempBasalPercent();
            result.isPercent = true;
            getAapsLogger().debug(LTag.PUMP, "setTempBasalPercent: Correct value already set");
            return result;
        }
        int durationInHours = Math.max(durationInMinutes / 60, 1);
        boolean connectionOK = sExecutionService.tempBasal(percent, durationInHours);
        if (connectionOK && pump.isTempBasalInProgress() && pump.getTempBasalPercent() == percent) {
            result.enacted = true;
            result.success = true;
            result.comment = getResourceHelper().gs(R.string.virtualpump_resultok);
            result.isTempCancel = false;
            result.duration = pump.getTempBasalRemainingMin();
            result.percent = pump.getTempBasalPercent();
            result.isPercent = true;
            getAapsLogger().debug(LTag.PUMP, "setTempBasalPercent: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = getResourceHelper().gs(R.string.tempbasaldeliveryerror);
        getAapsLogger().error("setTempBasalPercent: Failed to set temp basal");
        return result;
    }

    @NonNull @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        DanaRPump pump = danaRPump;
        insulin = constraintChecker.applyExtendedBolusConstraints(new Constraint<>(insulin)).value();
        // needs to be rounded
        int durationInHalfHours = Math.max(durationInMinutes / 30, 1);
        insulin = Round.roundTo(insulin, getPumpDescription().extendedBolusStep);

        PumpEnactResult result = new PumpEnactResult(getInjector());
        ExtendedBolus runningEB = treatmentsPlugin.getExtendedBolusFromHistory(System.currentTimeMillis());
        if (runningEB != null && Math.abs(runningEB.insulin - insulin) < getPumpDescription().extendedBolusStep) {
            result.enacted = false;
            result.success = true;
            result.comment = getResourceHelper().gs(R.string.virtualpump_resultok);
            result.duration = pump.getExtendedBolusRemainingMinutes();
            result.absolute = pump.getExtendedBolusAbsoluteRate();
            result.isPercent = false;
            result.isTempCancel = false;
            getAapsLogger().debug(LTag.PUMP, "setExtendedBolus: Correct extended bolus already set. Current: " + pump.getExtendedBolusAmount() + " Asked: " + insulin);
            return result;
        }
        boolean connectionOK = sExecutionService.extendedBolus(insulin, durationInHalfHours);
        if (connectionOK && pump.isExtendedInProgress() && Math.abs(pump.getExtendedBolusAmount() - insulin) < getPumpDescription().extendedBolusStep) {
            result.enacted = true;
            result.success = true;
            result.comment = getResourceHelper().gs(R.string.virtualpump_resultok);
            result.isTempCancel = false;
            result.duration = pump.getExtendedBolusRemainingMinutes();
            result.absolute = pump.getExtendedBolusAbsoluteRate();
            if (!sp.getBoolean("danar_useextended", false))
                result.bolusDelivered = pump.getExtendedBolusAmount();
            result.isPercent = false;
            getAapsLogger().debug(LTag.PUMP, "setExtendedBolus: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = getResourceHelper().gs(R.string.danar_valuenotsetproperly);
        getAapsLogger().error("setExtendedBolus: Failed to extended bolus");
        return result;
    }

    @NonNull @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        ExtendedBolus runningEB = treatmentsPlugin.getExtendedBolusFromHistory(System.currentTimeMillis());
        if (runningEB != null) {
            sExecutionService.extendedBolusStop();
            result.enacted = true;
            result.isTempCancel = true;
        }
        if (!danaRPump.isExtendedInProgress()) {
            result.success = true;
            result.comment = getResourceHelper().gs(R.string.virtualpump_resultok);
            getAapsLogger().debug(LTag.PUMP, "cancelExtendedBolus: OK");
            return result;
        } else {
            result.success = false;
            result.comment = getResourceHelper().gs(R.string.danar_valuenotsetproperly);
            getAapsLogger().error("cancelExtendedBolus: Failed to cancel extended bolus");
            return result;
        }
    }

    @Override
    public void connect(String from) {
        if (sExecutionService != null) {
            sExecutionService.connect();
            pumpDescription.basalStep = danaRPump.getBasalStep();
            pumpDescription.bolusStep = danaRPump.getBolusStep();
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
    public void disconnect(String from) {
        if (sExecutionService != null) sExecutionService.disconnect(from);
    }

    @Override
    public void stopConnecting() {
        if (sExecutionService != null) sExecutionService.stopConnecting();
    }

    @Override
    public void getPumpStatus() {
        if (sExecutionService != null) {
            sExecutionService.getPumpStatus();
            pumpDescription.basalStep = danaRPump.getBasalStep();
            pumpDescription.bolusStep = danaRPump.getBolusStep();
        }
    }

    @NonNull @Override
    public JSONObject getJSONStatus(Profile profile, String profilename) {
        DanaRPump pump = danaRPump;
        long now = System.currentTimeMillis();
        if (pump.getLastConnection() + 5 * 60 * 1000L < System.currentTimeMillis()) {
            return null;
        }
        JSONObject pumpjson = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", pump.getBatteryRemaining());
            status.put("status", pump.getPumpSuspended() ? "suspended" : "normal");
            status.put("timestamp", DateUtil.toISOString(pump.getLastConnection()));
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            if (pump.getLastBolusTime() != 0) {
                extended.put("LastBolus", DateUtil.dateAndTimeString(pump.getLastBolusTime()));
                extended.put("LastBolusAmount", pump.getLastBolusAmount());
            }
            TemporaryBasal tb = treatmentsPlugin.getRealTempBasalFromHistory(now);
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.tempBasalConvertedToAbsolute(now, profile));
                extended.put("TempBasalStart", DateUtil.dateAndTimeString(tb.date));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }
            ExtendedBolus eb = treatmentsPlugin.getExtendedBolusFromHistory(now);
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.absoluteRate());
                extended.put("ExtendedBolusStart", DateUtil.dateAndTimeString(eb.date));
                extended.put("ExtendedBolusRemaining", eb.getPlannedRemainingMinutes());
            }
            extended.put("BaseBasalRate", getBaseBasalRate());
            try {
                extended.put("ActiveProfile", profilename);
            } catch (Exception ignored) {
            }

            pumpjson.put("battery", battery);
            pumpjson.put("status", status);
            pumpjson.put("extended", extended);
            pumpjson.put("reservoir", (int) pump.getReservoirRemainingUnits());
            pumpjson.put("clock", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
            getAapsLogger().error("Unhandled exception", e);
        }
        return pumpjson;
    }

    @NonNull @Override
    public ManufacturerType manufacturer() {
        return ManufacturerType.Sooil;
    }

    @NonNull @Override
    public String serialNumber() {
        return danaRPump.getSerialNumber();
    }

    @NonNull @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    /**
     * DanaR interface
     */

    @Override
    public PumpEnactResult loadHistory(byte type) {
        return sExecutionService.loadHistory(type);
    }

    /**
     * Constraint interface
     */

    @NonNull @Override
    public Constraint<Double> applyBasalConstraints(Constraint<Double> absoluteRate, @NonNull Profile profile) {
        absoluteRate.setIfSmaller(getAapsLogger(), danaRPump.getMaxBasal(), String.format(getResourceHelper().gs(R.string.limitingbasalratio), danaRPump.getMaxBasal(), getResourceHelper().gs(R.string.pumplimit)), this);
        return absoluteRate;
    }

    @NonNull @Override
    public Constraint<Integer> applyBasalPercentConstraints(Constraint<Integer> percentRate, @NonNull Profile profile) {
        percentRate.setIfGreater(getAapsLogger(), 0, String.format(getResourceHelper().gs(R.string.limitingpercentrate), 0, getResourceHelper().gs(R.string.itmustbepositivevalue)), this);
        percentRate.setIfSmaller(getAapsLogger(), getPumpDescription().maxTempPercent, String.format(getResourceHelper().gs(R.string.limitingpercentrate), getPumpDescription().maxTempPercent, getResourceHelper().gs(R.string.pumplimit)), this);

        return percentRate;
    }

    @NonNull @Override
    public Constraint<Double> applyBolusConstraints(Constraint<Double> insulin) {
        insulin.setIfSmaller(getAapsLogger(), danaRPump.getMaxBolus(), String.format(getResourceHelper().gs(R.string.limitingbolus), danaRPump.getMaxBolus(), getResourceHelper().gs(R.string.pumplimit)), this);
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
        DanaRPump pump = danaRPump;
        String ret = "";
        if (pump.getLastConnection() != 0) {
            long agoMsec = System.currentTimeMillis() - pump.getLastConnection();
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += "LastConn: " + agoMin + " minago\n";
        }
        if (pump.getLastBolusTime() != 0) {
            ret += "LastBolus: " + DecimalFormatter.to2Decimal(pump.getLastBolusAmount()) + "U @" + android.text.format.DateFormat.format("HH:mm", pump.getLastBolusTime()) + "\n";
        }
        TemporaryBasal activeTemp = treatmentsPlugin.getRealTempBasalFromHistory(System.currentTimeMillis());
        if (activeTemp != null) {
            ret += "Temp: " + activeTemp.toStringFull() + "\n";
        }
        ExtendedBolus activeExtendedBolus = treatmentsPlugin.getExtendedBolusFromHistory(System.currentTimeMillis());
        if (activeExtendedBolus != null) {
            ret += "Extended: " + activeExtendedBolus.toString() + "\n";
        }
        if (!veryShort) {
            ret += "TDD: " + DecimalFormatter.to0Decimal(pump.getDailyTotalUnits()) + " / " + pump.getMaxDailyTotalUnits() + " U\n";
        }
        ret += "Reserv: " + DecimalFormatter.to0Decimal(pump.getReservoirRemainingUnits()) + "U\n";
        ret += "Batt: " + pump.getBatteryRemaining() + "\n";
        return ret;
    }
    // TODO: daily total constraint


    @Override
    public List<CustomAction> getCustomActions() {
        return null;
    }


    @Override
    public void executeCustomAction(CustomActionType customActionType) {

    }

    @Override
    public boolean canHandleDST() {
        return false;
    }

    @Override
    public void timezoneOrDSTChanged(TimeChangeType timeChangeType) {
    }


}
