package info.nightscout.androidaps.plugins.PumpVirtual;

import android.os.SystemClock;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.Overview.notifications.Notification;
import info.nightscout.androidaps.plugins.PumpVirtual.events.EventVirtualPumpUpdateGui;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.NSUpload;
import info.nightscout.utils.SP;

/**
 * Created by mike on 05.08.2016.
 */
public class VirtualPumpPlugin extends PluginBase implements PumpInterface {
    private static Logger log = LoggerFactory.getLogger(VirtualPumpPlugin.class);

    private static VirtualPumpPlugin plugin = null;

    public static VirtualPumpPlugin getPlugin() {
        loadFakingStatus();
        if (plugin == null)
            plugin = new VirtualPumpPlugin();
        return plugin;
    }

    static Integer batteryPercent = 50;
    static Integer reservoirInUnits = 50;

    private Date lastDataTime = new Date(0);

    private static boolean fromNSAreCommingFakedExtendedBoluses = false;

    private PumpDescription pumpDescription = new PumpDescription();

    private static void loadFakingStatus() {
        fromNSAreCommingFakedExtendedBoluses = SP.getBoolean(R.string.key_fromNSAreCommingFakedExtendedBoluses, false);
    }

    public static void setFakingStatus(boolean newStatus) {
        fromNSAreCommingFakedExtendedBoluses = newStatus;
        SP.putBoolean(R.string.key_fromNSAreCommingFakedExtendedBoluses, fromNSAreCommingFakedExtendedBoluses);
    }

    public static boolean getFakingStatus() {
        return fromNSAreCommingFakedExtendedBoluses;
    }

