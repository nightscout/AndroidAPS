package info.nightscout.androidaps.plugins.pump.danaRS;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.preference.Preference;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventAppExit;
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
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.bolusInfo.DetailedBolusInfoStorage;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRFragment;
import info.nightscout.androidaps.plugins.pump.danaR.DanaRPump;
import info.nightscout.androidaps.plugins.pump.danaR.comm.RecordTypes;
import info.nightscout.androidaps.plugins.pump.danaRS.events.EventDanaRSDeviceChange;
import info.nightscout.androidaps.plugins.pump.danaRS.services.DanaRSService;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.TimeChangeType;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

@Singleton
public class DanaRSPlugin extends PumpPluginBase implements PumpInterface, DanaRInterface, ConstraintsInterface {
    private CompositeDisposable disposable = new CompositeDisposable();

    private final Context context;
    private final ResourceHelper resourceHelper;
    private final ConstraintChecker constraintChecker;
    private final ProfileFunction profileFunction;
    private final TreatmentsPlugin treatmentsPlugin;
    private final SP sp;
    private final RxBusWrapper rxBus;
    private final CommandQueueProvider commandQueue;
    private final DanaRPump danaRPump;
    private final DetailedBolusInfoStorage detailedBolusInfoStorage;
    private final DateUtil dateUtil;

    private static DanaRSService danaRSService;

    private static String mDeviceAddress = "";
    public static String mDeviceName = "";

    public static PumpDescription pumpDescription = new PumpDescription();

    // Bolus & history handling
    public int bolusStartErrorCode; // from start message
    public Treatment bolusingTreatment; // actually delivered treatment
    public double bolusAmountToBeDelivered = 0.0; // amount to be delivered
    public boolean bolusStopped = false; // bolus finished
    public boolean bolusStopForced = false; // bolus forced to stop by user
    public boolean bolusDone = false; // success end
    public long bolusProgressLastTimeStamp = 0; // timestamp of last bolus progress message
    public boolean apsHistoryDone = false; // true when last history message is received
    public long lastEventTimeLoaded = 0; // timestamp of last received event

