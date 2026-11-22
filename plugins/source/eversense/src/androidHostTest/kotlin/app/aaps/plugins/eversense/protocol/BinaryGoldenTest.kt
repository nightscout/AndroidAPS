package app.aaps.plugins.eversense.protocol

import app.aaps.plugins.eversense.enums.CalibrationFlag
import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.enums.EversenseTrendArrow
import app.aaps.plugins.eversense.models.ActiveAlarm
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.packets.e3.util.EversenseE3Parser
import app.aaps.plugins.eversense.packets.e3.util.EversenseE3Writer
import app.aaps.plugins.eversense.packets.e365.CalibrationHistoryItem
import app.aaps.plugins.eversense.packets.e365.utils.toUnix
import app.aaps.plugins.eversense.packets.e365.utils.toUnixArray
import app.aaps.plugins.eversense.util.EversenseDmsBinaryCodec
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Random
import java.util.TimeZone

/**
 * The binary fields of E3, 365 and the Eversense cloud, for large input sets, against digests of what the first driver
 * produced (see [GoldenDigests]). The zone-dependent offset is pinned for a fixed set of zones and past dates: the
 * rules of every zone and of future years change between JDK versions.
 */
class BinaryGoldenTest {

    private val golden = GoldenDigests("binary")

    @Test fun binaryFieldsMatchTheFirstDriver() {
        // CRC-16/CCITT check value of "123456789", independent of any driver
        assertArrayEquals(byteArrayOf(0xb1.toByte(), 0x29), WireEncoding.crc16("123456789".toByteArray()))
        val random = Random(365)
        val fixtures = (0..255).map { byteArrayOf(it.toByte()) } + (0..512).map { ByteArray(it * 3).also(random::nextBytes) }
        for (bytes in fixtures) golden.add("crc16", WireEncoding.crc16(bytes))

        for (bits in 0..65535) {
            val bytes = byteArrayOf(bits.toByte(), (bits ushr 8).toByte())
            golden.add("readDate", EversenseE3Parser.readDate(bytes.toUByteArray(), 0))
            golden.add("readTime", EversenseE3Parser.readTime(bytes.toUByteArray(), 0))
            for (sign in listOf(0, 1, 255)) golden.add("readTimezone", EversenseE3Parser.readTimezone((bytes + byteArrayOf(sign.toByte())).toUByteArray(), 0))
        }

        val previous = TimeZone.getDefault()
        try {
            val timestamps = listOf("1900-01-01T00:00:00Z", "2000-02-29T23:59:59Z", "2024-02-29T01:02:03Z", "2025-01-01T00:00:00Z", "2025-07-01T23:59:59Z", "2127-12-31T23:59:59Z")
                .map { Instant.parse(it).toEpochMilli() }
            val phoneClock = listOf("2000-02-29T23:59:59Z", "2024-02-29T01:02:03Z", "2025-01-01T00:00:00Z", "2025-07-01T23:59:59Z").map { Instant.parse(it).toEpochMilli() }
            for (zone in ZONES) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for (timestamp in timestamps) {
                    golden.add("writeDate", EversenseE3Writer.writeDate(timestamp))
                    golden.add("writeTime", EversenseE3Writer.writeTime(timestamp))
                }
                for (timestamp in phoneClock) golden.add("writeTimezone", "$zone ${EversenseE3Writer.writeTimezone(timestamp).toHex()}")
            }
        } finally {
            TimeZone.setDefault(previous)
        }
        for (value in listOf(Int.MIN_VALUE, -65536, -1, 0, 1, 255, 256, 65535, Int.MAX_VALUE)) {
            golden.add("writeInt16", EversenseE3Writer.writeInt16(value))
            for (width in listOf(2, 3, 4)) golden.add("littleEndian", BinaryFields.littleEndian(value.toLong(), width))
        }
        for (value in listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -3.14159, 0.0, 1.5)) golden.add("writeDouble", EversenseE3Writer.writeDouble(value))

        for (value in listOf(Long.MIN_VALUE, -1L, 0L, 946_684_799_999L, 946_684_800_000L, 1_735_689_610_999L, Long.MAX_VALUE)) golden.add("toUnixArray", value.toUnixArray())
        val clockRandom = Random(191)
        for (size in 0..16) repeat(100) { golden.add("toUnix", ByteArray(size).also(clockRandom::nextBytes).toUByteArray().toUnix()) }

        val timestamp = 1_735_689_610_000L
        for (sensor in listOf("", "0", "00112233445566778899", "abcdef")) {
            val readings = listOf(0, 19, 20, 100, 449, 450, 65535).mapIndexed { index, glucose ->
                EversenseCGMResult(glucose, timestamp + index * 61_000L, EversenseTrendArrow.entries[index % EversenseTrendArrow.entries.size], sensor)
            }
            golden.add("dmsReadings", EversenseDmsBinaryCodec.buildSgBytes(readings))
            golden.add("dmsAlerts", EversenseDmsBinaryCodec.buildAlertBytes(sensor, EversenseAlarm.entries.map { ActiveAlarm(it, 0, 0, 0) }, timestamp, 123))
        }
        val calibrations = CalibrationFlag.entries.map { CalibrationHistoryItem(timestamp + it.ordinal * 60_000L, 80 + it.ordinal, it) }
        golden.add("dmsCalibrations", EversenseDmsBinaryCodec.buildMgBytes(calibrations))
        golden.add("dmsCalibrations", EversenseDmsBinaryCodec.buildEmptyMgBytes())
        golden.add("dmsPatient", EversenseDmsBinaryCodec.buildEmptyPatientBytes())
        for (signal in -10..110) golden.add("dmsSignal", EversenseDmsBinaryCodec.signalStrengthOrdinal(signal))
        for (trend in EversenseTrendArrow.entries) golden.add("dmsTrend", "${trend.name} ${EversenseDmsBinaryCodec.trendOrdinal(trend)}")

        golden.check()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    private companion object {

        /** Zones with whole, half and quarter hour offsets, summer time on both halves of the world */
        val ZONES = listOf("UTC", "Europe/Prague", "America/New_York", "America/Sao_Paulo", "Asia/Kolkata", "Asia/Kathmandu", "Australia/Adelaide", "Pacific/Auckland")
    }
}
