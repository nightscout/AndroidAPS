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

import org.json.JSONException;
import org.json.JSONObject;
import org.monkey.d.ruffy.ruffy.driver.IRuffyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import de.jotomo.ruffyscripter.RuffyScripter;
import de.jotomo.ruffyscripter.commands.BolusCommand;
import de.jotomo.ruffyscripter.commands.CancelTbrCommand;
import de.jotomo.ruffyscripter.commands.Command;
import de.jotomo.ruffyscripter.commands.CommandResult;
import de.jotomo.ruffyscripter.commands.DetermineCapabilitiesCommand;
import de.jotomo.ruffyscripter.commands.ReadPumpStateCommand;
import de.jotomo.ruffyscripter.commands.SetTbrCommand;
import de.jotomo.ruffyscripter.PumpState;
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
import info.nightscout.androidaps.plugins.PumpCombo.events.EventComboPumpUpdateGUI;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.ToastUtils;

/**
 * Created by mike on 05.08.2016.
 */
public class ComboPlugin implements PluginBase, PumpInterface {
    private static Logger log = LoggerFactory.getLogger(ComboPlugin.class);

    private boolean fragmentEnabled = false;
    private boolean fragmentVisible = false;

    private PumpDescription pumpDescription = new PumpDescription();

    private RuffyScripter ruffyScripter;
    private ServiceConnection mRuffyServiceConnection;

