package app.aaps.pump.ap550s.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager as AndroidNotificationManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.pump.defs.BasalUpdateResult
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.alerts.LocalAlertUtils
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.pump.ap550s.database.Ap550HistoryRecordDao
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.pump.PumpRate
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventBTChange
import app.aaps.pump.ap550s.events.EventBolusStart
import app.aaps.pump.ap550s.events.EventCurrentBasalChange
import app.aaps.pump.ap550s.events.EventGetCfgChangeRecord
import app.aaps.pump.ap550s.events.EventGetOtherLog
import app.aaps.pump.ap550s.events.EventGetResetVentRecord
import app.aaps.pump.ap550s.events.EventHypoglycemiaAlertCheck
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.pump.ap550s.events.EventUISPumpStatusChanged
import app.aaps.pump.ap550s.keys.Ap550BooleanKey
import app.aaps.pump.ap550s.keys.Ap550BooleanNonKey
import app.aaps.pump.ap550s.keys.Ap550IntNonKey
import app.aaps.pump.ap550s.keys.Ap550LongNonKey
import app.aaps.pump.ap550s.keys.Ap550StringNonKey
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.extensions.round
import app.aaps.core.data.time.SystemTimeZone
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.core.utils.notify
import app.aaps.core.utils.waitMillis
import app.aaps.core.objects.workflow.MetroService
import app.aaps.database.AppRepository
import app.aaps.database.entities.GlucoseValue
import app.aaps.pump.ap550s.AP550Pump
import app.aaps.pump.ap550s.AP550PumpPlugin
import app.aaps.pump.ap550s.BLEStateReceiver
import app.aaps.pump.ap550s.R
import app.aaps.pump.ap550.protocol.comm.RecordTypes
import app.aaps.pump.ap550s.database.DatabaseHelper
import app.aaps.pump.ap550s.defs.ConnectStatus
import app.aaps.pump.ap550s.events.EventAP550CheckState
import app.aaps.pump.ap550s.events.EventAP550NewBG
import app.aaps.pump.ap550s.events.EventAP550NewStatus
import app.aaps.pump.ap550s.events.EventAP550Reconnect
import app.aaps.pump.ap550s.events.EventBlePair
import app.aaps.pump.ap550s.events.EventProfileNeedsUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import java.lang.reflect.InvocationTargetException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import kotlin.math.abs
import kotlin.math.min

// ===== SDK receive path (task 13a): SDK protocol classes and POJOs =====
import app.aaps.core.data.model.TE
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.pump.ap550s.events.EventAlarmListToXiaozhi
import app.aaps.pump.ap550s.events.EventBumpProfileSetOk
import app.aaps.pump.ap550s.events.EventLastBolus
import app.aaps.pump.ap550s.events.EventPumpSuspendedChange
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.pump.ap550.protocol.comm.Ap550LinkCodec
import app.aaps.pump.ap550.protocol.comm.Ap550MessageFactory
import app.aaps.pump.ap550.protocol.comm.MessageBase as SdkMessageBase
import app.aaps.pump.ap550.protocol.comm.MessageHashTableAP550 as SdkMessageHashTableAP550
import app.aaps.pump.ap550.protocol.comm.MsgBolusProgress as SdkMsgBolusProgress
import app.aaps.pump.ap550.protocol.comm.MsgBolusPumpSendSyn as SdkMsgBolusPumpSendSyn
import app.aaps.pump.ap550.protocol.comm.MsgBolusStart as SdkMsgBolusStart
import app.aaps.pump.ap550.protocol.comm.MsgBolusStartWithSpeed as SdkMsgBolusStartWithSpeed
import app.aaps.pump.ap550.protocol.comm.MsgBgReadingsDataFromTime as SdkMsgBgReadingsDataFromTime
import app.aaps.pump.ap550.protocol.comm.MsgCRCError as SdkMsgCRCError
import app.aaps.pump.ap550.protocol.comm.MsgDisconnectBle as SdkMsgDisconnectBle
import app.aaps.pump.ap550.protocol.comm.MsgHostId as SdkMsgHostId
import app.aaps.pump.ap550.protocol.comm.MsgPCCommStart as SdkMsgPCCommStart
import app.aaps.pump.ap550.protocol.comm.MsgPCCommStop as SdkMsgPCCommStop
import app.aaps.pump.ap550.protocol.comm.MsgSetActivateBasalProfile as SdkMsgSetActivateBasalProfile
import app.aaps.pump.ap550.protocol.comm.MsgSetCarbsEntry as SdkMsgSetCarbsEntry
import app.aaps.pump.ap550.protocol.comm.MsgSetPumpMute as SdkMsgSetPumpMute
import app.aaps.pump.ap550.protocol.comm.MsgSettingActiveProfile as SdkMsgSettingActiveProfile
import app.aaps.pump.ap550.protocol.comm.MsgStopInjection as SdkMsgStopInjection
import app.aaps.pump.ap550.protocol.comm.MsgBolusStop as SdkMsgBolusStop
import app.aaps.pump.ap550.protocol.comm.MsgBumpLog as SdkMsgBumpLog
import app.aaps.pump.ap550.protocol.comm.MsgCfgChangeIndexSync as SdkMsgCfgChangeIndexSync
import app.aaps.pump.ap550.protocol.comm.MsgCfgChangeRecordSync as SdkMsgCfgChangeRecordSync
import app.aaps.pump.ap550.protocol.comm.MsgCheckValue as SdkMsgCheckValue
import app.aaps.pump.ap550.protocol.comm.MsgConnectAuthTimeOut as SdkMsgConnectAuthTimeOut
import app.aaps.pump.ap550.protocol.comm.MsgError as SdkMsgError
import app.aaps.pump.ap550.protocol.comm.MsgErrorArray as SdkMsgErrorArray
import app.aaps.pump.ap550.protocol.comm.MsgGetBolusTempbasalLog as SdkMsgGetBolusTempbasalLog
import app.aaps.pump.ap550.protocol.comm.MsgGetBolusTempbasalLogIndex as SdkMsgGetBolusTempbasalLogIndex
import app.aaps.pump.ap550.protocol.comm.MsgGetBumpBasal as SdkMsgGetBumpBasal
import app.aaps.pump.ap550.protocol.comm.MsgGetLogIndex as SdkMsgGetLogIndex
import app.aaps.pump.ap550.protocol.comm.MsgHistoryAll as SdkMsgHistoryAll
import app.aaps.pump.ap550.protocol.comm.MsgHistoryDone as SdkMsgHistoryDone
import app.aaps.pump.ap550.protocol.comm.MsgHistoryNewDone as SdkMsgHistoryNewDone
import app.aaps.pump.ap550.protocol.comm.MsgInitConnPairing as SdkMsgInitConnPairing
import app.aaps.pump.ap550.protocol.comm.MsgInitConnPairingCode as SdkMsgInitConnPairingCode
import app.aaps.pump.ap550.protocol.comm.MsgInitConnStatusBasic as SdkMsgInitConnStatusBasic
import app.aaps.pump.ap550.protocol.comm.MsgInitConnStatusBolus as SdkMsgInitConnStatusBolus
import app.aaps.pump.ap550.protocol.comm.MsgInitConnStatusOption as SdkMsgInitConnStatusOption
import app.aaps.pump.ap550.protocol.comm.MsgInitConnStatusTime as SdkMsgInitConnStatusTime
import app.aaps.pump.ap550.protocol.comm.MsgRemainingInsulin as SdkMsgRemainingInsulin
import app.aaps.pump.ap550.protocol.comm.MsgResetVentRecordIndexSync as SdkMsgResetVentRecordIndexSync
import app.aaps.pump.ap550.protocol.comm.MsgResetVentRecordSync as SdkMsgResetVentRecordSync
import app.aaps.pump.ap550.protocol.comm.MsgSetBasalProfile as SdkMsgSetBasalProfile
import app.aaps.pump.ap550.protocol.comm.MsgSetExtendedBolusStart as SdkMsgSetExtendedBolusStart
import app.aaps.pump.ap550.protocol.comm.MsgSetExtendedBolusStop as SdkMsgSetExtendedBolusStop
import app.aaps.pump.ap550.protocol.comm.MsgSetSingleBasalProfile as SdkMsgSetSingleBasalProfile
import app.aaps.pump.ap550.protocol.comm.MsgSetTempBasalAbsoluteStart as SdkMsgSetTempBasalAbsoluteStart
import app.aaps.pump.ap550.protocol.comm.MsgSetTempBasalStart as SdkMsgSetTempBasalStart
import app.aaps.pump.ap550.protocol.comm.MsgSetTempBasalStop as SdkMsgSetTempBasalStop
import app.aaps.pump.ap550.protocol.comm.MsgSetTime as SdkMsgSetTime
import app.aaps.pump.ap550.protocol.comm.MsgSettingBasal as SdkMsgSettingBasal
import app.aaps.pump.ap550.protocol.comm.MsgSettingBasalProfileAll as SdkMsgSettingBasalProfileAll
import app.aaps.pump.ap550.protocol.comm.MsgSettingGlucose as SdkMsgSettingGlucose
import app.aaps.pump.ap550.protocol.comm.MsgSettingMaxValues as SdkMsgSettingMaxValues
import app.aaps.pump.ap550.protocol.comm.MsgSettingMeal as SdkMsgSettingMeal
import app.aaps.pump.ap550.protocol.comm.MsgSettingProfileRatios as SdkMsgSettingProfileRatios
import app.aaps.pump.ap550.protocol.comm.MsgSettingProfileRatiosAll as SdkMsgSettingProfileRatiosAll
import app.aaps.pump.ap550.protocol.comm.MsgSettingPumpTime as SdkMsgSettingPumpTime
import app.aaps.pump.ap550.protocol.comm.MsgSettingShippingInfo as SdkMsgSettingShippingInfo
import app.aaps.pump.ap550.protocol.comm.MsgSettingUserOptions as SdkMsgSettingUserOptions
import app.aaps.pump.ap550.protocol.comm.MsgStatus as SdkMsgStatus
import app.aaps.pump.ap550.protocol.comm.MsgStatusBasic as SdkMsgStatusBasic
import app.aaps.pump.ap550.protocol.comm.MsgStatusBolusExtended as SdkMsgStatusBolusExtended
import app.aaps.pump.ap550.protocol.comm.MsgStatusProfile as SdkMsgStatusProfile
import app.aaps.pump.ap550.protocol.comm.MsgStatusPumpMute as SdkMsgStatusPumpMute
import app.aaps.pump.ap550.protocol.comm.MsgStatusTempBasal as SdkMsgStatusTempBasal
import app.aaps.pump.ap550.protocol.comm.MsgStatusTempBasalAbsolute as SdkMsgStatusTempBasalAbsolute
import app.aaps.pump.ap550.protocol.comm.MsgTempBasalHistory as SdkMsgTempBasalHistory
import app.aaps.pump.ap550.protocol.data.*
import app.aaps.pump.ap550.protocol.data.BolusProgressData as SdkBolusProgressData
import app.aaps.pump.ap550s.database.Ap550HistoryRecord
import app.aaps.pump.ap550s.events.EventBolusChange
import app.aaps.pump.ap550s.events.EventDosageChange
import app.aaps.pump.ap550s.events.EventMaxValue
import app.aaps.pump.ap550s.utils.ConnectUtil

/**
 * The same value as R.string.ap550_version_before_DCN18 in
 * pump/ap550/src/main/res/values/strings.xml. After the receive-side effects moved into the
 * Service, they no longer read app resources, so they all use this constant instead.
 */
private const val BEFORE_DCN18_VERSION_STRING = "CY8CKIT-062 PSoC 6 BLE Pioneer Kit"

