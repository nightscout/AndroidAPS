package info.nightscout.androidaps.plugins.pump.omnipod.eros;

import static info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.OmnipodConstants.BASAL_STEP_DURATION;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.plugins.pump.common.events.EventRileyLinkDeviceStatusChange;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpInfo;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandDeactivatePod;
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandHandleTimeChange;
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandPlayTestBeep;
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandResumeDelivery;
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandSilenceAlerts;
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandSuspendDelivery;
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandUpdateAlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.data.RLHistoryItemOmnipod;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.definition.OmnipodErosStorageKeys;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.action.service.ExpirationReminderBuilder;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.response.podinfo.PodInfoRecentPulseLog;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.ActivationProgress;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.AlertSet;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.BeepConfigType;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.schedule.BasalSchedule;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.manager.ErosPodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.util.TimeUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodErosActiveAlertsChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodErosFaultEventChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodErosPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodErosTbrChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.event.EventOmnipodErosUncertainTbrRecovered;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryDatabase;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodErosManager;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.queue.command.CommandGetPodStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.queue.command.CommandReadPulseLog;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.rileylink.service.RileyLinkOmnipodService;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.OmnipodErosOverviewFragment;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.util.AapsOmnipodUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.util.OmnipodAlertUtil;
import info.nightscout.androidaps.services.AlarmSoundService;
import info.nightscout.core.events.EventNewNotification;
import info.nightscout.core.pump.DetailedBolusInfoExtensionKt;
import info.nightscout.core.utils.DateTimeUtil;
import info.nightscout.core.utils.fabric.FabricPrivacy;
import info.nightscout.interfaces.notifications.Notification;
import info.nightscout.interfaces.plugin.ActivePlugin;
import info.nightscout.interfaces.plugin.OwnDatabasePlugin;
import info.nightscout.interfaces.plugin.PluginDescription;
import info.nightscout.interfaces.plugin.PluginType;
import info.nightscout.interfaces.profile.Profile;
import info.nightscout.interfaces.profile.ProfileFunction;
import info.nightscout.interfaces.pump.DetailedBolusInfo;
import info.nightscout.interfaces.pump.OmnipodEros;
import info.nightscout.interfaces.pump.Pump;
import info.nightscout.interfaces.pump.PumpEnactResult;
import info.nightscout.interfaces.pump.PumpPluginBase;
import info.nightscout.interfaces.pump.PumpSync;
import info.nightscout.interfaces.pump.actions.CustomActionType;
import info.nightscout.interfaces.pump.defs.ManufacturerType;
import info.nightscout.interfaces.pump.defs.PumpDescription;
import info.nightscout.interfaces.pump.defs.PumpType;
import info.nightscout.interfaces.queue.Callback;
import info.nightscout.interfaces.queue.CommandQueue;
import info.nightscout.interfaces.queue.CustomCommand;
import info.nightscout.interfaces.ui.ActivityNames;
import info.nightscout.interfaces.utils.DecimalFormatter;
import info.nightscout.interfaces.utils.Round;
import info.nightscout.interfaces.utils.TimeChangeType;
import info.nightscout.pump.common.defs.TempBasalPair;
import info.nightscout.rx.AapsSchedulers;
import info.nightscout.rx.bus.RxBus;
import info.nightscout.rx.events.EventAppExit;
import info.nightscout.rx.events.EventAppInitialized;
import info.nightscout.rx.events.EventDismissNotification;
import info.nightscout.rx.events.EventPreferenceChange;
import info.nightscout.rx.events.EventRefreshOverview;
import info.nightscout.rx.events.EventSWRLStatus;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;
import info.nightscout.shared.interfaces.ResourceHelper;
import info.nightscout.shared.sharedPreferences.SP;
import info.nightscout.shared.utils.DateUtil;
import info.nightscout.shared.utils.T;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

/**
 * Created by andy on 23.04.18.
 *
 * @author Andy Rozman (andy.rozman@gmail.com)
 */
@Singleton
public class OmnipodErosPumpPlugin extends PumpPluginBase implements Pump, RileyLinkPumpDevice, OmnipodEros, OwnDatabasePlugin {
    private static final long RILEY_LINK_CONNECT_TIMEOUT_MILLIS = 3 * 60 * 1_000L; // 3 minutes
    private static final long STATUS_CHECK_INTERVAL_MILLIS = 60 * 1_000L; // 1 minute
    public static final int STARTUP_STATUS_REQUEST_TRIES = 2;
    public static final double RESERVOIR_OVER_50_UNITS_DEFAULT = 75.0;

