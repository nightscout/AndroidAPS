package info.nightscout.androidaps.plugins.PumpVirtual;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.InsulinInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.TreatmentsInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.PumpVirtual.events.EventVirtualPumpUpdateGui;
import info.nightscout.androidaps.plugins.NSClientInternal.data.NSProfile;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DateUtil;

/**
 * Created by mike on 05.08.2016.
 */
public class VirtualPumpPlugin implements PluginBase, PumpInterface {
    private static Logger log = LoggerFactory.getLogger(VirtualPumpPlugin.class);

    public static Double defaultBasalValue = 0.2d;

    public static Integer batteryPercent = 50;
    public static Integer reservoirInUnits = 50;

    Date lastDataTime = new Date(0);

    boolean fragmentEnabled = true;
    boolean fragmentVisible = true;

    PumpDescription pumpDescription = new PumpDescription();

    public VirtualPumpPlugin() {
        pumpDescription.isBolusCapable = true;
        pumpDescription.bolusStep = 0.1d;

        pumpDescription.isExtendedBolusCapable = true;
        pumpDescription.extendedBolusStep = 0.2d;

        pumpDescription.isTempBasalCapable = true;
        pumpDescription.lowTempBasalStyle = PumpDescription.ABSOLUTE | PumpDescription.PERCENT;
        pumpDescription.highTempBasalStyle = PumpDescription.ABSOLUTE | PumpDescription.PERCENT;
        pumpDescription.maxHighTempPercent = 600;
        pumpDescription.maxHighTempAbsolute = 10;
        pumpDescription.lowTempPercentStep = 5;
        pumpDescription.lowTempAbsoluteStep = 0.1;
        pumpDescription.lowTempPercentDuration = 30;
        pumpDescription.lowTempAbsoluteDuration = 30;
        pumpDescription.highTempPercentStep = 10;
        pumpDescription.highTempAbsoluteStep = 0.05d;
        pumpDescription.highTempPercentDuration = 30;
        pumpDescription.highTempAbsoluteDuration = 30;

        pumpDescription.isSetBasalProfileCapable = true;
        pumpDescription.basalStep = 0.01d;
        pumpDescription.basalMinimumRate = 0.04d;

        pumpDescription.isRefillingCapable = false;
    }

    @Override
    public String getFragmentClass() {
        return VirtualPumpFragment.class.getName();
    }

    @Override
    public String getName() {
        return MainApp.instance().getString(R.string.virtualpump);
    }

    @Override
    public String getNameShort() {
        String name = MainApp.sResources.getString(R.string.virtualpump_shortname);
        if (!name.trim().isEmpty()){
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
    public String treatmentPlugin() {
        return TreatmentsPlugin.class.getName();
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return false;
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
    public int setNewBasalProfile(NSProfile profile) {
        // Do nothing here. we are using MainApp.getConfigBuilder().getActiveProfile().getProfile();
        lastDataTime = new Date();
        return SUCCESS;
    }

    @Override
    public boolean isThisProfileSet(NSProfile profile) {
        return false;
    }

    @Override
    public Date lastDataTime() {
        return lastDataTime;
    }

    @Override
    public void refreshDataFromPump(String reason) {
        MainApp.getConfigBuilder().uploadDeviceStatus();
        lastDataTime = new Date();
    }

    @Override
    public double getBaseBasalRate() {
        NSProfile profile = ConfigBuilderPlugin.getActiveProfile().getProfile();
        if (profile == null)
            return defaultBasalValue;
        return profile.getBasal(profile.secondsFromMidnight());
    }

    @Override
    public PumpEnactResult deliverTreatment(InsulinInterface insulinType, Double insulin, Integer carbs, Context context) {
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.bolusDelivered = insulin;
        result.carbsDelivered = carbs;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);

        Double delivering = 0d;

        while (delivering < insulin) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
            }
            EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
            bolusingEvent.status = String.format(MainApp.sResources.getString(R.string.bolusdelivering), delivering);
            bolusingEvent.percent = Math.min((int) (delivering / insulin * 100), 100);
            MainApp.bus().post(bolusingEvent);
            delivering += 0.1d;
        }
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
        }
        EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
        bolusingEvent.status = String.format(MainApp.sResources.getString(R.string.bolusdelivered), insulin);
        bolusingEvent.percent = 100;
        MainApp.bus().post(bolusingEvent);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        if (Config.logPumpComm)
            log.debug("Delivering treatment insulin: " + insulin + "U carbs: " + carbs + "g " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public void stopBolusDelivering() {

    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes) {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        TemporaryBasal tempBasal = new TemporaryBasal();
        tempBasal.date = new Date().getTime();
        tempBasal.isAbsolute = true;
        tempBasal.absoluteRate = absoluteRate;
        tempBasal.durationInMinutes = durationInMinutes;
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.enacted = true;
        result.isTempCancel = false;
        result.absolute = absoluteRate;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        treatmentsInterface.tempBasalStart(tempBasal);
        if (Config.logPumpComm)
            log.debug("Setting temp basal absolute: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes) {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = new PumpEnactResult();
        if (MainApp.getConfigBuilder().isTempBasalInProgress()) {
            result = cancelTempBasal();
            if (!result.success)
                return result;
        }
        TemporaryBasal tempBasal = new TemporaryBasal();
        tempBasal.date = new Date().getTime();
        tempBasal.isAbsolute = false;
        tempBasal.percentRate = percent;
        tempBasal.durationInMinutes = durationInMinutes;
        result.success = true;
        result.enacted = true;
        result.percent = percent;
        result.isPercent = true;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        treatmentsInterface.tempBasalStart(tempBasal);
        if (Config.logPumpComm)
            log.debug("Settings temp basal percent: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = cancelExtendedBolus();
        if (!result.success)
            return result;
        ExtendedBolus extendedBolus = new ExtendedBolus();
        extendedBolus.date = new Date().getTime();
        extendedBolus.insulin = insulin;
        extendedBolus.durationInMinutes = durationInMinutes;
        result.success = true;
        result.enacted = true;
        result.bolusDelivered = insulin;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        treatmentsInterface.extendedBolusStart(extendedBolus);
        if (Config.logPumpComm)
            log.debug("Setting extended bolus: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal() {
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.isTempCancel = true;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        if (treatmentsInterface.isTempBasalInProgress()) {
            result.enacted = true;
            treatmentsInterface.tempBasalStop(new Date().getTime());
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
        TreatmentsInterface treatmentsInterface = MainApp.getConfigBuilder();
        PumpEnactResult result = new PumpEnactResult();
        if (treatmentsInterface.isExtendedBoluslInProgress()) {
            treatmentsInterface.extendedBolusStop(new Date().getTime());
        }
        result.success = true;
        result.enacted = true;
        result.isTempCancel = true;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        if (Config.logPumpComm)
            log.debug("Canceling extended basal: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        lastDataTime = new Date();
        return result;
    }

    @Override
    public JSONObject getJSONStatus() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        if (!preferences.getBoolean("virtualpump_uploadstatus", false)) {
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
                extended.put("ActiveProfile", MainApp.getConfigBuilder().getActiveProfile().getProfile().getActiveProfile());
            } catch (Exception e) {}
            TemporaryBasal tb = MainApp.getConfigBuilder().getTempBasal(new Date().getTime());
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.tempBasalConvertedToAbsolute(new Date().getTime()));
                extended.put("TempBasalStart", DateUtil.dateAndTimeString(tb.date));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }
            ExtendedBolus eb = MainApp.getConfigBuilder().getExtendedBolus(new Date().getTime());
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
