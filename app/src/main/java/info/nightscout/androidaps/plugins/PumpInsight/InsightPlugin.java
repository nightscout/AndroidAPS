package info.nightscout.androidaps.plugins.PumpInsight;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.PumpInsight.connector.CancelBolusTaskRunner;
import info.nightscout.androidaps.plugins.PumpInsight.connector.Connector;
import info.nightscout.androidaps.plugins.PumpInsight.connector.SetTBRTaskRunner;
import info.nightscout.androidaps.plugins.PumpInsight.connector.StatusTaskRunner;
import info.nightscout.androidaps.plugins.PumpInsight.connector.WriteBasalProfileTaskRunner;
import info.nightscout.androidaps.plugins.PumpInsight.events.EventInsightCallback;
import info.nightscout.androidaps.plugins.PumpInsight.events.EventInsightUpdateGui;
import info.nightscout.androidaps.plugins.PumpInsight.history.HistoryReceiver;
import info.nightscout.androidaps.plugins.PumpInsight.history.LiveHistory;
import info.nightscout.androidaps.plugins.PumpInsight.utils.Helpers;
import info.nightscout.androidaps.plugins.PumpInsight.utils.StatusItem;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;
import sugar.free.sightparser.applayer.descriptors.ActiveBolus;
import sugar.free.sightparser.applayer.descriptors.ActiveBolusType;
import sugar.free.sightparser.applayer.descriptors.PumpStatus;
import sugar.free.sightparser.applayer.descriptors.configuration_blocks.BRProfileBlock;
import sugar.free.sightparser.applayer.messages.AppLayerMessage;
import sugar.free.sightparser.applayer.messages.remote_control.BolusMessage;
import sugar.free.sightparser.applayer.messages.remote_control.CancelBolusMessage;
import sugar.free.sightparser.applayer.messages.remote_control.CancelTBRMessage;
import sugar.free.sightparser.applayer.messages.remote_control.ExtendedBolusMessage;
import sugar.free.sightparser.applayer.messages.remote_control.StandardBolusMessage;
import sugar.free.sightparser.applayer.messages.status.ActiveBolusesMessage;
import sugar.free.sightparser.handling.SingleMessageTaskRunner;
import sugar.free.sightparser.handling.TaskRunner;
import sugar.free.sightparser.pipeline.Status;

import static info.nightscout.androidaps.plugins.PumpInsight.history.PumpIdCache.getRecordUniqueID;


/**
 * Created by jamorham on 23/01/2018.
 * <p>
 * Connects to SightRemote app service using SightParser library
 * <p>
 * SightRemote and SightParser created by Tebbe Ubben
 * <p>
 * Original proof of concept SightProxy by jamorham
 */

@SuppressWarnings("AccessStaticViaInstance")
public class InsightPlugin extends PluginBase implements PumpInterface, ConstraintsInterface {

    private static volatile InsightPlugin plugin;

    public static InsightPlugin getPlugin() {
        if (plugin == null) {
            plugin = new InsightPlugin();
        }
        return plugin;
    }

    private static final long BUSY_WAIT_TIME = 20000;
    private static Integer batteryPercent = 0;
    private static Integer reservoirInUnits = 0;
    private static boolean initialized = false;
    private static volatile boolean update_pending = false;
    private static Logger log = LoggerFactory.getLogger(InsightPlugin.class);
    private final InsightAsyncAdapter async = new InsightAsyncAdapter();
    private StatusTaskRunner.Result statusResult;
    private long statusResultTime = -1;
    private Date lastDataTime = new Date(0);
    private boolean fauxTBRcancel = true;
    private PumpDescription pumpDescription = new PumpDescription();
    private double basalRate = 0;
    private Connector connector;
    private volatile boolean connector_enabled = false;
    private List<BRProfileBlock.ProfileBlock> profileBlocks;

