package info.nightscout.androidaps.plugins.PumpCombo;

import android.os.SystemClock;
import android.support.annotation.NonNull;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import de.jotomo.ruffy.spi.BasalProfile;
import de.jotomo.ruffy.spi.BolusProgressReporter;
import de.jotomo.ruffy.spi.CommandResult;
import de.jotomo.ruffy.spi.PumpState;
import de.jotomo.ruffy.spi.PumpWarningCodes;
import de.jotomo.ruffy.spi.RuffyCommands;
import de.jotomo.ruffy.spi.history.Bolus;
import de.jotomo.ruffy.spi.history.PumpHistory;
import de.jotomo.ruffy.spi.history.PumpHistoryRequest;
import de.jotomo.ruffy.spi.history.Tbr;
import de.jotomo.ruffy.spi.history.WarningOrErrorCode;
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
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.PumpCombo.events.EventComboPumpUpdateGUI;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;
import info.nightscout.utils.SP;

import static de.jotomo.ruffy.spi.BolusProgressReporter.State.FINISHED;

/**
 * Created by mike on 05.08.2016.
 */
public class ComboPlugin implements PluginBase, PumpInterface, ConstraintsInterface {
    private static Logger log = LoggerFactory.getLogger(ComboPlugin.class);

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


        pumpDescription.isSetBasalProfileCapable = false;
        pumpDescription.basalStep = 0.01d;
        pumpDescription.basalMinimumRate = 0.0d;

