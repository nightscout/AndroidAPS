package info.nightscout.androidaps.plugins.pump.common;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.core.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.db.Treatment;
import info.nightscout.androidaps.events.EventAppExit;
import info.nightscout.androidaps.events.EventCustomActionsChanged;
import info.nightscout.androidaps.interfaces.ActivePluginProvider;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.interfaces.PumpPluginBase;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.pump.common.data.PumpStatus;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpDriverState;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.DecimalFormatter;
import info.nightscout.androidaps.utils.FabricPrivacy;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by andy on 23.04.18.
 */

// When using this class, make sure that your first step is to create mConnection (see MedtronicPumpPlugin)

public abstract class PumpPluginAbstract extends PumpPluginBase implements PumpInterface, ConstraintsInterface {
    private final CompositeDisposable disposable = new CompositeDisposable();

    protected HasAndroidInjector injector;
    protected AAPSLogger aapsLogger;
    protected RxBusWrapper rxBus;
    protected ActivePluginProvider activePlugin;
    protected Context context;
    protected FabricPrivacy fabricPrivacy;
    protected ResourceHelper resourceHelper;
    protected CommandQueueProvider commandQueue;
    protected SP sp;
    protected DateUtil dateUtil;
    protected PumpDescription pumpDescription = new PumpDescription();
    protected ServiceConnection serviceConnection;
    protected boolean serviceRunning = false;
    protected PumpDriverState pumpState = PumpDriverState.NotInitialized;
    protected boolean displayConnectionMessages = false;
    protected PumpType pumpType;


    protected PumpPluginAbstract(
            PluginDescription pluginDescription,
            PumpType pumpType,
            HasAndroidInjector injector,
            ResourceHelper resourceHelper,
            AAPSLogger aapsLogger,
            CommandQueueProvider commandQueue,
            RxBusWrapper rxBus,
            ActivePluginProvider activePlugin,
            SP sp,
            Context context,
            FabricPrivacy fabricPrivacy,
            DateUtil dateUtil
    ) {

        super(pluginDescription, injector, aapsLogger, resourceHelper, commandQueue);
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.activePlugin = activePlugin;
        this.context = context;
        this.fabricPrivacy = fabricPrivacy;
        this.resourceHelper = resourceHelper;
        this.sp = sp;
        this.commandQueue = commandQueue;

        pumpDescription.setPumpDescription(pumpType);
        this.pumpType = pumpType;
        this.dateUtil = dateUtil;
    }


    public abstract void initPumpStatusData();


    @Override
    protected void onStart() {
        super.onStart();

        initPumpStatusData();

        Intent intent = new Intent(context, getServiceClass());
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        serviceRunning = true;

        disposable.add(rxBus
                .toObservable(EventAppExit.class)
                .observeOn(Schedulers.io())
                .subscribe(event -> context.unbindService(serviceConnection), fabricPrivacy::logException)
        );
        onStartCustomActions();
    }


