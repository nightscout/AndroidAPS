package info.nightscout.androidaps.plugins.pump.insight;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.fragment.app.FragmentActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import info.nightscout.androidaps.BuildConfig;
import info.nightscout.androidaps.MainApp;
import info.nightscout.androidaps.R;
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.db.CareportalEvent;
import info.nightscout.androidaps.db.ExtendedBolus;
import info.nightscout.androidaps.db.Source;
import info.nightscout.androidaps.db.TDD;
import info.nightscout.androidaps.db.TemporaryBasal;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.ConstraintsInterface;
import info.nightscout.androidaps.interfaces.PluginBase;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpInterface;
import info.nightscout.androidaps.logging.L;
import info.nightscout.androidaps.plugins.bus.RxBus;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin;
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction;
import info.nightscout.androidaps.plugins.general.actions.defs.CustomActionType;
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload;
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue;
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification;
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress;
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification;
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.HistoryReadingDirection;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.ReadHistoryEventsMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.StartReadingHistoryMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.StopReadingHistoryMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.BolusDeliveredEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.BolusProgrammedEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.CannulaFilledEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.DateTimeChangedEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.DefaultDateTimeSetEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.EndOfTBREvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.HistoryEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.OccurrenceOfAlertEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.OperatingModeChangedEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.PowerUpEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.SniffingDoneEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.StartOfTBREvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.TotalDailyDoseEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.TubeFilledEvent;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.ActiveBRProfileBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.BRProfile1Block;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.BRProfileBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.FactoryMinBasalAmountBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.FactoryMinBolusAmountBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.MaxBasalAmountBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.MaxBolusAmountBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.TBROverNotificationBlock;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.CancelBolusMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.CancelTBRMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.ChangeTBRMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.ConfirmAlertMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.DeliverBolusMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.SetDateTimeMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.SetOperatingModeMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.SetTBRMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetActiveAlertMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetActiveBasalRateMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetActiveBolusesMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetActiveTBRMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetBatteryStatusMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetCartridgeStatusMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetDateTimeMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetOperatingModeMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetPumpStatusRegisterMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.GetTotalDailyDoseMessage;
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.ResetPumpStatusRegisterMessage;
import info.nightscout.androidaps.plugins.pump.insight.connection_service.InsightConnectionService;
import info.nightscout.androidaps.plugins.pump.insight.database.InsightBolusID;
import info.nightscout.androidaps.plugins.pump.insight.database.InsightHistoryOffset;
import info.nightscout.androidaps.plugins.pump.insight.database.InsightPumpID;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.ActiveBasalRate;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.ActiveBolus;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.ActiveTBR;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.AlertType;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.BasalProfile;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.BasalProfileBlock;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.BatteryStatus;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.BolusType;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.CartridgeStatus;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.InsightState;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.OperatingMode;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.PumpTime;
import info.nightscout.androidaps.plugins.pump.insight.descriptors.TotalDailyDose;
import info.nightscout.androidaps.plugins.pump.insight.events.EventLocalInsightUpdateGUI;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.InsightException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors.AppLayerErrorException;
import info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors.NoActiveTBRToCanceLException;
import info.nightscout.androidaps.plugins.pump.insight.utils.ExceptionTranslator;
import info.nightscout.androidaps.plugins.pump.insight.utils.ParameterBlockUtil;
import info.nightscout.androidaps.plugins.treatments.Treatment;
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin;
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.SP;

public class LocalInsightPlugin extends PluginBase implements PumpInterface, ConstraintsInterface, InsightConnectionService.StateCallback {

    private static LocalInsightPlugin instance = null;

    private Logger log = LoggerFactory.getLogger(L.PUMP);

