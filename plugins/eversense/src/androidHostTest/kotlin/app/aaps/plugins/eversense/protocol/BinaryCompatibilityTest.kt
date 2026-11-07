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
import java.lang.reflect.Method
import java.time.Instant
import java.util.Random
import java.util.TimeZone
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BinaryCompatibilityTest {
    private val methods = mutableMapOf<String, Pair<Any?, Method>>()
    private fun old(type: String, name: String, vararg args: Any): Any? {
        val (target, method) = methods.getOrPut("$type/$name") {
            val klass = Class.forName("app.aaps.plugins.eversense.reference.$type")
            val target = klass.fields.firstOrNull { it.name == "Companion" || it.name == "INSTANCE" }?.get(null)
            val owner = target?.javaClass ?: klass
            target to owner.methods.single { it.name.substringBefore('-') == name && '$' !in it.name && it.parameterCount == args.size }
        }
        return method.invoke(target, *args)
    }
    private val parser = "packets.e3.util.EversenseE3Parser"
    private val writer = "packets.e3.util.EversenseE3Writer"
    private val dms = "util.EversenseDmsBinaryCodec"
    private val extensions = "packets.e365.utils.ByteArrayUtilKt"

    @Test fun crcMatchesFrozenBitwiseEncoderAndIndependentCheckVector() {
        assertArrayEquals(byteArrayOf(0xb1.toByte(), 0x29), WireEncoding.crc16("123456789".toByteArray()))
        val random = Random(365)
        val fixtures = (0..255).map { byteArrayOf(it.toByte()) } +
            (0..512).map { ByteArray(it * 3).also(random::nextBytes) }
        for (bytes in fixtures) assertArrayEquals(old(writer, "generateChecksumCRC16", bytes) as ByteArray, WireEncoding.crc16(bytes))
    }

    @Test fun everyPackedDateTimeAndTimezoneValueMatchesOriginalIncludingLenientOverflow() {
        for (bits in 0..65535) {
            val bytes = byteArrayOf(bits.toByte(), (bits ushr 8).toByte())
            assertEquals(old(parser, "readDate", bytes, 0), EversenseE3Parser.readDate(bytes.toUByteArray(), 0), "date $bits")
            assertEquals(old(parser, "readTime", bytes, 0), EversenseE3Parser.readTime(bytes.toUByteArray(), 0), "time $bits")
            for (sign in listOf(0, 1, 255)) {
                val offset = bytes + byteArrayOf(sign.toByte())
                assertEquals(old(parser, "readTimezone", offset, 0), EversenseE3Parser.readTimezone(offset.toUByteArray(), 0), "offset $bits/$sign")
            }
        }
    }

    @Test fun dateTimeAndSignedIntegerEncodingMatchAcrossLeapDaysAndTimezoneRules() {
        val previous = TimeZone.getDefault()
        try {
            val timestamps = listOf("1900-01-01T00:00:00Z", "2000-02-29T23:59:59Z", "2024-02-29T01:02:03Z", "2025-01-01T00:00:00Z", "2025-07-01T23:59:59Z", "2127-12-31T23:59:59Z").map { Instant.parse(it).toEpochMilli() }
            for (zone in TimeZone.getAvailableIDs()) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for (timestamp in timestamps) {
                    assertArrayEquals(old(writer, "writeDate", timestamp) as ByteArray, EversenseE3Writer.writeDate(timestamp))
                    assertArrayEquals(old(writer, "writeTime", timestamp) as ByteArray, EversenseE3Writer.writeTime(timestamp))
                    assertArrayEquals(old(writer, "writeTimezone", timestamp) as ByteArray, EversenseE3Writer.writeTimezone(timestamp), "$zone at $timestamp")
                }
            }
        } finally { TimeZone.setDefault(previous) }
        for (value in listOf(Int.MIN_VALUE, -65536, -1, 0, 1, 255, 256, 65535, Int.MAX_VALUE)) {
            assertArrayEquals(old(writer, "writeInt16", value) as ByteArray, EversenseE3Writer.writeInt16(value))
            for (width in listOf(2, 3, 4)) {
                val name = "int${width * 8}LE"
                assertArrayEquals(old(dms, name, value) as ByteArray, BinaryFields.littleEndian(value.toLong(), width))
            }
        }
        for (value in listOf(Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -3.14159, 0.0, 1.5)) {
            assertArrayEquals(old(writer, "writeDouble", value) as ByteArray, EversenseE3Writer.writeDouble(value))
        }
    }

    @Test fun sensorClockUsesIdenticalTruncationOverflowAndEpoch() {
        for (value in listOf(Long.MIN_VALUE, -1L, 0L, 946_684_799_999L, 946_684_800_000L, 1_735_689_610_999L, Long.MAX_VALUE)) {
            assertArrayEquals(old(extensions, "toUnixArray", value) as ByteArray, value.toUnixArray())
        }
        val random = Random(191)
        for (size in 0..16) repeat(100) {
            val bytes = ByteArray(size).also(random::nextBytes)
            assertEquals(old(extensions, "toUnix", bytes), bytes.toUByteArray().toUnix())
        }
    }

    private fun legacyEnum(type: String, name: String): Any = Class.forName("app.aaps.plugins.eversense.reference.enums.$type").enumConstants!!.single { (it as Enum<*>).name == name }
    private fun legacyReading(value: EversenseCGMResult): Any = Class.forName("app.aaps.plugins.eversense.reference.models.EversenseCGMResult")
        .constructors.single { !it.isSynthetic && it.parameterCount == 5 }.newInstance(value.glucoseInMgDl, value.datetime, legacyEnum("EversenseTrendArrow", value.trend.name), value.sensorId, value.rawResponseHex)

    @Test fun dmsRecordsMatchByteForByteForSensorIdsReadingsCalibrationsAndEveryAlarm() {
        val timestamp = 1_735_689_610_000L
        for (sensor in listOf("", "0", "00112233445566778899", "abcdef")) {
            val readings = listOf(0, 19, 20, 100, 449, 450, 65535).mapIndexed { index, glucose -> EversenseCGMResult(glucose, timestamp + index * 61_000L, EversenseTrendArrow.entries[index % EversenseTrendArrow.entries.size], sensor) }
            assertEquals(old(dms, "buildSgBytes", readings.map(::legacyReading)), EversenseDmsBinaryCodec.buildSgBytes(readings))
            val alarms = EversenseAlarm.entries.map { ActiveAlarm(it, 0, 0, 0) }
            val legacyAlarms = alarms.map { Class.forName("app.aaps.plugins.eversense.reference.models.ActiveAlarm").constructors.single { !it.isSynthetic && it.parameterCount == 4 }.newInstance(legacyEnum("EversenseAlarm", it.code.name), 0, 0, 0) }
            assertEquals(old(dms, "buildAlertBytes", sensor, legacyAlarms, timestamp, 123), EversenseDmsBinaryCodec.buildAlertBytes(sensor, alarms, timestamp, 123))
        }
        val calibrations = CalibrationFlag.entries.map { CalibrationHistoryItem(timestamp + it.ordinal * 60_000L, 80 + it.ordinal, it) }
        val legacyCalibrations = calibrations.map { Class.forName("app.aaps.plugins.eversense.reference.packets.e365.CalibrationHistoryItem").constructors.single { !it.isSynthetic && it.parameterCount == 3 }.newInstance(it.datetime, it.glucoseInMgDl, legacyEnum("CalibrationFlag", it.flag.name)) }
        assertEquals(old(dms, "buildMgBytes", legacyCalibrations), EversenseDmsBinaryCodec.buildMgBytes(calibrations))
        assertEquals(old(dms, "buildEmptyMgBytes"), EversenseDmsBinaryCodec.buildEmptyMgBytes())
        assertEquals(old(dms, "buildEmptyPatientBytes"), EversenseDmsBinaryCodec.buildEmptyPatientBytes())
        for (signal in -10..110) assertEquals(old(dms, "signalStrengthOrdinal", signal), EversenseDmsBinaryCodec.signalStrengthOrdinal(signal))
        for (trend in EversenseTrendArrow.entries) assertEquals(old(dms, "trendOrdinal", legacyEnum("EversenseTrendArrow", trend.name)), EversenseDmsBinaryCodec.trendOrdinal(trend))
    }
}
