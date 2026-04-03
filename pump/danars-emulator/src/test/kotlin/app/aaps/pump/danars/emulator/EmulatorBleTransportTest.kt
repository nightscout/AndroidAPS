package app.aaps.pump.danars.emulator

import app.aaps.pump.danars.encryption.BleEncryption
import app.aaps.core.interfaces.pump.ble.BleTransportListener
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests EmulatorBleTransport + BleEncryption handshake and command round-trips.
 *
 * Uses two BleEncryption instances:
 * - appEncryption: simulates the app side (what BLEComm normally uses)
 * - pumpEncryption: inside EmulatorBleTransport (pump side)
 *
 * Tests the full encrypted byte flow without BLEComm or Android dependencies.
 */
class EmulatorBleTransportTest {

    private lateinit var transport: EmulatorBleTransport
    private lateinit var appEncryption: BleEncryption
    private val deviceName = "UHH00002TI"
    private val responses = mutableListOf<ByteArray>()

    private val listener = object : BleTransportListener {
        override fun onConnectionStateChanged(connected: Boolean) {}
        override fun onServicesDiscovered(success: Boolean) {}
        override fun onDescriptorWritten() {}
        override fun onCharacteristicChanged(data: ByteArray) {
            responses.add(data)
        }

        override fun onCharacteristicWritten() {}
    }

    @BeforeEach
    fun setup() {
        transport = EmulatorBleTransport(deviceName = deviceName)
        transport.setListener(listener)
        appEncryption = BleEncryption()
        responses.clear()
    }

    @Test
    fun testPumpCheckHandshake() {
        // App sends PUMP_CHECK with device name
        val pumpCheck = appEncryption.getEncryptedPacket(
            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK,
            null,
            deviceName
        )

        transport.gatt.writeCharacteristic(pumpCheck)

        assertThat(responses).hasSize(1)

        // App decrypts response
        val decrypted = appEncryption.getDecryptedPacket(responses[0])
        assertThat(decrypted).isNotNull()
        // Should be encryption response type
        assertThat(decrypted!![0].toInt() and 0xFF).isEqualTo(BleEncryption.DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE.toInt())
        // Opcode should be PUMP_CHECK
        assertThat(decrypted[1].toInt() and 0xFF).isEqualTo(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK)
        // Should contain "OK"
        assertThat(decrypted[2]).isEqualTo('O'.code.toByte())
        assertThat(decrypted[3]).isEqualTo('K'.code.toByte())
    }

    @Test
    fun testFullV1Handshake() {
        // Step 1: PUMP_CHECK
        val pumpCheck = appEncryption.getEncryptedPacket(
            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK,
            null,
            deviceName
        )
        transport.gatt.writeCharacteristic(pumpCheck)
        assertThat(responses).hasSize(1)
        val pumpCheckResponse = appEncryption.getDecryptedPacket(responses[0])
        assertThat(pumpCheckResponse).isNotNull()
        // Verify v1 OK response (4 bytes: TYPE, OPCODE, 'O', 'K')
        assertThat(pumpCheckResponse!!.size).isEqualTo(4)
        responses.clear()

        // Step 2: CHECK_PASSKEY
        val passkey = byteArrayOf(0xAB.toByte(), 0xCD.toByte())
        val passkeyCheck = appEncryption.getEncryptedPacket(
            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY,
            passkey,
            null
        )
        transport.gatt.writeCharacteristic(passkeyCheck)
        assertThat(responses).hasSize(1)
        val passkeyResponse = appEncryption.getDecryptedPacket(responses[0])
        assertThat(passkeyResponse).isNotNull()
        // Passkey OK = 0x00
        assertThat(passkeyResponse!![2]).isEqualTo(0x00.toByte())
        responses.clear()

        // Step 3: TIME_INFORMATION
        val timeInfo = appEncryption.getEncryptedPacket(
            BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION,
            null,
            null
        )
        transport.gatt.writeCharacteristic(timeInfo)
        assertThat(responses).hasSize(1)
        val timeResponse = appEncryption.getDecryptedPacket(responses[0])
        assertThat(timeResponse).isNotNull()
        // Response should contain time info + encoded password (at least 8 bytes: type + opcode + 6 time + 2 password)
        assertThat(timeResponse!!.size).isAtLeast(8)
    }

    @Test
    fun testCommandRoundTrip() {
        // First complete handshake (simplified - just set encryption state)
        completeHandshake()

        // Now send a regular command: GET_PROFILE_NUMBER
        transport.pumpState.activeProfileNumber = 2

        val encrypted = appEncryption.getEncryptedPacket(
            BleEncryption.DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_NUMBER,
            ByteArray(0),
            null
        )
        responses.clear()
        transport.gatt.writeCharacteristic(encrypted)

        assertThat(responses).hasSize(1)

        val decrypted = appEncryption.getDecryptedPacket(responses[0])
        assertThat(decrypted).isNotNull()
        // Type should be RESPONSE
        assertThat(decrypted!![0].toInt() and 0xFF).isEqualTo(BleEncryption.DANAR_PACKET__TYPE_RESPONSE)
        // OpCode should match
        assertThat(decrypted[1].toInt() and 0xFF).isEqualTo(BleEncryption.DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_NUMBER)
        // Data: active profile number = 2
        assertThat(decrypted[2].toInt() and 0xFF).isEqualTo(2)
    }

    @Test
    fun testSetTempBasalRoundTrip() {
        completeHandshake()

        // Send APS_SET_TEMPORARY_BASAL: 150%, 15min duration
        val params = byteArrayOf(150.toByte(), 0x00, 150.toByte()) // 150 = PARAM15MIN
        val encrypted = appEncryption.getEncryptedPacket(
            BleEncryption.DANAR_PACKET__OPCODE_BASAL__APS_SET_TEMPORARY_BASAL,
            params,
            null
        )
        responses.clear()
        transport.gatt.writeCharacteristic(encrypted)

        assertThat(responses).hasSize(1)
        val decrypted = appEncryption.getDecryptedPacket(responses[0])
        assertThat(decrypted).isNotNull()
        // Result = 0x00 (OK)
        assertThat(decrypted!![2]).isEqualTo(0x00.toByte())

        // Verify pump state changed
        assertThat(transport.pumpState.isTempBasalRunning).isTrue()
        assertThat(transport.pumpState.tempBasalPercent).isEqualTo(150)
    }

    /**
     * Run through the v1 encryption handshake to set both encryption instances to connected state.
     */
    private fun completeHandshake() {
        // PUMP_CHECK
        transport.gatt.writeCharacteristic(
            appEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK, null, deviceName)
        )
        appEncryption.getDecryptedPacket(responses.last())

        // CHECK_PASSKEY
        transport.gatt.writeCharacteristic(
            appEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY, byteArrayOf(0xAB.toByte(), 0xCD.toByte()), null)
        )
        appEncryption.getDecryptedPacket(responses.last())

        // TIME_INFORMATION
        transport.gatt.writeCharacteristic(
            appEncryption.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION, null, null)
        )
        appEncryption.getDecryptedPacket(responses.last())

        responses.clear()
    }
}
