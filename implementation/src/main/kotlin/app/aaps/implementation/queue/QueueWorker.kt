package app.aaps.implementation.queue

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.workflow.LoggingWorker
import app.aaps.core.ui.R
import app.aaps.core.utils.extensions.safeDisable
import app.aaps.core.utils.extensions.safeEnable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

@HiltWorker
class QueueWorker @AssistedInject internal constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    private val queue: CommandQueue,
    private val rxBus: RxBus,
    private val activePlugin: ActivePlugin,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val config: Config,
    private val bolusProgressData: BolusProgressData
) : LoggingWorker(context, params, Dispatchers.IO, aapsLogger, fabricPrivacy) {

    private var connectLogged = false

    override suspend fun doWorkAndLog(): Result {
        queue.waitingForDisconnect = false
        // Defensive: a previous worker may have been canceled mid-execute (e.g. blocking sleep
        // not honoring coroutine cancellation), leaving `performing` set. Without this reset the
        // new worker's main loop has no matching branch (performing != null AND queue non-empty)
        // and spins.
        queue.resetPerforming()
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
                if (!pump.isConfigured()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "pump not configured - completing queue as no-op")
                    queue.completeAllAsNoOp(R.string.pump_not_configured)
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                    return Result.success()
                }
                if (config.PUMPDRIVERS && pump.selectedActivePump() !is VirtualPump)
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.BLUETOOTH_SCAN
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        rxBus.send(EventShowSnackbar(rh.gs(R.string.need_connect_permission), EventShowSnackbar.Type.Error))
                        aapsLogger.debug(LTag.PUMPQUEUE, "no permission")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING))
                        delay(timeMillis = 5000)
                        continue
                    }
                if (!pump.isConnected() && secondsElapsed > Constants.PUMP_MAX_CONNECTION_TIME_IN_SECONDS) {
                    bolusProgressData.clear()
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
                        delay(timeMillis = 1000)
                        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter?.let { bluetoothAdapter ->
                            bluetoothAdapter.safeDisable(0)
                            delay(timeMillis = 1000)
                            bluetoothAdapter.safeEnable(0)
                            delay(timeMillis = 1000)
                        }
                        //start over again once after watchdog barked
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
                    delay(timeMillis = 100)
                    continue
                }
                if (pump.isConnecting()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "connecting $secondsElapsed")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, secondsElapsed.toInt()))
                    delay(timeMillis = 1000)
                    continue
                }
                if (!pump.isConnected()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "connect")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, secondsElapsed.toInt()))
                    pump.connect("Connection needed")
                    delay(timeMillis = 1000)
                    continue
                }
                if (pump.isBusy()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "busy")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, secondsElapsed.toInt()))
                    delay(timeMillis = 1000)
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
                            try {
                                it.executeWithCallback()
                            } catch (e: CancellationException) {
                                throw e // honor coroutine cancellation (worker stopped)
                            } catch (e: Exception) {
                                // A pump-driver throw must not kill the worker. Without this catch the
                                // exception unwinds out of doWorkAndLog(): the command's callback never
                                // runs (the caller's deferred await() hangs forever) and `performing`
                                // stays set. Complete the caller with a failure result and carry on so
                                // the remaining queue and the disconnect logic still run.
                                aapsLogger.error(LTag.PUMPQUEUE, "Command threw during execution: " + it.log(), e)
                                fabricPrivacy.logException(e)
                                it.cancel(R.string.error, success = false)
                            }
                            queue.resetPerforming()
                            rxBus.send(EventQueueChanged())
                            lastCommandTime = System.currentTimeMillis()
                            delay(timeMillis = 100)
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
                        delay(timeMillis = 1000)
                    }
                } else {
                    // Catch-all: no branch above matched (e.g. performing != null and queue non-empty,
                    // which can happen if a previous worker was canceled mid-execute). Without a yield
                    // here the loop spins CPU and the isStopped check never gets to fire.
                    delay(timeMillis = 100)
                }
            }
        } finally {
            if (wakeLock?.isHeld == true) wakeLock.release()
            aapsLogger.debug(LTag.PUMPQUEUE, "work end")
        }
    }
}