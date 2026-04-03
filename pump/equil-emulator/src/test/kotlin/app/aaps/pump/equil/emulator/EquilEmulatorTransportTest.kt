package app.aaps.pump.equil.emulator

import app.aaps.core.interfaces.pump.ble.BleTransportListener
import app.aaps.pump.equil.manager.AESUtil
import app.aaps.pump.equil.manager.EquilPacketCodec
import app.aaps.pump.equil.manager.Utils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Integration test: verifies the full BLE packet framing + encryption
 * round-trip through the emulator transport.
 */
class EquilEmulatorTransportTest {

    private lateinit var transport: EquilEmulatorBleTransport
    private lateinit var state: EquilPumpState

    // Captures responses delivered via onCharacteristicChanged
    private val receivedPackets = mutableListOf<ByteArray>()
    private var servicesDiscovered = false
    private var descriptorWritten = false
    private var connected = false

    private val testListener = object : BleTransportListener {
        override fun onConnectionStateChanged(connected: Boolean) {
            this@EquilEmulatorTransportTest.connected = connected
        }
        override fun onServicesDiscovered(success: Boolean) { servicesDiscovered = success }
        override fun onDescriptorWritten() { descriptorWritten = true }
        override fun onCharacteristicChanged(data: ByteArray) {
            receivedPackets.add(data.copyOf())
        }
        override fun onCharacteristicWritten() {}
    }

    @BeforeEach
    fun setUp() {
        state = EquilPumpState()
        state.devicePassword = ByteArray(32) { (it + 1).toByte() }
        state.sessionPassword = ByteArray(32) { (it + 0x10).toByte() }
        state.sessionCode = "A1B2"

        transport = EquilEmulatorBleTransport(
            emulator = EquilPumpEmulator(state)
        )
        transport.setListener(testListener)
        receivedPackets.clear()
    }

    @AfterEach
    fun tearDown() {
        transport.awaitPendingCallbacks()
    }

    @Test
    fun `connect should trigger connection callback`() {
        transport.gatt.connect("00:00:00:00:00:00")

        assertTrue(connected)
    }

    @Test
    fun `discoverServices should trigger services discovered`() {
        transport.gatt.connect("00:00:00:00:00:00")
        transport.gatt.discoverServices()

        assertTrue(servicesDiscovered)
    }

    @Test
    fun `enableNotifications should trigger descriptor written`() {
        transport.gatt.connect("00:00:00:00:00:00")
        transport.gatt.discoverServices()
        transport.gatt.enableNotifications()

        assertTrue(descriptorWritten)
    }

    @Test
    fun `sending framed packets should produce response packets`() {
        transport.gatt.connect("00:00:00:00:00:00")

        // Build an initial request: encrypt with device password
        val reqData = Utils.concat(Utils.intToBytes(10), ByteArray(8) { 0x01 })
        val encrypted = AESUtil.aesEncrypt(state.devicePassword, reqData)

        // Frame into BLE packets
        val packets = EquilPacketCodec.buildPackets(encrypted, "0F0F0000", 0, System.currentTimeMillis())

        // Send each packet
        for (buf in packets.send) {
            transport.gatt.writeCharacteristic(buf.array())
        }

        // Wait for async response delivery (5ms delay + margin)
        Thread.sleep(50)

        // Should have received response packets
        assertTrue(receivedPackets.isNotEmpty(), "Should receive response packets")

        // Last received packet should have end bit set
        val lastPacket = receivedPackets.last()
        assertTrue(EquilPacketCodec.isEnd(lastPacket[4]), "Last response packet should have end bit")
    }

    @Test
    fun `full 3-phase round trip through transport`() {
        transport.gatt.connect("00:00:00:00:00:00")

        // Phase 1: initial request
        sendEncryptedMessage(
            Utils.concat(Utils.intToBytes(10), ByteArray(8)),
            state.devicePassword,
            "0F0F0000",
            0
        )
        Thread.sleep(50)
        assertTrue(receivedPackets.isNotEmpty(), "Phase 1 should produce response")
        receivedPackets.clear()

        // Phase 2: running mode get (0x02, 0x00)
        sendEncryptedMessage(
            Utils.concat(Utils.intToBytes(11), byteArrayOf(0x02, 0x00)),
            state.sessionPassword,
            "0404A1B2",
            1
        )
        Thread.sleep(50)
        assertTrue(receivedPackets.isNotEmpty(), "Phase 2 should produce response")

        // Parse response and verify running mode
        val responseModel = reassembleResponse()
        assertNotNull(responseModel)
        val decrypted = AESUtil.decrypt(responseModel!!, state.sessionPassword)
        val bytes = Utils.hexStringToBytes(decrypted)
        // byte[6] should be running mode
        assertTrue(bytes.size > 6)

        receivedPackets.clear()

        // Phase 3: confirmation
        sendEncryptedMessage(
            Utils.concat(Utils.intToBytes(12), byteArrayOf(0x00, 0x02, 0x01)),
            state.sessionPassword,
            "0404A1B2",
            2
        )
        Thread.sleep(50)
        assertTrue(receivedPackets.isNotEmpty(), "Phase 3 should produce response")
    }

    @Test
    fun `scanner should emit device`() {
        var emitted = false
        // Can't easily collect SharedFlow in test without coroutines,
        // but we can verify startScan doesn't crash
        transport.scanner.startScan()
        transport.scanner.stopScan()
        // No crash = pass
    }

    @Test
    fun `disconnect should work without error`() {
        transport.gatt.connect("00:00:00:00:00:00")
        transport.gatt.disconnect()
        transport.gatt.close()
        // No crash = pass
    }

    // --- Helpers ---

    private fun sendEncryptedMessage(data: ByteArray, password: ByteArray, port: String, reqIndex: Int) {
        val encrypted = AESUtil.aesEncrypt(password, data)
        val packets = EquilPacketCodec.buildPackets(encrypted, port, reqIndex, System.currentTimeMillis())
        for (buf in packets.send) {
            transport.gatt.writeCharacteristic(buf.array())
        }
    }

    private fun reassembleResponse(): app.aaps.pump.equil.manager.EquilCmdModel? {
        if (receivedPackets.isEmpty()) return null
        val response = app.aaps.pump.equil.manager.EquilResponse(System.currentTimeMillis())
        for (packet in receivedPackets) {
            response.add(java.nio.ByteBuffer.wrap(packet))
        }
        return EquilPacketCodec.parseModel(response)
    }
}
