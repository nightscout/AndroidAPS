package info.nightscout.androidaps.plugins.PumpDanaRv2;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Objects;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.Constants;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.DanaRInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.ConfigBuilder.DetailedBolusInfoStorage;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.ProfileNS.NSProfilePlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRFragment;
import info.nightscout.androidaps.plugins.PumpDanaR.DanaRPump;
import info.nightscout.androidaps.plugins.PumpDanaRv2.services.DanaRv2ExecutionService;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.Round;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class DanaRv2Plugin implements PluginBase, PumpInterface, DanaRInterface, ConstraintsInterface, ProfileInterface {
    private static Logger log = LoggerFactory.getLogger(DanaRv2Plugin.class);

    @Override
    public String getFragmentClass() {
        return DanaRFragment.class.getName();
    }

    private boolean fragmentPumpEnabled = false;
    private boolean fragmentProfileEnabled = false;
    private boolean fragmentPumpVisible = false;

    private static DanaRv2ExecutionService sExecutionService;


    private static DanaRv2Plugin plugin = null;

    public static DanaRv2Plugin getPlugin() {
        if (plugin == null)
            plugin = new DanaRv2Plugin();
        return plugin;
    }

    private static DanaRPump pump = DanaRPump.getInstance();

    private static PumpDescription pumpDescription = new PumpDescription();

    private DanaRv2Plugin() {
        Context context = MainApp.instance().getApplicationContext();
        Intent intent = new Intent(context, DanaRv2ExecutionService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        MainApp.bus().register(this);

        pumpDescription.isBolusCapable = true;
        pumpDescription.bolusStep = 0.05d;

        pumpDescription.isExtendedBolusCapable = true;
        pumpDescription.extendedBolusStep = 0.05d;
        pumpDescription.extendedBolusDurationStep = 30;
        pumpDescription.extendedBolusMaxDuration = 8 * 60;

        pumpDescription.isTempBasalCapable = true;
        pumpDescription.tempBasalStyle = PumpDescription.PERCENT;

        pumpDescription.maxTempPercent = 200;
        pumpDescription.tempPercentStep = 10;

        pumpDescription.tempDurationStep = 60;
        pumpDescription.tempMaxDuration = 24 * 60;


        pumpDescription.isSetBasalProfileCapable = true;
        pumpDescription.basalStep = 0.01d;
        pumpDescription.basalMinimumRate = 0.04d;

        pumpDescription.isRefillingCapable = true;
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            log.debug("Service is disconnected");
            sExecutionService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            log.debug("Service is connected");
            DanaRv2ExecutionService.LocalBinder mLocalBinder = (DanaRv2ExecutionService.LocalBinder) service;
            sExecutionService = mLocalBinder.getServiceInstance();
        }
    };

    @SuppressWarnings("UnusedParameters")
    @Subscribe
    public void onStatusEvent(final EventAppExit e) {
        MainApp.instance().getApplicationContext().unbindService(mConnection);
    }

    // Plugin base interface
    @Override
    public int getType() {
        return PluginBase.PUMP;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.danarv2pump);
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
        if (type == PluginBase.PROFILE) return fragmentProfileEnabled && fragmentPumpEnabled;
        else if (type == PluginBase.PUMP) return fragmentPumpEnabled;
        else if (type == PluginBase.CONSTRAINTS) return fragmentPumpEnabled;
        return false;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        if (type == PluginBase.PROFILE || type == PluginBase.CONSTRAINTS) return false;
        else if (type == PluginBase.PUMP) return fragmentPumpVisible;
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
            this.fragmentProfileEnabled = fragmentEnabled;
        else if (type == PluginBase.PUMP)
            this.fragmentPumpEnabled = fragmentEnabled;
        // if pump profile was enabled need to switch to another too
        if (type == PluginBase.PUMP && !fragmentEnabled && this.fragmentProfileEnabled) {
            setFragmentEnabled(PluginBase.PROFILE, false);
            setFragmentVisible(PluginBase.PROFILE, false);
            NSProfilePlugin.getPlugin().setFragmentEnabled(PluginBase.PROFILE, true);
            NSProfilePlugin.getPlugin().setFragmentVisible(PluginBase.PROFILE, true);
        }
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == PluginBase.PUMP)
            this.fragmentPumpVisible = fragmentVisible;
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return pump.lastConnection.getTime() > 0;
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
    public int setNewBasalProfile(Profile profile) {
        if (sExecutionService == null) {
            log.error("setNewBasalProfile sExecutionService is null");
            return FAILED;
        }
        if (!isInitialized()) {
            log.error("setNewBasalProfile not initialized");
            Notification notification = new Notification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED, MainApp.sResources.getString(R.string.pumpNotInitializedProfileNotSet), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
            return FAILED;
        } else {
            MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
        }
        if (!sExecutionService.updateBasalsInPump(profile)) {
            Notification notification = new Notification(Notification.FAILED_UDPATE_PROFILE, MainApp.sResources.getString(R.string.failedupdatebasalprofile), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
            return FAILED;
        } else {
            MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
            MainApp.bus().post(new EventDismissNotification(Notification.FAILED_UDPATE_PROFILE));
            return SUCCESS;
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
        return pump.lastConnection;
    }

    @Override
    public void refreshDataFromPump(String reason) {
        if (!isConnected() && !isConnecting()) {
            doConnect(reason);
        }
    }

    @Override
    public double getBaseBasalRate() {
        return pump.currentBasal;
    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        ConfigBuilderPlugin configBuilderPlugin = MainApp.getConfigBuilder();
        detailedBolusInfo.insulin = configBuilderPlugin.applyBolusConstraints(detailedBolusInfo.insulin);
        if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
            DetailedBolusInfoStorage.add(detailedBolusInfo); // will be picked up on reading history
            // v2 stores end time for bolus, we need to adjust time
            // default delivery speed is 12 U/min
            int preferencesSpeed = SP.getInt(R.string.key_danars_bolusspeed, 0);
            int speed = 12;
            switch (preferencesSpeed) {
                case 0:
                    speed = 12;
                    break;
                case 1:
                    speed = 30;
                    break;
                case 2:
                    speed = 60;
                    break;
            }
            detailedBolusInfo.date += detailedBolusInfo.insulin / speed * 60d * 1000;
            // clean carbs to prevent counting them as twice because they will picked up as another record
            // I don't think it's necessary to copy DetailedBolusInfo right now for carbs records
            double carbs = detailedBolusInfo.carbs;
            detailedBolusInfo.carbs = 0;
            int carbTime = detailedBolusInfo.carbTime;
            detailedBolusInfo.carbTime = 0;

            Treatment t = new Treatment();
            boolean connectionOK = false;
            if (detailedBolusInfo.insulin > 0 || carbs > 0)
                connectionOK = sExecutionService.bolus(detailedBolusInfo.insulin, (int) carbs, System.currentTimeMillis() + carbTime * 60 * 1000 + 1000, t); // +1000 to make the record different
            PumpEnactResult result = new PumpEnactResult();
            result.success = connectionOK;
            result.bolusDelivered = t.insulin;
            result.carbsDelivered = detailedBolusInfo.carbs;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            if (Config.logPumpActions)
                log.debug("deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered);
            // remove carbs because it's get from history seprately
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult();
            result.success = false;
            result.bolusDelivered = 0d;
            result.carbsDelivered = 0d;
            result.comment = MainApp.instance().getString(R.string.danar_invalidinput);
            log.error("deliverTreatment: Invalid input");
            return result;
        }
    }

    @Override
    public void stopBolusDelivering() {
        if (sExecutionService == null) {
            log.error("stopBolusDelivering sExecutionService is null");
            return;
        }
        sExecutionService.bolusStop();
    }

    // This is called from APS
    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, boolean enforceNew) {
        // Recheck pump status if older than 30 min
        if (pump.lastConnection.getTime() + 30 * 60 * 1000L < System.currentTimeMillis()) {
            doConnect("setTempBasalAbsolute old data");
        }

        PumpEnactResult result = new PumpEnactResult();

        ConfigBuilderPlugin configBuilderPlugin = MainApp.getConfigBuilder();
        absoluteRate = configBuilderPlugin.applyBasalConstraints(absoluteRate);

        final boolean doTempOff = getBaseBasalRate() - absoluteRate == 0d;
        final boolean doLowTemp = absoluteRate < getBaseBasalRate();
        final boolean doHighTemp = absoluteRate > getBaseBasalRate();

        if (doTempOff) {
            // If temp in progress
            if (MainApp.getConfigBuilder().isTempBasalInProgress()) {
                if (Config.logPumpActions)
                    log.debug("setTempBasalAbsolute: Stopping temp basal (doTempOff)");
                return cancelTempBasal(false);
            }
            result.success = true;
            result.enacted = false;
            result.percent = 100;
            result.isPercent = true;
            result.isTempCancel = true;
            if (Config.logPumpActions)
                log.debug("setTempBasalAbsolute: doTempOff OK");
            return result;
        }

        if (doLowTemp || doHighTemp) {
            Integer percentRate = Double.valueOf(absoluteRate / getBaseBasalRate() * 100).intValue();
            if (percentRate < 100) percentRate = Round.ceilTo((double) percentRate, 10d).intValue();
            else percentRate = Round.floorTo((double) percentRate, 10d).intValue();
            if (percentRate > 500) // Special high temp 500/15min
                percentRate = 500;
            // Check if some temp is already in progress
            if (MainApp.getConfigBuilder().isTempBasalInProgress()) {
                // Correct basal already set ?
                if (MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis()).percentRate == percentRate) {
                    if (!enforceNew) {
                        result.success = true;
                        result.percent = percentRate;
                        result.absolute = MainApp.getConfigBuilder().getTempBasalAbsoluteRateHistory();
                        result.enacted = false;
                        result.duration = ((Double) MainApp.getConfigBuilder().getTempBasalRemainingMinutesFromHistory()).intValue();
                        result.isPercent = true;
                        result.isTempCancel = false;
                        if (Config.logPumpActions)
                            log.debug("setTempBasalAbsolute: Correct temp basal already set (doLowTemp || doHighTemp)");
                        return result;
                    }
                }
            }
            // Convert duration from minutes to hours
            if (Config.logPumpActions)
                log.debug("setTempBasalAbsolute: Setting temp basal " + percentRate + "% for " + durationInMinutes + " mins (doLowTemp || doHighTemp)");
            // use special APS temp basal call ... 100+/15min .... 100-/30min
            result = setHighTempBasalPercent(percentRate);
            if (!result.success) {
                log.error("setTempBasalAbsolute: Failed to set hightemp basal");
                return result;
            }
            if (Config.logPumpActions)
                log.debug("setTempBasalAbsolute: hightemp basal set ok");
            return result;
        }
        // We should never end here
        log.error("setTempBasalAbsolute: Internal error");
        result.success = false;
        result.comment = "Internal error";
        return result;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes) {
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
        if (runningTB != null && runningTB.percentRate == percent) {
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

    public PumpEnactResult setHighTempBasalPercent(Integer percent) {
        PumpEnactResult result = new PumpEnactResult();
        boolean connectionOK = sExecutionService.highTempBasal(percent);
        if (connectionOK && pump.isTempBasalInProgress && pump.tempBasalPercent == percent) {
            result.enacted = true;
            result.success = true;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            result.isTempCancel = false;
            result.duration = pump.tempBasalRemainingMin;
            result.percent = pump.tempBasalPercent;
            result.isPercent = true;
            if (Config.logPumpActions)
                log.debug("setHighTempBasalPercent: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = MainApp.instance().getString(R.string.danar_valuenotsetproperly);
        log.error("setHighTempBasalPercent: Failed to set temp basal");
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        ConfigBuilderPlugin configBuilderPlugin = MainApp.getConfigBuilder();
        insulin = configBuilderPlugin.applyBolusConstraints(insulin);
        // needs to be rounded
        int durationInHalfHours = Math.max(durationInMinutes / 30, 1);
        insulin = Round.roundTo(insulin, getPumpDescription().extendedBolusStep * (1 + durationInHalfHours % 1));
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
    public PumpEnactResult cancelTempBasal(boolean force) {
        PumpEnactResult result = new PumpEnactResult();
        TemporaryBasal runningTB =  MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis());
        if (runningTB != null) {
            sExecutionService.tempBasalStop();
            result.enacted = true;
            result.isTempCancel = true;
        }
        if (!pump.isTempBasalInProgress) {
            result.success = true;
            result.isTempCancel = true;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            if (Config.logPumpActions)
                log.debug("cancelRealTempBasal: OK");
            return result;
        } else {
            result.success = false;
            result.comment = MainApp.instance().getString(R.string.danar_valuenotsetproperly);
            result.isTempCancel = true;
            log.error("cancelRealTempBasal: Failed to cancel temp basal");
            return result;
        }
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

    public static void doConnect(String from) {
        if (sExecutionService != null) {
            sExecutionService.connect(from);
            pumpDescription.basalStep = pump.basalStep;
            pumpDescription.bolusStep = pump.bolusStep;
        }
    }

    public static boolean isConnected() {
        return sExecutionService != null && sExecutionService.isConnected();
    }

    public static boolean isConnecting() {
        return sExecutionService != null && sExecutionService.isConnecting();
    }

    public static void doDisconnect(String from) {
        if (sExecutionService != null) sExecutionService.disconnect(from);
    }

    @Override
    public JSONObject getJSONStatus() {
        if (pump.lastConnection.getTime() + 5 * 60 * 1000L < System.currentTimeMillis()) {
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
            TemporaryBasal tb = MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis());
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
    public boolean loadHistory(byte type) {
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
        if (pump.lastSettingsRead.getTime() == 0)
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
        if (pump.lastConnection.getTime() != 0) {
            Long agoMsec = System.currentTimeMillis() - pump.lastConnection.getTime();
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += "LastConn: " + agoMin + " minago\n";
        }
        if (pump.lastBolusTime.getTime() != 0) {
            ret += "LastBolus: " + DecimalFormatter.to2Decimal(pump.lastBolusAmount) + "U @" + android.text.format.DateFormat.format("HH:mm", pump.lastBolusTime) + "\n";
        }
        if (MainApp.getConfigBuilder().isTempBasalInProgress()) {
            ret += "Temp: " + MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis()).toStringFull() + "\n";
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
