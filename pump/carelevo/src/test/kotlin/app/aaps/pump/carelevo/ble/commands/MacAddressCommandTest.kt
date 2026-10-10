package app.aaps.pump.carelevo.ble.commands

import app.aaps.pump.carelevo.ble.BleClientImpl
import app.aaps.pump.carelevo.ble.gatt.FakeGattConnection
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

/**
 * Unit tests for [MacAddressCommand] — pure encode/decode plus one end-to-end round-trip
 * through [BleClientImpl] + [FakeGattConnection] to prove the whole stack works together
 * for a real opcode pair.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MacAddressCommandTest {

    @Test
    fun `encode produces 0x3B followed by the key byte`() {
        val cmd = MacAddressCommand(key = 0x7A)
        val encoded = cmd.encode()

        assertThat(encoded).isEqualTo(byteArrayOf(0x3B, 0x7A))
    }

    @Test
    fun `decode extracts mac address and checksum as hex strings`() {
        val cmd = MacAddressCommand(key = 0x01)
        // Opcode + 6 MAC bytes + 2 checksum bytes
        val payload = byteArrayOf(
            0x9B.toByte(),
            0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte(),
            0x12, 0x34
        )

        val response = cmd.decode(payload)

        assertThat(response.macAddress).isEqualTo("AABBCCDDEEFF")
        assertThat(response.checkSum).isEqualTo("1234")
    }

    @Test
    fun `decode rejects wrong opcode`() {
        val cmd = MacAddressCommand(key = 0x01)
        val wrongOpcode = byteArrayOf(
            0x84.toByte(), // NOT 0x9B
            0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07
        )

        assertFailsWith<IllegalArgumentException> {
            cmd.decode(wrongOpcode)
        }
    }

    @Test
    fun `decode rejects truncated payload`() {
        val cmd = MacAddressCommand(key = 0x01)
        val tooShort = byteArrayOf(0x9B.toByte(), 0x01, 0x02, 0x03)

        assertFailsWith<IllegalArgumentException> {
            cmd.decode(tooShort)
        }
    }

    @Test
    fun `end to end - BleClient sends request and decodes response from FakeGatt`() = runTest {
        val writeUuid = UUID.fromString("00000001-0000-1000-8000-00805f9b34fb")
        val notifyUuid = UUID.fromString("00000002-0000-1000-8000-00805f9b34fb")
        val gatt = FakeGattConnection()
        val client = BleClientImpl(gatt, writeUuid, notifyUuid, backgroundScope)
        runCurrent() // ensure the event collector is attached

        // Script the peripheral: when the SUT writes 0x3B, reply with 0x9B + MAC + checksum.
        gatt.onNextWrite { write ->
            assertThat(write.payload[0]).isEqualTo(0x3B.toByte())
            gatt.deliverNotification(
                notifyUuid,
                byteArrayOf(
                    0x9B.toByte(),
                    0x94.toByte(), 0xB2.toByte(), 0x16, 0x1D, 0x2F, 0x6D,
                    0xAB.toByte(), 0xCD.toByte()
                )
            )
        }

        val response = client.request(MacAddressCommand(key = 0x42))

        assertThat(response.macAddress).isEqualTo("94B2161D2F6D")
        assertThat(response.checkSum).isEqualTo("ABCD")
        assertThat(gatt.recordedWrites).hasSize(1)
        assertThat(gatt.recordedWrites.single().payload).isEqualTo(byteArrayOf(0x3B, 0x42))
    }
}