    @Inject
    public DanaRSPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            Context context,
            ResourceHelper resourceHelper,
            ConstraintChecker constraintChecker,
            ProfileFunction profileFunction,
            TreatmentsPlugin treatmentsPlugin,
            SP sp,
            CommandQueueProvider commandQueue,
            DanaRPump danaRPump,
            DetailedBolusInfoStorage detailedBolusInfoStorage,
            DateUtil dateUtil
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.PUMP)
                        .fragmentClass(DanaRFragment.class.getName())
                        .pluginName(R.string.danarspump)
                        .shortName(R.string.danarspump_shortname)
                        .preferencesId(R.xml.pref_danars)
                        .description(R.string.description_pump_dana_rs),
                injector, aapsLogger, resourceHelper, commandQueue
        );
        this.context = context;
        this.rxBus = rxBus;
        this.resourceHelper = resourceHelper;
        this.constraintChecker = constraintChecker;
        this.profileFunction = profileFunction;
        this.treatmentsPlugin = treatmentsPlugin;
        this.sp = sp;
        this.commandQueue = commandQueue;
        this.danaRPump = danaRPump;
        this.detailedBolusInfoStorage = detailedBolusInfoStorage;
        this.dateUtil = dateUtil;

        pumpDescription.setPumpDescription(PumpType.DanaRS);
    }

    @Override
    public void updatePreferenceSummary(@NotNull Preference pref) {
        super.updatePreferenceSummary(pref);

        if (pref.getKey().equals(resourceHelper.gs(R.string.key_danars_name)))
            pref.setSummary(sp.getString(R.string.key_danars_name, ""));
    }

    @Override
    protected void onStart() {
        Intent intent = new Intent(context, DanaRSService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        disposable.add(rxBus
                .toObservable(EventAppExit.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> context.unbindService(mConnection), exception -> FabricPrivacy.getInstance().logException(exception))
        );
        disposable.add(rxBus
                .toObservable(EventDanaRSDeviceChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> loadAddress(), exception -> FabricPrivacy.getInstance().logException(exception))
        );
        loadAddress(); // load device name
        super.onStart();
    }

    @Override
    protected void onStop() {
        context.unbindService(mConnection);

        disposable.clear();
        super.onStop();
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceDisconnected(ComponentName name) {
            getAapsLogger().debug(LTag.PUMP, "Service is disconnected");
            danaRSService = null;
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            getAapsLogger().debug(LTag.PUMP, "Service is connected");
            DanaRSService.LocalBinder mLocalBinder = (DanaRSService.LocalBinder) service;
            danaRSService = mLocalBinder.getServiceInstance();
        }
    };

    private void loadAddress() {
        mDeviceAddress = sp.getString(R.string.key_danars_address, "");
        mDeviceName = sp.getString(R.string.key_danars_name, "");
    }

    @Override
    public void connect(String from) {
        getAapsLogger().debug(LTag.PUMP, "RS connect from: " + from);
        if (danaRSService != null && !mDeviceAddress.equals("") && !mDeviceName.equals("")) {
            final Object o = new Object();

            danaRSService.connect(from, mDeviceAddress, o);
        }
    }

    @Override
    public boolean isConnected() {
        return danaRSService != null && danaRSService.isConnected();
    }

    @Override
    public boolean isConnecting() {
        return danaRSService != null && danaRSService.isConnecting();
    }

    @Override
    public boolean isHandshakeInProgress() {
        return false;
    }

    @Override
    public void finishHandshaking() {
    }

    @Override
    public void disconnect(String from) {
        getAapsLogger().debug(LTag.PUMP, "RS disconnect from: " + from);
        if (danaRSService != null) danaRSService.disconnect(from);
    }

    @Override
    public void stopConnecting() {
        if (danaRSService != null) danaRSService.stopConnecting();
    }

    @Override
    public void getPumpStatus() {
        if (danaRSService != null) {
            danaRSService.getPumpStatus();
            pumpDescription.basalStep = danaRPump.getBasalStep();
            pumpDescription.bolusStep = danaRPump.getBolusStep();
        }
    }

    // DanaR interface

    @Override
    public PumpEnactResult loadHistory(byte type) {
        return danaRSService.loadHistory(type);
    }

    @Override
    public PumpEnactResult loadEvents() {
        return danaRSService.loadEvents();
    }

    @Override
    public PumpEnactResult setUserOptions() {
        return danaRSService.setUserSettings();
    }

    // Constraints interface

    @NonNull
    @Override
    public Constraint<Double> applyBasalConstraints(Constraint<Double> absoluteRate, @NonNull Profile profile) {
        absoluteRate.setIfSmaller(getAapsLogger(), danaRPump.getMaxBasal(), resourceHelper.gs(R.string.limitingbasalratio, danaRPump.getMaxBasal(), resourceHelper.gs(R.string.pumplimit)), this);
        return absoluteRate;
    }

    @NonNull
    @Override
    public Constraint<Integer> applyBasalPercentConstraints(Constraint<Integer> percentRate, @NonNull Profile profile) {
        percentRate.setIfGreater(getAapsLogger(), 0, resourceHelper.gs(R.string.limitingpercentrate, 0, resourceHelper.gs(R.string.itmustbepositivevalue)), this);
        percentRate.setIfSmaller(getAapsLogger(), getPumpDescription().maxTempPercent, resourceHelper.gs(R.string.limitingpercentrate, getPumpDescription().maxTempPercent, resourceHelper.gs(R.string.pumplimit)), this);

        return percentRate;
    }


    @NonNull
    @Override
    public Constraint<Double> applyBolusConstraints(Constraint<Double> insulin) {
        insulin.setIfSmaller(getAapsLogger(), danaRPump.getMaxBolus(), resourceHelper.gs(R.string.limitingbolus, danaRPump.getMaxBolus(), resourceHelper.gs(R.string.pumplimit)), this);
        return insulin;
    }

    @NonNull
    @Override
    public Constraint<Double> applyExtendedBolusConstraints(@NonNull Constraint<Double> insulin) {
        return applyBolusConstraints(insulin);
    }

    // Pump interface

    @Override
    public boolean isInitialized() {
        return danaRPump.getLastConnection() > 0 && danaRPump.getMaxBasal() > 0;
    }

    @Override
    public boolean isSuspended() {
        return danaRPump.getPumpSuspended();
    }

    @Override
    public boolean isBusy() {
        if (danaRSService == null) return false;
        return danaRSService.isConnected() || danaRSService.isConnecting();
    }

    @NonNull @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        PumpEnactResult result = new PumpEnactResult(getInjector());

        if (danaRSService == null) {
            getAapsLogger().error("setNewBasalProfile sExecutionService is null");
            result.comment = "setNewBasalProfile sExecutionService is null";
            return result;
        }
        if (!isInitialized()) {
            getAapsLogger().error("setNewBasalProfile not initialized");
            Notification notification = new Notification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED, resourceHelper.gs(R.string.pumpNotInitializedProfileNotSet), Notification.URGENT);
            rxBus.send(new EventNewNotification(notification));
            result.comment = resourceHelper.gs(R.string.pumpNotInitializedProfileNotSet);
            return result;
        } else {
            rxBus.send(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
        }
        if (!danaRSService.updateBasalsInPump(profile)) {
            Notification notification = new Notification(Notification.FAILED_UDPATE_PROFILE, resourceHelper.gs(R.string.failedupdatebasalprofile), Notification.URGENT);
            rxBus.send(new EventNewNotification(notification));
            result.comment = resourceHelper.gs(R.string.failedupdatebasalprofile);
            return result;
        } else {
            rxBus.send(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
            rxBus.send(new EventDismissNotification(Notification.FAILED_UDPATE_PROFILE));
            Notification notification = new Notification(Notification.PROFILE_SET_OK, resourceHelper.gs(R.string.profile_set_ok), Notification.INFO, 60);
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

    @NonNull @Override
    public synchronized PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        detailedBolusInfo.insulin = constraintChecker.applyBolusConstraints(new Constraint<>(detailedBolusInfo.insulin)).value();
        if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
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
            // RS stores end time for bolus, we need to adjust time
            // default delivery speed is 12 sec/U
            detailedBolusInfo.date = DateUtil.now() + (long) (speed * detailedBolusInfo.insulin * 1000);
            // clean carbs to prevent counting them as twice because they will picked up as another record
            // I don't think it's necessary to copy DetailedBolusInfo right now for carbs records
            double carbs = detailedBolusInfo.carbs;
            detailedBolusInfo.carbs = 0;
            int carbTime = detailedBolusInfo.carbTime;
            if (carbTime == 0) carbTime--; // better set 1 min back to prevents clash with insulin
            detailedBolusInfo.carbTime = 0;

            detailedBolusInfoStorage.add(detailedBolusInfo); // will be picked up on reading history

            Treatment t = new Treatment();
            t.isSMB = detailedBolusInfo.isSMB;
            boolean connectionOK = false;
            if (detailedBolusInfo.insulin > 0 || carbs > 0)
                connectionOK = danaRSService.bolus(detailedBolusInfo.insulin, (int) carbs, DateUtil.now() + T.mins(carbTime).msecs(), t);
            PumpEnactResult result = new PumpEnactResult(getInjector());
            result.success = connectionOK && Math.abs(detailedBolusInfo.insulin - t.insulin) < pumpDescription.bolusStep;
            result.bolusDelivered = t.insulin;
            result.carbsDelivered = detailedBolusInfo.carbs;
            if (!result.success) {
                String error = "" + bolusStartErrorCode;
                switch (bolusStartErrorCode) {
                    // 4 reported as max bolus violation. Check later
                    case 0x10:
                        error = resourceHelper.gs(R.string.maxbolusviolation);
                        break;
                    case 0x20:
                        error = resourceHelper.gs(R.string.commanderror);
                        break;
                    case 0x40:
                        error = resourceHelper.gs(R.string.speederror);
                        break;
                    case 0x80:
                        error = resourceHelper.gs(R.string.insulinlimitviolation);
                        break;
                }
                result.comment = String.format(resourceHelper.gs(R.string.boluserrorcode), detailedBolusInfo.insulin, t.insulin, error);
            } else
                result.comment = resourceHelper.gs(R.string.virtualpump_resultok);
            getAapsLogger().debug(LTag.PUMP, "deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered);
            return result;
        } else {
            PumpEnactResult result = new PumpEnactResult(getInjector());
            result.success = false;
            result.bolusDelivered = 0d;
            result.carbsDelivered = 0d;
            result.comment = resourceHelper.gs(R.string.danar_invalidinput);
            getAapsLogger().error("deliverTreatment: Invalid input");
            return result;
        }
    }

    @Override
    public void stopBolusDelivering() {
        if (danaRSService == null) {
            getAapsLogger().error("stopBolusDelivering sExecutionService is null");
            return;
        }
        danaRSService.bolusStop();
    }

    // This is called from APS
    @NonNull @Override
    public synchronized PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        // Recheck pump status if older than 30 min

        //This should not be needed while using queue because connection should be done before calling this
        //if (pump.lastConnection.getTime() + 30 * 60 * 1000L < System.currentTimeMillis()) {
        //    connect("setTempBasalAbsolute old data");
        //}

        PumpEnactResult result = new PumpEnactResult(getInjector());

        absoluteRate = constraintChecker.applyBasalConstraints(new Constraint<>(absoluteRate), profile).value();

        final boolean doTempOff = getBaseBasalRate() - absoluteRate == 0d;
        final boolean doLowTemp = absoluteRate < getBaseBasalRate();
        final boolean doHighTemp = absoluteRate > getBaseBasalRate();

        if (doTempOff) {
            // If temp in progress
            if (treatmentsPlugin.isTempBasalInProgress()) {
                getAapsLogger().debug(LTag.PUMP, "setTempBasalAbsolute: Stopping temp basal (doTempOff)");
                return cancelTempBasal(false);
            }
            result.success = true;
            result.enacted = false;
            result.percent = 100;
            result.isPercent = true;
            result.isTempCancel = true;
            getAapsLogger().debug(LTag.PUMP, "setTempBasalAbsolute: doTempOff OK");
            return result;
        }

        if (doLowTemp || doHighTemp) {
            Integer percentRate = Double.valueOf(absoluteRate / getBaseBasalRate() * 100).intValue();
            if (percentRate < 100) percentRate = Round.ceilTo((double) percentRate, 10d).intValue();
            else percentRate = Round.floorTo((double) percentRate, 10d).intValue();
            if (percentRate > 500) // Special high temp 500/15min
                percentRate = 500;
            // Check if some temp is already in progress
            TemporaryBasal activeTemp = treatmentsPlugin.getTempBasalFromHistory(System.currentTimeMillis());
            if (activeTemp != null) {
                getAapsLogger().debug(LTag.PUMP, "setTempBasalAbsolute: currently running: " + activeTemp.toString());
                // Correct basal already set ?
                if (activeTemp.percentRate == percentRate && activeTemp.getPlannedRemainingMinutes() > 4) {
                    if (!enforceNew) {
                        result.success = true;
                        result.percent = percentRate;
                        result.enacted = false;
                        result.duration = activeTemp.getPlannedRemainingMinutes();
                        result.isPercent = true;
                        result.isTempCancel = false;
                        getAapsLogger().debug(LTag.PUMP, "setTempBasalAbsolute: Correct temp basal already set (doLowTemp || doHighTemp)");
                        return result;
                    }
                }
            }
            // Convert duration from minutes to hours
            getAapsLogger().debug(LTag.PUMP, "setTempBasalAbsolute: Setting temp basal " + percentRate + "% for " + durationInMinutes + " mins (doLowTemp || doHighTemp)");
            if (percentRate == 0 && durationInMinutes > 30) {
                result = setTempBasalPercent(percentRate, durationInMinutes, profile, enforceNew);
            } else {
                // use special APS temp basal call ... 100+/15min .... 100-/30min
                result = setHighTempBasalPercent(percentRate);
            }
            if (!result.success) {
                getAapsLogger().error("setTempBasalAbsolute: Failed to set hightemp basal");
                return result;
            }
            getAapsLogger().debug(LTag.PUMP, "setTempBasalAbsolute: hightemp basal set ok");
            return result;
        }
        // We should never end here
        getAapsLogger().error("setTempBasalAbsolute: Internal error");
        result.success = false;
        result.comment = "Internal error";
        return result;
    }

    @NonNull @Override
    public synchronized PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        DanaRPump pump = danaRPump;
        PumpEnactResult result = new PumpEnactResult(getInjector());
        percent = constraintChecker.applyBasalPercentConstraints(new Constraint<>(percent), profile).value();
        if (percent < 0) {
            result.isTempCancel = false;
            result.enacted = false;
            result.success = false;
            result.comment = resourceHelper.gs(R.string.danar_invalidinput);
            getAapsLogger().error("setTempBasalPercent: Invalid input");
            return result;
        }
        if (percent > getPumpDescription().maxTempPercent)
            percent = getPumpDescription().maxTempPercent;
        long now = System.currentTimeMillis();
        TemporaryBasal activeTemp = treatmentsPlugin.getTempBasalFromHistory(now);
        if (activeTemp != null && activeTemp.percentRate == percent && activeTemp.getPlannedRemainingMinutes() > 4 && !enforceNew) {
            result.enacted = false;
            result.success = true;
            result.isTempCancel = false;
            result.comment = resourceHelper.gs(R.string.virtualpump_resultok);
            result.duration = pump.getTempBasalRemainingMin();
            result.percent = pump.getTempBasalPercent();
            result.isPercent = true;
            getAapsLogger().debug(LTag.PUMP, "setTempBasalPercent: Correct value already set");
            return result;
        }
        boolean connectionOK;
        if (durationInMinutes == 15 || durationInMinutes == 30) {
            connectionOK = danaRSService.tempBasalShortDuration(percent, durationInMinutes);
        } else {
            int durationInHours = Math.max(durationInMinutes / 60, 1);
            connectionOK = danaRSService.tempBasal(percent, durationInHours);
        }
        if (connectionOK && pump.isTempBasalInProgress() && pump.getTempBasalPercent() == percent) {
            result.enacted = true;
            result.success = true;
            result.comment = resourceHelper.gs(R.string.virtualpump_resultok);
            result.isTempCancel = false;
            result.duration = pump.getTempBasalRemainingMin();
            result.percent = pump.getTempBasalPercent();
            result.isPercent = true;
            getAapsLogger().debug(LTag.PUMP, "setTempBasalPercent: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = resourceHelper.gs(R.string.tempbasaldeliveryerror);
        getAapsLogger().error("setTempBasalPercent: Failed to set temp basal");
        return result;
    }

    private synchronized PumpEnactResult setHighTempBasalPercent(Integer percent) {
        DanaRPump pump = danaRPump;
        PumpEnactResult result = new PumpEnactResult(getInjector());
        boolean connectionOK = danaRSService.highTempBasal(percent);
        if (connectionOK && pump.isTempBasalInProgress() && pump.getTempBasalPercent() == percent) {
            result.enacted = true;
            result.success = true;
            result.comment = resourceHelper.gs(R.string.virtualpump_resultok);
            result.isTempCancel = false;
            result.duration = pump.getTempBasalRemainingMin();
            result.percent = pump.getTempBasalPercent();
            result.isPercent = true;
            getAapsLogger().debug(LTag.PUMP, "setHighTempBasalPercent: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = resourceHelper.gs(R.string.danar_valuenotsetproperly);
        getAapsLogger().error("setHighTempBasalPercent: Failed to set temp basal");
        return result;
    }

    @NonNull @Override
    public synchronized PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
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
            result.comment = resourceHelper.gs(R.string.virtualpump_resultok);
            result.duration = pump.getExtendedBolusRemainingMinutes();
            result.absolute = pump.getExtendedBolusAbsoluteRate();
            result.isPercent = false;
            result.isTempCancel = false;
            getAapsLogger().debug(LTag.PUMP, "setExtendedBolus: Correct extended bolus already set. Current: " + pump.getExtendedBolusAmount() + " Asked: " + insulin);
            return result;
        }
        boolean connectionOK = danaRSService.extendedBolus(insulin, durationInHalfHours);
        if (connectionOK && pump.isExtendedInProgress() && Math.abs(pump.getExtendedBolusAbsoluteRate() - insulin) < getPumpDescription().extendedBolusStep) {
            result.enacted = true;
            result.success = true;
            result.comment = resourceHelper.gs(R.string.virtualpump_resultok);
            result.isTempCancel = false;
            result.duration = pump.getExtendedBolusRemainingMinutes();
            result.absolute = pump.getExtendedBolusAbsoluteRate();
            result.bolusDelivered = pump.getExtendedBolusAmount();
            result.isPercent = false;
            getAapsLogger().debug(LTag.PUMP, "setExtendedBolus: OK");
            return result;
        }
        result.enacted = false;
        result.success = false;
        result.comment = resourceHelper.gs(R.string.danar_valuenotsetproperly);
        getAapsLogger().error("setExtendedBolus: Failed to extended bolus");
        return result;
    }

    @NonNull @Override
    public synchronized PumpEnactResult cancelTempBasal(boolean force) {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        TemporaryBasal runningTB = treatmentsPlugin.getTempBasalFromHistory(System.currentTimeMillis());
        if (runningTB != null) {
            danaRSService.tempBasalStop();
            result.enacted = true;
            result.isTempCancel = true;
        }
        if (!danaRPump.isTempBasalInProgress()) {
            result.success = true;
            result.isTempCancel = true;
            result.comment = resourceHelper.gs(R.string.virtualpump_resultok);
            getAapsLogger().debug(LTag.PUMP, "cancelRealTempBasal: OK");
            return result;
        } else {
            result.success = false;
            result.comment = resourceHelper.gs(R.string.danar_valuenotsetproperly);
            result.isTempCancel = true;
            getAapsLogger().error("cancelRealTempBasal: Failed to cancel temp basal");
            return result;
        }
    }

    @NonNull @Override
    public synchronized PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        ExtendedBolus runningEB = treatmentsPlugin.getExtendedBolusFromHistory(System.currentTimeMillis());
        if (runningEB != null) {
            danaRSService.extendedBolusStop();
            result.enacted = true;
            result.isTempCancel = true;
        }
        if (!danaRPump.isExtendedInProgress()) {
            result.success = true;
            result.comment = resourceHelper.gs(R.string.virtualpump_resultok);
            getAapsLogger().debug(LTag.PUMP, "cancelExtendedBolus: OK");
            return result;
        } else {
            result.success = false;
            result.comment = resourceHelper.gs(R.string.danar_valuenotsetproperly);
            getAapsLogger().error("cancelExtendedBolus: Failed to cancel extended bolus");
            return result;
        }
    }

    @NonNull @Override
    public JSONObject getJSONStatus(Profile profile, String profileName) {
        DanaRPump pump = danaRPump;
        long now = System.currentTimeMillis();
        if (pump.getLastConnection() + 5 * 60 * 1000L < System.currentTimeMillis()) {
            return new JSONObject();
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
                extended.put("LastBolus", dateUtil.dateAndTimeString(pump.getLastBolusTime()));
                extended.put("LastBolusAmount", pump.getLastBolusAmount());
            }
            TemporaryBasal tb = treatmentsPlugin.getTempBasalFromHistory(now);
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.tempBasalConvertedToAbsolute(now, profile));
                extended.put("TempBasalStart", dateUtil.dateAndTimeString(tb.date));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }
            ExtendedBolus eb = treatmentsPlugin.getExtendedBolusFromHistory(now);
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.absoluteRate());
                extended.put("ExtendedBolusStart", dateUtil.dateAndTimeString(eb.date));
                extended.put("ExtendedBolusRemaining", eb.getPlannedRemainingMinutes());
            }
            extended.put("BaseBasalRate", getBaseBasalRate());
            try {
                extended.put("ActiveProfile", profileFunction.getProfileName());
            } catch (Exception e) {
                getAapsLogger().error("Unhandled exception", e);
            }

            pumpjson.put("battery", battery);
            pumpjson.put("status", status);
            pumpjson.put("extended", extended);
            pumpjson.put("reservoir", (int) pump.getReservoirRemainingUnits());
            pumpjson.put("clock", DateUtil.toISOString(now));
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
    public PumpType model() {
        return PumpType.DanaRS;
    }

    @NonNull @Override
    public String serialNumber() {
        return danaRPump.getSerialNumber();
    }

    @NonNull @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @NonNull @Override
    public String shortStatus(boolean veryShort) {
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

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @NonNull @Override
    public PumpEnactResult loadTDDs() {
        return loadHistory(RecordTypes.RECORD_TYPE_DAILY);
    }

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
    public void timezoneOrDSTChanged(TimeChangeType changeType) {
    }

}
