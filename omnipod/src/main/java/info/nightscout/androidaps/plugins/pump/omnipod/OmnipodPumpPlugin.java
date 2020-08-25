package info.nightscout.androidaps.plugins.pump.omnipod;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.function.Supplier;

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
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpInfo;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.data.ActiveBolus;
import info.nightscout.androidaps.plugins.pump.omnipod.data.RLHistoryItemOmnipod;
import info.nightscout.androidaps.plugins.pump.omnipod.definition.OmnipodCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.definition.OmnipodCustomActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.definition.OmnipodStatusRequestType;
import info.nightscout.androidaps.plugins.pump.omnipod.definition.OmnipodStorageKeys;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo.PodInfoRecentPulseLog;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.event.EventOmnipodPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsOmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.RileyLinkOmnipodService;
import info.nightscout.androidaps.plugins.pump.omnipod.ui.OmnipodFragment;
import info.nightscout.androidaps.plugins.pump.omnipod.util.AapsOmnipodUtil;
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
public class OmnipodPumpPlugin extends PumpPluginBase implements PumpInterface, RileyLinkPumpDevice {
    private static final long RILEY_LINK_CONNECT_TIMEOUT_MILLIS = 3 * 60 * 1000L; // 3 minutes
    private static final long STATUS_CHECK_INTERVAL_MILLIS = 60 * 1000L; // 1 minute

    private final PodStateManager podStateManager;
    private final RileyLinkServiceData rileyLinkServiceData;
    private final ServiceTaskExecutor serviceTaskExecutor;
    private final AapsOmnipodManager aapsOmnipodManager;
    private final AapsOmnipodUtil aapsOmnipodUtil;
    private final RileyLinkUtil rileyLinkUtil;
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
    private final PumpType pumpType = PumpType.Insulet_Omnipod;

    private final List<CustomAction> customActions = new ArrayList<>();
    private final List<OmnipodStatusRequestType> statusRequestList = new ArrayList<>();
    private final CompositeDisposable disposables = new CompositeDisposable();

    // variables for handling statuses and history
    private boolean firstRun = true;
    private boolean hasTimeDateOrTimeZoneChanged = false;
    private final boolean displayConnectionMessages = false;
    private RileyLinkOmnipodService rileyLinkOmnipodService;
    private boolean busy = false;
    private int timeChangeRetries;
    private long nextPodCheck;
    private boolean sentIdToFirebase;
    private long lastConnectionTimeMillis;
    private final Handler loopHandler = new Handler();

    private final Runnable statusChecker;

    @Inject
    public OmnipodPumpPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            Context context,
            ResourceHelper resourceHelper,
            ActivePluginProvider activePlugin,
            SP sp,
            PodStateManager podStateManager,
            AapsOmnipodManager aapsOmnipodManager,
            CommandQueueProvider commandQueue,
            FabricPrivacy fabricPrivacy,
            RileyLinkServiceData rileyLinkServiceData,
            ServiceTaskExecutor serviceTaskExecutor,
            DateUtil dateUtil,
            AapsOmnipodUtil aapsOmnipodUtil,
            RileyLinkUtil rileyLinkUtil
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
        this.aapsOmnipodManager = aapsOmnipodManager;
        this.aapsOmnipodUtil = aapsOmnipodUtil;
        this.rileyLinkUtil = rileyLinkUtil;

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

