package info.nightscout.androidaps.plugins.PumpCombo;

import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

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
import info.nightscout.androidaps.plugins.Overview.Notification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.PumpCombo.events.EventComboPumpUpdateGUI;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class ComboPlugin implements PluginBase, PumpInterface {
    private static Logger log = LoggerFactory.getLogger(ComboPlugin.class);

    private boolean fragmentEnabled = false;
    private boolean fragmentVisible = false;

    private PumpDescription pumpDescription = new PumpDescription();

    private RuffyCommands ruffyScripter;

    // TODO access to pump (and its members) is chaotic and needs an update
    private ComboPump pump = new ComboPump();

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
        MainApp.bus().register(this);
        startAlerter();
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
                    CommandResult localLastCmdResult = pump.lastCmdResult;
                    if (!SP.getBoolean(R.string.combo_disable_alarms, false) &&
                            localLastCmdResult != null && !localLastCmdResult.success) {
                        long now = System.currentTimeMillis();
                        long fiveMinutesSinceLastAlarm = lastAlarmTime + (5 * 60 * 1000) + (15 * 1000);
                        boolean loopEnabled = ConfigBuilderPlugin.getActiveLoop() != null;
                        if (now > fiveMinutesSinceLastAlarm && loopEnabled) {
                            log.error("Command result: " + localLastCmdResult);
                            PumpState localPumpState = pump.state;
                            if (localPumpState.errorMsg != null) {
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
                        }
                    }
                    SystemClock.sleep(5 * 1000);
                }
            }
        }, "combo-alerter").start();
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
        return pump.lastCmdResult != null;
    }

    @Override
    public boolean isSuspended() {
        return pump.state.suspended;
    }

    @Override
    public boolean isBusy() {
        return ruffyScripter.isPumpBusy() && !pump.state.suspended;
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

    @NonNull
    @Override
    public Date lastDataTime() {
        CommandResult lastCmdResult = pump.lastCmdResult;
        return lastCmdResult != null ? new Date(lastCmdResult.completionTime) : new Date(0);
    }

    private void initializePump() {
        CommandResult commandResult = runCommand("Checking pump history", false, new CommandExecution() {
            @Override
            public CommandResult execute() {
                return ruffyScripter.readHistory(
                        new PumpHistoryRequest()
                                .reservoirLevel(true)
                                .bolusHistory(PumpHistoryRequest.LAST)
                                .tbrHistory(PumpHistoryRequest.LAST)
                                .errorHistory(PumpHistoryRequest.LAST));
            }
        });

        if (!commandResult.success || commandResult.history == null) {
            // TODO error case, command
            return;
        }

        // TODO opt, construct PumpHistoryRequest to requset only what needs updating
        boolean syncNeeded = false;

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

        if (aapsBolus == null || pumpBolus == null) {
            syncNeeded = true;
        } else if (Math.abs(aapsBolus.insulin - pumpBolus.amount) > 0.05
            || aapsBolus.date  != pumpBolus.timestamp) {
            syncNeeded = true;
        }

        // last tbr
        List<TemporaryBasal> tempBasals = MainApp.getConfigBuilder().getTemporaryBasalsFromHistory().getReversedList();
        TemporaryBasal aapsTbr = null;
        if (!tempBasals.isEmpty()) {
            aapsTbr = tempBasals.get(0);
        }
        Tbr pumpTbr = null;
        List<Tbr> tbrHistory = commandResult.history.tbrHistory;
        if(!tbrHistory.isEmpty()) {
            pumpTbr = tbrHistory.get(0);
        }
        if (aapsTbr == null || pumpTbr == null) {
            syncNeeded = true;
        } else if (aapsTbr.percentRate != pumpTbr.percent || aapsTbr.durationInMinutes != pumpTbr.duration) {
            syncNeeded = true;
        }

        // last error
        // TODO add DB table

        if (syncNeeded) {
            runFullSync();
        }

        // TODO
        // detectStateMismatch(): expensive sync, checking everything (detectTbrMisMatch us called for every command)
        // check 'lasts' of pump against treatment db, request full sync if needed
        // and also remove treatments the pump doesn't have.
        // warn about this with a notification? show what was removed on combo tab?

    }

    // this method is regularly called from info.nightscout.androidaps.receivers.KeepAliveReceiver
    // TODO check this is eithor called regularly even with other commansd being fired; if not,
    // request this periodically
    @Override
    public synchronized void refreshDataFromPump(String reason) {
        log.debug("RefreshDataFromPump called");

        // if Android is sluggish this might get called before ruffy is bound
        if (!ruffyScripter.isPumpAvailable()) {
            log.error("Rejecting call to RefreshDataFromPump: scripter not ready yet.");
            return;
        }

        // TODO
//        boolean notAUserRequest = !reason.toLowerCase().contains("user");
//        boolean wasRunAtLeastOnce = pump.lastCmdResult != null;
//        boolean ranWithinTheLastMinute = wasRunAtLeastOnce && System.currentTimeMillis() < pump.lastCmdResult.completionTime + 60 * 1000;
//        if (notAUserRequest && wasRunAtLeastOnce && ranWithinTheLastMinute) {
//            log.debug("Not fetching state from pump, since we did already within the last 60 seconds");
//        } else {

        if (pump.lastCmdResult == null) {
           initializePump();
        } else {
            runCommand("Refreshing", new CommandExecution() {
                @Override
                public CommandResult execute() {
                    return ruffyScripter.readHistory(new PumpHistoryRequest().reservoirLevel(true).bolusHistory(PumpHistoryRequest.LAST));
                }
            });
        }
    }

    // TODO uses profile values for the time being
    // this get's called multiple times a minute, must absolutely be cached
    @Override
    public double getBaseBasalRate() {
        Profile profile = MainApp.getConfigBuilder().getProfile();
        Double basal = profile.getBasal();
        log.trace("getBaseBasalrate returning " + basal);
        return basal;
    }

    // TODO remove dep on BolusCommand
    private static BolusProgressReporter bolusProgressReporter =
            new BolusProgressReporter() {
                @Override
                public void report(BolusProgressReporter.State state, int percent, double delivered) {
                    EventOverviewBolusProgress event = EventOverviewBolusProgress.getInstance();
                    switch (state) {
                        case PROGRAMMING:
                            event.status = MainApp.sResources.getString(R.string.bolusprogramming);
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
                    }
                    event.percent = percent;
                    MainApp.bus().post(event);
                }
            };

    /**
     * Updates Treatment records with carbs and boluses and delivers a bolus if needed
     */
    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        try {
            if (detailedBolusInfo.insulin > 0 || detailedBolusInfo.carbs > 0) {
                if (detailedBolusInfo.insulin > 0) {
                    // bolus needed, ask pump to deliver it
                    return deliverBolus(detailedBolusInfo);
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
        } finally {
            MainApp.bus().post(new EventComboPumpUpdateGUI());
        }
    }

    @NonNull
    private PumpEnactResult deliverBolus(final DetailedBolusInfo detailedBolusInfo) {
        CommandResult bolusCmdResult = runCommand("Bolusing", new CommandExecution() {
            @Override
            public CommandResult execute() {
                return ruffyScripter.deliverBolus(detailedBolusInfo.insulin, bolusProgressReporter);
            }
        });

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
        CommandResult commandResult = runCommand("Setting TBR", new CommandExecution() {
                    @Override
                    public CommandResult execute() {
                        return ruffyScripter.setTbr(finalAdjustedPercent, durationInMinutes);
                    }
                }
        );

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
            commandResult = runCommand("Cancelling TBR", new CommandExecution() {
                @Override
                public CommandResult execute() {
                    return ruffyScripter.cancelTbr();
                }
            });

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
            final int percentage = (activeTemp.percentRate > 100) ? 110 : 90;
            log.debug("cancelTempBasal: changing tbr to " + percentage + "% for 15 mins.");
            commandResult = runCommand("Setting TBR", new CommandExecution() {
                @Override
                public CommandResult execute() {
                    return ruffyScripter.setTbr(percentage, 15);
                }
            });

            if (commandResult.enacted) {
                tempBasal = new TemporaryBasal(commandResult.completionTime);
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
            pumpEnactResult.comment = commandResult.message;
        }
        return pumpEnactResult;
    }

    interface CommandExecution {
        CommandResult execute();
    }

    private CommandResult runCommand(String status, CommandExecution commandExecution) {
        return runCommand(status, true, commandExecution);

    }

    private CommandResult runCommand(String status, boolean checkTbrMisMatch, CommandExecution commandExecution) {
        MainApp.bus().post(new EventComboPumpUpdateGUI(status));
        CommandResult commandResult = commandExecution.execute();

        if (commandResult.state.errorMsg != null) {
            CommandResult takeOverAlarmResult = ruffyScripter.takeOverAlarm();

            Notification notification = new Notification(Notification.IC_MISSING, "Pump alarm: " + takeOverAlarmResult.message
                    /*ainApp.sResources.getString(R.string.icmissing)*/, Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));

            commandResult.state = takeOverAlarmResult.state;
        }

        pump.lastCmdResult = commandResult;
        pump.state = commandResult.state;

        // TODO are there cases when this check should NOT be performed? perform this explicitly or have a flag to skip this?
        if (checkTbrMisMatch) {
            checkForTbrMismatch();
        }


        // TODO not propely set all the time ...
        if (pump.lastCmdResult == null) {
            log.error("JOE: no!");
        } else {
            // still crashable ...
            pump.lastCmdResult.completionTime = System.currentTimeMillis();
        }

        // TOOD
        if (commandResult.history != null) {
            if (commandResult.history.reservoirLevel != -1) {
                pump.reservoirLevel = commandResult.history.reservoirLevel;
            }
            pump.history = commandResult.history;
            if (pump.history.bolusHistory.size() > 0) {
                pump.lastBolus = pump.history.bolusHistory.get(0);
            }
        }

        MainApp.bus().post(new EventComboPumpUpdateGUI());
        return commandResult;
    }

    private void checkForTbrMismatch() {
        // detectTbrMismatch(): 'quick' check with not overhead on the pump side
        // TODO check if this works with pump suspend, esp. around pump suspend there'll be syncing to do;

        TemporaryBasal aapsTbr = MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis());
//        if (true) {
//
//            // not yet
//        } else
        if (aapsTbr == null && pump.state.tbrActive) {
            // pump runs TBR AAPS is unaware off
            // => fetch full history so the full TBR is added to treatments
            log.debug("JOE: sync required 1");
            runFullSync();
        } else if (aapsTbr != null && !pump.state.tbrActive) {
            // AAPS has a TBR but the pump isn't running a TBR
            // => remove the TBR from treatments
            // => fetch full history, so that if the TBR was cancelled but ran some time we get the IOB from that partial TBR
            log.debug("JOE: sync required 2");
            MainApp.getDbHelper().delete(aapsTbr);
            runFullSync();
        } else if (aapsTbr != null && pump.state.tbrActive) {
            // both AAPS and pump have a TBR ...
            if (aapsTbr.percentRate != pump.state.tbrPercent) {
                // ... but they have different percentages
                // => remove TBR from treatments
                // => full history sync so we get up to date on actual IOB
                log.debug("JOE: sync required 3");
                MainApp.getDbHelper().delete(aapsTbr);
                runFullSync();
            }
            int durationDiff = Math.abs(aapsTbr.getPlannedRemainingMinutes() - pump.state.tbrRemainingDuration);
            if (durationDiff > 2) {
                // ... but they have different runtimes
                // ^ same as above, merge branches
                log.debug("JOE: sync required 4");
                MainApp.getDbHelper().delete(aapsTbr);
                runFullSync();
            }
        }

        // TODO request a loop run to (re)apply a TBR/SMB given this new information? or just wait till next iteration?
        // could take 15m or so if there are missed SGVs ...

    }

    private void runFullSync() {
        // TODO separate fetching and comparing
        if (1 == 1 ) {
            log.error("Skipping full sync - not implemented yet");
            return;
        }
        CommandResult commandResult = runCommand("Syncing full pump history", false, new CommandExecution() {
            @Override
            public CommandResult execute() {
                return ruffyScripter.readHistory(
                        new PumpHistoryRequest()
                                .reservoirLevel(true)
                                .bolusHistory(PumpHistoryRequest.FULL)
                                .tbrHistory(PumpHistoryRequest.FULL)
                                .errorHistory(PumpHistoryRequest.FULL)
                                .tddHistory(PumpHistoryRequest.FULL)
                );
            }
        });


    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        return OPERATION_NOT_SUPPORTED;
    }

    // Returns the state of the pump as it was received during last pump comms.
    // TODO v2 add battery, reservoir info when we start reading that and clean up the code
    @Override
    public JSONObject getJSONStatus() {
        CommandResult lastCmdResult = pump.lastCmdResult;
        if (lastCmdResult == null || lastCmdResult.completionTime + 5 * 60 * 1000L < System.currentTimeMillis()) {
            return null;
        }

        try {
            JSONObject pumpJson = new JSONObject();
            JSONObject statusJson = new JSONObject();
            JSONObject extendedJson = new JSONObject();
            statusJson.put("status", pump.state.getStateSummary());
            extendedJson.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            try {
                extendedJson.put("ActiveProfile", MainApp.getConfigBuilder().getProfileName());
            } catch (Exception e) {
            }
            statusJson.put("timestamp", lastCmdResult.completionTime);

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
            pumpJson.put("clock", DateUtil.toISOString(lastCmdResult.completionTime));

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
        return pump.state.getStateSummary();
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }
}