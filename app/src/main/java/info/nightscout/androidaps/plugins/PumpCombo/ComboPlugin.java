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
import de.jotomo.ruffyscripter.commands.ReadPumpStateCommand;
import de.jotomo.ruffyscripter.commands.SetTbrCommand;
import de.jotomo.ruffyscripter.PumpState;
import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
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
    String statusSummary = "Initializing";
    @Nullable
    Command lastCmd;
    @Nullable
    CommandResult lastCmdResult;
    @NonNull
    Date lastCmdTime = new Date(0);
    @NonNull
    PumpState pumpState = new PumpState();

    private static PumpEnactResult OPERATION_NOT_SUPPORTED = new PumpEnactResult();

    static {
        OPERATION_NOT_SUPPORTED.success = false;
        OPERATION_NOT_SUPPORTED.enacted = false;
        OPERATION_NOT_SUPPORTED.comment = "Requested operation not supported by pump";
    }

    public ComboPlugin() {
        definePumpCapabilities();
        MainApp.bus().register(this);
        startAlerter();
        bindRuffyService();
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

        pumpDescription.isRefillingCapable = false;
    }

    private void startAlerter() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Context context = MainApp.instance().getApplicationContext();
                NotificationManager mgr = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                int id = 1000;
                long lastAlarmTime = 0;
                while (true) {
                    if (lastCmdResult != null && !lastCmdResult.success) {
                        long now = System.currentTimeMillis();
                        long fiveMinutesSinceLastAlarm = lastAlarmTime + (5 * 60 * 1000) + (15 * 1000);
                        if (now > fiveMinutesSinceLastAlarm) {
                            log.error("Command failed: " + lastCmd);
                            log.error("Command result: " + lastCmdResult);
                            if (pumpState.errorMsg != null) {
                                log.warn("Pump is in error state, displayng; " + pumpState.errorMsg);
                            }
                            long[] vibratePattern = new long[]{1000, 1000, 1000, 1000, 1000};
                            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                            NotificationCompat.Builder notificationBuilder =
                                    new NotificationCompat.Builder(context)
                                            .setSmallIcon(R.drawable.notif_icon)
                                            .setSmallIcon(R.drawable.icon_bolus)
                                            .setContentTitle("Combo communication error")
                                            .setContentText(lastCmdResult.message)
                                            .setPriority(NotificationCompat.PRIORITY_MAX)
                                            .setLights(Color.BLUE, 1000, 0)
                                            .setSound(uri)
                                            .setVibrate(vibratePattern);
                            mgr.notify(id, notificationBuilder.build());
                            lastAlarmTime = now;
                        } else {
                            log.warn("Pump still in error state, but alarm raised recently, so not triggering again: " + lastCmdResult.message);
                        }
                    }
                    SystemClock.sleep(5 * 1000);
                }
            }
        }, "combo-alerter").start();
    }

    private void bindRuffyService() {
        Context context = MainApp.instance().getApplicationContext();

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

        boolean success = context.bindService(intent, mRuffyServiceConnection, Context.BIND_AUTO_CREATE);
        if (!success) {
            log.error("Binding to ruffy service failed");
            // AAPS will still crash since it continues trying to access the pump, even when isInitalized
            // returns false and isBusy returns true; this will however not crash the alerter
            // which will raise an exception. Not really ideal.
            statusSummary = "Failed to bind to ruffy service";
        }
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
        return pumpState.suspended;
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
        return lastCmdTime;
    }

    // this method is regularly called from info.nightscout.androidaps.receivers.KeepAliveReceiver
    @Override
    public void refreshDataFromPump(String reason) {
        log.debug("RefreshDataFromPump called");

        // if Android is sluggish this might get called before ruffy is bound
        if (ruffyScripter == null) {
            log.debug("Rejecting call to RefreshDataFromPump: ruffy service not bound yet");
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
                PumpEnactResult result = new PumpEnactResult();
                result.success = bolusCmdResult.success;
                result.enacted = bolusCmdResult.enacted;
                result.comment = bolusCmdResult.message;

                // if enacted, add bolus and carbs to treatment history
                if (result.enacted) {
                    // TODO if no error occurred, the requested bolus is what the pump delievered,
                    // that has been checked. If an error occurred, we should check how much insulin
                    // was delivered, e.g. when the cartridge went empty mid-bolus
                    // For the first iteration, the alert the pump raises must suffice
                    result.bolusDelivered = detailedBolusInfo.insulin;
                    result.carbsDelivered = detailedBolusInfo.carbs;

                    detailedBolusInfo.date = bolusCmdResult.completionTime;
                    MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
                } else {
                    result.bolusDelivered = 0d;
                    result.carbsDelivered = 0d;
                }
                return result;
            } else {
                // no bolus required

                // TODO the ui freezes when the calculator issues a carb-only treatment
                // so just wait, yeah, this is dumb. for now; proper fix via GL#10
                // info.nightscout.androidaps.plugins.Overview.Dialogs.BolusProgressDialog.scheduleDismiss()
                SystemClock.sleep(6000);
                PumpEnactResult result = new PumpEnactResult();
                result.success = true;
                result.enacted = true;
                result.bolusDelivered = 0d;
                result.carbsDelivered = detailedBolusInfo.carbs;
                result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
                return result;
            }
        } else {
            PumpEnactResult result = new PumpEnactResult();
            result.success = false;
            result.enacted = false;
            result.bolusDelivered = 0d;
            result.carbsDelivered = 0d;
            result.comment = MainApp.instance().getString(R.string.danar_invalidinput);
            log.error("deliverTreatment: Invalid input");
            return result;
        }
    }

    private CommandResult runCommand(Command command) {
        statusSummary = "Executing " + command;
        MainApp.bus().post(new EventComboPumpUpdateGUI());

        CommandResult commandResult = ruffyScripter.runCommand(command);
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
        /* GL jotomo/AndroidAPS#18
        int stepSize = pumpDescription.tempDurationStep;
        if (durationInMinutes > stepSize) {
            log.debug("Reducing requested duration of " + durationInMinutes + "m to minimal duration supported by the pump: " + stepSize + "m");
            durationInMinutes = stepSize;
        }
        */
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
        // TODO experimenting .... with the BT connect costing time, this leads to the TBR
        // still running on the last minute when OpenAPS is run again, which will NOT enact
        // anything as it sees a TBR is still running. So fake that the TBR has started earlier
        // and is assumed to be over already. With the Combo, setting a TBR while an existing TBR
        // is still running is a no-issue: the new TBR simply overrides the existing.

        // can this lead to errors where AAPS tries to cancel a TBR that isn't running? ...
        // hm, TBRs on teh pump run 20s later, so no, it'd have to be the other way round. sounds good.


        // TODO check: if new TBR overrides existing one: who makes call to TempBasel end???

        long tbrStart = System.currentTimeMillis();
        // TODO DanaR sets tempStart to now -1 to acound for delay from pump?

        CommandResult commandResult = runCommand(new SetTbrCommand(percent, durationInMinutes));
        if (commandResult.enacted) {
            // make sure we're not skipping a loop iteration by a few secs
            TemporaryBasal tempStart = new TemporaryBasal(commandResult.completionTime - 5_000);
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

    // TODO
    // cache as much as possible - every time we interact with the pump it vibrates at the end
    @Override
    public JSONObject getJSONStatus() {
        /// TODO not doing that just yet
        if (1 == 1) return null;
        JSONObject pump = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            status.put("status", statusSummary);
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            try {
                extended.put("ActiveProfile", MainApp.getConfigBuilder().getProfileName());
            } catch (Exception e) {
            }
            status.put("timestamp", lastCmdTime);

            PumpState ps = this.pumpState;
            if (ps != null) {
                extended.put("TempBasalAbsoluteRate", ps.tbrRate);
                // TODO best guess at this point ...
                extended.put("TempBasalStart", DateUtil.dateAndTimeString(System.currentTimeMillis() - (ps.tbrRemainingDuration - 15 * 60 * 1000)));
                extended.put("TempBasalRemaining", ps.tbrRemainingDuration);
            }

// more info here .... look at dana plugin

            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("clock", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
        }
        return pump;
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
        MainApp.instance().getApplicationContext().unbindService(mRuffyServiceConnection);
    }
}


// If you want update fragment call
//        MainApp.bus().post(new EventComboPumpUpdateGUI());
// fragment should fetch data from plugin and display status, buttons etc ...
