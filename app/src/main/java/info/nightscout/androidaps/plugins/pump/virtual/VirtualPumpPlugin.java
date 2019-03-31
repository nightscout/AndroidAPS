package info.nightscout.androidaps.plugins.pump.virtual;

import android.os.SystemClock;

import com.squareup.otto.Subscribe;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
import info.nightscout.androidaps.events.EventPreferenceChange;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.virtual.events.EventVirtualPumpUpdateGui;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;


/**
 * Created by mike on 05.08.2016.
 */
public class VirtualPumpPlugin extends PluginBase implements PumpInterface {
    private Logger log = LoggerFactory.getLogger(L.PUMP);

    Integer batteryPercent = 50;
    Integer reservoirInUnits = 50;
    private static VirtualPumpPlugin plugin = null;
    private boolean fromNSAreCommingFakedExtendedBoluses = false;
    private PumpType pumpType = null;
    private long lastDataTime = 0;
    private PumpDescription pumpDescription = new PumpDescription();

    public VirtualPumpPlugin() {
        super(new PluginDescription()
                .mainType(PluginType.PUMP)
                .fragmentClass(VirtualPumpFragment.class.getName())
                .pluginName(R.string.virtualpump)
                .shortName(R.string.virtualpump_shortname)
                .preferencesId(R.xml.pref_virtualpump)
                .neverVisible(Config.NSCLIENT)
                .description(R.string.description_pump_virtual)
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

    public static VirtualPumpPlugin getPlugin() {
        if (plugin == null)
            plugin = new VirtualPumpPlugin();
        plugin.loadFakingStatus();
        return plugin;
    }

    private void loadFakingStatus() {
        fromNSAreCommingFakedExtendedBoluses = SP.getBoolean(R.string.key_fromNSAreCommingFakedExtendedBoluses, false);
    }

    public boolean getFakingStatus() {
        return fromNSAreCommingFakedExtendedBoluses;
    }

    public void setFakingStatus(boolean newStatus) {
        fromNSAreCommingFakedExtendedBoluses = newStatus;
        SP.putBoolean(R.string.key_fromNSAreCommingFakedExtendedBoluses, fromNSAreCommingFakedExtendedBoluses);
    }

    @Override
    protected void onStart() {
        super.onStart();
        MainApp.bus().register(this);
        refreshConfiguration();
    }

    @Override
    protected void onStop() {
        MainApp.bus().unregister(this);
    }

    @Subscribe
    public void onStatusEvent(final EventPreferenceChange s) {
        if (s.isChanged(R.string.key_virtualpump_type))
            refreshConfiguration();
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return (Config.NSCLIENT) && fromNSAreCommingFakedExtendedBoluses;
    }

    @Override
    public PumpEnactResult loadTDDs() {
        //no result, could read DB in the future?
        return new PumpEnactResult();
    }

    @Override
    public List<CustomAction> getCustomActions() {
        return null;
    }

    @Override
    public void executeCustomAction(CustomActionType customActionType) {

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
    public boolean isHandshakeInProgress() {
        return false;
    }

    @Override
    public void finishHandshaking() {
    }

    @Override
    public void connect(String reason) {
        if (!Config.NSCLIENT)
            NSUpload.uploadDeviceStatus();
        lastDataTime = System.currentTimeMillis();
    }

    @Override
    public void disconnect(String reason) {
    }

    @Override
    public void stopConnecting() {
    }

    @Override
    public void getPumpStatus() {
        lastDataTime = System.currentTimeMillis();
    }

    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        lastDataTime = System.currentTimeMillis();
        // Do nothing here. we are using ConfigBuilderPlugin.getPlugin().getActiveProfile().getProfile();
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
    public long lastDataTime() {
        return lastDataTime;
    }

    @Override
    public double getBaseBasalRate() {
        Profile profile = ProfileFunctions.getInstance().getProfile();
        if (profile != null)
            return profile.getBasal();
        else
            return 0d;
    }


    @Override
    public double getReservoirLevel() { return reservoirInUnits; }

    @Override
    public int getBatteryLevel() { return batteryPercent; }

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
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Delivering treatment insulin: " + detailedBolusInfo.insulin + "U carbs: " + detailedBolusInfo.carbs + "g " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = System.currentTimeMillis();
        TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, false);
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
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Setting temp basal absolute: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = System.currentTimeMillis();
        return result;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        TemporaryBasal tempBasal = new TemporaryBasal()
                .date(System.currentTimeMillis())
                .percent(percent)
                .duration(durationInMinutes)
                .source(Source.USER);
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.enacted = true;
        result.percent = percent;
        result.isPercent = true;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.gs(R.string.virtualpump_resultok);
        TreatmentsPlugin.getPlugin().addToHistoryTempBasal(tempBasal);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Settings temp basal percent: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = System.currentTimeMillis();
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        PumpEnactResult result = cancelExtendedBolus();
        if (!result.success)
            return result;

        ExtendedBolus extendedBolus = new ExtendedBolus()
                .date(System.currentTimeMillis())
                .insulin(insulin)
                .durationInMinutes(durationInMinutes)
                .source(Source.USER);
        result.success = true;
        result.enacted = true;
        result.bolusDelivered = insulin;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.gs(R.string.virtualpump_resultok);
        TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Setting extended bolus: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = System.currentTimeMillis();
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
            if (L.isEnabled(L.PUMPCOMM))
                log.debug("Canceling temp basal: " + result);
            MainApp.bus().post(new EventVirtualPumpUpdateGui());
        }
        lastDataTime = System.currentTimeMillis();
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
        if (L.isEnabled(L.PUMPCOMM))
            log.debug("Canceling extended bolus: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = System.currentTimeMillis();
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
            } catch (Exception ignored) {
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

    public PumpType getPumpType() {
        return pumpType;
    }

    @Override
    public boolean canHandleDST() {
        return true;
    }


    public void refreshConfiguration() {
        String pumptype = SP.getString(R.string.key_virtualpump_type, "Generic AAPS");

        PumpType pumpTypeNew = PumpType.getByDescription(pumptype);

        if (L.isEnabled(L.PUMP))
            log.debug("Pump in configuration: {}, PumpType object: {}", pumptype, pumpTypeNew);

        if (pumpType == pumpTypeNew)
            return;

        if (L.isEnabled(L.PUMP))
            log.debug("New pump configuration found ({}), changing from previous ({})", pumpTypeNew, pumpType);

        pumpDescription.setPumpDescription(pumpTypeNew);

        this.pumpType = pumpTypeNew;

    }

}
