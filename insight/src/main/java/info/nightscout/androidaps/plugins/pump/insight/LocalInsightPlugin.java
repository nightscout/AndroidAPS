package info.nightscout.androidaps.plugins.pump.insight;

import static info.nightscout.androidaps.extensions.PumpStateExtensionKt.convertedToAbsolute;
import static info.nightscout.androidaps.extensions.PumpStateExtensionKt.getPlannedRemainingMinutes;

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
import info.nightscout.androidaps.data.DetailedBolusInfo;
import info.nightscout.androidaps.data.Profile;
import info.nightscout.androidaps.data.PumpEnactResult;
import info.nightscout.androidaps.events.EventInitializationChanged;
import info.nightscout.androidaps.events.EventRefreshOverview;
import info.nightscout.androidaps.extensions.PumpStateExtensionKt;
import info.nightscout.androidaps.insight.R;
import info.nightscout.androidaps.interfaces.CommandQueueProvider;
import info.nightscout.androidaps.interfaces.Config;
import info.nightscout.androidaps.interfaces.Constraint;
import info.nightscout.androidaps.interfaces.Constraints;
import info.nightscout.androidaps.interfaces.PluginDescription;
import info.nightscout.androidaps.interfaces.PluginType;
import info.nightscout.androidaps.interfaces.ProfileFunction;
import info.nightscout.androidaps.interfaces.Pump;
import info.nightscout.androidaps.interfaces.PumpDescription;
import info.nightscout.androidaps.interfaces.PumpPluginBase;
import info.nightscout.androidaps.interfaces.PumpSync;
import info.nightscout.androidaps.interfaces.PumpSync.PumpState.TemporaryBasal;
import info.nightscout.androidaps.logging.AAPSLogger;
import info.nightscout.androidaps.logging.LTag;
import info.nightscout.androidaps.plugins.bus.RxBusWrapper;
import info.nightscout.androidaps.plugins.common.ManufacturerType;
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
import info.nightscout.androidaps.utils.DateUtil;
import info.nightscout.androidaps.utils.T;
import info.nightscout.androidaps.utils.resources.ResourceHelper;
import info.nightscout.androidaps.utils.sharedPreferences.SP;

@Singleton
public class LocalInsightPlugin extends PumpPluginBase implements Pump, Constraints, InsightConnectionService.StateCallback {

    private final AAPSLogger aapsLogger;
    private final RxBusWrapper rxBus;
    private final ResourceHelper resourceHelper;
    private final SP sp;
    private final CommandQueueProvider commandQueue;
    private final ProfileFunction profileFunction;
    private final Context context;
    private final DateUtil dateUtil;
    private final PumpSync pumpSync;

    public static final String ALERT_CHANNEL_ID = "AndroidAPS-InsightAlert";

    private final PumpDescription pumpDescription;
    private InsightAlertService alertService;
    private InsightConnectionService connectionService;
    private long timeOffset;
    private long lastStartEvent;
    private TemporaryBasal lastTbr;

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

