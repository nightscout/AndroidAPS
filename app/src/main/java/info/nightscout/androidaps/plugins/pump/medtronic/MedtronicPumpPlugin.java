package info.nightscout.androidaps.plugins.pump.medtronic;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventCustomActionsChanged;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.activities.ErrorHelperActivity;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.PumpPluginAbstract;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.MedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryResult;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUIComm;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.ui.MedtronicUITask;
import info.nightscout.androidaps.plugins.pump.medtronic.data.MedtronicHistoryData;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.BasalProfileEntry;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.ClockDTO;
import info.nightscout.androidaps.plugins.pump.medtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.BasalProfileStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicCustomActionType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicNotificationType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicStatusRefreshType;
import info.nightscout.androidaps.plugins.pump.medtronic.defs.MedtronicUIResponseType;
import info.nightscout.androidaps.plugins.pump.medtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventRefreshButtonState;
import info.nightscout.androidaps.plugins.pump.medtronic.service.RileyLinkMedtronicService;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.SP;

import static info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil.sendNotification;

/**
 * Created by andy on 23.04.18.
 *
 * @author Andy Rozman (andy.rozman@gmail.com)
 */
public class MedtronicPumpPlugin extends PumpPluginAbstract implements PumpInterface {

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMP);

    protected static MedtronicPumpPlugin plugin = null;
    private RileyLinkMedtronicService medtronicService;
    private MedtronicPumpStatus pumpStatusLocal = null;
    private MedtronicUIComm medtronicUIComm = new MedtronicUIComm();

    // variables for handling statuses and history
    private boolean firstRun = true;
    private boolean isRefresh = false;
    private boolean isBasalProfileInvalid = false;
    private boolean basalProfileChanged = false;
    private Map<MedtronicStatusRefreshType, Long> statusRefreshMap = new HashMap<>();
    private boolean isInitialized = false;
    private MedtronicHistoryData medtronicHistoryData;
    private MedtronicCommunicationManager medtronicCommunicationManager;
    private PumpHistoryEntry lastPumpHistoryEntry;

    public static boolean isBusy = false;
    private List<Long> busyTimestamps = new ArrayList<>();
    private boolean sentIdToFirebase = false;
    private boolean hasTimeDateOrTimeZoneChanged = false;


    private MedtronicPumpPlugin() {

        super(new PluginDescription() //
                        .mainType(PluginType.PUMP) //
                        .fragmentClass(MedtronicFragment.class.getName()) //
                        .pluginName(R.string.medtronic_name) //
                        .shortName(R.string.medtronic_name_short) //
                        .preferencesId(R.xml.pref_medtronic).description(R.string.description_pump_medtronic), //
                PumpType.Medtronic_522_722 // we default to most basic model, correct model from config is loaded later
        );

        displayConnectionMessages = false;

        medtronicHistoryData = new MedtronicHistoryData();

        serviceConnection = new ServiceConnection() {

            public void onServiceDisconnected(ComponentName name) {
                if (isLoggingEnabled())
                    LOG.debug("RileyLinkMedtronicService is disconnected");
                medtronicService = null;
            }

            public void onServiceConnected(ComponentName name, IBinder service) {
                if (isLoggingEnabled())
                    LOG.debug("RileyLinkMedtronicService is connected");
                RileyLinkMedtronicService.LocalBinder mLocalBinder = (RileyLinkMedtronicService.LocalBinder) service;
                medtronicService = mLocalBinder.getServiceInstance();

                new Thread(() -> {

                    for (int i = 0; i < 20; i++) {
                        SystemClock.sleep(5000);

                        if (MedtronicUtil.getPumpStatus() != null) {
                            if (isLoggingEnabled())
                                LOG.debug("Starting Medtronic-RileyLink service");
                            if (MedtronicUtil.getPumpStatus().setNotInPreInit()) {
                                break;
                            }
                        }
                    }
                }).start();
            }
        };
    }


    public static MedtronicPumpPlugin getPlugin() {
        if (plugin == null)
            plugin = new MedtronicPumpPlugin();
        return plugin;
    }


    private String getLogPrefix() {
        return "MedtronicPumpPlugin::";
    }


    public MedtronicHistoryData getMedtronicHistoryData() {
        return this.medtronicHistoryData;
    }


    @Override
    public void initPumpStatusData() {

        this.pumpStatusLocal = new MedtronicPumpStatus(pumpDescription);
        MedtronicUtil.setPumpStatus(pumpStatusLocal);

        pumpStatusLocal.lastConnection = SP.getLong(RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);
        pumpStatusLocal.lastDataTime = new LocalDateTime(pumpStatusLocal.lastConnection);
        pumpStatusLocal.previousConnection = pumpStatusLocal.lastConnection;

        pumpStatusLocal.refreshConfiguration();

        if (isLoggingEnabled())
            LOG.debug("initPumpStatusData: {}", this.pumpStatusLocal);

        this.pumpStatus = pumpStatusLocal;

        // this is only thing that can change, by being configured
        pumpDescription.maxTempAbsolute = (pumpStatusLocal.maxBasal != null) ? pumpStatusLocal.maxBasal : 35.0d;

        // set first Medtronic Pump Start
        if (!SP.contains(MedtronicConst.Statistics.FirstPumpStart)) {
            SP.putLong(MedtronicConst.Statistics.FirstPumpStart, System.currentTimeMillis());
        }

        migrateSettings();

    }


    private void migrateSettings() {

        if ("US (916 MHz)".equals(SP.getString(MedtronicConst.Prefs.PumpFrequency, null))) {
            SP.putString(MedtronicConst.Prefs.PumpFrequency, MainApp.gs(R.string.key_medtronic_pump_frequency_us_ca));
        }

        String encoding = SP.getString(MedtronicConst.Prefs.Encoding, null);

        if ("RileyLink 4b6b Encoding".equals(encoding)) {
            SP.putString(MedtronicConst.Prefs.Encoding, MainApp.gs(R.string.key_medtronic_pump_encoding_4b6b_rileylink));
        }

        if ("Local 4b6b Encoding".equals(encoding)) {
            SP.putString(MedtronicConst.Prefs.Encoding, MainApp.gs(R.string.key_medtronic_pump_encoding_4b6b_local));
        }
    }


    public void onStartCustomActions() {

        // check status every minute (if any status needs refresh we send readStatus command)
        new Thread(() -> {

            do {
                SystemClock.sleep(60000);

                if (this.isInitialized) {

                    Map<MedtronicStatusRefreshType, Long> statusRefresh = workWithStatusRefresh(
                            StatusRefreshAction.GetData, null, null);

                    if (doWeHaveAnyStatusNeededRefereshing(statusRefresh)) {
                        if (!ConfigBuilderPlugin.getPlugin().getCommandQueue().statusInQueue()) {
                            ConfigBuilderPlugin.getPlugin().getCommandQueue()
                                    .readStatus("Scheduled Status Refresh", null);
                        }
                    }

                    clearBusyQueue();
                }

            } while (serviceRunning);

        }).start();
    }


    public Class getServiceClass() {
        return RileyLinkMedtronicService.class;
    }


    @Override
    public String deviceID() {
        return "Medtronic";
    }


    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }


    @Override
    public boolean canHandleDST() {
        return false;
    }


    // Pump Plugin

    private boolean isServiceSet() {
        return medtronicService != null;
    }


    @Override
    public boolean isInitialized() {
        if (isLoggingEnabled() && displayConnectionMessages)
            LOG.debug("MedtronicPumpPlugin::isInitialized");
        return isServiceSet() && isInitialized;
    }


    @Override
    public boolean isBusy() {
        if (isLoggingEnabled() && displayConnectionMessages)
            LOG.debug("MedtronicPumpPlugin::isBusy");

        if (isServiceSet()) {

            if (isBusy)
                return true;

            if (busyTimestamps.size() > 0) {

                clearBusyQueue();

                if (busyTimestamps.size() > 0) {
                    return true;
                }
            }
        }

        return false;
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
            setEnableCustomAction(MedtronicCustomActionType.ClearBolusBlock, false);
        }

        if (deleteFromQueue.size() > 0) {
            busyTimestamps.removeAll(deleteFromQueue);
        }

    }


    @Override
    public boolean isConnected() {
        if (isLoggingEnabled() && displayConnectionMessages)
            LOG.debug("MedtronicPumpPlugin::isConnected");
        return isServiceSet() && medtronicService.isInitialized();
    }


    @Override
    public boolean isConnecting() {
        if (isLoggingEnabled() && displayConnectionMessages)
            LOG.debug("MedtronicPumpPlugin::isConnecting");
        return !isServiceSet() || !medtronicService.isInitialized();
    }


    @Override
    public void getPumpStatus() {

        getMDTPumpStatus();

        if (firstRun) {
            initializePump(!isRefresh);
        } else {
            refreshAnyStatusThatNeedsToBeRefreshed();
        }

        RxBus.INSTANCE.send(new EventMedtronicPumpValuesChanged());
    }


    void resetStatusState() {
        firstRun = true;
        isRefresh = true;
    }


    private boolean isPumpNotReachable() {

        RileyLinkServiceState rileyLinkServiceState = MedtronicUtil.getServiceState();

        if (rileyLinkServiceState == null) {
            LOG.error("RileyLink unreachable. RileyLinkServiceState is null.");
            return false;
        }

        if (rileyLinkServiceState != RileyLinkServiceState.PumpConnectorReady //
                && rileyLinkServiceState != RileyLinkServiceState.RileyLinkReady //
                && rileyLinkServiceState != RileyLinkServiceState.TuneUpDevice) {
            LOG.error("RileyLink unreachable.");
            return false;
        }

        return (!medtronicCommunicationManager.isDeviceReachable());
    }


    private void refreshAnyStatusThatNeedsToBeRefreshed() {

        Map<MedtronicStatusRefreshType, Long> statusRefresh = workWithStatusRefresh(StatusRefreshAction.GetData, null,
                null);

        if (!doWeHaveAnyStatusNeededRefereshing(statusRefresh)) {
            return;
        }

        boolean resetTime = false;

        if (isPumpNotReachable()) {
            if (isLoggingEnabled())
                LOG.error("Pump unreachable.");
            MedtronicUtil.sendNotification(MedtronicNotificationType.PumpUnreachable);

            return;
        }

        MedtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable);


        if (hasTimeDateOrTimeZoneChanged) {

            checkTimeAndOptionallySetTime();

            // read time if changed, set new time
            hasTimeDateOrTimeZoneChanged = false;
        }


        // execute
        Set<MedtronicStatusRefreshType> refreshTypesNeededToReschedule = new HashSet<>();

        for (Map.Entry<MedtronicStatusRefreshType, Long> refreshType : statusRefresh.entrySet()) {

            if (refreshType.getValue() > 0 && System.currentTimeMillis() > refreshType.getValue()) {

                switch (refreshType.getKey()) {
                    case PumpHistory: {
                        readPumpHistory();
                    }
                    break;

                    case PumpTime: {
                        checkTimeAndOptionallySetTime();
                        refreshTypesNeededToReschedule.add(refreshType.getKey());
                        resetTime = true;
                    }
                    break;

                    case BatteryStatus:
                    case RemainingInsulin: {
                        medtronicUIComm.executeCommand(refreshType.getKey().getCommandType());
                        refreshTypesNeededToReschedule.add(refreshType.getKey());
                        resetTime = true;
                    }
                    break;

                    case Configuration: {
                        medtronicUIComm.executeCommand(refreshType.getKey().getCommandType());
                        resetTime = true;
                    }
                    break;
                }
            }

            // reschedule
            for (MedtronicStatusRefreshType refreshType2 : refreshTypesNeededToReschedule) {
                scheduleNextRefresh(refreshType2);
            }

        }

        if (resetTime)
            pumpStatusLocal.setLastCommunicationToNow();

    }


    private boolean doWeHaveAnyStatusNeededRefereshing(Map<MedtronicStatusRefreshType, Long> statusRefresh) {

        for (Map.Entry<MedtronicStatusRefreshType, Long> refreshType : statusRefresh.entrySet()) {

            if (refreshType.getValue() > 0 && System.currentTimeMillis() > refreshType.getValue()) {
                return true;
            }
        }

        return hasTimeDateOrTimeZoneChanged;
    }


    private void setRefreshButtonEnabled(boolean enabled) {
        RxBus.INSTANCE.send(new EventRefreshButtonState(enabled));
    }


    private void initializePump(boolean realInit) {

        if (isLoggingEnabled())
            LOG.info(getLogPrefix() + "initializePump - start");

        if (medtronicCommunicationManager == null) {
            medtronicCommunicationManager = MedtronicCommunicationManager.getInstance();
            medtronicCommunicationManager.setDoWakeUpBeforeCommand(false);
        }

        setRefreshButtonEnabled(false);

        getMDTPumpStatus();

        if (isRefresh) {
            if (isPumpNotReachable()) {
                if (isLoggingEnabled())
                    LOG.error(getLogPrefix() + "initializePump::Pump unreachable.");
                MedtronicUtil.sendNotification(MedtronicNotificationType.PumpUnreachable);

                setRefreshButtonEnabled(true);

                return;
            }

            MedtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable);
        }

        // model (once)
        if (MedtronicUtil.getMedtronicPumpModel() == null) {
            medtronicUIComm.executeCommand(MedtronicCommandType.PumpModel);
        } else {
            if (pumpStatusLocal.medtronicDeviceType != MedtronicUtil.getMedtronicPumpModel()) {
                if (isLoggingEnabled())
                    LOG.warn(getLogPrefix() + "Configured pump is not the same as one detected.");
                MedtronicUtil.sendNotification(MedtronicNotificationType.PumpTypeNotSame);
            }
        }

        this.pumpState = PumpDriverState.Connected;

        // time (1h)
        checkTimeAndOptionallySetTime();

        readPumpHistory();

        // remaining insulin (>50 = 4h; 50-20 = 1h; 15m)
        medtronicUIComm.executeCommand(MedtronicCommandType.GetRemainingInsulin);
        scheduleNextRefresh(MedtronicStatusRefreshType.RemainingInsulin, 10);

        // remaining power (1h)
        medtronicUIComm.executeCommand(MedtronicCommandType.GetBatteryStatus);
        scheduleNextRefresh(MedtronicStatusRefreshType.BatteryStatus, 20);

        // configuration (once and then if history shows config changes)
        medtronicUIComm.executeCommand(MedtronicCommandType.getSettings(MedtronicUtil.getMedtronicPumpModel()));

        // read profile (once, later its controlled by isThisProfileSet method)
        getBasalProfiles();

        int errorCount = medtronicUIComm.getInvalidResponsesCount();

        if (errorCount >= 5) {
            if (isLoggingEnabled())
                LOG.error("Number of error counts was 5 or more. Starting tunning.");
            setRefreshButtonEnabled(true);
            ServiceTaskExecutor.startTask(new WakeAndTuneTask());
            return;
        }

        pumpStatusLocal.setLastCommunicationToNow();
        setRefreshButtonEnabled(true);

        if (!isRefresh) {
            pumpState = PumpDriverState.Initialized;
        }

        if (!sentIdToFirebase) {
            Bundle params = new Bundle();
            params.putString("version", BuildConfig.VERSION);
            MainApp.getFirebaseAnalytics().logEvent("MedtronicPumpInit", params);

            sentIdToFirebase = true;
        }

        isInitialized = true;
        // this.pumpState = PumpDriverState.Initialized;

        this.firstRun = false;
    }

    private void getBasalProfiles() {

        MedtronicUITask medtronicUITask = medtronicUIComm.executeCommand(MedtronicCommandType.GetBasalProfileSTD);

        if (medtronicUITask.getResponseType() == MedtronicUIResponseType.Error) {
            medtronicUIComm.executeCommand(MedtronicCommandType.GetBasalProfileSTD);
        }
    }


    @Override
    public boolean isThisProfileSet(Profile profile) {
        MedtronicPumpStatus mdtPumpStatus = getMDTPumpStatus();
        LOG.debug("isThisProfileSet: basalInitalized={}", mdtPumpStatus.basalProfileStatus);

        if (!isInitialized)
            return true;

        if (mdtPumpStatus.basalProfileStatus == BasalProfileStatus.NotInitialized) {
            // this shouldn't happen, but if there was problem we try again
            getBasalProfiles();
            return isProfileSame(profile);
        } else if (mdtPumpStatus.basalProfileStatus == BasalProfileStatus.ProfileChanged) {
            return false;
        } else {

        }


        return (getMDTPumpStatus().basalProfileStatus != BasalProfileStatus.ProfileOK) || isProfileSame(profile);
    }


    private boolean isProfileSame(Profile profile) {

        boolean invalid = false;
        Double[] basalsByHour = getMDTPumpStatus().basalsByHour;
        PumpType pumpType = getMDTPumpStatus().getPumpType();

        if (isLoggingEnabled())
            LOG.debug("Current Basals (h):   "
                    + (basalsByHour == null ? "null" : BasalProfile.getProfilesByHourToString(basalsByHour)));

        // int index = 0;

        if (basalsByHour == null)
            return true; // we don't want to set profile again, unless we are sure

        StringBuilder stringBuilder = new StringBuilder("Requested Basals (h): ");

        for (Profile.ProfileValue basalValue : profile.getBasalValues()) {

            double basalValueValue = pumpType.determineCorrectBasalSize(basalValue.value);

            int hour = basalValue.timeAsSeconds / (60 * 60);

            if (!MedtronicUtil.isSame(basalsByHour[hour], basalValueValue)) {
                invalid = true;
            }

            stringBuilder.append(String.format(Locale.ENGLISH, "%.3f", basalValueValue));
            stringBuilder.append(" ");
        }

        if (isLoggingEnabled()) {
            LOG.debug(stringBuilder.toString());

            if (!invalid) {
                LOG.debug("Basal profile is same as AAPS one.");
            } else {
                LOG.debug("Basal profile on Pump is different than the AAPS one.");
            }
        }

        return (!invalid);
    }


    @Override
    public long lastDataTime() {
        getMDTPumpStatus();

        if (pumpStatusLocal.lastConnection != 0) {
            return pumpStatusLocal.lastConnection;
        }

        return System.currentTimeMillis();
    }


    @Override
    public double getBaseBasalRate() {
        return getMDTPumpStatus().getBasalProfileForHour();
    }


    @Override
    public double getReservoirLevel() {
        return getMDTPumpStatus().reservoirRemainingUnits;
    }


    @Override
    public int getBatteryLevel() {
        return getMDTPumpStatus().batteryRemaining;
    }


    private MedtronicPumpStatus getMDTPumpStatus() {
        if (pumpStatusLocal == null) {
            // FIXME I don't know why this happens
            if (isLoggingEnabled())
                LOG.warn("!!!! Reset Pump Status Local");
            pumpStatusLocal = MedtronicUtil.getPumpStatus();
        }

        return pumpStatusLocal;
    }


    protected void triggerUIChange() {
        RxBus.INSTANCE.send(new EventMedtronicPumpValuesChanged());
    }

    private BolusDeliveryType bolusDeliveryType = BolusDeliveryType.Idle;

    private enum BolusDeliveryType {
        Idle, //
        DeliveryPrepared, //
        Delivering, //
        CancelDelivery
    }


    private void checkTimeAndOptionallySetTime() {

        if (isLoggingEnabled())
            LOG.info("MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Start");

        setRefreshButtonEnabled(false);

        if (isPumpNotReachable()) {
            LOG.debug("MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Pump Unreachable.");
            setRefreshButtonEnabled(true);
            return;
        }

        MedtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable);

        medtronicUIComm.executeCommand(MedtronicCommandType.GetRealTimeClock);

        ClockDTO clock = MedtronicUtil.getPumpTime();

        if (clock == null) { // retry
            medtronicUIComm.executeCommand(MedtronicCommandType.GetRealTimeClock);

            clock = MedtronicUtil.getPumpTime();
        }

        if (clock == null)
            return;

        int timeDiff = Math.abs(clock.timeDifference);

        if (timeDiff > 20) {

            if ((clock.localDeviceTime.getYear() <= 2015) || (timeDiff <= 24 * 60 * 60)) {

                if (isLoggingEnabled())
                    LOG.info("MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference is {} s. Set time on pump.", timeDiff);

                medtronicUIComm.executeCommand(MedtronicCommandType.SetRealTimeClock);

                if (clock.timeDifference == 0) {
                    Notification notification = new Notification(Notification.INSIGHT_DATE_TIME_UPDATED, MainApp.gs(R.string.pump_time_updated), Notification.INFO, 60);
                    RxBus.INSTANCE.send(new EventNewNotification(notification));
                }
            } else {
                if ((clock.localDeviceTime.getYear() > 2015)) {
                    LOG.error("MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference over 24h requested [diff={}]. Doing nothing.", timeDiff);
                    sendNotification(MedtronicNotificationType.TimeChangeOver24h);
                }
            }

        } else {
            if (isLoggingEnabled())
                LOG.info("MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference is {} s. Do nothing.", timeDiff);
        }

        scheduleNextRefresh(MedtronicStatusRefreshType.PumpTime, 0);
    }


    @NonNull
    protected PumpEnactResult deliverBolus(final DetailedBolusInfo detailedBolusInfo) {

        LOG.info("MedtronicPumpPlugin::deliverBolus - {}", BolusDeliveryType.DeliveryPrepared);

        setRefreshButtonEnabled(false);

        MedtronicPumpStatus mdtPumpStatus = getMDTPumpStatus();

        if (detailedBolusInfo.insulin > mdtPumpStatus.reservoirRemainingUnits) {
            return new PumpEnactResult() //
                    .success(false) //
                    .enacted(false) //
                    .comment(MainApp.gs(R.string.medtronic_cmd_bolus_could_not_be_delivered_no_insulin,
                            mdtPumpStatus.reservoirRemainingUnits,
                            detailedBolusInfo.insulin));
        }

        bolusDeliveryType = BolusDeliveryType.DeliveryPrepared;

        if (isPumpNotReachable()) {
            LOG.debug("MedtronicPumpPlugin::deliverBolus - Pump Unreachable.");
            return setNotReachable(true, false);
        }

        MedtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable);

        if (bolusDeliveryType == BolusDeliveryType.CancelDelivery) {
            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled.");
            return setNotReachable(true, true);
        }

        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Starting wait period.");

        int sleepTime = SP.getInt(MedtronicConst.Prefs.BolusDelay, 10) * 1000;

        SystemClock.sleep(sleepTime);

        if (bolusDeliveryType == BolusDeliveryType.CancelDelivery) {
            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled, before wait period.");
            return setNotReachable(true, true);
        }

        // LOG.debug("MedtronicPumpPlugin::deliverBolus - End wait period. Start delivery");

        try {

            bolusDeliveryType = BolusDeliveryType.Delivering;

            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Start delivery");

            MedtronicUITask responseTask = medtronicUIComm.executeCommand(MedtronicCommandType.SetBolus,
                    detailedBolusInfo.insulin);

            Boolean response = (Boolean) responseTask.returnData;

            setRefreshButtonEnabled(true);

            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Response: {}", response);

            if (response) {

                if (bolusDeliveryType == BolusDeliveryType.CancelDelivery) {
                    // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled after Bolus started.");

                    new Thread(() -> {
                        // Looper.prepare();
                        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Show dialog - before");
                        SystemClock.sleep(2000);
                        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Show dialog. Context: "
                        // + MainApp.instance().getApplicationContext());

                        Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                        i.putExtra("soundid", R.raw.boluserror);
                        i.putExtra("status", MainApp.gs(R.string.medtronic_cmd_cancel_bolus_not_supported));
                        i.putExtra("title", MainApp.gs(R.string.combo_warning));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        MainApp.instance().startActivity(i);

                    }).start();
                }

                long now = System.currentTimeMillis();

                detailedBolusInfo.date = now;
                detailedBolusInfo.deliverAt = now; // not sure about that one

                TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, true);

                // we subtract insulin, exact amount will be visible with next remainingInsulin update.
                getMDTPumpStatus().reservoirRemainingUnits -= detailedBolusInfo.insulin;

                incrementStatistics(detailedBolusInfo.isSMB ? MedtronicConst.Statistics.SMBBoluses
                        : MedtronicConst.Statistics.StandardBoluses);


                // calculate time for bolus and set driver to busy for that time
                int bolusTime = (int) (detailedBolusInfo.insulin * 42.0d);
                long time = now + (bolusTime * 1000);

                this.busyTimestamps.add(time);
                setEnableCustomAction(MedtronicCustomActionType.ClearBolusBlock, true);

                return new PumpEnactResult().success(true) //
                        .enacted(true) //
                        .bolusDelivered(detailedBolusInfo.insulin) //
                        .carbsDelivered(detailedBolusInfo.carbs);

            } else {
                return new PumpEnactResult() //
                        .success(bolusDeliveryType == BolusDeliveryType.CancelDelivery) //
                        .enacted(false) //
                        .comment(MainApp.gs(R.string.medtronic_cmd_bolus_could_not_be_delivered));
            }

        } finally {
            finishAction("Bolus");
            this.bolusDeliveryType = BolusDeliveryType.Idle;
        }
    }


    private PumpEnactResult setNotReachable(boolean isBolus, boolean success) {
        setRefreshButtonEnabled(true);

        if (isBolus) {
            bolusDeliveryType = BolusDeliveryType.Idle;
        }

        if (success) {
            return new PumpEnactResult() //
                    .success(true) //
                    .enacted(false);
        } else {
            return new PumpEnactResult() //
                    .success(false) //
                    .enacted(false) //
                    .comment(MainApp.gs(R.string.medtronic_pump_status_pump_unreachable));
        }
    }


    public void stopBolusDelivering() {

        this.bolusDeliveryType = BolusDeliveryType.CancelDelivery;

        // if (isLoggingEnabled())
        // LOG.warn("MedtronicPumpPlugin::deliverBolus - Stop Bolus Delivery.");
    }


    private void incrementStatistics(String statsKey) {
        long currentCount = SP.getLong(statsKey, 0L);
        currentCount++;
        SP.putLong(statsKey, currentCount);
    }


    // if enforceNew===true current temp basal is canceled and new TBR set (duration is prolonged),
    // if false and the same rate is requested enacted=false and success=true is returned and TBR is not changed
    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile,
                                                boolean enforceNew) {

        setRefreshButtonEnabled(false);

        if (isPumpNotReachable()) {

            setRefreshButtonEnabled(true);

            return new PumpEnactResult() //
                    .success(false) //
                    .enacted(false) //
                    .comment(MainApp.gs(R.string.medtronic_pump_status_pump_unreachable));
        }

        MedtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable);

        getMDTPumpStatus();

        if (isLoggingEnabled())
            LOG.info(getLogPrefix() + "setTempBasalAbsolute: rate: {}, duration={}", absoluteRate, durationInMinutes);

        // read current TBR
        TempBasalPair tbrCurrent = readTBR();

        if (tbrCurrent == null) {
            if (isLoggingEnabled())
                LOG.warn(getLogPrefix() + "setTempBasalAbsolute - Could not read current TBR, canceling operation.");
            finishAction("TBR");
            return new PumpEnactResult().success(false).enacted(false)
                    .comment(MainApp.gs(R.string.medtronic_cmd_cant_read_tbr));
        } else {
            if (isLoggingEnabled())
                LOG.info(getLogPrefix() + "setTempBasalAbsolute: Current Basal: duration: {} min, rate={}",
                        tbrCurrent.getDurationMinutes(), tbrCurrent.getInsulinRate());
        }

        if (!enforceNew) {

            if (MedtronicUtil.isSame(tbrCurrent.getInsulinRate(), absoluteRate)) {

                boolean sameRate = true;
                if (MedtronicUtil.isSame(0.0d, absoluteRate) && durationInMinutes > 0) {
                    // if rate is 0.0 and duration>0 then the rate is not the same
                    sameRate = false;
                }

                if (sameRate) {
                    if (isLoggingEnabled())
                        LOG.info(getLogPrefix() + "setTempBasalAbsolute - No enforceNew and same rate. Exiting.");
                    finishAction("TBR");
                    return new PumpEnactResult().success(true).enacted(false);
                }
            }
            // if not the same rate, we cancel and start new
        }

        // if TBR is running we will cancel it.
        if (tbrCurrent.getInsulinRate() != 0.0f && tbrCurrent.getDurationMinutes() > 0) {
            if (isLoggingEnabled())
                LOG.info(getLogPrefix() + "setTempBasalAbsolute - TBR running - so canceling it.");

            // CANCEL

            MedtronicUITask responseTask2 = medtronicUIComm.executeCommand(MedtronicCommandType.CancelTBR);

            Boolean response = (Boolean) responseTask2.returnData;

            if (response) {
                if (isLoggingEnabled())
                    LOG.info(getLogPrefix() + "setTempBasalAbsolute - Current TBR cancelled.");
            } else {
                if (isLoggingEnabled())
                    LOG.error(getLogPrefix() + "setTempBasalAbsolute - Cancel TBR failed.");

                finishAction("TBR");

                return new PumpEnactResult().success(false).enacted(false)
                        .comment(MainApp.gs(R.string.medtronic_cmd_cant_cancel_tbr_stop_op));
            }
        }

        // now start new TBR
        MedtronicUITask responseTask = medtronicUIComm.executeCommand(MedtronicCommandType.SetTemporaryBasal,
                absoluteRate, durationInMinutes);

        Boolean response = (Boolean) responseTask.returnData;

        if (isLoggingEnabled())
            LOG.info(getLogPrefix() + "setTempBasalAbsolute - setTBR. Response: " + response);

        if (response) {
            // FIXME put this into UIPostProcessor
            pumpStatusLocal.tempBasalStart = new Date();
            pumpStatusLocal.tempBasalAmount = absoluteRate;
            pumpStatusLocal.tempBasalLength = durationInMinutes;

            TemporaryBasal tempStart = new TemporaryBasal() //
                    .date(System.currentTimeMillis()) //
                    .duration(durationInMinutes) //
                    .absolute(absoluteRate) //
                    .source(Source.USER);

            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStart);

            incrementStatistics(MedtronicConst.Statistics.TBRsSet);

            finishAction("TBR");

            return new PumpEnactResult().success(true).enacted(true) //
                    .absolute(absoluteRate).duration(durationInMinutes);

        } else {
            finishAction("TBR");

            return new PumpEnactResult().success(false).enacted(false) //
                    .comment(MainApp.gs(R.string.medtronic_cmd_tbr_could_not_be_delivered));
        }

    }


    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile,
                                               boolean enforceNew) {
        if (percent == 0) {
            return setTempBasalAbsolute(0.0d, durationInMinutes, profile, enforceNew);
        } else {
            double absoluteValue = profile.getBasal() * (percent / 100.0d);
            getMDTPumpStatus();
            absoluteValue = pumpStatusLocal.pumpType.determineCorrectBasalSize(absoluteValue);
            LOG.warn("setTempBasalPercent [MedtronicPumpPlugin] - You are trying to use setTempBasalPercent with percent other then 0% (%d). This will start setTempBasalAbsolute, with calculated value (%.3f). Result might not be 100% correct.", percent, absoluteValue);
            return setTempBasalAbsolute(absoluteValue, durationInMinutes, profile, enforceNew);
        }
    }


    private void finishAction(String overviewKey) {

        if (overviewKey != null)
            RxBus.INSTANCE.send(new EventRefreshOverview(overviewKey));

        triggerUIChange();

        setRefreshButtonEnabled(true);
    }


    private void readPumpHistory() {

//        if (isLoggingEnabled())
//            LOG.error(getLogPrefix() + "readPumpHistory WIP.");

        readPumpHistoryLogic();

        scheduleNextRefresh(MedtronicStatusRefreshType.PumpHistory);

        if (medtronicHistoryData.hasRelevantConfigurationChanged()) {
            scheduleNextRefresh(MedtronicStatusRefreshType.Configuration, -1);
        }

        if (medtronicHistoryData.hasPumpTimeChanged()) {
            scheduleNextRefresh(MedtronicStatusRefreshType.PumpTime, -1);
        }

        if (this.getMDTPumpStatus().basalProfileStatus != BasalProfileStatus.NotInitialized
                && medtronicHistoryData.hasBasalProfileChanged()) {
            medtronicHistoryData.processLastBasalProfileChange(getMDTPumpStatus());
        }

        PumpDriverState previousState = this.pumpState;

        if (medtronicHistoryData.isPumpSuspended()) {
            this.pumpState = PumpDriverState.Suspended;
            if (isLoggingEnabled())
                LOG.debug(getLogPrefix() + "isPumpSuspended: true");
        } else {
            if (previousState == PumpDriverState.Suspended) {
                this.pumpState = PumpDriverState.Ready;
            }
            if (isLoggingEnabled())
                LOG.debug(getLogPrefix() + "isPumpSuspended: false");
        }

        medtronicHistoryData.processNewHistoryData();

        this.medtronicHistoryData.finalizeNewHistoryRecords();
        // this.medtronicHistoryData.setLastHistoryRecordTime(this.lastPumpHistoryEntry.atechDateTime);

    }


    private void readPumpHistoryLogic() {

        LocalDateTime targetDate = null;

        if (lastPumpHistoryEntry == null) {

            if (isLoggingEnabled())
                LOG.debug(getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntry: null");

            Long lastPumpHistoryEntryTime = getLastPumpEntryTime();

            LocalDateTime timeMinus36h = new LocalDateTime();
            timeMinus36h = timeMinus36h.minusHours(36);
            medtronicHistoryData.setIsInInit(true);

            if (lastPumpHistoryEntryTime == 0L) {
                if (isLoggingEnabled())
                    LOG.debug(getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntryTime: 0L - targetDate: "
                            + targetDate);
                targetDate = timeMinus36h;
            } else {
                // LocalDateTime lastHistoryRecordTime = DateTimeUtil.toLocalDateTime(lastPumpHistoryEntryTime);

                if (isLoggingEnabled())
                    LOG.debug(getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntryTime: {} - targetDate: {}",
                            lastPumpHistoryEntryTime, targetDate);

                medtronicHistoryData.setLastHistoryRecordTime(lastPumpHistoryEntryTime);

                LocalDateTime lastHistoryRecordTime = DateTimeUtil.toLocalDateTime(lastPumpHistoryEntryTime);

                lastHistoryRecordTime = lastHistoryRecordTime.minusHours(12); // we get last 12 hours of history to
                // determine pump state
                // (we don't process that data), we process only

                if (timeMinus36h.isAfter(lastHistoryRecordTime)) {
                    targetDate = timeMinus36h;
                }

                targetDate = (timeMinus36h.isAfter(lastHistoryRecordTime) ? timeMinus36h : lastHistoryRecordTime);

                if (isLoggingEnabled())
                    LOG.debug(getLogPrefix() + "readPumpHistoryLogic(): targetDate: " + targetDate);
            }
        } else {
            if (isLoggingEnabled())
                LOG.debug(getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntry: not null - {}",
                        MedtronicUtil.gsonInstance.toJson(lastPumpHistoryEntry));
            medtronicHistoryData.setIsInInit(false);
            // medtronicHistoryData.setLastHistoryRecordTime(lastPumpHistoryEntry.atechDateTime);

            // targetDate = lastPumpHistoryEntry.atechDateTime;
        }

        LOG.debug("HST: Target Date: {}", targetDate);

        MedtronicUITask responseTask2 = medtronicUIComm.executeCommand(MedtronicCommandType.GetHistoryData,
                lastPumpHistoryEntry, targetDate);

        LOG.debug("HST: After task");

        PumpHistoryResult historyResult = (PumpHistoryResult) responseTask2.returnData;

        LOG.debug("HST: History Result: {}", historyResult.toString());

        PumpHistoryEntry latestEntry = historyResult.getLatestEntry();

        if (isLoggingEnabled())
            LOG.debug(getLogPrefix() + "Last entry: " + latestEntry);

        if (latestEntry == null) // no new history to read
            return;

        this.lastPumpHistoryEntry = latestEntry;
        SP.putLong(MedtronicConst.Statistics.LastPumpHistoryEntry, latestEntry.atechDateTime);

        LOG.debug("HST: History: valid={}, unprocessed={}", historyResult.validEntries.size(),
                historyResult.unprocessedEntries.size());

        this.medtronicHistoryData.addNewHistory(historyResult);
        this.medtronicHistoryData.filterNewEntries();

        // determine if first run, if yes detrmine how much of update do we need
        // first run:
        // get last hiostory entry, if not there download 1.5 days of data
        // - there: check if last entry is older than 1.5 days
        // - yes: download 1.5 days
        // - no: download with last entry
        // - not there: download 1.5 days
        //
        // upload all new entries to NightScout (TBR, Bolus)
        // determine pump status
        //
        // save last entry
        //
        // not first run:
        // update to last entry
        // - save
        // - determine pump status

        //

    }

    private Long getLastPumpEntryTime() {
        Long lastPumpEntryTime = SP.getLong(MedtronicConst.Statistics.LastPumpHistoryEntry, 0L);

        try {
            LocalDateTime localDateTime = DateTimeUtil.toLocalDateTime(lastPumpEntryTime);

            if (localDateTime.getYear() != (new GregorianCalendar().get(Calendar.YEAR))) {
                LOG.warn("Saved LastPumpHistoryEntry was invalid. Year was not the same.");
                return 0L;
            }

            return lastPumpEntryTime;

        } catch (Exception ex) {
            LOG.warn("Saved LastPumpHistoryEntry was invalid.");
            return 0L;
        }

    }


    private void scheduleNextRefresh(MedtronicStatusRefreshType refreshType) {
        scheduleNextRefresh(refreshType, 0);
    }


    private void scheduleNextRefresh(MedtronicStatusRefreshType refreshType, int additionalTimeInMinutes) {
        switch (refreshType) {

            case RemainingInsulin: {
                double remaining = pumpStatusLocal.reservoirRemainingUnits;
                int min;
                if (remaining > 50)
                    min = 4 * 60;
                else if (remaining > 20)
                    min = 60;
                else
                    min = 15;

                workWithStatusRefresh(StatusRefreshAction.Add, refreshType, getTimeInFutureFromMinutes(min));
            }
            break;

            case PumpTime:
            case Configuration:
            case BatteryStatus:
            case PumpHistory: {
                workWithStatusRefresh(StatusRefreshAction.Add, refreshType,
                        getTimeInFutureFromMinutes(refreshType.getRefreshTime() + additionalTimeInMinutes));
            }
            break;
        }
    }

    private enum StatusRefreshAction {
        Add, //
        GetData
    }


    private synchronized Map<MedtronicStatusRefreshType, Long> workWithStatusRefresh(StatusRefreshAction action, //
                                                                                     MedtronicStatusRefreshType statusRefreshType, //
                                                                                     Long time) {

        switch (action) {

            case Add: {
                statusRefreshMap.put(statusRefreshType, time);
                return null;
            }

            case GetData: {
                return new HashMap<>(statusRefreshMap);
            }

            default:
                return null;

        }

    }


    private long getTimeInFutureFromMinutes(int minutes) {
        return System.currentTimeMillis() + getTimeInMs(minutes);
    }


    private long getTimeInMs(int minutes) {
        return minutes * 60 * 1000L;
    }


    private TempBasalPair readTBR() {
        MedtronicUITask responseTask = medtronicUIComm.executeCommand(MedtronicCommandType.ReadTemporaryBasal);

        if (responseTask.hasData()) {
            TempBasalPair tbr = (TempBasalPair) responseTask.returnData;

            // we sometimes get rate returned even if TBR is no longer running
            if (tbr.getDurationMinutes() == 0) {
                tbr.setInsulinRate(0.0d);
            }

            return tbr;
        } else {
            return null;
        }
    }


    @Override
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {

        if (isLoggingEnabled())
            LOG.info(getLogPrefix() + "cancelTempBasal - started");

        if (isPumpNotReachable()) {

            setRefreshButtonEnabled(true);

            return new PumpEnactResult() //
                    .success(false) //
                    .enacted(false) //
                    .comment(MainApp.gs(R.string.medtronic_pump_status_pump_unreachable));
        }

        MedtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable);
        setRefreshButtonEnabled(false);

        TempBasalPair tbrCurrent = readTBR();

        if (tbrCurrent != null) {
            if (tbrCurrent.getInsulinRate() == 0.0f && tbrCurrent.getDurationMinutes() == 0) {
                if (isLoggingEnabled())
                    LOG.info(getLogPrefix() + "cancelTempBasal - TBR already canceled.");
                finishAction("TBR");
                return new PumpEnactResult().success(true).enacted(false);
            }
        } else {
            if (isLoggingEnabled())
                LOG.warn(getLogPrefix() + "cancelTempBasal - Could not read currect TBR, canceling operation.");
            finishAction("TBR");
            return new PumpEnactResult().success(false).enacted(false)
                    .comment(MainApp.gs(R.string.medtronic_cmd_cant_read_tbr));
        }

        MedtronicUITask responseTask2 = medtronicUIComm.executeCommand(MedtronicCommandType.CancelTBR);

        Boolean response = (Boolean) responseTask2.returnData;

        finishAction("TBR");

        if (response) {
            if (isLoggingEnabled())
                LOG.info(getLogPrefix() + "cancelTempBasal - Cancel TBR successful.");

            TemporaryBasal tempBasal = new TemporaryBasal() //
                    .date(System.currentTimeMillis()) //
                    .duration(0) //
                    .source(Source.USER);

            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempBasal);

            return new PumpEnactResult().success(true).enacted(true) //
                    .isTempCancel(true);
        } else {
            if (isLoggingEnabled())
                LOG.info(getLogPrefix() + "cancelTempBasal - Cancel TBR failed.");

            return new PumpEnactResult().success(response).enacted(response) //
                    .comment(MainApp.gs(R.string.medtronic_cmd_cant_cancel_tbr));
        }
    }

    @Override
    public ManufacturerType manufacturer() {
        return getMDTPumpStatus().pumpType.getManufacturer();
    }

    @Override
    public PumpType model() {
        return getMDTPumpStatus().pumpType;
    }

    @Override
    public String serialNumber() {
        return getMDTPumpStatus().serialNumber;
    }

    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        if (isLoggingEnabled())
            LOG.info(getLogPrefix() + "setNewBasalProfile");

        // this shouldn't be needed, but let's do check if profile setting we are setting is same as current one
        if (isProfileSame(profile)) {
            return new PumpEnactResult() //
                    .success(true) //
                    .enacted(false) //
                    .comment(MainApp.gs(R.string.medtronic_cmd_basal_profile_not_set_is_same));
        }

        setRefreshButtonEnabled(false);

        if (isPumpNotReachable()) {

            setRefreshButtonEnabled(true);

            return new PumpEnactResult() //
                    .success(false) //
                    .enacted(false) //
                    .comment(MainApp.gs(R.string.medtronic_pump_status_pump_unreachable));
        }

        MedtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable);

        BasalProfile basalProfile = convertProfileToMedtronicProfile(profile);

        String profileInvalid = isProfileValid(basalProfile);

        if (profileInvalid != null) {
            return new PumpEnactResult() //
                    .success(false) //
                    .enacted(false) //
                    .comment(MainApp.gs(R.string.medtronic_cmd_set_profile_pattern_overflow, profileInvalid));
        }

        MedtronicUITask responseTask = medtronicUIComm.executeCommand(MedtronicCommandType.SetBasalProfileSTD,
                basalProfile);

        Boolean response = (Boolean) responseTask.returnData;

        if (isLoggingEnabled())
            LOG.info(getLogPrefix() + "Basal Profile was set: " + response);

        if (response) {
            return new PumpEnactResult().success(true).enacted(true);
        } else {
            return new PumpEnactResult().success(response).enacted(response) //
                    .comment(MainApp.gs(R.string.medtronic_cmd_basal_profile_could_not_be_set));
        }
    }


    private String isProfileValid(BasalProfile basalProfile) {

        StringBuilder stringBuilder = new StringBuilder();

        MedtronicPumpStatus pumpStatus = getMDTPumpStatus();

        if (pumpStatus.maxBasal == null)
            return null;

        for (BasalProfileEntry profileEntry : basalProfile.getEntries()) {

            if (profileEntry.rate > pumpStatus.maxBasal) {
                stringBuilder.append(profileEntry.startTime.toString("HH:mm"));
                stringBuilder.append("=");
                stringBuilder.append(profileEntry.rate);
            }
        }

        return stringBuilder.length() == 0 ? null : stringBuilder.toString();
    }


    @NonNull
    private BasalProfile convertProfileToMedtronicProfile(Profile profile) {

        MedtronicPumpStatus pumpStatus = getMDTPumpStatus();

        PumpType pumpType = pumpStatus.pumpType;

        BasalProfile basalProfile = new BasalProfile();

        for (int i = 0; i < 24; i++) {
            double rate = profile.getBasalTimeFromMidnight(i * 60 * 60);

            double v = pumpType.determineCorrectBasalSize(rate);

            BasalProfileEntry basalEntry = new BasalProfileEntry(v, i, 0);
            basalProfile.addEntry(basalEntry);

        }

        basalProfile.generateRawDataFromEntries();

        return basalProfile;
    }

    // OPERATIONS not supported by Pump or Plugin

    private List<CustomAction> customActions = null;

    private CustomAction customActionWakeUpAndTune = new CustomAction(R.string.medtronic_custom_action_wake_and_tune,
            MedtronicCustomActionType.WakeUpAndTune);

    private CustomAction customActionClearBolusBlock = new CustomAction(
            R.string.medtronic_custom_action_clear_bolus_block, MedtronicCustomActionType.ClearBolusBlock, false);

    private CustomAction customActionResetRLConfig = new CustomAction(
            R.string.medtronic_custom_action_reset_rileylink, MedtronicCustomActionType.ResetRileyLinkConfiguration, true);


    @Override
    public List<CustomAction> getCustomActions() {

        if (customActions == null) {
            this.customActions = Arrays.asList(customActionWakeUpAndTune, //
                    customActionClearBolusBlock, //
                    customActionResetRLConfig);
        }

        return this.customActions;
    }


    @Override
    public void executeCustomAction(CustomActionType customActionType) {

        MedtronicCustomActionType mcat = (MedtronicCustomActionType) customActionType;

        switch (mcat) {

            case WakeUpAndTune: {
                if (MedtronicUtil.getPumpStatus().verifyConfiguration()) {
                    ServiceTaskExecutor.startTask(new WakeAndTuneTask());
                } else {
                    Intent i = new Intent(MainApp.instance(), ErrorHelperActivity.class);
                    i.putExtra("soundid", R.raw.boluserror);
                    i.putExtra("status", MainApp.gs(R.string.medtronic_error_operation_not_possible_no_configuration));
                    i.putExtra("title", MainApp.gs(R.string.combo_warning));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    MainApp.instance().startActivity(i);
                }
            }
            break;

            case ClearBolusBlock: {
                this.busyTimestamps.clear();
                this.customActionClearBolusBlock.setEnabled(false);
                refreshCustomActionsList();
            }
            break;

            case ResetRileyLinkConfiguration: {
                ServiceTaskExecutor.startTask(new ResetRileyLinkConfigurationTask());
            }
            break;

            default:
                break;
        }

    }

    @Override
    public void timeDateOrTimeZoneChanged() {

        if (isLoggingEnabled())
            LOG.warn(getLogPrefix() + "Time, Date and/or TimeZone changed. ");

        this.hasTimeDateOrTimeZoneChanged = true;
    }

    private void refreshCustomActionsList() {
        RxBus.INSTANCE.send(new EventCustomActionsChanged());
    }


    public void setEnableCustomAction(MedtronicCustomActionType customAction, boolean isEnabled) {

        if (customAction == MedtronicCustomActionType.ClearBolusBlock) {
            this.customActionClearBolusBlock.setEnabled(isEnabled);
        } else if (customAction == MedtronicCustomActionType.ResetRileyLinkConfiguration) {
            this.customActionResetRLConfig.setEnabled(isEnabled);
        }

        refreshCustomActionsList();
    }


}
