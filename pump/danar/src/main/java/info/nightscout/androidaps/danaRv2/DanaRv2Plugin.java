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
import info.nightscout.androidaps.annotations.OpenForTesting;
import info.nightscout.androidaps.danaRv2.services.DanaRv2ExecutionService;
import info.nightscout.androidaps.danar.AbstractDanaRPlugin;
import info.nightscout.androidaps.danar.R;
import info.nightscout.core.utils.fabric.FabricPrivacy;
import info.nightscout.interfaces.constraints.Constraint;
import info.nightscout.interfaces.constraints.Constraints;
import info.nightscout.interfaces.plugin.ActivePlugin;
import info.nightscout.interfaces.profile.Profile;
import info.nightscout.interfaces.pump.DetailedBolusInfo;
import info.nightscout.interfaces.pump.DetailedBolusInfoStorage;
import info.nightscout.interfaces.pump.PumpEnactResult;
import info.nightscout.interfaces.pump.PumpSync;
import info.nightscout.interfaces.pump.TemporaryBasalStorage;
import info.nightscout.interfaces.pump.defs.PumpType;
import info.nightscout.interfaces.queue.CommandQueue;
import info.nightscout.interfaces.ui.UiInteraction;
import info.nightscout.interfaces.utils.Round;
import info.nightscout.pump.dana.DanaPump;
import info.nightscout.pump.dana.database.DanaHistoryDatabase;
import info.nightscout.rx.AapsSchedulers;
import info.nightscout.rx.bus.RxBus;
import info.nightscout.rx.events.EventAppExit;
import info.nightscout.rx.events.EventOverviewBolusProgress;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;
import info.nightscout.shared.interfaces.ResourceHelper;
import info.nightscout.shared.sharedPreferences.SP;
import info.nightscout.shared.utils.DateUtil;
import info.nightscout.shared.utils.T;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

@Singleton
@OpenForTesting
public class DanaRv2Plugin extends AbstractDanaRPlugin {
    private final CompositeDisposable disposable = new CompositeDisposable();

    private final AAPSLogger aapsLogger;
    private final Context context;
    private final ResourceHelper rh;
    private final Constraints constraintChecker;
    private final DetailedBolusInfoStorage detailedBolusInfoStorage;
    private final TemporaryBasalStorage temporaryBasalStorage;
    private final FabricPrivacy fabricPrivacy;

    public long lastEventTimeLoaded = 0;

