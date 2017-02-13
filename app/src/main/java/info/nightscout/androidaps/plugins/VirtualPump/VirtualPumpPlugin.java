package info.nightscout.androidaps.plugins.VirtualPump;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Date;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.Config;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.TempBasal;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.VirtualPump.events.EventVirtualPumpUpdateGui;
import info.nightscout.client.data.NSProfile;
import info.nightscout.utils.DateUtil;

/**
 * Created by mike on 05.08.2016.
 */
public class VirtualPumpPlugin implements PluginBase, PumpInterface {
    private static Logger log = LoggerFactory.getLogger(VirtualPumpPlugin.class);

    public static Double defaultBasalValue = 0.2d;

    public static Integer batteryPercent = 50;
    public static Integer reservoirInUnits = 50;

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
    public boolean isTempBasalInProgress() {
        return getTempBasal() != null;
    }

    @Override
    public boolean isExtendedBoluslInProgress() {
        return getExtendedBolus() != null;
    }

    @Override
    public int setNewBasalProfile(NSProfile profile) {
        // Do nothing here. we are using MainApp.getConfigBuilder().getActiveProfile().getProfile();
        return SUCCESS;
    }

    @Override
    public boolean isThisProfileSet(NSProfile profile) {
        return false;
    }

    @Override
    public Date lastStatusTime() {
        return new Date();
    }

    @Override
    public void updateStatus(String reason) {
        // do nothing
    }

    @Override
    public double getBaseBasalRate() {
        NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
        if (profile == null)
            return defaultBasalValue;
        return profile.getBasal(profile.secondsFromMidnight());
    }

    @Override
    public double getTempBasalAbsoluteRate() {
        if (!isTempBasalInProgress())
            return 0;
        if (getTempBasal().isAbsolute) {
            return getTempBasal().absolute;
        } else {
            NSProfile profile = MainApp.getConfigBuilder().getActiveProfile().getProfile();
            if (profile == null)
                return defaultBasalValue;
            Double baseRate = profile.getBasal(profile.secondsFromMidnight());
            Double tempRate = baseRate * (getTempBasal().percent / 100d);
            return baseRate + tempRate;
        }
    }

    @Override
    public TempBasal getTempBasal() {
        return MainApp.getConfigBuilder().getActiveTempBasals().getTempBasal(new Date());
    }

    @Override
    public TempBasal getExtendedBolus() {
        return MainApp.getConfigBuilder().getActiveTempBasals().getExtendedBolus(new Date());
    }

    @Override
    public double getTempBasalRemainingMinutes() {
        if (!isTempBasalInProgress())
            return 0;
        return getTempBasal().getPlannedRemainingMinutes();
    }

    @Override
    public TempBasal getTempBasal(Date time) {
        return MainApp.getConfigBuilder().getActiveTempBasals().getTempBasal(time);
    }

    @Override
    public PumpEnactResult deliverTreatment(Double insulin, Integer carbs, Context context) {
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
        return result;
    }

    @Override
    public void stopBolusDelivering() {

    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes) {
        PumpEnactResult result = cancelTempBasal();
        if (!result.success)
            return result;
        TempBasal tempBasal = new TempBasal();
        tempBasal.timeStart = new Date();
        tempBasal.isAbsolute = true;
        tempBasal.absolute = absoluteRate;
        tempBasal.duration = durationInMinutes;
        result.success = true;
        result.enacted = true;
        result.isTempCancel = false;
        result.absolute = absoluteRate;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        try {
            MainApp.instance().getDbHelper().getDaoTempBasals().create(tempBasal);
        } catch (SQLException e) {
            e.printStackTrace();
            result.success = false;
            result.comment = MainApp.instance().getString(R.string.virtualpump_sqlerror);
        }
        if (Config.logPumpComm)
            log.debug("Setting temp basal absolute: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        return result;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes) {
        PumpEnactResult result = new PumpEnactResult();
        if (isTempBasalInProgress()) {
            result = cancelTempBasal();
            if (!result.success)
                return result;
        }
        TempBasal tempBasal = new TempBasal();
        tempBasal.timeStart = new Date();
        tempBasal.isAbsolute = false;
        tempBasal.percent = percent;
        tempBasal.duration = durationInMinutes;
        result.success = true;
        result.enacted = true;
        result.percent = percent;
        result.isPercent = true;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        try {
            MainApp.instance().getDbHelper().getDaoTempBasals().create(tempBasal);
        } catch (SQLException e) {
            e.printStackTrace();
            result.success = false;
            result.comment = MainApp.instance().getString(R.string.virtualpump_sqlerror);
        }
        if (Config.logPumpComm)
            log.debug("Settings temp basal percent: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        PumpEnactResult result = cancelExtendedBolus();
        if (!result.success)
            return result;
        TempBasal extendedBolus = new TempBasal();
        extendedBolus.timeStart = new Date();
        extendedBolus.isExtended = true;
        extendedBolus.absolute = insulin * 60d / durationInMinutes;
        extendedBolus.duration = durationInMinutes;
        extendedBolus.isAbsolute = true;
        result.success = true;
        result.enacted = true;
        result.bolusDelivered = insulin;
        result.isTempCancel = false;
        result.duration = durationInMinutes;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        try {
            MainApp.instance().getDbHelper().getDaoTempBasals().create(extendedBolus);
        } catch (SQLException e) {
            e.printStackTrace();
            result.success = false;
            result.enacted = false;
            result.comment = MainApp.instance().getString(R.string.virtualpump_sqlerror);
        }
        if (Config.logPumpComm)
            log.debug("Setting extended bolus: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal() {
        PumpEnactResult result = new PumpEnactResult();
        result.success = true;
        result.isTempCancel = true;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        if (isTempBasalInProgress()) {
            result.enacted = true;
            TempBasal tb = getTempBasal();
            tb.timeEnd = new Date();
            try {
                MainApp.instance().getDbHelper().getDaoTempBasals().update(tb);
                //tempBasal = null;
                if (Config.logPumpComm)
                    log.debug("Canceling temp basal: " + result);
                MainApp.bus().post(new EventVirtualPumpUpdateGui());
            } catch (SQLException e) {
                e.printStackTrace();
                result.success = false;
                result.enacted = false;
                result.comment = MainApp.instance().getString(R.string.virtualpump_sqlerror);
            }
        }
        return result;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = new PumpEnactResult();
        if (isExtendedBoluslInProgress()) {
            TempBasal extendedBolus = getExtendedBolus();
            extendedBolus.timeEnd = new Date();
            try {
                MainApp.instance().getDbHelper().getDaoTempBasals().update(extendedBolus);
            } catch (SQLException e) {
                e.printStackTrace();
                result.success = false;
                result.comment = MainApp.instance().getString(R.string.virtualpump_sqlerror);
            }
        }
        result.success = true;
        result.enacted = true;
        result.isTempCancel = true;
        result.comment = MainApp.instance().getString(R.string.virtualpump_resultok);
        if (Config.logPumpComm)
            log.debug("Canceling extended basal: " + result);
        MainApp.bus().post(new EventVirtualPumpUpdateGui());
        return result;
    }

    @Override
    public JSONObject getJSONStatus() {
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
            TempBasal tb;
            if ((tb = getTempBasal()) != null) {
                status.put("tempbasalpct", tb.percent);
                status.put("tempbasalstart", DateUtil.toISOString(tb.timeStart));
                status.put("tempbasalremainmin", tb.getPlannedRemainingMinutes());
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
