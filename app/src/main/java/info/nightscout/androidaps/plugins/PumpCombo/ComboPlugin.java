package info.nightscout.androidaps.plugins.PumpCombo;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;

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
import de.jotomo.ruffyscripter.commands.NoOpCommand;
import de.jotomo.ruffyscripter.commands.SetTbrCommand;
import de.jotomo.ruffyscripter.commands.PumpState;
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
    private Date lastCmdTime = new Date(0);
    private ServiceConnection mRuffyServiceConnection;

    @Nullable
    volatile PumpState pumpState;

    volatile String statusSummary = "Initializing";

    private static PumpEnactResult OPERATION_NOT_SUPPORTED = new PumpEnactResult();

    static {
        OPERATION_NOT_SUPPORTED.success = false;
        OPERATION_NOT_SUPPORTED.enacted = false;
        OPERATION_NOT_SUPPORTED.comment = "Requested operation not supported by pump";
    }

    public ComboPlugin() {
        definePumpCapabilities();
        bindRuffyService();
        MainApp.bus().register(this);
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
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        runCommand(new NoOpCommand());
                    }
                }).start();
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                log.debug("ruffy service disconnected");
            }
        };

        boolean success = context.bindService(intent, mRuffyServiceConnection, Context.BIND_AUTO_CREATE);
        if (!success) {
            log.error("Binding to ruffy service failed");
        }
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
        // TODO
        // hm, lastCmdDate > 0, like the DanaR does it?
        return true; // scripter does this as needed; ruffyScripter != null;
    }

    @Override
    public boolean isSuspended() {
        return false;
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

    // TODO
    @Override
    public void refreshDataFromPump(String reason) {
        log.debug("RefreshDataFromPump called");

        // this is called regulary from keepalive

        // TODO how often is this called? use this to run checks regularly, e.g.
        // recheck active TBR, basal rate to ensure nothing broke?
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
        log.debug("deliver treatment called with dbi: " + detailedBolusInfo);
        if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
            PumpEnactResult result = new PumpEnactResult();
            if (detailedBolusInfo.insulin > 0) {
                CommandResult bolusCmdResult = runCommand(new BolusCommand(detailedBolusInfo.insulin));
                result.success = bolusCmdResult.success;
                result.enacted = bolusCmdResult.enacted;
                // TODO if no error occurred, the requested bolus is what the pump delievered,
                // that has been checked. If an error occurred, we should check how much insulin
                // was delivered, e.g. when the cartridge went empty mid-bolus
                // For the first iteration, the alert the pump raises must suffice
                result.bolusDelivered = detailedBolusInfo.insulin;
                result.comment = bolusCmdResult.message;
            } else {
                // TODO the ui freezes when the calculator issues a carb-only treatment
                // so just wait, yeah, this is dumb. for now; proper fix via GL#10
                // info.nightscout.androidaps.plugins.Overview.Dialogs.BolusProgressDialog.scheduleDismiss()
                SystemClock.sleep(6000);
                result.success = true;
                result.enacted = true;
                result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
            }
            if (result.enacted) {
                result.carbsDelivered = detailedBolusInfo.carbs;
                if (Config.logPumpActions)
                    log.debug("deliverTreatment: OK. Asked: " + detailedBolusInfo.insulin + " Delivered: " + result.bolusDelivered);
                detailedBolusInfo.date = new Date().getTime();
                MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
            }
            return result;
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
        synchronized (this) {
            // TODO use this to dispatch methods to a service thread, like DanaRs executionService
            // will be required when doing multiple commands in sequence.
            // Alternatively provide 'composite commands' to return everything needed in one go?
            try {
                statusSummary = "Busy running " + command;
                pumpState = null;
                MainApp.bus().post(new EventComboPumpUpdateGUI());
                CommandResult commandResult = ruffyScripter.runCommand(command);
                if (commandResult.success && commandResult.state != null) {
                    pumpState = commandResult.state;
                }
                return commandResult;
            } finally {
                lastCmdTime = new Date();
                statusSummary = pumpState != null && !pumpState.isErrorOrWarning
                        ? "Idle"
                        : "Error: " + pumpState.errorMsg;
                ruffyScripter.disconnect();
                MainApp.bus().post(new EventComboPumpUpdateGUI());
            }
        }
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
        int activeTbrPercentage = pumpState != null ? pumpState.tbrPercent : 100;
        if (activeTbrPercentage != -1 && Math.abs(activeTbrPercentage - roundedPercentage) <= 20) {
            log.debug("Not bothering the pump for a small TBR change from " + activeTbrPercentage + "% -> " + roundedPercentage + "%");
            PumpEnactResult pumpEnactResult = new PumpEnactResult();
            pumpEnactResult.success = true;
            pumpEnactResult.enacted = false;
            pumpEnactResult.percent = activeTbrPercentage;
            pumpEnactResult.comment = "TBR change too small, skipping change from " + activeTbrPercentage + "% -> " + roundedPercentage + "%";
            return pumpEnactResult;
        }
        int stepSize = pumpDescription.tempDurationStep;
        if (durationInMinutes > stepSize) {
            log.debug("Reducing requested duration of " + durationInMinutes + "m to minimal duration supported by the pump: " + stepSize + "m");
            durationInMinutes = stepSize;
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
        CommandResult commandResult = runCommand(new SetTbrCommand(percent, durationInMinutes));
        if (commandResult.enacted) {
            TemporaryBasal tempStart = new TemporaryBasal(System.currentTimeMillis());
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

    // TODO untested, probably not working
    @Override
    public PumpEnactResult cancelTempBasal() {
        log.debug("cancelTempBasal called");
        CommandResult commandResult = runCommand(new CancelTbrCommand());
        if (commandResult.enacted) {
            TemporaryBasal tempStop = new TemporaryBasal(System.currentTimeMillis());
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

            if (pumpState != null) {
                extended.put("TempBasalAbsoluteRate", pumpState.tbrRate);
                // TODO best guess at this point ...
                extended.put("TempBasalStart", DateUtil.dateAndTimeString(System.currentTimeMillis() - (pumpState.tbrRemainingDuration - 15 * 60 * 1000)));
                extended.put("TempBasalRemaining", pumpState.tbrRemainingDuration);
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
