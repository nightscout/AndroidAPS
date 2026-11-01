package app.aaps.pump.omnipod.common.bledriver.comm.packet

import app.aaps.pump.omnipod.common.bledriver.comm.message.CrcMismatchException
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Round-trips payloads through [PayloadSplitter] -> [BlePacket.toByteArray] -> [PayloadJoiner]
 * for both [BlePacketLayout.DASH] and [BlePacketLayout.OMNIPOD_5].
 *
 * The O5 sweep in particular targets the bug fixed alongside the O5 layout support: packet
 * "rest"/size fields are on-wire unsigned bytes (0..255), but Kotlin's Byte is signed, so any
 * payload whose last-packet remainder exceeds 127 previously sign-extended into a negative
 * length and broke [PayloadJoiner] parsing. Dash never produced a remainder over 127 (its
 * packets max out at 20 bytes), so this was unreachable there - O5's 244-byte packets reach it
 * routinely for realistic message sizes (e.g. certificate-carrying pairing messages).
 */
class PayloadSplitterJoinerTest : TestBase() {

    private fun payloadOf(size: Int, seed: Int): ByteArray = Random(seed).nextBytes(size)

    private fun roundTrip(payload: ByteArray, layout: BlePacketLayout): ByteArray {
        val packets = PayloadSplitter(payload, layout).splitInPackets()
        val encoded = packets.map { it.toByteArray(layout) }

        val joiner = PayloadJoiner(encoded.first(), layout)
        for (packetBytes in encoded.drop(1)) {
            joiner.accumulate(packetBytes)
        }
        return joiner.finalize()
    }

    @Test
    fun `Dash round-trip holds for every supported payload size`() {
        for (size in 1..283) {
            val payload = payloadOf(size, seed = size)
            val result = roundTrip(payload, BlePacketLayout.DASH)
            assertThat(result).isEqualTo(payload)
        }
    }

    @Test
    fun `O5 round-trip holds for every payload size from 1 to 2000 bytes`() {
        for (size in 1..2000) {
            val payload = payloadOf(size, seed = size)
            val result = roundTrip(payload, BlePacketLayout.OMNIPOD_5)
            assertThat(result).isEqualTo(payload)
        }
    }


    @Test
    fun `O5 layout constants match OmnipodKit's asserted values`() {
        val layout = BlePacketLayout.OMNIPOD_5
        assertThat(layout.maxPayloadSize).isEqualTo(244)
        assertThat(layout.maxFragments).isEqualTo(15)
        assertThat(layout.firstPacketCapacityWithoutMiddlePackets).isEqualTo(237)
        assertThat(layout.firstPacketCapacityWithMiddlePackets).isEqualTo(242)
        assertThat(layout.firstPacketCapacityWithOptionalPlusOnePacket).isEqualTo(242)
        assertThat(layout.middlePacketCapacity).isEqualTo(243)
        assertThat(layout.lastPacketCapacity).isEqualTo(238)
    }

    @Test
    fun `O5 split produces the same packet counts OmnipodKit asserts`() {
        val expected = mapOf(
            0 to 1,
            100 to 1,
            300 to 2,
            242 to 2,
            243 to 2,
            500 to 3,
            641 to 3,
            642 to 3,
            893 to 4,
            953 to 4,
            18 to 1,
            15 to 1,
            11 to 1,
            17 to 1,
            204 to 1,
            176 to 1,
            20 to 1
        )

        for ((size, packetCount) in expected) {
            val packets = PayloadSplitter(payloadOf(size, seed = size), BlePacketLayout.OMNIPOD_5).splitInPackets()
            assertThat(packets).hasSize(packetCount)
        }
    }

    @Test
    fun `O5 953-byte payload reports three full fragments, as OmnipodKit asserts`() {
        val packets = PayloadSplitter(payloadOf(953, seed = 2), BlePacketLayout.OMNIPOD_5).splitInPackets()

        assertThat(packets).hasSize(4)
        assertThat(packets.filterIsInstance<FirstBlePacket>().single().fullFragments).isEqualTo(3)
    }

