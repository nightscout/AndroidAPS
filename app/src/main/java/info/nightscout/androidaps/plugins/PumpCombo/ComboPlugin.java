package info.nightscout.androidaps.plugins.PumpCombo;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import com.squareup.otto.Subscribe;

import org.json.JSONObject;
import org.monkey.d.ruffy.ruffy.driver.IRuffyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import de.jotomo.ruffyscripter.PumpState;
import de.jotomo.ruffyscripter.RuffyScripter;
import de.jotomo.ruffyscripter.commands.BolusCommand;
import de.jotomo.ruffyscripter.commands.CancelTbrCommand;
import de.jotomo.ruffyscripter.commands.CancellableBolusCommand;
import de.jotomo.ruffyscripter.commands.Command;
import de.jotomo.ruffyscripter.commands.CommandResult;
import de.jotomo.ruffyscripter.commands.GetPumpStateCommand;
import de.jotomo.ruffyscripter.commands.SetTbrCommand;
import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.PumpCombo.events.EventComboPumpUpdateGUI;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class ComboPlugin implements PluginBase, PumpInterface {
    public static final String COMBO_MAX_TEMP_PERCENT_SP = "combo_maxTempPercent";
    private static Logger log = LoggerFactory.getLogger(ComboPlugin.class);

    private boolean fragmentEnabled = false;
    private boolean fragmentVisible = false;

    private PumpDescription pumpDescription = new PumpDescription();

    private RuffyScripter ruffyScripter;
    private ServiceConnection mRuffyServiceConnection;

    private ComboPump pump = new ComboPump();

    private boolean ignoreLastSetTbrFailure = false;

    @Nullable
    private volatile BolusCommand runningBolusCommand;

    private static PumpEnactResult OPERATION_NOT_SUPPORTED = new PumpEnactResult();

    static {
        OPERATION_NOT_SUPPORTED.success = false;
        OPERATION_NOT_SUPPORTED.enacted = false;
        OPERATION_NOT_SUPPORTED.comment = "Requested operation not supported by pump";
    }

    public ComboPlugin() {
        definePumpCapabilities();
        MainApp.bus().register(this);
        bindRuffyService();
        startAlerter();
        ruffyScripter = new RuffyScripter();
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


        pumpDescription.isSetBasalProfileCapable = false; // TODO GL#14
        pumpDescription.basalStep = 0.01d;
        pumpDescription.basalMinimumRate = 0.0d;

        pumpDescription.isRefillingCapable = true;
    }

    public ComboPump getPump() {
        return pump;
    }

    /**
     * The alerter frequently checks the result of the last executed command via the lastCmdResult
     * field and shows a notification with sound and vibration if an error occurred.
     * More details on the error can then be looked up in the Combo tab.
     * <p>
     * The alarm is re-raised every 5 minutes for as long as the error persist. As soon
     * as a command succeeds no more new alerts are raised.
     */
    private void startAlerter() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Context context = MainApp.instance().getApplicationContext();
                NotificationManager mgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                int id = 1000;
                long lastAlarmTime = 0;
                while (true) {
                    Command localLastCmd = pump.lastCmd;
                    CommandResult localLastCmdResult = pump.lastCmdResult;
                    if (localLastCmdResult != null && !localLastCmdResult.success) {
                        long now = System.currentTimeMillis();
                        long fiveMinutesSinceLastAlarm = lastAlarmTime + (5 * 60 * 1000) + (15 * 1000);
                        boolean loopEnabled = ConfigBuilderPlugin.getActiveLoop() != null;
                        if (now > fiveMinutesSinceLastAlarm && loopEnabled
                                && !(SP.getBoolean(R.string.key_combo_ignore_transient_tbr_errors, false) && localLastCmd instanceof SetTbrCommand && ignoreLastSetTbrFailure)) {
                            log.error("Command failed: " + localLastCmd);
                            log.error("Command result: " + localLastCmdResult);
                            PumpState localPumpState = pump.state;
                            if (localPumpState != null && localPumpState.errorMsg != null) {
                                log.warn("Pump is in error state, displaying; " + localPumpState.errorMsg);
                            }
                            long[] vibratePattern = new long[]{1000, 2000, 1000, 2000, 1000};
                            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                            NotificationCompat.Builder notificationBuilder =
                                    new NotificationCompat.Builder(context)
                                            .setSmallIcon(R.drawable.notif_icon)
                                            .setSmallIcon(R.drawable.icon_bolus)
                                            .setContentTitle("Combo communication error")
                                            .setContentText(localLastCmdResult.message)
                                            .setPriority(NotificationCompat.PRIORITY_MAX)
                                            .setLights(Color.BLUE, 1000, 0)
                                            .setSound(uri)
                                            .setVibrate(vibratePattern);
                            mgr.notify(id, notificationBuilder.build());
                            lastAlarmTime = now;
                        } else {
                            // TODO would it be useful to have a 'last error' field in the ui showing the most recent
                            // failed command? the next command that runs successful with will override this error
                            log.warn("Pump still in error state, but alarm raised recently, so not triggering again: " + localLastCmdResult.message);
                            refreshDataFromPump("from Error Recovery");
                        }
                    }
                    SystemClock.sleep(5 * 1000);
                }
            }
        }, "combo-alerter").start();
    }

    private boolean bindRuffyService() {

        Context context = MainApp.instance().getApplicationContext();
        boolean boundSucceeded = false;

        try {
            Intent intent = new Intent()
                    .setComponent(new ComponentName(
                            // this must be the base package of the app (check package attribute in
                            // manifest element in the manifest file of the providing app)
                            "org.monkey.d.ruffy.ruffy",
                            // full path to the driver;
                            // in the logs this service is mentioned as (note the slash)
                            // "org.monkey.d.ruffy.ruffy/.driver.Ruffy";
                            // org.monkey.d.ruffy.ruffy is the base package identifier
                            // and /.driver.Ruffy the service within the package
                            "org.monkey.d.ruffy.ruffy.driver.Ruffy"
                    ));
            context.startService(intent);

            mRuffyServiceConnection = new ServiceConnection() {

                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    keepUnbound = false;
                    ruffyScripter.start(IRuffyService.Stub.asInterface(service));
                    log.debug("ruffy serivce connected");
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    // TODO stop?
                    log.debug("ruffy service disconnected");
                    // try to reconnect ruffy service unless unbind was explicitly requested
                    // via unbindRuffyService
                    if (!keepUnbound) {
                        SystemClock.sleep(250);
                        bindRuffyService();
                    }
                }
            };
            boundSucceeded = context.bindService(intent, mRuffyServiceConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            log.error("Binding to ruffy service failed", e);
        }

        if (!boundSucceeded) {
            pump.stateSummary = "No connection to ruffy. Pump control unavailable.";
        }
        return true;
    }

    private boolean keepUnbound = false;

    private void unbindRuffyService() {
        keepUnbound = true;
        ruffyScripter.unbind();
        MainApp.instance().getApplicationContext().unbindService(mRuffyServiceConnection);
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
        // consider initialized when the pump's state was initially fetched,
        // after that lastCmd* variables will have values
        return pump.lastCmdTime.getTime() > 0;
    }

    @Override
    public boolean isSuspended() {
        return pump.state != null && pump.state.suspended;
    }

    @Override
    public boolean isBusy() {
        return ruffyScripter.isPumpBusy();
    }

    // TODO
    @Override
    public int setNewBasalProfile(Profile profile) {
        return FAILED;
    }

    // TODO
    @Override
    public boolean isThisProfileSet(Profile profile) {
        return false;
    }

    @Override
    public Date lastDataTime() {
        return pump.lastCmdTime;
    }

    // this method is regularly called from info.nightscout.androidaps.receivers.KeepAliveReceiver
    @Override
    public void refreshDataFromPump(String reason) {
        log.debug("RefreshDataFromPump called");

        // if Android is sluggish this might get called before ruffy is bound
        if (!ruffyScripter.isRunning()) {
            log.warn("Rejecting call to RefreshDataFromPump: scripter not ready yet.");
            return;
        }

        boolean notAUserRequest = !reason.toLowerCase().contains("user");
        boolean wasRunAtLeastOnce = pump.lastCmdTime.getTime() > 0;
        boolean ranWithinTheLastMinute = System.currentTimeMillis() < pump.lastCmdTime.getTime() + 60 * 1000;
        if (notAUserRequest && wasRunAtLeastOnce && ranWithinTheLastMinute) {
            log.debug("Not fetching state from pump, since we did already within the last 60 seconds");
        } else {
            runCommand(new GetPumpStateCommand());
        }
    }

    // TODO uses profile values for the time being
    // this get's called mulitple times a minute, must absolutely be cached
    @Override
    public double getBaseBasalRate() {
        Profile profile = MainApp.getConfigBuilder().getProfile();
        Double basal = profile.getBasal();
        log.trace("getBaseBasalrate returning " + basal);
        return basal;
    }

    private static CancellableBolusCommand.ProgressReportCallback bolusProgressReportCallback =
            new CancellableBolusCommand.ProgressReportCallback() {
        @Override
        public void report(CancellableBolusCommand.ProgressReportCallback.State state, int percent, double delivered) {
            EventOverviewBolusProgress enent = EventOverviewBolusProgress.getInstance();
            switch (state) {
                case DELIVERING:
                    enent.status = String.format(MainApp.sResources.getString(R.string.bolusdelivering), delivered);
                    break;
                case DELIVERED:
                    enent.status = String.format(MainApp.sResources.getString(R.string.bolusdelivered), delivered);
                    break;
                case STOPPING:
                    enent.status = MainApp.sResources.getString(R.string.bolusstopping);
                    break;
                case STOPPED:
                    enent.status = MainApp.sResources.getString(R.string.bolusstopped);
                    break;
            }
            enent.percent = percent;
            MainApp.bus().post(enent);
        }
    };

    /** Updates Treatment records with carbs and boluses and delivers a bolus if needed */
    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
            if (detailedBolusInfo.insulin > 0) {
                // bolus needed, ask pump to deliver it
                if (!SP.getBoolean(R.string.key_combo_enable_experimental_split_bolus, false)) {
                    return deliverBolus(detailedBolusInfo);
                } else {
                    // split up bolus into 2 U parts
                    PumpEnactResult pumpEnactResult = new PumpEnactResult();
                    pumpEnactResult.success = true;
                    pumpEnactResult.enacted = true;
                    pumpEnactResult.bolusDelivered = 0d;
                    pumpEnactResult.carbsDelivered = detailedBolusInfo.carbs;

                    double remainingBolus = detailedBolusInfo.insulin;
                    int split = 1;
                    while (remainingBolus > 0.05) {
                        double bolus = remainingBolus > 2 ? 2 : remainingBolus;
                        DetailedBolusInfo bolusInfo = new DetailedBolusInfo();
                        bolusInfo.insulin = bolus;
                        bolusInfo.isValid = false;
                        log.debug("Delivering split bolus #" + split + " with " + bolus + " U");
                        PumpEnactResult bolusResult = deliverBolus(bolusInfo);
                        if (!bolusResult.success) {
                            return bolusResult;
                        }
                        pumpEnactResult.bolusDelivered += bolus;
                        remainingBolus -= 2;
                        split++;
                        // Programming the pump for 2 U takes ~20, so wait 20s more so the
                        // boluses are spaced 40s apart.
                        SystemClock.sleep(20 * 1000);
                    }
                    MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
                    return pumpEnactResult;
                }
            } else {
                // no bolus required, carb only treatment

                // TODO the ui freezes when the calculator issues a carb-only treatment
                // so just wait, yeah, this is dumb. for now; proper fix via GL#10
                // info.nightscout.androidaps.plugins.Overview.Dialogs.BolusProgressDialog.scheduleDismiss()
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
        } else {
            // neither carbs nor bolus requested
            PumpEnactResult pumpEnactResult = new PumpEnactResult();
            pumpEnactResult.success = false;
            pumpEnactResult.enacted = false;
            pumpEnactResult.bolusDelivered = 0d;
            pumpEnactResult.carbsDelivered = 0d;
            pumpEnactResult.comment = MainApp.instance().getString(R.string.danar_invalidinput);
            log.error("deliverTreatment: Invalid input");
            return pumpEnactResult;
        }
    }

    @NonNull
    private PumpEnactResult deliverBolus(DetailedBolusInfo detailedBolusInfo) {
        runningBolusCommand = SP.getBoolean(R.string.key_combo_enable_experimental_bolus, false)
                ? new CancellableBolusCommand(detailedBolusInfo.insulin, bolusProgressReportCallback)
                : new BolusCommand(detailedBolusInfo.insulin);
        CommandResult bolusCmdResult = runCommand(runningBolusCommand);
        PumpEnactResult pumpEnactResult = new PumpEnactResult();
        pumpEnactResult.success = bolusCmdResult.success;
        pumpEnactResult.enacted = bolusCmdResult.enacted;
        pumpEnactResult.comment = bolusCmdResult.message;

        // if enacted, add bolus and carbs to treatment history
        if (pumpEnactResult.enacted) {
            // TODO if no error occurred, the requested bolus is what the pump delievered,
            // that has been checked. If an error occurred, we should check how much insulin
            // was delivered, e.g. when the cartridge went empty mid-bolus
            // For the first iteration, the alert the pump raises must suffice
            pumpEnactResult.bolusDelivered = detailedBolusInfo.insulin;
            pumpEnactResult.carbsDelivered = detailedBolusInfo.carbs;

            detailedBolusInfo.date = bolusCmdResult.completionTime;
            MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
        } else {
            pumpEnactResult.bolusDelivered = 0d;
            pumpEnactResult.carbsDelivered = 0d;
        }
        return pumpEnactResult;
    }

    @Override
    public void stopBolusDelivering() {
        BolusCommand localRunningBolusCommand = runningBolusCommand;
        if (localRunningBolusCommand != null) localRunningBolusCommand.requestCancellation();
    }

    private CommandResult runCommand(Command command) {
        if (ruffyScripter == null) {
            String msg = "No connection to ruffy. Pump control not available.";
            pump.stateSummary = msg;
            return new CommandResult().message(msg);
        }

        pump.stateSummary = "Executing " + command;
        MainApp.bus().post(new EventComboPumpUpdateGUI());

        CommandResult commandResult = ruffyScripter.runCommand(command);
        log.debug("RuffyScripter returned from command invocation, result: " + commandResult);
        if (commandResult.exception != null) {
            log.error("Exception received from pump", commandResult.exception);
        }

        // error tolerance
        if (commandResult.success) ignoreLastSetTbrFailure = false;

        if (command instanceof SetTbrCommand) {
            if (!commandResult.success && !ignoreLastSetTbrFailure) {
                // ignore this once
                ignoreLastSetTbrFailure = true;
            } else {
                // second failure in a row
                ignoreLastSetTbrFailure = false;
            }
        }

        pump.lastCmd = command;
        pump.lastCmdTime = new Date();
        pump.lastCmdResult = commandResult;
        pump.state = commandResult.state;

        if (commandResult.success && commandResult.state.suspended) {
            pump.stateSummary = "Suspended";
        } else if (commandResult.success) {
            pump.stateSummary = "Idle";
        } else {
            pump.stateSummary = "Error";
        }

        MainApp.bus().post(new EventComboPumpUpdateGUI());
        return commandResult;
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
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes) {
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

        CommandResult commandResult = runCommand(new SetTbrCommand(adjustedPercent, durationInMinutes));

        if (commandResult.enacted) {
            TemporaryBasal tempStart = new TemporaryBasal(commandResult.completionTime);
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
        pumpEnactResult.comment = commandResult.message;
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
            commandResult = runCommand(new CancelTbrCommand());
            if (commandResult.enacted) {
                tempBasal = new TemporaryBasal(commandResult.completionTime);
                tempBasal.durationInMinutes = 0;
                tempBasal.source = Source.USER;
                pumpEnactResult.isTempCancel = true;
            }
        } else if ((activeTemp.percentRate >= 90 && activeTemp.percentRate <= 110) && activeTemp.getPlannedRemainingMinutes() <= 15) {
            // Let fake neutral temp keep running (see below)
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
            long percentage = (activeTemp.percentRate > 100) ? 110 : 90;
            log.debug("cancelTempBasal: changing tbr to " + percentage + "% for 15 mins.");
            commandResult = runCommand(new SetTbrCommand(percentage, 15));
            if (commandResult.enacted) {
                tempBasal = new TemporaryBasal(commandResult.completionTime);
                tempBasal.durationInMinutes = 15;
                tempBasal.source = Source.USER;
                tempBasal.percentRate = (int) percentage;
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
            pumpEnactResult.comment = commandResult.message;
        }
        return pumpEnactResult;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        return OPERATION_NOT_SUPPORTED;
    }

    // Returns the state of the pump as it was received during last pump comms.
    // TODO v2 add battery, reservoir info when we start reading that and clean up the code
    @Override
    public JSONObject getJSONStatus() {
        if (true) { //pump.lastCmdTime.getTime() + 5 * 60 * 1000L < System.currentTimeMillis()) {
            return null;
        }

        try {
            JSONObject pumpJson = new JSONObject();
            JSONObject statusJson = new JSONObject();
            JSONObject extendedJson = new JSONObject();
            statusJson.put("status", pump.stateSummary);
            extendedJson.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            try {
                extendedJson.put("ActiveProfile", MainApp.getConfigBuilder().getProfileName());
            } catch (Exception e) {
            }
            statusJson.put("timestamp", pump.lastCmdTime);

            PumpState ps = pump.state;
            if (ps != null) {
                if (ps.tbrActive) {
                    extendedJson.put("TempBasalAbsoluteRate", ps.tbrRate);
                    extendedJson.put("TempBasalPercent", ps.tbrPercent);
                    extendedJson.put("TempBasalRemaining", ps.tbrRemainingDuration);
                }
                if (ps.errorMsg != null) {
                    extendedJson.put("ErrorMessage", ps.errorMsg);
                }
            }

// more info here .... look at dana plugin

            pumpJson.put("status", statusJson);
            pumpJson.put("extended", extendedJson);
            pumpJson.put("clock", DateUtil.toISOString(pump.lastCmdTime));

            return pumpJson;
        } catch (Exception e) {
            log.warn("Failed to gather device status for upload", e);
        }

        return null;
    }

    // TODO
    @Override
    public String deviceID() {
// Serial number here
        return "Combo";
    }

    @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @Override
    public String shortStatus(boolean veryShort) {
        // TODO trim for wear if veryShort==true
        return pump.stateSummary;
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @SuppressWarnings("UnusedParameters")
    @Subscribe
    public void onStatusEvent(final EventAppExit ignored) {
        unbindRuffyService();
    }
}