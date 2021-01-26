package info.nightscout.androidaps.plugins.pump.medtronic;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import org.jetbrains.annotations.NotNull;
import org.joda.time.LocalDateTime;

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

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.activities.ErrorHelperActivity;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.PumpPluginAbstract;
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.common.events.EventRefreshButtonState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpDevice;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkPumpInfo;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.RileyLinkServiceData;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ResetRileyLinkConfigurationTask;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.service.tasks.WakeAndTuneTask;
import info.nightscout.androidaps.plugins.pump.common.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryEntry;
import info.nightscout.androidaps.plugins.pump.medtronic.comm.history.pump.PumpHistoryResult;
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
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpConfigurationChanged;
import info.nightscout.androidaps.plugins.pump.medtronic.events.EventMedtronicPumpValuesChanged;
import info.nightscout.androidaps.plugins.pump.medtronic.service.RileyLinkMedtronicService;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicConst;
import info.nightscout.androidaps.plugins.pump.medtronic.util.MedtronicUtil;
import info.nightscout.androidaps.queue.commands.CustomCommand;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.TimeChangeType;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;


/**
 * Created by andy on 23.04.18.
 *
 * @author Andy Rozman (andy.rozman@gmail.com)
 */
@Singleton
public class MedtronicPumpPlugin extends PumpPluginAbstract implements PumpInterface, RileyLinkPumpDevice {

    private final SP sp;
    private final RileyLinkUtil rileyLinkUtil;
    private final MedtronicUtil medtronicUtil;
    private final MedtronicPumpStatus medtronicPumpStatus;
    private final MedtronicHistoryData medtronicHistoryData;
    private final RileyLinkServiceData rileyLinkServiceData;
    private final ServiceTaskExecutor serviceTaskExecutor;

    private RileyLinkMedtronicService rileyLinkMedtronicService;

    // variables for handling statuses and history
    private boolean firstRun = true;
    private boolean isRefresh = false;
    private final Map<MedtronicStatusRefreshType, Long> statusRefreshMap = new HashMap<>();
    private boolean isInitialized = false;
    private PumpHistoryEntry lastPumpHistoryEntry;

    public static boolean isBusy = false;
    private final List<Long> busyTimestamps = new ArrayList<>();
    private boolean hasTimeDateOrTimeZoneChanged = false;