    private PumpDescription pumpDescription;
    private InsightAlertService alertService;
    private InsightConnectionService connectionService;
    private long timeOffset;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (binder instanceof InsightConnectionService.LocalBinder) {
                connectionService = ((InsightConnectionService.LocalBinder) binder).getService();
                connectionService.registerStateCallback(LocalInsightPlugin.this);
            } else if (binder instanceof InsightAlertService.LocalBinder) {
                alertService = ((InsightAlertService.LocalBinder) binder).getService();
            }
            if (connectionService != null && alertService != null) {
                RxBus.INSTANCE.send(new EventInitializationChanged());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            connectionService = null;
        }
    };

    private final Object $bolusLock = new Object[0];
    private int bolusID;
    private boolean bolusCancelled;
    private BasalProfile activeBasalProfile;
    private List<BasalProfileBlock> profileBlocks;
    private boolean limitsFetched;
    private double maximumBolusAmount;
    private double maximumBasalAmount;
    private double minimumBolusAmount;
    private double minimumBasalAmount;
    private long lastUpdated = -1;
    private OperatingMode operatingMode;
    private BatteryStatus batteryStatus;
    private CartridgeStatus cartridgeStatus;
    private TotalDailyDose totalDailyDose;
    private ActiveBasalRate activeBasalRate;
    private ActiveTBR activeTBR;
    private List<ActiveBolus> activeBoluses;
    private boolean statusLoaded;
    private TBROverNotificationBlock tbrOverNotificationBlock;

    public static LocalInsightPlugin getPlugin() {
        if (instance == null) instance = new LocalInsightPlugin();
        return instance;
    }

    public LocalInsightPlugin() {
        super(new PluginDescription()
                .pluginName(R.string.insight_local)
                .shortName(R.string.insightpump_shortname)
                .mainType(PluginType.PUMP)
                .description(R.string.description_pump_insight_local)
                .fragmentClass(LocalInsightFragment.class.getName())
                .preferencesId(MainApp.instance().getPackageName().equals("info.nightscout.androidaps")
                        ? R.xml.pref_insight_local_full : R.xml.pref_insight_local_pumpcontrol));

        pumpDescription = new PumpDescription();
        pumpDescription.setPumpDescription(PumpType.AccuChekInsightBluetooth);
    }

    public TBROverNotificationBlock getTBROverNotificationBlock() {
        return tbrOverNotificationBlock;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public InsightConnectionService getConnectionService() {
        return connectionService;
    }

    public OperatingMode getOperatingMode() {
        return operatingMode;
    }

    public BatteryStatus getBatteryStatus() {
        return batteryStatus;
    }

    public CartridgeStatus getCartridgeStatus() {
        return cartridgeStatus;
    }

    public TotalDailyDose getTotalDailyDose() {
        return totalDailyDose;
    }

    public ActiveBasalRate getActiveBasalRate() {
        return activeBasalRate;
    }

    public ActiveTBR getActiveTBR() {
        return activeTBR;
    }

    public List<ActiveBolus> getActiveBoluses() {
        return activeBoluses;
    }

    @Override
    protected void onStart() {
        super.onStart();
        MainApp.instance().bindService(new Intent(MainApp.instance(), InsightConnectionService.class), serviceConnection, Context.BIND_AUTO_CREATE);
        MainApp.instance().bindService(new Intent(MainApp.instance(), InsightAlertService.class), serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        MainApp.instance().unbindService(serviceConnection);
    }

    @Override
    public void switchAllowed(boolean newState, FragmentActivity activity, PluginType type) {
        confirmPumpPluginActivation(newState, activity, type);
    }

    @Override
    public boolean isInitialized() {
        return connectionService != null && alertService != null && connectionService.isPaired();
    }

    @Override
    public boolean isSuspended() {
        return operatingMode != null && operatingMode != OperatingMode.STARTED;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public boolean isConnected() {
        return connectionService != null
                && alertService != null
                && connectionService.hasRequestedConnection(this)
                && connectionService.getState() == InsightState.CONNECTED;
    }

    @Override
    public boolean isConnecting() {
        if (connectionService == null || alertService == null || !connectionService.hasRequestedConnection(this))
            return false;
        InsightState state = connectionService.getState();
        return state == InsightState.CONNECTING
                || state == InsightState.APP_CONNECT_MESSAGE
                || state == InsightState.RECOVERING;
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
        if (connectionService != null && alertService != null)
            connectionService.requestConnection(this);
    }

    @Override
    public void disconnect(String reason) {
        if (connectionService != null && alertService != null)
            connectionService.withdrawConnectionRequest(this);
    }

    @Override
    public void stopConnecting() {
        if (connectionService != null && alertService != null)
            connectionService.withdrawConnectionRequest(this);
    }

    @Override
    public void getPumpStatus() {
        try {
            tbrOverNotificationBlock = ParameterBlockUtil.readParameterBlock(connectionService, Service.CONFIGURATION, TBROverNotificationBlock.class);
            readHistory();
            fetchBasalProfile();
            fetchLimitations();
            updatePumpTimeIfNeeded();
            fetchStatus();
        } catch (AppLayerErrorException e) {
            log.info("Exception while fetching status: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
        } catch (InsightException e) {
            log.info("Exception while fetching status: " + e.getClass().getCanonicalName());
        } catch (Exception e) {
            log.error("Exception while fetching status", e);
        }
    }

    private void updatePumpTimeIfNeeded() throws Exception {
        PumpTime pumpTime = connectionService.requestMessage(new GetDateTimeMessage()).await().getPumpTime();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.YEAR, pumpTime.getYear());
        calendar.set(Calendar.MONTH, pumpTime.getMonth() - 1);
        calendar.set(Calendar.DAY_OF_MONTH, pumpTime.getDay());
        calendar.set(Calendar.HOUR_OF_DAY, pumpTime.getHour());
        calendar.set(Calendar.MINUTE, pumpTime.getMinute());
        calendar.set(Calendar.SECOND, pumpTime.getSecond());
        if (calendar.get(Calendar.HOUR_OF_DAY) != pumpTime.getHour() || Math.abs(calendar.getTimeInMillis() - System.currentTimeMillis()) > 10000) {
            calendar.setTime(new Date());
            pumpTime.setYear(calendar.get(Calendar.YEAR));
            pumpTime.setMonth(calendar.get(Calendar.MONTH) + 1);
            pumpTime.setDay(calendar.get(Calendar.DAY_OF_MONTH));
            pumpTime.setHour(calendar.get(Calendar.HOUR_OF_DAY));
            pumpTime.setMinute(calendar.get(Calendar.MINUTE));
            pumpTime.setSecond(calendar.get(Calendar.SECOND));
            SetDateTimeMessage setDateTimeMessage = new SetDateTimeMessage();
            setDateTimeMessage.setPumpTime(pumpTime);
            connectionService.requestMessage(setDateTimeMessage).await();
            Notification notification = new Notification(Notification.INSIGHT_DATE_TIME_UPDATED, MainApp.gs(R.string.pump_time_updated), Notification.INFO, 60);
            RxBus.INSTANCE.send(new EventNewNotification(notification));
        }
    }

    private void fetchBasalProfile() throws Exception {
        activeBasalProfile = ParameterBlockUtil.readParameterBlock(connectionService, Service.CONFIGURATION, ActiveBRProfileBlock.class).getActiveBasalProfile();
        profileBlocks = ParameterBlockUtil.readParameterBlock(connectionService, Service.CONFIGURATION, BRProfile1Block.class).getProfileBlocks();
    }

    private void fetchStatus() throws Exception {
        if (statusLoaded) {
            GetPumpStatusRegisterMessage registerMessage = connectionService.requestMessage(new GetPumpStatusRegisterMessage()).await();
            ResetPumpStatusRegisterMessage resetMessage = new ResetPumpStatusRegisterMessage();
            resetMessage.setOperatingModeChanged(registerMessage.isOperatingModeChanged());
            resetMessage.setBatteryStatusChanged(registerMessage.isBatteryStatusChanged());
            resetMessage.setCartridgeStatusChanged(registerMessage.isCartridgeStatusChanged());
            resetMessage.setTotalDailyDoseChanged(registerMessage.isTotalDailyDoseChanged());
            resetMessage.setActiveTBRChanged(registerMessage.isActiveTBRChanged());
            resetMessage.setActiveBolusesChanged(registerMessage.isActiveBolusesChanged());
            connectionService.requestMessage(resetMessage).await();
            if (registerMessage.isOperatingModeChanged())
                operatingMode = connectionService.requestMessage(new GetOperatingModeMessage()).await().getOperatingMode();
            if (registerMessage.isBatteryStatusChanged())
                batteryStatus = connectionService.requestMessage(new GetBatteryStatusMessage()).await().getBatteryStatus();
            if (registerMessage.isCartridgeStatusChanged())
                cartridgeStatus = connectionService.requestMessage(new GetCartridgeStatusMessage()).await().getCartridgeStatus();
            if (registerMessage.isTotalDailyDoseChanged())
                totalDailyDose = connectionService.requestMessage(new GetTotalDailyDoseMessage()).await().getTDD();
            if (operatingMode == OperatingMode.STARTED) {
                if (registerMessage.isActiveBasalRateChanged())
                    activeBasalRate = connectionService.requestMessage(new GetActiveBasalRateMessage()).await().getActiveBasalRate();
                if (registerMessage.isActiveTBRChanged())
                    activeTBR = connectionService.requestMessage(new GetActiveTBRMessage()).await().getActiveTBR();
                if (registerMessage.isActiveBolusesChanged())
                    activeBoluses = connectionService.requestMessage(new GetActiveBolusesMessage()).await().getActiveBoluses();
            } else {
                activeBasalRate = null;
                activeTBR = null;
                activeBoluses = null;
            }

        } else {
            ResetPumpStatusRegisterMessage resetMessage = new ResetPumpStatusRegisterMessage();
            resetMessage.setOperatingModeChanged(true);
            resetMessage.setBatteryStatusChanged(true);
            resetMessage.setCartridgeStatusChanged(true);
            resetMessage.setTotalDailyDoseChanged(true);
            resetMessage.setActiveBasalRateChanged(true);
            resetMessage.setActiveTBRChanged(true);
            resetMessage.setActiveBolusesChanged(true);
            connectionService.requestMessage(resetMessage).await();
            operatingMode = connectionService.requestMessage(new GetOperatingModeMessage()).await().getOperatingMode();
            batteryStatus = connectionService.requestMessage(new GetBatteryStatusMessage()).await().getBatteryStatus();
            cartridgeStatus = connectionService.requestMessage(new GetCartridgeStatusMessage()).await().getCartridgeStatus();
            totalDailyDose = connectionService.requestMessage(new GetTotalDailyDoseMessage()).await().getTDD();
            if (operatingMode == OperatingMode.STARTED) {
                activeBasalRate = connectionService.requestMessage(new GetActiveBasalRateMessage()).await().getActiveBasalRate();
                activeTBR = connectionService.requestMessage(new GetActiveTBRMessage()).await().getActiveTBR();
                activeBoluses = connectionService.requestMessage(new GetActiveBolusesMessage()).await().getActiveBoluses();
            } else {
                activeBasalRate = null;
                activeTBR = null;
                activeBoluses = null;
            }
            statusLoaded = true;
        }
        lastUpdated = System.currentTimeMillis();
        new Handler(Looper.getMainLooper()).post(() -> {
            RxBus.INSTANCE.send(new EventLocalInsightUpdateGUI());
            RxBus.INSTANCE.send(new EventRefreshOverview("LocalInsightPlugin::fetchStatus"));
        });
    }

    private void fetchLimitations() throws Exception {
        maximumBolusAmount = ParameterBlockUtil.readParameterBlock(connectionService, Service.CONFIGURATION, MaxBolusAmountBlock.class).getAmountLimitation();
        maximumBasalAmount = ParameterBlockUtil.readParameterBlock(connectionService, Service.CONFIGURATION, MaxBasalAmountBlock.class).getAmountLimitation();
        minimumBolusAmount = ParameterBlockUtil.readParameterBlock(connectionService, Service.CONFIGURATION, FactoryMinBolusAmountBlock.class).getAmountLimitation();
        minimumBasalAmount = ParameterBlockUtil.readParameterBlock(connectionService, Service.CONFIGURATION, FactoryMinBasalAmountBlock.class).getAmountLimitation();
        this.pumpDescription.basalMaximumRate = maximumBasalAmount;
        this.pumpDescription.basalMinimumRate = minimumBasalAmount;
        limitsFetched = true;
    }

    @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        PumpEnactResult result = new PumpEnactResult();
        RxBus.INSTANCE.send(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
        List<BasalProfileBlock> profileBlocks = new ArrayList<>();
        for (int i = 0; i < profile.getBasalValues().length; i++) {
            Profile.ProfileValue basalValue = profile.getBasalValues()[i];
            Profile.ProfileValue nextValue = null;
            if (profile.getBasalValues().length > i + 1)
                nextValue = profile.getBasalValues()[i + 1];
            BasalProfileBlock profileBlock = new BasalProfileBlock();
            profileBlock.setBasalAmount(basalValue.value > 5 ? Math.round(basalValue.value / 0.1) * 0.1 : Math.round(basalValue.value / 0.01) * 0.01);
            profileBlock.setDuration((((nextValue != null ? nextValue.timeAsSeconds : 24 * 60 * 60) - basalValue.timeAsSeconds) / 60));
            profileBlocks.add(profileBlock);
        }
        try {
            ActiveBRProfileBlock activeBRProfileBlock = new ActiveBRProfileBlock();
            activeBRProfileBlock.setActiveBasalProfile(BasalProfile.PROFILE_1);
            ParameterBlockUtil.writeConfigurationBlock(connectionService, activeBRProfileBlock);
            activeBasalProfile = BasalProfile.PROFILE_1;
            BRProfileBlock profileBlock = new BRProfile1Block();
            profileBlock.setProfileBlocks(profileBlocks);
            ParameterBlockUtil.writeConfigurationBlock(connectionService, profileBlock);
            RxBus.INSTANCE.send(new EventDismissNotification(Notification.FAILED_UDPATE_PROFILE));
            Notification notification = new Notification(Notification.PROFILE_SET_OK, MainApp.gs(R.string.profile_set_ok), Notification.INFO, 60);
            RxBus.INSTANCE.send(new EventNewNotification(notification));
            result.success = true;
            result.enacted = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            this.profileBlocks = profileBlocks;
            try {
                fetchStatus();
            } catch (Exception ignored) {
            }
        } catch (AppLayerErrorException e) {
            log.info("Exception while setting profile: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            Notification notification = new Notification(Notification.FAILED_UDPATE_PROFILE, MainApp.gs(R.string.failedupdatebasalprofile), Notification.URGENT);
            RxBus.INSTANCE.send(new EventNewNotification(notification));
            result.comment = ExceptionTranslator.getString(e);
        } catch (InsightException e) {
            log.info("Exception while setting profile: " + e.getClass().getCanonicalName());
            Notification notification = new Notification(Notification.FAILED_UDPATE_PROFILE, MainApp.gs(R.string.failedupdatebasalprofile), Notification.URGENT);
            RxBus.INSTANCE.send(new EventNewNotification(notification));
            result.comment = ExceptionTranslator.getString(e);
        } catch (Exception e) {
            log.error("Exception while setting profile", e);
            Notification notification = new Notification(Notification.FAILED_UDPATE_PROFILE, MainApp.gs(R.string.failedupdatebasalprofile), Notification.URGENT);
            RxBus.INSTANCE.send(new EventNewNotification(notification));
            result.comment = ExceptionTranslator.getString(e);
        }
        return result;
    }

    @Override
    public boolean isThisProfileSet(Profile profile) {
        if (!isInitialized() || profileBlocks == null) return true;
        if (profile.getBasalValues().length != profileBlocks.size()) return false;
        if (activeBasalProfile != BasalProfile.PROFILE_1) return false;
        for (int i = 0; i < profileBlocks.size(); i++) {
            BasalProfileBlock profileBlock = profileBlocks.get(i);
            Profile.ProfileValue basalValue = profile.getBasalValues()[i];
            Profile.ProfileValue nextValue = null;
            if (profile.getBasalValues().length > i + 1)
                nextValue = profile.getBasalValues()[i + 1];
            if (profileBlock.getDuration() * 60 != (nextValue != null ? nextValue.timeAsSeconds : 24 * 60 * 60) - basalValue.timeAsSeconds)
                return false;
            if (Math.abs(profileBlock.getBasalAmount() - basalValue.value) > (basalValue.value > 5 ? 0.051 : 0.0051))
                return false;
        }
        return true;
    }

    @Override
    public long lastDataTime() {
        if (connectionService == null || alertService == null) return System.currentTimeMillis();
        return connectionService.getLastDataTime();
    }

    @Override
    public double getBaseBasalRate() {
        if (connectionService == null || alertService == null) return 0;
        if (activeBasalRate != null) return activeBasalRate.getActiveBasalRate();
        else return 0;
    }

    @Override
    public double getReservoirLevel() {
        if (cartridgeStatus == null) return 0;
        return cartridgeStatus.getRemainingAmount();
    }

    @Override
    public int getBatteryLevel() {
        if (batteryStatus == null) return 0;
        return batteryStatus.getBatteryAmount();
    }

    @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        PumpEnactResult result = new PumpEnactResult();
        double insulin = Math.round(detailedBolusInfo.insulin / 0.01) * 0.01;
        if (insulin > 0) {
            try {
                synchronized ($bolusLock) {
                    DeliverBolusMessage bolusMessage = new DeliverBolusMessage();
                    bolusMessage.setBolusType(BolusType.STANDARD);
                    bolusMessage.setDuration(0);
                    bolusMessage.setExtendedAmount(0);
                    bolusMessage.setImmediateAmount(insulin);
                    bolusID = connectionService.requestMessage(bolusMessage).await().getBolusId();
                    bolusCancelled = false;
                }
                result.success = true;
                result.enacted = true;
                Treatment t = new Treatment();
                t.isSMB = detailedBolusInfo.isSMB;
                final EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.INSTANCE;
                bolusingEvent.setT(t);
                bolusingEvent.setStatus(MainApp.gs(R.string.insight_delivered, 0d, insulin));
                bolusingEvent.setPercent(0);
                RxBus.INSTANCE.send(bolusingEvent);
                int trials = 0;
                InsightBolusID insightBolusID = new InsightBolusID();
                insightBolusID.bolusID = bolusID;
                insightBolusID.timestamp = System.currentTimeMillis();
                insightBolusID.pumpSerial = connectionService.getPumpSystemIdentification().getSerialNumber();
                MainApp.getDbHelper().createOrUpdate(insightBolusID);
                detailedBolusInfo.date = insightBolusID.timestamp;
                detailedBolusInfo.source = Source.PUMP;
                detailedBolusInfo.pumpId = insightBolusID.id;
                if (detailedBolusInfo.carbs > 0 && detailedBolusInfo.carbTime != 0) {
                    DetailedBolusInfo carbInfo = new DetailedBolusInfo();
                    carbInfo.carbs = detailedBolusInfo.carbs;
                    carbInfo.date = detailedBolusInfo.date + detailedBolusInfo.carbTime * 60L * 1000L;
                    carbInfo.source = Source.USER;
                    TreatmentsPlugin.getPlugin().addToHistoryTreatment(carbInfo, false);
                    detailedBolusInfo.carbTime = 0;
                    detailedBolusInfo.carbs = 0;
                }
                TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, true);
                while (true) {
                    synchronized ($bolusLock) {
                        if (bolusCancelled) break;
                    }
                    OperatingMode operatingMode = connectionService.requestMessage(new GetOperatingModeMessage()).await().getOperatingMode();
                    if (operatingMode != OperatingMode.STARTED) break;
                    List<ActiveBolus> activeBoluses = connectionService.requestMessage(new GetActiveBolusesMessage()).await().getActiveBoluses();
                    ActiveBolus activeBolus = null;
                    for (ActiveBolus bolus : activeBoluses) {
                        if (bolus.getBolusID() == bolusID) {
                            activeBolus = bolus;
                            break;
                        }
                    }
                    if (activeBolus != null) {
                        trials = -1;
                        int percentBefore = bolusingEvent.getPercent();
                        bolusingEvent.setPercent((int) (100D / activeBolus.getInitialAmount() * (activeBolus.getInitialAmount() - activeBolus.getRemainingAmount())));
                        bolusingEvent.setStatus(MainApp.gs(R.string.insight_delivered, activeBolus.getInitialAmount() - activeBolus.getRemainingAmount(), activeBolus.getInitialAmount()));
                        if (percentBefore != bolusingEvent.getPercent())
                            RxBus.INSTANCE.send(bolusingEvent);
                    } else {
                        synchronized ($bolusLock) {
                            if (bolusCancelled || trials == -1 || trials++ >= 5) {
                                if (!bolusCancelled) {
                                    bolusingEvent.setStatus(MainApp.gs(R.string.insight_delivered, insulin, insulin));
                                    bolusingEvent.setPercent(100);
                                    RxBus.INSTANCE.send(bolusingEvent);
                                }
                                break;
                            }
                        }
                    }
                    Thread.sleep(200);
                }
                readHistory();
                fetchStatus();
            } catch (AppLayerErrorException e) {
                log.info("Exception while delivering bolus: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
                result.comment = ExceptionTranslator.getString(e);
            } catch (InsightException e) {
                log.info("Exception while delivering bolus: " + e.getClass().getCanonicalName());
                result.comment = ExceptionTranslator.getString(e);
            } catch (Exception e) {
                log.error("Exception while delivering bolus", e);
                result.comment = ExceptionTranslator.getString(e);
            }
        } else if (detailedBolusInfo.carbs > 0) {
            result.success = true;
            result.enacted = true;
        }
        result.carbsDelivered = detailedBolusInfo.carbs;
        result.bolusDelivered = insulin;
        return result;
    }

    @Override
    public void stopBolusDelivering() {
        new Thread(() -> {
            try {
                synchronized ($bolusLock) {
                    alertService.ignore(AlertType.WARNING_38);
                    CancelBolusMessage cancelBolusMessage = new CancelBolusMessage();
                    cancelBolusMessage.setBolusID(bolusID);
                    connectionService.requestMessage(cancelBolusMessage).await();
                    bolusCancelled = true;
                }
                confirmAlert(AlertType.WARNING_38);
            } catch (AppLayerErrorException e) {
                log.info("Exception while canceling bolus: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            } catch (InsightException e) {
                log.info("Exception while canceling bolus: " + e.getClass().getCanonicalName());
            } catch (Exception e) {
                log.error("Exception while canceling bolus", e);
            }
        }).start();
    }

    @Override
    public PumpEnactResult setTempBasalAbsolute(Double absoluteRate, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        PumpEnactResult result = new PumpEnactResult();
        if (activeBasalRate == null) return result;
        if (activeBasalRate.getActiveBasalRate() == 0) return result;
        double percent = 100D / activeBasalRate.getActiveBasalRate() * absoluteRate;
        if (isFakingTempsByExtendedBoluses()) {
            PumpEnactResult cancelEBResult = cancelExtendedBolusOnly();
            if (cancelEBResult.success) {
                if (percent > 250) {
                    PumpEnactResult cancelTBRResult = cancelTempBasalOnly();
                    if (cancelTBRResult.success) {
                        PumpEnactResult ebResult = setExtendedBolusOnly((absoluteRate - getBaseBasalRate()) / 60D
                                * ((double) durationInMinutes), durationInMinutes);
                        if (ebResult.success) {
                            result.success = true;
                            result.enacted = true;
                            result.isPercent = false;
                            result.absolute = absoluteRate;
                            result.duration = durationInMinutes;
                            result.comment = MainApp.gs(R.string.virtualpump_resultok);
                        } else {
                            result.comment = ebResult.comment;
                        }
                    } else {
                        result.comment = cancelTBRResult.comment;
                    }
                } else {
                    return setTempBasalPercent((int) Math.round(percent), durationInMinutes, profile, enforceNew);
                }
            } else {
                result.comment = cancelEBResult.comment;
            }
        } else {
            return setTempBasalPercent((int) Math.round(percent), durationInMinutes, profile, enforceNew);
        }
        try {
            fetchStatus();
            readHistory();
        } catch (AppLayerErrorException e) {
            log.info("Exception after setting TBR: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
        } catch (InsightException e) {
            log.info("Exception after setting TBR: " + e.getClass().getCanonicalName());
        } catch (Exception e) {
            log.error("Exception after setting TBR", e);
        }
        return result;
    }

    @Override
    public PumpEnactResult setTempBasalPercent(Integer percent, Integer durationInMinutes, Profile profile, boolean enforceNew) {
        PumpEnactResult result = new PumpEnactResult();
        percent = (int) Math.round(((double) percent) / 10d) * 10;
        if (percent == 100) return cancelTempBasal(true);
        else if (percent > 250) percent = 250;
        try {
            if (activeTBR != null) {
                ChangeTBRMessage message = new ChangeTBRMessage();
                message.setDuration(durationInMinutes);
                message.setPercentage(percent);
                connectionService.requestMessage(message);
            } else {
                SetTBRMessage message = new SetTBRMessage();
                message.setDuration(durationInMinutes);
                message.setPercentage(percent);
                connectionService.requestMessage(message);
            }
            result.isPercent = true;
            result.percent = percent;
            result.duration = durationInMinutes;
            result.success = true;
            result.enacted = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
            readHistory();
            fetchStatus();
        } catch (AppLayerErrorException e) {
            log.info("Exception while setting TBR: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            result.comment = ExceptionTranslator.getString(e);
        } catch (InsightException e) {
            log.info("Exception while setting TBR: " + e.getClass().getCanonicalName());
            result.comment = ExceptionTranslator.getString(e);
        } catch (Exception e) {
            log.error("Exception while setting TBR", e);
            result.comment = ExceptionTranslator.getString(e);
        }
        return result;
    }

    @Override
    public PumpEnactResult setExtendedBolus(Double insulin, Integer durationInMinutes) {
        PumpEnactResult result = cancelExtendedBolusOnly();
        if (result.success) result = setExtendedBolusOnly(insulin, durationInMinutes);
        try {
            fetchStatus();
            readHistory();
        } catch (AppLayerErrorException e) {
            log.info("Exception after delivering extended bolus: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
        } catch (InsightException e) {
            log.info("Exception after delivering extended bolus: " + e.getClass().getCanonicalName());
        } catch (Exception e) {
            log.error("Exception after delivering extended bolus", e);
        }
        return result;
    }

    public PumpEnactResult setExtendedBolusOnly(Double insulin, Integer durationInMinutes) {
        PumpEnactResult result = new PumpEnactResult();
        try {
            DeliverBolusMessage bolusMessage = new DeliverBolusMessage();
            bolusMessage.setBolusType(BolusType.EXTENDED);
            bolusMessage.setDuration(durationInMinutes);
            bolusMessage.setExtendedAmount(insulin);
            bolusMessage.setImmediateAmount(0);
            int bolusID = connectionService.requestMessage(bolusMessage).await().getBolusId();
            InsightBolusID insightBolusID = new InsightBolusID();
            insightBolusID.bolusID = bolusID;
            insightBolusID.timestamp = System.currentTimeMillis();
            insightBolusID.pumpSerial = connectionService.getPumpSystemIdentification().getSerialNumber();
            MainApp.getDbHelper().createOrUpdate(insightBolusID);
            ExtendedBolus extendedBolus = new ExtendedBolus();
            extendedBolus.date = insightBolusID.timestamp;
            extendedBolus.source = Source.PUMP;
            extendedBolus.durationInMinutes = durationInMinutes;
            extendedBolus.insulin = insulin;
            extendedBolus.pumpId = insightBolusID.id;
            TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
            result.success = true;
            result.enacted = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
        } catch (AppLayerErrorException e) {
            log.info("Exception while delivering extended bolus: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            result.comment = ExceptionTranslator.getString(e);
        } catch (InsightException e) {
            log.info("Exception while delivering extended bolus: " + e.getClass().getCanonicalName());
            result.comment = ExceptionTranslator.getString(e);
        } catch (Exception e) {
            log.error("Exception while delivering extended bolus", e);
            result.comment = ExceptionTranslator.getString(e);
        }
        return result;
    }

    @Override
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        PumpEnactResult result = new PumpEnactResult();
        PumpEnactResult cancelEBResult = null;
        if (isFakingTempsByExtendedBoluses()) cancelEBResult = cancelExtendedBolusOnly();
        PumpEnactResult cancelTBRResult = cancelTempBasalOnly();
        result.success = (cancelEBResult != null && cancelEBResult.success) && cancelTBRResult.success;
        result.enacted = (cancelEBResult != null && cancelEBResult.enacted) || cancelTBRResult.enacted;
        result.comment = cancelEBResult != null ? cancelEBResult.comment : cancelTBRResult.comment;
        try {
            fetchStatus();
            readHistory();
        } catch (AppLayerErrorException e) {
            log.info("Exception after canceling TBR: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
        } catch (InsightException e) {
            log.info("Exception after canceling TBR: " + e.getClass().getCanonicalName());
        } catch (Exception e) {
            log.error("Exception after canceling TBR", e);
        }
        return result;
    }

    private PumpEnactResult cancelTempBasalOnly() {
        PumpEnactResult result = new PumpEnactResult();
        try {
            alertService.ignore(AlertType.WARNING_36);
            connectionService.requestMessage(new CancelTBRMessage()).await();
            result.success = true;
            result.enacted = true;
            result.isTempCancel = true;
            confirmAlert(AlertType.WARNING_36);
            alertService.ignore(null);
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
        } catch (NoActiveTBRToCanceLException e) {
            result.success = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
        } catch (AppLayerErrorException e) {
            log.info("Exception while canceling TBR: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            result.comment = ExceptionTranslator.getString(e);
        } catch (InsightException e) {
            log.info("Exception while canceling TBR: " + e.getClass().getCanonicalName());
            result.comment = ExceptionTranslator.getString(e);
        } catch (Exception e) {
            log.error("Exception while canceling TBR", e);
            result.comment = ExceptionTranslator.getString(e);
        }
        return result;
    }

    @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = cancelExtendedBolusOnly();
        try {
            fetchStatus();
            readHistory();
        } catch (AppLayerErrorException e) {
            log.info("Exception after canceling extended bolus: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
        } catch (InsightException e) {
            log.info("Exception after canceling extended bolus: " + e.getClass().getCanonicalName());
        } catch (Exception e) {
            log.error("Exception after canceling extended bolus", e);
        }
        return result;
    }

    private PumpEnactResult cancelExtendedBolusOnly() {
        PumpEnactResult result = new PumpEnactResult();
        try {
            for (ActiveBolus activeBolus : activeBoluses) {
                if (activeBolus.getBolusType() == BolusType.EXTENDED || activeBolus.getBolusType() == BolusType.MULTIWAVE) {
                    alertService.ignore(AlertType.WARNING_38);
                    CancelBolusMessage cancelBolusMessage = new CancelBolusMessage();
                    cancelBolusMessage.setBolusID(activeBolus.getBolusID());
                    connectionService.requestMessage(cancelBolusMessage).await();
                    confirmAlert(AlertType.WARNING_38);
                    alertService.ignore(null);
                    InsightBolusID insightBolusID = MainApp.getDbHelper().getInsightBolusID(connectionService.getPumpSystemIdentification().getSerialNumber(),
                            activeBolus.getBolusID(), System.currentTimeMillis());
                    if (insightBolusID != null) {
                        ExtendedBolus extendedBolus = MainApp.getDbHelper().getExtendedBolusByPumpId(insightBolusID.id);
                        if (extendedBolus != null) {
                            extendedBolus.durationInMinutes = (int) ((System.currentTimeMillis() - extendedBolus.date) / 60000);
                            if (extendedBolus.durationInMinutes <= 0) {
                                final String _id = extendedBolus._id;
                                if (NSUpload.isIdValid(_id))
                                    NSUpload.removeCareportalEntryFromNS(_id);
                                else UploadQueue.removeID("dbAdd", _id);
                                MainApp.getDbHelper().delete(extendedBolus);
                            } else
                                TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
                        }
                        result.enacted = true;
                        result.success = true;
                    }
                }
            }
            result.success = true;
            result.comment = MainApp.gs(R.string.virtualpump_resultok);
        } catch (AppLayerErrorException e) {
            log.info("Exception while canceling extended bolus: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            result.comment = ExceptionTranslator.getString(e);
        } catch (InsightException e) {
            log.info("Exception while canceling extended bolus: " + e.getClass().getCanonicalName());
            result.comment = ExceptionTranslator.getString(e);
        } catch (Exception e) {
            log.error("Exception while canceling extended bolus", e);
            result.comment = ExceptionTranslator.getString(e);
        }
        return result;
    }

    private void confirmAlert(AlertType alertType) {
        try {
            long started = System.currentTimeMillis();
            while (System.currentTimeMillis() - started < 10000) {
                GetActiveAlertMessage activeAlertMessage = connectionService.requestMessage(new GetActiveAlertMessage()).await();
                if (activeAlertMessage.getAlert() != null) {
                    if (activeAlertMessage.getAlert().getAlertType() == alertType) {
                        ConfirmAlertMessage confirmMessage = new ConfirmAlertMessage();
                        confirmMessage.setAlertID(activeAlertMessage.getAlert().getAlertId());
                        connectionService.requestMessage(confirmMessage).await();
                    } else break;
                }
            }
        } catch (AppLayerErrorException e) {
            log.info("Exception while confirming alert: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
        } catch (InsightException e) {
            log.info("Exception while confirming alert: " + e.getClass().getCanonicalName());
        } catch (Exception e) {
            log.error("Exception while confirming alert", e);
        }
    }

    @Override
    public JSONObject getJSONStatus(Profile profile, String profileName) {
        long now = System.currentTimeMillis();
        if (System.currentTimeMillis() - connectionService.getLastConnected() > (60 * 60 * 1000)) {
            return null;
        }

        final JSONObject pump = new JSONObject();
        final JSONObject battery = new JSONObject();
        final JSONObject status = new JSONObject();
        final JSONObject extended = new JSONObject();
        try {
            status.put("timestamp", DateUtil.toISOString(connectionService.getLastConnected()));
            extended.put("Version", BuildConfig.VERSION_NAME + "-" + BuildConfig.BUILDVERSION);
            try {
                extended.put("ActiveProfile", ProfileFunctions.getInstance().getProfileName());
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

            pump.put("extended", extended);
            if (statusLoaded) {
                status.put("status", operatingMode != OperatingMode.STARTED ? "suspended" : "normal");
                pump.put("status", status);
                battery.put("percent", batteryStatus.getBatteryAmount());
                pump.put("battery", battery);
                pump.put("reservoir", cartridgeStatus.getRemainingAmount());
            }
            pump.put("clock", DateUtil.toISOString(now));
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
        return pump;
    }

    @Override
    public ManufacturerType manufacturer() {
        return ManufacturerType.Roche;
    }

    @Override
    public PumpType model() {
        return PumpType.AccuChekInsightBluetooth;
    }

    @Override
    public String serialNumber() {
        if (connectionService == null || alertService == null) return "Unknown";
        return connectionService.getPumpSystemIdentification().getSerialNumber();
    }

    public PumpEnactResult stopPump() {
        PumpEnactResult result = new PumpEnactResult();
        try {
            SetOperatingModeMessage operatingModeMessage = new SetOperatingModeMessage();
            operatingModeMessage.setOperatingMode(OperatingMode.STOPPED);
            connectionService.requestMessage(operatingModeMessage).await();
            result.success = true;
            result.enacted = true;
            fetchStatus();
            readHistory();
        } catch (AppLayerErrorException e) {
            log.info("Exception while stopping pump: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            result.comment = ExceptionTranslator.getString(e);
        } catch (InsightException e) {
            log.info("Exception while stopping pump: " + e.getClass().getCanonicalName());
            result.comment = ExceptionTranslator.getString(e);
        } catch (Exception e) {
            log.error("Exception while stopping pump", e);
            result.comment = ExceptionTranslator.getString(e);
        }
        return result;
    }

    public PumpEnactResult startPump() {
        PumpEnactResult result = new PumpEnactResult();
        try {
            SetOperatingModeMessage operatingModeMessage = new SetOperatingModeMessage();
            operatingModeMessage.setOperatingMode(OperatingMode.STARTED);
            connectionService.requestMessage(operatingModeMessage).await();
            result.success = true;
            result.enacted = true;
            fetchStatus();
            readHistory();
        } catch (AppLayerErrorException e) {
            log.info("Exception while starting pump: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            result.comment = ExceptionTranslator.getString(e);
        } catch (InsightException e) {
            log.info("Exception while starting pump: " + e.getClass().getCanonicalName());
            result.comment = ExceptionTranslator.getString(e);
        } catch (Exception e) {
            log.error("Exception while starting pump", e);
            result.comment = ExceptionTranslator.getString(e);
        }
        return result;
    }

    public PumpEnactResult setTBROverNotification(boolean enabled) {
        PumpEnactResult result = new PumpEnactResult();
        boolean valueBefore = tbrOverNotificationBlock.isEnabled();
        tbrOverNotificationBlock.setEnabled(enabled);
        try {
            ParameterBlockUtil.writeConfigurationBlock(connectionService, tbrOverNotificationBlock);
            result.success = true;
            result.enacted = true;
        } catch (AppLayerErrorException e) {
            tbrOverNotificationBlock.setEnabled(valueBefore);
            log.info("Exception while updating TBR notification block: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            result.comment = ExceptionTranslator.getString(e);
        } catch (InsightException e) {
            tbrOverNotificationBlock.setEnabled(valueBefore);
            log.info("Exception while updating TBR notification block: " + e.getClass().getSimpleName());
            result.comment = ExceptionTranslator.getString(e);
        } catch (Exception e) {
            tbrOverNotificationBlock.setEnabled(valueBefore);
            log.error("Exception while updating TBR notification block", e);
            result.comment = ExceptionTranslator.getString(e);
        }
        return result;
    }

    @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @Override
    public String shortStatus(boolean veryShort) {
        StringBuilder ret = new StringBuilder();
        if (connectionService.getLastConnected() != 0) {
            Long agoMsec = System.currentTimeMillis() - connectionService.getLastConnected();
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret.append(MainApp.gs(R.string.short_status_last_connected, agoMin) + "\n");
        }
        if (activeTBR != null) {
            ret.append(MainApp.gs(R.string.short_status_tbr, activeTBR.getPercentage(),
                    activeTBR.getInitialDuration() - activeTBR.getRemainingDuration(), activeTBR.getInitialDuration()) + "\n");
        }
        if (activeBoluses != null) for (ActiveBolus activeBolus : activeBoluses) {
            if (activeBolus.getBolusType() == BolusType.STANDARD) continue;
            ret.append(MainApp.gs(activeBolus.getBolusType() == BolusType.MULTIWAVE ? R.string.short_status_multiwave : R.string.short_status_extended,
                    activeBolus.getRemainingAmount(), activeBolus.getInitialAmount(), activeBolus.getRemainingDuration()) + "\n");
        }
        if (!veryShort && totalDailyDose != null) {
            ret.append(MainApp.gs(R.string.short_status_tdd, totalDailyDose.getBolusAndBasal()) + "\n");
        }
        if (cartridgeStatus != null) {
            ret.append(MainApp.gs(R.string.short_status_reservoir, cartridgeStatus.getRemainingAmount()) + "\n");
        }
        if (batteryStatus != null) {
            ret.append(MainApp.gs(R.string.short_status_battery, batteryStatus.getBatteryAmount()) + "\n");
        }
        return ret.toString();
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return SP.getBoolean("insight_enable_tbr_emulation", false);
    }

    @Override
    public PumpEnactResult loadTDDs() {
        return new PumpEnactResult().success(true);
    }

    @Override
    public List<CustomAction> getCustomActions() {
        return null;
    }

    @Override
    public void executeCustomAction(CustomActionType customActionType) {

    }

    private void readHistory() {
        try {
            PumpTime pumpTime = connectionService.requestMessage(new GetDateTimeMessage()).await().getPumpTime();
            String pumpSerial = connectionService.getPumpSystemIdentification().getSerialNumber();
            timeOffset = Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis() - parseDate(pumpTime.getYear(),
                    pumpTime.getMonth(), pumpTime.getDay(), pumpTime.getHour(), pumpTime.getMinute(), pumpTime.getSecond());
            InsightHistoryOffset historyOffset = MainApp.getDbHelper().getInsightHistoryOffset(pumpSerial);
            try {
                List<HistoryEvent> historyEvents = new ArrayList<>();
                if (historyOffset == null) {
                    StartReadingHistoryMessage startMessage = new StartReadingHistoryMessage();
                    startMessage.setDirection(HistoryReadingDirection.BACKWARD);
                    startMessage.setOffset(0xFFFFFFFF);
                    connectionService.requestMessage(startMessage).await();
                    historyEvents = connectionService.requestMessage(new ReadHistoryEventsMessage()).await().getHistoryEvents();
                } else {
                    StartReadingHistoryMessage startMessage = new StartReadingHistoryMessage();
                    startMessage.setDirection(HistoryReadingDirection.FORWARD);
                    startMessage.setOffset(historyOffset.offset + 1);
                    connectionService.requestMessage(startMessage).await();
                    while (true) {
                        List<HistoryEvent> newEvents = connectionService.requestMessage(new ReadHistoryEventsMessage()).await().getHistoryEvents();
                        if (newEvents.size() == 0) break;
                        historyEvents.addAll(newEvents);
                    }
                }
                Collections.sort(historyEvents);
                Collections.reverse(historyEvents);
                if (historyOffset != null) processHistoryEvents(pumpSerial, historyEvents);
                if (historyEvents.size() > 0) {
                    historyOffset = new InsightHistoryOffset();
                    historyOffset.pumpSerial = pumpSerial;
                    historyOffset.offset = historyEvents.get(0).getEventPosition();
                    MainApp.getDbHelper().createOrUpdate(historyOffset);
                }
            } catch (AppLayerErrorException e) {
                log.info("Exception while reading history: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            } catch (InsightException e) {
                log.info("Exception while reading history: " + e.getClass().getSimpleName());
            } catch (Exception e) {
                log.error("Exception while reading history", e);
            } finally {
                try {
                    connectionService.requestMessage(new StopReadingHistoryMessage()).await();
                } catch (Exception ignored) {
                }
            }
        } catch (AppLayerErrorException e) {
            log.info("Exception while reading history: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
        } catch (InsightException e) {
            log.info("Exception while reading history: " + e.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("Exception while reading history", e);
        }
        new Handler(Looper.getMainLooper()).post(() -> RxBus.INSTANCE.send(new EventRefreshOverview("LocalInsightPlugin::readHistory")));
    }

    private void processHistoryEvents(String serial, List<HistoryEvent> historyEvents) {
        List<TemporaryBasal> temporaryBasals = new ArrayList<>();
        List<InsightPumpID> pumpStartedEvents = new ArrayList<>();
        for (HistoryEvent historyEvent : historyEvents)
            if (!processHistoryEvent(serial, temporaryBasals, pumpStartedEvents, historyEvent))
                break;
        Collections.reverse(temporaryBasals);
        for (InsightPumpID pumpID : pumpStartedEvents) {
            InsightPumpID stoppedEvent = MainApp.getDbHelper().getPumpStoppedEvent(pumpID.pumpSerial, pumpID.timestamp);
            if (stoppedEvent == null || stoppedEvent.eventType.equals("PumpPaused")) continue;
            long tbrStart = stoppedEvent.timestamp + 10000;
            TemporaryBasal temporaryBasal = new TemporaryBasal();
            temporaryBasal.durationInMinutes = (int) ((pumpID.timestamp - tbrStart) / 60000);
            temporaryBasal.date = tbrStart;
            temporaryBasal.source = Source.PUMP;
            temporaryBasal.pumpId = pumpID.id;
            temporaryBasal.percentRate = 0;
            temporaryBasal.isAbsolute = false;
            temporaryBasals.add(temporaryBasal);
        }
        Collections.sort(temporaryBasals, (o1, o2) -> (int) (o1.date - o2.date));
        for (TemporaryBasal temporaryBasal : temporaryBasals)
            TreatmentsPlugin.getPlugin().addToHistoryTempBasal(temporaryBasal);
    }

    private boolean processHistoryEvent(String serial, List<TemporaryBasal> temporaryBasals, List<InsightPumpID> pumpStartedEvents, HistoryEvent event) {
        if (event instanceof DefaultDateTimeSetEvent) return false;
        else if (event instanceof DateTimeChangedEvent)
            processDateTimeChangedEvent((DateTimeChangedEvent) event);
        else if (event instanceof CannulaFilledEvent)
            processCannulaFilledEvent((CannulaFilledEvent) event);
        else if (event instanceof TotalDailyDoseEvent)
            processTotalDailyDoseEvent((TotalDailyDoseEvent) event);
        else if (event instanceof TubeFilledEvent) processTubeFilledEvent((TubeFilledEvent) event);
        else if (event instanceof SniffingDoneEvent)
            processSniffingDoneEvent((SniffingDoneEvent) event);
        else if (event instanceof PowerUpEvent) processPowerUpEvent((PowerUpEvent) event);
        else if (event instanceof OperatingModeChangedEvent)
            processOperatingModeChangedEvent(serial, pumpStartedEvents, (OperatingModeChangedEvent) event);
        else if (event instanceof StartOfTBREvent)
            processStartOfTBREvent(serial, temporaryBasals, (StartOfTBREvent) event);
        else if (event instanceof EndOfTBREvent)
            processEndOfTBREvent(serial, temporaryBasals, (EndOfTBREvent) event);
        else if (event instanceof BolusProgrammedEvent)
            processBolusProgrammedEvent(serial, (BolusProgrammedEvent) event);
        else if (event instanceof BolusDeliveredEvent)
            processBolusDeliveredEvent(serial, (BolusDeliveredEvent) event);
        else if (event instanceof OccurrenceOfAlertEvent)
            processOccurrenceOfAlertEvent((OccurrenceOfAlertEvent) event);
        return true;
    }

    private void processDateTimeChangedEvent(DateTimeChangedEvent event) {
        long timeAfter = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(), event.getEventHour(), event.getEventMinute(), event.getEventSecond());
        long timeBefore = parseDate(event.getBeforeYear(), event.getBeforeMonth(), event.getBeforeDay(), event.getBeforeHour(), event.getBeforeMinute(), event.getBeforeSecond());
        timeOffset -= timeAfter - timeBefore;
    }

    private void processCannulaFilledEvent(CannulaFilledEvent event) {
        if (!SP.getBoolean("insight_log_site_changes", false)) return;
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        uploadCareportalEvent(timestamp, CareportalEvent.SITECHANGE);
    }

    private void processTotalDailyDoseEvent(TotalDailyDoseEvent event) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(0));
        calendar.set(Calendar.YEAR, event.getTotalYear());
        calendar.set(Calendar.MONTH, event.getTotalMonth() - 1);
        calendar.set(Calendar.DAY_OF_MONTH, event.getTotalDay());
        TDD tdd = new TDD();
        tdd.basal = event.getBasalTotal();
        tdd.bolus = event.getBolusTotal();
        tdd.total = tdd.basal + tdd.bolus;
        tdd.date = calendar.getTimeInMillis();
        MainApp.getDbHelper().createOrUpdateTDD(tdd);
    }

    private void processTubeFilledEvent(TubeFilledEvent event) {
        if (!SP.getBoolean("insight_log_tube_changes", false)) return;
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        logNote(timestamp, MainApp.gs(R.string.tube_changed));
    }

    private void processSniffingDoneEvent(SniffingDoneEvent event) {
        if (!SP.getBoolean("insight_log_reservoir_changes", false)) return;
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        uploadCareportalEvent(timestamp, CareportalEvent.INSULINCHANGE);
    }

    private void processPowerUpEvent(PowerUpEvent event) {
        if (!SP.getBoolean("insight_log_battery_changes", false)) return;
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        uploadCareportalEvent(timestamp, CareportalEvent.PUMPBATTERYCHANGE);
    }

    private void processOperatingModeChangedEvent(String serial, List<InsightPumpID> pumpStartedEvents, OperatingModeChangedEvent event) {
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        InsightPumpID pumpID = new InsightPumpID();
        pumpID.eventID = event.getEventPosition();
        pumpID.pumpSerial = serial;
        pumpID.timestamp = timestamp;
        switch (event.getNewValue()) {
            case STARTED:
                pumpID.eventType = "PumpStarted";
                pumpStartedEvents.add(pumpID);
                if (SP.getBoolean("insight_log_operating_mode_changes", false))
                    logNote(timestamp, MainApp.gs(R.string.pump_started));
                break;
            case STOPPED:
                pumpID.eventType = "PumpStopped";
                if (SP.getBoolean("insight_log_operating_mode_changes", false))
                    logNote(timestamp, MainApp.gs(R.string.pump_stopped));
                break;
            case PAUSED:
                pumpID.eventType = "PumpPaused";
                if (SP.getBoolean("insight_log_operating_mode_changes", false))
                    logNote(timestamp, MainApp.gs(R.string.pump_paused));
                break;
        }
        MainApp.getDbHelper().createOrUpdate(pumpID);
    }

    private void processStartOfTBREvent(String serial, List<TemporaryBasal> temporaryBasals, StartOfTBREvent event) {
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        InsightPumpID pumpID = new InsightPumpID();
        pumpID.eventID = event.getEventPosition();
        pumpID.pumpSerial = serial;
        pumpID.timestamp = timestamp;
        pumpID.eventType = "StartOfTBR";
        MainApp.getDbHelper().createOrUpdate(pumpID);
        TemporaryBasal temporaryBasal = new TemporaryBasal();
        temporaryBasal.durationInMinutes = event.getDuration();
        temporaryBasal.source = Source.PUMP;
        temporaryBasal.pumpId = pumpID.id;
        temporaryBasal.percentRate = event.getAmount();
        temporaryBasal.isAbsolute = false;
        temporaryBasal.date = timestamp;
        temporaryBasals.add(temporaryBasal);
    }

    private void processEndOfTBREvent(String serial, List<TemporaryBasal> temporaryBasals, EndOfTBREvent event) {
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        InsightPumpID pumpID = new InsightPumpID();
        pumpID.eventID = event.getEventPosition();
        pumpID.pumpSerial = serial;
        pumpID.eventType = "EndOfTBR";
        pumpID.timestamp = timestamp;
        MainApp.getDbHelper().createOrUpdate(pumpID);
        TemporaryBasal temporaryBasal = new TemporaryBasal();
        temporaryBasal.durationInMinutes = 0;
        temporaryBasal.source = Source.PUMP;
        temporaryBasal.pumpId = pumpID.id;
        temporaryBasal.date = timestamp;
        temporaryBasals.add(temporaryBasal);
    }

    private void processBolusProgrammedEvent(String serial, BolusProgrammedEvent event) {
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        InsightBolusID bolusID = MainApp.getDbHelper().getInsightBolusID(serial, event.getBolusID(), timestamp);
        if (bolusID != null && bolusID.endID != null) {
            bolusID.startID = event.getEventPosition();
            MainApp.getDbHelper().createOrUpdate(bolusID);
            return;
        }
        if (bolusID == null || bolusID.startID != null) {
            bolusID = new InsightBolusID();
            bolusID.timestamp = timestamp;
            bolusID.bolusID = event.getBolusID();
            bolusID.pumpSerial = serial;
        }
        bolusID.startID = event.getEventPosition();
        MainApp.getDbHelper().createOrUpdate(bolusID);
        if (event.getBolusType() == BolusType.STANDARD || event.getBolusType() == BolusType.MULTIWAVE) {
            DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
            detailedBolusInfo.date = bolusID.timestamp;
            detailedBolusInfo.source = Source.PUMP;
            detailedBolusInfo.pumpId = bolusID.id;
            detailedBolusInfo.insulin = event.getImmediateAmount();
            TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, true);
        }
        if ((event.getBolusType() == BolusType.EXTENDED || event.getBolusType() == BolusType.MULTIWAVE)) {
            ExtendedBolus extendedBolus = new ExtendedBolus();
            extendedBolus.date = bolusID.timestamp;
            extendedBolus.source = Source.PUMP;
            extendedBolus.durationInMinutes = event.getDuration();
            extendedBolus.insulin = event.getExtendedAmount();
            extendedBolus.pumpId = bolusID.id;
            if (ProfileFunctions.getInstance().getProfile(extendedBolus.date) != null)
                TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
        }
    }

    private void processBolusDeliveredEvent(String serial, BolusDeliveredEvent event) {
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        long startTimestamp = parseRelativeDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(), event.getEventHour(),
                event.getEventMinute(), event.getEventSecond(), event.getStartHour(), event.getStartMinute(), event.getStartSecond()) + timeOffset;
        InsightBolusID bolusID = MainApp.getDbHelper().getInsightBolusID(serial, event.getBolusID(), timestamp);
        if (bolusID == null || bolusID.endID != null) {
            bolusID = new InsightBolusID();
            bolusID.timestamp = startTimestamp;
            bolusID.bolusID = event.getBolusID();
            bolusID.pumpSerial = serial;
        }
        bolusID.endID = event.getEventPosition();
        MainApp.getDbHelper().createOrUpdate(bolusID);
        if (event.getBolusType() == BolusType.STANDARD || event.getBolusType() == BolusType.MULTIWAVE) {
            DetailedBolusInfo detailedBolusInfo = new DetailedBolusInfo();
            detailedBolusInfo.date = bolusID.timestamp;
            detailedBolusInfo.source = Source.PUMP;
            detailedBolusInfo.pumpId = bolusID.id;
            detailedBolusInfo.insulin = event.getImmediateAmount();
            TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, true);
        }
        if (event.getBolusType() == BolusType.EXTENDED || event.getBolusType() == BolusType.MULTIWAVE) {
            if (event.getDuration() == 0) {
                ExtendedBolus extendedBolus = MainApp.getDbHelper().getExtendedBolusByPumpId(bolusID.id);
                if (extendedBolus != null) {
                    final String _id = extendedBolus._id;
                    if (NSUpload.isIdValid(_id)) NSUpload.removeCareportalEntryFromNS(_id);
                    else UploadQueue.removeID("dbAdd", _id);
                    MainApp.getDbHelper().delete(extendedBolus);
                }
            } else {
                ExtendedBolus extendedBolus = new ExtendedBolus();
                extendedBolus.date = bolusID.timestamp;
                extendedBolus.source = Source.PUMP;
                extendedBolus.durationInMinutes = event.getDuration();
                extendedBolus.insulin = event.getExtendedAmount();
                extendedBolus.pumpId = bolusID.id;
                if (ProfileFunctions.getInstance().getProfile(extendedBolus.date) != null)
                    TreatmentsPlugin.getPlugin().addToHistoryExtendedBolus(extendedBolus);
            }
        }
    }

    private void processOccurrenceOfAlertEvent(OccurrenceOfAlertEvent event) {
        if (!SP.getBoolean("insight_log_alerts", false)) return;
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        Integer code = null;
        Integer title = null;
        switch (event.getAlertType()) {
            case ERROR_6:
                code = R.string.alert_e6_code;
                title = R.string.alert_e6_title;
                break;
            case ERROR_10:
                code = R.string.alert_e10_code;
                title = R.string.alert_e10_title;
                break;
            case ERROR_13:
                code = R.string.alert_e13_code;
                title = R.string.alert_e13_title;
                break;
            case MAINTENANCE_20:
                code = R.string.alert_m20_code;
                title = R.string.alert_m20_title;
                break;
            case MAINTENANCE_21:
                code = R.string.alert_m21_code;
                title = R.string.alert_m21_title;
                break;
            case MAINTENANCE_22:
                code = R.string.alert_m22_code;
                title = R.string.alert_m22_title;
                break;
            case MAINTENANCE_23:
                code = R.string.alert_m23_code;
                title = R.string.alert_m23_title;
                break;
            case MAINTENANCE_24:
                code = R.string.alert_m24_code;
                title = R.string.alert_m24_title;
                break;
            case MAINTENANCE_25:
                code = R.string.alert_m25_code;
                title = R.string.alert_m25_title;
                break;
            case MAINTENANCE_26:
                code = R.string.alert_m26_code;
                title = R.string.alert_m26_title;
                break;
            case MAINTENANCE_27:
                code = R.string.alert_m27_code;
                title = R.string.alert_m27_title;
                break;
            case MAINTENANCE_28:
                code = R.string.alert_m28_code;
                title = R.string.alert_m28_title;
                break;
            case MAINTENANCE_29:
                code = R.string.alert_m29_code;
                title = R.string.alert_m29_title;
                break;
            case MAINTENANCE_30:
                code = R.string.alert_m30_code;
                title = R.string.alert_m30_title;
                break;
            case WARNING_31:
                code = R.string.alert_w31_code;
                title = R.string.alert_w31_title;
                break;
            case WARNING_32:
                code = R.string.alert_w32_code;
                title = R.string.alert_w32_title;
                break;
            case WARNING_33:
                code = R.string.alert_w33_code;
                title = R.string.alert_w33_title;
                break;
            case WARNING_34:
                code = R.string.alert_w34_code;
                title = R.string.alert_w34_title;
                break;
            case WARNING_39:
                code = R.string.alert_w39_code;
                title = R.string.alert_w39_title;
                break;
        }
        if (code != null)
            logNote(timestamp, MainApp.gs(R.string.insight_alert_formatter, MainApp.gs(code), MainApp.gs(title)));
    }

    private long parseDate(int year, int month, int day, int hour, int minute, int second) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, second);
        return calendar.getTimeInMillis();
    }

    private void logNote(long date, String note) {
        try {
            if (MainApp.getDbHelper().getCareportalEventFromTimestamp(date) != null)
                return;
            JSONObject data = new JSONObject();
            String enteredBy = SP.getString("careportal_enteredby", "");
            if (!enteredBy.equals("")) data.put("enteredBy", enteredBy);
            data.put("created_at", DateUtil.toISOString(date));
            data.put("eventType", CareportalEvent.NOTE);
            data.put("notes", note);
            CareportalEvent careportalEvent = new CareportalEvent();
            careportalEvent.date = date;
            careportalEvent.source = Source.USER;
            careportalEvent.eventType = CareportalEvent.NOTE;
            careportalEvent.json = data.toString();
            MainApp.getDbHelper().createOrUpdate(careportalEvent);
            NSUpload.uploadCareportalEntryToNS(data);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    private long parseRelativeDate(int year, int month, int day, int hour, int minute, int second, int relativeHour, int relativeMinute, int relativeSecond) {
        if (relativeHour * 60 * 60 + relativeMinute * 60 + relativeSecond >= hour * 60 * 60 * minute * 60 + second)
            day--;
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, relativeHour);
        calendar.set(Calendar.MINUTE, relativeMinute);
        calendar.set(Calendar.SECOND, relativeSecond);
        return calendar.getTimeInMillis();
    }

    private void uploadCareportalEvent(long date, String event) {
        if (MainApp.getDbHelper().getCareportalEventFromTimestamp(date) != null)
            return;
        try {
            JSONObject data = new JSONObject();
            String enteredBy = SP.getString("careportal_enteredby", "");
            if (!enteredBy.equals("")) data.put("enteredBy", enteredBy);
            data.put("created_at", DateUtil.toISOString(date));
            data.put("eventType", event);
            CareportalEvent careportalEvent = new CareportalEvent();
            careportalEvent.date = date;
            careportalEvent.source = Source.USER;
            careportalEvent.eventType = event;
            careportalEvent.json = data.toString();
            MainApp.getDbHelper().createOrUpdate(careportalEvent);
            NSUpload.uploadCareportalEntryToNS(data);
        } catch (JSONException e) {
            log.error("Unhandled exception", e);
        }
    }

    @Override
    public Constraint<Integer> applyBasalPercentConstraints(Constraint<Integer> percentRate, Profile profile) {
        percentRate.setIfGreater(0, String.format(MainApp.gs(R.string.limitingpercentrate), 0, MainApp.gs(R.string.itmustbepositivevalue)), this);
        percentRate.setIfSmaller(getPumpDescription().maxTempPercent, String.format(MainApp.gs(R.string.limitingpercentrate), getPumpDescription().maxTempPercent, MainApp.gs(R.string.pumplimit)), this);
        return percentRate;
    }

    @Override
    public Constraint<Double> applyBolusConstraints(Constraint<Double> insulin) {
        if (!limitsFetched) return insulin;
        insulin.setIfSmaller(maximumBolusAmount, String.format(MainApp.gs(R.string.limitingbolus), maximumBolusAmount, MainApp.gs(R.string.pumplimit)), this);
        if (insulin.value() < minimumBolusAmount) {

            //TODO: Add function to Constraints or use different approach
            // This only works if the interface of the InsightPlugin is called last.
            // If not, another constraint could theoretically set the value between 0 and minimumBolusAmount

            insulin.set(0d, String.format(MainApp.gs(R.string.limitingbolus), minimumBolusAmount, MainApp.gs(R.string.pumplimit)), this);
        }
        return insulin;
    }

    @Override
    public Constraint<Double> applyExtendedBolusConstraints(Constraint<Double> insulin) {
        return applyBolusConstraints(insulin);
    }

    @Override
    public void onStateChanged(InsightState state) {
        if (state == InsightState.CONNECTED) {
            statusLoaded = false;
            new Handler(Looper.getMainLooper()).post(() -> RxBus.INSTANCE.send(new EventDismissNotification(Notification.INSIGHT_TIMEOUT_DURING_HANDSHAKE)));
        } else if (state == InsightState.NOT_PAIRED) {
            connectionService.withdrawConnectionRequest(this);
            statusLoaded = false;
            profileBlocks = null;
            operatingMode = null;
            batteryStatus = null;
            cartridgeStatus = null;
            totalDailyDose = null;
            activeBasalRate = null;
            activeTBR = null;
            activeBoluses = null;
            tbrOverNotificationBlock = null;
            new Handler(Looper.getMainLooper()).post(() -> RxBus.INSTANCE.send(new EventRefreshOverview("LocalInsightPlugin::onStateChanged")));
        }
        new Handler(Looper.getMainLooper()).post(() -> RxBus.INSTANCE.send(new EventLocalInsightUpdateGUI()));
    }

    @Override
    public void onPumpPaired() {
        ConfigBuilderPlugin.getPlugin().getCommandQueue().readStatus("Pump paired", null);
    }

    @Override
    public void onTimeoutDuringHandshake() {
        Notification notification = new Notification(Notification.INSIGHT_TIMEOUT_DURING_HANDSHAKE, MainApp.gs(R.string.timeout_during_handshake), Notification.URGENT);
        new Handler(Looper.getMainLooper()).post(() -> RxBus.INSTANCE.send(new EventNewNotification(notification)));
    }

    @Override
    public boolean canHandleDST() {
        return true;
    }

    @Override
    public void timeDateOrTimeZoneChanged() {

    }

}