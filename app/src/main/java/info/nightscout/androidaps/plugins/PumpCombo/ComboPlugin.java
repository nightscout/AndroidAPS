package info.nightscout.androidaps.plugins.PumpCombo;

import android.os.SystemClock;
import android.support.annotation.NonNull;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.jotomo.ruffy.spi.BasalProfile;
import de.jotomo.ruffy.spi.BolusProgressReporter;
import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.PumpWarningCodes;
import de.jotomo.ruffy.spi.RuffyCommands;
import de.jotomo.ruffy.spi.WarningOrErrorCode;
import de.jotomo.ruffy.spi.history.Bolus;
import de.jotomo.ruffy.spi.history.PumpHistory;
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
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.PumpCombo.events.EventComboPumpUpdateGUI;
import info.nightscout.utils.DateUtil;

/**
 * Created by mike on 05.08.2016.
 */
public class ComboPlugin implements PluginBase, PumpInterface, ConstraintsInterface {
    private static final Logger log = LoggerFactory.getLogger(ComboPlugin.class);

    private static ComboPlugin plugin = null;
    private boolean fragmentEnabled = false;
    private boolean fragmentVisible = false;

    private final static PumpDescription pumpDescription = new PumpDescription();

    static {
        // these properties can't be changed on the pump, some via desktop configuration software
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


        pumpDescription.isSetBasalProfileCapable = true;
        pumpDescription.basalStep = 0.01d;
        pumpDescription.basalMinimumRate = 0.05d;

        pumpDescription.isRefillingCapable = true;
    }

    @NonNull
    private final RuffyCommands ruffyScripter;

    @NonNull
    private static final ComboPump pump = new ComboPump();

    private volatile boolean bolusInProgress;
    private volatile boolean cancelBolus;
    private Bolus lastRequestedBolus;
    private long pumpHistoryLastChecked;

    public static ComboPlugin getPlugin() {
        if (plugin == null)
            plugin = new ComboPlugin();
        return plugin;
    }

    private static final PumpEnactResult OPERATION_NOT_SUPPORTED = new PumpEnactResult()
            .success(false).enacted(false).comment(MainApp.sResources.getString(R.string.combo_pump_unsupported_operation));

