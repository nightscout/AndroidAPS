package info.nightscout.androidaps.plugins.PumpMedtronic;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.NonNull;

import com.crashlytics.android.answers.CustomEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TDD;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.Actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.PumpCommon.PumpPluginAbstract;
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpDriverState;
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpType;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.RileyLinkConst;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.defs.RileyLinkServiceState;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.tasks.WakeAndTuneTask;
import info.nightscout.androidaps.plugins.PumpCommon.utils.DateTimeUtil;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.MedtronicCommunicationManager;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.PumpHistoryEntry;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.history.pump.PumpHistoryResult;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.ui.MedtronicUIComm;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.ui.MedtronicUITask;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.MedtronicHistoryData;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.BasalProfile;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.BasalProfileEntry;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicCustomActionType;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicNotificationType;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicStatusRefreshType;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.PumpMedtronic.events.EventMedtronicPumpValuesChanged;
import info.nightscout.androidaps.plugins.PumpMedtronic.service.RileyLinkMedtronicService;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicConst;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.FabricPrivacy;
import info.nightscout.utils.SP;

/**
 * Created by andy on 23.04.18.
 */
public class MedtronicPumpPlugin extends PumpPluginAbstract implements PumpInterface {

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicPumpPlugin.class);

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

    public static Gson gsonInstance = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();
    public static Gson gsonInstancePretty = new GsonBuilder().excludeFieldsWithoutExposeAnnotation()
        .setPrettyPrinting().create();


    private MedtronicPumpPlugin() {

        super(new PluginDescription() //
            .mainType(PluginType.PUMP) //
            .fragmentClass(MedtronicFragment.class.getName()) //
            .pluginName(R.string.medtronic_name) //
            .shortName(R.string.medtronic_name_short) //
            .preferencesId(R.xml.pref_medtronic).description(R.string.description_pump_medtronic), //
            PumpType.Medtronic_522_722 // we default to most basic model, correct model from config is loaded later
        );

        // TODO remove this later
        displayConnectionMessages = false;

        medtronicHistoryData = new MedtronicHistoryData();
        // medtronicCommunicationManager = MedtronicCommunicationManager.getInstance();
        // medtronicCommunicationManager.setDoWakeUpBeforeCommand(false);

        serviceConnection = new ServiceConnection() {

            public void onServiceDisconnected(ComponentName name) {
                LOG.debug("RileyLinkMedtronicService is disconnected");
                medtronicService = null;
            }


            public void onServiceConnected(ComponentName name, IBinder service) {
                LOG.debug("RileyLinkMedtronicService is connected");
                RileyLinkMedtronicService.LocalBinder mLocalBinder = (RileyLinkMedtronicService.LocalBinder)service;
                medtronicService = mLocalBinder.getServiceInstance();

                new Thread(() -> {

                    for (int i = 0; i < 20; i++) {
                        SystemClock.sleep(5000);

                        if (MedtronicUtil.getPumpStatus() != null) {
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


    @Override
    public void initPumpStatusData() {

        this.pumpStatusLocal = new MedtronicPumpStatus(pumpDescription);
        MedtronicUtil.setPumpStatus(pumpStatusLocal);

        pumpStatusLocal.lastConnection = SP.getLong(RileyLinkConst.Prefs.LastGoodDeviceCommunicationTime, 0L);
        pumpStatusLocal.lastDataTime = new LocalDateTime(pumpStatusLocal.lastConnection);
        pumpStatusLocal.previousConnection = pumpStatusLocal.lastConnection;

        pumpStatusLocal.refreshConfiguration();

        LOG.debug("initPumpStatusData: {}", this.pumpStatusLocal);

        this.pumpStatus = pumpStatusLocal;

        // this is only thing that can change, by being configured
        pumpDescription.maxTempAbsolute = (pumpStatusLocal.maxBasal != null) ? pumpStatusLocal.maxBasal : 35.0d;

        // set first Medtronic Pump Start
        if (!SP.contains(MedtronicConst.Statistics.FirstPumpStart)) {
            SP.putLong(MedtronicConst.Statistics.FirstPumpStart, System.currentTimeMillis());
        }

    }


    public void onStartCustomActions() {

        // check status every minute (if any status needs refresh we send readStatus command)
        new Thread(() -> {

            do {
                SystemClock.sleep(60000);

                if (doWeHaveAnyStatusNeededRefereshing()) {
                    ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("Scheduled Status Refresh", null);
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


    // Pump Plugin

    private boolean isServiceSet() {
        return medtronicService != null;
    }


    @Override
    public boolean isInitialized() {
        // TODO remove
        LOG.debug("MedtronicPumpPlugin::isInitialized");
        return isServiceSet() && isInitialized;
    }


    // @Override
    // public boolean isSuspended() {
    // // TO DO remove
    // LOG.debug("MedtronicPumpPlugin::isSuspended");
    // return isServiceSet() && medtronicHistoryData.is();
    // }

    @Override
    public boolean isBusy() {
        // TODO remove
        LOG.debug("MedtronicPumpPlugin::isBusy");
        return isServiceSet() && medtronicService.isBusy();
    }


    @Override
    public boolean isConnected() {
        // TODO remove
        LOG.debug("MedtronicPumpPlugin::isConnected");
        return isServiceSet() && medtronicService.isInitialized();
    }


    @Override
    public boolean isConnecting() {
        // TODO remove
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

        MainApp.bus().post(new EventMedtronicPumpValuesChanged());
    }


    public void resetStatusState() {
        firstRun = true;
        isRefresh = true;
    }


    private boolean isPumpNotReachable() {

        RileyLinkServiceState rileyLinkServiceState = MedtronicUtil.getServiceState();

        if (rileyLinkServiceState != RileyLinkServiceState.PumpConnectorReady //
            && rileyLinkServiceState != RileyLinkServiceState.RileyLinkReady //
            && rileyLinkServiceState != RileyLinkServiceState.TuneUpDevice) {
            LOG.error("RileyLink unreachable.");
            return false;
        }

        return (!medtronicCommunicationManager.isDeviceReachable());
    }


    private void refreshAnyStatusThatNeedsToBeRefreshed() {

        if (!doWeHaveAnyStatusNeededRefereshing()) {
            return;
        }

        boolean resetTime = false;

        if (isPumpNotReachable()) {
            LOG.error("Pump unreachable.");
            MedtronicUtil.sendNotification(MedtronicNotificationType.PumpUnreachable);

            return;
        }

        MedtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable);

        Set<MedtronicStatusRefreshType> refreshTypesNeededToReschedule = new HashSet<>();

        // execute
        for (Map.Entry<MedtronicStatusRefreshType, Long> refreshType : statusRefreshMap.entrySet()) {

            if (refreshType.getValue() > 0 && System.currentTimeMillis() > refreshType.getValue()) {

                switch (refreshType.getKey()) {
                    case PumpHistory: {
                        readPumpHistory();
                    }
                        break;

                    case PumpTime:
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
        }

        // reschedule
        for (MedtronicStatusRefreshType refreshType : refreshTypesNeededToReschedule) {
            scheduleNextRefresh(refreshType);
        }

        if (resetTime)
            pumpStatusLocal.setLastCommunicationToNow();

    }


    private boolean doWeHaveAnyStatusNeededRefereshing() {

        for (Map.Entry<MedtronicStatusRefreshType, Long> refreshType : statusRefreshMap.entrySet()) {

            if (refreshType.getValue() > 0 && System.currentTimeMillis() > refreshType.getValue()) {
                return true;
            }
        }

        return false;
    }


    private void setRefreshButtonEnabled(boolean enabled) {
        MedtronicFragment.refreshButtonEnabled(enabled);
    }


    private void initializePump(boolean realInit) {

        LOG.info(getLogPrefix() + "initializePump - start");

        if (medtronicCommunicationManager == null) {
            medtronicCommunicationManager = MedtronicCommunicationManager.getInstance();
            medtronicCommunicationManager.setDoWakeUpBeforeCommand(false);
        }

        setRefreshButtonEnabled(false);

        getMDTPumpStatus();

        if (isRefresh) {
            if (isPumpNotReachable()) {
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
                LOG.warn(getLogPrefix() + "Configured pump is not the same as one detected.");
                MedtronicUtil.sendNotification(MedtronicNotificationType.PumpTypeNotSame);
            }
        }

        this.pumpState = PumpDriverState.Connected;

        readPumpHistory();

        // TODO rewrite reading of data to be done in background or different thread perhaps ??

        // remaining insulin (>50 = 4h; 50-20 = 1h; 15m)
        medtronicUIComm.executeCommand(MedtronicCommandType.GetRemainingInsulin);
        scheduleNextRefresh(MedtronicStatusRefreshType.RemainingInsulin, 10);

        // remaining power (1h)
        medtronicUIComm.executeCommand(MedtronicCommandType.GetBatteryStatus);
        scheduleNextRefresh(MedtronicStatusRefreshType.BatteryStatus, 20);

        // configuration (once and then if history shows config changes)
        // medtronicUIComm.executeCommand(MedtronicCommandType.getSettings(MedtronicUtil.getMedtronicPumpModel()));

        // time (1h)
        medtronicUIComm.executeCommand(MedtronicCommandType.RealTimeClock);
        scheduleNextRefresh(MedtronicStatusRefreshType.PumpTime, 30);

        // read profile (once, later its controlled by isThisProfileSet method)
        medtronicUIComm.executeCommand(MedtronicCommandType.GetBasalProfileSTD);

        // TODO handle if tunning was needed (more than 5 timeouts)
        int errorCount = medtronicUIComm.getInvalidResponsesCount();

        if (errorCount >= 5) {
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

        FabricPrivacy.getInstance().logCustom( //
            new CustomEvent("MedtronicInitializePump") //
                .putCustomAttribute("buildversion", BuildConfig.BUILDVERSION) //
                .putCustomAttribute("version", BuildConfig.VERSION));

        isInitialized = true;
        // this.pumpState = PumpDriverState.Initialized;

        this.firstRun = false;
    }


    @Override
    public boolean isThisProfileSet(Profile profile) {

        if (!this.isInitialized) {
            return true;
        }

        if (!basalProfileChanged && getMDTPumpStatus().basalsByHour != null) {
            return (!isBasalProfileInvalid);
        }

        setRefreshButtonEnabled(false);

        if (isPumpNotReachable()) {
            MedtronicUtil.sendNotification(MedtronicNotificationType.PumpUnreachable);
            setRefreshButtonEnabled(true);

            return true; // we don't won't setting profile if pump unreachable
        }

        MedtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable);

        MedtronicUITask responseTask = medtronicUIComm.executeCommand(MedtronicCommandType.GetBasalProfileSTD);

        boolean invalid = false;

        if (responseTask.haveData()) {

            Double[] basalsByHour = getMDTPumpStatus().basalsByHour;

            LOG.debug("Basals by hour: " + (basalsByHour == null ? "null" : StringUtils.join(basalsByHour, " ")));

            int index = 0;

            if (basalsByHour == null)
                return true; // we don't want to set profile again, unless we are sure

            for (Profile.BasalValue basalValue : profile.getBasalValues()) {

                int hour = basalValue.timeAsSeconds / (60 * 60);

                if (MedtronicUtil.isSame(basalsByHour[index], basalValue.value)) {
                    if (index != hour) {
                        invalid = true;
                        break;
                    }
                } else {
                    invalid = true;
                    break;
                }

                index++;
            }

            if (!invalid) {
                LOG.debug("Basal profile is same as AAPS one.");
                basalProfileChanged = false;
            } else {
                LOG.debug("Basal profile on Pump is different than the AAPS one.");
            }

        } else {
            invalid = true;
            LOG.debug("Basal profile NO DATA");
        }

        isBasalProfileInvalid = invalid;

        setRefreshButtonEnabled(true);

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


    private MedtronicPumpStatus getMDTPumpStatus() {
        if (pumpStatusLocal == null) {
            // FIXME I don't know why this happens
            LOG.warn("!!!! Reset Pump Status Local");
            pumpStatusLocal = MedtronicUtil.getPumpStatus();
        }

        return pumpStatusLocal;
    }


    protected void triggerUIChange() {
        MainApp.bus().post(new EventMedtronicPumpValuesChanged());
    }


    @NonNull
    protected PumpEnactResult deliverBolus(final DetailedBolusInfo detailedBolusInfo) {

        setRefreshButtonEnabled(false);

        if (isPumpNotReachable()) {
            setRefreshButtonEnabled(true);
            return new PumpEnactResult() //
                .success(false) //
                .enacted(false) //
                .comment(MainApp.gs(R.string.medtronic_pump_status_pump_unreachable));
        }

        MedtronicUtil.dismissNotification(MedtronicNotificationType.PumpUnreachable);

        try {

            LOG.error("MedtronicPumpPlugin::deliverBolus Not fully implemented - Just base command.");

            MedtronicUITask responseTask = medtronicUIComm.executeCommand(MedtronicCommandType.SetBolus,
                detailedBolusInfo.insulin);

            Boolean response = (Boolean)responseTask.returnData;

            // TODO display bolus

            if (response) {
                // FIXME this needs to be fixed to read info from history
                boolean treatmentCreated = TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, true);

                getMDTPumpStatus().reservoirRemainingUnits -= detailedBolusInfo.insulin; // we subtract insulin, exact
                                                                                         // amount will be visible with
                                                                                         // next remainingInsulin
                                                                                         // update.

                incrementStatistics(detailedBolusInfo.isSMB ? MedtronicConst.Statistics.SMBBoluses
                    : MedtronicConst.Statistics.StandardBoluses);
            }

            // readPumpHistory();

            setRefreshButtonEnabled(true);

            return new PumpEnactResult().success(response).enacted(response);

            // pump.activity = MainApp.gs(R.string.combo_pump_action_bolusing, detailedBolusInfo.insulin);
            // MainApp.bus().post(new EventComboPumpUpdateGUI());
            //
            // // check pump is ready and all pump bolus records are known
            // CommandResult stateResult = runCommand(null, 2, () -> ruffyScripter.readQuickInfo(1));
            // if (!stateResult.success) {
            // return new PumpEnactResult().success(false).enacted(false)
            // .comment(MainApp.gs(R.string.combo_error_no_connection_no_bolus_delivered));
            // }
            // if (stateResult.reservoirLevel != -1 && stateResult.reservoirLevel - 0.5 < detailedBolusInfo.insulin) {
            // return new PumpEnactResult().success(false).enacted(false)
            // .comment(MainApp.gs(R.string.combo_reservoir_level_insufficient_for_bolus));
            // }
            // // the commands above ensured a connection was made, which updated this field
            // if (pumpHistoryChanged) {
            // return new PumpEnactResult().success(false).enacted(false)
            // .comment(MainApp.gs(R.string.combo_bolus_rejected_due_to_pump_history_change));
            // }
            //
            // Bolus previousBolus = stateResult.history != null && !stateResult.history.bolusHistory.isEmpty()
            // ? stateResult.history.bolusHistory.get(0)
            // : new Bolus(0, 0, false);
            //
            // // reject a bolus if one with the exact same size was successfully delivered
            // // within the last 1-2 minutes
            // if (Math.abs(previousBolus.amount - detailedBolusInfo.insulin) < 0.01
            // && previousBolus.timestamp + 60 * 1000 > System.currentTimeMillis()) {
            // log.debug("Bolu request rejected, same bolus was successfully delivered very recently");
            // return new PumpEnactResult().success(false).enacted(false)
            // .comment(MainApp.gs(R.string.bolus_frequency_exceeded));
            // }
            //
            //
            //
            // if (cancelBolus) {
            // return new PumpEnactResult().success(true).enacted(false);
            // }
            //
            // BolusProgressReporter progressReporter = detailedBolusInfo.isSMB ? nullBolusProgressReporter :
            // bolusProgressReporter;
            //
            // // start bolus delivery
            // scripterIsBolusing = true;
            // runCommand(null, 0,
            // () -> ruffyScripter.deliverBolus(detailedBolusInfo.insulin, progressReporter));
            // scripterIsBolusing = false;
            //
            // // Note that the result of the issued bolus command is not checked. If there was
            // // a connection problem, ruffyscripter tried to recover and we can just check the
            // // history below to see what was actually delivered
            //
            // // get last bolus from pump history for verification
            // // (reads 2 records to update `recentBoluses` further down)
            // CommandResult postBolusStateResult = runCommand(null, 3, () -> ruffyScripter.readQuickInfo(2));
            // if (!postBolusStateResult.success) {
            // return new PumpEnactResult().success(false).enacted(false)
            // .comment(MainApp.gs(R.string.combo_error_bolus_verification_failed));
            // }
            // Bolus lastPumpBolus = postBolusStateResult.history != null &&
            // !postBolusStateResult.history.bolusHistory.isEmpty()
            // ? postBolusStateResult.history.bolusHistory.get(0)
            // : null;
            //
            // // no bolus delivered?
            // if (lastPumpBolus == null || lastPumpBolus.equals(previousBolus)) {
            // if (cancelBolus) {
            // return new PumpEnactResult().success(true).enacted(false);
            // } else {
            // return new PumpEnactResult()
            // .success(false)
            // .enacted(false)
            // .comment(MainApp.gs(R.string.combo_error_no_bolus_delivered));
            // }
            // }
            //
            // // at least some insulin delivered, so add it to treatments
            // if (!addBolusToTreatments(detailedBolusInfo, lastPumpBolus))
            // return new PumpEnactResult().success(false).enacted(true)
            // .comment(MainApp.gs(R.string.combo_error_updating_treatment_record));
            //
            // // check pump bolus record has a sane timestamp
            // long now = System.currentTimeMillis();
            // if (lastPumpBolus.timestamp < now - 10 * 60 * 1000 || lastPumpBolus.timestamp > now + 10 * 60 * 1000) {
            // Notification notification = new Notification(Notification.COMBO_PUMP_ALARM,
            // MainApp.gs(R.string.combo_suspious_bolus_time), Notification.URGENT);
            // MainApp.bus().post(new EventNewNotification(notification));
            // }
            //
            // // update `recentBoluses` so the bolus was just delivered won't be detected as a new
            // // bolus that has been delivered on the pump
            // recentBoluses = postBolusStateResult.history.bolusHistory;
            //
            // // only a partial bolus was delivered
            // if (Math.abs(lastPumpBolus.amount - detailedBolusInfo.insulin) > 0.01) {
            // if (cancelBolus) {
            // return new PumpEnactResult().success(true).enacted(true);
            // }
            // return new PumpEnactResult().success(false).enacted(true)
            // .comment(MainApp.gs(R.string.combo_error_partial_bolus_delivered,
            // lastPumpBolus.amount, detailedBolusInfo.insulin));
            // }
            //
            // // full bolus was delivered successfully
            // incrementBolusCount();
            // return new PumpEnactResult()
            // .success(true)
            // .enacted(lastPumpBolus.amount > 0)
            // .bolusDelivered(lastPumpBolus.amount)
            // .carbsDelivered(detailedBolusInfo.carbs);
        } finally {
            // pump.activity = null;
            // MainApp.bus().post(new EventComboPumpUpdateGUI());
            MainApp.bus().post(new EventRefreshOverview("Bolus"));
            // cancelBolus = false;
            triggerUIChange();
        }
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

        LOG.info(getLogPrefix() + "setTempBasalAbsolute: rate: {}, duration={}", absoluteRate, durationInMinutes);

        // read current TBR
        TempBasalPair tbrCurrent = readTBR();

        if (tbrCurrent == null) {
            LOG.warn(getLogPrefix() + "setTempBasalAbsolute - Could not read current TBR, canceling operation.");
            finishAction("TBR");
            return new PumpEnactResult().success(false).enacted(false)
                .comment(MainApp.gs(R.string.medtronic_cmd_cant_read_tbr));
        } else {
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
                    LOG.info(getLogPrefix() + "setTempBasalAbsolute - No enforceNew and same rate. Exiting.");
                    finishAction("TBR");
                    return new PumpEnactResult().success(true).enacted(false);
                }
            }
            // if not the same rate, we cancel and start new
        }

        // if TBR is running we will cancel it.
        if (tbrCurrent.getInsulinRate() != 0.0f && tbrCurrent.getDurationMinutes() > 0) {
            LOG.info(getLogPrefix() + "setTempBasalAbsolute - TBR running - so canceling it.");

            // CANCEL

            MedtronicUITask responseTask2 = medtronicUIComm.executeCommand(MedtronicCommandType.CancelTBR);

            Boolean response = (Boolean)responseTask2.returnData;

            if (response) {
                LOG.info(getLogPrefix() + "setTempBasalAbsolute - Current TBR cancelled.");
            } else {
                LOG.error(getLogPrefix() + "setTempBasalAbsolute - Cancel TBR failed.");

                finishAction("TBR");

                return new PumpEnactResult().success(false).enacted(false)
                    .comment(MainApp.gs(R.string.medtronic_cmd_cant_cancel_tbr));
            }
        }

        // now start new TBR
        MedtronicUITask responseTask = medtronicUIComm.executeCommand(MedtronicCommandType.SetTemporaryBasal,
            absoluteRate, durationInMinutes);

        Boolean response = (Boolean)responseTask.returnData;

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
        }

        finishAction("TBR");

        setRefreshButtonEnabled(true);

        return new PumpEnactResult().success(response).enacted(response);
    }


    private void finishAction(String overviewKey) {

        if (overviewKey != null)
            MainApp.bus().post(new EventRefreshOverview(overviewKey));

        triggerUIChange();

        setRefreshButtonEnabled(true);
    }


    private void readPumpHistory() {

        LOG.error(getLogPrefix() + "readPumpHistory WIP.");

        readPumpHistoryLogic();

        scheduleNextRefresh(MedtronicStatusRefreshType.PumpHistory);

        if (medtronicHistoryData.hasRelevantConfigurationChanged()) {
            scheduleNextRefresh(MedtronicStatusRefreshType.Configuration, -1);
        }

        if (medtronicHistoryData.hasPumpTimeChanged()) {
            scheduleNextRefresh(MedtronicStatusRefreshType.PumpTime, -1);
        }

        if (medtronicHistoryData.hasBasalProfileChanged()) {
            this.basalProfileChanged = true;
        }

        PumpDriverState previousState = this.pumpState;

        if (medtronicHistoryData.isPumpSuspended(this.pumpState == PumpDriverState.Suspended)) {
            scheduleNextRefresh(MedtronicStatusRefreshType.PumpTime, -1);
            this.pumpState = PumpDriverState.Suspended;
            LOG.debug(getLogPrefix() + "isPumpSuspended: true");
        } else {
            if (previousState == PumpDriverState.Suspended) {
                this.pumpState = PumpDriverState.Ready;
            }
            LOG.debug(getLogPrefix() + "isPumpSuspended: false");
        }

        List<PumpHistoryEntry> tdds = medtronicHistoryData.getTDDs();

        if (tdds.size() > 0) {
            processTDDs(tdds);
        }

        List<PumpHistoryEntry> tdds2 = medtronicHistoryData.getTDDs2();

        LOG.debug("TDDs2: {}", gsonInstancePretty.toJson(tdds2));

        List<PumpHistoryEntry> treatments = medtronicHistoryData.getTreatments();

        if (treatments.size() > 0) {
            processTreatments(treatments);
        }

        this.medtronicHistoryData.finalizeNewHistoryRecords();

    }


    private void processTreatments(List<PumpHistoryEntry> treatments) {

        // FIXME bolus and tbr

        LOG.error(getLogPrefix() + "Treatments found: {}. Not processed.\n", treatments.size());

        MedtronicHistoryData.showLogs(null, gsonInstancePretty.toJson(treatments));

    }


    private void processTDDs(List<PumpHistoryEntry> tdds) {

        LOG.error(getLogPrefix() + "TDDs found: {}. Not processed.\n{}", tdds.size(), gsonInstancePretty.toJson(tdds));

        // FIXME tdd
        List<TDD> tdDsForLastXDays = MainApp.getDbHelper().getTDDs();

        for (TDD tdDsForLastXDay : tdDsForLastXDays) {
            LOG.debug("TDD: " + tdDsForLastXDay);
        }

    }


    private void readPumpHistoryLogic() {

        LocalDateTime targetDate = null;

        if (lastPumpHistoryEntry == null) {

            LOG.debug(getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntry: null");

            Long lastPumpHistoryEntryTime = SP.getLong(MedtronicConst.Statistics.LastPumpHistoryEntry, 0L);

            LocalDateTime timeMinus36h = new LocalDateTime();
            timeMinus36h = timeMinus36h.minusHours(36);
            medtronicHistoryData.setIsInInit(true);

            if (lastPumpHistoryEntryTime == 0L) {
                LOG.debug(getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntryTime: 0L - targetDate: "
                    + targetDate);
                targetDate = timeMinus36h;
            } else {
                LocalDateTime lastHistoryRecordTime = DateTimeUtil.toLocalDateTime(lastPumpHistoryEntryTime);

                LOG.debug(getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntryTime: {} - targetDate: {}",
                    lastHistoryRecordTime, targetDate);

                medtronicHistoryData.setLastHistoryRecordTime(DateTimeUtil.toLocalDateTime(lastPumpHistoryEntryTime));

                lastHistoryRecordTime = lastHistoryRecordTime.minusHours(12); // we get last 12 hours of history to
                                                                              // determine pump state
                // (we don't process that data), we process only

                if (timeMinus36h.isAfter(lastHistoryRecordTime)) {
                    targetDate = timeMinus36h;
                }

                targetDate = (timeMinus36h.isAfter(lastHistoryRecordTime) ? timeMinus36h : lastHistoryRecordTime);

                LOG.debug(getLogPrefix() + "readPumpHistoryLogic(): targetDate: " + targetDate);
            }
        } else {
            LOG.debug(getLogPrefix() + "readPumpHistoryLogic(): lastPumpHistoryEntry: not null - {}",
                gsonInstancePretty.toJson(lastPumpHistoryEntry));
            medtronicHistoryData.setIsInInit(false);
            medtronicHistoryData.setLastHistoryRecordTime(null);
        }

        MedtronicUITask responseTask2 = medtronicUIComm.executeCommand(MedtronicCommandType.GetHistoryData,
            lastPumpHistoryEntry, targetDate);

        PumpHistoryResult historyResult = (PumpHistoryResult)responseTask2.returnData;

        PumpHistoryEntry latestEntry = historyResult.getLatestEntry();

        LOG.debug(getLogPrefix() + "Last entry: " + latestEntry);

        if (latestEntry == null) // no new history to read
            return;

        this.lastPumpHistoryEntry = latestEntry;
        SP.putLong(MedtronicConst.Statistics.LastPumpHistoryEntry,
            DateTimeUtil.toATechDate(latestEntry.getLocalDateTime()));

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


    private void scheduleNextRefresh(MedtronicStatusRefreshType refreshType) {
        scheduleNextRefresh(refreshType, 0);
    }


    private void scheduleNextRefresh(MedtronicStatusRefreshType refreshType, int additionalTimeInMinutes) {
        switch (refreshType) {

            case RemainingInsulin: {
                Double remaining = pumpStatusLocal.reservoirRemainingUnits;
                int min;
                if (remaining > 50)
                    min = 4 * 60;
                else if (remaining > 20)
                    min = 60;
                else
                    min = 15;

                statusRefreshMap.put(refreshType, getTimeInFutureFromMinutes(min));
            }
                break;

            case PumpTime:
            case Configuration:
            case PumpHistory: {
                statusRefreshMap.put(refreshType, getTimeInFutureFromMinutes(refreshType.getRefreshTime()
                    + additionalTimeInMinutes));
            }
                break;
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
            TempBasalPair tbr = (TempBasalPair)responseTask.returnData;

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
                LOG.info(getLogPrefix() + "cancelTempBasal - TBR already canceled.");
                finishAction("TBR");
                return new PumpEnactResult().success(true).enacted(false);
            }
        } else {
            LOG.warn(getLogPrefix() + "cancelTempBasal - Could not read currect TBR, canceling operation.");
            finishAction("TBR");
            return new PumpEnactResult().success(false).enacted(false)
                .comment(MainApp.gs(R.string.medtronic_cmd_cant_read_tbr));
        }

        MedtronicUITask responseTask2 = medtronicUIComm.executeCommand(MedtronicCommandType.CancelTBR);

        Boolean response = (Boolean)responseTask2.returnData;

        if (response) {
            LOG.info(getLogPrefix() + "cancelTempBasal - Cancel TBR successful.");

        } else {
            LOG.info(getLogPrefix() + "cancelTempBasal - Cancel TBR failed.");
        }

        finishAction("TBR");

        return new PumpEnactResult().success(response).enacted(response);
    }


    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        LOG.error(getLogPrefix() + "setNewBasalProfile - WIP.");

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

        Boolean response = (Boolean)responseTask.returnData;

        LOG.info(getLogPrefix() + "Basal Profile was set: " + response);

        return new PumpEnactResult().success(response).enacted(response);
    }


    private String isProfileValid(BasalProfile basalProfile) {

        StringBuilder stringBuilder = new StringBuilder();

        MedtronicPumpStatus pumpStatus = getMDTPumpStatus();

        if (pumpStatus.maxBasal == null)
            return null;

        for (BasalProfileEntry profileEntry : basalProfile.getEntries()) {

            if (profileEntry.rate > pumpStatus.maxBasal) {

                stringBuilder.append(profileEntry.startTime.toString("HH:mm") + "=" + profileEntry.rate);

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

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        LOG.error("MedtronicPumpPlugin::setExtendedBolus NOT SUPPORTED.");
        return OPERATION_NOT_SUPPORTED;
    }


    @Override
    public PumpEnactResult cancelExtendedBolus() {
        LOG.warn("cancelExtendedBolus - operation not supported.");
        return getOperationNotSupportedWithCustomText(R.string.medtronic_cmd_cancel_bolus_not_supported);
    }


    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile,
            boolean enforceNew) {
        LOG.error("setTempBasalPercent NOT IMPLEMENTED.");
        // we will never come here unless somebody has played with configuration in PumpType
        return OPERATION_NOT_SUPPORTED;
    }


    // // we don't loadTDD. TDD is read from Pump History
    // @Override
    // public PumpEnactResult loadTDDs() {
    // return OPERATION_NOT_SUPPORTED;
    // }

    // public void connect(String reason) {
    // // we don't use this.
    // // we connect to RileyLink on startup and keep connection opened, then connection to pump
    // // is established when needed.
    //
    // // TODO remove
    // LOG.debug("MedtronicPumpPlugin::connect (reason: {})", reason);
    // }

    // public void disconnect(String reason) {
    // // see comment in connect
    // // TO DO remove
    // LOG.debug("MedtronicPumpPlugin::disconnect (reason: {})", reason);
    // }

    // public void stopConnecting() {
    // // see comment in connect
    // // TO DO remove
    // LOG.debug("MedtronicPumpPlugin::stopConnecting");
    // }

    @Override
    public void stopBolusDelivering() {
        // Medtronic doesn't have Bolus cancel, so we fake it.
    }

    List<CustomAction> customActions = null;


    @Override
    public List<CustomAction> getCustomActions() {

        if (customActions == null) {

            this.customActions = new ArrayList<>();

            CustomAction ca = new CustomAction(R.string.medtronic_custom_action_wake_and_tune,
                MedtronicCustomActionType.WakeUpAndTune);
            this.customActions.add(ca);
        }

        return this.customActions;
    }


    @Override
    public PumpEnactResult executeCustomAction(CustomActionType customActionType) {

        MedtronicCustomActionType mcat = (MedtronicCustomActionType)customActionType;

        switch (mcat) {

            case WakeUpAndTune:
                ServiceTaskExecutor.startTask(new WakeAndTuneTask());
                break;

            default:
                break;
        }

        return null;
    }
}