    @Override
    protected void onStop() {
        aapsLogger.debug(LTag.PUMP, this.deviceID() + " onStop()");

        context.unbindService(serviceConnection);

        serviceRunning = false;

        disposable.clear();
        super.onStop();
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

    public abstract PumpStatus getPumpStatusData();


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
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "isConnected [PumpPluginAbstract].");
        return PumpDriverState.isConnected(pumpState);
    }


    public boolean isConnecting() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "isConnecting [PumpPluginAbstract].");
        return pumpState == PumpDriverState.Connecting;
    }


    public void connect(String reason) {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "connect (reason={}) [PumpPluginAbstract] - default (empty) implementation." + reason);
    }


    public void disconnect(String reason) {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "disconnect (reason={}) [PumpPluginAbstract] - default (empty) implementation." + reason);
    }


    public void stopConnecting() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "stopConnecting [PumpPluginAbstract] - default (empty) implementation.");
    }


    @Override
    public boolean isHandshakeInProgress() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "isHandshakeInProgress [PumpPluginAbstract] - default (empty) implementation.");
        return false;
    }


    @Override
    public void finishHandshaking() {
        if (displayConnectionMessages)
            aapsLogger.debug(LTag.PUMP, "finishHandshaking [PumpPluginAbstract] - default (empty) implementation.");
    }

    // Upload to pump new basal profile
    @NonNull public PumpEnactResult setNewBasalProfile(Profile profile) {
        aapsLogger.debug(LTag.PUMP, "setNewBasalProfile [PumpPluginAbstract] - Not implemented.");
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver);
    }


    public boolean isThisProfileSet(Profile profile) {
        aapsLogger.debug(LTag.PUMP, "isThisProfileSet [PumpPluginAbstract] - Not implemented.");
        return true;
    }


    public long lastDataTime() {
        aapsLogger.debug(LTag.PUMP, "lastDataTime [PumpPluginAbstract].");
        return getPumpStatusData().lastConnection;
    }


    public double getBaseBasalRate() {
        aapsLogger.debug(LTag.PUMP, "getBaseBasalRate [PumpPluginAbstract] - Not implemented.");
        return 0.0d;
    } // base basal rate, not temp basal


    public void stopBolusDelivering() {
        aapsLogger.debug(LTag.PUMP, "stopBolusDelivering [PumpPluginAbstract] - Not implemented.");
    }


    @NonNull @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile,
                                                boolean enforceNew) {
        aapsLogger.debug(LTag.PUMP, "setTempBasalAbsolute [PumpPluginAbstract] - Not implemented.");
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver);
    }


    @NonNull @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile,
                                               boolean enforceNew) {
        aapsLogger.debug(LTag.PUMP, "setTempBasalPercent [PumpPluginAbstract] - Not implemented.");
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver);
    }


    @NonNull public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        aapsLogger.debug(LTag.PUMP, "setExtendedBolus [PumpPluginAbstract] - Not implemented.");
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver);
    }


    // some pumps might set a very short temp close to 100% as cancelling a temp can be noisy
    // when the cancel request is requested by the user (forced), the pump should always do a real cancel

    @NonNull public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        aapsLogger.debug(LTag.PUMP, "cancelTempBasal [PumpPluginAbstract] - Not implemented.");
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver);
    }


    @NonNull public PumpEnactResult cancelExtendedBolus() {
        aapsLogger.debug(LTag.PUMP, "cancelExtendedBolus [PumpPluginAbstract] - Not implemented.");
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver);
    }


    // Status to be passed to NS

    // public JSONObject getJSONStatus(Profile profile, String profileName) {
    // return pumpDriver.getJSONStatus(profile, profileName);
    // }

    public String deviceID() {
        aapsLogger.debug(LTag.PUMP, "deviceID [PumpPluginAbstract] - Not implemented.");
        return "FakeDevice";
    }


    // Pump capabilities

    @NonNull public PumpDescription getPumpDescription() {
        return pumpDescription;
    }


    // Short info for SMS, Wear etc

    public boolean isFakingTempsByExtendedBoluses() {
        aapsLogger.debug(LTag.PUMP, "isFakingTempsByExtendedBoluses [PumpPluginAbstract] - Not implemented.");
        return false;
    }


    @NonNull @Override
    public PumpEnactResult loadTDDs() {
        aapsLogger.debug(LTag.PUMP, "loadTDDs [PumpPluginAbstract] - Not implemented.");
        return getOperationNotSupportedWithCustomText(R.string.pump_operation_not_supported_by_pump_driver);
    }


    @NonNull @Override
    public JSONObject getJSONStatus(Profile profile, String profileName, String version) {

        if ((getPumpStatusData().lastConnection + 60 * 60 * 1000L) < System.currentTimeMillis()) {
            return new JSONObject();
        }

        JSONObject pump = new JSONObject();
        JSONObject battery = new JSONObject();
        JSONObject status = new JSONObject();
        JSONObject extended = new JSONObject();
        try {
            battery.put("percent", getPumpStatusData().batteryRemaining);
            status.put("status", getPumpStatusData().pumpStatusType != null ? getPumpStatusData().pumpStatusType.getStatus() : "normal");
            extended.put("Version", version);
            try {
                extended.put("ActiveProfile", profileName);
            } catch (Exception ignored) {
            }

            TemporaryBasal tb = activePlugin.getActiveTreatments().getTempBasalFromHistory(System.currentTimeMillis());
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate",
                        tb.tempBasalConvertedToAbsolute(System.currentTimeMillis(), profile));
                extended.put("TempBasalStart", dateUtil.dateAndTimeString(tb.date));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }

            ExtendedBolus eb = activePlugin.getActiveTreatments().getExtendedBolusFromHistory(System.currentTimeMillis());
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.absoluteRate());
                extended.put("ExtendedBolusStart", dateUtil.dateAndTimeString(eb.date));
                extended.put("ExtendedBolusRemaining", eb.getPlannedRemainingMinutes());
            }

            status.put("timestamp", DateUtil.toISOString(new Date()));

            pump.put("battery", battery);
            pump.put("status", status);
            pump.put("extended", extended);
            pump.put("reservoir", getPumpStatusData().reservoirRemainingUnits);
            pump.put("clock", DateUtil.toISOString(new Date()));
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return pump;
    }


    // FIXME i18n, null checks: iob, TDD
    @NonNull @Override
    public String shortStatus(boolean veryShort) {
        String ret = "";
        if (getPumpStatusData().lastConnection != 0) {
            long agoMsec = System.currentTimeMillis() - getPumpStatusData().lastConnection;
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret += "LastConn: " + agoMin + " min ago\n";
        }
        if (getPumpStatusData().lastBolusTime != null && getPumpStatusData().lastBolusTime.getTime() != 0) {
            ret += "LastBolus: " + DecimalFormatter.to2Decimal(getPumpStatusData().lastBolusAmount) + "U @" + //
                    android.text.format.DateFormat.format("HH:mm", getPumpStatusData().lastBolusTime) + "\n";
        }
        TemporaryBasal activeTemp = activePlugin.getActiveTreatments().getRealTempBasalFromHistory(System.currentTimeMillis());
        if (activeTemp != null) {
            ret += "Temp: " + activeTemp.toStringFull() + "\n";
        }
        ExtendedBolus activeExtendedBolus = activePlugin.getActiveTreatments().getExtendedBolusFromHistory(
                System.currentTimeMillis());
        if (activeExtendedBolus != null) {
            ret += "Extended: " + activeExtendedBolus.toString() + "\n";
        }
        // if (!veryShort) {
        // ret += "TDD: " + DecimalFormatter.to0Decimal(pumpStatus.dailyTotalUnits) + " / "
        // + pumpStatus.maxDailyTotalUnits + " U\n";
        // }
        ret += "IOB: " + getPumpStatusData().iob + "U\n";
        ret += "Reserv: " + DecimalFormatter.to0Decimal(getPumpStatusData().reservoirRemainingUnits) + "U\n";
        ret += "Batt: " + getPumpStatusData().batteryRemaining + "\n";
        return ret;
    }


    @NonNull @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {

        try {
            if (detailedBolusInfo.insulin == 0 && detailedBolusInfo.carbs == 0) {
                // neither carbs nor bolus requested
                aapsLogger.error("deliverTreatment: Invalid input");
                return new PumpEnactResult(getInjector()).success(false).enacted(false).bolusDelivered(0d).carbsDelivered(0d)
                        .comment(getResourceHelper().gs(R.string.invalidinput));
            } else if (detailedBolusInfo.insulin > 0) {
                // bolus needed, ask pump to deliver it
                return deliverBolus(detailedBolusInfo);
            } else {
                //if (MedtronicHistoryData.doubleBolusDebug)
                //    aapsLogger.debug("DoubleBolusDebug: deliverTreatment::(carb only entry)");

                // no bolus required, carb only treatment
                activePlugin.getActiveTreatments().addToHistoryTreatment(detailedBolusInfo, true);

                EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.INSTANCE;
                bolusingEvent.setT(new Treatment());
                bolusingEvent.getT().isSMB = detailedBolusInfo.isSMB;
                bolusingEvent.setPercent(100);
                rxBus.send(bolusingEvent);

                aapsLogger.debug(LTag.PUMP, "deliverTreatment: Carb only treatment.");

                return new PumpEnactResult(getInjector()).success(true).enacted(true).bolusDelivered(0d)
                        .carbsDelivered(detailedBolusInfo.carbs).comment(getResourceHelper().gs(R.string.common_resultok));
            }
        } finally {
            triggerUIChange();
        }

    }


    protected void refreshCustomActionsList() {
        rxBus.send(new EventCustomActionsChanged());
    }


    public ManufacturerType manufacturer() {
        return pumpType.getManufacturer();
    }

    @NotNull
    public PumpType model() {
        return pumpType;
    }


    public PumpType getPumpType() {
        return pumpType;
    }


    public void setPumpType(PumpType pumpType) {
        this.pumpType = pumpType;
        this.pumpDescription.setPumpDescription(pumpType);
    }


    public boolean canHandleDST() {
        return false;
    }


    protected abstract PumpEnactResult deliverBolus(DetailedBolusInfo detailedBolusInfo);

    protected abstract void triggerUIChange();

    private PumpEnactResult getOperationNotSupportedWithCustomText(int resourceId) {
        return new PumpEnactResult(getInjector()).success(false).enacted(false).comment(getResourceHelper().gs(resourceId));
    }

}
