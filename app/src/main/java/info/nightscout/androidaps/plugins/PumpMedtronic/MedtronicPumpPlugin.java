package info.nightscout.androidaps.plugins.PumpMedtronic;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.PumpCommon.PumpPluginAbstract;
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpType;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.tasks.ServiceTaskExecutor;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.service.tasks.WakeAndTuneTask;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.ui.MedtronicUIComm;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.ui.MedtronicUITask;
import info.nightscout.androidaps.plugins.PumpMedtronic.data.dto.TempBasalPair;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicStatusRefreshType;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpDriver;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.PumpMedtronic.events.EventMedtronicPumpValuesChanged;
import info.nightscout.androidaps.plugins.PumpMedtronic.service.RileyLinkMedtronicService;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicConst;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.SP;

/**
 * Created by andy on 23.04.18.
 */

public class MedtronicPumpPlugin extends PumpPluginAbstract implements PumpInterface {

    private static final Logger LOG = LoggerFactory.getLogger(MedtronicPumpPlugin.class);


    private RileyLinkMedtronicService medtronicService;
    protected static MedtronicPumpPlugin plugin = null;
    private MedtronicPumpStatus pumpStatusLocal = null;
    private MedtronicUIComm medtronicUIComm = new MedtronicUIComm();

    // variables for handling statuses and history
    private boolean firstRun = true;
    private boolean relevantConfigurationChangeFound = false;
    private boolean hasBasalProfileChanged = false;
    private boolean isBasalProfileInvalid = false;
    private Map<MedtronicStatusRefreshType, Long> statusRefreshMap = new HashMap<>();


    public static MedtronicPumpPlugin getPlugin() {
        if (plugin == null)
            plugin = new MedtronicPumpPlugin();
        return plugin;
    }


