package info.nightscout.androidaps.plugins.PumpCommon;

import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import com.squareup.otto.Subscribe;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.plugins.ConfigBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.Overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.PumpCommon.data.PumpStatus;
import info.nightscout.androidaps.plugins.PumpCommon.defs.PumpType;
import info.nightscout.androidaps.plugins.PumpCommon.driver.PumpDriverInterface;
import info.nightscout.androidaps.plugins.Treatments.Treatment;
import info.nightscout.androidaps.plugins.Treatments.TreatmentsPlugin;
import info.nightscout.utils.DateUtil;
import info.nightscout.utils.DecimalFormatter;

/**
 * Created by andy on 23.04.18.
 */

// When using this class, make sure that your first step is to create mConnection (see MedtronicPumpPlugin)

// FIXME remove PumpDriver instances, just keep methods that do something here
public abstract class PumpPluginAbstract extends PluginBase implements PumpInterface, ConstraintsInterface {

    protected static final PumpEnactResult OPERATION_NOT_SUPPORTED = new PumpEnactResult().success(false)
        .enacted(false).comment(MainApp.gs(R.string.pump_operation_not_supported_by_pump));
    protected static final PumpEnactResult OPERATION_NOT_YET_SUPPORTED = new PumpEnactResult().success(false)
        .enacted(false).comment(MainApp.gs(R.string.pump_operation_not_yet_supported_by_pump));
    // protected PumpStatus pumpStatusData;
    private static final Logger LOG = LoggerFactory.getLogger(PumpPluginAbstract.class);
    protected PumpDescription pumpDescription = new PumpDescription();
    protected PumpDriverInterface pumpDriver;
    protected PumpStatus pumpStatus;
    protected String internalName;
    protected ServiceConnection serviceConnection = null;
    protected boolean serviceRunning = false;


    // protected PumpPluginAbstract(PumpDriverInterface pumpDriverInterface, //
    // String internalName, //
    // String fragmentClassName, //
    // int pluginName, //
    // int pluginShortName, //
    // PumpType pumpType) {
    // this(pumpDriverInterface, //
    // internalName, //
    // new PluginDescription() //
    // .mainType(PluginType.PUMP) //
    // .fragmentClass(fragmentClassName) //
    // .pluginName(pluginName) //
    // .shortName(pluginShortName), //
    // pumpType //
    // );
    // }

    protected PumpPluginAbstract(PumpDriverInterface pumpDriverInterface, //
            String internalName, //
            PluginDescription pluginDescription, PumpType pumpType //
    ) {
        super(pluginDescription);

        LOG.error("After super called.");

        this.pumpDriver = pumpDriverInterface;
        this.internalName = internalName;

        LOG.error("Before Init Pump Statis Data called.");

        pumpDescription.setPumpDescription(pumpType);

        initPumpStatusData();

        LOG.error("Before set description");

        LOG.error("Before pumpDriver");

        this.pumpDriver.initDriver(this.pumpStatus, this.pumpDescription);
    }


    protected String getInternalName() {
        return this.internalName;
    }


    public abstract void initPumpStatusData();


    @Override
    protected void onStart() {
        Context context = MainApp.instance().getApplicationContext();
        Intent intent = new Intent(context, getServiceClass());
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        serviceRunning = true;

        MainApp.bus().register(this);
        onStartCustomActions();
        super.onStart();
    }


    @Override
    protected void onStop() {
        Context context = MainApp.instance().getApplicationContext();
        context.unbindService(serviceConnection);

        serviceRunning = false;

        MainApp.bus().unregister(this);
    }


    /**
     * If we need to run any custom actions in onStart (triggering events, etc)
     */
    public abstract void onStartCustomActions();


    /**
     * Service class (same one you did serviceConnection for)
     *
     * @return
     */
    public abstract Class getServiceClass();


    @SuppressWarnings("UnusedParameters")
    @Subscribe
    public void onStatusEvent(final EventAppExit e) {
        MainApp.instance().getApplicationContext().unbindService(serviceConnection);
    }


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


    public long lastDataTime() {
        return pumpDriver.lastDataTime();
    }


    public double getBaseBasalRate() {
        return pumpDriver.getBaseBasalRate();
    } // base basal rate, not temp basal


    // public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
    // return pumpDriver.deliverTreatment(detailedBolusInfo);
    // }

