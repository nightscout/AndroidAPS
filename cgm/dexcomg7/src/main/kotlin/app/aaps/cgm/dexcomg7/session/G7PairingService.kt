package app.aaps.cgm.dexcomg7.session

import android.content.Context
import app.aaps.cgm.dexcomg7.ble.G7Bluetooth
import app.aaps.cgm.dexcomg7.ble.G7GattClient
import app.aaps.cgm.dexcomg7.ble.G7ScanResult
import app.aaps.cgm.dexcomg7.data.G7CommLog
import app.aaps.cgm.dexcomg7.data.G7State
import app.aaps.cgm.dexcomg7.data.G7StateStore
import app.aaps.cgm.dexcomg7.protocol.G7AuthException
import app.aaps.cgm.dexcomg7.protocol.G7Authenticator
import app.aaps.cgm.dexcomg7.protocol.G7LinkException
import app.aaps.cgm.dexcomg7.protocol.G7PairingPlanner
import app.aaps.cgm.dexcomg7.protocol.G7SensorModel
import app.aaps.cgm.dexcomg7.protocol.toHex
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Progress of a pairing run, for the wizard. */
sealed class G7PairingState {

    data object Idle : G7PairingState()

    /** Looking for sensors. [candidates] names the ones found so far. */
    data class Scanning(val candidates: List<String>, val startedAt: Long) : G7PairingState()

    /** Running the handshake with [candidate]; [attempt] starts at 1. */
    data class Authenticating(val candidate: String, val attempt: Int, val startedAt: Long) : G7PairingState()

    data class Succeeded(val sensorName: String) : G7PairingState()

    data class Failed(val reason: Reason, val details: List<String> = emptyList()) : G7PairingState()

    enum class Reason { INVALID_CODE, BLUETOOTH_UNAVAILABLE, NO_SENSOR_FOUND, ALL_CANDIDATES_FAILED }

    val isFinished: Boolean get() = this is Succeeded || this is Failed
}

