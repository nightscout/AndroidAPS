package app.aaps.cgm.dexcomg7.session

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import app.aaps.cgm.dexcomg7.ble.G7Bluetooth
import app.aaps.cgm.dexcomg7.ble.G7GattClient
import app.aaps.cgm.dexcomg7.data.G7CalibrationRecord
import app.aaps.cgm.dexcomg7.data.G7CommLog
import app.aaps.cgm.dexcomg7.data.G7State
import app.aaps.cgm.dexcomg7.data.G7StateStore
import app.aaps.cgm.dexcomg7.protocol.ExtendedVersionMessage
import app.aaps.cgm.dexcomg7.protocol.G7AuthException
import app.aaps.cgm.dexcomg7.protocol.G7AuthFailureCode
import app.aaps.cgm.dexcomg7.protocol.G7Authenticator
import app.aaps.cgm.dexcomg7.protocol.G7BackfillMessage
import app.aaps.cgm.dexcomg7.protocol.G7CalibrateRxMessage
import app.aaps.cgm.dexcomg7.protocol.G7CalibrateTxMessage
import app.aaps.cgm.dexcomg7.protocol.G7CalibrationBoundsMessage
import app.aaps.cgm.dexcomg7.protocol.G7CalibrationProcessingStatus
import app.aaps.cgm.dexcomg7.protocol.G7Characteristic
import app.aaps.cgm.dexcomg7.protocol.G7Commands
import app.aaps.cgm.dexcomg7.protocol.G7GlucoseMessage
import app.aaps.cgm.dexcomg7.protocol.G7Lifecycle
import app.aaps.cgm.dexcomg7.protocol.G7LinkException
import app.aaps.cgm.dexcomg7.protocol.G7Opcode
import app.aaps.cgm.dexcomg7.protocol.TransmitterVersionMessage
import app.aaps.cgm.dexcomg7.protocol.hexToBytesOrNull
import app.aaps.cgm.dexcomg7.protocol.toHex
import app.aaps.core.interfaces.db.PersistenceLayer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.DateFormat

