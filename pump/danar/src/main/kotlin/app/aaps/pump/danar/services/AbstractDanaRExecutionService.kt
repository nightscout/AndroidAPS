package app.aaps.pump.danar.services

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAppExit
import app.aaps.core.interfaces.rx.events.EventBTChange
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.toast.ToastUtils.errorToast
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.R
import app.aaps.pump.dana.comm.RecordTypes
import app.aaps.pump.dana.keys.DanaStringKey
import app.aaps.pump.danar.SerialIOThread
import app.aaps.pump.danar.comm.MessageBase
import app.aaps.pump.danar.comm.MessageHashTableBase
import app.aaps.pump.danar.comm.MsgBolusStop
import app.aaps.pump.danar.comm.MsgHistoryAlarm
import app.aaps.pump.danar.comm.MsgHistoryBasalHour
import app.aaps.pump.danar.comm.MsgHistoryBolus
import app.aaps.pump.danar.comm.MsgHistoryCarbo
import app.aaps.pump.danar.comm.MsgHistoryDailyInsulin
import app.aaps.pump.danar.comm.MsgHistoryError
import app.aaps.pump.danar.comm.MsgHistoryGlucose
import app.aaps.pump.danar.comm.MsgHistoryRefill
import app.aaps.pump.danar.comm.MsgHistorySuspend
import app.aaps.pump.danar.comm.MsgPCCommStart
import app.aaps.pump.danar.comm.MsgPCCommStop
import dagger.android.DaggerService
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.abs
import kotlin.math.min

/**
 * Created by mike on 28.01.2018.
 */
