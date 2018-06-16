package info.nightscout.androidaps.plugins.PumpMedtronic;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.PumpCommon.PumpPluginAbstract;
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpType;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.ui.MedtronicUIComm;
import info.nightscout.androidaps.plugins.PumpMedtronic.comm.ui.MedtronicUITask;
import info.nightscout.androidaps.plugins.PumpMedtronic.defs.MedtronicCommandType;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpDriver;
import info.nightscout.androidaps.plugins.PumpMedtronic.driver.MedtronicPumpStatus;
import info.nightscout.androidaps.plugins.PumpMedtronic.events.EventMedtronicPumpValuesChanged;
import info.nightscout.androidaps.plugins.PumpMedtronic.service.RileyLinkMedtronicService;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicConst;
import info.nightscout.androidaps.plugins.PumpMedtronic.util.MedtronicUtil;
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
    boolean firstRun = true;

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
                PumpType.Minimed_512_712 // we default to most basic model, correct model from config is loaded later
        );

        LOG.error("After supper called.");

        serviceConnection = new ServiceConnection() {

            public void onServiceDisconnected(ComponentName name) {
                LOG.debug("RileyLinkMedtronicService is disconnected");
                medtronicService = null;
            }

            public void onServiceConnected(ComponentName name, IBinder service) {
                LOG.debug("RileyLinkMedtronicService is connected");
                RileyLinkMedtronicService.LocalBinder mLocalBinder = (RileyLinkMedtronicService.LocalBinder) service;
                medtronicService = mLocalBinder.getServiceInstance();

                //pumpStatusLocal.setNotInPreInit();

                new Thread(new Runnable() {
                    @Override
                    public void run() {

                        for (int i = 0; i < 20; i++) {
                            SystemClock.sleep(5000);
                            LOG.debug("Trying to start service L1.");

                            if (MedtronicUtil.getPumpStatus() != null) {
                                LOG.debug("Trying to starting service L2");
                                if (MedtronicUtil.getPumpStatus().setNotInPreInit()) {
                                    break;
                                }
                            }
                        }
                    }
                }).start();
            }
        };


    }


    @Override
    public void initPumpStatusData() {

        LOG.error("Init Pump Status Data");

        this.pumpStatusLocal = new MedtronicPumpStatus(pumpDescription);
        MedtronicUtil.setPumpStatus(pumpStatusLocal);

        pumpStatusLocal.refreshConfiguration();

        //MedtronicUtil.setPumpStatus(pumpStatusLocal);

        LOG.debug("initPumpStatusData: {}", this.pumpStatusLocal);


        this.pumpStatus = pumpStatusLocal;

        if (pumpStatusLocal.maxBasal != null)
            pumpDescription.maxTempAbsolute = (pumpStatusLocal.maxBasal != null) ? pumpStatusLocal.maxBasal : 35.0d;

        // needs to be changed in configuration, after all functionalities are done
        pumpDescription.isBolusCapable = true;
        pumpDescription.isTempBasalCapable = false; // WIP
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


        //pumpStatusLocal.setNotInPreInit();

        // set first Medtronic Pump Start
        if (!SP.contains(MedtronicConst.Prefs.FirstPumpStart)) {
            SP.putLong(MedtronicConst.Prefs.FirstPumpStart, System.currentTimeMillis());
        }

    }

    public void onStartCustomActions() {

        //pumpStatusLocal.setNotInPreInit();
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

    // we don't loadTDD
    @Override
    public PumpEnactResult loadTDDs() {
        return OPERATION_NOT_SUPPORTED;
    }


    // Pump Plugin

    private boolean isServiceSet() {
        return medtronicService != null;
    }

    public boolean isInitialized() {
        return isServiceSet() && medtronicService.isInitialized();
    }

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

    // FIXME
    @Override
    public void getPumpStatus() {

        if (this.pumpStatusLocal == null) {
            // FIXME I don't know why this happens
            LOG.debug("getPumpStatus: reset pumoStatusLocal ");
            this.pumpStatusLocal = MedtronicUtil.getPumpStatus();
        }

        if (firstRun) {
            initializePump();
        }

        LOG.error("MedtronicPumpPlugin::getPumpStatus NOT IMPLEMENTED.");

        LOG.debug("getPumpStatus: {}", this.pumpStatusLocal);
        LOG.debug("getPumpStatus: {}", MedtronicUtil.getPumpStatus());

        pumpStatusLocal.setLastCommunicationToNow();

        MainApp.bus().post(new EventMedtronicPumpValuesChanged());
    }

    private void initializePump() {

        LOG.error("MedtronicPumpPlugin::initializePump NOT IMPLEMENTED.");

        // pump history handling - special, updates every 5 minutes ???

        // remaining insulin (>50 = 4h; 50-20 = 1h; 15m)
        medtronicUIComm.executeCommand(MedtronicCommandType.GetRemainingInsulin);

        // remaining power (1h)

        // configuration (1h -> maybe just if something configured (something we are interested in))

        // read profile
        medtronicUIComm.executeCommand(MedtronicCommandType.GetBasalProfileSTD);


        this.firstRun = false;
    }


    @Override
    public boolean isThisProfileSet(Profile profile) {
        //LOG.error("MedtronicPumpPlugin::isThisProfileSet NOT IMPLEMENTED.");

        if (!isConnected()) {
            return true;
        }

        MedtronicUITask responseTask = medtronicUIComm.executeCommand(MedtronicCommandType.GetBasalProfileSTD);

        boolean invalid = false;

        if (responseTask.haveData()) {

            Double[] basalsByHour = getMDTPumpStatus().basalsByHour;

            int index = 0;

            for (Profile.BasalValue basalValue : profile.getBasalValues()) {

                int hour = basalValue.timeAsSeconds / (60 * 60);

                //LOG.debug("Basal profile::Pump rate={}, NS rate={}", basalsByHour[index], basalValue.value);
                //LOG.debug("Basal profile::Pump time={}, NS time={}", index, basalValue.timeAsSeconds / (60 * 60));

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
                LOG.debug("Basal profile on Pump is differentr than AAPS one.");
            }

        } else {
            invalid = true;
            LOG.debug("Basal profile NO DATA");
        }

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
            LOG.warn("Reset Pump Status Local");
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

            MedtronicUITask responseTask = medtronicUIComm.executeCommand(MedtronicCommandType.SetBolus, detailedBolusInfo.insulin);

            Boolean response = (Boolean) responseTask.returnData;

            if (response) {
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
//            MainApp.bus().post(new EventRefreshOverview("Bolus"));
//            cancelBolus = false;
            triggerUIChange();
        }
    }

    @Override
    public void stopBolusDelivering() {
    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {

        LOG.error("MedtronicPumpPlugin::setTempBasalAbsolute Not fully implemented - Just base command.");

        MedtronicUITask responseTask = medtronicUIComm.executeCommand(MedtronicCommandType.SetTemporaryBasal, absoluteRate, durationInMinutes);

        Boolean response = (Boolean) responseTask.returnData;

        if (response) {
            //pumpStatusLocal.tempBasalStart = new Date(); // TODO maybe not needed
            //pumpStatusLocal.tempBasalRemainMin = durationInMinutes;
        }

        readPumpHistory();

        return new PumpEnactResult().success(response).enacted(response);
    }

    private void readPumpHistory() {
        LOG.error("MedtronicPumpPlugin::readPumpHistory NOT IMPLEMENTED.");
    }


    // TODO
    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        LOG.error("MedtronicPumpPlugin::setExtendedBolus NOT IMPLEMENTED.");

        return null;
        //return OPERATION_NOT_YET_SUPPORTED;
    }

    // TODO
    @Override
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {

        LOG.error("MedtronicPumpPlugin::cancelTempBasal Not fully implemented - Just base command.");

        MedtronicUITask responseTask = medtronicUIComm.executeCommand(MedtronicCommandType.CancelTBR);

        Boolean response = (Boolean) responseTask.returnData;

        if (response) {
        }

        readPumpHistory();

        return new PumpEnactResult().success(response).enacted(response);
    }


    // TODO not supported but we will display message to user that he/she should do it on the pump
    @Override
    public PumpEnactResult cancelExtendedBolus() {
        LOG.error("MedtronicPumpPlugin::cancelExtendedBolus NOT IMPLEMENTED.");

        return null;
    }


    // OPERATIONS not supported by Pump

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        LOG.error("MedtronicPumpPlugin::setTempBasalPercent NOT IMPLEMENTED.");

        return null;
    }


    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        LOG.error("MedtronicPumpPlugin::setNewBasalProfile NOT IMPLEMENTED.");

        LOG.debug("isSetBasalProfileCapable:" + this.pumpDescription.isSetBasalProfileCapable);

        return new PumpEnactResult().success(false).enacted(false).comment(MainApp.gs(R.string.medtronic_cmd_profile_not_set));
    }


}
