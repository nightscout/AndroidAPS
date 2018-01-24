package info.nightscout.androidaps.plugins.PumpInsight;

import android.os.Handler;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Date;

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
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.PumpInsight.connector.CancelBolusTaskRunner;
import info.nightscout.androidaps.plugins.PumpInsight.connector.Connector;
import info.nightscout.androidaps.plugins.PumpInsight.events.EventInsightPumpUpdateGui;
import info.nightscout.androidaps.plugins.PumpInsight.utils.Helpers;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.NSUpload;
import sugar.free.sightparser.applayer.remote_control.CancelTBRMessage;
import sugar.free.sightparser.applayer.remote_control.ExtendedBolusMessage;
import sugar.free.sightparser.applayer.remote_control.StandardBolusMessage;
import sugar.free.sightparser.applayer.status.BolusType;
import sugar.free.sightparser.handling.SingleMessageTaskRunner;
import sugar.free.sightparser.handling.TaskRunner;
import sugar.free.sightparser.handling.taskrunners.SetTBRTaskRunner;
import sugar.free.sightparser.handling.taskrunners.StatusTaskRunner;

/**
 * Created by jamorham on 23/01/2018.
 *
 * Connects to SightRemote app service using SightParser library
 *
 * SightRemote and SightParser created by Tebbe Ubben
 *
 * Original proof of concept SightProxy by jamorham
 *
 */

public class InsightPumpPlugin implements PluginBase, PumpInterface {

    static Integer batteryPercent = 0;
    static Integer reservoirInUnits = 0;
    static boolean initialized = false;

    private static Logger log = LoggerFactory.getLogger(InsightPumpPlugin.class);

    private static volatile InsightPumpPlugin plugin;
    private Date lastDataTime = new Date(0);
    private TaskRunner taskRunner;
    private boolean fragmentEnabled = true;
    private boolean fragmentVisible = true;
    private PumpDescription pumpDescription = new PumpDescription();
    private Handler handler = new Handler();
    private StatusTaskRunner.StatusResult statusResult;
    private double basalRate = 0;
    private final TaskRunner.ResultCallback statusResultHandler = new TaskRunner.ResultCallback() {

        @Override
        public void onError(Exception e) {
            log("Got error taskrunner: " + e);

            if (e instanceof sugar.free.sightparser.error.DisconnectedError) {
                if (Helpers.ratelimit("insight-reconnect", 2)) {
                    Connector.connectToPump();
                    updateGui();
                }
            }
        }

        @Override
        public void onResult(Object result) {
            log("GOT STATUS RESULT!!!");
            statusResult = (StatusTaskRunner.StatusResult) result;
            processStatusResult();
            updateGui();


        }
    };
    private Connector connector;

    private InsightPumpPlugin() {
        log("InsightPumpPlugin");
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
        pumpDescription.tempMaxDuration = 24 * 60;


        pumpDescription.isSetBasalProfileCapable = false; // leave this for now
        pumpDescription.basalStep = 0.01d;
        pumpDescription.basalMinimumRate = 0.02d;

        pumpDescription.isRefillingCapable = false;

        this.connector = Connector.get();
        this.connector.init();
        log("back from init");
    }


    public static InsightPumpPlugin getPlugin() {
        if (plugin == null) {
            createInstance();
        }
        return plugin;
    }

    private static synchronized void createInstance() {
        if (plugin == null) {
            log("creating instance");
            plugin = new InsightPumpPlugin();
        }
    }

    private static void log(String msg) {
        android.util.Log.e("INSIGHTPUMP", msg);
    }

    @Override
    public String getFragmentClass() {
        return InsightPumpFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.insightpump);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.instance().getString(R.string.insightpump_shortname);
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
    public int getPreferencesId() {
        return R.xml.pref_insightpump;
    }

