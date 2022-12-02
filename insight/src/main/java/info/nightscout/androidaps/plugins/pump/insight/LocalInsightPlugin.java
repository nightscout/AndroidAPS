package info.nightscout.androidaps.plugins.pump.insight;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.android.HasAndroidInjector;
import info.nightscout.androidaps.insight.R;
import info.nightscout.androidaps.insight.database.InsightBolusID;
import info.nightscout.androidaps.insight.database.InsightDatabase;
import info.nightscout.androidaps.insight.database.InsightDbHelper;
import info.nightscout.androidaps.insight.database.InsightHistoryOffset;
import info.nightscout.androidaps.insight.database.InsightPumpID;
import info.nightscout.androidaps.insight.database.InsightPumpID.EventType;
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
import info.nightscout.core.events.EventNewNotification;
import info.nightscout.interfaces.Config;
import info.nightscout.interfaces.constraints.Constraint;
import info.nightscout.interfaces.constraints.Constraints;
import info.nightscout.interfaces.notifications.Notification;
import info.nightscout.interfaces.plugin.OwnDatabasePlugin;
import info.nightscout.interfaces.plugin.PluginDescription;
import info.nightscout.interfaces.plugin.PluginType;
import info.nightscout.interfaces.profile.Profile;
import info.nightscout.interfaces.profile.ProfileFunction;
import info.nightscout.interfaces.pump.DetailedBolusInfo;
import info.nightscout.interfaces.pump.Insight;
import info.nightscout.interfaces.pump.Pump;
import info.nightscout.interfaces.pump.PumpEnactResult;
import info.nightscout.interfaces.pump.PumpPluginBase;
import info.nightscout.interfaces.pump.PumpSync;
import info.nightscout.interfaces.pump.PumpSync.PumpState.TemporaryBasal;
import info.nightscout.interfaces.pump.defs.ManufacturerType;
import info.nightscout.interfaces.pump.defs.PumpDescription;
import info.nightscout.interfaces.pump.defs.PumpType;
import info.nightscout.interfaces.queue.CommandQueue;
import info.nightscout.rx.bus.RxBus;
import info.nightscout.rx.events.EventDismissNotification;
import info.nightscout.rx.events.EventInitializationChanged;
import info.nightscout.rx.events.EventOverviewBolusProgress;
import info.nightscout.rx.events.EventRefreshOverview;
import info.nightscout.rx.logging.AAPSLogger;
import info.nightscout.rx.logging.LTag;
import info.nightscout.shared.interfaces.ResourceHelper;
import info.nightscout.shared.sharedPreferences.SP;
import info.nightscout.shared.utils.DateUtil;
import info.nightscout.shared.utils.T;

