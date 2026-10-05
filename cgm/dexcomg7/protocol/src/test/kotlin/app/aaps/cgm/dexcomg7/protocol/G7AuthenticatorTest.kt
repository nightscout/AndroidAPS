package app.aaps.cgm.dexcomg7.protocol

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** The whole handshake against a simulated sensor. */
class G7AuthenticatorTest {

    private val logs = mutableListOf<String>()

    private fun authenticator(code: String?, key: ByteArray?) =
        G7Authenticator(code, key, G7Authenticator.PAIRING_STEP_TIMEOUT_MS, log = { logs.add(it) })

    @Test
    fun firstPairingRunsTheWholeHandshake() = runTest {
        val sensor = SimulatedG7Sensor("1155")
        val result = authenticator("1155", null).authenticate(sensor)

        assertThat(result.didExchangeKeys).isTrue()
        assertThat(result.sharedKey).hasLength(16)
        assertThat(result.sharedKey.toHex()).isEqualTo(sensor.storedKey!!.toHex())
        assertThat(sensor.receivedCertificates.map { it.size }).containsExactly(494, 465).inOrder()
        assertThat(sensor.signatureVerified).isTrue()
        assertThat(sensor.bondRequested).isTrue()
        assertThat(sensor.enabled).containsExactly(G7Characteristic.AUTHENTICATION, G7Characteristic.CERTIFICATE)
        assertThat(logs.joinToString()).doesNotContain("1155")
    }

    @Test
    fun reconnectWithAStoredKeyIsOneChallenge() = runTest {
        val key = ByteArray(16) { it.toByte() }
        val sensor = SimulatedG7Sensor("1155", storedKey = key)
        val result = authenticator(null, key).authenticate(sensor)

        assertThat(result.didExchangeKeys).isFalse()
        assertThat(sensor.receivedCertificates).isEmpty()
        assertThat(sensor.bondRequested).isFalse()
    }

    @Test
    fun wrongCodeIsAChallengeMismatch() = runTest {
        val sensor = SimulatedG7Sensor("9999")
        val error = runCatching { authenticator("1155", null).authenticate(sensor) }.exceptionOrNull()
        assertThat(error).isInstanceOf(G7AuthException.ChallengeMismatch::class.java)
    }

    @Test
    fun staleStoredKeyIsAChallengeMismatch() = runTest {
        val sensor = SimulatedG7Sensor("1155", storedKey = ByteArray(16) { 1 })
        val error = runCatching { authenticator("1155", ByteArray(16) { 2 }).authenticate(sensor) }.exceptionOrNull()
        assertThat(error).isInstanceOf(G7AuthException.ChallengeMismatch::class.java)
    }

    @Test
    fun refusalCarriesTheReason() = runTest {
        val key = ByteArray(16) { 3 }
        val sensor = SimulatedG7Sensor("1155", storedKey = key, refuseWith = G7AuthFailureCode.DEVICE_TYPE_RESTRICTION)
        val error = runCatching { authenticator(null, key).authenticate(sensor) }.exceptionOrNull()
        assertThat(error).isInstanceOf(G7AuthException.Rejected::class.java)
        assertThat((error as G7AuthException.Rejected).failureCode).isEqualTo(G7AuthFailureCode.DEVICE_TYPE_RESTRICTION)
    }

    @Test
    fun reconnectWithoutTheBondIsNotARefusal() = runTest {
        val key = ByteArray(16) { 4 }
        val sensor = SimulatedG7Sensor("1155", storedKey = key, bondStatusOnReconnect = 2)
        val error = runCatching { authenticator(null, key).authenticate(sensor) }.exceptionOrNull()
        assertThat(error).isInstanceOf(G7AuthException.NotBonded::class.java)
        assertThat((error as G7AuthException.NotBonded).bondStatus).isEqualTo(2)
    }

    @Test
    fun noCredentialsFailsAtOnce() {
        assertThrows(G7AuthException.NoCredentials::class.java) {
            runBlocking { authenticator(null, null).authenticate(SimulatedG7Sensor("1155")) }
        }
    }

    @Test
    fun silentSensorTimesOut() = runTest {
        val silent = object : G7Link {
            override val address = "AA:BB:CC:DD:EE:FF"
            override val name = "Dexcom12"
            override val isConnected = true
            override suspend fun enableNotifications(characteristic: G7Characteristic) {}
            override suspend fun write(characteristic: G7Characteristic, value: ByteArray, withResponse: Boolean) {}
            override fun setListener(characteristic: G7Characteristic, listener: ((ByteArray) -> Unit)?) {}
        }
        val error = runCatching { authenticator(null, ByteArray(16)).authenticate(silent) }.exceptionOrNull()
        assertThat(error).isInstanceOf(G7AuthException.Timeout::class.java)
    }
}
