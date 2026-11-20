package app.aaps.plugins.dexcomoneplus.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The shared replacement for `java.nio.ByteBuffer`, run on every target this module builds for.
 *
 * It lives in commonTest and not beside the other packet tests on purpose: these bytes go on the
 * wire to a sensor, so they have to be proved on every target, and integer shifts are exactly the
 * kind of thing that can differ between two compilers.
 *
 * Test names here carry no comma: Kotlin/Native rejects one inside a backtick quoted name.
 */
class OnePlusBytesTest {

    @Test
    fun `the writer puts multi byte fields little endian`() {
        val writer = OnePlusByteWriter(9)
        writer.put(0x34)
        writer.putShort(150)
        writer.putInt(0x01020304)

        assertEquals("34" + "9600" + "04030201" + "0000", writer.array().onePlusToHex())
    }

    @Test
    fun `bytes never written stay zero`() {
        val writer = OnePlusByteWriter(4)
        writer.put(0x01)

        assertEquals("01000000", writer.array().onePlusToHex())
    }

    @Test
    fun `the reader reads back what the writer wrote`() {
        val writer = OnePlusByteWriter(7)
        writer.put(-1)
        writer.putShort(-2)
        writer.putInt(-3)

        val reader = OnePlusByteReader(writer.array())
        assertEquals((-1).toByte(), reader.byte())
        assertEquals((-2).toShort(), reader.short())
        assertEquals(-3, reader.int())
        assertEquals(0, reader.remaining)
    }

    @Test
    fun `an unsigned byte is read as zero to 255`() {
        val reader = OnePlusByteReader(byteArrayOf(0x00, 0x7f, -1))
        assertEquals(0, reader.u8())
        assertEquals(127, reader.u8())
        assertEquals(255, reader.u8())
    }

    @Test
    fun `an absolute read does not move the position`() {
        val reader = OnePlusByteReader(byteArrayOf(0x25, 0x00, 0x04, 0x03, 0x02, 0x01))
        assertEquals(0x01020304, reader.intAt(2))
        assertEquals(0x25.toByte(), reader.byte())
    }

    @Test
    fun `skipping moves past the bytes`() {
        val reader = OnePlusByteReader(byteArrayOf(0x01, 0x02, 0x03))
        reader.skip(2)
        assertEquals(0x03.toByte(), reader.byte())
    }

    @Test
    fun `reading past the end fails instead of returning rubbish`() {
        val reader = OnePlusByteReader(byteArrayOf(0x01))
        reader.byte()
        assertFailsWith<IllegalArgumentException> { reader.byte() }
    }

    @Test
    fun `hex is lower case and two characters per byte`() {
        assertEquals("00017fff", byteArrayOf(0x00, 0x01, 0x7f, -1).onePlusToHex())
        assertEquals("", ByteArray(0).onePlusToHex())
    }

    @Test
    fun `a calibration request carries the same bytes on every target`() {
        val packet = assertNotNull(OnePlusCalibrateTx.build(glucoseMgdl = 150, dexTimeSeconds = 0x01020304))

        assertEquals(OnePlusCalibrateTx.PACKET_LENGTH, packet.size)
        assertEquals(OnePlusCalibrateTx.OPCODE, packet[0])
        assertEquals("34960004030201", packet.onePlusToHex())
    }

    @Test
    fun `a session start answer is read on every target`() {
        val writer = OnePlusByteWriter(OnePlusSessionStartRx.PACKET_LENGTH)
        writer.put(OnePlusSessionStartRx.OPCODE)
        writer.put(0x00) // status
        writer.put(0x01) // info: OK
        writer.putInt(1_000) // requested start
        writer.putInt(1_010) // session start
        writer.putInt(2_000) // transmitter time
        val packet = writer.array()
        val crc = OnePlusFastCrc16.calculate(packet, OnePlusSessionStartRx.PACKET_LENGTH - 2)
        packet[OnePlusSessionStartRx.PACKET_LENGTH - 2] = crc[0]
        packet[OnePlusSessionStartRx.PACKET_LENGTH - 1] = crc[1]

        val parsed = assertNotNull(OnePlusSessionStartRx.parse(packet))
        assertEquals(0, parsed.status)
        assertEquals(1_000, parsed.requestedStartTime)
        assertEquals(1_010, parsed.sessionStartTime)
        assertEquals(2_000, parsed.transmitterTime)
        assertTrue(parsed.isOkay())
    }

    @Test
    fun `a glucose packet decodes on every target`() {
        val writer = OnePlusByteWriter(16)
        writer.put(0x4f)
        writer.put(0x00) // status
        writer.putInt(1) // sequence
        writer.putInt(100) // transmitter time
        writer.putShort(120) // glucose, not display only
        writer.put(0x06) // Ok
        writer.put(0) // trend
        writer.putShort(0) // predicted
        val raw = writer.array()
        val crc = OnePlusFastCrc16.calculate(raw, raw.size - 2)
        raw[raw.size - 2] = crc[0]
        raw[raw.size - 1] = crc[1]

        val parsed = assertNotNull(OnePlusGlucoseParser.parseControlPacket(raw, nowMs = 1_700_000_000_000L))
        assertEquals(OnePlusCalibrationState.Ok, parsed.calibration)
        assertTrue(parsed.usable)
        assertEquals(120.0, assertNotNull(parsed.sample).mgdl)
    }
}