    private MedtronicPumpPlugin() {

//        super(new PluginDescription() //
//                .mainType(PluginType.PUMP) //
//                .fragmentClass(MedtronicFragment.class.getName()) //
//                .pluginName(R.string.medtronic_name) //
//                .shortName(R.string.medtronic_name_short) //
//                .preferencesId(R.xml.pref_medtronic));


        super(new MedtronicPumpDriver(), //
                "MedtronicPump", //
                new PluginDescription() //
                        .mainType(PluginType.PUMP) //
                        .fragmentClass(MedtronicFragment.class.getName()) //
                        .pluginName(R.string.medtronic_name) //
                        .shortName(R.string.medtronic_name_short) //
                        .preferencesId(R.xml.pref_medtronic), //
                PumpType.Medtronic_512_712 // we default to most basic model, correct model from config is loaded later
        );

        serviceConnection = new ServiceConnection() {

            public void onServiceDisconnected(ComponentName name) {
                LOG.debug("RileyLinkMedtronicService is disconnected");
                medtronicService = null;
            }

            public void onServiceConnected(ComponentName name, IBinder service) {
                LOG.debug("RileyLinkMedtronicService is connected");
                RileyLinkMedtronicService.LocalBinder mLocalBinder = (RileyLinkMedtronicService.LocalBinder) service;
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


    @Override
    public void initPumpStatusData() {

        this.pumpStatusLocal = new MedtronicPumpStatus(pumpDescription);
        MedtronicUtil.setPumpStatus(pumpStatusLocal);

        pumpStatusLocal.refreshConfiguration();

        LOG.debug("initPumpStatusData: {}", this.pumpStatusLocal);

        this.pumpStatus = pumpStatusLocal;

        if (pumpStatusLocal.maxBasal != null)
            pumpDescription.maxTempAbsolute = (pumpStatusLocal.maxBasal != null) ? pumpStatusLocal.maxBasal : 35.0d;

        // needs to be changed in configuration, after all functionalities are done
        pumpDescription.isBolusCapable = true;
        pumpDescription.isTempBasalCapable = true; // WIP
        pumpDescription.isExtendedBolusCapable = false;
        pumpDescription.isSetBasalProfileCapable = false;

        // unchangable
        pumpDescription.tempBasalStyle = PumpDescription.PERCENT;
        pumpDescription.tempDurationStep15mAllowed = false;
        pumpDescription.tempDurationStep30mAllowed = true;
        pumpDescription.isRefillingCapable = true;
        pumpDescription.storesCarbInfo = false;
        pumpDescription.is30minBasalRatesCapable = true;
        pumpDescription.supportsTDDs = true;
        pumpDescription.needsManualTDDLoad = false;

        // set first Medtronic Pump Start
        if (!SP.contains(MedtronicConst.Statistics.FirstPumpStart)) {
            SP.putLong(MedtronicConst.Statistics.FirstPumpStart, System.currentTimeMillis());
        }

    }

    public void onStartCustomActions() {

        // check status every minute
        new Thread(() -> {

            do {
                SystemClock.sleep(60000);

                if (doWeHaveAnyStatusNeededRefereshing()) {
                    ConfigBuilderPlugin.getCommandQueue().readStatus("Manual Status Request", null);
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
    public PumpDescription getPumpDescription() {
        return this.pumpDescription;
    }


    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }


    // Pump Plugin

    private boolean isServiceSet() {
        return medtronicService != null;
    }


    public boolean isInitialized() {
        return isServiceSet() && medtronicService.isInitialized();
    }

    // FIXME
    public boolean isSuspended() {
        return isServiceSet() && medtronicService.isSuspended();
    }


    public boolean isBusy() {
        return isServiceSet() && medtronicService.isBusy();
    }


    public boolean isConnected() {
        return isServiceSet() && medtronicService.isInitialized();
    }


    public boolean isConnecting() {

        if (!isServiceSet())
            return true;
        else
            return !medtronicService.isInitialized();
    }


    @Override
    public void getPumpStatus() {

        getMDTPumpStatus();


        if (firstRun) {
            initializePump(true);
        } else {
            refreshAnyStatusThatNeedsToBeRefreshed();
        }

        MainApp.bus().post(new EventMedtronicPumpValuesChanged());
    }


    public void resetStatusState() {
        firstRun = true;
    }

    private void refreshAnyStatusThatNeedsToBeRefreshed() {

        if (!doWeHaveAnyStatusNeededRefereshing()) {
            return;
        }

        boolean resetTime = false;

        for (Map.Entry<MedtronicStatusRefreshType, Long> refreshType : statusRefreshMap.entrySet()) {

            if (refreshType.getValue() > 0 && System.currentTimeMillis() > refreshType.getValue()) {

                switch (refreshType.getKey()) {
                    case PumpHistory: {
                        readPumpHistory();
                    }
                    break;

                    case PumpTime:
                    case BatteryStatus:
                    case RemainingInsulin:
                    case Configuration: {
                        medtronicUIComm.executeCommand(refreshType.getKey().getCommandType());
                        scheduleNextRefresh(refreshType.getKey());
                        resetTime = true;
                    }
                    break;
                }
            }
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

    private void initializePump(boolean realInit) {

        LOG.error("initializePump - start");

        getMDTPumpStatus();

        // model (once)
        if (MedtronicUtil.getMedtronicPumpModel() == null) {
            medtronicUIComm.executeCommand(MedtronicCommandType.PumpModel);
        } else {
            if (pumpStatusLocal.medtronicDeviceType != MedtronicUtil.getMedtronicPumpModel()) {
                LOG.warn("Configured pump is not the same as one detected.");
                Notification notification = new Notification(Notification.MEDTRONIC_PUMP_ALARM, MainApp.gs(R.string.medtronic_error_pump_type_set_differs_from_detected), Notification.NORMAL);
                MainApp.bus().post(new EventNewNotification(notification));
            }
        }

        // TODO this call might need to do deeper call (several pages)
        // pump history handling - special, updates every 5 minutes ???
        readPumpHistory();


        // TODO rewrite reading of data to be done in background or different thread perhaps ??

        // remaining insulin (>50 = 4h; 50-20 = 1h; 15m)
        medtronicUIComm.executeCommand(MedtronicCommandType.GetRemainingInsulin);
        scheduleNextRefresh(MedtronicStatusRefreshType.RemainingInsulin, 10);

        // remaining power (1h)
        medtronicUIComm.executeCommand(MedtronicCommandType.GetBatteryStatus);
        scheduleNextRefresh(MedtronicStatusRefreshType.BatteryStatus, 20);

        // configuration (once and then if history shows config changes)
        medtronicUIComm.executeCommand(MedtronicCommandType.getSettings(MedtronicUtil.getMedtronicPumpModel()));

        // time (1h)
        medtronicUIComm.executeCommand(MedtronicCommandType.RealTimeClock);
        scheduleNextRefresh(MedtronicStatusRefreshType.PumpTime, 30);

        // read profile (once, later its controlled by isThisProfileSet method)
        medtronicUIComm.executeCommand(MedtronicCommandType.GetBasalProfileSTD);


        // TODO handle if tunning was needed (more than 5 timeouts)
        int errorCount = medtronicUIComm.getInvalidResponsesCount();

        if (errorCount >= 5) {
            LOG.error("Number of error counts was 5 or more. Starting tunning.");
            ServiceTaskExecutor.startTask(new WakeAndTuneTask());
            return;
        }

        pumpStatusLocal.setLastCommunicationToNow();

        this.firstRun = false;
    }


    @Override
    public boolean isThisProfileSet(Profile profile) {

        if (!isConnected()) {
            return true;
        }

        if (!hasBasalProfileChanged && getMDTPumpStatus().basalsByHour != null) {
            return (!isBasalProfileInvalid);
        }

        MedtronicUITask responseTask = medtronicUIComm.executeCommand(MedtronicCommandType.GetBasalProfileSTD);

        boolean invalid = false;

        if (responseTask.haveData()) {

            Double[] basalsByHour = getMDTPumpStatus().basalsByHour;

            int index = 0;

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
            } else {
                LOG.debug("Basal profile on Pump is different than the AAPS one.");
            }

        } else {
            invalid = true;
            LOG.debug("Basal profile NO DATA");
        }

        isBasalProfileInvalid = invalid;

        return (!invalid);
    }


    @Override
    public Date lastDataTime() {
        if (pumpStatusLocal != null && pumpStatusLocal.lastDataTime != null) {
            return pumpStatusLocal.lastDataTime;
        }

        return new Date();
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
        try {

            LOG.error("MedtronicPumpPlugin::deliverBolus Not fully implemented - Just base command.");

            // TODO should wait and display bolus delivery
            MedtronicUITask responseTask = medtronicUIComm.executeCommand(MedtronicCommandType.SetBolus, detailedBolusInfo.insulin);

            Boolean response = (Boolean) responseTask.returnData;

            // TODO display bolus

            if (response) {
                // FIXME this needs to be fixed to read info from history
                boolean treatmentCreated = TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, true);

                getMDTPumpStatus().reservoirRemainingUnits -= detailedBolusInfo.insulin; // we subtract insulin, exact amount will be visible with next remainingInsulin update.

                incrementStatistics(detailedBolusInfo.isSMB ? MedtronicConst.Statistics.SMBBoluses : MedtronicConst.Statistics.StandardBoluses);
            }

            readPumpHistory();


            return new PumpEnactResult().success(response).enacted(response);


//            pump.activity = MainApp.gs(R.string.combo_pump_action_bolusing, detailedBolusInfo.insulin);
//            MainApp.bus().post(new EventComboPumpUpdateGUI());
//
//            // check pump is ready and all pump bolus records are known
//            CommandResult stateResult = runCommand(null, 2, () -> ruffyScripter.readQuickInfo(1));
//            if (!stateResult.success) {
//                return new PumpEnactResult().success(false).enacted(false)
//                        .comment(MainApp.gs(R.string.combo_error_no_connection_no_bolus_delivered));
//            }
//            if (stateResult.reservoirLevel != -1 && stateResult.reservoirLevel - 0.5 < detailedBolusInfo.insulin) {
//                return new PumpEnactResult().success(false).enacted(false)
//                        .comment(MainApp.gs(R.string.combo_reservoir_level_insufficient_for_bolus));
//            }
//            // the commands above ensured a connection was made, which updated this field
//            if (pumpHistoryChanged) {
//                return new PumpEnactResult().success(false).enacted(false)
//                        .comment(MainApp.gs(R.string.combo_bolus_rejected_due_to_pump_history_change));
//            }
//
//            Bolus previousBolus = stateResult.history != null && !stateResult.history.bolusHistory.isEmpty()
//                    ? stateResult.history.bolusHistory.get(0)
//                    : new Bolus(0, 0, false);
//
//            // reject a bolus if one with the exact same size was successfully delivered
//            // within the last 1-2 minutes
//            if (Math.abs(previousBolus.amount - detailedBolusInfo.insulin) < 0.01
//                    && previousBolus.timestamp + 60 * 1000 > System.currentTimeMillis()) {
//                log.debug("Bolu request rejected, same bolus was successfully delivered very recently");
//                return new PumpEnactResult().success(false).enacted(false)
//                        .comment(MainApp.gs(R.string.bolus_frequency_exceeded));
//            }
//
//
//
//            if (cancelBolus) {
//                return new PumpEnactResult().success(true).enacted(false);
//            }
//
//            BolusProgressReporter progressReporter = detailedBolusInfo.isSMB ? nullBolusProgressReporter : bolusProgressReporter;
//
//            // start bolus delivery
//            scripterIsBolusing = true;
//            runCommand(null, 0,
//                    () -> ruffyScripter.deliverBolus(detailedBolusInfo.insulin, progressReporter));
//            scripterIsBolusing = false;
//
//            // Note that the result of the issued bolus command is not checked. If there was
//            // a connection problem, ruffyscripter tried to recover and we can just check the
//            // history below to see what was actually delivered
//
//            // get last bolus from pump history for verification
//            // (reads 2 records to update `recentBoluses` further down)
//            CommandResult postBolusStateResult = runCommand(null, 3, () -> ruffyScripter.readQuickInfo(2));
//            if (!postBolusStateResult.success) {
//                return new PumpEnactResult().success(false).enacted(false)
//                        .comment(MainApp.gs(R.string.combo_error_bolus_verification_failed));
//            }
//            Bolus lastPumpBolus = postBolusStateResult.history != null && !postBolusStateResult.history.bolusHistory.isEmpty()
//                    ? postBolusStateResult.history.bolusHistory.get(0)
//                    : null;
//
//            // no bolus delivered?
//            if (lastPumpBolus == null || lastPumpBolus.equals(previousBolus)) {
//                if (cancelBolus) {
//                    return new PumpEnactResult().success(true).enacted(false);
//                } else {
//                    return new PumpEnactResult()
//                            .success(false)
//                            .enacted(false)
//                            .comment(MainApp.gs(R.string.combo_error_no_bolus_delivered));
//                }
//            }
//
//            // at least some insulin delivered, so add it to treatments
//            if (!addBolusToTreatments(detailedBolusInfo, lastPumpBolus))
//                return new PumpEnactResult().success(false).enacted(true)
//                        .comment(MainApp.gs(R.string.combo_error_updating_treatment_record));
//
//            // check pump bolus record has a sane timestamp
//            long now = System.currentTimeMillis();
//            if (lastPumpBolus.timestamp < now - 10 * 60 * 1000 || lastPumpBolus.timestamp > now + 10 * 60 * 1000) {
//                Notification notification = new Notification(Notification.COMBO_PUMP_ALARM, MainApp.gs(R.string.combo_suspious_bolus_time), Notification.URGENT);
//                MainApp.bus().post(new EventNewNotification(notification));
//            }
//
//            // update `recentBoluses` so the bolus was just delivered won't be detected as a new
//            // bolus that has been delivered on the pump
//            recentBoluses = postBolusStateResult.history.bolusHistory;
//
//            // only a partial bolus was delivered
//            if (Math.abs(lastPumpBolus.amount - detailedBolusInfo.insulin) > 0.01) {
//                if (cancelBolus) {
//                    return new PumpEnactResult().success(true).enacted(true);
//                }
//                return new PumpEnactResult().success(false).enacted(true)
//                        .comment(MainApp.gs(R.string.combo_error_partial_bolus_delivered,
//                                lastPumpBolus.amount, detailedBolusInfo.insulin));
//            }
//
//            // full bolus was delivered successfully
//            incrementBolusCount();
//            return new PumpEnactResult()
//                    .success(true)
//                    .enacted(lastPumpBolus.amount > 0)
//                    .bolusDelivered(lastPumpBolus.amount)
//                    .carbsDelivered(detailedBolusInfo.carbs);
        } finally {
//            pump.activity = null;
//            MainApp.bus().post(new EventComboPumpUpdateGUI());
            MainApp.bus().post(new EventRefreshOverview("Bolus"));
//            cancelBolus = false;
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
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {

        LOG.error("MedtronicPumpPlugin::setTempBasalAbsolute Not fully implemented - Just base command.");

        getMDTPumpStatus();

        LOG.info("MedtronicPumpPlugin::setTempBasalAbsolute: rate: {}, duration={}", absoluteRate, durationInMinutes);

        // read current TBR
        TempBasalPair tbrCurrent = readTBR();

        if (tbrCurrent == null) {
            LOG.warn("MedtronicPumpPlugin::setTempBasalAbsolute - Could not read current TBR, canceling operation.");
            // TODO translate
            return new PumpEnactResult().success(false).enacted(false).comment("Couldn't read current TBR.");
        } else {
            LOG.info("MedtronicPumpPlugin::setTempBasalAbsolute: Current Basal: duration: {} min, rate={}", tbrCurrent.getDurationMinutes(), tbrCurrent.getInsulinRate());
        }

        // FIXME doesn't work correctly. Read current TBR first
        if (!enforceNew) {

            if (MedtronicUtil.isSame(tbrCurrent.getInsulinRate(), absoluteRate)) {

                boolean sameRate = true;
                if (MedtronicUtil.isSame(0.0d, absoluteRate) && durationInMinutes > 0) {
                    // if rate is 0.0 and duration>0 then the rate is not the same
                    sameRate = false;
                }

                if (sameRate) {
                    LOG.info("MedtronicPumpPlugin::setTempBasalAbsolute - No enforceNew and same rate. Exiting.");
                    return new PumpEnactResult().success(true).enacted(false);
                }
            }
            // if not the same rate, we cancel and start new
        }


        // if TBR is running we will cancel it.
        if (tbrCurrent.getInsulinRate() != 0.0f && tbrCurrent.getDurationMinutes() > 0) {
            LOG.info("MedtronicPumpPlugin::setTempBasalAbsolute - TBR running - so canceling it.");

            // CANCEL

            MedtronicUITask responseTask2 = medtronicUIComm.executeCommand(MedtronicCommandType.CancelTBR);

            Boolean response = (Boolean) responseTask2.returnData;

            if (response) {
                LOG.info("MedtronicPumpPlugin::setTempBasalAbsolute - Current TBR cancelled.");
            } else {
                LOG.error("MedtronicPumpPlugin::setTempBasalAbsolute - Cancel TBR failed.");
                return new PumpEnactResult().success(false).enacted(false).comment("Couldn't cancel current TBR. Stopping operation. ");
            }
        }


        // now start new TBR
        MedtronicUITask responseTask = medtronicUIComm.executeCommand(MedtronicCommandType.SetTemporaryBasal, absoluteRate, durationInMinutes);

        Boolean response = (Boolean) responseTask.returnData;

        LOG.info("MedtronicPumpPlugin::setTempBasalAbsolute - setTBR. Response: " + response);

        if (response) {
            // FIXME put this into UIPostProcessor
            pumpStatusLocal.tempBasalStart = new Date();
            pumpStatusLocal.tempBasalAmount = absoluteRate;
            pumpStatusLocal.tempBasalLength = durationInMinutes;

            // FIXME should be read from history
            TemporaryBasal tempStart = new TemporaryBasal()
                    .date(System.currentTimeMillis())
                    .duration(durationInMinutes)
                    .absolute(absoluteRate)
                    .source(Source.USER);
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStart);

            incrementStatistics(MedtronicConst.Statistics.TBRsSet);
        }

        readPumpHistory(); // TODO maybe this is not needed here

        MainApp.bus().post(new EventRefreshOverview("TBR"));
        triggerUIChange();

        return new PumpEnactResult().success(response).enacted(response);
    }


    private void readPumpHistory() {
        LOG.error("MedtronicPumpPlugin::readPumpHistory NOT IMPLEMENTED.");

        // TODO implement logic here fror config changes
        relevantConfigurationChangeFound = false;

        // TODO implement logic to see if Basalrates changed from last time
        hasBasalProfileChanged = true;

        // TODO reset next refresh date, also set refreshdate if configuration changed
        scheduleNextRefresh(MedtronicStatusRefreshType.PumpHistory);

        if (relevantConfigurationChangeFound) {
            scheduleNextRefresh(MedtronicStatusRefreshType.Configuration, -1);
        }

        // FIXME set last read
    }


    private void scheduleNextRefresh(MedtronicStatusRefreshType refreshType) {
        scheduleNextRefresh(refreshType, 0);
    }


    private void scheduleNextRefresh(MedtronicStatusRefreshType refreshType, int additionalTimeInMinutes) {
        switch (refreshType) {

            case RemainingInsulin: {
                Double remaining = pumpStatusLocal.reservoirRemainingUnits;
                int min = 0;
                if (remaining > 50)
                    min = 4 * 60;
                else if (remaining > 20)
                    min = 60;
                else
                    min = 15;

                statusRefreshMap.put(refreshType, getTimeInFutureFromMinutes(min));
            }
            break;

            case Configuration:
            case PumpHistory: {
                statusRefreshMap.put(refreshType, getTimeInFutureFromMinutes(refreshType.getRefreshTime() + additionalTimeInMinutes));
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


    // TODO
    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        LOG.error("MedtronicPumpPlugin::setExtendedBolus NOT IMPLEMENTED YET.");

        return OPERATION_NOT_YET_SUPPORTED;
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

    // TODO
    @Override
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {

        LOG.info("cancelTempBasal - started");

        TempBasalPair tbrCurrent = readTBR();

        if (tbrCurrent != null) {
            if (tbrCurrent.getInsulinRate() == 0.0f && tbrCurrent.getDurationMinutes() == 0) {
                LOG.info("MedtronicPumpPlugin::cancelTempBasal - TBR already canceled.");
                return new PumpEnactResult().success(true).enacted(false);
            }
        } else {
            LOG.warn("MedtronicPumpPlugin::cancelTempBasal - Could not read currect TBR, canceling operation.");
            return new PumpEnactResult().success(false).enacted(false).comment("Couldn't read current TBR. ");
        }

        MedtronicUITask responseTask2 = medtronicUIComm.executeCommand(MedtronicCommandType.CancelTBR);

        Boolean response = (Boolean) responseTask2.returnData;

        if (response) {
            LOG.info("MedtronicPumpPlugin::cancelTempBasal - Cancel TBR successful.");

        } else {
            LOG.info("MedtronicPumpPlugin::cancelTempBasal - Cancel TBR failed.");
        }

        readPumpHistory();

        return new PumpEnactResult().success(response).enacted(response);
    }


    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        LOG.warn("MedtronicPumpPlugin::setNewBasalProfile NOT IMPLEMENTED YET.");

        return new PumpEnactResult().success(false).enacted(false).comment(MainApp.gs(R.string.medtronic_cmd_profile_not_set));
    }


    // OPERATIONS not supported by Pump or Plugin

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        LOG.warn("cancelExtendedBolus - operation not supported.");

        return getOperationNotSupportedWithCustomText(R.string.medtronic_cmd_cancel_bolus_not_supported);
    }


    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        LOG.error("setTempBasalPercent NOT IMPLEMENTED.");
        // we will never come here unless somebody has played with configuration in PumpType
        return OPERATION_NOT_SUPPORTED;
    }


    // we don't loadTDD
    @Override
    public PumpEnactResult loadTDDs() {
        return OPERATION_NOT_SUPPORTED;
    }


    public void connect(String reason) {
        // we don't use this.
        // we connect to RileyLink on startup and keep connection opened, then connection to pump
        // is established when needed.
    }


    public void disconnect(String reason) {
        // see comment in connect
    }


    public void stopConnecting() {
        // see comment in connect
    }


    @Override
    public void stopBolusDelivering() {
        // Medtronic doesn't have Bolus cancel, so we fake it.
    }

}