    private InsightPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.PUMP)
                .fragmentClass(InsightFragment.class.getName())
                .pluginName(R.string.insightpump)
                .shortName(R.string.insightpump_shortname)
                .preferencesId(R.xml.pref_insightpump)
        );
        log("InsightPlugin instantiated");
        pumpDescription.isBolusCapable = true;
        pumpDescription.bolusStep = 0.05d; // specification says 0.05U up to 2U then 0.1U @ 2-5U  0.2U @ 10-20U 0.5U 10-20U (are these just UI restrictions?)

        pumpDescription.isExtendedBolusCapable = true;
        pumpDescription.extendedBolusStep = 0.05d; // specification probably same as above
        pumpDescription.extendedBolusDurationStep = 15; // 15 minutes up to 24 hours
        pumpDescription.extendedBolusMaxDuration = 24 * 60;

        pumpDescription.isTempBasalCapable = true;
        //pumpDescription.tempBasalStyle = PumpDescription.PERCENT | PumpDescription.ABSOLUTE;
        pumpDescription.tempBasalStyle = PumpDescription.PERCENT;

        pumpDescription.maxTempPercent = 250; // 0-250%
        pumpDescription.tempPercentStep = 10;

        pumpDescription.tempDurationStep = 15; // 15 minutes up to 24 hours
        pumpDescription.tempDurationStep15mAllowed = true;
        pumpDescription.tempDurationStep30mAllowed = true;
        pumpDescription.tempMaxDuration = 24 * 60;

        pumpDescription.isSetBasalProfileCapable = true;
        pumpDescription.is30minBasalRatesCapable = true;
        pumpDescription.basalStep = 0.01d;
        pumpDescription.basalMinimumRate = 0.02d;

        pumpDescription.isRefillingCapable = true;

        pumpDescription.storesCarbInfo = false;

        pumpDescription.supportsTDDs = true;
        pumpDescription.needsManualTDDLoad = false;
    }


    // just log during debugging
    private static void log(String msg) {
        android.util.Log.e("INSIGHTPUMP", msg);
    }

    private static void updateGui() {
        update_pending = false;
        MainApp.bus().post(new EventInsightUpdateGui());
    }

    private static void pushCallbackEvent(EventInsightCallback e) {
        MainApp.bus().post(e);
    }

    @Override
    protected void onStart() {
        if (!connector_enabled) {
            synchronized (this) {
                if (!connector_enabled) {
                    log("Instantiating connector");
                    connector_enabled = true;
                    this.connector = Connector.get();
                    this.connector.init();
                }
            }
        }
        super.onStart();
    }

    protected void onStop() {
        if (connector_enabled) {
            synchronized (this) {
                if (connector_enabled) {
                    log("Shutting down connector");
                    Connector.get().shutdown();
                    connector_enabled = false;
                }
            }
        }
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @Override
    public PumpEnactResult loadTDDs() {
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        return result;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public boolean isSuspended() {
        return !isPumpRunning();
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public boolean isConnected() {
        return Connector.get().isPumpConnected();
    }

    @Override
    public boolean isConnecting() {
        return Connector.get().isPumpConnecting();
    }

    @Override
    public void connect(String reason) {
        log("InsightPlugin::connect()");
        try {
            if (!connector.isPumpConnected()) {
                if (Helpers.ratelimit("insight-connect-timer", 40)) {
                    log("Actually requesting a connect");
                    connector.connectToPump();
                }
            } else {
                log("Already connected");
            }
        } catch (NullPointerException e) {
            log("Could not sconnect - null pointer: " + e);
        }

        // TODO review
        if (!Config.NSCLIENT && !Config.G5UPLOADER)
            NSUpload.uploadDeviceStatus();
    }

    @Override
    public void disconnect(String reason) {
        log("InsightPlugin::disconnect()");
        try {
            if (!SP.getBoolean("insight_always_connected", false)) {
                log("Requesting disconnect");
                connector.disconnectFromPump();
            } else {
                log("Not disconnecting due to preference");
            }
        } catch (NullPointerException e) {
            log("Could not disconnect - null pointer: " + e);
        }
    }

    @Override
    public void stopConnecting() {
        log("InsightPlugin::stopConnecting()");
        try {
            if (isConnecting()) {
                if (!SP.getBoolean("insight_always_connected", false)) {
                    log("Requesting disconnect");
                    connector.disconnectFromPump();
                } else {
                    log("Not disconnecting due to preference");
                }
            } else {
                log("Not currently trying to connect so not stopping connection");
            }
        } catch (NullPointerException e) {
            log("Could not stop connecting - null pointer: " + e);
        }
    }

    @Override
    public void getPumpStatus() {

        log("getPumpStatus");
        if (Connector.get().isPumpConnected()) {
            log("is connected.. requesting status");
            final UUID uuid = aSyncTaskRunner(new StatusTaskRunner(connector.getServiceConnector()), "Status");
            Mstatus mstatus = async.busyWaitForCommandResult(uuid, BUSY_WAIT_TIME);
            if (mstatus.success()) {
                log("GOT STATUS RESULT!!! PARTY WOOHOO!!!");
                setStatusResult((StatusTaskRunner.Result) mstatus.getResponseObject());
                statusResultTime = Helpers.tsl();
                processStatusResult();
                updateGui();
                connector.requestHistoryReSync();
                connector.requestHistorySync();
            } else {
                log("StatusTaskRunner wasn't successful.");
                if (connector.getServiceConnector().isConnectedToService() && connector.getServiceConnector().getStatus() != Status.CONNECTED) {
                    if (Helpers.ratelimit("insight-reconnect", 2)) {
                        Connector.connectToPump();
                        updateGui();
                    }
                }
            }
        } else {
            log("not connected.. not requesting status");
        }
    }

    public void setStatusResult(StatusTaskRunner.Result result) {
        this.statusResult = result;
    }

    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        PumpEnactResult result = new PumpEnactResult();
        if (!isInitialized()) {
            log.error("setNewBasalProfile not initialized");
            Notification notification = new Notification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED, MainApp.gs(R.string.pumpNotInitializedProfileNotSet), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
            result.comment = MainApp.gs(R.string.pumpNotInitializedProfileNotSet);
            return result;
        }
        MainApp.bus().post(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
        List<BRProfileBlock.ProfileBlock> profileBlocks = new ArrayList<>();
        for (int i = 0; i < profile.getBasalValues().length; i++) {
            Profile.BasalValue basalValue = profile.getBasalValues()[i];
            Profile.BasalValue nextValue = null;
            if (profile.getBasalValues().length > i + 1)
                nextValue = profile.getBasalValues()[i + 1];
            profileBlocks.add(new BRProfileBlock.ProfileBlock((((nextValue != null ? nextValue.timeAsSeconds : 24 * 60 * 60) - basalValue.timeAsSeconds) / 60), Helpers.roundDouble(basalValue.value, 2)));
            log("setNewBasalProfile: " + basalValue.value + " for " + Integer.toString(((nextValue != null ? nextValue.timeAsSeconds : 24 * 60 * 60) - basalValue.timeAsSeconds) / 60));
        }
        final UUID uuid = aSyncTaskRunner(new WriteBasalProfileTaskRunner(connector.getServiceConnector(), profileBlocks), "Write basal profile");
        final Mstatus ms = async.busyWaitForCommandResult(uuid, BUSY_WAIT_TIME);
        if (ms.success()) {
            MainApp.bus().post(new EventDismissNotification(Notification.FAILED_UDPATE_PROFILE));
            Notification notification = new Notification(Notification.PROFILE_SET_OK, MainApp.gs(R.string.profile_set_ok), Notification.INFO, 60);
            MainApp.bus().post(new EventNewNotification(notification));
            result.success = true;
            result.enacted = true;
            result.comment = "OK";
            this.profileBlocks = profileBlocks;
        } else {
            Notification notification = new Notification(Notification.FAILED_UDPATE_PROFILE, MainApp.gs(R.string.failedupdatebasalprofile), Notification.URGENT);
            MainApp.bus().post(new EventNewNotification(notification));
            result.comment = MainApp.gs(R.string.failedupdatebasalprofile);
        }
        return result;
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        if (!isInitialized() || profileBlocks == null) return true;
        if (profile.getBasalValues().length != profileBlocks.size()) return false;
        for (int i = 0; i < profileBlocks.size(); i++) {
            BRProfileBlock.ProfileBlock profileBlock = profileBlocks.get(i);
            Profile.BasalValue basalValue = profile.getBasalValues()[i];
            Profile.BasalValue nextValue = null;
            if (profile.getBasalValues().length > i + 1)
                nextValue = profile.getBasalValues()[i + 1];
            log("isThisProfileSet - Comparing block: Pump: " + profileBlock.getAmount() + " for " + profileBlock.getDuration()
                    + " Profile: " + basalValue.value + " for " + Integer.toString(((nextValue != null ? nextValue.timeAsSeconds : 24 * 60 * 60) - basalValue.timeAsSeconds) / 60));
            if (profileBlock.getDuration() * 60 != (nextValue != null ? nextValue.timeAsSeconds : 24 * 60 * 60) - basalValue.timeAsSeconds)
                return false;
            //Allow a little imprecision due to rounding errors
            if (Math.abs(profileBlock.getAmount() - Helpers.roundDouble(basalValue.value, 2)) >= 0.01D)
                return false;
        }
        return true;
    }

    @Override
    public Date lastDataTime() {
        return lastDataTime;
    }

    @Override
    public double getBaseBasalRate() {
        return basalRate;
    }

    public String getBaseBasalRateString() {
        final DecimalFormat df = new DecimalFormat("#.##");
        return df.format(basalRate);
    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        final PumpEnactResult result = new PumpEnactResult();
        result.bolusDelivered = detailedBolusInfo.insulin;
        result.carbsDelivered = detailedBolusInfo.carbs;
        result.enacted = result.bolusDelivered > 0 || result.carbsDelivered > 0;
        result.comment = MainApp.gs(R.string.virtualpump_resultok);

        result.percent = 100;

        int bolusId = 0;

        // is there an insulin component to the treatment?
        if (detailedBolusInfo.insulin > 0) {
            final UUID cmd = deliverBolus(detailedBolusInfo.insulin); // actually request delivery
            if (cmd == null) {
                return pumpEnactFailure();
            }
            final Mstatus ms = async.busyWaitForCommandResult(cmd, BUSY_WAIT_TIME);

            result.success = ms.success();
            if (ms.success()) {
                detailedBolusInfo.pumpId = getRecordUniqueID(ms.getResponseID());
                bolusId = ms.getResponseID();
            }
        } else {
            result.success = true; // always true with carb only treatments
        }

        if (result.success) {
            log("Success!");

            Treatment t = new Treatment();
            t.isSMB = detailedBolusInfo.isSMB;
            final EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
            bolusingEvent.t = t;
            bolusingEvent.status = String.format(MainApp.gs(R.string.bolusdelivering), 0F);
            bolusingEvent.bolusId = bolusId;
            bolusingEvent.percent = 0;
            MainApp.bus().post(bolusingEvent);
            TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo);
        } else {
            log.debug("Failure to deliver treatment");
        }

        if (Config.logPumpComm)
            log.debug("Delivering treatment insulin: " + detailedBolusInfo.insulin + "U carbs: " + detailedBolusInfo.carbs + "g " + result);

        updateGui();
        connector.tryToGetPumpStatusAgain();

        connector.requestHistorySync(30000);

        if (result.success) while (true) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
            final UUID uuid = aSyncSingleCommand(new ActiveBolusesMessage(), "Active boluses");
            Mstatus mstatus = async.busyWaitForCommandResult(uuid, BUSY_WAIT_TIME);
            if (mstatus.success()) {
                final EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
                ActiveBolusesMessage activeBolusesMessage = (ActiveBolusesMessage) mstatus.getResponseObject();
                ActiveBolus activeBolus = null;
                if (activeBolusesMessage.getBolus1() != null && activeBolusesMessage.getBolus1().getBolusID() == bolusingEvent.bolusId)
                    activeBolus = activeBolusesMessage.getBolus1();
                else if (activeBolusesMessage.getBolus2() != null && activeBolusesMessage.getBolus2().getBolusID() == bolusingEvent.bolusId)
                    activeBolus = activeBolusesMessage.getBolus2();
                else if (activeBolusesMessage.getBolus3() != null && activeBolusesMessage.getBolus3().getBolusID() == bolusingEvent.bolusId)
                    activeBolus = activeBolusesMessage.getBolus3();
                if (activeBolus == null) break;
                else {
                    bolusingEvent.percent = (int) (100D / activeBolus.getInitialAmount() * (activeBolus.getInitialAmount() - activeBolus.getLeftoverAmount()));
                    bolusingEvent.status = String.format(MainApp.gs(R.string.bolusdelivering), activeBolus.getInitialAmount() - activeBolus.getLeftoverAmount());
                    MainApp.bus().post(bolusingEvent);
                }
            } else break;
        }
        return result;
    }

    @Override
    public void stopBolusDelivering() {
        CancelBolusMessage cancelBolusMessage = new CancelBolusMessage();
        cancelBolusMessage.setBolusId(EventOverviewBolusProgress.getInstance().bolusId);
        final UUID cmd = aSyncSingleCommand(cancelBolusMessage, "Cancel standard bolus");

        if (cmd == null) {
            return;
        }

        final Mstatus cs = async.busyWaitForCommandResult(cmd, BUSY_WAIT_TIME);
        log("Got command status: " + cs);
    }

    // Temporary Basals

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        absoluteRate = Helpers.roundDouble(absoluteRate, 3);
        log("Set TBR absolute: " + absoluteRate);
        final double base_basal = getBaseBasalRate();
        if (base_basal == 0) {
            log("Base basal rate appears to be zero!");
            return pumpEnactFailure();
        }
        int percent_amount = (int) Math.round(100d / base_basal * absoluteRate);
        log("Calculated requested rate: " + absoluteRate + " base rate: " + base_basal + " percentage: " + percent_amount + "%");
        percent_amount = (int) Math.round(((double) percent_amount) / 10d) * 10;
        log("Calculated final rate: " + percent_amount + "%");

        if (percent_amount == 100) {
            return cancelTempBasal(false);
        }

        if (percent_amount > 250) percent_amount = 250;


        final SetTBRTaskRunner task = new SetTBRTaskRunner(connector.getServiceConnector(), percent_amount, durationInMinutes);
        final UUID cmd = aSyncTaskRunner(task, "Set TBR abs: " + absoluteRate + " " + durationInMinutes + "m");

        if (cmd == null) {
            return pumpEnactFailure();
        }

        Mstatus ms = async.busyWaitForCommandResult(cmd, BUSY_WAIT_TIME);
        log("Got command status: " + ms);

        PumpEnactResult pumpEnactResult = new PumpEnactResult().enacted(true).isPercent(true).duration(durationInMinutes);
        pumpEnactResult.percent = percent_amount;
        pumpEnactResult.success = ms.success();
        pumpEnactResult.comment = ms.getCommandComment();


        if (pumpEnactResult.success) {
            // create log entry
            final TemporaryBasal tempBasal = new TemporaryBasal()
                    .date(System.currentTimeMillis())
                    .percent(percent_amount)
                    .duration(durationInMinutes)
                    .source(Source.USER);
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempBasal);
        }

        if (Config.logPumpComm)
            log.debug("Setting temp basal absolute: " + pumpEnactResult.success);

        updateGui();

        connector.requestHistorySync(5000);
        connector.tryToGetPumpStatusAgain();

        return pumpEnactResult;
    }


    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        log("Set TBR %");

        percent = (int) Math.round(((double) percent) / 10d) * 10;
        if (percent == 100) {
            // This would cause a cancel if a tbr is in progress so treat as a cancel
            return cancelTempBasal(false);
        }


        final UUID cmd = aSyncTaskRunner(new SetTBRTaskRunner(connector.getServiceConnector(), percent, durationInMinutes), "Set TBR " + percent + "%" + " " + durationInMinutes + "m");

        if (cmd == null) {
            return pumpEnactFailure();
        }

        final Mstatus ms = async.busyWaitForCommandResult(cmd, BUSY_WAIT_TIME);
        log("Got command status: " + ms);

        PumpEnactResult pumpEnactResult = new PumpEnactResult().enacted(true).isPercent(true).duration(durationInMinutes);
        pumpEnactResult.percent = percent;
        pumpEnactResult.success = ms.success();
        pumpEnactResult.comment = ms.getCommandComment();

        if (pumpEnactResult.success) {
            // create log entry
            final TemporaryBasal tempBasal = new TemporaryBasal()
                    .date(System.currentTimeMillis())
                    .percent(percent)
                    .duration(durationInMinutes)
                    .source(Source.USER); // TODO check this is correct
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempBasal);
        }

        updateGui();

        if (Config.logPumpComm)
            log.debug("Set temp basal " + percent + "% for " + durationInMinutes + "m");

        connector.requestHistorySync(5000);
        connector.tryToGetPumpStatusAgain();

        return pumpEnactResult;
    }


    @Override
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        log("Cancel TBR");


        fauxTBRcancel = !SP.getBoolean("insight_real_tbr_cancel", false);

        final UUID cmd;

        if (fauxTBRcancel) {
            cmd = aSyncTaskRunner(new SetTBRTaskRunner(connector.getServiceConnector(), 100, 1), "Faux Cancel TBR - setting " + "90%" + " 1m");
        } else {
            cmd = aSyncSingleCommand(new CancelTBRMessage(), "Cancel Temp Basal");
        }
        if (cmd == null) {
            return pumpEnactFailure();
        }

        // TODO isn't conditional on one apparently being in progress only the history change
        final Mstatus ms = async.busyWaitForCommandResult(cmd, BUSY_WAIT_TIME);

        if (TreatmentsPlugin.getPlugin().isTempBasalInProgress()) {
            TemporaryBasal tempStop = new TemporaryBasal().date(System.currentTimeMillis()).source(Source.USER);
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStop);
        }
        updateGui();
        if (Config.logPumpComm)
            log.debug("Canceling temp basal: "); // TODO get more info

        connector.requestHistorySync(5000);
        connector.tryToGetPumpStatusAgain();

        return new PumpEnactResult().success(ms.success()).enacted(true).isTempCancel(true);
    }


    // Extended Boluses

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        log("Set Extended bolus " + insulin + " " + durationInMinutes);
        ExtendedBolusMessage extendedBolusMessage = new ExtendedBolusMessage();
        extendedBolusMessage.setAmount(insulin);
        extendedBolusMessage.setDuration(durationInMinutes);
        final UUID cmd = aSyncSingleCommand(extendedBolusMessage, "Extended bolus U" + insulin + " mins:" + durationInMinutes);
        if (cmd == null) {
            return pumpEnactFailure();
        }

        final Mstatus ms = async.busyWaitForCommandResult(cmd, BUSY_WAIT_TIME);
        log("Got command status: " + ms);

        PumpEnactResult pumpEnactResult = new PumpEnactResult().enacted(true).bolusDelivered(insulin).duration(durationInMinutes);
        pumpEnactResult.success = ms.success();
        pumpEnactResult.comment = ms.getCommandComment();

        if (pumpEnactResult.success) {
            // create log entry
            final ExtendedBolus extendedBolus = new ExtendedBolus();
            extendedBolus.date = System.currentTimeMillis();
            extendedBolus.insulin = insulin;
            extendedBolus.durationInMinutes = durationInMinutes;
            extendedBolus.source = Source.USER;
            extendedBolus.pumpId = getRecordUniqueID(ms.getResponseID());
            TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
        }

        if (Config.logPumpComm)
            log.debug("Setting extended bolus: " + insulin + " mins:" + durationInMinutes + " " + pumpEnactResult.comment);

        updateGui();

        connector.requestHistorySync(30000);
        connector.tryToGetPumpStatusAgain();

        return pumpEnactResult;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {

        log("Cancel Extended bolus");

        // TODO note always sends cancel to pump but only changes history if present

        final UUID cmd = aSyncTaskRunner(new CancelBolusTaskRunner(connector.getServiceConnector(), ActiveBolusType.EXTENDED), "Cancel extended bolus");

        if (cmd == null) {
            return pumpEnactFailure();
        }

        final Mstatus ms = async.busyWaitForCommandResult(cmd, BUSY_WAIT_TIME);

        if (TreatmentsPlugin.getPlugin().isInHistoryExtendedBoluslInProgress()) {
            ExtendedBolus exStop = new ExtendedBolus(System.currentTimeMillis());
            exStop.source = Source.USER;
            TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(exStop);
        }

        if (Config.logPumpComm)
            log.debug("Cancel extended bolus:");

        updateGui();

        connector.requestHistorySync(5000);
        connector.tryToGetPumpStatusAgain();

        return new PumpEnactResult().success(ms.success()).enacted(true);
    }


    private synchronized UUID deliverBolus(double bolusValue) {
        log("DeliverBolus: " + bolusValue);

        if (bolusValue == 0) return null;
        if (bolusValue < 0) return null;

        // TODO check limits here or they already occur via a previous constraint interface?

        final StandardBolusMessage message = new StandardBolusMessage();
        message.setAmount(bolusValue);

        return aSyncSingleCommand(message, "Deliver Bolus " + bolusValue);
    }

    @Override
    public JSONObject getJSONStatus(Profile profile, String profileName) {
        long now = System.currentTimeMillis();
        if (Helpers.msSince(connector.getLastContactTime()) > (60 * 60 * 1000)) {
            log("getJSONStatus not returning as data likely stale");
            return null;
        }

        final JSONObject pump = new JSONObject();
        final JSONObject battery = new JSONObject();
        final JSONObject status = new JSONObject();
        final JSONObject extended = new JSONObject();
        try {
            battery.put("percent", batteryPercent);
            status.put("status", isSuspended() ? "suspended" : "normal");
            status.put("timestamp", DateUtil.toISOString(connector.getLastContactTime()));
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            try {
                extended.put("ActiveProfile", MainApp.getConfigBuilder().getProfileName());
            } catch (Exception e) {
            }
            TemporaryBasal tb = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(now);
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.tempBasalConvertedToAbsolute(now, profile));
                extended.put("TempBasalStart", DateUtil.dateAndTimeString(tb.date));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }
            ExtendedBolus eb = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(now);
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.absoluteRate());
                extended.put("ExtendedBolusStart", DateUtil.dateAndTimeString(eb.date));
                extended.put("ExtendedBolusRemaining", eb.getPlannedRemainingMinutes());
            }
            extended.put("BaseBasalRate", getBaseBasalRate());
            status.put("timestamp", DateUtil.toISOString(now));

            pump.put("battery", battery);
            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("reservoir", reservoirInUnits);
            pump.put("clock", DateUtil.toISOString(now));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return pump;
    }

    @Override
    public String deviceID() {
        return "InsightPump";
    }

    @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @Override
    public String shortStatus(boolean veryShort) {
        String msg = gs(R.string.insightpump_shortname) + " Batt: " + batteryPercent + " Reserv: " + reservoirInUnits + " Basal: " + basalRate;
        if (LiveHistory.getStatus().length() > 0) {
            msg += LiveHistory.getStatus();
        }
        return msg;
    }

    private void processStatusResult() {
        if (statusResult != null) {
            batteryPercent = statusResult.battery;
            reservoirInUnits = (int) statusResult.cartridge;
            basalRate = statusResult.baseBasalRate;
            profileBlocks = statusResult.basalProfile;
            initialized = true; // basic communication test
        }
    }

    private String gs(int id) {
        return MainApp.gs(id);
    }

    private boolean isPumpRunning() {
        if (statusResult == null) return true; // assume running if we have no information
        return statusResult.pumpStatus == PumpStatus.STARTED;
    }

    List<StatusItem> getStatusItems(boolean refresh) {
        final List<StatusItem> l = new ArrayList<>();

        // Todo last contact time

        l.add(new StatusItem(gs(R.string.status_no_colon), connector.getLastStatusMessage()));
        l.add(new StatusItem(gs(R.string.changed), connector.getNiceLastStatusTime()));

        boolean pumpRunning;
        // also check time since received
        if (statusResult != null) {

            pumpRunning = isPumpRunning();
            if (pumpRunning) {
                l.add(new StatusItem(gs(R.string.pump_basebasalrate_label), getBaseBasalRateString() + "U"));
            } else {
                l.add(new StatusItem(gs(R.string.combo_warning), gs(R.string.pump_stopped_uppercase), StatusItem.Highlight.CRITICAL));
            }
        }

        final long offset_ms = Helpers.msSince(statusResultTime);
        final long offset_minutes = offset_ms / 60000;

        if (statusResult != null) {
            l.add(new StatusItem(gs(R.string.status_updated), Helpers.niceTimeScalar(Helpers.msSince(statusResultTime)) + " " + gs(R.string.ago)));
            l.add(new StatusItem(gs(R.string.pump_battery_label), batteryPercent + "%", batteryPercent < 100 ?
                    (batteryPercent < 90 ?
                            (batteryPercent < 70 ?
                                    (StatusItem.Highlight.BAD) : StatusItem.Highlight.NOTICE) : StatusItem.Highlight.NORMAL) : StatusItem.Highlight.GOOD));
            l.add(new StatusItem(gs(R.string.pump_reservoir_label), reservoirInUnits + "U"));
            try {
                if (statusResult.tbrAmount != 100) {
                    l.add(new StatusItem(gs(R.string.insight_active_tbr), statusResult.tbrAmount + "% " + gs(R.string.with) + " "
                            + Helpers.qs(statusResult.tbrLeftoverDuration - offset_minutes, 0)
                            + " " + gs(R.string.insight_min_left), StatusItem.Highlight.NOTICE));
                }
            } catch (NullPointerException e) {
                // currentTBRMessage may be null
            }

        }

        if (TreatmentsPlugin.getPlugin().isTempBasalInProgress()) {
            try {
                l.add(new StatusItem(gs(R.string.pump_tempbasal_label), TreatmentsPlugin.getPlugin().getTempBasalFromHistory(System.currentTimeMillis()).toStringFull()));
            } catch (NullPointerException e) {
                //
            }
        }

        if (statusResult != null) {
            try {
                statusActiveBolus(statusResult.activeBolus1, offset_minutes, l);
                statusActiveBolus(statusResult.activeBolus2, offset_minutes, l);
                statusActiveBolus(statusResult.activeBolus3, offset_minutes, l);
            } catch (NullPointerException e) {
                // getActiveBolusesMessage() may be null
            }
        }

        if (TreatmentsPlugin.getPlugin().isInHistoryExtendedBoluslInProgress()) {
            try {

                l.add(new StatusItem(gs(R.string.virtualpump_extendedbolus_label), TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(System.currentTimeMillis()).toString()));
            } catch (NullPointerException e) {
                //
            }
        }

        l.add(new StatusItem(gs(R.string.log_book), HistoryReceiver.getStatusString()));

        if (LiveHistory.getStatus().length() > 0) {
            l.add(new StatusItem(gs(R.string.insight_last_completed_action), LiveHistory.getStatus()));
        }

        final String keep_alive_status = Connector.getKeepAliveString();
        if (keep_alive_status != null) {
            l.add(new StatusItem(gs(R.string.insight_keep_alive_status), keep_alive_status));
        }

        final List<StatusItem> status_statistics = connector.getStatusStatistics();
        if (status_statistics.size() > 0) {
            l.addAll(status_statistics);
        }

        if (Helpers.ratelimit("insight-status-ui-refresh", 10)) {
            connector.tryToGetPumpStatusAgain();
        }
        connector.requestHistorySync();
        if (refresh) scheduleGUIUpdate();

        return l;
    }

    private synchronized void scheduleGUIUpdate() {
        if (!update_pending && connector.uiFresh()) {
            update_pending = true;
            Helpers.runOnUiThreadDelayed(new Runnable() {
                @Override
                public void run() {
                    updateGui();
                }
            }, 1000);
        }
    }

    private void statusActiveBolus(ActiveBolus activeBolus, long offset_mins, List<StatusItem> l) {
        if (activeBolus == null) return;
        switch (activeBolus.getBolusType()) {

            case STANDARD:
                l.add(new StatusItem(activeBolus.getBolusType() + " " + gs(R.string.bolus), activeBolus.getInitialAmount() + "U", StatusItem.Highlight.NOTICE));
                break;
            case EXTENDED:
                l.add(new StatusItem(activeBolus.getBolusType() + " " + gs(R.string.bolus), activeBolus.getInitialAmount() + "U " + gs(R.string.insight_total_with) + " "
                        + activeBolus.getLeftoverAmount() + "U " + gs(R.string.insight_remaining_over) + " " + (activeBolus.getDuration() - offset_mins) + " " + gs(R.string.insight_min), StatusItem.Highlight.NOTICE));
                break;
            case MULTIWAVE:
                l.add(new StatusItem(activeBolus.getBolusType() + " " + gs(R.string.bolus), activeBolus.getInitialAmount() + "U " + gs(R.string.insight_upfront_with) + " "
                        + activeBolus.getLeftoverAmount() + "U " + gs(R.string.insight_remaining_over) + " " + (activeBolus.getDuration() - offset_mins) + " " + gs(R.string.insight_min), StatusItem.Highlight.NOTICE));

                break;
            default:
                log("ERROR: unknown bolus type! " + activeBolus.getBolusType());
        }
    }

    // Utility

    private synchronized UUID aSyncSingleCommand(final AppLayerMessage msg, final String name) {
        // if (!isConnected()) return false;
        //if (isBusy()) return false;
        log("asyncSinglecommand called: " + name);
        final EventInsightCallback event = new EventInsightCallback();
        new Thread() {
            @Override
            public void run() {
                log("asyncSingleCommand thread");
                final SingleMessageTaskRunner singleMessageTaskRunner = new SingleMessageTaskRunner(connector.getServiceConnector(), msg);
                try {
                    singleMessageTaskRunner.fetch(new TaskRunner.ResultCallback() {
                        @Override
                        public void onResult(Object o) {
                            lastDataTime = new Date();
                            log(name + " success");
                            event.response_object = o;
                            if (o instanceof BolusMessage) {
                                event.response_id = ((BolusMessage) o).getBolusId();
                            }
                            event.success = true;
                            pushCallbackEvent(event);
                        }

                        @Override
                        public void onError(Exception e) {
                            log(name + " error");
                            event.message = e.getMessage();
                            pushCallbackEvent(event);
                        }
                    });

                } catch (Exception e) {
                    log("EXCEPTION" + e.toString());
                }
            }
        }.start();
        return event.request_uuid;
    }

    private synchronized UUID aSyncTaskRunner(final TaskRunner task, final String name) {
        // if (!isConnected()) return false;
        //if (isBusy()) return false;
        log("asyncTaskRunner called: " + name);
        final EventInsightCallback event = new EventInsightCallback();
        new Thread() {
            @Override
            public void run() {
                log("asyncTaskRunner thread");
                try {
                    task.fetch(new TaskRunner.ResultCallback() {
                        @Override
                        public void onResult(Object o) {
                            lastDataTime = new Date();
                            log(name + " success");
                            event.response_object = o;
                            event.success = true;
                            pushCallbackEvent(event);
                        }

                        @Override
                        public void onError(Exception e) {
                            log(name + " error");
                            event.message = e.getMessage();
                            pushCallbackEvent(event);
                        }
                    });

                } catch (Exception e) {
                    log("EXCEPTION" + e.toString());
                }
            }
        }.start();
        return event.request_uuid;
    }


    private PumpEnactResult pumpEnactFailure() {
        return new PumpEnactResult().success(false).enacted(false);
    }

    // Constraints

    @Override
    public Constraint<Double> applyBasalConstraints(Constraint<Double> absoluteRate, Profile profile) {
        if (statusResult != null) {
            absoluteRate.setIfSmaller(statusResult.maximumBasalAmount, String.format(MainApp.gs(R.string.limitingbasalratio), statusResult.maximumBasalAmount, MainApp.gs(R.string.pumplimit)), this);
        }
        return absoluteRate;
    }

    @Override
    public Constraint<Integer> applyBasalPercentConstraints(Constraint<Integer> percentRate, Profile profile) {
        percentRate.setIfGreater(0, String.format(MainApp.gs(R.string.limitingpercentrate), 0, MainApp.gs(R.string.itmustbepositivevalue)), this);
        percentRate.setIfSmaller(getPumpDescription().maxTempPercent, String.format(MainApp.gs(R.string.limitingpercentrate), getPumpDescription().maxTempPercent, MainApp.gs(R.string.pumplimit)), this);

        return percentRate;
    }

    @Override
    public Constraint<Double> applyBolusConstraints(Constraint<Double> insulin) {
        if (statusResult != null)
            insulin.setIfSmaller(statusResult.maximumBolusAmount, String.format(MainApp.gs(R.string.limitingbolus), statusResult.maximumBolusAmount, MainApp.gs(R.string.pumplimit)), this);
        return insulin;
    }

}
