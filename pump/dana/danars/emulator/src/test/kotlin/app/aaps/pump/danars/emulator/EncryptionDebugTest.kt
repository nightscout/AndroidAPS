package app.aaps.pump.danars.emulator

import app.aaps.pump.danars.encryption.BleEncryption
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class EncryptionDebugTest {

    @Test
    fun debugPumpCheckPacketFormat() {
        val enc = BleEncryption()
        val deviceName = "UHH00002TI"
        val packet = enc.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK, null, deviceName)

        // Check start bytes are preserved
        assertThat(packet[0]).isEqualTo(0xA5.toByte())
        assertThat(packet[1]).isEqualTo(0xA5.toByte())

        // Check end bytes are preserved
        assertThat(packet[packet.size - 2]).isEqualTo(0x5A.toByte())
        assertThat(packet[packet.size - 1]).isEqualTo(0x5A.toByte())

        // Check length byte (unencoded)
        val length = packet[2].toInt() and 0xFF
        assertThat(packet.size).isEqualTo(length + 7)

        println("PUMP_CHECK packet: ${packet.map { String.format("%02X", it) }.joinToString(" ")}")
        println("Packet size: ${packet.size}, length field: $length")
    }

    @Test
    fun debugDecryptPumpCheck() {
        val appEnc = BleEncryption()
        val pumpEnc = BleEncryption()
        val deviceName = "UHH00002TI"

        // Set device name on pump side
        for (i in deviceName.indices) {
            if (i < 10) pumpEnc.deviceName[i] = deviceName[i].code.toUByte()
        }

        // App sends PUMP_CHECK
        val packet = appEnc.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK, null, deviceName)
        println("Encrypted packet: ${packet.map { String.format("%02X", it) }.joinToString(" ")}")

        // Pump side decrypts
        val decrypted = pumpEnc.getDecryptedPacket(packet)
        println("Decrypted: ${decrypted?.map { String.format("%02X", it) }?.joinToString(" ")}")
        assertThat(decrypted).isNotNull()

        // Should be: TYPE_ENCRYPTION_REQUEST (0x01), OPCODE_PUMP_CHECK (0x00), then device name bytes
        assertThat(decrypted!![0].toInt() and 0xFF).isEqualTo(BleEncryption.DANAR_PACKET__TYPE_ENCRYPTION_REQUEST.toInt())
        assertThat(decrypted[1].toInt() and 0xFF).isEqualTo(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK)
    }
}