    private final ErosPodStateManager podStateManager;
    private final RileyLinkServiceData rileyLinkServiceData;
    private final AapsOmnipodErosManager aapsOmnipodErosManager;
    private final AapsOmnipodUtil aapsOmnipodUtil;
    private final RileyLinkUtil rileyLinkUtil;
    private final OmnipodAlertUtil omnipodAlertUtil;
    private final ProfileFunction profileFunction;
    private final AAPSLogger aapsLogger;
    private final AapsSchedulers aapsSchedulers;
    private final RxBus rxBus;
    private final ActivePlugin activePlugin;
    private final Context context;
    private final FabricPrivacy fabricPrivacy;
    private final ResourceHelper rh;
    private final SP sp;
    private final DateUtil dateUtil;
    private final PumpDescription pumpDescription;
    private final ServiceConnection serviceConnection;
    private final PumpType pumpType = PumpType.OMNIPOD_EROS;
    private final PumpSync pumpSync;
    private final ActivityNames activityNames;
    private final ErosHistoryDatabase erosHistoryDatabase;

    private final CompositeDisposable disposable = new CompositeDisposable();

    // variables for handling statuses and history
    private boolean firstRun = true;
    private boolean hasTimeDateOrTimeZoneChanged = false;
    private Instant lastTimeDateOrTimeZoneUpdate = Instant.ofEpochSecond(0L);
    private final boolean displayConnectionMessages = false;
    private RileyLinkOmnipodService rileyLinkOmnipodService;
    private boolean busy = false;
    private int timeChangeRetries;
    private long nextPodWarningCheck;
    private long lastConnectionTimeMillis;
    private HandlerThread handlerThread;
    private Handler loopHandler;

    private final Runnable statusChecker;

