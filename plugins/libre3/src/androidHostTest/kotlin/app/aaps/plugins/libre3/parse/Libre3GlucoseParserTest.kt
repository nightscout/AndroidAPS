package app.aaps.plugins.libre3.parse

import app.aaps.plugins.libre3.warmup.Libre3WarmupClock
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The 29 byte reading, and the gate that decides what AAPS may dose on.
 *
 * Offsets follow LibreCRKit `DataPlane/RealtimeGlucoseReading.swift` at pin `a86b92f`.
 */
class Libre3GlucoseParserTest {

    /**
     * Builds a reading the way a sensor would.
     *
     * @param packedFlags bit 15 marks bad data, bits 13 and 14 carry the sensor state.
     */
    private fun reading(
        lifeCount: Int = 120,
        uncappedMgdl: Int = 100,
        packedFlags: Int = 0,
        rateHundredths: Int = 50,
        trendBits: Int = 3,
        actionable: Boolean = true,
        packedGlucose: Int = uncappedMgdl,
    ): ByteArray {
        val out = ByteArray(Libre3GlucoseParser.PLAINTEXT_SIZE)
        putU16(out, 0, lifeCount)
        putU16(out, 2, (packedGlucose and 0x1FFF) or packedFlags)
        putU16(out, 4, rateHundredths and 0xFFFF)
        out[14] = ((trendBits and 0x07) or (if (actionable) 0x08 else 0)).toByte()
        putU16(out, 15, uncappedMgdl)
        return out
    }

