package info.nightscout.androidaps.plugins.PumpCombo;

import android.os.SystemClock;
import android.support.annotation.NonNull;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.Objects;

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
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.DatabaseHelper;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.events.EventTreatmentChange;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.PumpCombo.events.EventComboPumpUpdateGUI;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;

import static de.jotomo.ruffy.spi.BolusProgressReporter.State.FINISHED;

/**
 * Created by mike on 05.08.2016.
 */
public class ComboPlugin implements PluginBase, PumpInterface, ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(ComboPlugin.class);

    private boolean fragmentEnabled = false;
    private boolean fragmentVisible = false;

    private PumpDescription pumpDescription = new PumpDescription();

    @NonNull
    private final RuffyCommands ruffyScripter;

    // TODO access to pump (and its members) is chaotic and needs an update
    private static ComboPump pump = new ComboPump();

    private static ComboPlugin plugin = null;

    private volatile boolean bolusInProgress;
    private volatile boolean cancelBolus;

    public static ComboPlugin getPlugin() {
        if (plugin == null)
            plugin = new ComboPlugin();
        return plugin;
    }

    private static PumpEnactResult OPERATION_NOT_SUPPORTED = new PumpEnactResult();

    static {
        OPERATION_NOT_SUPPORTED.success = false;
        OPERATION_NOT_SUPPORTED.enacted = false;
        OPERATION_NOT_SUPPORTED.comment = MainApp.sResources.getString(R.string.combo_pump_unsupported_operation);
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
        if (type == PluginBase.PUMP) return fragmentEnabled;
        else if (type == PluginBase.CONSTRAINTS) return fragmentEnabled;
        return false;
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
    public int getPreferencesId() {
        return R.xml.pref_combo;
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
        }

        CommandResult result = runCommand("Refreshing", 3, ruffyScripter::readReservoirLevelAndLastBolus);
        updateLocalData(result);

        // TODO fuse the below into 'sync'? or make checkForTbrMismatch jut a trigger to issue a sync if needed; don't run sync twice as is nice
//        checkForTbrMismatch();
        checkPumpHistory();
        // checkPumpDate()

        pump.initialized = true;
    }

    private void updateLocalData(CommandResult result) {
        if (result.reservoirLevel != PumpState.UNKNOWN) {
            pump.reservoirLevel = result.reservoirLevel;
        }
        if (result.lastBolus != null) {
            pump.lastBolus = result.lastBolus;
        } else if (result.history != null && !result.history.bolusHistory.isEmpty()) {
            pump.lastBolus = result.history.bolusHistory.get(0);
        }
        pump.state = result.state;
        MainApp.bus().post(new EventComboPumpUpdateGUI());
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
        // TODO safety check: reject bolus if same type and amount requested within the
        // same minute;
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
            cancelBolus = false;
            MainApp.bus().post(new EventComboPumpUpdateGUI());
        }
    }

    @NonNull
    private PumpEnactResult deliverBolus(final DetailedBolusInfo detailedBolusInfo) {
        try {
            pump.activity = MainApp.sResources.getString(R.string.combo_pump_action_bolusing);
            MainApp.bus().post(new EventComboPumpUpdateGUI());

            // refresh pump data
            CommandResult reservoirBolusResult = runCommand(null, 3,
                    ruffyScripter::readReservoirLevelAndLastBolus);
            if (!reservoirBolusResult.success) {
                return new PumpEnactResult().success(false).enacted(false);
                // todo anything else needed? persistent connection problems; escalated after 30m, interactive bolus gives generic error, should be fine
            }

            // check enough insulin left for bolus
            if (Math.round(detailedBolusInfo.insulin + 0.5) > reservoirBolusResult.reservoirLevel) {
                return new PumpEnactResult().success(false).enacted(false)
                        .comment(MainApp.sResources.getString(R.string.combo_reservoir_level_insufficant_for_bolus));
            }

            // verify we're update to date and know the most recent bolus
            // TODO should we check against pump.lastBolus or rather the DB ...
            if (!Objects.equals(pump.lastBolus, reservoirBolusResult.lastBolus)) {
                new Thread(this::checkPumpHistory).start();
                return new PumpEnactResult().success(false).enacted(false).
                        comment(MainApp.sResources.getString(R.string.combo_pump_bolus_history_state_mismatch));
            }

            if (cancelBolus) {
                PumpEnactResult pumpEnactResult = new PumpEnactResult();
                pumpEnactResult.success = true;
                pumpEnactResult.enacted = false;
                return pumpEnactResult;
            }

            // start bolus delivery
            bolusInProgress = true;
            CommandResult bolusCmdResult = runCommand(null, 0,
                    () -> ruffyScripter.deliverBolus(detailedBolusInfo.insulin,
                            detailedBolusInfo.isSMB ? nullBolusProgressReporter : bolusProgressReporter));
            bolusInProgress = false;

            if (!bolusCmdResult.success) {
                new Thread(this::checkPumpHistory).start();
                return new PumpEnactResult().success(false).enacted(false)
                        .comment(MainApp.sResources.getString(R.string.combo_bolus_bolus_delivery_failed));
            }

            // verify delivered bolus
            reservoirBolusResult = runCommand(null, 3, ruffyScripter::readReservoirLevelAndLastBolus);
            if (!reservoirBolusResult.success) {
                return new PumpEnactResult().success(false).enacted(false)
                        .comment(MainApp.sResources.getString(R.string.combo_pump_bolus_verification_failed));
            }
            Bolus lastPumpBolus = reservoirBolusResult.lastBolus;
            if (cancelBolus) {
                // if cancellation was requested, the delivered bolus is allowed to differ from requested
            } else if (lastPumpBolus == null || lastPumpBolus.amount != detailedBolusInfo.insulin) {
                // TODO schedule forced history sync
                return new PumpEnactResult().success(false).enacted(false).
                        comment(MainApp.sResources.getString(R.string.combo_pump_bolus_verification_failed));
            }

            // update local data
            pump.reservoirLevel = reservoirBolusResult.reservoirLevel;
            pump.lastBolus = lastPumpBolus;
            pump.state = reservoirBolusResult.state;

            // add treatment record to DB (if it wasn't cancelled)
            if (lastPumpBolus != null && (lastPumpBolus.amount > 0)) {
                detailedBolusInfo.insulin = lastPumpBolus.amount;
                detailedBolusInfo.date = lastPumpBolus.timestamp;
                MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
                return new PumpEnactResult()
                        .success(true)
                        .enacted(true)
                        .bolusDelivered(lastPumpBolus.amount)
                        .carbsDelivered(detailedBolusInfo.carbs);
            } else {
                return new PumpEnactResult().success(true).enacted(false);
            }
        } finally {
            // BolusCommand.execute() intentionally doesn't close the progress dialog if an error
            // occurred/ so it stays open while the connection was re-established if needed and/or
            // this method did recovery
            bolusProgressReporter.report(FINISHED, 100, 0);
            pump.activity = null;
            MainApp.bus().post(new EventComboPumpUpdateGUI());
            MainApp.bus().post(new EventRefreshOverview("Combo Bolus"));
        }
    }

    @Override
    public void stopBolusDelivering() {
        if (bolusInProgress) {
            ruffyScripter.cancelBolus();
        } else {
            cancelBolus = true;
        }
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
        CommandResult commandResult = runCommand(MainApp.sResources.getString(R.string.combo_pump_action_setting_tbr), 3, () -> ruffyScripter.setTbr(finalAdjustedPercent, durationInMinutes));

        PumpState state = commandResult.state;
        if (state.tbrActive && state.tbrPercent == percent
                && (state.tbrRemainingDuration == durationInMinutes || state.tbrRemainingDuration == durationInMinutes - 1)) {
            TemporaryBasal tempStart = new TemporaryBasal(state.timestamp);
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

            pump.state = commandResult.state;
            pump.tbrSetTime = state.timestamp;
            MainApp.bus().post(new EventComboPumpUpdateGUI());
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
            commandResult = runCommand(MainApp.sResources.getString(R.string.combo_pump_action_cancelling_tbr), 2, ruffyScripter::cancelTbr);

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
            commandResult = runCommand(MainApp.sResources.getString(R.string.combo_pump_action_setting_tbr), 2, () -> ruffyScripter.setTbr(percentage, 15));

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

    private interface CommandExecution {
        CommandResult execute();
    }

    /**
     * Runs a command, sets an activity if provided, retries if requested and updates fields
     * concerned with last connection.
     * NO history, reservoir level fields are updated, this make be done separately if desired.
     */
    private synchronized CommandResult runCommand(String activity, int retries, CommandExecution commandExecution) {
        CommandResult commandResult;
        try {
            if (activity != null) {
                pump.activity = activity;
                MainApp.bus().post(new EventComboPumpUpdateGUI());
            }

            // TOOD check for active warning and transfer if benign (should then also work with "Refresh" button

            commandResult = commandExecution.execute();

            if (!commandResult.success && retries > 0) {
                for (int retryAttempts = 1; !commandResult.success && retryAttempts <= retries; retryAttempts++) {
                    log.debug("Command was not successful, retries requested, doing retry #" + retryAttempts);
                    commandResult = commandExecution.execute();
                }
            }

            checkForUnsupportedBoluses(commandResult);

            pump.lastCmdResult = commandResult;
            pump.lastConnectionAttempt = System.currentTimeMillis();
            if (commandResult.success) {
                pump.lastSuccessfulConnection = System.currentTimeMillis();
            }
        } finally {
            if (activity != null) {
                pump.activity = null;
                MainApp.bus().post(new EventComboPumpUpdateGUI());
            }
        }
        return commandResult;
    }

    private void checkForUnsupportedBoluses(CommandResult commandResult) {
        long lastViolation = 0;
        if (commandResult.lastBolus != null && !commandResult.lastBolus.isValid) {
            lastViolation = commandResult.lastBolus.timestamp;
        } else if (commandResult.history != null) {
            for (Bolus bolus : commandResult.history.bolusHistory) {
                if (!bolus.isValid && bolus.timestamp > lastViolation) {
                    lastViolation = bolus.timestamp;
                }
            }
        }

        if (lastViolation > 0) {
            closedLoopDisabledUntil = lastViolation + 6 * 60 * 60 * 1000;
            if (closedLoopDisabledUntil > System.currentTimeMillis()) {
                // TODO add message to either Combo tab or its errors popup
                Notification n = new Notification(Notification.COMBO_PUMP_ALARM,
                        MainApp.sResources.getString(R.string.combo_force_disabled),
                        Notification.URGENT);
                n.soundId = R.raw.alarm;
                MainApp.bus().post(new EventNewNotification(n));
            }
        }
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

    /**
     * Checks if there are any changes on the pump AAPS isn't aware of yet and if so, read the
     * full pump history and update AAPS' DB.
     */
    private boolean checkPumpHistory() {
        CommandResult commandResult = runCommand(MainApp.sResources.getString(R.string.combo_pump_action_checking_history), 3, () ->
                ruffyScripter.readHistory(
                        new PumpHistoryRequest()
                                .bolusHistory(PumpHistoryRequest.LAST)
                                .tbrHistory(PumpHistoryRequest.LAST)
                                .errorHistory(PumpHistoryRequest.LAST)));

        if (!commandResult.success || commandResult.history == null) {
            // TODO error case, command
            return false;
        }

        // TODO opt, construct PumpHistoryRequest to requset only what needs updating
        boolean syncNeeded = false;
        PumpHistoryRequest request = new PumpHistoryRequest();

        // last bolus
        List<Treatment> treatments = MainApp.getConfigBuilder().getTreatmentsFromHistory();
//        Collections.reverse(treatments);
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
            log.debug("Last bolus on pump is unknown, refreshing full bolus history");
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
            // TODO
//            log.debug("Last TBR on pump is unknown, refreshing full TBR history");
//            syncNeeded = true;
//            request.tbrHistory = PumpHistoryRequest.FULL;
        }

        // last error
        // TODO add DB table ... or just keep in memory? does android allow that (fragment kill frenzy) without workarounds?
        // is comboplugin a service or a class with statics?
//        request.pumpErrorHistory = PumpHistoryRequest.FULL;

        // tdd
        // TODO; ... just fetch them on-deamand when the user opens the fragment?


        if (syncNeeded) {
            runFullSync(request);
        }

        return true;
    }

    private void runFullSync(final PumpHistoryRequest request) {
        CommandResult result = runCommand("Syncing pump history", 3, () -> ruffyScripter.readHistory(request));
        if (!result.success) {
            // TODO
        }

        DatabaseHelper dbHelper = MainApp.getDbHelper();
        // boluses
        for (Bolus pumpBolus : result.history.bolusHistory) {
            Treatment aapsBolus = dbHelper.getTreatmentByDate(pumpBolus.timestamp);
            if (aapsBolus == null) {
                log.debug("Creating record from pump bolus: " + pumpBolus);
                // info.nightscout.androidaps.plugins.ConfigBuilder.DetailedBolusInfoStorage.findDetailedBolusInfo( ??)
                DetailedBolusInfo dbi = new DetailedBolusInfo();
                dbi.date = pumpBolus.timestamp;
                dbi.insulin = pumpBolus.amount;
                dbi.eventType = CareportalEvent.CORRECTIONBOLUS;
                dbi.source = Source.USER;
                MainApp.getConfigBuilder().addToHistoryTreatment(dbi);
                MainApp.bus().post(new EventTreatmentChange()); // <- updates treatment-bolus tab
                // TODO another event to fforce iobcalc recalc since earliest change? hm, seems autodetected :-)
            }
        }

        // TBRs
        // TODO tolerance for start/end deviations? temp start is based on pump time, but in ms; round to seconds, so we can find it here more easily?

        // errors
        // TODO
        CommandResult refreshResult = runCommand("Refreshing", 3, ruffyScripter::readReservoirLevelAndLastBolus);
        updateLocalData(refreshResult);
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

    // Constraints interface
    private long closedLoopDisabledUntil = 0;

    @Override
    public boolean isLoopEnabled() {
        return true;
    }

    @Override
    public boolean isClosedModeEnabled() {
        return closedLoopDisabledUntil < System.currentTimeMillis();
    }

    @Override
    public boolean isAutosensModeEnabled() {
        return true;
    }

    @Override
    public boolean isAMAModeEnabled() {
        return true;
    }

    @Override
    public Double applyBasalConstraints(Double absoluteRate) {
        return absoluteRate;
    }

    @Override
    public Integer applyBasalConstraints(Integer percentRate) {
        return percentRate;
    }

    @Override
    public Double applyBolusConstraints(Double insulin) {
        return insulin;
    }

    @Override
    public Integer applyCarbsConstraints(Integer carbs) {
        return carbs;
    }

    @Override
    public Double applyMaxIOBConstraints(Double maxIob) {
        return maxIob;
    }
}