/**
 * The live session with the paired sensor.
 *
 * On every connection: run the handshake (one challenge with the stored key, or the full key
 * exchange with the stored code), ask for a reading, and once the reading is in, ask for what is
 * missing in the database (backfill, only when there is a gap), send a waiting calibration, and learn the sensor's session length, serial and
 * firmware. Then the sensor drops the link and we wait for its next advertisement, about five minutes
 * later. This follows Trio's G7Sensor; the Android side follows what Watch-APS learned.
 *
 * All state here is touched only on [scope], which runs one piece of work at a time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@Inject
class G7Session(
    private val context: Context,
    private val bluetooth: G7Bluetooth,
    private val store: G7StateStore,
    private val commLog: G7CommLog,
    private val persistenceLayer: PersistenceLayer
) : G7GattClient.Events {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val _readings = MutableSharedFlow<List<G7Reading>>(extraBufferCapacity = 64)

    /** New readings, live and from backfill. The plugin stores the usable ones. */
    val readings: SharedFlow<List<G7Reading>> = _readings.asSharedFlow()

    private val _status = MutableStateFlow(G7ConnectionStatus.STOPPED)
    val status: StateFlow<G7ConnectionStatus> = _status.asStateFlow()

    private var running = false
    private var paused = false
    private var client: G7GattClient? = null
    private var watchdog: Job? = null
    private var rearmJob: Job? = null
    private var idleJob: Job? = null

    /** Links the sensor opened right after the current reading. Logged as one line, see [logCameBack]. */
    private var cameBack = 0
    private var cameBackJob: Job? = null
    private var followUps: Job? = null

    /** Phone time of the sensor's second zero for this connection. See [G7Lifecycle.clockAnchor]. */
    private var activationDate: Long? = null
    private var backfillPending = false
    private var backfillBuffer = mutableListOf<G7BackfillMessage>()

    /** Newest reading in the database when this connection opened; a backfill starts after it. */
    private var latestAtSessionStart: Long? = null

    /** Phone time when the last live reading arrived. */
    private var lastReadingReceivedAt = 0L
    private var versionAsked = false
    private var boundsRequestPending = false
    private var pendingAnswer: Pair<Int, CompletableDeferred<ByteArray>>? = null

    /** When any packet last came from the sensor, usable or not. Warmup readings count too. */
    @Volatile var lastPacketAt = 0L
        private set
    private var startedAt = 0L
    private var lastRestartAt = 0L
    private var restartsSinceReading = 0

    /** After a refusal we leave the sensor alone for a while: four refusals in a row lock it. */
    private var retryNotBefore = 0L

    private fun log(text: String) = commLog.add(text)

    // ------------------------------------------------------------------------------------ control

    fun start() = scope.launch {
        if (running) return@launch
        running = true
        startedAt = System.currentTimeMillis()
        registerBluetoothReceiver()
        startWatchdog()
        connectToPairedSensor()
    }

    fun stop() = scope.launch {
        running = false
        watchdog?.cancel()
        unregisterBluetoothReceiver()
        closeClient()
        _status.value = G7ConnectionStatus.STOPPED
    }

    /** Lets a pairing run use Bluetooth alone. The old sensor is let go until [resume]. */
    suspend fun pauseForPairing() {
        scope.launch {
            paused = true
            closeClient()
            if (running) _status.value = G7ConnectionStatus.PAIRING
        }.join()
    }

    /** Goes back to the paired sensor after a pairing run that did not hand over a link. */
    fun resume() = scope.launch {
        paused = false
        if (running) connectToPairedSensor()
    }

    /**
     * Takes over the connection a pairing run just authenticated on, so the first reading comes now and
     * not five minutes later.
     */
    fun adoptPairedConnection(pairedClient: G7GattClient) = scope.launch {
        paused = false
        closeClient()
        resetSessionMemory()
        restartsSinceReading = 0
        retryNotBefore = 0
        client = pairedClient
        pairedClient.events = this@G7Session
        if (!running) {
            // Paired while the source is off: keep the key, drop the link. It opens when the source starts.
            closeClient()
            return@launch
        }
        if (pairedClient.isConnected) {
            _status.value = G7ConnectionStatus.CONNECTED
            store.update { it.copy(latestConnectAt = System.currentTimeMillis()) }
            beginSession(pairedClient)
        } else {
            log("The link dropped right after pairing; waiting for the sensor to come back")
            _status.value = G7ConnectionStatus.WAITING
            pairedClient.reconnect()
        }
    }

    /**
     * Forgets the paired sensor: closes the link, removes the phone's bond, and clears the key, code
     * and address. What is known about the sensor is kept as the previous sensor.
     */
    fun forgetSensor(removeBond: Boolean) = scope.launch {
        val c = client
        closeClient()
        val old = store.value
        if (removeBond) {
            (c ?: old.address?.let { bluetooth.device(it) }?.let { G7GattClient(context, it, null, ::log) })?.removeBond()
        }
        store.update { current -> G7State(previousSensor = if (current.address != null) current.archived(System.currentTimeMillis(), "forgotten") else current.previousSensor) }
        log("Sensor forgotten")
        resetSessionMemory()
        _status.value = if (running) G7ConnectionStatus.UNPAIRED else G7ConnectionStatus.STOPPED
    }

    /** Queues a meter value for the sensor. It is sent on the next connection, stamped with [takenAt]. */
    fun calibrate(glucoseMgdl: Int, takenAt: Long) = scope.launch {
        store.update { it.copy(calibration = G7CalibrationRecord(glucoseMgdl, takenAt)) }
        log("Calibration $glucoseMgdl mg/dL entered; it goes to the sensor on the next connection")
    }

    fun cancelPendingCalibration() = scope.launch {
        store.update { if (it.calibration?.outcome == G7CalibrationRecord.Outcome.PENDING) it.copy(calibration = null) else it }
        log("Waiting calibration cancelled")
    }

    private fun resetSessionMemory() {
        activationDate = null
        backfillPending = false
        backfillBuffer = mutableListOf()
        versionAsked = false
        boundsRequestPending = false
        followUps?.cancel()
    }

    private fun closeClient() {
        rearmJob?.cancel()
        idleJob?.cancel()
        followUps?.cancel()
        logCameBack()
        client?.let {
            it.events = null
            it.close()
        }
        client = null
    }

    private fun connectToPairedSensor() {
        if (!running || paused) return
        val address = store.value.address
        if (address == null) {
            _status.value = G7ConnectionStatus.UNPAIRED
            return
        }
        bluetooth.blockedReason()?.let {
            log("Not connecting: $it")
            _status.value = G7ConnectionStatus.BLOCKED
            return
        }
        val device = bluetooth.device(address) ?: run {
            log("$address is not a valid Bluetooth address")
            _status.value = G7ConnectionStatus.BLOCKED
            return
        }
        closeClient()
        // autoConnect: a standing request the stack completes when the sensor advertises, also in Doze.
        log("Waiting for the sensor to connect")
        client = G7GattClient(context, device, this, ::log).also { it.connect(autoConnect = true) }
        _status.value = G7ConnectionStatus.WAITING
    }

    // ----------------------------------------------------------------------------- GATT events

    override fun onReady(client: G7GattClient) {
        scope.launch { handleReady(client) }
    }

    override fun onDisconnected(client: G7GattClient, status: Int) {
        scope.launch { handleDisconnected(client, status) }
    }

    private suspend fun handleReady(c: G7GattClient) {
        if (c !== client || !running) return
        if (!c.hasCgmService()) {
            log("${c.address} has no Dexcom CGM service")
            c.disconnect()
            return
        }
        if (isRightAfterReading()) {
            // The sensor still advertises for a moment after it ends a cycle, and the standing
            // connection request picks that up. A handshake now fails anyway, so leave it for the next
            // reading. Keep the link open and idle rather than hanging up: hanging up re-armed the request
            // at once, the sensor was still advertising, and phone and sensor went round that loop about
            // a hundred times a second. The sensor ends an idle link itself; the job below is only a
            // fallback in case it does not. It happens after almost every reading, so it is counted and
            // logged once when the quiet minute is over, not line by line.
            if (cameBack++ == 0) {
                cameBackJob?.cancel()
                cameBackJob = scope.launch {
                    delay(maxOf(0L, lastReadingReceivedAt + QUIET_AFTER_READING_MS - System.currentTimeMillis()))
                    logCameBack()
                }
            }
            _status.value = G7ConnectionStatus.WAITING
            idleJob?.cancel()
            idleJob = scope.launch {
                delay(maxOf(0L, lastReadingReceivedAt + QUIET_AFTER_READING_MS - System.currentTimeMillis()))
                if (c === client && c.isConnected) {
                    log("Ending the idle link so the next reading can connect")
                    c.disconnect()
                }
            }
            return
        }
        logCameBack()
        _status.value = G7ConnectionStatus.CONNECTED
        store.update { it.copy(latestConnectAt = System.currentTimeMillis()) }
        if (System.currentTimeMillis() < retryNotBefore) {
            log("The sensor refused us recently; leaving it alone until ${DateFormat.getTimeInstance().format(retryNotBefore)}")
            c.disconnect()
            return
        }
        val state = store.value
        val key = state.sharedKeyHex?.hexToBytesOrNull()
        val code = state.pairingCode
        if (key == null && code == null) {
            log("No key and no pairing code stored; pair the sensor again")
            c.disconnect()
            return
        }
        val authenticator = G7Authenticator(
            pairingCode = code,
            storedSharedKey = key,
            stepTimeoutMs = if (key == null) G7Authenticator.PAIRING_STEP_TIMEOUT_MS else G7Authenticator.RECONNECT_STEP_TIMEOUT_MS,
            log = ::log
        )
        try {
            val result = authenticator.authenticate(c)
            if (result.didExchangeKeys) {
                log("New key agreed with the sensor")
                store.update { it.copy(sharedKeyHex = result.sharedKey.toHex()) }
            }
            beginSession(c)
        } catch (e: G7AuthException) {
            handleAuthenticationFailure(c, e, hadKey = key != null)
        } catch (e: G7LinkException) {
            log("Link problem during the handshake: ${e.message}")
            if (e.insufficientAuthentication && key != null && code != null) {
                // The sensor wants an encrypted link our bond cannot give. Drop both, pair again with the code.
                log("Dropping the old bond and key; the next connection pairs again with the stored code")
                c.removeBond()
                store.update { it.copy(sharedKeyHex = null) }
            }
            c.disconnect()
        }
    }

    private fun handleAuthenticationFailure(c: G7GattClient, e: G7AuthException, hadKey: Boolean) {
        val code = store.value.pairingCode
        val keyIsStale = e is G7AuthException.ChallengeMismatch || (e is G7AuthException.Rejected && e.failureCode == G7AuthFailureCode.NO_APP_KEY)
        when {
            keyIsStale && hadKey && code != null -> {
                // Fixable without the user: forget the key; the next connection runs the key exchange again.
                log("The saved key is no longer accepted; the next connection pairs again with the stored code")
                store.update { it.copy(sharedKeyHex = null) }
            }

            // The key is fine, so no pause: a pause would also skip the sensor's next readings.
            e is G7AuthException.NotBonded -> log("The sensor accepted the key but not the bond (bond=${e.bondStatus}); trying again at the next reading")

            e is G7AuthException.Rejected || e is G7AuthException.ChallengeMismatch || e is G7AuthException.UnexpectedResponse -> {
                log("The sensor refused the connection: ${e.message}")
                retryNotBefore = System.currentTimeMillis() + REFUSAL_PAUSE_MS
                store.update { it.copy(lastAuthenticationFailure = describe(e), lastAuthenticationFailureAt = System.currentTimeMillis()) }
            }

            else -> log("Handshake failed: ${e.message}")
        }
        c.disconnect()
    }

    private fun describe(e: G7AuthException): String = when (e) {
        is G7AuthException.Rejected           -> when (e.failureCode) {
            G7AuthFailureCode.DEVICE_TYPE_RESTRICTION -> REFUSAL_SLOT_TAKEN
            G7AuthFailureCode.CHALLENGE_MISMATCH      -> REFUSAL_KEY
            else                                      -> REFUSAL_OTHER
        }

        is G7AuthException.ChallengeMismatch  -> REFUSAL_WRONG_CODE
        else                                  -> REFUSAL_OTHER
    }

    private fun handleDisconnected(c: G7GattClient, status: Int) {
        if (c !== client) return
        idleJob?.cancel()
        flushBackfill()
        followUps?.cancel()
        pendingAnswer?.second?.cancel()
        pendingAnswer = null
        backfillPending = false
        val hadReading = gotReadingThisConnection
        gotReadingThisConnection = false
        // The sensor comes back a few times in the next seconds. The client leaves those links out of the log.
        if (hadReading) c.quietUntil = lastReadingReceivedAt + QUIET_AFTER_READING_MS
        if (!running || paused) return
        _status.value = G7ConnectionStatus.WAITING
        // After a normal reading cycle, re-arm at once: a delayed re-arm can be held back for minutes
        // while the phone dozes, and the sensor's next advertisement would be missed. The same goes for
        // a link we dropped because it came right after a reading. After a connection that failed
        // before a reading (often status 133, or a handshake cut short), wait a little, or it would be
        // retried as fast as the callback can fire.
        val wait = maxOf(if (hadReading || isRightAfterReading()) 0L else REARM_DELAY_MS, retryNotBefore - System.currentTimeMillis())
        rearmJob?.cancel()
        if (wait <= 0L) {
            c.reconnect()
            return
        }
        rearmJob = scope.launch {
            delay(wait)
            if (running && !paused && c === client) c.reconnect()
        }
    }

    /** True once the current connection brought a reading. */
    private var gotReadingThisConnection = false

    /** Writes how often the sensor came back right after its reading, if it did. */
    private fun logCameBack() {
        cameBackJob?.cancel()
        cameBackJob = null
        if (cameBack == 0) return
        log("The sensor came back $cameBack ${if (cameBack == 1) "time" else "times"} right after its reading; the links were left idle")
        cameBack = 0
    }

    private fun isRightAfterReading(): Boolean = System.currentTimeMillis() - lastReadingReceivedAt < QUIET_AFTER_READING_MS

    // ----------------------------------------------------------------------------- the session

    /** Subscribes to readings and backfill and asks for a reading straight away, like the Dexcom app does. */
    private suspend fun beginSession(c: G7GattClient) {
        if (!c.isConnected) {
            log("Link dropped before the session could open; waiting for the sensor")
            return
        }
        c.setListener(G7Characteristic.CONTROL) { data -> scope.launch { handleControl(c, data) } }
        c.setListener(G7Characteristic.BACKFILL) { data -> scope.launch { handleBackfill(data) } }
        try {
            c.enableNotifications(G7Characteristic.CONTROL)
            c.enableNotifications(G7Characteristic.BACKFILL)
            // The backfill request goes out only after the reading has come back: the sensor does one
            // command at a time, and a request on top of the reading request cost us the reading.
            backfillPending = true
            // Read before the live reading is stored, or it would hide the gap it closes.
            latestAtSessionStart = runCatching { persistenceLayer.getLastGlucoseValue()?.timestamp }.getOrNull()
            send(c, G7Commands.GLUCOSE)
        } catch (e: G7LinkException) {
            log("Could not open the session: ${e.message}")
        }
    }

    private suspend fun send(c: G7GattClient, command: ByteArray) {
        log("send control ${command.toHex()}")
        c.write(G7Characteristic.CONTROL, command, withResponse = true)
    }

    /** Sends [command] and waits for the answer that starts with [answerOpcode]. Null on timeout. */
    private suspend fun ask(c: G7GattClient, command: ByteArray, answerOpcode: Int, timeoutMs: Long = ANSWER_TIMEOUT_MS): ByteArray? {
        val deferred = CompletableDeferred<ByteArray>()
        pendingAnswer = answerOpcode to deferred
        return try {
            send(c, command)
            withTimeoutOrNull(timeoutMs) { deferred.await() }
        } catch (e: G7LinkException) {
            log("Command ${command.toHex()} failed: ${e.message}")
            null
        } finally {
            if (pendingAnswer?.second === deferred) pendingAnswer = null
        }
    }

    private fun handleControl(c: G7GattClient, data: ByteArray) {
        if (data.isEmpty()) return
        lastPacketAt = System.currentTimeMillis()
        log("control ${data.toHex()}")
        val opcode = data[0].toInt() and 0xff
        pendingAnswer?.let { (expected, deferred) -> if (expected == opcode) deferred.complete(data) }
        when (opcode) {
            G7Opcode.GLUCOSE             -> G7GlucoseMessage.parse(data)?.let { handleGlucose(c, it) } ?: log("Reading not understood")
            G7Opcode.EXTENDED_VERSION    -> ExtendedVersionMessage.parse(data)?.let { version ->
                log("Sensor: $version")
                store.update { it.copy(sessionLengthSeconds = version.sessionLengthSeconds, warmupSeconds = version.warmupSeconds) }
            }

            G7Opcode.TRANSMITTER_VERSION -> TransmitterVersionMessage.parse(data)?.let { version ->
                log("Sensor: $version")
                store.update { it.copy(serial = version.serialNumber.toString(), firmware = version.firmwareVersion) }
            }

            G7Opcode.CALIBRATE           -> G7CalibrateRxMessage.parse(data)?.let { answer ->
                log("Calibration ${if (answer.accepted) "accepted" else "refused"} by the sensor (status ${answer.status})")
                store.update { state ->
                    state.copy(
                        calibration = state.calibration?.copy(
                            outcome = if (answer.accepted) G7CalibrationRecord.Outcome.ACCEPTED else G7CalibrationRecord.Outcome.REJECTED,
                            outcomeAt = System.currentTimeMillis(),
                            status = answer.status
                        )
                    )
                }
            }

            G7Opcode.CALIBRATION_BOUNDS  -> G7CalibrationBoundsMessage.parse(data)?.let { bounds ->
                log("Calibration state: $bounds")
                store.update { state ->
                    if (state.calibration?.outcome == G7CalibrationRecord.Outcome.ACCEPTED) state.copy(calibration = state.calibration.copy(processingStatus = bounds.processingStatus.name))
                    else state
                }
                // Taking a calibration in takes a reading or two; keep asking until it is done.
                boundsRequestPending = bounds.processingStatus == G7CalibrationProcessingStatus.IN_PROGRESS
            }

            // The end-of-backfill answer comes after the records, so it also marks the end of them.
            G7Opcode.BACKFILL            -> flushBackfill()
        }
    }

    private fun handleGlucose(c: G7GattClient, message: G7GlucoseMessage) {
        val now = System.currentTimeMillis()
        val stored = store.value.let { it.clockAnchorAt ?: it.activatedAt }
        val activation = G7Lifecycle.clockAnchor(stored, now - message.messageTimestamp * 1000)
        if (stored != null && activation != stored) log("The phone clock moved against the sensor clock by ${(activation - stored) / 1000}s; using the new time")
        activationDate = activation
        val readingAt = activation + message.glucoseTimestamp * 1000
        restartsSinceReading = 0
        gotReadingThisConnection = true
        logCameBack()
        lastReadingReceivedAt = now

        store.update { state ->
            state.copy(
                activatedAt = state.activatedAt ?: activation,
                clockAnchorAt = activation,
                sensorName = state.sensorName ?: c.name,
                pairedAt = state.pairedAt ?: now,
                latestReadingAt = maxOf(readingAt, state.latestReadingAt ?: 0),
                latestGlucose = message.glucose,
                latestAlgorithmState = message.algorithmState.raw,
                latestTrendRate = message.trend,
                lastAuthenticationFailure = null,
                lastAuthenticationFailureAt = null
            )
        }
        retryNotBefore = 0
        _readings.tryEmit(
            listOf(
                G7Reading(
                    timestamp = readingAt,
                    glucose = message.glucose,
                    trend = message.trendType,
                    trendRate = message.trend,
                    algorithmState = message.algorithmState,
                    displayOnly = message.glucoseIsDisplayOnly,
                    fromBackfill = false
                )
            )
        )
        if (followUps?.isActive != true) followUps = scope.launch { runFollowUps(c, readingAt) }
    }

    /** What the connection is used for once the reading is in. One command at a time, each waiting for its answer. */
    private suspend fun runFollowUps(c: G7GattClient, liveReadingAt: Long) {
        val state = store.value
        val activation = activationDate ?: return

        state.calibration?.takeIf { it.outcome == G7CalibrationRecord.Outcome.PENDING }?.let { calibration ->
            val sensorAge = maxOf(0L, (calibration.enteredAt - activation) / 1000)
            log("Sending calibration ${calibration.glucose} mg/dL taken at sensor age ${sensorAge}s")
            val answer = ask(c, G7CalibrateTxMessage(calibration.glucose, sensorAge).data, G7Opcode.CALIBRATE)
            if (answer != null && G7CalibrateRxMessage.parse(answer)?.accepted == true) boundsRequestPending = true
        }
        if (boundsRequestPending && c.isConnected) {
            boundsRequestPending = false
            ask(c, G7Commands.CALIBRATION_BOUNDS, G7Opcode.CALIBRATION_BOUNDS)
        }

        if (backfillPending && c.isConnected) {
            backfillPending = false
            G7Lifecycle.backfillRange(activation, latestAtSessionStart, liveReadingAt)?.let { (start, end) ->
                log("Asking for backfill from ${start}s to ${end}s")
                ask(c, G7Commands.backfill(start, end), G7Opcode.BACKFILL, BACKFILL_TIMEOUT_MS)
                flushBackfill()
            }
        }

        if (!versionAsked && c.isConnected) {
            versionAsked = true
            ask(c, G7Commands.EXTENDED_VERSION, G7Opcode.EXTENDED_VERSION)
            if (c.isConnected) ask(c, G7Commands.TRANSMITTER_VERSION, G7Opcode.TRANSMITTER_VERSION)
        }
    }

    private fun handleBackfill(frame: ByteArray) {
        lastPacketAt = System.currentTimeMillis()
        log("backfill ${frame.toHex()}")
        val records = G7BackfillMessage.parseFrame(frame)
        if (records == null) {
            log("Backfill frame of unexpected length ${frame.size} ignored")
            return
        }
        backfillBuffer.addAll(records)
    }

    /** Hands the collected backfill on. Also on disconnect: the end marker does not always arrive. */
    private fun flushBackfill() {
        if (backfillBuffer.isEmpty()) return
        val records = backfillBuffer
        backfillBuffer = mutableListOf()
        val activation = activationDate ?: store.value.let { it.clockAnchorAt ?: it.activatedAt } ?: run {
            log("Backfill dropped: sensor start unknown")
            return
        }
        // The request already leaves out what we have. This also drops what the sensor sends anyway.
        val alreadyStored = latestAtSessionStart?.let { it + G7Lifecycle.CLOCK_TOLERANCE_MS } ?: Long.MIN_VALUE
        val readings = records.map {
            G7Reading(
                timestamp = activation + it.timestamp * 1000,
                glucose = it.glucose,
                trend = it.trendType,
                trendRate = it.trend,
                algorithmState = it.algorithmState,
                displayOnly = it.glucoseIsDisplayOnly,
                fromBackfill = true
            )
        }.filter { it.timestamp > alreadyStored }
        log("Backfill: ${readings.size} of ${records.size} records are new")
        if (readings.isEmpty()) return
        readings.maxOfOrNull { it.timestamp }?.let { newest ->
            store.update { if (newest > (it.latestReadingAt ?: 0)) it.copy(latestReadingAt = newest) else it }
        }
        _readings.tryEmit(readings)
    }

    // ---------------------------------------------------------------------------------- watchdog

    /**
     * Opens the link again when nothing has come for a long time. The disconnect callback covers the
     * normal case. This covers what the stack never reports: a client it has quietly dropped, or an
     * adapter reset underneath us. Silence is measured from the last packet, not the last stored
     * reading, so a sensor in warmup (whose readings are dropped on purpose) is not torn down.
     */
    private fun startWatchdog() {
        watchdog?.cancel()
        watchdog = scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                if (!running || paused || store.value.address == null) continue
                val now = System.currentTimeMillis()
                val heardFrom = maxOf(lastPacketAt, store.value.latestReadingAt ?: 0, startedAt)
                if (now - heardFrom > SILENCE_BEFORE_RESTART_MS && now - lastRestartAt > SILENCE_BEFORE_RESTART_MS && now >= retryNotBefore) {
                    lastRestartAt = now
                    restartsSinceReading++
                    log("Nothing from the sensor for ${(now - heardFrom) / 60_000} minutes; opening the link again (restart $restartsSinceReading)")
                    if (restartsSinceReading >= RESTARTS_BEFORE_SCAN) scanForPairedSensor() else connectToPairedSensor()
                }
            }
        }
    }

    /**
     * Looks for the paired sensor by scanning and connects directly when it is seen. Used after plain
     * restarts did not help: a direct connection to an advertising sensor is quicker than the stack's
     * background connection.
     */
    private fun scanForPairedSensor() {
        val address = store.value.address ?: return
        closeClient()
        _status.value = G7ConnectionStatus.WAITING
        var found = false
        val started = bluetooth.startScan(
            onResult = { result ->
                if (result.address == address && !found) {
                    found = true
                    scope.launch {
                        bluetooth.stopScan()
                        if (!running || paused) return@launch
                        log("Found the sensor by scanning; connecting")
                        val device = bluetooth.device(address) ?: return@launch
                        client = G7GattClient(context, device, this@G7Session, ::log).also { it.connect(autoConnect = false) }
                    }
                }
            },
            onFailed = { log("Scan: $it") }
        )
        if (!started) {
            connectToPairedSensor()
            return
        }
        scope.launch {
            delay(SCAN_FOR_SENSOR_MS)
            if (!found) {
                bluetooth.stopScan()
                log("The sensor was not seen while scanning; waiting for it in the background")
                connectToPairedSensor()
            }
        }
    }

    // ------------------------------------------------------------------------ Bluetooth on/off

    private var receiverRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON  -> scope.launch {
                    log("Bluetooth switched on")
                    connectToPairedSensor()
                }

                BluetoothAdapter.STATE_OFF -> scope.launch {
                    log("Bluetooth switched off")
                    closeClient()
                    if (running) _status.value = G7ConnectionStatus.BLOCKED
                }
            }
        }
    }

    private fun registerBluetoothReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(context, bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun unregisterBluetoothReceiver() {
        if (!receiverRegistered) return
        runCatching { context.unregisterReceiver(bluetoothReceiver) }
        receiverRegistered = false
    }

    companion object {

        const val REARM_DELAY_MS = 5_000L

        /** After a reading, the sensor is not asked again for this long. Readings are 5 minutes apart. */
        const val QUIET_AFTER_READING_MS = 60_000L
        const val ANSWER_TIMEOUT_MS = 3_000L
        const val BACKFILL_TIMEOUT_MS = 10_000L
        const val WATCHDOG_INTERVAL_MS = 60_000L
        const val SILENCE_BEFORE_RESTART_MS = 20 * 60_000L
        const val RESTARTS_BEFORE_SCAN = 2
        const val SCAN_FOR_SENSOR_MS = 6 * 60_000L

        /** How long a sensor keeps its phone slot for a phone that went quiet. */
        const val REFUSAL_PAUSE_MS = 15 * 60_000L

        // Refusal kinds, stored in the state and turned into text by the UI.
        const val REFUSAL_SLOT_TAKEN = "slot_taken"
        const val REFUSAL_KEY = "key"
        const val REFUSAL_WRONG_CODE = "wrong_code"
        const val REFUSAL_OTHER = "other"
    }
}
