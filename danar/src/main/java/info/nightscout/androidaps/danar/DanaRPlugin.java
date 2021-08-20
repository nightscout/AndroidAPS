package info.nightscout.androidaps.danar;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.dana.DanaPump;
import info.nightscout.androidaps.danar.services.DanaRExecutionService;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

@Singleton
public class DanaRPlugin extends AbstractDanaRPlugin {
    private final CompositeDisposable disposable = new CompositeDisposable();

    private final AAPSLogger aapsLogger;
    private final Context context;
    private final ResourceHelper resourceHelper;
    private final ConstraintChecker constraintChecker;
    private final FabricPrivacy fabricPrivacy;

    @Inject
    public DanaRPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            Context context,
            ResourceHelper resourceHelper,
            ConstraintChecker constraintChecker,
            ActivePluginProvider activePlugin,
            SP sp,
            CommandQueueProvider commandQueue,
            DanaPump danaPump,
            DateUtil dateUtil,
            FabricPrivacy fabricPrivacy
    ) {
        super(injector, danaPump, resourceHelper, constraintChecker, aapsLogger, commandQueue, rxBus, activePlugin, sp, dateUtil);
        this.aapsLogger = aapsLogger;
        this.context = context;
        this.resourceHelper = resourceHelper;
        this.constraintChecker = constraintChecker;
        this.fabricPrivacy = fabricPrivacy;

        useExtendedBoluses = sp.getBoolean(R.string.key_danar_useextended, false);
        pumpDescription.setPumpDescription(PumpType.DanaR);
    }

    @Override
    protected void onStart() {
        Intent intent = new Intent(context, DanaRExecutionService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        disposable.add(rxBus
                .toObservable(EventPreferenceChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (isEnabled(PluginType.PUMP)) {
                        boolean previousValue = useExtendedBoluses;
                        useExtendedBoluses = sp.getBoolean(R.string.key_danar_useextended, false);

                        if (useExtendedBoluses != previousValue && activePlugin.getActiveTreatments().isInHistoryExtendedBoluslInProgress()) {
                            sExecutionService.extendedBolusStop();
                        }
                    }
                }, fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventAppExit.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> context.unbindService(mConnection), fabricPrivacy::logException)
        );
        super.onStart();
    }

    @Override
    protected void onStop() {
        context.unbindService(mConnection);

        disposable.clear();
        super.onStop();
    }

    private final ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            aapsLogger.debug(LTag.PUMP, "Service is disconnected");
            sExecutionService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            aapsLogger.debug(LTag.PUMP, "Service is connected");
            DanaRExecutionService.LocalBinder mLocalBinder = (DanaRExecutionService.LocalBinder) service;
            sExecutionService = mLocalBinder.getServiceInstance();
        }
    };

    // Plugin base interface
    @NonNull
    @Override
    public String getName() {
        return resourceHelper.gs(R.string.danarpump);
    }

    @Override
    public int getPreferencesId() {
        return R.xml.pref_danar;
    }

    // Pump interface
    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return useExtendedBoluses;
    }

    @Override
    public boolean isInitialized() {
        return danaPump.getLastConnection() > 0 && danaPump.isExtendedBolusEnabled() && danaPump.getMaxBasal() > 0 && danaPump.isPasswordOK();
    }

    @Override
    public boolean isHandshakeInProgress() {
        return sExecutionService != null && sExecutionService.isHandshakeInProgress();
    }

    @Override
    public void finishHandshaking() {
        sExecutionService.finishHandshaking();
    }

    @NonNull @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        detailedBolusInfo.insulin = constraintChecker.applyBolusConstraints(new Constraint<>(detailedBolusInfo.insulin)).value();
        if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
            Treatment t = new Treatment();
            t.isSMB = detailedBolusInfo.isSMB;
            boolean connectionOK = false;
            if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0)
                connectionOK = sExecutionService.bolus(detailedBolusInfo.insulin, (int) detailedBolusInfo.carbs, detailedBolusInfo.carbTime, t);
            PumpEnactResult result = new PumpEnactResult(getInjector());
            result.success = connectionOK && Math.abs(detailedBolusInfo.insulin - t.insulin) < pumpDescription.bolusStep;
            result.bolusDelivered = t.insulin;
            result.carbsDelivered = detailedBolusInfo.carbs;
            if (!result.success)
                result.comment = String.format(resourceHelper.gs(R.string.boluserrorcode), detailedBolusInfo.insulin, t.insulin, danaPump.getBolusStartErrorCode());
            else
                result.comment = resourceHelper.gs(R.string.ok);
            aapsLogger.debug(LTag.PUMP, "deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered);
            detailedBolusInfo.insulin = t.insulin;
            detailedBolusInfo.date = System.currentTimeMillis();
            activePlugin.getActiveTreatments().addToHistoryTreatment(detailedBolusInfo, false);
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult(getInjector());
            result.success = false;
            result.bolusDelivered = 0d;
            result.carbsDelivered = 0d;
            result.comment = resourceHelper.gs(R.string.invalidinput);
            aapsLogger.error("deliverTreatment: Invalid input");
            return result;
        }
    }

    // This is called from APS
    @NonNull @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        // Recheck pump status if older than 30 min
        //This should not be needed while using queue because connection should be done before calling this
        //if (pump.lastConnection.getTime() + 30 * 60 * 1000L < System.currentTimeMillis()) {
        //    connect("setTempBasalAbsolute old data");
        //}
        PumpEnactResult result = new PumpEnactResult(getInjector());

        absoluteRate = constraintChecker.applyBasalConstraints(new Constraint<>(absoluteRate), profile).value();

        final boolean doTempOff = getBaseBasalRate() - absoluteRate == 0d && absoluteRate >= 0.10d;
        final boolean doLowTemp = absoluteRate < getBaseBasalRate() || absoluteRate < 0.10d;
        final boolean doHighTemp = absoluteRate > getBaseBasalRate() && !useExtendedBoluses;
        final boolean doExtendedTemp = absoluteRate > getBaseBasalRate() && useExtendedBoluses;

        long now = System.currentTimeMillis();
        TemporaryBasal activeTemp = activePlugin.getActiveTreatments().getRealTempBasalFromHistory(now);
        ExtendedBolus activeExtended = activePlugin.getActiveTreatments().getExtendedBolusFromHistory(now);

        if (doTempOff) {
            // If extended in progress
            if (activeExtended != null && useExtendedBoluses) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping extended bolus (doTempOff)");
                return cancelExtendedBolus();
            }
            // If temp in progress
            if (activeTemp != null) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doTempOff)");
                return cancelRealTempBasal();
            }
            result.success = true;
            result.enacted = false;
            result.percent = 100;
            result.isPercent = true;
            result.isTempCancel = true;
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: doTempOff OK");
            return result;
        }

        if (doLowTemp || doHighTemp) {
            Integer percentRate = Double.valueOf(absoluteRate / getBaseBasalRate() * 100).intValue();
            // Any basal less than 0.10u/h will be dumped once per hour, not every 4 mins. So if it's less than .10u/h, set a zero temp.
            if (absoluteRate < 0.10d) percentRate = 0;
            if (percentRate < 100) percentRate = Round.ceilTo((double) percentRate, 10d).intValue();
            else percentRate = Round.floorTo((double) percentRate, 10d).intValue();
            if (percentRate > getPumpDescription().maxTempPercent) {
                percentRate = getPumpDescription().maxTempPercent;
            }
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Calculated percent rate: " + percentRate);

            // If extended in progress
            if (activeExtended != null && useExtendedBoluses) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping extended bolus (doLowTemp || doHighTemp)");
                result = cancelExtendedBolus();
                if (!result.success) {
                    aapsLogger.error("setTempBasalAbsolute: Failed to stop previous extended bolus (doLowTemp || doHighTemp)");
                    return result;
                }
            }
            // Check if some temp is already in progress
            if (activeTemp != null) {
                // Correct basal already set ?
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: currently running: " + activeTemp.toString());
                if (activeTemp.percentRate == percentRate && activeTemp.getPlannedRemainingMinutes() > 4) {
                    if (enforceNew) {
                        cancelTempBasal(true);
                    } else {
                        result.success = true;
                        result.percent = percentRate;
                        result.enacted = false;
                        result.duration = activeTemp.getPlannedRemainingMinutes();
                        result.isPercent = true;
                        result.isTempCancel = false;
                        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Correct temp basal already set (doLowTemp || doHighTemp)");
                        return result;
                    }
                }
            }
            // Convert duration from minutes to hours
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Setting temp basal " + percentRate + "% for " + durationInMinutes + " mins (doLowTemp || doHighTemp)");
            return setTempBasalPercent(percentRate, durationInMinutes, profile, false);
        }
        if (doExtendedTemp) {
            // Check if some temp is already in progress
            if (activeTemp != null) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doExtendedTemp)");
                result = cancelRealTempBasal();
                // Check for proper result
                if (!result.success) {
                    aapsLogger.error("setTempBasalAbsolute: Failed to stop previous temp basal (doExtendedTemp)");
                    return result;
                }
            }

            // Calculate # of halfHours from minutes
            int durationInHalfHours = Math.max(durationInMinutes / 30, 1);
            // We keep current basal running so need to sub current basal
            Double extendedRateToSet = absoluteRate - getBaseBasalRate();
            extendedRateToSet = constraintChecker.applyBasalConstraints(new Constraint<>(extendedRateToSet), profile).value();
            // needs to be rounded to 0.1
            extendedRateToSet = Round.roundTo(extendedRateToSet, pumpDescription.extendedBolusStep * 2); // *2 because of halfhours

            // What is current rate of extended bolusing in u/h?
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Extended bolus in progress: " + (activeExtended != null) + " rate: " + danaPump.getExtendedBolusAbsoluteRate() + "U/h duration remaining: " + danaPump.getExtendedBolusRemainingMinutes() + "min");
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Rate to set: " + extendedRateToSet + "U/h");

            // Compare with extended rate in progress
            if (activeExtended != null && Math.abs(danaPump.getExtendedBolusAbsoluteRate() - extendedRateToSet) < getPumpDescription().extendedBolusStep) {
                // correct extended already set
                result.success = true;
                result.absolute = danaPump.getExtendedBolusAbsoluteRate();
                result.enacted = false;
                result.duration = danaPump.getExtendedBolusRemainingMinutes();
                result.isPercent = false;
                result.isTempCancel = false;
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Correct extended already set");
                return result;
            }

            // Now set new extended, no need to to stop previous (if running) because it's replaced
            Double extendedAmount = extendedRateToSet / 2 * durationInHalfHours;
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Setting extended: " + extendedAmount + "U  halfhours: " + durationInHalfHours);
            result = setExtendedBolus(extendedAmount, durationInMinutes);
            if (!result.success) {
                aapsLogger.error("setTempBasalAbsolute: Failed to set extended bolus");
                return result;
            }
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Extended bolus set ok");
            result.absolute = result.absolute + getBaseBasalRate();
            return result;
        }
        // We should never end here
        aapsLogger.error("setTempBasalAbsolute: Internal error");
        result.success = false;
        result.comment = "Internal error";
        return result;
    }

    @NonNull @Override
    public PumpEnactResult cancelTempBasal(boolean force) {
        if (activePlugin.getActiveTreatments().isInHistoryRealTempBasalInProgress())
            return cancelRealTempBasal();
        if (activePlugin.getActiveTreatments().isInHistoryExtendedBoluslInProgress() && useExtendedBoluses) {
            return cancelExtendedBolus();
        }
        PumpEnactResult result = new PumpEnactResult(getInjector());
        result.success = true;
        result.enacted = false;
        result.comment = resourceHelper.gs(R.string.ok);
        result.isTempCancel = true;
        return result;
    }

    @NonNull @Override
    public PumpType model() {
        return PumpType.DanaR;
    }

    private PumpEnactResult cancelRealTempBasal() {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        TemporaryBasal runningTB = activePlugin.getActiveTreatments().getTempBasalFromHistory(System.currentTimeMillis());
        if (runningTB != null) {
            sExecutionService.tempBasalStop();
            result.enacted = true;
            result.isTempCancel = true;
        }
        if (!danaPump.isTempBasalInProgress()) {
            result.success = true;
            result.isTempCancel = true;
            result.comment = resourceHelper.gs(R.string.ok);
            aapsLogger.debug(LTag.PUMP, "cancelRealTempBasal: OK");
            return result;
        } else {
            result.success = false;
            result.comment = resourceHelper.gs(R.string.danar_valuenotsetproperly);
            result.isTempCancel = true;
            aapsLogger.error("cancelRealTempBasal: Failed to cancel temp basal");
            return result;
        }
    }

    @Override
    public PumpEnactResult loadEvents() {
        return null; // no history, not needed
    }

    @Override
    public PumpEnactResult setUserOptions() {
        return sExecutionService.setUserOptions();
    }
}
