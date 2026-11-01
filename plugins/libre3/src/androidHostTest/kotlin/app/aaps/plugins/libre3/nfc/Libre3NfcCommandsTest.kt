package app.aaps.plugins.libre3.nfc

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Byte vectors taken from the LibreCRKit tests
 * (`Tests/LibreCRKitTests/NFCActivationCommandTests.swift`, pin `a86b92f`).
 *
 * The receiver id and the unique text below are **test** values. Production builds a new unique
 * text once per install, see `Libre3SensorStore.receiverId`.
 */
class Libre3NfcCommandsTest {

    private fun hex(data: ByteArray): String = data.joinToString("") { "%02x".format(it) }

    private fun bytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `receiver id of the pinned test text matches the vector`() {
        val receiverId = Libre3NfcCommands.receiverIdFrom("5abb0ad8-dc2e-4ede-9e2d-67472a3e630e")

        assertThat(receiverId).isEqualTo(0x6F0D8378.toInt())
        assertThat(hex(Libre3NfcCommands.receiverIdBytes(receiverId))).isEqualTo("78830d6f")
    }

    @Test
    fun `receiver id of the second pinned test text matches the vector`() {
        val receiverId = Libre3NfcCommands.receiverIdFrom("6147368e-c060-44a5-9e96-1c02333f43c0")

        assertThat(receiverId).isEqualTo(0xC3251F23.toInt())
        assertThat(hex(Libre3NfcCommands.receiverIdBytes(receiverId))).isEqualTo("231f25c3")
    }

    @Test
    fun `the CRC of the pinned body matches the vector`() {
        assertThat(Libre3NfcCommands.abbottCrc16(bytes("fc2bee6978830d6f"))).isEqualTo(0x71B4)
    }

    @Test
    fun `the activation body carries time, receiver id and CRC, all little endian`() {
        val body = Libre3NfcCommands.activationBody(1777216508L, 0x6F0D8378.toInt())

        assertThat(body).hasLength(10)
        assertThat(hex(body)).isEqualTo("fc2bee6978830d6fb471")
    }

    @Test
    fun `the activate frame matches the captured wire bytes`() {
        val frame = Libre3NfcCommands.activationFrame(Libre3NfcUids.CMD_ACTIVATE, 1777216508L, 0x6F0D8378.toInt())

        assertThat(hex(frame)).isEqualTo("02a07afc2bee6978830d6fb471")
    }

    @Test
    fun `the switch receiver frames match the captured wire bytes`() {
        val first = Libre3NfcCommands.activationFrame(Libre3NfcUids.CMD_SWITCH_RECEIVER, 1778097295L, 0xC3251F23.toInt())
        val second = Libre3NfcCommands.activationFrame(Libre3NfcUids.CMD_SWITCH_RECEIVER, 1778100341L, 0x6F0D8378.toInt())

        assertThat(hex(first)).isEqualTo("02a87a8f9cfb69231f25c3aac8")
        assertThat(hex(second)).isEqualTo("02a87a75a8fb6978830d6f4cd4")
    }

    @Test
    fun `the patch info frame is the three fixed bytes`() {
        assertThat(hex(Libre3NfcCommands.patchInfoFrame())).isEqualTo("02a17a")
    }

    @Test
    fun `only frames with the sensor manufacturer code are accepted`() {
        assertThat(Libre3NfcCommands.isForThisManufacturer(bytes("02a17a"))).isTrue()
        // Same command, another manufacturer. This frame is not for a Libre 3.
        assertThat(Libre3NfcCommands.isForThisManufacturer(bytes("02a107"))).isFalse()
        assertThat(Libre3NfcCommands.isForThisManufacturer(bytes("02a1"))).isFalse()
    }

    @Test
    fun `a fresh sensor is read and asks for the activate command`() {
        val info = Libre3NfcCommands.parsePatchInfo(
            bytes("00a50001000200010060541e020401040c0130525243393839415151ff17")
        )

        assertThat(info.serialNumber).isEqualTo("0RRC989AQ")
        assertThat(info.state).isEqualTo(0x01.toByte())
        assertThat(info.generation).isEqualTo(1)
        assertThat(info.isPlus).isTrue()
        assertThat(info.wearDurationMinutes).isEqualTo(21600)
        assertThat(info.warmupMinutes).isEqualTo(60)
        assertThat(info.productType).isEqualTo(4)
        assertThat(info.firmwareVersion).isEqualTo("1.4.2.30")
        assertThat(info.recommendedCommand).isEqualTo(Libre3NfcUids.CMD_ACTIVATE)
    }

