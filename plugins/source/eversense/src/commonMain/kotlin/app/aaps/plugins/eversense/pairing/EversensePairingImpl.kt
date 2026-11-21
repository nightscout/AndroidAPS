package app.aaps.plugins.eversense.pairing

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.AdapterState
import app.aaps.core.interfaces.pump.ble.BleCentral
import app.aaps.core.interfaces.pump.ble.BleException
import app.aaps.core.interfaces.pump.ble.ScanFilter
import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.source.CgmAttention
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.eversense.EversenseDriver
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.connection.EversenseSession
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.keys.EversenseRuntimeKey
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseSecureState
import app.aaps.plugins.eversense.models.EversenseState
import app.aaps.plugins.eversense.util.DmsSignIn
import app.aaps.plugins.eversense.util.EversenseHttp365Util
import app.aaps.plugins.eversense.util.EversenseHttpE3Util
import app.aaps.plugins.eversense.util.StorageKeys
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * [EversensePairing] on the driver. What it shows is read from the driver's store (one stored JSON object),
 * which it observes, so a login refused during a later upload reaches the overview's badge with no extra wiring.
 *
 * Nothing is read at construction: the flows start on first use.
 */
@SingleIn(AppScope::class)
@Inject
class EversensePairingImpl(
    // Deferred: building the driver opens its store, which a graph build must not do
    private val eversenseProvider: () -> EversenseDriver,
    private val central: BleCentral,
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger
) : EversensePairing {

    private val eversense get() = eversenseProvider()

    // Lives as long as the app: the plugin, the overview and the wizard share these flows
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val snapshot: StateFlow<PairingSnapshot> by lazy {
        preferences.observe(EversenseRuntimeKey.DeviceState)
            .map(PairingSnapshot::of)
            .stateIn(scope, SharingStarted.Eagerly, PairingSnapshot.of(preferences.get(EversenseRuntimeKey.DeviceState)))
    }

    override val account: StateFlow<EversenseAccount?> by lazy { snapshot.derived { it.account } }
    override val withoutSignIn: StateFlow<Boolean> by lazy { snapshot.derived { it.withoutSignIn } }
    override val transmitter: StateFlow<String?> by lazy { snapshot.derived { it.transmitter } }
    override val attention: StateFlow<CgmAttention?> by lazy {
        snapshot.derived { cgmAttention(it.account, it.transmitter, it.loginRefused, it.withoutSignIn) }
    }

    private fun <T> StateFlow<PairingSnapshot>.derived(pick: (PairingSnapshot) -> T): StateFlow<T> =
        map(pick).stateIn(scope, SharingStarted.Eagerly, pick(value))

    override suspend fun signIn(username: String, password: String, model: EversenseModel): SignInResult = withContext(Dispatchers.IO) {
        val outcome = DmsSignIn.check(tokenBaseUrl(model), username, password)
        aapsLogger.info(LTag.BGSOURCE, "Eversense sign-in for ${model.name}: $outcome")
        if (outcome == DmsSignIn.Outcome.OK) {
            eversense.setCredentials(username, password, europeanRegion = model == EversenseModel.E365_OTHER)
            eversense.rememberModel(model.name)
            DmsSignIn.remember(eversense.preferences, outcome)
        }
        when (outcome) {
            DmsSignIn.Outcome.OK            -> SignInResult.OK
            DmsSignIn.Outcome.REFUSED       -> SignInResult.REFUSED
            DmsSignIn.Outcome.NO_CONNECTION -> SignInResult.NO_CONNECTION
            DmsSignIn.Outcome.SERVER_ERROR  -> SignInResult.SERVER_ERROR
        }
    }

    /** The server each model signs in at: the same ones the driver's own logins use. */
    private fun tokenBaseUrl(model: EversenseModel): String = when (model) {
        EversenseModel.E3         -> EversenseHttpE3Util.tokenBaseUrl
        EversenseModel.E365_USA   -> EversenseHttp365Util.tokenBaseUrl
        EversenseModel.E365_OTHER -> EversenseHttp365Util.euTokenBaseUrl
    }

    override fun signOut() {
        scope.launch { eversense.clearCredentials() }
    }

    override fun useWithoutSignIn() {
        scope.launch { eversense.useWithoutLogin() }
    }

    /**
     * The transmitters around, for [SCAN_TIME]; then the flow ends. A scan that cannot run ends it at once.
     *
     * A transmitter is known by its Bluetooth service, not by its name: E3 names start with "T", but a test transmitter
     * showed "D313640". Trio's Eversense driver scans the same way. A device with no name is left out: the list would
     * have nothing to show for it.
     */
    override fun scan(): Flow<List<ScannedDevice>> = channelFlow {
        val found = LinkedHashMap<String, ScannedDevice>()
        try {
            withTimeoutOrNull(SCAN_TIME) {
                central.scan(ScanFilter(services = listOf(EversenseSession.SERVICE))).collect { advertisement ->
                    val name = advertisement.name ?: return@collect
                    found[advertisement.device.value] = ScannedDevice(name = name, address = advertisement.device.value, rssi = advertisement.rssi)
                    send(found.values.toList())
                }
            }
        } catch (e: BleException) {
            aapsLogger.warn(LTag.BGSOURCE, "Eversense scan cannot run: ${e.failure}")
        }
    }

    override fun scanProblem(): ScanProblem? = when (central.adapterState.value) {
        AdapterState.OFF, AdapterState.UNSUPPORTED -> ScanProblem.BLUETOOTH_OFF
        AdapterState.UNAUTHORIZED                  -> ScanProblem.NOT_ALLOWED
        AdapterState.ON, AdapterState.UNKNOWN      -> null
    }

    override suspend fun pair(transmitter: ScannedDevice): PairResult {
        val ready = CompletableDeferred<Unit>()
        val watcher = object : EversenseWatcher {
            override fun onTransmitterReady() {
                ready.complete(Unit)
            }

            override fun onConnectionChanged(connected: Boolean) {}
            override fun onStateChanged(state: EversenseState) {}
            override fun onCGMRead(type: EversenseType, readings: List<EversenseCGMResult>) {}
            override fun onAlarmReceived(alarm: ActiveAlarm) {}
            override fun onTransmitterNotPlaced() {}
        }
        eversense.addWatcher(watcher)
        try {
            if (!eversense.pair(transmitter.address, transmitter.name)) return PairResult.NOT_STARTED
            return if (withTimeoutOrNull(PAIR_TIMEOUT) { ready.await() } != null) PairResult.READY else PairResult.TIMED_OUT
        } finally {
            eversense.removeWatcher(watcher)
        }
    }

    override fun unpair() {
        scope.launch { eversense.clearStoredDevice() }
    }

    private companion object {

        /** As long as the driver gives a connection attempt; a 365 registration makes two server calls on top. */
        val PAIR_TIMEOUT = 3.minutes

        /** As the first version scanned */
        val SCAN_TIME = 10.seconds
    }
}

