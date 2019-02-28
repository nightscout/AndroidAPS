package info.nightscout.androidaps.plugins.pump.danaRKorean;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.danaR.AbstractDanaRPlugin;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaR.comm.MsgBolusStart;
import info.nightscout.androidaps.plugins.pump.danaRKorean.services.DanaRKoreanExecutionService;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.SP;

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
        pluginDescription.description(R.string.description_pump_dana_r_korean);

        useExtendedBoluses = SP.getBoolean(R.string.key_danar_useextended, false);
        pumpDescription.setPumpDescription(PumpType.DanaRKorean);
    }

    @Override
    public void switchAllowed(ConfigBuilderFragment.PluginViewHolder.PluginSwitcher pluginSwitcher, FragmentActivity context) {
        boolean allowHardwarePump = SP.getBoolean("allow_hardware_pump", false);
        if (allowHardwarePump || context == null) {
            pluginSwitcher.invoke();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(R.string.allow_hardware_pump_text)
                    .setPositiveButton(R.string.yes, (dialog, id) -> {
                        pluginSwitcher.invoke();
                        SP.putBoolean("allow_hardware_pump", true);
                        if (L.isEnabled(L.PUMP))
                            log.debug("First time HW pump allowed!");
                    })
                    .setNegativeButton(R.string.cancel, (dialog, id) -> {
                        pluginSwitcher.cancel();
                        if (L.isEnabled(L.PUMP))
                            log.debug("User does not allow switching to HW pump!");
                    });
            builder.create().show();
        }
    }


    @Override
    protected void onStart() {
        Context context = MainApp.instance().getApplicationContext();
        Intent intent = new Intent(context, DanaRKoreanExecutionService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        MainApp.bus().register(this);
        super.onStart();
    }

    @Override
    protected void onStop() {
        Context context = MainApp.instance().getApplicationContext();
        context.unbindService(mConnection);

        MainApp.bus().unregister(this);
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            if (L.isEnabled(L.PUMP))
                log.debug("Service is disconnected");
            sExecutionService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            if (L.isEnabled(L.PUMP))
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
        if (isEnabled(PluginType.PUMP)) {
            boolean previousValue = useExtendedBoluses;
            useExtendedBoluses = SP.getBoolean(R.string.key_danar_useextended, false);

            if (useExtendedBoluses != previousValue && TreatmentsPlugin.getPlugin().isInHistoryExtendedBoluslInProgress()) {
                sExecutionService.extendedBolusStop();
            }
        }
    }

    // Plugin base interface
    @Override
    public String getName() {
        return MainApp.gs(R.string.danarkoreanpump);
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
        DanaRPump pump = DanaRPump.getInstance();
        return pump.lastConnection > 0 && pump.maxBasal > 0 && !pump.isConfigUD && !pump.isEasyModeEnabled && pump.isExtendedBolusEnabled && pump.isPasswordOK();
    }

    @Override
    public boolean isHandshakeInProgress() {
        return sExecutionService != null && sExecutionService.isHandshakeInProgress();
    }

    @Override
    public void finishHandshaking() {
        sExecutionService.finishHandshaking();
    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        detailedBolusInfo.insulin = MainApp.getConstraintChecker().applyBolusConstraints(new Constraint<>(detailedBolusInfo.insulin)).value();
        if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
            Treatment t = new Treatment();
            t.isSMB = detailedBolusInfo.isSMB;
            boolean connectionOK = false;
            if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0)
                connectionOK = sExecutionService.bolus(detailedBolusInfo.insulin, (int) detailedBolusInfo.carbs, detailedBolusInfo.carbTime, t);
            PumpEnactResult result = new PumpEnactResult();
            result.success = connectionOK && Math.abs(detailedBolusInfo.insulin - t.insulin) < pumpDescription.bolusStep;
            result.bolusDelivered = t.insulin;
            result.carbsDelivered = detailedBolusInfo.carbs;
            if (!result.success)
                result.comment = String.format(MainApp.gs(R.string.boluserrorcode), detailedBolusInfo.insulin, t.insulin, MsgBolusStart.errorCode);
            else
                result.comment = MainApp.gs(R.string.virtualpump_resultok);
            if (L.isEnabled(L.PUMP))
                log.debug("deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered);
            detailedBolusInfo.insulin = t.insulin;
            detailedBolusInfo.date = System.currentTimeMillis();
            TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, false);
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult();
            result.success = false;
            result.bolusDelivered = 0d;
            result.carbsDelivered = 0d;
            result.comment = MainApp.gs(R.string.danar_invalidinput);
            log.error("deliverTreatment: Invalid input");
            return result;
        }
    }

    // This is called from APS
    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        // Recheck pump status if older than 30 min
        //This should not be needed while using queue because connection should be done before calling this
        //if (pump.lastConnection.getTime() + 30 * 60 * 1000L < System.currentTimeMillis()) {
        //    connect("setTempBasalAbsolute old data");
        //}
        DanaRPump pump = DanaRPump.getInstance();

        PumpEnactResult result = new PumpEnactResult();

        absoluteRate = MainApp.getConstraintChecker().applyBasalConstraints(new Constraint<>(absoluteRate), profile).value();

        final boolean doTempOff = getBaseBasalRate() - absoluteRate == 0d;
        final boolean doLowTemp = absoluteRate < getBaseBasalRate();
        final boolean doHighTemp = absoluteRate > getBaseBasalRate() && !useExtendedBoluses;
        final boolean doExtendedTemp = absoluteRate > getBaseBasalRate() && useExtendedBoluses;

        long now = System.currentTimeMillis();
        TemporaryBasal activeTemp = TreatmentsPlugin.getPlugin().getRealTempBasalFromHistory(now);
        ExtendedBolus activeExtended = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(now);

        if (doTempOff) {
            // If extended in progress
            if (activeExtended != null && useExtendedBoluses) {
                if (L.isEnabled(L.PUMP))
                    log.debug("setTempBasalAbsolute: Stopping extended bolus (doTempOff)");
                return cancelExtendedBolus();
            }
            // If temp in progress
            if (activeTemp != null) {
                if (L.isEnabled(L.PUMP))
                    log.debug("setTempBasalAbsolute: Stopping temp basal (doTempOff)");
                return cancelRealTempBasal();
            }
            result.success = true;
            result.enacted = false;
            result.percent = 100;
            result.isPercent = true;
            result.isTempCancel = true;
            if (L.isEnabled(L.PUMP))
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
            if (L.isEnabled(L.PUMP))
                log.debug("setTempBasalAbsolute: Calculated percent rate: " + percentRate);

            // If extended in progress
            if (activeExtended != null && useExtendedBoluses) {
                if (L.isEnabled(L.PUMP))
                    log.debug("setTempBasalAbsolute: Stopping extended bolus (doLowTemp || doHighTemp)");
                result = cancelExtendedBolus();
                if (!result.success) {
                    log.error("setTempBasalAbsolute: Failed to stop previous extended bolus (doLowTemp || doHighTemp)");
                    return result;
                }
            }
            // Check if some temp is already in progress
            if (activeTemp != null) {
                // Correct basal already set ?
                if (L.isEnabled(L.PUMP))
                    log.debug("setTempBasalAbsolute: currently running: " + activeTemp.toString());
                if (activeTemp.percentRate == percentRate) {
                    if (enforceNew) {
                        cancelTempBasal(true);
                    } else {
                        result.success = true;
                        result.percent = percentRate;
                        result.enacted = false;
                        result.duration = activeTemp.getPlannedRemainingMinutes();
                        result.isPercent = true;
                        result.isTempCancel = false;
                        if (L.isEnabled(L.PUMP))
                            log.debug("setTempBasalAbsolute: Correct temp basal already set (doLowTemp || doHighTemp)");
                        return result;
                    }
                }
            }
            // Convert duration from minutes to hours
            if (L.isEnabled(L.PUMP))
                log.debug("setTempBasalAbsolute: Setting temp basal " + percentRate + "% for " + durationInMinutes + " mins (doLowTemp || doHighTemp)");
            return setTempBasalPercent(percentRate, durationInMinutes, profile, false);
        }
        if (doExtendedTemp) {
            // Check if some temp is already in progress
            if (activeTemp != null) {
                if (L.isEnabled(L.PUMP))
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
            extendedRateToSet = MainApp.getConstraintChecker().applyBasalConstraints(new Constraint<>(extendedRateToSet), profile).value();
            // needs to be rounded to 0.1
            extendedRateToSet = Round.roundTo(extendedRateToSet, pumpDescription.extendedBolusStep * 2); // *2 because of halfhours

            // What is current rate of extended bolusing in u/h?
            if (L.isEnabled(L.PUMP)) {
                log.debug("setTempBasalAbsolute: Extended bolus in progress: " + (activeExtended != null) + " rate: " + pump.extendedBolusAbsoluteRate + "U/h duration remaining: " + pump.extendedBolusRemainingMinutes + "min");
                log.debug("setTempBasalAbsolute: Rate to set: " + extendedRateToSet + "U/h");
            }

            // Compare with extended rate in progress
            if (activeExtended != null && Math.abs(pump.extendedBolusAbsoluteRate - extendedRateToSet) < getPumpDescription().extendedBolusStep) {
                // correct extended already set
                result.success = true;
                result.absolute = pump.extendedBolusAbsoluteRate;
                result.enacted = false;
                result.duration = pump.extendedBolusRemainingMinutes;
                result.isPercent = false;
                result.isTempCancel = false;
                if (L.isEnabled(L.PUMP))
                    log.debug("setTempBasalAbsolute: Correct extended already set");
                return result;
            }

            // Now set new extended, no need to to stop previous (if running) because it's replaced
            Double extendedAmount = extendedRateToSet / 2 * durationInHalfHours;
            if (L.isEnabled(L.PUMP))
                log.debug("setTempBasalAbsolute: Setting extended: " + extendedAmount + "U  halfhours: " + durationInHalfHours);
            result = setExtendedBolus(extendedAmount, durationInMinutes);
            if (!result.success) {
                log.error("setTempBasalAbsolute: Failed to set extended bolus");
                return result;
            }
            if (L.isEnabled(L.PUMP))
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
        if (TreatmentsPlugin.getPlugin().isInHistoryRealTempBasalInProgress())
            return cancelRealTempBasal();
        if (TreatmentsPlugin.getPlugin().isInHistoryExtendedBoluslInProgress() && useExtendedBoluses) {
            return cancelExtendedBolus();
        }
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.enacted = false;
        result.comment = MainApp.gs(R.string.virtualpump_resultok);
        result.isTempCancel = true;
        return result;
    }

    private PumpEnactResult cancelRealTempBasal() {
        PumpEnactResult result = new PumpEnactResult();
        TemporaryBasal runningTB = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(System.currentTimeMillis());
        if (runningTB != null) {
            sExecutionService.tempBasalStop();
            result.enacted = true;
            result.isTempCancel = true;
        }
        if (!DanaRPump.getInstance().isTempBasalInProgress) {
            result.success = true;
            result.isTempCancel = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            if (L.isEnabled(L.PUMP))
                log.debug("cancelRealTempBasal: OK");
            return result;
        } else {
            result.success = false;
            result.comment = MainApp.gs(R.string.danar_valuenotsetproperly);
            result.isTempCancel = true;
            log.error("cancelRealTempBasal: Failed to cancel temp basal");
            return result;
        }
    }

    @Override
    public PumpEnactResult loadEvents() {
        return null; // no history, not needed
    }

    @Override
    public PumpEnactResult setUserOptions() {
        return null;
    }
}
