package info.nightscout.androidaps.plugins.pump.omnipod;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.activities.ErrorHelperActivity;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.PumpPluginAbstract;
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.data.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo.PodInfoRecentPulseLog;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCommunicationManagerInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodCustomActionType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodPumpPluginInterface;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.OmnipodStatusRequest;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.state.PodStateManager;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodDriverState;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.OmnipodPumpStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.ui.OmnipodUIComm;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.ui.OmnipodUITask;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.omnipod.events.EventOmnipodRefreshButtonState;
import info.nightscout.androidaps.plugins.pump.omnipod.service.RileyLinkOmnipodService;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodUtil;
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
public class OmnipodPumpPlugin extends PumpPluginAbstract implements OmnipodPumpPluginInterface, RileyLinkPumpDevice {

    // TODO Dagger (maybe done)
    @Inject protected PodStateManager podStateManager;
    private static OmnipodPumpPlugin plugin = null;
    private RileyLinkServiceData rileyLinkServiceData;
    private ServiceTaskExecutor serviceTaskExecutor;
    private RileyLinkOmnipodService rileyLinkOmnipodService;
    private OmnipodUtil omnipodUtil;
    protected OmnipodPumpStatus omnipodPumpStatus = null;
    //protected OmnipodUIComm omnipodUIComm;

    private CompositeDisposable disposable = new CompositeDisposable();


    // variables for handling statuses and history
    protected boolean firstRun = true;
    protected boolean isRefresh = false;
    private boolean isBasalProfileInvalid = false;
    private boolean basalProfileChanged = false;
    private boolean isInitialized = false;
    protected OmnipodCommunicationManagerInterface omnipodCommunicationManager;

    public static boolean isBusy = false;
    protected List<Long> busyTimestamps = new ArrayList<>();
    protected boolean sentIdToFirebase = false;
    protected boolean hasTimeDateOrTimeZoneChanged = false;
    private int timeChangeRetries = 0;

    private Profile currentProfile;

    //boolean omnipodServiceRunning = false;

    private long nextPodCheck = 0L;
    protected boolean isOmnipodEros = true;
    //OmnipodDriverState driverState = OmnipodDriverState.NotInitalized;

