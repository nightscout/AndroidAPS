package info.nightscout.androidaps.plugins.DanaR;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;
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
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.ProfileInterface;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.DanaR.Services.ExecutionService;
import info.nightscout.androidaps.plugins.NSProfile.NSProfilePlugin;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.Round;

/**
 * Created by mike on 05.08.2016.
 */
public class DanaRPlugin implements PluginBase, PumpInterface, ConstraintsInterface, ProfileInterface {
    private static Logger log = LoggerFactory.getLogger(DanaRPlugin.class);

    @Override
    public String getFragmentClass() {
        return DanaRFragment.class.getName();
    }

    static boolean fragmentPumpEnabled = true;
    static boolean fragmentProfileEnabled = true;
    static boolean fragmentPumpVisible = true;

    public static ExecutionService sExecutionService;


    private static DanaRPump sDanaRPump = new DanaRPump();
    private static boolean useExtendedBoluses = false;

    public static PumpDescription pumpDescription = new PumpDescription();

    public static DanaRPump getDanaRPump() {
        return sDanaRPump;
    }

    public DanaRPlugin() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        useExtendedBoluses = sharedPreferences.getBoolean("danar_useextended", false);

        Context context = MainApp.instance().getApplicationContext();
        Intent intent = new Intent(context, ExecutionService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        MainApp.bus().register(this);

        pumpDescription.isBolusCapable = true; // TODO: use description in setTempBasalAbsolute
        pumpDescription.bolusStep = 0.05d;

        pumpDescription.isExtendedBolusCapable = true;
        pumpDescription.extendedBolusStep = 0.05d;

        pumpDescription.isTempBasalCapable = true;
        pumpDescription.lowTempBasalStyle = PumpDescription.PERCENT;
        pumpDescription.highTempBasalStyle = useExtendedBoluses ? PumpDescription.EXTENDED : PumpDescription.PERCENT;
        pumpDescription.maxHighTempPercent = 200;
        pumpDescription.maxHighTempAbsolute = 0;
        pumpDescription.lowTempPercentStep = 10;
        pumpDescription.lowTempAbsoluteStep = 0;
        pumpDescription.lowTempPercentDuration = 60;
        pumpDescription.lowTempAbsoluteDuration = 60;
        pumpDescription.highTempPercentStep = 10;
        pumpDescription.highTempAbsoluteStep = 0.05d;
        pumpDescription.highTempPercentDuration = 60;
        pumpDescription.highTempAbsoluteDuration = 30;

        pumpDescription.isSetBasalProfileCapable = true;
        pumpDescription.basalStep = 0.01d;
        pumpDescription.basalMinimumRate = 0.04d;

        pumpDescription.isRefillingCapable = true;
    }

    ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            log.debug("Service is disconnected");
            sExecutionService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            log.debug("Service is connected");
            ExecutionService.LocalBinder mLocalBinder = (ExecutionService.LocalBinder) service;
            sExecutionService = mLocalBinder.getServiceInstance();
        }
    };

    @SuppressWarnings("UnusedParameters")
    @Subscribe
    public void onStatusEvent(final EventAppExit e) {
        MainApp.instance().getApplicationContext().unbindService(mConnection);
    }

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange s) {
        if (isEnabled(PUMP)) {
            boolean previousValue = useExtendedBoluses;
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
            useExtendedBoluses = sharedPreferences.getBoolean("danar_useextended", false);

            pumpDescription.highTempBasalStyle = useExtendedBoluses ? PumpDescription.EXTENDED : PumpDescription.PERCENT;

            if (useExtendedBoluses != previousValue && isExtendedBoluslInProgress()) {
                sExecutionService.extendedBolusStop();
            }
        }
    }

    // Plugin base interface
    @Override
    public int getType() {
        return PluginBase.PUMP;
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.danarpump);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.danarpump_shortname);
        if (!name.trim().isEmpty()){
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
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == PluginBase.PROFILE)
            this.fragmentProfileEnabled = fragmentEnabled;
        else if (type == PluginBase.PUMP)
            this.fragmentPumpEnabled = fragmentEnabled;
        // if pump profile was enabled need to switch to another too
        if (type == PluginBase.PUMP && !fragmentEnabled && this.fragmentProfileEnabled) {
            setFragmentEnabled(PluginBase.PROFILE, false);
            setFragmentVisible(PluginBase.PROFILE, false);
            MainApp.getSpecificPlugin(NSProfilePlugin.class).setFragmentEnabled(PluginBase.PROFILE, true);
            MainApp.getSpecificPlugin(NSProfilePlugin.class).setFragmentVisible(PluginBase.PROFILE, true);
        }
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == PluginBase.PUMP)
            this.fragmentPumpVisible = fragmentVisible;
    }

    @Override
    public boolean isInitialized() {
        return getDanaRPump().lastConnection.getTime() > 0 && getDanaRPump().isExtendedBolusEnabled;
    }

    @Override
    public boolean isSuspended() {
        return getDanaRPump().pumpSuspended;
    }

    @Override
    public boolean isBusy() {
        if (sExecutionService == null) return false;
        return sExecutionService.isConnected() || sExecutionService.isConnecting();
    }

    // Pump interface
    @Override
    public boolean isTempBasalInProgress() {
        if (getRealTempBasal() != null) return true;
        if (getExtendedBolus() != null && useExtendedBoluses) return true;
        return false;
    }

    public boolean isRealTempBasalInProgress() {
        return getRealTempBasal() != null; //TODO:  crosscheck here
    }

    @Override
    public boolean isExtendedBoluslInProgress() {
        return getExtendedBolus() != null; //TODO:  crosscheck here
    }

    @Override
    public int setNewBasalProfile(NSProfile profile) {
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
    public boolean isThisProfileSet(NSProfile profile) {
        if (!isInitialized())
            return true; // TODO: not sure what's better. so far TRUE to prevent too many SMS
        DanaRPump pump = getDanaRPump();
        if (pump.pumpProfiles == null)
            return true; // TODO: not sure what's better. so far TRUE to prevent too many SMS
        int basalValues = pump.basal48Enable ? 48 : 24;
        int basalIncrement = pump.basal48Enable ? 30 * 60 : 60 * 60;
        for (int h = 0; h < basalValues; h++) {
            Double pumpValue = pump.pumpProfiles[pump.activeProfile][h];
            Double profileValue = profile.getBasal(h * basalIncrement);
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
        return getDanaRPump().lastConnection;
    }

    @Override
    public void refreshDataFromPump(String reason) {
        if (!isConnected() && !isConnecting()) {
            doConnect(reason);
        }
    }

    @Override
    public double getBaseBasalRate() {
        return getDanaRPump().currentBasal;
    }

    @Override
    public double getTempBasalAbsoluteRate() {
        TempBasal tb = getRealTempBasal();
        if (tb != null) {
            if (tb.isAbsolute) {
                return tb.absolute;
            } else {
                Double baseRate = getBaseBasalRate();
                Double tempRate = baseRate * (tb.percent / 100d);
                return tempRate;
            }
        }
        TempBasal eb = getExtendedBolus();
        if (eb != null && useExtendedBoluses) {
            return getBaseBasalRate() + eb.absolute;
        }
        return 0;
    }

    @Override
    public double getTempBasalRemainingMinutes() {
        if (isRealTempBasalInProgress())
            return getRealTempBasal().getPlannedRemainingMinutes();
        if (isExtendedBoluslInProgress() && useExtendedBoluses)
            return getExtendedBolus().getPlannedRemainingMinutes();
        return 0;
    }

    @Override
    public TempBasal getTempBasal() {
        if (isRealTempBasalInProgress())
            return getRealTempBasal();
        if (isExtendedBoluslInProgress() && useExtendedBoluses)
            return getExtendedBolus();
        return null;
    }

    public TempBasal getTempBasal(Date time) {
        TempBasal temp = MainApp.getConfigBuilder().getActiveTempBasals().getTempBasal(time);
        if (temp != null) return temp;
        if (useExtendedBoluses)
            return MainApp.getConfigBuilder().getActiveTempBasals().getExtendedBolus(time);
        return null;
    }

    public TempBasal getRealTempBasal() {
        return MainApp.getConfigBuilder().getActiveTempBasals().getTempBasal(new Date());
    }

    @Override
    public TempBasal getExtendedBolus() {
        return MainApp.getConfigBuilder().getActiveTempBasals().getExtendedBolus(new Date());
    }

    @Override
    public PumpEnactResult deliverTreatment(Double insulin, Integer carbs, Context context) {
        ConfigBuilderPlugin configBuilderPlugin = MainApp.getConfigBuilder();
        insulin = configBuilderPlugin.applyBolusConstraints(insulin);
        if (insulin > 0 || carbs > 0) {
            Treatment t = new Treatment();
            boolean connectionOK = false;
            if (insulin > 0 || carbs > 0) connectionOK = sExecutionService.bolus(insulin, carbs, t);
            PumpEnactResult result = new PumpEnactResult();
            result.success = connectionOK;
            result.bolusDelivered = t.insulin;
            result.carbsDelivered = carbs;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            if (Config.logPumpActions)
                log.debug("deliverTreatment: OK. Asked: " + insulin + " Delivered: " + result.bolusDelivered);
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult();
            result.success = false;
            result.bolusDelivered = 0d;
            result.carbsDelivered = 0;
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
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes) {
        // Recheck pump status if older than 30 min
        if (getDanaRPump().lastConnection.getTime() + 30 * 60 * 1000L < new Date().getTime()) {
            doConnect("setTempBasalAbsolute old data");
        }

        PumpEnactResult result = new PumpEnactResult();

        ConfigBuilderPlugin configBuilderPlugin = MainApp.getConfigBuilder();
        absoluteRate = configBuilderPlugin.applyBasalConstraints(absoluteRate);

        final boolean doTempOff = getBaseBasalRate() - absoluteRate == 0d;
        final boolean doLowTemp = absoluteRate < getBaseBasalRate();
        final boolean doHighTemp = absoluteRate > getBaseBasalRate() && !useExtendedBoluses;
        final boolean doExtendedTemp = absoluteRate > getBaseBasalRate() && useExtendedBoluses;

        if (doTempOff) {
            // If extended in progress
            if (isExtendedBoluslInProgress() && useExtendedBoluses) {
                if (Config.logPumpActions)
                    log.debug("setTempBasalAbsolute: Stopping extended bolus (doTempOff)");
                return cancelExtendedBolus();
            }
            // If temp in progress
            if (isRealTempBasalInProgress()) {
                if (Config.logPumpActions)
                    log.debug("setTempBasalAbsolute: Stopping temp basal (doTempOff)");
                return cancelRealTempBasal();
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
            if (percentRate > 200) {
                percentRate = 200;
            }
            // If extended in progress
            if (isExtendedBoluslInProgress() && useExtendedBoluses) {
                if (Config.logPumpActions)
                    log.debug("setTempBasalAbsolute: Stopping extended bolus (doLowTemp || doHighTemp)");
                result = cancelExtendedBolus();
                if (!result.success) {
                    log.error("setTempBasalAbsolute: Failed to stop previous extended bolus (doLowTemp || doHighTemp)");
                    return result;
                }
            }
            // Check if some temp is already in progress
            if (isRealTempBasalInProgress()) {
                // Correct basal already set ?
                if (getRealTempBasal().percent == percentRate) {
                    result.success = true;
                    result.percent = percentRate;
                    result.absolute = getTempBasalAbsoluteRate();
                    result.enacted = false;
                    result.duration = ((Double) getTempBasalRemainingMinutes()).intValue();
                    result.isPercent = true;
                    result.isTempCancel = false;
                    if (Config.logPumpActions)
                        log.debug("setTempBasalAbsolute: Correct temp basal already set (doLowTemp || doHighTemp)");
                    return result;
                } else {
                    if (Config.logPumpActions)
                        log.debug("setTempBasalAbsolute: Stopping temp basal (doLowTemp || doHighTemp)");
                    result = cancelRealTempBasal();
                    // Check for proper result
                    if (!result.success) {
                        log.error("setTempBasalAbsolute: Failed to stop previous temp basal (doLowTemp || doHighTemp)");
                        return result;
                    }
                }
            }
            // Convert duration from minutes to hours
            if (Config.logPumpActions)
                log.debug("setTempBasalAbsolute: Setting temp basal " + percentRate + "% for " + durationInMinutes + " mins (doLowTemp || doHighTemp)");
            return setTempBasalPercent(percentRate, durationInMinutes);
        }
        if (doExtendedTemp) {
            // Check if some temp is already in progress
            if (isRealTempBasalInProgress()) {
                if (Config.logPumpActions)
                    log.debug("setTempBasalAbsolute: Stopping temp basal (doExtendedTemp)");
                result = cancelRealTempBasal();
                // Check for proper result
                if (!result.success) {
                    log.error("setTempBasalAbsolute: Failed to stop previous temp basal (doExtendedTemp)");
                    return result;
                }
            }

            // Calculate # of halfHours from minutes
            Integer durationInHalfHours = Math.max(durationInMinutes / 30, 1);
            // We keep current basal running so need to sub current basal
            Double extendedRateToSet = absoluteRate - getBaseBasalRate();
            extendedRateToSet = configBuilderPlugin.applyBasalConstraints(extendedRateToSet);
            // needs to be rounded to 0.1
            extendedRateToSet = Round.roundTo(extendedRateToSet, 0.1d);

            // What is current rate of extended bolusing in u/h?
            if (Config.logPumpActions) {
                log.debug("setTempBasalAbsolute: Extended bolus in progress: " + isExtendedBoluslInProgress() + " rate: " + getDanaRPump().extendedBolusAbsoluteRate + "U/h duration remaining: " + getDanaRPump().extendedBolusRemainingMinutes + "min");
                log.debug("setTempBasalAbsolute: Rate to set: " + extendedRateToSet + "U/h");
            }

            // Compare with extended rate in progress
            if (isExtendedBoluslInProgress() && Math.abs(getDanaRPump().extendedBolusAbsoluteRate - extendedRateToSet) < getPumpDescription().extendedBolusStep) {
                // correct extended already set
                result.success = true;
                result.absolute = getDanaRPump().extendedBolusAbsoluteRate;
                result.enacted = false;
                result.duration = getDanaRPump().extendedBolusRemainingMinutes;
                result.isPercent = false;
                result.isTempCancel = false;
                if (Config.logPumpActions)
                    log.debug("setTempBasalAbsolute: Correct extended already set");
                return result;
            }

            // Now set new extended, no need to to stop previous (if running) because it's replaced
            Double extendedAmount = extendedRateToSet / 2 * durationInHalfHours;
            if (Config.logPumpActions)
                log.debug("setTempBasalAbsolute: Setting extended: " + extendedAmount + "U  halfhours: " + durationInHalfHours);
            result = setExtendedBolus(extendedAmount, durationInMinutes);
            if (!result.success) {
                log.error("setTempBasalAbsolute: Failed to set extended bolus");
                return result;
            }
            if (Config.logPumpActions)
                log.debug("setTempBasalAbsolute: Extended bolus set ok");
            result.absolute = result.absolute + getBaseBasalRate();
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
        if (percent > 200) percent = 200;
        if (getDanaRPump().isTempBasalInProgress && getDanaRPump().tempBasalPercent == percent) {
            result.enacted = false;
            result.success = true;
            result.isTempCancel = false;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            result.duration = getDanaRPump().tempBasalRemainingMin;
            result.percent = getDanaRPump().tempBasalPercent;
            result.isPercent = true;
            if (Config.logPumpActions)
                log.debug("setTempBasalPercent: Correct value already set");
            return result;
        }
        int durationInHours = Math.max(durationInMinutes / 60, 1);
        boolean connectionOK = sExecutionService.tempBasal(percent, durationInHours);
        if (connectionOK && getDanaRPump().isTempBasalInProgress && getDanaRPump().tempBasalPercent == percent) {
            result.enacted = true;
            result.success = true;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            result.isTempCancel = false;
            result.duration = getDanaRPump().tempBasalRemainingMin;
            result.percent = getDanaRPump().tempBasalPercent;
            result.isPercent = true;
            if (Config.logPumpActions)
                log.debug("setTempBasalPercent: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = MainApp.instance().getString(R.string.danar_valuenotsetproperly);
        log.error("setTempBasalPercent: Failed to set temp basal");
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        ConfigBuilderPlugin configBuilderPlugin = MainApp.getConfigBuilder();
        insulin = configBuilderPlugin.applyBolusConstraints(insulin);
        // needs to be rounded
        insulin = Round.roundTo(insulin, getPumpDescription().extendedBolusStep);

        PumpEnactResult result = new PumpEnactResult();
        if (getDanaRPump().isExtendedInProgress && Math.abs(getDanaRPump().extendedBolusAmount - insulin) < getPumpDescription().extendedBolusStep) {
            result.enacted = false;
            result.success = true;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            result.duration = getDanaRPump().extendedBolusRemainingMinutes;
            result.absolute = getDanaRPump().extendedBolusAbsoluteRate;
            result.isPercent = false;
            result.isTempCancel = false;
            if (Config.logPumpActions)
                log.debug("setExtendedBolus: Correct extended bolus already set. Current: " + getDanaRPump().extendedBolusAmount + " Asked: " + insulin);
            return result;
        }
        int durationInHalfHours = Math.max(durationInMinutes / 30, 1);
        boolean connectionOK = sExecutionService.extendedBolus(insulin, durationInHalfHours);
        if (connectionOK && getDanaRPump().isExtendedInProgress && Math.abs(getDanaRPump().extendedBolusAmount - insulin) < getPumpDescription().extendedBolusStep) {
            result.enacted = true;
            result.success = true;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            result.isTempCancel = false;
            result.duration = getDanaRPump().extendedBolusRemainingMinutes;
            result.absolute = getDanaRPump().extendedBolusAbsoluteRate;
            result.bolusDelivered = getDanaRPump().extendedBolusAmount;
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
    public PumpEnactResult cancelTempBasal() {
        if (isRealTempBasalInProgress())
            return cancelRealTempBasal();
        if (isExtendedBoluslInProgress())
            return cancelExtendedBolus();
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.enacted = false;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        result.isTempCancel = true;
        return result;
    }

    public PumpEnactResult cancelRealTempBasal() {
        PumpEnactResult result = new PumpEnactResult();
        if (getDanaRPump().isTempBasalInProgress) {
            sExecutionService.tempBasalStop();
            result.enacted = true;
            result.isTempCancel = true;
        }
        if (!getDanaRPump().isTempBasalInProgress) {
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
        if (getDanaRPump().isExtendedInProgress) {
            sExecutionService.extendedBolusStop();
            result.enacted = true;
            result.isTempCancel = true;
        }
        if (!getDanaRPump().isExtendedInProgress) {
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
        if (sExecutionService != null) sExecutionService.connect(from);
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
        if (getDanaRPump().lastConnection.getTime() + 5 * 60 * 1000L < new Date().getTime()) {
            return null;
        }
        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", getDanaRPump().batteryRemaining);
            status.put("status", getDanaRPump().pumpSuspended ? "suspended" : "normal");
            status.put("timestamp", DateUtil.toISOString(getDanaRPump().lastConnection));
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            extended.put("PumpIOB", getDanaRPump().iob);
            extended.put("LastBolus", getDanaRPump().lastBolusTime.toLocaleString());
            extended.put("LastBolusAmount", getDanaRPump().lastBolusAmount);
            TempBasal tb = getTempBasal();
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", getTempBasalAbsoluteRate());
                extended.put("TempBasalStart", tb.timeStart.toLocaleString());
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
                extended.put("IsExtended", tb.isExtended);
            }
            extended.put("BaseBasalRate", getBaseBasalRate());
            try {
                extended.put("ActiveProfile", MainApp.getConfigBuilder().getActiveProfile().getProfile().getActiveProfile());
            } catch (Exception e) {
            }

            pump.put("battery", battery);
            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("reservoir", (int) getDanaRPump().reservoirRemainingUnits);
            pump.put("clock", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return pump;
    }

    @Override
    public String deviceID() {
        return getDanaRPump().serialNumber;
    }

    @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
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
        if (getDanaRPump() != null) {
            if (absoluteRate > getDanaRPump().maxBasal) {
                absoluteRate = getDanaRPump().maxBasal;
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
        if (percentRate > 200) percentRate = 200;
        if (!Objects.equals(percentRate, origPercentRate) && Config.logConstraintsChanges && !Objects.equals(origPercentRate, Constants.basalPercentOnlyForCheckLimit))
            log.debug("Limiting percent rate " + origPercentRate + "% to " + percentRate + "%");
        return percentRate;
    }

    @SuppressWarnings("PointlessBooleanExpression")
    @Override
    public Double applyBolusConstraints(Double insulin) {
        double origInsulin = insulin;
        if (getDanaRPump() != null) {
            if (insulin > getDanaRPump().maxBolus) {
                insulin = getDanaRPump().maxBolus;
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
    public NSProfile getProfile() {
        DanaRPump pump = getDanaRPump();
        if (pump.lastSettingsRead.getTime() == 0)
            return null; // no info now
        return pump.createConvertedProfile();
    }

    // Reply for sms communicator
    public String shortStatus(boolean veryShort) {
        String ret = "";
        if (getDanaRPump().lastConnection.getTime() != 0) {
            Long agoMsec = new Date().getTime() - getDanaRPump().lastConnection.getTime();
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += "LastConn: " + agoMin + " minago\n";
        }
        if (getDanaRPump().lastBolusTime.getTime() != 0) {
            ret += "LastBolus: " + DecimalFormatter.to2Decimal(getDanaRPump().lastBolusAmount) + "U @" + android.text.format.DateFormat.format("HH:mm", getDanaRPump().lastBolusTime) + "\n";
        }
        if (isRealTempBasalInProgress()) {
            ret += "Temp: " + getRealTempBasal().toString() + "\n";
        }
        if (isExtendedBoluslInProgress()) {
            ret += "Extended: " + getExtendedBolus().toString() + "\n";
        }
        if (!veryShort){
            ret += "TDD: " + DecimalFormatter.to0Decimal(getDanaRPump().dailyTotalUnits) + " / " + getDanaRPump().maxDailyTotalUnits + " U\n";
        }
        ret += "IOB: " + getDanaRPump().iob + "U\n";
        ret += "Reserv: " + DecimalFormatter.to0Decimal(getDanaRPump().reservoirRemainingUnits) + "U\n";
        ret += "Batt: " + getDanaRPump().batteryRemaining + "\n";
        return ret;
    }
    // TODO: daily total constraint

}
