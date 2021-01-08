package info.nightscout.androidaps.plugins.pump.omnipod;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventAppInitialized;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.PumpPluginBase;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpInfo;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.data.ActiveBolus;
import info.nightscout.androidaps.plugins.pump.omnipod.data.RLHistoryItemOmnipod;
import info.nightscout.androidaps.plugins.pump.omnipod.definition.OmnipodCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.definition.OmnipodStorageKeys;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.action.service.ExpirationReminderBuilder;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo.PodInfoRecentPulseLog;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.ActivationProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertSet;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.manager.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.util.TimeUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.event.EventOmnipodActiveAlertsChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.event.EventOmnipodFaultEventChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.event.EventOmnipodPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.event.EventOmnipodTbrChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.event.EventOmnipodUncertainTbrRecovered;
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsOmnipodManager;
import info.nightscout.androidaps.plugins.pump.omnipod.queue.command.CommandAcknowledgeAlerts;
import info.nightscout.androidaps.plugins.pump.omnipod.queue.command.CommandHandleTimeChange;
import info.nightscout.androidaps.plugins.pump.omnipod.queue.command.CommandPlayTestBeep;
import info.nightscout.androidaps.plugins.pump.omnipod.queue.command.CommandUpdateAlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.queue.command.OmnipodCustomCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.queue.command.OmnipodCustomCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.manager.OmnipodRileyLinkCommunicationManager;
import info.nightscout.androidaps.plugins.pump.omnipod.rileylink.service.RileyLinkOmnipodService;
import info.nightscout.androidaps.plugins.pump.omnipod.ui.OmnipodOverviewFragment;
import info.nightscout.androidaps.plugins.pump.omnipod.util.AapsOmnipodUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodAlertUtil;
import info.nightscout.androidaps.queue.Callback;
import info.nightscout.androidaps.queue.commands.CustomCommand;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.Round;
import info.nightscout.androidaps.utils.TimeChangeType;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import static info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants.BASAL_STEP_DURATION;

/**
 * Created by andy on 23.04.18.
 *
 * @author Andy Rozman (andy.rozman@gmail.com)
 */
@Singleton
public class OmnipodPumpPlugin extends PumpPluginBase implements PumpInterface, RileyLinkPumpDevice {
    private static final long RILEY_LINK_CONNECT_TIMEOUT_MILLIS = 3 * 60 * 1_000L; // 3 minutes
    private static final long STATUS_CHECK_INTERVAL_MILLIS = 60 * 1_000L; // 1 minute
    public static final int STARTUP_STATUS_REQUEST_TRIES = 2;
    public static final double RESERVOIR_OVER_50_UNITS_DEFAULT = 75.0;

    private final PodStateManager podStateManager;
    private final RileyLinkServiceData rileyLinkServiceData;
    private final AapsOmnipodManager aapsOmnipodManager;
    private final AapsOmnipodUtil aapsOmnipodUtil;
    private final RileyLinkUtil rileyLinkUtil;
    private final OmnipodAlertUtil omnipodAlertUtil;
    private final ProfileFunction profileFunction;
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
    private final OmnipodRileyLinkCommunicationManager omnipodRileyLinkCommunicationManager;
    private final PumpType pumpType = PumpType.Insulet_Omnipod;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final NSUpload nsUpload;