    // package-protected only so ComboFragment can access these
    @NonNull
    volatile String statusSummary = "Initializing";
    @Nullable
    volatile Command lastCmd;
    @Nullable
    volatile CommandResult lastCmdResult;
    @NonNull
    volatile Date lastCmdTime = new Date(0);
    volatile PumpState pumpState = new PumpState();

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
    }

    private void definePumpCapabilities() {
        pumpDescription.isBolusCapable = true;
        pumpDescription.bolusStep = 0.1d;

        pumpDescription.isExtendedBolusCapable = false; // TODO
        pumpDescription.extendedBolusStep = 0.1d;
        pumpDescription.extendedBolusDurationStep = 15;
        pumpDescription.extendedBolusMaxDuration = 12 * 60;

        pumpDescription.isTempBasalCapable = true;
        pumpDescription.tempBasalStyle = PumpDescription.PERCENT;

        pumpDescription.maxTempPercent = 500;
        pumpDescription.tempPercentStep = 10;

        pumpDescription.tempDurationStep = 15;
        pumpDescription.tempMaxDuration = 24 * 60;


        pumpDescription.isSetBasalProfileCapable = false; // TODO
        pumpDescription.basalStep = 0.01d;
        pumpDescription.basalMinimumRate = 0.0d;

        pumpDescription.isRefillingCapable = true;
    }

    /**
     * The alerter frequently checks the result of the last executed command via the lastCmdResult
     * field and shows a notification with sound and vibration if an error occurred.
     * More details on the error can then be looked up in the Combo tab.
     *
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
                    Command localLastCmd = lastCmd;
                    CommandResult localLastCmdResult = lastCmdResult;
                    if (localLastCmdResult != null && !localLastCmdResult.success) {
                        long now = System.currentTimeMillis();
                        long fiveMinutesSinceLastAlarm = lastAlarmTime + (5 * 60 * 1000) + (15 * 1000);
                        if (now > fiveMinutesSinceLastAlarm) {
                            log.error("Command failed: " + localLastCmd);
                            log.error("Command result: " + localLastCmdResult);
                            PumpState localPumpState = pumpState;
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
                            log.warn("Pump still in error state, but alarm raised recently, so not triggering again: " + localLastCmdResult.message);
                        }
                    }
                    SystemClock.sleep(5 * 1000);
                }
            }
        }, "combo-alerter").start();
    }

    private void bindRuffyService() {
        Context context = MainApp.instance().getApplicationContext();
        boolean boundSucceeded = false;

        try {
            Intent intent = new Intent()
                    .setComponent(new ComponentName(
                            // this must be the base package of the app (check package attribute in
                            // manifest element in the manifest file of the providing app)
                            "org.monkey.d.ruffy.ruffy",
                            // full path to the driver
                            // in the logs this service is mentioned as (note the slash)
                            // "org.monkey.d.ruffy.ruffy/.driver.Ruffy"
                            "org.monkey.d.ruffy.ruffy.driver.Ruffy"
                    ));
            context.startService(intent);

            mRuffyServiceConnection = new ServiceConnection() {

                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    ruffyScripter = new RuffyScripter(IRuffyService.Stub.asInterface(service));
                    log.debug("ruffy serivce connected");
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    log.debug("ruffy service disconnected");
                }
            };
            boundSucceeded = context.bindService(intent, mRuffyServiceConnection, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            log.error("Binding to ruffy service failed", e);
        }

        if (!boundSucceeded) {
            statusSummary = "No connection to ruffy. Pump control not available.";
        }
    }

    private void unbindRuffyService() {
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
        return lastCmdTime.getTime() > 0;
    }

    @Override
    public boolean isSuspended() {
        return pumpState != null && pumpState.suspended;
    }

    @Override
    public boolean isBusy() {
        return ruffyScripter == null || ruffyScripter.isPumpBusy();
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
        return lastCmdTime;
    }

    // this method is regularly called from info.nightscout.androidaps.receivers.KeepAliveReceiver
    @Override
    public void refreshDataFromPump(String reason) {
        log.debug("RefreshDataFromPump called");

        // if Android is sluggish this might get called before ruffy is bound
        if (ruffyScripter == null) {
            log.warn("Rejecting call to RefreshDataFromPump: ruffy service not bound (yet)");
            return;
        }

        if (!reason.toLowerCase().contains("user")
                && lastCmdTime.getTime() > 0
                && System.currentTimeMillis() > lastCmdTime.getTime() + 60 * 1000) {
            log.debug("Not fetching state from pump, since we did already within the last 60 seconds");
        } else {
            runCommand(new ReadPumpStateCommand());
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

    // what a mess: pump integration code reading carb info from Detailed**Bolus**Info,
    // writing carb treatments to the history table. What's PumpEnactResult for again?
    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
            if (detailedBolusInfo.insulin > 0) {
                // bolus needed, ask pump to deliver it
                CommandResult bolusCmdResult = runCommand(new BolusCommand(detailedBolusInfo.insulin));
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

    private CommandResult runCommand(Command command) {
        if (ruffyScripter == null) {
            String msg = "No connection to ruffy. Pump control not available.";
            statusSummary = msg;
            return new CommandResult().message(msg);
        }

        statusSummary = "Executing " + command;
        MainApp.bus().post(new EventComboPumpUpdateGUI());

        CommandResult commandResult = ruffyScripter.runCommand(command);
        if (!commandResult.success && commandResult.exception != null) {
            log.error("CommandResult has exception, rebinding ruffy service", commandResult.exception);

            // attempt to rebind the ruffy service, will start ruffy again if it crashed
            try {
                unbindRuffyService();
                SystemClock.sleep(5000);
                bindRuffyService();
                SystemClock.sleep(5000);
            } catch (Exception e) {
                String msg = "No connection to ruffy. Pump control not available.";
                statusSummary = msg;
                return new CommandResult().message(msg);
            }

            if (ruffyScripter == null) {
                log.error("Rebinding failed");
            } else if (!commandResult.enacted && !(command instanceof BolusCommand)) {
                // retry command, but make sure it wasn't enacted and don't retry
                // bolus commands (user is interacting with AAPS right now so he can
                // deal with it and we don't want to deliver a bolus twice)
                CommandResult retriedCommandResult = ruffyScripter.runCommand(command);
                if (retriedCommandResult.success) {
                    commandResult = retriedCommandResult;
                }
            }
        }

        log.debug("RuffyScripter returned from command invocation, result: " + commandResult);
        if (commandResult.exception != null) {
            log.error("Exception received from pump", commandResult.exception);
        }

        lastCmd = command;
        lastCmdTime = new Date();
        lastCmdResult = commandResult;
        pumpState = commandResult.state;

        if (commandResult.success && commandResult.state.suspended) {
            statusSummary = "Suspended";
        } else if (commandResult.success) {
            statusSummary = "Idle";
        } else {
            statusSummary = "Error";
        }

        MainApp.bus().post(new EventComboPumpUpdateGUI());
        return commandResult;
    }

    @Override
    public void stopBolusDelivering() {
        // there's no way to stop the combo once delivery has started
        // but before that, we could interrupt the command thread ... pause
        // till pump times out or raises an error
    }

    // Note: AAPS calls this only to enact OpenAPS recommendations
    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes) {
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
        if (percent % 10 != 0) {
            int rounded = percent;
            while (rounded % 10 != 0) rounded = rounded - 1;
            log.debug("Rounded requested percentage from " + percent + " to " + rounded);
            percent = rounded;
        }

        //FIXME maybe needs a flag in pump config?
        percent = percent > 500 ? 500 : percent;

        CommandResult commandResult = runCommand(new SetTbrCommand(percent, durationInMinutes));
        if (commandResult.enacted) {
            TemporaryBasal tempStart = new TemporaryBasal(commandResult.completionTime);
            // TODO commandResult.state.tbrRemainingDuration might already display 29 if 30 was set, since 29:59 is shown as 29 ...
            // we should check this, but really ... something must be really screwed up if that number was anything different
            tempStart.durationInMinutes = durationInMinutes;
            tempStart.percentRate = percent;
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
        pumpEnactResult.percent = percent;
        pumpEnactResult.duration = durationInMinutes;
        return pumpEnactResult;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        return OPERATION_NOT_SUPPORTED;
    }

    @Override
    public PumpEnactResult cancelTempBasal() {
        log.debug("cancelTempBasal called");
        CommandResult commandResult = runCommand(new CancelTbrCommand());
        if (commandResult.enacted) {
            TemporaryBasal tempStop = new TemporaryBasal(commandResult.completionTime);
            tempStop.durationInMinutes = 0; // ending temp basal
            tempStop.source = Source.USER;
            ConfigBuilderPlugin treatmentsInterface = MainApp.getConfigBuilder();
            treatmentsInterface.addToHistoryTempBasal(tempStop);
        }

        PumpEnactResult pumpEnactResult = new PumpEnactResult();
        pumpEnactResult.success = commandResult.success;
        pumpEnactResult.enacted = commandResult.enacted;
        pumpEnactResult.comment = commandResult.message;
        pumpEnactResult.isTempCancel = true;
        return pumpEnactResult;
    }

    // TODO
    @Override
    public PumpEnactResult cancelExtendedBolus() {
        return OPERATION_NOT_SUPPORTED;
    }

    // Returns the state of the pump as it was received during last pump comms.
    // TODO v2 add battery, reservoir info when we start reading that and clean up the code
    @Override
    public JSONObject getJSONStatus() {
        if (lastCmdTime.getTime() + 5 * 60 * 1000L < System.currentTimeMillis()) {
            return null;
        }

        try {
            JSONObject pump = new JSONObject();
            JSONObject status = new JSONObject();
            JSONObject extended = new JSONObject();
            status.put("status", statusSummary);
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            try {
                extended.put("ActiveProfile", MainApp.getConfigBuilder().getProfileName());
            } catch (Exception e) {
            }
            status.put("timestamp", lastCmdTime);

            PumpState ps = pumpState;
            if (ps != null) {
                if (ps.tbrActive) {
                    extended.put("TempBasalAbsoluteRate", ps.tbrRate);
                    extended.put("TempBasalPercent", ps.tbrPercent);
                    extended.put("TempBasalRemaining", ps.tbrRemainingDuration);
                }
                if (ps.errorMsg != null) {
                    extended.put("ErrorMessage", ps.errorMsg);
                }
            }

// more info here .... look at dana plugin

            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("clock", DateUtil.toISOString(lastCmdTime));

            return pump;
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
        return statusSummary;
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @SuppressWarnings("UnusedParameters")
    @Subscribe
    public void onStatusEvent(final EventAppExit e) {
        unbindRuffyService();
    }


    public void doTestAction() {
        ToastUtils.showToastInUiThread(MainApp.instance(), "TestAction called");

        // if Android is sluggish this might get called before ruffy is bound
        if (ruffyScripter == null) {
            log.warn("Rejecting call to RefreshDataFromPump: ruffy service not bound (yet)");
            ToastUtils.showToastInUiThread(MainApp.instance(), "Rejecting call to RefreshDataFromPump: ruffy service not bound (yet)");

            return;
        }
        CommandResult result = runCommand(new DetermineCapabilitiesCommand());
        if (result.success){
            ToastUtils.showToastInUiThread(MainApp.instance(), "max%: " + result.capabilities.maxTempPercent);
        } else {
            ToastUtils.showToastInUiThread(MainApp.instance(), "No success with test Command.");
        }
    }


}


// If you want update fragment call
//        MainApp.bus().post(new EventComboPumpUpdateGUI());
// fragment should fetch data from plugin and display status, buttons etc ...