    private ComboPlugin() {
        ruffyScripter = RuffyCommandsV1Impl.getInstance(MainApp.instance());
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
        if (ps.activeAlert != null) {
            return ps.activeAlert.errorCode != null
                    ? "E" + ps.activeAlert.errorCode + ": " + ps.activeAlert.message
                    : "W" + ps.activeAlert.warningCode + ": " + ps.activeAlert.message;
        } else if (ps.menu == null)
            return MainApp.sResources.getString(R.string.combo_pump_state_disconnected);
        else if (ps.suspended && (ps.batteryState == PumpState.EMPTY || ps.insulinState == PumpState.EMPTY))
            return MainApp.sResources.getString(R.string.combo_pump_state_suspended_due_to_error);
        else if (ps.suspended)
            return MainApp.sResources.getString(R.string.combo_pump_state_suspended_by_user);
        return MainApp.sResources.getString(R.string.combo_pump_state_running);
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
        return ruffyScripter.isPumpBusy();
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public boolean isConnecting() {
        return false;
    }

    @Override
    public void connect(String reason) {
        // ruffyscripter establishes a connection as needed
    }

    @Override
    public void disconnect(String reason) {
        log.debug("Disconnect called with reason: " + reason);
        ruffyScripter.disconnect();
    }

    @Override
    public void stopConnecting() {
        // we're not doing that
    }

    @Override
    public synchronized PumpEnactResult setNewBasalProfile(Profile profile) {
        if (!isInitialized()) {
            // note that this should not happen anymore since the queue is present, which
            // issues a READSTATE when starting to issue commands which initializes the pump
            log.error("setNewBasalProfile not initialized");
            Notification notification = new Notification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED, MainApp.sResources.getString(R.string.pumpNotInitializedProfileNotSet), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
            return new PumpEnactResult().success(false).enacted(false).comment(MainApp.sResources.getString(R.string.pumpNotInitializedProfileNotSet));
        }

        BasalProfile requestedBasalProfile = convertProfileToComboProfile(profile);
        if (pump.basalProfile.equals(requestedBasalProfile)) {
            //dismiss previously "FAILED" overview notifications
            MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
            MainApp.bus().post(new EventDismissNotification(Notification.FAILED_UDPATE_PROFILE));
            return new PumpEnactResult().success(true).enacted(false);
        }

        CommandResult setResult = runCommand(MainApp.sResources.getString(R.string.combo_activity_setting_basal_profile), 2,
                () -> ruffyScripter.setBasalProfile(requestedBasalProfile));
        if (!setResult.success) {
            Notification notification = new Notification(Notification.FAILED_UDPATE_PROFILE, MainApp.sResources.getString(R.string.failedupdatebasalprofile), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
            return new PumpEnactResult().success(false).enacted(false);
        }

/* don't re-read basal profile to not trigger pump bug; setBasalProfile command checks the total at the end, which must suffice
        CommandResult readResult = runCommand(MainApp.sResources.getString(R.string.combo_activity_setting_basal_profile), 2,
                ruffyScripter::readBasalProfile);
*/

        pump.basalProfile = requestedBasalProfile;

        //dismiss previously "FAILED" overview notifications
        MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
        MainApp.bus().post(new EventDismissNotification(Notification.FAILED_UDPATE_PROFILE));
        //issue success notification
        Notification notification = new Notification(Notification.PROFILE_SET_OK, MainApp.sResources.getString(R.string.profile_set_ok), Notification.INFO, 60);
        MainApp.bus().post(new EventNewNotification(notification));
        return new PumpEnactResult().success(true).enacted(true);
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        if (!isInitialized()) {
            /* This might be called too soon during boot. Return true to prevent a request
               to update the profile. KeepAlive is called every Constants.keepalivems
               and will detect the need for a profile update and apply it.
            */
            return true;
        }
        return pump.basalProfile.equals(convertProfileToComboProfile(profile));
    }

    @NonNull
    private BasalProfile convertProfileToComboProfile(Profile profile) {
        BasalProfile basalProfile = new BasalProfile();
        for (int i = 0; i < 24; i++) {
            double rate = profile.getBasal(Integer.valueOf(i * 60 * 60));

            /*The Combo pump does hava a different granularity for basal rate:
            * 0.01 - if below 1U/h
            * 0.05 - if above 1U/h
            * */

            if (rate < 1) {
                //round to 0.01 granularity;
                rate = Math.round(rate / 0.01) * 0.01;
            } else {
                //round to 0.05 granularity;
                rate = Math.round(rate / 0.05) * 0.05;
            }

            basalProfile.hourlyRates[i] = rate;
        }
        return basalProfile;
    }

    @NonNull
    @Override
    public Date lastDataTime() {
        return new Date(pump.lastSuccessfulCmdTime);
    }

    /**
     * Runs pump initializing if needed, checks for boluses given on the pump, updates the
     * reservoir level and checks the running TBR on the pump.
     */
    @Override
    public synchronized void getPumpStatus() {
        log.debug("getPumpStatus called");
        if (!pump.initialized) {
            long maxWait = System.currentTimeMillis() + 15 * 1000;
            while (!ruffyScripter.isPumpAvailable()) {
                log.debug("Waiting for ruffy service to come up ...");
                SystemClock.sleep(100);
                if (System.currentTimeMillis() > maxWait) {
                    log.debug("ruffy service unavailable, wtf");
                    return;
                }
            }
        }

        CommandResult stateResult = runCommand(pump.initialized ? MainApp.sResources.getString(R.string.combo_pump_action_refreshing) : MainApp.sResources.getString(R.string.combo_pump_action_initializing),
                1, ruffyScripter::readPumpState);
        if (!stateResult.success) {
            return;
        }

        // read basal profile into cache and update pump profile if needed
        if (!pump.initialized) {
            CommandResult readBasalResult = runCommand("Reading basal profile", 2, ruffyScripter::readBasalProfile);
            if (!readBasalResult.success) {
                return;
            }
            pump.basalProfile = readBasalResult.basalProfile;
        }

        if (!pump.initialized) {
            pump.initialized = true;
            MainApp.bus().post(new EventInitializationChanged());
        }

        // ComboFragment updates state fully only after the pump has initialized,
        // this fetches state again and updates the UI proper
        runCommand(null, 0, ruffyScripter::readPumpState);
    }

    private void updateLocalData(CommandResult result) {
        if (result.state.menu != null) {
            pump.state = result.state;
        }
        MainApp.bus().post(new EventComboPumpUpdateGUI());
    }

    @Override
    public double getBaseBasalRate() {
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return pump.basalProfile.hourlyRates[currentHour];
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
            case RECOVERING:
                event.status = MainApp.sResources.getString(R.string.combo_error_bolus_recovery_progress);
        }
        event.percent = percent;
        MainApp.bus().post(event);
    };

    /**
     * Updates Treatment records with carbs and boluses and delivers a bolus if needed
     */
    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        try {
            if (detailedBolusInfo.insulin == 0 && detailedBolusInfo.carbs == 0) {
                // neither carbs nor bolus requested
                log.error("deliverTreatment: Invalid input");
                return new PumpEnactResult().success(false).enacted(false)
                        .bolusDelivered(0d).carbsDelivered(0d)
                        .comment(MainApp.instance().getString(R.string.danar_invalidinput));
            } else if (detailedBolusInfo.insulin > 0) {
                // bolus needed, ask pump to deliver it
                return deliverBolus(detailedBolusInfo);
            } else {
                // no bolus required, carb only treatment
                MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);

                EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
                bolusingEvent.percent = 100;
                MainApp.bus().post(bolusingEvent);

                return new PumpEnactResult().success(true).enacted(true)
                        .bolusDelivered(0d).carbsDelivered(detailedBolusInfo.carbs)
                        .comment(MainApp.instance().getString(R.string.virtualpump_resultok));
            }
        } finally {
            MainApp.bus().post(new EventComboPumpUpdateGUI());
        }
    }

    @NonNull
    private PumpEnactResult deliverBolus(final DetailedBolusInfo detailedBolusInfo) {
        // guard against boluses issued multiple times within a minute
        if (lastRequestedBolus != null
                && Math.abs(lastRequestedBolus.amount - detailedBolusInfo.insulin) < 0.01
                && lastRequestedBolus.timestamp + 60 * 1000 > System.currentTimeMillis()) {
            log.error("Bolus delivery failure at stage 0", new Exception());
            return new PumpEnactResult().success(false).enacted(false)
                    .comment(MainApp.sResources.getString(R.string.bolus_frequency_exceeded));
        }
        lastRequestedBolus = new Bolus(System.currentTimeMillis(), detailedBolusInfo.insulin, true);

        long pumpTimeWhenBolusWasRequested = runCommand(null, 1, ruffyScripter::readPumpState).state.pumpTime;

        try {
            pump.activity = MainApp.sResources.getString(R.string.combo_pump_action_bolusing, detailedBolusInfo.insulin);
            MainApp.bus().post(new EventComboPumpUpdateGUI());

            if (cancelBolus) {
                return new PumpEnactResult().success(true).enacted(false);
            }

            BolusProgressReporter progressReporter = detailedBolusInfo.isSMB ? nullBolusProgressReporter : ComboPlugin.bolusProgressReporter;

            // start bolus delivery
            bolusInProgress = true;
            CommandResult bolusCmdResult = runCommand(null, 0,
                    () -> ruffyScripter.deliverBolus(detailedBolusInfo.insulin, progressReporter));
            bolusInProgress = false;

            if (bolusCmdResult.success) {
                if (bolusCmdResult.delivered > 0) {
                    detailedBolusInfo.insulin = bolusCmdResult.delivered;
                    detailedBolusInfo.source = Source.USER;
                    MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
                }
                return new PumpEnactResult()
                        .success(true)
                        .enacted(bolusCmdResult.delivered > 0)
                        .bolusDelivered(bolusCmdResult.delivered)
                        .carbsDelivered(detailedBolusInfo.carbs);
            } else {
                progressReporter.report(BolusProgressReporter.State.RECOVERING, 0, 0);
                return recoverFromErrorDuringBolusDelivery(detailedBolusInfo, pumpTimeWhenBolusWasRequested);
            }
        } finally {
            pump.activity = null;
            MainApp.bus().post(new EventComboPumpUpdateGUI());
            MainApp.bus().post(new EventRefreshOverview("Combo Bolus"));
            cancelBolus = false;
        }
    }


    /**
     * If there was an error during BolusCommand the scripter reconnects and cleans up. The pump
     * refuses connections while a bolus delivery is still in progress (once bolus delivery started
     * it  continues regardless of a connection loss).
     * Then verify the bolus record we read has a date which is >= the time the bolus was requested
     * (using the pump's time!). If there is such a bolus with <= the requested amount, then it's
     * from this command and shall be added to treatments. If the bolus wasn't delivered in full,
     * add it to treatments but raise a warning. Raise a warning as well if no bolus was delivered
     * at all.
     * This all still might fail for very large boluses and earthquakes in which case an error
     * is raised asking to user to deal with it.
     */
    private PumpEnactResult recoverFromErrorDuringBolusDelivery(DetailedBolusInfo detailedBolusInfo, long pumpTimeWhenBolusWasRequested) {
        log.debug("Trying to determine from pump history what was actually delivered");
        CommandResult readLastBolusResult = runCommand(null , 2,
                () -> ruffyScripter.readHistory(new PumpHistoryRequest().bolusHistory(PumpHistoryRequest.LAST)));
        if (!readLastBolusResult.success || readLastBolusResult.history == null) {
            // this happens when the cartridge runs empty during delivery, the pump will be in an error
            // state with multiple alarms ringing and no chance of reading history
            return new PumpEnactResult().success(false).enacted(false)
                    .comment(MainApp.sResources.getString(R.string.combo_error_bolus_verification_failed));
        }

        List<Bolus> bolusHistory = readLastBolusResult.history.bolusHistory;
        Bolus lastBolus = !bolusHistory.isEmpty() ? bolusHistory.get(0) : null;
        log.debug("Last bolus read from history: " + lastBolus + ", request time: " +
                pumpTimeWhenBolusWasRequested + " (" + new Date(pumpTimeWhenBolusWasRequested) + ")");

        if (lastBolus == null // no bolus ever given
                || lastBolus.timestamp < pumpTimeWhenBolusWasRequested // this is not the bolus you're looking for
                || !lastBolus.isValid) { // ext/multiwave bolus
            log.debug("No bolus was delivered");
            return new PumpEnactResult().success(false).enacted(false)
                    .comment(MainApp.sResources.getString(R.string.combo_error_no_bolus_delivered));
        } else if (Math.abs(lastBolus.amount - detailedBolusInfo.insulin) > 0.01) { // bolus only partially delivered
            double requestedBolus = detailedBolusInfo.insulin;

            detailedBolusInfo.insulin = lastBolus.amount;
            detailedBolusInfo.source = Source.USER;
            MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
            log.debug(String.format(Locale.getDefault(), "Added partial bolus of %.2f to treatments (requested: %.2f", lastBolus.amount, requestedBolus));

            return new PumpEnactResult().success(false).enacted(true)
                    .comment(MainApp.sResources.getString(R.string.combo_error_partial_bolus_delivered,
                            lastBolus.amount, requestedBolus));
        }

        // bolus was correctly and fully delivered
        detailedBolusInfo.insulin = lastBolus.amount;
        detailedBolusInfo.source = Source.USER;
        MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
        log.debug("Added correctly delivered bolus to treatments");
        return new PumpEnactResult().success(true).enacted(true)
                .bolusDelivered(lastBolus.amount)
                .carbsDelivered(detailedBolusInfo.carbs);
    }

    @Override
    public void stopBolusDelivering() {
        if (bolusInProgress) {
            ruffyScripter.cancelBolus();
        }
        cancelBolus = true;
    }

    // Note: AAPS calls this solely to enact OpenAPS suggestions
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

    /**
     * Note: AAPS calls this directly only for setting a temp basal issued by the user
     *
     * @param forceNew Driver always applies the requested TBR and simply overrides whatever TBR
     *                 is or isn't running at the moment
     */
    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, final Integer durationInMinutes, boolean forceNew) {
        return setTempBasalPercent(percent, durationInMinutes);
    }

    private PumpEnactResult setTempBasalPercent(Integer percent, final Integer durationInMinutes) {
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

        // do a soft TBR-cancel when requested rate was rounded to 100% (>94% && <104%)
        if (adjustedPercent == 100) {
            return cancelTempBasal(false);
        }

        int finalAdjustedPercent = adjustedPercent;
        CommandResult commandResult = runCommand(MainApp.sResources.getString(R.string.combo_pump_action_setting_tbr, percent, durationInMinutes),
                3, () -> ruffyScripter.setTbr(finalAdjustedPercent, durationInMinutes));
        if (!commandResult.success) {
            return new PumpEnactResult().success(false).enacted(false);
        }

        PumpState state = commandResult.state;
        if (state.tbrActive && state.tbrPercent == adjustedPercent
                && (state.tbrRemainingDuration == durationInMinutes || state.tbrRemainingDuration == durationInMinutes - 1)) {
            TemporaryBasal tempStart = new TemporaryBasal();
            tempStart.date = state.timestamp;
            tempStart.durationInMinutes = state.tbrRemainingDuration;
            tempStart.percentRate = state.tbrPercent;
            tempStart.isAbsolute = false;
            tempStart.source = Source.USER;
            MainApp.getConfigBuilder().addToHistoryTempBasal(tempStart);

            MainApp.bus().post(new EventComboPumpUpdateGUI());
        }

        return new PumpEnactResult().success(true).enacted(true).isPercent(true)
                .percent(state.tbrPercent).duration(state.tbrRemainingDuration);
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        return OPERATION_NOT_SUPPORTED;
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean userRequested) {
        log.debug("cancelTempBasal called");
        final TemporaryBasal activeTemp = MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis());
        if (userRequested) {
            log.debug("cancelTempBasal: hard-cancelling TBR since user requested");
            CommandResult commandResult = runCommand(MainApp.sResources.getString(R.string.combo_pump_action_cancelling_tbr), 2, ruffyScripter::cancelTbr);
            if (!commandResult.state.tbrActive) {
                TemporaryBasal tempBasal = new TemporaryBasal();
                tempBasal.date = commandResult.state.timestamp;
                tempBasal.durationInMinutes = 0;
                tempBasal.source = Source.USER;
                MainApp.getConfigBuilder().addToHistoryTempBasal(tempBasal);
                return new PumpEnactResult().isTempCancel(true).success(true).enacted(true);
            } else {
                return new PumpEnactResult().success(false).enacted(false);
            }
        } else if (activeTemp == null) {
            return new PumpEnactResult().success(true).enacted(false);
        } else if ((activeTemp.percentRate >= 90 && activeTemp.percentRate <= 110) && activeTemp.getPlannedRemainingMinutes() <= 15) {
            // Let fake neutral temp keep run (see below)
            // Note that a connection to the pump is still opened, since the queue issues a getPumpStatus() call whenever an empty
            // queue receives a new command. Probably not worth optimizing.
            log.debug("cancelTempBasal: skipping changing tbr since it already is at " + activeTemp.percentRate + "% and running for another " + activeTemp.getPlannedRemainingMinutes() + " mins.");
            return new PumpEnactResult().success(true).enacted(true)
                    .comment("cancelTempBasal skipping changing tbr since it already is at "
                            + activeTemp.percentRate + "% and running for another "
                            + activeTemp.getPlannedRemainingMinutes() + " mins.");
        } else {
            // Set a fake neutral temp to avoid TBR cancel alert. Decide 90% vs 110% based on
            // on whether the TBR we're cancelling is above or below 100%.
            final int percentage = (activeTemp.percentRate > 100) ? 110 : 90;
            log.debug("cancelTempBasal: changing TBR to " + percentage + "% for 15 mins.");
            return setTempBasalPercent(percentage, 15);
        }
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

            if (!ruffyScripter.isConnected()) {
                CommandResult preCheckError = runOnConnectChecks();
                if (preCheckError != null) {
                    updateLocalData(preCheckError);
                    return preCheckError;
                }
            }

            commandResult = commandExecution.execute();

            if (!commandResult.success && retries > 0) {
                for (int retryAttempts = 1; !commandResult.success && retryAttempts <= retries; retryAttempts++) {
                    log.debug("Command was not successful, retries requested, doing retry #" + retryAttempts);
                    commandResult = commandExecution.execute();
                }
            }

            for (Integer forwardedWarning : commandResult.forwardedWarnings) {
                notifyAboutPumpWarning(new WarningOrErrorCode(forwardedWarning, null, null));
            }

            if (commandResult.success) {
                pump.lastSuccessfulCmdTime = System.currentTimeMillis();
            }

            if (commandResult.success) {
                updateLocalData(commandResult);
            }
        } finally {
            if (activity != null) {
                pump.activity = null;
                MainApp.bus().post(new EventComboPumpUpdateGUI());
            }
        }

        checkForUnsafeUsage(commandResult);

        return commandResult;
    }

    /**
     * Returns the command result of running ReadPumpState if it wasn't successful, indicating
     * an error condition. Returns null otherwise.
     */
    private CommandResult runOnConnectChecks() {
        // connect, get status and check if an alarm is active
        CommandResult preCheckResult = ruffyScripter.readPumpState();
        if (!preCheckResult.success) {
            return preCheckResult;
        }

        WarningOrErrorCode activeAlert = preCheckResult.state.activeAlert;
        // note if multiple alerts are active this will and should fail; e.g. if pump was stopped
        // due to empty cartridge alert, which might also trigger TBR cancelled alert
        if (activeAlert != null) {
            if (activeAlert.warningCode != null
                    && (activeAlert.warningCode == PumpWarningCodes.CARTRIDGE_LOW ||
                    activeAlert.warningCode == PumpWarningCodes.BATTERY_LOW ||
                    activeAlert.warningCode == PumpWarningCodes.TBR_CANCELLED)) {
                // turn benign warnings into notifications
                notifyAboutPumpWarning(activeAlert);
                ruffyScripter.confirmAlert(activeAlert.warningCode);
            } else if (activeAlert.errorCode != null){
                Notification notification = new Notification();
                notification.date = new Date();
                notification.id = Notification.COMBO_PUMP_ALARM;
                notification.level = Notification.URGENT;
                notification.text = MainApp.sResources.getString(R.string.combo_is_in_error_state);
                MainApp.bus().post(new EventNewNotification(notification));
                return preCheckResult.success(false);
            }
        }

        checkAndResolveTbrMismatch(preCheckResult.state);
        checkPumpTime(preCheckResult.state);

        return null;
    }

    /** Check pump time (on the main menu) and raise notification if time is off.
     * (setting clock is not supported by ruffy) */
    private void checkPumpTime(PumpState state) {
        if (state.pumpTime == 0) {
            // time couldn't be read (e.g. a warning is displayed on the menu , hiding the time field)
        } else if (Math.abs(state.pumpTime - System.currentTimeMillis()) >= 10 * 60 * 1000) {
            Notification notification = new Notification(Notification.COMBO_PUMP_ALARM, MainApp.sResources.getString(R.string.combo_notification_check_time_date), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
        } else if (Math.abs(state.pumpTime - System.currentTimeMillis()) >= 3 * 60 * 1000) {
            Notification notification = new Notification(Notification.COMBO_PUMP_ALARM, MainApp.sResources.getString(R.string.combo_notification_check_time_date), Notification.NORMAL);
            MainApp.bus().post(new EventNewNotification(notification));
        }
    }

    private void notifyAboutPumpWarning(WarningOrErrorCode activeAlert) {
        if (activeAlert.warningCode == null ||
                (!activeAlert.warningCode.equals(PumpWarningCodes.CARTRIDGE_LOW)
                        && !activeAlert.warningCode.equals(PumpWarningCodes.BATTERY_LOW)
                        && !activeAlert.warningCode.equals(PumpWarningCodes.TBR_CANCELLED))) {
            throw new IllegalArgumentException(activeAlert.toString());
        }
        Notification notification = new Notification();
        notification.date = new Date();
        notification.id = Notification.COMBO_PUMP_ALARM;
        notification.level = Notification.NORMAL;
        if (activeAlert.warningCode == PumpWarningCodes.CARTRIDGE_LOW) {
            notification.text = MainApp.sResources.getString(R.string.combo_pump_cartridge_low_warrning);
        } else if (activeAlert.warningCode == PumpWarningCodes.BATTERY_LOW) {
            notification.text = MainApp.sResources.getString(R.string.combo_pump_battery_low_warrning);
        } else if (activeAlert.warningCode == PumpWarningCodes.TBR_CANCELLED) {
            notification.text = MainApp.sResources.getString(R.string.combo_pump_tbr_cancelled_warrning);
        }
        MainApp.bus().post(new EventNewNotification(notification));
    }

    private void checkForUnsafeUsage(CommandResult commandResult) {
        if (commandResult == null) return;

        long lastViolation = 0;
        if (commandResult.state.unsafeUsageDetected) {
            lastViolation = System.currentTimeMillis();
        } else if (commandResult.lastBolus != null && !commandResult.lastBolus.isValid) {
            lastViolation = commandResult.lastBolus.timestamp;
        } else if (commandResult.history != null) {
            for (Bolus bolus : commandResult.history.bolusHistory) {
                if (!bolus.isValid && bolus.timestamp > lastViolation) {
                    lastViolation = bolus.timestamp;
                }
            }
        }
        if (lastViolation > 0) {
            lowSuspendOnlyLoopEnforcetTill = lastViolation + 6 * 60 * 60 * 1000;
            if (lowSuspendOnlyLoopEnforcetTill > System.currentTimeMillis() && violationWarningRaisedFor != lowSuspendOnlyLoopEnforcetTill) {
                Notification n = new Notification(Notification.COMBO_PUMP_ALARM,
                        MainApp.sResources.getString(R.string.combo_force_disabled_notification),
                        Notification.URGENT);
                n.soundId = R.raw.alarm;
                MainApp.bus().post(new EventNewNotification(n));
                violationWarningRaisedFor = lowSuspendOnlyLoopEnforcetTill;
            }
        }
    }

    /**
     * Checks the main screen to determine if TBR on pump matches app state.
     */
    private void checkAndResolveTbrMismatch(PumpState state) {
        // compare with: info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgStatusTempBasal.updateTempBasalInDB()
        long now = System.currentTimeMillis();
        TemporaryBasal aapsTbr = MainApp.getConfigBuilder().getTempBasalFromHistory(now);
        if (aapsTbr == null && state.tbrActive && state.tbrRemainingDuration > 2) {
            log.debug("Creating temp basal from pump TBR");
            TemporaryBasal newTempBasal = new TemporaryBasal();
            newTempBasal.date = now;
            newTempBasal.percentRate = state.tbrPercent;
            newTempBasal.isAbsolute = false;
            newTempBasal.durationInMinutes = state.tbrRemainingDuration;
            newTempBasal.source = Source.USER;
            MainApp.getConfigBuilder().addToHistoryTempBasal(newTempBasal);
        } else if (aapsTbr != null && aapsTbr.getPlannedRemainingMinutes() > 2 && !state.tbrActive) {
            log.debug("Ending AAPS-TBR since pump has no TBR active");
            TemporaryBasal tempStop = new TemporaryBasal();
            tempStop.date = now;
            tempStop.durationInMinutes = 0;
            tempStop.source = Source.USER;
            MainApp.getConfigBuilder().addToHistoryTempBasal(tempStop);
        } else if (aapsTbr != null && state.tbrActive
                && (aapsTbr.percentRate != state.tbrPercent ||
                Math.abs(aapsTbr.getPlannedRemainingMinutes() - state.tbrRemainingDuration) > 2)) {
            log.debug("AAPSs and pump-TBR differ; ending AAPS-TBR and creating new TBR based on pump TBR");
            TemporaryBasal tempStop = new TemporaryBasal();
            tempStop.date = now - 1000;
            tempStop.durationInMinutes = 0;
            tempStop.source = Source.USER;
            MainApp.getConfigBuilder().addToHistoryTempBasal(tempStop);

            TemporaryBasal newTempBasal = new TemporaryBasal();
            newTempBasal.date = now;
            newTempBasal.percentRate = state.tbrPercent;
            newTempBasal.isAbsolute = false;
            newTempBasal.durationInMinutes = state.tbrRemainingDuration;
            newTempBasal.source = Source.USER;
            MainApp.getConfigBuilder().addToHistoryTempBasal(newTempBasal);
        }
    }

    /**
     * Reads the pump's history and updates the DB accordingly.
     * <p>
     * Only ever called by #readAllPumpData which is triggered by the user via the combo fragment
     * which warns the user against doing this.
     */
    private boolean readHistory(final PumpHistoryRequest request) {
        CommandResult historyResult = runCommand(MainApp.sResources.getString(R.string.combo_activity_reading_pump_history), 3, () -> ruffyScripter.readHistory(request));
        if (!historyResult.success) {
            return false;
        }

        PumpHistory history = historyResult.history;
        updateDbFromPumpHistory(history);

        // update local cache
        if (!history.pumpAlertHistory.isEmpty()) {
            pump.errorHistory = history.pumpAlertHistory;
        }
        if (!history.tddHistory.isEmpty()) {
            pump.tddHistory = history.tddHistory;
        }

        return historyResult.success;
    }

    private synchronized void updateDbFromPumpHistory(@NonNull PumpHistory history) {
        DatabaseHelper dbHelper = MainApp.getDbHelper();
        // boluses
        for (Bolus pumpBolus : history.bolusHistory) {
            Treatment aapsBolus = dbHelper.getTreatmentByDate(pumpBolus.timestamp);
            if (aapsBolus == null) {
                log.debug("Creating bolus record from pump bolus: " + pumpBolus);
                DetailedBolusInfo dbi = new DetailedBolusInfo();
                dbi.date = pumpBolus.timestamp;
                dbi.pumpId = pumpBolus.timestamp;
                dbi.source = Source.PUMP;
                dbi.insulin = pumpBolus.amount;
                dbi.eventType = CareportalEvent.CORRECTIONBOLUS;
                MainApp.getConfigBuilder().addToHistoryTreatment(dbi);
            }
        }

        // TBRs
        for (Tbr pumpTbr : history.tbrHistory) {
            TemporaryBasal aapsTbr = dbHelper.getTemporaryBasalsDataByDate(pumpTbr.timestamp);
            if (aapsTbr == null) {
                log.debug("Creating TBR from pump TBR: " + pumpTbr);
                TemporaryBasal temporaryBasal = new TemporaryBasal();
                temporaryBasal.date = pumpTbr.timestamp;
                temporaryBasal.pumpId = pumpTbr.timestamp;
                temporaryBasal.source = Source.PUMP;
                temporaryBasal.percentRate = pumpTbr.percent;
                temporaryBasal.durationInMinutes = pumpTbr.duration;
                temporaryBasal.isAbsolute = false;
                MainApp.getConfigBuilder().addToHistoryTempBasal(temporaryBasal);
            }
        }
    }

    // TODO queue
    void readTddData() {
        readHistory(new PumpHistoryRequest().tddHistory(PumpHistoryRequest.FULL));
        ruffyScripter.disconnect();
    }

    // TODO queue
    void readAlertData() {
        readHistory(new PumpHistoryRequest().pumpErrorHistory(PumpHistoryRequest.FULL));
        ruffyScripter.disconnect();
    }

    // TODO queue
    void readAllPumpData() {
        long lastCheckInitiated = System.currentTimeMillis();

        boolean readHistorySuccess = readHistory(new PumpHistoryRequest()
                .bolusHistory(pumpHistoryLastChecked)
                .tbrHistory(pumpHistoryLastChecked)
                .pumpErrorHistory(PumpHistoryRequest.FULL)
                .tddHistory(PumpHistoryRequest.FULL));
        if (!readHistorySuccess) {
            return;
        }

        pumpHistoryLastChecked = lastCheckInitiated;

/* not displayed in the UI anymore due to pump bug
        CommandResult reservoirResult = runCommand("Checking reservoir level", 2,
                ruffyScripter::readReservoirLevelAndLastBolus);
        if (!reservoirResult.success) {
            return;
        }
*/

        CommandResult basalResult = runCommand("Reading basal profile", 2, ruffyScripter::readBasalProfile);
        if (!basalResult.success) {
            return;
        }

        pump.basalProfile = basalResult.basalProfile;

        ruffyScripter.disconnect();
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
            pumpJson.put("clock", DateUtil.toISOString(pump.lastSuccessfulCmdTime));

            int level = 150;
            if (pump.state.insulinState == PumpState.LOW) level = 8;
            else if (pump.state.insulinState == PumpState.EMPTY) level = 0;
            pumpJson.put("reservoir", level);

            JSONObject statusJson = new JSONObject();
            statusJson.put("status", getStateSummary());
            statusJson.put("timestamp", pump.lastSuccessfulCmdTime);
            pumpJson.put("status", statusJson);

            JSONObject extendedJson = new JSONObject();
            extendedJson.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            extendedJson.put("ActiveProfile", MainApp.getConfigBuilder().getProfileName());
            PumpState ps = pump.state;
            if (ps.tbrActive) {
                extendedJson.put("TempBasalAbsoluteRate", ps.tbrRate);
                extendedJson.put("TempBasalPercent", ps.tbrPercent);
                extendedJson.put("TempBasalRemaining", ps.tbrRemainingDuration);
            }
            if (ps.activeAlert != null && ps.activeAlert.errorCode != null) {
                extendedJson.put("ErrorCode", ps.activeAlert.errorCode);
            }
            pumpJson.put("extended", extendedJson);

            JSONObject batteryJson = new JSONObject();
            int battery = 100;
            if (ps.batteryState == PumpState.LOW) battery = 25;
            else if (ps.batteryState == PumpState.EMPTY) battery = 0;
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
    private long lowSuspendOnlyLoopEnforcetTill = 0;
    private long violationWarningRaisedFor = 0;

    @Override
    public boolean isLoopEnabled() {
        return true;
    }

    @Override
    public boolean isClosedModeEnabled() {
        return true;
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
        return lowSuspendOnlyLoopEnforcetTill < System.currentTimeMillis() ? maxIob : 0;
    }
}
