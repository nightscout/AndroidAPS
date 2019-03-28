package info.nightscout.androidaps.plugins.pump.combo;

import android.content.DialogInterface;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TDD;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderFragment;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.combo.events.EventComboPumpUpdateGUI;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.BasalProfile;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.BolusProgressReporter;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.CommandResult;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.PumpState;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.PumpWarningCodes;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.RuffyCommands;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.RuffyScripter;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.WarningOrErrorCode;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.Bolus;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.PumpHistory;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.PumpHistoryRequest;
import info.nightscout.androidaps.plugins.pump.combo.ruffyscripter.history.Tdd;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;
/**
 * Created by mike on 05.08.2016.
 */
public class ComboPlugin extends PluginBase implements PumpInterface, ConstraintsInterface {
    private static final Logger log = LoggerFactory.getLogger(L.PUMP);
    static final String COMBO_TBRS_SET = "combo_tbrs_set";
    static final String COMBO_BOLUSES_DELIVERED = "combo_boluses_delivered";

    private static ComboPlugin plugin = null;

    public static ComboPlugin getPlugin() {
        if (plugin == null)
            plugin = new ComboPlugin();
        return plugin;
    }

    private final static PumpDescription pumpDescription = new PumpDescription();

    @NonNull
    private final RuffyCommands ruffyScripter;

    @NonNull
    private static final ComboPump pump = new ComboPump();

    /**
     * This is used to determine when to pass a bolus cancel request to the scripter
     */
    private volatile boolean scripterIsBolusing;
    /**
     * This is set to true to request a bolus cancellation. {@link #deliverBolus(DetailedBolusInfo)}
     * will reset this flag.
     */
    private volatile boolean cancelBolus;

    /**
     * This is set (in {@link #checkHistory()} whenever a connection to the pump is made and
     * indicates if new history records on the pump have been found. This effectively blocks
     * high temps ({@link #setTempBasalPercent(Integer, Integer)} and boluses
     * ({@link #deliverBolus(DetailedBolusInfo)} till the queue is empty and the connection
     * is shut down.
     * {@link #initializePump()} resets this since on startup the history is allowed to have
     * changed (and the user can't possible have already calculated anything with out of date IOB).
     * The next reconnect will then reset this flag. This might cause some grief when attempting
     * to bolus again within the 5s of idling it takes before the connecting is shut down. Or if
     * the queue is very large, giving the user more time to input boluses. I don't have a good
     * solution for this at the moment, but this is enough of an edge case - faulting in the right
     * direction - so that adding more complexity yields little benefit.
     */
    private volatile boolean pumpHistoryChanged = false;

    /**
     * Cache of the last <=2 boluses on the pump. Used to detect changes in pump history,
     * requiring reading more pump history. This is read/set in {@link #checkHistory()} when changed
     * pump history was detected and was read, as well as in {@link #deliverBolus(DetailedBolusInfo)}
     * after bolus delivery. Newest record is the first one.
     */
    private volatile List<Bolus> recentBoluses = new ArrayList<>(0);

    private static final PumpEnactResult OPERATION_NOT_SUPPORTED = new PumpEnactResult()
            .success(false).enacted(false).comment(MainApp.gs(R.string.combo_pump_unsupported_operation));