        pumpDescription.isRefillingCapable = true;
    }

    @NonNull
    private final RuffyCommands ruffyScripter;

    private static ComboPump pump = new ComboPump();

    private volatile boolean bolusInProgress;
    private volatile boolean cancelBolus;
    private Bolus lastRequestedBolus;
    private long pumpHistoryLastChecked;

    public static ComboPlugin getPlugin() {
        if (plugin == null)
            plugin = new ComboPlugin();
        return plugin;
    }

    private static PumpEnactResult OPERATION_NOT_SUPPORTED = new PumpEnactResult()
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
    public synchronized int setNewBasalProfile(Profile profile) {
        if (!isInitialized()) {
            log.error("setNewBasalProfile not initialized");
            Notification notification = new Notification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED, MainApp.sResources.getString(R.string.pumpNotInitializedProfileNotSet), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
            return PumpInterface.FAILED;
        }

        BasalProfile requestedBasalProfile = convertProfileToComboProfile(profile);
        if (pump.basalProfile.equals(requestedBasalProfile)) {
            //dismiss previously "FAILED" overview notifications
            MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
            MainApp.bus().post(new EventDismissNotification(Notification.FAILED_UDPATE_PROFILE));
            return PumpInterface.NOT_NEEDED;
        }

        CommandResult setResult = runCommand(MainApp.sResources.getString(R.string.combo_activity_setting_basal_profile), 2,
                () -> ruffyScripter.setBasalProfile(requestedBasalProfile));
        if (!setResult.success) {
            Notification notification = new Notification(Notification.FAILED_UDPATE_PROFILE, MainApp.sResources.getString(R.string.failedupdatebasalprofile), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
            return PumpInterface.FAILED;
        }

        CommandResult readResult = runCommand(MainApp.sResources.getString(R.string.combo_activity_setting_basal_profile), 2,
                ruffyScripter::readBasalProfile);

        pump.basalProfile = readResult.basalProfile;

        if(readResult.success && readResult.basalProfile.equals(requestedBasalProfile)){
            //dismiss previously "FAILED" overview notifications
            MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
            MainApp.bus().post(new EventDismissNotification(Notification.FAILED_UDPATE_PROFILE));
            //issue success notification
            Notification notification = new Notification(Notification.PROFILE_SET_OK, MainApp.sResources.getString(R.string.profile_set_ok), Notification.INFO, 60);
            MainApp.bus().post(new EventNewNotification(notification));
            return PumpInterface.SUCCESS;
        } else {
            Notification notification = new Notification(Notification.FAILED_UDPATE_PROFILE, MainApp.sResources.getString(R.string.failedupdatebasalprofile), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
            return PumpInterface.FAILED;
        }
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        if (!isInitialized()) {
            // This is called too soon (for the Combo) on startup, so ignore this.
            // The Combo init (refreshDataFromPump) will read the profile and update the pump's
            // profile if the pref is set;
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
    public synchronized void refreshDataFromPump(String reason) {
        log.debug("RefreshDataFromPump called");
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
                1, ruffyScripter::readReservoirLevelAndLastBolus);
        if (!stateResult.success) {
            return;
        }

        // ensure time and date(!) are current; connect triggers a notification on mismatch
        /* menu not supported by ruffy v1
        if (!pump.initialized) {
            if (!runCommand("Updating pump clock", 2, ruffyScripter::setDateAndTime).success) {
                return;
            }
        }
        */

        // read basal profile into cache and update pump profile if needed
        if (!pump.initialized) {
            CommandResult readBasalResult = runCommand("Reading basal profile", 2, ruffyScripter::readBasalProfile);
            if (!readBasalResult.success) {
                return;
            }
            pump.basalProfile = readBasalResult.basalProfile;

            Profile profile = MainApp.getConfigBuilder().getProfile();
            if (SP.getBoolean("syncprofiletopump", false)
                    && !pump.basalProfile.equals(convertProfileToComboProfile(profile))) {
                setNewBasalProfile(profile);
            }
        }

        if (!checkPumpHistory()) {
            return;
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
        if (result.reservoirLevel != PumpState.UNKNOWN) {
            pump.reservoirLevel = result.reservoirLevel;
        }

        if (result.lastBolus != null) {
            pump.lastBolus = result.lastBolus;
        } else if (result.history != null && !result.history.bolusHistory.isEmpty()) {
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
        try {
            if (detailedBolusInfo.insulin == 0 && detailedBolusInfo.carbs == 0) {
                // neither carbs nor bolus requested
                log.error("deliverTreatment: Invalid input");
                return new PumpEnactResult().success(false).enacted(false)
                        .bolusDelivered(0d).carbsDelivered(0d)
                        .comment(MainApp.instance().getString(R.string.danar_invalidinput));
            } else if (detailedBolusInfo.insulin > 0) {
                // bolus needed, ask pump to deliver it
                PumpEnactResult pumpEnactResult = deliverBolus(detailedBolusInfo);
                if (!pumpEnactResult.success) {
                    log.debug("Bolus delivery failed, refreshing history in background thread");
                    new Thread(this::checkPumpHistory).start();
                }
                return pumpEnactResult;
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
            return new PumpEnactResult().success(false).enacted(false)
                    .comment(MainApp.sResources.getString(R.string.bolus_frequency_exceeded));
        }
        lastRequestedBolus = new Bolus(System.currentTimeMillis(), detailedBolusInfo.insulin, true);

        Bolus lastKnownBolus = pump.lastBolus;

        try {
            pump.activity = MainApp.sResources.getString(R.string.combo_pump_action_bolusing, detailedBolusInfo.insulin);
            MainApp.bus().post(new EventComboPumpUpdateGUI());

            // refresh pump data
            CommandResult reservoirBolusResult = runCommand(null, 3,
                    ruffyScripter::readReservoirLevelAndLastBolus);
            if (!reservoirBolusResult.success) {
                return new PumpEnactResult().success(false).enacted(false);
            }

            // check enough insulin left for bolus
            if (Math.round(detailedBolusInfo.insulin + 0.5) > reservoirBolusResult.reservoirLevel) {
                return new PumpEnactResult().success(false).enacted(false)
                        .comment(MainApp.sResources.getString(R.string.combo_reservoir_level_insufficient_for_bolus));
            }

            // verify we're update to date and know the most recent bolus
            if (!Objects.equals(lastKnownBolus, reservoirBolusResult.lastBolus)) {
                log.error("Bolus delivery failure at stage 3", new Exception());
                return new PumpEnactResult().success(false).enacted(false)
                        .comment(MainApp.sResources.getString(R.string.combo_pump_bolus_history_state_mismatch));
            }

            if (cancelBolus) {
                return new PumpEnactResult().success(true).enacted(false);
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
                return new PumpEnactResult().success(false).enacted(false).
                        comment(MainApp.sResources.getString(R.string.combo_pump_bolus_verification_failed));
            }

            // add treatment record to DB (if it wasn't cancelled)
            if (lastPumpBolus != null && (lastPumpBolus.amount > 0)) {
                detailedBolusInfo.date = reservoirBolusResult.lastBolus.timestamp;
                detailedBolusInfo.insulin = lastPumpBolus.amount;
                detailedBolusInfo.date = lastPumpBolus.timestamp;
                MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
                return new PumpEnactResult().success(true).enacted(true)
                        .bolusDelivered(lastPumpBolus.amount).carbsDelivered(detailedBolusInfo.carbs);
            } else {
                return new PumpEnactResult().success(true).enacted(false);
            }
        } finally {
            // BolusCommand.execute() intentionally doesn't close the progress dialog if an error
            // occurred so it stays open while the connection was re-established if needed and/or
            // this method did recovery
            bolusProgressReporter.report(FINISHED, 100, 0);
            pump.activity = null;
            MainApp.bus().post(new EventComboPumpUpdateGUI());
            MainApp.bus().post(new EventRefreshOverview("Combo Bolus"));
            cancelBolus = false;
        }
    }

    @Override
    public void stopBolusDelivering() {
        if (bolusInProgress) {
            ruffyScripter.cancelBolus();
        }
        cancelBolus = true;
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

        // do a soft TBR-cancel when requested rate was rounded to 100% (>94% && <104%)
        if (adjustedPercent == 100) {
            return cancelTempBasal(false);
        }

        long thisMinute = System.currentTimeMillis() / (60 * 1000) * (60 * 1000);
        TemporaryBasal activeTbr = MainApp.getDbHelper().getTemporaryBasalsDataByDate(thisMinute);
        if (activeTbr != null && activeTbr.date == thisMinute) {
            // setting multiple TBRs within a single minute (with the first TBR having a runtime
            // of 0) is not supported. Attempting to do so sets a new TBR on the pump but adding
            // the record to the DB fails as there already is a record with that date.
            return new PumpEnactResult().success(false).enacted(false);
        }

        int finalAdjustedPercent = adjustedPercent;
        CommandResult commandResult = runCommand(MainApp.sResources.getString(R.string.combo_pump_action_setting_tbr, percent, durationInMinutes),
                3, () -> ruffyScripter.setTbr(finalAdjustedPercent, durationInMinutes));
        if (!commandResult.success) {
            return new PumpEnactResult().success(false).enacted(false);
        }

        PumpState state = commandResult.state;
        if (state.tbrActive && state.tbrPercent == percent
                && (state.tbrRemainingDuration == durationInMinutes || state.tbrRemainingDuration == durationInMinutes - 1)) {
            TemporaryBasal tempStart = new TemporaryBasal();
            tempStart.date = state.timestamp / (60 * 1000) * (60 * 1000);
            tempStart.durationInMinutes = durationInMinutes;
            tempStart.percentRate = adjustedPercent;
            tempStart.isAbsolute = false;
            tempStart.source = Source.USER;
            tempStart.pumpId = tempStart.date;
            ConfigBuilderPlugin treatmentsInterface = MainApp.getConfigBuilder();
            treatmentsInterface.addToHistoryTempBasal(tempStart);

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
        if (activeTemp == null) {
            return new PumpEnactResult().success(false).enacted(false);
        }
        if (userRequested) {
            log.debug("cancelTempBasal: hard-cancelling TBR since user requested");
            CommandResult commandResult = runCommand(MainApp.sResources.getString(R.string.combo_pump_action_cancelling_tbr), 2, ruffyScripter::cancelTbr);
            if (!commandResult.state.tbrActive) {
                TemporaryBasal tempBasal = new TemporaryBasal();
                tempBasal.date = commandResult.state.timestamp;
                tempBasal.durationInMinutes = 0;
                tempBasal.source = Source.USER;
                tempBasal.pumpId = activeTemp.pumpId;
                MainApp.getConfigBuilder().addToHistoryTempBasal(tempBasal);
                return new PumpEnactResult().isTempCancel(true).success(true).enacted(true);
            } else {
                return new PumpEnactResult().success(false).enacted(false);
            }
        } else if ((activeTemp.percentRate >= 90 && activeTemp.percentRate <= 110) && activeTemp.getPlannedRemainingMinutes() <= 15) {
            // Let fake neutral temp keep run (see below)
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
            CommandResult commandResult = runCommand(MainApp.sResources.getString(R.string.combo_pump_action_cancelling_tbr), 2, () -> ruffyScripter.setTbr(percentage, 15));

            if (commandResult.state.tbrActive && commandResult.state.tbrPercent == percentage
                    && (commandResult.state.tbrRemainingDuration == 15 || commandResult.state.tbrRemainingDuration == 14)) {
                TemporaryBasal tempBasal = new TemporaryBasal();
                tempBasal.date = System.currentTimeMillis() / (60 * 1000) * (60 * 1000);
                tempBasal.durationInMinutes = 15;
                tempBasal.source = Source.USER;
                tempBasal.pumpId = tempBasal.date;
                tempBasal.percentRate = percentage;
                tempBasal.isAbsolute = false;
                MainApp.getConfigBuilder().addToHistoryTempBasal(tempBasal);
                return new PumpEnactResult().success(true).enacted(true);
            } else {
                return new PumpEnactResult().success(false).enacted(false);
            }
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

        if (pump.initialized && pumpHistoryLastChecked + 15 * 60 * 1000 < System.currentTimeMillis()) {
            checkPumpHistory();
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
                    activeAlert.warningCode == PumpWarningCodes.BATTERY_LOW)) {
                // turn benign warnings into notifications
                notifyAboutPumpWarning(activeAlert);
                ruffyScripter.confirmAlert(activeAlert.warningCode);
            } else {
                Notification notification = new Notification();
                notification.date = new Date();
                notification.id = Notification.COMBO_PUMP_ALARM;
                notification.level = Notification.URGENT;
                notification.text = MainApp.sResources.getString(R.string.combo_is_in_error_state);
                MainApp.bus().post(new EventNewNotification(notification));
                return preCheckResult.success(false);
            }
        }

        // check if TBR was cancelled or set by user
        boolean mismatch = checkForTbrMismatch(preCheckResult.state);
        if (mismatch) checkPumpHistory();

        // raise notification if clock is off (setting clock is not supported by ruffy)
        if (preCheckResult.state.pumpTimeMinutesOfDay != 0) {
            Date now = new Date();
            int minutesOfDayNow = now.getHours() * 60 + now.getMinutes();
            if ((Math.abs(preCheckResult.state.pumpTimeMinutesOfDay - minutesOfDayNow) > 3)) {
                Notification notification = new Notification(Notification.COMBO_PUMP_ALARM, MainApp.sResources.getString(R.string.combo_notification_check_time_date), Notification.NORMAL);
                MainApp.bus().post(new EventNewNotification(notification));
//                    runCommand("Updating pump clock", 2, ruffyScripter::setDateAndTime);
            }
        }

        return null;
    }

    private void notifyAboutPumpWarning(WarningOrErrorCode activeAlert) {
        if (activeAlert.warningCode == null || (activeAlert.warningCode.equals(PumpWarningCodes.CARTRIDGE_LOW) && activeAlert.warningCode.equals(PumpWarningCodes.BATTERY_LOW))) {
            throw new IllegalArgumentException(activeAlert.toString());
        }
        Notification notification = new Notification();
        notification.date = new Date();
        notification.id = Notification.COMBO_PUMP_ALARM;
        notification.level = Notification.NORMAL;
        notification.text = activeAlert.warningCode == PumpWarningCodes.CARTRIDGE_LOW
                ? MainApp.sResources.getString(R.string.combo_pump_cartridge_low_warrning)
                : MainApp.sResources.getString(R.string.combo_pump_battery_low_warrning);
        MainApp.bus().post(new EventNewNotification(notification));
    }

    private void checkForUnsafeUsage(CommandResult commandResult) {
        long lastViolation = 0;
        if (commandResult.state.unsafeUsageDetected) {
            lastViolation = System.currentTimeMillis();
        }
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
            if (closedLoopDisabledUntil > System.currentTimeMillis() && violationWarningRaisedFor != closedLoopDisabledUntil) {
                Notification n = new Notification(Notification.COMBO_PUMP_ALARM,
                        MainApp.sResources.getString(R.string.combo_force_disabled_notification),
                        Notification.URGENT);
                n.soundId = R.raw.alarm;
                MainApp.bus().post(new EventNewNotification(n));
                violationWarningRaisedFor = closedLoopDisabledUntil;
            }
        }
    }

    /**
     * Checks the main screen to determine if TBR on pump matches app state.
     */
    private boolean checkForTbrMismatch(PumpState state) {
        TemporaryBasal aapsTbr = MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis());
        boolean sync = false;
        if (aapsTbr == null && state.tbrActive && state.tbrRemainingDuration <= 2) {
            // pump runs TBR AAPS is unaware off
            log.debug("Pump runs TBR AAPS is unaware of, cancelling TBR so it can be read from history properly");
            runCommand(null, 0, ruffyScripter::cancelTbr);
            sync = true;
        } else if (aapsTbr != null && aapsTbr.getPlannedRemainingMinutes() > 2 && !state.tbrActive) {
            // AAPS has a TBR but the pump isn't running a TBR
            log.debug("AAPS shows TBR but pump isn't running a TBR; deleting TBR in AAPS and reading pump history");
            MainApp.getDbHelper().delete(aapsTbr);
            sync = true;
        } else if (aapsTbr != null && state.tbrActive) {
            // both AAPS and pump have a TBR ...
            if (aapsTbr.percentRate != state.tbrPercent) {
                // ... but they have different percentages
                log.debug("TBR percentage differs between AAPS and pump; deleting TBR in AAPS and reading pump history");
                MainApp.getDbHelper().delete(aapsTbr);
                sync = true;
            }
            int durationDiff = Math.abs(aapsTbr.getPlannedRemainingMinutes() - state.tbrRemainingDuration);
            if (durationDiff > 2) {
                // ... but they have different runtimes
                log.debug("TBR duration differs between AAPS and pump; deleting TBR in AAPS and reading pump history");
                MainApp.getDbHelper().delete(aapsTbr);
                sync = true;
            }
        }

        if (sync) {
            log.debug("Sync requested from checkTbrMismatch");
        }

        return sync;
    }

    /**
     * Checks if there are any changes on the pump not in the DB yet and if so, issues a history
     * read to get the DB up to date.
     */
    private synchronized boolean checkPumpHistory() {
        // set here, rather at the end so that if this runs into an error it's not tried
        // over and over since it's called at the end of runCommand
        pumpHistoryLastChecked = System.currentTimeMillis();

        CommandResult commandResult = runCommand(MainApp.sResources.getString(R.string.combo_pump_action_checking_history), 3, () ->
                ruffyScripter.readHistory(
                        new PumpHistoryRequest()
                                .bolusHistory(PumpHistoryRequest.LAST)
                                .tbrHistory(PumpHistoryRequest.LAST)));

        if (!commandResult.success || commandResult.history == null) {
            return false;
        }

        PumpHistoryRequest request = new PumpHistoryRequest();

        // note: sync only ever happens one way from pump to aaps;
        // db records are only added after delivering a bolus and confirming them via pump history
        // so whatever is in the DB is either right, added by the user (possibly injecton)
        // so don't delete records, only get the last from the pump and check if that one is in
        // the DB (valid or note)

        // last bolus
        Bolus pumpBolus = null;
        List<Bolus> bolusHistory = commandResult.history.bolusHistory;
        if (!bolusHistory.isEmpty()) {
            pumpBolus = bolusHistory.get(0);
        }

        Treatment aapsBolus = null;
        if (pumpBolus != null) {
            aapsBolus = MainApp.getDbHelper().getTreatmentByDate(pumpBolus.timestamp);
        }

        // there's a pump bolus AAPS doesn't know, or we only know one within the same minute but different amount
        if (pumpBolus != null && (aapsBolus == null || Math.abs(aapsBolus.insulin - pumpBolus.amount) >= 0.01)) {
            log.debug("Last bolus on pump is unknown, refreshing bolus history");
            request.bolusHistory = aapsBolus == null ? PumpHistoryRequest.FULL : aapsBolus.date;
        }

        // last TBR (TBRs are added to history upon completion so here we don't need to care about TBRs cancelled
        //           by the user, checkTbrMismatch() takes care of that)
        Tbr pumpTbr = null;
        List<Tbr> tbrHistory = commandResult.history.tbrHistory;
        if (!tbrHistory.isEmpty()) {
            pumpTbr = tbrHistory.get(0);
        }

        TemporaryBasal aapsTbr = null;
        if (pumpTbr != null) {
            aapsTbr = MainApp.getDbHelper().getTemporaryBasalsDataByDate(pumpTbr.timestamp);
        }

        if (pumpTbr != null && (aapsTbr == null || pumpTbr.percent != aapsTbr.percentRate || pumpTbr.duration != aapsTbr.durationInMinutes)) {
            log.debug("Last TBR on pump is unknown, refreshing TBR history");
            request.tbrHistory = aapsTbr == null ? PumpHistoryRequest.FULL : aapsTbr.date;
        }

        if (request.bolusHistory != PumpHistoryRequest.SKIP
                || request.tbrHistory != PumpHistoryRequest.SKIP
                || request.pumpErrorHistory != PumpHistoryRequest.SKIP
                || request.tddHistory != PumpHistoryRequest.SKIP) {
            log.debug("History reads needed to get up to date: " + request);
            return readHistory(request);
        }

        return true;
    }

    /**
     * Reads the pump's history and updates the DB accordingly.
     */
    private boolean readHistory(final PumpHistoryRequest request) {
        CommandResult historyResult = runCommand(MainApp.sResources.getString(R.string.combo_activity_reading_pump_history), 3, () -> ruffyScripter.readHistory(request));
        if (!historyResult.success) {
            return false;
        }

        // update local cache
        PumpHistory history = historyResult.history;
        if (!history.bolusHistory.isEmpty()) {
            pump.lastHistoryBolusTime = history.bolusHistory.get(0).timestamp;
        }
        if (!history.tbrHistory.isEmpty()) {
            pump.lastHistoryTbrTime = history.tbrHistory.get(0).timestamp;
        }

        if (!history.pumpAlertHistory.isEmpty()) {
            pump.errorHistory = history.pumpAlertHistory;
        }
        if (!history.tddHistory.isEmpty()) {
            pump.tddHistory = history.tddHistory;
        }

        updateDbFromPumpHistory(history);

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

    void readAllPumpData() {
        readHistory(new PumpHistoryRequest()
                .bolusHistory(pump.lastHistoryBolusTime)
                .tbrHistory(pump.lastHistoryTbrTime)
                .pumpErrorHistory(PumpHistoryRequest.FULL)
                .tddHistory(PumpHistoryRequest.FULL));

        CommandResult reservoirResult = runCommand("Checking reservoir level", 2,
                ruffyScripter::readReservoirLevelAndLastBolus);
        if (!reservoirResult.success) {
            return;
        }

        CommandResult basalResult = runCommand("Reading basal profile", 2, ruffyScripter::readBasalProfile);
        if (!basalResult.success) {
            return;
        }

        pump.basalProfile = basalResult.basalProfile;
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
            pumpJson.put("reservoir", pump.reservoirLevel);

            JSONObject statusJson = new JSONObject();
            statusJson.put("status", getStateSummary());
            statusJson.put("timestamp", pump.lastSuccessfulCmdTime);
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
            if (ps.activeAlert != null && ps.activeAlert.errorCode != null) {
                extendedJson.put("ErrorCode", ps.activeAlert.errorCode);
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
    private long violationWarningRaisedFor = 0;

    @Override
    public boolean isLoopEnabled() {
        return closedLoopDisabledUntil < System.currentTimeMillis();
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
        return maxIob;
    }
}