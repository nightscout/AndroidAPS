package info.nightscout.androidaps.plugins.PumpCombo;

import android.os.SystemClock;
import android.support.annotation.NonNull;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import de.jotomo.ruffy.spi.BolusProgressReporter;
import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.RuffyCommands;
import de.jotomo.ruffy.spi.history.Bolus;
import de.jotomo.ruffy.spi.history.PumpHistoryRequest;
import de.jotomo.ruffy.spi.history.Tbr;
import de.jotomo.ruffyscripter.RuffyCommandsV1Impl;
import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.PumpCombo.events.EventComboPumpUpdateGUI;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;

/**
 * Created by mike on 05.08.2016.
 */
public class ComboPlugin implements PluginBase, PumpInterface {
    private static Logger log = LoggerFactory.getLogger(ComboPlugin.class);

    private boolean fragmentEnabled = false;
    private boolean fragmentVisible = false;

    private PumpDescription pumpDescription = new PumpDescription();

    @NonNull
    private final RuffyCommands ruffyScripter;

    // TODO access to pump (and its members) is chaotic and needs an update
    private static ComboPump pump = new ComboPump();

    private static ComboPlugin plugin = null;

    public static ComboPlugin getPlugin() {
        if (plugin == null)
            plugin = new ComboPlugin();
        return plugin;
    }

    private static PumpEnactResult OPERATION_NOT_SUPPORTED = new PumpEnactResult();

    static {
        OPERATION_NOT_SUPPORTED.success = false;
        OPERATION_NOT_SUPPORTED.enacted = false;
        OPERATION_NOT_SUPPORTED.comment = "Requested operation not supported by pump";
    }

    private ComboPlugin() {
        definePumpCapabilities();
        ruffyScripter = RuffyCommandsV1Impl.getInstance(MainApp.instance());
    }

    private void definePumpCapabilities() {
        pumpDescription.isBolusCapable = true;
        pumpDescription.bolusStep = 0.1d;

        pumpDescription.isExtendedBolusCapable = false;
        pumpDescription.extendedBolusStep = 0.1d;
        pumpDescription.extendedBolusDurationStep = 15;
        pumpDescription.extendedBolusMaxDuration = 12 * 60;

        pumpDescription.isTempBasalCapable = true;
        pumpDescription.tempBasalStyle = PumpDescription.PERCENT;

        pumpDescription.maxTempPercent = 500;
        pumpDescription.tempPercentStep = 10;

        pumpDescription.tempDurationStep = 15;
        pumpDescription.tempMaxDuration = 24 * 60;


        pumpDescription.isSetBasalProfileCapable = false;
        pumpDescription.basalStep = 0.01d;
        pumpDescription.basalMinimumRate = 0.0d;

        pumpDescription.isRefillingCapable = true;
    }

    public ComboPump getPump() {
        return pump;
    }

