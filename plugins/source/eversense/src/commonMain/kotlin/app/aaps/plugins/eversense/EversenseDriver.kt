package app.aaps.plugins.eversense

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.ble.BleFailure
import app.aaps.core.interfaces.pump.ble.BleLink
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.ConnectOptions
import app.aaps.core.interfaces.pump.ble.DeviceId
import app.aaps.core.interfaces.pump.ble.LinkState
import app.aaps.core.interfaces.pump.ble.WriteType
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.connection.EversenseSession
import app.aaps.plugins.eversense.connection.GuardedStore
import app.aaps.plugins.eversense.connection.closedByTransmitter
import app.aaps.plugins.eversense.connection.reconnectWithoutTimers
import app.aaps.plugins.eversense.enums.CalibrationReadiness
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.exceptions.EversenseWriteException
import app.aaps.plugins.eversense.models.EversenseSecureState
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.packets.Eversense365Communicator
import app.aaps.plugins.eversense.packets.EversenseE3Communicator
import app.aaps.plugins.eversense.packets.e3.GetCalibrationReadinessPacket
import app.aaps.plugins.eversense.packets.e3.GetSignalStrengthRawPacket
import app.aaps.plugins.eversense.packets.e365.GetSignalStrengthPacket
import app.aaps.plugins.eversense.packets.e365.SetBloodGlucosePointPacket365
import app.aaps.plugins.eversense.pairing.EversenseModel
import app.aaps.plugins.eversense.protocol.PacketTime
import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.eversense.storage.edit
import app.aaps.plugins.eversense.util.EversenseCrypto365Util
import app.aaps.plugins.eversense.util.StorageKeys
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The Eversense transmitter driver: it keeps one Bluetooth link to the paired transmitter, signs in, syncs, and
 * reconnects after a loss, on Android and iOS through the app's [BleCentral].
 *
 * All its state is changed on one thread ([dispatcher]), so it needs no locks. A start loads everything from
 * [preferences], a stop drops everything it holds in memory.
 *
 * The connection rules are those of the first version: a connection attempt has [ATTEMPT_DEADLINE] to become ready; a
 * lost link is tried again after 5 s, doubling up to a minute; an E3 that closed the link itself (lifted off the
 * sensor) is tried after 30 s, and three such closes in a row tell the user; a 365 that closed it (its usual cycle) is
 * waited for in the background; a link with no data for [DATA_WATCHDOG] is replaced.
 */
