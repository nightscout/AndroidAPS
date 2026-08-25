package app.aaps.implementation.queue

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
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
import app.aaps.core.ui.R
import app.aaps.core.utils.extensions.safeDisable
import app.aaps.core.utils.extensions.safeEnable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-owned host for the pump command loop. Replaces the WorkManager `QueueWorker`.
 *
 * The loop runs on a single dedicated thread owned by this singleton and lives for the whole app
 * session (the process is kept alive by the existing foreground service — no new notification).
 * WorkManager is intentionally NOT the execution host: its "stop / relaunch / duplicate / retry
 * across process death" semantics are a hazard for a non-idempotent insulin bolus and were the root
 * cause of the mid-bolus self-inflicted disconnect. Here there is exactly one owner by construction,
 * so a second drain can never run, `performing` can never be wiped in flight, and a bolus is never
 * interrupted by the framework.
 *
 * `drainOnce()` is a faithful port of the old `QueueWorker.doWorkAndLog()` state machine, with two
 * deliberate differences:
 *  1. the defensive entry `resetPerforming()` is gone (it wiped a live bolus's `performing`); the
 *     reset now happens in a `finally` around execution so it is always cleared and never leaked;
 *  2. the queue-empty disconnect tail consumes a pending wake before disconnecting so a command that
 *     arrives during the linger keeps draining instead of forcing a disconnect+reconnect.
 */
@Singleton
class CommandExecutor @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val fabricPrivacy: FabricPrivacy,
    private val queue: CommandQueue,
    private val rxBus: RxBus,
    private val activePlugin: ActivePlugin,
    private val rh: ResourceHelper,
    private val preferences: Preferences,
    private val config: Config,
    private val bolusProgressData: BolusProgressData,
    @ApplicationContext private val context: Context
) {

    // A single dedicated serial thread: guarantees strict command ordering and isolates long blocking
    // BLE I/O from the shared Dispatchers.Default pool that backs @ApplicationScope.
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "CommandExecutor") }.asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    // Conflated: many notifyAboutNewCommand() signals during one drain collapse to a single pending
    // wake. A duplicate signal while the loop is busy is a no-op.
    private val wake = Channel<Unit>(Channel.CONFLATED)

    @Volatile private var loopJob: Job? = null
    @Volatile private var draining = false

    /** True while a drain is actively processing the queue (used by e2e idle detection). */
    fun isProcessing(): Boolean = draining

    /**
     * Idempotent. Launches the single drain loop at most once. A duplicate call while the loop is
     * alive is a no-op by construction — this is the single-owner guarantee WorkManager could not give.
     */
    @Synchronized
    fun ensureRunning() {
        val j = loopJob
        if (j != null && j.isActive) return
        loopJob = scope.launch { runLoop() }
    }

    /** Replaces `enqueueUniqueWork`. Wakes an idle loop / nudges a live one; never spawns a 2nd loop. */
    fun signal() {
        ensureRunning()
        wake.trySend(Unit)
    }

    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                wake.receive()          // suspend (zero CPU) until a command is enqueued
                drainOnce()             // full connect -> execute -> disconnect state machine, one run
            } catch (e: CancellationException) {
                throw e                 // genuine app shutdown only
            } catch (t: Throwable) {
                // Self-heal: a driver/loop throw must not kill the queue for the rest of the session.
                aapsLogger.error(LTag.PUMPQUEUE, "CommandExecutor loop error; continuing", t)
                fabricPrivacy.logException(RuntimeException(t))
            }
        }
    }

    private suspend fun drainOnce() {
        draining = true
        queue.waitingForDisconnect = false
        var connectLogged = false
        val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager?)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, rh.gs(config.appName) + ":CommandExecutor")
        wakeLock?.acquire(T.mins(10).msecs())
        rxBus.send(EventQueueChanged())
        var lastCommandTime = System.currentTimeMillis()
        var connectionStartTime = lastCommandTime
        try {
            while (true) {
                currentCoroutineContext().ensureActive()    // cooperative cancel = app shutdown only
                val secondsElapsed = (System.currentTimeMillis() - connectionStartTime) / 1000
                val pump = activePlugin.activePump
                if (!pump.isConfigured()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "pump not configured - completing queue as no-op")
                    queue.completeAllAsNoOp(R.string.pump_not_configured)
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                    return
                }
                if (config.PUMPDRIVERS && pump.selectedActivePump() !is VirtualPump)
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED
                    ) {
                        rxBus.send(EventShowSnackbar(rh.gs(R.string.need_connect_permission), EventShowSnackbar.Type.Error))
                        aapsLogger.debug(LTag.PUMPQUEUE, "no permission")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING))
                        delay(5000)
                        continue
                    }
                if (!pump.isConnected() && secondsElapsed > Constants.PUMP_MAX_CONNECTION_TIME_IN_SECONDS) {
                    bolusProgressData.clear()
                    rxBus.send(EventPumpStatusChanged(rh.gs(R.string.connectiontimedout)))
                    aapsLogger.debug(LTag.PUMPQUEUE, "timed out")
                    pump.stopConnecting()

                    // BLUETOOTH-WATCHDOG
                    var watchdog = preferences.get(BooleanKey.PumpBtWatchdog)
                    val lastWatchdog = preferences.get(LongNonKey.BtWatchdogLastBark)
                    watchdog = watchdog && System.currentTimeMillis() - lastWatchdog > Constants.MIN_WATCHDOG_INTERVAL_IN_SECONDS * 1000
                    if (watchdog) {
                        aapsLogger.debug(LTag.PUMPQUEUE, "BT watchdog - toggling the phone bluetooth")
                        preferences.put(LongNonKey.BtWatchdogLastBark, System.currentTimeMillis())
                        pump.disconnect("watchdog")
                        delay(1000)
                        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?)?.adapter?.let { bluetoothAdapter ->
                            bluetoothAdapter.safeDisable(0)
                            delay(1000)
                            bluetoothAdapter.safeEnable(0)
                            delay(1000)
                        }
                        // start over again once after watchdog barked
                        lastCommandTime = System.currentTimeMillis()
                        connectionStartTime = lastCommandTime
                        pump.connect("watchdog")
                    } else {
                        queue.clear()
                        aapsLogger.debug(LTag.PUMPQUEUE, "no connection possible")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
                        pump.disconnect("Queue empty")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                        return
                    }
                }
                if (pump.isHandshakeInProgress()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "handshaking $secondsElapsed")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.HANDSHAKING, secondsElapsed.toInt()))
                    delay(100)
                    continue
                }
                if (pump.isConnecting()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "connecting $secondsElapsed")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, secondsElapsed.toInt()))
                    delay(1000)
                    continue
                }
                if (!pump.isConnected()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "connect")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, secondsElapsed.toInt()))
                    pump.connect("Connection needed")
                    delay(1000)
                    continue
                }
                if (pump.isBusy()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "busy")
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING, secondsElapsed.toInt()))
                    delay(1000)
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
                        val cmd = queue.performing()
                        if (cmd != null) {
                            aapsLogger.debug(LTag.PUMPQUEUE, "performing " + cmd.log())
                            rxBus.send(EventQueueChanged())
                            rxBus.send(EventPumpStatusChanged(cmd.status()))
                            try {
                                cmd.executeWithCallback()
                            } catch (e: CancellationException) {
                                throw e // honor coroutine cancellation (app shutdown)
                            } catch (e: Exception) {
                                // A pump-driver throw must not kill the loop. Complete the caller with a
                                // failure result and carry on so the remaining queue + disconnect run.
                                aapsLogger.error(LTag.PUMPQUEUE, "Command threw during execution: " + cmd.log(), e)
                                fabricPrivacy.logException(e)
                                cmd.cancel(R.string.error, success = false)
                            } finally {
                                // Honest `performing`: cleared on EVERY exit path (normal, driver throw,
                                // cancellation) — never leaked, so no defensive entry-reset is needed.
                                queue.resetPerforming()
                            }
                            rxBus.send(EventQueueChanged())
                            lastCommandTime = System.currentTimeMillis()
                            delay(100)
                            continue
                        }
                    }
                }
                if (queue.size() == 0 && queue.performing() == null) {
                    val secondsFromLastCommand = (System.currentTimeMillis() - lastCommandTime) / 1000
                    if (secondsFromLastCommand >= pump.waitForDisconnectionInSeconds()) {
                        // A command may have been enqueued during the linger. Consume a pending wake and
                        // keep draining instead of disconnecting, to avoid a needless disconnect+reconnect.
                        if (queue.size() > 0 || wake.tryReceive().isSuccess) continue
                        queue.waitingForDisconnect = true
                        aapsLogger.debug(LTag.PUMPQUEUE, "queue empty. disconnect")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTING))
                        pump.disconnect("Queue empty")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                        aapsLogger.debug(LTag.PUMPQUEUE, "disconnected")
                        return
                    } else {
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.WAITING_FOR_DISCONNECTION))
                        aapsLogger.debug(LTag.PUMPQUEUE, "waiting for disconnect")
                        delay(1000)
                    }
                } else {
                    // Catch-all yield: with a single owner `performing` is always null here, but keep the
                    // yield so the loop can never busy-spin.
                    delay(100)
                }
            }
        } finally {
            if (wakeLock?.isHeld == true) wakeLock.release()
            draining = false
            aapsLogger.debug(LTag.PUMPQUEUE, "drain end")
        }
    }

    /** Test seam: run exactly one drain synchronously on the caller's dispatcher. */
    @VisibleForTesting
    suspend fun drainQueueForTest() = drainOnce()
}
