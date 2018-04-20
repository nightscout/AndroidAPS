package info.nightscout.androidaps.plugins.PumpDanaR;

import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.util.Date;
import java.util.Objects;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.DanaRInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.ProfileNS.NSProfilePlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.services.AbstractDanaRExecutionService;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.Round;

/**
 * Created by mike on 28.01.2018.
 */

public abstract class AbstractDanaRPlugin implements PluginBase, PumpInterface, DanaRInterface, ConstraintsInterface, ProfileInterface {
    protected Logger log;

    protected boolean mPluginPumpEnabled = false;
    protected boolean mPluginProfileEnabled = false;
    protected boolean mFragmentPumpVisible = true;

    protected AbstractDanaRExecutionService sExecutionService;

    protected DanaRPump pump = DanaRPump.getInstance();
    protected boolean useExtendedBoluses = false;

    public PumpDescription pumpDescription = new PumpDescription();

    @Override
    public String getFragmentClass() {
        return DanaRFragment.class.getName();
    }

    // Plugin base interface
    @Override
    public int getType() {
        return PluginBase.PUMP;
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.danarpump_shortname);
        if (!name.trim().isEmpty()) {
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    @Override
    public boolean isEnabled(int type) {
        if (type == PluginBase.PROFILE) return mPluginProfileEnabled && mPluginPumpEnabled;
        else if (type == PluginBase.PUMP) return mPluginPumpEnabled;
        else if (type == PluginBase.CONSTRAINTS) return mPluginPumpEnabled;
        return false;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        if (type == PluginBase.PROFILE || type == PluginBase.CONSTRAINTS) return false;
        else if (type == PluginBase.PUMP) return mFragmentPumpVisible;
        return false;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return type == PUMP;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == PluginBase.PROFILE)
            mPluginProfileEnabled = fragmentEnabled;
        else if (type == PluginBase.PUMP)
            mPluginPumpEnabled = fragmentEnabled;
        // if pump profile was enabled need to switch to another too
        if (type == PluginBase.PUMP && !fragmentEnabled && mPluginProfileEnabled) {
            setFragmentEnabled(PluginBase.PROFILE, false);
            setFragmentVisible(PluginBase.PROFILE, false);
            NSProfilePlugin.getPlugin().setFragmentEnabled(PluginBase.PROFILE, true);
            NSProfilePlugin.getPlugin().setFragmentVisible(PluginBase.PROFILE, true);
        }
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == PluginBase.PUMP)
            mFragmentPumpVisible = fragmentVisible;
    }

    @Override
    public boolean isSuspended() {
        return pump.pumpSuspended;
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
            Notification notification = new Notification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED, MainApp.sResources.getString(R.string.pumpNotInitializedProfileNotSet), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
            result.comment = MainApp.sResources.getString(R.string.pumpNotInitializedProfileNotSet);
            return result;
        } else {
            MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
        }
        if (!sExecutionService.updateBasalsInPump(profile)) {
            Notification notification = new Notification(Notification.FAILED_UDPATE_PROFILE, MainApp.sResources.getString(R.string.failedupdatebasalprofile), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
            result.comment = MainApp.sResources.getString(R.string.failedupdatebasalprofile);
            return result;
        } else {
            MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
            MainApp.bus().post(new EventDismissNotification(Notification.FAILED_UDPATE_PROFILE));
            Notification notification = new Notification(Notification.PROFILE_SET_OK, MainApp.sResources.getString(R.string.profile_set_ok), Notification.INFO, 60);
            MainApp.bus().post(new EventNewNotification(notification));
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
        if (pump.pumpProfiles == null)
            return true; // TODO: not sure what's better. so far TRUE to prevent too many SMS
        int basalValues = pump.basal48Enable ? 48 : 24;
        int basalIncrement = pump.basal48Enable ? 30 * 60 : 60 * 60;
        for (int h = 0; h < basalValues; h++) {
            Double pumpValue = pump.pumpProfiles[pump.activeProfile][h];
            Double profileValue = profile.getBasal((Integer) (h * basalIncrement));
            if (profileValue == null) return true;
            if (Math.abs(pumpValue - profileValue) > getPumpDescription().basalStep) {
                log.debug("Diff found. Hour: " + h + " Pump: " + pumpValue + " Profile: " + profileValue);
                return false;
            }
        }
        return true;
    }

    @Override
    public Date lastDataTime() {
        return new Date(pump.lastConnection);
    }

    @Override
    public double getBaseBasalRate() {
        return pump.currentBasal;
    }

    @Override
    public void stopBolusDelivering() {
        if (sExecutionService == null) {
            log.error("stopBolusDelivering sExecutionService is null");
            return;
        }
        sExecutionService.bolusStop();
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, boolean enforceNew) {
        PumpEnactResult result = new PumpEnactResult();
        ConfigBuilderPlugin configBuilderPlugin = MainApp.getConfigBuilder();
        percent = configBuilderPlugin.applyBasalConstraints(percent);
        if (percent < 0) {
            result.isTempCancel = false;
            result.enacted = false;
            result.success = false;
            result.comment = MainApp.instance().getString(R.string.danar_invalidinput);
            log.error("setTempBasalPercent: Invalid input");
            return result;
        }
        if (percent > getPumpDescription().maxTempPercent)
            percent = getPumpDescription().maxTempPercent;
        TemporaryBasal runningTB =  MainApp.getConfigBuilder().getRealTempBasalFromHistory(System.currentTimeMillis());
        if (runningTB != null && runningTB.percentRate == percent && !enforceNew) {
            result.enacted = false;
            result.success = true;
            result.isTempCancel = false;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            result.duration = pump.tempBasalRemainingMin;
            result.percent = pump.tempBasalPercent;
            result.absolute = MainApp.getConfigBuilder().getTempBasalAbsoluteRateHistory();
            result.isPercent = true;
            if (Config.logPumpActions)
                log.debug("setTempBasalPercent: Correct value already set");
            return result;
        }
        int durationInHours = Math.max(durationInMinutes / 60, 1);
        boolean connectionOK = sExecutionService.tempBasal(percent, durationInHours);
        if (connectionOK && pump.isTempBasalInProgress && pump.tempBasalPercent == percent) {
            result.enacted = true;
            result.success = true;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            result.isTempCancel = false;
            result.duration = pump.tempBasalRemainingMin;
            result.percent = pump.tempBasalPercent;
            result.absolute = MainApp.getConfigBuilder().getTempBasalAbsoluteRateHistory();
            result.isPercent = true;
            if (Config.logPumpActions)
                log.debug("setTempBasalPercent: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = MainApp.instance().getString(R.string.tempbasaldeliveryerror);
        log.error("setTempBasalPercent: Failed to set temp basal");
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        ConfigBuilderPlugin configBuilderPlugin = MainApp.getConfigBuilder();
        insulin = configBuilderPlugin.applyBolusConstraints(insulin);
        // needs to be rounded
        int durationInHalfHours = Math.max(durationInMinutes / 30, 1);
        insulin = Round.roundTo(insulin, getPumpDescription().extendedBolusStep);

        PumpEnactResult result = new PumpEnactResult();
        ExtendedBolus runningEB = MainApp.getConfigBuilder().getExtendedBolusFromHistory(System.currentTimeMillis());
        if (runningEB != null && Math.abs(runningEB.insulin - insulin) < getPumpDescription().extendedBolusStep) {
            result.enacted = false;
            result.success = true;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            result.duration = pump.extendedBolusRemainingMinutes;
            result.absolute = pump.extendedBolusAbsoluteRate;
            result.isPercent = false;
            result.isTempCancel = false;
            if (Config.logPumpActions)
                log.debug("setExtendedBolus: Correct extended bolus already set. Current: " + pump.extendedBolusAmount + " Asked: " + insulin);
            return result;
        }
        boolean connectionOK = sExecutionService.extendedBolus(insulin, durationInHalfHours);
        if (connectionOK && pump.isExtendedInProgress && Math.abs(pump.extendedBolusAmount - insulin) < getPumpDescription().extendedBolusStep) {
            result.enacted = true;
            result.success = true;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            result.isTempCancel = false;
            result.duration = pump.extendedBolusRemainingMinutes;
            result.absolute = pump.extendedBolusAbsoluteRate;
            result.bolusDelivered = pump.extendedBolusAmount;
            result.isPercent = false;
            if (Config.logPumpActions)
                log.debug("setExtendedBolus: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = MainApp.instance().getString(R.string.danar_valuenotsetproperly);
        log.error("setExtendedBolus: Failed to extended bolus");
        return result;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult();
        ExtendedBolus runningEB = MainApp.getConfigBuilder().getExtendedBolusFromHistory(System.currentTimeMillis());
        if (runningEB != null) {
            sExecutionService.extendedBolusStop();
            result.enacted = true;
            result.isTempCancel = true;
        }
        if (!pump.isExtendedInProgress) {
            result.success = true;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            if (Config.logPumpActions)
                log.debug("cancelExtendedBolus: OK");
            return result;
        } else {
            result.success = false;
            result.comment = MainApp.instance().getString(R.string.danar_valuenotsetproperly);
            log.error("cancelExtendedBolus: Failed to cancel extended bolus");
            return result;
        }
    }

    @Override
    public void connect(String from) {
        if (sExecutionService != null) {
            sExecutionService.connect();
            pumpDescription.basalStep = pump.basalStep;
            pumpDescription.bolusStep = pump.bolusStep;
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
        if (sExecutionService != null) sExecutionService.getPumpStatus();
    }

    @Override
    public JSONObject getJSONStatus() {
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
            if (pump.lastBolusTime.getTime() != 0) {
                extended.put("LastBolus", pump.lastBolusTime.toLocaleString());
                extended.put("LastBolusAmount", pump.lastBolusAmount);
            }
            TemporaryBasal tb = MainApp.getConfigBuilder().getRealTempBasalFromHistory(System.currentTimeMillis());
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.tempBasalConvertedToAbsolute(System.currentTimeMillis()));
                extended.put("TempBasalStart", DateUtil.dateAndTimeString(tb.date));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }
            ExtendedBolus eb = MainApp.getConfigBuilder().getExtendedBolusFromHistory(System.currentTimeMillis());
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.absoluteRate());
                extended.put("ExtendedBolusStart", DateUtil.dateAndTimeString(eb.date));
                extended.put("ExtendedBolusRemaining", eb.getPlannedRemainingMinutes());
            }
            extended.put("BaseBasalRate", getBaseBasalRate());
            try {
                extended.put("ActiveProfile", MainApp.getConfigBuilder().getProfileName());
            } catch (Exception e) {
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
    public String deviceID() {
        return pump.serialNumber;
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
    public boolean isLoopEnabled() {
        return true;
    }

    @Override
    public boolean isClosedModeEnabled() {
        return true;
    }

    @Override
    public boolean isAutosensModeEnabled() {
        return true;
    }

    @Override
    public boolean isAMAModeEnabled() {
        return true;
    }

    @Override
    public boolean isSMBModeEnabled() {
        return true;
    }

    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    public Double applyBasalConstraints(Double absoluteRate) {
        double origAbsoluteRate = absoluteRate;
        if (pump != null) {
            if (absoluteRate > pump.maxBasal) {
                absoluteRate = pump.maxBasal;
                if (Config.logConstraintsChanges && origAbsoluteRate != Constants.basalAbsoluteOnlyForCheckLimit)
                    log.debug("Limiting rate " + origAbsoluteRate + "U/h by pump constraint to " + absoluteRate + "U/h");
            }
        }
        return absoluteRate;
    }

    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    public Integer applyBasalConstraints(Integer percentRate) {
        Integer origPercentRate = percentRate;
        if (percentRate < 0) percentRate = 0;
        if (percentRate > getPumpDescription().maxTempPercent)
            percentRate = getPumpDescription().maxTempPercent;
        if (!Objects.equals(percentRate, origPercentRate) && Config.logConstraintsChanges && !Objects.equals(origPercentRate, Constants.basalPercentOnlyForCheckLimit))
            log.debug("Limiting percent rate " + origPercentRate + "% to " + percentRate + "%");
        return percentRate;
    }

    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    public Double applyBolusConstraints(Double insulin) {
        double origInsulin = insulin;
        if (pump != null) {
            if (insulin > pump.maxBolus) {
                insulin = pump.maxBolus;
                if (Config.logConstraintsChanges && origInsulin != Constants.bolusOnlyForCheckLimit)
                    log.debug("Limiting bolus " + origInsulin + "U by pump constraint to " + insulin + "U");
            }
        }
        return insulin;
    }

    @Override
    public Integer applyCarbsConstraints(Integer carbs) {
        return carbs;
    }

    @Override
    public Double applyMaxIOBConstraints(Double maxIob) {
        return maxIob;
    }

    @Nullable
    @Override
    public ProfileStore getProfile() {
        if (pump.lastSettingsRead == 0)
            return null; // no info now
        return pump.createConvertedProfile();
    }

    @Override
    public String getUnits() {
        return pump.getUnits();
    }

    @Override
    public String getProfileName() {
        return pump.createConvertedProfileName();
    }

    // Reply for sms communicator
    public String shortStatus(boolean veryShort) {
        String ret = "";
        if (pump.lastConnection != 0) {
            Long agoMsec = System.currentTimeMillis() - pump.lastConnection;
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += "LastConn: " + agoMin + " minago\n";
        }
        if (pump.lastBolusTime.getTime() != 0) {
            ret += "LastBolus: " + DecimalFormatter.to2Decimal(pump.lastBolusAmount) + "U @" + android.text.format.DateFormat.format("HH:mm", pump.lastBolusTime) + "\n";
        }
        if (MainApp.getConfigBuilder().isInHistoryRealTempBasalInProgress()) {
            ret += "Temp: " + MainApp.getConfigBuilder().getRealTempBasalFromHistory(System.currentTimeMillis()).toStringFull() + "\n";
        }
        if (MainApp.getConfigBuilder().isInHistoryExtendedBoluslInProgress()) {
            ret += "Extended: " + MainApp.getConfigBuilder().getExtendedBolusFromHistory(System.currentTimeMillis()).toString() + "\n";
        }
        if (!veryShort) {
            ret += "TDD: " + DecimalFormatter.to0Decimal(pump.dailyTotalUnits) + " / " + pump.maxDailyTotalUnits + " U\n";
        }
        ret += "IOB: " + pump.iob + "U\n";
        ret += "Reserv: " + DecimalFormatter.to0Decimal(pump.reservoirRemainingUnits) + "U\n";
        ret += "Batt: " + pump.batteryRemaining + "\n";
        return ret;
    }
    // TODO: daily total constraint

}