    @Inject
    public DanaRv2Plugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            AapsSchedulers aapsSchedulers,
            RxBus rxBus,
            Context context,
            ResourceHelper rh,
            Constraints constraintChecker,
            ActivePlugin activePlugin,
            SP sp,
            CommandQueue commandQueue,
            DanaPump danaPump,
            DetailedBolusInfoStorage detailedBolusInfoStorage,
            TemporaryBasalStorage temporaryBasalStorage,
            DateUtil dateUtil,
            FabricPrivacy fabricPrivacy,
            PumpSync pumpSync,
            UiInteraction uiInteraction,
            DanaHistoryDatabase danaHistoryDatabase
    ) {
        super(injector, danaPump, rh, constraintChecker, aapsLogger, aapsSchedulers, commandQueue, rxBus, activePlugin, sp, dateUtil, pumpSync, uiInteraction, danaHistoryDatabase);
        this.aapsLogger = aapsLogger;
        this.context = context;
        this.rh = rh;
        this.constraintChecker = constraintChecker;
        this.detailedBolusInfoStorage = detailedBolusInfoStorage;
        this.temporaryBasalStorage = temporaryBasalStorage;
        this.fabricPrivacy = fabricPrivacy;
        getPluginDescription().description(R.string.description_pump_dana_r_v2);

        useExtendedBoluses = false;
        pumpDescription.fillFor(PumpType.DANA_RV2);
    }

    @Override
    protected void onStart() {
        Intent intent = new Intent(context, DanaRv2ExecutionService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        disposable.add(rxBus
                .toObservable(EventAppExit.class)
                .observeOn(aapsSchedulers.getIo())
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
        return rh.gs(R.string.danarv2pump);
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
            detailedBolusInfo.timestamp = dateUtil.now() + (long) (speed * detailedBolusInfo.insulin * 1000);
            // clean carbs to prevent counting them as twice because they will picked up as another record
            // I don't think it's necessary to copy DetailedBolusInfo right now for carbs records
            double carbs = detailedBolusInfo.carbs;
            detailedBolusInfo.carbs = 0;
            long carbTimeStamp = detailedBolusInfo.getCarbsTimestamp() != null ? detailedBolusInfo.getCarbsTimestamp() : detailedBolusInfo.timestamp;
            if (carbTimeStamp == detailedBolusInfo.timestamp) carbTimeStamp -= T.Companion.mins(1).msecs(); // better set 1 min back to prevents clash with insulin

            detailedBolusInfoStorage.add(detailedBolusInfo); // will be picked up on reading history

            EventOverviewBolusProgress.Treatment t = new EventOverviewBolusProgress.Treatment(0, 0, detailedBolusInfo.getBolusType() == DetailedBolusInfo.BolusType.SMB, detailedBolusInfo.getId());
            boolean connectionOK = false;
            if (detailedBolusInfo.insulin > 0 || carbs > 0)
                connectionOK = sExecutionService.bolus(detailedBolusInfo.insulin, (int) carbs, carbTimeStamp, t);
            PumpEnactResult result = new PumpEnactResult(getInjector());
            result.success(connectionOK && Math.abs(detailedBolusInfo.insulin - t.getInsulin()) < pumpDescription.getBolusStep())
                    .bolusDelivered(t.getInsulin())
                    .carbsDelivered(detailedBolusInfo.carbs);
            if (!result.getSuccess())
                result.comment(rh.gs(R.string.boluserrorcode, detailedBolusInfo.insulin, t.getInsulin(),
                        danaPump.getBolusStartErrorCode()));
            else
                result.comment(R.string.ok);
            aapsLogger.debug(LTag.PUMP, "deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.getBolusDelivered());
            // remove carbs because it's get from history separately
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult(getInjector());
            result.success(false).bolusDelivered(0d).carbsDelivered(0d).comment(R.string.invalid_input);
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
    public PumpEnactResult setTempBasalAbsolute(double absoluteRate, int durationInMinutes, @NonNull Profile profile, boolean enforceNew, @NonNull PumpSync.TemporaryBasalType tbrType) {

        PumpEnactResult result = new PumpEnactResult(getInjector());

        absoluteRate = constraintChecker.applyBasalConstraints(new Constraint<>(absoluteRate), profile).value();

        boolean doTempOff = getBaseBasalRate() - absoluteRate == 0d && absoluteRate >= 0.10d;
        final boolean doLowTemp = absoluteRate < getBaseBasalRate() || absoluteRate < 0.10d;
        final boolean doHighTemp = absoluteRate > getBaseBasalRate();

        int percentRate = Double.valueOf(absoluteRate / getBaseBasalRate() * 100).intValue();
        // Any basal less than 0.10u/h will be dumped once per hour, not every 4 minutes. So if it's less than .10u/h, set a zero temp.
        if (absoluteRate < 0.10d) percentRate = 0;
        if (percentRate < 100) percentRate = (int) Round.INSTANCE.ceilTo((double) percentRate, 10d);
        else percentRate = (int) Round.INSTANCE.floorTo((double) percentRate, 10d);
        if (percentRate > 500) // Special high temp 500/15min
            percentRate = 500;
        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Calculated percent rate: " + percentRate);

        if (percentRate == 100) doTempOff = true;

        if (doTempOff) {
            // If temp in progress
            if (danaPump.isTempBasalInProgress()) {
                aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doTempOff)");
                return cancelTempBasal(false);
            }
            result.success(true).enacted(false).percent(100).isPercent(true).isTempCancel(true);
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: doTempOff OK");
            return result;
        }

        if (doLowTemp || doHighTemp) {
            // Check if some temp is already in progress
            if (danaPump.isTempBasalInProgress()) {
                // Correct basal already set ?
                if (danaPump.getTempBasalPercent() == percentRate && danaPump.getTempBasalRemainingMin() > 4) {
                    if (!enforceNew) {
                        result.success(true).percent(percentRate).enacted(false).duration(danaPump.getTempBasalRemainingMin()).isPercent(true).isTempCancel(false);
                        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Correct temp basal already set (doLowTemp || doHighTemp)");
                        return result;
                    }
                }
            }
            temporaryBasalStorage.add(new PumpSync.PumpState.TemporaryBasal(dateUtil.now(), T.Companion.mins(durationInMinutes).msecs(), percentRate, false, tbrType, 0L, 0L));
            // Convert duration from minutes to hours
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: Setting temp basal " + percentRate + "% for " + durationInMinutes + " minutes (doLowTemp || doHighTemp)");
            if (percentRate == 0 && durationInMinutes > 30) {
                result = setTempBasalPercent(percentRate, durationInMinutes, profile, enforceNew, tbrType);
            } else {
                // use special APS temp basal call ... 100+/15min .... 100-/30min
                result = setHighTempBasalPercent(percentRate, durationInMinutes);
            }
            if (!result.getSuccess()) {
                aapsLogger.error("setTempBasalAbsolute: Failed to set high temp basal");
                return result;
            }
            aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute: high temp basal set ok");
            return result;
        }
        // We should never end here
        aapsLogger.error("setTempBasalAbsolute: Internal error");
        result.success(false).comment("Internal error");
        return result;
    }

    @NonNull @Override
    public PumpEnactResult setTempBasalPercent(int percent, int durationInMinutes, @NonNull Profile profile, boolean enforceNew, @NonNull PumpSync.TemporaryBasalType tbrType) {
        DanaPump pump = danaPump;
        PumpEnactResult result = new PumpEnactResult(getInjector());
        percent = constraintChecker.applyBasalPercentConstraints(new Constraint<>(percent), profile).value();
        if (percent < 0) {
            result.isTempCancel(false).enacted(false).success(false).comment(R.string.invalid_input);
            aapsLogger.error("setTempBasalPercent: Invalid input");
            return result;
        }
        if (percent > getPumpDescription().getMaxTempPercent())
            percent = getPumpDescription().getMaxTempPercent();
        if (danaPump.isTempBasalInProgress() && danaPump.getTempBasalPercent() == percent && danaPump.getTempBasalRemainingMin() > 4 && !enforceNew) {
            result.enacted(false).success(true).isTempCancel(false).comment(R.string.ok).duration(pump.getTempBasalRemainingMin()).percent(pump.getTempBasalPercent()).isPercent(true);
            aapsLogger.debug(LTag.PUMP, "setTempBasalPercent: Correct value already set");
            return result;
        }
        temporaryBasalStorage.add(new PumpSync.PumpState.TemporaryBasal(dateUtil.now(), T.Companion.mins(durationInMinutes).msecs(), percent, false, tbrType, 0L, 0L));
        boolean connectionOK;
        if (durationInMinutes == 15 || durationInMinutes == 30) {
            connectionOK = sExecutionService.tempBasalShortDuration(percent, durationInMinutes);
        } else {
            int durationInHours = Math.max(durationInMinutes / 60, 1);
            connectionOK = sExecutionService.tempBasal(percent, durationInHours);
        }
        if (connectionOK && pump.isTempBasalInProgress() && pump.getTempBasalPercent() == percent) {
            result.enacted(true).success(true).comment(R.string.ok).isTempCancel(false).duration(pump.getTempBasalRemainingMin()).percent(pump.getTempBasalPercent()).isPercent(true);
            aapsLogger.debug(LTag.PUMP, "setTempBasalPercent: OK");
            return result;
        }
        result.enacted(false).success(false).comment(R.string.temp_basal_delivery_error);
        aapsLogger.error("setTempBasalPercent: Failed to set temp basal");
        return result;
    }

    private PumpEnactResult setHighTempBasalPercent(Integer percent, int durationInMinutes) {
        DanaPump pump = danaPump;
        PumpEnactResult result = new PumpEnactResult(getInjector());
        boolean connectionOK = sExecutionService.highTempBasal(percent, durationInMinutes);
        if (connectionOK && pump.isTempBasalInProgress() && pump.getTempBasalPercent() == percent) {
            result.enacted(true).success(true).comment(R.string.ok).isTempCancel(false).duration(pump.getTempBasalRemainingMin()).percent(pump.getTempBasalPercent()).isPercent(true);
            aapsLogger.debug(LTag.PUMP, "setHighTempBasalPercent: OK");
            return result;
        }
        result.enacted(false).success(false).comment(R.string.danar_valuenotsetproperly);
        aapsLogger.error("setHighTempBasalPercent: Failed to set temp basal");
        return result;
    }

    @NonNull @Override
    public PumpEnactResult cancelTempBasal(boolean force) {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        if (danaPump.isTempBasalInProgress()) {
            sExecutionService.tempBasalStop();
            result.enacted(true).isTempCancel(true);
        } else {
            result.success(true).isTempCancel(true).comment(R.string.ok);
            aapsLogger.debug(LTag.PUMP, "cancelRealTempBasal: OK");
        }
        return result;
    }

    @NonNull @Override
    public PumpEnactResult setExtendedBolus(double insulin, int durationInMinutes) {
        DanaPump pump = danaPump;
        insulin = constraintChecker.applyExtendedBolusConstraints(new Constraint<>(insulin)).value();
        // needs to be rounded
        int durationInHalfHours = Math.max(durationInMinutes / 30, 1);
        insulin = Round.INSTANCE.roundTo(insulin, getPumpDescription().getExtendedBolusStep());

        PumpEnactResult result = new PumpEnactResult(getInjector());
        if (danaPump.isExtendedInProgress() && Math.abs(danaPump.getExtendedBolusAmount() - insulin) < pumpDescription.getExtendedBolusStep()) {
            result.enacted(false)
                    .success(true)
                    .comment(R.string.ok)
                    .duration(pump.getExtendedBolusRemainingMinutes())
                    .absolute(pump.getExtendedBolusAbsoluteRate())
                    .isPercent(false)
                    .isTempCancel(false);
            getAapsLogger().debug(LTag.PUMP, "setExtendedBolus: Correct extended bolus already set. Current: " + pump.getExtendedBolusAmount() + " Asked: " + insulin);
            return result;
        }
        boolean connectionOK = sExecutionService.extendedBolus(insulin, durationInHalfHours);
        if (connectionOK && pump.isExtendedInProgress() && Math.abs(pump.getExtendedBolusAmount() - insulin) < getPumpDescription().getExtendedBolusStep()) {
            result.enacted(true)
                    .success(true)
                    .comment(R.string.ok)
                    .isTempCancel(false)
                    .duration(pump.getExtendedBolusRemainingMinutes())
                    .absolute(pump.getExtendedBolusAbsoluteRate())
                    .isPercent(false);
            if (!sp.getBoolean("danar_useextended", false))
                result.bolusDelivered(pump.getExtendedBolusAmount());
            getAapsLogger().debug(LTag.PUMP, "setExtendedBolus: OK");
            return result;
        }
        result.enacted(false).success(false).comment(R.string.danar_valuenotsetproperly);
        getAapsLogger().error("setExtendedBolus: Failed to extended bolus");
        return result;
    }

    @NonNull @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        if (danaPump.isExtendedInProgress()) {
            sExecutionService.extendedBolusStop();
            result.enacted(true).success(!danaPump.isExtendedInProgress()).isTempCancel(true);
        } else  {
            result.success(true).enacted(false).comment(R.string.ok);
            getAapsLogger().debug(LTag.PUMP, "cancelExtendedBolus: OK");
        }
        return result;
    }

    @NonNull @Override
    public PumpType model() {
        return PumpType.DANA_RV2;
    }

    @NonNull @Override
    public PumpEnactResult loadEvents() {
        return sExecutionService.loadEvents();
    }

    @NonNull @Override
    public PumpEnactResult setUserOptions() {
        return sExecutionService.setUserOptions();
    }
}