    // variables for handling statuses and history
    private boolean firstRun = true;
    private boolean hasTimeDateOrTimeZoneChanged = false;
    private final boolean displayConnectionMessages = false;
    private RileyLinkOmnipodService rileyLinkOmnipodService;
    private boolean busy = false;
    private int timeChangeRetries;
    private long nextPodWarningCheck;
    private long lastConnectionTimeMillis;
    private final Handler loopHandler = new Handler(Looper.getMainLooper());

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
            DateUtil dateUtil,
            AapsOmnipodUtil aapsOmnipodUtil,
            RileyLinkUtil rileyLinkUtil,
            OmnipodAlertUtil omnipodAlertUtil,
            ProfileFunction profileFunction,
            NSUpload nsUpload,
            OmnipodRileyLinkCommunicationManager omnipodRileyLinkCommunicationManager
    ) {
        super(new PluginDescription() //
                        .mainType(PluginType.PUMP) //
                        .fragmentClass(OmnipodOverviewFragment.class.getName()) //
                        .pluginIcon(R.drawable.ic_pod_128)
                        .pluginName(R.string.omnipod_name) //
                        .shortName(R.string.omnipod_name_short) //
                        .preferencesId(R.xml.pref_omnipod) //
                        .description(R.string.omnipod_pump_description), //
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
        this.aapsOmnipodManager = aapsOmnipodManager;
        this.aapsOmnipodUtil = aapsOmnipodUtil;
        this.rileyLinkUtil = rileyLinkUtil;
        this.omnipodAlertUtil = omnipodAlertUtil;
        this.profileFunction = profileFunction;
        this.nsUpload = nsUpload;
        this.omnipodRileyLinkCommunicationManager = omnipodRileyLinkCommunicationManager;

        pumpDescription = new PumpDescription(pumpType);

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
                if (commandQueue.size() == 0) {
                    if (podStateManager.isPodRunning() && !podStateManager.isSuspended()) {
                        aapsOmnipodManager.cancelSuspendedFakeTbrIfExists();
                    } else {
                        aapsOmnipodManager.createSuspendedFakeTbrIfNotExists();
                    }

                    if (OmnipodPumpPlugin.this.hasTimeDateOrTimeZoneChanged) {
                        getCommandQueue().customCommand(new CommandHandleTimeChange(false), null);
                    }
                    if (!OmnipodPumpPlugin.this.verifyPodAlertConfiguration()) {
                        getCommandQueue().customCommand(new CommandUpdateAlertConfiguration(), null);
                    }

                    if (aapsOmnipodManager.isAutomaticallyAcknowledgeAlertsEnabled() && podStateManager.isPodActivationCompleted() && !podStateManager.isPodDead() &&
                            podStateManager.getActiveAlerts().size() > 0 && !getCommandQueue().isCustomCommandInQueue(CommandAcknowledgeAlerts.class)) {
                        queueAcknowledgeAlertsCommand();
                    }
                } else {
                    aapsLogger.debug(LTag.PUMP, "Skipping Pod status check because command queue is not empty");
                }

                updatePodWarningNotifications();

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
                .toObservable(EventOmnipodTbrChanged.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> handleCancelledTbr(), fabricPrivacy::logException)
        );
        disposables.add(rxBus
                .toObservable(EventOmnipodUncertainTbrRecovered.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> handleUncertainTbrRecovery(), fabricPrivacy::logException)
        );
        disposables.add(rxBus
                .toObservable(EventOmnipodActiveAlertsChanged.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> handleActivePodAlerts(), fabricPrivacy::logException)
        );
        disposables.add(rxBus
                .toObservable(EventOmnipodFaultEventChanged.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> handlePodFaultEvent(), fabricPrivacy::logException)
        );
        disposables.add(rxBus
                .toObservable(EventPreferenceChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if (event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.BASAL_BEEPS_ENABLED) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.BOLUS_BEEPS_ENABLED) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.TBR_BEEPS_ENABLED) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.SMB_BEEPS_ENABLED) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.SUSPEND_DELIVERY_BUTTON_ENABLED) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.PULSE_LOG_BUTTON_ENABLED) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.RILEY_LINK_STATS_BUTTON_ENABLED) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.SHOW_RILEY_LINK_BATTERY_LEVEL) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.BATTERY_CHANGE_LOGGING_ENABLED) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.TIME_CHANGE_EVENT_ENABLED) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.NOTIFICATION_UNCERTAIN_TBR_SOUND_ENABLED) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.NOTIFICATION_UNCERTAIN_SMB_SOUND_ENABLED) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.NOTIFICATION_UNCERTAIN_BOLUS_SOUND_ENABLED) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.AUTOMATICALLY_ACKNOWLEDGE_ALERTS_ENABLED)) {
                        aapsOmnipodManager.reloadSettings();
                    } else if (event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.EXPIRATION_REMINDER_ENABLED) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.EXPIRATION_REMINDER_HOURS_BEFORE_SHUTDOWN) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.LOW_RESERVOIR_ALERT_ENABLED) ||
                            event.isChanged(getResourceHelper(), OmnipodStorageKeys.Preferences.LOW_RESERVOIR_ALERT_UNITS)) {
                        if (!verifyPodAlertConfiguration()) {
                            getCommandQueue().customCommand(new CommandUpdateAlertConfiguration(), null);
                        }
                    }
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
                    if (sp.contains(OmnipodStorageKeys.Preferences.ACTIVE_BOLUS)) {
                        String activeBolusString = sp.getString(OmnipodStorageKeys.Preferences.ACTIVE_BOLUS, "");
                        aapsLogger.warn(LTag.PUMP, "Found active bolus in SP: {}. Adding Treatment.", activeBolusString);
                        try {
                            ActiveBolus activeBolus = aapsOmnipodUtil.getGsonInstance().fromJson(activeBolusString, ActiveBolus.class);
                            aapsOmnipodManager.addBolusToHistory(activeBolus.toDetailedBolusInfo(aapsLogger));
                        } catch (Exception ex) {
                            aapsLogger.error(LTag.PUMP, "Failed to add active bolus to history", ex);
                        }
                        sp.remove(OmnipodStorageKeys.Preferences.ACTIVE_BOLUS);
                    }
                }, fabricPrivacy::logException)
        );
    }

    public boolean isRileyLinkReady() {
        return rileyLinkServiceData.rileyLinkServiceState.isReady();
    }

    private void handleCancelledTbr() {
        if (!podStateManager.isTempBasalRunning() && activePlugin.getActiveTreatments().isTempBasalInProgress() && !aapsOmnipodManager.hasSuspendedFakeTbr()) {
            aapsOmnipodManager.reportCancelledTbr();
        }
    }

    private void handleUncertainTbrRecovery() {
        TemporaryBasal tempBasal = activePlugin.getActiveTreatments().getTempBasalFromHistory(System.currentTimeMillis());

        if (podStateManager.isTempBasalRunning() && tempBasal == null) {
            if (podStateManager.hasTempBasal()) {
                aapsLogger.warn(LTag.PUMP, "Registering TBR that AAPS was unaware of");
                long pumpId = aapsOmnipodManager.addTbrSuccessToHistory(podStateManager.getTempBasalStartTime().getMillis(),
                        new TempBasalPair(podStateManager.getTempBasalAmount(), false, (int) podStateManager.getTempBasalDuration().getStandardMinutes()));

                TemporaryBasal temporaryBasal = new TemporaryBasal(getInjector()) //
                        .absolute(podStateManager.getTempBasalAmount()) //
                        .duration((int) podStateManager.getTempBasalDuration().getStandardMinutes())
                        .date(podStateManager.getTempBasalStartTime().getMillis()) //
                        .source(Source.PUMP) //
                        .pumpId(pumpId);

                activePlugin.getActiveTreatments().addToHistoryTempBasal(temporaryBasal);
            } else {
                // Not sure what's going on. Notify the user
                aapsLogger.error(LTag.PUMP, "Unknown TBR in both Pod state and AAPS");
                rxBus.send(new EventNewNotification(new Notification(Notification.OMNIPOD_UNKNOWN_TBR, resourceHelper.gs(R.string.omnipod_error_tbr_running_but_aaps_not_aware), Notification.NORMAL).sound(R.raw.boluserror)));
            }
        } else if (!podStateManager.isTempBasalRunning() && tempBasal != null) {
            aapsLogger.warn(LTag.PUMP, "Removing AAPS TBR that actually hadn't succeeded");
            activePlugin.getActiveTreatments().removeTempBasal(tempBasal);
        }

        rxBus.send(new EventDismissNotification(Notification.OMNIPOD_TBR_ALERTS));
    }

    private void handleActivePodAlerts() {
        if (podStateManager.isPodActivationCompleted() && !podStateManager.isPodDead()) {
            AlertSet activeAlerts = podStateManager.getActiveAlerts();
            if (activeAlerts.size() > 0) {
                String alerts = TextUtils.join(", ", aapsOmnipodUtil.getTranslatedActiveAlerts(podStateManager));
                String notificationText = resourceHelper.gq(R.plurals.omnipod_pod_alerts, activeAlerts.size(), alerts);
                Notification notification = new Notification(Notification.OMNIPOD_POD_ALERTS, notificationText, Notification.URGENT);
                rxBus.send(new EventNewNotification(notification));
                nsUpload.uploadError(notificationText);

                if (aapsOmnipodManager.isAutomaticallyAcknowledgeAlertsEnabled() && !getCommandQueue().isCustomCommandInQueue(CommandAcknowledgeAlerts.class)) {
                    queueAcknowledgeAlertsCommand();
                }
            }
        }
    }

    private void handlePodFaultEvent() {
        if (podStateManager.isPodFaulted()) {
            String notificationText = resourceHelper.gs(R.string.omnipod_pod_status_pod_fault_description, podStateManager.getFaultEventCode().getValue(), podStateManager.getFaultEventCode().name());
            nsUpload.uploadError(notificationText);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        aapsLogger.debug(LTag.PUMP, "OmnipodPumpPlugin.onStop()");

        loopHandler.removeCallbacks(statusChecker);

        context.unbindService(serviceConnection);

        disposables.clear();
    }

    private void queueAcknowledgeAlertsCommand() {
        getCommandQueue().customCommand(new CommandAcknowledgeAlerts(), new Callback() {
            @Override public void run() {
                if (result != null) {
                    aapsLogger.debug(LTag.PUMP, "Acknowledge alerts result: {} ({})", result.success, result.comment);
                }
            }
        });
    }

    private void updatePodWarningNotifications() {
        if (System.currentTimeMillis() > this.nextPodWarningCheck) {
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

                    if (podStateManager.timeDeviatesMoreThan(OmnipodConstants.TIME_DEVIATION_THRESHOLD)) {
                        Notification notification = new Notification(Notification.OMNIPOD_TIME_OUT_OF_SYNC, resourceHelper.gs(R.string.omnipod_error_time_out_of_sync), Notification.NORMAL);
                        rxBus.send(new EventNewNotification(notification));
                    } else {
                        rxBus.send(new EventDismissNotification(Notification.OMNIPOD_TIME_OUT_OF_SYNC));
                    }
                }
            }

            this.nextPodWarningCheck = DateTimeUtil.getTimeInFutureFromMinutes(15);
        }
    }

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
        if (displayConnectionMessages) {
            aapsLogger.debug(LTag.PUMP, "isHandshakeInProgress [OmnipodPumpPlugin] - default (empty) implementation.");
        }
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
        String frequency = resourceHelper.gs(R.string.omnipod_frequency);
        String connectedModel = "Eros";
        String serialNumber = podStateManager.isPodInitialized() ? String.valueOf(podStateManager.getAddress()) : "-";
        return new RileyLinkPumpInfo(frequency, connectedModel, serialNumber);
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

    /**
     * We don't do periodical status requests because that could drain the Pod's battery
     * The only actual status requests we send to the Pod here are on startup (in {@link #initializeAfterRileyLinkConnection() initializeAfterRileyLinkConnection()}),
     * When explicitly requested through SMS commands
     * And when the basal and/or temp basal status is uncertain
     * When the user explicitly requested it by clicking the Refresh button on the Omnipod tab (which is executed through {@link #executeCustomCommand(CustomCommand)})
     */
    @Override
    public void getPumpStatus(String reason) {
        if (firstRun) {
            initializeAfterRileyLinkConnection();
            firstRun = false;
        } else {
            if ("SMS".equals(reason)) {
                aapsLogger.info(LTag.PUMP, "Acknowledged AAPS getPumpStatus request it was requested through an SMS");
                getPodStatus();
            } else if (podStateManager.isPodRunning() && (!podStateManager.isBasalCertain() || !podStateManager.isTempBasalCertain())) {
                aapsLogger.info(LTag.PUMP, "Acknowledged AAPS getPumpStatus request because basal and/or temp basal is uncertain");
                getPodStatus();
            }
        }
    }

    private PumpEnactResult getPodStatus() {
        return executeCommand(OmnipodCommandType.GET_POD_STATUS, aapsOmnipodManager::getPodStatus);
    }

    @NonNull
    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        PumpEnactResult result = executeCommand(OmnipodCommandType.SET_BASAL_PROFILE, () -> aapsOmnipodManager.setBasalProfile(profile, true));

        aapsLogger.info(LTag.PUMP, "Basal Profile was set: " + result.success);

        return result;
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        if (!podStateManager.isPodActivationCompleted()) {
            // When no Pod is active, return true here in order to prevent AAPS from setting a profile
            // When we activate a new Pod, we just use ProfileFunction to set the currently active profile
            return true;
        }
        return Objects.equals(podStateManager.getBasalSchedule(), AapsOmnipodManager.mapProfileToBasalSchedule(profile));
    }

    @Override
    public long lastDataTime() {
        return podStateManager.isPodInitialized() ? podStateManager.getLastSuccessfulCommunication().getMillis() : 0;
    }

    @Override
    public double getBaseBasalRate() {
        if (!podStateManager.isPodRunning()) {
            return 0.0d;
        }

        return podStateManager.getBasalSchedule().rateAt(TimeUtil.toDuration(DateTime.now()));
    }

    @Override
    public double getReservoirLevel() {
        if (!podStateManager.isPodRunning()) {
            return 0.0d;
        }
        Double reservoirLevel = podStateManager.getReservoirLevel();
        // Omnipod only reports reservoir level when it's 50 units or less.
        // When it's over 50 units, we don't know, so return some default over 50 units
        return reservoirLevel == null ? RESERVOIR_OVER_50_UNITS_DEFAULT : reservoirLevel;
    }

    @Override
    public int getBatteryLevel() {
        if (aapsOmnipodManager.isShowRileyLinkBatteryLevel()) {
            return Optional.ofNullable(rileyLinkServiceData.batteryLevel).orElse(0);
        }

        return 0;
    }

    @NonNull @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        if (detailedBolusInfo.insulin == 0 && detailedBolusInfo.carbs == 0) {
            // neither carbs nor bolus requested
            aapsLogger.error("deliverTreatment: Invalid input: neither carbs nor insulin are set in treatment");
            return new PumpEnactResult(getInjector()).success(false).enacted(false).bolusDelivered(0d).carbsDelivered(0d)
                    .comment(getResourceHelper().gs(info.nightscout.androidaps.core.R.string.invalidinput));
        } else if (detailedBolusInfo.insulin > 0) {
            // bolus needed, ask pump to deliver it
            return deliverBolus(detailedBolusInfo);
        } else {
            // no bolus required, carb only treatment
            activePlugin.getActiveTreatments().addToHistoryTreatment(detailedBolusInfo, true);

            return new PumpEnactResult(getInjector()).success(true).enacted(true).bolusDelivered(0d)
                    .carbsDelivered(detailedBolusInfo.carbs).comment(getResourceHelper().gs(info.nightscout.androidaps.core.R.string.common_resultok));
        }
    }

    @Override
    public void stopBolusDelivering() {
        executeCommand(OmnipodCommandType.CANCEL_BOLUS, aapsOmnipodManager::cancelBolus);
    }

    // if enforceNew is true, current temp basal is cancelled and new TBR set (duration is prolonged),
    // if false and the same rate is requested enacted=false and success=true is returned and TBR is not changed
    @Override
    @NonNull
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer
            durationInMinutes, Profile profile, boolean enforceNew) {
        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute: rate: {}, duration={}", absoluteRate, durationInMinutes);

        if (durationInMinutes <= 0 || durationInMinutes % BASAL_STEP_DURATION.getStandardMinutes() != 0) {
            return new PumpEnactResult(getInjector()).success(false).comment(resourceHelper.gs(R.string.omnipod_error_set_temp_basal_failed_validation, BASAL_STEP_DURATION.getStandardMinutes()));
        }

        // read current TBR
        TemporaryBasal tbrCurrent = readTBR();

        if (tbrCurrent != null) {
            aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute: Current Basal: duration: {} min, rate={}",
                    tbrCurrent.durationInMinutes, tbrCurrent.absoluteRate);
        }

        if (tbrCurrent != null && !enforceNew) {
            if (Round.isSame(tbrCurrent.absoluteRate, absoluteRate)) {
                aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - No enforceNew and same rate. Exiting.");
                return new PumpEnactResult(getInjector()).success(true).enacted(false);
            }
        }

        PumpEnactResult result = executeCommand(OmnipodCommandType.SET_TEMPORARY_BASAL, () -> aapsOmnipodManager.setTemporaryBasal(new TempBasalPair(absoluteRate, false, durationInMinutes)));

        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - setTBR. Response: " + result.success);

        if (result.success) {
            incrementStatistics(OmnipodStorageKeys.Statistics.TBRS_SET);
        }

        return result;
    }

    @Override
    @NonNull
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        TemporaryBasal tbrCurrent = readTBR();

        if (tbrCurrent == null) {
            aapsLogger.info(LTag.PUMP, "cancelTempBasal - TBR already cancelled.");
            return new PumpEnactResult(getInjector()).success(true).enacted(false);
        }

        return executeCommand(OmnipodCommandType.CANCEL_TEMPORARY_BASAL, aapsOmnipodManager::cancelTemporaryBasal);
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

            if (isUseRileyLinkBatteryLevel()) {
                pump.put("battery", battery);
            }

            pump.put("status", status);
            pump.put("extended", extended);

            double reservoirLevel = getReservoirLevel();
            if (reservoirLevel > OmnipodConstants.MAX_RESERVOIR_READING) {
                pump.put("reservoir_display_override", "50+");
                pump.put("reservoir", OmnipodConstants.MAX_RESERVOIR_READING);
            } else {
                pump.put("reservoir", reservoirLevel);
            }

            pump.put("clock", DateUtil.toISOString(podStateManager.getTime().toDate()));
        } catch (JSONException e) {
            aapsLogger.error(LTag.PUMP, "Unhandled exception", e);
        }
        return pump;
    }

    @Override @NonNull public ManufacturerType manufacturer() {
        return pumpType.getManufacturer();
    }

    @Override @NonNull
    public PumpType model() {
        return pumpType;
    }

    @NonNull
    @Override
    public String serialNumber() {
        return podStateManager.isPodInitialized() ? String.valueOf(podStateManager.getAddress()) : "-";
    }

    @Override @NonNull public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @NonNull @Override
    public String shortStatus(boolean veryShort) {
        if (!podStateManager.isPodActivationCompleted()) {
            return resourceHelper.gs(R.string.omnipod_short_status_no_active_pod);
        }
        String ret = "";
        if (lastConnectionTimeMillis != 0) {
            long agoMsec = System.currentTimeMillis() - lastConnectionTimeMillis;
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += resourceHelper.gs(R.string.omnipod_short_status_last_connection, agoMin) + "\n";
        }
        if (podStateManager.getLastBolusStartTime() != null) {
            ret += resourceHelper.gs(R.string.omnipod_short_status_last_bolus, DecimalFormatter.to2Decimal(podStateManager.getLastBolusAmount()),
                    android.text.format.DateFormat.format("HH:mm", podStateManager.getLastBolusStartTime().toDate())) + "\n";
        }
        TemporaryBasal activeTemp = activePlugin.getActiveTreatments().getRealTempBasalFromHistory(System.currentTimeMillis());
        if (activeTemp != null) {
            ret += resourceHelper.gs(R.string.omnipod_short_status_temp_basal, activeTemp.toStringFull()) + "\n";
        }
        ExtendedBolus activeExtendedBolus = activePlugin.getActiveTreatments().getExtendedBolusFromHistory(
                System.currentTimeMillis());
        if (activeExtendedBolus != null) {
            ret += resourceHelper.gs(R.string.omnipod_short_status_extended_bolus, activeExtendedBolus.toString()) + "\n";
        }
        ret += resourceHelper.gs(R.string.omnipod_short_status_reservoir, (getReservoirLevel() > OmnipodConstants.MAX_RESERVOIR_READING ? "50+" : DecimalFormatter.to0Decimal(getReservoirLevel()))) + "\n";
        if (isUseRileyLinkBatteryLevel()) {
            ret += resourceHelper.gs(R.string.omnipod_short_status_rl_battery, getBatteryLevel()) + "\n";
        }
        return ret.trim();
    }

    @Override
    public List<CustomAction> getCustomActions() {
        return Collections.emptyList();
    }

    @Override
    public void executeCustomAction(CustomActionType customActionType) {
        aapsLogger.warn(LTag.PUMP, "Unknown custom action: " + customActionType);
    }

    @Override
    public PumpEnactResult executeCustomCommand(CustomCommand command) {
        if (!(command instanceof OmnipodCustomCommand)) {
            aapsLogger.warn(LTag.PUMP, "Unknown custom command: " + command.getClass().getName());
            return new PumpEnactResult(getInjector()).success(false).enacted(false).comment(resourceHelper.gs(R.string.omnipod_error_unknown_custom_command, command.getClass().getName()));
        }

        OmnipodCustomCommandType commandType = ((OmnipodCustomCommand) command).getType();

        aapsLogger.debug(LTag.PUMP, "Executing custom command: " + commandType);

        switch (commandType) {
            case ACKNOWLEDGE_ALERTS:
                return executeCommand(OmnipodCommandType.ACKNOWLEDGE_ALERTS, aapsOmnipodManager::acknowledgeAlerts);
            case GET_POD_STATUS:
                return getPodStatus();
            case READ_PULSE_LOG:
                return retrievePulseLog();
            case SUSPEND_DELIVERY:
                return executeCommand(OmnipodCommandType.SUSPEND_DELIVERY, aapsOmnipodManager::suspendDelivery);
            case RESUME_DELIVERY:
                return executeCommand(OmnipodCommandType.RESUME_DELIVERY, () -> aapsOmnipodManager.setBasalProfile(profileFunction.getProfile(), false));
            case DEACTIVATE_POD:
                return executeCommand(OmnipodCommandType.DEACTIVATE_POD, aapsOmnipodManager::deactivatePod);
            case HANDLE_TIME_CHANGE:
                return handleTimeChange(((CommandHandleTimeChange) command).isRequestedByUser());
            case UPDATE_ALERT_CONFIGURATION:
                return updateAlertConfiguration();
            case PLAY_TEST_BEEP:
                return executeCommand(OmnipodCommandType.PLAY_TEST_BEEP, () -> aapsOmnipodManager.playTestBeep(((CommandPlayTestBeep) command).getBeepType()));
            default:
                aapsLogger.warn(LTag.PUMP, "Unknown custom command: " + commandType);
                return new PumpEnactResult(getInjector()).success(false).enacted(false).comment(resourceHelper.gs(R.string.omnipod_error_unknown_custom_command, commandType));
        }
    }

    private PumpEnactResult retrievePulseLog() {
        PodInfoRecentPulseLog result;
        try {
            result = executeCommand(OmnipodCommandType.READ_POD_PULSE_LOG, aapsOmnipodManager::readPulseLog);
        } catch (Exception ex) {
            return new PumpEnactResult(getInjector()).success(false).enacted(false).comment(aapsOmnipodManager.translateException(ex));
        }

        Intent i = new Intent(context, ErrorHelperActivity.class);
        i.putExtra("soundid", 0);
        i.putExtra("status", resourceHelper.gs(R.string.omnipod_pulse_log_value) + ":\n" + result.toString());
        i.putExtra("title", resourceHelper.gs(R.string.omnipod_pulse_log));
        i.putExtra("clipboardContent", result.toString());
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(i);
        return new PumpEnactResult(getInjector()).success(true).enacted(false);
    }

    @NonNull private PumpEnactResult updateAlertConfiguration() {
        Duration expirationReminderTimeBeforeShutdown = omnipodAlertUtil.getExpirationReminderTimeBeforeShutdown();
        Integer lowReservoirAlertUnits = omnipodAlertUtil.getLowReservoirAlertUnits();

        List<AlertConfiguration> alertConfigurations = new ExpirationReminderBuilder(podStateManager) //
                .expirationAdvisory(expirationReminderTimeBeforeShutdown != null,
                        Optional.ofNullable(expirationReminderTimeBeforeShutdown).orElse(Duration.ZERO)) //
                .lowReservoir(lowReservoirAlertUnits != null, Optional.ofNullable(lowReservoirAlertUnits).orElse(0)) //
                .build();

        PumpEnactResult result = executeCommand(OmnipodCommandType.CONFIGURE_ALERTS, () -> aapsOmnipodManager.configureAlerts(alertConfigurations));

        if (result.success) {
            aapsLogger.info(LTag.PUMP, "Successfully configured alerts in Pod");

            podStateManager.setExpirationAlertTimeBeforeShutdown(expirationReminderTimeBeforeShutdown);
            podStateManager.setLowReservoirAlertUnits(lowReservoirAlertUnits);

            Notification notification = new Notification(
                    Notification.OMNIPOD_POD_ALERTS_UPDATED,
                    resourceHelper.gs(R.string.omnipod_confirmation_expiration_alerts_updated),
                    Notification.INFO, 60);
            rxBus.send(new EventNewNotification(notification));
        } else {
            aapsLogger.warn(LTag.PUMP, "Failed to configure alerts in Pod");
        }

        return result;
    }

    @NonNull private PumpEnactResult handleTimeChange(boolean requestedByUser) {
        aapsLogger.debug(LTag.PUMP, "Setting time, requestedByUser={}", requestedByUser);

        PumpEnactResult result;
        if (requestedByUser || aapsOmnipodManager.isTimeChangeEventEnabled()) {
            result = executeCommand(OmnipodCommandType.SET_TIME, () -> aapsOmnipodManager.setTime(!requestedByUser));
        } else {
            // Even if automatically changing the time is disabled, we still want to at least do a GetStatus request,
            // in order to update the Pod's activation time, which we need for calculating the time on the Pod
            result = getPodStatus();
        }

        if (result.success) {
            this.hasTimeDateOrTimeZoneChanged = false;
            timeChangeRetries = 0;

            if (!requestedByUser && aapsOmnipodManager.isTimeChangeEventEnabled()) {
                Notification notification = new Notification(
                        Notification.TIME_OR_TIMEZONE_CHANGE,
                        resourceHelper.gs(R.string.omnipod_confirmation_time_on_pod_updated),
                        Notification.INFO, 60);
                rxBus.send(new EventNewNotification(notification));
            }

        } else {
            if (!requestedByUser) {
                timeChangeRetries++;

                if (timeChangeRetries > 3) {
                    if (aapsOmnipodManager.isTimeChangeEventEnabled()) {
                        Notification notification = new Notification(
                                Notification.TIME_OR_TIMEZONE_CHANGE,
                                resourceHelper.gs(R.string.omnipod_error_automatic_time_or_timezone_change_failed),
                                Notification.INFO, 60);
                        rxBus.send(new EventNewNotification(notification));
                    }
                    this.hasTimeDateOrTimeZoneChanged = false;
                    timeChangeRetries = 0;
                }
            }
        }

        return result;
    }

    @Override
    public void timezoneOrDSTChanged(TimeChangeType timeChangeType) {
        aapsLogger.info(LTag.PUMP, "Time, Date and/or TimeZone changed. [changeType=" + timeChangeType.name() + ", eventHandlingEnabled=" + aapsOmnipodManager.isTimeChangeEventEnabled() + "]");

        if (timeChangeType == TimeChangeType.TimeChanged) {
            aapsLogger.info(LTag.PUMP, "Ignoring time change because it is not a DST or TZ change");
            return;
        } else if (!podStateManager.isPodRunning()) {
            aapsLogger.info(LTag.PUMP, "Ignoring time change because no Pod is active");
            return;
        }

        aapsLogger.info(LTag.PUMP, "DST and/or TimeZone changed event will be consumed by driver");
        hasTimeDateOrTimeZoneChanged = true;
    }

    @Override
    public boolean isUnreachableAlertTimeoutExceeded(long unreachableTimeoutMilliseconds) {
        // We have a separate notification for when no Pod is active, see doPodCheck()
        if (podStateManager.isPodActivationCompleted() && podStateManager.getLastSuccessfulCommunication() != null) {
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

    @NonNull @Override public PumpEnactResult setTempBasalPercent(Integer percent, Integer
            durationInMinutes, Profile profile, boolean enforceNew) {
        if (percent == 0) {
            return setTempBasalAbsolute(0.0d, durationInMinutes, profile, enforceNew);
        } else {
            double absoluteValue = profile.getBasal() * (percent / 100.0d);
            absoluteValue = pumpDescription.pumpType.determineCorrectBasalSize(absoluteValue);
            aapsLogger.warn(LTag.PUMP, "setTempBasalPercent [OmnipodPumpPlugin] - You are trying to use setTempBasalPercent with percent other then 0% (" + percent + "). This will start setTempBasalAbsolute, with calculated value (" + absoluteValue + "). Result might not be 100% correct.");
            return setTempBasalAbsolute(absoluteValue, durationInMinutes, profile, enforceNew);
        }
    }

    @NonNull @Override public PumpEnactResult setExtendedBolus(Double insulin, Integer
            durationInMinutes) {
        aapsLogger.debug(LTag.PUMP, "setExtendedBolus [OmnipodPumpPlugin] - Not implemented.");
        return getOperationNotSupportedWithCustomText(info.nightscout.androidaps.core.R.string.pump_operation_not_supported_by_pump_driver);
    }

    @NonNull @Override public PumpEnactResult cancelExtendedBolus() {
        aapsLogger.debug(LTag.PUMP, "cancelExtendedBolus [OmnipodPumpPlugin] - Not implemented.");
        return getOperationNotSupportedWithCustomText(info.nightscout.androidaps.core.R.string.pump_operation_not_supported_by_pump_driver);
    }

    @NonNull @Override public PumpEnactResult loadTDDs() {
        aapsLogger.debug(LTag.PUMP, "loadTDDs [OmnipodPumpPlugin] - Not implemented.");
        return getOperationNotSupportedWithCustomText(info.nightscout.androidaps.core.R.string.pump_operation_not_supported_by_pump_driver);
    }

    public boolean isUseRileyLinkBatteryLevel() {
        return aapsOmnipodManager.isShowRileyLinkBatteryLevel();
    }

    public boolean isBatteryChangeLoggingEnabled() {
        return aapsOmnipodManager.isBatteryChangeLoggingEnabled();
    }

    private void initializeAfterRileyLinkConnection() {
        if (podStateManager.getActivationProgress().isAtLeast(ActivationProgress.PAIRING_COMPLETED)) {
            boolean success = false;
            for (int i = 0; STARTUP_STATUS_REQUEST_TRIES > i; i++) {
                PumpEnactResult result = getPodStatus();
                if (result.success) {
                    success = true;
                    aapsLogger.debug(LTag.PUMP, "Successfully retrieved Pod status on startup");
                    break;
                }
            }
            if (!success) {
                aapsLogger.warn(LTag.PUMP, "Failed to retrieve Pod status on startup");
                rxBus.send(new EventNewNotification(new Notification(Notification.OMNIPOD_STARTUP_STATUS_REFRESH_FAILED, resourceHelper.gs(R.string.omnipod_error_failed_to_refresh_status_on_startup), Notification.NORMAL)));
            }
        } else {
            aapsLogger.debug(LTag.PUMP, "Not retrieving Pod status on startup: no Pod running");
        }

        fabricPrivacy.logCustom("OmnipodPumpInit");
    }

    @NonNull private PumpEnactResult deliverBolus(final DetailedBolusInfo detailedBolusInfo) {
        PumpEnactResult result = executeCommand(OmnipodCommandType.SET_BOLUS, () -> aapsOmnipodManager.bolus(detailedBolusInfo));

        if (result.success) {
            incrementStatistics(detailedBolusInfo.isSMB ? OmnipodStorageKeys.Statistics.SMB_BOLUSES_DELIVERED
                    : OmnipodStorageKeys.Statistics.STANDARD_BOLUSES_DELIVERED);

            result.carbsDelivered(detailedBolusInfo.carbs);
        }

        return result;
    }

    private <T> T executeCommand(OmnipodCommandType commandType, Supplier<T> supplier) {
        try {
            aapsLogger.debug(LTag.PUMP, "Executing command: {}", commandType);

            rileyLinkUtil.getRileyLinkHistory().add(new RLHistoryItemOmnipod(getInjector(), commandType));

            return supplier.get();
        } finally {
            rxBus.send(new EventRefreshOverview("Omnipod command: " + commandType.name(), false));
            rxBus.send(new EventOmnipodPumpValuesChanged());
        }
    }

    private boolean verifyPodAlertConfiguration() {
        if (podStateManager.isPodRunning()) {
            Duration expirationReminderHoursBeforeShutdown = omnipodAlertUtil.getExpirationReminderTimeBeforeShutdown();
            Integer lowReservoirAlertUnits = omnipodAlertUtil.getLowReservoirAlertUnits();

            if (!Objects.equals(expirationReminderHoursBeforeShutdown, podStateManager.getExpirationAlertTimeBeforeShutdown())
                    || !Objects.equals(lowReservoirAlertUnits, podStateManager.getLowReservoirAlertUnits())) {
                aapsLogger.warn(LTag.PUMP, "Configured alerts in Pod don't match AAPS settings: expirationReminderHoursBeforeShutdown = {} (AAPS) vs {} Pod, " +
                                "lowReservoirAlertUnits = {} (AAPS) vs {} (Pod)", expirationReminderHoursBeforeShutdown, podStateManager.getExpirationAlertTimeBeforeShutdown(),
                        lowReservoirAlertUnits, podStateManager.getLowReservoirAlertUnits());
                return false;
            }
        }
        return true;
    }

    private void incrementStatistics(int statsKey) {
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