abstract class AbstractDanaRExecutionService : DaggerService() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var context: Context
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var pumpEnactResultProvider: Provider<PumpEnactResult>

    private val disposable = CompositeDisposable()
    protected var mRfcommSocket: BluetoothSocket? = null
    var isConnecting = false
        protected set
    protected var mHandshakeInProgress = false
    protected var mSerialIOThread: SerialIOThread? = null
    protected var mBinder: IBinder? = null
    abstract fun messageHashTable(): MessageHashTableBase

    @Suppress("PrivatePropertyName")
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    protected var lastApproachingDailyLimit: Long = 0
    abstract fun updateBasalsInPump(profile: Profile): Boolean
    abstract fun getPumpStatus()
    abstract fun loadEvents(): PumpEnactResult?
    abstract fun bolus(detailedBolusInfo: DetailedBolusInfo): Boolean
    abstract fun highTempBasal(percent: Int, durationInMinutes: Int): Boolean // Rv2 only
    abstract fun tempBasalShortDuration(percent: Int, durationInMinutes: Int): Boolean // Rv2 only
    abstract fun tempBasal(percent: Int, durationInHours: Int): Boolean
    abstract fun tempBasalStop(): Boolean
    abstract fun extendedBolus(insulin: Double, durationInHalfHours: Int): Boolean
    abstract fun extendedBolusStop(): Boolean
    abstract fun setUserOptions(): PumpEnactResult?

    @SuppressLint("MissingPermission")
    override fun onCreate() {
        super.onCreate()
        disposable += rxBus
            .toObservable(EventBTChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ event: EventBTChange ->
                           if (event.state === EventBTChange.Change.DISCONNECT) {
                               aapsLogger.debug(LTag.PUMP, "Device was disconnected " + event.deviceName) //Device was disconnected
                               if (preferences.get(DanaStringKey.RName) == event.deviceName) {
                                   mSerialIOThread?.disconnect("BT disconnection broadcast")
                                   rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                               }
                           }
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAppExit::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           aapsLogger.debug(LTag.PUMP, "EventAppExit received")
                           mSerialIOThread?.disconnect("Application exit")
                           stopSelf()
                       }, fabricPrivacy::logException)
    }

    override fun onDestroy() {
        disposable.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    val isConnected: Boolean
        get() = mRfcommSocket?.isConnected == true
    val isHandshakeInProgress: Boolean
        get() = isConnected && mHandshakeInProgress

    fun finishHandshaking() {
        mHandshakeInProgress = false
        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTED, 0))
    }

    fun disconnect(from: String) {
        mSerialIOThread?.disconnect(from)
        mRfcommSocket = null
        mSerialIOThread = null
    }

    fun stopConnecting() {
        mSerialIOThread?.disconnect("stopConnecting")
    }

    @SuppressLint("MissingPermission") fun connect() {
        if (isConnecting) return
        Thread(Runnable {
            mHandshakeInProgress = false
            isConnecting = true
            getBTSocketForSelectedPump()
            if (mRfcommSocket == null) {
                isConnecting = false
                return@Runnable  // Device not found
            }
            try {
                mRfcommSocket?.connect()
            } catch (e: IOException) {
                if (e.message?.contains("socket closed") == true) {
                    aapsLogger.error("Unhandled exception", e)
                }
            }
            if (isConnected) {
                mSerialIOThread?.disconnect("Recreate SerialIOThread")
                mSerialIOThread = SerialIOThread(aapsLogger, mRfcommSocket!!, messageHashTable(), danaPump)
                mHandshakeInProgress = true
                rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.HANDSHAKING, 0))
            }
            isConnecting = false
        }).start()
    }

    fun getBTSocketForSelectedPump() {
        val deviceName = preferences.get(DanaStringKey.RName)
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            (context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
                ?.bondedDevices?.firstOrNull { it.name == deviceName }?.let { mBTDevice ->
                    try {
                        mRfcommSocket = mBTDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                    } catch (e: IOException) {
                        aapsLogger.error("Error creating socket: ", e)
                        errorToast(context.applicationContext, "Error creating socket")
                    }
                } ?: { errorToast(context.applicationContext, R.string.devicenotfound) }
        } else {
            errorToast(context, app.aaps.core.ui.R.string.need_connect_permission)
        }
    }

    fun bolusStop() {
        aapsLogger.debug(LTag.PUMP, "bolusStop >>>>> @ ${BolusProgressData.delivered}")
        val stop = MsgBolusStop(injector)
        danaPump.bolusStopForced = true
        if (isConnected) {
            mSerialIOThread?.sendMessage(stop)
            while (!danaPump.bolusStopped) {
                mSerialIOThread?.sendMessage(stop)
                SystemClock.sleep(200)
            }
        } else {
            danaPump.bolusStopped = true
        }
    }

    fun loadHistory(type: Byte): PumpEnactResult {
        val result = pumpEnactResultProvider.get()
        if (!isConnected) return result
        val msg: MessageBase = when (type) {
            RecordTypes.RECORD_TYPE_ALARM     -> MsgHistoryAlarm(injector)
            RecordTypes.RECORD_TYPE_BASALHOUR -> MsgHistoryBasalHour(injector)
            RecordTypes.RECORD_TYPE_BOLUS     -> MsgHistoryBolus(injector)
            RecordTypes.RECORD_TYPE_CARBO     -> MsgHistoryCarbo(injector)
            RecordTypes.RECORD_TYPE_DAILY     -> MsgHistoryDailyInsulin(injector)
            RecordTypes.RECORD_TYPE_ERROR     -> MsgHistoryError(injector)
            RecordTypes.RECORD_TYPE_GLUCOSE   -> MsgHistoryGlucose(injector)
            RecordTypes.RECORD_TYPE_REFILL    -> MsgHistoryRefill(injector)
            RecordTypes.RECORD_TYPE_SUSPEND   -> MsgHistorySuspend(injector)

            else                              -> error("Unsupported type")
        }
        danaPump.historyDoneReceived = false
        mSerialIOThread?.sendMessage(MsgPCCommStart(injector))
        SystemClock.sleep(400)
        mSerialIOThread?.sendMessage(msg)
        while (!danaPump.historyDoneReceived && mRfcommSocket?.isConnected == true) {
            SystemClock.sleep(100)
        }
        SystemClock.sleep(200)
        mSerialIOThread?.sendMessage(MsgPCCommStop(injector))
        result.success(true).comment("OK")
        return result
    }

    fun waitForWholeMinute() {
        while (true) {
            val time = dateUtil.now()
            val timeToWholeMinute = 60000 - time % 60000
            if (timeToWholeMinute > 59800 || timeToWholeMinute < 3000) break
            rxBus.send(EventPumpStatusChanged(rh.gs(R.string.waitingfortimesynchronization, (timeToWholeMinute / 1000).toInt())))
            SystemClock.sleep(min(timeToWholeMinute, 100))
        }
    }

    fun doSanityCheck() {
        val (temporaryBasal, extendedBolus) = pumpSync.expectedPumpState()

        // Temporary basal
        if (temporaryBasal != null) {
            if (danaPump.isTempBasalInProgress) {
                if (temporaryBasal.rate != danaPump.tempBasalPercent.toDouble()
                    || abs(temporaryBasal.timestamp - danaPump.tempBasalStart) > 10000
                ) { // Close current temp basal
                    uiInteraction.addNotification(Notification.UNSUPPORTED_ACTION_IN_PUMP, rh.gs(app.aaps.pump.danar.R.string.unsupported_action_in_pump), Notification.URGENT)
                    aapsLogger.error(LTag.PUMP, "Different temporary basal found running AAPS: " + (temporaryBasal.toString() + " DanaPump " + danaPump.temporaryBasalToString()))
                    pumpSync.syncTemporaryBasalWithPumpId(
                        danaPump.tempBasalStart,
                        danaPump.tempBasalPercent.toDouble(), danaPump.tempBasalDuration,
                        false,
                        PumpSync.TemporaryBasalType.NORMAL,
                        danaPump.tempBasalStart,
                        activePlugin.activePump.model(),
                        activePlugin.activePump.serialNumber()
                    )
                }
            } else {
                pumpSync.syncStopTemporaryBasalWithPumpId(
                    dateUtil.now(),
                    dateUtil.now(),
                    activePlugin.activePump.model(),
                    activePlugin.activePump.serialNumber()
                )
                uiInteraction.addNotification(Notification.UNSUPPORTED_ACTION_IN_PUMP, rh.gs(app.aaps.pump.danar.R.string.unsupported_action_in_pump), Notification.URGENT)
                aapsLogger.error(LTag.PUMP, "Temporary basal should not be running. Sending stop to AAPS")
            }
        } else {
            if (danaPump.isTempBasalInProgress) { // Create new
                pumpSync.syncTemporaryBasalWithPumpId(
                    danaPump.tempBasalStart,
                    danaPump.tempBasalPercent.toDouble(), danaPump.tempBasalDuration,
                    false,
                    PumpSync.TemporaryBasalType.NORMAL,
                    danaPump.tempBasalStart,
                    activePlugin.activePump.model(),
                    activePlugin.activePump.serialNumber()
                )
                uiInteraction.addNotification(Notification.UNSUPPORTED_ACTION_IN_PUMP, rh.gs(app.aaps.pump.danar.R.string.unsupported_action_in_pump), Notification.URGENT)
                aapsLogger.error(LTag.PUMP, "Temporary basal should be running: DanaPump " + danaPump.temporaryBasalToString())
            }
        }
        // Extended bolus
        if (extendedBolus != null) {
            if (danaPump.isExtendedInProgress) {
                if (extendedBolus.rate != danaPump.extendedBolusAbsoluteRate
                    || abs(extendedBolus.timestamp - danaPump.extendedBolusStart) > 10000
                ) { // Close current extended
                    uiInteraction.addNotification(Notification.UNSUPPORTED_ACTION_IN_PUMP, rh.gs(app.aaps.pump.danar.R.string.unsupported_action_in_pump), Notification.URGENT)
                    aapsLogger.error(LTag.PUMP, "Different extended bolus found running AAPS: " + (extendedBolus.toString() + " DanaPump " + danaPump.extendedBolusToString()))
                    pumpSync.syncExtendedBolusWithPumpId(
                        danaPump.extendedBolusStart,
                        danaPump.extendedBolusAmount,
                        danaPump.extendedBolusDuration,
                        activePlugin.activePump.isFakingTempsByExtendedBoluses,
                        danaPump.tempBasalStart,
                        activePlugin.activePump.model(),
                        activePlugin.activePump.serialNumber()
                    )
                }
            } else {
                pumpSync.syncStopExtendedBolusWithPumpId(
                    dateUtil.now(),
                    dateUtil.now(),
                    activePlugin.activePump.model(),
                    activePlugin.activePump.serialNumber()
                )
                uiInteraction.addNotification(Notification.UNSUPPORTED_ACTION_IN_PUMP, rh.gs(app.aaps.pump.danar.R.string.unsupported_action_in_pump), Notification.URGENT)
                aapsLogger.error(LTag.PUMP, "Extended bolus should not be running. Sending stop to AAPS")
            }
        } else {
            if (danaPump.isExtendedInProgress) { // Create new
                uiInteraction.addNotification(Notification.UNSUPPORTED_ACTION_IN_PUMP, rh.gs(app.aaps.pump.danar.R.string.unsupported_action_in_pump), Notification.URGENT)
                aapsLogger.error(LTag.PUMP, "Extended bolus should not be running:  DanaPump " + danaPump.extendedBolusToString())
                pumpSync.syncExtendedBolusWithPumpId(
                    danaPump.extendedBolusStart,
                    danaPump.extendedBolusAmount,
                    danaPump.extendedBolusDuration,
                    activePlugin.activePump.isFakingTempsByExtendedBoluses,
                    danaPump.tempBasalStart,
                    activePlugin.activePump.model(),
                    activePlugin.activePump.serialNumber()
                )
            }
        }
    }
}
