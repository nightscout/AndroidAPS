package info.nightscout.pump.combo;

import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.joda.time.DateTime;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.core.utils.fabric.InstanceId;
import info.nightscout.interfaces.constraints.Constraint;
import info.nightscout.interfaces.constraints.Constraints;
import info.nightscout.interfaces.notifications.Notification;
import info.nightscout.interfaces.plugin.PluginDescription;
import info.nightscout.interfaces.plugin.PluginType;
import info.nightscout.interfaces.profile.Profile;
import info.nightscout.interfaces.profile.ProfileFunction;
import info.nightscout.interfaces.pump.DetailedBolusInfo;
import info.nightscout.interfaces.pump.Pump;
import info.nightscout.interfaces.pump.PumpEnactResult;
import info.nightscout.interfaces.pump.PumpPluginBase;
import info.nightscout.interfaces.pump.PumpSync;
import info.nightscout.interfaces.pump.defs.ManufacturerType;
import info.nightscout.interfaces.pump.defs.PumpDescription;
import info.nightscout.interfaces.pump.defs.PumpType;
import info.nightscout.interfaces.queue.CommandQueue;
import info.nightscout.interfaces.ui.UiInteraction;
import info.nightscout.pump.combo.events.EventComboPumpUpdateGUI;
import info.nightscout.pump.combo.ruffyscripter.BasalProfile;
import info.nightscout.pump.combo.ruffyscripter.BolusProgressReporter;
import info.nightscout.pump.combo.ruffyscripter.CommandResult;
import info.nightscout.pump.combo.ruffyscripter.PumpState;
import info.nightscout.pump.combo.ruffyscripter.PumpWarningCodes;
import info.nightscout.pump.combo.ruffyscripter.RuffyCommands;
import info.nightscout.pump.combo.ruffyscripter.RuffyScripter;
import info.nightscout.pump.combo.ruffyscripter.WarningOrErrorCode;
import info.nightscout.pump.combo.ruffyscripter.history.Bolus;
import info.nightscout.pump.combo.ruffyscripter.history.PumpHistory;
import info.nightscout.pump.combo.ruffyscripter.history.PumpHistoryRequest;
import info.nightscout.pump.combo.ruffyscripter.history.Tdd;
import info.nightscout.rx.bus.RxBus;
import info.nightscout.rx.events.EventDismissNotification;
import info.nightscout.rx.events.EventInitializationChanged;
import info.nightscout.rx.events.EventOverviewBolusProgress;
import info.nightscout.rx.events.EventRefreshOverview;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;
import info.nightscout.shared.interfaces.ResourceHelper;
import info.nightscout.shared.sharedPreferences.SP;
import info.nightscout.shared.utils.DateUtil;
import info.nightscout.shared.utils.T;

/**
 * Driver for the Roche Accu-Chek Combo pump, using the ruffy app for BT communication.
 * <p>
 * For boluses, the logic is to request a bolus and then read it back from the history to see what was
 * actually delivered.
 * <p>
 * TBR-handling doesn't read the pump history. On the pump, TBR records are only created after a TBR has finished.
 * So when a TBR is started on the pump, it can't be known when it started until the TBR ends or is cancelled.
 * Cancelling would assume a user works against the loop, and creating a temporary TBR (AAPS-side) and updating it
 * once a record exists on the pump has other problems, since there's no ID for TBRs, only timestamps.
 * For the regular uses where the user doesn't set TBR (or rather infrequently for some special cases), TBRs are
 * only changed on the pump for error conditions where the pump is stopped, those need to be synced.
 * The approach taken is to create a TBR record in AAPS when AAPS requests a TBR and forego the TBR history on
 * the pump entirely. The pump state is refreshed often enough to tolerate not seeing the full length of a TBR
 * on the pump if it was changed. Thus, during a pump refresh a new TBR starting now is created in AAPS if a
 * mismatch between expected state and actual pump state is detected see {@link #checkAndResolveTbrMismatch(PumpState)}.
 * This approach skipped implementing edge-cases that pose no real risk, in part due to limited resources to
 * implement every edge-case scenario. Insulin amount given via boluses are significantly higher, so the
 * priority was there to make that as safe as possible.
 * <p>
 * Created by mike on 05.08.2016.
 */
@Singleton
public class ComboPlugin extends PumpPluginBase implements Pump, Constraints {
    // collaborators
    private final ProfileFunction profileFunction;
    private final SP sp;
    private RxBus rxBus;
    private final CommandQueue commandQueue;
    private final PumpSync pumpSync;
    private final DateUtil dateUtil;
    private final RuffyCommands ruffyScripter;
    private final UiInteraction uiInteraction;

    private final static PumpDescription pumpDescription = new PumpDescription();


    @NonNull
    private static final ComboPump pump = new ComboPump();

    /**
     * This is used to determine when to pass a bolus cancel request to the scripter
     */
    private volatile boolean scripterIsBolusing;
    /**
     * This is set to true to request a bolus cancellation. {@link #deliverTreatment(DetailedBolusInfo)} (DetailedBolusInfo)}
     * will reset this flag.
     */
    private volatile boolean cancelBolus;

    /**
     * This is set (in {@link #checkHistory()} whenever a connection to the pump is made and
     * indicates if new history records on the pump have been found. This effectively blocks
     * high temps ({@link #setTempBasalPercent(Integer, Integer, PumpSync.TemporaryBasalType)} and boluses
     * ({@link #deliverTreatment(DetailedBolusInfo)} till the queue is empty and the connection
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
     * pump history was detected and was read, as well as in {@link #deliverTreatment(DetailedBolusInfo)}
     * after bolus delivery. Newest record is the first one.
     */
    private volatile List<Bolus> recentBoluses = new ArrayList<>(0);

    private PumpEnactResult OPERATION_NOT_SUPPORTED;

