package info.nightscout.androidaps.plugins.pump.danaR;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.DanaRInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.profile.ns.NSProfilePlugin;
import info.nightscout.androidaps.plugins.pump.danaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.pump.danaR.services.AbstractDanaRExecutionService;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.SP;

/**
 * Created by mike on 28.01.2018.
 */

public abstract class AbstractDanaRPlugin extends PluginBase implements PumpInterface, DanaRInterface, ConstraintsInterface, ProfileInterface {
    protected Logger log = LoggerFactory.getLogger(L.PUMP);

    protected AbstractDanaRExecutionService sExecutionService;

    protected boolean useExtendedBoluses = false;

    public PumpDescription pumpDescription = new PumpDescription();

    protected AbstractDanaRPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.PUMP)
                .fragmentClass(DanaRFragment.class.getName())
                .pluginName(R.string.danarspump)
                .shortName(R.string.danarpump_shortname)
                .preferencesId(R.xml.pref_danars)
                .description(R.string.description_pump_dana_r)
        );
    }

    @Override
    public void onStateChange(PluginType type, State oldState, State newState) {
        // if pump profile was enabled need to switch to another too
        if (type == PluginType.PUMP && newState == State.DISABLED && isProfileInterfaceEnabled) {
            setPluginEnabled(PluginType.PROFILE, false);
            NSProfilePlugin.getPlugin().setPluginEnabled(PluginType.PROFILE, true);
            NSProfilePlugin.getPlugin().setFragmentVisible(PluginType.PROFILE, true);
        }
    }

    @Override
    public void switchAllowed(boolean newState, FragmentActivity activity, PluginType type) {
        confirmPumpPluginActivation(newState, activity, type);
    }

    @Override
    public boolean isSuspended() {
        return DanaRPump.getInstance().pumpSuspended;
    }

    @Override
    public boolean isBusy() {
        if (sExecutionService == null) return false;
        return sExecutionService.isConnected() || sExecutionService.isConnecting();
    }

    // Pump interface
    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        PumpEnactResult result = new PumpEnactResult();

        if (sExecutionService == null) {
            log.error("setNewBasalProfile sExecutionService is null");
            result.comment = "setNewBasalProfile sExecutionService is null";
            return result;
        }
        if (!isInitialized()) {
            log.error("setNewBasalProfile not initialized");
            Notification notification = new Notification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED, MainApp.gs(R.string.pumpNotInitializedProfileNotSet), Notification.URGENT);
            RxBus.INSTANCE.send(new EventNewNotification(notification));
            result.comment = MainApp.gs(R.string.pumpNotInitializedProfileNotSet);
            return result;
        } else {
            RxBus.INSTANCE.send(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
        }
        if (!sExecutionService.updateBasalsInPump(profile)) {
            Notification notification = new Notification(Notification.FAILED_UDPATE_PROFILE, MainApp.gs(R.string.failedupdatebasalprofile), Notification.URGENT);
            RxBus.INSTANCE.send(new EventNewNotification(notification));
            result.comment = MainApp.gs(R.string.failedupdatebasalprofile);
            return result;
        } else {
            RxBus.INSTANCE.send(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
            RxBus.INSTANCE.send(new EventDismissNotification(Notification.FAILED_UDPATE_PROFILE));
            Notification notification = new Notification(Notification.PROFILE_SET_OK, MainApp.gs(R.string.profile_set_ok), Notification.INFO, 60);
            RxBus.INSTANCE.send(new EventNewNotification(notification));
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
        DanaRPump pump = DanaRPump.getInstance();
        if (pump.pumpProfiles == null)
            return true; // TODO: not sure what's better. so far TRUE to prevent too many SMS
        int basalValues = pump.basal48Enable ? 48 : 24;
        int basalIncrement = pump.basal48Enable ? 30 * 60 : 60 * 60;
        for (int h = 0; h < basalValues; h++) {
            Double pumpValue = pump.pumpProfiles[pump.activeProfile][h];
            Double profileValue = profile.getBasalTimeFromMidnight(h * basalIncrement);
            if (profileValue == null) return true;
            if (Math.abs(pumpValue - profileValue) > getPumpDescription().basalStep) {
                if (L.isEnabled(L.PUMP))
                    log.debug("Diff found. Hour: " + h + " Pump: " + pumpValue + " Profile: " + profileValue);
                return false;
            }
        }
        return true;
    }

    @Override
    public long lastDataTime() {
        return DanaRPump.getInstance().lastConnection;
    }

    @Override
    public double getBaseBasalRate() {
        return DanaRPump.getInstance().currentBasal;
    }

    @Override
    public double getReservoirLevel() { return DanaRPump.getInstance().reservoirRemainingUnits; }

    @Override
    public int getBatteryLevel() { return DanaRPump.getInstance().batteryRemaining; }

    @Override
    public void stopBolusDelivering() {
        if (sExecutionService == null) {
            log.error("stopBolusDelivering sExecutionService is null");
            return;
        }
        sExecutionService.bolusStop();
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        DanaRPump pump = DanaRPump.getInstance();
        PumpEnactResult result = new PumpEnactResult();
        percent = MainApp.getConstraintChecker().applyBasalPercentConstraints(new Constraint<>(percent), profile).value();
        if (percent < 0) {
            result.isTempCancel = false;
            result.enacted = false;
            result.success = false;
            result.comment = MainApp.gs(R.string.danar_invalidinput);
            log.error("setTempBasalPercent: Invalid input");
            return result;
        }
        if (percent > getPumpDescription().maxTempPercent)
            percent = getPumpDescription().maxTempPercent;
        long now = System.currentTimeMillis();
        TemporaryBasal activeTemp = TreatmentsPlugin.getPlugin().getRealTempBasalFromHistory(now);
        if (activeTemp != null && activeTemp.percentRate == percent && activeTemp.getPlannedRemainingMinutes() > 4 && !enforceNew) {
            result.enacted = false;
            result.success = true;
            result.isTempCancel = false;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            result.duration = pump.tempBasalRemainingMin;
            result.percent = pump.tempBasalPercent;
            result.isPercent = true;
            if (L.isEnabled(L.PUMP))
                log.debug("setTempBasalPercent: Correct value already set");
            return result;
        }
        int durationInHours = Math.max(durationInMinutes / 60, 1);
        boolean connectionOK = sExecutionService.tempBasal(percent, durationInHours);
        if (connectionOK && pump.isTempBasalInProgress && pump.tempBasalPercent == percent) {
            result.enacted = true;
            result.success = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            result.isTempCancel = false;
            result.duration = pump.tempBasalRemainingMin;
            result.percent = pump.tempBasalPercent;
            result.isPercent = true;
            if (L.isEnabled(L.PUMP))
                log.debug("setTempBasalPercent: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = MainApp.gs(R.string.tempbasaldeliveryerror);
        log.error("setTempBasalPercent: Failed to set temp basal");
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        DanaRPump pump = DanaRPump.getInstance();
        insulin = MainApp.getConstraintChecker().applyExtendedBolusConstraints(new Constraint<>(insulin)).value();
        // needs to be rounded
        int durationInHalfHours = Math.max(durationInMinutes / 30, 1);
        insulin = Round.roundTo(insulin, getPumpDescription().extendedBolusStep);

        PumpEnactResult result = new PumpEnactResult();
        ExtendedBolus runningEB = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(System.currentTimeMillis());
        if (runningEB != null && Math.abs(runningEB.insulin - insulin) < getPumpDescription().extendedBolusStep) {
            result.enacted = false;
            result.success = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            result.duration = pump.extendedBolusRemainingMinutes;
            result.absolute = pump.extendedBolusAbsoluteRate;
            result.isPercent = false;
            result.isTempCancel = false;
            if (L.isEnabled(L.PUMP))
                log.debug("setExtendedBolus: Correct extended bolus already set. Current: " + pump.extendedBolusAmount + " Asked: " + insulin);
            return result;
        }
        boolean connectionOK = sExecutionService.extendedBolus(insulin, durationInHalfHours);
        if (connectionOK && pump.isExtendedInProgress && Math.abs(pump.extendedBolusAmount - insulin) < getPumpDescription().extendedBolusStep) {
            result.enacted = true;
            result.success = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            result.isTempCancel = false;
            result.duration = pump.extendedBolusRemainingMinutes;
            result.absolute = pump.extendedBolusAbsoluteRate;
            if (!SP.getBoolean("danar_useextended", false))
                result.bolusDelivered = pump.extendedBolusAmount;
            result.isPercent = false;
            if (L.isEnabled(L.PUMP))
                log.debug("setExtendedBolus: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = MainApp.gs(R.string.danar_valuenotsetproperly);
        log.error("setExtendedBolus: Failed to extended bolus");
        return result;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult();
        ExtendedBolus runningEB = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(System.currentTimeMillis());
        if (runningEB != null) {
            sExecutionService.extendedBolusStop();
            result.enacted = true;
            result.isTempCancel = true;
        }
        if (!DanaRPump.getInstance().isExtendedInProgress) {
            result.success = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            if (L.isEnabled(L.PUMP))
                log.debug("cancelExtendedBolus: OK");
            return result;
        } else {
            result.success = false;
            result.comment = MainApp.gs(R.string.danar_valuenotsetproperly);
            log.error("cancelExtendedBolus: Failed to cancel extended bolus");
            return result;
        }
    }

    @Override
    public void connect(String from) {
        if (sExecutionService != null) {
            sExecutionService.connect();
            pumpDescription.basalStep = DanaRPump.getInstance().basalStep;
            pumpDescription.bolusStep = DanaRPump.getInstance().bolusStep;
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
            pumpDescription.basalStep = DanaRPump.getInstance().basalStep;
            pumpDescription.bolusStep = DanaRPump.getInstance().bolusStep;
        }
    }

    @Override
    public JSONObject getJSONStatus(Profile profile, String profilename) {
        DanaRPump pump = DanaRPump.getInstance();
        long now = System.currentTimeMillis();
        if (pump.lastConnection + 5 * 60 * 1000L < System.currentTimeMillis()) {
            return null;
        }
        JSONObject pumpjson = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", pump.batteryRemaining);
            status.put("status", pump.pumpSuspended ? "suspended" : "normal");
            status.put("timestamp", DateUtil.toISOString(pump.lastConnection));
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            extended.put("PumpIOB", pump.iob);
            if (pump.lastBolusTime != 0) {
                extended.put("LastBolus", DateUtil.dateAndTimeFullString(pump.lastBolusTime));
                extended.put("LastBolusAmount", pump.lastBolusAmount);
            }
            TemporaryBasal tb = TreatmentsPlugin.getPlugin().getRealTempBasalFromHistory(now);
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.tempBasalConvertedToAbsolute(now, profile));
                extended.put("TempBasalStart", DateUtil.dateAndTimeString(tb.date));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }
            ExtendedBolus eb = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(now);
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
            pumpjson.put("reservoir", (int) pump.reservoirRemainingUnits);
            pumpjson.put("clock", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return pumpjson;
    }

    @Override
    public ManufacturerType manufacturer() {
        return ManufacturerType.Sooil;
    }

    @Override
    public String serialNumber() {
        return DanaRPump.getInstance().serialNumber;
    }

    @Override
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

    @Override
    public Constraint<Double> applyBasalConstraints(Constraint<Double> absoluteRate, Profile profile) {
        absoluteRate.setIfSmaller(DanaRPump.getInstance().maxBasal, String.format(MainApp.gs(R.string.limitingbasalratio), DanaRPump.getInstance().maxBasal, MainApp.gs(R.string.pumplimit)), this);
        return absoluteRate;
    }

    @Override
    public Constraint<Integer> applyBasalPercentConstraints(Constraint<Integer> percentRate, Profile profile) {
        percentRate.setIfGreater(0, String.format(MainApp.gs(R.string.limitingpercentrate), 0, MainApp.gs(R.string.itmustbepositivevalue)), this);
        percentRate.setIfSmaller(getPumpDescription().maxTempPercent, String.format(MainApp.gs(R.string.limitingpercentrate), getPumpDescription().maxTempPercent, MainApp.gs(R.string.pumplimit)), this);

        return percentRate;
    }

    @Override
    public Constraint<Double> applyBolusConstraints(Constraint<Double> insulin) {
        insulin.setIfSmaller(DanaRPump.getInstance().maxBolus, String.format(MainApp.gs(R.string.limitingbolus), DanaRPump.getInstance().maxBolus, MainApp.gs(R.string.pumplimit)), this);
        return insulin;
    }

    @Override
    public Constraint<Double> applyExtendedBolusConstraints(Constraint<Double> insulin) {
        return applyBolusConstraints(insulin);
    }

    @Nullable
    @Override
    public ProfileStore getProfile() {
        if (DanaRPump.getInstance().lastSettingsRead == 0)
            return null; // no info now
        return DanaRPump.getInstance().createConvertedProfile();
    }

    @Override
    public String getUnits() {
        return DanaRPump.getInstance().getUnits();
    }

    @Override
    public String getProfileName() {
        return DanaRPump.getInstance().createConvertedProfileName();
    }

    @Override
    public PumpEnactResult loadTDDs() {
        return loadHistory(RecordTypes.RECORD_TYPE_DAILY);
    }

    // Reply for sms communicator
    public String shortStatus(boolean veryShort) {
        DanaRPump pump = DanaRPump.getInstance();
        String ret = "";
        if (pump.lastConnection != 0) {
            Long agoMsec = System.currentTimeMillis() - pump.lastConnection;
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += "LastConn: " + agoMin + " minago\n";
        }
        if (pump.lastBolusTime != 0) {
            ret += "LastBolus: " + DecimalFormatter.to2Decimal(pump.lastBolusAmount) + "U @" + android.text.format.DateFormat.format("HH:mm", pump.lastBolusTime) + "\n";
        }
        TemporaryBasal activeTemp = TreatmentsPlugin.getPlugin().getRealTempBasalFromHistory(System.currentTimeMillis());
        if (activeTemp != null) {
            ret += "Temp: " + activeTemp.toStringFull() + "\n";
        }
        ExtendedBolus activeExtendedBolus = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(System.currentTimeMillis());
        if (activeExtendedBolus != null) {
            ret += "Extended: " + activeExtendedBolus.toString() + "\n";
        }
        if (!veryShort) {
            ret += "TDD: " + DecimalFormatter.to0Decimal(pump.dailyTotalUnits) + " / " + pump.maxDailyTotalUnits + " U\n";
        }
        ret += "Reserv: " + DecimalFormatter.to0Decimal(pump.reservoirRemainingUnits) + "U\n";
        ret += "Batt: " + pump.batteryRemaining + "\n";
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
    public void timeDateOrTimeZoneChanged() {

    }


}