    @Inject
    public MedtronicPumpPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            Context context,
            ResourceHelper resourceHelper,
            ActivePluginProvider activePlugin,
            SP sp,
            CommandQueueProvider commandQueue,
            FabricPrivacy fabricPrivacy,
            RileyLinkUtil rileyLinkUtil,
            MedtronicUtil medtronicUtil,
            MedtronicPumpStatus medtronicPumpStatus,
            MedtronicHistoryData medtronicHistoryData,
            RileyLinkServiceData rileyLinkServiceData,
            ServiceTaskExecutor serviceTaskExecutor,
            DateUtil dateUtil
    ) {

        super(new PluginDescription() //
                        .mainType(PluginType.PUMP) //
                        .fragmentClass(MedtronicFragment.class.getName()) //
                        .pluginIcon(R.drawable.ic_veo_128)
                        .pluginName(R.string.medtronic_name) //
                        .shortName(R.string.medtronic_name_short) //
                        .preferencesId(R.xml.pref_medtronic)
                        .description(R.string.description_pump_medtronic), //
                PumpType.Medtronic_522_722, // we default to most basic model, correct model from config is loaded later
                injector, resourceHelper, aapsLogger, commandQueue, rxBus, activePlugin, sp, context, fabricPrivacy, dateUtil
        );

        this.rileyLinkUtil = rileyLinkUtil;
        this.medtronicUtil = medtronicUtil;
        this.sp = sp;
        this.medtronicPumpStatus = medtronicPumpStatus;
        this.medtronicHistoryData = medtronicHistoryData;
        this.rileyLinkServiceData = rileyLinkServiceData;
        this.serviceTaskExecutor = serviceTaskExecutor;

        displayConnectionMessages = false;
    }


    @Override
    protected void onStart() {
        aapsLogger.debug(LTag.PUMP, this.deviceID() + " started.");

        serviceConnection = new ServiceConnection() {

            public void onServiceDisconnected(ComponentName name) {
                aapsLogger.debug(LTag.PUMP, "RileyLinkMedtronicService is disconnected");
                rileyLinkMedtronicService = null;
            }

            public void onServiceConnected(ComponentName name, IBinder service) {
                aapsLogger.debug(LTag.PUMP, "RileyLinkMedtronicService is connected");
                RileyLinkMedtronicService.LocalBinder mLocalBinder = (RileyLinkMedtronicService.LocalBinder) service;
                rileyLinkMedtronicService = mLocalBinder.getServiceInstance();
                rileyLinkMedtronicService.verifyConfiguration();

                new Thread(() -> {

                    for (int i = 0; i < 20; i++) {
                        SystemClock.sleep(5000);

                        aapsLogger.debug(LTag.PUMP, "Starting Medtronic-RileyLink service");
                        if (rileyLinkMedtronicService.setNotInPreInit()) {
                            break;
                        }
                    }
                }).start();
            }
        };

        super.onStart();
    }

    @Override
    public void updatePreferenceSummary(@NotNull Preference pref) {
        super.updatePreferenceSummary(pref);

        if (pref.getKey().equals(getResourceHelper().gs(R.string.key_rileylink_mac_address))) {
            String value = sp.getStringOrNull(R.string.key_rileylink_mac_address, null);
            pref.setSummary(value == null ? getResourceHelper().gs(R.string.not_set_short) : value);
        }
    }

    private String getLogPrefix() {
        return "MedtronicPumpPlugin::";
    }

    @Override
    public void initPumpStatusData() {

        medtronicPumpStatus.lastConnection = sp.getLong(RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);
        medtronicPumpStatus.lastDataTime = medtronicPumpStatus.lastConnection;
        medtronicPumpStatus.previousConnection = medtronicPumpStatus.lastConnection;

        //if (rileyLinkMedtronicService != null) rileyLinkMedtronicService.verifyConfiguration();

        aapsLogger.debug(LTag.PUMP, "initPumpStatusData: " + this.medtronicPumpStatus);

        // this is only thing that can change, by being configured
        pumpDescription.maxTempAbsolute = (medtronicPumpStatus.maxBasal != null) ? medtronicPumpStatus.maxBasal : 35.0d;

        // set first Medtronic Pump Start
        if (!sp.contains(MedtronicConst.Statistics.FirstPumpStart)) {
            sp.putLong(MedtronicConst.Statistics.FirstPumpStart, System.currentTimeMillis());
        }

        migrateSettings();

    }

    @Override
    public void triggerPumpConfigurationChangedEvent() {
        rxBus.send(new EventMedtronicPumpConfigurationChanged());
    }

    private void migrateSettings() {

        if ("US (916 MHz)".equals(sp.getString(MedtronicConst.Prefs.PumpFrequency, "US (916 MHz)"))) {
            sp.putString(MedtronicConst.Prefs.PumpFrequency, getResourceHelper().gs(R.string.key_medtronic_pump_frequency_us_ca));
        }

        String encoding = sp.getString(MedtronicConst.Prefs.Encoding, "RileyLink 4b6b Encoding");

        if ("RileyLink 4b6b Encoding".equals(encoding)) {
            sp.putString(MedtronicConst.Prefs.Encoding, getResourceHelper().gs(R.string.key_medtronic_pump_encoding_4b6b_rileylink));
        }

        if ("Local 4b6b Encoding".equals(encoding)) {
            sp.putString(MedtronicConst.Prefs.Encoding, getResourceHelper().gs(R.string.key_medtronic_pump_encoding_4b6b_local));
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
                        if (!getCommandQueue().statusInQueue()) {
                            getCommandQueue().readStatus("Scheduled Status Refresh", null);
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

    @Override public PumpStatus getPumpStatusData() {
        return medtronicPumpStatus;
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
        return rileyLinkMedtronicService != null;
    }

    @Nullable
    public RileyLinkMedtronicService getRileyLinkService() {
        return rileyLinkMedtronicService;
    }

    @Override public RileyLinkPumpInfo getPumpInfo() {
        String frequency = resourceHelper.gs(medtronicPumpStatus.pumpFrequency.equals("medtronic_pump_frequency_us_ca") ? R.string.medtronic_pump_frequency_us_ca : R.string.medtronic_pump_frequency_worldwide);
        String model = medtronicPumpStatus.medtronicDeviceType == null ? "???" : "Medtronic " + medtronicPumpStatus.medtronicDeviceType.getPumpModel();
        String serialNumber = medtronicPumpStatus.serialNumber;
        return new RileyLinkPumpInfo(frequency, model, serialNumber);
    }

    @Override public long getLastConnectionTimeMillis() {
        return medtronicPumpStatus.lastConnection;
    }

    @Override public void setLastCommunicationToNow() {
        medtronicPumpStatus.setLastCommunicationToNow();
    }

    @Override
    public boolean isInitialized() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::isInitialized");
        return isServiceSet() && isInitialized;
    }


    @Override
    public void setBusy(boolean busy) {
        isBusy = busy;
    }

    @Override
    public boolean isBusy() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::isBusy");

        if (isServiceSet()) {

            if (isBusy)
                return true;

            if (busyTimestamps.size() > 0) {

                clearBusyQueue();

                return busyTimestamps.size() > 0;
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
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::isConnected");
        return isServiceSet() && rileyLinkMedtronicService.isInitialized();
    }


    @Override
    public boolean isConnecting() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::isConnecting");
        return !isServiceSet() || !rileyLinkMedtronicService.isInitialized();
    }


    @Override
    public void getPumpStatus(String reason) {
        boolean needRefresh = true;

        if (firstRun) {
            needRefresh = initializePump(!isRefresh);
        } else {
            refreshAnyStatusThatNeedsToBeRefreshed();
        }

        if (needRefresh)
            rxBus.send(new EventMedtronicPumpValuesChanged());
    }


    void resetStatusState() {
        firstRun = true;
        isRefresh = true;
    }


    private boolean isPumpNotReachable() {

        RileyLinkServiceState rileyLinkServiceState = rileyLinkServiceData.rileyLinkServiceState;

        if (rileyLinkServiceState == null) {
            aapsLogger.debug(LTag.PUMP, "RileyLink unreachable. RileyLinkServiceState is null.");
            return false;
        }

        if (rileyLinkServiceState != RileyLinkServiceState.PumpConnectorReady //
                && rileyLinkServiceState != RileyLinkServiceState.RileyLinkReady //
                && rileyLinkServiceState != RileyLinkServiceState.TuneUpDevice) {
            aapsLogger.debug(LTag.PUMP, "RileyLink unreachable.");
            return false;
        }

        return (!rileyLinkMedtronicService.getDeviceCommunicationManager().isDeviceReachable());
    }


    private void refreshAnyStatusThatNeedsToBeRefreshed() {

        Map<MedtronicStatusRefreshType, Long> statusRefresh = workWithStatusRefresh(StatusRefreshAction.GetData, null,
                null);

        if (!doWeHaveAnyStatusNeededRefereshing(statusRefresh)) {
            return;
        }

        boolean resetTime = false;

        if (isPumpNotReachable()) {
            aapsLogger.error("Pump unreachable.");
            medtronicUtil.sendNotification(MedtronicNotificationType.PumpUnreachable, getResourceHelper(), rxBus);

            return;
        }

        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);


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
                        rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(refreshType.getKey().getCommandType(medtronicUtil.getMedtronicPumpModel()));
                        refreshTypesNeededToReschedule.add(refreshType.getKey());
                        resetTime = true;
                    }
                    break;

                    case Configuration: {
                        rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(refreshType.getKey().getCommandType(medtronicUtil.getMedtronicPumpModel()));
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
            medtronicPumpStatus.setLastCommunicationToNow();

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
        rxBus.send(new EventRefreshButtonState(enabled));
    }


    private boolean initializePump(boolean realInit) {

        if (rileyLinkMedtronicService==null)
            return false;

        aapsLogger.info(LTag.PUMP, getLogPrefix() + "initializePump - start");

        rileyLinkMedtronicService.getDeviceCommunicationManager().setDoWakeUpBeforeCommand(false);

        setRefreshButtonEnabled(false);

        if (isRefresh) {
            if (isPumpNotReachable()) {
                aapsLogger.error(getLogPrefix() + "initializePump::Pump unreachable.");
                medtronicUtil.sendNotification(MedtronicNotificationType.PumpUnreachable, getResourceHelper(), rxBus);

                setRefreshButtonEnabled(true);

                return true;
            }

            medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);
        }

        // model (once)
        if (medtronicUtil.getMedtronicPumpModel() == null) {
            rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.PumpModel);
        } else {
            if (medtronicPumpStatus.medtronicDeviceType != medtronicUtil.getMedtronicPumpModel()) {
                aapsLogger.warn(LTag.PUMP, getLogPrefix() + "Configured pump is not the same as one detected.");
                medtronicUtil.sendNotification(MedtronicNotificationType.PumpTypeNotSame, getResourceHelper(), rxBus);
            }
        }

        this.pumpState = PumpDriverState.Connected;

        // time (1h)
        checkTimeAndOptionallySetTime();

        readPumpHistory();

        // remaining insulin (>50 = 4h; 50-20 = 1h; 15m)
        rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.GetRemainingInsulin);
        scheduleNextRefresh(MedtronicStatusRefreshType.RemainingInsulin, 10);

        // remaining power (1h)
        rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.GetBatteryStatus);
        scheduleNextRefresh(MedtronicStatusRefreshType.BatteryStatus, 20);

        // configuration (once and then if history shows config changes)
        rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.getSettings(medtronicUtil.getMedtronicPumpModel()));

        // read profile (once, later its controlled by isThisProfileSet method)
        getBasalProfiles();

        int errorCount = rileyLinkMedtronicService.getMedtronicUIComm().getInvalidResponsesCount();

        if (errorCount >= 5) {
            aapsLogger.error("Number of error counts was 5 or more. Starting tunning.");
            setRefreshButtonEnabled(true);
            serviceTaskExecutor.startTask(new WakeAndTuneTask(getInjector()));
            return true;
        }

        medtronicPumpStatus.setLastCommunicationToNow();
        setRefreshButtonEnabled(true);

        if (!isRefresh) {
            pumpState = PumpDriverState.Initialized;
        }

        isInitialized = true;
        // this.pumpState = PumpDriverState.Initialized;

        this.firstRun = false;

        return true;
    }

    private void getBasalProfiles() {

        MedtronicUITask medtronicUITask = rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.GetBasalProfileSTD);

        if (medtronicUITask.getResponseType() == MedtronicUIResponseType.Error) {
            rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.GetBasalProfileSTD);
        }
    }


    @Override
    public boolean isThisProfileSet(Profile profile) {
        aapsLogger.debug(LTag.PUMP, "isThisProfileSet: basalInitalized=" + medtronicPumpStatus.basalProfileStatus);

        if (!isInitialized)
            return true;

        if (medtronicPumpStatus.basalProfileStatus == BasalProfileStatus.NotInitialized) {
            // this shouldn't happen, but if there was problem we try again
            getBasalProfiles();
            return isProfileSame(profile);
        } else if (medtronicPumpStatus.basalProfileStatus == BasalProfileStatus.ProfileChanged) {
            return false;
        }

        return (medtronicPumpStatus.basalProfileStatus != BasalProfileStatus.ProfileOK) || isProfileSame(profile);
    }


    private boolean isProfileSame(Profile profile) {

        boolean invalid = false;
        Double[] basalsByHour = medtronicPumpStatus.basalsByHour;

        aapsLogger.debug(LTag.PUMP, "Current Basals (h):   "
                + (basalsByHour == null ? "null" : BasalProfile.getProfilesByHourToString(basalsByHour)));

        // int index = 0;

        if (basalsByHour == null)
            return true; // we don't want to set profile again, unless we are sure

        StringBuilder stringBuilder = new StringBuilder("Requested Basals (h): ");

        for (Profile.ProfileValue basalValue : profile.getBasalValues()) {

            double basalValueValue = pumpDescription.pumpType.determineCorrectBasalSize(basalValue.value);

            int hour = basalValue.timeAsSeconds / (60 * 60);

            if (!MedtronicUtil.isSame(basalsByHour[hour], basalValueValue)) {
                invalid = true;
            }

            stringBuilder.append(String.format(Locale.ENGLISH, "%.3f", basalValueValue));
            stringBuilder.append(" ");
        }

        aapsLogger.debug(LTag.PUMP, stringBuilder.toString());

        if (!invalid) {
            aapsLogger.debug(LTag.PUMP, "Basal profile is same as AAPS one.");
        } else {
            aapsLogger.debug(LTag.PUMP, "Basal profile on Pump is different than the AAPS one.");
        }

        return (!invalid);
    }


    @Override
    public long lastDataTime() {

        if (medtronicPumpStatus.lastConnection != 0) {
            return medtronicPumpStatus.lastConnection;
        }

        return System.currentTimeMillis();
    }


    @Override
    public double getBaseBasalRate() {
        return medtronicPumpStatus.getBasalProfileForHour();
    }


    @Override
    public double getReservoirLevel() {
        return medtronicPumpStatus.reservoirRemainingUnits;
    }


    @Override
    public int getBatteryLevel() {
        return medtronicPumpStatus.batteryRemaining;
    }

    protected void triggerUIChange() {
        rxBus.send(new EventMedtronicPumpValuesChanged());
    }

    private BolusDeliveryType bolusDeliveryType = BolusDeliveryType.Idle;

    private enum BolusDeliveryType {
        Idle, //
        DeliveryPrepared, //
        Delivering, //
        CancelDelivery
    }


    private void checkTimeAndOptionallySetTime() {

        aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Start");

        setRefreshButtonEnabled(false);

        if (isPumpNotReachable()) {
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Pump Unreachable.");
            setRefreshButtonEnabled(true);
            return;
        }

        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);

        rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.GetRealTimeClock);

        ClockDTO clock = medtronicUtil.getPumpTime();

        if (clock == null) { // retry
            rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.GetRealTimeClock);

            clock = medtronicUtil.getPumpTime();
        }

        if (clock == null)
            return;

        int timeDiff = Math.abs(clock.timeDifference);

        if (timeDiff > 20) {

            if ((clock.localDeviceTime.getYear() <= 2015) || (timeDiff <= 24 * 60 * 60)) {

                aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference is {} s. Set time on pump.", timeDiff);

                rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.SetRealTimeClock);

                if (clock.timeDifference == 0) {
                    Notification notification = new Notification(Notification.INSIGHT_DATE_TIME_UPDATED, getResourceHelper().gs(R.string.pump_time_updated), Notification.INFO, 60);
                    rxBus.send(new EventNewNotification(notification));
                }
            } else {
                if ((clock.localDeviceTime.getYear() > 2015)) {
                    aapsLogger.error("MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference over 24h requested [diff={} s]. Doing nothing.", timeDiff);
                    medtronicUtil.sendNotification(MedtronicNotificationType.TimeChangeOver24h, getResourceHelper(), rxBus);
                }
            }

        } else {
            aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::checkTimeAndOptionallySetTime - Time difference is {} s. Do nothing.", timeDiff);
        }

        scheduleNextRefresh(MedtronicStatusRefreshType.PumpTime, 0);
    }


    @NonNull
    protected PumpEnactResult deliverBolus(final DetailedBolusInfo detailedBolusInfo) {

        aapsLogger.info(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - " + BolusDeliveryType.DeliveryPrepared);

        setRefreshButtonEnabled(false);

        if (detailedBolusInfo.insulin > medtronicPumpStatus.reservoirRemainingUnits) {
            return new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_bolus_could_not_be_delivered_no_insulin,
                            medtronicPumpStatus.reservoirRemainingUnits,
                            detailedBolusInfo.insulin));
        }

        bolusDeliveryType = BolusDeliveryType.DeliveryPrepared;

        if (isPumpNotReachable()) {
            aapsLogger.debug(LTag.PUMP, "MedtronicPumpPlugin::deliverBolus - Pump Unreachable.");
            return setNotReachable(true, false);
        }

        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);

        if (bolusDeliveryType == BolusDeliveryType.CancelDelivery) {
            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled.");
            return setNotReachable(true, true);
        }

        // LOG.debug("MedtronicPumpPlugin::deliverBolus - Starting wait period.");

        int sleepTime = sp.getInt(MedtronicConst.Prefs.BolusDelay, 10) * 1000;

        SystemClock.sleep(sleepTime);

        if (bolusDeliveryType == BolusDeliveryType.CancelDelivery) {
            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Delivery Canceled, before wait period.");
            return setNotReachable(true, true);
        }

        // LOG.debug("MedtronicPumpPlugin::deliverBolus - End wait period. Start delivery");

        try {

            bolusDeliveryType = BolusDeliveryType.Delivering;

            // LOG.debug("MedtronicPumpPlugin::deliverBolus - Start delivery");

            MedtronicUITask responseTask = rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.SetBolus,
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

                        Intent i = new Intent(context, ErrorHelperActivity.class);
                        i.putExtra("soundid", R.raw.boluserror);
                        i.putExtra("status", getResourceHelper().gs(R.string.medtronic_cmd_cancel_bolus_not_supported));
                        i.putExtra("title", getResourceHelper().gs(R.string.medtronic_warning));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(i);

                    }).start();
                }

                long now = System.currentTimeMillis();

                detailedBolusInfo.date = now;
                detailedBolusInfo.deliverAt = now; // not sure about that one

                activePlugin.getActiveTreatments().addToHistoryTreatment(detailedBolusInfo, true);

                // we subtract insulin, exact amount will be visible with next remainingInsulin update.
                medtronicPumpStatus.reservoirRemainingUnits -= detailedBolusInfo.insulin;

                incrementStatistics(detailedBolusInfo.isSMB ? MedtronicConst.Statistics.SMBBoluses
                        : MedtronicConst.Statistics.StandardBoluses);


                // calculate time for bolus and set driver to busy for that time
                int bolusTime = (int) (detailedBolusInfo.insulin * 42.0d);
                long time = now + (bolusTime * 1000);

                this.busyTimestamps.add(time);
                setEnableCustomAction(MedtronicCustomActionType.ClearBolusBlock, true);

                return new PumpEnactResult(getInjector()).success(true) //
                        .enacted(true) //
                        .bolusDelivered(detailedBolusInfo.insulin) //
                        .carbsDelivered(detailedBolusInfo.carbs);

            } else {
                return new PumpEnactResult(getInjector()) //
                        .success(bolusDeliveryType == BolusDeliveryType.CancelDelivery) //
                        .enacted(false) //
                        .comment(getResourceHelper().gs(R.string.medtronic_cmd_bolus_could_not_be_delivered));
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
            return new PumpEnactResult(getInjector()) //
                    .success(true) //
                    .enacted(false);
        } else {
            return new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_pump_status_pump_unreachable));
        }
    }


    public void stopBolusDelivering() {

        this.bolusDeliveryType = BolusDeliveryType.CancelDelivery;

        // if (isLoggingEnabled())
        // LOG.warn("MedtronicPumpPlugin::deliverBolus - Stop Bolus Delivery.");
    }


    private void incrementStatistics(String statsKey) {
        long currentCount = sp.getLong(statsKey, 0L);
        currentCount++;
        sp.putLong(statsKey, currentCount);
    }


    // if enforceNew===true current temp basal is canceled and new TBR set (duration is prolonged),
    // if false and the same rate is requested enacted=false and success=true is returned and TBR is not changed
    @NonNull @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile,
                                                boolean enforceNew) {

        setRefreshButtonEnabled(false);

        if (isPumpNotReachable()) {

            setRefreshButtonEnabled(true);

            return new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_pump_status_pump_unreachable));
        }

        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);

        aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute: rate: " + absoluteRate + ", duration=" + durationInMinutes);

        // read current TBR
        TempBasalPair tbrCurrent = readTBR();

        if (tbrCurrent == null) {
            aapsLogger.warn(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - Could not read current TBR, canceling operation.");
            finishAction("TBR");
            return new PumpEnactResult(getInjector()).success(false).enacted(false)
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_cant_read_tbr));
        } else {
            aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute: Current Basal: duration: " + tbrCurrent.getDurationMinutes() + " min, rate=" + tbrCurrent.getInsulinRate());
        }

        if (!enforceNew) {

            if (MedtronicUtil.isSame(tbrCurrent.getInsulinRate(), absoluteRate)) {

                boolean sameRate = true;
                if (MedtronicUtil.isSame(0.0d, absoluteRate) && durationInMinutes > 0) {
                    // if rate is 0.0 and duration>0 then the rate is not the same
                    sameRate = false;
                }

                if (sameRate) {
                    aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - No enforceNew and same rate. Exiting.");
                    finishAction("TBR");
                    return new PumpEnactResult(getInjector()).success(true).enacted(false);
                }
            }
            // if not the same rate, we cancel and start new
        }

        // if TBR is running we will cancel it.
        if (tbrCurrent.getInsulinRate() != 0.0f && tbrCurrent.getDurationMinutes() > 0) {
            aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - TBR running - so canceling it.");

            // CANCEL

            MedtronicUITask responseTask2 = rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.CancelTBR);

            Boolean response = (Boolean) responseTask2.returnData;

            if (response) {
                aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - Current TBR cancelled.");
            } else {
                aapsLogger.error(getLogPrefix() + "setTempBasalAbsolute - Cancel TBR failed.");

                finishAction("TBR");

                return new PumpEnactResult(getInjector()).success(false).enacted(false)
                        .comment(getResourceHelper().gs(R.string.medtronic_cmd_cant_cancel_tbr_stop_op));
            }
        }

        // now start new TBR
        MedtronicUITask responseTask = rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.SetTemporaryBasal,
                absoluteRate, durationInMinutes);

        Boolean response = (Boolean) responseTask.returnData;

        aapsLogger.info(LTag.PUMP, getLogPrefix() + "setTempBasalAbsolute - setTBR. Response: " + response);

        if (response) {
            // FIXME put this into UIPostProcessor
            medtronicPumpStatus.tempBasalStart = new Date();
            medtronicPumpStatus.tempBasalAmount = absoluteRate;
            medtronicPumpStatus.tempBasalLength = durationInMinutes;

            TemporaryBasal tempStart = new TemporaryBasal(getInjector()) //
                    .date(System.currentTimeMillis()) //
                    .duration(durationInMinutes) //
                    .absolute(absoluteRate) //
                    .source(Source.USER);

            activePlugin.getActiveTreatments().addToHistoryTempBasal(tempStart);

            incrementStatistics(MedtronicConst.Statistics.TBRsSet);

            finishAction("TBR");

            return new PumpEnactResult(getInjector()).success(true).enacted(true) //
                    .absolute(absoluteRate).duration(durationInMinutes);

        } else {
            finishAction("TBR");

            return new PumpEnactResult(getInjector()).success(false).enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_tbr_could_not_be_delivered));
        }

    }


    @NonNull @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile,
                                               boolean enforceNew) {
        if (percent == 0) {
            return setTempBasalAbsolute(0.0d, durationInMinutes, profile, enforceNew);
        } else {
            double absoluteValue = profile.getBasal() * (percent / 100.0d);
            absoluteValue = pumpDescription.pumpType.determineCorrectBasalSize(absoluteValue);
            aapsLogger.warn(LTag.PUMP, "setTempBasalPercent [MedtronicPumpPlugin] - You are trying to use setTempBasalPercent with percent other then 0% (" + percent + "). This will start setTempBasalAbsolute, with calculated value (" + absoluteValue + "). Result might not be 100% correct.");
            return setTempBasalAbsolute(absoluteValue, durationInMinutes, profile, enforceNew);
        }
    }


    private void finishAction(String overviewKey) {

        if (overviewKey != null)
            rxBus.send(new EventRefreshOverview(overviewKey, false));

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

        if (this.medtronicPumpStatus.basalProfileStatus != BasalProfileStatus.NotInitialized
                && medtronicHistoryData.hasBasalProfileChanged()) {
            medtronicHistoryData.processLastBasalProfileChange(pumpDescription.pumpType, medtronicPumpStatus);
        }

        PumpDriverState previousState = this.pumpState;

        if (medtronicHistoryData.isPumpSuspended()) {
            this.pumpState = PumpDriverState.Suspended;
            aapsLogger.debug(LTag.PUMP, getLogPrefix() + "isPumpSuspended: true");
        } else {
            if (previousState == PumpDriverState.Suspended) {
                this.pumpState = PumpDriverState.Ready;
            }
            aapsLogger.debug(LTag.PUMP, getLogPrefix() + "isPumpSuspended: false");
        }

        medtronicHistoryData.processNewHistoryData();

        this.medtronicHistoryData.finalizeNewHistoryRecords();
        // this.medtronicHistoryData.setLastHistoryRecordTime(this.lastPumpHistoryEntry.atechDateTime);

    }


    private void readPumpHistoryLogic() {

        boolean debugHistory = false;

        LocalDateTime targetDate = null;

        if (lastPumpHistoryEntry == null) {

            if (debugHistory)
                aapsLogger.debug(LTag.PUMP, getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntry: null");

            Long lastPumpHistoryEntryTime = getLastPumpEntryTime();

            LocalDateTime timeMinus36h = new LocalDateTime();
            timeMinus36h = timeMinus36h.minusHours(36);
            medtronicHistoryData.setIsInInit(true);

            if (lastPumpHistoryEntryTime == 0L) {
                if (debugHistory)
                    aapsLogger.debug(LTag.PUMP, getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntryTime: 0L - targetDate: "
                            + targetDate);
                targetDate = timeMinus36h;
            } else {
                // LocalDateTime lastHistoryRecordTime = DateTimeUtil.toLocalDateTime(lastPumpHistoryEntryTime);

                if (debugHistory)
                    aapsLogger.debug(LTag.PUMP, getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntryTime: " + lastPumpHistoryEntryTime + " - targetDate: " + targetDate);

                medtronicHistoryData.setLastHistoryRecordTime(lastPumpHistoryEntryTime);

                LocalDateTime lastHistoryRecordTime = DateTimeUtil.toLocalDateTime(lastPumpHistoryEntryTime);

                lastHistoryRecordTime = lastHistoryRecordTime.minusHours(12); // we get last 12 hours of history to
                // determine pump state
                // (we don't process that data), we process only

                if (timeMinus36h.isAfter(lastHistoryRecordTime)) {
                    targetDate = timeMinus36h;
                }

                targetDate = (timeMinus36h.isAfter(lastHistoryRecordTime) ? timeMinus36h : lastHistoryRecordTime);

                if (debugHistory)
                    aapsLogger.debug(LTag.PUMP, getLogPrefix() + "readPumpHistoryLogic(): targetDate: " + targetDate);
            }
        } else {
            if (debugHistory)
                aapsLogger.debug(LTag.PUMP, getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntry: not null - " + medtronicUtil.gsonInstance.toJson(lastPumpHistoryEntry));
            medtronicHistoryData.setIsInInit(false);
            // medtronicHistoryData.setLastHistoryRecordTime(lastPumpHistoryEntry.atechDateTime);

            // targetDate = lastPumpHistoryEntry.atechDateTime;
        }

        //aapsLogger.debug(LTag.PUMP, "HST: Target Date: " + targetDate);

        MedtronicUITask responseTask2 = rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.GetHistoryData,
                lastPumpHistoryEntry, targetDate);

        if (debugHistory)
            aapsLogger.debug(LTag.PUMP, "HST: After task");

        PumpHistoryResult historyResult = (PumpHistoryResult) responseTask2.returnData;

        if (debugHistory)
            aapsLogger.debug(LTag.PUMP, "HST: History Result: " + historyResult.toString());

        PumpHistoryEntry latestEntry = historyResult.getLatestEntry();

        if (debugHistory)
            aapsLogger.debug(LTag.PUMP, getLogPrefix() + "Last entry: " + latestEntry);

        if (latestEntry == null) // no new history to read
            return;

        this.lastPumpHistoryEntry = latestEntry;
        sp.putLong(MedtronicConst.Statistics.LastPumpHistoryEntry, latestEntry.atechDateTime);

        if (debugHistory)
            aapsLogger.debug(LTag.PUMP, "HST: History: valid=" + historyResult.validEntries.size() + ", unprocessed=" + historyResult.unprocessedEntries.size());

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
    }


    private Long getLastPumpEntryTime() {
        Long lastPumpEntryTime = sp.getLong(MedtronicConst.Statistics.LastPumpHistoryEntry, 0L);

        try {
            LocalDateTime localDateTime = DateTimeUtil.toLocalDateTime(lastPumpEntryTime);

            if (localDateTime.getYear() != (new GregorianCalendar().get(Calendar.YEAR))) {
                aapsLogger.warn(LTag.PUMP, "Saved LastPumpHistoryEntry was invalid. Year was not the same.");
                return 0L;
            }

            return lastPumpEntryTime;

        } catch (Exception ex) {
            aapsLogger.warn(LTag.PUMP, "Saved LastPumpHistoryEntry was invalid.");
            return 0L;
        }

    }


    private void scheduleNextRefresh(MedtronicStatusRefreshType refreshType) {
        scheduleNextRefresh(refreshType, 0);
    }


    private void scheduleNextRefresh(MedtronicStatusRefreshType refreshType, int additionalTimeInMinutes) {
        switch (refreshType) {

            case RemainingInsulin: {
                double remaining = medtronicPumpStatus.reservoirRemainingUnits;
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
        MedtronicUITask responseTask = rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.ReadTemporaryBasal);

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


    @NonNull @Override
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {

        aapsLogger.info(LTag.PUMP, getLogPrefix() + "cancelTempBasal - started");

        if (isPumpNotReachable()) {

            setRefreshButtonEnabled(true);

            return new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_pump_status_pump_unreachable));
        }

        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);
        setRefreshButtonEnabled(false);

        TempBasalPair tbrCurrent = readTBR();

        if (tbrCurrent != null) {
            if (tbrCurrent.getInsulinRate() == 0.0f && tbrCurrent.getDurationMinutes() == 0) {
                aapsLogger.info(LTag.PUMP, getLogPrefix() + "cancelTempBasal - TBR already canceled.");
                finishAction("TBR");
                return new PumpEnactResult(getInjector()).success(true).enacted(false);
            }
        } else {
            aapsLogger.warn(LTag.PUMP, getLogPrefix() + "cancelTempBasal - Could not read currect TBR, canceling operation.");
            finishAction("TBR");
            return new PumpEnactResult(getInjector()).success(false).enacted(false)
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_cant_read_tbr));
        }

        MedtronicUITask responseTask2 = rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.CancelTBR);

        Boolean response = (Boolean) responseTask2.returnData;

        finishAction("TBR");

        if (response) {
            aapsLogger.info(LTag.PUMP, getLogPrefix() + "cancelTempBasal - Cancel TBR successful.");

            TemporaryBasal tempBasal = new TemporaryBasal(getInjector()) //
                    .date(System.currentTimeMillis()) //
                    .duration(0) //
                    .source(Source.USER);

            activePlugin.getActiveTreatments().addToHistoryTempBasal(tempBasal);

            return new PumpEnactResult(getInjector()).success(true).enacted(true) //
                    .isTempCancel(true);
        } else {
            aapsLogger.info(LTag.PUMP, getLogPrefix() + "cancelTempBasal - Cancel TBR failed.");

            return new PumpEnactResult(getInjector()).success(response).enacted(response) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_cant_cancel_tbr));
        }
    }

    @NonNull @Override
    public ManufacturerType manufacturer() {
        return pumpDescription.pumpType.getManufacturer();
    }

    @NonNull @Override
    public PumpType model() {
        return pumpDescription.pumpType;
    }

    @NonNull @Override
    public String serialNumber() {
        return medtronicPumpStatus.serialNumber;
    }

    @NonNull @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        aapsLogger.info(LTag.PUMP, getLogPrefix() + "setNewBasalProfile");

        // this shouldn't be needed, but let's do check if profile setting we are setting is same as current one
        if (isProfileSame(profile)) {
            return new PumpEnactResult(getInjector()) //
                    .success(true) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_basal_profile_not_set_is_same));
        }

        setRefreshButtonEnabled(false);

        if (isPumpNotReachable()) {

            setRefreshButtonEnabled(true);

            return new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_pump_status_pump_unreachable));
        }

        medtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable, rxBus);

        BasalProfile basalProfile = convertProfileToMedtronicProfile(profile);

        String profileInvalid = isProfileValid(basalProfile);

        if (profileInvalid != null) {
            return new PumpEnactResult(getInjector()) //
                    .success(false) //
                    .enacted(false) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_set_profile_pattern_overflow, profileInvalid));
        }

        MedtronicUITask responseTask = rileyLinkMedtronicService.getMedtronicUIComm().executeCommand(MedtronicCommandType.SetBasalProfileSTD,
                basalProfile);

        Boolean response = (Boolean) responseTask.returnData;

        aapsLogger.info(LTag.PUMP, getLogPrefix() + "Basal Profile was set: " + response);

        if (response) {
            return new PumpEnactResult(getInjector()).success(true).enacted(true);
        } else {
            return new PumpEnactResult(getInjector()).success(response).enacted(response) //
                    .comment(getResourceHelper().gs(R.string.medtronic_cmd_basal_profile_could_not_be_set));
        }
    }


    private String isProfileValid(BasalProfile basalProfile) {

        StringBuilder stringBuilder = new StringBuilder();

        if (medtronicPumpStatus.maxBasal == null)
            return null;

        for (BasalProfileEntry profileEntry : basalProfile.getEntries()) {

            if (profileEntry.rate > medtronicPumpStatus.maxBasal) {
                stringBuilder.append(profileEntry.startTime.toString("HH:mm"));
                stringBuilder.append("=");
                stringBuilder.append(profileEntry.rate);
            }
        }

        return stringBuilder.length() == 0 ? null : stringBuilder.toString();
    }


    @NonNull
    private BasalProfile convertProfileToMedtronicProfile(Profile profile) {

        BasalProfile basalProfile = new BasalProfile(aapsLogger);

        for (int i = 0; i < 24; i++) {
            double rate = profile.getBasalTimeFromMidnight(i * 60 * 60);

            double v = pumpDescription.pumpType.determineCorrectBasalSize(rate);

            BasalProfileEntry basalEntry = new BasalProfileEntry(v, i, 0);
            basalProfile.addEntry(basalEntry);

        }

        basalProfile.generateRawDataFromEntries();

        return basalProfile;
    }

    // OPERATIONS not supported by Pump or Plugin

    private List<CustomAction> customActions = null;

    private final CustomAction customActionWakeUpAndTune = new CustomAction(R.string.medtronic_custom_action_wake_and_tune,
            MedtronicCustomActionType.WakeUpAndTune);

    private final CustomAction customActionClearBolusBlock = new CustomAction(
            R.string.medtronic_custom_action_clear_bolus_block, MedtronicCustomActionType.ClearBolusBlock, false);

    private final CustomAction customActionResetRLConfig = new CustomAction(
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
                if (rileyLinkMedtronicService.verifyConfiguration()) {
                    serviceTaskExecutor.startTask(new WakeAndTuneTask(getInjector()));
                } else {
                    Intent i = new Intent(context, ErrorHelperActivity.class);
                    i.putExtra("soundid", R.raw.boluserror);
                    i.putExtra("status", getResourceHelper().gs(R.string.medtronic_error_operation_not_possible_no_configuration));
                    i.putExtra("title", getResourceHelper().gs(R.string.medtronic_warning));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(i);
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
                serviceTaskExecutor.startTask(new ResetRileyLinkConfigurationTask(getInjector()));
            }
            break;

            default:
                break;
        }

    }

    @Nullable @Override public PumpEnactResult executeCustomCommand(CustomCommand customCommand) {
        return null;
    }

    @Override
    public void timezoneOrDSTChanged(TimeChangeType changeType) {

        aapsLogger.warn(LTag.PUMP, getLogPrefix() + "Time or TimeZone changed. ");

        this.hasTimeDateOrTimeZoneChanged = true;
    }

    @Override
    public boolean setNeutralTempAtFullHour() {
        return sp.getBoolean(R.string.key_set_neutral_temps, true);
    }


    private void setEnableCustomAction(MedtronicCustomActionType customAction, boolean isEnabled) {

        if (customAction == MedtronicCustomActionType.ClearBolusBlock) {
            this.customActionClearBolusBlock.setEnabled(isEnabled);
        } else if (customAction == MedtronicCustomActionType.ResetRileyLinkConfiguration) {
            this.customActionResetRLConfig.setEnabled(isEnabled);
        }

        refreshCustomActionsList();
    }
}
