package info.nightscout.androidaps.plugins.pump.common;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import androidx.fragment.app.FragmentActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

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
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.medtronic.data.MedtronicHistoryData;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.FabricPrivacy;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by andy on 23.04.18.
 */

// When using this class, make sure that your first step is to create mConnection (see MedtronicPumpPlugin)

public abstract class PumpPluginAbstract extends PluginBase implements PumpInterface, ConstraintsInterface {
    private CompositeDisposable disposable = new CompositeDisposable();

    private static final Logger LOG = LoggerFactory.getLogger(L.PUMP);

    protected static final PumpEnactResult OPERATION_NOT_SUPPORTED = new PumpEnactResult().success(false)
            .enacted(false).comment(MainApp.gs(R.string.pump_operation_not_supported_by_pump_driver));
    protected static final PumpEnactResult OPERATION_NOT_YET_SUPPORTED = new PumpEnactResult().success(false)
            .enacted(false).comment(MainApp.gs(R.string.pump_operation_not_yet_supported_by_pump));

    protected PumpDescription pumpDescription = new PumpDescription();
    protected PumpStatus pumpStatus;
    protected ServiceConnection serviceConnection = null;
    protected boolean serviceRunning = false;
    // protected boolean isInitialized = false;
    protected PumpDriverState pumpState = PumpDriverState.NotInitialized;
    protected boolean displayConnectionMessages = false;


    protected PumpPluginAbstract(PluginDescription pluginDescription, PumpType pumpType) {

        super(pluginDescription);

        pumpDescription.setPumpDescription(pumpType);

        initPumpStatusData();

    }


    public abstract void initPumpStatusData();


    @Override
    protected void onStart() {
        super.onStart();
        Context context = MainApp.instance().getApplicationContext();
        Intent intent = new Intent(context, getServiceClass());
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        serviceRunning = true;

        disposable.add(RxBus.INSTANCE
                .toObservable(EventAppExit.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> {
                    MainApp.instance().getApplicationContext().unbindService(serviceConnection);
                }, FabricPrivacy::logException)
        );
        onStartCustomActions();
    }


    @Override
    protected void onStop() {
        Context context = MainApp.instance().getApplicationContext();
        context.unbindService(serviceConnection);

        serviceRunning = false;

        disposable.clear();
        super.onStop();
    }


    /**
     * If we need to run any custom actions in onStart (triggering events, etc)
     */
    public abstract void onStartCustomActions();

    @Override
    public void switchAllowed(boolean newState, FragmentActivity activity, PluginType type) {
        confirmPumpPluginActivation(newState, activity, type);
    }

    /**
     * Service class (same one you did serviceConnection for)
     *
     * @return
     */
    public abstract Class getServiceClass();

    public PumpStatus getPumpStatusData() {
        return pumpStatus;
    }


    public boolean isInitialized() {
        return PumpDriverState.isInitialized(pumpState);
    }


    public boolean isSuspended() {
        return pumpState == PumpDriverState.Suspended;
    }


    public boolean isBusy() {
        return pumpState == PumpDriverState.Busy;
    }


    public boolean isConnected() {
        if (displayConnectionMessages && isLoggingEnabled())
            LOG.warn("isConnected [PumpPluginAbstract].");
        return PumpDriverState.isConnected(pumpState);
    }


    public boolean isConnecting() {
        if (displayConnectionMessages && isLoggingEnabled())
            LOG.warn("isConnecting [PumpPluginAbstract].");
        return pumpState == PumpDriverState.Connecting;
    }


    public void connect(String reason) {
        if (displayConnectionMessages && isLoggingEnabled())
            LOG.warn("connect (reason={}) [PumpPluginAbstract] - default (empty) implementation.", reason);
    }


    public void disconnect(String reason) {
        if (displayConnectionMessages && isLoggingEnabled())
            LOG.warn("disconnect (reason={}) [PumpPluginAbstract] - default (empty) implementation.", reason);
    }


    public void stopConnecting() {
        if (displayConnectionMessages && isLoggingEnabled())
            LOG.warn("stopConnecting [PumpPluginAbstract] - default (empty) implementation.");
    }


    @Override
    public boolean isHandshakeInProgress() {
        if (displayConnectionMessages && isLoggingEnabled())
            LOG.warn("isHandshakeInProgress [PumpPluginAbstract] - default (empty) implementation.");
        return false;
    }


    @Override
    public void finishHandshaking() {
        if (displayConnectionMessages && isLoggingEnabled())
            LOG.warn("finishHandshaking [PumpPluginAbstract] - default (empty) implementation.");
    }


    public void getPumpStatus() {
        if (isLoggingEnabled())
            LOG.warn("getPumpStatus [PumpPluginAbstract] - Not implemented.");
    }