class AP550ExecutionService : MetroService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var context: Context
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var ap550Pump: AP550Pump
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var ap550PumpPlugin: AP550PumpPlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var dataUtil: DateUtil
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var bolusProgressData: BolusProgressData
    @Inject lateinit var notificationManager: NotificationManager
    @Inject lateinit var preferences: Preferences

    @Inject lateinit var localAlertUtils: LocalAlertUtils
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var loop: Loop
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var pumpEnactResultProvider: Provider<PumpEnactResult>
    @Inject lateinit var processedTbrEbData: ProcessedTbrEbData
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var ap550HistoryRecordDao: Ap550HistoryRecordDao

    // ===== SDK receive path (task 13a) =====
    @Inject lateinit var sdkMessageHashTable: SdkMessageHashTableAP550
    @Inject lateinit var sdkMessageFactory: Ap550MessageFactory

    // Task 17: plaintext frames stop at the SDK. Frame coding, encryption and the session key all
    // live inside Ap550LinkCodec, so no plaintext is exposed on the app side.
    @Inject lateinit var linkCodec: Ap550LinkCodec

    @Inject lateinit var connectUtil: ConnectUtil
    @Inject lateinit var configBuilder: ConfigBuilder

    // Service lifetime. Cancelled in onDestroy.
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Core calls (PersistenceLayer, PumpSync, CommandQueue, IobCobCalculator, LocalAlertUtils) are
    // suspend on this branch. The AP550 SDK calls back on its own BLE threads, so the message
    // handlers cannot be suspend without rewriting the whole listener chain. They use runBlocking
    // so the order of the calls stays exactly as it was in the original blocking code. Nothing here
    // runs on the main thread. Code that is already inside a coroutine calls the suspend API directly.

    protected var mDevName: String? = null
    protected var mDevMac: String? = null
    protected var mBinder: IBinder? = null

    //protected BluetoothSocket mRfcommSocket;
    protected var mBTDevice: BluetoothDevice? = null
    protected val AP550_SERVICE_UUID = UUID.fromString("00004d35-0000-1000-8000-00805f9b34fb")
    protected val AP550_DEVICE_ID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB")
    protected val AP550_DATA_TX_GATT_CHARACTERISTIC_UUID =
        UUID.fromString("0000ffe8-0000-1000-8000-00805f9b34fb")
    val AP550_DATA_RX_GATT_CHARACTERISTIC_UUID =
        UUID.fromString("0000ffe8-0000-1000-8000-00805f9b34fb")
    protected val CLIENT_CHARACTERISTIC_CONFIG =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    protected val AP550_DATA_DCN_GATT_CHARACTERISTIC_UUID =
        UUID.fromString("00002A27-0000-1000-8000-00805F9B34FB")
    protected var mHandshakeInProgress = false
    private var mtuExchangeSuccess = false
    protected var lastApproachingDailyLimit: Long = 0

    @Volatile
    private var mConnectionState = BluetoothProfile.STATE_DISCONNECTING
    private var processedMessage: SdkMessageBase? = null
    private val handler: Handler? = null
    private var sDatabaseHelper: DatabaseHelper? = null
    private var timer: Timer? = null
    private val liveExecutor = Executors.newSingleThreadScheduledExecutor()
    private var pumpStatusTask: ScheduledFuture<*>? = null
    private var connectStatusTimer: Timer? = null
    private var task: TimerTask? = null
    private var connectStatusTask: TimerTask? = null

    private var localBleSwitchReceiver: BroadcastReceiver? = null

    private var lastConnectStatus: Boolean = false
    var isPairSuccess: Boolean = false
    var timeZoneChanged = false

    // Reconnect right after the service starts
    private var lastReconnect: Long = 0

    // First status query one minute after the service starts
    private var lastquerytime: Long = System.currentTimeMillis() - 1 * 60 * 1000

    private var lastcheckCGMtime: Long = System.currentTimeMillis()

    private val scheduledExecutor = Executors.newScheduledThreadPool(1)
    private var onceSchedule = false
    var isBolusStart = false  // Has the bolus already started

    inner class LocalBinder : Binder() {

        val serviceInstance: AP550ExecutionService
            get() = this@AP550ExecutionService
    }

    /**
     * Promotes the service to a foreground service with type connectedDevice.
     *
     * On Android 14+ that type requires the BLUETOOTH_CONNECT runtime permission to be granted.
     * The service is bound at app start, before the user can grant anything, so the permission may
     * still be missing here. In that case we skip the promotion: the service keeps running as a
     * bound service, and [connect] promotes it once the permission is granted.
     */
    private fun maybeStartForeground() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            aapsLogger!!.warn(
                LTag.PUMPBTCOMM,
                "BLUETOOTH_CONNECT not granted yet, skipping foreground promotion"
            )
            return
        }
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "Test_ble_connect startForeground")
        val channelId = "ap550_service_channel"
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AP550 Pump Service")
            .setContentText("AP550 Pump Service is running...")
            .setSmallIcon(R.drawable.reconnect)
            .build()
        // Android 8.0+ requires a notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "AP550 Pump Service",
                AndroidNotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as AndroidNotificationManager)
                .createNotificationChannel(channel)
        }
        try {
            startForeground(1, notification)
        } catch (e: IllegalStateException) {
            // Android 12+ throws ForegroundServiceStartNotAllowedException (a subclass) when a
            // background app calls this. Reconnects are often started by the keep-alive alarm in
            // the background, so this is a normal case: the service stays a bound service and the
            // next promotion attempt from the foreground retries.
            aapsLogger!!.warn(LTag.PUMPBTCOMM, "Foreground promotion rejected by the system", e)
        } catch (e: SecurityException) {
            // The permission was revoked between the check above and the call.
            aapsLogger!!.warn(LTag.PUMPBTCOMM, "Foreground promotion lost the permission race", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "Test_ble_connect onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }

    override fun onCreate() {
        super.onCreate()
        mBinder = LocalBinder()
        rxBus.toFlow(EventBTChange::class)
            .collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { event ->
                if (event.state === EventBTChange.Change.DISCONNECT) {
                    aapsLogger.debug(
                        LTag.PUMP, "Device was disconnected " + event.deviceName
                    ) //Device was disconnected
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
                        if (ActivityCompat.checkSelfPermission(
                                this, Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return@collectResilient
                        }
                    }
                    if (mBTDevice != null && mBTDevice!!.name != null && mBTDevice!!.name == event.deviceName) {
                        //disconnect("BT disconnection broadcast");
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                    }
                }
            }
        rxBus.toFlow(EventAppExit::class)
            .collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) {
                aapsLogger.debug(LTag.PUMP, "EventAppExit received")
                disconnect("Application exit")
                stopSelf()
            }

        rxBus.toFlow(EventCurrentBasalChange::class)
            .collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) {
                sendMessage(sdkMessageFactory.msgStatusBasic())
                sendMessage(sdkMessageFactory.msgSettingBasal(ap550Pump.activeProfile, ap550PumpPlugin.pumpDescription.basalMinimumRate))
            }

        rxBus.toFlow(EventBolusStart::class)
            .collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) {
                sendMessage(sdkMessageFactory.msgStatus())
            }

        rxBus.toFlow(EventPumpStatusChanged::class)
            .collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { event ->
                when (event.status) {
                    EventPumpStatusChanged.Status.DISCONNECTED -> {
                        aapsLogger.debug(LTag.PUMP, "Test_ble_connect: EventPumpStatusChanged: rxBus.toFlow, lastConnectStatus=$lastConnectStatus disconnect")
                        // The QueueWorker sends DISCONNECTED when the queue is drained, but ap550
                        // ignores that request and keeps the link. Only log a real disconnect
                        // (isConnected is false), so the connection history shows no false entries.
                        if (lastConnectStatus && !isConnected) {
                            ap550Pump.scanStatusRecord(ConnectStatus.Disconnected)
                            // lastConnectStatus = false
                        }
                    }

                    EventPumpStatusChanged.Status.CONNECTED -> {
                        aapsLogger.debug(LTag.PUMP, "Test_ble_connect:EventPumpStatusChanged: rxBus.toFlow, ap550Pump.paired=${ap550Pump.paired}, isConnected=$isConnected, Connected")
                        if (ap550Pump.paired && isConnected) {
                            ap550Pump.scanStatusRecord(ConnectStatus.Connected)
                            // lastConnectStatus = true
                        }
                    }
                    else -> {}
                }
            }

        rxBus.toFlow(EventAP550Reconnect::class)
            .collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) {
                aapsLogger.debug(LTag.PUMP, "Test_ble_connect:EventAP550Reconnect: ap550Pump.paired=${ap550Pump.paired}, isConnected=$isConnected")
                if (System.currentTimeMillis() - lastReconnect > 2 * 60 * 1000) {
                    aapsLogger.debug(LTag.PUMP, "Reconnect by EventAP550Reconnect")
                    lastReconnect = System.currentTimeMillis()
                    connect("reconnected")
                }
            }

        rxBus.toFlow(EventAP550CheckState::class)
            .collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) {
                val now = System.currentTimeMillis()
                val agoMinutes = ((now - ap550Pump.lastConnection) / 60.0 / 1000.0).toInt()
                aapsLogger.debug(LTag.PUMP, "Test_ble_connect:CHECKSTATE paired=${ap550Pump.paired}, isConnected=$isConnected, CHECKSTATE")
                aapsLogger.debug(LTag.PUMP, "Test_ble_connect:CHECKSTATE last communication time agoMinutes=$agoMinutes")
                // Last connection more than 5 min ago, and last query more than 2 min ago
                // The function checks whether the pump is connected and paired. If so, it queries;
                // otherwise it sends a reconnect broadcast.
                if (now - ap550Pump.lastConnection > 5 * 60 * 1000 && System.currentTimeMillis() - lastquerytime > 2 * 60 * 1000) {
                    handleQueryPumpStatus()
                }

                // Near 22:00, add one hypoglycemia risk check
                val nowFromMidnight = MidnightTime.secondsFromMidnight(dateUtil.now(), SystemTimeZone.current())
                val clockTime = 22 * 60 * 60
                aapsLogger.debug(LTag.PUMP, "hypoglycemia")
                if (abs(clockTime - nowFromMidnight) <= 15) {    // Around 22:00, a 30 s window
                    handleHypoglycemiaAlertCheck()
                }

                // Daily pump TDD pull, triggered at the day rollover. Rate-limited, so the pump does not crash;
                val today = LocalDate.now(ZoneId.systemDefault())
                val lastSyncDate = preferences.get(Ap550StringNonKey.LastPumpTddSyncDate)
                val lastAttempt = preferences.get(Ap550LongNonKey.LastPumpTddFetchAttemptTime)
                val withinWindow = now - lastAttempt < 3 * 60 * 60 * 1000L
                if (today.toString() != lastSyncDate && !withinWindow) {
                    preferences.put(Ap550LongNonKey.LastPumpTddFetchAttemptTime, now)
                    fetchPumpTddToRawSp()
                }
            }

        rxBus.toFlow(EventBlePair::class)
            .collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { event ->
                if (event.status) {
                    aapsLogger.debug(LTag.PUMP, "Test_ble_connect: EventBlePair: rxBus.toFlow, Paired")
                    isPairSuccess = true
                    // Right after the Bluetooth pairing succeeds, queue a pump status read
                    scope.launch { commandQueue.readStatus("paired success") }
                } else {
                    aapsLogger.debug(LTag.PUMP, "Test_ble_connect: EventBlePair: rxBus.toFlow, NoPair")
                    isPairSuccess = false
                }
                // As soon as Bluetooth connects, check for a 550SE. A 550SE uses percent mode only.
                val nameValue = preferences.getIfExists(Ap550StringNonKey.BtDeviceName)
                if (nameValue != null) {
                    if(nameValue.contains("E")){
                        preferences.put(Ap550BooleanNonKey.BasalIsPercent, true)
                        ap550PumpPlugin.pumpDescription.tempBasalStyle  = PumpDescription.PERCENT // A 550SE only has percent
                    }
                }

            }

        rxBus.toFlow(EventUISPumpStatusChanged::class)
            .collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { event ->
                aapsLogger.debug(LTag.PUMP, "EventUISPumpStatusChanged: rxBus.toFlow, isConnected=${event.status}")
                if (event.status) {
                    aapsLogger.debug(LTag.PUMP, "EventUISPumpStatusChanged: isConnected=${event.status}")
                    if(syncPumpTime()){  // Fetch the bolus record right after a reconnect
                        bolusRecord("fetch bolus record after reconnect")
                    }
                }

            }
        rxBus.toFlow(EventBolusStart::class)
            .collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { event ->
                isBolusStart = event.bolusStart
                aapsLogger.debug(LTag.PUMP, "EventBolusStart: rxBus.toFlow, isBolusStart=$isBolusStart")
            }

        rxBus.toFlow(EventGetOtherLog::class)
            .collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { event ->
                if (event.logType == 0) {
                    sendMessage(sdkMessageFactory.msgBumpLog(0, ap550Pump.otherIndex + 1))
                } else if (event.logType == 2) {
                    sendMessage(sdkMessageFactory.msgGetBolusTempbasalLog(2, ap550Pump.tempBasalIndex + 1))
                } else {
                    aapsLogger.debug(LTag.PUMP, "EventGetOtherLog: rxBus.toFlow, logType=${event.logType}")
                }
            }

        rxBus.toFlow(EventGetResetVentRecord::class)
            .collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { event ->
                sendMessage(sdkMessageFactory.msgResetVentRecordSync(event.recordIndex))
                aapsLogger.debug(LTag.PUMP, "receive EventGetResetVentRecord: recordIndex=${event.recordIndex}")
            }
        rxBus.toFlow(EventGetCfgChangeRecord::class)
            .collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) { event ->
                sendMessage(sdkMessageFactory.msgCfgChangeRecordSync(event.recordIndex))
                aapsLogger.debug(LTag.PUMP, "receive EventGetCfgChangeRecord: recordIndex=${event.recordIndex}")
            }

        // Hypoglycemia alert check
        rxBus.toFlow(EventHypoglycemiaAlertCheck::class)
            .collectResilient(scope, aapsLogger, LTag.PUMP, start = CoroutineStart.UNDISPATCHED) {
                handleHypoglycemiaAlertCheck()
            }

        val macAddress = preferences.get(Ap550StringNonKey.MacAddress)
        if (!StringUtils.isEmpty(macAddress)) {
            isPairSuccess = true
        }
        aapsLogger.debug(LTag.PUMP, "Test_ble_connect: onCreate isPairSuccess=${isPairSuccess}")
        BLEStateReceiver.register(applicationContext)
        localBleSwitchReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.localbroadcast.BLE_SWITCH") {
                    val reason = intent.getStringExtra("reason")
                    if (isConnected && reason == "bluetooth is off") {
                        disconnect("bluetooth is off")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                    }
                }
            }
        }
        if(preferences.get(Ap550BooleanNonKey.BasalIsPercent)){
            ap550PumpPlugin.pumpDescription.tempBasalStyle = PumpDescription.PERCENT
        }else{
            ap550PumpPlugin.pumpDescription.tempBasalStyle = PumpDescription.ABSOLUTE
        }
        aapsLogger.debug(LTag.PUMP, "Test_ble_connect: onCreate ap550PumpPlugin.pumpDescription.tempBasalStyle=${ap550PumpPlugin.pumpDescription.tempBasalStyle}")

        val filter = IntentFilter()
        filter.addAction("com.localbroadcast.BLE_SWITCH")
        LocalBroadcastManager.getInstance(this)
            .registerReceiver(localBleSwitchReceiver as BroadcastReceiver, filter)
        rxBus.send(EventAP550Reconnect())
        maybeStartForeground()
        // On service start, load the pairing data from SP into ap550Pump
        ap550Pump.device_name_for_pair = preferences.get(Ap550StringNonKey.BtDeviceName)
        ap550Pump.mac_address_for_pair = preferences.get(Ap550StringNonKey.MacAddress)
        ap550Pump.sn_for_pair = preferences.get(Ap550StringNonKey.SerialNumber)
        // Restore the in-memory serial number too, so sync calls right after
        // reconnect (before the pump answers Shipping Info) carry the right serial.
        // Empty on first bind, so nothing is set then.
        if (StringUtils.isNotEmpty(ap550Pump.sn_for_pair)) {
            ap550Pump.serialNumber = ap550Pump.sn_for_pair
        }
        // On the first bind, read the reset/vent history index and store it in SP
        if (StringUtils.isEmpty(ap550Pump.sn_for_pair)) {
            ap550Pump.pumpIsFirstBindForVentResetRecord = true
            ap550Pump.pumpIsFirstBindForCfgChangeRecord = true
        }
    }

    // Fetch the pump daily totals into the global raw SP. loadHistory blocks and busy-waits, so it
    // must never run on the main thread. @Synchronized stops two concurrent pump talks. The
    // in-hospital / pump-off guard lives on the upload side.
    @Synchronized
    fun fetchPumpTddToRawSp(): Boolean {
        // The in-hospital / pump-off guard is handled on the upload side (handlePumpTdd)
        if (!isConnected || !ap550Pump!!.paired) {
            aapsLogger!!.debug(LTag.PUMP, "TDD upload - fetch skipped: pump not connected or not paired(connected=$isConnected, paired=${ap550Pump!!.paired})")
            return false
        }
        loadHistory(RecordTypes.RECORD_TYPE_DAILY)
        val zone = ZoneId.systemDefault()
        val startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfYesterday = startOfToday - 24L * 60 * 60 * 1000
        val sixMonthsAgo = startOfToday - 180 * 24 * 60 * 60 * 1000L
        // Fetch the last 6 months, today excluded. "Contains yesterday" is the real success test,
        // because loadHistory can report a false success. SQL filters the lower bound by index.
        val fetched = runBlocking { ap550HistoryRecordDao.allFromByType(sixMonthsAgo, RecordTypes.RECORD_TYPE_DAILY) }
            .filter { it.timestamp < startOfToday }
        val hasYesterday = fetched.any { it.timestamp in startOfYesterday until startOfToday }
        if (hasYesterday) {
            val arr = JSONArray()
            fetched.forEach { rec ->
                arr.put(JSONObject()
                    .put("timestamp", rec.timestamp)
                    .put("basal", rec.dailyBasal)
                    .put("bolus", rec.dailyBolus))
            }
            aapsLogger!!.debug(LTag.PUMP,"TDD upload - fetched pump daily totals: ${fetched.size} records, " +
                    "oldest=${dateUtil!!.dateAndTimeAndSecondsString(fetched.minOf { it.timestamp })}, " +
                    "newest=${dateUtil!!.dateAndTimeAndSecondsString(fetched.maxOf { it.timestamp })}")
            preferences.put(Ap550StringNonKey.CloudPumpTddRaw, arr.toString())
            preferences.put(Ap550StringNonKey.LastPumpTddSyncDate, LocalDate.now(zone).toString())
        } else {
            aapsLogger!!.debug(LTag.PUMP, "TDD upload - the fetched pump daily totals hold no yesterday data")
        }
        return hasYesterday
    }


    /**
     * Writes a suspend/resume therapy event from a non-suspend SDK callback.
     * See the note on [scope] for why this blocks.
     */
    private fun pumpSyncInsert(
        timestamp: Long,
        type: TE.Type,
        note: String? = null,
        pumpId: Long? = null,
        pumpType: PumpType,
        pumpSerial: String
    ) = runBlocking {
        pumpSync.insertTherapyEventIfNewWithTimestamp(timestamp, type, note, pumpId, pumpType, pumpSerial)
    }

    private suspend fun handleHypoglycemiaAlertCheck() {
        val useHypoglycemiaRiskInform = preferences.get(Ap550BooleanKey.UseHypoglycemiaRiskInform)
        if (!useHypoglycemiaRiskInform) {
            aapsLogger!!.debug(LTag.PUMP, "Hypoglycemia risk inform - the feature switch is off")
            return
        }
        // Thresholds (mmol/L)
        val highBgThreshold = 10.0      // Start of high glucose
        val midBgThreshold = 5.0        // Lower bound of mid glucose
        val lowRiskThreshold = 3.9      // Low alert line (70 mg/dL)
        val veryLowRiskThreshold = 1.0  // Very low risk
        val highRiskLowThreshold = 3.0  // Threshold of a high risk low

        // Latest glucose value (AAPS keeps it in mg/dL)
        val lastBg = persistenceLayer.getLastGlucoseValue() ?: return
        val currentBg = lastBg.value * Constants.MGDL_TO_MMOLL // to mmol/L

        // Total IOB (U)
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        val totalIob = bolusIob.iob + basalIob.basaliob

        // ISF (to mmol/L per U)
        val isfMgdl = profileFunction?.getProfile()?.getIsfMgdlTimeFromMidnight(
            MidnightTime.secondsFromMidnight(SystemTimeZone.current())
        ) ?: return
        val isf = isfMgdl * Constants.MGDL_TO_MMOLL

        // Predicted glucose (mmol/L)
        val forwardBg = currentBg - totalIob * isf

        // Is the trend falling ?
        val isDescendingTrend = lastBg.trendArrow in listOf(
            TrendArrow.FORTY_FIVE_DOWN,
            TrendArrow.SINGLE_DOWN,
            TrendArrow.DOUBLE_DOWN,
            TrendArrow.TRIPLE_DOWN
        )

        aapsLogger?.debug(LTag.PUMP,
            "Hypoglycemia risk inform - current glucose: ${"%.1f".format(currentBg)} mmol/L, " +
                    "trend: ${lastBg.trendArrow}, " +
                    "IOB: ${"%.2f".format(totalIob)} U, " +
                    "ISF: ${"%.1f".format(isf)} mmol/U, " +
                    "predicted glucose: ${"%.1f".format(forwardBg)} mmol/L"
        )

        // Risk level
        val riskLevel = when {
            // Case 1: high glucose (> 10)
            currentBg >= highBgThreshold -> {
                // Glucose is falling
                if (isDescendingTrend) {
                    when {
                        forwardBg <= 0 -> RiskLevel.HIGH_RISK
                        forwardBg <= lowRiskThreshold -> RiskLevel.LOW_RISK
                        else -> RiskLevel.NO_RISK
                    }
                } else {
                    RiskLevel.NO_RISK
                }
            }
            // Case 2: mid glucose (5.0 ~ 10.0)
            currentBg >= midBgThreshold -> {
                when {
                    forwardBg <= veryLowRiskThreshold -> RiskLevel.HIGH_RISK
                    forwardBg <= lowRiskThreshold -> RiskLevel.LOW_RISK
                    else -> RiskLevel.NO_RISK
                }
            }

            // Case 3: already low (< 5.0)
            else -> {
                when {
                    forwardBg <= highRiskLowThreshold -> RiskLevel.HIGH_RISK
                    forwardBg <= lowRiskThreshold -> RiskLevel.LOW_RISK
                    else -> RiskLevel.NO_RISK
                }
            }
        }

        // One place for the notification, so it is not posted twice
        when (riskLevel) {
            RiskLevel.HIGH_RISK, RiskLevel.LOW_RISK -> {
                val description = rh!!.gs(riskLevel.descriptionResId)
                val level = if (riskLevel == RiskLevel.HIGH_RISK) NotificationLevel.URGENT else NotificationLevel.NORMAL
                notificationManager.post(
                    NotificationId.HYPOGLYCEMIA_RISK,
                    description,
                    level,
                    sound = AlarmSound.ALARM
                )
                pumpSync?.insertAnnouncement(
                    rh!!.gs(R.string.hypoglycemia_risk_inform) + ":" + description,
                    null,
                    ap550Pump!!.pumpType(),
                    ap550Pump!!.serialNumber
                )
            } else -> {
                // Risk is normal, so drop the alert
                notificationManager.dismiss(NotificationId.HYPOGLYCEMIA_RISK)
            }
        }
        aapsLogger?.debug(LTag.PUMP, "Hypoglycemia risk inform - risk level: ${rh!!.gs(riskLevel.descriptionResId)}")
    }


    private fun updatePumpData() {
        if (timer == null) {
            timer = Timer()
        }
        if (task == null) {
            task = object : TimerTask() {
                override fun run() {
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "Test_ble_connect updatePumpData timer")
                    if (isConnected) {
                        try {
                            aapsLogger!!.debug(LTag.PUMP, "Test_pumpStatus updatePumpData")
                            pumpStatus
                        } catch (e: Exception) {
                            aapsLogger!!.error("Unhandled exception", e)
                        }
                        rxBus!!.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
                    } else {
                        aapsLogger!!.debug(LTag.PUMPBTCOMM, "BT is not connected")
                    }
                }
            }
        }
        if (isConnected) {
            if (timer != null && task != null) {
                timer!!.schedule(task, (5 * 60 * 1000).toLong(), (5 * 60 * 1000).toLong())
            }
        } else {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "BT is not connected")
        }
    }

    private fun query550PumpStatus() {
        try {
            aapsLogger!!.debug(LTag.PUMP, "Query 550 pump status")
            val now = System.currentTimeMillis()
            ap550Pump!!.lastConnection = now
            preferences.put(Ap550LongNonKey.LastConnection, now)
            ap550Pump?.scanStatusRecord(ConnectStatus.Connected)
            sendMessage(sdkMessageFactory!!.msgSettingMaxValues())
            if (syncPumpTime()) {
                sendMessage(sdkMessageFactory!!.msgStatus())
                tempbasalRecord()
                bolusRecord("query pump status")
                sendMessage(sdkMessageFactory!!.msgInitConnStatusBasic())     // Ask whether the pump is suspended
            }
            /* *************** Do not delete this code! It is the reset/fill history upload for Cloud; not added for now ****************
            if(now - preferences.get(Ap550LongNonKey.MsgHistoryRefill) > 59*60*1000){   // Sync the reset/fill history once per hour
                sendMessage(MsgHistoryRefill(injector!!))
                preferences.put(Ap550LongNonKey.MsgHistoryRefill, now)
                rxBus!!.send(EventRefillHistory())
            }
            */
            sendMessage(sdkMessageFactory!!.msgStatusBasic())
            sendMessage(sdkMessageFactory!!.msgErrorArray(ap550Pump!!.pumpVersionCode1))
            if (ap550Pump!!.pumpVersionString != BEFORE_DCN18_VERSION_STRING) {
                // Read the basal change log / other logs
                if (preferences.get(Ap550BooleanNonKey.IsGetLogIndex) == false || ap550Pump!!.basicIndex < 0 || ap550Pump!!.otherIndex < 0) {
                    aapsLogger!!.debug(LTag.PUMPCOMM, "IsGetLogIndex=${preferences.get(Ap550BooleanNonKey.IsGetLogIndex)} otherIndex=${ap550Pump!!.otherIndex} basicIndex=${ap550Pump!!.basicIndex}")
                    val mesGetLogIndex = sdkMessageFactory!!.msgGetLogIndex()
                    sendMessage(mesGetLogIndex)
                } else {
                    val msgBumpLog = sdkMessageFactory!!.msgBumpLog(1, ap550Pump!!.basicIndex + 1)
                    sendMessage(msgBumpLog)
                }

                // Read the bolus / temp basal log
//                if (sp?.getBoolean("IsGetBolusTempbasalLogIndex", false) == false || ap550Pump!!.bolusIndex < 0 || ap550Pump!!.tempBasalIndex < 0) {
//                    aapsLogger!!.debug(LTag.PUMPCOMM, "IsGetBolusTempbasalLogIndex=${sp?.getBoolean("IsGetBolusTempbasalLogIndex", false)} otherIndex=${ap550Pump!!.bolusIndex} basicIndex=${ap550Pump!!.tempBasalIndex}")
//                    val msgGetBolusTempbasalLogIndex = MsgGetBolusTempbasalLogIndex(injector!!)
//                    sendMessage(msgGetBolusTempbasalLogIndex)
//                } else {
//                    val msgGetBolusTempbasalLog = MsgGetBolusTempbasalLog(injector!!, 1)
//                    sendMessage(msgGetBolusTempbasalLog)
//                }

            }
            // For DCN20
            // TODO
            if (ap550Pump!!.pumpVersionString != BEFORE_DCN18_VERSION_STRING
                && ap550Pump!!.pumpVersionString != "2300") {
                val resetventrecordindexmsg = sdkMessageFactory!!.msgResetVentRecordIndexSync()
                sendMessage(resetventrecordindexmsg)
                val cfgchangerecordindexmsg = sdkMessageFactory!!.msgCfgChangeIndexSync()
                sendMessage(cfgchangerecordindexmsg)
                val statuspumpMute = sdkMessageFactory!!.msgStatusPumpMute()
                sendMessage(statuspumpMute)
            }
        } catch (e: Exception) {
            aapsLogger!!.error("Unhandled exception", e)
        }
    }

    private fun tempbasalRecord(): Boolean {
        if (!isConnected) return false
        val lastestTempBasalTime = runBlocking { persistenceLayer.getLastTemporaryBasalRecord() }?.timestamp
        val tempBasalSynMsg = if (lastestTempBasalTime == null) {
            sdkMessageFactory!!.msgTempBasalHistory(System.currentTimeMillis() - 5 * 60 * 60 * 1000, ap550Pump!!.pumpVersionString == BEFORE_DCN18_VERSION_STRING)   // Fetch the pump temp basal history of the last 5 hours
        } else {
            if (preferences.get(Ap550BooleanNonKey.IsTempBasalRunningAfterDisconnect) && ap550Pump!!.pumpVersionString != BEFORE_DCN18_VERSION_STRING) {
                aapsLogger?.debug(LTag.PUMPCOMM, "Fetch the latest temp basal record, lastestTempBasalTime - 13 * 1000: ${lastestTempBasalTime-13 * 1000}")
                sdkMessageFactory!!.msgTempBasalHistory(lastestTempBasalTime - 13 * 1000, ap550Pump!!.pumpVersionString == BEFORE_DCN18_VERSION_STRING)   // Fetch the latest temp basal record of the pump
            } else {
                aapsLogger?.debug(LTag.PUMPCOMM, "Fetch the latest temp basal record, lastestTempBasalTime: $lastestTempBasalTime")
                sdkMessageFactory!!.msgTempBasalHistory(lastestTempBasalTime, ap550Pump!!.pumpVersionString == BEFORE_DCN18_VERSION_STRING)   // Fetch the latest temp basal record of the pump
            }

        }
        sendMessage(tempBasalSynMsg)
        return true
    }

    fun bolusRecord(reason: String) {
        aapsLogger?.debug(LTag.PUMPCOMM, "Fetch bolus record - reason: $reason, isBolusStart = $isBolusStart")
        if (isBolusStart) return
        // The default query start is two hours ago
        val twoHoursAgo = System.currentTimeMillis() - 2 * 60 * 60 * 1000
        // Start of the newest bolus and end of the newest extended bolus
        val newestBolusTime = runBlocking { persistenceLayer.getNewestBolus() }?.timestamp ?: twoHoursAgo
        val newestExtendedBolusEndTime = runBlocking { persistenceLayer.getNewestExtendedBolus() }?.end ?: twoHoursAgo
        val queryStartTime = maxOf(newestBolusTime, newestExtendedBolusEndTime, twoHoursAgo)
        aapsLogger?.debug(LTag.PUMPCOMM, "Fetch bolus record - queryStartTime = $queryStartTime")
        // Send the sync message
        val bolusSynMsg = sdkMessageFactory!!.msgRemainingInsulin(queryStartTime, ap550Pump!!.pumpVersionString == BEFORE_DCN18_VERSION_STRING)
        sendMessage(bolusSynMsg)
    }

    private fun syncPumpTime(): Boolean {
        var syncSuccess = false
        val now = System.currentTimeMillis()
        if (ap550Pump!!.lastSyncPumpTime + 60 * 60 * 1000L < now || !ap550PumpPlugin!!.isInitialized() || ap550Pump!!.isNewPump || timeZoneChanged) {
            if (setPumpTime()) syncSuccess = true
            timeZoneChanged = false
            aapsLogger!!.debug(LTag.PUMP, "Test_ble_connect: 1h schedule time update")
        }
        return syncSuccess
    }

    private fun handleQueryPumpStatus() {
        lastquerytime = System.currentTimeMillis()
        aapsLogger?.debug(LTag.PUMP, "Test_ble_connect: handleQueryPumpStatus 5min schedule onceSchedule=$onceSchedule")
        try {
            aapsLogger?.debug(LTag.PUMP, "Test_ble_connect: handleQueryPumpStatus 5min schedule " +
                    "ap550Pump.paired=${ap550Pump!!.paired}, " +
                    "isConnected=$isConnected, " +
                    "lastConnectStatus=$lastConnectStatus")
            if (ap550Pump!!.paired && isConnected) {
                aapsLogger?.debug(LTag.PUMP, "Test_ble_connect: handleQueryPumpStatus to query550PumpStatus")
                query550PumpStatus()
                // The bolus-started flag can only be cleared when the last bolus started more than
                // 5 minutes ago. A bolus is normally finished within 5 minutes.
                if (System.currentTimeMillis() - ap550Pump!!.lastBolusStartTimestamp > 5*60*1000L){
                    aapsLogger?.debug(LTag.PUMP, "Test_ble_connect: handleQueryPumpStatus: Bolus not started for 5 minutes, resetting bolus status")
                    isBolusStart = false   // Reset bolus status
                }
                if (!lastConnectStatus) {
                    // Pump disconnected -> connected
                    aapsLogger?.debug(LTag.PUMP, "Pump connection changed - last connection state was: $lastConnectStatus, disconnected -> connected!")
                    val pumpConnectedStatusErrorString = context?.getString(R.string.aaps_pump_connected)
                    if (pumpConnectedStatusErrorString != null) {
                        ap550Pump?.pumpType()?.let {
                            ap550Pump?.serialNumber?.let { it1 ->
                                runBlocking {
                                    pumpSync?.insertAnnouncement(
                                        pumpConnectedStatusErrorString,
                                        null,
                                        it,
                                        it1
                                    )
                                }
                            }
                        }
                    }
                }

                rxBus?.send(EventUISPumpStatusChanged(true))
                lastConnectStatus = true

            } else {
                ap550Pump?.scanStatusRecord(ConnectStatus.Disconnected)
                if (lastConnectStatus) {
                    // Pump connected -> disconnected
                    aapsLogger?.debug(LTag.PUMP, "Pump connection changed - last connection state was: $lastConnectStatus, connected -> disconnected!")
                    val pumpDisconnectedStatusErrorString = context?.getString(R.string.aaps_pump_disconnected)
                    if (pumpDisconnectedStatusErrorString != null) {
                        ap550Pump?.pumpType()?.let {
                            ap550Pump?.serialNumber?.let { it1 ->
                                runBlocking {
                                    pumpSync?.insertAnnouncement(
                                        pumpDisconnectedStatusErrorString,
                                        null,
                                        it,
                                        it1
                                    )
                                }
                            }
                        }
                    }
                }
                // If the pairing failed but the link connected, force the Bluetooth link down and
                // wait for the next connection. This guards the odd case where isConnected is not
                // reset after Bluetooth drops.
                if(!ap550Pump!!.paired && isConnected){
                    aapsLogger?.debug(LTag.PUMP, "Test_ble_connect:BLE connected, but pair failed")
                    disconnect("BLE connected, but pair failed")
                }
                rxBus?.send(EventUISPumpStatusChanged(false))
                rxBus?.send(EventAP550Reconnect())
                aapsLogger?.debug(LTag.PUMP, "Reconnect by handleQueryPumpStatus")
                lastConnectStatus = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            aapsLogger?.debug(LTag.PUMP, "Timer exception occurred!")
            ap550Pump?.scanStatusRecord("Timer Exception")
        }
    }

    private fun handleQueryCGM() {
        val bgReading = runBlocking { persistenceLayer.getLastGlucoseValue() } ?: return
        aapsLogger?.debug(LTag.PUMP, "Test_ble_connect: handleQueryCGM, disconnect time over than 5min")
        if (bgReading.timestamp +  T.mins(8).msecs() < System.currentTimeMillis()) {
            runBlocking { localAlertUtils.checkStaleBGAlert() }
        }
    }

    private fun sendAuthentication() {
        val pairingCode = preferences.get(Ap550StringNonKey.PairingCode)
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "Test_ble_connect sendAuthentication pairingCode=$pairingCode")
        if (!StringUtils.isEmpty(pairingCode) && pairingCode.length == 6) {
            val value = pairingCode.toInt()
            val msgInitConnPairingCode = sdkMessageFactory!!.msgInitConnPairingCode(value)
            sendMessage(msgInitConnPairingCode)
            val msgHostId = sdkMessageFactory!!.msgHostId(ap550PumpPlugin!!.getAndroidId())
            sendMessage(msgHostId)
        }
    }

    val pumpStatus: Unit
        get() {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Test_ble_connect pumpStatus start mtuExchangeSuccess=$mtuExchangeSuccess")
            if(!mtuExchangeSuccess) {
                return
            }
            try {
                rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.ap550_ble_connection_authentication)))
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "Test_ble_connect pumpStatus start ap550Pump!!.paired=${ap550Pump!!.paired}")
                if (!ap550Pump!!.paired) {
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "Test_ble_connect pumpStatus paired failed BT connect is not authentication")
                    return
                }
                checkPumpVersion()
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "Test_ble_connect pumpStatus paired success")
                rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.getting_pump_status)))

                val msgInitConnStatusBolus = sdkMessageFactory!!.msgInitConnStatusBolus()
                val msgInitConnStatusBasic = sdkMessageFactory!!.msgInitConnStatusBasic()
                val exStatusMsg = sdkMessageFactory!!.msgStatusBolusExtended()
                val checkValue = sdkMessageFactory!!.msgCheckValue()

                if (ap550Pump!!.isNewPump) {
                    sendMessage(checkValue)
                    if (!checkValue.isReceived) {
                        return
                    }
                }
                if (ap550Pump!!.isFirstConnect) {    // On the first connection, set the time right away
                    aapsLogger!!.debug(LTag.PUMP, "setPumpTime isFirstConnect")
                    ap550Pump!!.isFirstConnect = false
                    sendMessage(sdkMessageFactory!!.msgSetTime(dateUtil?.now()!!))
                    sendMessage(sdkMessageFactory!!.msgSettingPumpTime())
                    val timeDiff = (ap550Pump!!.getPumpTime() - System.currentTimeMillis()) / 1000L
                    if (abs(timeDiff) > 10) {
                        resourceHelper.let {
                            notificationManager.post(
                                NotificationId.INSIGHT_DATE_TIME_UPDATED,
                                it.gs(R.string.pump_time_updated_failed),
                                NotificationLevel.URGENT,
                                validMinutes = 60
                            )
                        }
                        ap550Pump!!.lastSyncPumpTime = System.currentTimeMillis()
                    } else {
                        notificationManager.dismiss(NotificationId.INSIGHT_DATE_TIME_UPDATED)
                        resourceHelper.let {
                            notificationManager.post(
                                NotificationId.INSIGHT_DATE_TIME_UPDATED,
                                it.gs(R.string.pump_time_updated),
                                NotificationLevel.INFO,
                                validMinutes = 30
                            )
                        }
                    }
                }
                handleQueryPumpStatus()
                // alarmForQueryPumpStatus()       // Start the 15 s timer
                onceSchedule = true
                val now = System.currentTimeMillis()
                if (ap550Pump!!.lastSettingsRead + 60 * 60 * 1000L < now || !ap550PumpPlugin!!.isInitialized() || ap550Pump!!.isNewPump) {
                    aapsLogger!!.debug(LTag.PUMP, "Test_Basal_Setting ap550Pump.lastSettingsRead: ${ap550Pump!!.lastSettingsRead} ap550PumpPlugein.isInitialized: ${ap550PumpPlugin!!.isInitialized()}" +
                            " ap550Pump.isNewPump: ${ap550Pump!!.isNewPump}")
                    rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.getting_pump_settings)))
                    sendMessage(sdkMessageFactory!!.msgSettingActiveProfile())
                    sendMessage(sdkMessageFactory!!.msgSettingShippingInfo())
                    // sendMessage(MsgSettingMeal(injector!!))
                    if(now- (preferences.get(Ap550LongNonKey.LastGetBasalTimestamp) ?: 0) > 60*60*1000){
                        aapsLogger!!.debug(LTag.PUMPCOMM, "Test_Basal_Setting Basal mismatch 0")
                        sendMessage(sdkMessageFactory!!.msgSettingBasal(ap550Pump!!.activeProfile, ap550PumpPlugin!!.pumpDescription.basalMinimumRate))
                        preferences.put(Ap550LongNonKey.LastGetBasalTimestamp, now)
                    }
                    ap550Pump!!.lastSettingsRead = now
                }

                sendMessage(msgInitConnStatusBolus)
                sendMessage(msgInitConnStatusBasic)

                if (preferences.get(Ap550BooleanNonKey.BasalIsPercent) == true) {
                    rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.getting_temp_basal_status)))                   // Percent
                    val tempStatusMsg = sdkMessageFactory!!.msgStatusTempBasal(ap550Pump!!.pumpVersionCode1)
                    sendMessage(tempStatusMsg)
                } else {
                    val tempStatusAbsoluteMsg =
                        sdkMessageFactory!!.msgStatusTempBasalAbsolute()                                            // absolue
                    rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.getting_temp_basal_absolute_status)))
                    sendMessage(tempStatusAbsoluteMsg)
                }

                rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.getting_extended_bolus_status)))
                sendMessage(exStatusMsg)
                rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.getting_bolus_status)))

                if (now - ap550Pump!!.lastConnection >= 5 * 60 * 1000) {    // Update the last connection time only after 5 minutes
                    ap550Pump!!.lastConnection = now
                    ap550Pump!!.scanStatusRecord(ConnectStatus.Connected)
                    rxBus!!.send(EventUISPumpStatusChanged(true))
                    preferences.put(Ap550LongNonKey.LastConnection, now)
                    // lastConnectStatus = true
                }
                // Both checks below compare this against raw pump values, so it has to be the profile
                // as the pump sees it: the effective profile adjusted for insulin concentration.
                val profile = runBlocking { profileFunction!!.getProfile() }?.toPump()
                if (profile != null && Math.abs(ap550Pump!!.currentBasal - profile.getBasal()) >= ap550PumpPlugin!!.pumpDescription.basalStep) {
                    aapsLogger!!.debug(LTag.PUMPCOMM, "Test_Basal_Setting ap550Pump!!.currentBasal=${ap550Pump!!.currentBasal} profile.getBasal()=${profile.getBasal()}")
                    rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.getting_pump_settings)))
                    if(now- (preferences.get(Ap550LongNonKey.LastGetBasalTimestamp) ?: 0) > 60*60*1000){
                        aapsLogger!!.debug(LTag.PUMPCOMM, "Test_Basal_Setting Basal mismatch 1")
                        sendMessage(sdkMessageFactory!!.msgSettingBasal(ap550Pump!!.activeProfile, ap550PumpPlugin!!.pumpDescription.basalMinimumRate))
                        preferences.put(Ap550LongNonKey.LastGetBasalTimestamp, now)
                    }

                    if (!ap550PumpPlugin!!.isThisProfileSet(profile) && !commandQueue.isRunning(
                            Command.CommandType.BASAL_PROFILE
                        )
                    ) {
                        rxBus!!.send(EventProfileNeedsUpdate())
                    }
                } else if (profile != null) {
                    notificationManager.dismiss(NotificationId.FAILED_UPDATE_PROFILE)
                }

                if (ap550Pump!!.getPumpTime() == 0L) {
                    ap550Pump!!.reset()
                    rxBus!!.send(EventAP550NewStatus())
                    rxBus!!.send(EventInitializationChanged())
                    return
                }

                rxBus!!.send(EventAP550NewStatus())
                rxBus!!.send(EventInitializationChanged())
                //NSUpload.uploadDeviceStatus();
                if (ap550Pump!!.dailyTotalUnits > ap550Pump!!.maxDailyTotalUnits * Constants.DAILY_RESERVOIR_LIMIT_WARNING) {
                    aapsLogger!!.debug(
                        LTag.PUMP,
                        "Approaching daily limit: " + ap550Pump!!.dailyTotalUnits + "/" + ap550Pump!!.maxDailyTotalUnits
                    )
                    if (System.currentTimeMillis() > lastApproachingDailyLimit + 30 * 60 * 1000) {
                        notificationManager.post(
                            NotificationId.APPROACHING_DAILY_LIMIT,
                            resourceHelper.gs(R.string.approaching_daily_limit),
                            NotificationLevel.URGENT
                        )
                        runBlocking {
                            pumpSync.insertAnnouncement(
                                resourceHelper.gs(R.string.approaching_daily_limit) + ": " + String.format(
                                    "%.3f", ap550Pump!!.dailyTotalUnits
                                ) + "/" + ap550Pump!!.maxDailyTotalUnits + "U",
                                null,
                                PumpType.AP550S,
                                ap550Pump!!.serialNumber
                            )
                        }
                        lastApproachingDailyLimit = System.currentTimeMillis()
                    }
                } else {
                    notificationManager.dismiss(NotificationId.APPROACHING_DAILY_LIMIT)
                }

                if (ap550Pump!!.pumpVersionString != BEFORE_DCN18_VERSION_STRING) {
                    if (preferences.get(Ap550BooleanNonKey.IsGetLogIndex) == false || ap550Pump!!.basicIndex < 0 || ap550Pump!!.otherIndex < 0) {
                        aapsLogger!!.debug(LTag.PUMPCOMM, "IsGetLogIndex=${preferences.get(Ap550BooleanNonKey.IsGetLogIndex)} otherIndex=${ap550Pump!!.otherIndex} basicIndex=${ap550Pump!!.basicIndex}")
                        val mesGetLogIndex = sdkMessageFactory!!.msgGetLogIndex()
                        sendMessage(mesGetLogIndex)
                    }
                }

            } catch (e: Exception) {
                aapsLogger!!.error("Unhandled exception", e)
            }
        }

    private var reconnectLastTime: Long = 0

    // Connect attempts run here, one at a time. The attempt waits for the MTU exchange (up to 10 s)
    // and the version read (up to 5 s), and those waits must not block the caller: the command
    // queue calls connect() from its loop and has to keep sending its once-per-second status
    // events while the attempt runs, like it does for the DanaRS and Medtrum drivers.
    private val connectExecutor = Executors.newSingleThreadExecutor()

    /** The running connect attempt, so a caller that needs the link can wait for it to finish. */
    private var connectAttempt: Future<*>? = null

    /**
     * True from the connect() call until the attempt finished, on the attempt's own thread or on
     * the caller's. This is what [isConnecting] reports. Android never reports STATE_CONNECTING
     * in the GATT callback, and the attempt itself runs on a background thread, so a separate
     * flag is the only honest source for it.
     */
    @Volatile
    private var connectAttemptRunning = false

    /** Upper bound for waiting on a connect attempt. An attempt takes at most ~15 s (MTU + version read). */
    private val joinConnectAttemptTimeoutSec = 30L
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    fun connect(reason: String) {
        // Keep skipping the watchdog: the bluetooth radio was just toggled, so an immediate connect
        // would fail anyway. The next "Connection needed" call retries once the radio is back.
        if(reason == "watchdog") {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "connect reason: $reason")
            return
        }
        // The common CommandExecutor (the old QueueWorker) calls this with "Connection needed" once a
        // second while the pump is disconnected. The old fork skipped those calls here and needed a
        // custom QueueWorker to reconnect with a special reason. That is not possible any more, so the
        // call is honoured instead; the rate limit below keeps the retry pace at one attempt per 12
        // seconds, like the old "Reconnect by 15s" queue loop did.
        // By now the permission must be granted (the connect below needs it too), so this is
        // the moment the bound service becomes a foreground service after a late grant.
        maybeStartForeground()
        // Reconnect at most once every 12 seconds
        if(System.currentTimeMillis() - reconnectLastTime < 12 * 1000)
        {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "connect: too soon")
            return
        }
        // One attempt at a time. While an attempt runs, isConnecting stays true, so the command
        // queue waits instead of calling connect() again.
        if (connectAttemptRunning) {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "connect: attempt already running")
            return
        }
        reconnectLastTime = System.currentTimeMillis()
        // Tell the rest of the driver the link is being dialled. isConnecting() below reports the
        // attempt flag; this keeps the internal state honest for disconnected()/disconnecting().
        mConnectionState = STATE_CONNECTING
        connectAttemptRunning = true
        val closeOldLink = !disconnected()
        connectAttempt = try {
            connectExecutor.submit {
                try {
                    if (closeOldLink) {
                        aapsLogger!!.debug(LTag.PUMPBTCOMM, "connect: Trying to disconnect")
                        disconnect("connect status is disconnected")
                        SystemClock.sleep(500)
                    }
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "connect: Trying to connect disconnected")
                    bTGATTForSelectedPump
                } finally {
                    // The attempt body can leave early (bad MAC, missing permission, failed MTU or
                    // version read). Clear the connecting state so the queue can try again.
                    if (mConnectionState != STATE_CONNECTED) mConnectionState = STATE_DISCONNECTED
                    // Set last: isConnected() and isConnecting() report on this flag.
                    connectAttemptRunning = false
                }
            }
        } catch (e: Exception) {
            // Only when the service is shutting down. Undo, so the queue can retry later.
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "connect: submit failed: ${e.message}")
            connectAttemptRunning = false
            mConnectionState = STATE_DISCONNECTED
            null
        }
    }

    /**
     * Waits until the running connect attempt has finished (link up or attempt given up).
     *
     * [connect] returns before the link is up, so a caller that needs the result right away -
     * the pairing wizard - waits for the attempt this way instead.
     */
    fun joinConnectAttempt() {
        try {
            connectAttempt?.get(joinConnectAttemptTimeoutSec, TimeUnit.SECONDS)
        } catch (e: Exception) {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "joinConnectAttempt: ${e.message}")
        }
    }

    fun disconnectCommand() {
        val msgDisconnectBle = sdkMessageFactory!!.msgDisconnectBle()
        sendMessage(msgDisconnectBle)
    }
    // Helper that checks the Bluetooth address format
    private fun isValidBluetoothAddress(address: String?): Boolean {
        if (address.isNullOrEmpty()) return false

        // Standard Bluetooth MAC format: XX:XX:XX:XX:XX:XX (six pairs of hex digits)
        val pattern = "([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}".toRegex()
        return pattern.matches(address)
    }

    fun stopResumePump(status: Int): PumpEnactResult {
        val result = pumpEnactResultProvider()
        if (!isConnected) {
            aapsLogger?.error(LTag.PUMPBTCOMM, "stopResumePump: Bluetooth is not connected")
            return result.success(false).enacted(false).comment(rh!!.gs(R.string.ap550_bt_not_connected))
        }
        val msgStopInjection = sdkMessageFactory!!.msgStopInjection(status)
        sendMessage(msgStopInjection)
        return when {
            !msgStopInjection.isReceived -> {
                aapsLogger?.error(LTag.PUMPBTCOMM, "stopResumePump: the pump did not answer, communication timeout")
                result.success(false).enacted(false).comment(rh!!.gs(R.string.ap550_pump_no_response))
            }
            msgStopInjection.controlResult == 0 -> {
                aapsLogger?.error(LTag.PUMPBTCOMM, "stopResumePump: pump control failed, injection status=${msgStopInjection.pumpInjectionStatus}")
                result.success(false).enacted(false).comment(rh!!.gs(R.string.ap550_pump_control_failed))
            }
            else -> {
                aapsLogger?.debug(LTag.PUMPBTCOMM, "stopResumePump: control ok, injection status=${msgStopInjection.pumpInjectionStatus}")
                result.success(true).enacted(true).comment("OK")
            }
        }
    }

    fun mutePump() {
        // The pump is muted: turn the mute off
         if(ap550Pump?.pumpMuteStatus == true){
             val msgPumpMute = sdkMessageFactory!!.msgSetPumpMute(0)  // 0: unmute, 1: mute
             sendMessage(msgPumpMute)
         } else {
             val msgPumpMute = sdkMessageFactory!!.msgSetPumpMute(1)
             sendMessage(msgPumpMute)
         }
    }

    protected val bTGATTForSelectedPump: Unit
        get() {

            mDevMac = ap550Pump?.mac_address_for_pair
            mDevName = ap550Pump?.device_name_for_pair
            if(StringUtils.isEmpty(mDevName) || mDevName == "" || StringUtils.isEmpty(mDevMac) || mDevMac == "")
            {
                ap550Pump?.device_name_for_pair = preferences.get(Ap550StringNonKey.BtDeviceName)
                ap550Pump?.mac_address_for_pair = preferences.get(Ap550StringNonKey.MacAddress)
                ap550Pump?.sn_for_pair = preferences.get(Ap550StringNonKey.SerialNumber)
                aapsLogger?.debug(LTag.PUMPBTCOMM, "mDevName = " + mDevName)
                aapsLogger?.debug(LTag.PUMPBTCOMM, "mDevMac = " + mDevMac)
                aapsLogger?.debug(LTag.PUMPBTCOMM, "ap550Pump?.device_name_for_pair = " + ap550Pump?.device_name_for_pair)
                aapsLogger?.debug(LTag.PUMPBTCOMM, "ap550Pump?.mac_address_for_pair = " + ap550Pump?.mac_address_for_pair)
                aapsLogger?.debug(LTag.PUMPBTCOMM, "ap550Pump?.sn_for_pair = " + ap550Pump?.sn_for_pair)
            }
            mDevMac = ap550Pump?.mac_address_for_pair
            mDevName = ap550Pump?.device_name_for_pair
            // Check whether the MAC address is empty or invalid
            if (StringUtils.isEmpty(mDevMac) || mDevMac == "" || !isValidBluetoothAddress(mDevMac)) {
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "No correct MAC address saved for pump")
                return
            }
            // mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
            val mBluetoothAdapter = bluetoothManager.adapter
            aapsLogger?.debug(LTag.PUMPBTCOMM, "mDevName=$mDevName, mDevMac=$mDevMac mBluetoothAdapter != null=${mBluetoothAdapter!= null}")
            if (mBluetoothAdapter != null && !StringUtils.isEmpty(mDevMac)) {
                mBTDevice = mBluetoothAdapter.getRemoteDevice(mDevMac)
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
                    aapsLogger!!.debug(
                        LTag.PUMPBTCOMM,
                        "connect: Checking if we have permissions to connect"
                    )
                    if (ActivityCompat.checkSelfPermission(
                            this, Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        aapsLogger!!.debug(
                            LTag.PUMPBTCOMM,
                            "connect: We do not have permissions to connect  return"
                        )
                        return
                    }
                }

                mBluetoothDeviceName = mBTDevice!!.getName()
                mBluetoothDeviceAddress = mBTDevice!!.getAddress()
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "connect: Trying to create a new connection.")
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
                    if (ActivityCompat.checkSelfPermission(
                            this, Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                }
                aapsLogger!!.debug(
                    LTag.PUMPBTCOMM,
                    "connect: Trying to create a new connection. mBTDevice!!.connectGatt"
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    aapsLogger!!.debug(
                        LTag.PUMPBTCOMM,
                        "connect: Trying to create a new connection. mBTDevice!!.connectGatt. BluetoothDevice.TRANSPORT_LE"
                    )
                    mBluetoothGatt = mBTDevice!!.connectGatt(
                        applicationContext, false, mGattCallback, BluetoothDevice.TRANSPORT_LE
                    )
                } else {
                    aapsLogger!!.debug(
                        LTag.PUMPBTCOMM,
                        "connect: Trying to create a new connection. mBTDevice!!.connectGatt. BluetoothDevice.TRANSPORT_CLASSIC"
                    )
                    mBluetoothGatt =
                        mBTDevice!!.connectGatt(applicationContext, false, mGattCallback)
                }

                aapsLogger!!.debug(LTag.PUMPBTCOMM, "connect: Trying to create a new connection, mtuExchangeSuccess=$mtuExchangeSuccess")
                // Wait for the MTU exchange
                // Wait at most 10 seconds. Leave the loop early if the MTU exchange succeeds.
                for (i in 0..19) {
                    if (mtuExchangeSuccess) {
                        val isSuccess = readVersion()
                        aapsLogger!!.debug(LTag.PUMPBTCOMM, "readVersion $isSuccess")

                        if (!isSuccess) {
                            aapsLogger!!.debug(LTag.PUMPBTCOMM, "readVersion timeout")
                            mHandshakeInProgress = false
                            disconnect("readVersion timeout")
                            return
                        } else if (ap550Pump!!.pumpVersionString != BEFORE_DCN18_VERSION_STRING) {
                            aapsLogger!!.debug(LTag.PUMPBTCOMM, "DCN18 pump, read the dynamic key, pumpVersionString=${ap550Pump!!.pumpVersionString}")
                            readMessage()
                        }
                        aapsLogger!!.debug(LTag.PUMPBTCOMM, "MTU exchange success ap550Pump!!.paired=${ap550Pump!!.paired}")
                        if (!ap550Pump!!.paired) {
                            sendAuthentication()
                        }
                        break
                    } else {
                        SystemClock.sleep(500)
                    }
                    if (i >= 19 && !mtuExchangeSuccess) {
                        mHandshakeInProgress = false
                        disconnect("MTU exchange failed")
                        aapsLogger!!.debug(LTag.PUMPBTCOMM, "connect failed")
                        return
                    }
                }

            } else {
                rxBus.send(EventShowSnackbar(resourceHelper!!.gs(R.string.no_bt_adapter), EventShowSnackbar.Type.Error))
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "No bluetooth adapter found, please check device is paired")
                return
            }
            if (mBTDevice == null) {
                rxBus.send(EventShowSnackbar(resourceHelper!!.gs(R.string.device_not_found), EventShowSnackbar.Type.Error))
                return
            }
        }
    private val mGattCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        @Synchronized
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            mStatus = status // for display in system status
            if (status == 133) {
                error133++
            } else {
                error133 = 0
            }
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "onState change status = $status, newState = $newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTING -> {
                    mConnectionState = STATE_CONNECTING
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "onConnectionStateChange: State connecting.")
                    rxBus!!.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, 0))
                }

                BluetoothProfile.STATE_CONNECTED -> if (status == 0) {
                    mConnectionState = STATE_CONNECTED
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "onConnectionStateChange: Connected to GATT server. ap550Pump!!.paired=${ap550Pump!!.paired} isConnected=$isConnected")
                    if (ap550Pump!!.paired) {
                        rxBus!!.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED, 0))
                    }
                    handleConnectedStateChange()
                } else {
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "Not accepting CONNECTED change as status code is: $status")
                    if (status == 133) {
                        aapsLogger!!.debug(LTag.PUMPBTCOMM, "Treating as disconnected due to error 133 count: $error133")
                    }
                }

                BluetoothProfile.STATE_DISCONNECTING -> {
                    mConnectionState = STATE_DISCONNECTING
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "onConnectionStateChange: State disconnecting.")
                    rxBus!!.send(
                        EventPumpStatusChanged(
                            EventPumpStatusChanged.Status.DISCONNECTING, 0
                        )
                    )
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "onConnectionStateChange: State disconnected, mtuExchangeSuccess=$mtuExchangeSuccess.")
                    mConnectionState = STATE_DISCONNECTED
                    // Review fix (I-3): on disconnect, clear the codec frame buffer and ready queue, so
                    // a leftover frame from the last connection cannot be consumed after a reconnect.
                    // The session key and the encryption switch are kept: the original code never
                    // dropped the key on disconnect. linkCodec is a lazily initialized nullable field,
                    // so calling it is safe when the codec was never initialized (the handshake never
                    // finished).
                    linkCodec?.reset()
                    ap550Pump!!.paired = false
                    rxBus!!.send(
                        EventPumpStatusChanged(
                            EventPumpStatusChanged.Status.DISCONNECTED, 0
                        )
                    )
