package info.nightscout.androidaps.plugins.PumpCommon;

import android.support.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.ProfileStore;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.PumpCommon.data.PumpStatus;
import info.nightscout.androidaps.plugins.PumpCommon.driver.PumpDriverInterface;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DateUtil;

/**
 * Created by andy on 23.04.18.
 */

public abstract class PumpPluginAbstract extends PluginBase implements PumpInterface, ConstraintsInterface {

    private static final Logger LOG = LoggerFactory.getLogger(PumpPluginAbstract.class);
    protected boolean pumpServiceRunning = false;


    protected static PumpPluginAbstract plugin = null;
    protected PumpDriverInterface pumpDriver;
    protected PumpStatus pumpStatus;
    protected String internalName;


    protected PumpPluginAbstract(PumpDriverInterface pumpDriverInterface, //
                                 String internalName, //
                                 String fragmentClassName, //
                                 int pluginName, //
                                 int pluginShortName) {
        super(new PluginDescription()
                .mainType(PluginType.PUMP)
                .fragmentClass(fragmentClassName)
                .pluginName(pluginName)
                .shortName(pluginShortName)
        );

        this.pumpDriver = pumpDriverInterface;
        this.pumpStatus = this.pumpDriver.getPumpStatusData();
        this.internalName = internalName;
    }


    protected String getInternalName() {
        return this.internalName;
    }

    protected abstract void startPumpService();

    protected abstract void stopPumpService();


    public PumpStatus getPumpStatusData() {
        return pumpDriver.getPumpStatusData();
    }


    public boolean isInitialized() {
        return pumpDriver.isInitialized();
    }

    public boolean isSuspended() {
        return pumpDriver.isSuspended();
    }

    public boolean isBusy() {
        return pumpDriver.isBusy();
    }


    public boolean isConnected() {
        return pumpDriver.isConnected();
    }


    public boolean isConnecting() {
        return pumpDriver.isConnecting();
    }


    public void connect(String reason) {
        pumpDriver.connect(reason);
    }


    public void disconnect(String reason) {
        pumpDriver.disconnect(reason);
    }


    public void stopConnecting() {
        pumpDriver.stopConnecting();
    }


    public void getPumpStatus() {
        pumpDriver.getPumpStatus();
    }


    // Upload to pump new basal profile
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        return pumpDriver.setNewBasalProfile(profile);
    }


    public boolean isThisProfileSet(Profile profile) {
        return pumpDriver.isThisProfileSet(profile);
    }


    public Date lastDataTime() {
        return pumpDriver.lastDataTime();
    }


    public double getBaseBasalRate() {
        return pumpDriver.getBaseBasalRate();
    } // base basal rate, not temp basal


    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        return pumpDriver.deliverTreatment(detailedBolusInfo);
    }


    public void stopBolusDelivering() {
        pumpDriver.stopBolusDelivering();
    }


    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        return pumpDriver.setTempBasalAbsolute(absoluteRate, durationInMinutes, profile, enforceNew);
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        return pumpDriver.setTempBasalPercent(percent, durationInMinutes, profile, enforceNew);
    }


    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        return pumpDriver.setExtendedBolus(insulin, durationInMinutes);
    }
    //some pumps might set a very short temp close to 100% as cancelling a temp can be noisy
    //when the cancel request is requested by the user (forced), the pump should always do a real cancel


    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        return pumpDriver.cancelTempBasal(enforceNew);
    }


    public PumpEnactResult cancelExtendedBolus() {
        return pumpDriver.cancelExtendedBolus();
    }

    // Status to be passed to NS


//    public JSONObject getJSONStatus(Profile profile, String profileName) {
//        return pumpDriver.getJSONStatus(profile, profileName);
//    }


    public String deviceID() {
        return pumpDriver.deviceID();
    }

    // Pump capabilities


    public PumpDescription getPumpDescription() {
        return pumpDriver.getPumpDescription();
    }

    // Short info for SMS, Wear etc


    public String shortStatus(boolean veryShort) {
        return pumpDriver.shortStatus(veryShort);
    }


    public boolean isFakingTempsByExtendedBoluses() {
        return pumpDriver.isInitialized();
    }


    @Override
    public PumpEnactResult loadTDDs() {
        return this.pumpDriver.loadTDDs();
    }

    // Constraints interface

//    @Override
//    public boolean isLoopEnabled() {
//        return true;
//    }
//
//    @Override
//    public boolean isClosedModeEnabled() {
//        return true;
//    }
//
//    @Override
//    public boolean isAutosensModeEnabled() {
//        return true;
//    }
//
//    @Override
//    public boolean isAMAModeEnabled() {
//        return true;
//    }
//
//    @Override
//    public boolean isSMBModeEnabled() {
//        return true;
//    }
//
//    @Override
//    public Double applyBasalConstraints(Double absoluteRate) {
//        this.pumpStatus.constraintBasalRateAbsolute = absoluteRate;
//        return absoluteRate;
//    }
//
//    @Override
//    public Integer applyBasalConstraints(Integer percentRate) {
//        this.pumpStatus.constraintBasalRatePercent = percentRate;
//        return percentRate;
//    }
//
//    @Override
//    public Double applyBolusConstraints(Double insulin) {
//        this.pumpStatus.constraintBolus = insulin;
//        return insulin;
//    }
//
//    @Override
//    public Integer applyCarbsConstraints(Integer carbs) {
//        this.pumpStatus.constraintCarbs = carbs;
//        return carbs;
//    }
//
//    @Override
//    public Double applyMaxIOBConstraints(Double maxIob) {
//        this.pumpStatus.constraintMaxIob = maxIob;
//        return maxIob;
//    }


    @Override
    public JSONObject getJSONStatus(Profile profile, String profileName) {
        //if (!SP.getBoolean("virtualpump_uploadstatus", false)) {
        //    return null;
        //}

        long now = System.currentTimeMillis();
        if ((pumpStatus.lastConnection + 5 * 60 * 1000L) < System.currentTimeMillis()) {
            return null;
        }

        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", pumpStatus.batteryRemaining);
            status.put("status", pumpStatus.pumpStatusType != null ? pumpStatus.pumpStatusType.getStatus() : "normal");
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            try {
                extended.put("ActiveProfile", MainApp.getConfigBuilder().getProfileName());
            } catch (Exception e) {
            }

            TemporaryBasal tb = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(System.currentTimeMillis());
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.tempBasalConvertedToAbsolute(System.currentTimeMillis(), profile));
                extended.put("TempBasalStart", DateUtil.dateAndTimeString(tb.date));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }

            ExtendedBolus eb = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(System.currentTimeMillis());
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.absoluteRate());
                extended.put("ExtendedBolusStart", DateUtil.dateAndTimeString(eb.date));
                extended.put("ExtendedBolusRemaining", eb.getPlannedRemainingMinutes());
            }

            status.put("timestamp", DateUtil.toISOString(new Date()));

            pump.put("battery", battery);
            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("reservoir", pumpStatus.reservoirRemainingUnits);
            pump.put("clock", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
            LOG.error("Unhandled exception", e);
        }
        return pump;
    }


    // Profile interface

    @Nullable
    public ProfileStore getProfile() {
        return this.pumpStatus.profileStore;
    }

    public String getUnits() {
        return this.pumpStatus.units;
    }

    public String getProfileName() {
        return this.pumpStatus.activeProfileName;
    }


}
