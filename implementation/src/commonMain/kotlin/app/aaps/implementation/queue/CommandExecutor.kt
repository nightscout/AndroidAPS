package app.aaps.implementation.queue

import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.aapsIoDispatcher
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.CancellationException
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.Inject

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
@SingleIn(AppScope::class)
class CommandExecutor @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val fabricPrivacy: FabricPrivacy,
    private val queue: CommandQueue,
    private val rxBus: RxBus,
    private val activePlugin: ActivePlugin,
    private val rh: TextResolver,
    private val preferences: Preferences,
    private val config: Config,
    private val bolusProgressData: BolusProgressData,
    private val platform: CommandExecutionPlatform
) {

    // One command at a time: guarantees strict command ordering and isolates long blocking BLE I/O
    // from the shared pool that backs the application scope.
    //
    // Was `Executors.newSingleThreadExecutor { Thread(it, "CommandExecutor") }`, which is JVM only.
    // `limitedParallelism(1)` keeps the ordering guarantee - at most one task runs at a time - but NOT
    // thread affinity: successive commands may land on different IO threads. Nothing here depends on a
    // specific thread; a pump driver that does would already have been broken by the coroutine port.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = aapsIoDispatcher.limitedParallelism(1)
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    // Was @Synchronized on ensureRunning, which locks `this` and is JVM only. Same guard: the loop is
    // launched at most once even if two callers signal at the same moment.
    private val lock = AapsLock()

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
    fun ensureRunning(): Unit = lock.withLock {
        val j = loopJob
        if (j != null && j.isActive) return@withLock
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
        val wakeLock = platform.acquireWakeLock(rh.gs(config.appName) + ":CommandExecutor", T.mins(10).msecs())
        rxBus.send(EventQueueChanged())
        var lastCommandTime = Clock.System.now().toEpochMilliseconds()
        var connectionStartTime = lastCommandTime
        // When the current hold started, so its duration can be given back to the timers above.
        var heldSince: Long? = null
        try {
            while (true) {
                currentCoroutineContext().ensureActive()    // cooperative cancel = app shutdown only
                // Before anything touches `activePlugin.activePump`. Someone is applying imported
                // settings, which stops and starts pump drivers, so this must not connect, handshake or
                // pick anything up - and must not even resolve the pump, which is being swapped under
                // it. Sitting below the connect branch was wrong: the loop went on trying to reach the
                // outgoing driver for the whole hold and never reached this check.
                //
                // Note this deliberately does NOT make `withHold` wait for a connection in progress.
                // Connecting is not a command in flight, so applying is safe, and treating it as busy
                // would stall the apply behind a driver that cannot connect at all - the case that
                // prompted this, where a pump was selected with no hardware to answer.
                if (queue.isHeld()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "queue held")
                    heldSince = heldSince ?: Clock.System.now().toEpochMilliseconds()
                    delay(500)
                    continue
                }
                heldSince?.let {
                    // Do not charge the hold to the connection attempt. Without this a long hold pushes
                    // secondsElapsed past PUMP_MAX_CONNECTION_TIME_IN_SECONDS, and the first iteration
                    // after release takes the "timed out" branch and clears the queue - cancelling
                    // commands that were only ever waiting for the apply to finish.
                    val holdDuration = Clock.System.now().toEpochMilliseconds() - it
                    connectionStartTime += holdDuration
                    lastCommandTime += holdDuration
                    heldSince = null
                }
                val secondsElapsed = (Clock.System.now().toEpochMilliseconds() - connectionStartTime) / 1000
                val pump = activePlugin.activePump
                if (!pump.isConfigured()) {
                    aapsLogger.debug(LTag.PUMPQUEUE, "pump not configured - completing queue as no-op")
                    queue.completeAllAsNoOp(CoreUiStrings.pump_not_configured)
                    rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.DISCONNECTED))
                    return
                }
                if (config.PUMPDRIVERS && pump.selectedActivePump() !is VirtualPump)
                    if (!platform.hasBluetoothPermission()) {
                        rxBus.send(EventShowSnackbar(rh.gs(CoreUiStrings.need_connect_permission), EventShowSnackbar.Type.Error))
                        aapsLogger.debug(LTag.PUMPQUEUE, "no permission")
                        rxBus.send(EventPumpStatusChanged(EventPumpStatusChanged.Status.CONNECTING))
                        delay(5000)
                        continue
                    }
                if (!pump.isConnected() && secondsElapsed > Constants.PUMP_MAX_CONNECTION_TIME_IN_SECONDS) {
                    bolusProgressData.clear()
                    rxBus.send(EventPumpStatusChanged(rh.gs(CoreUiStrings.connectiontimedout)))
                    aapsLogger.debug(LTag.PUMPQUEUE, "timed out")
                    pump.stopConnecting()

                    // BLUETOOTH-WATCHDOG
                    var watchdog = preferences.get(BooleanKey.PumpBtWatchdog)
                    val lastWatchdog = preferences.get(LongNonKey.BtWatchdogLastBark)
                    // canRestartBluetooth is part of the condition, not a check inside the branch: where
                    // the platform cannot toggle the radio this must behave exactly as it does when the
                    // user has the watchdog switched off, rather than "barking" and doing nothing.
                    watchdog = watchdog && platform.canRestartBluetooth &&
                        Clock.System.now().toEpochMilliseconds() - lastWatchdog > Constants.MIN_WATCHDOG_INTERVAL_IN_SECONDS * 1000
                    if (watchdog) {
                        aapsLogger.debug(LTag.PUMPQUEUE, "BT watchdog - toggling the phone bluetooth")
                        preferences.put(LongNonKey.BtWatchdogLastBark, Clock.System.now().toEpochMilliseconds())
                        pump.disconnect("watchdog")
                        delay(1000)
                        platform.restartBluetooth()
                        // start over again once after watchdog barked
                        lastCommandTime = Clock.System.now().toEpochMilliseconds()
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
                                cmd.cancel(CoreUiStrings.error, success = false)
                            } finally {
                                // Honest `performing`: cleared on EVERY exit path (normal, driver throw,
                                // cancellation) — never leaked, so no defensive entry-reset is needed.
                                queue.resetPerforming()
                            }
                            rxBus.send(EventQueueChanged())
                            lastCommandTime = Clock.System.now().toEpochMilliseconds()
                            delay(100)
                            continue
                        }
                    }
                }
                if (queue.size() == 0 && queue.performing() == null) {
                    val secondsFromLastCommand = (Clock.System.now().toEpochMilliseconds() - lastCommandTime) / 1000
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
            wakeLock?.release()
            draining = false
            aapsLogger.debug(LTag.PUMPQUEUE, "drain end")
        }
    }

    /** Test seam: run exactly one drain synchronously on the caller's dispatcher. */
    suspend fun drainQueueForTest() = drainOnce()
}
