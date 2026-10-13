package app.aaps.implementation.queue

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.work.WorkerParameters
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.androidPermissions.AndroidPermission
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissBolusProgressIfRunning
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.ui.R
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.extensions.safeDisable
import app.aaps.core.utils.extensions.safeEnable
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class QueueWorker internal constructor(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.IO) {

    @Inject lateinit var queue: CommandQueue
    @Inject lateinit var context: Context
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var androidPermission: AndroidPermission
    @Inject lateinit var config: Config

    private var connectLogged = false

    override suspend fun doWorkAndLog(): Result {
        queue.waitingForDisconnect = false
        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager?)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, rh.gs(config.appName) + ":" + this::class.simpleName)
        wakeLock?.acquire(T.mins(10).msecs())
        rxBus.send(EventQueueChanged())
        var lastCommandTime: Long
        lastCommandTime = System.currentTimeMillis()
        var connectionStartTime = lastCommandTime
        try {
            while (true) {
                if (isStopped) return Result.failure()
                val secondsElapsed = (System.currentTimeMillis() - connectionStartTime) / 1000
                val pump = activePlugin.activePump
                //  Manifest.permission.BLUETOOTH_CONNECT
                if (config.PUMPDRIVERS && pump !is VirtualPump)
                    if (androidPermission.permissionNotGranted(context, "android.permission.BLUETOOTH_CONNECT") || androidPermission.permissionNotGranted(context, "android.permission.BLUETOOTH_SCAN")) {
                        ToastUtils.errorToast(context, R.string.need_connect_permission)
                        aapsLogger.debug(LTag.PUMPQUEUE, "no permission")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING))
                        SystemClock.sleep(5000)
                        continue
                    }
                if (!pump.isConnected() && secondsElapsed > Constants.PUMP_MAX_CONNECTION_TIME_IN_SECONDS) {
                    rxBus.send(EventDismissBolusProgressIfRunning(null, null))
                    rxBus.send(EventPumpStatusChanged(rh.gs(R.string.connectiontimedout)))
                    aapsLogger.debug(LTag.PUMPQUEUE, "timed out")
                    pump.stopConnecting()

                    //BLUETOOTH-WATCHDOG
                    var watchdog = preferences.get(BooleanKey.PumpBtWatchdog)
                    val lastWatchdog = preferences.get(LongNonKey.BtWatchdogLastBark)
                    watchdog = watchdog && System.currentTimeMillis() - lastWatchdog > Constants.MIN_WATCHDOG_INTERVAL_IN_SECONDS * 1000
                    if (watchdog) {
                        aapsLogger.debug(LTag.PUMPQUEUE, "BT watchdog - toggling the phone bluetooth")
                        //write time
                        preferences.put(LongNonKey.BtWatchdogLastBark, System.currentTimeMillis())
                        //toggle BT
                        pump.disconnect("watchdog")
                        SystemClock.sleep(1000)
                        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter?.let { bluetoothAdapter ->
                            bluetoothAdapter.safeDisable(1000)
                            bluetoothAdapter.safeEnable(1000)
                        }
                        //start over again once after watchdog barked
                        //Notification notification = new Notification(Notification.OLD_NSCLIENT, "Watchdog", Notification.URGENT);
                        //rxBus.send(new EventNewNotification(notification));
                        lastCommandTime = System.currentTimeMillis()
                        connectionStartTime = lastCommandTime
                        pump.connect("watchdog")
                    } else {
                        queue.clear()
                        aapsLogger.debug(LTag.PUMPQUEUE, "no connection possible")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
                        pump.disconnect("Queue empty")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                        return Result.success()
                    }
                }
                if (pump.isHandshakeInProgress()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "handshaking $secondsElapsed")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.HANDSHAKING, secondsElapsed.toInt()))
                    SystemClock.sleep(100)
                    continue
                }
                if (pump.isConnecting()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "connecting $secondsElapsed")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, secondsElapsed.toInt()))
                    SystemClock.sleep(1000)
                    continue
                }
                if (!pump.isConnected()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "connect")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, secondsElapsed.toInt()))
                    pump.connect("Connection needed")
                    SystemClock.sleep(1000)
                    continue
                }
                if (pump.isBusy()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "busy")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, secondsElapsed.toInt()))
                    SystemClock.sleep(1000)
                    continue
                }
                if (queue.performing() == null) {
                    if (!connectLogged) {
                        connectLogged = true
                        aapsLogger.debug(LTag.PUMPQUEUE, "connection time " + secondsElapsed + "s")
                    }
                    // Pickup 1st command and set performing variable
                    if (queue.size() > 0) {
                        queue.pickup()
                        val cont = queue.performing()?.let {
                            aapsLogger.debug(LTag.PUMPQUEUE, "performing " + it.log())
                            rxBus.send(EventQueueChanged())
                            rxBus.send(EventPumpStatusChanged(it.status()))
                            it.execute()
                            queue.resetPerforming()
                            rxBus.send(EventQueueChanged())
                            lastCommandTime = System.currentTimeMillis()
                            SystemClock.sleep(100)
                            true
                        } == true
                        if (cont) {
                            continue
                        }
                    }
                }
                if (queue.size() == 0 && queue.performing() == null) {
                    val secondsFromLastCommand = (System.currentTimeMillis() - lastCommandTime) / 1000
                    if (secondsFromLastCommand >= pump.waitForDisconnectionInSeconds()) {
                        queue.waitingForDisconnect = true
                        aapsLogger.debug(LTag.PUMPQUEUE, "queue empty. disconnect")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
                        pump.disconnect("Queue empty")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                        aapsLogger.debug(LTag.PUMPQUEUE, "disconnected")
                        return Result.success()
                    } else {
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.WAITING_FOR_DISCONNECTION))
                        aapsLogger.debug(LTag.PUMPQUEUE, "waiting for disconnect")
                        SystemClock.sleep(1000)
                    }
                }
            }
        } finally {
            if (wakeLock?.isHeld == true) wakeLock.release()
            aapsLogger.debug(LTag.PUMPQUEUE, "work end")
        }
    }
}