    @Inject
    public OmnipodPumpPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            Context context,
            ResourceHelper resourceHelper,
            ActivePluginProvider activePlugin,
            SP sp,
            OmnipodUtil omnipodUtil,
            OmnipodPumpStatus omnipodPumpStatus,
            CommandQueueProvider commandQueue,
            FabricPrivacy fabricPrivacy,
            RileyLinkServiceData rileyLinkServiceData,
            ServiceTaskExecutor serviceTaskExecutor) {

        super(new PluginDescription() //
                        .mainType(PluginType.PUMP) //
                        .fragmentClass(OmnipodFragment.class.getName()) //
                        .pluginName(R.string.omnipod_name) //
                        .shortName(R.string.omnipod_name_short) //
                        .preferencesId(R.xml.pref_omnipod) //
                        .description(R.string.description_pump_omnipod), //
                PumpType.Insulet_Omnipod,
                injector, resourceHelper, aapsLogger, commandQueue, rxBus, activePlugin, sp, context, fabricPrivacy
        );
        injector.androidInjector().inject(this);
        this.rileyLinkServiceData = rileyLinkServiceData;
        this.serviceTaskExecutor = serviceTaskExecutor;

        displayConnectionMessages = false;
        OmnipodPumpPlugin.plugin = this;
        this.omnipodUtil = omnipodUtil;
        this.omnipodPumpStatus = omnipodPumpStatus;

        this.isOmnipodEros = true;
    }

    protected OmnipodPumpPlugin(PluginDescription pluginDescription, PumpType pumpType,
                                HasAndroidInjector injector,
                                AAPSLogger aapsLogger,
                                RxBusWrapper rxBus,
                                Context context,
                                ResourceHelper resourceHelper,
                                ActivePluginProvider activePlugin,
                                info.nightscout.androidaps.utils.sharedPreferences.SP sp,
                                CommandQueueProvider commandQueue,
                                FabricPrivacy fabricPrivacy) {
        super(pluginDescription, pumpType, injector, resourceHelper, aapsLogger, commandQueue, rxBus, activePlugin, sp, context, fabricPrivacy);
    }

    @Deprecated
    public static OmnipodPumpPlugin getPlugin() {
        if (plugin == null)
            throw new IllegalStateException("Plugin not injected jet");
        return plugin;
    }


    @Override
    protected void onStart() {

        //OmnipodUtil.setDriverState();

// TODO loop
//        if (OmnipodUtil.isOmnipodEros()) {
//            OmnipodUtil.setPlugin(this);
//            OmnipodUtil.setOmnipodPodType(OmnipodPodType.Eros);
//            OmnipodUtil.setPumpType(PumpType.Insulet_Omnipod);
//        }

//        // TODO ccc

        if (isOmnipodEros) {

            // We can't do this in PodStateManager itself, because JodaTimeAndroid.init() hasn't been called yet
            // When PodStateManager is created, which causes an IllegalArgumentException for DateTimeZones not being recognized
            podStateManager.loadPodState();

            serviceConnection = new ServiceConnection() {

                @Override
                public void onServiceDisconnected(ComponentName name) {

                    aapsLogger.debug(LTag.PUMP, "RileyLinkOmnipodService is disconnected");
                    rileyLinkOmnipodService = null;
                }

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


//                        if (OmnipodPumpPlugin.this.omnipodPumpStatus != null) {
//
//                            aapsLogger.debug(LTag.PUMP, "Starting OmniPod-RileyLink service");
//                            if (omnipodService.setNotInPreInit()) {
//                                if (omnipodCommunicationManager == null) {
//                                    omnipodCommunicationManager = AapsOmnipodManager.getInstance();
//                                    omnipodCommunicationManager.setPumpStatus(OmnipodPumpPlugin.this.omnipodPumpStatus);
//                                    omnipodServiceRunning = true;
//                                } else {
//                                    omnipodCommunicationManager.setPumpStatus(OmnipodPumpPlugin.this.omnipodPumpStatus);
//                                }
//
//                                omnipodUtil.setOmnipodPodType(OmnipodPodType.Eros);
//                                //omnipodUtil.setPlugin(OmnipodPumpPlugin.this);
//
//                                omnipodUIComm = new OmnipodUIComm(omnipodCommunicationManager, plugin, OmnipodPumpPlugin.this.omnipodPumpStatus);
//                                break;
//                            }
//                        }
//
//                        SystemClock.sleep(5000);
                        //}
                    }).start();
                }
            };
        }


        disposable.add(rxBus
                .toObservable(EventPreferenceChange.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    if ((event.isChanged(getResourceHelper(), R.string.key_omnipod_beep_basal_enabled)) ||
                            (event.isChanged(getResourceHelper(), R.string.key_omnipod_beep_bolus_enabled)) ||
                            (event.isChanged(getResourceHelper(), R.string.key_omnipod_beep_tbr_enabled)) ||
                            (event.isChanged(getResourceHelper(), R.string.key_omnipod_pod_debugging_options_enabled)) ||
                            (event.isChanged(getResourceHelper(), R.string.key_omnipod_beep_smb_enabled)) ||
                            (event.isChanged(getResourceHelper(), R.string.key_omnipod_timechange_enabled)))
                        rileyLinkOmnipodService.verifyConfiguration();
                }, fabricPrivacy::logException)
        );

        super.onStart();


        //rileyLinkOmnipodService.verifyConfiguration();
        //initPumpStatusData();
    }

//    @Override
//    protected void onResume() {
//
//    }