    @Override
    public String getFragmentClass() {
        return ComboFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.combopump);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.combopump_shortname);
        if (!name.trim().isEmpty()) {
            //only if translation exists
            return name;
        }
        // use long name as fallback
        return getName();
    }

    String getStateSummary() {
        PumpState ps = pump.state;
        if (ps.menu == null)
            return MainApp.sResources.getString(R.string.combo_pump_state_disconnected);
        else if (ps.suspended && (ps.batteryState == PumpState.EMPTY || ps.insulinState == PumpState.EMPTY))
            return MainApp.sResources.getString(R.string.combo_pump_state_suspended_due_to_error);
        else if (ps.suspended)
            return MainApp.sResources.getString(R.string.combo_pump_state_suspended_by_user);
        return MainApp.sResources.getString(R.string.combo_pump_state_normal);
    }

    @Override
    public boolean isEnabled(int type) {
        return type == PUMP && fragmentEnabled;
    }

    @Override
    public boolean isVisibleInTabs(int type) {
        return type == PUMP && fragmentVisible;
    }

    @Override
    public boolean canBeHidden(int type) {
        return true;
    }

    @Override
    public boolean hasFragment() {
        return true;
    }

    @Override
    public boolean showInList(int type) {
        return true;
    }

    @Override
    public void setFragmentEnabled(int type, boolean fragmentEnabled) {
        if (type == PUMP) this.fragmentEnabled = fragmentEnabled;
    }

    @Override
    public void setFragmentVisible(int type, boolean fragmentVisible) {
        if (type == PUMP) this.fragmentVisible = fragmentVisible;
    }

    @Override
    public int getType() {
        return PluginBase.PUMP;
    }

    @Override
    public boolean isInitialized() {
        return pump.initialized;
    }

    @Override
    public boolean isSuspended() {
        return pump.state.suspended;
    }

    @Override
    public boolean isBusy() {
        return ruffyScripter.isPumpBusy() && !pump.state.suspended;
    }

    @Override
    public int setNewBasalProfile(Profile profile) {
        return FAILED;
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        return false;
    }

    @NonNull
    @Override
    public Date lastDataTime() {
        return new Date(pump.lastSuccessfulConnection);
    }

    /**
     * Runs pump initializing if needed, checks for boluses given on the pump, updates the
     * reservoir level and checks the running TBR on the pump.
     */
    @Override
    public synchronized void refreshDataFromPump(String reason) {
        log.debug("RefreshDataFromPump called");
        if (!pump.initialized) {
            // TODO reading profile
            long maxWait = System.currentTimeMillis() + 15 * 1000;
            while (!ruffyScripter.isPumpAvailable()) {
                log.debug("Waiting for ruffy service to be connected ...");
                SystemClock.sleep(100);
                if (System.currentTimeMillis() > maxWait) {
                    log.debug("ruffy service unavailable, wtf");
                    return;
                }
            }
            runCommand("Initializing", () -> ruffyScripter.readHistory(new PumpHistoryRequest()));
            pump.initialized = true;
        }

        runCommand("Refreshing", ruffyScripter::readReservoirLevelAndLastBolus);

        // TODO fuse the below into 'sync'? or make checkForTbrMismatch jut a trigger to issue a sync if needed; don't run sync twice as is nice
//        checkForTbrMismatch();
//        checkPumpHistory();
    }

    /**
     * Checks if there are any changes on the pump AAPS isn't aware of yet and if so, read the
     * full pump history and update AAPS' DB.
     */
    private void checkPumpHistory() {
        CommandResult commandResult = runCommand(MainApp.sResources.getString(R.string.combo_pump_action_checking_history), () ->
                ruffyScripter.readHistory(
                        new PumpHistoryRequest()
                                .bolusHistory(PumpHistoryRequest.LAST)
                                .tbrHistory(PumpHistoryRequest.LAST)
                                .errorHistory(PumpHistoryRequest.LAST)));

        if (!commandResult.success || commandResult.history == null) {
            // TODO error case, command
            return;
        }

        // TODO opt, construct PumpHistoryRequest to requset only what needs updating
        boolean syncNeeded = false;
        PumpHistoryRequest request = new PumpHistoryRequest();

        // last bolus
        List<Treatment> treatments = MainApp.getConfigBuilder().getTreatmentsFromHistory();
        Collections.reverse(treatments);
        Treatment aapsBolus = null;
        for (Treatment t : treatments) {
            if (t.insulin != 0) {
                aapsBolus = t;
                break;
            }
        }
        Bolus pumpBolus = null;
        List<Bolus> bolusHistory = commandResult.history.bolusHistory;
        if (!bolusHistory.isEmpty()) {
            pumpBolus = bolusHistory.get(0);
        }

        if ((aapsBolus == null || pumpBolus == null)
                || (Math.abs(aapsBolus.insulin - pumpBolus.amount) > 0.05 || aapsBolus.date != pumpBolus.timestamp)) {
            syncNeeded = true;
            request.bolusHistory = PumpHistoryRequest.FULL;
        }

        // last tbr
        List<TemporaryBasal> tempBasals = MainApp.getConfigBuilder().getTemporaryBasalsFromHistory().getReversedList();
        TemporaryBasal aapsTbr = null;
        if (!tempBasals.isEmpty()) {
            aapsTbr = tempBasals.get(0);
        }
        Tbr pumpTbr = null;
        List<Tbr> tbrHistory = commandResult.history.tbrHistory;
        if (!tbrHistory.isEmpty()) {
            pumpTbr = tbrHistory.get(0);
        }
        if ((aapsTbr == null || pumpTbr == null)
                || (aapsTbr.percentRate != pumpTbr.percent || aapsTbr.durationInMinutes != pumpTbr.duration)) {
            syncNeeded = true;
            request.tbrHistory = PumpHistoryRequest.FULL;
        }

        // last error
        // TODO add DB table ... or just keep in memory? does android allow that (fragment kill frenzy) without workarounds?
        // is comboplugin a service or a class with statics?
        request.pumpErrorHistory = PumpHistoryRequest.FULL;

        // tdd
        // TODO; ... just fetch them on-deamand when the user opens the fragment?


        if (syncNeeded) {
            runFullSync(request);
        }
    }

    // TODO uses profile values for the time being
    @Override
    public double getBaseBasalRate() {
/*        if (pump.basalProfile == null) {
// TODO when to force refresh this?
            CommandResult result = runCommand("Reading basal profile", new CommandExecution() {
                @Override
                public CommandResult execute() {
                    return ruffyScripter.readBasalProfile(1);
                }
            });
            pump.basalProfile = result.basalProfile;
            // TODO error handling ...
        }
        return pump.basalProfile.hourlyRates[Calendar.getInstance().get(Calendar.HOUR_OF_DAY)];
*/

        Profile profile = MainApp.getConfigBuilder().getProfile();
        Double basal = profile.getBasal();
        log.trace("getBaseBasalrate returning " + basal);
        return basal;
    }

    private static BolusProgressReporter nullBolusProgressReporter = (state, percent, delivered) -> {
    };

    private static BolusProgressReporter bolusProgressReporter = (state, percent, delivered) -> {
        EventOverviewBolusProgress event = EventOverviewBolusProgress.getInstance();
        switch (state) {
            case PROGRAMMING:
                event.status = MainApp.sResources.getString(R.string.combo_programming_bolus);
                break;
            case DELIVERING:
                event.status = String.format(MainApp.sResources.getString(R.string.bolusdelivering), delivered);
                break;
            case DELIVERED:
                event.status = String.format(MainApp.sResources.getString(R.string.bolusdelivered), delivered);
                break;
            case STOPPING:
                event.status = MainApp.sResources.getString(R.string.bolusstopping);
                break;
            case STOPPED:
                event.status = MainApp.sResources.getString(R.string.bolusstopped);
                break;
            case FINISHED:
                // no state, just percent below to close bolus progress dialog
                break;
        }
        event.percent = percent;
        MainApp.bus().post(event);
    };

    /**
     * Updates Treatment records with carbs and boluses and delivers a bolus if needed
     */
    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        // TODO for non-SMB: read resorvoir level first to make sure there's enough insulin left
        try {
            if (detailedBolusInfo.insulin == 0 && detailedBolusInfo.carbs == 0) {
                // neither carbs nor bolus requested
                PumpEnactResult pumpEnactResult = new PumpEnactResult();
                pumpEnactResult.success = false;
                pumpEnactResult.enacted = false;
                pumpEnactResult.bolusDelivered = 0d;
                pumpEnactResult.carbsDelivered = 0d;
                pumpEnactResult.comment = MainApp.instance().getString(R.string.danar_invalidinput);
                log.error("deliverTreatment: Invalid input");
                return pumpEnactResult;
            } else if (detailedBolusInfo.insulin > 0) {
                // bolus needed, ask pump to deliver it
                return deliverBolus(detailedBolusInfo);
            } else {
                // no bolus required, carb only treatment
                SystemClock.sleep(6000);
                PumpEnactResult pumpEnactResult = new PumpEnactResult();
                pumpEnactResult.success = true;
                pumpEnactResult.enacted = true;
                pumpEnactResult.bolusDelivered = 0d;
                pumpEnactResult.carbsDelivered = detailedBolusInfo.carbs;
                pumpEnactResult.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
                MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);

                EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
                bolusingEvent.percent = 100;
                MainApp.bus().post(bolusingEvent);
                return pumpEnactResult;
            }
        } finally {
            MainApp.bus().post(new EventComboPumpUpdateGUI());
        }
    }

    @NonNull
    private PumpEnactResult deliverBolus(final DetailedBolusInfo detailedBolusInfo) {
        // TODO
        // before non-SMB: check enough insulin is available, check we're up to date on boluses
        // after bolus: update reservoir level and check the bolus we just did is actually there

        // retry flag: reconnect, kill warning, check if command can be restarted, restart
        CommandResult bolusCmdResult = runCommand(MainApp.sResources.getString(R.string.combo_action_bolusing), () -> ruffyScripter.deliverBolus(detailedBolusInfo.insulin,
                detailedBolusInfo.isSMB ? nullBolusProgressReporter : bolusProgressReporter));

        PumpEnactResult pumpEnactResult = new PumpEnactResult();
        pumpEnactResult.success = bolusCmdResult.success;
        pumpEnactResult.enacted = bolusCmdResult.enacted;

        // if enacted, add bolus and carbs to treatment history
        if (pumpEnactResult.enacted) {
            // TODO if no error occurred, the requested bolus is what the pump delievered,
            // that has been checked. If an error occurred, we should check how much insulin
            // was delivered, e.g. when the cartridge went empty mid-bolus
            // For the first iteration, the alert the pump raises must suffice
            pumpEnactResult.bolusDelivered = detailedBolusInfo.insulin;
            pumpEnactResult.carbsDelivered = detailedBolusInfo.carbs;

            detailedBolusInfo.date = System.currentTimeMillis();
            MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
        } else {
            pumpEnactResult.bolusDelivered = 0d;
            pumpEnactResult.carbsDelivered = 0d;
        }
        return pumpEnactResult;
    }

    @Override
    public void stopBolusDelivering() {
        // TODO note that we requested this, so we can thandle this proper in runCommand;
        // or is it fine if the command returns success with noting enacted and history checks as well/**/
        ruffyScripter.cancelBolus();
    }

    // Note: AAPS calls this only to enact OpenAPS recommendations
    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, boolean force) {
        // the force parameter isn't used currently since we always set the tbr - there might be room for optimization to
        // first test the currently running tbr and only change it if it differs (as the DanaR plugin does).
        // This approach might have other issues though (what happens if the tbr which wasn't re-set to the new value
        // (and thus still has the old duration of e.g. 1 min) expires?)
        log.debug("setTempBasalAbsolute called with a rate of " + absoluteRate + " for " + durationInMinutes + " min.");
        int unroundedPercentage = Double.valueOf(absoluteRate / getBaseBasalRate() * 100).intValue();
        int roundedPercentage = (int) (Math.round(absoluteRate / getBaseBasalRate() * 10) * 10);
        if (unroundedPercentage != roundedPercentage) {
            log.debug("Rounded requested rate " + unroundedPercentage + "% -> " + roundedPercentage + "%");
        }

        return setTempBasalPercent(roundedPercentage, durationInMinutes);
    }

    // Note: AAPS calls this only for setting a temp basal issued by the user
    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, final Integer durationInMinutes) {
        log.debug("setTempBasalPercent called with " + percent + "% for " + durationInMinutes + "min");

        int adjustedPercent = percent;

        if (adjustedPercent > pumpDescription.maxTempPercent) {
            log.debug("Reducing requested TBR to the maximum support by the pump: " + percent + " -> " + pumpDescription.maxTempPercent);
            adjustedPercent = pumpDescription.maxTempPercent;
        }

        if (adjustedPercent % 10 != 0) {
            Long rounded = Math.round(adjustedPercent / 10d) * 10;
            log.debug("Rounded requested percentage:" + adjustedPercent + " -> " + rounded);
            adjustedPercent = rounded.intValue();
        }

        final int finalAdjustedPercent = adjustedPercent;
        CommandResult commandResult = runCommand(MainApp.sResources.getString(R.string.combo_pump_action_setting_tbr), () -> ruffyScripter.setTbr(finalAdjustedPercent, durationInMinutes));

        if (commandResult.enacted) {
            pump.tbrSetTime = System.currentTimeMillis();
            TemporaryBasal tempStart = new TemporaryBasal(pump.tbrSetTime);
            // TODO commandResult.state.tbrRemainingDuration might already display 29 if 30 was set, since 29:59 is shown as 29 ...
            // we should check this, but really ... something must be really screwed up if that number was anything different
            // TODO actually ... might setting 29 help with gaps between TBRs? w/o the hack in TemporaryBasal?
            // nah, fucks up duration in treatment history
            tempStart.durationInMinutes = durationInMinutes;
            tempStart.percentRate = adjustedPercent;
            tempStart.isAbsolute = false;
            tempStart.source = Source.USER;
            ConfigBuilderPlugin treatmentsInterface = MainApp.getConfigBuilder();
            treatmentsInterface.addToHistoryTempBasal(tempStart);
        }

        PumpEnactResult pumpEnactResult = new PumpEnactResult();
        pumpEnactResult.success = commandResult.success;
        pumpEnactResult.enacted = commandResult.enacted;
        pumpEnactResult.isPercent = true;
        // Combo would have bailed if this wasn't set properly. Maybe we should
        // have the command return this anyways ...
        pumpEnactResult.percent = adjustedPercent;
        pumpEnactResult.duration = durationInMinutes;

        return pumpEnactResult;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        return OPERATION_NOT_SUPPORTED;
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean userRequested) {
        log.debug("cancelTempBasal called");

        CommandResult commandResult = null;
        TemporaryBasal tempBasal = null;
        PumpEnactResult pumpEnactResult = new PumpEnactResult();

        final TemporaryBasal activeTemp = MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis());

        if (activeTemp == null || userRequested) {
            /* v1 compatibility to sync DB to pump if they diverged (activeTemp == null) */
            log.debug("cancelTempBasal: hard-cancelling TBR since user requested");
            commandResult = runCommand(MainApp.sResources.getString(R.string.combo_pump_action_cancelling_tbr), ruffyScripter::cancelTbr);

            if (commandResult.enacted) {
                tempBasal = new TemporaryBasal(System.currentTimeMillis());
                tempBasal.durationInMinutes = 0;
                tempBasal.source = Source.USER;
                pumpEnactResult.isTempCancel = true;
            }
        } else if ((activeTemp.percentRate >= 90 && activeTemp.percentRate <= 110) && activeTemp.getPlannedRemainingMinutes() <= 15) {
            // Let fake neutral temp keep run (see below)
            log.debug("cancelTempBasal: skipping changing tbr since it already is at " + activeTemp.percentRate + "% and running for another " + activeTemp.getPlannedRemainingMinutes() + " mins.");
            pumpEnactResult.comment = "cancelTempBasal skipping changing tbr since it already is at " + activeTemp.percentRate + "% and running for another " + activeTemp.getPlannedRemainingMinutes() + " mins.";
            // TODO check what AAPS does with this; no DB update is required;
            // looking at info/nightscout/androidaps/plugins/Loop/LoopPlugin.java:280
            // both values are used as one:
            //   if (applyResult.enacted || applyResult.success) { ...
            pumpEnactResult.enacted = true; // all fine though...
            pumpEnactResult.success = true; // just roll with it...
        } else {
            // Set a fake neutral temp to avoid TBR cancel alert. Decide 90% vs 110% based on
            // on whether the TBR we're cancelling is above or below 100%.
            final int percentage = (activeTemp.percentRate > 100) ? 110 : 90;
            log.debug("cancelTempBasal: changing TBR to " + percentage + "% for 15 mins.");
            commandResult = runCommand(MainApp.sResources.getString(R.string.combo_pump_action_setting_tbr), () -> ruffyScripter.setTbr(percentage, 15));

            if (commandResult.enacted) {
                tempBasal = new TemporaryBasal(System.currentTimeMillis());
                tempBasal.durationInMinutes = 15;
                tempBasal.source = Source.USER;
                tempBasal.percentRate = percentage;
                tempBasal.isAbsolute = false;
            }
        }

        if (tempBasal != null) {
            ConfigBuilderPlugin treatmentsInterface = MainApp.getConfigBuilder();
            treatmentsInterface.addToHistoryTempBasal(tempBasal);
        }

        if (commandResult != null) {
            pumpEnactResult.success = commandResult.success;
            pumpEnactResult.enacted = commandResult.enacted;
        }
        return pumpEnactResult;
    }

    interface CommandExecution {
        CommandResult execute();
    }

    // TODO if there was an error (or the pump was suspended) force a resync before a bolus;
    // transport a message, e.g. 'new bolus found on pump, synced, check and issue bolus again'
    // back to the user?b

    private synchronized CommandResult runCommand(String activity, CommandExecution commandExecution) {
        if (activity != null) {
            // danar has this is message on the overview screen, no?
            pump.activity = activity;
            MainApp.bus().post(new EventComboPumpUpdateGUI());
        }

        // TODO should probably specialize this:
        // smb, tbr stuff can be retried.
        // non-smb bolus shall be blocked if same amount already delivered within last 1 min

        // i need a version of this which sets activity, updates local state etc and one
        // which just does something, read history etc.
        // retrying: separate r/o and r/w commands? flag for it?

//        CommandResult precheck = ruffyScripter.readPumpState();
        // tbrcheck?
        // check for active alert; if warning confirm; on warning confirm, read history (bolus, errors), which shall raise alerts if appropriate
        //
//        precheck.
        // todo don't send out commands requining run mode if pump is suspended
        //

        CommandResult commandResult = commandExecution.execute();
        pump.lastCmdResult = commandResult;
        pump.lastConnectionAttempt = System.currentTimeMillis();
        if (commandResult.success) {
            pump.lastSuccessfulConnection = System.currentTimeMillis();
        } else {
            // TODO set flag to force sync on next connect; try to run immediately? or would this make things worse and we should just wait till the next iteratio?L
        }

        // copy over state (as supplied) so it will still be available when another command runs that doesn't return that data
        pump.state = commandResult.state;
        if (commandResult.reservoirLevel != -1) {
            pump.reservoirLevel = commandResult.reservoirLevel;
        }

        if (commandResult.lastBolus != null) {
            pump.lastBolus = commandResult.lastBolus;
        }

        if (commandResult.history != null) {
            if (!commandResult.history.bolusHistory.isEmpty()) {
                pump.lastBolus = commandResult.history.bolusHistory.get(0);
            }
            pump.history = commandResult.history;
        }


        // TODO hm... automatically confirm messages and return them and handle them here proper?
        // with an option to corfirm all messages, non-critical (letting occlusion alert ring on phone and pump)
        // or let all alarms ring and don't try to control the pump in any way

        // option how to deal with errors on connect; allow to explicitely be okay with e.g. TBR CANCELLED after interruption?!
        // or a separate command to check and deal with pump state? run a check command before all operations?

        // maybe think less in 'all in one command', but simpler commands?
        // get the current state, then decide what makes sense to do further, if anything,
        // send next request.
        // then request state again ... ?
        // TODO rethink this errorMsg field;

/*        if (commandResult.state.errorMsg != null) {
            CommandResult takeOverAlarmResult = ruffyScripter.takeOverAlarms();

            for (PumpError pumpError : takeOverAlarmResult.history.pumpErrorHistory) {
                MainApp.bus().post(new EventNewNotification(
                        new Notification(Notification.COMBO_PUMP_ALARM,
                        "Pump alarm: " + pumpError.message, Notification.URGENT)));
            }

            commandResult.state = takeOverAlarmResult.state;
        }*/


        // TODO call this explicitely when needed after/before calling this?
//        if (checkTbrMisMatch) {
//            checkForTbrMismatch();
//        }


        // TODO in the event of an error schedule a resync

        if (activity != null) {
            pump.activity = null;
            MainApp.bus().post(new EventComboPumpUpdateGUI());
        }
        return commandResult;
    }

    // TODO rename to checkState or so and also check time (& date) of pump
    private void checkForTbrMismatch() {
        // detectTbrMismatch(): 'quick' check with no overhead on the pump side
        // TODO check if this works with pump suspend, esp. around pump suspend there'll be syncing to do;

        // TODO we need to tolerate differences of 1-2 minutes due to the time it takes to programm a tbr
        // mismatching a 5m interval etc

        TemporaryBasal aapsTbr = MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis());
        boolean sync = false;
        if (aapsTbr == null && pump.state.tbrActive) {
            // pump runs TBR AAPS is unaware off
            log.debug("Pump runs TBR AAPS is unaware of, reading last 3h of pump TBR history");
            sync = true;
        } else if (aapsTbr != null && !pump.state.tbrActive) {
            // AAPS has a TBR but the pump isn't running a TBR
            log.debug("AAPS shows TBR but pump isn't running a TBR; deleting TBR in AAPS and reading last 3h of pump TBR history");
            MainApp.getDbHelper().delete(aapsTbr);
            sync = true;
        } else if (aapsTbr != null && pump.state.tbrActive) {
            // both AAPS and pump have a TBR ...
            if (aapsTbr.percentRate != pump.state.tbrPercent) {
                // ... but they have different percentages
                log.debug("TBR percentage differs between AAPS and pump; deleting TBR in AAPS and reading last 3h of pump TBR history");
                MainApp.getDbHelper().delete(aapsTbr);
                sync = true;
            }
            int durationDiff = Math.abs(aapsTbr.getPlannedRemainingMinutes() - pump.state.tbrRemainingDuration);
            if (durationDiff > 2) {
                // ... but they have different runtimes
                log.debug("TBR duration differs between AAPS and pump; deleting TBR in AAPS and reading last 3h of pump TBR history");
                MainApp.getDbHelper().delete(aapsTbr);
                sync = true;
            }
        }
        if (sync) {
            // todo just return the PHR?
            runFullSync(new PumpHistoryRequest().tbrHistory(System.currentTimeMillis() - 3 * 60 * 60 * 1000));
        }

        // TODO request a loop run to (re)apply a TBR/SMB given this new information? or just wait till next iteration?
        // could take 15m or so if there are missed SGVs ...
        // new sensitivity calc required, no?

    }

    private void runFullSync(final PumpHistoryRequest request) {
        CommandResult result = runCommand("Syncing full pump history", () -> ruffyScripter.readHistory(request));

        // boluses

        // TBRs

        // errors
        // TODO

    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        return OPERATION_NOT_SUPPORTED;
    }

    @Override
    public JSONObject getJSONStatus() {
        if (!pump.initialized) {
            return null;
        }

        try {
            JSONObject pumpJson = new JSONObject();
            pumpJson.put("clock", DateUtil.toISOString(pump.lastSuccessfulConnection));
            pumpJson.put("reservoir", pump.reservoirLevel);

            JSONObject statusJson = new JSONObject();
            statusJson.put("status", getStateSummary());
            statusJson.put("timestamp", pump.lastSuccessfulConnection);
            pumpJson.put("status", statusJson);

            JSONObject extendedJson = new JSONObject();
            extendedJson.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            extendedJson.put("ActiveProfile", MainApp.getConfigBuilder().getProfileName());
            if (pump.lastBolus != null) {
                extendedJson.put("LastBolus", new Date(pump.lastBolus.timestamp).toLocaleString());
                extendedJson.put("LastBolusAmount", DecimalFormatter.to1Decimal(pump.lastBolus.amount));
            }
            PumpState ps = pump.state;
            if (ps.tbrActive) {
                extendedJson.put("TempBasalAbsoluteRate", ps.tbrRate);
                extendedJson.put("TempBasalPercent", ps.tbrPercent);
                extendedJson.put("TempBasalRemaining", ps.tbrRemainingDuration);
            }
            if (ps.alertCodes != null && ps.alertCodes.errorCode != null) {
                extendedJson.put("ErrorCode", ps.alertCodes.errorCode);
            }
            pumpJson.put("extended", extendedJson);

            JSONObject batteryJson = new JSONObject();
            int battery;
            switch (ps.batteryState) {
                case PumpState.EMPTY:
                    battery = 0;
                    break;
                case PumpState.LOW:
                    battery = 25;
                    break;
                default:
                    battery = 75;
                    break;
            }
            batteryJson.put("percent", battery);
            pumpJson.put("battery", batteryJson);

            return pumpJson;
        } catch (Exception e) {
            log.warn("Failed to gather device status for upload", e);
        }

        return null;
    }

    @Override
    public String deviceID() {
        return "Combo";
    }

    @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @Override
    public String shortStatus(boolean veryShort) {
        return getStateSummary();
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }
}