    @Test
    fun `a running sensor is read and asks for the switch receiver command`() {
        val info = Libre3NfcCommands.parsePatchInfo(
            bytes("00a50001000200010060541e020401040c04305252433938394151c6ca")
        )

        assertThat(info.serialNumber).isEqualTo("0RRC989AQ")
        assertThat(info.state).isEqualTo(0x04.toByte())
        assertThat(info.recommendedCommand).isEqualTo(Libre3NfcUids.CMD_SWITCH_RECEIVER)
    }

    @Test
    fun `patch info is read when the leading zero is missing`() {
        val info = Libre3NfcCommands.parsePatchInfo(
            bytes("a50001000200010060541e020401040c04305252433938394151c6ca")
        )

        assertThat(info.serialNumber).isEqualTo("0RRC989AQ")
        assertThat(info.state).isEqualTo(0x04.toByte())
    }

    @Test
    fun `patch info is read when the reader padded the answer with a5 bytes`() {
        val info = Libre3NfcCommands.parsePatchInfo(
            bytes("00a5a5a5a5a5a5a5a50001000200010060541e020401040c04305252433938394151c6ca")
        )

        assertThat(info.serialNumber).isEqualTo("0RRC989AQ")
        assertThat(info.recommendedCommand).isEqualTo(Libre3NfcUids.CMD_SWITCH_RECEIVER)
    }

    @Test
    fun `patch info is read when extra bytes sit in front of the real frame`() {
        val padded = ByteArray(36) { 0x11 } +
            bytes("00a50001000200010060541e020401040c04305252433938394151c6ca")

        val info = Libre3NfcCommands.parsePatchInfo(padded)

        assertThat(padded.size).isEqualTo(65)
        assertThat(info.serialNumber).isEqualTo("0RRC989AQ")
        assertThat(info.state).isEqualTo(0x04.toByte())
    }

    @Test
    fun `a patch info answer that is too short is refused`() {
        assertThrows<Libre3NfcException> { Libre3NfcCommands.parsePatchInfo(bytes("00a50001")) }
    }

    @Test
    fun `the activation answer gives the address and the PIN`() {
        val answer = Libre3NfcCommands.parseActivationResponse(bytes("00a50058f9b8df22cc3225ec7200000000ad06"))

        assertThat(hex(answer.bleAddressLittleEndian)).isEqualTo("58f9b8df22cc")
        assertThat(answer.bleAddressDisplay).isEqualTo("CC:22:DF:B8:F9:58")
        assertThat(hex(answer.blePin)).isEqualTo("3225ec72")
        assertThat(hex(answer.activationTimeRaw)).isEqualTo("00000000")
    }

    @Test
    fun `the switch receiver answer of a running sensor carries its activation time`() {
        val answer = Libre3NfcCommands.parseActivationResponse(bytes("00a50058f9b8df22cc02bafbbbfc2bee698e6e"))

        assertThat(answer.bleAddressDisplay).isEqualTo("CC:22:DF:B8:F9:58")
        assertThat(hex(answer.blePin)).isEqualTo("02bafbbb")
        assertThat(hex(answer.activationTimeRaw)).isEqualTo("fc2bee69")
    }

    @Test
    fun `the activation answer is read when the leading zero is missing`() {
        val answer = Libre3NfcCommands.parseActivationResponse(bytes("a50058f9b8df22cc3225ec7200000000ad06"))

        assertThat(answer.bleAddressDisplay).isEqualTo("CC:22:DF:B8:F9:58")
        assertThat(hex(answer.blePin)).isEqualTo("3225ec72")
    }

    @Test
    fun `an activation answer of the wrong length is refused`() {
        assertThrows<Libre3NfcException> {
            Libre3NfcCommands.parseActivationResponse(bytes("00a50058f9b8df22cc3225ec720000"))
        }
    }

    @Test
    fun `an unknown command byte is refused before anything is sent`() {
        assertThrows<IllegalArgumentException> {
            Libre3NfcCommands.activationFrame(0xA5.toByte(), 1777216508L, 0x6F0D8378.toInt())
        }
    }
}