@SingleIn(AppScope::class)
class EversenseDriver internal constructor(
    private val central: BleCentral,
    val preferences: EversenseStore,
    val aapsLogger: AAPSLogger,
    private val receiverStatusStore: ReceiverStatusStore,
    private val dispatcher: CoroutineDispatcher,
    /** The platform's choice ([reconnectWithoutTimers]); a test sets it to run the iOS rules on the JVM. */
    private val waitInBackgroundAfterLoss: Boolean = reconnectWithoutTimers
) {

    @Inject
    constructor(central: BleCentral, preferences: EversenseStore, aapsLogger: AAPSLogger, receiverStatusStore: ReceiverStatusStore) :
        this(central, preferences, aapsLogger, receiverStatusStore, Dispatchers.Default.limitedParallelism(1))

    enum class ConnectionState { STOPPED, PAUSED, DISCONNECTED, CONNECTING, READY }

    private val _connectionState = MutableStateFlow(ConnectionState.STOPPED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val security = MutableStateFlow<EversenseSecurityType?>(null)
    private val watchers = MutableStateFlow<List<EversenseWatcher>>(emptyList())

    // Changed only on [dispatcher]
    private var scope: CoroutineScope? = null
    private var loop: Job? = null
    private var session: EversenseSession? = null
    private var linkEnd: CompletableDeferred<LinkEnd>? = null
    private var attempts = 0
    private var shortcutFailures = 0
    private var fullAuthFailures = 0
    private var placementFailures = 0
    private var lastData = 0L

    /** Read by the cloud upload from its own thread */
    @Volatile private var wanted = false
    @Volatile private var cloudGeneration = 0

    /** The stored login, loaded by [start] */
    internal var username: String = ""
        private set
    internal var password: String = ""
        private set

    /** The placement guide is open: the transmitter is kept in diagnostic mode, also after a reconnect. */
    @Volatile var isPositioningMode: Boolean = false
        private set

    /**
     * When the placement guide last closed. The transmitter pushes an alarm code with no meaning (UNKNOWN) 1-2 s after
     * diagnostic mode ends; the plugin drops that one push within a short time after this, and no other.
     */
    @Volatile var lastPositioningModeExitAt: Long = 0L
        private set

    fun addWatcher(watcher: EversenseWatcher) = watchers.update { if (watcher in it) it else it + watcher }
    fun removeWatcher(watcher: EversenseWatcher) = watchers.update { it - watcher }

    fun isConnected(): Boolean = _connectionState.value == ConnectionState.READY
    fun is365(): Boolean = security.value == EversenseSecurityType.SecureV2

    /** A 365, from the link when there is one, else from the model chosen at sign-in. */
    fun transmitterIs365(): Boolean = security.value?.let { it == EversenseSecurityType.SecureV2 } ?: secureState().model.startsWith("E365")

    /** Days the sensor lasts: a 365 a year, an E3 180 days. Null while neither a link nor a sign-in has told the model. */
    fun sensorLifetimeDays(): Int? =
        if (security.value == null && secureState().model.isEmpty()) null
        else if (transmitterIs365()) LIFETIME_365_DAYS else LIFETIME_E3_DAYS

    fun hasInternet(): Boolean = receiverStatusStore.isConnected

    fun getCurrentState(): EversenseState = JSON.decodeFromString<EversenseState>(preferences.getString(StorageKeys.STATE, null) ?: "{}")

    /** The store for the cloud upload: null while stopped or disconnected, and closed for the upload when either happens. */
    fun cloudStore(): EversenseStore? {
        if (!wanted) return null
        val generation = cloudGeneration
        return GuardedStore(preferences) { wanted && generation == cloudGeneration }
    }

    /**
     * Reads the stored login and connects when a transmitter is paired too. Unpaired, it waits: [pair] connects then. It
     * does not try every minute for a transmitter it does not have.
     */
    suspend fun start() = withContext(dispatcher) {
        if (scope != null) return@withContext
        scope = CoroutineScope(SupervisorJob() + dispatcher)
        val state = secureState()
        username = state.username
        password = state.password
        wanted = mayLink(state) && preferences.getString(StorageKeys.REMOTE_DEVICE_KEY, null) != null
        if (wanted) restartLoop() else _connectionState.value = ConnectionState.PAUSED
    }

    /**
     * Closes the link and drops what this object holds in memory: the retries and timers, the failure counters and the
     * login. The transmitter, its keys and the stored login stay in [preferences]; the next start reads them again.
     * Returns when the link is closed.
     */
    suspend fun stop() {
        val old = withContext(dispatcher) {
            val running = scope
            scope = null
            loop = null
            wanted = false
            cloudGeneration++
            isPositioningMode = false
            lastPositioningModeExitAt = 0L
            attempts = 0
            shortcutFailures = 0
            fullAuthFailures = 0
            placementFailures = 0
            username = ""
            password = ""
            _connectionState.value = ConnectionState.STOPPED
            // Cancelled here, on the driver's thread: the loop cannot run again between the state change and this
            running?.cancel()
            running
        }
        old?.coroutineContext?.job?.join()
    }

    /** Bluetooth is on again: Android can leave out the disconnect while it was off, so connect now. */
    suspend fun onBluetoothAvailable() = withContext(dispatcher) {
        if (scope != null && wanted) restartLoop()
    }

    /** Connects to the paired transmitter now, unless a connection is up or on its way. A new one is [pair]. */
    suspend fun connect(): Boolean = withContext(dispatcher) {
        if (scope == null) return@withContext false
        wanted = true
        when (_connectionState.value) {
            ConnectionState.READY      -> true
            ConnectionState.CONNECTING -> false
            else                       -> canOpen().also { restartLoop() }
        }
    }

    /** Closes the link and stays disconnected until [connect] or [pair]. */
    suspend fun disconnect() = withContext(dispatcher) {
        wanted = false
        cloudGeneration++
        val old = loop
        loop = null
        if (scope != null) _connectionState.value = ConnectionState.PAUSED
        old?.cancelAndJoin()
    }

    /**
     * Pairs the transmitter at [address]: stores it with its [name], makes the next connection a full registration (a
     * 365 registers this phone with the new transmitter), and connects.
     *
     * @return false when the link cannot be opened (not started, Bluetooth off, no login)
     */
    suspend fun pair(address: String, name: String): Boolean = withContext(dispatcher) {
        preferences.edit(commit = true) {
            putString(StorageKeys.REMOTE_DEVICE_KEY, address)
            putString(StorageKeys.REMOTE_DEVICE_NAME, name)
            // Paired again: the hint to forget the old bond is done
            remove(StorageKeys.BOND_LEFT_NAME)
        }
        EversenseCrypto365Util(preferences, aapsLogger).disallowUseShortcut()
        placementFailures = 0
        if (scope == null) return@withContext false
        wanted = true
        canOpen().also { restartLoop() }
    }

    /** Keeps the model chosen at sign-in with the login, to show it; the connection does not use it. */
    fun rememberModel(model: String) {
        val state = secureState()
        if (state.model == model) return
        state.model = model
        preferences.edit(commit = true) { putString(StorageKeys.SECURE_STATE, JSON.encodeToString(state)) }
    }

    /** Stores a new login (and region) and connects with it. Old work with the old login is closed first. */
    suspend fun setCredentials(username: String, password: String, europeanRegion: Boolean) {
        val state = secureState()
        val changed = state.username != username || state.password != password || state.isEuropeanRegion != europeanRegion
        if (!changed && this.username == username && this.password == password) return
        disconnect()
        withContext(dispatcher) {
            this@EversenseDriver.username = username
            this@EversenseDriver.password = password
            shortcutFailures = 0
            fullAuthFailures = 0
        }
        if (changed) {
            state.username = username
            state.password = password
            state.isEuropeanRegion = europeanRegion
            state.canUseShortcut = false
            preferences.edit(commit = true) {
                putString(StorageKeys.SECURE_STATE, JSON.encodeToString(state))
                remove(StorageKeys.ACCESS_TOKEN)
                remove(StorageKeys.ACCESS_TOKEN_EXPIRY)
                remove(StorageKeys.CLOUD_QUEUE)
                // A new login has not been refused yet
                remove(StorageKeys.LOGIN_REFUSED)
            }
        }
        if (username.isNotEmpty() && password.isNotEmpty() && preferences.getString(StorageKeys.REMOTE_DEVICE_KEY, null) != null) connect()
    }

    /** Forgets the login with its token, its cloud queue and the model; the link closes. */
    suspend fun clearCredentials() {
        disconnect()
        withContext(dispatcher) {
            username = ""
            password = ""
        }
        val state = secureState()
        state.username = ""
        state.password = ""
        state.model = ""
        state.canUseShortcut = false
        preferences.edit(commit = true) {
            putString(StorageKeys.SECURE_STATE, JSON.encodeToString(state))
            remove(StorageKeys.ACCESS_TOKEN)
            remove(StorageKeys.ACCESS_TOKEN_EXPIRY)
            remove(StorageKeys.CLOUD_QUEUE)
            remove(StorageKeys.LOGIN_REFUSED)
        }
    }

    /** Uses an E3 without a login: a stored login is forgotten (with its cloud queue), and the link opens without one. */
    suspend fun useWithoutLogin() {
        clearCredentials()
        rememberModel(EversenseModel.E3.name)
        if (preferences.getString(StorageKeys.REMOTE_DEVICE_KEY, null) != null) connect()
    }

    /** Unpairs: closes the link and forgets the transmitter, so the next pairing is a full registration. The login stays. */
    suspend fun clearStoredDevice() {
        val address = preferences.getString(StorageKeys.REMOTE_DEVICE_KEY, null)
        val name = preferences.getString(StorageKeys.REMOTE_DEVICE_NAME, null) ?: address
        disconnect()
        withContext(dispatcher) { placementFailures = 0 }
        preferences.edit(commit = true) {
            remove(StorageKeys.REMOTE_DEVICE_KEY)
            remove(StorageKeys.REMOTE_DEVICE_NAME)
        }
        EversenseCrypto365Util(preferences, aapsLogger).disallowUseShortcut()
        aapsLogger.info(LTag.BGSOURCE, "Cleared stored device address")
        // The phone's bond goes too, after the link is closed: a bond the phone keeps while the transmitter has
        // forgotten it can stop the next pairing on this phone. Only Android lets an app remove one; on iOS, or when
        // the removal fails, the user is told to forget the transmitter in the phone's settings (BOND_LEFT_NAME).
        if (address == null) return
        val removed = central.bonding?.removeBond(DeviceId(address)) is BleResult.Ok
        aapsLogger.info(LTag.BGSOURCE, "Bond of the transmitter removed: $removed")
        preferences.edit(commit = true) {
            if (removed) remove(StorageKeys.BOND_LEFT_NAME) else putString(StorageKeys.BOND_LEFT_NAME, name)
        }
    }

    // ── Work on the ready link ──

    /** Sends a finger-stick calibration. False when not connected or the transmitter did not take it. */
    suspend fun sendCalibration(glucoseMgDl: Int, timestampMs: Long = PacketTime.nowMillis()): Boolean = try {
        withReadySession(OPERATION_TIMEOUT) {
            if (is365) {
                writePacket<SetBloodGlucosePointPacket365.Response>(SetBloodGlucosePointPacket365(glucoseMgDl, timestampMs))
                aapsLogger.info(LTag.BGSOURCE, "365 calibration sent: $glucoseMgDl mg/dL")
            } else {
                EversenseE3Communicator.sendCalibration(this, glucoseMgDl)
            }
            val state = getCurrentState()
            state.lastCalibrationDate = timestampMs
            state.nextCalibrationDate = timestampMs + 24 * 60 * 60 * 1000L
            state.calibrationReadiness = CalibrationReadiness.WAITING_POST_CALIBRATION
            preferences.edit(commit = true) { putString(StorageKeys.STATE, JSON.encodeToString(state)) }
            aapsLogger.debug(LTag.BGSOURCE) { "Updated calibration state: lastCalibrationDate=$timestampMs, readiness=WAITING_POST_CALIBRATION" }
            // An E3 says when it takes the next one; read it after this task
            if (!is365) launchTask {
                try {
                    val readiness = writePacket<GetCalibrationReadinessPacket.Response>(GetCalibrationReadinessPacket())
                    val current = getCurrentState()
                    current.calibrationReadiness = readiness.readiness
                    preferences.edit(commit = true) { putString(StorageKeys.STATE, JSON.encodeToString(current)) }
                    aapsLogger.debug(LTag.BGSOURCE) { "Post-calibration readiness re-read: ${readiness.readiness}" }
                    watchers.value.forEach { it.onStateChanged(current) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    aapsLogger.warn(LTag.BGSOURCE, "Post-calibration readiness re-read failed (non-fatal): $e")
                }
            }
        }
        true
    } catch (e: EversenseWriteException) {
        aapsLogger.error(LTag.BGSOURCE, "Failed to send calibration: $e")
        false
    }

    /** Reads everything from the transmitter now, also when the last sync is recent ([force]). */
    suspend fun triggerFullSync(force: Boolean = false) = withContext(dispatcher) {
        val ready = session?.takeIf { isConnected() } ?: run {
            aapsLogger.error(LTag.BGSOURCE, "Cannot sync — not connected")
            return@withContext
        }
        ready.launchTask {
            aapsLogger.info(LTag.BGSOURCE, "Triggering full sync on user request")
            if (is365) {
                Eversense365Communicator.fullSync(this, preferences, watchers.value, force)
                Eversense365Communicator.readGlucose(this, preferences, watchers.value)
            } else {
                EversenseE3Communicator.fullSync(this, preferences, watchers.value, force)
                EversenseE3Communicator.readGlucose(this, preferences, watchers.value)
            }
        }
    }

    /** The transmitter's signal from the sensor, 0-100 %, or null when it cannot be read now. Stored in the state. */
    suspend fun readSignalStrength(): Int? = try {
        withReadySession(OPERATION_TIMEOUT) {
            val signal = if (is365) writePacket<GetSignalStrengthPacket.Response>(GetSignalStrengthPacket()).signalStrength
            else writePacket<GetSignalStrengthRawPacket.Response>(GetSignalStrengthRawPacket()).also {
                aapsLogger.debug(LTag.BGSOURCE) { "E3 signal raw: ${it.rawValue} -> ${it.signalStrength}%" }
            }.signalStrength
            val state = getCurrentState()
            state.sensorSignalStrength = signal
            preferences.edit { putString(StorageKeys.STATE, JSON.encodeToString(state)) }
            aapsLogger.debug(LTag.BGSOURCE) { "Signal strength: $signal%" }
            watchers.value.forEach { it.onStateChanged(state) }
            signal
        }
    } catch (e: EversenseWriteException) {
        aapsLogger.warn(LTag.BGSOURCE, "readSignalStrength failed: $e")
        null
    }

    /** Puts the transmitter in diagnostic mode for the placement guide, which reads the signal often. */
    suspend fun enterPositioningMode() {
        isPositioningMode = true
        setDiagnosticMode(true)
        aapsLogger.info(LTag.BGSOURCE, "Diagnostic Mode ENABLED: Positioning active")
    }

    suspend fun exitPositioningMode() {
        isPositioningMode = false
        lastPositioningModeExitAt = PacketTime.nowMillis()
        setDiagnosticMode(false)
        aapsLogger.info(LTag.BGSOURCE, "Diagnostic Mode DISABLED: Power saving active")
    }

    private suspend fun setDiagnosticMode(enabled: Boolean) {
        try {
            withReadySession(OPERATION_TIMEOUT) { setDiagnosticMode(enabled) }
        } catch (e: EversenseWriteException) {
            aapsLogger.warn(LTag.BGSOURCE, "setDiagnosticMode failed: $e")
        }
    }

    /** Runs [block] on the ready link, in its turn with the link's other work. */
    private suspend fun <T> withReadySession(timeout: Duration, block: suspend EversenseSession.() -> T): T {
        val ready = withContext(dispatcher) { session?.takeIf { isConnected() } } ?: throw EversenseWriteException("Transmitter is not connected")
        return try {
            withTimeout(timeout) { withContext(dispatcher) { ready.perform(block) } }
        } catch (e: TimeoutCancellationException) {
            throw EversenseWriteException("No answer in $timeout")
        }
    }

    // ── The connection ──

    /** How one link ended, which decides the wait before the next one. */
    private sealed interface LinkEnd {

        /** Nothing to connect: no transmitter, no login, Bluetooth off. */
        data object CannotOpen : LinkEnd
        data class Lost(val failure: BleFailure, val byTransmitter: Boolean, val is365: Boolean) : LinkEnd
        data class Closed(val reason: String) : LinkEnd

        /** Not ready within [ATTEMPT_DEADLINE]. */
        data object TimedOut : LinkEnd

        /** No data for [DATA_WATCHDOG]. */
        data object Stale : LinkEnd
    }

    /** Whether a link may be opened now. */
    private fun canOpen(): Boolean =
        mayLink() && central.adapterState.value == AdapterState.ON && preferences.getString(StorageKeys.REMOTE_DEVICE_KEY, null) != null

    /** With a login, or for an E3 used without one. A 365 registers this phone with the Eversense server, and that needs the login. */
    private fun mayLink(state: EversenseSecureState = secureState()): Boolean =
        (username.isNotEmpty() && password.isNotEmpty()) || (state.username.isEmpty() && state.model == EversenseModel.E3.name)

    /**
     * Ends the running link (and its waits) and starts connecting at once. Call on [dispatcher]. A cloud upload in
     * flight goes on: only a stop, a disconnect or a new login ends it.
     */
    private fun restartLoop() {
        val running = scope ?: return
        val old = loop
        _connectionState.value = ConnectionState.CONNECTING
        loop = running.launch {
            old?.cancelAndJoin()
            var waitForTransmitter = false
            while (true) {
                _connectionState.value = ConnectionState.CONNECTING
                val end = runLink(waitForTransmitter)
                _connectionState.value = ConnectionState.DISCONNECTED
                security.value = null
                aapsLogger.info(LTag.BGSOURCE, "Eversense link ended: $end")
                // On iOS a link lost by radio (or gone silent) waits for the transmitter in a pending connect: a timer
                // would not run while the app is in the background. A link the session closed (no internet for the
                // registration, a failed sign-in) waits on a timer there too, or it would fail again at once.
                val waitInBackground = waitInBackgroundAfterLoss && (end is LinkEnd.Lost || end == LinkEnd.Stale)
                waitForTransmitter = waitInBackground || (end is LinkEnd.Lost && end.byTransmitter && end.is365)
                if (!waitInBackground) delay(
                    when (end) {
                        LinkEnd.CannotOpen -> 60_000L
                        LinkEnd.Stale      -> 0L
                        is LinkEnd.Lost    -> if (end.byTransmitter && !end.is365) 30_000L else backoff()
                        else               -> backoff()
                    }
                )
            }
        }
    }

    private fun backoff(): Long = minOf(5_000L * (1L shl minOf(attempts++, 4)), 60_000L)

    /**
     * One link, from the connect to its end. [waitForTransmitter]: the connect waits until the transmitter is in range
     * (Android `autoConnect`), with no time limit on iOS; else it is a direct connect of [CONNECT_TIMEOUT].
     */
    private suspend fun runLink(waitForTransmitter: Boolean): LinkEnd {
        if (!canOpen()) return LinkEnd.CannotOpen
        val address = preferences.getString(StorageKeys.REMOTE_DEVICE_KEY, null) ?: return LinkEnd.CannotOpen
        val attemptStart = PacketTime.nowMillis()
        val options = ConnectOptions(
            timeout = when {
                !waitForTransmitter    -> CONNECT_TIMEOUT
                waitInBackgroundAfterLoss -> Duration.INFINITE
                else                   -> ATTEMPT_DEADLINE
            },
            services = listOf(EversenseSession.SERVICE),
            androidAutoConnect = waitForTransmitter
        )
        val link = when (val connected = central.connect(DeviceId(address), options)) {
            is BleResult.Ok     -> connected.value
            is BleResult.Failed -> return lost(connected.failure, is365 = false)
        }
        // A connect that waited for the transmitter can end long after it began: the sign-in gets its full time from
        // the moment the link is up
        val startedAt = if (waitForTransmitter) PacketTime.nowMillis() else attemptStart
        try {
            return coroutineScope {
                val end = useLink(link, startedAt)
                // The link's own work (receiving, its timers, a sync) ends with it
                coroutineContext.cancelChildren()
                end
            }
        } finally {
            session = null
            linkEnd = null
            withContext(NonCancellable) { link.close() }
            watchers.value.forEach { it.onConnectionChanged(false) }
        }
    }

    private suspend fun CoroutineScope.useLink(link: BleLink, startedAt: Long): LinkEnd {
        val end = CompletableDeferred<LinkEnd>()
        linkEnd = end
        watchers.value.forEach { it.onConnectionChanged(true) }
        launch {
            val state = link.state.first { it != LinkState.Up }
            end.complete(if (state is LinkState.Lost) lost(state.failure, security.value == EversenseSecurityType.SecureV2) else LinkEnd.Closed("closed"))
        }
        // A ready link that brings no reading or keep alive for this long is replaced
        launch {
            while (true) {
                delay(1.minutes)
                if (isConnected() && PacketTime.nowMillis() - lastData > DATA_WATCHDOG.inWholeMilliseconds) end.complete(LinkEnd.Stale)
            }
        }
        // The deadline of this attempt: ready (signed in and synced) in time, or tried again
        launch {
            delay(ATTEMPT_DEADLINE - (PacketTime.nowMillis() - startedAt).milliseconds)
            if (!isConnected()) end.complete(LinkEnd.TimedOut)
        }

        val (request, response, type) = when {
            link.services.has(EversenseSession.E3_REQUEST) && link.services.has(EversenseSession.E3_RESPONSE) ->
                Triple(EversenseSession.E3_REQUEST, EversenseSession.E3_RESPONSE, EversenseSecurityType.None)
            link.services.has(EversenseSession.V2_REQUEST) && link.services.has(EversenseSession.V2_RESPONSE) ->
                Triple(EversenseSession.V2_REQUEST, EversenseSession.V2_RESPONSE, EversenseSecurityType.SecureV2)
            else                                                                                              -> {
                aapsLogger.error(LTag.BGSOURCE, "No Eversense request/response characteristic found -> disconnecting")
                return LinkEnd.Closed("not an Eversense transmitter")
            }
        }
        aapsLogger.info(LTag.BGSOURCE, if (type == EversenseSecurityType.SecureV2) "Connected to Eversense 365!" else "Connected to Eversense E3!")
        security.value = type
        val mtu = link.requestMtu(512)
        val payloadSize = minOf(mtu - 3, link.maxWriteLength(WriteType.WITH_RESPONSE), link.maxWriteLength(WriteType.WITHOUT_RESPONSE)).coerceAtLeast(20)
        aapsLogger.debug(LTag.BGSOURCE) { "New payload size: $payloadSize" }

        val linkScope = this
        val current = EversenseSession(link, request, response, type, payloadSize, preferences, aapsLogger, linkScope, sessionHost(end))
        session = current
        launch { current.start() }

        return end.await()
    }

    private fun lost(failure: BleFailure, is365: Boolean): LinkEnd {
        val byTransmitter = closedByTransmitter(failure)
        // An E3 that closes the link three times in a row is not over its sensor
        if (byTransmitter && !is365) {
            if (++placementFailures >= 3) watchers.value.forEach { it.onTransmitterNotPlaced() }
        } else placementFailures = 0
        return LinkEnd.Lost(failure, byTransmitter, is365)
    }

    private fun sessionHost(end: CompletableDeferred<LinkEnd>) = object : EversenseSession.Host {
        override val username: String get() = this@EversenseDriver.username
        override val password: String get() = this@EversenseDriver.password
        override val positioningMode: Boolean get() = isPositioningMode
        override fun hasInternet(): Boolean = this@EversenseDriver.hasInternet()
        override fun watchers(): List<EversenseWatcher> = watchers.value

        override fun authenticationSucceeded() {
            shortcutFailures = 0
            fullAuthFailures = 0
        }

        override fun authenticationFailed(shortcut: Boolean) {
            if (shortcut) {
                if (!hasInternet()) return
                if (++shortcutFailures >= 3) {
                    shortcutFailures = 0
                    EversenseCrypto365Util(preferences, aapsLogger).disallowUseShortcut()
                }
            } else if (++fullAuthFailures >= 3) {
                fullAuthFailures = 0
                EversenseCrypto365Util(preferences, aapsLogger).allowUseShortcut()
            }
        }

        override fun ready() {
            if (end.isCompleted) return
            attempts = 0
            placementFailures = 0
            lastData = PacketTime.nowMillis()
            _connectionState.value = ConnectionState.READY
        }

        override fun dataReceived() {
            lastData = PacketTime.nowMillis()
        }

        override fun closeLink(reason: String) {
            end.complete(LinkEnd.Closed(reason))
        }
    }

    private fun secureState(): EversenseSecureState =
        JSON.decodeFromString<EversenseSecureState>(preferences.getString(StorageKeys.SECURE_STATE, null) ?: "{}")

    companion object {

        private val JSON = Json { ignoreUnknownKeys = true }

        /** From the start of a connection attempt to a signed-in, synced link */
        val ATTEMPT_DEADLINE = 180.seconds

        /** A direct connect, as Android's own limit for one */
        val CONNECT_TIMEOUT = 30.seconds

        /** A ready link with no reading or keep alive for this long is replaced */
        val DATA_WATCHDOG = 7.minutes

        /** A calibration, a signal read or a mode change, with the wait for its turn on the link */
        val OPERATION_TIMEOUT = 20.seconds

        /** How long a sensor lasts, in days */
        const val LIFETIME_365_DAYS = 365
        const val LIFETIME_E3_DAYS = 180
    }
}
