package app.aaps.plugins.eversense

import app.aaps.plugins.eversense.models.EversenseSecureState
import app.aaps.plugins.eversense.protocol.PacketTime
import app.aaps.plugins.eversense.util.EversenseCrypto365Util
import app.aaps.plugins.eversense.util.StorageKeys
import app.aaps.shared.tests.AAPSLoggerTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json

/**
 * A real [EversenseDriver] on a [FakeBleCentral] and an in-memory store, in virtual time: the driver's thread is the
 * test's dispatcher, and the protocol's clock follows the test's time.
 */
internal class DriverHarness {

    val store = MemoryStore()
    val central = FakeBleCentral()
    val network = FakeNetwork()
    var online: Boolean
        get() = network.online
        set(value) {
            network.online = value
        }
    val watcher = RecordingWatcher()

    /** The iOS rule: a lost link waits for the transmitter in a pending connect, not on a timer */
    var reconnectWithoutTimers = false

    fun storeLogin(username: String = "", password: String = "", model: String = "", shortcut: Boolean = false) {
        val state = secureState()
        state.username = username
        state.password = password
        state.model = model
        state.canUseShortcut = shortcut
        store.values[StorageKeys.SECURE_STATE] = Json.encodeToString(state)
    }

    fun storeTransmitter(address: String = ADDRESS, name: String = NAME) {
        store.values[StorageKeys.REMOTE_DEVICE_KEY] = address
        store.values[StorageKeys.REMOTE_DEVICE_NAME] = name
    }

    /** An E3 used without a login, paired. */
    fun pairedE3() {
        storeLogin(model = "E3")
        storeTransmitter()
    }

    /** A 365 with a login, paired, not registered yet. */
    fun paired365() {
        storeLogin(username = USERNAME, password = PASSWORD, model = "E365_USA")
        storeTransmitter()
    }

    /** A 365 with a login whose phone is registered with it: the sign-in takes the shortcut. */
    fun registered365() {
        paired365()
        EversenseCrypto365Util(store, AAPSLoggerTest()).generateKeyPairIfNotExists()
        val state = secureState()
        state.canUseShortcut = true
        store.values[StorageKeys.SECURE_STATE] = Json.encodeToString(state)
    }

    fun secureState(): EversenseSecureState = Json.decodeFromString<EversenseSecureState>(store.getString(StorageKeys.SECURE_STATE, null) ?: "{}")

    fun run(body: suspend TestScope.(EversenseDriver) -> Unit) {
        val dispatcher = StandardTestDispatcher()
        PacketTime.withClock({ START + dispatcher.scheduler.currentTime }) {
            runTest(dispatcher) {
                val driver = EversenseDriver(
                    central, store, AAPSLoggerTest(), network, dispatcher, reconnectWithoutTimers,
                    isClosedByTransmitter = { it.platformStatus == CLOSED_BY_TRANSMITTER }
                )
                driver.addWatcher(watcher)
                try {
                    body(driver)
                } finally {
                    // The driver's loop is not a child of the test: stopped, it cannot keep the test running
                    driver.stop()
                }
            }
        }
    }

    companion object {

        const val ADDRESS = "00:11:22:33:44:55"
        const val NAME = "T123456"
        const val USERNAME = "user@example.com"
        const val PASSWORD = "secret"

        /** The wall clock at virtual time 0 */
        const val START = 1_800_000_000_000L

        /**
         * The status of a link the transmitter closed, on every platform in these tests: Android's 19. Each platform
         * has its own code (`closedByTransmitter`: 7 on iOS, none on the desktop); the tests check the rules after it.
         */
        const val CLOSED_BY_TRANSMITTER = 19
    }
}