    @Inject
    public ComboPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBus rxBus,
            ResourceHelper rh,
            ProfileFunction profileFunction,
            SP sp,
            CommandQueue commandQueue,
            PumpSync pumpSync,
            DateUtil dateUtil,
            RuffyScripter ruffyScripter,
            UiInteraction activitynames
    ) {
        super(new PluginDescription()
                        .mainType(PluginType.PUMP)
                        .fragmentClass(ComboFragment.class.getName())
                        .pluginIcon(info.nightscout.core.ui.R.drawable.ic_combo_128)
                        .pluginName(R.string.combopump)
                        .shortName(R.string.combopump_shortname)
                        .preferencesId(R.xml.pref_combo)
                        .description(R.string.description_pump_combo),
                injector, aapsLogger, rh, commandQueue
        );
        this.rxBus = rxBus;
        this.profileFunction = profileFunction;
        this.sp = sp;
        this.commandQueue = commandQueue;
        this.pumpSync = pumpSync;
        this.dateUtil = dateUtil;
        this.ruffyScripter = ruffyScripter;
        this.uiInteraction = activitynames;

        pumpDescription.fillFor(PumpType.ACCU_CHEK_COMBO);
    }

    @Override protected void onStart() {
        super.onStart();
        OPERATION_NOT_SUPPORTED = new PumpEnactResult(getInjector())
                .success(false).enacted(false).comment(R.string.combo_pump_unsupported_operation);
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
            return getRh().gs(R.string.combo_pump_state_suspended_due_to_error);
        } else if (ps.suspended) {
            return getRh().gs(R.string.combo_pump_state_suspended_by_user);
        } else if (!pump.initialized) {
            return getRh().gs(R.string.combo_pump_state_initializing);
        } else if (!validBasalRateProfileSelectedOnPump) {
            return getRh().gs(info.nightscout.core.ui.R.string.loop_disabled);
        }
        return getRh().gs(R.string.combo_pump_state_running);
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
    public void connect(@NonNull String reason) {
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
    public void disconnect(@NonNull String reason) {
        getAapsLogger().debug(LTag.PUMP, "Disconnect called with reason: " + reason);
        ruffyScripter.disconnect();
    }

    @Override
    public void stopConnecting() {
        // we're not doing that
    }

    @NonNull @Override
    public synchronized PumpEnactResult setNewBasalProfile(@NonNull Profile profile) {
        if (!isInitialized()) {
            // note that this should not happen anymore since the queue is present, which
            // issues a READSTATE when starting to issue commands which initializes the pump
            getAapsLogger().error("setNewBasalProfile not initialized");
            uiInteraction.addNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED, getRh().gs(info.nightscout.core.ui.R.string.pump_not_initialized_profile_not_set), Notification.URGENT);
            return new PumpEnactResult(getInjector()).success(false).enacted(false).comment(info.nightscout.core.ui.R.string.pump_not_initialized_profile_not_set);
        }

        BasalProfile requestedBasalProfile = convertProfileToComboProfile(profile);
        if (pump.basalProfile.equals(requestedBasalProfile)) {
            //dismiss previously "FAILED" overview notifications
            rxBus.send(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
            rxBus.send(new EventDismissNotification(Notification.FAILED_UPDATE_PROFILE));
            return new PumpEnactResult(getInjector()).success(true).enacted(false);
        }

        CommandResult stateResult = runCommand(null, 1, ruffyScripter::readPumpState);
        if (stateResult.state.unsafeUsageDetected == PumpState.UNSUPPORTED_BASAL_RATE_PROFILE) {
            return new PumpEnactResult(getInjector()).success(false).enacted(false).comment(R.string.combo_force_disabled_notification);
        }

        CommandResult setResult = runCommand(getRh().gs(R.string.combo_activity_setting_basal_profile), 2,
                () -> ruffyScripter.setBasalProfile(requestedBasalProfile));
        if (!setResult.success) {
            uiInteraction.addNotification(Notification.FAILED_UPDATE_PROFILE, getRh().gs(info.nightscout.core.ui.R.string.failed_update_basal_profile), Notification.URGENT);
            return new PumpEnactResult(getInjector()).success(false).enacted(false).comment(info.nightscout.core.ui.R.string.failed_update_basal_profile);
        }

        pump.basalProfile = requestedBasalProfile;

        //dismiss previously "FAILED" overview notifications
        rxBus.send(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
        rxBus.send(new EventDismissNotification(Notification.FAILED_UPDATE_PROFILE));
        //issue success notification
        uiInteraction.addNotificationValidFor(Notification.PROFILE_SET_OK,
                getRh().gs(info.nightscout.core.ui.R.string.profile_set_ok), Notification.INFO, 60);
        return new PumpEnactResult(getInjector()).success(true).enacted(true);
    }

    @Override
    public boolean isThisProfileSet(@NonNull Profile profile) {
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
    public synchronized void getPumpStatus(@NonNull String reason) {
        getAapsLogger().debug(LTag.PUMP, "getPumpStatus called");
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
            getAapsLogger().debug(LTag.PUMP, "Waiting for ruffy service to come up ...");
            SystemClock.sleep(100);
            if (System.currentTimeMillis() > maxWait) {
                getAapsLogger().debug(LTag.PUMP, "ruffy service unavailable, wtf");
                return;
            }
        }

        // trigger a connect, which will update state and check history
        CommandResult stateResult = runCommand(null, 1, ruffyScripter::readPumpState);
        if (stateResult.invalidSetup) {
            uiInteraction.addNotification(Notification.COMBO_PUMP_ALARM,
                    getRh().gs(R.string.combo_invalid_setup),
                    Notification.URGENT);
            return;
        }
        if (!stateResult.success) {
            return;
        }

        // note that since the history is checked upon every connect, the above already updated
        // the DB with any changed history records
        if (pumpHistoryChanged) {
            getAapsLogger().debug(LTag.PUMP, "Pump history has changed and was imported");
            pumpHistoryChanged = false;
        }

        if (stateResult.state.unsafeUsageDetected == PumpState.UNSUPPORTED_BASAL_RATE_PROFILE) {
            uiInteraction.addNotificationWithSound(Notification.COMBO_PUMP_ALARM,
                    getRh().gs(R.string.combo_force_disabled_notification),
                    Notification.URGENT, info.nightscout.core.ui.R.raw.alarm);
            return;
        }

        // read basal profile into cache (KeepAlive will trigger a profile update if needed)
        CommandResult readBasalResult = runCommand(getRh().gs(R.string.combo_actvity_reading_basal_profile), 2, ruffyScripter::readBasalProfile);
        if (!readBasalResult.success) {
            return;
        }
        pump.basalProfile = readBasalResult.basalProfile;
        setValidBasalRateProfileSelectedOnPump(true);

        pump.initialized = true;
        rxBus.send(new EventInitializationChanged());

        // show notification to check pump date if last bolus is older than 24 hours
        // or is in the future
        if (!recentBoluses.isEmpty()) {
            long lastBolusTimestamp = recentBoluses.get(0).timestamp;
            long now = System.currentTimeMillis();
            if (lastBolusTimestamp < now - 24 * 60 * 60 * 1000 || lastBolusTimestamp > now + 5 * 60 * 1000) {
                uiInteraction.addNotification(Notification.COMBO_PUMP_ALARM,
                        getRh().gs(R.string.combo_check_date),
                        Notification.URGENT);
            }
        }

        // read pump BT mac address and use it as the pump's serial
        String macAddress = ruffyScripter.getMacAddress();
        getAapsLogger().debug("Connected pump has MAC address: " + macAddress);
        if (macAddress != null) {
            String lastKnownSN = serialNumber();
            if (!lastKnownSN.equals(fakeSerialNumber()) && !lastKnownSN.equals(macAddress)) {
                getAapsLogger().info(LTag.PUMP, "Pump serial number changed " + lastKnownSN + " -> " + macAddress);
                pumpSync.connectNewPump(true);
            }
            sp.putString(R.string.combo_pump_serial, macAddress);
        }
        if (!pumpSync.verifyPumpIdentification(pumpDescription.getPumpType(), serialNumber()))
            pumpSync.connectNewPump(true);
        // ComboFragment updates state fully only after the pump has initialized,
        // so force an update after initialization completed
        rxBus.send(new EventComboPumpUpdateGUI());
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
        rxBus.send(new EventComboPumpUpdateGUI());
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

    private final BolusProgressReporter bolusProgressReporter = (state, percent, delivered) -> {
        EventOverviewBolusProgress event = EventOverviewBolusProgress.INSTANCE;
        switch (state) {
            case PROGRAMMING:
                event.setStatus(getRh().gs(R.string.combo_programming_bolus));
                break;
            case DELIVERING:
                event.setStatus(getRh().gs(info.nightscout.core.ui.R.string.bolus_delivering, delivered));
                break;
            case DELIVERED:
                event.setStatus(getRh().gs(info.nightscout.core.ui.R.string.bolus_delivered_successfully, delivered));
                break;
            case STOPPING:
                event.setStatus(getRh().gs(R.string.bolusstopping));
                break;
            case STOPPED:
                event.setStatus(getRh().gs(R.string.bolusstopped));
                break;
        }
        event.setPercent(percent);
        rxBus.send(event);
    };

    /**
     * Updates Treatment records with carbs and boluses and delivers a bolus if needed
     */
    @NonNull @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        if (detailedBolusInfo.insulin == 0 || detailedBolusInfo.carbs > 0) {
            throw new IllegalArgumentException(detailedBolusInfo.toString());
        }
        try {
            pump.activity = getRh().gs(R.string.combo_pump_action_bolusing, detailedBolusInfo.insulin);
            rxBus.send(new EventComboPumpUpdateGUI());

            // check pump is ready and all pump bolus records are known
            CommandResult stateResult = runCommand(null, 2, () -> ruffyScripter.readQuickInfo(1));
            if (!stateResult.success) {
                return new PumpEnactResult(getInjector()).success(false).enacted(false)
                        .comment(R.string.combo_error_no_connection_no_bolus_delivered);
            }
            if (stateResult.reservoirLevel != -1 && stateResult.reservoirLevel - 0.5 < detailedBolusInfo.insulin) {
                return new PumpEnactResult(getInjector()).success(false).enacted(false)
                        .comment(R.string.combo_reservoir_level_insufficient_for_bolus);
            }
            // the commands above ensured a connection was made, which updated this field
            if (pumpHistoryChanged) {
                return new PumpEnactResult(getInjector()).success(false).enacted(false)
                        .comment(R.string.combo_bolus_rejected_due_to_pump_history_change);
            }

            Bolus previousBolus = stateResult.history != null && !stateResult.history.bolusHistory.isEmpty()
                    ? stateResult.history.bolusHistory.get(0)
                    : new Bolus(0, 0, false);

            // reject a bolus if one with the exact same size was successfully delivered
            // within the last 1-2 minutes
            if (Math.abs(previousBolus.amount - detailedBolusInfo.insulin) < 0.01
                    && previousBolus.timestamp + 60 * 1000 > System.currentTimeMillis()) {
                getAapsLogger().debug(LTag.PUMP, "Bolus request rejected, same bolus was successfully delivered very recently");
                return new PumpEnactResult(getInjector()).success(false).enacted(false)
                        .comment(R.string.bolus_frequency_exceeded);
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
                    return new PumpEnactResult(getInjector()).success(true).enacted(false);
                }
                if (!timeCheckResult.success) {
                    return new PumpEnactResult(getInjector()).success(false).enacted(false)
                            .comment(R.string.combo_error_no_connection_no_bolus_delivered);
                }
                getAapsLogger().debug(LTag.PUMP, "Waiting for pump clock to advance for the next unused bolus record timestamp");
                SystemClock.sleep(2000);
                timeCheckResult = runCommand(null, 0, ruffyScripter::readPumpState);
                waitLoops++;
            }
            if (waitLoops > 0) {
                long waitDuration = (System.currentTimeMillis() - waitStartTime) / 1000;
                getAapsLogger().debug(LTag.PUMP, "Waited " + waitDuration + "s for pump to switch to a fresh minute before bolusing");
            }

            if (cancelBolus) {
                return new PumpEnactResult(getInjector()).success(true).enacted(false);
            }

            EventOverviewBolusProgress.Treatment treatment = new EventOverviewBolusProgress.Treatment(0.0, 0, detailedBolusInfo.getBolusType() == DetailedBolusInfo.BolusType.SMB, detailedBolusInfo.getId());
            EventOverviewBolusProgress.INSTANCE.setT(treatment);

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
                return new PumpEnactResult(getInjector()).success(false).enacted(false)
                        .comment(R.string.combo_error_bolus_verification_failed);
            }
            Bolus lastPumpBolus = postBolusStateResult.history != null && !postBolusStateResult.history.bolusHistory.isEmpty()
                    ? postBolusStateResult.history.bolusHistory.get(0)
                    : null;

            // no bolus delivered?
            if (lastPumpBolus == null || lastPumpBolus.equals(previousBolus)) {
                if (cancelBolus) {
                    return new PumpEnactResult(getInjector()).success(true).enacted(false);
                } else {
                    return new PumpEnactResult(getInjector())
                            .success(false)
                            .enacted(false)
                            .comment(R.string.combo_error_no_bolus_delivered);
                }
            }

            // at least some insulin delivered, so add it to treatments
            if (!addBolusToTreatments(detailedBolusInfo, lastPumpBolus))
                return new PumpEnactResult(getInjector()).success(false).enacted(true)
                        .comment(R.string.combo_error_updating_treatment_record);

            // check pump bolus record has a sane timestamp
            long now = System.currentTimeMillis();
            if (lastPumpBolus.timestamp < now - 10 * 60 * 1000 || lastPumpBolus.timestamp > now + 10 * 60 * 1000) {
                uiInteraction.addNotification(Notification.COMBO_PUMP_ALARM,
                        getRh().gs(R.string.combo_suspious_bolus_time), Notification.URGENT);
            }

            // update `recentBoluses` so the bolus was just delivered won't be detected as a new
            // bolus that has been delivered on the pump
            recentBoluses = postBolusStateResult.history.bolusHistory;

            // only a partial bolus was delivered
            if (Math.abs(lastPumpBolus.amount - detailedBolusInfo.insulin) > 0.01) {
                if (cancelBolus) {
                    return new PumpEnactResult(getInjector()).success(true).enacted(true);
                }
                return new PumpEnactResult(getInjector()).success(false).enacted(true)
                        .comment(getRh().gs(R.string.combo_error_partial_bolus_delivered,
                                lastPumpBolus.amount, detailedBolusInfo.insulin));
            }

            // full bolus was delivered successfully
            incrementBolusCount();
            return new PumpEnactResult(getInjector())
                    .success(true)
                    .enacted(lastPumpBolus.amount > 0)
                    .bolusDelivered(lastPumpBolus.amount);
        } finally {
            pump.activity = null;
            rxBus.send(new EventComboPumpUpdateGUI());
            rxBus.send(new EventRefreshOverview("Bolus", false));
            cancelBolus = false;
        }
    }

    private void incrementTbrCount() {
        try {
            sp.putLong(R.string.combo_tbrs_set, sp.getLong(R.string.combo_tbrs_set, 0L) + 1);
        } catch (Exception e) {
            // ignore
        }
    }

    private void incrementBolusCount() {
        sp.incLong(R.string.combo_boluses_delivered);
    }

    public Long getTbrsSet() {
        return sp.getLong(R.string.combo_tbrs_set, 0L);
    }

    public Long getBolusesDelivered() {
        return sp.getLong(R.string.combo_boluses_delivered, 0L);
    }

    /**
     * Creates a treatment record based on the request in DetailBolusInfo and the delivered bolus.
     */
    private boolean addBolusToTreatments(DetailedBolusInfo detailedBolusInfo, Bolus lastPumpBolus) {
        try {
            pumpSync.syncBolusWithPumpId(
                    lastPumpBolus.timestamp,
                    lastPumpBolus.amount,
                    detailedBolusInfo.getBolusType(),
                    generatePumpBolusId(lastPumpBolus),
                    PumpType.ACCU_CHEK_COMBO,
                    serialNumber()
            );
        } catch (Exception e) {
            getAapsLogger().error("Adding treatment record failed", e);
            if (detailedBolusInfo.getBolusType() == DetailedBolusInfo.BolusType.SMB) {
                uiInteraction.addNotification(Notification.COMBO_PUMP_ALARM,
                        getRh().gs(R.string.combo_error_updating_treatment_record), Notification.URGENT);
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
    @NonNull @Override
    public PumpEnactResult setTempBasalAbsolute(double absoluteRate, int durationInMinutes, @NonNull Profile profile, boolean force, @NonNull PumpSync.TemporaryBasalType tbrType) {
        getAapsLogger().debug(LTag.PUMP, "setTempBasalAbsolute called with a rate of " + absoluteRate + " for " + durationInMinutes + " min.");
        int unRoundedPercentage = (int) (absoluteRate / getBaseBasalRate() * 100);
        int roundedPercentage = (int) (Math.round(absoluteRate / getBaseBasalRate() * 10) * 10);
        if (unRoundedPercentage != roundedPercentage) {
            getAapsLogger().debug(LTag.PUMP, "Rounded requested rate " + unRoundedPercentage + "% -> " + roundedPercentage + "%");
        }

        return setTempBasalPercent(roundedPercentage, durationInMinutes, tbrType);
    }

    /**
     * Note: AAPS calls this directly only for setting a temp basal issued by the user
     *
     * @param forceNew Driver always applies the requested TBR and simply overrides whatever TBR
     *                 is or isn't running at the moment
     */
    @NonNull @Override
    public PumpEnactResult setTempBasalPercent(int percent, int durationInMinutes, @NonNull Profile profile, boolean forceNew, @NonNull PumpSync.TemporaryBasalType tbrType) {
        return setTempBasalPercent(percent, durationInMinutes, tbrType);
    }

    private PumpEnactResult setTempBasalPercent(Integer percent, final Integer durationInMinutes, @NonNull PumpSync.TemporaryBasalType tbrType) {
        getAapsLogger().debug(LTag.PUMP, "setTempBasalPercent called with " + percent + "% for " + durationInMinutes + "min");

        if (pumpHistoryChanged && percent > 110) {
            return new PumpEnactResult(getInjector()).success(false).enacted(false)
                    .comment(R.string.combo_high_temp_rejected_due_to_pump_history_changes);
        }

        int adjustedPercent = percent;

        if (adjustedPercent > pumpDescription.getMaxTempPercent()) {
            getAapsLogger().debug(LTag.PUMP, "Reducing requested TBR to the maximum support by the pump: " + percent + " -> " + pumpDescription.getMaxTempPercent());
            adjustedPercent = pumpDescription.getMaxTempPercent();
        }

        if (adjustedPercent % 10 != 0) {
            long rounded = Math.round(adjustedPercent / 10d) * 10;
            getAapsLogger().debug(LTag.PUMP, "Rounded requested percentage:" + adjustedPercent + " -> " + rounded);
            adjustedPercent = (int) rounded;
        }

        // do a soft TBR-cancel when requested rate was rounded to 100% (>94% && <104%)
        if (adjustedPercent == 100) {
            return cancelTempBasal(false);
        }

        int finalAdjustedPercent = adjustedPercent;
        CommandResult commandResult = runCommand(getRh().gs(R.string.combo_pump_action_setting_tbr, percent, durationInMinutes),
                3, () -> ruffyScripter.setTbr(finalAdjustedPercent, durationInMinutes));
        if (!commandResult.success) {
            return new PumpEnactResult(getInjector()).success(false).enacted(false);
        }

        PumpState state = commandResult.state;
        if (state.tbrActive && state.tbrPercent == adjustedPercent
                && (state.tbrRemainingDuration == durationInMinutes || state.tbrRemainingDuration == durationInMinutes - 1)) {
            pumpSync.syncTemporaryBasalWithPumpId(
                    state.timestamp,
                    state.tbrPercent,
                    T.Companion.mins(state.tbrRemainingDuration).msecs(),
                    false,
                    tbrType,
                    // There are no IDs for TBRs on the pump and none is calculated (in contrast to boluses).
                    // The current time is used here as an ID, which has no meaning and does not allow identifying
                    // the record on the pump (which isn't needed), but only needs to be unique.
                    // Generally, TBR records are created when a TBR is set by AAPS or when a change on the pump has
                    // been detected, rather than checking the pumps history of TBRs.
                    state.timestamp,
                    PumpType.ACCU_CHEK_COMBO,
                    serialNumber()
            );

            rxBus.send(new EventComboPumpUpdateGUI());
        }

        incrementTbrCount();
        return new PumpEnactResult(getInjector()).success(true).enacted(true).isPercent(true)
                .percent(state.tbrPercent).duration(state.tbrRemainingDuration);
    }

    @NonNull @Override
    public PumpEnactResult setExtendedBolus(double insulin, int durationInMinutes) {
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
    @NonNull @Override
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        getAapsLogger().debug(LTag.PUMP, "cancelTempBasal called");
        final PumpSync.PumpState.TemporaryBasal activeTemp = pumpSync.expectedPumpState().getTemporaryBasal();
        if (enforceNew) {
            CommandResult stateResult = runCommand(getRh().gs(R.string.combo_pump_action_refreshing), 2, ruffyScripter::readPumpState);
            if (!stateResult.success) {
                return new PumpEnactResult(getInjector()).success(false).enacted(false);
            }
            if (!stateResult.state.tbrActive) {
                return new PumpEnactResult(getInjector()).success(true).enacted(false);
            }
            getAapsLogger().debug(LTag.PUMP, "cancelTempBasal: hard-cancelling TBR since force requested");
            CommandResult cancelResult = runCommand(getRh().gs(R.string.combo_pump_action_cancelling_tbr), 2, ruffyScripter::cancelTbr);
            if (!cancelResult.success) {
                return new PumpEnactResult(getInjector()).success(false).enacted(false);
            }
            if (!cancelResult.state.tbrActive) {
                pumpSync.syncStopTemporaryBasalWithPumpId(
                        cancelResult.state.timestamp,
                        // Combo doesn't have nor uses IDs for TBRs, see note in #setTempBasalPercent
                        cancelResult.state.timestamp,
                        PumpType.ACCU_CHEK_COMBO,
                        serialNumber()
                );
                return new PumpEnactResult(getInjector()).isTempCancel(true).success(true).enacted(true);
            } else {
                return new PumpEnactResult(getInjector()).success(false).enacted(false).comment(info.nightscout.core.ui.R.string.canceling_eb_failed);
            }
        } else if (activeTemp == null) {
            return new PumpEnactResult(getInjector()).success(true).enacted(false);
        } else if ((activeTemp.getRate() >= 90 && activeTemp.getRate() <= 110)
                && activeTemp.getPlannedRemainingMinutes() <= 15) {
            // Let fake neutral temp keep run (see below)
            // Note that since this runs on the queue a connection is opened regardless, but this
            // case doesn't occur all that often, so it's not worth optimizing (1.3k SetTBR vs 4 cancelTBR).
            getAapsLogger().debug(LTag.PUMP, "cancelTempBasal: skipping changing tbr since it " +
                    "already is at " + activeTemp.getRate() + "% and running for another " + activeTemp.getPlannedRemainingMinutes() + " mins.");
            return new PumpEnactResult(getInjector()).success(true).enacted(true)
                    .comment("cancelTempBasal skipping changing tbr since it already is at "
                            + activeTemp.getRate() + "% and running for another "
                            + activeTemp.getPlannedRemainingMinutes() + " mins.");
        } else {
            // Set a fake neutral temp to avoid TBR cancel alert. Decide 90% vs 110% based on
            // on whether the TBR we're cancelling is above or below 100%.
            final int percentage = (activeTemp.getRate() > 100) ? 110 : 90;
            getAapsLogger().debug(LTag.PUMP, "cancelTempBasal: changing TBR to " + percentage + "% for 15 mins.");
            return setTempBasalPercent(percentage, 15, PumpSync.TemporaryBasalType.NORMAL);
        }
    }

    @Override public int waitForDisconnectionInSeconds() {
        return 0;
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
                pump.activity = getRh().gs(R.string.combo_activity_checking_pump_state);
                rxBus.send(new EventComboPumpUpdateGUI());
                CommandResult preCheckError = runOnConnectChecks();
                pump.activity = originalActivity;
                if (preCheckError != null) {
                    updateLocalData(preCheckError);
                    return preCheckError;
                }
            }

            if (activity != null) {
                pump.activity = activity;
                rxBus.send(new EventComboPumpUpdateGUI());
            }

            commandResult = commandExecution.execute();

            if (!commandResult.success && retries > 0) {
                for (int retryAttempts = 1; !commandResult.success && retryAttempts <= retries; retryAttempts++) {
                    getAapsLogger().debug(LTag.PUMP, "Command was not successful, retries requested, doing retry #" + retryAttempts);
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
                    uiInteraction.addNotificationWithSound(Notification.COMBO_PUMP_ALARM,
                            getRh().gs(R.string.combo_force_disabled_notification),
                            Notification.URGENT, info.nightscout.core.ui.R.raw.alarm);
                    commandQueue.cancelTempBasal(true, null);
                }
                updateLocalData(commandResult);
            }
        } finally {
            if (activity != null) {
                pump.activity = null;
                rxBus.send(new EventComboPumpUpdateGUI());
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
                CommandResult alertConfirmationResult = ruffyScripter.confirmAlert(activeAlert.warningCode);
                if (!alertConfirmationResult.success) {
                    return alertConfirmationResult;
                }
                // while the warning was active the menu data couldn't be read, only after confirmation,
                // so update the var with it, so the check routines below can work on it
                preCheckResult = alertConfirmationResult;
            } else if (activeAlert.errorCode != null) {
                uiInteraction.addNotificationValidTo(Notification.COMBO_PUMP_ALARM, dateUtil.now(),
                        getRh().gs(R.string.combo_is_in_error_state, activeAlert.errorCode, activeAlert.message), Notification.URGENT, 0);
                return preCheckResult.success(false);
            }
        }

        if (!preCheckResult.state.suspended) {
            checkForUnsafeUsage(preCheckResult);
            checkAndResolveTbrMismatch(preCheckResult.state);
            checkPumpTime(preCheckResult.state);
            checkBasalRate(preCheckResult.state);
            return checkHistory();
        } else {
            long now = System.currentTimeMillis();
            PumpSync.PumpState.TemporaryBasal aapsTbr = pumpSync.expectedPumpState().getTemporaryBasal();
            if (aapsTbr == null || aapsTbr.getRate() != 0) {
                getAapsLogger().debug(LTag.PUMP, "Creating 15m zero temp since pump is suspended");
                pumpSync.syncTemporaryBasalWithPumpId(
                        now,
                        0.0,
                        T.Companion.mins(15).msecs(),
                        false,
                        PumpSync.TemporaryBasalType.PUMP_SUSPEND,
                        // Combo doesn't have nor uses IDs for TBRs, see note in #setTempBasalPercent
                        now,
                        PumpType.ACCU_CHEK_COMBO,
                        serialNumber()
                );
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
        int pumpHour = new DateTime(state.pumpTime).getHourOfDay();
        int phoneHour = DateTime.now().getHourOfDay();
        if (pumpHour != phoneHour) {
            // only check if clocks are close
            return;
        }

        if (Math.abs(pumpBasalRate - getBaseBasalRate()) > 0.001) {
            CommandResult readBasalResult = runCommand(getRh().gs(R.string.combo_actvity_reading_basal_profile), 2, ruffyScripter::readBasalProfile);
            if (readBasalResult.success) {
                pump.basalProfile = readBasalResult.basalProfile;
                uiInteraction.addNotification(Notification.COMBO_PUMP_ALARM,
                        getRh().gs(R.string.combo_warning_pump_basal_rate_changed), Notification.NORMAL);
            } else {
                uiInteraction.addNotification(Notification.COMBO_PUMP_ALARM,
                        getRh().gs(R.string.combo_error_failure_reading_changed_basal_rate), Notification.URGENT);
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
            getAapsLogger().debug(LTag.PUMP, "Pump clock needs update, pump time: " + state.pumpTime + " (" + new Date(state.pumpTime) + ")");
            uiInteraction.addNotification(Notification.COMBO_PUMP_ALARM, getRh().gs(R.string.combo_notification_check_time_date), Notification.URGENT);
        } else if (Math.abs(state.pumpTime - System.currentTimeMillis()) >= 3 * 60 * 1000) {
            getAapsLogger().debug(LTag.PUMP, "Pump clock needs update, pump time: " + state.pumpTime + " (" + new Date(state.pumpTime) + ")");
            uiInteraction.addNotification(Notification.COMBO_PUMP_ALARM, getRh().gs(R.string.combo_notification_check_time_date), Notification.NORMAL);
        }
    }

    private void notifyAboutPumpWarning(WarningOrErrorCode activeAlert) {
        if (activeAlert.warningCode == null ||
                (!activeAlert.warningCode.equals(PumpWarningCodes.CARTRIDGE_LOW)
                        && !activeAlert.warningCode.equals(PumpWarningCodes.BATTERY_LOW)
                        && !activeAlert.warningCode.equals(PumpWarningCodes.TBR_CANCELLED))) {
            throw new IllegalArgumentException(activeAlert.toString());
        }
        String text = "";
        if (activeAlert.warningCode == PumpWarningCodes.CARTRIDGE_LOW) {
            text = getRh().gs(R.string.combo_pump_cartridge_low_warrning);
        } else if (activeAlert.warningCode == PumpWarningCodes.BATTERY_LOW) {
            text = getRh().gs(R.string.combo_pump_battery_low_warrning);
        } else if (activeAlert.warningCode == PumpWarningCodes.TBR_CANCELLED) {
            text = getRh().gs(R.string.combo_pump_tbr_cancelled_warrning);
        }
        uiInteraction.addNotification(Notification.COMBO_PUMP_ALARM, text, Notification.NORMAL);
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
                uiInteraction.addNotificationWithSound(Notification.COMBO_PUMP_ALARM,
                        getRh().gs(R.string.combo_low_suspend_forced_notification),
                        Notification.URGENT, info.nightscout.core.ui.R.raw.alarm);
                violationWarningRaisedForBolusAt = lowSuspendOnlyLoopEnforcedUntil;
                commandQueue.cancelTempBasal(true, null);
            }
        }
    }

    /**
     * Checks the main screen to determine if TBR on pump matches app state.
     */
    private void checkAndResolveTbrMismatch(PumpState state) {
        long now = System.currentTimeMillis();
        // Combo doesn't have nor uses IDs for TBRs, see note in #setTempBasalPercent
        //noinspection UnnecessaryLocalVariable
        long tbrId = now;
        PumpSync.PumpState.TemporaryBasal aapsTbr = pumpSync.expectedPumpState().getTemporaryBasal();
        if (aapsTbr == null && state.tbrActive && state.tbrRemainingDuration > 2) {
            getAapsLogger().debug(LTag.PUMP, "Creating temp basal from pump TBR");
            pumpSync.syncTemporaryBasalWithPumpId(
                    now,
                    state.tbrPercent,
                    T.Companion.mins(state.tbrRemainingDuration).msecs(),
                    false,
                    PumpSync.TemporaryBasalType.NORMAL,
                    tbrId,
                    PumpType.ACCU_CHEK_COMBO,
                    serialNumber()
            );
        } else if (aapsTbr != null && aapsTbr.getPlannedRemainingMinutes() > 2 && !state.tbrActive) {
            getAapsLogger().debug(LTag.PUMP, "Ending AAPS-TBR since pump has no TBR active");
            pumpSync.syncStopTemporaryBasalWithPumpId(
                    now,
                    tbrId,
                    PumpType.ACCU_CHEK_COMBO,
                    serialNumber()
            );
        } else if (aapsTbr != null && state.tbrActive
                && (aapsTbr.getRate() != state.tbrPercent ||
                Math.abs(aapsTbr.getPlannedRemainingMinutes() - state.tbrRemainingDuration) > 2)) {
            getAapsLogger().debug(LTag.PUMP, "AAPSs and pump-TBR differ; ending AAPS-TBR and creating new TBR based on pump TBR");

            // crate TBR end record a second ago
            pumpSync.syncStopTemporaryBasalWithPumpId(
                    now - 1000,
                    // fake a unique ID that doesn't clash with the record below
                    tbrId - 1000,
                    PumpType.ACCU_CHEK_COMBO,
                    serialNumber()
            );

            // Create TBR start record, starting now
            pumpSync.syncTemporaryBasalWithPumpId(
                    now,
                    state.tbrPercent,
                    T.Companion.mins(state.tbrRemainingDuration).msecs(),
                    false,
                    PumpSync.TemporaryBasalType.NORMAL,
                    tbrId,
                    PumpType.ACCU_CHEK_COMBO,
                    serialNumber()
            );
        }
    }

    /**
     * Reads the pump's history and updates the DB accordingly.
     */
    private boolean readHistory(@Nullable PumpHistoryRequest request) {
        CommandResult historyResult = runCommand(getRh().gs(info.nightscout.core.ui.R.string.reading_pump_history), 3, () -> ruffyScripter.readHistory(request));
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

    /**
     * Return value indicates whether a new record was created.
     */
    private boolean updateDbFromPumpHistory(@NonNull PumpHistory history) {
        boolean updated = false;
        for (Bolus pumpBolus : history.bolusHistory) {
            if (pumpSync.syncBolusWithPumpId(
                    pumpBolus.timestamp,
                    pumpBolus.amount,
                    null,
                    generatePumpBolusId(pumpBolus),
                    PumpType.ACCU_CHEK_COMBO,
                    serialNumber()
            )) {
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
    long generatePumpBolusId(Bolus pumpBolus) {
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
        CommandResult quickInfoResult = runCommand(getRh().gs(R.string.combo_activity_checking_for_history_changes), 3,
                () -> ruffyScripter.readQuickInfo(2));

        // no history, nothing to check or complain about
        if (quickInfoResult.history == null || quickInfoResult.history.bolusHistory.isEmpty()) {
            getAapsLogger().debug(LTag.PUMP, "Setting 'pumpHistoryChanged' false");
            pumpHistoryChanged = false;
            return null;
        }

        // compare recent records
        List<Bolus> initialPumpBolusHistory = quickInfoResult.history.bolusHistory;
        if (recentBoluses.size() == 1 && initialPumpBolusHistory.size() >= 1
                && recentBoluses.get(0).equals(quickInfoResult.history.bolusHistory.get(0))) {
            getAapsLogger().debug(LTag.PUMP, "Setting 'pumpHistoryChanged' false");
            pumpHistoryChanged = false;
            return null;
        } else if (recentBoluses.size() == 2 && initialPumpBolusHistory.size() >= 2
                && recentBoluses.get(0).equals(quickInfoResult.history.bolusHistory.get(0))
                && recentBoluses.get(1).equals(quickInfoResult.history.bolusHistory.get(1))) {
            getAapsLogger().debug(LTag.PUMP, "Setting 'pumpHistoryChanged' false");
            pumpHistoryChanged = false;
            return null;
        }

        // fetch new records
        long lastKnownPumpRecordTimestamp = recentBoluses.isEmpty() ? 0 : recentBoluses.get(0).timestamp;
        CommandResult historyResult = runCommand(getRh().gs(info.nightscout.core.ui.R.string.reading_pump_history), 3, () ->
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
            getAapsLogger().debug(LTag.PUMP, "Bolus with same amount within the same minute imported. Only one will make it to the DB.");
            uiInteraction.addNotification(Notification.COMBO_PUMP_ALARM, getRh().gs(R.string.
                    combo_error_multiple_boluses_with_identical_timestamp), Notification.URGENT);
        }

        pumpHistoryChanged = updateDbFromPumpHistory(historyResult.history);
        if (pumpHistoryChanged) {
            getAapsLogger().debug(LTag.PUMP, "Setting 'pumpHistoryChanged' true");
        }

        List<Bolus> updatedPumpBolusHistory = historyResult.history.bolusHistory;
        if (!updatedPumpBolusHistory.isEmpty()) {
            recentBoluses = updatedPumpBolusHistory.subList(0, Math.min(updatedPumpBolusHistory.size(), 2));
        }

        return null;
    }

    @NonNull @Override
    public PumpEnactResult cancelExtendedBolus() {
        return OPERATION_NOT_SUPPORTED;
    }

    @NonNull @Override
    public JSONObject getJSONStatus(@NonNull Profile profile, @NonNull String profileName, @NonNull String version) {
        if (!pump.initialized) {
            return new JSONObject();
        }

        try {
            JSONObject pumpJson = new JSONObject();
            pumpJson.put("clock", dateUtil.toISOString(pump.lastSuccessfulCmdTime));

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
            extendedJson.put("Version", version);
            extendedJson.put("ActiveProfile", profileFunction.getProfileName());
            PumpState ps = pump.state;
            if (ps.tbrActive) {
                extendedJson.put("TempBasalAbsoluteRate", ps.basalRate);
                extendedJson.put("TempBasalPercent", ps.tbrPercent);
                extendedJson.put("TempBasalRemaining", ps.tbrRemainingDuration);
            }
            if (ps.activeAlert != null && ps.activeAlert.errorCode != null) {
                extendedJson.put("ErrorCode", ps.activeAlert.errorCode);
            }
            extendedJson.put("BaseBasalRate", getBaseBasalRate());
            pumpJson.put("extended", extendedJson);

            JSONObject batteryJson = new JSONObject();
            int battery = 100;
            if (ps.batteryState == PumpState.LOW) battery = 25;
            else if (ps.batteryState == PumpState.EMPTY) battery = 0;
            batteryJson.put("percent", battery);
            pumpJson.put("battery", batteryJson);

            return pumpJson;
        } catch (Exception e) {
            getAapsLogger().warn(LTag.PUMP, "Failed to gather device status for upload " + e);
        }

        return new JSONObject();
    }

    @NonNull @Override
    public ManufacturerType manufacturer() {
        return ManufacturerType.Roche;
    }

    @NonNull @Override
    public PumpType model() {
        return PumpType.ACCU_CHEK_COMBO;
    }

    @NonNull @Override
    public String serialNumber() {
        return sp.getString(R.string.combo_pump_serial, fakeSerialNumber());
    }

    private String fakeSerialNumber() {
        return InstanceId.INSTANCE.getInstanceId();
    }

    @NonNull @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @NonNull @Override
    public String shortStatus(boolean veryShort) {
        return getStateSummary();
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @NonNull @Override
    public PumpEnactResult loadTDDs() {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        result.success(readHistory(new PumpHistoryRequest().tddHistory(PumpHistoryRequest.FULL)));
        if (result.getSuccess()) {
            List<Tdd> tdds = pump.tddHistory;
            if (tdds != null) {
                HashMap<Long, Tdd> map = new HashMap<>();
                for (int i = 0; i < tdds.size(); i++) {
                    Tdd currTdd = tdds.get(i);
                    if (currTdd.total < 1)
                        continue; //cases where dummy days are introduced (e.g. Battery change with date loss)
                    if (map.containsKey(currTdd.timestamp)) {
                        //duplicate days on time changes
                        Tdd existing = map.get(currTdd.timestamp);
                        existing.total += currTdd.total;
                    } else {
                        map.put(currTdd.timestamp, currTdd);
                    }
                }

                Collection<Tdd> uniqueColl = map.values();

                for (Tdd currTdd : uniqueColl) {
                    pumpSync.createOrUpdateTotalDailyDose(
                            currTdd.timestamp,
                            0.0,
                            0.0,
                            currTdd.total,
                            null,
                            PumpType.ACCU_CHEK_COMBO,
                            serialNumber()
                    );
                }
            }
        }
        return result;
    }

    // Constraints interface
    private long lowSuspendOnlyLoopEnforcedUntil = 0;
    private long violationWarningRaisedForBolusAt = 0;
    private boolean validBasalRateProfileSelectedOnPump = true;

    @NonNull @Override
    public Constraint<Boolean> isLoopInvocationAllowed(@NonNull Constraint<Boolean> value) {
        if (!validBasalRateProfileSelectedOnPump)
            value.set(getAapsLogger(), false, getRh().gs(info.nightscout.core.ui.R.string.no_valid_basal_rate), this);
        return value;
    }

    @NonNull @Override
    public Constraint<Double> applyMaxIOBConstraints(@NonNull Constraint<Double> maxIob) {
        if (lowSuspendOnlyLoopEnforcedUntil > System.currentTimeMillis())
            maxIob.setIfSmaller(getAapsLogger(), 0d, getRh().gs(info.nightscout.core.ui.R.string.limiting_iob, 0d, getRh().gs(R.string.unsafeusage)), this);
        return maxIob;
    }

    @Override
    public boolean canHandleDST() {
        return false;
    }
}