    // Upload to pump new basal profile
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        if (isLoggingEnabled())
            LOG.warn("setNewBasalProfile [PumpPluginAbstract] - Not implemented.");
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver);
    }


    public boolean isThisProfileSet(Profile profile) {
        if (isLoggingEnabled())
            LOG.warn("isThisProfileSet [PumpPluginAbstract] - Not implemented.");
        return true;
    }


    public long lastDataTime() {
        if (isLoggingEnabled())
            LOG.warn("lastDataTime [PumpPluginAbstract].");
        return pumpStatus.lastConnection;
    }


    public double getBaseBasalRate() {
        if (isLoggingEnabled())
            LOG.warn("getBaseBasalRate [PumpPluginAbstract] - Not implemented.");
        return 0.0d;
    } // base basal rate, not temp basal


    public void stopBolusDelivering() {
        if (isLoggingEnabled())
            LOG.warn("stopBolusDelivering [PumpPluginAbstract] - Not implemented.");
    }


    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile,
                                                boolean enforceNew) {
        if (isLoggingEnabled())
            LOG.warn("setTempBasalAbsolute [PumpPluginAbstract] - Not implemented.");
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver);
    }


    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile,
                                               boolean enforceNew) {
        if (isLoggingEnabled())
            LOG.warn("setTempBasalPercent [PumpPluginAbstract] - Not implemented.");
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver);
    }


    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        if (isLoggingEnabled())
            LOG.warn("setExtendedBolus [PumpPluginAbstract] - Not implemented.");
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver);
    }


    // some pumps might set a very short temp close to 100% as cancelling a temp can be noisy
    // when the cancel request is requested by the user (forced), the pump should always do a real cancel

    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        if (isLoggingEnabled())
            LOG.warn("cancelTempBasal [PumpPluginAbstract] - Not implemented.");
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver);
    }


    public PumpEnactResult cancelExtendedBolus() {
        if (isLoggingEnabled())
            LOG.warn("cancelExtendedBolus [PumpPluginAbstract] - Not implemented.");
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver);
    }


    // Status to be passed to NS

    // public JSONObject getJSONStatus(Profile profile, String profileName) {
    // return pumpDriver.getJSONStatus(profile, profileName);
    // }

    public String deviceID() {
        if (isLoggingEnabled())
            LOG.warn("deviceID [PumpPluginAbstract] - Not implemented.");
        return "FakeDevice";
    }


    // Pump capabilities

    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }


    // Short info for SMS, Wear etc

    public boolean isFakingTempsByExtendedBoluses() {
        if (isLoggingEnabled())
            LOG.warn("isFakingTempsByExtendedBoluses [PumpPluginAbstract] - Not implemented.");
        return false;
    }


    @Override
    public PumpEnactResult loadTDDs() {
        if (isLoggingEnabled())
            LOG.warn("loadTDDs [PumpPluginAbstract] - Not implemented.");
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver);
    }


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
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += "LastConn: " + agoMin + " min ago\n";
        }
        if (pumpStatus.lastBolusTime != null && pumpStatus.lastBolusTime.getTime() != 0) {
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
        // if (!veryShort) {
        // ret += "TDD: " + DecimalFormatter.to0Decimal(pumpStatus.dailyTotalUnits) + " / "
        // + pumpStatus.maxDailyTotalUnits + " U\n";
        // }
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
                if (isLoggingEnabled())
                    LOG.error("deliverTreatment: Invalid input");
                return new PumpEnactResult().success(false).enacted(false).bolusDelivered(0d).carbsDelivered(0d)
                        .comment(MainApp.gs(R.string.danar_invalidinput));
            } else if (detailedBolusInfo.insulin > 0) {
                // bolus needed, ask pump to deliver it
                return deliverBolus(detailedBolusInfo);
            } else {
                if (MedtronicHistoryData.doubleBolusDebug)
                    LOG.debug("DoubleBolusDebug: deliverTreatment::(carb only entry)");

                // no bolus required, carb only treatment
                TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, true);

                EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.INSTANCE;
                bolusingEvent.setT(new Treatment());
                bolusingEvent.getT().isSMB = detailedBolusInfo.isSMB;
                bolusingEvent.setPercent(100);
                RxBus.INSTANCE.send(bolusingEvent);

                if (isLoggingEnabled())
                    LOG.debug("deliverTreatment: Carb only treatment.");

                return new PumpEnactResult().success(true).enacted(true).bolusDelivered(0d)
                        .carbsDelivered(detailedBolusInfo.carbs).comment(MainApp.gs(R.string.virtualpump_resultok));
            }
        } finally {
            triggerUIChange();
        }

    }


    public boolean isLoggingEnabled() {
        return L.isEnabled(L.PUMP);
    }


    protected abstract PumpEnactResult deliverBolus(DetailedBolusInfo detailedBolusInfo);


    protected abstract void triggerUIChange();


    public static PumpEnactResult getOperationNotSupportedWithCustomText(int resourceId) {
        return new PumpEnactResult().success(false).enacted(false).comment(MainApp.gs(resourceId));
    }

}
