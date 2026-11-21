package app.aaps.plugins.eversense.emulator

import app.aaps.core.interfaces.pump.ble.EmulatedBleCentral
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.plugins.eversense.EversenseDriver
import app.aaps.plugins.eversense.EversenseDriver.ConnectionState
import app.aaps.plugins.eversense.MemoryStore
import app.aaps.plugins.eversense.models.EversenseSecureState
import app.aaps.plugins.eversense.util.EversenseCrypto365Util
import app.aaps.plugins.eversense.util.EversenseHttp365Util
import app.aaps.plugins.eversense.util.StorageKeys
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

/**
 * A 365's first registration, the only step that needs the Eversense server: the real driver against the emulated
 * transmitter and a fake Eversense server on this machine. In real time, as the server calls are real HTTP; it takes
 * a few seconds.
 */
class EmulatedRegistrationTest {

    private val store = MemoryStore()
    private val network: ReceiverStatusStore = mock<ReceiverStatusStore>().also { whenever(it.isConnected).thenReturn(true) }
    private val server = MockWebServer()
    private val transmitter = EversenseTransmitterEmulator(name = "D3650002", address = "00:EE:00:36:50:02")
    private val peripheral = Eversense365Peripheral(transmitter)
    private val driver = EversenseDriver(EmulatedBleCentral().also { it.add(peripheral) }, store, AAPSLoggerTest(), network)
    private val urls = with(EversenseHttp365Util) { listOf(tokenBaseUrl, uploadBaseUrl, careBaseUrl, vaultBaseUrl) }

    @BeforeEach fun setUp() {
        server.start()
        // The US addresses: the login is not in the European region
        with(EversenseHttp365Util) {
            tokenBaseUrl = server.url("/token/").toString()
            uploadBaseUrl = server.url("/upload/").toString()
            careBaseUrl = server.url("/care/").toString()
            vaultBaseUrl = server.url("/vault/").toString()
        }
        // A 365 just paired: a login, no registration yet
        store.values[StorageKeys.SECURE_STATE] = Json.encodeToString(EversenseSecureState().apply {
            username = "user@example.com"
            password = "secret"
            model = "E365_USA"
        })
        store.values[StorageKeys.REMOTE_DEVICE_KEY] = transmitter.address
        store.values[StorageKeys.REMOTE_DEVICE_NAME] = transmitter.name
    }

    @AfterEach fun tearDown() {
        runBlocking { driver.stop() }
        with(EversenseHttp365Util) {
            tokenBaseUrl = urls[0]
            uploadBaseUrl = urls[1]
            careBaseUrl = urls[2]
            vaultBaseUrl = urls[3]
        }
        server.close()
    }

    private fun json(code: Int, body: String) = MockResponse.Builder().code(code).body(body).addHeader("Content-Type", "application/json; charset=utf-8").build()

    @Test fun aFirstPairingRegistersThroughTheServerAndSignsIn(): Unit = runBlocking {
        server.enqueue(json(200, TOKEN))
        server.enqueue(json(200, CERTIFICATE))

        driver.start()
        withTimeout(WAIT_MS) { driver.connectionState.first { it == ConnectionState.READY } }

        // The login, then the certificate for this transmitter, handed to it as the identity
        val login = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertThat(login.method).isEqualTo("POST")
        assertThat(login.target).contains("connect/token")
        val certificate = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertThat(certificate.target).contains("GetTxCertificate")
        assertThat(certificate.target).contains("txSerialNumber=")
        assertThat(peripheral.identity).isEqualTo(byteArrayOf(0x01, 0x02, 0x03))
        // Registered: the next connect takes the shortcut, with no server
        assertThat(EversenseCrypto365Util(store, AAPSLoggerTest()).canUseShortcut()).isTrue()
        assertThat(peripheral.signedIn).isTrue()
    }

    /** A login the server refuses ends the registration, and is recorded for the user's "sign in again". */
    @Test fun aRefusedLoginIsRecordedAndNothingIsRegistered(): Unit = runBlocking {
        server.enqueue(json(401, "{\"error\":\"invalid_grant\"}"))

        driver.start()
        withTimeout(WAIT_MS) { while (store.values[StorageKeys.LOGIN_REFUSED] != true) delay(50) }

        assertThat(driver.connectionState.value).isNotEqualTo(ConnectionState.READY)
        assertThat(peripheral.identity).isNull()
        assertThat(EversenseCrypto365Util(store, AAPSLoggerTest()).canUseShortcut()).isFalse()
    }

    private companion object {

        const val WAIT_MS = 20_000L
        const val TOKEN = """{"access_token":"fresh-token","expires_in":3600,"token_type":"Bearer","expires":"2099-01-01T00:00:00Z","lastLogin":"2025-01-01T00:00:00Z"}"""
        const val CERTIFICATE = """{"Status":"Success","StatusCode":200,"Result":{"Certificate":"010203","IsKeyAvailable":true}}"""
    }
}
