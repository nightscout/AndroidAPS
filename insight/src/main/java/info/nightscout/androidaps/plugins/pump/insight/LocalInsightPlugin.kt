package info.nightscout.androidaps.plugins.pump.insight

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.events.EventInitializationChanged
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.extensions.convertedToAbsolute
import info.nightscout.androidaps.extensions.plannedRemainingMinutes
import info.nightscout.androidaps.insight.R
import info.nightscout.androidaps.insight.database.InsightBolusID
import info.nightscout.androidaps.insight.database.InsightDbHelper
import info.nightscout.androidaps.insight.database.InsightHistoryOffset
import info.nightscout.androidaps.insight.database.InsightPumpID
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.interfaces.Profile.ProfileValue
import info.nightscout.androidaps.interfaces.PumpSync.PumpState.TemporaryBasal
import info.nightscout.androidaps.interfaces.PumpSync.TemporaryBasalType
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.common.ManufacturerType
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.insight.app_layer.Service
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.HistoryReadingDirection
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.ReadHistoryEventsMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.StartReadingHistoryMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.StopReadingHistoryMessage
import info.nightscout.androidaps.plugins.pump.insight.app_layer.history.history_events.*
import info.nightscout.androidaps.plugins.pump.insight.app_layer.parameter_blocks.*
import info.nightscout.androidaps.plugins.pump.insight.app_layer.remote_control.*
import info.nightscout.androidaps.plugins.pump.insight.app_layer.status.*
import info.nightscout.androidaps.plugins.pump.insight.connection_service.InsightConnectionService
import info.nightscout.androidaps.plugins.pump.insight.descriptors.*
import info.nightscout.androidaps.plugins.pump.insight.events.EventLocalInsightUpdateGUI
import info.nightscout.androidaps.plugins.pump.insight.exceptions.InsightException
import info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors.AppLayerErrorException
import info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors.NoActiveTBRToCanceLException
import info.nightscout.androidaps.plugins.pump.insight.utils.ExceptionTranslator
import info.nightscout.androidaps.plugins.pump.insight.utils.ParameterBlockUtil
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T.Companion.mins
import info.nightscout.androidaps.utils.T.Companion.days
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONException
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