    @Test
    fun `O5 last-packet remainder over 127 round-trips correctly (regression for the signed-Byte bug)`() {
        val size = 242 + 2 * 243 + 200
        val payload = payloadOf(size, seed = 4242)

        val packets = PayloadSplitter(payload, BlePacketLayout.OMNIPOD_5).splitInPackets()
        val lastPacket = packets.filterIsInstance<LastBlePacket>().single()
        assertThat(lastPacket.size.toUnsignedInt()).isEqualTo(200)

        val result = roundTrip(payload, BlePacketLayout.OMNIPOD_5)
        assertThat(result).isEqualTo(payload)
    }

    @Test
    fun `LastOptionalPlusOneBlePacket round-trips a size byte over 127`() {
        val layout = BlePacketLayout.OMNIPOD_5
        val payload = payloadOf(200, seed = 55)
        val packet = LastOptionalPlusOneBlePacket(index = 5, payload = payload, size = 200.toByte())

        val encoded = packet.toByteArray(layout)
        val parsed = LastOptionalPlusOneBlePacket.parse(encoded, layout)

        assertThat(parsed.size.toUnsignedInt()).isEqualTo(200)
        assertThat(parsed.payload).isEqualTo(payload)
    }


    @Test
    fun `O5 packets are written at their exact length, not padded to the 244-byte MTU`() {
        val payload = payloadOf(44, seed = 7)

        val packets = PayloadSplitter(payload, BlePacketLayout.OMNIPOD_5).splitInPackets()
        val encoded = packets.single().toByteArray(BlePacketLayout.OMNIPOD_5)

        assertThat(encoded.size).isEqualTo(51)
    }

    @Test
    fun `O5 multi-packet messages pad no packet, including the last`() {
        val payload = payloadOf(642, seed = 8)

        val packets = PayloadSplitter(payload, BlePacketLayout.OMNIPOD_5).splitInPackets()
        val encoded = packets.map { it.toByteArray(BlePacketLayout.OMNIPOD_5) }

        assertThat(encoded.last().size).isLessThan(BlePacketLayout.OMNIPOD_5.maxPayloadSize)
        val headerBytes = encoded.size * 1 + 1 + 4 + 1
        assertThat(encoded.sumOf { it.size }).isEqualTo(payload.size + headerBytes)
        assertThat(roundTrip(payload, BlePacketLayout.OMNIPOD_5)).isEqualTo(payload)
    }

    @Test
    fun `Dash packets keep their existing full-length padding`() {
        val payload = payloadOf(5, seed = 9)

        val encoded = PayloadSplitter(payload, BlePacketLayout.DASH)
            .splitInPackets().single().toByteArray(BlePacketLayout.DASH)

        assertThat(encoded.size).isEqualTo(BlePacketLayout.DASH.maxPayloadSize)
    }

    @Test
    fun `Byte toUnsignedInt reinterprets the full 0-255 range correctly`() {
        assertThat(0.toByte().toUnsignedInt()).isEqualTo(0)
        assertThat(127.toByte().toUnsignedInt()).isEqualTo(127)
        assertThat(128.toByte().toUnsignedInt()).isEqualTo(128)
        assertThat(200.toByte().toUnsignedInt()).isEqualTo(200)
        assertThat(255.toByte().toUnsignedInt()).isEqualTo(255)
    }

    @Test
    fun `corrupted payload is still detected via CRC mismatch after joining (O5)`() {
        val payload = payloadOf(500, seed = 99)
        val packets = PayloadSplitter(payload, BlePacketLayout.OMNIPOD_5).splitInPackets()
        val encoded = packets.map { it.toByteArray(BlePacketLayout.OMNIPOD_5) }.toMutableList()

        val corruptIndex = 6
        encoded[encoded.lastIndex][corruptIndex] = (encoded.last()[corruptIndex] + 1).toByte()

        val joiner = PayloadJoiner(encoded.first(), BlePacketLayout.OMNIPOD_5)
        for (packetBytes in encoded.drop(1)) {
            joiner.accumulate(packetBytes)
        }

        assertThrows(CrcMismatchException::class.java) {
            joiner.finalize()
        }
    }
}