        statusChecker = new Runnable() {
            @Override public void run() {
                if (!OmnipodPumpPlugin.this.statusRequestList.isEmpty() || OmnipodPumpPlugin.this.hasTimeDateOrTimeZoneChanged) {
                    if (!getCommandQueue().statusInQueue()) {
                        getCommandQueue().readStatus(statusRequestList.isEmpty() ? "Date or Time Zone Changed" : "Status Refresh Requested", null);
                    }
                }

                doPodCheck();

                loopHandler.postDelayed(this, STATUS_CHECK_INTERVAL_MILLIS);
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();

        loopHandler.postDelayed(statusChecker, STATUS_CHECK_INTERVAL_MILLIS);

        // We can't do this in PodStateManager itself, because JodaTimeAndroid.init() hasn't been called yet
        // When PodStateManager is created, which causes an IllegalArgumentException for DateTimeZones not being recognized
        podStateManager.loadPodState();

        lastConnectionTimeMillis = sp.getLong(
                RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);

        Intent intent = new Intent(context, RileyLinkOmnipodService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

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
                    if (sp.contains(OmnipodStorageKeys.Prefs.ActiveBolus)) {
                        String activeBolusString = sp.getString(OmnipodStorageKeys.Prefs.ActiveBolus, "");
                        aapsLogger.warn(LTag.PUMP, "Found active bolus in SP: {}. Adding Treatment.", activeBolusString);
                        try {
                            ActiveBolus activeBolus = aapsOmnipodUtil.getGsonInstance().fromJson(activeBolusString, ActiveBolus.class);
                            aapsOmnipodManager.addBolusToHistory(activeBolus.toDetailedBolusInfo(aapsLogger));
                        } catch (Exception ex) {
                            aapsLogger.error(LTag.PUMP, "Failed to add active bolus to history", ex);
                        }
                        sp.remove(OmnipodStorageKeys.Prefs.ActiveBolus);
                    }
                }, fabricPrivacy::logException)
        );
    }

    @Override
    protected void onStop() {
        super.onStop();
        aapsLogger.debug(LTag.PUMP, "OmnipodPumpPlugin.onStop()");

        loopHandler.removeCallbacks(statusChecker);

        context.unbindService(serviceConnection);

        disposables.clear();
    }

    private void doPodCheck() {
        if (System.currentTimeMillis() > this.nextPodCheck) {
            if (!podStateManager.isPodRunning()) {
                Notification notification = new Notification(Notification.OMNIPOD_POD_NOT_ATTACHED, resourceHelper.gs(R.string.omnipod_error_pod_not_attached), Notification.NORMAL);
                rxBus.send(new EventNewNotification(notification));
            } else {
                rxBus.send(new EventDismissNotification(Notification.OMNIPOD_POD_NOT_ATTACHED));

                if (podStateManager.isSuspended()) {
                    Notification notification = new Notification(Notification.OMNIPOD_POD_SUSPENDED, resourceHelper.gs(R.string.omnipod_error_pod_suspended), Notification.NORMAL);
                    rxBus.send(new EventNewNotification(notification));
                } else {
                    rxBus.send(new EventDismissNotification(Notification.OMNIPOD_POD_SUSPENDED));
                }
            }

            this.nextPodCheck = DateTimeUtil.getTimeInFutureFromMinutes(15);
        }
    }

    // TODO is this correct?
    @Override
    public boolean isInitialized() {
        return isConnected() && podStateManager.isPodActivationCompleted();
    }

    @Override
    public boolean isConnected() {
        return rileyLinkOmnipodService != null && rileyLinkOmnipodService.isInitialized();
    }

    @Override
    public boolean isConnecting() {
        return rileyLinkOmnipodService == null || !rileyLinkOmnipodService.isInitialized();
    }

    @Override
    public boolean isHandshakeInProgress() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "isHandshakeInProgress [OmnipodPumpPlugin] - default (empty) implementation.");
        return false;
    }

    // TODO is this correct?
    @Override
    public boolean isBusy() {
        return busy || rileyLinkOmnipodService == null || !podStateManager.isPodRunning();
    }

    @Override public void setBusy(boolean busy) {
        this.busy = busy;
    }

    @Override
    public boolean isSuspended() {
        return !podStateManager.isPodRunning() || podStateManager.isSuspended();
    }

    @Override
    public void triggerPumpConfigurationChangedEvent() {
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

    // Required by RileyLinkPumpDevice interface.
    // Kind of redundant because we also store last successful and last failed communication in PodStateManager

    /**
     * Get the last communication time with the Pod. In the current implementation, this
     * doesn't have to mean that a command was successfully executed as the Pod could also return an ErrorResponse or PodFaultEvent
     * For getting the last time a command was successfully executed, use PodStateManager.getLastSuccessfulCommunication
     */
    @Override public long getLastConnectionTimeMillis() {
        return lastConnectionTimeMillis;
    }

    // Required by RileyLinkPumpDevice interface.
    // Kind of redundant because we also store last successful and last failed communication in PodStateManager

    /**
     * Set the last communication time with the Pod to now. In the current implementation, this
     * doesn't have to mean that a command was successfully executed as the Pod could also return an ErrorResponse or PodFaultEvent
     * For setting the last time a command was successfully executed, use PodStateManager.setLastSuccessfulCommunication
     */
    @Override public void setLastCommunicationToNow() {
        lastConnectionTimeMillis = System.currentTimeMillis();
    }

    // We abuse getPumpStatus to squeeze commands in the queue
    // The only actual status requests we send to the Pod are on startup (in initializeAfterRileyLinkConnection)
    // And when the user explicitly requested it by clicking the Refresh button on the Omnipod tab
    // We don't do periodical status requests because that can drain the Pod's battery
    // However that should be fine because we get a StatusResponse from all insulin commands sent to the Pod
    @Override
    public void getPumpStatus() {
        if (firstRun) {
            initializeAfterRileyLinkConnection();
        } else if (!statusRequestList.isEmpty()) {
            Iterator<OmnipodStatusRequestType> iterator = statusRequestList.iterator();

            while (iterator.hasNext()) {
                OmnipodStatusRequestType statusRequest = iterator.next();
                switch (statusRequest) {
                    case GetPodPulseLog:
                        try {
                            PodInfoRecentPulseLog result = executeCommand(OmnipodCommandType.GetPodPulseLog, aapsOmnipodManager::readPulseLog);
                            Intent i = new Intent(context, ErrorHelperActivity.class);
                            i.putExtra("soundid", 0);
                            i.putExtra("status", "Pulse Log (copied to clipboard):\n" + result.toString());
                            i.putExtra("title", resourceHelper.gs(R.string.omnipod_warning));
                            i.putExtra("clipboardContent", result.toString());
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(i);
                        } catch (Exception ex) {
                            aapsLogger.warn(LTag.PUMP, "Failed to retrieve pulse log", ex);
                            Intent i = new Intent(context, ErrorHelperActivity.class);
                            i.putExtra("soundid", 0);
                            i.putExtra("status", "Failed to retrieve pulse log");
                            i.putExtra("title", resourceHelper.gs(R.string.omnipod_warning));
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(i);
                        }
                        break;
                    case AcknowledgeAlerts:
                        executeCommand(OmnipodCommandType.AcknowledgeAlerts, aapsOmnipodManager::acknowledgeAlerts);
                        break;
                    case GetPodState:
                        executeCommand(OmnipodCommandType.GetPodStatus, aapsOmnipodManager::getPodStatus);
                        break;
                    default:
                        aapsLogger.error(LTag.PUMP, "Unknown status request: " + statusRequest.name());
                }
                iterator.remove();
            }
        } else if (this.hasTimeDateOrTimeZoneChanged) {
            PumpEnactResult result = executeCommand(OmnipodCommandType.SetTime, aapsOmnipodManager::setTime);

            if (result.success) {
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
        PumpEnactResult result = executeCommand(OmnipodCommandType.SetBasalProfile, () -> aapsOmnipodManager.setBasalProfile(profile));

        aapsLogger.info(LTag.PUMP, "Basal Profile was set: " + result.success);

        if (result.success) {
            rxBus.send(new EventDismissNotification(Notification.OMNIPOD_POD_SUSPENDED));
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
        if (!podStateManager.isPodActivationCompleted()) {
            return false;
        }
        return podStateManager.getBasalSchedule().equals(AapsOmnipodManager.mapProfileToBasalSchedule(profile));
    }

    @Override
    public long lastDataTime() {
        return podStateManager.isPodActivationCompleted() ? podStateManager.getLastSuccessfulCommunication().getMillis() : 0;
    }

    @Override
    public double getBaseBasalRate() {
        if (!podStateManager.isPodRunning()) {
            return 0.0d;
        }

        DateTime now = DateTime.now();
        Duration offset = new Duration(now.withTimeAtStartOfDay(), now);
        return podStateManager.getBasalSchedule().rateAt(offset);
    }

    @Override
    public double getReservoirLevel() {
        if (!podStateManager.isPodRunning()) {
            return 0.0d;
        }
        Double reservoirLevel = podStateManager.getReservoirLevel();
        return reservoirLevel == null ? 75.0 : reservoirLevel;
    }

    @Override
    public int getBatteryLevel() {
        if (!podStateManager.isPodRunning()) {
            return 0;
        }
        return 75;
    }

    @NonNull @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
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

            // FIXME do we need this??
            EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.INSTANCE;
            bolusingEvent.setT(new Treatment());
            bolusingEvent.getT().isSMB = detailedBolusInfo.isSMB;
            bolusingEvent.setPercent(100);
            rxBus.send(bolusingEvent);

            aapsLogger.debug(LTag.PUMP, "deliverTreatment: Carb only treatment.");

            return new PumpEnactResult(getInjector()).success(true).enacted(true).bolusDelivered(0d)
                    .carbsDelivered(detailedBolusInfo.carbs).comment(getResourceHelper().gs(info.nightscout.androidaps.core.R.string.common_resultok));
        }
    }

    @Override
    public void stopBolusDelivering() {
        executeCommand(OmnipodCommandType.CancelBolus, aapsOmnipodManager::cancelBolus);
    }

    // if enforceNew===true current temp basal is canceled and new TBR set (duration is prolonged),
    // if false and the same rate is requested enacted=false and success=true is returned and TBR is not changed
    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer
            durationInMinutes, Profile profile, boolean enforceNew) {
        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute: rate: {}, duration={}", absoluteRate, durationInMinutes);

        // read current TBR
        TemporaryBasal tbrCurrent = readTBR();

        if (tbrCurrent != null) {
            aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute: Current Basal: duration: {} min, rate={}",
                    tbrCurrent.durationInMinutes, tbrCurrent.absoluteRate);
        }

        if (tbrCurrent != null && !enforceNew) {
            if (Round.isSame(tbrCurrent.absoluteRate, absoluteRate)) {
                aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - No enforceNew and same rate. Exiting.");
                rxBus.send(new EventRefreshOverview("Omnipod command: SetTemporaryBasal", false));
                return new PumpEnactResult(getInjector()).success(true).enacted(false);
            }
        }

        PumpEnactResult result = executeCommand(OmnipodCommandType.SetTemporaryBasal, () -> aapsOmnipodManager.setTemporaryBasal(new TempBasalPair(absoluteRate, false, durationInMinutes)));

        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - setTBR. Response: " + result.success);

        if (result.success) {
            incrementStatistics(OmnipodStorageKeys.Statistics.TBRsSet);
        }

        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        TemporaryBasal tbrCurrent = readTBR();

        if (tbrCurrent == null) {
            aapsLogger.info(LTag.PUMP, "cancelTempBasal - TBR already canceled.");
            rxBus.send(new EventRefreshOverview("Omnipod command: CancelTemporaryBasal", false));
            return new PumpEnactResult(getInjector()).success(true).enacted(false);
        }

        PumpEnactResult result = executeCommand(OmnipodCommandType.CancelTemporaryBasal, aapsOmnipodManager::cancelTemporaryBasal);

        if (result.success) {
            // TODO is this necessary?
            TemporaryBasal tempBasal = new TemporaryBasal(getInjector()) //
                    .date(System.currentTimeMillis()) //
                    .duration(0) //
                    .source(Source.USER);

            activePlugin.getActiveTreatments().addToHistoryTempBasal(tempBasal);
        }

        return result;
    }

    // TODO improve (i8n and more)
    @NonNull @Override
    public JSONObject getJSONStatus(Profile profile, String profileName, String version) {

        if (!podStateManager.isPodActivationCompleted() || lastConnectionTimeMillis + 60 * 60 * 1000L < System.currentTimeMillis()) {
            return new JSONObject();
        }

        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            status.put("status", podStateManager.isPodRunning() ? (podStateManager.isSuspended() ? "suspended" : "normal") : "no active Pod");
            status.put("timestamp", DateUtil.toISOString(new Date()));

            battery.put("percent", getBatteryLevel());

            extended.put("Version", version);
            try {
                extended.put("ActiveProfile", profileName);
            } catch (Exception ignored) {
            }

            TemporaryBasal tb = activePlugin.getActiveTreatments().getRealTempBasalFromHistory(System.currentTimeMillis());
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
            pump.put("reservoir", getReservoirLevel());
            pump.put("clock", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
            aapsLogger.error(LTag.PUMP, "Unhandled exception", e);
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
        if (!podStateManager.isPodActivationCompleted()) {
            return "No active pod";
        }
        String ret = "";
        if (lastConnectionTimeMillis != 0) {
            long agoMsec = System.currentTimeMillis() - lastConnectionTimeMillis;
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += "LastConn: " + agoMin + " min ago\n";
        }
        if (podStateManager.getLastBolusStartTime() != null) {
            ret += "LastBolus: " + DecimalFormatter.to2Decimal(podStateManager.getLastBolusAmount()) + "U @" + //
                    android.text.format.DateFormat.format("HH:mm", podStateManager.getLastBolusStartTime().toDate()) + "\n";
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
        ret += "Reserv: " + DecimalFormatter.to0Decimal(getReservoirLevel()) + "U\n";
        ret += "Batt: " + getBatteryLevel();
        return ret;
    }

    @Override
    public List<CustomAction> getCustomActions() {
        return customActions;
    }

    @Override
    public void executeCustomAction(CustomActionType customActionType) {
        OmnipodCustomActionType mcat = (OmnipodCustomActionType) customActionType;

        switch (mcat) {
            case ResetRileyLinkConfiguration:
                serviceTaskExecutor.startTask(new ResetRileyLinkConfigurationTask(getInjector()));
                break;

            default:
                aapsLogger.warn(LTag.PUMP, "Unknown custom action: {}" + mcat);
                break;
        }
    }

    @Override
    public void timezoneOrDSTChanged(TimeChangeType timeChangeType) {
        aapsLogger.warn(LTag.PUMP, "Time, Date and/or TimeZone changed. [changeType=" + timeChangeType.name() + ", eventHandlingEnabled=" + aapsOmnipodManager.isTimeChangeEventEnabled() + "]");

        if (aapsOmnipodManager.isTimeChangeEventEnabled() && podStateManager.isPodRunning()) {
            aapsLogger.info(LTag.PUMP, "Time, Date and/or TimeZone changed event received and will be consumed by driver.");
            this.hasTimeDateOrTimeZoneChanged = true;
        }
    }

    @Override
    public boolean isUnreachableAlertTimeoutExceeded(long unreachableTimeoutMilliseconds) {
        // We have a separate notification for when no Pod is active, see doPodCheck()
        if (podStateManager.isPodActivationCompleted() && podStateManager.getLastSuccessfulCommunication() != null) { // Null check for backwards compatibility
            long currentTimeMillis = System.currentTimeMillis();

            if (podStateManager.getLastSuccessfulCommunication().getMillis() + unreachableTimeoutMilliseconds < currentTimeMillis) {
                // We exceeded the user defined alert threshold. However, as we don't send periodical status requests to the Pod to prevent draining it's battery,
                // Exceeding the threshold alone is not a reason to trigger an alert: it could very well be that we just didn't need to send any commands for a while
                // Below return statement covers these cases in which we will trigger an alert:
                // - Sending the last command to the Pod failed
                // - The Pod is suspended
                // - RileyLink is in an error state
                // - RileyLink has been connecting for over RILEY_LINK_CONNECT_TIMEOUT
                return (podStateManager.getLastFailedCommunication() != null && podStateManager.getLastSuccessfulCommunication().isBefore(podStateManager.getLastFailedCommunication())) ||
                        podStateManager.isSuspended() ||
                        rileyLinkServiceData.rileyLinkServiceState.isError() ||
                        // The below clause is a hack for working around the RL service state forever staying in connecting state on startup if the RL is switched off / unreachable
                        (rileyLinkServiceData.getRileyLinkServiceState().isConnecting() && rileyLinkServiceData.getLastServiceStateChange() + RILEY_LINK_CONNECT_TIMEOUT_MILLIS < currentTimeMillis);
            }
        }

        return false;
    }

    public void addPodStatusRequest(OmnipodStatusRequestType pumpStatusRequest) {
        statusRequestList.add(pumpStatusRequest);
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @Override public boolean canHandleDST() {
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

    @NotNull @Override public PumpEnactResult setTempBasalPercent(Integer percent, Integer
            durationInMinutes, Profile profile, boolean enforceNew) {
        aapsLogger.debug(LTag.PUMP, "setTempBasalPercent [OmnipodPumpPlugin] - Not implemented.");
        return getOperationNotSupportedWithCustomText(info.nightscout.androidaps.core.R.string.pump_operation_not_supported_by_pump_driver);
    }

    @NotNull @Override public PumpEnactResult setExtendedBolus(Double insulin, Integer
            durationInMinutes) {
        aapsLogger.debug(LTag.PUMP, "setExtendedBolus [OmnipodPumpPlugin] - Not implemented.");
        return getOperationNotSupportedWithCustomText(info.nightscout.androidaps.core.R.string.pump_operation_not_supported_by_pump_driver);
    }

    @NotNull @Override public PumpEnactResult cancelExtendedBolus() {
        aapsLogger.debug(LTag.PUMP, "cancelExtendedBolus [OmnipodPumpPlugin] - Not implemented.");
        return getOperationNotSupportedWithCustomText(info.nightscout.androidaps.core.R.string.pump_operation_not_supported_by_pump_driver);
    }

    @NotNull @Override public PumpEnactResult loadTDDs() {
        aapsLogger.debug(LTag.PUMP, "loadTDDs [OmnipodPumpPlugin] - Not implemented.");
        return getOperationNotSupportedWithCustomText(info.nightscout.androidaps.core.R.string.pump_operation_not_supported_by_pump_driver);
    }

    private void initializeAfterRileyLinkConnection() {
        if (podStateManager.isPodInitialized() && podStateManager.getPodProgressStatus().isAtLeast(PodProgressStatus.PAIRING_COMPLETED)) {
            PumpEnactResult result = executeCommand(OmnipodCommandType.GetPodStatus, aapsOmnipodManager::getPodStatus);
            if (result.success) {
                aapsLogger.debug(LTag.PUMP, "Successfully retrieved Pod status on startup");
            } else {
                aapsLogger.warn(LTag.PUMP, "Failed to retrieve Pod status on startup");
            }
        } else {
            aapsLogger.debug(LTag.PUMP, "Not retrieving Pod status on startup: no Pod running");
        }

        if (!sentIdToFirebase) {
            Bundle params = new Bundle();
            params.putString("version", BuildConfig.VERSION);

            fabricPrivacy.getFirebaseAnalytics().logEvent("OmnipodPumpInit", params);

            sentIdToFirebase = true;
        }

        this.firstRun = false;
    }

    @NonNull private PumpEnactResult deliverBolus(final DetailedBolusInfo detailedBolusInfo) {
        PumpEnactResult result = executeCommand(OmnipodCommandType.SetBolus, () -> aapsOmnipodManager.bolus(detailedBolusInfo));

        if (result.success) {
            incrementStatistics(detailedBolusInfo.isSMB ? OmnipodStorageKeys.Statistics.SMBBoluses
                    : OmnipodStorageKeys.Statistics.StandardBoluses);

            result.carbsDelivered(detailedBolusInfo.carbs);
        }

        return result;
    }

    private <T> T executeCommand(OmnipodCommandType commandType, Supplier<T> supplier) {
        aapsLogger.debug(LTag.PUMP, "Executing command: {}", commandType);

        rileyLinkUtil.getRileyLinkHistory().add(new RLHistoryItemOmnipod(commandType));

        T pumpEnactResult = supplier.get();

        // TODO maybe only do this for specific commands
        rxBus.send(new EventRefreshOverview("Omnipod command: " + commandType.name(), false));

        rxBus.send(new EventOmnipodPumpValuesChanged());

        return pumpEnactResult;
    }

    private void incrementStatistics(String statsKey) {
        long currentCount = sp.getLong(statsKey, 0L);
        currentCount++;
        sp.putLong(statsKey, currentCount);
    }

    private TemporaryBasal readTBR() {
        return activePlugin.getActiveTreatments().getTempBasalFromHistory(System.currentTimeMillis());
    }

    private PumpEnactResult getOperationNotSupportedWithCustomText(int resourceId) {
        return new PumpEnactResult(getInjector()).success(false).enacted(false).comment(getResourceHelper().gs(resourceId));
    }

}
