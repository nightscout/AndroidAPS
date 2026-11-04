package app.aaps.plugins.aps.autotune.data

import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test

class PeakDeviationTest : TestBase() {

    @Test
    fun `default constructor creates empty PeakDeviation`() {
        val peakDeviation = PeakDeviation()

        assertThat(peakDeviation.peak).isEqualTo(0)
        assertThat(peakDeviation.meanDeviation).isEqualTo(0.0)
        assertThat(peakDeviation.smrDeviation).isEqualTo(0.0)
        assertThat(peakDeviation.rmsDeviation).isEqualTo(0.0)
    }

    @Test
    fun `constructor with parameters sets all fields`() {
        val peakDeviation = PeakDeviation(
            peak = 75,
            meanDeviation = 5.5,
            smrDeviation = 3.2,
            rmsDeviation = 4.8
        )

        assertThat(peakDeviation.peak).isEqualTo(75)
        assertThat(peakDeviation.meanDeviation).isEqualTo(5.5)
        assertThat(peakDeviation.smrDeviation).isEqualTo(3.2)
        assertThat(peakDeviation.rmsDeviation).isEqualTo(4.8)
    }

    @Test
    fun `constructor from JSON parses all fields`() {
        val json = JSONObject().apply {
            put("peak", 75)
            put("meanDeviation", 5.5)
            put("SMRDeviation", 3.2)
            put("RMSDeviation", 4.8)
        }

        val peakDeviation = PeakDeviation(json)

        assertThat(peakDeviation.peak).isEqualTo(75)
        assertThat(peakDeviation.meanDeviation).isEqualTo(5.5)
        assertThat(peakDeviation.smrDeviation).isEqualTo(3.2)
        assertThat(peakDeviation.rmsDeviation).isEqualTo(4.8)
    }

    @Test
    fun `constructor from JSON handles missing fields`() {
        val json = JSONObject().apply {
            put("peak", 75)
            put("meanDeviation", 5.5)
        }

        val peakDeviation = PeakDeviation(json)

        assertThat(peakDeviation.peak).isEqualTo(75)
        assertThat(peakDeviation.meanDeviation).isEqualTo(5.5)
        assertThat(peakDeviation.smrDeviation).isEqualTo(0.0)
        assertThat(peakDeviation.rmsDeviation).isEqualTo(0.0)
    }

    @Test
    fun `constructor from JSON handles empty JSON`() {
        val json = JSONObject()

        val peakDeviation = PeakDeviation(json)

        assertThat(peakDeviation.peak).isEqualTo(0)
        assertThat(peakDeviation.meanDeviation).isEqualTo(0.0)
        assertThat(peakDeviation.smrDeviation).isEqualTo(0.0)
        assertThat(peakDeviation.rmsDeviation).isEqualTo(0.0)
    }

    @Test
    fun `toJSON includes all fields`() {
        val peakDeviation = PeakDeviation(
            peak = 75,
            meanDeviation = 5.5,
            smrDeviation = 3.2,
            rmsDeviation = 4.8
        )

        val json = peakDeviation.toJSON()

        assertThat(json.getInt("peak")).isEqualTo(75)
        assertThat(json.getInt("meanDeviation")).isEqualTo(5)
        assertThat(json.getDouble("SMRDeviation")).isEqualTo(3.2)
        assertThat(json.getInt("RMSDeviation")).isEqualTo(4)
    }

    @Test
    fun `toJSON converts meanDeviation to integer`() {
        val peakDeviation = PeakDeviation(meanDeviation = 5.7)

        val json = peakDeviation.toJSON()

        assertThat(json.getInt("meanDeviation")).isEqualTo(5)
    }

    @Test
    fun `toJSON converts rmsDeviation to integer`() {
        val peakDeviation = PeakDeviation(rmsDeviation = 4.9)

        val json = peakDeviation.toJSON()

        assertThat(json.getInt("RMSDeviation")).isEqualTo(4)
    }

    @Test
    fun `toJSON keeps smrDeviation as double`() {
        val peakDeviation = PeakDeviation(smrDeviation = 3.256)

        val json = peakDeviation.toJSON()

        assertThat(json.getDouble("SMRDeviation")).isEqualTo(3.256)
    }

    @Test
    fun `handles zero values`() {
        val peakDeviation = PeakDeviation(
            peak = 0,
            meanDeviation = 0.0,
            smrDeviation = 0.0,
            rmsDeviation = 0.0
        )

        val json = peakDeviation.toJSON()

        assertThat(json.getInt("peak")).isEqualTo(0)
        assertThat(json.getInt("meanDeviation")).isEqualTo(0)
        assertThat(json.getDouble("SMRDeviation")).isEqualTo(0.0)
        assertThat(json.getInt("RMSDeviation")).isEqualTo(0)
    }

    @Test
    fun `handles negative values`() {
        val peakDeviation = PeakDeviation(
            peak = -1,
            meanDeviation = -2.5,
            smrDeviation = -1.3,
            rmsDeviation = -3.7
        )

        val json = peakDeviation.toJSON()

        assertThat(json.getInt("peak")).isEqualTo(-1)
        assertThat(json.getInt("meanDeviation")).isEqualTo(-2)
        assertThat(json.getDouble("SMRDeviation")).isEqualTo(-1.3)
        assertThat(json.getInt("RMSDeviation")).isEqualTo(-3)
    }

    @Test
    fun `handles large values`() {
        val peakDeviation = PeakDeviation(
            peak = 200,
            meanDeviation = 100.0,
            smrDeviation = 50.0,
            rmsDeviation = 75.0
        )

        val json = peakDeviation.toJSON()

        assertThat(json.getInt("peak")).isEqualTo(200)
        assertThat(json.getInt("meanDeviation")).isEqualTo(100)
        assertThat(json.getDouble("SMRDeviation")).isEqualTo(50.0)
        assertThat(json.getInt("RMSDeviation")).isEqualTo(75)
    }

    @Test
    fun `handles typical insulin peak values`() {
        val peaks = listOf(45, 55, 75, 100, 120)

        peaks.forEach { peakTime ->
            val peakDeviation = PeakDeviation(peak = peakTime)
            val json = peakDeviation.toJSON()
            assertThat(json.getInt("peak")).isEqualTo(peakTime)
        }
    }

    @Test
    fun `roundtrip from JSON to object to JSON preserves data`() {
        val originalJson = JSONObject().apply {
            put("peak", 75)
            put("meanDeviation", 5.8)
            put("SMRDeviation", 3.4)
            put("RMSDeviation", 4.2)
        }

        val peakDeviation = PeakDeviation(originalJson)
        val newJson = peakDeviation.toJSON()

        assertThat(newJson.getInt("peak")).isEqualTo(75)
        // meanDeviation gets truncated to int
        assertThat(newJson.getInt("meanDeviation")).isEqualTo(5)
        assertThat(newJson.getDouble("SMRDeviation")).isEqualTo(3.4)
        // rmsDeviation gets truncated to int
        assertThat(newJson.getInt("RMSDeviation")).isEqualTo(4)
    }

    @Test
    fun `handles ultra-rapid insulin peaks`() {
        val peakDeviation = PeakDeviation(peak = 45)

        val json = peakDeviation.toJSON()

        assertThat(json.getInt("peak")).isEqualTo(45)
    }

    @Test
    fun `handles rapid-acting insulin peaks`() {
        val peakDeviation = PeakDeviation(peak = 75)

        val json = peakDeviation.toJSON()

        assertThat(json.getInt("peak")).isEqualTo(75)
    }
}