    @Inject
    public OmnipodErosPumpPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            AapsSchedulers aapsSchedulers,
            RxBus rxBus,
            Context context,
            ResourceHelper rh,
            ActivePlugin activePlugin,
            SP sp,
            ErosPodStateManager podStateManager,
            AapsOmnipodErosManager aapsOmnipodErosManager,
            CommandQueue commandQueue,
            FabricPrivacy fabricPrivacy,
            RileyLinkServiceData rileyLinkServiceData,
            DateUtil dateUtil,
            AapsOmnipodUtil aapsOmnipodUtil,
            RileyLinkUtil rileyLinkUtil,
            OmnipodAlertUtil omnipodAlertUtil,
            ProfileFunction profileFunction,
            PumpSync pumpSync,
            ActivityNames activityNames,
            ErosHistoryDatabase erosHistoryDatabase
    ) {
        super(new PluginDescription() //
                        .mainType(PluginType.PUMP) //
                        .fragmentClass(OmnipodErosOverviewFragment.class.getName()) //
                        .pluginIcon(R.drawable.ic_pod_128)
                        .pluginName(R.string.omnipod_eros_name) //
                        .shortName(R.string.omnipod_eros_name_short) //
                        .preferencesId(R.xml.omnipod_eros_preferences) //
                        .description(R.string.omnipod_eros_pump_description), //
                injector, aapsLogger, rh, commandQueue);
        this.aapsLogger = aapsLogger;
        this.aapsSchedulers = aapsSchedulers;
        this.rxBus = rxBus;
        this.activePlugin = activePlugin;
        this.context = context;
        this.fabricPrivacy = fabricPrivacy;
        this.rh = rh;
        this.sp = sp;
        this.dateUtil = dateUtil;
        this.podStateManager = podStateManager;
        this.rileyLinkServiceData = rileyLinkServiceData;
        this.aapsOmnipodErosManager = aapsOmnipodErosManager;
        this.aapsOmnipodUtil = aapsOmnipodUtil;
        this.rileyLinkUtil = rileyLinkUtil;
        this.omnipodAlertUtil = omnipodAlertUtil;
        this.profileFunction = profileFunction;
        this.pumpSync = pumpSync;
        this.activityNames = activityNames;
        this.erosHistoryDatabase = erosHistoryDatabase;

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
                        aapsOmnipodErosManager.cancelSuspendedFakeTbrIfExists();
                    } else {
                        aapsOmnipodErosManager.createSuspendedFakeTbrIfNotExists();
                    }

                    if (OmnipodErosPumpPlugin.this.hasTimeDateOrTimeZoneChanged) {
                        getCommandQueue().customCommand(new CommandHandleTimeChange(false), null);
                    }
                    if (!OmnipodErosPumpPlugin.this.verifyPodAlertConfiguration()) {
                        getCommandQueue().customCommand(new CommandUpdateAlertConfiguration(), null);
                    }

                    if (aapsOmnipodErosManager.isAutomaticallyAcknowledgeAlertsEnabled() && podStateManager.isPodActivationCompleted() && !podStateManager.isPodDead() &&
                            podStateManager.getActiveAlerts().size() > 0 && !getCommandQueue().isCustomCommandInQueue(CommandSilenceAlerts.class)) {
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

        if (handlerThread == null) {
            handlerThread = new HandlerThread(OmnipodErosPumpPlugin.class.getSimpleName() + "Handler");
            handlerThread.start();
            loopHandler = new Handler(handlerThread.getLooper());
        }

        loopHandler.postDelayed(statusChecker, STATUS_CHECK_INTERVAL_MILLIS);

        // We can't do this in PodStateManager itself, because JodaTimeAndroid.init() hasn't been called yet
        // When PodStateManager is created, which causes an IllegalArgumentException for DateTimeZones not being recognized
        podStateManager.loadPodState();

        lastConnectionTimeMillis = sp.getLong(
                RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);

        Intent intent = new Intent(context, RileyLinkOmnipodService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        disposable.add(rxBus
                .toObservable(EventAppExit.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> context.unbindService(serviceConnection), fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventOmnipodErosTbrChanged.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> handleCancelledTbr(), fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventOmnipodErosUncertainTbrRecovered.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> handleUncertainTbrRecovery(), fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventOmnipodErosActiveAlertsChanged.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> handleActivePodAlerts(), fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventOmnipodErosFaultEventChanged.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> handlePodFaultEvent(), fabricPrivacy::logException)
        );
        // Pass only to setup wizard
        disposable.add(rxBus
                .toObservable(EventRileyLinkDeviceStatusChange.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> rxBus.send(new EventSWRLStatus(event.getStatus(context))),
                        fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventPreferenceChange.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> {
                    if (event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.BASAL_BEEPS_ENABLED)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.BOLUS_BEEPS_ENABLED)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.TBR_BEEPS_ENABLED)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.SMB_BEEPS_ENABLED)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.SUSPEND_DELIVERY_BUTTON_ENABLED)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.PULSE_LOG_BUTTON_ENABLED)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.RILEY_LINK_STATS_BUTTON_ENABLED)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.SHOW_RILEY_LINK_BATTERY_LEVEL)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.BATTERY_CHANGE_LOGGING_ENABLED)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.TIME_CHANGE_EVENT_ENABLED)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.NOTIFICATION_UNCERTAIN_TBR_SOUND_ENABLED)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.NOTIFICATION_UNCERTAIN_SMB_SOUND_ENABLED)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.NOTIFICATION_UNCERTAIN_BOLUS_SOUND_ENABLED)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.AUTOMATICALLY_ACKNOWLEDGE_ALERTS_ENABLED))) {
                        aapsOmnipodErosManager.reloadSettings();
                    } else if (event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.EXPIRATION_REMINDER_ENABLED)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.EXPIRATION_REMINDER_HOURS_BEFORE_SHUTDOWN)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.LOW_RESERVOIR_ALERT_ENABLED)) ||
                            event.isChanged(getRh().gs(OmnipodErosStorageKeys.Preferences.LOW_RESERVOIR_ALERT_UNITS))) {
                        if (!verifyPodAlertConfiguration()) {
                            getCommandQueue().customCommand(new CommandUpdateAlertConfiguration(), null);
                        }
                    }
                }, fabricPrivacy::logException)
        );
        disposable.add(rxBus
                .toObservable(EventAppInitialized.class)
                .observeOn(aapsSchedulers.getIo())
                .subscribe(event -> {
                    // See if a bolus was active before the app previously exited
                    // If so, add it to history
                    // Needs to be done after EventAppInitialized because otherwise, TreatmentsPlugin.onStart() hasn't been called yet
                    // so it didn't initialize a TreatmentService yet, resulting in a NullPointerException
                    if (sp.contains(OmnipodErosStorageKeys.Preferences.ACTIVE_BOLUS)) {
                        String activeBolusString = sp.getString(OmnipodErosStorageKeys.Preferences.ACTIVE_BOLUS, "");
                        aapsLogger.warn(LTag.PUMP, "Found active bolus in SP: {}. Adding Treatment.", activeBolusString);
                        try {
                            aapsOmnipodErosManager.addBolusToHistory(DetailedBolusInfoExtensionKt.fromJsonString(new DetailedBolusInfo(), activeBolusString));
                        } catch (Exception ex) {
                            aapsLogger.error(LTag.PUMP, "Failed to add active bolus to history", ex);
                        }
                        sp.remove(OmnipodErosStorageKeys.Preferences.ACTIVE_BOLUS);
                    }
                }, fabricPrivacy::logException)
        );
    }

    public boolean isRileyLinkReady() {
        return rileyLinkServiceData.getRileyLinkServiceState().isReady();
    }

    private void handleCancelledTbr() {
        PumpSync.PumpState.TemporaryBasal tbr = pumpSync.expectedPumpState().getTemporaryBasal();
        if (!podStateManager.isTempBasalRunning() && tbr != null && !aapsOmnipodErosManager.hasSuspendedFakeTbr()) {
            aapsOmnipodErosManager.reportCancelledTbr();
        }
    }

    private void handleUncertainTbrRecovery() {
        PumpSync.PumpState.TemporaryBasal tempBasal = pumpSync.expectedPumpState().getTemporaryBasal();

        if (podStateManager.isTempBasalRunning() && tempBasal == null) {
            if (podStateManager.hasTempBasal()) {
                aapsLogger.warn(LTag.PUMP, "Registering TBR that AAPS was unaware of");
                long pumpId = aapsOmnipodErosManager.addTbrSuccessToHistory(podStateManager.getTempBasalStartTime().getMillis(),
                        new TempBasalPair(podStateManager.getTempBasalAmount(), false, (int) podStateManager.getTempBasalDuration().getStandardMinutes()));

                pumpSync.syncTemporaryBasalWithPumpId(
                        podStateManager.getTempBasalStartTime().getMillis(),
                        podStateManager.getTempBasalAmount(),
                        podStateManager.getTempBasalDuration().getMillis(),
                        true,
                        PumpSync.TemporaryBasalType.NORMAL,
                        pumpId,
                        PumpType.OMNIPOD_EROS,
                        serialNumber()
                );
            } else {
                // Not sure what's going on. Notify the user
                aapsLogger.error(LTag.PUMP, "Unknown TBR in both Pod state and AAPS");
                rxBus.send(new EventNewNotification(new Notification(Notification.OMNIPOD_UNKNOWN_TBR, rh.gs(R.string.omnipod_eros_error_tbr_running_but_aaps_not_aware), Notification.NORMAL).sound(R.raw.boluserror)));
            }
        } else if (!podStateManager.isTempBasalRunning() && tempBasal != null) {
            aapsLogger.warn(LTag.PUMP, "Removing AAPS TBR that actually hadn't succeeded");
            pumpSync.invalidateTemporaryBasal(tempBasal.getId());
        }

        rxBus.send(new EventDismissNotification(Notification.OMNIPOD_TBR_ALERTS));
    }

    private void handleActivePodAlerts() {
        if (podStateManager.isPodActivationCompleted() && !podStateManager.isPodDead()) {
            AlertSet activeAlerts = podStateManager.getActiveAlerts();
            if (activeAlerts.size() > 0) {
                String alerts = TextUtils.join(", ", aapsOmnipodUtil.getTranslatedActiveAlerts(podStateManager));
                String notificationText = rh.gq(R.plurals.omnipod_common_pod_alerts, activeAlerts.size(), alerts);
                Notification notification = new Notification(Notification.OMNIPOD_POD_ALERTS, notificationText, Notification.URGENT);
                rxBus.send(new EventNewNotification(notification));
                pumpSync.insertAnnouncement(notificationText, null, PumpType.OMNIPOD_EROS, serialNumber());

                if (aapsOmnipodErosManager.isAutomaticallyAcknowledgeAlertsEnabled() && !getCommandQueue().isCustomCommandInQueue(CommandSilenceAlerts.class)) {
                    queueAcknowledgeAlertsCommand();
                }
            }
        }
    }

    private void handlePodFaultEvent() {
        if (podStateManager.isPodFaulted()) {
            String notificationText = rh.gs(R.string.omnipod_common_pod_status_pod_fault_description, podStateManager.getFaultEventCode().getValue(), podStateManager.getFaultEventCode().name());
            pumpSync.insertAnnouncement(notificationText, null, PumpType.OMNIPOD_EROS, serialNumber());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        aapsLogger.debug(LTag.PUMP, "OmnipodPumpPlugin.onStop()");

        loopHandler.removeCallbacks(statusChecker);

        context.unbindService(serviceConnection);

        disposable.clear();
    }

    private void queueAcknowledgeAlertsCommand() {
        getCommandQueue().customCommand(new CommandSilenceAlerts(), new Callback() {
            @Override public void run() {
                if (result != null) {
                    aapsLogger.debug(LTag.PUMP, "Acknowledge alerts result: {} ({})", result.getSuccess(), result.getComment());
                }
            }
        });
    }

    private void updatePodWarningNotifications() {
        if (System.currentTimeMillis() > this.nextPodWarningCheck) {
            if (!podStateManager.isPodRunning()) {
                Notification notification = new Notification(Notification.OMNIPOD_POD_NOT_ATTACHED, rh.gs(R.string.omnipod_common_error_pod_not_attached), Notification.NORMAL);
                rxBus.send(new EventNewNotification(notification));
            } else {
                rxBus.send(new EventDismissNotification(Notification.OMNIPOD_POD_NOT_ATTACHED));

                if (podStateManager.isSuspended()) {
                    Notification notification = new Notification(Notification.OMNIPOD_POD_SUSPENDED, rh.gs(R.string.omnipod_common_error_pod_suspended), Notification.NORMAL);
                    rxBus.send(new EventNewNotification(notification));
                } else {
                    rxBus.send(new EventDismissNotification(Notification.OMNIPOD_POD_SUSPENDED));

                    if (podStateManager.timeDeviatesMoreThan(OmnipodConstants.TIME_DEVIATION_THRESHOLD)) {
                        Notification notification = new Notification(Notification.OMNIPOD_TIME_OUT_OF_SYNC, rh.gs(R.string.omnipod_common_error_time_out_of_sync), Notification.NORMAL);
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
        String frequency = rh.gs(R.string.omnipod_eros_frequency);
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
    public void getPumpStatus(@NonNull String reason) {
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
        return executeCommand(OmnipodCommandType.GET_POD_STATUS, aapsOmnipodErosManager::getPodStatus);
    }

    @NonNull
    @Override
    public PumpEnactResult setNewBasalProfile(@NonNull Profile profile) {
        if (!podStateManager.hasPodState())
            return new PumpEnactResult(getInjector()).enacted(false).success(false).comment("Null pod state");
        PumpEnactResult result = executeCommand(OmnipodCommandType.SET_BASAL_PROFILE, () -> aapsOmnipodErosManager.setBasalProfile(profile, true));

        aapsLogger.info(LTag.PUMP, "Basal Profile was set: " + result.getSuccess());

        return result;
    }

    @Override
    public boolean isThisProfileSet(@NonNull Profile profile) {
        if (!podStateManager.isPodActivationCompleted()) {
            // When no Pod is active, return true here in order to prevent AAPS from setting a profile
            // When we activate a new Pod, we just use ProfileFunction to set the currently active profile
            return true;
        }
        return Objects.equals(podStateManager.getBasalSchedule(), AapsOmnipodErosManager.mapProfileToBasalSchedule(profile));
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
        BasalSchedule schedule = podStateManager.getBasalSchedule();
        if (schedule != null) return schedule.rateAt(TimeUtil.toDuration(DateTime.now()));
        else return 0;
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
        if (aapsOmnipodErosManager.isShowRileyLinkBatteryLevel()) {
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
                    .comment(R.string.invalid_input);
        } else if (detailedBolusInfo.insulin > 0) {
            // bolus needed, ask pump to deliver it
            return deliverBolus(detailedBolusInfo);
        } else {
            // no bolus required, carb only treatment
            boolean result = pumpSync.syncCarbsWithTimestamp(
                    detailedBolusInfo.timestamp,
                    detailedBolusInfo.carbs,
                    null,
                    model(),
                    serialNumber());

            aapsLogger.debug(LTag.PUMP, String.format(Locale.ENGLISH, "syncCarbsWithTimestamp " +
                            "[date=%d, carbs=%.2f, pumpSerial=%s] - Result: %b",
                    detailedBolusInfo.timestamp, detailedBolusInfo.carbs, serialNumber(), result));

            return new PumpEnactResult(getInjector()).success(true).enacted(true).bolusDelivered(0d)
                    .carbsDelivered(detailedBolusInfo.carbs);
        }
    }

    @Override
    public void stopBolusDelivering() {
        executeCommand(OmnipodCommandType.CANCEL_BOLUS, aapsOmnipodErosManager::cancelBolus);
    }

    // if enforceNew is true, current temp basal is cancelled and new TBR set (duration is prolonged),
    // if false and the same rate is requested enacted=false and success=true is returned and TBR is not changed
    @Override
    @NonNull
    public PumpEnactResult setTempBasalAbsolute(double absoluteRate, int durationInMinutes, @NonNull Profile profile, boolean enforceNew, @NonNull PumpSync.TemporaryBasalType tbrType) {
        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute: rate: {}, duration={}", absoluteRate, durationInMinutes);

        if (durationInMinutes <= 0 || durationInMinutes % BASAL_STEP_DURATION.getStandardMinutes() != 0) {
            return new PumpEnactResult(getInjector()).success(false).comment(rh.gs(R.string.omnipod_eros_error_set_temp_basal_failed_validation, BASAL_STEP_DURATION.getStandardMinutes()));
        }

        // read current TBR
        PumpSync.PumpState.TemporaryBasal tbrCurrent = readTBR();

        if (tbrCurrent != null) {
            aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute: Current Basal: duration: {} min, rate={}",
                    T.Companion.msecs(tbrCurrent.getDuration()).mins(), tbrCurrent.getRate());
        }

        if (tbrCurrent != null && !enforceNew) {
            if (Round.INSTANCE.isSame(tbrCurrent.getRate(), absoluteRate)) {
                aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - No enforceNew and same rate. Exiting.");
                return new PumpEnactResult(getInjector()).success(true).enacted(false);
            }
        }

        PumpEnactResult result = executeCommand(OmnipodCommandType.SET_TEMPORARY_BASAL, () -> aapsOmnipodErosManager.setTemporaryBasal(new TempBasalPair(absoluteRate, false, durationInMinutes)));

        aapsLogger.info(LTag.PUMP, "setTempBasalAbsolute - setTBR. Response: " + result.getSuccess());

        if (result.getSuccess()) {
            incrementStatistics(OmnipodErosStorageKeys.Statistics.TBRS_SET);
        }

        return result;
    }

    @Override
    @NonNull
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        PumpSync.PumpState.TemporaryBasal tbrCurrent = readTBR();

        if (tbrCurrent == null) {
            aapsLogger.info(LTag.PUMP, "cancelTempBasal - TBR already cancelled.");
            return new PumpEnactResult(getInjector()).success(true).enacted(false);
        }

        return executeCommand(OmnipodCommandType.CANCEL_TEMPORARY_BASAL, aapsOmnipodErosManager::cancelTemporaryBasal);
    }

    // TODO improve (i8n and more)
    @NonNull @Override
    public JSONObject getJSONStatus(@NonNull Profile profile, @NonNull String profileName, @NonNull String version) {

        if (!podStateManager.isPodActivationCompleted() || lastConnectionTimeMillis + 60 * 60 * 1000L < System.currentTimeMillis()) {
            return new JSONObject();
        }

        long now = System.currentTimeMillis();
        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            status.put("status", podStateManager.isPodRunning() ? (podStateManager.isSuspended() ? "suspended" : "normal") : "no active Pod");
            status.put("timestamp", dateUtil.toISOString(dateUtil.now()));

            battery.put("percent", getBatteryLevel());

            extended.put("Version", version);
            try {
                extended.put("ActiveProfile", profileName);
            } catch (Exception ignored) {
            }

            PumpSync.PumpState.TemporaryBasal tb = pumpSync.expectedPumpState().getTemporaryBasal();
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.convertedToAbsolute(now, profile));
                extended.put("TempBasalStart", dateUtil.dateAndTimeString(tb.getTimestamp()));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }
            PumpSync.PumpState.ExtendedBolus eb = pumpSync.expectedPumpState().getExtendedBolus();
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.getRate());
                extended.put("ExtendedBolusStart", dateUtil.dateAndTimeString(eb.getTimestamp()));
                extended.put("ExtendedBolusRemaining", eb.getPlannedRemainingMinutes());
            }

            status.put("timestamp", dateUtil.toISOString(dateUtil.now()));

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

            pump.put("clock", dateUtil.toISOString(podStateManager.getTime().getMillis()));
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
            return rh.gs(R.string.omnipod_common_short_status_no_active_pod);
        }
        String ret = "";
        if (lastConnectionTimeMillis != 0) {
            long agoMsec = System.currentTimeMillis() - lastConnectionTimeMillis;
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += rh.gs(R.string.omnipod_common_short_status_last_connection, agoMin) + "\n";
        }
        if (podStateManager.getLastBolusStartTime() != null) {
            ret += rh.gs(R.string.omnipod_common_short_status_last_bolus, DecimalFormatter.INSTANCE.to2Decimal(podStateManager.getLastBolusAmount()),
                    android.text.format.DateFormat.format("HH:mm", podStateManager.getLastBolusStartTime().toDate())) + "\n";
        }
        PumpSync.PumpState pumpState = pumpSync.expectedPumpState();
        if (pumpState.getTemporaryBasal() != null && pumpState.getProfile() != null) {
            ret += rh.gs(R.string.omnipod_common_short_status_temp_basal, pumpState.getTemporaryBasal().toStringFull(dateUtil) + "\n");
        }
        if (pumpState.getExtendedBolus() != null) {
            ret += rh.gs(R.string.omnipod_common_short_status_extended_bolus, pumpState.getExtendedBolus().toStringFull(dateUtil) + "\n");
        }
        ret += rh.gs(R.string.omnipod_common_short_status_reservoir, (getReservoirLevel() > OmnipodConstants.MAX_RESERVOIR_READING ? "50+" : DecimalFormatter.INSTANCE.to0Decimal(getReservoirLevel()))) + "\n";
        if (isUseRileyLinkBatteryLevel()) {
            ret += rh.gs(R.string.omnipod_eros_short_status_riley_link_battery, getBatteryLevel()) + "\n";
        }
        return ret.trim();
    }

    @Override
    public void executeCustomAction(@NonNull CustomActionType customActionType) {
        aapsLogger.warn(LTag.PUMP, "Unknown custom action: " + customActionType);
    }

    @Override
    public PumpEnactResult executeCustomCommand(@NonNull CustomCommand command) {
        if (!podStateManager.hasPodState())
            return new PumpEnactResult(getInjector()).enacted(false).success(false).comment("Null pod state");
        if (command instanceof CommandSilenceAlerts) {
            return executeCommand(OmnipodCommandType.ACKNOWLEDGE_ALERTS, aapsOmnipodErosManager::acknowledgeAlerts);
        }
        if (command instanceof CommandGetPodStatus) {
            return getPodStatus();
        }
        if (command instanceof CommandReadPulseLog) {
            return retrievePulseLog();
        }
        if (command instanceof CommandSuspendDelivery) {
            return executeCommand(OmnipodCommandType.SUSPEND_DELIVERY, aapsOmnipodErosManager::suspendDelivery);
        }
        if (command instanceof CommandResumeDelivery) {
            return executeCommand(OmnipodCommandType.RESUME_DELIVERY, () -> aapsOmnipodErosManager.setBasalProfile(profileFunction.getProfile(), false));
        }
        if (command instanceof CommandDeactivatePod) {
            return executeCommand(OmnipodCommandType.DEACTIVATE_POD, aapsOmnipodErosManager::deactivatePod);
        }
        if (command instanceof CommandHandleTimeChange) {
            return handleTimeChange(((CommandHandleTimeChange) command).isRequestedByUser());
        }
        if (command instanceof CommandUpdateAlertConfiguration) {
            return updateAlertConfiguration();
        }
        if (command instanceof CommandPlayTestBeep) {
            return executeCommand(OmnipodCommandType.PLAY_TEST_BEEP, () -> aapsOmnipodErosManager.playTestBeep(BeepConfigType.BEEEP));
        }

        aapsLogger.warn(LTag.PUMP, "Unsupported custom command: " + command.getClass().getName());
        return new PumpEnactResult(getInjector()).success(false).enacted(false).comment(rh.gs(R.string.omnipod_common_error_unsupported_custom_command, command.getClass().getName()));
    }

    private PumpEnactResult retrievePulseLog() {
        PodInfoRecentPulseLog result;
        try {
            result = executeCommand(OmnipodCommandType.READ_POD_PULSE_LOG, aapsOmnipodErosManager::readPulseLog);
        } catch (Exception ex) {
            return new PumpEnactResult(getInjector()).success(false).enacted(false).comment(aapsOmnipodErosManager.translateException(ex));
        }

        Intent i = new Intent(context, activityNames.getErrorHelperActivity());
        i.putExtra(AlarmSoundService.SOUND_ID, 0);
        i.putExtra(AlarmSoundService.STATUS, rh.gs(R.string.omnipod_eros_pod_management_pulse_log_value) + ":\n" + result.toString());
        i.putExtra(AlarmSoundService.TITLE, rh.gs(R.string.omnipod_eros_pod_management_pulse_log));
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

        PumpEnactResult result = executeCommand(OmnipodCommandType.CONFIGURE_ALERTS, () -> aapsOmnipodErosManager.configureAlerts(alertConfigurations));

        if (result.getSuccess()) {
            aapsLogger.info(LTag.PUMP, "Successfully configured alerts in Pod");

            podStateManager.setExpirationAlertTimeBeforeShutdown(expirationReminderTimeBeforeShutdown);
            podStateManager.setLowReservoirAlertUnits(lowReservoirAlertUnits);

            Notification notification = new Notification(
                    Notification.OMNIPOD_POD_ALERTS_UPDATED,
                    rh.gs(R.string.omnipod_common_confirmation_expiration_alerts_updated),
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
        if (requestedByUser || aapsOmnipodErosManager.isTimeChangeEventEnabled()) {
            result = executeCommand(OmnipodCommandType.SET_TIME, () -> aapsOmnipodErosManager.setTime(!requestedByUser));
        } else {
            // Even if automatically changing the time is disabled, we still want to at least do a GetStatus request,
            // in order to update the Pod's activation time, which we need for calculating the time on the Pod
            result = getPodStatus();
        }

        if (result.getSuccess()) {
            this.hasTimeDateOrTimeZoneChanged = false;
            timeChangeRetries = 0;

            if (!requestedByUser && aapsOmnipodErosManager.isTimeChangeEventEnabled()) {
                Notification notification = new Notification(
                        Notification.TIME_OR_TIMEZONE_CHANGE,
                        rh.gs(R.string.omnipod_common_confirmation_time_on_pod_updated),
                        Notification.INFO, 60);
                rxBus.send(new EventNewNotification(notification));
            }

        } else {
            if (!requestedByUser) {
                timeChangeRetries++;

                if (timeChangeRetries > 3) {
                    if (aapsOmnipodErosManager.isTimeChangeEventEnabled()) {
                        Notification notification = new Notification(
                                Notification.TIME_OR_TIMEZONE_CHANGE,
                                rh.gs(R.string.omnipod_eros_error_automatic_time_or_timezone_change_failed),
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
        aapsLogger.info(LTag.PUMP, "Time, Date and/or TimeZone changed. [changeType=" + timeChangeType.name() + ", eventHandlingEnabled=" + aapsOmnipodErosManager.isTimeChangeEventEnabled() + "]");

        Instant now = Instant.now();
        if (timeChangeType == TimeChangeType.TimeChanged && now.isBefore(lastTimeDateOrTimeZoneUpdate.plus(Duration.standardDays(1L)))) {
            aapsLogger.info(LTag.PUMP, "Ignoring time change because not a TZ or DST time change and the last one happened less than 24 hours ago.");
            return;
        }
        if (!podStateManager.isPodRunning()) {
            aapsLogger.info(LTag.PUMP, "Ignoring time change because no Pod is active");
            return;
        }

        aapsLogger.info(LTag.PUMP, "DST and/or TimeZone changed event will be consumed by driver");
        lastTimeDateOrTimeZoneUpdate = now;
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
                        rileyLinkServiceData.getRileyLinkServiceState().isError() ||
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

    @Override public void connect(@NonNull String reason) {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "connect (reason={}) [PumpPluginAbstract] - default (empty) implementation." + reason);
    }

    @Override public int waitForDisconnectionInSeconds() {
        return 0;
    }

    @Override public void disconnect(@NonNull String reason) {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "disconnect (reason={}) [PumpPluginAbstract] - default (empty) implementation." + reason);
    }

    @Override public void stopConnecting() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "stopConnecting [PumpPluginAbstract] - default (empty) implementation.");
    }

    @NonNull @Override public PumpEnactResult setTempBasalPercent(int percent, int durationInMinutes, @NonNull Profile profile, boolean enforceNew, @NonNull PumpSync.TemporaryBasalType tbrType) {
        if (percent == 0) {
            return setTempBasalAbsolute(0.0d, durationInMinutes, profile, enforceNew, tbrType);
        } else {
            double absoluteValue = profile.getBasal() * (percent / 100.0d);
            absoluteValue = pumpDescription.getPumpType().determineCorrectBasalSize(absoluteValue);
            aapsLogger.warn(LTag.PUMP, "setTempBasalPercent [OmnipodPumpPlugin] - You are trying to use setTempBasalPercent with percent other then 0% (" + percent + "). This will start setTempBasalAbsolute, with calculated value (" + absoluteValue + "). Result might not be 100% correct.");
            return setTempBasalAbsolute(absoluteValue, durationInMinutes, profile, enforceNew, tbrType);
        }
    }

    @NonNull @Override public PumpEnactResult setExtendedBolus(double insulin, int durationInMinutes) {
        aapsLogger.debug(LTag.PUMP, "setExtendedBolus [OmnipodPumpPlugin] - Not implemented.");
        return getOperationNotSupportedWithCustomText(info.nightscout.pump.common.R.string.pump_operation_not_supported_by_pump_driver);
    }

    @NonNull @Override public PumpEnactResult cancelExtendedBolus() {
        aapsLogger.debug(LTag.PUMP, "cancelExtendedBolus [OmnipodPumpPlugin] - Not implemented.");
        return getOperationNotSupportedWithCustomText(info.nightscout.pump.common.R.string.pump_operation_not_supported_by_pump_driver);
    }

    @NonNull @Override public PumpEnactResult loadTDDs() {
        aapsLogger.debug(LTag.PUMP, "loadTDDs [OmnipodPumpPlugin] - Not implemented.");
        return getOperationNotSupportedWithCustomText(info.nightscout.pump.common.R.string.pump_operation_not_supported_by_pump_driver);
    }

    @Override
    public boolean isUseRileyLinkBatteryLevel() {
        return aapsOmnipodErosManager.isShowRileyLinkBatteryLevel();
    }

    @Override public boolean isBatteryChangeLoggingEnabled() {
        return aapsOmnipodErosManager.isBatteryChangeLoggingEnabled();
    }

    private void initializeAfterRileyLinkConnection() {
        if (podStateManager.getActivationProgress().isAtLeast(ActivationProgress.PAIRING_COMPLETED)) {
            boolean success = false;
            for (int i = 0; STARTUP_STATUS_REQUEST_TRIES > i; i++) {
                PumpEnactResult result = getPodStatus();
                if (result.getSuccess()) {
                    success = true;
                    aapsLogger.debug(LTag.PUMP, "Successfully retrieved Pod status on startup");
                    break;
                }
            }
            if (!success) {
                aapsLogger.warn(LTag.PUMP, "Failed to retrieve Pod status on startup");
                rxBus.send(new EventNewNotification(new Notification(Notification.OMNIPOD_STARTUP_STATUS_REFRESH_FAILED, rh.gs(R.string.omnipod_common_error_failed_to_refresh_status_on_startup), Notification.NORMAL)));
            }
        } else {
            aapsLogger.debug(LTag.PUMP, "Not retrieving Pod status on startup: no Pod running");
        }

        fabricPrivacy.logCustom("OmnipodPumpInit");
    }

    @NonNull private PumpEnactResult deliverBolus(final DetailedBolusInfo detailedBolusInfo) {
        PumpEnactResult result = executeCommand(OmnipodCommandType.SET_BOLUS, () -> aapsOmnipodErosManager.bolus(detailedBolusInfo));

        if (result.getSuccess()) {
            incrementStatistics(detailedBolusInfo.getBolusType() == DetailedBolusInfo.BolusType.SMB ? OmnipodErosStorageKeys.Statistics.SMB_BOLUSES_DELIVERED
                    : OmnipodErosStorageKeys.Statistics.STANDARD_BOLUSES_DELIVERED);

            result.carbsDelivered(detailedBolusInfo.carbs);
        }

        return result;
    }

    @SuppressWarnings("TypeParameterHidesVisibleType")
    private <T> T executeCommand(OmnipodCommandType commandType, Supplier<T> supplier) {
        try {
            aapsLogger.debug(LTag.PUMP, "Executing command: {}", commandType);

            rileyLinkUtil.getRileyLinkHistory().add(new RLHistoryItemOmnipod(getInjector(), commandType));

            return supplier.get();
        } finally {
            rxBus.send(new EventRefreshOverview("Omnipod command: " + commandType.name(), false));
            rxBus.send(new EventOmnipodErosPumpValuesChanged());
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

    @Nullable private PumpSync.PumpState.TemporaryBasal readTBR() {
        return pumpSync.expectedPumpState().getTemporaryBasal();
    }

    private PumpEnactResult getOperationNotSupportedWithCustomText(int resourceId) {
        return new PumpEnactResult(getInjector()).success(false).enacted(false).comment(resourceId);
    }

    @Override public void clearAllTables() {
        erosHistoryDatabase.clearAllTables();
    }
}
