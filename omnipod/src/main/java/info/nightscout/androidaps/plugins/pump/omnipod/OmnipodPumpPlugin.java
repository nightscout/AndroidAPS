package info.nightscout.androidaps.plugins.pump.omnipod;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.activities.ErrorHelperActivity;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventAppInitialized;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.PumpPluginBase;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpInfo;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoRecentPulseLog;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCustomActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodPumpPluginInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodStatusRequest;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.comm.AapsOmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.ui.OmnipodUIComm;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.ui.OmnipodUITask;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodRefreshButtonState;
import info.nightscout.androidaps.plugins.pump.omnipod.service.RileyLinkOmnipodService;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.TimeChangeType;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by andy on 23.04.18.
 *
 * @author Andy Rozman (andy.rozman@gmail.com)
 */
@Singleton
public class OmnipodPumpPlugin extends PumpPluginBase implements PumpInterface, OmnipodPumpPluginInterface, RileyLinkPumpDevice {
    private final PodStateManager podStateManager;
    private final RileyLinkServiceData rileyLinkServiceData;
    private final ServiceTaskExecutor serviceTaskExecutor;
    private final OmnipodPumpStatus omnipodPumpStatus;
    private final AapsOmnipodManager aapsOmnipodManager;
    private final OmnipodUtil omnipodUtil;
    private final AAPSLogger aapsLogger;
    private final RxBusWrapper rxBus;
    private final ActivePluginProvider activePlugin;
    private final Context context;
    private final FabricPrivacy fabricPrivacy;
    private final ResourceHelper resourceHelper;
    private final SP sp;
    private final DateUtil dateUtil;
    private final PumpDescription pumpDescription;
    private final ServiceConnection serviceConnection;
    private final PumpType pumpType = PumpType.Insulet_Omnipod; // FIXME

    private final List<CustomAction> customActions = new ArrayList<>();
    private final List<OmnipodStatusRequest> omnipodStatusRequestList = new ArrayList<>();
    private final CompositeDisposable disposables = new CompositeDisposable();

    // variables for handling statuses and history
    protected boolean firstRun = true;
    protected boolean isRefresh = false;
    protected boolean hasTimeDateOrTimeZoneChanged = false;
    protected boolean serviceRunning = false;
    protected boolean displayConnectionMessages = false;
    private boolean isInitialized = false;
    private RileyLinkOmnipodService rileyLinkOmnipodService;
    private boolean busy = false;
    private int timeChangeRetries = 0;
    private Profile currentProfile;
    private long nextPodCheck = 0L;
    private boolean sentIdToFirebase;