    @Override
    public int getType() {
        return PluginBase.PUMP;
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public boolean isSuspended() {
        return false;
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
        return false;
    }

    @Override
    public void connect(String reason) {
        if (!Config.NSCLIENT && !Config.G5UPLOADER)
            NSUpload.uploadDeviceStatus();
        lastDataTime = new Date();
    }

    @Override
    public void disconnect(String reason) {
        try {
            connector.getServiceConnector().disconnect();
        } catch (NullPointerException e) {
            log("Could not disconnect - null pointer: " + e);
        }
    }

    @Override
    public void stopConnecting() {
    }

    @Override
    public void getPumpStatus() {

        log("getPumpStatus");
        lastDataTime = new Date();
        if (Connector.get().isPumpConnected()) {
            log("is connected.. requesting status");
            //handler.removeCallbacks(statusTaskRunnable);
            handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        taskRunner = new StatusTaskRunner(connector.getServiceConnector());
                                        taskRunner.fetch(statusResultHandler);
                                    }
                                }
                    , 500);
        } else {
            log("not connected.. not requesting status");
        }
    }

    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        lastDataTime = new Date();
        // Do nothing here. we are using MainApp.getConfigBuilder().getActiveProfile().getProfile();
        PumpEnactResult result = new PumpEnactResult();
        result.success = false;
        Notification notification = new Notification(Notification.PROFILE_SET_OK, MainApp.sResources.getString(R.string.profile_set_ok), Notification.INFO, 60);
        MainApp.bus().post(new EventNewNotification(notification));
        return result;
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
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
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.bolusDelivered = detailedBolusInfo.insulin;
        result.carbsDelivered = detailedBolusInfo.carbs;
        result.enacted = result.bolusDelivered > 0 || result.carbsDelivered > 0;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);

        Double delivering = 0d;

  /*      while (delivering < detailedBolusInfo.insulin) {
            SystemClock.sleep(200);
            EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
            bolusingEvent.status = String.format(MainApp.sResources.getString(R.string.bolusdelivering), delivering);
            bolusingEvent.percent = Math.min((int) (delivering / detailedBolusInfo.insulin * 100), 100);
            MainApp.bus().post(bolusingEvent);
            delivering += 0.1d;
        }
        SystemClock.sleep(200); */
        deliverBolus((float) detailedBolusInfo.insulin); // actually request delivery
        // TODO handle status result
        EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
        bolusingEvent.status = String.format(MainApp.sResources.getString(R.string.bolusdelivered), detailedBolusInfo.insulin);
        bolusingEvent.percent = 100;
        MainApp.bus().post(bolusingEvent);
        //  SystemClock.sleep(1000);
        if (Config.logPumpComm)
            log.debug("Delivering treatment insulin: " + detailedBolusInfo.insulin + "U carbs: " + detailedBolusInfo.carbs + "g " + result);
        MainApp.bus().post(new EventInsightPumpUpdateGui());
        lastDataTime = new Date();
        MainApp.getConfigBuilder().addToHistoryTreatment(detailedBolusInfo);
        return result;
    }

    @Override
    public void stopBolusDelivering() {

    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, boolean enforceNew) {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        TemporaryBasal tempBasal = new TemporaryBasal();
        tempBasal.date = System.currentTimeMillis();
        tempBasal.isAbsolute = true;
        tempBasal.absoluteRate = absoluteRate;
        tempBasal.durationInMinutes = durationInMinutes;
        tempBasal.source = Source.USER;
        PumpEnactResult result = new PumpEnactResult();
        result.success = false;
        result.enacted = true;
        result.isTempCancel = false;
        result.absolute = absoluteRate;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        treatmentsInterface.addToHistoryTempBasal(tempBasal);
        if (Config.logPumpComm)
            log.debug("Setting temp basal absolute: " + result);
        MainApp.bus().post(new EventInsightPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }

    /*@Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, boolean enforceNew) {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = new PumpEnactResult();
        if (MainApp.getConfigBuilder().isTempBasalInProgress()) {
            result = cancelTempBasal(false);
            if (!result.success)
                return result;
        }
        TemporaryBasal tempBasal = new TemporaryBasal();
        tempBasal.date = System.currentTimeMillis();
        tempBasal.isAbsolute = false;
        tempBasal.percentRate = percent;
        tempBasal.durationInMinutes = durationInMinutes;
        tempBasal.source = Source.USER;
        result.success = true;
        result.enacted = true;
        result.percent = percent;
        result.isPercent = true;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        treatmentsInterface.addToHistoryTempBasal(tempBasal);
        if (Config.logPumpComm)
            log.debug("Settings temp basal percent: " + result);
        MainApp.bus().post(new EventInsightPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }*/


    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, boolean enforceNew) {
        try {
            SetTBRTaskRunner setTBRTaskRunner = new SetTBRTaskRunner(connector.getServiceConnector(), percent, durationInMinutes);
            setTBRTaskRunner.fetchBlockingCall();
            PumpEnactResult pumpEnactResult = new PumpEnactResult().success(true).enacted(true).isPercent(true).duration(durationInMinutes);
            pumpEnactResult.percent = percent;
            return pumpEnactResult;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new PumpEnactResult().success(false).enacted(false);
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        ExtendedBolusMessage extendedBolusMessage = new ExtendedBolusMessage();
        extendedBolusMessage.setAmount((float) ((double) insulin));
        extendedBolusMessage.setDuration((short) ((int) durationInMinutes));
        SingleMessageTaskRunner singleMessageTaskRunner = new SingleMessageTaskRunner(connector.getServiceConnector(), extendedBolusMessage);
        try {
            singleMessageTaskRunner.fetchBlockingCall();
            return new PumpEnactResult().enacted(true).success(true).bolusDelivered(insulin).duration(durationInMinutes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new PumpEnactResult().success(false).enacted(false);
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        CancelTBRMessage cancelTBRMessage = new CancelTBRMessage();
        SingleMessageTaskRunner singleMessageTaskRunner = new SingleMessageTaskRunner(connector.getServiceConnector(), cancelTBRMessage);
        try {
            singleMessageTaskRunner.fetchBlockingCall();
            return new PumpEnactResult().success(true).enacted(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new PumpEnactResult().success(false).enacted(false);
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        CancelBolusTaskRunner cancelBolusTaskRunner = new CancelBolusTaskRunner(connector.getServiceConnector(), BolusType.EXTENDED);
        try {
            cancelBolusTaskRunner.fetchBlockingCall();
            return new PumpEnactResult().success(true).enacted(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new PumpEnactResult().success(false).enacted(false);
    }

    /*
    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = cancelExtendedBolus();
        if (!result.success)
            return result;
        ExtendedBolus extendedBolus = new ExtendedBolus();
        extendedBolus.date = System.currentTimeMillis();
        extendedBolus.insulin = insulin;
        extendedBolus.durationInMinutes = durationInMinutes;
        extendedBolus.source = Source.USER;
        result.success = false;
        result.enacted = true;
        result.bolusDelivered = insulin;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        treatmentsInterface.addToHistoryExtendedBolus(extendedBolus);
        if (Config.logPumpComm)
            log.debug("Setting extended bolus: " + result);
        MainApp.bus().post(new EventInsightPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }
    */

   /* @Override
    public PumpEnactResult cancelTempBasal(boolean force) {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.isTempCancel = true;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        if (treatmentsInterface.isTempBasalInProgress()) {
            result.enacted = true;
            TemporaryBasal tempStop = new TemporaryBasal(System.currentTimeMillis());
            tempStop.source = Source.USER;
            treatmentsInterface.addToHistoryTempBasal(tempStop);
            //tempBasal = null;
            if (Config.logPumpComm)
                log.debug("Canceling temp basal: " + result);
            MainApp.bus().post(new EventInsightPumpUpdateGui());
        }
        lastDataTime = new Date();
        return result;
    }
*/
  /*  @Override
    public PumpEnactResult cancelExtendedBolus() {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = new PumpEnactResult();
        if (treatmentsInterface.isInHistoryExtendedBoluslInProgress()) {
            ExtendedBolus exStop = new ExtendedBolus(System.currentTimeMillis());
            exStop.source = Source.USER;
            treatmentsInterface.addToHistoryExtendedBolus(exStop);
        }
        result.success = false;
        result.enacted = true;
        result.isTempCancel = true;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        if (Config.logPumpComm)
            log.debug("Canceling extended basal: " + result);
        MainApp.bus().post(new EventInsightPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }*/

    @Override
    public JSONObject getJSONStatus() {

        // TODO review

        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", batteryPercent);
            status.put("status", "normal");
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            try {
                extended.put("ActiveProfile", MainApp.getConfigBuilder().getProfileName());
            } catch (Exception e) {
            }
            TemporaryBasal tb = MainApp.getConfigBuilder().getTempBasalFromHistory(System.currentTimeMillis());
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.tempBasalConvertedToAbsolute(System.currentTimeMillis()));
                extended.put("TempBasalStart", DateUtil.dateAndTimeString(tb.date));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }
            ExtendedBolus eb = MainApp.getConfigBuilder().getExtendedBolusFromHistory(System.currentTimeMillis());
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.absoluteRate());
                extended.put("ExtendedBolusStart", DateUtil.dateAndTimeString(eb.date));
                extended.put("ExtendedBolusRemaining", eb.getPlannedRemainingMinutes());
            }
            status.put("timestamp", DateUtil.toISOString(new Date()));

            pump.put("battery", battery);
            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("reservoir", reservoirInUnits);
            pump.put("clock", DateUtil.toISOString(new Date()));
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
        return "Insight Pump";
    }


    private void processStatusResult() {
        if (statusResult != null) {
            batteryPercent = statusResult.getBatteryAmountMessage().getBatteryAmount();
            reservoirInUnits = (int) statusResult.getCartridgeAmountMessage().getCartridgeAmount();
            basalRate = statusResult.getCurrentBasalMessage().getCurrentBasalAmount();
            initialized = true; // basic communication test
        }
    }

    private void deliverBolus(float bolusValue) {
        log("!!!!!!!!!! DeliverBolus: " + bolusValue);


        // Bare sanity checking
        if (bolusValue == 0) return;
        if (bolusValue > 10) return;
        if (bolusValue < 0) return;

        // TODO check limits here?

        StandardBolusMessage message = new StandardBolusMessage();
        message.setAmount(bolusValue);
        final SingleMessageTaskRunner taskRunner = new SingleMessageTaskRunner(Connector.get().getServiceConnector(), message);


        taskRunner.fetch(new TaskRunner.ResultCallback() {
            @Override
            public void onResult(Object result) {
                log("Bolus result: " + result.toString());
            }

            @Override
            public void onError(Exception e) {
                log("Bolus error");
            }
        });

    }

    private static void updateGui() {
        MainApp.bus().post(new EventInsightPumpUpdateGui());
    }

}
