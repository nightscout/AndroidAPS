package app.aaps.plugins.eversense.util

import app.aaps.plugins.eversense.enums.CalibrationFlag
import app.aaps.plugins.eversense.enums.EversenseTrendArrow
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.packets.e365.CalibrationHistoryItem
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * Golden tests for [EversenseDmsBinaryCodec].
 *
 * These pin the exact bytes/base64 that get uploaded to the Eversense DMS portal. They were
 * computed independently (not from the Kotlin code) and verified to match the two original
 * per-file copies that this codec replaced, so they guarantee the de-duplication did not
 * change any uploaded value and guard against future drift.
 *
 * Reference timestamp: 1700000000000 ms == 2023-11-14T22:13:20 UTC.
 */
class EversenseDmsBinaryCodecTest {

    private val ts = 1700000000000L
    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)
    private fun reading(glucose: Int, datetime: Long, sensorId: String) =
        EversenseCGMResult(glucose, datetime, EversenseTrendArrow.FLAT, sensorId, "")

    // ─── little-endian integers ───────────────────────────────────────────────

    @Test
    fun `int16LE writes two little-endian bytes`() {
        assertArrayEquals(byteArrayOf(0x34, 0x12), EversenseDmsBinaryCodec.int16LE(0x1234))
    }

    @Test
    fun `int24LE writes three little-endian bytes`() {
        assertArrayEquals(byteArrayOf(0x56, 0x34, 0x12), EversenseDmsBinaryCodec.int24LE(0x123456))
    }

    @Test
    fun `int32LE writes four little-endian bytes including negative offsets`() {
        assertArrayEquals(byteArrayOf(0x78, 0x56, 0x34, 0x12), EversenseDmsBinaryCodec.int32LE(0x12345678))
        // Negative timezone offset (e.g. -18000 s == UTC-5) must two's-complement correctly
        assertEquals("sLn//w==", b64(EversenseDmsBinaryCodec.int32LE(-18000)))
    }

    // ─── timestamp packing ─────────────────────────────────────────────────────

    @Test
    fun `calcDateBytes packs the date the way the transmitter protocol expects`() {
        assertArrayEquals(byteArrayOf(0x6E, 0x2F), EversenseDmsBinaryCodec.calcDateBytes(ts))
    }

    @Test
    fun `calcTimeBytes packs the time the way the transmitter protocol expects`() {
        assertArrayEquals(byteArrayOf(0xAA.toByte(), 0xB1.toByte()), EversenseDmsBinaryCodec.calcTimeBytes(ts))
    }

    // ─── fixed-header blobs ─────────────────────────────────────────────────────

    @Test
    fun `buildEmptyMgBytes is the fixed zero-record header`() {
        assertEquals("mAEAAAAA", EversenseDmsBinaryCodec.buildEmptyMgBytes())
    }

    @Test
    fun `buildEmptyPatientBytes is the fixed zero-event header`() {
        assertEquals("ngEAAAA=", EversenseDmsBinaryCodec.buildEmptyPatientBytes())
    }

    @Test
    fun `buildAlertBytes embeds the sensor id and a zero count`() {
        assertEquals("kwEAAAAjwasA", EversenseDmsBinaryCodec.buildAlertBytes("23C1AB"))
        assertEquals("kwEAAAAA", EversenseDmsBinaryCodec.buildAlertBytes(""))
    }

    // ─── glucose / calibration record blobs ─────────────────────────────────────

    @Test
    fun `buildSgBytes with no readings is just the header and a zero count`() {
        assertEquals("jAABAAAAAAA=", EversenseDmsBinaryCodec.buildSgBytes(emptyList()))
    }

    @Test
    fun `buildSgBytes encodes a single reading exactly`() {
        val expected = "jAABAAABAAABAABuL6qxeAAAI8GrAAAAAAAAAAAAAAAAAAAAAAAAAA=="
        assertEquals(expected, EversenseDmsBinaryCodec.buildSgBytes(listOf(reading(120, ts, "23C1AB"))))
    }

    @Test
    fun `buildSgBytes encodes multiple readings with incrementing record numbers`() {
        val expected = "jAABAAACAAABAABuL6qxeAAAI8GrAAAAAAAAAAAAAAAAAAAAAAAAAAIAAG4vSrJfAAAjwasAAAAAAAAAAAAAAAAAAAAAAAAA"
        val readings = listOf(reading(120, ts, "23C1AB"), reading(95, ts + 300000, "23C1AB"))
        assertEquals(expected, EversenseDmsBinaryCodec.buildSgBytes(readings))
    }

    @Test
    fun `buildMgBytes encodes a calibration record exactly`() {
        val cals = listOf(CalibrationHistoryItem(ts, 100, CalibrationFlag.ACTUALLY_USED_FOR_CALIBRATION))
        assertEquals("mAEAAQAAAQBuL6qxZAAAAAEAAAA=", EversenseDmsBinaryCodec.buildMgBytes(cals))
    }

    // ─── ordinal mappings ───────────────────────────────────────────────────────

    @Test
    fun `signalStrengthOrdinal maps percentage bands to ordinals`() {
        assertEquals(5, EversenseDmsBinaryCodec.signalStrengthOrdinal(100))
        assertEquals(5, EversenseDmsBinaryCodec.signalStrengthOrdinal(75))
        assertEquals(4, EversenseDmsBinaryCodec.signalStrengthOrdinal(74))
        assertEquals(4, EversenseDmsBinaryCodec.signalStrengthOrdinal(48))
        assertEquals(3, EversenseDmsBinaryCodec.signalStrengthOrdinal(47))
        assertEquals(3, EversenseDmsBinaryCodec.signalStrengthOrdinal(30))
        assertEquals(2, EversenseDmsBinaryCodec.signalStrengthOrdinal(29))
        assertEquals(2, EversenseDmsBinaryCodec.signalStrengthOrdinal(28))
        assertEquals(1, EversenseDmsBinaryCodec.signalStrengthOrdinal(27))
        assertEquals(1, EversenseDmsBinaryCodec.signalStrengthOrdinal(25))
        assertEquals(0, EversenseDmsBinaryCodec.signalStrengthOrdinal(24))
        assertEquals(0, EversenseDmsBinaryCodec.signalStrengthOrdinal(0))
    }

    @Test
    fun `trendOrdinal maps every arrow to its protocol ordinal`() {
        assertEquals(0, EversenseDmsBinaryCodec.trendOrdinal(EversenseTrendArrow.NONE))
        assertEquals(1, EversenseDmsBinaryCodec.trendOrdinal(EversenseTrendArrow.SINGLE_DOWN))
        assertEquals(2, EversenseDmsBinaryCodec.trendOrdinal(EversenseTrendArrow.FORTY_FIVE_DOWN))
        assertEquals(3, EversenseDmsBinaryCodec.trendOrdinal(EversenseTrendArrow.FLAT))
        assertEquals(4, EversenseDmsBinaryCodec.trendOrdinal(EversenseTrendArrow.FORTY_FIVE_UP))
        assertEquals(5, EversenseDmsBinaryCodec.trendOrdinal(EversenseTrendArrow.SINGLE_UP))
    }
}