    private ComboPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.PUMP)
                .fragmentClass(ComboFragment.class.getName())
                .pluginName(R.string.combopump)
                .shortName(R.string.combopump_shortname)
                .description(R.string.description_pump_combo)
        );
        ruffyScripter = new RuffyScripter(MainApp.instance().getApplicationContext());
        pumpDescription.setPumpDescription(PumpType.AccuChekCombo);
    }

    public ComboPump getPump() {
        return pump;
    }

    String getStateSummary() {
        PumpState ps = pump.state;
        if (ps.activeAlert != null) {
            return ps.activeAlert.errorCode != null
                    ? "E" + ps.activeAlert.errorCode + ": " + ps.activeAlert.message
                    : "W" + ps.activeAlert.warningCode + ": " + ps.activeAlert.message;
        } else if (ps.suspended && (ps.batteryState == PumpState.EMPTY || ps.insulinState == PumpState.EMPTY)) {
            return MainApp.gs(R.string.combo_pump_state_suspended_due_to_error);
        } else if (ps.suspended) {
            return MainApp.gs(R.string.combo_pump_state_suspended_by_user);
        } else if (!pump.initialized) {
            return MainApp.gs(R.string.combo_pump_state_initializing);
        } else if (!validBasalRateProfileSelectedOnPump) {
            return MainApp.gs(R.string.loopdisabled);
        }
        return MainApp.gs(R.string.combo_pump_state_running);
    }

    @Override
    public void switchAllowed(ConfigBuilderFragment.PluginViewHolder.PluginSwitcher pluginSwitcher, FragmentActivity context) {
        boolean allowHardwarePump = SP.getBoolean("allow_hardware_pump", false);
        if (allowHardwarePump || context == null) {
            pluginSwitcher.invoke();
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(R.string.allow_hardware_pump_text)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            pluginSwitcher.invoke();
                            SP.putBoolean("allow_hardware_pump", true);
                            if (L.isEnabled(L.PUMP))
                                log.debug("First time HW pump allowed!");
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            pluginSwitcher.cancel();
                            if (L.isEnabled(L.PUMP))
                                log.debug("User does not allow switching to HW pump!");
                        }
                    });
            builder.create().show();
        }
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
    public boolean isHandshakeInProgress() {
        return false;
    }

    @Override
    public void finishHandshaking() {
    }

    @Override
    public void connect(String reason) {
        // ruffyscripter establishes a connection as needed.
        // ComboPlugin.runCommand performs on connect checks if needed, thus needs info on
        // whether a connection is there.
        // More importantly, RuffyScripter needs control over connection to be able to recover
        // from a failure and deal with alarms on pump.
        // Yes, this could also be done by keeping a flag 'inCmd' in RuffyScripter, which kicks
        // off recovery unless set to false again after command completion and have connect
        // checks be called in ComboPlugin.connect(); ... and have that cause other issues
    }

    @Override
    public void disconnect(String reason) {
        if (L.isEnabled(L.PUMP))
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
            Notification notification = new Notification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED, MainApp.gs(R.string.pumpNotInitializedProfileNotSet), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
            return new PumpEnactResult().success(false).enacted(false).comment(MainApp.gs(R.string.pumpNotInitializedProfileNotSet));
        }

        BasalProfile requestedBasalProfile = convertProfileToComboProfile(profile);
        if (pump.basalProfile.equals(requestedBasalProfile)) {
            //dismiss previously "FAILED" overview notifications
            MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
            MainApp.bus().post(new EventDismissNotification(Notification.FAILED_UDPATE_PROFILE));
            return new PumpEnactResult().success(true).enacted(false);
        }

        CommandResult stateResult = runCommand(null, 1, ruffyScripter::readPumpState);
        if (stateResult.state.unsafeUsageDetected == PumpState.UNSUPPORTED_BASAL_RATE_PROFILE) {
            return new PumpEnactResult().success(false).enacted(false).comment(MainApp.gs(R.string.combo_force_disabled_notification));
        }

        CommandResult setResult = runCommand(MainApp.gs(R.string.combo_activity_setting_basal_profile), 2,
                () -> ruffyScripter.setBasalProfile(requestedBasalProfile));
        if (!setResult.success) {
            Notification notification = new Notification(Notification.FAILED_UDPATE_PROFILE, MainApp.gs(R.string.failedupdatebasalprofile), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
            return new PumpEnactResult().success(false).enacted(false).comment(MainApp.gs(R.string.failedupdatebasalprofile));
        }

        pump.basalProfile = requestedBasalProfile;

        //dismiss previously "FAILED" overview notifications
        MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
        MainApp.bus().post(new EventDismissNotification(Notification.FAILED_UDPATE_PROFILE));
        //issue success notification
        Notification notification = new Notification(Notification.PROFILE_SET_OK, MainApp.gs(R.string.profile_set_ok), Notification.INFO, 60);
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
            double rate = profile.getBasalTimeFromMidnight(i * 60 * 60);

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

    @Override
    public long lastDataTime() {
        return pump.lastSuccessfulCmdTime;
    }

    /**
     * Runs pump initialization if needed and reads the pump state from the main screen.
     */
    @Override
    public synchronized void getPumpStatus() {
        if (L.isEnabled(L.PUMP))
            log.debug("getPumpStatus called");
        if (!pump.initialized) {
            initializePump();
        } else {
            // trigger a connect, which will update state and check history
            runCommand(null, 3, ruffyScripter::readPumpState);
        }
    }

    private synchronized void initializePump() {
        long maxWait = System.currentTimeMillis() + 15 * 1000;
        while (!ruffyScripter.isPumpAvailable()) {
            if (L.isEnabled(L.PUMP))
                log.debug("Waiting for ruffy service to come up ...");
            SystemClock.sleep(100);
            if (System.currentTimeMillis() > maxWait) {
                if (L.isEnabled(L.PUMP))
                    log.debug("ruffy service unavailable, wtf");
                return;
            }
        }

        // trigger a connect, which will update state and check history
        CommandResult stateResult = runCommand(null, 1, ruffyScripter::readPumpState);
        if (stateResult.invalidSetup) {
            MainApp.bus().post(new EventNewNotification(
                    new Notification(Notification.COMBO_PUMP_ALARM, MainApp.gs(R.string.combo_invalid_setup), Notification.URGENT)));
            return;
        }
        if (!stateResult.success) {
            return;
        }

        // note that since the history is checked upon every connect, the above already updated
        // the DB with any changed history records
        if (pumpHistoryChanged) {
            if (L.isEnabled(L.PUMP))
                log.debug("Pump history has changed and was imported");
            pumpHistoryChanged = false;
        }

        if (stateResult.state.unsafeUsageDetected == PumpState.UNSUPPORTED_BASAL_RATE_PROFILE) {
            Notification n = new Notification(Notification.COMBO_PUMP_ALARM,
                    MainApp.gs(R.string.combo_force_disabled_notification),
                    Notification.URGENT);
            n.soundId = R.raw.alarm;
            MainApp.bus().post(new EventNewNotification(n));
            return;
        }

        // read basal profile into cache (KeepAlive will trigger a profile update if needed)
        CommandResult readBasalResult = runCommand(MainApp.gs(R.string.combo_actvity_reading_basal_profile), 2, ruffyScripter::readBasalProfile);
        if (!readBasalResult.success) {
            return;
        }
        pump.basalProfile = readBasalResult.basalProfile;
        setValidBasalRateProfileSelectedOnPump(true);

        pump.initialized = true;
        MainApp.bus().post(new EventInitializationChanged());

        // show notification to check pump date if last bolus is older than 24 hours
        // or is in the future
        if (!recentBoluses.isEmpty()) {
            long lastBolusTimestamp = recentBoluses.get(0).timestamp;
            long now = System.currentTimeMillis();
            if (lastBolusTimestamp < now - 24 * 60 * 60 * 1000 || lastBolusTimestamp > now + 5 * 60 * 1000) {
                Notification notification = new Notification(Notification.COMBO_PUMP_ALARM, MainApp.gs(R.string.combo_check_date), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(notification));
            }
        }

        // ComboFragment updates state fully only after the pump has initialized,
        // so force an update after initialization completed
        MainApp.bus().post(new EventComboPumpUpdateGUI());
    }

    /**
     * Updates local cache with state (reservoir level, last bolus ...) returned from the pump
     */
    private void updateLocalData(CommandResult result) {
        if (result.reservoirLevel != PumpState.UNKNOWN) {
            pump.reservoirLevel = result.reservoirLevel;
        }
        if (result.history != null && !result.history.bolusHistory.isEmpty()) {
            pump.lastBolus = result.history.bolusHistory.get(0);
        }
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

    @Override
    public double getReservoirLevel() {
        return pump.reservoirLevel;
    }

    @Override
    public int getBatteryLevel() {
        switch (pump.state.batteryState) {
            case PumpState.EMPTY:
                return 5;
            case PumpState.LOW:
                return 25;
            default:
                return 100;
        }
    }

    private static BolusProgressReporter bolusProgressReporter = (state, percent, delivered) -> {
        EventOverviewBolusProgress event = EventOverviewBolusProgress.getInstance();
        switch (state) {
            case PROGRAMMING:
                event.status = MainApp.gs(R.string.combo_programming_bolus);
                break;
            case DELIVERING:
                event.status = MainApp.gs(R.string.bolusdelivering, delivered);
                break;
            case DELIVERED:
                event.status = MainApp.gs(R.string.bolusdelivered, delivered);
                break;
            case STOPPING:
                event.status = MainApp.gs(R.string.bolusstopping);
                break;
            case STOPPED:
                event.status = MainApp.gs(R.string.bolusstopped);
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
        try {
            if (detailedBolusInfo.insulin == 0 && detailedBolusInfo.carbs == 0) {
                // neither carbs nor bolus requested
                log.error("deliverTreatment: Invalid input");
                return new PumpEnactResult().success(false).enacted(false)
                        .bolusDelivered(0d).carbsDelivered(0d)
                        .comment(MainApp.gs(R.string.danar_invalidinput));
            } else if (detailedBolusInfo.insulin > 0) {
                // bolus needed, ask pump to deliver it
                return deliverBolus(detailedBolusInfo);
            } else {
                // no bolus required, carb only treatment
                TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, false);

                EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
                bolusingEvent.t = new Treatment();
                bolusingEvent.t.isSMB = detailedBolusInfo.isSMB;
                bolusingEvent.percent = 100;
                MainApp.bus().post(bolusingEvent);

                return new PumpEnactResult().success(true).enacted(true)
                        .bolusDelivered(0d).carbsDelivered(detailedBolusInfo.carbs)
                        .comment(MainApp.gs(R.string.virtualpump_resultok));
            }
        } finally {
            MainApp.bus().post(new EventComboPumpUpdateGUI());
        }
    }

    @NonNull
    private PumpEnactResult deliverBolus(final DetailedBolusInfo detailedBolusInfo) {
        try {
            pump.activity = MainApp.gs(R.string.combo_pump_action_bolusing, detailedBolusInfo.insulin);
            MainApp.bus().post(new EventComboPumpUpdateGUI());

            // check pump is ready and all pump bolus records are known
            CommandResult stateResult = runCommand(null, 2, () -> ruffyScripter.readQuickInfo(1));
            if (!stateResult.success) {
                return new PumpEnactResult().success(false).enacted(false)
                        .comment(MainApp.gs(R.string.combo_error_no_connection_no_bolus_delivered));
            }
            if (stateResult.reservoirLevel != -1 && stateResult.reservoirLevel - 0.5 < detailedBolusInfo.insulin) {
                return new PumpEnactResult().success(false).enacted(false)
                        .comment(MainApp.gs(R.string.combo_reservoir_level_insufficient_for_bolus));
            }
            // the commands above ensured a connection was made, which updated this field
            if (pumpHistoryChanged) {
                return new PumpEnactResult().success(false).enacted(false)
                        .comment(MainApp.gs(R.string.combo_bolus_rejected_due_to_pump_history_change));
            }

            Bolus previousBolus = stateResult.history != null && !stateResult.history.bolusHistory.isEmpty()
                    ? stateResult.history.bolusHistory.get(0)
                    : new Bolus(0, 0, false);

            // reject a bolus if one with the exact same size was successfully delivered
            // within the last 1-2 minutes
            if (Math.abs(previousBolus.amount - detailedBolusInfo.insulin) < 0.01
                    && previousBolus.timestamp + 60 * 1000 > System.currentTimeMillis()) {
                if (L.isEnabled(L.PUMP))
                    log.debug("Bolus request rejected, same bolus was successfully delivered very recently");
                return new PumpEnactResult().success(false).enacted(false)
                        .comment(MainApp.gs(R.string.bolus_frequency_exceeded));
            }

            // if the last bolus was given in the current minute, wait till the pump clock moves
            // to the next minute to ensure timestamps are unique and can be imported
            CommandResult timeCheckResult = stateResult;
            long waitStartTime = System.currentTimeMillis();
            long maxWaitTimeout = waitStartTime + 65 * 1000;
            int waitLoops = 0;
            while (previousBolus.timestamp == timeCheckResult.state.pumpTime
                    && maxWaitTimeout > System.currentTimeMillis()) {
                if (cancelBolus) {
                    return new PumpEnactResult().success(true).enacted(false);
                }
                if (!timeCheckResult.success) {
                    return new PumpEnactResult().success(false).enacted(false)
                            .comment(MainApp.gs(R.string.combo_error_no_connection_no_bolus_delivered));
                }
                if (L.isEnabled(L.PUMP))
                    log.debug("Waiting for pump clock to advance for the next unused bolus record timestamp");
                SystemClock.sleep(2000);
                timeCheckResult = runCommand(null, 0, ruffyScripter::readPumpState);
                waitLoops++;
            }
            if (waitLoops > 0) {
                long waitDuration = (System.currentTimeMillis() - waitStartTime) / 1000;
                if (L.isEnabled(L.PUMP))
                    log.debug("Waited " + waitDuration + "s for pump to switch to a fresh minute before bolusing");
            }

            if (cancelBolus) {
                return new PumpEnactResult().success(true).enacted(false);
            }

            Treatment treatment = new Treatment();
            treatment.isSMB = detailedBolusInfo.isSMB;
            EventOverviewBolusProgress.getInstance().t = treatment;

            // start bolus delivery
            scripterIsBolusing = true;
            runCommand(null, 0,
                    () -> ruffyScripter.deliverBolus(detailedBolusInfo.insulin, bolusProgressReporter));
            scripterIsBolusing = false;

            // Note that the result of the issued bolus command is not checked. If there was
            // a connection problem, ruffyscripter tried to recover and we can just check the
            // history below to see what was actually delivered

            // get last bolus from pump history for verification
            // (reads 2 records to update `recentBoluses` further down)
            CommandResult postBolusStateResult = runCommand(null, 3, () -> ruffyScripter.readQuickInfo(2));
            if (!postBolusStateResult.success) {
                return new PumpEnactResult().success(false).enacted(false)
                        .comment(MainApp.gs(R.string.combo_error_bolus_verification_failed));
            }
            Bolus lastPumpBolus = postBolusStateResult.history != null && !postBolusStateResult.history.bolusHistory.isEmpty()
                    ? postBolusStateResult.history.bolusHistory.get(0)
                    : null;

            // no bolus delivered?
            if (lastPumpBolus == null || lastPumpBolus.equals(previousBolus)) {
                if (cancelBolus) {
                    return new PumpEnactResult().success(true).enacted(false);
                } else {
                    return new PumpEnactResult()
                            .success(false)
                            .enacted(false)
                            .comment(MainApp.gs(R.string.combo_error_no_bolus_delivered));
                }
            }

            // at least some insulin delivered, so add it to treatments
            if (!addBolusToTreatments(detailedBolusInfo, lastPumpBolus))
                return new PumpEnactResult().success(false).enacted(true)
                        .comment(MainApp.gs(R.string.combo_error_updating_treatment_record));

            // check pump bolus record has a sane timestamp
            long now = System.currentTimeMillis();
            if (lastPumpBolus.timestamp < now - 10 * 60 * 1000 || lastPumpBolus.timestamp > now + 10 * 60 * 1000) {
                Notification notification = new Notification(Notification.COMBO_PUMP_ALARM, MainApp.gs(R.string.combo_suspious_bolus_time), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(notification));
            }

            // update `recentBoluses` so the bolus was just delivered won't be detected as a new
            // bolus that has been delivered on the pump
            recentBoluses = postBolusStateResult.history.bolusHistory;

            // only a partial bolus was delivered
            if (Math.abs(lastPumpBolus.amount - detailedBolusInfo.insulin) > 0.01) {
                if (cancelBolus) {
                    return new PumpEnactResult().success(true).enacted(true);
                }
                return new PumpEnactResult().success(false).enacted(true)
                        .comment(MainApp.gs(R.string.combo_error_partial_bolus_delivered,
                                lastPumpBolus.amount, detailedBolusInfo.insulin));
            }

            // full bolus was delivered successfully
            incrementBolusCount();
            return new PumpEnactResult()
                    .success(true)
                    .enacted(lastPumpBolus.amount > 0)
                    .bolusDelivered(lastPumpBolus.amount)
                    .carbsDelivered(detailedBolusInfo.carbs);
        } finally {
            pump.activity = null;
            MainApp.bus().post(new EventComboPumpUpdateGUI());
            MainApp.bus().post(new EventRefreshOverview("Bolus"));
            cancelBolus = false;
        }
    }

    private void incrementTbrCount() {
        try {
            SP.putLong(COMBO_TBRS_SET, SP.getLong(COMBO_TBRS_SET, 0L) + 1);
        } catch (Exception e) {
            // ignore
        }
    }

    private void incrementBolusCount() {
        try {
            SP.putLong(COMBO_BOLUSES_DELIVERED, SP.getLong(COMBO_BOLUSES_DELIVERED, 0L) + 1);
        } catch (Exception e) {
            // ignore
        }
    }

    /** Creates a treatment record based on the request in DetailBolusInfo and the delivered bolus. */
    private boolean addBolusToTreatments(DetailedBolusInfo detailedBolusInfo, Bolus lastPumpBolus) {
        DetailedBolusInfo dbi = detailedBolusInfo.copy();
        dbi.date = calculateFakeBolusDate(lastPumpBolus);
        dbi.pumpId = dbi.date;
        dbi.source = Source.PUMP;
        dbi.insulin = lastPumpBolus.amount;
        try {
            TreatmentsPlugin.getPlugin().addToHistoryTreatment(dbi, true);
        } catch (Exception e) {
            log.error("Adding treatment record failed", e);
            if (dbi.isSMB) {
                Notification notification = new Notification(Notification.COMBO_PUMP_ALARM, MainApp.gs(R.string.combo_error_updating_treatment_record), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(notification));
            }
            return false;
        }
        return true;
    }

    @Override
    public void stopBolusDelivering() {
        if (scripterIsBolusing) {
            ruffyScripter.cancelBolus();
        }
        cancelBolus = true;
    }

    /**
     * Note: AAPS calls this solely to enact OpenAPS suggestions
     *
     * @param force the force parameter isn't used currently since we always set the tbr -
     *              there might be room for optimization to first test the currently running tbr
     *              and only change it if it differs (as the DanaR plugin does). This approach
     *              might have other issues though (what happens if the tbr which wasn't re-set to
     *              the new value (and thus still has the old duration of e.g. 1 min) expires?)
     */
    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean force) {
        if (L.isEnabled(L.PUMP))
            log.debug("setTempBasalAbsolute called with a rate of " + absoluteRate + " for " + durationInMinutes + " min.");
        int unroundedPercentage = Double.valueOf(absoluteRate / getBaseBasalRate() * 100).intValue();
        int roundedPercentage = (int) (Math.round(absoluteRate / getBaseBasalRate() * 10) * 10);
        if (unroundedPercentage != roundedPercentage) {
            if (L.isEnabled(L.PUMP))
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
    public PumpEnactResult setTempBasalPercent(Integer percent, final Integer durationInMinutes, Profile profile, boolean forceNew) {
        return setTempBasalPercent(percent, durationInMinutes);
    }

    private PumpEnactResult setTempBasalPercent(Integer percent, final Integer durationInMinutes) {
        if (L.isEnabled(L.PUMP))
            log.debug("setTempBasalPercent called with " + percent + "% for " + durationInMinutes + "min");

        if (pumpHistoryChanged && percent > 110) {
            return new PumpEnactResult().success(false).enacted(false)
                    .comment(MainApp.gs(R.string.combo_high_temp_rejected_due_to_pump_history_changes));
        }

        int adjustedPercent = percent;

        if (adjustedPercent > pumpDescription.maxTempPercent) {
            if (L.isEnabled(L.PUMP))
                log.debug("Reducing requested TBR to the maximum support by the pump: " + percent + " -> " + pumpDescription.maxTempPercent);
            adjustedPercent = pumpDescription.maxTempPercent;
        }

        if (adjustedPercent % 10 != 0) {
            Long rounded = Math.round(adjustedPercent / 10d) * 10;
            if (L.isEnabled(L.PUMP))
                log.debug("Rounded requested percentage:" + adjustedPercent + " -> " + rounded);
            adjustedPercent = rounded.intValue();
        }

        // do a soft TBR-cancel when requested rate was rounded to 100% (>94% && <104%)
        if (adjustedPercent == 100) {
            return cancelTempBasal(false);
        }

        int finalAdjustedPercent = adjustedPercent;
        CommandResult commandResult = runCommand(MainApp.gs(R.string.combo_pump_action_setting_tbr, percent, durationInMinutes),
                3, () -> ruffyScripter.setTbr(finalAdjustedPercent, durationInMinutes));
        if (!commandResult.success) {
            return new PumpEnactResult().success(false).enacted(false);
        }

        PumpState state = commandResult.state;
        if (state.tbrActive && state.tbrPercent == adjustedPercent
                && (state.tbrRemainingDuration == durationInMinutes || state.tbrRemainingDuration == durationInMinutes - 1)) {
            TemporaryBasal tempStart = new TemporaryBasal()
                    .date(state.timestamp)
                    .duration(state.tbrRemainingDuration)
                    .percent(state.tbrPercent)
                    .source(Source.USER);
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStart);

            MainApp.bus().post(new EventComboPumpUpdateGUI());
        }

        incrementTbrCount();
        return new PumpEnactResult().success(true).enacted(true).isPercent(true)
                .percent(state.tbrPercent).duration(state.tbrRemainingDuration);
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        return OPERATION_NOT_SUPPORTED;
    }

    /**
     * Cancel an active Temp Basal. Mostly sets a fake Temp Basal to avoid a TBR CANCELLED
     * alert. This relies on TemporaryBasal objects to properly reflect the pumps state,
     * which is ensured by {@link #checkAndResolveTbrMismatch(PumpState)}, which runs on each
     * connect. When a hard cancel is requested, the pump is queried for it's TBR state to
     * make absolutely sure no TBR is running (such a request is also made when resuming the
     * loop, irregardless of whether a TBR is running or not).
     */
    @Override
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        if (L.isEnabled(L.PUMP))
            log.debug("cancelTempBasal called");
        final TemporaryBasal activeTemp = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(System.currentTimeMillis());
        if (enforceNew) {
            CommandResult stateResult = runCommand(MainApp.gs(R.string.combo_pump_action_refreshing), 2, ruffyScripter::readPumpState);
            if (!stateResult.success) {
                return new PumpEnactResult().success(false).enacted(false);
            }
            if (!stateResult.state.tbrActive) {
                return new PumpEnactResult().success(true).enacted(false);
            }
            if (L.isEnabled(L.PUMP))
                log.debug("cancelTempBasal: hard-cancelling TBR since force requested");
            CommandResult cancelResult = runCommand(MainApp.gs(R.string.combo_pump_action_cancelling_tbr), 2, ruffyScripter::cancelTbr);
            if (!cancelResult.success) {
                return new PumpEnactResult().success(false).enacted(false);
            }
            if (!cancelResult.state.tbrActive) {
                TemporaryBasal tempBasal = new TemporaryBasal()
                        .date(cancelResult.state.timestamp)
                        .duration(0)
                        .source(Source.USER);
                TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempBasal);
                return new PumpEnactResult().isTempCancel(true).success(true).enacted(true);
            } else {
                return new PumpEnactResult().success(false).enacted(false);
            }
        } else if (activeTemp == null) {
            return new PumpEnactResult().success(true).enacted(false);
        } else if ((activeTemp.percentRate >= 90 && activeTemp.percentRate <= 110) && activeTemp.getPlannedRemainingMinutes() <= 15) {
            // Let fake neutral temp keep run (see below)
            // Note that since this runs on the queue a connection is opened regardless, but this
            // case doesn't occur all that often, so it's not worth optimizing (1.3k SetTBR vs 4 cancelTBR).
            if (L.isEnabled(L.PUMP))
                log.debug("cancelTempBasal: skipping changing tbr since it already is at " + activeTemp.percentRate + "% and running for another " + activeTemp.getPlannedRemainingMinutes() + " mins.");
            return new PumpEnactResult().success(true).enacted(true)
                    .comment("cancelTempBasal skipping changing tbr since it already is at "
                            + activeTemp.percentRate + "% and running for another "
                            + activeTemp.getPlannedRemainingMinutes() + " mins.");
        } else {
            // Set a fake neutral temp to avoid TBR cancel alert. Decide 90% vs 110% based on
            // on whether the TBR we're cancelling is above or below 100%.
            final int percentage = (activeTemp.percentRate > 100) ? 110 : 90;
            if (L.isEnabled(L.PUMP))
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
     * Local cache (history, reservoir level, pump state) are updated via #updateLocalData()
     * if returned by a command.
     */
    private synchronized CommandResult runCommand(String activity, int retries, CommandExecution commandExecution) {
        CommandResult commandResult;
        try {
            if (!ruffyScripter.isConnected()) {
                String originalActivity = pump.activity;
                pump.activity = MainApp.gs(R.string.combo_activity_checking_pump_state);
                MainApp.bus().post(new EventComboPumpUpdateGUI());
                CommandResult preCheckError = runOnConnectChecks();
                pump.activity = originalActivity;
                if (preCheckError != null) {
                    updateLocalData(preCheckError);
                    return preCheckError;
                }
            }

            if (activity != null) {
                pump.activity = activity;
                MainApp.bus().post(new EventComboPumpUpdateGUI());
            }

            commandResult = commandExecution.execute();

            if (!commandResult.success && retries > 0) {
                for (int retryAttempts = 1; !commandResult.success && retryAttempts <= retries; retryAttempts++) {
                    if (L.isEnabled(L.PUMP))
                        log.debug("Command was not successful, retries requested, doing retry #" + retryAttempts);
                    commandResult = commandExecution.execute();
                }
            }

            for (Integer forwardedWarning : commandResult.forwardedWarnings) {
                notifyAboutPumpWarning(new WarningOrErrorCode(forwardedWarning, null, null));
            }

            if (commandResult.success) {
                pump.lastSuccessfulCmdTime = System.currentTimeMillis();
                if (validBasalRateProfileSelectedOnPump && commandResult.state.unsafeUsageDetected == PumpState.UNSUPPORTED_BASAL_RATE_PROFILE) {
                    setValidBasalRateProfileSelectedOnPump(false);
                    Notification n = new Notification(Notification.COMBO_PUMP_ALARM,
                            MainApp.gs(R.string.combo_force_disabled_notification),
                            Notification.URGENT);
                    n.soundId = R.raw.alarm;
                    MainApp.bus().post(new EventNewNotification(n));
                    ConfigBuilderPlugin.getPlugin().getCommandQueue().cancelTempBasal(true, null);
                }
                updateLocalData(commandResult);
            }
        } finally {
            if (activity != null) {
                pump.activity = null;
                MainApp.bus().post(new EventComboPumpUpdateGUI());
            }
        }

        return commandResult;
    }

    public void setValidBasalRateProfileSelectedOnPump(boolean value) {
        validBasalRateProfileSelectedOnPump = value;
    }

    /**
     * Returns the command result of running ReadPumpState if it wasn't successful, indicating
     * an error condition. Returns null otherwise.
     */
    private CommandResult runOnConnectChecks() {
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
            } else if (activeAlert.errorCode != null) {
                Notification notification = new Notification();
                notification.date = new Date();
                notification.id = Notification.COMBO_PUMP_ALARM;
                notification.level = Notification.URGENT;
                notification.text = MainApp.gs(R.string.combo_is_in_error_state, activeAlert.errorCode, activeAlert.message);
                MainApp.bus().post(new EventNewNotification(notification));
                return preCheckResult.success(false);
            }
        }

        if (!preCheckResult.state.suspended) {
            checkForUnsafeUsage(preCheckResult);
            checkAndResolveTbrMismatch(preCheckResult.state);
            checkPumpTime(preCheckResult.state);
            checkBasalRate(preCheckResult.state);
            CommandResult historyCheckError = checkHistory();
            if (historyCheckError != null) {
                return historyCheckError;
            }
        } else {
            long now = System.currentTimeMillis();
            TemporaryBasal aapsTbr = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(now);
            if (aapsTbr == null || aapsTbr.percentRate != 0) {
                if (L.isEnabled(L.PUMP))
                    log.debug("Creating 15m zero temp since pump is suspended");
                TemporaryBasal newTempBasal = new TemporaryBasal()
                        .date(now)
                        .percent(0)
                        .duration(15)
                        .source(Source.USER);
                TreatmentsPlugin.getPlugin().addToHistoryTempBasal(newTempBasal);
            }
        }

        return null;
    }


    private void checkBasalRate(PumpState state) {
        if (!pump.initialized) {
            // no cached profile to compare against
            return;
        }
        if (state.unsafeUsageDetected != PumpState.SAFE_USAGE) {
            // with an extended or multiwavo bolus running it's not (easily) possible
            // to infer base basal rate and not supported either. Also don't compare
            // if set basal rate profile is != -1.
            return;
        }
        if (state.tbrActive && state.tbrPercent == 0) {
            // can't infer base basal rate if TBR is 0
            return;
        }
        double pumpBasalRate = state.tbrActive
                ? Math.round(state.basalRate * 100 / state.tbrPercent * 100) / 100d
                : state.basalRate;
        int pumpHour = new Date(state.pumpTime).getHours();
        int phoneHour = new Date().getHours();
        if (pumpHour != phoneHour) {
            // only check if clocks are close
            return;
        }

        if (Math.abs(pumpBasalRate - getBaseBasalRate()) > 0.001) {
            CommandResult readBasalResult = runCommand(MainApp.gs(R.string.combo_actvity_reading_basal_profile), 2, ruffyScripter::readBasalProfile);
            if (readBasalResult.success) {
                pump.basalProfile = readBasalResult.basalProfile;
                Notification notification = new Notification(Notification.COMBO_PUMP_ALARM, MainApp.gs(R.string.combo_warning_pump_basal_rate_changed), Notification.NORMAL);
                MainApp.bus().post(new EventNewNotification(notification));
            } else {
                Notification notification = new Notification(Notification.COMBO_PUMP_ALARM, MainApp.gs(R.string.combo_error_failure_reading_changed_basal_rate), Notification.URGENT);
                MainApp.bus().post(new EventNewNotification(notification));
            }
        }
    }

    /**
     * Check pump time (on the main menu) and raise notification if time is off.
     * (setting clock is not supported by ruffy)
     */
    private void checkPumpTime(PumpState state) {
        if (state.pumpTime == 0) {
            // time couldn't be read (e.g. a warning is displayed on the menu , hiding the time field)
        } else if (Math.abs(state.pumpTime - System.currentTimeMillis()) >= 10 * 60 * 1000) {
            if (L.isEnabled(L.PUMP))
                log.debug("Pump clock needs update, pump time: " + state.pumpTime + " (" + new Date(state.pumpTime) + ")");
            Notification notification = new Notification(Notification.COMBO_PUMP_ALARM, MainApp.gs(R.string.combo_notification_check_time_date), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
        } else if (Math.abs(state.pumpTime - System.currentTimeMillis()) >= 3 * 60 * 1000) {
            if (L.isEnabled(L.PUMP))
                log.debug("Pump clock needs update, pump time: " + state.pumpTime + " (" + new Date(state.pumpTime) + ")");
            Notification notification = new Notification(Notification.COMBO_PUMP_ALARM, MainApp.gs(R.string.combo_notification_check_time_date), Notification.NORMAL);
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
            notification.text = MainApp.gs(R.string.combo_pump_cartridge_low_warrning);
        } else if (activeAlert.warningCode == PumpWarningCodes.BATTERY_LOW) {
            notification.text = MainApp.gs(R.string.combo_pump_battery_low_warrning);
        } else if (activeAlert.warningCode == PumpWarningCodes.TBR_CANCELLED) {
            notification.text = MainApp.gs(R.string.combo_pump_tbr_cancelled_warrning);
        }
        MainApp.bus().post(new EventNewNotification(notification));
    }

    private void checkForUnsafeUsage(CommandResult commandResult) {
        if (commandResult == null) return;

        long lastViolation = 0;
        if (commandResult.state.unsafeUsageDetected == PumpState.UNSUPPORTED_BOLUS_TYPE) {
            lastViolation = System.currentTimeMillis();
        } else if (commandResult.history != null) {
            for (Bolus bolus : commandResult.history.bolusHistory) {
                if (!bolus.isValid && bolus.timestamp > lastViolation) {
                    lastViolation = bolus.timestamp;
                }
            }
        }
        if (lastViolation > 0) {
            lowSuspendOnlyLoopEnforcedUntil = lastViolation + 6 * 60 * 60 * 1000;
            if (lowSuspendOnlyLoopEnforcedUntil > System.currentTimeMillis() && violationWarningRaisedForBolusAt != lowSuspendOnlyLoopEnforcedUntil) {
                Notification n = new Notification(Notification.COMBO_PUMP_ALARM,
                        MainApp.gs(R.string.combo_low_suspend_forced_notification),
                        Notification.URGENT);
                n.soundId = R.raw.alarm;
                MainApp.bus().post(new EventNewNotification(n));
                violationWarningRaisedForBolusAt = lowSuspendOnlyLoopEnforcedUntil;
                ConfigBuilderPlugin.getPlugin().getCommandQueue().cancelTempBasal(true, null);
            }
        }
    }

    /**
     * Checks the main screen to determine if TBR on pump matches app state.
     */
    private void checkAndResolveTbrMismatch(PumpState state) {
        // compare with: info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgStatusTempBasal.updateTempBasalInDB()
        long now = System.currentTimeMillis();
        TemporaryBasal aapsTbr = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(now);
        if (aapsTbr == null && state.tbrActive && state.tbrRemainingDuration > 2) {
            if (L.isEnabled(L.PUMP))
                log.debug("Creating temp basal from pump TBR");
            TemporaryBasal newTempBasal = new TemporaryBasal()
                    .date(now)
                    .percent(state.tbrPercent)
                    .duration(state.tbrRemainingDuration)
                    .source(Source.USER);
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(newTempBasal);
        } else if (aapsTbr != null && aapsTbr.getPlannedRemainingMinutes() > 2 && !state.tbrActive) {
            if (L.isEnabled(L.PUMP))
                log.debug("Ending AAPS-TBR since pump has no TBR active");
            TemporaryBasal tempStop = new TemporaryBasal()
                    .date(now)
                    .duration(0)
                    .source(Source.USER);
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStop);
        } else if (aapsTbr != null && state.tbrActive
                && (aapsTbr.percentRate != state.tbrPercent ||
                Math.abs(aapsTbr.getPlannedRemainingMinutes() - state.tbrRemainingDuration) > 2)) {
            if (L.isEnabled(L.PUMP))
                log.debug("AAPSs and pump-TBR differ; ending AAPS-TBR and creating new TBR based on pump TBR");
            TemporaryBasal tempStop = new TemporaryBasal()
                    .date(now - 1000)
                    .duration(0)
                    .source(Source.USER);
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStop);

            TemporaryBasal newTempBasal = new TemporaryBasal()
                    .date(now)
                    .percent(state.tbrPercent)
                    .duration(state.tbrRemainingDuration)
                    .source(Source.USER);
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(newTempBasal);
        }
    }

    /**
     * Reads the pump's history and updates the DB accordingly.
     */
    private boolean readHistory(@Nullable PumpHistoryRequest request) {
        CommandResult historyResult = runCommand(MainApp.gs(R.string.combo_activity_reading_pump_history), 3, () -> ruffyScripter.readHistory(request));
        PumpHistory history = historyResult.history;
        if (!historyResult.success || history == null) {
            return false;
        }

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

    /** Return value indicates whether a new record was created. */
    private boolean updateDbFromPumpHistory(@NonNull PumpHistory history) {
        boolean updated = false;
        for (Bolus pumpBolus : history.bolusHistory) {
            DetailedBolusInfo dbi = new DetailedBolusInfo();
            dbi.date = calculateFakeBolusDate(pumpBolus);
            dbi.pumpId = dbi.date;
            dbi.source = Source.PUMP;
            dbi.insulin = pumpBolus.amount;
            dbi.eventType = CareportalEvent.CORRECTIONBOLUS;
            if (TreatmentsPlugin.getPlugin().addToHistoryTreatment(dbi, true)) {
                updated = true;
            }
        }
        return updated;
    }

    /**
     * Adds the bolus to the timestamp to be able to differentiate multiple boluses in the same
     * minute. Best effort, since this covers only boluses up to 6.0 U and relies on other code
     * to prevent a boluses with the same amount to be delivered within the same minute.
     * Should be good enough, even with command mode, it's a challenge to create that situation
     * and most time clashes will be around SMBs which are covered.
     */
    long calculateFakeBolusDate(Bolus pumpBolus) {
        double bolus = pumpBolus.amount - 0.1;
        int secondsFromBolus = (int) (bolus * 10 * 1000);
        return pumpBolus.timestamp + Math.min(secondsFromBolus, 59 * 1000);
    }

    /**
     * Reads QuickInfo to update reservoir level and determine if new boluses exist on the pump
     * and if so, queries the history for all new records.
     *
     * @return null on success or the failed command result
     */
    private CommandResult checkHistory() {
        CommandResult quickInfoResult = runCommand(MainApp.gs(R.string.combo_activity_checking_for_history_changes), 3,
                () -> ruffyScripter.readQuickInfo(2));

        // no history, nothing to check or complain about
        if (quickInfoResult.history == null || quickInfoResult.history.bolusHistory.isEmpty()) {
            if (L.isEnabled(L.PUMP))
                log.debug("Setting 'pumpHistoryChanged' false");
            pumpHistoryChanged = false;
            return null;
        }

        // compare recent records
        List<Bolus> initialPumpBolusHistory = quickInfoResult.history.bolusHistory;
        if (recentBoluses.size() == 1 && initialPumpBolusHistory.size() >= 1
                && recentBoluses.get(0).equals(quickInfoResult.history.bolusHistory.get(0))) {
            if (L.isEnabled(L.PUMP))
                log.debug("Setting 'pumpHistoryChanged' false");
            pumpHistoryChanged = false;
            return null;
        } else if (recentBoluses.size() == 2 && initialPumpBolusHistory.size() >= 2
                && recentBoluses.get(0).equals(quickInfoResult.history.bolusHistory.get(0))
                && recentBoluses.get(1).equals(quickInfoResult.history.bolusHistory.get(1))) {
            if (L.isEnabled(L.PUMP))
                log.debug("Setting 'pumpHistoryChanged' false");
            pumpHistoryChanged = false;
            return null;
        }

        // fetch new records
        long lastKnownPumpRecordTimestamp = recentBoluses.isEmpty() ? 0 : recentBoluses.get(0).timestamp;
        CommandResult historyResult = runCommand(MainApp.gs(R.string.combo_activity_reading_pump_history), 3, () ->
                ruffyScripter.readHistory(new PumpHistoryRequest().bolusHistory(lastKnownPumpRecordTimestamp)));
        if (!historyResult.success) {
            pumpHistoryChanged = true;
            return historyResult;
        }

        // Check edge of multiple boluses with the same amount in the same minute being imported.
        // This is about as edgy-casey as it can get. I'd be surprised of this one actually ever
        // triggers. It might, so at least give a warning, since a delivered bolus isn't accounted
        // for.
        HashSet<Bolus> bolusSet = new HashSet<>(historyResult.history.bolusHistory);
        if (bolusSet.size() != historyResult.history.bolusHistory.size()) {
            if (L.isEnabled(L.PUMP))
                log.debug("Bolus with same amount within the same minute imported. Only one will make it to the DB.");
            Notification notification = new Notification(Notification.COMBO_PUMP_ALARM, MainApp.gs(R.string.
                    combo_error_multiple_boluses_with_identical_timestamp), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
        }

        pumpHistoryChanged = updateDbFromPumpHistory(historyResult.history);
        if (L.isEnabled(L.PUMP) && pumpHistoryChanged) {
            log.debug("Setting 'pumpHistoryChanged' true");
        }

        List<Bolus> updatedPumpBolusHistory = historyResult.history.bolusHistory;
        if (!updatedPumpBolusHistory.isEmpty()) {
            recentBoluses = updatedPumpBolusHistory.subList(0, Math.min(updatedPumpBolusHistory.size(), 2));
        }

        return null;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        return OPERATION_NOT_SUPPORTED;
    }

    @Override
    public JSONObject getJSONStatus(Profile profile, String profileName) {
        if (!pump.initialized) {
            return null;
        }

        try {
            JSONObject pumpJson = new JSONObject();
            pumpJson.put("clock", DateUtil.toISOString(pump.lastSuccessfulCmdTime));

            int level;
            if (pump.reservoirLevel != -1) level = pump.reservoirLevel;
            else if (pump.state.insulinState == PumpState.LOW) level = 8;
            else if (pump.state.insulinState == PumpState.EMPTY) level = 0;
            else level = 150;
            pumpJson.put("reservoir", level);

            JSONObject statusJson = new JSONObject();
            statusJson.put("status", getStateSummary());
            statusJson.put("timestamp", pump.lastSuccessfulCmdTime);
            pumpJson.put("status", statusJson);

            JSONObject extendedJson = new JSONObject();
            extendedJson.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            extendedJson.put("ActiveProfile", ProfileFunctions.getInstance().getProfileName());
            PumpState ps = pump.state;
            if (ps.tbrActive) {
                extendedJson.put("TempBasalAbsoluteRate", ps.basalRate);
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

    @Override
    public PumpEnactResult loadTDDs() {
        PumpEnactResult result = new PumpEnactResult();
        result.success = readHistory(new PumpHistoryRequest().tddHistory(PumpHistoryRequest.FULL));
        if (result.success) {
            List<Tdd> tdds = pump.tddHistory;
            if (tdds != null) {
                HashMap<Long, TDD> map = new HashMap<>();
                for (int i = 0; i < tdds.size(); i++) {
                    Tdd currTdd = tdds.get(i);
                    if (currTdd.total < 1)
                        continue; //cases where dummy days are introduced (e.g. Battery change with date loss)
                    if (map.containsKey(currTdd.timestamp)) {
                        //duplicate days on time changes
                        TDD existing = map.get(currTdd.timestamp);
                        existing.total += currTdd.total;
                    } else {
                        map.put(currTdd.timestamp, new TDD(currTdd.timestamp, 0d, 0d, currTdd.total));
                    }
                }

                Collection<TDD> uniqueColl = map.values();

                for (TDD currTdd : uniqueColl) {
                    MainApp.getDbHelper().createOrUpdateTDD(currTdd);
                }
            }
        }
        return result;
    }

    // Constraints interface
    private long lowSuspendOnlyLoopEnforcedUntil = 0;
    private long violationWarningRaisedForBolusAt = 0;
    private boolean validBasalRateProfileSelectedOnPump = true;

    @Override
    public Constraint<Boolean> isLoopInvocationAllowed(Constraint<Boolean> value) {
        if (!validBasalRateProfileSelectedOnPump)
            value.set(false, MainApp.gs(R.string.novalidbasalrate), this);
        return value;
    }

    @Override
    public Constraint<Double> applyMaxIOBConstraints(Constraint<Double> maxIob) {
        if (lowSuspendOnlyLoopEnforcedUntil > System.currentTimeMillis())
            maxIob.setIfSmaller(0d, String.format(MainApp.gs(R.string.limitingmaxiob), 0d, MainApp.gs(R.string.unsafeusage)), this);
        return maxIob;
    }

    @Override
    public List<CustomAction> getCustomActions() {
        return null;
    }

    @Override
    public void executeCustomAction(CustomActionType customActionType) {

    }

    @Override
    public boolean canHandleDST() {
        return false;
    }

}