    @Inject
    public LocalInsightPlugin(
            HasAndroidInjector injector,
            AAPSLogger aapsLogger,
            RxBusWrapper rxBus,
            ResourceHelper resourceHelper,
            SP sp,
            CommandQueueProvider commandQueue,
            ProfileFunction profileFunction,
            Context context,
            Config config,
            DateUtil dateUtil,
            PumpSync pumpSync
    ) {
        super(new PluginDescription()
                        .pluginIcon(R.drawable.ic_insight_128)
                        .pluginName(R.string.insight_local)
                        .shortName(R.string.insightpump_shortname)
                        .mainType(PluginType.PUMP)
                        .description(R.string.description_pump_insight_local)
                        .fragmentClass(LocalInsightFragment.class.getName())
                        .preferencesId(config.getAPS() ? R.xml.pref_insight_local_full : R.xml.pref_insight_local_pumpcontrol),
                injector, aapsLogger, resourceHelper, commandQueue

        );
        this.aapsLogger = aapsLogger;
        this.rxBus = rxBus;
        this.resourceHelper = resourceHelper;
        this.sp = sp;
        this.commandQueue = commandQueue;
        this.profileFunction = profileFunction;
        this.context = context;
        this.dateUtil = dateUtil;
        this.pumpSync = pumpSync;

        pumpDescription = new PumpDescription();
        pumpDescription.setPumpDescription(PumpType.ACCU_CHEK_INSIGHT_BLUETOOTH);
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
        NotificationChannel channel = new NotificationChannel(ALERT_CHANNEL_ID, resourceHelper.gs(R.string.insight_alert_notification_channel), NotificationManager.IMPORTANCE_HIGH);
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
            Notification notification = new Notification(Notification.INSIGHT_DATE_TIME_UPDATED, resourceHelper.gs(R.string.pump_time_updated), Notification.INFO, 60);
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
            rxBus.send(new EventDismissNotification(Notification.FAILED_UPDATE_PROFILE));
            Notification notification = new Notification(Notification.PROFILE_SET_OK, resourceHelper.gs(R.string.profile_set_ok), Notification.INFO, 60);
            rxBus.send(new EventNewNotification(notification));
            result.success(true)
                    .enacted(true)
                    .comment(R.string.virtualpump_resultok);
            this.profileBlocks = profileBlocks;
            try {
                fetchStatus();
            } catch (Exception ignored) {
            }
        } catch (AppLayerErrorException e) {
            aapsLogger.info(LTag.PUMP, "Exception while setting profile: " + e.getClass().getCanonicalName() + " (" + e.getErrorCode() + ")");
            Notification notification = new Notification(Notification.FAILED_UPDATE_PROFILE, resourceHelper.gs(R.string.failedupdatebasalprofile), Notification.URGENT);
            rxBus.send(new EventNewNotification(notification));
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (InsightException e) {
            aapsLogger.info(LTag.PUMP, "Exception while setting profile: " + e.getClass().getCanonicalName());
            Notification notification = new Notification(Notification.FAILED_UPDATE_PROFILE, resourceHelper.gs(R.string.failedupdatebasalprofile), Notification.URGENT);
            rxBus.send(new EventNewNotification(notification));
            result.comment(ExceptionTranslator.getString(context, e));
        } catch (Exception e) {
            aapsLogger.error("Exception while setting profile", e);
            Notification notification = new Notification(Notification.FAILED_UPDATE_PROFILE, resourceHelper.gs(R.string.failedupdatebasalprofile), Notification.URGENT);
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
            if (profileBlock.getDuration() * 60 != (nextValue != null ? nextValue.timeAsSeconds : 24 * 60 * 60) - basalValue.timeAsSeconds)
                return false;
            if (Math.abs(profileBlock.getBasalAmount() - basalValue.value) > (basalValue.value > 5 ? 0.051 : 0.0051))
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
            aapsLogger.debug("XXXX Illegal argument : Insulin: " + detailedBolusInfo.insulin + " Carbs: " + detailedBolusInfo.carbs);
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
                    bolusMessage.setVibration(sp.getBoolean(detailedBolusInfo.getBolusType() == DetailedBolusInfo.BolusType.SMB ? R.string.key_disable_vibration_auto : R.string.key_disable_vibration, false));
                    bolusID = connectionService.requestMessage(bolusMessage).await().getBolusId();
                    bolusCancelled = false;
                }
                result.success(true).enacted(true);
                EventOverviewBolusProgress.Treatment t = new EventOverviewBolusProgress.Treatment(0, 0, detailedBolusInfo.getBolusType() == DetailedBolusInfo.BolusType.SMB);
                final EventOverviewBolusProgress bolusingEvent = EventOverviewBolusProgress.INSTANCE;
                bolusingEvent.setT(t);
                bolusingEvent.setStatus(resourceHelper.gs(R.string.insight_delivered, 0d, insulin));
                bolusingEvent.setPercent(0);
                rxBus.send(bolusingEvent);
                int trials = 0;
                long timestamp = dateUtil.now();
                pumpSync.syncBolusWithPumpId(
                        timestamp,
                        detailedBolusInfo.insulin,
                        detailedBolusInfo.getBolusType(),
                        bolusID,
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
                        bolusingEvent.setStatus(resourceHelper.gs(R.string.insight_delivered, activeBolus.getInitialAmount() - activeBolus.getRemainingAmount(), activeBolus.getInitialAmount()));
                        if (percentBefore != bolusingEvent.getPercent())
                            rxBus.send(bolusingEvent);
                    } else {
                        synchronized ($bolusLock) {
                            if (bolusCancelled || trials == -1 || trials++ >= 5) {
                                if (!bolusCancelled) {
                                    bolusingEvent.setStatus(resourceHelper.gs(R.string.insight_delivered, insulin, insulin));
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
                                sp.getBoolean(R.string.key_disable_vibration_auto, false));
                        if (ebResult.getSuccess()) {
                            result.success(true)
                                    .enacted(true)
                                    .isPercent(false)
                                    .absolute(absoluteRate)
                                    .duration(durationInMinutes)
                                    .comment(R.string.virtualpump_resultok);
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
                    .comment(R.string.virtualpump_resultok);
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
            result = setExtendedBolusOnly(insulin, durationInMinutes, sp.getBoolean(R.string.key_disable_vibration, false));
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
            connectionService.requestMessage(bolusMessage).await().getBolusId();
            result.success(true).enacted(true).comment(R.string.virtualpump_resultok);
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
            result.comment(R.string.virtualpump_resultok);
        } catch (NoActiveTBRToCanceLException e) {
            result.success(true);
            result.comment(R.string.virtualpump_resultok);
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
                    result.enacted(true).success(true);
                }
            }
            result.success(true).comment(R.string.virtualpump_resultok);
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
            TemporaryBasal tb = pumpSync.expectedPumpState().getTemporaryBasal();
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", convertedToAbsolute(tb, now, profile));
                extended.put("TempBasalStart", dateUtil.dateAndTimeString(tb.getTimestamp()));
                extended.put("TempBasalRemaining", getPlannedRemainingMinutes(tb));
            }
            PumpSync.PumpState.ExtendedBolus eb = pumpSync.expectedPumpState().getExtendedBolus();
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.getRate());
                extended.put("ExtendedBolusStart", dateUtil.dateAndTimeString(eb.getTimestamp()));
                extended.put("ExtendedBolusRemaining", getPlannedRemainingMinutes(eb));
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
        return PumpType.ACCU_CHEK_INSIGHT_BLUETOOTH;
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
        if (connectionService.getLastConnected() != 0) {
            long agoMsec = dateUtil.now() - connectionService.getLastConnected();
            int agoMin = (int) (agoMsec / 60d / 1000d);
            ret.append(resourceHelper.gs(R.string.short_status_last_connected, agoMin)).append("\n");
        }
        if (activeTBR != null) {
            ret.append(resourceHelper.gs(R.string.short_status_tbr, activeTBR.getPercentage(),
                    activeTBR.getInitialDuration() - activeTBR.getRemainingDuration(), activeTBR.getInitialDuration())).append("\n");
        }
        if (activeBoluses != null) for (ActiveBolus activeBolus : activeBoluses) {
            if (activeBolus.getBolusType() == BolusType.STANDARD) continue;
            ret.append(resourceHelper.gs(activeBolus.getBolusType() == BolusType.MULTIWAVE ? R.string.short_status_multiwave : R.string.short_status_extended,
                    activeBolus.getRemainingAmount(), activeBolus.getInitialAmount(), activeBolus.getRemainingDuration())).append("\n");
        }
        if (!veryShort && totalDailyDose != null) {
            ret.append(resourceHelper.gs(R.string.short_status_tdd, totalDailyDose.getBolusAndBasal())).append("\n");
        }
        if (cartridgeStatus != null) {
            ret.append(resourceHelper.gs(R.string.short_status_reservoir, cartridgeStatus.getRemainingAmount())).append("\n");
        }
        if (batteryStatus != null) {
            ret.append(resourceHelper.gs(R.string.short_status_battery, batteryStatus.getBatteryAmount())).append("\n");
        }
        return ret.toString();
    }