@Singleton
public class LocalInsightPlugin extends PumpPluginBase implements Pump, Insight, Constraints, OwnDatabasePlugin,
        InsightConnectionService.StateCallback {

    private final AAPSLogger aapsLogger;
    private final RxBus rxBus;
    private final ResourceHelper rh;
    private final SP sp;
    private final CommandQueue commandQueue;
    private final ProfileFunction profileFunction;
    private final Context context;
    private final DateUtil dateUtil;
    private final InsightDbHelper insightDbHelper;
    private final PumpSync pumpSync;
    private final InsightDatabase insightDatabase;

    public static final String ALERT_CHANNEL_ID = "AAPS-InsightAlert";

    private final PumpDescription pumpDescription;
    private InsightAlertService alertService;
    private InsightConnectionService connectionService;
    private long timeOffset;
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (binder instanceof InsightConnectionService.LocalBinder) {
                connectionService = ((InsightConnectionService.LocalBinder) binder).getService();
                connectionService.registerStateCallback(LocalInsightPlugin.this);
            } else if (binder instanceof InsightAlertService.LocalBinder) {
                alertService = ((InsightAlertService.LocalBinder) binder).getService();
            }
            if (connectionService != null && alertService != null) {
                rxBus.send(new EventInitializationChanged());
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
    private double minimumBolusAmount;
    private OperatingMode operatingMode;
    private BatteryStatus batteryStatus;
    private CartridgeStatus cartridgeStatus;
    private TotalDailyDose totalDailyDose;
    private ActiveBasalRate activeBasalRate;
    private ActiveTBR activeTBR;
    private List<ActiveBolus> activeBoluses;
    private boolean statusLoaded;
    private TBROverNotificationBlock tbrOverNotificationBlock;
    public double lastBolusAmount = 0;
    public long lastBolusTimestamp = 0L;

    @Inject
    public LocalInsightPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBus rxBus,
            ResourceHelper rh,
            SP sp,
            CommandQueue commandQueue,
            ProfileFunction profileFunction,
            Context context,
            Config config,
            DateUtil dateUtil,
            InsightDbHelper insightDbHelper,
            PumpSync pumpSync,
            InsightDatabase insightDatabase
    ) {
        super(new PluginDescription()
                        .pluginIcon(info.nightscout.core.ui.R.drawable.ic_insight_128)
                        .pluginName(R.string.insight_local)
                        .shortName(R.string.insightpump_shortname)
                        .mainType(PluginType.PUMP)
                        .description(R.string.description_pump_insight_local)
                        .fragmentClass(LocalInsightFragment.class.getName())
                        .preferencesId(config.getAPS() ? R.xml.pref_insight_local_full : R.xml.pref_insight_local_pumpcontrol),
                injector, aapsLogger, rh, commandQueue

        );
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.rh = rh;
        this.sp = sp;
        this.commandQueue = commandQueue;
        this.profileFunction = profileFunction;
        this.context = context;
        this.dateUtil = dateUtil;
        this.insightDbHelper = insightDbHelper;
        this.pumpSync = pumpSync;
        this.insightDatabase = insightDatabase;

        pumpDescription = new PumpDescription();
        pumpDescription.fillFor(PumpType.ACCU_CHEK_INSIGHT);
        lastBolusTimestamp = sp.getLong(R.string.key_insight_last_bolus_timestamp, 0L);
        lastBolusAmount = sp.getDouble(R.string.key_insight_last_bolus_amount, 0.0);
    }

    public TBROverNotificationBlock getTBROverNotificationBlock() {
        return tbrOverNotificationBlock;
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
        context.bindService(new Intent(context, InsightConnectionService.class), serviceConnection, Context.BIND_AUTO_CREATE);
        context.bindService(new Intent(context, InsightAlertService.class), serviceConnection, Context.BIND_AUTO_CREATE);
        createNotificationChannel();
    }

    private void createNotificationChannel() {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(ALERT_CHANNEL_ID, rh.gs(R.string.insight_alert_notification_channel), NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(null, null);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    protected void onStop() {
        super.onStop();
        context.unbindService(serviceConnection);
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
    public void connect(@NonNull String reason) {
        if (connectionService != null && alertService != null)
            connectionService.requestConnection(this);
    }

    @Override
    public void disconnect(@NonNull String reason) {
        if (connectionService != null && alertService != null)
            connectionService.withdrawConnectionRequest(this);
    }

    @Override
    public void stopConnecting() {
        if (connectionService != null && alertService != null)
            connectionService.withdrawConnectionRequest(this);
    }

    @Override
    public void getPumpStatus(@NonNull String reason) {
        try {
            tbrOverNotificationBlock = ParameterBlockUtil.readParameterBlock(connectionService, Service.CONFIGURATION, TBROverNotificationBlock.class);
            readHistory();
            fetchBasalProfile();
            fetchLimitations();
            updatePumpTimeIfNeeded();
            fetchStatus();
        } catch (AppLayerErrorException e) {
            aapsLogger.info(LTag.PUMP, "Exception while fetching status: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
        } catch (InsightException e) {
            aapsLogger.info(LTag.PUMP, "Exception while fetching status: " + e.getClass().getCanonicalName());
        } catch (Exception e) {
            aapsLogger.error("Exception while fetching status", e);
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
        if (calendar.get(Calendar.HOUR_OF_DAY) != pumpTime.getHour() || Math.abs(calendar.getTimeInMillis() - dateUtil.now()) > 10000) {
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
            Notification notification = new Notification(Notification.INSIGHT_DATE_TIME_UPDATED, rh.gs(info.nightscout.core.ui.R.string.pump_time_updated), Notification.INFO, 60);
            rxBus.send(new EventNewNotification(notification));
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
        new Handler(Looper.getMainLooper()).post(() -> {
            rxBus.send(new EventLocalInsightUpdateGUI());
            rxBus.send(new EventRefreshOverview("LocalInsightPlugin::fetchStatus", false));
        });
    }

    private void fetchLimitations() throws Exception {
        maximumBolusAmount = ParameterBlockUtil.readParameterBlock(connectionService, Service.CONFIGURATION, MaxBolusAmountBlock.class).getAmountLimitation();
        double maximumBasalAmount = ParameterBlockUtil.readParameterBlock(connectionService, Service.CONFIGURATION, MaxBasalAmountBlock.class).getAmountLimitation();
        minimumBolusAmount = ParameterBlockUtil.readParameterBlock(connectionService, Service.CONFIGURATION, FactoryMinBolusAmountBlock.class).getAmountLimitation();
        double minimumBasalAmount = ParameterBlockUtil.readParameterBlock(connectionService, Service.CONFIGURATION, FactoryMinBasalAmountBlock.class).getAmountLimitation();
        this.pumpDescription.setBasalMaximumRate(maximumBasalAmount);
        this.pumpDescription.setBasalMinimumRate(minimumBasalAmount);
        limitsFetched = true;
    }

    @NonNull @Override
    public PumpEnactResult setNewBasalProfile(Profile profile) {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        rxBus.send(new EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED));
        List<BasalProfileBlock> profileBlocks = new ArrayList<>();
        for (int i = 0; i < profile.getBasalValues().length; i++) {
            Profile.ProfileValue basalValue = profile.getBasalValues()[i];
            Profile.ProfileValue nextValue = null;
            if (profile.getBasalValues().length > i + 1)
                nextValue = profile.getBasalValues()[i + 1];
            BasalProfileBlock profileBlock = new BasalProfileBlock();
            profileBlock.setBasalAmount(basalValue.getValue() > 5 ? Math.round(basalValue.getValue() / 0.1) * 0.1 : Math.round(basalValue.getValue() / 0.01) * 0.01);
            profileBlock.setDuration((((nextValue != null ? nextValue.getTimeAsSeconds() : 24 * 60 * 60) - basalValue.getTimeAsSeconds()) / 60));
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
            rxBus.send(new EventDismissNotification(Notification.FAILED_UPDATE_PROFILE));
            Notification notification = new Notification(Notification.PROFILE_SET_OK, rh.gs(info.nightscout.core.ui.R.string.profile_set_ok), Notification.INFO, 60);
            rxBus.send(new EventNewNotification(notification));
            result.success(true)
                    .enacted(true)
                    .comment(info.nightscout.core.ui.R.string.virtualpump_resultok);
            this.profileBlocks = profileBlocks;
            try {
                fetchStatus();
            } catch (Exception ignored) {
            }
        } catch (AppLayerErrorException e) {
            aapsLogger.info(LTag.PUMP, "Exception while setting profile: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            Notification notification = new Notification(Notification.FAILED_UPDATE_PROFILE, rh.gs(info.nightscout.core.ui.R.string.failed_update_basal_profile), Notification.URGENT);
            rxBus.send(new EventNewNotification(notification));
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (InsightException e) {
            aapsLogger.info(LTag.PUMP, "Exception while setting profile: " + e.getClass().getCanonicalName());
            Notification notification = new Notification(Notification.FAILED_UPDATE_PROFILE, rh.gs(info.nightscout.core.ui.R.string.failed_update_basal_profile), Notification.URGENT);
            rxBus.send(new EventNewNotification(notification));
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (Exception e) {
            aapsLogger.error("Exception while setting profile", e);
            Notification notification = new Notification(Notification.FAILED_UPDATE_PROFILE, rh.gs(info.nightscout.core.ui.R.string.failed_update_basal_profile), Notification.URGENT);
            rxBus.send(new EventNewNotification(notification));
            result.comment(ExceptionTranslator.getString(context, e));
        }
        return result;
    }

    @Override
    public boolean isThisProfileSet(@NonNull Profile profile) {
        if (!isInitialized() || profileBlocks == null) return true;
        if (profile.getBasalValues().length != profileBlocks.size()) return false;
        if (activeBasalProfile != BasalProfile.PROFILE_1) return false;
        for (int i = 0; i < profileBlocks.size(); i++) {
            BasalProfileBlock profileBlock = profileBlocks.get(i);
            Profile.ProfileValue basalValue = profile.getBasalValues()[i];
            Profile.ProfileValue nextValue = null;
            if (profile.getBasalValues().length > i + 1)
                nextValue = profile.getBasalValues()[i + 1];
            if (profileBlock.getDuration() * 60 != (nextValue != null ? nextValue.getTimeAsSeconds() : 24 * 60 * 60) - basalValue.getTimeAsSeconds())
                return false;
            if (Math.abs(profileBlock.getBasalAmount() - basalValue.getValue()) > (basalValue.getValue() > 5 ? 0.051 : 0.0051))
                return false;
        }
        return true;
    }

    @Override
    public long lastDataTime() {
        if (connectionService == null || alertService == null) return dateUtil.now();
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

    @NonNull @Override
    public PumpEnactResult deliverTreatment(DetailedBolusInfo detailedBolusInfo) {
        if (detailedBolusInfo.insulin == 0 || detailedBolusInfo.carbs > 0) {
            throw new IllegalArgumentException(detailedBolusInfo.toString(), new Exception());
        }
        PumpEnactResult result = new PumpEnactResult(getInjector());
        double insulin = Math.round(detailedBolusInfo.insulin / 0.01) * 0.01;
        if (insulin > 0) {
            try {
                synchronized ($bolusLock) {
                    DeliverBolusMessage bolusMessage = new DeliverBolusMessage();
                    bolusMessage.setBolusType(BolusType.STANDARD);
                    bolusMessage.setDuration(0);
                    bolusMessage.setExtendedAmount(0);
                    bolusMessage.setImmediateAmount(insulin);
                    bolusMessage.setVibration(sp.getBoolean(detailedBolusInfo.getBolusType() == DetailedBolusInfo.BolusType.SMB ? R.string.key_insight_disable_vibration_auto : R.string.key_insight_disable_vibration, false));
                    bolusID = connectionService.requestMessage(bolusMessage).await().getBolusId();
                    bolusCancelled = false;
                }
                result.success(true).enacted(true);
                EventOverviewBolusProgress.Treatment t = new EventOverviewBolusProgress.Treatment(0, 0, detailedBolusInfo.getBolusType() == DetailedBolusInfo.BolusType.SMB, detailedBolusInfo.getId());
                final EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.INSTANCE;
                bolusingEvent.setT(t);
                bolusingEvent.setStatus(rh.gs(info.nightscout.pump.common.R.string.bolus_delivered_so_far, 0d, insulin));
                bolusingEvent.setPercent(0);
                rxBus.send(bolusingEvent);
                int trials = 0;
                long now = dateUtil.now();
                String serial = serialNumber();
                insightDbHelper.createOrUpdate(new InsightBolusID(
                        now,
                        serial,
                        bolusID,
                        null,
                        null
                ));
                InsightBolusID insightBolusID = insightDbHelper.getInsightBolusID(serial, bolusID, now);
                pumpSync.syncBolusWithPumpId(
                        insightBolusID.getTimestamp(),
                        detailedBolusInfo.insulin,
                        detailedBolusInfo.getBolusType(),
                        insightBolusID.getId(),
                        PumpType.ACCU_CHEK_INSIGHT,
                        serialNumber());
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
                        bolusingEvent.setStatus(rh.gs(info.nightscout.pump.common.R.string.bolus_delivered_so_far, activeBolus.getInitialAmount() - activeBolus.getRemainingAmount(),
                                activeBolus.getInitialAmount()));
                        if (percentBefore != bolusingEvent.getPercent())
                            rxBus.send(bolusingEvent);
                    } else {
                        synchronized ($bolusLock) {
                            if (bolusCancelled || trials == -1 || trials++ >= 5) {
                                if (!bolusCancelled) {
                                    bolusingEvent.setStatus(rh.gs(info.nightscout.pump.common.R.string.bolus_delivered_so_far, insulin, insulin));
                                    bolusingEvent.setPercent(100);
                                    rxBus.send(bolusingEvent);
                                }
                                break;
                            }
                        }
                    }
                    SystemClock.sleep(200);
                }
                readHistory();
                fetchStatus();
            } catch (AppLayerErrorException e) {
                aapsLogger.info(LTag.PUMP, "Exception while delivering bolus: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
                result.comment(ExceptionTranslator.getString(context, e));
            } catch (InsightException e) {
                aapsLogger.info(LTag.PUMP, "Exception while delivering bolus: " + e.getClass().getCanonicalName());
                result.comment(ExceptionTranslator.getString(context, e));
            } catch (Exception e) {
                aapsLogger.error("Exception while delivering bolus", e);
                result.comment(ExceptionTranslator.getString(context, e));
            }
            result.bolusDelivered(insulin);
        }
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
                    confirmAlert(AlertType.WARNING_38);
                    alertService.ignore(null);
                }
            } catch (AppLayerErrorException e) {
                aapsLogger.info(LTag.PUMP, "Exception while canceling bolus: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            } catch (InsightException e) {
                aapsLogger.info(LTag.PUMP, "Exception while canceling bolus: " + e.getClass().getCanonicalName());
            } catch (Exception e) {
                aapsLogger.error("Exception while canceling bolus", e);
            }
        }).start();
    }

    @NonNull @Override
    public PumpEnactResult setTempBasalAbsolute(double absoluteRate, int durationInMinutes, @NonNull Profile profile, boolean enforceNew, @NonNull PumpSync.TemporaryBasalType tbrType) {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        if (activeBasalRate == null) return result;
        if (activeBasalRate.getActiveBasalRate() == 0) return result;
        double percent = 100D / activeBasalRate.getActiveBasalRate() * absoluteRate;
        if (isFakingTempsByExtendedBoluses()) {
            PumpEnactResult cancelEBResult = cancelExtendedBolusOnly();
            if (cancelEBResult.getSuccess()) {
                if (percent > 250) {
                    PumpEnactResult cancelTBRResult = cancelTempBasalOnly();
                    if (cancelTBRResult.getSuccess()) {
                        PumpEnactResult ebResult = setExtendedBolusOnly((absoluteRate - getBaseBasalRate()) / 60D
                                        * ((double) durationInMinutes), durationInMinutes,
                                sp.getBoolean(R.string.key_insight_disable_vibration_auto, false));
                        if (ebResult.getSuccess()) {
                            result.success(true)
                                    .enacted(true)
                                    .isPercent(false)
                                    .absolute(absoluteRate)
                                    .duration(durationInMinutes)
                                    .comment(info.nightscout.core.ui.R.string.virtualpump_resultok);
                        } else {
                            result.comment(ebResult.getComment());
                        }
                    } else {
                        result.comment(cancelTBRResult.getComment());
                    }
                } else {
                    return setTempBasalPercent((int) Math.round(percent), durationInMinutes, profile, enforceNew, tbrType);
                }
            } else {
                result.comment(cancelEBResult.getComment());
            }
        } else {
            return setTempBasalPercent((int) Math.round(percent), durationInMinutes, profile, enforceNew, tbrType);
        }
        try {
            fetchStatus();
            readHistory();
        } catch (AppLayerErrorException e) {
            aapsLogger.info(LTag.PUMP, "Exception after setting TBR: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
        } catch (InsightException e) {
            aapsLogger.info(LTag.PUMP, "Exception after setting TBR: " + e.getClass().getCanonicalName());
        } catch (Exception e) {
            aapsLogger.error("Exception after setting TBR", e);
        }
        return result;
    }

    @NonNull @Override
    public PumpEnactResult setTempBasalPercent(int percent, int durationInMinutes, @NonNull Profile profile, boolean enforceNew, @NonNull PumpSync.TemporaryBasalType tbrType) {
        PumpEnactResult result = new PumpEnactResult(getInjector());
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
            result.isPercent(true)
                    .percent(percent)
                    .duration(durationInMinutes)
                    .success(true)
                    .enacted(true)
                    .comment(info.nightscout.core.ui.R.string.virtualpump_resultok);
            readHistory();
            fetchStatus();
        } catch (AppLayerErrorException e) {
            aapsLogger.info(LTag.PUMP, "Exception while setting TBR: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (InsightException e) {
            aapsLogger.info(LTag.PUMP, "Exception while setting TBR: " + e.getClass().getCanonicalName());
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (Exception e) {
            aapsLogger.error("Exception while setting TBR", e);
            result.comment(ExceptionTranslator.getString(context, e));
        }
        return result;
    }

    @NonNull @Override
    public PumpEnactResult setExtendedBolus(double insulin, int durationInMinutes) {
        PumpEnactResult result = cancelExtendedBolusOnly();
        if (result.getSuccess())
            result = setExtendedBolusOnly(insulin, durationInMinutes, sp.getBoolean(R.string.key_insight_disable_vibration, false));
        try {
            fetchStatus();
            readHistory();
        } catch (AppLayerErrorException e) {
            aapsLogger.info(LTag.PUMP, "Exception after delivering extended bolus: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
        } catch (InsightException e) {
            aapsLogger.info(LTag.PUMP, "Exception after delivering extended bolus: " + e.getClass().getCanonicalName());
        } catch (Exception e) {
            aapsLogger.error("Exception after delivering extended bolus", e);
        }
        return result;
    }

    public PumpEnactResult setExtendedBolusOnly(Double insulin, Integer durationInMinutes, boolean disableVibration) {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        try {
            DeliverBolusMessage bolusMessage = new DeliverBolusMessage();
            bolusMessage.setBolusType(BolusType.EXTENDED);
            bolusMessage.setDuration(durationInMinutes);
            bolusMessage.setExtendedAmount(insulin);
            bolusMessage.setImmediateAmount(0);
            bolusMessage.setVibration(disableVibration);
            int bolusID = connectionService.requestMessage(bolusMessage).await().getBolusId();
            insightDbHelper.createOrUpdate(new InsightBolusID(
                    dateUtil.now(),
                    serialNumber(),
                    bolusID,
                    null,
                    null
            ));
            result.success(true).enacted(true).comment(info.nightscout.core.ui.R.string.virtualpump_resultok);
        } catch (AppLayerErrorException e) {
            aapsLogger.info(LTag.PUMP, "Exception while delivering extended bolus: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (InsightException e) {
            aapsLogger.info(LTag.PUMP, "Exception while delivering extended bolus: " + e.getClass().getCanonicalName());
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (Exception e) {
            aapsLogger.error("Exception while delivering extended bolus", e);
            result.comment(ExceptionTranslator.getString(context, e));
        }
        return result;
    }

    @NonNull @Override
    public PumpEnactResult cancelTempBasal(boolean enforceNew) {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        PumpEnactResult cancelEBResult = null;
        if (isFakingTempsByExtendedBoluses()) cancelEBResult = cancelExtendedBolusOnly();
        PumpEnactResult cancelTBRResult = cancelTempBasalOnly();
        result.success((cancelEBResult == null || (cancelEBResult != null && cancelEBResult.getSuccess())) && cancelTBRResult.getSuccess());
        result.enacted((cancelEBResult != null && cancelEBResult.getEnacted()) || cancelTBRResult.getEnacted());
        result.comment(cancelEBResult != null ? cancelEBResult.getComment() : cancelTBRResult.getComment());
        try {
            fetchStatus();
            readHistory();
        } catch (AppLayerErrorException e) {
            aapsLogger.info(LTag.PUMP, "Exception after canceling TBR: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
        } catch (InsightException e) {
            aapsLogger.info(LTag.PUMP, "Exception after canceling TBR: " + e.getClass().getCanonicalName());
        } catch (Exception e) {
            aapsLogger.error("Exception after canceling TBR", e);
        }
        return result;
    }

    private PumpEnactResult cancelTempBasalOnly() {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        try {
            alertService.ignore(AlertType.WARNING_36);
            connectionService.requestMessage(new CancelTBRMessage()).await();
            result.success(true)
                    .enacted(true)
                    .isTempCancel(true);
            confirmAlert(AlertType.WARNING_36);
            alertService.ignore(null);
            result.comment(info.nightscout.core.ui.R.string.virtualpump_resultok);
        } catch (NoActiveTBRToCanceLException e) {
            result.success(true);
            result.comment(info.nightscout.core.ui.R.string.virtualpump_resultok);
        } catch (AppLayerErrorException e) {
            aapsLogger.info(LTag.PUMP, "Exception while canceling TBR: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (InsightException e) {
            aapsLogger.info(LTag.PUMP, "Exception while canceling TBR: " + e.getClass().getCanonicalName());
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (Exception e) {
            aapsLogger.error("Exception while canceling TBR", e);
            result.comment(ExceptionTranslator.getString(context, e));
        }
        return result;
    }

    @NonNull @Override
    public PumpEnactResult cancelExtendedBolus() {
        PumpEnactResult result = cancelExtendedBolusOnly();
        try {
            fetchStatus();
            readHistory();
        } catch (AppLayerErrorException e) {
            aapsLogger.info(LTag.PUMP, "Exception after canceling extended bolus: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
        } catch (InsightException e) {
            aapsLogger.info(LTag.PUMP, "Exception after canceling extended bolus: " + e.getClass().getCanonicalName());
        } catch (Exception e) {
            aapsLogger.error("Exception after canceling extended bolus", e);
        }
        return result;
    }

    private PumpEnactResult cancelExtendedBolusOnly() {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        try {
            for (ActiveBolus activeBolus : activeBoluses) {
                if (activeBolus.getBolusType() == BolusType.EXTENDED || activeBolus.getBolusType() == BolusType.MULTIWAVE) {
                    alertService.ignore(AlertType.WARNING_38);
                    CancelBolusMessage cancelBolusMessage = new CancelBolusMessage();
                    cancelBolusMessage.setBolusID(activeBolus.getBolusID());
                    connectionService.requestMessage(cancelBolusMessage).await();
                    confirmAlert(AlertType.WARNING_38);
                    alertService.ignore(null);
                    InsightBolusID insightBolusID = insightDbHelper.getInsightBolusID(serialNumber(), activeBolus.getBolusID(), dateUtil.now());
                    if (insightBolusID != null) {
                        result.enacted(true).success(true);
                    }
                }
            }
            result.success(true).comment(info.nightscout.core.ui.R.string.virtualpump_resultok);
        } catch (AppLayerErrorException e) {
            aapsLogger.info(LTag.PUMP, "Exception while canceling extended bolus: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (InsightException e) {
            aapsLogger.info(LTag.PUMP, "Exception while canceling extended bolus: " + e.getClass().getCanonicalName());
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (Exception e) {
            aapsLogger.error("Exception while canceling extended bolus", e);
            result.comment(ExceptionTranslator.getString(context, e));
        }
        return result;
    }

    private void confirmAlert(AlertType alertType) {
        try {
            long started = dateUtil.now();
            while (dateUtil.now() - started < 10000) {
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
            aapsLogger.info(LTag.PUMP, "Exception while confirming alert: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
        } catch (InsightException e) {
            aapsLogger.info(LTag.PUMP, "Exception while confirming alert: " + e.getClass().getCanonicalName());
        } catch (Exception e) {
            aapsLogger.error("Exception while confirming alert", e);
        }
    }

    @NonNull @Override
    public JSONObject getJSONStatus(@NonNull Profile profile, @NonNull String profileName, @NonNull String version) {
        long now = dateUtil.now();
        if (connectionService == null) return new JSONObject();
        if (dateUtil.now() - connectionService.getLastConnected() > (60 * 60 * 1000)) {
            return new JSONObject();
        }

        final JSONObject pump = new JSONObject();
        final JSONObject battery = new JSONObject();
        final JSONObject status = new JSONObject();
        final JSONObject extended = new JSONObject();
        try {
            status.put("timestamp", dateUtil.toISOString(connectionService.getLastConnected()));
            extended.put("Version", version);
            try {
                extended.put("ActiveProfile", profileFunction.getProfileName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            PumpSync.PumpState.TemporaryBasal tb = pumpSync.expectedPumpState().getTemporaryBasal();
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.convertedToAbsolute(now, profile));
                extended.put("TempBasalStart", dateUtil.dateAndTimeString(tb.getTimestamp()));
                extended.put("TempBasalRemaining", tb.getPlannedRemainingMinutes());
            }
            PumpSync.PumpState.ExtendedBolus eb = pumpSync.expectedPumpState().getExtendedBolus();
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.getRate());
                extended.put("ExtendedBolusStart", dateUtil.dateAndTimeString(eb.getTimestamp()));
                extended.put("ExtendedBolusRemaining", eb.getPlannedRemainingMinutes());
            }
            extended.put("BaseBasalRate", getBaseBasalRate());
            status.put("timestamp", dateUtil.toISOString(now));

            pump.put("extended", extended);
            if (statusLoaded) {
                status.put("status", operatingMode != OperatingMode.STARTED ? "suspended" : "normal");
                pump.put("status", status);
                battery.put("percent", batteryStatus.getBatteryAmount());
                pump.put("battery", battery);
                pump.put("reservoir", cartridgeStatus.getRemainingAmount());
            }
            pump.put("clock", dateUtil.toISOString(now));
        } catch (JSONException e) {
            aapsLogger.error("Unhandled exception", e);
        }
        return pump;
    }

    @NonNull @Override
    public ManufacturerType manufacturer() {
        return ManufacturerType.Roche;
    }

    @NonNull @Override
    public PumpType model() {
        return PumpType.ACCU_CHEK_INSIGHT;
    }

    @NonNull @Override
    public String serialNumber() {
        if (connectionService == null || alertService == null) return "Unknown";
        return connectionService.getPumpSystemIdentification().getSerialNumber();
    }

    public PumpEnactResult stopPump() {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        try {
            SetOperatingModeMessage operatingModeMessage = new SetOperatingModeMessage();
            operatingModeMessage.setOperatingMode(OperatingMode.STOPPED);
            connectionService.requestMessage(operatingModeMessage).await();
            result.success(true).enacted(true);
            fetchStatus();
            readHistory();
        } catch (AppLayerErrorException e) {
            aapsLogger.info(LTag.PUMP, "Exception while stopping pump: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (InsightException e) {
            aapsLogger.info(LTag.PUMP, "Exception while stopping pump: " + e.getClass().getCanonicalName());
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (Exception e) {
            aapsLogger.error("Exception while stopping pump", e);
            result.comment(ExceptionTranslator.getString(context, e));
        }
        return result;
    }

    public PumpEnactResult startPump() {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        try {
            SetOperatingModeMessage operatingModeMessage = new SetOperatingModeMessage();
            operatingModeMessage.setOperatingMode(OperatingMode.STARTED);
            connectionService.requestMessage(operatingModeMessage).await();
            result.success(true).enacted(true);
            fetchStatus();
            readHistory();
        } catch (AppLayerErrorException e) {
            aapsLogger.info(LTag.PUMP, "Exception while starting pump: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (InsightException e) {
            aapsLogger.info(LTag.PUMP, "Exception while starting pump: " + e.getClass().getCanonicalName());
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (Exception e) {
            aapsLogger.error("Exception while starting pump", e);
            result.comment(ExceptionTranslator.getString(context, e));
        }
        return result;
    }

    public PumpEnactResult setTBROverNotification(boolean enabled) {
        PumpEnactResult result = new PumpEnactResult(getInjector());
        boolean valueBefore = tbrOverNotificationBlock.isEnabled();
        tbrOverNotificationBlock.setEnabled(enabled);
        try {
            ParameterBlockUtil.writeConfigurationBlock(connectionService, tbrOverNotificationBlock);
            result.success(true).enacted(true);
        } catch (AppLayerErrorException e) {
            tbrOverNotificationBlock.setEnabled(valueBefore);
            aapsLogger.info(LTag.PUMP, "Exception while updating TBR notification block: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (InsightException e) {
            tbrOverNotificationBlock.setEnabled(valueBefore);
            aapsLogger.info(LTag.PUMP, "Exception while updating TBR notification block: " + e.getClass().getSimpleName());
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (Exception e) {
            tbrOverNotificationBlock.setEnabled(valueBefore);
            aapsLogger.error("Exception while updating TBR notification block", e);
            result.comment(ExceptionTranslator.getString(context, e));
        }
        return result;
    }

    @NonNull @Override
    public PumpDescription getPumpDescription() {
        return pumpDescription;
    }

    @NonNull @Override
    public String shortStatus(boolean veryShort) {
        StringBuilder ret = new StringBuilder();
        if (connectionService != null && connectionService.getLastConnected() != 0) {
            long agoMsec = dateUtil.now() - connectionService.getLastConnected();
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret.append(rh.gs(R.string.short_status_last_connected, agoMin)).append("\n");
        }
        if (activeTBR != null) {
            ret.append(rh.gs(R.string.short_status_tbr, activeTBR.getPercentage(),
                    activeTBR.getInitialDuration() - activeTBR.getRemainingDuration(), activeTBR.getInitialDuration())).append("\n");
        }
        if (activeBoluses != null) for (ActiveBolus activeBolus : activeBoluses) {
            if (activeBolus.getBolusType() == BolusType.STANDARD) continue;
            ret.append(rh.gs(activeBolus.getBolusType() == BolusType.MULTIWAVE ? R.string.short_status_multiwave : R.string.short_status_extended,
                    activeBolus.getRemainingAmount(), activeBolus.getInitialAmount(), activeBolus.getRemainingDuration())).append("\n");
        }
        if (!veryShort && totalDailyDose != null) {
            ret.append(rh.gs(R.string.short_status_tdd, totalDailyDose.getBolusAndBasal())).append("\n");
        }
        if (cartridgeStatus != null) {
            ret.append(rh.gs(R.string.short_status_reservoir, cartridgeStatus.getRemainingAmount())).append("\n");
        }
        if (batteryStatus != null) {
            ret.append(rh.gs(R.string.short_status_battery, batteryStatus.getBatteryAmount())).append("\n");
        }
        return ret.toString();
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return sp.getBoolean(R.string.key_insight_enable_tbr_emulation, false);
    }

    @NonNull @Override
    public PumpEnactResult loadTDDs() {
        return new PumpEnactResult(getInjector()).success(true);
    }

    private void readHistory() {
        try {
            PumpTime pumpTime = connectionService.requestMessage(new GetDateTimeMessage()).await().getPumpTime();
            String serial = serialNumber();
            timeOffset = Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis() - parseDate(pumpTime.getYear(),
                    pumpTime.getMonth(), pumpTime.getDay(), pumpTime.getHour(), pumpTime.getMinute(), pumpTime.getSecond());
            InsightHistoryOffset historyOffset = insightDbHelper.getInsightHistoryOffset(serial);
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
                    startMessage.setOffset(historyOffset.getOffset() + 1);
                    connectionService.requestMessage(startMessage).await();
                    while (true) {
                        List<HistoryEvent> newEvents = connectionService.requestMessage(new ReadHistoryEventsMessage()).await().getHistoryEvents();
                        if (newEvents.size() == 0) break;
                        historyEvents.addAll(newEvents);
                    }
                }
                Collections.sort(historyEvents);
                Collections.reverse(historyEvents);
                if (historyOffset != null) processHistoryEvents(serial, historyEvents);
                if (historyEvents.size() > 0) {
                    insightDbHelper.createOrUpdate(new InsightHistoryOffset(
                            serial,
                            historyEvents.get(0).getEventPosition())
                    );
                }
            } catch (AppLayerErrorException e) {
                aapsLogger.info(LTag.PUMP, "Exception while reading history: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            } catch (InsightException e) {
                aapsLogger.info(LTag.PUMP, "Exception while reading history: " + e.getClass().getSimpleName());
            } catch (Exception e) {
                aapsLogger.error("Exception while reading history", e);
            } finally {
                try {
                    connectionService.requestMessage(new StopReadingHistoryMessage()).await();
                } catch (Exception ignored) {
                }
            }
        } catch (AppLayerErrorException e) {
            aapsLogger.info(LTag.PUMP, "Exception while reading history: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
        } catch (InsightException e) {
            aapsLogger.info(LTag.PUMP, "Exception while reading history: " + e.getClass().getSimpleName());
        } catch (Exception e) {
            aapsLogger.error("Exception while reading history", e);
        }
        rxBus.send(new EventRefreshOverview("LocalInsightPlugin::readHistory", false));
    }

    private void processHistoryEvents(String serial, List<HistoryEvent> historyEvents) {
        List<TemporaryBasal> temporaryBasals = new ArrayList<>();
        List<InsightPumpID> pumpStartedEvents = new ArrayList<>();
        for (HistoryEvent historyEvent : historyEvents)
            if (!processHistoryEvent(serial, temporaryBasals, pumpStartedEvents, historyEvent))
                break;
        Collections.reverse(temporaryBasals);

        for (InsightPumpID pumpID : pumpStartedEvents) {
            InsightPumpID stoppedEvent = insightDbHelper.getPumpStoppedEvent(pumpID.getPumpSerial(), pumpID.getTimestamp());
            if (stoppedEvent != null && stoppedEvent.getEventType().equals(EventType.PumpStopped)) {             // Search if Stop event is after 15min of Pause
                InsightPumpID pauseEvent = insightDbHelper.getPumpStoppedEvent(pumpID.getPumpSerial(), stoppedEvent.getTimestamp() - T.Companion.mins(1).msecs());
                if (pauseEvent != null && pauseEvent.getEventType().equals(EventType.PumpPaused) && (stoppedEvent.getTimestamp() - pauseEvent.getTimestamp() < T.Companion.mins(16).msecs())) {
                    stoppedEvent = pauseEvent;
                    stoppedEvent.setEventType(EventType.PumpStopped);
                }
            }
            if (stoppedEvent == null || stoppedEvent.getEventType().equals(EventType.PumpPaused) || pumpID.getTimestamp() - stoppedEvent.getTimestamp() < 10000)
                continue;
            long tbrStart = stoppedEvent.getTimestamp() + 10000;
            TemporaryBasal temporaryBasal = new TemporaryBasal(
                    tbrStart,
                    pumpID.getTimestamp() - tbrStart,
                    0,
                    false,
                    PumpSync.TemporaryBasalType.NORMAL,
                    pumpID.getEventID(),
                    pumpID.getEventID());
            temporaryBasals.add(temporaryBasal);
        }
        temporaryBasals.sort((o1, o2) -> (int) (o1.getTimestamp() - o2.getTimestamp()));
        for (TemporaryBasal temporaryBasal : temporaryBasals) {
            if (temporaryBasal.getDuration() == 0L) {                    // for Stop TBR event duration = 0L
                pumpSync.syncStopTemporaryBasalWithPumpId(
                        temporaryBasal.getTimestamp(),
                        temporaryBasal.getPumpId(),
                        PumpType.ACCU_CHEK_INSIGHT,
                        serial);
            }
            if (temporaryBasal.getRate() != 100.0) {
                pumpSync.syncTemporaryBasalWithPumpId(
                        temporaryBasal.getTimestamp(),
                        temporaryBasal.getRate(),
                        temporaryBasal.getDuration(),
                        temporaryBasal.isAbsolute(),
                        temporaryBasal.getType(),
                        temporaryBasal.getPumpId(),
                        PumpType.ACCU_CHEK_INSIGHT,
                        serial);
            }
        }
    }

    private boolean processHistoryEvent(String serial, List<TemporaryBasal> temporaryBasals, List<InsightPumpID> pumpStartedEvents, HistoryEvent event) {
        if (event instanceof DefaultDateTimeSetEvent) return false;
        else if (event instanceof DateTimeChangedEvent)
            processDateTimeChangedEvent((DateTimeChangedEvent) event);
        else if (event instanceof CannulaFilledEvent)
            processCannulaFilledEvent(serial, (CannulaFilledEvent) event);
        else if (event instanceof TotalDailyDoseEvent)
            processTotalDailyDoseEvent(serial, (TotalDailyDoseEvent) event);
        else if (event instanceof TubeFilledEvent)
            processTubeFilledEvent(serial, (TubeFilledEvent) event);
        else if (event instanceof SniffingDoneEvent)
            processSniffingDoneEvent(serial, (SniffingDoneEvent) event);
        else if (event instanceof PowerUpEvent) processPowerUpEvent(serial, (PowerUpEvent) event);
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

    private void processCannulaFilledEvent(String serial, CannulaFilledEvent event) {
        if (!sp.getBoolean(R.string.key_insight_log_site_changes, false)) return;
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        if (event.getAmount() > 0.0)                 // Don't record event if amount is null => Fix Site Change with Insight v3 (event is always sent when Reservoir is changed)
            uploadCareportalEvent(timestamp, DetailedBolusInfo.EventType.CANNULA_CHANGE);
    }

    private void processTotalDailyDoseEvent(String serial, TotalDailyDoseEvent event) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date(0));
        calendar.set(Calendar.YEAR, event.getTotalYear());
        calendar.set(Calendar.MONTH, event.getTotalMonth() - 1);
        calendar.set(Calendar.DAY_OF_MONTH, event.getTotalDay());
        pumpSync.createOrUpdateTotalDailyDose(
                calendar.getTimeInMillis(),
                event.getBolusTotal(),
                event.getBasalTotal(),
                0.0, // will be calculated automatically
                event.getEventPosition(),
                PumpType.ACCU_CHEK_INSIGHT,
                serial);
    }

    private void processTubeFilledEvent(String serial, TubeFilledEvent event) {
        if (!sp.getBoolean(R.string.key_insight_log_tube_changes, false)) return;
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        if (event.getAmount() > 0.0)               // Don't record event if amount is null
            logNote(timestamp, rh.gs(R.string.tube_changed));
    }

    private void processSniffingDoneEvent(String serial, SniffingDoneEvent event) {
        if (!sp.getBoolean(R.string.key_insight_log_reservoir_changes, false)) return;
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        uploadCareportalEvent(timestamp, DetailedBolusInfo.EventType.INSULIN_CHANGE);
    }

    private void processPowerUpEvent(String serial, PowerUpEvent event) {
        if (!sp.getBoolean(R.string.key_insight_log_battery_changes, false)) return;
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        uploadCareportalEvent(timestamp, DetailedBolusInfo.EventType.PUMP_BATTERY_CHANGE);
    }

    private void processOperatingModeChangedEvent(String serial, List<InsightPumpID> pumpStartedEvents, OperatingModeChangedEvent event) {
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        InsightPumpID pumpID = new InsightPumpID(
                timestamp,
                EventType.None,
                serial,
                event.getEventPosition());
        switch (event.getNewValue()) {
            case STARTED:
                pumpID.setEventType(EventType.PumpStarted);
                pumpStartedEvents.add(pumpID);
                if (sp.getBoolean("insight_log_operating_mode_changes", false))
                    logNote(timestamp, rh.gs(R.string.pump_started));
                break;
            case STOPPED:
                pumpID.setEventType(EventType.PumpStopped);
                if (sp.getBoolean("insight_log_operating_mode_changes", false))
                    logNote(timestamp, rh.gs(R.string.pump_stopped));
                break;
            case PAUSED:
                pumpID.setEventType(EventType.PumpPaused);
                if (sp.getBoolean("insight_log_operating_mode_changes", false))
                    logNote(timestamp, rh.gs(info.nightscout.core.ui.R.string.pump_paused));
                break;
        }
        insightDbHelper.createOrUpdate(pumpID);
    }

    private void processStartOfTBREvent(String serial, List<TemporaryBasal> temporaryBasals, StartOfTBREvent event) {
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        insightDbHelper.createOrUpdate(new InsightPumpID(
                timestamp,
                EventType.StartOfTBR,
                serial,
                event.getEventPosition()));
        temporaryBasals.add(new TemporaryBasal(
                timestamp,
                T.Companion.mins(event.getDuration()).msecs(),
                event.getAmount(),
                false,
                PumpSync.TemporaryBasalType.NORMAL,
                event.getEventPosition(),
                event.getEventPosition()));
    }

    private void processEndOfTBREvent(String serial, List<TemporaryBasal> temporaryBasals, EndOfTBREvent event) {
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        insightDbHelper.createOrUpdate(new InsightPumpID(
                timestamp - 1500L,
                EventType.EndOfTBR,
                serial,
                event.getEventPosition()));

        temporaryBasals.add(new PumpSync.PumpState.TemporaryBasal(
                timestamp - 1500L,
                0L,
                100.0,
                false,
                PumpSync.TemporaryBasalType.NORMAL,
                event.getEventPosition(),
                event.getEventPosition()));
    }

    private void processBolusProgrammedEvent(String serial, BolusProgrammedEvent event) {
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        InsightBolusID bolusID = insightDbHelper.getInsightBolusID(serial, event.getBolusID(), timestamp);
        if (bolusID != null && bolusID.getEndID() != null) {
            bolusID.setStartID(event.getEventPosition());
            insightDbHelper.createOrUpdate(bolusID);
            return;
        }
        if (bolusID == null || bolusID.getStartID() != null) {                        //In rare edge cases two boluses can share the same ID
            insightDbHelper.createOrUpdate(new InsightBolusID(
                    timestamp,
                    serial,
                    event.getBolusID(),
                    event.getEventPosition(),
                    null
            ));
            bolusID = insightDbHelper.getInsightBolusID(serial, event.getBolusID(), timestamp);
        }
        bolusID.setStartID(event.getEventPosition());
        insightDbHelper.createOrUpdate(bolusID);

        if (event.getBolusType() == BolusType.STANDARD || event.getBolusType() == BolusType.MULTIWAVE) {
            pumpSync.syncBolusWithPumpId(
                    bolusID.getTimestamp(),
                    event.getImmediateAmount(),
                    null,
                    bolusID.getId(),
                    PumpType.ACCU_CHEK_INSIGHT,
                    serial);
        }
        if ((event.getBolusType() == BolusType.EXTENDED || event.getBolusType() == BolusType.MULTIWAVE)) {
            if (profileFunction.getProfile(bolusID.getTimestamp()) != null)
                pumpSync.syncExtendedBolusWithPumpId(
                        bolusID.getTimestamp(),
                        event.getExtendedAmount(),
                        T.Companion.mins(event.getDuration()).msecs(),
                        isFakingTempsByExtendedBoluses(),
                        bolusID.getId(),
                        PumpType.ACCU_CHEK_INSIGHT,
                        serial);
        }
    }

    private void processBolusDeliveredEvent(String serial, BolusDeliveredEvent event) {
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        long startTimestamp = parseRelativeDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(), event.getEventHour(),
                event.getEventMinute(), event.getEventSecond(), event.getStartHour(), event.getStartMinute(), event.getStartSecond()) + timeOffset;
        InsightBolusID bolusID = insightDbHelper.getInsightBolusID(serial, event.getBolusID(), timestamp);
        if (bolusID == null || bolusID.getEndID() != null) {                        // TODO() Check if test EndID is necessary
            bolusID = new InsightBolusID(
                    startTimestamp,
                    serial,
                    event.getBolusID(),
                    bolusID == null ? event.getEventPosition() : bolusID.getStartID(),
                    event.getEventPosition());
        }
        bolusID.setEndID(event.getEventPosition());
        insightDbHelper.createOrUpdate(bolusID);
        bolusID = insightDbHelper.getInsightBolusID(serial, event.getBolusID(), startTimestamp); // Line added to get id
        if (event.getBolusType() == BolusType.STANDARD || event.getBolusType() == BolusType.MULTIWAVE) {
            pumpSync.syncBolusWithPumpId(
                    bolusID.getTimestamp(),
                    event.getImmediateAmount(),
                    null,
                    bolusID.getId(),
                    PumpType.ACCU_CHEK_INSIGHT,
                    serial);
            lastBolusTimestamp = bolusID.getTimestamp();
            sp.putLong(R.string.key_insight_last_bolus_timestamp, lastBolusTimestamp);
            lastBolusAmount = event.getImmediateAmount();
            sp.putDouble(R.string.key_insight_last_bolus_amount, lastBolusAmount);
        }
        if (event.getBolusType() == BolusType.EXTENDED || event.getBolusType() == BolusType.MULTIWAVE) {
            if (event.getDuration() > 0 && profileFunction.getProfile(bolusID.getTimestamp()) != null)
                pumpSync.syncExtendedBolusWithPumpId(
                        bolusID.getTimestamp(),
                        event.getExtendedAmount(),
                        timestamp - startTimestamp,
                        isFakingTempsByExtendedBoluses(),
                        bolusID.getId(),
                        PumpType.ACCU_CHEK_INSIGHT,
                        serial);
        }
    }

    private void processOccurrenceOfAlertEvent(OccurrenceOfAlertEvent event) {
        if (!sp.getBoolean(R.string.key_insight_log_alerts, false)) return;
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
            logNote(timestamp, rh.gs(R.string.insight_alert_formatter, rh.gs(code), rh.gs(title)));
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
        pumpSync.insertTherapyEventIfNewWithTimestamp(date, DetailedBolusInfo.EventType.NOTE, note, null, PumpType.ACCU_CHEK_INSIGHT, serialNumber());
    }

    private long parseRelativeDate(int year, int month, int day, int hour, int minute, int second, int relativeHour, int relativeMinute, int relativeSecond) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, month - 1);
        calendar.set(Calendar.DAY_OF_MONTH, day);
        calendar.set(Calendar.HOUR_OF_DAY, relativeHour);
        calendar.set(Calendar.MINUTE, relativeMinute);
        calendar.set(Calendar.SECOND, relativeSecond);
        long dayOffset =
                relativeHour * 60 * 60 + relativeMinute * 60 + relativeSecond >= hour * 60 * 60 + minute * 60 + second ? T.Companion.days(1).msecs() : 0L;
        return calendar.getTimeInMillis() - dayOffset;
    }

    private void uploadCareportalEvent(long date, DetailedBolusInfo.EventType event) {
        pumpSync.insertTherapyEventIfNewWithTimestamp(date, event, null, null, PumpType.ACCU_CHEK_INSIGHT, serialNumber());
    }

    @NonNull @Override
    public Constraint<Integer> applyBasalPercentConstraints(Constraint<Integer> percentRate, @NonNull Profile profile) {
        percentRate.setIfGreater(getAapsLogger(), 0, rh.gs(info.nightscout.core.ui.R.string.limitingpercentrate, 0, rh.gs(info.nightscout.core.ui.R.string.itmustbepositivevalue)), this);
        percentRate.setIfSmaller(getAapsLogger(), getPumpDescription().getMaxTempPercent(), rh.gs(info.nightscout.core.ui.R.string.limitingpercentrate, getPumpDescription().getMaxTempPercent(), rh.gs(info.nightscout.core.ui.R.string.pumplimit)), this);
        return percentRate;
    }

    @NonNull @Override
    public Constraint<Double> applyBolusConstraints(@NonNull Constraint<Double> insulin) {
        if (!limitsFetched) return insulin;
        insulin.setIfSmaller(getAapsLogger(), maximumBolusAmount, rh.gs(info.nightscout.core.ui.R.string.limitingbolus, maximumBolusAmount, rh.gs(info.nightscout.core.ui.R.string.pumplimit)), this);
        if (insulin.value() < minimumBolusAmount) {

            //TODO: Add function to Constraints or use different approach
            // This only works if the interface of the InsightPlugin is called last.
            // If not, another constraint could theoretically set the value between 0 and minimumBolusAmount

            insulin.set(getAapsLogger(), 0d, rh.gs(info.nightscout.core.ui.R.string.limitingbolus, minimumBolusAmount, rh.gs(info.nightscout.core.ui.R.string.pumplimit)), this);
        }
        return insulin;
    }

    @NonNull @Override
    public Constraint<Double> applyExtendedBolusConstraints(@NonNull Constraint<Double> insulin) {
        return applyBolusConstraints(insulin);
    }

    @Override
    public void onStateChanged(InsightState state) {
        if (state == InsightState.CONNECTED) {
            statusLoaded = false;
            rxBus.send(new EventDismissNotification(Notification.INSIGHT_TIMEOUT_DURING_HANDSHAKE));
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
            rxBus.send(new EventRefreshOverview("LocalInsightPlugin::onStateChanged", false));
        }
        rxBus.send(new EventLocalInsightUpdateGUI());
    }

    @Override
    public void onPumpPaired() {
        commandQueue.readStatus(rh.gs(info.nightscout.core.ui.R.string.pump_paired), null);
    }

    @Override
    public void onTimeoutDuringHandshake() {
        Notification notification = new Notification(Notification.INSIGHT_TIMEOUT_DURING_HANDSHAKE, rh.gs(R.string.timeout_during_handshake), Notification.URGENT);
        rxBus.send(new EventNewNotification(notification));
    }

    @Override
    public boolean canHandleDST() {
        return true;
    }

    @Override public void clearAllTables() {
        insightDatabase.clearAllTables();
    }
}