@Singleton
class LocalInsightPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    rh: ResourceHelper,
    private val sp: SP,
    commandQueue: CommandQueue,
    private val profileFunction: ProfileFunction,
    private val context: Context,
    config: Config,
    private val dateUtil: DateUtil,
    private val insightDbHelper: InsightDbHelper,
    private val pumpSync: PumpSync
) : PumpPluginBase(PluginDescription()
    .pluginIcon(R.drawable.ic_insight_128)
    .pluginName(R.string.insight_local)
    .shortName(R.string.insightpump_shortname)
    .mainType(PluginType.PUMP)
    .description(R.string.description_pump_insight_local)
    .fragmentClass(LocalInsightFragment::class.java.name)
    .preferencesId(if (config.APS) R.xml.pref_insight_local_full else R.xml.pref_insight_local_pumpcontrol),
    injector, aapsLogger, rh, commandQueue
), Pump, Constraints, InsightConnectionService.StateCallback {

    override val pumpDescription: PumpDescription = PumpDescription().also { it.fillFor(PumpType.ACCU_CHEK_INSIGHT) }
    private var alertService: InsightAlertService? = null
    var connectionService: InsightConnectionService? = null
        private set
    private var timeOffset: Long = 0
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (binder is InsightConnectionService.LocalBinder) {
                connectionService = binder.service
                connectionService?.registerStateCallback(this@LocalInsightPlugin)
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
    private val _bolusLock: Any = arrayOfNulls<Any>(0)
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
    var lastBolusAmount = 0.0
        private set
    var lastBolusTimestamp = 0L
        private set

    override fun onStart() {
        super.onStart()
        context.bindService(Intent(context, InsightConnectionService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        context.bindService(Intent(context, InsightAlertService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        createNotificationChannel()
        lastBolusTimestamp = sp.getLong(R.string.key_insight_last_bolus_timestamp, 0L)
        lastBolusAmount = sp.getDouble(R.string.key_insight_last_bolus_amount, 0.0)
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
        return connectionService?.let { alertService != null && it.isPaired } ?: false
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
        if (connectionService == null || alertService == null || !connectionService!!.hasRequestedConnection(this)) return false
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
        try {
            tBROverNotificationBlock = connectionService?.let { ParameterBlockUtil.readParameterBlock(it, Service.CONFIGURATION, TBROverNotificationBlock::class.java) } // TODO resolve !!
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

    @Throws(Exception::class) private fun updatePumpTimeIfNeeded() {
        val pumpTime = connectionService?.run { requestMessage(GetDateTimeMessage()).await().pumpTime }
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
                connectionService?.run { requestMessage(setDateTimeMessage).await() }
                val notification = Notification(Notification.INSIGHT_DATE_TIME_UPDATED, rh.gs(R.string.pump_time_updated), Notification.INFO, 60)
                rxBus.send(EventNewNotification(notification))
            }
        }
    }

    @Throws(Exception::class) private fun fetchBasalProfile() {
        activeBasalProfile = ParameterBlockUtil.readParameterBlock(connectionService!!, Service.CONFIGURATION, ActiveBRProfileBlock::class.java)!!.activeBasalProfile // TODO resolve !!
        profileBlocks = ParameterBlockUtil.readParameterBlock(connectionService!!, Service.CONFIGURATION, BRProfile1Block::class.java)!!.profileBlocks // TODO resolve !!
    }

    @Throws(Exception::class) private fun fetchStatus() {
        if (statusLoaded) {
            val registerMessage = connectionService?.run { requestMessage(GetPumpStatusRegisterMessage()).await() }
                ?: return
            val resetMessage = ResetPumpStatusRegisterMessage(
                        operatingModeChanged = registerMessage.isOperatingModeChanged,
                        batteryStatusChanged = registerMessage.isBatteryStatusChanged,
                        cartridgeStatusChanged = registerMessage.isCartridgeStatusChanged,
                        totalDailyDoseChanged = registerMessage.isTotalDailyDoseChanged,
                        activeTBRChanged = registerMessage.isActiveTBRChanged,
                        activeBolusesChanged = registerMessage.isActiveBolusesChanged
                    )

            connectionService?.run { requestMessage(resetMessage).await() }
            registerMessage.run {
                if (isOperatingModeChanged) operatingMode = connectionService?.run { requestMessage(GetOperatingModeMessage()).await().operatingMode }
                if (isBatteryStatusChanged) batteryStatus = connectionService?.run { requestMessage(GetBatteryStatusMessage()).await().batteryStatus }
                if (isCartridgeStatusChanged) cartridgeStatus = connectionService?.run { requestMessage(GetCartridgeStatusMessage()).await().cartridgeStatus }
                if (isTotalDailyDoseChanged) totalDailyDose = connectionService?.run { requestMessage(GetTotalDailyDoseMessage()).await().tDD }
                if (operatingMode == OperatingMode.STARTED) {
                    if (isActiveBasalRateChanged) activeBasalRate = connectionService?.run { requestMessage(GetActiveBasalRateMessage()).await().activeBasalRate }
                    if (isActiveTBRChanged) activeTBR = connectionService?.run { requestMessage(GetActiveTBRMessage()).await().activeTBR }
                    if (isActiveBolusesChanged) activeBoluses = connectionService?.run { requestMessage(GetActiveBolusesMessage()).await().activeBoluses }
                } else {
                    activeBasalRate = null
                    activeTBR = null
                    activeBoluses = null
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
            connectionService?.run { requestMessage(resetMessage).await() }
            connectionService?.run {
                operatingMode = requestMessage(GetOperatingModeMessage()).await().operatingMode
                batteryStatus = requestMessage(GetBatteryStatusMessage()).await().batteryStatus
                cartridgeStatus = requestMessage(GetCartridgeStatusMessage()).await().cartridgeStatus
                totalDailyDose = requestMessage(GetTotalDailyDoseMessage()).await().tDD
                if (operatingMode == OperatingMode.STARTED) {
                    activeBasalRate = requestMessage(GetActiveBasalRateMessage()).await().activeBasalRate
                    activeTBR = requestMessage(GetActiveTBRMessage()).await().activeTBR
                    activeBoluses = requestMessage(GetActiveBolusesMessage()).await().activeBoluses
                } else {
                    activeBasalRate = null
                    activeTBR = null
                    activeBoluses = null
                }
            }
            statusLoaded = true
        }
        Handler(Looper.getMainLooper()).post {
            rxBus.send(EventLocalInsightUpdateGUI())
            rxBus.send(EventRefreshOverview("LocalInsightPlugin::fetchStatus", false))
        }
    }

    @Throws(Exception::class) private fun fetchLimitations() {
        connectionService!!.let { service -> // TODO resolve !!:
            maximumBolusAmount = ParameterBlockUtil.readParameterBlock(service, Service.CONFIGURATION, MaxBolusAmountBlock::class.java)!!.amountLimitation
            val maximumBasalAmount = ParameterBlockUtil.readParameterBlock(service, Service.CONFIGURATION, MaxBasalAmountBlock::class.java)!!.amountLimitation
            minimumBolusAmount = ParameterBlockUtil.readParameterBlock(service, Service.CONFIGURATION, FactoryMinBolusAmountBlock::class.java)!!.amountLimitation
            val minimumBasalAmount = ParameterBlockUtil.readParameterBlock(service, Service.CONFIGURATION, FactoryMinBasalAmountBlock::class.java)!!.amountLimitation
            pumpDescription.basalMaximumRate = maximumBasalAmount
            pumpDescription.basalMinimumRate = minimumBasalAmount
            limitsFetched = true
        }
    }

    override fun setNewBasalProfile(profile: Profile): PumpEnactResult {
        val result = PumpEnactResult(injector)
        rxBus.send(EventDismissNotification(Notification.PROFILE_NOT_SET_NOT_INITIALIZED))
        val profileBlocks: MutableList<BasalProfileBlock> = ArrayList()
        for (i in profile.getBasalValues().indices) {
            val basalValue = profile.getBasalValues()[i]
            var nextValue: ProfileValue? = null
            if (profile.getBasalValues().size > i + 1) nextValue = profile.getBasalValues()[i + 1]
            val profileBlock = BasalProfileBlock()
            profileBlock.basalAmount = if (basalValue.value > 5) (basalValue.value / 0.1).roundToLong() * 0.1 else (basalValue.value / 0.01).roundToLong() * 0.01
            profileBlock.duration = ((nextValue?.timeAsSeconds
                ?: 24 * 60 * 60) - basalValue.timeAsSeconds) / 60
            profileBlocks.add(profileBlock)
        }
        try {
            val activeBRProfileBlock = ActiveBRProfileBlock()
            activeBRProfileBlock.activeBasalProfile = BasalProfile.PROFILE_1
            ParameterBlockUtil.writeConfigurationBlock(connectionService!!, activeBRProfileBlock) // TODO resolve !!
            activeBasalProfile = BasalProfile.PROFILE_1
            val profileBlock: BRProfileBlock = BRProfile1Block()
            profileBlock.profileBlocks = profileBlocks
            ParameterBlockUtil.writeConfigurationBlock(connectionService!!, profileBlock) // TODO resolve !!
            rxBus.send(EventDismissNotification(Notification.FAILED_UPDATE_PROFILE))
            val notification = Notification(Notification.PROFILE_SET_OK, rh.gs(R.string.profile_set_ok), Notification.INFO, 60)
            rxBus.send(EventNewNotification(notification))
            result.success(true)
                .enacted(true)
                .comment(R.string.virtualpump_resultok)
            this.profileBlocks = profileBlocks
            try {
                fetchStatus()
            } catch (ignored: Exception) {
            }
        } catch (e: AppLayerErrorException) {
            aapsLogger.info(LTag.PUMP, "Exception while setting profile: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
            val notification = Notification(Notification.FAILED_UPDATE_PROFILE, rh.gs(R.string.failedupdatebasalprofile), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
            result.comment(ExceptionTranslator.getString(context, e))
        } catch (e: InsightException) {
            aapsLogger.info(LTag.PUMP, "Exception while setting profile: " + e.javaClass.canonicalName)
            val notification = Notification(Notification.FAILED_UPDATE_PROFILE, rh.gs(R.string.failedupdatebasalprofile), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
            result.comment(ExceptionTranslator.getString(context, e))
        } catch (e: Exception) {
            aapsLogger.error("Exception while setting profile", e)
            val notification = Notification(Notification.FAILED_UPDATE_PROFILE, rh.gs(R.string.failedupdatebasalprofile), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
            result.comment(ExceptionTranslator.getString(context, e))
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
                var nextValue: ProfileValue? = null
                if (profile.getBasalValues().size > i + 1) nextValue = profile.getBasalValues()[i + 1]
                if (profileBlock.duration * 60 != (nextValue?.timeAsSeconds
                        ?: 24 * 60 * 60) - basalValue.timeAsSeconds) return false
                if (abs(profileBlock.basalAmount - basalValue.value) > (if (basalValue.value > 5) 0.051 else 0.0051)) return false
            }
        }
        return true
    }

    override fun lastDataTime(): Long {
        return if (connectionService == null || alertService == null) dateUtil.now() else connectionService!!.lastDataTime
    }

    override val baseBasalRate: Double
        get() {
            if (connectionService == null || alertService == null) return 0.0
            return activeBasalRate?.activeBasalRate  ?: 0.0
        }
    override val reservoirLevel: Double
        get() = cartridgeStatus?.remainingAmount ?: 0.0
    override val batteryLevel: Int
        get() = batteryStatus?.batteryAmount ?: 0

    override fun deliverTreatment(detailedBolusInfo: DetailedBolusInfo): PumpEnactResult {
        if (detailedBolusInfo.insulin.equals(0.0) || detailedBolusInfo.carbs > 0) {
            throw IllegalArgumentException(detailedBolusInfo.toString(), Exception())
        }
        val result = PumpEnactResult(injector)
        val insulin = (detailedBolusInfo.insulin / 0.01).roundToInt() * 0.01
        if (insulin > 0) {
            try {
                synchronized(_bolusLock) {
                    val bolusMessage = DeliverBolusMessage()
                    bolusMessage.bolusType = BolusType.STANDARD
                    bolusMessage.duration = 0
                    bolusMessage.extendedAmount = 0.0
                    bolusMessage.immediateAmount = insulin
                    bolusMessage.disableVibration = sp.getBoolean(if (detailedBolusInfo.bolusType === DetailedBolusInfo.BolusType.SMB) R.string.key_insight_disable_vibration_auto else R.string.key_insight_disable_vibration, false)
                    bolusID = connectionService!!.requestMessage(bolusMessage).await().bolusId
                    bolusCancelled = false
                }
                result.success(true).enacted(true)
                val t = EventOverviewBolusProgress.Treatment(0.0, 0, detailedBolusInfo.bolusType === DetailedBolusInfo.BolusType.SMB, detailedBolusInfo.id)
                val bolusingEvent = EventOverviewBolusProgress
                bolusingEvent.t = t
                bolusingEvent.status = rh.gs(R.string.insight_delivered, 0.0, insulin)
                bolusingEvent.percent = 0
                rxBus.send(bolusingEvent)
                var trials = 0
                val now = dateUtil.now()
                val serial = serialNumber()
                insightDbHelper.createOrUpdate(InsightBolusID(
                    now,
                    serial,
                    bolusID,
                    null,
                    null
                ))
                val insightBolusID = insightDbHelper.getInsightBolusID(serial, bolusID, now)
                pumpSync.syncBolusWithPumpId(
                    insightBolusID!!.timestamp,
                    detailedBolusInfo.insulin,
                    detailedBolusInfo.bolusType,
                    insightBolusID.id,
                    PumpType.ACCU_CHEK_INSIGHT,
                    serialNumber())
                while (!bolusCancelled) {
                    val operatingMode = connectionService!!.requestMessage(GetOperatingModeMessage()).await().operatingMode
                    if (operatingMode != OperatingMode.STARTED) break
                    val activeBoluses = connectionService!!.requestMessage(GetActiveBolusesMessage()).await().activeBoluses
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
                        val percentBefore = bolusingEvent.percent
                        bolusingEvent.percent = (100.0 / activeBolus.initialAmount * (activeBolus.initialAmount - activeBolus.remainingAmount)).toInt()
                        bolusingEvent.status = rh.gs(R.string.insight_delivered, activeBolus.initialAmount - activeBolus.remainingAmount, activeBolus.initialAmount)
                        if (percentBefore != bolusingEvent.percent) rxBus.send(bolusingEvent)
                    } else {
                        synchronized(_bolusLock) {
                            if (bolusCancelled || trials == -1 || trials++ >= 5) {
                                if (!bolusCancelled) {
                                    bolusingEvent.status = rh.gs(R.string.insight_delivered, insulin, insulin)
                                    bolusingEvent.percent = 100
                                    rxBus.send(bolusingEvent)
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
        return result
    }

    override fun stopBolusDelivering() {
        Thread {
            try {
                synchronized(_bolusLock) {
                    alertService?.ignore(AlertType.WARNING_38)
                    val cancelBolusMessage = CancelBolusMessage()
                    cancelBolusMessage.bolusID = bolusID
                    connectionService!!.requestMessage(cancelBolusMessage).await()
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
        }.start()
    }

    override fun setTempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        val result = PumpEnactResult(injector)
        if (activeBasalRate == null || activeBasalRate?.activeBasalRate == 0.0) return result
        val percent = 100.0 / activeBasalRate!!.activeBasalRate * absoluteRate
        if (isFakingTempsByExtendedBoluses) {
            val cancelEBResult = cancelExtendedBolusOnly()
            if (cancelEBResult.success) {
                if (percent > 250) {
                    val cancelTBRResult = cancelTempBasalOnly()
                    if (cancelTBRResult.success) {
                        val ebResult = setExtendedBolusOnly((absoluteRate - baseBasalRate) / 60.0
                            * durationInMinutes.toDouble(), durationInMinutes,
                            sp.getBoolean(R.string.key_insight_disable_vibration_auto, false))
                        if (ebResult.success) {
                            result.success(true)
                                .enacted(true)
                                .isPercent(false)
                                .absolute(absoluteRate)
                                .duration(durationInMinutes)
                                .comment(R.string.virtualpump_resultok)
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
        return result
    }

    override fun setTempBasalPercent(percent: Int, durationInMinutes: Int, profile: Profile, enforceNew: Boolean, tbrType: TemporaryBasalType): PumpEnactResult {
        val result = PumpEnactResult(injector)
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
                .comment(R.string.virtualpump_resultok)
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
        if (result.success) result = setExtendedBolusOnly(insulin, durationInMinutes, sp.getBoolean(R.string.key_insight_disable_vibration, false))
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

    fun setExtendedBolusOnly(insulin: Double?, durationInMinutes: Int?, disableVibration: Boolean): PumpEnactResult {
        val result = PumpEnactResult(injector)
        try {
            val bolusMessage = DeliverBolusMessage()
            bolusMessage.bolusType = BolusType.EXTENDED
            bolusMessage.duration = durationInMinutes ?: 0
            bolusMessage.extendedAmount = insulin ?: 0.0
            bolusMessage.immediateAmount = 0.0
            bolusMessage.disableVibration = disableVibration
            val bolusID = connectionService!!.requestMessage(bolusMessage).await().bolusId
            insightDbHelper.createOrUpdate(InsightBolusID(
                timestamp = dateUtil.now(),
                pumpSerial = serialNumber(),
                bolusID = bolusID
            ))
            result.success(true).enacted(true).comment(R.string.virtualpump_resultok)
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
        return result
    }

    override fun cancelTempBasal(enforceNew: Boolean): PumpEnactResult {
        val result = PumpEnactResult(injector)
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
        val result = PumpEnactResult(injector)
        try {
            alertService?.ignore(AlertType.WARNING_36)
            connectionService!!.requestMessage(CancelTBRMessage()).await()
            result.success(true)
                .enacted(true)
                .isTempCancel(true)
            confirmAlert(AlertType.WARNING_36)
            alertService?.ignore(null)
            result.comment(R.string.virtualpump_resultok)
        } catch (e: NoActiveTBRToCanceLException) {
            result.success(true)
            result.comment(R.string.virtualpump_resultok)
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
        val result = PumpEnactResult(injector)
        try {
            for (activeBolus in activeBoluses!!) {
                if (activeBolus.bolusType == BolusType.EXTENDED || activeBolus.bolusType == BolusType.MULTIWAVE) {
                    alertService!!.ignore(AlertType.WARNING_38)
                    val cancelBolusMessage = CancelBolusMessage()
                    cancelBolusMessage.bolusID = activeBolus.bolusID
                    connectionService!!.requestMessage(cancelBolusMessage).await()
                    confirmAlert(AlertType.WARNING_38)
                    alertService!!.ignore(null)
                    val insightBolusID = insightDbHelper.getInsightBolusID(serialNumber(), activeBolus.bolusID, dateUtil.now())
                    if (insightBolusID != null) {
                        result.enacted(true).success(true)
                    }
                }
            }
            result.success(true).comment(R.string.virtualpump_resultok)
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
        return result
    }

    private fun confirmAlert(alertType: AlertType) {
        try {
            val started = dateUtil.now()
            var continueLoop = true                                             //Todo improve this
            while (dateUtil.now() - started < 10000 && continueLoop) {
                connectionService?.run {
                    val activeAlertMessage = requestMessage(GetActiveAlertMessage()).await()
                    activeAlertMessage.alert?.let {
                        if (it.alertType == alertType) {
                            val confirmMessage = ConfirmAlertMessage()
                            confirmMessage.alertID = it.alertId
                            requestMessage(confirmMessage).await()
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

    override fun getJSONStatus(profile: Profile, profileName: String, version: String): JSONObject {
        val now = dateUtil.now()
        if (connectionService == null) return JSONObject()
        if (dateUtil.now() - connectionService!!.lastConnected > 60 * 60 * 1000) {
            return JSONObject()
        }
        val pump = JSONObject()
        val battery = JSONObject()
        val status = JSONObject()
        val extended = JSONObject()
        try {
            status.put("timestamp", dateUtil.toISOString(connectionService!!.lastConnected))
            extended.put("Version", version)
            try {
                extended.put("ActiveProfile", profileFunction.getProfileName())
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val tb = pumpSync.expectedPumpState().temporaryBasal
            if (tb != null) {
                extended.put("TempBasalAbsoluteRate", tb.convertedToAbsolute(now, profile))
                extended.put("TempBasalStart", dateUtil.dateAndTimeString(tb.timestamp))
                extended.put("TempBasalRemaining", tb.plannedRemainingMinutes)
            }
            val eb = pumpSync.expectedPumpState().extendedBolus
            if (eb != null) {
                extended.put("ExtendedBolusAbsoluteRate", eb.rate)
                extended.put("ExtendedBolusStart", dateUtil.dateAndTimeString(eb.timestamp))
                extended.put("ExtendedBolusRemaining", eb.plannedRemainingMinutes)
            }
            extended.put("BaseBasalRate", baseBasalRate)
            status.put("timestamp", dateUtil.toISOString(now))
            pump.put("extended", extended)
            if (statusLoaded) {
                status.put("status", if (operatingMode != OperatingMode.STARTED) "suspended" else "normal")
                pump.put("status", status)
                battery.put("percent", batteryStatus!!.batteryAmount)
                pump.put("battery", battery)
                pump.put("reservoir", cartridgeStatus!!.remainingAmount)
            }
            pump.put("clock", dateUtil.toISOString(now))
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return pump
    }

    override fun manufacturer(): ManufacturerType {
        return ManufacturerType.Roche
    }

    override fun model(): PumpType {
        return PumpType.ACCU_CHEK_INSIGHT
    }

    override fun serialNumber(): String {
        return if (connectionService == null || alertService == null) "Unknown" else connectionService!!.pumpSystemIdentification?.serialNumber!!
    }

    fun stopPump(): PumpEnactResult {
        val result = PumpEnactResult(injector)
        try {
            val operatingModeMessage = SetOperatingModeMessage()
            operatingModeMessage.operatingMode = OperatingMode.STOPPED
            connectionService!!.requestMessage(operatingModeMessage).await()
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
        return result
    }

    fun startPump(): PumpEnactResult {
        val result = PumpEnactResult(injector)
        try {
            val operatingModeMessage = SetOperatingModeMessage()
            operatingModeMessage.operatingMode = OperatingMode.STARTED
            connectionService!!.requestMessage(operatingModeMessage).await()
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
        return result
    }

    fun setTBROverNotification(enabled: Boolean): PumpEnactResult {
        val result = PumpEnactResult(injector)
        val valueBefore = tBROverNotificationBlock!!.isEnabled
        tBROverNotificationBlock!!.isEnabled = enabled
        try {
            ParameterBlockUtil.writeConfigurationBlock(connectionService!!, tBROverNotificationBlock) // TODO resolve !!
            result.success(true).enacted(true)
        } catch (e: AppLayerErrorException) {
            tBROverNotificationBlock!!.isEnabled = valueBefore
            aapsLogger.info(LTag.PUMP, "Exception while updating TBR notification block: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
            result.comment(ExceptionTranslator.getString(context, e))
        } catch (e: InsightException) {
            tBROverNotificationBlock!!.isEnabled = valueBefore
            aapsLogger.info(LTag.PUMP, "Exception while updating TBR notification block: " + e.javaClass.simpleName)
            result.comment(ExceptionTranslator.getString(context, e))
        } catch (e: Exception) {
            tBROverNotificationBlock!!.isEnabled = valueBefore
            aapsLogger.error("Exception while updating TBR notification block", e)
            result.comment(ExceptionTranslator.getString(context, e))
        }
        return result
    }

    override fun shortStatus(veryShort: Boolean): String {
        val ret = StringBuilder()
        connectionService?.run {
            if (lastConnected != 0L) {
                val agoMsec = dateUtil.now() - lastConnected
                val agoMin = (agoMsec / 60.0 / 1000.0).toInt()
                ret.append(rh.gs(R.string.short_status_last_connected, agoMin)).append("\n")
            }
            activeTBR?.let { ret.append(rh.gs(R.string.short_status_tbr, it.percentage, it.initialDuration - it.remainingDuration, it.initialDuration)).append("\n") }
            activeBoluses?.forEach {
                if (it.bolusType != BolusType.STANDARD)
                    ret.append(rh.gs(if (it.bolusType == BolusType.MULTIWAVE) R.string.short_status_multiwave else R.string.short_status_extended,
                        it.remainingAmount, it.initialAmount, it.remainingDuration)).append("\n")
            }
            if (!veryShort)
                totalDailyDose?.let { ret.append(rh.gs(R.string.short_status_tdd, it.bolusAndBasal)).append("\n") }
            cartridgeStatus?.let { ret.append(rh.gs(R.string.short_status_reservoir, it.remainingAmount)).append("\n") }
            batteryStatus?.let { ret.append(rh.gs(R.string.short_status_battery, it.batteryAmount)).append("\n") }
        }
        return ret.toString()
    }

    override val isFakingTempsByExtendedBoluses: Boolean
        get() = sp.getBoolean(R.string.key_insight_enable_tbr_emulation, false)

    override fun loadTDDs(): PumpEnactResult {
        return PumpEnactResult(injector).success(true)
    }

    private fun readHistory() {
        try {
            val pumpTime = connectionService?.run { requestMessage(GetDateTimeMessage()).await().pumpTime }
            val serial = serialNumber()
            pumpTime?.let {
                timeOffset = Calendar.getInstance(TimeZone.getTimeZone("UTC")).timeInMillis - parseDate(it.year,
                    it.month, it.day, it.hour, it.minute, it.second)
            }
            val historyOffset = insightDbHelper.getInsightHistoryOffset(serial)
            try {
                var historyEvents: MutableList<HistoryEvent> = ArrayList()
                if (historyOffset == null) {
                    val startMessage = StartReadingHistoryMessage()
                    startMessage.direction = HistoryReadingDirection.BACKWARD
                    startMessage.offset = -0x1
                    connectionService!!.requestMessage(startMessage).await()
                    historyEvents = connectionService!!.requestMessage(ReadHistoryEventsMessage()).await().historyEvents
                } else {
                    val startMessage = StartReadingHistoryMessage()
                    startMessage.direction = HistoryReadingDirection.FORWARD
                    startMessage.offset = historyOffset.offset + 1
                    connectionService!!.requestMessage(startMessage).await()
                    while (true) {
                        val newEvents = connectionService!!.requestMessage(ReadHistoryEventsMessage()).await().historyEvents
                        if (newEvents.size == 0) break
                        historyEvents.addAll(newEvents)
                    }
                }
                historyEvents.sort()
                historyEvents.reverse()
                if (historyOffset != null) processHistoryEvents(serial, historyEvents)
                if (historyEvents.size > 0) {
                    insightDbHelper.createOrUpdate(InsightHistoryOffset(
                        serial,
                        historyEvents[0].eventPosition)
                    )
                }
            } catch (e: AppLayerErrorException) {
                aapsLogger.info(LTag.PUMP, "Exception while reading history: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
            } catch (e: InsightException) {
                aapsLogger.info(LTag.PUMP, "Exception while reading history: " + e.javaClass.simpleName)
            } catch (e: Exception) {
                aapsLogger.error("Exception while reading history", e)
            } finally {
                connectionService?.run { requestMessage(StopReadingHistoryMessage()).await() }
            }
        } catch (e: AppLayerErrorException) {
            aapsLogger.info(LTag.PUMP, "Exception while reading history: " + e.javaClass.canonicalName + " (" + e.errorCode + ")")
        } catch (e: InsightException) {
            aapsLogger.info(LTag.PUMP, "Exception while reading history: " + e.javaClass.simpleName)
        } catch (e: Exception) {
            aapsLogger.error("Exception while reading history", e)
        }
        Handler(Looper.getMainLooper()).post { rxBus.send(EventRefreshOverview("LocalInsightPlugin::readHistory", false)) }
    }

    private fun processHistoryEvents(serial: String, historyEvents: List<HistoryEvent?>) {
        val temporaryBasals: MutableList<TemporaryBasal> = ArrayList()
        val pumpStartedEvents: MutableList<InsightPumpID> = ArrayList()
        for (historyEvent in historyEvents) if (!processHistoryEvent(serial, temporaryBasals, pumpStartedEvents, historyEvent)) break
        temporaryBasals.reverse()
        for ((timestamp, _, pumpSerial, eventID) in pumpStartedEvents) {
            var stoppedEvent = insightDbHelper.getPumpStoppedEvent(pumpSerial!!, timestamp)
            if (stoppedEvent != null && stoppedEvent.eventType == InsightPumpID.EventType.PumpStopped) {             // Search if Stop event is after 15min of Pause
                val pauseEvent = insightDbHelper.getPumpStoppedEvent(pumpSerial, stoppedEvent.timestamp - mins(1).msecs())
                if (pauseEvent != null && pauseEvent.eventType == InsightPumpID.EventType.PumpPaused && stoppedEvent.timestamp - pauseEvent.timestamp < mins(16).msecs()) {
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
                pumpId = eventID)
            temporaryBasals.add(temporaryBasal)
        }
        temporaryBasals.sortWith { o1, o2 -> (o1.timestamp - o2.timestamp).toInt() }
        for (temporaryBasal in temporaryBasals) {
            if (temporaryBasal.duration == 0L) {                    // for Stop TBR event duration = 0L
                pumpSync.syncStopTemporaryBasalWithPumpId(
                    timestamp = temporaryBasal.timestamp,
                    endPumpId = temporaryBasal.pumpId!!,
                    pumpType = PumpType.ACCU_CHEK_INSIGHT,
                    pumpSerial = serial)
            }
            if (temporaryBasal.rate != 100.0) {
                pumpSync.syncTemporaryBasalWithPumpId(
                    timestamp = temporaryBasal.timestamp,
                    rate = temporaryBasal.rate,
                    duration = temporaryBasal.duration,
                    isAbsolute = temporaryBasal.isAbsolute,
                    type = temporaryBasal.type,
                    pumpId = temporaryBasal.pumpId!!,
                    pumpType = PumpType.ACCU_CHEK_INSIGHT,
                    pumpSerial = serial)
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
        if (!sp.getBoolean(R.string.key_insight_log_site_changes, false)) return
        val timestamp = parseDate(event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond) + timeOffset
        if (event.amount > 0.0)                 // Don't record event if amount is null => Fix Site Change with Insight v3 (event is always sent when Reservoir is changed)
            uploadCareportalEvent(timestamp, DetailedBolusInfo.EventType.CANNULA_CHANGE)
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
            pumpSerial = serial)
    }

    private fun processTubeFilledEvent(event: TubeFilledEvent) {
        if (!sp.getBoolean(R.string.key_insight_log_tube_changes, false)) return
        val timestamp = parseDate(event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond) + timeOffset
        if (event.amount > 0.0) // Don't record event if amount is null
            logNote(timestamp, rh.gs(R.string.tube_changed))
    }

    private fun processSniffingDoneEvent(event: SniffingDoneEvent) {
        if (!sp.getBoolean(R.string.key_insight_log_reservoir_changes, false)) return
        val timestamp = parseDate(event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond) + timeOffset
        uploadCareportalEvent(timestamp, DetailedBolusInfo.EventType.INSULIN_CHANGE)
    }

    private fun processPowerUpEvent(event: PowerUpEvent) {
        if (!sp.getBoolean(R.string.key_insight_log_battery_changes, false)) return
        val timestamp = parseDate(event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond) + timeOffset
        uploadCareportalEvent(timestamp, DetailedBolusInfo.EventType.PUMP_BATTERY_CHANGE)
    }

    private fun processOperatingModeChangedEvent(serial: String, pumpStartedEvents: MutableList<InsightPumpID>, event: OperatingModeChangedEvent) {
        val timestamp = parseDate(event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond) + timeOffset
        val pumpID = InsightPumpID(
            timestamp,
            InsightPumpID.EventType.None,
            serial,
            event.eventPosition)
        when (event.newValue) {
            OperatingMode.STARTED -> {
                pumpID.eventType = InsightPumpID.EventType.PumpStarted
                pumpStartedEvents.add(pumpID)
                if (sp.getBoolean("insight_log_operating_mode_changes", false)) logNote(timestamp, rh.gs(R.string.pump_started))
            }

            OperatingMode.STOPPED -> {
                pumpID.eventType = InsightPumpID.EventType.PumpStopped
                if (sp.getBoolean("insight_log_operating_mode_changes", false)) logNote(timestamp, rh.gs(R.string.pump_stopped))
            }

            OperatingMode.PAUSED  -> {
                pumpID.eventType = InsightPumpID.EventType.PumpPaused
                if (sp.getBoolean("insight_log_operating_mode_changes", false)) logNote(timestamp, rh.gs(R.string.pump_paused))
            }

            else                  -> Unit
        }
        insightDbHelper.createOrUpdate(pumpID)
    }

    private fun processStartOfTBREvent(serial: String, temporaryBasals: MutableList<TemporaryBasal>, event: StartOfTBREvent) {
        val timestamp = parseDate(event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond) + timeOffset
        insightDbHelper.createOrUpdate(InsightPumpID(
            timestamp = timestamp,
            eventType = InsightPumpID.EventType.StartOfTBR,
            pumpSerial = serial,
            eventID = event.eventPosition))
        temporaryBasals.add(TemporaryBasal(
            timestamp = timestamp,
            duration = mins(event.duration.toLong()).msecs(),
            rate = event.amount.toDouble(),
            isAbsolute = false,
            type = TemporaryBasalType.NORMAL,
            id = event.eventPosition,
            pumpId = event.eventPosition))
    }

    private fun processEndOfTBREvent(serial: String, temporaryBasals: MutableList<TemporaryBasal>, event: EndOfTBREvent) {
        val timestamp = parseDate(event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond) + timeOffset
        insightDbHelper.createOrUpdate(InsightPumpID(
            timestamp = timestamp - 1500L,
            eventType = InsightPumpID.EventType.EndOfTBR,
            pumpSerial = serial,
            eventID = event.eventPosition))
        temporaryBasals.add(TemporaryBasal(
            timestamp = timestamp - 1500L,
            duration = 0L,
            rate = 100.0,
            isAbsolute = false,
            type = TemporaryBasalType.NORMAL,
            id = event.eventPosition,
            pumpId = event.eventPosition))
    }

    private fun processBolusProgrammedEvent(serial: String, event: BolusProgrammedEvent) {
        val timestamp = parseDate(event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond) + timeOffset
        var bolusID = insightDbHelper.getInsightBolusID(serial, event.bolusID, timestamp)
        if (bolusID?.endID != null) {
            bolusID.startID = event.eventPosition
            insightDbHelper.createOrUpdate(bolusID)
            return
        }
        if (bolusID == null || bolusID.startID != null) {                        //In rare edge cases two boluses can share the same ID
            insightDbHelper.createOrUpdate(InsightBolusID(
                timestamp = timestamp,
                pumpSerial = serial,
                bolusID = event.bolusID,
                startID = event.eventPosition))
            bolusID = insightDbHelper.getInsightBolusID(serial, event.bolusID, timestamp)
        }
        bolusID!!.startID = event.eventPosition
        insightDbHelper.createOrUpdate(bolusID)
        if (event.bolusType == BolusType.STANDARD || event.bolusType == BolusType.MULTIWAVE) {
            pumpSync.syncBolusWithPumpId(
                timestamp = timestamp,
                amount = event.immediateAmount,
                type = null,
                pumpId = bolusID.id,
                pumpType = PumpType.ACCU_CHEK_INSIGHT,
                pumpSerial = serial)
        }
        if (event.bolusType == BolusType.EXTENDED || event.bolusType == BolusType.MULTIWAVE) {
            if (profileFunction.getProfile(bolusID.timestamp) != null) pumpSync.syncExtendedBolusWithPumpId(
                timestamp = timestamp,
                amount = event.extendedAmount,
                duration = mins(event.duration.toLong()).msecs(),
                isEmulatingTB = isFakingTempsByExtendedBoluses,
                pumpId = bolusID.id,
                pumpType = PumpType.ACCU_CHEK_INSIGHT,
                pumpSerial = serial)
        }
    }

    private fun processBolusDeliveredEvent(serial: String, event: BolusDeliveredEvent) {
        val timestamp = parseDate(event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond) + timeOffset
        val startTimestamp = parseRelativeDate(event.eventYear, event.eventMonth, event.eventDay, event.eventHour,
            event.eventMinute, event.eventSecond, event.startHour, event.startMinute, event.startSecond) + timeOffset
        var bolusID = insightDbHelper.getInsightBolusID(serial, event.bolusID, timestamp)
        if (bolusID == null || bolusID.endID != null) {
            bolusID = InsightBolusID(
                timestamp = startTimestamp,
                pumpSerial = serial,
                bolusID = event.bolusID,
                startID = bolusID?.startID ?: event.eventPosition,
                endID = event.eventPosition)
        }
        bolusID.endID = event.eventPosition
        insightDbHelper.createOrUpdate(bolusID)
        bolusID = insightDbHelper.getInsightBolusID(serial, event.bolusID, startTimestamp) // Line added to get id
        if (event.bolusType == BolusType.STANDARD || event.bolusType == BolusType.MULTIWAVE) {
            pumpSync.syncBolusWithPumpId(
                timestamp = bolusID!!.timestamp,
                amount = event.immediateAmount,
                type = null,
                pumpId = bolusID.id,
                pumpType = PumpType.ACCU_CHEK_INSIGHT,
                pumpSerial = serial)
            lastBolusTimestamp = bolusID.timestamp
            sp.putLong(R.string.key_insight_last_bolus_timestamp, lastBolusTimestamp)
            lastBolusAmount = event.immediateAmount
            sp.putDouble(R.string.key_insight_last_bolus_amount, lastBolusAmount)
        }
        if (event.bolusType == BolusType.EXTENDED || event.bolusType == BolusType.MULTIWAVE) {
            if (event.duration > 0 && profileFunction.getProfile(bolusID!!.timestamp) != null) pumpSync.syncExtendedBolusWithPumpId(
                timestamp = bolusID.timestamp,
                amount = event.extendedAmount,
                duration = timestamp - startTimestamp,
                isEmulatingTB = isFakingTempsByExtendedBoluses,
                pumpId = bolusID.id,
                pumpType = PumpType.ACCU_CHEK_INSIGHT,
                pumpSerial = serial)
        }
    }

    private fun processOccurrenceOfAlertEvent(event: OccurrenceOfAlertEvent) {
        if (!sp.getBoolean(R.string.key_insight_log_alerts, false)) return
        val timestamp = parseDate(event.eventYear, event.eventMonth, event.eventDay,
            event.eventHour, event.eventMinute, event.eventSecond) + timeOffset
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
        if (code != null) logNote(timestamp, rh.gs(R.string.insight_alert_formatter, rh.gs(code), rh.gs(title!!)))
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
        pumpSync.insertTherapyEventIfNewWithTimestamp(date, DetailedBolusInfo.EventType.NOTE, note, null, PumpType.ACCU_CHEK_INSIGHT, serialNumber())
    }

    private fun parseRelativeDate(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, relativeHour: Int, relativeMinute: Int, relativeSecond: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar[Calendar.YEAR] = year
        calendar[Calendar.MONTH] = month - 1
        calendar[Calendar.DAY_OF_MONTH] = day
        calendar[Calendar.HOUR_OF_DAY] = relativeHour
        calendar[Calendar.MINUTE] = relativeMinute
        calendar[Calendar.SECOND] = relativeSecond
        return calendar.timeInMillis - if (relativeHour * 60 * 60 + relativeMinute * 60 + relativeSecond >= hour * 60 * 60 + minute * 60 + second) days(1).msecs() else 0
    }

    private fun uploadCareportalEvent(date: Long, event: DetailedBolusInfo.EventType) {
        pumpSync.insertTherapyEventIfNewWithTimestamp(date, event, null, null, PumpType.ACCU_CHEK_INSIGHT, serialNumber())
    }

    override fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> {
        percentRate.setIfGreater(aapsLogger, 0, rh.gs(R.string.limitingpercentrate, 0, rh.gs(R.string.itmustbepositivevalue)), this)
        percentRate.setIfSmaller(aapsLogger, pumpDescription.maxTempPercent, rh.gs(R.string.limitingpercentrate, pumpDescription.maxTempPercent, rh.gs(R.string.pumplimit))
                                 , this)
        return percentRate
    }

    override fun applyBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        if (!limitsFetched) return insulin
        insulin.setIfSmaller(aapsLogger, maximumBolusAmount, rh.gs(R.string.limitingbolus, maximumBolusAmount, rh.gs(R.string.pumplimit)), this)
        if (insulin.value() < minimumBolusAmount) {

            //TODO: Add function to Constraints or use different approach
            // This only works if the interface of the InsightPlugin is called last.
            // If not, another constraint could theoretically set the value between 0 and minimumBolusAmount
            insulin.set(aapsLogger, 0.0, rh.gs(R.string.limitingbolus, minimumBolusAmount, rh.gs(R.string.pumplimit)), this)
        }
        return insulin
    }

    override fun applyExtendedBolusConstraints(insulin: Constraint<Double>): Constraint<Double> {
        return applyBolusConstraints(insulin)
    }

    override fun onStateChanged(state: InsightState?) {
        if (state == InsightState.CONNECTED) {
            statusLoaded = false
            Handler(Looper.getMainLooper()).post { rxBus.send(EventDismissNotification(Notification.INSIGHT_TIMEOUT_DURING_HANDSHAKE)) }
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
            Handler(Looper.getMainLooper()).post { rxBus.send(EventRefreshOverview("LocalInsightPlugin::onStateChanged", false)) }
        }
        Handler(Looper.getMainLooper()).post { rxBus.send(EventLocalInsightUpdateGUI()) }
    }

    override fun onPumpPaired() {
        commandQueue.readStatus("Pump paired", null)
    }

    override fun onTimeoutDuringHandshake() {
        val notification = Notification(Notification.INSIGHT_TIMEOUT_DURING_HANDSHAKE, rh.gs(R.string.timeout_during_handshake), Notification.URGENT)
        Handler(Looper.getMainLooper()).post { rxBus.send(EventNewNotification(notification)) }
    }

    override fun canHandleDST(): Boolean {
        return true
    }

    companion object {

        const val ALERT_CHANNEL_ID = "AndroidAPS-InsightAlert"
    }

}