/**
 * Pairs with a sensor: scan, try each likely sensor in turn, run the handshake, keep the key.
 *
 * Unlike Trio, which connects to several sensors at once, this tries one sensor at a time. Android
 * handles a single GATT client far more reliably, and the planner already decides the order.
 *
 * While a run is active the paired sensor (if any) is let go. On success the authenticated link goes
 * straight to [G7Session], so the first reading comes at once.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@Inject
class G7PairingService(
    private val context: Context,
    private val bluetooth: G7Bluetooth,
    private val store: G7StateStore,
    private val session: G7Session,
    private val commLog: G7CommLog
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))

    private val _state = MutableStateFlow<G7PairingState>(G7PairingState.Idle)
    val state: StateFlow<G7PairingState> = _state.asStateFlow()

    private var planner = G7PairingPlanner()
    private var pairingCode = ""
    private var expectedSerial: String? = null
    private var excludedAddress: String? = null
    private var runJob: Job? = null
    private var attemptJob: Job? = null
    private var scanDeadline: Job? = null
    private var client: G7GattClient? = null
    private var scanStartedAt = 0L
    private var active = false

    private fun log(text: String) = commLog.add(text)

    /**
     * Starts pairing with [code]. [serial] is the package serial when the code came from the barcode;
     * sensors that cannot have that serial are then skipped.
     */
    fun start(code: String, serial: String?) {
        runJob = scope.launch {
            cancelRun()
            val trimmed = code.trim()
            if (!isValidPairingCode(trimmed)) {
                _state.value = G7PairingState.Failed(G7PairingState.Reason.INVALID_CODE)
                return@launch
            }
            bluetooth.blockedReason()?.let {
                log("Pairing not possible: $it")
                _state.value = G7PairingState.Failed(G7PairingState.Reason.BLUETOOTH_UNAVAILABLE, listOf(it))
                return@launch
            }
            session.pauseForPairing()
            pairingCode = trimmed
            expectedSerial = serial?.takeIf { it.isNotBlank() }
            // Pairing a new sensor: never try the one we already have. Pairing the same sensor again with
            // its own code (a lost key): that one is the target.
            val current = store.value
            excludedAddress = if (current.pairingCode != trimmed) current.address else null
            // With a scanned serial a mismatch means a wrong code. With a typed code, it may be a neighbour's sensor.
            planner = G7PairingPlanner(keepScanningWhenExhausted = expectedSerial == null)
            active = true
            scanStartedAt = System.currentTimeMillis()
            _state.value = G7PairingState.Scanning(emptyList(), scanStartedAt)
            log("Pairing: scanning for sensors" + (expectedSerial?.let { " (looking for serial $it)" } ?: ""))
            val started = bluetooth.startScan(
                onResult = { result -> scope.launch { handleScanResult(result) } },
                onFailed = { reason -> scope.launch { log("Pairing scan: $reason") } }
            )
            if (!started) {
                fail(G7PairingState.Failed(G7PairingState.Reason.BLUETOOTH_UNAVAILABLE))
                return@launch
            }
            scanDeadline = scope.launch {
                // A sensor used by another phone in the last ~15 minutes only advertises briefly around
                // each reading until that lease ends, so the wait must be longer than the lease.
                delay(SCAN_TIMEOUT_MS)
                if (active && attemptJob?.isActive != true) {
                    val giveUp = planner.exhaustion()
                    fail(G7PairingState.Failed(toReason(giveUp.reason), giveUp.details))
                }
            }
        }
    }

    /** Stops a run. The previously paired sensor, if any, is picked up again. */
    fun cancel() {
        scope.launch {
            val wasActive = active
            cancelRun()
            _state.value = G7PairingState.Idle
            if (wasActive) session.resume()
        }
    }

    /** Back to idle after a finished run, for the next time the wizard opens. */
    fun reset() {
        scope.launch { if (!active) _state.value = G7PairingState.Idle }
    }

    private fun cancelRun() {
        active = false
        scanDeadline?.cancel()
        attemptJob?.cancel()
        bluetooth.stopScan()
        client?.close()
        client = null
    }

    private fun fail(failed: G7PairingState.Failed) {
        log("Pairing failed: ${failed.reason} ${failed.details.joinToString("; ")}")
        cancelRun()
        _state.value = failed
        session.resume()
    }

    private fun toReason(reason: G7PairingPlanner.Reason) = when (reason) {
        G7PairingPlanner.Reason.NO_SENSOR_FOUND       -> G7PairingState.Reason.NO_SENSOR_FOUND
        G7PairingPlanner.Reason.ALL_CANDIDATES_FAILED -> G7PairingState.Reason.ALL_CANDIDATES_FAILED
    }

    private fun handleScanResult(result: G7ScanResult) {
        if (!active) return
        val advertisement = result.advertisement
        if (!advertisement.isSupportedSensor) {
            if (advertisement.name.startsWith(G7SensorModel.STELO_PREFIX)) log("Skipping ${advertisement.name}: Stelo is not supported")
            return
        }
        if (result.address == excludedAddress) return
        expectedSerial?.let { serial -> if (!advertisement.couldHaveSerial(serial)) return }
        val rssi = if (result.rssi < 0) result.rssi else G7PairingPlanner.UNKNOWN_RSSI
        val held = advertisement.isSlotHeld()
        if (planner.addCandidate(result.address, advertisement.name, held ?: false, rssi)) {
            log(if (held == true) "Found ${advertisement.name}; another phone used it recently, so trying others first" else "Found ${advertisement.name}")
            if (_state.value is G7PairingState.Scanning) _state.value = G7PairingState.Scanning(planner.candidates.map { it.name }, scanStartedAt)
        } else {
            planner.updateSlot(result.address, held, rssi)
        }
        if (attemptJob?.isActive != true) tryCurrentCandidate()
    }

    private fun tryCurrentCandidate() {
        if (!active || attemptJob?.isActive == true) return
        val candidate = planner.currentCandidate ?: return
        val device = bluetooth.device(candidate.id) ?: run {
            handleAction(planner.abandonCurrentCandidate("not a valid address"))
            return
        }
        val attempt = planner.nextAttemptNumber
        _state.value = G7PairingState.Authenticating(candidate.name, attempt, System.currentTimeMillis())
        log("Trying ${candidate.name}, attempt $attempt")
        attemptJob = scope.launch {
            val ready = CompletableDeferred<Boolean>()
            val candidateClient = G7GattClient(context, device, object : G7GattClient.Events {
                override fun onReady(client: G7GattClient) {
                    ready.complete(true)
                }

                override fun onDisconnected(client: G7GattClient, status: Int) {
                    ready.complete(false)
                }
            }, ::log)
            client = candidateClient
            candidateClient.connect(autoConnect = false)
            val connected = withTimeoutOrNull(CANDIDATE_TIMEOUT_MS) { ready.await() } == true
            if (!connected) {
                log("${candidate.name} did not connect in time")
                finishAttempt(candidateClient, planner.recordFailure())
                return@launch
            }
            if (!candidateClient.hasCgmService()) {
                finishAttempt(candidateClient, planner.abandonCurrentCandidate("no Dexcom CGM service"))
                return@launch
            }
            val authenticator = G7Authenticator(pairingCode, null, G7Authenticator.PAIRING_STEP_TIMEOUT_MS, log = ::log)
            try {
                val result = withTimeoutOrNull(AUTHENTICATION_TIMEOUT_MS) { authenticator.authenticate(candidateClient) }
                if (result == null) {
                    log("${candidate.name} attempt $attempt took too long")
                    finishAttempt(candidateClient, planner.recordFailure())
                    return@launch
                }
                succeed(candidateClient, candidate, result.sharedKey)
            } catch (e: G7AuthException) {
                log("${candidate.name} attempt $attempt failed: ${e.message}")
                val action = when (e) {
                    // Final for this sensor, and trying again invites the lockout.
                    is G7AuthException.Rejected          -> planner.abandonCurrentCandidate(e.message ?: "refused")
                    // Proof that this sensor does not belong to the code.
                    is G7AuthException.ChallengeMismatch -> planner.abandonCurrentCandidate("does not match the code")
                    else                                 -> planner.recordFailure()
                }
                finishAttempt(candidateClient, action)
            } catch (e: G7LinkException) {
                log("${candidate.name} attempt $attempt: link problem: ${e.message}")
                if (e.insufficientAuthentication) {
                    log("The phone holds an old Bluetooth bond with ${candidate.name}; removing it")
                    candidateClient.removeBond()
                }
                finishAttempt(candidateClient, planner.recordFailure())
            }
        }
    }

    private suspend fun finishAttempt(candidateClient: G7GattClient, action: G7PairingPlanner.Action) {
        candidateClient.close()
        if (client === candidateClient) client = null
        // Give the sensor a moment to notice the disconnect before the next try.
        delay(RETRY_PAUSE_MS)
        handleAction(action)
    }

    private fun handleAction(action: G7PairingPlanner.Action) {
        if (!active) return
        when (action) {
            G7PairingPlanner.Action.RetryCurrent, G7PairingPlanner.Action.AdvanceToNext -> {
                _state.value = G7PairingState.Scanning(planner.candidates.map { it.name }, scanStartedAt)
                // The attempt job is still finishing; start the next try after it.
                scope.launch { tryCurrentCandidate() }
            }

            G7PairingPlanner.Action.KeepScanning                                         -> {
                log("None of the sensors seen so far match the code; still scanning")
                _state.value = G7PairingState.Scanning(planner.candidates.map { it.name }, scanStartedAt)
            }

            is G7PairingPlanner.Action.GiveUp                                            -> fail(G7PairingState.Failed(toReason(action.reason), action.details))
        }
    }

    private fun succeed(pairedClient: G7GattClient, candidate: G7PairingPlanner.Candidate, sharedKey: ByteArray) {
        log("Paired with ${candidate.name}")
        active = false
        scanDeadline?.cancel()
        bluetooth.stopScan()
        client = null
        val now = System.currentTimeMillis()
        store.update { old ->
            val sameSensor = old.address == candidate.id
            G7State(
                address = candidate.id,
                pairingCode = pairingCode,
                sharedKeyHex = sharedKey.toHex(),
                sensorName = candidate.name,
                serial = expectedSerial ?: if (sameSensor) old.serial else null,
                firmware = if (sameSensor) old.firmware else null,
                activatedAt = if (sameSensor) old.activatedAt else null,
                clockAnchorAt = if (sameSensor) old.clockAnchorAt else null,
                sessionLengthSeconds = if (sameSensor) old.sessionLengthSeconds else null,
                warmupSeconds = if (sameSensor) old.warmupSeconds else null,
                pairedAt = now,
                latestReadingAt = if (sameSensor) old.latestReadingAt else null,
                latestGlucose = if (sameSensor) old.latestGlucose else null,
                latestAlgorithmState = if (sameSensor) old.latestAlgorithmState else null,
                previousSensor = if (sameSensor || old.address == null) old.previousSensor else old.archived(now, "replaced"),
                alertsIssued = if (sameSensor) old.alertsIssued else emptySet()
            )
        }
        _state.value = G7PairingState.Succeeded(candidate.name)
        session.adoptPairedConnection(pairedClient)
    }

    companion object {

        const val SCAN_TIMEOUT_MS = 20 * 60_000L
        const val CANDIDATE_TIMEOUT_MS = 20_000L
        const val AUTHENTICATION_TIMEOUT_MS = 90_000L
        const val RETRY_PAUSE_MS = 2_000L

        fun isValidPairingCode(code: String): Boolean = code.length == 4 && code.all { it in '0'..'9' }
    }
}
