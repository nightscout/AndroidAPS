package app.aaps.pump.insight

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.TE
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.pump.defs.ManufacturerType
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.OwnDatabasePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.Insight
import app.aaps.core.interfaces.pump.Pump
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpPluginBase
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.PumpSync.PumpState.TemporaryBasal
import app.aaps.core.interfaces.pump.PumpSync.TemporaryBasalType
import app.aaps.core.interfaces.pump.defs.fillFor
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.validators.preferences.AdaptiveIntPreference
import app.aaps.core.validators.preferences.AdaptiveIntentPreference
import app.aaps.core.validators.preferences.AdaptiveSwitchPreference
import app.aaps.pump.insight.app_layer.Service
import app.aaps.pump.insight.app_layer.activities.InsightPairingInformationActivity
import app.aaps.pump.insight.app_layer.history.StartReadingHistoryMessage
import app.aaps.pump.insight.app_layer.history.StopReadingHistoryMessage
import app.aaps.pump.insight.app_layer.history.history_events.BolusDeliveredEvent
import app.aaps.pump.insight.app_layer.history.history_events.BolusProgrammedEvent
import app.aaps.pump.insight.app_layer.history.history_events.CannulaFilledEvent
import app.aaps.pump.insight.app_layer.history.history_events.DateTimeChangedEvent
import app.aaps.pump.insight.app_layer.history.history_events.DefaultDateTimeSetEvent
import app.aaps.pump.insight.app_layer.history.history_events.EndOfTBREvent
import app.aaps.pump.insight.app_layer.history.history_events.HistoryEvent
import app.aaps.pump.insight.app_layer.history.history_events.OccurrenceOfAlertEvent
import app.aaps.pump.insight.app_layer.history.history_events.OperatingModeChangedEvent
import app.aaps.pump.insight.app_layer.history.history_events.PowerUpEvent
import app.aaps.pump.insight.app_layer.history.history_events.SniffingDoneEvent
import app.aaps.pump.insight.app_layer.history.history_events.StartOfTBREvent
import app.aaps.pump.insight.app_layer.history.history_events.TotalDailyDoseEvent
import app.aaps.pump.insight.app_layer.history.history_events.TubeFilledEvent
import app.aaps.pump.insight.app_layer.parameter_blocks.ActiveBRProfileBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.BRProfile1Block
import app.aaps.pump.insight.app_layer.parameter_blocks.BRProfileBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.FactoryMinBolusAmountBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.MaxBolusAmountBlock
import app.aaps.pump.insight.app_layer.parameter_blocks.TBROverNotificationBlock
import app.aaps.pump.insight.app_layer.remote_control.CancelBolusMessage
import app.aaps.pump.insight.app_layer.remote_control.CancelTBRMessage
import app.aaps.pump.insight.app_layer.remote_control.ChangeTBRMessage
import app.aaps.pump.insight.app_layer.remote_control.ConfirmAlertMessage
import app.aaps.pump.insight.app_layer.remote_control.DeliverBolusMessage
import app.aaps.pump.insight.app_layer.remote_control.SetDateTimeMessage
import app.aaps.pump.insight.app_layer.remote_control.SetOperatingModeMessage
import app.aaps.pump.insight.app_layer.remote_control.SetTBRMessage
import app.aaps.pump.insight.app_layer.status.GetActiveAlertMessage
import app.aaps.pump.insight.app_layer.status.GetActiveBasalRateMessage
import app.aaps.pump.insight.app_layer.status.GetActiveBolusesMessage
import app.aaps.pump.insight.app_layer.status.GetActiveTBRMessage
import app.aaps.pump.insight.app_layer.status.GetBatteryStatusMessage
import app.aaps.pump.insight.app_layer.status.GetCartridgeStatusMessage
import app.aaps.pump.insight.app_layer.status.GetDateTimeMessage
import app.aaps.pump.insight.app_layer.status.GetOperatingModeMessage
import app.aaps.pump.insight.app_layer.status.GetPumpStatusRegisterMessage
import app.aaps.pump.insight.app_layer.status.GetTotalDailyDoseMessage
import app.aaps.pump.insight.app_layer.status.ResetPumpStatusRegisterMessage
import app.aaps.pump.insight.connection_service.InsightConnectionService
import app.aaps.pump.insight.database.InsightBolusID
import app.aaps.pump.insight.database.InsightDatabase
import app.aaps.pump.insight.database.InsightDbHelper
import app.aaps.pump.insight.database.InsightHistoryOffset
import app.aaps.pump.insight.database.InsightPumpID
import app.aaps.pump.insight.descriptors.ActiveBasalRate
import app.aaps.pump.insight.descriptors.ActiveBolus
import app.aaps.pump.insight.descriptors.ActiveTBR
import app.aaps.pump.insight.descriptors.AlertType
import app.aaps.pump.insight.descriptors.BasalProfile
import app.aaps.pump.insight.descriptors.BasalProfileBlock
import app.aaps.pump.insight.descriptors.BatteryStatus
import app.aaps.pump.insight.descriptors.BolusType
import app.aaps.pump.insight.descriptors.CartridgeStatus
import app.aaps.pump.insight.descriptors.InsightState
import app.aaps.pump.insight.descriptors.OperatingMode
import app.aaps.pump.insight.descriptors.TotalDailyDose
import app.aaps.pump.insight.events.EventLocalInsightUpdateGUI
import app.aaps.pump.insight.exceptions.InsightException
import app.aaps.pump.insight.exceptions.app_layer_errors.AppLayerErrorException
import app.aaps.pump.insight.exceptions.app_layer_errors.NoActiveTBRToCancelException
import app.aaps.pump.insight.keys.InsightBooleanKey
import app.aaps.pump.insight.keys.InsightDoubleNonKey
import app.aaps.pump.insight.keys.InsightIntKey
import app.aaps.pump.insight.keys.InsightIntentKey
import app.aaps.pump.insight.keys.InsightLongNonKey
import app.aaps.pump.insight.utils.ExceptionTranslator
import app.aaps.pump.insight.utils.ParameterBlockUtil
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Singleton
class InsightPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    commandQueue: CommandQueue,
    private val rxBus: RxBus,
    private val profileFunction: ProfileFunction,
    private val context: Context,
    private val dateUtil: DateUtil,
    private val insightDbHelper: InsightDbHelper,
    private val pumpSync: PumpSync,
    private val insightDatabase: InsightDatabase,
    private val pumpEnactResultProvider: Provider<PumpEnactResult>
) : PumpPluginBase(
    pluginDescription = PluginDescription()
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_insight_128)
        .pluginName(R.string.insight_local)
        .shortName(R.string.insightpump_shortname)
        .mainType(PluginType.PUMP)
        .description(R.string.description_pump_insight_local)
        .fragmentClass(InsightFragment::class.java.name)
        .preferencesId(PluginDescription.PREFERENCE_SCREEN),
    ownPreferences = listOf(
        InsightIntentKey::class.java, InsightBooleanKey::class.java, InsightIntKey::class.java,
        InsightLongNonKey::class.java, InsightDoubleNonKey::class.java,
    ),
    aapsLogger, rh, preferences, commandQueue
), Pump, Insight, PluginConstraints, InsightConnectionService.StateCallback, OwnDatabasePlugin {

    override val pumpDescription: PumpDescription = PumpDescription().also { it.fillFor(PumpType.ACCU_CHEK_INSIGHT) }
    private val _bolusLock: Any = arrayOfNulls<Any>(0)
    override var lastBolusAmount = 0.0
        private set
    var lastBolusTimestamp = 0L
        private set
    private var alertService: InsightAlertService? = null
    var connectionService: InsightConnectionService? = null
        private set
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (binder is InsightConnectionService.LocalBinder) {
                connectionService = binder.service
                connectionService?.registerStateCallback(this@InsightPlugin)
            } else if (binder is InsightAlertService.LocalBinder) {
                alertService = binder.service
            }
            if (connectionService != null && alertService != null) {
                rxBus.send(EventInitializationChanged())
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            connectionService = null
        }
    }
    private var timeOffset: Long = 0
    private var bolusID = 0
    private var bolusCancelled = false
    private var activeBasalProfile: BasalProfile? = null
    private var profileBlocks: List<BasalProfileBlock>? = null
    private var limitsFetched = false
    private var maximumBolusAmount = 0.0
    private var minimumBolusAmount = 0.0
    var operatingMode: OperatingMode? = null
        private set
    var batteryStatus: BatteryStatus? = null
        private set
    var cartridgeStatus: CartridgeStatus? = null
        private set
    var totalDailyDose: TotalDailyDose? = null
        private set
    var activeBasalRate: ActiveBasalRate? = null
        private set
    var activeTBR: ActiveTBR? = null
        private set
    var activeBoluses: List<ActiveBolus>? = null
        private set
    private var statusLoaded = false
    var tBROverNotificationBlock: TBROverNotificationBlock? = null
        private set

    override fun onStart() {
        super.onStart()
        context.bindService(Intent(context, InsightConnectionService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        context.bindService(Intent(context, InsightAlertService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        createNotificationChannel()
        lastBolusTimestamp = preferences.get(InsightLongNonKey.LastBolusTimestamp)
        lastBolusAmount = preferences.get(InsightDoubleNonKey.LastBolusAmount)
    }

    private fun createNotificationChannel() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(ALERT_CHANNEL_ID, rh.gs(R.string.insight_alert_notification_channel), NotificationManager.IMPORTANCE_HIGH)
        channel.setSound(null, null)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onStop() {
        super.onStop()
        context.unbindService(serviceConnection)
    }

    override fun isInitialized(): Boolean {
        return connectionService?.let { alertService != null && it.isPaired } == true
    }

    override fun isSuspended(): Boolean {
        return operatingMode != null && operatingMode != OperatingMode.STARTED
    }

    override fun isBusy(): Boolean {
        return false
    }

    override fun isConnected(): Boolean {
        return connectionService?.let { (alertService != null && it.hasRequestedConnection(this) && it.state == InsightState.CONNECTED) } == true
    }

    override fun isConnecting(): Boolean {
        if (connectionService == null || alertService == null || connectionService?.hasRequestedConnection(this) == false) return false
        val state = connectionService?.state
        return state == InsightState.CONNECTING || state == InsightState.APP_CONNECT_MESSAGE || state == InsightState.RECOVERING
    }

    override fun isHandshakeInProgress(): Boolean {
        return false
    }

    override fun connect(reason: String) {
        if (alertService != null) connectionService?.requestConnection(this)
    }

    override fun disconnect(reason: String) {
        if (alertService != null) connectionService?.withdrawConnectionRequest(this)
    }

    override fun stopConnecting() {
        if (alertService != null) connectionService?.withdrawConnectionRequest(this)
    }

    override fun getPumpStatus(reason: String) {
        connectionService?.let { service ->
            try {
                tBROverNotificationBlock = ParameterBlockUtil.readParameterBlock(service, Service.CONFIGURATION, TBROverNotificationBlock::class.java)
                readHistory()
                fetchBasalProfile()
                fetchLimitations()
                updatePumpTimeIfNeeded()
                fetchStatus()
            } catch (e: AppLayerErrorException) {
                aapsLogger.info(LTag.PUMP, "Exception while fetching status: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
            } catch (e: InsightException) {
                aapsLogger.info(LTag.PUMP, "Exception while fetching status: " + e.javaClass.canonicalName)
            } catch (e: Exception) {
                aapsLogger.error("Exception while fetching status", e)
            }
        }
    }

    @Throws(Exception::class) private fun updatePumpTimeIfNeeded() {
        val pumpTime = connectionService?.requestMessage(GetDateTimeMessage())?.await()?.pumpTime
        val calendar = Calendar.getInstance()

        if (pumpTime != null) {
            calendar[Calendar.YEAR] = pumpTime.year
            calendar[Calendar.MONTH] = pumpTime.month - 1
            calendar[Calendar.DAY_OF_MONTH] = pumpTime.day
            calendar[Calendar.HOUR_OF_DAY] = pumpTime.hour
            calendar[Calendar.MINUTE] = pumpTime.minute
            calendar[Calendar.SECOND] = pumpTime.second
            if (calendar[Calendar.HOUR_OF_DAY] != pumpTime.hour || abs(calendar.timeInMillis - dateUtil.now()) > 10000) {
                calendar.time = Date()
                pumpTime.year = calendar[Calendar.YEAR]
                pumpTime.month = calendar[Calendar.MONTH] + 1
                pumpTime.day = calendar[Calendar.DAY_OF_MONTH]
                pumpTime.hour = calendar[Calendar.HOUR_OF_DAY]
                pumpTime.minute = calendar[Calendar.MINUTE]
                pumpTime.second = calendar[Calendar.SECOND]
                val setDateTimeMessage = SetDateTimeMessage()
                setDateTimeMessage.pumpTime = pumpTime
                connectionService?.requestMessage(setDateTimeMessage)?.await()
                val notification = Notification(Notification.INSIGHT_DATE_TIME_UPDATED, rh.gs(app.aaps.core.ui.R.string.pump_time_updated), Notification.INFO, 60)
                rxBus.send(EventNewNotification(notification))
            }
        }
    }

    @Throws(Exception::class) private fun fetchBasalProfile() {
        connectionService?.let { service ->
            activeBasalProfile =
                ParameterBlockUtil.readParameterBlock(service, Service.CONFIGURATION, ActiveBRProfileBlock::class.java)?.activeBasalProfile
            profileBlocks = ParameterBlockUtil.readParameterBlock(service, Service.CONFIGURATION, BRProfile1Block::class.java)?.profileBlocks
        }
    }

    @Throws(Exception::class) private fun fetchStatus() {
        if (statusLoaded) {
            val registerMessage = connectionService?.requestMessage(GetPumpStatusRegisterMessage())?.await()
                ?: return
            val resetMessage = ResetPumpStatusRegisterMessage(
                operatingModeChanged = registerMessage.isOperatingModeChanged,
                batteryStatusChanged = registerMessage.isBatteryStatusChanged,
                cartridgeStatusChanged = registerMessage.isCartridgeStatusChanged,
                totalDailyDoseChanged = registerMessage.isTotalDailyDoseChanged,
                activeTBRChanged = registerMessage.isActiveTBRChanged,
                activeBolusesChanged = registerMessage.isActiveBolusesChanged
            )

            connectionService?.let { service ->
                service.requestMessage(resetMessage).await()
                registerMessage.run {
                    if (isOperatingModeChanged) operatingMode = service.requestMessage(GetOperatingModeMessage()).await().operatingMode
                    if (isBatteryStatusChanged) batteryStatus = service.requestMessage(GetBatteryStatusMessage()).await().batteryStatus
                    if (isCartridgeStatusChanged) cartridgeStatus = service.requestMessage(GetCartridgeStatusMessage()).await().cartridgeStatus
                    if (isTotalDailyDoseChanged) totalDailyDose = service.requestMessage(GetTotalDailyDoseMessage()).await().tDD
                    if (operatingMode == OperatingMode.STARTED) {
                        if (isActiveBasalRateChanged) activeBasalRate = service.requestMessage(GetActiveBasalRateMessage()).await().activeBasalRate
                        if (isActiveTBRChanged) activeTBR = service.requestMessage(GetActiveTBRMessage()).await().activeTBR
                        if (isActiveBolusesChanged) activeBoluses = service.requestMessage(GetActiveBolusesMessage()).await().activeBoluses
                    } else {
                        activeBasalRate = null
                        activeTBR = null
                        activeBoluses = null
                    }
                }
            }
        } else {
            val resetMessage = ResetPumpStatusRegisterMessage(
                operatingModeChanged = true,
                batteryStatusChanged = true,
                cartridgeStatusChanged = true,
                totalDailyDoseChanged = true,
                activeBasalRateChanged = true,
                activeTBRChanged = true,
                activeBolusesChanged = true
            )
            connectionService?.let { service ->
                service.requestMessage(resetMessage).await()
                operatingMode = service.requestMessage(GetOperatingModeMessage()).await().operatingMode
                batteryStatus = service.requestMessage(GetBatteryStatusMessage()).await().batteryStatus
                cartridgeStatus = service.requestMessage(GetCartridgeStatusMessage()).await().cartridgeStatus
                totalDailyDose = service.requestMessage(GetTotalDailyDoseMessage()).await().tDD
                if (operatingMode == OperatingMode.STARTED) {
                    activeBasalRate = service.requestMessage(GetActiveBasalRateMessage()).await().activeBasalRate
                    activeTBR = service.requestMessage(GetActiveTBRMessage()).await().activeTBR
                    activeBoluses = service.requestMessage(GetActiveBolusesMessage()).await().activeBoluses
                } else {
                    activeBasalRate = null
                    activeTBR = null
                    activeBoluses = null
                }
            }
            statusLoaded = true
        }
        rxBus.send(EventLocalInsightUpdateGUI())
        rxBus.send(EventRefreshOverview("LocalInsightPlugin::fetchStatus", false))
    }

    @Throws(Exception::class) private fun fetchLimitations() {
        connectionService?.let { service ->
            ParameterBlockUtil.readParameterBlock(service, Service.CONFIGURATION, MaxBolusAmountBlock::class.java)?.let {
                maximumBolusAmount = it.amountLimitation
                pumpDescription.basalMaximumRate = it.amountLimitation
            }
            ParameterBlockUtil.readParameterBlock(service, Service.CONFIGURATION, FactoryMinBolusAmountBlock::class.java)?.let {
                minimumBolusAmount = it.amountLimitation
                pumpDescription.basalMinimumRate = it.amountLimitation
            }
            limitsFetched = true
        }
    }

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        rxBus.send(EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED))
        val profileBlocks: MutableList<BasalProfileBlock> = ArrayList()
        for (i in profile.getBasalValues().indices) {
            val basalValue = profile.getBasalValues()[i]
            var nextValue: Profile.ProfileValue? = null
            if (profile.getBasalValues().size > i + 1) nextValue = profile.getBasalValues()[i + 1]
            val profileBlock = BasalProfileBlock()
            profileBlock.basalAmount = if (basalValue.value > 5) (basalValue.value / 0.1).roundToLong() * 0.1 else (basalValue.value / 0.01).roundToLong() * 0.01
            profileBlock.duration = ((nextValue?.timeAsSeconds ?: 24 * 60 * 60) - basalValue.timeAsSeconds) / 60
            profileBlocks.add(profileBlock)
        }
        connectionService?.let { service ->
            try {
                val activeBRProfileBlock = ActiveBRProfileBlock()
                activeBRProfileBlock.activeBasalProfile = BasalProfile.PROFILE_1
                ParameterBlockUtil.writeConfigurationBlock(service, activeBRProfileBlock)
                activeBasalProfile = BasalProfile.PROFILE_1
                val profileBlock: BRProfileBlock = BRProfile1Block()
                profileBlock.profileBlocks = profileBlocks
                ParameterBlockUtil.writeConfigurationBlock(service, profileBlock)
                rxBus.send(EventDismissNotification(Notification.FAILED_UPDATE_PROFILE))
                val notification = Notification(Notification.PROFILE_SET_OK, rh.gs(app.aaps.core.ui.R.string.profile_set_ok), Notification.INFO, 60)
                rxBus.send(EventNewNotification(notification))
                result.success(true)
                    .enacted(true)
                    .comment(app.aaps.core.ui.R.string.virtualpump_resultok)
                this.profileBlocks = profileBlocks
                try {
                    fetchStatus()
                } catch (_: Exception) {
                }
            } catch (e: AppLayerErrorException) {
                aapsLogger.info(LTag.PUMP, "Exception while setting profile: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
                val notification = Notification(Notification.FAILED_UPDATE_PROFILE, rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile), Notification.URGENT)
                rxBus.send(EventNewNotification(notification))
                result.comment(ExceptionTranslator.getString(context, e))
            } catch (e: InsightException) {
                aapsLogger.info(LTag.PUMP, "Exception while setting profile: " + e.javaClass.canonicalName)
                val notification = Notification(Notification.FAILED_UPDATE_PROFILE, rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile), Notification.URGENT)
                rxBus.send(EventNewNotification(notification))
                result.comment(ExceptionTranslator.getString(context, e))
            } catch (e: Exception) {
                aapsLogger.error("Exception while setting profile", e)
                val notification = Notification(Notification.FAILED_UPDATE_PROFILE, rh.gs(app.aaps.core.ui.R.string.failed_update_basal_profile), Notification.URGENT)
                rxBus.send(EventNewNotification(notification))
                result.comment(ExceptionTranslator.getString(context, e))
            }
        }
        return result
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
        if (!isInitialized() || profileBlocks == null) return true
        profileBlocks?.let {
            if (profile.getBasalValues().size != it.size) return false
            if (activeBasalProfile != BasalProfile.PROFILE_1) return false
            for (i in it.indices) {
                val profileBlock = it[i]
                val basalValue = profile.getBasalValues()[i]
                var nextValue: Profile.ProfileValue? = null
                if (profile.getBasalValues().size > i + 1) nextValue = profile.getBasalValues()[i + 1]
                if (profileBlock.duration * 60 != (nextValue?.timeAsSeconds ?: 24 * 60 * 60) - basalValue.timeAsSeconds
                ) return false
                if (abs(profileBlock.basalAmount - basalValue.value) > (if (basalValue.value > 5) 0.051 else 0.0051)) return false
            }
        }
        return true
    }

    override val lastDataTime: Long get() = if (connectionService == null || alertService == null) dateUtil.now() else connectionService?.lastDataTime ?: 0
    override val lastBolusTime: Long? get() = lastBolusTimestamp

    override val baseBasalRate: Double
        get() {
            if (connectionService == null || alertService == null) return 0.0
            return activeBasalRate?.activeBasalRate ?: 0.0
        }
    override val reservoirLevel: Double get() = cartridgeStatus?.remainingAmount ?: 0.0
    override val batteryLevel: Int? get() = batteryStatus?.batteryAmount

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        if (detailedBolusInfo.insulin.equals(0.0) || detailedBolusInfo.carbs > 0) {
            throw IllegalArgumentException(detailedBolusInfo.toString(), Exception())
        }
        val result = pumpEnactResultProvider.get()
        connectionService?.let { service ->
            val insulin = (detailedBolusInfo.insulin / 0.01).roundToInt() * 0.01
            if (insulin > 0) {
                try {
                    synchronized(_bolusLock) {
                        val bolusMessage = DeliverBolusMessage()
                        bolusMessage.bolusType = BolusType.STANDARD
                        bolusMessage.duration = 0
                        bolusMessage.extendedAmount = 0.0
                        bolusMessage.immediateAmount = insulin
                        bolusMessage.disableVibration = preferences.get(if (detailedBolusInfo.bolusType === BS.Type.SMB) InsightBooleanKey.DisableVibrationAuto else InsightBooleanKey.DisableVibration)
                        bolusID = service.requestMessage(bolusMessage).await().bolusId
                        bolusCancelled = false
                    }
                    result.success(true).enacted(true)
                    rxBus.send(EventOverviewBolusProgress(rh, 0.0, id = detailedBolusInfo.id))
                    var trials = 0
                    val now = dateUtil.now()
                    val serial = serialNumber()
                    insightDbHelper.createOrUpdate(
                        InsightBolusID(
                            now,
                            serial,
                            bolusID,
                            null,
                            null
                        )
                    )
                    insightDbHelper.getInsightBolusID(serial, bolusID, now)?.also {
                        pumpSync.syncBolusWithPumpId(
                            it.timestamp,
                            detailedBolusInfo.insulin,
                            detailedBolusInfo.bolusType,
                            it.id,
                            PumpType.ACCU_CHEK_INSIGHT,
                            serialNumber()
                        )
                    }
                    while (!bolusCancelled) {
                        val operatingMode = service.requestMessage(GetOperatingModeMessage()).await().operatingMode
                        if (operatingMode != OperatingMode.STARTED) break
                        val activeBoluses = service.requestMessage(GetActiveBolusesMessage()).await().activeBoluses
                        var activeBolus: ActiveBolus? = null
                        if (activeBoluses != null) {
                            for (bolus in activeBoluses) {
                                if (bolus.bolusID == bolusID) {
                                    activeBolus = bolus
                                    break
                                }
                            }
                        }
                        if (activeBolus != null) {
                            trials = -1
                            rxBus.send(EventOverviewBolusProgress(rh, delivered = activeBolus.initialAmount - activeBolus.remainingAmount, id = detailedBolusInfo.id))
                        } else {
                            synchronized(_bolusLock) {
                                if (bolusCancelled || trials == -1 || trials++ >= 5) {
                                    if (!bolusCancelled) {
                                        rxBus.send(EventOverviewBolusProgress(rh, delivered = insulin, id = detailedBolusInfo.id))
                                    }
                                }
                            }
                            if (trials == -1 || trials >= 5) {
                                break
                            }
                        }
                        SystemClock.sleep(200)
                    }
                    readHistory()
                    fetchStatus()
                } catch (e: AppLayerErrorException) {
                    aapsLogger.info(LTag.PUMP, "Exception while delivering bolus: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
                    result.comment(ExceptionTranslator.getString(context, e))
                } catch (e: InsightException) {
                    aapsLogger.info(LTag.PUMP, "Exception while delivering bolus: " + e.javaClass.canonicalName)
                    result.comment(ExceptionTranslator.getString(context, e))
                } catch (e: Exception) {
                    aapsLogger.error("Exception while delivering bolus", e)
                    result.comment(ExceptionTranslator.getString(context, e))
                }
                result.bolusDelivered(insulin)
            }
        }
        return result
    }

    override fun stopBolusDelivering() {
        Thread {
            connectionService?.let { service ->
                try {
                    synchronized(_bolusLock) {
                        alertService?.ignore(AlertType.WARNING_38)
                        val cancelBolusMessage = CancelBolusMessage()
                        cancelBolusMessage.bolusID = bolusID
                        service.requestMessage(cancelBolusMessage).await()
                        bolusCancelled = true
                        confirmAlert(AlertType.WARNING_38)
                        alertService?.ignore(null)
                        aapsLogger.info(LTag.PUMP, "XXXX Stop Thread end)")
                    }
                } catch (e: AppLayerErrorException) {
                    aapsLogger.info(LTag.PUMP, "Exception while canceling bolus: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
                } catch (e: InsightException) {
                    aapsLogger.info(LTag.PUMP, "Exception while canceling bolus: " + e.javaClass.canonicalName)
                } catch (e: Exception) {
                    aapsLogger.error("Exception while canceling bolus", e)
                }
            }
        }.start()
    }

    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (activeBasalRate?.activeBasalRate == 0.0) return result
        activeBasalRate?.let { activeBasalRate ->
            val percent = 100.0 / activeBasalRate.activeBasalRate * absoluteRate
            if (isFakingTempsByExtendedBoluses) {
                val cancelEBResult = cancelExtendedBolusOnly()
                if (cancelEBResult.success) {
                    if (percent > 250) {
                        val cancelTBRResult = cancelTempBasalOnly()
                        if (cancelTBRResult.success) {
                            val ebResult = setExtendedBolusOnly(
                                (absoluteRate - baseBasalRate) / 60.0 * durationInMinutes.toDouble(), durationInMinutes,
                                preferences.get(InsightBooleanKey.DisableVibrationAuto)
                            )
                            if (ebResult.success) {
                                result.success(true)
                                    .enacted(true)
                                    .isPercent(false)
                                    .absolute(absoluteRate)
                                    .duration(durationInMinutes)
                                    .comment(app.aaps.core.ui.R.string.virtualpump_resultok)
                            } else {
                                result.comment(ebResult.comment)
                            }
                        } else {
                            result.comment(cancelTBRResult.comment)
                        }
                    } else {
                        return setTempBasalPercent(percent.roundToInt(), durationInMinutes, profile, enforceNew, tbrType)
                    }
                } else {
                    result.comment(cancelEBResult.comment)
                }
            } else {
                return setTempBasalPercent(percent.roundToInt(), durationInMinutes, profile, enforceNew, tbrType)
            }
            try {
                fetchStatus()
                readHistory()
            } catch (e: AppLayerErrorException) {
                aapsLogger.info(LTag.PUMP, "Exception after setting TBR: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
            } catch (e: InsightException) {
                aapsLogger.info(LTag.PUMP, "Exception after setting TBR: " + e.javaClass.canonicalName)
            } catch (e: Exception) {
                aapsLogger.error("Exception after setting TBR", e)
            }
        }
        return result
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        var percentage = (percent.toDouble() / 10.0).roundToInt() * 10
        if (percentage == 100) return cancelTempBasal(true) else if (percentage > 250) percentage = 250
        try {
            if (activeTBR != null) {
                val message = ChangeTBRMessage()
                message.duration = durationInMinutes
                message.percentage = percentage
                connectionService?.requestMessage(message)
            } else {
                val message = SetTBRMessage()
                message.duration = durationInMinutes
                message.percentage = percentage
                connectionService?.requestMessage(message)
            }
            result.isPercent(true)
                .percent(percentage)
                .duration(durationInMinutes)
                .success(true)
                .enacted(true)
                .comment(app.aaps.core.ui.R.string.virtualpump_resultok)
            readHistory()
            fetchStatus()
        } catch (e: AppLayerErrorException) {
            aapsLogger.info(LTag.PUMP, "Exception while setting TBR: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
            result.comment(ExceptionTranslator.getString(context, e))
        } catch (e: InsightException) {
            aapsLogger.info(LTag.PUMP, "Exception while setting TBR: " + e.javaClass.canonicalName)
            result.comment(ExceptionTranslator.getString(context, e))
        } catch (e: Exception) {
            aapsLogger.error("Exception while setting TBR", e)
            result.comment(ExceptionTranslator.getString(context, e))
        }
        return result
    }

    override fun setExtendedBolus(insulin: Double, durationInMinutes: Int): PumpEnactResult {
        var result = cancelExtendedBolusOnly()
        if (result.success) result = setExtendedBolusOnly(insulin, durationInMinutes, preferences.get(InsightBooleanKey.DisableVibration))
        try {
            fetchStatus()
            readHistory()
        } catch (e: AppLayerErrorException) {
            aapsLogger.info(LTag.PUMP, "Exception after delivering extended bolus: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
        } catch (e: InsightException) {
            aapsLogger.info(LTag.PUMP, "Exception after delivering extended bolus: " + e.javaClass.canonicalName)
        } catch (e: Exception) {
            aapsLogger.error("Exception after delivering extended bolus", e)
        }
        return result
    }

    fun setExtendedBolusOnly(insulin: Double, durationInMinutes: Int, disableVibration: Boolean): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        connectionService?.let { service ->
            try {
                val bolusMessage = DeliverBolusMessage()
                bolusMessage.bolusType = BolusType.EXTENDED
                bolusMessage.duration = durationInMinutes
                bolusMessage.extendedAmount = insulin
                bolusMessage.immediateAmount = 0.0
                bolusMessage.disableVibration = disableVibration
                val bolusID = service.requestMessage(bolusMessage).await().bolusId
                insightDbHelper.createOrUpdate(
                    InsightBolusID(
                        timestamp = dateUtil.now(),
                        pumpSerial = serialNumber(),
                        bolusID = bolusID
                    )
                )
                result.success(true).enacted(true).comment(app.aaps.core.ui.R.string.virtualpump_resultok)
            } catch (e: AppLayerErrorException) {
                aapsLogger.info(LTag.PUMP, "Exception while delivering extended bolus: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
                result.comment(ExceptionTranslator.getString(context, e))
            } catch (e: InsightException) {
                aapsLogger.info(LTag.PUMP, "Exception while delivering extended bolus: " + e.javaClass.canonicalName)
                result.comment(ExceptionTranslator.getString(context, e))
            } catch (e: Exception) {
                aapsLogger.error("Exception while delivering extended bolus", e)
                result.comment(ExceptionTranslator.getString(context, e))
            }
        }
        return result
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        var cancelEBResult: PumpEnactResult? = null
        if (isFakingTempsByExtendedBoluses) cancelEBResult = cancelExtendedBolusOnly()
        val cancelTBRResult = cancelTempBasalOnly()
        result.success((cancelEBResult == null || cancelEBResult.success) && cancelTBRResult.success)
        result.enacted(cancelEBResult != null && cancelEBResult.enacted || cancelTBRResult.enacted)
        result.comment(cancelEBResult?.comment ?: cancelTBRResult.comment)
        try {
            fetchStatus()
            readHistory()
        } catch (e: AppLayerErrorException) {
            aapsLogger.info(LTag.PUMP, "Exception after canceling TBR: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
        } catch (e: InsightException) {
            aapsLogger.info(LTag.PUMP, "Exception after canceling TBR: " + e.javaClass.canonicalName)
        } catch (e: Exception) {
            aapsLogger.error("Exception after canceling TBR", e)
        }
        return result
    }

    private fun cancelTempBasalOnly(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        connectionService?.let { service ->
            try {
                alertService?.ignore(AlertType.WARNING_36)
                service.requestMessage(CancelTBRMessage()).await()
                result.success(true)
                    .enacted(true)
                    .isTempCancel(true)
                confirmAlert(AlertType.WARNING_36)
                alertService?.ignore(null)
                result.comment(app.aaps.core.ui.R.string.virtualpump_resultok)
            } catch (_: NoActiveTBRToCancelException) {
                result.success(true)
                result.comment(app.aaps.core.ui.R.string.virtualpump_resultok)
            } catch (e: AppLayerErrorException) {
                aapsLogger.info(LTag.PUMP, "Exception while canceling TBR: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
                result.comment(ExceptionTranslator.getString(context, e))
            } catch (e: InsightException) {
                aapsLogger.info(LTag.PUMP, "Exception while canceling TBR: " + e.javaClass.canonicalName)
                result.comment(ExceptionTranslator.getString(context, e))
            } catch (e: Exception) {
                aapsLogger.error("Exception while canceling TBR", e)
                result.comment(ExceptionTranslator.getString(context, e))
            }
        }
        return result
    }

    override fun cancelExtendedBolus(): PumpEnactResult {
        val result = cancelExtendedBolusOnly()
        try {
            fetchStatus()
            readHistory()
        } catch (e: AppLayerErrorException) {
            aapsLogger.info(LTag.PUMP, "Exception after canceling extended bolus: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
        } catch (e: InsightException) {
            aapsLogger.info(LTag.PUMP, "Exception after canceling extended bolus: " + e.javaClass.canonicalName)
        } catch (e: Exception) {
            aapsLogger.error("Exception after canceling extended bolus", e)
        }
        return result
    }

    private fun cancelExtendedBolusOnly(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        connectionService?.let { service ->
            try {
                activeBoluses?.forEach { activeBolus ->
                    if (activeBolus.bolusType == BolusType.EXTENDED || activeBolus.bolusType == BolusType.MULTIWAVE) {
                        alertService?.ignore(AlertType.WARNING_38)
                        val cancelBolusMessage = CancelBolusMessage()
                        cancelBolusMessage.bolusID = activeBolus.bolusID
                        service.requestMessage(cancelBolusMessage).await()
                        confirmAlert(AlertType.WARNING_38)
                        alertService?.ignore(null)
                        val insightBolusID = insightDbHelper.getInsightBolusID(serialNumber(), activeBolus.bolusID, dateUtil.now())
                        if (insightBolusID != null) {
                            result.enacted(true).success(true)
                        }
                    }
                }
                result.success(true).comment(app.aaps.core.ui.R.string.virtualpump_resultok)
            } catch (e: AppLayerErrorException) {
                aapsLogger.info(LTag.PUMP, "Exception while canceling extended bolus: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
                result.comment(ExceptionTranslator.getString(context, e))
            } catch (e: InsightException) {
                aapsLogger.info(LTag.PUMP, "Exception while canceling extended bolus: " + e.javaClass.canonicalName)
                result.comment(ExceptionTranslator.getString(context, e))
            } catch (e: Exception) {
                aapsLogger.error("Exception while canceling extended bolus", e)
                result.comment(ExceptionTranslator.getString(context, e))
            }
        }
        return result
    }

    private fun confirmAlert(alertType: AlertType) {
        try {
            val started = dateUtil.now()
            var continueLoop = true                                             //Todo improve this
            while (dateUtil.now() - started < 10000 && continueLoop) {
                connectionService?.let { service ->
                    val activeAlertMessage = service.requestMessage(GetActiveAlertMessage()).await()
                    activeAlertMessage.alert?.let {
                        if (it.alertType == alertType) {
                            val confirmMessage = ConfirmAlertMessage()
                            confirmMessage.alertID = it.alertId
                            service.requestMessage(confirmMessage).await()
                        } else continueLoop = false                            // break not accepted here because jump accross class bundary
                    }
                }
            }
        } catch (e: AppLayerErrorException) {
            aapsLogger.info(LTag.PUMP, "Exception while confirming alert: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
        } catch (e: InsightException) {
            aapsLogger.info(LTag.PUMP, "Exception while confirming alert: " + e.javaClass.canonicalName)
        } catch (e: Exception) {
            aapsLogger.error("Exception while confirming alert", e)
        }
    }

    override fun manufacturer(): ManufacturerType = ManufacturerType.Roche
    override fun model(): PumpType = PumpType.ACCU_CHEK_INSIGHT

    override fun serialNumber(): String {
        return connectionService?.let { service ->
            if (alertService == null) "Unknown" else service.pumpSystemIdentification?.serialNumber
        }
            ?: "Unknown"
    }

    override fun stopPump(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        connectionService?.let { service ->
            try {
                val operatingModeMessage = SetOperatingModeMessage()
                operatingModeMessage.operatingMode = OperatingMode.STOPPED
                service.requestMessage(operatingModeMessage).await()
                result.success(true).enacted(true)
                fetchStatus()
                readHistory()
            } catch (e: AppLayerErrorException) {
                aapsLogger.info(LTag.PUMP, "Exception while stopping pump: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
                result.comment(ExceptionTranslator.getString(context, e))
            } catch (e: InsightException) {
                aapsLogger.info(LTag.PUMP, "Exception while stopping pump: " + e.javaClass.canonicalName)
                result.comment(ExceptionTranslator.getString(context, e))
            } catch (e: Exception) {
                aapsLogger.error("Exception while stopping pump", e)
                result.comment(ExceptionTranslator.getString(context, e))
            }
        }
        return result
    }

    override fun startPump(): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        connectionService?.let { service ->
            try {
                val operatingModeMessage = SetOperatingModeMessage()
                operatingModeMessage.operatingMode = OperatingMode.STARTED
                service.requestMessage(operatingModeMessage).await()
                result.success(true).enacted(true)
                fetchStatus()
                readHistory()
            } catch (e: AppLayerErrorException) {
                aapsLogger.info(LTag.PUMP, "Exception while starting pump: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
                result.comment(ExceptionTranslator.getString(context, e))
            } catch (e: InsightException) {
                aapsLogger.info(LTag.PUMP, "Exception while starting pump: " + e.javaClass.canonicalName)
                result.comment(ExceptionTranslator.getString(context, e))
            } catch (e: Exception) {
                aapsLogger.error("Exception while starting pump", e)
                result.comment(ExceptionTranslator.getString(context, e))
            }
        }
        return result
    }

    override fun setTBROverNotification(enabled: Boolean): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        tBROverNotificationBlock?.let { tBROverNotificationBlock ->
            val valueBefore = tBROverNotificationBlock.isEnabled
            tBROverNotificationBlock.isEnabled = enabled
            connectionService?.let { service ->
                try {
                    ParameterBlockUtil.writeConfigurationBlock(service, tBROverNotificationBlock)
                    result.success(true).enacted(true)
                } catch (e: AppLayerErrorException) {
                    tBROverNotificationBlock.isEnabled = valueBefore
                    aapsLogger.info(LTag.PUMP, "Exception while updating TBR notification block: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
                    result.comment(ExceptionTranslator.getString(context, e))
                } catch (e: InsightException) {
                    tBROverNotificationBlock.isEnabled = valueBefore
                    aapsLogger.info(LTag.PUMP, "Exception while updating TBR notification block: " + e.javaClass.simpleName)
                    result.comment(ExceptionTranslator.getString(context, e))
                } catch (e: Exception) {
                    tBROverNotificationBlock.isEnabled = valueBefore
                    aapsLogger.error("Exception while updating TBR notification block", e)
                    result.comment(ExceptionTranslator.getString(context, e))
                }
            }
        }
        return result
    }

    override val isFakingTempsByExtendedBoluses: Boolean
        get() = preferences.get(InsightBooleanKey.EnableTbrEmulation)

    override fun loadTDDs(): PumpEnactResult =
        pumpEnactResultProvider.get().success(true)

    private fun readHistory() {
        connectionService?.let { service ->
            try {
                val pumpTime = service.requestMessage(GetDateTimeMessage()).await().pumpTime
                val serial = serialNumber()
                pumpTime?.let {
                    timeOffset = Calendar.getInstance(TimeZone.getTimeZone("UTC")).timeInMillis - parseDate(
                        it.year,
                        it.month, it.day, it.hour, it.minute, it.second
                    )
                }
                val historyOffset = insightDbHelper.getInsightHistoryOffset(serial)
                try {
                    var historyEvents: MutableList<HistoryEvent> = ArrayList()
                    if (historyOffset == null) {
                        val startMessage = StartReadingHistoryMessage()
                        startMessage.direction = app.aaps.pump.insight.app_layer.history.HistoryReadingDirection.BACKWARD
                        startMessage.offset = -0x1
                        service.requestMessage(startMessage).await()
                        historyEvents = service.requestMessage(app.aaps.pump.insight.app_layer.history.ReadHistoryEventsMessage()).await().historyEvents
                    } else {
                        val startMessage = StartReadingHistoryMessage()
                        startMessage.direction = app.aaps.pump.insight.app_layer.history.HistoryReadingDirection.FORWARD
                        startMessage.offset = historyOffset.offset + 1
                        service.requestMessage(startMessage).await()
                        while (true) {
                            val newEvents = service.requestMessage(app.aaps.pump.insight.app_layer.history.ReadHistoryEventsMessage()).await().historyEvents
                            if (newEvents.isEmpty()) break
                            historyEvents.addAll(newEvents)
                        }
                    }
                    historyEvents.sort()
                    historyEvents.reverse()
                    if (historyOffset != null) processHistoryEvents(serial, historyEvents)
                    if (historyEvents.isNotEmpty()) {
                        insightDbHelper.createOrUpdate(
                            InsightHistoryOffset(
                                serial,
                                historyEvents[0].eventPosition
                            )
                        )
                    }
                } catch (e: AppLayerErrorException) {
                    aapsLogger.info(LTag.PUMP, "Exception while reading history: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
                } catch (e: InsightException) {
                    aapsLogger.info(LTag.PUMP, "Exception while reading history: " + e.javaClass.simpleName)
                } catch (e: Exception) {
                    aapsLogger.error("Exception while reading history", e)
                } finally {
                    connectionService?.requestMessage(StopReadingHistoryMessage())?.await()
                }
            } catch (e: AppLayerErrorException) {
                aapsLogger.info(LTag.PUMP, "Exception while reading history: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
            } catch (e: InsightException) {
                aapsLogger.info(LTag.PUMP, "Exception while reading history: " + e.javaClass.simpleName)
            } catch (e: Exception) {
                aapsLogger.error("Exception while reading history", e)
            }
            rxBus.send(EventRefreshOverview("LocalInsightPlugin::readHistory", false))
        }
    }

    private fun processHistoryEvents(serial: String, historyEvents: List<HistoryEvent?>) {
        val temporaryBasals: MutableList<TemporaryBasal> = ArrayList()
        val pumpStartedEvents: MutableList<InsightPumpID> = ArrayList()
        for (historyEvent in historyEvents) if (!processHistoryEvent(serial, temporaryBasals, pumpStartedEvents, historyEvent)) break
        temporaryBasals.reverse()
        for ((timestamp, _, pumpSerial, eventID) in pumpStartedEvents) {
            var stoppedEvent = pumpSerial?.let { insightDbHelper.getPumpStoppedEvent(it, timestamp) }
            if (stoppedEvent != null && pumpSerial != null && stoppedEvent.eventType == InsightPumpID.EventType.PumpStopped) {             // Search if Stop event is after 15min of Pause
                val pauseEvent = insightDbHelper.getPumpStoppedEvent(pumpSerial, stoppedEvent.timestamp - T.mins(1).msecs())
                if (pauseEvent != null && pauseEvent.eventType == InsightPumpID.EventType.PumpPaused && stoppedEvent.timestamp - pauseEvent.timestamp < T.mins(16).msecs()) {
                    stoppedEvent = pauseEvent
                    stoppedEvent.eventType = InsightPumpID.EventType.PumpStopped
                }
            }
            if (stoppedEvent == null || stoppedEvent.eventType == InsightPumpID.EventType.PumpPaused || timestamp - stoppedEvent.timestamp < 10000) continue
            val tbrStart = stoppedEvent.timestamp + 10000
            val temporaryBasal = TemporaryBasal(
                timestamp = tbrStart,
                duration = timestamp - tbrStart,
                rate = 0.0,
                isAbsolute = false,
                type = TemporaryBasalType.NORMAL,
                id = eventID,
                pumpId = eventID
            )
            temporaryBasals.add(temporaryBasal)
        }
        temporaryBasals.sortWith { o1, o2 -> (o1.timestamp - o2.timestamp).toInt() }
        for (temporaryBasal in temporaryBasals) {
            temporaryBasal.pumpId?.let { pumpId ->
                if (temporaryBasal.duration == 0L) {                    // for Stop TBR event duration = 0L
                    pumpSync.syncStopTemporaryBasalWithPumpId(
                        timestamp = temporaryBasal.timestamp,
                        endPumpId = pumpId,
                        pumpType = PumpType.ACCU_CHEK_INSIGHT,
                        pumpSerial = serial
                    )
                }
                if (temporaryBasal.rate != 100.0) {
                    pumpSync.syncTemporaryBasalWithPumpId(
                        timestamp = temporaryBasal.timestamp,
                        rate = temporaryBasal.rate,
                        duration = temporaryBasal.duration,
                        isAbsolute = temporaryBasal.isAbsolute,
                        type = temporaryBasal.type,
                        pumpId = pumpId,
                        pumpType = PumpType.ACCU_CHEK_INSIGHT,
                        pumpSerial = serial
                    )
                }
            }
        }
    }

    private fun processHistoryEvent(serial: String, temporaryBasals: MutableList<TemporaryBasal>, pumpStartedEvents: MutableList<InsightPumpID>, event: HistoryEvent?): Boolean {
        when (event) {
            is DefaultDateTimeSetEvent   -> return false
            is DateTimeChangedEvent      -> processDateTimeChangedEvent(event)
            is CannulaFilledEvent        -> processCannulaFilledEvent(event)
            is TotalDailyDoseEvent       -> processTotalDailyDoseEvent(serial, event)
            is TubeFilledEvent           -> processTubeFilledEvent(event)
            is SniffingDoneEvent         -> processSniffingDoneEvent(event)
            is PowerUpEvent              -> processPowerUpEvent(event)
            is OperatingModeChangedEvent -> processOperatingModeChangedEvent(serial, pumpStartedEvents, event)
            is StartOfTBREvent           -> processStartOfTBREvent(serial, temporaryBasals, event)
            is EndOfTBREvent             -> processEndOfTBREvent(serial, temporaryBasals, event)
            is BolusProgrammedEvent      -> processBolusProgrammedEvent(serial, event)
            is BolusDeliveredEvent       -> processBolusDeliveredEvent(serial, event)
            is OccurrenceOfAlertEvent    -> processOccurrenceOfAlertEvent(event)
        }
        return true
    }

    private fun processDateTimeChangedEvent(event: DateTimeChangedEvent) {
        val timeAfter = parseDate(event.eventYear, event.eventMonth, event.eventDay, event.eventHour, event.eventMinute, event.eventSecond)
        val timeBefore = parseDate(event.beforeYear, event.beforeMonth, event.beforeDay, event.beforeHour, event.beforeMinute, event.beforeSecond)
        timeOffset -= timeAfter - timeBefore
    }

    private fun processCannulaFilledEvent(event: CannulaFilledEvent) {
        if (!preferences.get(InsightBooleanKey.LogSiteChanges)) return
        val timestamp = parseDate(
            event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond
        ) + timeOffset
        if (event.amount > 0.0)                 // Don't record event if amount is null => Fix Site Change with Insight v3 (event is always sent when Reservoir is changed)
            uploadCareportalEvent(timestamp, TE.Type.CANNULA_CHANGE)
    }

    private fun processTotalDailyDoseEvent(serial: String, event: TotalDailyDoseEvent) {
        val calendar = Calendar.getInstance()
        calendar.time = Date(0)
        calendar[Calendar.YEAR] = event.totalYear
        calendar[Calendar.MONTH] = event.totalMonth - 1
        calendar[Calendar.DAY_OF_MONTH] = event.totalDay
        pumpSync.createOrUpdateTotalDailyDose(
            timestamp = dateUtil.now(),
            bolusAmount = event.bolusTotal,
            basalAmount = event.basalTotal,
            totalAmount = 0.0,  // will be calculated automatically
            pumpId = event.eventPosition,
            pumpType = PumpType.ACCU_CHEK_INSIGHT,
            pumpSerial = serial
        )
    }

    private fun processTubeFilledEvent(event: TubeFilledEvent) {
        if (!preferences.get(InsightBooleanKey.LogTubeChanges)) return
        val timestamp = parseDate(
            event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond
        ) + timeOffset
        if (event.amount > 0.0) // Don't record event if amount is null
            logNote(timestamp, rh.gs(R.string.tube_changed))
    }

    private fun processSniffingDoneEvent(event: SniffingDoneEvent) {
        if (!preferences.get(InsightBooleanKey.LogReservoirChanges)) return
        val timestamp = parseDate(
            event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond
        ) + timeOffset
        uploadCareportalEvent(timestamp, TE.Type.INSULIN_CHANGE)
    }

    private fun processPowerUpEvent(event: PowerUpEvent) {
        if (!preferences.get(InsightBooleanKey.LogBatteryChanges)) return
        val timestamp = parseDate(
            event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond
        ) + timeOffset
        uploadCareportalEvent(timestamp, TE.Type.PUMP_BATTERY_CHANGE)
    }

    private fun processOperatingModeChangedEvent(serial: String, pumpStartedEvents: MutableList<InsightPumpID>, event: OperatingModeChangedEvent) {
        val timestamp = parseDate(
            event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond
        ) + timeOffset
        val pumpID = InsightPumpID(
            timestamp,
            InsightPumpID.EventType.None,
            serial,
            event.eventPosition
        )
        when (event.newValue) {
            OperatingMode.STARTED -> {
                pumpID.eventType = InsightPumpID.EventType.PumpStarted
                pumpStartedEvents.add(pumpID)
                if (preferences.get(InsightBooleanKey.LogOperatingModeChanges)) logNote(timestamp, rh.gs(R.string.pump_started))
            }

            OperatingMode.STOPPED -> {
                pumpID.eventType = InsightPumpID.EventType.PumpStopped
                if (preferences.get(InsightBooleanKey.LogOperatingModeChanges)) logNote(timestamp, rh.gs(R.string.pump_stopped))
            }

            OperatingMode.PAUSED  -> {
                pumpID.eventType = InsightPumpID.EventType.PumpPaused
                if (preferences.get(InsightBooleanKey.LogOperatingModeChanges)) logNote(timestamp, rh.gs(app.aaps.core.ui.R.string.pump_paused))
            }

            else                  -> Unit
        }
        insightDbHelper.createOrUpdate(pumpID)
    }

    private fun processStartOfTBREvent(serial: String, temporaryBasals: MutableList<TemporaryBasal>, event: StartOfTBREvent) {
        val timestamp = parseDate(
            event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond
        ) + timeOffset
        insightDbHelper.createOrUpdate(
            InsightPumpID(
                timestamp = timestamp,
                eventType = InsightPumpID.EventType.StartOfTBR,
                pumpSerial = serial,
                eventID = event.eventPosition
            )
        )
        temporaryBasals.add(
            TemporaryBasal(
                timestamp = timestamp,
                duration = T.mins(event.duration.toLong()).msecs(),
                rate = event.amount.toDouble(),
                isAbsolute = false,
                type = TemporaryBasalType.NORMAL,
                id = event.eventPosition,
                pumpId = event.eventPosition
            )
        )
    }

    private fun processEndOfTBREvent(serial: String, temporaryBasals: MutableList<TemporaryBasal>, event: EndOfTBREvent) {
        val timestamp = parseDate(
            event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond
        ) + timeOffset
        insightDbHelper.createOrUpdate(
            InsightPumpID(
                timestamp = timestamp - 1500L,
                eventType = InsightPumpID.EventType.EndOfTBR,
                pumpSerial = serial,
                eventID = event.eventPosition
            )
        )
        temporaryBasals.add(
            TemporaryBasal(
                timestamp = timestamp - 1500L,
                duration = 0L,
                rate = 100.0,
                isAbsolute = false,
                type = TemporaryBasalType.NORMAL,
                id = event.eventPosition,
                pumpId = event.eventPosition
            )
        )
    }

    private fun processBolusProgrammedEvent(serial: String, event: BolusProgrammedEvent) {
        val timestamp = parseDate(
            event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond
        ) + timeOffset
        var bolusID = insightDbHelper.getInsightBolusID(serial, event.bolusID, timestamp)
        if (bolusID?.endID != null) {
            bolusID.startID = event.eventPosition
            insightDbHelper.createOrUpdate(bolusID)
            return
        }
        if (bolusID == null || bolusID.startID != null) {                        //In rare edge cases two boluses can share the same ID
            insightDbHelper.createOrUpdate(
                InsightBolusID(
                    timestamp = timestamp,
                    pumpSerial = serial,
                    bolusID = event.bolusID,
                    startID = event.eventPosition
                )
            )
            bolusID = insightDbHelper.getInsightBolusID(serial, event.bolusID, timestamp)
        }
        bolusID?.let { insightBolusID ->
            insightBolusID.startID = event.eventPosition
            insightDbHelper.createOrUpdate(insightBolusID)
            if (event.bolusType == BolusType.STANDARD || event.bolusType == BolusType.MULTIWAVE) {
                pumpSync.syncBolusWithPumpId(
                    timestamp = timestamp,
                    amount = event.immediateAmount,
                    type = null,
                    pumpId = insightBolusID.id,
                    pumpType = PumpType.ACCU_CHEK_INSIGHT,
                    pumpSerial = serial
                )
            }
            if (event.bolusType == BolusType.EXTENDED || event.bolusType == BolusType.MULTIWAVE) {
                if (profileFunction.getProfile(insightBolusID.timestamp) != null) pumpSync.syncExtendedBolusWithPumpId(
                    timestamp = timestamp,
                    amount = event.extendedAmount,
                    duration = T.mins(event.duration.toLong()).msecs(),
                    isEmulatingTB = isFakingTempsByExtendedBoluses,
                    pumpId = insightBolusID.id,
                    pumpType = PumpType.ACCU_CHEK_INSIGHT,
                    pumpSerial = serial
                )
            }
        }
    }

    private fun processBolusDeliveredEvent(serial: String, event: BolusDeliveredEvent) {
        val timestamp = parseDate(
            event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond
        ) + timeOffset
        val startTimestamp = parseRelativeDate(
            event.eventYear, event.eventMonth, event.eventDay, event.eventHour,
            event.eventMinute, event.eventSecond, event.startHour, event.startMinute, event.startSecond
        ) + timeOffset
        var bolusID = insightDbHelper.getInsightBolusID(serial, event.bolusID, timestamp)
        if (bolusID == null || bolusID.endID != null) {
            bolusID = InsightBolusID(
                timestamp = startTimestamp,
                pumpSerial = serial,
                bolusID = event.bolusID,
                startID = bolusID?.startID ?: event.eventPosition,
                endID = event.eventPosition
            )
        }
        bolusID.endID = event.eventPosition
        insightDbHelper.createOrUpdate(bolusID)
        insightDbHelper.getInsightBolusID(serial, event.bolusID, startTimestamp)?.also { insightBolusID ->
            if (event.bolusType == BolusType.STANDARD || event.bolusType == BolusType.MULTIWAVE) {
                pumpSync.syncBolusWithPumpId(
                    timestamp = insightBolusID.timestamp,
                    amount = event.immediateAmount,
                    type = null,
                    pumpId = insightBolusID.id,
                    pumpType = PumpType.ACCU_CHEK_INSIGHT,
                    pumpSerial = serial
                )
                lastBolusTimestamp = insightBolusID.timestamp
                preferences.put(InsightLongNonKey.LastBolusTimestamp, lastBolusTimestamp)
                lastBolusAmount = event.immediateAmount
                preferences.put(InsightDoubleNonKey.LastBolusAmount, lastBolusAmount)
            }
            if (event.bolusType == BolusType.EXTENDED || event.bolusType == BolusType.MULTIWAVE) {
                if (event.duration > 0 && profileFunction.getProfile(insightBolusID.timestamp) != null) pumpSync.syncExtendedBolusWithPumpId(
                    timestamp = insightBolusID.timestamp,
                    amount = event.extendedAmount,
                    duration = timestamp - startTimestamp,
                    isEmulatingTB = isFakingTempsByExtendedBoluses,
                    pumpId = insightBolusID.id,
                    pumpType = PumpType.ACCU_CHEK_INSIGHT,
                    pumpSerial = serial
                )
            }
        }
    }

    private fun processOccurrenceOfAlertEvent(event: OccurrenceOfAlertEvent) {
        if (!preferences.get(InsightBooleanKey.LogAlerts)) return
        val timestamp = parseDate(
            event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond
        ) + timeOffset
        var code: Int? = null
        var title: Int? = null
        when (event.alertType) {
            AlertType.ERROR_6        -> {
                code = R.string.alert_e6_code
                title = R.string.alert_e6_title
            }

            AlertType.ERROR_10       -> {
                code = R.string.alert_e10_code
                title = R.string.alert_e10_title
            }

            AlertType.ERROR_13       -> {
                code = R.string.alert_e13_code
                title = R.string.alert_e13_title
            }

            AlertType.MAINTENANCE_20 -> {
                code = R.string.alert_m20_code
                title = R.string.alert_m20_title
            }

            AlertType.MAINTENANCE_21 -> {
                code = R.string.alert_m21_code
                title = R.string.alert_m21_title
            }

            AlertType.MAINTENANCE_22 -> {
                code = R.string.alert_m22_code
                title = R.string.alert_m22_title
            }

            AlertType.MAINTENANCE_23 -> {
                code = R.string.alert_m23_code
                title = R.string.alert_m23_title
            }

            AlertType.MAINTENANCE_24 -> {
                code = R.string.alert_m24_code
                title = R.string.alert_m24_title
            }

            AlertType.MAINTENANCE_25 -> {
                code = R.string.alert_m25_code
                title = R.string.alert_m25_title
            }

            AlertType.MAINTENANCE_26 -> {
                code = R.string.alert_m26_code
                title = R.string.alert_m26_title
            }

            AlertType.MAINTENANCE_27 -> {
                code = R.string.alert_m27_code
                title = R.string.alert_m27_title
            }

            AlertType.MAINTENANCE_28 -> {
                code = R.string.alert_m28_code
                title = R.string.alert_m28_title
            }

            AlertType.MAINTENANCE_29 -> {
                code = R.string.alert_m29_code
                title = R.string.alert_m29_title
            }

            AlertType.MAINTENANCE_30 -> {
                code = R.string.alert_m30_code
                title = R.string.alert_m30_title
            }

            AlertType.WARNING_31     -> {
                code = R.string.alert_w31_code
                title = R.string.alert_w31_title
            }

            AlertType.WARNING_32     -> {
                code = R.string.alert_w32_code
                title = R.string.alert_w32_title
            }

            AlertType.WARNING_33     -> {
                code = R.string.alert_w33_code
                title = R.string.alert_w33_title
            }

            AlertType.WARNING_34     -> {
                code = R.string.alert_w34_code
                title = R.string.alert_w34_title
            }

            AlertType.WARNING_39     -> {
                code = R.string.alert_w39_code
                title = R.string.alert_w39_title
            }

            else                     -> Unit
        }
        if (code != null && title != null) logNote(timestamp, rh.gs(R.string.insight_alert_formatter, rh.gs(code), rh.gs(title)))
    }

    private fun parseDate(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar[Calendar.YEAR] = year
        calendar[Calendar.MONTH] = month - 1
        calendar[Calendar.DAY_OF_MONTH] = day
        calendar[Calendar.HOUR_OF_DAY] = hour
        calendar[Calendar.MINUTE] = minute
        calendar[Calendar.SECOND] = second
        return calendar.timeInMillis
    }

    private fun logNote(date: Long, note: String) {
        pumpSync.insertTherapyEventIfNewWithTimestamp(date, TE.Type.NOTE, note, null, PumpType.ACCU_CHEK_INSIGHT, serialNumber())
    }

    private fun parseRelativeDate(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, relativeHour: Int, relativeMinute: Int, relativeSecond: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar[Calendar.YEAR] = year
        calendar[Calendar.MONTH] = month - 1
        calendar[Calendar.DAY_OF_MONTH] = day
        calendar[Calendar.HOUR_OF_DAY] = relativeHour
        calendar[Calendar.MINUTE] = relativeMinute
        calendar[Calendar.SECOND] = relativeSecond
        return calendar.timeInMillis - if (relativeHour * 60 * 60 + relativeMinute * 60 + relativeSecond >= hour * 60 * 60 + minute * 60 + second) T.days(1).msecs() else 0
    }

    private fun uploadCareportalEvent(date: Long, event: TE.Type) {
        pumpSync.insertTherapyEventIfNewWithTimestamp(date, event, null, null, PumpType.ACCU_CHEK_INSIGHT, serialNumber())
    }

    override fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> {
        percentRate.setIfGreater(0, rh.gs(app.aaps.core.ui.R.string.limitingpercentrate, 0, rh.gs(app.aaps.core.ui.R.string.itmustbepositivevalue)), this)
        percentRate.setIfSmaller(
            pumpDescription.maxTempPercent, rh.gs(app.aaps.core.ui.R.string.limitingpercentrate, pumpDescription.maxTempPercent, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this
        )
        return percentRate
    }

    override fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        if (!limitsFetched) return insulin
        insulin.setIfSmaller(maximumBolusAmount, rh.gs(app.aaps.core.ui.R.string.limitingbolus, maximumBolusAmount, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
        if (insulin.value() < minimumBolusAmount) {

            //TODO: Add function to Constraints or use different approach
            // This only works if the interface of the InsightPlugin is called last.
            // If not, another constraint could theoretically set the value between 0 and minimumBolusAmount
            insulin.set(0.0, rh.gs(app.aaps.core.ui.R.string.limitingbolus, minimumBolusAmount, rh.gs(app.aaps.core.ui.R.string.pumplimit)), this)
        }
        return insulin
    }

    override fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        return applyBolusConstraints(insulin)
    }

    override fun onStateChanged(state: InsightState?) {
        if (state == InsightState.CONNECTED) {
            statusLoaded = false
            rxBus.send(EventDismissNotification(Notification.INSIGHT_TIMEOUT_DURING_HANDSHAKE))
        } else if (state == InsightState.NOT_PAIRED) {
            connectionService?.withdrawConnectionRequest(this)
            statusLoaded = false
            profileBlocks = null
            operatingMode = null
            batteryStatus = null
            cartridgeStatus = null
            totalDailyDose = null
            activeBasalRate = null
            activeTBR = null
            activeBoluses = null
            tBROverNotificationBlock = null
            rxBus.send(EventRefreshOverview("LocalInsightPlugin::onStateChanged", false))
        }
        rxBus.send(EventLocalInsightUpdateGUI())
    }

    override fun onPumpPaired() {
        commandQueue.readStatus("Pump paired", null)
    }

    override fun onTimeoutDuringHandshake() {
        val notification = Notification(Notification.INSIGHT_TIMEOUT_DURING_HANDSHAKE, rh.gs(R.string.timeout_during_handshake), Notification.URGENT)
        rxBus.send(EventNewNotification(notification))
    }

    override fun canHandleDST(): Boolean {
        return true
    }

    override fun clearAllTables() {
        insightDatabase.clearAllTables()
    }

    override fun addPreferenceScreen(preferenceManager: PreferenceManager, parent: PreferenceScreen, context: Context, requiredKey: String?) {
        if (requiredKey != null) return

        // val speedEntries = arrayOf<CharSequence>("12 s/U", "30 s/U", "60 s/U")
        // val speedValues = arrayOf<CharSequence>("0", "1", "2")

        val category = PreferenceCategory(context)
        parent.addPreference(category)
        category.apply {
            key = "insight_settings"
            title = rh.gs(R.string.insight_local)
            initialExpandedChildrenCount = 0
            addPreference(
                AdaptiveIntentPreference(
                    ctx = context, intentKey = InsightIntentKey.InsightPairing, title = R.string.insight_pairing,
                    intent = Intent().setComponent(ComponentName(context, InsightPairingInformationActivity::class.java)),
                )
            )
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = InsightBooleanKey.LogReservoirChanges, title = R.string.log_reservoir_changes))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = InsightBooleanKey.LogTubeChanges, title = R.string.log_tube_changes))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = InsightBooleanKey.LogSiteChanges, title = R.string.log_site_changes))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = InsightBooleanKey.LogBatteryChanges, title = R.string.log_battery_changes))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = InsightBooleanKey.LogOperatingModeChanges, title = R.string.log_operating_mode_changes))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = InsightBooleanKey.LogAlerts, title = R.string.log_alerts))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = InsightBooleanKey.EnableTbrEmulation, title = R.string.enable_tbr_emulation, summary = R.string.enable_tbr_emulation_summary))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = InsightBooleanKey.DisableVibration, title = R.string.disable_vibration, summary = R.string.disable_vibration_summary))
            addPreference(AdaptiveSwitchPreference(ctx = context, booleanKey = InsightBooleanKey.DisableVibrationAuto, title = R.string.disable_vibration_auto, summary = R.string.disable_vibration_auto_summary))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = InsightIntKey.MinRecoveryDuration, title = R.string.min_recovery_duration))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = InsightIntKey.MaxRecoveryDuration, title = R.string.max_recovery_duration))
            addPreference(AdaptiveIntPreference(ctx = context, intKey = InsightIntKey.DisconnectDelay, title = R.string.disconnect_delay))
        }
    }

    companion object {

        const val ALERT_CHANNEL_ID = "AAPS-InsightAlert"
    }

}