/** What pairing shows, read from the driver's stored JSON object. */
internal data class PairingSnapshot(
    val account: EversenseAccount?,
    val transmitter: String?,
    val loginRefused: Boolean,
    val withoutSignIn: Boolean = false
) {

    companion object {

        private val json = Json { ignoreUnknownKeys = true }

        fun of(deviceState: String): PairingSnapshot {
            val store = runCatching { json.parseToJsonElement(deviceState).jsonObject }.getOrDefault(JsonObject(emptyMap()))
            fun string(key: String) = store[key]?.jsonPrimitive?.contentOrNull
            val secure = string(StorageKeys.SECURE_STATE)
                ?.let { runCatching { json.decodeFromString<EversenseSecureState>(it) }.getOrNull() }
                ?: EversenseSecureState()
            val account = if (secure.username.isNotEmpty() && secure.password.isNotEmpty())
                EversenseAccount(secure.username, EversenseModel.entries.firstOrNull { it.name == secure.model })
            else null
            val address = string(StorageKeys.REMOTE_DEVICE_KEY)
            return PairingSnapshot(
                account = account,
                transmitter = address?.let { string(StorageKeys.REMOTE_DEVICE_NAME) ?: it },
                loginRefused = store[StorageKeys.LOGIN_REFUSED]?.jsonPrimitive?.booleanOrNull == true,
                withoutSignIn = account == null && secure.model == EversenseModel.E3.name
            )
        }
    }
}