    @Override
    public boolean isFakingTempsByExtendedBoluses() {
        return sp.getBoolean("insight_enable_tbr_emulation", false);
    }

    @NonNull @Override
    public PumpEnactResult loadTDDs() {
        return new PumpEnactResult(getInjector()).success(true);
    }

    private void readHistory() {
        try {
            PumpTime pumpTime = connectionService.requestMessage(new GetDateTimeMessage()).await().getPumpTime();
            timeOffset = Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis() - parseDate(pumpTime.getYear(),
                    pumpTime.getMonth(), pumpTime.getDay(), pumpTime.getHour(), pumpTime.getMinute(), pumpTime.getSecond());
            aapsLogger.debug("XXXX Start History Ref Timestamp Now: " + dateUtil.dateAndTimeAndSecondsString(dateUtil.now()));
            try {
                StartReadingHistoryMessage startMessage = new StartReadingHistoryMessage();
                startMessage.setDirection(HistoryReadingDirection.BACKWARD);                    //event must be read in Backward direction
                startMessage.setOffset(0xFFFFFFFF);
                connectionService.requestMessage(startMessage).await();
                List<HistoryEvent> historyEvents = connectionService.requestMessage(new ReadHistoryEventsMessage()).await().getHistoryEvents();
                Collections.sort(historyEvents);
                if (historyEvents.size() > 0) processHistoryEvents(serialNumber(), historyEvents);
                aapsLogger.debug("XXXX End history HistorySize: " + historyEvents.size());
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
        new Handler(Looper.getMainLooper()).post(() -> rxBus.send(new EventRefreshOverview("LocalInsightPlugin::readHistory", false)));
    }

    private void processHistoryEvents(String serial, List<HistoryEvent> historyEvents) throws Exception {
        List<TemporaryBasal> temporaryBasals = new ArrayList<>();
        TemporaryBasal currentTbr = pumpSync.expectedPumpState().getTemporaryBasal();
        long currentTimestamp = currentTbr != null ? currentTbr.getTimestamp() : dateUtil.now();
        for (HistoryEvent historyEvent : historyEvents)
            if (!processHistoryEvent(serial, temporaryBasals, historyEvent))
                break;
        temporaryBasals.sort((o1, o2) -> (int) (o1.getTimestamp() - o2.getTimestamp()));
        aapsLogger.debug("XXXX tbr size :" + temporaryBasals.size());
        if (lastTbr == null) lastTbr = pumpSync.expectedPumpState().getTemporaryBasal();
        if (lastTbr == null) lastTbr = new TemporaryBasal(dateUtil.now() - 10000L, 0, 100, false, PumpSync.TemporaryBasalType.NORMAL, 0, 0L);
        for (TemporaryBasal temporaryBasal : temporaryBasals) {
            if (temporaryBasal.getRate() == 100.0 && temporaryBasal.getTimestamp() > lastTbr.getTimestamp()) {                    // for Stop TBR event rate = 100.0
                if (temporaryBasal.getTimestamp() > currentTimestamp) {
                    pumpSync.syncStopTemporaryBasalWithPumpId(
                            temporaryBasal.getTimestamp(),
                            temporaryBasal.getPumpId(),
                            PumpType.ACCU_CHEK_INSIGHT,
                            serial);
                }
                aapsLogger.debug("XXXX Sync Stop " + temporaryBasal.getTimestamp() + " date: " + dateUtil.dateAndTimeAndSecondsString(temporaryBasal.getTimestamp()) + " pumpId: " + temporaryBasal.getPumpId());
                lastTbr = temporaryBasal;
            }
            if (temporaryBasal.getRate() != 100.0 &&  temporaryBasal.getTimestamp() > lastTbr.getTimestamp()){
                Boolean resultdb = pumpSync.syncTemporaryBasalWithPumpId(
                        temporaryBasal.getTimestamp(),
                        temporaryBasal.getRate(),
                        temporaryBasal.getDuration(),
                        temporaryBasal.isAbsolute(),
                        temporaryBasal.getType(),
                        temporaryBasal.getPumpId(),
                        PumpType.ACCU_CHEK_INSIGHT,
                        serial);
                aapsLogger.debug("XXXX sync starttbr: " + temporaryBasal.getTimestamp() + " date: " + dateUtil.dateAndTimeAndSecondsString(temporaryBasal.getTimestamp()) + " duration: " + temporaryBasal.getDuration() / 60000 + " pumpId: " + temporaryBasal.getPumpId() + " rate: " + temporaryBasal.getRate() + " resultdb: " + resultdb);
                lastTbr = temporaryBasal;
            }
        }
        // TODO remove log once Insight Driver Ok
        TemporaryBasal activeDbTBR = pumpSync.expectedPumpState().getTemporaryBasal();
        activeTBR = connectionService.requestMessage(new GetActiveTBRMessage()).await().getActiveTBR();
        if (activeTBR != null || activeDbTBR != null) {
            if (activeDbTBR == null)
                aapsLogger.debug("XXXX INCONCISTENCY TBR running only in Pump: " + activeTBR.getPercentage() + "% started " + (activeTBR.getInitialDuration() - activeTBR.getRemainingDuration()) + "min ago " + activeTBR.getPercentage() + "% in pump (" + (activeTBR.getInitialDuration() - activeTBR.getRemainingDuration()) + "min ago Duration (min):" + activeTBR.getInitialDuration());
            else if (activeTBR == null)
                aapsLogger.debug("XXXX INCONCISTENCY TBR running only in AAPS: " + activeDbTBR.getRate() + "% started " + (dateUtil.now() - activeDbTBR.getTimestamp())/1000 + "s ago Duration (min):" + activeDbTBR.getDuration()/60000);
            else if (activeTBR.getPercentage() != activeDbTBR.getRate())
                aapsLogger.debug("XXXX INCONCISTENCY TBR Not the same %: " + activeDbTBR.getRate() + "% in AAPS ("+ (dateUtil.now() - activeDbTBR.getTimestamp())/1000 + "s ago) " + activeTBR.getPercentage() + "% in pump (" + (activeTBR.getInitialDuration() - activeTBR.getRemainingDuration()) + "min ago Duration (min):" + activeTBR.getInitialDuration());
        }
        checkAndResolveTbrMismatch();           // on start use to resynchro AAPS with current TBR in pump
    }

    private boolean processHistoryEvent(String serial, List<TemporaryBasal> temporaryBasals, HistoryEvent event) {
        if (event instanceof DefaultDateTimeSetEvent) return false;
        else if (event instanceof DateTimeChangedEvent)
            processDateTimeChangedEvent((DateTimeChangedEvent) event);
        else if (event instanceof CannulaFilledEvent)
            processCannulaFilledEvent(serial, (CannulaFilledEvent) event);
        else if (event instanceof TotalDailyDoseEvent)
            processTotalDailyDoseEvent(serial, (TotalDailyDoseEvent) event);
        else if (event instanceof TubeFilledEvent) processTubeFilledEvent(serial, (TubeFilledEvent) event);
        else if (event instanceof SniffingDoneEvent)
            processSniffingDoneEvent((SniffingDoneEvent) event);
        else if (event instanceof PowerUpEvent) processPowerUpEvent(serial, (PowerUpEvent) event);
        else if (event instanceof OperatingModeChangedEvent)
            processOperatingModeChangedEvent(temporaryBasals, (OperatingModeChangedEvent) event);
        else if (event instanceof StartOfTBREvent)
            processStartOfTBREvent(temporaryBasals, (StartOfTBREvent) event);
        else if (event instanceof EndOfTBREvent)
            processEndOfTBREvent(temporaryBasals, (EndOfTBREvent) event);
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
        if (!sp.getBoolean("insight_log_site_changes", false)) return;
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        uploadCareportalEvent(timestamp, DetailedBolusInfo.EventType.CANNULA_CHANGE);
        pumpSync.insertTherapyEventIfNewWithTimestamp(
                timestamp,
                DetailedBolusInfo.EventType.CANNULA_CHANGE,
                "",
                null,
                PumpType.ACCU_CHEK_INSIGHT,
                serial);
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
                null,
                PumpType.ACCU_CHEK_INSIGHT,
                serial);
    }

    private void processTubeFilledEvent(String serial, TubeFilledEvent event) {
        if (!sp.getBoolean("insight_log_tube_changes", false)) return;
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        logNote(timestamp, resourceHelper.gs(R.string.tube_changed));
        pumpSync.insertTherapyEventIfNewWithTimestamp(
                timestamp,
                DetailedBolusInfo.EventType.INSULIN_CHANGE,
                "",
                null,
                PumpType.ACCU_CHEK_INSIGHT,
                serial);
    }

    private void processSniffingDoneEvent(SniffingDoneEvent event) {
        if (!sp.getBoolean("insight_log_reservoir_changes", false)) return;
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        uploadCareportalEvent(timestamp, DetailedBolusInfo.EventType.INSULIN_CHANGE);
    }

    private void processPowerUpEvent(String serial, PowerUpEvent event) {
        if (!sp.getBoolean("insight_log_battery_changes", false)) return;
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        uploadCareportalEvent(timestamp, DetailedBolusInfo.EventType.PUMP_BATTERY_CHANGE);
        pumpSync.insertTherapyEventIfNewWithTimestamp(
                timestamp,
                DetailedBolusInfo.EventType.PUMP_BATTERY_CHANGE,
                "",
                null,
                PumpType.ACCU_CHEK_INSIGHT,
                serial);
    }

    private void processOperatingModeChangedEvent(List<TemporaryBasal> temporaryBasals, OperatingModeChangedEvent event) {
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        switch (event.getNewValue()) {
            case STARTED:
                lastStartEvent = timestamp + 10000L;                        // I don't now the reason of 10s offset, so I keep it as it was in original Insight Driver
                if (sp.getBoolean("insight_log_operating_mode_changes", false))
                    logNote(timestamp, resourceHelper.gs(R.string.pump_started));
                aapsLogger.debug("XXXX START Event TimeStamp: " + lastStartEvent + " HMS: " + dateUtil.dateAndTimeAndSecondsString(lastStartEvent));
                break;
            case STOPPED:
                TemporaryBasal temporaryBasal = new TemporaryBasal(
                        timestamp,
                        lastStartEvent - timestamp,                 // event are in Backward order
                        0.0,
                        false,
                        PumpSync.TemporaryBasalType.NORMAL,
                        timestamp,
                        timestamp);
                temporaryBasals.add(temporaryBasal);
                aapsLogger.debug("XXXX STOP: " + timestamp + " HMS: " + dateUtil.dateAndTimeAndSecondsString(timestamp) + " ZeroTemp Duration (min): " + (lastStartEvent - timestamp)/60000);
                if (sp.getBoolean("insight_log_operating_mode_changes", false))
                    logNote(timestamp, resourceHelper.gs(R.string.pump_stopped));
                break;
            case PAUSED:
                if (sp.getBoolean("insight_log_operating_mode_changes", false))
                    logNote(timestamp, resourceHelper.gs(R.string.pump_paused));
                break;
        }
    }

    private void processStartOfTBREvent(List<PumpSync.PumpState.TemporaryBasal> temporaryBasals, StartOfTBREvent event) {
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;

        aapsLogger.debug("XXXX event StartTbr history rec Timestamp: " + timestamp + " date: " + event.getEventYear() + "/" + event.getEventMonth() + "/" + event.getEventDay() + " " + event.getEventHour() + ":" + event.getEventMinute() + ":" + event.getEventSecond() + " Rate: " + event.getAmount() + " Duration(min): " + event.getDuration());
        TemporaryBasal temporaryBasal = new TemporaryBasal(
                timestamp,
                T.mins(event.getDuration()).msecs(),
                event.getAmount(),
                false,
                PumpSync.TemporaryBasalType.NORMAL,
                timestamp,
                timestamp);
        temporaryBasals.add(temporaryBasal);
    }

    private void processEndOfTBREvent(List<PumpSync.PumpState.TemporaryBasal> temporaryBasals, EndOfTBREvent event) {
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        aapsLogger.debug("XXXX event StopTbr history rec Timestamp: " + timestamp + " date: " + event.getEventYear() + "/" + event.getEventMonth() + "/" + event.getEventDay() + " " + event.getEventHour() + ":" + event.getEventMinute() + ":" + event.getEventSecond());
            PumpSync.PumpState.TemporaryBasal temporaryBasal = new PumpSync.PumpState.TemporaryBasal(
                    timestamp - 1500L,
                    0L,
                    100.0,
                    false,
                    PumpSync.TemporaryBasalType.NORMAL,
                    timestamp - 1500L,
                    timestamp - 1500L);
            temporaryBasals.add(temporaryBasal);
    }

    /**
     * Checks the main screen to determine if TBR on pump matches app state.
     */
    private void checkAndResolveTbrMismatch() {
        // compare with: info.nightscout.androidaps.plugins.PumpDanaR.comm.MsgStatusTempBasal.updateTempBasalInDB()
        long now = dateUtil.now();
        //activeBoluses;
        PumpSync.PumpState.TemporaryBasal aapsTbr = pumpSync.expectedPumpState().getTemporaryBasal();
        if (aapsTbr == null && activeTBR != null && activeTBR.getRemainingDuration() > 1) {
            aapsLogger.debug(LTag.PUMP, "XXXX Creating temp basal from pump TBR estimated timestamp: " + now + " duration: " + activeTBR.getInitialDuration() + " rate: " + activeTBR.getPercentage());
            pumpSync.syncTemporaryBasalWithPumpId(
                    now,
                    activeTBR.getPercentage(),
                    activeTBR.getRemainingDuration(),
                    false,
                    PumpSync.TemporaryBasalType.NORMAL,
                    now,
                    PumpType.ACCU_CHEK_INSIGHT,
                    serialNumber()
            );
        } else if (aapsTbr != null && PumpStateExtensionKt.getPlannedRemainingMinutes(aapsTbr) > 1 && activeTBR == null) {
            aapsLogger.debug(LTag.PUMP, "XXXX Ending AAPS-TBR since pump has no TBR active");
            pumpSync.syncStopTemporaryBasalWithPumpId(
                    now,
                    now,
                    PumpType.ACCU_CHEK_INSIGHT,
                    serialNumber());
        } else if (aapsTbr != null && activeTBR != null
                && (aapsTbr.getRate() != activeTBR.getPercentage() ||
                Math.abs(PumpStateExtensionKt.getPlannedRemainingMinutes(aapsTbr) - activeTBR.getRemainingDuration()) > 1)) {
            aapsLogger.debug(LTag.PUMP, "XXXX AAPSs and pump-TBR differ; ending AAPS-TBR and creating new TBR based on pump TBR");
            // create TBR end record a second ago
            pumpSync.syncStopTemporaryBasalWithPumpId(
                    now - 1500,
                    now-1500,
                    PumpType.ACCU_CHEK_INSIGHT,
                    serialNumber()
            );
            // Create TBR start record, starting now
            pumpSync.syncTemporaryBasalWithPumpId(
                    now,
                    activeTBR.getPercentage(),
                    T.mins(activeTBR.getRemainingDuration()).msecs(),
                    false,
                    PumpSync.TemporaryBasalType.NORMAL,
                    now,
                    PumpType.ACCU_CHEK_INSIGHT,
                    serialNumber()
            );
        }
    }

    private void processBolusProgrammedEvent(String serial, BolusProgrammedEvent event) {
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        if (event.getBolusType() == BolusType.STANDARD || event.getBolusType() == BolusType.MULTIWAVE) {
            pumpSync.syncBolusWithPumpId(
                    timestamp,
                    event.getImmediateAmount(),
                    null,
                    event.getBolusID(),
                    PumpType.ACCU_CHEK_INSIGHT,
                    serial);
        }
        if (event.getBolusType() == BolusType.EXTENDED || event.getBolusType() == BolusType.MULTIWAVE) {
            if (profileFunction.getProfile(timestamp) != null)
                pumpSync.syncExtendedBolusWithPumpId(
                        timestamp,
                        event.getExtendedAmount(),
                        T.mins(event.getDuration()).msecs(),
                        true,
                        event.getBolusID(),
                        PumpType.ACCU_CHEK_INSIGHT,
                        serial);
        }
    }

    private void processBolusDeliveredEvent(String serial, BolusDeliveredEvent event) {
        long timestamp = parseDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(),
                event.getEventHour(), event.getEventMinute(), event.getEventSecond()) + timeOffset;
        long startTimestamp = parseRelativeDate(event.getEventYear(), event.getEventMonth(), event.getEventDay(), event.getEventHour(),
                event.getEventMinute(), event.getEventSecond(), event.getStartHour(), event.getStartMinute(), event.getStartSecond()) + timeOffset;
        if (event.getBolusType() == BolusType.STANDARD || event.getBolusType() == BolusType.MULTIWAVE) {
            pumpSync.syncBolusWithPumpId(
                    startTimestamp,
                    event.getImmediateAmount(),
                    null,
                    event.getBolusID(),
                    PumpType.ACCU_CHEK_INSIGHT,
                    serial);
        }
        if (event.getBolusType() == BolusType.EXTENDED || event.getBolusType() == BolusType.MULTIWAVE) {
            if (profileFunction.getProfile(startTimestamp) != null)
                pumpSync.syncExtendedBolusWithPumpId(
                        startTimestamp,
                        event.getExtendedAmount(),
                        T.mins(event.getDuration()).msecs(),
                        true,
                        event.getBolusID(),
                        PumpType.ACCU_CHEK_INSIGHT,
                        serial);
        }
    }