    @Inject
    public OmnipodPumpPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            Context context,
            ResourceHelper resourceHelper,
            ActivePluginProvider activePlugin,
            SP sp,
            OmnipodPumpStatus omnipodPumpStatus,
            PodStateManager podStateManager,
            AapsOmnipodManager aapsOmnipodManager,
            CommandQueueProvider commandQueue,
            FabricPrivacy fabricPrivacy,
            RileyLinkServiceData rileyLinkServiceData,
            ServiceTaskExecutor serviceTaskExecutor,
            DateUtil dateUtil,
            OmnipodUtil omnipodUtil
    ) {
        super(new PluginDescription() //
                        .mainType(PluginType.PUMP) //
                        .fragmentClass(OmnipodFragment.class.getName()) //
                        .pluginName(R.string.omnipod_name) //
                        .shortName(R.string.omnipod_name_short) //
                        .preferencesId(R.xml.pref_omnipod) //
                        .description(R.string.description_pump_omnipod), //
                injector, aapsLogger, resourceHelper, commandQueue);
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.activePlugin = activePlugin;
        this.context = context;
        this.fabricPrivacy = fabricPrivacy;
        this.resourceHelper = resourceHelper;
        this.sp = sp;
        this.dateUtil = dateUtil;
        this.podStateManager = podStateManager;
        this.rileyLinkServiceData = rileyLinkServiceData;
        this.serviceTaskExecutor = serviceTaskExecutor;
        this.omnipodPumpStatus = omnipodPumpStatus;
        this.aapsOmnipodManager = aapsOmnipodManager;
        this.omnipodUtil = omnipodUtil;

        pumpDescription = new PumpDescription(pumpType);

        customActions.add(new CustomAction(
                R.string.omnipod_custom_action_reset_rileylink, OmnipodCustomActionType.ResetRileyLinkConfiguration, true));

        this.serviceConnection = new ServiceConnection() {

            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                aapsLogger.debug(LTag.PUMP, "RileyLinkOmnipodService is connected");
                RileyLinkOmnipodService.LocalBinder mLocalBinder = (RileyLinkOmnipodService.LocalBinder) service;
                rileyLinkOmnipodService = mLocalBinder.getServiceInstance();
                rileyLinkOmnipodService.verifyConfiguration();

                new Thread(() -> {

                    for (int i = 0; i < 20; i++) {
                        SystemClock.sleep(5000);

                        aapsLogger.debug(LTag.PUMP, "Starting Omnipod-RileyLink service");
                        if (rileyLinkOmnipodService.setNotInPreInit()) {
                            break;
                        }
                    }
                }).start();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                aapsLogger.debug(LTag.PUMP, "RileyLinkOmnipodService is disconnected");
                rileyLinkOmnipodService = null;
            }
        };
    }

    public PodStateManager getPodStateManager() {
        return podStateManager;
    }

    @Override
    protected void onStart() {
        super.onStart();

        // We can't do this in PodStateManager itself, because JodaTimeAndroid.init() hasn't been called yet
        // When PodStateManager is created, which causes an IllegalArgumentException for DateTimeZones not being recognized
        // TODO either find a more elegant solution, or at least make sure this is the right place to do this
        podStateManager.loadPodState();

        initPumpStatusData();

        Intent intent = new Intent(context, RileyLinkOmnipodService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        serviceRunning = true;

        disposables.add(rxBus
                .toObservable(EventAppExit.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> context.unbindService(serviceConnection), fabricPrivacy::logException)
        );
        disposables.add(rxBus
                .toObservable(EventPreferenceChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if ((event.isChanged(getResourceHelper(), R.string.key_omnipod_beep_basal_enabled)) ||
                            (event.isChanged(getResourceHelper(), R.string.key_omnipod_beep_bolus_enabled)) ||
                            (event.isChanged(getResourceHelper(), R.string.key_omnipod_beep_tbr_enabled)) ||
                            (event.isChanged(getResourceHelper(), R.string.key_omnipod_pod_debugging_options_enabled)) ||
                            (event.isChanged(getResourceHelper(), R.string.key_omnipod_beep_smb_enabled)) ||
                            (event.isChanged(getResourceHelper(), R.string.key_omnipod_timechange_enabled)))
                        aapsOmnipodManager.reloadSettings();
                }, fabricPrivacy::logException)
        );
        disposables.add(rxBus
                .toObservable(EventAppInitialized.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    // See if a bolus was active before the app previously exited
                    // If so, add it to history
                    // Needs to be done after EventAppInitialized because otherwise, TreatmentsPlugin.onStart() hasn't been called yet
                    // so it didn't initialize a TreatmentService yet, resulting in a NullPointerException
                    if (sp.contains(OmnipodConst.Prefs.CurrentBolus)) {
                        String currentBolusString = sp.getString(OmnipodConst.Prefs.CurrentBolus, "");
                        aapsLogger.warn(LTag.PUMP, "Found active bolus in SP. Adding Treatment: {}", currentBolusString);
                        try {
                            DetailedBolusInfo detailedBolusInfo = omnipodUtil.getGsonInstance().fromJson(currentBolusString, DetailedBolusInfo.class);
                            aapsOmnipodManager.addBolusToHistory(detailedBolusInfo);
                        } catch (Exception ex) {
                            aapsLogger.error(LTag.PUMP, "Failed to add active bolus to history", ex);
                        }
                        sp.remove(OmnipodConst.Prefs.CurrentBolus);
                    }
                }, fabricPrivacy::logException)
        );

        // check status every minute (if any status needs refresh we send readStatus command)
        new Thread(() -> {
            do {
                SystemClock.sleep(60000);

                if (!this.omnipodStatusRequestList.isEmpty() || this.hasTimeDateOrTimeZoneChanged) {
                    if (!getCommandQueue().statusInQueue()) {
                        getCommandQueue().readStatus("Status Refresh Requested", null);
                    }
                }

                doPodCheck();

            } while (serviceRunning);

        }).start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        aapsLogger.debug(LTag.PUMP, "OmnipodPumpPlugin.onStop()");

        context.unbindService(serviceConnection);

        serviceRunning = false;

        disposables.clear();
    }

    private String getLogPrefix() {
        return "OmnipodPlugin::";
    }

    // FIXME remove
    public void initPumpStatusData() {
        omnipodPumpStatus.lastConnection = sp.getLong(RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);
        omnipodPumpStatus.lastDataTime = omnipodPumpStatus.lastConnection;
        omnipodPumpStatus.previousConnection = omnipodPumpStatus.lastConnection;

        aapsLogger.debug(LTag.PUMP, "initPumpStatusData: " + this.omnipodPumpStatus);
    }

    private void doPodCheck() {
        if (System.currentTimeMillis() > this.nextPodCheck) {
            if (!podStateManager.isPodRunning()) {
                Notification notification = new Notification(Notification.OMNIPOD_POD_NOT_ATTACHED, resourceHelper.gs(R.string.omnipod_error_pod_not_attached), Notification.NORMAL);
                rxBus.send(new EventNewNotification(notification));
            } else {
                rxBus.send(new EventDismissNotification(Notification.OMNIPOD_POD_NOT_ATTACHED));
            }

            this.nextPodCheck = DateTimeUtil.getTimeInFutureFromMinutes(15);
        }
    }

    public PumpStatus getPumpStatusData() {
        return this.omnipodPumpStatus;
    }

    private boolean isServiceSet() {
        return rileyLinkOmnipodService != null;
    }

    @Override
    public boolean isInitialized() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, getLogPrefix() + "isInitialized");
        return isServiceSet() && isInitialized;
    }

    @Override
    public boolean isSuspended() {
        return !podStateManager.isPodRunning() || podStateManager.isSuspended();
    }

    @Override
    public boolean isBusy() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, getLogPrefix() + "isBusy");

        if (isServiceSet()) {
            return busy || !podStateManager.isPodRunning();
        }

        return false;
    }

    @Override public void setBusy(boolean busy) {
        this.busy = busy;
    }

    @Override
    public void triggerPumpConfigurationChangedEvent() {
        rxBus.send(new EventOmnipodPumpValuesChanged());
        rxBus.send(new EventRileyLinkDeviceStatusChange());
    }

    @Override
    public RileyLinkOmnipodService getRileyLinkService() {
        return rileyLinkOmnipodService;
    }

    @Override public RileyLinkPumpInfo getPumpInfo() {
        String pumpDescription = "Eros";
        String frequency = resourceHelper.gs(R.string.omnipod_frequency);
        String connectedModel = podStateManager.isPodInitialized() ? "Eros Pod" : "-";
        String serialNumber = podStateManager.isPodInitialized() ? String.valueOf(podStateManager.getAddress()) : "-";
        return new RileyLinkPumpInfo(pumpDescription, frequency, connectedModel, serialNumber);
    }

    /**
     * Returns the last successful RileyLink communication
     * For actual communication with the Pod, we use PodStateManager
     */
    @Override public long getLastConnectionTimeMillis() {
        return omnipodPumpStatus.lastConnection;
    }

    /**
     * Only use for setting last successful RileyLink communication to now
     * For actual communication with the Pod, we use PodStateManager
     */
    @Override public void setLastCommunicationToNow() {
        omnipodPumpStatus.setLastCommunicationToNow();
    }

    @Override
    public boolean isConnected() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, getLogPrefix() + "isConnected");
        return isServiceSet() && rileyLinkOmnipodService.isInitialized();
    }

    @Override
    public boolean isConnecting() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, getLogPrefix() + "isConnecting");
        return !isServiceSet() || !rileyLinkOmnipodService.isInitialized();
    }

    @Override
    public boolean isHandshakeInProgress() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "isHandshakeInProgress [OmnipodPumpPlugin] - default (empty) implementation.");
        return false;
    }

    @Override
    public void finishHandshaking() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "finishHandshaking [OmnipodPumpPlugin] - default (empty) implementation.");
    }

    @Override public void connect(String reason) {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "connect (reason={}) [PumpPluginAbstract] - default (empty) implementation." + reason);
    }

    @Override public void disconnect(String reason) {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "disconnect (reason={}) [PumpPluginAbstract] - default (empty) implementation." + reason);
    }

    @Override public void stopConnecting() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "stopConnecting [PumpPluginAbstract] - default (empty) implementation.");
    }

    @Override
    public void getPumpStatus() {
        if (firstRun) {
            initializePump(!isRefresh);
            triggerUIChange();

        } else if (!omnipodStatusRequestList.isEmpty()) {
            List<OmnipodStatusRequest> removeList = new ArrayList<>();

            for (OmnipodStatusRequest omnipodStatusRequest : omnipodStatusRequestList) {
                if (omnipodStatusRequest == OmnipodStatusRequest.GetPodPulseLog) {
                    OmnipodUITask omnipodUITask = getDeviceCommandExecutor().executeCommand(omnipodStatusRequest.getCommandType());

                    PodInfoRecentPulseLog result = (PodInfoRecentPulseLog) omnipodUITask.returnDataObject;

                    if (result == null) {
                        aapsLogger.warn(LTag.PUMP, "Result was null.");
                    } else {
                        aapsLogger.warn(LTag.PUMP, "Result was NOT null.");

                        Intent i = new Intent(context, ErrorHelperActivity.class);
                        i.putExtra("soundid", 0);
                        i.putExtra("status", "Pulse Log (copied to clipboard):\n" + result.toString());
                        i.putExtra("title", resourceHelper.gs(R.string.omnipod_warning));
                        i.putExtra("clipboardContent", result.toString());
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(i);
                    }

                } else {
                    getDeviceCommandExecutor().executeCommand(omnipodStatusRequest.getCommandType());
                }
                removeList.add(omnipodStatusRequest);
            }

            omnipodStatusRequestList.removeAll(removeList);

        } else if (this.hasTimeDateOrTimeZoneChanged) {
            OmnipodUITask omnipodUITask = getDeviceCommandExecutor().executeCommand(OmnipodCommandType.SetTime);

            if (omnipodUITask.wasCommandSuccessful()) {
                this.hasTimeDateOrTimeZoneChanged = false;
                timeChangeRetries = 0;

                Notification notification = new Notification(
                        Notification.TIME_OR_TIMEZONE_CHANGE,
                        resourceHelper.gs(R.string.omnipod_time_or_timezone_change),
                        Notification.INFO, 60);
                rxBus.send(new EventNewNotification(notification));

            } else {
                timeChangeRetries++;

                if (timeChangeRetries > 3) {
                    this.hasTimeDateOrTimeZoneChanged = false;
                    timeChangeRetries = 0;
                }
            }
        }
    }

    @NotNull
    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        aapsLogger.info(LTag.PUMP, getLogPrefix() + "setNewBasalProfile");

        // this shouldn't be needed, but let's do check if profile setting we are setting is same as current one
        if (this.currentProfile != null && this.currentProfile.areProfileBasalPatternsSame(profile)) {
            return new PumpEnactResult(getInjector()) //
                    .success(true) //
                    .enacted(false) //
                    .comment(resourceHelper.gs(R.string.omnipod_cmd_basal_profile_not_set_is_same));
        }

        setRefreshButtonEnabled(false);

        OmnipodUITask responseTask = getDeviceCommandExecutor().executeCommand(OmnipodCommandType.SetBasalProfile,
                profile);

        PumpEnactResult result = responseTask.getResult();

        aapsLogger.info(LTag.PUMP, getLogPrefix() + "Basal Profile was set: " + result.success);

        if (result.success) {
            this.currentProfile = profile;

            Notification notification = new Notification(Notification.PROFILE_SET_OK,
                    resourceHelper.gs(R.string.profile_set_ok),
                    Notification.INFO, 60);
            rxBus.send(new EventNewNotification(notification));
        } else {
            Notification notification = new Notification(Notification.FAILED_UDPATE_PROFILE,
                    resourceHelper.gs(R.string.failedupdatebasalprofile),
                    Notification.URGENT);
            rxBus.send(new EventNewNotification(notification));
        }

        return result;
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        // TODO status was not yet read from pod
        // TODO maybe not possible, need to see how we will handle that
        if (currentProfile == null) {
            this.currentProfile = profile;
            return true;
        }

        return (currentProfile.areProfileBasalPatternsSame(profile));
    }

    @Override
    public long lastDataTime() {
        if (omnipodPumpStatus.lastConnection != 0) {
            return omnipodPumpStatus.lastConnection;
        }

        return System.currentTimeMillis();
    }

    @Override
    public double getBaseBasalRate() {
        if (currentProfile != null) {
            int hour = (new GregorianCalendar()).get(Calendar.HOUR_OF_DAY);
            return currentProfile.getBasalTimeFromMidnight(DateTimeUtil.getTimeInS(hour * 60));
        } else {
            return 0.0d;
        }
    }

    @Override
    public double getReservoirLevel() {
        return omnipodPumpStatus.reservoirRemainingUnits;
    }

    @Override
    public int getBatteryLevel() {
        return 75;
    }

    @NonNull @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {

        try {
            if (detailedBolusInfo.insulin == 0 && detailedBolusInfo.carbs == 0) {
                // neither carbs nor bolus requested
                aapsLogger.error("deliverTreatment: Invalid input");
                return new PumpEnactResult(getInjector()).success(false).enacted(false).bolusDelivered(0d).carbsDelivered(0d)
                        .comment(getResourceHelper().gs(info.nightscout.androidaps.core.R.string.invalidinput));
            } else if (detailedBolusInfo.insulin > 0) {
                // bolus needed, ask pump to deliver it
                return deliverBolus(detailedBolusInfo);
            } else {
                // no bolus required, carb only treatment
                activePlugin.getActiveTreatments().addToHistoryTreatment(detailedBolusInfo, true);

                EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.INSTANCE;
                bolusingEvent.setT(new Treatment());
                bolusingEvent.getT().isSMB = detailedBolusInfo.isSMB;
                bolusingEvent.setPercent(100);
                rxBus.send(bolusingEvent);

                aapsLogger.debug(LTag.PUMP, "deliverTreatment: Carb only treatment.");

                return new PumpEnactResult(getInjector()).success(true).enacted(true).bolusDelivered(0d)
                        .carbsDelivered(detailedBolusInfo.carbs).comment(getResourceHelper().gs(info.nightscout.androidaps.core.R.string.common_resultok));
            }
        } finally {
            triggerUIChange();
        }
    }

    @Override
    public void stopBolusDelivering() {
        aapsLogger.info(LTag.PUMP, getLogPrefix() + "stopBolusDelivering");

        setRefreshButtonEnabled(false);

        OmnipodUITask responseTask = getDeviceCommandExecutor().executeCommand(OmnipodCommandType.CancelBolus);

        PumpEnactResult result = responseTask.getResult();

        //setRefreshButtonEnabled(true);

        aapsLogger.info(LTag.PUMP, getLogPrefix() + "stopBolusDelivering - wasSuccess={}", result.success);

        //finishAction("Bolus");
    }

    // if enforceNew===true current temp basal is canceled and new TBR set (duration is prolonged),
    // if false and the same rate is requested enacted=false and success=true is returned and TBR is not changed
    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        setRefreshButtonEnabled(false);

        aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute: rate: {}, duration={}", absoluteRate, durationInMinutes);

        // read current TBR
        TempBasalPair tbrCurrent = readTBR();

        if (tbrCurrent != null) {
            aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute: Current Basal: duration: {} min, rate={}",
                    tbrCurrent.getDurationMinutes(), tbrCurrent.getInsulinRate());
        }

        if (tbrCurrent != null && !enforceNew) {
            if (Round.isSame(tbrCurrent.getInsulinRate(), absoluteRate)) {
                aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - No enforceNew and same rate. Exiting.");
                finishAction("TBR");
                return new PumpEnactResult(getInjector()).success(true).enacted(false);
            }
        }

        // now start new TBR
        OmnipodUITask responseTask = getDeviceCommandExecutor().executeCommand(OmnipodCommandType.SetTemporaryBasal,
                absoluteRate, durationInMinutes);

        PumpEnactResult result = responseTask.getResult();

        aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - setTBR. Response: " + result.success);

        if (result.success) {
            incrementStatistics(OmnipodConst.Statistics.TBRsSet);
        }

        finishAction("TBR");
        return result;
    }

    @NotNull @Override public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        aapsLogger.debug(LTag.PUMP, "setTempBasalPercent [OmnipodPumpPlugin] - Not implemented.");
        return getOperationNotSupportedWithCustomText(info.nightscout.androidaps.core.R.string.pump_operation_not_supported_by_pump_driver);
    }

    @NotNull @Override public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        aapsLogger.debug(LTag.PUMP, "setExtendedBolus [OmnipodPumpPlugin] - Not implemented.");
        return getOperationNotSupportedWithCustomText(info.nightscout.androidaps.core.R.string.pump_operation_not_supported_by_pump_driver);
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        aapsLogger.info(LTag.PUMP, getLogPrefix() + "cancelTempBasal - started");

        setRefreshButtonEnabled(false);

        TempBasalPair tbrCurrent = readTBR();

        if (tbrCurrent == null) {
            aapsLogger.info(LTag.PUMP, getLogPrefix() + "cancelTempBasal - TBR already canceled.");
            finishAction("TBR");
            return new PumpEnactResult(getInjector()).success(true).enacted(false);
        }

        OmnipodUITask responseTask2 = getDeviceCommandExecutor().executeCommand(OmnipodCommandType.CancelTemporaryBasal);

        PumpEnactResult result = responseTask2.getResult();

        finishAction("TBR");

        if (result.success) {
            aapsLogger.info(LTag.PUMP, getLogPrefix() + "cancelTempBasal - Cancel TBR successful.");

            TemporaryBasal tempBasal = new TemporaryBasal() //
                    .date(System.currentTimeMillis()) //
                    .duration(0) //
                    .source(Source.USER);

            activePlugin.getActiveTreatments().addToHistoryTempBasal(tempBasal);
        } else {
            aapsLogger.info(LTag.PUMP, getLogPrefix() + "cancelTempBasal - Cancel TBR failed.");
        }

        return result;
    }

    @NotNull @Override public PumpEnactResult cancelExtendedBolus() {
        aapsLogger.debug(LTag.PUMP, "cancelExtendedBolus [OmnipodPumpPlugin] - Not implemented.");
        return getOperationNotSupportedWithCustomText(info.nightscout.androidaps.core.R.string.pump_operation_not_supported_by_pump_driver);
    }

    @NonNull @Override
    public JSONObject getJSONStatus(Profile profile, String profileName, String version) {

        if ((getPumpStatusData().lastConnection + 60 * 60 * 1000L) < System.currentTimeMillis()) {
            return new JSONObject();
        }

        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", getPumpStatusData().batteryRemaining);
            status.put("status", getPumpStatusData().pumpStatusType != null ? getPumpStatusData().pumpStatusType.getStatus() : "normal");
            extended.put("Version", version);
            try {
                extended.put("ActiveProfile", profileName);
            } catch (Exception ignored) {
            }

            TemporaryBasal tb = activePlugin.getActiveTreatments().getTempBasalFromHistory(System.currentTimeMillis());
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate",
                        tb.tempBasalConvertedToAbsolute(System.currentTimeMillis(), profile));
                extended.put("TempBasalStart", dateUtil.dateAndTimeString(tb.date));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }

            ExtendedBolus eb = activePlugin.getActiveTreatments().getExtendedBolusFromHistory(System.currentTimeMillis());
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.absoluteRate());
                extended.put("ExtendedBolusStart", dateUtil.dateAndTimeString(eb.date));
                extended.put("ExtendedBolusRemaining", eb.getPlannedRemainingMinutes());
            }

            status.put("timestamp", DateUtil.toISOString(new Date()));

            pump.put("battery", battery);
            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("reservoir", getPumpStatusData().reservoirRemainingUnits);
            pump.put("clock", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return pump;
    }

    @Override public ManufacturerType manufacturer() {
        return pumpType.getManufacturer();
    }

    @Override @NotNull
    public PumpType model() {
        return pumpType;
    }

    @NotNull
    @Override
    public String serialNumber() {
        return podStateManager.isPodInitialized() ? String.valueOf(podStateManager.getAddress()) : "-";
    }

    @Override @NonNull public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    // FIXME i18n, null checks: iob, TDD
    @NonNull @Override
    public String shortStatus(boolean veryShort) {
        String ret = "";
        if (getPumpStatusData().lastConnection != 0) {
            long agoMsec = System.currentTimeMillis() - getPumpStatusData().lastConnection;
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += "LastConn: " + agoMin + " min ago\n";
        }
        if (getPumpStatusData().lastBolusTime != null && getPumpStatusData().lastBolusTime.getTime() != 0) {
            ret += "LastBolus: " + DecimalFormatter.to2Decimal(getPumpStatusData().lastBolusAmount) + "U @" + //
                    android.text.format.DateFormat.format("HH:mm", getPumpStatusData().lastBolusTime) + "\n";
        }
        TemporaryBasal activeTemp = activePlugin.getActiveTreatments().getRealTempBasalFromHistory(System.currentTimeMillis());
        if (activeTemp != null) {
            ret += "Temp: " + activeTemp.toStringFull() + "\n";
        }
        ExtendedBolus activeExtendedBolus = activePlugin.getActiveTreatments().getExtendedBolusFromHistory(
                System.currentTimeMillis());
        if (activeExtendedBolus != null) {
            ret += "Extended: " + activeExtendedBolus.toString() + "\n";
        }
        ret += "IOB: " + getPumpStatusData().iob + "U\n";
        ret += "Reserv: " + DecimalFormatter.to0Decimal(getPumpStatusData().reservoirRemainingUnits) + "U\n";
        ret += "Batt: " + getPumpStatusData().batteryRemaining + "\n";
        return ret;
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @NotNull @Override public PumpEnactResult loadTDDs() {
        aapsLogger.debug(LTag.PUMP, "loadTDDs [OmnipodPumpPlugin] - Not implemented.");
        return getOperationNotSupportedWithCustomText(info.nightscout.androidaps.core.R.string.pump_operation_not_supported_by_pump_driver);
    }

    @Override public boolean canHandleDST() {
        return false;
    }

    @Override
    public List<CustomAction> getCustomActions() {
        return customActions;
    }

    @Override
    public void executeCustomAction(CustomActionType customActionType) {
        OmnipodCustomActionType mcat = (OmnipodCustomActionType) customActionType;

        switch (mcat) {
            case ResetRileyLinkConfiguration: {
                serviceTaskExecutor.startTask(new ResetRileyLinkConfigurationTask(getInjector()));
            }
            break;

            default:
                break;
        }
    }

    @Override
    public void timezoneOrDSTChanged(TimeChangeType timeChangeType) {
        aapsLogger.warn(LTag.PUMP, getLogPrefix() + "Time, Date and/or TimeZone changed. [changeType=" + timeChangeType.name() + ", eventHandlingEnabled=" + aapsOmnipodManager.isTimeChangeEventEnabled() + "]");

        if (aapsOmnipodManager.isTimeChangeEventEnabled() && podStateManager.isPodRunning()) {
            aapsLogger.info(LTag.PUMP, getLogPrefix() + "Time,and/or TimeZone changed event received and will be consumed by driver.");
            this.hasTimeDateOrTimeZoneChanged = true;
        }
    }

    @Override
    public boolean isUnreachableAlertTimeoutExceeded(long unreachableTimeoutMilliseconds) {
        long rileyLinkInitializationTimeout = 3 * 60 * 1000L; // 3 minutes
        if (podStateManager.isPodRunning() && podStateManager.getLastSuccessfulCommunication() != null) { // Null check for backwards compatibility
            if (podStateManager.getLastSuccessfulCommunication().getMillis() + unreachableTimeoutMilliseconds < System.currentTimeMillis()) {
                // We exceeded the alert threshold, and either our last command failed or we cannot reach the RL
                // We should show an alert
                return (podStateManager.getLastFailedCommunication() != null && podStateManager.getLastSuccessfulCommunication().isBefore(podStateManager.getLastFailedCommunication())) ||
                        rileyLinkServiceData.rileyLinkServiceState.isError() ||
                        // The below clause is a hack for working around the RL service state forever staying in connecting state on startup if the RL is switched off / unreachable
                        (rileyLinkServiceData.getRileyLinkServiceState().isConnecting() && rileyLinkServiceData.getLastServiceStateChange() + rileyLinkInitializationTimeout < System.currentTimeMillis());

                // Don't trigger an alert when we exceeded the thresholds, but the last communication was successful & the RL is reachable
                // This happens when we simply didn't need to send any commands to the pump
            }
        }

        return false;
    }

    public OmnipodUIComm getDeviceCommandExecutor() {
        return rileyLinkOmnipodService.getDeviceCommandExecutor();
    }

    private void getPodPumpStatus() {
        // TODO read pod status
        aapsLogger.error(LTag.PUMP, "getPodPumpStatus() NOT IMPLEMENTED");
    }

    @Override public void addPodStatusRequest(OmnipodStatusRequest pumpStatusRequest) {
        if (pumpStatusRequest == OmnipodStatusRequest.ResetState) {
            resetStatusState();
        } else {
            omnipodStatusRequestList.add(pumpStatusRequest);
        }
    }

    public void resetStatusState() {
        firstRun = true;
        isRefresh = true;
    }

    // FIXME do we actually need this? If a user presses refresh during an action,
    //  I suppose the GetStatusCommand would just be queued?
    private void setRefreshButtonEnabled(boolean enabled) {
        rxBus.send(new EventOmnipodRefreshButtonState(enabled));
    }

    private void initializePump(boolean realInit) {
        aapsLogger.info(LTag.PUMP, getLogPrefix() + "initializePump - start");

        setRefreshButtonEnabled(false);

        if (podStateManager.isPodInitialized()) {
            aapsLogger.debug(LTag.PUMP, "PodStateManager (saved): " + podStateManager);
            // TODO handle if session state too old
            getPodPumpStatus();
        } else {
            aapsLogger.debug(LTag.PUMP, "No Pod running");
        }

        finishAction("Omnipod Pump");

        if (!sentIdToFirebase) {
            Bundle params = new Bundle();
            params.putString("version", BuildConfig.VERSION);

            fabricPrivacy.getFirebaseAnalytics().logEvent("OmnipodPumpInit", params);

            sentIdToFirebase = true;
        }

        isInitialized = true;

        this.firstRun = false;
    }

    protected void triggerUIChange() {
        rxBus.send(new EventOmnipodPumpValuesChanged());
    }

    @NonNull
    protected PumpEnactResult deliverBolus(final DetailedBolusInfo detailedBolusInfo) {
        aapsLogger.info(LTag.PUMP, getLogPrefix() + "deliverBolus - {}", detailedBolusInfo);

        setRefreshButtonEnabled(false);

        try {
            OmnipodUITask responseTask = getDeviceCommandExecutor().executeCommand(OmnipodCommandType.SetBolus,
                    detailedBolusInfo);

            PumpEnactResult result = responseTask.getResult();

            setRefreshButtonEnabled(true);

            if (result.success) {
                incrementStatistics(detailedBolusInfo.isSMB ? OmnipodConst.Statistics.SMBBoluses
                        : OmnipodConst.Statistics.StandardBoluses);

                result.carbsDelivered(detailedBolusInfo.carbs);
            }

            return result;
        } finally {
            finishAction("Bolus");
        }
    }

    private void incrementStatistics(String statsKey) {
        long currentCount = sp.getLong(statsKey, 0L);
        currentCount++;
        sp.putLong(statsKey, currentCount);
    }

    protected TempBasalPair readTBR() {
        // TODO we can do it like this or read status from pod ??
        if (omnipodPumpStatus.tempBasalEnd < System.currentTimeMillis()) {
            // TBR done
            omnipodPumpStatus.clearTemporaryBasal();

            return null;
        }

        return omnipodPumpStatus.getTemporaryBasal();
    }

    protected void finishAction(String overviewKey) {
        if (overviewKey != null)
            rxBus.send(new EventRefreshOverview(overviewKey, false));

        triggerUIChange();

        setRefreshButtonEnabled(true);
    }

    private PumpEnactResult getOperationNotSupportedWithCustomText(int resourceId) {
        return new PumpEnactResult(getInjector()).success(false).enacted(false).comment(getResourceHelper().gs(resourceId));
    }

}