    public void stopBolusDelivering() {
        pumpDriver.stopBolusDelivering();
    }


    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile,
            boolean enforceNew) {
        return pumpDriver.setTempBasalAbsolute(absoluteRate, durationInMinutes, profile, enforceNew);
    }


    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile,
            boolean enforceNew) {
        return pumpDriver.setTempBasalPercent(percent, durationInMinutes, profile, enforceNew);
    }


    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        return pumpDriver.setExtendedBolus(insulin, durationInMinutes);
    }


    // some pumps might set a very short temp close to 100% as cancelling a temp can be noisy
    // when the cancel request is requested by the user (forced), the pump should always do a real cancel

    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        return pumpDriver.cancelTempBasal(enforceNew);
    }


    public PumpEnactResult cancelExtendedBolus() {
        LOG.warn("deviceID [PumpPluginAbstract] - Not implemented.");
        return OPERATION_NOT_YET_SUPPORTED;
    }


    // Status to be passed to NS

    // public JSONObject getJSONStatus(Profile profile, String profileName) {
    // return pumpDriver.getJSONStatus(profile, profileName);
    // }

    public String deviceID() {
        LOG.warn("deviceID [PumpPluginAbstract] - Not implemented.");
        return "FakeDevice";
    }


    // Pump capabilities

    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }


    // Short info for SMS, Wear etc

    public boolean isFakingTempsByExtendedBoluses() {
        LOG.warn("isFakingTempsByExtendedBoluses [PumpPluginAbstract] - Not implemented.");
        return false;
    }


    @Override
    public PumpEnactResult loadTDDs() {
        return this.pumpDriver.loadTDDs();
    }


    // Constraints interface

    // @Override
    // public boolean isLoopEnabled() {
    // return true;
    // }
    //
    // @Override
    // public boolean isClosedModeEnabled() {
    // return true;
    // }
    //
    // @Override
    // public boolean isAutosensModeEnabled() {
    // return true;
    // }
    //
    // @Override
    // public boolean isAMAModeEnabled() {
    // return true;
    // }
    //
    // @Override
    // public boolean isSMBModeEnabled() {
    // return true;
    // }
    //
    // @Override
    // public Double applyBasalConstraints(Double absoluteRate) {
    // this.pumpStatus.constraintBasalRateAbsolute = absoluteRate;
    // return absoluteRate;
    // }
    //
    // @Override
    // public Integer applyBasalConstraints(Integer percentRate) {
    // this.pumpStatus.constraintBasalRatePercent = percentRate;
    // return percentRate;
    // }
    //
    // @Override
    // public Double applyBolusConstraints(Double insulin) {
    // this.pumpStatus.constraintBolus = insulin;
    // return insulin;
    // }
    //
    // @Override
    // public Integer applyCarbsConstraints(Integer carbs) {
    // this.pumpStatus.constraintCarbs = carbs;
    // return carbs;
    // }
    //
    // @Override
    // public Double applyMaxIOBConstraints(Double maxIob) {
    // this.pumpStatus.constraintMaxIob = maxIob;
    // return maxIob;
    // }

    @Override
    public JSONObject getJSONStatus(Profile profile, String profileName) {

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
                extended.put("ActiveProfile", profileName);
            } catch (Exception e) {
            }

            TemporaryBasal tb = TreatmentsPlugin.getPlugin().getTempBasalFromHistory(System.currentTimeMillis());
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate",
                    tb.tempBasalConvertedToAbsolute(System.currentTimeMillis(), profile));
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


    // FIXME i18n, null checks: iob, TDD
    @Override
    public String shortStatus(boolean veryShort) {
        String ret = "";
        if (pumpStatus.lastConnection != 0) {
            Long agoMsec = System.currentTimeMillis() - pumpStatus.lastConnection;
            int agoMin = (int)(agoMsec / 60d / 1000d);
            ret += "LastConn: " + agoMin + " min ago\n";
        }
        if (pumpStatus.lastBolusTime.getTime() != 0) {
            ret += "LastBolus: " + DecimalFormatter.to2Decimal(pumpStatus.lastBolusAmount) + "U @" + //
                android.text.format.DateFormat.format("HH:mm", pumpStatus.lastBolusTime) + "\n";
        }
        TemporaryBasal activeTemp = TreatmentsPlugin.getPlugin()
            .getRealTempBasalFromHistory(System.currentTimeMillis());
        if (activeTemp != null) {
            ret += "Temp: " + activeTemp.toStringFull() + "\n";
        }
        ExtendedBolus activeExtendedBolus = TreatmentsPlugin.getPlugin().getExtendedBolusFromHistory(
            System.currentTimeMillis());
        if (activeExtendedBolus != null) {
            ret += "Extended: " + activeExtendedBolus.toString() + "\n";
        }
        if (!veryShort) {
            ret += "TDD: " + DecimalFormatter.to0Decimal(pumpStatus.dailyTotalUnits) + " / "
                + pumpStatus.maxDailyTotalUnits + " U\n";
        }
        ret += "IOB: " + pumpStatus.iob + "U\n";
        ret += "Reserv: " + DecimalFormatter.to0Decimal(pumpStatus.reservoirRemainingUnits) + "U\n";
        ret += "Batt: " + pumpStatus.batteryRemaining + "\n";
        return ret;
    }


    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {

        try {
            if (detailedBolusInfo.insulin == 0 && detailedBolusInfo.carbs == 0) {
                // neither carbs nor bolus requested
                LOG.error("deliverTreatment: Invalid input");
                return new PumpEnactResult().success(false).enacted(false).bolusDelivered(0d).carbsDelivered(0d)
                    .comment(MainApp.gs(R.string.danar_invalidinput));
            } else if (detailedBolusInfo.insulin > 0) {
                // bolus needed, ask pump to deliver it
                return deliverBolus(detailedBolusInfo);
            } else {
                // no bolus required, carb only treatment
                TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, true);

                EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.getInstance();
                bolusingEvent.t = new Treatment();
                bolusingEvent.t.isSMB = detailedBolusInfo.isSMB;
                bolusingEvent.percent = 100;
                MainApp.bus().post(bolusingEvent);

                LOG.debug("deliverTreatment: Carb only treatment.");

                return new PumpEnactResult().success(true).enacted(true).bolusDelivered(0d)
                    .carbsDelivered(detailedBolusInfo.carbs).comment(MainApp.gs(R.string.virtualpump_resultok));
            }
        } finally {
            triggerUIChange();
        }

    }


    protected abstract PumpEnactResult deliverBolus(DetailedBolusInfo detailedBolusInfo);


    protected abstract void triggerUIChange();


    public PumpEnactResult getOperationNotSupportedWithCustomText(int resourceId) {
        return new PumpEnactResult().success(false).enacted(false).comment(MainApp.gs(resourceId));
    }

    // Profile interface

    // @Nullable
    // public ProfileStore getProfile() {
    // return this.pumpStatus.profileStore;
    // }
    //
    // public String getUnits() {
    // return this.pumpStatus.units;
    // }
    //
    // public String getProfileName() {
    // return this.pumpStatus.activeProfileName;
    // }

}