//    private void refreshConfiguration() {
//        if (pumpStatusLocal != null) {
//            pumpStatusLocal.refreshConfiguration();
//        }
//        verifyConfiguration()
//    }

    @Override
    protected void onStop() {
        disposable.clear();
        super.onStop();
    }

    private String getLogPrefix() {
        return "OmnipodPlugin::";
    }


    @Override
    public void initPumpStatusData() {

        omnipodPumpStatus.lastConnection = sp.getLong(RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);
        omnipodPumpStatus.lastDataTime = omnipodPumpStatus.lastConnection;
        omnipodPumpStatus.previousConnection = omnipodPumpStatus.lastConnection;

        if (rileyLinkOmnipodService != null) rileyLinkOmnipodService.verifyConfiguration();

        aapsLogger.debug(LTag.PUMP, "initPumpStatusData: " + this.omnipodPumpStatus);

        // set first Omnipod Pump Start
        if (!sp.contains(OmnipodConst.Statistics.FirstPumpStart)) {
            sp.putLong(OmnipodConst.Statistics.FirstPumpStart, System.currentTimeMillis());
        }

    }


    @Override
    public void onStartCustomActions() {

        // check status every minute (if any status needs refresh we send readStatus command)
        new Thread(() -> {

            do {
                SystemClock.sleep(60000);

                if (this.isInitialized) {
                    clearBusyQueue();
                }

                if (!this.omnipodStatusRequestList.isEmpty() || this.hasTimeDateOrTimeZoneChanged) {
                    if (!getCommandQueue().statusInQueue()) {
                        getCommandQueue().readStatus("Status Refresh Requested", null);
                    }
                }

                doPodCheck();

            } while (serviceRunning);

        }).start();
    }

    private void doPodCheck() {
        if (System.currentTimeMillis() > this.nextPodCheck) {
            if (omnipodUtil.getDriverState() == OmnipodDriverState.Initalized_NoPod) {
                Notification notification = new Notification(Notification.OMNIPOD_POD_NOT_ATTACHED, resourceHelper.gs(R.string.omnipod_error_pod_not_attached), Notification.NORMAL);
                rxBus.send(new EventNewNotification(notification));
            } else {
                rxBus.send(new EventDismissNotification(Notification.OMNIPOD_POD_NOT_ATTACHED));
            }

            this.nextPodCheck = DateTimeUtil.getTimeInFutureFromMinutes(15);
        }
    }


    @Override
    public Class getServiceClass() {
        return RileyLinkOmnipodService.class;
    }

    @Override
    public PumpStatus getPumpStatusData() {
        return this.omnipodPumpStatus;
    }


    @Override
    public String deviceID() {
        return "Omnipod";
    }


    // Pump Plugin

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
    public boolean isBusy() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, getLogPrefix() + "isBusy");

        if (isServiceSet()) {

            if (isBusy || !omnipodPumpStatus.podAvailable)
                return true;

            if (busyTimestamps.size() > 0) {

                clearBusyQueue();

                return (busyTimestamps.size() > 0);
            }
        }

        return false;
    }


    @Override
    public void resetRileyLinkConfiguration() {
        rileyLinkOmnipodService.resetRileyLinkConfiguration();
    }


    @Override
    public boolean hasTuneUp() {
        return false;
    }


    @Override
    public void doTuneUpDevice() {
        //rileyLinkOmnipodService.doTuneUpDevice();
    }


    @Override
    public RileyLinkOmnipodService getRileyLinkService() {
        return rileyLinkOmnipodService;
    }

    @Override
    public OmnipodUIComm getDeviceCommandExecutor() {
        return rileyLinkOmnipodService.getDeviceCommandExecutor();
    }


    private synchronized void clearBusyQueue() {

        if (busyTimestamps.size() == 0) {
            return;
        }

        Set<Long> deleteFromQueue = new HashSet<>();

        for (Long busyTimestamp : busyTimestamps) {

            if (System.currentTimeMillis() > busyTimestamp) {
                deleteFromQueue.add(busyTimestamp);
            }
        }

        if (deleteFromQueue.size() == busyTimestamps.size()) {
            busyTimestamps.clear();
            //setEnableCustomAction(MedtronicCustomActionType.ClearBolusBlock, false);
        }

        if (deleteFromQueue.size() > 0) {
            busyTimestamps.removeAll(deleteFromQueue);
        }

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
    public boolean isSuspended() {

        return omnipodUtil.getDriverState() == OmnipodDriverState.Initalized_NoPod ||
                !podStateManager.isSetupCompleted() || podStateManager.isSuspended();

//        return (pumpStatusLocal != null && !pumpStatusLocal.podAvailable) ||
//                (omnipodUtil.getPodStateManager().hasState() && OmnipodUtil.getPodStateManager().isSuspended());
//
// TODO ddd
//        return (OmnipodUtil.getDriverState() == OmnipodDriverState.Initalized_NoPod) ||
//                (omnipodUtil.getPodStateManager().hasState() && OmnipodUtil.getPodStateManager().isSuspended());
//
//        return (pumpStatusLocal != null && !pumpStatusLocal.podAvailable) ||
//                (omnipodUtil.getPodStateManager().hasState() && OmnipodUtil.getPodStateManager().isSuspended());
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

                        Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                        i.putExtra("soundid", 0);
                        i.putExtra("status", "Pulse Log (copied to clipboard):\n" + result.toString());
                        i.putExtra("title", resourceHelper.gs(R.string.combo_warning));
                        i.putExtra("clipboardContent", result.toString());
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        MainApp.instance().startActivity(i);

//                        OKDialog.show(MainApp.instance().getApplicationContext(), MainApp.gs(R.string.action),
//                                "Pulse Log:\n" + result.toString(), null);
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
                        resourceHelper.gs(R.string.time_or_timezone_change),
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


    public void setIsBusy(boolean isBusy_) {
        isBusy = isBusy_;
    }


    private void getPodPumpStatus() {
        // TODO read pod status
        aapsLogger.error(LTag.PUMP, "getPodPumpStatus() NOT IMPLEMENTED");

        //addPodStatusRequest(OmnipodStatusRequest.GetPodState);

        //getPodPumpStatusObject().driverState = OmnipodDriverState.Initalized_PodAvailable;
        //driverState = OmnipodDriverState.Initalized_PodAvailable;
        // FIXME this does not seem to make sense
        omnipodUtil.setDriverState(OmnipodDriverState.Initalized_PodAttached);
        // we would probably need to read Basal Profile here too
    }


    List<OmnipodStatusRequest> omnipodStatusRequestList = new ArrayList<>();

    public void addPodStatusRequest(OmnipodStatusRequest pumpStatusRequest) {
        if (pumpStatusRequest == OmnipodStatusRequest.ResetState) {
            resetStatusState();
        } else {
            omnipodStatusRequestList.add(pumpStatusRequest);
        }
    }

    @Override
    public void setDriverState(OmnipodDriverState state) {
        //this.driverState = state;
    }


    public void resetStatusState() {
        firstRun = true;
        isRefresh = true;
    }


    private void setRefreshButtonEnabled(boolean enabled) {
        rxBus.send(new EventOmnipodRefreshButtonState(enabled));
    }


    private void initializePump(boolean realInit) {
        aapsLogger.info(LTag.PUMP, getLogPrefix() + "initializePump - start");

        // TODO ccc
        //OmnipodPumpStatus podPumpStatus = getPodPumpStatusObject();

        setRefreshButtonEnabled(false);

        if (podStateManager.isPaired()) {
            aapsLogger.debug(LTag.PUMP, "PodStateManager (saved): " + podStateManager);

            if (!isRefresh) {
                pumpState = PumpDriverState.Initialized;
            }

            // TODO handle if session state too old
            getPodPumpStatus();
        } else {
            aapsLogger.debug(LTag.PUMP, "No Pod running");
            omnipodUtil.setDriverState(OmnipodDriverState.Initalized_NoPod);
        }

        finishAction("Omnipod Pump");

//        if (!sentIdToFirebase) {
//            Bundle params = new Bundle();
//            params.putString("version", BuildConfig.VERSION);
//            MainApp.getFirebaseAnalytics().logEvent("OmnipodPumpInit", params);
//
//            sentIdToFirebase = true;
//        }

        isInitialized = true;

        this.firstRun = false;
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


    @Override
    protected void triggerUIChange() {
        rxBus.send(new EventOmnipodPumpValuesChanged());
    }


    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }


    @Override
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

                // we subtract insulin, exact amount will be visible with next remainingInsulin update.
//                if (getPodPumpStatusObject().reservoirRemainingUnits != 0 &&
//                        getPodPumpStatusObject().reservoirRemainingUnits != 75 ) {
//                    getPodPumpStatusObject().reservoirRemainingUnits -= detailedBolusInfo.insulin;
//                }

                incrementStatistics(detailedBolusInfo.isSMB ? OmnipodConst.Statistics.SMBBoluses
                        : OmnipodConst.Statistics.StandardBoluses);

                result.carbsDelivered(detailedBolusInfo.carbs);
            }

            return result;
        } finally {
            finishAction("Bolus");
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


    private void incrementStatistics(String statsKey) {
        long currentCount = sp.getLong(statsKey, 0L);
        currentCount++;
        sp.putLong(statsKey, currentCount);
    }


    // if enforceNew===true current temp basal is canceled and new TBR set (duration is prolonged),
    // if false and the same rate is requested enacted=false and success=true is returned and TBR is not changed
    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile,
                                                boolean enforceNew) {

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

        // if TBR is running we will cancel it.
//        if (tbrCurrent != null) {
//            
//                aapsLogger.info(LTag.PUMP,getLogPrefix() + "setTempBasalAbsolute - TBR running - so canceling it.");
//
//            // CANCEL
//            OmnipodUITask responseTask2 = omnipodUIComm.executeCommand(OmnipodCommandType.CancelTemporaryBasal);
//
//            PumpEnactResult result = responseTask2.getResult();
//
//            if (result.success) {
//                
//                    aapsLogger.info(LTag.PUMP,getLogPrefix() + "setTempBasalAbsolute - Current TBR cancelled.");
//            } else {
//                
//                    aapsLogger.error(LTag.PUMP,getLogPrefix() + "setTempBasalAbsolute - Cancel TBR failed.");
//
//                finishAction("TBR");
//
//                return result;
//            }
//        }

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

    @NotNull
    @Override
    public String serialNumber() {
        return StringUtils.isNotBlank(omnipodPumpStatus.podNumber) ?
                omnipodPumpStatus.podNumber : "None";
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
                    .comment(resourceHelper.gs(R.string.medtronic_cmd_basal_profile_not_set_is_same));
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


    // OPERATIONS not supported by Pump or Plugin

    protected List<CustomAction> customActions = null;

    private CustomAction customActionResetRLConfig = new CustomAction(
            R.string.medtronic_custom_action_reset_rileylink, OmnipodCustomActionType.ResetRileyLinkConfiguration, true);


    @Override
    public List<CustomAction> getCustomActions() {

        if (customActions == null) {
            this.customActions = Arrays.asList(
                    customActionResetRLConfig //,
            );
        }

        return this.customActions;
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
        aapsLogger.warn(LTag.PUMP, getLogPrefix() + "Time, Date and/or TimeZone changed. [changeType=" + timeChangeType.name() + ", eventHandlingEnabled=" + omnipodPumpStatus.timeChangeEventEnabled + "]");

        if (omnipodUtil.getDriverState() == OmnipodDriverState.Initalized_PodAttached) {
            if (omnipodPumpStatus.timeChangeEventEnabled) {
                aapsLogger.info(LTag.PUMP, getLogPrefix() + "Time,and/or TimeZone changed event received and will be consumed by driver.");
                this.hasTimeDateOrTimeZoneChanged = true;
            }
        }
    }

    @Override
    public boolean isUnreachableAlertTimeoutExceeded(long unreachableTimeoutMilliseconds) {
        if (omnipodPumpStatus.lastConnection != 0 || omnipodPumpStatus.lastErrorConnection != 0) {
            if (omnipodPumpStatus.lastConnection + unreachableTimeoutMilliseconds < System.currentTimeMillis()) {
                if (omnipodPumpStatus.lastErrorConnection > omnipodPumpStatus.lastConnection) {
                    // We exceeded the alert threshold, and our last connection failed
                    // We should show an alert
                    return true;
                }

                // Don't trigger an alert when we exceeded the thresholds, but the last communication was successful
                // This happens when we simply didn't need to send any commands to the pump
            }
        }

        return false;
    }


    @Override
    public RxBusWrapper getRxBus() {
        return this.rxBus;
    }


}
