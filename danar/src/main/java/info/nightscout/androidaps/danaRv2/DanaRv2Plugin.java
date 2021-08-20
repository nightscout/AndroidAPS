package info.nightscout.androidaps.danaRv2;

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
import info.nightscout.androidaps.danaRv2.services.DanaRv2ExecutionService;
import info.nightscout.androidaps.danar.AbstractDanaRPlugin;
import info.nightscout.androidaps.danar.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker;
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

@Singleton
public class DanaRv2Plugin extends AbstractDanaRPlugin {
    private final CompositeDisposable disposable = new CompositeDisposable();

    private final AAPSLogger aapsLogger;
    private final Context context;
    private final ResourceHelper resourceHelper;
    private final ConstraintChecker constraintChecker;
    private final DetailedBolusInfoStorage detailedBolusInfoStorage;
    private final FabricPrivacy fabricPrivacy;

    public long lastEventTimeLoaded = 0;
    public boolean eventsLoadingDone = false;

    @Inject
    public DanaRv2Plugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            Context context,
            DanaPump danaPump,
            ResourceHelper resourceHelper,
            ConstraintChecker constraintChecker,
            ActivePluginProvider activePlugin,
            SP sp,
            CommandQueueProvider commandQueue,
            DetailedBolusInfoStorage detailedBolusInfoStorage,
            DateUtil dateUtil,
            FabricPrivacy fabricPrivacy
    ) {
        super(injector, danaPump, resourceHelper, constraintChecker, aapsLogger, commandQueue, rxBus, activePlugin, sp, dateUtil);
        this.aapsLogger = aapsLogger;
        this.context = context;
        this.resourceHelper = resourceHelper;
        this.constraintChecker = constraintChecker;
        this.detailedBolusInfoStorage = detailedBolusInfoStorage;
        this.fabricPrivacy = fabricPrivacy;
        getPluginDescription().description(R.string.description_pump_dana_r_v2);

        useExtendedBoluses = false;
        pumpDescription.setPumpDescription(PumpType.DanaRv2);
    }

    @Override
    protected void onStart() {
        Intent intent = new Intent(context, DanaRv2ExecutionService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

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
            DanaRv2ExecutionService.LocalBinder mLocalBinder = (DanaRv2ExecutionService.LocalBinder) service;
            sExecutionService = mLocalBinder.getServiceInstance();
        }
    };

    // Plugin base interface
    @NonNull
    @Override
    public String getName() {
        return resourceHelper.gs(R.string.danarv2pump);
    }

    @Override
    public int getPreferencesId() {
        return R.xml.pref_danarv2;
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return danaPump.getLastConnection() > 0 && danaPump.getMaxBasal() > 0 && danaPump.isPasswordOK();
    }

    @Override
    public boolean isHandshakeInProgress() {
        return sExecutionService != null && sExecutionService.isHandshakeInProgress();
    }

    @Override
    public void finishHandshaking() {
        sExecutionService.finishHandshaking();
    }

    // Pump interface
    @NonNull @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        detailedBolusInfo.insulin = constraintChecker.applyBolusConstraints(new Constraint<>(detailedBolusInfo.insulin)).value();
        if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
            // v2 stores end time for bolus, we need to adjust time
            // default delivery speed is 12 sec/U
            int preferencesSpeed = sp.getInt(R.string.key_danars_bolusspeed, 0);
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
            detailedBolusInfo.date = DateUtil.now() + (long) (speed * detailedBolusInfo.insulin * 1000);
            // clean carbs to prevent counting them as twice because they will picked up as another record
            // I don't think it's necessary to copy DetailedBolusInfo right now for carbs records
            double carbs = detailedBolusInfo.carbs;
            detailedBolusInfo.carbs = 0;
            int carbTime = detailedBolusInfo.carbTime;
            if (carbTime == 0) carbTime--; // better set 1 man back to prevent clash with insulin
            detailedBolusInfo.carbTime = 0;

            detailedBolusInfoStorage.add(detailedBolusInfo); // will be picked up on reading history

            Treatment t = new Treatment();
            t.isSMB = detailedBolusInfo.isSMB;
            boolean connectionOK = false;
            if (detailedBolusInfo.insulin > 0 || carbs > 0)
                connectionOK = sExecutionService.bolus(detailedBolusInfo.insulin, (int) carbs, DateUtil.now() + T.mins(carbTime).msecs(), t);
            PumpEnactResult result = new PumpEnactResult(getInjector());
            result.success = connectionOK && Math.abs(detailedBolusInfo.insulin - t.insulin) < pumpDescription.bolusStep;
            result.bolusDelivered = t.insulin;
            result.carbsDelivered = detailedBolusInfo.carbs;
            if (!result.success)
                result.comment = String.format(resourceHelper.gs(R.string.boluserrorcode), detailedBolusInfo.insulin, t.insulin, danaPump.getBolusStartErrorCode());
            else
                result.comment = resourceHelper.gs(R.string.ok);
            aapsLogger.debug(LTag.PUMP, "deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered);
            // remove carbs because it's get from history separately
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

    @Override
    public void stopBolusDelivering() {
        if (sExecutionService == null) {
            aapsLogger.error("stopBolusDelivering sExecutionService is null");
            return;
        }
        sExecutionService.bolusStop();
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
        final boolean doHighTemp = absoluteRate > getBaseBasalRate();

        if (doTempOff) {
            // If temp in progress
            if (activePlugin.getActiveTreatments().isTempBasalInProgress()) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doTempOff)");
                return cancelTempBasal(false);
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
            if (percentRate > 500) // Special high temp 500/15min
                percentRate = 500;
            // Check if some temp is already in progress
            TemporaryBasal activeTemp = activePlugin.getActiveTreatments().getTempBasalFromHistory(System.currentTimeMillis());
            if (activeTemp != null) {
                // Correct basal already set ?
                if (activeTemp.percentRate == percentRate && activeTemp.getPlannedRemainingMinutes() > 4) {
                    if (!enforceNew) {
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
            if (percentRate == 0 && durationInMinutes > 30) {
                result = setTempBasalPercent(percentRate, durationInMinutes, profile, enforceNew);
            } else {
                // use special APS temp basal call ... 100+/15min .... 100-/30min
                result = setHighTempBasalPercent(percentRate, durationInMinutes);
            }
            if (!result.success) {
                aapsLogger.error("setTempBasalAbsolute: Failed to set hightemp basal");
                return result;
            }
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: hightemp basal set ok");
            return result;
        }
        // We should never end here
        aapsLogger.error("setTempBasalAbsolute: Internal error");
        result.success = false;
        result.comment = "Internal error";
        return result;
    }

    @NonNull @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        DanaPump pump = danaPump;
        PumpEnactResult result = new PumpEnactResult(getInjector());
        percent = constraintChecker.applyBasalPercentConstraints(new Constraint<>(percent), profile).value();
        if (percent < 0) {
            result.isTempCancel = false;
            result.enacted = false;
            result.success = false;
            result.comment = resourceHelper.gs(R.string.invalidinput);
            aapsLogger.error("setTempBasalPercent: Invalid input");
            return result;
        }
        if (percent > getPumpDescription().maxTempPercent)
            percent = getPumpDescription().maxTempPercent;
        long now = System.currentTimeMillis();
        TemporaryBasal activeTemp = activePlugin.getActiveTreatments().getRealTempBasalFromHistory(now);
        if (activeTemp != null && activeTemp.percentRate == percent && activeTemp.getPlannedRemainingMinutes() > 4 && !enforceNew) {
            result.enacted = false;
            result.success = true;
            result.isTempCancel = false;
            result.comment = resourceHelper.gs(R.string.ok);
            result.duration = pump.getTempBasalRemainingMin();
            result.percent = pump.getTempBasalPercent();
            result.isPercent = true;
            aapsLogger.debug(LTag.PUMP, "setTempBasalPercent: Correct value already set");
            return result;
        }
        boolean connectionOK;
        if (durationInMinutes == 15 || durationInMinutes == 30) {
            connectionOK = sExecutionService.tempBasalShortDuration(percent, durationInMinutes);
        } else {
            int durationInHours = Math.max(durationInMinutes / 60, 1);
            connectionOK = sExecutionService.tempBasal(percent, durationInHours);
        }
        if (connectionOK && pump.isTempBasalInProgress() && pump.getTempBasalPercent() == percent) {
            result.enacted = true;
            result.success = true;
            result.comment = resourceHelper.gs(R.string.ok);
            result.isTempCancel = false;
            result.duration = pump.getTempBasalRemainingMin();
            result.percent = pump.getTempBasalPercent();
            result.isPercent = true;
            aapsLogger.debug(LTag.PUMP, "setTempBasalPercent: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = resourceHelper.gs(R.string.tempbasaldeliveryerror);
        aapsLogger.error("setTempBasalPercent: Failed to set temp basal");
        return result;
    }

    private PumpEnactResult setHighTempBasalPercent(Integer percent, int durationInMinutes) {
        DanaPump pump = danaPump;
        PumpEnactResult result = new PumpEnactResult(getInjector());
        boolean connectionOK = sExecutionService.highTempBasal(percent, durationInMinutes);
        if (connectionOK && pump.isTempBasalInProgress() && pump.getTempBasalPercent() == percent) {
            result.enacted = true;
            result.success = true;
            result.comment = resourceHelper.gs(R.string.ok);
            result.isTempCancel = false;
            result.duration = pump.getTempBasalRemainingMin();
            result.percent = pump.getTempBasalPercent();
            result.isPercent = true;
            aapsLogger.debug(LTag.PUMP, "setHighTempBasalPercent: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = resourceHelper.gs(R.string.danar_valuenotsetproperly);
        aapsLogger.error("setHighTempBasalPercent: Failed to set temp basal");
        return result;
    }

    @NonNull @Override
    public PumpEnactResult cancelTempBasal(boolean force) {
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

    @NonNull @Override
    public PumpType model() {
        return PumpType.DanaRv2;
    }

    @Override
    public PumpEnactResult loadEvents() {
        return sExecutionService.loadEvents();
    }

    @Override
    public PumpEnactResult setUserOptions() {
        return sExecutionService.setUserOptions();
    }
}