//                    handleTempBasalWhenDisconnect()
                    handleDisconnectedStateChange()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Services discovered start")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "onServicesDiscovered received: $status")
                return
            }
            val gattService = mBluetoothGatt!!.getService(AP550_SERVICE_UUID)
            if (gattService == null) {
                listAvailableServices(mBluetoothGatt)
            } else {
                mCharacteristicReceiver = gattService.getCharacteristic(AP550_DATA_TX_GATT_CHARACTERISTIC_UUID)
                if (mCharacteristicReceiver == null) {
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "onServicesDiscovered: characteristic $AP550_DATA_TX_GATT_CHARACTERISTIC_UUID not found")
                }
                val charaProp = mCharacteristicReceiver?.properties
                if (charaProp != null) {
                    if (charaProp or BluetoothGattCharacteristic.PROPERTY_NOTIFY > 0) {
                        try {
                            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
                                if (ActivityCompat.checkSelfPermission(
                                        this@AP550ExecutionService,
                                        Manifest.permission.BLUETOOTH_CONNECT
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    return
                                }
                            }
                            mBluetoothGatt?.setCharacteristicNotification(mCharacteristicReceiver, true)
                            val ap550Descriptor = mCharacteristicReceiver?.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                            aapsLogger!!.debug(LTag.PUMPBTCOMM, "AP550 Bluetooth Notification Descriptor found: " + ap550Descriptor?.uuid)
                            if (ap550Descriptor != null) {
                                ap550Descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            }
                            mBluetoothGatt!!.writeDescriptor(ap550Descriptor)
                        } catch (e: Exception) {
                            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Error setting notification value descriptor: $e")
                        }
                    }
                }
                mCharacteristicSend = gattService.getCharacteristic(AP550_DATA_RX_GATT_CHARACTERISTIC_UUID)
                if (mCharacteristicSend == null) {
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "onServicesDiscovered: AP550 characteristic $AP550_DATA_RX_GATT_CHARACTERISTIC_UUID not found")
                    return
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    SystemClock.sleep(200)
//                    mtuExchangeSuccess = exchangeGattMtu(512)
                    exchangeGattMtu(512)
                }

                if (isConnected) {
                    mHandshakeInProgress = false
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "onCharacteristicRead " + characteristic.uuid)
            val bytes = characteristic.value
            for (i in bytes.indices) {
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "onCharacteristicRead value " + bytes[i])
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "onCharacteristicWrite " + characteristic.uuid)
        }

        override fun onDescriptorRead(
            gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int
        ) {
            super.onDescriptorRead(gatt, descriptor, status)
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "onDescriptorRead " + descriptor.uuid)
            val bytes = descriptor.value
            for (i in bytes.indices) {
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "onCharacteristicChanged value " + bytes[i])
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int
        ) {
            super.onDescriptorWrite(gatt, descriptor, status)
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "onDescriptorWrite " + descriptor.uuid)
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            if(mtu > 128) {
                mtuExchangeSuccess = true
            }
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "onMtuChanged: " + "device name: " + mBluetoothDeviceName + ", device address: " + mBluetoothDeviceAddress + ", MTU = " + mtu + ", status = " + status)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            super.onReadRemoteRssi(gatt, rssi, status)
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "onReadRemoteRssi " + getGattStatusMessage(status) + ": " + rssi)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic
        ) {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "onCharacteristicChanged-- " + characteristic.uuid)
            if (characteristic == null || characteristic.value == null) {
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "characteristic data is null")
            }

            // Task 17: plaintext frames stop at the SDK. The raw GATT bytes (ciphertext for a DCN18
            // pump) go straight into LinkCodec. Decryption, buffering, frame splitting and the CRC
            // check all happen inside the SDK, so no plaintext frame leaves the codec.
            val readyCount = linkCodec!!.feed(characteristic.value)
            if (readyCount == 0) return

            // Task 13b: one dispatch path for both routes: SDK handleResponse for the pure parsing,
            // and the applyXxx methods on the Service for the side effects. A reply to a pending
            // request hits the processedMessage instance (the SDK instance the factory built), so a
            // waiter on message.waitMillis in sendMessage can be notified and read the parse result.
            // Everything else (pump pushes and replies with no pending request) uses the cached
            // instance from the hash table. The codec decides the prefer match frame by frame, so
            // when several frames arrive together only the frame of the matching command uses
            // processedMessage.
            val prefer = processedMessage
            repeat(readyCount) {
                val (message, payload) = linkCodec!!.resolveReadyFrame(prefer) { matched -> rebuildForParse(matched) }
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "receivedMessage: ${message.messageName} (${message.javaClass.simpleName})")
                message.isReceived = true
                dispatchResponse(message, payload)
                synchronized(message) { message.notify() }
            }
        }
    }

    /**
     * Displays all services and characteristics for debugging purposes.
     *
     * @param bluetoothGatt BLE gatt profile.
     */
    private fun listAvailableServices(bluetoothGatt: BluetoothGatt?) {
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "Listing available services:")
        for (service in bluetoothGatt!!.services) {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Service: " + service.uuid.toString())
            for (characteristic in service.characteristics) {
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "|-- Characteristic: " + characteristic.uuid.toString())
            }
        }
    }

    private fun exchangeGattMtu(mtu: Int): Boolean {
        var retry = 5
        var status = false
        while (false == status && retry > 0) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return status
                }
            }
            status = mBluetoothGatt!!.requestMtu(mtu)
            if (status) {
                break
            } else {
                retry--
            }
        }
        val dataLog = formatForRootLocale(
            "[%1\$s | %2\$s] %3\$s request, MTU: %4\$d, Status: %5\$d",
            mBluetoothDeviceName,
            mBluetoothDeviceAddress,
            "Exchange GATT MTU",
            mtu,
            if (status) 0x00 else 0x01
        )
        aapsLogger!!.debug(LTag.PUMPBTCOMM, dataLog)
        return status
    }

    @Synchronized
    private fun handleConnectedStateChange() {
        if (mBluetoothGatt != null) {
            val result =
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R && ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                } else {
                    mBluetoothGatt!!.discoverServices()
                }
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "discover services: $result")
        } else {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Wanted to discover services but gatt was null!")
        }
    }

    private fun getGattStatusMessage(status: Int): String {
        val statusMessage: String
        statusMessage = if (status == BluetoothGatt.GATT_SUCCESS) {
            "SUCCESS"
        } else if (status == BluetoothGatt.GATT_FAILURE) {
            "FAILED"
        } else if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
            "NOT PERMITTED"
        } else if (status == 133) {
            "Found the strange 133 bug"
        } else {
            "UNKNOWN ($status)"
        }
        return statusMessage
    }

    @Synchronized
    private fun handleDisconnectedStateChange() {
        mtuExchangeSuccess = false
        disconnect("disconnect")
        // Clear it on disconnect, so the next connection does not reuse it
        // ap550Pump?.mac_address_for_pair = ""
        // ap550Pump?.device_name_for_pair = ""
        // ap550Pump?.sn_for_pair          = ""
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "BLE disconnected in ble callback")
    }

    private fun handleTempBasalWhenDisconnect() {
        val nowTimestamp = dateUtil!!.now()
        val runningTempBasal = runBlocking { processedTbrEbData.getTempBasalIncludingConvertedExtended(nowTimestamp) }
        if (runningTempBasal != null) {
            preferences.put(Ap550BooleanNonKey.IsTempBasalRunningAfterDisconnect, true)
            val timestamp = runningTempBasal.timestamp
            val serialNumber = runningTempBasal.ids.pumpSerial
            val duration =runningTempBasal.duration
            aapsLogger!!.debug("A running basal found at pump disconnect, time: $timestamp, duration: $duration, pump serial: $serialNumber")
            preferences.put(Ap550LongNonKey.TempBasalRunningTimestamp, timestamp)
            preferences.put(Ap550LongNonKey.TempBasalRunningDisconnectTimestamp, nowTimestamp)
            preferences.put(Ap550StringNonKey.TempBasalRunningSerialNumber, serialNumber!!)
            preferences.put(Ap550LongNonKey.TempBasalRunningDuration, duration)
        }
    }

    // True when the GATT link is up AND the connect attempt (MTU exchange, version read,
    // authentication) has finished, so messages can actually be sent. The GATT callback sets
    // STATE_CONNECTED on link up only; the attempt flag covers the rest. Before the connect
    // went async, connect() blocked the caller until the attempt was done, which had the same
    // effect.
    val isConnected: Boolean
        get() = mConnectionState == STATE_CONNECTED && !connectAttemptRunning

    private enum class DISCOVERED {
        NULL, PENDING, COMPLETE
    }


    @Synchronized
    fun readVersion() : Boolean {
        // A disconnect from another thread (stopConnecting on queue timeout, user disconnect) can
        // close the link while this attempt is still waiting. Give up quietly instead of crashing.
        if (mBluetoothGatt == null) return false
        // Read the pump DCN
        val deviceDcnService = mBluetoothGatt!!.getService(AP550_DEVICE_ID)
        if (deviceDcnService == null) {
            listAvailableServices(mBluetoothGatt)
            return false
        } else {
            val dcnCharacteristic = deviceDcnService.getCharacteristic(AP550_DATA_DCN_GATT_CHARACTERISTIC_UUID)
            if (dcnCharacteristic == null) {
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "onServicesDiscovered: characteristic $AP550_DATA_DCN_GATT_CHARACTERISTIC_UUID not found")
                return false
            }
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
                if (ActivityCompat.checkSelfPermission(
                        this@AP550ExecutionService,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }
            mBluetoothGatt!!.readCharacteristic(dcnCharacteristic)
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Reading device version characteristic")

            // Wait up to 5 seconds, checking every 500 ms
            val startTime = System.currentTimeMillis()
            val timeout = 5000 // 5 second timeout
            var count = 0

            while (System.currentTimeMillis() - startTime < timeout) {
                synchronized(dcnCharacteristic) {
                    try {
                        count++
                        dcnCharacteristic.waitMillis(500)
                    } catch (e: InterruptedException) {
                        aapsLogger!!.error(LTag.PUMPBTCOMM, "readVersion InterruptedException", e)
                        return false
                    }
                }

                if (dcnCharacteristic.value != null) {
                    val versionBytes = dcnCharacteristic.value
                    val version = String(versionBytes, Charsets.UTF_8)
                    ap550Pump!!.pumpVersionString = version
                    // Task 17: the encryption switch follows the pump version down into LinkCodec
                    // (DCN18 and newer use the AES/GCM layer)
                    linkCodec!!.setEncryptionEnabled(version != BEFORE_DCN18_VERSION_STRING)
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "Device version: $version")
                    return true
                }

                aapsLogger!!.error(LTag.PUMPBTCOMM, "Read version count $count")
            }
            return false
        }
    }

    @Synchronized
    fun readMessage() {
        if (mConnectionState != BluetoothProfile.STATE_CONNECTED) {
            aapsLogger!!.error(LTag.PUMPBTCOMM, "GATT not connected on readMessage")
            return
        }

        if (mCharacteristicSend == null || mBluetoothGatt == null) {
            aapsLogger!!.error(LTag.PUMPBTCOMM, "GATT or Characteristic is NULL")
            return
        }

        try {
            val isSuccess =
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R &&
                    ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    aapsLogger!!.error(LTag.PUMPBTCOMM, "Bluetooth permission not granted")
                    return
                } else {
                    mBluetoothGatt!!.readCharacteristic(mCharacteristicSend)
                }

            aapsLogger!!.debug(LTag.PUMPBTCOMM, "readCharacteristic initiated, isSuccess = $isSuccess")

            // Wait for the read to finish
            synchronized(mCharacteristicSend!!) {
                try {
                    mCharacteristicSend!!.waitMillis(2000) // Wait up to 2 seconds
                } catch (e: InterruptedException) {
                    aapsLogger!!.error(LTag.PUMPBTCOMM, "readMessage InterruptedException", e)
                }
            }

            // Check whether the read succeeded
            if (mCharacteristicSend!!.value != null) {
                val value = mCharacteristicSend!!.value
                // Task 17: the raw session key only goes into LinkCodec, which keeps its own copy.
                // It is never stored in an app variable and never logged.
                linkCodec!!.setSessionKey(value)
            } else {
                aapsLogger!!.error(LTag.PUMPBTCOMM, "No data received from characteristic")
            }

        } catch (e: Exception) {
            aapsLogger!!.error(LTag.PUMPBTCOMM, "readMessage exception: ", e)
        }
    }

    @Synchronized
    fun sendMessage(message: SdkMessageBase?) {
        if (mConnectionState != BluetoothProfile.STATE_CONNECTED) {
            aapsLogger!!.error(LTag.PUMPBTCOMM, "GATT not connected on sendMessage")
            return
        }
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "sendMessage: ${message?.messageName} (${message?.javaClass?.simpleName})")
        processedMessage = message
        // Task 17 plaintext frame cleanup: frame assembly/encryption happens inside LinkCodec,
        // the app side only gets the bytes to write to GATT
        val wireBytes = linkCodec!!.encodeOutgoing(message!!)
        val desc = message.messageClassName
        if (!desc.isNullOrEmpty()) {
            ap550Pump?.scanStatusRecord(desc)
        }
        Thread.sleep(200)
        if (mCharacteristicSend == null || mBluetoothGatt == null) {
            aapsLogger!!.error(LTag.PUMPBTCOMM, "GATT or Characteristic is NULL")
            return
        }
        try {
            // Whether to encrypt (DCN18/older pumps) is decided by LinkCodec internal state;
            // with encryption on but no key set, the behavior matches the old one (write null)
            mCharacteristicSend!!.value = wireBytes

//            mCharacteristicSend!!.value = messageBytes
            val isSuccess =
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R && ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                } else {
                    mBluetoothGatt!!.writeCharacteristic(mCharacteristicSend)
                }
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "isSuccess = $isSuccess")
        } catch (e: Exception) {
            aapsLogger!!.error(LTag.PUMPBTCOMM, "sendMessage write exception: ", e)
        }
        if(message.messageName != "CMD_DISCONNECT_BLE" && message.messageName != "CMD_SEND_HOST_ID") {
            synchronized(message) {
                try {
                    message.waitMillis(2000)
                } catch (e: InterruptedException) {
                    aapsLogger!!.error(LTag.PUMPBTCOMM, "sendMessage InterruptedException", e)
                }
            }
        }

        // Wait for the pump reply
        if (!message.isReceived) {
            dispatchNotReceived(message)
            val desc = message.messageClassName
            if (!desc.isNullOrEmpty()) {
                ap550Pump?.scanStatusRecord("Not Received: $desc")
            }
//            if(message.messageName=="CMD_SETTING_V_MAX_VALUE_I"){  // no reply to this timed command, treat Bluetooth as disconnected
//                mConnectionState = STATE_DISCONNECTED
//                rxBus!!.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED, 0))
//            }
            aapsLogger!!.error(LTag.PUMPBTCOMM, "Test_ble_connect Reply not received " + message.messageName)
            if (message is SdkMsgCheckValue) {
                ap550Pump!!.isNewPump = false
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "Old firmware detected")
            }
        }
    }

    // Task 13b: timeout path cleanup. The old sendMessage timeout branch called
    // message.handleMessageNotReceived() directly. The MsgSettingPumpTime timeout
    // implementation (resetPumpTime) has side effects and was not migrated with the
    // pure-parsing SDK rework, so this method dispatches it instead;
    // all other classes keep the empty base class implementation.
    private fun dispatchNotReceived(message: SdkMessageBase) {
        when (message) {
            is SdkMsgSettingPumpTime -> ap550Pump!!.resetPumpTime()
            else -> message.handleMessageNotReceived()
        }
    }

    // True while a connect attempt runs, from the connect() call until the attempt finished. The
    // command queue relies on this to keep its once-per-second status events going and to not send
    // commands while the attempt is still setting the link up.
    val isConnecting: Boolean
        get() = connectAttemptRunning

    fun disconnecting(): Boolean {
        return mConnectionState == STATE_DISCONNECTING
    }

    fun disconnected(): Boolean {
        return mConnectionState == STATE_DISCONNECTED
    }

    val isHandshakeInProgress: Boolean
        get() = isConnected && mHandshakeInProgress

    fun finishHandshaking() {
        mHandshakeInProgress = false
    }

    fun disconnect(reason: String): Boolean {
        if (mBluetoothGatt != null) {
            // refreshDeviceCache(mBluetoothGatt)
            mConnectionState = STATE_DISCONNECTED
            mtuExchangeSuccess = false
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
                if (ActivityCompat.checkSelfPermission(
                        this, Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return false
                }
            }
            mBluetoothGatt!!.disconnect()
            handleTempBasalWhenDisconnect()
            SystemClock.sleep(100)
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "mBluetoothGatt is not null")
        } else {
            mConnectionState = STATE_DISCONNECTED
            mtuExchangeSuccess = false
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "mBluetoothGatt is null")
            return false
        }
        //Creating a new instance of GATT before connect
        if (mBluetoothGatt != null) {
            mBluetoothGatt!!.close() // Disposing off previous connection resources
            mBluetoothGatt = null
        }
        if (timer != null) {
            timer!!.cancel()
        }
        if (task != null) {
            task!!.cancel()
        }
        return try {
            System.runFinalization()
            timer = null
            task = null
            ap550Pump!!.paired = false
            // Force the connection state to disconnected
            mConnectionState = BluetoothProfile.STATE_DISCONNECTED
            mtuExchangeSuccess = false
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Disconnected: $reason")
            true
        } catch (e: Exception) {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, e.message!!)
            false
        }
    }

    fun checkPumpVersion(): Boolean {
        sendMessage(sdkMessageFactory!!.msgInitConnStatusTime())
        val aapsDcnVersion = preferences.get(Ap550IntNonKey.DcnVersion)
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "versionPairCheck appsDcnVersion:" + aapsDcnVersion + " ap550Pump.getPumpVersionCode1():" + ap550Pump!!.pumpVersionCode1)
        return ap550Pump!!.pumpVersionCode1 == aapsDcnVersion
    }

    /**
     * Method to clear the device cache
     *
     * @param gatt
     * @return boolean
     */
    private fun refreshDeviceCache(gatt: BluetoothGatt?): Boolean {
        try {
            val refresh = gatt!!.javaClass.getMethod("refresh")
            if (refresh != null) {
                return refresh.invoke(gatt) as Boolean
            }
        } catch (ex: Exception) {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "An exception occurred while refreshing device")
        }
        return false
    }

    private fun unpairDevice(device: BluetoothDevice): Boolean {
        return try {
            invokeBluetoothDeviceMethod(device, "removeBond") as Boolean
        } catch (e: Exception) {
            false
        }
    }

    fun stopConnecting() {
        disconnect("stopConnecting")
        // Raise a reconnect failed alarm; no popup while not paired
        var sn_for_pair: String = ""
        sn_for_pair = preferences.get(Ap550StringNonKey.SerialNumber)
        if (!StringUtils.isEmpty(sn_for_pair)) {
            notificationManager.post(
                NotificationId.PUMP_UNREACHABLE,
                resourceHelper!!.gs(R.string.ap550s_connect_fail),
                NotificationLevel.URGENT,
                sound = AlarmSound.ALARM
            )
        }
    }

    fun startOtherActivity() {
        val componentName = ComponentName("com.sisensing.sisensingcgm", "com.sisensing.launcher.LauncherActivity")
        val intent = Intent().setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.component = componentName
        context?.startActivity(intent)
        Log.d("PullAPP", "run")
    }

    fun tempBasal(percent: Int, durationInHours: Int): Boolean {
        if (!isConnected) return false
        if (setPumpTime()) {
            tempbasalRecord()   // pull temp basal records before setting a temp basal
        }
        if (ap550Pump!!.isTempBasalInProgress) {
            rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.stopping_temp_basal)))
            sendMessage(sdkMessageFactory!!.msgSetTempBasalStop())
            SystemClock.sleep(500)
        }
        rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.setting_temp_basal)))
        sendMessage(sdkMessageFactory!!.msgSetTempBasalStart(ap550Pump!!.pumpVersionCode1, percent, durationInHours))
        sendMessage(sdkMessageFactory!!.msgStatusTempBasal(ap550Pump!!.pumpVersionCode1))
        //rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true
    }

    fun tempBasalAbsolute(absoluteRate: Double, durationInHours: Int): Boolean {
        if (!isConnected) return false
        if (setPumpTime()) {
            tempbasalRecord()   // pull temp basal records before setting a temp basal
        }
        if (ap550Pump!!.isTempBasalInProgress) {
            rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.stopping_temp_basal)))
            sendMessage(sdkMessageFactory!!.msgSetTempBasalStop())
            SystemClock.sleep(500)
        }
        rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.setting_temp_basal)))
        sendMessage(sdkMessageFactory!!.msgSetTempBasalAbsoluteStart(absoluteRate, durationInHours))
        sendMessage(sdkMessageFactory!!.msgStatusTempBasalAbsolute())
        //rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true
    }

    fun tempBasalStop(): Boolean {
        if (!isConnected) return false
        rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.stopping_temp_basal)))
        sendMessage(sdkMessageFactory!!.msgSetTempBasalStop())
        sendMessage(sdkMessageFactory!!.msgStatusTempBasal(ap550Pump!!.pumpVersionCode1))
        //rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true
    }

    fun getPumpExtendedBolusStatus() {
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "Getting pump ExtendedBolus status")
        if (!isConnected) {
             return
        }
        sendMessage(sdkMessageFactory!!.msgStatusBolusExtended())
    }

    fun extendedBolus(insulin: Double, durationInHalfHours: Int): Boolean {
        if (!isConnected) return false
        bolusRecord("Before AAPS extended bolus")     // pull all bolus records from the pump (including square wave and dual wave) before sending an extended bolus
        rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.setting_extended_bolus)))
        sendMessage(sdkMessageFactory!!.msgSetExtendedBolusStart(insulin, (durationInHalfHours and 0xFF).toByte()))
        sendMessage(sdkMessageFactory!!.msgStatusBolusExtended())
        //rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true
    }

    fun extendedBolusStop(): Boolean {
        if (!isConnected) return false
        rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.stopping_extended_bolus)))
        sendMessage(sdkMessageFactory!!.msgSetExtendedBolusStop())
        sendMessage(sdkMessageFactory!!.msgStatusBolusExtended())
        //rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
        return true
    }

    fun bolusStop() {
        aapsLogger!!.debug(
            LTag.PUMP,
            "bolusStop >>>>> @ " + (bolusProgressData.state.value?.delivered?.cU ?: 0.0)
        )
        val stop = sdkMessageFactory!!.msgBolusStop(false)
        ap550Pump!!.bolusStopForced = true
        if (isConnected) {
            sendMessage(stop)
            while (!ap550Pump!!.bolusStopped) {
                sendMessage(stop)
                SystemClock.sleep(200)
            }
        } else {
            ap550Pump!!.bolusStopped = true
        }
    }

    fun loadHistory(type: Byte): PumpEnactResult {
        aapsLogger!!.debug(LTag.PUMP, "Ap550History")
        val result = pumpEnactResultProvider()
        if (!isConnected) return result
        var msg: SdkMessageBase? = null
        when (type) {
            RecordTypes.RECORD_TYPE_ALARM -> msg = sdkMessageFactory!!.msgHistoryAlarm()
            RecordTypes.RECORD_TYPE_BASALHOUR -> msg = sdkMessageFactory!!.msgHistoryBasalHour()
            RecordTypes.RECORD_TYPE_BOLUS -> msg = sdkMessageFactory!!.msgHistoryBolus()
            RecordTypes.RECORD_TYPE_CARBO -> msg = sdkMessageFactory!!.msgHistoryCarbo()
            RecordTypes.RECORD_TYPE_DAILY -> msg = sdkMessageFactory!!.msgHistoryDailyInsulin()
            RecordTypes.RECORD_TYPE_ERROR -> msg = sdkMessageFactory!!.msgHistoryError()
            RecordTypes.RECORD_TYPE_GLUCOSE -> msg = sdkMessageFactory!!.msgHistoryGlucose()
            RecordTypes.RECORD_TYPE_REFILL -> msg = sdkMessageFactory!!.msgHistoryRefill()
            RecordTypes.RECORD_TYPE_SUSPEND -> msg = sdkMessageFactory!!.msgHistorySuspend()
            RecordTypes.RECORD_TYPE_PARAMMODIFY -> msg = sdkMessageFactory!!.msgHistoryParamModify()
            RecordTypes.RECORD_TYPE_BASALMODIFY -> msg = sdkMessageFactory!!.msgHistoryBasalModify()
        }
        ap550Pump!!.historyDoneReceived = false
        //sendMessage(new MsgPCCommStart(injector));
        SystemClock.sleep(400)
        sendMessage(msg)
        while (!ap550Pump!!.historyDoneReceived && isConnected) {
            SystemClock.sleep(100)
        }
        SystemClock.sleep(200)
        //sendMessage(new MsgPCCommStop(injector));
        result.success = true
        result.comment = "OK"
        return result
    }

    protected fun waitForWholeMinute() {
        while (true) {
            val time: Long = dateUtil?.now()!!
            val timeToWholeMinute = 60000 - time % 60000
            if (timeToWholeMinute > 59800 || timeToWholeMinute < 3000) break
            rxBus!!.send(
                EventPumpStatusChanged(
                    resourceHelper!!.gs(
                        R.string.waiting_for_time_synchronization,
                        (timeToWholeMinute / 1000).toInt()
                    )
                )
            )
            SystemClock.sleep(Math.min(timeToWholeMinute, 100))
        }
    }

    fun loadEvents(): PumpEnactResult? {
        return null
    }

    fun setPumpTime(): Boolean {
        if (!isConnected || !ap550Pump!!.paired) return false
        // Update the pump time before a bolus
        sendMessage(sdkMessageFactory!!.msgSettingPumpTime())
        val now = System.currentTimeMillis()
        var timeDiff = (ap550Pump!!.getPumpTime() - now) / 1000L
        aapsLogger!!.debug(LTag.PUMP, "setPumpTime Pump time difference1: $timeDiff seconds  ap550Pump!!.getPumpTime()=${ap550Pump!!.getPumpTime()}")

        if (abs(timeDiff) > 10) {   // 10 seconds tolerance is allowed; it relates to the time spent storing a bolus, do not increase it lightly
            val pumpTime: LocalDateTime =
                Instant.ofEpochMilli(ap550Pump!!.getPumpTime()).atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
            aapsLogger!!.debug(LTag.PUMP, "setPumpTime pumpTime=${pumpTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            if (pumpTime.year < 2026 || abs(timeDiff) < 24 * 60 * 60) {
                sendMessage(sdkMessageFactory!!.msgSetTime(dateUtil?.now()!!))
                val formattedDate =
                    Instant.ofEpochMilli(dateUtil?.now()!!).atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                aapsLogger!!.debug(LTag.PUMP, "setPumpTime Pump time1: $formattedDate")

                sendMessage(sdkMessageFactory!!.msgSettingPumpTime())
                val formattedDate1 =
                    Instant.ofEpochMilli(ap550Pump!!.getPumpTime()).atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                timeDiff = (ap550Pump!!.getPumpTime() - System.currentTimeMillis()) / 1000L
                aapsLogger!!.debug(LTag.PUMP, "setPumpTime Pump time2: $formattedDate1")
                aapsLogger!!.debug(LTag.PUMP, "setPumpTime Pump time difference2: $timeDiff seconds")

                if (abs(timeDiff) > 10) {   // 10 seconds tolerance is allowed; it relates to the time spent storing a bolus, do not increase it lightly
                    if (abs(timeDiff) < 3 * 60) {
                        aapsLogger!!.debug(LTag.PUMP, "AAPS and pump differ by more than 10 seconds but less than 3 minutes")
                        return false
                    } else {
                        resourceHelper.let {
                            notificationManager.post(
                                NotificationId.INSIGHT_DATE_TIME_UPDATED,
                                it.gs(R.string.pump_time_updated_failed),
                                NotificationLevel.URGENT,
                                validMinutes = 60
                            )
                        }
                        ap550Pump!!.lastSyncPumpTime = System.currentTimeMillis()
                        return false
                    }
                } else {
                    notificationManager.dismiss(NotificationId.INSIGHT_DATE_TIME_UPDATED)
                    resourceHelper.let {
                        notificationManager.post(
                            NotificationId.INSIGHT_DATE_TIME_UPDATED,
                            it.gs(R.string.pump_time_updated),
                            NotificationLevel.INFO,
                            validMinutes = 30
                        )
                    }
                    return true
                }
            } else {
                resourceHelper.let {
                    notificationManager.post(
                        NotificationId.INSIGHT_DATE_TIME_UPDATED,
                        it.gs(R.string.pump_1h_time_change_requested),
                        NotificationLevel.URGENT,
                        validMinutes = 60
                    )
                }
                return false
            }
        }
        return true
    }

    fun bolus(
        amount: Double, carbs: Int, carbtime: Long, bolusId: Long, bolusType: BS.Type
    ): Boolean {
        if (!isConnected) return false
        if (bolusProgressData.isStopPressed) return false
        if (setPumpTime()) {  // update the pump time before a bolus; bolus history is pulled only if the update succeeds
            aapsLogger!!.debug(LTag.PUMP, "BolusRecord before Bolus")
            bolusRecord("Before AAPS bolus")     // pull the pump bolus history before delivering a bolus
        }
        ap550Pump!!.bolusingBolusId = bolusId
        ap550Pump!!.bolusDone = false
        val bolustype = when (bolusType) {
            BS.Type.NORMAL -> 0
            BS.Type.SMB -> 1
            BS.Type.PRIMING -> 2
        }
        val start = sdkMessageFactory!!.msgBolusStart(amount, bolustype, ap550Pump!!.pumpVersionString == BEFORE_DCN18_VERSION_STRING)
        ap550Pump!!.bolusStopped = false
        ap550Pump!!.bolusStopForced = false

        if (carbs > 0) {
            sendMessage(sdkMessageFactory!!.msgSetCarbsEntry(carbtime, carbs))
        }
        if (amount > 0) {
            ap550Pump!!.bolusingBolusId = bolusId
            ap550Pump!!.bolusAmountToBeDelivered = amount
            if (!ap550Pump!!.bolusStopped && !bolusProgressData.isStopPressed) {
                sendMessage(start)
            } else {
                return false
            }
            while (!ap550Pump!!.bolusStopped && !start.failed) {
                // The wait here must be longer than 1 second, because the pump bolus delivery
                // depends on its 1-second interrupt. Waiting only 1 second is too tight, so we
                // use 3 seconds to avoid AAPS errors when the pump interrupt is late
                SystemClock.sleep(3000)
                if (System.currentTimeMillis() - ap550Pump!!.bolusProgressLastTimeStamp > 15 * 1000L) { // if i didn't receive status for more than 15 sec expecting broken comm
                    ap550Pump!!.bolusStopped = true
                    ap550Pump!!.bolusStopForced = true
                    aapsLogger!!.debug(LTag.PUMP, "Communication stopped")
                    ap550Pump!!.bolusStartErrorCode = 12 // communication error
                    runBlocking { pumpSync.insertAnnouncement(rh!!.gs(R.string.ap550_bolus_comm_interrupted), null, ap550Pump!!.pumpType(), ap550Pump!!.serialNumber) }
                }
            }
            SystemClock.sleep(300)
            ap550Pump!!.bolusingBolusId = -1L
            // bolus() runs inside CommandBolus.execute() on the single command executor. Awaiting an
            // enqueued READSTATUS here deadlocks the queue, so CommandBolus never finishes and the
            // bolus progress dialog never closes. Enqueue without waiting; the executor runs the
            // READSTATUS right after this command ends.
            scope.launch { commandQueue.readStatus("bolusOK") }
        }
        return !start.failed
    }

    fun carbsEntry(amount: Int): Boolean {
        if (!isConnected) return false
        val msg = sdkMessageFactory!!.msgSetCarbsEntry(System.currentTimeMillis(), amount)
        sendMessage(msg)
        return true
    }

    fun highTempBasal(percent: Int, durationInMinutes: Int): Boolean {
        return false
    }

    fun tempBasalShortDuration(percent: Int, durationInMinutes: Int): Boolean {
        return false
    }

    fun updateBasalsInPump(profile: Profile?): BasalUpdateResult {
        if (!isConnected) return BasalUpdateResult.DISCONNECTED
        rxBus!!.send(EventPumpStatusChanged(resourceHelper!!.gs(R.string.updating_basal_rates)))
        val basal = ap550Pump!!.buildDanaRProfileRecord(profile!!)
        val msgSet = sdkMessageFactory!!.msgSetSingleBasalProfile(basal)
        sendMessage(msgSet)

        if (msgSet.isreceived && !msgSet.failed) {
            ap550Pump!!.lastSettingsRead = 0 // force read full settings
            ap550Pump!!.lastSyncPumpTime = 0
            //rxBus.send(new EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING));
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Test_ble_connect updateBasalsInPump get_pumpStatus")
            pumpStatus
            return BasalUpdateResult.SUCCESS
        } else if (msgSet.isreceived && msgSet.failed) {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Basal rate setting failed")
            return BasalUpdateResult.FAILED
        } else {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "No reply received from the pump")
            return BasalUpdateResult.DISCONNECTED
        }
    }

    fun setUserOptions(): PumpEnactResult? {
        return null
    }

    override fun onDestroy() {
        scope.cancel()
        if (timer != null) {
            timer!!.cancel()
        }
        if (task != null) {
            task!!.cancel()
        }
        timer = null
        task = null
        scheduledExecutor.shutdown()
        connectExecutor.shutdownNow()
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED, 0))
        BLEStateReceiver.unregister(applicationContext)
        localBleSwitchReceiver?.let {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(it)
        }
        super.onDestroy()
    }

    // =========================================================================================
    // ===== SDK receive dispatch (Task 13a) =====
    // Pure parsing is done by the SDK handleResponse(bytes); the side effects of the old Msg
    // class handleMessage methods were migrated 1:1 into the applyXxx methods of this Service,
    // as registered under "skipped side effects (Task 13)".
    // =========================================================================================
    // Task 17 plaintext frame cleanup: parse instances are rebuilt ahead of parsing
    // (migrated 1:1 from the head of the old dispatchResponse when).
    // The cached hashtable instances are built with dummy constructor arguments, and some
    // classes parse using the runtime state at send time (9 classes),
    // the instance is rebuilt from the current pump state after a response and put back into
    // the hashtable (same semantics as the original send-time constructor arguments);
    // MsgErrorArray.injectingErrorCode is runtime state, originally passed during response
    // handling, now injected before parsing after the cleanup.
    // This method is called from the LinkCodec.resolveReadyFrame adapt callback
    // (before parsing; plaintext frames never leave the codec).
    private fun rebuildForParse(message: SdkMessageBase): SdkMessageBase =
        when (message) {
            is SdkMsgBolusProgress ->
                sdkMessageFactory!!.msgBolusProgress(ap550Pump!!.bolusAmountToBeDelivered).also { sdkMessageHashTable!!.put(it) }
            is SdkMsgBolusPumpSendSyn ->
                sdkMessageFactory!!.msgBolusPumpSendSyn(ap550Pump!!.maxBolus, ap550Pump!!.pumpVersionString == BEFORE_DCN18_VERSION_STRING).also { sdkMessageHashTable!!.put(it) }
            is SdkMsgStatusProfile ->
                sdkMessageFactory!!.msgStatusProfile(ap550Pump!!.units == AP550Pump.UNITS_MGDL).also { sdkMessageHashTable!!.put(it) }
            is SdkMsgStatusTempBasal ->
                sdkMessageFactory!!.msgStatusTempBasal(ap550Pump!!.pumpVersionCode1).also { sdkMessageHashTable!!.put(it) }
            is SdkMsgSettingBasal ->
                sdkMessageFactory!!.msgSettingBasal(ap550Pump!!.activeProfile, ap550PumpPlugin!!.pumpDescription.basalMinimumRate).also { sdkMessageHashTable!!.put(it) }
            is SdkMsgSettingBasalProfileAll ->
                sdkMessageFactory!!.msgSettingBasalProfileAll(ap550Pump!!.basal48Enable).also { sdkMessageHashTable!!.put(it) }
            is SdkMsgErrorArray ->
                sdkMessageFactory!!.msgErrorArray(ap550Pump!!.pumpVersionCode1)
                    .also { it.injectingErrorCode = ap550Pump!!.injectingErrorCode }
                    .also { sdkMessageHashTable!!.put(it) }
            is SdkMsgTempBasalHistory ->
                sdkMessageFactory!!.msgTempBasalHistory(0L, ap550Pump!!.pumpVersionString == BEFORE_DCN18_VERSION_STRING).also { sdkMessageHashTable!!.put(it) }
            is SdkMsgRemainingInsulin ->
                sdkMessageFactory!!.msgRemainingInsulin(0L, ap550Pump!!.pumpVersionString == BEFORE_DCN18_VERSION_STRING).also { sdkMessageHashTable!!.put(it) }
            else -> message
        }

    private fun dispatchResponse(message: SdkMessageBase, payload: Any?) {
        // Task 17 plaintext frame cleanup: frame parsing is done inside the SDK Ap550LinkCodec
        // (plaintext frames never leave the codec),
        // this method only dispatches the decoded payload (POJO) to the applyXxx side-effect
        // methods; the old message.handleResponse(bytes) branches map 1:1 to payload type checks.
        // Review fix (I-4b): the SDK parse exception fallback (I-4a) and the per-Msg length
        // guards can make payload null
        // (like the existing "handleResponse returns null" precedents, e.g. unknown
        // MsgHistoryAll types);
        // in that case dispatch is skipped entirely, keeping the precedent semantics
        // (no applyXxx call, message.failed untouched),
        // to avoid an unsafe `payload as XxxData` cast crash in the branches below
        if (payload == null) {
            aapsLogger!!.warn(LTag.PUMPBTCOMM, "dispatchResponse: ${message.messageName} payload is null, skip apply")
            return
        }
        when (message) {
            is SdkMsgBolusProgress -> applyBolusProgress(payload as SdkBolusProgressData)
            is SdkMsgBolusPumpSendSyn -> applyBolusPumpSendSyn(payload as BolusPumpSendSynData)
            is SdkMsgBolusStart -> {
                val data = payload as BolusStartData
                message.failed = data.errorCode != 0
                applyBolusStart(data)
            }
            is SdkMsgBolusStartWithSpeed -> {
                val result = payload as Int
                message.failed = result != 0
                applyBolusStartWithSpeed(result)
            }
            is SdkMsgBolusStop -> applyBolusStop()
            is SdkMsgBumpLog -> applyBumpLog(payload as? PumpLogData)
            is SdkMsgCfgChangeIndexSync -> applyCfgChangeIndexSync(payload as? Int)
            is SdkMsgCfgChangeRecordSync -> applyCfgChangeRecordSync(payload as? CfgChangeRecordData)
            is SdkMsgCheckValue -> applyCheckValue(payload as CheckValueData)
            is SdkMsgConnectAuthTimeOut -> applyConnectAuthTimeOut(payload as Int)
            is SdkMsgError -> {
                val data = payload as ErrorData
                message.failed = data.errorCode < 7
                applyError(data)
            }
            is SdkMsgErrorArray -> applyErrorArray(payload as ErrorArrayData)
            is SdkMsgGetBolusTempbasalLog -> applyGetBolusTempbasalLog(payload as? PumpLogData)
            is SdkMsgGetBolusTempbasalLogIndex -> applyGetBolusTempbasalLogIndex(payload as? BolusTempbasalLogIndexData)
            is SdkMsgGetBumpBasal -> applyGetBumpBasal(payload as DoubleArray)
            is SdkMsgGetLogIndex -> applyGetLogIndex(payload as? LogIndexData)
            is SdkMsgHistoryAll -> applyHistoryRecord(payload as? HistoryRecordData)
            is SdkMsgHistoryDone -> applyHistoryDone()
            is SdkMsgHistoryNewDone -> applyHistoryNewDone()
            is SdkMsgInitConnPairing -> applyInitConnPairing(payload as Boolean)
            is SdkMsgInitConnPairingCode -> applyInitConnPairingCode(payload as Int)
            is SdkMsgInitConnStatusBasic -> applyInitConnStatusBasic(payload as? InitConnStatusBasicData)
            is SdkMsgInitConnStatusBolus -> applyInitConnStatusBolus(payload as? InitConnStatusBolusData)
            is SdkMsgInitConnStatusOption -> applyInitConnStatusOption(payload as? InitConnStatusOptionData)
            is SdkMsgInitConnStatusTime -> applyInitConnStatusTime(payload as? InitConnStatusTimeData)
            is SdkMsgRemainingInsulin -> applyRemainingInsulin(payload as? RemainingInsulinData)
            is SdkMsgResetVentRecordIndexSync -> applyResetVentRecordIndexSync(payload as? Int)
            is SdkMsgResetVentRecordSync -> applyResetVentRecordSync(payload as? VentRecordData)
            is SdkMsgSetBasalProfile -> {
                val result = payload as Int
                message.failed = result != 1
                applySetBasalProfile(result)
            }
            is SdkMsgSetExtendedBolusStart -> {
                val result = payload as Int
                message.failed = result != 0
                applySetExtendedBolusStart(result)
            }
            is SdkMsgSetExtendedBolusStop -> {
                val result = payload as Int
                message.failed = !(result == 1 || result == 2)
                applySetExtendedBolusStop(result)
            }
            is SdkMsgSetSingleBasalProfile -> {
                val result = payload as Int
                message.failed = result != 0
                applySetSingleBasalProfile(result)
            }
            is SdkMsgSetTempBasalAbsoluteStart -> {
                val result = payload as Int
                message.failed = result != 0
                applySetTempBasalAbsoluteStart(result)
            }
            is SdkMsgSetTempBasalStart -> {
                val result = payload as Int
                message.failed = result != 0
                applySetTempBasalStart(result)
            }
            is SdkMsgSetTempBasalStop -> {
                val result = payload as Int
                message.failed = !(result == 1 || result == 2)
                applySetTempBasalStop(result)
            }
            is SdkMsgSetTime -> {
                val result = payload as Int
                message.failed = result != 0
                applySetTime(result)
            }
            is SdkMsgSettingBasal -> applySettingBasal(payload as SettingBasalData)
            is SdkMsgSettingBasalProfileAll -> applySettingBasalProfileAll(payload as SettingBasalProfileAllData)
            is SdkMsgSettingGlucose -> applySettingGlucose(payload as SettingGlucoseData)
            is SdkMsgSettingMaxValues -> applySettingMaxValues(payload as SettingMaxValuesData)
            is SdkMsgSettingMeal -> applySettingMeal(payload as SettingMealData)
            is SdkMsgSettingProfileRatios -> applySettingProfileRatios(payload as SettingProfileRatiosData)
            is SdkMsgSettingProfileRatiosAll -> applySettingProfileRatiosAll(payload as SettingProfileRatiosAllData)
            is SdkMsgSettingPumpTime -> applySettingPumpTime(payload as Long)
            is SdkMsgSettingShippingInfo -> applySettingShippingInfo(payload as SettingShippingInfoData)
            is SdkMsgSettingUserOptions -> applySettingUserOptions(payload as? SettingUserOptionsData ?: return)
            is SdkMsgStatus -> applyStatus(payload as StatusData)
            is SdkMsgStatusBasic -> applyStatusBasic(payload as StatusBasicData)
            is SdkMsgStatusBolusExtended -> applyStatusBolusExtended(payload as StatusBolusExtendedData)
            is SdkMsgStatusProfile -> applyStatusProfile(payload as StatusProfileData)
            is SdkMsgStatusPumpMute -> applyStatusPumpMute(payload as StatusPumpMuteData)
            is SdkMsgStatusTempBasal -> applyStatusTempBasal(payload as StatusTempBasalData)
            is SdkMsgStatusTempBasalAbsolute -> applyStatusTempBasalAbsolute(payload as StatusTempBasalAbsoluteData)
            is SdkMsgTempBasalHistory -> applyTempBasalHistory(payload as? TempBasalHistoryData)
            // Classes registered in the hashtable with no app-side side effects: parsing is
            // done by LinkCodec (base class UNPROCESSED log); nothing more to do here
            is SdkMsgCRCError, is SdkMsgDisconnectBle, is SdkMsgHostId, is SdkMsgPCCommStart, is SdkMsgPCCommStop,
            is SdkMsgSetPumpMute, is SdkMsgStopInjection, is SdkMsgBgReadingsDataFromTime -> Unit
            is SdkMsgSettingActiveProfile -> applySettingActiveProfile(payload as Int)
            is SdkMsgSetCarbsEntry -> {
                val result = payload as Int
                message.failed = result != 1
            }
            is SdkMsgSetActivateBasalProfile -> {
                val result = payload as Int
                message.failed = result != 1
            }
            else -> {
                // Classes with no registered side effects: keep the SDK base class
                // UNPROCESSED log (called by the internal LinkCodec handleResponse)
            }
        }
    }

    // The old MsgBolusProgress.handleMessage side effects
    private fun applyBolusProgress(data: SdkBolusProgressData) {
        ap550Pump!!.bolusProgressLastTimeStamp = data.bolusProgressLastTimeStamp
        if (data.percent == 100) {
            ap550Pump!!.bolusStopped = data.bolusStopped
            ap550Pump!!.bolusStopForced = data.bolusStopForced
        }
        aapsLogger!!.debug(LTag.PUMPCOMM, "Delivered insulin so far: ${data.deliveredInsulin}, progress:${data.percent}")
        // Core owns the progress state (percent + status text); the driver only reports delivered insulin.
        bolusProgressData.updateProgress(PumpInsulin(data.deliveredInsulin))
    }

    // The old MsgBolusStop.handleMessage side effects
    private fun applyBolusStop() {
        // 仅置位 bolusStopped 让主循环退出，不再把进度强行推到 100%。
        //
        // 背景：泵上手动停止 CMD_MEALINS_STOP 到来时，bolusStopForced=false（该标志
        // 只在 AAPS 主动按停止键或通信超时时置 true）。旧代码走 updateProgress(100)
        // 分支，BolusProgressData.updateProgress(percent) 会按"总量 × percent%"反推
        // delivered → 把真实输注量（例如 0.25 U）伪造成整量（1.0 U），导致
        // deliverTreatment() 的 abs(requested - delivered) < bolusStep 判断为 true →
        // success=true → CommandBolus 走 completeAndAutoClear（而非 clear），
        // WizardBolusExecutor 的 BOLUS_DELIVERY_FAILED 告警永远不会触发，数据库
        // IOB 也按整量记录。
        //
        // 移除后：delivered 由最后一条 CMD_PUMP_THIS_REMAINDER_MEAL_INS 进度帧的
        // applyBolusProgress() 写入，deliverTreatment() 读到真实值后判 success=false，
        // CommandBolus.clear() 立刻清空 UI 并触发告警。
        //
        // AAPS 主动按停止键的场景（bolusStopForced=true）本来就跳过这个分支，
        // 不受影响；泵正常完成时 percent 会自然递增到 100，updateProgress(100)
        // 是幂等操作，同样不受影响。
        ap550Pump!!.bolusStopped = true
    }

    // The old MsgBolusStart.handleMessage side effects
    private fun applyBolusStart(data: BolusStartData) {
        val errorCode = data.errorCode
        if (errorCode != 0) {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Messsage response: $errorCode FAILED!!")
            ap550Pump!!.scanStatusRecord(rh!!.gs(R.string.ap550_bolus_error_reason, rh!!.gs(connectUtil.errorCode2StringRes(errorCode, 1))))
        } else {
            if (data.injectingErrorCode != null) {
                ap550Pump!!.injectingErrorCode = data.injectingErrorCode!!
            }
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Messsage response: $errorCode OK")
            rxBus!!.send(EventBolusStart(true))   // bolus delivery started
            data.lastBolusStartTimestamp?.let { ap550Pump!!.lastBolusStartTimestamp = it }
        }
        ap550Pump!!.bolusStartErrorCode = errorCode
    }

    // The old MsgBolusStartWithSpeed.handleMessage side effects
    private fun applyBolusStartWithSpeed(errorCode: Int) {
        if (errorCode != 0) {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Messsage response: $errorCode FAILED!!")
        } else {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Messsage response: $errorCode OK")
        }
        ap550Pump!!.bolusStartErrorCode = errorCode
    }

    // The old MsgBolusPumpSendSyn.handleMessage side effects
    private fun applyBolusPumpSendSyn(data: BolusPumpSendSynData) {
        ap550Pump!!.lastBolusTime = data.lastBolusTime
        ap550Pump!!.lastBolusAmount = data.lastBolusAmount
        rxBus!!.send(EventBolusChange())
        rxBus!!.send(EventHypoglycemiaAlertCheck())   // after the pump finishes a bolus, trigger the hypoglycemia risk check; it must run before the bolus data is stored
        if (data.lastBolusAmount > 0) {
            // Compatibility for syncing a bolus finished on the pump:
            // (1) DCN18 and above: pull it from the pump
            // (2) Below DCN18: keep the old approach and store it directly (risk of duplicate bolus)
            if (ap550Pump!!.pumpVersionString != BEFORE_DCN18_VERSION_STRING) {
                aapsLogger!!.debug(LTag.PUMPBTCOMM, "DCN18 pump, pumpVersionString=${ap550Pump!!.pumpVersionString}")
                // Get the pump bolus by sending the pull-bolus command
                ap550PumpPlugin!!.getBolusRecord()
            } else {
                runBlocking {
                    pumpSync.syncBolusWithPumpId(
                        data.lastBolusTime,
                        PumpInsulin(data.lastBolusAmount),
                        BS.Type.NORMAL,
                        data.lastBolusTime,
                        PumpType.AP550S,
                        ap550Pump!!.serialNumber
                    )
                }
            }
        }
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "ap550Pump.lastBolusAmount = ${ap550Pump!!.lastBolusAmount}")
    }

    // The old MsgBumpLog.handleMessage side effects (log parsing/printing is done SDK-side)
    private fun applyBumpLog(data: PumpLogData?) {
        if (data == null) return
        if (data.packageSum <= 0) {
            if (data.logType == 1) {
                aapsLogger!!.debug(LTag.PUMPCOMM, "No basal log, getting other logs")
                rxBus!!.send(EventGetOtherLog(0))
            }
        } else {
            if (data.logType == 0) {
                ap550Pump!!.otherIndex = data.lastIndex
            } else {
                ap550Pump!!.basicIndex = data.lastIndex
            }
            if (data.packageSum == data.packageNum && data.logType == 1) {
                aapsLogger!!.debug(LTag.PUMPCOMM, "Basal log fully received, getting other logs")
                rxBus!!.send(EventGetOtherLog(0))
            }
        }
    }

    // The old MsgGetBolusTempbasalLog.handleMessage side effects (this command is currently unused)
    private fun applyGetBolusTempbasalLog(data: PumpLogData?) {
        if (data == null) return
        if (data.packageSum <= 0) {
            if (data.logType == 1) {
                aapsLogger!!.debug(LTag.PUMPCOMM, "No bolus log, getting temp basal logs")
                rxBus!!.send(EventGetOtherLog(2))
            }
        } else {
            if (data.logType == 1) {
                ap550Pump!!.bolusIndex = data.lastIndex
            } else {
                ap550Pump!!.tempBasalIndex = data.lastIndex
            }
            if (data.packageSum == data.packageNum && data.logType == 1) {
                aapsLogger!!.debug(LTag.PUMPCOMM, "Bolus log fully received, getting temp basal logs")
                rxBus!!.send(EventGetOtherLog(2))
            }
        }
    }

    // The old MsgGetLogIndex.handleMessage side effects
    private fun applyGetLogIndex(data: LogIndexData?) {
        if (data == null) return
        preferences.put(Ap550BooleanNonKey.IsGetLogIndex, true)
        ap550Pump!!.otherIndex = data.otherIndex
        ap550Pump!!.basicIndex = data.basicIndex
    }

    // The old MsgGetBolusTempbasalLogIndex.handleMessage side effects
    private fun applyGetBolusTempbasalLogIndex(data: BolusTempbasalLogIndexData?) {
        if (data == null) return
        preferences.put(Ap550BooleanNonKey.IsGetBolusTempbasalLogIndex, true)
        ap550Pump!!.tempBasalIndex = data.tempBasalIndex
        ap550Pump!!.bolusIndex = data.bolusIndex
    }

    // The old MsgGetBumpBasal.handleMessage side effects
    private fun applyGetBumpBasal(rates: DoubleArray) {
        aapsLogger!!.debug(LTag.PUMPCOMM, "Received the reply with the pump basal rates")
        // Adjusted for insulin concentration so it can be compared with the raw pump rates below.
        val runningProfile = runBlocking { profileFunction!!.getProfile() }?.toPump()
        if (runningProfile != null) {
            for (index in 0 until 48) {
                val rate = rates[index]
                val profilerate = runningProfile.getBasalTimeFromMidnight(index / 2 * 60 * 60)
                if (abs(rate - profilerate) > ap550PumpPlugin!!.pumpDescription.basalStep) {
                    aapsLogger!!.debug(LTag.PUMPCOMM, "Basal rate mismatch: pump: $rate profile: $profilerate")
                    preferences.put(Ap550BooleanNonKey.IsBasalSame, false)
                    break
                }
                aapsLogger!!.debug(LTag.PUMPCOMM, "Basal rate match: pump: $rate profile: $profilerate")
            }
        }
    }

    // The old MsgCfgChangeIndexSync.handleMessage side effects
    private fun applyCfgChangeIndexSync(index: Int?) {
        if (index == null) return
        if (ap550Pump!!.pumpIsFirstBindForCfgChangeRecord == true) {
            ap550Pump!!.pumpIsFirstBindForCfgChangeRecord = false
            preferences.put(Ap550IntNonKey.PumpConfigChangeRecordIndex, index)
            aapsLogger!!.debug(LTag.PUMPCOMM, "pump is firstly bind, reset the CFG Change index in SP ${preferences.get(Ap550IntNonKey.PumpConfigChangeRecordIndex)}")
        } else {
            aapsLogger!!.debug(LTag.PUMPCOMM, "AAPS receive cfg change index, in SP = ${preferences.get(Ap550IntNonKey.PumpConfigChangeRecordIndex)}, Pump index = $index")
            // Not the first pairing: compare the index stored in SP with the one reported by
            // the pump; if the SP one is smaller, the history records must be synced
            if (preferences.get(Ap550IntNonKey.PumpConfigChangeRecordIndex) < index) {
                rxBus!!.send(EventGetCfgChangeRecord(preferences.get(Ap550IntNonKey.PumpConfigChangeRecordIndex)))
            }
        }
    }

    // The old MsgCfgChangeRecordSync.handleMessage side effects
    private fun applyCfgChangeRecordSync(data: CfgChangeRecordData?) {
        if (data == null) return
        if (data.flag == 0x00) {
            aapsLogger!!.debug(LTag.PUMPCOMM, "config change record sync over")
        } else {
            aapsLogger!!.debug(LTag.PUMPCOMM, "config change record[${data.index}]: ${dateUtil!!.dateAndTimeString(data.time)} flag:${data.flag} res:${data.res} value:${data.value}")
            // Update the reset/prime history record index
            ap550Pump!!.cfgchangeIndex = data.index + 1
            preferences.put(Ap550IntNonKey.PumpConfigChangeRecordIndex, data.index + 1)
            if (data.flag == 25 || data.flag == 17 || data.flag == 28) {
                val notes = when (data.flag) {
                    17 -> "PUMP"
                    25 -> "OTHER"
                    28 -> "AAPS"
                    else -> "UNKNOWN"
                }
                if (data.res == 0) {
                    // Suspend delivery
                    pumpSyncInsert(
                        timestamp = data.time,
                        type = TE.Type.PUMP_SUSPENDED,
                        note = notes,
                        pumpId = null,
                        pumpType = PumpType.AP550S,
                        pumpSerial = ap550Pump!!.serialNumber
                    )
                } else if (data.res == 1) {
                    // Resume delivery
                    pumpSyncInsert(
                        timestamp = data.time,
                        type = TE.Type.PUMP_RESUMED,
                        note = notes,
                        pumpId = null,
                        pumpType = PumpType.AP550S,
                        pumpSerial = ap550Pump!!.serialNumber
                    )
                }
                aapsLogger!!.debug(LTag.PUMPCOMM, "Pump delivery state, state = ${if (data.res == 0) "suspended" else "resumed"}, note = $notes")
            }
        }
    }

    // The old MsgResetVentRecordIndexSync.handleMessage side effects
    private fun applyResetVentRecordIndexSync(index: Int?) {
        if (index == null) return
        if (ap550Pump!!.pumpIsFirstBindForVentResetRecord == true) {
            ap550Pump!!.pumpIsFirstBindForVentResetRecord = false
            preferences.put(Ap550IntNonKey.PumpResetAndVentRecordIndex, index)
            aapsLogger!!.debug(LTag.PUMPCOMM, "pump is firstly bind, reset the Vent Reset index in SP ${preferences.get(Ap550IntNonKey.PumpResetAndVentRecordIndex)}")
        } else {
            aapsLogger!!.debug(LTag.PUMPCOMM, "AAPS receive vent reset index, in SP = ${preferences.get(Ap550IntNonKey.PumpResetAndVentRecordIndex)}, Pump index = $index")
            // Not the first pairing: compare the index stored in SP with the one reported by
            // the pump; if the SP one is smaller, the history records must be synced
            if (preferences.get(Ap550IntNonKey.PumpResetAndVentRecordIndex) < index) {
                rxBus!!.send(EventGetResetVentRecord(preferences.get(Ap550IntNonKey.PumpResetAndVentRecordIndex)))
            }
        }
    }

    // The old MsgResetVentRecordSync.handleMessage side effects
    private fun applyResetVentRecordSync(data: VentRecordData?) {
        if (data == null) return
        if (data.type == 0x00) {
            aapsLogger!!.debug(LTag.PUMPCOMM, "record sync over")
        } else {
            aapsLogger!!.debug(LTag.PUMPCOMM, "reset vent record[${data.index}]: ${dateUtil!!.dateAndTimeString(data.time)} type:${data.type} value:${data.value}")
            // Update the reset/prime history record index
            ap550Pump!!.ventIndex = data.index + 1
            preferences.put(Ap550IntNonKey.PumpResetAndVentRecordIndex, data.index + 1)
            // Add the reset/prime history record entry to the care records
            val notes = when (data.type) {
                0x07 -> rh!!.gs(R.string.pump_fixed_vent_record, data.value / 10.0)
                0x08 -> rh!!.gs(R.string.pump_reset_vent_record)
                0x0D -> rh!!.gs(R.string.pump_vent_without_reset_record)
                else -> ""
            }
            aapsLogger!!.debug(LTag.PUMPCOMM, "Prime, time: ${dateUtil!!.dateAndTimeAndSecondsString(data.time)}, note: $notes")
            pumpSyncInsert(
                timestamp = data.time,
                type = TE.Type.PUMP_VENT,
                note = notes,
                pumpId = if (data.type == 0x07) data.value.toLong() else null,  // for a fixed amount prime, pass the primed value through pumpId
                pumpType = PumpType.AP550S,
                pumpSerial = ap550Pump!!.serialNumber
            )
        }
    }

    // The old MsgCheckValue.handleMessage side effects
    private fun applyCheckValue(data: CheckValueData) {
        ap550Pump!!.isNewPump = true
        aapsLogger!!.debug(LTag.PUMPCOMM, "New firmware confirmed")
        ap550Pump!!.hwModel = data.hwModel
        ap550Pump!!.protocol = data.protocol
        ap550Pump!!.productCode = data.productCode
        aapsLogger!!.debug(LTag.PUMPCOMM, "Model: " + String.format("%02X ", ap550Pump!!.hwModel))
        aapsLogger!!.debug(LTag.PUMPCOMM, "Protocol: " + String.format("%02X ", ap550Pump!!.protocol))
        aapsLogger!!.debug(LTag.PUMPCOMM, "Product Code: " + String.format("%02X ", ap550Pump!!.productCode))
    }

    // The old MsgConnectAuthTimeOut.handleMessage side effects
    private fun applyConnectAuthTimeOut(result: Int) {
        aapsLogger!!.debug(LTag.PUMPCOMM, "MsgConnectAuthTimeOut")
        if (result == 3) {
            aapsLogger!!.debug(LTag.PUMPCOMM, "Bluetooth wait timeout")
            // The scan screen was removed in the Compose rewrite; keep the action string so a future
            // pairing UI can still listen for the timeout.
            val intent = Intent(BLUETOOTH_INTENT_ACTION_CONNECT_TIME_OUT)
            LocalBroadcastManager.getInstance(context!!).sendBroadcast(intent)
        }
    }

    // The old MsgError.handleMessage side effects
    private fun applyError(data: ErrorData) {
        ap550Pump!!.bolusProgressLastTimeStamp = data.bolusProgressLastTimeStamp
        var errorString = ""
        when (data.errorCode) {
            1 -> errorString = rh!!.gs(R.string.deliver_treatment_error_code_04) // battery empty
            2 -> errorString = rh!!.gs(R.string.deliver_treatment_error_code_03) // motor error
            3 -> errorString = rh!!.gs(R.string.deliver_treatment_error_code_05) // insulin empty
            4 -> errorString = rh!!.gs(R.string.deliver_treatment_error_code_06) // occlusion
            5 -> errorString = rh!!.gs(R.string.deliver_treatment_error_code_07) // reservoir empty
            6 -> errorString = rh!!.gs(R.string.deliver_treatment_error_code_08) // tube detached
            7 -> errorString = rh!!.gs(R.string.deliver_treatment_error_code_10) // battery low
            8 -> errorString = rh!!.gs(R.string.deliver_treatment_error_code_11) // insulin low
        }
        data.injectingErrorCode?.let { ap550Pump!!.injectingErrorCode = it }
        if (data.bolusStopped) { // bolus delivering stopped
            ap550Pump!!.bolusStopped = true
            // Show the pump error in the running bolus dialog; percent and delivered stay as reported.
            bolusProgressData.updateProgress(
                bolusProgressData.state.value?.percent ?: 0,
                TextRef.Literal(errorString),
                bolusProgressData.state.value?.delivered ?: PumpInsulin(0.0)
            )
        }
        ap550Pump!!.scanStatusRecord(errorString)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Error detected: $errorString")
        // nsUpload.uploadError(errorString)
        if (ap550Pump!!.pumpVersionCode1 == 0 || ap550Pump!!.pumpVersionCode1 == 21) {   // only DCN15 sends it as a generic alarm
            runBlocking { pumpSync.insertAnnouncement(errorString, null, ap550Pump!!.pumpType(), ap550Pump!!.serialNumber) }
        }
    }

    // The old MsgErrorArray.handleMessage side effects
    private fun applyErrorArray(data: ErrorArrayData) {
        // Review fix (leak-medium): the old alarmHex log line was removed -
        // ErrorArrayData.alarmHex is gone, that info stays in the SDK encrypted log
        // nsUpload.uploadError(errorString)
        if (ap550Pump!!.pumpVersionCode1 >= 22) {   // DCN16 and above forward the alarms
            // Task 17 plaintext frame cleanup: forward only the decoded alarm codes
            // (the old implementation forwarded the whole plaintext frame as a ByteArray)
            rxBus!!.send(EventAlarmListToXiaozhi(data.alarmCodes))
            // The pump has an alarm; check whether the code list needs to be reset
            // (the reset logic is computed by the SDK from the raw bytes; kept as is when no match)
            if (data.injectingErrorCode != ap550Pump!!.injectingErrorCode) {
                aapsLogger!!.debug(LTag.PUMPCOMM, "pump alarm code: ${ap550Pump!!.injectingErrorCode}")
                ap550Pump!!.injectingErrorCode = data.injectingErrorCode
            }
        }
        if (ap550Pump!!.pumpVersionCode1 >= 24) { // DCN20 reports live pipe pressure and occlusion pressure
            aapsLogger!!.debug(LTag.PUMPCOMM, "current pump pipe pressure: ${data.pipePressure} mV, pump block pressure: ${data.blockPressure} mV")
        }
    }

    // The old MsgHistoryAll.handleMessage side effects (history records go to the database; shared by all history subclasses)
    private fun applyHistoryRecord(data: HistoryRecordData?) {
        if (data == null) return
        ap550HistoryRecordDao.createOrUpdate(
            Ap550HistoryRecord(
                timestamp = data.timestamp,
                code = data.code,
                value = data.value,
                bolusType = data.bolusType,
                stringValue = data.stringValue,
                duration = data.duration,
                dailyBasal = data.dailyBasal,
                dailyBolus = data.dailyBolus,
                alarm = data.alarm
            )
        )
        aapsLogger!!.debug(LTag.PUMPCOMM, "Ap550HistoryRecord: $data")
        // The old rxBus.send(EventDanaRSyncStatus(...)) relied on the parse-time messageType
        // text, which the POJO does not carry; registered as skipped in the SDK comments
    }

    // The old MsgHistoryDone.handleMessage side effects
    private fun applyHistoryDone() {
        ap550Pump!!.historyDoneReceived = true
    }

    // The old MsgHistoryAllDone.handleMessage side effects (0x41F1 was never registered in the
    // hashtable and never sent; removed by Task 13b)

    // The old MsgHistoryNewDone.handleMessage side effects
    private fun applyHistoryNewDone() {
        ap550Pump!!.historyDoneReceived = true
    }

    // The old MsgInitConnPairing.handleMessage side effects
    private fun applyInitConnPairing(received: Boolean) {
        ap550Pump!!.initPair = received
    }

    // The old MsgInitConnPairingCode.handleMessage side effects
    private fun applyInitConnPairingCode(result: Int) {
        if (result == 1) {
            ap550Pump!!.paired = true
            ap550Pump!!.pairCodeResult = result
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "pairing is success, time = " + ap550Pump!!.pairCodeResult)
            scope.launch {
                if (!commandQueue.statusInQueue()) {
                    commandQueue.readStatus("pair success read status")
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "pairing is success, need to read status")
                } else {
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "read status is in commandqueue, remove it")
                    commandQueue.cancelReadStatus()
                    commandQueue.readStatus("pair success read status")
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "pairing is success, need to read status")
                }
            }
            // Pump connected; dismiss the "connection failed" notification
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "MsgInitConnPairingCode - dismissing the \"connection failed\" notification")
            notificationManager.dismiss(NotificationId.PUMP_UNREACHABLE)
        } else if (result == 2) {
            ap550Pump!!.paired = false
            ap550Pump!!.pairCodeResult = result
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "pairing is failed, input pairing code error time = " + ap550Pump!!.pairCodeResult)
        } else if (result == 3) {
            ap550Pump!!.paired = false
            ap550Pump!!.pairCodeResult = result
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "pairing is failed, input pairing code error time = " + ap550Pump!!.pairCodeResult)
        } else {
            ap550Pump!!.paired = false
            ap550Pump!!.pairCodeResult = result
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "pairing is failed, input pairing code error time = " + ap550Pump!!.pairCodeResult + ", error is unknown")
        }
    }

    // The old MsgInitConnStatusBasic.handleMessage side effects
    private fun applyInitConnStatusBasic(data: InitConnStatusBasicData?) {
        if (data == null) return
        val tempPumpSuspended = ap550Pump!!.pumpSuspended
        ap550Pump!!.pumpSuspended = data.pumpSuspended
        ap550Pump!!.isEasyModeEnabled = data.isEasyModeEnabled
        ap550Pump!!.password = data.password
        aapsLogger!!.debug(LTag.PUMPCOMM, "isStatusSuspendOn: " + ap550Pump!!.pumpSuspended)
        aapsLogger!!.debug(LTag.PUMPCOMM, "isUtilityEnable: ${data.isUtilityEnable}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Is EasyUI Enabled: " + ap550Pump!!.isEasyModeEnabled)
        aapsLogger!!.debug(LTag.PUMPCOMM, "easyUIMode: ${data.easyUIMode}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Pump password: " + ap550Pump!!.password)
        if (tempPumpSuspended != ap550Pump!!.pumpSuspended) {
            val isAAPSStop = (System.currentTimeMillis() - ap550Pump!!.AAPSStopButtomTime <= 3000)
            val isAAPSResume = (System.currentTimeMillis() - ap550Pump!!.AAPSResumeButtomTime <= 3000)
            aapsLogger!!.debug(LTag.PUMPCOMM, "AAPSStopButtomTime: ${ap550Pump!!.AAPSStopButtomTime}" + " isAAPSStop: $isAAPSStop" + " AAPSResumeButtomTime: ${ap550Pump!!.AAPSResumeButtomTime}" + " isAAPSResume: $isAAPSResume")
            if (ap550Pump!!.pumpVersionString == BEFORE_DCN18_VERSION_STRING || ap550Pump!!.pumpVersionString == "2300") {
                if (ap550Pump!!.pumpSuspended && !isAAPSStop) {
                    ap550Pump!!.pumpType().let {
                        ap550Pump!!.serialNumber.let { it1 ->
                            pumpSyncInsert(
                                timestamp = dateUtil!!.now(),
                                type = TE.Type.PUMP_SUSPENDED,
                                note = "PUMP",
                                pumpId = null,
                                pumpType = it,
                                pumpSerial = it1
                            )
                        }
                    }
                } else if (ap550Pump!!.pumpSuspended && isAAPSStop) {
                    ap550Pump!!.pumpType().let {
                        ap550Pump!!.serialNumber.let { it1 ->
                            pumpSyncInsert(
                                timestamp = dateUtil!!.now(),
                                type = TE.Type.PUMP_SUSPENDED,
                                note = "AAPS",
                                pumpId = null,
                                pumpType = it,
                                pumpSerial = it1
                            )
                        }
                    }
                    ap550Pump!!.AAPSStopButtomTime = 0
                } else if (!ap550Pump!!.pumpSuspended && !isAAPSResume) {
                    ap550Pump!!.pumpType().let {
                        ap550Pump!!.serialNumber.let { it1 ->
                            pumpSyncInsert(
                                timestamp = dateUtil!!.now(),
                                type = TE.Type.PUMP_RESUMED,
                                note = "PUMP",
                                pumpId = null,
                                pumpType = it,
                                pumpSerial = it1
                            )
                        }
                    }
                } else if (!ap550Pump!!.pumpSuspended && isAAPSResume) {
                    ap550Pump!!.pumpType().let {
                        ap550Pump!!.serialNumber.let { it1 ->
                            pumpSyncInsert(
                                timestamp = dateUtil!!.now(),
                                type = TE.Type.PUMP_RESUMED,
                                note = "AAPS",
                                pumpId = null,
                                pumpType = it,
                                pumpSerial = it1
                            )
                        }
                    }
                    ap550Pump!!.AAPSResumeButtomTime = 0
                }
            }
            rxBus!!.send(EventPumpSuspendedChange(ap550Pump!!.pumpSuspended))
            // The loop listens for EventPumpStatusChanged to reconcile the running mode at once
            // (see LoopPlugin). Without it a suspend done on the pump itself would only reach the
            // home screen at the next loop run, because no command ran that could send the event.
            // CONNECTED with 0 seconds elapsed: the link is up, and it cannot be mistaken for a
            // reconnect-in-progress notice.
            rxBus!!.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED))
        }
        if (ap550Pump!!.isEasyModeEnabled) {
            notificationManager.post(NotificationId.EASY_MODE_ENABLED, rh!!.gs(R.string.danar_disableeasymode), NotificationLevel.URGENT)
        } else {
            notificationManager.dismiss(NotificationId.EASY_MODE_ENABLED)
        }
        if (!ap550Pump!!.isPasswordOK) {
            notificationManager.post(NotificationId.WRONG_PUMP_PASSWORD, rh!!.gs(R.string.wrongpumppassword), NotificationLevel.URGENT)
        } else {
            notificationManager.dismiss(NotificationId.WRONG_PUMP_PASSWORD)
        }
    }

    // The old MsgInitConnStatusBolus.handleMessage side effects
    private fun applyInitConnStatusBolus(data: InitConnStatusBolusData?) {
        if (data == null) return
        ap550Pump!!.isExtendedBolusEnabled = data.isExtendedBolusEnabled
        ap550Pump!!.bolusStep = data.bolusStep
        ap550Pump!!.maxBolus = data.maxBolus
        aapsLogger!!.debug(LTag.PUMPCOMM, "Is Extended bolus enabled: " + ap550Pump!!.isExtendedBolusEnabled)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Bolus increment: " + ap550Pump!!.bolusStep)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Bolus max: " + ap550Pump!!.maxBolus)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Delivery status: ${data.deliveryStatus}")
        if (!ap550Pump!!.isExtendedBolusEnabled) {
            notificationManager.post(NotificationId.EXTENDED_BOLUS_DISABLED, rh!!.gs(R.string.ap550_enableextendedbolus), NotificationLevel.URGENT)
        } else {
            notificationManager.dismiss(NotificationId.EXTENDED_BOLUS_DISABLED)
        }
        // This is last message of initial sequence
        activePlugin.activePump.finishHandshaking()
    }

    // The old MsgInitConnStatusOption.handleMessage side effects (the notification and
    // finishHandshaking also run when parsing fails)
    private fun applyInitConnStatusOption(data: InitConnStatusOptionData?) {
        if (data != null) {
            ap550Pump!!.password = data.password
            aapsLogger!!.debug(LTag.PUMPCOMM, "Pump password: " + ap550Pump!!.password)
        }
        if (!ap550Pump!!.isPasswordOK) {
            notificationManager.post(NotificationId.WRONG_PUMP_PASSWORD, rh!!.gs(R.string.wrongpumppassword), NotificationLevel.URGENT)
        } else {
            notificationManager.dismiss(NotificationId.WRONG_PUMP_PASSWORD)
        }
        // This is last message of initial sequence
        activePlugin.activePump.finishHandshaking()
    }

    // The old MsgInitConnStatusTime.handleMessage side effects (null = length guard hit = wrong driver model branch)
    private fun applyInitConnStatusTime(data: InitConnStatusTimeData?) {
        if (data == null) {
            notificationManager.post(NotificationId.WRONG_DRIVER, rh!!.gs(R.string.pumpdrivercorrected), NotificationLevel.NORMAL)
            ap550PumpPlugin!!.disconnect("Wrong Model")
            aapsLogger!!.debug(LTag.PUMPCOMM, "Wrong model selected. Switching to export DanaR")
            ap550PumpPlugin!!.setPluginEnabled(PluginType.PUMP, false)
            ap550PumpPlugin!!.setPluginEnabled(PluginType.PUMP, true)
            ap550Pump!!.reset() // mark not initialized
            //If profile coming from pump, switch it as well
            configBuilder.storeSettings("ChangingKoreanDanaDriver")
            scope.launch { commandQueue.readStatus("PumpDriverChange") } // force new connection
            return
        }
        ap550Pump!!.pumpVersionCode1 = data.versionCode1
        ap550Pump!!.pumpVersionCode2 = data.versionCode2
        if (ap550Pump!!.pumpVersionCode1 == 0 || ap550Pump!!.pumpVersionCode1 == 21) {
            ap550PumpPlugin!!.pumpDescription.maxTempPercent = 200
        } else if (ap550Pump!!.pumpVersionCode1 >= 22) {
            ap550PumpPlugin!!.pumpDescription.maxTempPercent = 300
        }
        aapsLogger!!.debug(LTag.PUMPCOMM, "Pump time: " + dateUtil!!.dateAndTimeString(data.time))
        aapsLogger!!.debug(LTag.PUMPCOMM, "MsgInitConnStatusTime Version: ${ap550Pump!!.pumpVersionCode1}${ap550Pump!!.pumpVersionCode2}")
    }

    // The old MsgRemainingInsulin.handleMessage side effects
    private fun applyRemainingInsulin(data: RemainingInsulinData?) {
        if (data == null) return
        // Pump version compatibility
        if (ap550Pump!!.pumpVersionString != BEFORE_DCN18_VERSION_STRING) {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Remaining insulin response: code = ${data.code}")
            when (data.bolusTypeText) {
                // This is a bolus
                "NORMAL", "SMB", "PRIMING" -> {
                    val bolusType = BS.Type.fromString(data.bolusTypeText)
                    runBlocking {
                        pumpSync.syncBolusWithPumpId(
                            timestamp = data.time,
                            amount = PumpInsulin(data.value),
                            type = bolusType,      // tells a normal bolus from an SMB
                            pumpId = data.time,          // the pump history timestamp is used as pumpId
                            pumpType = PumpType.AP550S,
                            pumpSerial = ap550Pump!!.serialNumber
                        )
                    }
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "Remaining insulin(Bolus): " +
                        "timeRaw = ${data.time}, " +
                        "time = ${dateUtil!!.dateAndTimeAndSecondsString(data.time)}, " +
                        "amount = ${data.value}, " +
                        "bolusType = $bolusType, " +
                        "bolusSource = ${data.sources}")
                }
                // This is an extended bolus
                else -> {
                    val durationMilli = data.durationMilli
                    val extendedBolusInfo = "${data.sources}_${data.bolusTypeText}"
                    runBlocking {
                        pumpSync.syncExtendedBolusWithPumpId(
                            timestamp = data.time - durationMilli,
                            rate = PumpRate(data.value),
                            duration = durationMilli + 1,   // add 1 ms; the EB data structure requires duration > 0
                            isEmulatingTB = preferences.get(Ap550BooleanNonKey.UseExtended),
                            pumpId = data.time - durationMilli,
                            pumpType = PumpType.AP550S,
                            pumpSerial = ap550Pump!!.serialNumber
                        )
                    }
                    aapsLogger!!.debug(LTag.PUMPBTCOMM, "Remaining insulin(ExtendedBolus): " +
                        "timeRaw = ${data.time}, " +
                        "time = ${dateUtil!!.dateAndTimeAndSecondsString(data.time)}, " +
                        "amount = ${data.value}, " +
                        "extendedBolusInfo = $extendedBolusInfo")
                }
            }
        } else {
            runBlocking {
                pumpSync.syncBolusWithPumpId(
                    data.time,
                    PumpInsulin(data.value),
                    BS.Type.NORMAL,
                    dateUtil!!.now(), // versions before DCN18 keep the AAPS timestamp for now
                    PumpType.AP550S,
                    ap550Pump!!.serialNumber
                )
            }
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Remaining insulin: timeRaw = ${data.time}, time = ${dateUtil!!.dateAndTimeAndSecondsString(data.time)}, amount = ${data.value}")
        }
    }

    // The old MsgSetBasalProfile.handleMessage side effects (the original rxBus notification was
    // already commented out; not migrated)
    private fun applySetBasalProfile(result: Int) {
        if (result != 1) {
            aapsLogger!!.debug(LTag.PUMPCOMM, "Set basal profile result: $result FAILED!!!")
        } else {
            aapsLogger!!.debug(LTag.PUMPCOMM, "Set basal profile result: $result")
        }
    }

    // The old MsgSetExtendedBolusStart.handleMessage side effects
    private fun applySetExtendedBolusStart(result: Int) {
        if (result != 0) {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Set extended bolus start result: $result FAILED!!!")
            ap550Pump!!.scanStatusRecord(rh!!.gs(R.string.ap550_extended_bolus_error_reason, rh!!.gs(connectUtil.errorCode2StringRes(result, 3))))
        } else {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Set extended bolus start result: $result")
        }
        ap550Pump!!.extendedBolusStartErrorCode = result
    }

    // The old MsgSetExtendedBolusStop.handleMessage side effects
    private fun applySetExtendedBolusStop(result: Int) {
        if (result == 1) {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Set extended bolus stop result: $result")
            ap550Pump!!.scanStatusRecord(rh!!.gs(R.string.ap550_extended_bolus_stopped_by_aaps))
        } else if (result == 2) {
            // Fire and forget, same reason as in applyStatusTempBasal above.
            scope.launch { commandQueue.cancelExtended() }
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Set extended bolus stop result: $result")
            ap550Pump!!.scanStatusRecord(rh!!.gs(R.string.ap550_extended_bolus_stopped_by_pump))
        } else {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Set extended bolus stop result: $result FAILED!!!")
            ap550Pump!!.scanStatusRecord(rh!!.gs(R.string.ap550_extended_bolus_stop_failed))
        }
    }

    // The old MsgSetSingleBasalProfile.handleMessage side effects
    private fun applySetSingleBasalProfile(result: Int) {
        if (result != 0) {
            aapsLogger!!.debug(LTag.PUMPCOMM, "Set basal profile result: $result FAILED!!!")
        } else {
            aapsLogger!!.debug(LTag.PUMPCOMM, "Set basal profile result: $result")
            // The write went through: the pump now has the profile AAPS wants. Only a successful
            // write clears the mismatch flag, never a pump read, so it cannot be cleared by a pump
            // that merely reports "no mismatch" because it never had the profile.
            preferences.put(Ap550BooleanNonKey.BasalProfileMismatch, false)
            notificationManager.post(NotificationId.PROFILE_SET_OK, rh!!.gs(app.aaps.core.ui.R.string.profile_set_ok), NotificationLevel.INFO, validMinutes = 60)
            rxBus!!.send(EventBumpProfileSetOk())
            rxBus!!.send(EventCurrentBasalChange())
        }
    }

    // The old MsgSetTempBasalStart.handleMessage side effects
    private fun applySetTempBasalStart(result: Int) {
        if (result != 0) {
            aapsLogger!!.debug(LTag.PUMPCOMM, "Set temp basal start result: $result FAILED!!!")
            ap550Pump!!.scanStatusRecord(rh!!.gs(R.string.ap550_temp_basal_error_reason, rh!!.gs(connectUtil.errorCode2StringRes(result, 2))))
        } else {
            aapsLogger!!.debug(LTag.PUMPCOMM, "Set temp basal start result: $result")
        }
        ap550Pump!!.basalStartErrorCode = result
    }

    // The old MsgSetTempBasalStop.handleMessage side effects
    private fun applySetTempBasalStop(result: Int) {
        if (result == 1) {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Set temp basal stop result: $result")
            ap550Pump!!.scanStatusRecord(rh!!.gs(R.string.ap550_temp_basal_stopped_by_aaps))
        } else if (result == 2) {
            // Fire and forget, same reason as in applyStatusTempBasal above. Blocking this thread
            // delayed the CMD_PUMP_TEMP_BASAL_ABS status push until after CommandCancelTempBasal
            // had read isTempBasalInProgress, so the stop never reached the treatments database.
            scope.launch { commandQueue.cancelTempBasal(true) }
            ap550Pump!!.scanStatusRecord(rh!!.gs(R.string.ap550_temp_basal_stopped_by_pump))
        } else {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Set temp basal stop result: $result FAILED!!!")
            ap550Pump!!.scanStatusRecord(rh!!.gs(R.string.ap550_temp_basal_stop_failed))
        }
    }

    // The old MsgSetTempBasalAbsoluteStart.handleMessage side effects
    private fun applySetTempBasalAbsoluteStart(result: Int) {
        if (result != 0) {
            aapsLogger!!.debug(LTag.PUMPCOMM, "Set temp basal start result: $result FAILED!!!")
        } else {
            aapsLogger!!.debug(LTag.PUMPCOMM, "Set temp basal start result: $result")
        }
        ap550Pump!!.basalStartErrorCode = result
    }

    // The old MsgSetTime.handleMessage side effects
    private fun applySetTime(result: Int) {
        if (result != 0) {
            ap550Pump!!.scanStatusRecord(rh!!.gs(R.string.time_update))
        } else {
            ap550Pump!!.scanStatusRecord(rh!!.gs(R.string.time_update_success))
        }
        aapsLogger!!.debug(LTag.PUMPCOMM, "Result of setting time: $result")
    }

    // The old MsgSettingBasal.handleMessage side effects
    private fun applySettingBasal(data: SettingBasalData) {
        ap550Pump!!.pumpProfiles = data.pumpProfiles
        aapsLogger.debug(LTag.PUMP, "data.pumpProfiles: ${data.pumpProfiles.contentDeepToString()}")
        ap550Pump!!.currentBasal = data.currentBasal
        if (data.flag == 1) {     // the AAPS and pump basal rates do not match
            // The pump tells us its profile no longer matches. Remember it, so the next profile
            // set is not skipped by isThisProfileSet even if the cached profile still looks equal.
            preferences.put(Ap550BooleanNonKey.BasalProfileMismatch, true)
            aapsLogger!!.debug(LTag.PUMP, "Test_Basal_Setting: flag=${data.flag}, currentBasal=${data.currentBasal}, basal=${ap550Pump!!.currentBasal}")
            val basalProfileErrorString = context!!.getString(R.string.profile_basal_dismatch, data.currentBasal)
            notificationManager.post(NotificationId.FAILED_UPDATE_PROFILE, basalProfileErrorString, NotificationLevel.URGENT)
            // ToastUtils.errorToast(context, basalProfileErrorString)
            runBlocking { pumpSync.insertAnnouncement(basalProfileErrorString, null, ap550Pump!!.pumpType(), ap550Pump!!.serialNumber) }
        } else {
            aapsLogger!!.debug(LTag.PUMP, "Test_Basal_Setting: flag=${data.flag}, currentBasal=${data.currentBasal}, basal=${ap550Pump!!.currentBasal}")
            notificationManager.dismiss(NotificationId.FAILED_UPDATE_PROFILE)
        }
        for (index in 0..23)
            aapsLogger!!.debug(LTag.PUMPCOMM, "Basal " + String.format(Locale.ENGLISH, "%02d", index) + "h: " + ap550Pump!!.pumpProfiles!![ap550Pump!!.activeProfile][index])
    }

    // The old MsgSettingBasalProfileAll.handleMessage side effects
    private fun applySettingBasalProfileAll(data: SettingBasalProfileAllData) {
        ap550Pump!!.pumpProfiles = data.pumpProfiles
        if (ap550Pump!!.basal48Enable) {
            for (profile in 0..3) {
                for (index in 0..23) {
                    aapsLogger!!.debug(LTag.PUMPCOMM, "Basal profile " + profile + ": " + String.format(Locale.ENGLISH, "%02d", index) + "h: " + ap550Pump!!.pumpProfiles!![profile][index])
                }
            }
        } else {
            for (profile in 0..3) {
                for (index in 0..47) {
                    aapsLogger!!.debug(LTag.PUMPCOMM, "Basal profile " + profile + ": " + String.format(Locale.ENGLISH, "%02d", index / 2) +
                        ":" + String.format(Locale.ENGLISH, "%02d", index % 2 * 30) + " : " +
                        ap550Pump!!.pumpProfiles!![profile][index])
                }
            }
        }
    }

    // The old MsgSettingGlucose.handleMessage side effects
    private fun applySettingGlucose(data: SettingGlucoseData) {
        ap550Pump!!.units = data.units
        ap550Pump!!.easyBasalMode = data.easyBasalMode
        aapsLogger!!.debug(LTag.PUMPCOMM, "Pump units: " + if (ap550Pump!!.units == AP550Pump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Easy basal mode: " + ap550Pump!!.easyBasalMode)
    }

    // The old MsgSettingMaxValues.handleMessage side effects
    private fun applySettingMaxValues(data: SettingMaxValuesData) {
        ap550Pump!!.maxBolus = data.maxBolus
        ap550Pump!!.maxBasal = data.maxBasal
        ap550Pump!!.maxDailyTotalUnits = data.maxDailyTotalUnits
        rxBus!!.send(EventMaxValue())
        aapsLogger!!.debug(LTag.PUMPCOMM, "Max bolus: " + ap550Pump!!.maxBolus)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Max basal: " + ap550Pump!!.maxBasal)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Total daily max units: " + ap550Pump!!.maxDailyTotalUnits)
    }

    // The old MsgSettingMeal.handleMessage side effects
    private fun applySettingMeal(data: SettingMealData) {
        ap550Pump!!.basalStep = data.basalStep
        ap550Pump!!.bolusStep = data.bolusStep
        ap550Pump!!.isConfigUD = data.isConfigUD
        aapsLogger!!.debug(LTag.PUMPCOMM, "Basal step: " + ap550Pump!!.basalStep)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Bolus step: " + ap550Pump!!.bolusStep)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Bolus enabled: ${data.bolusEnabled}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Melody time: ${data.melodyTime}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Block time: ${data.blockTime}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Is Config U/d: " + ap550Pump!!.isConfigUD)
        if (ap550Pump!!.isConfigUD) {
            notificationManager.post(NotificationId.UD_MODE_ENABLED, rh!!.gs(R.string.ap550_switch_touch_mode), NotificationLevel.URGENT)
        } else {
            notificationManager.dismiss(NotificationId.UD_MODE_ENABLED)
        }
    }

    // The old MsgSettingProfileRatios.handleMessage side effects
    private fun applySettingProfileRatios(data: SettingProfileRatiosData) {
        ap550Pump!!.currentCIR = data.currentCIR
        ap550Pump!!.currentCF = data.currentCF
        ap550Pump!!.currentAI = data.currentAI
        ap550Pump!!.currentTargetLow = data.currentTargetLow
        ap550Pump!!.currentTargetHigh = data.currentTargetHigh
        ap550Pump!!.currentDia = data.currentDia
        ap550Pump!!.currentAIDR = data.currentAIDR
        aapsLogger!!.debug(LTag.PUMPCOMM, "Pump units (saved): " + if (ap550Pump!!.units == AP550Pump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump CIR: " + ap550Pump!!.currentCIR)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump CF: " + ap550Pump!!.currentCF)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump AI: " + ap550Pump!!.currentAI)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump target low: " + ap550Pump!!.currentTargetLow)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump target high: " + ap550Pump!!.currentTargetHigh)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump target DIA: " + ap550Pump!!.currentDia)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump AIDR: " + ap550Pump!!.currentAIDR)
    }

    // The old MsgSettingProfileRatiosAll.handleMessage side effects
    private fun applySettingProfileRatiosAll(data: SettingProfileRatiosAllData) {
        ap550Pump!!.morningCIR = data.morningCIR
        ap550Pump!!.morningCF = data.morningCF
        ap550Pump!!.afternoonCIR = data.afternoonCIR
        ap550Pump!!.afternoonCF = data.afternoonCF
        ap550Pump!!.eveningCIR = data.eveningCIR
        ap550Pump!!.eveningCF = data.eveningCF
        ap550Pump!!.nightCIR = data.nightCIR
        ap550Pump!!.nightCF = data.nightCF
        aapsLogger!!.debug(LTag.PUMPCOMM, "Pump units: " + if (ap550Pump!!.units == AP550Pump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump morning CIR: " + ap550Pump!!.morningCIR)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump morning CF: " + ap550Pump!!.morningCF)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump afternoon CIR: " + ap550Pump!!.afternoonCIR)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump afternoon CF: " + ap550Pump!!.afternoonCF)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump evening CIR: " + ap550Pump!!.eveningCIR)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump evening CF: " + ap550Pump!!.eveningCF)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump night CIR: " + ap550Pump!!.nightCIR)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump night CF: " + ap550Pump!!.nightCF)
    }

    // The old MsgSettingPumpTime.handleMessage side effects (the resetPumpTime timeout path of
    // handleMessageNotReceived is handled by Task 13b)
    private fun applySettingPumpTime(time: Long) {
        aapsLogger!!.debug(LTag.PUMPCOMM, "Pump time: " + dateUtil!!.dateAndTimeString(time) + " Phone time: " + java.util.Date())
        ap550Pump!!.setPumpTime(time)
    }

    // The old MsgSettingShippingInfo.handleMessage side effects
    private fun applySettingShippingInfo(data: SettingShippingInfoData) {
        // ap550Pump!!.serialNumber = data.serialNumber
        ap550Pump!!.serialNumber = ap550Pump!!.sn_for_pair
        ap550Pump!!.shippingDate = data.shippingDate
        ap550Pump!!.shippingCountry = data.shippingCountry
        aapsLogger!!.debug(LTag.PUMPCOMM, "Serial number: " + ap550Pump!!.serialNumber)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Shipping date: " + ap550Pump!!.shippingDate)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Shipping country: " + ap550Pump!!.shippingCountry)
    }

    // The old MsgSettingActiveProfile.handleMessage side effects.
    // Registered deviation: the old implementation had the parse line commented out and
    // hardcoded ap550Pump.activeProfile = 0;
    // here the parsed SDK value is written instead (whether to restore the hardcoded 0 is
    // left for later verification).
    private fun applySettingActiveProfile(profile: Int) {
        ap550Pump!!.activeProfile = profile
        aapsLogger!!.debug(LTag.PUMPCOMM, "Active profile number: $profile")
    }

    // The old MsgSettingUserOptions.handleMessage side effects
    private fun applySettingUserOptions(data: SettingUserOptionsData) {
        ap550Pump!!.userOptionsFrompump = data.raw // saving pumpDataBytes to use it in MsgSetUserOptions
        ap550Pump!!.timeDisplayType24 = data.timeDisplayType24
        ap550Pump!!.buttonScrollOnOff = data.buttonScrollOnOff
        ap550Pump!!.beepAndAlarm = data.beepAndAlarm
        ap550Pump!!.lcdOnTimeSec = data.lcdOnTimeSec
        ap550Pump!!.backlightOnTimeSec = data.backlightOnTimeSec
        ap550Pump!!.selectedLanguage = data.selectedLanguage
        ap550Pump!!.units = data.units
        ap550Pump!!.shutdownHour = data.shutdownHour
        ap550Pump!!.lowReservoirRate = data.lowReservoirRate
        aapsLogger!!.debug(LTag.PUMPCOMM, "timeDisplayType24: " + ap550Pump!!.timeDisplayType24)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Button scroll: " + ap550Pump!!.buttonScrollOnOff)
        aapsLogger!!.debug(LTag.PUMPCOMM, "BeepAndAlarm: " + ap550Pump!!.beepAndAlarm)
        aapsLogger!!.debug(LTag.PUMPCOMM, "screen timeout: " + ap550Pump!!.lcdOnTimeSec)
        aapsLogger!!.debug(LTag.PUMPCOMM, "BackLight: " + ap550Pump!!.backlightOnTimeSec)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Selected language: " + ap550Pump!!.selectedLanguage)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Units: " + ap550Pump!!.units)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Shutdown: " + ap550Pump!!.shutdownHour)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Low reservoir: " + ap550Pump!!.lowReservoirRate)
    }

    // The old MsgStatus.handleMessage side effects
    private fun applyStatus(data: StatusData) {
        ap550Pump!!.dailyTotalUnits = data.dailyTotalUnits
        ap550Pump!!.isExtendedInProgress = data.isExtendedInProgress
        ap550Pump!!.extendedBolusMinutes = data.extendedBolusMinutes
        ap550Pump!!.extendedBolusAmount = data.extendedBolusAmount
        if (data.lastBolusAmount != 0.0) {
            ap550Pump!!.lastBolusTime = data.lastBolusTime
            ap550Pump!!.lastBolusAmount = data.lastBolusAmount
        }
        ap550Pump!!.iob = data.iob
        aapsLogger!!.debug(LTag.PUMPCOMM, "Daily total: " + ap550Pump!!.dailyTotalUnits)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Is extended bolus running: " + ap550Pump!!.isExtendedInProgress)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Extended bolus min: " + ap550Pump!!.extendedBolusMinutes)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Extended bolus amount: " + ap550Pump!!.extendedBolusAmount)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Last bolus time: " + ap550Pump!!.lastBolusTime)
        aapsLogger!!.debug(LTag.PUMPCOMM, "ap550Pump.lastBolusAmount: " + ap550Pump!!.lastBolusAmount)
        aapsLogger!!.debug(LTag.PUMPCOMM, "IOB: " + ap550Pump!!.iob)
        rxBus!!.send(EventLastBolus())
    }

    // The old MsgStatusBasic.handleMessage side effects
    private fun applyStatusBasic(data: StatusBasicData) {
        ap550Pump!!.dailyTotalUnits = data.dailyTotalUnits
        ap550Pump!!.maxDailyTotalUnits = data.maxDailyTotalUnits
        ap550Pump!!.reservoirRemainingUnits = data.reservoirRemainingUnits
        ap550Pump!!.currentBasal = data.currentBasal
        ap550Pump!!.batteryRemaining = data.batteryRemaining
        rxBus!!.send(EventDosageChange())
        aapsLogger!!.debug(LTag.PUMPCOMM, "Daily total units: ${data.dailyTotalUnits}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Max daily total units: ${data.maxDailyTotalUnits}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Reservoir remaining units: ${data.reservoirRemainingUnits}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current basal: ${data.currentBasal}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Battery remaining: ${data.batteryRemaining}")
    }

    // The old MsgStatusBolusExtended.handleMessage side effects
    private fun applyStatusBolusExtended(data: StatusBolusExtendedData) {
        if (data.isExtendedInProgress && !isWithin3SecExtendedBolus(data.extendedBolusStart)) {
            ap550Pump!!.extendedBolusDuration = data.extendedBolusDurationMs
            ap550Pump!!.extendedBolusAmount = data.extendedBolusAmount
            ap550Pump!!.extendedBolusStart = data.extendedBolusStart
            ap550Pump!!.isExtendedInProgress = true
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "New extended bolus detected")
        } else if (!data.isExtendedInProgress) {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "Extended bolus stopped. Previous state: ${ap550Pump!!.isExtendedInProgress}")
            // The query found no extended bolus in progress; reset the data
            ap550Pump!!.extendedBolusDuration = 0
            ap550Pump!!.extendedBolusAmount = 0.0
            ap550Pump!!.extendedBolusStart = 0
            ap550Pump!!.isExtendedInProgress = false
        } else {
            aapsLogger!!.debug(LTag.PUMPBTCOMM, "No change in extended bolus. Current state: ${ap550Pump!!.isExtendedInProgress}")
        }
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "Is extended bolus running: ${data.isExtendedInProgress}")
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "Extended bolus min: ${data.extendedBolusMinutes}")
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "Extended bolus amount: ${data.extendedBolusAmount}")
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "Extended bolus so far in minutes: ${data.extendedBolusSoFarInMinutes}")
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "Extended bolus absolute rate: ${data.extendedBolusAbsoluteRate}")
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "Extended bolus start: " + dateUtil!!.dateAndTimeString(data.extendedBolusStart))
        aapsLogger!!.debug(LTag.PUMPBTCOMM, "Extended bolus remaining minutes: ${data.extendedBolusRemainingMinutes}")
    }

    // because there is no fixed timestamp of start allow update of eb only if tbr start differs more
    private fun isWithin3SecExtendedBolus(newStart: Long) = abs(newStart - ap550Pump!!.extendedBolusStart) < 3000

    // The old MsgStatusProfile.handleMessage side effects (the MGDL branch does not write
    // targetHigh/DIA; those keep their old values)
    private fun applyStatusProfile(data: StatusProfileData) {
        if (ap550Pump!!.units == AP550Pump.UNITS_MGDL) {
            ap550Pump!!.currentCIR = data.currentCIR
            ap550Pump!!.currentCF = data.currentCF
            ap550Pump!!.currentAI = data.currentAI
            ap550Pump!!.currentTargetLow = data.currentTargetLow
        } else {
            ap550Pump!!.currentCIR = data.currentCIR
            ap550Pump!!.currentCF = data.currentCF
            ap550Pump!!.currentAI = data.currentAI
            ap550Pump!!.currentTargetLow = data.currentTargetLow
            ap550Pump!!.currentTargetHigh = data.currentTargetHigh
            ap550Pump!!.currentDia = data.currentDia
        }
        aapsLogger!!.debug(LTag.PUMPCOMM, "Pump units (saved): " + if (ap550Pump!!.units == AP550Pump.UNITS_MGDL) "MGDL" else "MMOL")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump CIR: " + ap550Pump!!.currentCIR)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump CF: " + ap550Pump!!.currentCF)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump AI: " + ap550Pump!!.currentAI)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump target low: " + ap550Pump!!.currentTargetLow)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump target high: " + ap550Pump!!.currentTargetHigh)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump target DIA: " + ap550Pump!!.currentDia)
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current pump AIDR: " + ap550Pump!!.currentAIDR)
    }

    // The old MsgStatusPumpMute.handleMessage side effects
    private fun applyStatusPumpMute(data: StatusPumpMuteData) {
        aapsLogger!!.debug(LTag.PUMPCOMM, "Received the reply with the pump mute and alarm state")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Mute state: ${data.pumpMute}" + " alarm type: ${data.pumpAlarmType}")
        ap550Pump!!.pumpMuteStatus = data.pumpMuteStatus
        // Send an event to update the UI
        rxBus!!.send(EventAP550NewStatus())
    }

    // The old MsgStatusTempBasal.handleMessage side effects
    private fun applyStatusTempBasal(data: StatusTempBasalData) {
        // This means the temp basal is currently in percent mode
        ap550PumpPlugin!!.pumpDescription.tempBasalStyle = PumpDescription.PERCENT
        if (data.isTempBasalInProgress && !isWithin3SecTempBasal(data.tempBasalStart, data.tempBasalPercent, data.tempBasalTotalMin)) {
            aapsLogger!!.debug(LTag.PUMPCOMM, "Previous tempBasalStart = ${ap550Pump!!.tempBasalStart}")
            ap550Pump!!.tempBasalStart = data.tempBasalStart
            ap550Pump!!.tempBasalPercent = data.tempBasalPercent
            ap550Pump!!.tempBasalDuration = data.tempBasalDurationMs
            aapsLogger!!.debug(LTag.PUMPCOMM, "New temp basal detected")
        } else if (!data.isTempBasalInProgress) {
            aapsLogger!!.debug(LTag.PUMPCOMM, "Temp basal stopped. Previous state: ${ap550Pump!!.isTempBasalInProgress}")
            ap550Pump!!.isTempBasalInProgress = false
        } else {
            aapsLogger!!.debug(LTag.PUMPCOMM, "No change in temp basal. Current state: ${ap550Pump!!.isTempBasalInProgress}")
        }
        aapsLogger!!.debug(LTag.PUMPCOMM, "Temp basal state - ap550Pump.tempBasalPercent = ${ap550Pump!!.tempBasalPercent}")
        val profile = runBlocking { profileFunction!!.getProfile() } ?: return
        var percent = constraintChecker.applyBasalPercentConstraints(    // constrain the temp basal
            ConstraintObject<Int>(
                ap550Pump!!.tempBasalPercent,
                aapsLogger!!
            ), profile
        ).value()

        // After the constraints are applied, restore the originally configured style
        val sp550BasalIsPercent = preferences.get(Ap550BooleanNonKey.BasalIsPercent)
        ap550PumpPlugin!!.pumpDescription.tempBasalStyle = if (sp550BasalIsPercent) PumpDescription.PERCENT else PumpDescription.ABSOLUTE

        aapsLogger!!.debug(LTag.PUMPCOMM, "Temp basal state - percentAfterConstraintChecker = $percent")
        if (abs(percent - ap550Pump!!.tempBasalPercent) < 10) {     // the AAPS temp basal step is 10, the pump one is 5
            percent = ap550Pump!!.tempBasalPercent
        }
        ap550Pump!!.tempBasalPercent = percent  // constrain the temp basal to avoid problems elsewhere
        aapsLogger!!.debug(LTag.PUMPCOMM, "Temp basal state - ap550Pump.tempBasalPercent = ${ap550Pump!!.tempBasalPercent}")

        if (data.isManual == 1) {    // the temp basal was started on the pump
            ap550Pump!!.isManual = 1
            // Fire and forget. Never runBlocking here: this runs on the GATT receive callback
            // thread, and a blocked receive thread cannot deliver the pump replies the command
            // itself waits for (the old code queued the command and returned at once).
            scope.launch { commandQueue.tempBasalPercent(percent, data.tempBasalTotalMin, true, profile, PumpSync.TemporaryBasalType.NORMAL) }
        } else {                     // AAPS pulled the pump temp basal
            ap550Pump!!.isManual = 0
            if (ap550Pump!!.isTempBasalInProgress) {    // after reconnecting, the pump temp basal can be synced to AAPS
                runBlocking {
                    pumpSync.syncTemporaryBasalWithPumpId(
                        ap550Pump!!.tempBasalStart,
                        PumpRate(percent.toDouble()),
                        ap550Pump!!.tempBasalDuration,
                        false,
                        PumpSync.TemporaryBasalType.NORMAL,
                        ap550Pump!!.tempBasalStart,
                        PumpType.AP550S,
                        ap550Pump!!.serialNumber
                    )
                }
            }
        }
        aapsLogger!!.debug(LTag.PUMPCOMM, "Is temp basal running: ${data.isTempBasalInProgress}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Is APS temp basal running: ${data.isAPSTempBasalInProgress}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current temp basal percent: ${data.tempBasalPercent}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current temp basal remaining min: ${data.tempBasalRemainingMin}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current temp basal total min: ${data.tempBasalTotalMin}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current temp basal start: " + dateUtil!!.dateAndTimeString(data.tempBasalStart))
    }

    // The old MsgStatusTempBasal.isWithin3Sec (also backfills from persistenceLayer for
    // deduplication when ap550Pump.tempBasalStart <= 0)
    private fun isWithin3SecTempBasal(newStart: Long, parsedPercent: Int, parsedTotalMin: Int): Boolean {
        if (ap550Pump!!.tempBasalStart <= 0) {
            val lastestTempBasalTime = runBlocking { persistenceLayer.getLastTemporaryBasalRecord() }?.timestamp
            if (lastestTempBasalTime != null) {
                ap550Pump!!.tempBasalStart = lastestTempBasalTime
                ap550Pump!!.tempBasalPercent = parsedPercent
                ap550Pump!!.tempBasalDuration = T.mins(parsedTotalMin.toLong()).msecs()
                return abs(newStart - lastestTempBasalTime) < 3000
            } else {
                return false
            }
        } else {
            return abs(newStart - ap550Pump!!.tempBasalStart) < 3000
        }
    }

    // The old MsgStatusTempBasalAbsolute.handleMessage side effects
    private fun applyStatusTempBasalAbsolute(data: StatusTempBasalAbsoluteData) {
        if (data.isTempBasalInProgress && !isWithin3SecTempBasalAbsolute(data.tempBasalStart)) {
            aapsLogger!!.debug(LTag.PUMPCOMM, "Previous tempBasalStart = ${ap550Pump!!.tempBasalStart}")
            ap550Pump!!.tempBasalStart = data.tempBasalStart
            ap550Pump!!.tempBasalAbsolute = data.tempBasalAbsolute
            ap550Pump!!.tempBasalDuration = data.tempBasalDurationMs
            // Reset tempBasalPercent: when a percent temp basal finishes and an absolute one is
            // set, an unreset tempBasalPercent would make ap550Pump.temporaryBasalToString()
            // report a wrong value
            aapsLogger!!.debug(LTag.PUMPCOMM, "New temp basal detected")
        } else if (!data.isTempBasalInProgress) {
            aapsLogger!!.debug(LTag.PUMPCOMM, "Temp basal stopped. Previous state: ${ap550Pump!!.isTempBasalInProgress}")
            ap550Pump!!.isTempBasalInProgress = false
        } else {
            aapsLogger!!.debug(LTag.PUMPCOMM, "No change in temp basal. Current state: ${ap550Pump!!.isTempBasalInProgress}")
        }
        val profile = runBlocking { profileFunction!!.getProfile() } ?: return
        // The absolute value constraint branch was already commented out in the old code; not migrated
        if (data.isManual == 1) {
            ap550Pump!!.isManual = 1
            // Fire and forget, same reason as in applyStatusTempBasal above.
            scope.launch { commandQueue.tempBasalAbsolute(ap550Pump!!.tempBasalAbsolute, data.tempBasalTotalMin, true, profile, PumpSync.TemporaryBasalType.NORMAL) }
        } else {
            ap550Pump!!.isManual = 0
            if (ap550Pump!!.isTempBasalInProgress) {     // after reconnecting, a running pump temp basal can be synced to AAPS; only for one still running
                runBlocking {
                    pumpSync.syncTemporaryBasalWithPumpId(
                        ap550Pump!!.tempBasalStart,
                        PumpRate(ap550Pump!!.tempBasalAbsolute),
                        ap550Pump!!.tempBasalDuration,
                        true,
                        PumpSync.TemporaryBasalType.NORMAL,
                        ap550Pump!!.tempBasalStart,
                        PumpType.AP550S,
                        ap550Pump!!.serialNumber
                    )
                }
            }
        }
        aapsLogger!!.debug(LTag.PUMPCOMM, "Is temp basal running: ${data.isTempBasalInProgress}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Is APS temp basal running: ${data.isAPSTempBasalInProgress}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current temp basal: ${data.tempBasalAbsolute}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current temp basal remaining min: ${data.tempBasalRemainingMin}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current temp basal total min: ${data.tempBasalTotalMin}")
        aapsLogger!!.debug(LTag.PUMPCOMM, "Current temp basal start: " + dateUtil!!.dateAndTimeString(data.tempBasalStart))
    }

    // The old MsgStatusTempBasalAbsolute.isWithin3Sec (also backfills from persistenceLayer for
    // deduplication when tempBasalStart <= 0)
    private fun isWithin3SecTempBasalAbsolute(newStart: Long): Boolean {
        if (ap550Pump!!.tempBasalStart <= 0) {
            val lastTempBasalRecord = runBlocking { persistenceLayer.getLastTemporaryBasalRecord() }
            val lastestTempBasalTime = lastTempBasalRecord?.timestamp
            val latestTempBasalAbsolute = lastTempBasalRecord?.rate
            val latestTempBasalDuration = lastTempBasalRecord?.duration
            if (lastestTempBasalTime != null && latestTempBasalAbsolute != null && latestTempBasalDuration != null) {
                ap550Pump!!.tempBasalStart = lastestTempBasalTime
                ap550Pump!!.tempBasalAbsolute = latestTempBasalAbsolute
                ap550Pump!!.tempBasalDuration = latestTempBasalDuration
                return abs(newStart - lastestTempBasalTime) < 3000
            } else {
                return false
            }
        } else {
            return abs(newStart - ap550Pump!!.tempBasalStart) < 3000
        }
    }

    // The old MsgTempBasalHistory.handleMessage side effects
    private fun applyTempBasalHistory(data: TempBasalHistoryData?) {
        if (data == null) return   // ff ff means an empty reply (the SDK logs it)
        val time = data.time
        val value = data.value
        val tempBasalDuration = data.duration
        val isAbsolute = data.isAbsolute

        if (preferences.get(Ap550BooleanNonKey.IsTempBasalRunningAfterDisconnect) && ap550Pump!!.pumpVersionString != BEFORE_DCN18_VERSION_STRING) {
            val timestamp = preferences.get(Ap550LongNonKey.TempBasalRunningTimestamp) // start time of the temp basal still running at disconnect
            val disconnectTimestamp = preferences.get(Ap550LongNonKey.TempBasalRunningDisconnectTimestamp) // disconnect timestamp
            val duration = preferences.get(Ap550LongNonKey.TempBasalRunningDuration)
            val serialNumber = preferences.get(Ap550StringNonKey.TempBasalRunningSerialNumber)
            aapsLogger!!.debug(LTag.PUMPCOMM, "time basal running after disconnect time: $time,  timestamp: $timestamp, disconnectTimestamp: $disconnectTimestamp, duration: $duration, serialNumber: $serialNumber")
            if (timestamp > 0 && disconnectTimestamp > 0 && duration > 0 && serialNumber != "") {
                if (abs(timestamp - time) > 13 * 1000 || serialNumber != ap550Pump!!.serialNumber) { // more than 13 seconds apart (max 10 s AAPS-pump time diff + 3 s communication), so the pump has no matching temp basal record; stop this temp basal with the disconnect timestamp as the stop time
                    aapsLogger!!.debug(LTag.PUMPCOMM, "Time difference above 3 seconds or pump serial number mismatch, temp basal disconnect: $timestamp, record from bump:$time")
                    preferences.put(Ap550BooleanNonKey.IsTempBasalRunningAfterDisconnect, false)
                    val endTimestamp = min(disconnectTimestamp, timestamp + duration)
                    aapsLogger!!.debug(LTag.PUMPCOMM, "MsgTempBasalHistory stop time: $endTimestamp, serialNumber: $serialNumber")

                    val s = runBlocking {
                        pumpSync.syncStopTemporaryBasalWithPumpId(
                            endTimestamp,
                            endTimestamp,
                            PumpType.AP550S,
                            serialNumber
                        )
                    }
                    aapsLogger!!.debug(LTag.PUMPCOMM, "temp basal after disconnect stop success: $s")
                } else {
                    val success = runBlocking {
                        pumpSync.syncTemporaryBasalWithPumpId(
                            timestamp,
                            PumpRate(value),
                            tempBasalDuration + 1,
                            isAbsolute,
                            PumpSync.TemporaryBasalType.NORMAL,
                            timestamp,
                            PumpType.AP550S,
                            ap550Pump!!.serialNumber
                        )
                    }
                    aapsLogger!!.debug(LTag.PUMPCOMM, "temp basal after disconnect success: $success")
                    preferences.put(Ap550BooleanNonKey.IsTempBasalRunningAfterDisconnect, false)
                    return
                }
            } else {
                preferences.put(Ap550BooleanNonKey.IsTempBasalRunningAfterDisconnect, false)
            }
        }

        if (tempBasalDuration > 1) {
            aapsLogger!!.debug(LTag.PUMPCOMM, "MsgTempBasalHistory time: $time, tempBasalDuration:$tempBasalDuration, ${time + tempBasalDuration}")
            // Take the latest record from the database. If its start time is within 3 seconds of
            // the pump record start time, it is the same record; use the AAPS recorded start
            // time for this record to avoid inserting a new one
            val lastestTempBasalTime = runBlocking { persistenceLayer.getLastTemporaryBasalRecord() }?.timestamp
            // Avoid this problem: when a temp basal is sent, a pump status query is also sent
            // (which queries the temp basal records). A new temp basal record gets inserted
            // first, then the reply to the record query arrives (it is the old record), and
            // that old record would mix up the old and new records on AAPS
            if (lastestTempBasalTime != null && (lastestTempBasalTime - time) > 13 * 1000) return
            val timeAfterProcess = if (lastestTempBasalTime != null) {
                aapsLogger!!.debug(LTag.PUMPCOMM, "MsgTempBasalHistory lastestTempBasalTime: $lastestTempBasalTime")
                if (abs(lastestTempBasalTime - time) > 13 * 1000) { //todo: also need to check that the pump serial numbers match
                    time
                } else {
                    lastestTempBasalTime
                }
            } else {
                time
            }
            aapsLogger!!.debug(LTag.PUMPCOMM, "MsgTempBasalHistory timeAfterProcess: $timeAfterProcess")
            val success = runBlocking {
                pumpSync.syncTemporaryBasalWithPumpId(
                    timeAfterProcess,
                    PumpRate(value),
                    tempBasalDuration + 1,
                    isAbsolute,
                    PumpSync.TemporaryBasalType.NORMAL,
                    timeAfterProcess,
                    PumpType.AP550S,
                    ap550Pump!!.serialNumber
                )
            }
            aapsLogger!!.debug(LTag.PUMPCOMM, "MsgTempBasalHistory success: $success")
            if (success) {
                val s = runBlocking {
                    pumpSync.syncStopTemporaryBasalWithPumpId(
                        timeAfterProcess + tempBasalDuration,
                        timeAfterProcess + tempBasalDuration,
                        PumpType.AP550S,
                        ap550Pump!!.serialNumber
                    )
                }
                aapsLogger!!.debug(LTag.PUMPCOMM, "MsgTempBasalHistory stop success: $s")
            }
        }
        aapsLogger!!.debug(LTag.PUMPCOMM, "temp basal insulin time: ${dateUtil!!.dateAndTimeString(time)}, value: $value")
    }

    companion object {

        /** Broadcast sent when the pairing screen gives up waiting for the pump to answer. */
        const val BLUETOOTH_INTENT_ACTION_CONNECT_TIME_OUT = "AAPS.AP550S.Connect_Time_Out"

        @Volatile
        private var mBluetoothGatt: BluetoothGatt? = null

        @Volatile
        private var mCharacteristicReceiver: BluetoothGattCharacteristic? = null

        @Volatile
        private var mCharacteristicSend: BluetoothGattCharacteristic? = null
        private const val STATE_DISCONNECTED = BluetoothProfile.STATE_DISCONNECTED
        private const val STATE_DISCONNECTING = BluetoothProfile.STATE_DISCONNECTING
        private const val STATE_CONNECTING = BluetoothProfile.STATE_CONNECTING
        private const val STATE_CONNECTED = BluetoothProfile.STATE_CONNECTED

        // @Volatile
        // private val servicesDiscovered = DISCOVERED.NULL
        private var mStatus = -1
        private var error133 = 0
        private var mBluetoothDeviceAddress: String? = null
        private var mBluetoothDeviceName: String? = null
        fun formatForRootLocale(format: String?, vararg args: Any?): String {
            return String.format(Locale.ROOT, format!!, *args)
        }

        @Throws(
            ClassNotFoundException::class,
            NoSuchMethodException::class,
            IllegalAccessException::class,
            InvocationTargetException::class
        )
        private fun invokeBluetoothDeviceMethod(
            dev: BluetoothDevice, methodName: String, vararg args: Any
        ): Any {
            val c: Class<*> = dev.javaClass
            val m = c.getMethod(methodName)
            m.isAccessible = true
            return m.invoke(dev, *args)
        }

        // Risk level enum
        enum class RiskLevel(val level: Int, @StringRes val descriptionResId: Int) {
            NO_RISK(0, R.string.ap550_risk_no_risk),
            LOW_RISK(1, R.string.ap550_risk_low_risk),
            HIGH_RISK(2, R.string.ap550_risk_high_risk)
        }
    }
}
