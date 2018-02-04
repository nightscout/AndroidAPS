package info.nightscout.androidaps.plugins.PumpDanaRKorean;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.squareup.otto.Subscribe;

import org.slf4j.LoggerFactory;

import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpDanaR.AbstractDanaRPlugin;
import info.nightscout.androidaps.plugins.PumpDanaRKorean.services.DanaRKoreanExecutionService;
import info.nightscout.utils.Round;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class DanaRKoreanPlugin extends AbstractDanaRPlugin {

    private static DanaRKoreanPlugin plugin = null;

    public static DanaRKoreanPlugin getPlugin() {
        if (plugin == null)
            plugin = new DanaRKoreanPlugin();
        return plugin;
    }

    public DanaRKoreanPlugin() {
        log = LoggerFactory.getLogger(DanaRKoreanPlugin.class);
        useExtendedBoluses = SP.getBoolean("danar_useextended", false);

        Context context = MainApp.instance().getApplicationContext();
        Intent intent = new Intent(context, DanaRKoreanExecutionService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        MainApp.bus().register(this);

        pumpDescription.isBolusCapable = true;
        pumpDescription.bolusStep = 0.1d;

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
        pumpDescription.basalMinimumRate = 0.1d;

        pumpDescription.isRefillingCapable = true;

        pumpDescription.storesCarbInfo = true;
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            log.debug("Service is disconnected");
            sExecutionService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            log.debug("Service is connected");
            DanaRKoreanExecutionService.LocalBinder mLocalBinder = (DanaRKoreanExecutionService.LocalBinder) service;
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
            useExtendedBoluses = SP.getBoolean("danar_useextended", false);

            if (useExtendedBoluses != previousValue && MainApp.getConfigBuilder().isInHistoryExtendedBoluslInProgress()) {
                sExecutionService.extendedBolusStop();
            }
        }
    }

    // Plugin base interface
    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.danarkoreanpump);
    }

    @Override
    public int getPreferencesId() {
        return R.xml.pref_danarkorean;
    }

    // Pump interface
    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return useExtendedBoluses;
    }

    @Override
    public boolean isInitialized() {
        return pump.lastConnection > 0 && pump.maxBasal > 0 && !pump.isConfigUD && !pump.isEasyModeEnabled && pump.isExtendedBolusEnabled;
    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        ConfigBuilderPlugin configBuilderPlugin = MainApp.getConfigBuilder();
        detailedBolusInfo.insulin = configBuilderPlugin.applyBolusConstraints(detailedBolusInfo.insulin);
        if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
            Treatment t = new Treatment();
            boolean connectionOK = false;
            if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) connectionOK = sExecutionService.bolus(detailedBolusInfo.insulin, (int) detailedBolusInfo.carbs, detailedBolusInfo.carbTime, t);
            PumpEnactResult result = new PumpEnactResult();
            result.success = connectionOK;
            result.bolusDelivered = t.insulin;
            result.carbsDelivered = detailedBolusInfo.carbs;
            result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            if (Config.logPumpActions)
                log.debug("deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered);
            detailedBolusInfo.insulin = t.insulin;
            detailedBolusInfo.date = System.currentTimeMillis();
            MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
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

    // This is called from APS
    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, boolean enforceNew) {
        // Recheck pump status if older than 30 min
        //This should not be needed while using queue because connection should be done before calling this
        //if (pump.lastConnection.getTime() + 30 * 60 * 1000L < System.currentTimeMillis()) {
        //    connect("setTempBasalAbsolute old data");
        //}

        PumpEnactResult result = new PumpEnactResult();

        ConfigBuilderPlugin configBuilderPlugin = MainApp.getConfigBuilder();
        absoluteRate = configBuilderPlugin.applyBasalConstraints(absoluteRate);

        final boolean doTempOff = getBaseBasalRate() - absoluteRate == 0d;
        final boolean doLowTemp = absoluteRate < getBaseBasalRate();
        final boolean doHighTemp = absoluteRate > getBaseBasalRate() && !useExtendedBoluses;
        final boolean doExtendedTemp = absoluteRate > getBaseBasalRate() && useExtendedBoluses;

        if (doTempOff) {
            // If extended in progress
            if (MainApp.getConfigBuilder().isInHistoryExtendedBoluslInProgress() && useExtendedBoluses) {
                if (Config.logPumpActions)
                    log.debug("setTempBasalAbsolute: Stopping extended bolus (doTempOff)");
                return cancelExtendedBolus();
            }
            // If temp in progress
            if (MainApp.getConfigBuilder().isInHistoryRealTempBasalInProgress()) {
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
            if (percentRate > getPumpDescription().maxTempPercent) {
                percentRate = getPumpDescription().maxTempPercent;
            }
            if (Config.logPumpActions)
                log.debug("setTempBasalAbsolute: Calculated percent rate: " + percentRate);

            // If extended in progress
            if (MainApp.getConfigBuilder().isInHistoryExtendedBoluslInProgress() && useExtendedBoluses) {
                if (Config.logPumpActions)
                    log.debug("setTempBasalAbsolute: Stopping extended bolus (doLowTemp || doHighTemp)");
                result = cancelExtendedBolus();
                if (!result.success) {
                    log.error("setTempBasalAbsolute: Failed to stop previous extended bolus (doLowTemp || doHighTemp)");
                    return result;
                }
            }
            // Check if some temp is already in progress
            if (MainApp.getConfigBuilder().isInHistoryRealTempBasalInProgress()) {
                // Correct basal already set ?
                TemporaryBasal running = MainApp.getConfigBuilder().getRealTempBasalFromHistory(System.currentTimeMillis());
                if (Config.logPumpActions)
                    log.debug("setTempBasalAbsolute: currently running: " + running.toString());
                if (running.percentRate == percentRate) {
                    if (enforceNew) {
                        cancelTempBasal(true);
                    } else {
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
            return setTempBasalPercent(percentRate, durationInMinutes, false);
        }
        if (doExtendedTemp) {
            // Check if some temp is already in progress
            if (MainApp.getConfigBuilder().isInHistoryRealTempBasalInProgress()) {
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
            extendedRateToSet = Round.roundTo(extendedRateToSet, pumpDescription.extendedBolusStep * 2); // *2 because of halfhours

            // What is current rate of extended bolusing in u/h?
            if (Config.logPumpActions) {
                log.debug("setTempBasalAbsolute: Extended bolus in progress: " + MainApp.getConfigBuilder().isInHistoryExtendedBoluslInProgress() + " rate: " + pump.extendedBolusAbsoluteRate + "U/h duration remaining: " + pump.extendedBolusRemainingMinutes + "min");
                log.debug("setTempBasalAbsolute: Rate to set: " + extendedRateToSet + "U/h");
            }

            // Compare with extended rate in progress
            if (MainApp.getConfigBuilder().isInHistoryExtendedBoluslInProgress() && Math.abs(pump.extendedBolusAbsoluteRate - extendedRateToSet) < getPumpDescription().extendedBolusStep) {
                // correct extended already set
                result.success = true;
                result.absolute = pump.extendedBolusAbsoluteRate;
                result.enacted = false;
                result.duration = pump.extendedBolusRemainingMinutes;
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
    public PumpEnactResult cancelTempBasal(boolean force) {
        if (MainApp.getConfigBuilder().isInHistoryRealTempBasalInProgress())
            return cancelRealTempBasal();
        if (MainApp.getConfigBuilder().isInHistoryExtendedBoluslInProgress() && useExtendedBoluses) {
            PumpEnactResult cancelEx = cancelExtendedBolus();
            return cancelEx;
        }
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.enacted = false;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        result.isTempCancel = true;
        return result;
    }

    public PumpEnactResult cancelRealTempBasal() {
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
    public PumpEnactResult loadEvents() {
        return null; // no history, not needed
    }
}