    private void processOccurrenceOfAlertEvent(OccurrenceOfAlertEvent event) {
        if (!sp.getBoolean("insight_log_alerts", false)) return;
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
            logNote(timestamp, resourceHelper.gs(R.string.insight_alert_formatter, resourceHelper.gs(code), resourceHelper.gs(title)));
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

    private void uploadCareportalEvent(long date, DetailedBolusInfo.EventType event) {
        pumpSync.insertTherapyEventIfNewWithTimestamp(date, event, null, null, PumpType.ACCU_CHEK_INSIGHT, serialNumber());
    }

    @NonNull @Override
    public Constraint<Integer> applyBasalPercentConstraints(Constraint<Integer> percentRate, @NonNull Profile profile) {
        percentRate.setIfGreater(getAapsLogger(), 0, String.format(resourceHelper.gs(R.string.limitingpercentrate), 0, resourceHelper.gs(R.string.itmustbepositivevalue)), this);
        percentRate.setIfSmaller(getAapsLogger(), getPumpDescription().getMaxTempPercent(), String.format(resourceHelper.gs(R.string.limitingpercentrate), getPumpDescription().getMaxTempPercent(), resourceHelper.gs(R.string.pumplimit)), this);
        return percentRate;
    }

    @NonNull @Override
    public Constraint<Double> applyBolusConstraints(@NonNull Constraint<Double> insulin) {
        if (!limitsFetched) return insulin;
        insulin.setIfSmaller(getAapsLogger(), maximumBolusAmount, String.format(resourceHelper.gs(R.string.limitingbolus), maximumBolusAmount, resourceHelper.gs(R.string.pumplimit)), this);
        if (insulin.value() < minimumBolusAmount) {

            //TODO: Add function to Constraints or use different approach
            // This only works if the interface of the InsightPlugin is called last.
            // If not, another constraint could theoretically set the value between 0 and minimumBolusAmount

            insulin.set(getAapsLogger(), 0d, String.format(resourceHelper.gs(R.string.limitingbolus), minimumBolusAmount, resourceHelper.gs(R.string.pumplimit)), this);
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
            new Handler(Looper.getMainLooper()).post(() -> rxBus.send(new EventDismissNotification(Notification.INSIGHT_TIMEOUT_DURING_HANDSHAKE)));
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
            new Handler(Looper.getMainLooper()).post(() -> rxBus.send(new EventRefreshOverview("LocalInsightPlugin::onStateChanged", false)));
        }
        new Handler(Looper.getMainLooper()).post(() -> rxBus.send(new EventLocalInsightUpdateGUI()));
    }

    @Override
    public void onPumpPaired() {
        commandQueue.readStatus("Pump paired", null);
    }

    @Override
    public void onTimeoutDuringHandshake() {
        Notification notification = new Notification(Notification.INSIGHT_TIMEOUT_DURING_HANDSHAKE, resourceHelper.gs(R.string.timeout_during_handshake), Notification.URGENT);
        new Handler(Looper.getMainLooper()).post(() -> rxBus.send(new EventNewNotification(notification)));
    }

    @Override
    public boolean canHandleDST() {
        return true;
    }

}