    public VirtualPumpPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.PUMP)
                .fragmentClass(VirtualPumpFragment.class.getName())
                .pluginName(R.string.virtualpump)
                .shortName(R.string.virtualpump_shortname)
                .preferencesId(R.xml.pref_virtualpump)
        );
        pumpDescription.isBolusCapable = true;
        pumpDescription.bolusStep = 0.1d;

        pumpDescription.isExtendedBolusCapable = true;
        pumpDescription.extendedBolusStep = 0.05d;
        pumpDescription.extendedBolusDurationStep = 30;
        pumpDescription.extendedBolusMaxDuration = 8 * 60;

        pumpDescription.isTempBasalCapable = true;
        pumpDescription.tempBasalStyle = PumpDescription.PERCENT | PumpDescription.ABSOLUTE;

        pumpDescription.maxTempPercent = 500;
        pumpDescription.tempPercentStep = 10;

        pumpDescription.tempDurationStep = 30;
        pumpDescription.tempDurationStep15mAllowed = true;
        pumpDescription.tempDurationStep30mAllowed = true;
        pumpDescription.tempMaxDuration = 24 * 60;


        pumpDescription.isSetBasalProfileCapable = true;
        pumpDescription.basalStep = 0.01d;
        pumpDescription.basalMinimumRate = 0.01d;

        pumpDescription.isRefillingCapable = true;

        pumpDescription.storesCarbInfo = false;
        pumpDescription.is30minBasalRatesCapable = true;
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return (Config.NSCLIENT || Config.G5UPLOADER) && fromNSAreCommingFakedExtendedBoluses;
    }

    @Override
    public PumpEnactResult loadTDDs() {
        //no result, could read DB in the future?
        PumpEnactResult result = new PumpEnactResult();
        return result;
    }

    @Override
    public boolean isInitialized() {
        return true;
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
        return true;
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
    }

    @Override
    public void stopConnecting() {
    }

    @Override
    public void getPumpStatus() {
        lastDataTime = new Date();
    }

    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        lastDataTime = new Date();
        // Do nothing here. we are using MainApp.getConfigBuilder().getActiveProfile().getProfile();
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        Notification notification = new Notification(Notification.PROFILE_SET_OK, MainApp.gs(R.string.profile_set_ok), Notification.INFO, 60);
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
        Profile profile = MainApp.getConfigBuilder().getProfile();
        if (profile != null)
            return profile.getBasal();
        else
            return 0d;
    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.bolusDelivered = detailedBolusInfo.insulin;
        result.carbsDelivered = detailedBolusInfo.carbs;
        result.enacted = result.bolusDelivered > 0 || result.carbsDelivered > 0;
        result.comment = MainApp.gs(R.string.virtualpump_resultok);

        Double delivering = 0d;

        while (delivering < detailedBolusInfo.insulin) {
            SystemClock.sleep(200);
            EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
            bolusingEvent.status = String.format(MainApp.gs(R.string.bolusdelivering), delivering);
            bolusingEvent.percent = Math.min((int) (delivering / detailedBolusInfo.insulin * 100), 100);
            MainApp.bus().post(bolusingEvent);
            delivering += 0.1d;
        }
        SystemClock.sleep(200);
        EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
        bolusingEvent.status = String.format(MainApp.gs(R.string.bolusdelivered), detailedBolusInfo.insulin);
        bolusingEvent.percent = 100;
        MainApp.bus().post(bolusingEvent);
        SystemClock.sleep(1000);
        if (Config.logPumpComm)
            log.debug("Delivering treatment insulin: " + detailedBolusInfo.insulin + "U carbs: " + detailedBolusInfo.carbs + "g " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = new Date();
        TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo);
        return result;
    }

    @Override
    public void stopBolusDelivering() {

    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        TemporaryBasal tempBasal = new TemporaryBasal()
                .date(System.currentTimeMillis())
                .absolute(absoluteRate)
                .duration(durationInMinutes)
                .source(Source.USER);
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.enacted = true;
        result.isTempCancel = false;
        result.absolute = absoluteRate;
        result.duration = durationInMinutes;
        result.comment = MainApp.gs(R.string.virtualpump_resultok);
        TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempBasal);
        if (Config.logPumpComm)
            log.debug("Setting temp basal absolute: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        PumpEnactResult result = new PumpEnactResult();
        if (TreatmentsPlugin.getPlugin().isTempBasalInProgress()) {
            result = cancelTempBasal(false);
            if (!result.success)
                return result;
        }
        TemporaryBasal tempBasal = new TemporaryBasal()
                .date(System.currentTimeMillis())
                .percent(percent)
                .duration(durationInMinutes)
                .source(Source.USER);
        result.success = true;
        result.enacted = true;
        result.percent = percent;
        result.isPercent = true;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.gs(R.string.virtualpump_resultok);
        TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempBasal);
        if (Config.logPumpComm)
            log.debug("Settings temp basal percent: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        PumpEnactResult result = cancelExtendedBolus();
        if (!result.success)
            return result;
        ExtendedBolus extendedBolus = new ExtendedBolus();
        extendedBolus.date = System.currentTimeMillis();
        extendedBolus.insulin = insulin;
        extendedBolus.durationInMinutes = durationInMinutes;
        extendedBolus.source = Source.USER;
        result.success = true;
        result.enacted = true;
        result.bolusDelivered = insulin;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.gs(R.string.virtualpump_resultok);
        TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
        if (Config.logPumpComm)
            log.debug("Setting extended bolus: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean force) {
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.isTempCancel = true;
        result.comment = MainApp.gs(R.string.virtualpump_resultok);
        if (TreatmentsPlugin.getPlugin().isTempBasalInProgress()) {
            result.enacted = true;
            TemporaryBasal tempStop = new TemporaryBasal().date(System.currentTimeMillis()).source(Source.USER);
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempStop);
            //tempBasal = null;
            if (Config.logPumpComm)
                log.debug("Canceling temp basal: " + result);
            MainApp.bus().post(new EventVirtualPumpUpdateGui());
        }
        lastDataTime = new Date();
        return result;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult();
        if (TreatmentsPlugin.getPlugin().isInHistoryExtendedBoluslInProgress()) {
            ExtendedBolus exStop = new ExtendedBolus(System.currentTimeMillis());
            exStop.source = Source.USER;
            TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(exStop);
        }
        result.success = true;
        result.enacted = true;
        result.isTempCancel = true;
        result.comment = MainApp.gs(R.string.virtualpump_resultok);
        if (Config.logPumpComm)
            log.debug("Canceling extended basal: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public JSONObject getJSONStatus(Profile profile, String profileName) {
        long now = System.currentTimeMillis();
        if (!SP.getBoolean("virtualpump_uploadstatus", false)) {
            return null;
        }
        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", batteryPercent);
            status.put("status", "normal");
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            try {
                extended.put("ActiveProfile", profileName);
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
        return "VirtualPump";
    }

    @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @Override
    public String shortStatus(boolean veryShort) {
        return "Virtual Pump";
    }

}