    private fun putU16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value and 0xFF).toByte()
        data[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    @Test
    fun `a normal reading is read completely`() {
        val parsed = Libre3GlucoseParser.parse(reading(lifeCount = 1234, uncappedMgdl = 142, rateHundredths = 250))

        assertThat(parsed.lifeCount).isEqualTo(1234)
        assertThat(parsed.glucoseMgdl).isEqualTo(142)
        assertThat(parsed.rateOfChangeMgdlPerMinute).isWithin(1e-9).of(2.5)
        assertThat(parsed.trend).isEqualTo(Libre3Trend.STABLE)
        assertThat(parsed.sensorCondition).isEqualTo(Libre3SensorCondition.OK)
        assertThat(parsed.dataQualityGood).isTrue()
        assertThat(parsed.actionable).isTrue()
    }

    @Test
    fun `the shown number comes from byte 15, not from the packed word`() {
        // The two places hold different numbers here on purpose. The upstream parser reads byte 15
        // for the value and uses the packed word only for the state bits. A change back to the
        // packed word would pass every other test in this file, so this one pins it.
        val parsed = Libre3GlucoseParser.parse(reading(uncappedMgdl = 250, packedGlucose = 100))

        assertThat(parsed.glucoseMgdl).isEqualTo(250)
        assertThat(parsed.uncappedGlucoseMgdl).isEqualTo(250)
    }

    @Test
    fun `a very high reading is cut to the top of the shown range, whatever the packed word says`() {
        val parsed = Libre3GlucoseParser.parse(reading(uncappedMgdl = 700, packedGlucose = 120))

        assertThat(parsed.glucoseMgdl).isEqualTo(501)
        assertThat(parsed.uncappedGlucoseMgdl).isEqualTo(700)
    }

    @Test
    fun `byte 14 is kept exactly as the sensor sent it`() {
        val parsed = Libre3GlucoseParser.parse(reading(trendBits = 5, actionable = true))

        assertThat(parsed.trendAndStatusByte).isEqualTo(0x0D)
    }

    @Test
    fun `a falling rate of change is read as a negative number`() {
        val parsed = Libre3GlucoseParser.parse(reading(rateHundredths = -175))

        assertThat(parsed.rateOfChangeMgdlPerMinute).isWithin(1e-9).of(-1.75)
    }

    @Test
    fun `a missing rate of change is null, not a huge negative number`() {
        val parsed = Libre3GlucoseParser.parse(reading(rateHundredths = Libre3GlucoseParser.RATE_OF_CHANGE_MISSING))

        assertThat(parsed.rateOfChangeMgdlPerMinute).isNull()
    }

    @Test
    fun `every direction the sensor can send is read`() {
        val expected = listOf(
            Libre3Trend.NOT_DETERMINED, Libre3Trend.FALLING_QUICKLY, Libre3Trend.FALLING,
            Libre3Trend.STABLE, Libre3Trend.RISING, Libre3Trend.RISING_QUICKLY,
            Libre3Trend.UNKNOWN, Libre3Trend.UNKNOWN,
        )
        for (bits in 0..7) {
            assertThat(Libre3GlucoseParser.parse(reading(trendBits = bits)).trend).isEqualTo(expected[bits])
        }
    }

    @Test
    fun `the shown range follows the sensor rules`() {
        assertThat(Libre3GlucoseParser.displayValue(0)).isNull()
        assertThat(Libre3GlucoseParser.displayValue(1)).isEqualTo(39)
        assertThat(Libre3GlucoseParser.displayValue(38)).isEqualTo(39)
        assertThat(Libre3GlucoseParser.displayValue(39)).isEqualTo(39)
        assertThat(Libre3GlucoseParser.displayValue(100)).isEqualTo(100)
        assertThat(Libre3GlucoseParser.displayValue(501)).isEqualTo(501)
        assertThat(Libre3GlucoseParser.displayValue(502)).isEqualTo(501)
        assertThat(Libre3GlucoseParser.displayValue(999)).isEqualTo(501)
        assertThat(Libre3GlucoseParser.displayValue(1000)).isNull()
        assertThat(Libre3GlucoseParser.displayValue(65535)).isNull()
    }

    @Test
    fun `a sensor state other than good is read`() {
        assertThat(Libre3GlucoseParser.parse(reading(packedFlags = 1 shl 13)).sensorCondition)
            .isEqualTo(Libre3SensorCondition.INVALID)
        assertThat(Libre3GlucoseParser.parse(reading(packedFlags = 2 shl 13)).sensorCondition)
            .isEqualTo(Libre3SensorCondition.ESA)
    }

    @Test
    fun `the sensor own bad data mark is read`() {
        val parsed = Libre3GlucoseParser.parse(reading(packedFlags = 0x8000))

        assertThat(parsed.dataQualityGood).isFalse()
    }

    @Test
    fun `a block of the wrong size is refused`() {
        assertThrows<Libre3ParseException> { Libre3GlucoseParser.parse(ByteArray(28)) }
        assertThrows<Libre3ParseException> { Libre3GlucoseParser.parse(ByteArray(30)) }
    }

    // ---- the gate that decides what may reach the loop ----

    private val activeClock = Libre3WarmupClock(lifeCountMinutes = 120, warmupMinutes = 60, wearMinutes = 21600)

    @Test
    fun `a good reading from an active sensor may be used`() {
        val parsed = Libre3GlucoseParser.parse(reading())

        assertThat(parsed.isUsable(activeClock)).isTrue()
        assertThat(parsed.qualityIssues(activeClock)).isEmpty()
    }

    @Test
    fun `a reading during warm-up may never be used`() {
        val warmingUp = Libre3WarmupClock(lifeCountMinutes = 30, warmupMinutes = 60, wearMinutes = 21600)
        val parsed = Libre3GlucoseParser.parse(reading(lifeCount = 30))

        assertThat(parsed.isUsable(warmingUp)).isFalse()
        assertThat(parsed.qualityIssues(warmingUp)).contains(Libre3QualityIssue.SENSOR_WARMUP)
    }

    @Test
    fun `a reading from a sensor that is past its life may never be used`() {
        val expired = Libre3WarmupClock(lifeCountMinutes = 21700, warmupMinutes = 60, wearMinutes = 21600)
        val parsed = Libre3GlucoseParser.parse(reading(lifeCount = 21700))

        assertThat(parsed.isUsable(expired)).isFalse()
        assertThat(parsed.qualityIssues(expired)).contains(Libre3QualityIssue.SENSOR_EXPIRED)
    }

    @Test
    fun `a reading the sensor marked as bad may never be used`() {
        val parsed = Libre3GlucoseParser.parse(reading(packedFlags = 0x8000))

        assertThat(parsed.isUsable(activeClock)).isFalse()
    }

    @Test
    fun `a reading made in a bad sensor state may never be used`() {
        val parsed = Libre3GlucoseParser.parse(reading(packedFlags = 1 shl 13))

        assertThat(parsed.isUsable(activeClock)).isFalse()
    }

    @Test
    fun `a reading with no number at all may never be used`() {
        val parsed = Libre3GlucoseParser.parse(reading(uncappedMgdl = 0))

        assertThat(parsed.glucoseMgdl).isNull()
        assertThat(parsed.isUsable(activeClock)).isFalse()
    }

    @Test
    fun `a reading the sensor says not to act on is still used when all else is good`() {
        // This is the upstream rule, and it is on purpose: the mark is advice, not a fault.
        val parsed = Libre3GlucoseParser.parse(reading(actionable = false))

        assertThat(parsed.qualityIssues(activeClock)).containsExactly(Libre3QualityIssue.NOT_ACTIONABLE)
        assertThat(parsed.isUsable(activeClock)).isTrue()
    }

    @Test
    fun `only the advice issue leaves a reading usable`() {
        for (issue in Libre3QualityIssue.entries) {
            assertThat(issue.blocksUse).isEqualTo(issue != Libre3QualityIssue.NOT_ACTIONABLE)
        }
    }

    // ---- the only way a reading may become something the loop can dose on ----

    @Test
    fun `a good reading becomes a sample timed from the sensor start`() {
        val activatedAt = 1_777_216_508_000L
        val parsed = Libre3GlucoseParser.parse(reading(lifeCount = 120, uncappedMgdl = 142))

        val sample = parsed.toSampleOrNull(activatedAt, wearMinutes = 21600, warmupMinutes = 60)

        assertThat(sample).isNotNull()
        assertThat(sample!!.mgdl).isEqualTo(142.0)
        assertThat(sample.lifeCount).isEqualTo(120)
        assertThat(sample.timestampMs).isEqualTo(activatedAt + 120 * 60_000L)
        assertThat(sample.trendArrowRaw).isEqualTo("Flat")
    }

    @Test
    fun `a reading during warm-up never becomes a sample`() {
        val parsed = Libre3GlucoseParser.parse(reading(lifeCount = 30))

        assertThat(parsed.toSampleOrNull(1_777_216_508_000L, wearMinutes = 21600, warmupMinutes = 60)).isNull()
    }

    @Test
    fun `a reading from a finished sensor never becomes a sample`() {
        val parsed = Libre3GlucoseParser.parse(reading(lifeCount = 21700))

        assertThat(parsed.toSampleOrNull(1_777_216_508_000L, wearMinutes = 21600, warmupMinutes = 60)).isNull()
    }

    @Test
    fun `a reading the sensor marked as bad never becomes a sample`() {
        val parsed = Libre3GlucoseParser.parse(reading(packedFlags = 0x8000))

        assertThat(parsed.toSampleOrNull(1_777_216_508_000L, wearMinutes = 21600, warmupMinutes = 60)).isNull()
    }

    @Test
    fun `a sample never carries the raw number, only the one that may be shown`() {
        val parsed = Libre3GlucoseParser.parse(reading(lifeCount = 120, uncappedMgdl = 700))

        val sample = parsed.toSampleOrNull(1_777_216_508_000L, wearMinutes = 21600, warmupMinutes = 60)

        assertThat(sample!!.mgdl).isEqualTo(501.0)
    }

    @Test
    fun `the sensor life is always checked, even when the caller gives no wear time`() {
        // The clock is built from the reading itself, so warm-up can never be skipped by leaving
        // an argument out.
        val parsed = Libre3GlucoseParser.parse(reading(lifeCount = 10))

        assertThat(parsed.toSampleOrNull(1_777_216_508_000L, wearMinutes = null)).isNull()
    }

    @Test
    fun `a reading is usable when the sensor life is not known yet`() {
        val parsed = Libre3GlucoseParser.parse(reading())

        assertThat(parsed.isUsable(null)).isTrue()
    }
}
